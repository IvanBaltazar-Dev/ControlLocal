# D-F5-1 — Los reportes PDF quedan fuera del alcance de la migración

**Decisión (2026-07-30).** Los cinco endpoints PDF de JasperReports de la v1 **no se portan**. No se
replica su layout ni su contenido, y **no se elige todavía** la tecnología de reemplazo. La nueva
funcionalidad de reportes PDF se diseñará después, desde cero, a partir de la nueva página de
reportes.

Con esto **el backend v2 queda cerrado**: 26 de 26 recursos REST cortados, la matriz operación→rol
cubierta por test, y lo que falta declarado en vez de pendiente.

Esto **deroga** la etapa "F5/F8 reportes Jasper" del plan: ya no es un paso diferido que alguien
tenga que retomar, es trabajo retirado.

---

## Por qué

Los tres motivos, en orden de peso:

1. **Portar Jasper era portar una decisión de 2024, no una necesidad.** Las cinco plantillas son
   layouts de **coordenadas absolutas** (`x`/`y`/`width` píxel a píxel) que solo se editan cómodamente
   con Jaspersoft Studio. Replicarlas en la v2 significaba heredar esa restricción justo cuando la
   aplicación cambia de cliente.
2. **No se sabe todavía qué tiene que imprimir.** La página de reportes del SPA está sin diseñar. Un
   PDF es la exportación de una pantalla; elegir el motor antes de saber qué muestra la pantalla es
   elegir a ciegas. El orden correcto es: **primero la página, después el PDF**.
3. **No bloquean a nadie.** Es la única pieza del backend de la que no depende ningún otro módulo.
   Retirarla deja el camino crítico libre para E6 (frontend) sin deuda oculta.

---

## Qué deja de existir en la v2

Cinco endpoints, todos operativos en la v1 y consumidos hoy por el Blazor:

| Endpoint v1 | Plantilla | Qué produce |
|---|---|---|
| `GET /captaciones/{codigo}/contrato-exclusividad/pdf` | `contrato_exclusividad.jasper` | El encargo de exclusividad para firmar con el propietario. |
| `GET /captaciones/{codigo}/ficha-captacion/pdf` | `ficha_captacion.jasper` | Resumen interno de la captación (condiciones comerciales + propiedad + observaciones). |
| `GET /captaciones/{codigo}/ficha-propiedad/pdf` | `ficha_propiedad.jasper` | La ficha comercial del local para compartir con cliente o propietario. Es la más rica: ~30 campos, histórico de precios y conteo de fotos. |
| `GET /captaciones/{codigo}/reportes-propietario/{idReporte}/pdf` | `reporte_propietario.jasper` | El reporte periódico de avance que se entrega al propietario, con gráfico de consultas/visitas. |
| `GET /indicadores/reporte/pdf` | `reporte_indicadores.jasper` | El panel de indicadores exportado. El nombre del archivo cambia por rol: `reporte_global` / `reporte_equipo` / `reporte_agente`. |

Y con ellos, **diez clases y diez archivos de plantilla** de `backend-java/controllocal-rest/` que no
tienen contraparte v2: `reports/JasperPdfService`, `reports/ReportTemplateCompiler`,
`reports/CaptacionJasperMapper`, `reports/IndicadoresJasperMapper`, `reports/ReportCharts`
(415 líneas de Java2D dibujando barras, donas, embudos y series), los cinco `*ReporteDto`/`*JasperDto`,
los cinco `.jrxml` con sus `.jasper` compilados de `resources/reports/`, `JasperPdfServiceTest` y la
dependencia `jasperreports` del `pom.xml`.

**El legado no se toca**: `backend-java/` sigue sirviendo esos PDF mientras viva. Esto es una
decisión sobre qué **no** se construye en la v2, no un borrado.

---

## Consecuencias que hay que tener presentes

### 1. La paridad del corte deja de ser 1:1

La verificación módulo a módulo contra el legado —la que usa la colección Postman de la v1 como
arnés— ya **no puede exigir cobertura total**: cinco endpoints de la v1 se quedan sin contraparte
**a propósito**. Tiene que ser una línea explícita del checklist de corte, no una sorpresa el día de
apagar GlassFish.

### 2. Tres pantallas del SPA no deben llevar botón "Exportar PDF"

Estas exportan PDF hoy en el Blazor. Sus equivalentes Angular salen **sin** ese botón, o E6 construye
botones muertos:

| Pantalla Blazor | Botones a no replicar |
|---|---|
| `Components/Pages/CaptacionDetail.razor` | **Tres**: ficha de captación, contrato de exclusividad y el PDF por cada reporte de avance de la lista. |
| `Components/Pages/FichaPropiedad.razor` | "Exportar PDF" de la cabecera, más la tarjeta que lo publicita al pie ("Exporta la ficha a PDF para compartirla…"). |
| `Components/Pages/Reportes.razor` | "Exportar PDF" del panel de indicadores. |

