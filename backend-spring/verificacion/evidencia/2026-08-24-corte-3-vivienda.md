# Evidencia — Corte 3 · Vivienda (D, C) — `V80`

**Fecha:** 2026-08-24
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA del código:** `099a723` (V79)
**Encargo que gobierna:** `docs/ai/encargo-corte-3-vivienda-reconstruido.md`
**Migración:** `V80__la_vivienda_descrita_de_verdad.sql` (única)
**Commit previo del corte:** `3.a` (`e0ece07` + enmienda `e8cfaa4`), sin migración

---

## 1. Precheck — todo lo que CONTROL midió, confirmado antes de tocar nada

| # | Afirmación | Medición propia | Veredicto |
|---|---|---|---|
| 1 | Flyway aplicado hasta `V79` | cabeza = `79 · la identidad registral de la propiedad · success=t` | ✅ |
| 2 | `max(orden)` de PROPIEDAD = 250 | **250** | ✅ |
| 3 | Catálogo del sistema = 51 (25 PROPIEDAD + 26 ENCARGO) | **25 + 26 = 51** | ✅ |
| 4 | Cero `PUB` en todo el sistema | tipo: ALT **10**, OPC **86**; operación: OPC **112**. **Cero PUB** | ✅ |
| 5 | `mascotas_aceptadas` aplica a **C y D** | `C/A/OPC` y `D/A/OPC` | ✅ |
| 6 | El corpus real son 26 propiedades | **26** | ✅ |
| 7 | `Verificar-Cierre.ps1` no menciona el gate `.sql` | `grep -c` → **0** | ✅ |
| 8 | El censo `M2` del gate está rojo en `BASE_SHA` | corrida completa: 68 comprobaciones, **67 verdes, 1 roja (la 16)** | ✅ |

**Ninguna medición contradijo el encargo.** No hubo `STOP`.

Además, comprobado antes de escribir el `INSERT`: **ninguna de las 30 claves
nuevas colisiona** con las 51 existentes (`uq_catalogo_atributo_clave` es única
sobre `COALESCE(organizacion_id,0), clave`, es decir **global** para las claves
del sistema, independientemente del sujeto).

---

## 2. Qué se sembró

**Sujeto `PROPIEDAD`, `destino = 'ATRIBUTO'`, `campo_estructural = NULL`,
`del_sistema = true`, `organizacion_id = NULL`, `aplica_todos = false`,
`familia = NULL`, `exigencia = 'OPC'`, `requerido = false`.** Sin excepción.

`orden` de **260 a 550**, de diez en diez, continuando el 250 de
`cargas_gravamenes` (V79). Los huecos de diez existen para que un corte
posterior pueda intercalar sin renumerar.

| Bloque | Claves |
|---|---|
| §3.3 · estado del activo | `estado_conservacion`, `etapa_entrega` |
| §3.4 · edificio y servicios comunes | `ascensores`, `vigilancia`, `areas_comunes`, `unidades_por_piso`, `en_condominio`, `restriccion_reglamento_interno`, `accesibilidad_movilidad_reducida` |
| §3.6 · distribución interior | `tipologia`, `niveles_internos`, `medios_banos`, `cuarto_servicio`, `bano_servicio`, `tipo_cocina`, `lavanderia`, `estudio`, `vista`, `terraza`, `area_terraza`, `balcon`, `jardin`, `patio`, `area_jardin_patio`, `piscina`, `depositos`, `deposito_area`, `tipo_estacionamiento`, `mascotas_reglamento` |
| §3.8 por redacción, vivienda por pertenencia | `torre_bloque` |

**El resultado, contado por la propia migración:**

```
V80: 30 claves de vivienda, 68 filas de aplicabilidad, 49 opciones en 9
     vocabularios, 0 en PUB, 0 valores materializados.
```

Y contra el catálogo después de aplicar:

| | antes | después |
|---|---|---|
| catálogo del sistema, PROPIEDAD | 25 | **55** |
| catálogo del sistema, ENCARGO | 26 | 26 |
| `catalogo_atributo_tipo` OPC | 86 | **154** (+68) |
| `catalogo_atributo_tipo` **ALT** | **10** | **10** — *sin mover* |
| `catalogo_atributo_operacion` | 112 OPC | 112 OPC — *sin tocar* |
| filas **PUB**, en las dos tablas | **0** | **0** |
| `max(orden)` PROPIEDAD | 250 | 550 |

Las diez filas `ALT` son **exactamente las mismas de antes** (`metraje_total` ×7,
`dormitorios` ×2, `zonificacion`/T). El corte no cambió la exigencia de ninguna
clave existente.

---

## 3. Las dos correcciones que la medición impuso al plan

### 3.1 · `mascotas_reglamento` nace en **C y D**, no sólo en D

La auditoría §3.6 dice `D`. Medido contra `controllocal_dev`:

```
mascotas_aceptadas  BOOLEANO  sujeto=ENCARGO
  catalogo_atributo_operacion:  C / A / OPC
                                D / A / OPC
```

El guard 2.2 de `V78` exige que **un hecho no llegue menos lejos que su
condición**. Con `D` solo, `V80` habría fallado en su propia guarda — y **se
comprobó simulándolo**, no se supuso:

```
BEGIN;  <V80 completa>
DELETE FROM catalogo_atributo_tipo ... WHERE clave='mascotas_reglamento' AND tipo_propiedad='C';
ERROR:  V80: mascotas_reglamento no llega a C y su condicion mascotas_aceptadas si.
        Ahi el pacto seria el unico sitio donde cabe el hecho.
ROLLBACK;
```

**Manda la medición: se corrige el documento, nunca el código.** Anotado en
`auditoria-profundidad-inmobiliaria.md` §3.6, §6 y en la tabla de pares.

### 3.2 · `torre_bloque` se ejecuta aquí, no en el Corte 5

Está redactada en §3.8 (*Terreno y parámetros urbanísticos*), pero **su
`aplica_a` es `D`** y su justificación es de vivienda («el 501 existe en la Torre
A y en la B»). Arrastre de redacción, no pertenencia. **Un corte se define por
tipo, no por número de sección**: llevarla al 5 sembraría una clave de
departamentos en la tanda del terreno. Anotado en §3.8 y §6.

---

## 4. Que las guardas de `V80` **muerden**

Una migración cuyas guardas nunca se han visto fallar no se sabe si guarda algo.
Las tres que deciden el corte se rompieron a propósito dentro de transacciones
que terminan en `ROLLBACK`:

| Rotura simulada | Resultado |
|---|---|
| `mascotas_reglamento` sembrada sólo en `D` | ❌ **guarda 6.9 aborta**, nombrando `C` y su condición |
| `tipologia` sembrada como `LISTA` **sin vocabulario** (el fallo que se degrada en silencio a texto libre, como `servicios_disponibles`) | ❌ **guarda 6.4 aborta**: `listas sin vocabulario: tipologia` |
| El array de claves con distinto tamaño | ❌ guarda 6.0 aborta |

La segunda importa especialmente: **una LISTA sin vocabulario no falla en ninguna
parte.** `MotorDeCaptura.controlDe` sólo emite `SELECTOR` si hay opciones y
`exigir_atributo_gobernado()` condiciona la validación de pertenencia a que el
vocabulario exista — así que la clave nacería muda, aceptando texto libre, con
todo en verde. Las 7 `LISTA` y 2 `LISTA_MULTIPLE` nuevas podían repetirlo y la
guarda 6.4 es lo único que lo atrapa.

---

## 5. Las 30 claves llegan por el cable **sin tocar Angular**

`GET /captura/definicion?intencion=REGISTRAR_PROPIEDAD&tipoPropiedad=…`, con la
API reconstruida y reiniciada:

| Tipo | preguntas `delTipo` | de ellas, nuevas de `V80` |
|---|---|---|
| `DEPARTAMENTO` | **45** | **28** (todas menos `en_condominio` y `piscina`) |
| `CASA` | **33** | **16** |

Todas con `exigencia: OPC`, y los vocabularios completos por el cable —
`estado_conservacion` 6, `etapa_entrega` 3, `vigilancia` 6, `areas_comunes` 10,
`tipologia` 6, `tipo_cocina` 4, `lavanderia` 5, `vista` 5,
`tipo_estacionamiento` 4. Los controles derivados por el motor son los correctos:
`SELECTOR` para LISTA, `SELECTOR_MULTIPLE` para LISTA_MULTIPLE, `INTERRUPTOR`
para BOOLEANO.

**Ni un fichero de `frontend-angular/` cambió en este corte.** Que las 30
aparezcan solas es una **prueba** del corte, no un supuesto:
`cl-campo-gobernado` deriva del catálogo y `FronteraDeAutoridadEnElSpaTest`
rompe el build si el SPA escribe una clave o ramifica por tipo.

### Las dos puertas reciben la MISMA definición, y no por convención

D-A-1 se cumple **estructuralmente**: el alta (`propiedad-form.ts`) y el editor
(`propiedad-editor.ts:302`) llaman al mismo `CapturaService.definicion(...)`,
que es el mismo `GET /captura/definicion` con la misma intención por defecto. No
hay dos caminos que puedan divergir. `CatalogoQueHablaIntegrationTest` lo asegura
además por prueba.

---

## 6. La ida y vuelta de las 30 claves, probada — y el gate que **no** la cubría

**Nada la cubría.** Medido:

- `ConservacionDeLaEdicionIntegrationTest` lleva sus casos por tipo **escritos a
  mano**: 10 claves para DEPARTAMENTO y 11 para CASA, todas anteriores a `V79`.
  Las 30 nuevas **no entran solas**.
- `e2e-editor-universal.ps1:162` **excluye `SELECTOR_MULTIPLE`** de su fixture, así
  que `vigilancia` y `areas_comunes` **no las tocaba ninguna suite**.

Extendido el caso de DEPARTAMENTO a **38 claves** y el de CASA a **26**, incluidas
las dos `LISTA_MULTIPLE`. Es el propio contrato de ese record —«la carga de
atributos más ancha que el catálogo le permite **hoy**»— y un caso congelado en el
catálogo de anteayer deja de medir lo que dice medir sin que nada se ponga rojo.
**Es un fichero de test que ya existe: no toca el inventario de las 20 clases de
integración**, así que `GateDeCierreTest` y `Verificar-Cierre.ps1` siguen
coincidiendo sin tocarlos.

Con ello hubo que arreglar **dos defectos reales del propio gate**, que sólo se
ven cuando hay un multivalor en el recorrido:

1. **`comandoEspejo` reenviaba un `LISTA_MULTIPLE` como escalar.** Un multivalor
   vuelve del Core en `valores`, con `valor` a `NULL`. El Core lo rechaza, y con
   razón: *«El atributo "vigilancia" admite varios valores: se edita con la vía
   de multivalor, no con un valor suelto.»* Ahora el espejo distingue.
2. **`retrato` guardaba sólo `valor()`**, que en un `LISTA_MULTIPLE` es `NULL`.
   Perder los tres valores de `vigilancia` habría salido como *«null igual a
   null»* y el gate habría dicho que se conservó. Ahora retrata la lista (y la
   moneda de un IMPORTE, por la misma razón).

**Y se comprobó que el retrato corregido muerde**, inyectando una pérdida
deliberada (`valores().subList(0,1)`) en el espejo:

```
[ERROR] Tests run: 47, Failures: 10
  - atributo.vigilancia:    "CAMARAS_CCTV|CASETA_24H|CONTROL_DE_ACCESO"  ->  "CAMARAS_CCTV"
  - atributo.areas_comunes: "AZOTEA|GIMNASIO|SUM"                        ->  "AZOTEA"
  - atributo.vigilancia:    "CERCO_PERIMETRICO|PORTERO_DIURNO"           ->  "CERCO_PERIMETRICO"
  - atributo.areas_comunes: "JUEGOS_INFANTILES|PARRILLAS|PISCINA"        ->  "JUEGOS_INFANTILES"
```

Inyección revertida; con el espejo correcto, **47 de 47 en verde**.

La aserción de cobertura pasa de cinco familias a **seis**:
`LISTA_MULTIPLE` existía en la PROPIEDAD desde `V79` (`cargas_gravamenes`) y el
recorrido no la tocaba — **la familia más difícil de conservar en una ida y
vuelta (N filas y no una) era justamente la única que este gate no probaba.**

---

## 7. Las 26 propiedades reales siguen siendo publicables — comprobado

`exigirPublicable` bloquea con las claves **ALT y PUB** sin valor
(`AtributosGobernados.faltantesDePropiedadParaPublicar`). Reproducida esa
consulta sobre la cartera real, después de aplicar `V80`:

```sql
-- claves ALT/PUB aplicables al tipo de cada propiedad, sin valor
(0 rows)

propiedades: 26
```

**Cero bloqueantes en las 26.** No podía ser de otro modo —`V80` añadió 68 filas
`OPC` y ni una `ALT` ni `PUB`— pero **se comprobó en lugar de suponerlo**, que es
lo que pedía el encargo.

---

## 8. El gate `.sql`, en verde de verdad y con `V80` sembrada

| Momento | Resultado |
|---|---|
| `BASE_SHA`, gate original | 68 comprobaciones · **67 verdes, 1 roja** · salida **3** |
| Tras `3.a`, antes de `V80` | 69 comprobaciones · **69 verdes** · salida 0 |
| Tras `3.b`, con las 30 sembradas | 69 comprobaciones · **69 verdes** · salida 0 |

El verde final **no es «sin regresiones respecto a `BASE_SHA`»**: es verde de
verdad, sobre un gate que ahora comprueba algo que antes no comprobaba, y que
desde `3.a` **se ejecuta dentro de la corrida de cierre**.

`node docs/ai/modelo/gate-modelo-universal.js`: **165 comprobaciones, todas
verdes.**

---

## 9. La corrida de cierre

**Una sola**, con `TEST_DB_URL`, y **sin nada más compilando en la máquina**.

```
powershell -File verificacion\Verificar-Cierre.ps1
  JAVA_HOME    = Eclipse Adoptium jdk-21.0.11.10-hotspot
  TEST_DB_URL  = jdbc:postgresql://localhost:5433/controllocal_repositorios
```

| Paso | Resultado |
|---|---|
| **1.** Requisitos | OK — `TEST_DB_URL` y `JAVA_HOME` presentes |
| **2.** Gate del modelo universal (nuevo, de `3.a`) | **69 en verde, 0 en rojo, 69 total** · `ROLLBACK` · OK sobre `controllocal_dev` |
| **3.** Reactor completo contra PostgreSQL real | **BUILD SUCCESS**, 8:32 min, los seis módulos |
| **4.** Los 20 de integración se **ejecutaron** | **20 de 20** confirmados en la salida, ninguno saltado |
| **5.** Suites E2E | **5 de 5** |
| | **`== CIERRE VERDE ==`**, salida **0** |

**Tests del reactor, sin un solo salto:**

```
dominio        Tests run: 720, Failures: 0, Errors: 0, Skipped: 0
persistencia   Tests run:  48, Failures: 0, Errors: 0, Skipped: 0
aplicacion     Tests run: 393, Failures: 0, Errors: 0, Skipped: 0
```

