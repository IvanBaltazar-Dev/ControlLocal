package com.controllocal.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ComisionLiquidacionBusinessLogic;
import com.controllocal.bl.ContratoAlquilerBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ComisionLiquidacionBusinessLogicImpl;
import com.controllocal.bl.impl.ContratoAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.EstadoContrato;
import com.controllocal.model.comercial.enums.FormaPago;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("contratos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContratosRest {

    private final ContratoAlquilerBusinessLogic contratos = new ContratoAlquilerBusinessLogicImpl();
    private final SolicitudAlquilerBusinessLogic solicitudes = new SolicitudAlquilerBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final ComisionLiquidacionBusinessLogic comisiones = new ComisionLiquidacionBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.ContratoResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("100") int tamano) {
        List<Dtos.ContratoResponse> fuente = contratosDelUsuario(SeguridadRest.usuario(request));
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        return new PageResponse<>(fuente.subList(desde, hasta), fuente.size(), paginaValida, tamanoValido);
    }

    @POST
    public Response registrar(Dtos.ContratoRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.idSolicitud() == null || dto.idSolicitud() <= 0) {
            throw ApiException.badRequest("Selecciona la solicitud aprobada que se va a alquilar.");
        }
        SolicitudAlquiler solicitud = solicitudes.buscarPorId(dto.idSolicitud())
                .orElseThrow(() -> ApiException.noEncontrado("Solicitud"));
        if (!esSolicitudDelAgente(solicitud, usuario)) {
            throw ApiException.prohibido();
        }

        EstadoContrato estado = estadoCierre(dto.estadoContrato());
        LocalDate fechaCierre = dto.fechaCierre() != null ? dto.fechaCierre() : LocalDate.now();
        if (fechaCierre.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("La fecha de cierre no puede ser futura.");
        }

        Long idContrato = contratos.registrarPorSolicitud(
                dto.idSolicitud(), fechaCierre, estado, dto.incidencias());

        ContratoAlquiler creado = contratos.buscarPorId(idContrato)
                .orElseThrow(() -> ApiException.noEncontrado("Contrato"));
        SolicitudAlquiler refrescada = solicitudes.buscarPorId(dto.idSolicitud()).orElse(solicitud);
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.ContratoResponse.desde(creado, refrescada, comision(creado)))
                .build();
    }

    // Etapa 2: el broker supervisor define el monto real del agente; la empresa se calcula sola.
    // Solo sobre contratos de captaciones que el broker supervisa y con la comision aun Pendiente.
    @POST
    @Path("{idContrato}/comision/asignar")
    public Dtos.ContratoResponse asignarComision(@PathParam("idContrato") long idContrato,
            Dtos.ComisionAsignarRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER");
        if (dto == null || dto.montoAgente() == null) {
            throw ApiException.badRequest("Indica el monto del agente.");
        }
        ContratoAlquiler contrato = contratos.buscarPorId(idContrato)
                .orElseThrow(() -> ApiException.noEncontrado("Contrato"));
        SolicitudAlquiler solicitud = solicitudDe(contrato);
        if (!brokerSupervisaContrato(solicitud, usuario)) {
            throw ApiException.prohibido();
        }
        ComisionLiquidacion comision = comision(contrato);
        if (comision == null || comision.getIdComisionLiquidacion() == null) {
            throw ApiException.noEncontrado("Liquidacion de comision");
        }
        ComisionLiquidacion actualizada = comisiones.asignarMontoAgente(
                comision.getIdComisionLiquidacion(), dto.montoAgente());
        return Dtos.ContratoResponse.desde(contrato, solicitud, actualizada, true);
    }

    // Etapa 2/3: el broker supervisor registra el desenlace del cobro (Cobrada/Anulada),
    // con fecha de cobro y forma de pago cuando es Cobrada. El administrador no registra
    // cobros (solo lectura); al marcarse Cobrada, la alerta llega al agente y al admin.
    // Solo sobre contratos de captaciones que el broker supervisa.
    @POST
    @Path("{idContrato}/comision/cobro")
    public Dtos.ContratoResponse registrarCobro(@PathParam("idContrato") long idContrato,
            Dtos.ComisionCobroRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER");
        if (dto == null || dto.estado() == null || dto.estado().isBlank()) {
            throw ApiException.badRequest("Indica el estado del cobro (Cobrada o Anulada).");
        }
        ContratoAlquiler contrato = contratos.buscarPorId(idContrato)
                .orElseThrow(() -> ApiException.noEncontrado("Contrato"));
        SolicitudAlquiler solicitud = solicitudDe(contrato);
        if (!brokerSupervisaContrato(solicitud, usuario)) {
            throw ApiException.prohibido();
        }
        ComisionLiquidacion comision = comision(contrato);
        if (comision == null || comision.getIdComisionLiquidacion() == null) {
            throw ApiException.noEncontrado("Liquidacion de comision");
        }
        ComisionLiquidacion actualizada = comisiones.registrarCobro(
                comision.getIdComisionLiquidacion(), estadoCobro(dto.estado()),
                dto.fechaCobro(), formaPago(dto.formaPago()));
        return Dtos.ContratoResponse.desde(contrato, solicitud, actualizada, true);
    }

    private SolicitudAlquiler solicitudDe(ContratoAlquiler contrato) {
        Long idSolicitud = contrato.getSolicitudAlquiler() != null
                ? contrato.getSolicitudAlquiler().getIdSolicitud() : null;
        if (idSolicitud == null) {
            throw ApiException.noEncontrado("Solicitud del contrato");
        }
        return solicitudes.buscarPorId(idSolicitud)
                .orElseThrow(() -> ApiException.noEncontrado("Solicitud del contrato"));
    }

    private boolean brokerSupervisaContrato(SolicitudAlquiler solicitud, UsuarioAutenticado usuario) {
        Long idCaptacion = solicitud != null && solicitud.getCaptacion() != null
                ? solicitud.getCaptacion().getIdCaptacion() : null;
        if (idCaptacion == null) {
            return false;
        }
        return captaciones.listarPorBroker(usuario.idDominio()).stream()
                .anyMatch(c -> idCaptacion.equals(c.getIdCaptacion()));
    }

    private static EstadoComision estadoCobro(String codigo) {
        EstadoComision estado;
        try {
            estado = EstadoComision.valueOf(codigo.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Estado de cobro invalido.");
        }
        if (estado != EstadoComision.COBRADA && estado != EstadoComision.ANULADA) {
            throw ApiException.badRequest("El cobro solo admite los estados Cobrada o Anulada.");
        }
        return estado;
    }

    private static FormaPago formaPago(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        try {
            return FormaPago.valueOf(codigo.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Forma de pago invalida.");
        }
    }

    // El cierre solo admite Firmado o Vigente; por defecto Vigente. Rechaza cualquier otro estado.
    private static EstadoContrato estadoCierre(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return EstadoContrato.VIGENTE;
        }
        EstadoContrato estado;
        try {
            estado = EstadoContrato.fromCodigo(codigo.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Estado de contrato invalido.");
        }
        if (estado != EstadoContrato.FIRMADO && estado != EstadoContrato.VIGENTE) {
            throw ApiException.badRequest("El cierre solo admite los estados Firmado o Vigente.");
        }
        return estado;
    }

    // Liquidacion de comision del contrato (la creada al cerrar); null si no tiene.
    private ComisionLiquidacion comision(ContratoAlquiler contrato) {
        if (contrato.getIdContratoAlquiler() == null) {
            return null;
        }
        return comisiones.listarPorContrato(contrato.getIdContratoAlquiler()).stream()
                .findFirst()
                .orElse(null);
    }

    // Alcance por rol: el agente ve sus contratos; el broker/admin, los de las
    // captaciones que supervisa. Se enriquece cada contrato con su solicitud.
    private List<Dtos.ContratoResponse> contratosDelUsuario(UsuarioAutenticado usuario) {
        boolean esAdmin = usuario.tieneRol("ADMIN");
        boolean esBroker = usuario.tieneRol("BROKER");
        boolean esAgente = "AGENTE".equals(usuario.rol());
        if (!esAdmin && !esBroker && !esAgente) {
            throw ApiException.prohibido();
        }
        Set<Long> captacionesBroker = esBroker
                ? captaciones.listarPorBroker(usuario.idDominio()).stream()
                        .map(Captacion::getIdCaptacion).collect(Collectors.toSet())
                : Set.of();

        // Precarga en bloque para evitar el N+1: antes se hacia una consulta de solicitud
        // y otra de comision por CADA contrato (1 + 2N). Ahora se traen las tres listas
        // completas una sola vez y se cruzan en memoria por id.
        Map<Long, SolicitudAlquiler> solicitudPorId = new HashMap<>();
        for (SolicitudAlquiler s : solicitudes.listarTodos()) {
            if (s.getIdSolicitud() != null) {
                solicitudPorId.putIfAbsent(s.getIdSolicitud(), s);
            }
        }
        // La primera comision por contrato (las listas vienen ordenadas por id, igual que
        // listarPorContrato), que es justo la que usaba comision(contrato).
        Map<Long, ComisionLiquidacion> comisionPorContrato = new HashMap<>();
        for (ComisionLiquidacion c : comisiones.listarTodos()) {
            Long idContrato = c.getContratoAlquiler() != null
                    ? c.getContratoAlquiler().getIdContratoAlquiler() : null;
            if (idContrato != null) {
                comisionPorContrato.putIfAbsent(idContrato, c);
            }
        }

        List<Dtos.ContratoResponse> resultado = new ArrayList<>();
        for (ContratoAlquiler contrato : contratos.listarTodos()) {
            Long idSolicitud = contrato.getSolicitudAlquiler() != null
                    ? contrato.getSolicitudAlquiler().getIdSolicitud() : null;
            SolicitudAlquiler solicitud = idSolicitud != null
                    ? solicitudPorId.get(idSolicitud) : null;
            if (solicitud == null) {
                continue;
            }
            if (esAgente && !esSolicitudDelAgente(solicitud, usuario)) {
                continue;
            }
            if (esBroker) {
                Long idCaptacion = solicitud.getCaptacion() != null
                        ? solicitud.getCaptacion().getIdCaptacion() : null;
                if (idCaptacion == null || !captacionesBroker.contains(idCaptacion)) {
                    continue;
                }
            }
            ComisionLiquidacion comision = contrato.getIdContratoAlquiler() != null
                    ? comisionPorContrato.get(contrato.getIdContratoAlquiler()) : null;
            resultado.add(Dtos.ContratoResponse.desde(contrato, solicitud, comision, esAdmin || esBroker));
        }
        return resultado;
    }

    private boolean esSolicitudDelAgente(SolicitudAlquiler solicitud, UsuarioAutenticado usuario) {
        return solicitud.getAgenteResponsable() != null
                && solicitud.getAgenteResponsable().getIdAgente() != null
                && usuario.idDominio() == solicitud.getAgenteResponsable().getIdAgente();
    }
}
