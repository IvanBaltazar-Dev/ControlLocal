# Pendientes de BROX — inventario completo

**Qué responde:** todo lo que queda por hacer, medido contra el repositorio y la
base de datos reales, no contra lo que los documentos dicen que falta.

**Hecho el 2026-08-22**, justo después de cerrar V78 (Corte 1, mitad de sujeto).
Estado del árbol: rama `feat/modelo-universal-y-autoridad-del-dato`, commit
`48e8ede`, migraciones hasta **V78**.

**Cómo se hizo:** recorriendo `docs/ai/` (66 documentos), `backend-spring/`,
`frontend-angular/src/`, `backend-spring/verificacion/` y las dos bases
PostgreSQL vivas. Cada fila que dice «abierto» se comprobó; las que los
documentos daban por pendientes y **ya estaban hechas** están marcadas como
tales y se corrigieron en su documento de origen.

**Este documento no sustituye a nadie.** `mapa-ejecucion-brox.md` sigue siendo
la portada («dónde estamos») y
`checklist-captura-moat-e-inteligencia-inmobiliaria.md` los requisitos de cierre
de la etapa en curso. Esto es el **inventario transversal**: lo que ninguno de
los dos recoge entero porque está repartido en quince documentos.

---

## 0. Lo primero, porque no es técnico y bloquea todo lo demás

| # | Pendiente | Medido | Por qué importa |
|---|---|---|---|
| **0.1** | **43 commits sin publicar.** La rama no tiene *upstream* y `origin/main` sigue en `2832a9b` | `git rev-parse @{u}` → *no upstream*; `git log main..HEAD` → 43 | Todo el trabajo desde la migración vive **sólo en este disco**. Un fallo de la máquina lo pierde entero — incluidos V71…V78 y las 22 suites de integración |
| **0.2** | **Rotar el secreto JWT y las credenciales RDS publicadas** en `2832a9b` | `origin/main` = `2832a9b` | El commit que las publicó **es la cabeza de `main` en GitHub**. Y `backend-spring` reutiliza ese mismo secreto de firma. Sin GlassFish al que preservar compatibilidad, rotarlo es un cambio de configuración |
| **0.3** | **Decidir qué pasa con `origin/main`** | — | La historia pública se quedó en la v1. O se fusiona lo nuevo, o se declara que `main` no representa el producto |

> **0.1 y 0.2 están relacionados y el orden importa**: publicar la rama sin
> rotar antes no empeora nada (el secreto ya está publicado desde `2832a9b`),
> pero rotar y no publicar deja la rotación también en un solo disco.

---

## 1. Lo que bloquea la etapa en curso — E3 · Negociación

E3 es la única etapa 🟡 SIGUIENTE y está **bloqueada por tres decisiones de E0**
que hay que tomar **antes** de escribir la primera fila de oferta, porque las
tres cambian el dato que se persiste (`decision-hito-oferta-de-demanda.md` §«Dos
cuestiones abiertas»).

| # | Cuestión | Propuesta que ya existe en el documento | Estado |
|---|---|---|---|
| **1.1** | ¿Contra qué precio pedido se congela el *snapshot* de la oferta: `U` (lo autorizado, privado) o `P` (lo que el mercado vio)? | último `P` vigente, con caída a `U` si no hay anuncio, **dejando constancia de cuál se usó** | ⬜ sin decidir |
| **1.2** | ¿Dónde vive `O` sin mezclar dos negociaciones del mismo inmueble? `PrecioPropiedad` cuelga de la **propiedad**, y con dos interesados la serie interleava dos mesas distintas | referencia a la **oportunidad** | ⬜ sin decidir |
| **1.3** | Dónde consta lo declarado (menor) | — | ⬜ sin decidir |

**Además, y es nuevo desde que se escribió esa decisión:** el histórico
económico tampoco sabe **de qué encargo** es. Con venta y alquiler vivos a la
vez sobre la misma propiedad, las dos series se mezclan. Está anotado en
`decision-modelo-universal-propiedad-operacion.md` §2 como una de las seis cosas
que faltaban de verdad, y sigue abierto.

---

## 2. Profundidad inmobiliaria — lo que queda de los cortes de catálogo

Fuente: `auditoria-profundidad-inmobiliaria.md`. La cadena real de migraciones y
lo que ocupó cada una está en su §6. **La siguiente libre es `V79`.**

