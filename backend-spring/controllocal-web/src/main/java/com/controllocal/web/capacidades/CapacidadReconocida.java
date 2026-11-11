package com.controllocal.web.capacidades;

import java.lang.annotation.*;

/** Enlace explícito entre un adaptador real y sus IDs Core; no registra rutas arbitrarias. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CapacidadReconocida {
    String[] value();
    /** Sólo la confirmación común: selecciona el efecto durable autorizado, nunca un ID del cuerpo. */
    boolean porActuacion() default false;
}
