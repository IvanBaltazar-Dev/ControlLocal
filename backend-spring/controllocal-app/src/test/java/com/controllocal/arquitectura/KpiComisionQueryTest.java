package com.controllocal.arquitectura;

import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KpiComisionQueryTest {

    @Test
    void lecturasEconomicasConservanTenantAtribucionYMonedas() throws Exception {
        // C5 separa cálculo, reparto e ingresos reales. Anular o corregir un
        // cálculo no puede hacer desaparecer los fondos del historial.
        for (String metodo : new String[]{"comisionesGeneradasDeAgente", "repartosDeAgente",
                "movimientosDeAgente"}) {
            Query query = ContratoAlquilerRepository.class.getMethod(metodo,long.class,long.class)
                    .getAnnotation(Query.class);
            String sql = query.value().replaceAll("\\s+", " ");
            assertTrue(query.nativeQuery(), metodo);
            assertTrue(sql.contains("c.organizacion_id=:idOrganizacion"), metodo);
            assertTrue(sql.contains("c.id_rol_agente_cierre=:idRolAgente"), metodo);
            assertTrue(sql.contains("m.organizacion_id=:idOrganizacion"), metodo);
            assertTrue(sql.contains("group by"), metodo);
            assertTrue(sql.contains("moneda"), metodo);
        }
    }
}
