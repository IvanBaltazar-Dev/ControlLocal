# Base De Datos

La carpeta `database` contiene los scripts necesarios para crear una base ControlLocal reproducible. Tambien documenta el sustento del modelo: por que existe cada entidad, que papel cumplen sus atributos y para que sirven los enums.

## Contenido

| Archivo | Uso |
| --- | --- |
| `00_recreate_database_controllocal.sql` | Elimina y recrea la base `controllocal`. Es destructivo. |
| `01_create_schema_controllocal.sql` | Crea tablas, claves, restricciones e indices. |
| `02_seed_base_data.sql` | Carga catalogos obligatorios y usuarios demo. |
| `03_seed_demo_data.sql` | Carga datos amplios para navegar y probar el sistema. |
| `diagrams/database_diagram_V5.png` | Diagrama visual del modelo. |

## Orden De Ejecucion

1. `00_recreate_database_controllocal.sql`
2. `01_create_schema_controllocal.sql`
3. `02_seed_base_data.sql`
4. `03_seed_demo_data.sql` si necesitas datos operativos de prueba.

Los scripts `02` y `03` estan preparados para poder ejecutarse nuevamente sin duplicar datos demo.

## Usuarios Demo

| Rol | Usuario | Contrasena |
| --- | --- | --- |
| Admin | `admin@controllocal.test` | `Admin2026` |
| Broker supervisor | `rsalas` | `Broker2026` |
| Broker supervisor | `psoto` | `Broker2026` |
| Agente | `vmora` | `Agente2026` |
| Agente | `jruiz` | `Agente2026` |
| Agente | `ltorres` | `Agente2026` |
| Agente | `creyes` | `Agente2026` |

Las contrasenas no se guardan en texto plano. El seed carga hashes PBKDF2 precalculados para estos usuarios.

## Datos Demo

`03_seed_demo_data.sql` agrega datos conectados entre si:

- Propietarios, locales y distritos.
- Captaciones revisadas y pendientes.
- Clientes interesados y requerimientos.
- Oportunidades, interacciones y visitas.
- Solicitudes, documentos y evaluaciones.
- Contrato, comision, reportes, tareas, alertas e historial.

Escenarios utiles:

- `AGE-004` queda reasignada de `BRK-001` a `BRK-002`.
- `CAP-DEMO-003` queda reasignada de `AGE-001` a `AGE-002`.

## Reglas Fuertes Del Esquema

| Regla | Sustento |
| --- | --- |
| Solo un broker administrador activo por base | Evita ambiguedad en gobierno global. |
| Un agente solo puede tener un broker supervisor activo | Permite saber quien responde por la supervision de cada agente. |
| Un local no debe tener dos captaciones activas al mismo tiempo | Evita duplicar oferta comercial sobre el mismo inmueble. |
| Un cliente no debe tener dos oportunidades abiertas sobre la misma captacion | Evita seguimiento duplicado de la misma intencion de alquiler. |
| Solo una evaluacion final por solicitud | Evita cierres contradictorios. |
| El motivo de no continuidad apunta a una sola causa operativa | Permite auditoria clara desde interaccion, visita o solicitud. |
| Montos, fechas y rangos tienen restricciones | Reduce datos imposibles antes de que lleguen a reportes. |

## Modelo De Dominio

### Como Leer Las Entidades

Los atributos siguen un patron comun:

- `id...`: identificador interno, usado para claves primarias y foraneas.
- `codigo...`: identificador legible para operacion, reportes o soporte.
- `estado`: punto actual del ciclo de vida de la entidad.
- `fecha...`: trazabilidad temporal de registro, revision, cierre o actualizacion.
- Referencias a otras entidades: unen el flujo y evitan datos aislados.
- `observaciones`, `motivo`, `descripcion`: explican decisiones humanas que un estado por si solo no cuenta.

### Personas Y Usuarios

