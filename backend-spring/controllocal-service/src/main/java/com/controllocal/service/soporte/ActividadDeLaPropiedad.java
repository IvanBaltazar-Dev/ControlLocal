package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
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
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.InteraccionService.FichaInteraccion;
import com.controllocal.service.InteraccionService.FinalidadLectura;
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
 * Actividad de una propiedad: hechos de sus Encargos visibles e interacciones
 * autorizadas que pueden precederlos (C4, §§1/4). La comunicación conserva una
 * identidad y todos sus orígenes; sólo un origen único determina idEncargo/operación.
 * No se siguen sus vínculos para recuperar contactos o notas fuera de la proyección.
 * Las consultas no tienen un tope silencioso que aparente una cronología completa.
 */
@Component
public class ActividadDeLaPropiedad {

    public static final String OPORTUNIDAD = "OPORTUNIDAD";
    public static final String VISITA = "VISITA";
    public static final String INTERACCION = "INTERACCION";
    public static final String EXPEDIENTE = "EXPEDIENTE";
    public static final String CONTRATO = "CONTRATO";

    private final OportunidadComercialRepository oportunidades;
    private final VisitaRepository visitas;
    private final InteraccionService interacciones;
    private final SolicitudAlquilerRepository expedientes;
    private final ContratoAlquilerRepository contratos;

    public ActividadDeLaPropiedad(OportunidadComercialRepository oportunidades,
                                  VisitaRepository visitas,
                                  InteraccionService interacciones,
                                  SolicitudAlquilerRepository expedientes,
                                  ContratoAlquilerRepository contratos) {
        this.oportunidades = oportunidades;
        this.visitas = visitas;
        this.interacciones = interacciones;
        this.expedientes = expedientes;
        this.contratos = contratos;
    }

    /**
     * @param encargos los autorizados de la propiedad, incluidos los cerrados.
     *                 Un contrato firmado en 2024 cuelga de un encargo que hoy
     *                 esta cerrado, y filtrarlos por vivos borraria el cierre
     *                 mas importante de la ficha
     */
    public ActividadPropiedad de(Actor actor, long idPropiedad, List<Captacion> encargos) {
        Alcances.exigirOperador(actor);
        long idOrganizacion = actor.idOrganizacion();
        if (encargos.isEmpty()) {
            return new ActividadPropiedad(List.of(), List.of(),
                    interaccionesDe(actor, idPropiedad, Map.of()), List.of(), List.of());
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
                interaccionesDe(actor, idPropiedad, operaciones),
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
                        "oportunidades/" + o.getId(), null))
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
                        "visitas", null))
                .toList();
    }

    /**
     * Una sola lectura canónica incluye vínculos directos y padres autorizados,
     * sin exigir Encargo ni inferir la identidad del interlocutor.
     */
    private List<HechoDeActividad> interaccionesDe(Actor actor, long idPropiedad,
                                                   Map<Long, OperacionInmobiliaria> operaciones) {
        return interacciones.porPropiedad(idPropiedad, actor,
                        FinalidadLectura.FICHA_PROPIEDAD).stream()
                .map(i -> hecho(INTERACCION, i.id(), null,
                        PresentacionDeInteraccion.titulo(i), PresentacionDeInteraccion.detalle(i),
                        PresentacionDeInteraccion.estado(i), PresentacionDeInteraccion.resultado(i),
                        i.fechaHecho(), null, null, idEncargoDe(i, operaciones), operaciones,
                        "interacciones/" + i.id(), List.copyOf(i.vinculos())))
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
                        "solicitudes/" + s.getCodigoSolicitud(), null))
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
                        "propiedades-alquiladas", null))
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
                                          String ruta, List<InteraccionService.VinculoFicha> vinculos) {
        OperacionInmobiliaria operacion = idEncargo == null ? null : operaciones.get(idEncargo);
        return new HechoDeActividad(proceso, id, codigo, titulo, detalle, estado, estadoRotulo,
                fecha, monto, moneda, idEncargo,
                operacion == null ? null : operacion.name(),
                // El valor para comparar y el rotulo para leer, como en el
                // resto del read model: «Venta», no VENTA.
                operacion == null ? null : enFrase(operacion.name()),
                ruta, vinculos);
    }

    private static Long idEncargo(OportunidadComercial oportunidad) {
        return oportunidad == null || oportunidad.getCaptacion() == null
                ? null : oportunidad.getCaptacion().getId();
    }

    private static Long idEncargoDe(FichaInteraccion interaccion,
                                    Map<Long, OperacionInmobiliaria> operaciones) {
        List<Long> ids = interaccion.vinculos().stream()
                .filter(v -> "ENCARGO".equals(v.tipo()) && operaciones.containsKey(v.referencia()))
                .map(InteraccionService.VinculoFicha::referencia).distinct().toList();
        // Varias referencias no se reducen falsamente a un único origen/operación.
        return ids.size() == 1 ? ids.getFirst() : null;
    }

    /** VENTA -> Venta. El valor viaja en mayusculas; la persona lo lee en frase. */
    private static String enFrase(String valor) {
        return valor.charAt(0) + valor.substring(1).toLowerCase(java.util.Locale.ROOT);
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
