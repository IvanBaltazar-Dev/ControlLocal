package com.controllocal.asistencia.interpretacion;

import com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.*;

/** Traducción de argumentos declarados. No rellena partes, fechas, versiones o permisos. */
public final class ArgumentosComerciales {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_LONG_FOR_INTS);
    private ArgumentosComerciales() { }
    public static Map<String,Object> leer(String texto,OperacionComercial operacion) {
        int inicio=texto.indexOf('{');
        if(inicio>=0) {
            try { return JSON.readValue(texto.substring(inicio),new TypeReference<LinkedHashMap<String,Object>>(){}); }
            catch(Exception invalido) { throw new IllegalArgumentException("Argumentos estructurados inválidos",invalido); }
        }
        var datos=new LinkedHashMap<String,Object>();
        String t=texto.toLowerCase(Locale.ROOT);
        String clave=switch(operacion) {
            case ABRIR_OPORTUNIDAD -> "idEncargo";
            case RECTIFICAR_INTERACCION,VINCULAR_INTERACCION,ANOTAR_OBSERVACION_INTERACCION -> "idInteraccion";
            case CONSULTAR_SEGUIMIENTOS_PROSPECCION,PLANIFICAR_SEGUIMIENTO_PROSPECCION,POSPONER_SEGUIMIENTO_PROSPECCION -> "idProspeccion";
            case PROGRAMAR_VISITA,CONSULTAR_OPORTUNIDAD,REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD -> "idOportunidad";
            case CANCELAR_VISITA,REPROGRAMAR_VISITA,REGISTRAR_RESULTADO_VISITA -> "idVisita";
            case PRESENTAR_OFERTA,REGISTRAR_CONTRAOFERTA,REGISTRAR_ACEPTACION_OFERTA,REGISTRAR_RECHAZO_OFERTA,REGISTRAR_RETIRADA_OFERTA -> "oportunidad";
            case EVALUAR_PUBLICABILIDAD,CONSULTAR_PUBLICACIONES,CONSULTAR_PUBLICACION,CONSULTAR_HISTORIA_PUBLICACION,
                    CREAR_BORRADOR_ANUNCIO,EDITAR_BORRADOR_ANUNCIO,PUBLICAR_ANUNCIO,ACTUALIZAR_ANUNCIO_VISIBLE,
                    PAUSAR_ANUNCIO,REANUDAR_ANUNCIO,FINALIZAR_DIFUSION_ANUNCIO -> "encargo";
            case CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA,CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA,PREPARAR_PIEZA_COMERCIAL -> "encargo";
            default -> null;
        };
        if(clave!=null) {
            String sujeto=clave.equals("idEncargo")||clave.equals("encargo")?"encargo":clave.equals("idInteraccion")?"interaccion":clave.equals("idVisita")?"visita":clave.equals("idProspeccion")?"prospecci[oó]n":"oportunidad";
            var m=Pattern.compile("\\b"+sujeto+"\\s+(\\d+)\\b").matcher(t);
            if(m.find())datos.put(clave,Long.valueOf(m.group(1)));
        }
        if("encargo".equals(clave)) {
            var publicacion=Pattern.compile("\\b(?:publicaci[oó]n|anuncio)\\s+(\\d+)\\b").matcher(t);
            if(publicacion.find())datos.put("publicacion",Long.valueOf(publicacion.group(1)));
        }
        if(operacion==OperacionComercial.PLANIFICAR_SEGUIMIENTO_PROSPECCION || operacion==OperacionComercial.POSPONER_SEGUIMIENTO_PROSPECCION) {
            var seguimiento=Pattern.compile("\\bseguimiento\\s+(\\d+)\\b").matcher(t);
            if(seguimiento.find())datos.put("idSeguimiento",Long.valueOf(seguimiento.group(1)));
        }
        if(operacion==OperacionComercial.CONSULTAR_MATERIAL_IA){
            var referencia=Pattern.compile("\\boperaci[oó]n\\s+([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\b").matcher(t);
            if(referencia.find())datos.put("operacion",referencia.group(1));
        }
        // Sólo campos nombrados explícitamente, no números sueltos ni la versión actual.
        if(Pattern.compile("\\b[A-Za-z][A-Za-z0-9]*\\s*=\\s*-?\\d+,\\d+\\b").matcher(texto).find())
            throw new IllegalArgumentException("Use un decimal inequívoco en los argumentos estructurados");
        var campo=Pattern.compile("\\b([A-Za-z][A-Za-z0-9]*)\\s*=\\s*(?:\"([^\"]*)\"|([^\\s,;]+))").matcher(texto);
        while(campo.find()) {
            String valor=campo.group(2)!=null?campo.group(2):campo.group(3);
            Object v=valor;
            if(campo.group(2)==null && valor.matches("-?(?:0|[1-9]\\d*)")) {
                try { v=Long.valueOf(valor); } catch(NumberFormatException ignored) { }
            } else if(campo.group(2)==null && valor.matches("-?(?:0|[1-9]\\d*)\\.\\d+")) {
                v=new java.math.BigDecimal(valor);
            }
            datos.put(campo.group(1),v);
        }
        return datos;
    }
    public static String serializar(Map<String,Object> datos) {
        try { return JSON.writeValueAsString(datos); }
        catch(Exception imposible) { throw new IllegalArgumentException(imposible); }
    }
}

