# Backend del Inicio: qué hay, qué falta y en qué orden hacerlo

**Qué responde:** cuánto del Inicio diseñado en D-E2-1 se puede construir con
el backend de hoy, qué falta de verdad, y el orden de desarrollo con su
checklist.

**Verificado el 2026-08-11** leyendo `backend-spring`: 32 controladores,
`TareaServiceImpl`, `PoliticaComercial`, `IndicadorServiceImpl`,
`CoincidenciaCartera`.

**Diseño que tiene que sostener:** `decision-inicio-foco-y-resolucion.md`
(D-E2-1) y `traspaso-inicio-a-angular.md`.

---

## 1. La respuesta corta

**Hay bastante más de lo que parece, y falta menos de lo que parece.** El motor
que deriva la cola del estado real existe, funciona y está probado. Lo que no
existe es la **capa de interpretación** que el prototipo pone encima.

| Pieza | Estado |
|---|---|
| Motor que **deriva** la cola del estado real | ✅ 7 disparadores, con dedup y auto-resolución |
| Umbrales de negocio en un solo sitio | ✅ `PoliticaComercial`, cerrado en E1 |
| Hechos ya interpretados en el cable | ✅ `senales[]` en `/indicadores/resumen` |
| Motor de **coincidencias** (la semilla del hallazgo) | ✅ con puntaje y umbral |
| Motor que **prioriza** | 🟡 existe, pero con 2 criterios de los 6 que pide el diseño |
| `DEPENDE_DE_MI` | ❌ no existe, y es el **primer filtro** del despacho |
| Cola **del broker** | ❌ `/tareas` es `hasRole('AGENTE')`, por decisión explícita |
| `ComoEsta`, `lectura`, `contraste` | ❌ nada |
| Metas por KPI | ❌ no están en el modelo |
| Tiempo real | ⚠️ no hay socket ni planificador — **y no hace falta** (§5) |

---

## 2. Lo que ya está construido

### 2.1 El motor de la bandeja

`TareaServiceImpl` deriva, reconcilia, enriquece, ordena y corta. **Siete
disparadores**, cada uno con su consulta y la condición bajada al `WHERE`:

| | Disparador | Prioridad | Origen |
|---|---|---|---|
| 1 | Recontactos vencidos | ALTA | `prospecciones.paraRecontactar` |
| 2 | Solicitudes aprobadas sin cierre | ALTA | `solicitudes.porEstadoDelAgente(APROBADA)` |
| 3 | Comisiones listas para cobro | MEDIA | `contratos.conComisionListaParaCobro` |
| 4 | Documentos observados | ALTA | `solicitudes.porEstadoDelAgente(OBSERVADA)` |
| 5 | Visitas caídas, vencidas o próximas | ALTA / MEDIA | `visitas.queExigenAccion` |
| 6 | Reporte periódico al propietario | MEDIA | `captaciones.activasDelAgente` |
| 7 | **Coincidencias de cartera** | MEDIA | `CoincidenciaCartera.evaluar` |

Y una decisión de arquitectura que vale oro para el Inicio: **las lecturas
reconcilian** (D-F7-3). La bandeja se pone al día sola en cada `GET`, sin
planificador. Registrar el reporte resuelve su tarea; subsanar el documento
resuelve la suya.

### 2.2 La política única

De E1. Siete reglas con nombre, valor, unidad, alcance y versión. Y un
`enum Concepto` donde **cada concepto ya trae su nivel de atención y su orden**:

```
SOLICITUD_POR_EVALUAR          ALTO   1   COSAS
RECONTACTO_VENCIDO             ALTO   2   COSAS
CAPTACION_POR_REVISAR          MEDIO  3   COSAS
SOLICITUD_APROBADA_SIN_CIERRE  MEDIO  4   COSAS
DEMORA_DE_SEGUIMIENTO          MEDIO  5   MAGNITUD   ← días, no conteo
```

Ese es exactamente el vocabulario que el Radar necesita para el tono y para
`Cómo está`. **No hay que inventarlo: hay que exponerlo por asunto** en vez de
solo agregado por periodo.

### 2.3 El motor del hallazgo, escondido dentro de una tarea

`CoincidenciaCartera.evaluar(requerimiento, propiedad)` devuelve un **puntaje** y
`PoliticaComercial.valeLaPenaProponer(puntaje)` decide el umbral. Hoy eso se
emite como **tarea**: *«Coincidencia de cartera (78 %): propón CAP-0034 a un
cliente interesado.»*

