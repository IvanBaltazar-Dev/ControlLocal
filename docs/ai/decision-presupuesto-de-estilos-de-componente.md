# EL PRESUPUESTO DE ESTILOS DE COMPONENTE

**Decisión (2026-08-21).** El presupuesto `anyComponentStyle` de `frontend-angular/angular.json`
sube su techo de error de **8 kB a 16 kB**. El aviso se queda en **4 kB**, sin tocar. Y el Radar del
Inicio se parte en cuatro componentes, uno por tarjeta, porque su hoja era el único caso que no cabía
por densidad sino por acumulación.

Las dos cosas van juntas a propósito: subir el techo sin partir lo que sí se podía partir es
desactivar la medida.

---

## El fallo que la motiva

`npm run build` resuelve a `ng build` con `defaultConfiguration: production`, y **fallaba**. Tres
hojas pasaban del techo de 8 kB:

| Hoja | Tamaño |
|---|---|
| `features/dashboard/partes/radar.scss` | 17.43 kB |
| `features/indicadores/indicadores.scss` | 14.03 kB |
| `features/dashboard/dashboard.scss` | 10.89 kB |

`ng test` pasaba (653/653) porque las pruebas no aplican presupuestos: se compilan en configuración
de desarrollo. El único sitio donde esto se ve es el build de producción.

**Dónde se rompió.** Midiendo cada `.scss` de cada commit con `sass --style=compressed` —calibrado
contra lo que reporta Angular, con menos del 3 % de desvío—:

| commit | `dashboard` | `radar` | `indicadores` |
|---|---|---|---|
| `a4ba8b8` (main) | 5.37 kB | no existe | 2.29 kB |
| `c33d49a` *la fundación visual sale del prototipo* | **12.48 kB** | no existe | 7.42 kB |
| `17073e5` · `193e5ac` · `151d937` | 12.48 kB | no existe | 7.42 kB |
| `bc3de56` *el Inicio pasa a foco y resolución* | 11.05 kB | **17.98 kB** | **14.07 kB** |

En `main` la mayor de las 74 hojas de componente es `dashboard.css` con 5.37 kB y ninguna pasa de
8 kB: **`main` compila**. La rotura entra en `c33d49a`, el primer commit de
`feat/modelo-universal-y-autoridad-del-dato`, y `bc3de56` la agrava con dos hojas más.

---

## Por qué se sube el techo

1. **Nadie eligió los 8 kB.** El presupuesto entró con `10f985e`, el commit que trae el
   `angular.json` completo, y son literalmente los valores que emite `ng new`. No es un techo que
   este proyecto se fijara y luego incumpliera: es un valor por defecto que nunca se revisó.
2. **El defecto asume componentes pequeños y reutilizables.** El Inicio y los Indicadores no lo son.
   Son pantallas de instancia única portadas una a una desde `docs/ai/prototipos/*.html` con los
   valores copiados sin reinterpretar (lo dicen las cabeceras de sus hojas). En una pantalla así la
   hoja **es** la pantalla, y su tamaño mide densidad visual, no desorden.
3. **El aviso de 4 kB es el que hace el trabajo.** Sigue en pie y sigue disparando en siete hojas.
   Es el que avisa de que un componente ha cruzado la línea de «reutilizable»; el error solo tiene
   que atrapar el caso en el que una pantalla ya son varias pantallas.

**16 kB, y no «lo que haga falta hoy».** Deja a `indicadores.scss` (14.03 kB) con ~2 kB de holgura y
al resto muy por debajo. Es el doble del defecto: el punto en el que «esta pantalla es densa» pasa a
ser «esta pantalla ya son varias».

---

## Por qué el Radar sí se parte

`radar.scss` no era una pantalla densa: eran **cuatro tarjetas apiladas en un fichero**. La
plantilla ya las tenía separadas —el modo general enseña el hallazgo destacado; el de resolución
conmuta entre «Qué hacer ahora» + «Cómo está» y «Antecedentes»—, pero las cuatro compartían hoja.

Cada tarjeta pasa a ser su propio componente:

| Componente | Hoja | Qué se lleva |
|---|---|---|
| `cl-radar-hallazgo` | bajo el aviso de 4 kB | `.hallazgo`, `.en-cola` |
| `cl-radar-resolver` | 4.40 kB | `.reco`, `.senales`, `.bt-p`, `.bt-t` |
| `cl-radar-como-esta` | bajo el aviso de 4 kB | `.ahora`, `.avance`, `.proximo`, `.senalada` |
| `cl-radar-antecedentes` | bajo el aviso de 4 kB | `.lectura`, `.ant-*`, `.vs` |
| `cl-radar` (armazón) | **7.97 kB**, de 17.43 | superficie, cabecera, cuerpo, ruta, conmutador |

El armazón queda por debajo incluso del techo viejo de 8 kB: **el Radar deja de necesitar la subida**
y esta se aplica solo a `indicadores.scss` (14.03 kB) y `dashboard.scss` (10.89 kB), que son las dos
pantallas portadas del prototipo.

**El anfitrión de cada uno ES la tarjeta.** `.radar-cuerpo > *` da fondo, borde y entrada escalonada
al hijo directo, y el hijo directo pasa a ser el componente: por eso cada `:host` lleva
`display: block` —sin él sería `inline`— y recoge lo que ponía `.rz` o `.reco`. Así no se añade
ningún nivel al DOM y los selectores de `dashboard.spec.ts` (`.reco .que`, `.ahora li`,
`.hallazgo .c`, `.ant-fila`…) siguen encontrando lo mismo donde estaba.

**Tres detalles que la encapsulación obliga a repetir**, y no son descuido:

- `.cab-rz` y `.mas` se duplican en las tarjetas que los pintan. Las reglas del padre no alcanzan la
  plantilla del hijo. Es lo mismo que ya hacía `accion-rapida.scss` con `.bt-p` y `.bt-t`.
- `:host > p` reproduce el `.rz > p` original en `antecedentes` y en `como-esta`. Esa regla le gana
  en especificidad a `.lectura` y a `.avance`, que son `<p>` directos: llevarse el bloque sin
  traérsela habría cambiado el cuerpo y el margen de los dos.
- `@keyframes brox-aflorar` se queda en `radar.scss`. Las `@keyframes` no se scopean con
  encapsulación emulada y el armazón siempre está pintado cuando existe una tarjeta, así que la
  referencia resuelve. Es la misma razón por la que todas van prefijadas.

---

## Qué haría revisar esto

- Que una hoja vuelva a acercarse a 16 kB. Entonces la pregunta no es el techo: es si esa pantalla
  ya son varias, como lo era el Radar.
- Que se quiten los avisos de 4 kB por molestos. Ahí se acaba de perder la medida entera.
- Que aparezca una hoja grande que **no** venga de portar un prototipo. El argumento de arriba no la
  cubre.
