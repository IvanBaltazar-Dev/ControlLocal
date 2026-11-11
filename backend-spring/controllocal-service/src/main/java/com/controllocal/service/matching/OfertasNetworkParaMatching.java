package com.controllocal.service.matching;

import com.controllocal.service.Actor;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SPI de lectura Root: Demanda PROPIA → ofertas L2 participantes ya autorizadas/minimizadas.
 * No retorna demandas ajenas, no crea Actor de contraparte y no lee para luego ocultar campos.
 * La versión/huella son de la proyección pública relevante, no de una edición privada irrelevante.
 */
public interface OfertasNetworkParaMatching {
    record Referencia(String ofertaRef, long version, String huellaPublica) {
        public Referencia {
            if (ofertaRef == null || ofertaRef.isBlank() || version < 0
                    || huellaPublica == null || huellaPublica.isBlank())
                throw new IllegalArgumentException("Referencia Network incompleta.");
        }
    }

    /** camposPermitidos contiene exclusivamente la tarjeta V1 publicada/autorizada por su fuente. */
    record Candidata(Referencia referencia, ContratoMatching.Oferta oferta, Map<String, Object> camposPermitidos) {
        public Candidata {
            Objects.requireNonNull(referencia); Objects.requireNonNull(oferta);
            if (referencia.version() != oferta.version()
                    || !referencia.ofertaRef().equals(oferta.referenciaEstable()))
                throw new IllegalArgumentException("La proyección no corresponde a su referencia/versión.");
            camposPermitidos = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(camposPermitidos)));
        }
    }

    List<Candidata> candidatas(long demandaPropia, long versionDemanda, Actor actor, Instant ahoraCore);

    /**
     * Omite referencias que perdieron participación/permiso/elegibilidad/versión relevante.
     * No expone su existencia o causa privada. Un fallo técnico se propaga; no se vuelve lista vacía.
     * Sin callbacks, mutaciones o ampliación del alcance. Se invoca antes de cada entrega/explicación.
     */
    List<Candidata> revalidar(long demandaPropia, long versionDemanda, List<Referencia> referencias,
                              Actor actor, Instant ahoraCore);
}