> **El hallazgo del prototipo es ese mismo motor con otra salida.** No es
> trabajo nuevo: es dejar de meterlo en la cola y publicarlo como hallazgo, con
> los criterios que coinciden y el que falta. Es la subtanda más barata de
> todas y la que más se nota.

### 2.4 Lo que se reutiliza tal cual

`GET /dashboard` ya compone resumen + bandeja en un round-trip. `/indicadores/
resumen` trae `senales[]`. `DocumentosController` sirve la acción `ARCHIVO` e
`InteraccionesController` la acción `REGISTRO`. Los destinos de `Ver expediente
completo` son rutas reales del SPA.

---

## 3. Lo que falta, y por qué en ese orden

El criterio de orden es **qué desbloquea a qué**, no qué es más fácil:

1. Sin `DEPENDE_DE_MI` el foco es la bandeja de siempre con otro nombre. Va
   primero, y **pegado a la política de despacho** porque tocan el mismo
   comparador y la misma consulta: separarlos obliga a pasar dos veces por el
   mismo código.
2. El hallazgo se adelanta porque **el motor ya está hecho** y es lo que sostiene
   la promesa entera del Radar. Cuesta poco y se nota mucho.
3. La capa de interpretación (`ComoEsta`, `lectura`, expediente) sale toda de la
   misma vista compuesta por asunto: una sola tanda.
4. La cola del broker exige **revisar una decisión anterior**, así que va cuando
   lo demás esté probado con el agente.
5. Las metas van al final: es el bloque más caro y el que menos desbloquea. El
   Inicio funciona sin ellas, con el pie degradado a avance sin marca de ritmo.

---

## 4. El plan: cinco tandas

Cada tanda termina con algo que se abre en `localhost:4200` y se evalúa a ojo, y
cierra con **gate + pruebas + evidencia**, como el resto de E2.

---

### Tanda 1 · La pelota y el orden

**Qué se ve al terminar:** el foco deja de mostrar lo que espera a un tercero, y
el 01–05 no baila entre recargas.

- [ ] **`DEPENDE_DE_MI` como campo del dominio.** Cada disparador ya sabe quién
      tiene la pelota; se hace explícito en `Derivada` y viaja en `Tarea`.
      *Cuidado:* el disparador 2 (aprobada sin cierre) y el 3 (comisión lista)
      dependen del agente; el 4 (observados) también; una captación en revisión
      **no** — espera al broker.
- [ ] **`lado` y `paso`** (`OFERTA`/`DEMANDA` + el paso de su cadena). Es un
      `switch` sobre `entidadTipo`. **Son dos cadenas disjuntas**, no una de
      siete (D-E2-1 §7.0.d).
- [ ] **La política de despacho de seis criterios** en `service/soporte`, con
      los pesos en `PoliticaComercial`: ventana temporal, ventana de
      oportunidad, desbloqueo, antigüedad con tope y **estabilidad**.
- [ ] **`motivoDelPrimero`**: la frase de cuatro o cinco palabras que explica
      por qué el 01 es el 01. Sale del disparador, no se redacta en Angular.
- [ ] Pruebas: que un asunto que espera al broker **no** entre en el foco; que
      dos lecturas seguidas devuelvan el mismo orden; que las dos cadenas no
      compartan ningún paso.

### Tanda 2 · El hallazgo sale de la cola

**Qué se ve al terminar:** el Radar tiene su hallazgo destacado, y la
coincidencia deja de ocupar un sitio del foco.

- [ ] **`Hallazgo` como objeto propio**, no como tarea. Mismo motor
      (`CoincidenciaCartera`), otra salida: título, cuerpo con **los criterios
      que coinciden y el que falta**, y el `asunto` al que enlaza.
- [ ] **Retirar el disparador 7** de la bandeja. Es la regla del hogar único
      (D-E2-1 §11): un hallazgo no es una tarea, es un aviso con enlace a un
      asunto que ya está en la cola.
- [ ] **`hallazgo.asunto` lleva el id de la cola de ESE rol.** Con un id
      compartido, el mismo encargo sale dos veces en el broker.
- [ ] Pruebas: que retirar el disparador no deje huérfana ninguna tarea ya
      creada; que el hallazgo enlace a un asunto que existe en el foco del rol.

