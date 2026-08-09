# Contrato de transversales del frontend Angular

Estado: **IMPLEMENTADO Y VERIFICADO** (2026-07-30).

Este documento fija las reglas que deben reutilizar las pantallas Angular. No modifica el
contrato REST congelado; describe cómo consumirlo sin repetir los problemas del Blazor.

## 1. Sesión y respuestas 401

- `authInterceptor` es el único responsable de adjuntar `Authorization`.
- El JWT solo se adjunta a `API_BASE_URL`; una llamada externa con `HttpClient` nunca lo recibe.
- `POST /auth/login` no lleva un token anterior y su 401 no cierra otra vez la sesión.
- Cualquier 401 de un endpoint protegido limpia la señal, `localStorage` y navega a `/login`
  con `replaceUrl`.
- El cierre es idempotente: varios 401 concurrentes producen una sola navegación.
- Una sesión persistida corrupta, incompleta o vencida se elimina durante el arranque.

Una pantalla no captura 401 para decidir si cierra sesión. Solo presenta errores de negocio;
el interceptor ya resolvió la expiración de manera global.

## 2. Selección y validación de archivos

`ArchivosService` y `cl-subida-archivo` son la única entrada de archivos de usuario:

- máximo predeterminado: 5 MB;
- extensiones conocidas: `.csv`, `.pdf`, `.png`, `.jpg`, `.jpeg`;
- lista blanca de MIME por extensión;
- firma binaria para PDF, PNG y JPEG;
- nombre sin rutas, caracteres de control o símbolos inseguros, limitado a 80 caracteres;
- selección múltiple atómica: si un archivo falla, no se entrega una lista parcial.

Las pantallas configuran el subconjunto permitido:

- fotos: `.png`, `.jpg`, `.jpeg`;
- documentos de solicitud: `.pdf`, `.png`, `.jpg`, `.jpeg`;
- importaciones: `.csv`.

`ApiClient.postBinario$` es la vía preferida para binarios: conserva los bytes, informa progreso
y se cancela al desuscribirse. `ArchivosService.base64()` se usa exclusivamente con endpoints
congelados que todavía exigen JSON base64 (`/locales/{id}/fotos` y `/perfil/foto`).

No se usa la subida por trozos desde Angular. Ese endpoint existe por una limitación del cliente
.NET y se retirará junto con el Blazor.

## 3. Visualización y descarga

`cl-visor-documento` nunca pone
`/documentos/contenido?clave=...` directamente en `src` o `href`.

El flujo obligatorio es:

1. `DocumentosService.contenido$()` pide el Blob mediante `ApiClient`.
2. El interceptor adjunta el JWT.
3. El componente crea un `blob:` URL local.
4. Imagen/PDF se incrustan; otros tipos se ofrecen como descarga.
5. Al cambiar de documento, reintentar o destruir el componente, la petición se cancela y el
   object URL anterior se revoca.

El endpoint sigue siendo `permitAll` mientras vive el contrato congelado porque el Blazor lo
necesita. Angular ya queda preparado para volverlo privado o retirarlo en el corte sin cambiar
las pantallas.

## 4. Formato de fechas, números y montos

`core/formato.ts` es el único sitio donde se convierte un valor del cable en texto para la
pantalla. Reglas:

- Se usa `Intl` y **no** los pipes de Angular: `DatePipe`/`DecimalPipe` formatean con el
  `LOCALE_ID` de la aplicación, que es `en-US` y solo trae sus propios datos de locale.
  Registrar otro locale es una decisión global que no hace falta — `Intl` ya está en el
  navegador.
- Una fecha **sin hora** (`YYYY-MM-DD`) se construye en hora local a propósito.
  `new Date('2026-07-30')` la interpreta como medianoche UTC y en Lima (UTC-5) muestra el día
  anterior; ese desfase de un día es el error clásico y está fijado por test.
- Ausente o inválido se muestra como `—` (`SIN_DATO`), nunca como `undefined` o `NaN`. El
  **cero sí es dato** y se muestra.
- El monto lleva **el código de la moneda delante** (`PEN 8,500`), no el símbolo: confundir
  `S/` con `$` en una renta es caro, y el cable maneja las dos.

