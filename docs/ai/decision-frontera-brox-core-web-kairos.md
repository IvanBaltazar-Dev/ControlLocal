# D-A-1 · La frontera: BROX, BROX Core, BROX Web y KAIROS

**Qué congela:** los cuatro nombres con los que se habla de este producto, qué
decide cada pieza y qué no puede decidir. A partir de aquí, «el backend» y «el
frontend» dejan de ser la forma de nombrar las partes.

**Estado:** congelado. Protegido por tres gates que rompen la compilación
(§6), no por una convención escrita.

**Relacionado:** `decision-modelo-universal-propiedad-operacion.md` (D-E4-1) —
el modelo que BROX Core defiende; `decision-motor-de-registro.md` (D-E4-2) — la
máquina de preguntas que las dos interfaces consumen;
`decision-kairos-contrato-de-acciones.md` (D-K-1) — qué puede ejecutar KAIROS;
`decision-autoridad-de-cada-dato.md` (D-E4-3) — qué puede saber una interfaz de
dónde vive un dato.

---

## 1. Por qué hace falta nombrarlo

Mientras las partes se llamen «backend» y «frontend», la pregunta *¿dónde va
esta regla?* se responde por comodidad: donde sea más rápido escribirla. Y la
respuesta cómoda casi siempre es la pantalla, porque ahí se ve el efecto.

El coste llega después y llega dos veces: la primera cuando la regla hay que
cambiarla en dos sitios, y la segunda cuando aparece un canal que no es una
pantalla —WhatsApp, voz, un modelo— y descubre que la mitad del negocio estaba
escrita en un componente de Angular.

Esta decisión no reorganiza código. **Nombra lo que ya existe** y fija de qué
lado de la frontera cae cada tipo de decisión.

---

## 2. Los cuatro nombres

### BROX

El producto completo. No significa ni Angular ni Spring: significa la
plataforma. Cuando se dice *«BROX ya admite venta»*, se está hablando de la
capacidad, no de dónde está escrita.

### BROX Core

**El núcleo operacional y de dominio.** Es donde vive la verdad.

| | |
|---|---|
| dominio inmobiliario | propiedad, encargo, titularidad, oportunidad, expediente |
| casos de uso | alta universal, captación, evaluación, cierre, comisión |
| políticas | `PoliticaComercial`, umbrales, plazos, despacho del Inicio |
| permisos | matriz operación → rol, alcance por rol, tenancy |
| motor de captura | qué se pregunta, qué falta, si ya hay suficiente |
| matcher | qué encaja con qué, y por qué |
| histórico y trazabilidad | hitos económicos, `historial_estado`, procedencia |
| idempotencia | un reintento no duplica |
| persistencia | PostgreSQL, Flyway, outbox de eventos |
| contratos | los endpoints REST y lo que prometen |

> **BROX Core decide qué es verdad y qué está permitido.**
> Las interfaces no vuelven a implementar esas decisiones.

Hoy son los cinco módulos de `backend-spring/`.

### BROX Web

**La aplicación Angular.** Una interfaz sobre BROX Core, no «el otro medio
sistema».

| Le corresponde | No le corresponde |
|---|---|
| presentar | decidir reglas inmobiliarias |
| navegar | decidir qué atributos aplican a cada tipo |
| capturar la intención | inventar validaciones de dominio |
| mostrar información | inferir la operación |
| ejecutar casos de uso | duplicar políticas del backend |
| representar la pregunta | saber dónde se persiste un dato |

La línea fina, porque es la que se cruza sin darse cuenta: **nombrar un dato no
es decidir sobre él**. Un formulario con un campo `dormitorios` es lenguaje
inmobiliario y está bien. Un formulario que decide *que a una casa se le
preguntan dormitorios* es una regla de negocio, y esa vive en el catálogo.

### KAIROS

**Otro cliente de BROX Core**, independiente de BROX Web. Puede vivir en
WhatsApp, en Python, en texto, audio, voz o imágenes, y puede apoyarse en
modelos de IA.

Lo que no puede tener es lógica inmobiliaria propia. KAIROS **no escribe en
PostgreSQL**: ejecuta exactamente los mismos casos de uso que ejecutaría una
persona desde BROX Web, con la identidad, los permisos y la trazabilidad de esa
persona. El catálogo de lo que puede invocar está en D-K-1.

---

## 3. La forma

```
                       BROX CORE
                    verdad + negocio
                           │
                    API / casos de uso
                  ┌────────┴────────┐
                  │                 │
              BROX WEB           KAIROS
               Angular        Python / IA /
                               WhatsApp
```

**Dos hechos que esta forma afirma, y que hay que poder sostener:**

1. **BROX Web y KAIROS no dependen el uno del otro.** KAIROS no se construye
   encima de Angular; se puede evolucionar cualquiera de los dos sin tocar el
   otro.
2. **La dependencia va en un solo sentido.** Las interfaces conocen las
   capacidades públicas de BROX Core; BROX Core no conoce ninguna interfaz.

