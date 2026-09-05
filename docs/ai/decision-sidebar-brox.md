# D-E3-1 · La navegación de BROX

**Qué decide:** qué entradas tiene el menú, cómo se agrupan, qué cambia por rol
y qué se va del menú porque no era una sección.

**Estado:** **APLICADA** el 2026-08-18 en el Corte 2, con dos correcciones del
productor anotadas en §4 bis y §3.1 bis. Sale de
`auditoria-ui-brox.md` (medición) y se contrasta con
`docs/ai/matriz-operacion-rol.md` (permisos).

**Relacionado:** `decision-inicio-foco-y-resolucion.md` (D-E2-1) — el Inicio es
el destino de las colas que salen de aquí.

---

## 1. La decisión

**El menú representa el trabajo mental del usuario, no las tablas del modelo.**

De eso salen tres reglas que ordenan todo lo demás:

1. **Una cola de trabajo no es una sección.** «Captaciones por revisar» y
   «Solicitudes por revisar» son la misma lista que su bandeja, filtrada por
   estado. Su sitio es el **Inicio** —que es la superficie de decisión— y, como
   filtro, dentro de su bandeja.
2. **El alcance no crea entradas.** «Cartera del equipo» es *Locales* con el
   alcance del broker: lo resuelve el backend, no el menú.
3. **La misma taxonomía para todos los roles.** Cambian las capacidades, el
   alcance y algunas entradas de organización — nunca las grandes categorías.

---

## 2. La estructura

```
BROX

  [ + Registrar ]           ← crear; no es una sección, es un botón

  Inicio                    ← a lo que se vuelve para saber qué resolver

  OFERTA
    Propiedades
    Propietarios
    Prospecciones
    Captaciones

  DEMANDA
    Clientes
    Requerimientos          ← pantalla nueva, TODAVIA NO EXISTE
    Oportunidades
    Visitas

  CIERRE
    Solicitudes             ← alquiler y compraventa, un solo sitio
    Evaluaciones            ← pantalla nueva, TODAVIA NO EXISTE
    Contratos
    Comisiones

  GESTIÓN
    Indicadores
    Seguimiento

  ORGANIZACIÓN              ← según rol
    Agentes
    Brokers
    Asignaciones
    Seguridad y accesos

  ─────────────────────────
  Perfil · Configuración · Salir      ← al pie
```

**Cuentas previstas:** el agente pasa de **18 a 13** entradas; el broker, de
**24 a 15**; el administrador, de **24 a 16**. Y ninguna es una cola.

> **Cuentas reales tras aplicarlo (2026-08-18): 15 · 17 · 19.** La diferencia
> son **dos entradas por rol**, siempre las mismas: **Interacciones** y
> **Reportes**. Las dos están marcadas en §4 como uniones que *«no se ejecutan
> sin decidirlas aparte»*, así que se conservan hasta esa decisión. Ninguna de
> las dos es una cola ni una entrada de alcance, que es lo que este corte venía
> a eliminar — y eso sí está al 100 %.
>
> Las dos pantallas nuevas del esquema —**Requerimientos** y **Evaluaciones**—
> tampoco cuentan todavía: su recurso existe en el backend, la pantalla no. Se
> añadirán con ella, no antes: una entrada de menú que lleva a un hueco es peor
> que no tenerla.

### 2.0 `+ Registrar` no es una entrada del menú

**El sidebar sirve para entrar al trabajo; `+ Registrar` sirve para crear.** Por
eso va arriba del todo, separado, y **no** se convierte en cinco entradas
(*Nueva propiedad*, *Nuevo cliente*, *Nueva visita*…) que devolverían el menú a
25 enlaces — justo lo que la auditoría vino a quitar.

Al pulsarlo pregunta **«¿Qué quieres registrar?»**, no «seleccione el módulo de
destino». Y cada módulo conserva además su botón contextual —*Propiedades →
+ Registrar propiedad*—: el mismo motor con el contexto ya puesto.

