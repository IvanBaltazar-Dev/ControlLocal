# Evidencia — 4.P · Procedencia granular del dato gobernado

**Fecha:** 2026-08-25
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `59a7765`
**Encargo:** `docs/ai/encargo-4p-procedencia-del-dato-gobernado.md`
**Modelo aprobado:** `docs/ai/decision-4p-modelo-de-procedencia.md` (A3, congelado)
**Migración:** **`V83__la_procedencia_del_dato_gobernado.sql`**
**Frontera del cutover en `controllocal_dev`:** `2026-08-25 09:41:33.879331+00`

> **Lo que se demuestra aquí no es que los tests pasen: es que el sistema puede
> reconstruir historias reales.** Un valor que se escribe, cambia y se borra
> tiene que poder contarse entero después — y con la fila vigente ya ausente.
>
> **Cuarta versión.** El primer candidato dejaba abierta la única puerta que usa
> el producto y **ningún gate lo veía**; el segundo fijó la decisión del titular
> y, al fijarla, destapó una **tercera** asimetría —el alta aceptaba lo que la
> edición rechazaba—; el tercero contenía **una refutación falsa mía**, escrita
> con un barrido que miraba el universo equivocado.
>
> Empieza por §0 bis y sigue por §8 bis. **Lo que más deja este microcorte no es
> el linaje: es §8 bis** — el mismo error de método cometido dos veces, y por qué
> el control positivo no lo vio.

---

## 0. Preflight, medido contra el repo y las bases vivas

Antes de escribir una línea, contra `controllocal_dev` y el árbol en `59a7765`:

```
rama            feat/modelo-universal-y-autoridad-del-dato   HEAD attached
arbol           limpio
Flyway          82  ->  V83 confirmada libre
atributo_propiedad          76 filas · fecha_actualizacion NULL en las 76
atributo_encargo             0 filas
atributo_propiedad_opcion    0 · atributo_encargo_opcion  0
propiedades                 26  ·  claves ESTRUCTURAL  4
Procedencia (V59)           9 dimensiones, ya persistidas en evento_dominio
naturaleza                  NO existe en ninguna parte del backend
```

**Las decisiones congeladas coinciden con el repo real**, una por una: `A3` sin
columnas nuevas en las tablas de valor; `Procedencia` nace en `registrar:205` y
`editar:445` y **no llegaba a ninguno de los siete escritores**; `retirar`
devolvía `boolean`; `escribirMultivalor` hacía `borrarDe(ancla)` + `save` por
opción; y **pactar una condición del encargo no emitía ni siquiera evento de
operación**. No hubo `STOP`.

### 0.1 · Lo que el preflight del encargo dejaba abierto, barrido con `rg`

Inventario completo de productores de las cuatro tablas de valor
(`rg`, no `grep -iF` — con control positivo: `rg -c "atributo_propiedad"
gate-modelo-universal.sql` → **8**):

| productor | qué escribe |
|---|---|
| `PropiedadUniversalServiceImpl` | las cinco superficies, vía los dos enrutadores |
| `AtributosGobernados` / `AtributosDeEncargo` | conversión y validación |
| `NucleoUniversalIntegrationTest` | `saveAndFlush` que **espera un rechazo** (no commitea) |
| migraciones `V48`, `V61`, `V71` | *backfill* histórico por SQL |
| 6 suites E2E + `gate-modelo-universal.sql` | SQL directo, **a propósito** |

**No hay ningún otro camino de importación.** Cerrada la frontera del servicio,
lo que queda fuera es SQL deliberado de pruebas.

---

---

## 0 bis. SEGUNDA VUELTA — el defecto que la auditoría encontró, y qué lo tapaba

**El primer candidato se rechazó con severidad ALTA, y con razón.** Queda aquí
antes que nada porque es lo que más enseña de este microcorte.

### El defecto

`PropiedadUniversalServiceImpl.aplicarUbicacion` escribía

```java
siViene(ubicacion.piso(), propiedad::setPiso);
```

y **`piso` es una de las cuatro claves `ESTRUCTURAL`** (`destino='ESTRUCTURAL'`,
`campo_estructural='PISO'`). No pasaba por `AtributosGobernados` ni por
`LinajeDelValor`. Reproducido en vivo por el Auditor, con sesión real y después
del cutover:

```
PUT /propiedades/3260 {"ubicacion":{"piso":"7"}}   200 OK
propiedad.piso            NULL -> '7'
rastro_valor_gobernado    6 filas ANTES -> 6 filas DESPUES
clave='piso'              -> 0
```

Y como **retirar sí pasaba** por el enrutador, la historia quedaba
`EDICION 7→8` + `RETIRADA 8→∅` **sin ALTA**: el `7` aparecía de la nada como
valor hallado, aunque lo había escrito el producto 29 segundos antes.

**No era una puerta exótica: era la única que usa el producto.**
`propiedades.service.ts` mete `'piso'` en `CAMPOS_DE_UBICACION` y
`propiedad-editor.ts` lo enruta por `ubicacion`, nunca por `atributos`. Editar el
piso desde el SPA no dejaba linaje jamás.

### El error de método — y es el hallazgo de verdad

El inventario de 4.P barrió **«productores de las cuatro TABLAS de valor»** y
**nunca inventarió productores de las cuatro COLUMNAS `ESTRUCTURAL`**. La quinta
superficie se demostró con `metraje_total` —que no tiene segunda puerta— y **la
conclusión se extendió a las cuatro claves sin medirlas una por una**. `piso` es
el **único solape** entre los nueve huecos de `UbicacionRequest` y el catálogo, y
justo por ahí entraba.

Medición que lo habría cazado en el preflight, y que ahora está hecha: en todo el
código de producción, los únicos llamantes de los cuatro *setters* estructurales
son `EscritorEstructural` (4 conceptos) y **`aplicarUbicacion:1803`**. Uno, y era
el defecto.

### Por qué NINGÚN gate lo veía

Con el defecto vivo, el Auditor corrió todo: gate `83/83`,
`LinajeDeTodaEscrituraTest` `4/4`, reactor `1191`. Verde entero. Tres causas
independientes, y las tres están cerradas:

| lo que lo tapaba | cómo se cierra |
|---|---|
| las comprobaciones `.sql` de frontera miraban **sólo las dos tablas**, y una clave `ESTRUCTURAL` **no crea fila** | comprobación nueva sobre **las cuatro columnas** + **control de cobertura** contra el catálogo |
| `DESTINOS_DE_VALOR` no incluía los *setters* de `Propiedad` | los cuatro *setters* vigilados, con `SETTER_POR_CONCEPTO` como control de cobertura |
| **`propiedad::setPiso` es una REFERENCIA a método, y ArchUnit no la ve en `getMethodCallsFromSelf()`** | el gate mira `getAccessesFromSelf()` |

> **La tercera es la que más importa, y la encontré probándola.** Tras añadir los
> *setters* a `DESTINOS_DE_VALOR` reintroduje el defecto a propósito y el gate
> **siguió en verde, 5/5**. Una referencia a método no es una llamada. Con
> `getAccessesFromSelf()` el gate se pone rojo y **nombra el método exacto**:
>
> ```
> estos metodos escriben un valor gobernado fuera del enrutador de su sujeto:
> [com.controllocal.service.impl.PropiedadUniversalServiceImpl#aplicarUbicacion]
> ```
>
> Sin esa prueba de que el gate muerde, habría entregado un segundo gate de
> mentira encima del primero.

### La corrección

`aplicarUbicacion` **ya no toca `piso`**. El hueco del cable se conserva —el SPA
y las suites lo mandan ahí, y 4.P no estrena superficie— y el Core lo **enruta**:
`conElPisoGobernado` lo mete entre los atributos antes de que se escriba nada, y
de ahí recorre el mismo camino que las demás claves. La clave se resuelve **por
concepto** (`claveDelCampo(PISO)`), no por el literal `"piso"`, para que una
organización que declare la suya con otro nombre siga funcionando — el mismo
principio por el que `EscritorEstructural` conmuta sobre el concepto.

**Mandarlo por los dos huecos a la vez se rechaza**, con la regla de siempre:
entre dos intenciones sobre el mismo dato no se elige, se avisa.

