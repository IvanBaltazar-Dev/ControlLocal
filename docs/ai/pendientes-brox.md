# Pendientes de BROX — inventario completo

**Qué responde:** todo lo que queda por hacer, medido contra el repositorio y la
base de datos reales, no contra lo que los documentos dicen que falta.

**Hecho el 2026-08-22**, justo después de cerrar V78 (Corte 1, mitad de sujeto).
Estado del árbol: rama `feat/modelo-universal-y-autoridad-del-dato`, commit
`48e8ede`, migraciones hasta **V78**.

> **Corregido el 2026-08-23 con el Corte 2 (`V79`)**, y sólo en lo que dejó de
> ser cierto: la rama **ya está publicada** (§0.1 y §10), la identidad registral
> **ya existe** (§2.5), las suites de integración son **20** y no 22, y las
> cifras de impacto de §2.1 **salían de `TEST_DB_URL`**, no del mercado. Lo
> demás se deja como se midió.

**Cómo se hizo:** recorriendo `docs/ai/` (66 documentos), `backend-spring/`,
`frontend-angular/src/`, `backend-spring/verificacion/` y las dos bases
PostgreSQL vivas. Cada fila que dice «abierto» se comprobó; las que los
documentos daban por pendientes y **ya estaban hechas** están marcadas como
tales y se corrigieron en su documento de origen.

**Este documento no sustituye a nadie.** `mapa-ejecucion-brox.md` sigue siendo
la portada («dónde estamos») y
`checklist-captura-moat-e-inteligencia-inmobiliaria.md` los requisitos de cierre
de la etapa en curso. Esto es el **inventario transversal**: lo que ninguno de
los dos recoge entero porque está repartido en quince documentos.

---

## 0. Lo primero, porque no es técnico y bloquea todo lo demás

| # | Pendiente | Medido | Por qué importa |
|---|---|---|---|
| **0.1** | ~~**43 commits sin publicar.**~~ ✅ **RESUELTO el 2026-08-23**: la rama está publicada | `git rev-parse @{u}` → `origin/feat/modelo-universal-y-autoridad-del-dato`; `git ls-remote` → la rama existe en el remoto y `origin/main..origin/<rama>` = **48** | Eran **48** contra lo que existe en GitHub, no 43: las 43 se contaron contra `main` **local**, que está 4 commits por delante de `origin/main`. Y las suites de integración son **20**, no 22 — `GateDeCierreTest` las inventaría y `Verificar-Cierre.ps1` comprueba que se ejecutaron |
| **0.2** | **Rotar el secreto JWT y las credenciales RDS publicadas** en `2832a9b` | `origin/main` = `2832a9b` | El commit que las publicó **es la cabeza de `main` en GitHub**. Y `backend-spring` reutiliza ese mismo secreto de firma. Sin GlassFish al que preservar compatibilidad, rotarlo es un cambio de configuración |
| **0.3** | **Decidir qué pasa con `origin/main`** | — | La historia pública se quedó en la v1. O se fusiona lo nuevo, o se declara que `main` no representa el producto |

> **0.1 y 0.2 están relacionados y el orden importa**: publicar la rama sin
> rotar antes no empeora nada (el secreto ya está publicado desde `2832a9b`),
> pero rotar y no publicar deja la rotación también en un solo disco.

---

## 1. Lo que bloquea la etapa en curso — E3 · Negociación

E3 es la única etapa 🟡 SIGUIENTE y está **bloqueada por tres decisiones de E0**
que hay que tomar **antes** de escribir la primera fila de oferta, porque las
tres cambian el dato que se persiste (`decision-hito-oferta-de-demanda.md` §«Dos
cuestiones abiertas»).

| # | Cuestión | Propuesta que ya existe en el documento | Estado |
|---|---|---|---|
| **1.1** | ¿Contra qué precio pedido se congela el *snapshot* de la oferta: `U` (lo autorizado, privado) o `P` (lo que el mercado vio)? | último `P` vigente, con caída a `U` si no hay anuncio, **dejando constancia de cuál se usó** | ⬜ sin decidir |
| **1.2** | ¿Dónde vive `O` sin mezclar dos negociaciones del mismo inmueble? `PrecioPropiedad` cuelga de la **propiedad**, y con dos interesados la serie interleava dos mesas distintas | referencia a la **oportunidad** | ⬜ sin decidir |
| **1.3** | Dónde consta lo declarado (menor) | — | ⬜ sin decidir |

**Además, y es nuevo desde que se escribió esa decisión:** el histórico
económico tampoco sabe **de qué encargo** es. Con venta y alquiler vivos a la
vez sobre la misma propiedad, las dos series se mezclan. Está anotado en
`decision-modelo-universal-propiedad-operacion.md` §2 como una de las seis cosas
que faltaban de verdad, y sigue abierto.

---

## 2. Profundidad inmobiliaria — lo que queda de los cortes de catálogo

Fuente: `auditoria-profundidad-inmobiliaria.md`. La cadena real de migraciones y
lo que ocupó cada una está en su §6. **La siguiente libre es `V81`**: `V79` la ocupo el Corte 2 el 2026-08-23 y `V80` el Corte 3 el 2026-08-24.

### 2.1 Corte 1 · mitad de PROFUNDIDAD ⬜ — APLAZADO

