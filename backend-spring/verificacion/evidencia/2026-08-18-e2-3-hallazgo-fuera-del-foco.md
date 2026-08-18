# E2.3 — El hallazgo sale de la cola (2026-08-18)

**Qué cierra:** una coincidencia de cartera deja de ser una tarea. `foco[]` queda
para lo accionable; `hallazgos[]` nace como colección propia del Inicio.

**Qué NO abre:** matcher v2, E5, Radar ni interpretación adicional. El motor es
exactamente el que ya existía.

---

## 1. El principio

```
una TAREA     dice «hay algo que debes resolver»
un HALLAZGO   dice «encontré algo que vale la pena mirar»
```

Una coincidencia de cartera puede ser extraordinariamente valiosa **sin ser una
obligación**. Mientras viajó dentro de la bandeja compitió con una solicitud
pendiente, una captación por decidir y un seguimiento vencido — y les ganaba,
porque la política de despacho la trata como ocasión, que lo es.

**Medido antes de tocar nada:** el agente `vmora` abría su Inicio y encontraba
**22 sugerencias por delante de todo lo que le reclamaba algo**. Los cinco
puestos del foco eran cinco coincidencias.

No se arregla bajando su peso: se arregla sacándola de la colección equivocada.
Un hallazgo que no se atiende no deja nada a medias; una solicitud sin cerrar, sí.

---

## 2. Mismo motor, otra salida

El bucle se movió **entero** de `TareaServiceImpl` (séptimo disparador) a
`HallazgoServiceImpl`, sin tocar la evaluación:

| Se conserva | |
|---|---|
| la evidencia | `CoincidenciaCartera.evaluar` — el mismo puntaje y los mismos criterios que el panel de coincidencias |
| el umbral | `PoliticaComercial.valeLaPenaProponer` |
| la deduplicación | por par cliente-captación, contra las oportunidades ya propuestas |
| el destino | `cliente-detail/{id}`, que es donde vive el panel de propiedades compatibles |

**No hay un segundo matcher, y el gate lo impide.**

### Lo que el hallazgo lleva encima

```json
{
  "id": "COINCIDENCIA_DE_CARTERA:77:500",
  "titulo": "Av. Pardo 1120",
  "porQue": "Cruza 4 de 5 criterios; queda fuera en rubro: busca cafeteria, local permite agencia bancaria.",
  "puntaje": 80,
  "cumple": [...], "noCumple": [...],
  "destino": "cliente-detail/77"
}
```

- **Identidad estable**: se compone de los dos extremos que producen la
  coincidencia. Ni un contador —cambiaría en cada recarga y la pantalla no podría
  recordar que ya lo miraste— ni un hash del texto, que convertiría el mismo
  hallazgo en otro al cambiar una palabra del rótulo.
- **`porQue` lo redacta el dominio.** Si esa frase se compusiera en Angular,
  KAIROS tendría que escribir la suya para decir lo mismo por WhatsApp, y las dos
  empezarían a divergir.
- **El pero se declara.** Un hallazgo que solo presume se decide peor que uno
  honesto: quien lo lee va a descubrir el pero de todos modos, y mejor antes de
  llamar al cliente.

### No se persiste

Un hallazgo es una lectura del estado de hoy, no un hecho ocurrido. Guardarlo
obligaría a mantenerlo al día cada vez que cambie un requerimiento o se retire un
local, y **un hallazgo obsoleto es peor que ninguno**: manda a proponer algo que
ya no encaja.

---

## 3. Las 22 que ya estaban escritas — V63

Se retiran como **CANCELADAS (`A`)**, no como completadas.

`REQUERIMIENTO` está en `ENTIDADES_AUTO`, así que al dejar de derivarse el propio
servicio las habría marcado `COMPLETADA` en la siguiente lectura — y eso sería
falso: nadie las completó, nadie propuso esas oportunidades. El histórico diría
que el agente resolvió 22 asuntos que en realidad se le retiraron de la mesa.

