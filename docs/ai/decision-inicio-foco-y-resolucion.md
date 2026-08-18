# D-E2-1 · El Inicio es un sistema de foco y resolución

**Qué responde:** cómo se compone la pantalla de Inicio y qué reglas la gobiernan.
**Estado:** decidido el 2026-08-11. Congela E2.2–E2.5 del `mapa-ejecucion-brox.md`.
**Maqueta de referencia:** artifact `234233a0-7267-4a08-9bf9-d066cf58d7c2`
(«BROX · Inicio — Foco y resolución»), con 245 comprobaciones automáticas.

---

## 1. La decisión

El Inicio **no es una lista de tareas con widgets al lado**. Son dos superficies
con una sola lógica:

| Zona | Ancho | Pregunta que responde |
|---|---|---|
| **Qué atender ahora** (izquierda) | ~66 % | ¿qué elijo resolver? |
| **Radar BROX** (derecha) | ~34 % | ¿qué está pasando alrededor, y cómo lo resuelvo? |

Debajo de la izquierda, una banda compacta con el resto de la cola. Nada más.

**La izquierda identifica y selecciona. La derecha comprende y resuelve.** De ahí
salen casi todas las reglas siguientes.

---

## 2. Hasta cinco asuntos, y nunca se rellena hasta cinco

`Qué atender ahora` muestra **como máximo 5**. Cinco es un tope visual, no una
categoría: si solo hay dos asuntos que se puedan resolver, se ven dos y la
pantalla no inventa un tercero.

La cabecera lo dice en dos líneas, y **la segunda no es una plantilla**: nombra el
motivo concreto del primero, y añade una advertencia solo si algo del foco no
llega a mañana.

> Hay **12** asuntos que dependen de ti.
> Empieza por el **01**: bloquea una publicación. El **05** vence hoy.

El motivo va en **cuatro o cinco palabras** y la advertencia en tres. Una frase
larga ahí pesa más de lo que informa: la línea entera se queda por debajo de 70
caracteres.

El `12` no va en rojo ni en cuerpo gigante, y lo acentuado son los **números de
posición**, no el total. La protagonista es la capacidad de resolver, no la deuda.

Lo que **no** va ahí: recuentos del tipo «12 tuyos · 9 esperan a terceros · 6 se
moverán esta semana». No dicen nada del negocio y compiten con el titular. Lo que
está en juego tampoco: vive en el pie (§6.2), una sola vez.

**No hay buscador en el Inicio.** La pantalla es para resolver cinco cosas, no
para buscar; buscar tiene su sitio en cada listado.

---

## 3. Política de despacho: cómo se eligen esos cinco

Determinista, del dominio, **nunca visible como puntuación**. Se aplica en este
orden:

| | Criterio | Qué hace |
|---|---|---|
| 1 | **¿Se puede hacer algo ahora?** | Solo compite lo que `DEPENDE_DE_MI`. Lo que espera al interesado, al propietario, al broker o a documentación no ocupa un lugar del foco. |
| 2 | **Ventana temporal** | Cuanto menos margen queda, más pesa. Hoy pesa mucho más que dentro de doce días. |
| 3 | **Ventana de oportunidad** | Algo acaba de moverse y conviene aprovecharlo. **Puede superar a un vencimiento lejano** — es deliberado. |
| 4 | **Desbloqueo** | Si al resolverlo continúa un proceso hoy detenido, sube. |
| 5 | **Antigüedad accionable** | Gana turno poco a poco, con tope, para que nada se quede sin atender y para que la antigüedad no mande sola. |
| 6 | **Estabilidad** | Ante situaciones equivalentes se conserva el orden anterior. El 01–05 no baila entre recargas. |

### 3.1 Traer un asunto desde la cola **no reordena nada**

Lo que el usuario abre desde `Revisar cola completa` **se añade al final** del
foco y queda marcado como *«lo trajiste tú»*. No compite por los cinco sitios ni
desplaza a ninguno.

Es una regla de confianza, no de layout: si compitiera, traer un segundo asunto
expulsaría al quinto y **parecería que BROX cambió la prioridad**. Los números que
eligió BROX no se mueven nunca. La cabecera de la zona lo dice: `5 + 2 que
trajiste`.

**El criterio no se explica en pantalla.** Hubo un panel «¿Cómo elige BROX?» con
los seis pasos y se retiró: el orden se demuestra ordenando, y la segunda línea
del titular ya dice por qué el primero es el primero. La política vive aquí, en
la documentación, y en el dominio.

> **Regla de implementación:** estos pesos son **del dominio**, como los umbrales
> de E1. Angular recibe el orden ya resuelto y solo dibuja.

---

## 4. Se eliminan las etiquetas de clasificación

`REQUIERE ATENCIÓN`, `VENCIMIENTO`, `AVANCE`, `ESPERA`, `OPORTUNIDAD` **no
aparecen**. Mezclaban dimensiones distintas y competían con el contenido.

En su lugar, la fila enseña **el hecho**:

```
01   Jr. Ica 118
     Falta la partida registral · bloquea la publicación        ›

02   Av. Arequipa 1840
     Vence en 12 días · el propietario respondió ayer           ›
```

La clasificación sobrevive como **marca mínima**: el tono del número (rojo,
ámbar, verde). El proceso, como icono de su color delante del título.

---

## 5. La fila no lleva CTA

Llevaba el mismo botón que la recomendación del Radar y competían entre sí. La
fila termina en un chevron; pulsarla en cualquier punto selecciona el caso, y la
acción vive en la recomendación.

---

## 6. La cola restante no se despliega dentro del Inicio

Debajo del foco, una banda de una sola altura:

```
7 siguen en tu cola                           Revisar cola completa →
4 seguimientos · 3 expedientes
```

No otra tabla, no `05, 06, 07…`. La vista `Revisar cola completa` sí puede tener
búsqueda, orden y agrupación.

> **Excepción, decidida el 2026-08-11:** el Inicio sí lleva **dos** filtros, y
> solo dos — `Propietario` y `Cliente` (§7.0.f). No son un filtro de búsqueda:
> separan las **dos operaciones del negocio**, que hasta entonces se leían como
> una sola lista. Cualquier otro filtro sigue viviendo en la cola completa.

---

## 6.1 Accesos rápidos: lo que se empieza desde cero

Debajo de la cola, una barra de **cuatro** accesos. El orden importa: primero se
resuelve lo que ya existe (el foco), después lo que sigue en la cola, y solo
entonces lo que se crea de nuevo.

| | Agente | Broker |
|---|---|---|
| 1 | Nueva prospección → `/locales/nuevo` | Revisar captaciones → `/captaciones/pendientes` |
| 2 | Nueva captación → `/captaciones/nueva` | Evaluar solicitudes → `/solicitudes/revisar` |
| 3 | Programar visita → `/visitas/nueva` | Seguimiento del equipo → `/seguimiento-comercial` |
| 4 | Reporte al propietario → `/reportes` | Reasignar cartera → `/captaciones/reasignaciones` |

**No son los mismos para los dos roles**: el agente crea, el broker revisa,
decide y reparte. Las ocho rutas existen hoy en el SPA. Y, como en el resto de la
pantalla, **la ruta va en el `href` y nunca a la vista**.

> **Pendiente de confirmar**: «Nueva prospección» abre el alta del local, porque
> en el dominio registrar un local es lo que abre su prospección (no hay
> `/prospecciones/nueva`). Si comercialmente se quiere otro punto de entrada, se
> cambia el destino sin tocar la barra.

