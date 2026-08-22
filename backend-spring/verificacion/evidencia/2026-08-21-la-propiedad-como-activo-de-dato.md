# La propiedad como activo de dato · V76

**Cerrado el 2026-08-21.** Migración `V76__la_propiedad_como_activo_de_dato.sql`.

Microcorte de convergencia, igual que V75 y por la misma grieta: **el registro
seguía siendo un acto comercial disfrazado**. V75 le quitó la operación
obligatoria; V76 le quita la última atadura y separa lo que BROX *sabe* de lo
que BROX *gestiona*.

---

## Las dos frases que congela

> Una Propiedad representa un inmueble **conocido por BROX**, no necesariamente
> una oferta gestionada por BROX. Su existencia, procedencia e historia
> observada son independientes de Prospecciones y Encargos. Los hechos
> comerciales solo nacen cuando existe la relación comercial que los autoriza.

> BROX **nunca** convierte una observación de mercado en un hecho comercial ni
> inventa una relación para poder conservar conocimiento.

---

## El defecto

Quitada la operación, quedaba el titular. `propiedad.id_rol_propietario` era
`NOT NULL`, así que para anotar un departamento visto en un portal —90 m²,
180 000 USD, anunciado el martes— había exactamente una salida: **inventar un
propietario**. Y un «Propietario por confirmar» es una persona falsa dentro de
la cartera: cuenta en los listados, aparece en las búsquedas y no se distingue
de una real.

Lo mismo por el otro lado: el precio observado no tenía dónde ir. `precio_propiedad`
es la serie del **encargo** —cada hito lo autorizó alguien—, y escribir ahí lo
que se vio en un anuncio convierte un rumor en un hecho comercial. Sin sitio
propio, ese conocimiento o se pierde o se falsifica.

Las dos cosas son el mismo error: **usar una relación comercial como envase de
conocimiento.**

---

## Lo que cambia

### 1 · La titularidad se muda del registro al encargo

| Antes | Desde V76 |
|---|---|
| `id_rol_propietario` `NOT NULL` | nullable, con `ck_propiedad_titular_completo`: van los dos campos o ninguno |
| El alta exige ≥ 1 titular | El alta lo **pregunta** y no bloquea |
| — | **Encargar** exige ≥ 1 titularidad vigente |

La exigencia vive en **un solo sitio**, `service/soporte/TitularParaEncargar`, y
no en los tres servicios que abren una captación. No es estética: hay **tres**
caminos hasta una fila de `captacion` —el alta con operaciones,
`POST /captaciones` y `captar` desde una prospección— y hasta V76 **ninguno**
comprobaba la titularidad. El sistema parecía seguro solo porque el alta la
exigía, que es justo el sitio del que esta decisión la retira. Con la regla
repetida tres veces bastaría olvidarla en una; escrita una vez, quien abra el
cuarto camino la encuentra.

El gate es **débil a propósito**: pide *una titularidad vigente*, no cuotas al
100 % ni representante formal. Se midió lo que el negocio consume aguas abajo y
es un nombre y un interlocutor — `ComisionServiceImpl` no menciona al
propietario en sus 478 líneas, `ContratoServiceImpl` solo lo pinta, el reporte
al propietario ni siquiera lo resuelve. Cada condición inventada es una que
alguien acabará rellenando a mano.

### 2 · La propiedad declara cómo llegó a conocerse

`propiedad.origen_incorporacion`, `NOT NULL`, tres códigos y ningún defecto en
Java (`OrigenIncorporacion.desde` no tiene `orElse`):

| Código | Qué significa |
|---|---|
| `OPERACION` | entró trabajándola: alguien la registró para gestionarla |
| `OBSERVACION` | entró como conocimiento: se vio, no se gestiona |
| `SEMILLA` | estaba antes de que la pregunta existiera |

**No se inventó `IMPORTACION`.** Un vocabulario con valores que nadie produce
deja de poder auditarse, y este catálogo entero existe para lo contrario. Cuando
haya una importación real, se añade con su productor.

`id_rol_incorporo` guarda **quién**, con FK compuesta por tenant. Sin eso, la
procedencia dice el «cómo» y pierde el «quién», que es la mitad que sirve para
preguntar.

