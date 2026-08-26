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

En `dev` hay 0 filas y en `controllocal_repositorios` hay 322. **Una aserción `= 0`
pasaría en dev y mentiría en pruebas.**

> **La frase que iba aquí era FALSA y la midió la segunda auditoría (N2).** Decía
> «…hay 322 **porque un fixture las escribe en cada corrida**». No las escribía
> nadie: el único productor de `servicios_disponibles` era el fixture de
> `ConservacionDeLaEdicionIntegrationTest`, y **este mismo corte lo eliminó** al
> reescribir esa línea. Las 322 filas son **residuo histórico** de corridas
> anteriores: sobre una base nueva —CI, otra máquina, un `docker volume rm`— el
> universo es **cero**, y tanto la comprobación **91** del gate como
> `elLegadoNoSeTradujo` salían **verdes sin haber mirado nada**. Corregido en la
> tercera tanda: hay productor determinista, control de cobertura y control
> positivo (§13).

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

> **Actualizado en la tercera tanda (§13.2): el gate tiene ahora 98
> comprobaciones**, dos más de `5A` —la **92**, control positivo de la 91, y la
> **93**, que exige que ese control devuelva la clave a `activo = false`—.
> Medido: `controllocal_dev` **98/98, EXIT=0**; `controllocal_repositorios`
> **97/1**, con la **78** como único rojo y **de base** (§13.6).

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
`atributo_propiedad` es `AtributosGobernados`. **No hay segundo productor.**

> **La segunda frase de este párrafo era falsa, y la corrección es del
> 2026-08-25.** Decía: *«la única aparición de un literal de clave en `main` es
> dentro de un comentario de `EscritorEstructural`»*. **El universo barrido no
> incluía `controllocal-domain`**, que es justo donde viven los literales:
> `CatalogoAtributo.java` declara **16 constantes `CLAVE_*`** con su literal
> (`:110-130`), y `Propiedad.java` nombra otras tres en el `name` de sus
> `@Column` (`piso`, `partida_registral`, `oficina_registral`).
>
> **Universo real y hecho medido** (2026-08-25) — se enumeró primero el universo
> en vez de buscar ficheros conocidos: **las 123 claves** que tiene
> `catalogo_atributo` en `controllocal_dev` (PROPIEDAD y ENCARGO, activas y
> retiradas), leídas de la base y no escritas a mano, buscadas **como literal
> entrecomillado** con `rg` sobre el `src/main` de **los cinco módulos**
> —`domain`, `persistence`, `service`, `web`, `app`—:
>
> ```
> 28 líneas en 7 ficheros
>   17  domain/inmueble/CatalogoAtributo.java          16 constantes CLAVE_* + 1 cita en javadoc
>    3  domain/inmueble/Propiedad.java                 3 @Column(name=…): piso, partida_registral, oficina_registral
>    3  service/impl/PropiedadUniversalServiceImpl.java  comentario / javadoc
>    2  persistence/repositorio/CatalogoAtributoRepository.java  javadoc
>    1  service/soporte/AtributosGobernados.java       javadoc
>    1  service/soporte/EscritorEstructural.java       comentario (el antipatrón que prohíbe)
>    1  web/dto/PropiedadUniversalDtos.java            javadoc
> ```
>
> Se clasificaron **una a una**: **9** son comentario o javadoc —las 8 de los
> cinco ficheros de abajo **más `CatalogoAtributo.java:205`**, que va dentro de
> la fila de 17 y es la cita `si clave == "metraje_total"` del antipatrón que ese
> mismo javadoc prohíbe—; 3 son el `name` de
> una `@Column` —de las claves **estructurales**, cuya columna se llama igual, no
> un enrutado por nombre—; y 16 son las constantes de `CatalogoAtributo`, que es
> exactamente lo que ese fichero declara hacer («nombrarlas NO es la matriz
> prohibida; lo prohibido es decidir DÓNDE se guarda una clave a partir de su
> nombre»). **9 + 3 + 16 = 28, y ninguna de las 28 escribe un atributo.**
>
> > La cifra de comentarios decía **8** y no cuadraba con el total —corregido el
> > **2026-08-25**, tras la segunda vuelta de auditoría—: se contaron los cinco
> > ficheros que son sólo comentario y se olvidó que la fila de 17 lleva 16
> > constantes **y una cita en javadoc**. La tabla de arriba ya lo decía; la
> > clasificación de abajo no lo sumaba.
>
> La conclusión de fondo —«no hay segundo productor»— **se sostiene y no
> dependía de la frase falsa**: descansa en que el único escritor de
> `atributo_propiedad` es `AtributosGobernados`. Lo que no se sostenía era
> afirmar «la única aparición» habiendo barrido tres módulos de cinco.

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

