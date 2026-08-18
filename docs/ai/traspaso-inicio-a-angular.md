# Traspaso a Angular — pantalla de Inicio

**Qué responde:** qué hay que construir, con qué datos, y qué falta en el
backend para que el Inicio diseñado en D-E2-1 exista de verdad.

**Diseño que implementa:** `decision-inicio-foco-y-resolucion.md` (D-E2-1).
**Maqueta de referencia:** artifact `234233a0-7267-4a08-9bf9-d066cf58d7c2`,
verificada con **245 comprobaciones** automáticas.
**Actualizado:** 2026-08-11.

---

## 1. Regla de reparto: qué decide el dominio y qué dibuja Angular

Es la misma de E1 y no se negocia. **Angular no clasifica, no ordena y no
calcula umbrales.**

| Decisión | Dónde vive |
|---|---|
| Qué asuntos dependen del usuario (`DEPENDE_DE_MI`) | dominio |
| El **orden** de los cinco del foco y su posición | dominio |
| El motivo corto que explica por qué el primero es el primero | dominio |
| El tono (rojo/ámbar/verde) de cada asunto | dominio |
| Qué recomienda BROX y para qué | dominio |
| `metaEsperadaAHoy` y el estado de ritmo | dominio |
| Cuántos caben en pantalla, cuándo scrollear, el orden visual | Angular |

Si Angular necesita una cifra que el cable no trae, **la respuesta correcta es
añadirla al backend**, no derivarla en la pantalla.

---

## 2. Árbol de componentes

Todos `standalone`, `ChangeDetectionStrategy.OnPush`, con signals, como el resto
del SPA.

```
features/inicio/
  inicio.ts                     · orquesta; una sola llamada a /inicio
  inicio.html
  partes/
    cabecera-inicio.ts          · saludo, titular de dos líneas, aviso de ausencia
    foco-lista.ts                 · hasta 5 filas; emite (seleccionar)
    foco-filtros.ts             · Propietario | Cliente; NO renumera (D-E2-1 §7.0.f)
    foco-fila.ts                · icono de proceso + hecho + chevron. SIN CTA
    cola-banda.ts               · «7 siguen en tu cola» + enlace
    accesos-rapidos.ts          · 4 enlaces, distintos por rol
    pie-indicadores.ts          · 4 KPI + cifra del mes; enlaza a /indicadores
    radar/
      radar.ts                  · cabecera de marca + conmuta los dos modos
      radar-nucleo.ts           · el octógono QUIETO (SVG inline, sin imágenes)
      seccion-radar.ts          · tarjeta + cabecera con icono en placa (D-E2-1 §7.0.c)
      ruta-proceso.ts           · la cadena del lado: 3 pasos u 4, nunca mezclados
      senales-chip.ts           · atribución; emite (iluminar) sobre los hechos
      radar-general.ts          · hallazgo + qué cambió + qué viene
      radar-resolucion.ts       · conmuta Resolver | Antecedentes (D-E2-1 §7.0.g)
      vista-resolver.ts         · recomendación + acción + cómo está
      vista-antecedentes.ts     · lectura + 4 renglones + expediente completo
      boton-ejecucion.ts        · etiqueta → trabajo → confirmación (§7.0.h)
      como-esta.ts              · barra de avance + hechos con estado (D-E2-1 §10.1)
      control-accion.ts         · archivo | fecha | registro | dato | expediente
  cola-completa/                · pantalla aparte (`/inicio/cola`)
```

**`foco-fila` no lleva botón de acción.** Es la regla de D-E2-1 §5 y hay una
comprobación que la vigila; si alguien añade un CTA ahí, compite con la
recomendación del Radar.

---

## 3. El cable que hace falta: `GET /inicio`

Una sola llamada, como hoy `/dashboard`. Todo ya clasificado.

