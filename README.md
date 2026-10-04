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

### AI auto-reply (Groq)

When `POST /webhook` receives an incoming WhatsApp text message, the server acknowledges Meta immediately and,
in the background, asks the Groq model for a reply and sends it back through the same send logic used by
`POST /messages`. The reply is sent to the message sender (`from`).

- The API key is read from `TOKEN_GROK` (or `GROQ_API_KEY`).
- Default model: `openai/gpt-oss-120b`, with `temperature=1`, `max_completion_tokens=2048`, `top_p=1` and
  `reasoning_effort=medium` (all overridable with `GROQ_MODEL`, `GROQ_TEMPERATURE`,
  `GROQ_MAX_COMPLETION_TOKENS`, `GROQ_TOP_P`, `GROQ_REASONING_EFFORT`).
- Duplicate deliveries of the same Meta message id are ignored.

The conversation is stateful per customer through the `sessions` table:

- **On the first message of a session** the bot sends the store's welcome message **plus the address and phone**
  **once**. Afterwards it does not repeat them, unless the client asks for the address or phone.
- If there are **no messages for 2 minutes**, the session resets: the state is cleared and the opening message is
  sent again on the next message.
- Products are **always looked up with the store filter** (`GET /products/filter` logic). The AI does **not**
  invent products: if there is more than one match, the bot replies with a **numbered list** and resolves the
  client's numeric choice deterministically. If there is no match, it lists the store's menu.
- If the selected product has **steps/ingredients**, the bot lists them and asks which ones are wanted.
- The opening message also tells the client that they can type **`mi catálogo`** at any time to see all products;
  that keyword (and `catálogo`, `menu`, `qué venden`, ...) returns the catalog as a numbered list. It uses the
  products endpoint and, if the store has no linked products, falls back to all products.

### Orders

The `orders` table stores the order and is related to the store (`store_id`), with `customer_phone`,
`product_id`, `product_name`, `unit_price`, `selected_inputs`, `quantity`, `total`, `customer_name`,
`delivery_address`, `payment_type`, `status` (`DRAFT`/`PLACED`), `created_at` and `updated_at`.

Order flow (each step is persisted as a `DRAFT` order linked to the session):

1. The client picks a product (numbered options) and, if it has steps, the ingredients.
2. The bot asks (through the AI) for the **quantity**, the **customer name**, the **delivery address** and the
   **payment type**.
3. When all data is collected, the **total** is computed (`unit_price * quantity`), the order is marked
   `PLACED` and a summary is sent to the client.

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
