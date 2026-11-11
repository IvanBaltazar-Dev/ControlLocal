package com.controllocal.service.matching;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.captura.SuficienciaRegistrada;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.encargo.CondicionesDeEncargo;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.controllocal.service.matching.ContratoMatching.*;

/** D1-01/D4: obtiene primero referencias en alcance; después proyecta valores del mismo objeto. */
@Component
public class FuenteCarteraMatching {
    public record Candidata(String tipo, String referenciaInterna, long version, Long responsable, Oferta oferta,
                            Map<String,Object> datosPermitidos) {
        public Candidata { datosPermitidos=Collections.unmodifiableMap(new LinkedHashMap<>(datosPermitidos)); }
    }
    private final CaptacionRepository captaciones;
    private final ProspeccionRepository prospecciones;
    private final AccesoEncargo acceso;
    private final EncargoService encargos;
    private final AutoridadDePropiedad autoridad;
    private final Alcances alcances;
    private final LectorPorAutoridad lector;
    private final AtributosGobernados atributos;
    private final AtributosDeEncargo condiciones;
    private final CatalogoAtributoRepository catalogo;
    private final SuficienciaRegistrada suficiencia;
    private final MotorDeCaptura capturas;
    private final DisponibilidadContextualService disponibilidad;
    private final GobiernoCatalogoCapturaService vocabularios;
    private final ResolverDeDistrito distritos;
    private final CondicionesDeEncargo pactos;
    private final MotorMatchingDeterminista motor=new MotorMatchingDeterminista();

    public FuenteCarteraMatching(CaptacionRepository captaciones,ProspeccionRepository prospecciones,
            AccesoEncargo acceso,EncargoService encargos,AutoridadDePropiedad autoridad,Alcances alcances,
            LectorPorAutoridad lector,AtributosGobernados atributos,AtributosDeEncargo condiciones,
            CatalogoAtributoRepository catalogo,SuficienciaRegistrada suficiencia,MotorDeCaptura capturas,
            DisponibilidadContextualService disponibilidad,GobiernoCatalogoCapturaService vocabularios,ResolverDeDistrito distritos,
            CondicionesDeEncargo pactos) {
        this.captaciones=captaciones;this.prospecciones=prospecciones;this.acceso=acceso;this.encargos=encargos;
        this.autoridad=autoridad;this.alcances=alcances;this.lector=lector;this.atributos=atributos;
        this.condiciones=condiciones;this.catalogo=catalogo;this.suficiencia=suficiencia;
        this.capturas=capturas;this.disponibilidad=disponibilidad;this.vocabularios=vocabularios;this.distritos=distritos;
        this.pactos=pactos;
    }

    public List<Long> referencias(Actor actor) {
        var alcance=alcances.de(actor);
        return alcance.rolesAgente().isEmpty()?List.of():captaciones.referenciasParaMatching(actor.idOrganizacion(),alcance.rolesAgente());
    }

    /** Descriptor del origen; sin universo candidato, puntuación ni contactos. */
    public long versionAutorizada(CoincidenciaService.Origen tipo,long id,Actor actor) {
        Alcances.exigirOperador(actor);
        if(tipo==CoincidenciaService.Origen.ENCARGO){acceso.exigirLectura(id,actor);return acceso.contexto(id,actor).getVersion();}
        if(tipo==CoincidenciaService.Origen.CAPTURA)return capturas.consultar(id,actor).version();
        long propiedad=id;
        if(tipo==CoincidenciaService.Origen.PROSPECCION){
            var alcance=alcances.de(actor);
            if(alcance.rolesAgente().isEmpty())throw new NoEncontradoException("Prospección");
            propiedad=prospecciones.propiedadParaMatching(actor.idOrganizacion(),id,alcance.rolesAgente())
                    .orElseThrow(()->new NoEncontradoException("Prospección")).getIdPropiedad();
        } else if(tipo!=CoincidenciaService.Origen.FISICO)throw new ReglaNegocioException("Origen físico requerido.");
        return autoridad.buscarParaLectura(actor,propiedad).orElseThrow(()->new NoEncontradoException("Propiedad")).getVersionFisica();
    }