### El tercer rostro del mismo defecto, que salió al cerrarlo

Enrutar `piso` puso en rojo `ConservacionDeLaEdicionIntegrationTest` —46 errores—
y lo que había detrás **no era el arreglo: era más defecto**.

**`piso` viaja DOS VECES por el cable, y en las dos direcciones.** La ficha lo
publica dentro de `ubicacion` (`ubicacionDe` lee `propiedad.getPiso()`) **y**
entre los `atributos` (el lector añade las claves `ESTRUCTURAL` por concepto).
Así que el espejo del propio Core —leer la ficha y devolverla— manda el mismo
dato por los dos huecos.

Y el fixture de conservación mandaba, en la misma petición:

```
ubicacion.piso = "4"        (para los SIETE tipos)
atributos.piso = "3"        (LOCAL)   ·   "7" (OFICINA)   ·   "4" (DEPARTAMENTO)
```

**Dos valores distintos para el mismo dato, y el Core los aceptaba.** Los
resolvía **en silencio por orden de escritura**: `aplicarUbicacion` escribía el
`4` y el enrutador de atributos lo pisaba con el `3`. Nadie se enteraba, y el
linaje —cuando lo hubo— habría contado sólo la mitad de la historia.

**Qué se hizo, y qué NO.**

- **Sí**: el mismo valor por los dos huecos se **acepta** y produce **una sola**
  escritura —rechazarlo sería rechazar la ida y vuelta del propio Core—; con
  valores **distintos** se avisa, que es la regla de siempre.
- **Sí**: el fixture pasa a mandar el piso **una vez** y sólo donde el catálogo
  lo gobierna. Ninguna aserción tocada; lo que se quitó fue un payload
  contradictorio.
- **No**: no se ha cambiado el cable para que `piso` deje de viajar dos veces.
  Eso toca el read model y tiene consumidor en Angular, y 4.P no estrena
  superficie.

### ✅ RESUELTO POR EL TITULAR (2026-08-25) — **manda el catálogo**

> **Alternativa (1), la implementada.** Las dos puertas responden lo mismo: `piso`
> aplica a **D, L y O**, y una `CASA` o un `TERRENO` reciben **400 por ambas**.

**Por qué ésta:** es **la única rama que no inventa una regla** — aplica lo que el
catálogo ya dice. La (2) obligaría a afirmar que un terreno tiene piso, y sería
**ampliar el catálogo para que encaje una puerta del cable**, al revés de como
debe ir. La (3) —dos nombres para dos significados— **reintroduce la doble
autoridad que 4.P vino a eliminar**: el mismo dato con dos dueños y dos historias
posibles.

**El endurecimiento es correcto y es el punto:** que antes una `CASA` aceptara
`ubicacion.piso` con `200` **era el síntoma**, no una capacidad — lo permitía
justamente la puerta **no gobernada**. Cerrarla iguala las dos respuestas.

**Impacto medido antes de decidir:** `0` propiedades con piso · `0` de tipo no
aplicable · `0` suites que manden uno. **Ningún dato real dependía de la rama.**

> **Queda anotado, y NO es de este microcorte:** si la aplicabilidad actual
> resultara estrecha —el caso a mirar es **`A` (almacén)**, que puede ocupar una
> planta alta en un edificio industrial—, eso se revisa en **un corte de catálogo
> propio, con su medición**. Cambiar aplicabilidad es una decisión, y 4.P no la
> toma.

### El STOP, tal como se planteó

Cerrar la puerta obliga a que el catálogo mande también por el hueco de
`ubicacion`, y eso **cambia una respuesta visible** que hasta hoy dependía de por
dónde entrara el dato:

```
catalogo_atributo_tipo para `piso`  ->  D, L, O   (OPC)   y nadie mas
antes:  PUT {"ubicacion":{"piso":"4"}} sobre una CASA  ->  200, columna escrita
        PUT {"atributos":[{"clave":"piso"}]} sobre una CASA -> 400 "no aplica"
ahora:  las dos puertas dan la MISMA respuesta -> 400
```

**No he cambiado el catálogo**, y no puedo: modificar `ALT`/`PUB`/`OPC` o la
aplicabilidad por tipo exige una decisión, y ésa no es mía. Lo que el árbol hace
hoy es **aplicar lo que el catálogo ya dice**, que es la única rama que no
inventa una regla nueva. Las alternativas, para que se decidan y no se hereden:

| | alternativa | qué implica |
|---|---|---|
| **1** | *(implementada)* el catálogo manda: `piso` es de `D`, `L`, `O`. Mandarlo para `CASA`, `TERRENO`, `ALMACEN` u `OTRO` es un error | una puerta menos y una sola respuesta. **Cambia un 200 por un 400** en un caso que hoy nadie usa |
| **2** | `piso` aplica a los siete tipos | cuatro filas de catálogo en una migración nueva. Hay que responder si un **terreno** tiene piso, y qué se le pregunta entonces al corredor |
| **3** | `ubicacion.piso` es texto libre de ubicación y el `piso` gobernado es otra cosa | **es la doble autoridad**, con dos nombres para dos significados. Habría que nombrarlos distinto en el cable, o vuelve el defecto |

**Impacto medido de la rama 1, para que la decisión no se tome a ciegas:**

```
propiedades con `piso` en controllocal_dev       0  de 26
propiedades de tipo no aplicable con `piso`      0
suites E2E que mandan un piso                    0   (ninguna pasa -Piso)
consumidores afectados                           el fixture de conservacion, corregido
```

**Ningún dato real depende de la rama elegida.** Si CONTROL prefiere la 2 o la 3,
el cambio es barato y localizado; lo que no puede quedarse es el estado anterior,
en el que la respuesta dependía del hueco por el que entrara el dato.

**Y una segunda decisión, aparte de ésta y también abierta:** que `piso` viaje
por dos huecos en las dos direcciones. Mientras siga así, un cliente puede mandar
dos valores para un dato —ahora se avisa en vez de resolverlo en silencio, pero
la ambigüedad del contrato sigue ahí—. Unificarlo es un cambio de cable con
consumidor en Angular: **no se toca en 4.P y queda declarado**.

### Los otros cuatro hallazgos, y qué se hizo con cada uno

| # | hallazgo | corrección | prueba |
|---|---|---|---|
| **3** | se anotaba una `RETIRADA` **que no ocurrió**: nombrar en `atributosABorrar` una clave que nunca tuvo valor escribía un hecho fechado, con autor y canal, de algo que no pasó — en una tabla que no se puede corregir | los dos enrutadores anotan **sólo si había valor**. Retirar lo que no está sigue siendo legítimo y silencioso; lo que no deja es rastro de un no-hecho | `noSeAnotaUnaRetiradaQueNoOcurrio`, con control positivo (con valor **sí** anota) |
| **4** | el conjunto vacío se trataba de **dos formas a dos metros**: `escribirMultivalor` decía «el vacío es un conjunto» y `retirar` lo leía como escalar y devolvía `null` — perdiendo que el ancla existía | `retirar` lee **según la forma que declara el catálogo**, igual que escribe. Mismo principio, un solo sitio | `retirarUnMultivalorVacioNoLoConfundeConAusencia` |
| **5** | el arreglo del multivalor quitaba **el disparo, no la trampa**: seguía apoyado en una invariante —«ninguna entidad de este tipo está en el contexto»— que **no fijaba ningún test** | se cierra **por construcción**: se escribe la **diferencia** y se anota el **conjunto**. El elemento que está en los dos conjuntos ya no se borra ni se reinserta, así que no hay `merge` que pueda volverse un `UPDATE` de una fila borrada. `flushAutomatically` ordena lo pendiente | `elConjuntoSobreviveAunqueAlguienLoHayaLeidoAntes`, **verificado que muerde**: con el gesto anterior falla con el síntoma exacto (`[CONTROL_DE_ACCESO]` a secas) |
| **6** | `TRUNCATE` rodea el append-only | **declarado**, no cerrado — §8.9, con el motivo | — |

