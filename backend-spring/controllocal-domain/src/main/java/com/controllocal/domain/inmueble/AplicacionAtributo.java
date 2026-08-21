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

    /**
     * true = sin este dato el alta de ese tipo no se puede cerrar.
     *
     * <p><b>Deuda con fecha: se retira cuando nadie lo lea.</b> V72 lo sustituyo
     * por {@link #exigencia}, que sabe decir tres cosas en vez de dos. La columna
     * sigue en la base para que la conversion sea auditable --su guarda compara
     * el antes contra el despues-- y porque retirarla en la misma migracion que
     * la introduce dejaria sin forma de comprobar que no se perdio ni se invento
     * obligatoriedad. Nadie debe leerla ya: se pregunta por {@link #exigenciaTipada()}.
     */
    @Column(name = "requerido", nullable = false)
    private boolean requerido;

    /** ALT bloquea el alta, PUB bloquea publicar, OPC no bloquea (V72). */
    @Column(name = "exigencia", nullable = false, length = 3)
    private String exigencia = Exigencia.OPC.codigo();

    protected AplicacionAtributo() {
        // JPA
    }

    public AplicacionAtributo(String tipoPropiedad, Exigencia exigencia) {
        this.tipoPropiedad = tipoPropiedad;
        this.exigencia = exigencia.codigo();
        // Se mantiene coherente mientras la columna exista, para que nadie que
        // la lea por descuido vea algo distinto de lo que dice la exigencia.
        this.requerido = exigencia.bloqueaAlta();
    }

    public String getTipoPropiedad() {
        return tipoPropiedad;
    }

    /** Cuanto hace falta el dato para este tipo. Es por donde hay que preguntar. */
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
