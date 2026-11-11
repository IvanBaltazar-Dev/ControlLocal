package com.controllocal.service.captura;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.captura.MotorDeCaptura.MetadatoRespuesta;
import com.controllocal.service.captura.MotorDeCaptura.Respuesta;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AtributosDeEncargo;
import com.controllocal.service.soporte.AtributosGobernados;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/** El mismo contrato de respuesta al guardar borrador y al corregir un dato físico. */
@Component
public final class ValidadorRespuestaCaptura {
    private final AtributosGobernados gobierno;
    private final AtributosDeEncargo condiciones;
    private final GobiernoCatalogoCapturaService catalogos;
    private final CatalogoAtributoRepository definiciones;
    public ValidadorRespuestaCaptura(AtributosGobernados gobierno,AtributosDeEncargo condiciones,
                                     GobiernoCatalogoCapturaService catalogos,CatalogoAtributoRepository definiciones){
        this.gobierno=gobierno;this.condiciones=condiciones;this.catalogos=catalogos;
        this.definiciones=definiciones;
    }
    public MetadatoRespuesta validar(Actor actor,String clave,Respuesta respuesta){
        if(clave==null||clave.isBlank()||!clave.equals(clave.trim()))
            throw new ReglaNegocioException("La clave de la respuesta debe ser canónica.");
        if(respuesta==null||respuesta.estado()==null)
            throw new ReglaNegocioException("Cada respuesta debe declarar el estado del dato.");
        if(respuesta.estado()==SuficienciaInmueble.EstadoDato.CONOCIDO&&(respuesta.valor()==null||respuesta.valor().isBlank()))
            throw new ReglaNegocioException("Una respuesta conocida requiere su valor.");
        if(respuesta.noSabe()&&respuesta.estado()!=SuficienciaInmueble.EstadoDato.FALTANTE)
            throw new ReglaNegocioException("No saber conserva un dato faltante, sin valor conocido.");
        if(respuesta.estado()!=SuficienciaInmueble.EstadoDato.CONOCIDO&&respuesta.valor()!=null&&!respuesta.valor().isBlank())
            throw new ReglaNegocioException("Un estado sin valor conocido no admite un valor oculto.");
        if(respuesta.estado()==SuficienciaInmueble.EstadoDato.NO_APLICABLE&&(respuesta.evidenciaRef()==null||respuesta.evidenciaRef().isBlank()))
            throw new ReglaNegocioException("No aplicable requiere su sustento.");
        if(clave.startsWith("contexto.")&&respuesta.estado()==SuficienciaInmueble.EstadoDato.CONOCIDO)
            valorAplicabilidad(clave,respuesta.valor());
        return new MetadatoRespuesta(respuesta.estado(),respuesta.evidenciaRef(),respuesta.concepto(),respuesta.referenciaCatalogo(),
                versionDeRespuesta(actor,clave,respuesta.versionDefinicion()),respuesta.observadoEn(),respuesta.noSabe());
    }
    public static SuficienciaInmueble.Aplicabilidad valorAplicabilidad(String clave,String valor) {
        if(!clave.startsWith("contexto.")||!SuficienciaInmueble.COORDENADAS_APLICABILIDAD.contains(clave.substring(9)))
            throw new ReglaNegocioException("Coordenada de aplicabilidad no reconocida.");
        try { return SuficienciaInmueble.Aplicabilidad.valueOf(valor.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch(IllegalArgumentException error) { throw new ReglaNegocioException("La aplicabilidad admite APLICA, NO_APLICA o NO_PRECISADA."); }
    }

    /** Una corrección de contexto no reutiliza el sustento de la declaración anterior. */
    public SuficienciaInmueble.Contexto reconciliarContexto(SuficienciaInmueble.Contexto anterior,
            SuficienciaInmueble.Contexto calculado,SuficienciaInmueble.Contexto declarado,
            Map<String,Respuesta> respuestas,Map<String,MetadatoRespuesta> metadatos) {
        var flags=new LinkedHashMap<>(calculado.aplicabilidad());
        for(String clave:flags.keySet())if(!SuficienciaInmueble.COORDENADAS_APLICABILIDAD.contains(clave))
            throw new ReglaNegocioException("Coordenada de aplicabilidad no reconocida: "+clave);
        for(var entrada:respuestas.entrySet())if(entrada.getKey().startsWith("contexto.")) {
            String clave=entrada.getKey().substring(9);var respuesta=entrada.getValue();
            var valor=respuesta.estado()==SuficienciaInmueble.EstadoDato.FALTANTE?null:
                    respuesta.estado()==SuficienciaInmueble.EstadoDato.NO_APLICABLE?
                            SuficienciaInmueble.Aplicabilidad.NO_APLICA:valorAplicabilidad(entrada.getKey(),respuesta.valor());
            if(declarado!=null&&declarado.aplicabilidad().containsKey(clave)
                    &&declarado.aplicabilidad().get(clave)!=valor)
                throw new ReglaNegocioException("La respuesta y el contexto deben coincidir: "+clave);
            if(valor==null)flags.remove(clave);else flags.put(clave,valor);
        }
        var previos=anterior==null?Map.<String,SuficienciaInmueble.Aplicabilidad>of():anterior.aplicabilidad();
        for(String clave:SuficienciaInmueble.COORDENADAS_APLICABILIDAD)
            if(!java.util.Objects.equals(previos.get(clave),flags.get(clave))&&!respuestas.containsKey("contexto."+clave))
                metadatos.put("contexto."+clave,new MetadatoRespuesta(
                        flags.containsKey(clave)?SuficienciaInmueble.EstadoDato.CONOCIDO:SuficienciaInmueble.EstadoDato.FALTANTE,
                        null,null,null,null,null,false));
        return new SuficienciaInmueble.Contexto(calculado.tipoPropiedad(),calculado.usoOfrecido(),calculado.objetoOfrecido(),
                calculado.operacion(),flags,calculado.prestacionesOfrecidas(),calculado.jurisdiccion());
    }
    public record OpcionRevisada(String valorConocido,MetadatoRespuesta metadato) { }
    public OpcionRevisada validarZonificacion(long org,String valor,MetadatoRespuesta meta,SuficienciaInmueble.Contexto contexto){
        if(valor==null)return new OpcionRevisada(null,meta);
        var r=catalogos.validarOpcion(org,"zonificacion",valor,meta==null?null:meta.referenciaCatalogo(),contexto==null?null:contexto.jurisdiccion());
        if(r.valida()&&meta!=null&&meta.versionDefinicion()!=null
                &&!meta.versionDefinicion().equals(String.valueOf(r.versionDefinicion())))
            throw new ConflictoException("La versión declarada no coincide con el catálogo de zonificación vigente.");
        if(r.valida())return new OpcionRevisada(valor,new MetadatoRespuesta(SuficienciaInmueble.EstadoDato.CONOCIDO,
                meta==null?null:meta.evidenciaRef(),meta==null?null:meta.concepto(),r.referenciaCatalogo(),String.valueOf(r.versionDefinicion()),
                meta==null?null:meta.observadoEn(),false));
        return new OpcionRevisada(null,new MetadatoRespuesta(SuficienciaInmueble.EstadoDato.FALTANTE,
                meta==null?null:meta.evidenciaRef(),meta==null?null:meta.concepto(),meta==null?null:meta.referenciaCatalogo(),
                meta==null?null:meta.versionDefinicion(),meta==null?null:meta.observadoEn(),false,valor,r.motivo()));
    }
    private String versionDeRespuesta(Actor actor,String clave,String esperada){
        if(clave.startsWith("contexto.")){
            String contexto=clave.substring("contexto.".length());
            if(!SuficienciaInmueble.COORDENADAS_APLICABILIDAD.contains(contexto))
                throw new ReglaNegocioException("Coordenada de captura no reconocida: "+clave+".");
            return null;
        }
        if(GuionRegistroPropiedad.esEstructural(clave)||"zonificacion".equals(clave))return null;
        String atributo=GuionRegistroPropiedad.atributoDeMoneda(clave);if(atributo==null)atributo=clave;
        OperacionInmobiliaria operacion=GuionRegistroPropiedad.operacionDe(atributo);String base=GuionRegistroPropiedad.claveBase(atributo);
        CatalogoAtributo definicion=operacion==null?gobierno.definicionDe(actor.idOrganizacion(),base):condiciones.definicionDe(actor.idOrganizacion(),base);
        long version=definicion.getVersionDefinicion();
        if(operacion==null){
            var vigente=(TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    ?definiciones.actualParaCaptura(actor.idOrganizacion(),base):definiciones.bloquearActualParaCaptura(actor.idOrganizacion(),base))
                    .orElseThrow(()->new ConflictoException("La definición de "+clave+" ya no está vigente."));
            version=vigente.getVersion();
        }
        String actual=Long.toString(version);
        if(esperada!=null&&!actual.equals(esperada))throw new ConflictoException("La definición de "+clave+" cambió desde la versión revisada.");
        return actual;
    }
}