```ts
export interface InicioCarga {
  ambito: string;                    // «Mi actividad» · «Mi equipo»
  saludo: string;                    // el dominio decide el tratamiento
  pastilla: { texto: string; tono: Tono };
  dependenDeMi: number;              // total, no solo los del foco
  foco: AsuntoFoco[];                // como MÁXIMO 5, ya ordenados
  motivoDelPrimero: string;          // «bloquea una publicación.»
  avisoInmediato?: string;           // «El 05 vence hoy.»
  cola: { total: number; porTipo: { tipo: string; n: number }[] };
  desdeUltimaVisita?: { cambios: number; nuevos: number };
  accesos: Acceso[];                 // 4, distintos por rol
  pie: PieIndicadores;
  radar: Radar;
}

export type Tono = 'ALTO' | 'MEDIO' | 'BUENO';

/** D-E2-1 §7.0.d. DOS cadenas, no una, y no comparten ningún paso.
 *  Oferta:   PROSPECCION → CAPTACION → PUBLICACION
 *  Demanda:  OPORTUNIDAD → VISITA → SOLICITUD → CONTRATO
 *  El lado y el índice los decide el dominio; Angular solo dibuja N segmentos. */
export type Lado = 'OFERTA' | 'DEMANDA';   // se rotulan Propietario / Cliente
export type Paso =
  | 'PROSPECCION' | 'CAPTACION'  | 'PUBLICACION'
  | 'OPORTUNIDAD' | 'VISITA'     | 'SOLICITUD'  | 'CONTRATO';

export interface AsuntoFoco {
  id: string;
  proceso: Proceso;                  // PROSPECCION | CAPTACION | … | COMISION
  tono: Tono;
  titulo: string;                    // «Av. Arequipa 1840»
  hecho: string;                     // «Vence en 12 días · respondió ayer»
  identidad: string;                 // «Sr. Ramírez · US$ 4,500 · vence 22 ago»
  recomendacion: string;
  paraQue: string;
  accion: Accion;
  como: ComoEsta;                    // el estado, hecho por hecho
  proximo: string | null;            // null se dice, no se oculta
  expediente: RenglonExpediente[];   // 4 renglones fijos
  lectura: string;                   // la frase que sintetiza los cuatro
  destino: string;                   // ruta REAL del SPA
  lado: Lado;                        // de quién es la operación
  paso: Paso;                        // dónde cae en SU cadena, para la ruta
  traidoPorMi?: boolean;             // lo trajo el usuario desde la cola
}

/** D-E2-1 §10.1. Vocabulario cerrado: no se añaden estados sin decisión. */
export type EstadoHecho =
  | 'HECHO'    // resuelto            → ✓ verde
  | 'FALTA'    // pendiente de alguien → ○ ámbar   (lo accionable)
  | 'PLAZO'    // corre el tiempo      → ⏱ rojo
  | 'FRENO'    // qué queda parado     → ⊘ rojo
  | 'DATO';    // contexto             → – gris

export interface ComoEsta {
  /** Solo si hay requisitos contables de verdad. Nunca inventar uno. */
  avance?: { hechos: number; total: number; unidad: string };
  hechos: { estado: EstadoHecho; texto: string }[];   // COMO MÁXIMO 3
}

/** Hasta los CUATRO pueden llevar `estado`; la condición no es un tope sino
 *  que ninguno se tiña sin una señal real detrás (D-E2-1 §10.3.1). */
export interface RenglonExpediente {
  rotulo: string;                    // «Encargo» · «Renta» · «Actividad» · …
  valor: string;                     // la frase; la señal va delimitada en ella. SIN códigos
  estado?: 'BIEN' | 'OJO' | 'MAL';   // ausente = historial, sin color
  ventana?: { consumido: number; total: number };   // barra + su razón
  serie?: number[];                  // chispa de la renta — sale de E0
  contraste?: Contraste;             // dónde cae este dato en TU operación
}

/** D-E2-1 §10.3.2. Sale SIEMPRE de datos de la organización, nunca del
 *  sector: rango real de la zona, media propia, plazo de la casa. */
export type Contraste =
  | { tipo: 'RANGO'; min: number; max: number; valor: number; texto: string }
  | { tipo: 'DESVIACION'; etiqueta: string; texto: string };

export type Accion =
  | { tipo: 'ARCHIVO'; etiqueta: string; formatos: string; maxMb: number }
  | { tipo: 'FECHA'; etiqueta: string; valorPorDefecto?: string }
  | { tipo: 'REGISTRO'; etiqueta: string; opciones: string[]; conNota: boolean }
  | { tipo: 'DATO'; etiqueta: string; opciones: string[] }
  | { tipo: 'EXPEDIENTE'; etiqueta: string; nota: string };

export interface Radar {
  vigila: string;                    // «Vigilando 18 operaciones · 4 movimientos hoy»
  hallazgo: { titulo: string; cuerpo: string; asunto: string | null } | null;
  cambio: { cuando: string; que: string; consecuencia?: string }[];
  viene: { cuando: string; que: string; detalle: string;
           proceso: Proceso; asunto: string | null }[];
}

export interface PieIndicadores {
  cifra: { rotulo: string; valor: string; detalle: string; serie: number[] };
  /** Los 4 canónicos de D-E2-2, con el nombre LITERAL de esa pantalla.
   *  `falta` y `empuje` los redacta el dominio: «A 2 de la meta · vas 1 por
   *  delante» / «· hoy deberías ir por 14» / «Meta cumplida». */
  kpis: { nombre: string; actual: number; meta: number;
          esperadoAHoy: number; ritmo: Tono; empuje: string }[];
  /** Solo del broker (D-E2-2 §6.1): la distribución, no el total. Sin
   *  nombres — la instrucción 13 prohíbe el ranking. */
  pulso?: { n: number; etiqueta: string; tono: Tono }[];
}
```

