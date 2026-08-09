# Mapa de estado — qué falta para terminar ControlLocal v2

**Fecha: 2026-08-01.** Este documento es la **foto para entender de un vistazo** dónde está el
proyecto, qué problemas aparecieron por el camino y qué queda por hacer.

- ¿Buscas el inventario detallado y el orden de trabajo? → `checklist-migracion.md`
- ¿Vas a tocar código de una vertical? → `backend-spring/README.md` + el contrato congelado de esa
  vertical
- ¿Quieres entender el proyecto en general? → este documento
- **¿Buscas el plan completo hasta producción (seguridad, operación, corte, multiempresa, IA)?**
  → **`plan-maestro-ruta-a-produccion.md`**: las 7 fases acordadas, cada punto etiquetado como
  hecho / parcial / planificado / pendiente / futuro, más lo que ya se cerró y no se reabre

---

## 1. El resumen en 30 segundos

Estamos reescribiendo ControlLocal de **Blazor + Java/GlassFish + MySQL** a
**Angular + Spring Boot + PostgreSQL**, sin apagar el sistema viejo: los dos conviven detrás del
mismo contrato REST, y cada módulo se "corta" cuando el nuevo responde igual que el viejo.

```
BACKEND    ████████████████████████████  26 de 26 recursos + API aditiva (100 %)
BASE DATOS ████████████████████████████  26 migraciones; modelo normalizado (100 %)
FRONTEND   ██████████████████████████░░  47 de ~52 pantallas
```

> **Actualizado el 2026-08-04 (tarde) — CAMBIA EL CAMINO CRÍTICO.** Se acordó un **plan maestro de
> 7 fases hasta producción**: `plan-maestro-ruta-a-produccion.md`, que pasa a ser **el índice de
> orden superior**. **E5 deja de ser lo siguiente**: van delante la **persistencia** (BLOQUE 1) y la
> **seguridad e identidad** (S0).
>
> Ya ejecutado y verificado en esta fecha: **volumen persistente del almacén** —hasta hoy, recrear
> el contenedor **borraba fotos y documentos**—, **respaldo automático de PostgreSQL**,
> **restauración verificada (26/26 comprobaciones)**, **perfiles `dev`/`test`/`prod`** y **arranque
> fallido** ante configuración productiva insegura. Reactor en **466 pruebas**. Evidencia literal:
> `backend-spring/operacion/EVIDENCIA.md`; guía operativa: `backend-spring/operacion/README.md`.
>
> Y **cambiar contraseña / recuperar acceso dejan de ser una decisión pendiente**: las desbloquea
> S0.3, que les da backend real.

> **Actualizado el 2026-08-04.** Se cerraron **Personas** (11 pantallas, el 03) y el **bloque
> comercial** (5 pantallas + la campana de avisos, el 04). Con eso el SPA **consume los 26
> recursos del backend** y la suite llega a **469 pruebas**. Lo único que queda del frontend son
> **cambiar contraseña** y **recuperar acceso**, y las dos **no tienen endpoint en la v1**: eran
> maquetas del Blazor.

> **Actualizado el 2026-08-02.** La migración de pantallas se reanudó y cerró de un tirón las dos
> verticales que faltaban del ciclo comercial: **F3 Demanda** (9 pantallas) y **F4 Cierre**
> (6 pantallas). Con eso el sistema nuevo recorre el proceso entero, de la prospección al
> contrato firmado. La normalización transversal (V15–V20) quedó cerrada antes: estados de un
> carácter, monedas obligatorias, condiciones económicas explícitas, vigencias/cierres, ciclo
> contractual, movimientos de comisión y disponibilidad de inmuebles coherentes.

**El backend está completo.** Cierra el ciclo entero del negocio —de captar un local a firmar el
contrato y cobrar la comisión—, no le queda ni un `TODO` pendiente y ahora también tiene los tres
agregadores que alimentan el dashboard y los reportes de pantalla.

**El frontend es, ahora sí, lo único grande que queda.** La aplicación Angular tiene login,
inicio, armazón y cuatro verticales completas: **Oferta** (6 pantallas), **Proceso F2** (8),
**Demanda F3** (9) y **Cierre F4** (6). Los transversales están cerrados y la suite suma
**385 pruebas**. Lo que falta es **gestión de personas** (propietarios, agentes, brokers,
asignaciones), **identidad/perfil** y el **bloque comercial** (dashboard, indicadores,
seguimiento, comisiones y la nueva página de reportes).

