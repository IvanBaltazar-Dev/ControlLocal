package com.controllocal.service.encargo;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.OrganizacionRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Alcances;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.TreeSet;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

/** Una autoridad para expediente y efectos. Gobierno no hereda operación comercial. */
@Component
public class AccesoEncargo {
    private final CaptacionRepository encargos;
    private final Alcances alcances;
    private final OrganizacionRepository organizaciones;
    private final UsuarioOrganizacionRepository miembros;
    private final CuentaAccesoRepository cuentas;
    private final ContextosService contextos;
    public AccesoEncargo(CaptacionRepository encargos,Alcances alcances,
            OrganizacionRepository organizaciones,UsuarioOrganizacionRepository miembros,
            CuentaAccesoRepository cuentas,ContextosService contextos) {
        this.encargos=encargos; this.alcances=alcances; this.organizaciones=organizaciones;
        this.miembros=miembros; this.cuentas=cuentas; this.contextos=contextos;
    }
    public CaptacionRepository.ContextoActualEncargo contexto(long id,Actor actor) {
        return encargos.contextoActual(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Encargo"));
    }
    public void exigirLectura(long id,Actor actor) {
        var actual=contexto(id,actor);
        if(!permiteResponsableLectura(actual.getResponsable(),actor)) throw new AccesoNoAutorizadoException();
    }
    public Captacion leer(long id,Actor actor) {
        var actual=contexto(id,actor);
        if(!permiteResponsableLectura(actual.getResponsable(),actor)) throw new AccesoNoAutorizadoException();
        Captacion c=encargos.buscarFicha(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Encargo"));
        if(c.getVersion()!=actual.getVersion() || c.getAgente()==null
                || !Objects.equals(c.getAgente().getId(),actual.getResponsable()))
            throw new ConflictoException("El Encargo cambió; consulta su versión actual.");
        return c;
    }
    /** Cuentas por ID → sesión vigente → comando → Encargo; WRITE en la entrada anterior, READ protegido en C6. */
    public record Inicio(Long responsable) { }
    /** Misma política sobre metadata actual de Core; no admite referencias aportadas por clientes. */
    public boolean permiteResponsableLectura(Long responsable,Actor actor) {
        return permiteLectura(responsable,alcances.de(actor).rolesAgente());
    }
    public Set<Long> permitidosActuales(Map<Long,Long> responsables,Actor actor){
        var roles=Set.copyOf(alcances.de(actor).rolesAgente());var permitidos=new HashSet<Long>();
        responsables.forEach((id,rol)->{if(permiteLectura(rol,roles))permitidos.add(id);});return Set.copyOf(permitidos);
    }
    private static boolean permiteLectura(Long responsable,Collection<Long> roles){return responsable!=null&&roles.contains(responsable);}
    public Inicio iniciar(Actor actor,ContextoSesion sesion,Long encargo) {
        return iniciar(actor,sesion,encargo,null,false,false);
    }
    /** Misma autoridad; C6 sólo lee cuentas y Encargo, conservando los cambios exclusivos. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Inicio iniciarConLecturaProtegida(Actor actor,ContextoSesion sesion,Long encargo) {
        return iniciar(actor,sesion,encargo,null,false,true);
    }
    public Inicio iniciarReasignacion(Actor actor,ContextoSesion sesion,long encargo,long destino) {
        return iniciar(actor,sesion,encargo,destino,true,false);
    }
    private Inicio iniciar(Actor actor,ContextoSesion sesion,Long encargo,Long destino,boolean gobierno,boolean lecturaProtegida) {
        var recurso=encargo==null?null:contexto(encargo,actor);
        if(sesion==null) throw new AccesoNoAutorizadoException();
        var propias=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(propias.size()!=1) throw new AccesoNoAutorizadoException();
        TreeSet<Long> ids=new TreeSet<>(propias);
        if(recurso!=null && recurso.getCuenta()!=null) ids.add(recurso.getCuenta());
        if(destino!=null) encargos.cuentaDelRol(actor.idOrganizacion(),destino).ifPresent(ids::add);
        for(long id:ids) (lecturaProtegida?cuentas.bloquearLecturaContexto(id):cuentas.bloquearCambioAcceso(id))
                .orElseThrow(AccesoNoAutorizadoException::new);
        try {
            var vigente=lecturaProtegida?
                    contextos.exigirContextoConLecturaProtegida(sesion.referenciaSesion(),sesion.revisionContexto(),actor):
                    contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(!ContextoSesion.de(vigente).equals(sesion)) throw new AccesoNoAutorizadoException();
        } catch(CredencialesInvalidasException fallo) { throw new AccesoNoAutorizadoException(); }
        if(gobierno) {
            if(!actor.esBroker() && !actor.esTenantAdmin()) throw new AccesoNoAutorizadoException();
        } else Alcances.exigirOperador(actor);
        if(recurso!=null) {
            var actual=contexto(encargo,actor);
            if(!Objects.equals(actual.getCuenta(),recurso.getCuenta())
                    || !Objects.equals(actual.getResponsable(),recurso.getResponsable())) {
                boolean autorizado=gobierno?alcances.alcanzaParaGobiernoDeReasignacion(actor,actual.getResponsable()):
                        alcances.alcanza(actor,actual.getResponsable());
                if(!autorizado)throw new AccesoNoAutorizadoException();
                throw new ConflictoException("El responsable cambió; vuelve a comprobar el Encargo.");
            }
        }
        return new Inicio(recurso==null?null:recurso.getResponsable());
    }
    public Captacion escribir(long id,Actor actor,Inicio inicio) {
        Captacion c=encargos.bloquearParaEscritura(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Encargo"));
        exigirActuacion(c,actor,inicio);
        return c;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Captacion leerParaActuar(long id,Actor actor,Inicio inicio) {
        Captacion c=encargos.bloquearParaLecturaContextual(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Encargo"));
        exigirActuacion(c,actor,inicio);
        // El candado no refresca por sí solo una entidad previamente gestionada.
        var actual=contexto(id,actor);
        if(c.getVersion()!=actual.getVersion()||!Objects.equals(c.getAgente().getId(),actual.getResponsable()))
            throw new ConflictoException("El Encargo cambió; consulta su versión actual.");
        return c;
    }
    private void exigirActuacion(Captacion c,Actor actor,Inicio inicio) {
        if(!alcances.alcanza(actor,c.getAgente().getId())) throw new AccesoNoAutorizadoException();
        if(!Objects.equals(c.getAgente().getId(),inicio.responsable()))
            throw new ConflictoException("El responsable cambió; vuelve a comprobar el Encargo.");
    }
    public Captacion escribirParaReasignacion(long id,Actor actor,Inicio inicio) {
        Captacion c=encargos.bloquearParaEscritura(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Encargo"));
        if(!alcances.alcanzaParaGobiernoDeReasignacion(actor,c.getAgente().getId()))
            throw new AccesoNoAutorizadoException();
        if(!Objects.equals(c.getAgente().getId(),inicio.responsable()))
            throw new ConflictoException("El responsable cambió; vuelve a comprobar el Encargo.");
        return c;
    }
    public void exigirResponsable(Captacion c,Actor actor) {
        if(!actor.esAgente() || !Objects.equals(c.getAgente().getId(),actor.idRolOperativo()))
            throw new AccesoNoAutorizadoException();
    }
    public void exigirEditor(Captacion c,Actor actor) {
        exigirResponsable(c,actor);
        if(!c.editable()) throw new ReglaNegocioException(
                "Los términos aceptados requieren adenda; un episodio terminado no admite edición ordinaria.");
    }
    /** Frontera técnica compartida con atributos gobernados; no abre otro comando público. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void modificarContenido(Captacion c,long versionEsperada,Actor actor,
                                    java.util.function.BooleanSupplier cambio) {
        exigirEditor(c,actor);
        if(c.getVersion()!=versionEsperada)
            throw new ConflictoException("La versión del Encargo cambió; vuelve a revisar el contenido.");
        var actual=contexto(c.getId(),actor);
        if(actual.getVersion()!=c.getVersion()
                || !Objects.equals(actual.getResponsable(),c.getAgente().getId()))
            throw new ConflictoException("El Encargo cambió antes de modificar sus condiciones.");
        if(cambio.getAsBoolean()) c.renovarContenido();
    }
    public void exigirDecision(Captacion c,Actor actor) {
        if(habilitadoParaDecidir(actor)&&c.getAgente()!=null
                &&alcances.alcanza(actor,c.getAgente().getId())) return;
        throw new AccesoNoAutorizadoException();
    }
    /** La misma autoridad de decisión en forma de selector, para lecturas financieras en lote. */
    public Alcances.Alcance paraDecision(Actor actor) {
        if(actor==null)throw new AccesoNoAutorizadoException();
        return habilitadoParaDecidir(actor)?alcances.de(actor):
                new Alcances.Alcance(actor.idOrganizacion(),false,java.util.List.of());
    }
    private boolean habilitadoParaDecidir(Actor actor) {
        return actor!=null&&(actor.esBroker()||actor.esAgente()
                &&organizaciones.findById(actor.idOrganizacion())
                .filter(o->o.estaActiva()&&"INDIVIDUAL".equals(o.getModalidad())).isPresent());
    }
    public boolean puedeDecidir(Captacion c,Actor actor) {
        try { exigirDecision(c,actor); return true; }
        catch(AccesoNoAutorizadoException denegado) { return false; }
    }
    public boolean puedeDecidir(long encargo,Actor actor) {
        return puedeDecidir(leer(encargo,actor),actor);
    }
    public String nombreOrganizacion(Actor actor) {
        return organizaciones.findById(actor.idOrganizacion())
                .filter(o->o.estaActiva()).map(o->o.getNombre()).orElse(null);
    }
}
