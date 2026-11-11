package com.controllocal.app;

import com.controllocal.persistence.repositorio.ReconfirmacionesDisponibilidadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** D2: observación local sin envíos externos; cada destinatario usa su propia TX y autoridad actual. */
@Component
@ConditionalOnProperty(name="controllocal.disponibilidad.observador.habilitado",havingValue="true",matchIfMissing=true)
public class ObservadorReconfirmaciones {
    private static final Logger LOG=LoggerFactory.getLogger(ObservadorReconfirmaciones.class);
    private final ReconfirmacionesDisponibilidadRepository repo;
    private final ReconfirmacionesDisponibilidad solicitudes;
    public ObservadorReconfirmaciones(ReconfirmacionesDisponibilidadRepository repo,ReconfirmacionesDisponibilidad solicitudes){this.repo=repo;this.solicitudes=solicitudes;}
    @Scheduled(fixedDelayString="${controllocal.disponibilidad.observador.intervalo-ms:60000}")
    public void observar(){
        long despues=0;
        while(!Thread.currentThread().isInterrupted()){
            var lote=repo.destinatarios(despues,100);if(lote.isEmpty())return;
            for(var d:lote){despues=d.rol();try{solicitudes.sincronizar(new Actor(d.organizacion(),d.persona(),d.rol(),Actor.AGENTE));}
                catch(AccesoNoAutorizadoException revocado){LOG.debug("D2 omite destinatario sin atribución operativa vigente: {}",d.rol());}
                catch(RuntimeException fallo){LOG.error("D2 no pudo comprobar el destinatario {}; conserva el trabajo para recuperación",d.rol(),fallo);}}
        }
    }
}