| Entidad | Por que existe | Sustento de sus atributos |
| --- | --- | --- |
| `Persona` | Centraliza datos civiles o empresariales comunes para propietarios, clientes y usuarios internos. | `tipoPersona`, `tipoDocumento`, `numeroDocumento` identifican legalmente; `nombresORazonSocial`, `telefono`, `correo` permiten contacto; `estado` evita borrar historico; `consentimientoUsoDato` sustenta tratamiento de datos; fechas auditan alta y cambio. |
| `UsuarioInterno` | Representa a quien entra al sistema y ejecuta acciones. | `persona` vincula identidad real; `nombreUsuario` y `contrasenaHash` soportan autenticacion; `rol` separa broker y agente; `estadoAdministrativo` permite bloquear acceso; fechas auditan ciclo de vida. |
| `Broker` | Modela al supervisor o administrador que revisa y decide. | `codigoBroker` da trazabilidad operativa; `zona` delimita responsabilidad; `fechaDesignacion` marca inicio del rol; `esAdministrador` distingue gobierno global; `captacionesSupervisadas` expresa su alcance. |
| `AgenteInmobiliario` | Modela al operador que registra inmuebles, clientes y actividad comercial. | `codigoAgente` identifica al agente; `zonaAsignada` organiza trabajo; `fechaIngreso` ubica antiguedad; `estadoOperativo` permite saber si puede recibir trabajo; `captacionesAsignadas` conecta su cartera. |
| `BrokerAgente` | Conserva la asignacion vigente o historica entre broker y agente. | `broker` y `agente` definen la relacion; `fechaAsignacion` y `fechaFin` dan vigencia; `motivo` explica cambios; `estado` permite una sola asignacion activa sin perder historial. |
| `ReasignacionAgenteBroker` | Audita cambios de supervision de un agente entre brokers. | `fechaCambio` y `motivo` explican la decision; `agente`, `brokerAnterior`, `brokerNuevo` muestran origen y destino; `brokerAdministrador` registra quien autorizo. |
| `Propietario` | Especializa a una persona como titular de locales comerciales. | `persona` evita duplicar datos personales; `localesComerciales` justifica la relacion uno a muchos entre titular y bienes. |
| `ClienteInteresado` | Especializa a una persona como posible arrendatario. | `persona` contiene identidad/contacto; `rubroComercial` ayuda a calificar compatibilidad con el local; consentimientos separan contacto comercial y uso de datos. |

### Inmuebles Y Publicacion

| Entidad | Por que existe | Sustento de sus atributos |
| --- | --- | --- |
| `Distrito` | Normaliza zonas geograficas para busqueda y requerimientos. | `nombre` y `provincia` ubican el mercado; `activo` permite retirar zonas sin romper historico. |
| `LocalComercial` | Describe el inmueble disponible o administrado. | `codigoLocal`, `direccion`, `distrito`, `zonaUrbanizacion`, `geoLat`, `geoLong` permiten ubicarlo; `metraje`, `frente`, `ambientes`, `antiguedadAnios` describen capacidad fisica; `precioReferencial`, `cuotaMantenimiento` sustentan negociacion; `rubroPermitido`, `zonificacion`, `aptoLicenciaFuncionamiento`, `cargaElectricaKw`, `numeroEstacionamientos` validan uso comercial; `estado` controla disponibilidad; `propietario` conecta titularidad; fechas auditan cambios. |
| `PrecioLocal` | Guarda hitos de precio durante la negociacion. | `idLocal` vincula el local; `hito` explica si el precio fue esperado, recomendado, publicado, ofertado o cerrado; `moneda`, `monto`, `fecha` permiten historico comparativo. |
| `Publicacion` | Registra donde y como se difunde un local. | `inmueble` vincula el anuncio; `canal`, `urlPublicacion`, `versionAnuncio`, `tituloAnuncio` identifican la pieza publicada; `rentaPublicada`, `moneda`, `inversionPauta` permiten medir efectividad; `codigoOrigen` rastrea leads; `fechaPublicacion`, `fechaBaja`, `estado` controlan vigencia. |

### Captacion Y Prospeccion

| Entidad | Por que existe | Sustento de sus atributos |
| --- | --- | --- |
| `Captacion` | Es el compromiso operativo para comercializar un local. | `codigoCaptacion` identifica expediente; fechas de captacion y vigencia delimitan permiso comercial; `comisionPactada` sustenta ingresos esperados; `estado`, `fechaRevision`, `observacionRevision`, `brokerRevisor` documentan control del broker; `localComercial` y `agenteResponsable` asignan bien y operador; `motivoOperacion`, `urgencia`, `exclusividad` califican prioridad y condiciones. |
| `Prospeccion` | Representa una oportunidad temprana de captar un local antes de formalizar captacion. | Fechas de contacto, reunion, propuesta y recontacto permiten seguimiento; `estado` y `resultadoPropuesta` ubican avance; `localComercial`, `agenteResponsable`, `captacion` conectan la prospeccion con la operacion si se concreta. |
| `ReasignacionCaptacion` | Audita el cambio de agente responsable de una captacion. | `captacion`, `agenteAnterior`, `agenteNuevo` muestran el traspaso; `brokerResponsable` autoriza; `fechaCambio` y `motivo` explican por que ocurrio. |
| `ReportePropietario` | Registra reportes enviados al titular del inmueble. | `captacion` y `agente` ubican la cartera; periodo, consultas y visitas reportadas miden actividad; objeciones y ajustes recomendados sustentan decisiones; `canalEnvio` y `fechaReporte` prueban comunicacion. |

