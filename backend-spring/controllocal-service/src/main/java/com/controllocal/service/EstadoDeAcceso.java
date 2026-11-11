package com.controllocal.service;
import java.time.OffsetDateTime;
/** Restricciones canónicas de cuenta y rol del contexto ya validado por Core. No resuelve autoridad desde JWT. */
public record EstadoDeAcceso(OffsetDateTime sesionesInvalidasDesde,boolean debeCambiarContrasena,
        boolean debeEnrolarMfa,String rolEfectivo) {
    /** Valor de transporte sin restricciones conocidas; nunca sustituye una cuenta o sesión ausente. */
    public static final EstadoDeAcceso SIN_RESTRICCIONES=new EstadoDeAcceso(null,false,false,null);
}
