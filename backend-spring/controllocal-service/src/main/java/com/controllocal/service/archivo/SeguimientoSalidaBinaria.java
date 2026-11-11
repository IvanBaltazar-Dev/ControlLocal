package com.controllocal.service.archivo;

import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository.Intento;
import com.controllocal.persistence.archivo.DiarioBinarioTecnico;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.time.Instant;

/** Durabilidad técnica, no segundo motor de permisos, cuotas o conservación comercial. */
@Service
public class SeguimientoSalidaBinaria {
    public enum Estado { PREPARADO,ESCRITO_VERIFICADO,INCIERTO,VINCULADO,PENDIENTE_RETIRO,RETIRADO_VERIFICADO }
    /** Vista técnica de Service: no expone un row/repositorio de Persistence a Web. */
    public record IntentoTecnico(long id,PropietarioBinario propietario,UUID operacion,int numero,String fuente,String clave,
            String extension,String contentType,String huella,long bytes,String estado,
            boolean escrituraIncierta,Instant ultimaVerificacion,String resultadoTecnico,String almacenHuella) { }
    public record Preparado(IntentoTecnico intento,boolean nuevo) { }
    @FunctionalInterface public interface Retiro { boolean retirarYVerificar(IntentoTecnico intento); }
    private static final Set<String> EXTENSIONES=Set.of("pdf","png","jpg","jpeg","txt","csv","bin");
    private final IntentoSalidaBinariaRepository intentos;
    private final IntentoSalidaBinariaRepository diario;
    private final TransactionTemplate nueva;
    public SeguimientoSalidaBinaria(IntentoSalidaBinariaRepository intentos,DiarioBinarioTecnico tecnico){
        this.intentos=intentos;this.diario=tecnico.repositorio();this.nueva=new TransactionTemplate(tecnico.gestor());
        this.nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public static long identidad(PropietarioBinario org,UUID operacion){
        String h=Idempotencia.huellaExacta((org instanceof PropietarioBinario.Organizacion o?"BROX-SALIDA-BINARIA\u0000"+o.id():"BROX-SALIDA-BINARIA-EVALUACION\u0000"+org.caso())+"\u0000"+operacion);
        return Long.parseUnsignedLong(h.substring(0,16),16);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bloquear(PropietarioBinario org,UUID operacion){
        exigir(org,operacion);if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La salida binaria debe vincularse dentro de su transacción.");
        intentos.bloquear(identidad(org,operacion));
    }
    /** NO_IO significa ningún intento, de ningún número/estado/namespace. No adquiere tarde el fence. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void exigirSinIntento(PropietarioBinario propietario,UUID operacion){
        exigir(propietario,operacion);
        if(!intentos.poseeBloqueo(identidad(propietario,operacion)))throw new IllegalStateException("El fence IO debe preceder a P/reserva/B0.");
        if(intentos.ultimo(dueno(propietario),operacion).isPresent())throw new ConflictoException("Un intento conserva su recuperación y capacidad.");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<IntentoTecnico> consultar(PropietarioBinario org,UUID operacion,String fuente,String almacenHuella){
        bloquear(org,operacion);fuente(fuente);var previo=intentos.ultimo(dueno(org),operacion);
        almacen(almacenHuella);previo.ifPresent(i->{mismoAlmacen(i,almacenHuella);mismaFuente(i,fuente);});return previo.map(SeguimientoSalidaBinaria::vista);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Preparado preparar(PropietarioBinario org,UUID operacion,String fuente,String almacenHuella,String extension,String tipo,String huella,long bytes){
        bloquear(org,operacion);fuente(fuente);almacen(almacenHuella);
        if(!EXTENSIONES.contains(extension)||tipo==null||tipo.isBlank()||tipo.length()>100
                ||huella==null||!huella.matches("[0-9a-f]{64}")||bytes<0)
            throw new IllegalArgumentException("Metadatos técnicos de salida no válidos.");
        return nueva.execute(tx->{
            // El padre puede ocupar todo Main; el dueño comprometido se lee por el mismo carril139.
            UUID dueno=diario.dueno(org.organizacion(),org.caso());
            var anterior=diario.ultimo(dueno,operacion);
            if(anterior.isPresent()){
                var i=anterior.get();mismoAlmacen(i,almacenHuella);mismaFuente(i,fuente);
                if(!Estado.RETIRADO_VERIFICADO.name().equals(i.estado())){
                    if(!i.huella().equals(huella)||i.bytes()!=bytes||!i.extension().equals(extension)||!i.contentType().equals(tipo))
                        throw new ConflictoException("Existe un intento de salida con contenido pendiente distinto; recupéralo antes de generar otro.");
                    return new Preparado(vista(i),false);
                }
            }
            int numero=anterior.isEmpty()?1:Math.addExact(anterior.get().numero(),1);
            String clave=org.prefijo()+"/salidas/"+operacion+"/"+numero+"."+extension;
            return new Preparado(vista(diario.insertar(dueno,operacion,numero,fuente,clave,extension,tipo,huella,bytes,almacenHuella)),true);
        });
    }
    public void verificado(IntentoTecnico i){almacen(i.almacenHuella());nueva.executeWithoutResult(tx->diario.estado(registro(i),
            Estado.VINCULADO.name().equals(i.estado())?Estado.VINCULADO.name():Estado.ESCRITO_VERIFICADO.name(),false,"LECTURA_EXACTA",true));}
    public void incierto(IntentoTecnico i){almacen(i.almacenHuella());nueva.executeWithoutResult(tx->diario.estado(registro(i),Estado.INCIERTO.name(),true,"IO_SIN_CONFIRMAR",false));}
    /** Sólo intentos anteriores ya retirados pero cuyo put fue incierto: podría aparecer un objeto tardío. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void revisarAnteriores(PropietarioBinario org,UUID operacion,String almacenHuella,Retiro retiro){
        bloquear(org,operacion);almacen(almacenHuella);
        for(var i:intentos.deOperacion(dueno(org),operacion))if(almacenHuella.equals(i.almacenHuella())&&i.escrituraIncierta()&&Estado.RETIRADO_VERIFICADO.name().equals(i.estado()))
            nueva.executeWithoutResult(tx->resolver(i,retiro));
    }
    /** La comprobación no espera a una TX padre activa ni elimina bytes que todavía va a vincular. */
    public boolean completar(PropietarioBinario org,UUID operacion,String almacenHuella,Retiro retiro){
        exigir(org,operacion);almacen(almacenHuella);
        return Boolean.TRUE.equals(nueva.execute(tx->{
            if(!diario.intentarBloqueo(identidad(org,operacion)))return false;
            UUID dueno=diario.dueno(org.organizacion(),org.caso());
            for(var i:diario.deOperacion(dueno,operacion))if(almacenHuella.equals(i.almacenHuella()))resolver(i,retiro);return true;
        }));
    }
    private void resolver(Intento i,Retiro retiro){
        if(diario.vinculado(i.dueno(),i.clave())){
            diario.estado(i,Estado.VINCULADO.name(),false,"VINCULO_CONFIRMADO",true);return;
        }
        // Una recepción todavía sin QC/vínculo comercial tiene un propietario durable.
        // No se transforma la tenencia PREPARADA en una escritura verificada.
        if(diario.custodiado(i.dueno(),i.operacion(),i.numero()))return;
        if(Estado.RETIRADO_VERIFICADO.name().equals(i.estado())&&!i.escrituraIncierta())return;
        boolean retirado;
        try{retirado=retiro.retirarYVerificar(vista(i));}catch(RuntimeException fallo){retirado=false;}
        diario.estado(i,retirado?Estado.RETIRADO_VERIFICADO.name():Estado.PENDIENTE_RETIRO.name(),
                i.escrituraIncierta(),retirado?"AUSENCIA_OBSERVADA":"RETIRO_SIN_CONFIRMAR",retirado);
    }
    /** Mantenimiento técnico de arranque; cursor evita que un pendiente persistente bloquee otros. */
    public void recuperarPendientes(String almacenHuella,Retiro retiro){
        almacen(almacenHuella);
        long desde=0;
        while(true){
            long cursor=desde;var lote=nueva.execute(tx->diario.pendientesDespues(almacenHuella,cursor,128));
            if(lote==null||lote.isEmpty())return;
            var visitados=new HashSet<String>();
            for(var i:lote){desde=Math.max(desde,i.id());String k=i.dueno()+":"+i.operacion();
                if(visitados.add(k))completar(vista(i).propietario(),i.operacion(),almacenHuella,retiro);}
        }
    }
    private static void exigir(PropietarioBinario org,UUID operacion){if(org==null||operacion==null)throw new IllegalArgumentException("Identidad técnica de salida no válida.");}
    private static IntentoTecnico vista(Intento i){return new IntentoTecnico(i.id(),PropietarioBinario.de(i.organizacion(),i.caso()),i.operacion(),i.numero(),i.fuente(),i.clave(),i.extension(),i.contentType(),i.huella(),i.bytes(),i.estado(),i.escrituraIncierta(),i.ultimaVerificacion(),i.resultadoTecnico(),i.almacenHuella());}
    private Intento registro(IntentoTecnico i){return new Intento(i.id(),i.propietario().organizacion(),i.operacion(),i.numero(),i.fuente(),i.clave(),i.extension(),i.contentType(),i.huella(),i.bytes(),i.estado(),i.escrituraIncierta(),i.ultimaVerificacion(),i.resultadoTecnico(),i.almacenHuella(),diario.dueno(i.propietario().organizacion(),i.propietario().caso()),i.propietario().caso());}
    private UUID dueno(PropietarioBinario p){return intentos.dueno(p.organizacion(),p.caso());}
    private static void fuente(String fuente){if(fuente==null||fuente.isBlank()||fuente.length()>2000)throw new IllegalArgumentException("La fuente técnica debe identificar la misma salida autorizada.");}
    private static void almacen(String hash){if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("No se acreditó la identidad técnica del almacén.");}
    private static void mismoAlmacen(Intento i,String hash){if(!hash.equals(i.almacenHuella()))throw new ConflictoException("La salida pertenece a otro almacén o su identidad no está acreditada; no se repetirá I/O en este namespace.");}
    private static void mismaFuente(Intento i,String fuente){fuente(fuente);if(!i.fuente().equals(fuente))throw new ConflictoException("La salida corresponde a otra versión o finalidad; vuelve a comprobar su origen.");}
}