### 3 · Lo que se ve del mercado tiene tabla propia

`observacion_mercado`: `idPropiedad`, `fechaObservada`, `operacion`, `importe`,
`moneda`, `fuente`, `detalle`, `idRolActor`. **Append-only por trigger**
(`tg_observacion_append_only`): lo que se vio no se corrige ni se borra — si el
dato estaba mal, se anota otro.

Cada campo está porque sin él la serie no sirve:

- **la fecha**, porque un precio sin fecha no se compara con nada;
- **la operación**, porque el mismo número es un precio de venta o una renta
  según cuál sea, y suponerla guardaría un comparable falso;
- **la fuente**, porque sin decir de dónde salió un precio observado es un
  rumor, y una serie de rumores no es un comparable.

El vocabulario de `fuente` está **abierto a propósito** y esto se decide, no se
olvida: las fuentes reales son un hecho del campo —un portal, un cartel, otro
corredor, el propio propietario— y nadie las ha inventariado. Cerrarlas hoy
sería elegir una lista arbitraria u obligar a que la gente meta lo que ve en la
casilla que menos miente. Se normaliza a mayúsculas para que al menos no haya
tres formas de escribir lo mismo.

**Lo que este servicio NO hace es la mitad de su definición.** No escribe
`precio_propiedad`, no toca `propiedad.precio_referencial`, no cambia la
disponibilidad y no abre nada. Sería cómodo que anotar «lo vi a 190 000» dejara
el inmueble con un precio que pintar en el listado — y ese número no lo autorizó
nadie: en cuanto se proyectara, cualquier búsqueda por precio máximo lo trataría
como si un propietario lo hubiera aceptado.

### 4 · La frontera, dicha también desde la base

`tg_precio_exige_encargo` rechaza un hito de `precio_propiedad` sin
`id_captacion`, y el mensaje de error **nombra la salida correcta**:

```
Un hito economico nace de un ENCARGO, y este no declara ninguno. Si lo que se
quiere guardar es lo que se VIO del mercado, va en observacion_mercado: BROX no
convierte una observacion en un hecho comercial.
```

Un mensaje que solo dijera «falta id_captacion» invita a rellenarlo con
cualquier encargo a mano.

### 5 · La pantalla declara la operación del encargo

`captacion-form` enviaba `motivoOperacion: 'A'` **pase lo que pase**: cualquier
encargo de venta se guardaba como alquiler, con el precio de venta en la casilla
de la renta y la comisión calculada sobre él. Ahora la operación es un campo sin
valor previo, y arrastra el resto:

- el rótulo del importe la sigue — «Renta mensual» / «Precio de venta»; mientras
  no se declare, «Importe del encargo» y ninguna de las dos;
- las modalidades de comisión salen de ella: en una venta no existe «un mes de
  alquiler» que cobrar;
- la base de cálculo se **deriva**, no se elige: `R` en alquiler, `V` en venta.

Y el backend deja de suponerlo por su cuenta: `CondicionesEconomicas.basePorDefecto`
y `exigirBaseCoherente` (V76) sustituyen al defecto fijo `RENTA_MENSUAL` que
`CaptacionServiceImpl` aplicaba a cualquier operación. `CalculadoraComision`
comprueba lo mismo, y una condición **sin operación declarada** ya no se calcula:
la columna es `NOT NULL`, así que llegar sin ella significa que el objeto está a
medio construir.

---

## Verificación

```
backend  359/359 · 0 skipped · 21/21 suites de integración ejecutadas
angular  647/647
gate SQL   68/68 comprobaciones (verificacion/gate-modelo-universal.sql)
gate node 165/165 (docs/ai/modelo/gate-modelo-universal.js)

E2E      comision-movimientos     65 OK / 0
         disponibilidad-contrato  41 OK / 0
         estabilizacion-alquiler  18 OK / 0
         f4-solicitud            125 OK / 0
         f3-demanda              103 OK / 0
         f6-f7-alertas-tareas     64 OK / 0
         ficha-comercial          61 OK / 0
         reportes-propietario     50 OK / 0
         v6                       53 OK / 0
         e4-dashboard            129 OK / 0
```