V78 cerró la mitad de **sujeto** (¿de quién es cada clave?). Queda la mitad de
**profundidad** (¿a qué tipos aplica y con qué exigencia?), que está medida e
inerte y sólo espera decisiones de negocio:

| Cambio | Medido | Qué falta decidir |
|---|---|---|
| `banos` → L, O, A | 406 valores, todos en C y D; cero en L, O, A | la exigencia |
| `zonificacion` → O | 584 valores en A, C, L, T; las 72 oficinas sin ninguno | ídem |
| `pisos_edificacion` → D, O | la clave más estrecha: **una sola fila**, en C | ídem |
| `frente` → C | 475 valores en A, L, T; ninguna casa | ídem |
| `interiorUnidad` / `nombreEdificioGaleria` → A | no son claves de catálogo: dos `Set.of` en `GuionRegistroPropiedad`, sin migración | ídem |

> **El flip a PUB es el cambio de mayor impacto operativo del corte** — y las
> dos frases con que se describía estaban mal las dos. Corregidas el
> 2026-08-23:
>
> 1. **Las cifras no eran del mercado.** «1 048 departamentos», «781 D / 407 L»
>    y «806 D / 139 C» salen de `controllocal_repositorios`, que **es
>    `TEST_DB_URL`**: la base donde cometen las 20 suites de integración, con
>    **2 871 propiedades** y **757 claves `zz_*`** de residuo. El corpus real es
>    `controllocal_dev`: **26 propiedades** —1 C, 1 D, 21 L, 2 O, 1 T— y 74
>    valores escritos. En `dev` no hay 1 048 departamentos: hay **uno**.
> 2. **«Prácticamente ningún local volvería a ser publicable» se queda corto, y
>    no es una figura**: `PUB` cuelga de `exigirPublicable`, que **lanza** y sale
>    como **HTTP 400** (`PublicacionServiceImpl:186`, `ManejadorErroresApi:45`).
>    Hoy las 26 propiedades reales pasan el gate; con esas claves en PUB
>    dejarían de poder anunciarse las 26. Y **PUB no informa de nada**: no
>    existe ninguna superficie del cable que reporte una PUB de la PROPIEDAD.
>    Detalle y evidencia en `auditoria-profundidad-inmobiliaria.md` §6 bis.
>
> **O se escalona, o se acepta y se dice.** Hoy **ninguna** clave del sistema
> tiene exigencia PUB — tampoco las seis que sembró `V79`, que entraron OPC a
> propósito.

### 2.2 Las conversiones de tipo, bloqueadas por una invariante deliberada

`tg_catalogo_sistema_inmutable` **prohíbe cambiar el `tipo_dato` de una clave
del sistema** («los valores ya escritos dejarían de significar lo mismo»). No es
un obstáculo a rodear: es la garantía. Las cuatro necesitan **otra** vía —clave
nueva + migración de datos + retirada de la vieja—, y cada una tiene además su
propio bloqueo de dato:

| Conversión | Bloqueo adicional | Dónde va |
|---|---|---|
| `cuota_mantenimiento` DECIMAL → **IMPORTE** | 784 filas con `valor_moneda` NULL al 100 %, y **ninguna fuente de la que deducirla**: `moneda_referencial` es la moneda de una renta, no la de un gasto de junta; el mismo importe 350 aparece bajo PEN (237 veces) y USD (56); el encargo vivo tiene 74 casos con monedas en conflicto | cuando la moneda se **declare** |
| `rubro_permitido` TEXTO → **LISTA_MULTIPLE** | 22 valores libres distintos, varios no mapeables con certeza; y cambia el almacén (los valores pasan a `atributo_propiedad_opcion`) | Corte 3/4, con vocabulario reconciliado |
| `zonificacion` TEXTO → **LISTA** | los 584 valores mapearían al 100 %; **el problema es el vocabulario nuevo**: derivarlo de lo observado daría 4 opciones y Lima tiene decenas (CV, CM, CE, RDB, I1–I4, OU, ZRP, ZTE…) | cuando salga de los planos de zonificación |
| `banos` DECIMAL → **ENTERO** + `medios_banos` | ~~`medios_banos` es clave nueva~~ ✅ **nació en `V80`** (Corte 3, 2026-08-24), y con él **la convención de `banos` ya está publicada en su `ayuda`** — un baño completo cuenta 1, un medio baño sin ducha cuenta 0.5. Era la precondición que faltaba: la migración de datos es determinista (379 de 406 valores son `.5` exactos) pero descansaba en una convención que nadie había escrito. **El estrechamiento en sí sigue pendiente** | corte propio, ya **desbloqueado** |

### 2.3 `servicios_disponibles` — una LISTA muda

`tipo_dato='LISTA'` con **cero opciones sembradas**. El trigger sólo valida
pertenencia *si la clave tiene vocabulario*, así que acepta cualquier cadena; y
`MotorDeCaptura.controlDe` deriva el control de si hay opciones, así que la
dibuja como **texto libre**. El dato entra y no compara con nada.

- **No se le inventa vocabulario** ni se le cambia el tipo: es hecho de la
  PROPIEDAD y está bien colocado.