### Clientes, Oportunidades Y Seguimiento

| Entidad | Por que existe | Sustento de sus atributos |
| --- | --- | --- |
| `RequerimientoCliente` | Define lo que busca un cliente, incluso antes de una oportunidad especifica. | `rubro`, `tipoInmueble`, rangos de renta y metraje, `frenteMinimo`, `distritos` y `moneda` permiten matching; `estado` indica si sigue vigente; observaciones y fechas dan contexto. |
| `OportunidadComercial` | Une cliente, captacion y agente para conservar trazabilidad de una intencion real de alquiler. | `codigoOportunidad` identifica el caso; `estado`, `fechaActualizacionEstado`, `fechaCierre`, `motivoCierre` explican avance y cierre; `clienteInteresado`, `captacion`, `agenteResponsable` conectan demanda, oferta y operador; `publicacionOrigen`, `fuenteOrigen`, `codigoOrigenCapturado`, `fechaPrimeraConsulta` explican de donde llego el lead. |
| `InteraccionComercial` | Registra llamadas, WhatsApp, correos, reuniones u otros contactos. | `fechaHora`, `canalContacto`, `resultado` y `observaciones` explican que paso; `oportunidadComercial` mantiene el hilo; `agenteResponsable` indica quien contacto; `transcripcionNota` permite guardar detalle adicional. |
| `Visita` | Registra agenda y resultado de visita al local. | `fechaVisita`, `horaVisita`, `estado` controlan agenda; `resultado`, `nivelInteres`, `objecionPrincipal`, `opinionPrecio`, `proximaAccion` califican reaccion del cliente; `oportunidadComercial` y `agenteResponsable` conectan seguimiento. |
| `MotivoNoContinuidad` | Explica por que un cliente deja de avanzar. | `razonPrincipal` clasifica el motivo; observaciones dan detalle; referencias opcionales a oportunidad, interaccion, visita o solicitud ubican el evento que origino el cierre; `agenteResponsable` registra quien lo informo. |

### Solicitud, Documentos Y Evaluacion

| Entidad | Por que existe | Sustento de sus atributos |
| --- | --- | --- |
| `SolicitudAlquiler` | Formaliza la intencion de alquiler cuando el cliente decide avanzar. | `codigoSolicitud`, `fechaRegistro`, `montoPropuesto`, `plazoTentativo`, `fechaVigenciaOferta` registran condiciones; `estado` y `fechaActualizacionEstado` controlan revision; `oportunidadComercial`, `clienteInteresado`, `captacion`, `agenteResponsable` mantienen trazabilidad; documentos y evaluaciones concentran evidencias. |
| `TipoDocumentoRequerido` | Define que documentos se piden para una operacion de alquiler. | `tipoOperacion` fija el contexto; `tipoDocumento` nombra el requisito; `obligatorio`, `activo`, `descripcion` permiten catalogo flexible sin cambiar codigo. |
| `DocumentoSolicitud` | Guarda evidencia documental entregada por el cliente. | `tipoDocumentoRequerido` clasifica; `nombreArchivo`, `rutaArchivo`, `fechaEntrega` ubican el archivo; `resultadoRevision`, `observaciones`, `estado` documentan revision; `solicitudAlquiler` lo vincula al expediente. |
| `EvaluacionSolicitud` | Registra la decision del broker sobre una solicitud. | `fechaEvaluacion`, `resultado`, `observaciones`, `tipoEvaluacion` explican la decision; `responsableEvaluacion` da responsabilidad; `solicitudAlquiler` conecta el dictamen al expediente. |

### Cierre, Control Y Auditoria

