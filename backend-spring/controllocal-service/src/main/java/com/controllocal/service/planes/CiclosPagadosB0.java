package com.controllocal.service.planes;

import com.controllocal.domain.planes.*;
import com.controllocal.domain.presupuesto.PrecisionCapacidad;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.controllocal.service.planes.ComprasVerificadasB0.*;

/** CAT-R23/E38: aplicar un hecho verificado no equivale a comprar, cobrar o ajustar saldo. */
@Service
public class CiclosPagadosB0 {
    public static final String VERSION_OFERTA="BROX-OFERTA-R04";
    public record Resultado(UUID pedido,String estado){}
    private final List<ComprasVerificadasB0> fuentes;
    private final PresupuestoIaRepository presupuesto;
    private final CiclosPagadosB0Repository ciclos;
    private final Documentos json;
    private final Clock reloj;
    @org.springframework.beans.factory.annotation.Autowired
    public CiclosPagadosB0(List<ComprasVerificadasB0> fuentes,PresupuestoIaRepository presupuesto,CiclosPagadosB0Repository ciclos,Documentos json){
        this(fuentes,presupuesto,ciclos,json,Clock.systemUTC());
    }
    public CiclosPagadosB0(List<ComprasVerificadasB0> fuentes,PresupuestoIaRepository presupuesto,CiclosPagadosB0Repository ciclos,Documentos json,Clock reloj){
        this.fuentes=List.copyOf(fuentes);this.presupuesto=presupuesto;this.ciclos=ciclos;this.json=json;this.reloj=reloj;
    }
    /** No recibe pago booleano, importe ni rol del cliente; no ejecuta una pasarela. */
    @Transactional
    public Resultado aplicar(UUID idPedido){
        if(idPedido==null)throw new ReglaNegocioException("Identifica el pedido original.");
        var encontrados=fuentes.stream().map(f->f.consultar(idPedido)).flatMap(Optional::stream).toList();
        if(encontrados.size()!=1)throw new CapacidadDeshabilitadaException();Pedido p=encontrados.getFirst();Instant ahora=ahora();
        validar(p,idPedido,ahora);Organizacion org=presupuesto.bloquearOrganizacion(p.organizacion());
        if(org==null||!org.activa())throw new AccesoNoAutorizadoException();
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(p));var previa=ciclos.acreditacion(p.organizacion(),p.id());
        if(previa.isPresent()){
            if(!previa.get().equals(hash))throw new ConflictoException("El pedido ya conserva otra acreditación.");
            return new Resultado(p.id(),"ACREDITADO");
        }
        Ciclo actual=presupuesto.cicloVigente(p.organizacion(),ahora).orElse(null);
        if(p.tipo()!=Tipo.BOOST)validarModalidad(p,org);
        switch(p.tipo()){
            case BOOST -> boost(p,ahora);
            case BROKER_ADICIONAL -> broker(p,actual,ahora);
            case ALTA_PLAN -> alta(p,actual,ahora);
            case MEJORA_PLAN -> mejora(p,actual,ahora);
            case RENOVACION -> renovacion(p,actual,ahora);
        }
        presupuesto.antecedente(p.organizacion(),null,"COMPRA_ACREDITADA",json.objetoCanonico(Map.of("pedido",p.id(),"tipo",p.tipo())),ahora);
        return new Resultado(p.id(),"ACREDITADO");
    }
    private void alta(Pedido p,Ciclo actual,Instant ahora){
        if(actual!=null||ciclos.tieneFuturo(p.organizacion(),ahora))throw new ConflictoException("Ya existe un período financiado; conserva la misma orden para resolverlo.");
        if(p.cicloEsperado()!=null||p.versionCicloEsperada()!=null)throw new ConflictoException("La cotización identifica otro ciclo.");
        montoExacto(recurrente(p.plan(),p.brokersExtra()),p.importeUsd());acreditar(p,ahora);
        crearCiclo(p,ahora,ahora.atZone(CalendarioComercial.ZONA).getDayOfMonth(),ahora);
    }
    private void mejora(Pedido p,Ciclo actual,Instant ahora){
        cicloExacto(p,actual);
        if(ciclos.tieneFuturo(p.organizacion(),ahora))throw new ConflictoException("Conserva la renovación ya financiada antes de resolver este cambio.");
        PlanBrox anterior=PlanBrox.valueOf(actual.plan());
        if(anterior.empresarial()!=p.plan().empresarial()||p.plan().precioUsd().compareTo(anterior.precioUsd())<=0)
            throw new ReglaNegocioException("Una reducción corresponde a la renovación.");
        var lineas=ciclos.lineas(p.organizacion(),actual.id());
        if(lineas.isEmpty())throw new ConflictoException("El período requiere sus líneas financiadas originales.");
        for(var l:lineas){
            if(p.corte().isBefore(l.desde())||!p.corte().isBefore(l.hasta()))throw new ConflictoException("Cambió la financiación desde la cotización.");
        }
        CotizacionDePeriodo cotizacion;
        try{cotizacion=CotizacionDePeriodo.mejoraConLineas(recurrente(p.plan(),p.brokersExtra()),
                lineas.stream().map(l->new CotizacionDePeriodo.LineaFinanciada(l.financiado(),l.desde(),l.hasta())).toList(),p.corte());}
        catch(IllegalArgumentException ex){throw new ConflictoException("La cotización necesita resolver sus líneas financiadas.");}
        montoExacto(cotizacion.totalUsd(),p.importeUsd());acreditar(p,ahora);ciclos.cerrarCiclo(p.organizacion(),actual.id(),actual.version(),ahora);
        crearCiclo(p,ahora,ahora.atZone(CalendarioComercial.ZONA).getDayOfMonth(),ahora);
    }
    private void renovacion(Pedido p,Ciclo actual,Instant ahora){
        if(actual==null){
            // Reactivación después del vencimiento: no inventa meses atrasados.
            if(p.cicloEsperado()==null){alta(p,null,ahora);return;}
            Ciclo anterior=presupuesto.ciclo(p.organizacion(),p.cicloEsperado()).orElseThrow(()->new NoEncontradoException("Ciclo"));
            cicloExacto(p,anterior);
            if(anterior.hasta().isAfter(ahora)||ciclos.tieneFuturo(p.organizacion(),ahora))throw new ConflictoException("Existe un período financiado que debe conservarse.");
            montoExacto(recurrente(p.plan(),p.brokersExtra()),p.importeUsd());acreditar(p,ahora);
            crearCiclo(p,ahora,ahora.atZone(CalendarioComercial.ZONA).getDayOfMonth(),ahora);return;
        }
        cicloExacto(p,actual);
        if(ciclos.tieneFuturo(p.organizacion(),ahora))throw new ConflictoException("El siguiente período ya tiene financiación.");
        if(PlanBrox.valueOf(actual.plan()).empresarial()!=p.plan().empresarial())throw new ReglaNegocioException("El plan debe corresponder al espacio actual.");
        montoExacto(recurrente(p.plan(),p.brokersExtra()),p.importeUsd());acreditar(p,ahora);
        crearCiclo(p,actual.hasta(),actual.aniversario(),ahora);
    }
    private void broker(Pedido p,Ciclo actual,Instant ahora){
        cicloExacto(p,actual);
        if(!PlanBrox.valueOf(actual.plan()).empresarial()||p.plan()!=PlanBrox.valueOf(actual.plan())||p.brokersExtra()!=actual.brokersExtra()+1)
            throw new ReglaNegocioException("El complemento corresponde al ciclo empresarial actual.");
        montoExacto(CotizacionDePeriodo.brokerAdicional(actual.desde(),actual.hasta(),p.corte()),p.importeUsd());
        acreditar(p,ahora);ciclos.brokerAdicional(p.organizacion(),actual.id(),actual.version(),p.brokersExtra());
        UUID fuente=ciclos.fuenteBroker(p.organizacion(),actual.id()).orElseThrow(()->new ConflictoException("El ciclo no conserva su bolsa broker."));
        ciclos.plaza(p.organizacion(),actual.id(),"BROKER",p.brokersExtra()+1,fuente);
        ciclos.linea(p.organizacion(),actual.id(),p.id(),"BROKER_EXTRA",p.importeUsd(),ahora,actual.hasta());
    }
    private void boost(Pedido p,Instant ahora){
        if(p.boost()==null||p.plan()!=null||p.brokersExtra()!=0)throw new ReglaNegocioException("Identifica el paquete Boost de la orden.");
        Ciclo alComprar=presupuesto.cicloVigente(p.organizacion(),p.fondosVerificadosEn()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(alComprar.revocado())throw new CapacidadDeshabilitadaException();
        montoExacto(p.boost().precioUsd(),p.importeUsd());acreditar(p,ahora);
        ciclos.fuente(p.organizacion(),null,"BOOST",null,BigDecimal.valueOf(p.boost().creditos()),"PEDIDO:"+p.id(),
                p.fondosVerificadosEn(),CalendarioComercial.vencimientoBoost(p.fondosVerificadosEn()),ahora);
    }
    private void crearCiclo(Pedido p,Instant desde,int aniversario,Instant ahora){
        UUID id=UUID.randomUUID();Instant hasta=CalendarioComercial.siguienteMes(desde,aniversario);PlanBrox plan=p.plan();
        ciclos.nuevoCiclo(p.organizacion(),id,plan.name(),desde,hasta,aniversario,recurrente(plan,p.brokersExtra()),p.id(),p.evidenciaVerificacion(),p.brokersExtra(),ahora);
        ciclos.linea(p.organizacion(),id,p.id(),"PLAN",plan.precioUsd(),desde,hasta);
        for(int i=1;i<=plan.plazasAgente();i++){
            UUID f=ciclos.fuente(p.organizacion(),id,"MENSUAL_AGENTE",i,BigDecimal.valueOf(plan.cuotaAgente()),"CICLO:"+id+":AGENTE:"+i,desde,hasta,ahora);
            ciclos.plaza(p.organizacion(),id,"AGENTE",i,f);
        }
        if(plan.empresarial()){
            UUID broker=ciclos.fuente(p.organizacion(),id,"MENSUAL_BROKER",null,BigDecimal.valueOf(plan.bolsaBroker()),"CICLO:"+id+":BROKER",desde,hasta,ahora);
            // La fuente permanece única; las plazas no son bolsas adicionales.
            ciclos.plaza(p.organizacion(),id,"BROKER",1,broker);
            ciclos.fuente(p.organizacion(),id,"RESERVA",null,BigDecimal.valueOf(plan.reservaAsignable()),"CICLO:"+id+":RESERVA",desde,hasta,ahora);
            if(p.brokersExtra()>0)ciclos.linea(p.organizacion(),id,p.id(),"BROKER_EXTRA",PlanBrox.precioBrokerExtraUsd().multiply(BigDecimal.valueOf(p.brokersExtra())),desde,hasta);
        }
    }
    private void acreditar(Pedido p,Instant ahora){
        String contenido=json.objetoCanonico(p);if(!ciclos.acreditar(p.organizacion(),p.id(),p.proveedor(),p.transaccion(),Idempotencia.huellaExacta(contenido),contenido,ahora))
            throw new ConflictoException("La transacción ya identifica otra acreditación.");
    }
    private static void cicloExacto(Pedido p,Ciclo actual){
        if(actual==null||actual.revocado()||!Objects.equals(p.cicloEsperado(),actual.id())||p.versionCicloEsperada()==null
                ||p.versionCicloEsperada()!=actual.version()||p.corte().isBefore(actual.desde())||!p.corte().isBefore(actual.hasta()))
            throw new ConflictoException("El ciclo cambió desde la cotización; conserva la misma orden para resolverlo.");
    }
    private static void validarModalidad(Pedido p,Organizacion org){
        if(p.plan()==null||!p.plan().pagado()||p.boost()!=null||p.plan().empresarial()!= "EMPRESARIAL".equals(org.modalidad())
                ||!Set.of("EMPRESARIAL","INDIVIDUAL").contains(org.modalidad())||!p.plan().empresarial()&&p.brokersExtra()!=0)
            throw new ReglaNegocioException("El plan debe corresponder al espacio del pedido.");
    }
    private static void validar(Pedido p,UUID id,Instant ahora){
        if(p==null||!id.equals(p.id())||p.organizacion()<=0||p.tipo()==null||!VERSION_OFERTA.equals(p.versionOferta())
                ||p.huellaConsentimiento()==null||!p.huellaConsentimiento().matches("[0-9a-f]{64}")||p.corte()==null
                ||p.fondosVerificadosEn()==null||p.fondosVerificadosEn().isBefore(p.corte())||p.fondosVerificadosEn().isAfter(ahora)
                ||p.importeUsd()==null||p.importeUsd().signum()<0||p.importeUsd().scale()>2||p.importeUsd().precision()>12||!"USD".equals(p.moneda())||p.brokersExtra()<0||p.brokersExtra()==Integer.MAX_VALUE
                ||!texto(p.proveedor(),100)||!texto(p.transaccion(),200)||!texto(p.evidenciaVerificacion(),4000))
            throw new ReglaNegocioException("El pedido requiere sus condiciones consentidas y fondos verificados en servidor.");
    }
    private static BigDecimal recurrente(PlanBrox plan,int extras){return plan.precioUsd().add(PlanBrox.precioBrokerExtraUsd().multiply(BigDecimal.valueOf(extras)));}
    private static void montoExacto(BigDecimal esperado,BigDecimal recibido){if(esperado.compareTo(recibido)!=0)throw new ConflictoException("Los fondos no coinciden con la cotización consentida.");}
    private static boolean texto(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max;}
    private Instant ahora(){return reloj.instant().truncatedTo(ChronoUnit.MICROS);}
}
