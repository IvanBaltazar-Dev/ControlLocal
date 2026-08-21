# Corte 0C — El sujeto del dato · V73 + V74

**Cerrado el 2026-08-21.** Migraciones `V73__el_sujeto_del_dato.sql` y
`V74__las_primeras_condiciones_del_encargo.sql`.

D-E4-3 respondió **dónde vive** cada dato. Faltaba la pregunta que va antes:

> ¿De quién es?

El catálogo presuponía una sola respuesta —todo era de la Propiedad— porque
`atributo_propiedad` cuelga de `id_propiedad` y no había otro sitio donde
ponerlo. `amoblado` es el caso que lo prueba: una vivienda puede **tener**
muebles, venderse sin ellos, alquilarse con ellos, y tener dos alquileres que lo
pactan distinto. Con un solo sujeto la tercera historia era irrepresentable —
el valor se sobrescribía y nadie se enteraba.

La regla del reparto, que es lo único que hay que saber para clasificar una
clave nueva:

> **Si al firmar el siguiente encargo el dato puede cambiar sin que la propiedad
> haya cambiado, es del ENCARGO.**

---

## La cadena, completa

```
clave  →  vocabulario  →  SUJETO  →  autoridad  →  mecanismo

PROPIEDAD  →  catalogo_atributo_tipo       →  atributo_propiedad | campo canónico
ENCARGO    →  catalogo_atributo_operacion  →  atributo_encargo
              nunca en las dos
```

Y el sujeto se declara **una vez por clave**. De él se deriva todo lo demás:
dónde se declara la aplicabilidad, en qué tabla vive el valor, qué trigger lo
vigila, quién lo lee, quién lo borra y quién cuenta lo que falta.

---

## La decisión central: la identidad es el ENCARGO, no la operación

`atributo_encargo` cuelga de `id_captacion`. Su índice único es
`(id_captacion, clave)` — no `(id_propiedad, operacion, clave)`.

La simplificación contraria parece razonable y es falsa. `uq_captacion_viva_por_operacion`
(V50) prohíbe dos encargos **vivos** de la misma operación, no que hayan
existido varios: una propiedad con tres alquileres a lo largo del tiempo tiene
tres garantías distintas. Agrupar por operación haría que el alquiler de 2026
naciera con lo pactado en 2024, sin que nadie lo escribiera y sin que nada
fallara.

---

## Lo que ya no puede pasar

| Antes | Ahora |
|---|---|
| Una condición negociada se guardaba como hecho del inmueble, y el segundo encargo pisaba al primero | El trigger la rechaza, y Java lo dice antes con el nombre del dato |
| `amoblado` significaba dos cosas a la vez | Dos claves, dos sujetos, y una guarda de V74 rompe si alguien las une |
| La aplicabilidad sólo sabía decir «a qué tipo» | `(tipo, operación)`: la garantía aplica al alquiler de un departamento y no a su venta |
| Publicar sólo preguntaba por la ficha | Pregunta a los dos sujetos y **dice cuál de los dos** falta |
| Lo que faltaba viajaba en una lista de la propiedad | Cada encargo trae `faltanParaPublicar` en su bloque |
| Un multivalor del encargo no tenía dónde vivir | `atributo_encargo_opcion`, con FK compuesta de tenant y `CASCADE` |

---

## Contrato

**Ficha** — cada `encargo` gana dos listas propias:

```
encargos[].condiciones          [{clave, rotulo, tipoDato, unidad, valor}]
encargos[].faltanParaPublicar   [{clave, rotulo}]
```

Van **dentro del encargo** y no en la ficha porque no son del inmueble: la venta
y el alquiler abiertos a la vez enseñan números distintos, y el alquiler cerrado
de 2024 sigue enseñando los suyos.

**Edición** — `PUT /propiedades/{id}` gana `condiciones`, una lista de bloques:

```json
"condiciones": [
  { "idEncargo": 812, "atributos": [...], "atributosABorrar": [...] }
]
```

La regla de bloques de 0A, un nivel más adentro y sin excepciones:

```
condiciones ausente o null      → no se toca NINGÚN encargo
bloque de un encargo ausente    → ese encargo queda como estaba
bloque presente                 → sólo toca a SU idEncargo
```

El cliente manda el **id del encargo**, nunca su operación. Un bloque sin
`idEncargo` se rechaza: con dos encargos abiertos, adivinar es escribir en el
equivocado.