### Tanda 3 · La capa de interpretación

**Qué se ve al terminar:** el Radar en modo resolución muestra `Cómo está`, la
lectura y el expediente de cuatro renglones con datos reales.

- [ ] **`ComoEsta`**: hasta tres hechos con su estado
      (`HECHO`/`FALTA`/`PLAZO`/`FRENO`/`DATO`) y el `avance` cuando hay
      requisitos contables. Es del mismo orden que `senales[]`, un escalón más
      abajo. **El estado lo decide el dominio**, nunca la pantalla.
- [ ] **El expediente de cuatro renglones**: la vista que junta `captacion`,
      `local`, `historico_precio`, `visita` y `propietario`, con el `estado` y la
      `ventana` de cada renglón.
- [ ] **`lectura`**: la frase que sintetiza los cuatro renglones sin recitarlos.
- [ ] **`recomendacion` + `paraQue` + `accion`** por asunto. `ARCHIVO` se apoya
      en `DocumentosController`, `REGISTRO` en `InteraccionesController`;
      **`FECHA` necesita un endpoint de agenda que no existe**.
- [ ] Pruebas: que ningún texto lleve un código `AAA-0000`; que ningún renglón
      con estado se quede sin portador visible.

### Tanda 4 · El Radar del broker

**Qué se ve al terminar:** el broker entra y ve sus cinco asuntos, no los de
nadie más.

- [ ] **Decidir primero, picar después.** `TareasController` es solo del agente
      y está documentado: *«la bandeja no es un tablero de control»*. El
      prototipo le da Radar propio. **Esa decisión hay que revisarla en un
      `decision-*.md`, no saltársela.**
- [ ] Disparadores del broker: captaciones por revisar, solicitudes por evaluar,
      comisiones sin cobrar, concentración de cartera de un agente.
- [ ] **Su propio hallazgo**, que no es de cartera sino de **concentración**: la
      media del equipo no la revela, y por eso vale.
- [ ] `ambito` y los cuatro accesos rápidos por rol.
- [ ] Pruebas: que el foco del broker y el del agente no compartan ids; que la
      matriz `docs/ai/matriz-operacion-rol.md` recoja cada endpoint nuevo — si
      no, el build falla.

### Tanda 5 · Contraste, pie y metas

**Qué se ve al terminar:** el expediente sitúa cada dato contra la operación de
la casa, y el pie enlaza a Indicadores con su marca de ritmo.

- [ ] **`contraste`**: dos agregados que no existen — el **rango de renta por
      zona y metraje** (sale de `historico_precio`, cerrado en E0) y las
      **medias propias** (propuestas por visita, días hasta contrato, plazo real
      de recontacto). Son consultas, no modelo nuevo.
- [ ] **La salida canónica de KPI, completa**. Es el bloque grande de D-E2-2
      (§5 y §13) y hasta que exista el pie se degrada. Cada KPI devuelve, por
      **actor + KPI + periodo**:

      | Campo | Qué es |
      |---|---|
      | `actual` | lo conseguido en el periodo |
      | `metaPeriodo` | la meta vigente del periodo |
      | `metaEsperadaAHoy` | dónde debería ir a día de hoy |
      | `proyeccionCierre` | en cuánto acaba si mantiene este ritmo |
      | `porcentajeProyectado` | esa proyección sobre la meta |
      | `estadoRitmo` | `EN_RITMO` · `ATENCION` · `FUERA_DE_RITMO` · `SIN_BASE` |

      > **Regla arquitectónica: `estadoRitmo` es una conclusión del dominio y se
      > calcula UNA sola vez.** Ni el Inicio ni Indicadores pueden reclasificar
      > en Angular; solo eligen el color con el que dibujan el estado que ya
      > viene decidido. Es la regla 8 de D-E2-2 llevada al cable.
      >
      > **Y el vocabulario de ritmo no se mezcla con el de severidad.** `ALTA` /
      > `MEDIA` describen un asunto de la cola; `EN_RITMO` / `ATENCION` /
      > `FUERA_DE_RITMO` / `SIN_BASE` describen un KPI. Los antiguos
      > `alto`/`medio`/`bueno` significaban las dos cosas a la vez, y por ahí se
      > coló que el pie del Inicio pintara en rojo un KPI que Indicadores daba
      > por ámbar.

      *Por qué está aquí:* en los prototipos publicados el pie del Inicio
      llevaba `estadoRitmo` y `metaEsperadaAHoy` **escritos a mano**, y tres de
      las ocho tarjetas contradecían a Indicadores —además de un
      `metaEsperadaAHoy` mal calculado—. `docs/ai/prototipos/` ya demuestra la
      forma correcta: una función, dos pantallas, y una prueba que falla si
      alguna vuelve a calcular por su cuenta.

