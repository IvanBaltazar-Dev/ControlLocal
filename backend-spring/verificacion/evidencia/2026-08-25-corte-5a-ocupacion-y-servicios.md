# Corte 5 · subtanda 5A — la ocupación y los servicios, con vocabulario (`V84`)

**Fecha:** 2026-08-25 · **Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `795ffbf16384853b3e2c220895d4ac5ff6d01d06`
**Encargo congelado:** `docs/ai/encargo-corte-5-terreno.md` (D-1…D-7)
**Decisión de fondo de la clave transversal:** `docs/ai/decision-estado-ocupacion-en-los-siete.md` (D-C5-1)

> **Este documento NO cierra el corte.** El corte lo cierra CONTROL tras auditoría
> adversarial independiente. Aquí está lo medido, no un veredicto.

> **SEGUNDA VUELTA.** El candidato `8048006` fue **RECHAZADO** con ocho hallazgos.
> Las correcciones están en **§12**, hallazgo por hallazgo, y las secciones que
> decían algo falso —**§2**, **§5**, **§7** y **§10**— están corregidas **en su
> sitio**, con lo que decían y por qué era falso. Ninguna se ha borrado.

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

Estado final del catálogo del sistema en `controllocal_dev`, **remedido tras
reaplicar el fichero del commit** (2026-08-25, 18:2x):

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

> **REMEDIDO el 2026-08-25 tras el hallazgo H1** (§10 y §12.1). Las cifras que
> había aquí procedían de un fichero que no está en git; éstas salen de deshacer
> `V84` en `dev`, medir el estado previo, dejar que Flyway aplique **el fichero
> del commit** y volver a medir. La consulta está en §11 y las dos columnas se
> tomaron con la **misma** consulta, no con dos.
>
> **El resultado numérico coincide con el anterior**, y eso no lo convierte en
> redundante: antes era una coincidencia sin autoridad, y ahora es una medición.

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

> **Y comprobada OTRA VEZ tras deshacer y reaplicar** (§10): el corpus de 322
> filas sobrevivió a la operación entera —deshacer `V84`, volver a `V83`,
> reaplicar la migración del commit desde la corrida de pruebas— con las mismas
> dos cadenas y los mismos recuentos. Deshacer una migración **no** era una
> excusa para perder el legado con el que se prueba.

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

> **S8 medía lo correcto con el predicado equivocado.** La comprobación 91 exigía
> entonces un rastro con `canal <> 'SISTEMA'`, y eso **prohibía el mecanismo que
> la propia `V84` incorpora** — el reparto del acta escribe `canal = 'SISTEMA'`—.
> Salía verde sólo porque el acta no resolvía ninguna cadena. Corregido tras la
> auditoría (§12.2) y **saboteado en las dos direcciones**, S10a y S10b.

**S8 dejó una lección de propina**: además de la comprobación de 5A saltó la de
4.P «después del cutover ningún hecho del inmueble sin linaje», porque el valor se
insertó por SQL directo. Los dos gates ven el mismo defecto desde dos ángulos, y
eso es lo que se quería.

**S1 es el sabotaje que el encargo pedía explícitamente** («siembra
`estado_ocupacion` en SEIS tipos → ROJO»): las tres comprobaciones lo cazan por
separado, y la 82 existe precisamente para que la 81 no pueda salir verde sobre un
universo vacío.

### Los cuatro sabotajes que AÑADIÓ la auditoría (2026-08-25)

Sobre copias desechables creadas con `TEMPLATE` y **eliminadas al terminar**:
`v84_ctrl` (copia de `controllocal_repositorios`, con sus **322 filas de legado**)
y `v84_h7` (copia de `controllocal_dev`).