| Entidad | Por que existe | Sustento de sus atributos |
| --- | --- | --- |
| `ContratoAlquiler` | Representa el cierre contractual cuando una oportunidad se concreta. | `oportunidad` y `solicitudAlquiler` conectan origen; renta, moneda, plazo, fechas, garantias, adelanto, mantenimiento, reajuste y forma de pago describen condiciones; `fechaCierre`, `comisionGenerada`, `estadoContrato`, `incidencias` permiten seguimiento post cierre. |
| `ComisionLiquidacion` | Registra cobro y reparto de la comision generada. | `contratoAlquiler` conecta el origen; `monto`, `moneda`, `montoAgente`, `montoEmpresa` sustentan liquidacion; `fechaCobro` y `estado` controlan cobranza. |
| `Tarea` | Permite programar acciones operativas pendientes. | `tipo`, `entidadTipo`, `entidadId` conectan la tarea al proceso; `agente`, `descripcion`, fechas de programacion, recordatorio y completado gestionan ejecucion; `estado` y `prioridad` ordenan trabajo. |
| `Alerta` | Notifica situaciones que requieren atencion. | `tipo`, `severidad`, `entidadTipo`, `entidadId` clasifican riesgo; `agente` asigna destinatario; `mensaje` explica accion; `estado`, `fechaGeneracion`, `fechaResolucion` controlan atencion. |
| `HistorialEstado` | Audita cambios de estado de entidades importantes. | `entidadTipo`, `entidadId`, `estadoAnterior`, `estadoNuevo` reconstruyen transicion; `usuario`, `fechaEvento`, `observacion` explican quien, cuando y por que. |

## Enums

Los enums hacen que los estados, tipos y resultados sean finitos y entendibles. Algunos guardan codigos cortos en BD; otros guardan el nombre completo del enum.

### Personas Y Usuarios

| Enum | Valores | Sustento |
| --- | --- | --- |
| `TipoPersona` | `NATURAL(N)`, `JURIDICA(J)` | Diferencia personas naturales y empresas. |
| `TipoDocumentoIdentidad` | `DNI(D)`, `RUC(R)`, `CARNET_EXTRANJERIA(C)`, `PASAPORTE(P)` | Normaliza documentos aceptados. |
| `EstadoActivoInactivo` | `ACTIVO(A)`, `INACTIVO(I)` | Permite desactivar sin borrar historico. |
| `RolUsuarioInterno` | `BROKER(B)`, `AGENTE(A)` | Separa permisos de supervision y operacion. |
| `EstadoOperativoAgente` | `DISPONIBLE(D)`, `LICENCIA(L)`, `NO_DISPONIBLE(N)` | Indica si un agente puede recibir trabajo. |

### Inmuebles

| Enum | Valores | Sustento |
| --- | --- | --- |
| `EstadoLocalComercial` | `DISPONIBLE(D)`, `NO_DISPONIBLE(N)`, `INACTIVO(I)` | Controla si un local puede comercializarse. |
| `EstadoPublicacion` | `BORRADOR(B)`, `PUBLICADO(P)`, `PAUSADO(S)`, `CERRADO(C)` | Controla vigencia del anuncio. |
| `TipoInmueble` | `LOCAL(L)`, `OFICINA(O)`, `DEPARTAMENTO(D)`, `CASA(C)`, `TERRENO(T)`, `OTRO(X)` | Clasifica bienes generales. |
| `UsoInmueble` | `COMERCIAL(C)`, `VIVIENDA(V)`, `INDUSTRIAL(I)`, `MIXTO(M)` | Indica uso permitido o esperado. |
| `TipoInmuebleComercial` | `LOCAL_COMERCIAL`, `OFICINA`, `DEPOSITO_ALMACEN`, `STAND_MODULO`, `TERRENO_COMERCIAL`, `OTRO` | Detalla preferencias comerciales del cliente. |

### Comercial

