# D-P0-1 · Autoridad de edición de la propiedad

**Fecha:** 2026-08-30
**Estado:** **VIGENTE.** Implementada en `V87` + `V88` + `V89`.
**Decisión del titular.** Este documento es la **autoridad**: dice qué regla
está viva. Las mediciones fechadas de la implantación viven aparte, en
`backend-spring/verificacion/evidencia/2026-08-30-p0-autoridad-de-edicion.md`,
y no repiten esta regla — la citan.

> **Éste es el único documento normativo de P0, y VIAJA** (`N41`, 2026-09-02).
> Está en la lista blanca de `.gitignore`, así que un clon limpio lo tiene, y
> `AutoridadDeLaPropiedadTest#laAutoridadQueGobiernaViajaConElCodigo` pone el
> build en rojo si deja de estarlo. Si una regla P0 sólo vive en
> `docs/ai/pendientes-brox.md` —que es **inventario, no autoridad**, y además
> está ignorado— no gobierna nada: su sitio es aquí.

---

## El problema que cierra

`PUT /propiedades/{id}` cargaba la fila por `(organizacion, id)` y escribía.
**Cualquier AGENTE del tenant editaba cualquier propiedad**, y de paso el
importe, la exclusividad y la vigencia de un ENCARGO ajeno — con su hito `U` en
la serie económica de otro.

Lo más parecido a una regla que existía, `LocalComercialServiceImpl
.exigirPertenencia`, era un **OR** (`captación viva ∨ prospección ∨
id_rol_incorporo`) con **un solo llamador**. Con dos encargos de agentes
distintos daba **verdadero para los dos**.

---

## Las decisiones

### P0-1 · Una propiedad sólo la modifica su agente responsable

La lectura puede ser más amplia: **ver no concede editar**. BROKER y
TENANT_ADMIN conservan supervisión y gobierno pero **no escriben hechos de la
propiedad por alcance de tenant**. Lo que el broker sí decide es **quién**
responde.

### P0-2 · Responsable explícito, y traspaso trazable

`propiedad.id_rol_responsable`, con **FK compuesta por organización**. Se
descartó derivarlo del encargo —con una venta y un alquiler vivos de agentes
distintos habría que elegir uno, y una fecha comercial no puede ser la autoridad
sobre la verdad física— y se descartó consagrar `id_rol_incorporo`, que es
**procedencia histórica inmutable** y no un permiso.

**`id_rol_incorporo` e `id_rol_responsable` son conceptos distintos** aunque al
nacer señalen al mismo agente.

### P0-3 · La ausencia es FALTANTE

`NULL` significa **no se sabe**, no «de todos». La propiedad queda **visible y
no editable, con motivo explícito**, hasta que un **BROKER** asigne. **No se
infiere de nada** —ni del encargo vivo, ni de la prospección, ni de
`id_rol_incorporo`— y **no se tapa con un actor sintético** tipo `SISTEMA`.

**FALTANTE bloquea la escritura de la PROPIEDAD y nada más**: quien tenga un
encargo legítimo lo sigue operando, y la propiedad se sigue leyendo,
publicando y cruzando.

### P0-4 · El encargo lo edita su propio agente

Cada encargo responde a su `captacion.id_rol_agente`: importe, exclusividad,
vigencia y condiciones comerciales gobernadas. Y con ello **todo su histórico
económico**: ninguna vía indirecta puede producir un hito `U`, `P` o `C` sobre
un encargo ajeno.

### P0-5 · El alta fija el responsable inicial *(titular, 2026-08-30)*

Cuando un agente registra una propiedad **realmente nueva**, queda como su
responsable inicial, y **el alta escribe su fila** append-only en
`asignacion_responsable_propiedad`, trazada con `origen = 'ALTA'`.

No es una inferencia: el actor del alta es un hecho conocido. La alternativa
—que toda propiedad nazca FALTANTE— dejaría al agente sin poder editar lo que
acaba de registrar.

#### El límite crítico

Esto vale **única y exclusivamente cuando nace una fila de `propiedad`**.

- Que otro agente **vuelva a captar** una propiedad existente, le **abra un
  ENCARGO nuevo**, la **retome** o la **vuelva a trabajar** **no** lo convierte
  en responsable.
- Una nueva **VENTA** o **ALQUILER** tampoco cambia la responsabilidad de la
  PROPIEDAD.
- **Detectar o reutilizar** una propiedad existente **jamás** ejecuta el alta
  del responsable.
- Una propiedad histórica **FALTANTE sigue FALTANTE** hasta que un BROKER
  asigne.
- Una propiedad que ya existe **sólo** cambia de responsable por **traspaso
  explícito autorizado por BROKER**.

---

### C5 · El inventario sin dueño lo gobierna cualquier bróker del tenant *(titular, 2026-08-30)*

**Quién lee el expediente de una propiedad FALTANTE**, y por extensión cualquier
superficie donde el alcance del bróker se calcule **sobre el responsable**:

- propiedad **con** responsable → BROKER **sólo si supervisa a ese responsable**;
- propiedad **FALTANTE** → **cualquier BROKER del mismo tenant**;
- **TENANT_ADMIN** → cualquier propiedad de su tenant, sin cambio;
- **otro tenant** → **recurso inexistente**, y esa frontera se pregunta **antes**
  que el rol, sin cambio.

**El porqué, que es lo que evita que se lea como una excepción:** gobernar el
inventario sin dueño **es trabajo de bróker** — es justo lo que tiene que mirar
para decidir a quién asignarlo. La regla «sus supervisados vigentes» existe para
**no cruzar equipos**; sin responsable **no hay a quien supervisar**, así que esa
regla no tiene sobre qué aplicarse y el límite efectivo vuelve a ser el que va
siempre delante: **el tenant**.

> **Corregido por C6 (2026-08-30).** Aquí decía «**la asignación no cambia**: su
> alcance ya se calcula sobre el agente que **recibe**, no sobre el que no hay».
> Eso era cierto de la lectura del expediente y **falso** de la asignación: mirar
> sólo al que recibe dejaba abierta la puerta del que sale. C6 comprueba **los
> dos extremos**, y es entonces cuando la frase se vuelve verdadera en su forma
> completa — sin saliente no hay nada que comprobar por ese lado, que es
> exactamente lo que C5 dice.

**Dónde vive.** En `Alcances.alcanzaIncluidoSinDueno`, junto a `alcanza` y con la
diferencia escrita entre las dos. No en una rama del llamador: `alcanza` devuelve
`false` ante un dueño nulo **antes** de mirar la banda —y cinco llamadores
dependen de ello a propósito—, así que la respuesta correcta tiene que salir del
sitio que decide alcances, o la siguiente superficie que pregunte por un recurso
sin dueño volverá a heredar la equivocada, en silencio.

**Y no es la misma pregunta que `puedeTraspasar`.** Ofrecer el traspaso y leer
el expediente son dos autorizaciones distintas — una responde en la **ficha**,
la otra autoriza la lectura del **expediente** —, pero desde **C7** el booleano
del cable ya no sale de la banda: lleva el **alcance sobre el responsable**
saliente. Ver «C7 · El booleano del cable lleva el alcance del saliente».

> **Matizado por C6 (2026-08-30) y resuelto por C7 (2026-09-01).** Aquí se
> ilustraba con «un bróker de otro equipo **puede traspasar** una propiedad y
> no leer su expediente», y ese ejemplo ya **no es cierto** de una propiedad
> **con** responsable: desde C6 ese bróker tampoco la traspasa. El ejemplo que
> sigue siendo cierto es el de la propiedad **FALTANTE**, donde sí entra por
> las dos puertas. El booleano, que C6 dejó **de banda** —ofrecerse y que el
> POST rechazara—, lo estrechó **C7** al alcance del saliente; el POST sigue
> siendo la autoridad final. Ver «C7 · El booleano del cable lleva el alcance
> del saliente».

---

### C6 · Un traspaso tiene DOS extremos, y los dos se comprueban *(titular, 2026-08-30)*

Hasta aquí, `asignar` sólo miraba **a dónde iba** la propiedad. Eso dejaba
abierta la puerta de **dónde salía**: un BROKER que supervisara al **destino**
podía **sacar una propiedad del equipo de otro bróker** con sólo elegir un
destino suyo — un traspaso **entre** equipos disfrazado de movimiento interno.

- propiedad **con** responsable → el **BROKER** sólo la traspasa si supervisa de
  forma vigente al **saliente** **y** al **destino**. Si cualquiera de los dos
  pertenece a otro equipo, el traspaso es organizativo y **corresponde al
  TENANT_ADMIN**;
- propiedad **FALTANTE** → **C5 permanece intacto**: no hay saliente a quien
  supervisar, así que **cualquier BROKER del tenant la gobierna para
  asignarla** — **pero únicamente a uno de sus supervisados vigentes**. La
  excepción abre **qué** propiedades gobierna, **no a quién** puede
  entregarlas;
- **después de la asignación la excepción desaparece** y vuelve el alcance
  **EQUIPO**, sin que nadie ejecute nada más;
- **otro tenant** → **nunca**, y como **recurso inexistente**. La frontera va
  delante de las dos comprobaciones y no se levanta porque las supervisiones del
  actor estén en regla.

**Un solo algoritmo de supervisión.** Las dos preguntas salen del **mismo**
`Alcances` que ya decide la lectura del expediente: `alcanza` para el destino y
`alcanzaIncluidoSinDueno` para el saliente. No se escribió una segunda
comparación — dos sitios donde se compare lo mismo son dos sitios que después
divergen, y divergen hacia el lado que concede de más. Y la distinción «hay
responsable / no hay responsable» se conserva tal cual: es la que hace que C5
siga siendo una excepción **por ausencia de dueño** y no alcance de tenant para
cualquier bróker.