**Sobre el 5, la decisión y su porqué.** Se descartó `clearAutomatically = true`
en los dos `borrarDe`: vacía **el contexto entero** desde dentro de un
repositorio, así que desprende el agregado a medio editar y deja el resultado
dependiendo de un orden de *flush* que no se ve desde el sitio donde se escribe
la anotación. Es acción a distancia para arreglar un caso local. Tocar sólo lo
que cambia hace la invariante **innecesaria** en vez de exigirla, y el test la
sostiene igualmente por si alguien vuelve al gesto anterior.

### TERCERA VUELTA — la simetría, y la asimetría que apareció al fijarla

Fijar por prueba la decisión del titular —**las dos puertas responden lo mismo**—
no era un trámite: **la primera ejecución de esa prueba se puso roja**, y no por
la puerta de `ubicacion`.

```
lasDosPuertasDanLaMismaRespuesta:441
  Expected ReglaNegocioException to be thrown, but nothing was thrown.
```

**Una `CASA` se podía REGISTRAR con un `piso`, por las dos puertas.** Medido en el
código: la aplicabilidad se exigía en `convertir`, `convertirMultivalor` y
`escribirEnEdicion` — y **en ninguna de las dos mitades del alta**. Una clave
`ESTRUCTURAL` no pasa por `convertir` (no crea fila), así que el alta la escribía
sin preguntar el tipo.

El resultado era un dato **que entraba y ya no se podía corregir**: registrabas la
casa con piso `2`, y cualquier edición posterior de ese valor moría con «no aplica
a una propiedad de tipo CASA». Peor que rechazarlo.

**Cerrado**: `aplicarEstructuralesAlAlta` y `escribirAlAlta` exigen la
aplicabilidad, cada una en su mitad. Las dos, y no una: son los dos tiempos del
alta, y **la mitad que no exija se convierte en la puerta permisiva**.

> **Y esto no es la asimetría que buscaba el Auditor, es una tercera.** La suya
> era *puerta contra puerta*; la del titular, *catálogo contra cable*. Ésta es
> **alta contra edición**, y estaba viva antes de 4.P —`enrutarEstructurales`
> tampoco preguntaba—. Salió porque la prueba compara las dos puertas **en el
> alta**, que es donde nadie había mirado.

**Verificada por sabotaje**, y el primer intento enseñó algo: quitar la guarda de
**una sola** de las dos mitades **no pone la prueba en rojo** —las dos corren en
la misma transacción, la superviviente lanza, la transacción revierte y el
observable por el cable es idéntico—. Con las dos quitadas, que es el estado real
anterior:

```
lasDosPuertasDanLaMismaRespuesta:445
  Expected ReglaNegocioException to be thrown, but nothing was thrown.   ROJO
```

**Y eso se cerró, no se declaró.** Que una de las dos guardas pueda desaparecer
sin que nada avise no es un defecto hoy —el rollback salva el observable— pero el
día que el alta deje de ser una sola transacción **sobrevive la que quede, que
puede ser la equivocada**. Barrido con control positivo:
`exigirQueAplique` **no aparecía ni una vez en todo el árbol de tests**
(`aplicarEstructuralesAlAlta` sí, en `LinajeDeTodaEscrituraTest`, así que el
barrido funcionaba).

El gate nuevo —`ningunEscritorPierdeLaGuardaDeAplicabilidad`— es el mismo patrón
que `SETTER_POR_CONCEPTO`: **no vigila que el código haga algo, vigila que el
inventario siga completo**. Quitando **una sola** guarda:

```
LinajeDeTodaEscrituraTest.ningunEscritorPierdeLaGuardaDeAplicabilidad:375
  expected: <[aplicarEstructuralesAlAlta, convertir, convertirMultivalor,
              escribirAlAlta, escribirEnEdicion]>
  but was:  <[aplicarEstructuralesAlAlta, convertir, convertirMultivalor,
              escribirEnEdicion]>                                        ROJO
```

...mientras `lasDosPuertasDanLaMismaRespuesta` seguía **verde 1/1**. Ése era
exactamente el hueco: **un gate que no ve el defecto es peor que el defecto**,
porque el verde da confianza.

La prueba lleva además su **control positivo**: donde `piso` **sí** aplica, las
dos puertas **aceptan** y dejan `ALTA`. Sin él, un fallo que rechazara siempre
pasaría por simetría.

### Sabotaje B — la salida que faltaba

Constaban A (referencia a método) y C (quinto concepto). **B** —la llamada normal
al *setter*— no. Ejecutado y medido aquí:

```java
// inyectado en aplicarUbicacion:
if (ubicacion.piso() != null) { propiedad.setPiso(ubicacion.piso()); }
```

```
LinajeDeTodaEscrituraTest.soloLosEnrutadoresEscribenValores:230
  estos metodos escriben un valor gobernado fuera del enrutador de su sujeto:
  [com.controllocal.service.impl.PropiedadUniversalServiceImpl#aplicarUbicacion]
  ==> expected: <[]> but was: <[...#aplicarUbicacion]>
  Tests run: 5, Failures: 1                                              ROJO
```

**Muerde igual que con la referencia a método**, que era lo que había que
comprobar: `getAccessesFromSelf()` cubre las dos formas de invocación.

### `EDICION 6→6` no contradice al hallazgo 3

En el residuo hay una fila `EDICION` con `hallado = 6` y `valor = 6`: reafirmar el
mismo valor **deja fila**. Es correcto y hay que decirlo, porque leído deprisa
parece chocar con «no se anota una retirada que no ocurrió».

```
V83:  una fila por ESCRITURA, no por valor.
```

- **`EDICION 6→6`**: hubo una escritura. Alguien mandó el valor, en un instante,
  por un canal, y el Core lo escribió. Que coincidiera con el anterior no la
  convierte en un no-hecho — y para Intelligence «lo volvieron a afirmar el 25»
  es información, no ruido.
- **`RETIRADA` de lo que no existía**: **no hubo escritura ninguna**. No había
  fila que borrar ni columna que vaciar; el `DELETE` no tocó nada.

La regla es una sola y distingue las dos: **se anota lo que pasó**. En la primera
pasó algo; en la segunda, no.

### `RESIDUO_AUDITORIA_4P` — el residuo, remedido y con marca localizable

> **Corregido: la cifra anterior había dejado de ser cierta.** Esta sección decía
> «8 filas: 6 génesis + **2** de residuo». **Son 12: 6 génesis + 6 de residuo.**
> La reauditoría añadió cuatro filas más, y el número que yo había escrito
> envejeció en una tarde. Es la misma clase de afirmación que este corte lleva
> seis rondas cazando, cometida por mí. **Remedido ahora, no copiado.**

**Medición, `controllocal_dev`, hecha para esta versión:**

```
rastro_valor_gobernado                      12 filas
  genesis (V83, ids 1-6)                     6
  RESIDUO_AUDITORIA_4P (ids 23,24,41-44)     6   todas clave='piso', id_agregado=3260

  23  EDICION   7 -> 8        2026-08-25 11:05:33   (primera auditoria)
  24  RETIRADA  8 -> ausente  2026-08-25 11:05:52
  41  ALTA          -> 5      2026-08-25 13:34:27   (reauditoria)
  42  EDICION   5 -> 6        2026-08-25 13:34:28
  43  EDICION   6 -> 6        2026-08-25 13:34:29
  44  RETIRADA  6 -> ausente  2026-08-25 13:34:52
```

**Y aquí escribí una refutación FALSA, que es lo segundo que hay que corregir.**
Se me indicó que la reauditoría había dejado además las claves `AUD2-D-1/2/4/6`.
Respondí que **no existían**, con este barrido:

```
rastro_valor_gobernado   claves 'AUD%'   ->  0
atributo_propiedad       claves 'AUD%'   ->  0
catalogo_atributo        claves 'AUD%'   ->  0
propiedad                codigos 'AUD%'  ->  0
```

**Existen. Son siete.** Viven en `comando_idempotente`, que es la tabla donde vive
una clave de idempotencia — y es la única de las cinco que no miré:

```sql
SELECT id_comando, idempotency_key, tipo_comando, entidad_tipo, entidad_id, canal, fecha
  FROM comando_idempotente WHERE idempotency_key LIKE 'AUD%' ORDER BY id_comando;
```

