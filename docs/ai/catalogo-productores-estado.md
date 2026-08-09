# Catálogo de productores de estado

**Estado: vigente desde 2026-08-08.** Cierra el Bloque 7.3.3. Este documento
responde una sola pregunta por cada estado persistible del sistema: **¿quién lo
produce?** Si nadie lo produce, dice por qué se conserva o por qué se retira.

La fuente ejecutable es `CatalogoProductoresTest` (capa 1) y
`VocabularioPersistidoIntegrationTest` (capa 2). **El build falla si aparece un
código nuevo sin clasificar**, así que este archivo no puede quedarse atrás.

## Por qué existe

Durante el Bloque 7 encontramos ocho códigos que la base de datos admitía, el
dominio declaraba y la UI a veces ofrecía, pero que **ninguna operación podía
producir**. No eran un problema teórico: `comision_movimiento.A` se aceptaba,
se persistía como evidencia económica y no movía ningún saldo — un 200 que no
cambiaba nada. Un estado sin productor es una promesa que el sistema no cumple.

La auditoría manual que los encontró no es repetible. Este catálogo sí.

## Las cinco clasificaciones

| Clase | Significado | Qué hacer con ella en el corte del legado |
|---|---|---|
| `PRODUCIDO` | Una operación real lo escribe. | Nada. |
| `DERIVADO` | No se persiste: se calcula de otros hechos en lectura. | Nada. |
| `RESERVADO_COMPATIBILIDAD` | Sin productor. Existe **porque el cable congelado lo exige** y desaparece con él. | **Eliminar**: request, CHECK y catálogo. |
| `RESERVADO_FUTURO` | Sin productor. Representa capacidad **todavía no construida**, con una causa de negocio identificada. | **Conservar**: se implementa cuando llegue esa causa. |
| `DEPRECADO` | Tuvo o pudo tener valores; no se producen más. Solo lectura histórica. | **Eliminar** si no quedan filas. |

La diferencia entre las dos reservas es la que hace mecánica la limpieza: al
retirar el legado se borra la lista de `RESERVADO_COMPATIBILIDAD` sin volver a
decidir caso por caso, y `RESERVADO_FUTURO` permanece intacta.

## Capa 1 — catálogo de `EstadosDominio`

Los 83 códigos de los 22 enums. Cualquier código nuevo sin fila aquí rompe
`CatalogoProductoresTest`.

### Sin productor: las decisiones del Bloque 7.3.3

| Enum | Código | Clase | Justificación |
|---|---:|---|---|
| `EstadoProspeccion` | `E` | `RESERVADO_COMPATIBILIDAD` | `POST /prospecciones/{id}/propuesta` produce `S` (Seguimiento), no `E`. Legible para filas importadas; retirado de toda acción. Eliminable tras el corte si no hay filas. |
| `EstadoCaptacion` | `V` | `PRODUCIDO` | **Nuevo en 7.3.3.** Lo produce el reconciliador de vigencia: `A → V` cuando `fecha_fin_encargo < hoy`. Determinista y repetible. |
| `EstadoSolicitud` | `D` | `PRODUCIDO` | **Nuevo en 7.3.3.** `POST /solicitudes/{id}/desistir`, agente en su alcance, motivo obligatorio. Grafo `G/E/O → D`; **no** existe `A → D`. |
| `EstadoOportunidad` | `X` | `PRODUCIDO` | **Nuevo en 7.3.3.** Consecuencia, no botón: solicitud `R` o `D` arrastra `S → X` en la misma transacción. No hay endpoint propio. |
| `EstadoTarea` | `E` | `RESERVADO_FUTURO` | Falta una acción funcional «iniciar tarea». No se inventa un endpoint para llenar el enum. |
| `EstadoTarea` | `V` | `DERIVADO` | El vencimiento **ya se conoce por fecha** en lectura. Persistirlo exigiría un reloj para mantener una verdad duplicada. |
| `EstadoAlerta` | `D` | `RESERVADO_FUTURO` | No existe definición de en qué se diferencia «descartar» de «atender». Mismo criterio que el ajuste de comisión: sin semántica inequívoca, no se inventa comportamiento. |
| `DisponibilidadComercial` | `R` | `RESERVADO_FUTURO` | 7.3.2 fijó que un contrato `P` **no** significa reserva y que la recuperación solo termina en `D`/`T`. Hasta que exista una operación real de reserva, no se produce. No se reutiliza un borrador contractual para producirlo. |

