package com.controllocal.model.inmueble;

import java.time.LocalDateTime;

/**
 * Foto de la galeria de un local comercial. El binario vive en el almacen de archivos;
 * aqui solo se guarda su clave opaca (igual que los documentos del expediente).
 */
public class FotoLocal {

    private Long idFoto;
    private Long idLocal;
    private String clave;
    private String nombreArchivo;
    private int orden;
    private LocalDateTime fechaRegistro;

    public Long getIdFoto() {
        return idFoto;
    }

    public void setIdFoto(Long idFoto) {
        this.idFoto = idFoto;
    }

    public Long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(Long idLocal) {
        this.idLocal = idLocal;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

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

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
