# `docs/ai/` — qué es autoridad viva y qué es historia

**Este índice no decide nada.** Dice **dónde** está escrita cada decisión y
**cuál** de estos documentos gobierna código hoy. La autoridad de una regla es
siempre el documento que la decide, nunca esta página.

**Medido el 2026-09-05** contra el árbol (`feat/brox-next`, `1702343`), el
catálogo vivo de `controllocal_dev` y el código. Cierra `N41`.

---

## 1. La regla, en una línea

`AGENTS.md`, bullet de `docs/ai/`:

> Una **decisión VIGENTE que gobierna código viaja**. Un clon que no la lleva
> deja la regla sin autoridad escrita, y al gate que la exige sin nada a lo que
> apuntar. **Historia, auditoría y evidencia fechada pueden quedar ignoradas**:
> explican el *porqué*, no deciden. Y **`pendientes-brox.md` es inventario,
> nunca autoridad**.

`docs/` está ignorado con lista blanca porque el repositorio es **público**.
Versionar un documento es **publicarlo**: la lista blanca es, además de una
decisión técnica, una decisión de divulgación, y esa la toma el titular.

---

## 2. Las 17 decisiones — clasificación

**La cifra cambió y hay que decirlo**: la ficha `N41` (2026-09-02) hablaba de
**21** documentos `decision-*.md`, de los que viajaban 3. El 2026-09-05, al
empezar, en el árbol había **16**, de los que viajaban 3. La diferencia no se
explica con el historial —`docs/` está ignorado, así que git no vio ni la
creación ni el borrado de ninguno—, y **no se inventa** una procedencia: se mide
lo que hay. Dos de los que faltan sí están nombrados por otros textos y por
`AGENTS.md` (`decision-contrato-v2-descongelado.md`,
`decision-reportes-pdf-fuera-de-alcance.md`); de los otros tres no queda ni el
nombre. Ver §4.

**Y son 17 al terminar el día**: `decision-oferta-y-cierre-de-venta.md` se
escribió mientras se clasificaba, en el corte `WP-A`. Se dice porque es la
prueba de que **este índice envejece si nadie lo mantiene**: una decisión nueva
que gobierna código y que nadie añada a la lista blanca **no viaja**, y no hay
gate que lo cace (ver §6).

> **Resuelto el 2026-09-05**: la decisión de la oferta se renumeró a `D-N-1…D-N-5` («N» de
> Negociación); `D-E3-1` vuelve a nombrar una sola cosa, la navegación de
> `decision-sidebar-brox.md`. La correspondencia con los códigos antiguos está en la
> cabecera de `decision-oferta-y-cierre-de-venta.md`.

Criterio aplicado a cada uno, en este orden:
1. ¿la regla que decide **sigue siendo la que el código hace**? (comprobado
   contra gates, clases y migraciones que cita);
2. ¿hay código, gate o prueba que **apunte** al documento?