- Sus reemplazos (`agua_desague`, `energia_electrica`, `gas`, cada uno con su
  tercer estado «con factibilidad aprobada») **nacen en el Corte 5**, y sólo
  entonces pasa a `activo = false`. Retirarla antes dejaría varios cortes en los
  que BROX deja de capturar un hecho que hoy captura.
- **Y falta su guarda gemela**: la comprobación «ninguna LISTA sin vocabulario»
  que V77 escribió sólo mira `sujeto = 'ENCARGO'`. La PROPIEDAD no la tiene — por
  eso esta clave sobrevivió muda. Extenderla exige antes darle vocabulario, así
  que **van en la misma tanda**.

### 2.4 Los hechos que faltan de un par deliberado — quedan **dos**

El guard de pares vigila que un hecho y su condición no compartan sujeto, y V78
añadió que el hecho no llegue menos lejos que su condición. Eran cuatro; **`V80` cerró el primero y `V81` el segundo**, y quedan **dos** —los
dos del Corte 5—, donde el pacto sigue siendo el único sitio donde cabe la verdad
física:

| Hecho que falta | Su condición, que ya existe | Corte |
|---|---|---|
| ~~`mascotas_reglamento`~~ ✅ **HECHO 2026-08-24 · `V80`**, y nació en **C y D** | `mascotas_aceptadas` | ~~3~~ |
| ~~`nivel_implementacion`~~ ✅ **HECHO 2026-08-24 · `V81`**, en **A, L y O** | `se_entrega_implementado` | ~~4~~ |
| `estado_ocupacion` | `entrega_desocupado` | 5 |
| `lote_minimo_normativo` | `acepta_venta_fraccionada` | 5 |

> Que falte el lado PROPIEDAD **no impedía sembrar el lado ENCARGO** —la
> condición es cierta por sí sola— y por eso se hizo. Lo que hay que recordar al
> construirlos: **tienen que nacer cubriendo la aplicabilidad de su condición**,
> o el gate de V78 lo dirá.

> **Y lo dijo.** El plan de la auditoría daba `mascotas_reglamento` como «D».
> Medido antes de sembrar: su condición se pacta en `C/A` **y** `D/A`, así que
> nacer sólo en D habría roto `V80` en su propia guarda — se comprobó
> simulándolo, y la rompe. Nació en **C y D**. La lección para los tres que
> quedan es literal: **la aplicabilidad del hecho no se elige, se lee de la de su
> condición.**

### 2.5 Cortes 2 a 7 ⬜

| Corte | Qué trae | Migración |
|---|---|---|
| ~~**2 · Identidad registral**~~ ✅ **HECHO 2026-08-23** | `partida_registral` y `oficina_registral` como **estructurales**, más `independizado`, `cargas_gravamenes`, `area_segun_partida`, `declaratoria_fabrica` — **las seis OPC**, ninguna PUB. Se adelantó al resto del Corte 1 porque era un hueco estructural que se podía modelar **sin inferir nada del corpus contaminado**. Lo que queda fuera y sigue abierto: la promoción OPC→PUB, y el *snapshot* fechado de `condicion_compraventa.partida_registral`, que nace con el expediente de compraventa (bloque 6) | **V79** ✅ |
| ~~**3 · Vivienda (D, C)**~~ ✅ **HECHO 2026-08-24** | **30 claves** OPC —tipología, conservación, etapa de entrega, ascensores, vigilancia, áreas comunes, vista, bloque de baños/servicio, áreas exteriores, depósitos, torre— con 9 vocabularios (49 opciones) y 68 filas de aplicabilidad. Ninguna ALT, ninguna PUB. **Heredado a medias a propósito**: `medios_banos` nació y la convención de `banos` ya está publicada, pero **el estrechamiento sigue pendiente** (§2.2). Fuera: la promoción OPC→PUB, `familia` para agrupar un formulario que pasa de 25 a 55 campos (va con el corte del SPA), y `estacionamiento_independizado`, que el Corte 6 sustituye con `unidad_relacionada`. Un commit previo sin migración (`3.a`) arregló el gate `.sql`, **rojo desde `V77` y nunca ejecutado** | **V80** ✅ |
| ~~**4 · Comercial (L, O, A)**~~ ✅ **HECHO 2026-08-24** | **39 claves** con 18 vocabularios (83 opciones) y 71 filas de aplicabilidad. **`tipo_acceso` entró `ALT` en `L`** —decisión del titular— y con ella **las publicables pasaron de 26 a 5**: los 21 locales salen del mercado hasta que se visiten, y **no se rellenó el dato en ninguno**. **`V82` la corrigió a `PUB` el mismo día** para que un local se pueda **registrar** sin el dato, sin mover la publicabilidad (§2.5 ter). Las otras 38, `OPC`. Termina además **las instalaciones de la vivienda** (`gas`, `agua_caliente`) que §3.5 mezclaba y el Corte 3 excluyó. Fuera: la retirada de `apto_licencia_funcionamiento`, que necesita migración de datos | **V81** ✅ |
| **5 · Terreno (T)** | parámetros urbanísticos, servicios con su tercer estado, vía y ocupación. **Hereda**: los tres reemplazos de `servicios_disponibles` | — |
| **6 · Unidades relacionadas** | una unidad con partida propia **es una Propiedad relacionada**, no un escalar dentro de un EAV: `unidad_relacionada`, códigos `E`/`B`, `unidadesRelacionadas[]` | — |
| **7 · Demanda y matcher** | unificar el vocabulario de tipo (hoy ALMACÉN y `DEPOSITO_ALMACEN` —el mismo concepto— se declaran no comparables), permitir que un requerimiento pida atributos gobernados, y **arreglar el sesgo**: un dato faltante hace que el criterio NO APLIQUE sin castigar el puntaje, así que **la propiedad peor capturada obtiene mejor puntaje** | — |