```
126  AUD-4P-PISO-001  EDITAR_PROPIEDAD  PROPIEDAD 3260  SPA  2026-08-25 11:05:04
127  AUD-4P-PISO-002  EDITAR_PROPIEDAD  PROPIEDAD 3260  SPA  2026-08-25 11:05:33
128  AUD-4P-PISO-003  EDITAR_PROPIEDAD  PROPIEDAD 3260  SPA  2026-08-25 11:05:52
129  AUD2-D-1         EDITAR_PROPIEDAD  PROPIEDAD 3260  SPA  2026-08-25 13:34:27
130  AUD2-D-2         EDITAR_PROPIEDAD  PROPIEDAD 3260  SPA  2026-08-25 13:34:28
131  AUD2-D-4         EDITAR_PROPIEDAD  PROPIEDAD 3260  SPA  2026-08-25 13:34:29
132  AUD2-D-6         EDITAR_PROPIEDAD  PROPIEDAD 3260  SPA  2026-08-25 13:34:52
```

**Control positivo:** la tabla tiene **12** filas en total, así que el `7` no es
un cero disfrazado. *(Y la primera consulta que escribí ni siquiera compiló: pedí
`clave_idempotencia` y la columna se llama `idempotency_key`. No conocía la tabla
porque nunca la había mirado — que es exactamente el punto.)*

**El fallo fue de dos**: CONTROL dio por buena mi refutación al reelevarla en vez
de pedir la consulta. Queda escrito porque un error que sólo se le apunta a uno se
repite en el otro.

**No son historia del producto: `PROP-0023` nunca tuvo un piso 5, 6, 7 ni 8.**

**Y el linaje no se puede borrar, que es lo correcto**: la tabla es append-only y
su trigger lo impide — la misma garantía que el corte entrega es la que conserva
su propio residuo.

### `RESIDUO_AUDITORIA_4P` — el inventario COMPLETO, y sus dos consultas

**El residuo son 13 filas en dos tablas**, no 6 en una. La marca inicial sólo
cubría el linaje, que es la mitad que yo sabía mirar:

| tabla | filas | qué son |
|---|---|---|
| `rastro_valor_gobernado` | **6** | el linaje que produjo reproducir el defecto |
| `comando_idempotente` | **7** | las claves con las que se mandaron esos `PUT` |
| **base de datos, resto** | **0** | los `POST` de `CASA` revirtieron enteros |

```sql
-- RESIDUO_AUDITORIA_4P (1/2): linaje producido REPRODUCIENDO el defecto.
SELECT r.* FROM rastro_valor_gobernado r
  JOIN propiedad p ON p.id_propiedad = r.id_agregado AND p.organizacion_id = r.organizacion_id
 WHERE r.sujeto = 'PROPIEDAD' AND r.clave = 'piso' AND p.codigo = 'PROP-0023';

-- RESIDUO_AUDITORIA_4P (2/2): los comandos con los que se mandaron esas ediciones.
-- Viven en comando_idempotente, que es DONDE VIVE una clave de idempotencia --
-- y es la tabla que mi primer barrido no miro.
SELECT c.* FROM comando_idempotente c
 WHERE c.idempotency_key LIKE 'AUD%';
```

**Las dos consultas juntas son la marca.** Una sola dejaba fuera siete filas y
daba la falsa sensación de haber inventariado el residuo.

### El residuo en git, que tampoco es de la base

`dc54931` es un **commit colgante** que la auditoría creó con `commit-tree` para
demostrar que mis dos commits equivalen al *squash*. Verificado aquí:

```
git cat-file -t dc54931          -> commit
dc54931 tree                     -> 9a6478ae14a030e19a766d867b1be9b375588cb6
HEAD^{tree}                      -> 9a6478ae14a030e19a766d867b1be9b375588cb6   IDENTICOS
git for-each-ref --contains      -> (ninguna referencia)
git fsck  ->  34 objetos colgantes, 6 de ellos commits; dc54931 es uno
```

**Sin referencias: lo recoge el `gc` y no hay que hacer nada.** Se anota porque un
commit colgante con un árbol idéntico al de `HEAD` es justo la clase de cosa que
alguien encuentra dentro de un año y no sabe interpretar.

**El rechazo no dejó nada, y eso también se remidió.** Los `400` de `CASA` y
`TERRENO` revierten la transacción entera:

```
propiedades                26   (sin cambio; ultimo id 3263)
propiedades post-cutover    0   ninguna alta llego a comprometer
atributo_propiedad         76 filas ·  0 con fecha_actualizacion
propiedad 3260 . piso      NULL   (restaurado por el Auditor, verificado antes de tocar nada)
```

## 1. Las cinco superficies — todas, y la quinta decide la forma

| # | superficie | cómo queda | dónde se prueba |
|---|---|---|---|
| **1** | PROPIEDAD **escalar** | la fila vigente se actualiza; el rastro gana una **segunda** fila y la primera sigue ahí | casos 1 y 2 |
| **2** | PROPIEDAD **multivalor** | el conjunto **entero** anterior y el nuevo, en `rastro_valor_opcion` con `momento` | caso 4 y 4 bis |
| **3** | PROPIEDAD **retirada** | la fila vigente desaparece; el rastro gana `RETIRADA` **con el valor que se quitó** | caso 3 |
| **4** | **ENCARGO** en sus tres formas | idéntico, **desde el primer commit**: `sujeto='ENCARGO'`, `id_agregado=id_captacion` | caso 8 |
| **5** | **`ESTRUCTURAL`** | escribe `propiedad.metraje` y **no crea fila**; el rastro se indexa por clave, así que deja linaje igual | caso 5, 5 bis y **5 ter** |

**Las cuatro claves `ESTRUCTURAL`, medidas una por una** y no por extensión desde
`metraje_total`, que es el error que costó el primer rechazo:

| clave | campo canónico | ¿segunda puerta en el cable? | linaje |
|---|---|---|---|
| `metraje_total` | `METRAJE` | no | caso 5 |
| `piso` | `PISO` | **sí — `ubicacion.piso`**, la única que usa el SPA | **caso 5 ter** |
| `partida_registral` | `PARTIDA_REGISTRAL` | no | caso 5 bis |
| `oficina_registral` | `OFICINA_REGISTRAL` | no | cubierta por el gate de cobertura |

**La quinta es la que decide la forma.** Está comprobado en el propio test:
después de escribir `metraje_total`, `select count(*) from atributo_propiedad
where clave='metraje_total'` sigue siendo **0** — y su linaje existe. Ninguna
columna en esa tabla podía darle procedencia, y ése es el dato que mató A1.

---

## 2. LOS OCHO CASOS — cada uno con su medición

Todos en `ProcedenciaDelValorIntegrationTest`, **14 pruebas, contra PostgreSQL
real** (`controllocal_repositorios`).

### Caso 1 · valor simple nuevo — `valorSimpleNuevo`

```
ausente  ->  torre_bloque = "Torre A"
```

| medición | resultado |
|---|---|
| filas de linaje | **1** |
| `verbo` | `ALTA` |
| `valor_texto` | `Torre A` |
| `hallado_texto` | **NULL** — un alta no encontró nada |
| `canal` · `id_persona_rol` · `rol_actor` · `registrado_en` | SPA · el del actor · `AGENTE` · presente |
| `naturaleza` | **NULL** |

**La procedencia operacional está completa aunque la naturaleza no conste.** Es
la mitad que el Core sí sabe siempre; la otra se declara o se calla.

### Caso 2 · edición — `editarConservaLoAnterior`

```
estado_conservacion:  BUENO  ->  MUY_BUENO
```

| medición | resultado |
|---|---|
| filas de linaje | **2** |
| fila 1 | `ALTA` · `BUENO` · canal `SPA` · naturaleza **NULL** |
| fila 2 | `EDICION` · `MUY_BUENO` · canal `API` · naturaleza `OBSERVADO` |
| `hallado_texto` de la fila 2 | **`BUENO`** |
| valor vigente | `MUY_BUENO`, uno solo |

**A sigue ahí con SU procedencia intacta.** Es exactamente la segunda fila que
`uq_atributo_propiedad_clave` impide tener en la tabla de valor y que el `UPDATE`
pisaba.

### Caso 3 · borrado — `borrarConservaLaHistoria`

