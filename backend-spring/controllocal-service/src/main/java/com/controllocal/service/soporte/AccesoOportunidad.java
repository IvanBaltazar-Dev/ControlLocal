package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import org.springframework.stereotype.Component;

/**
 * Carga de la oportunidad CON el alcance del actor ya aplicado.
 *
 * <p>Es la gemela de {@link AccesoSolicitud} y existe por la misma razon: son
 * varios los casos de uso que entran por la oportunidad —la oportunidad misma,
 * su caida y, desde WP-A, su <b>mesa de negociacion</b>—, y una segunda copia
 * de este predicado se separaria de la primera. La regla no es la de las
 * solicitudes y no se unifican:
 *
 * <pre>
 *   solicitudes   el BROKER alcanza por AGENTE supervisado
 *   oportunidades el BROKER alcanza por CAPTACION supervisada
 * </pre>
 *
 * <p>El orden es el de siempre: <b>tenant primero</b> —una oportunidad de otra
 * corredora responde 404, no 403, porque un 403 confirmaria que existe— y
 * alcance despues.
 *
 * <p>Antes de WP-A la regla vivia dentro de {@code OportunidadServiceImpl}
 * como metodo privado. Sigue estando ahi el metodo que la matriz nombra
 * ({@code cargarConAcceso}); lo que ya no esta es la <b>definicion</b>, que se
 * escribia una vez y se habria escrito dos.
 */
@Component
public class AccesoOportunidad {

    private final OportunidadComercialRepository oportunidades;
    private final Alcances alcances;

    public AccesoOportunidad(OportunidadComercialRepository oportunidades, Alcances alcances) {
        this.oportunidades = oportunidades;
        this.alcances = alcances;
    }

    /** La oportunidad; 404 si no existe en el tenant, 403 si esta fuera del alcance. */
    public OportunidadComercial conAcceso(long id, Actor actor) {
        OportunidadComercial oportunidad = oportunidades.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Oportunidad"));
        if (!alcanza(oportunidad, actor)) {
            throw new AccesoNoAutorizadoException();
        }
        return oportunidad;
    }

    /**
     * ¿Alcanza este actor a esta oportunidad? Es la pregunta de LECTURA, y no
     * concede escritura: quien escribe en la mesa es el agente del ENCARGO
     * ({@code AutoridadDePropiedad.exigirEdicionDelEncargo}), que es otra
     * pregunta y tiene otra respuesta.
     */
    public boolean alcanza(OportunidadComercial oportunidad, Actor actor) {
        if (actor.esTenantAdmin()) {
            return true;
        }
        if (actor.esAgente()) {
            return oportunidad.getAgente() != null
                    && oportunidad.getAgente().getId() == actor.idRolOperativo();
        }
        Captacion captacion = oportunidad.getCaptacion();
        return captacion != null && captacion.getAgente() != null
                && alcances.supervisados(actor.idOrganizacion(), actor.idRolOperativo())
                        .contains(captacion.getAgente().getId());
    }
}
