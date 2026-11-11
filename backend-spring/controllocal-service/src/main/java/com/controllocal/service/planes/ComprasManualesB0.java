package com.controllocal.service.planes;

import com.controllocal.domain.planes.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.presupuesto.ComprasManualesB0Repository.Compra;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.soporte.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.controllocal.service.planes.DatosCompraManual.*;
import static com.controllocal.service.planes.ComprasVerificadasB0.Tipo;

/** Venta manual Web, CAT-E38. Autorización/pedido/pago no equivalen a una pasarela. */
@Service
public class ComprasManualesB0 {
    private final ContextosService contextos;private final AtribucionesPlataforma atribuciones;
    private final ComprasManualesB0Repository repo;private final PresupuestoIaRepository presupuesto;
    private final CiclosPagadosB0Repository ciclos;private final CiclosPagadosB0 acreditacion;
    private final Documentos json;private final Clock reloj;private final String whatsapp;
    @Autowired public ComprasManualesB0(ContextosService contextos,AtribucionesPlataforma atribuciones,ComprasManualesB0Repository repo,
            PresupuestoIaRepository presupuesto,CiclosPagadosB0Repository ciclos,CiclosPagadosB0 acreditacion,Documentos json,
            @Value("${controllocal.compras.whatsapp-numero:}") String whatsapp){
        this(contextos,atribuciones,repo,presupuesto,ciclos,acreditacion,json,whatsapp,Clock.systemUTC());
    }
    public ComprasManualesB0(ContextosService contextos,AtribucionesPlataforma atribuciones,ComprasManualesB0Repository repo,
            PresupuestoIaRepository presupuesto,CiclosPagadosB0Repository ciclos,CiclosPagadosB0 acreditacion,Documentos json,String whatsapp,Clock reloj){
        this.contextos=contextos;this.atribuciones=atribuciones;this.repo=repo;this.presupuesto=presupuesto;
        this.ciclos=ciclos;this.acreditacion=acreditacion;this.json=json;this.whatsapp=whatsapp;this.reloj=reloj;
    }
    @Transactional public List<Producto> catalogo(Actor a,ContextoSesion s){
        var b=comprador(a,s);boolean empresa="EMPRESARIAL".equals(b.activo().modalidad());
        List<Producto> productos=new ArrayList<>();
        for(var p:PlanBrox.values())if(p.pagado()&&p.empresarial()==empresa)
            productos.add(new Producto(p.name(),"Plan "+p.name(),p.precioUsd(),"USD",null,p.almacenamientoGb()));
        for(var p:PaqueteBoost.values())productos.add(new Producto(p.name(),"Boost "+p.creditos(),p.precioUsd(),"USD",(long)p.creditos(),null));
        return List.copyOf(productos);
    }
    /** Preparación sencilla para mostrar condiciones; aún no consiente ni abre WhatsApp. */
    @Transactional public DatosCompraManual.Compra preparar(Actor a,ContextoSesion s,UUID id,Seleccion seleccion){
        var b=comprador(a,s);if(id==null||seleccion==null)throw new ReglaNegocioException("Identifica la compra y el producto.");
        var org=organizacion(a.idOrganizacion());b=comprador(a,s);String hash=huella(Map.of("producto",seleccion,"cuenta",b.identidad().idCuenta()));
        var anterior=repo.bloquear(id);
        if(anterior.isPresent()){
            var c=propia(anterior.get(),a);if(!c.solicitudHuella().equals(hash))throw new ConflictoException("La referencia conserva otra solicitud.");return comercial(c);
        }
        Terminos t=terminos(a.idOrganizacion(),org.modalidad(),seleccion,ahora());
        if(!repo.crear(id,a.idOrganizacion(),b.identidad().idCuenta(),hash,json.objetoCanonico(t),ahora()))
            throw new ConflictoException("La referencia ya identifica otra solicitud.");
        var c=repo.bloquear(id).orElseThrow();
        evento(c,id,b.identidad().idCuenta(),null,"PREPARADA",hash,Map.of("producto",seleccion));return comercial(c);
    }
    @Transactional public Apertura consentir(Actor a,ContextoSesion s,UUID id,Confirmacion d){
        var b=comprador(a,s);organizacion(a.idOrganizacion());var c=propia(bloquear(id),a);b=comprador(a,s);
        String hash=comando(c,d,"CONSENTIR",Map.of("cuenta",b.identidad().idCuenta()));
        if(repetido(c,d,hash))return apertura(c);
        version(c,d);if(c.consentidaEn()!=null)throw new ConflictoException("La compra ya conserva su consentimiento.");
        exigirAplicabilidad(c);
        // El destino se valida antes de consentir. No se usa un número de pruebas o del chatbot.
        exigirCanal();Instant consentidoEn=ahora();String consentimiento=huella(Map.of("pedido",c.id(),"terminos",json.comoMapa(c.terminos()),"cuenta",b.identidad().idCuenta(),"consentidoEn",consentidoEn));
        repo.consentir(c,b.identidad().idCuenta(),consentimiento,consentidoEn);c=bloquear(id);
        evento(c,d.comando(),b.identidad().idCuenta(),null,"CONSENTIDA",hash,Map.of("huellaConsentimiento",consentimiento));return apertura(c);
    }
    @Transactional public DatosCompraManual.Compra consultar(Actor a,ContextoSesion s,UUID id){
        comprador(a,s);organizacion(a.idOrganizacion());comprador(a,s);return comercial(propia(repo.consultar(id).orElseThrow(()->new NoEncontradoException("Compra")),a));
    }
    @Transactional public List<DatosCompraManual.Compra> listar(Actor a,ContextoSesion s,int limite,int desplazamiento){
        comprador(a,s);organizacion(a.idOrganizacion());comprador(a,s);pagina(limite,desplazamiento);
        return comerciales(repo.listar(a.idOrganizacion(),limite,desplazamiento));
    }
    @Transactional public List<DatosCompraManual.Compra> listarFinanciero(SesionPlataforma s,int limite,int desplazamiento){
        financiero(s,Accion.CONSULTAR_COMPRA,false);pagina(limite,desplazamiento);
        return comerciales(repo.listar(null,limite,desplazamiento));
    }
    @Transactional public DetalleFinanciero consultarFinanciero(SesionPlataforma s,UUID id){
        financiero(s,Accion.CONSULTAR_COMPRA,true);return financiero(repo.consultar(id).orElseThrow(()->new NoEncontradoException("Compra")));
    }
    @Transactional public DetalleFinanciero iniciarVerificacion(SesionPlataforma s,UUID id,Confirmacion d,PagoInformado pago){
        var f=financiero(s,Accion.INICIAR_VERIFICACION,true);var c=bloquearFinanciero(id);f=financiero(s,Accion.INICIAR_VERIFICACION,true);
        String hash=comando(c,d,"INICIAR_VERIFICACION",pago);if(repetido(c,d,hash))return financiero(c);version(c,d);
        if(c.consentidaEn()==null||c.fondos()!=null||"ACREDITADA".equals(c.estado()))throw new ConflictoException("Revisa el consentimiento y la verificación ya conservados.");
        if(pago==null||pago.fecha()==null)throw new ReglaNegocioException("Conserva la fecha y procedencia informadas del pago.");
        pago.fecha().validar(ahora());texto(pago.referenciaInformada(),200,"Referencia informada");
        repo.informar(c,json.objetoCanonico(pago));c=bloquear(id);evento(c,d.comando(),f.cuenta(),f.concesion(),"VERIFICACION_INICIADA",hash,pago);return financiero(c);
    }
    /** Transacción propia: queda durable aunque la posterior acreditación falle. */
    @Transactional public DetalleFinanciero verificarFondos(SesionPlataforma s,UUID id,Confirmacion d,Verificacion entrada){
        var f=financiero(s,Accion.VERIFICAR_FONDOS,true);var c=bloquearFinanciero(id);f=financiero(s,Accion.VERIFICAR_FONDOS,true);Verificacion v=verificacion(entrada);
        String hash=comando(c,d,"VERIFICAR_FONDOS",v);if(repetido(c,d,hash))return financiero(c);version(c,d);
        if(c.consentidaEn()==null)throw new ConflictoException("La compra requiere las condiciones consentidas.");
        if(c.fondos()!=null)throw new ConflictoException("Conserva la verificación original; no sobrescribas los fondos.");
        if(!repo.reservarPago(id,v.origen(),v.referencia())&&!repo.mismoPago(id,v.origen(),v.referencia()))
            throw new ConflictoException("Ese pago ya identifica otra compra.");
        f=financiero(s,Accion.VERIFICAR_FONDOS,true);
        repo.verificar(c,json.objetoCanonico(new Fondos(v,f.cuenta(),f.concesion(),ahora())));c=bloquear(id);
        evento(c,d.comando(),f.cuenta(),f.concesion(),"FONDOS_VERIFICADOS",hash,v);return financiero(c);
    }
    /** Mismo motor, con R25 actual. Activación y estado se confirman atómicamente. */
    @Transactional public DetalleFinanciero acreditar(SesionPlataforma s,UUID id){
        var f=financiero(s,Accion.VERIFICAR_FONDOS,true);var c=bloquearFinanciero(id);f=financiero(s,Accion.VERIFICAR_FONDOS,true);
        if("ACREDITADA".equals(c.estado()))return financiero(c);
        if(c.fondos()==null)throw new ConflictoException("No hay fondos verificados para acreditar.");
        acreditacion.aplicar(id);repo.estado(c,"ACREDITADA",null);c=bloquear(id);
        evento(c,UUID.randomUUID(),f.cuenta(),f.concesion(),"ACREDITADA",huella(Map.of("pedido",id,"fondos",json.comoMapa(c.fondos()))),Map.of("pedido",id));return financiero(c);
    }
    @Transactional public DetalleFinanciero incidencia(SesionPlataforma s,UUID id,Confirmacion d,String motivo){
        var f=financiero(s,Accion.REGISTRAR_INCIDENCIA,true);var c=bloquearFinanciero(id);f=financiero(s,Accion.REGISTRAR_INCIDENCIA,true);texto(motivo,2000,"Motivo");
        String hash=comando(c,d,"REGISTRAR_INCIDENCIA",Map.of("motivo",motivo));if(repetido(c,d,hash))return financiero(c);version(c,d);
        if("ACREDITADA".equals(c.estado()))throw new ConflictoException("Una incidencia no revoca derechos ya acreditados.");
        repo.estado(c,"CON_INCIDENCIA","Requiere atención por WhatsApp.");c=bloquear(id);
        evento(c,d.comando(),f.cuenta(),f.concesion(),"INCIDENCIA",hash,Map.of("motivoInterno",motivo));return financiero(c);
    }
    /** Fallo de activación ya ocurrido; conserva fondos y no añade una decisión económica. */
    @Transactional public DetalleFinanciero activacionPendiente(SesionPlataforma s,UUID id){
        var f=financiero(s,Accion.VERIFICAR_FONDOS,true);var c=bloquearFinanciero(id);f=financiero(s,Accion.VERIFICAR_FONDOS,true);
        if(c.fondos()==null||"ACREDITADA".equals(c.estado()))return financiero(c);
        if(!"CON_INCIDENCIA".equals(c.estado())){
            repo.estado(c,"CON_INCIDENCIA","La acreditación requiere revisión; conserva la misma referencia de compra.");c=bloquear(id);
            evento(c,UUID.randomUUID(),f.cuenta(),f.concesion(),"ACREDITACION_PENDIENTE",huella(Map.of("pedido",id,"version",c.version())),Map.of("causa","ACTIVACION_NO_CONFIRMADA"));
        }return financiero(c);
    }
    private ContextosService.SesionAccesoDTO comprador(Actor a,ContextoSesion s){
        if(a==null||s==null)throw new AccesoNoAutorizadoException();
        var b=contextos.exigirContexto(s.referenciaSesion(),s.revisionContexto(),a);
        if(!ContextoSesion.de(b).equals(s)||b.restricciones().debeCambiarContrasena()||b.restricciones().debeEnrolarMfa()
                ||!("INDIVIDUAL".equals(b.activo().modalidad())&&a.esAgente()||"EMPRESARIAL".equals(b.activo().modalidad())&&a.esTenantAdmin()))
            throw new AccesoNoAutorizadoException();return b;
    }
    private Actual financiero(SesionPlataforma s,Accion a,boolean pago){
        return atribuciones.exigirCompra(s,a,pago?Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL,Campo.DATOS_PAGO_VERIFICADO):Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL));
    }
    private Compra bloquearFinanciero(UUID id){
        // Sólo después de R25. La lectura no bloqueante fija el orden org→pedido usado por el motor.
        var c=repo.consultar(id).orElseThrow(()->new NoEncontradoException("Compra"));organizacion(c.organizacion());return bloquear(id);
    }
    private Compra bloquear(UUID id){return repo.bloquear(id).orElseThrow(()->new NoEncontradoException("Compra"));}
    private PresupuestoIaRepository.Organizacion organizacion(long id){var o=presupuesto.bloquearOrganizacion(id);if(o==null||!o.activa())throw new AccesoNoAutorizadoException();return o;}
    private Compra propia(Compra c,Actor a){if(c.organizacion()!=a.idOrganizacion())throw new NoEncontradoException("Compra");return c;}
    private List<DatosCompraManual.Compra> comerciales(List<Compra> compras){
        if(compras.isEmpty())return List.of();
        var historias=repo.historiales(compras.stream().map(Compra::id).toList());
        return compras.stream().map(c->comercial(c,historias.getOrDefault(c.id(),List.of()))).toList();
    }
    private DatosCompraManual.Compra comercial(Compra c){return comercial(c,repo.historial(c.id()));}
    private DatosCompraManual.Compra comercial(Compra c,List<ComprasManualesB0Repository.Evento> historia){
        Fondos f=c.fondos()==null?null:json.comoObjeto(c.fondos(),Fondos.class);PagoInformado p=c.pagoInformado()==null?null:json.comoObjeto(c.pagoInformado(),PagoInformado.class);
        return new DatosCompraManual.Compra(c.id(),c.version(),json.comoObjeto(c.terminos(),Terminos.class),Estado.valueOf(c.estado()),c.consentidaEn()!=null,c.creadaEn(),
                fechaComercial(f!=null?f.verificacion().fecha():p==null?null:p.fecha()),f==null?null:f.verificadosEn(),c.incidencia(),
                historia.stream().map(e->new DatosCompraManual.Evento(e.tipo(),e.registradaEn(),e.estado())).toList());
    }
    private DetalleFinanciero financiero(Compra c){return new DetalleFinanciero(comercial(c),c.pagoInformado()==null?null:json.comoObjeto(c.pagoInformado(),PagoInformado.class),c.fondos()==null?null:json.comoObjeto(c.fondos(),Fondos.class));}
    private static FechaPagoComercial fechaComercial(FechaPago f){return f==null?null:new FechaPagoComercial(f.fecha(),f.instante(),f.objetivoVerificacion());}
    private Apertura apertura(Compra c){
        exigirCanal();String mensaje="Hola BROX, quiero comprar "+nombre(json.comoObjeto(c.terminos(),Terminos.class).producto())+". Referencia: "+c.id();
        return new Apertura(comercial(c),"https://wa.me/"+whatsapp+"?text="+URLEncoder.encode(mensaje,StandardCharsets.UTF_8),mensaje);
    }
    private void exigirCanal(){if(whatsapp==null||!whatsapp.matches("[1-9][0-9]{7,14}"))throw new CapacidadDeshabilitadaException();}
    /** Evita invitar a pagar un cambio que ya se sabe inaplicable; no recalcula lo pactado. */
    private void exigirAplicabilidad(Compra compra){
        var t=json.comoObjeto(compra.terminos(),Terminos.class);var p=t.producto();Instant corte=ahora();
        var actual=presupuesto.cicloVigente(compra.organizacion(),corte).orElse(null);
        if(actual!=null&&actual.revocado())throw new CapacidadDeshabilitadaException();
        if(p.tipo()==Tipo.BOOST){if(actual==null)throw new CapacidadDeshabilitadaException();return;}
        if(ciclos.tieneFuturo(compra.organizacion(),corte))throw new ConflictoException("Ya existe otro período financiado.");
        if(p.tipo()==Tipo.ALTA_PLAN||t.cicloEsperado()==null){
            if(actual!=null)throw new ConflictoException("Las condiciones ya no corresponden al período actual.");return;
        }
        if(actual==null&&p.tipo()==Tipo.RENOVACION)
            actual=presupuesto.ciclo(compra.organizacion(),t.cicloEsperado()).orElse(null);
        if(actual==null||actual.revocado()||!Objects.equals(t.cicloEsperado(),actual.id())||!Objects.equals(t.versionCicloEsperada(),actual.version()))
            throw new ConflictoException("Cambió el ciclo desde la preparación; revisa la misma compra antes de pagar.");
    }
    private static String nombre(Seleccion s){return s.tipo()==Tipo.BOOST?"Boost "+s.boost().creditos()+" créditos":"Plan "+s.plan().name()+(s.tipo()==Tipo.BROKER_ADICIONAL?" — broker adicional":"");}
    private Terminos terminos(long org,String modalidad,Seleccion p,Instant corte){
        if(p.tipo()==null||p.brokersExtra()<0||p.brokersExtra()==Integer.MAX_VALUE)throw new ReglaNegocioException("Selecciona un producto válido.");
        var c=presupuesto.cicloVigente(org,corte).orElse(null);if(c!=null&&c.revocado())throw new CapacidadDeshabilitadaException();
        if(p.tipo()==Tipo.BOOST){
            if(p.boost()==null||p.plan()!=null||p.brokersExtra()!=0)throw new ReglaNegocioException("Selecciona el paquete Boost.");
            if(c==null)throw new CapacidadDeshabilitadaException();return new Terminos(p,CiclosPagadosB0.VERSION_OFERTA,corte,null,null,p.boost().precioUsd(),BigDecimal.ZERO.setScale(2),p.boost().precioUsd(),"USD",null,null,"BOOST_DOCE_MESES_DESDE_COMPRA_VERIFICADA");
        }
        if(p.plan()==null||!p.plan().pagado()||p.boost()!=null||p.plan().empresarial()!= "EMPRESARIAL".equals(modalidad)||!p.plan().empresarial()&&p.brokersExtra()!=0)
            throw new ReglaNegocioException("El producto debe corresponder al workspace actual.");
        if(ciclos.tieneFuturo(org,corte))throw new ConflictoException("Conserva la financiación futura existente.");
        BigDecimal nuevo=p.plan().precioUsd().add(PlanBrox.precioBrokerExtraUsd().multiply(BigDecimal.valueOf(p.brokersExtra()))),abono=BigDecimal.ZERO.setScale(2),total=nuevo;
        switch(p.tipo()){
            case ALTA_PLAN -> {if(c!=null)throw new ConflictoException("Ya existe un período financiado.");}
            case MEJORA_PLAN -> {
                if(c==null||p.plan().precioUsd().compareTo(PlanBrox.valueOf(c.plan()).precioUsd())<=0)throw new ReglaNegocioException("La mejora necesita un plan superior al vigente.");
                var lineas=ciclos.lineas(org,c.id());if(lineas.isEmpty())throw new ConflictoException("Faltan las líneas financiadas originales.");
                var q=CotizacionDePeriodo.mejoraConLineas(nuevo,lineas.stream().map(l->new CotizacionDePeriodo.LineaFinanciada(l.financiado(),l.desde(),l.hasta())).toList(),corte);abono=q.abonoTemporalUsd();total=q.totalUsd();
            }
            case BROKER_ADICIONAL -> {
                if(c==null||!p.plan().empresarial()||!p.plan().name().equals(c.plan())||p.brokersExtra()!=c.brokersExtra()+1)throw new ReglaNegocioException("El complemento corresponde al ciclo empresarial vigente.");
                total=CotizacionDePeriodo.brokerAdicional(c.desde(),c.hasta(),corte);nuevo=total;
            }
            case RENOVACION -> { /* Una reducción sólo prepara la siguiente renovación. */ }
            default -> throw new ReglaNegocioException("Selecciona el producto.");
        }
        return new Terminos(p,CiclosPagadosB0.VERSION_OFERTA,corte,c==null?null:c.id(),c==null?null:c.version(),nuevo,abono,total,"USD",
                p.tipo()==Tipo.RENOVACION&&c!=null?c.hasta():null,p.plan().precioUsd().add(PlanBrox.precioBrokerExtraUsd().multiply(BigDecimal.valueOf(p.brokersExtra()))),
                p.tipo()==Tipo.BROKER_ADICIONAL?"CONSERVA_PERIODO_VIGENTE":p.tipo()==Tipo.RENOVACION&&c!=null?"SIGUIENTE_PERIODO_FINANCIADO":"AL_ACTIVAR_TRAS_VERIFICAR_FONDOS");
    }
    private Verificacion verificacion(Verificacion v){
        if(v==null||v.fecha()==null)throw new ReglaNegocioException("Registra el hecho real de fondos verificados.");v.fecha().validar(ahora());
        texto(v.origen(),80,"Origen verificable");texto(v.referencia(),200,"Referencia del pago");texto(v.evidencia(),4000,"Evidencia de verificación");
        if(v.importe()==null||v.importe().signum()<0||v.importe().scale()>2||v.importe().precision()>12||v.moneda()==null||!v.moneda().matches("[A-Z]{3}"))
            throw new ReglaNegocioException("Conserva importe y moneda de los fondos reales, sin conversión automática.");
        return new Verificacion(v.fecha(),v.origen().strip().toUpperCase(Locale.ROOT),v.referencia().strip().toUpperCase(Locale.ROOT),v.importe(),v.moneda(),v.evidencia());
    }
    private String comando(Compra c,Confirmacion d,String tipo,Object datos){if(d==null||d.comando()==null||datos==null)throw new ReglaNegocioException("Identifica el comando y su versión.");return huella(Map.of("pedido",c.id(),"version",d.version(),"tipo",tipo,"datos",datos));}
    private boolean repetido(Compra c,Confirmacion d,String hash){var previa=repo.comando(c.id(),d.comando());if(previa.isEmpty())return false;if(!previa.get().equals(hash))throw new ConflictoException("El comando conserva otro contenido.");return true;}
    private static void version(Compra c,Confirmacion d){if(c.version()!=d.version())throw new ConflictoException("Revisa la versión actual de la compra.");}
    private void evento(Compra c,UUID id,long cuenta,UUID concesion,String tipo,String hash,Object datos){repo.evento(c,id,cuenta,concesion,tipo,hash,json.objetoCanonico(datos),ahora());}
    private String huella(Object d){return Idempotencia.huellaExacta(json.objetoCanonico(d));}
    private static void texto(String s,int max,String nombre){if(s==null||s.isBlank()||s.length()>max)throw new ReglaNegocioException(nombre+" requerido.");}
    private static void pagina(int limite,int offset){if(limite<1||limite>50||offset<0)throw new ReglaNegocioException("Solicita entre1 y50 compras y un desplazamiento no negativo.");}
    private Instant ahora(){return reloj.instant().truncatedTo(ChronoUnit.MICROS);}
}
