# Evidencia — Corte 4 · Comercial (L, O, A) — `V81`

**Fecha:** 2026-08-24
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `4165e54` (encargo congelado; el código es `c083bc0`, Flyway hasta `V80`)
**Encargo que gobierna:** `docs/ai/encargo-corte-4-comercial.md`
**Migración:** `V81__el_activo_comercial_descrito.sql` (única)

---

## 1. Precheck — nada contradijo el encargo

| # | Afirmación | Medición propia | Veredicto |
|---|---|---|---|
| 1 | Flyway aplicado hasta `V80` | cabeza = `80 · la vivienda descrita de verdad · true` | ✅ |
| 2 | `max(orden)` de PROPIEDAD = 550 | **550** → arranque en 560 | ✅ |
| 3 | Catálogo del sistema = 81 (55 PROPIEDAD + 26 ENCARGO) | **55 + 26** | ✅ |
| 4 | ALT 10 · OPC 154 · operación 112 · **cero PUB** | idéntico | ✅ |
| 5 | `se_entrega_implementado` se pacta en **A, L, O** | tres filas, `A/A`, `L/A`, `O/A` | ✅ |
| 6 | Las 39 claves son nuevas | 39 declaradas, 39 distintas, **0 colisiones** | ✅ |
| 7 | `area_minima_arrendable.unidad` = `m2` | **`m2`**, sin acento | ✅ |
| 8 | 26 propiedades, `C=1 D=1 L=21 O=2 T=1` | idéntico | ✅ |
| 9 | **Publicables hoy: 26 de 26, cero bloqueantes** | **0 bloqueantes** | ✅ |
| 10 | `ALT` impide publicar | `AtributoPropiedadRepository:100` → `a.exigencia in ('ALT', 'PUB')` | ✅ |
| 11 | `ALT` **sí** informa | `clavesObligatoriasQueFaltan` filtra `exigencia = 'ALT'`, y alimenta `atributosQueFaltan` | ✅ |

**No hubo `STOP`.**

---

## 2. Qué se sembró, contado contra la predicción independiente del Auditor

| | antes | predicho | **medido después** |
|---|---|---|---|
| `catalogo_atributo del_sistema` | 81 | 120 | **120** ✅ |
| …PROPIEDAD / ENCARGO | 55 / 26 | 94 / 26 | **94 / 26** ✅ |
| `catalogo_atributo_tipo` | 164 | 235 (+71) | **235** ✅ |
| `catalogo_atributo_operacion` | 112 | 112 (intacta) | **112** ✅ |
| `catalogo_atributo_opcion` | 84 | 167 (+83) | **167** ✅ |
| `atributo_propiedad` | 74 | 74 | **74** ✅ |
| `ALT` / `requerido = true` | 10 / 10 | 11 / 11 | **11 / 11** ✅ |
| `OPC` (tipo) | 154 | 224 | **224** ✅ |
| **`PUB`** | 0 | **0** | **0** ✅ |
| `max(orden)` PROPIEDAD | 550 | 940 | **940** ✅ |
| claves con `unidad = 'm2'` | 1 | **0** | **0** ✅ |

`11 + 224 = 235` cuadra. Aplicabilidad por tipo: **A 28 · L 16 · O 19 · C 2 · D 3 · T 3 = 71**, exactamente lo predicho.

La propia migración lo dice al aplicarse:

```
V81: 39 claves comerciales, 71 filas de aplicabilidad (1 ALT: tipo_acceso/L),
     83 opciones en 18 vocabularios, 0 en PUB, 0 valores materializados.
```

---

## 3. La decisión del corte: 26 publicables pasan a 5

Medido contra `controllocal_dev` **después** de aplicar `V81`:

```
 total | bloqueadas | publicables | claves_bloqueantes
-------+------------+-------------+--------------------
    26 |         21 |           5 | tipo_acceso
```

**Las 21 están bloqueadas por `tipo_acceso` y sólo por `tipo_acceso`.** No es un
supuesto: la consulta agrega `string_agg(distinct clave)` sobre todas las claves
bloqueantes de todas las propiedades, y devuelve **una sola**. Ninguna de las
otras 38 claves nuevas aparece.

### 3.1 · Funciona de punta a punta, no sólo en la tabla

Intento real de publicar `LOC-D012` (encargo vivo de alquiler, id 13):