**Efecto lateral que sí conviene ver:** desde C6, «¿puedo traspasar esta
propiedad?» por el lado del saliente y «¿puedo leer su expediente?» son **el
mismo predicado**. Las dos superficies convergieron, y por eso el ejemplo de
arriba dejó de ser cierto.

#### C7 · El booleano del cable lleva el alcance del saliente *(CONTROL, 2026-09-01)*

Aquí vivía la anotación que C6 dejó pendiente: `responsabilidad.puedeTraspasar`
**no se estrechó** en C6 y siguió valiendo `!actor.esAgente()` —la primera guarda
de `asignar`—, de modo que para una propiedad cuyo responsable era de otro
equipo **el botón se ofrecía y el POST respondía 403**. Quedó inventariada como
decisión funcional del titular, no resuelta por omisión. **C7 la resolvió.**

**El significado nuevo**, exacto: `puedeTraspasar` responde «**el actor puede
iniciar ahora el cambio de responsable de ESTA propiedad, considerando el
responsable actual**». Lo resuelven las dos guardas de `asignar` que la ficha sí
puede mirar, y ninguna más — sin reglas nuevas y sin segunda política:

| Caso | Actor / estado | `puedeTraspasar` |
|---|---|---|
| A | AGENTE | `false` |
| B | BROKER, propiedad FALTANTE | `true` — no hay saliente a quien supervisar (C5 intacta) |
| C | BROKER, saliente supervisado vigente | `true` |
| D | BROKER, saliente de otro equipo | `false` |
| E | TENANT_ADMIN, propiedad de su tenant | `true` — por **autoridad de gobierno del tenant**, no como super-broker: no gana edición de hechos ni autoridad comercial |

El productor sigue siendo **el mismo método** que deniega la escritura
(`AutoridadDePropiedad.responsabilidadDe`), y el predicado del saliente es el
**mismo** `Alcances.alcanzaIncluidoSinDueno` que pregunta el POST — la ficha no
puede prometer lo que el POST va a negar.

**Lo que NO cambió:** el alcance sobre el **destino** sigue sin poder resolverse
en la ficha — ahí todavía no hay destino elegido — y `puedeTraspasar=true` **no
autoriza nada**: «puedes iniciar desde este estado». El POST de asignación
sigue siendo la autoridad final y vuelve a comprobar banda, saliente y destino;
ni una guarda suya se tocó. La excepción FALTANTE (C5) queda exactamente como
estaba: abre **qué** propiedades gobierna, no **a quién** puede entregarlas.

**La línea que C5 dejó medida se invirtió de forma consciente:** en
`traspasarYLeerElExpedienteSiguenSiendoDosPreguntas`, el broker de otro equipo
pasa de que la ficha le ofrezca el traspaso a que no se la ofrezca. «Puede
traspasar» y «puede leer el expediente» siguen siendo dos autorizaciones
distintas — una de ficha, otra de comando —; la separación sigue viva en las
tres respuestas distintas del responsable (edita, no traspasa, no lee).

### D-P0-6 · Quién lee históricos *(CONTROL, 2026-09-01) · VIGENTE*

Cierra `N39`, que P0 dejó abierta explícitamente: aquel decidió **quién
escribe**, y la lectura del pasado siguió siendo «todo el tenant».

| Actor | Historia de la PROPIEDAD | Histórico de un ENCARGO | Expediente de traspasos | Rastro de reasignaciones del ENCARGO |
|---|---|---|---|---|
| AGENTE **responsable** de la propiedad | **sí** | sólo **sus** encargos | no | **no**, tampoco el de un encargo suyo |
| AGENTE **no** responsable | no | sólo sus encargos, si los tuviera | no | **no** |
| BROKER | propiedades dentro de su **alcance de supervisión** (el mismo predicado que C5: `alcanzaIncluidoSinDueno` sobre el responsable) | encargos dentro de su alcance (`alcanza` sobre el agente del encargo) | **sí**, dentro de su alcance de gobierno *(ya existía, C2)* | los encargos que **hoy** lleva un supervisado (`Alcances.de` sobre `captacion.id_rol_agente`) |
| TENANT_ADMIN | **no** por ser admin | **no** por ser admin | **sí**, todo el tenant *(ya existía, C2)* | **sí**, todo el tenant — mismo gobierno que el expediente |
| Otro tenant | **nunca** — 404 antes que nada *(ya existía)* | nunca | nunca | nunca: la consulta arranca por la organización del actor |

**El principio, y la frase que lo resume: gobernar no es operar.** Poder
gobernar responsables —y leer el expediente de traspasos, que es **organigrama**:
quién respondió, desde cuándo y por qué se movió— **no concede** la información
**comercial** histórica: a cuánto se pidió, a cuánto se cerró, cuántas veces
estuvo en venta. Una persona con **TENANT_ADMIN + BROKER** obtiene lo comercial
**actuando con su banda BROKER**; el `Actor` llega con **una sola banda por
petición**, y la auditoría dice cuál usó.

**La lectura conjunta de las dos filas, que es interpretación de esta
implementación y se declara como tal.** Las dos primeras columnas no son
independientes: la **historia de la propiedad** es un bloque *agregado* sobre
sus encargos, así que si se sirviera entera a quien puede leerla se convertiría
en **la puerta por la que se lee el importe de un encargo ajeno** sin haberlo
pedido. Por eso:

- `historia` se compone **sólo de los encargos que ese actor puede ver**. Si no
  queda ninguno, el bloque **no viaja**;
- `actividad` son los hechos **de esos mismos encargos visibles**;
- `encargos[].historico` viaja o no **encargo por encargo**; el resto del bloque
  —importe **vigente**, exclusividad, condiciones, anuncios y `puedeEditar`—
  viaja siempre, porque «no puedes ver lo que se pidió en 2023» **no** es «este
  encargo no existe»;
- un **hito sin encargo** (legado) no tiene episodio al que atribuirse y **sigue
  la regla de la PROPIEDAD**. No se le inventa un encargo para poder
  clasificarlo, y **no entra** en la historia agregada: sin episodio no se puede
  decir de qué operación era, y afirmarlo sería inventarlo.

**Nulo no es vacío.** Jackson va `NON_NULL`, así que el campo **no viaja**: el
cliente debe leer su ausencia como «**no disponible para ti**», nunca como «no
ha pasado nada con esta propiedad». Una serie vacía y una serie que no
corresponde son dos hechos distintos.

**Y trajo una fuga que no estaba inventariada.** `GET /locales/{id}/precios`
llamaba al servicio **sin actor**, y el servicio consultaba `precio_propiedad`
por `id_propiedad` **sin tenant**: cualquier usuario autenticado de **otra
corredora** leía la serie económica completa de cualquier propiedad sabiendo su
id. La fila de la matriz decía «se alcanza por el id del padre, que sí va
filtrado por tenant» — y **el padre no se cargaba**. Describía una protección
que no existía. Corregido en el mismo corte, con la prueba escrita **en rojo
primero**.

**No es guarda de escritura.** Los dos predicados —
`puedeLeerHistoriaDeLaPropiedad` y `puedeLeerHistoricoDelEncargo`— **responden**,
no deniegan, así que **no entran** en las listas `GUARDAS_*` del gate de
autoridad: aceptar allí un lector como si fuera una guarda es exactamente el
error que ese gate ya cometió una vez con `puedeEditar`.

**Aplicación al rastro de reasignaciones del ENCARGO, declarada como
interpretación para que CONTROL pueda vetarla** *(CONSTRUCTOR, 2026-09-02)*.
`GET /captaciones/reasignaciones` es la tercera superficie histórica del
encargo, y no aparecía en la tabla de arriba: servía
`findByOrganizacionIdOrderByIdDesc(actor.idOrganizacion())` —**todo el
tenant**—, así que un BRÓKER leía de qué agente salió cada encargo, hacia cuál
fue y **con qué motivo** en equipos que no supervisa. El motivo es texto libre
de gobierno sobre personas, y `F3` lo dejó tokenizado como `{autoridad: TENANT}`
precisamente porque estrecharlo cerraba una puerta. La fila de esta decisión que
lo resuelve es la del **histórico de ENCARGO** —*BROKER: los que están dentro de
su alcance; TENANT_ADMIN: todo el tenant, por gobierno del expediente de
traspasos*—, y se aplica **sin inventar regla**: el rastro se acota con **el
mismo `Alcances.de` que ya usa el listado de captaciones**, sobre **la misma
columna** (`captacion.id_rol_agente`). Quien ve el encargo ve su rastro. Lo que
sí es elección, y por eso se escribe:

- **el alcance es el del encargo de HOY**, no el del agente **saliente** —medirlo
  por él dejaría un encargo que ya cambió de equipo en el rastro del bróker
  anterior para siempre— ni el del **bróker que firmó** la reasignación —medirlo
  por él haría invisible para todo bróker una reasignación de gobierno—;
- **la banda va delante y el alcance no la sustituye**: a un AGENTE
  `Alcances.de` le devolvería «lo suyo», y sus propias reasignaciones son justo
  lo que no le corresponde leer —quien lleva un encargo ya sabe que lo lleva, y
  eso no le concede los motivos con que se movió la cartera—. El Core repite la
  banda del `@PreAuthorize` con `exigirBandaQueGobiernaElEncargo`, hermana de
  `exigirBandaComercial` por el otro lado: allí el gobierno no hereda la
  operación, aquí la operación no hereda el gobierno;