```
torre_bloque = "Torre B"  ->  ausencia vigente
```

| medición | resultado |
|---|---|
| fila vigente en `atributo_propiedad` | **no existe** (borrado físico, sigue siéndolo) |
| filas de linaje | **2** |
| fila 2 | `RETIRADA` · `valor_texto` **NULL** · `hallado_texto` **`Torre B`** |
| actor y fecha de la retirada | presentes |

**La clave queda con linaje y sin valor vigente** — imposible si el rastro
colgara del `id` de la fila borrada.

### Caso 4 · `LISTA_MULTIPLE` — `multivalorConservaElConjuntoAnterior`

```
vigilancia:  {CASETA_24H, CAMARAS_CCTV}  ->  {CAMARAS_CCTV, CONTROL_DE_ACCESO}
```

| medición | resultado |
|---|---|
| `ALTA` · `ESCRITO` | `[CAMARAS_CCTV, CASETA_24H]` |
| `ALTA` · `HALLADO` | `[]` |
| `EDICION` · `HALLADO` | **`[CAMARAS_CCTV, CASETA_24H]`** — el conjunto anterior **entero** |
| `EDICION` · `ESCRITO` | `[CAMARAS_CCTV, CONTROL_DE_ACCESO]` |
| conjunto vigente | `[CAMARAS_CCTV, CONTROL_DE_ACCESO]` |

**Se guarda el conjunto, no la diferencia.** «Se quitó `CASETA_24H`» no permite
reconstruir qué había si el conjunto anterior fuera legado y nadie lo hubiera
escrito nunca.

**Y 4 bis — `vaciarUnMultivalorTambienDejaLinaje`:** vaciar la lista es una
escritura con autor y fecha, no una ausencia. `HALLADO = [PORTERO_DIURNO]`,
`ESCRITO = []`, `es_multivalor = true`. El conjunto vacío **es** un conjunto.

> **Este caso encontró un defecto real y lo cerró.** Leer el conjunto anterior
> —que antes de 4.P nadie leía— metía las entidades en el contexto de
> persistencia; el `borrarDe` es un DELETE masivo que **no lo limpia**, y el
> `save` posterior de un elemento presente en **los dos** conjuntos se resolvía
> como `merge` → UPDATE de una fila ya borrada → **el elemento desaparecía de la
> ficha**. Medido: `{CASETA_24H, CAMARAS_CCTV}` → `{CAMARAS_CCTV,
> CONTROL_DE_ACCESO}` dejaba **`{CONTROL_DE_ACCESO}`** a secas. Cerrado con una
> proyección escalar (`valoresDe`) en los dos repositorios de multivalor, con la
> razón escrita en su javadoc.

### Caso 5 · `ESTRUCTURAL` legado — `estructuralLegadoConservaElValorHallado`

```
metraje:  120 (alta)  ->  100 (escrito por SQL, fuera de la frontera)  ->  105
```

| medición | resultado |
|---|---|
| filas en `atributo_propiedad` para `metraje_total` | **0**, antes y después |
| filas de linaje añadidas por la edición | **1** |
| `verbo` | `EDICION` |
| `hallado_numero` | **`100`** — lo que el Core **encontró**, no lo que decía la última fila de linaje (`120`) |
| `valor_numero` | `105` |
| procedencia de la fila | la del **acto que escribió el 105** (canal `SPA`, su actor, su instante) |
| naturaleza | **NULL** |
| `propiedad.metraje` final | `105` |

**Del 100 no se afirma nada más.** No se le pone canal, ni actor, ni fecha de
nacimiento, ni naturaleza: lo único que consta es *«en el momento de esta
edición, el Core encontró este valor»*, que es una **constatación del estado
hallado** y no una génesis. Que el `hallado` sea `100` y no `120` demuestra que
se **lee la autoridad**, no se deduce del rastro.

**5 bis — `legadoSinLinajeNoEsDefectoAntesDelCutover`:** `partida_registral`
escrita por SQL directo queda con **cero** filas de linaje —y eso **no es un
defecto**, porque cae antes de la frontera— y su primera edición posterior
conserva `P-11111111` como hallado y `P-99999999` con procedencia completa.
`select frontera_de_linaje()` responde, así que un gate puede decir de qué lado
cae cada fila **sin criterio propio**.

### Caso 6 · operación mixta — `unaOperacionConNaturalezasDistintas`

Un solo `PUT`, tres valores:

| clave | naturaleza declarada | **medida** |
|---|---|---|
| `estado_conservacion` | `OBSERVADO` | **`OBSERVADO`** |
| `mascotas_reglamento` | `DECLARADO` | **`DECLARADO`** |
| `ascensores` | ninguna | **NULL** |

**Cero contaminación entre naturalezas**, y el otro eje —`canal = SPA`— es el
mismo para los tres, porque es del **acto**. Es el caso que abrió 4.P: una sola
respuesta al guardar habría estampado una naturaleza falsa en dos de las tres.

**6 bis — `elCoreNoDeduceLaNaturaleza`:** el **mismo** valor (`balcon = true`)
escrito **por dos canales y con dos actores distintos**:

```
SPA · actor A  ->  naturaleza NULL
API · actor B  ->  naturaleza NULL
```

Los canales y los actores **sí** difieren (`SPA` ≠ `API`, `id_persona_rol`
distintos). La naturaleza no se deriva de ninguno de los dos. Es una prohibición
ejecutable: el test falla si alguien la infiere.

### Caso 7 · `INFERIDO` — `inferidoExigeAutorModeloVersionYConfianza`

| intento | resultado |
|---|---|
| `INFERIDO` sin agente, modelo, versión ni confianza | **rechazado**; el mensaje nombra *el agente* y *la confianza* |
| `INFERIDO` con agente + modelo + versión, **sin confianza** | **rechazado**; el mensaje nombra *la confianza* |
| `INFERIDO` completo | **aceptado**: `agente=kairos`, `modelo=vision-brox`, `version=v3`, `confianza=0.810`, `canal=WHATSAPP`, `evidencia_ref=foto:fachada-3` |
| el mismo `INFERIDO` incompleto **por SQL directo** | **rechazado por la base** (`ck_rastro_inferido_completo`) |

**Aquí Java y PostgreSQL dicen lo mismo**: el Core da el mensaje, la base da la
garantía. Una inferencia sin autor no se puede revisar ni retirar el día que el
modelo resulte estar equivocado, y en silencio se convierte en un hecho
confirmado.

### Caso 8 · `ENCARGO` — `elEncargoTieneLaMismaGarantia`

| forma | medición |
|---|---|
| `ALTA` escalar (`garantia_meses = 2`) | 1 fila, `sujeto=ENCARGO`, `id_agregado=id_captacion`, canal `SPA` |
| `ALTA` multivalor (`equipamiento_incluido`) | `ESCRITO = [COCINA, REFRIGERADORA]` |
| `EDICION` escalar (`2 -> 3`, `DECLARADO`) | `hallado_numero = 2`, naturaleza `DECLARADO` |
| `EDICION` multivalor | `HALLADO = [COCINA, REFRIGERADORA]` · `ESCRITO = [COCINA, LAVADORA]` |
| `RETIRADA` | `valor_numero` NULL, `hallado_numero = 3`, y **cero** filas en `atributo_encargo` |

**Simetría desde el primer commit.** Y cierra de paso la asimetría medida en el
preflight: hasta hoy pactar una condición no dejaba **ningún** rastro, ni
siquiera el de operación.

---

## 3. LAS 12 INVARIANTES, una por una

