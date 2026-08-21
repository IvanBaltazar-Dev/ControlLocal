package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Uno de los N valores de una clave LISTA_MULTIPLE (V72).
 *
 * <h2>Por que una tabla y no una columna</h2>
 * La alternativa era guardar {@code "AGUA, LUZ"} en {@code valor_texto}, y es
 * exactamente el defecto que este corte cierra: dos fichas que marcan lo mismo
 * en distinto orden dejan de ser iguales para cualquier comparacion, y no hay
 * forma de preguntar «cuales tienen agua» sin recorrer cadenas.
 *
 * <p>La otra alternativa era N filas sueltas en {@code atributo_propiedad}, y
 * habria obligado a retirar {@code uq_atributo_propiedad_clave} -- el indice
 * sobre el que V71 apoyo su justificacion al borrar la ultima tabla espejo. Con
 * el se iria la garantia de un valor por propiedad y concepto.
 *
 * <p>Asi que cuelgan de una fila <b>ancla</b> que no lleva escalar, con FK
 * compuesta {@code (organizacion_id, id_atributo_propiedad)} para que ningun
 * valor pueda apuntar al de otra corredora, y {@code ON DELETE CASCADE} para
 * que retirar la clave se lleve sus valores sin dejar huerfanos.
 */
@Entity
@Table(name = "atributo_propiedad_opcion")
@IdClass(ValorMultipleAtributo.Clave.class)
public class ValorMultipleAtributo extends EntidadDeOrganizacion {

    @Id
    @Column(name = "id_atributo_propiedad", nullable = false)
    private Long idAtributoPropiedad;

    @Id
    @Column(name = "valor", nullable = false, length = 40)
    private String valor;

    protected ValorMultipleAtributo() {
        // JPA
    }

    public ValorMultipleAtributo(Long idOrganizacion, Long idAtributoPropiedad, String valor) {
        setOrganizacionId(idOrganizacion);
        this.idAtributoPropiedad = idAtributoPropiedad;
        this.valor = valor;
    }

    public Long getIdAtributoPropiedad() {
        return idAtributoPropiedad;
    }

    public String getValor() {
        return valor;
    }

    /** La clave compuesta. La tabla no tiene id propio: un valor no es una entidad. */
    public static class Clave implements Serializable {

        private Long idAtributoPropiedad;
        private String valor;

        public Clave() {
        }

        public Clave(Long idAtributoPropiedad, String valor) {
            this.idAtributoPropiedad = idAtributoPropiedad;
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
            return Objects.equals(idAtributoPropiedad, clave.idAtributoPropiedad)
                    && Objects.equals(valor, clave.valor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idAtributoPropiedad, valor);
        }
    }
}