---

## 13. Correcciones de la SEGUNDA auditoría (2026-08-25, tercera tanda)

La segunda auditoría cerró los ocho hallazgos H1…H8 sin regresión, y encontró
**dos graves nuevos** —los dos de este corte— más cuatro menores. Esto es lo que
se hizo con cada uno.

### 13.1 N1 · GRAVE — la corrección de H6 arregló dos documentos de tres

`docs/ai/pendientes-brox.md` §9.4 seguía enumerando **siete** documentos
gobernantes, e `i0-industrializacion-brox.md` **lo añadió este corte** —medido con
`git diff 795ffbf 1b1cc0b -- docs/ai/pendientes-brox.md`—: la misma extensión de
autoridad no decidida de H6, en un tercer sitio.

- §9.4 lleva ahora **la redacción de tres del mapa**, en tabla, y el **mismo
  aviso**: la ampliación es un cambio de autoridad documental que **nadie
  decidió**, y queda como decisión pendiente del titular.
- El mapa gana una nota que dice que había un tercer sitio y que ya está alineado.
- **Los tres sitios dicen lo mismo**: `CLAUDE.md`, `mapa-ejecucion-brox.md` y
  `pendientes-brox.md` §9.4.

### 13.2 N2 · GRAVE — el corte borró el productor del legado y seguía citándolo como vivo

**Lo medido.** Ninguna prueba escribía ya `servicios_disponibles`. El único
productor era el fixture de `ConservacionDeLaEdicionIntegrationTest`, y este mismo
corte lo eliminó al reescribir esa línea. Las 322 filas de
`controllocal_repositorios` son **residuo histórico**; en `controllocal_dev` el
universo ya es **0**. Sobre una base nueva, la comprobación **91** y
`elLegadoNoSeTradujo` salían **verdes sin haber mirado nada** — y ninguna de las
dos llevaba el control de cobertura que sí llevan sus vecinas.

| | antes | ahora |
|---|---|---|
| **productor** | ninguno | `OcupacionYServiciosIntegrationTest.sembrarLegadoAmbiguo(idPropiedad)` — siembra por SQL, y **comprueba que sembró** |
| **cobertura** | ninguna | el caso mide `filas de legado` y `pares legado/servicio` y **falla si el universo está vacío**; en el gate, el universo real viaja en la **columna `nota`** de la comprobación 92 y **se imprime** |
| **control positivo** | ninguno | el mismo par escrito **sin linaje**: la consulta tiene que cazarlo, o su verde no significa nada |
| **predicado** | escrito **dos veces** (gate y Java) | **una sola definición por lado**: `pg_temp.hay_legado_traducido_sin_linaje()` en el gate, `legadoTraducidoSinLinaje()` en Java |

**Sembrar el legado exige saltarse la puerta, y se dice.** La clave está
`activo = false`, así que `exigir_atributo_gobernado` rechaza también el INSERT
directo —no la encuentra: `SQLSTATE 23503`—. Hay que **reactivarla, escribir y
retirarla**, y las tres sentencias van dentro de un `DO` (Java) o de un
`SAVEPOINT` (gate), que son **una sola transacción**: ninguna otra sesión ve la
clave activa y nada queda reabierto. Que sembrar el legado exija esta maniobra es,
en sí mismo, la prueba de que la puerta está cerrada — y por eso el gate añade la
comprobación **93**, que exige que la clave haya vuelto a `activo = false`.

