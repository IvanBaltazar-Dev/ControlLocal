# D-E2-2 · Indicadores comerciales: los cuatro KPI, el ritmo y la lectura por rol

**Qué responde:** qué se mide en la pantalla de indicadores, con qué definiciones
y qué cambia entre agente y broker.
**Estado:** decidido el 2026-08-11, **implementado en E2.6 el 2026-08-19**. Las
siete cuestiones que quedaban abiertas en §13 se cerraron ese día; la que sigue
abierta está marcada como tal.
**Relacionado:** `decision-inicio-foco-y-resolucion.md` (D-E2-1) para el Inicio;
`inventario-umbrales-de-dominio.md` para dónde viven las reglas.

---

## 1. Los cuatro KPI canónicos

> **Los nombres cambiaron el 2026-08-19, y esta tabla es la que se corrigió.**
> Este documento decía «Prospección efectiva / Captaciones activadas /
> Solicitudes generadas» y D-E2-1 §6.2 decía otra cosa, con una comprobación que
> exigía los cuatro nombres «letra por letra». El gate no se podía escribir:
> pedía dos verdades. Gana el **hecho de negocio** sobre el término abstracto —el
> tablero se lee en segundos o no es un centro de decisión— y «Locales» pasa a
> **«Propiedades»**, porque BROX dejó de ser un sistema de alquiler de locales el
> 2026-08-17.

| KPI | Código | Qué evento cuenta | Agente | Broker |
|---|---|---|---|---|
| **Propietarios contactados** | `C` | `prospeccion.fecha_contacto` dentro del mes | los suyos | suma del equipo |
| **Propiedades captadas** | `P` | la transición de la captación **a ACTIVA** | las suyas | suma del equipo |
| **Solicitudes ingresadas** | `S` | `solicitud_alquiler.fecha_registro` | las suyas | suma del equipo |
| **Contratos firmados** | `F` | `contrato_alquiler.fecha_cierre` | los suyos | suma del equipo |

**El código es estable; el rótulo no.** `C`/`P`/`S`/`F` es lo que persiste y lo
que viaja; el nombre visible puede cambiar el día que el negocio lo diga sin
migrar una fila.

**Y cada nombre cuenta un evento concreto, no algo parecido.** Dos de los cuatro
se midieron mal en la primera aproximación y la diferencia no era menor:

- *Propietarios contactados* sale de la **fecha de contacto**, no de la escalera
  de estados: `D` (descartado) se sale de la escalera y **sí** hubo contacto —los
  tres descartados de la base tienen su fecha—, así que contar por estado los
  perdería y premiaría dejar la prospección a medias.
- *Propiedades captadas* sale de la **transición a ACTIVA**, no del estado actual
  ni de `fecha_captacion`. Medido el 2026-08-19: por estado salen **5** y por
  evento salen **9**, porque cuatro ya cerraron en contrato. Y `fecha_captacion`
  es cuando se registró, no cuando el broker aprobó.

**Las definiciones son idénticas para los dos roles; lo único que cambia es el
alcance.** Es lo que permite que un broker abra un agente y entienda de dónde
sale el total del equipo.

Encaja con los dos embudos del producto: **Oferta** (propietario → prospección →
captación/encargo → publicación → actividad) y **Demanda/Cierre** (requerimiento
→ match → consulta → oportunidad → visita → oferta → solicitud → evaluación →
contrato → comisión).

### 1.1 «Prospecciones nuevas» no es el KPI principal

Crear 31 registros no es haber trabajado 31 prospectos. El número principal es
**contactadas**; las nuevas van debajo, como contexto:

```
PROSPECCIÓN EFECTIVA
22 contactadas          Meta 24
31 nuevas detectadas · 71 % de las nuevas llegaron a contacto
```

Se premia actividad con avance, no acumulación de registros. Si comercialmente se
decide que «nuevas» también tenga meta, puede seguir visible, pero **no como
número principal**.

---

## 2. Qué trae cada KPI

Siete campos, todos calculados en el dominio:

| Campo | Qué es |
|---|---|
| `actual` | lo conseguido en el periodo |
| `metaPeriodo` | la meta vigente del periodo |
| `metaEsperadaAHoy` | cuánto debería llevar a día de hoy |
| `porcentajeMeta` | avance sobre la meta |
| `faltante` | cuánto falta |
| `variacionComparable` | frente al periodo equivalente anterior |
| `estadoRitmo` | el semáforo (§4) |

**El frontend solamente dibuja.** Angular no calcula prorrateos, ni ritmos
esperados, ni decide colores — misma regla que E1 dejó congelada para los
umbrales.

---

## 3. El círculo de rendimiento

Cada círculo responde cinco cosas: **actual, meta, avance, faltante y estado**.

