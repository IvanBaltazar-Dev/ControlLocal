# D-E4-2 · El motor de registro

**Qué decide:** cómo se da de alta cualquier cosa en BROX con **una sola**
máquina de preguntas, servida por el backend y consumida por dos caras: el
botón `+ Registrar` de la pantalla y KAIROS.

**Estado:** propuesta congelable, **corregida el 2026-08-21 (V75)**: la
`operación` dejó de ser un paso fijo del alta. Ver §2 y la nota que la explica.

Maqueta ejecutable:

```bash
node docs/ai/modelo/motor-captura.js
```

> La maqueta todavía codifica la regla vieja —corta el plan con
> `if (!tipo || !op)`— y por tanto **ya no demuestra lo que el motor hace**. Lo
> que gobierna es `GuionRegistroPropiedad` y su gate; la maqueta se queda como
> ilustración del mecanismo, no como prueba.

**Depende de:** `decision-modelo-universal-propiedad-operacion.md` (D-E4-1) —
sin catálogo de atributos gobernado, el motor no puede derivar nada.

---

## 1. La decisión

**No hay siete formularios. Hay un motor que sabe lo que ya conoce, lo que
necesita y cuál es la siguiente pregunta.**

Y la regla que lo sostiene:

> **El plan de preguntas se DERIVA del modelo, no se escribe.** Sale del
> catálogo de atributos: de a qué tipos aplica cada uno y de cuáles son
> obligatorios. Añadir *Almacén* no añade una pantalla — añade filas al
> catálogo.

La maqueta lo demuestra sin trampa:

| Tipo | Características que pregunta | Obligatorias |
|---|---|---|
| Local comercial | 13 | 1 |
| Oficina | 9 | 1 |
| Departamento | 10 | 2 |
| Casa | 11 | 2 |
| Terreno | **7** | 2 |
| Almacén | 12 | 1 |
| Otro | **3** | 1 |

> `dormitorios` solo se pregunta en **departamento y casa**.
> `carga_electrica_kw` solo en **local, oficina y almacén**.
> Nadie escribió esas dos reglas: están en el catálogo.

---

## 2. La experiencia

```
+ Registrar
   ¿Qué quieres registrar?
   Propiedad · Propietario · Cliente · Actividad

→ Propiedad
   ¿Qué tipo de propiedad es?
   Departamento · Casa · Local · Oficina · Terreno · Almacén · Otro

   ¿Qué quieres hacer con la propiedad?
   Alquilar · Vender · Las dos cosas · Todavía nada: solo registrarla

   ¿De quién es?
   [busca entre los propietarios registrados antes de pedir datos nuevos]

   … y a partir de ahí, solo lo que aplica.
```

**Un paso fijo, y dos que ordenan si se responden.** `tipo` es el único fijo:
sin él el motor no sabe qué preguntar. La `operación` **ordena pero no
bloquea**: decide si el importe que viene detrás es un precio de venta o una
renta mensual, y por eso se pregunta antes que él — pero puede quedarse sin
responder. El `titular` **se pregunta siempre y tampoco bloquea** desde V76:
sin él no se puede encargar nada, pero sí se puede conocer el inmueble.

> **Corregido el 2026-08-21 (V76).** Esta sección decía «`tipo` y `titular` son
> fijos … sin titular no hay de quién es». Es verdad para un encargo y falso
> para un registro: BROX puede conocer legítimamente un departamento anunciado
> en un portal sin saber quién es el dueño, y exigir el titular en el alta
> obliga a **inventarlo** —un «Propietario por confirmar»— que es una persona
> falsa dentro de la cartera. La exigencia no desaparece: se muda del alta al
> encargo, y vive en un solo sitio (`TitularParaEncargar`) por el que pasan los
> tres caminos que abren una captación. Ver D-E4-1 §3.2 bis.

> **Corregido el 2026-08-21 (V75).** Esta sección decía «tres pasos fijos» y
> pintaba tres respuestas, todas comerciales. Describía un alta **comercial** y
> no contemplaba el estado previo: una propiedad que solo se está prospectando.
> El embudo de BROX es `propietario → prospección → encargo → publicación`, así
> que si la prospección existe para conseguir el encargo, el encargo no puede
> tener que existir antes de prospectar.
>
> La semántica queda así, y no necesita tres endpoints —es del caso de uso, no
> del cable—:
>
> ```
> REGISTRAR_PROPIEDAD   →  0, 1 o 2 operaciones
> prospectar            →  0 operaciones: Propiedad + Prospección
> captar                →  operación EXPLÍCITA: nace el Encargo
> ```
>
> Y una distinción que no se deduce sola: **propiedad sin encargo ≠
> prospección**. Son dos cosas que apuntan a la propiedad, no un estado suyo.
> Una propiedad puede registrarse solo como dato maestro, tener una prospección
> encima, o llevar varios episodios comerciales históricos, sin que haya que
> inventar estados para distinguirlos.

### 2.1 Buscar antes de pedir

`¿De quién es?` es una **búsqueda**, no un formulario. Si la señora Torres ya
está registrada, se elige; si no, se crea ahí mismo sin salir de la sesión. Es
lo que evita el duplicado de personas, que es el error más caro de un CRM
inmobiliario.