### 2.5 bis Deudas de base que el Corte 4 pagó

- **D-BASE-4 · `area_minima_arrendable.unidad` estaba en `m2`, sin acento.**
  Olvido de `V77`, que sí aplicó el `UPDATE` de acentos a sus hermanas.
  **Pagada en `V81`**: era la última clave del catálogo con `unidad = 'm2'` y
  ahora quedan **cero**. No se tocó ningún valor escrito — es el rótulo de la
  unidad, no el dato.
- **D-BASE4-1 · el gate de ida y vuelta no crecía con el catálogo.**
  `ConservacionDeLaEdicionIntegrationTest` prometía en su javadoc «la carga más
  ancha que el catálogo permite **hoy**» y llevaba listas escritas a mano.
  Medido antes de `V81`: **L 13 de 24, O 10 de 28, A 12 de 22** — treinta y una
  claves sin probar, siete de ellas sembradas por `V80` para el local. **Pagada
  en `V81`**: los seis casos llevan ahora todo lo que su tipo admite
  (L 40 · O 47 · A 50 · D 46 · C 35 · T 14) y **un test nuevo lo comprueba contra
  el catálogo**, así que la promesa dejó de depender de que alguien se acordara.

### 2.5 ter Lo que el Corte 4 dejó abierto — la decisión ya está tomada, quedan dos deudas

- ~~**DECISIÓN PENDIENTE DEL TITULAR · `ALT` tampoco deja registrar**~~ ✅
  **RESUELTA 2026-08-24 · `V82`.** El titular decidió que `tipo_acceso` quede en
  **`PUB`**: un local sin ese dato **se registra, se edita, se conserva y sirve
  para inteligencia — pero no se publica**. No hizo falta tocar Java: `V72` ya
  había construido ese nivel (`Exigencia.bloqueaAlta()` mira sólo `ALT`). **La
  publicabilidad no se movió** —siguen 5 de 26 y las mismas 21 bloqueadas—, que
  es la prueba de que el cambio hizo lo suyo y sólo eso. Evidencia:
  `verificacion/evidencia/2026-08-24-correccion-tipo-acceso-pub.md`.

  El diagnóstico que llevó a la decisión, tal como se midió: `exigirObligatorios` corre en
  `PropiedadUniversalServiceImpl.registrar:231` y **`editar:437` no lo llama**:
  las 21 propiedades ya existentes se siguen editando y **ninguna se ve
  afectada**, pero **desde el 2026-08-24 no se da de alta un local nuevo sin
  `tipo_acceso`**. No es un comportamiento nuevo —`metraje_total`, `dormitorios`
  y `zonificacion` ya lo tenían— y el Corte 4 **no relajó nada** para esquivarlo.
  El titular consintió sobre «no se puede anunciar»; sobre esto **no se le
  preguntó**. Roza `V75`/`V76` (registrar no es encargar; BROX conoce inmuebles
  que no gestiona), pero **el hueco es más estrecho de lo que parece**: hoy hay
  **cero** propiedades con `origen_incorporacion = OBSERVACION`, y `tipo_acceso`
  —a pie de calle, esquina, galería, pasaje— es justo lo que se ve desde la
  calle. Lo bloqueado no es el inmueble avistado, sino **el reportado por
  teléfono o desde un anuncio, sin nadie delante**. Separar «obligatorio para
  publicar» de «obligatorio para registrar» es **un corte propio**; rebajar la
  exigencia, no. Medido en §9 de
  `verificacion/evidencia/2026-08-24-corte-4-comercial.md`.
- **El test de completitud es ahora una obligación permanente, y crece.** Desde
  `V81`, **todo** corte de profundidad debe extender los siete casos de
  `ConservacionDeLaEdicionIntegrationTest` o el build rompe. Es exactamente lo
  que se quería, pero el fixture lleva ya **220** valores escritos a mano.
  Cuando el catálogo se acerque a 200 claves habrá que decidir si sigue a mano o
  **se deriva de `/captura/definicion`**, como ya hace `e2e-editor-universal`.
  Anotarlo ahora es más barato que descubrirlo en el Corte 7.
