# Matriz operación → rol

**Las 180 operaciones REST del backend v2, con quién puede llamarlas y dónde se decide qué ve.**

Este documento es la **fuente de verdad**, no un resumen: `MatrizOperacionRolTest`
(`controllocal-app/src/test/java/com/controllocal/arquitectura/`) lo parsea y **rompe el build**
si el código y la tabla dejan de coincidir. No puede quedar desactualizado, y un endpoint nuevo no
entra sin una fila que declare su decisión de rol.

Está pensado para el SPA Angular: es la entrada directa del estado por rol y de la navegación
(el equivalente de `RouteAccess` del Blazor).

---

## Cómo leer la columna **Roles**

| Valor | Qué significa |
|---|---|
| `PUBLICO` | Sin token. Son siete y están en la lista `permitAll` de `ConfiguracionSeguridad`, que un test compara contra este documento en los dos sentidos. |
| `TODOS` | Autenticado, **sin gate de rol**. Los tres roles entran y lo que cambia es *qué ven*: eso es la columna **Alcance**, que aquí es obligatoria. |
| `AGENTE`, `BROKER`, `TENANT_ADMIN` | Gate explícito (`@PreAuthorize`). Fuera de esos roles: **403** con el mensaje congelado *"No tienes permisos para esta operacion."* |

> **`TENANT_ADMIN` no es el `ADMIN` de antes (Bloque 5, D-S0-17).** Hasta aquí el
> administrador **era un broker con un booleano** (`detalle_broker.es_administrador`) y
> entraba por herencia a 18 operaciones comerciales. Ahora es una **banda propia**, resuelta
> en servidor desde `usuario_organizacion`, bajo la regla *gobernar no es operar*: ve todo su
> tenant **en lectura**, gobierna cuentas y organigrama, y **no firma ningún hecho del
> negocio** — ni aprueba captaciones, ni las cierra, ni conforma documentos, ni evalúa
> solicitudes. De las 18 filas compartidas, **8 cambiaron de dueño**.
>
> **El rol que viaja en el token sigue siendo `ADMIN`** y no aparece en esta tabla: el formato
> está congelado y solo admite tres valores mientras GlassFish conviva (R1). La banda real se
> resuelve por petición y se consulta con `GET /sesion`.
>
> Si una persona gobierna **y** opera, lleva **dos roles explícitos** y la auditoría dice cuál usó.

**87 de las 180 operaciones no llevan gate de rol** (7 públicas + 79 autenticadas). No es un olvido:
en la v1 el control de esas operaciones es de *alcance*, no de *acceso* — todos entran y cada uno
recibe su porción. Por eso la columna **Alcance** es la parte importante de esta tabla, y la que el
SPA necesita para no prometer en pantalla lo que el backend va a devolver vacío.

Dos gates están declarados **a nivel de clase** y por eso valen para todos los handlers del
recurso: `/agentes` (BROKER, TENANT_ADMIN) y `/asignaciones` (TENANT_ADMIN). En `/agentes` **dos
métodos lo estrechan**: `POST` y `PUT` llevan su propio `@PreAuthorize` de `TENANT_ADMIN`, porque
dar de alta y editar agentes pasó a ser gobierno (filas 17 y 18). La tabla lista siempre los roles
**efectivos**, sin importar dónde esté escrita la anotación.

> **Regla de fondo, anterior al rol**: desde V6 el alcance arranca por el **tenant**. El "todo" de
> un TENANT_ADMIN es todo lo de **su** organización, nunca lo de otra corredora. Un id de otro tenant
> responde **404**, no 403. `Alcances.de(actor)` resuelve las dos capas en ese orden.

---