El motor que responde está congelado en `decision-motor-de-registro.md`
(D-E4-2), y es el mismo que consume KAIROS.

### 2.1 Por qué estas cuatro categorías de negocio

Son las del dominio, no una invención: `Propietario → LocalComercial →
Captacion` es **oferta**; `Cliente → Requerimiento → Oportunidad → Visita` es
**demanda**; `Solicitud → Evaluación → Contrato → Comisión` es **cierre**. Es la
misma partición que usan los dos prototipos para pintar el lado de cada asunto,
y la que hace que «de quién es esto» se lea antes que el nombre.

**La sección `Proceso` de hoy desaparece**: mezclaba captaciones (oferta) con
cartera del equipo (supervisión) y era el único agrupador que no nombraba una
fase del negocio.

---

## 3. Entrada por entrada, con las cinco preguntas

`R` = qué rol la ve · `A` = alcance (lo resuelve el backend) · `V` = veredicto.

| Hoy | Mañana | R | ¿Qué se hace dentro? | ¿Cambia por alcance o por permiso? | V |
|---|---|---|---|---|---|
| Dashboard | **Inicio** | todos | resolver los asuntos del día | alcance | **RENOMBRAR** |
| Locales | **Propiedades** | todos | buscar y abrir un inmueble | alcance | **RENOMBRAR** (§5) |
| Cartera del equipo | *(filtro de Propiedades)* | broker · admin | ver los inmuebles del equipo | **alcance** | **UNIFICAR** |
| Propietarios | Propietarios | todos | catálogo; alta es de agente | permiso al escribir | **CONSERVAR** |
| Prospecciones | Prospecciones | todos | recontactar y calificar | alcance | **CONSERVAR** |
| Captaciones | Captaciones | todos | seguir los encargos | alcance | **CONSERVAR** |
| Captaciones por revisar | *(al Inicio + filtro)* | broker · admin | decidir un encargo | **permiso**: `POST /captaciones/{id}/decision` | **REUBICAR** |
| Reasignaciones | *(acción en Captaciones)* | broker · admin | reasignar un encargo | permiso | **REUBICAR** |
| Clientes | Clientes | todos | catálogo; alta es de agente | permiso al escribir | **CONSERVAR** |
| — | **Requerimientos** | todos | qué busca cada cliente | alcance | **BACKEND_FALTANTE**: el recurso existe, la pantalla no |
| Oportunidades | Oportunidades | todos | avanzar una oportunidad | alcance (broker por captación) | **CONSERVAR** |
| Visitas | Visitas | todos | agendar y registrar resultado | alcance | **CONSERVAR** |
| Interacciones | *(dentro del expediente)* | todos | ver la conversación | alcance | **REUBICAR** (§4) |
| Solicitudes | **Expedientes** | todos | seguir los cierres en curso, de alquiler y de venta | alcance (broker por agente) | **RENOMBRAR** (D-E4-1) |
| Solicitudes por revisar | *(al Inicio + filtro)* | broker | evaluar y firmar | **permiso**: `POST /evaluaciones` | **REUBICAR** |
| — | **Evaluaciones** | broker · admin | historial de lo decidido | permiso al firmar, alcance al leer | **BACKEND_FALTANTE**: `GET /evaluaciones` existe, la pantalla no |
| Cierres exitosos | **Contratos** | todos | consultar lo firmado | alcance | **RENOMBRAR** |
| Comisiones | Comisiones | todos | liquidar | **permiso**: las tres operaciones son de broker | **CONSERVAR** |
| Seguimiento | Seguimiento | todos | actividad y recontactos | alcance | **CONSERVAR** (§4) |
| Indicadores | Indicadores | todos | rendimiento y ritmo | alcance | **CONSERVAR** |
| Reportes | *(pestaña de Indicadores)* | todos | avance por propiedad (RF-017) | alcance | **UNIFICAR** (§5) |
| Agentes | Agentes | broker · admin | padrón; alta es de admin | permiso al escribir | **CONSERVAR** |
| Mi equipo | *(filtro de Agentes)* | broker | ver a los suyos | **alcance** | **UNIFICAR** |
| Brokers | Brokers | **broker · admin** | padrón | permiso | **CONSERVAR** endureciendo |
| Asignaciones | Asignaciones | admin | organigrama | permiso | **CONSERVAR** |
| Seguridad y accesos | Seguridad y accesos | admin | cuentas y avisos | permiso | **CONSERVAR** |
| Catálogos | *(en Configuración)* | todos | consultar códigos | ninguno | **REUBICAR** |
| Mi perfil | **Perfil**, al pie | todos | cuenta, contraseña, MFA | ninguno | **REUBICAR** |
| *(botón en la barra)* | **Salir**, al pie | todos | cerrar sesión | ninguno | **REUBICAR** |