- ~~**DEUDA NUEVA DE `V82` · el bloqueo es real y NADIE lo anuncia**~~ ✅
  **SALDADA 2026-08-24**, en el corte corto que siguió a `V82` y **sin migración**.
  La PROPIEDAD reporta ahora su propia deuda en
  `PropiedadResponse.faltanParaPublicar`, con el mismo nombre con que el ENCARGO
  reporta la suya, y sale del **mismo método de dominio que decide el rechazo**
  (`faltantesDePropiedadParaPublicar`), no de una segunda matriz. Y la capacidad
  `publicacionGestionable.permitida` **dejó de contradecirla**: se deriva de las
  dos listas que la ficha ya calcula. Medido sobre las 26: **21 bloqueadas, 21
  con causa visible, 8 encargos vivos con `permitida = false`, y cero casos de
  `permitida = true` con faltantes conocidos**. Evidencia:
  `verificacion/evidencia/2026-08-24-senal-pub-visible.md`.

  El diagnóstico original, tal como se midió el 2026-08-24 **y antes de que el
  corte de la señal lo resolviera** — su presente ya no es el de hoy:

  > Al pasar a `PUB`, `tipo_acceso` **desapareció de `atributosQueFaltan`** (que
  > sólo lleva `ALT`) y **no aparece en `faltanParaPublicar`**, que es del sujeto
  > ENCARGO y por el guard 2.5 de `V78` **no puede llevar una clave de la
  > PROPIEDAD**. El encargo de la corrección predijo «7 avisan / 14 no»; **lo
  > medido es 21 → 0**. La barrera sigue en pie —publicar devuelve 400— pero
  > **ninguna superficie de lectura lo dice**. Hoy **no existe ningún sitio donde
  > una clave `PUB` de la PROPIEDAD se reporte**: construirlo es **un corte
  > propio**. El hueco queda fijado en
  > `CatalogoQueHablaIntegrationTest.elBloqueoNoSeAnunciaEnNingunaSuperficie`,
  > que se pondrá **rojo** el día que alguien lo construya — y esa es la señal.
- **El suelo del gate `.sql` sigue en 51 con 120 claves sembradas.** Es el
  límite honesto que su propio comentario declara desde `e8cfaa4`, y no es
  defecto de este corte — pero el margen ya es de **69 retiradas** antes de que
  salte. Quien vigila de verdad es la invariante de aplicabilidad, no el número.
- ~~**DEUDA NUEVA · la cuarta puerta de exposición no pregunta por la deuda de
  catálogo**~~ ✅ **SALDADA 2026-08-24 · y eran CINCO, no cuatro.** El preflight
  del microcorte encontró una quinta que no estaba en ningún inventario:
  **`sincronizar(idPropiedad, …)`** creaba la publicación, la dejaba en
  `PUBLICADO` y escribía el hito `P` **sin preguntar por una sola clave del
  catálogo** — residuo del formulario de la v1, borrada el 2026-08-08. **Las dos
  tenían cero consumidores de producción y ninguna estaba expuesta**, así que
  **se retiraron** en lugar de hacerlas delegar: una vía que delega sigue
  existiendo y puede desincronizarse en el próximo cambio; una que no existe no
  puede eludir nada. Quedan `crearEnEncargo` (crea) y `cambiarEstado` (publica),
  las dos con `exigirPublicable`, y `actualizar`, que **no toca el estado**. Lo
  fija `PuertasDePublicacionTest`, y se comprobó que muerde reintroduciendo la
  vía. Evidencia:
  `verificacion/evidencia/2026-08-24-puertas-de-publicacion.md`.

  El diagnóstico original, tal como se escribió:

  > `PublicacionServiceImpl.crear(idPropiedad, datos, actor)`
  > (`:135-147`) **no llama a `exigirPublicable`**: sólo comprueba
  > `exigirAlgunEncargo`. Las otras tres sí lo hacen — `crearEnEncargo:94` y
  > `cambiarEstado:326`, esta última en la transición a `PUBLICADO`, que es la que
  > expone al mercado.
  > **Hoy no es explotable y por eso no fue defecto del corte**: esa ruta **no está
  > expuesta** —el único endpoint de creación es
  > `POST /encargos/{idEncargo}/publicaciones`, que va por `crearEnEncargo`—,
  > verificado en los controladores y en la matriz.
  > Pero la cadena que el corte acaba de establecer —**regla → `faltanParaPublicar`
  > → `permitida` → acción visible**— tiene esa cuarta puerta sin guardar **en el
  > código**. El día que alguien la exponga, `permitida` diría `false` y la
  > publicación funcionaría igual. Se anota **ahora que la coherencia es
  > explícita**, no cuando alguien la abra.
- **Límite de cobertura, dicho en vez de presentado como total.** El barrido
  empírico del corte sólo ejercita **la rama de la PROPIEDAD**: las **112** filas
  de `catalogo_atributo_operacion` son **todas OPC**, así que hoy **ningún
  encargo puede tener un faltante ALT/PUB propio** y el escenario «el ENCARGO
  bloquea» **no es reproducible contra la cartera real**. Queda probado por
  construcción y por el test de integración, no por los datos. Cuando el Corte 5
  o el 7 siembren la primera condición ALT/PUB del ENCARGO, **ese escenario pasa
  a ser medible y hay que medirlo**.

### 2.5 quater Enriquecimiento de Propiedad — capacidad de producto, capa calidad/moat

**Decidido por el titular el 2026-08-24**, al aceptar la consecuencia de `V82`.

**La regla, primero, porque cierra una vía que parecía cómoda:**
**`atributosQueFaltan` NO debe usarse como mecanismo genérico de
enriquecimiento.** Es la lista de lo que **bloquea el alta** —sólo `ALT`— y nada
más. Colgar de ahí «lo que le falta de profundidad a un inmueble» volvería a
mezclar dos preguntas que `V72` separó a propósito, y acabaría empujando claves a
`ALT` para que se vean, que es exactamente el movimiento que el Corte 4 tuvo que
deshacer.