- **no cambian ni el DTO ni el orden** (`id` descendente, el más reciente
  primero): acotar quién lee no reordena lo que lee.

Y el derivado que devolvía el tenant entero **se retira**, no se deja al lado:
mientras existiera, el siguiente consumidor de esa tabla podía volver a tomarlo
sin enterarse de que se saltaba el alcance —que es exactamente como llegó aquí
el defecto—.

### D-P0-7 · Elegibilidad del nuevo responsable *(CONTROL, 2026-09-01) · VIGENTE*

Cierra `N45`. **Alcanzar a un agente no es que ese agente pueda operar**: hasta
aquí el traspaso comprobaba el alcance del actor sobre el destino y nada sobre
el destino mismo, así que una propiedad podía acabar en manos de una cuenta
suspendida o de un agente de baja.

El **DESTINO** debe cumplir **todas**:

| Condición | Autoridad que ya la decide hoy |
|---|---|
| mismo **tenant** | `organizacion_id` del detalle **y** del rol |
| rol **AGENTE vigente** | `persona_rol.tipo_rol='AGENTE' and vigencia_hasta is null`, en la organización |
| **cuenta habilitada** para operar | `credencial_usuario.estado_administrativo='A'` de la **misma persona** en la org, colgando de su `persona_rol` `USUARIO_INTERNO` **vigente** (el mismo camino de `DetalleAgenteRepository.DESDE_FILTRABLE`) |
| **relación organizacional vigente** | `usuario_organizacion.estado='A'` con `id_usuario` = ese `persona_rol` `USUARIO_INTERNO` (la misma fuente de `UsuarioOrganizacionRepository.bandaActivaDePersona`) |
| **operativo** | `detalle_agente.estado_operativo='D'` |
| **supervisión vigente** *(sólo si el actor es BROKER)* | `supervision_agente.fecha_fin is null` por ese bróker — el mismo `Alcances.alcanza` |

**No se inventa ni un estado.** Cada fila apunta a la columna que ya gobierna
ese hecho; no hay una tabla nueva de «agentes habilitados para recibir».

**El TENANT_ADMIN no necesita supervisar**, pero el destino tiene que ser
igualmente un AGENTE válido y operativo del mismo tenant: está exento de la
restricción de **equipo**, no de la de **elegibilidad**. «Exento de supervisar»
y «exento de comprobar» son cosas distintas, y confundirlas es el fallo que la
prueba `c8ElGobiernoDelTenantTampocoAsignaAUnAgenteDeBaja` impide.

**Supervisión vigente y agente operativo son invariantes DISTINTAS** y se
prueban por separado. Fundirlas —«si lo supervisa, es que puede»— dejaría entrar
al supervisado de baja, que es el error caro: el bróker cree que reparte dentro
de su equipo y se la da a alguien que no la va a trabajar.

**Un solo mensaje, y no dice cuál falló.** Publicar el estado administrativo de
una cuenta ajena a quien sólo preguntaba por un traspaso sería información que
no le corresponde. El bróker que necesite el detalle lo tiene en la ficha del
agente, que es la superficie donde ese dato sí es suyo.

**Dónde va la guarda**: la **última** de `asignar`, después de banda, tenant,
alcance del destino, alcance del saliente y «mismo agente», y **antes** de
escribir nada. Adelantarla cambiaría el **motivo** del rechazo en casos que hoy
fallan por otra razón, y un cambio de motivo es un cambio de contrato aunque el
código HTTP coincida.

**Rol cerrado responde 403, no 404**, y está medido: `asignar` resuelve el
destino con `findById` —por clave primaria, sin mirar vigencia—, así que la fila
sigue ahí y lo que corta es la elegibilidad. Es la respuesta correcta: el agente
**existe** —lleva propiedades, firmó encargos, el expediente lo nombra— y lo que
no está vigente es su rol.

**Y se aplica en TODA reasignación de autoridad, no sólo en la de la
propiedad** *(2026-09-01)*: desde este corte la exige también
`POST /captaciones/{id}/reasignar`, con el **mismo** componente y el **mismo**
predicado SQL. Ver «Las mismas decisiones, dichas sobre el ENCARGO».

### D-P0-8 · Desactivar no reasigna *(CONTROL, 2026-09-01) · VIGENTE*

Desactivar a un agente **no mueve ni una propiedad**. Es una decisión, no una
omisión:

- **no puede recibir** nuevas — sale de la lista de candidatos y el POST hacia
  él responde 403;
- las que **ya tenía se quedan** donde están, como **situación que requiere
  gobierno explícito**;
- la resuelven el **TENANT_ADMIN** y **el BROKER que lo supervisa** —la
  supervisión sigue abierta, porque desactivar una cuenta **no cierra el
  organigrama**— con el **traspaso trazable**, que deja su fila con motivo y con
  el desactivado como `anterior`;
- **no se borra ni se reinterpreta historia**: el rastro del saliente sigue
  donde estaba.

Reasignar en cascada al desactivar repartiría cartera **sin que nadie lo
decidiera y sin motivo en el expediente** — justo el tipo de escritura que P0
vino a quitar. Hoy nadie reasigna al desactivar, y esta decisión es lo que hace
que siga siendo así: el corte no añadió código, añadió **pruebas** que se ponen
rojas si alguien lo añade.

### D-P0-9 · Traspaso causal, no *last-write-wins* *(CONTROL, 2026-09-01) · VIGENTE*

Un traspaso **no es «pon a B»**: es «**cambia A por B**». Hasta aquí, las guardas
decidían **quién** puede traspasar y **a quién**, y ninguna decidía **desde
dónde**. Dos comandos que salieran del mismo responsable A —uno hacia B y otro
hacia C— pasaban exactamente las mismas comprobaciones, y el segundo pisaba al
primero: **la última escritura ganaba**, y el expediente quedaba afirmando «de A
a C» sobre una propiedad que en ese momento ya llevaba B. Nadie había decidido
eso.

**Lo que se congela es el comportamiento observable**, no el mecanismo:

- el comando declara **sobre qué responsable actúa** —el que el actor **vio en la
  ficha** cuando decidió—;
- si al ejecutarse el responsable ya **no** es ése, la respuesta es **409
  CONFLICT** y **no se ha escrito nada**;
- **no se reinterpreta**: un «cambia A por C» que llega cuando ya responde B
  **no** se convierte en «cambia B por C». Sería una decisión distinta de la que
  se tomó, tomada por el sistema y firmada por una persona que nunca la vio;
- de un estado concreto puede partir **exactamente una** transición legítima.

**FALTANTE se declara, no se infiere de una ausencia.** El cuerpo lleva
**exactamente una** de las dos: `idResponsableActual` —el responsable que se
vio— o `sinResponsableActual: true` —«la vi sin responsable»—. Ninguna de las
dos, o las dos a la vez, es **400**: un cuerpo sin declaración no dice «estaba
FALTANTE», dice que **nadie miró**, y quedarse con la interpretación más
probable sería inventar la observación. Y las combinaciones cruzadas también
son 409 —FALTANTE declarado sobre una propiedad con responsable, o un
responsable declarado sobre una FALTANTE—: el hueco no es un comodín.

**El mecanismo elegido, y por qué es el mínimo.** Dos comprobaciones, que no son
redundancia sino dos preguntas distintas:

1. **precondición en memoria**, antes de tocar nada. Corta el comando obsoleto
   sin escribir y **con el estado de hoy en el mensaje**, que es lo único que
   permite volver a decidir en vez de reintentar a ciegas;
2. **compare-and-set en SQL** sobre el responsable observado
   (`update propiedad set id_rol_responsable = :nuevo where … and
   id_rol_responsable = :observado`), que es lo que resuelve **la carrera**:
   entre la comprobación en memoria y la escritura cabe otra transacción, y el
   único sitio donde no cabe ninguna es **dentro del propio UPDATE**. Bajo
   `READ COMMITTED` —el nivel por defecto de PostgreSQL— un UPDATE que encuentra
   la fila bloqueada por otra transacción **espera y re-evalúa el WHERE sobre la
   fila ya actualizada**, así que el segundo comando ve el responsable nuevo y
   afecta **cero filas**.

**Sin `@Version` y sin migración**: la columna que hace de testigo es la que ya
existe y ya es la autoridad, `id_rol_responsable`. Añadir una columna de versión
habría sido una migración y un segundo sitio donde vive la misma verdad. Las dos
comprobaciones responden lo mismo al cliente —409— porque para quien traspasa el
hecho es el mismo: **el estado cambió y hay que volver a mirar**; lo que cambia
es cuánta información se puede dar, y por eso la primera nombra al responsable
de hoy y la segunda no —la transacción que pierde la carrera no puede afirmar
quién ganó—.

**Cómo se prueba.** Con **dos transacciones reales** sobre PostgreSQL
(`CausalidadDelTraspasoIntegrationTest`): una se queda detenida **dentro** de su
transacción con la fila ya bloqueada, la otra parte del mismo responsable, y la
prueba **comprueba contra `pg_stat_activity` que la segunda está de verdad
esperando un lock** —por una conexión propia, fuera del pool— antes de soltar a
la primera. Si el bloqueo no llega a existir, la prueba **falla**: dar por buena
una carrera que nunca ocurrió sería un verde que no ha mirado nada.

**Y sólo la autoridad puede ejecutar ese CAS.** Es una escritura de
`id_rol_responsable` como cualquier otra, así que la vigila un gate gemelo del
que ya protegía el *setter* de la entidad: un segundo llamador desde otro
servicio pone el build en rojo, porque sería la puerta por la que volvería a
entrar un traspaso que no declara de dónde parte.