### 2.1 Corte 1 · mitad de PROFUNDIDAD ⬜ — lo inmediato

V78 cerró la mitad de **sujeto** (¿de quién es cada clave?). Queda la mitad de
**profundidad** (¿a qué tipos aplica y con qué exigencia?), que está medida e
inerte y sólo espera decisiones de negocio:

| Cambio | Medido | Qué falta decidir |
|---|---|---|
| `banos` → L, O, A | 406 valores, todos en C y D; cero en L, O, A | la exigencia |
| `zonificacion` → O | 584 valores en A, C, L, T; las 72 oficinas sin ninguno | ídem |
| `pisos_edificacion` → D, O | la clave más estrecha: **una sola fila**, en C | ídem |
| `frente` → C | 475 valores en A, L, T; ninguna casa | ídem |
| `interiorUnidad` / `nombreEdificioGaleria` → A | no son claves de catálogo: dos `Set.of` en `GuionRegistroPropiedad`, sin migración | ídem |

> **El flip a PUB es el cambio de mayor impacto operativo del corte, y está
> cuantificado**: `pisos_edificacion` faltaría en 1 048 de 1 048 departamentos,
> `banos` en 781 D / 407 L / 72 O / 83 A, `cuota_mantenimiento` en 806 D / 139 C
> / 97 L / 83 A. En `dev` prácticamente ningún local volvería a ser publicable.
> **O se escalona, o se acepta y se dice.** Hoy **ninguna** de las 19 claves
> tiene exigencia PUB.

### 2.2 Las conversiones de tipo, bloqueadas por una invariante deliberada

`tg_catalogo_sistema_inmutable` **prohíbe cambiar el `tipo_dato` de una clave
del sistema** («los valores ya escritos dejarían de significar lo mismo»). No es
un obstáculo a rodear: es la garantía. Las cuatro necesitan **otra** vía —clave
nueva + migración de datos + retirada de la vieja—, y cada una tiene además su
propio bloqueo de dato:

| Conversión | Bloqueo adicional | Dónde va |
|---|---|---|
| `cuota_mantenimiento` DECIMAL → **IMPORTE** | 784 filas con `valor_moneda` NULL al 100 %, y **ninguna fuente de la que deducirla**: `moneda_referencial` es la moneda de una renta, no la de un gasto de junta; el mismo importe 350 aparece bajo PEN (237 veces) y USD (56); el encargo vivo tiene 74 casos con monedas en conflicto | cuando la moneda se **declare** |
| `rubro_permitido` TEXTO → **LISTA_MULTIPLE** | 22 valores libres distintos, varios no mapeables con certeza; y cambia el almacén (los valores pasan a `atributo_propiedad_opcion`) | Corte 3/4, con vocabulario reconciliado |
| `zonificacion` TEXTO → **LISTA** | los 584 valores mapearían al 100 %; **el problema es el vocabulario nuevo**: derivarlo de lo observado daría 4 opciones y Lima tiene decenas (CV, CM, CE, RDB, I1–I4, OU, ZRP, ZTE…) | cuando salga de los planos de zonificación |
| `banos` DECIMAL → **ENTERO** + `medios_banos` | `medios_banos` es clave nueva; 379 de 406 valores son `.5` | **Corte 3** |

### 2.3 `servicios_disponibles` — una LISTA muda

`tipo_dato='LISTA'` con **cero opciones sembradas**. El trigger sólo valida
pertenencia *si la clave tiene vocabulario*, así que acepta cualquier cadena; y
`MotorDeCaptura.controlDe` deriva el control de si hay opciones, así que la
dibuja como **texto libre**. El dato entra y no compara con nada.

- **No se le inventa vocabulario** ni se le cambia el tipo: es hecho de la
  PROPIEDAD y está bien colocado.
- Sus reemplazos (`agua_desague`, `energia_electrica`, `gas`, cada uno con su
  tercer estado «con factibilidad aprobada») **nacen en el Corte 5**, y sólo
  entonces pasa a `activo = false`. Retirarla antes dejaría varios cortes en los
  que BROX deja de capturar un hecho que hoy captura.
- **Y falta su guarda gemela**: la comprobación «ninguna LISTA sin vocabulario»
  que V77 escribió sólo mira `sujeto = 'ENCARGO'`. La PROPIEDAD no la tiene — por
  eso esta clave sobrevivió muda. Extenderla exige antes darle vocabulario, así
  que **van en la misma tanda**.

