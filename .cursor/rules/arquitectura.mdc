---
description: Arquitectura backend OrderWhatsApp (data / domain / routes)
alwaysApply: true
---

# Arquitectura

## Flujo de capas

```
routes
  -> domain/usecase
    -> domain/repository (interfaz)
      -> data/repositoryImpl
        -> data/database/{tabla}/{Tabla}Dao
          -> tabla
```

## Carpetas

| Qué | Dónde |
|-----|-------|
| Configuración de la base | `data/database/` (`DatabaseConfig`, `DatabaseFactory`) |
| Cada tabla con su DAO | `data/database/{tabla}/` (`{Tabla}Table.kt`, `{Tabla}Dao.kt`) |
| Todos los DTO y mappers | `data/mapper/` |
| Todos los enum | `data/enum/` |
| Implementaciones de repositorio | `data/repositoryImpl/` |
| Interfaces de repositorio | `domain/repository/` |
| Casos de uso | `domain/usecase/` |
| Endpoints | `routes/` |

## Reglas

- El **caso de uso no lleva lógica**: solo delega en el repositorio.
- **Toda la lógica** (validaciones, hashing, tokens, mapeos) va en `data/repositoryImpl/`.
- El **DAO solo accede a la base de datos**; no tiene lógica de negocio.
- Los **DTO** van en `data/mapper/`; nunca en `routes/`, `domain/` ni en la tabla.
- Los **enum** van en `data/enum/`.
- La **configuración y credenciales** se leen solo de variables de entorno.
- Las tablas viven dentro de `data/database/`.