### 3.1 El único endurecimiento nuevo

**Brokers** pasa de `TODOS` a `BROKER · TENANT_ADMIN`. Hoy un agente ve el
padrón de brokers y no hay operación suya que lo necesite. `GET /brokers` seguirá
respondiendo —el backend manda— pero dejar de ofrecerlo no promete nada falso.

### 3.1 bis · Las cinco salen del menú YA, no cuando exista el Inicio

**Corrección del productor, 2026-08-18.** Al implementar el Corte 2 se propuso
conservar temporalmente las cinco entradas redundantes —«Captaciones por
revisar», «Solicitudes por revisar», «Cartera del equipo», «Mi equipo» y
«Reasignaciones»— hasta que el Inicio de E2.2–E2.6 pudiera absorberlas. **Se
rechazó, y con razón:** la auditoría que originó este corte encontró
exactamente eso —colas presentadas como secciones y pares de pantallas que eran
el mismo objeto con otro filtro—, así que conservarlas «temporalmente» conserva
el problema que el corte venía a eliminar.

La transición no necesita al Inicio. Cada una tiene ya su superficie natural:

| Entrada que sale | Cómo se llega hoy |
|---|---|
| Captaciones por revisar | Captaciones → filtro *Por revisar*, con su contador para el broker |
| Solicitudes por revisar | Solicitudes → filtro *Por evaluar* |
| Cartera del equipo | Propiedades → el mismo listado con el alcance del broker |
| Mi equipo | Agentes → filtro de supervisados |
| Reasignaciones | Captaciones → acción *Reasignar* y su historial |

Cuando `GET /inicio` y E2.2–E2.6 existan, esas colas **alimentarán además** el
Inicio con su priorización. No cambia la taxonomía y no hay que desmontar el
menú otra vez.

> **Lo que no puede pasar al quitarlas.** Borrar la fila del mapa de acceso
> dejaría la ruta sin módulo, y el guard **deja pasar lo que no reconoce**
> («sin data explícita: la autorización definitiva sigue en el backend»). Es
> decir: quitarlas del menú borrándolas las abriría a cualquier rol por URL.
> Por eso el módulo se conserva con `enMenu: false` — invisible y protegido—, y
> hay una prueba que lo exige en los dos sentidos.

### 3.2 Los dos módulos que endurecen y por qué

`Solicitudes por revisar` y `Mi equipo` declaran hoy menos roles que su
operación de entrada. **No es un desajuste, es intención** — y por eso el módulo
tiene que declararla en el dato y no en un comentario:

```ts
{
  ruta: 'solicitudes',
  operacionEntrada: 'GET /solicitudes',     // TODOS
  operacionSalida:  'POST /evaluaciones',   // BROKER  ← por esto se endurece
}
```

Con eso la prueba de navegación (Fase G) puede exigir «visible ⊆ permitido» y
distinguir el endurecimiento deliberado de la divergencia.

---

## 4. Las tres uniones que hay que mirar dos veces