### 2.4 Los cuatro hechos que faltan de un par deliberado

El guard de pares vigila que un hecho y su condición no compartan sujeto, y V78
añadió que el hecho no llegue menos lejos que su condición. **Cuatro pares tienen
hoy la condición y no el hecho**, así que el pacto es el único sitio donde cabe
la verdad física:

| Hecho que falta | Su condición, que ya existe | Corte |
|---|---|---|
| `mascotas_reglamento` (lo que permite el reglamento del edificio) | `mascotas_aceptadas` | 3 |
| `nivel_implementacion` | `se_entrega_implementado` | 4 |
| `estado_ocupacion` | `entrega_desocupado` | 5 |
| `lote_minimo_normativo` | `acepta_venta_fraccionada` | 5 |

> Que falte el lado PROPIEDAD **no impedía sembrar el lado ENCARGO** —la
> condición es cierta por sí sola— y por eso se hizo. Lo que hay que recordar al
> construirlos: **tienen que nacer cubriendo la aplicabilidad de su condición**,
> o el gate de V78 lo dirá.

### 2.5 Cortes 2 a 7 ⬜

| Corte | Qué trae | Migración |
|---|---|---|
| **2 · Identidad registral** | `partida_registral` y `oficina_registral` como **estructurales**, más `independizado`, `cargas_gravamenes`, `area_segun_partida`, `declaratoria_fabrica`. Hoy la partida existe **una sola vez** en toda la base: `condicion_compraventa.partida_registral`, colgada de una solicitud de venta — un inmueble que nunca se puso en venta no tiene partida en ningún sitio | **V79+** |
| **3 · Vivienda (D, C)** | tipología, conservación, ascensores, vigilancia, áreas comunes, vista, bloque de baños/servicio, áreas exteriores. **Hereda**: `medios_banos` y el estrechamiento de `banos` | — |
| **4 · Comercial (L, O, A)** | `tipo_acceso`, `clase_edificio`, `nivel_implementacion`, `metraje_arrendable`, `aforo_itse`, `certificado_itse`, bloque logístico | — |
| **5 · Terreno (T)** | parámetros urbanísticos, servicios con su tercer estado, vía y ocupación. **Hereda**: los tres reemplazos de `servicios_disponibles` | — |
| **6 · Unidades relacionadas** | una unidad con partida propia **es una Propiedad relacionada**, no un escalar dentro de un EAV: `unidad_relacionada`, códigos `E`/`B`, `unidadesRelacionadas[]` | — |
| **7 · Demanda y matcher** | unificar el vocabulario de tipo (hoy ALMACÉN y `DEPOSITO_ALMACEN` —el mismo concepto— se declaran no comparables), permitir que un requerimiento pida atributos gobernados, y **arreglar el sesgo**: un dato faltante hace que el criterio NO APLIQUE sin castigar el puntaje, así que **la propiedad peor capturada obtiene mejor puntaje** | — |

### 2.6 Y una decisión que el Corte 1 dejó explícitamente sin tomar

**El tipo `X` (OTRO) se está quedando sin preguntas.** Hoy tiene exactamente
tres claves aplicables y las tres son de `aplica_todos`; quitar dos lo dejaría
con `metraje_total`. El plan dice «no abrir X, auditarlo antes de decidir si
sigue existiendo», y ningún corte lo ha auditado. **Que sea una decisión, no un
efecto colateral.**

---

## 3. Modelo universal — lo que `decision-modelo-universal-propiedad-operacion.md` daba por faltante

De las seis cosas que §2 de esa decisión declaraba pendientes:

| | Estado hoy |
|---|---|
| Multi-titular (`propiedad.id_rol_propietario` 1:1 `NOT NULL`) | ✅ resuelto — `titularidad_propiedad`, y la columna admite NULL desde V76 |
| Atributos gobernados (era una tabla por subtipo) | ✅ resuelto — `detalle_local_comercial` retirada en V71 |
| **El histórico económico no sabe de qué encargo es** | ⬜ **abierto** — bloquea E3 (§1) |
| **El requerimiento habla alquiler comercial** (`renta_min/max`, `rubro` obligatorio, un solo `tipo_inmueble`) | ⬜ **abierto** — Corte 7 |
| **La compraventa no tiene expediente** | ⬜ **abierto** — bloque 6 del mapa |
| PostGIS y outbox de eventos | ✅ resuelto — `propiedad.ubicacion geography(Point,4326)` y el outbox existen |

