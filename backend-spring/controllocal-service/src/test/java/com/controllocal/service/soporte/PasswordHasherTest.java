package com.controllocal.service.soporte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la COMPATIBILIDAD del hasher con la v1: los hashes del seed
 * (database/02_seed_base_data.sql) deben validar con las claves documentadas.
 * Si esto rompe, el backfill de credenciales v1->v2 deja de ser posible.
 */
class PasswordHasherTest {

    private static final String HASH_ADMIN_V1 =
            "pbkdf2$100000$uy2GnOLWMudcyeMG7pKhjA==$3twwP9cAqG+ykRGAx5BmI8ZTAPa3w2dcwviW8dqvDdE=";
    private static final String HASH_AGENTE_V1 =
            "pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=";

    @Test
    void validaHashesDelSeedV1() {
        assertTrue(PasswordHasher.verificar("Admin2026".toCharArray(), HASH_ADMIN_V1));
        assertTrue(PasswordHasher.verificar("Agente2026".toCharArray(), HASH_AGENTE_V1));
    }

    @Test
    void rechazaClaveIncorrecta() {
        assertFalse(PasswordHasher.verificar("otraClave".toCharArray(), HASH_ADMIN_V1));
    }

    @Test
    void rechazaFormatosDesconocidos() {
        assertFalse(PasswordHasher.verificar("Admin2026".toCharArray(), "Admin2026"));
        assertFalse(PasswordHasher.verificar("Admin2026".toCharArray(), null));
        assertFalse(PasswordHasher.verificar(null, HASH_ADMIN_V1));
    }

    @Test
    void hashPropioValida() {
        String hash = PasswordHasher.hash("NuevaClave2026".toCharArray());
        assertTrue(hash.startsWith("pbkdf2$100000$"));
        assertTrue(PasswordHasher.verificar("NuevaClave2026".toCharArray(), hash));
        assertFalse(PasswordHasher.verificar("NuevaClave2027".toCharArray(), hash));
    }
}
