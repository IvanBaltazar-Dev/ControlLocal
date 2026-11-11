package com.controllocal.service.materialia.openai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.Objects;
import java.util.function.Consumer;
import com.controllocal.service.presupuesto.ObservacionHttpIa;

/** Frontera técnica de una sola petición. El doble TEST no cambia el destino productivo. */
@FunctionalInterface
public interface TransporteOpenAi {
    Respuesta enviar(HttpRequest solicitud,int maximoEnvoltura,Duration total,ContextoIntento intento,
                     Consumer<ObservacionHttpIa> observador) throws IOException,InterruptedException,TimeoutException;
    /** Sólo identidad técnica servidor; nunca se serializa en el payload. */
    record ContextoIntento(String claveIntento,String proveedor,String modelo,String versionConfiguracion) {
        public ContextoIntento {
            Objects.requireNonNull(claveIntento);Objects.requireNonNull(proveedor);
            Objects.requireNonNull(modelo);Objects.requireNonNull(versionConfiguracion);
        }
        @Override public String toString(){return "ContextoIntento[metadatos internos]";}
    }
    record Respuesta(int estado,@JsonIgnore HttpHeaders cabeceras,@JsonIgnore byte[] cuerpo) {
        public Respuesta { cuerpo=cuerpo.clone(); }
        @Override @JsonIgnore public byte[] cuerpo(){return cuerpo.clone();}
        @Override public String toString(){return "RespuestaEdicion[estado="+estado+",contenido privado]";}
    }
}
