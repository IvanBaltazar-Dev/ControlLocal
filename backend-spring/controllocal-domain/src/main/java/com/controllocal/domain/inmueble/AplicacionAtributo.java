package com.controllocal.domain.inmueble;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * A que tipo de propiedad aplica un atributo del catalogo, y si ahi es
 * obligatorio (fila de {@code catalogo_atributo_tipo}, V48).
 *
 * <p>Es un VALOR, no una entidad: no tiene identidad ni vida fuera del atributo
 * que la contiene, y por eso viaja como {@code @ElementCollection}. Dos
 * aplicaciones con el mismo tipo son la misma cosa — de ahi el equals por
 * {@link #tipoPropiedad}.
 */
@Embeddable
public class AplicacionAtributo {

    /** 'L' local, 'O' oficina, 'D' departamento, 'C' casa, 'T' terreno, 'A' almacen, 'X' otro. */
    @Column(name = "tipo_propiedad", nullable = false, length = 1)
    private String tipoPropiedad;

    /** true = sin este dato el alta de ese tipo no se puede cerrar. */
    @Column(name = "requerido", nullable = false)
    private boolean requerido;

    protected AplicacionAtributo() {
        // JPA
    }

    public AplicacionAtributo(String tipoPropiedad, boolean requerido) {
        this.tipoPropiedad = tipoPropiedad;
        this.requerido = requerido;
    }

    public String getTipoPropiedad() {
        return tipoPropiedad;
    }

    public boolean isRequerido() {
        return requerido;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof AplicacionAtributo aplicacion)) {
            return false;
        }
        return Objects.equals(tipoPropiedad, aplicacion.tipoPropiedad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipoPropiedad);
    }
}
