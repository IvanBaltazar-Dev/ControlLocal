package com.controllocal.service.colaboracion;

import com.controllocal.domain.colaboracion.LadoColaboracion;
import java.time.Instant;
import java.util.Set;

/** Fixture de contratos unitarios: no acredita una concesión runtime ni se usa en integración PG. */
public final class ReferenciasConcedidasDePrueba {
    private ReferenciasConcedidasDePrueba() { }
    public static ReferenciaParteConcedida de(String concesion,long versionConcesion,String solicitud,
            long versionAlcance,long idDecision,LadoColaboracion lado,long org,long idRecurso,long responsable,
            long versionRecurso,Set<String> campos,Set<String> selectores,String finalidad,Instant desde,Instant hasta,String huella) {
        return new ReferenciaParteConcedida(concesion,versionConcesion,solicitud,versionAlcance,idDecision,lado,org,idRecurso,
                responsable,versionRecurso,campos,selectores,finalidad,desde,hasta,huella);
    }
    public static ReferenciaParteConcedida demanda(long org,long id,long responsable,long version,
            Set<String> campos,Set<String> selectores) {
        return de("00000000-0000-0000-0000-000000000001",0,"00000000-0000-0000-0000-000000000002",0,1,
                LadoColaboracion.DEMANDA,org,id,responsable,version,campos,selectores,"RED_COLABORATIVA",
                Instant.parse("2026-10-01T00:00:00Z"),Instant.parse("2100-01-01T00:00:00Z"),"a".repeat(64));
    }
}
