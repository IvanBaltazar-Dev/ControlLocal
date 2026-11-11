package com.controllocal.arquitectura;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invariantes declaradas por V96 sin requerir una base viva. */
class UnidadRelacionadaMigrationTest {

    @Test
    void v96DeclaraTenantFksVocabularioYGuardaDePartida() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V96__unidad_relacionada_fisica.sql");
        if (!Files.exists(migration)) {
            migration = Path.of("backend-spring/controllocal-app/src/main/resources/db/migration/V96__unidad_relacionada_fisica.sql");
        }
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("create table unidad_relacionada"));
        assertTrue(sql.contains("organizacion_id        bigint not null"));
        assertTrue(sql.contains("fk_unidad_relacionada_principal"));
        assertTrue(sql.contains("fk_unidad_relacionada_destino"));
        assertTrue(sql.contains("check (tipo_relacion in ('a', 'i', 'c'))"));
        assertTrue(sql.contains("id_propiedad_principal <> id_propiedad_relacionada"));
        assertTrue(sql.contains("least(id_propiedad_principal, id_propiedad_relacionada)"));
        assertTrue(sql.contains("greatest(id_propiedad_principal, id_propiedad_relacionada)"));
        assertTrue(sql.contains("validar_unidad_relacionada"));
        assertTrue(sql.contains("partida_registral is not null"));
        assertTrue(sql.contains("proteger_partida_de_unidad_independiente"));
    }
}
