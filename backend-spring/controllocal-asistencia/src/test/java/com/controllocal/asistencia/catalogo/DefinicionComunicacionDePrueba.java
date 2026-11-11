package com.controllocal.asistencia.catalogo;

import java.util.List;
import java.util.Map;

/** Opciones Core usadas por el corpus lingüístico; no certifica el catálogo ni sus reglas. */
public final class DefinicionComunicacionDePrueba {
    private DefinicionComunicacionDePrueba() { }

    public static Map<String,Object> opciones() {
        return Map.of("version","C4-TEST","tipos",List.of(opcion("LLAMADA","Llamada"),opcion("REUNION","Reunión")),
                "canales",List.of(opcion("TELEFONO","Teléfono"),opcion("WHATSAPP","WhatsApp"),
                        opcion("CORREO_ELECTRONICO","Correo electrónico"),opcion("PORTAL_INMOBILIARIO","Portal inmobiliario")),
                "eventos",List.of(opcion("LLAMADA_SALIENTE","LLAMADA_SALIENTE"),opcion("LLAMADA_ENTRANTE","LLAMADA_ENTRANTE")),
                "resultados",List.of(opcion("INTERCAMBIO_EFECTIVO","Intercambio efectivo"),opcion("SIN_RESPUESTA","Sin respuesta")),
                "compatibilidades",List.of());
    }

    private static Map<String,Object> opcion(String valor,String rotulo) {
        return Map.of("valor",valor,"rotulo",rotulo);
    }
}
