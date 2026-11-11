package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.SesionAcceso;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.soporte.EventosSeguridad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** CAT-E01/R19 y RF-001: una cuenta no concede un contexto; la sesión lo elige y revalida. */
@Service
public class ContextosServiceImpl implements ContextosService {
    private static final SecureRandom ALEATORIO=new SecureRandom();
    private final CuentaAccesoRepository cuentas;
    private final FactorAutenticacionRepository factores;
    private final SesionAccesoRepository sesiones;
    private final UsuarioOrganizacionRepository membresias;
    private final PersonaRolRepository roles;
    private final EventosSeguridad auditoria;
    private final MfaService mfa;
    private final Clock reloj;

    @org.springframework.beans.factory.annotation.Autowired
    public ContextosServiceImpl(CuentaAccesoRepository cuentas,SesionAccesoRepository sesiones,
            UsuarioOrganizacionRepository membresias,PersonaRolRepository roles,EventosSeguridad auditoria,MfaService mfa,FactorAutenticacionRepository factores) {
        this(cuentas,sesiones,membresias,roles,auditoria,mfa,factores,Clock.systemUTC());
    }
    ContextosServiceImpl(CuentaAccesoRepository cuentas,SesionAccesoRepository sesiones,
            UsuarioOrganizacionRepository membresias,PersonaRolRepository roles,EventosSeguridad auditoria,MfaService mfa,FactorAutenticacionRepository factores,Clock reloj) {
        this.cuentas=cuentas;this.factores=factores;this.sesiones=sesiones;
        this.membresias=membresias;this.roles=roles;this.auditoria=auditoria;this.mfa=mfa;this.reloj=reloj;
    }

    @Override @Transactional
    public AccesoIniciado iniciarAcceso(String identificador,char[] contrasena,boolean permitirDesafio) {
        var identidad=autenticar(identificador,contrasena);
        if(mfa.exigeSegundoFactor(identidad.idCuenta())) {
            if(!permitirDesafio) throw rechazo();
            return new AccesoIniciado(null,mfa.emitirDesafio(identidad.idCuenta()));
        }
        return new AccesoIniciado(abrirSesion(identidad),null);
    }

    @Override @Transactional
    public SesionAccesoDTO completarSegundoFactor(String desafio,String codigo,String ip,String agenteUsuario) {
        var verificada=mfa.verificarDesafio(desafio,codigo,ip,agenteUsuario);
        return abrirSesion(identidadDeCuenta(verificada.idCuenta()));
    }

    private IdentidadAutenticada autenticar(String identificador,char[] contrasena) {
        if (identificador==null || identificador.isBlank() || contrasena==null || contrasena.length==0)
            throw rechazo();
        var encontrada=cuentas.buscarPorCorreo(identificador.trim()).orElseThrow(ContextosServiceImpl::rechazo);
        long id=encontrada.getId();cuentas.bloquearCambioAcceso(id).orElseThrow(ContextosServiceImpl::rechazo);
        var actual=cuentas.accesoActual(id).orElseThrow(ContextosServiceImpl::rechazo);
        if(actual.correoConfirmadoEn()==null||!actual.correoPropio().equalsIgnoreCase(identificador.trim())
                ||!PasswordHasher.verificar(contrasena,actual.contrasenaHash()))throw rechazo();
        return identidad(cuentaActiva(id));
    }

    private IdentidadAutenticada identidadDeCuenta(long cuenta) { return identidad(cuentaActiva(cuenta)); }

    private SesionAccesoDTO abrirSesion(IdentidadAutenticada identidad) {
        if (identidad==null) throw rechazo();
        cuentas.bloquearCambioAcceso(identidad.idCuenta()).orElseThrow(ContextosServiceImpl::rechazo);
        var cuenta=cuentaActiva(identidad.idCuenta());
        String ref=nuevaReferencia(); Instant ahora=reloj.instant();
        SesionAcceso s=new SesionAcceso();s.setIdCuenta(cuenta.idCuenta());s.setReferenciaHash(huella(ref));
        s.setSemillaContextos(Idempotencia.huellaExacta(nuevaReferencia()));s.setRevision(1);
        s.setCreadaEn(ahora.truncatedTo(ChronoUnit.MICROS));s.setEmitidaEn(segundo(ahora));s.setVenceEn(segundo(ahora).plusSeconds(DURACION_SEGUNDOS));
        sesiones.saveAndFlush(s);
        return dto(ref,comprobar(ref,s.getRevision(),null,null));
    }

