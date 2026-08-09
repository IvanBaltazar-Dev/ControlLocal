package com.controllocal.web.dto;

import com.controllocal.service.ContratoService.FichaRevisionDisponibilidad;

import java.time.LocalDate;

/** Espejo de la revision ejecutada. */
public record RevisionDisponibilidadResponse(Long id, Long idContrato, Long idPropiedad,
                                             String disponibilidadAnterior,
                                             String disponibilidadNueva, String resultado,
                                             String motivo, LocalDate fechaRevision,
                                             boolean repetida) {

    public static RevisionDisponibilidadResponse desde(FichaRevisionDisponibilidad f) {
        return new RevisionDisponibilidadResponse(f.id(), f.idContrato(), f.idPropiedad(),
                f.disponibilidadAnterior(), f.disponibilidadNueva(), f.resultado(), f.motivo(),
                f.fechaRevision(), f.repetida());
    }
}
