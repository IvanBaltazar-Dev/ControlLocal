# Decisión — estados de esquema sin operación productora (D-B7-1)

**Fecha: 2026-08-08.** Cierra el punto **3.9** del plan maestro y la **decisión #7** del
`diagnostico-estados-valores-economicos-y-fixtures.md`, cuya §2.1 queda **superada por este
documento**.

El plan pedía «implementar, retirar o documentar cada uno — pero que la UI no prometa acciones
inexistentes». Esto hace las tres cosas: verifica cuáles siguen huérfanos, decide qué se hace con
cada uno y deja constancia de por qué la UI ya cumple.

---

## 1. Lo primero: la lista de §2.1 estaba desactualizada

Se verificó código por código contra `backend-spring` a 2026-08-08. **Cuatro de las diez filas ya
no son ciertas**: el Bloque 7 (ciclo contractual) y el trabajo de comisiones les dieron productor y
nadie volvió a tocar el diagnóstico.

La tabla cubre **todo** el vocabulario de los ocho procesos, no solo los huérfanos: clasificar
únicamente lo que falla es como se llegó a la lista anterior. Una fila por proceso y respuesta.

| Proceso | Códigos | ¿Productor hoy? | Evidencia |
|---|---|---|---|
| Prospección | `P` `C` `R` `S` `T` `D` | ✅ **Sí** | `InteraccionServiceImpl` y `ProspeccionServiceImpl` los transicionan |
| Prospección | `E` (Propuesta entregada) | ❌ **No** | `InteraccionServiceImpl` ~300: el resultado `PROPUESTA_ENVIADA` marca la fecha y transiciona a **`SEGUIMIENTO`**, no a `E` |
| Captación | `P` `O` `R` `A` `C` | ✅ **Sí** | `CaptacionServiceImpl`: alta, revisión del broker y cierre |
| Captación | `V` (Vencida) | ❌ **No** | aparece **solo** en el grafo (`MaquinasEstado`) |
| Oportunidad | `A` `S` `N` `F` | ✅ **Sí** | alta, creación de solicitud, no continuidad y cascada de cierre exitoso |
| Oportunidad | `X` (Finalizada no favorable) | ❌ **No** | aparece **solo** en el grafo |
| Solicitud | `G` `E` `O` `A` `R` `C` | ✅ **Sí** | alta, revisión, evaluación del broker y cierre del alquiler |
| Solicitud | `D` (Desistida) | ❌ **No** | su única aparición es una **lectura** de KPI en `SolicitudServiceImpl` |
| Contrato | `P` `D` `V` `R` `F` `S` `A` | ✅ **Sí, los siete** | `ContratoServiceImpl`: nacimiento en `EN_PROCESO` y transiciones a firmado, vigente, renovado, finalizado, rescindido y anulado |
| Comisión | `P` `R` `C` `A` | ✅ **Sí, los cuatro** | `ComisionServiceImpl`: `R` (Parcial) lo **calcula el saldo** tras registrar un movimiento |
| Tarea | `P` `C` `A` | ✅ **Sí** | `Tarea`: nace `PENDIENTE` y se marca completada o cancelada |
| Tarea | `E` (En proceso) `V` (Vencida) | ❌ **No** | nadie las escribe; no hay paso intermedio ni vencimiento automático |
| Alerta | `A` `T` | ✅ **Sí** | `Alerta` nace activa y se marca atendida |
| Alerta | `D` (Descartada) | ❌ **No** | solo existe la constante |

Fuera de la tabla, porque **no son estados** sino tipos de entrada que el usuario elige, y los dos
resultaron tener productor: **resultado de propuesta `S` (Recontactar)**, que `InteraccionServiceImpl`
admite, y **evaluación de tipo `P` (Preliminar)**, que `EvaluacionServiceImpl` acepta.

**Quedan seis códigos sin productor**, no diez.

> **Trampa de lectura**: los códigos son **locales a cada agregado**. El mismo carácter significa
> cosas distintas — `R` es *Reunión* en prospección, *Rechazada* en captación, *Renovado* en contrato
> y **Parcial** en comisión—. No hay un significado global y no hay que buscarlo.

---

## 2. Decisión, uno por uno

### 2.1 Prospección `E` — **documentar y congelar**

No es un olvido: es **una réplica deliberada de la v1**, que tampoco lo emite nunca. El camino
natural (`PROPUESTA_ENVIADA` desde una interacción) marca la fecha de propuesta y salta a
`EN_SEGUIMIENTO`.

Emitir `E` **cambiaría el `estado` que viaja por el cable** en una respuesta existente, así que
**no cabe en el Bloque 7**, que por definición no rompe el contrato. Va al **Bloque B de la Fase 3**,
después de descongelar.

Y el dato no se pierde mientras tanto: `RESULTADO_PROPUESTA` (`P/A/R/S`) es la marca real de
«propuesta entregada», y así está anotado en `codigos.ts`.

