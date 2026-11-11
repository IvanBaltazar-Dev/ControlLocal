package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.Procedencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

/** RF062–064/CAT-E26–27: cálculo, devengo, exigibilidad y fondos conservan fuentes distintas. */
public interface ComisionService {
    record Dinero(BigDecimal importe, String moneda) { }
    record Calculo(Dinero cuantia, List<String> faltantes) {
        public Calculo { faltantes=List.copyOf(faltantes); }
    }
    record Devengo(String estado, Dinero cuantiaAcreditada, List<ReferenciaHito> hechos, List<String> faltantes) {
        public Devengo { hechos=List.copyOf(hechos); faltantes=List.copyOf(faltantes); }
    }
    record Exigibilidad(Boolean exigible, LocalDate fechaAcreditada, List<String> faltantes) {
        public Exigibilidad { faltantes=List.copyOf(faltantes); }
    }
    record Vencimiento(Integer indiceTramo, LocalDate fechaAcreditada, String condicionLiteral, List<String> faltantes) {
        public Vencimiento { faltantes=List.copyOf(faltantes); }
    }
    record Reparto(BigDecimal agente, BigDecimal empresa, BigDecimal pagadoAgente, BigDecimal saldoPagoAgente,
                   Long evaluacionBase, String moneda) { }
    record Cobro(List<Dinero> ingresosRecibidos, Dinero saldoInformativoSobreCalculo, String estado) {
        public Cobro { ingresosRecibidos=List.copyOf(ingresosRecibidos); }
    }
    record GestionCobro(boolean concluida, LocalDate proximaRevision, String motivo, OffsetDateTime registradoEn) { }
    record TramoRealizado(long id, int indicePacto, ReferenciaPacto pacto,
                          Calculo calculo, Devengo devengo, Exigibilidad exigibilidad) { }
    record FichaComision(Long id, Long version, ReferenciaContextoEconomico contexto,
                         ReferenciaPacto pacto, long versionContenidoPacto,
                         ReferenciaFuenteEconomica fuenteFinal, Calculo calculo, Devengo devengo,
                         Exigibilidad exigibilidad, List<Vencimiento> calendario,
                         List<TramoRealizado> tramos, Cobro cobro, Reparto reparto, GestionCobro gestion) {
        public FichaComision { calendario=List.copyOf(calendario); tramos=List.copyOf(tramos); }
    }
    /** Contexto ausente: preview del pacto, sin crear expediente ni liquidación ficticios. */
    FichaComision consultar(ReferenciaPacto pacto, ReferenciaContextoEconomico contexto, Actor actor);
    /** D5: proyección mínima desde la aritmética C5; null no equivale a saldo cero. */
    record GestionRadar(boolean basePendiente,Boolean saldoExigiblePendiente,LocalDate revisarEn,List<String> faltantes) {
        public GestionRadar {faltantes=List.copyOf(faltantes);}
    }
    GestionRadar gestionParaRadar(ReferenciaPacto pacto,ReferenciaContextoEconomico contexto,Actor actor);
    /** C7 consume importes por la puerta C5; no calcula dinero pendiente como ingreso. */
    record ImportePorOperacion(String operacion,BigDecimal importe,String moneda) { }
    record CoberturaExigibilidad(int exigibles,int noExigibles,int sinEvidencia) { }
    record ResumenEconomico(LocalDate desde,LocalDate hasta,java.time.Instant corte,List<ImportePorOperacion> calculadoActual,
                            List<ImportePorOperacion> devengadoActual,List<ImportePorOperacion> cobradoEnPeriodo,int casos,int sinCuantiaConfirmada,CoberturaExigibilidad exigibilidadActual) { }
    record FiltroEconomico(java.util.Set<Long> encargos,String operacion) {public FiltroEconomico{encargos=encargos==null?null:java.util.Set.copyOf(encargos);}}
    ResumenEconomico resumenDePeriodo(LocalDate desde,LocalDate hasta,java.time.Instant corte,FiltroEconomico filtro,Actor actor);
    Optional<FichaComision> porContratoAlquiler(long idContrato, Actor actor);
    Map<Long,FichaComision> porContratosAlquiler(Collection<Long> idsContrato, Actor actor);
    /** Efecto técnico MANDATORY; causa nullable, clave explícita obligatoria. */
    FichaComision evaluar(ReferenciaContextoEconomico contexto, ReferenciaHito causa, Actor actor,
                         ContextoSesion sesion, String clave, Procedencia procedencia);

