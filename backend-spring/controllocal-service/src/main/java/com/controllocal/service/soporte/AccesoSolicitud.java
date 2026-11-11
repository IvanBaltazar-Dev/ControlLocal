package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * Carga de la solicitud CON el alcance del actor ya aplicado (F4 §7): las
 * solicitudes alcanzan <b>por agente</b> —el BROKER ve las de su equipo—, a
 * diferencia de contratos y oportunidades, que alcanzan por captacion. No
 * unificar las dos reglas.
 *
 * <p>Vive aqui, y no dentro de un service, porque son TRES los casos de uso
 * que entran por la solicitud: la solicitud misma, sus documentos y el
 * historial de evaluaciones. Una sola definicion evita que las tres copias se
 * separen con el tiempo.
 */
@Component
public class AccesoSolicitud {

    private final SolicitudAlquilerRepository solicitudes;
    private final Alcances alcances;

    public AccesoSolicitud(SolicitudAlquilerRepository solicitudes, Alcances alcances) {
        this.solicitudes = solicitudes;
        this.alcances = alcances;
    }

    /** Ficha completa de la solicitud; 404 si no existe en el tenant, 403 si es ajena. */
    public SolicitudAlquiler conAcceso(long id, Actor actor) {
        SolicitudAlquiler solicitud = solicitudes.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Solicitud"));
        exigir(solicitud, actor);
        return solicitud;
    }

    /** Igual que {@link #conAcceso}, por el codigo del cable (SOL-yyMMddHHmmss). */
    public SolicitudAlquiler porCodigo(String codigo, Actor actor) {
        if (codigo == null || codigo.isBlank()) {
            throw new NoEncontradoException("Solicitud");
        }
        SolicitudAlquiler solicitud = solicitudes
                .buscarFichaPorCodigo(actor.idOrganizacion(), codigo.trim())
                .orElseThrow(() -> new NoEncontradoException("Solicitud"));
        exigir(solicitud, actor);
        return solicitud;
    }

    public void exigir(SolicitudAlquiler solicitud, Actor actor) {
        DetalleAgente agente = solicitud.getAgente();
        exigirRolAgente(agente == null ? null : agente.getId(), actor);
    }

    /** Consulta de autoridad actual para entregar bytes después de esperar al proveedor. */
    @Transactional(readOnly = true)
    public void exigirConsultaActual(long idSolicitud, Actor actor) {
        var actual = solicitudes.accesoActual(actor.idOrganizacion(), idSolicitud)
                .orElseThrow(() -> new NoEncontradoException("Solicitud"));
        exigirRolAgente(actual.getIdRolAgente(), actor);
    }

    private void exigirRolAgente(Long idRolAgente, Actor actor) {
        if (!permiteResponsableActual(idRolAgente, actor)) {
            throw new AccesoNoAutorizadoException();
        }
    }

    /** Sólo metadata actual obtenida por Core; misma política para ficha y selección por lote. */
    public boolean permiteResponsableActual(Long idRolAgente, Actor actor) {
        return permite(idRolAgente,alcances.de(actor).rolesAgente());
    }
    public Set<Long> permitidasActuales(Map<Long,Long> responsables,Actor actor){
        var roles=Set.copyOf(alcances.de(actor).rolesAgente());var permitidas=new HashSet<Long>();
        responsables.forEach((id,rol)->{if(permite(rol,roles))permitidas.add(id);});return Set.copyOf(permitidas);
    }
    private static boolean permite(Long responsable,Collection<Long> roles){return responsable!=null&&roles.contains(responsable);}
}
