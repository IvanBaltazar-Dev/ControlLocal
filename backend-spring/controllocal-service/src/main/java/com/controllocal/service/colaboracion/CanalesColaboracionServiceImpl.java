package com.controllocal.service.colaboracion;

import com.controllocal.domain.auditoria.*;
import com.controllocal.domain.colaboracion.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.controllocal.service.colaboracion.ProyeccionNetwork.recursos;

/** El titular declara; la máquina humana y las dos decisiones conservan sus autoridades separadas. */
@Service
public class CanalesColaboracionServiceImpl implements CanalesColaboracionService {
    private static final String COMANDO="DECLARAR_CANAL_COLABORACION";
    private final SolicitudColaboracionRepository solicitudes;
    private final CanalColaboracionRepository canales;
    private final ProyeccionNetwork network;
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final Documentos documentos;
    private final EventoDominioRepository eventos;
    private final EntityManager entidades;
    private final Clock reloj;
    @org.springframework.beans.factory.annotation.Autowired
    public CanalesColaboracionServiceImpl(SolicitudColaboracionRepository solicitudes,CanalColaboracionRepository canales,
            ProyeccionNetwork network,ContextosService contextos,CuentaAccesoRepository cuentas,
            UsuarioOrganizacionRepository miembros,ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,
            Documentos documentos,EventoDominioRepository eventos,EntityManager entidades) {
        this(solicitudes,canales,network,contextos,cuentas,miembros,registros,comandos,documentos,eventos,entidades,Clock.systemUTC());
    }
    CanalesColaboracionServiceImpl(SolicitudColaboracionRepository solicitudes,CanalColaboracionRepository canales,
            ProyeccionNetwork network,ContextosService contextos,CuentaAccesoRepository cuentas,
            UsuarioOrganizacionRepository miembros,ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,
            Documentos documentos,EventoDominioRepository eventos,EntityManager entidades,Clock reloj) {
        this.solicitudes=solicitudes;this.canales=canales;this.network=network;this.contextos=contextos;this.cuentas=cuentas;
        this.miembros=miembros;this.registros=registros;this.comandos=comandos;this.documentos=documentos;
        this.eventos=eventos;this.entidades=entidades;this.reloj=reloj;
    }
    @Override @Transactional
    public DatosDeclaracion validarDeclaracion(DatosDeclaracion datos,Actor actor) {
        var d=entrada(datos);var s=leer(d.solicitud(),actor);validar(s,d,actor);return d;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Declaracion declarar(DatosDeclaracion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia) {
        iniciar(actor,sesion);var d=entrada(datos);String k=Idempotencia.exigirClave(clave);Objects.requireNonNull(procedencia,"procedencia");
        String h=Idempotencia.huellaExacta(documentos.objetoCanonico(d));
        var identificador=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+k);
        registros.bloquearIdentidad(Long.parseUnsignedLong(identificador.substring(0,16),16));
        Optional<ComandoIdempotente> anterior;
        try{anterior=comandos.buscar(actor,k,COMANDO,h);}catch(ReglaNegocioException e){throw new ConflictoException("La clave corresponde a otra actuación o contenido.");}
        if(anterior.isPresent())return restaurar(anterior.get().getResultado(),actor);
        var s=solicitudes.bloquear(ref(d.solicitud()),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        entidades.refresh(s);validar(s,d,actor);
        var actual=actual(s,d.lado());
        if(actual.isPresent() && Objects.equals(actual.get().getCanalComercial(),d.canalComercial()))
            return guardarRecibo(s,actual.get(),actor,k,h,procedencia,false);
        var c=canales.saveAndFlush(CanalColaboracion.declarar(s,d.lado(),actor.idOrganizacion(),actor.idRolOperativo(),d.versionRecurso(),d.canalComercial(),reloj.instant()));
        s.registrarCanal(d.lado(),c.getId());solicitudes.saveAndFlush(s);
        return guardarRecibo(s,c,actor,k,h,procedencia,true);
    }
    @Override @Transactional
    public Optional<Declaracion> consultarResultado(String clave,Actor actor) {
        Alcances.exigirOperador(actor);
        return comandos.consultarResultado(actor,clave,COMANDO).map(c->restaurar(c.getResultado(),actor));
    }
    @Override @Transactional
    public Optional<Declaracion> consultarDeclaracionPropia(String solicitud,LadoColaboracion lado,Actor actor) {
        if(lado==null)throw new ReglaNegocioException("Debe indicar el lado de la declaración.");
        var s=leer(solicitud,actor);network.exigirPartePropia(recursos(s),lado,ProyeccionNetwork.Accion.CONSULTAR,actor);
        return actual(s,lado).map(c->vista(s,c));
    }
    private void validar(SolicitudColaboracion s,DatosDeclaracion d,Actor actor) {
        if(actor==null || !actor.esAgente())throw new AccesoNoAutorizadoException();
        var a=network.exigirPartePropia(recursos(s),d.lado(),ProyeccionNetwork.Accion.CONSULTAR,actor);
        if(a.lado()!=d.lado() || a.organizacion()!=actor.idOrganizacion() || a.responsable()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        long responsableEsperado=d.lado()!=LadoColaboracion.ENCARGO?s.responsableContraparte():s.getResponsableEncargo();
        if(a.responsable()!=responsableEsperado)throw new ConflictoException("El responsable de esta solicitud cambió; declare el canal en una nueva solicitud.");
        version(d.versionSolicitud(),s.getVersion());version(d.versionAlcance(),s.getVersionAlcance());version(d.versionRecurso(),a.version());
        var alcance=documentos.comoObjeto(s.getAlcance(),AlcanceColaboracion.class);
        if(!alcance.campos().getOrDefault(d.lado(),Set.of()).contains(PoliticaColaboracionBilateral.CANAL_COMERCIAL))
            throw new ReglaNegocioException("Esta solicitud no incluye el canal comercial de ese lado.");
        var c=actual(s,d.lado());
        boolean igual=c.isPresent() && Objects.equals(c.get().getCanalComercial(),d.canalComercial());
        if(!igual && (s.decisionDe(d.lado())!=null || !SolicitudColaboracion.PENDIENTE.equals(s.estadoActual())))
            throw new ConflictoException("El canal aprobado no se modifica: un cambio material requiere una nueva solicitud y decisiones.");
    }
    private Optional<CanalColaboracion> actual(SolicitudColaboracion s,LadoColaboracion lado) {
        Long id=s.canalDe(lado);if(id==null)return Optional.empty();
        long org=lado!=LadoColaboracion.ENCARGO?s.getOrganizacionId():s.getOrganizacionEncargo();
        var c=canales.findByOrganizacionIdAndId(org,id).orElseThrow(()->new IllegalStateException("La declaración del lado no existe."));
        if(!Objects.equals(c.getSolicitud(),s.getId()) || c.getLado()!=lado || c.getVersionAlcance()!=s.getVersionAlcance())
            throw new IllegalStateException("La declaración pertenece a otro alcance.");
        return Optional.of(c);
    }
    private Declaracion guardarRecibo(SolicitudColaboracion s,CanalColaboracion c,Actor actor,String clave,String huella,Procedencia p,boolean nuevo) {
        if(nuevo)eventos.saveAndFlush(p.sellar(EventoDominio.de(actor.idOrganizacion(),"CANAL_COLABORACION_DECLARADO",SolicitudColaboracion.TIPO,s.getId(),actor.idRolOperativo(),p.canal()))
                .con(documentos.objetoCanonico(Map.of("solicitud",s.getReferencia(),"version",s.getVersion(),"lado",c.getLado(),"declaracion",c.getReferencia()))));
        String json=documentos.objetoCanonico(vista(s,c));comandos.registrar(actor,clave,COMANDO,huella,SolicitudColaboracion.TIPO,s.getId(),p,json);
        return documentos.comoObjeto(json,Declaracion.class);
    }
    private Declaracion restaurar(String json,Actor actor) {
        var d=documentos.comoObjeto(json,Declaracion.class);var s=leer(d.solicitud(),actor);
        network.exigirPartePropia(recursos(s),d.lado(),ProyeccionNetwork.Accion.CONSULTAR,actor);return d;
    }
    private Declaracion vista(SolicitudColaboracion s,CanalColaboracion c){return new Declaracion(c.getReferencia().toString(),s.getReferencia().toString(),c.getLado(),s.getVersion(),c.getVersionAlcance(),c.getVersionRecurso(),c.getCanalComercial(),c.getDeclaradaEn());}
    private SolicitudColaboracion leer(String referencia,Actor actor){Alcances.exigirOperador(actor);var s=solicitudes.leer(ref(referencia),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));entidades.refresh(s);return s;}
    private void iniciar(Actor actor,ContextoSesion sesion) {
        if(actor==null || !actor.esAgente() || sesion==null)throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        cuentas.bloquearCambioAcceso(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        try{if(!ContextoSesion.de(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor)).equals(sesion))throw new AccesoNoAutorizadoException();}
        catch(CredencialesInvalidasException e){throw new AccesoNoAutorizadoException();}
    }
    private static DatosDeclaracion entrada(DatosDeclaracion d) {
        if(d==null || d.lado()==null || d.canalComercial()==null || d.canalComercial().isBlank())throw new ReglaNegocioException("Declare el canal comercial explícito para este lado y colaboración.");
        ref(d.solicitud());exigirVersion(d.versionSolicitud());exigirVersion(d.versionAlcance());exigirVersion(d.versionRecurso());
        return new DatosDeclaracion(d.solicitud(),d.lado(),d.versionSolicitud(),d.versionAlcance(),d.versionRecurso(),d.canalComercial().strip());
    }
    private static void exigirVersion(Long v){if(v==null || v<0)throw new ReglaNegocioException("Se requiere la versión observada.");}
    private static void version(long observada,long actual){if(observada!=actual)throw new ConflictoException("La versión observada cambió; revise el caso actual.");}
    private static UUID ref(String r){try{return UUID.fromString(r);}catch(RuntimeException e){throw new NoEncontradoException("Colaboración");}}
}
