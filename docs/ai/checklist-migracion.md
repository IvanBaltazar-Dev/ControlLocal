# Checklist de la migración a Java fullstack

> **HISTÓRICO — NO GOBIERNA EL ROADMAP ACTUAL.**
> Describe el mundo de la migración: v1 sobre GlassFish, SPA Blazor, contrato
> congelado y corte del legado. Ese stack se borró el 2026-08-08 y el contrato se
> descongeló el 2026-08-09. Se conserva porque explica **por qué** las cosas son
> como son, no **qué** hacer ahora.
>
> El orden vigente sale solo de `mapa-ejecucion-brox.md` (dónde estamos) y
> `checklist-captura-moat-e-inteligencia-inmobiliaria.md` (qué falta para cerrar
> la etapa).

Qué falta para retirar del todo el stack legado (`backend-java/` + GlassFish + MySQL y
`frontend-csharp/` Blazor) y quedarse solo con `backend-spring/` + `frontend-angular/`.

Estado a **2026-08-02**. El estado vivo por vertical se mantiene en `backend-spring/README.md`;
esto es la foto completa, de punta a punta.

> **¿Solo quieres entender cómo va el proyecto y qué falta?** Empieza por
> **`mapa-estado-y-pendientes.md`**: es la versión legible —qué está hecho, qué problemas
> aparecieron y en qué orden atacar lo que queda—. Este documento es el inventario de trabajo.

> **Ojo al alcance de este checklist: solo cubre la migración.** El plan completo hasta producción
> —seguridad e identidad (S0), protección operativa, reglas de negocio, corte, arquitectura
> productiva, multiempresa e IA— vive en **`plan-maestro-ruta-a-produccion.md`**, que etiqueta cada
> punto como hecho / parcial / planificado / pendiente / futuro. Este checklist es la **Fase 4** de
> aquel (el corte), más las verticales ya cerradas.

> **2026-08-04 — E5 ya NO es lo siguiente.** El orden vinculante pasa a ser el de los BLOQUES 0–11
> del plan maestro: primero **persistencia y backups**, después **S0 seguridad**, y el corte
> (esto) llega **después**. Tres consecuencias para este documento:
> 1. **Las 2 pantallas que faltaban ya no están bloqueadas por una decisión de producto**: cambiar
>    contraseña y recuperar acceso **las desbloquea S0.3**, que les da endpoints reales.
> 2. **La deuda de seguridad de `GET /documentos/contenido`** (§1) sigue igual, pero ahora tiene
>    dueño y fecha: se retira en el corte, y el SPA ya no se apoya en ella.
> 3. **Se añadieron perfiles `dev`/`test`/`prod`** y un arranque que **falla** ante configuración
>    productiva insegura. Al tocar `application.yml` recuerde que `spring.datasource.*` ya **no vive
>    ahí**, sino en el fichero de cada perfil.

> **Actualización 2026-08-05**: con el **Bloque 4 (contraseñas y recuperación)** cerrado, las dos
> pantallas de identidad que faltaban **ya existen y tienen endpoint real**, así que el SPA llega a
> **49 de ~52 pantallas**. Del frontend solo queda la **paridad final**.
>
> **Ese mismo día se cerró el Bloque 5 (roles y gobierno)**: D-S0-17 aprobada tal cual, `TENANT_ADMIN`
> separado de `BROKER` en **26 operaciones**, y `usuario_organizacion` como fuente de verdad de la
> banda. Angular **499/499**, backend **515 tests + 4 gates**, `e2e-s0-roles` **48/48**.
> **Lo que cambia para quien migre pantallas**: el rol de la sesión que lee el SPA ya **no** es el
> del token —sale de `GET /sesion`— y `'ADMIN'` dejó de existir como valor; si una pantalla nueva
> ofrece una acción de broker, tiene que mirar `'BROKER'`, no "no es agente".
>
> El camino crítico sigue siendo el orden de bloques del plan maestro; el siguiente es el
> **Bloque 6 (MFA + break-glass)**, ya sin puertas cerradas delante.

**Dónde estamos**: **26 de 26** recursos REST migrados (+7 operaciones aditivas y 14 filtros
nuevos para las bandejas de cartera, cierres, captaciones, demanda y solicitudes),
**29 de ~52** pantallas de negocio migradas y las **verticales Oferta, F2 Proceso, F3 Demanda y
F4 Cierre completas**. Los transversales de autenticación/archivos/formato están cerrados.
**El backend está CERRADO** (2026-07-30): a los 26 recursos se sumaron la **matriz operación→rol**
con test de cobertura y la decisión **D-F5-1**, que retira los reportes PDF del alcance de la
migración. Lo único que queda en el camino crítico es el frontend Angular; Oferta está completa y
el bloque F2 está completo para agente, broker y administrador.

**Normalización integral cerrada (2026-08-01):** Flyway V15–V20 expande, rellena con evidencia
y finalmente restringe el modelo económico y contractual. Todos los estados persistidos usan
códigos de un carácter con `CHECK`; el carácter sigue siendo la única fuente de verdad JPA y
los enums estrictos se derivan mediante accessors `@Transient`. Quedaron separados estado del
registro/disponibilidad, vigencias/cierre, condición económica, movimientos de comisión y
auditoría de disponibilidad. Las monedas son `PEN`/`USD` obligatorias donde hay importe.

El contexto completo de Spring arrancó contra PostgreSQL 17.10, validó las **26 migraciones** y
ejecutó todas las consultas de estado (JPQL, métodos derivados, proyecciones y KPI): **4/4**
pruebas de repositorio verdes, sin cruces `String`/enum. A 2026-08-02 el reactor pasa
**453 pruebas** y Angular **385/385**. Cada E2E crea `controllocal_e2e_<run_id>` y elimina en
`finally` contenedores, red, volúmenes y filas; desarrollo usa exclusivamente
`controllocal_dev`.

> **Aviso que cuesta una corrida entera**: los 16 tests de integración se **saltan en silencio**
> sin `TEST_DB_URL` (`@EnabledIfEnvironmentVariable`), y son justo los que compilan el SQL nativo
> nuevo. Un `mvn test` "verde" sin esa variable no ha verificado ninguna consulta añadida.

> **La decisión "BACKEND PRIMERO" del 2026-07-29 se cumplió y ya no aplica.** El motivo era una
> inversión de dependencias que este documento no veía: `LocalRequest` exige `idPropietario`, y
> `LocalForm`, `Prospecciones`, `ReasignarCaptaciones` y `CaptacionDetail` del Blazor consumen
> propietario/broker/agente/reportes — o sea que cuatro de los recursos del "paso 3" bloqueaban
> pantallas de las dos primeras verticales del frontend. Cerrando el backend primero, **ninguna
> pantalla arranca bloqueada**. Etapas: ~~E0 tests y decisiones~~ ✅ →
> ~~E1 personas y perfil~~ ✅ → ~~E2 reportes-propietario~~ ✅ → ~~E3 ficha comercial~~ ✅ →
> ~~E4 dashboard/indicadores/seguimiento~~ ✅ → ~~cierre del backend (matriz de roles + D-F5-1)~~ ✅
> → **E6 frontend** (el bloque grande) → E5 paridad + S3 → corte.
>
> **E5 se movió detrás de E6 a propósito**: la paridad módulo a módulo y el almacén S3 real no
> bloquean ninguna pantalla, y la paridad se verifica mejor cuando hay un cliente nuevo que la
> ejercite. Lo único de E5 que no se dejó para el final fue la **matriz operación→rol con test de
> cobertura** —porque el SPA se apoya en ella para decidir qué muestra cada rol—, y ya está
> hecha: `matriz-operacion-rol.md`.

---

## 0. Orden de trabajo sugerido

El inventario de las secciones 1–4 dice *qué* falta; esto dice **en qué orden conviene hacerlo y
por qué**. No es un cronograma: es el orden de dependencias, para no bloquearse.

> **La regla que manda sobre todo lo demás**: mientras el legado siga vivo, el **contrato REST
> está congelado**. Nada de "arreglar de paso" un comportamiento raro de la v1 — se replica y se
> anota. Los bugs se corrigen recién en el paso 8.

### 1) ~~Terminar F4 — solicitud → contrato~~ ✅ HECHO (2026-07-28)

**Cerrada de punta a punta**: V8 + entidades + repositorios + los cinco services + los tres
controllers con sus DTOs congelados y el almacén de binarios. `mvn clean install` **192/192** y
`verificacion/e2e-f4-solicitud.ps1` **116/116**, con la cascada de siete efectos verificada
efecto por efecto contra la BD real. Con esto **el ciclo del negocio ya cierra**: hay contrato,
comisión y oportunidad finalizada exitosa.

Lo que quedó abierto y sigue vivo más abajo: los **efectos 6 y 7 de la cascada que dependen de
F6/F7** (`TODO(F6-alertas)` / `TODO(F7-tareas)`, sin romper la transacción), **D-F4-5** (el hueco
de alcance al revisar un documento suelto, replicado a propósito) y los **tests de comportamiento
de `SolicitudServiceImpl`**. Todo el detalle en el §9 de
`contrato-congelado-f4-solicitud.md`.

### 2) ~~F6 alertas + F7 tareas~~ ✅ HECHO (2026-07-28)

V9 (`alerta`, `tarea` y `reporte_propietario`) + los dos services + controllers. Salió el efecto 7
de la cascada de F4, la deuda vieja de F2 (*"Modificación comercial sensible"*) y **todos** los
`TODO(F6-alertas)`/`TODO(F7-tareas)` que quedaban en el backend: hoy no queda ninguno.

**Resultó menos "chica" de lo que decía este plan**: el recurso REST es trivial, pero las
**once emisiones** repartidas por captación, solicitud, documentos, evaluación, contrato y comisión
—cada una con su severidad derivada y su mensaje literal— y el **motor de siete disparadores** de
la bandeja son el grueso. Detalle y decisiones en
`docs/ai/contrato-congelado-f6-f7-alertas-tareas.md`; lo que hay que saber antes de tocarlas, en
`backend-spring/README.md`.

El recurso REST de `reportes-propietario` quedó cerrado en E2; sigue pendiente la **campana del
SPA**, que era lo que esto desbloqueaba.

### 3) ~~Personas y perfil~~ ✅ HECHO (E1, 2026-07-29)

Los cinco recursos están cortados: `/propietarios`, `/agentes`, `/brokers`, `/asignaciones` y
`/perfil`. Al cerrar E1 el reactor completo pasaba **321 pruebas** y
`verificacion/e2e-personas.ps1` pasa
**99/99** contra PostgreSQL. E1 reutiliza las tablas Party-Role de V1 y añade V10
(`reasignacion_agente_broker`) porque `supervision_agente` describe la relación vigente, pero no
conservaba el evento completo de reasignación (anterior, nuevo, administrador, motivo y fecha-hora).