**Los tres sabotajes del gate** (contra `controllocal_dev`, sobre el gate real,
revertidos y verificados con `sha256sum -c`):

| # | defecto introducido | exigido | medido |
|---|---|---|---|
| **G1** | el control positivo **no siembra** el legado → universo vacío | ROJO | **97/1** · `92 FALLO - no se pudo sembrar el legado: el control no probo nada`. **La 91 siguió VERDE** — que es exactamente el defecto que N2 denuncia |
| **G2** | el predicado de la 91 se queda **ciego** (`AND false`) | ROJO | **97/1** · `92 FALLO - el predicado de la 91 no caza una traduccion sin linaje: su verde no significa nada`. **La 91 siguió VERDE** |
| **G3** | el control reactiva la clave y **no la devuelve** (sin `ROLLBACK TO`, sin el `UPDATE` de vuelta) | ROJO | **97/1** · `93 FALLO` |

Revertido: **98/98 en verde, `EXIT=0`** contra `controllocal_dev`. Contra
`controllocal_repositorios`: **97/1**, y el único rojo es la **78** de base
(54 propiedades con `piso` sin linaje, anteriores a 4.P — §13.6).

**Los dos sabotajes de la prueba Java**, sobre una base **sin residuo**
(`v84_sinresiduo_pruebas`, copia `TEMPLATE` de `controllocal_repositorios` con
`DELETE FROM atributo_propiedad WHERE clave='servicios_disponibles'` → **0 filas**,
eliminada al terminar):

| # | defecto introducido | exigido | medido |
|---|---|---|---|
| **T1** | `elLegadoNoSeTradujo` **no llama** al productor | ROJO | `AssertionFailedError: el universo de la comprobacion esta VACIO y su verde no significaria nada: 0 filas de legado, 0 pares legado/servicio` |
| **T2** | el predicado `legadoTraducidoSinLinaje()` se queda **ciego** (`and false`) | ROJO | `AssertionFailedError: la consulta no caza un servicio escrito sobre un legado ambiguo sin ningun linaje: entonces su verde no significa nada` |

Revertidos —`sha256sum -c` OK sobre el fichero de la suite— y **verde sobre esa
misma base sin residuo**: `Tests run: 15, Failures: 0, Errors: 0`. Medido después:
la suite dejó **2 filas de legado y 1 par** donde antes había **0**. Ésa es la
demostración pedida: **el fixture nuevo fabrica su propio universo**.

#### El autoataque que sí encontró algo: el productor envenenaba otro gate

La primera versión del sembrador dejaba el `DEFAULT now()`. Al correr el gate
después de las suites, `controllocal_repositorios` pasó de **97/1** a **96/2**: se
había puesto roja la **76** de 4.P —«después del cutover ningún hecho del inmueble
sin linaje»—, con seis filas nombradas, todas del fixture.

**Tenía razón.** Un valor posterior al cutover sin rastro es un defecto real; el
fixture estaba fabricando un **dato imposible**. El legado es, por definición,
anterior al mecanismo de linaje —las filas históricas de `servicios_disponibles`
anteriores a la frontera lo son—, así que el sembrador escribe con
`frontera_de_linaje() - interval '1 day'`, en Java y en el gate. Verificado: tras
la corrección, la suite deja **324** filas de legado (322 + 2) y el gate vuelve a
**97/1**, con la 78 como único rojo.

> **Y en el camino se perdieron 71 filas de la base de pruebas, por un `DELETE` mal
> acotado del constructor. Se dice, y se dice cómo se repusieron.** Al limpiar el
> residuo mal fechado, el `WHERE fecha_creacion > frontera_de_linaje()` alcanzó
> **77** filas y no las 6 introducidas: 71 eran legado histórico **posterior** al
> cutover que sí tenía linaje. **No se inventó nada para reponerlas**:
> `rastro_valor_gobernado` es append-only y conserva de cada una su
> `organizacion_id`, su `id_agregado`, su `valor_texto` y su `registrado_en`, así
> que se reconstruyeron **desde el rastro**, que es la autoridad que 4.P existe
> para dar. Resultado medido, idéntico al corpus que mide este mismo documento más
> arriba: **322 filas, 283 «Agua, luz y desague» + 39 «agua y desague»**, y `76`
> otra vez verde. Si el rastro no hubiera existido, esas 71 filas se habrían
> perdido — que es exactamente el argumento de 4.P, comprobado a mi costa.