| # | invariante | cómo queda | dónde |
|---|---|---|---|
| 1 | toda escritura gobernada nueva deja procedencia, en PROPIEDAD **y** ENCARGO | **sostenida en la frontera del servicio** | `LinajeDeTodaEscrituraTest` (3 reglas + control positivo) · gate `.sql` «después del cutover ningún hecho / ninguna condición sin linaje» |
| 2 | misma transacción que el valor | el enrutador escribe valor y linaje dentro del mismo `@Transactional`; no hay dos fases ni cola | casos 1–8: toda la lectura es post-commit |
| 3 | editar no destruye la procedencia anterior | la fila `ALTA` sigue con su canal y su naturaleza | caso 2 |
| 4 | borrar no destruye la historia | `RETIRADA` con el valor que se quitó | caso 3 |
| 5 | `INFERIDO` exige quién/regla/modelo/versión + confianza | CHECK + validación en el Core | caso 7 · gate `.sql` |
| 6 | el legado sin procedencia demostrable **no recibe procedencia inventada** | **cero génesis con naturaleza**; y las 70 de `V48` **se quedaron sin génesis** (§4) | gate `.sql` «ninguna génesis declara naturaleza» |
| 7 | `LOC-D001` / `LOC-0002` con la fuente demostrable, sin inventar quién originó el texto | `evidencia_ref = "evento_dominio#1280 + propiedad.descripcion"` y `naturaleza` **NULL** | gate `.sql` «las transcripciones documentadas nombran su fuente» |
| 8 | Web y KAIROS, la **misma** semántica | un solo `ValorAtributo`; KAIROS entra por el mismo campo del cable | `MotorDeCapturaImpl` converge en `ValorAtributo`; caso 7 usa `Procedencia.deAgente` |
| 9 | lo que el Core ya sabe no lo teclea el usuario | canal, agente, actor, rol y `registrado_en` los deriva el Core; el cliente sólo puede aportar naturaleza, confianza, `observadoEn` y `evidenciaRef` | `AtributoRequest` |
| 10 | **ningún valor actual se pierde ni cambia** | `atributo_propiedad` sigue en **76** filas, **0** con `fecha_actualizacion`, mínimo `2026-08-17 23:23:23.570485+00` | medición sobre `controllocal_dev` |
| 11 | bloqueados **siguen en 19**, ningún `P` nuevo, ninguna publicación cambia | locales sin `tipo_acceso` = **19** de 21 · hitos `P` = **3** · publicación `C=9`, `P=3` | medición sobre `controllocal_dev` |
| 12 | `evento_dominio` sigue siendo outbox | sigue emitiéndose, y su carga útil **sigue sin nombrar la clave** | `elOutboxSigueSiendoElOutbox` |

Y la decimotercera que el encargo añade aparte —**el multivalor conserva el
conjunto anterior**— está en el caso 4.

---

## 4. LA GÉNESIS DEL LEGADO — qué se sembró y qué NO, con la medición

### 4.1 · Las 70 de `V48` **se quedan sin génesis** — y el motivo es el CANAL

> **Corregido tras la auditoría.** La primera versión de esta evidencia decía que
> «la única forma de identificarlas es comparar contra `installed_on`». **Es
> falso, y el Auditor tenía razón:** la partición por correlación las separa sola
> —`0 eventos → 70`, `1 evento → 6`, `≥2 → 0`— y su fecha es su propia
> `fecha_creacion`, exactamente el mismo estándar que se aplicó a las 6
> sembradas. El argumento estaba apoyado en el pie flojo. Lo que sigue es el
> terreno firme.

**Lo que de esas 70 no se puede demostrar es su CANAL.**

`V48` no **originó** esos valores: los **transcribió**. Cada uno de sus `INSERT`
copia una columna legada que ya tenía valor —`propiedad.metraje`,
`propiedad.zonificacion`, `propiedad.piso`, `detalle_local_comercial.*`— y de
**quién escribió aquella columna, y por dónde, no consta nada**. Estampar
`canal = SISTEMA` afirmaría que el valor lo originó el sistema, y eso es
precisamente lo que §6 quinquies prohíbe con esas palabras:

> «No se crea `DESCONOCIDO` como naturaleza **ni se estampa `SISTEMA` como
> origen del valor**.»

Una génesis sin canal tampoco sirve: la génesis existe para decir de dónde salió
el valor, y una fila que sólo dice *cuándo se copió* no responde a eso — responde
a otra pregunta, y ponerla en la columna del origen la haría pasar por lo que no
es.

**Así que esas 70 filas no reciben génesis, y queda dicho.** Es la salida que la
decisión congelada preveía explícitamente. Las 6 que sí la reciben la reciben
porque su `evento_dominio` **sí demuestra el canal y el actor**, y nada más.

> **Una nota sobre el comentario de `V83`.** Su bloque §5 recoge la formulación
> anterior —la de `installed_on`—, y **no se ha reescrito a propósito**: la
> migración está aplicada y la regla de esta máquina es que una migración
> aplicada no se edita. El argumento que gobierna es éste; el del fichero está
> **superado**, y la misma corrección está escrita en
> `gate-modelo-universal.sql`, que es donde mira quien vaya a tocar esto.

### 4.2 · Las cuatro `ESTRUCTURAL`: sin génesis retroactiva (E2, decisión del titular)

No hay fecha por valor y nada demuestra que el `metraje` lo escribiera el sistema
y no un agente desde el SPA. Su linaje **empieza en la primera escritura
posterior a `V83`**, que sí es completa — y ésa conserva el valor anterior en
`hallado_*` (caso 5).

### 4.3 · Lo que **sí** se sembró: 6 filas, y sólo lo que el evento demuestra

Filas de `atributo_propiedad` con **exactamente un** `evento_dominio`
correlacionable dentro de la ventana de su transacción:

```
id_agregado  clave             valor              canal  actor  evidencia_ref
     2       tipo_acceso       GALERIA_INTERIOR   SPA     28    evento_dominio#1281 + propiedad.descripcion
     3       tipo_acceso       A_PIE_DE_CALLE     SPA     28    evento_dominio#1280 + propiedad.descripcion
  3260       dormitorios       3                  SPA     28    evento_dominio#1274
  3261       zonificacion      RDM                SPA     28    evento_dominio#1276
  3262       dormitorios       4                  SPA     28    evento_dominio#1278
  3263       rubro_permitido   Cafeteria          SPA     28    evento_dominio#1279
```

**Naturaleza NULL en las seis.** `registrado_en` es la propia `fecha_creacion` de
la fila —una columna suya, no una deducción— y canal/actor salen del evento.
`rol_actor` queda **NULL**: el evento guarda el id del rol, no con qué banda se
actuó, y no se inventa.

**Y la fuente TEXTUAL de las dos transcripciones documentadas** (`LOC-D001` y
`LOC-0002`) se añade **sólo si la migración comprueba que la `descripcion` sigue
conteniendo la frase literal**. Si el texto hubiera cambiado, no se escribe nada.
No se les atribuye `DECLARADO`: el sistema no puede demostrar que hubiera una
declaración de contraparte.

**Cero filas cayeron fuera de los tres casos previstos**, así que no hubo `STOP`.

---

## 5. La frontera de garantía, explícita en el modelo

```
ANTES de frontera_de_linaje()   -> puede existir legado SIN linaje.  NO es defecto.
DESPUES                          -> una escritura gobernada SIN linaje ES UN DEFECTO.
```

`frontera_de_linaje()` es una función `IMMUTABLE` con el instante de instalación
de `V83` **horneado en su cuerpo**. Va como función y no como fila por dos
razones: una fila se puede `UPDATE`ar y esta frontera no debe moverse nunca, y un
gate necesita poder preguntarla desde SQL sin acordarse de qué tabla mirar.

El gate la usa en las dos direcciones: «ninguna génesis declara naturaleza»
(antes) y «después del cutover ningún hecho del inmueble / ninguna condición del
encargo sin linaje».

---

## 6. Los SQL directos de tests y gates — qué garantiza el servicio y qué no

**La procedencia NO es `NOT NULL` en las cuatro tablas de valor, y es una
decisión, no una concesión.** Seis suites E2E y `gate-modelo-universal.sql`
escriben en `atributo_propiedad` por SQL directo **a propósito**: es como se
prueban los triggers de la base, intentando romperlos. Un `NOT NULL` allí
convertiría el gate en rehén del servicio y le quitaría exactamente la capacidad
por la que existe.

| | garantía |
|---|---|
| **el servicio SÍ garantiza** | que **ninguna operación del producto** escribe un valor gobernado sin decir de dónde sale — en las cinco superficies y en los dos sujetos |
| **un `INSERT` manual NO garantiza nada** | escribe el valor y **no deja linaje**. No es un defecto: es SQL deliberado fuera de la frontera |