**En el cliente**, el SPA envía el responsable que está mostrando y, ante un 409,
**no reintenta**: muestra el mensaje del Core y ofrece **volver a cargar la
ficha**. No anuncia traspaso, porque no lo hubo.

**Y lo mismo sobre el ENCARGO** *(2026-09-01)*: `id_rol_agente` es la segunda
autoridad mutable de P0 y tenía exactamente esta forma de defecto. Ver «Las
mismas decisiones, dichas sobre el ENCARGO».

### D-P0-10 · El traspaso es atómico *(CONTROL, 2026-09-01) · VIGENTE*

Cambiar el responsable, dejar **saliente, destino, actor, banda y motivo** en el
expediente, y anotar el **evento de dominio** son **partes de un mismo hecho**,
no tres efectos que puedan ocurrir por separado. Van en la **misma transacción**
y si cualquiera falla **se deshacen todas**.

No se acepta:

- **responsable cambiado sin traza** — una autoridad que nadie puede explicar,
  que es exactamente lo que P0 vino a quitar;
- **traza que afirme un cambio que no ocurrió** — peor que no tener traza,
  porque el expediente es *append-only* y nadie lo corrige después;
- **motivo de otra transición** — el motivo pertenece al traspaso que lo produjo.

**Cómo se prueba, y por qué no basta leer el `@Transactional`.** «Es atómico
porque el método lleva la anotación» es una lectura del código, no una
comprobación. La prueba **rompe cada escritura por separado** —inyecta un fallo
en el repositorio del rastro y, en otra, en el del evento— y mira la base
después: el responsable vuelve a su valor, no queda fila de expediente (ni
siquiera la que ya se había insertado) y no queda evento. Cada caso lleva su
**control positivo**: el mismo traspaso, sin fallo inyectado, escribe las tres
cosas.

**La columna es de sólo inserción para el ORM; en `UPDATE` sólo la escribe el
compare-and-set.** `propiedad.id_rol_responsable` está mapeada
`@Column(updatable = false)`, así que ningún *flush* de entidad gestionada la
toca: la escriben el `INSERT` del alta —`AutoridadDePropiedad.fijarAlAlta`, antes
del primer `save`— y, en `UPDATE`, únicamente
`PropiedadRepository.cambiarResponsableSi`, que es un `UPDATE` JPQL y por tanto
no pasa por esa anotación. Sin la marca había una segunda puerta que nadie había
decidido: `Propiedad` no lleva `@DynamicUpdate`, de modo que un
`PUT /propiedades/{id}` que hubiera cargado la ficha con responsable A y guardara
**después** de que un traspaso A→B comiteara devolvía la columna a A **sin fila
de expediente** —«responsable cambiado sin traza», por una puerta que no es el
traspaso—. Lo mide `CausalidadDelTraspasoIntegrationTest`, caso *«una edición
concurrente no revierte un traspaso»* (medido el 2026-09-01: rojo antes del
mapeo, `expected: <29> but was: <28>`; verde después).

**Y lo mismo sobre el ENCARGO** *(2026-09-01)*: la reasignación y su fila de
rastro son un solo hecho, y la columna es de sólo inserción para el ORM. Ver
«Las mismas decisiones, dichas sobre el ENCARGO».

### D-P0-11 · Una publicación siempre pertenece a un encargo *(CONTROL, 2026-09-01) · VIGENTE*

Un anuncio no anuncia «una propiedad»: anuncia que **esta propiedad se ofrece en
ESTA operación a ESTE precio**. Sin encargo, una publicación no sabe qué
operación publica, cómo se llama su importe —«precio de venta» o «renta
mensual»— ni **quién responde por ella**. Son los cuatro huecos que P0 vino a
cerrar, en la última columna del bloque que seguía siendo nullable.

`V70` la dejó así **a propósito y con razón**: había anuncios anteriores cuya
propiedad tenía varios encargos candidatos, y elegir uno habría sido inventar de
cuál era. Esa razón se agotó — su *backfill* demostrable los resolvió todos.

**La decisión: `publicacion.id_captacion` es NOT NULL** (`V89`), y la entidad lo
declara igual (`@Column(name = "id_captacion", nullable = false)`).

**Por qué en el esquema y no sólo en el servicio.** `PublicacionServiceImpl` ya
exigía el encargo al crear, así que el caso que la nulabilidad protegía **ya no
se podía operar**: una fila sin encargo no se puede editar, ni cambiar de
estado, ni publicar — `exigirEncargoPropio` la rechaza. Mantener la columna
nullable no conservaba un dato: conservaba **una puerta** por la que un INSERT
directo, una carga o un productor futuro podían volver a meter un anuncio sin
dueño. La regla se pone donde ningún productor la puede saltar.

**Y la migración no inventa nada.** No rellena, no borra y no reinterpreta: un
bloque `DO` cuenta las filas sin encargo y, si hay alguna, **aborta diciendo
cuántas** y deja la base como estaba. A qué encargo pertenece un anuncio no se
deduce del inmueble — una propiedad con venta y alquiler vivos a la vez tiene
dos candidatos, y adjudicar mal falsea a la vez su importe, su operación y su
responsable. Lo ambiguo permanece FALTANTE y la migración no entra.

**Medición previa (2026-09-02), antes de escribir la migración:**

| base | `publicacion` | `id_captacion IS NULL` |
|---|---|---|
| `controllocal_dev` | 12 | **0** |
| `controllocal_repositorios` | 1081 | **0** |

**La guarda del servicio se queda, y no es redundante.** La rama «sin encargo
resuelto» de `exigirEncargoPropio` pasa a ser **defensiva**, y se conserva
porque `encargoDe` devuelve `null` también cuando el encargo existe pero es **de
otro tenant** —lo busca por `(organización, id)`— y cuando el id es `<= 0`. En
esos dos casos la respuesta correcta sigue siendo negarse; quitarla convertiría
su fallo en un `NullPointerException`.

**Lo que esta decisión NO hace.** No toca `id_propiedad`, que se conserva y no
es redundante: el listado heredado y el estado de publicación del local siguen
preguntando por inmueble. Y no cambia ninguna regla de quién publica: eso lo
sigue decidiendo `exigirEncargoPropio` con la autoridad del encargo (P0-4).

### D-P0-12 · Angular no decide autoridad *(CONTROL, 2026-09-01) · VIGENTE*

El Core responde, resuelto y por el cable: «**¿puedo traspasar?**», «**¿qué
destinos puedo seleccionar?**» y «**¿puedo editar / revisar / cerrar este
encargo?**».

- **«¿puedo traspasar?»** ya viaja: `responsabilidad.puedeTraspasar` (C7).
- **«¿qué destinos?»** estrena superficie propia:
  `GET /propiedades/{id}/responsable/candidatos`, **paginada y buscable**, con
  los candidatos **ya elegibles** para ese recurso y ese actor. Se pagina porque
  la lista es del **tenant**, no del formulario: filtrar en el cliente sobre una
  página devuelve resultados incompletos en cuanto haya más agentes que sitio.
- **«¿qué puedo hacer con este encargo?»** viaja como `capacidades
  {puedeEditar, puedeRevisar, puedeCerrar}` en las fichas **individuales** de
  `/captaciones` — por id y por código, que son **dos puertas al mismo
  recurso**—. En los listados llega nula y **no viaja**: allí la pregunta es
  «qué hay», no «qué puedo hacer con este», y su ausencia significa «no
  calculado aquí», no «no puedes».

**Cada capacidad la produce el mismo predicado que después deniega el comando**,
no una segunda tabla de decisión. Un segundo criterio «sólo para pintar» es
exactamente como se llega a un botón activo que el backend rechaza cuando la
persona ya escribió.

**Y el POST revalida todo.** La lista **no autoriza nada**: entre pedirla y
usarla, una cuenta se puede suspender. Por eso la lista y la revalidación
comparten **el mismo predicado SQL** (`DetalleAgenteRepository.CONDICION_CANDIDATO`)
— si fueran dos escrituras de la regla, una acabaría ofreciendo lo que la otra
rechaza.

**Sin candidatos y sin permiso son dos respuestas distintas.** Un actor que no
puede iniciar el traspaso de esa propiedad recibe **403** —el mismo predicado
que apaga el botón en la ficha—, no una lista vacía: devolver la primera por la
segunda deja al usuario buscando un agente que no existe. Y un id de otra
corredora, **404**, delante de todo.

**Alcance de esta decisión**: aquí entra sólo la **mitad backend**. El consumo
en Angular va en otro paquete, y hasta que llegue estas superficies existen y no
las usa nadie — que es el orden correcto, porque la alternativa es que la
pantalla se escriba primero con su propia copia de la regla.

**Ese otro paquete llegó el mismo 2026-09-01.** El SPA ya consume las dos
superficies: `traspaso-responsable` pide `candidatos` —con su texto y su
página— y **pinta lo devuelto sin depurar nada**, y `captacion-detail` lee las
tres `capacidades` en vez de deducirlas. Con ello **las tres pantallas P0
—`propiedad-detail`, `traspaso-responsable` y `captacion-detail`— dejan de leer
`sesion().rol` para decidir qué se ofrece**; `captacion-detail` ni siquiera
inyecta ya `AuthService`, que es lo que impide que la regla vuelva a entrar por
la puerta de atrás.

### D-P0-13 · La elegibilidad no puede caducar entre comprobar y escribir *(CONTROL, 2026-09-01) · VIGENTE*