`PropiedadComoActivoDeDatoIntegrationTest` — 15 comprobaciones, en el orden en
que el usuario describió el corte:

| Comportamiento | Qué demuestra |
|---|---|
| alta sin titulares | la propiedad existe, activa, sin titularidad y sin encargo |
| alta con titular | no cambia nada de lo anterior |
| toda propiedad declara su origen | ninguna fila con `origen_incorporacion` NULL |
| el origen distingue trabajo de observación | con operaciones `OPERACION`; sin ellas `OBSERVACION` |
| conserva quién la registró | `id_rol_incorporo` apunta al actor real |
| el ciclo completo no produce nada comercial | crear → leer → editar → observar → releer: 0 captaciones, 0 hitos, 0 anuncios, 0 prospecciones |
| la historia previa sobrevive | al aparecer titular y encargo, las observaciones anteriores siguen ahí y no se reinterpretan |
| captar exige titularidad | y el mensaje dice que registrarla así **sí** era legítimo |
| el alta con operaciones exige titularidad | el mismo gate por el otro camino |
| la observación no es un hecho | ni precio, ni disponibilidad, ni publicación |
| cada observación conserva su evidencia | fecha, operación, importe, moneda, fuente y actor |
| las observaciones son append-only | `UPDATE` y `DELETE` rechazados por la base |
| sin evidencia no hay observación | falta la fuente → rechazo |
| no se observa el futuro | una fecha por venir no es evidencia, es expectativa |
| ningún hito nuevo sin encargo | `precio_propiedad` con `id_captacion` NULL → rechazo |

---

## Lo que se encontró al hacerlo

**El gate del modelo llevaba tres comprobaciones en rojo, y ninguna era de
V76.**

- **`M2 ninguna propiedad perdio su metraje`** comprobaba **lo contrario** de lo
  que decidió D-E4-3: pedía una *copia* del metraje en `atributo_propiedad`, y
  V61 borró todas esas copias a propósito porque la autoridad es la columna
  canónica. El gate llevaba rojo desde entonces contra la decisión que lo
  superaba. Reescrita: el dato está en su único sitio, y la copia no ha vuelto.
- **`OUT el outbox distingue el origen KAIROS`** preguntaba por
  `ck_evento_origen`, una restricción que no existe con ese nombre. La
  subconsulta devolvía NULL y eso se contaba como fallo. La que existe es
  `ck_evento_canal`.
- **`M2 el catalogo del sistema tiene 19 atributos`** se quedó en 19 cuando V74
  añadió las seis primeras condiciones del encargo. Aquí la cifra exacta sí
  vale: el catálogo del sistema es una constante del producto.

**Dos censos que medían la cartera, no la invariante.** `count(*) = 21 FROM
propiedad` y los 21 rubros: escritos así, cualquier alta —el uso normal del
producto— ponía el gate en rojo sin que nada se hubiera roto. Un gate que se
rompe al usar el producto deja de leerse, y ese es el modo de fallo que importa.
Ahora son suelos, y se dice por qué.

**`pg_temp.rechaza()` no distinguía «lo aceptó» de «no tocó ninguna fila».** Un
`UPDATE` que no encuentra filas no dispara ningún trigger y termina sin error.
Las dos pruebas nuevas de append-only cayeron en eso: la propiedad de apoyo no
tenía observaciones, así que probaban una invariante que nunca llegaron a tocar.
Se distinguen, y las pruebas de observación siembran su fila dentro del
savepoint.

**El bloque de dos encargos elegía una captación cualquiera.** En cuanto la
cartera tuvo una propiedad en venta y alquiler a la vez —justo lo que ese bloque
existe para demostrar que se puede— el `INSERT` chocaba contra
`uq_captacion_viva_por_operacion` y el gate moría **antes del informe**.

**`CatalogoProductoresTest` sólo medía la longitud del texto**, y el catálogo
mentía en cuatro sitios:

- `SolicitudServiceImpl.reenviar` — el método se llama `reenviarAEvaluacion`;
- `prospeccion.resultado_propuesta.S` citaba `EstadoProspeccion.SEGUIMIENTO`,
  que no es la constante viva (`Prospeccion.EN_SEGUIMIENTO`);