**La justificación falsa, corregida donde se podía tocar:**

| artefacto | qué se hizo |
|---|---|
| `verificacion/gate-modelo-universal.sql` | **corregido**: dice que el productor no existía, que las 322 son residuo y que por eso la invariante viaja con control positivo |
| `OcupacionYServiciosIntegrationTest` (javadoc de `elLegadoNoSeTradujo`) | **corregido** |
| esta evidencia (§ «La invariante, escrita como invariante») | **corregida** |
| `ConservacionDeLaEdicionIntegrationTest:376` | **corregido**: el comentario ya no deja huérfana la mención; dice que este fixture dejó de producir legado y adónde se movió el productor |
| **`V84…sql:408`** | **NO se toca. Decisión razonada abajo** |

#### Por qué NO se toca `V84`

`V84` está **aplicada en las dos bases**. Editar su fichero invalida el checksum y
repite **H1** entero: `clean install` con el tamaño del jar como testigo, deshacer
y reaplicar en las dos bases, verificar arranque limpio y checksums iguales. Se
decide **no tocarla**, y por tres razones, no por comodidad:

1. **La regla del repositorio es «never edit an applied migration»**, y aquí no hay
   defecto funcional que la justifique: lo que sobra es **una frase de un
   comentario**. El coste del ciclo H1 es alto y el riesgo —dejar una base con un
   checksum distinto de la otra— es real; ya ocurrió una vez en este mismo corte.
2. **Una migración aplicada es evidencia fechada.** Su cabecera dice por qué se
   hizo lo que se hizo el 2026-08-25 y con qué creencias; reescribirla es maquillar
   el registro, que es precisamente lo que este repositorio prohíbe.
3. **La frase no gobierna ninguna ejecución.** Los artefactos vivos —el gate y las
   suites— son los que deciden verde o rojo, y ésos **sí** están corregidos. La
   línea 408 queda **declarada como falsa aquí y en `pendientes-brox.md`**, que es
   donde se registra una deuda documental sin tocar la migración.

### 13.3 N4 · `AtributosDeEncargo.definicionesParaLeer` llegaba sin prueba

Añadida `OcupacionYServiciosIntegrationTest.retirarUnaCondicionNoOcultaLoPactado`:
crea una condición del **ENCARGO** en el tenant, la pacta en un encargo real, la
**retira**, y comprueba que lo pactado se sigue leyendo **con su rótulo y su tipo**
y que el motor de captura deja de ofrecerla. Es la gemela exacta de
`retirarUnaClaveNoOcultaSusValores`. Y no es teórico: una condición pactada vive en
encargos **ya cerrados**, que nadie puede volver a rellenar.

### 13.4 N5 · SQLSTATE equivocado en la deuda §2.3 ter

Medido contra `controllocal_dev`, no deducido:

```
ERROR:  23503: El atributo "servicios_disponibles" no esta en el catalogo
CONTEXT:  PL/pgSQL function exigir_atributo_gobernado() line 16 at RAISE
```

Una clave **retirada** no se encuentra —la consulta del trigger lleva
`AND c.activo = true`— y sale por `RAISE … USING ERRCODE = 'foreign_key_violation'`
→ **23503**. `23514` es lo que devuelven las otras ramas del mismo trigger (valor
fuera de vocabulario, columna equivocada). El desenlace —409 «duplicado»— es el
mismo por las dos vías y sigue siendo correcto. Corregido en `pendientes-brox.md`.

### 13.5 N6 · la justificación de H7 describía un estado que la base prohíbe

**Corregido en el gate** (el comentario gemelo de `V84:612-617` cae bajo la
decisión de §13.2). El motivo verdadero, medido:

- **ALCANZABLE**: `exigir_catalogo_no_sombrea_al_sistema` sólo mira en **una
  dirección** —sale por `RETURN NEW` cuando `NEW.organizacion_id IS NULL`—, así que
  una migración que siembra la clave del sistema **no comprueba** si algún tenant
  ya la tenía. Un tenant que hubiera declarado `estado_ocupacion` **antes de
  `V84`** conserva su fila y, sin el filtro, taparía el hueco. Ése, y sólo ése, es
  el orden histórico en que el sombreado existe.
- **NO ALCANZABLE**, y también se dice: *«una organización declara la suya»* a
  secas **no puede pasar** — el trigger lanza «una organizacion no puede
  redefinirla» en cuanto la del sistema existe.

> **DISCREPANCIA CON EL HALLAZGO, MEDIDA.** N6 pedía escribir que el caso
> alcanzable es «una segunda fila del SISTEMA con la misma clave, porque no hay
> UNIQUE sobre `(organizacion_id, clave)`». **Eso es falso**: existe
> `uq_catalogo_atributo_clave` (V48), `UNIQUE` sobre
> `(COALESCE(organizacion_id, 0), clave)`, que impide exactamente esa segunda fila.
> Medido en `pg_indexes` de `controllocal_dev`. No se sustituye una justificación
> falsa por otra: el comentario dice el motivo que sí se sostiene, y nombra el caso
> que el filtro no defiende **porque no existe**.

**Y S11 necesitó saltarse esa guarda para construir su estado**, cosa que esta
evidencia no decía. Medido hoy contra `controllocal_dev`:

```
ERROR:  La clave "estado_ocupacion" es del catalogo comun (tipo LISTA): una
        organizacion no puede redefinirla. Usa esa clave tal cual, o elige un
        nombre propio.
CONTEXT:  PL/pgSQL function exigir_catalogo_no_sombrea_al_sistema() line 26
```

Es decir: el estado que S11 monta sobre la copia desechable `v84_h7` **no es
alcanzable por la puerta normal** mientras la fila del sistema exista, así que hubo
que rodear la guarda para fabricarlo. Un sabotaje que exige rodear una guarda
prueba la comprobación **y** demuestra que la guarda está puesta; lo que no puede
es presentarse como un estado que el sistema alcanzaría solo. Eso es exactamente lo
que corrige la justificación de arriba.

### 13.6 Deudas registradas, sin tocar código

N3 (la quinta superficie: una clave **ESTRUCTURAL** retirada perdería su valor en
la ficha), N7 (`paraLeer` no filtra `sujeto`), N8 (la 91 no correlaciona el rastro
con el valor vigente), N9 (`UnSoloLectorPorSujetoTest` no ve la dependencia nueva)
y la **comprobación 78** en copias de `controllocal_repositorios`: todas anotadas
con ruta, medición y condición de disparo en `pendientes-brox.md` §2.3 quater.

**N3 no cambia el código, y sí cambia el javadoc.**
`AtributosGobernados.definicionesParaLeer` afirmaba sin matiz «Retirar la pregunta
no puede degradar la respuesta», y para las ESTRUCTURALES es **falso**: su valor lo
inyecta `LectorPorAutoridad.armar` recorriendo el mapa ya filtrado por `activo`, y
`definicionesParaLeer` llega después. Hoy no se retira ninguna ESTRUCTURAL, así que
**no hay dato perdido**; el javadoc dice ahora el límite exacto y remite a la
deuda.

### 13.7 Cierre de la tercera tanda — lo que se ejecutó

`TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_repositorios`,
JDK 21, `CONTROLLOCAL_CIERRE=1`.

| suite | tests | resultado |
|---|---|---|
| `OcupacionYServiciosIntegrationTest` | **15** (una nueva: N4) | ✅ |
| `ConservacionDeLaEdicionIntegrationTest` | 48 | ✅ |
| `CatalogoQueHablaIntegrationTest` | 38 | ✅ |
| `SujetoDelDatoIntegrationTest` | 30 | ✅ |
| `AislamientoDePruebasTest` / `GateDeCierreTest` / `UnSoloLectorPorSujetoTest` | 17 | ✅ |
| **total** | **148** | **0 fallos, 0 errores, 0 saltados** |