```
        Captaciones
          13 / 15          ← centro
            87 %           ← debajo
        faltan 2           ← fuera del círculo
```

El círculo contiene: arco recorrido, tramo pendiente, **manija de posición
actual**, marca fina de la meta y **una segunda marca, muy discreta, del ritmo
esperado a hoy**. Esa última es la que hace que el semáforo se entienda.

---

## 4. El semáforo mide ritmo, no porcentaje consumado

La pregunta no es *¿ya llegué a la meta?* sino **¿con el ritmo actual voy camino
de alcanzarla?**

| Estado | Cuándo |
|---|---|
| 🟢 **En ritmo** | va acorde o por encima del ritmo necesario |
| 🟠 **Atención** | hay desviación, todavía razonablemente recuperable |
| 🔴 **Fuera de ritmo** | la brecha ya necesita intervención |
| ⚪ **Sin base suficiente** | no hay muestra para concluir |

Ejemplo: meta mensual de 15 captaciones, día 5 de 30, lleva 5. Faltan 10, pero va
**muy por encima** del ritmo necesario → 🟢 **En ritmo**, no 🟠 «cerca».

Para el equipo, igual: meta 56, esperadas a hoy 28, tienen 31 → 🟢, y debajo
*«31 actuales · 28 esperadas a hoy»*, que informa muchísimo más que «55 % de
meta».

### 4.1 El cuarto estado no es decorativo

`1 visita → 1 solicitud = 100 %` no merece un 🟢 enorme. La tarjeta dice:

```
100 %  ·  N=1
⚪ Aún sin base suficiente
```

**Todo porcentaje muestra su N.** Con muestra insuficiente el estado es neutral.

---

## 5. Metas individuales y meta de equipo

> **Meta del equipo = suma de las metas vigentes de los agentes activos durante
> el periodo.**

Así el broker abre `Equipo 56` y encuentra `Valentina 8 · Carlos 8 · Andrea 7 ·
Luis 6 …` y todo reconcilia.

Si un agente entra a mitad de periodo, tiene licencia o cambia de equipo, **su
meta se prorratea según la política comercial**, en el dominio.

---

## 6. El broker: mismos KPI, otra lectura

La cabecera no dice «tu gestión» sino:

```
Tu equipo · 8 agentes · 1 mes                      [Todos los agentes ▾]
```

**No se le atribuye al broker el resultado personal del equipo.** Él supervisa el
resultado; su propia gestión se mide aparte (§8).

### 6.1 Pulso del equipo

Un total verde puede esconder un equipo roto: meta 20, resultado 21, pero 2
agentes hicieron 17, 3 hicieron 4 y 3 hicieron 0. Por eso, **inmediatamente
debajo de los cuatro KPI**, una franja de una sola altura:

```
PULSO DEL EQUIPO
🟢 6 en ritmo · 🟠 1 requiere atención · 🔴 1 fuera de ritmo        Ver agentes →
```

Resultado total y distribución del resultado son dos cosas distintas.

### 6.2 Nunca un ranking

Un agente puede tener cartera recién asignada y otro seis meses de expedientes
maduros. **No** «1.º Valentina 94 · 8.º Luis 42». Sí:

| Agente | Ritmo | Principal brecha |
|---|---|---|
| Valentina | 🟢 En ritmo | — |
| Diego | 🟠 Atención | Captación |
| Luis | 🔴 Fuera de ritmo | Prospección |
| Carla | 🟠 Atención | Solicitud → contrato |

Y **gestión por excepción**: debajo solo aparecen los que necesitan intervención.
El broker no lee ocho fichas si seis están bien.

---

## 7. Filtro de agente: una sola semántica

Arriba del broker, `Todos los agentes ▾` junto al periodo. Al elegir a
`Valentina Mora` **no se diseña otro tablero**: se reutiliza exactamente el del
agente, con una indicación:

```
Valentina Mora · supervisión
```

Equipo → agente → caso individual. Un mismo lenguaje en los tres niveles.

---

## 8. Segunda familia de KPI, solo del broker: Supervisión

Los cuatro círculos miden **resultado del equipo**. Aparte, una franja de cuatro
microindicadores de lo que **el broker sí controla** — no velocímetros:

| Indicador | Contenido | Acción |
|---|---|---|
| Captaciones por revisar | 3 pendientes · 1 supera el tiempo esperado | Revisar captaciones → |
| Evaluaciones pendientes | 2 solicitudes · la más antigua espera 2 días | Revisar expedientes → |
| Seguimiento del equipo | 82 % al día · 2 agentes concentran 7 vencidos | Ver seguimiento → |
| Distribución de carga | 1 agente sobrecargado · 38 % de las oportunidades | Revisar carga → |

