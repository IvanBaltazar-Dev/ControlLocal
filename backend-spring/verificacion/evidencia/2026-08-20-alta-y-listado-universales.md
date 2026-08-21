# Evidencia · El alta y el listado dejan de ser de local comercial

**Fecha:** 2026-08-20
**Tandas del encargo BROX universal:** 0 (frontera), 1 (alta universal visible) y
la mitad de 2 (listado universal). La ficha universal queda abierta.

---

## Qué se puede abrir y mirar

`localhost:4200` con `vmora / Agente2026`.

| # | Camino | Qué tiene que verse |
|---|---|---|
| 1 | Propiedades → + Registrar propiedad → **Departamento · Venta** | pide **Dormitorios\***, baños, estacionamientos; **no** pide rubro, galería ni renta. El bloque económico se titula «Condición de venta» y el campo, «Precio de venta» |
| 2 | … → **Terreno · Venta** | pide **Zonificación\***, área de terreno, frente, servicios; **no** pide dormitorios, baños ni rubro. Ni un concepto de local o de alquiler entre los campos |
| 3 | … → **Local · Venta y alquiler** | **una** ficha física y **dos** bloques económicos: «Condición de venta / Precio de venta» y «Condición de alquiler / Renta mensual» |
| 4 | Volver al listado | se distinguen `Departamento · Venta`, `Terreno · Venta`, `Local comercial · Venta + alquiler`, `Casa · Alquiler`, cada una con **su** importe por operación |

Comprobado a mano el 2026-08-20 contra la API en Docker. El caso 3 se registró
de verdad:

```
propiedad 3259  PROP-0022  tipo L  uso C  Av. La Marina 2450  San Miguel  160 m2
captacion 608   V  P        captacion 609   A  P
precio_propiedad  V/U 320000 USD    A/U 4800 USD
titularidad 1    atributo_propiedad 0 (metraje es estructural)
```

**Una propiedad, dos encargos, dos series económicas.** El filtro del listado
responde 3 / 9 / 1 para `VENTA`, `ALQUILER` y `VENTA,ALQUILER`: la última es
sólo la que tiene los dos encargos vivos.

---

## Lo que se rompió por el camino, y no era del alta

Tres defectos **anteriores** que la generalización hizo visibles. Los tres
estaban tapados porque la pantalla vieja no ejercitaba el camino.

### 1. `Idempotency-Key` no pasaba CORS · el peor de los tres

`ConfiguracionSeguridad` permitía `Authorization` y `Content-Type`. El SPA
mandaba `Idempotency-Key` en los comandos de contrato desde que existen. El
navegador respondía al preflight con **200** y después tumbaba el POST con
`net::ERR_FAILED`: sin cuerpo, sin estado y **sin nada en el log del servidor**,
porque la petición no llegaba.

Ninguna prueba podía verlo: el spec del SPA usa `HttpTestingController`, que
intercepta antes del navegador y **no cruza CORS**. Afirmaba una verdad —la
cabecera se pone— sobre un camino que en producción no existía.

`X-Elevacion` estaba igual, y es peor: la revocación del factor MFA ajeno
(D-S0-34) nunca llegó a ejecutarse desde el navegador. Permitirla no relaja
nada — el token de elevación lo sigue validando el servidor.

**Gate nuevo:** `CabecerasDelSpaPermitidasTest` lee las cabeceras que el SPA
manda de verdad y las contrasta con la lista de CORS. Encontró `X-Elevacion` a
los treinta segundos de existir.

### 2. El piso tenía dos dueños · V67

`catalogo_atributo.piso` (ATRIBUTO → `atributo_propiedad`) y
`GuionRegistroPropiedad.pisoUnidad` (estructural → `propiedad.piso`) nombraban
el mismo concepto y aplicaban a los mismos tres tipos. D-E4-3 no lo vio porque
revisaba **cada clave contra su columna**, y cada una declaraba una sola
autoridad: el defecto era que las claves eran dos.

Invisible mientras la pantalla dibujaba etiquetas escritas a mano. El alta
universal pinta lo que el motor publica, así que salieron **dos campos «Piso»
seguidos**.

V67 declara `propiedad.piso` como autoridad única (concepto `PISO`, el mismo
patrón que `METRAJE` en V60) y retira `pisoUnidad` del guion.

**Gate nuevo:** `ningunDatoSePreguntaDosVeces` — dos preguntas con el mismo
rótulo dentro de la ficha física son dos dueños del mismo dato. Comprueba los
siete tipos. La condición económica se comprueba **por bloque**: que «Moneda»
aparezca en el de venta y en el de alquiler no es una repetición, son dos
encargos.