Gate SQL: `controllocal_dev` **98/98 `EXIT=0`**; `controllocal_repositorios`
**97/1** (la 78, de base).

**Estado del legado en la base de pruebas**, antes y después de la tanda:

```
antes de la tanda          322 filas   (283 «Agua, luz y desague» + 39 «agua y desague»)
tras el DELETE mal acotado 251 filas   ← 71 perdidas por error del constructor
tras reponer desde rastro  322 filas   (283 + 39, corpus IDÉNTICO al de partida)
tras la corrida dirigida   326 filas   (322 + 4 sembradas por el fixture, todas
                                        anteriores a la frontera del linaje)
en controllocal_dev          0 filas   (sin cambio: la suite no escribe en dev)
```

**No se ejecuta el reactor completo ni `Verificar-Cierre.ps1`**: la corrida larga
la ordena CONTROL. El corte **no** se declara cerrado y **no** se abre 5B.

---

## 14. Correcciones de la TERCERA auditoría (2026-08-25, cuarta tanda)

La tercera auditoría declaró el candidato **apto para corrida larga** y midió por
su cuenta N1, N2, N4, N5, N6, la mordida del legado con cuatro sabotajes propios,
la reposición del incidente de datos y que `V84` sigue intacta y con checksum
idéntico en las dos bases. Quedaron **cuatro menores y dos observaciones**, todas
de una o dos líneas. **Ninguna cambia comportamiento**, y esta tanda no lo hizo:
`V84` no se toca, ni el servicio, ni el lector, ni el predicado del legado.

### 14.1 N10 · el mecanismo de cobertura de N2 era **inobservable**, y el gate lo afirmaba

**Lo medido.** El informe formateaba con `rpad(prueba, 62)` y `rpad` **trunca**.
El nombre de la 92 medía **115 caracteres**, así que el sufijo
`(legado realmente presente en esta base: N filas)` **no se imprimía nunca**; y
como el script termina en `ROLLBACK`, la tabla `resultado` tampoco se podía
consultar después. La cifra era literalmente inobservable — y el gate y esta
evidencia la daban por informada. No era sólo la 92: **siete** nombres salían
cortados.

```
 n  | len |                        prueba (medido antes del arreglo)
----+-----+------------------------------------------------------------------
 33 |  67 | M4 ninguna propiedad tiene dos encargos vivos de la misma operacion
 42 |  63 | M5 rechaza condiciones de compraventa en expediente de alquiler
 77 |  63 | 4P despues del cutover ninguna condicion del encargo sin linaje
 88 |  65 | 5A gas distingue la red de la calle del papel de la concesionaria
 91 |  75 | 5A ningun inmueble con legado recibio un servicio sin que nadie lo afirmara
 92 | 115 | 5A CONTROL el predicado del legado caza una traduccion sin linaje (...)
 93 |  67 | 5A CONTROL y el savepoint devolvio servicios_disponibles a retirada
```

**Lo que entró.** Tres cosas, y ninguna toca la mordida de la 91/92:

1. `resultado` gana una columna **`nota`**, y `pg_temp.comprobar` un cuarto
   parámetro opcional para escribirla. La cifra del universo viaja ahí, en
   **columna propia**, no en el nombre.
2. El informe **deja de usar `rpad`**: rellena con `repeat(' ', greatest(2, ...))`,
   así que un nombre largo **desalinea** la fila pero ya **no puede perder su
   cola**. La truncadura silenciosa deja de ser posible por construcción.
3. Dos comprobaciones nuevas, las **99** y **100**, con una única cifra de ancho
   (`\set ANCHO_PRUEBA 78`) compartida por el informe y por la que lo vigila, para
   que no puedan separarse.

**Y ahora se lee.** El contraste entre las dos bases —que era justo lo que N2
denunciaba y no se veía— sale impreso:

```
controllocal_dev           92  5A CONTROL el predicado del legado caza una traduccion sin linaje   OK   legado realmente presente en esta base: 0 filas
controllocal_repositorios  92  5A CONTROL el predicado del legado caza una traduccion sin linaje   OK   legado realmente presente en esta base: 328 filas
                          100  INFORME ningun nombre de comprobacion se sale del ancho             OK   el mas largo mide 75 de 76
```

**Sabotaje de las dos comprobaciones nuevas** (gate real, `controllocal_dev`, una
sola a la vez, revertida y comprobada con `git diff`):

| # | defecto introducido | exigido | obtenido |
|---|---|---|---|
| **G4** | se alarga el nombre de la 98 hasta **80** caracteres | ROJO | **99/1** · `100 FALLO - un nombre no cabe en el ancho del informe...` · `el mas largo mide 80 de 76`. Y la 98 salió **entera**, desalineada: el informe ya no corta |
| **G5** | el control del legado inserta `nota = NULL` (deja de declarar su universo) | ROJO | **99/1** · `99 FALLO - el control del legado no dejo dicho cuantas filas de legado hay en esta base` |

Revertidos los dos: **100/100 `EXIT=0`** en `controllocal_dev`;
`controllocal_repositorios` **99/1**, con la **78** como único rojo y **de base**
(§13.6). `git diff` sin residuo de sabotaje.

### 14.2 N11 · número de comprobación equivocado

`OcupacionYServiciosIntegrationTest` citaba la **78** para «después del cutover
ningún hecho del inmueble sin linaje». Esa es la **76**; la 78 es «ninguna columna
estructural sin linaje». Importa porque la 78 es justo la que está **roja de base**
en `controllocal_repositorios`: citarla mal apunta la deuda equivocada. Una
palabra. El comentario gemelo del gate ya decía 76.

### 14.3 N12 · dos atribuciones de autoridad que el barrido de N1 no cazó

El barrido de N1 buscó **enumeraciones** de documentos gobernantes; estas dos son
**atribuciones puntuales**, y las dos son de este corte:

| dónde | decía | dice |
|---|---|---|
| `pendientes-brox.md` §9.4 | «El `encargo-corte-5-terreno.md` prepara el siguiente corte, **pero no lo abre**» | es el encargo del **corte en curso** —congelado, 5A ejecutándose contra él— y, como todo `encargo-*`, **ejecuta** lo que las decisiones y el mapa gobiernan |
| `auditoria-profundidad-inmobiliaria.md:5-7` (introducida por `8048006`) | «para Corte 5 **gobiernan** `decision-estado-ocupacion-en-los-siete.md` y `encargo-corte-5-terreno.md`» | gobierna la **decisión**; el encargo la **ejecuta** |

**Barrido de esta corrección** (`rg`, nunca `grep -iF`): `rg -n "gobiern..."` sobre
`docs/ai/*.md` cruzado con `encargo|i0-|north-star|auditoria-profundidad`. Queda
**una tercera** ocurrencia, y **no se toca**: `encargo-corte-3-vivienda.md:8`
(«el encargo que gobierna el Corte 3 es ...»), dentro del banner de incidente que
documenta un hecho **fechado** del 2026-08-24 sobre un corte **cerrado**. Es
registro histórico, no estado presente. **Se declara aquí para que CONTROL
decida**, no se reescribe.

### 14.4 N13 · el fixture fabricaba una fila temporalmente imposible

**Lo medido.** `sembrarLegadoAmbiguo` fechaba el legado en
`frontera_de_linaje() - 1 day`, pero la propiedad se acababa de crear con
`registrarTerreno()`, o sea **después** de la frontera: el atributo quedaba ~5 días
**anterior a su propia propiedad**. Esas 4 filas eran las **únicas de toda la
tabla** con `fecha_creacion < propiedad.fecha_registro`. Esquivar el dato imposible
que la **76** sí ve fabricando otro que hoy nadie mira no es esquivarlo.

