# D-E2-3 · Dónde se crea un propietario

**Qué responde:** por qué `Propietarios` faltaba en la navegación, dónde va, y
por qué el **alta** no se hace desde ahí.

**Estado:** decidido el 2026-08-12.
**Verificado sobre:** `frontend-angular` (4 rutas de propietario ya existen),
`PropietariosController` (`POST /propietarios`, rol AGENTE) y
`local-form.html` (paso 1: vincular propietario).

---

## 1. Lo que faltaba, y lo que no

Faltaban **dos cosas distintas**, y solo una es la del lateral:

| | Hueco | Gravedad |
|---|---|---|
| 1 | El catálogo de propietarios **existe y no se puede alcanzar** desde la navegación | claro, y de una línea |
| 2 | Si el propietario no existe, el alta del local **obliga a abandonar el formulario** | el caro |

---

## 2. La asimetría lo delataba

El lateral agrupa por el flujo del negocio y en **Demanda** la contraparte
—`Clientes`— está la primera. En **Oferta** no estaba su equivalente:

```
OFERTA                    DEMANDA
  Prospecciones             Clientes        ← la contraparte
  Captaciones               Oportunidades
  Locales                   Visitas
  ???
```

`Propietarios` va **al final de Oferta**, no al principio. Y es deliberado: en la
práctica se entra por prospección o captación, y el catálogo es **consulta y
mantenimiento**, no punto de partida. Poner al propietario primero sugeriría que
el trabajo empieza dando de alta personas, y no empieza ahí.

---

## 3. El alta **no** se hace desde el menú

> **Nadie crea un propietario en abstracto. Se crea porque estás registrando su
> local.**

Un «Nuevo propietario» en el lateral produce **fichas huérfanas**: personas sin
ningún inmueble, que nadie mantiene, que ensucian la búsqueda del formulario de
local y que hacen que el contador `cantidadLocales` sea 0 para siempre. Es el
clásico registro huérfano, y en una agenda comercial se paga caro porque la
búsqueda de propietario es el paso 1 de toda captación.

### 3.1 Se crea donde hace falta

El alta de local (`/locales/nuevo`) ya tiene su **paso 1 — Propietario del
local**, pero hoy solo busca **entre los ya cargados**. Cuando el dueño no está,
el agente tiene que salir, ir a `/propietarios/nuevo`, crearlo y **volver a
empezar el formulario**. Eso es justo el momento en que el dato es fresco —está
delante del local, hablando con el dueño— y es cuando más caro sale perderlo.

Lo que debe pasar:

```
Buscar propietario
[ Aliaga                                        ]
Sin resultados para «Aliaga».
  + Registrar a Aliaga como propietario nuevo
```

Un panel lateral con **los cuatro campos mínimos** —tipo de persona, nombre o
razón social, documento, teléfono—, se guarda, **queda seleccionado**, y el
formulario del local **no se pierde**. `POST /propietarios` ya existe y es del
rol AGENTE, así que el cable está.

### 3.1.1 ¿Y desde prospecciones? **No, y lo decide el dominio**

Parece que tendría sentido —una prospección es el primer contacto con un
propietario— pero el cable dice otra cosa:

```java
public record ProspeccionRequest(Long idLocal, String observaciones) {}
```

**`POST /prospecciones` exige `idLocal`.** Una prospección se abre *sobre un
local que ya existe*, y ese local **ya trae su propietario** — es campo
obligatorio de `LocalRequest`. Cuando llegas a la pantalla de prospecciones, el
propietario ya está creado por definición.

Y el otro sentido tampoco existe: no hay ruta ni formulario de «nueva
prospección» en el SPA. La prospección **nace sola** al registrar el local; el
propio formulario lo dice: *«Registra el inmueble y su propietario. El backend
abrirá la prospección inicial.»*

> **Conclusión:** hay **un solo punto** en todo el flujo donde un propietario
> puede no existir y hacer falta — el paso 1 del alta de local. Añadir el alta a
> prospecciones sería ponerla en una pantalla que no crea nada.

### 3.1.2 Lo que se implementó

En `local-form`, cuando la búsqueda no encuentra a nadie:

- Aparece **`+ Registrar a «Aliaga» como propietario nuevo`**, con lo que ya
  escribió.
- Se abre un **panel dentro del paso 1** — no un modal: un modal taparía el
  formulario y haría dudar de si lo escrito sigue ahí, que es justo el miedo que
  esto viene a quitar.
- **Cuatro campos**: tipo de persona, nombre o razón social, documento y
  teléfono. Persona jurídica fuerza RUC, igual que el alta completa.
- Lo que se tecleó en la búsqueda **se reaprovecha**: si son dígitos va al
  documento, si no, al nombre. Nadie escribe lo mismo dos veces.
- Al guardar, el propietario **queda seleccionado** y el formulario del local no
  se toca. Si no seleccionara, el desvío seguiría ahí, solo que más corto.
- **Duplicado por documento**: si ya existe uno con ese número entre los
  cargados, lo selecciona en vez de crear otro. El backend no lo impide y una
  persona repetida ensucia la búsqueda de toda captación futura.

### 3.2 El catálogo sirve para lo que sirve un catálogo

Consultar, corregir un dato, ver qué inmuebles tiene una persona y **detectar
duplicados**. Ahí sí cabe un «Nuevo propietario» —para el caso legítimo de dar
de alta a alguien con varios inmuebles antes de registrarlos—, pero **dentro de
la pantalla, no en el lateral**.

---

## 4. Consecuencia para el Inicio

Ninguna. Los cuatro accesos rápidos del agente (D-E2-1 §6.1) siguen siendo los
correctos: `Nueva prospección` abre el alta del local, y **ahí dentro** es donde
el propietario se resuelve. Añadir «Nuevo propietario» a los accesos sería
volver a la creación en abstracto por la puerta de atrás.

---

## 5. Por qué esta pantalla **no** se prototipa

El Inicio tiene maqueta porque **no existe en código**. Esta sí: `local-form` es
un componente real, con sus pruebas. Duplicarla en un artifact crearía dos
fuentes de verdad para la misma pantalla y la maqueta empezaría a mentir en
cuanto alguien tocara el componente — que es justo el problema que se quería
evitar.

**La forma de revisarla es correrla**, y para eso hace falta la API arriba:

```bash
docker compose -f backend-spring/docker-compose.yml up -d
```

Con eso y `npm --prefix frontend-angular start`, la pantalla está en
`/locales/nuevo`. **No hacen falta credenciales especiales**: las de siembra
están en `CLAUDE.md` y sirven (`vmora`…/Agente2026 para el rol AGENTE, que es el
único que puede registrar).

---

## 6. Checklist

- [x] `Propietarios` al final del grupo **Oferta** del lateral
- [x] **Creación en contexto** en el paso 1 del alta de local, con los cuatro
      campos mínimos y el propietario seleccionado al volver
- [x] **Duplicado por documento**: si ya existe uno con ese número entre los
      cargados, se selecciona en vez de crear otro
- [x] Seis pruebas nuevas en `local-form.spec.ts`; suite completa **556/556**
- [ ] `Nuevo propietario` **dentro** de `/propietarios`, no en el lateral
- [ ] Duplicado por documento **en el servidor**: hoy la comprobación es solo
      sobre los propietarios ya cargados en el formulario, así que dos agentes
      simultáneos todavía pueden crear la misma persona. Va con `POST
      /propietarios`, no con la pantalla.
