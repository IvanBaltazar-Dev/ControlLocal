# I0 · Industrialización BROX

**Estado:** 🟡 **EN CURSO**  
**Abierto:** 2026-08-25, después de publicar el cierre definitivo del Corte 4  
**SHA de entrada:** `795ffbf16384853b3e2c220895d4ac5ff6d01d06`  
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`  
**Ámbito:** documentación, protocolo de ejecución y preparación del siguiente corte

I0 convierte el trabajo de las sesiones en un sistema que otra sesión pueda
reanudar sin depender de memoria, resúmenes privados o cifras que envejecieron.
No implementa producto, ~~no abre el Corte 5~~ y no modifica `V81`, `V82` ni
`V83`. **La mitad tachada duró un día**: el titular congeló el encargo del Corte 5
el mismo 2026-08-25 y la subtanda **5A** (`V84`) se implementó en paralelo. I0 no
la abrió ni la bloqueó; las dos avanzan a la vez. Ver §2.

## 1. Qué gobierna cada documento

> **⚠ ESTA TABLA ES UNA PROPUESTA DE I0, NO LA AUTORIDAD VIGENTE.** Lo que
> gobierna hoy son **tres** documentos —`mapa-ejecucion-brox.md`,
> `checklist-captura-moat-e-inteligencia-inmobiliaria.md` y `decision-*.md`—,
> como dicen el mapa y `CLAUDE.md`. Ampliar esa lista es un **cambio de autoridad
> documental** y **lo decide el titular**: no se establece por escribirlo aquí ni
> por editar la tabla del mapa dentro de un corte de catálogo. Se intentó el
> 2026-08-25 y quedó revertido en la auditoría de 5A.
>
> La tabla sigue siendo útil como lo que es: **qué pregunta responde cada
> documento**. Lo que está pendiente de decisión es cuáles **mandan**.

| Documento | Responde | Autoridad |
|---|---|---|
| `north-star-brox.md` | hacia dónde debe avanzar BROX | dirección estratégica |
| `mapa-ejecucion-brox.md` | dónde estamos y cuál es el siguiente paso | estado vigente |
| `checklist-captura-moat-e-inteligencia-inmobiliaria.md` | qué requisitos cierran la etapa | cierre de etapa |
| `pendientes-brox.md` | qué queda en todo el repositorio | inventario transversal |
| `auditoria-profundidad-inmobiliaria.md` | qué son los cortes del catálogo | profundidad del modelo |
| `decision-*.md` | qué decisión funcional está congelada | contrato de decisión |
| `matriz-operacion-rol.md` | quién puede hacer qué | seguridad operativa |

Las evidencias y los encargos de sesión explican cómo se llegó a una decisión;
no sustituyen al mapa ni convierten una cifra histórica en estado actual.

## 2. Estado de entrada

- **Corte 4:** cerrado definitivamente.
- **SHA final:** `795ffbf16384853b3e2c220895d4ac5ff6d01d06`.
- **Auditoría final:** limpia, sin noveno contraejemplo.
- **Cartera medida:** 7 propiedades publicables y 19 bloqueadas de 26.
- **4.P / V83:** cerrado técnicamente; la auditoría final queda registrada en el
  cierre documental del bloque.
- **Corte 5:** no abierto.
- **Código y migraciones:** sin cambios en I0.

Las cifras `5/26` y `21 bloqueadas` quedan únicamente como registros fechados
de los pasos anteriores del Corte 4. No se presentan como estado presente.

> **Esta sección es el estado DE ENTRADA de I0, y ya tiene dos líneas
> superadas** — se fechan, no se reescriben:
>
> - **«Corte 5: no abierto»** fue cierto al abrir I0 y dejó de serlo el mismo
>   **2026-08-25**: el titular congeló `encargo-corte-5-terreno.md` con D-1…D-7 y
>   la **subtanda 5A** entró en ejecución. **I0 sigue sin abrirlo y sin
>   implementarlo** (§1 y §7): los dos avanzan en paralelo.
> - **«7 publicables y 19 bloqueadas de 26»** es igualmente un registro fechado
>   **anterior a `V84`**. 5A estrena dos `PUB` en `T` y el único terreno de la
>   cartera pasa a bloqueado; la cifra con autoridad sale de **la evidencia de
>   cierre de 5A**.

## 3. Entregables de I0

1. Actualizar las fuentes de estado con la fecha, rama, SHA y cierre real.
2. Marcar como históricos los encargos y borradores que ya no gobiernan el
   trabajo, conservando su evidencia y sus decisiones.
3. Crear un encargo único para el Corte 5, con preflight, decisiones,
   migración, pruebas, gates y protocolo de STOP.
4. Separar explícitamente decisiones vigentes, hallazgos históricos y trabajo
   todavía no decidido.
5. Comprobar que no queda ningún documento gobernante presentando como pendiente
   una decisión ya cerrada.

## 4. Gate de salida

I0 sólo cierra cuando:

- el mapa y el inventario transversal describen el mismo estado;
- el cierre de Corte 4 nombra el SHA final publicado;
- el único siguiente trabajo técnico es el encargo de Corte 5, completo y listo
  para congelarse;
- las cifras históricas están fechadas y no se leen como estado actual;
- no hay una instrucción activa que contradiga una decisión `D-*` vigente;
- no se ha tocado código, migración, Angular ni `V81`/`V82`/`V83`.

## 5. Fuera de I0

No entran aquí la implementación del Corte 5, la rotación de secretos, RLS,
producción, el matcher, E3, KAIROS ni la eliminación masiva de documentos.
Una eliminación sólo se hará después de comprobar enlaces entrantes y confirmar
que el archivo no conserva una decisión o evidencia única.
