package com.controllocal.service.matching;

import com.controllocal.service.Actor;
import com.controllocal.service.CoincidenciaService.Origen;
import com.controllocal.service.CoincidenciaService.TipoVersion;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aprobación L1 29/10: recomendación de origen propio autorizado, sin abrir cartera ajena.
 * Los proveedores delimitan filas/campos ANTES de recuperarlos; no usan Actor de contraparte.
 * Es independiente de participación o activación L2. No es un puerto de listado de expedientes.
 */
public interface RecomendacionesInternasParaMatching {
    record OrigenReferencia(Origen origen,long id,long version,TipoVersion tipoVersion) {
        public OrigenReferencia {
            Objects.requireNonNull(origen);Objects.requireNonNull(tipoVersion);
            if(id<=0||version<0)throw new IllegalArgumentException("Referencia de origen inválida.");
        }
    }
    /** Identificación del compañero; la ausencia de nombre conocido no se completa con uno ficticio. */
    record ResponsableVisible(String referencia,String nombre) {
        public ResponsableVisible { if(referencia==null||referencia.isBlank())throw new IllegalArgumentException("Responsable sin referencia."); }
    }
    /** Referencia de fuente INTERNA estable, distinta de un permiso o un ID que pueda publicarse por defecto. */
    record ReferenciaOferta(String referencia,long version,String huella) {
        public ReferenciaOferta {
            if(referencia==null||referencia.isBlank()||version<0||huella==null||!huella.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Referencia de oferta interna inválida.");
        }
    }
    record OfertaInterna(ReferenciaOferta referencia,ContratoMatching.Oferta oferta,
                          Map<String,Object> camposV1,ResponsableVisible responsable) {
        public OfertaInterna {
            Objects.requireNonNull(referencia);Objects.requireNonNull(oferta);Objects.requireNonNull(responsable);
            if(referencia.version()!=oferta.version())throw new IllegalArgumentException("Versiones de oferta incompatibles.");
            camposV1=Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(camposV1)));
        }
    }
    /** Consulta física positiva: nunca contiene presupuesto, cliente, notas, contactos o expediente. */
    record DemandaInterna(long id,long version,ContratoMatching.Consulta consulta,ResponsableVisible responsable,
                          ContratoMatching.ConfirmacionDisponibilidad disponibilidad) {
        public DemandaInterna {
            Objects.requireNonNull(consulta);Objects.requireNonNull(responsable);
            if(id<=0||version<0||version!=consulta.version()
                    ||consulta.coberturaCriterios()!=ContratoMatching.CoberturaCriterios.PARCIAL)
                throw new IllegalArgumentException("La consulta L1 inversa debe declarar su alcance parcial.");
        }
    }
    List<OfertaInterna> ofertas(OrigenReferencia origen,Actor actor,Instant ahora);
    /** Omite revocados; devuelve proyección actual autorizada ante cambio relevante, para actualizar explícitamente. */
    List<OfertaInterna> revalidarOfertas(OrigenReferencia origen,List<ReferenciaOferta> referencias,Actor actor,Instant ahora);
    List<DemandaInterna> demandas(OrigenReferencia origen,ContratoMatching.Modalidad modalidad,String regla,Instant corte,Actor actor);
    List<DemandaInterna> revalidarDemandas(OrigenReferencia origen,List<Long> ids,ContratoMatching.Modalidad modalidad,
                                         String regla,Instant corte,Actor actor,Instant ahora);
}
