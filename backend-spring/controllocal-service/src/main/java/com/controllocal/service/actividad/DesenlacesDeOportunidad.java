package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository.FuenteDesenlace;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.AccesoOportunidad;
import com.controllocal.service.soporte.Alcances;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;

/** CAT-E29 §20.8: ensambla fuentes autorizadas, sin otro motor de estados o de cálculo. */
@Component
public class DesenlacesDeOportunidad {
    private final OportunidadComercialRepository oportunidades;
    private final AccesoOportunidad acceso;
    private final Alcances alcances;
    private final ContextosDeActividad contexto;
    public DesenlacesDeOportunidad(OportunidadComercialRepository oportunidades,AccesoOportunidad acceso,
            Alcances alcances,ContextosDeActividad contexto){
        this.oportunidades=oportunidades;this.acceso=acceso;this.alcances=alcances;this.contexto=contexto;
    }
    @Transactional(readOnly=true)
    public List<Desenlace> leer(Actor actor,Instant corte){
        contexto.exigirOperador(actor);Objects.requireNonNull(corte);
        var alcance=alcances.de(actor);
        var fuentes=oportunidades.fuentesDesenlace(actor.idOrganizacion(),alcance.paramRoles(),corte);
        var permitidas=permitidas(fuentes,actor);var salida=new ArrayList<Desenlace>();
        for(var f:fuentes)if(permitidas.contains(f.getId())){
            if(f.getActualizado()!=null&&f.getActualizado().isAfter(corte))
                throw new ConflictoException("La oportunidad cambió después del corte; actualiza la medición.");
            salida.add(desde(f));
        }
        contexto.exigirOperador(actor);var nuevo=alcances.de(actor);
        var actuales=oportunidades.fuentesDesenlace(actor.idOrganizacion(),nuevo.paramRoles(),corte);
        if(!huellas(fuentes).equals(huellas(actuales))||!permitidas.equals(permitidas(actuales,actor)))
            throw new ConflictoException("Cambiaron las fuentes o permisos de los desenlaces; actualiza la medición.");
        return List.copyOf(salida);
    }
    private Set<Long> permitidas(List<FuenteDesenlace> filas,Actor actor){
        var responsables=new LinkedHashMap<Long,AccesoOportunidad.ResponsablesActuales>();
        for(var f:filas)if(responsables.put(f.getId(),new AccesoOportunidad.ResponsablesActuales(f.getResponsable(),f.getResponsableEncargo()))!=null)
            throw new IllegalStateException("Una oportunidad no puede duplicarse en las fuentes de desenlace.");
        return acceso.permitidasActuales(responsables,actor);
    }
    static Desenlace desde(FuenteDesenlace f){
        boolean resuelta=Set.of("F","N","X").contains(f.getEstado());
        boolean favorable="F".equals(f.getEstado())&&Boolean.TRUE.equals(f.getConfirmadoC5());
        var fecha=Set.of("N","X").contains(f.getEstado())||favorable?f.getFechaResolucion():null;
        var fuente=new Fuente("OPORTUNIDAD",f.getId(),f.getVersion(),f.getRegistrado(),f.getResponsableHistorico());
        return new Desenlace(fuente,com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(f.getOperacion()).name(),fecha,favorable,resuelta,f.getRevisionHito(),f.getHistorial());
    }
    private static Map<Long,List<Object>> huellas(List<FuenteDesenlace> filas){
        var h=new LinkedHashMap<Long,List<Object>>();
        for(var f:filas)if(h.put(f.getId(),Arrays.asList(f.getVersion(),f.getResponsable(),f.getResponsableEncargo(),f.getEstado(),
                f.getOperacion(),f.getResponsableHistorico(),f.getRevisionHito(),f.getHistorial(),f.getRegistrado(),f.getActualizado(),f.getFechaResolucion(),f.getConfirmadoC5()))!=null)
            throw new IllegalStateException("Fuente de desenlace duplicada.");
        return h;
    }
}