| # | qué se comprueba | resultado |
|---|---|---|
| **S9a** | **H3** · una cadena de legado que el acta NO inventaría (`'sin agua, con luz'` — el contraejemplo que cita la propia cabecera del bloque 5) | **`V84` ABORTA, `EXIT=3`**, nombrando la cadena: *«1 valores de servicios_disponibles llevan cadenas que el acta no inventaria: 'sin agua, con luz'…»* |
| **S9b** | **H3** · la misma base **sin** esa cadena | `V84` aplica **`EXIT=0`**, y las **322 filas de legado siguen ahí**, con `servicios_disponibles` en `activo = false` |
| **S10a** | **H2** · un servicio traducido sobre un legado ambiguo **sin ningún linaje** | **93 verde / 3 rojo**: caen la **91** *(«ningun inmueble con legado recibio un servicio sin que nadie lo afirmara»)* y la **76** de 4.P, más la 78 que ya estaba roja en esa copia (ver nota) |
| **S10b** | **H2** · el **mismo** valor, ahora **repartido por el acta**, con su linaje `canal = 'SISTEMA'` y su `evidencia_ref` | **95 verde / 1 rojo** (sólo la 78 de base): la 91 vuelve a **VERDE**. **El predicado anterior seguía delatándolo**: medido sobre ese mismo estado, `canal <> 'SISTEMA'` encuentra **1 fila** — es decir, el gate viejo se habría puesto rojo por comportarse bien |
| **S11** | **H7** · el sistema pierde `estado_ocupacion` en `X` **y** una organización declara la suya cubriendo los siete | **92 verde / 4 rojo**: **81, 82 y 83** en rojo (más la 50, que salta porque el sabotaje sombrea una clave común). Con el predicado **anterior**, sin `organizacion_id IS NULL`, la **82 habría salido VERDE** sobre ese mismo estado — medido: `comprobacion_82_VIEJA = t`. Revertido el sabotaje: **96/96** |

> **La 78** (`4P despues del cutover ninguna columna estructural sin linaje`) está
> en rojo **de base** en la copia de `controllocal_repositorios`: es residuo de
> propiedades creadas por pruebas anteriores al mecanismo de linaje, no lo
> introduce ningún sabotaje y no aparece en `controllocal_dev`. Se dice porque un
> «3 rojo» sin explicar la tercera no es una medición.

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
gate-modelo-universal.sql contra controllocal_dev   (remedido tras las correcciones)
  en verde | en rojo | total
        96 |       0 |    96      EXIT=0

  81  5A el hecho de la ocupacion llega donde se pacta su condicion   OK
  82  5A y el par esta cubierto en los SIETE tipos, no en cero        OK
  83  5A estado_ocupacion aplica EXACTAMENTE a los siete tipos        OK
  84  5A servicios_disponibles esta retirada y NO borrada             OK
  85  5A sus dos reemplazos estan activos y con vocabulario           OK
  86  5A los dos servicios impiden PUBLICAR un terreno                OK
  87  5A ninguna LISTA activa se quedo sin vocabulario                OK
  88  5A gas distingue la red de la calle del papel de la concesionaria OK
  89  5A y gas no cambio de concepto: sigue LISTA y no llego a X      OK
  90  5A requerido sigue siendo espejo exacto de exigencia = ALT      OK
  91  5A ningun inmueble con legado recibio un servicio sin que nadie lo afirmara  OK
