package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.soporte.Procedencia;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** CAT-E24–27: lectura reautorizada de fuentes; no confirma hechos ni escribe dinero. */
public interface HitosConfirmados {
    record ReferenciaContextoEconomico(long id) { }
    record ReferenciaHito(long id,long revision) { }
    /** Tipo ACUERDO_C4 o REVISION_C5 identifica su fuente, nunca una cifra sin procedencia. */
    record ReferenciaFuenteEconomica(String tipo,long id,long revision) { }
    record FuenteEconomicaFinal(ReferenciaFuenteEconomica referencia,
            Long acuerdoC4,Long versionAcuerdoC4,OfertaService.ObjetoExacto objeto,
            OfertaService.Dinero precioVenta,OfertaService.Dinero rentaMensual,
            List<ReferenciaDocumento> documentos,Procedencia procedencia) {
        public FuenteEconomicaFinal { documentos=documentos==null?null:List.copyOf(documentos); }
    }
    record ReferenciaDocumento(java.util.UUID referencia,long version,String huella) { }
    record FuenteHitoConfirmado(ReferenciaContextoEconomico contexto,
            ReferenciaHito referencia,TipoHitoContractual tipo,LocalDate fechaEfectiva,
            OffsetDateTime registradoEn,ReferenciaPacto pacto,
            ReferenciaFuenteEconomica fuenteEconomica,ReferenciaHito rectifica,
            Procedencia procedencia) { }
    /** Identidad estable entre minuta/escritura; fecha de carga no elige pacto ni acuerdo. */
    record ContextoEconomico(ReferenciaContextoEconomico referencia,long organizacion,
            long encargo,OperacionInmobiliaria operacion,Long oportunidad,
            Long solicitudAlquiler,Long contratoAlquiler,Long contratoVenta,
            ReferenciaPacto pacto,FuenteEconomicaFinal fuenteEconomica,
            List<FuenteHitoConfirmado> hechosConfirmados) {
        public ContextoEconomico { hechosConfirmados=List.copyOf(hechosConfirmados); }
    }

    ContextoEconomico consultarContexto(ReferenciaContextoEconomico referencia,Actor actor);
    /** Fuente histórica exacta de cada devengo, aunque luego se haya acreditado otra cifra. */
    FuenteEconomicaFinal consultarFuente(ReferenciaContextoEconomico contexto,
            ReferenciaFuenteEconomica referencia,Actor actor);
}