    @Override @Transactional(readOnly=true)
    public SesionAccesoDTO comprobarSesion(String referencia,long revision,Instant emitidaEn,Instant venceEn) {
        return dto(referencia,comprobar(referencia,revision,emitidaEn,venceEn));
    }

    @Override @Transactional(readOnly=true)
    public List<Contexto> contextos(String referencia,long revision) {
        Validada v=comprobar(referencia,revision,null,null);
        return opciones(v.sesion()).stream().map(Elegible::contexto).toList();
    }

    @Override @Transactional
    public SesionAccesoDTO seleccionar(String referencia,long revision,String contextoRef) {
        SesionAcceso s=bloquear(referencia,revision,false);
        Validada v=comprobar(referencia,revision,null,null);
        Elegible elegida=opciones(v.sesion()).stream().filter(e->e.contexto().referencia().equals(contextoRef))
                .findFirst().orElseThrow(AccesoNoAutorizadoException::new);
        membresias.bloquearParaContexto(elegida.membresia(),s.getIdCuenta()).orElseThrow(ContextosServiceImpl::rechazo);
        // La opción se vuelve a resolver después de adquirir su candado.
        elegida=opciones(v.sesion()).stream().filter(e->e.contexto().referencia().equals(contextoRef))
                .findFirst().orElseThrow(AccesoNoAutorizadoException::new);
        s.setRevision(s.getRevision()+1);s.setEmitidaEn(segundo(reloj.instant()));
        s.setIdMembresia(elegida.membresia());s.setIdRolOperativo(elegida.contexto().idRolOperativo());
        s.setRolEfectivo(elegida.contexto().rol());
        s.setContextoRefHash(huella(referenciaContexto(s.getSemillaContextos(),s.getRevision(),elegida.membresia(),
                elegida.contexto().idOrganizacion(),elegida.contexto().idPersona(),elegida.contexto().idRolOperativo(),
                elegida.contexto().rol(),elegida.contexto().modalidad())));
        sesiones.saveAndFlush(s);
        auditar(s,elegida.contexto(),"SELECCIONAR_CONTEXTO");
        return dto(referencia,comprobar(referencia,s.getRevision(),null,null));
    }

    @Override @Transactional
    public SesionAccesoDTO renovar(String referencia,long revision) {
        SesionAcceso s=bloquear(referencia,revision,false);Validada v=comprobar(referencia,revision,null,null);
        if (v.activo()!=null) membresias.bloquearParaContexto(s.getIdMembresia(),s.getIdCuenta())
                .orElseThrow(ContextosServiceImpl::rechazo);
        v=comprobar(referencia,revision,null,null);
        Instant ahora=segundo(reloj.instant());s.setRevision(s.getRevision()+1);s.setEmitidaEn(ahora);
        s.setVenceEn(ahora.plusSeconds(DURACION_SEGUNDOS));
        if (v.activo()!=null) {
            Contexto c=v.activo();s.setContextoRefHash(huella(referenciaContexto(s.getSemillaContextos(),s.getRevision(),
                    s.getIdMembresia(),c.idOrganizacion(),c.idPersona(),c.idRolOperativo(),c.rol(),c.modalidad())));
        }
        sesiones.saveAndFlush(s);auditar(s,v.activo(),"RENOVAR_SESION");
        return dto(referencia,comprobar(referencia,s.getRevision(),null,null));
    }

    @Override @Transactional
    public void cerrar(String referencia,long revision) {
        SesionAcceso s=bloquear(referencia,revision,true);comprobar(referencia,revision,null,null);
        var c=cuentas.findById(s.getIdCuenta()).orElseThrow(ContextosServiceImpl::rechazo);
        c.setSesionesInvalidasDesde(OffsetDateTime.ofInstant(reloj.instant(),ZoneOffset.UTC));
        cuentas.saveAndFlush(c);sesiones.revocarDeCuenta(s.getIdCuenta());
    }

    @Override @Transactional
    public SesionAccesoDTO exigirContexto(String referencia,long revision,Actor actor) {
        bloquear(referencia,revision,false);
        return contextoBloqueado(referencia,revision,actor);
    }

    @Override @Transactional
    public SesionAccesoDTO exigirContextoConLecturaProtegida(String referencia,long revision,Actor actor) {
        bloquear(referencia,revision,false,true);
        return contextoBloqueado(referencia,revision,actor);
    }