    record DatosReparto(Long comision, Long versionEsperada, BigDecimal montoAgente, String observacion) { }
    /**
     * PERSONA/ORGANIZACION usan id contextual. CAMPO_PACTO usa pacto+localizador
     * cerrado parteObligada/beneficiario. CAMPO_DOCUMENTO usa documento+localizador
     * de la fuente revisada. Una etiqueta no acredita identidad ni permiso.
     */
    record ReferenciaSujeto(String tipo, Long id, ReferenciaPacto pacto,
                            ReferenciaDocumento documento, String localizador) { }
    record SujetoDeclarado(ReferenciaSujeto referencia, String etiqueta) { }
    record DestinoPago(String datoDestino, SujetoDeclarado titular) { }
    record DatosCobro(Long comision, Long versionEsperada, Long tramo,
                       BigDecimal monto, String moneda, LocalDate fecha, String formaPago,
                       SujetoDeclarado parteObligada, SujetoDeclarado beneficiario,
                       DestinoPago destino, String referenciaExterna,
                       List<ReferenciaDocumento> evidencias, String observacion) {
        public DatosCobro { evidencias=evidencias==null?List.of():List.copyOf(evidencias); }
        @JsonAnySetter public void rechazarCampoDesconocido(String campo,JsonNode valor){campoNoAdmitido();}
    }
    record ResultadoReparto(long comision, long version, Reparto reparto) { }
    record ResultadoCobro(long comision, long version, long movimiento,
                         Dinero importe, LocalDate fecha, OffsetDateTime registradoEn, Long tramo) { }
    record DatosReprogramacion(Long comision, Long versionEsperada, LocalDate proximaRevision, String motivo) {
        @JsonAnySetter public void rechazarCampoDesconocido(String campo,JsonNode valor){campoNoAdmitido();}
    }
    record DatosConclusion(Long comision, Long versionEsperada, String motivo) {
        @JsonAnySetter public void rechazarCampoDesconocido(String campo,JsonNode valor){campoNoAdmitido();}
    }
    record ResultadoGestion(long comision, long version, GestionCobro gestion) { }
    DatosReparto validarReparto(DatosReparto datos, Actor actor);
    void exigirLecturaReparto(DatosReparto original, Actor actor);
    ResultadoReparto asignarReparto(DatosReparto datos, Actor actor,
                                  ContextoSesion sesion, String clave, Procedencia procedencia);
    Optional<ResultadoReparto> consultarResultadoReparto(String clave, Actor actor);
    DatosCobro validarCobro(DatosCobro datos, Actor actor);
    void exigirLecturaCobro(DatosCobro original, Actor actor);
    ResultadoCobro registrarCobro(DatosCobro datos, Actor actor,
                                ContextoSesion sesion, String clave, Procedencia procedencia);
    Optional<ResultadoCobro> consultarResultadoCobro(String clave, Actor actor);
    DatosReprogramacion validarReprogramacion(DatosReprogramacion datos, Actor actor);
    DatosConclusion validarConclusion(DatosConclusion datos, Actor actor);
    void exigirLecturaReprogramacion(DatosReprogramacion original, Actor actor);
    void exigirLecturaConclusion(DatosConclusion original, Actor actor);
    ResultadoGestion reprogramarGestion(DatosReprogramacion datos, Actor actor,
                                       ContextoSesion sesion, String clave, Procedencia procedencia);
    ResultadoGestion concluirGestion(DatosConclusion datos, Actor actor,
                                     ContextoSesion sesion, String clave, Procedencia procedencia);
    Optional<ResultadoGestion> consultarResultadoGestion(String capacidad, String clave, Actor actor);
    /** Resumen exacto para el mismo protocolo humano; sólo lectura, nunca produce fondos o gestión. */
    Map<String,Object> previsualizarActuacion(String capacidad, Map<String,Object> datos, Actor actor);
    private static void campoNoAdmitido(){throw new com.controllocal.service.excepcion.ReglaNegocioException("La actuación de cobro contiene un campo no admitido.");}
}