---

## 6.2 El pie: anticipo de Indicadores, no una métrica nueva

Una franja al pie, a todo el ancho, y **el enlace es la franja entera**:

```
PUEDE CERRARSE ESTE MES   PROSPECCIÓN   CAPTACIONES   SOLICITUDES   CONTRATOS
US$ 9,300      ╱‾╲╱       22 de 24      13 de 15      6 de 8        4 de 5
3 operaciones · renta     ▬▬▬▬▬▬┃▬      ▬▬▬▬▬┃▬▬▬     ▬▬▬▬┃▬▬▬▬     ▬▬▬▬▬┃      Ver indicadores →
```

**No inventa ninguna métrica.** Son los **cuatro KPI canónicos de D-E2-2** —mismo
nombre, misma meta, mismo semáforo de ritmo— en miniatura. Por construcción no
puede contradecir a Indicadores: si allí cambia la definición, aquí cambia sola.

> **El nombre va completo, siempre.** *Propietarios contactados*, *Locales
> captados*, *Solicitudes ingresadas*, *Contratos firmados* — **los mismos que
> use la pantalla de Indicadores, letra por letra**. Cuando allí cambiaron
> (2026-08-11), aquí cambiaron el mismo día: un pie que contradice a Indicadores
> es peor que no tener pie. Abreviarlos deshace la distinción que D-E2-2 §1.1 vino
> a fijar: **31 registros creados no son 31 prospectos trabajados**. Hay una
> comprobación que exige los cuatro nombres literales, en los dos roles.

### 6.2.0 Los KPI son los mismos; la LECTURA no

Se preguntó si los cuatro indicadores tienen sentido para un broker. **Los
indicadores sí; la lectura que se les daba, no** — y D-E2-2 ya lo tenía
decidido antes de que el pie existiera:

| Instrucción de D-E2-2 | Qué obliga aquí |
|---|---|
| **3** · mismas definiciones, cambia solo el alcance | los cuatro KPI **no se tocan** |
| **4** · el broker **no recibe crédito personal** por el resultado del equipo | «hoy deberías ir por 150» estaba **prohibido**: le atribuía una producción que él no hace |
| **11** · el agente recibe **acciones**; el broker, **intervenciones de supervisión** | su segunda línea habla del equipo, no de él |
| **§6.1** · «un total verde puede esconder un equipo roto» | el pie del broker lleva el **pulso**: la distribución |
| **14** · un solo diagnóstico principal | el pulso va **una vez**, no repetido por KPI, y sin nombres (instrucción 13) |

```
AGENTE   22 de 24    A 2 de la meta · hoy deberías ir por 21
BROKER  146 de 160   A 14 de la meta · el equipo va por detrás
                     PULSO DEL EQUIPO  ● 6 en ritmo  ● 1 atención  ● 1 fuera
```

Y la cifra en juego del broker gana **su propia palanca**, que es lo único de
esa franja sobre lo que él actúa directamente (D-E2-2 §8):

> US$ 41,200 — 11 operaciones · **3 esperan tu decisión**

---

### 6.2.1 Es gráfico, y lo gráfico es información

Una fila de números es plana y se lee como una obligación. Cada pieza aquí
**significa algo**, no decora:

| Pieza | Qué dice |
|---|---|
| **Barra por KPI** | lo recorrido, en el tono del ritmo |
| **Marca fina sobre la barra** | el **ritmo esperado a hoy** (`metaEsperadaAHoy`). Sin ella, un 87 % no dice si vas por delante o por detrás — es la marca que pide D-E2-2 §3 |
| **Curva de la cifra del mes** | seis meses de la misma medida: la cifra tiene de dónde venir |
| **Malla de fondo al 7 %** | la firma de BROX, la misma geometría del núcleo del Radar, quieta. Se intuye, no se lee |

**La etiqueta dice qué se mide.** «En juego este mes» no decía nada: ahora es
**«Puede cerrarse este mes»**, con la cifra, el número de operaciones y la unidad.

Tres reglas que evitan que se convierta en un tablero:

- **Cuatro KPI, ni uno más**, y una sola cifra de dinero. El detalle —conversión,
  embudo, comparativas, equipo— está en Indicadores, a un clic.
- **Lo que está en juego se dice una sola vez.** Antes había una línea en la
  cabecera; se movió aquí.
- **No repite ninguna cifra del foco.** Hay una comprobación que lo verifica.

> **Pendiente de definir en D-E2-2**: qué suma exactamente «puede cerrarse este
> mes». La lectura de la maqueta es *renta mensual de las operaciones que pueden
> firmarse en el periodo*, pero esa métrica todavía no está en el documento de
> indicadores.

---

## 7. El Radar BROX es el panel principal de la aplicación

No es «un widget a la derecha». Es la pieza que tiene que sostener la impresión
de que **detrás hay un sistema que razona** — hoy con reglas de dominio, mañana
con modelos. Esa impresión se construye ahora o no se construye.

Cuerpo en **azul hielo**, con la `RECOMENDACIÓN BROX` como placa elevada:
*azul BROX = el sistema está interpretando y ayudando*. Rojo, ámbar y verde
siguen reservados a la semántica de negocio.

### 7.0.a Qué lo hace parecer inteligente, y qué no

La versión anterior tenía el contenido bien y la presentación era **una lista de
párrafos con palabras resaltadas**. Colorear más no arreglaba nada: el problema
no era el color, era que **nada en la superficie decía que aquello estaba
razonado**.

Lo que sí lo dice, tomado de los paneles que lo consiguen (Linear, Perplexity,
Fin, v0, Raycast) y adaptado a BROX:

| Recurso | Qué transmite | Cómo se hace aquí |
|---|---|---|
| **Material propio** | «esto es una superficie del sistema, no una página» | borde en degradado enmascarado, halo que **toma el color del caso**, realce interior |
| **Pila de tarjetas** | estructura, no documento | cada bloque es una pieza con borde y sombra sobre el sustrato azul hielo |
| **Rastro de estructura** | se lee la forma antes que el texto | **toda sección se anuncia con su icono en placa** |
| **Atribución** | la respuesta tiene de dónde salir | `N señales`, y **al pulsarlo se iluminan los hechos** que la sostienen |
| **Aparición escalonada** | una conclusión formándose, no una página pintándose | 55 ms por bloque, de arriba abajo |
| **Micro-visualización** | densidad sin texto | hilo de tiempo, ruta del proceso, barras, chispas |

Y lo que **no** lo dice, que es la lección cara: más animación. Cinco efectos en
el mismo sitio se anulan, y un emblema que parpadea parece un adorno.

### 7.0.b La pila de tarjetas

**Regla estructural.** El cuerpo del Radar no lleva secciones separadas por
filetes: lleva **tarjetas** —fondo propio, borde de 1 px, radio 12, sombra
corta— sobre el sustrato azul hielo, con 8 px entre ellas.

Es una sola regla CSS y cambia la lectura entera: con filetes se lee como un
documento; con piezas, como un panel. Dos tarjetas se distinguen del resto
porque son las que importan: la **recomendación** (degradado frío, firma de la
malla BROX al fondo al 5 %, sombra azul) y el **hallazgo** (degradado verde muy
tenue, borde y sombra en verde).

### 7.0.c Ninguna sección lleva icono: llevan **canto de color**

Los iconos en placa se retiraron **de todas partes** — primero de los renglones
del expediente y después de las cabeceras de sección. En su lugar, la misma
gramática en toda la pantalla: **un canto de 3 px y una versalita teñida**.

