# Corte 5 · subtanda 5A — la ocupación y los servicios, con vocabulario (`V84`)

**Fecha:** 2026-08-25 · **Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `795ffbf16384853b3e2c220895d4ac5ff6d01d06`
**Encargo congelado:** `docs/ai/encargo-corte-5-terreno.md` (D-1…D-7)
**Decisión de fondo de la clave transversal:** `docs/ai/decision-estado-ocupacion-en-los-siete.md` (D-C5-1)

> **Este documento NO cierra el corte.** El corte lo cierra CONTROL tras auditoría
> adversarial independiente. Aquí está lo medido, no un veredicto.

---

## 1. Qué entró

| # | cambio | resultado medido |
|---|---|---|
| 1 | **`estado_ocupacion`** nace LISTA, `aplica_todos = false`, **OPC en los siete** | 7 filas de aplicabilidad, 4 opciones |
| 2 | **`agua_desague`** nace LISTA **con** su vocabulario | **PUB en `T`**, OPC en `A`, 3 opciones |
| 3 | **`energia_electrica`** nace LISTA **con** su vocabulario | **PUB en `T`**, 3 opciones |
| 4 | **`gas`** gana `CON_FACTIBILIDAD_APROBADA` en la posición 3 | 6 opciones, orden denso 1…6, **misma clave, mismo tipo, misma aplicabilidad, no llega a `X`** |
| 5 | **`servicios_disponibles` → `activo = false`** | sigue existiendo, `del_sistema`, con su fila `T=OPC` y **con todos sus valores** |
| 6 | la guarda **«ninguna LISTA/LISTA_MULTIPLE *activa* de PROPIEDAD sin vocabulario»**, extendida | 0 listas activas mudas, en los dos sujetos y los dos ámbitos |

Estado final del catálogo del sistema en `controllocal_dev`:

```
         clave         | orden | tipo  | activo | opciones |            aplicabilidad
-----------------------+-------+-------+--------+----------+---------------------------------------
 servicios_disponibles |   190 | LISTA |   f    |    0     | T=OPC
 gas                   |   610 | LISTA |   t    |    6     | A=OPC C=OPC D=OPC L=OPC O=OPC T=OPC
 agua_desague          |   612 | LISTA |   t    |    3     | A=OPC T=PUB
 energia_electrica     |   614 | LISTA |   t    |    3     | T=PUB
 estado_ocupacion      |   950 | LISTA |   t    |    4     | A=OPC C=OPC D=OPC L=OPC O=OPC T=OPC X=OPC
```

`orden` **612** y **614** intercalan detrás de `gas` (610). No es cosmética: los
huecos de diez de `V81` existen exactamente para esto —su cabecera lo dice— y las
tres claves son **la misma conversación con el propietario**: qué servicios llegan
a esta puerta. `estado_ocupacion` va a 950 y no intercalado porque no pertenece a
ningún grupo existente: es un hecho de **situación**, no una instalación.

---

## 2. Medición ANTES / DESPUÉS · `controllocal_dev`

|  | ANTES de `V84` | DESPUÉS de `V84` |
|---|---|---|
| propiedades | 26 | 26 |
| **publicables** | **7** | **6** |
| bloqueadas | 19 (todas `L`, por `tipo_acceso`) | 20 (19 `L` + **`PROP-0024`**) |
| filas `PUB` del sistema | 1 (`tipo_acceso`/`L`) | **3** (`+ agua_desague/T`, `+ energia_electrica/T`) |
| claves del sistema activas | 120 | **122** (+3 nacen, −1 se retira) |
| LISTAS activas sin vocabulario | **1** (`servicios_disponibles`) | **0** |

### El efecto buscado, con nombre y código

```
PROP-0024   T   metraje = 1200 (columna canónica) · zonificacion = RDM
ANTES     publicable
DESPUÉS   bloqueado por: agua_desague, energia_electrica
```

**Es el resultado esperado, no un fallo.** Un terreno anunciado sin decir si tiene
agua y luz no es una oferta. Y en la periferia se tiene luz y no desagüe, o al
revés: un solo campo agregado —que es lo que era `servicios_disponibles`— escondía
justo la combinación que decide la compra.