`Skipped: 0` en los tres es la mitad que importa: es lo que demuestra que
`CONTROLLOCAL_CIERRE=1` y `TEST_DB_URL` hicieron su trabajo y que los tests de
integración **corrieron**, en vez de terminar en verde por no haberse ejecutado.

**Suites E2E, en orden, todas verdes:** `comision-movimientos`,
`disponibilidad-contrato`, `f4-solicitud` (125 OK / 0 FALLAS),
`estabilizacion-alquiler`, `editor-universal` (147 OK / 0 FALLAS).
**419 comprobaciones `OK` y cero `FALLA` en toda la corrida.**

`editor-universal` recorre los siete tipos con el catálogo **ya ensanchado** —
DEPARTAMENTO pasa de 17 a 45 preguntas y CASA de 17 a 33 — y los siete conservan:

```
== 2.3 DEPARTAMENTO ==   OK   DEPARTAMENTO conserva lo que el contrato pidio
== 2.4 CASA ==           OK   CASA conserva lo que el contrato pidio
```

**Y las dos suites que publican siguen publicando** (`f4-solicitud` y
`estabilizacion-alquiler`), que era el riesgo concreto de sembrar una `PUB` por
descuido: crean un LOCAL con `metraje_total` y `rubro_permitido` y nada más.

### Build de producción de Angular

`ng test` **no** comprueba los presupuestos, así que va aparte y después, con la
máquina libre:

```
npm --prefix frontend-angular run build -- --configuration production
Application bundle generation complete. [51.957 seconds]
NG_BUILD_EXIT=0
```

**Compila.** Los ocho avisos de `anyComponentStyle` son los **preexistentes**
—aviso a 4 kB, error a 16 kB (`decision-presupuesto-de-estilos-de-componente.md`),
y el mayor es `indicadores.scss` con 14.03 kB—: **ninguno lo introduce este
corte, porque no se tocó ni un fichero de `frontend-angular/`.**

---

## 10. Lo que este corte deja abierto, a propósito y por escrito

| | |
|---|---|
| La promoción `OPC → PUB` de las 30 de `V80` y las 6 de `V79` | Corte propio, con su medición sobre corpus real. Hoy el catálogo del sistema sigue con **cero PUB** |
| El estrechamiento de `banos` a ENTERO | **Desbloqueado, no hecho.** `medios_banos` ya existe y la convención ya está publicada en la `ayuda`. Falta clave nueva + migración de datos + retirada de la vieja |
| Las otras tres conversiones de tipo | `pendientes-brox.md` §2.2, cada una con su propio bloqueo de dato |
| `familia` — agrupar un formulario que pasa de 25 a 55 campos | Decisión de presentación; va con el corte del SPA. **Registrado como consecuencia de este corte, no como hueco silencioso** |
| `estacionamiento_independizado` | No se siembra: §3.6 la marca provisional y el Corte 6 la cierra con `unidad_relacionada`. Sembrar hoy lo que sabemos que será sustituido es deuda de retirada a cambio de nada |
| `servicios_disponibles`, LISTA muda | Sus reemplazos nacen en el Corte 5 y la guarda global va con ellos. Retirarla antes deja un agujero de captura |
| Los tres hechos huérfanos restantes | `nivel_implementacion` (4), `estado_ocupacion` y `lote_minimo_normativo` (5) |
| El tipo `X` (OTRO) | Ninguna de las 30 lo incluye. Sigue sin auditar |
| El límite del suelo del gate | Con 81 claves, un suelo de 51 tolera 30 retiradas. Escrito en `2026-08-24-el-censo-que-se-rompia-al-avanzar.md`, no escondido |
| `area_minima_arrendable` tiene `unidad = 'm2'` sin acento (olvido de `V77`) | **No se arregla aquí**: está fuera del encargo congelado. Lo recoge CONTROL para el Corte 4 |
