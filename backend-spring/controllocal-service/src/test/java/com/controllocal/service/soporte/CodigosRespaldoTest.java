package com.controllocal.service.soporte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodigosRespaldoTest {

    @Test
    void generaOchoConIdentificadorUnico() {
        List<CodigosRespaldo.Generado> codigos = CodigosRespaldo.generar();

        assertEquals(CodigosRespaldo.CANTIDAD, codigos.size());
        Set<String> identificadores = codigos.stream()
                .map(CodigosRespaldo.Generado::identificador)
                .collect(Collectors.toSet());
        assertEquals(codigos.size(), identificadores.size(),
                "el identificador tiene que localizar UNA fila; repetido, no localiza nada");
    }

    @Test
    @DisplayName("el hash es lento y con sal, no un SHA-256 del codigo")
    void seGuardaConHashLento() {
        var codigo = CodigosRespaldo.generar().get(0);

        assertTrue(codigo.hash().startsWith("pbkdf2$"), codigo.hash());
        // Dos codigos distintos con el mismo texto darian hashes distintos: la
        // sal esta dentro del formato.
        assertFalse(codigo.hash().contains(codigo.visible()));
    }

    @Test
    @DisplayName("80 bits de secreto, no 50")
    void elSecretoTiene80Bits() {
        var codigo = CodigosRespaldo.generar().get(0);
        var partes = CodigosRespaldo.partir(codigo.visible());

        // 16 caracteres Base32 = 80 bits. Es lo que exige un secreto de
        // consulta almacenado; los 50 de la primera version no llegaban.
        assertEquals(16, partes.secreto().length(), codigo.visible());
        assertTrue(partes.completo());
    }

    @Test
    @DisplayName("el identificador viaja en claro: es un indice, no un secreto")
    void elIdentificadorSeRecuperaDelCodigoVisible() {
        var codigo = CodigosRespaldo.generar().get(0);

        assertEquals(codigo.identificador(),
                CodigosRespaldo.partir(codigo.visible()).identificador());
    }

    @Test
    @DisplayName("el codigo verifica contra su hash, y otro no")
    void verificaContraSuHash() {
        List<CodigosRespaldo.Generado> codigos = CodigosRespaldo.generar();
        var primero = codigos.get(0);
        var segundo = codigos.get(1);

        assertTrue(PasswordHasher.verificar(
                CodigosRespaldo.partir(primero.visible()).secreto().toCharArray(),
                primero.hash()));
        assertFalse(PasswordHasher.verificar(
                CodigosRespaldo.partir(segundo.visible()).secreto().toCharArray(),
                primero.hash()));
    }

    @Test
    @DisplayName("tolera guiones, minusculas y las confusiones de Crockford")
    void toleraLoQueUnHumanoTeclea() {
        var codigo = CodigosRespaldo.generar().get(0);
        String tecleado = codigo.visible().toLowerCase().replace("-", " ");

        var partes = CodigosRespaldo.partir(tecleado);
        assertEquals(codigo.identificador(), partes.identificador());
        assertTrue(PasswordHasher.verificar(partes.secreto().toCharArray(), codigo.hash()));
    }

    @Test
    void unCodigoIncompletoNoSeIntenta() {
        assertFalse(CodigosRespaldo.partir("AB7K").completo());
        assertFalse(CodigosRespaldo.partir("").completo());
        assertFalse(CodigosRespaldo.partir(null).completo());
    }

    @Test
    @DisplayName("no se repiten entre generaciones")
    void sonAleatorios() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            CodigosRespaldo.generar().forEach(c -> vistos.add(c.visible()));
        }
        assertEquals(5 * CodigosRespaldo.CANTIDAD, vistos.size());
    }
}
