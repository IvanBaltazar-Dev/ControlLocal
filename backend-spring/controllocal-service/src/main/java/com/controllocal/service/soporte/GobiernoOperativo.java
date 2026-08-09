package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

/**
 * Guarda del invariante «una organizacion nunca se queda sin administrador
 * OPERATIVO» (D-S0-37).
 *
 * <p><b>Por que no basta contar membresias</b> (que es lo que hacia V34): una
 * cuenta suspendida, sin segundo factor o con un cambio obligatorio pendiente
 * aparece en el recuento y no puede gobernar. Un tenant con "un administrador"
 * que en realidad no puede entrar es un tenant sin gobierno, y peor: uno que
 * cree tenerlo.
 *
 * <p><b>Esta guarda no sustituye al trigger de V37, lo acompana.</b> La guarda
 * da el mensaje que el usuario entiende y corta antes de escribir; el trigger
 * es la garantia de que la regla se cumple aunque alguien escriba por SQL o
 * aparezca un camino de codigo que nadie reviso. Es el mismo reparto que en
 * D-S0-9.
 *
 * <p><b>Y por que existe el nivel 3 del diseno.</b> La consecuencia directa de
 * esto es que, en un tenant con un solo administrador, <b>nadie</b> —ni el
 * mismo— puede revocarle el factor por la via ordinaria. Eso no es un efecto
 * colateral: es lo que obliga a que la recuperacion de emergencia exista y sea
 * excepcional de verdad.
 */
@Component
public class GobiernoOperativo {

    /** Ninguna cuenta real tiene este id; sirve para "no excluyas a nadie". */
    private static final long SIN_EXCLUIR = -1L;

    private final UsuarioOrganizacionRepository membresias;

    public GobiernoOperativo(UsuarioOrganizacionRepository membresias) {
        this.membresias = membresias;
    }

    /**
     * ¿Quedaria la organizacion con gobierno si esta cuenta dejara de ser
     * operativa?
     */
    public boolean quedaOtroOperativo(long idOrganizacion, long idCredencialAfectada) {
        return membresias.contarAdministradoresOperativos(idOrganizacion, idCredencialAfectada) > 0;
    }

    public boolean hayAlgunoOperativo(long idOrganizacion) {
        return membresias.contarAdministradoresOperativos(idOrganizacion, SIN_EXCLUIR) > 0;
    }

    /**
     * Corta antes de degradar a la ultima cuenta capaz de gobernar.
     *
     * <p>Se aplica a las cinco operaciones que pueden dejar sin gobierno:
     * suspender una cuenta, dar de baja una membresia, <b>revocar un factor
     * MFA</b>, cambiar el rol de una membresia y aplicar una recuperacion.
     *
     * <p><b>Solo actua si la organizacion ya tiene gobierno operativo.</b> Si
     * no lo tiene —por ejemplo entre el despliegue y el primer enrolamiento—,
     * bloquear cualquier cambio dejaria el tenant tapiado justo cuando hay que
     * arreglarlo. Es la misma cautela que hace segura la bandera
     * {@code mfa_gobierno_exigido} del trigger.
     */
    public void exigirQueQuedeGobierno(long idOrganizacion, long idCredencialAfectada,
                                       String queSeIntenta) {
        if (!hayAlgunoOperativo(idOrganizacion)) {
            return;
        }
        if (!quedaOtroOperativo(idOrganizacion, idCredencialAfectada)) {
            throw new ReglaNegocioException(
                    "No se puede " + queSeIntenta + ": la organizacion se quedaria sin "
                            + "ningun administrador operativo. Designe otro administrador "
                            + "con su segundo factor activo antes de continuar.");
        }
    }
}
