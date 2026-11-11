package com.controllocal.service.encargo;

import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDate;

/** CAT-R05/E14: almacena la condición declarada, sin calcular devengo/cobro ni inventar el precio final. */
@Component
public final class CondicionesDeEncargo {
    private final ObjectMapper json;
    private final DeclaracionMensualEncargo mensuales;
    public CondicionesDeEncargo(ObjectMapper json) { this.json=json;this.mensuales=new DeclaracionMensualEncargo(json); }
    /** Una edición parcial conserva lo conocido; un cambio de concepto no reetiqueta su cantidad anterior. */
    public EncargoService.Condiciones completar(CondicionEconomicaCaptacion anterior,EncargoService.Condiciones nueva) {
        var a=ficha(anterior); if(nueva==null) return a; if(a==null) return nueva;
        boolean concepto=(nueva.tipoComision()!=null&&!nueva.tipoComision().equals(a.tipoComision()))
                ||(nueva.baseCalculo()!=null&&!nueva.baseCalculo().equals(a.baseCalculo()));
        boolean condicionCambio=concepto
                || (nueva.hitoDevengo()!=null&&!nueva.hitoDevengo().equals(a.hitoDevengo()))
                || (nueva.exigibilidad()!=null&&!nueva.exigibilidad().equals(a.exigibilidad()));
        return new EncargoService.Condiciones(elegir(nueva.precioPedido(),a.precioPedido()),elegir(nueva.monedaPrecio(),a.monedaPrecio()),
                elegir(nueva.periodoPrecio(),a.periodoPrecio()),elegir(nueva.tipoComision(),a.tipoComision()),
                elegir(nueva.baseCalculo(),concepto?null:a.baseCalculo()),elegir(nueva.valorComision(),concepto?null:a.valorComision()),
                elegir(nueva.monedaComision(),concepto?null:a.monedaComision()),elegir(nueva.tratamientoIgv(),a.tratamientoIgv()),
                elegir(nueva.motivoSinComision(),a.motivoSinComision()),elegir(nueva.hitoDevengo(),a.hitoDevengo()),
                elegir(nueva.parteObligada(),a.parteObligada()),elegir(nueva.beneficiario(),a.beneficiario()),
                elegir(nueva.exigibilidad(),a.exigibilidad()),elegir(nueva.procedencia(),a.procedencia()),
                elegir(nueva.tramos(),concepto?null:a.tramos()),elegir(nueva.composicionMensual(),a.composicionMensual()),
                elegir(nueva.ejecucion(),condicionCambio?null:a.ejecucion()));
    }
    private static <T> T elegir(T valor,T anterior) { return valor==null?anterior:valor; }
    public CondicionEconomicaCaptacion nueva(long org,OperacionInmobiliaria operacion,
                                             EncargoService.Condiciones d) {
        if(d==null) return null;
        if(d.precioPedido()!=null && d.precioPedido().signum()<=0)
            throw new ReglaNegocioException("El precio pedido declarado debe ser mayor que cero.");
        if(d.valorComision()!=null && d.valorComision().signum()<0)
            throw new ReglaNegocioException("La comisión declarada no puede ser negativa.");
        String moneda=moneda(d.monedaPrecio()), monedaComision=moneda(d.monedaComision());
        String tipo=texto(d.tipoComision()), base=texto(d.baseCalculo()), igv=texto(d.tratamientoIgv());
        if(tipo!=null && !Set.of("E","P","F").contains(tipo))
            throw new ReglaNegocioException("El tipo de comisión no está definido.");
        if(base!=null && !Set.of("R","V","N").contains(base))
            throw new ReglaNegocioException("La base de comisión no está definida.");
        if(igv!=null && !Set.of("I","A","N").contains(igv))
            throw new ReglaNegocioException("El tratamiento del IGV no está definido.");
        if(tipo!=null && base!=null && !(("E".equals(tipo)&&"R".equals(base))
                || ("P".equals(tipo)&&Set.of("R","V").contains(base)) || ("F".equals(tipo)&&"N".equals(base))))
            throw new ReglaNegocioException("El tipo y la base de comisión son incompatibles.");
        if(operacion!=null && tipo!=null && base!=null)
            CondicionesEconomicas.exigirBaseCoherente(operacion.codigo(),tipo,base);
        // V17 ck_condicion_tipo_base: E/P conservan la moneda explícita de su base, no otra moneda por defecto.
        if(tipo!=null && Set.of("E","P").contains(tipo) && base!=null && moneda!=null) {
            if(monedaComision!=null && !moneda.equals(monedaComision))
                throw new ReglaNegocioException("La comisión derivada conserva la moneda de su base explícita.");
            monedaComision=moneda;
        }
        if(d.tramos()!=null) for(var t:d.tramos()) {
            if(t==null || (t.importe()!=null && t.porcentaje()!=null))
                throw new ReglaNegocioException("Un tramo conserva importe o porcentaje, sin mezclar ambos conceptos.");
            if(t.importe()!=null && t.importe().signum()<0)
                throw new ReglaNegocioException("El importe del tramo no puede ser negativo.");
            if(texto(t.moneda())!=null) moneda(t.moneda());
            if(t.porcentaje()!=null && t.porcentaje().signum()<0)
                throw new ReglaNegocioException("El porcentaje del tramo no puede ser negativo.");
        }
        CondicionEconomicaCaptacion c=new CondicionEconomicaCaptacion(); c.setOrganizacionId(org);
        c.setTipoOperacion(operacion==null?null:operacion.codigo()); c.setImporteReferencia(d.precioPedido());
        c.setMonedaReferencia(moneda); c.setPeriodoPrecio(texto(d.periodoPrecio()));
        c.setTipoComision(tipo); c.setBaseCalculo(base); c.setValorComision(d.valorComision());
        c.setMonedaComision(monedaComision); c.setTratamientoIgv(igv);
        c.setMotivoSinComision(texto(d.motivoSinComision())); c.setHitoDevengo(texto(d.hitoDevengo()));
        c.setParteObligada(texto(d.parteObligada())); c.setBeneficiario(texto(d.beneficiario()));
        c.setExigibilidad(texto(d.exigibilidad())); c.setProcedenciaPacto(texto(d.procedencia()));
        c.setTramos(d.tramos()==null?null:escribir(d.tramos()));
        c.setEjecucionPacto(d.ejecucion()==null?null:escribir(d.ejecucion()));
        c.setComposicionMensual(mensuales.preparar(d.composicionMensual(),d.procedencia()));return c;
    }
    public List<String> faltantes(CondicionEconomicaCaptacion c,OperacionInmobiliaria op) {
        List<String> f=new ArrayList<>();
        if(c==null) return List.of("CONDICIONES_COMERCIALES","PACTO_COMISION");
        if(c.getImporteReferencia()==null) f.add("PRECIO_PEDIDO");
        if(c.getMonedaReferencia()==null) f.add("MONEDA_PRECIO");
        if(op==OperacionInmobiliaria.ALQUILER && c.getPeriodoPrecio()==null) f.add("PERIODO_PRECIO");
        if(c.getTipoComision()==null) f.add("TIPO_COMISION");
        if(c.getBaseCalculo()==null) f.add("BASE_COMISION");
        if(c.getValorComision()==null) f.add("VALOR_COMISION");
        if(c.getMonedaComision()==null) f.add("MONEDA_COMISION");
        if(c.getTratamientoIgv()==null) f.add("TRATAMIENTO_IGV");
        if(c.getHitoDevengo()==null) f.add("HITO_DEVENGO");
        if(c.getParteObligada()==null) f.add("PARTE_OBLIGADA");
        if(c.getBeneficiario()==null) f.add("BENEFICIARIO_COMISION");
        if(c.getExigibilidad()==null) f.add("EXIGIBILIDAD");
        if(c.getProcedenciaPacto()==null) f.add("PROCEDENCIA_PACTO");
        if(c.getValorComision()!=null && c.getValorComision().signum()==0 && c.getMotivoSinComision()==null)
            f.add("MOTIVO_SIN_COMISION");
        var tramos=tramos(c);
        for(int i=0;i<tramos.size();i++) {
            var t=tramos.get(i); String prefijo="TRAMOS["+i+"]";
            if(texto(t.hito())==null) f.add(prefijo+".HITO");
            if(texto(t.exigibilidad())==null) f.add(prefijo+".EXIGIBILIDAD");
            if(t.importe()==null && t.porcentaje()==null) f.add(prefijo+".CONDICION");
            if(t.importe()!=null && texto(t.moneda())==null) f.add(prefijo+".MONEDA");
            if(t.importe()!=null && c.getMonedaComision()!=null && t.moneda()!=null
                    && !c.getMonedaComision().equals(t.moneda())) f.add(prefijo+".MONEDA_NO_COMPARABLE");
        }
        if("F".equals(c.getTipoComision()) && c.getValorComision()!=null && !tramos.isEmpty()
                && tramos.stream().allMatch(t->t.importe()!=null && java.util.Objects.equals(c.getMonedaComision(),t.moneda()))) {
            BigDecimal suma=tramos.stream().map(EncargoService.Tramo::importe).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(suma.compareTo(c.getValorComision())!=0) f.add("TRAMOS_TOTAL_DIFIERE_DEL_PACTO");
        }
        return List.copyOf(f);
    }
    public EncargoService.Condiciones ficha(CondicionEconomicaCaptacion c) {
        return c==null?null:new EncargoService.Condiciones(c.getImporteReferencia(),c.getMonedaReferencia(),
                c.getPeriodoPrecio(),c.getTipoComision(),c.getBaseCalculo(),c.getValorComision(),
                c.getMonedaComision(),c.getTratamientoIgv(),c.getMotivoSinComision(),c.getHitoDevengo(),
                c.getParteObligada(),c.getBeneficiario(),c.getExigibilidad(),c.getProcedenciaPacto(),tramos(c),mensuales.declarada(c),ejecucion(c));
    }
    /** La declaración de otra tarifa/configuración permanece como antecedente, no como total actual. */
    public EncargoService.Condiciones fichaParaContexto(CondicionEconomicaCaptacion c,String configuracion) {
        var d=ficha(c);if(d==null||d.composicionMensual()==null||mensuales.corresponde(c,configuracion))return d;
        return new EncargoService.Condiciones(d.precioPedido(),d.monedaPrecio(),d.periodoPrecio(),d.tipoComision(),d.baseCalculo(),
                d.valorComision(),d.monedaComision(),d.tratamientoIgv(),d.motivoSinComision(),d.hitoDevengo(),d.parteObligada(),
                d.beneficiario(),d.exigibilidad(),d.procedencia(),d.tramos(),null,d.ejecucion());
    }
    /** Sólo la escritura explícita del pacto fija una base; nunca al leer ni al buscar coincidencias. */
    public void vincularMensual(CondicionEconomicaCaptacion c,String configuracion){mensuales.vincular(c,configuracion);}
    public com.controllocal.service.matching.ContratoMatching.Dato totalMensual(CondicionEconomicaCaptacion c,
            String operacion,String configuracion,LocalDate desde,LocalDate hasta,String evidencia,Instant corte,String modalidadPrecio) {
        return mensuales.total(c,operacion,configuracion,desde,hasta,evidencia,corte,modalidadPrecio);
    }
    private List<EncargoService.Tramo> tramos(CondicionEconomicaCaptacion c) {
        if(c.getTramos()==null) return List.of();
        try { return json.readValue(c.getTramos(),new TypeReference<List<EncargoService.Tramo>>(){}); }
        catch(Exception fallo) { throw new IllegalStateException("El pacto conserva tramos ilegibles.",fallo); }
    }
    private PactosDeEncargo.EjecucionPacto ejecucion(CondicionEconomicaCaptacion c) {
        if(c.getEjecucionPacto()==null)return null;
        try { return json.readValue(c.getEjecucionPacto(),PactosDeEncargo.EjecucionPacto.class); }
        catch(Exception fallo) { throw new IllegalStateException("El pacto conserva condiciones ejecutables ilegibles.",fallo); }
    }
    private String escribir(Object valor) {
        try { return json.writeValueAsString(valor); }
        catch(Exception fallo) { throw new IllegalStateException("No se pudo conservar el pacto.",fallo); }
    }
    private static String texto(String valor) { return valor==null||valor.isBlank()?null:valor.trim(); }
    private static String moneda(String valor) { return texto(valor)==null?null:CondicionesEconomicas.moneda(valor,"declarada"); }
}
