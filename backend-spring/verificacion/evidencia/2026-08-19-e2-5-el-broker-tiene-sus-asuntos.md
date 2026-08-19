# E2.5 — El Radar del broker (2026-08-19)

**Qué cierra:** el broker deja de entrar a BROX y ver sólo cuatro indicadores
agregados. Tiene sus asuntos —los que **sólo él** puede decidir— y su hallazgo.

**Lo que el mapa exigía primero:** revisar la regla «la bandeja no es un tablero
de control» en un `decision-*.md`, no saltársela. Está en
`docs/ai/decision-el-broker-tiene-sus-propios-asuntos.md` (D-E2-5).

---

## 1. La revisión, y por qué la regla se mantiene

La regla vive en `TareasController` y es la única excepción de acceso del
sistema:

> Es el único recurso del sistema sin acceso de ADMIN, y es coherente: la bandeja
> no es un tablero de control, es la lista de cosas que un agente tiene que
> hacer. Ni el broker ni el admin entran.

La tensión no era «¿el broker ve tareas, sí o no?». Eran dos preguntas con
respuestas distintas:

| Pregunta | Respuesta |
|---|---|
| ¿Puede el broker ver **la bandeja del agente**? | **No.** Nunca. Eso sí sería un tablero de control. |
| ¿Puede el broker tener **asuntos suyos**? | **Sí.** Y no tenerlos era el error. |

La regla original mezclaba las dos sin querer, porque cuando se escribió sólo
existía una bandeja. Hoy hay que decir **de qué** no entra.

> **La decisión:** la bandeja sigue sin ser un tablero de control, y precisamente
> por eso el broker tiene la suya. Cada rol ve lo que él tiene que decidir, nunca
> lo que otro tiene que hacer.

`GET /tareas` **no se tocó**. Lo que cambia es que deja de ser la única bandeja.

---

## 2. Lo que el broker no tenía, medido contra la matriz

Cuatro operaciones que **nadie más puede hacer**, y ninguna aparecía en el
Inicio:

| Sólo el broker | Endpoint |
|---|---|
| aprobar u observar una captación | `PATCH /captaciones/{id}/revisar` |
| **firmar una evaluación** — «la más sensible de las 18» | `POST /evaluaciones` |
| revisar y conformar documentos | `PATCH /solicitudes/{id}/documentos/…` |
| registrar el cobro de una comisión | `POST /contratos/{id}/comision/cobro` |

> **El hueco que E2.2 ya había rozado.** La tarea «comisión lista para cobro»
> vivía en la bandeja **del agente** con `dependeDeMi = false`, porque quien
> cobra es el broker. Ese asunto no tenía dueño: al agente no le servía y al
> broker no le llegaba. Aquí encuentra el suyo.

---

## 3. El mismo motor, sin excepciones

Los asuntos del broker pasan por la **misma** `PoliticaDeDespacho` de E2.2 y
producen la **misma** `Interpretacion` de E2.4. Devuelven `CandidatoTarea`, la
forma que ya usan los disparadores del agente, precisamente para que no haya un
segundo motor.

```
GET /dashboard   (broker rsalas, token real)

  bandeja del agente que ve el broker: 0     <- cerrada
  SUS asuntos: 109

  SOLICITUD_POR_EVALUAR  SOL-260715103000  esperando 35d   id=SOLICITUD_ALQUILER:1-b
      FALTA  Falta tu evaluacion de la solicitud
      DATO   Esperando desde hace 35 dias
      FRENO  El interesado espera respuesta y el contrato no puede firmarse

  CAPTACION_POR_REVISAR  CAP-0004          esperando 18d   id=CAPTACION:4-b
      FALTA  Falta tu decision sobre la captacion
      DATO   Esperando desde hace 18 dias
      FRENO  El local no se puede ofrecer hasta que la apruebes
```

Y `GET /tareas` como broker sigue respondiendo **403**.

---

## 4. Su hallazgo no es de cartera: es de concentración

```
CONCENTRACION_DEL_EQUIPO
Dónde está concentrada la cartera
Casi toda la cartera está en manos de Valentina Mora; el resto del equipo
apenas mueve 0.
```

**Vale porque la media lo esconde.** Y hereda la regla de E2.4 sin excepción:
«Valentina 107» es un dato; que toda la cartera esté en una persona **mientras
el resto no mueve nada** es una conclusión.

### El caso que faltaba, y lo destapó el dato real