---

## 4. Interfaz — lo que queda del corte de UI

Fuente: `auditoria-ui-brox.md` (medición del 2026-08-17) y
`auditoria-residuos-semanticos.md`, **re-medidos hoy**.

### 4.1 Ya hecho, y el documento no lo decía

- `estadoRitmo` y la clasificación por asunto (E2.6), `DEPENDE_DE_MI` (E2.2) y
  la cola del broker (E2.5): tres de los cinco `BACKEND_FALTANTE`.
- El literal `Panel` del cascarón y la pantalla «Catálogos del sistema»:
  retirados.
- El menú: Locales→Propiedades, Dashboard→Inicio.
- `local-form` y `local-detail`: **borrados**; el alta y el editor universales
  los sustituyen y salen del catálogo.

### 4.2 Abierto, medido hoy

| # | Pendiente | Medida de hoy |
|---|---|---|
| **4.1** | **`BroxPageHeader` no existe**: cada pantalla se pinta su propia miga de pan | **50** ficheros con `class="miga"` |
| **4.2** | **«ControlLocal» sigue visible al usuario** | **6** plantillas (login, recuperar-acceso, cambiar-contrasena, enrolar-mfa, agente-form, broker-form) + `<title>ControllocalWeb</title>` en `index.html` |
| **4.3** | **«Cierres exitosos» no se renombró a «Contratos»** | `propiedades-alquiladas.html`, título y miga |
| **4.4** | **`locales.service.ts` sigue vivo** y con él el modelo plano de L/O | lo usan 4 pantallas: `ficha-propiedad`, `captacion-detail`, `captacion-form`, `captacion-review` |
| **4.5** | **`ficha-propiedad`** (la de `captaciones/:codigo/ficha`) lee el modelo heredado, no el universal | `import ... from '../../core/api/locales.service'` |
| **4.6** | **`GET /inicio` compuesto** no existe: el Inicio se arma con varias llamadas | no hay `InicioController` |
| **4.7** | **Capacidades por sesión** no existen | sin `capacidades` en `web/` |
| **4.8** | Las dos uniones: **Interacciones** dentro del expediente · **Reportes** como pestaña de Indicadores | mantienen el menú en 15/17/19 en vez de 13/15/16 |
| **4.9** | Unificar tabla de bandeja, filtros, badges, timeline, KPI y tokens; `agente-form`+`broker-form`; las 4 parejas duplicadas | 13 veredictos `UNIFICAR` |
| **4.10** | Rediseñar estado activo del menú, iconografía, **móvil (drawer)** | 5 veredictos `REDISEÑAR` |
| **4.11** | Subtítulo del login («Gestión comercial de locales») y patrón de progreso por pasos en el resto de altas | deuda menor |

### 4.3 Y lo que la auditoría de UI declaró **no auditado**

Sigue sin auditarse, y conviene no confundirlo con «está bien»:

- **Accesibilidad** — ni contraste real, ni foco, ni lectores de pantalla.
- **Rendimiento percibido** — no se ha medido pintado ni tamaño de *bundle*.
- **Glosario de textos** — el vocabulario funcional único **no está escrito**.
- **Las pantallas de seguridad** (`seguridad`, `enrolar-mfa`, `perfil`) —
  inventariadas, flujo sin revisar.

---

## 5. Multi-tenancy — el diseño está cerrado, la ejecución no

`arquitectura-multitenancy-colaboracion.md` §12 declara **todas las preguntas
resueltas** (D-18 a D-26). Lo que queda es implementación:

| # | Pendiente | Medido hoy |
|---|---|---|
| **5.1** | **RLS no está activado en ninguna tabla.** D-24 dice activarlo **antes del segundo tenant** | `select count(*) from pg_class where relrowsecurity` → **0** |
| **5.2** | `BROX_LEGACY` sigue siendo el único tenant real | 4 organizaciones: `BROX_LEGACY` (26 propiedades) + 3 de prueba |
| **5.3** | Separar cuenta de acceso de persona (D-22): `cuenta_acceso` global | `usuario_organizacion` existe; `cuenta_acceso` **no** |
| **5.4** | `canal_whatsapp` (D-21) y la bóveda de identidad de red (D-18) | no existen |
| **5.5** | F0/Locales/F2 se construyeron sin contexto de organización → añadir el contexto explícito de D-20 | — |