## F0 — Identidad y salud

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/salud` | PUBLICO | Sin datos de negocio. | F0 |
| POST | `/auth/login` | PUBLICO | Mientras D-20 mantenga la convivencia con GlassFish, solo busca credenciales en el tenant `BROX_LEGACY`. | F0 |
| POST | `/auth/logout` | TODOS | **Aditivo (D-S0-12), no existe en la v1.** Logout con efecto en servidor: sella `sesiones_invalidas_desde` de la credencial del actor y responde 204. Alcance implícito y no discutible — **solo puede cerrar su propia cuenta**, porque la persona sale del token, no del cuerpo. Cierra **todas** las sesiones de esa cuenta: sesiones individuales exigirían un `jti` que no cabe en el token congelado. | F0 |
| POST | `/auth/renovar` | TODOS | **Aditivo, no existe en la v1.** Reemite el token del actor y convierte los 30 minutos en un límite de **inactividad**, no absoluto: quien trabaja sigue dentro, quien deja el navegador quieto media hora cae. Alcance implícito y no discutible — **solo renueva su propia sesión**, porque la identidad sale del token y no del cuerpo. No relaja nada: exige un token que el filtro ya validó (firma, caducidad y revocación por `sesiones_invalidas_desde`), así que **no puede resucitar una sesión revocada**; y **no está en las listas de sesión capada**, de modo que una contraseña temporal o un MFA sin enrolar siguen bloqueando en vez de renovarse indefinidamente. La identidad se relee con `identidadDe` para que un rol cambiado o una cuenta desactivada no sobrevivan otros 30 minutos. | S0 |
| GET | `/aviso-privacidad` | PUBLICO | **Aditivo (D-27), no existe en la v1.** Versión vigente del aviso. Público **a propósito**: el titular debe poder leerlo sin cuenta, y el enlace del formulario de alta apunta aquí. Sin parámetros, sin datos personales y **misma respuesta para todos** — a diferencia de `/documentos/contenido`, cumple de verdad las condiciones de una ruta pública. | F0 |
| POST | `/auth/recuperacion` | PUBLICO | **Aditivo (§4.3), no existe en la v1.** Responde **202 siempre**, exista o no la cuenta y venga vacío el cuerpo: cualquier otra cosa convertiría el endpoint en un padrón de usuarios. Público porque quien lo usa **no tiene sesión** — es lo que viene a recuperar. Consume cupo del bloqueo **solo por IP**: contarlo por cuenta dejaría bloquear la cuenta ajena pidiendo su recuperación en bucle, que sería una denegación de servicio dirigida y gratuita. | S0 |
| POST | `/auth/recuperacion/canje` | PUBLICO | **Aditivo (§4.3).** Canjea el token de un solo uso —sirve para `RECUPERACION` y para `INVITACION`— y el **titular** define su clave. `usado_en` se sella en la misma transacción, así que repetirlo no hace nada. Caducado, usado, reemplazado o inventado dan **el mismo error**. | S0 |
| POST | `/perfil/contrasena` | TODOS | **Aditivo (§4.2), no existe en la v1** (H-02: el `PUT` de brokers y agentes **ignoraba** el campo). Exige contraseña **actual** + nueva. Alcance implícito y no discutible: **solo sobre su propia cuenta**, porque la persona sale del token. Responde 204 e **invalida todas las sesiones, incluida la que llama**. | S0 |
| GET | `/sesion` | TODOS | **Aditivo (Bloque 5, R3), no existe en la v1.** Publica la **banda efectiva** del actor (`AGENTE`/`BROKER`/`TENANT_ADMIN`), que **no cabe en el token**: su formato solo admite tres valores y el `ADMIN` que lleva es la banda heredada. Alcance implícito y no discutible — **solo habla de quien pregunta**, porque todo sale de la sesión y no acepta ni un parámetro. Sin gate a propósito: negarle a alguien saber quién es sería absurdo. | S0 |
| POST | `/auth/mfa/desafio` | PUBLICO | **Aditivo (V37, D-S0-22).** Primer paso del login con segundo factor. Responde **200 + el `LoginResponse` congelado** si la cuenta no tiene MFA y **202 + un desafío** si lo tiene, de modo que el cliente usa **un solo camino** sin adivinar lo que no puede saber. Público porque es PRE-sesión por definición; consume el mismo cupo por cuenta e IP que el login. | S0 |
| POST | `/auth/mfa/verificar` | PUBLICO | **Aditivo (V37).** Canjea el desafío por la sesión, con un código TOTP **o** uno de respaldo — el servidor decide cuál es sin que el cliente lo declare. El desafío es su única credencial, muere al primer uso y **no autoriza ningún otro endpoint**. Tres controles de intentos a la vez: 5 por desafío, acumulado por cuenta **que no se reinicia pidiendo desafíos nuevos**, y por IP. | S0 |
| GET | `/perfil/mfa` | TODOS | **Aditivo (V37).** Estado del propio factor y cuántos códigos de respaldo quedan. **Nunca devuelve el secreto**: no existe endpoint que lo relea. Alcance implícito y no discutible — sale de la sesión, así que solo habla de quien pregunta. | S0 |
| POST | `/perfil/mfa` | TODOS | **Aditivo (V37).** Inicia el enrolamiento y devuelve secreto y `otpauth://` **una sola vez**, con `Cache-Control: no-store`. El factor nace PENDIENTE y **caduca a los 15 minutos** si no se confirma. | S0 |
| POST | `/perfil/mfa/confirmar` | TODOS | **Aditivo (V37).** Activa el factor con el primer código y devuelve los **8 códigos de respaldo**, también una sola vez. Cuatro efectos inseparables: ACTIVO, códigos, apagar `debe_enrolar_mfa` e **invalidar las sesiones vivas** —nacieron sin segundo factor—. Si es el primer administrador, la organización cruza a MFA de gobierno. | S0 |
| DELETE | `/perfil/mfa` | TODOS | **Aditivo (V37, D-S0-34).** Revoca el factor propio. Exige **contraseña + código vigente**: una sesión abierta no basta, porque si bastara, robarla equivaldría a quedarse con la cuenta. Se rechaza si dejaría al tenant **sin administrador operativo**. | S0 |
| POST | `/perfil/mfa/codigos` | TODOS | **Aditivo (V37).** Regenera los códigos e **invalida todos los anteriores**, usados o no. Exige contraseña + código vigente. **No** invalida sesiones: regenerar códigos no cambia quién eres ni cómo entras. | S0 |
| POST | `/perfil/elevacion` | TODOS | **Aditivo (V37, D-S0-34).** Token de elevación de **5 minutos**, un solo uso, ligado a credencial + tenant + **acción concreta**. Existe porque el token congelado **no lleva** cuándo se probó el segundo factor: inferirlo de una sesión nacida hace horas sería falso. Viaja en cabecera, nunca en la URL. | S0 |
| DELETE | `/accesos/{idPersona}/mfa` | TENANT_ADMIN | **Aditivo (V37).** Nivel 2 de la recuperación: el gobierno **revoca** el factor de otra persona; no lo ve ni lo fija, y el titular vuelve a enrolarlo. Un BROKER **no** revoca el de nadie, ni el de sus agentes (D-S0-18). Exige **elevación** y **motivo obligatorio**, e invalida las sesiones del afectado. Contra sí mismo se rechaza; si el afectado es el **último administrador operativo**, también — esa es del nivel 3. Otra organización responde **404, no 403**. | S0 |
| GET | `/accesos` | TENANT_ADMIN | **Aditivo (V37).** Padrón de cuentas del **propio** tenant: quién tiene acceso, con qué banda, si su cuenta está activa y si tiene segundo factor. Existe porque las fichas comerciales congeladas identifican por `persona_rol.id` y **todo este recurso habla de la persona**; publicar aquí la correspondencia evita añadir `idPersona` a `AgenteResponse` y `BrokerResponse`, que están congelados. Devuelve **cuántos** códigos de respaldo quedan, nunca los códigos ni el secreto. | S0 |
| POST | `/accesos/{idPersona}/invitacion` | TENANT_ADMIN | **Aditivo (§4.4, D-S0-18).** Gobierno del tenant: un BROKER **no invita**, ni a su propio equipo. Devuelve el token **una sola vez** (en la base solo queda su hash) e invalida la invitación anterior de esa cuenta. Alcance: personas del **mismo tenant**; otra organización responde **404, no 403** — un 403 confirmaría que esa persona existe. | S0 |
| POST | `/accesos/{idPersona}/contrasena-temporal` | TENANT_ADMIN | **Aditivo (§4.4).** La contraseña la **genera el sistema**, no la elige el administrador, y se muestra una sola vez. Deja la cuenta con `debe_cambiar_contrasena` e invalida sus sesiones vivas. Mismo alcance y mismo 404 que la invitación. | S0 |
| — | `/gestion/recuperacion/**` | **FUERA DE ESTA MATRIZ** | **Aditivo (V38).** Recuperación de emergencia. **No es API del producto**: vive en `com.controllocal.web.gestion` —fuera del paquete que este gate escanea— y solo se atiende en el conector ligado a `127.0.0.1`, en un puerto que Docker no publica; pedirla por el puerto público responde **404**. No lleva rol porque **no hay sesión**: quien la usa es un operador local con dos aprobaciones de custodio, no un usuario. No emite token. Apagada por defecto. | S0 |
| GET | `/seguridad/avisos` | TENANT_ADMIN | **Aditivo (V37, §11 · D-S0-49).** Aviso persistente de gobierno: quién tocó factores, accesos o roles en **su propia organización**. Lectura sobre `evento_seguridad`, que es append-only y de un solo escritor — por eso **no se puede atender ni silenciar**, que es justo lo que haría quien acabara de revocar un factor sin permiso. Se filtra a los hechos de gobierno: un tablero ahogado en `LOGIN_OK` deja de avisar. **No publica** `detalle_json` ni el agente de usuario. `Cache-Control: no-store`. | S0 |