    private SesionAccesoDTO contextoBloqueado(String referencia,long revision,Actor actor) {
        // Fila actual: una entidad gestionada anterior no elige qué pertenencia/rol proteger.
        SesionAccesoFila s=sesion(referencia);
        if (s.idMembresia()==null) throw new AccesoNoAutorizadoException();
        membresias.bloquearParaContexto(s.idMembresia(),s.idCuenta()).orElseThrow(ContextosServiceImpl::rechazo);
        roles.bloquearParaContexto(s.idRolOperativo()).orElseThrow(ContextosServiceImpl::rechazo);
        Validada v=comprobar(referencia,revision,null,null);Contexto c=v.activo();
        if (c==null || actor==null || !new Actor(c.idOrganizacion(),c.idPersona(),c.idRolOperativo(),c.rol()).equals(actor)
                || v.cuenta().debeCambiarContrasena() || necesitaMfa(v)) throw new AccesoNoAutorizadoException();
        return dto(referencia,v);
    }

    @Override @Transactional
    public SesionAccesoDTO exigirIdentidad(String referencia,long revision) {
        bloquear(referencia,revision,true);return dto(referencia,comprobar(referencia,revision,null,null));
    }

    private SesionAcceso bloquear(String referencia,long revision,boolean cambioGlobal) {
        return bloquear(referencia,revision,cambioGlobal,false);
    }

    private SesionAcceso bloquear(String referencia,long revision,boolean cambioGlobal,boolean lecturaProtegida) {
        SesionAccesoFila inicial=sesion(referencia);
        if (cambioGlobal) cuentas.bloquearCambioAcceso(inicial.idCuenta()).orElseThrow(ContextosServiceImpl::rechazo);
        else cuentas.bloquearLecturaContexto(inicial.idCuenta()).orElseThrow(ContextosServiceImpl::rechazo);
        SesionAcceso s=(lecturaProtegida?sesiones.bloquearLecturaContexto(huella(referencia)):
                sesiones.bloquear(huella(referencia))).orElseThrow(ContextosServiceImpl::rechazo);
        comprobar(referencia,revision,null,null);return s;
    }

    private record Validada(SesionAccesoFila sesion,CuentaAccesoFila cuenta,Contexto activo) { }
    private record Elegible(long membresia,Contexto contexto) { }

    private Validada comprobar(String referencia,long revision,Instant emitida,Instant vence) {
        Integer aislamiento=org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if(aislamiento!=null && aislamiento!=java.sql.Connection.TRANSACTION_READ_COMMITTED)
            throw new IllegalStateException("La autorizacion contextual exige lecturas actuales READ_COMMITTED.");
        SesionAccesoFila s=sesion(referencia);Instant ahora=reloj.instant();
        if (s.revocada() || revision!=s.revision() || !ahora.isBefore(s.venceEn()) || s.emitidaEn().isAfter(ahora)
                || (emitida!=null && !s.emitidaEn().equals(emitida)) || (vence!=null && !s.venceEn().equals(vence))) throw rechazo();
        CuentaAccesoFila c=cuentaActiva(s.idCuenta());
        if (c.sesionesInvalidasDesde()!=null && !s.creadaEn().isAfter(c.sesionesInvalidasDesde().toInstant())) throw rechazo();
        Contexto activo=null;
        if (s.idMembresia()!=null) {
            activo=opciones(s).stream().filter(e->e.membresia()==s.idMembresia()
                    && e.contexto().idRolOperativo()==s.idRolOperativo()
                    && e.contexto().rol().equals(s.rolEfectivo()))
                    .map(Elegible::contexto).filter(e->huella(e.referencia()).equals(s.contextoRefHash()))
                    .findFirst().orElseThrow(ContextosServiceImpl::rechazo);
        } else if (s.idRolOperativo()!=null || s.rolEfectivo()!=null || s.contextoRefHash()!=null) throw rechazo();
        return new Validada(s,c,activo);
    }