**Alta** — `OperacionSolicitada` gana `condiciones`. En el alta el encargo
todavía no tiene id, así que la operación declarada es lo único que puede decir
a cuál pertenece cada condición.

**Captura** — cada condición se pregunta **dentro del bloque de su operación** y
con la clave calificada, igual que el importe:

```
tipoPropiedad + VENTA,ALQUILER
  → físico una vez
  → bloque VENTA     … (sin garantía: allí no significa nada)
  → bloque ALQUILER  … garantia_meses:ALQUILER, plazo_minimo_meses:ALQUILER, …
```

---

## Verificación

```
backend    315/315 · 0 skipped · PostgreSQL real · TEST_DB_URL + CONTROLLOCAL_CIERRE=1
           18/18 suites de integración ejecutadas
angular    643/643
kairos      35/35
git diff --check limpio

E2E del cierre    comision-movimientos    65 OK / 0 fallas
                  disponibilidad-contrato 41 OK / 0 fallas
                  estabilizacion-alquiler 18 OK / 0 fallas
                  f4-solicitud            BLOQUEADA — ver abajo
```

**`Verificar-Cierre.ps1` no corre entera todavía, y no por este corte.** Su fase
E2E destapó una deuda del Corte 0A: diez guiones seguían llamando a
`POST /locales`, retirado en V71, y contestaba 405. Tres de las cuatro suites del
cierre están migradas al alta universal y verdes; la cuarta —y otras dos que no
están en el gate— dependen de una capacidad que el Corte 0A dejó sin entrada, y
eso es una decisión de producto. Está detallado al final.

`SujetoDelDatoIntegrationTest` — **21/21**, contra PostgreSQL real porque casi
todo lo que afirma lo garantiza un trigger o un índice único, y eso no lo lee
javac, ni Hibernate, ni ArchUnit. Estaba **rojo 6/6 contra HEAD** antes de V73.

| Comportamiento | Qué demuestra |
|---|---|
| el catálogo declara `sujeto` | con CHECK y sin nulos: un dato que no sabe de quién es no se enruta |
| PK de `catalogo_atributo_operacion` | la aplicabilidad no tiene identidad propia: es una fila **de** un atributo |
| `atributo_encargo` cuelga de `id_captacion` | y **no** tiene columna de operación |
| invariante en las dos direcciones | ninguna clave declara su aplicabilidad en la tabla del otro sujeto |
| ningún valor cruzado, en los dos sentidos | contra los datos reales del repositorio |
| dos encargos, misma clave, valores distintos | el caso que justifica el corte entero |
| editar uno deja el otro **idéntico** | retrato plano del otro bloque, no una clave suelta |
| borrar en uno no borra en el otro | leer, escribir y **borrar** recorren el mismo enrutamiento |
| editar la propiedad no toca ningún encargo | los dos retratos, antes y después |
| cerrar un encargo no migra nada | el cerrado conserva lo suyo; el vivo sigue sin ello |
| **un segundo alquiler no hereda del primero** | la prueba de que la identidad es el episodio |
| la ficha muestra cada valor bajo su `idEncargo` | y ninguno entre los atributos del inmueble |
| clave de PROPIEDAD pactada en un encargo | rechazada, y el mensaje dice de quién es el dato |
| condición escrita como atributo del inmueble | rechazada, y el mensaje dice de quién es el dato |
| condición sólo de ALQUILER pactada en la VENTA | rechazada nombrando la operación |
| bloque sin `idEncargo` | rechazado |
| PUB del encargo impide publicar | y el mensaje separa «la ficha» de «este encargo» |
| faltantes por bloque | al alquiler le falta, a la venta ni aplica |
| la definición pregunta dentro del bloque | y calificada, y nunca entre lo físico |
| lo dictado en un bloque va a su encargo | el circuito conversacional entero |

`UnSoloLectorPorSujetoTest` — el gate que el Corte 0B se ganó a pulso:

- nadie fuera de los dos enrutadores, el lector y el caso de uso toca los cuatro
  repositorios de valores;
- el enrutador de la propiedad no conoce `AtributoEncargo`, ni el del encargo
  conoce `AtributoPropiedad`;
- el motor de captura no invoca ningún repositorio de valores.

---

## Lo que se encontró al hacerlo

**`operacionesValidadas` reconstruía el record y se dejaba el campo nuevo.** El
alta se guardaba en verde y la condición dictada desaparecía sin un solo error.
Un normalizador que reconstruye un record es exactamente el sitio donde se
pierde un dato sin que nada falle — la misma clase de pérdida callada que
persigue 0A, en una línea que parecía inocente. Lo destapó la prueba del camino
conversacional, no la del caso de uso.

