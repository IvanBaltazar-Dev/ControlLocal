package com.controllocal.rest;

import java.util.ArrayList;
import java.util.List;
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

        Long idContrato = contratos.registrarPorSolicitud(dto.idSolicitud());

        ContratoAlquiler creado = contratos.buscarPorId(idContrato)
                .orElseThrow(() -> ApiException.noEncontrado("Contrato"));
        SolicitudAlquiler refrescada = solicitudes.buscarPorId(dto.idSolicitud()).orElse(solicitud);
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.ContratoResponse.desde(creado, refrescada, comision(creado)))
                .build();
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
        boolean esBroker = usuario.tieneRol("BROKER", "ADMIN");
        boolean esAgente = "AGENTE".equals(usuario.rol());
        if (!esBroker && !esAgente) {
            throw ApiException.prohibido();
        }
        Set<Long> captacionesBroker = esBroker
                ? captaciones.listarPorBroker(usuario.idDominio()).stream()
                        .map(Captacion::getIdCaptacion).collect(Collectors.toSet())
                : Set.of();

        List<Dtos.ContratoResponse> resultado = new ArrayList<>();
        for (ContratoAlquiler contrato : contratos.listarTodos()) {
            Long idSolicitud = contrato.getSolicitudAlquiler() != null
                    ? contrato.getSolicitudAlquiler().getIdSolicitud() : null;
            SolicitudAlquiler solicitud = idSolicitud != null
                    ? solicitudes.buscarPorId(idSolicitud).orElse(null) : null;
            if (solicitud == null) {
                continue;
            }
            if (esAgente && !esSolicitudDelAgente(solicitud, usuario)) {
                continue;
            }
            if (esBroker && !esAgente) {
                Long idCaptacion = solicitud.getCaptacion() != null
                        ? solicitud.getCaptacion().getIdCaptacion() : null;
                if (idCaptacion == null || !captacionesBroker.contains(idCaptacion)) {
                    continue;
                }
            }
            resultado.add(Dtos.ContratoResponse.desde(contrato, solicitud, comision(contrato)));
        }
        return resultado;
    }

    private boolean esSolicitudDelAgente(SolicitudAlquiler solicitud, UsuarioAutenticado usuario) {
        return solicitud.getAgenteResponsable() != null
                && solicitud.getAgenteResponsable().getIdAgente() != null
                && usuario.idDominio() == solicitud.getAgenteResponsable().getIdAgente();
    }
}