| Documento | Código | Estado | Por qué |
|---|---|---|---|
| `decision-autoridad-de-edicion-de-la-propiedad.md` | D-P0-1 | **VIGENTE · ya viaja** | Autoridad de escritura de propiedad y encargo; `AutoridadDeLaPropiedadTest#laAutoridadQueGobiernaViajaConElCodigo` **lee el fichero** y rompe el build si falta. `V87`–`V89` |
| `decision-brox-intelligence-alcances-y-frontera.md` | D-I-1 | **VIGENTE · ya viaja** | Fija qué información puede salir de la operación interna; sin código de Intelligence todavía, condiciona cada salida nueva. Lo lee el mismo gate |
| `decision-estados-sin-productor.md` | D-B7-1 | **VIGENTE · ya viaja** | `EstadosSinProductorTest` **parsea su tabla §1** contra el vocabulario real |
| `decision-frontera-brox-core-web-kairos.md` | D-A-1 | **VIGENTE · debe viajar** | Los cuatro nombres y qué decide cada pieza. Su §6 nombra los gates que la protegen —`FronteraKairosTest` y `FronteraDeAutoridadEnElSpaTest`—, y el segundo cita `D-A-1` tres veces (49 citas de `D-A-1` en todo el código) |
| `decision-modelo-universal-propiedad-operacion.md` | D-E4-1 | **VIGENTE · debe viajar** | El modelo Propiedad × Operación que defiende el Core; su gate ejecutable es `docs/ai/modelo/gate-modelo-universal.js`, que **sí** viaja (59 citas) |
| `decision-motor-de-registro.md` | D-E4-2 | **VIGENTE · debe viajar** | Una sola máquina de preguntas servida por el Core: hoy es `GuionRegistroPropiedad`. El documento **ya declara** que su maqueta `motor-captura.js` ilustra pero no gobierna (20 citas) |
| `decision-autoridad-de-cada-dato.md` | D-E4-3 | **VIGENTE · debe viajar** | Exactamente una autoridad por clave, y la regla del SUJETO (§9, `V73`/`V74`). La vigila `UnSoloLectorPorSujetoTest`, que la cita por su código (93 citas de `D-E4-3`, la decisión más citada del repositorio después de P0) |
| `decision-4p-modelo-de-procedencia.md` | D-4P-1 | **VIGENTE · debe viajar** | El modelo de procedencia del dato gobernado: `V83`, `RastroValorGobernado`, `ProcedenciaDelValor`, `ProcedenciaDelValorIntegrationTest` |
| `decision-estado-ocupacion-en-los-siete.md` | D-C5-1 | **VIGENTE · debe viajar** | Fija el vocabulario de `estado_ocupacion` y su aplicabilidad en los siete tipos; es lo que sembró `V84` y lo que el contrato-dato compara contra el Core (15 citas) |
| `decision-kairos-contrato-de-acciones.md` | D-K-1 | **VIGENTE · debe viajar** | Qué puede ejecutar KAIROS, con qué permisos y qué rastro deja. Ya hay `kairos-service/` y `FronteraKairosTest`; la distinción `Procedencia` ≠ `OrigenIncorporacion` sale de aquí |
| `decision-inicio-foco-y-resolucion.md` | D-E2-1 | **VIGENTE · debe viajar** | La composición del Inicio y los cuatro KPI. `PoliticaUnicaTest` exige los nombres «letra por letra» citando **D-E2-1 §6.2** (77 citas) |
| `decision-indicadores-comerciales.md` | D-E2-2 | **VIGENTE · debe viajar** | Las definiciones de los cuatro KPI y la lectura por rol; el mismo gate cita **D-E2-2 §1** (49 citas) |
| `decision-sidebar-brox.md` | D-E3-1 | **VIGENTE · debe viajar** | La taxonomía del menú; `frontend-angular/src/app/core/auth/acceso.spec.ts` la fija contra este documento |
| `decision-el-broker-tiene-sus-propios-asuntos.md` | D-E2-5 | **VIGENTE · debe viajar** | La única excepción de acceso del sistema —la bandeja no admite ADMIN— vive en `TareasController` y la mide `FocoDelBrokerIntegrationTest` (16 citas) |
| `decision-alta-de-propietario.md` | D-E2-3 | **VIGENTE · debe viajar** | Dónde se crea un propietario y por qué el alta no se hace desde el catálogo. Lo consume **KAIROS**: `InterpreteDeterminista` cita §3.1.2 dos veces |
| `decision-presupuesto-de-estilos-de-componente.md` | — | **VIGENTE · debe viajar** | Es la razón escrita del techo `anyComponentStyle: 16 kB` de `frontend-angular/angular.json`, y `AGENTS.md` —que sí viaja— remite a él por su ruta |
| `decision-oferta-y-cierre-de-venta.md` | D-N-1…D-N-5 | **VIGENTE · debe viajar** | **Apareció el 2026-09-05, mientras se escribía este índice**: es la decisión de `WP-A` (la mesa de negociación y el cierre de una venta). Entra por el mismo criterio y no por ser nueva — `V90`, `Oferta`, `ContratoCompraventa`, `OfertasController` y **ocho filas nuevas** de `matriz-operacion-rol.md` la citan—, y además es el **sustituto** del retirado `decision-hito-oferta-de-demanda.md` (§4) |

