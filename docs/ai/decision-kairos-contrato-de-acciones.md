# D-K-1 · KAIROS: contrato de acciones

**Qué decide:** qué puede hacer KAIROS, por dónde lo hace, con qué permisos,
qué se registra y qué necesita confirmación humana.

**Estado:** propuesta congelable. **Se puede escribir hoy**, antes de que exista
una línea de KAIROS, porque no describe un modelo de lenguaje: describe un
**contrato de ejecución** contra el dominio que ya existe.

**Depende de:** `decision-motor-de-registro.md` (D-E4-2) para la captura y
`decision-modelo-universal-propiedad-operacion.md` (D-E4-1) para el dominio.

---

## 1. La decisión

> **KAIROS no es un chatbot encima de BROX. Es un operador que ejecuta los
> mismos casos de uso, con los mismos permisos y dejando el mismo rastro.**

De ahí salen cuatro reglas que no se negocian:

| # | Regla | Qué impide |
|---|---|---|
| 1 | **Una herramienta de KAIROS es un caso de uso que ya existe.** Nunca un camino propio a la base de datos | que el negocio acabe escrito dos veces |
| 2 | **KAIROS actúa COMO la persona**, con su sesión y sus permisos. Nunca con permisos propios ni elevados | que la conversación se convierta en un agujero de autorización |
| 3 | **Todo lo que hace queda con su origen.** `origen = KAIROS` y el turno que lo pidió | que no se pueda auditar quién decidió qué |
| 4 | **Lo irreversible se confirma.** Y la confirmación la da una persona, en la pantalla | que un malentendido publique, envíe o firme |

---

## 2. Las tres capas

```
   intención           lo que la persona quiere
       ↓
   herramienta         lo que KAIROS puede invocar   ← este documento
       ↓
   caso de uso         lo que el dominio ya sabe hacer
       ↓
   endpoint + matriz   quién puede llamarlo
```

**KAIROS solo añade la primera capa.** Las otras tres ya existen: 166
operaciones REST, su matriz de roles y sus servicios.

---

## 3. Catálogo de herramientas

Cada herramienta declara: **qué caso de uso invoca**, **qué permiso exige** y
**si necesita confirmación**.

### 3.1 Lectura — nunca necesitan confirmación

| Herramienta | Caso de uso | Permiso |
|---|---|---|
| `buscar_propiedades` | búsqueda de cartera con filtros y cercanía | `GET /propiedades` |
| `buscar_coincidencias` | `CoincidenciaCartera` sobre un requerimiento | alcance del requerimiento |
| `resumir_oportunidad` | expediente de la oportunidad | alcance de la oportunidad |
| `explicar_match` | criterios cumplidos e incumplidos | igual que la coincidencia |
| `estado_de_propiedad` | encargos vivos, publicación, actividad | alcance de la propiedad |
| `que_resolver_hoy` | el foco del Inicio, con su política de despacho | el del actor |
| `detectar_faltantes` | qué le falta a un expediente o a una ficha | alcance del objeto |

> **`explicar_match` es la que hace a KAIROS útil de verdad.** Con criterios
> `INDISPENSABLE` / `DESEABLE` (D-E4-1 §3.4) la respuesta es *«cumple 4 de 5;
> falta confirmar la potencia eléctrica»*, no un porcentaje sin causa.

### 3.2 Escritura reversible — se ejecutan y se avisan

| Herramienta | Caso de uso | Por qué no pide confirmación |
|---|---|---|
| `registrar_interaccion` | `POST /interacciones` | es una nota; se corrige editándola |
| `abrir_sesion_captura` | `POST /captura/sesiones` | **no escribe nada** hasta confirmar |
| `responder_captura` | `POST …/respuestas` | tampoco escribe |
| `programar_visita` | `POST /visitas` | se reprograma o cancela |
| `crear_requerimiento` | `POST /requerimientos` | se edita |
| `anotar_seguimiento` | tarea/recordatorio | se cancela |

### 3.3 Escritura sensible — **siempre** confirmación humana

| Herramienta | Caso de uso | Por qué |
|---|---|---|
| `confirmar_captura` | `POST …/confirmar` | crea propiedad, titularidad y encargos |
| `publicar_encargo` | `POST /publicaciones` | **sale al exterior** |
| `presentar_oferta` | hito `O` del histórico | compromete un número frente al cliente |
| `decidir_captacion` | `POST /captaciones/{id}/decision` | es una firma del broker |
| `evaluar_solicitud` | `POST /evaluaciones` | es una firma del broker |
| `cerrar_encargo` | `POST /captaciones/{id}/cierre` | termina una relación comercial |
| `registrar_cobro` | movimiento de comisión | es dinero |
| `enviar_reporte` | reporte al propietario | **sale al exterior** |

