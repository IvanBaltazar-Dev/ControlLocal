# D-E2-5 · El broker tiene sus propios asuntos, no la bandeja del agente

**Qué decide:** si el broker puede tener un Radar con asuntos accionables sin
contradecir la regla que dice que *«la bandeja no es un tablero de control»*.

**Por qué existe:** el mapa de ejecución exige revisar esa regla **antes** de
picar E2.5, no saltársela. Está escrita en `TareasController` y es la única
excepción de acceso de todo el sistema:

> **Es el unico recurso del sistema sin acceso de ADMIN**, y es coherente: la
> bandeja no es un tablero de control, es la lista de cosas que un agente tiene
> que hacer. Ni el broker ni el admin entran.

Y el prototipo, aprobado, le da al broker un Radar propio (D-E2-1 §7.1).

**Estado:** decidida el 2026-08-19. Abre E2.5.

---

## 1. La tensión, dicha con precisión

No es «¿el broker ve tareas, sí o no?». Es más fina, y por eso conviene
separarla en dos preguntas que tienen respuestas distintas:

| Pregunta | Respuesta |
|---|---|
| ¿Puede el broker ver **la bandeja del agente**? | **No.** Nunca. Eso sí sería un tablero de control. |
| ¿Puede el broker tener **asuntos suyos**? | **Sí.** Y no tenerlos es el error actual. |

La regla original mezclaba las dos sin querer, porque cuando se escribió sólo
existía una bandeja: la del agente. Decir «el broker no entra» era correcto y
suficiente. Hoy hay que decir de qué no entra.

---

## 2. Por qué el broker sin asuntos es un error, no una virtud

Un broker **decide**. La matriz operación-rol lo dice sin ambigüedad, y son
operaciones que **nadie más puede hacer**:

| Lo que sólo el broker puede hacer | Endpoint |
|---|---|
| aprobar u observar una captación | `PATCH /captaciones/{id}/revisar` |
| **firmar una evaluación** — «la más sensible de las 18» | `POST /evaluaciones` |
| revisar y conformar documentos de una solicitud | `PATCH /solicitudes/{id}/documentos/…` |
| registrar el cobro de una comisión | `POST /contratos/{id}/comision/cobro` |

Eso no es supervisión: **es su trabajo**. Y hoy no aparece en ninguna parte del
Inicio. El broker abre BROX y ve cuatro indicadores agregados, mientras hay
solicitudes esperando su firma.

> Ya lo tocamos en E2.2 sin buscarlo: la tarea «comisión lista para cobro» vivía
> en la bandeja **del agente** con `dependeDeMi = false`, porque quien cobra es
> el broker. Ese asunto no tiene dueño: al agente no le sirve y al broker no le
> llega. Es exactamente el hueco que esta decisión cierra.

---

## 3. La decisión

> **La bandeja sigue sin ser un tablero de control. Y precisamente por eso el
> broker tiene la suya.**
>
> Cada rol ve **lo que él tiene que decidir**, nunca lo que otro tiene que hacer.

Tres consecuencias, y ninguna es opcional:

**a · `GET /tareas` no se toca.** Sigue siendo del agente, sigue sin acceso de
ADMIN, y el broker sigue sin entrar. La excepción documentada en
`TareasController` se mantiene **tal cual está escrita**: lo que cambia no es esa
regla, es que deja de ser la única bandeja del sistema.

**b · Los asuntos del broker son de otra naturaleza.** No son las tareas del
agente filtradas: son sus propias decisiones pendientes. Un agente no las ve, y
un broker no ve las del agente. Si algún día los dos conjuntos se solapan, es
señal de que alguien copió el disparador equivocado.

**c · Nunca comparten identidad.** D-E2-1 §7.1 lo aprendió a golpes: con un solo
id, «Av. Arequipa» salía dos veces en el broker —en su cola y como fecha suelta
en la agenda— porque la regla del hogar único no vale si el identificador no es
el del rol que mira. El foco del broker y el del agente **no pueden compartir
ids**, y hay un test que lo comprueba.

### Lo que sigue prohibido, para que quede escrito