**Lo que queda registrado como capacidad pendiente**, distinta y con su propio
sitio: **profundidad / enriquecimiento de la Propiedad**, especialmente para
inmuebles con **`origen_incorporacion = OBSERVACION`**. Pertenece a la capa de
**calidad / moat**, no al motor de exigencias.

**Sus límites, explícitos:**

- **No cambia `ALT`/`PUB`/`OPC`** de ninguna clave.
- **No entró en `V82`** ni podía: `V82` fue una fila y dos columnas.
- No se implementa reutilizando `atributosQueFaltan`.

**Qué la separa de la deuda de `V82` de arriba**, porque se tocan y no son lo
mismo: aquélla es **deuda de publicación** —una clave `PUB` que bloquea y que
ninguna superficie reporta, y cuyo arreglo es exponer las `PUB` faltantes junto a
las `ALT` **sin fundir las dos listas**—. Ésta es **deuda de conocimiento**: qué
le falta por saber a un inmueble que nadie ha encargado todavía. La primera tiene
21 casos concretos hoy; la segunda no tiene ninguno todavía —hay **cero**
propiedades con origen `OBSERVACION`— y nace para cuando los haya.

**Los 14 locales sin encargo vivo no reciben señal artificial**: no tienen deuda
de publicación, y fabricarles una sería inventar una obligación que nadie pactó.

### 2.6 Y una decisión que el Corte 1 dejó explícitamente sin tomar

**El tipo `X` (OTRO) se está quedando sin preguntas.** Hoy tiene exactamente
tres claves aplicables y las tres son de `aplica_todos`; quitar dos lo dejaría
con `metraje_total`. El plan dice «no abrir X, auditarlo antes de decidir si
sigue existiendo», y ningún corte lo ha auditado. **Que sea una decisión, no un
efecto colateral.**

---

## 3. Modelo universal — lo que `decision-modelo-universal-propiedad-operacion.md` daba por faltante

De las seis cosas que §2 de esa decisión declaraba pendientes:

| | Estado hoy |
|---|---|
| Multi-titular (`propiedad.id_rol_propietario` 1:1 `NOT NULL`) | ✅ resuelto — `titularidad_propiedad`, y la columna admite NULL desde V76 |
| Atributos gobernados (era una tabla por subtipo) | ✅ resuelto — `detalle_local_comercial` retirada en V71 |
| **El histórico económico no sabe de qué encargo es** | ⬜ **abierto** — bloquea E3 (§1) |
| **El requerimiento habla alquiler comercial** (`renta_min/max`, `rubro` obligatorio, un solo `tipo_inmueble`) | ⬜ **abierto** — Corte 7 |
| **La compraventa no tiene expediente** | ⬜ **abierto** — bloque 6 del mapa |
| PostGIS y outbox de eventos | ✅ resuelto — `propiedad.ubicacion geography(Point,4326)` y el outbox existen |

---

## 4. Interfaz — lo que queda del corte de UI

Fuente: `auditoria-ui-brox.md` (medición del 2026-08-17) y
`auditoria-residuos-semanticos.md`, **re-medidos hoy**.

### 4.1 Ya hecho, y el documento no lo decía

- `estadoRitmo` y la clasificación por asunto (E2.6), `DEPENDE_DE_MI` (E2.2) y
  la cola del broker (E2.5): tres de los cinco `BACKEND_FALTANTE`.
- El literal `Panel` del cascarón y la pantalla «Catálogos del sistema»:
  retirados.
- El menú: Locales→Propiedades, Dashboard→Inicio.
- `local-form` y `local-detail`: **borrados**; el alta y el editor universales
  los sustituyen y salen del catálogo.

### 4.2 Abierto, medido hoy

| # | Pendiente | Medida de hoy |
|---|---|---|
| **4.1** | **`BroxPageHeader` no existe**: cada pantalla se pinta su propia miga de pan | **50** ficheros con `class="miga"` |
| **4.2** | **«ControlLocal» sigue visible al usuario** | **6** plantillas (login, recuperar-acceso, cambiar-contrasena, enrolar-mfa, agente-form, broker-form) + `<title>ControllocalWeb</title>` en `index.html` |
| **4.3** | **«Cierres exitosos» no se renombró a «Contratos»** | `propiedades-alquiladas.html`, título y miga |
| **4.4** | **`locales.service.ts` sigue vivo** y con él el modelo plano de L/O | lo usan 4 pantallas: `ficha-propiedad`, `captacion-detail`, `captacion-form`, `captacion-review` |
| **4.5** | **`ficha-propiedad`** (la de `captaciones/:codigo/ficha`) lee el modelo heredado, no el universal | `import ... from '../../core/api/locales.service'` |
| **4.6** | **`GET /inicio` compuesto** no existe: el Inicio se arma con varias llamadas | no hay `InicioController` |
| **4.7** | **Capacidades por sesión** no existen | sin `capacidades` en `web/` |
| **4.8** | Las dos uniones: **Interacciones** dentro del expediente · **Reportes** como pestaña de Indicadores | mantienen el menú en 15/17/19 en vez de 13/15/16 |
| **4.9** | Unificar tabla de bandeja, filtros, badges, timeline, KPI y tokens; `agente-form`+`broker-form`; las 4 parejas duplicadas | 13 veredictos `UNIFICAR` |
| **4.10** | Rediseñar estado activo del menú, iconografía, **móvil (drawer)** | 5 veredictos `REDISEÑAR` |
| **4.11** | Subtítulo del login («Gestión comercial de locales») y patrón de progreso por pasos en el resto de altas | deuda menor |

