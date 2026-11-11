package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.demanda.NormalizadorCriteriosDemanda;
import com.controllocal.service.demanda.NormalizadorCriteriosDemanda.Normalizado;
import com.controllocal.service.demanda.ProyeccionComercialDemanda;
import com.controllocal.service.colaboracion.ReferenciaParteConcedida;
import com.controllocal.domain.colaboracion.LadoColaboracion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** RF028–030: autoridad actual, CAS y snapshots del mismo agregado. */
@Service
public class RequerimientoServiceImpl implements RequerimientoService {
    private static final String ALTA="REGISTRAR_DEMANDA",EDICION="EDITAR_DEMANDA",FIN="RETIRAR_O_FINALIZAR_DEMANDA";
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private static final Set<String> CAMPOS=ProyeccionComercialDemanda.CAMPOS;
    private final RequerimientoClienteRepository requerimientos;
    private final CriterioRequerimientoRepository criterios;
    private final HistoriaRequerimientoRepository historia;
    private final DistritoRepository distritos;
    private final InteresadosDeDemanda interesados;
    private final NormalizadorCriteriosDemanda normalizador;
    private final Alcances alcances;
    private final OrganizacionRepository organizaciones;
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final EventoDominioRepository eventos;
    private final Documentos documentos;
    private final ObjectMapper json;
    private final EntityManager entidades;
    private final ProyeccionDemandaCompartibleRepository compartibles;
    private final Clock reloj;

    @Autowired
    public RequerimientoServiceImpl(RequerimientoClienteRepository requerimientos,CriterioRequerimientoRepository criterios,
            HistoriaRequerimientoRepository historia,DistritoRepository distritos,InteresadosDeDemanda interesados,
            NormalizadorCriteriosDemanda normalizador,Alcances alcances,OrganizacionRepository organizaciones,
            ContextosService contextos,CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,
            ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,EventoDominioRepository eventos,
            Documentos documentos,ObjectMapper json,EntityManager entidades,ProyeccionDemandaCompartibleRepository compartibles) {
        this(requerimientos,criterios,historia,distritos,interesados,normalizador,alcances,organizaciones,contextos,
                cuentas,miembros,registros,comandos,eventos,documentos,json,entidades,compartibles,Clock.systemUTC());
    }
    RequerimientoServiceImpl(RequerimientoClienteRepository requerimientos,CriterioRequerimientoRepository criterios,
            HistoriaRequerimientoRepository historia,DistritoRepository distritos,InteresadosDeDemanda interesados,
            NormalizadorCriteriosDemanda normalizador,Alcances alcances,OrganizacionRepository organizaciones,
            ContextosService contextos,CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,
            ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,EventoDominioRepository eventos,
            Documentos documentos,ObjectMapper json,EntityManager entidades,ProyeccionDemandaCompartibleRepository compartibles,Clock reloj) {
        this.requerimientos=requerimientos;this.criterios=criterios;this.historia=historia;this.distritos=distritos;
        this.interesados=interesados;this.normalizador=normalizador;this.alcances=alcances;this.organizaciones=organizaciones;
        this.contextos=contextos;this.cuentas=cuentas;this.miembros=miembros;this.registros=registros;this.comandos=comandos;
        this.eventos=eventos;this.documentos=documentos;this.json=json;this.entidades=entidades;this.compartibles=compartibles;this.reloj=reloj;
    }
    private record VersionCriterios(List<Normalizado> elementos,Map<String,String> originales) { }

