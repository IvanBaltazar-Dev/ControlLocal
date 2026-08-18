# Checklist — captura del moat e inteligencia inmobiliaria

**Qué responde:** qué falta para cerrar la etapa en curso.
**Dónde estamos:** `mapa-ejecucion-brox.md`. Este documento no lleva la cuenta;
lleva los requisitos.

**Actualizado:** 2026-08-18

---

## La mecánica

Una etapa pasa a ✅ **CERRADA** cuando tiene las tres cosas. No dos.

| | Qué es | Qué NO cuenta |
|---|---|---|
| **Gate** | algo que rompe el build si la etapa se deshace | un comentario pidiendo cuidado |
| **Pruebas** | unitarias del comportamiento + suite E2E verde | "compila" |
| **Evidencia** | archivo en `backend-spring/verificacion/evidencia/` con fecha y salida real | "lo probé a mano" |

Y una condición que no se negocia: **el repositorio queda coherente**. Una suite
roja de una etapa anterior bloquea el cierre de la actual, aunque la rotura sea
ajena. Pasó el 2026-08-10 con `e2e-v6`: E0 había cambiado la semántica de los
precios y su aserción se quedó vieja; E1 no se cerró hasta reconciliarlo.

---

## E0 · Histórico económico ✅ CERRADA (2026-08-10)

- [x] **0.1** Primer hito `U` al alta + backfill de los existentes (V45)
- [x] **0.2** Hito `P` al publicar la renta
- [x] **0.3** Decisión del hito `O` — `decision-hito-oferta-de-demanda.md`
- [x] **Gate** — `HistoricoPrecioIntegrationTest`
- [x] **Pruebas** — `LocalComercialServiceImplTest`, `PublicacionServiceImplTest`, suite `v6` **46/46**
- [x] **Evidencia** — `evidencia/2026-08-10-e0-1-primer-precio-autorizado.md`, `evidencia/2026-08-10-e0-2-renta-publicada.md`

**Reconciliación pendiente al cierre, ya resuelta:** la aserción
`el hito de precio U nace con tenant` de `e2e-v6` exigía **una** fila cuando la
serie append-only pasó a tener dos (alta + edición). Corregida el 2026-08-10 para
comprobar la semántica nueva: los dos hitos, en orden y sin pisarse.

**Queda abierto para E3, y hay que resolverlo ANTES de la primera escritura de
`O`** (las tres del §"Dos cuestiones abiertas" de la decisión):

- [ ] Contra qué precio pedido se congela el snapshot (`P` vigente, con caída a `U`)
- [ ] Dónde vive `O` sin mezclar dos negociaciones del mismo inmueble
- [ ] Menor: dónde consta lo declarado

## E1 · Instrumentación y políticas ✅ CERRADA (2026-08-10)

- [x] **1.1** Inventario de umbrales y su clasificación — `inventario-umbrales-de-dominio.md`
- [x] **1.2** Política única `PoliticaComercial` — 7 reglas con nombre estable, significado, valor, unidad, alcance y versión
- [x] **1.3** Cinco copias del plazo de recontacto reducidas a una (la quinta, muerta en el dominio, la encontró el gate)
- [x] **1.4** Longitud mínima del motivo de reasignación aplicada en el **backend**, en los dos puntos
- [x] **1.5** Severidad y orden por concepto nombrados — `Concepto` + `NivelAtencion`
- [x] **1.6** `senales[]` en el cable: el hecho llega interpretado; Angular sin umbrales
- [x] **1.7** Regla transversal de lenguaje — `decision-lenguaje-natural-de-negocio.md`
- [x] **Gate** — `PoliticaUnicaTest`: recorre backend y SPA y rompe el build si una regla se reescribe a mano
- [x] **Pruebas** — `PoliticaComercialTest` (16), `IndicadorServiceImplTest` (32, 4 nuevas de `senales`), `AsignacionServiceImplTest`; Angular **545/545**; suites `e4-dashboard` **125/125** y `personas` **126/126**
- [x] **Evidencia** — `evidencia/2026-08-10-e1-politica-unica-y-senales.md`

**Anotado y fuera de alcance** (en el inventario, §"Lo que se anotó y no entró"):
el mapeo estado → tono duplicado en diez pantallas, y la configuración de la
política por organización, que sigue declarada y sin implementar a propósito.

## E2 · Dashboard inmobiliario 🟡 EN CURSO

Requisito propio de la etapa, además del gate/pruebas/evidencia:
**cada subtanda termina con algo abrible en `localhost:4200/dashboard`.**

- [x] **E2.0** La conversión deja de tomar prestada la de otro agente. Sin muestra
      no es 0: es **no calculable**, y se dice. `conversionPropia` pasa a ser el
      único numérico nulable del resumen
