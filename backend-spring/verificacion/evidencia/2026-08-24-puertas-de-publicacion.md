# Evidencia — Microcorte · ningún camino de creación elude la publicabilidad

**Fecha:** 2026-08-24
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `7655295` (encargo congelado en `0d83d81`, sólo documentación)
**Encargo:** `docs/ai/encargo-cerrar-puertas-de-publicacion.md`
**Migración:** **ninguna.** No se toca el esquema ni Angular.

---

## 1. Preflight — verificado con control positivo, no copiado

`7655295..0d83d81` = **sólo documentación**. Árbol limpio, dev en `V82`.

**Control positivo primero**: `.crearEnEncargo(` **sí** aparece en `src/main`
(`EncargosController:81`). Con el barrido demostrado vivo, sus ceros valen:

| # | vía | consumidores en `src/main` | expuesta | ¿`exigirPublicable`? |
|---|---|---|---|---|
| 1 | `crearEnEncargo` | `EncargosController:81` | **SÍ** | **sí** (`:94`) |
| 2 | `cambiarEstado` | `EncargosController:107` | **SÍ** | **sí** (`:309`, al pasar a `PUBLICADO`) |
| 3 | `actualizar` | `EncargosController:94` | SÍ | no crea, **y no toca el estado** |
| 4 | `crear(idPropiedad, …)` | **cero** | NO | **NO** |
| 5 | `sincronizar(idPropiedad, …)` | **cero** (sólo declaración y definición) | NO | **NO** |

Los métodos del servicio invocados desde la capa web son exactamente cuatro:
`listarDeEncargo`, `crearEnEncargo`, `actualizar`, `cambiarEstado`. **Ni `crear`
ni `sincronizar` están expuestos.**

**No hubo `STOP`**: ningún consumidor legítimo apareció.

### 1.1 · La caza de la sexta puerta

El encargo pedía asumir que podía haber otra. Se buscó **también por escrituras
directas al repositorio**, no sólo por llamadas al servicio:

- `publicaciones.save(...)` en `src/main`: **seis sitios**. Cinco en
  `PublicacionServiceImpl` (`crearEnEncargo`, `crear`, `actualizar`,
  `cambiarEstado`, `sincronizar`) y **uno en `ContratoServiceImpl:798`**.
- **`ContratoServiceImpl` NO es una puerta**: recorre las publicaciones que ya
  existen y las **cierra** al firmar el contrato (`setEstado(ESTADO_CERRADO)`).
  No instancia ninguna.
- `new Publicacion()` en `src/main`: **dos sitios**, ambos en
  `PublicacionServiceImpl` — `construir` (vía 1 y 4) y el bloque de creación de
  `sincronizar` (vía 5).
- **`actualizar` se leyó entera** para descartarla como sexta puerta: escribe
  canal, url, importe, moneda, título, código de origen y versión — **nunca
  `estado`**. No puede publicar un borrador.

**No hay sexta puerta.** Tras el corte queda **un solo** `new Publicacion()`, en
`construir`, alcanzable únicamente desde `crearEnEncargo` — **y cero métodos
privados sin llamante en el impl**, que es la cifra que cierra el punto ciego
declarado por `PuertasDePublicacionTest`.

Se añaden dos comprobaciones más, que no había hecho y sí hizo el Auditor:
**`PublicacionRepository` no tiene ni un `@Modifying`** —sus nueve consultas son
`SELECT`, así que no hay escritura por JPQL— y los `setEstado` del impl están
todos en los métodos ya inventariados.

---

## 2. La decisión: se retiran las dos

Cero consumidores de producción, ninguna expuesta. **Retirar, no delegar**: una
vía que delega **sigue existiendo** y puede desincronizarse en el próximo cambio;
una vía que no existe no puede eludir nada. El objetivo dice «ningún camino».

Quedan:

| vía | qué hace | guarda |
|---|---|---|
| `crearEnEncargo` | **crea** | `exigirPublicable` |
| `cambiarEstado` | **publica** | `exigirPublicable` en la transición a `PUBLICADO` |
| `actualizar` | edita | no la necesita: **no toca el estado** |

En el hueco que dejaron, la interfaz lleva escrito **por qué no hay más puertas**
y por qué se retiraron en vez de delegar.

---

## 3. Sus pruebas no desaparecieron

### 3.1 · La regla de «sin encargo no se publica» pasó a ser estructural

