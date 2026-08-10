# Mapa de ejecución BROX

**Esta es la portada del avance.** Si quieres saber dónde estamos, qué cerramos y
qué sigue, se responde aquí y en ninguna otra parte.

**Actualizado:** 2026-08-10

---

## Dónde estamos

| Etapa | Estado | Lo que puedes comprobar |
|---|---|---|
| **E0** · Histórico económico | ✅ **CERRADA** | `U` inicial + backfill, hito `P` de renta publicada, decisión del hito `O` |
| **E1** · Instrumentación y políticas | ✅ **CERRADA** | política única, `senales[]` clasificadas, Angular sin umbrales |
| **E2** · Dashboard inmobiliario | 🟡 **EN CURSO** | aquí empieza el cambio visual: el tablero pasa a centro de decisión |
| **E3** · Negociación | ⬜ | — |
| **E4** · Moat Health | ⬜ | — |
| **E5** · Matcher v2 | ⬜ | — |
| **E6** · KAIROS | ⬜ | — |
| **E7** · Portafolio | ⬜ | — |
| **E8** · Inteligencia | ⬜ | — |
| **E9** · Certificación | ⬜ | — |

Una etapa pasa a ✅ **CERRADA** con **gate + pruebas + evidencia**, y recién
entonces la siguiente pasa a 🟡 **EN CURSO**. El detalle de qué falta para cerrar
cada una vive en `checklist-captura-moat-e-inteligencia-inmobiliaria.md`.

---

## Qué cerramos

### E0 · Histórico económico — cerrada 2026-08-10

Los precios dejaron de ser un campo que se sobrescribe y pasaron a ser una serie.

| | |
|---|---|
| 0.1 | Primer hito `U` (autorizado) al dar de alta el inmueble, más backfill de los existentes (V45, 16 rescatados) |
| 0.2 | Hito `P` cuando se publica la renta |
| 0.3 | Decisión funcional del hito `O` — la oferta del interesado (`decision-hito-oferta-de-demanda.md`) |

**Verificable:** editar el precio de un local ya no borra el de salida —quedan
los dos hitos, en orden—. Suite `v6`: **46/46**.

### E1 · Instrumentación y políticas — cerrada 2026-08-10

El sistema dejó de tener la misma regla escrita en varios sitios, y el frontend
dejó de decidir qué significan los números.

| | |
|---|---|
| 1.1 | Inventario de umbrales (`inventario-umbrales-de-dominio.md`) |
| 1.2 | `PoliticaComercial`: 7 reglas con nombre, significado, valor, unidad, alcance y versión |
| 1.3 | `senales[]` en el cable — el hecho llega ya interpretado (R-07) |
| 1.4 | Regla transversal de lenguaje (`decision-lenguaje-natural-de-negocio.md`) |

**Verificable:** el plazo de recontacto está en **un** sitio (estaba en cinco); la
API rechaza un motivo de reasignación de tres caracteres; el dashboard ya no
calcula ningún umbral. Suites `e4-dashboard` **125/125** y `personas` **126/126**.

---

## Qué sigue: E2 · Dashboard inmobiliario

El tablero actual informa. E2 lo convierte en **centro de decisión**: qué
necesita de ti, qué inmueble está frenado y dónde.

**Regla de la etapa:** cada subtanda termina con algo que se puede abrir en
`localhost:4200/dashboard` y evaluar a ojo. Nada de bloques invisibles.

| Subtanda | Qué se ve al terminar | Estado |
|---|---|---|
| **E2.0** | La conversión deja de tomar prestado el número de otro agente | ✅ |
| **E2.1** | Cabecera de decisión: "8 cosas necesitan tu atención · 4 operaciones abiertas" | ✅ |
| **E2.2** | La pelota: de lo abierto, qué depende de ti y qué de otros | ⬜ **siguiente** |
| **E2.3** | Inmuebles que necesitan atención, con el embudo y dónde se frena | ⬜ |
| **E2.4** | Encargos por vencer y propietarios sin novedades | ⬜ |
| **E2.5** | Hallazgos: coincidencias nuevas con utilidad real | ⬜ |

**Abrir `localhost:4200/dashboard` y mirar** (E2.0 + E2.1, 2026-08-10):

- Arriba del todo, una franja oscura con una frase en vez de números sueltos:
  *"8 cosas necesitan tu atención · 4 operaciones abiertas · 2 alquileres
  firmados en los últimos 6 meses"*. Sin pendientes dice **"Nada te reclama
  ahora mismo"** y la franja lateral pasa de ámbar a verde.
- La línea económica **solo aparece si hay alquileres firmados**. No se insinúa
  renta ni comisión agregada: el resumen no las trae.
- En "Mi rendimiento", un periodo sin captaciones muestra **—** y *"No abriste
  captaciones en …, así que todavía no hay conversión que medir"*, en vez del
  porcentaje del agente que más cerró.

El titular lo suma el dominio (`pendientesDeAtencion`). No se puede derivar en
la pantalla sumando `senales`: `DEMORA_DE_SEGUIMIENTO` vale **días**, y colarla
diría "17 cosas" donde hay 8 pendientes y 9 días de atraso.

E2 **interpreta y presenta**; no vuelve a construir backend. `/dashboard`,
`/indicadores/resumen`, `/indicadores/avance` y `/seguimiento-comercial` ya
existen, están cortados y verificados.

---

## Qué gobierna, y qué no

El orden vigente sale **solo** de tres sitios:

| Documento | Responde |
|---|---|
| `mapa-ejecucion-brox.md` (este) | dónde estamos |
| `checklist-captura-moat-e-inteligencia-inmobiliaria.md` | qué falta para cerrar la etapa |
| `decision-*.md` (D-E…) | decisiones funcionales concretas |

Todo lo demás es **historia**. Los documentos del mundo legado —GlassFish,
Blazor, "contrato congelado", corte de la v1— llevan una marca
`HISTÓRICO — NO GOBIERNA EL ROADMAP ACTUAL` en su cabecera. Se conservan porque
explican por qué las cosas son como son; no porque digan qué hacer.
