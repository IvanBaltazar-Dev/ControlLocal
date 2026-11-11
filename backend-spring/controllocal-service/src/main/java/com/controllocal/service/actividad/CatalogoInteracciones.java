package com.controllocal.service.actividad;

import com.controllocal.service.excepcion.ReglaNegocioException;
import java.util.*;

/** Definición única C4 30/10/2026, independiente del vocabulario histórico de visitas. */
public final class CatalogoInteracciones {
    private CatalogoInteracciones() { }
    public static final String VERSION="C4-COMUNICACION-20261110-1";
    public enum TipoActividad {
        LLAMADA(1,"Llamada"),MENSAJERIA(2,"Mensajería"),REUNION(3,"Reunión");
        private final int codigo;private final String rotulo;
        TipoActividad(int c,String r){codigo=c;rotulo=r;}public int codigo(){return codigo;}public String rotulo(){return rotulo;}
    }
    public enum CanalComunicacion {
        TELEFONO(1,"Teléfono"),WHATSAPP(2,"WhatsApp"),CORREO_ELECTRONICO(3,"Correo electrónico"),SMS(4,"SMS"),
        PRESENCIAL(5,"Presencial"),VIDEOCONFERENCIA(6,"Videoconferencia"),PORTAL_INMOBILIARIO(7,"Portal inmobiliario"),OTRO_IDENTIFICADO(8,"Otro medio identificado");
        private final int codigo;private final String rotulo;
        CanalComunicacion(int c,String r){codigo=c;rotulo=r;}public int codigo(){return codigo;}public String rotulo(){return rotulo;}
    }
    public enum EventoComunicacion {
        LLAMADA_SALIENTE(1,"Llamada saliente"),LLAMADA_ENTRANTE(2,"Llamada entrante"),MENSAJE_ENVIADO(3,"Mensaje enviado"),MENSAJE_RECIBIDO(4,"Mensaje recibido"),ENVIO_FALLIDO(5,"Intento de envío fallido"),REUNION_REALIZADA(6,"Reunión realizada");
        private final int codigo;private final String rotulo;EventoComunicacion(int c,String r){codigo=c;rotulo=r;}public int codigo(){return codigo;}public String rotulo(){return rotulo;}
    }
    public enum ResultadoInteraccion {
        INTERCAMBIO_EFECTIVO(1,"Intercambio efectivo"),SIN_INTERCAMBIO(2,"Sin intercambio"),SIN_RESPUESTA(3,"Sin respuesta"),
        COMUNICACION_FALLIDA(4,"Comunicación fallida"),CONVERSACION_RECHAZADA(5,"Conversación rechazada");
        private final int codigo;private final String rotulo;
        ResultadoInteraccion(int c,String r){codigo=c;rotulo=r;}public int codigo(){return codigo;}public String rotulo(){return rotulo;}
    }
    public record Opcion(int codigo,String valor,String rotulo) { }
    public record Compatibilidad(TipoActividad tipo,Set<CanalComunicacion> canales,Set<EventoComunicacion> eventos,Set<ResultadoInteraccion> resultados) { }
    public record ReferenciaLocal(String valor,String rotulo,String semantica) { }
    public record ValorContextual(String valor,String rotulo) { }
    public record Definicion(String version,String semantica,List<Opcion> tipos,List<Opcion> canales,List<Opcion> eventos,List<Opcion> resultados,List<Compatibilidad> compatibilidades,List<ReferenciaLocal> referenciasLocales,
            List<ValorContextual> finalidadesRestriccionConocidas,List<ValorContextual> relacionesDeclaradasParaRecontacto,
            List<ValorContextual> finalidadesContacto,List<ValorContextual> funcionesContacto) { }
    private static final List<Compatibilidad> REGLAS=List.of(
        new Compatibilidad(TipoActividad.LLAMADA,Set.of(CanalComunicacion.TELEFONO,CanalComunicacion.WHATSAPP,CanalComunicacion.VIDEOCONFERENCIA,CanalComunicacion.OTRO_IDENTIFICADO),
            Set.of(EventoComunicacion.LLAMADA_SALIENTE,EventoComunicacion.LLAMADA_ENTRANTE),Set.of(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,ResultadoInteraccion.SIN_RESPUESTA,ResultadoInteraccion.COMUNICACION_FALLIDA,ResultadoInteraccion.CONVERSACION_RECHAZADA)),
        new Compatibilidad(TipoActividad.MENSAJERIA,Set.of(CanalComunicacion.WHATSAPP,CanalComunicacion.CORREO_ELECTRONICO,CanalComunicacion.SMS,CanalComunicacion.PORTAL_INMOBILIARIO,CanalComunicacion.OTRO_IDENTIFICADO),
            Set.of(EventoComunicacion.MENSAJE_ENVIADO,EventoComunicacion.MENSAJE_RECIBIDO,EventoComunicacion.ENVIO_FALLIDO),Set.of(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,ResultadoInteraccion.SIN_INTERCAMBIO,ResultadoInteraccion.COMUNICACION_FALLIDA,ResultadoInteraccion.CONVERSACION_RECHAZADA)),
        new Compatibilidad(TipoActividad.REUNION,Set.of(CanalComunicacion.PRESENCIAL,CanalComunicacion.VIDEOCONFERENCIA,CanalComunicacion.WHATSAPP,CanalComunicacion.TELEFONO,CanalComunicacion.OTRO_IDENTIFICADO),
            Set.of(EventoComunicacion.REUNION_REALIZADA),Set.of(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,ResultadoInteraccion.CONVERSACION_RECHAZADA)));
    public static Definicion definicion(){return new Definicion(VERSION,
        "Registrar declara comunicación real. Desconocidos permanecen ausentes; una nota o mensaje preparado no acredita comunicación. Un destinatario literal recibe referenciaDeclarada contextual técnica; reutilizarla expresamente vincula el mismo destinatario declarado, sin crear Persona ni comparar nombre o teléfono.",
        Arrays.stream(TipoActividad.values()).map(v->new Opcion(v.codigo(),v.name(),v.rotulo())).toList(),
        Arrays.stream(CanalComunicacion.values()).map(v->new Opcion(v.codigo(),v.name(),v.rotulo())).toList(),
        Arrays.stream(EventoComunicacion.values()).map(v->new Opcion(v.codigo(),v.name(),v.rotulo())).toList(),
        Arrays.stream(ResultadoInteraccion.values()).map(v->new Opcion(v.codigo(),v.name(),v.rotulo())).toList(),REGLAS,
        List.of(new ReferenciaLocal("DESTINATARIO","Mismo destinatario buscado","Sólo este comando y su referencia contextual explícita; no admite IDs ni datos adicionales."),
                new ReferenciaLocal("INTERLOCUTOR","Mismo interlocutor declarado","Sólo este comando y su referencia contextual explícita; sin autorreferencia, ciclos o participantes ausentes.")),
        List.of(new ValorContextual(SeguimientoDeProspeccion.FINALIDAD,"Recontactar al propietario en el ámbito declarado")),
        SeguimientoDeProspeccion.relacionesDeclaradasDeRecontacto().stream().map(v->new ValorContextual(v,switch(v){case "PROPIETARIO"->"Propietario declarado";case "GESTOR_COMERCIAL"->"Gestiona propuestas (declarado)";default->"Representante declarado";})).toList(),
        List.of(new ValorContextual("PROSPECCION_COMERCIAL","Prospección comercial"),new ValorContextual("COORDINACION_ACCESO","Coordinar acceso"),new ValorContextual("INFORMACION_CONTACTO","Obtener información o contacto"),new ValorContextual("OTRA_GESTION","Otra gestión")),
        List.of(new ValorContextual("GESTION_COMERCIAL","Gestiona propuestas o conversaciones comerciales"),new ValorContextual("COORDINACION_ACCESO","Coordina acceso o logística"),new ValorContextual("INFORMACION_CONTACTO","Facilita información o contacto"),new ValorContextual("POR_PRECISAR","Función por precisar")));}
    public static void validar(TipoActividad tipo,CanalComunicacion canal,String medio,Set<EventoComunicacion> eventos,ResultadoInteraccion resultado){
        if(canal==CanalComunicacion.OTRO_IDENTIFICADO&&(medio==null||medio.isBlank()))throw new ReglaNegocioException("Identifique el medio real; Otro no sustituye un canal desconocido.");
        if(REGLAS.stream().noneMatch(r->(tipo==null||r.tipo()==tipo)&&(canal==null||r.canales().contains(canal))
                &&(resultado==null||r.resultados().contains(resultado))&&r.eventos().containsAll(eventos)))
            throw new ReglaNegocioException("Tipo, canal, eventos y resultado de comunicación incompatibles.");
        if(eventos.contains(EventoComunicacion.LLAMADA_ENTRANTE)&&eventos.contains(EventoComunicacion.LLAMADA_SALIENTE))
            throw new ReglaNegocioException("Dos llamadas de direcciones distintas son comunicaciones distintas.");
        if(eventos.contains(EventoComunicacion.ENVIO_FALLIDO)&&(eventos.contains(EventoComunicacion.MENSAJE_ENVIADO)
                ||resultado!=ResultadoInteraccion.COMUNICACION_FALLIDA))
            throw new ReglaNegocioException("El intento fallido no acredita envío efectivo y su resultado conocido es comunicación fallida.");
        if(resultado==ResultadoInteraccion.INTERCAMBIO_EFECTIVO&&(tipo==TipoActividad.MENSAJERIA||eventos.contains(EventoComunicacion.MENSAJE_ENVIADO)||eventos.contains(EventoComunicacion.MENSAJE_RECIBIDO))
                &&!(eventos.contains(EventoComunicacion.MENSAJE_ENVIADO)&&eventos.contains(EventoComunicacion.MENSAJE_RECIBIDO)))
            throw new ReglaNegocioException("El intercambio de mensajería necesita participación conocida en ambos sentidos del mismo asunto.");
        if(tipo==TipoActividad.MENSAJERIA&&resultado==ResultadoInteraccion.CONVERSACION_RECHAZADA&&!eventos.contains(EventoComunicacion.MENSAJE_RECIBIDO))
            throw new ReglaNegocioException("La negativa por mensajería conserva la recepción que la declaró.");
    }
    public static String rotulo(ResultadoInteraccion resultado,Set<EventoComunicacion> eventos){
        if(resultado==null)return null;
        if(resultado!=ResultadoInteraccion.SIN_RESPUESTA)return resultado.rotulo();
        if(eventos.contains(EventoComunicacion.LLAMADA_ENTRANTE))return "Llamada perdida";
        return eventos.contains(EventoComunicacion.LLAMADA_SALIENTE)?"No respondieron":"Sin respuesta";
    }
}