```
POST /encargos/13/publicaciones
HTTP=400
{"error":"Todavia no se puede publicar: de la ficha del inmueble falta Tipo de
          acceso. Se puede registrar sin ese dato, pero no anunciarlo."}

publicaciones creadas para ese encargo: 0
```

Y la ficha **informa**, que es lo que distingue esta `ALT` de una `PUB`:

```
GET /propiedades/6
codigo: LOC-D012 | tipo: LOCAL
atributosQueFaltan: [{"clave":"tipo_acceso","rotulo":"Tipo de acceso"}]
```

El bloqueo viaja con la instrucción de cómo quitarlo. Una `PUB` sólo habría
prohibido, en silencio.

---

## 4. LA LISTA DE TRABAJO DE CAMPO — los 21 locales que salen del mercado

Un corte que saca inventario del mercado sin decir cuál, lo saca a ciegas. Éstos
son, con su código. **Se desbloquean uno a uno, visitándolos y registrando
`tipo_acceso`.** Ninguno se rellenó desde la migración.

### 4.1 · Urgentes — **7 con encargo vivo**

Dejan de anunciarse **teniendo mandato del propietario**. Son los que cuestan
dinero cada día que pasan sin visitar.

| Código | Distrito | m² | Encargo vivo |
|---|---|---|---|
| `LOC-D012` | Surquillo | 62.50 | Alquiler |
| `LOC-D018` | Lima Cercado | 48.00 | Alquiler |
| `LOC-D024` | Lince | 190.00 | Alquiler |
| `LOC-D025` | Santiago de Surco | 88.00 | Alquiler |
| `LOC-D027` | Miraflores | 105.00 | Alquiler |
| `PROP-0022` | San Miguel | 160.00 | **Alquiler + Venta** |
| `PROP-0026` | Miraflores | 140.00 | Alquiler |

### 4.2 · Los otros 14, sin encargo vivo

| Código | Distrito | m² |
|---|---|---|
| `LOC-0001` | Miraflores | 120.00 |
| `LOC-0002` | Lima | 85.00 |
| `LOC-D001` | Miraflores | 120.50 |
| `LOC-D010` | Miraflores | 85.00 |
| `LOC-D011` | Lima Cercado | 140.00 |
| `LOC-D013` | San Miguel | 210.00 |
| `LOC-D015` | Santiago de Surco | 175.00 |
| `LOC-D016` | Surquillo | 58.00 |
| `LOC-D017` | La Molina | 320.00 |
| `LOC-D020` | Los Olivos | 110.00 |
| `LOC-D021` | Barranco | 72.00 |
| `LOC-D022` | Jesús María | 155.00 |
| `LOC-D023` | Miraflores | 66.00 |
| `LOC-D026` | Callao | 260.00 |

**Las 5 que siguen publicables**: la casa, el departamento, las dos oficinas y el
terreno. Ninguna es un local.

---

## 5. Que las guardas de `V81` **muerden**

Tres roturas deliberadas, dentro de transacciones que terminan en `ROLLBACK`:

| Rotura simulada | Resultado |
|---|---|
| Alguien «arregla» las suites bajando `tipo_acceso` a `OPC` | ❌ **guarda 6.6 aborta**: `filas ALT = 0` |
| El código `24_7` en lugar de `H24_7` (empieza por dígito) | ❌ **guarda 6.5 aborta**: `horario_acceso_edificio/24_7` |
| `nivel_implementacion` sin cubrir `A` | ❌ **guarda 6.10 aborta**: `no llega a A` |

La primera es la que importa: **la exigencia no se puede relajar en silencio**.
Si un corte futuro baja `tipo_acceso` a `OPC` para que algo pase, la migración
que lo haga tendrá que quitar además la guarda, y eso se ve en la revisión.

La tercera es el guard 2.2 de `V78`, que **ignora `tipo_operacion`** y compara
sólo el conjunto de `tipo_propiedad`: por eso `nivel_implementacion` tuvo que
cubrir **A, L y O**, y no sólo donde el plan sugería.

---

## 6. Las 39 llegan por el cable **sin tocar Angular**

`GET /captura/definicion?intencion=REGISTRAR_PROPIEDAD&tipoPropiedad=…`

| Tipo | preguntas `delTipo` | de ellas, nuevas de `V81` |
|---|---|---|
| `LOCAL` | 42 | **16** (una de ellas `ALT`) |
| `OFICINA` | 49 | **19** |
| `ALMACEN` | 50 | **28** |

