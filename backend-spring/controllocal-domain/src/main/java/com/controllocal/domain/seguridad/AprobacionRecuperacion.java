package com.controllocal.domain.seguridad;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Una de las dos aprobaciones de una concesion (V38, §9.3).
 *
 * <p><b>La doble aprobacion es estructural, no un {@code CHECK} de textos.</b>
 * La version descartada comparaba dos cadenas de una misma fila, y un operador
 * que escribiera dos nombres distintos pasaba. Aqui cada aprobacion es su
 * propia fila, verificada contra su propio hash de configuracion, y el
 * {@code UNIQUE (id_concesion, identificador_custodio)} impide que una sola
 * persona cubra las dos partes.
 *
 * <p><b>Lo que esto prueba, sin adornos:</b> que se presentaron <b>dos
 * secretos en manos separadas</b>. Ningun control de software prueba que
 * habia dos personas — eso lo sostiene el procedimiento, no el esquema.
 *
 * <p>No guarda ninguna copia ni derivado del secreto presentado: la
 * verificacion ya ocurrio contra el hash de configuracion, y material
 * derivado de un secreto vivo en una tabla legible solo regala un objetivo.
 */
@Entity
@Table(name = "aprobacion_recuperacion")
public class AprobacionRecuperacion extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aprobacion")
    private Long id;

    @Column(name = "id_concesion", nullable = false)
    private Long idConcesion;

    /** El de configuracion. No es clave ajena: no hay tabla de custodios (D-S0-51). */
    @Column(name = "identificador_custodio", nullable = false, length = 60)
    private String identificadorCustodio;

    @Column(name = "aprobado_en", nullable = false)
    private OffsetDateTime aprobadoEn;

    @Column(name = "orden", nullable = false)
    private short orden;

    public Long getId() {
        return id;
    }

    public Long getIdConcesion() {
        return idConcesion;
    }

    public void setIdConcesion(Long idConcesion) {
        this.idConcesion = idConcesion;
    }

    public String getIdentificadorCustodio() {
        return identificadorCustodio;
    }

    public void setIdentificadorCustodio(String identificadorCustodio) {
        this.identificadorCustodio = identificadorCustodio;
    }

    public OffsetDateTime getAprobadoEn() {
        return aprobadoEn;
    }

    public void setAprobadoEn(OffsetDateTime aprobadoEn) {
        this.aprobadoEn = aprobadoEn;
    }

    public short getOrden() {
        return orden;
    }

    public void setOrden(short orden) {
        this.orden = orden;
    }
}