**Cuatro campos que parecen menores y no lo son:**

- `como.hechos[].estado` lo decide **el dominio, no la pantalla**. Es lo que
  impide que un asunto en rojo pinte de rojo también sus buenas noticias. Angular
  se limita a mapear estado → marca y color; si deduce el estado del tono del
  asunto, vuelve el problema que esta versión arregló (D-E2-1 §10.1).
- `hallazgo.asunto` y `viene[].asunto` llevan el **id del asunto en la cola de
  ESE rol**. Es lo que sostiene la regla del hogar único; con un id compartido,
  el mismo encargo sale dos veces en el broker (pasó, §7.1 de D-E2-1).
- `esperadoAHoy` es lo que convierte la barra del pie en información. Sin él, un
  87 % no dice si vas por delante o por detrás.
- `proximo: null` **se muestra** («Sin próxima actividad programada»), no se
  esconde: muchas veces es el dato importante.

**Regla de redacción del cable, no de la pantalla:** todo texto que viaje en
`como.hechos[].texto` y en `radar.cambio[]` **nombra su objeto** — código,
dirección o nombre propio (D-E2-1 §10.4). «María Torres completó el expediente»
no se puede resolver en el frontend: falta el dato, y el dato lo tiene el
backend.

---

## 4. Qué existe hoy y qué falta

Verificado el 2026-08-11 sobre `backend-spring` (32 controladores) y el SPA
(≈55 rutas).

### Ya existe y se reutiliza

| Pieza | De dónde sale |
|---|---|
| Pendientes de atención, señales clasificadas | `GET /indicadores/resumen` (`senales[]`, `pendientesDeAtencion`) |
| La bandeja de tareas del agente | `GET /tareas` — trae `prioridad`, `diasSinAccion`, `fechaVencimiento`, `entidadCodigo` |
| Composición en un round-trip | `GET /dashboard` ya compone resumen + bandeja |
| Los cuatro KPI en bruto | `IndicadoresResumen`: `captacionesTotales`, `cierres`, `conversionPropia`… |
| Todos los destinos de `Ver expediente` | rutas reales del SPA (D-E2-1 §9.1) |

### Falta, y hay que decidirlo antes de picar

- [ ] **`DEPENDE_DE_MI`** como campo del dominio (E2.2 del mapa). Hoy no se
      distingue lo que espera al usuario de lo que espera a un tercero, y es el
      **primer filtro** del despacho.
- [ ] **La política de despacho** (D-E2-1 §3) en `service/soporte`: ventana
      temporal, ventana de oportunidad, desbloqueo, antigüedad y estabilidad.
      Hoy `/tareas` ordena por prioridad y días, que no es lo mismo.
- [ ] **`metaPeriodo` y `metaEsperadaAHoy`** por KPI: no existen metas en el
      modelo. Es el bloque grande de D-E2-2 (§5 y §13).
- [ ] **«Puede cerrarse este mes»**: la métrica no está definida en D-E2-2.
- [ ] **El expediente de 4 renglones**: los datos existen repartidos
      (`captacion`, `local`, `historico_precio`, `visita`, `propietario`); falta
      la vista que los junta, **y con ella el `estado` y la `ventana` de cada
      renglón**. La `serie` es la única pieza que ya existe: es
      `historico_precio`, cerrado en E0.
