package com.controllocal.service.documentos;

import com.controllocal.service.Actor;
import java.util.UUID;

/** Almacena exclusivamente el binario compuesto y validado; no concede acceso. */
public interface ArchivosPieza {
    record Guardado(String clave,String huella) { }
    java.util.Optional<Guardado> recuperar(UUID pieza,String finalidad,String huellaOrigen,Actor actor);
    Guardado guardar(UUID pieza,CompositorPieza.Binario binario,String finalidad,String huellaOrigen,Actor actor);
    byte[] abrir(String clave,Actor actor);
    /** Sólo bytes del derivado confirmado por el caso IA, con SID actual; no una clave de almacén. */
    byte[] fotoDerivada(UUID operacion,UUID archivo,Actor actor);
}