Un solo recurso repetido se aprende en dos segundos; cuatro recursos distintos
para lo mismo se leen como desorden.

| Sección | Color del canto | Por qué |
|---|---|---|
| `Hallazgo destacado` | verde | es una buena noticia |
| `Cómo está` | el tono del caso | es el estado del asunto |
| `Qué cambió` | **oro** | lo que se movió; es la vigilancia de BROX |
| `Qué viene` | **grafito** | lo agendado; todavía no ha pasado |

El mismo canto marca el lado de la operación en la ruta, el filtro activo del
foco y el renglón con señal del expediente.

Se probó poner una placa en cada rótulo del expediente —firma, moneda, lupa,
persona— y **se retiró**: dentro de un renglón de dos líneas el icono parte la
línea y compite con el dato.

**Pero con el icono se fue el color, y eso fue un error.** El icono sobraba; la
señal cromática no. Vuelve donde no compite: **en el rótulo**. Un renglón con
señal lleva su versalita en el color de su estado y un canto de 3 px delante;
uno sin señal, en gris. Y el dato vuelve a **tinta plena**, que es lo que lo
separa del rótulo.

```
ENCARGO       gris    Firmado el 2 ago · 6 meses · vence el 18 nov
RENTA         gris    US$ 2,900 propuesta · aún sin publicar
▌REVISIÓN     verde   Devuelta el 6 ago · 2 observaciones, [1 resuelta]
▌PROPIETARIO  ámbar   Sr. Aliaga · [sin reportes enviados]
```

> **La regla que queda:** icono para lo que **estructura** (una sección);
> tipografía para lo que **etiqueta** (un renglón).

### 7.0.d La ruta: dónde está el asunto **en su cadena**

La ficha de identidad decía **qué** es y no decía **dónde** está. Debajo del
título va la cadena, con la operación identificada:

> ▌PROPIETARIO │ Prospección — **Captación** — Publicación
> ▌CLIENTE │ **Oportunidad** — Visita — Solicitud — Contrato

**Los pasos van con su nombre, no numerados.** «paso 2 de 3» obligaba a
preguntar cuál era el 3; enseñarlos lo contesta sin una palabra más. El actual
es el único en tinta y en el color de su proceso.

**No hay una cadena: hay dos, y no comparten ningún paso.**

| Lado | Se llama | Cadena |
|---|---|---|
| Oferta | **Propietario** | Prospección → Captación → Publicación |
| Demanda | **Cliente** | Oportunidad → Visita → Solicitud → Contrato |

`Publicación` cierra la de la oferta porque un local sin publicar no genera
oportunidades. `Cliente` cae en `Oportunidad` porque un requerimiento abierto
**es** la oportunidad; visita y solicitud son sus pasos siguientes.

> **Esto se corrigió el 2026-08-11 y era un error de dominio, no de diseño.**
> Una sola cadena de seis pasos ponía un requerimiento recién registrado en
> «paso 4 de 6» —por delante de una captación en curso— cuando en realidad es
> **el primero de su propia carrera**. Medir la demanda es
> `oportunidad → visita`, `visita → solicitud`, `solicitud aprobada → contrato`;
> lo demás solo aplica a la oferta. Hay una comprobación que exige que las dos
> listas no compartan ningún nombre.

---

### 7.0.g El Radar **navega**, no scrollea

El desplegable de antecedentes empujaba el panel justo cuando más contexto
necesitas, y un desplegable dentro de un panel es una capa de más: abre,
empuja, tapa y hay que volver.

Pasa a ser lo que ya era conceptualmente: **dos vistas del mismo asunto**, con
una **barra de pestañas** — no una cápsula.

```
Resolver   Antecedentes 4
━━━━━━━━
──────────────────────────────  ← filete a todo el ancho
```

La cápsula blanca leía «interruptor», no «hay otra sección aquí», y por eso
Antecedentes pasaba desapercibido. El filete que cruza todo el ancho es lo que
dice «esto es una barra de secciones»; el subrayado, cuál está abierta; y **la
cuenta `4`**, que ahí dentro hay algo. Es el patrón de Stripe, de los ajustes de
Linear y de cualquier terminal financiera.

El subrayado **se mide** sobre la pestaña abierta y se desplaza con la curva de
resorte: los dos rótulos no miden lo mismo, así que una fracción fija quedaría
corta o larga.

- **Resolver** — la recomendación, su acción y `Cómo está`.
- **Antecedentes** — la lectura de BROX, los cuatro renglones y el enlace al
  expediente completo.

Se cambia en 340 ms y **la navegación deja de ser vertical**. Al conmutar, la
cabecera y el propio conmutador **no parpadean** (solo se recompone lo de
abajo): re-animarlos delataría el truco.

**Y la cabecera pasa a decir de qué asunto se trata.** En resolución mostraba
«RADAR BROX» y debajo había una tarjeta de 121 px repitiendo el título que ya
estaba seleccionado a la izquierda: dos piezas para lo mismo, y la altura se
paga en scroll. Ahora la banda lleva el asunto y su identidad, y abajo queda una
barra fina con la ruta. La marca no se pierde: el emblema sigue ahí y
`← General` devuelve.

**El compromiso de altura, medido y no prometido:** desde **940 px de alto de
ventana** —un portátil normal— ninguna de las dos vistas scrollea. Por debajo,
lo que scrollea es el **cuerpo del Radar**, nunca la página.

### 7.0.h Los botones ejecutan, no responden

Pulsar y que la pantalla salte no cuenta nada. El botón de acción pasa por
**tres estados en 980 ms**:

| Estado | Qué se ve |
|---|---|
| etiqueta | el botón normal; al pasar por encima, un barrido de luz lo cruza una vez |
| trabajo | **el azul se cierra** (más profundo, no más claro) y **un haz de luz recorre el canto** |
| confirmación | verde, y la marca **se dibuja** — no aparece |

Recién entonces se resuelve el asunto y sale de la cola. Es lo que separa «me
respondió» de «lo hizo».

**El haz es un `conic-gradient` cuyo ángulo es una propiedad registrada con
`@property --giro`, enmascarado a 1,5 px de canto.** Sin registrar el ángulo el
navegador no sabe interpolarlo y el cono no se movería; con él, es una sola capa
compuesta por la GPU. Es la técnica de los paneles de Vercel, Linear y Magic UI.
Los tres puntitos y la barra de progreso que había antes se retiraron: eran la
versión genérica de esto mismo.

### 7.0.i Registro visual: corporativo, no de consumo

Tres decisiones que valen para toda la pantalla y que se tomaron juntas:

**Menos curva.** Los radios bajan —tarjeta 14 → **8**, control 9 → **6**,
pastillas de 999 px → **3–5**— y las cápsulas desaparecen. Las herramientas que
se usan ocho horas al día (Bloomberg, Stripe, Linear, Palantir) son
**rectilíneas**: la jerarquía la llevan la tipografía y el filete, no el
redondeo.

**Fuera los adornos circulares.** La chapa redonda con icono del lado
(`◉ Propietario`) se retiró: un **canto de 3 px** y una versalita dicen lo mismo
y encajan con el resto. Igual en los filtros, que pierden el punto de color.

**Curva de resorte con `linear()`.** Una función de easing de veinte puntos que
reproduce un muelle amortiguado, sin librería y sin coste. La usa el subrayado
de las pestañas. Y la entrada de los bloques deja de ser un desplazamiento: entra
con `filter: blur(6px)` que **se resuelve**. Un desenfoque que enfoca se lee como
algo que se forma; un `translate`, como algo que se empuja.