### 4.3 Y lo que la auditoría de UI declaró **no auditado**

Sigue sin auditarse, y conviene no confundirlo con «está bien»:

- **Accesibilidad** — ni contraste real, ni foco, ni lectores de pantalla.
- **Rendimiento percibido** — no se ha medido pintado ni tamaño de *bundle*.
- **Glosario de textos** — el vocabulario funcional único **no está escrito**.
- **Las pantallas de seguridad** (`seguridad`, `enrolar-mfa`, `perfil`) —
  inventariadas, flujo sin revisar.

---

## 5. Multi-tenancy — el diseño está cerrado, la ejecución no

`arquitectura-multitenancy-colaboracion.md` §12 declara **todas las preguntas
resueltas** (D-18 a D-26). Lo que queda es implementación:

| # | Pendiente | Medido hoy |
|---|---|---|
| **5.1** | **RLS no está activado en ninguna tabla.** D-24 dice activarlo **antes del segundo tenant** | `select count(*) from pg_class where relrowsecurity` → **0** |
| **5.2** | `BROX_LEGACY` sigue siendo el único tenant real | 4 organizaciones: `BROX_LEGACY` (26 propiedades) + 3 de prueba |
| **5.3** | Separar cuenta de acceso de persona (D-22): `cuenta_acceso` global | `usuario_organizacion` existe; `cuenta_acceso` **no** |
| **5.4** | `canal_whatsapp` (D-21) y la bóveda de identidad de red (D-18) | no existen |
| **5.5** | F0/Locales/F2 se construyeron sin contexto de organización → añadir el contexto explícito de D-20 | — |

> **Y un residuo:** en `controllocal_dev` viven tres tenants de prueba
> (`E2E-UNIVERSAL-A`, `E2E-UNIVERSAL-B`, `SIMULACRO-RECUPERACION`) con 6
> `persona_rol` y cero propiedades. `AislamientoDePruebasTest` impide que eso se
> repita, pero lo que ya entró sigue ahí.

---

## 6. Producción — nada se despliega en público antes de esto

### 6.1 Configuración e identidad

| # | Pendiente |
|---|---|
| **6.1** | Rotar JWT y credenciales (§0.2) |
| **6.2** | Separar los *seeds* de desarrollo de los datos reales. `ValidadorConfiguracionSeguridad` **se niega a arrancar `prod`** mientras alguna credencial sembrada siga viva — está bien que así sea, pero significa que hoy `prod` no arranca |
| **6.3** | *Bootstrap* inicial de una organización real (hoy sólo existe la de legado) |
| **6.4** | Configuración fuera de `localhost`, imagen productiva, TLS/proxy |

### 6.2 Respaldo y almacén (bloque 9)

De `backend-spring/operacion/README.md` §8, y ninguno bloquea a S0:

| # | Pendiente | Por qué importa |
|---|---|---|
| **6.5** | **Copia de los binarios del almacén** | `pg_dump` guarda las claves, no los archivos: una restauración deja la base íntegra y **los documentos ausentes** |
| **6.6** | **Copia fuera de la máquina** | hoy el destino por defecto es el mismo disco |
| **6.7** | **Cifrado del respaldo en reposo** | el *dump* lleva datos personales en claro |
| **6.8** | **Alerta cuando el respaldo falla** | un *backup* roto desde hace semanas parece uno que funciona |
| **6.9** | **Elegir el servidor S3** y migrar los binarios antes de cambiar el proveedor por defecto | los archivos viven en el volumen `controllocal_almacen`, no en ningún *bucket*: cambiar primero muestra un almacén vacío. La herramienta (`MigracionAlmacen`, modos `conciliar`/`migrar`) ya existe |

---

## 7. Verificación — el gate de cierre cubre 5 de 23 suites

Existen **23 suites E2E** (más `e2e-context.ps1`, que es soporte). La corrida de
cierre ejecuta **cinco**:

```
en el gate:  comision-movimientos · disponibilidad-contrato · f4-solicitud
             estabilizacion-alquiler · editor-universal

fuera:       demanda-busqueda · e4-dashboard · f3-demanda · f6-f7-alertas-tareas
             ficha-comercial · locales-busqueda · locales-listado · personas
             reportes-propietario · solicitudes-busqueda · sonda-transporte · v6
             s0-bloqueo · s0-contrasenas · s0-emergencia · s0-mfa · s0-roles
             s0-sesiones
```

**Qué significa:** una regresión en personas, demanda, indicadores, alertas o en
**cualquiera de las seis suites de seguridad** no la detecta la corrida de
cierre. No es un descuido gratuito —las tres suites de búsqueda miden p95 en
esta misma máquina y son frágiles frente a una compilación en paralelo—, pero
**hoy no hay ninguna corrida que las pase todas**, ni una periodicidad acordada
para hacerlo.

Pendiente: decidir un **ciclo largo** (todas las suites, sin nada más corriendo)
y con qué frecuencia se ejecuta.

---

## 8. Fuera de alcance declarado — que no vuelva a proponerse

