package com.controllocal.service.documentos;

import com.controllocal.domain.inmueble.CopiaComercialFoto;
import com.controllocal.persistence.repositorio.CopiaComercialFotoRepository;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** CAT-E33/R14: original privado, copia comercial saneada y linaje verificable, sin IA. */
@Service
public class FotosComercialesService {
    public record FotoSalida(UUID referencia,UUID original,int ancho,int alto,String huella,
                             String versionProceso,boolean exifDepurado,java.time.Instant originalRegistradoEn) { }
    private final FotoPropiedadService fotos;private final ArchivosFoto archivos;
    private final EvaluacionTecnicaFoto evaluador;private final CopiaComercialFotoRepository copias;
    private final ContextosDeActividad contextos;
    private final ComandoIdempotenteRepository locks;private final Documentos json;
    private final FotoPropiedadRepository originales;
    public FotosComercialesService(FotoPropiedadService fotos,ArchivosFoto archivos,
            EvaluacionTecnicaFoto evaluador,CopiaComercialFotoRepository copias,
            ContextosDeActividad contextos,ComandoIdempotenteRepository locks,Documentos json,FotoPropiedadRepository originales){
        this.fotos=fotos;this.archivos=archivos;this.evaluador=evaluador;this.copias=copias;
        this.contextos=contextos;this.locks=locks;this.json=json;this.originales=originales;
    }
    @Transactional(readOnly=true)
    public EvaluacionTecnicaFoto.Evaluacion evaluar(long propiedad,UUID original,Actor actor){
        exigirFoto(propiedad,original,actor);
        var salida=evaluador.evaluar(archivos.original(original,actor)).evaluacion();
        exigirFoto(propiedad,original,actor);return salida;
    }
    /** Todos los fences ordenados antes del primer binario/B0, sin upgrade de lector a escritor. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Map<UUID,FotoSalida> prepararCopias(long propiedad,List<UUID> originales,Actor actor){
        contextos.exigirAgente(actor);
        if(originales==null||originales.stream().anyMatch(Objects::isNull))
            throw new ReglaNegocioException("Identifica las fotografías originales.");
        var ordenados=originales.stream().distinct().sorted().toList();
        if(ordenados.isEmpty())return Map.of();
        for(UUID original:ordenados)exigirFoto(propiedad,original,actor);
        var reutilizables=new HashSet<>(copias.originalesConCopia(actor.idOrganizacion(),ordenados,EvaluacionTecnicaFoto.VERSION));
        if(!new HashSet<>(ordenados).containsAll(reutilizables))
            throw new IllegalStateException("La selección de copias no corresponde al lote autorizado.");
        for(UUID original:ordenados){
            String lock=Idempotencia.huellaExacta("COPIA-FOTO\u0000"+actor.idOrganizacion()+"\u0000"+original);
            long identidad=Long.parseUnsignedLong(lock.substring(0,16),16);
            if(reutilizables.contains(original))locks.bloquearLecturaIdentidad(identidad);
            else locks.bloquearIdentidad(identidad);
        }
        var resultado=new LinkedHashMap<UUID,FotoSalida>();
        for(UUID original:ordenados)resultado.put(original,preparar(propiedad,original,actor,reutilizables.contains(original)));
        return Map.copyOf(resultado);
    }
    private FotoSalida preparar(long propiedad,UUID original,Actor actor,boolean lectura){
        contextos.exigirAgente(actor);var foto=exigirFoto(propiedad,original,actor);
        var fechaOriginal=originales.fechaRegistroOriginal(actor.idOrganizacion(),original)
                .orElseThrow(()->new ReglaNegocioException("La fotografía necesita la procedencia de su registro original."));
        byte[] contenidoOriginal=archivos.original(original,actor);
        String huellaOriginal=EvaluacionTecnicaFoto.huella(contenidoOriginal);
        var previa=copias.findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(actor.idOrganizacion(),
                original,huellaOriginal,EvaluacionTecnicaFoto.VERSION);
        if(previa.isPresent())return salida(previa.get());
        if(lectura)throw new ConflictoException("La copia preparada ya no corresponde al original; vuelve a preparar la pieza.");
        // Mismos bytes/proceso ya saneados: no volver a decodificar y recodificar para descartar el resultado.
        var procesada=evaluador.evaluar(contenidoOriginal);
        String clave=archivos.guardarCopia(propiedad,original,procesada.huella(),procesada.contenido(),actor);
        exigirFoto(propiedad,original,actor);
        var c=CopiaComercialFoto.de(actor.idOrganizacion(),propiedad,foto.id(),original,
                procesada.evaluacion().huellaOriginal(),procesada.huella(),clave,
                procesada.evaluacion().anchoCopia(),procesada.evaluacion().altoCopia(),
                json.objetoCanonico(procesada.evaluacion()),actor.idRolOperativo(),fechaOriginal.toInstant(),contextos.ahora());
        // Ante rollback, el intento durable compensa/verifica o conserva su incidencia recuperable.
        copias.saveAndFlush(c);return salida(c);
    }
    @Transactional(readOnly=true)
    public FotoSalida consultar(UUID referencia,Actor actor){return salida(autorizada(referencia,actor));}
    @Transactional(readOnly=true)
    public byte[] obtener(UUID referencia,Actor actor){
        var c=autorizada(referencia,actor);byte[] bytes=archivos.leerCopia(c.getClave(),actor);
        if(!c.getHuella().equals(EvaluacionTecnicaFoto.huella(bytes)))
            throw new ConflictoException("La copia de la fotografía no conserva su integridad.");
        autorizada(referencia,actor);return bytes;
    }
    private CopiaComercialFoto autorizada(UUID ref,Actor actor){
        contextos.exigirOperador(actor);
        var c=copias.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),Objects.requireNonNull(ref))
                .orElseThrow(()->new NoEncontradoException("Copia de fotografía"));
        exigirFoto(c.getPropiedad(),c.getOriginal(),actor);return c;
    }
    private FotoPropiedadService.FotoDePropiedad exigirFoto(long propiedad,UUID original,Actor actor){
        contextos.exigirOperador(actor);
        if(original==null||propiedad<=0)throw new NoEncontradoException("Fotografía");
        final List<FotoPropiedadService.FotoDePropiedad> galeria;
        try{
            // listar aplica el predicado SQL fresco; no preleer el mismo permiso por EXISTS.
            galeria=fotos.listar(propiedad,actor);
        }catch(NoEncontradoException e){
            // No distinguir una propiedad ajena de una fotografía ausente en esta puerta.
            throw new NoEncontradoException("Fotografía");
        }
        return galeria.stream().filter(f->original.toString().equals(f.clave())).findFirst()
                .orElseThrow(()->new NoEncontradoException("Fotografía"));
    }
    private static FotoSalida salida(CopiaComercialFoto c){return new FotoSalida(c.getReferencia(),c.getOriginal(),
            c.getAncho(),c.getAlto(),c.getHuella(),c.getProceso(),true,c.getOriginalRegistradoEn());}
}
