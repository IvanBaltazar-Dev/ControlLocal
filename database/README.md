# Base de datos

Los scripts `00` a `04` inicializan una base MySQL vacia. Los scripts `05` y
`06` actualizan instalaciones que ya contienen datos.

## Orden de ejecucion

1. `00_recreate_database_controllocal.sql`
   Elimina y vuelve a crear la base `controllocal`.
2. `01_create_schema_controllocal.sql`
   Crea el esquema final completo, con tablas, claves, restricciones e indices.
3. `02_seed_catalogs.sql`
   Carga los distritos de Lima y los tipos de documento requeridos.
4. `03_seed_initial_users.sql`
   Crea un broker administrador, un broker y un agente. Requiere variables de
   sesion privadas para los usuarios y hashes PBKDF2.
5. `04_seed_demo_data.sql` (opcional)
   Carga una muestra operativa completa para desarrollo y pruebas.
6. `05_restrict_alquiler_comercial.sql` (solo base existente)
   Normaliza operaciones anteriores y restringe captaciones y documentos a
   alquiler comercial sin recrear la base.
7. `06_visita_flujo_estados.sql` (solo base existente)
   Incorpora el estado `No realizada` y separa la asistencia de la visita de
   su resultado comercial.

El archivo `00` es destructivo. Debe ejecutarse solo cuando se quiera empezar
desde cero. Los archivos `02`, `03` y `04` son idempotentes.

## Usuarios iniciales

Los nombres de usuario y los hashes de contrasena no se almacenan en el
repositorio. Antes de ejecutar `03_seed_initial_users.sql`, define en la misma
sesion las variables indicadas al inicio del archivo. Usa valores privados y
hashes generados con `PasswordHasher`.

## Ejecucion rapida en MySQL Workbench

Abre y ejecuta cada archivo completo, respetando el orden numerico. Para una
instalacion minima se ejecutan `00` a `03`; el archivo `04` es la carga demo.

El esquema final contiene 30 entidades de dominio y la tabla puente fisica
`requerimiento_distrito`. En una instalacion nueva no es necesario ejecutar los
scripts `05` y `06`, porque sus restricciones ya forman parte del esquema final.

Las transacciones de la aplicacion se manejan desde Java con
`TransactionRunner`.
