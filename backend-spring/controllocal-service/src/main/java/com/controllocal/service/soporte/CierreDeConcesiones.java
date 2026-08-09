package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.ConcesionRecuperacion;
import com.controllocal.persistence.repositorio.ConcesionRecuperacionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Cierra una concesion <b>en su propia transaccion</b>, y esto no es un
 * detalle de implementacion.
 *
 * <h2>El fallo que obliga a que esto exista</h2>
 * Cuando llega una accion y la organizacion <b>ya tiene gobierno</b>, hay que
 * hacer dos cosas: cerrar la concesion y rechazar la accion. Si las dos van en
 * la misma transaccion, la excepcion del rechazo <b>arrastra consigo el
 * cierre</b>: la accion se rechaza —bien— pero la concesion sigue figurando
 * {@code VIGENTE} en la base. Lo detecto el simulacro, no el compilador.
 *
 * <p>Es exactamente el mismo patron que ya obligo a sacar de su transaccion los
 * contadores de intentos y la auditoria: <b>lo que registra un hecho no puede
 * viajar en la transaccion que falla</b>.
 *
 * <p><b>Solo para el cierre PREVIO a actuar.</b> El cierre posterior —cuando la
 * accion salio bien y el gobierno volvio con ella— se queda en la transaccion
 * principal a proposito: alli la fila ya esta modificada por el consumo de
 * capacidad, y abrir una transaccion nueva sobre la misma fila seria esperar a
 * un bloqueo que solo suelta la que espera.
 */
@Component
public class CierreDeConcesiones {

    public static final String GOBIERNO_RESTABLECIDO = "GOBIERNO_RESTABLECIDO";

    private final ConcesionRecuperacionRepository concesiones;

    public CierreDeConcesiones(ConcesionRecuperacionRepository concesiones) {
        this.concesiones = concesiones;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cerrarPorGobierno(long idConcesion, OffsetDateTime ahora) {
        return concesiones.findById(idConcesion).map(concesion -> {
            concesion.setEstado(ConcesionRecuperacion.CERRADA);
            concesion.setCerradaEn(ahora);
            concesion.setCierreMotivo(GOBIERNO_RESTABLECIDO);
            concesiones.save(concesion);
            return true;
        }).orElse(false);
    }
}
