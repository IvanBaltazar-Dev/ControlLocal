# Mapa de ejecución BROX

**Esta es la portada del avance.** Si quieres saber dónde estamos, qué cerramos y
qué sigue, se responde aquí y en ninguna otra parte.

**Y si lo que quieres saber es _hacia dónde_**, eso está en
[`north-star-brox.md`](north-star-brox.md): la North Star, las tres capacidades,
la métrica de dirección y los seis principios. Este mapa dice el siguiente paso;
aquél dice contra qué se mide si el paso fue un avance.

**Y si lo que quieres es la lista completa de lo que falta** —incluido lo que no
pertenece a ninguna etapa: producción, multi-tenancy, UI, verificación y estado
documental—, está en [`pendientes-brox.md`](pendientes-brox.md), medido contra
el repositorio y la base el 2026-08-22. Este mapa dice **el siguiente paso**;
aquél dice **todo lo que queda**.

**Actualizado:** 2026-08-22

---

## Dónde estamos

| Etapa | Estado | Lo que puedes comprobar |
|---|---|---|
| **E0** · Histórico económico | ✅ **CERRADA** | `U` inicial + backfill, hito `P` de renta publicada, decisión del hito `O` |
| **E1** · Instrumentación y políticas | ✅ **CERRADA** | política única, `senales[]` clasificadas, Angular sin umbrales |
| **E2** · Dashboard inmobiliario | ✅ **CERRADA** | el tablero es centro de decisión: foco, radar, interpretación, metas y ritmo |
| **E3** · Negociación | 🟡 **SIGUIENTE** | bloqueada por las tres cuestiones abiertas de E0 |
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

## Qué cerró E2 · Dashboard inmobiliario — 2026-08-19

El tablero informaba. E2 lo convirtió en **centro de decisión**: qué
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
| **E2.6** | Contraste, pie y metas | ✅ |

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
| **3b** | **El alta universal, visible** | `/propiedades/nueva` sirve a los siete tipos y a las dos operaciones; el listado deja de ser una tabla de locales | ✅ **CERRADO** 2026-08-20 |
| **3c** | **La ficha universal** | `/propiedades/:id` deja de leer el modelo heredado: la cosa física, un bloque **por encargo** con su histórico, la actividad con su procedencia y la **historia del inmueble** | ✅ **CERRADO** 2026-08-20 |
| **3d** | **La publicación por encargo** | `Propiedad → Encargo → Publicación`: API canónica, editor fuera del detalle heredado, y `features/local-detail/` borrado | ✅ **CERRADO** 2026-08-20 |
| **3g** | **El lenguaje del ENCARGO** | `V77`: el catálogo pasa de 6 a **26** condiciones comerciales y VENTA deja de estar muda (0 → 7). Aplicabilidad por **tipo × operación**, vocabularios sembrados, y el guard de pares hecho/condición ampliado a los ocho. Ninguna con valor por defecto: la ausencia sigue significando «todavía no se sabe» | ✅ **CERRADO** 2026-08-22 |
| **3f** | **El editor universal** | `/propiedades/:id/editar`: **una** ruta para los siete tipos sobre `PUT /propiedades/{id}`. Cuatro bloques —propiedad y ubicación, características del catálogo, titulares, un bloque **por encargo**— y un cuerpo que lleva **sólo lo tocado**: vaciar no viaja, borrar es «Quitar», la operación de un encargo no se cambia. El renderizador de campos pasa a ser compartido con el alta (`cl-campo-gobernado`) | ✅ **CERRADO** 2026-08-22 |
| **3h** | **El hecho llega donde llega su condición** | `V78`: las 19 claves de PROPIEDAD clasificadas una por una —14 hecho puro, 2 mitad de un par ya separado, 3 con problema de modelo, **ninguna condición disfrazada**—. Lo que sí apareció es la **cobertura del par**: `se_ofrece_amoblado` llegaba a OFICINA y `amoblado` no, y `mantenimiento_a_cargo_de` a ALMACÉN y CASA sin `cuota_mantenimiento`. Tres filas OPC lo cierran, y la invariante queda puesta | ✅ **CERRADO** 2026-08-22 |
| **3e** | **Profundidad inmobiliaria** | que el catálogo describa de verdad los siete tipos: vocabularios gobernados, atributos del encargo, identidad registral, unidades relacionadas | 🟡 **EN CURSO** — cerrados los tres cortes técnicos (0A `V71`, 0B `V72`, 0C `V73`+`V74`+`V77`) y el **Corte 1** en su mitad de sujeto (`V78`); queda la profundidad por tipo, cortes 1(resto)–7. Ver `auditoria-profundidad-inmobiliaria.md` |
| **4** | **KAIROS funcional** | alta conversacional sobre el mismo motor | ⬜ |
| **5** | **Demanda + Matcher + E3** | requerimiento universal, criterios, negociación inmobiliaria | ⬜ |
| **6** | **Cierre de venta** | expediente de compraventa junto al de alquiler | ⬜ |
| **7** | **Inteligencia** | Foco, Radar, metas, ritmo (= E2.2–E2.6) + KAIROS ejecutor | 🟡 **la mitad de E2**: falta KAIROS ejecutor |
| **8** | **Migración del resto de pantallas** | las 57 al sistema normalizado | ⬜ |
| **9** | **Certificación 1.0** | venta/alquiler, tipos, roles, móvil, multi-tenant, seguridad, rendimiento, E2E | ⬜ |