`PropiedadSinEncargoIntegrationTest` la probaba llamando a
`publicaciones.crear(id, …)` y esperando el rechazo de `exigirAlgunEncargo`.
Retirado el método, **la garantía no se debilitó: se hizo más fuerte** — ya no hay
un método que lo rechace en ejecución, **no hay método al que pedírselo**.

El test se reescribió para afirmar lo que ahora es cierto: **ninguna firma pública
acepta publicar nombrando sólo el inmueble**, y la única que crea exige el
encargo. Se conserva en esa clase —y no sólo en el test de arquitectura— porque lo
que hay que conservar es **la regla**, no el método que la implementaba.

### 3.2 · Los cuatro casos de `sincronizar` se repuntaron

Lo que protegían —deduplicación del hito `P` y tratamiento de la moneda— **sigue
vivo** en `registrarImportePublicado`, que usan `actualizar` y `cambiarEstado`:

| test | antes | ahora |
|---|---|---|
| dedup por escala (`5200` vs `5200.00`) | `sincronizar` | `actualizar` |
| renta nueva deja otro hito | `sincronizar` | `actualizar` |
| cambiar sólo la moneda cuenta como renta nueva | `sincronizar` | `actualizar` |
| un borrador no escribe hito | `sincronizar` (sin publicación previa) | `cambiarEstado` a `BORRADOR` |
| sin encargo resuelto no se inventa la operación | `crear` | `actualizar` sobre un anuncio **sin `idEncargo`** |
| un borrador no deja hito | `crear` | `crearEnEncargo` |

El penúltimo merece nota: `Publicacion.idEncargo` **es anulable** —hay anuncios
anteriores a `V70`—, así que esa rama **sigue siendo alcanzable en producción** y
el test la alcanza igual, por otra puerta.

Se corrigieron además **dos comentarios que habían quedado falsos** al citar a
`sincronizar` como el llamador que obliga a deduplicar.

---

## 4. El javadoc que mentía

`PublicacionServiceImpl` afirmaba, en `registrarImportePublicado`:

> *«…porque `LocalComercialServiceImpl` llama a `sincronizar` en TODA
> actualizacion, cambie o no el precio.»*

**Era falso.** `LocalComercialServiceImpl` **sí inyecta** `PublicacionService`
—campo y constructor, y por eso el barrido la ve— **pero no llama a ninguno de los
dos métodos**. Corregido: quien obliga a deduplicar hoy es `actualizar`, que pasa
por ahí en cada guardado. Se deja escrito qué decía antes y por qué dejó de ser
cierto.

También se corrigió el javadoc de cabecera de la interfaz, que enlazaba
`{@link #sincronizar}` como uno de los métodos «que preguntan por inmueble»: ese
enlace apuntaba a un método retirado.

---

## 5. Las pruebas del corte

### 5.1 · Ningún camino elude el bloqueo — §5

`CatalogoQueHablaIntegrationTest.ningunCaminoEludeElBloqueo`, sobre un `LOCAL`
bloqueado por catálogo:

| camino | resultado |
|---|---|
| `crearEnEncargo` con estado `PUBLICADO` | **rechazado** |
| `crearEnEncargo` con estado `BORRADOR` | **rechazado** |
| `cambiarEstado` a `PUBLICADO` sobre un anuncio que nació completo | **rechazado** |
| `actualizar` pidiendo estado `PUBLICADO` | **no publica**: sigue en `BORRADOR` |
| completar `tipo_acceso` → `cambiarEstado` a `PUBLICADO` | **funciona** |

> **Hallazgo, y es más estricto de lo que yo suponía.** Escribí primero el test
> dando por hecho que un **borrador** de una propiedad bloqueada sí se podría
> guardar. **Falla**: `crearEnEncargo` llama a `exigirPublicable`
> **sin mirar el estado pedido**. Así que de una propiedad bloqueada **no entra ni
> un borrador**, y la única forma de tener un anuncio sobre una es que naciera
> cuando la ficha estaba completa. El test se reescribió para probar **lo que es
> cierto**, no lo que yo había supuesto — y ese escenario es justamente el que
> permite ejercitar `cambiarEstado`.

### 5.2 · El test de arquitectura, y lo que NO cubre

`PuertasDePublicacionTest`, cuatro comprobaciones:

1. **La superficie de `PublicacionService` es exactamente la inventariada** —
   con dos listas explícitas: las que **crean o publican** (`crearEnEncargo`,
   `cambiarEstado`) y las que no. Un método nuevo rompe hasta que alguien decida
   en cuál va.