> **El backend ya no está del todo congelado en lo aditivo.** Seis operaciones nuevas y catorce
> filtros opcionales salieron de las pantallas: `/captaciones/propiedades-equipo` (+ `/resumen`),
> `/contratos/resumen`, `/clientes/resumen`, `/oportunidades/resumen`, `/visitas/resumen` y
> `/solicitudes/resumen`. El motivo es siempre el mismo: **hay cosas que no se pueden calcular
> sobre una página**. Deduplicar inmuebles con varias captaciones, sumar la comisión de toda la
> cartera o contar los siete estados de las solicitudes exige verlo entero — y el tope del cable
> es 100 filas. No tocan el contrato de la v1: omitidos, responde exactamente igual que antes.

---

## 2. Dónde estamos, con detalle

### Backend — lo que ya funciona

| Bloque | Qué cubre | Estado |
|---|---|---|
| Identidad y acceso | login, sesión, roles | ✅ verificado |
| Locales comerciales | alta, fotos, precios, publicaciones | ✅ verificado |
| Prospección y captación | el agente capta, el broker aprueba | ✅ verificado |
| Multi-empresa (tenant) | aislamiento de datos entre corredoras | ✅ verificado |
| Demanda | clientes, requerimientos, oportunidades, visitas | ✅ verificado |
| Cierre | solicitud → documentos → evaluación → contrato → comisión | ✅ verificado |
| Alertas y tareas | campana de avisos + bandeja del agente | ✅ verificado |
| Personas y perfil | propietarios, agentes, brokers, asignaciones, perfil | ✅ verificado |
| Reportes al propietario | avance derivado + reinicio de la tarea periódica | ✅ verificado |
| Ficha comercial | historial transversal de clientes y propietarios, 11 secciones | ✅ verificado |
| Dashboard, indicadores y seguimiento | KPIs, embudo, series, avance por propiedad y vista transversal | ✅ verificado |

"Verificado" significa que hay un script que lo prueba **contra la base de datos real**, no solo
tests unitarios. Son 8 scripts y suman **643 comprobaciones**.

### Backend — lo que falta

**Nada: el backend está cerrado.** 26 de 26 recursos REST cortados, y desde el 2026-07-30 también
la última deuda transversal —la **matriz operación→rol**, hoy en `matriz-operacion-rol.md` con
las 139 operaciones y su test de cobertura, que rompe el build si el documento y el código dejan
de coincidir—.

Los **5 endpoints PDF** de la v1 no cuentan como pendiente: quedaron **fuera del alcance** de la
migración (D-F5-1). No se portan, y la nueva funcionalidad de reportes se diseñará desde cero
junto con la página que la origina. Detalle en `decision-reportes-pdf-fuera-de-alcance.md`.

**E1 personas quedó cerrado el 2026-07-29** con 99/99 comprobaciones. Reutilizó el Party-Role de
V1 y añadió V10 para conservar el evento histórico de reasignación agente→broker.

**E2 reportes-propietario quedó cerrado el 2026-07-29** con 50/50 comprobaciones. Reutiliza V9,
deriva consultas/visitas/objeciones en SQL y reinicia la cadencia de 15 días de la tarea F7.

**E3 ficha comercial quedó cerrado el 2026-07-29** con 60/60 comprobaciones y 12 tests de
comportamiento. Corta los cuatro GET compartidos por clientes y propietarios, sus 11 secciones,
la carga parcial y la privacidad por equipo/tenant. No necesitó V11.

**E4 dashboard, indicadores y seguimiento quedó cerrado el 2026-07-29** con 115/115
comprobaciones y 44 tests de comportamiento, y **con él el backend entero**. Los agregadores
fueron al final a propósito —hacerlos antes obligaba a rehacerlos cada vez que se cortaba una
vertical— y la apuesta salió bien: al llegar aquí ya estaba migrado todo lo que agregan. Como
son agregados sobre una base con datos de ejemplo, su script no compara números absolutos: toma
una foto antes, crea un caso identificable y comprueba **cuánto se movió cada indicador**.

### Frontend — lo que falta