### Barrido en curso: hallazgos ya verificados

El inventario autoritativo no es el enum sino **PostgreSQL**: 33 `CHECK` sobre
columnas de estado/resultado/tipo, ~122 pares `(tabla.columna, código)` — más
que los 83 del catálogo Java, justo por los vocabularios que viven fuera de
`EstadosDominio`.

BROX produce estado por **tres vías**, y buscar solo una subestima el resultado:

1. transición formal (`Transiciones` → `MaquinasEstado`);
2. mutación directa (`setEstado(...)`, asignación en la entidad);
3. constantes de entidad con **nombre distinto** al del enum
   (`Publicacion.ESTADO_CERRADO`, `Propiedad.ESTADO_NO_DISPONIBLE`).

#### Dos huérfanos NUEVOS, fuera de la lista de nueve

| Columna | Código | Evidencia |
|---|---:|---|
| `concesion_recuperacion.estado` | `A` Agotada | `RecuperacionEmergenciaServiceImpl` escribía `P`, `V`, `C` y `D`; `A` solo aparecía en `EstadosDominio`. Al gastar la última acción la concesión seguía VIGENTE aunque `consumirCapacidad` ya no fuera a dejar pasar ni una más: el estado decía una cosa y la capacidad otra. **Resuelto en 7.3.3: `PRODUCIDO`** por `marcarAgotadaSiConsumioSuUltimaAccion`. |

#### Un falso positivo del barrido, y la cuarta vía que lo explica

`token_acceso.estado = 'R'` pareció huérfano porque `MfaServiceImpl:465` solo
lo **lee**, y ni `setEstado` ni ninguna asignación de entidad lo escriben. Es
**falso**: lo produce `TokenAccesoRepository.invalidarVivosDe`, un
`@Modifying` masivo que fija `invalidado_en` y `estado = REVOCADO` a la vez,
con tres llamadores reales (`ContrasenaServiceImpl:315`,
`MfaServiceImpl:313` y `:376`). Clasificación correcta: **`PRODUCIDO`**.

Esto añade una **cuarta vía de producción** que hay que barrer siempre, y que
las tres primeras no cubren:

4. **`@Modifying` JPQL en repositorios** — el estado se escribe dentro de una
   cadena de consulta, así que no aparece buscando `setEstado` ni asignaciones
   en la entidad.

#### Producidos, pero por un setter genérico

`publicacion.estado` (`B/P/S/C`) y `requerimiento_cliente.estado` (`A/P/C`) se
escriben desde endpoints de «cambiar estado» que aceptan **cualquier código del
vocabulario** (`PublicacionServiceImpl:146`, `RequerimientoServiceImpl:98`).
Son alcanzables, así que son `PRODUCIDO` — pero es la misma forma de puerta que
tenía `marcarCierre`: el estado no lo decide una operación de negocio con
nombre, lo elige el cliente. No es un huérfano y no bloquea este tramo; queda
anotado como deuda a revisar cuando se toque cada vertical.

#### Columnas ya barridas

| Columna | Códigos | Resultado |
|---|---|---|
| `token_acceso.estado` | `V C R A` | Todos `PRODUCIDO`. `V` por defecto, `C` en `MfaServiceImpl`, `R` por `invalidarVivosDe`, `A` por la entidad y por `sumarIntentoFallido`. |
| `factor_autenticacion.estado` | `P A R` | Todos `PRODUCIDO` (`MfaServiceImpl:141,165,532`). |
| `concesion_recuperacion.estado` | `P V C D A` | Todos `PRODUCIDO`. `A` **desde 7.3.3**. |
| `tarea.estado` | `P C A` prod · `E V` sin productor | Confirma la decisión: `E` `RESERVADO_FUTURO`, `V` `DERIVADO`. |
| `alerta.estado` | `A T` prod · `D` sin productor | Confirma `D` `RESERVADO_FUTURO`. |
| `prospeccion.resultado_propuesta` | `P A R` prod · `S` | `S` `DEPRECADO`, puerta cerrada. |
| `publicacion.estado` | `B P S C` | `PRODUCIDO` por setter genérico (deuda anotada). |
| `requerimiento_cliente.estado` | `A P C` | `PRODUCIDO` por setter genérico (deuda anotada). |