Implementé primero el ejemplo documentado en D-E2-2 §9.1 —un **rezagado**, Luis
12 contra una mediana de 47— y contra los datos reales no disparaba nunca. El
equipo era **107 · 0 · 0 · 0**: la concentración estaba por el **otro extremo**,
uno que acapara, y mirar sólo al último comparaba 0 contra una mediana de 0.

«Concentración de cartera de un agente» —la frase de la checklist— es
exactamente ese caso. Ahora se detectan los dos, con su umbral cada uno: la
mitad de la mediana para el rezagado, el triple para el que acapara.

> **Sin concentración no hay hallazgo.** El bloque no existe; no se rellena con
> «el equipo está equilibrado», igual que una lectura de relleno enseña a no
> leerla.

---

## 5. Los gates

| Gate | Qué impide |
|---|---|
| `laBandejaDelAgenteSigueCerradaAlBroker` | que `GET /tareas` se abra al broker o al admin |
| `elAgenteNoRecibeLosAsuntosDelBroker` | el cruce por el otro lado |
| `elAdminNoTieneAsuntos` | dar asuntos a quien no puede resolverlos — la definición de un tablero |
| **`losDosFocosNoCompartenIds`** | que el mismo encargo salga dos veces (D-E2-1 §7.1) |
| `cadaAsuntoLlegaInterpretado` | que el broker reciba datos crudos donde el agente recibe lectura |
| `elOrdenEsDeterminista` | dos lecturas seguidas, el mismo orden |

El de los ids es el que más costó aprender: **Av. Arequipa puede estar en las dos
colas y son dos asuntos distintos** —uno dice «recontacta», el otro «aprueba»—.
Con un id compartido, la regla del hogar único los trata como el mismo. Por eso
el id del broker lleva su sufijo `-b`.

---

## 6. Verificación

```
backend  903 pruebas · 0 fallos · 0 SKIPPED
           674 servicio + 43 web/arquitectura + 186 app (40 de integracion)
Angular  565 / 565
```

`GateDeCierreTest` volvió a hacer su trabajo: exigió inventariar
`FocoDelBrokerIntegrationTest` y comprobar que `Verificar-Cierre.ps1` sigue
exigiendo que **todos** se ejecuten.

### Un fallo mío que la comprobación visual encontró

El hallazgo de concentración estaba escrito, probado y **no salía**. En
`DashboardController`, la rama del no-agente devolvía `List.of()` fijo para
`hallazgos`: el broker nunca los pedía.

Los tests no lo veían porque probaban el servicio, no el controlador. Lo
encontró mirar la pantalla — que es exactamente para lo que sirve la
comprobación visual, y por qué no se sustituye con tests verdes.

---

## 7. Un «hueco del seed» que no existía

> **Corregido el 2026-08-19.** Esta sección afirmaba que los brokers de
> `detalle_broker` no tenían credenciales. **Era falso**, y el error era mío al
> leer la tabla: busqué `credencial_usuario` por el `id_persona_rol` del broker y
> no encontré ninguna fila, sin caer en que el modelo es Party-Role.

La credencial cuelga del rol `USUARIO_INTERNO` de **la misma persona**, no del
rol de negocio:

```
persona 2 ──┬── USUARIO_INTERNO #2   ← aquí vive la credencial (rsalas)
            └── BROKER          #23  ← aquí vive el alcance (4 agentes)
```

Los cinco brokers entran y resuelven a su rol correcto —23, 24, 25, 26, 27—, así
que `rsalas → idDominio 23` no era una casualidad: era la resolución haciendo su
trabajo. **No había nada que arreglar en el seed ni en `migration-dev`.**

Lo que sí faltaba era la **afirmación**, y ahora existe:
`IdentidadDelBrokerIntegrationTest` comprueba que todo broker con equipo tiene
identidad con la que entrar, que esa identidad resuelve a un único rol BROKER que
es el suyo, y que hay al menos uno con el que mirar la pantalla. Sin eso,
verificar cualquier superficie de broker dependía de dar con el usuario correcto
por azar — y esta sección es la prueba de que eso pasa.

---

## 8. Cierre de E2.5 — lo descubierto no quedó como deuda

Nada de lo que apareció durante E2.0–E2.5 se dejó anotado. Esta sección es el
cierre, no una tanda nueva.

### El cuarto disparador, y la duplicación que evitó

`documentos por conformar` faltaba. Al construirlo apareció el problema de
verdad: **la solicitud 1 estaba EN_REVISION —ya competía como «por evaluar»— y
tenía 3 de 5 documentos pendientes.** Un cuarto disparador ingenuo la habría
puesto **dos veces** en el foco, que es la duplicación que D-E2-1 §11 prohíbe.