El sistema viejo tiene **58 pantallas**. La aplicación nueva tiene login, **dashboard** y armazón
(menú, sesión, campana de avisos, manejo de errores y transversales de archivos y formato) más
**seis bloques completos**: Oferta, Proceso, Demanda, Cierre, Personas y Comercial/gestión. El
inventario restante es de **2 pantallas**, agrupadas así:

| Grupo | Pantallas |
|---|---|
| Identidad y perfil | **2** — cambiar contraseña y recuperar acceso, las dos **sin endpoint en la v1** |
| Locales (oferta) | — **completa** |
| Personas (propietarios, agentes, brokers) | — **completa** (2026-08-03) |
| Prospección y captación | — **completa** |
| Demanda (clientes, oportunidades, visitas) | — **completa** |
| Cierre (solicitudes, documentos, evaluación) | — **completa** |
| Gestión (dashboard, indicadores, seguimiento, comisiones, reportes) | — **completa** (2026-08-04) |

> El bloque de Cierre cerró con **6** pantallas y no con las 7 del inventario original: la que
> el sistema viejo llama "Cierre" no cierra un alquiler, cierra una **captación**, y esa acción
> ya vive dentro del expediente de la captación desde el corte de Proceso.

La base transversal ya está construida: capa HTTP, filtros y tablas paginadas, archivos, estado
por rol y cierre de sesión por 401. Las pantallas nuevas deben reutilizarla.

---

## 3. Los problemas encontrados

Aquí está lo que de verdad conviene saber. Los agrupo por **qué tipo de problema son**, porque cada
tipo se resuelve de forma distinta.

### 3.1 Bugs del sistema viejo que copiamos a propósito

La regla del proyecto es que **mientras el sistema viejo siga vivo, el contrato no se toca**. Si el
sistema viejo hace algo raro, el nuevo lo hace igual — porque si no, las pantallas viejas se rompen.
Todos estos están replicados y anotados para arreglarlos **el día que se apague el legado**:

| # | El problema | Consecuencia real hoy |
|---|---|---|
| 1 | La comisión se calcula siempre en **dólares**, aunque todo lo demás opere en soles | El monto de la comisión sale en la moneda equivocada |
| 2 | La alerta *"Modificación comercial sensible"* viaja con un **tipo que no le corresponde** (`SOLICITUD_EVALUADA`), porque no existe uno que encaje | La alerta llega, pero mal clasificada |
| 3 | **La alerta de "captación nueva" casi nunca se emite**: el camino normal para crear una captación se salta el código que avisa | El broker **no recibe aviso** de las captaciones que le llegan a revisar |
| 4 | La pantalla que "cierra la oportunidad" **siempre da error 400** | Es intencional: el cierre lo produce el contrato, no ese botón. Confunde a quien lo lee sin contexto |
| 5 | Existen **3 formas distintas de subir un archivo**, creadas para esquivar un fallo del cliente .NET | Código duplicado que solo tiene sentido mientras viva el Blazor |
| 6 | La bandeja de tareas **corta en 10 y descarta el resto en silencio** | El agente no puede distinguir "tengo 10 tareas" de "tengo 40" |
| 7 | En el embudo, *"Con visita realizada"* **no mira el estado de la visita**: cuenta también las canceladas | El tramo del embudo exagera cuántas oportunidades llegaron a visita |
| 8 | El primer tramo del embudo muestra **100 %** aunque no haya ninguna oportunidad | Un embudo vacío se ve "al 100 %" |
| 9 | El dashboard manda **dos veces el mismo número** de captaciones por revisar (con dos nombres distintos) | Ninguna: la pantalla usa uno solo. Es peso muerto del contrato |
| 10 | Si en el periodo elegido no hubo ninguna prospección, los indicadores de seguimiento **caen a todo el historial** en vez de mostrar cero | El número de "disciplina comercial" no corresponde al periodo que el usuario eligió |
| 11 | En la vista transversal, las filas **sin fecha aparecen primero**, no al final | Lo más viejo o incompleto encabeza la lista |

> **Ninguno de estos es un error nuestro.** Están documentados uno por uno en los contratos
> congelados, con el sitio exacto donde se arreglan cuando llegue el momento.

### 3.2 Decisiones de seguridad ya cerradas

- El hueco de alcance al revisar documentos se cerró: Spring responde **403** fuera del equipo,
  una divergencia deliberada y documentada respecto de la v1.
