# KAIROS — asistente conversacional

Prototipo de un canal conversacional sobre las capacidades públicas de BROX: registrar una
propiedad hablando, preguntar qué encaja con qué y por qué, y saber qué hay que resolver hoy.

**Es un proyecto aparte, y eso es la decisión, no un accidente.** No comparte proceso, ni base de
datos, ni jar con BROX; queda fuera del reactor de `backend-spring/`. Comparte **el contrato**, y
la dependencia va en una sola dirección: KAIROS conoce a BROX, BROX no conoce a KAIROS. Lo
sostiene una prueba,
[`FronteraKairosTest`](../backend-spring/controllocal-app/src/test/java/com/controllocal/arquitectura/FronteraKairosTest.java),
que rompe el build de BROX si alguien invierte la flecha.

Que sea un servicio separado es lo que permite que el mismo Core atienda a la SPA y a un canal de
mensajería sin que ninguno de los dos herede las decisiones del otro.

```bash
mvn -f kairos-service/pom.xml test
```

---

## Configuración

KAIROS **no tiene credenciales propias**, y eso no es una carencia: es la regla 2 de
[D-K-1](../docs/ai/decision-kairos-contrato-de-acciones.md). Un turno viaja con el token de la
persona que conversa, sin token de servicio ni cuenta de sistema. Por eso lo único que hay que
configurar es a qué Core hablar.

| Propiedad | Por defecto | Qué es |
|---|---|---|
| `brox.url` | `http://localhost:8090/controllocal/Api` | la API del Core |
| `kairos.agente` | `KAIROS` | lo que se escribe en `X-Agente` del rastro |
| `kairos.modelo` | `determinista` | con qué razonó el turno |
| `kairos.modelo-version` | `0.1.0` | y en qué versión; irreconstruible después del despliegue |

No hay usuario, ni contraseña, ni clave de API. Si algún día hiciera falta autenticación
**entre servicios**, sería una decisión nueva y se anotaría aquí: hoy no existe.

El token de la persona llega en la cabecera `Authorization` de `POST /kairos/turnos` y se
reenvía tal cual. KAIROS no lo interpreta ni decide nada con él —no sabe qué rol tiene ni a qué
organización pertenece hasta que el Core se lo dice—, porque un asistente que resolviera
permisos por su cuenta sería una segunda política de autorización, y la segunda es la que nadie
audita.

### Trazabilidad: qué cabeceras viajan

Todas las llamadas —**también las lecturas**— salen con la procedencia del turno. Estas ocho las
lee `ProcedenciaDeCabeceras` del Core:

```
X-Canal           SPA · WHATSAPP · API · SISTEMA. Lo declara el adaptador del canal
X-Agente          KAIROS
X-Agente-Modelo   con qué modelo razonó
X-Agente-Version  y en qué versión
X-Conversacion    de qué conversación
X-Turno           de qué turno exacto
X-Mensaje         el mensaje del canal; es la clave de idempotencia
X-Peticion-B64    lo que la persona escribió, en base64 (una cabecera HTTP es ASCII)
```

Y una novena que **el Core todavía no lee**:

```
X-Herramienta     cuál de las del catálogo se invocó
```

`Procedencia` del Core ya tiene el campo `herramienta` y el método `invocando(…)`, pero
`ProcedenciaDeCabeceras` no lee ninguna cabecera que lo llene y `invocando(…)` no tiene ni un
llamador. KAIROS la manda igual —una cabecera que nadie lee no rompe nada— y el día que el Core
la lea, el rastro queda completo sin desplegar KAIROS.

---

## Catálogo de herramientas

Una herramienta es **un caso de uso que ya existe, nombrado**. Nunca un camino propio a la base:
KAIROS no tiene el driver de PostgreSQL en su `pom.xml`, y esa ausencia es deliberada.

Cada una declara qué invoca, qué permiso exige —la celda de la matriz operación→rol, **copiada**,
no interpretada— y cuánta confirmación humana necesita. El catálogo vive en
`com.kairos.herramientas` y lo comprueba `HerramientasTest`.