**Resultado: 17 VIGENTE, 0 HISTORIA.** Que ninguno sea historia tiene
explicación: los superados ya se borraron.

**Y no es «versionar todo `docs/`». Medido el 2026-09-05 con
`git check-ignore --stdin --no-index`** sobre los **55** ficheros de `docs/ai/`:
viajarían **25** —las 17 decisiones, los 6 artefactos que un test lee
(`matriz-codigos-estado.md`, `matriz-operacion-rol.md`,
`catalogo-productores-canonico.md`, `modelo/*.js` —que son tres— y
`prototipos/nucleo-brox.js`) y este índice— y siguen **ignorados 30**: encargos,
auditorías, borradores, diagnósticos, maquetas, el volcado del catálogo, el
North Star y el inventario. **Hoy, en el índice de git, hay 10.**

> **La cifra de arriba envejece sola, y hay que decirlo**: cada decisión nueva
> que gobierne código la mueve. No se fija ningún número en ningún gate por esa
> razón —un censo congelado se rompe al avanzar—, y la forma de recontarlo es el
> comando, no esta línea:
> `find docs/ai -type f | git check-ignore --stdin --no-index | wc -l` da los
> ignorados, con control positivo sobre `docs/ai/north-star-brox.md`.

---

## 3. Lo que NO viaja, y por qué

| Qué | Por qué se queda fuera |
|---|---|
| `north-star-brox.md` | Es el marco estratégico —la Red Útil Activa y el *moat*—. No decide una regla de código: dice contra qué se mide un avance. Publicarlo es una decisión de negocio, no de reproducibilidad |
| `pendientes-brox.md` | **Inventario, nunca autoridad** (`AGENTS.md`). Una regla que sólo viva aquí no obliga a nadie: su sitio es la decisión que la posee |
| `encargo-*.md`, `borrador-*.md` | Encargos de sesión ya ejecutados: explican **cómo se llegó**, no qué manda |
| `auditoria-*.md`, `diagnostico-*.md` | Medición fechada. Se conserva por el *porqué*; un clon reproduce el cierre sin ella |
| `i0-industrializacion-brox.md` | Encargo abierto, no autoridad; su propia cabecera lo dice |
| `estado-backend-para-el-inicio.md`, `mapa-pantalla-dominio-backend.md`, `traspaso-inicio-a-angular.md` | Estado medido de una tanda cerrada |
| `prototipos/*.html`, `prototipos/*.mjs`, `prototipos/pruebas-nucleo.js`, `prototipos/README.md` | Maquetas. La excepción es `prototipos/nucleo-brox.js`, que **sí** viaja porque `PoliticaUnicaTest` lo lee |
| `modelo/catalogo-core.json` | **Volcado del catálogo**: una lectura fechada de una base viva, o sea evidencia. Versionarlo crearía la segunda autoridad que el propio gate existe para impedir. Se regenera (§5) |
| `backend-spring/verificacion/evidencia/` | 2,4 MB de historia fechada. No hace falta para correr nada |

### 3 bis · Un documento que viaja **cita** documentos que no viajan, y es a propósito

**Medido el 2026-09-05**: de los 17 ficheros que viajarían, **11** contienen en
total **16** punteros a documentos de `docs/ai/` que se quedan ignorados. En un
clon esos punteros no llevan a ningún sitio, así que hay que decir por qué no es
la avería que `N26` y `N41` existen para evitar.