**No se rellenó el dato en ninguna propiedad.** La aserción 8.8 de la migración lo
comprueba: el número de valores de `agua_desague`/`energia_electrica` escritos por
`V84` tiene que ser **exactamente** el que autoriza el acta de clasificación, que
en `dev` es cero. Se desbloquea el hecho verificado, no el relleno.

El bloqueo **informa**: desde `35cf09c` la PROPIEDAD reporta su propia deuda en
`PropiedadResponse.faltanParaPublicar`, con el **rótulo** («Agua y desagüe»), y el
rechazo del `POST` lo nombra. Sin esa superficie, estrenar una `PUB` habría sido
estrenar un rechazo mudo.

---

## 3. El legado de `servicios_disponibles`

### Lo medido antes de escribir nada

```
controllocal_dev            0 filas
controllocal_repositorios   322 filas · DOS cadenas distintas
                            "Agua, luz y desague"  283
                            "agua y desague"        39
```

Y un dato que corrige la fuente: **`servicios_disponibles` aplica sólo a `T`.** La
enumeración de siete tipos de `auditoria-profundidad-inmobiliaria.md:115`
(`A,C,L,O,T,D,X`) **nunca fue cierta**: `catalogo_atributo_tipo` tiene **una** fila
para la clave `19`, `T/OPC`. El legado sólo puede venir de terrenos.

### El veredicto, y por qué

**Recuperables: 0. Ambiguos: 322. No inventariados: 0.**

La clave era LISTA **sin una sola opción**, así que aceptaba cualquier cadena: es
texto libre de facto. Las dos cadenas medidas afirman que hay agua y desagüe, y una
afirma además que hay luz. Lo que **ninguna** dice es lo único que las claves nuevas
existen para capturar: si el servicio está **conectado** o sólo tiene **factibilidad
aprobada**. La auditoría pidió ese tercer estado *precisamente porque el campo viejo
no sabía distinguirlo*; traducir «tiene agua» a `CONECTADO` sería inventar por el
caso frecuente justo la distinción que motivó el cambio.

`SIN_SERVICIO` tampoco es la traducción de «no lo mencionó»: que una cadena hable de
agua y calle la luz no dice que no haya luz, dice que **no consta**.

**Lo ambiguo permanece FALTANTE, y se cuenta.** El dato se recupera visitando.

### Cómo se clasifica, y qué pasa con una cadena que nadie ha visto

Por **texto exacto**, con un acta explícita y corta —el mismo patrón que el
`codigo IN (...)` de `V14`, `V76` y `V83`—. Escribir un intérprete de castellano
(«si contiene *agua* entonces…») acertaría con «agua y luz» y mentiría con «sin
agua, con luz». Lo que no está en el acta **no se adivina**: se cuenta como
`NO_INVENTARIADO`, que es una forma de FALTANTE.

### La invariante, escrita como invariante y jamás como `= 0`

```
clasificado = total_legado           (ningún valor sin destino ni sin declararse FALTANTE)
recuperables + ambiguos + no_inventariados = clasificado
conjunto de valores de servicios_disponibles ANTES == DESPUÉS  (fila a fila)
```

En `dev` hay 0 filas y en `controllocal_repositorios` hay 322 porque un fixture las
escribe en cada corrida. **Una aserción `= 0` pasaría en dev y mentiría en pruebas.**

### Conservación comprobada tras aplicar

```
controllocal_repositorios, después de V84
  "Agua, luz y desague"  283   (idéntico)
  "agua y desague"        39   (idéntico)
  linaje escrito por el reparto: 0 filas  (porque el reparto no repartió nada)
```

Y el mecanismo que lo hace seguro: **retirar una clave no oculta sus valores**.
`LectorPorAutoridad` lee las filas del inmueble sin preguntar si su clave sigue
activa, y `fichaDeAtributo` tolera la definición ausente (`rotulo = clave`). Si la
lectura filtrara por catálogo activo, retirar una clave **borraría de la vista**
todo lo capturado con ella. Lo fija
`OcupacionYServiciosIntegrationTest.retirarUnaClaveNoOcultaSusValores`, sobre una
clave de prueba: la real ya no admite escrituras —esa es la otra mitad del
contrato— y un caso que dependiera del legado que traiga la base sería verde y
vacío en una base limpia.

