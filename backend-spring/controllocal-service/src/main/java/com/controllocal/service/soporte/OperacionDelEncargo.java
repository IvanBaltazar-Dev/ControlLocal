package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * <b>De que operacion es un importe.</b> El sustituto del defecto silencioso.
 *
 * <h2>Que se quito y por que</h2>
 * Hasta esta tanda, un hito de precio sin operacion se guardaba como alquiler:
 * el valor lo ponia la entidad ({@code = OPERACION_ALQUILER}) y nadie se
 * enteraba. Mientras el sistema solo supo alquilar eso era invisible; con la
 * venta dentro, es un precio de venta archivado en la serie de alquiler — y no
 * hay CHECK que pueda detectarlo, porque 180 000 es un numero perfectamente
 * legal para una renta.
 *
 * <h2>La regla</h2>
 * <ol>
 *   <li>Si el productor <b>declara</b> la operacion, manda esa. Es la unica
 *       fuente que no puede equivocarse por omision.</li>
 *   <li>Si no la declara pero la propiedad tiene <b>un solo encargo vivo</b>,
 *       la operacion es la de ese encargo. No es una suposicion: el modelo dice
 *       que la operacion vive en el encargo (D-E4-1), asi que leerla de ahi es
 *       leer la fuente.</li>
 *   <li>Si no la declara y hay <b>dos</b> encargos vivos —venta y alquiler, el
 *       caso que el modelo universal existe para admitir— la pregunta sigue
 *       abierta y se <b>rechaza</b>.</li>
 *   <li>Si no la declara y no hay ningun encargo, tampoco se supone nada.</li>
 * </ol>
 *
 * <p>Los dos ultimos casos son los que antes se resolvian con "pues alquiler".
 * Ahora devuelven un mensaje que dice que falta y como darlo, que es lo que el
 * motor de captura necesita para poder <b>declararlo faltante</b> en vez de
 * fallar al guardar.
 *
 * <h2>Y una comprobacion mas</h2>
 * Declarar VENTA sobre una propiedad cuyo unico encargo vivo es de alquiler no
 * es un dato: es un error. {@link #resolver} lo corta aqui, antes de que el
 * trigger {@code tg_precio_operacion_encargo} (V49) lo corte en la base con un
 * mensaje de PostgreSQL.
 */
@Component
public class OperacionDelEncargo {

    private final CaptacionRepository captaciones;

    public OperacionDelEncargo(CaptacionRepository captaciones) {
        this.captaciones = captaciones;
    }

    /**
     * La operacion de esta escritura economica, o una excepcion que explica
     * que falta.
     *
     * @param declarada lo que dijo el productor: {@code "VENTA"},
     *                  {@code "ALQUILER"}, su codigo, o {@code null} si no dijo
     *                  nada
     */
    public OperacionInmobiliaria resolver(long idOrganizacion, long idPropiedad, String declarada) {
        List<Captacion> vivos = captaciones.encargosVivosDe(idOrganizacion, idPropiedad);

        if (declarada != null && !declarada.isBlank()) {
            OperacionInmobiliaria operacion = deTexto(declarada);
            exigirCoherenciaConLosEncargos(operacion, vivos);
            return operacion;
        }

        if (vivos.size() == 1) {
            return vivos.get(0).operacion();
        }
        if (vivos.size() > 1) {
            throw new ReglaNegocioException(
                    "Esta propiedad tiene encargo de venta y de alquiler a la vez: declara a cual "
                            + "pertenece el importe (VENTA o ALQUILER). No se puede deducir.");
        }
        throw new ReglaNegocioException(
                "Falta la operacion: declara VENTA o ALQUILER. La propiedad no tiene ningun "
                        + "encargo vivo del que deducirla, y suponer alquiler guardaria un dato falso.");
    }

    /**
     * Lo mismo, pero sin excepcion: {@code empty()} cuando no se puede saber.
     * Lo usa el motor de captura, que no falla — <b>declara lo que falta</b>.
     */
    public Optional<OperacionInmobiliaria> deducir(long idOrganizacion, long idPropiedad) {
        List<Captacion> vivos = captaciones.encargosVivosDe(idOrganizacion, idPropiedad);
        return vivos.size() == 1 ? Optional.of(vivos.get(0).operacion()) : Optional.empty();
    }

    /** El encargo vivo de esa operacion, si lo hay. Es el dueno de la serie. */
    public Optional<Captacion> encargoDe(long idOrganizacion, long idPropiedad,
                                         OperacionInmobiliaria operacion) {
        return captaciones.encargoVivoDe(idOrganizacion, idPropiedad, operacion.codigo());
    }

    /**
     * Traduce el texto del cable a la operacion, convirtiendo el
     * {@link IllegalArgumentException} del dominio en el error de negocio que
     * la web sabe devolver como 400. Los mensajes del enum ya explican por que
     * COMPRA y AMBAS no existen; aqui solo cambian de tipo.
     */
    public static OperacionInmobiliaria deTexto(String valor) {
        try {
            return OperacionInmobiliaria.desde(valor);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
    }

    private void exigirCoherenciaConLosEncargos(OperacionInmobiliaria operacion,
                                                List<Captacion> vivos) {
        if (vivos.isEmpty()) {
            // Declarar la operacion a mano no crea el encargo que la sostiene
            // (V75). Sin ningun encargo vivo, el hito nacia HUERFANO -con
            // `id_captacion` nulo- y los dos lectores discrepaban: la ficha
            // universal lo filtra y `GET /locales/{id}/precios` lo ensena. Un
            // mismo dato con dos verdades es peor que no tenerlo.
            throw new ReglaNegocioException(
                    "Esta propiedad no tiene ningun encargo vivo, asi que no hay serie economica "
                            + "donde anotar su " + operacion.nombreDelImporte() + ". Captala "
                            + "primero: el precio autorizado pertenece a un encargo.");
        }
        boolean encaja = vivos.stream().anyMatch(c -> operacion == c.operacion());
        if (!encaja) {
            String queHay = vivos.stream().map(c -> c.operacion().name()).reduce((a, b) -> a + " y " + b)
                    .orElse("");
            throw new ReglaNegocioException(
                    "Declaraste " + operacion.name() + ", pero el encargo vivo de esta propiedad es de "
                            + queHay + ". Abre un encargo de " + operacion.name()
                            + " antes de registrar su " + operacion.nombreDelImporte() + ".");
        }
    }
}