- **`organizacion.estado.I` y `usuario_organizacion.estado.I` figuraban como
  `PRODUCIDO` con la evidencia «Baja de organizacion» / «Baja de membresia», y
  esas bajas **no existen**: nadie llama a esos setters y ninguna migración
  actualiza las columnas. Pasan a `RESERVADO_FUTURO`.

El gate ahora resuelve **cada símbolo citado contra `src/main`** —clase, método
o migración, incluidos los tipos anidados— y exige que todo `PRODUCIDO` nombre
al menos uno: «Baja de organizacion» tiene prosa de sobra y no se puede
comprobar. Siete filas estaban así.

**`operacionProyectada` devolvía ALQUILER cuando no había ningún encargo vivo.**
Es una inferencia que este corte prohíbe: habría escrito una renta mensual en
`precio_referencial` de una propiedad que nadie ha encargado. Devuelve `null` y
no se proyecta nada.

**`HistoricoPrecioIntegrationTest` colgaba sus hitos de `min(id_propiedad)`**,
sin encargo. Lo detectó el trigger nuevo, que es exactamente su trabajo: el
fixture ahora toma un encargo vivo real.

**Y el trigger destapó un escritor de producción: `ContratoServiceImpl.cerrarLocal`
escribía el hito `C` sin decir de qué encargo era.** Es el último hito de la
serie de ese encargo, así que sin el id dos alquileres sucesivos del mismo
inmueble mezclaban sus cierres en una sola línea. Los dos sitios que lo llaman
tenían la captación en la mano. `POST /locales/{id}/precios` tenía el mismo
hueco por omisión —ataba el encargo «cuando lo hubiera»— y ahora **rechaza con
su propio mensaje**, que nombra las observaciones como la alternativa, en vez de
dejar que llegue un error de integridad.

**La operación de un encargo se podía editar, y eso reinterpreta su historia.**
`PUT /captaciones/{id}` copiaba `motivoOperacion` del cuerpo sin mirar: un
encargo de venta pasaba a ser de alquiler **conservando su histórico**, con lo
que los 350 000 USD del precio de venta se leían como renta mensual. Cuando el
inmueble ya tenía encargo vivo de la otra operación salía como un 409 de índice
único; cuando no, pasaba en silencio. La operación es la **identidad** del
encargo —la misma frase que el Corte 0C fijó por el otro lado: dos alquileres
sucesivos son dos episodios—, así que ahora se rechaza de frente y el mensaje
dice qué hacer: cerrar este encargo y abrir el de la otra operación.

**Y por ahí salió que `e2e-v6` llevaba roto desde `ca3b856`.** Su subsanación
editaba con `motivoOperacion = 'A'` un encargo que `captar` acababa de abrir
como VENTA. Se comprobó ejecutando la suite sobre HEAD en un worktree limpio:
**42 de 52** comprobaciones, el mismo 409 y en la misma línea — el guion se editó
después de la corrida que dejó el «50 OK» en la evidencia de V75, y nadie lo
volvió a correr. Ahora subsana **como venta**, comprueba que la operación
sobrevive a la edición y añade el caso de que cambiarla se rechaza: **53 OK**.

---

## Deuda registrada, no resuelta

**`observacion_mercado` no tiene pantalla.** Los dos endpoints existen
(`POST`/`GET /propiedades/{id}/observaciones`, con su fila en la matriz) y el
SPA todavía no los llama. El dato se puede capturar por API y por KAIROS; la
cara de la pantalla es trabajo aparte.

**La `fuente` no está inventariada.** El vocabulario abierto es la decisión
correcta hoy y una deuda mañana: cuando haya volumen real habrá que mirar qué
escribe la gente y decidir si se cierra.

**Sigue abierto lo que V75 dejó dicho**: la alerta «Modificación comercial
sensible» sin emisor, la propiedad sólo registrada sin agente dueño
(`exigirPertenencia`) y su propietario fuera del alcance del broker. V76 no las
toca — y las agranda un poco, porque ahora también puede no tener titular.