2. **Todo método que llama a `construir` llama antes a `exigirPublicable`** —
   `construir` es donde nace una `Publicacion`, y era justo lo que las dos vías
   retiradas hacían sin validar.
3. **Sólo `PublicacionServiceImpl` instancia una `Publicacion`** — para que nadie
   la guarde por el repositorio saltándose la interfaz. `ContratoServiceImpl`
   escribe publicaciones pero **no crea ninguna**, y por eso no aparece.
4. **`crear` y `sincronizar` no vuelven**, ni con otra firma.

**Lo que no cubre, dicho en vez de fingido:** un método privado nuevo dentro del
propio `PublicacionServiceImpl` que hiciera `new Publicacion()` y `save` a mano,
invocado desde un método público ya cubierto, **no lo vería**. La comprobación 1
lo caza sólo si se expone en la interfaz — que es lo que tendría que pasar para
que sirviera de algo. Queda escrito en el javadoc de la clase.

**Comprobado que muerde**: se reintrodujo `crear(idPropiedad, …)` sin guarda, en
interfaz e implementación. **Tres de las cuatro pruebas fallaron por separado**:

```
laSuperficieEsLaInventariada  → apareció `crear` en la superficie
lasDosViasRetiradasNoVuelven  → volvió `crear`
construirSoloDesdeMetodosQueValidan → [crear] crea sin pasar por exigirPublicable
```

Inyección revertida desde copia previa y verificada a cero.

> **Un tropiezo propio, anotado.** Tras revertir, la primera reejecución seguía en
> rojo: el módulo `controllocal-app` compila contra el **jar instalado** de
> `controllocal-service`, que aún llevaba la inyección. No era un fallo del test
> sino del orden de construcción — el gotcha que `CLAUDE.md` ya documenta. Se
> reinstaló el módulo y quedó verde.

---

## 6. La corrida de cierre

**Una sola** con `TEST_DB_URL`, **sin nada más compilando**.

| Paso | Resultado |
|---|---|
| **1.** Requisitos | OK |
| **2.** Gate del modelo universal | **69 en verde, 0 en rojo, 69 total** · `ROLLBACK` |
| **3.** Reactor completo contra PostgreSQL real | **BUILD SUCCESS**, los seis módulos |
| **4.** Los 20 de integración se **ejecutaron** | **20 de 20** |
| **5.** Suites E2E | **5 de 5** |
| | **`== CIERRE VERDE ==`**, salida **0** |

```
dominio        Tests run: 720, Failures: 0, Errors: 0, Skipped: 0
persistencia   Tests run:  48, Failures: 0, Errors: 0, Skipped: 0
aplicacion     Tests run: 405, Failures: 0, Errors: 0, Skipped: 0
```

`aplicacion` pasa de 400 a **405**: las 4 del test de arquitectura nuevo y la de
«ningún camino elude el bloqueo». `Skipped: 0` en los tres.

**419 comprobaciones `OK` y cero `FALLA`** en las cinco suites. Ninguna se tocó:
el único endpoint de creación sigue siendo
`POST /encargos/{idEncargo}/publicaciones`.

### Angular — no se tocó, y se demuestra

```
ng test                            671 SUCCESS   (igual que antes)
ng build --configuration production   NG_BUILD_EXIT=0
```

**Cero ficheros de `frontend-angular/` modificados.** Se corrieron igualmente
porque el encargo lo pide: retirar métodos del servicio no podía romper el SPA
—ninguno estaba expuesto— y aquí queda demostrado en vez de supuesto.

---

## 7. Lo que no se tocó

- **Ninguna migración.** `V81` y `V82` intactas.
- **Ningún endpoint**, ninguna fila nueva en `matriz-operacion-rol.md`: la
  superficie REST no cambia, porque las dos vías retiradas **no estaban
  expuestas**.
- **No se copió `exigirPublicable`** ni se creó otra consulta; no se *hardcodeó*
  `ALT`/`PUB` en ninguna capa.
- Ninguna exigencia, ningún *backfill*, el Corte 5 sin abrir.
- `GateDeCierreTest` no necesita entrada: su inventario sólo recorre
  `src/test/java/com/controllocal/integracion`, y el test nuevo vive en
  `arquitectura/` y no lleva `@EnabledIfEnvironmentVariable`.

---

## 8. Tres correcciones tras la auditoría — señalización, no comportamiento

La auditoría aprobó el cierre de las puertas y verificó empíricamente que no hay
sexta. **Rechazó por señalización**, y en los tres casos tenía razón. Ninguna
corrección toca comportamiento.

