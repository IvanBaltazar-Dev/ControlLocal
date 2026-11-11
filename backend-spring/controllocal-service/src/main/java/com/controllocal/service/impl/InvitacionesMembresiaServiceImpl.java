package com.controllocal.service.impl;

import com.controllocal.domain.organizacion.InvitacionMembresia;
import com.controllocal.domain.organizacion.Organizacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.InvitacionesMembresiaService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.EnlacesDeCuenta;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.service.soporte.Personas;
import com.controllocal.service.soporte.UsuariosInternos;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.Procedencia;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Cierre humano C1: pertenencia aceptada, vigencia explícita y ninguna credencial de gobierno. */
@Service
@Transactional
public class InvitacionesMembresiaServiceImpl implements InvitacionesMembresiaService {
    private static final String COMANDO_EMISION="EMITIR_INVITACION_MEMBRESIA";
    private final InvitacionMembresiaRepository invitaciones;
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final PersonaRolRepository roles;
    private final PersonaRepository personas;
    private final OrganizacionRepository organizaciones;
    private final DetalleAgenteRepository agentes;
    private final DetalleBrokerRepository brokers;
    private final SupervisionAgenteRepository supervisiones;
    private final UsuariosInternos usuarios;
    private final EventosSeguridad eventos;
    private final EntityManager entidades;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository registros;
    private final Documentos documentos;
    private final Clock reloj;

    @Autowired
    public InvitacionesMembresiaServiceImpl(InvitacionMembresiaRepository invitaciones,
            ContextosService contextos, CuentaAccesoRepository cuentas,
            UsuarioOrganizacionRepository miembros, PersonaRolRepository roles,
            PersonaRepository personas, OrganizacionRepository organizaciones,
            DetalleAgenteRepository agentes, DetalleBrokerRepository brokers,
            SupervisionAgenteRepository supervisiones, UsuariosInternos usuarios,
            EventosSeguridad eventos, EntityManager entidades, ComandosIdempotentes comandos,
            ComandoIdempotenteRepository registros, Documentos documentos) {
        this(invitaciones,contextos,cuentas,miembros,roles,personas,organizaciones,agentes,
                brokers,supervisiones,usuarios,eventos,entidades,comandos,registros,documentos,Clock.systemUTC());
    }

    public InvitacionesMembresiaServiceImpl(InvitacionMembresiaRepository invitaciones,
            ContextosService contextos, CuentaAccesoRepository cuentas,
            UsuarioOrganizacionRepository miembros, PersonaRolRepository roles,
            PersonaRepository personas, OrganizacionRepository organizaciones,
            DetalleAgenteRepository agentes, DetalleBrokerRepository brokers,
            SupervisionAgenteRepository supervisiones, UsuariosInternos usuarios,
            EventosSeguridad eventos, EntityManager entidades, ComandosIdempotentes comandos,
            ComandoIdempotenteRepository registros, Documentos documentos, Clock reloj) {
        this.invitaciones=invitaciones;this.contextos=contextos;this.cuentas=cuentas;
        this.miembros=miembros;this.roles=roles;this.personas=personas;
        this.organizaciones=organizaciones;this.agentes=agentes;this.brokers=brokers;
        this.supervisiones=supervisiones;this.usuarios=usuarios;this.eventos=eventos;
        this.entidades=entidades;this.reloj=reloj;
        this.comandos=comandos;this.registros=registros;this.documentos=documentos;
    }

