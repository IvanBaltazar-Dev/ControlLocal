# Inventario Backend Java — foco Base de Datos (32 tablas)

> Documento 3 de 7 · Fase doc-first de la migración. Inventario exhaustivo del esquema (`01_create_schema_controllocal.sql`) tabla por tabla: propósito, PK, FKs, discriminadores/estados, columnas notables, índices y oportunidades de herencia/normalización/redundancia.
> Complementa: análisis de modelado en [`modelo-herencia-y-generalizacion.md`](modelo-herencia-y-generalizacion.md); capas de código (model/dao/bl/rest) resumidas en §10 y en las memorias de Serena (`mem:backend/core`).
> Convención: 🔎 = observación/mejora; los IDs **MEJ-xx** se acumulan en el registro vivo (Doc 1 §7 + anexo + §9 aquí).

Motor: **InnoDB / MySQL (RDS)**. Todas las PK son `BIGINT AUTO_INCREMENT` surrogate salvo que se indique. Casi todas las tablas núcleo llevan `fecha_creacion`/`fecha_actualizacion` (`ON UPDATE CURRENT_TIMESTAMP`).

---

## G1 · Personas y usuarios (7)

**1. persona** — datos comunes de cualquier persona (natural/jurídica).
- PK `id_persona`. Discriminador `tipo_persona` ∈ {N,J}. `tipo_documento` ∈ {D,R,C,P}, `numero_documento` UNIQUE, `correo` UNIQUE, `estado` ∈ {A,I}, `consentimiento_uso_dato`, `foto_clave` (clave opaca de almacén).
- Índices: `(tipo_documento, numero_documento)`, `(estado)`.
- 🔎 Es la raíz de la jerarquía pero **nadie hereda de ella** (ver anexo §A). Estados CHAR(1).

**2. usuario_interno** — quien accede al sistema. PK `id_usuario` + `id_persona` **UNIQUE** FK→persona (1:1 asociación). `nombre_usuario` UNIQUE, `contrasena_hash`, `estado_administrativo` ∈ {A,I}, `rol` ∈ {B,A}. Índices `(id_persona)`,`(estado_administrativo)`,`(rol)`.
- 🔎 `rol` no está atado a la existencia de fila broker/agente (**MEJ-11**). Persona por composición, no herencia.

**3. broker** — especialización de usuario_interno. PK `id_broker` + `id_usuario` **UNIQUE** FK→usuario_interno (ON DELETE CASCADE). `codigo_broker` UNIQUE, `zona`, `fecha_designacion`, `es_administrador` BOOL. Columna generada `broker_admin_unico` STORED + UNIQUE → **garantiza un único broker administrador**.
- 🔎 Herencia real en Java (`extends UsuarioInterno`) pero 1:1 por FK en BD; identidad triple persona/usuario/broker (anexo §A). Patrón de unicidad parcial vía columna generada (**MEJ-21**).

**4. agente_inmobiliario** — especialización de usuario_interno. PK `id_agente` + `id_usuario` **UNIQUE** FK (CASCADE). `codigo_agente` UNIQUE, `zona_asignada`, `fecha_ingreso`, `estado_operativo` ∈ {D,L,N}. Índices `(id_usuario)`,`(estado_operativo)`.
- 🔎 Mismo patrón/observación que broker.

**5. broker_agente** — supervisión broker→agente (quién supervisa a quién). PK `id_broker_agente` + FKs a broker y agente. `fecha_asignacion`, `fecha_fin`, `motivo`, `estado` ∈ {A,I}. Columna generada `id_agente_activo` STORED + UNIQUE → **un solo broker supervisor activo por agente**. CHECK fechas coherentes.
- 🔎 Vigencia por fechas + estado; el histórico de cambios vive en `reasignacion_agente_broker`. Buen patrón (heredar).