    private SesionAccesoFila sesion(String ref) {
        if (ref==null || !ref.matches("[A-Za-z0-9_-]{43}")) throw rechazo();
        return sesiones.vista(huella(ref)).orElseThrow(ContextosServiceImpl::rechazo);
    }
    private CuentaAccesoFila cuentaActiva(long id) {
        CuentaAccesoFila c=cuentas.vista(id).orElseThrow(ContextosServiceImpl::rechazo);
        // La identidad personal no depende del estado de ninguna Persona organizacional.
        if (!"A".equals(c.estadoCuenta())||c.correoConfirmadoEn()==null) throw rechazo();
        return c;
    }
    private List<Elegible> opciones(SesionAccesoFila s) {
        List<Elegible> opciones=new ArrayList<>();LocalDate hoy=LocalDate.now(reloj.withZone(ZoneId.of("America/Lima")));
        var filas=membresias.lecturaContextosDeCuenta(s.idCuenta());
        if(filas.stream().filter(f->"A".equals(f.estadoMembresia())).map(ContextoCuentaFila::idMembresia).distinct().count()>1)
            throw rechazo();
        for (ContextoCuentaFila f:filas) {
            if (!"A".equals(f.estadoMembresia()) || !"A".equals(f.estadoOrganizacion()) || f.idPersona()==null
                    || !("INDIVIDUAL".equals(f.modalidad()) || "EMPRESARIAL".equals(f.modalidad()))) continue;
            if (f.idRolOperativo()==null || !"A".equals(f.estadoPersona()) || f.desde()==null
                    || f.desde().isAfter(hoy) || f.hasta()!=null) continue;
            String rol=f.tipoRol()==TipoRol.ADMIN && f.administrar() ? Actor.TENANT_ADMIN
                    : (f.tipoRol()==TipoRol.AGENTE && Actor.AGENTE.equals(f.rolPrincipal())) ? Actor.AGENTE
                    : (f.tipoRol()==TipoRol.BROKER && Actor.BROKER.equals(f.rolPrincipal())) ? Actor.BROKER : null;
            if (rol==null) continue;
            opciones.add(new Elegible(f.idMembresia(),new Contexto(referenciaContexto(s.semillaContextos(),s.revision(),
                    f.idMembresia(),f.idOrganizacion(),f.idPersona(),f.idRolOperativo(),rol,f.modalidad()),f.idOrganizacion(),
                    f.idPersona(),f.idRolOperativo(),rol,f.modalidad(),f.nombreOrganizacion())));
        }
        return opciones.stream().sorted(Comparator.comparing((Elegible e)->e.contexto().idOrganizacion())
                .thenComparing(e->e.contexto().rol()).thenComparing(e->e.contexto().idRolOperativo())).toList();
    }
    private static String referenciaContexto(String semilla,long revision,long miembro,long org,long persona,long rolId,String rol,String modalidad) {
        return huella(String.join("\u0000",semilla,Long.toString(revision),Long.toString(miembro),Long.toString(org),
                Long.toString(persona),Long.toString(rolId),rol,Objects.toString(modalidad,"")));
    }
    private static IdentidadAutenticada identidad(CuentaAccesoFila c) {
        return new IdentidadAutenticada(c.idCuenta(),c.nombreUsuario(),c.correoPropio(),c.nombreProfesionalDeclarado());
    }
    private SesionAccesoDTO dto(String referencia,Validada v) {
        return new SesionAccesoDTO(referencia,v.sesion().revision(),identidad(v.cuenta()),v.activo(),v.sesion().emitidaEn(),
                v.sesion().venceEn(),new EstadoDeAcceso(v.cuenta().sesionesInvalidasDesde(),v.cuenta().debeCambiarContrasena(),
                necesitaMfa(v),v.activo()==null ? "IDENTIDAD" : v.activo().rol()));
    }
    private boolean necesitaMfa(Validada v){return v.cuenta().debeEnrolarMfa()
            ||(v.activo()!=null&&Actor.TENANT_ADMIN.equals(v.activo().rol())&&!factores.activoDeCuenta(v.cuenta().idCuenta()));}
    private static Instant segundo(Instant i) { return i.truncatedTo(ChronoUnit.SECONDS); }
    private void auditar(SesionAcceso sesion,Contexto contexto,String accion) {
        var actor=contexto==null ? new EventosSeguridad.Contexto(null,null,sesion.getIdCuenta(),null,null,null)
                : new EventosSeguridad.Contexto(contexto.idOrganizacion(),contexto.idPersona(),sesion.getIdCuenta(),contexto.rol(),null,null);
        auditoria.registrarEnTransaccion(contexto==null ? "SESIONES_INVALIDADAS" : "ACCESO_TENANT_USADO","OK",actor,
                Map.of("accion",accion,"revision",sesion.getRevision(),"sesion",sesion.getId()));
    }
    private static String huella(String texto) { return Idempotencia.huellaExacta(texto); }
    private static String nuevaReferencia() { byte[] bytes=new byte[32];ALEATORIO.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static CredencialesInvalidasException rechazo() { return new CredencialesInvalidasException(); }
}