    @Override @Transactional(readOnly=true)
    public List<FichaRequerimiento> listarPorCliente(long idCliente,Actor actor) {
        exigirAgente(actor);
        return requerimientos.delClienteYResponsable(actor.idOrganizacion(),idCliente,actor.idRolOperativo()).stream()
                .map(r->ficha(r,cargarCriterios(r).elementos())).toList();
    }
    @Override @Transactional(readOnly=true)
    public FichaRequerimiento buscar(long id,Actor actor){return consultar(id,null,null,actor);}
    @Override @Transactional(readOnly=true)
    public FichaRequerimiento consultar(long id,Long version,Instant corte,Actor actor) {
        exigirPropia(id,actor);
        if(version!=null&&corte!=null)throw regla("Selecciona versión o corte, no ambos.");
        if(version!=null&&version<0)throw regla("La versión no puede ser negativa.");
        if(version!=null||corte!=null) {
            String original=(version!=null?historia.version(actor.idOrganizacion(),id,version):historia.alCorte(actor.idOrganizacion(),id,corte))
                    .orElseThrow(()->new NoEncontradoException("Versión de Demanda"));
            var resultado=documentos.comoObjeto(original,FichaRequerimiento.class);
            if(resultado.id()!=id)throw new IllegalStateException("El snapshot no corresponde a la demanda autorizada.");
            return resultado;
        }
        var r=cargar(id,actor);return ficha(r,cargarCriterios(r).elementos());
    }
    @Override @Transactional
    public FichaRequerimiento crear(DatosRequerimiento datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        iniciar(actor,sesion);clave=Idempotencia.exigirClave(clave);var proc=Procedencia.oPantalla(origen);
        validarDatos(datos);String huella=huella(datos,proc);bloquearComando(actor,clave);
        var previo=comandos.buscar(actor,clave,ALTA,huella);
        if(previo.isPresent())return restaurarFicha(previo.get().getResultado(),actor);
        validarDatos(datos);
        var interesado=interesados.resolver(referencia(datos),actor,proc);
        var r=new RequerimientoCliente();r.setOrganizacionId(actor.idOrganizacion());r.setIdRolResponsable(actor.idRolOperativo());
        r.setCliente(interesado.cliente());r.setIdPersonaInteresada(interesado.persona().getId());r.setInteresadoContextoJson(interesado.contextoJson());
        aplicar(r,datos);r.setProcedenciaJson(procedencia(proc,clave));
        var normalizados=new VersionCriterios(normalizador.normalizar(datos.criterios(),actor,r.getTipos(),r.operacion().name()),Map.of());
        requerimientos.saveAndFlush(r);
        var resultado=conservarVersion(r,normalizados,actor,proc,reloj.instant());
        evento(ALTA,r,datos,actor,proc);
        comandos.registrar(actor,clave,ALTA,huella,"DEMANDA",r.getId(),proc,escribir(resultado));
        return resultado;
    }
    @Override @Transactional
    public FichaRequerimiento actualizar(long id,long versionEsperada,DatosRequerimiento datos,Actor actor,
            ContextoSesion sesion,String clave,Procedencia origen) {
        iniciar(actor,sesion);clave=Idempotencia.exigirClave(clave);var proc=Procedencia.oPantalla(origen);
        validarDatos(datos);String huella=huella(Map.of("id",id,"versionEsperada",versionEsperada,"datos",datos),proc);
        bloquearComando(actor,clave);var previo=comandos.buscar(actor,clave,EDICION,huella);
        if(previo.isPresent())return restaurarFicha(previo.get().getResultado(),actor);
        var r=bloquearPropia(id,actor);exigirVersion(r,versionEsperada);exigirAbierta(r);validarDatos(datos);
        VersionCriterios anteriores=cargarCriterios(r);
        String operacionAnterior=r.operacion().name();var tiposAnteriores=Set.copyOf(r.getTipos());
        if(datos.interesado()!=null||datos.idCliente()!=null) {
            var interesado=interesados.resolver(referencia(datos),actor,proc);
            r.setCliente(interesado.cliente());r.setIdPersonaInteresada(interesado.persona().getId());r.setInteresadoContextoJson(interesado.contextoJson());
        }
        aplicar(r,datos);r.setProcedenciaJson(procedencia(proc,clave));
        if(datos.criterios()==null&&(!operacionAnterior.equals(r.operacion().name())||!tiposAnteriores.equals(r.getTipos())))
            normalizador.validarContextoConservado(anteriores.elementos(),actor,r.getTipos(),r.operacion().name());
        var nuevos=datos.criterios()==null?anteriores:new VersionCriterios(normalizador.normalizar(datos.criterios(),actor,r.getTipos(),r.operacion().name()),Map.of());
        requerimientos.saveAndFlush(r);comprobarIncremento(r,versionEsperada);
        var resultado=conservarVersion(r,nuevos,actor,proc,reloj.instant());evento(EDICION,r,datos,actor,proc);
        comandos.registrar(actor,clave,EDICION,huella,"DEMANDA",r.getId(),proc,escribir(resultado));return resultado;
    }
    @Override @Transactional(readOnly=true)
    public FinDemanda validarFin(FinDemanda fin,Actor actor) {
        exigirAgente(actor);var normalizado=normalizarFin(fin);var c=contextoPropio(normalizado.idDemanda(),actor);
        validarFinActual(normalizado,c.getEstado(),c.getVersion());return normalizado;
    }
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ResultadoFinDemanda retirarOFinalizar(FinDemanda fin,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        iniciar(actor,sesion);clave=Idempotencia.exigirClave(clave);var proc=Procedencia.oPantalla(origen);
        fin=normalizarFin(fin);
        String huella=huella(fin,proc);bloquearComando(actor,clave);
        var previo=comandos.buscar(actor,clave,FIN,huella);
        if(previo.isPresent()) {
            var original=documentos.comoObjeto(previo.get().getResultado(),ResultadoFinDemanda.class);
            exigirPropia(original.idDemanda(),actor);return original;
        }
        var r=bloquearPropia(fin.idDemanda(),actor);validarFinActual(fin,r.getEstado(),r.getVersion());
        var anteriores=cargarCriterios(r);Instant ahora=reloj.instant().truncatedTo(ChronoUnit.MICROS);
        r.finalizar(fin.tipo()==TipoFinDemanda.RETIRADA?RequerimientoCliente.RETIRADA:RequerimientoCliente.FINALIZADA,
                fin.fechaDelHecho(),fin.motivo().trim(),OffsetDateTime.ofInstant(ahora,ZoneOffset.UTC));
        r.setProcedenciaJson(procedencia(proc,clave));requerimientos.saveAndFlush(r);comprobarIncremento(r,fin.versionEsperada());
        conservarVersion(r,anteriores,actor,proc,ahora);var evento=evento(FIN,r,fin,actor,proc);
        var resultado=new ResultadoFinDemanda(r.getId(),r.getVersion(),r.getEstado(),fin.tipo(),r.getFinFecha(),r.getFinMotivo(),ahora,evento.getId());
        comandos.registrar(actor,clave,FIN,huella,"DEMANDA",r.getId(),proc,escribir(resultado));return resultado;
    }
    @Override @Transactional(readOnly=true)
    public Optional<ResultadoFinDemanda> consultarResultadoFin(String clave,Actor actor) {
        exigirAgente(actor);return comandos.consultarResultado(actor,clave,FIN).map(c->{
            var r=documentos.comoObjeto(c.getResultado(),ResultadoFinDemanda.class);exigirPropia(r.idDemanda(),actor);return r;});
    }
    @Override @Transactional(readOnly=true)
    public Optional<FichaRequerimiento> consultarResultado(String clave,Actor actor) {
        exigirAgente(actor);var registro=comandos.consultarResultado(actor,clave,ALTA);
        if(registro.isEmpty())registro=comandos.consultarResultado(actor,clave,EDICION);
        return registro.map(c->restaurarFicha(c.getResultado(),actor));
    }
    @Override @Transactional(readOnly=true)
    public ContratoMatching.Consulta paraMatching(long id,long version,ContratoMatching.Modalidad modalidad,String versionRegla,Instant corteCore,Actor actor) {
        var contexto=exigirContextoComercial(id,actor);
        if(contexto.getVersion()!=version)throw new ConflictoException("Cambió la versión de la demanda para matching.");
        var r=cargar(id,actor);exigirVersion(r,version);exigirAbierta(r);
        Objects.requireNonNull(corteCore,"El corte lo proporciona Core.");
        return ProyeccionMatchingDemanda.proyectar(r,cargarCriterios(r).elementos(),modalidad,versionRegla,corteCore);
    }
    @Override @Transactional(readOnly=true)
    public List<ReferenciaDemanda> demandasParaMatching(Actor actor,Instant corteCore) {
        exigirOperadorActual(actor);Objects.requireNonNull(corteCore,"El corte lo proporciona Core.");
        List<Long> responsables=actor.esAgente()?List.of(actor.idRolOperativo()):alcances.supervisados(actor.idOrganizacion(),actor.idRolOperativo());
        if(responsables.isEmpty())return List.of();
        return requerimientos.referenciasVigentes(actor.idOrganizacion(),responsables,corteCore.atZone(LIMA).toLocalDate()).stream()
                .map(c->new ReferenciaDemanda(c.getId(),c.getVersion(),c.getResponsable(),c.getEstado(),c.getVigenteDesde(),c.getVigenteHasta())).toList();
    }
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ContextoDisponibilidadDemanda contextoDisponibilidad(long id,Actor actor,boolean confirmar) {
        if(confirmar) {
            var r=bloquearPropia(id,actor);
            return new ContextoDisponibilidadDemanda(id,r.getVersion(),r.operacion(),r.getIdRolResponsable(),r.getEstado(),actor.idOrganizacion(),r.getVigenteDesde(),r.getVigenteHasta());
        }
        var c=exigirContextoComercial(id,actor);
        return new ContextoDisponibilidadDemanda(id,c.getVersion(),OperacionInmobiliaria.desde(c.getOperacion()),c.getResponsable(),c.getEstado(),actor.idOrganizacion(),c.getVigenteDesde(),c.getVigenteHasta());
    }
    @Override @Transactional(readOnly=true)
    public AutoridadLadoDemanda autoridadParaColaboracion(long id,long version,AccionColaboracion accion,Actor actor) {
        if(accion==null)throw regla("Indica la actuación sobre el lado propio.");
        exigirOperadorActual(actor);
        var c=compartibles.metadatosConcedidos(actor.idOrganizacion(),id,Set.of(),LocalDate.now(reloj.withZone(LIMA)))
                .orElseThrow(()->new NoEncontradoException("Demanda"));
        if(!alcances.alcanza(actor,c.responsable()))throw new AccesoNoAutorizadoException();
        if(c.version()!=version)throw new ConflictoException("Cambió el lado Demanda de la colaboración.");
        boolean propio=actor.esAgente()&&c.responsable()==actor.idRolOperativo();
        if(accion==AccionColaboracion.SOLICITAR&&!propio)throw new AccesoNoAutorizadoException();
        if(accion==AccionColaboracion.DECIDIR||accion==AccionColaboracion.REVOCAR) {
            boolean independiente=propio&&organizaciones.findById(actor.idOrganizacion()).map(o->"INDIVIDUAL".equals(o.getModalidad())).orElse(false);
            if(!actor.esBroker()&&!independiente)throw new AccesoNoAutorizadoException();
        }
        return new AutoridadLadoDemanda(id,actor.idOrganizacion(),c.responsable(),c.version(),c.estado(),c.desde(),c.hasta(),CAMPOS);
    }
    @Override @Transactional(readOnly=true)
    public Map<String,Object> proyectarCamposColaboracion(long id,long version,Set<String> campos,Set<String> selectoresCriterioAprobados,Actor actor) {
        autoridadParaColaboracion(id,version,AccionColaboracion.DECIDIR,actor);
        if(campos==null||!CAMPOS.containsAll(campos))throw regla("El alcance contiene campos ajenos a la proyección comercial de Demanda.");
        if(campos.contains("criterios"))ProyeccionComercialDemanda.exigirSelectores(selectoresCriterioAprobados);
        var actual=subset(actor.idOrganizacion(),id,campos,selectoresCriterioAprobados,version,null);
        autoridadParaColaboracion(id,version,AccionColaboracion.DECIDIR,actor);
        return actual.campos();
    }
    @Override @Transactional(readOnly=true)
    public VerificacionCamposDemanda verificarCamposColaboracion(ReferenciaParteConcedida prueba,Actor receptorReal) {
        exigirOperadorActual(receptorReal);
        if(prueba==null||prueba.lado()!=LadoColaboracion.DEMANDA||prueba.organizacion()<=0||prueba.idRecurso()<=0
                ||prueba.responsable()<=0||!Set.of("operacion","tiposAdmitidos","zona","criterios").containsAll(prueba.campos()))
            throw new AccesoNoAutorizadoException();
        if(prueba.campos().contains("criterios"))ProyeccionComercialDemanda.exigirSelectores(prueba.clavesFisicas());
        var actual=subset(prueba.organizacion(),prueba.idRecurso(),prueba.campos(),prueba.clavesFisicas(),null,prueba);
        var b=actual.base();
        exigirOperadorActual(receptorReal);
        return new VerificacionCamposDemanda(b.id(),b.version(),b.responsable(),b.estado(),b.desde(),b.hasta(),
                Idempotencia.huellaExacta(documentos.objetoCanonico(actual.campos())));
    }
    private record Subset(ProyeccionDemandaCompartibleRepository.Base base,Map<String,Object> campos) { }
    private Subset subset(long org,long id,Set<String> campos,Set<String> selectores,Long versionEsperada,ReferenciaParteConcedida prueba) {
        var hoy=LocalDate.now(reloj.withZone(LIMA));
        for(int intento=0;intento<2;intento++) {
            var base=compartibles.metadatosConcedidos(org,id,campos,hoy).orElseThrow(()->new NoEncontradoException("Demanda"));
            if(versionEsperada!=null&&base.version()!=versionEsperada)throw new ConflictoException("Cambió la versión del lado Demanda.");
            if(prueba!=null)exigirRecursoConcedido(base,prueba,hoy);
            List<ContratoMatching.Criterio> seleccion=campos.contains("criterios")
                    ?compartibles.criterios(org,id,base.version(),selectores).stream().map(s->documentos.comoObjeto(s,ContratoMatching.Criterio.class)).toList():List.of();
            var despues=compartibles.metadatosConcedidos(org,id,Set.of(),hoy).orElseThrow(()->new NoEncontradoException("Demanda"));
            if(prueba!=null)exigirRecursoConcedido(despues,prueba,hoy);
            if(base.version()!=despues.version()||base.responsable()!=despues.responsable())continue;
            var proyeccion=new ProyeccionComercialDemanda.Base(base.version(),OperacionInmobiliaria.desde(base.operacion()).name(),
                    base.tipos(),base.zonas(),base.finalidad(),base.estado(),base.desde(),base.hasta());
            return new Subset(base,ProyeccionComercialDemanda.campos(proyeccion,campos,seleccion,selectores));
        }
        throw new ConflictoException("El subset de Demanda cambió durante la consulta; vuelve a verificarlo.");
    }
    private static void exigirRecursoConcedido(ProyeccionDemandaCompartibleRepository.Base b,ReferenciaParteConcedida prueba,LocalDate hoy) {
        if(b.responsable()!=prueba.responsable()||b.version()<prueba.versionRecursoAprobada()
                ||!RequerimientoCliente.ABIERTA.equals(b.estado())||b.desde()!=null&&hoy.isBefore(b.desde())
                ||b.hasta()!=null&&hoy.isAfter(b.hasta()))throw new AccesoNoAutorizadoException();
    }
    private RequerimientoClienteRepository.ContextoDemanda exigirContextoComercial(long id,Actor actor) {
        exigirOperadorActual(actor);var c=requerimientos.contexto(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Demanda"));
        if(c.getResponsable()==null||!alcances.alcanza(actor,c.getResponsable()))throw new AccesoNoAutorizadoException();return c;
    }
    private RequerimientoClienteRepository.ContextoDemanda exigirPropia(long id,Actor actor){exigirAgente(actor);return contextoPropio(id,actor);}
    private RequerimientoClienteRepository.ContextoDemanda contextoPropio(long id,Actor actor){var c=requerimientos.contexto(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Demanda"));if(!Objects.equals(c.getResponsable(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();return c;}
    private RequerimientoCliente cargar(long id,Actor actor){return requerimientos.buscarFicha(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Demanda"));}
    private RequerimientoCliente bloquearPropia(long id,Actor actor) {
        exigirPropia(id,actor);var r=requerimientos.bloquear(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Demanda"));
        entidades.refresh(r,LockModeType.PESSIMISTIC_WRITE);
        if(!Objects.equals(r.getIdRolResponsable(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();return r;
    }
    private void exigirOperadorActual(Actor actor) {
        if(actor==null||(!actor.esAgente()&&!actor.esBroker())||!requerimientos.contextoOperativoActual(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),LocalDate.now(reloj.withZone(LIMA))))throw new AccesoNoAutorizadoException();
    }
    private void exigirAgente(Actor actor){if(actor==null||!actor.esAgente())throw new AccesoNoAutorizadoException();exigirOperadorActual(actor);}
    private void iniciar(Actor actor,ContextoSesion sesion) {
        if(actor==null||!actor.esAgente()||sesion==null)throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(ids.size()!=1)throw new AccesoNoAutorizadoException();cuentas.bloquearCambioAcceso(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        try {var vigente=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);if(!ContextoSesion.de(vigente).equals(sesion))throw new AccesoNoAutorizadoException();}
        catch(CredencialesInvalidasException e){throw new AccesoNoAutorizadoException();}
    }
    private void bloquearComando(Actor actor,String clave){String h=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+clave);registros.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));}
    private String huella(Object datos,Procedencia proc){return Idempotencia.huellaExacta(escribir(Map.of("datos",datos,"procedencia",proc)));}
    private String procedencia(Procedencia proc,String clave){return documentos.objetoCanonico(Map.of("procedencia",proc,"clave",clave));}
    private static void exigirVersion(RequerimientoCliente r,long esperada){if(esperada<0||r.getVersion()!=esperada)throw new ConflictoException("Cambió la demanda; revisa la versión observada.");}
    private static void comprobarIncremento(RequerimientoCliente r,long anterior){if(r.getVersion()!=anterior+1)throw new IllegalStateException("La edición de Demanda debe incrementar una sola versión.");}
    private static void exigirAbierta(RequerimientoCliente r){if(!RequerimientoCliente.ABIERTA.equals(r.getEstado()))throw regla("Una demanda retirada o finalizada no se reabre con edición ordinaria.");}
    private FinDemanda normalizarFin(FinDemanda fin) {
        if(fin==null||fin.idDemanda()<=0||fin.versionEsperada()<0||fin.tipo()==null||fin.fechaDelHecho()==null
                ||fin.motivo()==null||fin.motivo().isBlank())throw regla("El hecho de fin requiere demanda, versión, tipo, fecha y motivo explícitos.");
        if(fin.fechaDelHecho().isAfter(LocalDate.now(reloj.withZone(LIMA))))throw regla("La fecha del hecho no puede describir como ocurrido un fin futuro.");
        return new FinDemanda(fin.idDemanda(),fin.versionEsperada(),fin.tipo(),fin.fechaDelHecho(),fin.motivo().trim());
    }
    private static void validarFinActual(FinDemanda fin,String estado,long version) {
        if(fin.versionEsperada()!=version)throw new ConflictoException("Cambió la versión de Demanda; revisa antes del fin.");
        if(!RequerimientoCliente.ABIERTA.equals(estado))throw regla("La demanda ya tiene un fin conservado.");
    }
    private static void validarDatos(DatosRequerimiento d){if(d==null)throw regla("Faltan los datos declarados de la demanda.");if(d.estado()!=null&&!d.estado().equals(RequerimientoCliente.ABIERTA))throw regla("El fin requiere su actuación exacta; no se edita el estado genéricamente.");if(d.frenteMinimo()!=null)throw regla("Declara el frente como criterio con su obligatoriedad, unidad y contexto; no se le atribuye una preferencia implícita.");}
    private static InteresadosDeDemanda.Referencia referencia(DatosRequerimiento d) {
        if(d.interesado()!=null){if(d.idCliente()!=null)throw regla("No declares dos referencias del interesado.");return d.interesado();}
        return new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.CLIENTE,d.idCliente(),null,null,null);
    }
    private void aplicar(RequerimientoCliente r,DatosRequerimiento d) {
        if(d.operacion()!=null) {try{r.setOperacion(OperacionInmobiliaria.desde(d.operacion()));}catch(IllegalArgumentException e){throw regla(e.getMessage());}}
        if(r.operacion()==null)throw regla("Declara VENTA o ALQUILER; no se infiere por importe ni cliente.");
        if(d.tiposInmueble()!=null){var t=new LinkedHashSet<String>();for(String v:d.tiposInmueble())t.add(TipoDemandado.codigo(v).orElseThrow(()->regla("Tipo de inmueble no reconocido.")));r.setTipos(t);}
        else if(d.tipoInmueble()!=null)r.setTipos(new LinkedHashSet<>(List.of(TipoDemandado.codigo(d.tipoInmueble()).orElseThrow(()->regla("Tipo de inmueble no reconocido.")))));
        if(d.presupuestoMin()!=null||d.rentaMin()!=null)r.setPresupuestoMin(importe(d.presupuestoMin(),d.rentaMin(),r.operacion()));
        if(d.presupuestoMax()!=null||d.rentaMax()!=null)r.setPresupuestoMax(importe(d.presupuestoMax(),d.rentaMax(),r.operacion()));
        if(d.moneda()!=null)r.setMoneda(d.moneda().isBlank()?null:CondicionesEconomicas.moneda(d.moneda(),"del presupuesto"));
        if(d.metrajeMin()!=null)r.setMetrajeMin(d.metrajeMin());if(d.metrajeMax()!=null)r.setMetrajeMax(d.metrajeMax());
        if(d.vigenteDesde()!=null)r.setVigenteDesde(d.vigenteDesde());if(d.vigenteHasta()!=null)r.setVigenteHasta(d.vigenteHasta());
        if(d.finalidad()!=null)r.setFinalidad(limpiar(d.finalidad()));if(d.observaciones()!=null)r.setObservaciones(limpiar(d.observaciones()));
        if(d.rubro()!=null)r.setRubro(limpiar(d.rubro()));if(d.conceptoPresupuesto()!=null)r.setConceptoPresupuesto(limpiar(d.conceptoPresupuesto()));
        if(d.periodoPresupuesto()!=null)r.setPeriodoPresupuesto(limpiar(d.periodoPresupuesto()));
        if(d.camposARetirar()!=null)for(String campo:d.camposARetirar())switch(campo) {
            case "metrajeMin"->r.setMetrajeMin(null);case "metrajeMax"->r.setMetrajeMax(null);
            case "presupuestoMin"->r.setPresupuestoMin(null);case "presupuestoMax"->r.setPresupuestoMax(null);
            case "moneda"->r.setMoneda(null);case "vigenteDesde"->r.setVigenteDesde(null);case "vigenteHasta"->r.setVigenteHasta(null);
            case "finalidad"->r.setFinalidad(null);case "observaciones"->r.setObservaciones(null);case "rubro"->r.setRubro(null);
            case "conceptoPresupuesto"->r.setConceptoPresupuesto(null);case "periodoPresupuesto"->r.setPeriodoPresupuesto(null);
            default->throw regla("No se retira ese campo mediante la edición de Demanda: "+campo);
        }
        if(d.distritos()!=null) {
            var lista=new ArrayList<Distrito>();var disponibles=distritos.findByActivoTrueOrderByNombre();
            for(String nombre:d.distritos()){var distrito=disponibles.stream().filter(x->x.getNombre().equalsIgnoreCase(nombre)).findFirst().orElseThrow(()->regla("Distrito no localizado en el catálogo: "+nombre));if(lista.stream().noneMatch(x->x.getId().equals(distrito.getId())))lista.add(distrito);}r.setDistritos(lista);
        }
        orden(r.getPresupuestoMin(),r.getPresupuestoMax());orden(r.getMetrajeMin(),r.getMetrajeMax());
        if(r.getVigenteDesde()!=null&&r.getVigenteHasta()!=null&&r.getVigenteHasta().isBefore(r.getVigenteDesde()))throw regla("La vigencia declarada está invertida.");
    }
    private static BigDecimal importe(BigDecimal canonico,BigDecimal renta,OperacionInmobiliaria operacion){if(renta!=null&&operacion!=OperacionInmobiliaria.ALQUILER)throw regla("Una renta no declara precio de venta.");if(canonico!=null&&renta!=null&&canonico.compareTo(renta)!=0)throw regla("Hay dos importes distintos para el mismo presupuesto.");return canonico!=null?canonico:renta;}
    private static void orden(BigDecimal a,BigDecimal b){if(a!=null&&b!=null&&a.compareTo(b)>0)throw regla("El límite mínimo supera al máximo.");}
    private static String limpiar(String s){return s==null||s.isBlank()?null:s.trim();}
    private VersionCriterios cargarCriterios(RequerimientoCliente r) {
        var lista=new ArrayList<Normalizado>();var originales=new LinkedHashMap<String,String>();
        for(var c:criterios.deVersion(r.getOrganizacionId(),r.getId(),r.getVersion())) {
            if(c.getCriterioJson()==null)throw regla("El criterio técnico anterior no acredita metadatos; requiere declaración explícita.");
            var normal=documentos.comoObjeto(c.getCriterioJson(),ContratoMatching.Criterio.class);
            lista.add(new Normalizado(normal,c.getRotulo(),c.getUnidad(),c.getTipoDatoObservado()));originales.put(normal.clave(),c.getCriterioJson());
        }
        return new VersionCriterios(List.copyOf(lista),Map.copyOf(originales));
    }
    private FichaRequerimiento conservarVersion(RequerimientoCliente r,VersionCriterios cs,Actor actor,Procedencia proc,Instant registrado) {
        var ficha=ficha(r,cs.elementos());historia.anexar(actor.idOrganizacion(),r.getId(),r.getVersion(),actor.idRolOperativo(),registrado,escribir(ficha),r.getProcedenciaJson());
        for(var n:cs.elementos()) {var c=new CriterioRequerimiento();c.setOrganizacionId(actor.idOrganizacion());c.setIdRequerimiento(r.getId());c.setVersionDemanda(r.getVersion());c.setClave(n.criterio().clave());c.setCriterioJson(cs.originales().getOrDefault(c.getClave(),escribir(n.criterio())));c.setRotulo(n.rotulo());c.setUnidad(n.unidad());c.setTipoDatoObservado(n.tipoDato());criterios.save(c);}criterios.flush();return ficha;
    }
    private EventoDominio evento(String tipo,RequerimientoCliente r,Object datos,Actor actor,Procedencia proc){var carga=new LinkedHashMap<String,Object>();carga.put("idDemanda",r.getId());carga.put("version",r.getVersion());carga.put("datosOriginales",datos);return eventos.saveAndFlush(proc.sellar(EventoDominio.de(actor.idOrganizacion(),tipo,"DEMANDA",r.getId(),actor.idRolOperativo(),proc.canal())).con(documentos.objetoCanonico(carga)));}
    private FichaRequerimiento restaurarFicha(String original,Actor actor){var f=documentos.comoObjeto(original,FichaRequerimiento.class);exigirPropia(f.id(),actor);return f;}
    private FichaRequerimiento ficha(RequerimientoCliente r,List<Normalizado> cs) {
        var tipos=List.copyOf(r.getTipos());boolean alquiler=r.operacion()==OperacionInmobiliaria.ALQUILER;
        return new FichaRequerimiento(r.getId(),r.getCliente()==null?null:r.getCliente().getId(),r.getRubro(),tipos.size()==1?TipoDemandado.nombreLegado(tipos.getFirst()):null,
            alquiler?r.getPresupuestoMin():null,alquiler?r.getPresupuestoMax():null,null,r.operacion().name(),tipos,r.getPresupuestoMin(),r.getPresupuestoMax(),r.getMoneda(),r.getMetrajeMin(),r.getMetrajeMax(),r.getVigenteHasta(),Fechas.local(r.getFechaRevalidacion()),r.getEstado(),r.getObservaciones(),r.getDistritos().stream().map(Distrito::getNombre).toList(),cs.stream().map(this::fichaCriterio).toList(),Fechas.local(r.getFechaCreacion()),Fechas.local(r.getFechaActualizacion()),r.getVersion(),r.getIdRolResponsable(),r.getIdPersonaInteresada(),r.getFinalidad(),r.getVigenteDesde(),r.getConceptoPresupuesto(),r.getPeriodoPresupuesto(),documentos.comoMapa(r.getInteresadoContextoJson()),r.getFinTipo()==null?null:new FinDeclarado(r.getFinTipo(),r.getFinFecha(),r.getFinMotivo(),r.getFinRegistradoEn().toInstant()));
    }
    private CriterioFicha fichaCriterio(Normalizado n){var c=n.criterio();var a=c.alternativas().size()==1?c.alternativas().getFirst():null;var v=a==null?null:a.valor();var h=a==null?null:a.hasta();return new CriterioFicha(c.clave(),n.rotulo(),n.unidad(),n.tipoDato(),a==null?null:a.operador().name(),c.exigencia()==ContratoMatching.Exigencia.OBLIGATORIO?"INDISPENSABLE":"DESEABLE",null,v==null?null:v.texto(),v==null?null:v.numero(),h==null?null:h.numero(),v==null?null:v.booleano(),v==null?null:v.fecha(),h==null?null:h.fecha(),v==null?null:v.valores(),v==null?null:v.moneda(),c);}
    private String escribir(Object valor){try{return json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(valor);}catch(Exception e){throw new IllegalStateException("No se pudo conservar Demanda.",e);}}
    private static ReglaNegocioException regla(String mensaje){return new ReglaNegocioException(mensaje);}
}
