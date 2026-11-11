package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository.FuenteConversion;
import com.controllocal.service.Actor;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.Alcances;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;

/** Fuentes20.8/20.9; agrupa por identidad de prospección, no por inmueble o número de Encargos. */
@Component
public class ConversionDeProspecciones {
    public record Lectura(List<Prospeccion> hechos,boolean verificable){public Lectura{hechos=List.copyOf(hechos);}}
    private final ProspeccionRepository repo;private final AccesoEncargo acceso;private final Alcances alcances;private final ContextosDeActividad contexto;
    public ConversionDeProspecciones(ProspeccionRepository repo,AccesoEncargo acceso,Alcances alcances,ContextosDeActividad contexto){this.repo=repo;this.acceso=acceso;this.alcances=alcances;this.contexto=contexto;}
    @Transactional(readOnly=true)
    public Lectura leer(Actor actor,Ventana ventana){
        contexto.exigirOperador(actor);var alcance=alcances.de(actor);
        var filas=repo.fuentesConversion(actor.idOrganizacion(),alcance.paramRoles(),ventana.corte());var permitidos=permitidos(filas,actor);
        var grupos=new LinkedHashMap<Long,List<FuenteConversion>>();for(var f:filas)grupos.computeIfAbsent(f.getId(),k->new ArrayList<>()).add(f);
        var salida=new ArrayList<Prospeccion>();boolean verificable=true;
        for(var grupo:grupos.values()){
            var p=grupo.getFirst();if(p.getActualizada()!=null&&p.getActualizada().isAfter(ventana.corte()))throw new ConflictoException("Cambió la prospección después del corte.");
            for(var f:grupo)if(f.getEncargo()!=null&&!permitidos.contains(f.getEncargo())&&(f.getHabilitada()==null||ventana.contiene(f.getHabilitada())))verificable=false;
            if(grupo.stream().anyMatch(f->f.getEncargo()!=null&&!permitidos.contains(f.getEncargo())))continue;
            salida.add(desde(grupo,ventana.corte()));
        }
        contexto.exigirOperador(actor);var actual=repo.fuentesConversion(actor.idOrganizacion(),alcances.de(actor).paramRoles(),ventana.corte());
        if(!huellas(filas).equals(huellas(actual))||!permitidos.equals(permitidos(actual,actor)))throw new ConflictoException("Cambiaron las fuentes o permisos de conversión; actualiza la medición.");
        return new Lectura(salida,verificable);
    }
    private Set<Long> permitidos(List<FuenteConversion> filas,Actor actor){
        var ids=new LinkedHashMap<Long,Long>();for(var f:filas)if(f.getEncargo()!=null){
            var anterior=ids.put(f.getEncargo(),f.getResponsableEncargo());if(anterior!=null&&!anterior.equals(f.getResponsableEncargo()))throw new ConflictoException("Cambió el responsable del Encargo.");
        }return acceso.permitidosActuales(ids,actor);
    }
    static Prospeccion desde(List<FuenteConversion> filas,Instant corte){
        var p=filas.getFirst();boolean dudosa=false,preparacion=false,origenConocido=false;FuenteConversion primera=null;
        for(var f:filas)if(f.getEncargo()!=null){
            if(f.getOrigenRegistrado()!=null&&f.getOrigenRegistrado().isAfter(corte))throw new ConflictoException("El origen cambió después del corte.");
            boolean origen="PROSPECCION".equals(f.getTipoOrigen())&&Objects.equals(f.getOrigen(),p.getId());
            if(!origen){if("DESCONOCIDO".equals(f.getTipoOrigen()))dudosa=true;continue;}
            origenConocido=true;
            if(f.getHabilitada()!=null){if(primera==null||f.getHabilitada().isBefore(primera.getHabilitada())||f.getHabilitada().equals(primera.getHabilitada())&&f.getRevisionHabilitacion()<primera.getRevisionHabilitacion())primera=f;}
            else if("A".equals(f.getEstadoEncargo())){
                // Aprobar antes del inicio no acredita un hecho futuro. Al llegar el plazo sin constancia,
                // falta evidencia de la primera habilitación; no inventar fecha ni mantener un borrador ficticio.
                if(f.getDesdeEncargo()==null||!f.getDesdeEncargo().isAfter(corte.atZone(ZoneId.of("America/Lima")).toLocalDate()))dudosa=true;
            }else preparacion=true;
        }
        Long responsable=p.getResponsable();Instant registrada=p.getRegistrada();List<LocalDate> habilitaciones=List.of();
        if(primera!=null){
            responsable=Objects.equals(primera.getProspeccionHabilitada(),p.getId())?primera.getResponsableHabilitacion():primera.getResponsableAnterior();
            registrada=primera.getHabilitacionRegistrada();habilitaciones=List.of(primera.getHabilitada());
        }else if("D".equals(p.getEstado())){responsable=p.getResponsableTermino();registrada=p.getTerminoRegistrado();}
        long version=primera!=null?primera.getRevisionHabilitacion():p.getHistorial()==null?0:p.getHistorial();
        var f=new Fuente("PROSPECCION",p.getId(),version,registrada,responsable);
        return new Prospeccion(f,p.getTermino(),habilitaciones,preparacion||!origenConocido&&"T".equals(p.getEstado()),"D".equals(p.getEstado()),dudosa&&primera==null);
    }
    private static List<List<Object>> huellas(List<FuenteConversion> filas){
        return filas.stream().map(f->Arrays.<Object>asList(f.getId(),f.getResponsable(),f.getEstado(),f.getRegistrada(),f.getActualizada(),f.getHistorial(),f.getTermino(),f.getTerminoRegistrado(),f.getResponsableTermino(),f.getEncargo(),f.getVersionEncargo(),f.getResponsableEncargo(),f.getEstadoEncargo(),f.getDesdeEncargo(),f.getTipoOrigen(),f.getOrigen(),f.getOrigenRegistrado(),f.getRevisionHabilitacion(),f.getHabilitada(),f.getHabilitacionRegistrada(),f.getProspeccionHabilitada(),f.getResponsableHabilitacion(),f.getResponsableAnterior())).toList();
    }
}
