# D-27 — Autorización de datos personales: una sola vez, en el alta

**Fecha de la decisión: 2026-08-04.** · **Estado: APROBADA** (indicaciones consolidadas de
preparación para producción, BLOQUE 0).
**Sustituye el alcance inicial de D-25** (`arquitectura-multitenancy-colaboracion.md` §9.1): aquel
modelo de finalidades múltiples **no se despliega en la primera versión**; se conserva como destino,
no como alternativa abierta.

---

## 1. La regla de producto

> **La autorización no puede dominar la interfaz ni convertirse en una máquina de estados visible
> para el agente.** La persona autoriza **una sola vez**, desde su formulario de registro.

El formulario de alta de **cliente** y de **propietario** lleva una sección breve, al final:

| Elemento | Qué es |
|---|---|
| **Casilla única** | *"La persona autorizó el registro y uso de sus datos para atender su solicitud y gestionar la relación comercial."* |
| **Enlace** | "Consultar el aviso de privacidad" (abre la versión vigente; no es un modal obligatorio) |

**Dos elementos. Eso es todo lo que el agente ve y todo lo que el agente escribe.**

Debajo de la casilla, cuando no está marcada: *"Sin esta autorización no se puede completar el
registro y no se guardará ningún dato."*

> **Corrección del 2026-08-05 (tarde) — fuera el desplegable de canal.** Hubo un tercer elemento,
> *Canal de autorización*, con seis opciones. Se retiró: le pedía al agente que **describiera la
> pantalla en la que ya estaba**, que es fricción sin información —siempre iba a ser el formulario—
> y una decisión más antes de poder guardar. El canal **se sigue registrando** (§1.1); lo que
> desaparece es la pregunta.

### 1.1 Lo que el sistema registra solo

Sin intervención del usuario, y sin pedirle ni un dato más:

| Campo | De dónde sale |
|---|---|
| fecha y hora | reloj del servidor |
| organización | tenant de la sesión |
| usuario que registró | actor de la sesión |
| canal | valor técnico `FORMULARIO_BROX`, puesto por el backend (**no se pregunta ni se muestra**) |
| versión del aviso de privacidad | versión vigente en ese instante |
| base del tratamiento | derivada (§3.2) |
| finalidad | `OPERACION_SERVICIO`, fija en esta versión |

El canal se conserva como columna —`evidencia_autorizacion.canal` es `NOT NULL`— porque prepara el
día en que existan **otros caminos de entrada** (WhatsApp de KAIROS, portal del titular): cada uno
sellará el suyo, y entonces el dato sí distinguirá algo. Hoy, con un único camino, preguntarlo solo
podía introducir ruido.

**Prohibido explícitamente:** que el agente teclee fechas, versiones legales, códigos internos,
canales o finalidades técnicas. Si un campo de esos aparece en pantalla, la pantalla está mal.

---

## 2. Si la persona no autoriza

**No se crea nada identificable.** En concreto, no se crea:

- la `persona`;
- el cliente, el propietario, la prospección ni el requerimiento;
- ningún registro con nombre, DNI, teléfono, correo, dirección u observaciones identificables;
- **ni una fila de persona marcada como "no autorizó"** — eso sería exactamente el dato que no se
  puede guardar.

Lo único que puede quedar es un **evento técnico anónimo**:

```
formulario no completado · fecha · canal · motivo = AUTORIZACION_NO_OTORGADA
```

Sin `persona_id`, sin teléfono, sin nombre, sin nada que permita reidentificar. Sirve para saber
cuántas altas se caen por esta razón, no para saber quiénes.

> **Divergencia deliberada del contrato congelado (D-27-b).** La v1 acepta
> `consentimientoUsoDato = false` y **crea la persona igualmente**. La v2 **rechaza el alta**. Es del
> mismo tipo que D-F4-5 (donde la v2 responde 403 y la v1 respondía 200) y **no rompe al Blazor**,
> porque el Blazor habla con GlassFish, no con este backend. El único cliente del v2 es el SPA, y el
> SPA no permitirá enviar el formulario sin la casilla marcada.

---

## 3. Modelo de datos

### 3.1 El hallazgo que cambia la implementación

