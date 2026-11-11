package com.controllocal.web.almacen;

import com.controllocal.service.archivo.SeguimientoSalidaBinaria.IntentoTecnico;
import com.controllocal.service.archivo.SeguimientoSalidaBinaria;
import com.controllocal.service.archivo.PropietarioBinario;
import com.controllocal.service.archivo.SeguimientoSalidaBinaria.Estado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Adaptador interno de I/O. El caso de uso autoriza antes y después; una clave no es un permiso. */
@Component
public class SalidaBinariaPersistente {
    private static final Logger LOG=LoggerFactory.getLogger(SalidaBinariaPersistente.class);
    public enum EstadoConsulta { NO_INICIADO,DISPONIBLE,EN_VERIFICACION,RETIRADO_VERIFICADO }
    /** Metadatos esperados: todavía no prueban que el binario exista o esté vinculado. */
    public record Intencion(PropietarioBinario propietario,UUID operacion,int numeroIntento,String namespaceHash,String clave,String huella,long bytes) { }
    @FunctionalInterface public interface Admision { void comprobar(Intencion intencion); }
    /** El propietario compromete su recepción exacta después de preparar139 y antes de cualquier PUT. */
    @FunctionalInterface public interface Custodia { void comprometer(Intencion intencion); }
    public record Guardado(PropietarioBinario propietario,UUID operacion,int numeroIntento,String namespaceHash,String clave,String huella,long bytes) { }
    public record Consulta(EstadoConsulta estado,Guardado guardado) { }
    private final AlmacenDocumentos almacen;
    private final SeguimientoSalidaBinaria seguimiento;
    public SalidaBinariaPersistente(AlmacenDocumentos almacen,SeguimientoSalidaBinaria seguimiento){this.almacen=almacen;this.seguimiento=seguimiento;}