D-P0-7 se **comprueba** y después se **escribe**. Entre las dos cosas cabe otra
transacción que desactive al destino —le suspenda la cuenta, lo ponga de
licencia o le cierre la supervisión— y la propiedad, o el encargo, acaba en
manos de alguien que **ya no puede recibirlo**, con todas las guardas verdes.
No es un fallo del predicado: el predicado dice la verdad en el instante en que
se pregunta, y el problema es que **el instante pasa**.

**La decisión: un único punto de serialización**, la fila `detalle_agente` del
agente afectado.

- **Quien lee la elegibilidad y escribe** —`ElegibilidadDeResponsable.exigirElegible`—
  **toma la fila primero y pregunta después**. Ese orden es la decisión: al
  revés, la respuesta se leería antes del candado y volvería a poder caducar
  entre las dos líneas.
- **Quien cambia la elegibilidad toma el mismo candado antes de escribir**:
  `PUT /agentes/{id}` cuando el cuerpo trae `estado` o `estadoOperativo` —los
  dos campos que la mueven— y `POST /asignaciones/reasignar`, porque cambiar de
  supervisor cambia la sexta condición para cualquier BRÓKER.

**Por qué UNA fila y no las cinco.** Las condiciones viven en cuatro tablas
(`persona_rol`, `credencial_usuario`, `usuario_organizacion`, `detalle_agente`)
más `supervision_agente`. Bloquearlas todas serían cinco candados y un orden que
respetar; bloquear una convenida basta **si todo el que cambia la elegibilidad
pasa por ella**. Se elige `detalle_agente` porque es la fila que **siempre**
existe para un agente y la que ya identifica al sujeto de la decisión.

**Y hay una condición de mantenimiento, dicha en vez de supuesta.** Medido el
2026-09-01: `usuario_organizacion.estado` y `persona_rol.vigencia_hasta`
**no tienen escritor de servicio**. Si alguien añade uno, **tiene que tomar este
bloqueo antes de escribir**, o la ventana vuelve a abrirse por la puerta nueva.
Está escrito en el javadoc de `DetalleAgenteRepository.bloquearParaGobierno`,
con la fecha, que es donde lo va a leer quien la abra.

**Lo que el candado NO hace: no ordena la historia.** No decide quién gana, sólo
que **haya un orden**. Si la baja entra primero, el traspaso se rechaza; si
entra después, queda un agente desactivado que lleva cartera — que es la
situación **legítima** que describe D-P0-8, no un fallo, y que un traspaso
explícito y trazable tiene que resolver.

**Cómo se prueba.** Con dos transacciones reales sobre PostgreSQL, en las dos
autoridades (`CausalidadDelTraspasoIntegrationTest` y
`CausalidadDeLaReasignacionIntegrationTest`): una se queda detenida **dentro**
de su transacción con la fila del destino tomada, la otra ejecuta la baja **por
su caso de uso real** —no por SQL— y la prueba **comprueba contra
`pg_stat_activity` que está de verdad esperando un lock** antes de soltar a la
primera. Y el caso inverso —baja comiteada primero— va en la misma prueba: es lo
que demuestra que el candado ordena la carrera **sin perdonar la guarda**.

#### Y la autoridad de edición tampoco *(F2.10, 2026-09-02)*

Lo de arriba cierra la ventana de la **elegibilidad del destino**. Quedaba
abierta la misma ventana sobre la **autoridad de edición misma**, que es la
regla que P0-1 y P0-4 congelaron:

> **PROPIEDAD: sólo su responsable ACTUAL modifica hechos físicos**
> **ENCARGO: lo edita su propio agente**

`PropiedadUniversalServiceImpl.editar` y `CaptacionServiceImpl.actualizar`
comprobaban la autoridad **al cargar** y escribían **después**, sin nada que
sujetara la fila del agregado entre las dos cosas. En esa ventana cabe un
traspaso —o una reasignación— **entero**: su compare-and-set toma la fila un
instante y la suelta al comitear, y la edición del agente **saliente** aterrizaba
sobre un agregado que ya es de otro. Es la misma clase de defecto que D-P0-13
—comprobar y luego actuar—, dicha sobre la otra autoridad.

**Esto no es lo que arreglaron F2.1 y F2.2.** El `updatable = false` de
`id_rol_responsable` y de `id_rol_agente` impide que esa edición tardía
**revierta** la autoridad. Nunca impidió que **se escriba**.

**La decisión, y no hay ninguna funcional nueva:** el comando que va a escribir
**toma la fila del agregado al cargarla** y comprueba la autoridad **bajo el
candado**. Con eso, el traspaso espera a que la edición termine, o la edición
espera al traspaso y entonces **ve al nuevo responsable/agente** y recibe
**exactamente el 403 que el Core ya produce** —`OTRO_RESPONSABLE` en la
propiedad, «el encargo lo lleva otro agente» en el encargo—. La regla es la de
siempre; lo único que cambia es que se comprueba **a tiempo**.

**Quién toma el candado**, medido el 2026-09-02 con un barrido sobre
`exigirEdicion(` y `exigirEdicionDelEncargo(` con control positivo:

```
  PROPIEDAD (fila `propiedad`)
    PUT  /propiedades/{id}                    editar
    DELETE /locales/{id}                      desactivar
    POST /locales/{id}/fotos                  agregarFoto
    DELETE /locales/fotos/{idFoto}            eliminarFoto

  ENCARGO (fila `captacion`)
    PUT  /captaciones/{id}                    actualizar
    PUT  /propiedades/{id}  (operaciones)     actualizarEncargo
    PUT  /propiedades/{id}  (condiciones)     aplicarCondicionesDe
    POST /locales/{id}/precios                registrar
    POST /encargos/{id}/publicaciones         crearEnEncargo
    PUT  /publicaciones/{id}                  actualizar
    PUT  /publicaciones/{id}/estado           cambiarEstado
```

**Quién NO lo toma, y por qué.** `AtributosGobernados.escribirEnEdicion` y
`retirar` exigen la autoridad pero **no cargan la propiedad**: la reciben ya
cargada de `editar`, que es quien tomó la fila. Lo mismo `AtributosDeEncargo`.
Las **lecturas** no lo toman —ni pueden: una transacción `readOnly` no ejecuta
`SELECT … FOR UPDATE`—. Y `decidir`, `cerrar`, `cerrarPorContrato` y
`reasignar` siguen cargando sin candado: los tres primeros son gobierno del
BRÓKER sobre el ciclo del encargo, no edición del trato bajo la autoridad del
agente, y `reasignar` ya resuelve su carrera con el compare-and-set — meterle el
candado además **invertiría** el orden de bloqueos (tomaría `captacion` antes que
`detalle_agente`) y abriría un ciclo de interbloqueo real.

**Orden de candados**, que es la mitad de la decisión:

```
  detalle_agente   ->   propiedad   ->   captacion

  traspaso      : detalle_agente del destino  ->  fila `propiedad` (CAS)
  edición de la propiedad : fila `propiedad`  ->  filas `captacion` que toque
  reasignación  : detalle_agente del destino  ->  fila `captacion` (CAS)
  edición del encargo     : fila `captacion`  (y nada más)
```

Ninguna vía va en sentido contrario —ninguna toma `captacion` y después
`propiedad`—, así que no hay ciclo. Está escrito en el javadoc de
`PropiedadRepository.bloquearParaEscritura`, que es donde lo va a leer quien
añada la siguiente vía.

**Una condición de mantenimiento, dicha en vez de supuesta.** El candado sólo
vale si la carga con candado es la **primera** de esa fila en la transacción:
Hibernate devuelve la instancia que ya está en el contexto **sin refrescarla**,
así que cargar primero y bloquear después toma el bloqueo de verdad y comprueba
la autoridad sobre el valor viejo — el defecto intacto y con aspecto de
arreglado. Por eso `POST /locales/{id}/precios` y `PUT /propiedades/{id}`
(operaciones) bloquean el **conjunto de encargos vivos** antes de resolver cuál
les toca, en orden de id: no saben en cuál van a escribir hasta después de
resolver la operación.

**Un efecto colateral que hubo que cerrar con esto.** Con el candado, la
reasignación **espera** a la edición y sigue después con su contexto de
persistencia cargado antes. `PUT /captaciones/{id}` **sustituye** la fila de
`condicion_economica_captacion` en cada edición (la asociación es
`orphanRemoval`), así que la reasignación se encontraba un proxy apuntando a una
fila borrada y estallaba con `EntityNotFoundException` — 500 y *rollback* de una
reasignación legítima. Se cierra materializando la condición económica en la
consulta de la ficha (`CaptacionRepository.FICHA`), que es donde debía estar: la
ficha lee **nueve** campos suyos siempre. Así la reasignación informa lo que
**leyó**, que es lo que una ficha hace.

**Cómo se prueba.** Los dos órdenes, en las dos autoridades, con dos
transacciones reales y sondeo de `pg_stat_activity`:

| caso | prueba |
|---|---|
| la edición tardía del saliente no aterriza (propiedad) | `CausalidadDelTraspasoIntegrationTest#laEdicionTardiaDelSalienteNoAterriza` |
| el orden inverso sigue siendo legítimo (propiedad) | `CausalidadDelTraspasoIntegrationTest#laEdicionQueTomoLaFilaEscribeYElTraspasoEsperaSuTurno` |
| la edición tardía del agente saliente no aterriza (encargo) | `CausalidadDeLaReasignacionIntegrationTest#laEdicionTardiaDelAgenteSalienteNoAterriza` |
| el orden inverso sigue siendo legítimo (encargo) | `CausalidadDeLaReasignacionIntegrationTest#laEdicionQueTomoLaFilaEscribeYLaReasignacionEsperaSuTurno` |