**El modelo complejo de D-25 ya está creado en el esquema y no lo usa nadie.** V6 (aplicada) creó
cuatro tablas —`finalidad_tratamiento`, `aviso_privacidad_version`, `evidencia_autorizacion` y
`autorizacion_tratamiento_evento` (append-only)— con sus cuatro entidades de dominio, **sin un solo
repositorio ni service que las lea o escriba**.

Y lo que hoy sí está en uso son **dos booleanos**, no tres, los dos dentro del contrato congelado:

| Booleano | Dónde | Quién lo expone |
|---|---|---|
| `persona.consentimiento_uso_dato` | V1 | `ClienteRequest/Response`, `PropietarioRequest/Response` |
| `detalle_cliente.consentimiento_contacto` | V7 | `ClienteRequest/Response` |

### 3.2 Decisión de almacenamiento — **reutilizar V6** ✅ CONFIRMADA (2026-08-05)

*"Reutilizar las estructuras de autorización existentes. No crear tablas paralelas."*

La estructura que se había esbozado como `autorizacion_datos` y la tabla que ya existe
(`autorizacion_tratamiento_evento`) **describen lo mismo**. Crear la primera dejaría dos estructuras
para un solo hecho, que es justo lo que el mismo encargo prohíbe para `usuario_organizacion`.
**No se crea ninguna tabla nueva**: V28 solo añade dos columnas y ajusta el catálogo.

Se reutiliza la de V6 con estos añadidos mínimos:

| Dato | Dónde vive | Acción |
|---|---|---|
| `id`, `organizacion_id`, `persona_id` | `autorizacion_tratamiento_evento` | ya existe |
| base del tratamiento | `base_juridica` | ya existe |
| finalidad | `finalidad_codigo` | **se reutiliza `OPERACION_SERVICIO`** (§3.3) |
| otorgada en | `ocurrido_en` con `evento = 'OTORGADO'` | ya existe |
| canal | `evidencia_autorizacion.canal` | ya existe; lo sella el backend (`FORMULARIO_BROX`), no se pregunta |
| versión del aviso | `version_aviso` | ya existe |
| **registrada por** | — | **columna nueva** (`id_persona_rol` del actor) |
| revocada en | `ocurrido_en` con `evento = 'REVOCADO'` | ya existe (append-only) |
| **motivo de revocación** | — | **columna nueva** |

Ventaja que no cuesta nada: al ser **append-only**, la revocación es un evento más y la trazabilidad
sale gratis. El "estado vigente" se resuelve con una **proyección** por
`(organizacion_id, persona, finalidad)` quedándose con el último evento.

### 3.3 Una sola autorización, cinco ámbitos

**No se crea una finalidad nueva**: se reutiliza **`OPERACION_SERVICIO`**, que V6 ya sembró, y V28
solo ajusta sus banderas (`requiere_consentimiento = TRUE`, `permite_revocacion = TRUE`) y su
descripción. Es la única finalidad **activa**.

Esa autorización única cubre:

| # | Ámbito | Ejemplos |
|---|---|---|
| 1 | **Gestión comercial e inmobiliaria** | prospección, captación, oportunidades, visitas, matching |
| 2 | **Comunicaciones y seguimiento** | contacto, recontacto, avisos del proceso, reportes al propietario |
| 3 | **Documentos, contratos y pagos** | expediente, evaluación, contrato de alquiler, comisión |
| 4 | **Seguridad, auditoría y cumplimiento legal** | `historial_estado`, `evento_seguridad`, obligaciones contables y tributarias |
| 5 | **Automatizaciones internas necesarias para operar** | alertas, bandeja de tareas, recordatorios, derivaciones |

> **Los cinco ámbitos NO comparten base jurídica, y por eso la revocación no lo tumba todo.** Lo
> que el titular autoriza con la casilla es el **consentimiento** (ámbitos 1, 2 y 5). Los ámbitos 3
> y 4 se sostienen además —o en su lugar— en **relación contractual** y **obligación legal**, que
> **subsisten a una revocación**. Es exactamente lo que hace posible §5: retirar el consentimiento
> no borra un contrato firmado ni la trazabilidad que hay que conservar.

Bases jurídicas en uso:

```
CONSENTIMIENTO          el caso normal: la casilla del alta
RELACION_CONTRACTUAL    ya existe contrato; no se finge un consentimiento
OTRA_BASE_LEGAL         obligación legal y demás excepciones
```

