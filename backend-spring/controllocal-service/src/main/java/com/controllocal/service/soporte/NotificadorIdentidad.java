package com.controllocal.service.soporte;

import java.time.OffsetDateTime;

/** Frontera privada de correo de identidad; ningún secreto se devuelve al cliente ni al modelo. */
public interface NotificadorIdentidad {
    enum Proposito { ALTA_CORREO, RECUPERACION, CAMBIO_CORREO }
    /** Cuenta ausente sólo antes de crearla mediante su prueba de correo. */
    record Destino(Long idCuenta, String nombreProfesionalDeclarado, String correoPropio) {
        @Override public String toString() { return "Destino[cuenta="+idCuenta+", correo protegido]"; }
    }
    record TokenEmitido(String token, OffsetDateTime expiraEn) {
        @Override public String toString() { return "TokenEmitido[secreto protegido, expiraEn="+expiraEn+"]"; }
    }
    /** El adaptador local no entrega correo; producción exige un transporte demostrado. */
    boolean entregaAlTitular();
    void enviar(Proposito proposito, Destino destino, TokenEmitido token);
}