**Lo que sí se cierra es la ESCRITURA.** `exigir_atributo_gobernado` exige
`activo = true`, así que la clave retirada no admite valores nuevos. Es lo correcto
—un concepto retirado no sigue capturando— y queda dicho para que no se descubra
como sorpresa.

---

## 4. El orden dentro de `V84`, y una afirmación del encargo que la medición no sostiene

| paso | qué | comprobado |
|---|---|---|
| 1 | foto del estado previo en tablas TEMP, con `DROP` explícito (nunca `ON COMMIT DROP`) | ✅ |
| 2 | nacen `agua_desague` y `energia_electrica` **con** sus opciones, en la misma sentencia | ✅ |
| 3 | sus filas de aplicabilidad, `requerido = false` | ✅ |
| 4 | nace `estado_ocupacion` con su vocabulario; `gas` gana su opción y su reordenación | ✅ |
| 5 | **sólo entonces** el reparto de lo recuperable, con linaje | ✅ |
| 6 | **sólo entonces** `servicios_disponibles → activo = false` | ✅ |
| 7 | **sólo entonces** la guarda de vocabulario extendida | ✅ |
| 8 | bloque `DO $$` de aserciones | ✅ |
| 9 | comparación contra la foto, por **conjuntos** | ✅ |

### `invertir 6 y 7` → **la migración ABORTA**, comprobado

Ejecutando el bloque 7 tal cual sobre una copia de la base con
`servicios_disponibles` reactivada:

```
ERROR:  V84: hay LISTAS activas sin vocabulario: PROPIEDAD/servicios_disponibles (sistema).
        Una LISTA sin opciones se degrada a TEXTO en el motor de captura y el trigger
        acepta cualquier cadena: la clave nace muda y nadie lo ve.
EXIT=3
```
y con la clave ya retirada, el mismo bloque pasa (`EXIT=0`).

### `invertir 5 y 6` → **el encargo dice que «pierde el legado» y, MEDIDO CONTRA ESTA IMPLEMENTACIÓN, NO ES ASÍ**

Se escribe en vez de repetirse. La retirada es `activo = false` y **no borra ni una
fila**; ningún paso del bloque 5 mira `catalogo_atributo.activo` de la clave que
reparte —clasifica leyendo `atributo_propiedad` y escribe sobre las claves
**nuevas**, que están activas—. Invertirlos hoy sería inocuo.

**El orden se respeta igual**, y por una razón que sí se sostiene: la única forma de
que el reparto no pueda mirar lo que sustituye es que alguien convierta la retirada
en algo más que una desactivación. Ese día el orden es lo único que lo impide.

---

## 5. Gates: sabotaje en las dos direcciones

Sobre una **copia desechable** de `controllocal_dev` (`v84_sabotaje`, creada con
`TEMPLATE` y **eliminada al terminar**), no sobre la cartera. Cada sabotaje:
introducir exactamente el defecto protegido → correr **sólo** el gate → exigir
ROJO → revertir → exigir VERDE.

| # | defecto introducido | comprobaciones que se pusieron en ROJO | tras revertir |
|---|---|---|---|
| S1 | `estado_ocupacion` sembrado en **SEIS** tipos (falta `X`) | **81, 82, 83** (3 fallos) | VERDE |
| S2 | `servicios_disponibles` vuelve a `activo = true` | **84, 87** (2 fallos) | VERDE |
| S3 | `agua_desague` pierde su vocabulario | **85, 87** (2 fallos) | VERDE |
| S4 | `agua_desague`/`T` baja de `PUB` a `OPC` | **86** (1 fallo) | VERDE |
| S5 | `gas` se extiende a `X` | **89** (1 fallo) | VERDE |
| S6 | se rompe el espejo `requerido` ↔ `exigencia` en una fila `OPC` | **90** (1 fallo) | VERDE |
| S7 | `gas` pierde `CON_FACTIBILIDAD_APROBADA` | **88** (1 fallo) | VERDE |
| S8 | un inmueble con legado ambiguo aparece con el servicio ya traducido | **91** + una de `4P` (2 fallos) | VERDE |