La decisión: **una solicitud produce un solo asunto, y sus documentos deciden
cuál.** Conformar va antes de evaluar, así que mientras queden pendientes es
`DOCUMENTOS_POR_CONFORMAR`, y cuando estén todos conformes pasa a
`SOLICITUD_POR_EVALUAR`. Modela la secuencia real y respeta el hogar único.

Y trae el **primer contador real del `avance`** de E2.4, que hasta ahora viajaba
en `null` por no haber ningún requisito contable de verdad:

```
DOCUMENTOS_POR_CONFORMAR  SOL-260715103000   id=SOLICITUD_ALQUILER:1-b
AVANCE: 2 de 5 documentos conformados
   FALTA  Faltan documentos por conformar
   DATO   Esperando desde hace 35 dias
   FRENO  Sin conformidad no se puede evaluar la solicitud
```

Una consulta con `group by` para toda la página, no una por solicitud.

### Las credenciales del broker: no era deuda, era un diagnóstico mío equivocado

Afirmé que los brokers de `detalle_broker` «no tienen credenciales». **Es falso.**
El seed sigue Party-Role y la credencial cuelga del rol `USUARIO_INTERNO` de la
misma persona:

```
persona 2 ──┬── USUARIO_INTERNO #2   ← la credencial (rsalas)
            └── BROKER          #23  ← el alcance (4 agentes)
```

Los cinco brokers entran y resuelven a su rol: 23, 24, 25, 26, 27. No hacía falta
tocar el seed ni `migration-dev`.

Lo que sí faltaba era la **afirmación**: `IdentidadDelBrokerIntegrationTest`
comprueba que todo broker con equipo tiene identidad con la que entrar, que
resuelve a un único rol BROKER que es el suyo, y que existe al menos uno con el
que mirar la pantalla. Sin eso, cada comprobación visual de una superficie de
broker dependía de dar con el usuario correcto por azar.

### La auditoría de cierre

| Buscado | Resultado |
|---|---|
| `TODO` / `FIXME` / `placeholder` / `provisional` | **0** (los aciertos eran «todo/toda» en prosa) |
| colecciones vacías fijas en ramas de controlador | **2 encontradas, 2 corregidas** |
| mocks o datos cocinados en el Inicio | 0 |
| prioridad u orden reimplementados fuera de las políticas | 0 |
| interpretación en Angular | 0 (sólo estado → símbolo, que es la traducción permitida) |
| N+1 | **1 encontrado, 1 corregido** |
| disparadores declarados sin productor | **1 encontrado** (`documentos por conformar`), construido |
| asuntos que existen y nadie puede resolver | 0 |
| asuntos resolubles que no llegan a su actor | **1** (documentos), cerrado |

**Los dos `List.of()` fijos.** Uno era el que dejó al broker sin hallazgo. El
otro, su espejo en la rama del agente. Ninguno fallaba, ninguno lo veía un test
de servicio, y los dos mentían: ahora contesta el servicio, que es lo que hace
imposible esa clase de rama muerta.

**El N+1** estaba en el hallazgo de concentración: los nombres del equipo se
pedían de uno en uno dentro del bucle. Cuatro consultas donde va una — pequeño,
pero la auditoría existe justo para que un N+1 pequeño no se quede porque es
pequeño.

### El contrato, recorrido criterio por criterio

Al comparar contra la tanda 4 de `estado-backend-para-el-inicio.md` apareció un
**PENDIENTE real que me había saltado**: `ambito` y los cuatro accesos rápidos
por rol.

Al implementarlo apareció otra cosa: **`ambito` ya existía** en
`IndicadoresResponse`. Publicar el mío habría creado un segundo productor del
mismo hecho —la doble verdad que D-E4-3 cerró para los datos de la propiedad—,
así que se alineó el que había con el diseño (el broker decía «Reportes de
equipo», que se lee como el título de un informe y no como un alcance; ahora dice
«Mi equipo») y no se añadió ninguno.

### Los dos roles, a ojo

```
              rsalas (broker)                    vmora (agente)
ambito        Mi equipo                          Mi actividad
bandeja       0                                  19
foco broker   124                                0
hallazgos     1  (concentracion)                 22 (cartera)
accesos       Revisar captaciones                Nueva prospeccion
              Evaluar solicitudes                Nueva captacion
              Seguimiento del equipo             Programar visita
              Reasignar cartera                  Reporte al propietario
```

Cada rol ve lo que él tiene que decidir, y nada de lo que otro tiene que hacer.