**Y el cambio de vista usa la View Transitions API**: el navegador fotografía el
antes y el después y los cruza con `::view-transition-old/new`, con dirección
según a dónde vas. Donde la API no existe, el escalonado hace el trabajo solo.

---

## 7.0.f El foco distingue las dos operaciones

En `Qué atender ahora` conviven encargos de propietarios y requerimientos de
clientes, y hasta ahora **nada lo decía**.

**Dos filtros, y se llaman `Propietario` y `Cliente`** — no «oferta» y
«demanda», que son los nombres de Indicadores (D-E2-2) y no los que se usan
operando. Solo aparecen si hay de los dos: un botón que no separa nada estorba.

**Filtrar NO renumera.** El `03` sigue siendo el `03` aunque se oculten el 01 y
el 02. Es la misma regla de confianza de §3.1: si los números bailaran al
filtrar, parecería que BROX cambió la prioridad.

**Mezclados, cada fila lleva su lado en el fondo**: un canto de 2,5 px y un
lavado que se apaga hacia la derecha. Los dos tonos son **temperaturas, no
estados** — arena para el propietario, acero para el cliente— y deliberadamente
**ninguno de los tres del semáforo**, que ya significa otra cosa en la misma
fila. Filtrado, el lavado desaparece: ya lo dice el filtro.

### 7.0.e La atribución: `N señales`

En la cabecera de la recomendación, una pastilla discreta con el número de
hechos sobre los que se apoya — se calcula de los mismos datos que se enseñan
debajo (`Cómo está` más los renglones del expediente con señal), **no es un
número decorativo**.

**Al pulsarla se abren los antecedentes y se iluminan uno a uno esos hechos**,
con 70 ms de desfase. Es lo que hace Perplexity cuando pulsas una cita: la
recomendación deja de ser una frase y pasa a tener respaldo visible.

### 7.0 La cabecera lleva la presencia de la marca

Es el **segundo y último bloque oscuro** de la pantalla, junto al lateral: una
banda de ~55 px en azul noche (`#0C1D34 → #07101E`) con un **núcleo que piensa**.

Un **octógono** —la forma del logotipo— con sus cuatro diámetros, un rombo al
centro y ocho nodos, todo en 46 px:

| Capa | Qué hace | Técnica |
|---|---|---|
| malla | el octógono, trazado con **hilo de degradado** oro → azul | `linearGradient` SVG |
| cables | los cuatro diámetros, más finos y fríos | SVG |
| rombo | el centro, que rima con la marca de la recomendación | SVG |
| nodos | ocho vértices, radios alternos, con bloom | filtro `feGaussianBlur` + `feMerge` |

**El emblema está quieto, y esa es la decisión.** Parpadeando parecía un adorno;
grabado con degradado y nodos fijos parece una marca. La señal de vida se fue a
donde el oficio la pone: un **punto verde de 5 px** junto a «Vigilando 18
operaciones», con la pulsación convencional de sistema en vivo (3,2 s). Es la
**única animación de la cabecera**.

**Lo que se probó y se quitó**, porque es la lección que cuesta: un cometa
girando (`conic-gradient` + `@property`), dos octógonos en órbitas opuestas,
tres impulsos viajando por el cableado con CSS Motion Path, los anillos
concéntricos de fondo, el corazón latiendo, el halo, y por último las propias
sinapsis. Todo funcionaba y era técnicamente lo más moderno; **ocupaba el mismo
círculo de 46 px y se anulaba entre sí**. Una forma bien grabada impone más que
cinco animaciones superpuestas.

### 7.0.1 El título tiene que pesar

`RADAR BROX` estaba en 10,5 px al lado de un núcleo de 46: desaparecía. Va en
**15 px**, versalitas, `letter-spacing: 0.32em` y un degradado de blanco a oro
con `background-clip: text`.

Y el subtítulo **dice lo que BROX está vigilando**, no una frase de folleto:

> Vigilando **18 operaciones** · 4 movimientos hoy

«Lo relevante alrededor de tu trabajo» no aportaba nada: cualquier producto
podría decirlo. Un recuento de lo que el sistema tiene bajo observación, sí.

Cuatro reglas, y las tres primeras salieron de equivocarse:

- **Sin imágenes.** Todo son gradientes, SVG y animaciones CSS: pesa cero,
  escala a cualquier densidad y no hay ningún PNG que mantener.
- **`box-shadow` con spread NO hace anillos**: pinta círculos **rellenos**, y con
  centros transparentes se apilan discos — la mancha mostaza del primer intento.
  Los anillos de verdad salen de un `repeating-radial-gradient` enmascarado.
- **Oro opaco sobre azul noche da marrón.** El halo va con
  `mix-blend-mode: screen` para que la luz sume en vez de tapar.
- **Un movimiento lento no se percibe.** Un barrido de 11 s por vuelta pasaba
  desapercibido; el pulso de 2,6 s se ve de reojo sin distraer. Todo se apaga
  con `prefers-reduced-motion`.
- **Una animación por superficie.** Dos o más en el mismo punto no suman: se
  tapan. Si hace falta añadir otra, va en otro sitio o no va.

El resto de la pantalla sigue siendo claro. Esta banda y el lateral son las dos
únicas excepciones.

### 7.1 El Radar es **de cada rol**, no compartido

Lo que cambia no es el formato: es el **alcance**. Un broker no necesita saber
que se hizo una visita en Petit Thouars; necesita saber qué pasa con su equipo.

| | Agente | Broker |
|---|---|---|
| Hallazgo destacado | una **coincidencia de cartera**: dos locales vuelven a encajar | una **concentración**: el cuello del equipo está en un agente |
| Qué cambió | sus operaciones | movimientos del equipo y de sus agentes |
| Qué viene | sus visitas y reportes | sus decisiones, revisiones y vencimientos del equipo |

Se compartían, y el resultado era que **el broker se quedaba sin hallazgo** —el
bloque estaba condicionado a `AGENTE`— justo el rol al que más le sirve que BROX
encuentre algo que la media esconde (D-E2-2 §9.1).

> **El `caso` de cada fecha es un mapa por rol.** Av. Arequipa está en la cola
> del agente como `arequipa` y en la del broker como `arequipa-b`. Con un solo id,
> la referencia fallaba para el broker y **el mismo encargo salía dos veces**: en
> su cola y como fecha suelta en la agenda. La regla del hogar único (§11) no vale
> si el identificador no es el del rol que está mirando.

### Modo A · Radar general (ningún caso seleccionado)

Composición **editorial**, no tres widgets del mismo tamaño:

1. **Hallazgo destacado** — solo cuando existe algo que destacar. Si BROX no
   encontró nada, **el bloque no existe** y `Qué cambió` ocupa su sitio. No se
   rellena con «todo está bien».
2. **Qué cambió** — feed compacto con contador y `Ver los N →`.
3. **Qué viene** — miniagenda.

### Modo B · Resolución (un caso seleccionado)

```
◇ RADAR BROX                              ← General
Av. Arequipa 1840
Sr. Ramírez · US$ 4,500 · vence 22 ago
───────────────────────────────────────────────────
◇ RECOMENDACIÓN BROX
Programar la conversación con el propietario.

PARA QUÉ
Llegar al vencimiento con la renovación decidida.

[ Fecha ] [ Hora ]   [ Programar ]
───────────────────────────────────────────────────
CÓMO ESTÁ
✓ El propietario devolvió la llamada ayer     (verde)
⏱ El encargo vence en 12 días                 (rojo)
○ No hay conversación programada              (ámbar)

PRÓXIMO   22 ago · vencimiento del encargo

Ver antecedentes ↓
```

