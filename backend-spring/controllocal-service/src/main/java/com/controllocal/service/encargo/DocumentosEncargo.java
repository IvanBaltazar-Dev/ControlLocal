package com.controllocal.service.encargo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;

/** Puerto técnico de binarios. Core entrega sólo campos permitidos; el adaptador no decide negocio. */
public interface DocumentosEncargo {
    /** Fuente ya autorizada en Core; no se obtiene de indicadores esencial/tipo enviados por el cliente. */
    record Origen(UUID operacion,Actor actor,ContextoSesion sesion,String tipoRecurso,String referencia,
                  String finalidad,String huellaOrigen) { }
    record Campo(String rotulo, String valor) { }
    record Contenido(String titulo, List<Campo> campos, List<String> faltantes) {
        public Contenido { campos=List.copyOf(campos); faltantes=List.copyOf(faltantes); }
    }
    record Guardado(String claveProveedor, String nombre, String contentType, String huella) { }
    record Binario(byte[] contenido, String nombre, String contentType) { }
    Guardado generar(Origen origen, Contenido contenido);
    Guardado guardar(Origen origen, String nombre, String contentType, byte[] contenido);
    Optional<Binario> abrir(String claveProveedor);
}
