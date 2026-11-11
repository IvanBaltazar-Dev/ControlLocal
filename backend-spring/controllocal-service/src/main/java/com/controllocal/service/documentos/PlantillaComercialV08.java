package com.controllocal.service.documentos;

import com.controllocal.domain.auditoria.RastroValorGobernado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.captura.RotulosDeSuperficie;
import com.controllocal.service.encargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.TratamientoMensual;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Proyección V08: procedencia C6 y rótulos de superficie1.14; no transporta el expediente. */
@Component
public class PlantillaComercialV08 {
    public record Proyeccion(Map<String,Object> datos,String huellaOrigen,long propiedad,long responsable) { }
    /** Hechos físicos permitidos para utilidad privada, sin crear un Encargo o una pieza. */
    public record ProyeccionFisica(Map<String,Object> datos,long versionFisica) { }
    private final AccesoEncargo acceso;private final EncargoService ciclo;private final PropiedadRepository propiedades;
    private final LectorPorAutoridad lector;private final AtributosGobernados fisicos;
    private final AtributosDeEncargo comerciales;private final RastroValorGobernadoRepository rastros;
    private final ContextosDeActividad contextos;
    private final PrecioPropiedadRepository precios;private final ComposicionEncargoRepository composiciones;
    private final SeleccionInmueblePieza seleccion;
    private final Documentos json;private final Map<String,Object> catalogo;
    public PlantillaComercialV08(AccesoEncargo acceso,EncargoService ciclo,PropiedadRepository propiedades,LectorPorAutoridad lector,
            AtributosGobernados fisicos,AtributosDeEncargo comerciales,RastroValorGobernadoRepository rastros,
            ContextosDeActividad contextos,Documentos json,ObjectMapper mapper,
            PrecioPropiedadRepository precios,ComposicionEncargoRepository composiciones,SeleccionInmueblePieza seleccion){
        this.acceso=acceso;this.ciclo=ciclo;this.propiedades=propiedades;this.lector=lector;this.fisicos=fisicos;this.comerciales=comerciales;
        this.rastros=rastros;this.contextos=contextos;this.json=json;
        this.precios=precios;this.composiciones=composiciones;this.seleccion=seleccion;
        try(var in=getClass().getResourceAsStream("/piezas/v08/catalogo-proyeccion.json")){
            if(in==null)throw new IllegalStateException("Falta el contrato aprobado de proyección V08.");
            catalogo=mapper.readValue(in,new TypeReference<Map<String,Object>>(){});
        }catch(IOException e){throw new IllegalStateException("El contrato V08 no se pudo cargar.",e);}
    }
    public Proyeccion proyectar(PiezasComercialesService.Preparacion d,UUID pieza,long version,String salida,Actor actor){
        contextos.exigirAgente(actor);Captacion c=acceso.leer(d.encargo(),actor);acceso.exigirResponsable(c,actor);
        var objeto=seleccion.elegir(c,d.propiedadFicha(),actor);Propiedad p=objeto.propiedad();
        if(!objeto.pertenece()&&"DIFUSION_AUTORIZADA".equals(salida))throw new ReglaNegocioException("Elige una unidad comprendida en el objeto actual del Encargo antes de difundir.");
        var contextoActual=acceso.contexto(d.encargo(),actor);
        if(contextoActual.getVersion()!=d.versionEncargo()
                ||propiedades.versionFisicaActual(actor.idOrganizacion(),p.getId()).orElseThrow(()->new NoEncontradoException("Inmueble"))!=d.versionFisica()
                ||c.getVersion()!=d.versionEncargo()||p.getVersionFisica()!=d.versionFisica())
            throw new ConflictoException("El Encargo o inmueble cambió; vuelve a preparar la pieza.");
        var origenComposicion=objeto.composicion()==null?null:composicion(c,objeto.composicion());
        var ficha=ciclo.consultar(c.getId(),actor);
        var meta=json.comoMapa(p.getMetadatosAtributos());
        var valores=lector.de(actor.idOrganizacion(),p);
        var condiciones=lector.deEncargo(c.getId());
        var definicionesFisicas=fisicos.definicionesDe(actor.idOrganizacion(),p.getTipoInmueble());
        var definicionesComerciales=comerciales.definicionesDe(actor.idOrganizacion(),Comercializacion.de(c,p));
        Map<String,Object> atributos=atributos("propiedad",valores,definicionesFisicas,
                historia(actor.idOrganizacion(),"PROPIEDAD",p.getId()),meta);
        Map<String,Object> pactados=atributos("encargo",condiciones,
                definicionesComerciales,historia(actor.idOrganizacion(),"ENCARGO",c.getId()),Map.of());
        var fechaPropiedad=proveniencia(p.getFechaRegistro(),null);
        var fechaEncargo=proveniencia(null,null);
        if(c.getFechaCaptacion()!=null)fechaEncargo.put("observadoEn",c.getFechaCaptacion().toString());
        var fechaPrecio=origenPrecio(c,objeto.composicion());
        var huecos=new ArrayList<Map<String,Object>>();
        var mantenimiento=mantenimiento(c,ficha,atributos,valores.valor("cuota_mantenimiento"),huecos,objeto.composicion()!=null);
        Map<String,Object> inmueble=new LinkedHashMap<>();
        inmueble.put("tipo",dato(p.getTipoInmueble(),fechaPropiedad));
        inmueble.put("ubicacion",ubicacion(p));
        inmueble.put("atributos",atributos);inmueble.put("fueraDeCatalogo",huecos);inmueble.put("fotos",List.of());
        var precio=new LinkedHashMap<String,Object>();
        boolean precioDelObjeto=objeto.composicion()==null||ficha.vigencia()!=null&&ficha.vigencia().habilitado();
        precio.put("importe",precioDelObjeto?dato(ficha.condiciones()==null?null:ficha.condiciones().precioPedido(),fechaPrecio):
                faltante("El precio de la composición actual no está acreditado por el Encargo habilitado."));
        precio.put("moneda",precioDelObjeto?dato(ficha.condiciones()==null?null:ficha.condiciones().monedaPrecio(),fechaPrecio):
                faltante("La moneda del precio de la composición actual no está acreditada."));
        precio.put("hito","U");
        // V08§3.3 define la renta de Core como mensual; no se extiende esa definición al mantenimiento.
        if(c.operacion()==OperacionInmobiliaria.ALQUILER)precio.put("periodo","MENSUAL");
        var sujeto=sujeto(objeto,mantenimiento,origenComposicion);
        if(sujeto!=null)precio.put("sujeto",sujeto);
        var encargo=new LinkedHashMap<String,Object>();encargo.put("id",Long.toString(c.getId()));
        // El renderer exige versión material ≥1; CAS conserva por separado la revisión de fila Core.
        encargo.put("version",c.getVersionContenido());encargo.put("estado",switch(c.estadoActual()){
            case Captacion.APROBADO->"APROBADO";case Captacion.FINALIZADO->"FINALIZADO";
            case Captacion.RESCINDIDO->"RESCINDIDO";default->"EN_PREPARACION";});
        encargo.put("vigenteHasta",ficha.hasta()==null?null:ficha.hasta().toString());
        encargo.put("operacion",dato(c.operacion().name(),fechaEncargo));encargo.put("precio",precio);
        encargo.put("condiciones",pactados);
        var piezaDatos=new LinkedHashMap<String,Object>();piezaDatos.put("id",pieza.toString());
        piezaDatos.put("encargo",Long.toString(c.getId()));piezaDatos.put("versionContenido",version);
        piezaDatos.put("plantilla","BROX-FICHA");piezaDatos.put("versionPlantilla","V08");
        piezaDatos.put("naturaleza","FUENTE_REAL");piezaDatos.put("salida",salida);
        piezaDatos.put("fechaCorte",contextos.hoy().toString());
        var origenes=new ArrayList<Map<String,Object>>();
        origenes.add(origen("PROPIEDAD:"+p.getId(),"Versión física "+p.getVersionFisica(),p.getFechaActualizacion()==null?p.getFechaRegistro():p.getFechaActualizacion()));
        origenes.add(origen("ENCARGO:"+c.getId(),"Revisión "+c.getVersion()+"; contenido "+c.getVersionContenido(),c.getFechaActualizacion()));
        if(c.getCondicionEconomica()!=null)origenes.add(origen("CONDICION_ECONOMICA:"+c.getCondicionEconomica().getId(),"Versión económica del Encargo",c.getCondicionEconomica().getFechaCreacion()));
        if(origenComposicion!=null)origenes.add(origen("COMPOSICION:"+origenComposicion.getId(),
                "Versión "+origenComposicion.getVersionContenido(),origenComposicion.getRegistradaEn()));
        piezaDatos.put("versionesOrigen",origenes);
        var datos=new LinkedHashMap<String,Object>();datos.put("contrato","brox-ficha-datos");datos.put("versionContrato","1.14.0");
        datos.put("pieza",piezaDatos);datos.put("procedencias",Map.of());datos.put("inmueble",inmueble);datos.put("encargo",encargo);
        var firma=new LinkedHashMap<String,Object>();
        if(d.contacto()==null)firma.put("estado","FALTANTE");
        else {
            validarContacto(d.contacto());firma.put("estado","VIGENTE");firma.put("naturaleza","REAL");
            firma.put("condicion","INDIVIDUAL".equals(contextoActual.getModalidadOrganizacion())?"INDEPENDIENTE":"AGENTE_DE_ORGANIZACION");
            firma.put("nombre",contextoActual.getNombreProfesional());
            if(!"INDIVIDUAL".equals(contextoActual.getModalidadOrganizacion()))firma.put("organizacion",contextoActual.getNombreOrganizacion());
            firma.put("contactos",List.of(Map.of("tipo",d.contacto().tipo(),"valor",d.contacto().valor(),"principal",true,
                    "autorizadoParaSalida",true)));
        }
        datos.put("marcaYContacto",firma);
        datos.put("presentacion",Map.of("perfil",switch(p.getTipoInmueble()){
            case "D"->"departamento";case "C"->"casa";case "T"->"terreno";case "L"->"local";
            case "O"->"oficina";case "A"->"almacen";case "E"->"estacionamiento";default->"otro";}));
        // La fecha de render no envejece artificialmente los datos; vigencia se revalida aparte.
        var identidad=new LinkedHashMap<String,Object>();identidad.put("encargo",c.getVersion());
        identidad.put("fisica",p.getVersionFisica());identidad.put("valores",atributos);identidad.put("condiciones",pactados);
        identidad.put("precio",precio);identidad.put("firma",firma);identidad.put("metadatos",meta);
        identidad.put("huecos",huecos);identidad.put("composicion",objeto.composicion());identidad.put("propiedadFicha",p.getId());
        // El origen conserva sus cifras aunque la proyección no pueda presentarlas por falta de procedencia.
        identidad.put("economia",ficha.condiciones());
        identidad.put("distrito",p.getDistrito());identidad.put("zona",p.getZonaUrbanizacion());
        identidad.put("definiciones",List.of(definicionesFisicas.values().stream()
                .map(a->a.getClave()+":"+a.getVersionDefinicion()).sorted().toList(),
                definicionesComerciales.values().stream()
                        .map(a->a.getClave()+":"+a.getVersionDefinicion()).sorted().toList()));
        return new Proyeccion(datos,Idempotencia.huellaExacta(json.objetoCanonico(identidad)),p.getId(),c.getAgente().getId());
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public ProyeccionFisica prepararFisicaPrivada(long propiedad,Actor actor){
        contextos.exigirAgente(actor);
        Propiedad p=seleccion.exigirLecturaHistorica(actor.idOrganizacion(),propiedad,actor);
        var atributos=atributos("propiedad",lector.de(actor.idOrganizacion(),p),
                fisicos.definicionesDe(actor.idOrganizacion(),p.getTipoInmueble()),
                historia(actor.idOrganizacion(),"PROPIEDAD",propiedad),json.comoMapa(p.getMetadatosAtributos()));
        var datos=new LinkedHashMap<String,Object>();
        datos.put("tipo",dato(p.getTipoInmueble(),proveniencia(p.getFechaRegistro(),null)));
        datos.put("ubicacion",ubicacion(p));datos.put("atributos",atributos);
        seleccion.exigirLecturaHistorica(actor.idOrganizacion(),propiedad,actor);
        return new ProyeccionFisica(Map.copyOf(datos),p.getVersionFisica());
    }
    private Map<String,Object> ubicacion(Propiedad p){
        var fecha=proveniencia(p.getFechaRegistro(),null);var ubicacion=new LinkedHashMap<String,Object>();
        ubicacion.put("distrito",dato(p.getDistrito(),fecha));
        // V08 §3.2 exige ambos wrappers; desconocido no es un requisito nuevo.
        ubicacion.put("zonaUrbanizacion",dato(p.getZonaUrbanizacion(),fecha));return ubicacion;
    }
    private Map<String,RastroValorGobernado> historia(long org,String sujeto,long id){
        var mapa=new LinkedHashMap<String,RastroValorGobernado>();
        rastros.historiaDelAgregado(org,sujeto,id).forEach(r->mapa.put(r.getClave(),r));return mapa;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> atributos(String sujeto,ValoresGobernados valores,Map<String,CatalogoAtributo> definiciones,
            Map<String,RastroValorGobernado> historia,Map<String,Object> metadatos){
        Map<String,Object> permitidas=(Map<String,Object>)catalogo.get(sujeto);var salida=new LinkedHashMap<String,Object>();
        for(var e:definiciones.entrySet()){
            String clave=e.getKey();var def=e.getValue();
            // El catálogo de tipos del renderer también contiene claves privadas; no es una concesión.
            if(!permitidas.containsKey(clave)||!def.isActivo()||PRIVADAS.contains(clave))continue;
            Object metadato=metadatos.get(clave);
            if(metadato instanceof Map<?,?> m && "NO_APLICABLE".equals(m.get("estado")))continue;
            var r=historia.get(clave);Object valor=valor(valores.valor(clave));
            boolean explicito=metadato instanceof Map<?,?> m&&("FALTANTE".equals(m.get("estado"))||Boolean.TRUE.equals(m.get("noSabe")));
            if(valor==null&&!explicito)continue;
            var fuente=proveniencia(r==null?null:r.getRegistradoEn(),r);
            if(fuente.isEmpty()&&metadato instanceof Map<?,?> m&&m.get("observadoEn") instanceof String fecha)fuente.put("observadoEn",fecha);
            // V92 conserva importe/moneda en Core; V08 conserva la cifra histórica y H-18 su contexto completo.
            if("cuota_mantenimiento".equals(clave)&&valores.valor(clave)!=null)valor=valores.valor(clave).numero();
            var dato=explicito?faltante("El dato se declaró desconocido para este contexto."):dato(valor,fuente);
            if(def.getUnidad()!=null&&!"cuota_mantenimiento".equals(clave))dato.put("unidad",def.getUnidad());
            if("propiedad".equals(sujeto)&&"m²".equals(def.getUnidad())&&"VIGENTE".equals(dato.get("estado"))){
                String concepto=metadato instanceof Map<?,?> m&&m.get("concepto") instanceof String s?s:null;
                var rotulo=RotulosDeSuperficie.deConcepto(concepto);
                rotulo.ifPresent(nombreRotulo->dato.put("rotulo",nombreRotulo));
                dato.put("definicion",rotulo.map(nombreRotulo->"Corresponde al "+nombreRotulo.toLowerCase(Locale.ROOT)+".")
                        .orElse("Superficie declarada, concepto por precisar."));
            }
            salida.put(clave,dato);
        }
        return salida;
    }
    private static Object valor(ValorLogico v){
        if(v==null)return null;if(v.moneda()!=null)return Map.of("importe",v.numero(),"moneda",v.moneda());
        if(v.numero()!=null)return v.numero();if(v.booleano()!=null)return v.booleano();
        if(v.fecha()!=null)return v.fecha().toString();if(v.valores()!=null)return v.valores();return v.texto();
    }
    private static Map<String,Object> dato(Object valor,Map<String,Object> fuente){
        var d=new LinkedHashMap<String,Object>();
        if(valor==null||valor instanceof String s&&s.isBlank()){d.put("estado","FALTANTE");return d;}
        if(!fuente.containsKey("registradoEn")&&!fuente.containsKey("observadoEn"))return faltante("El valor consta en Core, pero no consta la fecha de su procedencia individual para esta salida.");
        d.put("estado","VIGENTE");d.put("valor",valor);d.put("procedencia",fuente);return d;
    }
    private static Map<String,Object> proveniencia(OffsetDateTime fecha,RastroValorGobernado r){
        var p=new LinkedHashMap<String,Object>();
        if(fecha!=null)p.put("registradoEn",fecha.atZoneSameInstant(ZoneId.of("America/Lima")).toLocalDate().toString());
        if(r!=null){if(r.getNaturaleza()!=null)p.put("naturaleza",r.getNaturaleza());
            if(r.getObservadoEn()!=null)p.put("observadoEn",r.getObservadoEn().toString());
            if(r.getConfianza()!=null)p.put("confianza",r.getConfianza());}
        return p;
    }
    private static final Set<String> PRIVADAS=Set.of("titular","titulares","propietario","propietarios","dni","documento","numeroDocumento","numero_documento","tipoDocumento","partida","partida_registral","partidaRegistral","firma","firmas","direccion","direccionExacta","referenciaInterna","referencia_interna","interiorUnidad","interior_unidad","geo_lat","geo_long","telefonoPropietario","correoPropietario","expediente","precioReservado","precio_reservado","reservado","documentos","adjuntos","exif");
    private static Map<String,Object> faltante(String motivo){var d=new LinkedHashMap<String,Object>();d.put("estado","FALTANTE");d.put("motivo",motivo);return d;}
    private static Map<String,Object> origen(String fuente,String alcance,OffsetDateTime fecha){
        var r=new LinkedHashMap<String,Object>();r.put("fuente",fuente);r.put("alcance",alcance);
        if(fecha!=null)r.put("fecha",fecha.atZoneSameInstant(ZoneId.of("America/Lima")).toLocalDate().toString());return r;
    }
    private Map<String,Object> origenPrecio(Captacion c,EncargoService.Composicion composicion){
        // U de una unidad no acredita la fecha ni el objeto económico de un conjunto.
        if(composicion==null){var u=precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(c.getId(),PrecioPropiedad.HITO_AUTORIZADO);
            var condicion=c.getCondicionEconomica();
            if(condicion!=null&&u.filter(p->Objects.equals(p.getIdCaptacion(),c.getId())&&Objects.equals(p.getIdPropiedad(),c.getPropiedad().getId())
                    &&Objects.equals(p.getOrganizacionId(),c.getOrganizacionId())&&Objects.equals(p.getOperacion(),c.operacion().codigo())
                    &&Objects.equals(p.getMoneda(),condicion.getMonedaReferencia())&&p.getMonto()!=null&&condicion.getImporteReferencia()!=null
                    &&p.getMonto().compareTo(condicion.getImporteReferencia())==0).isPresent()){
                var p=u.orElseThrow();var fuente=proveniencia(p.getFechaCreacion(),null);
                if(p.getFecha()!=null)fuente.put("observadoEn",p.getFecha().toString());
                if(!fuente.isEmpty())return fuente;
            }}
        return proveniencia(c.getCondicionEconomica()==null?null:c.getCondicionEconomica().getFechaCreacion(),null);
    }
    private com.controllocal.domain.comercial.ComposicionEncargo composicion(Captacion c,EncargoService.Composicion comp){
        var r=composiciones.findByOrganizacionIdAndId(c.getOrganizacionId(),comp.id()).orElseThrow(()->new ConflictoException("La composición ya no está disponible."));
        if(!Objects.equals(r.getIdEncargo(),c.getId())||r.getVersionContenido()!=comp.version())throw new ConflictoException("La composición cambió durante la proyección.");return r;
    }
    /** Sólo el contrato comercial que cabe en H-18/H-26; no recalcula el total mensual de matching. */
    private Map<String,Object> mantenimiento(Captacion c,EncargoService.Ficha ficha,Map<String,Object> atributos,ValorLogico cuota,
            List<Map<String,Object>> huecos,boolean conjunto){
        var mensual=ficha.condiciones()==null?null:ficha.condiciones().composicionMensual();
        var lineas=mensual==null||mensual.componentes()==null?List.<EncargoService.ImporteMensual>of():mensual.componentes().stream().filter(x->"MANTENIMIENTO".equals(x.concepto())).toList();
        if(cuota==null&&lineas.isEmpty())return null;
        // La cuota de una unidad no acredita el mantenimiento del objeto compuesto.
        if(conjunto)atributos.remove("cuota_mantenimiento");
        if(lineas.size()!=1)return faltante("Falta identificar una declaración vigente de moneda, período y tratamiento del mantenimiento.");
        var x=lineas.getFirst();
        boolean actual=(x.vigenteDesde()==null||!x.vigenteDesde().isAfter(contextos.hoy()))&&(x.vigenteHasta()==null||!x.vigenteHasta().isBefore(contextos.hoy()));
        if(x.intervalo()||x.importeHasta()!=null||x.importe()==null||x.moneda()==null
                ||!conjunto&&cuota!=null&&(cuota.numero()==null||cuota.numero().compareTo(x.importe())!=0||!Objects.equals(cuota.moneda(),x.moneda()))
                ||!"MENSUAL".equals(x.periodo())||x.tratamiento()==null||!actual
                ||x.tratamiento()!=TratamientoMensual.EXIGIBLE&&x.tratamiento()!=TratamientoMensual.INCLUIDO_EN_OTRO)
            return faltante("Falta conciliar importe, moneda, período, vigencia y tratamiento del mantenimiento con su declaración actual.");
        var fuente=proveniencia(c.getCondicionEconomica()==null?null:c.getCondicionEconomica().getFechaCreacion(),null);
        var valor=Map.<String,Object>of("importe",x.importe(),"moneda",x.moneda(),"periodo",x.periodo());
        var completo=dato(valor,fuente);
        if(!"VIGENTE".equals(completo.get("estado")))return completo;
        if(x.tratamiento()==TratamientoMensual.INCLUIDO_EN_OTRO){
            boolean renta="RENTA_DEL_ENCARGO".equals(x.incluidoEn());
            EncargoService.ImporteMensual cobertura=null;
            if(!renta&&mensual.componentes()!=null){
                var destino=mensual.componentes().stream().filter(y->Objects.equals(y.referencia(),x.incluidoEn())).toList();
                if(destino.size()==1){
                    var y=destino.getFirst();
                    cobertura=y;
                    renta="PRECIO_ALQUILER".equals(y.concepto())&&y.tratamiento()==TratamientoMensual.EXIGIBLE
                            &&!y.intervalo()&&y.importeHasta()==null&&Objects.equals(y.moneda(),ficha.condiciones().monedaPrecio())
                            &&y.importe()!=null&&ficha.condiciones().precioPedido()!=null&&y.importe().compareTo(ficha.condiciones().precioPedido())==0
                            &&"MENSUAL".equals(y.periodo())&&(y.vigenteDesde()==null||!y.vigenteDesde().isAfter(contextos.hoy()))
                            &&(y.vigenteHasta()==null||!y.vigenteHasta().isBefore(contextos.hoy()));
                }
            }
            if(!renta&&cobertura!=null&&!"PRECIO_ALQUILER".equals(cobertura.concepto())
                    &&cobertura.tratamiento()==TratamientoMensual.EXIGIBLE&&!cobertura.intervalo()&&cobertura.importeHasta()==null
                    &&cobertura.importe()!=null&&cobertura.moneda()!=null&&"MENSUAL".equals(cobertura.periodo())
                    &&(cobertura.vigenteDesde()==null||!cobertura.vigenteDesde().isAfter(contextos.hoy()))
                    &&(cobertura.vigenteHasta()==null||!cobertura.vigenteHasta().isBefore(contextos.hoy()))){
                atributos.remove("cuota_mantenimiento");
                return dato("El mantenimiento de "+x.importe().toPlainString()+" "+x.moneda()+" al mes está incluido en el componente mensual declarado de "+
                        cobertura.importe().toPlainString()+" "+cobertura.moneda()+". No se cobra otra vez como mantenimiento.",fuente);
            }
            if(!renta)return faltante("La referencia de inclusión no acredita un componente mensual vigente con importe y moneda conocidos.");
            // V08 dibuja H-18 como pago adicional. La inclusión expresa se comunica sólo por su sujeto H-26.
            atributos.remove("cuota_mantenimiento");
            return dato("La renta incluye el mantenimiento de "+x.importe().toPlainString()+" "+x.moneda()+" al mes.",fuente);
        }
        // Ambas partes del adaptador proceden de la misma declaración comercial vigente, sin asumir período físico.
        atributos.put("cuota_mantenimiento",dato(x.importe(),fuente));
        completo.put("hueco","H-18");huecos.add(completo);
        return null;
    }
    private Map<String,Object> sujeto(SeleccionInmueblePieza.Seleccion objeto,Map<String,Object> mantenimiento,
            com.controllocal.domain.comercial.ComposicionEncargo origen){
        if(!objeto.pertenece())return faltante("El inmueble descrito y sus fotos no pertenecen al objeto actual del Encargo; elige una unidad comprendida.");
        if(mantenimiento!=null&&"FALTANTE".equals(mantenimiento.get("estado")))return mantenimiento;
        var comp=objeto.composicion();if(comp==null)return mantenimiento;
        var fuente=proveniencia(origen.getRegistradaEn(),null);
        int cantidad=comp.unidades().size();
        String texto="El precio corresponde al conjunto de "+cantidad+(cantidad==1?" inmueble.":" inmuebles.")+" Los datos físicos y las fotografías describen una unidad de ese conjunto.";
        if(mantenimiento!=null)texto+=" "+mantenimiento.get("valor");
        return dato(texto,fuente);
    }
    public static void validarContacto(PiezasComercialesService.Contacto c){
        if(c==null)return;String v=c.valor();
        if(v==null||v.length()>300||v.contains("\n")||v.contains("\r"))throw new ReglaNegocioException("Elige un canal comercial válido para esta pieza.");
        boolean valido=switch(c.tipo()==null?"":c.tipo()){
            case "WHATSAPP","TELEFONO"->v.matches("\\+[1-9][0-9]{7,14}");
            case "CORREO"->v.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
            case "WEB"->v.matches("https://[A-Za-z0-9.-]+(?:/[^\\s<>\"\\\\]*)?");default->false;};
        if(!valido)throw new ReglaNegocioException("El canal comercial necesita tipo y valor completos, sin deducir contactos personales.");
    }
}
