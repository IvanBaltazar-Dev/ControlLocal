package com.controllocal.service.captura;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.captura.MotorDeCaptura.MetadatoRespuesta;
import com.controllocal.service.captura.SuficienciaInmueble.Accion;
import com.controllocal.service.captura.SuficienciaInmueble.Contexto;
import com.controllocal.service.captura.SuficienciaInmueble.Dato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.captura.SuficienciaInmueble.Evaluacion;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValorLogico;
import com.controllocal.service.soporte.ValoresGobernados;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Única traducción del dato persistido a suficiencia. El llamante autoriza la
 * Propiedad antes de entregarla. El valor lo resuelve LectorPorAutoridad; estos
 * metadatos no eligen otra tabla ni crean ausencias o conceptos desconocidos.
 */
@Component
public final class SuficienciaRegistrada {
    private final LectorPorAutoridad lector;
    private final ObjectMapper mapper;
    private final SuficienciaInmueble suficiencia;
    private final GobiernoCatalogoCapturaService catalogo;

    public SuficienciaRegistrada(LectorPorAutoridad lector, ObjectMapper mapper,
                                 SuficienciaInmueble suficiencia, GobiernoCatalogoCapturaService catalogo) {
        this.lector = lector; this.mapper = mapper; this.suficiencia = suficiencia;
        this.catalogo = catalogo;
    }

    public Evaluacion evaluar(Propiedad propiedad, Accion accion, String operacion,
                               Contexto contextoDeclarado) {
        Contexto contexto = contexto(propiedad, operacion, contextoDeclarado);
        return evaluar(propiedad.getOrganizacionId(), accion, contexto,
                datosSinValidar(propiedad, lector.de(propiedad.getOrganizacionId(), propiedad)));
    }

    public Evaluacion evaluar(long organizacion, Accion accion, Contexto contexto, Map<String, Dato> datos) {
        return suficiencia.evaluar(accion, contexto, validarDatos(organizacion, contexto, datos));
    }

    public Map<String, Dato> validarDatos(long organizacion, Contexto contexto, Map<String, Dato> datos) {
        Dato zonificacion = datos.get("zonificacion");
        if (zonificacion == null || zonificacion.estado() != EstadoDato.CONOCIDO || zonificacion.valor() == null)
            return datos;
        var validacion = catalogo.validarOpcion(organizacion, "zonificacion", zonificacion.valor().toString(),
                zonificacion.referenciaCatalogo(), contexto == null ? null : contexto.jurisdiccion());
        Map<String, Dato> resultado = new LinkedHashMap<>(datos);
        resultado.put("zonificacion", new Dato(zonificacion.valor(), validacion.valida()
                ? EstadoDato.CONOCIDO : EstadoDato.FALTANTE, zonificacion.evidenciaRef(),
                zonificacion.concepto(), validacion.valida() ? validacion.referenciaCatalogo() : null));
        return java.util.Collections.unmodifiableMap(resultado);
    }

    public Contexto contexto(Propiedad propiedad, String operacion, Contexto declarado) {
        Contexto anterior = declarado == null ? contexto(propiedad.getContextoCaptura()) : declarado;
        return new Contexto(propiedad.getTipoInmueble(), propiedad.getUso(),
                anterior == null ? null : anterior.objetoOfrecido(), operacion,
                anterior == null ? Map.of() : anterior.aplicabilidad(),
                anterior == null ? java.util.Set.of() : anterior.prestacionesOfrecidas(),
                anterior == null ? null : anterior.jurisdiccion());
    }

    public Map<String, Dato> datos(Propiedad propiedad) {
        return datos(propiedad, lector.de(propiedad.getOrganizacionId(), propiedad));
    }

    /** Reutiliza la lectura autorizada ya hecha por una ficha o un expediente. */
    public Map<String, Dato> datos(Propiedad propiedad, ValoresGobernados leidos) {
        return validarDatos(propiedad.getOrganizacionId(), contexto(propiedad, null, null),
                datosSinValidar(propiedad, leidos));
    }

    private Map<String, Dato> datosSinValidar(Propiedad propiedad, ValoresGobernados leidos) {
        Map<String, Object> conocidos = new LinkedHashMap<>();
        poner(conocidos, "direccion", propiedad.getDireccion());
        poner(conocidos, "distrito", propiedad.getDistrito());
        poner(conocidos, "interiorUnidad", propiedad.getInteriorUnidad());
        poner(conocidos, "referenciaInterna", propiedad.getReferenciaInterna());
        poner(conocidos, "descripcion", propiedad.getDescripcion());
        leidos.todos().forEach((clave, valor) -> {
            poner(conocidos, clave, escalar(valor));
            if (valor.moneda() != null) poner(conocidos, "moneda:" + clave, valor.moneda());
        });
        return datos(conocidos, metadatos(propiedad.getMetadatosAtributos()));
    }

    /** La misma combinación para el borrador, sin crear Propiedad ficticia. */
    public Map<String, Dato> datos(Map<String, Object> conocidos,
                                    Map<String, MetadatoRespuesta> metadatos) {
        Map<String, Dato> resultado = new LinkedHashMap<>();
        conocidos.forEach((clave, valor) -> {
            MetadatoRespuesta meta = metadatos.get(clave);
            resultado.put(clave, new Dato(valor, meta == null ? EstadoDato.CONOCIDO : meta.estado(),
                    meta == null ? null : meta.evidenciaRef(), meta == null ? null : meta.concepto(),
                    meta == null ? null : meta.referenciaCatalogo()));
        });
        metadatos.forEach((clave, meta) -> resultado.putIfAbsent(clave,
                new Dato(null, meta.estado(), meta.evidenciaRef(), meta.concepto(), meta.referenciaCatalogo())));
        return java.util.Collections.unmodifiableMap(resultado);
    }

    public Contexto contexto(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return null;
        try { return mapper.readValue(json, Contexto.class); }
        catch (Exception error) { throw new IllegalStateException("Contexto de captura persistido inválido.", error); }
    }

    public Map<String, MetadatoRespuesta> metadatos(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try { return mapper.readValue(json, new TypeReference<LinkedHashMap<String, MetadatoRespuesta>>() { }); }
        catch (Exception error) { throw new IllegalStateException("Metadatos de captura persistidos inválidos.", error); }
    }

    public String contextoJson(Contexto contexto) { return json(contexto == null ? Map.of() : contexto); }
    public String metadatosJson(Map<String, MetadatoRespuesta> metadatos) { return json(metadatos); }
    private String json(Object contenido) {
        try { return mapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(contenido); }
        catch (Exception error) { throw new IllegalStateException("No se pudo conservar el contexto de captura.", error); }
    }
    private static void poner(Map<String, Object> destino, String clave, Object valor) {
        if (valor != null && (!(valor instanceof String texto) || !texto.isBlank())) destino.put(clave, valor);
    }
    private static Object escalar(ValorLogico valor) {
        if (valor.numero() != null) return valor.numero();
        if (valor.booleano() != null) return valor.booleano();
        if (valor.fecha() != null) return valor.fecha();
        if (valor.valores() != null) return valor.valores();
        return valor.texto();
    }
}