**El contenedor de valores se llamaba `ValoresDePropiedad`** y dejó de ser
cierto en cuanto apareció el segundo sujeto. Renombrado a `ValoresGobernados`:
un contenedor que dijera «de propiedad» llevando condiciones comerciales sería
la primera pieza en volver a mezclarlos.

**La conversión de valores no depende del sujeto.** Un entero es un entero lo
lleve quien lo lleve, y las monedas que existen son las mismas. Copiarla en el
segundo enrutador habría creado dos definiciones de la misma regla, y habrían
divergido en el primer arreglo hecho en una sola. Vive en `ConversionDeValores`.

**`enRango` sólo comprobaba el mínimo.** El trigger comprueba las dos puntas
desde V72, así que un valor por encima del máximo se rechazaba con un mensaje de
PostgreSQL a mitad de la transacción en vez de con el nombre del atributo
delante. Cerrado al extraer la conversión.

---

## Deuda registrada, no resuelta

**El SPA todavía no pinta ni edita el bloque de condiciones.** El contrato las
publica y el caso de uso las acepta; la interfaz es el corte del **editor
universal**. Hasta entonces las condiciones se pactan por API o por KAIROS.

**No hay caso de uso que reabra un encargo cerrado.** `editar` con una operación
**actualiza el encargo vivo** y contesta «esta propiedad no tiene ningún encargo
vivo de ALQUILER» cuando no lo hay. Es una capacidad que falta, no un fallo de
este corte; la prueba del segundo episodio abre el encargo por SQL y lo dice.

**Las seis claves entran OPC.** Ninguna bloquea el alta ni la publicación. Que
una garantía sea imprescindible para anunciar un alquiler puede ser cierto, pero
es una decisión del negocio que nadie ha tomado, y tomarla aquí dejaría fichas
ya publicadas incompletas de golpe. Subirla a PUB es una línea de SQL.

**`AplicacionAtributo.requerido`** sigue en la base junto a `exigencia`, como
quedó en 0B. Nadie debe leerla.

---

## Bloqueo abierto, heredado del Corte 0A: la prospección se quedó sin entrada

**No es de este corte y no se resuelve solo.** Lo destapó la corrida de cierre.

`POST /locales` hacía tres cosas: registraba el inmueble, **abría una
prospección** y **creaba un anuncio** (`estadoPublicacion`). Al retirarlo, V71
sustituyó la primera y las otras dos desaparecieron sin dueño. El anuncio se
repone en una línea —publicar es una decisión y ahora se declara—, pero la
prospección deja una contradicción de modelo:

```
POST /propiedades   exige al menos una operación   -> toda propiedad nace con encargo VIVO
prospección         existe para conseguir el encargo -> presupone que todavía NO hay
uq_captacion_viva_por_operacion  prohíbe dos vivos de la misma operación
```

Encadenado: registrar deja el encargo en `P` (vivo); `prospecciones/{id}/captar`
crea **otro** encargo de la misma operación; el índice único lo rechaza. **Hoy no
hay forma de registrar una propiedad que sólo se está prospectando**, y por eso
`e2e-f3-demanda`, `e2e-f4-solicitud` y `e2e-f6-f7-alertas-tareas` no se pueden
migrar sin decidir antes qué modelo se quiere:

| Opción | Qué dice del dominio |
|---|---|
| `POST /propiedades` acepta **cero operaciones** | una propiedad en prospección todavía no tiene encargo; el encargo nace cuando el propietario acepta |
| `captar` **completa** el encargo pendiente en vez de crear otro | el alta ya es la captación; la prospección sólo la empuja |
| la prospección arranca de otro sitio | el inmueble prospectado no entra en la cartera hasta captarlo |

La primera es la que mejor encaja con D-E4-1 —propiedad y encargo son cosas
distintas y el encargo es un episodio—, pero **es una decisión de producto**, no
un arreglo: cambia qué significa registrar.

Migradas y verdes: `comision-movimientos`, `disponibilidad-contrato`,
`estabilizacion-alquiler`. Sin migrar y sin poder migrarse todavía:
`f3-demanda`, `f4-solicitud`, `f6-f7-alertas-tareas`. Sin migrar y sin bloqueo
—sustitución mecánica pendiente—: `e4-dashboard`, `ficha-comercial`,
`reportes-propietario`, `v6`.
