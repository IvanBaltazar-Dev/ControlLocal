package com.controllocal.rest;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.InteraccionComercialBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.CanalContacto;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("interacciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InteraccionesRest {

    private final InteraccionComercialBusinessLogic interacciones = new InteraccionComercialBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades = new OportunidadComercialBusinessLogicImpl();
    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.InteraccionResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("50") int tamano,
            @QueryParam("contexto") String contexto,
            @QueryParam("idOportunidad") Long idOportunidad,
            @QueryParam("idProspeccion") Long idProspeccion,
            @QueryParam("idCaptacion") Long idCaptacion,
            @QueryParam("idCliente") Long idCliente,
            @QueryParam("grupo") String grupo,
            @QueryParam("resultado") String resultado,
            @QueryParam("canal") String canal,
            @QueryParam("q") String q) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Set<Long> agentesPermitidos = agentesPermitidos(usuario);
        List<InteraccionComercial> base = listarBase(usuario, agentesPermitidos, idOportunidad, idProspeccion, idCaptacion, idCliente);
        List<InteraccionComercial> fuente = base.stream()
                .filter(item -> visiblePara(usuario, item, agentesPermitidos))
                .filter(item -> coincideContexto(contexto, item))
                .toList();
        enriquecerDirectas(fuente);
        Map<Long, OportunidadComercial> ops = contieneOportunidades(fuente) ? mapaOportunidades(fuente) : Map.of();

        List<Dtos.InteraccionResponse> respuestas = fuente.stream()
                .sorted(Comparator.comparing(InteraccionesRest::fechaHoraOrden,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(InteraccionComercial::getIdInteraccion, Comparator.nullsLast(Comparator.reverseOrder())))
                // Map.of().get(null) lanza NPE (los mapas inmutables rechazan keys null);
                // las interacciones de PROSPECCION/CAPTACION no tienen idOportunidad, asi
                // que primero verificamos si hay id antes de consultar el cache.
                .map(item -> {
                    Long idOp = idOportunidad(item);
                    OportunidadComercial oportunidad = idOp != null ? ops.get(idOp) : null;
                    return Dtos.InteraccionResponse.desde(item, oportunidad);
                })
                .filter(item -> coincideGrupo(grupo, item))
                .filter(item -> resultado == null || resultado.isBlank() || resultado.equalsIgnoreCase(item.resultado()))
                .filter(item -> canal == null || canal.isBlank() || canal.equalsIgnoreCase(item.canalContacto()))
                .filter(item -> coincideBusqueda(q, item))
                .toList();
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, respuestas.size());
        int hasta = Math.min(desde + tamanoValido, respuestas.size());
        return new PageResponse<>(respuestas.subList(desde, hasta), respuestas.size(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.InteraccionResponse obtener(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        InteraccionComercial interaccion = interacciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Interaccion"));
        if (!visiblePara(usuario, interaccion, agentesPermitidos(usuario))) {
            throw ApiException.prohibido();
        }
        enriquecerDirectas(List.of(interaccion));
        return Dtos.InteraccionResponse.desde(interaccion, oportunidadPara(interaccion));
    }

    @POST
    public Response registrar(Dtos.InteraccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("La interaccion es obligatoria.");
        }
        String contexto = contexto(dto);

        InteraccionComercial interaccion = new InteraccionComercial();
        interaccion.setContexto(contexto);
        asignarEntidad(interaccion, dto, contexto);
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(usuario.idDominio());
        interaccion.setAgenteResponsable(agente);
        interaccion.setCanalContacto(canal(dto.canalContacto()));
        if (dto.resultado() != null && !dto.resultado().isBlank()) {
            interaccion.setResultado(resultado(dto.resultado(), contexto));
        }
        interaccion.setObservaciones(dto.observaciones());
        interaccion.setTranscripcionNota(dto.transcripcionNota());

        long id = interacciones.registrar(interaccion);
        InteraccionComercial creada = interacciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Interaccion"));
        enriquecerDirectas(List.of(creada));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.InteraccionResponse.desde(creada, oportunidadPara(creada)))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.InteraccionResponse actualizar(@PathParam("id") long id, Dtos.InteraccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        InteraccionComercial interaccion = interacciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Interaccion"));
        if (!visiblePara(usuario, interaccion, agentesPermitidos(usuario))) {
            throw ApiException.prohibido();
        }
        if (dto != null && dto.resultado() != null && !dto.resultado().isBlank()) {
            interaccion.setResultado(resultado(dto.resultado(), contexto(interaccion)));
        }
        if (dto != null && dto.observaciones() != null) {
            interaccion.setObservaciones(dto.observaciones());
        }
        interacciones.actualizar(interaccion);
        enriquecerDirectas(List.of(interaccion));
        return Dtos.InteraccionResponse.desde(interaccion, oportunidadPara(interaccion));
    }

    // Solo carga las oportunidades referenciadas por las interacciones visibles (antes traia la
    // tabla completa para enriquecer la pagina).
    private Map<Long, OportunidadComercial> mapaOportunidades(List<InteraccionComercial> fuente) {
        Set<Long> ids = fuente.stream()
                .map(InteraccionesRest::idOportunidad)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return oportunidades.listarPorIds(ids).stream()
                .filter(item -> item.getIdOportunidad() != null)
                .collect(Collectors.toMap(OportunidadComercial::getIdOportunidad, Function.identity(), (a, b) -> a));
    }

    private OportunidadComercial oportunidadPara(InteraccionComercial interaccion) {
        Long idOportunidad = idOportunidad(interaccion);
        return idOportunidad != null
                ? oportunidades.buscarPorId(idOportunidad).orElse(null)
                : null;
    }

    private void enriquecerDirectas(List<InteraccionComercial> fuente) {
        for (InteraccionComercial item : fuente) {
            if (item.getProspeccion() != null && item.getProspeccion().getIdProspeccion() != null) {
                prospecciones.buscarPorId(item.getProspeccion().getIdProspeccion()).ifPresent(item::setProspeccion);
            }
            if (item.getCaptacion() != null && item.getCaptacion().getIdCaptacion() != null) {
                captaciones.buscarPorId(item.getCaptacion().getIdCaptacion()).ifPresent(item::setCaptacion);
            }
        }
    }

    private List<InteraccionComercial> listarBase(
            UsuarioAutenticado usuario,
            Set<Long> agentesPermitidos,
            Long idOportunidad,
            Long idProspeccion,
            Long idCaptacion,
            Long idCliente) {
        int filtros = 0;
        filtros += idOportunidad != null ? 1 : 0;
        filtros += idProspeccion != null ? 1 : 0;
        filtros += idCaptacion != null ? 1 : 0;
        filtros += idCliente != null ? 1 : 0;
        if (filtros > 1) {
            throw ApiException.badRequest("Filtra por una sola entidad de interaccion.");
        }
        if (idOportunidad != null) {
            return interacciones.listarPorOportunidad(idOportunidad);
        }
        if (idProspeccion != null) {
            return interacciones.listarPorProspeccion(idProspeccion);
        }
        if (idCaptacion != null) {
            return interacciones.listarPorCaptacion(idCaptacion);
        }
        if (idCliente != null) {
            return interacciones.listarPorCliente(idCliente);
        }
        // Sin filtro de entidad: acotar el origen al alcance del usuario en SQL.
        // ADMIN ve todo (agentesPermitidos vacio = "todos", no "ninguno"); el resto
        // queda limitado a sus agentes (un broker sin equipo no ve nada).
        if (usuario.tieneRol("ADMIN")) {
            return interacciones.listarTodos();
        }
        return interacciones.listarPorAgentes(agentesPermitidos);
    }

    private static boolean contieneOportunidades(List<InteraccionComercial> fuente) {
        return fuente.stream().anyMatch(item -> idOportunidad(item) != null);
    }

    private static boolean coincideContexto(String contexto, InteraccionComercial interaccion) {
        if (contexto == null || contexto.isBlank()) {
            return true;
        }
        return contexto.equalsIgnoreCase(contexto(interaccion));
    }

    private static boolean coincideGrupo(String grupo, Dtos.InteraccionResponse item) {
        if (grupo == null || grupo.isBlank() || "TODAS".equalsIgnoreCase(grupo)) {
            return true;
        }
        boolean propietario = "PROPIETARIO".equalsIgnoreCase(item.personaTipo());
        return "PROPIETARIO".equalsIgnoreCase(grupo) ? propietario : !propietario;
    }

    private static boolean coincideBusqueda(String q, Dtos.InteraccionResponse item) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String aguja = q.trim().toLowerCase();
        return contiene(item.personaNombre(), aguja)
                || contiene(item.clienteNombre(), aguja)
                || contiene(item.propietarioNombre(), aguja)
                || contiene(item.codigoCaptacion(), aguja)
                || contiene(item.codigoProspeccion(), aguja)
                || contiene(item.agenteNombre(), aguja)
                || contiene(item.observaciones(), aguja);
    }

    private static boolean contiene(String valor, String q) {
        return valor != null && valor.toLowerCase().contains(q);
    }

    private static java.time.LocalDateTime fechaHoraOrden(InteraccionComercial interaccion) {
        return interaccion != null ? interaccion.getFechaHora() : null;
    }

    private static String contexto(Dtos.InteraccionRequest dto) {
        String contexto = dto.contexto();
        if (contexto == null || contexto.isBlank()) {
            if (dto.idProspeccion() != null) {
                contexto = "PROSPECCION";
            } else if (dto.idCaptacion() != null) {
                contexto = "CAPTACION";
            } else if (dto.idCliente() != null) {
                contexto = "CLIENTE";
            } else {
                contexto = "OPORTUNIDAD";
            }
        }
        contexto = contexto.trim().toUpperCase();
        if (!"OPORTUNIDAD".equals(contexto)
                && !"PROSPECCION".equals(contexto)
                && !"CAPTACION".equals(contexto)
                && !"CLIENTE".equals(contexto)) {
            throw ApiException.badRequest("Contexto de interaccion invalido: " + contexto);
        }
        return contexto;
    }

    private static void asignarEntidad(
            InteraccionComercial interaccion,
            Dtos.InteraccionRequest dto,
            String contexto) {
        switch (contexto) {
            case "PROSPECCION" -> {
                if (dto.idProspeccion() == null) {
                    throw ApiException.badRequest("La prospeccion de la interaccion es obligatoria.");
                }
                Prospeccion prospeccion = new Prospeccion();
                prospeccion.setIdProspeccion(dto.idProspeccion());
                interaccion.setProspeccion(prospeccion);
            }
            case "CAPTACION" -> {
                if (dto.idCaptacion() == null) {
                    throw ApiException.badRequest("La captacion de la interaccion es obligatoria.");
                }
                Captacion captacion = new Captacion();
                captacion.setIdCaptacion(dto.idCaptacion());
                interaccion.setCaptacion(captacion);
            }
            case "CLIENTE" -> {
                if (dto.idCliente() == null) {
                    throw ApiException.badRequest("El cliente interesado de la interaccion es obligatorio.");
                }
                ClienteInteresado cliente = new ClienteInteresado();
                cliente.setIdCliente(dto.idCliente());
                interaccion.setClienteInteresado(cliente);
            }
            case "OPORTUNIDAD" -> {
                if (dto.idOportunidad() == null) {
                    throw ApiException.badRequest("La oportunidad de la interaccion es obligatoria.");
                }
                OportunidadComercial oportunidad = new OportunidadComercial();
                oportunidad.setIdOportunidad(dto.idOportunidad());
                interaccion.setOportunidadComercial(oportunidad);
            }
            default -> throw ApiException.badRequest("Contexto de interaccion invalido: " + contexto);
        }
    }

    private static String contexto(InteraccionComercial interaccion) {
        String contexto = interaccion.getContexto();
        return contexto == null || contexto.isBlank() ? "OPORTUNIDAD" : contexto;
    }

    private Set<Long> agentesPermitidos(UsuarioAutenticado usuario) {
        if (usuario.tieneRol("ADMIN")) {
            return Set.of();
        }
        if (usuario.tieneRol("BROKER")) {
            return brokers.listarAgentesSupervisados(usuario.idDominio()).stream()
                    .map(item -> item.getIdAgente())
                    .collect(Collectors.toSet());
        }
        return Set.of(usuario.idDominio());
    }

    private boolean visiblePara(UsuarioAutenticado usuario, InteraccionComercial interaccion, Set<Long> agentesPermitidos) {
        if (usuario.tieneRol("ADMIN")) {
            return true;
        }
        Long idAgente = interaccion.getAgenteResponsable() != null
                ? interaccion.getAgenteResponsable().getIdAgente()
                : null;
        return idAgente != null && agentesPermitidos.contains(idAgente);
    }

    private static Long idOportunidad(InteraccionComercial interaccion) {
        return interaccion.getOportunidadComercial() != null
                ? interaccion.getOportunidadComercial().getIdOportunidad()
                : null;
    }

    private static CanalContacto canal(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw ApiException.badRequest("El canal de contacto es obligatorio.");
        }
        try {
            return CanalContacto.fromCodigo(codigo);
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Canal de contacto invalido: " + codigo);
        }
    }

    private static ResultadoInteraccion resultado(String codigo, String contexto) {
        try {
            ResultadoInteraccion resultado = ResultadoInteraccion.fromCodigo(codigo);
            if (!resultadoPermitido(contexto, resultado.getCodigo())) {
                throw ApiException.badRequest("Resultado no valido para " + contexto + ": " + codigo);
            }
            return resultado;
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Resultado de interaccion invalido: " + codigo);
        }
    }

    private static boolean resultadoPermitido(String contexto, String resultado) {
        return switch (contexto == null ? "OPORTUNIDAD" : contexto.trim().toUpperCase()) {
            case "PROSPECCION" -> Set.of(
                    "CONTACTADO", "REUNION_AGENDADA", "PROPUESTA_ENVIADA",
                    "ACEPTA_CAPTAR", "NO_ACEPTA", "RECONTACTAR").contains(resultado);
            case "CAPTACION" -> Set.of(
                    "DOCS_SOLICITADOS", "CONDICIONES_AJUSTADAS", "PUBLICACION_COORDINADA",
                    "PROPIETARIO_OBSERVA", "LISTO_PARA_PUBLICAR", "PAUSAR_GESTION").contains(resultado);
            case "CLIENTE" -> Set.of(
                    "BUSQUEDA_LEVANTADA", "PROPUESTA_ENVIADA", "REQUIERE_OPCIONES",
                    "NO_RESPONDE", "SEGUIMIENTO", "DESCARTADO").contains(resultado);
            default -> Set.of(
                    "INTERESADO", "VISITA_AGENDADA", "OFERTA_SOLICITADA",
                    "NEGOCIANDO", "NO_INTERESADO", "DESCARTADO").contains(resultado);
        };
    }
}
