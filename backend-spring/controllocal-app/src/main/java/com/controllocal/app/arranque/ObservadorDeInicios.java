package com.controllocal.app.arranque;

import com.controllocal.service.encargo.EncargoServiceImpl;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Observa la vigencia aprobada; no firma, aprueba ni suplanta un profesional. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="controllocal.encargo.observador-inicios.habilitado",havingValue="true",matchIfMissing=true)
public class ObservadorDeInicios {
    private static final Logger log=LoggerFactory.getLogger(ObservadorDeInicios.class);
    private final EncargoServiceImpl encargos;
    public ObservadorDeInicios(EncargoServiceImpl e){encargos=e;}
    @Scheduled(fixedDelayString="${controllocal.encargo.observador-inicios.intervalo-ms:60000}",initialDelayString="${controllocal.encargo.observador-inicios.intervalo-ms:60000}")
    public void observar(){
        long despues=0;
        while(true){var pagina=encargos.iniciosPendientes(despues);if(pagina.isEmpty())return;
        for(var c:pagina){despues=c.getEncargo();
            try{encargos.observarInicioAprobado(c.getOrganizacion(),c.getEncargo());}
            catch(RuntimeException e){log.warn("No se pudo comprobar un inicio aprobado; se conserva pendiente",e);}
        }
        if(pagina.size()<100)return;
        }
    }
}
