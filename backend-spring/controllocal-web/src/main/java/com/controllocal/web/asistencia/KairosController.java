package com.controllocal.web.asistencia;

import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.conversacion.Kairos;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entrada de KAIROS Mobile en el único Core (P-07). No acepta autoridad en el cuerpo. */
@RestController
@RequestMapping("kairos")
public class KairosController {
    private final Kairos kairos;

    public KairosController(Kairos kairos) {
        this.kairos = kairos;
    }

    public record TurnoRequest(String canal, String conversacionId, String turnoId,
                               String mensajeId, String texto, Long idBorrador,
                               String referenciaActuacion, Integer versionBorrador) { }

    @PostMapping("turnos")
    @CapacidadReconocida("atender_turno_kairos")
    public Kairos.Respuesta turno(@RequestBody TurnoRequest dto) {
        if (dto == null) throw new IllegalArgumentException("Se requiere un turno.");
        var actor = SesionActual.actor();
        var contexto = ContextoDeActuacion.de(SesionActual.sesion());
        var sesion = new SesionBrox(actor.idOrganizacion(), actor.idPersona(), actor.idRolOperativo(),
                actor.rolEfectivo(), contexto.identidad(), contexto.expiraEnEpochSeconds());
        return kairos.turno(new Kairos.Turno(dto.canal(), dto.conversacionId(), dto.turnoId(),
                dto.mensajeId(), dto.texto(), dto.idBorrador(), dto.referenciaActuacion(), dto.versionBorrador()), sesion);
    }
}
