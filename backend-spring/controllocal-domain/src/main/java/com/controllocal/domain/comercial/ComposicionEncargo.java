package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Corte inmutable de las unidades y cifras expresamente ofrecidas; no prorratea. */
@Entity
@Table(name = "composicion_encargo")
public class ComposicionEncargo extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_composicion_encargo") private Long id;
    @Column(name = "id_captacion", nullable = false) private Long idEncargo;
    @Column(name = "version_contenido", nullable = false) private long versionContenido;
    @Column(name = "unidades", nullable = false, columnDefinition = "text") private String unidades;
    @Column(name = "total_declarado", precision = 18, scale = 4) private BigDecimal totalDeclarado;
    @Column(name = "moneda", length = 3) private String moneda;
    @Column(name = "procedencia", nullable = false, columnDefinition = "text") private String procedencia;
    @Column(name = "registrada_en", nullable = false) private OffsetDateTime registradaEn;
    @Column(name = "id_rol_actor", nullable = false) private Long idActor;
    protected ComposicionEncargo() { }
    public static ComposicionEncargo declarar(long org, long encargo, long version, String unidades,
            BigDecimal total, String moneda, String procedencia, long actor, OffsetDateTime ahora) {
        ComposicionEncargo c=new ComposicionEncargo(); c.setOrganizacionId(org); c.idEncargo=encargo;
        c.versionContenido=version; c.unidades=unidades; c.totalDeclarado=total; c.moneda=moneda;
        c.procedencia=procedencia; c.idActor=actor; c.registradaEn=ahora; return c;
    }
    public Long getId() { return id; }
    public Long getIdEncargo() { return idEncargo; }
    public long getVersionContenido() { return versionContenido; }
    public String getUnidades() { return unidades; }
    public BigDecimal getTotalDeclarado() { return totalDeclarado; }
    public String getMoneda() { return moneda; }
    public String getProcedencia() { return procedencia; }
    public OffsetDateTime getRegistradaEn() { return registradaEn; }
    public Long getIdActor() { return idActor; }
}
