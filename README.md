This is a Kotlin Multiplatform project targeting Server.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Server: `./gradlew :server:run`

---

## Authentication endpoints

All responses are JSON. Protected endpoints expect `Authorization: Bearer <token>`.

| Method | Path             | Auth | Body                                              |
|--------|------------------|------|---------------------------------------------------|
| POST   | `/auth/register` | No   | `{ "email": "...", "password": "...", "name": "..." }` |
| POST   | `/auth/login`    | No   | `{ "email": "...", "password": "..." }`           |
| POST   | `/auth/federated`| No   | `{ "idToken": "<firebase-id-token>", "provider": "GOOGLE", "name": "..." }` |
| GET    | `/auth/me`       | Yes  | -                                                 |
| POST   | `/auth/refresh`  | Yes  | -                                                 |

Successful register/login response:

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": 1, "email": "user@example.com", "name": "User", "storeId": 1 }
}
```

The backend JWT includes the `storeId` claim (when the user has a store), so the client does not need to call
`/auth/me`. If the store is created after logging in, call `POST /auth/refresh` (with the token) to get a fresh
token that includes the new `storeId`.

When a **new** user authenticates (`/auth/register` or `/auth/federated`), the user and their **store** are
created together in the same transaction (`stores.user_id` links them). The store starts with a default welcome
message and empty address/phone data, which the user completes with `PUT /stores/{id}`. An existing user with
valid credentials is only authenticated (their store already exists).

Errors return `{ "error": "<code>" }` with codes: `invalid_email`, `invalid_password`, `invalid_name`,
`email_already_registered`, `invalid_credentials`, `invalid_request`, `invalid_token`, `unauthorized`,
`user_not_found`, `internal_error`.

### Federated authentication

`POST /auth/federated` exchanges a Firebase ID token for a backend JWT. The server verifies the token
signature against Google's public x509 certificates, then checks the `aud` (must equal `FIREBASE_PROJECT_ID`),
the `iss` (`https://securetoken.google.com/<FIREBASE_PROJECT_ID>`) and the expiration.

- `idToken` is required; `provider` (`GOOGLE` | `PHONE` | `EMAIL`) and `name` are optional.
- The user is looked up by `firebase_uid` (Firebase `sub`), then by verified email. If it does not exist it is
  created automatically. The call is idempotent: logging in twice returns the same user.
- A federated user has no usable password (an unusable sentinel hash is stored), so it cannot authenticate
  through `/auth/login`.
- Invalid or expired tokens return `401 {"error":"invalid_token"}`; missing fields return
  `400 {"error":"invalid_request"}`.

```sh
curl -X POST http://localhost:8080/auth/federated \
  -H "Content-Type: application/json" \
  -d '{"idToken":"<firebase-id-token>","provider":"GOOGLE","name":"User"}'
```

### WhatsApp webhook

`GET /webhook` implements Meta's webhook verification handshake. Meta sends `hub.mode=subscribe`,
`hub.verify_token` and `hub.challenge` as query parameters. If the mode is `subscribe` and the token matches
`WHATSAPP_VERIFY_TOKEN`, the server responds `200` with the raw challenge (text/plain); otherwise it responds
`403 Forbidden`.

Set the callback URL in the Meta app dashboard to `https://<your-public-host>/webhook` and use the same token
as `WHATSAPP_VERIFY_TOKEN`.

```sh
curl "http://localhost:8080/webhook?hub.mode=subscribe&hub.verify_token=orderwhatsapp_verify&hub.challenge=123456"
# -> 123456
```

### Send a WhatsApp message

`POST /messages` sends a text message through the WhatsApp Cloud API. The recipient is given as an
`extension` (country code) plus a `number`; the server strips non-digits and joins them (e.g. `+57` + `3138427026`
→ `573138427026`).

```sh
curl -X POST http://localhost:8080/messages \
  -H "Content-Type: application/json" \
  -d '{"extension":"+57","number":"3138427026","message":"Hola desde la API"}'
```

Success (`200`):

```json
{ "messageId": "wamid.HBg...", "to": "573138427026" }
```

Errors: `400` for invalid `extension`/`number`/`message`, `503` when the access token or phone number id is not
configured, `502` when the Graph API rejects the request (the response includes a `detail` field).

### Order-taking agent (Groq + tools)

Incoming WhatsApp messages are handled by an **LLM agent with tool calling** instead of a scripted state machine.
The agent queries the database **directly** through in-process tools (no HTTP round-trips) and only asks the
customer what it actually needs.

- API key from `TOKEN_GROK` (or `GROQ_API_KEY`). Default model `openai/gpt-oss-120b` (override with
  `GROQ_MODEL`). `reasoning_effort` is only sent for `gpt-oss` models. Note: Groq rotates models; check
  `GET https://api.groq.com/openai/v1/models` if a model stops working.
