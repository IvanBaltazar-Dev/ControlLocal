package com.controllocal.arquitectura;

import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KpiComisionQueryTest {

    @Test
    void kpiEconomicoExcluyeLiquidacionesAnuladasYAgrupaPorMoneda() throws Exception {
        for (String metodo : new String[]{"comisionesGeneradas", "repartosPorMoneda",
                "movimientosPorMoneda"}) {
            Query query = java.util.Arrays.stream(ContratoAlquilerRepository.class.getMethods())
                    .filter(m -> m.getName().equals(metodo))
                    .findFirst().orElseThrow().getAnnotation(Query.class);
            String jpql = query.value().replaceAll("\\s+", " ");
            assertTrue(jpql.contains("com.estado <> 'A'"), metodo);
            assertTrue(jpql.contains("group by"), metodo);
            assertTrue(jpql.contains("moneda"), metodo);
        }
    }
}
