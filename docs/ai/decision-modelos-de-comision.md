# Modelo económico y de comisión

**Estado: implementado y vigente desde 2026-08-01.** Sustituye la convención
histórica `comisionPactada` sin unidad por una condición económica explícita.
El campo legado solo existe como adaptación del contrato REST; no es una
segunda regla de dominio ni una columna persistida.

## Condición económica de captación

Cada captación tiene una relación 1:1 con
`condicion_economica_captacion`:

| Campo | Valores | Regla |
|---|---|---|
| `tipo_operacion` | `A` arrendamiento, `V` venta | La UI actual solo ofrece alquiler; el esquema admite venta futura. |
| `importe_referencia` | `NUMERIC(14,2)` | Renta mensual o precio de venta declarado. |
| `moneda_referencia` | `PEN`, `USD` | Obligatoria, nunca inferida por magnitud. |
| `tipo_comision` | `E` mensualidades, `P` porcentaje, `F` monto fijo | Da unidad al número. |
| `base_calculo` | `R` renta, `V` venta, `N` no aplica | `E/R`, `P/R|V`, `F/N`. |
| `valor_comision` | `NUMERIC(12,4)` | `1.00` significa una mensualidad; `25.00` significa 25 %. |
| `moneda_comision` | `PEN`, `USD` | Hereda la base en `E/P`; es obligatoria y propia en `F`. |
| `tratamiento_igv` | `I` incluido, `A` adicional, `N` no aplica | No se deduce silenciosamente. |

No hay heurísticas. `3600` no se transforma en `36`, `100`, `1` ni en un
importe. Un origen ambiguo entra en `regularizacion_dato_economico`; V17 se
niega a aplicar restricciones finales mientras exista una fila pendiente.

La semántica histórica documentada sí constituía evidencia: en la columna
antigua `50/100/150/200` eran porcentajes de una renta. V16 los convierte de
forma determinista a `E/R/0.50`, `E/R/1.00`, `E/R/1.50` y `E/R/2.00`. V17
elimina `captacion.comision_pactada`.

## Cálculo y presentación

`CalculadoraComision` valida primero la combinación tipo/base y calcula:

- `E/R`: renta × mensualidades.
- `P/R`: renta × porcentaje / 100.
- `P/V`: precio de venta × porcentaje / 100.
- `F/N`: el valor fijo, con su moneda explícita.

La liquidación conserva `monto_bruto` como snapshot. Una comisión ya generada
no cambia si después se edita el importe de referencia.

Angular pregunta «¿A cuánto equivale la comisión?» y ofrece medio, uno, uno y
medio o dos meses de alquiler, porcentaje personalizado, monto fijo autorizado
y sin comisión con motivo obligatorio. La ficha muestra, por ejemplo,
«Un mes de alquiler — USD 7,200»; la fórmula queda como dato secundario.

## Cobro, reparto y pago al agente

`comision_liquidacion` guarda bruto, moneda, parte de empresa y parte de agente.
`comision_movimiento` es la evidencia económica:

- `C`: cobro recibido por la inmobiliaria.
- `P`: pago al agente.
- `A`: ajuste.
- `R`: reversión.

El estado de liquidación usa `P/R/C/A` (pendiente, parcial, cobrada, anulada) y
se deriva del saldo real de movimientos. Los KPI separan, siempre por moneda,
bruto generado, cobrado, pendiente de cobro, pagado al agente y pendiente de
pago al agente. Las anuladas se excluyen y los contratos sin liquidación se
cuentan de forma explícita. PEN y USD nunca se suman.

## Compatibilidad REST

Las respuestas mantienen los códigos y campos legados de forma aditiva.
`comisionPactada` se deriva únicamente cuando la condición puede representarse
sin ambigüedad; los campos normalizados son la fuente de verdad. Los
repositorios consultan el atributo `String` persistido y las reglas nuevas usan
enums o tipos de dominio, no letras dispersas.

## Verificación

- Migraciones consecutivas V15–V17: expansión, backfill con evidencia y
  restricciones/eliminación de columnas sustituidas.
- Pruebas de cálculo para mensualidades, porcentaje sobre renta/venta y fijo.
- Pruebas de rechazo de combinaciones inválidas y moneda ausente.
- Pruebas de cobro/pago parcial, anulaciones y KPI agrupados por moneda.
- `RepositorioEstadosIntegrationTest`: contexto Spring completo, PostgreSQL
  real, Flyway, JPQL, métodos derivados y proyecciones.
- `e2e-estabilizacion-alquiler.ps1`: agregado completo sobre base efímera.