`tipo_acceso` llega como `SELECTOR` con sus 7 opciones y `exigencia: ALT`; las
otras 38, `OPC`. Los controles derivados por el motor son los correctos.

**Ni un fichero de `frontend-angular/` cambió en este corte.** Las dos puertas
—alta y editor— llaman al mismo `CapturaService.definicion(...)`, así que reciben
la misma definición del Core por construcción (D-A-1).

Medido en el cable, `tipo_acceso` sale así, y **sólo en `LOCAL`**:

```
LOCAL:   delTipo=42 | nuevas de V81=16 | ALT=["metraje_total","tipo_acceso"]
         tipo_acceso -> control=SELECTOR exigencia=ALT opciones=7
OFICINA: delTipo=49 | nuevas de V81=19 | ALT=["metraje_total"]
ALMACEN: delTipo=50 | nuevas de V81=28 | ALT=["metraje_total"]
```

---

## 7. La deuda de verificación del Corte 3, pagada — y era mayor de lo que decía

El encargo §7 pedía extender `ConservacionDeLaEdicionIntegrationTest` a L, O y A.
Medido, el agujero era más ancho: sus casos por tipo eran **listas escritas a
mano** y llevaban congelado el catálogo de anteayer.

| tipo | admitía | el gate cargaba | sin probar |
|---|---|---|---|
| **L** | 24 | 13 | **11** |
| **O** | 28 | 10 | **18** |
| **A** | 22 | 12 | **10** |

De esas 39, **`V80` había sembrado 7 para el local, 13 para la oficina y 4 para el
almacén**, y el Corte 3 —que extendió sólo D y C— no las metió en ningún caso.
`estado_conservacion`, `vigilancia`, `etapa_entrega`, `ascensores`… existían para
un local y **su ida y vuelta no la tocaba nadie**, sin que nada se pusiera rojo.

**Los seis casos se derivaron del catálogo**, no a mano, y ahora llevan **todo lo
que su tipo admite**:

```
L 40 · O 47 · A 50 · D 46 · C 35 · T 14      (X = 3, ya estaba completo)
```

Se completaron también **D, C y T**, que el encargo no exigía: `V81` les añade
`gas`, `agua_caliente` y `respaldo_electrico` (D, C) y tres claves más a T, y
dejarlos cortos habría abierto un hueco nuevo **de este mismo corte**.

### 7.1 · Y la promesa dejó de depender de que alguien se acuerde

El javadoc de `CasoDeTipo` prometía «la carga más ancha que el catálogo le permite
**hoy**». Era una promesa que nadie comprobaba, y por eso llevaba dos cortes
siendo falsa. Ahora hay un test que **la comprueba contra el catálogo real**:

```java
@DisplayName("cada caso lleva TODAS las claves que su tipo admite en el catalogo")
void cadaCasoLlevaTodoLoQueSuTipoAdmite()
```

**Comprobado que muerde**, quitando `tipo_acceso` del caso `LOCAL`:

```
AssertionFailedError: Hay claves del catalogo que ningun caso escribe, asi que su
ida y vuelta no se prueba. ==> expected: <[]> but was:
<[LOCAL no ejercita 1 de 40: tipo_acceso]>
```

Dice **qué tipo, cuántas y cuáles**. Inyección revertida; con los casos completos,
**48 de 48 en verde**.

Es un fichero de test que ya existía: **no toca el inventario de las 20 clases de
integración**, así que `GateDeCierreTest` y `Verificar-Cierre.ps1` siguen
coincidiendo sin tocarlos.

---

## 8. Las suites E2E: se arregló el *fixture*, no la regla

`tipo_acceso` ALT en `L` rompe toda suite que publique un local. La corrección
está en **un solo sitio**, `backend-spring/verificacion/lib-alta-inmueble.ps1`,
porque sus dos helpers (`NuevoInmuebleConEncargo`, `NuevoInmuebleSinEncargo`) son
la única puerta por la que los guiones dan de alta un inmueble — y los dos crean
**LOCAL por defecto**.

Registran ahora `tipo_acceso` (`A_PIE_DE_CALLE` por defecto, parametrizable), y
**sólo cuando el tipo es LOCAL**, respetando lo que traiga el guion en
`-Atributos`.