El resto de la exportación **no** se ve afectado: `Services/Exportacion.cs` también baja CSV y eso no
depende del backend.

### 3. `/captaciones/{id}/reportes-propietario` sigue completo

El recurso REST de E2 está cortado y verificado (50/50). Lo único que le faltaba era su PDF, y ahora
no le falta: no forma parte del alcance. La captura del avance —consultas, visitas y objeciones
agregadas en SQL— es información del sistema y sigue disponible por JSON.

---

## Qué hay que definir antes de elegir tecnología

En este orden, porque cada respuesta condiciona la siguiente:

1. **Qué reportes existen.** Los cinco de la v1 no son un requisito: son lo que se construyó. Puede
   que el nuevo conjunto sea otro (por ejemplo, uno solo por captación que reemplace a tres).
2. **Qué datos muestra cada uno**, y si son los mismos que ya se ven en pantalla o un recorte.
3. **Quién los consume y cómo llegan.** No es lo mismo un PDF que el agente descarga que uno que
   **el propietario recibe** —el reporte de avance sale del sistema y llega a alguien que no tiene
   usuario—. Eso decide cuánta calidad de maquetación hace falta.
4. **Qué fidelidad visual se exige.** Es lo que separa un motor en proceso de uno con navegador.
5. **Dónde se genera**: servidor (un endpoint más, autenticado y con alcance) o cliente (el SPA
   imprime lo que ya tiene en memoria). Es una decisión de arquitectura, no de librería.

### Dos restricciones que ya se conocen y conviene no redescubrir

- **El contenedor no tiene fuentes.** El API corre sobre `eclipse-temurin:21-jre-alpine`
  (`backend-spring/docker-compose.yml`), sin fontconfig ni fuentes del sistema. Cualquier motor que
  dependa de `java.awt` para dibujar texto o gráficos necesita **fuentes embebidas** o un contenedor
  distinto. Es justamente donde el Jasper de la v1 —que dibuja sus gráficos con `Graphics2D`— habría
  dado problemas al migrar tal cual.
- **La capa web no ve el dominio.** `ArquitecturaCapasTest` rompe el build si `com.controllocal.web`
  toca `domain` o `persistence`. Los mappers de la v1 leen entidades directamente
  (`Captacion`, `LocalComercial`, `Propietario`), y **eso no es portable**: cualquier generador de
  reportes en la v2 tiene que componer desde **DTOs de service**.

---

## Lo que ya está resuelto para cuando se retome

Un hallazgo útil de la exploración que motivó esta decisión: **el contenido equivalente de los cinco
reportes es alcanzable hoy desde los DTOs de service, sin una sola consulta nueva.**

| Reporte | De dónde saldrían sus datos en la v2 |
|---|---|
| Contrato de exclusividad · Ficha de captación | `CaptacionService.FichaCaptacion` cubre todo **menos `precioReferencial`**, que está en `LocalComercialService.FichaLocal`. |
| Ficha de propiedad | `LocalComercialService.FichaLocal` (los ~25 campos del local) + `PropietarioService.FichaPropietario` (documento, teléfono, correo) + `PrecioLocalService.listarPorLocal` + `LocalComercialService.listarFotos`. |
| Reporte al propietario | `ReportePropietarioService.FichaReporte` + la captación. |
| Indicadores | `IndicadorService.resumen`, que es exactamente el JSON que ya alimenta `/indicadores/resumen`. |

El alcance tampoco habría que inventarlo: los cuatro reportes de captación se resolvían con el mismo
403/404 que `GET /captaciones/codigo/{codigo}`, que ya está cortado y verificado.

Y si se quisiera conservar el **sobre** del cable v1 (por si algún día se reactivan esas rutas):
`application/pdf` + `Content-Disposition: attachment; filename="…"`, con el nombre saneado
reemplazando `[^A-Za-z0-9._-]` por `_`.

---

## Dónde queda registrado

- `docs/ai/checklist-migracion.md` — el paso "Reportes Jasper (F5/F8)" desaparece del orden de
  trabajo; el §4 de corte final gana la línea de paridad no-1:1.
- `backend-spring/README.md` — la fila "F5/F8 reportes Jasper · diferidos" de la tabla de estado.
- `docs/ai/mapa-estado-y-pendientes.md`, `contrato-congelado-f2-prospeccion-captacion.md`,
  `contrato-congelado-e2-reportes-propietario.md` y
  `contrato-congelado-e4-dashboard-indicadores-seguimiento.md` — donde decían "diferido a F5/F8".
- `CLAUDE.md`, `AGENTS.md`, `estado-actual-control-local.md` e `inventario-frontend-blazor.md`
  **no cambian**: describen el legado, y el legado sí tiene Jasper.