### D-1 y D-2 · Dos privados quedaron huérfanos, con etiqueta de guarda

Al retirar las dos vías, sus ayudantes se quedaron sin llamante — **medido: una
sola aparición en el fichero, su propia declaración**:

| método | qué guardaba | estado |
|---|---|---|
| `exigirAlgunEncargo` | la ruta por inmueble (`crear`) | **cero llamantes** |
| `encargoUnicoDeAlquiler` | el formulario heredado (`sincronizar`) | **cero llamantes** |

**No era cosmético.** `exigirAlgunEncargo` seguía vivo, lanzando, y su javadoc lo
presentaba como la guarda de una ruta por inmueble que ya no existe. Quien mañana
restaure una vía masiva lo habría encontrado, lo habría llamado y **habría creído
estar cubierto** — cuando sólo comprueba que exista *algún* encargo y **nunca miró
el catálogo**. Es exactamente la confusión que produjo la cuarta puerta.

Y agravaba mi propio punto ciego: `PuertasDePublicacionTest` declara que **un
método privado nuevo no lo vería**, y el corte dejaba **dos privados sin usar
justo ahí**. Retirados los dos, **el impl queda con cero privados sin llamante**.

**Barrido de comprobación**, sobre los 15 privados del impl: sólo esos dos tenían
una única aparición; los otros trece tienen su declaración **y al menos un sitio
de llamada real** —verificado uno a uno, no por conteo—. **No hay un tercero.**
Retirarlos no dejó ningún import huérfano (`Captacion`, `OperacionInmobiliaria`,
`List` y `ReglaNegocioException` siguen usados).

> **El punto ciego sigue siendo una limitación del test, no un problema abierto.**
> Hoy no hay ningún privado sin llamante; lo que el test no puede prometer es que
> no aparezca uno mañana. Se dice así en vez de presentarlo como cerrado.

### D-3 · Mi corrección del javadoc introdujo una afirmación nueva y falsa

Escribí que `LocalComercialServiceImpl` *«inyecta `PublicacionService` pero no lo
llama»*. **Es falso, y lo comprobé:**

```
LocalComercialServiceImpl:173  publicaciones.codigosEstadoPublicacion(ids)
LocalComercialServiceImpl:294  publicaciones.codigosEstadoPublicacion(ids)
LocalComercialServiceImpl:493  publicaciones.codigoEstadoPublicacion(p.getId())
```

**Tres llamadas.** Lo cierto es que inyecta el servicio y **sólo le pregunta el
estado de publicación**; lo que **nunca** llamó fue a `sincronizar`. El encargo
decía «no llama a **ninguno de los dos**» y yo lo comprimí a «no lo llama»,
volviéndolo falso. Un mantenedor que auditara «quién consume `PublicacionService`»
se habría fiado de esa frase y **habría perdido un consumidor**.

Corregido al texto exacto, con las dos operaciones nombradas.

> **Es la segunda vez que me pasa lo mismo**: en el Corte 3 escribí que
> `valor()` era `NULL` en un multivalor. Las dos veces el error estuvo en un
> párrafo **cuya única razón de existir era la exactitud** — una corrección de
> algo que ya era falso. Corregir una afirmación no exime de medir la que se pone
> en su lugar.

### 8.1 · La corrida de cierre de la enmienda (repetida tras D-4)

Repetida entera tras las tres correcciones:

| Paso | Resultado |
|---|---|
| Gate del modelo universal | **69 en verde, 0 en rojo** |
| Reactor completo | **BUILD SUCCESS** — `720 · 48 · 405`, **`Skipped: 0`** |
| Los 20 de integración | **20 de 20 ejecutados** |
| Suites E2E | **5 de 5**, **419 `OK` / 0 `FALLA`** |
| | **`== CIERRE VERDE ==`**, salida **0** |

```
ng test                            671 SUCCESS
ng build --configuration production   NG_BUILD_EXIT=0
```

**405 en `aplicacion`, igual que antes de la enmienda**: retirar dos privados sin
llamante y corregir dos javadoc no mueve ni una prueba — que es exactamente lo que
significa «ninguna corrección toca comportamiento».

---

## 9. D-4 · La cuarta afirmación falsa, y el barrido que la generaliza

### 9.1 · La línea que el corte volvió falsa sin tocarla

`PublicacionServiceImpl`, javadoc de `encargoDe`:

```java
* <p>No falla cuando no hay encargo: {@code crear(idPropiedad, ...)} existe
* y produce publicaciones sueltas.
```

**Es byte-idéntica a `7655295`**, donde era **cierta**. Al retirar
`crear(idPropiedad, …)` la volví falsa **sin editarla**: no aparece en el diff, y
por eso ni yo ni la primera pasada del auditor la vimos.

**La tolerancia a `null` sigue justificada, pero por otra razón**, medida:

```
publicacion.id_captacion   is_nullable = YES
0 sin encargo de 12 publicaciones
```

La columna **es anulable** —hay anuncios anteriores a `V70` cuya operación no se
sabe— y `cambiarEstado` pregunta por el encargo de **cualquier** anuncio. Hoy
ninguna fila la usa, **pero el esquema lo permite y basta con eso**. La razón
verdadera **nunca fue** la vía retirada. Corregido, dejando escrito qué decía
antes y por qué dejó de ser cierto.

### 9.2 · La lección, que vale más que el renglón

> **Al retirar un símbolo, el barrido correcto no es «¿quedan métodos sin
> llamante?» sino «¿qué javadocs y comentarios nombran el símbolo que acabo de
> borrar?».**

Lo primero encuentra código muerto; lo segundo encuentra **documentación que se
vuelve mentira sin aparecer en el diff**. Las cuatro afirmaciones falsas de este
corte son de la segunda clase.

### 9.3 · El barrido, con sus cifras

`rg` sobre **todo el árbol** —`src/main`, `src/test`, `verificacion/`,
`docs/ai/`—, con control positivo previo (`crearEnEncargo`, que sí existe, da
aciertos):

| símbolo retirado | aciertos | corregidos |
|---|---|---|
| `crear(idPropiedad` | **14** | 0 |
| `sincronizar` | **44** | 0 |
| `exigirAlgunEncargo` | **5** | **1** |
| `encargoUnicoDeAlquiler` | **1** | 0 |
| **total** | **64** | **2** |

Los **dos corregidos** son el javadoc de `encargoDe` (§9.1) y una **cita rota en
`pendientes-brox.md`**: había marcado el diagnóstico original como cita pero
**sólo la primera línea llevaba `>`**, así que trece líneas en **presente**
—«tiene esa cuarta puerta sin guardar **en el código**»— quedaban fuera de la
cita y se leían como estado actual. Cerrada la cita, línea a línea.

**Los otros 62 se revisaron uno a uno** y se quedan, cada uno por su razón:

- **En pasado, describiendo la retirada** — la gran mayoría, en el código, los
  tests, la evidencia de este corte, el mapa y `pendientes-brox.md`.
- **El encargo congelado** (`encargo-cerrar-puertas-de-publicacion.md`): describe
  el estado **anterior** al corte, que es su función.
- **`estado-actual-control-local.md`**: lleva banner **`HISTÓRICO — NO GOBIERNA`**
  y habla del `PublicacionBusinessLogic` de la **v1**, borrada el 2026-08-08.
- **`diagnostico-estados-…md:90`**: es **otra palabra** — «al emitir o
  sincronizar recontacto», el verbo castellano aplicado a una **Alerta**, no el
  método. Un acierto del barrido, no del símbolo.
- **Dos evidencias de `2026-08-10`**: son **registros fechados**. Reescribirlas
  falsificaría lo que se midió aquel día, que es justo lo contrario de lo que la
  evidencia existe para hacer.
- **`V70__publicacion_pertenece_al_encargo.sql:42`**: migración **aplicada**
  (`success = t`) y por tanto **inmutable**. Su comentario describe el mundo de
  `V70`, no el de hoy.

### 9.4 · La corrida de cierre, repetida entera tras D-4

| Paso | Resultado |
|---|---|
| Gate del modelo universal | **69 en verde, 0 en rojo** |
| Reactor completo | **BUILD SUCCESS** — `720 · 48 · 405`, **`Skipped: 0`** |
| Los 20 de integración | **20 de 20 ejecutados** |
| Suites E2E | **5 de 5**, **419 `OK` / 0 `FALLA`** |
| | **`== CIERRE VERDE ==`**, salida **0** |

```
ng test                            671 SUCCESS
ng build --configuration production   NG_BUILD_EXIT=0
```

**405 en `aplicacion`, idéntico a las dos corridas anteriores.** Corregir un
javadoc y cerrar una cita no mueve una sola prueba — que es lo que significa que
esto no toca comportamiento.