    @Transactional(propagation=Propagation.MANDATORY)
    public Consulta recuperar(PropietarioBinario org,UUID operacion,String fuente,Admision admision){
        exigirAdmision(admision);
        String identidad=almacen.identidadTecnica();
        seguimiento.bloquear(org,operacion);
        seguimiento.revisarAnteriores(org,operacion,identidad,this::retirarYVerificar);
        var actual=seguimiento.consultar(org,operacion,fuente,identidad);
        if(actual.isEmpty())return new Consulta(EstadoConsulta.NO_INICIADO,null);
        var i=actual.get();exigirAlmacen(i,identidad);
        if(Estado.RETIRADO_VERIFICADO.name().equals(i.estado()))return new Consulta(EstadoConsulta.RETIRADO_VERIFICADO,null);
        registrarCallback(org,operacion,identidad);
        try{
            exigirAlmacen(i,almacen.identidadTecnica());
            var b=almacen.abrir(i.clave());
            exigirAlmacen(i,almacen.identidadTecnica());
            if(b.isEmpty()||!coincide(i,b.get())){seguimiento.incierto(i);return new Consulta(EstadoConsulta.EN_VERIFICACION,null);}
        }catch(RuntimeException fallo){
            anotarIncertidumbre(i);return new Consulta(EstadoConsulta.EN_VERIFICACION,null);
        }
        // Una denegación de permiso/capacidad/cuota conserva su clasificación;
        // no es un error del proveedor ni se permite saltarla al recuperar.
        admision.comprobar(intencion(i));
        try{exigirAlmacen(i,almacen.identidadTecnica());seguimiento.verificado(i);return new Consulta(EstadoConsulta.DISPONIBLE,guardado(i));}
        catch(RuntimeException fallo){anotarIncertidumbre(i);return new Consulta(EstadoConsulta.EN_VERIFICACION,null);}
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Guardado guardar(PropietarioBinario org,UUID operacion,String fuente,String extensionServidor,String contentType,byte[] contenido,Admision admision){
        return guardarDentro(org,operacion,fuente,extensionServidor,contentType,contenido,admision,null);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Guardado recibir(PropietarioBinario org,UUID operacion,String fuente,String extensionServidor,String contentType,byte[] contenido,Admision admision,Custodia custodia){
        if(custodia==null)throw new IllegalArgumentException("La recepción exige su propietario durable.");
        return guardarDentro(org,operacion,fuente,extensionServidor,contentType,contenido,admision,custodia);
    }
    private Guardado guardarDentro(PropietarioBinario org,UUID operacion,String fuente,String extensionServidor,String contentType,byte[] contenido,Admision admision,Custodia custodia){
        exigirAdmision(admision);
        if(contenido==null)throw new IllegalArgumentException("La salida requiere su contenido binario.");
        String identidad=almacen.identidadTecnica();
        seguimiento.bloquear(org,operacion);
        seguimiento.revisarAnteriores(org,operacion,identidad,this::retirarYVerificar);
        var p=seguimiento.preparar(org,operacion,fuente,identidad,extensionServidor,contentType,huella(contenido),contenido.length);
        var i=p.intento();exigirAlmacen(i,identidad);registrarCallback(org,operacion,identidad);
        if(custodia!=null)custodia.comprometer(intencion(i));
        // El caso exige admisión humana o recepción correlacionada con su claim/previsión.
        // El diario no conoce planes ni conserva una vía que omita esa admisión.
        admision.comprobar(intencion(i));
        try{
            exigirAlmacen(i,almacen.identidadTecnica());
            var existente=almacen.abrir(i.clave());
            exigirAlmacen(i,almacen.identidadTecnica());
            if(existente.isPresent()){
                if(!coincide(i,existente.get()))throw new IllegalStateException("La salida observada no coincide con el contenido esperado.");
                seguimiento.verificado(i);return guardado(i);
            }
            // Nunca se repite un put de resultado incierto ni se sobrescribe una salida preexistente.
            if(!p.nuevo())throw new IllegalStateException("La salida anterior requiere verificación antes de volver a escribir.");
            exigirAlmacen(i,almacen.identidadTecnica());
            almacen.guardarEnClave(i.clave(),contenido,contentType);
            var confirmada=almacen.abrir(i.clave());
            exigirAlmacen(i,almacen.identidadTecnica());
            if(confirmada.isEmpty()||!coincide(i,confirmada.get()))throw new IllegalStateException("No se confirmó el contenido almacenado.");
            seguimiento.verificado(i);return guardado(i);
        }catch(RuntimeException fallo){
            anotarIncertidumbre(i);
            // No propagar mensajes/cause del SDK, URLs o claves a un DTO o log comercial.
            throw new AlmacenException("No se confirmó la salida binaria; su intento queda disponible para verificación.",null);
        }
    }
    private void anotarIncertidumbre(IntentoTecnico i){
        try{seguimiento.incierto(i);}catch(RuntimeException noActualizado){LOG.warn("Queda pendiente verificar un intento binario durable.");}
    }
    private static Intencion intencion(IntentoTecnico i){return new Intencion(i.propietario(),i.operacion(),i.numero(),i.almacenHuella(),i.clave(),i.huella(),i.bytes());}
    private static Guardado guardado(IntentoTecnico i){return new Guardado(i.propietario(),i.operacion(),i.numero(),i.almacenHuella(),i.clave(),i.huella(),i.bytes());}
    private static void exigirAdmision(Admision admision){if(admision==null)throw new IllegalArgumentException("La salida requiere admisión explícita de su caso autorizado.");}
    private static boolean coincide(IntentoTecnico i,AlmacenDocumentos.ArchivoDescargado b){return b.contenido()!=null&&b.contenido().length==i.bytes()&&huella(b.contenido()).equals(i.huella());}
    private static String huella(byte[] contenido){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));}
        catch(NoSuchAlgorithmException imposible){throw new IllegalStateException("SHA-256 no disponible.");}}
    private boolean retirarYVerificar(IntentoTecnico i){
        try{exigirAlmacen(i,almacen.identidadTecnica());almacen.eliminar(i.clave());
            var actual=almacen.abrir(i.clave());exigirAlmacen(i,almacen.identidadTecnica());return actual.isEmpty();}
        catch(RuntimeException noConfirmado){return false;}
    }
    private static void exigirAlmacen(IntentoTecnico i,String identidad){if(identidad==null||!identidad.equals(i.almacenHuella()))throw new AlmacenException("No se acreditó el namespace de la salida binaria.",null);}
    private void registrarCallback(PropietarioBinario org,UUID operacion,String identidad){
        boolean existe=TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(s->s instanceof Finalizacion f&&f.org.equals(org)&&f.operacion.equals(operacion));
        if(!existe)TransactionSynchronizationManager.registerSynchronization(new Finalizacion(org,operacion,identidad));
    }
    private final class Finalizacion implements TransactionSynchronization {
        private final PropietarioBinario org;private final UUID operacion;private final String identidad;
        private Finalizacion(PropietarioBinario org,UUID operacion,String identidad){this.org=org;this.operacion=operacion;this.identidad=identidad;}
        @Override public void afterCompletion(int estado){
            try{if(identidad.equals(almacen.identidadTecnica()))seguimiento.completar(org,operacion,identidad,SalidaBinariaPersistente.this::retirarYVerificar);}
            catch(RuntimeException noConfirmado){LOG.warn("Un intento binario durable conserva recuperación pendiente.");}
        }
    }
    /** No tarea comercial, scheduler o limpieza de todo el almacén: sólo intentos propios registrados. */
    @EventListener(ApplicationReadyEvent.class)
    public void recuperarAlArranque(){
        try{seguimiento.recuperarPendientes(almacen.identidadTecnica(),this::retirarYVerificar);}
        catch(RuntimeException noConfirmado){LOG.warn("La recuperación técnica de salidas conserva pendientes verificables.");}
    }
}
