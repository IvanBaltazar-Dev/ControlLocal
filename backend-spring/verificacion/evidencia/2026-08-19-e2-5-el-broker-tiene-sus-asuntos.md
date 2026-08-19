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
backend  897 pruebas · 0 fallos · 0 SKIPPED
           674 servicio + 43 web/arquitectura + 180 app (38 de integracion)
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

## 7. Un hueco del seed, anotado

Los brokers con equipo (`detalle_broker` 22–27) **no tienen credenciales**, y los
usuarios que sí las tienen (`rsalas`…) son `USUARIO_INTERNO` en `persona_rol` con
rol de membresía BROKER. Para la comprobación visual hizo falta un broker real
con equipo; `rsalas` resultó tener `idDominio = 23` en su token, que **sí** es un
`detalle_broker` con cuatro agentes, así que la prueba salió sin tocar datos.

Se anota porque el siguiente que quiera probar una pantalla de broker se va a
encontrar con lo mismo, y porque el intento de «arreglarlo» insertando filas
choca contra dos FK.
