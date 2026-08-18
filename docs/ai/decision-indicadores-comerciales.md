# D-E2-2 · Indicadores comerciales: los cuatro KPI, el ritmo y la lectura por rol

**Qué responde:** qué se mide en la pantalla de indicadores, con qué definiciones
y qué cambia entre agente y broker.
**Estado:** decidido el 2026-08-11. **Pendiente de implementación** — este
documento congela las definiciones antes de tocar código.
**Relacionado:** `decision-inicio-foco-y-resolucion.md` (D-E2-1) para el Inicio;
`inventario-umbrales-de-dominio.md` para dónde viven las reglas.

---

## 1. Los cuatro KPI canónicos

| KPI | Agente | Broker |
|---|---|---|
| **Prospección efectiva** | sus prospectos que llegaron a contacto comercial real | suma del equipo |
| **Captaciones activadas** | sus captaciones que entraron realmente a cartera | suma del equipo |
| **Solicitudes generadas** | solicitudes originadas por su gestión | suma del equipo |
| **Contratos firmados** | contratos atribuidos a su gestión | suma del equipo |

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

## 13. Lo que falta decidir antes de implementar

- [ ] Definición exacta de «contacto comercial real» que convierte un prospecto en *prospección efectiva*
- [ ] Definición de «entró realmente a cartera» para *captaciones activadas* (¿estado activo, o encargo firmado?)
- [ ] Regla de atribución de una solicitud y de un contrato a un agente cuando intervienen dos
- [ ] Umbrales de 🟠 y 🔴 sobre la desviación de ritmo, y N mínimo para salir de ⚪
- [ ] Fórmula de prorrateo de meta por alta, licencia o cambio de equipo a mitad de periodo
- [ ] Dónde se fijan las metas y quién puede cambiarlas
- [ ] **«En juego este mes»**: qué suma exactamente. La maqueta del Inicio lo lee
      como *renta mensual de las operaciones que pueden cerrarse en el periodo*,
      pero no es todavía una métrica de este documento
