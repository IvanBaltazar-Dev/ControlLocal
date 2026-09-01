# <ID> — HANDOFF del CONSTRUCTOR

**Estado del candidato:** AUDIT
**Base:** <sha>
**Commit candidato:** <sha>
**Constructor:** GLM-5.3 / ZCode

## Resumen

<Qué quedó implementado, sin reinterpretar la decisión.>

## Archivos modificados

- `<ruta>` — <por qué>

## Migraciones

- `<Vxx__...sql>` o `Ninguna`

## Pruebas ejecutadas

| Comando/gate | Resultado | Evidencia |
|---|---|---|
| `<comando>` | PASS/FAIL | `<ruta/log>` |

## Criterios de aceptación

- [ ] <criterio 1> — evidencia: <...>
- [ ] <criterio 2> — evidencia: <...>

## Decisiones funcionales tomadas durante implementación

**Ninguna.**

Si no es ninguna, STOP: devolver a CONTROL antes de auditoría.

## Riesgos o dudas residuales

- <ninguno / lista concreta>

## Desviaciones respecto de CUT.md

**Ninguna.**

## Solicitud al AUDITOR

Auditar exclusivamente contra `CUT.md`, este handoff, el diff del candidato y la evidencia. Buscar especialmente ampliación de permisos, cruces de tenant, pérdida de datos, regresiones, bypass de gates y pruebas ausentes.