- Tools (all read/write the DB through repositories): `buscar_productos`, `ver_producto`,
  `agregar_producto(productoId, cantidad, opciones)`, `quitar_producto`, `ver_pedido`, `info_tienda`,
  `confirmar_pedido(tipo, nombre, direccion, pago)`.
- The persona/rules live in [`prompts/order-agent.md`](./server/src/main/resources/prompts/order-agent.md) and are
  loaded as the system prompt at startup.
- Conversation history is stored per customer in `session_messages` (last 20 messages sent to the model); the
  session expires after **30 minutes** of inactivity.
- Duplicate deliveries of the same Meta message id are ignored.
- Prices are never invented: the agent must call the tools. Product options (steps/inputs) are asked only when
  the product has them and the customer did not specify.

Conversation flow:

1. The customer asks for one or more products ("quiero una gaseosa y unas papas"); the agent looks each one up
   and adds it. It asks again only when needed (not found, ambiguous, missing options or quantity).
2. When done adding, it asks **"¿Quieres algo más?"**.
3. Then it asks **"¿Es para comer en el lugar o para domicilio?"**.
   - **Local**: it shows a summary and asks to confirm; on "sí" the order is created.
   - **Domicilio**: it asks for the receiver's **name**, the **address** and the **payment method**
     (*Efectivo* / *Transferencia*), then shows a summary and asks to confirm.
4. Confirmed orders are saved as `PENDING`, broadcast over the live socket and pushed to the store.

### Orders

The `orders` table stores the order **header** (`store_id`, `customer_phone`, `customer_name`,
`delivery_address`, `payment_type`, `total`, `status`, timestamps). The products of the order live in
`order_items` (`order_id`, `product_id`, `product_name`, `unit_price`, `selected_inputs`, `step_name`,
`quantity`), so a single order can hold **several products**.

Order status lifecycle: `DRAFT` (being built) → `PENDING` (just placed) → `IN_KITCHEN` → `ON_THE_WAY` →
`DELIVERED`.