- Los dos supuestos mensajes inventados se resolvieron al comprobar que el mapper legado ya
  respondía **409**; no queda una decisión bloqueante allí.
- D-20 sigue vigente: mientras conviva GlassFish, solo autentica `BROX_LEGACY`. El
  multi-tenant funcional y RLS llegan después del corte.

### 3.3 Deudas técnicas nuestras (esto sí es trabajo pendiente real)

| Deuda | Por qué importa |
|---|---|
| ~~**Los binarios se pierden al recrear el contenedor**~~ | ✅ **CERRADO el 2026-08-04**: volumen `controllocal_almacen` montado en ruta absoluta, verificado con `--force-recreate` y con `down`+`up` |
| ~~**No hay copias de seguridad**~~ | ✅ **CERRADO el 2026-08-04**: `operacion/respaldo.ps1` (checksum, manifiesto y retención) + `operacion/restaurar-verificar.ps1` (26 comprobaciones sobre base nueva) |
| **Los binarios no tienen copia propia** | `pg_dump` guarda las **claves**, no los archivos. Una restauración deja la base íntegra y **los documentos ausentes**. Es el pendiente inmediato del BLOQUE 9 |
| **Las copias viven en el mismo disco** | Con el servidor perdido se pierde también el respaldo |
| **6 de 29 services sin test de comportamiento propio** | El reactor tiene 466 pruebas y los ocho E2E están verdes, pero aún faltan suites dedicadas para cliente, coincidencia, organización, precio, publicación y requerimiento |
| **Falta el almacenamiento compartido** | Hoy es disco local con volumen: **correcto para una sola instancia**, insuficiente en cuanto haya dos (BLOQUE 9) |
| **Falta el traspaso real de datos** desde MySQL | Hoy la base nueva tiene datos de demostración. El traspaso definitivo (con el mapeo de identificadores viejos↔nuevos) está diseñado pero no ejecutado |
| **Aislamiento entre empresas a nivel aplicación**, no de base de datos | Funciona, pero lo refuerza el código. Lo robusto es activarlo en PostgreSQL (RLS) cuando se pase a multi-empresa real |
| **La reportería PDF quedó fuera del corte** | D-F5-1 retiró los Jasper legados; una futura página de reportes se diseñará desde cero después del SPA |
| **La cobertura Angular todavía debe crecer con cada pantalla** | Ya hay 240 pruebas verdes; cada migración añade contrato, permisos y estados de UI |

### 3.4 Trampas del entorno (cuestan horas si no se saben)

| Trampa | Cómo se sortea |
|---|---|
| **El servidor Java no arranca desde sesiones automatizadas** en esta máquina (falla un mecanismo interno de Windows) | Se levanta **en Docker** o desde IntelliJ |
| El `JAVA_HOME` del sistema apunta a una versión vieja de Java y **rompe la compilación** | Apuntarlo al JDK 21 antes de compilar |
| Tras cambiar la base de datos hay que **reempaquetar** antes de reiniciar | Si no, los cambios no se aplican |
| El login permite **10 intentos por minuto**; dos corridas seguidas de un script fallan | Esperar un minuto entre corridas |

---

## 4. Qué falta hacer, en orden

### ~~E0 — Tests y decisiones~~ ✅

### ~~E1 — Personas y perfil~~ ✅

Cinco recursos cortados, V10 aplicada, reactor 321/321 y E2E 99/99.

### ~~E2 — Reportes al propietario~~ ✅

Recurso cortado sobre V9, reactor 332/332 y E2E 50/50.

### ~~E3 — Ficha comercial~~ ✅

Cerrada con cuatro endpoints, 11 secciones distintas, reactor 344/344 y E2E 60/60.

### ~~E4 — Dashboard, indicadores y seguimiento~~ ✅

Cuatro endpoints cortados sin migración nueva, reactor 388/388 y E2E 115/115. Fueron al final
porque agregan todas las verticales anteriores. **Con esto el backend está completo.**

### E6 — Frontend Angular ← **en curso, y ya nada lo bloquea**

