# Diccionario del modelo económico y contractual v2

**Vigente desde 2026-08-01.** Todas las columnas privadas llevan
`organizacion_id`. Los importes usan `NUMERIC`, nunca punto flotante. Las
monedas admitidas son `PEN` y `USD`.

## Propiedad

| Campo | Tipo | Regla |
|---|---|---|
| `estado_registro` | `VARCHAR(1)` | `A/I`; existencia administrativa. |
| `disponibilidad_comercial` | `VARCHAR(1)` | `D/R/A/T`; independiente del registro. |
| `moneda_referencial` | `VARCHAR(3)` | Obligatoria si existe el importe referencial. |
| `interior_unidad`, `piso`, `referencia_interna`, `nombre_edificio_galeria` | texto nullable | Identificación técnica; no se inventan datos desconocidos. |

El adaptador REST legado devuelve `I` para registro inactivo, `D` para activo y
disponible, y `N` para las demás disponibilidades. Un contrato vigente marca
`A`; finalizar o rescindir crea una tarea de revisión y no reactiva el local.

## Captación y condición económica

| Campo | Tipo | Regla |
|---|---|---|
| `fecha_inicio_encargo` | `DATE` | Obligatoria al activar. |
| `fecha_fin_encargo` | `DATE` | Obligatoria al activar y posterior al inicio. |
| `fecha_cierre` | `DATE` | Obligatoria si estado `C`. |
| `motivo_cierre` | `VARCHAR(1)` | `A/P/M/V/O`; solo con estado `C`. |
| `id_condicion_economica` | FK 1:1 | Obligatoria al activar. |

La tabla `condicion_economica_captacion` está descrita en
`decision-modelos-de-comision.md`. La moneda de una comisión derivada coincide
con la de la base; una comisión fija declara la suya.

## Contrato

| Campo | Tipo | Regla |
|---|---|---|
| `fecha_inicio_contrato`, `fecha_fin_contrato` | `DATE` | Obligatorias en firmado/vigente; fin posterior al inicio. |
| `renta_contractual` | `NUMERIC(14,2)` | Snapshot positivo. |
| `moneda` | `VARCHAR(3)` | Heredada de la solicitud aprobada. |
| `id_contrato_anterior` | FK nullable | Renovación como sucesor; unicidad evita dos sucesores. |
| `fecha_efectiva_estado` | `DATE` | Fecha de negocio de la última transición. |

El grafo es `P→D→V→F`, con `V→S`, `P/D→A` y `V→R`. Renovar crea otro
contrato y conserva el anterior.

## Liquidación y movimientos

| Tabla/campo | Tipo | Regla |
|---|---|---|
| `comision_liquidacion.monto_bruto` | `NUMERIC(14,2)` | Snapshot obligatorio. |
| `parte_empresa`, `parte_agente` | `NUMERIC(14,2)` | No negativas; si ambas existen suman el bruto. |
| `moneda` | `VARCHAR(3)` | Obligatoria. |
| `estado` | `VARCHAR(1)` | `P/R/C/A`. |
| `comision_movimiento.tipo` | `VARCHAR(1)` | `C/P/A/R`. |
| `monto`, `moneda`, `fecha` | importe/ISO/fecha | Evidencia obligatoria, monto positivo. |
| `forma_pago`, `id_usuario`, `rol_usuario`, `observacion` | metadatos | Trazabilidad del movimiento. |

## Regularización

`regularizacion_dato_economico` registra entidad, campo, valor de origen,
motivo y estado `P/R/D`. No es un fallback: una fila `P` bloquea la migración
final. Las correcciones requieren evidencia o la eliminación identificada de
contaminación E2E después de un respaldo.

La matriz completa de estados y sus restricciones está en
`matriz-codigos-estado.md`.