Supervisar no es producir: estas acciones **no cuentan como producción del
agente** ni se mezclan con los cuatro KPI.

---

## 9. La conversión sale de arriba y mejora abajo

`Conversión` desaparece como KPI principal: era un concepto ambiguo sin
denominador. Baja al embudo, y **toda conversión nombra origen y destino**:

**Oferta** — Prospecto → Contactado · Contactado → Captación · Captación → Publicación
**Demanda y cierre** — Oportunidad → Visita · Visita → Solicitud · Solicitud aprobada → Contrato

Cada salto muestra `origen → destino`, `N inicial`, `N que avanzó`, `%` y
`N que no avanzó`:

```
Visita → Solicitud
8 visitas · 3 solicitudes · 38 % · 5 no avanzaron
🟠 Principal cuello: Visita → Solicitud
```

### 9.1 El broker además descubre concentración

Equipo en `Visita → Solicitud: 38 %` parece razonable. Al abrir:
`Valentina 52 · Andrea 47 · Carlos 41 · Luis 12`. Entonces BROX dice:

> El cuello está concentrado en un agente; el resto del equipo se mantiene
> estable.

Infinitamente más útil que «media del equipo 38 %».

---

## 10. Lectura, foco y cartera, por rol

| Bloque | Agente | Broker |
|---|---|---|
| Diagnóstico | **Lectura de tu gestión** | **Lectura del equipo** |
| Acción | **acciones comerciales** | **intervenciones de supervisión** |
| Cartera | **Mi cartera** (etapa, distrito) | **Cartera del equipo** (+ selector *Agente*) |
| Evolución | tu evolución, una métrica + meta | evolución del equipo, una métrica + meta |

Los tres grupos de la lectura se conservan —`Vas bien` / `Cuida esto` / `Puedes
mejorar`— y cambian de semántica: en el broker hablan de agentes, concentración y
cuellos, no de expedientes propios.

En `Evolución`, **una métrica elegida a la vez**, con su meta. Cuatro líneas
simultáneas son ruido.

---

## 11. Diferencia final entre los dos roles

| Pregunta | Agente | Broker |
|---|---|---|
| ¿Cómo voy? | mis 4 KPI | 4 KPI del equipo |
| ¿Llegaré a meta? | mi ritmo | ritmo del equipo |
| ¿Dónde pierdo? | mi embudo | embudo del equipo |
| ¿Qué requiere atención? | mis casos | excepciones del equipo |
| ¿Qué hago hoy? | acciones comerciales | intervenciones de supervisión |
| ¿Cómo está mi cartera? | mi cartera | cartera del equipo |
| ¿Quién necesita ayuda? | no aplica | agentes fuera de ritmo |
| ¿Quién decide/revisa? | espera cuando corresponde | bandeja de decisión |

**No son dos productos distintos: son dos niveles de la misma inteligencia
comercial.**

---

## 12. Las quince instrucciones congeladas

1. `Conversión` sale de los cuatro KPI principales. Toda conversión nombra origen y destino.
2. Los KPI canónicos son **Prospección efectiva, Captaciones activadas, Solicitudes generadas y Contratos firmados**.
3. Mismas definiciones para agente y broker; cambia solo el alcance.
4. El broker no recibe crédito personal por el resultado del equipo; su supervisión se mide aparte.
5. Cada KPI trae actual, meta, esperado a hoy, faltante, avance %, variación comparable y estado.
6. El semáforo mide **ritmo contra meta**, no porcentaje consumado.
7. El círculo lleva manija actual + marca de meta + marca discreta de ritmo esperado.
8. Las reglas 🟢/🟠/🔴/⚪ se determinan en el dominio, **nunca en Angular**.
9. Los porcentajes muestran su N; con muestra insuficiente el estado es neutral.
10. Agente y broker conservan los dos embudos Oferta / Demanda.
11. El agente recibe **acciones**; el broker, **intervenciones de supervisión**.
12. El broker dispone de filtro *Todos los agentes / agente individual*; al entrar a uno reutiliza las definiciones del tablero personal.
13. Sin rankings ni «mejor/peor agente»: gestión por excepción y contra meta.
14. El tablero **no duplica el mismo diagnóstico** en KPI, embudo, lectura y bandeja: un diagnóstico principal.
15. **Rendimiento, diagnóstico, cartera y acción son capas diferentes.** Ninguna tarjeta intenta resolver las cuatro.

---

## 12 bis. El anticipo en el Inicio

El pie del Inicio (D-E2-1 §6.2) muestra **estos mismos cuatro KPI** en miniatura
—nombre, `actual / metaPeriodo` y el semáforo de ritmo— y enlaza entero a esta
pantalla. **No define nada por su cuenta**: si aquí cambia una definición, allí
cambia sola — cuando los cuatro nombres cambiaron el 2026-08-11, el pie cambió
el mismo día.