### 2.2 Captación `V` — **documentar; candidato a implementar, pero necesita infraestructura**

El caso de uso es legítimo: una captación cuyo `fecha_fin_encargo` pasó debería vencer sola. Lo que
no existe hoy es **quien lo dispare**: no hay planificador en el sistema. Introducirlo no es añadir
un endpoint, es añadir una pieza de infraestructura con su propio comportamiento en varias
instancias — justo la decisión que el **Bloque 9** (arquitectura productiva) tiene que tomar antes.

Se deja **explícitamente pendiente y con dueño: Bloque 9**, no en el aire.

### 2.3 Oportunidad `X` — **candidato a retirar**

`N` (No continúa) ya cubre «no prosperó», y con más información: lleva `MotivoNoContinuidad`. `X`
es vocabulario duplicado sin semántica propia.

Retirarlo toca un `CHECK` y el catálogo que el SPA usa para **etiquetar** filas existentes, así que
se hace **después del corte** (cuando se sepa si el backfill trajo alguna fila con `X`). Hasta
entonces se mantiene y se documenta.

### 2.4 Solicitud `D` — **el único implementable hoy**

«El cliente desistió» es un hecho real del negocio, el grafo ya lo admite desde `REGISTRADA`,
`OBSERVADA`, `EN_REVISION` y `APROBADA`, y **construirlo es puramente aditivo**: un `POST` nuevo,
sin tocar ninguna respuesta existente.

**No se implementa en esta tanda porque exige decisiones de producto que no son técnicas**: qué rol
puede marcarlo (¿el agente que la registró, el broker, ambos?), si exige motivo y si arrastra la
oportunidad a `N`. Queda como **el ítem accionable número uno** del backlog de reglas de negocio,
con su fila de matriz pendiente.

### 2.5 Tarea `E` En proceso / `V` Vencida — **documentar**

La tarea **sí** tiene ciclo de vida persistido: nace `PENDIENTE` y se marca `COMPLETADA` o
`CANCELADA`. Lo que no existe son los dos estados intermedios:

- **`E` En proceso** no tiene caso de uso: una tarea de esta bandeja se resuelve haciendo el trabajo
  en la pantalla de la entidad (subir el documento, registrar la visita), no marcándola «empezada».
  Añadirlo daría un botón que solo mueve una etiqueta.
- **`V` Vencida** tiene el mismo problema que la captación vencida: **necesita quien lo dispare**, y
  no hay planificador. Además la bandeja ya distingue lo atrasado por la **fecha**, que es dato
  vivo, sin necesidad de congelarlo en un estado.

**No se implementan, y no están previstos.**

### 2.6 Alerta `DESCARTADA` — **documentar**

`ACTIVA` → `ATENDIDA` es el único camino con caso de uso pedido. «Descartar sin atender» no lo ha
pedido nadie y añadirlo daría al usuario una forma de esconder avisos sin resolverlos.

---

## 3. La UI ya cumple el requisito vinculante

El requisito literal era **«que la UI no prometa acciones inexistentes»**. Se verificó y **ya se
cumple**, sin cambios:

- Los desplegables de **acción** usan catálogos **restringidos**, no el vocabulario entero. El
  ejemplo a imitar es `ESTADO_CONTRATO_AL_CERRAR` (`V`, `D`): son exactamente los dos estados en los
  que un contrato puede nacer, y no los siete que existen.
- Los códigos huérfanos aparecen solo en dos sitios legítimos: como **etiqueta** (`describir`, que
  debe poder nombrar cualquier fila que llegue de la base) y como **valor de filtro** en listados.
- **Los filtros se dejan como están, a propósito.** Un filtro por «Vencida» devuelve cero y eso es
  honesto; recortarlos ahora **escondería filas legítimas después del backfill**, que sí puede traer
  esos códigos desde MySQL. Se revisan en E5, con datos delante.

---

## 4. Qué protege esto de aquí en adelante

`EstadosSinProductorTest` fija la tabla de la §1 contra el vocabulario real: si alguien añade un
estado a `EstadosDominio` o retira uno, el test falla hasta que la tabla vuelva a cuadrar. Es el
mismo mecanismo que `MatrizOperacionRolTest` usa para la matriz operación→rol, y por el mismo
motivo: **el documento que nadie verifica se desactualiza en semanas** — que es exactamente lo que
le pasó a la §2.1 del diagnóstico.

Lo que el test **no** comprueba, dicho sin adornos: no detecta si un estado *gana* productor. Las
constantes `static final String` de Java se **incrustan** en el bytecode, así que ni ArchUnit ni la
reflexión ven quién las usa. Detectarlo exigiría analizar el código fuente, y un analizador frágil
que falla sin motivo es peor que ninguno. La revisión de productores es **manual y con fecha**: la
de este documento es **2026-08-08**.