Los 16 apuntan, **sin excepción**, a *porqué*: encargos ejecutados
(`encargo-4p-…`), auditorías y diagnósticos (`auditoria-profundidad-inmobiliaria`,
`auditoria-ui-brox`, `diagnostico-e2-6-…`), estado medido de una tanda cerrada
(`estado-backend-para-el-inicio`, `mapa-pantalla-dominio-backend`,
`traspaso-inicio-a-angular`), el marco (`north-star-brox`), el encargo abierto de
I0 y el inventario (`pendientes-brox`). **Ninguno es la autoridad de una regla**:
la regla está escrita en el documento que viaja, y por eso el clon la puede
cumplir y el gate la puede exigir. Lo que el clon pierde es el relato de cómo se
llegó, que es exactamente lo que `AGENTS.md` autoriza a dejar fuera.

**Dónde sí sería avería**: si una decisión que viaja dijera «la regla está en
`pendientes-brox.md`» o «según `auditoria-…`, se hace así». Eso no es un puntero
al porqué, es autoridad delegada a un fichero ausente, y entonces la regla se
mueve al documento que viaja. Es la misma comprobación que hizo `F3` sobre las
cinco decisiones de P0.

---

## 4. Documentos citados que ya no existen

Cada mención a uno de éstos quedó anotada en su sitio con
`` `nombre.md` (retirado; ver `docs/ai/README.md`) `` o con un `~~tachado~~`.
Aquí está a qué apunta ahora cada asunto:

| Documento retirado | Lo vigente de ese asunto |
|---|---|
| `mapa-ejecucion-brox.md` | No hay sustituto documental. El estado y el siguiente paso los fija **CONTROL** con el corte en curso; lo que queda se inventaría en `pendientes-brox.md`. `AGENTS.md` ya contempla su ausencia y dice que no bloquea el trabajo |
| `checklist-captura-moat-e-inteligencia-inmobiliaria.md` | `pendientes-brox.md` §2 (profundidad inmobiliaria) |
| `decision-contrato-v2-descongelado.md` | `AGENTS.md`, bullet «THE CONTRACT IS UNFROZEN (2026-08-09)»: cada cambio viaja con sus pruebas, y el contrato ejecutable son OpenAPI y los tests |
| `decision-reportes-pdf-fuera-de-alcance.md` (D-F5-1) | `AGENTS.md` (último bullet del stack) y `pendientes-brox.md` §8: los 5 endpoints Jasper no se portan y no hay tecnología elegida |
| `decision-lenguaje-natural-de-negocio.md` | El rótulo de cada dato lo dicta el catálogo del Core (`catalogo_atributo.rotulo`, `V72`), no la interfaz |
| `inventario-umbrales-de-dominio.md` | `service/soporte/PoliticaComercial`: el único sitio donde se decide qué significa un número. Su javadoc cita el inventario retirado y cuenta por qué existe |
| `arquitectura-multitenancy-colaboracion.md` | El discriminador de organización que `ArquitecturaTenancyTest` exige a toda entidad privada (`V6`, ampliado por `V32`). La red colaborativa (MLS) **sigue sin implementar** |
| `decision-modelos-de-comision.md`, `diccionario-modelo-economico.md` | `CondicionEconomica` (`tipo_comision`, `base_calculo`, `valor_comision`, `tratamiento_igv`) y `comision_movimiento` |
| `diagnostico-estados-valores-economicos-y-fixtures.md` | Su §2.1 la superó `decision-estados-sin-productor.md`, que es la autoridad viva y la que lee el gate |
| `catalogo-productores-estado.md` | El estado lo imprime `CatalogoProductoresTest` en cada build; la tabla canónica es `catalogo-productores-canonico.md` |
| `decision-hito-oferta-de-demanda.md` | `decision-oferta-y-cierre-de-venta.md` (D-N-1…D-N-5), que escribe el corte de la negociación y el cierre de venta. Es donde se resuelven las tres cuestiones que `pendientes-brox.md` §1 daba por abiertas: contra qué precio se congela la referencia de la oferta, dónde vive el hito `O` sin mezclar dos mesas del mismo inmueble y dónde consta lo declarado |
| `contrato-local-form.md`, `modelo-herencia-y-generalizacion.md`, `seguridad-no-leer.md` | Retirados de `docs/ai/`. Los dos primeros están registrados como eliminados en `pendientes-brox.md` §9 |

