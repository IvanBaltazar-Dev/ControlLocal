package com.controllocal.model.inmueble;

import java.util.Objects;

/**
 * Catalogo cerrado de distritos donde opera la corredora (tabla distrito).
 * Reemplaza progresivamente el texto libre de local_comercial.distrito.
 */
public class Distrito {

    private Long idDistrito;
    private String nombre;
    private String provincia;
    private boolean activo = true;

    public Distrito() {
    }

    public Distrito(Long idDistrito, String nombre, String provincia, boolean activo) {
        this.idDistrito = idDistrito;
        this.nombre = nombre;
        this.provincia = provincia;
        this.activo = activo;
    }

    public Long getIdDistrito() {
        return idDistrito;
    }

    public void setIdDistrito(Long idDistrito) {
        this.idDistrito = idDistrito;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getProvincia() {
        return provincia;
    }

    public void setProvincia(String provincia) {
        this.provincia = provincia;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return "Distrito{idDistrito=" + idDistrito + ", nombre='" + nombre + "', provincia='" + provincia + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Distrito that)) {
            return false;
        }
        if (idDistrito == null || that.idDistrito == null) {
            return false;
        }
        return Objects.equals(idDistrito, that.idDistrito);
    }

    @Override
    public int hashCode() {
        return idDistrito != null ? Objects.hash(idDistrito) : System.identityHashCode(this);
    }
}
