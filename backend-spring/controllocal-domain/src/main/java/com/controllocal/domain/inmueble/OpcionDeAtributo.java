package com.controllocal.domain.inmueble;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Un valor admitido por una LISTA o una LISTA_MULTIPLE, con su rotulo (V72).
 *
 * <p>Es lo que separa una lista de un texto libre. Sin vocabulario, dos fichas
 * que dicen lo mismo con distintas palabras dejan de poder compararse -- y
 * comparar es todo lo que hace util una cartera.
 *
 * <p>Es un VALOR y no una entidad, igual que {@link AplicacionAtributo}: no
 * tiene identidad ni vida fuera del atributo que la contiene, asi que viaja
 * como {@code @ElementCollection} y se hidrata con su catalogo. Dos opciones
 * con el mismo {@code valor} son la misma.
 */
@Embeddable
public class OpcionDeAtributo {

    /** El codigo estable. Es lo que se guarda y lo que compara el matcher. */
    @Column(name = "valor", nullable = false, length = 40)
    private String valor;

    /** Como se lee. Cambiarlo no cambia ningun dato ya escrito. */
    @Column(name = "rotulo", nullable = false, length = 120)
    private String rotulo;

    @Column(name = "orden", nullable = false)
    private int orden = 100;

    /**
     * Una opcion retirada deja de ofrecerse pero <b>no invalida</b> lo ya
     * escrito: borrarla convertiria en mentira las fichas que la eligieron.
     */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected OpcionDeAtributo() {
        // JPA
    }

    public OpcionDeAtributo(String valor, String rotulo, int orden) {
        this.valor = valor;
        this.rotulo = rotulo;
        this.orden = orden;
    }

    public String getValor() {
        return valor;
    }

    public String getRotulo() {
        return rotulo;
    }

    public int getOrden() {
        return orden;
    }

    public boolean isActivo() {
        return activo;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof OpcionDeAtributo opcion)) {
            return false;
        }
        return Objects.equals(valor, opcion.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valor);
    }
}