### 3. Un código de prueba que chocaba consigo mismo

`AutoridadDelDatoIntegrationTest` generaba `"AUT-" + nanoTime % 1000000`. La
suite **comete**, así que las filas se acumulan: con un espacio de un millón la
colisión no era improbable sino cuestión de cuántas veces se corriera. Fallaba
con `uq_propiedad_codigo` y un mensaje de PostgreSQL — la peor clase de rojo,
porque manda a mirar al sitio equivocado. Ahora usa un UUID.

---

## Lo que cambió en BROX Core

| | |
|---|---|
| **El borrador admite dos operaciones** | `operaciones = "VENTA,ALQUILER"`, y las claves económicas se califican: `importe:VENTA`, `moneda:ALQUILER`. El único sitio que estrangulaba a una era `comandoDesde`; el caso de uso ya admitía N desde el bloque 2 |
| **`GET /captura/definicion`** | `deLaOperacion` pasa de lista plana a **lista de bloques**, cada uno con su operación y su rótulo. Sin esto el cliente tendría que partir `importe:VENTA` por el `:` — conocer la estructura de la clave es justo lo que las tres familias evitan |
| **`GET /captura/apertura`** *(nuevo)* | qué hay que decidir **antes** de que exista un plan. Para que ni Angular ni KAIROS escriban «primero el tipo, luego la operación» |
| **`GET /propiedades`** *(nuevo)* | una fila por propiedad con **sus encargos dentro**. Filtros en SQL; `operaciones=VENTA,ALQUILER` se resuelve con dos EXISTS, porque no hay ningún valor combinado que consultar |
| **`GET /propiedades/filtros`** *(nuevo)* | los distritos **con cartera**. Los tipos y las operaciones no están aquí: son del motor |
| **El distrito sale del catálogo** | estaba como texto libre y el formulario de locales llevaba 43 distritos escritos a mano |
| **Los rótulos se leen** | V68 acentúa los del catálogo; `rotuloDelTipo` publica «Local comercial» para que el cliente no traduzca `L` |

`AMBAS` sigue sin existir. `OperacionInmobiliaria.desdeLista` devuelve **cuántos
encargos se abren**, no una operación combinada, y lo dice en su javadoc.

---

## Lo que cambió en BROX Web

| | |
|---|---|
| `/propiedades/nueva` | `PropiedadForm`: **una** pantalla para los siete tipos. No sabe qué se pregunta a cada uno — lo pide |
| `/propiedades` | `Propiedades`: columna Tipo, columna Operación compuesta, un importe por encargo, copropiedad con «y N más» |
| `/propiedades/:id/editar` | **sigue en el formulario heredado a propósito**: el motor es de alta (D-E4-2 §6) |

**Gate nuevo:** `FronteraDeAutoridadEnElSpaTest` gana dos comprobaciones — el SPA
no ramifica por tipo de propiedad (`=== 'CASA'`) y no lleva su propia lista de
tipos. Las dos son la forma concreta en que se pierde el modelo universal.

---

## Verificación

```
backend   mvn clean install con TEST_DB_URL y CONTROLLOCAL_CIERRE=1
          860 pruebas · 0 fallos · 0 SKIPPED
          (las 16 de integracion ejecutadas de verdad contra PostgreSQL)

frontend  ng test --browsers=EdgeHeadlessCI
          644 pruebas · 0 fallos

migraciones aplicadas   V67, V68
```

---

## Lo que queda abierto

| | |
|---|---|
| **Ficha universal** (§17 del encargo) | `/propiedades/:id` sigue cargando `local-detail`, que lee el modelo heredado. Es la otra mitad de la tanda 2 |
| **`features/locales/`** | queda huérfano: nada lo enruta. Borrarlo es limpieza, no trabajo del alta |
| **El selector de tipo del alta** | muestra `LOCAL`, `OFICINA`… en mayúsculas. El rótulo existe (`rotuloDelTipo`) pero `Pregunta.opciones` es una lista de valores, no de pares valor/etiqueta |
| **Catálogo: `antiguedad_anios` y `estacionamientos`** | no tienen ninguna fila en `catalogo_atributo_tipo`, así que aplican a **todos** los tipos — y por eso un terreno pregunta su antigüedad. Es una decisión de negocio sobre el catálogo, no un defecto de código: **no se toca sin decidirlo** |
| **Tandas 3 a 7** | demanda universal, matcher con operación, dataset, Inicio/Radar e KAIROS |