- [ ] **La evaluación de solicitud es un paso del embudo, no un empate.**
      `solicitudesIngresadas` y `solicitudesAprobadas` son dos contadores
      distintos: la pérdida entre ambos es el trabajo del broker. El embudo de
      demanda tiene **cuatro** saltos (`Oportunidad → Visita → Solicitud →
      Aprobada → Contrato`), no tres.
- [ ] **El pulso del equipo es un `GROUP BY estadoRitmo`**, no una fila de
      texto: `totalAgentes = enRitmo + atencion + fueraDeRitmo + sinBase`, y
      toda excepción visible pertenece a un grupo distinto de `EN_RITMO`.
- [ ] **La concentración de cartera se deriva de la misma distribución** que
      alimenta *Cartera · Agente*: el agente que se nombra es, por
      construcción, el primero de esa distribución.
- [ ] **Definir «Puede cerrarse este mes»**, que sigue sin definición en D-E2-2.
- [ ] **`generadoEn`** en la respuesta, para que la cabecera diga «hace 2 min».
- [ ] Pruebas: que ningún texto de contraste mencione el sector; que el pie use
      los cuatro nombres canónicos completos; que para un mismo actor, KPI y
      periodo el Inicio e Indicadores reciban **exactamente** los mismos seis
      campos; que `visitas ≥ solicitudes ≥ aprobadas ≥ contratos`.

---

## 5. Tiempo real: no hace falta lo que parece que hace falta

No hay WebSocket, ni SSE, ni `@Scheduled` de negocio. El único `@Scheduled` es
un barrido de concesiones de recuperación al arranque.

**Y está bien así**, porque el motor de la bandeja **reconcilia en cada
lectura**:

- un `GET /inicio` al montar la pantalla devuelve el estado de este segundo;
- un refetch al volver a la pestaña (`visibilitychange`) cubre el 95 % de lo que
  en una herramienta de trabajo se llama «tiempo real»;
- un `polling` de 60–90 s en la pestaña activa cubre el resto.

`AlertasController` ya documenta ese mismo patrón —«planificador de pobre»—: la
lectura hace el trabajo.

> **Lo que sí conviene:** `generadoEn` en la respuesta, y que la cabecera del
> Radar lo use. Un socket para una bandeja que se revisa cada varios minutos es
> coste sin beneficio, y obliga a resolver reconexión, multi-pestaña y
> autorización por socket.

Reconsiderarlo cuando llegue la mensajería con el cliente (WhatsApp), que sí es
conversacional y sí pide empuje.

---

## 6. Lo que no se debe hacer

- **Calcular en Angular lo que falte.** Es la regla de E1 y sigue en pie: si el
  cable no trae una cifra, se añade al backend. En particular, **`estadoRitmo`
  no se recalcula ni se reinterpreta en el frontend**: llega decidido y solo se
  pinta.
- **Escribir un hecho dos veces.** Un dato que aparece en cinco componentes
  existe una vez en el modelo; si hay que teclearlo por segunda vez para que
  otra pantalla lo enseñe, falta una derivación.
- **Dejar el hallazgo dentro de la cola.** Es lo que se hace hoy y es justo lo
  que confunde.
- **Empezar por las metas.** El bloque más caro y el que menos desbloquea.
- **Saltarse la matriz operación→rol.** Un endpoint nuevo sin su fila rompe el
  build, y está bien que lo rompa.

---

## 7. Checklist de cierre

Una tanda no está cerrada hasta que estas tres cosas son ciertas:

- [ ] **Gate**: `verificacion/Verificar-Cierre.ps1` en verde — no `mvn clean
      install`, que se salta las 37 pruebas de integración en silencio.
- [ ] **Pruebas**: las del traspaso que apliquen a la tanda, portadas a
      Karma/Jasmine y a JUnit según el lado.
- [ ] **Evidencia**: en `backend-spring/verificacion/evidencia/`, y la fila de la
      subtanda en `mapa-ejecucion-brox.md` pasa a ✅.
