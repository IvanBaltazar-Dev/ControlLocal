package com.controllocal.web.almacen;

import com.controllocal.service.archivo.PropietarioBinario;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.presupuesto.EvaluacionesIaB0;
import com.controllocal.service.presupuesto.ArchivosEvaluacionIa;
import com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion;
import com.controllocal.service.presupuesto.PresupuestoIaService;
import com.controllocal.service.archivo.SeguimientoSalidaBinaria;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Adaptador privado: mismo bridge/almacén/cuota, sin rutas ni motor de evaluación. */
@Component
public class ArchivosEvaluacionIaB0 implements ArchivosEvaluacionIa {
    private final PresupuestoIaService presupuesto;private final SeguimientoSalidaBinaria seguimiento;
    private final EvaluacionesIaB0 casos;private final UsoAlmacenamientoB0 uso;
    private final SalidaBinariaPersistente salidas;private final CuotaArchivosB0 cuota;
    private final AlmacenDocumentos almacen;private final TransactionTemplate tx;
    public ArchivosEvaluacionIaB0(EvaluacionesIaB0 casos,UsoAlmacenamientoB0 uso,
        SalidaBinariaPersistente salidas,CuotaArchivosB0 cuota,AlmacenDocumentos almacen,PlatformTransactionManager gestor,
        PresupuestoIaService presupuesto,SeguimientoSalidaBinaria seguimiento){
        this.presupuesto=presupuesto;this.seguimiento=seguimiento;this.casos=casos;this.uso=uso;this.salidas=salidas;this.cuota=cuota;this.almacen=almacen;this.tx=new TransactionTemplate(gestor);
    }
    public UUID guardarOriginal(UUID caso,UUID archivo,String mime,byte[] contenido,SesionPlataforma sesion){
        fueraSql();validarFoto(mime,contenido);byte[] bytes=contenido.clone();String hash=EvaluacionTecnicaFoto.huella(bytes);
        return tx.execute(t->{
            var p=casos.exigirOriginal(caso,archivo,bytes.length,hash,sesion);namespace(p.namespace());
            var dueno=new PropietarioBinario.CasoEvaluacion(caso);String fuente=fuente(caso,archivo,p.huella(),hash);
            var admision=cuota.admisionOriginalEvaluacion(caso,archivo,sesion,casos);
            var previa=salidas.recuperar(dueno,archivo,fuente,admision);
            var g=exigirOEscribir(previa,()->salidas.guardar(dueno,archivo,fuente,extension(mime),mime,bytes,admision));
            casos.verificarArchivo(caso,archivo);cuota.confirmarRecepcion(new UsoAlmacenamientoB0.RefEvaluacion(caso,archivo),g);
            casos.exigirOriginal(caso,archivo,bytes.length,hash,sesion);return archivo;
        });
    }
    /** Resultado del claim original: conserva bytes/costo, no inicia otra llamada ni aprueba QC. */
    @Override public UUID recibir(ReferenciaEvaluacion ref,UUID archivo,String mime,byte[] contenido){
        fueraSql();validarFoto(mime,contenido);byte[] bytes=contenido.clone();String hash=EvaluacionTecnicaFoto.huella(bytes);
        var original=presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow(()->new ConflictoException("Falta el Gen durable."));
        var descriptor=original.descriptor();exigirDescriptor(ref,descriptor,archivo,mime,hash,bytes.length);
        return tx.execute(t->{
            seguimiento.bloquear(new PropietarioBinario.CasoEvaluacion(ref.caso()),descriptor.operacionIo());
            if(!original.equals(presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow()))throw new ConflictoException("La respuesta cambió.");
            casos.exigirArchivoResultado(ref.caso(),archivo);
            var p=casos.contextoArchivo(ref.caso());namespace(p.namespace());
            var reserva=UsoAlmacenamientoB0.referenciaEvaluacion(ref.caso(),ref.operacion());
            var limite=uso.limiteRecepcion(reserva);
            if(bytes.length>limite.maximoBytesContenido())throw new ConflictoException("La salida excede la previsión.");
            UUID b0=casos.identidadOperacion(ref.caso(),ref.operacion());
            UUID io=UUID.nameUUIDFromBytes(("BROX-EVALUACION-ARCHIVO\u0000"+ref.caso()+"\u0000"+ref.operacion()+"\u0000"+archivo).getBytes(StandardCharsets.UTF_8));
            var dueno=new PropietarioBinario.CasoEvaluacion(ref.caso());String fuente=fuente(ref.caso(),archivo,p.huella(),hash);
            var admision=cuota.admisionEvaluacion(reserva,b0,archivo);
            var previa=salidas.recuperar(dueno,io,fuente,admision);
            var g=exigirOEscribir(previa,()->salidas.recibir(dueno,io,fuente,extension(mime),mime,bytes,admision,
                i->casos.comprometerArchivo(ref.caso(),archivo,b0,i.operacion(),i.numeroIntento(),i.huella(),i.bytes())));
            casos.verificarArchivo(ref.caso(),archivo);cuota.confirmarRecepcion(reserva,g);return archivo;
        });
    }
    @Override public byte[] leerOriginalParaProcesar(UUID caso,SesionPlataforma sesion){
        fueraSql();var a=casos.exigirOriginalParaProcesar(caso,sesion);byte[] contenido=leerVerificado(a);
        if(!a.equals(casos.exigirOriginalParaProcesar(caso,sesion)))
            throw new ConflictoException("El original cambió durante su lectura privada.");
        return contenido;
    }
    @Override public Optional<byte[]> recuperarResultadoParaProcesar(ReferenciaEvaluacion ref,UUID archivo,String mime,
                                                                  String huella,SesionPlataforma sesion){
        fueraSql();
        if(archivo==null||huella==null||!huella.matches("[0-9a-f]{64}")||!("image/png".equals(mime)||"image/jpeg".equals(mime)))
            throw new ReglaNegocioException("Conserva la referencia exacta de la respuesta medida.");
        casos.contextoParaProcesar(ref.caso(),sesion);
        var original=presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow(()->new ConflictoException("Falta el Gen durable."));
        var descriptor=original.descriptor();exigirDescriptor(ref,descriptor,archivo,mime,huella,descriptor==null?0:descriptor.bytes());
        boolean recuperada=Boolean.TRUE.equals(tx.execute(t->{
            seguimiento.bloquear(new PropietarioBinario.CasoEvaluacion(ref.caso()),descriptor.operacionIo());
            if(!original.equals(presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow()))throw new ConflictoException("La respuesta cambió.");
            casos.exigirArchivoResultado(ref.caso(),archivo);
            var p=casos.contextoParaProcesar(ref.caso(),sesion);namespace(p.namespace());
            UUID b0=casos.identidadOperacion(ref.caso(),ref.operacion());
            var reserva=UsoAlmacenamientoB0.referenciaEvaluacion(ref.caso(),ref.operacion());
            UUID io=UUID.nameUUIDFromBytes(("BROX-EVALUACION-ARCHIVO\u0000"+ref.caso()+"\u0000"+ref.operacion()+"\u0000"+archivo).getBytes(StandardCharsets.UTF_8));
            var previa=salidas.recuperar(new PropietarioBinario.CasoEvaluacion(ref.caso()),io,
                    fuente(ref.caso(),archivo,p.huella(),huella),cuota.admisionEvaluacion(reserva,b0,archivo));
            if(previa.guardado()==null){
                if(previa.estado()==SalidaBinariaPersistente.EstadoConsulta.NO_INICIADO)return false;
                throw new ConflictoException("El mismo intento requiere recuperación; no se repite PUT ni generación.");
            }
            var g=previa.guardado();
            if(!huella.equals(g.huella()))
                throw new ConflictoException("La recuperación conserva otra respuesta.");
            casos.verificarArchivo(ref.caso(),archivo);cuota.confirmarRecepcion(reserva,g);
            casos.contextoParaProcesar(ref.caso(),sesion);return true;
        }));
        if(!recuperada)return Optional.empty();
        var a=casos.exigirResultadoParaProcesar(ref,archivo,sesion);byte[] contenido=leerVerificado(a);
        if(!huella.equals(a.huella())||!mime.equals(a.mime())||!a.equals(casos.exigirResultadoParaProcesar(ref,archivo,sesion)))
            throw new ConflictoException("El resultado cambió durante su lectura privada.");
        return Optional.of(contenido);
    }
    @Override public void liberarSalidaSinBinario(ReferenciaEvaluacion ref){
        fueraSql();var r=presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow(()->new ConflictoException("Falta la respuesta original."));
        if(r.descriptor()==null)throw new ConflictoException("Falta el descriptor del resultado.");
        tx.executeWithoutResult(t->{
            seguimiento.bloquear(new PropietarioBinario.CasoEvaluacion(ref.caso()),r.descriptor().operacionIo());
            if(!r.equals(presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow()))throw new ConflictoException("La respuesta cambió.");
            uso.liberarEvaluacionSinBinario(ref,r);
        });
    }
    private void exigirDescriptor(ReferenciaEvaluacion ref,com.controllocal.service.presupuesto.DescriptorSalidaEvaluacion d,
                                   UUID archivo,String mime,String hash,long bytes){
        UUID io=UUID.nameUUIDFromBytes(("BROX-EVALUACION-ARCHIVO\u0000"+ref.caso()+"\u0000"+ref.operacion()+"\u0000"+archivo).getBytes(StandardCharsets.UTF_8));
        if(d==null||d.huella()==null||!archivo.equals(d.archivo())||!io.equals(d.operacionIo())||!hash.equals(d.huella())
                ||bytes!=d.bytes()||!mime.equals(d.mime()))throw new ConflictoException("La recepción exige el descriptor original del Gen.");
    }
    public byte[] leerMuestra(UUID caso,UUID archivo,SesionPlataforma sesion){
        fueraSql();casos.exigirLecturaMuestra(caso,sesion);var a=casos.archivoInterno(caso,archivo);
        byte[] contenido=leerVerificado(a);casos.exigirLecturaMuestra(caso,sesion);return contenido;
    }
    private byte[] leerVerificado(EvaluacionesIaB0.ArchivoMuestra a){
        if(!"VERIFICADA".equals(a.estado()))throw new NoEncontradoException("Muestra verificada");
        namespace(a.namespace());var b=almacen.abrir(a.clave()).orElseThrow(()->new NoEncontradoException("Muestra"));
        namespace(a.namespace());
        if(b.contenido()==null||b.contenido().length!=a.bytes()||!EvaluacionTecnicaFoto.huella(b.contenido()).equals(a.huella())
            ||!Objects.equals(a.mime(),b.contentType()))throw new ConflictoException("La muestra no conserva su contenido.");
        return b.contenido().clone();
    }
    private SalidaBinariaPersistente.Guardado exigirOEscribir(SalidaBinariaPersistente.Consulta previa,
            java.util.function.Supplier<SalidaBinariaPersistente.Guardado> escribir){
        if(previa.guardado()!=null)return previa.guardado();
        if(previa.estado()!=SalidaBinariaPersistente.EstadoConsulta.NO_INICIADO)
            throw new ConflictoException("El mismo intento requiere recuperación; no se repite PUT.");
        return escribir.get();
    }
    private static String fuente(UUID caso,UUID archivo,String plan,String contenido){return "EVALUACION-IA:"+caso+":"+archivo+":"+plan+":"+contenido;}
    private void namespace(String esperado){if(!Objects.equals(esperado,almacen.identidadTecnica()))throw new ConflictoException("El namespace no corresponde al plan.");}
    private static String extension(String mime){return "image/png".equals(mime)?"png":"jpg";}
    private static void validarFoto(String mime,byte[] bytes){
        if(bytes==null||bytes.length==0||!("image/png".equals(mime)||"image/jpeg".equals(mime)))
            throw new ReglaNegocioException("Conserva la fotografía y su formato conocido.");
        boolean png=bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71
            &&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10;
        boolean jpg=bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
        if("image/png".equals(mime)?!png:!jpg)throw new ReglaNegocioException("El formato no coincide con los bytes.");
    }
    private static void fueraSql(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La entrada requiere una transacción corta propia.");}
}