| Herramienta | Caso de uso | Permiso | Confirma | Estado |
|---|---|---|---|---|
| `buscar_propiedades` | `GET /locales` | TODOS | nunca | viva |
| `estado_de_propiedad` | `GET /propiedades/{id}` | TODOS | nunca | viva |
| `buscar_persona` | `GET /clientes` | TODOS | nunca | viva |
| `buscar_coincidencias` | `GET /{origen}/{id}/coincidencias` | TODOS | nunca | viva |
| `explicar_match` | `GET /{origen}/{id}/coincidencias` | TODOS | nunca | viva |
| `que_resolver_hoy` | `GET /dashboard` | TODOS | nunca | viva |
| `capturas_en_curso` | `GET /captura` | TODOS | nunca | viva |
| `consultar_capacidades` | `GET /capacidades` | *sin fila* | nunca | **el Core no la sirve** |
| `catalogo_de_captura` | `GET /captura/definicion` | TODOS | nunca | viva |
| `resultados_de_interaccion` | `GET /interacciones/resultados` | *sin fila* | nunca | **el Core no la sirve** |
| `abrir_sesion_captura` | `POST /captura` | TODOS | avisa | viva |
| `responder_captura` | `POST /captura` | TODOS | avisa | viva |
| `registrar_interaccion` | `POST /interacciones` | AGENTE | avisa | viva |
| `registrar_propietario` | `POST /propietarios` | AGENTE | avisa | viva |
| `confirmar_captura` | `POST /captura/{id}/ejecutar` | AGENTE | **siempre** | viva |
| `publicar_encargo` | `POST /encargos/{idEncargo}/publicaciones` | AGENTE | **siempre** | declarada |
| `presentar_oferta` | `POST /locales/{id}/precios` | AGENTE | **siempre** | declarada |
| `decidir_captacion` | `POST /captaciones/{id}/decision` | BROKER | **siempre** | declarada |
| `evaluar_solicitud` | `POST /evaluaciones` | BROKER | **siempre** | declarada |
| `cerrar_encargo` | `POST /captaciones/{id}/cierre` | BROKER | **siempre** | declarada |
| `registrar_cobro` | `POST /contratos/{idContrato}/comision/movimientos` | BROKER | **siempre** | declarada |
| `enviar_reporte` | `POST /captaciones/{idCaptacion}/reportes-propietario` | AGENTE | **siempre** | declarada |

**«Declarada» significa que KAIROS todavía no la invoca**, no que esté a medias. Están escritas
con su caso de uso y su confirmación para que el día que se implementen no haya que volver a
decidir si publicar sale al exterior. Una intención que apuntara a una de ellas no arranca: falla
al cargar la clase, que es cuando todavía no hay nadie esperando una respuesta.

**El permiso no autoriza nada.** Es una declaración, copiada de la matriz para poder compararla.
Quien decide es el Core con el token de la persona, y KAIROS recibe el mismo 403 que recibiría el
navegador. Con la sesión capada —contraseña temporal o MFA pendiente— el Core bloquea igual y
KAIROS lo dice.

### Las tres puertas del mismo matching

`buscar_coincidencias` lleva `{origen}` en la ruta porque el mismo caso de uso tiene tres puertas
en el Core y las tres devuelven la misma forma:

```
GET /clientes/{id}/coincidencias               cliente     → propiedades
GET /captaciones/{idOrCodigo}/coincidencias    encargo     → clientes
GET /prospecciones/{id}/coincidencias          prospección → clientes
```

`explicar_match` **es la misma lectura**, no una consulta nueva: el Core devuelve la explicación
dentro de cada ítem, así que explicar es quedarse con la que la frase nombra. Evaluar dos veces
habría dado dos puntajes para el mismo par, y el segundo es el que empieza a discrepar.

La respuesta cuenta criterios en tres cubos —`cumple`, `noCumple`, `porConfirmar`— para poder
decir *«cumple 4 de 5; falta confirmar la potencia eléctrica»* en vez de un porcentaje sin causa.
**El Core manda hoy dos**; el tercero llega vacío y `evaluados` vale `cumple + noCumple`, que es
exactamente lo que el Core evalúa. Cuando el tercero llegue, esta capa no cambia.

---

## Qué se confirma, y por qué

```
catálogo de KAIROS   suelo: lo sensible se confirma SIEMPRE      (D-K-1 §3.3)
       +
capacidad del Core   puede exigir MÁS, nunca menos               (D-K-1 §4)
```

- **nunca** — lectura. No escribe: no hay nada que confirmar.
- **avisa** — escritura reversible. Se ejecuta y se dice que se hizo. Reversible no es una
  opinión: es que existe **otra acción del sistema** que la deshace.
- **siempre** — escritura sensible. Exige un turno **aparte** en el que la persona diga que sí. Si
  la confirmación pudiera llegar en el mismo turno que propone, bastaría una frase ambigua para
  publicar, firmar o cobrar.

El criterio, dicho una vez: si la acción **sale de la organización**, **firma algo**, **mueve
dinero** o **no se puede deshacer con otra acción del sistema**, la confirma una persona.

