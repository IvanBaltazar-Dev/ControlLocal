package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Uno de los N valores de una clave LISTA_MULTIPLE del ENCARGO (V73).
 *
 * <p>Gemela de {@code ValorMultipleAtributo}, y tiene que serlo: un tipo de
 * dato significa lo mismo lo lleve quien lo lleve. Si el multivalor de una
 * propiedad son N filas reales y el de un encargo fuera una cadena con comas,
 * habria dos semanticas para {@code LISTA_MULTIPLE} y el matcher tendria que
 * saber cual le toca -- que es la ramificacion por sujeto que este corte
 * precisamente retira del codigo consumidor.
 */
@Entity
@Table(name = "atributo_encargo_opcion")
@IdClass(ValorMultipleEncargo.Clave.class)
public class ValorMultipleEncargo extends EntidadDeOrganizacion {

    @Id
    @Column(name = "id_atributo_encargo", nullable = false)
    private Long idAtributoEncargo;

    @Id
    @Column(name = "valor", nullable = false, length = 40)
    private String valor;

    protected ValorMultipleEncargo() {
        // JPA
    }

    public ValorMultipleEncargo(Long idOrganizacion, Long idAtributoEncargo, String valor) {
        setOrganizacionId(idOrganizacion);
        this.idAtributoEncargo = idAtributoEncargo;
        this.valor = valor;
    }

    public Long getIdAtributoEncargo() {
        return idAtributoEncargo;
    }

    public String getValor() {
        return valor;
    }

    /** La clave compuesta. Un valor no es una entidad: no tiene id propio. */
    public static class Clave implements Serializable {

        private Long idAtributoEncargo;
        private String valor;

        public Clave() {
        }

        public Clave(Long idAtributoEncargo, String valor) {
            this.idAtributoEncargo = idAtributoEncargo;
            this.valor = valor;
        }

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Clave clave)) {
                return false;
            }
            return Objects.equals(idAtributoEncargo, clave.idAtributoEncargo)
                    && Objects.equals(valor, clave.valor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idAtributoEncargo, valor);
        }
    }
}
