package com.controllocal.service.plataforma;

import java.time.Instant;
import java.util.*;

/** CAT-R25: frontera administrativa interna. Sin adaptador acreditado no hay habilitación. */
public interface AcreditacionesTitularPlataforma {
    record Decision(UUID referencia,long otorgante,Long destinatario,String actuacion,String huellaContenido,
                    String evidencia,Instant acreditadaEn) {}
    /** No es una API comercial ni una prueba declarada por el cliente. */
    Optional<Decision> acreditar(UUID referencia);
}