**No se ejecutan sin decidirlas aparte.** Están aquí para que no se pierdan.

1. **Interacciones dentro del expediente.** Hoy hay dos superficies para la
   misma conversación: `/interacciones` (bandeja) y `/clientes/:id/contacto`
   (bitácora). La bitácora es la buena —la conversación pertenece al cliente—,
   pero la bandeja sirve para «qué hice esta semana». *Propuesta:* la
   conversación vive en el expediente y lo transversal se resuelve en
   Seguimiento.
2. **Seguimiento contra Inicio.** Las dos responden «qué tengo pendiente». El
   Inicio decide **qué resolver ahora** (5 asuntos); Seguimiento es **la lista
   completa con su plazo**. Se conservan si esa frontera se respeta; si no,
   Seguimiento es la cola del Inicio y sobra.
3. **Reportes contra Indicadores.** RF-017 es avance **por propiedad**;
   Indicadores es rendimiento **por actor**. *Propuesta:* pestaña dentro de
   Indicadores, que además le da el marco analítico que hoy no tiene.

---

## 4 bis. Lo que cambió tras congelar el modelo universal

Este documento se escribió antes que `decision-modelo-universal-propiedad-operacion.md`
(D-E4-1). Dos entradas cambian por su culpa, y es una mejora:

| Antes | Ahora | Por qué |
|---|---|---|
| ~~**Solicitudes** → **Expedientes**~~ | **Solicitudes**, sin cambio | **Revertido el 2026-08-18 al implementar el Corte 2.** Ver abajo |
| — | **`+ Registrar`** | el alta deja de ser una entrada por objeto y pasa a ser un motor de preguntas (D-E4-2) |

> **Por qué se revirtió el renombre a «Expedientes».** *Expediente* es la
> **profundidad de navegación** de un objeto, no el nombre del objeto:
> `mapa-pantalla-dominio-backend.md` distingue explícitamente bandeja
> (`/solicitudes`) de expediente (`/solicitudes/:codigo`), y el menú nombra
> bandejas. Llamar «Expedientes» a la bandeja rompía esa distinción en el único
> sitio donde estaba escrita.
>
> Lo que motivaba el renombre —que el cierre de una compraventa no es una
> «solicitud de alquiler»— sigue siendo cierto y se resuelve donde corresponde:
> `solicitud_alquiler.tipo` ya distingue `A` de `V` (V51), y **el detalle sí
> puede titularse «Expediente de solicitud»**. El objeto se llama solicitud; su
> ficha se llama expediente.

Y una que **se refuerza**: *Propiedades* deja de ser solo un rótulo mejor y pasa
a ser exacto — con el modelo universal, lo que hay en cartera son propiedades de
siete tipos, no locales.

---

## 5. La decisión de vocabulario que hay que tomar

La estructura objetivo dice **Propiedades**. El dominio dice **`LocalComercial`**,
el menú dice «Locales», la ruta es `/locales` y el negocio es el alquiler de
**locales comerciales**.

**Resuelta por D-E4-1.** La entidad del backend **ya se llama `Propiedad`**
—`propiedad`, con `tipo_inmueble ∈ {L,O,D,C,T,X}`— desde V4. El que va con
retraso es el rótulo: «Locales» describe **un tipo de los siete**, no la
cartera.

| | |
|---|---|
| **Rótulo del menú** | **Propiedades** |
| **Entidad** | `Propiedad` (ya existe) |
| **Tipo** | `LOCAL_COMERCIAL` es uno de siete |
| **Ruta** | `/propiedades`, con `/locales` redirigiendo mientras dure la migración |

Lo que queda pendiente no es esta palabra sino **el glosario completo**:
`Encargo` (hoy `Captación`), `Expediente` (hoy `SolicitudAlquiler`),
`Titularidad`. Sale del modelo universal y **bloquea el Corte 2**: no se
renombra nada en 57 pantallas antes de tenerlo escrito.

