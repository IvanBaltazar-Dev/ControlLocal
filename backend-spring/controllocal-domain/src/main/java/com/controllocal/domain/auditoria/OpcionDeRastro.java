package com.controllocal.domain.auditoria;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * <b>Un elemento del conjunto de un multivalor, en una escritura concreta</b>
 * (4.P, V83).
 *
 * <h2>Por que el conjunto ENTERO y no la diferencia</h2>
 * Un {@code LISTA_MULTIPLE} se escribe <b>sustituyendo</b>:
 * {@code borrarDe(ancla)} y un {@code save} por opcion. Sin esta tabla, cambiar
 * {@code vigilancia} de {@code {CASETA_24H, CAMARAS_CCTV}} a
 * {@code {CAMARAS_CCTV}} destruia el conjunto anterior sin dejar rastro.
 *
 * <p>Guardar un <b>diff</b> —«se quito CASETA_24H»— no sirve: si el conjunto
 * anterior es legado y nadie lo escribio nunca, el diff no permite reconstruir
 * que habia. Por eso se guardan los dos conjuntos completos.
 *
 * <pre>
 *   momento = HALLADO   el conjunto que el Core encontro antes de escribir
 *   momento = ESCRITO   el que queda despues
 * </pre>
 *
 * <p>Las claves primarias de {@code atributo_propiedad_opcion} y
 * {@code atributo_encargo_opcion} son {@code (id_atributo, valor)}: un conjunto
 * <b>sin orden y sin duplicados</b>. Conservar el conjunto <b>es</b> conservar
 * el dato completo, y por eso esta tabla no necesita guardar posiciones.
 */
@Entity
@Table(name = "rastro_valor_opcion")
@IdClass(OpcionDeRastro.Clave.class)
public class OpcionDeRastro extends EntidadDeOrganizacion {

    /** El conjunto que habia. */
    public static final String HALLADO = "HALLADO";
    /** El conjunto que queda. */
    public static final String ESCRITO = "ESCRITO";

    @Id
    @Column(name = "id_rastro", nullable = false)
    private Long idRastro;

    @Id
    @Column(name = "momento", nullable = false, length = 7)
    private String momento;

    @Id
    @Column(name = "valor", nullable = false, length = 40)
    private String valor;

    protected OpcionDeRastro() {
        // JPA
    }

    public OpcionDeRastro(Long idOrganizacion, Long idRastro, String momento, String valor) {
        setOrganizacionId(idOrganizacion);
        this.idRastro = idRastro;
        this.momento = momento;
        this.valor = valor;
    }

    public Long getIdRastro() {
        return idRastro;
    }

    public String getMomento() {
        return momento;
    }

    public String getValor() {
        return valor;
    }

    /** La clave compuesta. No hay id propio: un elemento no es una entidad. */
    public static class Clave implements Serializable {

        private Long idRastro;
        private String momento;
        private String valor;

        public Clave() {
        }

        public Clave(Long idRastro, String momento, String valor) {
            this.idRastro = idRastro;
            this.momento = momento;
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
            return Objects.equals(idRastro, clave.idRastro)
                    && Objects.equals(momento, clave.momento)
                    && Objects.equals(valor, clave.valor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idRastro, momento, valor);
        }
    }
}
