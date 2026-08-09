package com.controllocal.domain.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Map;
import java.util.Set;

/**
 * Catalogo GLOBAL de tipos de documento requeridos para el alquiler: NO es
 * privado del tenant (como distrito), por eso no hereda
 * {@code EntidadDeOrganizacion} y esta declarado global en
 * {@code ArquitecturaTenancyTest}.
 *
 * <p><b>Los ids 1..8 son parte del cable.</b> El enum
 * {@code TipoDocumentoSolicitud} de la v1 mapea codigo &lt;-&gt; id de catalogo
 * por posicion, y el cable viaja con el CODIGO de una letra. Aqui se conserva
 * ese mapeo para traducir en ambos sentidos sin tocar el contrato.
 */
@Entity
@Table(name = "tipo_documento_requerido")
public class TipoDocumentoRequerido {

    /** Codigo del cable -> id del catalogo (orden del enum de la v1). */
    public static final Map<String, Long> ID_POR_CODIGO = Map.of(
            "I", 1L,  // Documento de identidad
            "R", 2L,  // Ficha o constancia RUC
            "V", 3L,  // Vigencia de poder
            "P", 4L,  // Poder de representacion
            "E", 5L,  // Sustento economico
            "G", 6L,  // Documento de garantia
            "D", 7L,  // Declaracion jurada
            "O", 8L); // Otro

    /**
     * Los SEIS tipos que cuentan para el indicador "X/Y" de las pantallas.
     * Ojo: poder de representacion ("P") y otro ("O") se pueden subir pero
     * NO suman al checklist — asi lo hace la v1.
     */
    public static final Set<String> REQUERIDOS = Set.of("I", "R", "V", "E", "G", "D");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_documento_requerido")
    private Long id;

    /** Siempre 'A' (alquiler): la v1 ya lo restringe asi. */
    @Column(name = "tipo_operacion", nullable = false, length = 1)
    private String tipoOperacion;

    @Column(name = "tipo_documento", nullable = false, length = 60)
    private String tipoDocumento;

    @Column(name = "obligatorio", nullable = false)
    private boolean obligatorio;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    /** Traduce el codigo del cable al id del catalogo; null si no existe. */
    public static Long idDe(String codigo) {
        return codigo == null ? null : ID_POR_CODIGO.get(codigo.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** Traduce el id del catalogo al codigo del cable; null si no existe. */
    public static String codigoDe(Long idCatalogo) {
        if (idCatalogo == null) {
            return null;
        }
        return ID_POR_CODIGO.entrySet().stream()
                .filter(entrada -> entrada.getValue().equals(idCatalogo))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipoOperacion() {
        return tipoOperacion;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public boolean isObligatorio() {
        return obligatorio;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