Los transversales de filtros, tablas, carga de archivos, visor Blob, estado por rol, 401,
formato de fechas/montos y primitivas de estilo están cerrados, y **cuatro verticales completas**
los reutilizan con **385 pruebas verdes**: Oferta, Proceso (F2), Demanda (F3) y Cierre (F4).
Con F4 el sistema nuevo recorre el ciclo entero: prospectar, captar, interesar a un cliente,
visitar, ofertar, evaluar el expediente y **cerrar el alquiler** —el contrato, la comisión y el
local que deja de estar disponible salen de esa única transacción—.
**Personas** se cerró el 2026-08-03 (11/11) y el **bloque comercial** el 2026-08-04: dashboard,
indicadores, seguimiento, comisiones y reportes, más la **campana de avisos**. Con eso el SPA
**consume los 26 recursos** y llega a **47 de ~52 pantallas**, con **469 pruebas verdes**.

Lo único que queda del frontend son **cambiar contraseña** y **recuperar acceso**, y ahí hay que
decidir antes de programar: **la v1 no tiene endpoint para ninguna de las dos** —las pantallas del
Blazor eran maquetas sin backend—, así que portarlas tal cual sería portar el engaño. O se diseñan
de cero con backend nuevo (fuera del contrato congelado) o se quedan fuera del corte.

**El camino crítico pasa a ser E5**: paridad módulo a módulo, almacén S3 real y el corte.

### E5 — Paridad, almacenamiento y corte

Comparar módulo a módulo, ejecutar el backfill real y retirar GlassFish/MySQL. Queda el almacén
S3 real (hoy solo disco, y ninguna pantalla nota la diferencia); **Jasper ya no está en esta
lista**: se retiró del alcance (D-F5-1).

Ojo con la paridad: **ya no es 1:1**. Cinco endpoints de la v1 se quedan sin contraparte a
propósito, así que la comparación módulo a módulo no puede exigir cobertura total.

Solo después del corte se levantan los comportamientos congelados y se activa multi-tenant real
con RLS.

> **E5 quedó detrás de E6 a propósito**: ni la paridad módulo a módulo ni el almacenamiento en S3
> bloquean una sola pantalla, y la paridad se comprueba mejor cuando hay un cliente nuevo que la
> ejercite. La única pieza de E5 que conviene no dejar para el final es la **matriz de permisos
> con su prueba de cobertura**, porque el SPA va a apoyarse en ella para decidir qué ve cada rol.

---

## 5. Cómo comprobar que algo funciona

```bash
docker compose -f backend-spring/docker-compose.yml up -d
```

Eso levanta la base de datos y el API. Después, cada vertical tiene su script de verificación:

| Script | Qué prueba | Última corrida (2026-07-29) |
|---|---|---|
| `e2e-v6.ps1` | captación + aislamiento entre empresas | 46/46 |
| `e2e-f3-demanda.ps1` | clientes, oportunidades, visitas | 89/89 |
| `e2e-f4-solicitud.ps1` | solicitud → contrato → comisión | 116/116 |
| `e2e-f6-f7-alertas-tareas.ps1` | alertas y bandeja de tareas | 68/68 |
| `e2e-personas.ps1` | propietarios, agentes, brokers, asignaciones, perfil y tenancy | 99/99 |
| `e2e-reportes-propietario.ps1` | preview, alta, alcance, tenancy y reinicio F7 | 50/50 |
| `e2e-ficha-comercial.ps1` | 11 secciones, aliases, privacidad, alcance y tenancy | 60/60 |
| `e2e-e4-dashboard.ps1` | indicadores, avance, dashboard, seguimiento, alcance y tenancy | 115/115 |

Todos viven en `backend-spring/verificacion/` y se corren con
`powershell -File backend-spring/verificacion/<script>.ps1`. **Los ocho suman 643
comprobaciones y están en verde.**

Para el backend completo: `mvn clean install` (**403 pruebas**, incluidas **4 clases de control
automático** que **rompen la compilación** si alguien salta una regla estructural: capas,
auditoría de transiciones, tenancy y —desde el 2026-07-30— la matriz operación→rol).

> **Cuidado al encadenar corridas**: el login admite **10 intentos por minuto** y cada script hace
> tres o cuatro. Dos seguidos fallan; hay que esperar un minuto entre uno y otro.

---

## 6. La foto en una frase

**El backend está al 88 % y es sólido; E3 quedó cerrado.** Faltan tres agregadores; después viene
la paridad/corte y el bloque grande de frontend. La
decisión vigente sigue siendo **terminar el backend primero**.