---

## 6. Arquitectura visual

### 6.1 Escritorio

- **Ancho fijo**, logo **BROX** arriba, navegación al centro, cuenta al pie.
- **Iconografía lineal**, una por entrada. Hoy no hay: hay un punto de 6 px
  igual para las 26, y en colapsado no quedaría nada que leer.
- **Agrupadores discretos**: versalita tenue, sin filete ni caja.
- **Sin submenús** en esta versión: con 13–16 entradas no reducen ruido, lo
  esconden.

### 6.2 Estado activo — una sola señal

Hoy hay **tres a la vez**: fondo dorado al 14 %, texto blanco y punto dorado.

**Decisión:** *superficie activa ligeramente elevada + texto de mayor contraste*.
El icono acompaña en el mismo tono del texto. **Ni barra lateral, ni glow, ni
borde, ni icono coloreado.** Una señal basta, y la que se elige es la que
sobrevive al modo colapsado.

### 6.3 Colapsado

Se reduce a iconos **sin alterar el layout de la página**: el contenido no se
reflow-ea, solo cambia el ancho de la columna. **Tooltip obligatorio** en cada
entrada, con la etiqueta completa.

### 6.4 Móvil

**No se comprime: se convierte en drawer.** Hoy no existe —`shell.scss` no tiene
una sola media query— y el armazón es `grid 15.5rem 1fr` a cualquier ancho.

---

## 7. Color

Se mantiene la decisión ya trabajada, y se escribe para que no se reinterprete:

| Elemento | Regla |
|---|---|
| **Lateral** | oscuro. Sin degradado nuevo, sin luces, sin gradientes de acento |
| **Contenido** | claro |
| **Azul BROX** | marca, selección y recomendación. Con moderación |
| **Celeste** | acento o señal tecnológica, nunca estado |
| **Verde · ámbar · rojo** | **solo** con semántica de estado |
| **Oro** | identidad premium. **Nunca** indicador operativo |

> Hoy el oro **sí** es indicador operativo: es el fondo del estado activo del
> menú (`rgba(217,164,65,.14)`) y el color del punto. Eso cambia con §6.2.

El instrumento antes que el tablero: si una decisión visual se puede describir
como «queda espectacular» y no como «esto significa X», no entra.

---

## 8. Lo que esta decisión NO cambia

- **`core/auth/acceso.ts` sigue siendo la fuente del menú.** Se le añaden
  campos (icono, `operacionEntrada`, `operacionSalida`), no se sustituye.
- **Los guards siguen leyendo ese mismo mapa.** Menú y guard no pueden divergir
  porque son el mismo dato.
- **Las 37 rutas sin entrada de menú siguen sin ella.** Detalles y formularios
  se alcanzan desde su listado; lo que cambia es que el contexto lo dará el
  `BroxPageHeader`, no una miga escrita 51 veces.
- **El backend no cambia por esto.** Ninguna entrada nueva pide un endpoint que
  no exista: Requerimientos y Evaluaciones consumen recursos ya publicados.

---

## 9. Criterios de aceptación

1. Ningún rol ve más de **16** entradas primarias.
2. Ninguna entrada del menú es una **cola de trabajo**.
3. Ninguna entrada existe solo por **alcance**.
4. Las tres grandes categorías de negocio —Oferta, Demanda, Cierre— son
   **idénticas** para agente, broker y administrador.
5. Toda entrada declara su **operación de entrada**, y una prueba comprueba
   `visible ⊆ permitido` contra la matriz para los tres roles.
6. El estado activo se distingue con **una** señal.
7. El menú colapsa a iconos **sin mover** el contenido de la página.
8. En móvil el menú es **drawer**, no una columna estrecha.
9. **Ningún hex** en el SCSS del armazón: todo por token.
10. El rótulo de la página **no** vive en el shell como literal, y **no** se
    repite como miga de pan dentro de cada pantalla.