Todo esto es presentación: lo que se envía al API sigue siendo el valor crudo del contrato.

**Un campo que el backend exige se pide al usuario, no se rellena.** Si una columna es NOT NULL
y el service la escribe tal cual, el vacío no produce un 400 del contrato sino un error de la
base: la respuesta correcta es marcar el campo obligatorio en el formulario, no inventar un
valor por defecto. El caso vivo es `rentaPublicada` de las publicaciones — un anuncio "por 0"
es un dato falso que además ensuciaría los indicadores comerciales—. La regla se aplica en tres
capas: el tipo del request lo declara `number` (no opcional), el formulario lo valida y el
guardado vuelve a comprobar que el valor es finito antes de armar el cuerpo.

Ojo con `NaN`: **`Validators.required` y `Validators.min` lo dejan pasar** —no es nulo ni
cadena vacía, y toda comparación con `NaN` es falsa, así que tampoco viola el mínimo—. Por eso
los importes llevan además un validador de valor finito.

## 4bis. Fichas de detalle

Dos reglas que fija `core/bloque.ts` y respetan `LocalDetail` y `FichaPropiedad`:

- **El recurso principal es fatal; lo complementario degrada.** Cada bloque secundario se
  dibuja aunque falle, con su propio aviso. Esconder la pantalla entera porque cayó el
  histórico de precios es peor que enseñarla con un aviso en esa tarjeta — y el `try/catch`
  mudo del Blazor, que dejaba la lista vacía sin decir nada, es peor todavía: no se distingue
  "no hay datos" de "no se pudieron leer".
- **Una dirección no identifica un inmueble.** Dos propiedades distintas pueden compartirla —una
  galería, un centro comercial— y en los datos actuales pasa. Cualquier listado de inmuebles
  lleva además el **código del local**, o sus filas se leen como duplicados.
- **Los recursos se relacionan por identificador, nunca por texto.** La ficha de propiedad
  encadena captación → `idLocal` → local → `idPropietario` → propietario. La v1 descargaba las
  bandejas completas de captaciones, locales y propietarios y emparejaba por *coincidencia
  difusa* de dirección y de nombre; eso no solo es carga masiva y filtrado en memoria, es una
  identificación que **puede acertarle al registro equivocado** (bastan dos locales en la misma
  avenida).

## 4ter. Estados, economía y vocabulario normalizados

- El API conserva códigos de un carácter. Angular los tipa y traduce sólo mediante los catálogos
  de `core/modelo`; una regla nueva no compara literales sueltos ni supone que `A`, `C` o `P`
  significan lo mismo en agregados distintos.
- Todo importe viaja y se presenta con moneda `PEN` o `USD`. No existe fallback operativo
  “Moneda no definida” ni se suman monedas en un mismo KPI.
- La comisión de alquiler se captura en lenguaje natural: medio mes, uno, uno y medio, dos,
  porcentaje personalizado de una renta o monto fijo permitido. El request envía tipo, base,
  valor y tratamiento de IGV; el importe calculado se muestra antes de guardar.
- Las fechas se nombran por su función: inicio/fin del encargo, cierre de captación e inicio/fin
  del contrato. Una captación cerrada muestra fecha y motivo, no días restantes.
- `estadoRegistro` y `disponibilidadComercial` son dimensiones distintas. Finalizar o rescindir
  no reactiva el inmueble: sólo una operación expresa de revisión puede hacerlo.
- En oferta se muestra `código · unidad/piso` antes de la dirección. La detección de duplicados
  advierte y permite continuar; los campos técnicos ausentes se resumen como información
  incompleta, sin filas de guiones.
- Navegación canónica: **Oferta** contiene Locales y Propietarios; **Proceso** contiene
  Prospecciones, Captaciones, Captaciones por revisar, Cartera del equipo y Reasignaciones;
  **Demanda** contiene Clientes, Oportunidades, Visitas e Interacciones; **Cierre** contiene
  Solicitudes, Solicitudes por revisar y Cierres exitosos.
- **Un desembolso no se presenta como un único número.** Garantía y adelanto son del
  **propietario** —la garantía además se devuelve al final— y la comisión es de la
  **inmobiliaria**; sumarlos antes de mostrarlos es lo que hace creer al inquilino que paga tres
  meses de renta al propietario. `core/comision.ts` los devuelve por concepto y el total solo
  responde a "cuánto necesito para entrar".