    public Candidata encargo(long id,Actor actor,Instant ahora) {
        Alcances.exigirOperador(actor);
        return proyectar(acceso.leer(id,actor),actor,ahora,null);
    }
    private record Lecturas(ValoresGobernados fisicos,Map<String,CatalogoAtributo> definicionesFisicas,
                            ValoresGobernados comerciales,Map<String,CatalogoAtributo> definicionesComerciales) { }

    /** Dos lotes EAV después del scope, con definiciones compartidas por tipo/operación. Sin top-N previo. */
    public List<Candidata> encargos(Collection<Long> referencias,Actor actor,Instant ahora) {
        Alcances.exigirOperador(actor);List<Captacion> autorizados=new ArrayList<>();
        for(long id:new LinkedHashSet<>(referencias)) {
            try {
                var c=acceso.leer(id,actor);var p=c.getPropiedad();
                if(p==null)throw new IllegalStateException("Encargo sin identidad física.");
                if(c.getIdComposicionActual()==null&&!autoridad.puedeLeer(actor,p.getId()))continue;
                autorizados.add(c);
            } catch(com.controllocal.service.excepcion.AccesoNoAutorizadoException|NoEncontradoException revocado){/* sin pista */}
        }
        if(autorizados.isEmpty())return List.of();
        var props=autorizados.stream().filter(c->c.getIdComposicionActual()==null).map(Captacion::getPropiedad).distinct().toList();
        var fisicos=props.isEmpty()?Map.<Long,ValoresGobernados>of():lector.deVarias(actor.idOrganizacion(),props);
        var comerciales=lector.deEncargos(autorizados.stream().map(Captacion::getId).toList());
        Map<String,Set<String>> clavesFisicas=new HashMap<>(),clavesComerciales=new HashMap<>();
        for(var c:autorizados) {
            var p=c.getPropiedad();String tipo=p.getTipoInmueble();
            clavesFisicas.computeIfAbsent(tipo,k->new LinkedHashSet<>()).addAll(fisicos.getOrDefault(p.getId(),ValoresGobernados.vacio()).claves());
            clavesComerciales.computeIfAbsent(tipo+":"+c.getMotivoOperacion(),k->new LinkedHashSet<>()).addAll(comerciales.getOrDefault(c.getId(),ValoresGobernados.vacio()).claves());
        }
        Map<String,Map<String,CatalogoAtributo>> defsFisicas=new HashMap<>(),defsComerciales=new HashMap<>();
        List<Candidata> salida=new ArrayList<>();
        for(var c:autorizados) {
            var p=c.getPropiedad();String tipo=p.getTipoInmueble(),clave=tipo+":"+c.getMotivoOperacion();
            var df=defsFisicas.computeIfAbsent(tipo,k->atributos.definicionesParaLeer(actor.idOrganizacion(),tipo,clavesFisicas.get(tipo)));
            var dc=defsComerciales.computeIfAbsent(clave,k->condiciones.definicionesParaLeer(actor.idOrganizacion(),Comercializacion.de(c,p),clavesComerciales.get(clave)));
            try{salida.add(proyectar(c,actor,ahora,new Lecturas(fisicos.getOrDefault(p.getId(),ValoresGobernados.vacio()),df,
                    comerciales.getOrDefault(c.getId(),ValoresGobernados.vacio()),dc)));}
            catch(com.controllocal.service.excepcion.AccesoNoAutorizadoException|NoEncontradoException revocado){/* sin pista */}
        }
        return List.copyOf(salida);
    }
    private Candidata proyectar(Captacion c,Actor actor,Instant ahora,Lecturas lecturas) {
        long id=c.getId();
        Propiedad p=c.getPropiedad();
        if(p==null) throw new IllegalStateException("Encargo sin identidad física.");
        String op=c.operacion().name();
        String config=c.getIdComposicionActual()==null?"UNIDAD:"+p.getId():"COMPOSICION:"+c.getIdComposicionActual();
        List<Dato> datos=new ArrayList<>();
        datos.add(escalar(OPERACION,TipoDato.LISTA,ValorLogico.deTexto(op),null,null,op,config,conocido()));
        // La matriz no describe automáticamente el paquete; tampoco se suman superficies de unidades.
        if(c.getIdComposicionActual()==null) datos.addAll(lecturas==null?fisicos(p,actor,op,config)
                :fisicosLeidos(p,actor,op,config,lecturas.fisicos(),lecturas.definicionesFisicas()));
        var comerciales=lecturas==null?lector.deEncargo(id):lecturas.comerciales();
        var definiciones=lecturas==null?condiciones.definicionesParaLeer(actor.idOrganizacion(),Comercializacion.de(c,p),comerciales.claves()):lecturas.definicionesComerciales();
        comerciales.todos().forEach((clave,valor)->{
            var def=definiciones.get(clave);
            if(def!=null) datos.add(deGobernado(def,valor,null,null,op,config,actor.idOrganizacion(),null));
        });
        var vigencia=encargos.consultarVigencia(id,ahora.atZone(ZoneId.of("America/Lima")).toLocalDate(),actor);
        BigDecimal precio=null;String moneda=null;String periodo=null;
        if(c.getIdComposicionActual()!=null) {
            var comp=encargos.consultarComposicion(id,null,actor);
            if(!Objects.equals(c.getIdComposicionActual(),comp.id()))throw new ConflictoException("La configuración de la oferta cambió durante la consulta.");
            // G acredita el pacto del MISMO snapshot/configuración. El total del desglose no es otro precio vigente.
            if(vigencia.habilitado()) {
                var pacto=c.getCondicionEconomica();
                if(pacto==null)throw new IllegalStateException("Encargo habilitado sin su condición económica acreditada.");
                precio=pacto.getImporteReferencia();moneda=pacto.getMonedaReferencia();periodo=pacto.getPeriodoPrecio();
            }
            // En preparación no se hereda la tarifa del objeto anterior ni se convierte totalDeclarado en precioPedido.
        } else if(c.getCondicionEconomica()!=null) {
            precio=c.getCondicionEconomica().getImporteReferencia();
            moneda=c.getCondicionEconomica().getMonedaReferencia();
            periodo=c.getCondicionEconomica().getPeriodoPrecio();
        }
        String modalidadPrecio=comerciales.texto("modalidad_precio");
        Dato total=null;
        if("ALQUILER".equals(op)&&vigencia.habilitado()&&c.getCondicionEconomica()!=null
                &&c.getCondicionEconomica().getComposicionMensual()!=null)
            total=pactos.totalMensual(c.getCondicionEconomica(),op,config,c.getFechaInicioVigencia(),c.getFechaFinVigencia(),
                    c.getIdDocumentoActual()==null?null:"DOCUMENTO_ENCARGO:"+c.getIdDocumentoActual(),ahora,modalidadPrecio);
        boolean porMetro="POR_M2_AL_MES".equals(modalidadPrecio);
        if("ALQUILER".equals(op)&&porMetro&&total==null)
            total=new Dato(TOTAL_MENSUAL_OBLIGATORIO,TipoDato.IMPORTE,null,null,false,
                    new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,moneda,"MENSUAL",op,config),faltante(Causa.UNIDAD_INCOMPATIBLE));
        agregarPrecio(datos,precio,moneda,periodo,op,config,ahora,total,porMetro);
        var confirmacion=disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.ENCARGO,id,actor);
        Conclusion g=vigencia.habilitado()?motor.disponibilidad(confirmacion,ahora).conclusion():Conclusion.INCUMPLE;
        Map<String,Object> visibles=new LinkedHashMap<>();
        visibles.put("idEncargo",id);visibles.put("codigoEncargo",c.getCodigoCaptacion());
        visibles.put("idPropiedad",p.getId());visibles.put("operacion",op);
        return candidata("ENCARGO",id,c.getVersion(),c.getAgente().getId(),actor,config,g,confirmacion,datos,visibles);
    }
    private static void agregarPrecio(List<Dato> datos,BigDecimal precio,String moneda,String periodo,String op,String config,Instant ahora,Dato totalDeclarado,boolean porMetro) {
        if("VENTA".equals(op)) datos.add(escalar(PRECIO_VENTA,TipoDato.IMPORTE,
                ValorLogico.deImporte(precio,moneda),"PRECIO_VENTA",moneda,op,config,
                precio==null?faltante(Causa.DATO_FALTANTE):moneda==null?faltante(Causa.MONEDA_NO_COMPARABLE):conocido()));
        else {
            var contextoRenta=new ContextoValor(porMetro?"POR_M2_AL_MES":"PRECIO_ALQUILER",porMetro?"m2":null,moneda,periodo,op,config);
            var renta=new Dato(selector(Sujeto.ENCARGO,"precio_pedido"),TipoDato.IMPORTE,
                    ValorLogico.deImporte(precio,moneda),null,false,contextoRenta,
                    precio==null?faltante(Causa.DATO_FALTANTE):moneda==null?faltante(Causa.MONEDA_NO_COMPARABLE):conocido());
            datos.add(renta);
            // El catálogo no acredita exhaustividad de los cargos: la renta es una cota, no el total.
            datos.add(totalDeclarado!=null?totalDeclarado:ComposicionMensualMatching.total(TOTAL_MENSUAL_OBLIGATORIO,
                    List.of(new ComponenteMensual("RENTA_DEL_ENCARGO",renta,TratamientoMensual.EXIGIBLE)),false,
                    new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,moneda,"MENSUAL",op,config),conocido(),ahora));
        }
    }

    /** Orígenes AC149: valores propios/autorizados sin materializar un Encargo ni intención ficticia. */
    public Candidata preliminar(CoincidenciaService.Origen tipo,long id,Long version,Actor actor,String operacionEvaluada,Instant corteCore) {
        Alcances.exigirOperador(actor);
        if(tipo==CoincidenciaService.Origen.CAPTURA) {
            var b=capturas.consultar(id,actor);
            exigirVersion(version,b.version());
            var contexto=b.contexto();
            String op=contexto==null?null:contexto.operacion();
            Object operaciones=b.conocido().get(GuionRegistroPropiedad.OPERACIONES);
            var declaradas=operaciones==null?List.<OperacionInmobiliaria>of():OperacionInmobiliaria.desdeLista(operaciones.toString());
            if(op==null&&operacionEvaluada!=null&&declaradas.stream().anyMatch(v->v.name().equals(operacionEvaluada)))op=operacionEvaluada;
            String config="CAPTURA:"+id+(op==null?"":":"+op);
            Map<String,CatalogoAtributo> defs=new LinkedHashMap<>();
            Set<String> claves=new LinkedHashSet<>(b.conocido().keySet());claves.addAll(b.metadatos().keySet());
            var clavesFisicas=claves.stream().filter(k->GuionRegistroPropiedad.operacionDe(k)==null&&!k.startsWith("moneda:")).toList();
            if(!clavesFisicas.isEmpty())for(var def:catalogo.paraLeer(actor.idOrganizacion(),clavesFisicas,Sujeto.PROPIEDAD.codigo()))defs.putIfAbsent(def.getClave(),def);
            var capturados=suficiencia.datos(b.conocido(),b.metadatos());
            if(contexto!=null) capturados=suficiencia.validarDatos(actor.idOrganizacion(),contexto,capturados);
            List<Dato> datos=new ArrayList<>();
            for(var e:capturados.entrySet()) {
                var def=defs.get(e.getKey());if(def==null)continue;
                var meta=b.metadatos().get(e.getKey());
                datos.add(deGobernado(def,valor(def,e.getValue().valor(),b.conocido().get("moneda:"+e.getKey())),
                        e.getValue(),meta,op==null?operacionEvaluada:op,config,actor.idOrganizacion(),contexto==null?null:contexto.jurisdiccion()));
            }
            if(contexto!=null&&contexto.tipoPropiedad()!=null)
                datos.add(escalar(TIPO_PROPIEDAD,TipoDato.LISTA,ValorLogico.deTexto(AtributosGobernados.codigoDelTipo(contexto.tipoPropiedad())
                                .orElseThrow(()->new ReglaNegocioException("Tipo de captura no reconocido."))),null,null,
                        op==null?operacionEvaluada:op,config,conocido()));
            Object nombreDistrito=b.conocido().get("distrito");
            Long distrito=nombreDistrito==null?null:distritos.idParaNombre(nombreDistrito.toString());
            if(distrito!=null)datos.add(escalar(DISTRITO,TipoDato.LISTA,ValorLogico.deTexto(distrito.toString()),null,null,
                    op==null?operacionEvaluada:op,config,conocido()));
            if(op!=null)datos.add(escalar(OPERACION,TipoDato.LISTA,ValorLogico.deTexto(op),null,null,op,config,conocido()));
            if(op!=null) {
                var operacion=OperacionInmobiliaria.valueOf(op);
                String claveImporte=GuionRegistroPropiedad.para(GuionRegistroPropiedad.IMPORTE,operacion);
                Object importe=b.conocido().get(claveImporte),moneda=b.conocido().get(GuionRegistroPropiedad.para(GuionRegistroPropiedad.MONEDA,operacion));
                if(importe!=null) {
                    // Esta pregunta declara renta MENSUAL por definición (nombreDelImporte), no por magnitud.
                    agregarPrecio(datos,new BigDecimal(importe.toString()),moneda==null?null:moneda.toString(),
                            operacion==OperacionInmobiliaria.ALQUILER?"MENSUAL":null,op,config,corteCore,null,false);
                }
                var comerciales=claves.stream().filter(k->operacion==GuionRegistroPropiedad.operacionDe(k)&&!k.startsWith("moneda:"))
                        .map(GuionRegistroPropiedad::claveBase).distinct().toList();
                if(!comerciales.isEmpty())for(var def:catalogo.paraLeer(actor.idOrganizacion(),comerciales,Sujeto.ENCARGO.codigo())) {
                    String clave=GuionRegistroPropiedad.para(def.getClave(),operacion);
                    var material=capturados.get(clave);if(material==null)continue;
                    datos.add(deGobernado(def,valor(def,material.valor(),b.conocido().get("moneda:"+clave)),material,b.metadatos().get(clave),op,config,actor.idOrganizacion(),null));
                }
            }
            return candidata("CAPTURA",id,b.version(),actor.idRolOperativo(),actor,config,Conclusion.NO_DETERMINADO,null,datos,Map.of("idCaptura",id));
        }
        long propiedad=id;Long responsable=null;
        if(tipo==CoincidenciaService.Origen.PROSPECCION) {
            var scope=alcances.de(actor);
            var ref=scope.rolesAgente().isEmpty()?Optional.<ProspeccionRepository.OrigenParaMatching>empty()
                    :prospecciones.propiedadParaMatching(actor.idOrganizacion(),id,scope.rolesAgente());
            var origen=ref.orElseThrow(()->new NoEncontradoException("Prospección"));
            propiedad=origen.getIdPropiedad();responsable=origen.getIdResponsable();
        } else if(tipo!=CoincidenciaService.Origen.FISICO) throw new ReglaNegocioException("Origen preliminar no admitido.");
        var p=autoridad.buscarParaLectura(actor,propiedad).orElseThrow(()->new NoEncontradoException("Propiedad"));
        exigirVersion(version,p.getVersionFisica());
        String config="FISICO:"+p.getId();
        List<Dato> datos=fisicos(p,actor,operacionEvaluada,config);
        return candidata(tipo.name(),id,p.getVersionFisica(),responsable==null?p.getIdRolResponsable():responsable,actor,config,Conclusion.NO_DETERMINADO,null,datos,
                Map.of("idPropiedad",p.getId(),"versionFisica",p.getVersionFisica()));
    }

    private List<Dato> fisicos(Propiedad p,Actor actor,String op,String config) {
        // El alcance del Encargo o de la Prospección no se convierte en permiso físico implícito.
        if(!autoridad.puedeLeer(actor,p.getId())) throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
        var valores=lector.de(actor.idOrganizacion(),p);
        var defs=atributos.definicionesParaLeer(actor.idOrganizacion(),p.getTipoInmueble(),valores.claves());
        return fisicosLeidos(p,actor,op,config,valores,defs);
    }
    private List<Dato> fisicosLeidos(Propiedad p,Actor actor,String op,String config,ValoresGobernados valores,
                                     Map<String,CatalogoAtributo> defs) {
        var material=suficiencia.datos(p,valores);
        var metas=suficiencia.metadatos(p.getMetadatosAtributos());
        var contexto=suficiencia.contexto(p.getContextoCaptura());
        List<Dato> salida=new ArrayList<>();
        if(p.getTipoInmueble()!=null)salida.add(escalar(TIPO_PROPIEDAD,TipoDato.LISTA,ValorLogico.deTexto(p.getTipoInmueble()),null,null,op,config,conocido()));
        if(p.getIdDistrito()!=null)salida.add(escalar(DISTRITO,TipoDato.LISTA,ValorLogico.deTexto(p.getIdDistrito().toString()),null,null,op,config,conocido()));
        Set<String> claves=new LinkedHashSet<>(valores.claves());claves.addAll(metas.keySet());
        for(String clave:claves) {
            var def=defs.get(clave);if(def!=null)salida.add(deGobernado(def,valores.valor(clave),material.get(clave),metas.get(clave),op,config,
                    actor.idOrganizacion(),contexto==null?null:contexto.jurisdiccion()));
        }
        return List.copyOf(salida);
    }

    private Dato deGobernado(CatalogoAtributo def,ValorLogico valor,SuficienciaInmueble.Dato material,
                                     MotorDeCaptura.MetadatoRespuesta original,String op,String config,long org,String jurisdiccion) {
        EstadoDato estado=material==null?(valor==null?EstadoDato.FALTANTE:EstadoDato.CONOCIDO):material.estado();
        String concepto=material==null?null:material.concepto();
        String perfil=null,versionFuente=null;
        if("zonificacion".equals(def.getClave())) {
            var lista=vocabularios.opcionesVigentes(org,def.getClave(),jurisdiccion);
            if(lista.motivo()==null&&material!=null&&Objects.equals(lista.referenciaCatalogo(),material.referenciaCatalogo())) {
                perfil=lista.referenciaCatalogo();jurisdiccion=lista.jurisdiccion();versionFuente=lista.versionFuente();
            } else estado=EstadoDato.FALTANTE;
        }
        var meta=new MetadatosDato(estado,estado==EstadoDato.FALTANTE?Causa.DATO_FALTANTE:null,
                "CAT:D:"+def.getId()+":"+def.getVersionDefinicion(),def.getVersionDefinicion(),
                material==null?null:material.evidenciaRef(),null,original==null?null:original.observadoEn(),null,null);
        return new Dato(selector(Sujeto.desde(def.getSujeto()),def.getClave()),TipoDato.valueOf(def.getTipoDato()),
                estado==EstadoDato.CONOCIDO?valor:null,null,false,
                new ContextoValor(concepto,def.getUnidad(),valor==null?null:valor.moneda(),null,op,config,
                        "zonificacion".equals(def.getClave())?jurisdiccion:null,perfil,versionFuente),meta);
    }
    private static ValorLogico valor(CatalogoAtributo def,Object bruto,Object moneda) {
        if(bruto==null)return null;
        return switch(TipoDato.valueOf(def.getTipoDato())) {
            case ENTERO,DECIMAL->ValorLogico.deNumero(new BigDecimal(bruto.toString()));
            case IMPORTE->ValorLogico.deImporte(new BigDecimal(bruto.toString()),moneda==null?null:moneda.toString());
            case BOOLEANO->ValorLogico.deBooleano(bruto instanceof Boolean b?b:ConversionDeValores.booleano(def.getClave(),bruto.toString()));
            case FECHA->ValorLogico.deFecha(bruto instanceof LocalDate d?d:LocalDate.parse(bruto.toString()));
            case LISTA_MULTIPLE->bruto instanceof Collection<?> c?ValorLogico.deValores(c.stream().map(Object::toString).toList()):null;
            default->ValorLogico.deTexto(bruto.toString());
        };
    }
    private static Dato escalar(String clave,TipoDato tipo,ValorLogico valor,String concepto,String moneda,String op,String config,MetadatosDato meta) {
        return new Dato(clave,tipo,valor,null,false,new ContextoValor(concepto,null,moneda,null,op,config),meta);
    }
    private static MetadatosDato conocido(){return new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);}
    private static MetadatosDato faltante(Causa causa){return new MetadatosDato(EstadoDato.FALTANTE,causa,null,null,null,null,null,null,null);}
    private static Candidata candidata(String tipo,long id,long version,Long responsable,Actor actor,String config,Conclusion g,
                                       ConfirmacionDisponibilidad confirmacion,List<Dato> datos,Map<String,Object> visibles) {
        String ref=Idempotencia.huellaExacta(actor.idOrganizacion()+"|"+tipo+"|"+id+"|"+config);
        return new Candidata(tipo,Long.toString(id),version,responsable,
                new Oferta(ref,new ClaveCandidato(Long.toString(actor.idOrganizacion()),tipo+":"+id,config),version,true,g,confirmacion,datos),visibles);
    }
    public static void exigirVersion(Long observada,long actual) {
        if(observada==null||observada<0)throw new ReglaNegocioException("Versión observada requerida.");
        if(observada!=actual)throw new ConflictoException("El contenido observado cambió; actualice la consulta.");
    }
}