---

## 8. «Para qué», nunca «por qué»

BROX **no explica su ranking** como protagonista. El contrato de interfaz es
siempre:

| Bloque | Contenido |
|---|---|
| `RECOMENDACIÓN BROX` | qué hacer |
| `PARA QUÉ` | qué consigue o qué permite |
| acción | resolverlo |

La evidencia que produjo la recomendación **existe y es trazable** (hecho →
diagnóstico → recomendación → evidencia → desenlace), pero no ocupa el primer
plano. Se consulta en antecedentes y en el expediente.

---

## 9. La acción pequeña se ejecuta en el propio Radar

| Cabe en el Radar | Sale al expediente |
|---|---|
| adjuntar un documento | crear un contrato |
| registrar el resultado de una llamada | evaluar una solicitud completa |
| elegir una fecha | editar toda la captación |
| confirmar un dato | modificar condiciones económicas |
| marcar realizado | |

Cuando sale, el botón dice `Abrir …` y **el Radar conserva el contexto**: al
volver, el asunto sigue seleccionado.

### 9.1 El expediente es una pantalla real del SPA, no un panel inventado

Cada asunto declara su destino, y son rutas **que ya existen** (32 controladores
REST y 57 pantallas en el SPA, verificado el 2026-08-11):

| Tipo de asunto | Pantalla | Ruta |
|---|---|---|
| Captación / inmueble con encargo | Ficha comercial de la captación | `/captaciones/:codigo/ficha` |
| Captación por decidir (broker) | Revisión de la captación | `/captaciones/:codigo/revisar` |
| Local | Ficha del local | `/locales/:id` |
| Prospección | Detalle de la prospección | `/prospecciones/:id` |
| Cliente | Bitácora del cliente | `/clientes/:id/contacto` |
| Propietario | Ficha del propietario | `/propietarios/:id` |
| Oportunidad | Detalle de la oportunidad | `/oportunidades/:id` |
| Solicitud (documentos) | Documentos de la solicitud | `/solicitudes/:codigo/documentos` |
| Solicitud (decisión) | Evaluación de la solicitud | `/solicitudes/:codigo/evaluar` |
| Comisión | Comisiones | `/comisiones` |
| Carga del equipo | Mi equipo | `/mi-equipo` |

**La ruta va en el `href`, nunca a la vista.** Un `/locales/132` debajo de un
botón no le dice nada a quien usa la aplicación: es información del
desarrollador. El botón nombra el destino en lenguaje de negocio —«Abrir el
local»— y el enlace hace el resto. Tampoco se abre un panel con un resumen que ya
está en el propio Radar.

---

## 10. `CÓMO ESTÁ` lleva como máximo tres hechos, **cada uno con su estado**

Tres viñetas, sin párrafos. `PRÓXIMO` **solo aparece cuando existe**; si no hay
nada programado, se dice — que muchas veces es el dato importante:

> Sin próxima actividad programada.

### 10.0 El rótulo se llama `CÓMO ESTÁ`, no `AHORA`

`AHORA` no se entendía: se leía como un adverbio suelto, no como el nombre de un
bloque. `CÓMO ESTÁ` dice exactamente lo que hay debajo.

### 10.1 Cada hecho lleva su propio estado, y ese estado tiene marca y color

**Este es el punto de la sección.** Los tres hechos compartían el tono del
asunto, así que un asunto en rojo pintaba de rojo también sus buenas noticias:

```
● 1 observación pendiente        (rojo)
● Metraje ya corregido           (rojo — y es una BUENA noticia)
● Publicación bloqueada          (rojo)
```

De un vistazo eso dice «aquí todo va mal», que es falso y desmoraliza. Un hecho
resuelto y un hecho que frena **no son el mismo tipo de hecho** y no pueden
compartir marca. El vocabulario es de cinco estados y **no crece**:

| Estado | Marca | Color | Qué significa | Ejemplo |
|---|---|---|---|---|
| `HECHO` | ✓ | verde | ya está resuelto, nadie tiene que volver sobre ello | «El metraje ya está corregido» |
| `FALTA` | ○ | ámbar | falta que alguien lo haga; **es lo accionable** | «Falta subir la partida registral» |
| `PLAZO` | ⏱ | rojo | corre el tiempo, con fecha | «El encargo vence en 12 días» |
| `FRENO` | ⊘ | rojo | la **consecuencia** de lo que falta: qué queda parado | «Hasta que llegue, el local no se puede publicar» |
| `DATO` | – | gris | contexto, ni bueno ni malo | «Último contacto: 3 de agosto» |

Solo `FRENO` va en tinta plena; los demás en tinta secundaria. La marca roja ya
carga la alarma, y subir además el peso hacía que la consecuencia compitiera con
el titular de la recomendación, que es lo único que se pide leer primero.

**El orden dentro del bloque es narrativo, no por gravedad:** lo que ya está →
lo que falta → qué queda parado por ello. Se lee como una frase.

### 10.2 La barra de avance, solo donde hay algo real que contar

Cuando el asunto tiene requisitos contables —documentos, observaciones,
criterios de coincidencia— encima de los tres hechos va una barra segmentada:

> ▮▯  **1 de 2** observaciones resueltas
> ▮▮▮▮ **4 de 4** documentos verificados

Es lo que contesta «¿cuánto me falta?» sin leer. **Donde no hay nada que contar
no se pone**: una barra de dos segmentos inventada para rellenar sería peor que
la ausencia. Y **no repite la identidad del caso** — cuando el encabezado decía
«4 de 4 documentos verificados» y la barra también, se quitó del encabezado y
ese hueco pasó a llevar el dato comercial (distrito y renta).

### 10.3 Antecedentes: el expediente comercial en cuatro renglones

Plegados por defecto. Al abrirlos **el Radar scrollea hasta ellos**: antes se
desplegaban fuera de vista y parecía que no había pasado nada. La barra del
cuerpo del Radar es visible a propósito — si no se ve la barra, nadie sabe que
hay más abajo.

**El rótulo va encima del dato, no a su izquierda.** Con una columna fija de
etiquetas, «Encargo» y «Renta» caben pero «Requerimiento» y «Coincidencias» se
cortan contra el valor. Apilado aguanta cualquier palabra y se lee igual en
móvil.

**Y el dato va en tinta plena, no en tinta secundaria.** Con rótulo y valor casi
del mismo gris, los cuatro renglones se leían como un párrafo corrido y no se
distinguía dónde acababa `Encargo` y empezaba `Renta`. La separación es de tres
cosas a la vez: versalita pequeña y clara para el rótulo, tinta plena para el
dato, y un filete entre renglones.

### 10.3.1 Cada renglón lleva su estado, y dos de ellos llevan gráfico

El contenido de los antecedentes era correcto y aun así el bloque **no se
leía**: un encargo al 93 % de su vigencia y una renta parada 54 días salían con
el mismo gris que el metraje. Nada decía qué renglón importaba.

**Tres piezas, y ninguna más.**

**1 · Estado por renglón** (`bien` / `ojo` / `mal`, o ninguno). Tiñe el
**fragmento que lleva la señal**, no el renglón entero — el resto sigue siendo
historial y no debe gritar. El fragmento va en una pastilla muy tenue (13 % del
color): se ve al barrer la columna y no interrumpe la frase.

