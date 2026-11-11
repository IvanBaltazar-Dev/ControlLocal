package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.Transicionable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Set;

/**
 * Foto de la galeria de una propiedad: el binario vive en el almacen
 * (S3/disco). El original y su referencia permanecen privados e inmutables.
 * La primera foto activa por (orden,id) es la portada; retirar conserva fila,
 * bytes y antecedente. Los topes pertenecen al caso de uso.
 */
@Entity
@Table(name = "foto_propiedad")
public class FotoPropiedad extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "FOTO_PROPIEDAD";
    public static final String ACTIVA = "A";
    public static final String RETIRADA = "I";
    public static final Set<String> ESTADOS = Set.of(ACTIVA, RETIRADA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_foto")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    @Column(name = "clave", nullable = false, length = 200)
    private String clave;

    @Column(name = "archivo_ref", nullable = false)
    private UUID archivoRef;

    @Column(name = "nombre_archivo", nullable = false, length = 150)
    private String nombreArchivo;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "fecha_retiro")
    private OffsetDateTime fechaRetiro;

    @Column(name = "id_persona_registro")
    private Long idPersonaRegistro;

    @Column(name = "id_rol_registro")
    private Long idRolRegistro;

    @Column(name = "rol_registro", length = 20)
    private String rolRegistro;

    @Override public String entidadTipo() { return ENTIDAD_TIPO; }
    @Override public String estadoActual() { return estado; }
    @Override public void transicionarA(String nuevoEstado) {
        if (!ESTADOS.contains(nuevoEstado)) throw new IllegalArgumentException("Estado de foto inválido");
        estado = nuevoEstado;
    }

    public OffsetDateTime getFechaRetiro() { return fechaRetiro; }
    public void setFechaRetiro(OffsetDateTime fechaRetiro) { this.fechaRetiro = fechaRetiro; }
    public Long getIdPersonaRegistro() { return idPersonaRegistro; }
    public void setIdPersonaRegistro(Long idPersonaRegistro) { this.idPersonaRegistro = idPersonaRegistro; }
    public Long getIdRolRegistro() { return idRolRegistro; }
    public void setIdRolRegistro(Long idRolRegistro) { this.idRolRegistro = idRolRegistro; }
    public String getRolRegistro() { return rolRegistro; }
    public void setRolRegistro(String rolRegistro) { this.rolRegistro = rolRegistro; }

    public Long getId() {
        return id;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        if (!java.util.Objects.equals(this.clave, clave)) {
            this.archivoRef = clave == null || clave.isBlank() ? null : UUID.randomUUID();
        }
        this.clave = clave;
    }

    public UUID getArchivoRef() { return archivoRef; }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
