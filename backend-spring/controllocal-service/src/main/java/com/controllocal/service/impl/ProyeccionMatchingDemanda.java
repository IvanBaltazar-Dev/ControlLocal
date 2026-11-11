package com.controllocal.service.impl;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.demanda.NormalizadorCriteriosDemanda.Normalizado;
import com.controllocal.service.demanda.ProyeccionComercialDemanda;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.matching.ContratoMatching;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.soporte.ValorLogico;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Una proyección del agregado autorizado, sin Persona ni otro parser del motor. */
final class ProyeccionMatchingDemanda {
    private ProyeccionMatchingDemanda() { }
    static Consulta proyectar(RequerimientoCliente r,List<Normalizado> gobernados,Modalidad modalidad,
                              String regla,Instant corte) {
        var hoy=corte.atZone(ZoneId.of("America/Lima")).toLocalDate();
        if(r.getVigenteDesde()!=null&&hoy.isBefore(r.getVigenteDesde())
                ||r.getVigenteHasta()!=null&&hoy.isAfter(r.getVigenteHasta()))
            throw new ReglaNegocioException("La demanda no está vigente en el corte de matching.");
        List<Criterio> criterios=new ArrayList<>();
        String op=r.operacion().name();
        var meta=meta(EstadoDato.CONOCIDO,null);
        criterios.addAll(ProyeccionComercialDemanda.criteriosBase(op,List.copyOf(r.getTipos()),
                r.getDistritos().stream().map(d->Long.toString(d.getId())).toList()));
        if(r.getPresupuestoMin()!=null||r.getPresupuestoMax()!=null) {
            boolean alquiler=r.operacion()==OperacionInmobiliaria.ALQUILER;
            String concepto=alquiler?"TOTAL_MENSUAL_OBLIGATORIO":"PRECIO_VENTA";
            boolean comparable=concepto.equals(r.getConceptoPresupuesto())
                    &&(alquiler?"MENSUAL".equals(r.getPeriodoPresupuesto()):r.getPeriodoPresupuesto()==null)
                    &&r.getMoneda()!=null;
            var presupuestoContexto=new ContextoValor(r.getConceptoPresupuesto(),null,r.getMoneda(),r.getPeriodoPresupuesto(),op,null);
            var estado=comparable?meta:meta(EstadoDato.FALTANTE,r.getMoneda()==null?Causa.MONEDA_NO_COMPARABLE:Causa.DATO_FALTANTE);
            criterios.add(criterio("presupuesto",List.of(rango(alquiler?ContratoMatching.TOTAL_MENSUAL_OBLIGATORIO:ContratoMatching.PRECIO_VENTA,
                    TipoDato.IMPORTE,r.getPresupuestoMin(),r.getPresupuestoMax(),r.getMoneda(),presupuestoContexto,estado))));
        }
        if(r.getMetrajeMin()!=null||r.getMetrajeMax()!=null) {
            // Es la intención conservada, no un atributo físico ni una equivalencia de superficies.
            // Su causa explícita evita recuperar/evaluar un área hasta identificar el concepto.
            criterios.add(criterio("superficieSinConcepto",List.of(rango("PENDIENTE:superficie_concepto",TipoDato.DECIMAL,
                    r.getMetrajeMin(),r.getMetrajeMax(),null,new ContextoValor(null,null,null,null,op,null),
                    meta(EstadoDato.FALTANTE,Causa.DATO_FALTANTE)))));
        }
        criterios.addAll(gobernados.stream().map(Normalizado::criterio).toList());
        return new Consulta("demanda:"+r.getId(),r.getVersion(),modalidad,criterios,corte,regla);
    }
    private static Criterio criterio(String clave,List<Condicion> alternativas) {
        return new Criterio("base:"+clave,"base:"+clave,Exigencia.OBLIGATORIO,null,alternativas);
    }
    private static MetadatosDato meta(EstadoDato estado,Causa causa) {
        return new MetadatosDato(estado,causa,null,null,null,null,null,null,null);
    }
    private static Condicion rango(String selector,TipoDato tipo,BigDecimal min,BigDecimal max,
            String moneda,ContextoValor contexto,MetadatosDato meta) {
        var operador=min!=null&&max!=null?OperadorCriterio.ENTRE:min!=null?OperadorCriterio.MINIMO:OperadorCriterio.MAXIMO;
        ValorLogico valor=new ValorLogico(null,min!=null?min:max,null,null,moneda,null);
        ValorLogico hasta=min!=null&&max!=null?new ValorLogico(null,max,null,null,moneda,null):null;
        return new Condicion(selector,tipo,operador,valor,hasta,false,contexto,meta);
    }
}
