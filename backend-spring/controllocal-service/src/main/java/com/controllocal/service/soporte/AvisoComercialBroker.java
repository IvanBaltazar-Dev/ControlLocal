package com.controllocal.service.soporte;

import java.util.*;

/** CAT20.3: referencia mínima en el outbox del hecho; no decide destinatarios ni concede autoridad. */
public final class AvisoComercialBroker {
    private AvisoComercialBroker() { }
    public record Marca(String codigo,String recurso,long referencia,long version,List<String> campos) {
        public Marca {
            if(!Set.of("NB-01","NB-02","NB-03","NB-04","NB-05","NB-06","NB-07","NB-08","NB-09","NB-10","NB-11","NB-12","NB-13","NB-14","NB-15").contains(codigo)
                ||!Set.of("ENCARGO","DEMANDA","COLABORACION","INMUEBLE","ORGANIZACION").contains(recurso)||referencia<=0||version<0)
                throw new IllegalArgumentException("Hecho comercial sin referencia/versiones coherentes.");
            campos=campos==null?List.of():List.copyOf(campos);
        }
    }
    public static Map<String,Object> con(Map<String,?> contenido,Marca marca){
        var datos=new LinkedHashMap<String,Object>(contenido);if(marca!=null)datos.put("avisoBroker",marca);return datos;
    }
    public static Marca encargo(String evento,long id,long version){
        String codigo=switch(evento){
            case "PETICION_ENCARGO_PRESENTADA","PETICION_ENCARGO_REENVIADA"->"NB-01";
            case "ADENDA_PROPUESTA","FIN_ENCARGO_SOLICITADO"->"NB-02";
            case "PRECIO_ENCARGO_AUTORIZADO"->"NB-03";
            case "ENCARGO_FINALIZADO"->"NB-10";
            default->null;
        };
        return codigo==null?null:new Marca(codigo,"ENCARGO",id,version,List.of());
    }
    /** La marca no basta: la lectura comprueba también familia del productor, recurso y permiso actuales. */
    public static boolean productorAdmitido(String codigo,String tipo,String entidad){
        return switch(codigo){
            case "NB-01"->"CAPTACION".equals(entidad)&&Set.of("PETICION_ENCARGO_PRESENTADA","PETICION_ENCARGO_REENVIADA").contains(tipo);
            case "NB-02"->"CAPTACION".equals(entidad)&&Set.of("ADENDA_PROPUESTA","FIN_ENCARGO_SOLICITADO").contains(tipo);
            case "NB-03"->"CAPTACION".equals(entidad)&&"PRECIO_ENCARGO_AUTORIZADO".equals(tipo);
            case "NB-04","NB-05","NB-06"->"OFERTA".equals(entidad)&&"NEGOCIACION_REGISTRADA".equals(tipo);
            case "NB-07","NB-08"->Set.of("EXPEDIENTE_OPERACION","HITO_CONTRACTUAL","COMISION_LIQUIDACION").contains(entidad)&&Set.of("FORMALIZACION_REGISTRADA","COMISION_EVALUADA").contains(tipo);
            case "NB-09"->"COMISION_LIQUIDACION".equals(entidad)&&"COMISION_COBRO_REGISTRADO".equals(tipo);
            case "NB-10"->"CAPTACION".equals(entidad)&&"ENCARGO_FINALIZADO".equals(tipo);
            case "NB-11"->"SOLICITUD_COLABORACION".equals(entidad)&&Set.of("INVITACION_COLABORACION_PREPARADA","COLABORACION_SOLICITADA","COLABORACION_DECIDIDA","COLABORACION_REVOCADA","COLABORACION_LADO_DISPONIBLE").contains(tipo);
            case "NB-13"->"CAPTACION".equals(entidad)&&"ENCARGO_REASIGNADO".equals(tipo);
            case "NB-14"->"RECONFIRMACION".equals(entidad)&&"SOLICITUD_DISPONIBILIDAD_ESCALADA".equals(tipo);
            // No se acepta un enum o payload inventado como propuesta física/valoración publicada.
            case "NB-12","NB-15"->false;
            default->false;
        };
    }
    public static String accion(String codigo){return switch(codigo){
        case "NB-01"->"Revisar la versión presentada del Encargo";
        case "NB-02"->"Revisar la propuesta y sus diferencias";
        case "NB-03"->"Consultar el cambio de precio ya autorizado";
        case "NB-04"->"Revisar la oferta y su siguiente actuación";
        case "NB-05"->"Revisar la contraoferta vinculada";
        case "NB-06"->"Consultar el resultado de la negociación";
        case "NB-07"->"Revisar el expediente o hito informado";
        case "NB-08"->"Revisar la base y los hechos de la comisión";
        case "NB-09"->"Consultar el cobro efectivamente registrado";
        case "NB-10"->"Revisar las actuaciones afectadas por el fin del Encargo";
        case "NB-11"->"Revisar únicamente el lado propio de la colaboración";
        case "NB-13"->"Consultar la reasignación dentro del permiso vigente";
        case "NB-14"->"Revisar la atención de disponibilidad sin confirmar por el responsable";
        default->throw new IllegalArgumentException("El aviso no tiene productor comprobado.");};}
}