## F2 — Oferta (locales comerciales)

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/documentos/contenido` | TODOS | **Dejó de ser público el 2026-08-08 (H-12 cerrado).** Era `PUBLICO` replicando la v1, con el argumento de que la clave era una *capability*; no lo era —es la ruta física, filtra el correlativo y el nombre, tiene 32 bits, no caduca ni se revoca y viaja en el query string, o sea en los access logs—. Por ahí se descargan documentos de identidad. Con el Blazor eliminado desapareció hasta la excusa: el SPA siempre mandó el token. Alcance: la clave sigue siendo lo único que identifica el binario, así que quien tenga sesión y clave lee — acotarlo por dueño es trabajo aparte. | F2 |
| GET | `/locales` | TODOS | Cartera completa de la organización; no se acota por rol. Filtra (`texto`, `estado`), ordena, pagina y cuenta **en SQL**; los filtros son aditivos y opcionales. | F2 |
| GET | `/locales/resumen` | TODOS | Mismo alcance que el listado (tenant, sin rol) y el mismo filtro `texto`, para que los KPI cuadren con la lista. Se calcula con un `group by` en la BD, **no** contando filas descargadas. | F2 |
| GET | `/locales/{id}` | TODOS | Cartera de la organización. | F2 |
| GET | `/locales/mis-locales` | AGENTE | Los locales de **sus captaciones** (RF-004), no los que registró. | F2 |
| POST | `/locales` | AGENTE | Alta en la organización del actor; crea la prospección inicial. | F2 |
| POST | `/locales/posibles-duplicados` | AGENTE | Advertencia no bloqueante dentro del tenant: compara inmuebles del mismo propietario por dirección técnica, unidad/piso compatibles y metraje aproximado. | F2 |
| PUT | `/locales/{id}` | AGENTE | Solo locales que el agente **prospectó o captó**. Un cambio comercial sensible emite alerta al broker. | F2 |
| DELETE | `/locales/{id}` | AGENTE | Baja lógica (estado I); solo locales que el agente **prospectó o captó**. | F2 |
| GET | `/locales/{id}/precios` | TODOS | Colección hija: se alcanza por el id del padre, que sí va filtrado por tenant. | F2 |
| POST | `/locales/{id}/precios` | AGENTE | Hito del histórico sobre un local de sus captaciones. | F2 |
| GET | `/locales/{id}/publicaciones` | TODOS | Colección hija del local. | F2 |
| POST | `/locales/{id}/publicaciones` | AGENTE | Sobre un local de sus captaciones. | F2 |
| PUT | `/locales/{id}/publicaciones/{idPublicacion}` | AGENTE | Sobre un local de sus captaciones. | F2 |
| POST | `/locales/{id}/publicaciones/{idPublicacion}/estado` | AGENTE | Sobre un local de sus captaciones. | F2 |
| GET | `/locales/{id}/fotos` | TODOS | Colección hija del local. | F2 |
| POST | `/locales/{id}/fotos` | AGENTE | Máximo 6 por local; el binario ya está en el almacén. | F2 |
| DELETE | `/locales/{id}/fotos/{idFoto}` | AGENTE | Devuelve la clave para limpiar el almacén. | F2 |

## E4 — Propiedad universal y captura

**El modelo nuevo (D-E4-1, D-E4-2).** `/locales` sigue siendo el alta de la v1 —un local comercial
en alquiler, un propietario, el precio en una columna— y no se toca. `/propiedades` es el modelo
entero: siete tipos, copropiedad con cuotas, atributos gobernados por catálogo y **una o dos
operaciones simultáneas**, cada una con su encargo, su precio y su histórico.

`/captura` es el motor de preguntas, y lo consumen **Angular y KAIROS por igual**: qué se sabe, qué
falta, qué se pregunta ahora y si ya hay suficiente para ejecutar. Ninguno de los dos clientes
puede tener una segunda copia de esas reglas — salen del catálogo.

Dos cabeceras cruzan todo el bloque. `Idempotency-Key` hace que un reintento devuelva lo que
produjo el primer intento en vez de duplicar la propiedad; `X-Origen` (UI, KAIROS, API, SISTEMA)
viaja al evento de dominio y es lo que responde *«quién decidió esto»*.

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| POST | `/propiedades` | AGENTE | Alta universal en la organización del actor, en **una sola transacción**: propiedad, ubicación, titulares, atributos, encargos, condición económica, primer hito `U` y evento. El encargo nace PENDIENTE — el agente registra, el broker decide. | E4 |
| GET | `/propiedades/{id}` | TODOS | Cartera de la organización; un id de otro tenant responde **404**. Devuelve titulares, atributos y encargos con el histórico **separado por operación**. | E4 |
| PUT | `/propiedades/{id}` | AGENTE | Edición parcial sobre la cartera del tenant: lo que llega `null` no se toca. Cambiar el importe **añade** un hito; nunca sobrescribe el anterior. | E4 |
| GET | `/propiedades/catalogo/{tipoPropiedad}` | TODOS | Qué se pregunta para ese tipo, derivado de `catalogo_atributo` (comunes de BROX + privados del tenant). Un TERRENO no devuelve dormitorios. | E4 |
| POST | `/captura` | TODOS | Abre o continúa un borrador del tenant. **No escribe nada del negocio**: sólo anota lo conocido y responde qué falta. | E4 |
| GET | `/captura` | TODOS | Los borradores en curso de la **organización**, no los de quien pregunta: es lo que permite que una captura iniciada por KAIROS la termine otra persona. | E4 |
| GET | `/captura/definicion` | TODOS | Qué campos aplican a un `tipoPropiedad` + `operacion`, en tres familias: comunes, del tipo y de la operación. Sin alcance porque no devuelve datos de nadie — devuelve la **definición**, que sale del catálogo del tenant (`catalogo_atributo`). Existe para que ni Angular ni KAIROS tengan su propia matriz «tipo → campos». | E4 |
| GET | `/captura/{id}` | TODOS | Un borrador del tenant. | E4 |
| POST | `/captura/{id}/ejecutar` | AGENTE | Corre el caso de uso con lo que el borrador sabe. Falla con la lista de lo que falta si no hay suficiente. | E4 |
| DELETE | `/captura/{id}` | TODOS | Descarta el borrador del tenant. No lo borra: que alguien lo empezara también es un hecho. | E4 |
## F2 — Proceso (prospección y captación)

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/prospecciones` | TODOS | `Alcances`: AGENTE las suyas, BROKER las de sus supervisados vigentes, TENANT_ADMIN el tenant. | F2 |
| GET | `/prospecciones/{id}` | TODOS | Mismo alcance que el listado. | F2 |
| GET | `/prospecciones/recontactar` | TODOS | Recontacto vencido **dentro del alcance del actor**. | F2 |
| GET | `/prospecciones/{id}/coincidencias` | TODOS | Matching con vista personal (ver `/captaciones/{idOrCodigo}/coincidencias`). | F2 |
| POST | `/prospecciones` | AGENTE | Alta propia, estado inicial P. | F2 |
| POST | `/prospecciones/{id}/contactar` | AGENTE | Solo sobre prospecciones suyas. | F2 |
| POST | `/prospecciones/{id}/reunion` | AGENTE | Solo sobre prospecciones suyas. | F2 |
| POST | `/prospecciones/{id}/propuesta` | AGENTE | Cable real: **la v1 nunca emite el estado `E`**; deja `S` y marca `fechaPropuesta` + `resultadoPropuesta='P'`. | F2 |
| POST | `/prospecciones/{id}/seguimiento` | AGENTE | Solo sobre prospecciones suyas. | F2 |
| POST | `/prospecciones/{id}/rechazar` | AGENTE | Exige observación (MEJ-03). | F2 |
| POST | `/prospecciones/{id}/descartar` | AGENTE | Solo sobre prospecciones suyas. | F2 |
| POST | `/prospecciones/{id}/captar` | AGENTE | Crea la captación saltándose el alta que avisa: por eso `CAPTACION_CREADA` casi nunca se emite (bug congelado). | F2 |
| POST | `/prospecciones/{id}/marcar-captado` | AGENTE | Enlaza una captación ya creada por el agente. | F2 |
| GET | `/captaciones` | TODOS | `Alcances`: AGENTE las suyas, BROKER las de sus supervisados, ADMIN el tenant. | F2 |
| GET | `/captaciones/{id}` | TODOS | Mismo alcance que el listado; fuera de él, 403. | F2 |
| GET | `/captaciones/codigo/{codigo}` | TODOS | Mismo alcance que `{id}`. | F2 |
| GET | `/captaciones/pendientes` | BROKER, TENANT_ADMIN | **Supervisión.** Bandeja de revisión: las P u O del equipo del broker; el TENANT_ADMIN, las del tenant **y solo para verlas** — decidirlas es la fila siguiente, que ya no alcanza. Ver la cola no produce ningún hecho. | F2 |
| GET | `/captaciones/reasignables` | BROKER, TENANT_ADMIN | **Supervisión.** Las ACTIVAS dentro del alcance. Es el insumo de la reasignación, que el TENANT_ADMIN sí conserva. | F2 |
| GET | `/captaciones/propiedades-equipo` | BROKER, TENANT_ADMIN | **Extensión aditiva** (no existe en la v1). Mismo alcance que el listado —BROKER sus supervisados, ADMIN el tenant— pero **una fila por INMUEBLE**: deduplica por propiedad quedándose con la captación más reciente. Filtra (`texto`, `distrito`), ordena, pagina y cuenta **en SQL**. | F2 |
| GET | `/captaciones/propiedades-equipo/resumen` | BROKER, TENANT_ADMIN | Mismo alcance y mismo `texto` que la lista. Cuenta **inmuebles distintos** en la base, no filas descargadas, y devuelve los distritos disponibles para el filtro. No acepta `distrito`: el resumen es lo que ese filtro acota. | F2 |
| GET | `/captaciones/{idOrCodigo}/coincidencias` | TODOS | **Vista personal**: para un actor no-ADMIN la demanda propia son los clientes que YA tienen oportunidad del equipo, así que un cliente recién creado no aparece aunque case al 100 %. | F2 |
| POST | `/captaciones` | AGENTE | Alta propia sobre un local sin captación ACTIVA. | F2 |
| PUT | `/captaciones/{id}` | AGENTE | Solo las suyas; editar una OBSERVADA la reenvía a PENDIENTE. | F2 |
| POST | `/captaciones/{id}/decision` | BROKER | **Operación comercial — el TENANT_ADMIN dejó de alcanzarla (D-S0-17 fila 5).** Es el juicio profesional sobre un encargo: quién entra a cartera lo decide el broker, no quien administra cuentas. Aprobar/observar/rechazar dentro de su alcance; observar y rechazar exigen observación. | F2 |
| POST | `/captaciones/{id}/reasignar` | BROKER, TENANT_ADMIN | **Las dos cosas a la vez (fila 6).** Reasignar **dentro** del equipo es supervisión y la conserva el BROKER; reasignar **entre** equipos es organigrama, y eso es gobierno del TENANT_ADMIN. Mismo endpoint, **dos alcances distintos**. Evento de actor (tabla-evento), no transición. | F2 |
| POST | `/captaciones/{id}/cierre` | BROKER | **Operación comercial — el TENANT_ADMIN dejó de alcanzarla (fila 7).** Cerrar un encargo tiene efecto sobre disponibilidad y cartera. | F2 |
| GET | `/captaciones/reasignaciones` | BROKER, TENANT_ADMIN | **Supervisión + rastro.** Es el registro de lo que decide la reasignación, que el TENANT_ADMIN conserva. Historial de gobierno, el más reciente primero. | F2 |

