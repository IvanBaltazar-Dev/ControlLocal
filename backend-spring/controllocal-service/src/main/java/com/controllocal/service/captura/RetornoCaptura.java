package com.controllocal.service.captura;

import java.util.List;
import java.util.Map;

/** Datos para las plantillas de CAT19; Core no redacta conversación ni envía. */
public record RetornoCaptura(String estado, List<Grupo> grupos, List<String> preguntasPertinentes,
                             List<String> cambios, Revision revision,
                             ReferenciaPrecio referenciaPrecio) {
    public RetornoCaptura {
        grupos = List.copyOf(grupos);
        preguntasPertinentes = List.copyOf(preguntasPertinentes);
        cambios = List.copyOf(cambios);
    }
    public record Grupo(String codigo, String resultado, Map<String, Object> datos,
                         List<String> acciones) {
        public Grupo { datos = Map.copyOf(datos); acciones = List.copyOf(acciones); }
    }
    /** Completitud agrupada; no representa prioridad Radar ni reloj D2. */
    public record Revision(long diasDesdeDeteccion, String atencion, List<String> acciones,
                            java.time.LocalDate pospuestaHasta, String motivoPosposicion) {
        public Revision { acciones = List.copyOf(acciones); }
    }
    /** Sin corpus elegible no existe una cifra: se conserva la preparación útil. */
    public record ReferenciaPrecio(String estado, List<String> faltantes) {
        public ReferenciaPrecio { faltantes = List.copyOf(faltantes); }
    }
}