**6. propietario** — persona dueña de locales. PK `id_propietario` + `id_persona` **UNIQUE** FK. Sin más columnas propias.
- 🔎 Tabla "delgada" (solo el vínculo). Candidata clara a shared-PK o Party-Role (anexo §A.5). Composición, no herencia.

**7. cliente_interesado** — persona interesada en alquilar. PK `id_cliente` + `id_persona` **UNIQUE** FK. `rubro_comercial`, `consentimiento_contacto`, `consentimiento_uso_dato`.
- 🔎 Igual patrón que propietario. Una misma persona puede ser propietario y cliente (filas en ambas), pero no hay concepto explícito de multi-rol.

---

## G2 · Inmueble / oferta (5)

**8. distrito** — catálogo cerrado. PK `id_distrito`, `nombre` UNIQUE, `provincia` DEFAULT 'Lima'.

**9. local_comercial** — el inmueble ofertado (**tabla-gorda**). PK `id_local`, `codigo_local` UNIQUE, `id_propietario` FK.
- Genéricos de inmueble: `metraje`, `direccion`, `id_distrito` FK(NULL), `tipo_inmueble` ∈ {L,O,D,C,T,X}, `uso` ∈ {C,V,I,M}, `ambientes`, `antiguedad_anios`, `zona_urbanizacion`, `geo_lat/geo_long`, `frente`, `zonificacion`, `numero_estacionamientos`, `cuota_mantenimiento`.
- Específicos comerciales: `rubro_permitido`, `precio_referencial`, `apto_licencia_funcionamiento`, `carga_electrica_kw`. `estado` ∈ {D,N,I}.
- 🔎 **Redundancia distrito**: `distrito VARCHAR(100)` **y** `id_distrito` FK coexisten (**MEJ-13**). 🔎 Falta generalización `Propiedad`/`Inmueble` (**MEJ-12**). 🔎 `tipo_inmueble` en CHAR vs enum `TipoInmueble`/`TipoInmuebleComercial` (**MEJ-14/18**).

**10. foto_local** — galería. PK `id_foto` + `id_local` FK. `clave` (almacén), `nombre_archivo`, `orden`. Índice `(id_local)`. Binario fuera de BD (almacén S3/disco).

**11. publicacion** — versión de anuncio del local. PK `id_publicacion` + `id_local` FK. `canal` ∈ {URBANIA, ADONDEVIVIR, PROPERATI, NEXO_INMOBILIARIO, FACEBOOK, MARKETPLACE, INSTAGRAM, WHATSAPP, WEB_PROPIA, REFERIDO, OTRO}, `url_publicacion`, `version_anuncio`, `titulo_anuncio`, `renta_publicada`, `moneda` ∈ {PEN,USD}, `inversion_pauta`, `codigo_origen`, `estado` ∈ {B,P,S,C}. Índices `(id_local)`,`(estado)`,`(codigo_origen)`.
- 🔎 Esquema rico infrautilizado (Doc 1 §5.2, **MEJ-04**). Estados CHAR pero canal/ moneda VARCHAR: mezcla de estilos.

**12. precio_local** — histórico de precios por hito. PK `id_precio` + `id_local` FK. `hito` ∈ {E,R,U,P,O,A,C}, `moneda` ∈ {PEN,USD}, `monto`, `fecha`. Índice `(id_local, fecha)`. Buen patrón de historial de precios (heredar).

---

## G3 · Captación y prospección (4)

**13. captacion** — encargo del local a un agente; revisada por broker. PK `id_captacion`, `codigo_captacion` UNIQUE. FKs: `id_local`, `id_agente`, `id_broker_revisor`(NULL). `estado` ∈ {P,O,R,A,C,V}, `fecha_revision`, `observacion_revision`, `comision_pactada`, `urgencia`(1-5), `exclusividad`. Columna generada `id_local_activo` STORED + UNIQUE → **una captación activa por local** (invariante clave). Índices ricos: `(estado)`,`(id_agente,estado)`,`(fecha_captacion)`,`(codigo_captacion,estado)`…
- 🔎 `motivo_operacion CHAR(1)` con CHECK **= 'A'** (valor único): columna vestigial (**MEJ-20**).

