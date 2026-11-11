package com.controllocal.web.almacen;

import com.controllocal.service.Actor;
import com.controllocal.service.archivo.PropietarioBinario;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.planes.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Une la admisión Core con el único I/O durable; nunca decide categoría o autoridad en Web. */
@Component
public class CuotaArchivosB0 {
    public record Contexto(Recurso recurso,Actor actor,ContextoSesion sesion){}
    private final UsoAlmacenamientoB0 uso;private final ConfirmacionAlmacenamientoB0 confirmaciones;private final AlmacenDocumentos almacen;
    public CuotaArchivosB0(UsoAlmacenamientoB0 uso,ConfirmacionAlmacenamientoB0 confirmaciones,AlmacenDocumentos almacen){
        this.uso=uso;this.confirmaciones=confirmaciones;this.almacen=almacen;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public SalidaBinariaPersistente.Admision admision(Contexto c){
        exigir(c);
        return i->{
            if(organizacion(i.propietario())!=c.actor().idOrganizacion())throw new ConflictoException("La salida pertenece a otro contexto.");
            var s=solicitud(c,i);var r=uso.reservar(s,c.actor(),c.sesion());
            switch(r.estado()){
                case "RESERVADA"->{if(!uso.iniciar(s,c.actor(),c.sesion()))throw new ConflictoException("La carga cambió antes de su ejecución.");}
                case "ALMACENANDO","INCIERTA","CONFIRMADA"->{/* La recuperación exacta no reclama otro PUT. */}
                default->throw new ConflictoException("La carga original ya no admite esta salida.");
            }
        };
    }
    /** El mismo ledger consume la previsión del job; no crea la reserva exacta del intento. */
    @Transactional(propagation=Propagation.MANDATORY)
    public SalidaBinariaPersistente.Admision admision(Contexto c,RefInterna prevision){
        exigir(c);exigir(c,prevision);
        return i->{
            if(organizacion(i.propietario())!=c.actor().idOrganizacion())throw new ConflictoException("La salida pertenece a otro contexto.");
            var s=new Solicitud(prevision.reserva(),c.recurso(),i.bytes(),i.huella(),identidad(i.namespaceHash(),i.clave()),i.namespaceHash());
            var r=uso.admitirPrevisionParaAlmacenamiento(prevision,s,c.actor(),c.sesion());
            if(!Set.of("ALMACENANDO","INCIERTA","CONFIRMADA").contains(r.estado()))throw new ConflictoException("La previsión no admite esta salida.");
        };
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public SalidaBinariaPersistente.Admision admisionRecepcion(RefInterna prevision,Recurso recurso,java.util.function.Supplier<RecepcionComprobada> prueba){
        if(prevision==null||recurso==null||prueba==null||!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La recepción exige transacción y prueba Core.");
        return i->{
            var p=Objects.requireNonNull(prueba.get());
            if(organizacion(i.propietario())!=prevision.organizacion()||!i.operacion().equals(p.operacionIo())||i.numeroIntento()!=p.numero())throw new ConflictoException("La recepción no corresponde al intento.");
            var s=new Solicitud(prevision.reserva(),recurso,i.bytes(),i.huella(),identidad(i.namespaceHash(),i.clave()),i.namespaceHash());
            var r=uso.admitirRecepcion(prevision,s,p);
            if(!Set.of("ALMACENANDO","INCIERTA","CONFIRMADA").contains(r.estado()))throw new ConflictoException("La previsión no admite la recepción.");
        };
    }
    /** Sólo después de recibir Guardado verificado; se confirma junto al vínculo Core antes de commit. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void confirmar(Contexto c,SalidaBinariaPersistente.Guardado g){
        exigir(c);Objects.requireNonNull(g);
        confirmar(c,g,new RefInterna(c.actor().idOrganizacion(),reserva(c.actor().idOrganizacion(),g.operacion(),g.numeroIntento())));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void confirmar(Contexto c,SalidaBinariaPersistente.Guardado g,RefInterna prevision){
        exigir(c);exigir(c,prevision);confirmarRecepcion(prevision,g);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void confirmarRecepcion(ReferenciaReserva prevision,SalidaBinariaPersistente.Guardado g){
        Objects.requireNonNull(prevision);Objects.requireNonNull(g);
        if(prevision.reserva()==null||!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La confirmación exige su TX Core.");
        UUID reserva=prevision.reserva();
        PropietarioBinario propietario=prevision instanceof RefInterna x?new PropietarioBinario.Organizacion(x.organizacion()):new PropietarioBinario.CasoEvaluacion(((RefEvaluacion)prevision).caso());
        if(!propietario.equals(g.propietario()))throw new ConflictoException("El archivo pertenece a otro dueño.");
        boolean existe=TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(x->x instanceof Confirmacion f&&f.referencia.equals(prevision));
        if(!existe)TransactionSynchronizationManager.registerSynchronization(new Confirmacion(prevision,g));
    }
    /** Callback Core de una intención durable: no requiere reutilizar una sesión humana histórica. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Resultado liberarRetiradaVerificada(RefInterna prevision,AusenciaComprobada ausencia){
        if(prevision==null||prevision.organizacion()<=0||prevision.reserva()==null||!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La retirada requiere su transacción Core y referencia originales.");
        Objects.requireNonNull(ausencia);
        String namespace=almacen.identidadTecnica();
        if(namespace==null||ausencia.identidadObjeto()==null||!ausencia.identidadObjeto().startsWith(namespace+":"))throw new ConflictoException("La retirada no corresponde al namespace actual.");
        String clave=ausencia.identidadObjeto().substring(namespace.length()+1);
        Optional<AlmacenDocumentos.ArchivoDescargado> actual;
        try{actual=almacen.abrir(clave);}catch(RuntimeException fallo){throw new AlmacenException("No se confirmó la ausencia privada del binario retirado.",null);}
        if(actual.isPresent()||!namespace.equals(almacen.identidadTecnica()))throw new ConflictoException("No se comprobó la ausencia del binario retirado.");
        return uso.liberarRetiradaVerificada(prevision,ausencia);
    }
    public static UUID reserva(long org,UUID operacion,int numero){
        return UUID.nameUUIDFromBytes(("BROX-CUOTA-ARCHIVO\u0000"+org+"\u0000"+operacion+"\u0000"+numero).getBytes(StandardCharsets.UTF_8));
    }
    private static String identidad(String namespace,String clave){return namespace+":"+clave;}
    private Solicitud solicitud(Contexto c,SalidaBinariaPersistente.Intencion i){
        return new Solicitud(reserva(organizacion(i.propietario()),i.operacion(),i.numeroIntento()),c.recurso(),i.bytes(),i.huella(),identidad(i.namespaceHash(),i.clave()),i.namespaceHash());
    }
    private static void exigir(Contexto c){
        if(c==null||c.recurso()==null||c.actor()==null||c.sesion()==null||!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La carga exige contexto autorizado y la transacción de su vínculo.");
    }
    private static void exigir(Contexto c,RefInterna ref){if(ref==null||ref.reserva()==null||ref.organizacion()!=c.actor().idOrganizacion())throw new ConflictoException("La previsión pertenece a otro contexto.");}
    private final class Confirmacion implements TransactionSynchronization {
        private final ReferenciaReserva referencia;private final SalidaBinariaPersistente.Guardado g;
        private Confirmacion(ReferenciaReserva referencia,SalidaBinariaPersistente.Guardado g){this.referencia=referencia;this.g=g;}
        @Override public void beforeCommit(boolean readOnly){
            if(readOnly)throw new IllegalStateException("La carga no puede confirmarse en una transacción de lectura.");
            if(!g.namespaceHash().equals(almacen.identidadTecnica()))throw new ConflictoException("El namespace del archivo cambió antes de confirmar.");
            AlmacenDocumentos.ArchivoDescargado actual;
            try{actual=almacen.abrir(g.clave()).orElseThrow(()->new AlmacenException("No se verificó el archivo conservado.",null));}
            catch(RuntimeException fallo){throw new AlmacenException("No se verificó el archivo conservado antes de confirmar.",null);}
            if(!g.namespaceHash().equals(almacen.identidadTecnica())||actual.contenido()==null||actual.contenido().length!=g.bytes()
                    ||!g.huella().equals(huella(actual.contenido())))throw new ConflictoException("El contenido no coincide con la carga autorizada.");
            confirmaciones.confirmar(referencia,new ComprobacionObjeto(identidad(g.namespaceHash(),g.clave()),g.bytes(),g.huella(),"IO-VERIFICADO:"+g.operacion()+":"+g.numeroIntento()),g.clave());
        }
    }
    private static String huella(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException imposible){throw new IllegalStateException(imposible);}}

    @Transactional(propagation=Propagation.MANDATORY)
    public SalidaBinariaPersistente.Admision admisionEvaluacion(RefEvaluacion ref,UUID operacionB0,UUID archivo){
        return i->{
            if(!new PropietarioBinario.CasoEvaluacion(ref.caso()).equals(i.propietario()))throw new ConflictoException("La recepción pertenece a otro caso.");
            var s=uso.solicitudRecepcionEvaluacion(ref,i.bytes(),i.huella(),identidad(i.namespaceHash(),i.clave()),i.namespaceHash());
            uso.admitirRecepcionEvaluacion(ref,operacionB0,archivo,i.operacion(),i.numeroIntento(),s);
        };
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public SalidaBinariaPersistente.Admision admisionOriginalEvaluacion(UUID caso,UUID archivo,
        com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion,
        com.controllocal.service.presupuesto.EvaluacionesIaB0 evaluaciones){
        return i->{
            if(!new PropietarioBinario.CasoEvaluacion(caso).equals(i.propietario()))throw new ConflictoException("El original pertenece a otro caso.");
            uso.reservarOriginalEvaluacion(caso,archivo,i.bytes(),i.huella(),i.namespaceHash(),identidad(i.namespaceHash(),i.clave()),sesion);
            evaluaciones.comprometerArchivo(caso,archivo,null,i.operacion(),i.numeroIntento(),i.huella(),i.bytes());
        };
    }
    private static long organizacion(PropietarioBinario p){
        if(!(p instanceof PropietarioBinario.Organizacion o))throw new ConflictoException("La salida no es organizacional.");
        return o.id();
    }
}