**S8 dejó una lección de propina**: además de la comprobación de 5A saltó la de
4.P «después del cutover ningún hecho del inmueble sin linaje», porque el valor se
insertó por SQL directo. Los dos gates ven el mismo defecto desde dos ángulos, y
eso es lo que se quería.

**S1 es el sabotaje que el encargo pedía explícitamente** («siembra
`estado_ocupacion` en SEIS tipos → ROJO»): las tres comprobaciones lo cazan por
separado, y la 82 existe precisamente para que la 81 no pueda salir verde sobre un
universo vacío.

### Un gate mordió de verdad, sin sabotaje

`GateDeCierreTest` **se puso rojo por sí solo** al añadir la suite nueva:

```
GateDeCierreTest.elScriptDeCierreComprobaraTodasLasPruebasDeIntegracion:166
  expected: <[]> but was: <[OcupacionYServiciosIntegrationTest]>
GateDeCierreTest.todoTestDeIntegracionDependeDeLaMismaVariableYEstaInventariado:76
  Cambio el inventario de tests de integracion.
```

Es exactamente su trabajo: una suite de integración que no esté en la lista de
`Verificar-Cierre.ps1` se saltaría **en silencio** en una corrida de cierre. Se
registró en los dos inventarios y volvió a verde.

### Estado del gate SQL

```
gate-modelo-universal.sql contra controllocal_dev
  en verde | en rojo | total
        96 |       0 |    96
```
De ellas, **11 nuevas** (81…91), todas del bloque `5A`.

---

## 6. Pruebas dirigidas (T1/T2)

`TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_repositorios`

| suite | tests | resultado |
|---|---|---|
| `OcupacionYServiciosIntegrationTest` **(nueva)** | 14 | ✅ |
| `CatalogoQueHablaIntegrationTest` | 38 | ✅ |
| `ConservacionDeLaEdicionIntegrationTest` | 48 | ✅ |
| `ProcedenciaDelValorIntegrationTest` | 20 | ✅ |
| `PropiedadUniversalIntegrationTest` | 52 | ✅ |
| `SujetoDelDatoIntegrationTest` | 30 | ✅ |
| **total** | **202** | **0 fallos, 0 errores, 0 saltados** |
| `com.controllocal.arquitectura.*Test` (los gates de construcción) | 73 | ✅ |

**No se ejecutó** el reactor completo, ni `Verificar-Cierre.ps1`, ni `ng test`, ni
`ng build`: el encargo lo prohíbe expresamente para esta fase, y el cambio no toca
Angular ni ninguna capa transversal. La corrida T3 va después de la auditoría y
cuando CONTROL la autorice.

### Lo que cambió en las suites existentes

- **`ConservacionDeLaEdicionIntegrationTest`** — su contrato es «la carga más
  ancha que el catálogo le permite HOY», y `cadaCasoLlevaTodoLoQueSuTipoAdmite` lo
  comprueba contra el catálogo real. Los **siete** casos ganan
  `estado_ocupacion`; `TERRENO` gana `agua_desague` y `energia_electrica`;
  `ALMACEN` gana `agua_desague`. **El fixture de TERRENO se reescribió, no se
  borró**: donde iba `servicios_disponibles` con el texto libre «Agua, luz y
  desague» —una de las dos fuentes de las 322 filas de legado— van ahora los dos
  hechos separados, con el comentario que dice qué había y por qué cambió.
  `OTRO` pasa de 3 claves a 4 y su conjunto de familias declaradas gana `LISTA`.
- **`CatalogoQueHablaIntegrationTest.serviciosDisponiblesNoSeRompio()`** →
  **reescrito** como `serviciosDisponiblesQuedoRetiradaYSustituida()`. Afirmaba que
  la clave seguía aceptando texto libre y seguía con cero opciones: era cierto,
  era deuda declarada, y tras 5A es falso **por diseño**. No se borra —se perdería
  la constancia de que durante cuatro cortes hubo en el catálogo una LISTA que
  aceptaba cualquier cadena, y de que eso fue un aplazamiento con fecha y no un
  descuido—.