**No se debilitó ninguna prueba para que pasara.** Ninguna suite E2E se ajustó
—precisamente porque la procedencia no es obligatoria en el esquema— y las
pruebas modificadas lo fueron para **apretar**, no para aflojar:

- `UnSoloLectorPorSujetoTest`: la lista de clases con permiso **encogió**
  (`PropiedadUniversalServiceImpl` ya no escribe valores).
- `GateDeCierreTest` y `Verificar-Cierre.ps1`: inventarían el test nuevo, así que
  la corrida de cierre exige que se haya **ejecutado**.
- `gate-modelo-universal.sql`: **16 comprobaciones nuevas**, ninguna retirada.
- `LinajeDeTodaEscrituraTest`: pasó de mirar `getMethodCallsFromSelf()` a
  `getAccessesFromSelf()` y ganó las cuatro columnas `ESTRUCTURAL` como destino
  vigilado, más un control de cobertura contra los conceptos declarados. **Ve
  estrictamente más que antes.**

**Y las dos correcciones de la segunda vuelta se verificaron por el único método
que vale: reintroduciendo el defecto.** El gate de arquitectura se puso rojo
nombrando `aplicarUbicacion`; el test del multivalor falló con el síntoma exacto
que había descrito la auditoría. Un gate que no se ha visto morder no es un gate.

Y la propia frontera se usa dentro del test: el legado del caso 5 se fabrica
**con un `UPDATE` por SQL directo**, que es la forma honesta de tener un valor
sin linaje.

---

## 7. Angular — no estrena superficie, y se demuestra

**Cero ficheros de Angular tocados.** El contrato gana cuatro campos
**opcionales** en `AtributoRequest` (`naturaleza`, `confianza`, `observadoEn`,
`evidenciaRef`) y ningún cliente actual los manda, así que el cable de siempre
sigue funcionando sin cambiar una línea del SPA.

**Prohibido y no hecho:** un `Naturaleza: [ DECLARADO ▼ ]` al lado de cada uno de
los ~40 campos. Cuando llegue esa UX, la interfaz hablará llano —«lo observé»,
«me lo dijeron»— y el vocabulario del enum **no se le enseñará al usuario**.

`ng test` y `ng build --configuration production` se corrieron **aunque no se
tocara Angular**, precisamente para demostrarlo (§9).

---

## 8. LOS HUECOS — lo que NINGÚN test cubre, dicho y no disimulado

1. **La invariante 1 no se sostiene a nivel de esquema**, y no puede. Se sostiene
   en la frontera del servicio con un test de arquitectura, y **ese test no verá
   un escritor privado nuevo dentro de los propios enrutadores** si lo llama un
   método público que sí anota. Es el mismo límite que ya declara
   `PuertasDePublicacionTest`. Tampoco ve nada escrito por SQL directo.
2. **La génesis sembrada descansa en una correlación, no en un dato.** El vínculo
   fila↔evento **no está registrado en ninguna parte** —la carga útil del evento
   es `{"idPropiedad": N}` y no nombra la clave—, así que se reconstruye por
   coincidencia de agregado e instante. Se exige **unicidad** para reconstruir
   sólo cuando no hay elección posible, pero eso no la convierte en un hecho.
3. **La invariante 8 se prueba en el Core; que KAIROS lo consuma igual no tiene
   suite propia hoy.** El Core publica una sola semántica y el motor de captura
   converge en el mismo `ValorAtributo`; lo que no existe es un consumidor real
   contra el que medirlo.
4. **El linaje no se lee todavía por el cable.** `AtributoFicha` no publica la
   procedencia del valor: en 4.P sólo se escribe. Publicar un campo de lectura
   sin consumidor sería estrenar superficie, que es justo lo que el titular
   acotó. La consulta existe (`RastroValorGobernadoRepository.historiaDe` /
   `historiaDelAgregado`) y no la usa ningún endpoint.
5. **El multivalor legado anterior al cutover no es reconstruible**, y no puede
   serlo: su conjunto se destruía al reescribirlo. La primera edición posterior a
   `V83` sí conserva el conjunto hallado; lo de antes, no.
6. **`observado_en` y `evidencia_ref` viajan por el cable y ningún productor los
   manda hoy.** Están porque el modelo aprobado los lleva y porque `evidencia_ref`
   es lo que sostiene la invariante 7; su UX es posterior.
7. **La correlación de génesis del ENCARGO no sembró nada** porque
   `atributo_encargo` tiene cero filas. La rama está escrita y ejercitada por los
   tests, no por datos históricos.
8. **`4P despues del cutover ninguna COLUMNA ESTRUCTURAL sin linaje` es hoy vacua
   por datos — y su alcance excluye, POR CONSTRUCCIÓN, el escenario que produjo
   el defecto.** Las dos mitades, y la segunda es la que importa:
   - **Vacua por datos**: sólo mira propiedades registradas **después** del
     cutover, y en `controllocal_dev` hay **0**. Medido: quitándole el filtro de
     frontera, el predicado encuentra **26** filas —legado legítimo—, así que el
     cuerpo funciona y lo vacío es la muestra.
   - **Y no habría cazado el defecto, nunca.** `PROP-0023` se registró **antes**
     del cutover, así que queda fuera de `fecha_registro > frontera_de_linaje()`
     por definición. Un `piso` escrito hoy sin linaje sobre una propiedad vieja
     **no la enciende**. No puede: de una columna sin fecha propia sobre una
     propiedad anterior al cutover no se puede afirmar cuándo se escribió, y esa
     comprobación se negó a afirmarlo.
   - **La red real es el gate de arquitectura**, que sí muerde y lo hace sobre el
     código, no sobre los datos: `LinajeDeTodaEscrituraTest` se pone rojo con la
     referencia a método, con la llamada normal y con un quinto concepto
     canónico. **Nadie debe leer la comprobación `.sql` como la protección
     principal.** Lo que sí aporta sin depender de los datos es su gemela,
     `4P la frontera vigila TODOS los campos canónicos declarados`: ésa se pone
     roja el día que aparezca un campo sin vigilar.

   *(Las comprobaciones se nombran, no se numeran: cualquier añadido renumera la
   lista entera y una referencia por ordinal envejece a la primera inserción.)*
9. **`TRUNCATE` rodea el append-only.** Los dos triggers son `FOR EACH ROW`, y
   `TRUNCATE` no dispara triggers de fila: vaciaría las dos tablas sin error. Es
   la **misma limitación del patrón que el modelo aprobó** —`V76`
   (`tg_observacion_append_only`) la tiene igual— y **no es una regresión**,
   pero no estaba declarada y ahora lo está. No se cierra en este microcorte por
   dos razones: exigiría una migración nueva sólo para eso, y cerrarla aquí
   dejaría `observacion_mercado` más débil que su gemela sin que nadie lo
   hubiera decidido. `TRUNCATE` además exige privilegios de dueño de la tabla,
   que la aplicación no usa.

10. **`ubicacion.piso` en una organización que no gobierne el campo `PISO`: ahora
    se RECHAZA, y antes se perdía.** Si `claveDelCampo(PISO)` volviera vacío,
    `conElPisoGobernado` devolvía el mapa intacto y el valor **desaparecía con un
    `200`** — el comentario decía que «se queda donde estaba», y desde que
    `aplicarUbicacion` dejó de escribirlo eso era **falso**. Arreglado: «aquí no
    se gobierna el piso» significa **se dice**, nunca **se pierde**. La rama
    **sigue siendo inalcanzable** —`piso` es clave del sistema y ninguna
    organización puede retirarla—, así que no hay test que la ejercite: queda
    declarada aquí y el mensaje está escrito para el día que deje de serlo.
11. **La simetría se comprueba por COMPORTAMIENTO sólo para `piso`.**
    `lasDosPuertasDanLaMismaRespuesta` compara las dos puertas sobre la única
    clave `ESTRUCTURAL` que tiene dos; las otras tres (`metraje_total`,
    `partida_registral`, `oficina_registral`) tienen una sola, y `metraje_total`
    además aplica a los siete tipos, así que no hay caso negativo que escribir.
    Lo que **sí** cubre a las cuatro es el gate estructural
    (`ningunEscritorPierdeLaGuardaDeAplicabilidad`), que vigila el inventario de
    escritores y no un valor concreto — y por eso se puso rojo donde la prueba de
    comportamiento se quedaba verde.

