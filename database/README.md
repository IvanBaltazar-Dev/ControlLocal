# Base de datos

La carpeta `database` queda reducida a cuatro scripts SQL principales. El
flujo normal para una base nueva es ejecutar `00`, `01`, `02` y, si se quiere
data de prueba completa, `03`.

## Orden de ejecucion

1. `00_recreate_database_controllocal.sql`
   Elimina y vuelve a crear la base `controllocal`. Es destructivo.
2. `01_create_schema_controllocal.sql`
   Crea el esquema final completo, con tablas, claves, restricciones e indices.
3. `02_seed_base_data.sql`
   Carga catalogos obligatorios y usuarios internos de prueba.
4. `03_seed_demo_data.sql` (opcional)
   Carga una muestra operativa amplia para desarrollo y pruebas.

Los scripts `02` y `03` son idempotentes: pueden ejecutarse nuevamente sin
duplicar filas demo.

## Usuarios de prueba

`02_seed_base_data.sql` deja creados estos accesos:

| Rol | Usuario | Contrasena |
| --- | --- | --- |
| Admin | `admin@controllocal.test` | `Admin2026` |
| Broker supervisor | `rsalas` | `Broker2026` |
| Broker supervisor | `psoto` | `Broker2026` |
| Agente | `vmora` | `Agente2026` |
| Agente | `jruiz` | `Agente2026` |
| Agente | `ltorres` | `Agente2026` |
| Agente | `creyes` | `Agente2026` |

Las contrasenas no se guardan en texto plano. El seed almacena hashes PBKDF2
precalculados para estos usuarios demo.

## Datos demo

`03_seed_demo_data.sql` agrega propietarios, locales, captaciones, clientes,
requerimientos, oportunidades, interacciones, visitas, solicitudes, documentos,
evaluaciones, contrato, comision, reportes, tareas, alertas e historial.

Tambien deja dos escenarios utiles para probar pantallas de administracion:

- `AGE-004` queda reasignada de `BRK-001` a `BRK-002`.
- `CAP-DEMO-003` queda reasignada de `AGE-001` a `AGE-002`.

El esquema `01` ya contiene las restricciones finales de alquiler comercial,
visitas y alertas, por lo que no hay migraciones sueltas para una base nueva.
