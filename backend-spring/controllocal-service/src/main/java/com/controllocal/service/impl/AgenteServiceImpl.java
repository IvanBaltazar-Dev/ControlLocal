package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion;
import com.controllocal.domain.comun.EstadosDominio.EstadoOportunidad;
import com.controllocal.domain.comun.EstadosDominio.EstadoSolicitud;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ComisionGeneradaPorMoneda;
import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.MovimientoComisionPorMoneda;
import com.controllocal.persistence.query.RepartoComisionPorMoneda;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AgenteService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.UsuariosInternos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class AgenteServiceImpl implements AgenteService {

    private final DetalleAgenteRepository agentes;
    private final DetalleBrokerRepository brokers;
    private final SupervisionAgenteRepository supervisiones;
    private final PersonaRepository personas;
    private final CaptacionRepository captaciones;
    private final OportunidadComercialRepository oportunidades;
    private final SolicitudAlquilerRepository solicitudes;
    private final ContratoAlquilerRepository contratos;
    private final UsuariosInternos usuarios;

    /** Tope de la lista corta de cierres en la ficha; el resto va en Cierres. */
    private static final int ULTIMOS_CIERRES = 8;

    public AgenteServiceImpl(DetalleAgenteRepository agentes,
                             DetalleBrokerRepository brokers,
                             SupervisionAgenteRepository supervisiones,
                             PersonaRepository personas,
                             CaptacionRepository captaciones,
                             OportunidadComercialRepository oportunidades,
                             SolicitudAlquilerRepository solicitudes,
                             ContratoAlquilerRepository contratos,
                             UsuariosInternos usuarios) {
        this.agentes = agentes;
        this.brokers = brokers;
        this.supervisiones = supervisiones;
        this.personas = personas;
        this.captaciones = captaciones;
        this.oportunidades = oportunidades;
        this.solicitudes = solicitudes;
        this.contratos = contratos;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaAgente> listar(int pagina, int tamano, Actor actor) {
        return listar(new FiltrosAgente(null, null, null, null, pagina, tamano), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaAgente> listar(FiltrosAgente filtros, Actor actor) {
        exigirBrokerOAdmin(actor);
        validarActorOperativo(actor);
        int paginaValida = Math.max(1, filtros.pagina());
        int tamanoValido = tamano(filtros.tamano());
        Page<DetalleAgente> page = agentes.buscar(actor.idOrganizacion(), actor.esTenantAdmin(),
                actor.idRolOperativo(), limpiar(filtros.texto()), codigo(filtros.estado()),
                codigo(filtros.estadoOperativo()), limpiar(filtros.zona()),
                PageRequest.of(paginaValida - 1, tamanoValido));
        return new Pagina<>(fichas(page.getContent(), actor.idOrganizacion(), actor.esBroker()),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenAgentes resumen(FiltrosAgente filtros, Actor actor) {
        exigirBrokerOAdmin(actor);
        validarActorOperativo(actor);
        long org = actor.idOrganizacion();
        boolean sinScope = actor.esTenantAdmin();
        long broker = actor.idRolOperativo();
        String texto = limpiar(filtros.texto());
        String estado = codigo(filtros.estado());
        String operativo = codigo(filtros.estadoOperativo());
        String zona = limpiar(filtros.zona());

        long total = agentes.contarFiltrados(org, sinScope, broker, texto, estado, operativo, zona);
        Map<String, Long> administrativos = porEstado(agentes.contarPorEstadoAdministrativo(
                org, sinScope, broker, texto, estado, operativo, zona));
        Map<String, Long> operativos = porEstado(agentes.contarPorEstadoOperativo(
                org, sinScope, broker, texto, estado, operativo, zona));
        return new ResumenAgentes(total,
                administrativos.getOrDefault("A", 0L), administrativos.getOrDefault("I", 0L),
                operativos.getOrDefault("D", 0L), operativos.getOrDefault("O", 0L),
                operativos.getOrDefault("V", 0L), operativos.getOrDefault("S", 0L),
                // Las zonas recorren el ALCANCE completo, no el conjunto filtrado:
                // son las opciones que se ofrecen, igual que en el resto de bandejas.
                agentes.zonasDisponibles(org, sinScope, broker));
    }

    private static Map<String, Long> porEstado(List<ConteoPorEstado> filas) {
        Map<String, Long> resultado = new HashMap<>();
        for (ConteoPorEstado fila : filas) {
            if (fila.getEstado() != null) {
                resultado.merge(fila.getEstado(), fila.getTotal(), Long::sum);
            }
        }
        return resultado;
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Un código de una letra, en mayúscula. Vacío = sin filtro. */
    private static String codigo(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
    }

    /**
     * Ficha individual del agente. Es una operación ADITIVA (la v1 no tenía
     * {@code GET /agentes/{id}}) y responde de una sola vez lo que antes habría
     * que reconstruir combinando páginas de cuatro bandejas distintas —lo que
     * además daría números falsos, porque cada listado pagina—.
     *
     * <p><b>Los cierres y el dinero salen de la atribución histórica de V27</b>,
     * no de la cadena solicitud→agente: un agente que cambió de equipo conserva
     * sus cierres, que es justo lo que una ficha de persona tiene que mostrar.
     *
     * <p>El alcance se comprueba aquí: el BROKER llega a los agentes que supervisa
     * actualmente. TENANT_ADMIN dispone del padrón de gobierno, sin esta ficha.
     * Las consultas de abajo
     * no vuelven a filtrar por rol; si lo hicieran, la ficha mostraría números
     * distintos de los que el propio agente ve en sus bandejas.
     */
    @Override
    @Transactional(readOnly = true)
    public FichaCompletaAgente ficha(long id, Actor actor) {
        // CAT-E01/R19: el padrón de gobierno no concede el expediente comercial.
        if (!actor.esBroker()) {
            throw new AccesoNoAutorizadoException();
        }
        validarActorOperativo(actor);
        long org = actor.idOrganizacion();
        DetalleAgente agente = agentes.buscarFicha(org, id)
                .orElseThrow(() -> new NoEncontradoException("Agente"));

        Optional<SupervisionAgente> supervision = supervisiones.buscarActivaPorAgente(org, id);
        if (supervision.filter(s -> s.getIdRolBroker().equals(actor.idRolOperativo()))
                        .isEmpty()) {
            throw new AccesoNoAutorizadoException();
        }

        CuentaAcceso credencial = usuarios.cuenta(
                org, agente.getRol().getPersona().getId());
        int caps = conteosCaptacion(List.of(id), org).getOrDefault(id, 0);
        int ops = conteosOportunidad(List.of(id), org).getOrDefault(id, 0);

        return new FichaCompletaAgente(
                ficha(agente, credencial, caps, ops,usuarios.estadoLocalDelRol(org,agente.getRol().getPersona().getId(),"AGENTE")),
                supervision.map(this::supervisionVigente).orElse(null),
                conteos(captaciones.contarPorEstadoDeAgente(org, id), EstadoCaptacion::desde),
                conteos(oportunidades.contarPorEstadoDeAgente(org, id), EstadoOportunidad::desde),
                conteos(solicitudes.contarPorEstadoDeAgente(org, id), EstadoSolicitud::desde),
                contratos.contarCierresDeAgente(org, id),
                comisionesDe(org, id),
                ultimosCierres(org, id));
    }

    private SupervisionVigente supervisionVigente(SupervisionAgente supervision) {
        DetalleBroker broker = brokers
                .buscarFicha(supervision.getOrganizacionId(), supervision.getIdRolBroker())
                .orElse(null);
        return new SupervisionVigente(supervision.getIdRolBroker(),
                broker != null ? broker.getRol().getPersona().getNombresORazonSocial() : null,
                broker != null ? broker.getCodigoBroker() : null,
                supervision.getFechaAsignacion(), supervision.getMotivo());
    }

    /**
     * Traduce los pares (código, total) del {@code group by} a códigos con su
     * descripción. Un código que el enum no reconozca se muestra tal cual en
     * vez de romper la ficha: es un dato, no una regla.
     */
    private static List<ConteoEstado> conteos(List<ConteoPorEstado> filas,
                                              java.util.function.Function<String, ?> traductor) {
        return filas.stream()
                .filter(f -> f.getEstado() != null)
                .map(f -> new ConteoEstado(f.getEstado(), descripcion(f.getEstado(), traductor),
                        f.getTotal()))
                .sorted(java.util.Comparator.comparing(ConteoEstado::estado))
                .toList();
    }

    private static String descripcion(String codigo,
                                      java.util.function.Function<String, ?> traductor) {
        try {
            Object estado = traductor.apply(codigo);
            return estado instanceof EstadosDominio.Codigo c ? c.descripcion() : codigo;
        } catch (RuntimeException noReconocido) {
            return codigo;
        }
    }

    /** Hechos y cálculo separados. Los saldos se calculan por comisión antes de agrupar. */
    private ComisionesAgente comisionesDe(long org, long idAgente) {
        var generada=contratos.comisionesGeneradasDeAgente(org,idAgente);
        var asignada=contratos.repartosDeAgente(org,idAgente);
        List<MovimientoComisionPorMoneda> evidencia = contratos.movimientosDeAgente(org, idAgente);
        if(evidencia.stream().anyMatch(f->!Boolean.FALSE.equals(f.getAnomalia())))
            throw new IllegalStateException("El agregado de movimientos no acredita su integridad.");
        return new ComisionesAgente(
                importes(generada,ComisionGeneradaPorMoneda::getMoneda,ComisionGeneradaPorMoneda::getMonto,
                        ComisionGeneradaPorMoneda::getDeterminados,ComisionGeneradaPorMoneda::getPendientes),
                fondos(evidencia,MovimientoComisionPorMoneda::getMontoCobrado),
                importes(generada,ComisionGeneradaPorMoneda::getMoneda,ComisionGeneradaPorMoneda::getSaldoInformativoSobreCalculo,
                        ComisionGeneradaPorMoneda::getSaldoDeterminados,ComisionGeneradaPorMoneda::getSaldoPendientes),
                importes(asignada,RepartoComisionPorMoneda::getMoneda,RepartoComisionPorMoneda::getParteAgente,
                        RepartoComisionPorMoneda::getDeterminados,RepartoComisionPorMoneda::getPendientes),
                fondos(evidencia,MovimientoComisionPorMoneda::getMontoPagadoAgente),
                importes(asignada,RepartoComisionPorMoneda::getMoneda,RepartoComisionPorMoneda::getSaldoPagoAgente,
                        RepartoComisionPorMoneda::getSaldoDeterminados,RepartoComisionPorMoneda::getSaldoPendientes));
    }

    private List<CierreDeAgente> ultimosCierres(long org, long idAgente) {
        return contratos.cierresDeAgente(org, idAgente,
                        PageRequest.of(0, ULTIMOS_CIERRES,
                                Sort.by(Sort.Direction.DESC, "fechaCierre", "id")))
                .getContent().stream()
                .map(AgenteServiceImpl::cierre)
                .toList();
    }

    private static CierreDeAgente cierre(ContratoAlquiler c) {
        SolicitudAlquiler solicitud = c.getSolicitud();
        OportunidadComercial oportunidad = c.getOportunidad();
        DetalleCliente cliente = oportunidad != null ? oportunidad.getCliente() : null;
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        return new CierreDeAgente(c.getId(),
                solicitud != null ? solicitud.getCodigoSolicitud() : null,
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                cliente != null && cliente.getRol() != null
                        && cliente.getRol().getPersona() != null
                        ? cliente.getRol().getPersona().getNombresORazonSocial() : null,
                c.getFechaCierre(), c.estadoActual(),
                c.getRentaContractual(), c.getMoneda());
    }

    private static <T> List<ImportePorMoneda> importes(List<T> filas,
            java.util.function.Function<T,String> moneda,java.util.function.Function<T,BigDecimal> monto,
            java.util.function.Function<T,Long> determinados,java.util.function.Function<T,Long> pendientes) {
        return filas.stream().map(f->{
            Long conocidos=determinados.apply(f),faltantes=pendientes.apply(f);BigDecimal valor=monto.apply(f);
            if(conocidos==null||faltantes==null||conocidos<0||faltantes<0
                    ||(valor==null&&conocidos>0)||(valor!=null&&(valor.signum()<0||moneda.apply(f)==null)))
                throw new IllegalStateException("El agregado económico no conserva su cobertura.");
            return new ImportePorMoneda(moneda.apply(f),valor,conocidos,faltantes);
        }).sorted(java.util.Comparator.comparing(ImportePorMoneda::moneda,
                java.util.Comparator.nullsLast(String::compareTo))).toList();
    }
    private static List<ImportePorMoneda> fondos(List<MovimientoComisionPorMoneda> filas,
            java.util.function.Function<MovimientoComisionPorMoneda,BigDecimal> importe) {
        return filas.stream().map(f->{BigDecimal valor=importe.apply(f);
            if(valor==null||valor.signum()<0||f.getMoneda()==null||f.getMoneda().isBlank())
                throw new IllegalStateException("Los fondos recibidos no conservan importe y moneda.");
            return new ImportePorMoneda(f.getMoneda(),valor,null,null);
        }).sorted(java.util.Comparator.comparing(ImportePorMoneda::moneda)).toList();
    }

    @Override
    @Transactional
    public FichaAgente actualizar(long id, DatosAgente datos, Actor actor) {
        // Fila 18: editar la ficha administrativa de un agente es gobierno. El
        // filtro de supervision que habia aqui protegia al BROKER de tocar
        // agentes ajenos y ya no aplica — el broker no llega a esta operacion,
        // y el TENANT_ADMIN alcanza a todo su tenant por definicion.
        DetalleAgente agente = buscarParaGobierno(id,actor);
        // D-P0-13. Si este cuerpo cambia la ELEGIBILIDAD del agente -su cuenta o
        // su disponibilidad-, esta operacion se serializa con los traspasos que
        // esten mirandola: toma la MISMA fila que `exigirElegible` bloquea.
        //
        // Sin esto, una baja podia colarse entre el «este destino vale» de un
        // traspaso y su escritura, y la propiedad -o el encargo- acababa en
        // manos de alguien que ya no podia recibirlo, con todas las guardas
        // verdes. El candado no decide el orden: decide que haya uno. Si la baja
        // llega primero, el traspaso se rechaza; si llega despues, queda un
        // agente desactivado que lleva cartera, que es la situacion legitima que
        // D-P0-8 describe y que un traspaso explicito tiene que resolver.
        //
        // Solo cuando el cuerpo los trae: cambiar el telefono o la zona no mueve
        // la elegibilidad, y bloquear siempre convertiria una edicion inocua en
        // un punto de espera de los traspasos en curso.
        if (datos != null && !UsuariosInternos.vacio(datos.estadoOperativo())) {
            agentes.bloquearParaGobierno(actor.idOrganizacion(), id)
                    .orElseThrow(() -> new NoEncontradoException("Agente"));
        }

        CuentaAcceso credencial = usuarios.cuenta(actor.idOrganizacion(),
                agente.getRol().getPersona().getId());
        if (datos != null) {
            Persona persona = agente.getRol().getPersona();
            if (!UsuariosInternos.vacio(datos.nombre())) {
                persona.setNombresORazonSocial(datos.nombre());
            }
            if (datos.telefono() != null) {
                persona.setTelefono(datos.telefono());
            }
            if (datos.correoLaboral() != null) {
                persona.setCorreo(datos.correoLaboral());
            }
            if (!UsuariosInternos.vacio(datos.estadoOperativo())) {
                agente.setEstadoOperativo(
                        UsuariosInternos.estadoOperativoO(datos.estadoOperativo(), "D"));
            }
            if (datos.zona() != null) {
                agente.setZonaAsignada(datos.zona());
            }
            personas.save(persona);
            agentes.save(agente);
        }
        return ficha(agente, credencial, null, null,usuarios.estadoLocalDelRol(actor.idOrganizacion(),agente.getRol().getPersona().getId(),"AGENTE"));
    }

    @Override
    @Transactional(readOnly = true)
    public FichaAgente consultarParaGobierno(long id, Actor actor) {
        DetalleAgente agente=buscarParaGobierno(id,actor);
        long persona=agente.getRol().getPersona().getId();
        return ficha(agente,usuarios.cuenta(actor.idOrganizacion(),persona),null,null,
                usuarios.estadoLocalDelRol(actor.idOrganizacion(),persona,"AGENTE"));
    }

    private DetalleAgente buscarParaGobierno(long id,Actor actor) {
        exigirGobierno(actor);
        return agentes.buscarFicha(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Agente"));
    }

    private List<FichaAgente> fichas(List<DetalleAgente> filas, long idOrganizacion,
                                    boolean incluirActividadComercial) {
        List<Long> idsPersona = filas.stream()
                .map(a -> a.getRol().getPersona().getId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, CuentaAcceso> usuariosPorPersona =
                usuarios.cuentasPorPersona(idOrganizacion, idsPersona);
        var estados=usuarios.estadosLocalesDelRol(idOrganizacion,idsPersona,"AGENTE");
        List<Long> idsAgente = filas.stream().map(DetalleAgente::getId).toList();
        Map<Long, Integer> caps = incluirActividadComercial ? conteosCaptacion(idsAgente, idOrganizacion) : Map.of();
        Map<Long, Integer> ops = incluirActividadComercial ? conteosOportunidad(idsAgente, idOrganizacion) : Map.of();
        return filas.stream()
                .map(a -> ficha(a,
                        usuariosPorPersona.get(a.getRol().getPersona().getId()),
                        incluirActividadComercial ? caps.getOrDefault(a.getId(), 0) : null,
                        incluirActividadComercial ? ops.getOrDefault(a.getId(), 0) : null,
                        estados.get(a.getRol().getPersona().getId())))
                .toList();
    }

    private Map<Long, Integer> conteosCaptacion(
            Collection<Long> ids, long idOrganizacion) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapaConteo(
                captaciones.contarEnCarteraPorAgentes(idOrganizacion, ids));
    }

    private Map<Long, Integer> conteosOportunidad(
            Collection<Long> ids, long idOrganizacion) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapaConteo(
                oportunidades.contarActivasPorAgentes(idOrganizacion, ids));
    }

    private static Map<Long, Integer> mapaConteo(List<ConteoPorAgente> filas) {
        Map<Long, Integer> resultado = new HashMap<>();
        for (ConteoPorAgente fila : filas) {
            resultado.put(fila.getIdAgente(), Math.toIntExact(fila.getTotal()));
        }
        return resultado;
    }

    private DetalleBroker validarBrokerActivo(long id, long idOrganizacion) {
        DetalleBroker broker = brokers.buscarFicha(idOrganizacion, id)
                .orElseThrow(() -> new ReglaNegocioException("Broker no encontrado."));
        CuentaAcceso credencial = usuarios.cuenta(
                idOrganizacion, broker.getRol().getPersona().getId());
        if (!"A".equals(credencial.getEstado()) || !"A".equals(usuarios.estadoLocalDelRol(idOrganizacion,broker.getRol().getPersona().getId(),"BROKER"))) {
            throw new ReglaNegocioException("El broker no esta activo.");
        }
        return broker;
    }

    /** Lectura del catalogo (filas 14-16): supervision del broker o del admin. */
    private static void exigirBrokerOAdmin(Actor actor) {
        if (actor == null || (!actor.esBroker() && !actor.esTenantAdmin())) {
            throw new AccesoNoAutorizadoException();
        }
    }

    /**
     * Edición local de agentes: sólo gobierno. Duplica en
     * el service el gate de {@code @PreAuthorize} a proposito — es la regla de
     * negocio, no una comprobacion de transporte, y un caso de uso no deberia
     * depender de que nadie olvide la anotacion.
     */
    private static void exigirGobierno(Actor actor) {
        if (actor == null || !actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
    }

    /**
     * El BROKER tiene que estar activo para operar sobre su equipo. El
     * TENANT_ADMIN no tiene equipo <b>ni {@code detalle_broker}</b>: su
     * {@code idRolOperativo} es el rol de gobierno, y buscarlo entre los
     * brokers fallaria con "Broker no encontrado".
     */
    private void validarActorOperativo(Actor actor) {
        if (actor.esBroker()) {
            validarBrokerActivo(actor.idRolOperativo(), actor.idOrganizacion());
        }
    }

    private static FichaAgente ficha(DetalleAgente agente,
                                     CuentaAcceso credencial,
                                     Integer captacionesActivas,
                                     Integer operacionesActivas,String estadoLocal) {
        Persona persona = agente.getRol().getPersona();
        return new FichaAgente(agente.getId(), agente.getCodigoAgente(),
                persona.getNombresORazonSocial(), persona.getTipoPersona(),
                persona.getTipoDocumento(), persona.getNumeroDocumento(),
                persona.getTelefono(), persona.getCorreo(),
                credencial != null ? credencial.getNombreUsuario() : null,
                agente.getZonaAsignada(), agente.getFechaIngreso(),
                estadoLocal,
                agente.getEstadoOperativo(), captacionesActivas, operacionesActivas);
    }

    private static int tamano(int valor) {
        return Math.max(1, Math.min(100, valor));
    }
}
