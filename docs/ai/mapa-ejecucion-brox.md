# Mapa de ejecución BROX

**Esta es la portada del avance.** Si quieres saber dónde estamos, qué cerramos y
qué sigue, se responde aquí y en ninguna otra parte.

**Actualizado:** 2026-08-17

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

**Diseño congelado el 2026-08-11.** Antes de seguir picando subtandas se fijaron
dos decisiones, y **las subtandas E2.2–E2.5 dejan de ser cuatro bloques nuevos
para ser partes de una misma pantalla**:

| Decisión | Qué congela |
|---|---|
| `decision-inicio-foco-y-resolucion.md` (D-E2-1) | el **Inicio**: hasta 5 asuntos accionables + Radar BROX con dos modos, política de despacho, regla contra duplicados |
| `decision-indicadores-comerciales.md` (D-E2-2) | los **indicadores**: 4 KPI canónicos, ritmo contra meta, semáforo de 4 estados, lectura de agente vs broker |
| `traspaso-inicio-a-angular.md` | el **cable y los componentes** del Inicio: `GET /inicio`, árbol de componentes, y las pruebas que viajan |
| `estado-backend-para-el-inicio.md` | **qué hay en el backend, qué falta y en qué orden**: cinco tandas con su checklist |

| Subtanda | Qué se ve al terminar | Estado |
|---|---|---|
| **E2.0** | La conversión deja de tomar prestado el número de otro agente | ✅ |
| **E2.1** | Cabecera de decisión: "8 cosas necesitan tu atención · 4 operaciones abiertas" | ✅ |
| **E2.2** | La pelota y el orden: `DEPENDE_DE_MI` + `lado`/`paso` + política de 6 criterios | ✅ |
| **E2.3** | El hallazgo sale de la cola: mismo motor, otra salida | ✅ |
| **E2.4** | Capa de interpretación: `ComoEsta`, lectura y expediente de 4 renglones | ✅ |
| **E2.5** | El Radar del broker: sus propios asuntos + hallazgo de concentración | ✅ |
| **E2.6** | Contraste, pie y metas | ⬜ **siguiente** |

> **El orden cambió el 2026-08-11** tras inventariar el backend
> (`estado-backend-para-el-inicio.md`): el motor de coincidencias **ya existe**,
> así que el hallazgo se adelanta —cuesta poco y se nota mucho—, y
> `DEPENDE_DE_MI` va pegado a la política de despacho porque tocan el mismo
> comparador.

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

## La ruta a BROX 1.0

**Abierta el 2026-08-17.** BROX deja de ser un sistema de alquiler de locales y
pasa a ser un sistema inmobiliario: venta y alquiler, siete tipos de propiedad,
y KAIROS ejecutando los mismos casos de uso que la pantalla.

**El orden no es negociable en un punto:** *nada de E3 antes de congelar el
modelo universal*. La negociación necesita que el importe lleve unidad y base;
con «renta mensual» cocinada en el modelo, E3 nace torcida.

| # | Bloque | Qué entrega | Estado |
|---|---|---|---|
| **1a** | **Contrato universal** | `Propiedad × Operación` congelado, con gate ejecutable | ✅ **CERRADO** 2026-08-17 |
| **1b** | **Fundaciones del SPA** | Cortes 2+3 fusionados: sidebar, shell responsive, tokens, componentes | ⬜ **en paralelo, siguiente** |
| **2** | **Núcleo universal implementado** | PostGIS, titularidad múltiple, atributos gobernados, histórico por encargo, outbox | ✅ **CERRADO** 2026-08-18 |
| **3** | **Motor de registro** | `+ Registrar` y la máquina de preguntas en backend | ✅ **CERRADO** 2026-08-18 |
| **4** | **KAIROS funcional** | alta conversacional sobre el mismo motor | ⬜ **arranca aquí** |
| **5** | **Demanda + Matcher + E3** | requerimiento universal, criterios, negociación inmobiliaria | ⬜ |
| **6** | **Cierre de venta** | expediente de compraventa junto al de alquiler | ⬜ |
| **7** | **Inteligencia** | Foco, Radar, metas, ritmo (= E2.2–E2.6) + KAIROS ejecutor | ⬜ |
| **8** | **Migración del resto de pantallas** | las 57 al sistema normalizado | ⬜ |
| **9** | **Certificación 1.0** | venta/alquiler, tipos, roles, móvil, multi-tenant, seguridad, rendimiento, E2E | ⬜ |

### Lo que cerró el bloque 1a

| Documento | Qué congela |
|---|---|
| `decision-modelo-universal-propiedad-operacion.md` (D-E4-1) | el modelo: **la operación vive en el encargo, no en la propiedad** |
| `decision-motor-de-registro.md` (D-E4-2) | una máquina de preguntas derivada del catálogo, no siete formularios |
| `decision-autoridad-de-cada-dato.md` (D-E4-3) | **una sola autoridad por clave**: dónde vive de verdad cada dato, y quién lo lee |
| `decision-kairos-contrato-de-acciones.md` (D-K-1) | qué puede hacer KAIROS, con qué permisos y qué se confirma |
| `docs/ai/modelo/` | el contrato **como dato ejecutable** + su gate |

