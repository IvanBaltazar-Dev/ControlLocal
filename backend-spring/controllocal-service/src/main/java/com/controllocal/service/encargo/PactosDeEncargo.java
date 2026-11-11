package com.controllocal.service.encargo;

import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.service.Actor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** CAT-E14/15: lectura unica del instrumento aprobado exacto, independiente del caso de escritura. */
public interface PactosDeEncargo {
    record ReferenciaPacto(long encargo,long revisionAprobatoria) { }
    record AcreditacionPacto(long documento,long peticion,long revisionPresentacion,
                            String huellaMaterial,String huellaCobertura,String huellaManifestacion) { }
    record CondicionDevengo(TipoHitoContractual hito) { }
    record CondicionExigibilidad(TipoHitoContractual hito,LocalDate fechaExpresa,
                                Integer cantidad,String unidadExpresa,String computoExpreso) { }
    record EjecucionPacto(CondicionDevengo devengo,CondicionExigibilidad exigibilidad) { }
    record ReglaComision(String tipoComision,String baseCalculo,BigDecimal valorComision,
                         String monedaComision,String tratamientoIgv,String motivoSinComision,
                         String hitoDevengo,String parteObligada,String beneficiario,
                         String exigibilidad,String procedencia,List<EncargoService.Tramo> tramos,
                         EjecucionPacto ejecucion) {
        public ReglaComision { tramos=List.copyOf(tramos); }
    }
    record PactoFirmado(ReferenciaPacto referencia,long versionContenido,
                        AcreditacionPacto acreditacion,ReglaComision regla) { }

    PactoFirmado consultarPactoFirmado(ReferenciaPacto referencia,Actor actor);
}