**14. prospeccion** — pre-captación (agente persigue al propietario). PK `id_prospeccion`, `codigo` UNIQUE. FKs `id_local`,`id_agente`,`id_captacion`(NULL, nace al aceptar). `estado` ∈ {P,C,R,E,S,T,D}, `resultado_propuesta` ∈ {P,A,R,S}, hitos `fecha_contacto/reunion/propuesta/recontacto`. Índices `(estado)`,`(fecha_recontacto)`,`(estado,fecha_recontacto)`,`(id_agente,estado,fecha_recontacto)`… (alerta día-8 sin recontacto).
- 🔎 "Espejo de la oferta" de oportunidad_comercial; las fechas-hito hacen de micro-historial (patrón alternativo a `historial_estado`).

**15. reasignacion_captacion** — histórico de cambio de agente en una captación. PK `id_reasignacion` + FKs captacion(CASCADE), agente_anterior, agente_nuevo, broker. CHECK anterior≠nuevo. Índice `(id_captacion)`.

**16. reasignacion_agente_broker** — histórico de cambio de broker supervisor de un agente (autorizado por broker admin). PK + FKs agente, broker_anterior(NULL=primera), broker_nuevo, broker_administrador. CHECK anterior≠nuevo.
- 🔎 G3 tiene **dos tablas de reasignación** con la misma forma (evento + actores + motivo). Candidatas a un patrón de "evento de reasignación" unificado o a `historial_estado` (**relacionado con MEJ-01/19**).

---

## G4 · Demanda / oportunidad (4)

**17. oportunidad_comercial** — entidad-hub del proceso. PK `id_oportunidad`, `codigo` UNIQUE. FKs `id_cliente`,`id_captacion`,`id_agente`,`id_publicacion_origen`(NULL). `estado` ∈ {A,S,N,F,X}, `fuente_origen` (VARCHAR: PORTAL, REDES_SOCIALES, WHATSAPP, LLAMADA_DIRECTA, REFERIDO, CARTERA_PROPIA, WEB_PROPIA, OTRO), `codigo_origen_capturado`. Columna generada `clave_oportunidad_abierta` = `cliente-captacion` STORED + UNIQUE → **una oportunidad abierta por (cliente,captación)**. Índices compuestos `(id_captacion,estado,fecha_registro)`,`(id_agente,estado,fecha_registro)`.
- 🔎 Estado CHAR pero fuente_origen VARCHAR: mezcla de estilos (**MEJ-17**).

**18. interaccion_comercial** — punto de contacto **polimórfico** por `contexto` ∈ {OPORTUNIDAD, PROSPECCION, CAPTACION, CLIENTE}. 4 FKs nullable (oportunidad/prospeccion/captacion/cliente) + `id_agente`; CHECK obliga exactamente una FK según contexto; `resultado` VARCHAR con **4 dominios distintos según contexto** (CHECK condicional); `canal_contacto` ∈ {L,W,E,P,R,T,O}.
- **Índices**: `(contexto)`, `(fecha_hora)`, uno por FK, y **compuestos `(contexto, id_oportunidad, fecha_hora)`** + equivalentes prospección/captación/cliente. → Las consultas por contexto+entidad **ya están servidas por índice** (corrige el anexo).
- 🔎 Subtipar/particionar sigue siendo válido por **claridad de modelo** (4 máquinas de resultado en una columna) y escala física, no por falta de índices (**MEJ-15**, reevaluado).

**19. visita** — visita al local (casi-hermana de interacción). PK `id_visita` + FKs `id_oportunidad`,`id_agente`. `estado` ∈ {P,G,C,N,R}, `resultado` CHAR (solo si estado='R'), `nivel_interes`(1-5), `objecion_principal`, `opinion_precio`, `proxima_accion`. CHECK "desenlace solo si realizada". Índices `(id_oportunidad,fecha,hora)`,`(id_agente,estado,fecha)`.
- 🔎 Comparte dominio de `resultado` con interacción → familia "touchpoint" a formalizar (**MEJ-15**).