- [ ] **`ComoEsta` por asunto**: hoy nada clasifica un hecho como resuelto,
      pendiente, en plazo o freno. Es el mismo trabajo que `senales[]` de E1, un
      escalón más abajo: el hecho ya llega interpretado. **`avance` sale de
      contar requisitos reales** (documentos de la solicitud, observaciones de la
      captación, criterios comprobados del matching), no de una estimación.
- [ ] **`lado` y `paso`**: las dos cadenas existen en el dominio pero no se
      exponen como posición. Es un `switch` sobre el tipo de asunto, no una tabla
      nueva. **Ojo: son dos cadenas disjuntas**, no una de siete.
- [ ] **`lectura`**: hoy nada sintetiza el expediente en una frase. Es del mismo
      orden que `senales[]` de E1 — el hecho llega ya interpretado.
- [ ] **`contraste`**: necesita dos agregados que no existen — el **rango de renta
      por zona y metraje** (sale de `historico_precio`, cerrado en E0) y las
      **medias propias** (propuestas por visita, días hasta contrato, plazo de
      recontacto real). Ambos son consultas, no modelo nuevo.
- [ ] **`hallazgo`**: hoy no hay ningún productor de hallazgos. Es E2.5.
- [ ] **Acciones desde el Radar**: `ARCHIVO` puede apoyarse en
      `DocumentosController`; `REGISTRO` en `InteraccionesController`; `FECHA`
      necesita un endpoint de agenda que **no existe**.

Mientras falten, el Inicio se puede montar con `/dashboard` y `/tareas`
degradando: sin hallazgo, sin pie y con el foco ordenado por lo que hoy manda
`/tareas`. **Lo que no se debe hacer es calcular en Angular lo que falta.**

---

## 5. Detalles de implementación que ya costaron tiempo

Están en D-E2-1 con su porqué; aquí van como recordatorio para quien pique.

| Tema | Regla |
|---|---|
| Alto de la pantalla | la página **no scrollea si el contenido cabe**, y **sí** en cuanto el foco crece. Nunca `overflow: hidden` |
| Tope del Radar | se mide (`ResizeObserver` sobre cabecera y pie), no se pone en `vh` |
| Anillos | `repeating-radial-gradient` enmascarado; `box-shadow` con spread pinta discos rellenos |
| Oro sobre azul noche | `mix-blend-mode: screen`, o sale marrón |
| Animaciones | **una por superficie**. Varias en el mismo punto se anulan; el emblema va quieto y la señal de vida es el punto de la cabecera |
| Estructura del panel | **tarjetas sobre sustrato**, nunca bandas con filete: es lo que separa «panel» de «documento» |
| Iconos de sección | obligatorios; la placa toma el color que corresponda (tono del caso, estado del renglón, verde del hallazgo) |
| Aparición | escalonada, 55 ms por bloque; el índice se pone en JS al montar, no en la plantilla |
| Rótulos del expediente | encima del dato, no en columna: «Requerimiento» no cabe; y el **dato en tinta plena**, o los cuatro renglones se leen como un párrafo |
| Barras y chispas | siempre con su razón (`168/180`); una barra sin cifra es un adorno, y con el riel invisible un relleno del 8 % desaparece |
| Color en los antecedentes | en el fragmento, nunca en el rótulo; **dos renglones con color por expediente como máximo** |
| Nombres de KPI | **completos y literales, y los mismos que Indicadores**; si allí cambian, aquí cambian el mismo día |
| Lectura por rol | el KPI es el mismo, **la segunda línea no**: al agente se le pide producción, al broker se le informa del equipo |
| Rutas | en el `href`, nunca a la vista |
| Móvil | accesos y KPI en 2 × 2; el Radar deja de estar acotado |
| Iconos | **en ninguna parte del Radar**: ni en secciones ni en renglones. Un canto de 3 px y una versalita teñida hacen el mismo trabajo |
| Códigos | `AAA-0000` **jamás** en texto visible; solo en las rutas del `href` |
| Ruta del proceso | **solo el paso actual**, nunca el itinerario: al operar interesa dónde estás, no cómo se llega |
| Identidad del asunto | persona en blanco, dinero en verde, plazo en ámbar con canto: los tres datos no valen lo mismo |
| Color del renglón | va en el **rótulo** (versalita teñida + canto de 3 px); el dato siempre en tinta plena |
| Cuántos renglones con señal | **los que la tengan**, hasta los cuatro; ninguno teñido por rellenar |
| Registro visual | rectilíneo: radio máximo **8 px**, ninguna cápsula de 999, ningún punto de color decorativo |
| Selección de fila | **grafito** (`--ink`), nunca el azul de BROX ni el color del lado |
| Animación | `@property` para interpolar ángulos, `linear()` para el resorte, View Transitions para cambiar de vista; todo GPU y sin librería |
| Altura del Radar | dos vistas, cada una completa; la de Resolver pide ~570 px de cuerpo |
| Lados del negocio | temperaturas (arena / acero), **nunca** los tres del semáforo: en la misma fila ya significan otra cosa |

