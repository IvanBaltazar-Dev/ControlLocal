# D-P0-1 · Autoridad de edición de la propiedad

**Fecha:** 2026-08-30
**Estado:** **VIGENTE.** Implementada en `V87` + `V88`.
**Decisión del titular.** Este documento es la **autoridad**: dice qué regla
está viva. Las mediciones fechadas de la implantación viven aparte, en
`backend-spring/verificacion/evidencia/2026-08-30-p0-autoridad-de-edicion.md`,
y no repiten esta regla — la citan.

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

**Y no es la misma pregunta que `puedeTraspasar`.** Ofrecer el traspaso sale de
la **banda**; leer el expediente, del **alcance sobre el responsable**. Siguen
siendo dos preguntas y el booleano del cable sigue saliendo de la banda.

> **Matizado por C6 (2026-08-30).** Aquí se ilustraba con «un bróker de otro
> equipo **puede traspasar** una propiedad y no leer su expediente», y ese
> ejemplo ya **no es cierto** de una propiedad **con** responsable: desde C6 ese
> bróker tampoco la traspasa. El ejemplo que sigue siendo cierto es el de la
> propiedad **FALTANTE**, donde sí entra por las dos puertas. Y lo que no
> cambió es el booleano: `puedeTraspasar` es de banda, así que puede ofrecerse
> y el POST rechazar. Ver «Lo que C6 deja anotado».

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

#### Lo que C6 deja anotado

`responsabilidad.puedeTraspasar` **no se estrechó**. Sigue valiendo
`!actor.esAgente()` —la primera guarda de `asignar`— y por tanto ya **nunca fue**
la condición completa: el alcance sobre el **destino** no se puede resolver en la
ficha porque ahí todavía no hay destino elegido. Lo que cambia con C6 es que
ahora tampoco es completa **por el lado del saliente**, y ése **sí** sería
resoluble en la ficha.

- **Coste medido:** para una propiedad cuyo responsable es de otro equipo, el
  botón se ofrece y el POST responde 403 con el motivo escrito por el Core. No
  es un permiso de más — se deniega igual —, es un botón que en ese caso ya no
  lleva a ningún destino válido.
- **Por qué no se cambió aquí:** estrecharlo cambia el cable de la ficha y una
  respuesta que **C5 dejó medida** (`traspasarYLeerElExpedienteSiguenSiendoDosPreguntas`),
  y el encargo de este corte era C6 **sin estrechar ni ensanchar C5**. Es una
  decisión funcional del titular, no una consecuencia mecánica: queda
  **inventariada**, no resuelta por omisión.

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

1. Hay **trece vías de escritura** repartidas por cinco servicios. Una anotación
   protege *una puerta*; la autoridad tiene que proteger *el hecho*.
2. **KAIROS no tiene escritor propio**: entra por los mismos casos de uso con
   `X-Canal`/`X-Origen`. Por eso Web y KAIROS no reciben la misma regla porque
   se hayan comprobado las dos, sino porque **es la misma**.

### Lo que lo sostiene cuando nadie mira

| Mecanismo | Qué impide |
|---|---|
| `AutoridadDeLaPropiedadTest` (gate) | Que nazca una escritura nueva sin pasar por la autoridad, o con la autoridad **equivocada** — la de la propiedad y la del encargo se exigen por separado |
| `uq_asignacion_alta_por_propiedad` | Una **segunda** fila `origen='ALTA'` sobre la misma propiedad. Es el límite crítico hecho estructura, no comentario |
| `ck_asignacion_resp_banda` | Que un AGENTE firme un TRASPASO, o que un ALTA traiga predecesor |
| `AutoridadDeEdicionIntegrationTest` | Las 25 comprobaciones de comportamiento contra PostgreSQL real |

---

## El cliente no decide

La ficha publica `responsabilidad {idResponsable, nombre, puedeEditar, motivo,
motivoTexto}` y cada encargo publica su `puedeEditar`. **Lo resuelve el mismo
método que después deniega la escritura**, así que la pantalla no puede prometer
lo que el Core va a negar.

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

- **No restringe ninguna lectura.** El histórico de la ficha lo lee todo el
  tenant, igual que antes de P0; el traspaso **no abre nada** — concede
  escritura sobre lo vigente. Si esa lectura debe estrecharse, es otra decisión
  (ficha `N39`).
- **No bloquea la publicación ni el matching** por FALTANTE.
- **No toca `id_rol_incorporo`**, que conserva exclusivamente su significado
  histórico.