**20. motivo_no_continuidad** — cierre de oportunidad cuando el cliente no sigue. PK + FKs `id_agente`,`id_oportunidad`, y **referencia opcional única** a `id_interaccion`/`id_visita`/`id_solicitud` (CHECK: a lo sumo una). `razon_principal` ∈ {P,U,C,L,N,E,O}. Índices por cada FK.
- 🔎 Otro polimorfismo "origen del cierre" (3 FKs mutuamente excluyentes) — patrón recurrente.

---

## G5 · Solicitud, documentos, evaluación (4)

**21. solicitud_alquiler** — formaliza la oportunidad. PK `id_solicitud`, `codigo` UNIQUE, `id_oportunidad` **UNIQUE** FK (1:1 con oportunidad), `id_agente` FK. `estado` ∈ {G,E,O,A,R,D,C} (registrada/en-evaluación/observada/aprobada/rechazada/descartada/cerrada), `monto_propuesto`, y **condiciones del trato** que el contrato hereda: `plazo_contrato_meses`, `fecha_inicio_contrato`, `forma_pago`, `meses_garantia`, `meses_adelanto`. Índices compuestos `(id_oportunidad,estado,fecha)`,`(id_agente,estado,fecha)`.
- 🔎 Las condiciones del trato viven aquí (no se duplican en contrato) — buena normalización (heredar).

**22. tipo_documento_requerido** — catálogo de documentos exigidos. PK + `tipo_operacion` CHECK **='A'** (vestigial, **MEJ-20**), `tipo_documento`, `obligatorio`, `activo`. UNIQUE `(tipo_operacion, tipo_documento)`.

**23. documento_solicitud** — documento cargado de una solicitud. PK + FKs `id_tipo_documento_requerido`, `id_solicitud`(CASCADE). `nombre_archivo`, `ruta_archivo`, `resultado_revision` ∈ {P,C,O}, `estado` ∈ {R,O,V}. Índices `(id_solicitud)`,`(id_solicitud,estado,tipo)`.
- 🔎 `ruta_archivo` VARCHAR: el binario vive en almacén (clave opaca); revisar coherencia con `foto_local.clave`/`persona.foto_clave` (nombres distintos para el mismo concepto de "clave de almacén").

**24. evaluacion_solicitud** — historial de evaluaciones del broker. PK + FK `responsable_evaluacion`→**broker**, `id_solicitud` FK. `resultado` ∈ {A,R,O}, `tipo_evaluacion` ∈ {P,O,F}. Columna generada `id_solicitud_final` STORED + UNIQUE → **una evaluación final por solicitud**. Índices `(id_solicitud)`,`(responsable_evaluacion)`,`(tipo_evaluacion)`.
- 🔎 Tabla-historial que sí funciona (a diferencia de `historial_estado`): modelo a imitar para trazabilidad transversal.

---

## G6 · Cierre (3)

**25. contrato_alquiler** — formaliza el vínculo/cierre (**minimalista por diseño**). PK + `id_oportunidad` **UNIQUE** FK, `id_solicitud` **UNIQUE** FK(NULL). `fecha_cierre`, `estado_contrato` ∈ {P,D,V,R,F,S,A}, `incidencias`.
- 🔎 No duplica condiciones (viven en solicitud) ni comisión (en comision_liquidacion). Buena normalización (heredar).

**26. comision_liquidacion** — comisión del contrato. PK + `id_contrato_alquiler` FK. `monto`, `moneda` ∈ {PEN,USD}, `monto_agente`, `monto_empresa`, `fecha_cobro`, `forma_pago`, `estado` (VARCHAR: PENDIENTE/PARCIAL/COBRADA/ANULADA). Índices `(id_contrato)`,`(estado)`.
- 🔎 `id_contrato_alquiler` NO es UNIQUE → admite múltiples liquidaciones por contrato (¿intencional? posible split de pagos).