Order endpoints (both require `Authorization: Bearer <jwt>`; the store is the authenticated user's):

| Method | Path                   | Body / result |
|--------|------------------------|---------------|
| GET    | `/orders`              | list of the store's orders (newest first), with their items |
| PATCH  | `/orders/{id}/status`  | `{ "status": "IN_KITCHEN" }` |

```sh
curl http://localhost:8080/orders -H "Authorization: Bearer <jwt>"

curl -X PATCH http://localhost:8080/orders/12/status \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"status":"ON_THE_WAY"}'
```

Valid statuses: `PENDING`, `IN_KITCHEN`, `ON_THE_WAY`, `DELIVERED`.

### Live orders (WebSocket + sequence)

Every order gets a monotonic **`sequence`** (a DB autoincrement column), so clients can measure how far the
stream is and resume after a disconnect.

| Channel | Path | Description |
|---------|------|-------------|
| WS  | `/orders/ws?token=<jwt>` | Live stream of new orders for the authenticated user's store. Each frame is an `OrderResponse` JSON with its `sequence`. The socket stays open (no retry loop). |
| GET | `/orders/sequence` | Current highest sequence: `{ "sequence": 42 }`. |
| GET | `/orders/since/{sequence}` | Orders of the store with `sequence > {sequence}`, ascending (catch-up after reconnect). |

Flow: the client connects to `/orders/ws`, stores the last `sequence` it saw, and if it disconnects it reconnects
and calls `/orders/since/{lastSequence}` to fetch what it missed. A new order is broadcast to every socket of that
store the moment it is placed.

Order flow (WhatsApp): the customer chats with the order-taking agent, which builds the order with the tools
described above and creates it after confirmation with status **PENDING**. The total is computed from all lines
(product price plus the chosen inputs). The client is given the **order number** (the autoincrement id).

### Stores and product filtering

The `stores` table holds the per-store configuration: `id`, `welcome_message`, `address`, `phone`,
`whatsapp_business_phone` (the `display_phone_number` Meta sends in the webhook) and `id_whatsapp`
(the `phone_number_id`).

Products store the store they belong to in `products.store_id` (no pivot table). The store is taken from the
authenticated user, so it is never sent by the client.

Store endpoints:

| Method | Path           | Body |
|--------|----------------|------|
| POST   | `/stores`      | `{ "welcomeMessage": "...", "address": "...", "phone": "...", "whatsappBusinessPhone": "573138427026", "idWhatsApp": "1379699841884103" }` |
| GET    | `/stores`      | - |
| GET    | `/stores/{id}` | - |
| PUT    | `/stores/{id}` | same body as POST |

`whatsappBusinessPhone` is stored as digits (so it matches the webhook's `display_phone_number`).
Creating a store (`POST /stores`) requires `Authorization: Bearer <jwt>` and links the store to the authenticated
user. Creating a store with an existing `whatsappBusinessPhone` or `idWhatsApp` returns `409 store_already_exists`.

```sh
curl -X POST http://localhost:8080/stores \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"welcomeMessage":"¡Hola! Bienvenido a mi tienda","address":"Calle 1 #2-3","phone":"+57 300 0000000","whatsappBusinessPhone":"573138427026","idWhatsApp":"1379699841884103"}'

curl http://localhost:8080/stores
```

`GET /products` is authenticated and returns **only the products of the authenticated user's store** (the `WHERE`
uses `products.store_id`).

`GET /products/catalog` (public) returns the store's full catalog, looked up by the WhatsApp number. If the store
is not identified or has no products, it falls back to **all products**.

`GET /products/filter` returns the products of a store, looked up by its WhatsApp number, optionally filtered
by name. The name filter is lenient: the query is split into significant words (ignoring articles like "una",
"el"), accents and punctuation are removed, and products are matched by word prefix (so `"Una Coca cola"`
finds `"Coca cola"` and `"Coca Cola Zero"`). Products with more word matches are returned first; a blank `q`
returns the full menu.

```sh
curl "http://localhost:8080/products/filter?whatsappBusinessPhone=573138427026&q=cafe"
# or by phone number id:
curl "http://localhost:8080/products/filter?idWhatsApp=1379699841884103&q=cafe"
```

The AI auto-reply uses the same search: for each incoming message it looks up the store by the WhatsApp number
from the webhook and searches that store's products with the client's text, returning a numbered list when there
is more than one match.

### Creating products

The store is **not sent by the client**: `POST /products` requires the `Authorization: Bearer <jwt>` token and
uses the store linked to the authenticated user (set when the user creates their store), storing it in
`products.store_id`.

- No token → `401 unauthorized`.
- The user has no store → `400 store_not_configured`.

```sh
curl -X POST http://localhost:8080/products \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Coca cola","price":2000,"cost":0,"quantity":10,"type":"CREATED"}'
```

`PUT /products/{id}` updates the product and `DELETE /products/{id}` also removes its store links.

Creating a store (`POST /stores`) requires the same JWT and links the new store to the authenticated user, so
their next product creations go to that store. The login/`/auth/me` response includes the user's `storeId`.

### Environment variables

| Variable                 | Default                                        |
|--------------------------|------------------------------------------------|
| `DATABASE_URL`           | - (required; JDBC or `postgresql://`)          |
| `DATABASE_USER`          | - (required)                                   |
| `DATABASE_PASSWORD`      | - (required)                                   |
| `DATABASE_MAX_POOL_SIZE` | `5`                                            |
| `JWT_SECRET`             | development default (change in production)     |
| `JWT_ISSUER`             | `watsapp-order`                                |
| `JWT_AUDIENCE`           | `watsapp-order-clients`                        |
| `JWT_REALM`              | `watsapp-order`                                |
| `JWT_EXPIRATION_MINUTES` | `60`                                           |
| `FIREBASE_PROJECT_ID`    | - (required for `/auth/federated`)             |
| `WHATSAPP_VERIFY_TOKEN`  | `orderwhatsapp_verify` (change in production)  |
| `WHATSAPP_ACCESS_TOKEN`  | - (required for `POST /messages`; alias `WHATAPP_PERMANT`) |
| `WHATSAPP_BUSINESS_ACCOUNT_ID` | `1415775780434889` (WhatsApp Business Account id) |
| `WHATSAPP_PHONE_NUMBER_ID` | `1379699841884103` (sender phone number id)  |
| `TOKEN_GROK`             | - (required for the WhatsApp AI auto-reply)    |
| `HOST` / `PORT`          | from `application.conf` (`ktor.deployment`)    |

Database credentials are read **only** from environment variables; `application.conf` no longer contains a URL, user, or password.

Set the variables in the environment where the app runs: the service environment on Render (see `render.yaml`) or exported locally.

`DATABASE_URL` accepts either a JDBC URL (`jdbc:postgresql://...`) or a Render-style connection string (`postgresql://user:password@host/database`). The latter is converted to JDBC automatically and SSL is enabled for non-local hosts.

- Running on Render: use the **Internal Database URL** (internal hostname, only resolves inside Render).
- Running locally: use the **External Database URL** (host includes the domain, e.g. `...oregon-postgres.render.com`, plus `?sslmode=require`). The internal hostname does **not** resolve outside Render.

### Local database

```sh
docker compose up -d
export DATABASE_URL=jdbc:postgresql://localhost:5432/watsapporder
export DATABASE_USER=postgres
export DATABASE_PASSWORD=postgres
export JWT_SECRET=local-development-secret
./gradlew :server:run
```

### Example

```sh
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123","name":"User"}'

curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123"}'

curl http://localhost:8080/auth/me -H "Authorization: Bearer <token>"
```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
# WatsAppOrder
