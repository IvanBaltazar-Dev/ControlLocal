# `db/migration-dev` — seed de desarrollo y E2E

Location de Flyway activa **solo en los perfiles `dev` y `test`**
(`application-dev.yml` / `application-test.yml`).

Aquí vive el seed **futuro** de desarrollo. Una base productiva **no nace con las 21 cuentas
conocidas**: en `prod` esta location no se carga.

## Reglas

- **V3 no se toca.** El seed actual ya está aplicado como migración versionada y es historia; lo
  que se separa es el seed *futuro* (Plan S0 §1.5).
- Las migraciones de esta carpeta comparten la **línea de versiones** con `db/migration`. Para no
  colisionar, use **repetibles** (`R__…`) siempre que pueda: se ejecutan por checksum y no
  consumen número de versión.
- Los 13 scripts de `backend-spring/verificacion/` entran con las credenciales sembradas y corren
  contra bases efímeras en perfil `dev`/`test`. **No hay que reescribirlos.**

El directorio se mantiene con este README para que la location exista en el classpath del jar.