**Lo que entró.** El mismo `DO` envejece también `propiedad.fecha_registro`
(`frontera - 2 days`), y el orden queda entero: **propiedad → legado → frontera**.
Una aserción nueva en el propio fixture lo exige, así que no puede volver.

**Lo que eso cuesta, dicho.** La propiedad sembrada **sale del universo de la
comprobación 78**, que se mide sobre las registradas *después* del cutover. Es
coherente —una propiedad con legado previo al cutover es anterior al cutover— y
**no tapa nada**: medido el 2026-08-25 sobre `controllocal_repositorios`, lo que
tiene roja la 78 es `PISO` escrito sin linaje por **otra** suite (54 propiedades,
§13.6), ninguna de ellas un `Caso 5A TERRENO`; y los otros siete
`registrarTerreno()` de esta suite siguen entrando en ese universo.

**Antes / después** en `controllocal_repositorios`:

```
filas con fecha_creacion < fecha_registro de su propiedad
  antes de la tanda            4   (PROP-8681, PROP-8682, PROP-8771, PROP-8772)
  aportadas por esta corrida   0   <- las dos nuevas (PROP-8804, PROP-8805) nacen
                                      con fecha_registro = frontera - 2 dias
```

Las 4 anteriores **se declaran como residuo y no se reparan a mano**
(`pendientes-brox.md` §2.3 quinquies): son datos de prueba en una base de pruebas,
y el mecanismo que las producía ya no existe.

### 14.5 O2 · cifra que se auto-invalidaba

El javadoc del fixture decía «las **251** filas anteriores a la frontera». Cada
corrida de esa misma prueba añade filas: eran 255 cuando lo midió la auditoría y
328 al cerrar esta tanda. **La cifra sale del javadoc**: la mide el propio caso
antes de afirmar nada, y el gate la imprime en la columna `nota` de la 92. Y no se
sustituye por una invariante falsa: medido, `servicios_disponibles` tenía **326
filas totales y 255 anteriores a la frontera** en esa base, así que «todas son
anteriores» **no** es cierto y no se escribe.

### 14.6 O3 · deuda registrada, no corregida

En `pendientes-brox.md` §2.3 quinquies, junto a `N8`:

- **N14** — las comprobaciones **76, 77 y 78** corren sobre **universo vacío** en
  `controllocal_dev`, que es la base del gate de cierre. Medido el 2026-08-25:
  `atributo_propiedad` 76 filas / **0** posteriores a la frontera;
  `atributo_encargo` **0** filas en total; `propiedad` **0** registradas después de
  la frontera. Es la ceguera de N2 en la familia del linaje, **preexistente**.
- **N15** — **acumulación entre suites**: cada corrida deja 2 filas permanentes de
  legado. Si otra prueba llegara a escribir `agua_desague`/`energia_electrica`
  **sin rastro** sobre una propiedad con legado, la 91 y `elLegadoNoSeTradujo`
  dependerían del orden de ejecución. **Hoy no ocurre**, y se comprobó por qué: los
  únicos otros escritores de esas dos claves pasan por el Core —dejan rastro— o
  esperan que el INSERT **falle**.

### 14.7 Cierre de la cuarta tanda — lo que se ejecutó

| qué | resultado |
|---|---|
| `OcupacionYServiciosIntegrationTest` (única suite tocada) | **15/15**, 0 fallos, 0 errores, 0 saltados |
| Gate SQL · `controllocal_dev` | **100/100 · `EXIT=0`** |
| Gate SQL · `controllocal_repositorios` | **99/1**, único rojo la **78**, de base |
| Sabotajes G4 y G5 | ROJO exigido y obtenido; revertidos; `git diff` sin residuo |
| `V84` | **no se toca** — ni el fichero ni su checksum |
| comportamiento | **sin cambios**: migraciones, servicio, lector y predicados intactos; lo tocado son el formato del informe del gate, dos comprobaciones nuevas del gate, la fecha del fixture y documentación |

**No se ejecuta el reactor completo ni `Verificar-Cierre.ps1`**: la corrida larga
la ordena CONTROL. El corte **no** se declara cerrado y **no** se abre 5B.