**27. reporte_propietario** — reporte de avance al propietario sobre una captación. PK + FKs `id_captacion`,`id_agente`. `consultas_reportadas`, `visitas_reportadas`, `objeciones_frecuentes`, `ajustes_recomendados`, `canal_envio` ∈ {L,W,E,P,R,T,O}, `periodo_inicio/fin`. Índice `(id_captacion, fecha_reporte)`.

---

## G7 · Requerimientos del cliente (2)

**28. requerimiento_cliente** — búsqueda/criterios del cliente. PK + `id_cliente` FK. `rubro`, `tipo_inmueble` (**VARCHAR**: LOCAL_COMERCIAL, OFICINA, DEPOSITO_ALMACEN, STAND_MODULO, TERRENO_COMERCIAL, OTRO), `renta_min/max`, `moneda`, `metraje_min/max`, `frente_minimo`, `estado` (VARCHAR: ACTIVO/PAUSADO/CERRADO). Índices `(id_cliente)`,`(estado)`.
- 🔎 **`tipo_inmueble` aquí es VARCHAR full-word**, en `local_comercial` es CHAR(1) — mismo concepto, dos codificaciones (**MEJ-18**). Es la contraparte de demanda del `local_comercial` (matching oferta↔demanda).

**29. requerimiento_distrito** — M:N requerimiento↔distrito (zonas buscadas). **PK compuesta** `(id_requerimiento, id_distrito)` + FKs (requerimiento CASCADE). Única tabla con PK natural compuesta (patrón de tabla puente correcto).

---

## G8 · Transversales / trazabilidad (3)

**30. historial_estado** — auditoría **polimórfica** de cambios de estado. PK + `entidad_tipo` VARCHAR ∈ {PROSPECCION, CAPTACION, OPORTUNIDAD, INTERACCION, VISITA, SOLICITUD_ALQUILER, INMUEBLE, PUBLICACION, CONTRATO_ALQUILER}, `entidad_id`, `estado_anterior/nuevo`, `id_usuario` FK, `fecha_evento`, `observacion`. Índices `(entidad_tipo,entidad_id)`,`(fecha_evento)`.
- 🔎 **Trazabilidad parcial y write-only: solo INMUEBLE se escribe; nada lo lee** (Doc 1 §5.1, **MEJ-01/02/03**). Sin FK real por polimorfismo.

**31. tarea** — tareas/recordatorios del agente (**polimórfica**). PK + `tipo` (12 valores), `entidad_tipo` (12 valores: incluye CLIENTE_INTERESADO/PROPIETARIO/REQUERIMIENTO además de los 9 de historial), `entidad_id`, `id_agente` FK, `estado` (PENDIENTE/EN_PROCESO/COMPLETADA/VENCIDA/CANCELADA), `prioridad` (BAJA/MEDIA/ALTA), fechas programada/recordatorio/completada. Índices `(id_agente,estado)`,`(id_agente,estado,fecha_programada)`,`(entidad_tipo,entidad_id)`.
- 🔎 Vocabulario `entidad_tipo` **diverge** del de historial_estado/alerta (**MEJ-19**).

**32. alerta** — avisos del flujo comercial (**polimórfica**). PK + `tipo` (17 valores: SIN_RESPUESTA, OFERTA_POR_VENCER, CAPTACION_CREADA, COMISION_COBRADA…), `severidad` (INFO/MEDIA/ALTA), `entidad_tipo` (9 valores, como historial), `entidad_id`, `id_agente` FK, `mensaje`, `estado` (ACTIVA/ATENDIDA/DESCARTADA). Índices `(id_agente,estado)`,`(entidad_tipo,entidad_id)`.
- 🔎 Tercer uso del patrón `(entidad_tipo, entidad_id)` (historial/tarea/alerta) con vocabularios ligeramente distintos (**MEJ-19**).

