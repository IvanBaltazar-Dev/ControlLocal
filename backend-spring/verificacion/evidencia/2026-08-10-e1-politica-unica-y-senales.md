# E1 — política única, `senales[]` y lenguaje de negocio

**Fecha:** 2026-08-10
**Etapa:** E1 · Instrumentación y políticas
**Estado:** CERRADA — gate verde, suites verdes, repositorio coherente

---

## Qué se cerró

Dos problemas distintos con la misma raíz: **la misma decisión escrita en varios
sitios**.

1. **Los umbrales estaban repartidos.** El plazo de recontacto —el más
   importante del sistema— vivía en cinco lugares, coordinados por un comentario
   que pedía que los números cuadraran. Nada rompía si uno se quedaba atrás.
2. **Angular decidía qué significan.** Ocho ternarios repartidos por rol más un
   `> 7`, que era la cuarta copia del plazo.

## La quinta copia

El inventario encontró cuatro porque buscó las que **se usaban**. El gate
encontró una más: `Prospeccion.DIAS_RECONTACTO = 7`, pública, con javadoc y
muerta. Es la peor clase de copia — la que alguien reutiliza de buena fe seis
meses después porque parece la definición oficial.

## La contradicción que estaba viva

Al centralizar la prioridad salió que **las dos escalas de rol se
contradecían**: los recontactos vencidos eran lo primero para el administrador y
lo cuarto para el broker. No era un bug reportado por nadie; era imposible de ver
mientras el orden estuviera en dos listas de pesos distintas.

---

## Lo que quedó ejecutable

| Pieza | Dónde |
|---|---|
| Política única, 7 reglas | `service/soporte/PoliticaComercial.java` |
| Señales clasificadas en el cable | `IndicadorService.Senal` → `IndicadorSenalResponse` |
| Espejo declarado del SPA (2 valores) | `frontend-angular/src/app/core/politica-comercial.ts` |
| Gate anti-recaída | `arquitectura/PoliticaUnicaTest.java` |

El gate recorre el fuente del backend **y del SPA**. No persigue el número suelto
—un 7 puede ser cualquier cosa— sino el 7 **aplicado como plazo**:
`minusDays(7)`, `plusMonths(6)`, `< 10`, y las constantes retiradas por nombre.
Ignora comentarios a propósito: este repositorio los usa para explicar las
reglas, y sin eso el gate se dispararía con su propia documentación.

---

## Verificación

| Suite | Resultado |
|---|---|
| `mvn clean install` | **625** service + **43** web + **103** app · 0 fallas |
| Angular `ng test` | **545/545** |
| `ng build` | limpio |
| E2E `e4-dashboard` | **125/125** (5 comprobaciones nuevas de `senales` sobre HTTP real) |
| E2E `personas` | **126/126** (reasignación agente ↔ broker con la validación endurecida) |
| E2E `v6` | **46/46** |

## La suite que bloqueó el cierre

`e2e-v6` terminó en 45/1 mientras E1 estaba listo. La falla era **de E0**: su
cambio 0.1 hizo que el alta dejara un hito `U`, así que tras editar el precio hay
dos, y la aserción committeada exigía uno.

El producto estaba bien y la prueba vieja. Se corrigió para comprobar la
semántica nueva —`9200>9900`, en orden y sin pisarse— en vez de relajar el
número, porque un solo hito volvería a significar que la edición machaca el
precio de salida.

**E1 no se cerró hasta que esa suite volvió a verde.** Una etapa cerrada deja el
repositorio coherente; si no, "cerrada" no significa nada.

---

## Anotado, fuera de alcance

- El mapeo estado → tono duplicado en diez pantallas (deuda visual, no de política).
- La configuración por organización: declarada en `Regla.alcance()`, sin
  implementar a propósito. Con una sola corredora real es complejidad sin usuario.
- El respaldo de conversión del dashboard, que toma la cifra de otro agente
  cuando no hay cohorte. Se corrige como **E2.0**.