**Las de `pendientes-brox.md` quedaron anotadas casi todas.** Medido el
2026-09-05: **13** líneas de ese documento citan un documento retirado, **11**
llevan ya su `~~tachado~~` con el destino vivo —§1 (la oferta), §5
(multi-tenancy), §8 (PDF y umbrales), §9.1, §9.3 y §9.4— y **2 no**, por una
razón que hay que decir en vez de disimular: **están en la cabecera del
documento y el encargo de este paquete prohíbe tocarla**. Son las dos de la
frase «este documento no sustituye a nadie», que sigue diciendo que
`mapa-ejecucion-brox.md` «sigue siendo la portada» y
`checklist-captura-moat-e-inteligencia-inmobiliaria.md` «los requisitos de
cierre». **Las dos afirmaciones son falsas hoy** —§9.4 del mismo documento ya lo
dice—, así que el inventario se contradice consigo mismo en su primera página
hasta que alguien con permiso para editar esa cabecera lo arregle.

**Y una queda sin anotar y no es de este paquete**, porque otro agente escribe
en ese fichero y dos agentes no editan el mismo texto a la vez:
`matriz-operacion-rol.md:384` → `decision-reportes-pdf-fuera-de-alcance.md`.

Y **`AGENTS.md` cita 14 documentos que ya no existen**, medido el 2026-09-05:
`mapa-ejecucion-brox.md` (éste sí contemplado — dice «when it exists»),
`checklist-captura-moat-e-inteligencia-inmobiliaria.md`,
`decision-contrato-v2-descongelado.md`, `decision-reportes-pdf-fuera-de-alcance.md`,
`seguridad-no-leer.md`, la evidencia `2026-08-09-baseline-v2-pre-descongelado.md`
y los ocho de la era de la migración (`plan-maestro-ruta-a-produccion.md`,
`checklist-migracion.md`, `estado-actual-control-local.md`,
`mapa-estado-y-pendientes.md`, `arquitectura-objetivo-java-fullstack.md`,
`inventario-backend-java.md`, `inventario-frontend-blazor.md`,
`uat-rc1-guion-manual.md`).

**Y hay una afirmación de `AGENTS.md` que es falsa hoy**: dice que «la familia
`contrato-congelado-*.md` sigue describiendo el comportamiento del cable y se
mantiene al día». **No existe ni un solo `contrato-congelado-*.md`** en el
árbol — cero ficheros, con control positivo (`decision-*.md` da 16 en el mismo
barrido). El contrato ejecutable son OpenAPI y los tests, que es lo que el
mismo bullet dice dos líneas antes.

**Nada de eso se ha tocado, a propósito**: otro agente estaba editando
`AGENTS.md` en esta misma sesión, y dos agentes no escriben el mismo texto a la
vez. La corrección es la de la tabla de arriba.

---

## 5. Cómo se corren los gates que leen estos ficheros

```bash
# Gates de arquitectura (los que leen docs/ai y verificacion/). Sin base.
mvn -o -pl controllocal-app -am test -Dtest='com.controllocal.arquitectura.*Test' \
    -Dsurefire.failIfNoSpecifiedTests=false      # desde backend-spring/

# Contrato-dato contra el catálogo VIVO (necesita el contenedor levantado)
node docs/ai/modelo/gate-modelo-universal.js

# Volcado del catálogo, para correr el gate sin Docker.
# Lo escribe el propio gate: la consulta tiene un solo dueño.
node docs/ai/modelo/gate-modelo-universal.js --volcar docs/ai/modelo/catalogo-core.json
BROX_GATE_VOLCADO=docs/ai/modelo/catalogo-core.json node docs/ai/modelo/gate-modelo-universal.js
```

