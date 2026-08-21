package com.controllocal.domain.inmueble;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * A que <b>(tipo de propiedad, operacion)</b> aplica una clave del ENCARGO, y
 * cuanto hace falta ahi (fila de {@code catalogo_atributo_operacion}, V73).
 *
 * <h2>Por que la operacion tambien decide</h2>
 * {@link AplicacionAtributo} responde «¿aplica a un departamento?», y para un
 * hecho fisico eso basta. Para una condicion comercial no:
 *
 * <pre>
 *   garantia_meses     aplica a un ALQUILER de departamento   ·  no a su VENTA
 *   partida_registral  bloquea una VENTA                      ·  es irrelevante en un ALQUILER
 * </pre>
 *
 * <p>Los dos ejemplos van en direcciones contrarias, asi que no hay forma de
 * reducirlo a una sola dimension. Declarar la aplicabilidad solo por tipo
 * obligaria a marcar {@code garantia_meses} como opcional en todas partes —y
 * entonces no bloquea nada— o como obligatoria en todas —y entonces bloquea una
 * venta pidiendo meses de garantia.
 *
 * <p>Es un VALOR, como su gemela: no tiene identidad ni vida fuera del atributo
 * que la contiene. Dos filas con el mismo par son la misma cosa.
 */
@Embeddable
public class AplicacionPorOperacion {

    /** 'L' local, 'O' oficina, 'D' departamento, 'C' casa, 'T' terreno, 'A' almacen, 'X' otro. */
    @Column(name = "tipo_propiedad", nullable = false, length = 1)
    private String tipoPropiedad;

    /**
     * El mismo vocabulario que {@code captacion.motivo_operacion} (V17): 'A'
     * alquiler, 'V' venta. Inventar otro aqui seria la segunda lista de
     * operaciones, y las dos divergirian el dia que se anada una tercera.
     */
    @Column(name = "tipo_operacion", nullable = false, length = 1)
    private String tipoOperacion;

    /** ALT bloquea el alta del encargo, PUB bloquea publicarlo, OPC no bloquea. */
    @Column(name = "exigencia", nullable = false, length = 3)
    private String exigencia = Exigencia.OPC.codigo();

    protected AplicacionPorOperacion() {
        // JPA
    }

    public AplicacionPorOperacion(String tipoPropiedad, String tipoOperacion,
                                  Exigencia exigencia) {
        this.tipoPropiedad = tipoPropiedad;
        this.tipoOperacion = tipoOperacion;
        this.exigencia = exigencia.codigo();
    }

    public String getTipoPropiedad() {
        return tipoPropiedad;
    }

    public String getTipoOperacion() {
        return tipoOperacion;
    }

    public Exigencia exigenciaTipada() {
        return Exigencia.desde(exigencia);
    }

    public String getExigencia() {
        return exigencia;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof AplicacionPorOperacion aplicacion)) {
            return false;
        }
        return Objects.equals(tipoPropiedad, aplicacion.tipoPropiedad)
                && Objects.equals(tipoOperacion, aplicacion.tipoOperacion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipoPropiedad, tipoOperacion);
    }
}