### 2.2 «Las dos cosas» no es un caso especial

Con la operación en el encargo (D-E4-1), elegir *Las dos cosas* simplemente
**repite el bloque económico**:

```
7. ¿En cuánto se vende?          → USD 540,000
8. ¿Cuánto es la renta mensual?  → USD 6,800
```

y al confirmar crea **2 Encargo · 2 CondicionEconomica** sobre una sola
propiedad. Cero ramas nuevas en el motor.

---

## 3. El contrato

```
POST /captura/sesiones
     { intencion, contexto? }              → { sesionId, siguiente }

GET  /captura/sesiones/{id}/siguiente      → Paso | null

POST /captura/sesiones/{id}/respuestas
     { paso, valor }                       → { siguiente, falta[] }

GET  /captura/sesiones/{id}/resumen        → { resumen, falta[], listoParaConfirmar }

POST /captura/sesiones/{id}/confirmar
     { idempotencyKey }                    → { creado, ids }
```

Un **Paso** lleva todo lo que la pantalla necesita para pintarlo y KAIROS para
entenderlo — y nada más:

```json
{
  "id": "attr:dormitorios",
  "pregunta": "¿Dormitorios?",
  "tipo": "NUMERO",
  "obligatorio": true,
  "unidad": null,
  "porQue": "Sin esto la ficha no se puede publicar."
}
```

### 3.1 Las cuatro reglas del contrato

1. **Nada se escribe hasta `confirmar`.** No hay borradores a medias ni
   entidades huérfanas. Es lo que hace seguro que KAIROS conduzca: si abandona
   la conversación, no queda basura.
2. **`contexto` es lo que el motor ya sabe.** Se abre desde la ficha de un
   propietario → no pregunta de quién es. KAIROS extrae cinco datos de una
   frase → entran como contexto y **no se vuelven a preguntar**. La maqueta lo
   enseña: la misma alta pasa de 7 preguntas a 2.
3. **`falta[]` siempre está disponible.** Es lo que permite a KAIROS decir *«me
   falta el metraje»* en lugar de fallar al guardar.
4. **`confirmar` es idempotente.** Con `idempotencyKey`, como ya hacen los
   movimientos de comisión (V41). Un reintento no duplica una propiedad.

### 3.2 Dónde vive

**En el backend**, en `service/captura`. Angular presenta; KAIROS conversa;
**ninguno de los dos decide qué se pregunta**. Es la misma regla de E1 que ya
sacó los umbrales de Angular, aplicada al alta.

Un motor de captura en el frontend significaría escribirlo dos veces —una para
la pantalla y otra para KAIROS— y que las dos se separaran en la tercera
semana.

---

## 4. Por qué esto es lo que desbloquea KAIROS

```
Angular:  Vender → Departamento → Miraflores → US$ 180 000 …
KAIROS:   «Registra un departamento en Miraflores.
           La señora Torres lo quiere vender en US$ 180 mil.»
```

**Las dos llaman al mismo caso de uso.** KAIROS no tiene un camino propio a la
base de datos: extrae respuestas de la frase, las mete como contexto de una
sesión de captura y pregunta lo que falte. La maqueta ya ejecuta ese guion.

Eso es lo que convierte a KAIROS en un operador y no en un chatbot encima de
BROX: **el resultado es el mismo dato, con la misma validación y la misma
trazabilidad**.

---

## 5. `+ Registrar` en la navegación

**El sidebar sirve para entrar al trabajo; `+ Registrar` sirve para crear.** Son
dos cosas distintas y por eso no se mezclan:

- **`+ Registrar` va arriba del todo, siempre visible**, encima de *Inicio*.
- **No** se añaden entradas *Nueva propiedad*, *Nuevo cliente*, *Nueva visita*…
  Eso devuelve el menú a 25 enlaces, que es justo lo que la auditoría vino a
  quitar.
- **Cada módulo conserva su botón contextual**: Propiedades → *+ Registrar
  propiedad*; Clientes → *+ Registrar cliente*; Visitas → *+ Agendar visita*.
  Es el mismo motor con el contexto ya puesto.

Entrar por intención global o por contexto: los dos caminos, un solo motor.

---

## 6. Qué NO hace el motor

| Fuera | Por qué |
|---|---|
| **Decidir permisos** | los decide la política de capacidades; el motor no ofrece una intención que la sesión no pueda ejecutar |
| **Inventar atributos** | solo pregunta lo que está en el catálogo. Una clave libre rompería el matcher |
| **Adivinar** | si falta algo obligatorio, lo dice. No completa por defecto |
| **Editar** | esto es alta. La edición es del expediente, que tiene otra forma y otros permisos |

---

## 7. Criterios de aceptación

1. Añadir un tipo de propiedad **no toca el motor**: solo el catálogo.
2. Ninguna pregunta se escribe dos veces para dos tipos distintos.
3. Un alta con contexto completo **no pregunta nada** y va directa al resumen.
4. `falta[]` nombra exactamente lo que impide confirmar.
5. Nada se persiste antes de `confirmar`, y `confirmar` es idempotente.
6. La misma sesión acepta respuestas por clic y por lenguaje natural **sin
   ramas distintas**.
7. `+ Registrar` no añade ninguna entrada al menú lateral.
