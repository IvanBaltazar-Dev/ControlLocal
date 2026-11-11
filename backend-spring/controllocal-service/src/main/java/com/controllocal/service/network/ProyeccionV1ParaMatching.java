package com.controllocal.service.network;

import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.ValorLogico;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.controllocal.domain.inmueble.TipoDato.*;
import static com.controllocal.service.matching.ContratoMatching.*;

/** Decodifica sólo el contenido ya aprobado. No consulta expedientes ni completa datos ausentes. */
public final class ProyeccionV1ParaMatching {
    private ProyeccionV1ParaMatching() { }
    public static List<Dato> datos(Map<String,Object> publicos,String configuracion,Instant corteCore) {
        Objects.requireNonNull(corteCore,"El corte lo proporciona Core.");
        if(publicos==null||!PoliticaProyeccionNetworkV1.CAMPOS.containsAll(publicos.keySet()))
            throw new IllegalStateException("La proyección Network contiene campos fuera de V1.");
        String operacion=null;
        if(publicos.containsKey("ENCARGO:operacion"))operacion=texto(campo(publicos.get("ENCARGO:operacion")),"valor",true);
        List<Dato> salida=new ArrayList<>();
        for(var e:publicos.entrySet()) {
            Map<String,Object> c=campo(e.getValue());Object v=c.get("valor");
            if(v==null)throw new IllegalStateException("Un campo público conocido no conserva su valor.");
            String clave=e.getKey(),concepto=texto(c,"concepto",false),unidad=texto(c,"unidad",false),
                    moneda=texto(c,"moneda",false),periodo=texto(c,"periodo",false);
            com.controllocal.domain.inmueble.TipoDato tipo;
            ValorLogico valor;
            switch(clave) {
                case "ENCARGO:operacion","PROPIEDAD:tipo_propiedad","PROPIEDAD:distrito" -> {
                    tipo=LISTA;valor=ValorLogico.deTexto(texto(c,"valor",true));
                }
                case "PROPIEDAD:zonificacion","PROPIEDAD:piso" -> {
                    tipo=TEXTO;valor=ValorLogico.deTexto(texto(c,"valor",true));
                }
                case "PROPIEDAD:dormitorios","PROPIEDAD:estacionamientos","PROPIEDAD:ascensores" -> {
                    tipo=ENTERO;var numero=numero(v);
                    if(numero.stripTrailingZeros().scale()>0)throw new IllegalStateException("La cantidad pública no es entera.");
                    valor=ValorLogico.deNumero(numero);
                }
                case "PROPIEDAD:metraje_total","PROPIEDAD:metraje_construido","PROPIEDAD:area_terreno","PROPIEDAD:banos" -> {
                    tipo=DECIMAL;valor=ValorLogico.deNumero(numero(v));
                }
                case "ENCARGO:se_ofrece_amoblado","ENCARGO:se_entrega_implementado","ENCARGO:disponibilidad" -> {
                    if(!(v instanceof Boolean b))throw new IllegalStateException("El hecho público no es booleano.");
                    tipo=BOOLEANO;valor=ValorLogico.deBooleano(b);
                }
                case "ENCARGO:precio_pedido" -> {
                    tipo=IMPORTE;
                    if(!Set.of("PEN","USD").contains(Objects.toString(moneda,"")))throw new IllegalStateException("Precio público sin moneda válida.");
                    valor=ValorLogico.deImporte(numero(v),moneda);
                    if("PRECIO_VENTA".equals(concepto))clave="ENCARGO:precio_venta";
                    else if("PRECIO_ALQUILER".equals(concepto))clave="ENCARGO:precio_alquiler";
                    else throw new IllegalStateException("El precio público no conserva su concepto.");
                    // Alquiler pedido NO es total mensual obligatorio: no se agregan cargos desconocidos.
                }
                default -> throw new IllegalStateException("Campo Network sin contrato de comparación.");
            }
            salida.add(new Dato(clave,tipo,valor,null,false,
                    new ContextoValor(concepto,unidad,moneda,periodo,operacion,configuracion,
                            texto(c,"jurisdiccion",false),null,texto(c,"versionFuente",false)),
                    new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,"NETWORK_V1_AUTORIZADA",null,null,null)));
        }
        var alquiler=salida.stream().filter(d->d.clave().equals("ENCARGO:precio_alquiler")).findFirst();
        if(alquiler.isPresent()) {
            var d=alquiler.get();
            salida.add(com.controllocal.service.matching.ComposicionMensualMatching.total(TOTAL_MENSUAL_OBLIGATORIO,
                    List.of(new ComponenteMensual("ALQUILER_PUBLICADO",d,TratamientoMensual.EXIGIBLE)),false,
                    new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,d.contexto().moneda(),"MENSUAL",d.contexto().operacion(),configuracion),
                    d.metadatos(),corteCore));
        }
        return List.copyOf(salida);
    }
    private static Map<String,Object> campo(Object objeto) {
        if(!(objeto instanceof Map<?,?> m))throw new IllegalStateException("El campo Network no conserva su estructura.");
        Map<String,Object> c=new LinkedHashMap<>();
        for(var e:m.entrySet()) {
            if(!(e.getKey() instanceof String s)||!Set.of("valor","concepto","unidad","moneda","periodo","jurisdiccion","versionFuente").contains(s))
                throw new IllegalStateException("Metadata ajena a la proyección pública.");
            c.put(s,e.getValue());
        }
        return c;
    }
    private static String texto(Map<String,Object> c,String clave,boolean requerido) {
        Object o=c.get(clave);if(o==null&&!requerido)return null;
        if(!(o instanceof String s)||s.isBlank())throw new IllegalStateException("El campo público no conserva su texto tipado.");
        return s;
    }
    private static BigDecimal numero(Object o) {
        if(!(o instanceof Number))throw new IllegalStateException("El campo público no conserva su número.");
        try{return new BigDecimal(o.toString());}
        catch(NumberFormatException e){throw new IllegalStateException("Número público inválido.",e);}
    }
}