### El orden de ejecución, decidido el 2026-08-22

La numeración de los bloques dice **qué existe**, no en qué orden se avanza. Tras
la auditoría de avance del 2026-08-22 (26 recursos, 187 operaciones, 60 rutas —
y aun así tres suites E2E que no podían ni cargar su fixture), el orden es este,
y sale de una distinción que no hay que perder: hay diferencia entre *motor
técnicamente amplio*, *producto operable todos los días* y *plataforma lista
para ser la red del North Star*. Hoy estamos mucho más cerca del primero.

**AHORA — en este orden:**

1. ~~**Las 3 suites E2E que no cargaban su fixture**~~ ✅ 2026-08-22 —
   `demanda-busqueda`, `solicitudes-busqueda` y `v6-dos-organizaciones`
   insertaban en `captacion` sin `motivo_operacion` (V50 retiró el DEFAULT) y en
   `solicitud_alquiler` sin `tipo` (V51). No se cambió ninguna expectativa: los
   fixtures declaran ahora la operación, que es exactamente el contrato que el
   esquema pasó a exigir. Hasta este arreglo, el «cierre verde» del reactor no
   era baseline suficiente: había suites que ni arrancaban.
2. ~~**El editor universal**~~ ✅ 2026-08-22 — bloque **3f**. Era el hueco
   funcional más grave: `PUT /propiedades/{id}` existía y la SPA no lo llamaba
   desde ninguna pantalla. El editor **representa el contrato del Core**, no
   reconstruye la matriz tipo→campos; edita la propiedad como cosa física y
   **cada encargo por separado** (venta y alquiler simultáneos son dos encargos,
   jamás `AMBAS`); y conserva la regla del 0A: **bloque ausente = no tocar ese
   dominio**. Evidencia: `verificacion/evidencia/2026-08-22-editor-universal.md`.
3. ~~**V77 — el lenguaje completo del ENCARGO**~~ ✅ 2026-08-22. El catálogo
   conocía 6 de 26 condiciones y **VENTA no tenía ninguna propia**: un encargo
   de venta no podía decir si se entrega desocupado ni si el propietario acepta
   crédito. El mecanismo estaba entero desde 0C; faltaba el idioma. 26
   condiciones, 22 opciones de vocabulario, 112 filas de aplicabilidad por
   **tipo × operación**, el guard de pares semánticos ampliado a los ocho, y la
   prueba que da sentido al sujeto: dos alquileres sucesivos de la misma
   propiedad con condiciones contrarias, ninguna pisando a la otra. Evidencia:
   `verificacion/evidencia/2026-08-22-lenguaje-completo-del-encargo.md`.
