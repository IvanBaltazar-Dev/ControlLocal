package com.controllocal.service.documentos;

import com.controllocal.service.Actor;
import java.util.UUID;

/** Puerto de binarios contextual: la infraestructura no recibe una ruta elegida por el cliente. */
public interface ArchivosFoto {
    byte[] original(UUID referencia,Actor actor);
    String guardarCopia(long propiedad,UUID original,String huella,byte[] contenido,Actor actor);
    byte[] leerCopia(String clave,Actor actor);
}