---

## 7. Simetrías comprobadas

| simetría | qué se comprobó |
|---|---|
| **ALTA ↔ EDICIÓN** | los siete casos de `ConservacionDeLaEdicion` escriben las claves nuevas **en el alta**; los casos de 5A las escriben **en la edición**. Las dos puertas aceptan lo mismo |
| **PUERTA A ↔ PUERTA B** | `crearEnEncargo` **y** `cambiarEstado` rechazan el terreno bloqueado; `PuertasDePublicacionTest` (4 pruebas) sigue verde |
| **PROPIEDAD ↔ ENCARGO** | `estado_ocupacion` es del hecho (PROPIEDAD), `entrega_desocupado` del pacto (ENCARGO). `SujetoDelDatoIntegrationTest` lo comprueba en las dos direcciones y ya cubría el par desde `V78` |
| **ESCALAR ↔ MULTIVALOR** | 5A no introduce ningún `LISTA_MULTIPLE`. Nada que reescribir, y por tanto nada que perder al reescribir |
| **RETIRADA ↔ CONSERVACIÓN** | la clave se retira, sus valores se conservan **y se siguen leyendo**; escribirla queda cerrado |
| **WEB ↔ CORE** | el SPA **no conoce claves**: barrido con `rg` sobre `frontend-angular/src` → 0 apariciones de las cuatro claves, con control positivo (`metraje_total`/`tipo_acceso` sólo aparecen en **comentarios**). No se tocó Angular |
| **CORE ↔ CABLE** | el vocabulario que publica `MotorDeCaptura` se compara fila a fila con el del catálogo, no con una lista en Java |

### «¿Hay una segunda manera de escribir esto?»

Barrido con `rg` (nunca `grep -iF`) sobre `controllocal-service`,
`controllocal-web` y `controllocal-persistence`: el **único** escritor de
`atributo_propiedad` es `AtributosGobernados`, y la única aparición de un literal
de clave en `main` es **dentro de un comentario** de `EscritorEstructural`. No hay
segundo productor.

---

## 8. Autoataque: lo que sí encontré

### 8.1 Un gate sin registrar (encontrado por el propio gate)

Ver §5. Corregido.

### 8.2 Una afirmación del encargo que no resiste la medición

`invertir 5 y 6 pierde el legado` — ver §4. Se corrigió en la cabecera de la
migración en vez de repetirla.

### 8.3 **HALLAZGO PARA CONTROL — hay un segundo criterio de «ocupación» en el sistema, y 5A no lo toca**

Medido: existe `uq_contrato_vivo_por_propiedad`, un índice único parcial sobre los
contratos vivos (`estado ∈ {D, V}`), y su prueba se llama
`OcupacionInmuebleIntegrationTest`. Es decir: **el sistema ya deriva una ocupación**
—«esta propiedad tiene un contrato vivo»— distinta de la que 5A introduce.

No son lo mismo:

- `estado_ocupacion` es un hecho **declarado u observado** sobre cualquier inmueble
  que BROX conozca, incluidos los que no gestiona;
- un contrato vivo es un registro **comercial propio**, y sólo existe para lo que
  BROX alquiló.

**Hoy no hay contradicción operativa**: `estado_ocupacion` es `OPC` y **ningún
consumidor la lee todavía**, así que nada elige entre las dos. Pero el día que
alguien derive publicabilidad, disponibilidad o un indicador de una de ellas, habrá
**dos autoridades** para la misma pregunta —y una podrá decir `DESOCUPADO` mientras
la otra tiene un contrato vivo—.

**No se ha cableado ninguna derivación**: hacerlo sería inventar una regla que nadie
decidió y ampliar el alcance. Queda **escrito y medido** para que CONTROL decida si
abre decisión propia. No bloquea 5A.

### 8.4 Lo que se buscó y no apareció