4. ~~**Corte 1 / la pregunta del sujeto sobre las 19 claves**~~ ✅ 2026-08-22 —
   bloque **3h**, migración `V78`. La respuesta, medida y no opinada:
   **ninguna de las 19 era una condición disfrazada** — 0C, `V74` y `V77` ya
   habían sacado del sujeto PROPIEDAD todo lo que se negocia. Lo que sí seguía
   abierto era la mitad de la regla del par que nadie miraba: **un hecho puede
   llegar menos lejos que su condición**, y donde eso pasa el pacto es la única
   casilla donde cabe el hecho. Tres huecos exactos, tres filas OPC, cero
   valores afectados, y la comprobación puesta para que no vuelva. Las
   conversiones de tipo (`cuota_mantenimiento`→IMPORTE, `rubro_permitido`→
   LISTA_MULTIPLE, `zonificacion`→LISTA, `banos`→ENTERO) **siguen bloqueadas
   por `tg_catalogo_sistema_inmutable`**, que es una invariante deliberada y no
   un obstáculo a rodear. Evidencia:
   `verificacion/evidencia/2026-08-22-corte-1-el-hecho-y-su-condicion.md`.

5. **Corte 1 (resto) / profundidad de la PROPIEDAD** — lo que queda del corte no
   es sujeto, es **profundidad**: ampliar aplicabilidad por tipo (`banos` a
   L,O,A · `zonificacion` a O · `pisos_edificacion` a D,O · `frente` a C) y
   darle vocabulario a `servicios_disponibles`, que hoy es una LISTA muda. Ya
   está medido en `auditoria-profundidad-inmobiliaria.md`; lo que falta es
   decidir la exigencia y los vocabularios, que son decisiones de negocio.

**ANTES DE PRODUCCIÓN** (en paralelo, es trabajo de otra naturaleza y no debe
contaminar el modelo inmobiliario): separar seeds de desarrollo, bootstrap
inicial de una organización real, **rotar el JWT y las credenciales publicadas**
(siguen pendientes desde `2832a9b`), configuración fuera de `localhost`, imagen
productiva, TLS/proxy y restauración automatizada. Nada se despliega en público
antes de esto.

**ANTES DE ABRIR A VARIAS INMOBILIARIAS:** multi-tenancy real. Para un piloto
interno de una organización puede esperar semanas; para decir «en el mercado»,
no puede esperar meses. Una organización codificada `BROX_LEGACY` contradice la
North Star —comunidades progresivamente más densas— y la propia matriz, que fija
el tenant como frontera anterior incluso al rol.

**DESPUÉS:** demanda/matcher/colaboración, y KAIROS **sobre contratos ya
estables**.

**Dos correcciones al roadmap que esta decisión deja escritas:**

- **KAIROS no se adelanta por ser el bloque 4.** Es otro cliente de BROX Core y
  todas sus herramientas corresponden a casos de uso reales existentes (D-K-1).
  Hoy su primera llamada depende de rutas que no existen o no encajan —el
  handshake de `/capacidades` incluido—, así que lo primero es **reconciliar el
  adaptador con el Core**, no construir WhatsApp, LLM, voz ni memoria alrededor
  de una integración que no completa el primer saludo.
- **BROX Network deja de ser «algún día».** No una red social completa: las
  **primitivas de la primera etapa** del North Star — organización real,
  profesional real, requerimiento vigente, oferta vigente, coincidencia
  explicable, colaboración atribuible. Son exactamente las condiciones que el
  documento estratégico define como lo que la primera etapa debe demostrar.

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
| 2.1 | `RegistrarPropiedad`: **una transacción**. Con operaciones declaradas, nueve efectos (propiedad, ubicación, titulares, titularidad, atributos, encargo por operación, condición económica, primer hito `U`, evento). **Con cero operaciones, cinco**; y **sin titular conocido, cuatro** (V76): la propiedad queda registrada, no ofrecida y declarando cómo llegó a conocerse. Todo o nada. |
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

