package com.controllocal.app.arranque;

import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.materialia.ResultadosMaterialIa.*;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disparador técnico único; el reloj, la elegibilidad, los locks y la retirada pertenecen a Core144/B0. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="controllocal.material-ia.conservacion.habilitada",havingValue="true")
public class ConservacionMaterialIa {
    private static final Logger log=LoggerFactory.getLogger(ConservacionMaterialIa.class);
    /** Límite de trabajo técnico por tick, no límite funcional ni SLA de eliminación. */
    static final int LOTE=128;
    public record Resultado(int candidatas,int conciliadas,int fallos,boolean omitida) { }
    private final ResultadosMaterialIa resultados;
    private final AlmacenDocumentos almacen;
    private final AtomicBoolean ejecutando=new AtomicBoolean();
    private CursorConservacion cursor;
    public ConservacionMaterialIa(ResultadosMaterialIa resultados,AlmacenDocumentos almacen){this.resultados=resultados;this.almacen=almacen;}
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar(){conciliar();}
    /** 60s es cadencia técnica configurable NO_SLA; no cambia el vencimiento lógico exacto de144. */
    @Scheduled(fixedDelayString="${controllocal.material-ia.conservacion.intervalo-ms:60000}",
            initialDelayString="${controllocal.material-ia.conservacion.intervalo-ms:60000}")
    public void tick(){conciliar();}
    public Resultado conciliar(){
        if(!ejecutando.compareAndSet(false,true))return new Resultado(0,0,0,true);
        try{
            String namespace=almacen.identidadTecnica();
            if(namespace==null||!namespace.matches("[0-9a-f]{64}"))throw new IllegalStateException("Identidad técnica de almacén no acreditada.");
            var lote=resultados.candidatosConservacion(namespace,cursor,LOTE);validar(lote);
            int hechas=0,fallos=0;
            for(var candidata:lote.candidatos()){
                try{
                    if(!namespace.equals(almacen.identidadTecnica()))throw new IllegalStateException("Cambió la identidad técnica del almacén.");
                    resultados.conciliarCaducidad(candidata.organizacion(),candidata.operacion());hechas++;
                }catch(RuntimeException fallo){
                    fallos++;
                    // Metadata técnica de correlación, nunca mensajes/payload del proveedor o secretos.
                    log.warn("Conservación C6 pendiente: organización={}, operación={}, clase={}; se reintentará.",
                            candidata.organizacion(),candidata.operacion(),fallo.getClass().getSimpleName());
                }
            }
            cursor=lote.fin()?null:lote.siguiente();
            return new Resultado(lote.candidatos().size(),hechas,fallos,false);
        }catch(RuntimeException fallo){
            log.warn("No se pudo consultar el lote técnico de conservación C6: clase={}; se mantiene el cursor para recuperación.",fallo.getClass().getSimpleName());
            return new Resultado(0,0,1,false);
        }finally{ejecutando.set(false);}
    }
    private void validar(LoteConservacion lote){
        if(lote==null||lote.candidatos()==null||lote.candidatos().size()>LOTE)throw new IllegalStateException("Lote técnico de conservación inválido.");
        Set<CursorConservacion> claves=new HashSet<>();
        for(var c:lote.candidatos())if(c==null||c.organizacion()<=0||c.operacion()==null||c.revision()<0
                ||!claves.add(new CursorConservacion(c.organizacion(),c.operacion())))throw new IllegalStateException("Candidato técnico inválido o duplicado.");
        if(!lote.fin()){
            if(lote.candidatos().isEmpty()||lote.siguiente()==null||lote.siguiente().equals(cursor))throw new IllegalStateException("El cursor técnico no avanza.");
            var ultimo=lote.candidatos().getLast();
            if(!lote.siguiente().equals(new CursorConservacion(ultimo.organizacion(),ultimo.operacion())))throw new IllegalStateException("El cursor técnico no corresponde al lote.");
        }
    }
}