- El broker **no ve** «Recontacta la prospección PRO-0002»: eso es del agente.
- El TENANT_ADMIN **no tiene asuntos**. Puede ver colas para auditar (la matriz
  ya se lo permite en `/captaciones/pendientes` y `/evaluaciones`) pero no decide
  ninguna operación comercial desde D-F4-5. Un Inicio con asuntos para quien no
  puede resolverlos es la definición de un tablero de control.

---

## 4. Qué compone el foco del broker

Cuatro disparadores, y los cuatro son operaciones que **sólo él** puede resolver:

| Disparador | De dónde sale | Por qué depende de él |
|---|---|---|
| **Captaciones por revisar** | las `P`/`O` de su equipo | aprobar u observar es suyo |
| **Solicitudes por evaluar** | las que esperan evaluación | firmar la evaluación es suyo |
| **Documentos por conformar** | solicitudes con documentos sin revisar | revisar y conformar es suyo |
| **Comisiones sin cobrar** | contratos con comisión asignada | registrar el cobro es suyo |

Los cuatro pasan por la **misma** `PoliticaDeDespacho` de E2.2 y por la **misma**
`InterpretacionDelAsunto` de E2.4. No hay un segundo motor de orden ni una
segunda capa de lectura: si el broker necesitara pesos distintos, se añadiría un
criterio a la política, no una política nueva.

---

## 5. Su hallazgo no es de cartera: es de concentración

El agente descubre que **dos locales vuelven a encajar** con un cliente. El
broker descubre otra cosa:

```
Equipo en Visita → Solicitud: 38 %      ← la media no dice nada
Valentina 52 · Andrea 47 · Carlos 41 · Luis 12
```

> El cuello está concentrado en un agente; el resto del equipo se mantiene
> estable.

**Vale precisamente porque la media lo esconde.** Un 38 % de equipo parece
razonable hasta que se abre.

Y hereda la regla de E2.4 sin excepción: **un hecho aislado no se disfraza de
interpretación**. «Luis 12 %» es un dato; que el cuello esté en una persona
mientras el resto se sostiene es una conclusión — relaciona la distribución con
la media, y por eso es un hallazgo.

> **Sin nombres en el pulso.** D-E2-2 §6.1 prohíbe el ranking: el pie del broker
> lleva la distribución, no la lista de quién va último. El hallazgo sí nombra al
> agente, porque una concentración sin sujeto no se puede resolver — pero es un
> hallazgo dirigido a quien puede actuar, no una tabla de posiciones para todos.

---

## 6. Lo que esta decisión NO abre

- **`contraste`, pie y metas** son E2.6.
- **Acciones desde el Radar** (`ARCHIVO`, `REGISTRO`, `FECHA`): `FECHA` necesita
  un endpoint de agenda que no existe. Fuera.
- **KAIROS** no entra por aquí.

---

## 7. Cómo se cierra

Cerrada el 2026-08-19. Ningún punto quedó como nota.

- [x] `foco[]` del broker con los **cuatro** disparadores, por la misma política
- [x] cada asunto con su interpretación de E2.4
- [x] el hallazgo de concentración, con su evidencia
- [x] **gate**: el foco del broker y el del agente no comparten ids
- [x] **gate**: `GET /tareas` sigue siendo del agente y sin ADMIN — responde 403
- [x] la matriz operación-rol: **no aplica**, no se añadió ningún endpoint (todo
      viaja por `/dashboard`, que ya está en la matriz) y su test sigue verde
- [x] prueba visual: el broker entra y ve **sus** asuntos, no los de nadie

### Añadido al cerrar, porque el contrato lo pedía y faltaba

- [x] **`documentos por conformar`**, el cuarto disparador, de punta a punta
- [x] **`ambito` y los cuatro accesos rápidos por rol** (D-E2-1 §6.1), que la
      tanda 4 de `estado-backend-para-el-inicio.md` exige y se me había pasado
- [x] un E2E que garantiza que **un broker con equipo puede entrar**, para que
      mirar su pantalla no dependa de dar con el usuario correcto por azar