- [x] **E2.1** Cabecera de decisión — cuántas cosas necesitan tu atención y
      cuántas operaciones hay abiertas, más la línea económica **solo cuando el
      dato aguanta** (alquileres firmados en el periodo). El conteo lo suma el
      dominio: `Concepto` distingue lo que cuenta cosas de lo que mide días
- [x] **E2.D** Diseño congelado (2026-08-11) — `decision-inicio-foco-y-resolucion.md`
      (Inicio: 5 asuntos + Radar de dos modos, política de despacho, regla contra
      duplicados) y `decision-indicadores-comerciales.md` (4 KPI canónicos, ritmo
      contra meta, agente vs broker). Maqueta verificada con 85 comprobaciones
- [ ] **E2.2** La pelota — de las abiertas, cuáles dependen de ti y de quién
      dependen las demás (propietario, interesado, broker, documentación).
      **Es el primer filtro del despacho** (D-E2-1 §3): solo `DEPENDE_DE_MI`
      compite por uno de los cinco sitios del Inicio
- [ ] **E2.3** Inmuebles que necesitan atención — embudo por inmueble y **dónde se
      frena**, sobre `/indicadores/avance` (RF-017), que ya existe
- [ ] **E2.4** Encargos por vencer y propietarios sin novedades, separados del
      diagnóstico comercial del inmueble
- [ ] **E2.5** Hallazgos — coincidencias con utilidad real, no otra bandeja de alertas
- [ ] **Gate** — por definir al cerrar la etapa
- [ ] **Pruebas** — Angular verde + `e4-dashboard` verde · *al día: **550/550** y
      **129/129** tras E2.1*
- [ ] **Evidencia** — capturas del tablero por subtanda

## Ruta a BROX 1.0 · bloques 2 y 3 ✅ CERRADOS (2026-08-18)

**Propiedad Universal Operativa + Captura v1.** El esquema de V46–V52 pasó a ser
capacidad: hay un caso de uso que registra, lee y edita una propiedad por el
modelo nuevo, y un motor de captura que decide qué preguntar desde el catálogo.

| | Gate | Pruebas | Evidencia |
|---|---|---|---|
| **2** · Núcleo universal | V54–V58 + `MatrizOperacionRolTest` + `PoliticaUnicaTest` + inventario de `GateDeCierreTest` | `PropiedadUniversalIntegrationTest` **24/24** contra PostgreSQL real | `2026-08-18-propiedad-universal-y-captura.md` |
| **3** · Motor de registro | `service/captura` + `borrador_captura` con su CHECK de intención | recorrido completo de 6 pasos, ejecutado y retomado | ídem |

**Los dos pendientes técnicos se cerraron dentro de esta tanda**, no en una
etapa aparte:

1. **El simulacro de recuperación** dejó de leer el estado de la base compartida
   y pasa a **construir su precondición**: tenant propio con un `TENANT_ADMIN`
   sin factor MFA. Repetible, y ya no hay error conocido que arrastrar.
2. **La operación inmobiliaria dejó de inferirse.** `= OPERACION_ALQUILER` en
   `PrecioPropiedad` y `= "A"` en `Captacion` se retiraron. Toda escritura
   económica declara VENTA o ALQUILER; si no se sabe, se **declara faltante**.

**Vocabulario congelado:** solo `VENTA` y `ALQUILER`. Una propiedad disponible
para las dos cosas se representa con **dos encargos independientes** — el enum
rechaza `AMBAS` y `COMPRA` explicando por qué, no con «valor inválido».

**El hallazgo:** V50 creía admitir venta y alquiler simultáneos, pero
`uq_captacion_activa_por_local` seguía en pie y no distingue operación. Funcionaba
con los dos encargos PENDIENTES —así se verificó— y fallaba al aprobar el
segundo. **V58** lo sustituye por la invariante correcta: un encargo vivo por
(propiedad, operación).

**Lo que NO se construyó, a propósito:** matcher v2, negociación E3, compraventa
completa, Neo4j, WhatsApp, LLM, voz, embeddings, memoria vectorial, LangGraph y
automatizaciones autónomas de KAIROS. Todos dependían de este spine.

---

## E3 · Negociación ⬜

Implementa el hito `O` decidido en E0.3. **Bloqueada** por las tres cuestiones
abiertas de E0: hay que resolverlas antes de escribir la primera oferta.

## E4 · Moat Health ⬜ · E5 · Matcher v2 ⬜ · E6 · KAIROS ⬜

## E7 · Portafolio ⬜ · E8 · Inteligencia ⬜ · E9 · Certificación ⬜

> Los requisitos de E4 a E9 **no están definidos todavía**, y este documento no
> los inventa. Se escriben cuando la etapa anterior cierre y la siguiente pase a
> 🟡 EN CURSO, que es cuando se sabe de verdad qué hace falta.