**Lo que NO se hizo, y es la parte que importa:**

- **No se bajó `tipo_acceso` a `OPC`** porque las suites se pusieran rojas. Eso
  relaja la regla que decidió el titular; el `.ps1` lo dice en su comentario.
- **No se cambió el tipo del inmueble a `OFICINA`** para esquivar el ALT. Eso
  cambia lo que la suite dice estar probando, y es la trampa que el Auditor tenía
  fichada.
- **No se sembró el valor en las 21 propiedades reales.** El fixture crea
  inmuebles nuevos; la cartera real sigue con el dato FALTANTE.

`e2e-editor-universal.ps1` no necesitó nada: construye el alta **desde la
definición del Core**, así que recoge `tipo_acceso` como `SELECTOR` solo.

---

## 9. HALLAZGO — `ALT` no sólo impide publicar: **impide registrar**

Lo descubrió la corrida de cierre, no el precheck, y **CONTROL debe conocerlo**
porque el encargo describe el efecto sólo como «impide publicar».

Medido:

```
PropiedadUniversalServiceImpl.registrar:231  ->  exigirObligatorios(...)
ReglaNegocioException: Faltan atributos obligatorios de LOCAL: tipo_acceso.
```

Hay **dos** puertas y `ALT` está en las dos:

| Puerta | Consulta | Qué exigencias mira |
|---|---|---|
| **Alta** (`registrar`) | `clavesObligatoriasQueFaltan` | **`ALT`** |
| **Publicar** (`exigirPublicable`) | `clavesQueImpidenPublicar` | **`ALT` + `PUB`** |

`ALT` significa literalmente **obligatorio en el ALTA**. El encargo midió la
segunda puerta —26 publicables → 5— y no mencionó la primera.

### 9.1 · Por qué esto NO invalida la decisión, y por qué aun así se reporta

**No es un comportamiento que `V81` invente.** Ya era así para las diez `ALT`
anteriores: hoy no se puede registrar un `DEPARTAMENTO` sin `dormitorios`, ni un
`TERRENO` sin `zonificacion`. El propio código lo tiene escrito desde antes
(`PropiedadUniversalIntegrationTest:464`): *«El alta no deja registrar sin lo
obligatorio»*.

Y **es coherente con la razón que dio el titular**: eligió `ALT` porque
`tipo_acceso` *«es el único dato que el agente tiene delante cuando capta: está
de pie en el local»*. Exigirlo en el alta es exactamente esa frase.

**Ninguna propiedad existente se ve afectada**: `exigirObligatorios` sólo corre en
`registrar`, no en `editar`. Los 21 locales siguen registrados; sólo no se
publican.

### 9.2 · Lo que sí cambia, y es una decisión de producto que no me corresponde

**A partir de `V81` no se puede dar de alta un local sin haberlo visto.** Un local
avistado desde la calle o reportado por teléfono no entra en el registro maestro.

Eso roza —no contradice, pero roza— lo que `V75` y `V76` establecieron a
propósito: que *registrar no es encargar* y que **BROX conoce legítimamente
inmuebles que no gestiona**. Aquella tanda quitó del alta la obligación de tener
titular precisamente para no obligar a inventarlo.

**No he relajado nada.** `tipo_acceso` sigue `ALT`, no toqué `exigirObligatorios`
y no sembré el dato. Lo dejo escrito para que CONTROL decida si el titular quiere
esto sabiéndolo, ahora que está medido. La alternativa técnica existe y no es
rebajar la exigencia —sería separar «obligatorio para publicar» de «obligatorio
para registrar», que hoy son la misma columna— y **eso es un corte propio**.

### 9.3 · Lo que costó, y qué se corrigió

**30 errores en 2 clases de integración**, todos con el mismo mensaje. Todas eran
altas de `LOCAL` en *fixtures* que no traían el dato. Corregido registrándolo:

- `PropiedadUniversalIntegrationTest`: en el helper `comando(...)` —que sólo lo
  añade **si el tipo es LOCAL y el caso no lo trae ya**— y en los tres sitios que
  construyen su `ComandoRegistro` a mano.
- `PropiedadSinEncargoIntegrationTest`: en su `alta(...)`, que siempre es LOCAL.

