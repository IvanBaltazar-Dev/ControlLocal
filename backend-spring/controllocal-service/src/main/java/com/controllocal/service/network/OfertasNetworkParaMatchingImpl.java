package com.controllocal.service.network;

import com.controllocal.service.Actor;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.matching.OfertasNetworkParaMatching;
import com.controllocal.service.matching.MotorMatchingDeterminista;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import static com.controllocal.service.matching.ContratoMatching.*;

/** Sólo demanda propia → versiones L2 aprobadas. No recibe ni crea un Actor de la contraparte. */
@Component
public class OfertasNetworkParaMatchingImpl implements OfertasNetworkParaMatching {
    private final RequerimientoService demandas;
    private final NetworkService network;
    private final MotorMatchingDeterminista motor;
    private final Documentos documentos;
    public OfertasNetworkParaMatchingImpl(RequerimientoService demandas,NetworkService network,MotorMatchingDeterminista motor,Documentos documentos) {
        this.demandas=demandas;this.network=network;this.motor=motor;this.documentos=documentos;
    }
    @Override public List<Candidata> candidatas(long id,long version,Actor a,Instant corte) {
        exigirOrigen(id,version,a,corte);
        var referencias=network.referenciasParaConsulta(a);
        if(referencias.isEmpty())return List.of();
        return cargar(referencias.stream().map(FuenteDeNetwork.ReferenciaAutorizada::ofertaRef).toList(),a,corte);
    }
    @Override public List<Candidata> revalidar(long id,long version,List<Referencia> referencias,Actor a,Instant corte) {
        exigirOrigen(id,version,a,corte);
        if(referencias==null)throw new ReglaNegocioException("Se requieren las referencias de la continuación.");
        Map<String,Referencia> originales=new HashMap<>();
        for(var r:referencias)if(originales.putIfAbsent(r.ofertaRef(),r)!=null)
            throw new ReglaNegocioException("La continuación repite una referencia.");
        if(originales.isEmpty())return List.of();
        // Una versión actual todavía autorizada se devuelve para distinguir cambio de revocación.
        return cargar(originales.keySet(),a,corte);
    }
    private List<Candidata> cargar(Collection<String> referencias,Actor a,Instant corte) {
        Set<String> solicitadas=Set.copyOf(referencias);
        var vigentes=network.revalidarParaEntrega(referencias,a);
        List<Candidata> resultado=new ArrayList<>();Set<String> vistas=new HashSet<>();
        for(var v:vigentes) {
            var r=v.referencia();
            if(!solicitadas.contains(r.ofertaRef())||!vistas.add(r.ofertaRef()))
                throw new IllegalStateException("La fuente Network no conserva el conjunto solicitado.");
            if(v.disponibilidad()==null||motor.disponibilidad(v.disponibilidad(),corte).conclusion()!=Conclusion.CUMPLE)
                throw new IllegalStateException("La entrega Network no acredita la confirmación específica de disponibilidad.");
            String configuracion="OFERTA_NETWORK:"+r.ofertaRef();
            var datos=ProyeccionV1ParaMatching.datos(v.datos(),configuracion,corte);
            String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(v.datos()));
            var ref=new Referencia(r.ofertaRef(),r.versionProyeccion(),huella);
            var oferta=new Oferta(ref.ofertaRef(),new ClaveCandidato("L2",ref.ofertaRef(),configuracion),
                    ref.version(),true,Conclusion.CUMPLE,v.disponibilidad(),datos);
            resultado.add(new Candidata(ref,oferta,v.datos()));
        }
        return List.copyOf(resultado);
    }
    private void exigirOrigen(long id,long version,Actor a,Instant corte) {
        Objects.requireNonNull(corte,"El corte lo proporciona Core.");
        var d=demandas.autoridadParaColaboracion(id,version,RequerimientoService.AccionColaboracion.CONSULTAR,a);
        var fecha=corte.atZone(ZoneId.of("America/Lima")).toLocalDate();
        if(!"A".equals(d.estado())||d.vigenteDesde()!=null&&fecha.isBefore(d.vigenteDesde())
                ||d.vigenteHasta()!=null&&fecha.isAfter(d.vigenteHasta()))
            throw new ReglaNegocioException("La demanda no está vigente para descubrir coincidencias.");
    }
}