```bash
node docs/ai/modelo/gate-modelo-universal.js   # 160 comprobaciones
node docs/ai/modelo/motor-captura.js           # tres altas, incluida la de KAIROS
```

**D-E4-3 CERRADA el 2026-08-18**, los once pasos. Siete conceptos
vivían a la vez como columna de `propiedad` y como fila de `atributo_propiedad`,
con uno solo sincronizado. V60 declaró la autoridad, V61 consolidó los valores y
**V62 retiró las seis columnas espejo** mudando antes al catálogo los cuatro
CHECK de rango que el `DROP` se habría llevado en silencio.

La regla que deja escrita, y que no estaba: **si el escritor enruta por
autoridad, el lector también** — y lo hace la misma capa que conoce `destino`,
no cada caso de uso. Para el consumidor sigue existiendo `metraje_total = 90` sin
saber que salió de `propiedad.metraje`: la autoridad física cambia, el contrato
lógico no.

**Angular no necesitó ningún cambio**, y eso es el resultado, no un atajo: el SPA
nunca supo dónde vivía cada valor. Queda un gate que lo mantiene así — el SPA
puede conocer la clave lógica y el tipo de dato funcional, nunca la autoridad
física.

```
835 pruebas · 0 fallos · 0 SKIPPED (las 37 de integración ejecutadas de verdad)
evidencia: verificacion/evidencia/2026-08-18-d-e4-3-autoridad-del-dato.md
```

> **Y aquí se para.** Lo que esto habilita —ampliar a casa, departamento, terreno
> u oficina sin añadir una columna por tipo— es real, pero el trabajo vigente es
> **E2**. Una victoria técnica que se convierte en túnel deja de ser una victoria.

**El hallazgo que encogió el trabajo:** media casa ya estaba construida.
`propiedad` ya generaliza (V4), `captacion.motivo_operacion ∈ {A,V}` ya está
**validado en la entidad**, y la condición económica ya cuelga del encargo con
su unidad (V15). El contrato no inventa la operación: la nombra.

**Neo4j no entra como dependencia.** PostgreSQL sigue siendo la verdad; el grafo
será una proyección reconstruible. Lo único que hay que hacer ahora es el
outbox (`evento_dominio`), porque es imposible reconstruirlo después.

### Lo que lleva el bloque 2

**Las siete migraciones están aplicadas** (V46–V52, esquema en v52) y
verificadas: `backend-spring/verificacion/evidencia/2026-08-17-modelo-universal-v46-v52.md`.

```bash
docker exec controllocal-postgres-v2 \
    psql -U controllocal -d controllocal_dev -q -f /tmp/gate-modelo-universal.sql
#  en verde: 47 · en rojo: 0
```

13 de esas 47 comprobaciones **intentan romper el modelo** y exigen que la base
lo rechace — incluida la que decide todo: *admite venta y alquiler vivos sobre
la misma propiedad, y rechaza dos encargos vivos de la misma operación*.

| Hecho | |
|---|---|
| 21 titularidades creadas, ninguna propiedad sin titular | ✅ |
| 91 valores migrados a atributos gobernados | ✅ |
| 14 hitos de precio atados a su encargo | ✅ |
| `ddl-auto: validate` pasa y la API responde 200 en los cinco listados | ✅ |
| Nada borrado: las columnas del cable siguen en su sitio | ✅ |

**Una dependencia nueva de infraestructura:** el contenedor pasa a
`postgis/postgis:17-3.5-alpine`. Producción la va a necesitar, y el compose de
E2E también: V46 crea la extensión sobre una base vacía, así que sin ella
Flyway aborta antes de que la suite arranque.

### Lo que cerraron los bloques 2 y 3 — 2026-08-18

**Evidencia:** `backend-spring/verificacion/evidencia/2026-08-18-propiedad-universal-y-captura.md`.

El esquema pasó a ser **capacidad**: hay un caso de uso transaccional que
registra, lee y edita una propiedad por el modelo nuevo, y un motor de captura
que decide qué preguntar a partir del catálogo.