**Y respeta la instrucción 4 también en miniatura.** Al agente le dice «hoy
deberías ir por 21»; al broker, «el equipo va por detrás» más el **pulso**
(§6.1) y cuántas operaciones esperan una decisión suya (§8). Pedirle a un broker
una producción personal en el pie sería contradecir aquí lo que esta pantalla
decidió. Cualquier cifra que el Inicio quiera enseñar y que no esté en esta
lista es señal de que falta decidirla aquí, no de que haya que inventarla allí.

---

## 13. Lo que faltaba decidir — cerrado el 2026-08-19 (E2.6)

Las siete cuestiones se resolvieron con la medición delante
(`diagnostico-e2-6-contraste-medias-y-metas.md`), no en abstracto.

- [x] **«Contacto comercial real»** → `prospeccion.fecha_contacto`, no el estado.
      La escalera de estados pierde los descartados, y los tres descartados de la
      base **sí** habían sido contactados.
- [x] **«Entró realmente a cartera»** → la **transición a ACTIVA**, no el estado
      actual ni `fecha_captacion`. Por estado salen 5 y por evento 9: la
      diferencia son las que ya cerraron en contrato.
- [x] **Atribución** → el agente responsable de cada hecho:
      `prospeccion.id_rol_agente`, `captacion.id_rol_agente`,
      `solicitud.id_rol_agente` y `contrato.id_rol_agente_cierre`. No hay reparto
      entre dos: el hecho tiene un dueño, y si intervienen dos, la reasignación ya
      dejó su rastro.
- [x] **Umbrales del ritmo** → `PoliticaComercial`: llega 100 %, atención 85 %,
      arranque 15 % del periodo, volumen mínimo 3, muestra mínima 5. Bajaron de la
      maqueta, donde estaban duplicados y ya divergían.
- [x] **Prorrateo de meta a mitad de periodo** → **no se implementa, y no es un
      olvido**. La meta es mensual y se fija por agente: quien entra a mitad de mes
      recibe la meta que su broker decida para ese mes, que es más honesto que un
      prorrateo automático sobre un alta cuya fecha exacta el sistema no siempre
      conoce. Si el negocio pide el automatismo, se decide aquí.
- [x] **Dónde se fijan las metas** → tabla `meta_comercial` (V65), mensual y por
      agente. Las fija el **broker sobre sus agentes** o el administrador sobre los
      de su organización, por `PUT /indicadores/metas`. **Un agente no fija la
      suya**, y **no existe meta de equipo**: la del equipo es la suma, así que no
      puede contradecir a sus sumandos. Si falta la de alguno, el ritmo del equipo
      se declara `COBERTURA_INCOMPLETA` en vez de compararse contra una meta
      parcial, que daría siempre una brecha a favor.
- [x] **«Puede cerrarse este mes»** → §14.

---

## 14. «Puede cerrarse este mes»: determinista, no pronóstico

**Tres condiciones, las tres comprobables**, y ninguna es una expectativa:

1. La solicitud está **aprobada** (`estado = A`). Es la fase formal de cierre:
   el broker ya evaluó y lo que falta es firmar. En revisión u observada tiene un
   bloqueante sin resolver; registrada ni siquiera entró a revisión. Y una
   oportunidad prometedora o una visita que fue bien **no entran de ninguna
   manera**.
2. **No tiene contrato todavía.** Con contrato ya no puede cerrarse: se cerró, y
   contarla otra vez sumaría dos veces el mismo dinero.
3. **La oferta sigue vigente.** Una oferta cuya vigencia pasó no se firma, por
   muy aprobada que esté.

**El importe sale de `monto_propuesto` y conserva su moneda.** No se convierte:
un tipo de cambio que nadie declaró es un número inventado dentro de una cifra
que se presenta como hecho. Con dos monedas se dice, y se muestra la de mayor
importe.

Medido el 2026-08-19 con esta definición: **cero operaciones**. La única
solicitud viva estaba en revisión y además con la oferta vencida el día 15. La
maqueta enseñaba «3 operaciones · US$ 9,300»; eran constantes.

E2 **no introduce probabilidad aprendida ni índices disfrazados**. Cuando exista
un pronóstico, será otra métrica con otro nombre, y dirá que lo es.

---

## 15. Lo que sigue abierto

- [ ] **Configuración de la política por organización.** `Regla.alcance()` ya lo
      prevé y ninguna regla lo usa: con una sola corredora real sería complejidad
      sin usuario. Se implementa cuando haya una segunda que pida otro umbral.