| Enum | Valores | Sustento |
| --- | --- | --- |
| `OperacionRequerimiento` | `ALQUILER(A)` | Fija el alcance actual del sistema: alquiler comercial. |
| `EstadoCaptacion` | `PENDIENTE_REVISION(P)`, `OBSERVADA(O)`, `RECHAZADA(R)`, `ACTIVA(A)`, `CERRADA(C)`, `VENCIDA(V)` | Controla revision y vigencia de la captacion. |
| `EstadoProspeccion` | `PROSPECTO(P)`, `CONTACTADO(C)`, `REUNION(R)`, `PROPUESTA_ENTREGADA(E)`, `EN_SEGUIMIENTO(S)`, `CAPTADO(T)`, `DESCARTADO(D)` | Modela el embudo previo a captar. |
| `ResultadoPropuesta` | `PENDIENTE(P)`, `ACEPTADA(A)`, `RECHAZADA(R)`, `POSPUESTA(S)` | Resume la respuesta del propietario a una propuesta. |
| `EstadoOportunidadComercial` | `ABIERTA(A)`, `SOLICITUD_CREADA(S)`, `NO_CONTINUA(N)`, `FINALIZADA_EXITOSA(F)`, `FINALIZADA_NO_FAVORABLE(X)` | Modela la vida de la oportunidad. |
| `ResultadoInteraccion` | `PENDIENTE(P)`, `INTERESADO(I)`, `NO_INTERESADO(N)`, `SEGUIMIENTO(S)`, `DESCARTADO(D)` | Resume resultado de contacto o visita. |
| `CanalContacto` | `LLAMADA(L)`, `WHATSAPP(W)`, `EMAIL(E)`, `PRESENCIAL(P)`, `REUNION(R)`, `PORTAL(T)`, `OTRO(O)` | Normaliza origen o medio de contacto. |
| `CanalPublicacion` | `URBANIA`, `ADONDEVIVIR`, `PROPERATI`, `NEXO_INMOBILIARIO`, `FACEBOOK`, `MARKETPLACE`, `INSTAGRAM`, `WHATSAPP`, `WEB_PROPIA`, `REFERIDO`, `OTRO` | Permite medir canales de anuncio y origen. |
| `FuenteOrigen` | `PORTAL`, `REDES_SOCIALES`, `WHATSAPP`, `LLAMADA_DIRECTA`, `REFERIDO`, `CARTERA_PROPIA`, `WEB_PROPIA`, `OTRO` | Explica de donde viene el lead. |
| `EstadoVisita` | `PROGRAMADA(P)`, `REPROGRAMADA(G)`, `CANCELADA(C)`, `NO_REALIZADA(N)`, `REALIZADA(R)` | Controla agenda y ejecucion. |
| `ObjecionVisita` | `PRECIO(P)`, `UBICACION(U)`, `ESTADO(E)`, `CONDICIONES(C)`, `OTRA(O)` | Clasifica la objecion principal del cliente. |
| `OpinionPrecio` | `ALTO(A)`, `JUSTO(J)`, `BAJO(B)` | Recoge percepcion de precio. |
| `ProximaAccionVisita` | `NUEVA_VISITA(V)`, `OFERTA(O)`, `SEGUIMIENTO(S)`, `DESCARTADO(D)` | Define el siguiente paso despues de visitar. |
| `MotivoNoContinuidadTipo` | `PRECIO(P)`, `UBICACION(U)`, `CONDICIONES_CONTRATO(C)`, `LOCAL_NO_ADECUADO(L)`, `CLIENTE_NO_RESPONDE(N)`, `ENCONTRO_OTRA_OPCION(E)`, `OTRO(O)` | Explica por que se cierra sin avanzar. |

### Solicitudes Y Cierre

| Enum | Valores | Sustento |
| --- | --- | --- |
| `EstadoSolicitudAlquiler` | `REGISTRADA(G)`, `EN_REVISION(E)`, `OBSERVADA(O)`, `APROBADA(A)`, `RECHAZADA(R)`, `DESISTIDA(D)` | Controla revision formal de solicitud. |
| `TipoDocumentoSolicitud` | `DOCUMENTO_IDENTIDAD(I)`, `FICHA_RUC(R)`, `VIGENCIA_PODER(V)`, `PODER_REPRESENTACION(P)`, `SUSTENTO_ECONOMICO(E)`, `GARANTIA(G)`, `DECLARACION_JURADA(D)`, `OTRO(O)` | Define documentos esperados para sustentar una solicitud. |
| `EstadoDocumentoSolicitud` | `REGISTRADO(R)`, `OBSERVADO(O)`, `VALIDADO(V)` | Controla revision documental. |
| `ResultadoRevisionDocumento` | `PENDIENTE(P)`, `CONFORME(C)`, `OBSERVADO(O)` | Registra dictamen puntual sobre un documento. |
| `ResultadoEvaluacionSolicitud` | `APROBADA(A)`, `RECHAZADA(R)`, `OBSERVADA(O)` | Resume decision del broker. |
| `TipoEvaluacionSolicitud` | `PRELIMINAR(P)`, `OBSERVACION(O)`, `FINAL(F)` | Distingue revisiones previas, observaciones y cierre. |
| `EstadoContrato` | `EN_PROCESO`, `FIRMADO`, `VIGENTE`, `RENOVADO`, `FINALIZADO`, `RESCINDIDO`, `ANULADO` | Controla vida del contrato. |
| `EstadoComision` | `PENDIENTE`, `PARCIAL`, `COBRADA`, `ANULADA` | Controla cobranza de comision. |
| `FormaPago` | `TRANSFERENCIA`, `DEPOSITO_BANCARIO`, `EFECTIVO`, `CHEQUE`, `OTRO` | Normaliza forma de pago contractual. |
| `TipoReajuste` | `NINGUNO`, `ANUAL_FIJO`, `INDEXADO_IPC`, `OTRO` | Describe reajustes de renta. |
| `Moneda` | `PEN`, `USD` | Evita mezclar montos sin moneda. |
| `HitoPrecio` | `ESPERADO(E)`, `RECOMENDADO(R)`, `AUTORIZADO(U)`, `PUBLICADO(P)`, `OFERTADO(O)`, `ACEPTADO(A)`, `CERRADO(C)` | Da contexto al historico de precios. |