## F3 — Demanda

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/clientes` | TODOS | **Catálogo compartido**: ADMIN y AGENTE ven y editan todos los del tenant, sin regla de pertenencia. El único acotado es el BROKER, y su conjunto se deriva de **las oportunidades de su equipo**. | F3 |
| GET | `/clientes/resumen` | TODOS | **Extensión aditiva** (no existe en la v1). Mismo alcance y mismos filtros que el listado, salvo `estado`: es uno de los cubos que devuelve. Cuenta en la base sobre el mismo conjunto de candidatos que pagina la lista, y devuelve los rubros disponibles para el selector. | F3 |
| GET | `/clientes/{id}` | TODOS | Mismo catálogo compartido. | F3 |
| GET | `/clientes/{id}/coincidencias` | TODOS | Matching con vista personal. | F3 |
| GET | `/clientes/{id}/autorizacion` | TODOS | **Aditivo (D-27), no existe en la v1.** Constancia de la autorización de datos para la ficha: estado, cuándo y quién la registró. Mismo alcance que `GET /clientes/{id}` — se resuelve con el mismo `cargarConAcceso`, así que un cliente fuera del alcance del BROKER responde 403 y uno de otro tenant 404. La consulta va por `persona.id`, no por el id del rol: la autorización la dio la persona una vez y cubre todos sus roles. | F3 |
| POST | `/clientes` | AGENTE | Alta en el tenant del actor. | F3 |
| PUT | `/clientes/{id}` | AGENTE | Catálogo compartido: el agente edita cualquiera de su organización. | F3 |
| DELETE | `/clientes/{id}` | AGENTE | Baja lógica. | F3 |
| GET | `/requerimientos/cliente/{idCliente}` | TODOS | Por tenant, colgado del cliente. | F3 |
| POST | `/requerimientos` | AGENTE | Sobre un cliente del tenant. | F3 |
| PUT | `/requerimientos/{id}` | AGENTE | Sobre un requerimiento del tenant. | F3 |
| POST | `/requerimientos/{id}/estado` | AGENTE | Cambio de estado del requerimiento. | F3 |
| GET | `/oportunidades` | TODOS | AGENTE las suyas; **BROKER las de SUS CAPTACIONES**, no las de sus agentes; ADMIN el tenant. Distinto de interacciones **a propósito**. | F3 |
| GET | `/oportunidades/resumen` | TODOS | **Extensión aditiva** (no existe en la v1). Mismo alcance y mismos filtros que el listado, salvo `estado`: son los cubos que devuelve. Cuenta por etapa **en la base**, con un solo `group by` sobre el mismo conjunto que pagina la lista. | F3 |
| GET | `/oportunidades/{id}` | TODOS | Mismo alcance que el listado. | F3 |
| POST | `/oportunidades` | AGENTE | La captación indicada debe ser del agente que registra, o 403. | F3 |
| POST | `/oportunidades/{id}/no-continuidad` | AGENTE | Cierre A→N con motivo tipificado. | F3 |
| POST | `/oportunidades/{id}/cierre-exitoso` | AGENTE | **Responde 400 siempre** y es correcto: el cierre exitoso lo produce la cascada del contrato (F4), no un botón. | F3 |
| GET | `/visitas` | TODOS | AGENTE las suyas; BROKER **las de sus captaciones**; ADMIN todas las del tenant. | F3 |
| GET | `/visitas/resumen` | TODOS | **Extensión aditiva** (no existe en la v1). Mismo alcance y mismo `query` que el listado. Cuenta los cinco estados **en la base** y devuelve los distritos disponibles para el filtro. No acepta `estado` ni `distrito`: son los filtros que acota. | F3 |
| GET | `/visitas/{id}` | TODOS | Mismo alcance que el listado. | F3 |
| GET | `/visitas/proximas` | TODOS | Mismo alcance, ventana futura. | F3 |
| GET | `/visitas/mes` | TODOS | Mismo alcance, agenda del mes. | F3 |
| POST | `/visitas` | AGENTE | Ojo: exige que la oportunidad sea del **propio** agente, **sin** alcance de broker. | F3 |
| PATCH | `/visitas/{id}/reprogramar` | AGENTE | Sobre visitas suyas; queda auditada. | F3 |
| PATCH | `/visitas/{id}/cancelar` | AGENTE | Sobre visitas suyas. | F3 |
| PATCH | `/visitas/{id}/realizar` | AGENTE | Sobre visitas suyas. | F3 |
| PATCH | `/visitas/{id}/no-realizada` | AGENTE | Sobre visitas suyas. | F3 |
| PATCH | `/visitas/{id}/resultado` | AGENTE | Resultado tipificado de la visita. | F3 |
| GET | `/interacciones` | TODOS | AGENTE las suyas; **BROKER por AGENTE SUPERVISADO**; ADMIN todo. Distinto de oportunidades/visitas, que alcanzan por captación: **no unificar**. | F3 |
| GET | `/interacciones/{id}` | TODOS | Mismo alcance que el listado. | F3 |
| POST | `/interacciones` | AGENTE | Polimórfica: cuelga de una de cuatro entidades según `contexto`, garantizado por CHECK en la BD. | F3 |
| PUT | `/interacciones/{id}` | AGENTE | Sobre interacciones suyas. | F3 |

## E3 — Ficha comercial (lectura transversal)

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/clientes/{id}/ficha-comercial` | TODOS | AGENTE ve **solo su historia y sin nombre de agente**; BROKER su equipo o las captaciones que revisa; ADMIN el tenant. Carga inicial parcial, tope de 8 filas. | E3 |
| GET | `/clientes/{id}/ficha-comercial/{section}` | TODOS | Igual que la ficha completa; aliases `page`/`pagina` y `page_size`/`tamano`. | E3 |
| GET | `/propietarios/{id}/ficha-comercial` | TODOS | Mismo criterio que la de cliente. La cabecera responde `cantidadLocales=0` por paridad con el cable. | E3 |
| GET | `/propietarios/{id}/ficha-comercial/{section}` | TODOS | Igual que la ficha completa. | E3 |