> **Y un residuo:** en `controllocal_dev` viven tres tenants de prueba
> (`E2E-UNIVERSAL-A`, `E2E-UNIVERSAL-B`, `SIMULACRO-RECUPERACION`) con 6
> `persona_rol` y cero propiedades. `AislamientoDePruebasTest` impide que eso se
> repita, pero lo que ya entró sigue ahí.

---

## 6. Producción — nada se despliega en público antes de esto

### 6.1 Configuración e identidad

| # | Pendiente |
|---|---|
| **6.1** | Rotar JWT y credenciales (§0.2) |
| **6.2** | Separar los *seeds* de desarrollo de los datos reales. `ValidadorConfiguracionSeguridad` **se niega a arrancar `prod`** mientras alguna credencial sembrada siga viva — está bien que así sea, pero significa que hoy `prod` no arranca |
| **6.3** | *Bootstrap* inicial de una organización real (hoy sólo existe la de legado) |
| **6.4** | Configuración fuera de `localhost`, imagen productiva, TLS/proxy |

### 6.2 Respaldo y almacén (bloque 9)

De `backend-spring/operacion/README.md` §8, y ninguno bloquea a S0:

| # | Pendiente | Por qué importa |
|---|---|---|
| **6.5** | **Copia de los binarios del almacén** | `pg_dump` guarda las claves, no los archivos: una restauración deja la base íntegra y **los documentos ausentes** |
| **6.6** | **Copia fuera de la máquina** | hoy el destino por defecto es el mismo disco |
| **6.7** | **Cifrado del respaldo en reposo** | el *dump* lleva datos personales en claro |
| **6.8** | **Alerta cuando el respaldo falla** | un *backup* roto desde hace semanas parece uno que funciona |
| **6.9** | **Elegir el servidor S3** y migrar los binarios antes de cambiar el proveedor por defecto | los archivos viven en el volumen `controllocal_almacen`, no en ningún *bucket*: cambiar primero muestra un almacén vacío. La herramienta (`MigracionAlmacen`, modos `conciliar`/`migrar`) ya existe |

---

## 7. Verificación — el gate de cierre cubre 5 de 23 suites

Existen **23 suites E2E** (más `e2e-context.ps1`, que es soporte). La corrida de
cierre ejecuta **cinco**:

```
en el gate:  comision-movimientos · disponibilidad-contrato · f4-solicitud
             estabilizacion-alquiler · editor-universal

fuera:       demanda-busqueda · e4-dashboard · f3-demanda · f6-f7-alertas-tareas
             ficha-comercial · locales-busqueda · locales-listado · personas
             reportes-propietario · solicitudes-busqueda · sonda-transporte · v6
             s0-bloqueo · s0-contrasenas · s0-emergencia · s0-mfa · s0-roles
             s0-sesiones
```

**Qué significa:** una regresión en personas, demanda, indicadores, alertas o en
**cualquiera de las seis suites de seguridad** no la detecta la corrida de
cierre. No es un descuido gratuito —las tres suites de búsqueda miden p95 en
esta misma máquina y son frágiles frente a una compilación en paralelo—, pero
**hoy no hay ninguna corrida que las pase todas**, ni una periodicidad acordada
para hacerlo.

Pendiente: decidir un **ciclo largo** (todas las suites, sin nada más corriendo)
y con qué frecuencia se ejecuta.

---

## 8. Fuera de alcance declarado — que no vuelva a proponerse

No son pendientes: son cosas **decididas como fuera**, y conviene tenerlas juntas
para que nadie las reabra por descuido.

| Qué | Dónde se decidió |
|---|---|
| **Informes PDF** (los 5 endpoints Jasper de la v1) — no se portan y no hay tecnología elegida. **No añadir botones «Exportar PDF»** | D-F5-1, `decision-reportes-pdf-fuera-de-alcance.md` |
| Matcher v2, negociación E3, compraventa completa, Neo4j, WhatsApp, LLM, voz, *embeddings*, memoria vectorial, LangGraph y automatizaciones autónomas de KAIROS | checklist, cierre de los bloques 2 y 3 |
| Configuración de la política comercial **por organización** — declarada y sin implementar a propósito | `inventario-umbrales-de-dominio.md` |
| El mapeo estado → tono duplicado en diez pantallas | ídem |

