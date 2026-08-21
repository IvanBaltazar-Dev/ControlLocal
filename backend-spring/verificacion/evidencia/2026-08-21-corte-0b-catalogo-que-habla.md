# Corte 0B — El catálogo aprende a hablar · V72

**Cerrado el 2026-08-21.** Migración `V72__el_catalogo_aprende_a_hablar.sql`.

Hasta aquí el catálogo sabía declarar cinco tipos de dato y ninguna otra cosa. No
sabía decir qué opciones tiene una LISTA —y por eso la única sembrada,
`servicios_disponibles`, viajaba como texto libre y destruía justamente la
combinación que importa: «agua sí, desagüe no» y «agua no, desagüe sí» son la
misma cadena para cualquier comparación—, ni cuánto mide un texto, ni cuánto vale
como máximo un número, ni que un importe lleva moneda, ni que una fecha es una
fecha, ni que un dato puede hacer falta para **publicar** sin hacer falta para
**registrar**.

**Este corte no siembra ni una clave inmobiliaria.** Solo añade capacidades. Esa
separación es deliberada: sembrar decenas de campos antes de que el catálogo sepa
declarar su vocabulario los trasladaría a Angular, que es lo que el gate de D-A-1
rompe.

---

## Lo que ya no puede pasar

| Antes | Ahora |
|---|---|
| Un tipo de dato sin regla en el trigger **entraba** por el `ELSE` implícito, con cualquier columna rellena o con ninguna | `CASE … ELSE RAISE`: falla en la primera escritura, con el nombre del tipo |
| Un `IMPORTE` se guardaba como texto y perdía la moneda | Monto y moneda en la misma fila, exigidos juntos por Java **y** por el trigger |
| Una `FECHA` se guardaba como texto | Columna propia; el texto se rechaza |
| Una `LISTA` admitía cualquier valor | Vocabulario declarado, vigilado por **dos** triggers (fila padre y tabla hija) |
| No existía el multivalor | Fila ancla + tabla hija con FK compuesta de tenant y `CASCADE` |
| `requerido` solo sabía decir «bloquea el alta» | `ALT` / `PUB` / `OPC`, con `PUB` conectado al caso de uso real de publicación |
| El rubro perdió su `VARCHAR(120)` en V71 | `longitud_maxima` en el catálogo, aplicada en Java y en el trigger |
| Dos endpoints contestaban «qué se pregunta para este tipo», con distinto `orden` | Uno: `GET /captura/definicion` |

---

## Contrato

`GET /captura/definicion?tipoPropiedad=…` con `operaciones` **opcional**:

```
tipoPropiedad                        -> comunes + sección del tipo · CERO bloques de encargo
tipoPropiedad + VENTA                -> propiedad una vez + bloque VENTA
tipoPropiedad + VENTA,ALQUILER       -> propiedad una vez + dos bloques
```

**La operación no se infiere nunca.** La propiedad es la cosa física; la
operación vive en el Encargo. Rellenarla «porque es lo normal» devolvería un
bloque económico que nadie pidió, rotulado con una operación que nadie declaró.

Cada pregunta publica: `control`, `rotulo`, `ayuda`, `seccion`, `familia`,
`unidad`, `opciones[{valor,rotulo}]`, `exigencia`, `orden` **del catálogo**, y
las cuatro restricciones (`minimo`, `maximo`, `longitudMaxima`, `decimales`).

Dos renombres que había que hacer: la clasificación del guion pasó de `familia` a
`seccion` —el catálogo declara **su** familia y el cliente recibía las dos con el
mismo nombre en el mismo objeto—, y el `orden` dejó de recalcularse con la
posición del bucle.

---

## Verificación

```
backend    290/290 · 0 skipped · PostgreSQL real · TEST_DB_URL + CONTROLLOCAL_CIERRE=1
           17/17 suites de integración ejecutadas
angular    643/643
kairos      35/35
git diff --check limpio
```

`CatalogoQueHablaIntegrationTest` — **19/19**, contra PostgreSQL real porque casi
todo lo que afirma lo garantiza un trigger, y un trigger no lo lee ni javac, ni
Hibernate, ni ArchUnit:

| Comportamiento | Qué demuestra |
|---|---|
| FECHA ida y vuelta | va a `valor_fecha`, no a `valor_texto` |
| IMPORTE ida y vuelta | monto y moneda sobreviven juntos; se lee «USD 120.5» |
| IMPORTE sin moneda | rechazado |
| LISTA_MULTIPLE ida y vuelta | N filas reales, no una cadena con comas |
| LISTA_MULTIPLE reemplazo | editar sustituye; sin eso no habría forma de **quitar** |
| LISTA_MULTIPLE retirada | `CASCADE` se lleva los valores, cero huérfanos |
| vocabulario inválido | rechazado por las **dos** puertas |
| tipo inventado | el trigger lo rechaza en vez de aceptarlo en silencio |
| cuerpo `pg_proc.prosrc` | la salida por defecto que grita sigue ahí |
| definición sin operación | cero bloques de encargo |
| definición VENTA+ALQUILER | física una vez, dos bloques |
| opciones con rótulo | el control deja de caer a TEXTO |
| orden | viene del catálogo, no del bucle |
| ALT | bloquea el alta |
| PUB | impide publicar y lo dice con el **nombre** del dato |
| PUB completado | publicar pasa |
| OPC | no bloquea |
| transición a PUBLICADO | **también** pregunta: los dos caminos, no uno |
| lote | tres claves multivalor cuestan las mismas consultas que una |

Gates nuevos: `elProductorHeredadoDeDefinicionNoResucita` (el endpoint retirado no
vuelve) y `elTriggerConservaSuElseQueGrita` (la regla del `prosrc` no desaparece).

---

## Lo que se encontró al hacerlo

**`tipo_dato` era `VARCHAR(10)` y `LISTA_MULTIPLE` mide 14.** La migración aplicaba
en verde —un CHECK no valida longitud— y habría reventado en la primera siembra,
un corte más allá y lejos de la causa. Lo detectó ejercitar el comportamiento, no
comprobar que «aplicó sin error».

**La auditoría se equivoca en su propio inventario**: dice «cuatro filas marcadas
requeridas» y en la base viva son **diez**. La guarda de conversión compara el
antes contra el después en vez de un número escrito a mano; con la cifra literal
habría abortado la migración, o peor, alguien la habría «arreglado» bajando el
número.

**`ficha` tenía su propio lector** y se saltaba el enrutador de autoridad: por eso
un importe llegaba sin su moneda y un multivalor no llegaba. Es la misma clase de
asimetría que cerró D-E4-3, reaparecida en el lado de la lectura.

**El grep que prescribe CLAUDE.md no encuentra una de las cuatro funciones**:
`proteger_catalogo_del_sistema` solo nombra `OLD.*` y `NEW.*`, así que
`prosrc ilike '%catalogo_atributo%'` no la ve.

---

## Deuda registrada, no resuelta

`AplicacionAtributo.requerido` sigue en la base junto a `exigencia`, para que la
conversión siga siendo auditable. Nadie debe leerla.

`rubro_permitido` sigue declarado `requerido = false` desde V48 aunque la tabla
por tipo decía `NOT NULL`; V72 resuelve a favor de la autoridad declarada y **no**
cambia la exigencia. Subirla es siembra.

Los vocabularios del guion viajan con valor y rótulo iguales (`LOCAL` → `LOCAL`).
Ponerles rótulo de verdad es cambiar la presentación, y eso no entra de
contrabando en un corte que solo amplía capacidades.