No son pendientes: son cosas **decididas como fuera**, y conviene tenerlas juntas
para que nadie las reabra por descuido.

| Qué | Dónde se decidió |
|---|---|
| **Informes PDF** (los 5 endpoints Jasper de la v1) — no se portan y no hay tecnología elegida. **No añadir botones «Exportar PDF»** | D-F5-1, `decision-reportes-pdf-fuera-de-alcance.md` |
| Matcher v2, negociación E3, compraventa completa, Neo4j, WhatsApp, LLM, voz, *embeddings*, memoria vectorial, LangGraph y automatizaciones autónomas de KAIROS | checklist, cierre de los bloques 2 y 3 |
| Configuración de la política comercial **por organización** — declarada y sin implementar a propósito | `inventario-umbrales-de-dominio.md` |
| El mapeo estado → tono duplicado en diez pantallas | ídem |

---

## 9. Estado documental — qué se corrigió hoy y qué queda por vigilar

### 9.1 Eliminado

| Documento | Por qué |
|---|---|
| `contrato-local-form.md` | Describía `LocalForm` y las rutas `/locales/nuevo`, `/locales/:id/editar`, **borrados** en los bloques 3d y 3f. Se presentaba como «el patrón reutilizable para formularios de alta/edición del SPA», que hoy es `propiedad-form` + `propiedad-editor` + `cl-campo-gobernado`: no estaba desactualizado, estaba **enseñando lo contrario**. Cero enlaces entrantes. Queda en git |

### 9.2 Anotados hoy (siguen siendo útiles, pero ya no dicen la verdad presente)

| Documento | Anotación |
|---|---|
| `modelo-herencia-y-generalizacion.md` | **HISTÓRICO**: lee el esquema MySQL y las clases de `backend-java/`, borrados. Se conserva por su punto B, que es el germen del modelo universal |
| `estado-backend-para-el-inicio.md` | **CUMPLIDO**: E2 cerró el 2026-08-19. Su único hueco vivo (`GET /inicio`) pasa a §4.6 de aquí |
| `traspaso-inicio-a-angular.md` | **CUMPLIDO** |
| `encargo-sesion-kairos.md` | **NO EJECUTADO, y a propósito.** Da por vigente el estado del bloque 3 (2026-08-18); desde entonces entraron V71…V78. **Reescribirlo antes de usarlo** |
| `auditoria-residuos-semanticos.md` | **PARCIALMENTE RESUELTA**, con el estado real de sus cinco puntos |
| `auditoria-ui-brox.md` | Tres de sus cinco `BACKEND_FALTANTE` ya existen. Y aviso de colisión: sus «Corte 1/2» son de UI, no los del catálogo |
| `mapa-pantalla-dominio-backend.md` | Medido el 2026-08-17 y no re-medido: varias filas `DERIVADO_FRONTEND` ya no lo están |
| Los 8 `contrato-congelado-*.md` | Citaban rutas `backend-java/...` como «fuente de verdad» y esa carpeta no existe desde el 2026-08-08 |
| `auditoria-profundidad-inmobiliaria.md` | Su cadena de migraciones previstas (V71…V75) se quedó corta: la real llega a V78. Se añadió la tabla de lo realmente aplicado y se corrigió «Corte 2 · migración V78» → **V79** |

### 9.3 Trampas de numeración que conviene no olvidar

Hay **tres** planes que usan las mismas letras y números para cosas distintas:

| Numeración | De qué habla | Dónde |
|---|---|---|
| **E0…E9** | etapas de captura del *moat* (E0 histórico económico … E9 certificación) | `mapa-ejecucion-brox.md`, checklist |
| **E1…E5** | etapas de la **migración** (personas, reportes, ficha, dashboard, corte) | docs marcados HISTÓRICO |
| **Corte 0A…7** | cortes del **catálogo** inmobiliario | `auditoria-profundidad-inmobiliaria.md` |
| **Corte 1, 2** | cortes de **UI** | `auditoria-ui-brox.md` |

Y los **bloques 2…9** de la ruta a BROX 1.0, que son otra cosa más.

### 9.4 Lo que sigue vigente y no se ha tocado

Gobiernan: `north-star-brox.md` (contra qué se mide un avance),
`mapa-ejecucion-brox.md` (dónde estamos),
`checklist-captura-moat-e-inteligencia-inmobiliaria.md` (qué falta para cerrar),
`auditoria-profundidad-inmobiliaria.md` (los cortes), las `decision-*` y
`matriz-operacion-rol.md` (que además está vigilada por un test). Los once
documentos con banner HISTÓRICO de la era de la migración se conservan tal cual:
explican el **porqué**, y CLAUDE.md ya avisa de que no gobiernan.

---

## 10. Si sólo se puede hacer una cosa

~~**Publicar la rama.**~~ ✅ **Hecho el 2026-08-23.** Eran **48** commits contra
lo que existía en GitHub —el modelo universal, el sujeto del dato, el catálogo
gobernado, el editor universal y las **20** suites de integración— y vivían en
un único disco. Ya no.

**Lo siguiente que no se puede planificar, se ejecuta:** rotar el secreto JWT
(§0.2). Sigue publicado en `2832a9b`, que es la cabeza de `main` en GitHub, y
`backend-spring` firma con él.