> Alta el 12 may · `3 requerimientos abiertos` ← verde
> US$ 4,500 desde el 18 jun · `54 días sin cambios` ← ámbar

**El rótulo se queda gris siempre.** Cuatro versalitas de colores distintos
compiten entre sí; el color vive donde significa algo.

**2 · Barra de ventana consumida**, alineada a la derecha del rótulo, con su
razón delante: `168/180`. Responde «cuánto se ha gastado» sin leer la frase.
**Sin la razón la barra es un adorno** — se ve «casi llena» pero no de qué —, y
el riel tiene que ser visible o un relleno del 8 % desaparece. La razón no
duplica el renglón de abajo: ese da la prosa, esta da la proporción.

**3 · Chispa de la serie de precio**, que existe desde E0. Una línea plana de
54 días **dice** que la renta no se ha movido, y en un local con objeciones de
precio esa línea es media conversación.

Ambos gráficos se dibujan **al abrir**, no antes: si la barra ya está llena
cuando despliegas, el gesto no cuenta nada.

**La regla ya no es un tope, es una condición.** Empezó siendo «como máximo dos
renglones con color» y era demasiado estricta: el expediente se quedaba con dos
renglones vivos y dos mudos aunque los cuatro tuvieran una señal real.

> **Cada renglón dice algo si hay algo que decir. Ninguno se tiñe por rellenar.**

Los cuatro pueden llevar color; lo que no puede es que un renglón lleve estado
sin un portador visible (una marca, una barra o una chispa), ni que un fragmento
marcado cuelgue de un renglón sin estado. Hay una comprobación para cada cosa.

### 10.3.2 El expediente deja de reportar y pasa a interpretar

Cuatro hechos con iconos siguen siendo cuatro hechos. **Lo que hace que un panel
se sienta inteligente no es adornar el dato: es situarlo.** Un dato solo
informa; un dato comparado decide. Dos capas, y las dos salen de datos de la
propia casa — que es exactamente el moat que ControlLocal existe para capturar.

**1 · La lectura de BROX.** Una frase al abrir el bloque, con la marca `◇`, que
**sintetiza los cuatro renglones sin recitarlos**:

> Seis meses de exclusiva casi agotados, cuatro visitas sin propuesta y la renta
> sin moverse desde junio.

Es la diferencia entre volcar un expediente y haberlo leído. Una comprobación
verifica que **no repita literalmente** ninguno de los cuatro valores: si los
recitara no aportaría nada.

**2 · El contraste.** Debajo del dato, dónde cae respecto de **tu** operación.
Dos formas, según lo que se compara:

| Forma | Cuándo | Ejemplo |
|---|---|---|
| **Posición en un rango** | hay un rango real (zona, presupuesto del cliente) | `en el 68 % del rango de Miraflores · 3,200–5,100` con la marca sobre la escala |
| **Desviación contra tu media** | hay un histórico propio | `0 de 4` · *tu media es 1 propuesta cada 3 visitas* |

**Ninguna estadística del sector.** Todo sale de la base de la organización: el
rango real de la zona, tu media de propuestas por visita, el plazo de recontacto
de tu casa, cuántos locales de **tu** cartera entran en ese presupuesto. Es
comprobable, y es lo que hace que el dato pese. Hay una comprobación que rechaza
las palabras «sector», «mercado nacional», «industria» y «benchmark».

Como el color, **el contraste va donde importa** — uno o dos por expediente—, no
en los cuatro renglones.

Y dejan de ser cuatro fechas sueltas. Son **cuatro renglones fijos**, los mismos
para todo asunto, porque son las cuatro cosas con las que se sostiene una
conversación comercial:

| Renglón | Qué contesta |
|---|---|
| **Encargo** / Solicitud / Oportunidad | desde cuándo existe, con qué vigencia y cuándo vence |
| **Renta** / Presupuesto / Comisión | el dinero, y desde cuándo no se mueve |
| **Actividad** | visitas, propuestas, contactos: qué se ha intentado |
| **Propietario** / Cliente / Agente | quién está al otro lado y cuándo se le habló |

No repiten `CÓMO ESTÁ`, que habla del **estado**; esto es el **historial**. Y no
pretenden ser el expediente: `Ver expediente completo →` sigue llevando a la
pantalla real del SPA.

---

## 10.3.3 Ningún código técnico en el texto

«Abierta el 22 jul · **OPO-0098**» no le dice nada a nadie. Quien opera
identifica la operación por **la dirección y la persona**, no por un
consecutivo, y el código ocupa el sitio de algo que sí se usa.

**Los códigos salen de todo el texto visible** —títulos, identidades,
expediente, `Qué cambió`, `Qué viene`— y en su hueco va información real: los
días en curso, quién es el propietario, cuánto se pide. Siguen vivos donde
tienen que estar: en las **rutas del `href`**, que es lo que el sistema
necesita y el usuario no ve.

Una comprobación recorre el `innerText` de la pantalla entera y falla si
encuentra un solo `AAA-0000`.

---

## 10.3.4 Todo texto del broker habla del equipo

> **Si una frase del broker se puede leer igual en el Inicio de un agente,
> está mal escrita.**

Es la regla que faltaba y la que más fácil se incumple, porque los textos se
escriben una vez y se reaprovechan. El caso que la destapó:

> ~~«Mañana a las 09:00, Av. Colonial 780 entra en tu cola.»~~
> **«Mañana, Valentina Mora visita Av. Colonial 780. Nada tuyo antes.»**

Una bandeja no se llena a las nueve en punto, y a un broker con la bandeja
limpia no le interesa cuándo caerá su próxima decisión: le interesa **qué mueve
su equipo**. Se repasaron los cuatro sitios donde el broker hablaba como agente:

| Antes | Ahora |
|---|---|
| «Av. Colonial 780 entra en tu cola» | «Valentina Mora visita Av. Colonial 780» |
| «3 captaciones entran en cola» | «3 captaciones **de Diego Ruiz** cumplen plazo» |
| «Encargo de Jr. Ica 118» | «Jr. Ica 118 · **Valentina Mora**» |
| «Av. Colonial 780 · tu plazo» | «Av. Colonial 780 · **vence** tu plazo» |

Y la simétrica del agente: una decisión que toma otro, en su agenda, tiene que
decir de quién es —«Petit Thouars 259 · **la toma el broker**»— o parece suya.

---

## 10.4 Todo hecho nombra su objeto

Regla de lenguaje, y es de las que más se incumplen sin darse cuenta:

> **Ningún hecho puede obligar a preguntar «¿de qué?».**

`Qué cambió` decía:

> María Torres completó el expediente.
> La decisión vuelve al broker.

En una bandeja de 74 operaciones eso no identifica nada: el broker no sabe qué
expediente ni de qué inmueble, y tiene que ir a buscarlo. Con el objeto delante:

> María Torres entregó los documentos que faltaban.
> Solicitud SOL-0114 · Petit Thouars 259 · espera tu decisión.

Lo mismo con «Arenales 380 fue aprobado» (¿quién lo aprobó, y qué se puede hacer
ahora?) → «El broker aprobó la captación de Arenales 380. El local ya se puede
publicar.» **El hecho, su objeto y qué habilita**, en dos líneas.

Una comprobación automática vigila que ningún renglón de `Qué cambió` se quede
sin código, dirección o nombre propio.

---

## 11. Regla de oro contra la duplicación

**Un hecho puede existir en varios niveles de datos, pero tiene una sola
representación principal en el Inicio.**

