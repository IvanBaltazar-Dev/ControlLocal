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
 * Codigo de respaldo del segundo factor (D-S0-24).
 *
 * <p>Lleva {@code organizacion_id} aunque cuelgue de un factor que ya vive en
 * un tenant: la regla del proyecto es que <b>toda</b> fila privada carga el
 * discriminador, y {@code ArquitecturaTenancyTest} rompe el build si alguna no
 * lo hace. Ahorrarselo en las hijas es como se cuela una consulta que cruza
 * corredoras.
 *
 * <p>{@code identificador} es <b>publico</b> y no es un secreto: es un indice.
 * Lo que protege el codigo son los 80 bits de {@code hashSecreto}. Su papel es
 * localizar <b>una sola fila</b>, para que verificar cueste una derivacion
 * lenta y no ocho.
 */
@Entity
@Table(name = "codigo_respaldo_mfa")
public class CodigoRespaldoMfa extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo")
    private Long id;

    @Column(name = "id_factor", nullable = false)
    private Long idFactor;

    @Column(name = "identificador", nullable = false, length = 8)
    private String identificador;

    /** PBKDF2 con sal, formato de {@code PasswordHasher}. Nunca el codigo. */
    @Column(name = "hash_secreto", nullable = false, length = 255)
    private String hashSecreto;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "usado_en")
    private OffsetDateTime usadoEn;

    public boolean disponible() {
        return usadoEn == null;
    }

    public Long getId() {
        return id;
    }

    public Long getIdFactor() {
        return idFactor;
    }

    public void setIdFactor(Long idFactor) {
        this.idFactor = idFactor;
    }

    public String getIdentificador() {
        return identificador;
    }

    public void setIdentificador(String identificador) {
        this.identificador = identificador;
    }

    public String getHashSecreto() {
        return hashSecreto;
    }

    public void setHashSecreto(String hashSecreto) {
        this.hashSecreto = hashSecreto;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getUsadoEn() {
        return usadoEn;
    }

    public void setUsadoEn(OffsetDateTime usadoEn) {
        this.usadoEn = usadoEn;
    }
}
