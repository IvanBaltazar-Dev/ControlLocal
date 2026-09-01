# <ID> — <titulo>

**Estado:** DRAFT
**Base:** <commit/branch>
**Responsable de CONTROL:** <modelo/persona>
**CONSTRUCTOR:** GLM-5.3 / ZCode
**AUDITOR:** Claude Opus

## Objetivo

<Una sola consecuencia verificable que debe quedar cierta al cerrar el corte.>

## Por qué entra ahora

<Dependencia o riesgo que justifica abrir este corte.>

## Invariantes congeladas

- <regla 1>
- <regla 2>

Estas reglas no se reinterpretan durante implementación. Una contradicción obliga a STOP y retorno a CONTROL.

## Fuentes autoritativas

Leer únicamente lo necesario:

- `AGENTS.md`
- `<decision vigente>`
- `<matriz/contrato/gate relevante>`

## Alcance permitido

- <backend / frontend / migración / docs / tests>

## Fuera de alcance

- <explícito>

## Criterios de aceptación

- [ ] <criterio observable 1>
- [ ] <criterio observable 2>

## Pruebas y gates obligatorios

- [ ] <test/gate>
- [ ] `backend-spring/verificacion/Verificar-Cierre.ps1` cuando corresponda al cierre backend
- [ ] build/test frontend cuando el cambio lo requiera

## STOP — volver a CONTROL si

- aparece una decisión funcional no contenida en las fuentes autoritativas;
- cumplir un criterio exige cambiar una invariante;
- se descubre pérdida de datos, cruce de tenant o ampliación de permisos no prevista;
- el alcance necesita expandirse de forma material.

## Entrega esperada

El CONSTRUCTOR deja commit candidato + `HANDOFF.md`. Después el AUDITOR emite `AUDIT.md`.
