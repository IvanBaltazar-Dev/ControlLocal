package com.controllocal.service.materialia;

import com.controllocal.persistence.materialia.RegistroMaterialIaRepository;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository.Operacion;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.presupuesto.AutorizadorRecursoIa;
import com.controllocal.service.presupuesto.PresupuestoIaService;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Une B0 a la intención exacta y a la autoridad propietaria; no crea una segunda política de permisos. */
@Component
public class AutoridadMaterialIa implements AutorizadorRecursoIa {
    private final RegistroMaterialIaRepository repo;
    private final Documentos json;
    private final List<FuentesMaterialIa> fuentes;
    private final PerfilesMaterialIa perfiles;
    public AutoridadMaterialIa(RegistroMaterialIaRepository repo,Documentos json,List<FuentesMaterialIa> fuentes,PerfilesMaterialIa perfiles){
        this.repo=repo;this.json=json;this.fuentes=List.copyOf(fuentes);this.perfiles=perfiles;
    }
    @Override public boolean atiende(String perfil,String tipo){
        return Set.of("MATERIAL_IA_PROPIEDAD","MATERIAL_IA_ENCARGO").contains(tipo)&&perfiles.reconoce(perfil);
    }
    @Override public void reautorizar(PresupuestoIaService.Solicitud peticion,Actor actor){
        var o=repo.obtener(actor.idOrganizacion(),peticion.operacion()).orElseThrow(()->new NoEncontradoException("Material IA"));
        actor(o,actor);var s=solicitud(o);var f=snapshot(o);
        if(!presupuesto(o,s,f).equals(peticion))throw new ConflictoException("La financiación no corresponde a esta intención de material.");
        revalidar(s,f,actor);exigirEvidencia(f);
    }
    public FuentesMaterialIa fuente(){
        if(fuentes.size()!=1)throw new CapacidadDeshabilitadaException();return fuentes.getFirst();
    }
    public void revalidar(Solicitud s,Snapshot f,Actor actor){fuente().revalidar(s,f,actor);}
    public void exigirLecturaHistorica(Solicitud s,Snapshot f,Actor actor){fuente().exigirLecturaHistorica(s,f,actor);}
    public Solicitud solicitud(Operacion o){return json.comoObjeto(o.solicitud(),Solicitud.class);}
    public Snapshot snapshot(Operacion o){
        if(!Idempotencia.huellaExacta(o.snapshot()).equals(o.huellaSnapshot()))
            throw new IllegalStateException("La fuente privada del material no conserva su integridad.");
        return json.comoObjeto(o.snapshot(),Snapshot.class);
    }
    public static PresupuestoIaService.Solicitud presupuesto(Operacion o,Solicitud s,Snapshot f){
        return new PresupuestoIaService.Solicitud(o.id(),o.perfil(),o.versionPerfil(),new PresupuestoIaService.Recurso(
                "MATERIAL_IA_"+s.recurso().tipo(),s.recurso().referencia(),s.accion().name(),f.huellaOrigen()),o.peticion());
    }
    public static void actor(Operacion o,Actor a){
        if(o.organizacion()!=a.idOrganizacion()||o.persona()!=a.idPersona()||o.rol()!=a.idRolOperativo()||!o.rolEfectivo().equals(a.rolEfectivo()))
            throw new AccesoNoAutorizadoException();
    }
    public static void exigirEvidencia(Snapshot s){
        var e=s.evidencia();
        if(e==null||e.estado()!=EstadoEvidencia.ACREDITADA||!texto(e.finalidad())||e.categorias().isEmpty()
                ||e.categorias().stream().anyMatch(c->!texto(c))||!texto(e.condicionesVersion())||!texto(e.proveedorExplicado())
                ||!texto(e.decision())||!texto(e.referenciaEvidencia())||!texto(e.legitimacionTerceros())||!texto(e.preferenciaVersion()))
            throw new CapacidadDeshabilitadaException();
    }
    private static boolean texto(String v){return v!=null&&!v.isBlank();}
}
