package com.controllocal.service.mercado;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.mercado.CalculoPrecioMercado.*;
import com.controllocal.service.mercado.ContratoInvestigacionManual.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** CAT-R24/E09/E38: contratos Core de trabajo y referencia; no contiene credenciales o costos nativos. */
public final class DatosInvestigacionMercado {
    private DatosInvestigacionMercado() { }
    public record Segmento(OperacionInmobiliaria operacion,String tipo,long distrito,String moneda,String periodo,
                           Naturaleza naturaleza,String conceptoArea,String composicion) { }
    /** Valores numéricos sólo con decisión acreditada; ninguna cota productiva predeterminada. */
    public record Configuracion(UUID id,String version,Segmento segmento,List<FuentePrevista> fuentes,
                                Set<Etapa> etapas,String parada,Duration limiteActivoPorRonda,int maximoRondas,
                                Duration plazoEntrega,Duration vigenciaReferencia,UUID regla,String versionRegla,
                                Instant desde,Instant hasta,boolean habilitada,boolean permiteReferenciaHistorica,
                                Normalizacion normalizacion,String evidencia) {
        public Configuracion {fuentes=fuentes==null?null:List.copyOf(fuentes);etapas=etapas==null?null:Set.copyOf(etapas);}
    }
    public record Preparar(UUID solicitud,UUID politica,String versionPolitica,Long propiedad,String carencia,int rondas,Instant plazoComunicado) { }
    /** Excepción R24: máximo comercial, sin tarifa privada, normalizador, modelo, tokens o costo proveedor. */
    public record Propuesta(Preparar datos,Plan plan,BigDecimal maximo,String huella) { }
    public record Aceptacion(Preparar datos,BigDecimal maximoAceptado,String huellaAceptada) { }
    public record Comando(UUID id,long versionEsperada) { }
    public enum DecisionRevision { APROBAR,OBSERVAR,RECHAZAR }
    public record RevisionEntrega(Comando comando,DecisionRevision decision,String motivo) { }
    public record Retiro(Comando comando,String motivo) { }
    public record RetiroPolitica(UUID decision,String motivo) { }
    public record Entrega(Comando comando,List<Ronda> rondas) {
        public Entrega {rondas=rondas==null?null:List.copyOf(rondas);}
    }
    /** Consulta privada del profesional: nunca devuelve el expediente interno de revisión/finanzas. */
    public record EstadoTrabajo(UUID solicitud,String estado,long version,Instant creada,Instant plazoComunicado,
                                BigDecimal maximoAceptado,UUID referenciaPublicada) { }
    public record ResultadoPrivado(EstadoTrabajo trabajo,String resultado,ReferenciaOrientativa referencia) { }
    public record TrabajoFinanciero(CabeceraTrabajo cabecera,Long organizacion,UUID solicitud,UUID regla,String versionRegla,
                                   BigDecimal maximoAceptado,BigDecimal rondasAutorizadas,BigDecimal rondasAcreditadas,
                                   BigDecimal tarifaAceptada,Instant liquidadaEn,BigDecimal rondasLiquidadas) { }
    /** La colección R25 no expone el máximo/ledger del cliente ni identidades de su cartera. */
    public record CabeceraTrabajo(long trabajo,String estado,long version,Instant creada,Instant plazoComunicado) { }
    public record TrabajoOperador(CabeceraTrabajo cabecera,Plan plan,String contexto,List<Ronda> evidencia,boolean puedeInvestigar,String limitacionActual) {
        public TrabajoOperador {evidencia=evidencia==null?List.of():List.copyOf(evidencia);}
    }
    public record TrabajoRevision(CabeceraTrabajo cabecera,UUID politica,String versionPolitica,List<Ronda> evidencia) {
        public TrabajoRevision {evidencia=List.copyOf(evidencia);}
    }
    /** Fuente pública revisada; se almacena aparte de la solicitud privada y se consulta por proyección. */
    public record CorpusPublicado(Segmento segmento,Instant corte,List<Referencia> referencias,List<String> limitaciones) {
        public CorpusPublicado {referencias=referencias==null?null:List.copyOf(referencias);limitaciones=limitaciones==null?null:List.copyOf(limitaciones);}
    }
    public record Publicacion(Comando comando,UUID referencia,List<String> referenciasSeleccionadas,Instant desde,Instant hasta,List<String> limitacionesPublicas) {
        public Publicacion {limitacionesPublicas=limitacionesPublicas==null?null:List.copyOf(limitacionesPublicas);referenciasSeleccionadas=referenciasSeleccionadas==null?null:List.copyOf(referenciasSeleccionadas);}
    }
    /** Lectura orientativa integrada en prospección: referencia de oferta/cierre acreditado, no precio autorizado. */
    public record ReferenciaOrientativa(UUID version,Segmento segmento,Instant corte,Instant vigenteHasta,
                                       String estado,int inmueblesUnicos,BigDecimal promedioPorM2,
                                       String alcance,List<String> limitaciones) {
        public ReferenciaOrientativa {limitaciones=List.copyOf(limitaciones);}
    }
}
