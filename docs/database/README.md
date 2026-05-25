# Database

Estructura recomendada para scripts SQL del proyecto:

- `ddl/`: creacion y evolucion del esquema. No debe contener `INSERT`.
- `dml/`: datos de apoyo, carga inicial o datos manuales de prueba. No debe contener `CREATE TABLE`.
- `tx/`: plantillas de transacciones SQL para ejecutar cargas o procesos manuales.
- `sp/`: procedimientos almacenados y funciones, solo si la capa DAO los usa con `CallableStatement`.

Orden recomendado para una base limpia:

1. `ddl/00_recreate_database_controllocal.sql`
2. `ddl/01_create_schema_controllocal_v3.sql`
3. `dml/01_seed_initial_data.sql`
4. `dml/02_test_data_local_comercial.sql`

Si no quieres borrar datos, no ejecutes el archivo `00_recreate_database_controllocal.sql`.
En ese caso selecciona/crea manualmente la base `controllocal` y luego ejecuta el archivo de esquema.

Las transacciones principales de la aplicacion se manejan desde Java con `TransactionRunner`.
Los scripts en `tx/` quedan para ejecuciones manuales en MySQL Workbench.

## Cambio importante: oportunidad comercial

El flujo comercial ahora incluye `oportunidad_comercial` entre `cliente_interesado`
y `solicitud_alquiler`.

La oportunidad nace cuando un cliente se interesa por una captacion activa. Desde
ese punto se registran interacciones, visitas y posibles motivos de no continuidad.
Si el cliente continua, se crea una solicitud de alquiler asociada a la oportunidad.

Estados de `oportunidad_comercial`:

- `A`: abierta
- `S`: solicitud creada
- `N`: no continua
- `F`: finalizada exitosa
- `X`: finalizada no favorable

Tablas que dependen de la oportunidad:

- `interaccion_comercial`
- `visita`
- `solicitud_alquiler`
- `motivo_no_continuidad`

Las tablas hijas no duplican cliente ni captacion. Esos datos se consultan desde
`oportunidad_comercial`. El agente de una accion comercial puede ser distinto del
agente responsable de la oportunidad, pero la capa de negocio debe validar que
este activo y disponible.

Si ya tienes una base creada con la version anterior, debes recrearla o migrarla
antes de ejecutar los tests manuales, porque estas tablas ahora requieren
`id_oportunidad`.

## Cambio importante: supervision broker-agente

El esquema incluye `broker_agente` para definir que agentes supervisa cada broker
normal. El broker administrador mantiene alcance global por regla de negocio y no
necesita registros en esa tabla para ver o revisar operaciones.

Reglas de alcance:

- Broker administrador: ve y supervisa todo el proceso.
- Broker administrador: puede reasignar agentes entre brokers cuando exista una
  intervencion administrativa.
- Broker normal: registra sus propios agentes y el sistema crea su asignacion
  activa en `broker_agente`.
- Broker normal: ve y opera solo sobre agentes con asignacion activa en
  `broker_agente`.
- Agente inmobiliario: registra y consulta sus propios movimientos.

La tabla `captacion.id_broker_revisor` no define supervision previa; solo guarda
quien reviso la captacion. La supervision operativa se obtiene desde
`broker_agente`.