## 5. Primitivas de estilo compartidas

Las piezas que repite toda pantalla viven en `src/styles.scss` con prefijo `cl-`:
`cl-tarjeta`, `cl-tabla` (+ `cl-tabla-marco`), `cl-badge`, `cl-btn` (`primario`, `pequeno`) y
el armazón de una ficha de detalle: `cl-cabecera`, `cl-resumen`, `cl-pares` (+ `apiladas`),
`cl-columnas`, `cl-aviso`, `cl-ok`, `cl-vacio` y `cl-menudo`. Los estilos propios de cada
pantalla siguen en su `.scss`.

Se promovieron al detectar que la tabla y el badge ya estaban duplicados entre el listado de
locales y su ficha, con ~49 pantallas por migrar; mantenerlos por componente además hacía que
una pantalla de detalle superara el presupuesto de 4 kB de `anyComponentStyle`.

**El tono del badge se pide con una clase (`bien`/`aviso`/`mal`) y nunca se deriva del código
de una letra**: el mismo código significa cosas distintas según el dominio — `P` es
"pendiente" en captación y "publicado" en publicación—. Cada pantalla traduce su propio código
a tono.

## 6. Verificación

La suite Angular cubre:

- no filtrar el JWT a destinos externos;
- excluir el login y cerrar sesión ante 401 protegido;
- limpieza idempotente y rechazo de sesiones vencidas;
- octet-stream, parámetros y traducción de errores Blob;
- extensión, MIME, firma binaria, nombre seguro y base64;
- selección válida/inválida;
- carga, cancelación, reintento y liberación de object URLs;
- fechas sin hora que no corren un día, el cero como dato y el código de moneda por delante.

Verificación real del 2026-07-30:

- dos respuestas 401 concurrentes de página + resumen limpiaron la sesión y produjeron una sola
  navegación a `/login`, sin error JavaScript ni bucle;
- un PDF temporal se subió como `application/octet-stream`, se leyó como Blob con JWT, respondió
  200 `application/pdf`, conservó la firma `%PDF` y produjo un object URL;
- el registro y el archivo del fixture se retiraron al terminar (conteo residual: 0).

Verificación real del 2026-07-31 (`LocalDetail`, contra el API en Docker):

- la ficha de `LOC-0001` cargó con **cuatro lecturas en paralelo, todas 200**, y la prospección
  viajó filtrada (`GET /prospecciones?idLocal=1`), sin descargar la bandeja;
- `POST /locales/1/publicaciones/1/estado` respondió 200 y el botón pasó de "Pausar" a
  "Publicar"; se devolvió el estado original al seed;
- el alta de una publicación respondió **201** (queda esa fila extra en la BD de desarrollo,
  publicada, para no alterar el `estadoPublicacion` derivado del local);
- con sesión de BROKER la ficha se ve entera con el badge "Solo lectura" y **cero botones de
  escritura**;
- un id inexistente muestra el mensaje congelado "Local no encontrado." con reintento.
  Sin errores de consola en ninguno de los casos.

Verificación real del 2026-07-31 (`FichaPropiedad`, contra el API en Docker, tres roles):

- la ficha de `CAP-0001` se resolvió encadenando ids —captación → local → propietario—, con la
  comisión, el documento (`RUC …`, no la letra `R`) y la ficha técnica correctos;
- **AGENTE**: subió una foto PNG real (contador 0/6 → 1/6), la miniatura se pintó desde un
  `blob:` **con token** (`naturalWidth > 0`, sin tocar `/documentos/contenido`), y al
  eliminarla el contador volvió a 0/6. Seed restaurado;
- **BROKER de otro equipo** (`sramirez`) entrando **por URL directa**: la pantalla responde
  *"La captación CAP-0001 está fuera de tu alcance."* y **no filtra ningún dato** de la ficha;
- **BROKER del equipo** y **ADMIN**: leen la ficha completa y en el DOM **no existe ningún
  `input[type=file]` ni botón de eliminar** — no es que estén ocultos, no se renderizan; los
  métodos de escritura también se niegan si se invocan directamente (cubierto por test);
