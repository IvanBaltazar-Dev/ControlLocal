package com.controllocal.persistence.query;

/**
 * Read-DTO del contador {@code cantidadLocales} del cable: cuantos locales
 * DISTINTOS de un propietario estan en seguimiento dentro del alcance de quien
 * pregunta.
 *
 * <p>"En seguimiento" es la union de dos caminos —tener captacion o tener
 * prospeccion—, asi que el conteo NO es un {@code count} sobre propiedad: un
 * local con ambas cuenta una sola vez. Por eso baja a SQL nativo y no a JPQL
 * (que no tiene UNION).
 */
public interface ConteoPorPropietario {

    /** {@code persona_rol.id} del rol PROPIETARIO (el idPropietario del cable). */
    Long getIdPropietario();

    int getTotal();
}
