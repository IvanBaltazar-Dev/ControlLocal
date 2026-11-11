package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.demanda.InteresadosDeDemanda.Referencia;
import com.controllocal.service.matching.ContratoMatching;
import com.controllocal.service.matching.ContratoMatching.ContextoValor;
import com.controllocal.service.matching.ContratoMatching.MetadatosDato;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.ValorLogico;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** RF028–030/CAT-E18: un requerimiento, su autoridad actual y versiones conservadas. */
public interface RequerimientoService {
    record AlternativaPedida(String operador,ValorLogico valor,ValorLogico hasta,
        boolean negada,ContextoValor contexto,MetadatosDato metadatos) { }
    /** Transporte tipado; normalización única al ContratoMatching compartido. */
    record CriterioPedido(String clave,String operador,String peso,
        String valorTexto,BigDecimal valorNumero,BigDecimal valorNumeroHasta,
        Boolean valorBooleano,LocalDate valorFecha,LocalDate valorFechaHasta,
        List<String> valores,String moneda,Integer prioridad,ContextoValor contexto,
        MetadatosDato metadatos,boolean negada,List<AlternativaPedida> alternativas) { }
    /** Los escalares son proyección de una alternativa única, nunca el primer OR. */
    record CriterioFicha(String clave,String rotulo,String unidad,String tipoDato,
        String operador,String peso,String descripcion,String valorTexto,
        BigDecimal valorNumero,BigDecimal valorNumeroHasta,Boolean valorBooleano,
        LocalDate valorFecha,LocalDate valorFechaHasta,List<String> valores,String moneda,
        ContratoMatching.Criterio normalizado) { }
    record DatosRequerimiento(Long idCliente,String rubro,String tipoInmueble,
        BigDecimal rentaMin,BigDecimal rentaMax,BigDecimal frenteMinimo,
        String operacion,List<String> tiposInmueble,BigDecimal presupuestoMin,
        BigDecimal presupuestoMax,String moneda,BigDecimal metrajeMin,BigDecimal metrajeMax,
        LocalDate vigenteHasta,String estado,String observaciones,List<String> distritos,
        List<CriterioPedido> criterios,Referencia interesado,String finalidad,
        LocalDate vigenteDesde,String conceptoPresupuesto,String periodoPresupuesto,Set<String> camposARetirar) { }
    record FinDeclarado(String tipo,LocalDate fechaDelHecho,String motivo,Instant registradoEn) { }
    record FichaRequerimiento(Long id,Long idCliente,String rubro,String tipoInmueble,
        BigDecimal rentaMin,BigDecimal rentaMax,BigDecimal frenteMinimo,
        String operacion,List<String> tiposInmueble,BigDecimal presupuestoMin,
        BigDecimal presupuestoMax,String moneda,BigDecimal metrajeMin,BigDecimal metrajeMax,
        LocalDate vigenteHasta,LocalDateTime fechaRevalidacion,String estado,String observaciones,
        List<String> distritos,List<CriterioFicha> criterios,LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion,long version,Long idResponsable,Long idPersonaInteresada,
        String finalidad,LocalDate vigenteDesde,String conceptoPresupuesto,String periodoPresupuesto,
        Map<String,Object> contextoInteresado,FinDeclarado fin) { }
    enum TipoFinDemanda { RETIRADA, FINALIZADA }
    record FinDemanda(long idDemanda,long versionEsperada,TipoFinDemanda tipo,
        LocalDate fechaDelHecho,String motivo) { }
    record ResultadoFinDemanda(long idDemanda,long version,String estado,TipoFinDemanda tipo,
        LocalDate fechaDelHecho,String motivo,Instant registradoEn,long idEvento) { }
    enum AccionColaboracion { SOLICITAR, DECIDIR, REVOCAR, CONSULTAR }
    record AutoridadLadoDemanda(long idDemanda,long organizacionId,long responsableId,
        long version,String estado,LocalDate vigenteDesde,LocalDate vigenteHasta,
        Set<String> camposConcedibles) { }
    record ReferenciaDemanda(long idDemanda,long version,long responsableId,
        String estado,LocalDate vigenteDesde,LocalDate vigenteHasta) { }
    record ContextoDisponibilidadDemanda(long idDemanda,long version,
        com.controllocal.domain.inmueble.OperacionInmobiliaria operacion,long responsableId,String estado,
        long organizacionId,LocalDate vigenteDesde,LocalDate vigenteHasta) { }
    record VerificacionCamposDemanda(long idDemanda,long versionActual,long responsableActual,
        String estado,LocalDate vigenteDesde,LocalDate vigenteHasta,String huellaSubset) { }

    List<FichaRequerimiento> listarPorCliente(long idCliente,Actor actor);
    FichaRequerimiento buscar(long id,Actor actor);
    FichaRequerimiento consultar(long id,Long version,Instant corte,Actor actor);
    FichaRequerimiento crear(DatosRequerimiento datos,Actor actor,ContextoSesion sesion,
        String clave,Procedencia procedencia);
    FichaRequerimiento actualizar(long id,long versionEsperada,DatosRequerimiento datos,
        Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    /** Efecto interno de la revisión exacta canónica; sin endpoint de efecto directo. */
    FinDemanda validarFin(FinDemanda fin,Actor actor);
    ResultadoFinDemanda retirarOFinalizar(FinDemanda fin,Actor actor,ContextoSesion sesion,
        String clave,Procedencia procedencia);
    Optional<ResultadoFinDemanda> consultarResultadoFin(String clave,Actor actor);
    Optional<FichaRequerimiento> consultarResultado(String clave,Actor actor);
    ContratoMatching.Consulta paraMatching(long id,long version,
        ContratoMatching.Modalidad modalidad,String versionRegla,Instant corteCore,Actor actor);
    List<ReferenciaDemanda> demandasParaMatching(Actor actor,Instant corteCore);
    /** Metadata mínima E20/CAT-E28. false no calcula scores; true bloquea para D2 en TX existente. */
    ContextoDisponibilidadDemanda contextoDisponibilidad(long id,Actor actor,boolean confirmar);
    AutoridadLadoDemanda autoridadParaColaboracion(long id,long version,
        AccionColaboracion accion,Actor actor);
    /** Selectores sujeto:clave explícitos; cada criterio completo debe estar autorizado. */
    Map<String,Object> proyectarCamposColaboracion(long id,long version,Set<String> campos,
        Set<String> selectoresCriterioAprobados,Actor actor);
    /** Proof interno de Colaboración persistida. Sólo metadata/hash, sin devolver valores al receptor. */
    VerificacionCamposDemanda verificarCamposColaboracion(
        com.controllocal.service.colaboracion.ReferenciaParteConcedida prueba,Actor receptorReal);
}