#### Segundo lote (barrido completado)

| Columna | Códigos | Resultado |
|---|---|---|
| `visita.estado` | `P G C N R` | Todos `PRODUCIDO` (programar, reprogramar, cancelar, no realizada, realizar). |
| `solicitud_alquiler.estado` | `G E O A R C` prod · `D` | `G` alta, `E` reenviar, `C` cierre del contrato; `O/A/R` los escribe `EvaluacionServiceImpl` vía `DESTINO_SOLICITUD`. `D` **sin productor** → se implementa en este tramo. |
| `contrato_alquiler.estado_contrato` | `P D V R F S A` | Todos `PRODUCIDO` (verificado en 7.3.1 y 7.3.2). |
| `comision_liquidacion.estado` | `P R C A` | Todos `PRODUCIDO` (verificado en 7.2: `P` al cerrar, `R/C` derivados del saldo, `A` por anulación expresa o cascada del contrato). |
| `oportunidad_comercial.estado` | `A S N F` prod · `X` | `X` **sin productor** → se implementa en este tramo. |
| `documento_solicitud.estado` | `R O V` | Todos `PRODUCIDO`. |
| `documento_solicitud.resultado_revision` | `P C O` | Todos `PRODUCIDO` (`P` por defecto de la entidad, `C/O` en la revisión del broker). |
| `evaluacion_solicitud.resultado` | `A R O` | Todos `PRODUCIDO`. |
| `evaluacion_solicitud.tipo_evaluacion` | `O F` prod · `P` | `O/F` derivados del resultado. `P` `RESERVADO_COMPATIBILIDAD`, confirmado. |
| `propiedad.disponibilidad_comercial` | `D A T` prod · `R` | `A` al cerrar el alquiler, `D`/`T` por la revisión de 7.3.2. `R` `RESERVADO_FUTURO`. |
| `propiedad.estado_registro` | `A I` | Ambos `PRODUCIDO`. **Ojo**: conviven con `Propiedad.ESTADO_DISPONIBLE/NO_DISPONIBLE`, que son de OTRA columna. Alias históricos, fácil de confundir. |
| `detalle_agente.estado_operativo` | `D L N` | Todos `PRODUCIDO` desde el alta/edición de agente. La validación es un `Set.of("D","L","N")` **a mano** en `UsuariosInternos:153`, no el enum: vocabulario duplicado, deuda menor. |
| `revision_disponibilidad.disponibilidad_nueva` | `D T` | Ambos `PRODUCIDO` (7.3.2). |
| `persona.estado`, `organizacion.estado`, `usuario_organizacion.estado`, `credencial_usuario.estado_administrativo`, `finalidad_tratamiento.estado` | `A I` | Todos `PRODUCIDO`. |

#### Tercer hallazgo: una tabla que la aplicación no toca

`regularizacion_dato_economico` (`P R D`) **no tiene entidad JPA, ni repositorio,
ni servicio**. La única referencia Java es un test que contrasta su `CHECK`
contra el enum. La escribió V16 (backfill) y V17 la usó como cola de calidad
—se negaba a aplicar las restricciones finales mientras quedara una fila `P`—.
Ese episodio terminó: las tres migraciones están aplicadas y **la tabla está
vacía**.

De los tres códigos, solo `D` llegó a insertarse alguna vez, y por una
migración. Aplicando la regla acordada —una migración histórica no convierte un
código en `PRODUCIDO` funcional— los tres son **`DEPRECADO`**: artefacto de
migración sin caso de uso actual.

**Decidido: se retira en el Bloque 8**, con el corte del legado. No ahora. La
tabla está vacía y no la usa nadie, así que borrarla hoy sería un `DROP TABLE`
gratuito en mitad de un tramo de reglas de negocio; hacerlo con el corte la
agrupa con el resto de la limpieza y deja V15–V17 legibles hasta entonces
—siguen explicando por qué el modelo económico quedó como quedó—.