Las dos últimas **sustituyen** a `unaEdicionConcurrenteNoRevierteUnTraspaso` y a
`elFlushDeUnaEdicionNoRevierteLaReasignacion`, con todas sus exigencias
conservadas y una más —la sonda del lock—. No se borró nada: el montaje anterior
lanzaba el traspaso en el **hilo principal** mientras la edición estaba parada, y
con el candado ese montaje se auto-bloquea, porque el único hilo que puede soltar
el *latch* es el que está esperando la fila.

## Las mismas decisiones, dichas sobre el ENCARGO *(2026-09-01)*

P0 tiene **dos** autoridades mutables, no una:

```
  PROPIEDAD  ->  propiedad.id_rol_responsable  ->  POST /propiedades/{id}/responsable
  ENCARGO    ->  captacion.id_rol_agente       ->  POST /captaciones/{id}/reasignar
```

Los cortes anteriores cerraron la primera. La segunda seguía siendo exactamente
lo que aquélla dejó de ser, y por eso este corte no inventa reglas nuevas:
**aplica las mismas** al otro sujeto. Que las dos tuvieran la misma forma de
defecto no es casualidad — es la misma clase de defecto.

**Una sola puerta canónica.** `CaptacionServiceImpl.reasignar` es el único sitio
que mueve `captacion.id_rol_agente` sobre un encargo que ya existe. No se puede
mover por *dirty checking*, `save()` genérico, actualización de otra parte del
agregado, `PUT` general, mapper, copia, cascada, frontend ni sincronización
indirecta, y eso **no se confía a la disciplina**: la asociación está mapeada
`@JoinColumn(updatable = false)` —así que ningún *flush* la escribe— y en
`UPDATE` sólo la toca `CaptacionRepository.cambiarAgenteSi`. Lo vigilan dos
reglas de `AutoridadDeLaPropiedadTest`, gemelas de las de la propiedad: quién
puede llamar a `Captacion#setAgente` (los tres altas, que son el `INSERT`, y la
puerta canónica) y quién puede ejecutar el compare-and-set.

**Editar el encargo no lo reasigna.** `PUT /captaciones/{id}` escribe importe,
exclusividad, vigencia, urgencia y observaciones, y **no puede tocar el agente**.
Antes sí podía, y sin pedirlo: bastaba con haber cargado la fila antes de una
reasignación para que su *flush* la devolviera al agente anterior, dejando un
agente cambiado **sin fila que lo explique** y un historial que afirmaba «de A a
B» sobre un encargo que respondía ante A.

**Reasignar es un caso de uso explícito, y quién puede NO cambia.** Sigue siendo
D-S0-17 fila 6 —BRÓKER dentro de su equipo, TENANT_ADMIN entre equipos—, ni un
rol más. El TENANT_ADMIN **no** se convierte en bróker: sigue sin decidir ni
cerrar encargos. Y el responsable de la **PROPIEDAD** no gana ninguna autoridad
sobre el **ENCARGO**: son dos autoridades distintas y siguen separadas.

**El destino tiene que poder recibirlo (D-P0-7).** Las mismas cinco condiciones,
el mismo componente y el mismo predicado SQL que el traspaso de una propiedad.
Un agente que no puede recibir una propiedad tampoco puede recibir un encargo:
la pregunta —«¿está esta persona en condiciones de responder por trabajo
comercial hoy?»— es literalmente la misma.

**Y parte de un estado que alguien miró (D-P0-9).** El cuerpo lleva
`idAgenteActual`, **obligatorio**; su ausencia es **400**, porque un cuerpo sin
declaración no dice «me da igual quién lo lleve», dice que **nadie miró**. Si al
ejecutarse el encargo ya no lo lleva ese agente: **409**, nada escrito, y **no se
reinterpreta** —un «cambia A por C» que llega cuando ya lo lleva B no se
convierte en «cambia B por C»—. De un agente concreto parte **exactamente una**
reasignación legítima.

> **No hay equivalente a `sinResponsableActual`, y es una diferencia real.**
> `captacion.id_rol_agente` es **NOT NULL desde V5**: un encargo sin agente no
> existe, así que FALTANTE no es un estado observable aquí y no hay una segunda
> forma de declarar el punto de partida. La propiedad sí lo tiene porque su
> hueco **es** un estado.

**Y es un solo hecho (D-P0-10).** Columna y fila de `reasignacion_captacion` van
en la misma transacción; si cualquiera falla se deshacen las dos. No queda agente
cambiado sin traza ni traza de un cambio que no ocurrió.

**Angular no decide los destinos (D-P0-12).** El Core publica
`GET /captaciones/{id}/reasignacion/candidatos` —paginado y buscable— y la
capacidad `puedeReasignar` en la ficha individual. Las **dos** pantallas que
reasignan en el SPA consumen las dos superficies.

### Trazabilidad: campo exigido → columna, en las dos tablas

Ningún cambio de autoridad es un simple `UPDATE`. Lo exigido por CONTROL, y
dónde vive en cada una de las dos tablas — **sin migración: ya existía todo**.

| Campo exigido | `asignacion_responsable_propiedad` (PROPIEDAD) | `reasignacion_captacion` (ENCARGO) |
| --- | --- | --- |
| **ANTES** (saliente) | `id_rol_responsable_anterior` (NULL = FALTANTE, y es un dato) | `id_rol_agente_anterior` (NOT NULL: un encargo sin agente no existe) |
| **DESPUÉS** (destino) | `id_rol_responsable_nuevo` | `id_rol_agente_nuevo` |
| **QUIÉN** | `id_persona_actor` | `id_persona_actor` |
| **CON QUÉ AUTORIDAD** | `tipo_rol_actor` (la banda). El **alcance efectivo** lo implican las guardas que corrieron: sin ellas la fila no existe | `tipo_rol_actor`, y además `id_rol_broker` cuando el actor es BRÓKER (nulo para el TENANT_ADMIN, cuyo rol operativo es el de gobierno) |
| **POR QUÉ** | `motivo`, con el mínimo de `PoliticaComercial` | `motivo`, con el **mismo** mínimo |
| **CUÁNDO** | `fecha_asignacion` | `fecha_cambio` |
| **SOBRE QUÉ** | `id_propiedad` | `id_captacion` |
| **QUÉ ESTADO OBSERVÓ** | el **anterior es el observado**: lo declaró el comando y el compare-and-set lo exigió, así que la fila dice de dónde salió el traspaso **que de verdad ocurrió**, no de dónde estaba la entidad cuando se cargó | igual, sobre `id_rol_agente_anterior` |
| **QUÉ CLASE DE HECHO** | `origen` (`ALTA` \| `TRASPASO`), que **no se deduce** de que falte el anterior | no aplica: esta tabla sólo registra reasignaciones, y el alta del encargo es el `INSERT` de `captacion` |

**El estado observado no tiene columna propia, y no le hace falta.** Después de
la precondición en memoria y del compare-and-set, «observado» y «anterior» son
**el mismo valor por construcción**: si difirieran, la fila no se habría
escrito. Una columna aparte sería un segundo sitio donde vive la misma verdad —y
el sitio donde empezaría a divergir.

## REGISTRO DE CIERRE P0

Los hallazgos de P0, con la regla que protege cada uno y **el test o gate que lo
demuestra**. No es un registro de actividad: es lo que hay que volver a mirar si
alguna de estas reglas se toca. Sin logs — trazabilidad normativa.