## F4 — Solicitud → contrato

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/solicitudes` | TODOS | AGENTE las suyas; **BROKER por AGENTE SUPERVISADO**; ADMIN el tenant. Ojo: contratos alcanzan por captación — son dos reglas distintas y no se unifican. Los filtros `idAgente`, `estado`, `distrito` y `texto` son **aditivos**: omitidos, la respuesta es la del cable v1. `estado=PENDIENTES` no es un estado, es el cubo `{E,O}` de la cola del broker. | F4 |
| GET | `/solicitudes/resumen` | TODOS | **Extensión aditiva** (no existe en la v1). Mismo alcance y mismo `texto` que el listado. Cuenta los siete estados **en la base**, con un solo `group by` sobre el mismo conjunto que pagina la lista, y devuelve los distritos y agentes disponibles para los filtros. No acepta `estado`, `distrito` ni `idAgente`: son justo lo que acota. | F4 |
| GET | `/solicitudes/{id}` | TODOS | Mismo alcance que el listado. | F4 |
| GET | `/solicitudes/codigo/{codigo}` | TODOS | Mismo alcance que `{id}`. | F4 |
| POST | `/solicitudes` | AGENTE | Exige captación ACTIVA y oportunidad ABIERTA; el alta **no** comprueba que la oportunidad sea del agente (cable real). | F4 |
| POST | `/solicitudes/{id}/reenviar` | AGENTE | Solo desde REGISTRADA u OBSERVADA, y exige que el agente tenga broker supervisor activo. | F4 |
| GET | `/solicitudes/{id}/evaluaciones` | TODOS | Historial de la solicitud: lo ve **también el agente dueño**, a diferencia de `/evaluaciones`. | F4 |
| GET | `/solicitudes/{id}/documentos` | TODOS | Por agente, igual que la solicitud. | F4 |
| POST | `/solicitudes/{id}/documentos/archivo` | AGENTE | Subida octet-stream. | F4 |
| PATCH | `/solicitudes/{id}/documentos/{idDoc}/revisar` | BROKER | **Operación comercial — el TENANT_ADMIN dejó de alcanzarla (fila 9).** Juicio sobre un expediente. **D-F4-5 cerrada**: comprueba el alcance del broker y responde **403** donde la v1 respondía 200. Divergencia deliberada. | F4 |
| PATCH | `/solicitudes/{id}/documentos/conformar` | BROKER | **Operación comercial — el TENANT_ADMIN dejó de alcanzarla (fila 10).** Conformidad en bloque, con alcance de broker; separarla de la anterior abriría el hueco que D-F4-5 cerró. | F4 |
| GET | `/evaluaciones` | BROKER, TENANT_ADMIN | **Supervisión.** El broker ve **solo las que él firmó**; el TENANT_ADMIN, las del tenant: auditar qué se aprobó es justo lo que un administrador debe poder hacer, aunque ya no pueda firmarlo. El agente no entra aquí (sí a `/solicitudes/{id}/evaluaciones`). | F4 |
| GET | `/evaluaciones/{id}` | BROKER, TENANT_ADMIN | Mismo alcance que el listado. | F4 |
| POST | `/evaluaciones` | BROKER | **La más sensible de las 18, y el TENANT_ADMIN dejó de alcanzarla (fila 13).** Es la decisión que desemboca en contrato y comisión: firmarla es responsabilidad profesional del broker. El tipo se **deriva del resultado** (el broker no lo elige), pero el request exige `tipoEvaluacion` presente y válido aunque luego lo pise. | F4 |
| GET | `/contratos` | TODOS | AGENTE los suyos vía el agente de la solicitud; **BROKER por CAPTACION supervisada**; ADMIN el tenant. `montoAgente`/`montoEmpresa` solo viajan para ADMIN/BROKER: el agente no ve el reparto. Tamaño por defecto **100**, no 10. Acepta filtros **aditivos** (`texto`, `distrito`, `idAgente`) y `orden=cierre`; omitidos, responde igual que antes —incluido el orden congelado por id descendente—. El filtro por agente usa el de la **solicitud**, que es el que la respuesta publica. | F4 |
| GET | `/contratos/resumen` | TODOS | **Extensión aditiva** (no existe en la v1). Mismo alcance y mismo `texto` que el listado. Suma la comisión y cuenta los cierres **en la base**, no sobre las filas descargadas: con el tope de 100 por página, un total calculado en el cliente sería falso pasados los 100 cierres. Devuelve además los distritos y agentes disponibles para los filtros. No acepta `distrito` ni `idAgente`: son los filtros que acota. | F4 |
| GET | `/contratos/oportunidad/{idOportunidad}` | TODOS | Mismo alcance que el listado. | F4 |
| POST | `/contratos` | AGENTE | Sobre una solicitud APROBADA suya; dispara la cascada de siete efectos que cierra el ciclo. | F4 |
| POST | `/contratos/en-proceso` | AGENTE | Crea el borrador contractual P sobre una solicitud APROBADA propia, sin cerrar todavía la operación. | F4 |
| POST | `/contratos/{idContrato}/firmar` | AGENTE | P → D; completa el snapshot y ejecuta el cierre comercial auditado. | F4 |
| POST | `/contratos/{idContrato}/activar` | AGENTE | D → V con fecha efectiva, actor, rol y motivo. | F4 |
| POST | `/contratos/{idContrato}/finalizar` | AGENTE | V → F y crea una tarea de revisión del inmueble sin reactivarlo. | F4 |
| POST | `/contratos/{idContrato}/rescindir` | BROKER | **BROKER sin ADMIN** *(cambiado 2026-08-06, Bloque 7)*: «el broker decide, el agente registra». Rescindir corta un alquiler **que ya producía efectos** y arrastra consecuencias económicas; no es el registro de un hecho consumado como finalizar por plazo. Un `TENANT_ADMIN` sin rol operativo de broker recibe **403**: administrar el tenant no produce decisiones comerciales (D-S0-17). V → S y crea una tarea de revisión del inmueble **sin reactivarlo**. | F4 |
| POST | `/contratos/{idContrato}/anular` | BROKER | **BROKER sin ADMIN** *(cambiado 2026-08-06, Bloque 7)*, por lo mismo que rescindir: anular deja sin efecto un contrato. Solo P/D → A — **anular un contrato VIGENTE se rechaza**, y esa es la diferencia con rescindir: borraría de la historia un alquiler que existió y por el que se cobró comisión. | F4 |
| POST | `/contratos/{idContrato}/renovar` | AGENTE | V → R y crea un contrato sucesor enlazado, preservando el snapshot anterior. | F4 |
| POST | `/contratos/{idContrato}/comision/asignar` | BROKER | **BROKER sin ADMIN**, a propósito: funciona porque el filtro JWT publica una sola authority `ROLE_<rol>`. | F4 |
| POST | `/contratos/{idContrato}/comision/cobro` | BROKER | **BROKER sin ADMIN**, igual que asignar. | F4 |
| POST | `/contratos/{idContrato}/comision/movimientos` | BROKER | Registra evidencia económica de cobro, pago al agente o reversión, en la moneda de la liquidación. **El ajuste (`A`) ya no se acepta**: nunca tuvo regla económica. Acepta `Idempotency-Key`. | F4 |
| POST | `/contratos/{idContrato}/revision-disponibilidad` | BROKER | Recupera la disponibilidad de un inmueble tras terminar el contrato: `VOLVER_AL_MERCADO` (`A→D`) o `RETIRAR_DEL_MERCADO` (`A→T`), con motivo obligatorio. Alcance por CAPTACIÓN, igual que el resto del recurso. Mismo gate que rescindir: es un hecho comercial sobre el inmueble. | 7.3.2 |

## F6 / F7 — Alertas y bandeja

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/alertas` | TODOS | AGENTE las suyas; BROKER las de sus supervisados; ADMIN el tenant. **No hay columna de destinatario**: la alerta cuelga siempre de un AGENTE y quién la lee lo decide el TIPO. El GET **escribe**: materializa el barrido de recontacto como mucho una vez cada 5 min. | F6 |
| POST | `/alertas/{id}/atender` | TODOS | Sobre alertas en su alcance; la segunda llamada responde `false` sin ser error. | F6 |
| PATCH | `/alertas/{id}/atender` | TODOS | Mismo comportamiento que el POST (los dos verbos son del cable). | F6 |
| GET | `/tareas` | AGENTE | Bandeja **estrictamente personal**. Es el **único recurso del sistema sin acceso de ADMIN**. El GET **escribe**: deriva y reconcilia antes de devolver. Corta en 10 y descarta el resto en silencio. | F7 |
| GET | `/tareas/pendientes` | AGENTE | Igual de personal que `/tareas`. | F7 |
| POST | `/tareas/{id}/cancelar` | AGENTE | Cancelar **la mata para siempre**: el reconcile no la vuelve a crear aunque el disparador siga vigente. No es "más tarde". | F7 |

