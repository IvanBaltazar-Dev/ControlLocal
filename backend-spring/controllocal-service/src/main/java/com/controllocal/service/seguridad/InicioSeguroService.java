package com.controllocal.service.seguridad;

import com.controllocal.service.Actor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Contrato determinista CAT-E41/UC-K-16; no recibe secretos ni texto conversacional. */
public interface InicioSeguroService {
    enum Proposito { ACCESO }
    record Vinculo(String flujoRef, Proposito proposito, Instant venceEn) { }
    /** Exclusivamente para la superficie segura autenticada. No es un resultado de herramienta IA. */
    record RetornoPreparado(String flujoRef, Proposito proposito, String retornoRef, Instant venceEn) { }
    record RetornoConfirmado(String flujoRef, Proposito proposito, String estado) { }
    /** Proviene del principal validado por Core; nunca se deserializa del cliente. */
    record SesionVerificada(Actor actor, String contexto, Instant emitidaEn, Instant expiraEn,
                           String referenciaSesion, long revisionContexto) {
        public SesionVerificada {
            // iat/exp son claims de segundo del contrato de autenticación.
            // Canonicalizar antes de persistir mantiene la misma identidad
            // tras roundtrip PostgreSQL sin tolerancias ni comparación difusa.
            if (emitidaEn != null) emitidaEn = emitidaEn.truncatedTo(ChronoUnit.SECONDS);
            if (expiraEn != null) expiraEn = expiraEn.truncatedTo(ChronoUnit.SECONDS);
        }
    }

    Vinculo iniciar(Proposito proposito, Instant venceEn);
    RetornoPreparado completar(String flujoRef, Proposito proposito, SesionVerificada sesion);
    RetornoConfirmado consumir(String flujoRef, Proposito proposito, String retornoRef, SesionVerificada sesion);
}