| ID | Hallazgo | Regla que protege | Resolución | Test / gate que lo demuestra | Estado |
| --- | --- | --- | --- | --- | --- |
| **P0-R1** | El *dirty checking* de `PUT /propiedades/{id}` podía **revertir el responsable**: la entidad se guarda entera y devolvía la columna al valor cargado, sin fila de expediente | D-P0-10 — no queda responsable cambiado sin traza, ni por una puerta que no es el traspaso | `Propiedad.idRolResponsable` mapeada `@Column(updatable = false)`; en `UPDATE` sólo la escribe el compare-and-set | `CausalidadDelTraspasoIntegrationTest#unaEdicionConcurrenteNoRevierteUnTraspaso` | CERRADO |
| **P0-R2** | Lo mismo sobre el **ENCARGO**: el *flush* de `PUT /captaciones/{id}` devolvía `id_rol_agente` al agente anterior tras una reasignación comiteada | D-P0-10 sobre la segunda autoridad | `Captacion.agente` con `@JoinColumn(updatable = false)`; el `UPDATE` lo hace sólo `cambiarAgenteSi` | `CausalidadDeLaReasignacionIntegrationTest#elFlushDeUnaEdicionNoRevierteLaReasignacion` (medido rojo el 2026-09-01, antes del mapeo: `expected: <29> but was: <28>`) | CERRADO |
| **P0-R3** | **Angular reconstruía la elegibilidad del ENCARGO**: dos pantallas pedían `GET /agentes` y depuraban en el cliente —una con dos de las seis condiciones sobre una página, la otra con ninguna— | D-P0-12 — el cliente no decide autoridad, tampoco por omisión | `GET /captaciones/{id}/reasignacion/candidatos` desde el **mismo** predicado SQL que revalida el POST; el SPA pinta lo devuelto | `reasignaciones-captacion.spec.ts` y `captacion-review.spec.ts` (piden al Core y no filtran); `AutoridadDeEdicionIntegrationTest` en el Core | CERRADO |
| **P0-R4** | `PUT /captaciones/{id}` escribía el encargo —importe, exclusividad, vigencia— **sin la autoridad canónica**: sólo tenía el alcance de LECTURA | P0-4 — el encargo lo edita **su** agente | `autoridad.exigirEdicionDelEncargo` en `actualizar`, exigido por gate | `AutoridadDeLaPropiedadTest#ningunHitoEconomicoSinLaAutoridadDelEncargo`; `AutoridadDeEdicionIntegrationTest` | CERRADO |
| **P0-R5** | `POST /locales/{id}/precios` escribía la serie económica **sin frontera de tenant** y sin la autoridad del encargo que autorizó el importe | P0-4 + D-P0-6 | Autoridad del encargo resuelta **después** de saber de qué encargo es el importe; tenant delante de todo | `AutoridadDeEdicionIntegrationTest`; `LecturaHistoricaIntegrationTest` | CERRADO |
| **P0-R6** | `puedeTraspasar` se calculaba **por banda**, sin mirar el saliente: prometía un traspaso que el POST negaba | C7 — la ficha no puede prometer lo que el POST va a negar | El booleano lleva el **mismo** predicado del saliente (`alcanzaIncluidoSinDueno`) | `AlcanceYGobiernoDeLaAutoridadIntegrationTest`; `propiedad-detail.spec.ts` | CERRADO |
| **P0-R7** | Una **supervisión expirada** no estaba cubierta: el alcance se leía sin exigir `fecha_fin is null` en todas las puertas | C6 — un traspaso tiene dos extremos y los dos se comprueban | Todas las preguntas de supervisión salen del mismo `Alcances` | `AlcanceYGobiernoDeLaAutoridadIntegrationTest` | CERRADO |
| **P0-R8** | El **destino de un traspaso no tenía que poder recibir**: bastaba con que el bróker lo alcanzara | D-P0-7 — cinco condiciones, cada una contra la autoridad que ya decide ese hecho | `ElegibilidadDeResponsable.exigirElegible`, última guarda y antes de escribir nada | `AlcanceYGobiernoDeLaAutoridadIntegrationTest`; `CausalidadDeLaReasignacionIntegrationTest#elDestinoNoElegibleNoRecibeElEncargo` | CERRADO |
| **P0-R9** | Los **históricos** (expediente de traspasos, serie económica) eran legibles por todo el tenant | D-P0-6 — el pasado comercial es superficie de gobierno | `exigirLecturaDelExpediente` / `puedeLeerHistoricoDelEncargo`, con el tenant delante | `LecturaHistoricaIntegrationTest` | CERRADO |
| **P0-R10** | El **TENANT_ADMIN decidía y cerraba encargos** en el Core: lo único que lo frenaba era el `@PreAuthorize` del controlador | D-S0-17 filas 5 y 7 — son operaciones comerciales y el gobierno no las hereda | `exigirBandaComercial` en el servicio, **después** de `cargarConAcceso` para conservar el 404 de otro tenant | `AutoridadDeEdicionIntegrationTest#elGobiernoDelTenantNoDecideNiCierraEncargos` | CERRADO |
| **P0-R11** | El traspaso era ***last-write-wins***: dos comandos desde el mismo A entraban los dos | D-P0-9 — parte de un estado que alguien miró, y no se reinterpreta | Responsable observado obligatorio + compare-and-set en SQL | `CausalidadDelTraspasoIntegrationTest` (la clase entera: dos transacciones vivas y sondeo de `pg_stat_activity`) | CERRADO |
| **P0-R12** | La **atomicidad** del traspaso estaba *declarada* (`@Transactional`) pero no demostrada | D-P0-10 — o entran todas las partes, o no entra ninguna | Fallos inyectados en el rastro y en el evento, con control positivo | `CausalidadDelTraspasoIntegrationTest#sinRastroNoHayTraspaso` y `#sinEventoNoHayTraspaso` | CERRADO |
| **P0-R13** | **TOCTOU de la elegibilidad**: entre comprobarla y escribir cabía una baja, y el destino acababa con trabajo que ya no podía recibir | D-P0-13 — un único punto de serialización, la fila `detalle_agente` | `bloquearParaGobierno` antes de preguntar en `exigirElegible`, y el mismo candado en `PUT /agentes/{id}` y `POST /asignaciones/reasignar` | `CausalidadDelTraspasoIntegrationTest#laElegibilidadDelDestinoNoCaducaAMitadDeUnTraspaso` y `CausalidadDeLaReasignacionIntegrationTest#laElegibilidadDelDestinoNoCaducaAMitadDeUnaReasignacion` | CERRADO |
| **P0-R14** | La reasignación del **ENCARGO** no tenía **causalidad, ni concurrencia, ni atomicidad demostradas**, ni frontera de tenant en el destino | D-P0-7 + D-P0-9 + D-P0-10 sobre la segunda autoridad | Puerta canónica única, `idAgenteActual` obligatorio, compare-and-set, elegibilidad y filtro de tenant en el destino | `CausalidadDeLaReasignacionIntegrationTest` (la clase entera); `ReasignacionRequestTest`; `AutoridadDeLaPropiedadTest#unSoloEscritorDelAgenteDelEncargo` y `#unSoloEscritorDelCompareAndSetDelAgente` | CERRADO |
| **P0-R15** | La **edición del saliente aterrizaba tras un traspaso o una reasignación ya comiteados** (TOCTOU de la autoridad de edición): se comprobaba al cargar y se escribía después, sin candado sobre la fila del agregado | regla P0-1 y regla P0-4, sin decisión funcional nueva — la edición tardía recibe el 403 que el Core ya produce | Candado de escritura (`PESSIMISTIC_WRITE`) al cargar en `editar` y en `actualizar`, y en las demás vías que escriben hechos de la propiedad o del encargo; orden `detalle_agente` → `propiedad` → `captacion` | `CausalidadDelTraspasoIntegrationTest#laEdicionTardiaDelSalienteNoAterriza` y `#laEdicionQueTomoLaFilaEscribeYElTraspasoEsperaSuTurno`; `CausalidadDeLaReasignacionIntegrationTest#laEdicionTardiaDelAgenteSalienteNoAterriza` y `#laEdicionQueTomoLaFilaEscribeYLaReasignacionEsperaSuTurno` | CERRADO |

| **P0-R16** | **`publicacion.id_captacion` seguía siendo nullable**: la regla «un anuncio pertenece a un encargo» vivía sólo en `PublicacionServiceImpl`, así que un INSERT directo, una carga o un productor nuevo podían dejar un anuncio sin operación, sin rótulo de importe y sin agente que responda por él | D-P0-11 — una publicación siempre pertenece a un encargo | `V89`: bloque `DO` que **aborta con el recuento** si hay filas huérfanas (nunca inventa a qué encargo pertenecen) + `SET NOT NULL` + `COMMENT`; entidad `nullable = false`; la rama del servicio queda como guarda defensiva (tenant ajeno, id `<= 0`) | `PropiedadUniversalIntegrationTest#unAnuncioSinEncargoNoEntraNiPorSql` (INSERT por SQL crudo → `DataIntegrityViolationException`, más `information_schema` con control positivo) y `#v89ConstaAplicada` | CERRADO |

