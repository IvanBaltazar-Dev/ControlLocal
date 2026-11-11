package com.controllocal.service.formalizacion;

import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.soporte.Procedencia;
import java.util.List;

/** Formato de los snapshots propios de formalizacion, sin elegir fuentes actuales por antiguedad. */
final class ContenidoFormalizacion {
    private ContenidoFormalizacion() { }
    record Presentacion(DatosPresentacion datos,CoberturaRevision cobertura,
                        FuenteEconomicaFinal fuenteC4,Procedencia procedencia) { }
    record Revision(DatosRevision datos,CoberturaRevision cobertura,
                    FuenteEconomicaFinal fuenteC4,Procedencia procedencia) { }
    record Hecho(ReferenciaPacto pacto,ReferenciaFuenteEconomica fuente,
                 List<ReferenciaDocumento> evidencias,String descripcion,Procedencia procedencia) {
        Hecho { evidencias=List.copyOf(evidencias); }
    }
}