El volcado lleva dentro la consulta con la que se generó: si el gate cambia lo
que pregunta, el volcado sale **rojo** en vez de contestar por una pregunta que
ya no se hace. Y si el Core responde **y** hay volcado, se comparan los dos: un
volcado que contradiga al Core es rojo.

Variables: `BROX_GATE_CONTENEDOR` (por defecto `controllocal-postgres-v2`),
`BROX_GATE_BASE` (`controllocal_dev`), `BROX_GATE_USUARIO` (`controllocal`).

Y la comprobación que dice si un clon puede reproducir todo esto, **que es la de
`N26` y no se hace desde el checkout** —desde dentro los ignorados están en
disco y el verde no significa nada—:

```bash
git archive HEAD | tar -x -C "$TMP/clon"      # fuera del checkout, a propósito
cd "$TMP/clon/backend-spring" && mvn -o -pl controllocal-app -am test \
    -Dtest='com.controllocal.arquitectura.*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

Medido así el 2026-09-05 sobre `1702343`: **BUILD SUCCESS**. No hay
`.gitattributes` en el árbol, así que `git archive` y `git clone` entregan lo
mismo y la medición vale.

### 5 bis · Barrer `docs/` con `rg` sin darse cuenta de que no lo está mirando

**`rg` respeta `.gitignore`, y aquí eso significa que un barrido de `docs/ai/`
NO mira la mayor parte de `docs/ai/`.** `pendientes-brox.md`, el North Star, los
encargos, las auditorías y `i0-…` están ignorados: un `rg "loquesea" docs/ai/`
sale **vacío** sobre ellos y **el vacío parece un resultado**. Costó una pasada
entera de este paquete: la primera medición de referencias rotas dio 60
menciones distintas y la buena, con `rg -u`, da **81**.

**Y no es uniforme, que es lo que lo hace traicionero**: medido el 2026-09-05,
bajo `docs/ai/` `rg` **sí** salta lo ignorado, y bajo
`backend-spring/verificacion/` **no** —encuentra `evidencia/`, que git ignora—.
Las dos vistas de «qué está ignorado» **no coinciden**, así que la de `rg` no
sirve para razonar sobre lo que viaja.

- Para **buscar texto** en `docs/`: `rg -u` (o `--no-ignore`), siempre.
- Para saber **qué viaja**: `git check-ignore`, y **por código de salida**.
  `git check-ignore -v` imprime línea también cuando la regla que casa es una
  **negación**, así que «¿imprimió algo?» clasifica como ignorado justo lo que
  acaba de dejar de estarlo. Con control positivo:
  `git check-ignore -q docs/ai/north-star-brox.md` tiene que dar 0.

---

## 6. Lo que este índice NO resuelve

**Nada exige que estos 25 ficheros sigan viajando.** El único gate que mira la
lista blanca es
`AutoridadDeLaPropiedadTest#laAutoridadQueGobiernaViajaConElCodigo`, y mira
**dos** documentos, escritos a mano en un `List.of`. De los que viajan, sólo
**seis** los lee de verdad algún test —`matriz-codigos-estado.md`,
`matriz-operacion-rol.md`, `catalogo-productores-canonico.md`,
`decision-estados-sin-productor.md`, `prototipos/nucleo-brox.js` y
`modelo/*.js`—; **los demás viajan por política**, y saldrían de la lista blanca
**en silencio**. Es la enfermedad de `N21` —una lista a mano que envejece sin
ponerse roja— aplicada a la autoridad documental.

**Y este índice tampoco se vigila solo**: `decision-oferta-y-cierre-de-venta.md`
demostró el mismo día que una decisión nueva puede nacer sin fila aquí y sin
línea en `.gitignore`.

La reparación es una línea de `.java` —que la lista salga de `.gitignore` en vez
de estar copiada en el test— y **queda fuera del alcance de `WP-G`**, que no
toca código. Anotado en `pendientes-brox.md`, ficha `N41`.