### Operacion, Alertas Y Auditoria

| Enum | Valores | Sustento |
| --- | --- | --- |
| `EstadoRequerimiento` | `ACTIVO`, `PAUSADO`, `CERRADO` | Controla si una busqueda del cliente sigue vigente. |
| `TipoTarea` | `SEGUIMIENTO`, `LLAMADA`, `VISITA`, `ENVIO_INFO`, `RECONTACTO`, `REPORTE_PROPIETARIO`, `ENVIAR_REVISION`, `SUBIR_DOCUMENTOS`, `REGISTRAR_CAPTACION`, `REGISTRAR_INTERACCION`, `OTRO` | Clasifica pendientes operativos. |
| `EstadoTarea` | `PENDIENTE`, `EN_PROCESO`, `COMPLETADA`, `VENCIDA`, `CANCELADA` | Controla ejecucion de tareas. |
| `Prioridad` | `BAJA`, `MEDIA`, `ALTA` | Ordena urgencia. |
| `TipoAlerta` | `SIN_RESPUESTA`, `SIN_AVANCE`, `OFERTA_POR_VENCER`, `CONTRATO_POR_VENCER`, `VISITA_PROXIMA`, `CAPTACION_VENCIDA`, `SOLICITUD_REENVIADA`, `SOLICITUD_EVALUADA` | Clasifica eventos que requieren atencion. |
| `EstadoAlerta` | `ACTIVA`, `ATENDIDA`, `DESCARTADA` | Controla ciclo de vida de notificaciones. |
| `Severidad` | `INFO`, `MEDIA`, `ALTA` | Prioriza alertas. |
| `TipoEntidad` | `PROSPECCION`, `CAPTACION`, `OPORTUNIDAD`, `INTERACCION`, `VISITA`, `SOLICITUD_ALQUILER`, `INMUEBLE`, `PUBLICACION`, `CONTRATO_ALQUILER` | Permite que tareas, alertas e historial apunten a distintos objetos. |
| `DesenlaceOportunidad` | `CERRADA_FAVORABLE(F)`, `CAIDA(X)` | Resume desenlace comercial cuando se necesita una etiqueta de resultado. |

## Relacion Entre Scripts Y Modelo Java

El modelo Java vive en:

```text
backend-java/controllocal-model/src/main/java/com/controllocal/model
```

El esquema SQL vive en:

```text
database/01_create_schema_controllocal.sql
```

La regla de mantenimiento es:

1. Si se agrega una entidad Java persistente, debe existir tabla o una razon clara para no persistirla.
2. Si se agrega un enum con codigo, el `CHECK` SQL debe aceptar ese codigo.
3. Si se agrega una columna obligatoria, el seed debe actualizarse para seguir levantando una base nueva.
4. Si una relacion afecta trazabilidad, debe tener clave foranea o historial asociado.

## Como Validar Rapidamente

Despues de ejecutar los scripts:

1. Inicia la API.
2. Llama `GET http://localhost:8080/controllocal/Api/salud`.
3. Inicia sesion con un usuario demo.
4. Lista captaciones, clientes u oportunidades.
5. Abre el frontend en `http://localhost:5232/login`.

La guia completa de ejecucion esta en [../COMO_PROBAR.md](../COMO_PROBAR.md).