- **productor no inventariado** → no hay: un solo escritor (§7);
- **puerta alternativa de publicación** → `PuertasDePublicacionTest` verde, sin vías nuevas;
- **asimetría alta/edición** → cubierta por los siete casos de alta + los de edición;
- **pérdida al retirar** → probada, en el mecanismo y en la conservación de las 322 filas;
- **pérdida al reescribir multivalor** → 5A no introduce multivalor;
- **dato legado reinterpretado** → 0 traducciones, con gate que lo caza (S8);
- **documentación que dejó de ser cierta** → §9.

---

## 9. Documentación tocada, y por qué

| documento | qué cambió |
|---|---|
| `encargo-corte-5-terreno.md` | **D-3 transcrita literalmente** (`condicion_terreno` = `PUB`, no `ALT`). Se cierra el hueco de §2 y la reclamación de §7 — que se conserva citada, porque durante unas horas fue el estado real del encargo |
| `auditoria-profundidad-inmobiliaria.md` | la celda de `condicion_terreno` (línea 242) queda **derogada en su eje de exigencia** por D-3, conservando el argumento que sostiene *que la clave debe existir*, que sigue en pie. Era la única `ALT` que la auditoría proponía para el Corte 5: **va en 5B** |
| `mapa-ejecucion-brox.md` | Corte 5 pasa a **🟡 EN CURSO (5A)** con fecha 2026-08-25; la frase «I0 no abre el Corte 5» queda **fechada y superada**, no borrada; la mitad de «Corte 1 (resto)» que hablaba de dar vocabulario a `servicios_disponibles` deja de ser cierta y se dice |
| `pendientes-brox.md`, `checklist-…md`, `i0-industrializacion-brox.md` | Corte 5 abierto; **`7 publicables y 19 bloqueadas de 26` queda fechado como registro histórico anterior a `V84`**, y se anota que la cifra con autoridad sale de esta evidencia |

**No se tocaron** `encargo-corte-3-vivienda.md`, `…-reconstruido.md` ni
`encargo-corte-4-comercial.md`: son evidencia fechada de cortes cerrados.

---

## 10. Nota de método: `V84` se reescribió y se reaplicó

Tras aplicar `V84` en `controllocal_dev` se corrigió su **cabecera** (§4, la
afirmación sobre invertir 5 y 6), lo que invalida el checksum de Flyway. En vez de
dejar una migración aplicada con un comentario falso, se deshizo su efecto en `dev`
—las tres claves borradas con el trigger de protección desactivado, `gas` devuelto a
sus cinco opciones y a su ayuda de `V81`, `servicios_disponibles` reactivada, la
fila `84` de `flyway_schema_history` eliminada— y se **reaplicó desde cero** al
reiniciar el contenedor. `V84` no había salido de esta máquina: no se reescribió una
migración publicada, se rehízo una que no existía por la mañana.

Verificado después: Flyway la aplicó limpia, el gate volvió a 96/96 y el estado
final medido es el de §1 y §2.

---

## 11. Reproducir

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5433/controllocal_repositorios'

# 1. la migración (CLEAN obligatorio: sin él el fat jar no se reconstruye)
mvn -f backend-spring/pom.xml -pl controllocal-app -am clean install -DskipTests
docker restart controllocal-api-v2

# 2. el gate SQL
docker cp backend-spring/verificacion/gate-modelo-universal.sql controllocal-postgres-v2:/tmp/gate.sql
docker exec controllocal-postgres-v2 psql -U controllocal -d controllocal_dev -v ON_ERROR_STOP=1 -f /tmp/gate.sql

# 3. las pruebas dirigidas (OJO: surefire separa por COMA, no por '+';
#    con '+' y failIfNoSpecifiedTests=false corre CERO tests y dice BUILD SUCCESS)
mvn -f backend-spring/pom.xml -pl controllocal-app test `
  "-Dtest=OcupacionYServiciosIntegrationTest,CatalogoQueHablaIntegrationTest,ConservacionDeLaEdicionIntegrationTest,SujetoDelDatoIntegrationTest,PropiedadUniversalIntegrationTest,ProcedenciaDelValorIntegrationTest"
```