Las otras cuatro finalidades que V6 sembró —`ANALITICA_AGREGADA`, `MEJORA_MODELOS`,
`RED_COLABORATIVA`, `PROSPECCION_COMERCIAL`— **quedan inactivas** (`estado = 'I'`): siguen en el
catálogo como destino, pero **ninguna pantalla las ofrece** y ningún flujo las escribe.

### 3.4 Vigencia y cambio material del aviso

**La autorización se registra una sola vez y permanece vigente hasta que ocurra una de dos cosas:**

1. **el titular la revoca** (§5), o
2. **el aviso de privacidad cambia materialmente**.

Para lo segundo, `aviso_privacidad_version` gana una columna **`cambio_material BOOLEAN`**. Publicar
una versión con `cambio_material = TRUE` **caduca las autorizaciones otorgadas contra versiones
anteriores**: no se borra ni se modifica ningún evento —el registro es append-only—, simplemente la
proyección deja de considerarlas vigentes y el sistema vuelve a pedirla.

Una autorización está **vigente** cuando:

```
su ultimo evento es OTORGADO o REOTORGADO
  Y no hay REVOCADO posterior
  Y su version_aviso NO es anterior a la ultima version con cambio_material = TRUE
```

Una corrección de redacción o un cambio de domicilio de contacto se publican con
`cambio_material = FALSE` y **no molestan a nadie**. Ese es el punto de que la bandera exista:
sin ella, cualquier retoque del texto obligaría a repreguntar a toda la cartera.

### 3.4 Qué pasa con los dos booleanos

Siguen existiendo y **siguen viajando en el contrato congelado** — no se tocan hasta el corte. Pero
dejan de ser la fuente de verdad:

- **Al dar de alta**: el service escribe la autorización **y** deja los dos booleanos en `true`
  (coherencia con el cable). No hay caso de alta con booleanos en `false`, porque sin autorización
  no hay alta.
- **Al leer**: se siguen sirviendo desde la persona/detalle, como hoy.
- **En el corte**: se retiran del DTO y se derivan de la proyección, o se eliminan.

---

## 4. IA y analítica

Para la primera versión, y sin excepciones:

| Regla | Consecuencia práctica |
|---|---|
| BROX y KAIROS usan **solo datos operativos autorizados y dentro de su tenant** | Ningún acceso transversal automático entre organizaciones |
| La analítica transversal usa **datos agregados o disociados** | Nunca fichas ni conversaciones identificables |
| **No** se usan conversaciones, documentos ni fichas personales para entrenamiento automático | `MEJORA_MODELOS` permanece inactiva |
| Entrenar con datos identificables exigiría una **autorización independiente y separada** de la gestión comercial | No se pide en el formulario comercial, ni ahora ni como casilla adicional |

Esto **cierra** una ambigüedad de D-25: allí `MEJORA_MODELOS` se planteaba como algo que KAIROS
pediría conversacionalmente. En la primera versión **no se pide, porque no se usa**.

---

## 5. Revocación — **no hay flujo. Decisión cerrada (2026-08-05)**

> **La autorización queda cerrada como CONSTANCIA ÚNICA registrada durante el alta, y no tendrá
> flujo de revocación.** Ni pantalla, ni botón, ni endpoint, ni procedimiento interno pendiente de
> escribir. Esto **sustituye** la versión anterior de esta sección, que enumeraba cinco pasos de un
> flujo que no se va a construir, y también lo que §8.1 dejaba abierto.

Consecuencias prácticas, para que nadie las reabra por descuido:

- **No se construye** ninguna superficie de revocación en el producto.
- Una solicitud del titular llega al correo oficial y **se atiende fuera del producto**.
- El registro sigue siendo **append-only**, así que si algún día se aplica una corrección
  administrativa por SQL, la trazabilidad la absorbe sin cambios de esquema y **la ficha la muestra
  correctamente** (`REVOCADA`): la lectura contempla ese estado aunque no exista camino de escritura
  en la aplicación.
- Lo que sí sigue vivo y automático es la **caducidad por cambio material del aviso** (§3.4), que no
  es una revocación del titular sino una consecuencia de publicar un aviso nuevo.

