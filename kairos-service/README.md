# KAIROS — asistente conversacional

Prototipo de un canal conversacional sobre las capacidades públicas de BROX: registrar una
propiedad hablando, retomar un borrador a medias, consultar una ficha.

**Es un proyecto aparte, y eso es la decisión, no un accidente.** No comparte proceso, ni base de
datos, ni jar con BROX; queda fuera del reactor de `backend-spring/`. Comparte **el contrato**, y
la dependencia va en una sola dirección: KAIROS conoce a BROX, BROX no conoce a KAIROS. Lo
sostiene una prueba, `FronteraKairosTest`, que rompe el build de BROX si alguien invierte la
flecha.

Que sea un servicio separado es lo que permite que el mismo Core atienda a la SPA y a un canal de
mensajería sin que ninguno de los dos herede las decisiones del otro.

```bash
mvn -f kairos-service/pom.xml test
```

Expone `POST /kairos/turnos` y habla con la API en `http://localhost:8090/controllocal/Api`,
configurable con `brox.url`.

## Qué está construido y qué no

Hay intérprete determinista, máquina de conversación, cliente HTTP con trazas y seis acciones
declaradas —consultar propiedad, iniciar captura, continuar borrador, buscar persona, registrar
propietario, registrar interacción—.

**Lo que falta para que una conversación termine de verdad:**

- **`GET /capacidades` no existe en BROX.** KAIROS lo pide para saber qué puede hacer en cada
  sesión, y hoy no hay nadie al otro lado. Diseñar ese endpoint —representación, alcance por
  sesión, confirmaciones, versionado— es una decisión pendiente.
- **No hay adaptador de WhatsApp** en el repositorio. Si algún comentario del código habla de esa
  integración en presente, describe el objetivo, no el estado.
- **La procedencia y la idempotencia llegan más lejos que el servidor**: KAIROS envía trazas e
  `Idempotency-Key` al registrar propietarios e interacciones, pero esos controladores todavía no
  los consumen como sí hacen los de captura y propiedad.
- **Falta decidir la autenticación entre servicios** y cómo se resuelve la identidad humana en un
  canal como mensajería.

Mientras esas cuatro cosas no se resuelvan, esto es un prototipo que demuestra la forma, no un
canal en operación. La decisión de si KAIROS es exploración, producto o base contractual está
abierta y se resolverá en la etapa E6 del
[mapa de ejecución](../docs/ai/mapa-ejecucion-brox.md).
