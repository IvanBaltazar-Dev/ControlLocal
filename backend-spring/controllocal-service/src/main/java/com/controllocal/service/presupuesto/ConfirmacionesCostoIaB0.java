package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository.Financiacion;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** CAT-R25. Conservar un costo causado no lo convierte en otro permiso de gasto o lectura general. */
@Service
public class ConfirmacionesCostoIaB0 {
    // Límite representable del numeric nativo de PostgreSQL, no cuantización ni coeficiente comercial.
    private static final int MAXIMA_ESCALA_NATIVA = 16383;
    public record Solicitud(UUID confirmacion,UUID medicion,String huellaMedicion,UUID anterior,String cuentaProveedor,String emisorFacturacion,
            String documento,String linea,String revisionFuente,BigDecimal costoTotalUsd,String fechaFuente,
            String evidencia,String huellaEvidencia,String causa){
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String campo,Object valor){
            throw new IllegalArgumentException("La confirmación sólo admite su contrato completo por medición.");
        }
    }
    public record Recuperacion(UUID confirmacion){
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String campo,Object valor){
            throw new IllegalArgumentException("La recuperación sólo referencia un hecho conservado.");
        }
    }
    /** Recibo de la actuación, sin otorgar LEER_LEDGER al escritor. Condición derivada, no estado B0 nuevo. */
    public record Recibo(UUID confirmacion,long revision,boolean aplicada,String motivo){}
    public record Hecho(String esquema,UUID confirmacion,UUID operacionB0,UUID medicion,String proveedor,
            String huellaMedicion,UUID anterior,String fuenteHash,String fuenteNaturalHash,BigDecimal costoTotalUsd,String solicitudHash,
            Solicitud solicitud,long cuenta,UUID concesion,String sidHash,long revisionSesion,Instant registradaEn){}
    private record Aplicacion(String esquema,UUID confirmacion,long revisionAnterior,long revision,String conjunto,BigDecimal diferenciaUsd,
            BigDecimal efectivoUsd,BigDecimal imputadoUsd,long cuenta,UUID concesion,String sidHash,long revisionSesion){}
    private record Fuente(String esquema,UUID confirmacion,String fuenteNaturalHash,UUID operacionB0,UUID medicion,
            String cuentaProveedor,String emisorFacturacion,String documento,String linea,long cuenta,UUID concesion,
            String sidHash,long revisionSesion,Instant registradaEn){}
    private record Contexto(Actual autoridad,Operacion operacion,Fondo fondo,Financiacion financiacion){}
    private final PresupuestoIaService b0;private final PresupuestoIaRepository repo;
    private final EvaluacionesIaB0Repository evaluaciones;private final AtribucionesPlataforma autoridad;
    private final Documentos json;private final TransactionTemplate tx;
    private final CostosTecnicosIaB0 costos;
    public ConfirmacionesCostoIaB0(PresupuestoIaService b0,PresupuestoIaRepository repo,EvaluacionesIaB0Repository evaluaciones,
            AtribucionesPlataforma autoridad,Documentos json,PlatformTransactionManager gestor){
        this.b0=b0;this.repo=repo;this.evaluaciones=evaluaciones;this.autoridad=autoridad;this.json=json;
        // Colaborador sin estado: conserva el repositorio de esta actuación y no lee campos del proxy Spring de B0.
        this.costos=new CostosTecnicosIaB0(repo,json);
        tx=new TransactionTemplate(gestor);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Recibo confirmar(UUID caso,UUID comando,Solicitud solicitud,SesionPlataforma sesion){
        fueraSql();validar(solicitud);
        tx.executeWithoutResult(t->conservar(caso,comando,solicitud,sesion));
        // El mismo hecho ya está comprometido: aplicar no requiere otra aceptación ni otra petición económica.
        return aplicar(caso,comando,new Recuperacion(solicitud.confirmacion()),sesion);
    }
    public Recibo aplicar(UUID caso,UUID comando,Recuperacion recuperacion,SesionPlataforma sesion){
        fueraSql();if(recuperacion==null||recuperacion.confirmacion()==null)throw new ReglaNegocioException("Identifica la confirmación conservada.");
        return tx.execute(t->{
            var c=bloquear(caso,comando,sesion);var o=c.operacion();
            var h=hecho(recuperacion.confirmacion());if(!o.tecnica().equals(h.operacionB0()))throw new AccesoNoAutorizadoException();
            var p=costos.refrescar(o);
            String motivo;
            boolean aplicada=false;
            if(!repo.ultimaConfirmacionCosto(o.tecnica(),h.medicion()).filter(h.confirmacion()::equals).isPresent())motivo="CONFIRMACION_RECTIFICADA";
            else if(!CostosTecnicosIaB0.cerrada(o.estado()))motivo="CONJUNTO_AUN_ABIERTO";
            else{
                BigDecimal diferencia=p.efectivo().subtract(p.imputado());
                if(diferencia.signum()==0){aplicada=true;motivo="SIN_DIFERENCIA_PENDIENTE";}
                else if(diferencia.signum()>0&&(p.efectivo().compareTo(o.maximoTecnico())>0
                        ||evaluaciones.comprometido(o.autorizacion()).compareTo(c.financiacion().maximo())>0))motivo="LIMITE_TECNICO_ORIGINAL";
                else if(diferencia.signum()>0&&c.fondo().financiado().subtract(c.fondo().retenido()).subtract(c.fondo().liquidado()).compareTo(diferencia)<0)
                    motivo="PROVISION_INSUFICIENTE";
                else{
                    revalidar(c,sesion);
                    repo.aplicarDiferenciaCosto(o.fondo(),diferencia);
                    var nueva=costos.imputarAjuste(p);
                    repo.antecedente(null,o.tecnica(),"APLICACION_COSTO_IA",json.objetoCanonico(new Aplicacion(
                        "APLICACION-COSTO-IA-1",h.confirmacion(),p.version(),nueva.version(),p.huella(),diferencia,
                        nueva.efectivo(),nueva.imputado(),c.autoridad().cuenta(),c.autoridad().concesion(),
                        Idempotencia.huellaExacta(sesion.referenciaSesion()),sesion.revisionSesion())),Instant.now());
                    p=nueva;aplicada=true;motivo="APLICACION_CONSERVADA";
                }
            }
            revalidar(c,sesion);return new Recibo(h.confirmacion(),p.version(),aplicada,motivo);
        });
    }
    private void conservar(UUID caso,UUID comando,Solicitud s,SesionPlataforma sesion){
        var c=bloquear(caso,comando,sesion);var o=c.operacion();
        var original=repo.medicionOriginalCosto(o.tecnica(),s.medicion()).orElseThrow(()->new NoEncontradoException("Medición técnica"));
        if(!original.huella().equals(s.huellaMedicion()))throw new ConflictoException("La confirmación conserva otra medición original.");
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(s));var previa=repo.confirmacionCosto(s.confirmacion());
        if(previa.isPresent()){
            var h=json.comoObjeto(previa.get(),Hecho.class);
            if(!o.tecnica().equals(h.operacionB0())||!hash.equals(h.solicitudHash()))throw new ConflictoException("La confirmación conserva otro contenido.");
            revalidar(c,sesion);return;
        }
        if(!Objects.equals(repo.ultimaConfirmacionCosto(o.tecnica(),s.medicion()).orElse(null),s.anterior()))
            throw new ConflictoException("La cadena financiera cambió; conserva su última confirmación.");
        String natural=Idempotencia.huellaExacta(s.cuentaProveedor()+"\u0000"+s.documento()+"\u0000"+s.linea());
        var fuente=repo.fuenteCosto(natural);
        if(fuente.isPresent()){
            var f=json.comoObjeto(fuente.get(),Fuente.class);
            if(!o.tecnica().equals(f.operacionB0())||!s.medicion().equals(f.medicion())||!s.emisorFacturacion().equals(f.emisorFacturacion()))
                throw new ConflictoException("La fuente financiera conserva otro dueño o emisor acreditado.");
        }else{
            var f=new Fuente("FUENTE-COSTO-IA-1",s.confirmacion(),natural,o.tecnica(),s.medicion(),s.cuentaProveedor(),s.emisorFacturacion(),
                s.documento(),s.linea(),c.autoridad().cuenta(),c.autoridad().concesion(),Idempotencia.huellaExacta(sesion.referenciaSesion()),sesion.revisionSesion(),Instant.now());
            revalidar(c,sesion);repo.antecedente(null,o.tecnica(),"FUENTE_COSTO_IA",json.objetoCanonico(f),f.registradaEn());
        }
        var h=new Hecho("COSTO-IA-1",s.confirmacion(),o.tecnica(),s.medicion(),original.proveedor(),s.huellaMedicion(),s.anterior(),
            Idempotencia.huellaExacta(natural+"\u0000"+s.revisionFuente()),natural,
            s.costoTotalUsd(),hash,s,c.autoridad().cuenta(),c.autoridad().concesion(),Idempotencia.huellaExacta(sesion.referenciaSesion()),sesion.revisionSesion(),Instant.now());
        revalidar(c,sesion);
        repo.antecedente(null,o.tecnica(),"CONFIRMACION_COSTO_IA",json.objetoCanonico(h),h.registradaEn());
        costos.refrescar(o);revalidar(c,sesion);
    }
    private Contexto bloquear(UUID caso,UUID comando,SesionPlataforma sesion){
        var a=autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.AJUSTAR_COSTO,Campo.LEDGER);
        if(caso==null||comando==null||a.fondo()==null||a.recursoCompra()!=null||a.caso()!=null&&!a.caso().equals(caso))throw new AccesoNoAutorizadoException();
        var c=evaluaciones.caso(caso).orElseThrow(AccesoNoAutorizadoException::new);
        var plan=evaluaciones.plan(c.plan(),c.version()).orElseThrow(AccesoNoAutorizadoException::new);
        if(!a.plan().equals(c.plan())||!a.versionPlan().equals(c.version())||!a.finalidad().equals(plan.finalidad()))throw new AccesoNoAutorizadoException();
        var inicial=repo.evaluacion(caso,comando).orElseThrow(()->new NoEncontradoException("Operación de evaluación"));
        var f=evaluaciones.financiacion(inicial.autorizacion(),true).orElseThrow(AccesoNoAutorizadoException::new);
        if(!f.plan().equals(a.plan())||!f.version().equals(a.versionPlan())||!f.fondo().equals(a.fondo()))throw new AccesoNoAutorizadoException();
        var o=b0.bloquear(new ReferenciaEvaluacion(caso,comando));
        if(!o.autorizacion().equals(f.id())||!o.fondo().equals(a.fondo())||!o.finalidad().equals(a.finalidad()))throw new AccesoNoAutorizadoException();
        var fondo=repo.bloquearFondo(o.fondo()).orElseThrow(AccesoNoAutorizadoException::new);
        costos.asegurarFondo(fondo.id());var contexto=new Contexto(a,o,fondo,f);revalidar(contexto,sesion);return contexto;
    }
    private void revalidar(Contexto antes,SesionPlataforma sesion){
        if(!antes.autoridad().equals(autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.AJUSTAR_COSTO,Campo.LEDGER)))
            throw new AccesoNoAutorizadoException();
    }
    private Hecho hecho(UUID id){return json.comoObjeto(repo.confirmacionCosto(id).orElseThrow(()->new NoEncontradoException("Confirmación de costo")),Hecho.class);}
    private static void validar(Solicitud s){
        if(s==null||s.confirmacion()==null||s.medicion()==null||!hash(s.huellaMedicion())||!hash(s.huellaEvidencia())
                ||s.costoTotalUsd()==null||s.costoTotalUsd().signum()<0
                ||(long)s.costoTotalUsd().precision()-s.costoTotalUsd().scale()>26
                ||s.costoTotalUsd().scale()>MAXIMA_ESCALA_NATIVA
                ||!texto(s.cuentaProveedor())||!texto(s.emisorFacturacion())||!texto(s.documento())||!texto(s.linea())||!texto(s.revisionFuente())||!texto(s.evidencia())||!texto(s.causa()))
            throw new ReglaNegocioException("Conserva un costo completo en USD de la medición y su evidencia financiera.");
        try{try{LocalDate.parse(s.fechaFuente());}catch(java.time.format.DateTimeParseException e){Instant.parse(s.fechaFuente());}}
        catch(RuntimeException e){throw new ReglaNegocioException("Conserva la fecha de la fuente con su precisión real.");}
    }
    private static boolean hash(String x){return x!=null&&x.matches("[0-9a-f]{64}");}
    private static boolean texto(String x){return x!=null&&!x.isBlank()&&x.equals(x.trim())&&x.length()<=2000&&x.chars().noneMatch(c->c<32);}
    private static void fueraSql(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La confirmación requiere fases transaccionales propias.");}
}