    @Override
    public InvitacionEmitida emitir(DatosInvitacion datos, Actor actor, ContextoSesion sesion, String claveComando) {
        if(actor==null || !actor.esTenantAdmin() || sesion==null) throw new AccesoNoAutorizadoException();
        if(datos==null) throw new ReglaNegocioException("Debe indicar la invitacion de pertenencia.");
        String clave=Idempotencia.exigirClave(claveComando);
        String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("contrato",1,"actor",actor,"entrada",datos)));
        // Sólo se prelee existencia para ordenar cuentas; nunca se entrega un resultado sin gobierno actual.
        boolean conocido=registros.findByOrganizacionIdAndClaveIdempotencia(actor.idOrganizacion(),clave).isPresent();
        Long cuentaSupervisor=null;
        if(!conocido && "AGENTE".equals(datos.rolPrincipal()==null?null:datos.rolPrincipal().trim()) && datos.idBrokerSupervisor()!=null)
            cuentaSupervisor=invitaciones.supervisorActual(actor.idOrganizacion(),datos.idBrokerSupervisor())
                    .map(InvitacionMembresiaRepository.SupervisorActual::getCuenta).orElse(null);
        long cuentaGobierno=iniciarGobierno(actor,sesion,cuentaSupervisor);
        String identidad=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+clave);
        registros.bloquearIdentidad(Long.parseUnsignedLong(identidad.substring(0,16),16));
        java.util.Optional<com.controllocal.domain.auditoria.ComandoIdempotente> previo;
        try {previo=comandos.buscar(actor,clave,COMANDO_EMISION,huella);}
        catch(ReglaNegocioException distintaEntrada) {throw new ConflictoException("La clave del comando no admite otro contenido.");}
        if(previo.isPresent()) return restaurarEmision(previo.get().getResultado());
        String correo=EnlacesDeCuenta.correo(datos.correoCuenta());
        TipoRol principal=principal(datos.rolPrincipal(),datos.administrar());
        String motivo=obligatorio(datos.motivo(),500,"motivo");
        Instant ahora=reloj.instant();
        if(datos.venceEn()==null || !datos.venceEn().isAfter(ahora))
            throw new ReglaNegocioException("Debe elegir una fecha y hora limite futura para la invitacion.");
        if(principal==TipoRol.AGENTE) {
            if(datos.idBrokerSupervisor()==null) throw new ReglaNegocioException("Debe indicar el broker supervisor.");
        } else if(datos.idBrokerSupervisor()!=null || texto(datos.estadoOperativo(),1)!=null) {
            throw new ReglaNegocioException("Supervisor y disponibilidad corresponden al agente.");
        }
        if(principal==TipoRol.AGENTE) bloquearSupervisor(actor.idOrganizacion(),datos.idBrokerSupervisor(),cuentaSupervisor);
        exigirOrganizacion(actor.idOrganizacion());
        PersonaLocal p=datos.persona();
        if(p!=null && p.idPersona()!=null)
            personas.findByOrganizacionIdAndId(actor.idOrganizacion(),p.idPersona())
                    .orElseThrow(()->new NoEncontradoException("Persona"));
        var i=new InvitacionMembresia();i.setOrganizacionId(actor.idOrganizacion());
        i.setReferencia(UUID.randomUUID());i.setCorreoCuenta(correo);
        i.setRolPrincipal(principal==null?null:principal.name());i.setAdministrar(datos.administrar());
        i.setIdBrokerSupervisor(datos.idBrokerSupervisor());i.setCodigoInterno(texto(datos.codigoInterno(),20));
        i.setZona(texto(datos.zona(),100));
        i.setEstadoOperativo(principal==TipoRol.AGENTE?UsuariosInternos.estadoOperativoO(datos.estadoOperativo(),"D"):null);
        i.setCreadaEn(ahora.truncatedTo(java.time.temporal.ChronoUnit.MICROS));i.setVenceEn(datos.venceEn());i.setMotivo(motivo);
        i.setIdPersonaEmisora(actor.idPersona());i.setIdRolEmisor(actor.idRolOperativo());
        if(p!=null) {
            i.setIdPersonaLocal(p.idPersona());i.setNombre(texto(p.nombre(),150));
            i.setTipoPersona(codigoOpcional(p.tipoPersona(),Personas.TIPOS_PERSONA,"tipo de persona"));
            i.setTipoDocumento(codigoOpcional(p.tipoDocumento(),Personas.TIPOS_DOCUMENTO,"tipo de documento"));
            i.setNumeroDocumento(texto(p.numeroDocumento(),30));i.setTelefono(texto(p.telefono(),20));
            String laboral=texto(p.correoLaboral(),150);
            i.setCorreoLaboral(laboral==null?null:EnlacesDeCuenta.correo(laboral));
        }
        invitaciones.saveAndFlush(i);
        eventos.registrarEnTransaccion("INVITACION_EMITIDA","OK",gobierno(actor,cuentaGobierno),
                Map.of("invitacion",i.getId(),"administrar",i.isAdministrar(),
                        "rolPropuesto",i.getRolPrincipal()==null?"SIN_PRINCIPAL":i.getRolPrincipal()));
        var resultado=new InvitacionEmitida(i.getReferencia().toString(),i.getVenceEn(),i.getRolPrincipal(),i.isAdministrar());
        Map<String,Object> snapshot=resultadoDeEmision(resultado);
        comandos.registrar(actor,clave,COMANDO_EMISION,huella,"INVITACION_MEMBRESIA",i.getId(),
                Procedencia.deCabecera("API"),documentos.objetoCanonico(Map.of("version",1,"resultado",snapshot,
                        "huellaResultado",Idempotencia.huellaExacta(documentos.objetoCanonico(snapshot)))));
        return resultado;
    }

    @Override
    public PertenenciaAceptada aceptar(String referencia, String sesion, long revision) {
        // Orden común con baja: todas las cuentas WRITE, después sesión e invitación.
        var inicial=contextos.comprobarSesion(sesion,revision,null,null).identidad();
        UUID ref=referencia(referencia);
        var snapshot=invitaciones.contextoDe(ref).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
        if(!Objects.equals(snapshot.getCorreo(),inicial.correoPropio())) throw noDisponible();
        Long cuentaSupervisor=null;
        if("AGENTE".equals(snapshot.getPrincipal()) && snapshot.getAceptadaEn()==null && snapshot.getRetiradaEn()==null)
            cuentaSupervisor=supervisor(snapshot.getOrganizacion(),snapshot.getSupervisor()).getCuenta();
        bloquearCuentas(inicial.idCuenta(),cuentaSupervisor);
        var identidad=contextos.exigirIdentidad(sesion,revision).identidad();
        if(identidad.idCuenta()!=inicial.idCuenta()) throw noDisponible();
        var cuenta=cuentas.vista(identidad.idCuenta()).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
        if(!"A".equals(cuenta.estadoCuenta()) || cuenta.correoConfirmadoEn()==null) throw noDisponible();
        var i=bloquear(referencia);
        if(i.getOrganizacionId()!=snapshot.getOrganizacion() || !Objects.equals(i.getRolPrincipal(),snapshot.getPrincipal())
                || !Objects.equals(i.getIdBrokerSupervisor(),snapshot.getSupervisor())) throw noDisponible();
        if(!Objects.equals(i.getCorreoCuenta(),cuenta.correoPropio())) throw noDisponible();
        if(i.getAceptadaEn()!=null) {
            if(!Objects.equals(i.getIdCuentaAceptante(),cuenta.idCuenta())) throw noDisponible();
            return resultado(i);
        }
        if(i.getRetiradaEn()!=null || !reloj.instant().isBefore(i.getVenceEn())) throw noDisponible();
        TipoRol principal=principal(i.getRolPrincipal(),i.isAdministrar());
        // La baja del emisor no retira la decisión de la organización.
        if(principal==TipoRol.AGENTE) bloquearSupervisor(i.getOrganizacionId(),i.getIdBrokerSupervisor(),cuentaSupervisor);
        exigirOrganizacion(i.getOrganizacionId());
        Persona persona=personaParaAceptacion(i,cuenta.idCuenta());
        var vinculo=usuarios.vincularPertenenciaAceptada(i.getOrganizacionId(),cuenta.idCuenta(),
                persona,principal,i.isAdministrar());
        LocalDate hoy=LocalDate.now(reloj.withZone(ZoneId.of("America/Lima")));
        if(principal==TipoRol.AGENTE) {
            if(agentes.findById(vinculo.rolOperativo().getId()).isEmpty()) {
                var agente=new DetalleAgente();agente.setOrganizacionId(i.getOrganizacionId());
                agente.setRol(vinculo.rolOperativo());agente.setCodigoAgente(codigoInterno(i,"AGE",vinculo.rolOperativo().getId()));
                agente.setZonaAsignada(i.getZona());agente.setEstadoOperativo(i.getEstadoOperativo());
                agente.setFechaIngreso(hoy);agentes.saveAndFlush(agente);
            }
            var activa=supervisiones.buscarActivaPorAgente(i.getOrganizacionId(),vinculo.rolOperativo().getId());
            if(activa.isPresent() && !Objects.equals(activa.get().getIdRolBroker(),i.getIdBrokerSupervisor()))
                throw new ConflictoException("La supervision vigente difiere de la invitacion; requiere su reasignacion autorizada.");
            if(activa.isEmpty()) {
                var s=new SupervisionAgente();s.setOrganizacionId(i.getOrganizacionId());
                s.setIdRolAgente(vinculo.rolOperativo().getId());s.setIdRolBroker(i.getIdBrokerSupervisor());
                s.setFechaAsignacion(hoy);s.setMotivo(i.getMotivo());supervisiones.save(s);
            }
        } else if(principal==TipoRol.BROKER && brokers.findById(vinculo.rolOperativo().getId()).isEmpty()) {
            var broker=new DetalleBroker();broker.setOrganizacionId(i.getOrganizacionId());
            broker.setRol(vinculo.rolOperativo());broker.setCodigoBroker(codigoInterno(i,"BRK",vinculo.rolOperativo().getId()));
            broker.setZona(i.getZona());broker.setFechaDesignacion(hoy);brokers.saveAndFlush(broker);
        }
        Long rol=vinculo.rolOperativo()==null?null:vinculo.rolOperativo().getId();
        // Comprobación final con el reloj de PostgreSQL, después de todos los posibles candados.
        if(invitaciones.consumir(i.getReferencia(),cuenta.idCuenta(),vinculo.pertenencia().getId(),persona.getId(),rol)!=1)
            throw noDisponible();
        eventos.registrarEnTransaccion("ACCESO_TENANT_CONCEDIDO","OK",
                new EventosSeguridad.Contexto(i.getOrganizacionId(),null,cuenta.idCuenta(),null,null,null),
                Map.of("invitacion",i.getId(),"membresia",vinculo.pertenencia().getId(),"personaLocal",persona.getId()));
        return new PertenenciaAceptada(i.getReferencia().toString(),i.getOrganizacionId(),
                vinculo.pertenencia().getId(),persona.getId(),rol,i.getRolPrincipal(),i.isAdministrar());
    }

    @Override
    public InvitacionRetirada retirar(String referencia, String motivo, Actor actor, ContextoSesion sesion) {
        long cuentaGobierno=iniciarGobierno(actor,sesion,null);
        String motivoValidado=obligatorio(motivo,500,"motivo de retiro");
        var i=bloquear(referencia);
        if(!Objects.equals(i.getOrganizacionId(),actor.idOrganizacion())) throw noDisponible();
        if(i.getAceptadaEn()!=null) throw new ConflictoException("La pertenencia ya fue aceptada; retirar la invitacion no da de baja al miembro.");
        if(i.getRetiradaEn()==null) {
            i.setRetiradaEn(reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));i.setMotivoRetiro(motivoValidado);
            i.setIdPersonaRetiro(actor.idPersona());i.setIdRolRetiro(actor.idRolOperativo());
            invitaciones.saveAndFlush(i);
            eventos.registrarEnTransaccion("INVITACION_RETIRADA","OK",gobierno(actor,cuentaGobierno),Map.of("invitacion",i.getId()));
        }
        return new InvitacionRetirada(i.getReferencia().toString(),i.getRetiradaEn());
    }

    private Persona personaParaAceptacion(InvitacionMembresia i,long cuenta) {
        Long id=i.getIdPersonaLocal();
        var anterior=miembros.buscarPorCuenta(i.getOrganizacionId(),cuenta);
        if(anterior.isPresent()) {
            Long conocida=anterior.get().getIdPersona();
            if(id!=null && !Objects.equals(id,conocida)) throw new ConflictoException("La referencia de persona no corresponde a esta pertenencia.");
            id=conocida;
        }
        if(id!=null) {
            if(invitaciones.personaLigadaAOtraCuenta(i.getOrganizacionId(),id,cuenta)) throw noDisponible();
            Persona p=personas.findByOrganizacionIdAndId(i.getOrganizacionId(),id).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
            entidades.refresh(p,LockModeType.PESSIMISTIC_READ);
            if(!"A".equals(p.getEstado())) throw noDisponible();
            exigirCoincidencia(i.getNombre(),p.getNombresORazonSocial());
            exigirCoincidencia(i.getTipoPersona(),p.getTipoPersona());exigirCoincidencia(i.getTipoDocumento(),p.getTipoDocumento());
            exigirCoincidencia(i.getNumeroDocumento(),p.getNumeroDocumento());exigirCoincidencia(i.getTelefono(),p.getTelefono());
            exigirCoincidencia(i.getCorreoLaboral(),p.getCorreo());
            return p;
        }
        // Dato desconocido queda null; no se inventa nombre, DNI ni un alias de correo.
        return personas.saveAndFlush(Personas.nueva(i.getOrganizacionId(),i.getTipoPersona(),i.getTipoDocumento(),
                i.getNumeroDocumento(),i.getNombre(),i.getTelefono(),i.getCorreoLaboral(),"A",null));
    }

    private InvitacionMembresiaRepository.SupervisorActual supervisor(long org,Long rol) {
        if(rol==null) throw new ReglaNegocioException("Debe indicar el broker supervisor.");
        return invitaciones.supervisorActual(org,rol).orElseThrow(()->new ReglaNegocioException("El broker supervisor no esta vigente en la organizacion."));
    }
    private void bloquearSupervisor(long org,Long rol,Long cuentaEsperada) {
        var s=supervisor(org,rol);
        if(cuentaEsperada==null || cuentaEsperada!=s.getCuenta()) throw noDisponible();
        miembros.bloquearParaContexto(s.getMembresia(),s.getCuenta()).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
        roles.bloquearParaContexto(rol).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
        entidades.find(Persona.class,s.getPersona(),LockModeType.PESSIMISTIC_READ);
        var actual=invitaciones.supervisorActual(org,rol).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
        if(s.getCuenta()!=actual.getCuenta() || s.getMembresia()!=actual.getMembresia() || s.getPersona()!=actual.getPersona()) throw noDisponible();
    }

    private long iniciarGobierno(Actor actor,ContextoSesion sesion,Long supervisor) {
        if(actor==null || !actor.esTenantAdmin() || sesion==null) throw new AccesoNoAutorizadoException();
        var cuentasActor=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(cuentasActor.size()!=1) throw new AccesoNoAutorizadoException();
        long cuenta=cuentasActor.getFirst();bloquearCuentas(cuenta,supervisor);
        try {
            var actual=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(actual.identidad().idCuenta()!=cuenta || !ContextoSesion.de(actual).equals(sesion)) throw new AccesoNoAutorizadoException();
        } catch(CredencialesInvalidasException e) {throw new AccesoNoAutorizadoException();}
        if(!invitaciones.gobiernoVigente(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo()))
            throw new AccesoNoAutorizadoException();
        return cuenta;
    }
    private void bloquearCuentas(long propia,Long supervisor) {
        var ids=new java.util.TreeSet<Long>();ids.add(propia);if(supervisor!=null)ids.add(supervisor);
        for(long id:ids) cuentas.bloquearCambioAcceso(id).orElseThrow(AccesoNoAutorizadoException::new);
    }
    private void exigirOrganizacion(long org) {
        var o=organizaciones.findById(org).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
        entidades.refresh(o,LockModeType.PESSIMISTIC_READ);
        if(!o.estaActiva() || !"EMPRESARIAL".equals(o.getModalidad())) throw new ReglaNegocioException("La invitacion de equipo requiere una organizacion empresarial activa.");
    }
    private InvitacionMembresia bloquear(String valor) {
        UUID ref=referencia(valor);
        var i=invitaciones.bloquearParaAceptar(ref).orElseThrow(InvitacionesMembresiaServiceImpl::noDisponible);
        entidades.refresh(i,LockModeType.PESSIMISTIC_WRITE);return i;
    }
    private static UUID referencia(String valor) {
        UUID ref;
        try {
            if(valor==null || !valor.trim().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException();
            ref=UUID.fromString(valor.trim());
        } catch(IllegalArgumentException e) { throw noDisponible(); }
        return ref;
    }
    private static TipoRol principal(String valor,boolean administrar) {
        String limpio=texto(valor,16);
        if(limpio==null && administrar) return null;
        if("AGENTE".equals(limpio)) return TipoRol.AGENTE;
        if("BROKER".equals(limpio)) return TipoRol.BROKER;
        throw new ReglaNegocioException("Debe indicar un rol principal valido o una pertenencia administrativa.");
    }
    private static String codigoOpcional(String valor,java.util.Set<String> permitidos,String campo) {
        return texto(valor,1)==null?null:Personas.exigirCodigo(valor.trim(),permitidos,campo);
    }
    private static String texto(String valor,int maximo) {
        if(valor==null || valor.isBlank()) return null;
        String t=valor.trim();
        if(t.length()>maximo || t.chars().anyMatch(Character::isISOControl)) throw new ReglaNegocioException("El dato supera su formato permitido.");
        return t;
    }
    private static String obligatorio(String valor,int maximo,String campo) {
        String t=texto(valor,maximo);if(t==null) throw new ReglaNegocioException("Debe indicar el "+campo+".");return t;
    }
    private static String codigoInterno(InvitacionMembresia i,String prefijo,long rol) {
        return i.getCodigoInterno()!=null?i.getCodigoInterno():prefijo+"-"+Long.toUnsignedString(rol,36).toUpperCase(java.util.Locale.ROOT);
    }
    private static void exigirCoincidencia(String aportado,String conocido) {
        if(aportado!=null && !Objects.equals(aportado,conocido)) throw new ConflictoException("Los datos de la persona referenciada requieren su edicion contextual autorizada.");
    }
    private static PertenenciaAceptada resultado(InvitacionMembresia i) {
        return new PertenenciaAceptada(i.getReferencia().toString(),i.getOrganizacionId(),i.getIdMembresiaAceptada(),
                i.getIdPersonaAceptada(),i.getIdRolAceptado(),i.getRolPrincipal(),i.isAdministrar());
    }
    private Map<String,Object> resultadoDeEmision(InvitacionEmitida r) {
        Map<String,Object> m=new java.util.LinkedHashMap<>();m.put("referencia",r.referencia());
        m.put("venceEn",r.venceEn().toString());m.put("rolPrincipal",r.rolPrincipal());m.put("administrar",r.administrar());return m;
    }
    private InvitacionEmitida restaurarEmision(String guardado) {
        var snapshot=documentos.comoMapa(guardado);
        if(!(snapshot.get("version") instanceof Number v) || v.intValue()!=1 || !(snapshot.get("resultado") instanceof Map<?,?> resultado))
            throw new IllegalStateException("El resultado original de la invitacion no tiene un contrato conocido.");
        Map<String,Object> r=new java.util.LinkedHashMap<>();resultado.forEach((k,val)->r.put(String.valueOf(k),val));
        if(!Objects.equals(snapshot.get("huellaResultado"),Idempotencia.huellaExacta(documentos.objetoCanonico(r))))
            throw new IllegalStateException("La integridad del resultado original de invitacion no es valida.");
        try {
            return new InvitacionEmitida((String)r.get("referencia"),Instant.parse((String)r.get("venceEn")),
                    (String)r.get("rolPrincipal"),(Boolean)r.get("administrar"));
        } catch(RuntimeException corrupto) {throw new IllegalStateException("El resultado original de invitacion no se puede interpretar.",corrupto);}
    }
    private static EventosSeguridad.Contexto gobierno(Actor a,long cuenta) {
        return new EventosSeguridad.Contexto(a.idOrganizacion(),a.idPersona(),cuenta,a.rolEfectivo(),null,null);
    }
    private static NoEncontradoException noDisponible() { return new NoEncontradoException("Invitacion de pertenencia"); }
}
