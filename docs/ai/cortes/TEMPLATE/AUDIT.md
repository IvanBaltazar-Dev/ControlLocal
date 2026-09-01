# <ID> — AUDITORÍA

**Auditor:** Claude Opus
**Commit auditado:** <sha>
**Veredicto:** PASS | REJECT

## Alcance auditado

- `CUT.md`
- `HANDOFF.md`
- diff del candidato
- evidencia y resultados de pruebas

## Hallazgos

| Severidad | Archivo/símbolo | Invariante/criterio | Evidencia | Acción |
|---|---|---|---|---|
| <CRITICAL/HIGH/MEDIUM/LOW> | `<ruta/símbolo>` | <regla> | <reproducción> | <volver al constructor / decisión CONTROL> |

Si no hay hallazgos materiales, indicar `Ninguno`.

## Pruebas faltantes o engañosas

- <ninguna / lista concreta>

## Riesgos de autorización / tenant / datos

- <ninguno / lista concreta>

## Clasificación del resultado

- [ ] PASS: no se encontró violación material y la evidencia satisface el corte.
- [ ] REJECT técnico: vuelve al CONSTRUCTOR; no requiere nueva decisión funcional.
- [ ] STOP funcional: CONTROL debe resolver una decisión no contenida en el contrato.

## Nota al CONSTRUCTOR

El AUDITOR no modifica código en esta primera ronda. Cada hallazgo debe volver como una instrucción reproducible y verificable.
