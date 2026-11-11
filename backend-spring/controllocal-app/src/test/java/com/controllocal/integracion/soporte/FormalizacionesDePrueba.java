package com.controllocal.integracion.soporte;

import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.*;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.encargo.*;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.formalizacion.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.*;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Banco TEST C5: C2 manuscrita y actos humanos Core reales. No acredita una firma legal,
 * operación bancaria, clic de navegador ni login HTTP. No inserta hitos, dinero o aprobaciones por SQL.
 */
public final class FormalizacionesDePrueba {
    public final JdbcTemplate jdbc;
    public final EncargoService encargos;
    public final FormalizacionService formalizacion;
    public final ComisionService comisiones;
    public final OfertaService ofertas;
    public final OportunidadService oportunidades;
    public final ActuacionesService actuaciones;
    public final PactosDeEncargo pactos;
    public final SesionesComercialesDePrueba sesiones;
    public final ActividadNegociacionDePrueba negociacion;
    public final ActuacionesHumanasC4DePrueba humanas;
    private final EncargosDePrueba bancoEncargos;
    private final EvidenciasDeFormalizacion evidencias;

    public FormalizacionesDePrueba(ApplicationContext contexto){
        jdbc=contexto.getBean(JdbcTemplate.class);encargos=contexto.getBean(EncargoService.class);
        formalizacion=contexto.getBean(FormalizacionService.class);comisiones=contexto.getBean(ComisionService.class);
        ofertas=contexto.getBean(OfertaService.class);oportunidades=contexto.getBean(OportunidadService.class);
        actuaciones=contexto.getBean(ActuacionesService.class);pactos=contexto.getBean(PactosDeEncargo.class);
        evidencias=contexto.getBean(EvidenciasDeFormalizacion.class);
        var gestor=contexto.getBean(PlatformTransactionManager.class);var contextos=contexto.getBean(ContextosService.class);
        var documentos=contexto.getBean(Documentos.class);var archivos=contexto.getBean(AccesoArchivoService.class);
        var almacen=contexto.getBean(AlmacenDocumentos.class);
        sesiones=new SesionesComercialesDePrueba(jdbc,contextos,contexto.getBean(CuentaService.class),
                contexto.getBean(FactorAutenticacionRepository.class),contexto.getBean(CifradoSecretos.class));
        var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contextos);
        negociacion=new ActividadNegociacionDePrueba(jdbc,contexto.getBean(PropiedadUniversalService.class),encargos,
                contexto.getBean(PropietarioService.class),contexto.getBean(ClienteService.class),oportunidades,ofertas,
                actuaciones,documentos,origenes,sesiones,gestor,archivos,almacen);
        bancoEncargos=new EncargosDePrueba(jdbc,encargos,gestor,archivos,almacen,actuaciones,documentos);
        humanas=new ActuacionesHumanasC4DePrueba(actuaciones,documentos);
    }

    public record Caso(ActividadNegociacionDePrueba.Caso comercial,ReferenciaPacto pacto,
                       EncargoService.Condiciones condiciones,ReferenciaDocumento documento) {
        public Actor agente(){return comercial.agente();}
        public Actor broker(){return comercial.equipo().broker();}
        public long encargo(){return comercial.encargo();}
        public long oportunidad(){return comercial.oportunidad();}
    }
    public record HitoRevisado(Caso caso,Expediente expediente,Hito informado,AcuerdoFinalDocumentado acuerdo) { }

    public Caso nuevo(String operacion,EncargoService.Condiciones condiciones){
        return nuevoEnEquipo(null,operacion,condiciones);
    }
    public Caso nuevoEnEquipo(OrigenesDePrueba.Equipo equipo,String operacion,EncargoService.Condiciones condiciones){
        var c=equipo==null?negociacion.mesa(operacion,condiciones.precioPedido(),condiciones.monedaPrecio(),false):
                negociacion.mesaEnEquipo(equipo,operacion,condiciones.precioPedido(),condiciones.monedaPrecio(),false);
        var resultado=bancoEncargos.habilitarExistente(c.encargo(),new EncargosDePrueba.Caso(c.propiedad(),
                OperacionInmobiliaria.desde(operacion),hoy(),hoy().plusMonths(12),false,condiciones,
                new EncargosDePrueba.Actuante(c.agente(),sesiones.de(c.agente())),new EncargosDePrueba.Actuante(c.equipo().broker(),sesiones.de(c.equipo().broker()))));
        Long revision=resultado.peticion().ultimaRevision();
        if(revision==null)throw new AssertionError("El instrumento TEST no conserva su revisión aprobatoria Core.");
        return desde(c,new ReferenciaPacto(c.encargo(),revision),condiciones);
    }
    public Caso desde(ActividadNegociacionDePrueba.Caso c){
        var p=jdbc.queryForObject("select id_revision_aplicable from captacion where organizacion_id=? and id_captacion=?",Long.class,c.agente().idOrganizacion(),c.encargo());
        if(p==null)throw new AssertionError("El fixture C4 no conserva un pacto firmado aprobado.");
        return desde(c,new ReferenciaPacto(c.encargo(),p),encargos.consultar(c.encargo(),c.agente()).condiciones());
    }
    private Caso desde(ActividadNegociacionDePrueba.Caso c,ReferenciaPacto pacto,EncargoService.Condiciones condiciones){
        pactos.consultarPactoFirmado(pacto,c.equipo().broker());
        var documento=archivo(c.encargo(),"HITO_CONTRACTUAL",pdf("ACTA CONTRACTUAL TEST",c.encargo(),"Contexto contractual sintetico aportado expresamente",false),c.agente());
        return new Caso(c,pacto,condiciones,documento);
    }

    public Expediente presentar(Caso c,boolean conAcuerdo){
        var a=conAcuerdo?ofertas.consultarAcuerdoAceptado(c.oportunidad(),null,c.agente())
                .orElseThrow(()->new AssertionError("El caso pide un acuerdo C4 real aceptado.")):null;
        var datos=new DatosPresentacion(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),
                c.encargo(),encargos.consultar(c.encargo(),c.agente()).version(),a==null?null:a.id(),a==null?null:a.version(),
                c.pacto(),List.of(c.documento()),null,null,null);
        var p=actuaciones.prepararPresentacionExpediente(c.agente(),datos,sesiones.de(c.agente()),procedencia());
        var resultado=humanas.confirmar(p,c.agente(),sesiones.de(c.agente()),Expediente.class).datos();
        EvidenciaC4DePrueba.recurso(c.agente(),"EXPEDIENTE_OPERACION",resultado.id(),resultado.version());return resultado;
    }
    public Expediente aprobar(Caso c,Expediente e,AcuerdoFinalDocumentado acuerdo){
        var actual=formalizacion.consultar(e.id(),c.broker());
        var datos=new DatosRevision(e.id(),actual.version(),"APROBAR","Revisión expresa de fuentes sintéticas TEST",actual.documentos(),true,true,acuerdo,null);
        var p=actuaciones.prepararEvaluacionExpediente(c.broker(),datos,sesiones.de(c.broker()),procedencia());
        var resultado=humanas.confirmar(p,c.broker(),sesiones.de(c.broker()),Expediente.class).datos();
        EvidenciaC4DePrueba.recurso(c.broker(),"EXPEDIENTE_OPERACION",resultado.id(),resultado.version());return resultado;
    }
    public Hito informar(Caso c,Expediente e,TipoHitoContractual tipo,LocalDate fecha){
        var actual=formalizacion.consultar(e.id(),c.agente());
        var documento=archivo(c.encargo(),"HITO_CONTRACTUAL",pdf("HECHO CONTRACTUAL TEST",c.encargo(),tipo.name()+"; fecha efectiva declarada TEST: "+fecha,true),c.agente());
        var datos=new DatosInformacionHito(e.id(),actual.version(),tipo,fecha,List.of(documento),"Hecho contractual sintético TEST informado explícitamente");
        var p=actuaciones.prepararInformacionHito(c.agente(),datos,sesiones.de(c.agente()),procedencia());
        var resultado=humanas.confirmar(p,c.agente(),sesiones.de(c.agente()),Hito.class).datos();
        EvidenciaC4DePrueba.recurso(c.agente(),"HITO_CONTRACTUAL",resultado.id(),resultado.version());return resultado;
    }
    public Propuesta prepararConfirmacion(HitoRevisado r){return prepararConfirmacion(r,r.caso().broker());}
    public Propuesta prepararConfirmacion(HitoRevisado r,Actor decisor){
        var e=formalizacion.consultar(r.expediente().id(),decisor);var h=r.informado();
        var d=new DatosConfirmacionHito(h.id(),h.version(),e.version(),e.revisionAplicable(),h.revisionInformada(),r.caso().pacto(),r.acuerdo());
        return actuaciones.prepararConfirmacionHito(decisor,d,sesiones.de(decisor),procedencia());
    }
    public Hito confirmar(HitoRevisado r){
        var resultado=humanas.confirmar(prepararConfirmacion(r),r.caso().broker(),sesiones.de(r.caso().broker()),Hito.class).datos();
        EvidenciaC4DePrueba.recurso(r.caso().broker(),"HITO_CONTRACTUAL",resultado.id(),resultado.version());return resultado;
    }
    public HitoRevisado listo(Caso c,TipoHitoContractual tipo,LocalDate fecha,AcuerdoFinalDocumentado acuerdo,boolean conAcuerdo){
        var e=aprobar(c,presentar(c,conAcuerdo),acuerdo);var h=informar(c,e,tipo,fecha);return new HitoRevisado(c,e,h,acuerdo);
    }
    public ComisionService.FichaComision comision(Caso c,Expediente e){
        return comisiones.consultar(c.pacto(),new ReferenciaContextoEconomico(e.id()),c.broker());
    }
    public AcuerdoFinalDocumentado venta(Caso c,BigDecimal precio,String moneda,OfertaService.Dinero declarado){
        var doc=archivo(c.encargo(),"HITO_CONTRACTUAL",pdf("ACUERDO FINAL TEST",c.encargo(),"Precio pactado TEST: "+precio+" "+moneda+"; declarado: "+declarado,false),c.agente());
        return new AcuerdoFinalDocumentado(new OfertaService.Dinero(precio,moneda,"PRECIO_FINAL_DECLARADO",null),null,declarado,List.of(doc),"Acuerdo final documentado TEST",null);
    }
    public AcuerdoFinalDocumentado alquiler(Caso c,BigDecimal renta,String moneda){
        var doc=archivo(c.encargo(),"HITO_CONTRACTUAL",pdf("RENTA CONTRACTUAL TEST",c.encargo(),"Renta TEST sin cargos: "+renta+" "+moneda+"; inicio: "+hoy()+"; fin: "+hoy().plusMonths(12),false),c.agente());
        return new AcuerdoFinalDocumentado(null,new OfertaService.Dinero(renta,moneda,"RENTA_DECLARADA","MENSUAL"),null,
                List.of(doc),"Renta contractual documentada TEST",new TerminosAlquiler(hoy(),hoy().plusMonths(12)));
    }
    public Propuesta prepararCobro(Caso c,Expediente e,BigDecimal importe,LocalDate fecha,String referencia){
        return prepararCobro(c,e,importe,fecha,referencia,c.broker());
    }
    public Propuesta prepararCobro(Caso c,Expediente e,BigDecimal importe,LocalDate fecha,String referencia,Actor actor){
        var f=comisiones.consultar(c.pacto(),new ReferenciaContextoEconomico(e.id()),actor);
        var regla=pactos.consultarPactoFirmado(c.pacto(),actor).regla();
        var obligado=new ComisionService.SujetoDeclarado(new ComisionService.ReferenciaSujeto("CAMPO_PACTO",null,c.pacto(),null,"parteObligada"),regla.parteObligada());
        var beneficiario=new ComisionService.SujetoDeclarado(new ComisionService.ReferenciaSujeto("CAMPO_PACTO",null,c.pacto(),null,"beneficiario"),regla.beneficiario());
        var evidencia=archivo(c.encargo(),"COBRO_COMISION",pdf("CONSTANCIA DE COBRO TEST",c.encargo(),"Recibido TEST: "+importe+" "+f.calculo().cuantia().moneda()+"; fecha: "+fecha,false),c.agente());
        var d=new ComisionService.DatosCobro(f.id(),f.version(),null,importe,f.calculo().cuantia().moneda(),fecha,"TRANSFERENCIA",
                obligado,beneficiario,new ComisionService.DestinoPago("Destino bancario ficticio TEST",beneficiario),referencia,List.of(evidencia),"Cobro externo sintético TEST, no operación bancaria");
        return actuaciones.prepararMovimientoComision(actor,d,sesiones.de(actor),procedencia());
    }
    public ActuacionesHumanasC4DePrueba.Confirmada<ComisionService.ResultadoCobro> cobrar(Caso c,Propuesta p){
        var r=humanas.confirmar(p,c.broker(),sesiones.de(c.broker()),ComisionService.ResultadoCobro.class);
        EvidenciaC4DePrueba.recurso(c.broker(),"COMISION",r.datos().comision(),r.datos().version());
        EvidenciaC4DePrueba.recurso(c.broker(),"COBRO",r.datos().movimiento(),null);return r;
    }
    public Propuesta prepararReprogramacion(Caso c,Expediente e,LocalDate fecha,String motivo){
        var f=comision(c,e);return actuaciones.prepararReprogramacionCobro(c.broker(),new ComisionService.DatosReprogramacion(f.id(),f.version(),fecha,motivo),sesiones.de(c.broker()),procedencia());
    }
    public Propuesta prepararConclusion(Caso c,Expediente e,String motivo){
        var f=comision(c,e);return actuaciones.prepararConclusionCobro(c.broker(),new ComisionService.DatosConclusion(f.id(),f.version(),motivo),sesiones.de(c.broker()),procedencia());
    }
    public ActuacionesHumanasC4DePrueba.Confirmada<ComisionService.ResultadoGestion> gestionar(Caso c,Propuesta p){
        var r=humanas.confirmar(p,c.broker(),sesiones.de(c.broker()),ComisionService.ResultadoGestion.class);
        EvidenciaC4DePrueba.recurso(c.broker(),"COMISION",r.datos().comision(),r.datos().version());return r;
    }

    public ReferenciaDocumento archivo(long encargo,String finalidad,byte[] contenido,Actor agente){
        long v=encargos.consultar(encargo,agente).version();
        var carga=encargos.cargarArchivo(new EncargoService.CargaArchivo(encargo,null,finalidad,"evidencia-sintetica-TEST.pdf","application/pdf",contenido,v),
                agente,sesiones.de(agente),clave(),procedencia());
        var a=encargos.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),encargo,null,finalidad,v),agente,sesiones.de(agente),clave(),procedencia());
        return evidencias.consultar(encargo,a.referencia(),finalidad,agente);
    }
    /** PDF válido y rotulado TEST; el trazo lo produce la misma transformación usada por C2. */
    static byte[] pdf(String titulo,long encargo,String detalle,boolean firmaDeclarada){
        return DocumentoManuscritoDePrueba.acta(titulo,encargo,detalle,firmaDeclarada);
    }
    public static LocalDate hoy(){return OrigenesDePrueba.hoy();}
    public static Procedencia procedencia(){return Procedencia.deLaPantalla();}
    public static String clave(){return "C5-"+UUID.randomUUID();}
}