**El criterio, dicho una vez:** si la acción *sale de la organización*, *firma
algo*, *mueve dinero* o *no se puede deshacer con otra acción del sistema*, la
confirma una persona.

### 3.4 Lo que KAIROS no puede hacer nunca

- Cambiar permisos, roles o asignaciones.
- Tocar credenciales, MFA o sesiones.
- Borrar nada.
- **Escribir en el histórico económico saltándose el hito**: el histórico es
  append-only y la razón por la que existe E0.
- Actuar fuera del alcance del actor. Si la persona no ve una propiedad, KAIROS
  tampoco.

---

## 4. Permisos: KAIROS no tiene los suyos

```
Persona ──sesión──> KAIROS ──misma sesión──> caso de uso ──> matriz operación→rol
```

- **Un turno de KAIROS viaja con el token de la persona.** Sin token de
  servicio, sin cuenta de sistema, sin `sudo`.
- **La matriz sigue siendo la fuente de verdad**, y su test sigue rompiendo el
  build. Una herramienta nueva que llame a un endpoint nuevo necesita su fila,
  igual que una pantalla.
- **Sesión capada = KAIROS capado.** Con contraseña temporal o MFA pendiente,
  el backend ya bloquea; KAIROS recibe el mismo 403 y lo dice.
- **La política de capacidades del frontend (auditoría §4.4) la comparte
  KAIROS**: si el botón no se ofrece, la herramienta tampoco.

---

## 5. Trazabilidad

Cada acción escribe, **en la misma transacción que el hecho**:

```
actor            quién (persona_rol), nunca «el sistema»
origen           UI | KAIROS | API
conversacionId   la conversación
turnoId          el turno exacto que lo pidió
peticion         lo que la persona escribió
herramienta      la que se invocó
confirmadoPor    persona y momento, si era sensible
```

Y va al **mismo sitio que ya usa el resto**: `historial_estado` para las
transiciones y `evento_dominio` (D-E4-1) para el outbox. **No se inventa una
bitácora de KAIROS aparte**: si la hubiera, auditar una operación exigiría
mirar en dos sitios y sabríamos que uno de los dos miente.

> **Consecuencia buscada:** poder preguntar *«¿quién decidió esto?»* y que la
> respuesta sea siempre una persona, con la frase que lo pidió al lado.

---

## 6. Qué se mide

La tesis del North Star es **ejecutar → capturar resultado → aprender**. Si
KAIROS no deja resultado medible, es una demo.

| Medida | Qué dice |
|---|---|
| Acciones ejecutadas por herramienta | qué se usa de verdad |
| Ratio de confirmación / rechazo | si KAIROS propone bien |
| Preguntas evitadas en la captura | cuánto ahorra frente a la pantalla |
| Tiempo hasta el alta completa | UI contra conversación |
| Asuntos del foco resueltos tras una recomendación suya | si mueve el negocio |
| Correcciones humanas posteriores | dónde se equivoca |

Todas salen de datos que el contrato ya obliga a guardar. Ninguna necesita
instrumentación aparte.

---

## 7. Por dónde empieza

**Tres herramientas, en este orden**, y cada una es útil sola:

1. **`abrir_sesion_captura` + `responder_captura` + `confirmar_captura`.**
   El alta conversacional. Es el caso con más ahorro medible y el más seguro:
   no escribe hasta el final. *Depende de D-E4-2.*
2. **`buscar_coincidencias` + `explicar_match`.** Solo lectura, y es lo que
   hace que BROX parezca inteligente. *Depende del matcher.*
3. **`que_resolver_hoy` + `registrar_interaccion`.** Cierra el ciclo: KAIROS
   propone qué atender y anota el resultado. *Depende de la Tanda 1 del backend
   (`DEPENDE_DE_MI` y la política de despacho).*

**No hace falta esperar a BROX 1.0.** Se puede diseñar hoy —esto es ese
diseño— y empezar a implementar en cuanto el motor de captura exista.

---

## 8. Criterios de aceptación

1. Ninguna herramienta accede a la base de datos por su cuenta: todas pasan por
   un caso de uso existente.
2. Ninguna herramienta usa permisos distintos de los de la persona.
3. Toda acción sensible tiene confirmación humana registrada, con persona y
   momento.
4. Toda escritura lleva `origen`, `conversacionId` y `turnoId`.
5. Un endpoint nuevo para una herramienta entra en la matriz operación→rol o el
   build falla.
6. La misma alta hecha por pantalla y por KAIROS produce **datos idénticos**, y
   hay una prueba que lo compara.
7. Con la sesión capada, KAIROS no ejecuta nada y lo explica.
