package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.InteraccionComercial;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.domain.comun.EstadosDominio.EstadoContrato;
import com.controllocal.domain.comun.EstadosDominio.EstadoOportunidad;
import com.controllocal.domain.comun.EstadosDominio.EstadoSolicitud;
import com.controllocal.domain.comun.EstadosDominio.EstadoVisita;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.PropiedadUniversalService.ActividadPropiedad;
import com.controllocal.service.PropiedadUniversalService.HechoDeActividad;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Que ha pasado con una propiedad, y de que encargo viene cada cosa</b>
 * (D-E4-1, D-A-1).
 *
 * <h2>Por que esta regla vive aqui y no en la pantalla</h2>
 * «La actividad de una propiedad es la actividad de sus encargos» es una regla
 * de dominio, no una consulta. Escrita en el cliente obliga a recorrer
 * {@code visita -> oportunidad -> captacion -> propiedad} para saber de donde
 * sale cada fila, que es exactamente la topologia del modelo que una interfaz
 * no debe conocer (D-E4-3). Y con dos interfaces --BROX Web y KAIROS-- serian
 * dos recorridos que se separan.
 *
 * <p>Escrita aqui, se escribe una vez: cada hecho sale de la consulta <b>con su
 * {@code idEncargo} puesto</b>.
 *
 * <h2>La procedencia no es decoracion</h2>
 * Una propiedad en venta y en alquiler acumula visitas de gente que quiere
 * comprar y de gente que quiere alquilar. En una lista plana se leen igual, y
 * eso deshace justo lo que el modelo universal vino a arreglar. Por eso todo
 * {@link HechoDeActividad} arrastra el encargo del que nace, y la operacion de
 * ese encargo <b>en palabras</b>.
 *
 * <h2>Lo que se pide, y lo que no</h2>
 * Cinco consultas por ficha, todas por {@code id in (...)} sobre los encargos
 * que ya se leyeron. Ninguna se recorta: son los hechos de <b>una</b> propiedad,
 * no una bandeja, y un tope silencioso haria que la ficha pareciera completa
 * cuando no lo esta.
 */
@Component
public class ActividadDeLaPropiedad {

    public static final String OPORTUNIDAD = "OPORTUNIDAD";
    public static final String VISITA = "VISITA";
    public static final String INTERACCION = "INTERACCION";
    public static final String EXPEDIENTE = "EXPEDIENTE";
    public static final String CONTRATO = "CONTRATO";

    /**
     * El canal de un contacto, en palabras.
     *
     * <p>Esta aqui y no en el cliente por el mismo motivo que
     * {@code rotuloDelTipo}: es vocabulario del negocio. La copia que el SPA
     * heredo sigue sirviendo a las pantallas que aun no han migrado; esta
     * ficha ya no la necesita.
     */
    private static final Map<String, String> CANALES = Map.of(
            "L", "Llamada",
            "W", "WhatsApp",
            "E", "Email",
            "P", "Presencial",
            "R", "Reunion",
            "T", "Portal",
            "O", "Otro");

    private final OportunidadComercialRepository oportunidades;
    private final VisitaRepository visitas;
    private final InteraccionComercialRepository interacciones;
    private final SolicitudAlquilerRepository expedientes;
    private final ContratoAlquilerRepository contratos;

    public ActividadDeLaPropiedad(OportunidadComercialRepository oportunidades,
                                  VisitaRepository visitas,
                                  InteraccionComercialRepository interacciones,
                                  SolicitudAlquilerRepository expedientes,
                                  ContratoAlquilerRepository contratos) {
        this.oportunidades = oportunidades;
        this.visitas = visitas;
        this.interacciones = interacciones;
        this.expedientes = expedientes;
        this.contratos = contratos;
    }

    /**
     * @param encargos los de la propiedad, <b>todos</b>: tambien los cerrados.
     *                 Un contrato firmado en 2024 cuelga de un encargo que hoy
     *                 esta cerrado, y filtrarlos por vivos borraria el cierre
     *                 mas importante de la ficha
     */
    public ActividadPropiedad de(long idOrganizacion, List<Captacion> encargos) {
        if (encargos.isEmpty()) {
            return new ActividadPropiedad(List.of(), List.of(), List.of(), List.of(), List.of());
        }
        List<Long> ids = encargos.stream().map(Captacion::getId).toList();

        // La operacion de cada encargo, resuelta una vez: cada hecho la lleva
        // pegada y sin ella «visita del 19 de agosto» no dice si el interesado
        // venia a comprar o a alquilar.
        Map<Long, OperacionInmobiliaria> operaciones = new LinkedHashMap<>();
        for (Captacion encargo : encargos) {
            operaciones.put(encargo.getId(), encargo.operacion());
        }

        return new ActividadPropiedad(
                oportunidadesDe(idOrganizacion, ids, operaciones),
                visitasDe(idOrganizacion, ids, operaciones),
                interaccionesDe(idOrganizacion, ids, operaciones),
                expedientesDe(idOrganizacion, ids, operaciones),
                contratosDe(idOrganizacion, ids, operaciones));
    }

    // ==================================================================

    private List<HechoDeActividad> oportunidadesDe(long idOrganizacion, List<Long> ids,
                                                   Map<Long, OperacionInmobiliaria> operaciones) {
        return oportunidades.listarFichaPorEncargos(idOrganizacion, ids).stream()
                .map(o -> hecho(OPORTUNIDAD, o.getId(), o.getCodigoOportunidad(),
                        nombre(o.getCliente()),
                        "Interesado " + nombre(o.getCliente()),
                        o.estadoActual(), rotulo(EstadoOportunidad.desde(o.estadoActual())),
                        fecha(o.getFechaRegistro()), null, null,
                        idEncargo(o), operaciones,
                        "oportunidades/" + o.getId()))
                .toList();
    }

