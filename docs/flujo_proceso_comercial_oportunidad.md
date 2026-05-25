# Flujo comercial con oportunidad

Este flujo explica la trazabilidad completa del proceso comercial. La diferencia
principal frente al flujo anterior es que `OportunidadComercial` aparece antes de
la solicitud formal, para guardar actividad aunque el cliente no llegue a solicitar
el alquiler.

## Flujo operativo

1. Registrar local comercial y propietario.
2. Registrar captacion por agente inmobiliario.
3. Revisar captacion por broker.
4. Si el broker solicita ajustes, el agente corrige y vuelve a revision.
5. Si el broker rechaza, finaliza el proceso de captacion.
6. Si el broker aprueba, la captacion queda activa.
7. Registrar cliente interesado.
8. Crear oportunidad comercial con cliente, captacion y agente.
9. Registrar interacciones comerciales sobre la oportunidad.
10. Programar y ejecutar visitas sobre la oportunidad.
11. Decidir si el cliente continua.
12. Si no continua, registrar motivo de no continuidad y cerrar oportunidad.
13. Si continua, registrar solicitud de alquiler asociada a la oportunidad.
14. Registrar documentacion asociada a la solicitud.
15. Evaluar solicitud por broker.
16. Si se observa, actualizar solicitud/documentos y volver a evaluacion.
17. Si se aprueba, cerrar oportunidad como finalizada exitosa.
18. Si se rechaza, cerrar oportunidad como finalizada no favorable.

## Estados clave

Captacion:

- `P`: pendiente de revision
- `O`: observada
- `R`: rechazada
- `A`: activa
- `C`: cerrada
- `V`: vencida

Oportunidad comercial:

- `A`: abierta
- `S`: solicitud creada
- `N`: no continua
- `F`: finalizada exitosa
- `X`: finalizada no favorable

Las interacciones, visitas, solicitudes y motivos de no continuidad usan
`id_oportunidad` como referencia principal. El cliente y la captacion se obtienen
desde la oportunidad. El agente de la accion puede ser diferente, siempre que este
activo y disponible.

Solicitud de alquiler:

- `G`: registrada
- `E`: en revision
- `O`: observada
- `A`: aprobada
- `R`: rechazada
- `D`: desistida

## Prompt para recrear la imagen del flujo

```text
Genera un diagrama horizontal de flujo de proceso comercial para el sistema ControlLocal.
Usa un estilo limpio, profesional y académico, con cajas rectangulares para actividades,
rombos para decisiones y colores suaves: azul claro para actividades operativas, verde
para revisiones/aprobaciones, naranja para ajustes/observaciones, rojo suave para rechazos
o no continuidad, y verde intenso para fin exitoso.

El flujo debe ser de izquierda a derecha y contener:

Inicio
-> Registro de local comercial y propietario
-> Registro de captacion por el agente inmobiliario
-> Revision de captacion por el broker

Desde "Revision de captacion por el broker" salen tres caminos:
1. Aprueba -> Captacion activa
2. Solicita ajustes -> Correccion de captacion por el agente -> vuelve a Revision de captacion por el broker
3. Rechaza -> Fin del proceso

Desde "Captacion activa":
-> Registro de cliente interesado
-> Creacion de oportunidad comercial
-> Registro de interacciones comerciales
-> Programacion y ejecucion de visitas
-> Decision: "El cliente continua la operacion?"

Si la decision es No:
-> Registro de motivo de no continuidad
-> Cierre de oportunidad como no continua
-> Fin del proceso

Si la decision es Si:
-> Registro de solicitud de alquiler
-> Registro de documentacion asociada
-> Evaluacion de la solicitud por el broker

Desde "Evaluacion de la solicitud por el broker" salen tres caminos:
1. Aprueba -> Cierre exitoso de la oportunidad -> Fin del proceso
2. Rechaza -> Cierre no favorable de la oportunidad -> Fin del proceso
3. Observa / solicita ajustes -> Actualizacion de solicitud o documentos -> vuelve a Evaluacion de la solicitud por el broker

Agregar una nota visual pequena debajo de "Creacion de oportunidad comercial":
"La oportunidad conserva trazabilidad incluso si no se genera solicitud formal".

Formato: diagrama horizontal panoramico, legible en una sola imagen, con flechas claras
y etiquetas de decision "Aprueba", "Solicita ajustes", "Rechaza", "Si", "No",
"Observa / solicita ajustes".
```