> **Ojo con el vocabulario.** En el bloque de seguridad —renombrado a *"Seguridad de sesiones,
> auditoría y bloqueo de accesos"* justamente por esto— «revocación» significa **invalidar sesiones
> de usuario** (`sesiones_invalidas_desde`, D-S0-12). No tiene ninguna relación con este documento.

---

## 6. Regla técnica: el alta es transaccional

```
persona + rol comercial + contacto + autorización   →   una sola transacción
```

- Si falta la autorización requerida, **no se persiste ninguna parte** del alta.
- **Prohibido** guardar primero la persona y marcar después que rechazó.
- El invariante se fija con un test de service y un check del E2E: *tras un alta rechazada por falta
  de autorización, `SELECT count(*) FROM persona WHERE …` devuelve 0*.

---

## 6 bis. Página pública «Privacidad y uso de datos»

**Obligatoria** (indicación 8 del 2026-08-05). Es la página a la que apunta el enlace del formulario,
y tiene que ser **pública**: el titular debe poder leerla **sin cuenta**.

| Sección | Contenido mínimo |
|---|---|
| **Finalidades** | Los cinco ámbitos de §3.3, en lenguaje llano |
| **Conservación** | Cuánto se guarda cada familia de dato y por qué; qué sobrevive a una revocación y con qué base |
| **Seguridad** | Medidas reales, no promesas: control de acceso por rol y organización, auditoría de accesos, cifrado en tránsito |
| **Derechos del titular** | Acceso, rectificación, cancelación, oposición y revocación, con el procedimiento |
| **Canales de atención** | Cómo ejercerlos y en cuánto tiempo se responde |

Piezas técnicas:

- `GET /aviso-privacidad` — **público**, devuelve la versión vigente (`version`, `vigenteDesde`,
  `contenido`). Necesita su fila en `matriz-operacion-rol.md`, y como es `permitAll` el gate
  comprueba que esté declarada en los dos sentidos.
- Ruta pública `/privacidad` en el SPA, alcanzable **sin sesión** y enlazada desde el formulario de
  alta y desde el pie del login.
- El contenido vive en `aviso_privacidad_version.contenido`, versionado y con `contenido_hash`: lo
  que se muestra es exactamente lo que se registró en la autorización.

> **El texto legal no lo redacta este documento.** El modelo es la estructura; el contenido debe
> revisarlo un **especialista peruano en protección de datos** antes de publicarse.

---

## 7. Alcance: lo que este documento NO decide

- **No** implementa autorizaciones separadas para analítica, entrenamiento o mejora de IA.
- **No** activa las cuatro finalidades opcionales de V6.
- **No** toca el flujo conversacional de KAIROS (BLOQUE 11, no iniciado).
- **No** redacta el contenido legal del aviso de privacidad: el modelo es la estructura. Los textos
  definitivos deben ser revisados por un **especialista peruano en protección de datos** antes de
  publicarse.

---

## 8. Estado de implementación (2026-08-05)

| Pieza | Estado |
|---|---|
| **V28** — reutiliza `OPERACION_SERVICIO`, desactiva las otras cuatro, añade `registrada_por` y `motivo_revocacion`, `cambio_material` y el aviso 1.0 | ✅ **aplicada y verificada** contra PostgreSQL |
| `service/soporte/Autorizaciones` — registrar, revocar, proyección de vigencia, aviso vigente | ✅ hecho |
| Repositorios de los tres agregados (evento, evidencia, aviso) | ✅ hechos (**no existía ninguno**) |
| `ClienteServiceImpl` / `PropietarioServiceImpl` — alta transaccional | ✅ hecho y **verificado end-to-end**: sin autorización, `count(*) = 0` |
| `GET /aviso-privacidad` público + fila en la matriz | ✅ hecho |
| SPA — `ClienteForm`: casilla + enlace, y **sin envío** sin autorización | ✅ hecho |
| SPA — **formulario de propietario**, misma sección | ✅ hecho y verificado contra el API (2026-08-05) |
| SPA — página pública `/privacidad`, **versión corporativa aprobada** | ✅ hecha |
| **Retirado el desplegable de canal** de los dos formularios y del cable; el backend sella `FORMULARIO_BROX` | ✅ hecho (2026-08-05, tarde) — sin migración: la columna no tiene `CHECK` |
| Pruebas: alta autorizada, rechazo sin persistencia, versionado, revocación | ✅ **17 de servicio + 4 (cliente) + 5 (propietario) + 5 (página)**; reactor y suite Angular verdes (483) |
| Fichas de cliente y propietario: *quién* autorizó y *cuándo* | ✅ **hecho** (2026-08-05) — §8.3 |
| `contrato-transversales-frontend.md` — patrón de la sección | ⬜ pendiente |

