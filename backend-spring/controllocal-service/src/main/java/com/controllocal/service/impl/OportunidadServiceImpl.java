package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService;
import com.controllocal.service.OportunidadService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoOportunidad;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actividad.RecibosDeActividad;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.domain.auditoria.EventoDominio;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Propagation;
import static com.controllocal.service.negociacion.ContenidoNegociacion.*;
import java.util.List;
import java.util.Map;

/** CAT-E21/RF040/042/050. El interés real y su episodio son explícitos; las
 * lecturas/historias conservan autoridad actual y las escrituras usan revisión/SID/CAS/recibo.
 */
@Service
public class OportunidadServiceImpl implements OportunidadService {


    private final OportunidadComercialRepository oportunidades;
    private final MotivoNoContinuidadRepository motivos;
    private final CaptacionRepository captaciones;
    private final DetalleAgenteRepository agentes;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final PlanDeConsulta plan;
    /** Quien alcanza esta oportunidad. Una sola definicion para los tres casos de uso. */
    private final AccesoOportunidad acceso;
    /**
     * La mesa de negociacion (WP-A). La ficha INDIVIDUAL publica sus ofertas y
     * las capacidades del actor sobre ella para que la pantalla no tenga que
     * pedirlas aparte ni decidir por rol.
     */
    private final OfertaService ofertas;
    private final ContextosDeActividad contextos;
    private final InteresadosDeDemanda interesados;
    private final EncargoService encargos;
    private final RequerimientoService demandas;
    private final CoincidenciaService coincidencias;
    private final InteraccionService interacciones;
    private final VisitaService visitas;
    private final RecibosDeActividad recibos;
    private final Documentos documentos;
    private final EntityManager entidades;
    private final com.controllocal.persistence.repositorio.PublicacionRepository publicaciones;