### Lo que cerró el bloque 3b — 2026-08-20

**Evidencia:** `backend-spring/verificacion/evidencia/2026-08-20-alta-y-listado-universales.md`.

La capacidad existía desde el bloque 3 y **el producto seguía comportándose como
un sistema de alquiler de locales**: `/propiedades/nueva` cargaba el formulario
de local comercial con la operación fijada en `ALQUILER`.

| | |
|---|---|
| 3b.1 | `decision-frontera-brox-core-web-kairos.md` (**D-A-1**): los cuatro nombres congelados y las dos reglas finales, con tres gates que las protegen |
| 3b.2 | El borrador admite **dos operaciones**: `operaciones = "VENTA,ALQUILER"` y claves económicas calificadas (`importe:VENTA`). `deLaOperacion` pasa a ser una lista de **bloques** |
| 3b.3 | `GET /captura/apertura`: qué hay que decidir antes de que exista un plan, para que ninguna interfaz escriba «primero el tipo, luego la operación» |
| 3b.4 | `PropiedadForm`: **una** pantalla para los siete tipos, que no sabe qué se pregunta a cada uno — lo pide |
| 3b.5 | `GET /propiedades` + `Propiedades`: una fila por propiedad con **sus encargos dentro**; «Venta + alquiler» se compone al pintar y se filtra con dos EXISTS |
| 3b.6 | **V67** una sola autoridad para el piso · **V68** los rótulos del catálogo se leen |

**Los tres defectos que encontró, y ninguno era del alta:** `Idempotency-Key` y
`X-Elevacion` no pasaban CORS —así que **ningún comando idempotente del SPA
había funcionado nunca en un navegador**, y el spec no podía verlo porque
`HttpTestingController` no cruza CORS—; el piso se preguntaba dos veces porque
tenía dos claves para un concepto; y un código de prueba chocaba consigo mismo
tras suficientes corridas.

```
860 pruebas backend · 0 fallos · 0 SKIPPED    644 pruebas Angular · 0 fallos
```

### Lo que cerró el bloque 3c — 2026-08-20

**Evidencia:**
`backend-spring/verificacion/evidencia/2026-08-20-ficha-universal-corte-a-matriz-contrato.md`.

`/propiedades/:id` ya **no toca `GET /locales/{id}`**. La ficha separa tres
conceptos que no se vuelven a mezclar —la cosa física, un bloque **por
encargo** con su precio y su histórico, y la actividad— y añade un cuarto
nivel de lectura: **la historia del inmueble**.

**Se midió el contrato antes de tocar Angular**, y el resultado no fue el
esperado: nueve huecos. La ficha publicaba `tipoPropiedad = "L"` —el código de
almacenamiento, mientras el listado publicaba `LOCAL` y «Local comercial»—, el
encargo no decía su agente ni cómo se llama su importe, `exclusividad` se
calculaba y se perdía en el DTO, la actividad no existía, y **la ficha escondía
los encargos cerrados**, borrando de la vista series económicas enteras.

Los nueve se cerraron **en BROX Core**, y ocho consistían en publicar algo que
el dominio ya sabía decir y se quedaba dentro (`nombreDelImporte()`,
`rotuloDelTipo()`, `descripcion()` de los enums).

**Los dos niveles de lectura**, que es la decisión de fondo:

```
idEncargo    la identidad técnica de UN episodio comercial
idPropiedad  la continuidad histórica del inmueble
```

El bloque de encargo audita y negocia; la historia contesta «¿a cuánto se
alquiló la última vez?», «¿cuántas veces estuvo en venta?». **No fusiona
históricos: los agrega para leerlos**, y cada cifra sigue apuntando a su
`idEncargo`.