Hallazgos que quedan congelados:
- **El catálogo es compartido para ADMIN y AGENTE**; el único rol acotado es el BROKER, y lo acota
  por sus **propiedades** (via captación o prospección), no por oportunidades como en clientes.
- **`cantidadLocales` es un contador CON ALCANCE**: dos actores ven números distintos del mismo
  propietario, y es correcto. Baja a SQL nativo (UNION de las dos ramas + `count(distinct)`)
  porque JPQL no tiene UNION.
- El **PUT responde el contador en 0**: el cable v1 no lo recalcula.
- La validación de persona se extrajo a `service/soporte/Personas` y `ClienteServiceImpl` ya
  delega ahí; altas de agentes/brokers comparten `service/soporte/UsuariosInternos`.
- `/perfil` sigue cubriendo solo teléfono y foto —eso es contrato congelado—, pero el cambio de contraseña ya existe **aparte**: `POST /perfil/contrasena`, aditivo (Bloque 4, 2026-08-05). La pantalla Blazor que lo ofrecía era un mock sin endpoint; ahora hay endpoint y hay pantalla.
- Mientras D-20 mantenga la convivencia con GlassFish, el login solo busca credenciales en
  `BROX_LEGACY`. El E2E crea una segunda organización, prueba que no entra en `/brokers` y que su
  credencial se rechaza, y luego retira el fixture.

### 4) ~~Reportes periódicos al propietario~~ ✅ HECHO (E2, 2026-07-29)

`GET` de listado, `GET /preview` y `POST` cortados sobre la tabla V9, sin V11. Los agregados se
calculan en SQL, el alta reinicia la tarea F7 y `verificacion/e2e-reportes-propietario.ps1` pasa
**50/50**. El reactor completo queda en **332 pruebas**.

### 5) ~~Ficha comercial~~ ✅ HECHO (E3, 2026-07-29)

Los cuatro GET compartidos por `/clientes` y `/propietarios` quedaron cortados con sus 11
secciones distintas, carga inicial parcial, aliases de paginación y privacidad por rol/equipo.
No necesitó V11. `verificacion/e2e-ficha-comercial.ps1` pasa **60/60** y el reactor completo,
**344/344**.

### 6) ~~Dashboard, indicadores y seguimiento comercial~~ ✅ HECHO (E4, 2026-07-29)

`/dashboard`, `/indicadores/resumen`, `/indicadores/avance` (RF-017) y
`/seguimiento-comercial`, cortados sin V11. Iban al final a propósito —agregan sobre las
verticales, y hacerlos antes obligaba a rehacerlos cada vez que se cortaba una— y esa apuesta
salió bien: al llegar aquí todo lo que agregan ya estaba migrado.
`verificacion/e2e-e4-dashboard.ps1` pasa **115/115** y el reactor queda en **388 pruebas**.

**Con esto el backend está completo: 26 de 26 recursos.**

Lo que quedó congelado y hay que conocer antes de tocarlo (detalle en
`contrato-congelado-e4-dashboard-indicadores-seguimiento.md`):

- **Ningún gate de rol en los tres recursos**; lo que cambia por rol es el alcance y el
  `ambito`. Para BROKER y ADMIN la bandeja del dashboard viaja **vacía**, no 403.
- **Dos reglas de alcance distintas que no se unifican**: indicadores alcanza solo por agente
  responsable; seguimiento por la unión agente-o-captación, y esa segunda rama existe solo para
  el BROKER (al AGENTE no le suma nada).
- El **contrato hereda** agente y captación de su solicitud y, en su defecto, de su
  oportunidad — en la v1 eso era un efecto colateral del DAO "shallow".
- Los agregados **bajan a SQL** con proyecciones estrechas: la v1 leía seis tablas completas en
  cada carga del dashboard.
- Cuatro bugs del cable se replican (el `100` fijo del embudo, la "visita realizada" que no
  mira el estado, `captacionesPendientes` duplicado y el fallback del operativo), más una
  rareza contraintuitiva: en el seguimiento **las filas sin fecha encabezan** la lista.

Lo único que E4 dejó fuera fue **`GET /indicadores/reporte/pdf`**, y ya no está diferido: se
retiró del alcance con el resto de Jasper (paso 7).

### 7) ~~Cierre del backend: matriz de roles + retiro de Jasper~~ ✅ HECHO (2026-07-30)

Dos cosas, y con ellas el backend queda cerrado:

**La matriz operación→rol**, que era la última deuda transversal.
`docs/ai/matriz-operacion-rol.md` lista las **146 operaciones** del backend con sus roles y —lo
importante— **dónde se decide el alcance**, y `MatrizOperacionRolTest` la vigila con cuatro
comprobaciones: cobertura (un endpoint nuevo no entra sin fila), sin filas muertas, que los roles
declarados sean exactamente los de `@PreAuthorize`, y que `PUBLICO` y `permitAll()` sean el mismo
conjunto en los dos sentidos. El documento **no puede quedar desactualizado**: si el código y la
tabla divergen, rompe el build.

Lo que la matriz deja a la vista y el SPA necesita saber: **62 de las 146 operaciones no llevan
gate de rol** (3 públicas + 59 autenticadas). No es un olvido — en la v1 el control de esas
operaciones es de *alcance*, no de *acceso*: todos entran y cada uno recibe su porción. Ese
silencio era justamente lo que había que documentar.

**Los reportes PDF quedan fuera del alcance** (**D-F5-1**,
`docs/ai/decision-reportes-pdf-fuera-de-alcance.md`). Los cinco endpoints Jasper de la v1 no se
portan, no se replica su layout ni su contenido, y no se elige todavía la tecnología de
reemplazo: la nueva funcionalidad de reportes se diseñará desde cero a partir de la nueva página
de reportes. Primero la página, después el PDF.

Dos consecuencias que no hay que perder de vista:
- **La paridad del corte deja de ser 1:1**: cinco endpoints de la v1 se quedan sin contraparte a
  propósito (ver §4).
- **Tres pantallas del SPA no deben llevar botón "Exportar PDF"**: los equivalentes Angular de
  `CaptacionDetail` (3 botones), `FichaPropiedad` y `Reportes`.

### 8) Frontend Angular (E6) — **el único camino crítico que queda**

Es el bloque más grande y ahora **nada lo bloquea**: los 26 recursos están cortados, así que
ninguna pantalla arranca esperando backend. 29 de ~52 pantallas, con **Oferta, F2, F3 y F4
completas**: bandejas, seguimiento, alta/subsanación, expediente, decisión, reasignaciones
auditadas, agenda de visitas, bitácora polimórfica y el **cierre del alquiler**. Quedan
**Personas** (11), **Identidad/perfil** (4) y **Comercial/gestión** (5).
Dentro del frontend, el orden **sí** es estricto:

1. **Transversales primero** (§3): servicios HTTP por módulo, componentes compartidos
   (`FilterBar`/`FilterSelect`/KPIs clicables, tabla paginada, subida de archivos), estado por rol
   y navegación, manejo de 401. Sin esto, cada pantalla reinventa lo mismo.
   El **estado por rol y la navegación** ya tienen su insumo listo: `matriz-operacion-rol.md`.
2. **Luego, vertical por vertical**, siguiendo el mismo orden del backend: oferta → proceso F2 →
   demanda F3 → cierre F4 → gestión.

Conviene arrastrar las lecciones ya pagadas en el Blazor: refrescar en `OnInit` para no servir
caché obsoleta, el 401 que cierra sesión **completa**, y no usar `@Assets` para imágenes.

Y una regla nueva por D-F5-1: **ninguna pantalla lleva botón "Exportar PDF"** hasta que exista la
nueva página de reportes (afecta a `CaptacionDetail`, `FichaPropiedad` y `Reportes`).

### 9) La nueva página de reportes (sin fecha, después del SPA)

Reemplaza a lo que era "F5/F8 reportes Jasper". **No es portar**: es diseñar de cero qué reportes
existen, qué datos muestra cada uno, quién los consume y con cuánta fidelidad — y recién entonces
elegir con qué se imprimen. Lo que ya está resuelto (los datos son alcanzables desde los DTOs de
service sin consultas nuevas) y las dos restricciones que condicionan la elección están en
`decision-reportes-pdf-fuera-de-alcance.md`.

### 10) Corte y recién entonces, mejoras

Paridad módulo a módulo, backfill definitivo, apagar GlassFish y MySQL. **Solo después** se
levanta el contrato congelado y se arreglan los bugs que hoy se replican a propósito (el 400 fijo
de `cierre-exitoso`, la moneda USD de la comisión, los tres endpoints de subida que solo existen
por un bug del cliente .NET). Y al final del todo: esquema AI-ready y multi-tenant real con RLS.

---

## 1. Backend — recursos REST

### Cortados y verificados E2E (26 — todos)

| Recurso v2 | Vertical |
|---|---|
| `/salud`, `/auth` | F0 |
| `/locales`, `/documentos/contenido` | F2-oferta |
| `/prospecciones`, `/captaciones`, `/captaciones/reasignaciones` | F2-proceso |
| `/clientes` (incluye ficha comercial E3), `/requerimientos`, `/oportunidades`, `/visitas`, `/interacciones` | F3 + E3 |
| `/solicitudes`, `/evaluaciones`, `/contratos` | **F4** |
| `/alertas` | **F6** |
| `/tareas` | **F7** |
| **`/propietarios`** (CRUD E1 + ficha comercial E3) | **E1 + E3** |
| `/captaciones/propiedades-equipo` (+ `/resumen`) — **aditivo, no existe en la v1** | F2 |
| `/contratos/resumen` — **aditivo**; `/contratos` gana filtros y `orden`, todos opcionales | F4 |
| `/agentes`, `/brokers`, `/asignaciones`, `/perfil` | **E1** |
| `/captaciones/{id}/reportes-propietario` | **E2** |
| **`/dashboard`, `/indicadores`, `/seguimiento-comercial`** | **E4** |

### Pendientes (0)

**Ninguno.** Los **5 endpoints PDF** de la v1 (los 4 de captación + `GET /indicadores/reporte/pdf`)
no cuentan como pendientes: quedaron **fuera del alcance** de la migración por **D-F5-1**, no
diferidos. No se portan.

### Cobertura de roles

Las **146 operaciones** están declaradas en `matriz-operacion-rol.md` y verificadas por
`MatrizOperacionRolTest`. **57 no llevan gate de rol** (3 públicas + 59 autenticadas): en esas, lo
que limita no es el acceso sino el **alcance**, y la matriz dice dónde se decide en cada una.

> **Corrección de dimensionamiento resuelta en E3 (2026-07-29):**
> `FichaComercialSupport` tenía **1.221 líneas y 11 secciones** compartidas por clientes y
> propietarios, por lo que se separó de los CRUD de E1. Sus cuatro endpoints ya están cortados y
> verificados; la paridad interna de ambos recursos quedó cerrada.