La composición va siempre hacia el lado estricto. Una capacidad declarada `AUTO` por el Core
**no** apaga la confirmación de `confirmar_captura`: si pudiera, un error de configuración del
Core bastaría para que una conversación diera de alta inmuebles sola. Al revés sí — el Core puede
pedir confirmación donde el catálogo no la exige, y KAIROS la pide.

**Sin turno de confirmación, KAIROS dice qué haría y no ejecuta.** Eso vale también para las siete
sensibles que están declaradas y no implementadas.

El alta conversacional usa **tres herramientas y sólo la tercera crea algo**:

```
abrir_sesion_captura   POST /captura                  no escribe nada del negocio
responder_captura      POST /captura (con borrador)   tampoco
confirmar_captura      POST /captura/{id}/ejecutar    crea propiedad, titularidad y encargos
```

Abrir y responder van por la **misma ruta** y aun así son dos herramientas: el rastro tiene que
poder decir cuál de las dos fue, y el permiso **no se hereda** de una a otra. Deducir que quien
abre puede responder sería conceder una capacidad que nadie declaró.

---

## Qué está construido y qué no

Hay intérprete determinista, máquina de conversación, cliente HTTP con trazas y **nueve acciones**
—consultar propiedad, consultar cliente, iniciar captura, continuar borrador, registrar
propietario, registrar interacción, buscar coincidencias, explicar match y qué resolver hoy—.

**Lo que le falta al Core** (sale del catálogo, no de esta lista: `Herramientas.sinPuertaEnElCore()`):

- **`GET /capacidades` no existe.** KAIROS lo pide en cada turno para saber qué puede hacer en esa
  sesión, y responde 404. Su ausencia se lee como «no publicado» —manda entonces el catálogo de
  KAIROS y decide el Core con su 403—, nunca como «no puedes»: leerla como negativa dejaría a
  KAIROS sin poder atender un solo turno. Diseñar ese endpoint —representación, alcance por
  sesión, confirmaciones, versionado— es una decisión pendiente. Y tiene que publicar **nombres de
  herramienta**, no rutas: `abrir_sesion_captura` y `responder_captura` comparten ruta y son dos
  capacidades distintas.
- **`GET /interacciones/resultados` no existe.** Choca con `GET /interacciones/{id}`, que espera un
  número, y responde 400. La lista vacía se lee como «no publicado» y KAIROS manda lo que la frase
  dijo para que el Core lo rechace con su explicación — nunca inventarse la lista.
- **`X-Herramienta` no se lee.** Ver arriba.
- **La idempotencia llega más lejos que el servidor**: KAIROS manda `Idempotency-Key` al registrar
  propietarios e interacciones, y esos controladores todavía no la consumen como sí hacen los de
  captura.

**Lo que le falta a KAIROS:**

- **No hay adaptador de WhatsApp** en el repositorio. Si algún comentario del código habla de esa
  integración en presente, describe el objetivo, no el estado.
- **Falta decidir cómo se resuelve la identidad humana** en un canal como mensajería. Tiene que
  resolverse **técnicamente**, nunca preguntándoselo al modelo.
- Las siete sensibles declaradas y sin implementar.

Mientras esas cosas no se resuelvan, esto es un prototipo que demuestra la forma, no un canal en
operación. La decisión de si KAIROS es exploración, producto o base contractual está abierta.

---

## Las pruebas y el catálogo grabado

Las pruebas corren **sin BROX levantado** —es la mitad del argumento de haberlos separado—, pero
**no contra un catálogo inventado**. `src/test/resources/catalogo/` son las siete respuestas
literales de `GET /captura/definicion` de los siete tipos, grabadas contra el Core de desarrollo.
No se editan a mano: se vuelven a grabar.

```bash
curl "$BROX_URL/captura/definicion?tipoPropiedad=TERRENO" -H "Authorization: Bearer $BROX_TOKEN"
```

El riesgo de una foto es que envejezca en silencio, y contra eso está `CatalogoVivoTest`: cuando
hay un Core al que preguntar pide los siete tipos y compara, **diciendo la clave** que difiere.

```bash
BROX_URL=http://localhost:8090/controllocal/Api BROX_TOKEN=<jwt> mvn -o test
```

Va desactivada por defecto porque una prueba que necesita red no puede decidir si el build pasa.
**Y no corre dentro del harness de agentes**: las JVM de ese entorno no levantan la conexión de
loopback (`Unable to establish loopback connection`) aunque `curl` alcance el mismo Core sin
problema. Ahí la comparación se hace con `curl` y un diff.