Se retiran juntos: la tabla, su `CHECK` y el enum
`EstadoRegularizacionEconomica`, que hoy ocupa sitio en el catálogo canónico
para algo que la aplicación nunca lee.

### Resto del catálogo: PENDIENTE DEL BARRIDO

Los ocho casos de arriba se verificaron **uno a uno**. La clasificación del
resto permanece **pendiente del barrido exhaustivo de productores**, incluidos
setters directos, SQL, aliases de constante y funciones de base de datos.

No se afirma aquí que tengan productor, porque todavía no está demostrado.
Mientras esta sección diga esto, el gate no debe construirse: un gate sobre una
clasificación no demostrada sería una falsa fuente de verdad, y peor que no
tenerlo, porque las decisiones siguientes partirían de datos incorrectos.

## Capa 2 — vocabularios fuera de `EstadosDominio`

Aquí estaban los dos casos que más costaron de ver, precisamente porque un gate
que solo recorra `EstadosDominio` **no los mira**.
`VocabularioPersistidoIntegrationTest` los contrasta contra el `CHECK` real de
PostgreSQL: añadir una letra al constraint sin clasificarla rompe el build.

| Columna | Código | Clase | Justificación |
|---|---:|---|---|
| `prospeccion.resultado_propuesta` | `P` | `PRODUCIDO` | `marcarPropuesta()`. |
| `prospeccion.resultado_propuesta` | `A` | `PRODUCIDO` | `marcarAceptada()`. |
| `prospeccion.resultado_propuesta` | `R` | `PRODUCIDO` | `marcarRechazoDelPropietario()`. |
| `prospeccion.resultado_propuesta` | `S` | `DEPRECADO` | Nunca tuvo productor —ni siquiera constante Java—. La continuidad comercial ya la cubre `EstadoProspeccion.SEGUIMIENTO`; una segunda máquina para lo mismo solo añade ambigüedad. **No confundir con `RECONTACTAR` de `interaccion_comercial`**: otro vocabulario, vivo, del que se derivan tareas. |
| `evaluacion_solicitud.tipo_evaluacion` | `P` | `RESERVADO_COMPATIBILIDAD` | El request **debe traerlo y ser válido**, pero el service deriva el tipo del resultado y lo pisa (`EvaluacionServiceImpl`: «se valida un campo que luego se pisa»). Además `ck_evaluacion_tipo_derivado` hace imposible persistirlo. Angular no lo ofrece. |
| `evaluacion_solicitud.tipo_evaluacion` | `O`, `F` | `PRODUCIDO` | Derivados del resultado: observada ⇒ `O`, aprobada/rechazada ⇒ `F`. |
| `evaluacion_solicitud.resultado` | `A`, `R`, `O` | `PRODUCIDO` | Los tres desenlaces del broker. |
| `documento_solicitud.resultado_revision` | `P`, `C`, `O` | `PRODUCIDO` | Revisión del broker por documento. |
| `comision_movimiento.tipo` | `C`, `P`, `R` | `PRODUCIDO` | Cobro, pago al agente, reversión. |
| `comision_movimiento.tipo` | `A` | `DEPRECADO` | Ajuste retirado en 7.2: no existía regla que dijera qué saldo modifica, con qué signo ni contra qué tope. El CHECK lo conserva solo por si hubiera filas históricas; hoy hay 0. |

## La puerta que se cerró con `S`

Deprecar el valor no bastaba. `Prospeccion.marcarCierre(motivo, resultado)`
recibía el código como `String` libre: aunque sus dos llamadores pasaran `R` y
`null`, la firma dejaba entrar mañana cualquier letra. El `CHECK` lo habría
detenido — convirtiendo **un error de dominio en un fallo tardío de
persistencia**.

Se sustituyó por dos métodos con un solo desenlace cada uno:

```
marcarRechazoDelPropietario(motivo)  -> resultado_propuesta = R
marcarDescartePorAgente(motivo)      -> no toca resultado_propuesta
```

Mejor que un enum: el método ya dice cuál es su desenlace, así que no hay
parámetro que validar. `ProspeccionResultadoPropuestaTest` comprueba por
reflexión que no reaparezca ningún método que acepte el resultado desde fuera.
