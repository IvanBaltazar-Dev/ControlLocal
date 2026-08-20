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

## E2 · Dashboard inmobiliario ✅ CERRADA (2026-08-19)

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
- [x] **E2.2** La pelota — `DEPENDE_DE_MI` + `lado`/`paso` y una sola política de
      despacho de seis criterios
- [x] **E2.3** El hallazgo sale de la cola: mismo motor, otra salida
- [x] **E2.4** Capa de interpretación: `ComoEsta`, lectura y expediente de cuatro
      renglones
- [x] **E2.5** El Radar del broker: sus propios asuntos y el hallazgo de
      concentración
- [x] **E2.6** Contraste, pie y metas — ver abajo
- [x] **Gate** — cinco, y cuatro son nuevos de E2.6:
      `AislamientoDePruebasTest` (una prueba de integración no puede escribir en
      la base de desarrollo), `PoliticaUnicaTest` ampliado (la maqueta no puede
      divergir de la política, y los cuatro nombres canónicos salen del dominio),
      más `MatrizOperacionRolTest` y `GateDeCierreTest`, que ya estaban
- [x] **Pruebas** — reactor **956** · Angular **591** · prototipo **334**
- [x] **Evidencia** — `evidencia/2026-08-19-e2-6-contraste-pie-y-metas.md`

### E2.6 · Contraste, pie y metas — cerrada 2026-08-19

**Se midió el bloque entero antes de escribir la primera consulta**
(`diagnostico-e2-6-contraste-medias-y-metas.md`), y la medición cambió el
alcance en lugar de confirmarlo:

| | |
|---|---|
| **Los cuatro nombres canónicos** | D-E2-1 y D-E2-2 decían cosas distintas y había una comprobación que exigía los cuatro «letra por letra»: el gate pedía dos verdades. Gana el hecho de negocio, y «Locales» pasa a **«Propiedades»** |
| **El periodo** | de ventana móvil a **mes de calendario**. `metaEsperadaAHoy` sobre una ventana móvil es tautológica: los días transcurridos serían siempre los totales |
| **Las metas** | pasan a existir (V65). La del equipo **es la suma** de las de sus agentes; si falta la de alguno, el ritmo se declara sin base en vez de compararse contra una meta parcial |
| **El ritmo** | baja de la maqueta al dominio, con sus cinco umbrales |
| **«Puede cerrarse este mes»** | determinista: aprobadas, sin contrato y con oferta vigente. Hoy son **cero**, y eso es lo que dice |
| **El contraste** | se implementa **la degradación**, no un rango falso: la mejor celda de la cartera tenía cuatro observaciones y el mínimo son diez |
| **`generadoEn`** | un solo productor, dentro de `rendimiento`. El Inicio lo lee, igual que lee `ambito` |

**Y dos trabajos previos que la medición descubrió, y que no eran de E2.6:**

1. **Una prueba de integración podía escribir en `controllocal_dev`**, y lo hizo:
   162 propiedades, 120 captaciones y 184 hitos de precio. La cabecera del Inicio
   decía «125 cosas necesitan tu atención». V64 lo retiró identificándolo por la
   dirección —literal del código de prueba, y ninguna propiedad legítima la
   usa—, y `BaseDeDatosDePruebas` cierra la causa: falla antes de arrancar.
2. **El hito `P` nunca se había ejercitado.** Cero filas con cinco publicaciones
   vivas. Sin backfill inventado —nadie puede demostrar qué renta vio el mercado
   en el pasado— pero sí verificado hacia adelante: publicar produce exactamente
   un `P`, republicar el mismo importe no duplica, y cambiarlo añade el segundo.

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