La prueba que sostiene el diseño **no es venta + alquiler** —con un encargo de
cada, un `groupBy(operacion)` incorrecto pasa— sino **tres alquileres
sucesivos**: tres bloques, tres históricos, y ninguno ve la cifra de otro. El
histórico se filtra por encargo, no por operación.

```
231 pruebas backend · 0 fallos · 0 SKIPPED    660 pruebas Angular · 0 fallos
```

`features/locales/` **borrado** tras demostrar cero consumidores.

### Lo que cerró el bloque 3d — 2026-08-20

La deuda que 3c dejó abierta, cerrada en el mismo día y por su raíz: **una
publicación anuncia un ENCARGO** — esta propiedad, en esta operación, a este
precio— y no una propiedad.

**V70** le da a `publicacion` su `id_captacion` y renombra `renta_publicada` a
`importe_publicado` (en una publicación de venta, «renta» era sencillamente
falso, y el nombre viajaba hasta la pantalla). El API canónico es
`/encargos/{idEncargo}/publicaciones`; **los cuatro endpoints heredados se
retiraron**, porque su último consumidor —`oportunidad-form`— pasó a preguntar
por su encargo, que además es lo que de verdad quería.

**El hallazgo que lo justificó** estaba escrito por alguien en el propio
código, junto a un `setOperacion(ALQUILER)` fijo: *«la publicación de una venta
llegará con el encargo de venta y su propio importe, y entonces esta línea
dejará de ser una constante»*. Era ahora. Y tenía una consecuencia que nadie
veía: los hitos `P` nacían **sin encargo**, así que existían en la base y **no
aparecían en ninguna ficha**, que filtra por encargo.

`features/local-detail/` **borrado**: su única pieza propia, el editor de
publicaciones, vive ahora en `shared/publicaciones/` y cuelga del encargo.

```
723 + 43 + 241 pruebas backend · 0 fallos · 0 SKIPPED    653 Angular · 0 fallos
```

Con un gate nuevo, `FichaUniversalNoVuelveAlModeloViejoTest`, que rompe el
build si la ficha vuelve a `/locales/{id}`, importa `local-detail`, deja de
pintar `tipoRotulo` o agrupa encargos y anuncios por operación en vez de por
`idEncargo`.

### Lo que midió el bloque 3e, y por qué no se empezó — 2026-08-20

**`docs/ai/auditoria-profundidad-inmobiliaria.md`** (diez auditorías en
paralelo, de sólo lectura).

El veredicto es que **hoy BROX no describe bien ninguno de los siete tipos**:
el catálogo entero son 19 claves y sólo cuatro filas están marcadas requeridas.
Un departamento puede quedar descrito por «90 m², 3 dormitorios».

> **Y no se arregla añadiendo filas.** La medición encontró dos bloqueos
> estructurales:
>
> 1. **El catálogo no sabe declarar un vocabulario.** `tipo_dato='LISTA'`
>    existe pero no hay dónde guardar sus opciones —ni columna ni tabla—, y el
>    motor pasa `opciones=null` para todo atributo de catálogo. Comprobable:
>    `servicios_disponibles`, la única LISTA sembrada, viaja hoy como texto
>    libre. Sin esto, la tipología de departamento (flat, dúplex, penthouse) y
>    otras ~14 listas **no tienen dónde vivir**, y escribirlas en Angular es lo
>    que el gate de D-A-1 rompe.
> 2. **El ENCARGO no es sujeto de ningún atributo.** `catalogo_atributo` sólo
>    se mapea contra `tipo_propiedad` y `atributo_propiedad` cuelga de
>    `id_propiedad`: por construcción, todo atributo gobernado es un hecho de
>    la PROPIEDAD. Por eso «el propietario acepta mascotas en este alquiler» y
>    «se ofrece amoblado» no tienen dónde escribirse — y por eso `amoblado`
>    guarda hoy como hecho físico permanente algo que se negocia en cada
>    alquiler.