### 8.3 La constancia en las fichas (2026-08-05)

**Endpoints nuevos, no campos nuevos**: `GET /clientes/{id}/autorizacion` y
`GET /propietarios/{id}/autorizacion`. Ampliar `ClienteResponse` habría separado del cable de la v1
una respuesta congelada; un endpoint aditivo no toca nada. Los dos llevan su fila en
`matriz-operacion-rol.md` (el gate no admite endpoint sin fila) y **el mismo alcance** que
`GET /{id}`: se resuelven con el mismo `cargarConAcceso`, así que fuera del alcance del BROKER
responden 403 y desde otro tenant, 404.

La consulta va por **`persona.id`, no por el id del rol**: la autorización la dio la persona una
sola vez y cubre todos sus roles.

Lo que se muestra, y nada más:

| Campo | Nota |
|---|---|
| **Estado** | `VIGENTE` · `REVOCADA` · `CADUCADA` · `SIN_REGISTRO` · `NO_VIGENTE` (defensivo) |
| **Fecha y hora** | del último evento, formateada en `es-PE` |
| **Quién la registró** | nombre, resuelto desde `registrada_por` **dentro del tenant**. Si el rol ya no existe: *"No consta"* |
| **Versión del aviso** | **solo si difiere de la vigente.** Es la lectura operativa de *"solo si aporta valor"*: cuando coinciden, el número es ruido; cuando no, dice que esa persona autorizó contra un aviso anterior |

**El canal no se muestra** — y tampoco se pregunta al registrarlo (§1). Vale siempre
`FORMULARIO_BROX`, y un dato constante no informa de nada.

Dos decisiones de pantalla que conviene no deshacer:

- **`SIN_REGISTRO` no es "no autorizó".** Las personas dadas de alta antes de este bloque no tienen
  evento; la ficha lo dice con esas palabras en vez de insinuar una negativa que nunca ocurrió.
- **Un fallo del endpoint no tumba la ficha.** La constancia se pide en paralelo y su error se
  muestra dentro del panel: es un dato de cumplimiento, no la historia comercial. Y sobre todo, un
  error **no** se pinta como "sin autorización".

Un **único componente compartido** (`shared/constancia-autorizacion`) pinta las dos fichas, por la
misma razón por la que el service devuelve un solo record: es el mismo hecho sobre la misma persona,
y dos plantillas gemelas acaban divergiendo.

### 8.1 Revocación: **no hay flujo, y no lo habrá** (decisión del 2026-08-05)

Ver §5. **No se crean pantallas, botones, endpoints ni procedimiento interno.** La versión anterior
de este apartado dejaba `Autorizaciones.revocar` reservado *"para el procedimiento interno cuando se
escriba"*; **esa promesa queda anulada**: no se va a escribir.

`Autorizaciones.revocar` sigue en el árbol, sin exponer y con sus pruebas, como herramienta de
corrección administrativa fuera de banda — no como flujo de producto. Lo que se registra al dar de
alta no cambia: **versión del aviso, `cambio_material`, actor, fecha, canal técnico y tenant**.

> Si "revocación" aparece en un documento de seguridad, se refiere a **sesiones de usuario**
> (D-S0-12), no a esto.

### 8.2 Qué NO publica la página pública

Retirado a propósito, y fijado con tests para que no vuelva:

- **detalles técnicos** (algoritmos de hash, nombres de tablas, arquitectura);
- **instrucciones extensas** para solicitar eliminación — hay un correo, y eso basta;
- **el texto interno que se guarda como evidencia** (`aviso_privacidad_version.contenido`): de la
  llamada solo se toman **versión** y **fecha de vigencia**;
- **referencias a funcionalidades futuras** (entrenamiento de modelos, red colaborativa).