- en ningún rol aparece "Exportar PDF" ni hay un solo `<iframe>` en la página.

Verificación real del 2026-08-01 (`PropiedadesEquipo`, contra el API en Docker, tres roles):

- **la deduplicación se probó de verdad**, no por inspección: los datos actuales no tenían
  ninguna propiedad con dos captaciones, así que se insertó una segunda sobre la propiedad 21
  con vigencia hasta 2099. El listado siguió en **36 filas / 36 `idPropiedad` distintos** con
  **37 captaciones** en el equipo, y la fila de esa propiedad pasó a mostrar la captación de
  vigencia más lejana. El fixture se retiró (0 filas residuales);
- **BROKER**: 36 propiedades, 18 con captación activa, 2 agentes, 2 distritos. `distrito`
  filtra a 1 fila **sin mover los KPI** (correcto: el resumen no recibe `distrito`), y `texto`
  sí los mueve a 1 (sí lo recibe);
- **ADMIN**: entra, ve el módulo en el menú y los mismos contadores;
- **AGENTE**: el módulo **no aparece en el menú** y entrar por URL directa redirige a
  `/acceso-denegado` sin filtrar ningún dato. El endpoint le responde **403**;
- "Ver ficha" navega a `/captaciones/CAP-0001/ficha`, que muestra
  *"Un mes de alquiler — USD 8,500"* tras la corrección V12. Sin errores de consola.

Verificación real del 2026-08-01 (`PropiedadesAlquiladas`, contra el API en Docker):

- **el agregado se contrastó contra SQL**, no por inspección: el resumen responde 14 cierres,
  USD 4.150 y 0 pendientes, y la consulta directa da lo mismo. El contraste destapó por qué el
  join tenía que ser **LEFT**: 3 de los 14 contratos no tienen liquidación, así que un inner
  habría reportado 11 cierres;
- filtros y orden en el servidor: `texto` acota a 1, `distrito=Barranco` a 0, `idAgente=28` a
  14, y **sin filtros el orden congelado se conserva** (primer id = 14, descendente);
- **BROKER/ADMIN** ven el filtro por agente; el **AGENTE** no —su alcance ya es él mismo— y su
  pantalla trae un solo select. Un **BROKER de otro equipo** ve 0 cierres, el mensaje de vacío
  y ningún dato ajeno;
- la **exportación** disparó una sola petición extra (`tamano=100`, porque 14 < 100) y avisó
  *"Se exportaron 14 cierres en cierres_exitosos_20260731.csv"*. Sin errores de consola.

Verificación real del 2026-08-02 (**F4 Cierre**, contra el API en Docker, dos roles):

- **BROKER**: la cola `/solicitudes/revisar` trajo 1 pendiente con el cubo `PENDIENTES`, y los
  KPI de la cabecera (1 en revisión, 0 observadas) cuadran con el resumen del backend. Los dos
  selectores llegaron **data-driven** —"Miraflores" y "Valentina Mora"— sin llamada extra;
- la pantalla de evaluación mostró las condiciones, el checklist **4/6** y los cuatro documentos
  con su estado real. El diálogo de "Observar y devolver" **nace bloqueado** y se desbloquea al
  escribir el motivo: la regla se refleja con `toSignal(valueChanges)`, no con un `computed()`
  sobre `FormControl.value` —el fallo que ya costó un botón muerto en F3—;
- el expediente calculó el desembolso concepto por concepto sobre una renta de PEN 9.000 con 2
  meses de garantía y 1 de adelanto: **18.000 / 9.000 / 9.000 y 36.000 de total**, con la
  comisión en un mes de alquiler tras la corrección V12;
- **AGENTE**: la bandeja no ofrece el filtro por agente —su alcance ya es él mismo— y el
  expediente documental dibujó **seis** filas, con "Pendiente de carga" en las dos que faltan.
  Con la solicitud *en revisión* el botón de reenviar está bloqueado y explica por qué, pero la
  carga sigue disponible: el broker puede observar un documento sin devolver la solicitud.
  Sin errores de consola en ninguno de los casos.

Comando:

```powershell
npm --prefix frontend-angular test -- --watch=false --browsers=EdgeHeadlessCI
```
