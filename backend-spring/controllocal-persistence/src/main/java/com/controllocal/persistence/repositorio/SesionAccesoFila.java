package com.controllocal.persistence.repositorio;
import java.time.Instant;
public record SesionAccesoFila(long id,long idCuenta,long revision,Instant creadaEn,Instant emitidaEn,
        Instant venceEn,boolean revocada,Long idMembresia,Long idRolOperativo,String rolEfectivo,
        String semillaContextos,String contextoRefHash) { }