## E1 — Personas y perfil

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/propietarios` | TODOS | ADMIN y AGENTE ven el catálogo entero del tenant; el **BROKER** se acota por sus **propiedades** (vía captación o prospección), no por oportunidades. `cantidadLocales` es un contador **con alcance**: dos actores ven números distintos del mismo propietario, y es correcto. Los filtros `texto` y `estado` son **aditivos**: omitidos, la respuesta es la del cable v1, orden por id descendente incluido. | E1 |
| GET | `/propietarios/resumen` | TODOS | **Extensión aditiva** (no existe en la v1). Mismo alcance y mismo `texto` que el listado; cuenta en la base sobre el MISMO conjunto que pagina la lista. No acepta `estado`: es el cubo que devuelve. | E1 |
| GET | `/propietarios/{id}` | TODOS | Mismo alcance que el listado. | E1 |
| GET | `/propietarios/{id}/autorizacion` | TODOS | **Aditivo (D-27), no existe en la v1.** Misma constancia y misma forma que la de cliente: es el mismo hecho sobre la misma persona. Alcance idéntico al de `GET /propietarios/{id}`, resuelto por el mismo `cargarConAcceso`. | E1 |
| POST | `/propietarios` | AGENTE | Responde `cantidadLocales` en 0 por paridad con el cable. | E1 |
| PUT | `/propietarios/{id}` | AGENTE | También responde el contador en 0: la v1 no lo recalcula. | E1 |
| DELETE | `/propietarios/{id}` | AGENTE | Baja lógica: la persona queda en estado I. | E1 |
| GET | `/agentes` | BROKER, TENANT_ADMIN | Gate declarado **a nivel de clase**. TENANT_ADMIN el tenant (es su padrón); **BROKER solo los agentes que supervisa**. Los filtros `texto`, `estado`, `estadoOperativo` y `zona` son **aditivos**: omitidos, la respuesta es la del cable v1. El estado administrativo se filtra por la **credencial**, no por el agente. | E1 |
| GET | `/agentes/resumen` | BROKER, TENANT_ADMIN | **Extensión aditiva**. Mismo alcance y mismos filtros que el listado, menos `zona`: es una de las opciones que devuelve. Dos cubos por dos máquinas distintas —administrativo (credencial) y operativo (agente)—, más las zonas del alcance completo para el selector. | E1 |
| GET | `/agentes/{id}` | BROKER, TENANT_ADMIN | **Extensión aditiva** (la v1 no tenía GET individual). Ficha completa: identidad, supervisión vigente, captaciones/oportunidades/solicitudes por estado, cierres y las cuatro magnitudes de comisión por moneda. **El BROKER solo alcanza a los agentes que supervisa hoy**; fuera de su equipo responde 403. Los cierres y el dinero salen de la **atribución histórica de V27** (`contrato_alquiler.id_rol_agente_cierre`), no de la cadena solicitud→agente: un agente que cambió de equipo conserva su historia. | E1 |
| POST | `/agentes` | TENANT_ADMIN | **Gobierno del tenant — el BROKER dejó de alcanzarla (fila 17, D-S0-18: un broker no crea cuentas).** Exige `idBrokerSupervisor` **nuevo y obligatorio**: antes el supervisor era el broker de la sesión, y quien gobierna no supervisa a nadie de quien deducirlo. Crea atómicamente persona + `USUARIO_INTERNO` + rol + credencial + **membresía** + supervisión inicial. Responde contadores comerciales en 0 (rareza del cable). | E1 |
| PUT | `/agentes/{id}` | TENANT_ADMIN | **Gobierno — el BROKER dejó de alcanzarla (fila 18).** Lo editable es identidad administrativa: el `PUT` ya descartaba en silencio documento, usuario, contraseña y código. También responde contadores en 0. | E1 |
| GET | `/brokers` | TODOS | Por **tenant**, sin filtro de rol: los tres roles ven el mismo catálogo de brokers de su organización. | E1 |
| GET | `/brokers/{id}` | TODOS | Por tenant. | E1 |
| GET | `/brokers/{id}/agentes` | TODOS | Equipo vigente del broker. Conserva la rareza del cable: contadores comerciales en 0. | E1 |
| POST | `/brokers` | TENANT_ADMIN | Alta atómica persona + usuario interno + rol + credencial + membresía. Con `esAdministrador`, además concede gobierno (membresía `TENANT_ADMIN` + rol `ADMIN`): **ya no hay límite de administradores por organización**; lo único que sigue siendo único es el booleano heredado que lee GlassFish. | E1 |
| PUT | `/brokers/{id}` | TENANT_ADMIN | Solo el TENANT_ADMIN edita brokers. | E1 |
| GET | `/asignaciones/agentes` | TENANT_ADMIN | Gate declarado **a nivel de clase**. Gobierno: tenant completo. | E1 |
| GET | `/asignaciones/brokers` | TENANT_ADMIN | Gobierno: tenant completo. | E1 |
| GET | `/asignaciones/historial` | TENANT_ADMIN | Evento histórico de reasignación agente→broker (V10). | E1 |
| POST | `/asignaciones/reasignar` | TENANT_ADMIN | Cierra la supervisión anterior y abre la nueva en una sola transacción, respetando el único parcial. | E1 |
| GET | `/perfil` | TODOS | El actor **sobre sí mismo**; no hay alcance que resolver. | E1 |
| PATCH | `/perfil` | TODOS | Solo teléfono: **no hay endpoint de contraseña** en la v1 (la pantalla Blazor era un mock). | E1 |
| POST | `/perfil/foto` | TODOS | Valida extensión (no magic bytes) y guarda la clave opaca en `persona.foto_clave`. | E1 |

## E2 — Reportes al propietario

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/captaciones/{idCaptacion}/reportes-propietario` | TODOS | ADMIN el tenant, BROKER el equipo vigente, AGENTE lo suyo. Un id de otra organización responde 404. | E2 |
| GET | `/captaciones/{idCaptacion}/reportes-propietario/preview` | TODOS | Mismo alcance; los tres agregados se calculan en SQL por captación, periodo y organización. | E2 |
| POST | `/captaciones/{idCaptacion}/reportes-propietario` | AGENTE | **Solo el agente responsable** registra. Ignora consultas/visitas/objeciones si el cliente las manda: son autoritativas del servidor. Reinicia el reloj de 15 días de F7. | E2 |

