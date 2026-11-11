package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blinda la politica de contrasenas propias (§4.5).
 *
 * <p>Lo que mas importa de aqui no es que rechace lo malo, sino <b>que no
 * exija lo que no debe</b>: una regla de "mayuscula + digito + simbolo"
 * fabricaria {@code Clave2026!}, que es exactamente el patron del seed que
 * este bloque viene a retirar.
 */
class PoliticaContrasenasTest {

    @Test
    @DisplayName("una frase larga en minusculas es valida: la longitud es lo que aporta entropia")
    void aceptaFraseLargaSinSimbolos() {
        assertDoesNotThrow(() ->
                PoliticaContrasenas.exigirValida("mi perro come alfalfa".toCharArray(), "vmora"));
    }

    @Test
    @DisplayName("rechaza por debajo del minimo y lo dice con el numero")
    void rechazaCorta() {
        var error = assertThrows(ReglaNegocioException.class, () ->
                PoliticaContrasenas.exigirValida("corta123".toCharArray(), "vmora"));
        assertTrue(error.getMessage().contains(String.valueOf(PoliticaContrasenas.LARGO_MINIMO)),
                error.getMessage());
    }

    @Test
    @DisplayName("rechaza la que contiene el nombre de usuario, aunque sea larga")
    void rechazaLaQueLlevaElUsuario() {
        assertThrows(ReglaNegocioException.class, () ->
                PoliticaContrasenas.exigirValida("xxxVMORAxxxyyy".toCharArray(), "vmora"));
    }

    @Test
    @DisplayName("rechaza las de la lista corta de claves comunes")
    void rechazaComunes() {
        assertThrows(ReglaNegocioException.class, () ->
                PoliticaContrasenas.exigirValida("controllocal".toCharArray(), "vmora"));
    }

    @Test
    @DisplayName("nula o vacia es un mensaje propio, no un NullPointerException")
    void rechazaVacia() {
        assertThrows(ReglaNegocioException.class, () ->
                PoliticaContrasenas.exigirValida(null, "vmora"));
        assertThrows(ReglaNegocioException.class, () ->
                PoliticaContrasenas.exigirValida(new char[0], "vmora"));
    }

}