---

## 9. Estado documental — qué se corrigió hoy y qué queda por vigilar

### 9.1 Eliminado

| Documento | Por qué |
|---|---|
| `contrato-local-form.md` | Describía `LocalForm` y las rutas `/locales/nuevo`, `/locales/:id/editar`, **borrados** en los bloques 3d y 3f. Se presentaba como «el patrón reutilizable para formularios de alta/edición del SPA», que hoy es `propiedad-form` + `propiedad-editor` + `cl-campo-gobernado`: no estaba desactualizado, estaba **enseñando lo contrario**. Cero enlaces entrantes. Queda en git |

### 9.2 Anotados hoy (siguen siendo útiles, pero ya no dicen la verdad presente)

| Documento | Anotación |
|---|---|
| `modelo-herencia-y-generalizacion.md` | **HISTÓRICO**: lee el esquema MySQL y las clases de `backend-java/`, borrados. Se conserva por su punto B, que es el germen del modelo universal |
| `estado-backend-para-el-inicio.md` | **CUMPLIDO**: E2 cerró el 2026-08-19. Su único hueco vivo (`GET /inicio`) pasa a §4.6 de aquí |
| `traspaso-inicio-a-angular.md` | **CUMPLIDO** |
| `encargo-sesion-kairos.md` | **NO EJECUTADO, y a propósito.** Da por vigente el estado del bloque 3 (2026-08-18); desde entonces entraron V71…V78. **Reescribirlo antes de usarlo** |
| `auditoria-residuos-semanticos.md` | **PARCIALMENTE RESUELTA**, con el estado real de sus cinco puntos |
| `auditoria-ui-brox.md` | Tres de sus cinco `BACKEND_FALTANTE` ya existen. Y aviso de colisión: sus «Corte 1/2» son de UI, no los del catálogo |
| `mapa-pantalla-dominio-backend.md` | Medido el 2026-08-17 y no re-medido: varias filas `DERIVADO_FRONTEND` ya no lo están |
| Los 8 `contrato-congelado-*.md` | Citaban rutas `backend-java/...` como «fuente de verdad» y esa carpeta no existe desde el 2026-08-08 |
| `auditoria-profundidad-inmobiliaria.md` | Su cadena de migraciones previstas (V71…V75) se quedó corta: la real llega a V78. Se añadió la tabla de lo realmente aplicado y se corrigió «Corte 2 · migración V78» → **V79** |

### 9.3 Trampas de numeración que conviene no olvidar

Hay **tres** planes que usan las mismas letras y números para cosas distintas:

| Numeración | De qué habla | Dónde |
|---|---|---|
| **E0…E9** | etapas de captura del *moat* (E0 histórico económico … E9 certificación) | `mapa-ejecucion-brox.md`, checklist |
| **E1…E5** | etapas de la **migración** (personas, reportes, ficha, dashboard, corte) | docs marcados HISTÓRICO |
| **Corte 0A…7** | cortes del **catálogo** inmobiliario | `auditoria-profundidad-inmobiliaria.md` |
| **Corte 1, 2** | cortes de **UI** | `auditoria-ui-brox.md` |

Y los **bloques 2…9** de la ruta a BROX 1.0, que son otra cosa más.

### 9.4 Lo que sigue vigente y no se ha tocado

Gobiernan: `north-star-brox.md` (contra qué se mide un avance),
`mapa-ejecucion-brox.md` (dónde estamos),
`checklist-captura-moat-e-inteligencia-inmobiliaria.md` (qué falta para cerrar),
`auditoria-profundidad-inmobiliaria.md` (los cortes), las `decision-*` y
`matriz-operacion-rol.md` (que además está vigilada por un test). Los once
documentos con banner HISTÓRICO de la era de la migración se conservan tal cual:
explican el **porqué**, y CLAUDE.md ya avisa de que no gobiernan.

---

## 10. Si sólo se puede hacer una cosa

**Publicar la rama.** 43 commits —el modelo universal, el sujeto del dato, el
catálogo gobernado, el editor universal y las 22 suites de integración— existen
en un único disco. Todo lo demás de este documento se puede planificar; eso no
se puede recuperar.