## E4 — Dashboard, indicadores y seguimiento

| Método | Ruta | Roles | Alcance | Vertical |
|---|---|---|---|---|
| GET | `/dashboard` | TODOS | Alcance y `ambito` por rol. La bandeja embebida sale de `/tareas`, que es solo del AGENTE: para **BROKER y ADMIN viaja vacía, no es un 403**. | E4 |
| GET | `/indicadores/resumen` | TODOS | Alcance **solo por agente responsable**: la captación no amplía el de nadie aquí. Distinto del seguimiento — **no unificar** (D-E4-4). | E4 |
| GET | `/indicadores/avance` | TODOS | RF-017, acumulado (no acepta periodo). Mismo alcance por agente responsable. | E4 |
| GET | `/seguimiento-comercial` | TODOS | Alcance por la **unión** de agente propio y agente de la captación; esa segunda rama existe **solo para el BROKER** (al AGENTE no le suma nada). Las filas **sin fecha encabezan** la lista. | E4 |

---

## Lo que esta tabla deja a la vista

Cinco cosas que sorprenden y **no** son errores; están fijadas por test y replicadas del cable v1:

1. **Cuatro pares de reglas de alcance que no se unifican.** Oportunidades y visitas alcanzan por
   *captación*; interacciones y solicitudes, por *agente supervisado*; contratos, otra vez por
   *captación*; indicadores solo por *agente responsable* y seguimiento por la *unión*. Cada
   intento de unificarlas rompe paridad.
2. **Los dos endpoints de comisión son `BROKER` sin `ADMIN`.** Funciona porque el filtro JWT
   publica una sola authority.
3. **`/tareas` es el único recurso sin acceso de ADMIN**, y es coherente con lo que es: una lista
   de cosas por hacer, no un tablero.
4. **Dos GET escriben**: `/alertas` y `/tareas` reconcilian antes de devolver. Es la forma de tener
   la bandeja al día sin planificador.
5. **`POST /oportunidades/{id}/cierre-exitoso` responde 400 para siempre.** El cierre exitoso lo
   produce la cascada del contrato, no un botón — el SPA no debe ofrecerlo.

## Qué NO cubre

- **Reportes PDF**: fuera del alcance de la migración (D-F5-1, ver
  `decision-reportes-pdf-fuera-de-alcance.md`). Los 5 endpoints `/pdf` de la v1 no tienen fila
  porque no existen en la v2.
- **Reglas de estado**: la tabla dice quién puede llamar y qué ve, no desde qué estado. Las
  máquinas de estado están en los contratos congelados por vertical.
- **RLS**: hoy el aislamiento de tenant es discriminador + filtro en la aplicación (D-24). Cuando
  se active RLS en Postgres, esta tabla no cambia: cambia dónde se aplica.
