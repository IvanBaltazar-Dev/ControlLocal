package com.controllocal.app.arranque;

import com.controllocal.service.actividad.ProgramasPersonales;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

/** E_C06 dentro Core: tres fronteras transaccionales, sin JWT permanente ni otro backend. */
@Component
@org.springframework.scheduling.annotation.EnableScheduling
@ConditionalOnProperty(name="controllocal.programas.habilitado",havingValue="true",matchIfMissing=true)
public class EjecutorProgramasPersonales {
    private final ProgramasPersonales programas;
    public EjecutorProgramasPersonales(ProgramasPersonales p){programas=p;}
    @Scheduled(fixedDelayString="${controllocal.programas.espera-ms:1000}",initialDelayString="${controllocal.programas.espera-ms:1000}")
    public void ejecutarUna(){
        var reclamo=programas.reclamar();if(reclamo.isEmpty())return;
        try{var resultado=programas.producir(reclamo.get());programas.finalizar(reclamo.get(),resultado);}
        catch(RuntimeException e){programas.fallar(reclamo.get(),e);}
    }
}
