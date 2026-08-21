package com.controllocal.persistence.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * <b>Una fila del listado universal de propiedades.</b>
 *
 * <h2>Que NO trae, y por que</h2>
 * No trae la operacion ni el precio. No es un olvido: <b>no son de la
 * propiedad</b>. Una propiedad puede tener un encargo de venta y otro de
 * alquiler a la vez, cada uno con su importe, y meterlos en esta proyeccion
 * obligaria a multiplicar la fila —dos filas por la misma propiedad— o a elegir
 * uno de los dos y llamarlo "el precio", que es exactamente la mentira que el
 * modelo universal vino a quitar (D-E4-1).
 *
 * <p>Los encargos se hidratan aparte, para los ids de la pagina ya resuelta:
 * <b>dos consultas por pagina, no N+1</b>. Es el mismo patron con el que el
 * listado heredado hidrata los atributos gobernados.
 *
 * <p>{@code metraje} si esta, y por la misma razon que en el listado heredado:
 * es el unico concepto estructural que un listado necesita para ordenar y
 * filtrar, y eso se hace en SQL antes del LIMIT.
 */
public interface PropiedadListado {

    Long getId();

    String getCodigo();

    /** L, O, D, C, T, A o X. El nombre largo lo pone el cliente. */
    String getTipoPropiedad();

    String getUso();

    String getDireccion();

    String getDistrito();

    BigDecimal getMetraje();

    /** D disponible · N no disponible · I inactiva (contrato heredado). */
    String getEstado();

    Long getIdPropietario();

    /**
     * El <b>representante</b> de la titularidad, no "el propietario". Con
     * copropiedad hay varios y el listado ensena a quien responde por ellos;
     * la ficha los ensena todos con sus cuotas.
     */
    String getPropietarioNombre();

    /** Cuantos titulares vigentes tiene. Con 1 no se dice nada; con 3, si. */
    Long getTitulares();

    /** Se proyecta tal cual la guarda la base; a hora local lo pasa el servicio. */
    OffsetDateTime getFechaRegistro();
}