Hay un tercer hallazgo que **no es de catálogo y corrompe datos hoy**: el único
editor del SPA (`local-form`, montado en `propiedades/:id/editar` para los siete
tipos) rechaza cinco de ellos, **inventa `rubro_permitido`** y **aplasta `uso` a
`'C'`** al guardar. `PUT /propiedades/{id}` existe, está en la matriz, y el SPA
no lo llama desde ninguna parte.

El plan propone diez cortes, y los dos primeros son técnicos a propósito — **en
este orden**:

```
0A  contener la corrupción de edición   ✅ CERRADO 2026-08-20 · V71
0B  el catálogo aprende a hablar        ✅ CERRADO 2026-08-21 · V72
0C  declarar el sujeto del dato         ✅ CERRADO 2026-08-21 · V73 + V74
    └ convergencia: registrar no es encargar   ✅ 2026-08-21 · V75
0D  la propiedad como activo de dato    ✅ CERRADO 2026-08-21 · V76
0E  el lenguaje completo del ENCARGO    ✅ CERRADO 2026-08-22 · V77
1   las 19 claves — mitad de SUJETO     ✅ CERRADO 2026-08-22 · V78
1   las 19 claves — mitad de PROFUNDIDAD ⬜ siguiente
2…7 profundidad por tipo                ⬜
```

> **El Corte 1 se partió en dos, y no por comodidad.** Su mitad de **sujeto**
> —¿cada clave describe al inmueble o al encargo?— se puede contestar con
> evidencia y cerrarse; su mitad de **profundidad** —¿a qué tipos aplica y con
> qué exigencia?— depende de decisiones de negocio que nadie ha tomado y de
> vocabularios que no existen. Mezclarlas habría hecho imposible decir qué
> corrigió qué.

**V75 no estaba en el plan y salió de la corrida de cierre de 0C.** El alta
exigía al menos una operación, así que toda propiedad nacía con un encargo vivo
— y el embudo dice `propietario → prospección → encargo`, de modo que el encargo
no puede tener que existir antes de prospectar. `POST /propiedades` acepta ahora
cero operaciones y el Encargo nace al captar, con su operación declarada.

**V76 termina esa misma frase.** Quitada la operación obligatoria quedaba la
otra atadura del alta: el titular. Se podía registrar un inmueble sin encargo,
pero no sin dueño conocido — y BROX conoce legítimamente inmuebles que no
gestiona, así que la única forma de anotar uno era **inventar un propietario**.
La exigencia se mudó del registro al encargo (`TitularParaEncargar`, por el que
pasan los tres caminos que abren captación), la propiedad declara **cómo llegó a
conocerse** (`origen_incorporacion`) y lo que se ve del mercado vive en
`observacion_mercado`: una serie *append-only*, separada del histórico del
encargo, que no escribe precio, ni disponibilidad, ni publicación.

> Una Propiedad representa un inmueble conocido por BROX, no necesariamente una
> oferta gestionada por BROX. Su existencia, procedencia e historia observada
> son independientes de Prospecciones y Encargos. Los hechos comerciales solo
> nacen cuando existe la relación comercial que los autoriza.
>
> BROX nunca convierte una observación de mercado en un hecho comercial ni
> inventa una relación para poder conservar conocimiento.

Los tres cortes técnicos están cerrados, cada uno con su gate y su evidencia en
`backend-spring/verificacion/evidencia/`. Lo que queda es siembra: filas de
catálogo, tipo por tipo, sobre un mecanismo que ya sabe declarar vocabulario,
exigencia y **de quién es cada dato**.

**0A va delante de 0B**, y esto se corrigió después de escribir el plan: mientras
editar pueda destruir, añadir capacidades inmobiliarias es ampliar la superficie
de lo que se puede perder. El gate de 0A es el de D-E4-3 un paso más allá —
*leer → abrir el editor → no tocar ese dato → guardar → releer = idéntico*—, y
por los siete tipos, no por un departamento feliz.

La cadena de migraciones arrancó en **V71** —V70 ya era la publicación por
encargo— y va por **V76**. El Corte 1 empieza en V77.

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