> **Un error mío, anotado porque enseña algo.** Al parchear por patrón de texto,
> mi primer intento cayó sobre un comando de **OFICINA** y le añadió
> `tipo_acceso` —clave que ese tipo no admite—, convirtiendo un fallo en otro
> distinto. `String.replace` sustituye la **primera** coincidencia, no la que uno
> tiene en la cabeza. Se revirtió y se parcheó **por línea exacta**, tras
> comprobar el tipo de cada sitio contra el fichero. Verificado después: las seis
> apariciones de `tipo_acceso` en esa clase están en contexto `LOCAL`.

Los tests que dependen de que falte un obligatorio (`atributoObligatorioAusente`
en DEPARTAMENTO, el de TERRENO) **no se tocaron y siguen probando lo mismo**: el
helper sólo actúa sobre LOCAL.

---

## 10. La corrida de cierre

**Una sola** con `TEST_DB_URL`, **sin nada más compilando**. (Hubo una corrida
previa que **abortó**, y es la que descubrió §9: 30 errores de *fixture* por
`ALT` en el alta. Se corrigieron los *fixtures* y se repitió entera.)

| Paso | Resultado |
|---|---|
| **1.** Requisitos | OK |
| **2.** Gate del modelo universal, **con las 39 sembradas** | **69 en verde, 0 en rojo, 69 total** · `ROLLBACK` |
| **3.** Reactor completo contra PostgreSQL real | **BUILD SUCCESS** |
| **4.** Los 20 de integración se **ejecutaron** | **20 de 20** |
| **5.** Suites E2E | **5 de 5** |
| | **`== CIERRE VERDE ==`**, salida **0** |

```
dominio        Tests run: 720, Failures: 0, Errors: 0, Skipped: 0
persistencia   Tests run:  48, Failures: 0, Errors: 0, Skipped: 0
aplicacion     Tests run: 394, Failures: 0, Errors: 0, Skipped: 0
```

`Skipped: 0` en los tres es lo que demuestra que los de integración **corrieron**
en vez de terminar verdes por no ejecutarse. `aplicacion` pasa de 393 a **394**:
el test nuevo de completitud del §7.1.

**419 comprobaciones `OK` y cero `FALLA`** en las cinco suites
(`comision-movimientos`, `disponibilidad-contrato`, `f4-solicitud` 125/0,
`estabilizacion-alquiler`, `editor-universal` 147/0). Las tres que publican un
local pasan **con el fixture corregido**, no con la regla relajada.

### Build de producción de Angular

```
Output location: D:\init\ControlLocal\frontend-angular\dist\controllocal-web
NG_BUILD_EXIT=0
```

Compila. Los avisos de `anyComponentStyle` son los **preexistentes** (aviso a
4 kB, error a 16 kB): **ninguno lo introduce este corte, porque no se tocó ni un
fichero de `frontend-angular/`.**

---

## 11. Lo que este corte deja abierto, a propósito y por escrito

| | |
|---|---|
| **Si `tipo_acceso` debe bloquear también el ALTA** | §9. Medido y reportado, **no decidido por mí**. Separar «obligatorio para publicar» de «obligatorio para registrar» sería un corte propio; rebajar la exigencia, no |
| **Los 21 locales fuera del mercado** | §4. Se desbloquean visitándolos. **7 son urgentes**: tienen encargo vivo |
| La promoción `OPC → PUB` | Las catorce que propone la auditoría siguen siendo propuesta. El catálogo sigue con **cero `PUB`** |
| `apto_licencia_funcionamiento` vs `certificado_itse` | Conviven. La retirada necesita migración de datos y es un corte propio |
| `agua_desague`, `energia_electrica`, la retirada de `servicios_disponibles` | Corte 5, con la guarda «ninguna LISTA sin vocabulario» extendida a PROPIEDAD |
| `estado_ocupacion` | Corte 5, **y con un error de plan ya medido**: su condición `entrega_desocupado` está en los **siete** tipos y §3.8 lo planea para T,C. Sembrado así, la migración lanza |
| `lote_minimo_normativo` (T) · `unidad_relacionada` (Corte 6) | — |
| `familia` — el formulario pasa de 55 a **94** campos | Decisión de presentación; va con el corte del SPA. **Registrado, no silenciado** |
| Las conversiones de tipo | `pendientes-brox.md` §2.2 |
| El tipo `X` (OTRO) | Ninguna de las 39 lo incluye. Sigue sin auditar |