| **P0-R17** | **La columna «Alcance» de la matriz era prosa que nadie verificaba**: el gate sólo exigía que no estuviera vacía, y sólo en las filas `TODOS`. Así fue como la fila de `POST /locales/{id}/precios` declaró «un local de **sus captaciones**» mientras el código comprobaba únicamente el tenant | `N36` — la matriz es fuente de verdad o no lo es; una fila que puede mentir no declara nada | Cada fila **protegida** termina su Alcance con **exactamente un** token `{autoridad: CLAVE}` de un **vocabulario cerrado**; las claves `Clase.metodo` se comprueban **contra el bytecode** de `com.controllocal.service`, y `GOBIERNO` sólo vale en filas cuyo rol es exactamente `TENANT_ADMIN`. Las claves en mayúsculas (`TENANT`, `BANDA`, `SESION`, `GOBIERNO`) son **confesiones**: dicen que no hay componente de autoridad | `MatrizOperacionRolTest#todaFilaProtegidaDeclaraQueAutoridadDecideSuAlcance`, con control positivo sobre `AutoridadDePropiedad.exigirEdicion`. Sabotajes medidos el 2026-09-02: token quitado → rojo; clave inventada → rojo; `GOBIERNO` sobre una fila BROKER → rojo | CERRADO |
| **P0-R18** | **El gate de autoridad no veía las escrituras por *dirty checking***: su predicado era **por repositorio**, así que `CaptacionServiceImpl#decidir`, `#cerrar` y `#cerrarPorContrato` —que mutan la `Captacion` gestionada y dejan que el *flush* la vuelque— estaban **fuera del universo**. **Y al construir el sabotaje apareció un defecto mayor y anterior**: la cobertura transitiva dejaba que la guarda de un **hermano** certificara la escritura de al lado — quitar `exigirEdicionDelEncargo` de `PropiedadUniversalServiceImpl#actualizarEncargo` seguía **VERDE** porque su único llamador, `editar`, alcanzaba la guarda por la rama de las **condiciones**, que sólo se ejecuta si el comando las trae | `N43` + P0-4 — la guarda tiene que proteger **el hecho**, no la puerta ni la rama vecina | (a) los dos predicados cuentan también a quien invoca un **MUTADOR** de `Propiedad` o de `Captacion`, y los mutadores se **derivan del bytecode** (método con un acceso `SET` a campo propio), no de una lista; (b) la recursión que busca la guarda **se detiene en todo auxiliar que alcance una escritura de este universo**, aunque escriba a través de un colaborador exento; (c) `decidir` y `cerrar` no se eximen: se declaran bajo una **autoridad alternativa** (`cargarConAcceso` **+** `exigirBandaComercial`, las dos exigidas); (d) `PropiedadUniversalServiceImpl#autoridadDelEncargo` entra en `PREGUNTAN_NO_IMPIDEN`; (e) las exenciones cuyo motivo son sus llamadores llevan **censo comprobado** | `AutoridadDeLaPropiedadTest`: `#elGateVeLoQueElPredicadoPorRepositorioNoVeia`, `#losMutadoresSalenDelBytecode`, `#elCensoDeLlamadoresQueSostieneCadaExencionSigueSiendoCierto`. Sabotajes medidos el 2026-09-02: quitar `exigirEdicionDelEncargo` de `actualizarEncargo` → **rojo** (era verde antes de esta corrección); quitar `exigirBandaComercial` de `cerrar` → **rojo** (con el predicado anterior, **verde**: contraprueba ejecutada); quitar `exigirEdicion` de `LocalComercialServiceImpl#desactivar` → rojo | CERRADO |
| **P0-R19** | **El control de cobertura sólo miraba `domain.inmueble`**, y `Captacion` —que **es** el ENCARGO— vive en `domain.comercial`: ninguna de sus 24 tablas tenía que estar clasificada, así que una entidad nueva ahí **no ponía nada en rojo** | `N44` — el gate no puede echar de menos lo que nunca se nombró | El control enumera las entidades JPA de **los dos** paquetes (35 el 2026-09-02, con control positivo `>= 30` y exigiendo `propiedad` y `captacion`). Las 24 comerciales quedan clasificadas: tres **vigiladas**, `condicion_economica_captacion` vigilada **por cascada** de `CaptacionRepository` —su única vía de escritura, y la que descubrió P0-R4—, y 20 en `FUERA_DE_LOS_DOS_UNIVERSOS` con un motivo que nombra **su agregado y la guarda que decide sobre él** | `AutoridadDeLaPropiedadTest#ningunaTablaDelInmuebleSinClasificar`. Sabotaje medido el 2026-09-02: quitar la clasificación de `visita` → rojo nombrando la tabla | CERRADO |
| **P0-R20** | **La política de qué documento viaja no estaba escrita**, así que «gobierna» y «está en la lista blanca» eran dos cosas sin relación: un documento que decide y no viaja no gobierna nada, y una regla que sólo vive en el inventario no obliga a nadie | `N41`/`N42` — la autoridad tiene que estar donde un clon limpio la encuentre | Tres líneas en `AGENTS.md`: **decisión VIGENTE que gobierna código → viaja**; **historia, auditoría y evidencia fechada → pueden quedar ignoradas**; **`pendientes-brox.md` → inventario, nunca autoridad**. Y la cabecera de este documento declara que es el único normativo de P0 | `git check-ignore -v` el 2026-09-02: decisión y matriz **no** ignoradas, `pendientes-brox.md` **sí**; y `AutoridadDeLaPropiedadTest#laAutoridadQueGobiernaViajaConElCodigo` lo rompe si sale de la lista blanca. Barrido el mismo día: las cinco decisiones P0 citadas en el inventario (`D-P0-6`, `-7`, `-11`, `-12`, `-13`) están todas aquí | CERRADO |

| **P0-R21** | **El rastro de reasignaciones del ENCARGO era de todo el tenant**: `GET /captaciones/reasignaciones` servía `findByOrganizacionIdOrderByIdDesc(actor.idOrganizacion())` sin pasar por `Alcances`, así que un BRÓKER leía de qué agente salió cada encargo, hacia cuál fue y **con qué motivo** en equipos que no supervisa. `F3` lo dejó tokenizado como `{autoridad: TENANT}` —decía la verdad— porque estrecharlo cerraba una puerta | D-P0-6, fila del **histórico de ENCARGO**: *BROKER los que están dentro de su alcance; TENANT_ADMIN todo el tenant*. Aplicada a esta superficie **sin regla nueva** — quien ve el encargo ve su rastro | `ReasignacionCaptacionRepository.bitacora` con el mismo par `(:sinScope, :rolesAgente)` de los listados y sobre la misma columna (`captacion.id_rol_agente`); `Alcances.de` en el servicio, con `vacio()` → lista vacía; **banda delante** (`exigirBandaQueGobiernaElEncargo`), porque a un AGENTE el alcance le concedería «lo suyo»; el derivado tenant-wide **se retira** para que no quede una segunda puerta. Sin tocar DTO ni orden. **Interpretación declarada**: el alcance es el del encargo **de hoy**, no el del agente saliente ni el del bróker que firmó | `LecturaHistoricaIntegrationTest#elRastroDeReasignacionesSeAcotaConElAlcanceDelEncargo` (bróker A ve la reasignación de su equipo y **no** la del encargo que hoy lleva el equipo B; bróker B al revés; TENANT_ADMIN las dos y en el mismo orden; AGENTE 403). Medido **rojo primero** el 2026-09-02: `expected: <false> but was: <true>`. Sabotajes el mismo día: forzar `sinScope=true` → rojo; quitar la guarda de banda → rojo (*"Expected AccesoNoAutorizadoException to be thrown, but nothing was thrown"*) | CERRADO |

**Lo que este registro no dice.** No dice que P0 esté cerrado como etapa. Dice qué
hallazgos están cerrados **y con qué se comprueba**, que es lo que permite volver
a mirarlos sin reconstruir la historia. Y **`N41` sigue abierta**: la política de
qué viaja está escrita, pero clasificar los 18 `decision-*.md` que hoy no viajan
es una decisión del titular, no del CONSTRUCTOR.

## Las tres autoridades, que pueden ser tres personas

```
PROPIEDAD            ->  su responsable actual   ->  los hechos físicos
ENCARGO de VENTA     ->  su propio agente        ->  ese encargo
ENCARGO de ALQUILER  ->  su propio agente        ->  ese encargo
```

Se comprueban **por separado y no con un OR**, en las dos direcciones:
responder por la propiedad **no** concede permiso sobre el encargo de otro, y
tener un encargo **no** concede autoridad sobre la ficha física.

---

## Dónde vive la regla, y por qué ahí

En **`service/soporte/AutoridadDePropiedad`**, no en el `@PreAuthorize`. Dos
razones medidas:

1. Las **vías de escritura** están repartidas por varios servicios, y **las
   cuenta el gate en cada build contra el bytecode** — aquí no va una cifra
   transcrita a mano: el inventario inicial se quedó corto **el mismo día**, y
   otra vez en la auditoría del **2026-09-01**, cuando `PUT /captaciones/{id}`
   apareció **fuera del universo del gate**. Una anotación protege *una puerta*;
   la autoridad tiene que proteger *el hecho*.
2. **KAIROS no tiene escritor propio**: entra por los mismos casos de uso con
   `X-Canal`/`X-Origen`. Por eso Web y KAIROS no reciben la misma regla porque
   se hayan comprobado las dos, sino porque **es la misma**.

### Lo que lo sostiene cuando nadie mira

| Mecanismo | Qué impide |
|---|---|
| `AutoridadDeLaPropiedadTest` (gate) | Que nazca una escritura nueva sin pasar por la autoridad, o con la autoridad **equivocada** — la de la propiedad y la del encargo se exigen por separado |
| `uq_asignacion_alta_por_propiedad` | Una **segunda** fila `origen='ALTA'` sobre la misma propiedad. Es el límite crítico hecho estructura, no comentario |
| `ck_asignacion_resp_banda` | Que un AGENTE firme un TRASPASO, o que un ALTA traiga predecesor |
| `AutoridadDeEdicionIntegrationTest` | Las comprobaciones de comportamiento contra PostgreSQL real: ver no concede editar, FALTANTE no habilita a nadie, cada encargo responde ante su agente por todas sus puertas (incluida `PUT /captaciones/{id}`) |

---

## El cliente no decide

La ficha publica `responsabilidad {idResponsable, nombre, puedeEditar, motivo,
motivoTexto, puedeTraspasar}` y cada encargo publica su `puedeEditar`. **Lo
resuelve el mismo método que después deniega la escritura**, así que la pantalla
no puede prometer lo que el Core va a negar.

Desde **D-P0-12** (2026-09-01) el mismo criterio cubre las tres preguntas que
faltaban: los **destinos** posibles de un traspaso los ofrece el Core
(`GET /propiedades/{id}/responsable/candidatos`), y las **capacidades** de un
encargo viajan con su ficha individual (`capacidades {puedeEditar, puedeRevisar,
puedeCerrar}`). Ninguna de las dos autoriza nada: el comando revalida.

El SPA **no lleva ninguna lista de roles ni de claves**. Antes sí:
`propiedad-detail` decidía con `sesion().rol === 'AGENTE'`, una copia del gate
del backend que dejó de ser cierta con `V87`.

---

## Frontera de información

`id_rol_responsable` es **dato de gobierno interno**. Viaja en la ficha
operativa del tenant y **no sale** por ninguna proyección externa. La regla de
construcción que lo hace sostenible: **nunca serializar el modelo interno
completo para después ocultar campos** — las respuestas se montan campo a campo.

Ver `decision-brox-intelligence-alcances-y-frontera.md`.

---

## Lo que esta decisión NO decide

- ~~**No restringe ninguna lectura.** El histórico de la ficha lo lee todo el
  tenant, igual que antes de P0.~~ **Dejó de ser cierto el 2026-09-01**: lo
  decidió **D-P0-6** más arriba, que es la otra decisión que esta frase
  anunciaba (`N39`). Lo que sigue en pie es la mitad que no cambió: **el
  traspaso no abre nada** — concede escritura sobre lo vigente, y no mueve ni
  una línea de lo que el nuevo responsable puede leer.
- **No bloquea la publicación ni el matching** por FALTANTE.
- **No toca `id_rol_incorporo`**, que conserva exclusivamente su significado
  histórico.