    private List<HechoDeActividad> visitasDe(long idOrganizacion, List<Long> ids,
                                             Map<Long, OperacionInmobiliaria> operaciones) {
        return visitas.listarFichaPorEncargos(idOrganizacion, ids).stream()
                .map(v -> hecho(VISITA, v.getId(), null,
                        "Visita de " + nombre(v.getOportunidad().getCliente()),
                        v.getHoraVisita() == null ? null : "A las " + v.getHoraVisita(),
                        v.estadoActual(), rotulo(EstadoVisita.desde(v.estadoActual())),
                        v.getFechaVisita(), null, null,
                        idEncargo(v.getOportunidad()), operaciones,
                        "visitas"))
                .toList();
    }

    /**
     * La bitacora, con sus dos ramas: lo hablado con el propietario cuelga del
     * encargo y lo hablado con el interesado cuelga de la oportunidad. Las dos
     * son actividad de la misma propiedad.
     */
    private List<HechoDeActividad> interaccionesDe(long idOrganizacion, List<Long> ids,
                                                   Map<Long, OperacionInmobiliaria> operaciones) {
        return interacciones.listarFichaPorEncargos(idOrganizacion, ids).stream()
                .map(i -> hecho(INTERACCION, i.getId(), null,
                        canal(i.getCanalContacto())
                                + (i.getCliente() == null ? " con el propietario"
                                                          : " con " + nombre(i.getCliente())),
                        i.getObservaciones(),
                        null, null,
                        fecha(i.getFechaHora()), null, null,
                        idEncargoDe(i), operaciones,
                        "interacciones/" + i.getId()))
                .toList();
    }

    private List<HechoDeActividad> expedientesDe(long idOrganizacion, List<Long> ids,
                                                 Map<Long, OperacionInmobiliaria> operaciones) {
        return expedientes.listarFichaPorEncargos(idOrganizacion, ids).stream()
                .map(s -> hecho(EXPEDIENTE, s.getId(), s.getCodigoSolicitud(),
                        "Expediente de " + nombre(s.getOportunidad().getCliente()),
                        s.getMontoPropuesto() == null ? null : "Ofrece",
                        s.estadoActual(), rotulo(EstadoSolicitud.desde(s.estadoActual())),
                        s.getFechaRegistro(), s.getMontoPropuesto(), s.getMoneda(),
                        idEncargo(s.getOportunidad()), operaciones,
                        "solicitudes/" + s.getCodigoSolicitud()))
                .toList();
    }

    private List<HechoDeActividad> contratosDe(long idOrganizacion, List<Long> ids,
                                               Map<Long, OperacionInmobiliaria> operaciones) {
        return contratos.listarFichaPorEncargos(idOrganizacion, ids).stream()
                .map(c -> hecho(CONTRATO, c.getId(),
                        c.getSolicitud() == null ? null : c.getSolicitud().getCodigoSolicitud(),
                        "Cierre con " + nombre(c.getOportunidad().getCliente()),
                        null,
                        c.estadoActual(), rotulo(EstadoContrato.desde(c.estadoActual())),
                        c.getFechaCierre(), c.getRentaContractual(), c.getMoneda(),
                        idEncargo(c.getOportunidad()), operaciones,
                        "propiedades-alquiladas"))
                .toList();
    }

    // ==================================================================

    /**
     * El hecho, ya escrito. {@code operacion} sale del encargo y nunca de la
     * fila: es la unica forma de que no haga falta deducirla despues.
     */
    private static HechoDeActividad hecho(String proceso, Long id, String codigo, String titulo,
                                          String detalle, String estado, String estadoRotulo,
                                          LocalDate fecha, BigDecimal monto, String moneda,
                                          Long idEncargo,
                                          Map<Long, OperacionInmobiliaria> operaciones,
                                          String ruta) {
        OperacionInmobiliaria operacion = idEncargo == null ? null : operaciones.get(idEncargo);
        return new HechoDeActividad(proceso, id, codigo, titulo, detalle, estado, estadoRotulo,
                fecha, monto, moneda, idEncargo,
                operacion == null ? null : operacion.name(),
                // El valor para comparar y el rotulo para leer, como en el
                // resto del read model: «Venta», no VENTA.
                operacion == null ? null : enFrase(operacion.name()),
                ruta);
    }

    private static Long idEncargo(OportunidadComercial oportunidad) {
        return oportunidad == null || oportunidad.getCaptacion() == null
                ? null : oportunidad.getCaptacion().getId();
    }

    /** El encargo de una interaccion: el suyo, o el de su oportunidad. */
    private static Long idEncargoDe(InteraccionComercial interaccion) {
        if (interaccion.getCaptacion() != null) {
            return interaccion.getCaptacion().getId();
        }
        return idEncargo(interaccion.getOportunidad());
    }

    /** VENTA -> Venta. El valor viaja en mayusculas; la persona lo lee en frase. */
    private static String enFrase(String valor) {
        return valor.charAt(0) + valor.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String canal(String codigo) {
        return CANALES.getOrDefault(codigo, "Contacto");
    }

    private static String rotulo(com.controllocal.domain.comun.EstadosDominio.Codigo estado) {
        return estado == null ? null : estado.descripcion();
    }

    private static LocalDate fecha(OffsetDateTime marca) {
        return marca == null ? null : marca.toLocalDate();
    }

    private static String nombre(DetalleCliente cliente) {
        if (cliente == null) {
            return "-";
        }
        PersonaRol rol = cliente.getRol();
        return rol == null || rol.getPersona() == null
                ? "-" : rol.getPersona().getNombresORazonSocial();
    }
}