### Deudas técnicas del backend

- [x] **F4 cerrada** (2026-07-28): entidades, repos, los cinco services, los tres controllers con
      sus DTOs congelados y el almacén. 192/192 en el reactor y 116/116 en el E2E.
- [x] **`SolicitudServiceImpl` ya tiene tests de comportamiento** (2026-07-29): 32, cubriendo los
      nueve casos que el §9 listaba como faltantes. Con esto los cinco services de F4 están
      cubiertos.
- [x] **Los dos mensajes "inventados" RESUELTOS** (2026-07-29) — y la premisa del §9 era falsa:
      la v1 **no** devuelve 500 al duplicar, su `ApiExceptionMapper` responde **409**. Los dos
      `if` convertían ese 409 en un 400. Se quitó entero el de oportunidad (código muerto: la
      precondición "ABIERTA" corta antes) y el de código pasa a `ConflictoException` → **409**,
      conservando el mensaje que nombra el código. Detalle en el §9 del contrato.
- [x] **D-F4-5 CERRADA** (2026-07-29): revisar un documento suelto ya comprueba el alcance del
      broker, igual que conformar en bloque y evaluar. Donde la v1 respondía 200, la v2 responde
      **403**. Divergencia deliberada del contrato congelado, decidida en equipo; el Blazor no la
      alcanza por navegación. Actualizados el test y el check del E2E que fijaban el hueco.
- [x] **F6 + F7 cerradas** (2026-07-28): V9 (3 tablas), los dos services, controllers y las **once
      emisiones** cableadas. Con esto **no queda ni un `TODO(F6-alertas)`/`TODO(F7-tareas)` en el
      backend**: cayeron la alerta *"Modificación comercial sensible"* (deuda abierta desde F2) y
      el efecto 7 de la cascada de F4.
- [x] **RC-003 CERRADO para el listado de locales** (2026-08-02) — con margen operativo, no
      rozando el límite: **p95 944 ms** en página 1 y **peor 1.040 ms** sobre 100.000 locales
      medidos por HTTP, contra el límite de 3 s. El texto libre dejó de resolverse con un `OR` que
      cruza tablas —ningún índice puede servirlo— y pasó a **conjunto de candidatos**: una rama
      indexable por tabla unidas con `UNION`, el mismo conjunto para conteo, página y KPI, y la
      proyección completa cargada solo para los ids ya paginados. El término selectivo, que es lo
      que la gente escribe, cayó de 1.240 ms a 63. `texto` **ahora busca también por rubro**
      (V23 le dio su trigrama). Gate: `e2e-locales-busqueda.ps1` **21/21**, más
      `BusquedaLocalesIntegrationTest` **12/12**. El patrón es obligatorio para toda bandeja nueva
      con texto libre: `contrato-listados-paginados.md` §5.
- [ ] **Observación, no tarea: la última página cuesta ~800 ms por el `OFFSET`.** Recorrer 99.990
      entradas de índice se paga con o sin búsqueda (con texto 824 ms de p95, sin texto 796), así
      que ninguna mejora de la búsqueda lo mueve. Si alguna vez se quiere la última página por
      debajo del segundo, la palanca es **paginación por clave** (keyset), y eso toca la forma del
      cable —`page` numérica— así que **no antes de levantar el contrato congelado**. Hoy no
      molesta: nadie navega a la página 10.000, y RC-003 se cumple con 3× de margen.
- [ ] **Observación, no tarea: la primera llamada en frío cuesta bastante más.** Se han visto
      picos de 2,5–6,6 s en la primera petición de cada escenario, por el JIT del camino de
      consulta, la caché de planes vacía y las páginas fuera del *buffer*. **No depende del
      volumen** —sale igual con 30.000 filas que con 100.000, de hecho salió peor con 30.000—, así
      que no es un problema de diseño de consulta. El gate la mide aparte (columna `Frio`) y le
      exige el límite de RC-003. Si algún día molesta en producción, se ataca por calentamiento al
      desplegar, no tocando el SQL. Ojo al medir: hay **varianza de máquina** de hasta 3× entre
      corridas del mismo escenario; se juzga por el gate completo, no por una cifra suelta.
- [ ] **`CAPTACION_CREADA` casi nunca se emite** y es cable: `captar` crea la captación saltándose
      el alta que avisa, así que el broker no recibe aviso por el camino normal. Replicado;
      candidato claro a arreglar al levantar el contrato congelado.
      > **Cerrado a medias (2026-08-01):** el hueco de aviso sigue, pero `captar` ya **no** crea
      > captaciones sin periodo de encargo (D-F2-1): lo completa con el defecto de la casa y
      > V21 puso `NOT NULL` en las dos fechas.
- [ ] La alerta de modificación sensible viaja con el tipo **`SOLICITUD_EVALUADA`** porque no hay
      uno que encaje (D-F6-5). Bug congelado, como la moneda USD.
- [x] **`AlertaService`/`TareaService` ya tienen tests de comportamiento**: `AlertaServiceImplTest`
      (16) y `TareaServiceImplTest` (21). Esta entrada estuvo marcada como abierta por error hasta
      el 2026-07-30; el motor de derivación de la bandeja —lo que más los pedía— está cubierto.
- [x] **Los 5 PDF Jasper salen del alcance** (D-F5-1, 2026-07-30): los 4 de F2 (incluido el de
      reportes-propietario) y `GET /indicadores/reporte/pdf` de E4 **no se portan**. Ya no son un
      diferido a F5/F8: la nueva funcionalidad de reportes se diseñará desde cero junto con su
      pantalla. Ver `decision-reportes-pdf-fuera-de-alcance.md`.
- [x] `GET /evaluaciones` de la v1 paginaba en memoria → en la v2 baja a SQL con LIMIT/OFFSET
      (MEJ-05 / RC-003), misma respuesta. Hecho con `EvaluacionServiceImpl`.
- [x] **Los agregados de E4 bajan a SQL** (2026-07-29): la v1 cargaba seis tablas completas en
      cada carga del dashboard —la causa del incidente de ~50 s de RC-003—; la v2 pone el scope
      y la ventana en el WHERE y lee proyecciones estrechas del paquete `query`.
