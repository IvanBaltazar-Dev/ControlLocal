package com.controllocal.domain.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Uno de los valores que admite un criterio {@code INCLUYE} (V92).
 *
 * <p>Es un VALOR y no una entidad, igual que {@code OpcionDeAtributo}: no tiene
 * identidad ni vida fuera del criterio que lo contiene, y dos valores con el
 * mismo texto son el mismo.
 *
 * <p>Un conjunto y no una cadena separada por comas, por la misma razon que
 * {@code atributo_propiedad_opcion}: <i>«agua, desague»</i> y <i>«agua
 * desague»</i> serian la misma cadena para cualquier comparacion, y entonces
 * pedir dos cosas y pedir una dejarian de distinguirse.
 */
@Embeddable
public class ValorDeCriterio {

    @Column(name = "valor", nullable = false, length = 120)
    private String valor;

    protected ValorDeCriterio() {
        // JPA
    }

    public ValorDeCriterio(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ValorDeCriterio pedido)) {
            return false;
        }
        return Objects.equals(valor, pedido.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valor);
    }
}