---

## 9. Hallazgos transversales y nuevas mejoras

- **Dos "eras" de codificación** (🔎 **MEJ-17**): el núcleo (persona, usuario, local, captacion, oportunidad, visita, solicitud, documento, evaluacion, contrato, precio) usa **`CHAR(1)` + CHECK**; las tablas más nuevas (publicacion canal, requerimiento, tarea, alerta, comision, oportunidad.fuente) usan **`VARCHAR` full-word**. Homogeneizar hacia enum/catálogo.
- **`tipo_inmueble` codificado dos veces** (🔎 **MEJ-18**): CHAR en local_comercial vs VARCHAR en requerimiento_cliente + dos enums Java. Catálogo único de tipo de inmueble.
- **Patrón polimórfico `(entidad_tipo, entidad_id)`** en 3 tablas (historial_estado, tarea, alerta) con **vocabularios divergentes** y sin FK real (🔎 **MEJ-19**). Unificar el vocabulario y considerar catálogo de tipos de entidad + estrategia de integridad.
- **Columnas vestigiales de valor único** (🔎 **MEJ-20**): `captacion.motivo_operacion` y `tipo_documento_requerido.tipo_operacion` con CHECK `='A'`.
- **Unicidad parcial vía columnas GENERATED STORED** (🔎 **MEJ-21**): `broker_admin_unico`, `broker_agente.id_agente_activo`, `captacion.id_local_activo`, `oportunidad.clave_oportunidad_abierta`, `evaluacion.id_solicitud_final`. Patrón potente pero **MySQL-específico**; documentar para portabilidad (índices únicos filtrados en otro motor).
- **"Clave de almacén" con 3 nombres** (🔎 **MEJ-22**): `persona.foto_clave`, `foto_local.clave`, `documento_solicitud.ruta_archivo` designan el mismo concepto (referencia opaca al almacén) con nombres/semántica distintos. Homogeneizar.
- **Reasignaciones duplicadas** (🔎 relacionado **MEJ-19**): `reasignacion_captacion` y `reasignacion_agente_broker` comparten forma (evento+actores+motivo); candidatas a patrón de evento unificado o a `historial_estado`.

**Fortalezas a heredar** (no romper en el rediseño): indexado compuesto por `(actor, estado, fecha)` muy completo; contrato minimalista sin duplicación; condiciones del trato centralizadas en solicitud; `evaluacion_solicitud` como modelo de tabla-historial que sí funciona; unicidad parcial por columna generada; historial_estado con diseño polimórfico correcto (solo falta cablearlo).

## 10. Capas de código (resumen — detalle en `mem:backend/core`)
Reactor Maven Java 21, orden `model → dao → db-manager → bl → rest`. **model**: 32 entidades + ~45 enums (por dominio comercial/inmueble/persona/usuario). **dao**: interfaz `XxxDAO` + `XxxDAOImpl extends AbstractJdbcCrudDAO` (JDBC puro, `JdbcSupport` helper, `bindInsert/mapRow/validate`). **db-manager**: `com.controllocal.config` (DBManager, DatabaseConfig, TransactionContext). **bl**: `XxxBusinessLogic` + Impl; transacciones vía `bl.support.TransactionRunner`. **rest**: ~26 recursos JAX-RS. *(El inventario detallado de DAO/BL/REST se ampliará según necesidad; el foco de este documento es la BD.)*

---

### Estado
Doc 3 ✅ (foco BD, 32/32 tablas). Correcciones aplicadas al anexo de modelado (índices de interacción). Registro de mejoras vivo: **MEJ-01…MEJ-22**. Siguiente sugerido: profundizar en máquinas de estado (captacion/oportunidad/solicitud) y candidatas a generalización (Doc 4/5).