- [ ] **`GET /documentos/contenido` no cumple las condiciones de un endpoint público** (revisión
      2026-07-30). Es réplica del cable v1, así que arreglarlo es divergir del contrato congelado
      —decisión deliberada, como D-F4-5—. Lo que falla, en orden de gravedad:
      1. **La clave ES la ruta física** (`AlmacenDisco` la construye como
         `carpeta/uuid8-nombre` y `resolver()` hace `raiz.resolve(clave)`). Filtra el correlativo
         (`SOL-0007`), el id del local y el **nombre original del archivo**: la clave misma dice
         *"este es el DNI de la solicitud SOL-0007"*.
      2. **32 bits de entropía**: `UUID.randomUUID().toString().substring(0, 8)`. Y como el resto
         de la clave es predecible, adivinar un documento es fuerza bruta sobre 2³² **sin ningún
         límite de tasa** — `LimitadorIntentos` solo protege `/auth/login`.
      3. **Las claves no caducan ni se pueden revocar.** No hay TTL ni firma. Una clave filtrada
         (historial del navegador, `Referer`, captura, log de proxy) expone el documento para
         siempre — y ahí viven DNI, comprobantes de ingresos y contratos.
      4. **La clave viaja en el query string**, que es justo lo que registran los access logs de
         Tomcat, los proxies y el `Referer`. Además, `AlmacenException` incrusta la clave completa
         en el mensaje y el controlador la devuelve al cliente dentro de un 502.
      5. **Cabeceras**: `Content-Type` se deriva de la **extensión** (no de los magic bytes ni de
         metadato almacenado), se sirve **siempre `inline`**, y faltan `X-Content-Type-Options:
         nosniff` y `Cache-Control: private, no-store`.
      **Qué NO falla**: el path traversal está bien cerrado (doble guarda — el controlador rechaza
      `..` y `\`, y `AlmacenDisco.resolver()` normaliza y exige `startsWith(raiz)`), y no hay
      listado de directorio.
      **Lo que cambia con E6**: el endpoint es público *por una restricción del Blazor* —"que el
      visor cargue binarios sin propagar el JWT al navegador"—, y **Angular no la tiene**: el SPA
      ya tiene el token y puede pedir el binario con `responseType: 'blob'` + `Authorization` y
      renderizarlo con `URL.createObjectURL`. Así que **el visor del SPA no se construye sobre la
      URL pública** y el endpoint queda como superficie a retirar en el corte.
- [ ] `IndicadorService`/`SeguimientoComercialService` tienen suite dedicada (27 y 17), pero
      **el `desempeno` por broker solo está cubierto por test unitario**: el E2E no crea un
      segundo broker con equipo propio, así que la comparación entre brokers reales del seed no
      se ejercita de punta a punta.
- [x] **Matriz completa operación→rol CERRADA** (2026-07-30): `docs/ai/matriz-operacion-rol.md`
      declara las 146 operaciones y `MatrizOperacionRolTest` las vigila con cuatro comprobaciones
      (cobertura, sin filas muertas, roles == `@PreAuthorize`, `PUBLICO` == `permitAll`). El
      documento no puede quedar desactualizado: si diverge del código, rompe el build.

**Diferidas al post-corte a propósito** (no son trabajo pendiente del backend; cada una con su
razón, para que nadie las retome antes de tiempo):

- [ ] **Almacén S3 real**: hoy solo `AlmacenDisco`, y funciona (F4 lo reusa tal cual para el
      expediente). Es un cambio de infraestructura que **ninguna pantalla percibe**, así que no
      bloquea el SPA ni cambia el contrato.
- [ ] **Buffer de la subida por trozos**: el de una carga **abandonada** no se libera hasta
      reiniciar el API (mismo comportamiento que la v1). No se arregla: el endpoint entero **se
      elimina** cuando muera el Blazor —existe solo por un bug del `SocketsHttpHandler` de .NET 10
      y con Angular basta base64/octet-stream—, así que arreglarlo hoy es trabajo que se tira.
- [ ] **`Descripciones` duplicado**: las descripciones congeladas de los códigos de estado están
      en `service/soporte/Descripciones`, pero `FichaComercialServiceImpl` (E3) conserva su copia
      privada. Unificarlas es limpieza; E3 está verificada con 60/60 y **no se toca durante la
      convivencia** — el riesgo de romper un módulo cerrado no compensa.

---

## 2. Base de datos

- [x] V1–V3 identidad Party-Role + auditoría universal + seed
- [x] V4 oferta · V5 proceso · V6 núcleo multi-tenant · V7 demanda
- [x] **V8 solicitud/documentos/contrato/comisión** (aplicada 2026-07-27)
- [x] **V9 alertas, tareas y tabla de reportes-propietario** (aplicada 2026-07-28)
- [x] **V10 evento histórico de reasignación agente→broker** (aplicada 2026-07-29)
- [x] **V11 índices del listado filtrado de locales**
- [x] **V12 corrección de dato: comisión de `CAP-0001`** (aplicada 2026-07-31). El seed de V5
      la puso en `4250.00` como si el campo fuera un importe en soles; es un **porcentaje sobre
      la renta mensual**, así que declaraba una comisión de 42,5 meses de alquiler. Pasa a
      `100.00` = un mes. Solo esa fila, localizada por código **y organización** (desde V6 la
      unicidad es `(organizacion_id, codigo_captacion)`, así que otro tenant puede tener su
      propio `CAP-0001`). No edita V5. Semántica completa en
      `decision-modelos-de-comision.md`.
- [ ] **Backfill real desde MySQL v1** (Doc 6: mapeo de ids v1↔v2, credenciales sin re-hashear)
- [ ] Activar **RLS** en Postgres al pasar a multi-tenant real (hoy: discriminador + filtro en la
      app, D-24, tenant único `BROX_LEGACY`)

---

## 3. Frontend Angular — lo más grande que falta

Hoy el SPA tiene **login + inicio + shell, las 6 pantallas de Oferta, las 8 de F2, las 9 de F3
y las 5 de F4**; el Blazor tiene **58 `.razor`** (≈52 funcionales, el resto son
error/404/acceso denegado). **Locales** fijó el patrón de listados paginados, **LocalForm** el
de formularios de alta/edición con catálogo paginado, **LocalDetail** el de fichas de detalle
con bloques que fallan por separado y **FichaPropiedad** el de fichas que **encadenan
identificadores** entre recursos en vez de emparejar registros por texto.

### Transversales primero (habilitan todo lo demás)

- [ ] Capa de servicios HTTP por módulo (equivalente a los `Http<Nombre>Service` del Blazor)
      — hechos: locales (+ precios y publicaciones), propietarios, documentos,
      **prospecciones y captaciones** (2026-08-01), **clientes, oportunidades, requerimientos,
      visitas, interacciones y coincidencias** (2026-08-02), **solicitudes (con su expediente
      documental e historial), evaluaciones y contratos** (2026-08-02). Ojo al añadir el
      resto: `/prospecciones`, `/clientes` y `/solicitudes` paginan con `pagina`/`tamano` y **no**
      aceptan los alias `page`/`page_size` de `/locales`; y el nombre del texto libre **cambia por
      recurso** (`texto` en locales y solicitudes, `query` en oportunidades y visitas, `q` en
      interacciones y captaciones). No se unifica mientras el contrato siga congelado.
- [x] **Formato de fechas, números y montos** (2026-07-31): `core/formato.ts` con `Intl` en vez
      de los pipes de Angular (el `LOCALE_ID` es `en-US` y solo trae sus datos). Fija que una
      fecha **sin hora no corre un día** —`new Date('2026-07-30')` es medianoche UTC y en Lima
      mostraría el 29—, que el **cero es dato**, que el monto lleva el **código** de moneda
      delante y que un **porcentaje no se agrupa** (`4,250 %` se lee como 4,25 % y ahí se
      muestra la comisión pactada de una captación).
- [x] **Bloque complementario reutilizable** (2026-07-31): `core/bloque.ts` (`Bloque<T>` +
      `complementario()`). Regla de las fichas: **el recurso principal es fatal, lo
      complementario degrada con su propio aviso**. Lo usan `LocalDetail` y `FichaPropiedad`.
- [x] **Imagen del almacén con token** (2026-07-31): `cl-imagen-segura` es a las miniaturas lo
      que `cl-visor-documento` a los documentos —mismo `DocumentosService`, misma regla de no
      poner `/documentos/contenido?clave=` en un `src`—, pero pensado para rejillas. Cada
      instancia revoca su object URL.
- [x] **Primitivas de estilo compartidas** (2026-07-31): `cl-tarjeta`, `cl-tabla`, `cl-badge`,
      `cl-btn` y el armazón de una ficha (`cl-cabecera`, `cl-resumen`, `cl-pares`,
      `cl-columnas`, `cl-aviso`/`cl-ok`/`cl-vacio`/`cl-menudo`) en `styles.scss`. Se
      promovieron al ver la tabla y el badge duplicados entre el listado de locales y su
      ficha, con ~48 pantallas por delante. **El tono del badge se pide con una clase, nunca
      se deriva de la letra**: `P` es "pendiente" en captación y "publicado" en publicación.
- [x] **Capa HTTP base** (2026-07-30): `core/api/api.types.ts` (`PageResponse` **1-based**,
      `ApiError` con `sinPermiso`/`conflicto`/… para no decidir comparando cadenas),
      `core/api/api.client.ts` (único punto de salida; el token lo pone el interceptor) y
      `core/api/codigos.ts` (catálogo de los códigos de una letra del cable).
- [x] **Servicio de Locales** (2026-07-30): página filtrada y resumen en backend; `get$`
      cancelable para que `switchMap` aborte la petición anterior. Se retiraron
      `cartera()`, el barrido de 10 páginas y el techo artificial de 1.000 filas.
- [x] **Componentes compartidos** (2026-07-30): `cl-barra-filtros` (búsqueda con antirebote +
      proyección de filtros secundarios + contador + limpiar), `cl-filtro-select`,
      `cl-paginacion` (misma ventana con elipsis que el Blazor, **11 tests**), `cl-kpi`
      (clicable como atajo de filtro), `cl-estado-listado` (carga/error/vacío) y
      `cl-confirmacion` (distingue `ocupado` de `bloqueado`), `cl-subida-archivo`
      (validación atómica de extensión/MIME/firma/tamaño) y `cl-visor-documento`
      (Blob autenticado, cancelación y liberación de object URLs).
- [x] **Estado por rol y navegación** (2026-07-30): `core/auth/acceso.ts` deriva de
      `matriz-operacion-rol.md`; `rolGuard` usa el mismo mapa que dibuja el menú, así que un
      módulo que no aparece tampoco se alcanza por URL. Pantalla `acceso-denegado`. Verificado en
      navegador con AGENTE y BROKER, y fijado con **8 tests**.
- [x] **Subida de archivos reusable** (2026-07-30): `ArchivosService` comparte el máximo de
      5 MB, nombre seguro, listas blancas de extensión/MIME y magic bytes; `cl-subida-archivo`
      entrega únicamente archivos ya validados. `ApiClient.postBinario$` ofrece progreso y
      cancelación sin convertir a base64; `base64()` queda solo para los endpoints congelados
      de fotos que lo exigen.
- [x] **Visor de documentos con token, NO con la URL pública** (2026-07-30): `responseType: 'blob'` +
      `Authorization` + `URL.createObjectURL`. El `GET /documentos/contenido` público existe por
      una restricción del Blazor que Angular no tiene (ver la deuda de seguridad en §1); si el SPA
      se apoya en él, esa superficie se vuelve imposible de retirar en el corte. El visor cancela
      la lectura anterior y revoca el object URL al cambiar de documento o destruirse.
- [x] **Campana de notificaciones** (2026-08-04): `cl-campana` en el shell, **sin store global** —el
      `NotificacionStore` Singleton del Blazor existía porque el estado vivía en el servidor; aquí
      las alertas son un recurso REST—. Se pide al entrar y al abrir, **sin sondeo**: `GET /alertas`
      materializa el barrido de recontacto como mucho una vez cada 5 minutos, así que sondear no
      adelantaría ningún aviso. Devuelve **solo las activas**, así que el contador es su
      `totalRecords`; atender comprueba **visibilidad, no propiedad** (un broker atiende las de su
      equipo) y un `atendida: false` significa "ya estaba atendida", por lo que la fila se retira
      igual en vez de quedarse zombi.
- [x] **Manejo global de 401** (2026-07-30): el interceptor solo adjunta el JWT al API de
      ControlLocal (nunca a URLs externas), excluye el login y convierte cualquier 401 protegido
      en limpieza completa e idempotente de señal + `localStorage` + navegación con `replaceUrl`.
      Las sesiones persistidas vencidas o corruptas se descartan al arrancar.
- [ ] Reglas heredadas: refrescar en `OnInit` para evitar caché obsoleta; **no** usar `@Assets`
      para imágenes

### Pantallas por vertical (agrupadas como se van a cortar)

- [x] **Identidad/perfil** (4): ~~Perfil~~ ✅, ~~Acceso denegado~~ ✅, ~~Cambiar contraseña~~ ✅
      y ~~Recuperar acceso~~ ✅ (2026-08-05, Bloque 4) — **vertical Identidad COMPLETA**. Las dos
      últimas eran mocks del Blazor sin endpoint en la v1; ahora los tienen (`POST
      /perfil/contrasena`, `POST /auth/recuperacion` y su canje). **Viven fuera del shell**: la de
      cambio tiene que funcionar con la sesión capada por contraseña temporal, y ahí el armazón
      —campana y menú— llama a endpoints que el 403 bloquea
- [ ] **Oferta** (6): ~~Locales~~ ✅, ~~LocalForm~~ ✅ (2026-07-30), ~~LocalDetail~~ ✅
      (2026-07-31), ~~FichaPropiedad~~ ✅ (2026-07-31), ~~PropiedadesEquipo~~ ✅
      (2026-08-01), ~~PropiedadesAlquiladas~~ ✅ (2026-08-01) — **vertical Oferta COMPLETA**
      > **PropiedadesAlquiladas** ("Cierres exitosos") tambien necesitó extensión aditiva:
      > `GET /contratos` solo aceptaba `pagina`/`tamano`, y la pantalla filtra por texto,
      > distrito y agente, ordena por fecha de cierre y **suma la comisión de todo el
      > alcance**. Con el tope de 100 filas por página, una suma hecha en el cliente sería
      > falsa pasados los 100 cierres y no lo avisaría. Se añadieron los filtros
      > (`texto`, `distrito`, `idAgente`, `orden=cierre`, todos opcionales: omitidos responde
      > igual que antes, incluido el orden congelado por id) y `GET /contratos/resumen`.
      > Divergencias deliberadas: se muestra el **estado real del contrato** en vez del
      > "Alquilado" fijo del legado (que rotulaba igual a los rescindidos), y **no hay "Ver
      > detalle"** porque `SolicitudDetail` no está migrada. La **exportación CSV** sí se
      > porta: recorre el conjunto filtrado —acción puntual del usuario, no carga de
      > pantalla— y **avisa si supera el máximo** en vez de recortar en silencio.
      > **Estabilización económica (2026-08-01):** la tabla separa estado jurídico del
      > contrato, disponibilidad del local y cobro de la comisión; el resumen usa exactamente
      > los filtros de la tabla, excluye anuladas y cuenta contratos sin liquidación. La moneda
      > ya forma parte de la renta y de la liquidación. V16 regulariza sólo con evidencia y
      > V17 hace `PEN`/`USD` obligatorios: las pantallas operativas no tienen fallback
      > “Moneda no definida”. Verificación real: Flyway V20, 18/18 E2E aislado y
      > repositorios de estado 4/4 contra PostgreSQL.
      > **PropiedadesEquipo** es la primera pantalla que **necesitó extensión aditiva del
      > backend**, y la razón es estructural: mira la cartera **por inmueble**, no por
      > captación —un local acumula varias (cerradas, rechazadas, vencidas) y solo una ACTIVA—,
      > y **deduplicar por propiedad no se puede hacer sobre una página**. El Blazor lo
      > resolvía descargando todas las captaciones del equipo y agrupando en memoria. Se
      > añadieron `GET /captaciones/propiedades-equipo` y `/resumen` (BROKER, ADMIN), que
      > deduplican con `DISTINCT ON`, filtran (`texto`, `distrito`), ordenan, paginan y cuentan
      > en SQL; el resumen cuenta **inmuebles distintos** y devuelve los distritos disponibles
      > para que el filtro sea data-driven sin llamada extra. Divergencia deliberada: el legado
      > da la pantalla **solo al BROKER**; aquí entra también el **ADMIN**, como el resto de
      > endpoints de supervisión del v2.
      > **FichaPropiedad** entra por el **código de captación** (`/captaciones/:codigo/ficha`)
      > y su aporte es cómo arma el modelo: la v1 descargaba **las tres bandejas enteras**
      > —captaciones, locales y propietarios— y luego emparejaba el local por *coincidencia
      > difusa de dirección* y el propietario por *coincidencia difusa de nombre* (`Loose()`:
      > igualdad o que una cadena contenga a la otra). Además de carga masiva y filtrado en
      > memoria, eso **puede identificar el registro equivocado**: bastan dos locales en la
      > misma avenida. La v2 encadena ids, que es lo que el contrato ya permitía —la captación
      > trae `idLocal` y el local trae `idPropietario`—: tres saltos, todos por id y con el
      > alcance resuelto en el backend. Divergencias deliberadas: **sin "Exportar PDF"**
      > (D-F5-1, tampoco el aviso que invitaba a exportar), la **descripción es la del local**
      > en vez de un párrafo comercial generado igual para toda propiedad, y el **mapa se abre
      > al pulsarlo** en lugar de incrustar un iframe de Google en cada carga.
      > **LocalDetail** fija el patrón de las **fichas de detalle**: cabecera + barra de
      > resumen, tarjetas por bloque y **fallo independiente por bloque** (si el histórico de
      > precios responde 500, la ficha se dibuja igual con su aviso; solo el recurso principal
      > es fatal). Tres divergencias deliberadas respecto del Blazor, todas documentadas en el
      > componente: la prospección se pide **filtrada por `idLocal`** en vez de descargar la
      > bandeja y emparejar por dirección (RC-003); los bloques complementarios fallan por
      > separado en vez del `try/catch` mudo; y **no lleva enlace "Crear captación"**, porque
      > esa pantalla no está migrada y un botón que no lleva a ninguna parte es peor que su
      > ausencia. Incluye el editor de publicaciones (alta/edición + pausar/publicar/cerrar,
      > solo AGENTE).
      > **Patrón definitivo cerrado**: `GET /locales` filtra/pagina/cuenta en SQL,
      > `GET /locales/resumen` calcula los KPI en PostgreSQL y Angular conserva
      > `texto`/`estado`/`page` en la URL. KPI y select escriben el mismo estado,
      > `switchMap` cancela lecturas anteriores y no existe carga masiva. Convención y
      > contrato: `docs/ai/contrato-listados-paginados.md`. Verificación real:
      > `e2e-locales-listado.ps1` **18/18** con 1.005 + 7 filas de otro tenant.
      > **Búsqueda cerrada (2026-08-02, RC-003):** el texto libre pasó a conjunto de candidatos
      > —una rama indexable por tabla, `UNION`, mismo conjunto para conteo/página/KPI— y ahora
      > alcanza también al **rubro**. p95 944 ms sobre 100.000 locales; el término selectivo cayó
      > 20 veces. Es el patrón obligatorio para las bandejas que quedan (§5 del contrato de
      > listados), y **la primera que lo hereda es Clientes**, en F3.
- [x] **Personas** (11): ~~Propietarios (3)~~ ✅, ~~Agentes (3)~~ ✅, ~~Brokers (4)~~ ✅,
      ~~Catálogos~~ ✅
      > **COMPLETA (2026-08-03): 11/11**, más `Perfil`, que cuenta en el bloque de identidad.
      > Angular **397/397**, build de producción verde (AOT), reactor **464**, matriz **150
      > operaciones** y `e2e-personas.ps1` **122/122**.
      > - **Propietarios (3)**: bandeja con búsqueda, alta/edición y ficha comercial E3.
      > - **Agentes (3)**: bandeja con búsqueda y cubos, alta/edición y **ficha completa**.
      > - **Brokers (4)**: bandeja, ficha con equipo, alta/edición y **Mi equipo**.
      > - **Catálogos**, **Asignaciones** y **Perfil**.
      >
      > **Tres extensiones aditivas del backend hicieron falta**, y ninguna era opcional:
      > - **`GET /agentes/{id}`**: la ficha del agente **no se arma combinando páginas** de las
      >   bandejas de captaciones, oportunidades, solicitudes y cierres. No es eficiencia, es
      >   corrección: cada listado pagina, y contar sobre la página visible da un número falso en
      >   cuanto el agente tiene más de una página de trabajo. Devuelve identidad, supervisión
      >   vigente, los tres repartos por estado, los cierres y **las cuatro magnitudes de
      >   comisión** separadas por moneda.
      > - **Filtros en `GET /agentes` y `GET /propietarios` + sus `/resumen`**: la búsqueda la
      >   resuelve la base. Filtrar en memoria haría que «no hay resultados» significara «no hay
      >   en estas diez filas».
      > - Los tres respetan el alcance y **omitidos los filtros el cable responde igual que
      >   antes**, orden por id descendente incluido.
      >
      > **V27 es lo que hace posible la ficha**: los cierres y el dinero se filtran por
      > `contrato_alquiler.id_rol_agente_cierre`, no por la cadena solicitud→agente. Un agente que
      > cambió de equipo conserva su historia, que es justo lo que una ficha de persona debe
      > mostrar; antes de V27 la respuesta se movía con el organigrama.
      >
      > Rarezas del cable que las pantallas respetan en vez de disimular:
      > - **Los contadores comerciales solo son reales en `GET /agentes`**. POST, PUT y
      >   `/brokers/{id}/agentes` los devuelven en `0`, así que ni la ficha del broker ni **Mi
      >   equipo** los pintan: enlazan a la ficha del agente, que sí los calcula.
      > - **`cantidadLocales` viaja en 0 en la cabecera de la ficha de propietario** aunque la
      >   sección `locales` tenga registros: el número bueno sale del `totalRecords` de la sección.
      > - **`prospecciones` y `captaciones` llegan con total calculado pero `items` vacío**, así que
      >   `esPendiente` las da por resueltas; hay que llevar la cuenta aparte de lo realmente
      >   pedido. Única asimetría con la ficha de cliente.
      > - **El ADMIN no da de alta agentes** aunque el gate lo admita: el alta crea la supervisión
      >   inicial *por el broker en sesión*. Se anticipa en el formulario, no con un 400.
      > - **`PUT` de agente y broker descarta en silencio** documento, tipos, usuario, contraseña,
      >   código, fecha y `esAdministrador`: en edición van bloqueados y con el motivo a la vista.
      > - **No hay cambio de contraseña** en el contrato; `Perfil` no pinta ese formulario (la
      >   pantalla Blazor que lo ofrecía era un mock sin llamada HTTP).
      >
      > Dos decisiones de producto que se tomaron aquí y conviene conocer:
      > - **«Mi equipo» no duplica la ficha de broker.** Aquella es la de *un* broker desde el
      >   catálogo y la abre cualquiera; esta es la del que tiene la sesión, con el aviso de quién
      >   no puede recibir encargos hoy. Al ADMIN no se le ofrece: no supervisa a nadie y su vista
      >   del organigrama es **Asignaciones**.
      > - **Catálogos se genera de `core/api/codigos.ts`**, la misma fuente que leen las pantallas.
      >   El Blazor tenía esa tabla escrita a mano dentro del componente y podía desviarse de lo
      >   que el sistema usaba de verdad; así no puede.
- [x] **Proceso F2** (8): ~~Prospecciones~~ ✅ (2026-08-01), ~~ProspeccionDetail~~ ✅ (2026-08-01),
      ~~Captaciones~~ ✅ (2026-08-01), ~~CaptacionForm~~ ✅ (2026-08-01),
      ~~CaptacionDetail~~ ✅ (núcleo F2, 2026-08-01), ~~CaptacionReview~~ ✅ (núcleo F2, 2026-08-01),
      ~~BandejaCaptaciones~~ ✅ (2026-08-01), ~~reasignaciones de captación + historial~~ ✅
      (consolidadas en una pantalla, 2026-08-01)
      > Las dos bandejas conservan filtros y página en la URL, cancelan lecturas anteriores y
      > filtran/paginan en SQL. Al construir `Prospecciones` aparecieron dos brechas de paridad
      > del listado v2: `estado=GESTION` debía significar el cubo `{P,C,R,E,S}` y faltaba
      > `idBrokerSupervisor`; ambas quedaron corregidas y fijadas con tests. `Captaciones` gana
      > tres filtros **aditivos y opcionales** (`estado`, `idAgente`, `q`): si se omiten, el GET
      > conserva el resultado congelado. La navegación deja de usar los rótulos confundibles
      > “Ficha propiedad”/“Ver local”: **Datos del local** abre el registro técnico-operativo y
      > **Resumen comercial** abre galería, condiciones, comisión y responsables de la captación.
      > `ProspeccionDetail` replica la máquina real —incluido que propuesta deja `S`, no `E`—,
      > separa lectura BROKER/ADMIN de las mutaciones del AGENTE y no adelanta interacciones ni
      > matching, que pertenecen a F3. `CaptacionForm` admite alta libre, alta desde prospección y
      > edición P/O; una O muestra la observación del broker y su PUT la reenvía a P. La selección
      > libre consulta solo 20 locales disponibles y permite buscar en SQL, en vez de descargar la
      > cartera. Si el alta se completa pero falla el vínculo con la prospección, conserva la
      > captación creada para reintentar únicamente `marcar-captado`, evitando duplicados.
      > La bandeja del broker filtra `estado`/`idAgente`/`q` y pagina en SQL. Revisión implementa
      > aprobar, observar y rechazar —motivo obligatorio en O/R—, además de reasignar sin alterar
      > el estado. El expediente ofrece cerrar una aprobada y usa tres nombres deliberadamente
      > distintos: **Expediente**, **Datos del local** y **Resumen comercial**. Sus paneles de
      > oportunidades/interacciones y solicitudes se incorporarán con F3/F4, no como adelanto F2.
      > La última pantalla consolida los dos Razor del broker: pagina/busca captaciones activas en
      > `/reasignables`, ofrece solo agentes activos y disponibles, exige motivo y refresca el
      > historial auditado después del POST. La reasignación agente↔broker del ADMIN no es F2:
      > queda correctamente en el bloque Personas/Asignaciones.
      > **D-F2-1 (2026-08-01): el periodo del encargo es obligatorio siempre.** La v1 solo lo
      > exigía para activar, así que `POST /prospecciones/{id}/captar` creaba borradores sin
      > fechas y la semilla mostraba CAP-0001 con el periodo en blanco. Ahora `captar` lo completa
      > con el defecto de la casa y **V21 puso `NOT NULL`** en las dos columnas; los fixtures SQL
      > de E2E viajan con el periodo. Divergencia de datos con la v1, no de contrato.
- [x] **Demanda F3 (COMPLETA, 2026-08-02)**: ~~Clientes~~, ~~ClienteForm~~, ~~ClienteDetail~~,
      ~~ClienteContactoDetail~~, ~~Oportunidades~~, ~~OportunidadForm~~, ~~OportunidadDetail~~,
      ~~Visitas~~, ~~VisitaForm~~, ~~Interacciones + InteraccionForm + InteraccionDetail~~
      > **Dos extensiones aditivas del backend** hicieron falta para no repetir lo que hacía el
      > Blazor —descargar todo y agrupar en memoria—: `estado` en `GET /oportunidades` y
      > `GET /oportunidades/resumen`, y `GET /visitas/resumen` (cinco cubos + los distritos del
      > alcance). Ambas cuentan con un solo `group by` sobre el MISMO conjunto que pagina la lista,
      > omitidas responden byte a byte como la v1, y llevan su fila en la matriz. Mismo criterio que
      > con `/locales/resumen`, `/clientes/resumen` y `/contratos/resumen`.
      > **Las tres bandejas heredan el patrón de búsqueda por conjunto de candidatos (§5)**. No lo
      > hacían al escribirlas —nacieron con el `OR` cruzado que §5 prohíbe— y se corrigieron antes
      > de cerrarlas: 4 ramas en oportunidades, 2 en visitas y 5 en interacciones, `UNION` en la
      > base, el mismo conjunto para conteo, página y KPI, y la proyección cargada después solo
      > para los ids de la página. **V25** pone los cuatro trigramas que faltaban
      > (código de oportunidad, de captación, de prospección y observaciones de la interacción) más
      > tres índices de recorrido por tenant.
      > **Gate de rendimiento `e2e-demanda-busqueda.ps1`, 100.000 filas por tabla: SUPERADO
      > (2026-08-02) y FIRMADO 69/69 (2026-08-03, corrida `20260803093503-7523`).** El rojo
      > del criterio 3 que apareció el 2026-08-03 —dos corridas seguidas en 3,3 s— **no era del
      > producto**: lo ponía el proxy de puertos de Docker Desktop renovando la conexión cada
      > 200 peticiones. Retirado el artefacto sólo en el entorno E2E, el peor caso del escenario
      > que fallaba cae de **3.357 a 1.577 ms** sin tocar consulta, índice ni umbral. Regla que
      > queda: antes de creerse los percentiles de cualquier gate en esta máquina, correr
      > `Invoke-E2E.ps1 -Suite sonda-transporte`. Análisis completo en
      > `docs/ai/diagnostico-pico-rc003-gate-f3.md`.
      > Pasan la semántica de las seis ramas, conteo = página, KPI = lista, los
      > **planes** (cada rama por su trigrama, ninguna tabla grande recorrida entera, y el
      > contraste del `OR` prohibido cayendo a `Seq Scan`), las tres guardas estáticas y los
      > **tres criterios** en que se partió el objetivo (§5): discriminante < 1.000 ms —medido
      > entre 48 y 232 ms—, no discriminante bajo RC-003 con su referencia sin texto como
      > evidencia, y paginación profunda bajo RC-003.
      > La corrida destapó **dos defectos reales, ya corregidos**: el **plan genérico** del
      > *prepared statement*, que duplicaba el coste y cuyo síntoma era que la llamada en frío
      > salía más rápida que el régimen; y los **joins que quedaron muertos** en el listado al
      > mudar el texto al `UNION`. El término selectivo pasó de **5.552 a 139 ms** de p95 y el
      > listado sin texto de oportunidades, de 1.043 a 371.
      > **Decidido: no se construye proyección materializada para visitas.** La única desviación
      > es un término que casa con el 100 % del banco, donde `Seq Scan` es el plan correcto y el
      > caso equivale funcionalmente a listar sin filtro; duplicar datos y sincronizarlos no se
      > justifica. La **paginación profunda** queda bajo RC-003 con una tarea posterior
      > registrada: sustituir `OFFSET` por cursor/keyset — no por una proyección.
      > **ClienteContactoDetail** es la bitácora agente-cliente: las interacciones de contexto
      > `CLIENTE` —las que no cuelgan de ninguna oportunidad— más las propuestas que sí llegaron a
      > serlo. Es la pantalla que justifica que `interaccion_comercial` sea polimórfica. Dos cosas
      > que conviene no volver a introducir: los **dos bloques fallan por separado** (solo el
      > cliente es fatal), y el **"agente visible" es un dato derivado** —el rol CLIENTE no guarda
      > agente asignado, así que se deduce de quién registró el último rastro y puede decir «no
      > asignado» para un cliente que otro agente sí atiende fuera de tu alcance—.
      > **Oportunidades** filtra por etapa y cuenta en SQL; el **alcance del BROKER es por
      > CAPTACIÓN**, no por agente supervisado (distinto de interacciones **a propósito**).
      > **OportunidadForm** sustituye los dos catálogos completos del Blazor por **dos selectores
      > que buscan en el servidor** (20 candidatos, `estado=A` en ambos) y atribuye la publicación
      > de origen cuando el local tiene anuncios. **OportunidadDetail** lleva la barra de etapas
      > —`A` cubre dos y avanza cuando ya hay algo registrado, que es lo único que el cable no
      > distingue— y el cierre por no continuidad con razón tipificada.
      > **En ninguna de las tres hay botón de "cerrar exitosa"**, y no es un olvido: el endpoint
      > existe y responde **400 siempre** porque ese cierre lo produce la cascada del contrato (F4).
      > Tampoco hay "crear solicitud" todavía: F4 no está migrada y la casa no ofrece enlaces a
      > pantallas que no existen.
      > **Visitas** implementa las cinco operaciones de agenda. Dos reglas que la pantalla hace
      > visibles antes del 400: `realizar` y el desenlace son **dos pasos** —el resultado exige
      > visita `R` y es **irrepetible**—, y un resultado que implica no continuidad **cierra la
      > oportunidad**, así que se exige la razón tipificada en el propio formulario. Salió de aquí
      > un fallo que conviene recordar: un `computed()` que lee `FormControl.value` **no es
      > reactivo** y dejaba el botón bloqueado con el motivo ya escrito; se resuelve reflejando el
      > formulario con `toSignal(valueChanges)`.
      > **Interacciones**: `grupo` **parte el universo en dos**, no filtra por contexto
      > (`PROPIETARIO` = prospección o captación; su complemento es el lado del cliente), y el
      > catálogo de `resultado` **depende del contexto**, así que el filtro se acota a la pestaña y
      > se limpia al cambiarla. `InteraccionForm` replica la allow-list por contexto y envía **solo
      > el id de su entidad** (el CHECK de la base exige exactamente una). `InteraccionDetail` deja
      > editar **solo `resultado` y `observaciones`**, que es lo único que toca el PUT: el resto se
      > muestra como dato fijo con el motivo a la vista, igual que la identidad en `ClienteForm`.
      > **ClienteDetail** es la **primera pantalla que consume la ficha por secciones de E3**, así
      > que fija el patrón para la del propietario. Lo que hay que entender antes de tocarla: la
      > carga inicial es **parcial**. Solo `requerimientos` viene resuelto; las demás secciones
      > llegan con `totalRecords: -1`, que es un **marcador de pendiente, no un total**. Cada
      > pestaña se pide al abrirla y se guarda; una sección legítimamente vacía trae `0` y tampoco
      > se vuelve a pedir, por eso la distinción va por el marcador y no por `items.length`.
      > Dos correcciones que salieron al verificar en navegador y conviene no volver a introducir:
      > las **métricas de la cabecera muestran un guion** —no un cero— mientras su sección no se
      > haya pedido, porque «0 oportunidades» y «aún no lo sé» son cosas distintas; y las columnas
      > opcionales se ocultan comparando también contra **`-`**, que es como el cable normaliza los
      > nulos descriptivos (el SPA usa `—`), o se dibujaban columnas enteras de guiones.
      > En la tabla solo se ofrece «Ver detalle» cuando la `ruta` del cable tiene pantalla en el SPA.
      > **Sus dos pendientes quedaron cerrados con `OportunidadForm` (2026-08-02)**: el **editor de
      > requerimientos** —alta, edición y el endpoint propio de estado, porque pausar/cerrar no es
      > parte del PUT— y el **panel de coincidencias** con su CTA *Presentar propiedades de
      > cartera*, que solo se ofrece si hay búsqueda activa (sin requerimiento `ACTIVO` el matching
      > no tiene contra qué comparar). Dos detalles del cable que la pantalla respeta: **ningún
      > límite del requerimiento es obligatorio** —un criterio sin dato cuenta como NO_APLICA, no
      > como incumplido—, y en `cliente → propiedades` el `id` de cada coincidencia es el de la
      > **captación**, que es justo lo que necesita el alta de oportunidad. El porcentaje se muestra
      > siempre con sus motivos, porque un 100 % significa «cumple todo **lo que se pudo evaluar**».
      > **ClienteForm** hace explícito lo que el legado dejaba a medias: en edición, **tipo de
      > persona, tipo de documento y número van bloqueados**, con el motivo a la vista. No es una
      > decisión de pantalla — `PUT /clientes/{id}` solo toca nombre, contacto, rubro,
      > consentimientos y estado, y **descarta el resto en silencio**—, así que dejarlos editables
      > prometería un cambio que el backend no hace. Se envían igual (`getRawValue` incluye los
      > deshabilitados) para que el PUT no los vea cambiar. Réplica cliente del `Personas.validar`
      > del backend —RUC 11 dígitos, DNI 8, carné y pasaporte libres— para no descubrir el 400 tras
      > escribir el formulario entero. Una persona jurídica fija RUC y ni siquiera dibuja el
      > selector de documento. Teléfono y correo siguen siendo obligatorios **por regla de
      > pantalla**, no del cable: el backend los acepta vacíos, pero un cliente sin forma de
      > contactarlo no sirve para la demanda. El **rubro es texto libre**, no un enum: `rubrosCon()`
      > ofrece el valor actual aunque no esté en la lista sugerida, o editar cambiaría en silencio
      > el rubro de un cliente venido de la v1. El estado no se toca aquí: se cambia con la baja o
      > la reactivación de la bandeja, que llevan confirmación.
      > **Clientes** es la primera bandeja que hereda el patrón de búsqueda por conjunto de
      > candidatos, y no por elegancia: su texto busca en nombre y documento (`persona`) y en el
      > rubro (`detalle_cliente`), otra vez **dos tablas**. `GET /clientes` gana cuatro filtros
      > **aditivos y opcionales** (`texto`, `tipoPersona`, `rubro`, `estado`) y estrena
      > `GET /clientes/resumen`, que cuenta en la base sobre el mismo conjunto y devuelve los
      > rubros para que el selector sea data-driven. El Blazor descargaba la cartera entera,
      > filtraba en memoria y derivaba de ahí los KPI y la lista de rubros: con paginación real eso
      > habría pasado a contar solo la página visible. V24 da su trigrama a los dos campos
      > buscables que faltaban. Divergencia deliberada respecto del legado: los **dos
      > consentimientos se muestran por separado** —el de contacto es del rol y el de uso de dato
      > es de la persona—, porque confundirlos en pantalla es lo que lleva a contactar a quien no
      > lo autorizó. La baja usa `cl-confirmacion` y la reactivación no inventa endpoint: es el PUT
      > con `estado='A'`, encapsulado en el servicio. Sin "Exportar CSV" todavía: se porta con
      > ClienteDetail, para no repetir el recorrido del conjunto filtrado en dos sitios.
- [x] **Cierre F4 (COMPLETA, 2026-08-02)**: ~~Solicitudes~~, ~~SolicitudesRevisar~~,
      ~~SolicitudForm~~, ~~SolicitudDetail~~ (expediente **+ cierre del alquiler**),
      ~~Documentos~~, ~~Evaluación~~ — y **`Cierre` no se porta: ya está migrada**, ver abajo.
      > **`Cierre.razor` del Blazor NO es del cierre de F4**: es "cerrar una captación" del
      > broker, y esa acción **ya vive en `CaptacionDetail`** desde el corte de F2. Portarla
      > como pantalla-silo obligaría a volver a elegir en un desplegable la captación que ya
      > se está mirando; es el mismo criterio con el que E5 quedó dentro del dashboard del
      > agente en vez de ser una página aparte. Lo que sí es el cierre de F4 —registrar el
      > contrato— vive en `SolicitudDetail`, igual que en el legado. De ahí que la vertical
      > cierre con **6 pantallas** y no con 7.
      > **Una extensión aditiva del backend** hizo falta, por la razón de siempre: la bandeja
      > filtra por estado, distrito, agente y texto, y los KPI no se pueden derivar de una
      > página. `GET /solicitudes` gana `idAgente`, `estado`, `distrito` y `texto` —omitidos
      > responde byte a byte como la v1, incluido el orden por id descendente— y estrena
      > `GET /solicitudes/resumen`, que cuenta los siete estados con un solo `group by` sobre el
      > MISMO conjunto que pagina la lista y devuelve los distritos y agentes disponibles para
      > que los dos selectores sean data-driven sin llamada extra. **V26** pone el trigrama del
      > código de la solicitud y el índice de recorrido por tenant.
      > **`estado=PENDIENTES` no es un estado**: es el cubo `{E, O}` de la cola del broker,
      > resuelto en la base como `GESTION` en prospecciones. Existe para que la cola salga en
      > **una** consulta paginada en vez de dos listados unidos en el cliente, y el resumen lo
      > devuelve ya sumado para que la pantalla no lo calcule por su cuenta y se desincronice.
      > **La bandeja hereda el patrón de búsqueda por conjunto de candidatos (§5)** con **cinco
      > ramas** —código de solicitud, código de oportunidad, dirección y distrito de la
      > propiedad, nombre del cliente y nombre del agente—, que es una más que ninguna anterior.
      > Su gate, `e2e-solicitudes-busqueda.ps1`, mide sobre **100.000 filas** con los tres
      > criterios de la §5 y añade dos comprobaciones propias: que el cubo `PENDIENTES` sea
      > exactamente `enRevision + observadas`, y que el resumen ignore los tres filtros que
      > devuelve. Dos tropiezos del propio script que conviene no repetir: `date - bigint` no
      > existe en PostgreSQL (`row_number()` es bigint, hay que castear a `int`), y en un
      > here-string `@"…"@` de PowerShell **la comilla invertida escapa** —un `` `n `` dentro de
      > un comentario SQL se convierte en salto de línea y rompe la sentencia entera—.
      > **Gate FIRMADO (2026-08-02): 48/48 sobre 100.000 filas**, con la máquina en reposo y el
      > banco retirado por completo. p95 del término **discriminante entre 32 y 147 ms** —el
      > caso que de verdad importa, porque es lo que la gente escribe—, término que casa con
      > todo 1.332, página profunda 1.445, resumen 444, y la llamada en frío bajo RC-003.
      > Pasan también los planes (cada rama por su trigrama, ningún `Seq Scan` de tabla grande),
      > el contraste del `OR` prohibido cayendo a `Seq Scan` sobre el mismo banco, y la guarda
      > estática que impide devolver el texto al JPQL de listado.
      > **Dos correcciones del propio gate hicieron falta antes de firmarlo, y las dos son
      > lecciones portables**: (1) el clasificador heredado de F3 juzga "discriminante" por el
      > `totalRecords` de la respuesta, y eso es incorrecto en cuanto hay otro filtro —
      > `texto=Calle&estado=PENDIENTES` devuelve 28.572 filas pero su texto casa con las
      > 100.000, así que el trabajo que mide es construir el conjunto entero: es criterio 2, no
      > 1—; ahora cada escenario declara su término y se clasifica por la medida de **ese
      > término sin otros filtros**; (2) el `/resumen` se juzga bajo RC-003 y no bajo el objetivo
      > de 1.000 ms, porque **dos de sus tres consultas recorren el alcance completo** por
      > convención —son las opciones de los selectores, no la búsqueda—. Y un aviso de
      > operación: **dos entornos E2E simultáneos contaminan las medidas** (una corrida con una
      > build de Angular en paralelo dio 1.381 ms donde en reposo da 444).
      > **`SolicitudesRevisar` es una pantalla aparte y no un filtro más**, con el mismo criterio
      > que `BandejaCaptaciones` frente a `Captaciones`: es el punto de entrada del trabajo del
      > broker y cada fila lleva a decidir. Lleva gate de rol aunque su listado no lo tenga: lo
      > que sí es de BROKER/ADMIN es `POST /evaluaciones`, la decisión a la que conduce. Que la
      > cola incluya las **observadas** —que en realidad esperan al agente— es la definición del
      > legado, replicada: es *su* observación y quiere verla hasta que se resuelva.
      > **`SolicitudForm` separa el alta del envío a evaluación**, donde el Blazor los encadenaba
      > en un botón subiendo documentos por el medio. La solicitud nace REGISTRADA, el agente
      > completa el expediente y desde ahí la envía. Encadenarlo escondía el estado intermedio y,
      > al fallar a mitad, dejaba una solicitud creada sin que el usuario supiera dónde estaba.
      > El selector busca en el servidor (20 candidatos, solo ABIERTAS: una con solicitud ya está
      > en `S` y el alta fallaría), y el código **no se manda** — lo genera el backend.
      > **`Documentos` distingue cargar de reenviar, y no es un matiz**: el broker puede observar
      > UN documento sin devolver la solicitud entera, que sigue en `E`. Si cargar dependiera de
      > poder reenviar, el agente no podría subsanarlo hasta que llegara la decisión completa. Se
      > carga mientras la solicitud no esté resuelta; se reenvía solo desde `G`/`O`. El checklist
      > dibuja **seis** filas, no las ocho del cable, y si hay varios documentos del mismo tipo
      > gana el último. La subida va por **octet-stream**: de las cuatro vías de la v1 el SPA usa
      > una sola —base64 infla un tercio y la subida por trozos existe por un bug del cliente
      > .NET que muere con el Blazor—.
      > **`Evaluación` no ofrece elegir el tipo** porque lo deriva el resultado, y bloquea
      > aprobar mientras haya un documento observado sin resolver: es regla de la casa, no del
      > backend, y evita aprobar una solicitud cuya propia revisión dijo que estaba mal. Ofrece
      > las dos salidas —validarlos u observar la solicitud entera—. El motivo obligatorio de
      > observar y rechazar se refleja con `toSignal(valueChanges)`, no con un `computed()` sobre
      > `FormControl.value`: ese fallo ya costó un botón bloqueado con el motivo escrito en F3.
      > **`SolicitudDetail` es donde se cierra el alquiler**, y el diálogo dice antes de
      > confirmar lo que la transacción provoca (contrato, comisión pendiente, oportunidad
      > exitosa, solicitud y captación cerradas, local no disponible). La comisión que muestra es
      > una **estimación** con la fórmula del backend, y si la captación no se pudo leer **no se
      > inventa un número**: el bloque cae solo con su aviso. Estrena en pantalla el
      > `desembolsoInicial` que `core/comision.ts` tenía escrito y sin usar desde F2: garantía y
      > adelanto son del **propietario**, la comisión de la **inmobiliaria**, y presentarlos como
      > un único número es lo que hace creer al cliente que paga tres meses al propietario.
      > El **404 de `GET /contratos/oportunidad/{id}` es el caso normal** mientras la operación
      > sigue viva; tratarlo como error llenaría de rojo todos los expedientes abiertos.
- [x] **Comercial/gestión** (5): ~~Dashboard~~ ✅, ~~Indicadores~~ ✅, ~~SeguimientoComercial~~ ✅,
      ~~Comisiones~~ ✅, ~~Reportes~~ ✅ (2026-08-04) — **bloque COMPLETO, y con él el SPA consume
      los 26 recursos del backend**. No necesitó backend nuevo: E4, F6 y F7 ya estaban cortados.
      > **El dashboard ES la home (`/`) y la bandeja de tareas vive dentro**, no en una
      > página-silo: es lo primero que ve el agente al entrar. Desaparecen del menú `Inicio`,
      > `Dashboard` y `Mis tareas` como módulos separados —tres entradas al mismo tablero eran
      > ruido— y `acceso.spec.ts` deja de exigir "Mis tareas" en el menú del agente: la reserva por
      > rol sigue, pero la impone el backend (`/tareas` es el único recurso sin acceso de ADMIN).
      > **Una sola llamada**: `GET /dashboard` compone indicadores + bandeja. Eso disuelve la
      > ambigüedad que el Blazor manejaba a mano —bandeja vacía podía ser "todo al día" o "falló la
      > llamada"—; aquí, si la respuesta llegó, la bandeja es autoritativa. Para BROKER y ADMIN
      > llega **vacía por contrato**, así que se les muestra su centro de control (focos derivados
      > de los indicadores) y no un "no hay tareas".
      > **Cancelar una tarea se explica antes de confirmar**: `CANCELADA` impide que el
      > reconciliador la vuelva a crear para esa entidad. No es "más tarde", y el diálogo lo dice.
      > **`core/navegacion-legado.ts` es la pieza transversal que faltaba**: alertas (`ruta`) y
      > tareas (`rutaResolver`) viajan con **rutas del Blazor** —están congeladas—, así que se
      > traducen en un solo sitio y con función pura. El caso raro: **las alertas de solicitud
      > llevan el id numérico** mientras la ficha del SPA enruta por código, así que el id se
      > resuelve con una llamada **al pulsar**, no al listar. Lo que no se sabe traducir devuelve
      > `null` y el aviso **se muestra sin enlace**, igual que el cable con los tipos que no enruta.
      > **Ninguna lleva "Exportar PDF"** (D-F5-1). `Reportes` es **RF-017**, el avance por
      > propiedad: su endpoint existe en la v1 pero **ningún `.razor` lo consume**, así que esta es
      > su primera pantalla. Exporta **CSV**, que es dato y no maquetación.
      > **Comisiones respeta dos rarezas del gate**: las tres operaciones son de **BROKER sin
      > ADMIN**, así que al administrador se le da la lectura sin botones; y
      > `montoAgente`/`montoEmpresa` **no le llegan al agente**, por lo que esas columnas no se
      > pintan vacías: no se pintan. Un movimiento parcial deja la comisión en `R`, no en `C`, y
      > por eso "Movimiento" y "Cerrar cobro" son acciones distintas.
      > **Reglas de gráfico que quedan fijadas para lo que venga**: conteos y porcentajes van en
      > **gráficos separados** (una sola escala por marco), la escala arranca en cero, con dos o
      > más series la leyenda es obligatoria y cada marca lleva su valor; la paleta de dos series
      > está **validada** contra daltonismo y contraste —el petróleo de marca **no pasa**, lee como
      > gris (croma 0,079), y el dorado se queda en 2,19:1—; los estados usan la paleta de estado
      > reservada y nunca el color como única señal.
- [x] **Identidad** (4 de 4) ✅ **2026-08-05**. `Perfil` y `AccesoDenegado` ya estaban;
      `CambiarContrasena` y `RecuperarAcceso` se cerraron con el Bloque 4. **Eran mocks en el
      Blazor** —la v1 no tiene endpoint de contraseña ni de recuperación— y por eso no se portaron
      antes: portarlas sin backend habría sido portar el engaño. Ahora tienen endpoints reales
      (`POST /perfil/contrasena`, `POST /auth/recuperacion`, `POST /auth/recuperacion/canje`) y
      **no son una copia de la v1**: son funcionalidad nueva con su fila en la matriz.
- [x] Pruebas del SPA (2026-08-02, cierre de F4): **385 en verde**. Las 52 nuevas cubren la
      bandeja de `Solicitudes` (los cuatro filtros al backend, el resumen que solo comparte el
      texto, el estado inventado que no viaja, el filtro por agente solo para quien supervisa,
      "Subsanar" solo del agente y sobre una observada, y el avance leído del contador del
      backend), `SolicitudesRevisar` (el cubo `PENDIENTES` por defecto, la vista inventada que cae
      al cubo, el defecto que no ensucia la URL y la fila que lleva a evaluar ESA solicitud),
      `SolicitudForm` (candidatos en el servidor y solo abiertas, la oportunidad fijada por la
      URL que no dispara búsqueda, el código que no se manda y el error que no pierde lo escrito),
      `DocumentosSolicitud` (seis filas y no ocho, el último documento de cada tipo, cargar ≠
      reenviar en revisión, la solicitud resuelta en solo lectura y el broker sin acciones),
      `EvaluacionSolicitud` (sin selector de tipo, motivo obligatorio al observar y rechazar,
      aprobación bloqueada con observados, "validar todos" solo con pendientes y el historial que
      cae sin impedir decidir) y `SolicitudDetail` (el 404 del contrato que no es error, quién
      puede cerrar, la estimación de comisión y su ausencia sin captación, el desembolso concepto
      por concepto, la fecha futura que bloquea y el cierre que recarga la solicitud).
      Las 333 anteriores (cierre de F3): las 60 que cubren
      `ClienteContactoDetail` (contexto CLIENTE en la petición, fallo independiente de cada bloque,
      agente visible derivado y solo lectura del broker), la bandeja de `Oportunidades` (filtros a
      SQL, resumen sin `estado`, código inventado en la URL que no viaja, KPI como atajo y ausencia
      del cierre exitoso), `OportunidadDetail` (etapas, razón obligatoria al cerrar y paneles que
      caen por separado), `Visitas` (desenlace solo sobre una realizada sin resultado, motivo
      obligatorio, razón exigida cuando el resultado cierra la oportunidad y el nivel de interés
      en cero que no viaja), `Interacciones` (la partición por `grupo`, el catálogo de resultado
      por pestaña sin duplicados y su limpieza al cambiar), `InteraccionForm` (allow-list por
      contexto y **solo el id de su entidad** en el request) y las 9 nuevas de `ClienteDetail`
      (CTA solo con búsqueda activa, coincidencias bajo demanda, distritos por nombre, requerimiento
      sin límites y el estado por su endpoint propio).
      Las 273 anteriores: 11 de `ClienteDetail` (carga inicial que no pide secciones, pendiente que no muestra contador ni cero, pestaña que se pide una sola vez, sección vacía que no se repite, pestaña de agentes solo para quien supervisa, enlace solo a pantallas migradas y error de sección que no tumba la ficha), 10 de `ClienteForm` (identidad bloqueada en edición y enviada igual, largo de documento por tipo, jurídica que fija RUC, rubro fuera del catálogo que no se pierde, error del backend sin perder lo escrito y guardado vedado a quien no es AGENTE), 11 de la bandeja de `Clientes` (los cuatro
      filtros al backend, el resumen que NO lleva estado porque lo cuenta, KPI y rubros del
      alcance completo, códigos inventados en la URL que no viajan, acciones solo del AGENTE,
      baja con confirmación, reactivación por PUT y los dos consentimientos separados) y una que
      cierra el hueco del formulario de captación que dejaba pasar un encargo de duración cero
      (`fin == inicio` pasaba el validador cruzado y el 400 llegaba del servidor). A las 57 del
      2026-07-30 se suman 10 de
      `LocalDetail`, 6 del servicio de prospecciones, 11 de `formato`, 10 del editor de
      publicaciones (la renta obligatoria y el `NaN` que `required`/`min` dejan pasar), 6 del
      servicio de captaciones, 23 de `FichaPropiedad` (permisos por rol, errores parciales,
      tope de fotos, ausencia del botón PDF y cómo se dice la comisión), 20 de `comision`
      (cálculo, lenguaje natural y desembolso con conceptos separados), 12 de
      `PropiedadesEquipo` (filtros al backend, KPI sobre inmuebles distintos, acceso por rol y
      el código de local que desambigua direcciones repetidas), 14 de `PropiedadesAlquiladas`
      (filtros y orden al backend, estado real del contrato, filtro por agente solo para quien
      supervisa, y la exportación que avisa en vez de recortar) y 5 de `csv` (escapado RFC 4180
      y BOM, que es lo que evita el clásico "el CSV se abre mal en Excel"), más 18 del corte de
      las dos bandejas F2 (paridad de filtros, recontacto, permisos, URL, estados vacíos/error,
      catálogo de personal y nombres de las dos vistas del inmueble), y 14 de sus dos pantallas
      siguientes (`ProspeccionDetail`, `CaptacionForm` y operaciones HTTP): transiciones por
      estado, solo lectura, descarte con motivo, alta+vínculo, subsanación, vigencia y paginación
      de locales; y 17 de `BandejaCaptaciones`, `CaptacionReview`, `CaptacionDetail` y sus nuevas
      operaciones HTTP: filtros del broker, decisiones con motivo, reasignación, cierre, permisos
      y ausencia deliberada de reportes PDF; más 8 del cierre F2: destinos válidos, motivo,
      refresh de lista/historial, URL, filtros, roles y rutas HTTP de reasignación; más 9 de la
      normalización final (condición económica tipada, moneda obligatoria, vigencias/cierre,
      advertencia de posibles duplicados y navegación Oferta/Proceso).
      > En esta máquina se ejecutan con Edge y el launcher `EdgeHeadlessCI`; `karma.conf.js`
      > detecta Edge cuando Chrome no está instalado:
      > `npm test -- --watch=false --browsers=EdgeHeadlessCI`.

---

## 4. Corte final (cuando backend y frontend estén completos)

- [ ] Verificación de paridad módulo a módulo contra el legado (la colección Postman de la v1 es
      el arnés natural). **Ojo: la paridad ya NO es 1:1.** Por D-F5-1, cinco endpoints de la v1
      —los 4 PDF de captación y `GET /indicadores/reporte/pdf`— se quedan **sin contraparte a
      propósito**, así que la comparación no puede exigir cobertura total. Hay que excluirlos
      explícitamente del arnés, no descubrirlos como fallo el día del corte.
- [ ] Backfill definitivo de datos y ventana de corte
- [ ] Apagar GlassFish + MySQL v1 y archivar `backend-java/` y `frontend-csharp/`
- [ ] Retirar la regla de **contrato congelado**: hasta aquí es intocable, y solo después se
      pueden arreglar los bugs que hoy se replican a propósito (el 400 fijo de `cierre-exitoso`,
      la moneda USD de la comisión, los 3 endpoints de subida que existen por un bug del cliente
      .NET, el `100` fijo del embudo y la "visita realizada" que no mira el estado — E4)
- [ ] Recién entonces: el esquema AI-ready y el multi-tenant real con RLS

---

## Resumen de una línea

~~F4~~ → ~~alertas/tareas~~ → ~~personas~~ → ~~reportes-propietario~~ →
~~ficha comercial~~ → ~~dashboard/indicadores/seguimiento~~ → ~~cierre del backend~~ →
**frontend Angular → nueva página de reportes → paridad/S3 → corte**.

El **backend está CERRADO**: 26/26 recursos, el ciclo del negocio cierra de punta a punta, la
matriz operación→rol está cubierta por test y los reportes PDF salieron del alcance (D-F5-1). Lo
único que queda en el camino crítico es el bloque grande de ~52 pantallas. El detalle y el porqué
están en la sección 0.