- Si `Av. Arequipa vence el 22` todavía no necesita acción → vive en `Qué viene`.
- Si entra en la cola de atención → **deja de ser una fila de agenda** y queda la
  referencia: *«Av. Arequipa 1840 · ya está en tu atención (02)»*.
- Igual con un hallazgo: si se convirtió en `03 Cafetería · Miraflores`, el Radar
  dice *«↗ Está en tu atención · 03»* y **no vuelve a pedir otra acción**.

La separación se hace **antes** de recortar la lista, para que el recorte nunca
se coma la referencia.

---

## 12. Estabilidad durante la operación

Los datos cambian en tiempo real; la posición visual no.

- No se mueve la fila seleccionada.
- No se reordena mientras el usuario opera.
- No se desplazan botones ni se cierra una recomendación.

Al terminar, un aviso corto y el relevo:

```
✓ Partida adjuntada   La captación volvió a revisión del broker.
```

La fila sale con una transición discreta y la siguiente entra en el puesto 05.

---

## 13. Lo urgente pregunta, no se cuela

Si aparece algo que merece romper el foco, **no desplaza al 01 en silencio**:

```
NUEVO · REQUIERE ATENCIÓN INMEDIATA
Carlos Mejía respondió sobre Petit Thouars 259.          Ver →
```

Solo cuando el usuario lo acepta, BROX recompone el foco. Es un canal de
excepción, no la forma habitual de trabajar.

---

## 14. Volver tras varios días no cambia la filosofía

No se añaden veinte filas. Una línea discreta arriba:

```
DESDE TU ÚLTIMA VISITA   8 cambios relevantes · 3 asuntos nuevos
```

El despacho reconstruye los cinco asuntos que **ahora** merecen atención y el
Radar general explica qué ocurrió mientras tanto. El agente vuelve al estado
actual del negocio, no a un historial de culpa.

---

## 14.1 Día cubierto: cerrar la lista es un logro y se nota

No es un aviso gris. Es lo mejor que puede pasarle a un día de trabajo.

- **Un sello que se completa**: el anillo se cierra y después se dibuja la marca.
  Un solo gesto, ~1,3 s, y se apaga con `prefers-reduced-motion`.
- **Un titular propio de cada rol**: *«Cerraste tu lista.»* para el agente,
  *«Tu bandeja está limpia.»* para el broker.
- **El dato que lo hace valioso sale de la propia organización**, no de una
  estadística del sector inventada. BROX sabe cuántas veces lo has conseguido
  este mes y cuántos del equipo lo lograron hoy:

  > Es el **cuarto día** que la cierras este mes. Hoy lo han conseguido **2 de
  > los 8 agentes** del equipo.

  Para el broker: *«Es la segunda vez este mes. Los otros días cerraste con 3
  decisiones pendientes de media.»*

- Debajo, **lo revisado** (que es lo que da permiso de irse) y **lo siguiente**.

**Un solo titular.** En un día cubierto la tarjeta es la protagonista y el
titular de la cabecera desaparece: dos frases diciendo lo mismo repartían la
atención en vez de sumarla.

---

## 14.2 Los cuatro escenarios son distintos de verdad

`Normal · Riesgo · Solo 2 accionables · Vuelve tras 3 días · Día cubierto` viven
en una tabla, no en condicionales sueltos. Llegó a pasar que **«Riesgo» era
idéntico a «Normal»**: al mover el Radar a por-rol, sus campos quedaron muertos
y el andamio dejó de ofrecerlo sin que nadie lo notara.

Un día de riesgo **no es «lo mismo en rojo»**: el escenario acorta la **ventana
temporal** de varios asuntos y la política de despacho reordena sola. No hay una
lista escrita a mano que mantener en dos sitios. Además trae su propio hallazgo
—*«Tres encargos vencen la misma semana»*— y su propia pastilla.

---

## 15. Móvil

No conviven lista y Radar. Primero `Qué atender ahora`; seleccionar un asunto
abre la resolución a pantalla completa con `← Volver`, en el mismo orden:
recomendación → para qué → acción → cómo está → antecedentes opcionales. **Mismo
modelo conceptual, distinto comportamiento.**

A 402 px no basta con apilar: cada pieza se reorganiza a lo que cabe, sin perder
nada.

| Pieza | En escritorio | En móvil |
|---|---|---|
| Accesos rápidos | cuatro en fila | **2 × 2**, con su icono |
| Pie | cifra + cuatro KPI en fila | cifra arriba, **KPI en 2 × 2**, enlace al final |
| Radar | panel acotado con scroll propio | ocupa la pantalla; scrollea la página |
| Firma de fondo del pie | derecha, 7 % | más apartada y al 3,5 %: en 402 px se cruzaba con los rótulos |

El tope del Radar **no se aplica en móvil**: ahí no hay rejilla acotada que
respetar y limitar su alto solo escondería contenido.

---

## 16. Métricas de la maqueta, para el frontend

| | |
|---|---|
| Izquierda / derecha | 66 % / 34 %, gap 22 px |
| Fila del foco | 56–68 px de alto |
| Banda de la cola | una sola altura, ~56 px |
| Separadores entre filas | cortos, no de lado a lado |
| Fila seleccionada | fondo azul hielo + filo vertical azul de 2 px, sin tarjeta dentro de tarjeta |
| Cabecera del Radar | degradado `#2E6DF3 → #1D4ED8`; cuerpo `#F3F7FE` |
| Superficie del Radar | sombra tintada `0 20px 44px -24px rgba(37,99,235,.34)` — se levanta del lienzo |
| Recomendación | placa blanca inset, radio 11, filete propio bajo el rótulo |
| Tipografía | Geist Sans embebida (ver §17) |

### 16.1 La pantalla no scrollea… mientras el contenido quepa

**En escritorio (≥1241 px de ancho y ≥700 px de alto) el estado de entrada cabe
entero: cinco asuntos, la cola, los accesos y el Radar, sin scroll.** Y en modo
resolución tampoco, ni con los antecedentes desplegados.

**Pero el scroll no se prohíbe.** En cuanto el foco crece —al traer asuntos desde
la cola— la página vuelve a scrollear, porque si no, lo de abajo (la banda de la
cola y los accesos rápidos) queda **fuera de alcance**. Se probó con
`height: 100vh; overflow: hidden` y era exactamente ese el defecto.

Quien no puede empujar nunca es el **Radar**: se le pone un tope y su cuerpo
scrollea por dentro. Dos trampas en el camino:

- Un tope en `vh` **no sirve**: no conoce lo que ocupan la cabecera de arriba ni
  el pie de abajo. El valor se calcula midiendo dónde empieza la rejilla y cuánto
  mide el pie (`medirRadar`), y lo vigila un **`ResizeObserver`** sobre el saludo,
  el titular, el aviso de ausencia y el pie: si cualquiera cambia de alto —el
  titular partiendo en dos líneas, el pie creciendo— el tope se ajusta solo, sin
  depender de que alguien se acuerde de llamar a la función.
- El Radar va **`position: sticky`**: al recorrer una cola larga sigue acompañando
  en lugar de perderse por arriba.

Comprobado a **1770 × 832**: 832 de documento en los dos modos y con los
antecedentes abiertos; con tres asuntos traídos a mano son 900 y **sí scrollea**,
con los accesos rápidos alcanzables al final.

---

## 17. Lo que hereda del sistema visual y no se toca