| | |
|---|---|
| 2.1 | `RegistrarPropiedad`: **una transacción, nueve efectos** (propiedad, ubicación, titulares, titularidad, atributos, encargo por operación, condición económica, primer hito `U`, evento). Todo o nada. |
| 2.2 | El ciclo cerrado: `POST /propiedades` → `GET` → `PUT` → `GET`. La misma información escrita por el modelo universal regresa por el modelo universal. |
| 2.3 | Gobierno del catálogo (V55): una organización **no puede** borrar, retipar ni **sombrear** un atributo común de BROX; lo suyo sí puede crearlo. |
| 2.4 | Idempotencia de comandos (V57): el mismo `Idempotency-Key` no crea una segunda propiedad — devuelve la del primer intento. |
| 3.1 | `service/captura`: qué se sabe, qué falta, qué se pregunta ahora y si ya hay suficiente. Lo consumen **Angular y KAIROS por igual**. |
| 3.2 | `BorradorCaptura` (V56): una captura empezada en un canal se termina en otro, y perder el contexto del modelo no cuesta los datos ya dictados. |

**Los dos pendientes técnicos se cerraron aquí, no en una etapa aparte:** el
simulacro de recuperación pasó a construir su propio tenant sin gobierno, y
**la operación inmobiliaria dejó de inferirse**. `= OPERACION_ALQUILER` y
`motivoOperacion = "A"` se retiraron de las entidades: hoy toda escritura
económica declara VENTA o ALQUILER, y si no se sabe, se declara **faltante**.

**El hallazgo del bloque (V58):** V50 creyó admitir venta y alquiler
simultáneos, pero dejó en pie `uq_captacion_activa_por_local`, que no distingue
operación. Con los dos encargos PENDIENTES funcionaba —y así se verificó—; al
aprobar el segundo, la base lo rechazaba. **El modelo universal se rompía justo
en el paso que lo hace útil.**

```bash
# 24 escenarios de aceptacion contra PostgreSQL real, en tenants propios
mvn -o test -pl controllocal-app -Dtest=PropiedadUniversalIntegrationTest
```

**Verificable:** una misma propiedad con encargo de venta a USD 180 000 y de
alquiler a USD 2 900, cada uno con **su** histórico; editar el precio de venta a
175 000 deja los dos hitos en orden; y repetir el mismo comando no duplica nada.

**Cierre verde el 2026-08-18** (`verificacion/Verificar-Cierre.ps1`): reactor de
**816 pruebas** con los 10 tests de integración comprobados como ejecutados, más
las 4 suites E2E — 65 + 41 + 125 + 18, **0 fallas**. Sin errores conocidos
arrastrados: el simulacro de recuperación, que era el único, se cerró aquí.

**Hicieron falta cuatro corridas, y las tres primeras encontraron trabajo real.**
Ninguno de los tres fallos era del modelo universal: dos eran **columnas
obligatorias sin productor** (V51 `solicitud_alquiler.tipo`, y
`captacion.motivo_operacion` en `POST /prospecciones/{id}/captar`) que el defecto
de la entidad venía tapando —quitarlo fue lo que las hizo visibles—, y el tercero
un barrido de tenancy que no conocía el catálogo híbrido de BROX. Los dos
primeros rompían caminos de uso diario y **el reactor no podía verlos**: los
tests de servicio simulan el repositorio, así que un campo sin rellenar nunca
llega a PostgreSQL. Es el argumento entero de por qué el cierre incluye E2E.

---

## En paralelo: normalización transversal del SPA

**Abierto el 2026-08-17.** Inicio e Indicadores dejaron de ser dos pantallas
bonitas dentro de un SPA disparejo y pasan a ser **la referencia de calidad**.
El programa son ocho cortes, y **no se abre uno sin cerrar el anterior**.

| Corte | Qué entrega | Estado |
|---|---|---|
| **1** | Auditoría transversal: inventario, shell, permisos, tokens, semántica y componentes | ✅ **CERRADO** 2026-08-17 |
| **2+3** | **Fusionados**: sidebar, shell responsive, tokens y componentes base de una vez | ⬜ **siguiente** |
| **4–7** | = bloques 4, 5 y 7 de la ruta a 1.0 | ⬜ |
| **8** | Migración del resto de pantallas | ⬜ |

**Los cortes 2 y 3 se fusionan** porque separarlos obliga a tocar las mismas 57
pantallas dos veces: los tokens sin componentes no se aplican solos, y los
componentes sin tokens nacen con hex dentro.

**Y los cortes 4–7 no son trabajo nuevo:** son las subtandas E2.2–E2.6 con su
mitad de frontend, ya reflejadas en la ruta a 1.0 de arriba.

Lo que salió del Corte 1, en tres documentos:

| Documento | Qué fija |
|---|---|
| `auditoria-ui-brox.md` | las 57 pantallas medidas, con veredicto por hallazgo |
| `mapa-pantalla-dominio-backend.md` | qué dato es hecho, cuál es interpretación y cuál deriva Angular sin permiso |
| `decision-sidebar-brox.md` (D-E3-1) | la navegación: de 24 entradas a 15, y ninguna que sea una cola |

**Los tres números que justifican el programa:** 60 colores fuera de token, 167
comparaciones de estado dentro de Angular y 51 migas de pan escritas a mano.

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
