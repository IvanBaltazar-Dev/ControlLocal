package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum ResultadoInteraccion implements CodigoEnum {
    PENDIENTE("P", "Pendiente"),
    INTERESADO("I", "Interesado"),
    NO_INTERESADO("N", "No interesado"),
    SEGUIMIENTO("S", "Seguimiento"),
    DESCARTADO("D", "Descartado"),

    CONTACTADO("CONTACTADO", "Contactado"),
    REUNION_AGENDADA("REUNION_AGENDADA", "Reunion agendada"),
    PROPUESTA_ENVIADA("PROPUESTA_ENVIADA", "Propuesta enviada"),
    ACEPTA_CAPTAR("ACEPTA_CAPTAR", "Acepta captar"),
    NO_ACEPTA("NO_ACEPTA", "No acepta"),
    RECONTACTAR("RECONTACTAR", "Recontactar"),

    DOCS_SOLICITADOS("DOCS_SOLICITADOS", "Documentos solicitados"),
    CONDICIONES_AJUSTADAS("CONDICIONES_AJUSTADAS", "Condiciones ajustadas"),
    PUBLICACION_COORDINADA("PUBLICACION_COORDINADA", "Publicacion coordinada"),
    PROPIETARIO_OBSERVA("PROPIETARIO_OBSERVA", "Propietario observa"),
    LISTO_PARA_PUBLICAR("LISTO_PARA_PUBLICAR", "Listo para publicar"),
    PAUSAR_GESTION("PAUSAR_GESTION", "Pausar gestion"),

    INTERESADO_OPORTUNIDAD("INTERESADO", "Interesado"),
    VISITA_AGENDADA("VISITA_AGENDADA", "Visita agendada"),
    OFERTA_SOLICITADA("OFERTA_SOLICITADA", "Oferta solicitada"),
    NEGOCIANDO("NEGOCIANDO", "Negociando"),
    NO_INTERESADO_COMERCIAL("NO_INTERESADO", "No interesado"),
    DESCARTADO_COMERCIAL("DESCARTADO", "Descartado"),

    BUSQUEDA_LEVANTADA("BUSQUEDA_LEVANTADA", "Busqueda levantada"),
    REQUIERE_OPCIONES("REQUIERE_OPCIONES", "Requiere opciones"),
    NO_RESPONDE("NO_RESPONDE", "No responde"),
    SEGUIMIENTO_CLIENTE("SEGUIMIENTO", "Seguimiento");

    private final String codigo;
    private final String descripcion;

    ResultadoInteraccion(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    @Override
    public String getCodigo() {
        return codigo;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    public static ResultadoInteraccion fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ResultadoInteraccion.class, codigo);
    }

    /**
     * Indica si el resultado mantiene viva la oportunidad y amerita seguimiento.
     * Se comparte entre interacciones y visitas para responder, de forma uniforme,
     * "¿debemos darle seguimiento?".
     */
    public boolean mantieneOportunidadAbierta() {
        return this == PENDIENTE || this == INTERESADO || this == SEGUIMIENTO
                || this == INTERESADO_OPORTUNIDAD || this == VISITA_AGENDADA
                || this == OFERTA_SOLICITADA || this == NEGOCIANDO;
    }

    /**
     * Indica si el resultado implica que el cliente no continua. En ese caso el
     * flujo debe registrar un {@code MotivoNoContinuidad} ligado al origen
     * (interaccion o visita) para cerrar la oportunidad.
     */
    public boolean implicaNoContinuidad() {
        return this == NO_INTERESADO || this == DESCARTADO
                || this == NO_INTERESADO_COMERCIAL || this == DESCARTADO_COMERCIAL;
    }
}