---

## 6. Pruebas que deberían viajar con el componente

La maqueta trae 245 comprobaciones; estas son las que tienen sentido portar a
Karma/Jasmine porque protegen **decisiones**, no píxeles:

1. Nunca más de 5 en el foco, y con 2 accionables se ven 2.
2. Ninguna fila del foco contiene un botón de acción.
3. Ningún texto del foco contiene una etiqueta de clasificación.
4. Lo que está en la cola aparece en `Qué viene` como referencia, no como fecha.
5. El hallazgo enlaza a su número y no ofrece una segunda acción.
6. Traer un asunto de la cola **no cambia** el orden de los demás.
7. Los cuatro KPI llevan su nombre canónico completo.
8. Ninguna ruta aparece en el texto visible.
9. La palabra «por qué» no aparece en el Radar.
10. A 402 px nada se sale a lo ancho y ningún rótulo se corta.
11. Dentro de `CÓMO ESTÁ`, un `HECHO` sale en verde y un `FRENO` en rojo **en el mismo asunto**: el estado no se hereda del tono.
12. No hay barra de avance donde `avance` no viene, y donde viene no repite la identidad del caso.
13. Ningún renglón de `Qué cambió` se queda sin código, dirección o nombre propio.
14. Ningún expediente tiene más de dos renglones con color, y ningún fragmento teñido cuelga de un renglón sin estado.
15. Toda barra lleva su razón y su animación arranca al desplegar, no antes.
16. Todo bloque del cuerpo es una tarjeta (fondo, radio ≥ 10, sombra) y aparece con su turno.
17. Toda sección y todo renglón del expediente llevan icono en placa.
18. La ruta marca uno de seis pasos, y el marcado corresponde al proceso del asunto.
19. Pulsar `N señales` ilumina exactamente los hechos que sostienen la recomendación.
20. La ruta usa la cadena de SU lado, y las dos listas de pasos no se solapan.
21. Filtrar el foco por `Cliente` no cambia los números de las filas visibles.
22. La lectura del expediente no repite literalmente ninguno de los cuatro valores.
23. Ningún texto de contraste menciona «sector», «mercado nacional» ni «benchmark».
24. Ninguna de las dos vistas del Radar scrollea con la ventana a 940 px de alto o más.
25. Conmutar de vista no re-anima ni la cabecera ni el propio conmutador.
26. El botón de acción pasa por `trabajando` y `listo` antes de que el asunto salga de la cola.
27. Ningún rótulo del expediente lleva icono, y su tinta es distinta de la del dato.
28. El rótulo de un renglón con señal va en el color de su estado; el de uno sin señal, en gris.
29. La ruta enseña los pasos con su nombre y marca en cuál está.
30. La barra de vistas no tiene fondo ni radio, sí filete, y el subrayado mide lo que la pestaña abierta.
31. La fila seleccionada se marca con `--ink`, no con `--accion` ni con el color del lado.
32. El `innerText` de la pantalla no contiene ningún `AAA-0000`.
33. Ninguna cabecera de sección tiene icono, y las cuatro llevan canto de color distinto del gris.
34. Ningún renglón con estado carece de portador visible, y ninguna marca cuelga de un renglón sin estado.
35. Ningún texto del broker se puede leer igual en el Inicio de un agente.
36. Cada KPI del pie abre con «A N de la meta» (o «Meta cumplida») antes del ritmo.
37. El pie del broker no contiene la frase «hoy deberías ir por»: eso es producción personal.
38. El pulso del equipo aparece solo en el broker, una vez, y con sus tres tonos.

---

## 7. Lo que queda fuera de este traspaso

La pantalla de **Indicadores** (D-E2-2) tiene su diseño decidido pero **no tiene
maqueta todavía**. El pie del Inicio ya la anticipa y enlaza a ella, así que se
puede construir el Inicio primero sin bloquear nada.