---

---

## 8 bis. LA LECCIÓN DE MÉTODO — el mismo error, dos veces, y el control positivo no lo vio

**Es lo más valioso que deja este microcorte, y no es técnico.**

El mismo error de método se cometió **dos veces**, con dos vueltas de distancia:

| # | qué se barrió | dónde estaba de verdad | qué costó |
|---|---|---|---|
| **1** | «productores de **las cuatro TABLAS** de valor» | en **las cuatro COLUMNAS** `ESTRUCTURAL`, que no tienen tabla | el defecto de `ubicacion.piso`: la única puerta que usa el producto, escribiendo sin linaje |
| **2** | claves `AUD%` en **cuatro tablas de clave y catálogo** | en **`comando_idempotente`**, que es donde vive una clave de idempotencia | una **refutación falsa** escrita en esta evidencia y elevada como cierta |

**La forma del error es idéntica:** barrer **donde esperas encontrarlo** en vez de
preguntar **dónde puede vivir esa clase de cosa**. En el primero, el inventario
partió de las tablas que ya conocía y la quinta superficie —la que no tiene
tabla— quedó fuera. En el segundo, busqué una clave en las tablas que tienen una
columna llamada `clave`, y una clave de idempotencia no está en ninguna de ellas.
Ni siquiera sabía cómo se llamaba la columna: mi primera consulta pidió
`clave_idempotencia` y falló, porque es `idempotency_key`.

### Y aquí está lo que hay que escribir, porque llevamos toda la sesión apoyándonos en ello

> **El control positivo NO protege de este error.** Pasa **trivialmente sobre el
> universo equivocado.**

Mi barrido de las claves `AUD%` **llevaba control positivo** —comprobé que las
tablas no estaban vacías— y aun así devolvió un cero verdadero sobre un universo
que no contenía la respuesta. El control positivo responde a *«¿mi consulta sabe
encontrar algo?»*; **no responde a *«¿estoy mirando donde puede estar?»***. Son
dos preguntas y la sesión entera ha estado usando la primera como si contestara la
segunda.

**Lo que sí lo habría cazado, en los dos casos:** enumerar primero **las clases de
sitio donde ese tipo de cosa puede vivir** —para un valor gobernado: tabla de
atributos, columna del agregado, tabla hija de multivalor; para una clave de
idempotencia: la tabla de comandos— y sólo después barrer. El inventario se hace
**por concepto**, y la consulta después.

**Y una segunda cosa, del proceso:** la refutación falsa no la paró nadie. CONTROL
la dio por buena al reelevarla, en vez de pedir la consulta que la sostenía. **El
fallo fue de dos**, y se anota así porque un error que sólo se le apunta a uno se
repite en el otro.

**Ninguno de los dos casos lo habría visto un gate.** El primero lo cerró un test
de arquitectura *después* de que un humano lo encontrara; el segundo no es
código, es método. Por eso queda escrito aquí y no en un `assert`.

---

## 9. La corrida de cierre

`backend-spring/verificacion/Verificar-Cierre.ps1`, con
`TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_repositorios` y
JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`).

### 9.1 · Gate del modelo universal contra `controllocal_dev`

```
85 en verde  ·  0 en rojo  ·  85 total
```

**16 comprobaciones de 4.P**, nombradas y no numeradas —cualquier añadido
renumera la lista y una referencia por ordinal envejece a la primera inserción:

```
4P existen las dos tablas del linaje
4P el linaje NO cuelga del id de la fila vigente
4P el linaje se direcciona por la clave logica
4P el linaje no se puede corregir
4P el linaje no se puede borrar
4P no hay una cuarta naturaleza
4P un INFERIDO sin autor, modelo, version ni confianza
4P un ALTA no puede haber hallado un valor
4P una RETIRADA no deja valor vigente
4P ninguna genesis declara naturaleza
4P la frontera del cutover existe y ya paso
4P despues del cutover ningun hecho del inmueble sin linaje
4P despues del cutover ninguna condicion del encargo sin linaje
4P despues del cutover ninguna COLUMNA ESTRUCTURAL sin linaje
4P la frontera vigila TODOS los campos canonicos declarados
4P las transcripciones documentadas nombran su fuente
```

**Y no se lea la penúltima como la protección real**: es vacua por datos y su
alcance excluye por construcción el escenario que produjo el defecto (§8.8). La
red es el gate de arquitectura.

### 9.2 · Reactor completo contra PostgreSQL real

```
servicios     720 tests · 0 fallos
web            48 tests · 0 fallos
aplicacion    431 tests · 0 fallos
              ---
              1199 tests · 0 fallos · 0 saltados     BUILD SUCCESS
```

De 4.P:

```
LinajeDeTodaEscrituraTest             6 tests   (arquitectura, +1 en la 4.a vuelta)
ProcedenciaDelValorIntegrationTest   20 tests   (8 casos + hallazgos + simetria)
```

**Los 21 tests de integración aparecen EJECUTADOS**, comprobado por el script
contra la salida de Maven — no «no fallaron».

### 9.3 · Suites E2E

```
comision-movimientos       65 OK / 0 fallas
disponibilidad-contrato    41 OK / 0 fallas
f4-solicitud              125 OK / 0 fallas
estabilizacion-alquiler    18 OK / 0 fallas
editor-universal          147 OK / 0 fallas
                          ---
                          396 comprobaciones / 0 fallas
```

```
== CIERRE VERDE ==
```

### 9.4 · Angular, corrido aunque no se tocara

```
ng test                                671 SUCCESS / 671
ng build --configuration production    BUILD OK, sin errores
```

Sólo los avisos de presupuesto **preexistentes**. Corrido aparte de las suites
E2E, no en paralelo.

### 9.5 · Los sabotajes — ningún gate se entrega sin verlo morder

| sabotaje | resultado medido |
|---|---|
| `propiedad::setPiso` (**referencia a método**) | **ROJO**, nombra `aplicarUbicacion` |
| `propiedad.setPiso(...)` (**llamada normal**) | **ROJO**, nombra `aplicarUbicacion` |
| gesto original del multivalor | **ROJO**, síntoma exacto `[CONTROL_DE_ACCESO]` |
| quitar **las dos** guardas de aplicabilidad del alta | **ROJO**, `nothing was thrown` |
| quitar **una sola** guarda — prueba de comportamiento | **verde 1/1**: la otra lanza y revierte |
| quitar **una sola** guarda — **gate de inventario** | **ROJO**, nombra `escribirAlAlta` |

Las dos últimas filas juntas son el motivo de la cuarta vuelta: la primera es el
hueco, la segunda es el cierre.

### 9.6 · Conservación y residuo, remedidos al cerrar

```
atributo_propiedad          76 filas ·  0 con fecha_actualizacion
publicacion                 C = 9  ·  P = 3
locales bloqueados          19
propiedades                 26  ·  propiedad 3260 . piso = NULL
rastro_valor_gobernado      12 filas: 6 genesis + 6 RESIDUO_AUDITORIA_4P
comando_idempotente         12 filas: 5 del producto + 7 RESIDUO_AUDITORIA_4P
```

**Ningún valor actual se perdió ni cambió.** La corrida de cierre **no añade
residuo** a la cartera: las suites E2E usan bases efímeras y las de integración
`controllocal_repositorios`.

### 9.7 · La migración

`V83` sigue aplicada y **no se ha tocado** en ninguna de las cuatro vueltas: lo
que cambió es el código que escribe y los gates que lo vigilan.

### 9.8 · `git diff --check`

Sin avisos.

## 10. Lo que este microcorte NO hizo

No se abrió el **Corte 5** ni **I0**. No se tocaron `V81` ni `V82`. No se añadió
ninguna columna a `atributo_propiedad` ni a `atributo_encargo` — esas dos tablas
siguen siendo el **estado vigente** y no cambiaron ni una fila. No se movió
ninguna clave entre `PROPIEDAD` y `ENCARGO`, ni entre `ALT`/`PUB`/`OPC`, ni entre
`ATRIBUTO` y `ESTRUCTURAL`. `evento_dominio` sigue siendo el outbox.