```
De ellas, **11 nuevas** (81…91), todas del bloque `5A`. Las **81, 82 y 91**
cambiaron de predicado tras la auditoría (§12.2 y §12.7).

---

## 6. Pruebas dirigidas (T1/T2)

`TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_repositorios`

| suite | tests | resultado |
|---|---|---|
| `OcupacionYServiciosIntegrationTest` **(nueva)** | 14 | ✅ |
| `CatalogoQueHablaIntegrationTest` | 38 | ✅ |
| `ConservacionDeLaEdicionIntegrationTest` | 48 | ✅ |
| `NucleoUniversalIntegrationTest` | 15 | ✅ |
| `ProcedenciaDelValorIntegrationTest` | 20 | ✅ |
| `PropiedadUniversalIntegrationTest` | 52 | ✅ |
| `SujetoDelDatoIntegrationTest` | 30 | ✅ |
| **total** | **217** | **0 fallos, 0 errores, 0 saltados** |
| `com.controllocal.arquitectura.*Test` (los gates de construcción) | 73 | ✅ |

> **Reejecutadas enteras después de las correcciones de auditoría** (2026-08-25).
> `NucleoUniversalIntegrationTest` se añade a la lista porque la corrección de la
> **lectura** (§12.8) toca `PropiedadUniversalServiceImpl.ficha()` y los dos
> enrutadores del catálogo: es la suite que cubre el núcleo por debajo de ellos.
> La corrida que aplicó `V84` sobre `controllocal_repositorios` fue **ésta**, con
> las 322 filas de legado delante: la aserción 8.8 corrió con universo real, no
> con uno vacío.

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
| **WEB ↔ CORE** | el SPA **no conoce las claves de 5A**: barrido con `rg` sobre `frontend-angular/src` → **0 apariciones** de las cuatro, con control positivo que **sí** devuelve resultados (`metraje_total` y `tipo_acceso` aparecen en **comentarios y en fixtures de `propiedad-detail.spec.ts`**, líneas 67, 515, 522, 525, 532 y 717 — ver la corrección de §12.6). La lectura de la ficha **sí** cambió en el backend (§12.8) y el SPA no necesitó tocarse, porque ya pintaba `rotulo` y `tipoDato` |
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

## 10. Nota de método: `V84` se reescribió y se reaplicó — **y la primera vez salió mal**

> **⚠ ESTA SECCIÓN AFIRMABA ALGO QUE LA MEDICIÓN CONTRADICE.** Se corrige entera
> el 2026-08-25 tras la auditoría (hallazgo H1). Se deja el error escrito porque
> el error **es** el hallazgo: una nota de método que dice «verificado» sin
> medirlo es exactamente lo que la auditoría existe para cazar.

**Lo que esta sección decía:** que tras corregir la cabecera de `V84` se deshizo
su efecto en `dev` y se **reaplicó desde cero**, y que «Flyway la aplicó limpia».

**Lo que había pasado de verdad:** la reaplicación corrió contra el **jar
anterior**. Medido por CONTROL:

```
controllocal_dev            V84 checksum = -77882090    (de un fichero que no existe en git)
controllocal_repositorios   V84 checksum = 1818954932   (el fichero del commit)
controllocal-app/target/*.jar   17:25:51
V84__...sql                     17:29:54                <- la fuente es POSTERIOR al jar
```

Flyway lee la migración **del classpath**, no del árbol de fuentes: sin
`clean install` el fat jar no se reconstruye, así que `dev` reaplicó la versión
vieja y se quedó con su checksum. Como el diff era **sólo comentario**, el
catálogo resultante era idéntico y nada lo delató — pero el primer
`clean install` + reinicio habría dejado `dev` sin arrancar con
«Migration checksum mismatch for migration version 84».

**Lo que se hizo para corregirlo** (2026-08-25, tras la auditoría):

1. `mvn -f backend-spring/pom.xml -pl controllocal-app -am clean install -DskipTests`,
   comprobando el jar **antes y después**: `75 529 834 B @ 17:25:51` →
   `75 532 090 B @ 18:23:46`. **El tamaño es el testigo**: sin `clean` no cambia.
2. Foto del catálogo del sistema de `dev` **antes** de tocar nada (549 líneas:
   claves, aplicabilidad y vocabulario).
3. Deshecho el efecto de `V84` en `dev` **y en `controllocal_repositorios`** —las
   tres claves y su vocabulario, `gas` devuelto a sus cinco opciones, a su orden
   denso 1…5 y a su `ayuda` de `V81`, `servicios_disponibles` reactivada, el
   linaje del reparto retirado y la fila `84` de `flyway_schema_history`
   eliminada—, con los dos triggers de protección desactivados dentro de la misma
   transacción y **rehabilitados al terminar**.
4. Reiniciado el contenedor: Flyway aplicó la migración **del fichero del commit**.

```
o.f.core.internal.command.DbMigrate : Migrating schema "public" to version "84 - la ocupacion y los servicios con vocabulario"
DB: V84: nacen estado_ocupacion (OPC en los 7), agua_desague (PUB en T, OPC en A) y
    energia_electrica (PUB en T), las tres CON vocabulario; gas pasa a 6 opciones;
    servicios_disponibles queda activo=false conservando sus 0 valores
    (0 recuperables, 0 ambiguos, 0 no inventariados); quedan 122 claves del sistema activas.
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "public", now at version v84
c.c.app.ControlLocalApplication     : Started ControlLocalApplication in 45.948 seconds
```

**Los dos checksums, después:**

```
controllocal_dev            84 | -772651593 | success = t
controllocal_repositorios   84 | -772651593 | success = t     (aplicada por Flyway en la corrida de pruebas)
```

Y la **foto del catálogo comparada línea a línea** entre el estado producido por
la versión anterior y el producido por la del commit: **idénticas**. Es la prueba
de que la reescritura no cambió ni una fila —sólo comentarios y aserciones— y de
que la reaplicación es fiel.

`V84` no había salido de esta máquina: no se reescribió una migración publicada,
se rehízo una que no existía por la mañana.

### Y una comprobación para que no vuelva a pasar

El runbook de §11 lleva ahora el `clean` **obligatorio con su motivo y con el
testigo** (el tamaño del jar), y el arranque limpio del contenedor como paso
verificable. Un gate que compare `flyway_schema_history.checksum` con el resuelto
del classpath **es mejor y no cabe aquí**: exige un componente que lea el
classpath de Flyway dentro de una prueba de arranque, y eso es trabajo propio, no
una corrección de auditoría. Queda anotado como deuda en `pendientes-brox.md`
§7 — hasta entonces, lo que protege es el runbook.

---

## 11. Reproducir

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5433/controllocal_repositorios'

# 1. la migración. CLEAN OBLIGATORIO: Flyway lee del CLASSPATH, y sin `clean` el
#    fat jar NO se reconstruye -- Maven dice BUILD SUCCESS igual. El testigo es
#    el TAMANO del jar: si no cambia, no se reempaqueto y el contenedor sirve la
#    migración vieja (fue el hallazgo H1 de la auditoría, ver §10).
ls backend-spring/controllocal-app/target/*.jar          # antes
mvn -f backend-spring/pom.xml -pl controllocal-app -am clean install -DskipTests
ls backend-spring/controllocal-app/target/*.jar          # despues: tiene que haber cambiado
docker restart controllocal-api-v2

# 1 bis. y el arranque tiene que ser LIMPIO. Un checksum desalineado muere aquí:
docker logs controllocal-api-v2 --since 3m | Select-String "Successfully applied|checksum|Started ControlLocalApplication"

# 2. el gate SQL
docker cp backend-spring/verificacion/gate-modelo-universal.sql controllocal-postgres-v2:/tmp/gate.sql
docker exec controllocal-postgres-v2 psql -U controllocal -d controllocal_dev -v ON_ERROR_STOP=1 -f /tmp/gate.sql

# 3. las pruebas dirigidas (OJO: surefire separa por COMA, no por '+';
#    con '+' y failIfNoSpecifiedTests=false corre CERO tests y dice BUILD SUCCESS)
mvn -f backend-spring/pom.xml -pl controllocal-app test `
  "-Dtest=OcupacionYServiciosIntegrationTest,CatalogoQueHablaIntegrationTest,ConservacionDeLaEdicionIntegrationTest,SujetoDelDatoIntegrationTest,PropiedadUniversalIntegrationTest,ProcedenciaDelValorIntegrationTest,NucleoUniversalIntegrationTest"

# 4. los gates de construcción
mvn -f backend-spring/pom.xml -pl controllocal-app test "-Dtest=com.controllocal.arquitectura.*Test"
```

---

## 12. Correcciones de la auditoría adversarial (2026-08-25)

CONTROL **rechazó** el candidato `8048006` con ocho hallazgos y verificó de forma
independiente el bloqueante y los documentales. Se corrigieron **en un solo
lote**. Lo que sigue es qué era cada uno, qué se cambió y con qué se demuestra.

### 12.1 H1 · BLOQUEANTE — `dev` tenía aplicada una `V84` que no existe

Ver **§10**, reescrita entera. Corregido: `clean install` con el tamaño del jar
como testigo, las **dos** bases devueltas a `V83` y reaplicada la migración del
commit, checksums iguales al fichero (`-772651593`), arranque limpio del
contenedor, foto del catálogo **idéntica** antes y después, y §1/§2 **remedidas**.
La recurrencia queda cerrada por runbook (§11, paso 1 y 1 bis) y anotada como
deuda de gate en `pendientes-brox.md` §7.1.

### 12.2 H2 · GRAVE — el gate prohibía el mecanismo que la propia `V84` incorpora

La comprobación **91** y su gemela `OcupacionYServiciosIntegrationTest.elLegadoNoSeTradujo`
exigían un rastro con `canal <> 'SISTEMA'`. El bloque 5 de `V84` escribe
`canal = 'SISTEMA'` con `evidencia_ref = 'V84 reparto…'`: **el día que el acta
resolviera una cadena, el gate se pondría rojo por comportarse bien**. Salía
verde sólo porque el acta autoriza cero filas.

**Corregido**: el predicado dice lo que quiere decir — *nadie afirmó este hecho* —
y se discrimina por **linaje**, no por canal: `naturaleza IS NOT NULL OR
evidencia_ref IS NOT NULL OR id_persona_rol IS NOT NULL`. El acta deja
`evidencia_ref`; una persona deja `id_persona_rol` (lo estampa
`LinajeDelValor.porActor`) y su `naturaleza` **si la sabe** — exigir la naturaleza
habría sido el mismo error al revés, porque es opcional por diseño y nunca se
deduce. **El mismo predicado, literal, en el gate y en el test**: dos formas de la
misma pregunta vuelven a divergir.

Probado en las dos direcciones: **S10a** (sin linaje → ROJO) y **S10b** (repartido
por el acta con `SISTEMA` → VERDE), §5.

### 12.3 H3 · GRAVE — la invariante «ningún valor sin destino» era vacua

El bloque 8.8 comparaba `total_legado` con `clasificado`, y `v84_reparto` se
construye con `LEFT JOIN` sobre **ese mismo conjunto** más
`coalesce(veredicto, 'NO_INVENTARIADO')`: la igualdad era cierta **por
construcción** y pasaba incluso con el acta vacía.

**Corregido**: se asevera sobre el **universo previo** y sobre lo que el acta no
cubre. `no_inventar > 0` **detiene la migración y nombra las cadenas distintas**;
`total_legado` se toma ahora de la **foto del bloque 0** —otra fuente— así que la
comparación con `clasificado` deja de ser una identidad y pasa a cazar que el
universo se mueva durante la migración.

Y se corrigió el razonamiento escrito, que era la raíz: «jamás `= 0`» valía para
el **tamaño del legado** (0 en `dev`, 322 en pruebas), no para *«ninguna de esas
filas, sean 0 o 322, cayó fuera del acta»*, que sí es una cifra exacta legítima.

Probado en las dos direcciones: **S9a** (`EXIT=3` nombrando `'sin agua, con luz'`)
y **S9b** (`EXIT=0` sin ella, con las 322 filas intactas), §5.

### 12.4 H4 · documentos que negaban `V84` en el commit que la trae

| documento | qué decía | qué dice ahora |
|---|---|---|
| `pendientes-brox.md:77` | «la siguiente migración de catálogo **será** `V84`» | `V84` **aplicada**; la siguiente es `V85` y no se abre hasta auditar 5A (D-4) |
| `pendientes-brox.md:697` | «I0 … antes de abrir una migración `V84`» | tachado y fechado: superado el mismo día, I0 y 5A avanzan en paralelo |
| `pendientes-brox.md` §2.3 | entera en presente: «acepta cualquier cadena», «sólo entonces pasa a `activo=false`», «falta su guarda gemela» | reescrita en pasado, con el diagnóstico conservado y la guarda marcada ✅ |
| `pendientes-brox.md` §2.4 | «quedan **dos**» pares hecho/condición | quedan **uno**; `estado_ocupacion` marcado ✅ `V84`, en los siete |
| `auditoria-…md:415` | `estado_ocupacion \| entrega_desocupado \| **falta**` | ✅ **cubierto** — `V84`, OPC en los siete |
| `auditoria-…md:940` | `V84  5  ⬜ <- preparado, no abierto` | `V84 🟡 5A … <- aplicada; 5B sin abrir`, y un banner de estado en la sección del Corte 5 |
| `encargo-corte-5-terreno.md:65` | «invertir 5 y 6 pierde el legado» | tachado, con **quién lo refutó y con qué medición** — ver 12.5 |

### 12.5 H4 bis · la frase falsa del encargo **era de CONTROL**, y así queda escrito

`encargo-corte-5-terreno.md` §«El orden dentro de `V84`» afirmaba que invertir los
bloques 5 y 6 **pierde el legado**. Lo escribió **CONTROL**, no el constructor, y
sin medirlo. El constructor se negó a repetirlo y lo refutó midiendo; la auditoría
**confirmó la refutación**: el bloque 5 no consulta `catalogo_atributo.activo` de
la clave que reparte, así que invertirlo hoy es inocuo — mientras que invertir 6 y
7 **sí** aborta (`EXIT=3`). El encargo lleva ahora la corrección **y la
atribución**. El orden 5 → 6 se conserva por la razón que sí resiste.

### 12.6 H5 · la evidencia afirmaba algo que la medición contradice

Dos frases, las dos corregidas:

1. **§10** decía que la migración corregida «se reaplicó desde cero» y que «Flyway
   la aplicó limpia». Lo reaplicado fue la versión **anterior**. Reescrita entera.
2. **§7** decía que el control positivo del barrido del SPA (`metraje_total`,
   `tipo_acceso`) «sólo aparece en comentarios». **Falso**: aparece también como
   *fixture* en `propiedad-detail.spec.ts:67, 515, 522, 525, 532, 717`. El barrido
   era correcto —0 apariciones de las cuatro claves de 5A, con un control positivo
   que **sí** devuelve resultados—; la frase que lo describía, no.

### 12.7 H7 · asimetría de ámbito en el gate

Las comprobaciones **81** y **82** y la aserción **8.3** de `V84` unían hecho y
condición **sin** `organizacion_id IS NULL`, mientras la vecina **83** sí filtraba.
Las tres miran ahora el **catálogo del sistema**. Que importaba está medido: con el
predicado anterior, una organización que declarase su propia `estado_ocupacion`
cubriendo los siete tipos dejaba la **82 en VERDE** aunque la del sistema hubiera
perdido `X` (**S11**, §5).

### 12.8 H8 · conservación de la LECTURA — entra, porque el encargo la exige

Retirar la clave conservaba el valor y **degradaba su lectura**:
`definicionesDe` sale de `aplicablesA`, que filtra `activo = true`, así que
`fichaDeAtributo` devolvía `rotulo = "servicios_disponibles"` y `tipoDato = null`,
y el valor caía al final de la lista. Un broker leería **la clave desnuda** — el
defecto que este repositorio ya nombra en `propiedad-detail.html:44` («*falta
metraje_total*, que no es una frase para nadie»). Y `tipoDato = null` no es
cosmético: el SPA decide con él si un booleano se dice «Sí/No» o «true»
(`propiedad-detail.ts:255`).

**Qué se cambió**, y dónde está la frontera:

| superficie | antes | ahora |
|---|---|---|
| **CAPTURA** — alta y editor (`aplicablesA`) | filtra `activo` | **igual**: una clave retirada no se pregunta |
| **ESCRITURA** — `exigir_atributo_gobernado` | exige `activo` | **igual**: una clave retirada no admite valores nuevos |
| **LECTURA** — la ficha | perdía rótulo, tipo, unidad y orden | los resuelve **aunque la clave esté inactiva** |

- `CatalogoAtributoRepository.paraLeer(org, claves)` — **la única** consulta del
  catálogo que no filtra `activo`, y sólo se invoca para claves **que ya tienen
  valor escrito** y que la consulta de captura no resolvió: no puede reintroducir
  una clave retirada en ningún formulario.
- `AtributosGobernados.definicionesParaLeer(...)` y su gemela
  `AtributosDeEncargo.definicionesParaLeer(...)`. **Las dos**, por simetría
  PROPIEDAD ↔ ENCARGO: la clave que 5A retira es de la propiedad, pero retirar una
  condición del encargo es la misma operación sobre la otra mitad, y arreglar una
  sola mitad es como se fabrica una asimetría.

**Prueba de las dos mitades**, en
`OcupacionYServiciosIntegrationTest.retirarUnaClaveNoOcultaSusValores`: sobre una
clave del tenant, **mientras está activa se pregunta**; retirada, la ficha sigue
devolviendo `rotulo = "Clave retirable"` y `tipoDato = "TEXTO"` con su valor, y el
motor de captura **deja de ofrecerla**. Y sobre la clave **real**, en
`serviciosDisponiblesQuedoRetiradaYNoBorrada`: `paraLeer` resuelve
`"Servicios disponibles"` / `LISTA` con `activo = false` — un caso que **no
depende** de que la base traiga legado, porque `dev` no tiene ninguno.

**Lo que NO entra**, por decisión de CONTROL: el `PUT` de una clave retirada muere
en el trigger y `ManejadorErroresApi` lo mapea a **409 «duplicado»**, que no es lo
que pasó. Es preexistente y toca el mapeo global de errores. Anotado como deuda
con su ruta en `pendientes-brox.md` §2.3 ter.

### 12.9 H6 · alcance — lo que se queda y lo que se revierte

- **Se queda, y se declara aquí**: `docs/ai/i0-industrializacion-brox.md` (fichero
  nuevo) y los banderines `HISTÓRICO — CERRADO` de los seis encargos **son trabajo
  de I0 arrastrado dentro de este commit**, no parte de 5A. Se dicen para que la
  próxima auditoría no tenga que descubrirlo.
- **Revertido**: el cambio de `mapa-ejecucion-brox.md` que pasaba la tabla «Qué
  gobierna, y qué no» de **tres** documentos a **cinco**. Es un cambio de
  **autoridad documental**, contradice `CLAUDE.md` («only three documents govern»)
  y **nadie lo decidió**. Vuelve a tres, con la ampliación anotada como **decisión
  pendiente del titular** — que no se resuelve por vía de los hechos.