Tokens, tipografía, bordes y densidad del prototipo anterior: lienzo `#F6F6F3`,
tinta `#14191E`, oro `#E0A11B` para la marca y el filo superior de la zona de
trabajo, radios 14/9, `font-variant-numeric: tabular-nums`, y **Geist Sans
embebida en base64 dentro del `<style>`**. Sin ella la pantalla cae a fuente de
sistema y se pierde el acabado.

El texto, en **lenguaje llano de negocio**: el hecho con su cifra y su fecha, y
luego qué hacer. Sin metáforas.

---

## 18. Criterios de aceptación

Comprobables, y comprobados en la maqueta:

- [x] Nunca más de 5 en el foco; con 2 accionables se ven 2 y no hay banda de cola
- [x] Ninguna fila del foco contiene un `<button>` de acción
- [x] Ninguna etiqueta de clasificación en el texto del foco
- [x] No hay buscador en el Inicio ni panel «¿Cómo elige BROX?»
- [x] La segunda línea del titular nombra el motivo del primero y mide < 70 caracteres
- [x] En toda la cabecera del Radar hay **exactamente una** animación (`sinapsis`), bloom por filtro SVG y ninguna capa de fondo — sin una sola imagen
- [x] `RADAR BROX` en 15 px con degradado, y el subtítulo dice cuántas operaciones vigila
- [x] Cuatro accesos rápidos, **distintos por rol**, debajo del foco y con rutas reales
- [x] El pie lleva los **cuatro KPI canónicos de D-E2-2** con su meta y su ritmo, enlaza entero a Indicadores y no repite ninguna cifra del foco
- [x] Y es gráfico: barra por KPI **con la marca del ritmo esperado**, curva de seis meses en la cifra del mes y la malla de BROX al 7 % de fondo
- [x] Los antecedentes son cuatro renglones de expediente comercial, y al abrirlos el Radar **scrollea hasta ellos**
- [x] La línea de contexto habla de dinero y fechas, no de recuentos
- [x] Traer un asunto desde la cola lo añade al final y **no cambia** el orden de los demás
- [x] La página no scrollea a 1770 × 832 en el estado de entrada, en los dos modos y con antecedentes abiertos
- [x] **Sí scrollea** cuando el foco crece, y el final de la columna queda alcanzable
- [x] `Abrir …` y `Ver expediente completo` apuntan a rutas reales del SPA **y no las muestran en pantalla**
- [x] La cola restante es una banda, no una tabla (`0` filas dentro)
- [x] El Radar tiene exactamente dos modos y vuelve a general con un control
- [x] En resolución, el orden es recomendación → para qué → cómo está
- [x] La palabra «por qué» no aparece en el Radar
- [x] `CÓMO ESTÁ` lleva como máximo 3 hechos, **cada uno con su propio estado**; `PRÓXIMO` solo si existe
- [x] Un hecho resuelto sale en verde aunque el asunto esté en rojo, y lo que frena la operación sale en rojo aunque el asunto esté en verde
- [x] La barra de avance existe solo donde hay requisitos contables, y no repite lo que ya dice la identidad del caso
- [x] En los antecedentes, el rótulo y el dato se distinguen por tamaño, caja y tinta
- [x] Cada renglón del expediente lleva su estado en el fragmento con la señal, y **como máximo dos por expediente llevan color**
- [x] Toda barra lleva su razón (`168/180`) y se dibuja al abrir, no antes
- [x] La renta con serie muestra su chispa; una línea plana se lee como lo que es
- [x] El cuerpo del Radar es una **pila de tarjetas** sobre sustrato, no bandas con filete
- [x] Los bloques aparecen **por turnos**, de arriba abajo, a 55 ms
- [x] **Toda sección lleva su icono en placa**, y los cuatro renglones del expediente también
- [x] La ficha de identidad dice **en qué paso de los seis** está el asunto
- [x] La recomendación declara **cuántas señales cruza**, y al pulsarlas se iluminan los hechos
- [x] El emblema **no se anima**; la única animación de la cabecera es el punto de actividad
- [x] La ruta usa **la cadena del lado**: 3 pasos para el propietario, 4 para el cliente, y no comparten ninguno
- [x] El foco tiene **dos filtros, `Propietario` y `Cliente`**, y filtrar **no renumera**
- [x] Mezclados, cada fila lleva su lado en el fondo, con temperaturas que **no son las del semáforo**
- [x] El expediente abre con **la lectura de BROX**, que sintetiza sin recitar
- [x] El contraste sitúa el dato contra **la operación de la casa**, nunca contra el sector
- [x] El Radar **navega entre dos vistas** en vez de desplegar, y desde 940 px de alto ninguna scrollea
- [x] En resolución la **cabecera dice de qué asunto se trata**, y no se repite debajo
- [x] Los rótulos del expediente **no llevan icono**, y llevan **el color de su estado**
- [x] Los pasos de la ruta van **con su nombre**, no numerados
- [x] `Resolver | Antecedentes` es una **barra de pestañas** con filete y cuenta, no una cápsula
- [x] La fila seleccionada se marca en **grafito**: ni azul BROX ni el color de su lado
- [x] Ninguna cápsula de 999 px sobrevive en el panel; el radio máximo es 8 px
- [x] **Ningún código técnico** aparece en el texto visible de la pantalla
- [x] **Ninguna sección lleva icono**; todas llevan canto de color, y `Qué cambió` / `Qué viene` no son dos grises
- [x] Cada renglón del expediente lleva su señal **si existe**, y ninguna señal está sin portador
- [x] El haz del botón se ve **también al pasar por encima**, y da una vuelta entera por cada ejecución
- [x] La ruta enseña **solo el paso actual**: PROPIETARIO | CAPTACIÓN, las dos con el mismo peso
- [x] En la identidad, **la persona, el dinero y el plazo** se distinguen entre sí
- [x] La cifra de cada sección **cuadra por centro** con su rótulo
- [x] Toda consecuencia se dice desde el lado de **quien la lee**, no desde la mecánica del sistema
- [x] **Ningún texto del broker se puede leer igual en el Inicio de un agente**
- [x] Cada KPI del pie dice **a cuánto estás de la meta** antes que el ritmo, y usa la misma frase que Indicadores
- [x] Al broker **nunca** se le pide producción personal: su segunda línea habla del equipo (D-E2-2 §4)
- [x] El pie del broker lleva el **pulso del equipo**, una sola vez y sin nombres
- [x] La cifra en juego del broker dice **cuánto depende de una decisión suya**
- [x] El botón de acción pasa por **trabajo y confirmación** antes de resolver
- [x] Los dos lados se ven de reojo: **oro para el propietario, azul para el cliente**
- [x] Ningún hecho de `Qué cambió` obliga a preguntar «¿de qué?»
- [x] Lo que está en la cola aparece en `Qué viene` como referencia, no como fila
- [x] El hallazgo enlaza a su número y no ofrece una segunda acción
- [x] La acción pequeña se ejecuta en el Radar; la compleja abre el expediente
- [x] El orden no cambia al navegar entre casos
- [x] La irrupción no entra al foco hasta que el usuario la acepta
- [x] En móvil, seleccionar oculta la lista y muestra la resolución
- [x] A 402 px nada se sale a lo ancho: accesos y KPI en 2 × 2, y ningún rótulo del expediente se corta
- [x] Los cuatro KPI llevan su **nombre canónico completo**, en agente y en broker
- [x] El broker tiene **su propio** hallazgo, sus propios movimientos y su propia agenda
- [x] La regla del hogar único vale también para el broker: su encargo no aparece a la vez en la cola y en la agenda