`CANCELADA` además **bloquea la recreación para siempre**, que aquí es
exactamente lo que se quiere.

```
tarea PROPONER_OPORTUNIDAD | estado A | 22     <- retiradas
```

Las que alguien completó de verdad no se tocan: son historia cierta.

---

## 4. Verificación

```
backend  865 pruebas · 0 fallos · 0 SKIPPED
Angular  565 / 565
```

### El gate

`HallazgoFueraDelFocoTest`, dos comprobaciones:

| Test | Qué impide |
|---|---|
| `laBandejaNoPuedeVolverAEvaluarCoincidencias` | que `TareaServiceImpl` importe `CoincidenciaCartera`, `HallazgoService` o el repositorio de requerimientos |
| `ningunDisparadorEmiteCoincidencias` | que un disparador emita `Tarea.PROPONER_OPORTUNIDAD` |

**Mira el import y no una lista de tipos** porque la forma de recaer no es
escribir el nombre de la tarea: es volver a llamar al matcher desde el motor de
la bandeja. Si el servicio de tareas no puede evaluar una coincidencia, no puede
convertirla en tarea.

Comprobado que muerde: añadiendo el import de `CoincidenciaCartera` a
`TareaServiceImpl`, falla nombrándolo.

### Un NPE real que el test destapó

`Map.of()` —lo que devuelve `LectorPorAutoridad.deVarias` cuando no hay nada que
hidratar— **lanza NPE al preguntarle por una clave nula**, incluso desde
`getOrDefault`. El `getOrDefault(idPropiedad, vacio())` que venía de E2.2 parecía
seguro y reventaba justo en el caso menos probado: cartera sin atributos y una
captación sin propiedad.

Resuelto en un solo sitio —`LectorPorAutoridad.de(lote, id)`— y aplicado a los
**cuatro** consumidores que tenían el mismo patrón.

---

## 5. La comprobación visual

`GET /dashboard` como el agente `vmora`:

```
ANTES (E2.2)                          AHORA
foco: 5 coincidencias MEDIA           foco: 19 asuntos, los 5 primeros ALTA
      (22 por delante de todo)              RECONTACTO · VISITA · VISITA · VISITA · VISITA
hallazgos: no existían                hallazgos: 22, con su evidencia
```

**El `prioridad` que E2.2 dejó anotado se leyó solo.** Aquellos `MEDIA` por
encima de `ALTA` eran las coincidencias ganando por ser ocasión; al salir del
foco, la bandeja vuelve a leerse coherente. La deuda sigue declarada, pero ya no
se ve — y por eso no hacía falta tocarla dentro de E2.3.

### Cambiar la evidencia cambia el hallazgo, y el foco no se mueve

En `/dashboard`, sin tocar una línea de TypeScript:

```sql
UPDATE detalle_local_comercial SET rubro_permitido = 'Cafeteria y panaderia'
 WHERE id_propiedad = 21;
```

```
hallazgo antes    Av. Pardo 1120 — Cruza 4 de 5 criterios; queda fuera en rubro:
                  busca cafeteria, local permite agencia bancaria.
hallazgo después  Av. Pardo 1120 — Cruza 5 de 5 criterios, sin ningún pero.

foco antes        ALTA PRO-0002 · ALTA 30 jul · ALTA 01 ago · ALTA 02 ago · ALTA 03 ago
foco después      ALTA PRO-0002 · ALTA 30 jul · ALTA 01 ago · ALTA 02 ago · ALTA 03 ago
                  ^ idéntico, fila por fila
```

Evidencia restaurada después.

---

## 6. La superficie

Va **después** de la bandeja y separada, con canto verde —es una buena noticia—,
sin numeración (no compite por un puesto) y sin semáforo (no hay nada vencido):

```
LO QUE BROX ENCONTRÓ  22
No te reclaman nada; están aquí por si valen la pena.

  Av. Pardo 1120
  Cruza 4 de 5 criterios; queda fuera en rubro: busca cafeteria…
  CAP-0002
```
