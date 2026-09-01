# BROX — Flujo operativo de agentes

**Estado:** VIGENTE para cortes nuevos.

Este documento define cómo colaboran CONTROL, CONSTRUCTOR y AUDITOR sin duplicar trabajo ni convertir varios agentes en escritores concurrentes.

## 1. Principio

Un corte tiene tres responsabilidades y un solo escritor.

- **CONTROL** decide y congela el corte. No programa.
- **CONSTRUCTOR** es el único escritor. Implementa, prueba y corrige.
- **AUDITOR** intenta demostrar que el candidato es incorrecto. No escribe durante la primera auditoría.

La herramienta o proveedor puede cambiar. La responsabilidad no cambia.

## 2. Asignación recomendada de modelos

- **CONTROL:** modelo frontier con razonamiento fuerte. Puede ser ChatGPT/GPT.
- **CONSTRUCTOR:** GLM-5.3 en ZCode. Usa el pool barato para exploración, implementación, pruebas y correcciones.
- **EXPLORACIÓN AUXILIAR:** subagentes ZCode sobre GLM-5.3-Flash cuando la tarea sea mecánica y pueda resumirse antes de volver al constructor.
- **AUDITOR:** Claude Opus. Sólo recibe contexto acotado del corte, diff, evidencia y resultados de pruebas. No debe volver a investigar todo el repositorio salvo que un hallazgo concreto lo exija.

Objetivo económico: gastar tokens frontier en decisiones y auditoría, no en lectura repetitiva, edición rutinaria o ciclos de prueba.

## 3. Artefactos obligatorios por corte

Cada corte nuevo vive en:

`docs/ai/cortes/<ID>/`

Archivos:

1. `CUT.md` — contrato inmutable del corte durante implementación.
2. `HANDOFF.md` — entrega del constructor al auditor.
3. `AUDIT.md` — veredicto del auditor y hallazgos.

No se usa el chat como autoridad durable. Las decisiones que cambien reglas vigentes deben terminar en un `decision-*.md` o en el documento autoritativo correspondiente.

## 4. Estado del corte

`CUT.md` contiene uno de estos estados:

- `DRAFT`
- `READY`
- `BUILDING`
- `AUDIT`
- `REJECTED`
- `CLOSED`

Sólo CONTROL puede mover `DRAFT -> READY`.

CONSTRUCTOR mueve `READY -> BUILDING -> AUDIT` cuando entrega candidato verificable.

AUDITOR mueve lógicamente a `REJECTED` mediante `AUDIT.md` o aprueba el cierre. El cierre documental definitivo corresponde a CONTROL después de verificar evidencia.

## 5. Contrato mínimo de CUT.md

Debe declarar:

- objetivo;
- por qué entra ahora;
- invariantes que no se pueden reinterpretar;
- fuentes autoritativas que deben leerse;
- alcance permitido;
- fuera de alcance;
- criterios de aceptación;
- pruebas/gates obligatorios;
- condición de STOP: qué ambigüedad obliga a devolver el corte a CONTROL.

El constructor no debe redescubrir el producto completo. Si el corte no puede ejecutarse leyendo `AGENTS.md`, `CUT.md` y las fuentes allí citadas, el corte todavía no está listo.

## 6. Contrato mínimo de HANDOFF.md

El constructor entrega exclusivamente hechos verificables:

- commit candidato;
- archivos modificados;
- migraciones creadas;
- pruebas ejecutadas y resultado;
- gates ejecutados y resultado;
- decisiones tomadas durante implementación: idealmente ninguna;
- riesgos o dudas residuales;
- desviaciones respecto de `CUT.md`: deben ser cero o justificadas y devueltas a CONTROL.

No declarar `PASS` por haber ejecutado sólo `mvn clean install`. El cierre backend usa el mecanismo de cierre definido en `AGENTS.md`.

## 7. Contrato mínimo de AUDIT.md

El auditor responde:

- `PASS` o `REJECT`;
- severidad de cada hallazgo;
- archivo/símbolo afectado;
- invariante o criterio violado;
- evidencia reproducible;
- prueba que falta o que debería fallar;
- si el hallazgo es técnico reproducible o una decisión funcional nueva.

El auditor no arregla un rechazo en la primera ronda. Un rechazo técnico vuelve al CONSTRUCTOR.

## 8. Regla de contexto y tokens

Orden de lectura del CONSTRUCTOR:

1. `AGENTS.md`.
2. `CUT.md` del corte activo.
3. Sólo los documentos autoritativos citados en `CUT.md`.
4. Código necesario para implementar.

Orden de lectura del AUDITOR:

1. `AGENTS.md` — sólo protocolo y restricciones relevantes.
2. `CUT.md`.
3. `HANDOFF.md`.
4. `git diff` del candidato contra su base.
5. Evidencia/pruebas.
6. Código adicional únicamente si un hallazgo lo exige.

Prohibido iniciar una auditoría con «revisa BROX completo».

## 9. Exploración

No se exige Serena, Graphify ni otro MCP como dependencia del proceso.

- `rg`, búsqueda estructural, IDE o MCP son herramientas, no autoridad.
- Un subagente de exploración debe devolver un resumen acotado: archivos/símbolos, consumidores, riesgos y evidencia.
- El constructor principal no debe recibir transcript completo de exploración si un resumen verificable basta.

Añadir Serena o Graphify sólo cuando una medición real demuestre que resuelve un cuello de botella concreto.

## 10. Concurrencia

Durante un corte:

- sólo CONSTRUCTOR modifica código del candidato;
- CONTROL y AUDITOR pueden leer;
- no ejecutar dos constructores sobre el mismo árbol de trabajo;
- si se requiere paralelismo, usar worktrees/ramas aisladas y no mezclar resultados sin nueva auditoría;
- no ejecutar builds frontend pesados en paralelo con E2E de latencia, conforme a `AGENTS.md`.

## 11. Integración entre ZCode y Claude Code

No se intenta que las dos aplicaciones compartan memoria privada. Se conectan por Git y por los tres artefactos del corte.

Flujo:

`CONTROL -> CUT.md -> ZCode/GLM constructor -> commit + HANDOFF.md -> Claude/Opus auditor -> AUDIT.md -> GLM corrige o CONTROL cierra`

Este mecanismo es proveedor-agnóstico: si cambia GLM, Claude o GPT, el proceso sigue siendo el mismo.

## 12. Definition of Done

Un corte sólo puede cerrarse cuando:

- criterios de aceptación satisfechos;
- pruebas y gates requeridos ejecutados de verdad;
- evidencia guardada;
- `HANDOFF.md` completo;
- `AUDIT.md` en `PASS`;
- documentación autoritativa actualizada si cambió el comportamiento;
- no quedan decisiones funcionales implícitas;
- commit candidato identificable y árbol limpio.
