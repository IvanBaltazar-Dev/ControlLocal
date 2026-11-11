package com.controllocal.web.dto;

import com.controllocal.service.InteraccionService;
import com.controllocal.service.soporte.Procedencia;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Proyección ya autorizada por Core, con diccionario e historia diferenciados. */
public record InteraccionResponse(long id,long version,long idAgente,LocalDate fechaHecho,Instant registradoEn,
        InteraccionService.DatosInteraccion datos,List<InteraccionService.VinculoFicha> vinculos,String versionDiccionario,
        Procedencia procedencia,String resultadoHistorico,String canalHistorico,String agenteNombre,
        List<InteraccionService.HechoPosterior> hechosPosteriores,String rotuloResultado,InteraccionService.DatosLegados datosLegados,
        InteraccionService.DatoContactoProspeccion contactoProspeccion) {
    public static InteraccionResponse desde(InteraccionService.FichaInteraccion f) {
        return new InteraccionResponse(f.id(),f.version(),f.idAgente(),f.fechaHecho(),f.registradoEn(),f.datos(),
                f.vinculos(),f.versionDiccionario(),f.procedencia(),f.resultadoHistorico(),f.canalHistorico(),f.agenteNombre(),f.hechosPosteriores(),f.rotuloResultado(),f.datosLegados(),f.contactoProspeccion());
    }
}