    public OportunidadServiceImpl(OportunidadComercialRepository oportunidades,
                                  MotivoNoContinuidadRepository motivos, CaptacionRepository captaciones,
                                  DetalleAgenteRepository agentes,
                                  Alcances alcances, Transiciones transiciones,
                                  PlanDeConsulta plan, AccesoOportunidad acceso,
                                  OfertaService ofertas,ContextosDeActividad contextos,InteresadosDeDemanda interesados,
                                  EncargoService encargos,RequerimientoService demandas,CoincidenciaService coincidencias,
                                  InteraccionService interacciones,VisitaService visitas,RecibosDeActividad recibos,
                                  Documentos documentos,EntityManager entidades,
                                  com.controllocal.persistence.repositorio.PublicacionRepository publicaciones) {
        this.oportunidades = oportunidades;
        this.motivos = motivos;
        this.captaciones = captaciones;
        this.agentes = agentes;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.plan = plan;
        this.acceso = acceso;
        this.ofertas = ofertas;this.contextos=contextos;this.interesados=interesados;this.encargos=encargos;
        this.demandas=demandas;this.coincidencias=coincidencias;this.interacciones=interacciones;this.visitas=visitas;
        this.recibos=recibos;this.documentos=documentos;this.entidades=entidades;
        this.publicaciones=publicaciones;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaOportunidad> listar(int pagina, int tamano, Long idCaptacion, Long idCliente,
                                           String estado, String query, Actor actor) {
        contextos.exigirOperador(actor);
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        int tamanoValido = tamano(tamano);
        String texto = vacioNull(query);
        if (texto != null) {
            return porTexto(alcance, actor, idCaptacion, idCliente, vacioNull(estado), texto,
                    Math.max(1, pagina), tamanoValido);
        }
        Page<OportunidadComercial> page = oportunidades.buscar(
                alcance.idOrganizacion(), alcance.global(), actor.esAgente(), alcance.paramRoles(),
                idCaptacion, idCliente, vacioNull(estado),
                PageRequest.of(Math.max(0, pagina - 1), tamanoValido));
        return new Pagina<>(page.isEmpty()?List.of():oportunidades.buscarFichaPorIds(actor.idOrganizacion(),page.getContent().stream().map(OportunidadComercial::getId).toList()).stream().map(this::ficha).toList(),
                page.getTotalElements());
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (§5 del contrato de
     * listados): una rama por tabla, {@code UNION} en la base, y el mismo
     * conjunto para el conteo y para la pagina. La proyeccion completa se carga
     * despues, solo para los ids ya resueltos.
     */
    private Pagina<FichaOportunidad> porTexto(Alcance alcance, Actor actor, Long idCaptacion,
                                              Long idCliente, String estado, String texto,
                                              int pagina, int tamano) {
        plan.forzarPersonalizado();
        String roles = alcance.paramRolesArray();
        long total = oportunidades.contarPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, idCaptacion, idCliente, estado, texto);
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        List<Long> ids = oportunidades.idsPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, idCaptacion, idCliente, estado, texto,
                tamano, (pagina - 1) * tamano);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        return new Pagina<>(
                oportunidades.buscarFichaPorIds(alcance.idOrganizacion(), ids).stream()
                        .map(this::ficha).toList(),
                total);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenOportunidades resumen(Long idCaptacion, Long idCliente, String query, Actor actor) {
        contextos.exigirOperador(actor);
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return new ResumenOportunidades(0, 0, 0, 0, 0, 0);
        }
        // El estado viaja nulo: el resumen cuenta los cubos, no filtra por uno.
        // Con texto, cuenta sobre el MISMO conjunto de candidatos que pagina la
        // lista; sin texto, sobre el mismo WHERE. Nunca sobre dos criterios.
        String texto = vacioNull(query);
        if (texto != null) {
            plan.forzarPersonalizado();
        }
        List<ConteoPorEstado> conteos = texto != null
                ? oportunidades.contarPorEstadoConTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), idCaptacion, idCliente, null, texto)
                : oportunidades.contarPorEstado(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), idCaptacion, idCliente, null);
        Map<String, Long> porEstado = conteos.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConteoPorEstado::getEstado, ConteoPorEstado::getTotal));
        long abiertas = porEstado.getOrDefault("A", 0L);
        long conSolicitud = porEstado.getOrDefault("S", 0L);
        long noContinuan = porEstado.getOrDefault("N", 0L);
        long exitosas = porEstado.getOrDefault("F", 0L);
        long noFavorables = porEstado.getOrDefault("X", 0L);
        // El total suma TODOS los cubos devueltos, no solo los cinco conocidos:
        // si algun dia apareciera un estado nuevo, el total seguiria cuadrando
        // con la lista en vez de quedarse corto en silencio.
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        return new ResumenOportunidades(total, abiertas, conSolicitud, noContinuan,
                exitosas, noFavorables);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaOportunidad obtener(long id, Actor actor) {
        return fichaIndividual(cargarConAcceso(id, actor), actor);
    }

    @Override @Transactional
    public DatosOportunidad validarApertura(DatosOportunidad bruto,Actor actor){
        var d=normalizar(bruto);contextos.exigirAgente(actor);var e=encargos.consultar(d.idEncargo(),actor);
        if(!Objects.equals(e.responsable(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
        exacta(d.versionEncargo(),e.version(),"Encargo");var interesadoValidado=interesados.validar(d.interesado(),actor);
        if(d.idPublicacionOrigen()!=null){
            var publicacion=publicaciones.findByOrganizacionIdAndId(actor.idOrganizacion(),d.idPublicacionOrigen())
                .orElseThrow(()->new NoEncontradoException("Publicación"));
            if(!Objects.equals(publicacion.getIdEncargo(),d.idEncargo()))throw new ReglaNegocioException("La publicación no corresponde al Encargo de la oportunidad.");
        }
        if(d.idDemanda()!=null)exacta(d.versionDemanda(),demandas.buscar(d.idDemanda(),actor).version(),"Demanda");
        if("COINCIDENCIA".equals(d.origen()))coincidencias.verificarSeleccionParaApertura(
            d.referenciaConsulta(),d.referenciaCoincidencia(),d.idEncargo(),d.idDemanda(),d.versionDemanda(),actor);
        return new DatosOportunidad(d.codigoOportunidad(),d.idEncargo(),d.versionEncargo(),interesadoValidado,d.observaciones(),
            d.idPublicacionOrigen(),d.idDemanda(),d.versionDemanda(),d.origen(),d.referenciaConsulta(),d.referenciaCoincidencia(),d.evidenciaInteres());
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaApertura(DatosOportunidad d,Actor actor){
        if(d==null)throw new ReglaNegocioException("No se conserva la propuesta de apertura.");
        contextos.exigirOperador(actor);encargos.consultar(d.idEncargo(),actor);
        interesados.exigirLectura(d.interesado(),null,actor);
    }
    private DatosOportunidad normalizar(DatosOportunidad d){
        if(d==null)throw new ReglaNegocioException("Aporta la oportunidad y su interesado real.");
        identidad(d.idEncargo(),"el Encargo");version(d.versionEncargo(),"Encargo");
        if(d.interesado()==null)throw new ReglaNegocioException("Identifica un interesado conocido sin inventar un rol Cliente.");
        String origen=texto(d.origen());
        if(origen==null||!Set.of("DIRECTO","COINCIDENCIA","COLABORACION").contains(origen))
            throw new ReglaNegocioException("Declara el origen DIRECTO, COINCIDENCIA o COLABORACION.");
        if((d.idDemanda()==null)!=(d.versionDemanda()==null))throw new ReglaNegocioException("Identifica Demanda y versión juntas.");
        if(d.idDemanda()!=null){identidad(d.idDemanda(),"la Demanda");version(d.versionDemanda(),"Demanda");}
        if(d.evidenciaInteres()==null||(texto(d.evidenciaInteres().descripcion())==null&&texto(d.evidenciaInteres().referencia())==null))
            throw new ReglaNegocioException("Conserva la manifestación conocida de interés.");
        if(d.idPublicacionOrigen()!=null)identidad(d.idPublicacionOrigen(),"la publicación de origen");
        if("COINCIDENCIA".equals(origen)&&(texto(d.referenciaConsulta())==null||texto(d.referenciaCoincidencia())==null))
            throw new ReglaNegocioException("Conserva las referencias de la coincidencia elegida.");
        String codigo=texto(d.codigoOportunidad());
        if(codigo!=null&&codigo.length()>20)throw new ReglaNegocioException("El código de oportunidad admite hasta 20 caracteres.");
        return new DatosOportunidad(codigo,d.idEncargo(),d.versionEncargo(),d.interesado(),texto(d.observaciones()),d.idPublicacionOrigen(),
            d.idDemanda(),d.versionDemanda(),origen,texto(d.referenciaConsulta()),texto(d.referenciaCoincidencia()),d.evidenciaInteres());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaOportunidad registrar(DatosOportunidad bruto,Actor actor,ContextoSesion sesion,String clave,Procedencia p){
        var d=normalizar(bruto);var previo=iniciar(RecibosDeActividad.ABRIR,d,actor,sesion,clave,p);
        if(previo.isPresent())return restaurar(previo.get(),actor);
        validarApertura(d,actor);
        var cap=captaciones.bloquearParaEscritura(actor.idOrganizacion(),d.idEncargo()).orElseThrow(()->new NoEncontradoException("Encargo"));
        entidades.refresh(cap,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(cap.getAgente()==null||cap.getAgente().getId()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        exacta(d.versionEncargo(),cap.getVersion(),"Encargo");
        var i=interesados.resolver(d.interesado(),actor,p);
        if(d.idDemanda()!=null){var dem=demandas.buscar(d.idDemanda(),actor);exacta(d.versionDemanda(),dem.version(),"Demanda");
            if(!Objects.equals(dem.idPersonaInteresada(),i.persona().getId()))throw new ReglaNegocioException("La Demanda corresponde a otro interesado.");}
        if(oportunidades.existeAbiertaDelInteresado(actor.idOrganizacion(),cap.getId(),i.persona().getId()))
            throw new ConflictoException("Ya existe una oportunidad abierta para este interesado y Encargo.");
        oportunidades.bloquearNumeracion(actor.idOrganizacion());
        var o=new OportunidadComercial();o.setOrganizacionId(actor.idOrganizacion());
        o.setCodigoOportunidad(d.codigoOportunidad()==null?"OP-"+oportunidades.siguienteNumero(actor.idOrganizacion()):d.codigoOportunidad());
        o.setCliente(i.cliente());o.setPersonaInteresada(i.persona());o.setInteresadoContextoJson(i.contextoJson());
        o.setCaptacion(cap);o.setAgente(agentes.findById(actor.idRolOperativo()).orElseThrow(AccesoNoAutorizadoException::new));
        o.setObservaciones(d.observaciones());o.setIdPublicacionOrigen(d.idPublicacionOrigen());o.setIdDemanda(d.idDemanda());
        o.setVersionDemanda(d.versionDemanda());o.setOrigen(d.origen());o.setReferenciaConsulta(d.referenciaConsulta());
        o.setReferenciaCoincidencia(d.referenciaCoincidencia());o.setEvidenciaInteresJson(documentos.objetoCanonico(d.evidenciaInteres()));
        o.setProcedenciaJson(documentos.objetoCanonico(p));transiciones.iniciar(o,OportunidadComercial.ABIERTA);
        o.fijarRegistro(contextos.ahora().atOffset(ZoneOffset.UTC));oportunidades.saveAndFlush(o);
        return terminar(RecibosDeActividad.ABRIR,d,o,actor,clave,p,null);
    }
    @Override @Transactional
    public NoContinuidad validarNoContinuidad(NoContinuidad bruto,Actor actor){
        var d=normalizar(bruto);contextos.exigirAgente(actor);var o=acceso.conAcceso(d.idOportunidad(),actor);
        if(o.getAgente()==null||o.getAgente().getId()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        exacta(d.versionEsperada(),o.getVersion(),"Oportunidad");
        if(!o.estaAbierta())throw new ReglaNegocioException("La oportunidad comercial debe estar ABIERTA.");return d;
    }
    private NoContinuidad normalizar(NoContinuidad d){
        if(d==null)throw new ReglaNegocioException("Aporta la decisión de no continuidad.");
        identidad(d.idOportunidad(),"la oportunidad");version(d.versionEsperada(),"Oportunidad");fecha(d.fechaHecho());
        String r=texto(d.razon());if(r==null||!MotivoNoContinuidad.RAZONES.contains(r))throw new ReglaNegocioException("Indica una razón de no continuidad admitida.");
        return new NoContinuidad(d.idOportunidad(),d.versionEsperada(),r,texto(d.observaciones()),d.fechaHecho());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaOportunidad noContinuidad(NoContinuidad bruto,Actor a,ContextoSesion s,String clave,Procedencia p){
        var d=normalizar(bruto);var previo=iniciar(RecibosDeActividad.NO_CONTINUAR,d,a,s,clave,p);
        if(previo.isPresent())return restaurar(previo.get(),a);
        var o=acceso.bloquearActividad(d.idOportunidad(),d.versionEsperada(),a);validarNoContinuidad(d,a);
        if(oportunidades.fichaVersion(a.idOrganizacion(),o.getId(),o.getVersion()).isEmpty())
            oportunidades.historizar(a.idOrganizacion(),o.getId(),o.getVersion(),contextos.ahora(),"ANTECEDENTE_OBSERVADO",
                "Estado observado antes de la modificación; sin atribuir fecha histórica desconocida.",documentos.objetoCanonico(ficha(o)));
        var m=new MotivoNoContinuidad();m.setOrganizacionId(a.idOrganizacion());m.setOportunidad(o);
        m.setRazonPrincipal(d.razon());m.setObservaciones(d.observaciones());m.setAgente(o.getAgente());motivos.save(m);
        String descripcion=MotivoNoContinuidad.rotuloDe(d.razon());
        transiciones.aplicar(o,o.getId(),OportunidadComercial.NO_CONTINUA,a,descripcion,d.fechaHecho());
        o.registrarNoContinuidad(d.fechaHecho(),contextos.ahora().atOffset(ZoneOffset.UTC),descripcion,documentos.objetoCanonico(p));
        oportunidades.saveAndFlush(o);return terminar(RecibosDeActividad.NO_CONTINUAR,d,o,a,clave,p,descripcion);
    }
    @Override @Transactional(readOnly=true)
    public Optional<FichaOportunidad> consultarResultadoApertura(String clave,Actor a){return resultado(RecibosDeActividad.ABRIR,clave,a);}
    @Override @Transactional(readOnly=true)
    public Optional<FichaOportunidad> consultarResultadoNoContinuidad(String clave,Actor a){return resultado(RecibosDeActividad.NO_CONTINUAR,clave,a);}
    private Optional<FichaOportunidad> resultado(String cap,String clave,Actor a){
        contextos.exigirOperador(a);return recibos.consultar(a,clave,cap).map(r->restaurar(r,a));
    }
    private Optional<String> iniciar(String cap,Object d,Actor a,ContextoSesion s,String clave,Procedencia p){
        if(p==null)throw new ReglaNegocioException("Conserva la procedencia del hecho.");contextos.iniciar(a,s);
        return recibos.buscarOriginal(a,clave,cap,d,p);
    }
    private FichaOportunidad terminar(String cap,Object d,OportunidadComercial o,Actor a,String clave,Procedencia p,String motivo){
        String json=documentos.objetoCanonico(ficha(o));Instant ahora=contextos.ahora();
        oportunidades.historizar(a.idOrganizacion(),o.getId(),o.getVersion(),ahora,cap,motivo,json);
        recibos.registrar(a,clave,cap,d,p,OportunidadComercial.ENTIDAD_TIPO,o.getId(),ficha(o));
        return documentos.comoObjeto(json,FichaOportunidad.class);
    }
    private FichaOportunidad restaurar(String json,Actor a){
        var f=documentos.comoObjeto(json,FichaOportunidad.class);var actual=acceso.contextoParaNegociacion(f.id(),a);
        if(!Objects.equals(f.idCaptacion(),actual.idEncargo())||!Objects.equals(f.interesado(),actual.interesado()))
            throw new AccesoNoAutorizadoException();
        return f;
    }
    @Override @Transactional(readOnly=true)
    public ConsultaOportunidad consultar(long id,Seccion seccion,Instant corte,Actor a){
        acceso.contextoParaNegociacion(id,a);if(seccion==null)throw new ReglaNegocioException("Selecciona la sección de la oportunidad.");
        Instant hasta=corte==null?contextos.ahora():corte;
        var base=corte==null?ficha(cargarConAcceso(id,a)):oportunidades.fichaAlCorte(a.idOrganizacion(),id,hasta)
            .map(j->restaurar(j,a)).orElseThrow(()->new NoEncontradoException("Oportunidad en el corte"));
        var historia=seccion==Seccion.HISTORIA?oportunidades.historia(a.idOrganizacion(),id,hasta).stream().map(v->
            new Antecedente(v.getVersion(),v.getRegistradoEn(),v.getOperacion(),v.getMotivo(),restaurar(v.getFicha(),a))).toList():null;
        if(seccion==Seccion.NEGOCIACION)base=conMesa(base,ofertas.listar(id,hasta,a),ofertas.capacidades(id,a));
        return new ConsultaOportunidad(base,seccion,corte,historia,seccion==Seccion.ACTIVIDAD?interacciones.porOportunidad(id,hasta,a):null,
            seccion==Seccion.VISITAS?visitas.porOportunidad(id,hasta,a):null,
            seccion==Seccion.NEGOCIACION?ofertas.consultarAcuerdoAceptado(id,hasta,a).orElse(null):null);
    }
    private static void exacta(long obs,long actual,String campo){if(obs!=actual)throw new ConflictoException("Cambió la versión de "+campo+"; revisa el hecho exacto.");}

    // ------------------------------------------------------------------
    // Alcance por rol: BROKER por CAPTACION (§4), no por agente.
    // ------------------------------------------------------------------

    /**
     * <b>La definicion vive en {@link AccesoOportunidad}</b> desde WP-A, y este
     * metodo la usa. Se conserva el nombre porque es el que la matriz
     * operacion-rol declara como autoridad de estas filas; lo que cambio es que
     * la regla dejo de estar escrita aqui dentro, donde la mesa de negociacion
     * habria tenido que copiarla.
     */
    private OportunidadComercial cargarConAcceso(long id, Actor actor) {
        return acceso.conAcceso(id, actor);
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    /**
     * <b>La ficha INDIVIDUAL, con la mesa dentro</b> (WP-A).
     *
     * <p>Trae la cadena de ofertas y las capacidades del actor sobre ella. Los
     * listados no pasan por aqui, igual que en la ficha del encargo: su pregunta
     * es «que hay», no «que puedo hacer con este», y resolver la mesa por fila
     * serian dos consultas por elemento de la pagina. En el listado los dos
     * campos llegan nulos, y con {@code NON_NULL} no viajan: su ausencia
     * significa «no calculado aqui», no «no hay ofertas» ni «no puedes».
     */
    private FichaOportunidad fichaIndividual(OportunidadComercial o,Actor a){return conMesa(ficha(o),ofertas.listar(o.getId(),a),ofertas.capacidades(o.getId(),a));}
    private FichaOportunidad conMesa(FichaOportunidad b,List<OfertaService.FichaOferta> mesa,OfertaService.CapacidadesDeLaMesa capacidades){
        return new FichaOportunidad(b.id(),b.codigoOportunidad(),b.idCliente(),b.clienteNombre(),b.idCaptacion(),b.codigoCaptacion(),
            b.direccionLocal(),b.distritoLocal(),b.idAgente(),b.agenteNombre(),b.estado(),b.fechaRegistro(),b.motivoCierre(),b.observaciones(),
            b.fechaCierre(),b.fechaActualizacion(),b.idPublicacionOrigen(),mesa,capacidades,b.version(),b.interesado(),b.idDemanda(),b.versionDemanda(),
            b.origen(),b.referenciaConsulta(),b.referenciaCoincidencia(),b.fechaNoContinuidad(),b.procedencia());
    }

    private FichaOportunidad ficha(OportunidadComercial o) {
        DetalleCliente cliente = o.getCliente();
        Captacion captacion = o.getCaptacion();
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        DetalleAgente agente = o.getAgente();
        Long persona=o.getPersonaInteresada()!=null?o.getPersonaInteresada().getId():
            cliente==null||cliente.getRol()==null||cliente.getRol().getPersona()==null?null:cliente.getRol().getPersona().getId();
        return new FichaOportunidad(
                o.getId(), o.getCodigoOportunidad(),
                cliente != null ? cliente.getId() : null,
                o.getPersonaInteresada()!=null?o.getPersonaInteresada().getNombresORazonSocial():nombre(cliente != null ? cliente.getRol() : null),
                captacion != null ? captacion.getId() : null,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                agente != null ? agente.getId() : null,
                nombre(agente != null ? agente.getRol() : null),
                o.estadoActual(),
                Fechas.local(o.getFechaRegistro()),
                o.getMotivoCierre(), o.getObservaciones(),
                Fechas.local(o.getFechaCierre()),
                Fechas.local(o.getFechaActualizacion()),
                o.getIdPublicacionOrigen(),
                // Sin mesa ni capacidades: los produce fichaIndividual.
                null, null,o.getVersion(),persona==null?null:new InteresadoReferencia(persona,cliente==null?null:cliente.getId()),
                o.getIdDemanda(),o.getVersionDemanda(),o.getOrigen(),o.getReferenciaConsulta(),o.getReferenciaCoincidencia(),o.getFechaNoContinuidad(),
                o.getProcedenciaJson()==null?null:documentos.comoObjeto(o.getProcedenciaJson(),Procedencia.class));
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
