package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Detalle por tipo LOCAL/OFICINA de una {@link Propiedad} (composicion,
 * Doc 5 §4): rubro obligatorio para operar comercialmente. Comparte la PK
 * con la propiedad. Los tipos de vivienda/terreno tendran su propio detalle
 * en oleadas siguientes.
 */
@Entity
@Table(name = "detalle_local_comercial")
public class DetalleLocalComercial extends EntidadDeOrganizacion {

    @Id
    @Column(name = "id_propiedad")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_propiedad")
    private Propiedad propiedad;

    @Column(name = "rubro_permitido", nullable = false, length = 120)
    private String rubroPermitido;

    @Column(name = "apto_licencia_funcionamiento")
    private Boolean aptoLicenciaFuncionamiento;

    @Column(name = "carga_electrica_kw", precision = 8, scale = 2)
    private BigDecimal cargaElectricaKw;

    protected DetalleLocalComercial() {
        // requerido por JPA
    }

    DetalleLocalComercial(Propiedad propiedad) {
        this.propiedad = propiedad;
    }

    public Long getId() {
        return id;
    }

    public Propiedad getPropiedad() {
        return propiedad;
    }

    public String getRubroPermitido() {
        return rubroPermitido;
    }

    public void setRubroPermitido(String rubroPermitido) {
        this.rubroPermitido = rubroPermitido;
    }

    public Boolean getAptoLicenciaFuncionamiento() {
        return aptoLicenciaFuncionamiento;
    }

    public void setAptoLicenciaFuncionamiento(Boolean aptoLicenciaFuncionamiento) {
        this.aptoLicenciaFuncionamiento = aptoLicenciaFuncionamiento;
    }

    public BigDecimal getCargaElectricaKw() {
        return cargaElectricaKw;
    }

    public void setCargaElectricaKw(BigDecimal cargaElectricaKw) {
        this.cargaElectricaKw = cargaElectricaKw;
    }
}