Lo que se gana es concreto: una regla nueva —«un terreno también pide
zonificación»— se escribe **una vez**, en el catálogo, y aparece igual en la
pantalla y en la conversación. Con la regla en la interfaz habría que
desplegarla dos veces y las dos copias empezarían a separarse en la tercera
semana.

---

## 4. Dónde cae cada decisión

La pregunta útil no es «¿esto es backend o frontend?», sino **«si mañana esto
cambia, ¿cuántos sitios hay que tocar?»**. Si la respuesta honesta es «dos»,
está en el lado equivocado.

| Decisión | Dónde vive | Por qué |
|---|---|---|
| Qué campos aplican a un tipo de propiedad | **Core** — catálogo | añadir *Almacén* tiene que ser una fila, no un despliegue |
| Qué campos son obligatorios | **Core** — catálogo | es lo que impide publicar una ficha coja |
| El rango de un valor | **Core** — catálogo | una regla con dos dueños es una regla rota (D-E4-3) |
| Si el importe se llama «precio de venta» o «renta mensual» | **Core** | 180 000 y 2 900 no se distinguen por magnitud |
| Qué es un asunto que reclama atención | **Core** — política | E1 ya sacó los umbrales de Angular |
| Qué se puede hacer con un rol | **Core** — matriz | vigilada por `MatrizOperacionRolTest` |
| En qué orden se muestran los bloques | **Web** | es presentación |
| Si un bloque se pinta plegado o abierto | **Web** | es presentación |
| Qué texto acompaña a un campo obligatorio | **Web**, con el `porQué` del Core | el hecho lo da el Core; el tono es de la interfaz |
| Cómo se dibuja un `SELECTOR` | **Web** | el Core dice el control, no el HTML |

**El caso frontera, resuelto:** BROX Web muestra *Tipo* y luego *Operación*
porque es como piensa una persona —«tengo un departamento y quiero venderlo»—.
Eso es orden de presentación y le corresponde. Lo que **no** cambia es el
dominio: la propiedad no tiene operación, y las dos respuestas se recogen al
principio únicamente para que BROX Core pueda construir el plan de preguntas.

---

## 5. Las dos reglas que quedan escritas

Son las que gobiernan todo el trabajo posterior. Cualquier cambio que las
contradiga está mal aunque las pruebas estén verdes.

> **1. Una propiedad es la cosa física. La operación vive en el Encargo. Un
> mismo inmueble puede tener simultáneamente un Encargo de Venta y otro de
> Alquiler.**

> **2. BROX Core es la autoridad funcional. BROX Web y KAIROS son interfaces
> independientes que ejecutan sus casos de uso. Ninguna interfaz reimplementa el
> negocio.**

---

## 6. Cómo se protegen

No con esta página. Con tres gates que corren en el cierre:

| Gate | Qué impide |
|---|---|
| `FronteraKairosTest` | que BROX acabe necesitando a KAIROS para arrancar, y que un proveedor de IA entre en el dominio o el servicio |
| `FronteraDeAutoridadEnElSpaTest` · *no nombra estructuras de almacenamiento* | que BROX Web sepa **dónde** vive un dato |
| `FronteraDeAutoridadEnElSpaTest` · *no decide qué se pregunta por tipo* | que BROX Web tenga su propia matriz «tipo → campos» — la forma concreta en que la regla 2 se rompe en un alta |

El tercero es el que añade esta decisión, y vigila lo que de verdad pasa
cuando alguien tiene prisa: un `if (tipo === 'CASA') { mostrarDormitorios() }`
dentro de un componente. Eso no es un atajo de maquetación; es el catálogo
reescrito en Angular, y a partir de ahí hay dos catálogos.

**Lo que el gate permite, para que no se lea de más:** BROX Web puede nombrar
`dormitorios`, puede pintar un control distinto para un `SELECTOR` que para un
`INTERRUPTOR`, y puede agrupar los campos que el Core devuelve en las familias
que el Core declara. Lo que no puede es **decidir la pertenencia** de un campo a
un tipo de propiedad.

---

## 7. Qué NO decide esta página

| Fuera | Dónde se decide |
|---|---|
| Qué puede ejecutar KAIROS y qué se confirma | D-K-1 |
| El modelo de dominio | D-E4-1 |
| Qué se pregunta en un alta | D-E4-2 y el catálogo |
| La navegación y el `+ Registrar` | D-E3-1 |
| El lenguaje de los rótulos | `decision-lenguaje-natural-de-negocio.md` |

---

## 8. Criterios de aceptación

1. Los cuatro nombres se usan en la documentación y en los mensajes de commit.
2. Ninguna clase de BROX Core depende de KAIROS ni de un proveedor de IA.
3. BROX Web no nombra ninguna estructura de almacenamiento.
4. BROX Web no contiene ninguna matriz «tipo de propiedad → campos».
5. Una regla nueva sobre qué se pregunta se escribe **una vez** y aparece en las
   dos interfaces sin tocar ninguna de las dos.
