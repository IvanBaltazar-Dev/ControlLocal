# `db/migration-prod` — endurecimiento productivo

Location de Flyway activa **solo en el perfil `prod`** (`application-prod.yml`).

## Rango reservado: `V900+` (D-29)

Flyway **funde todas las locations en una sola línea de versiones**. Si esta carpeta usara `V28`,
la siguiente migración de esquema no podría llamarse `V28` y cada entorno acabaría con un historial
distinto. Por eso las migraciones exclusivas de producción arrancan en **`V900`** y suben desde ahí:
el rango nunca colisionará con la línea principal.

## Contenido previsto

| Migración | Qué hace |
|---|---|
| `V900__neutraliza_credenciales_semilla.sql` | Reemplaza el `contrasena_hash` de toda credencial que coincida con los tres hashes del seed por un centinela **no verificable** (`invalidado$…`). `PasswordHasher.verificar` exige el prefijo `pbkdf2$`, así que ese hash **no valida jamás**. La cuenta **sigue activa**: se invalida la contraseña, nunca el usuario (Plan S0 §1.4). |

## Regla que no se rompe

**Ninguna migración de esta carpeta edita una migración aplicada.** V1–V27 son historia; todo cambio
es una migración nueva.

El directorio se mantiene con este README para que la location exista en el classpath del jar.
