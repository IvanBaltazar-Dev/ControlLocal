package com.controllocal.service.materialia;

import com.controllocal.persistence.materialia.RegistroMaterialIaRepository;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository.Operacion;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.FuentesMaterialIa.Snapshot;
import com.controllocal.service.materialia.FuentesMaterialIa.DestinoTratamiento;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.ResultadoFacturable;
import com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaInterna;
import com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Intención/financiación/claim confirmados por separado. Ningún SQL lock atraviesa el procesador. */
@Service
public class MaterialIaServiceImpl implements MaterialIaService {
    /** Metadatos privados persistibles: los bytes no se serializan a SQL ni a la respuesta. */
    public record DetalleSalida(String texto,String contentType,String huellaFoto,String proceso,String versionProceso) { }
    public record Entregado(String texto,UUID archivo) { }
    public record PlanControl(DestinoTratamiento destino,String clave,String huellaEntrada){}
    public record RespuestaControl(String estado,EvaluacionFacturable resultado){}
    private static final String ETAPA_QC=ControlesMaterialIa.ETAPA_FINANCIERA;
    private final RegistroMaterialIaRepository repo;
    private final PresupuestoIaService presupuesto;
    private final ContextoPresupuestoB0 contexto;
    private final AutoridadMaterialIa autoridad;
    private final PerfilesMaterialIa perfiles;
    private final Documentos json;
    private final List<ProveedorMaterialIa> proveedores;
    private final List<ControlesMaterialIa> controles;
    private final List<ResultadosMaterialIa> resultados;
    private final TransactionTemplate nueva;
    public MaterialIaServiceImpl(RegistroMaterialIaRepository repo,PresupuestoIaService presupuesto,ContextoPresupuestoB0 contexto,
            AutoridadMaterialIa autoridad,PerfilesMaterialIa perfiles,Documentos json,List<ProveedorMaterialIa> proveedores,
            List<ControlesMaterialIa> controles,List<ResultadosMaterialIa> resultados,PlatformTransactionManager gestor){
        this.repo=repo;this.presupuesto=presupuesto;this.contexto=contexto;this.autoridad=autoridad;this.perfiles=perfiles;this.json=json;
        this.proveedores=List.copyOf(proveedores);this.controles=List.copyOf(controles);this.resultados=List.copyOf(resultados);
        nueva=new TransactionTemplate(gestor);nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public Preparacion preparar(Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s,false);return fuera(()->{
            contexto(actor,sesion);var f=autoridad.fuente().preparar(s,actor,sesion);validarFuente(f);
            return new Preparacion(s.operacion(),s.accion(),s.recurso(),f.versionOrigen(),f.utilidadLocal(),f.pendientesLocales());
        });
    }
    @Override public Resultado solicitar(Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s,true);return fuera(()->solicitarFuera(s,actor,sesion));
    }
    @Override public Disponibilidad disponibilidad(Accion accion){
        try{autoridad.fuente();}catch(CapacidadDeshabilitadaException falta){return new Disponibilidad(false,false,"FUENTE_NO_DISPONIBLE");}
        if(accion==null)return new Disponibilidad(true,false,"ACCION_NO_DEFINIDA");
        if(!presupuesto.gastoExternoHabilitado())return new Disponibilidad(true,false,"GASTO_DESHABILITADO");
        try{var p=perfiles.de(accion);proveedor(accion,p.id(),p.version());control(accion,p.id(),p.version());
            if(accion==Accion.MEJORAR_FOTO)almacen();
        }catch(CapacidadDeshabilitadaException falta){return new Disponibilidad(true,false,"CONFIGURACION_NO_DISPONIBLE");}
        return new Disponibilidad(true,true,null);
    }
    @Override public void exigirLectura(UUID id,Actor actor){
        if(id==null)throw new ReglaNegocioException("Identifica la operación de material.");
        var o=cargar(actor.idOrganizacion(),id);AutoridadMaterialIa.actor(o,actor);autoridad.exigirLecturaHistorica(autoridad.solicitud(o),autoridad.snapshot(o),actor);
    }
    private Resultado solicitarFuera(Solicitud s,Actor actor,ContextoSesion sesion){
        contexto(actor,sesion);
        var existente=repo.obtener(actor.idOrganizacion(),s.operacion());
        Operacion o;
        if(existente.isPresent()){
            o=existente.get();misma(o,s,actor);
            if(!Set.of("INTENCION","RESERVADA").contains(o.estado())){
                if(salidaSinTexto(o,json)){resolverSalidaNoUtilizable(o.organizacion(),o.id(),AusenciaSalida.TEXTO);return consultarFuera(cargar(o.organizacion(),o.id()),actor,sesion);}
                if(s.accion()==Accion.MEJORAR_FOTO&&Set.of("MEDIDA","CONCILIADA","FALLO_RESULTADO","CONTROLADA").contains(o.estado()))return recuperarResultadoDurable(o.organizacion(),o.id(),actor,sesion);
                return consultarFuera(o,actor,sesion);
            }
            autoridad.revalidar(s,autoridad.snapshot(o),actor);
        }else{
            var f=autoridad.fuente().preparar(s,actor,sesion);validarFuente(f);
            if(!s.versionOrigen().equals(f.versionOrigen()))throw new ConflictoException("La fuente cambió; vuelve a preparar la actuación.");
            AutoridadMaterialIa.exigirEvidencia(f);var perfil=perfiles.de(s.accion());
            proveedor(s.accion(),perfil.id(),perfil.version());control(s.accion(),perfil.id(),perfil.version());
            if(s.accion()==Accion.MEJORAR_FOTO)almacen();
            String snapshot=json.objetoCanonico(f),solicitud=json.objetoCanonico(s);
            var creada=new Operacion(actor.idOrganizacion(),s.operacion(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),
                    sesion.identidad(),s.accion().name(),hashPeticion(s,actor),solicitud,snapshot,Idempotencia.huellaExacta(snapshot),
                    perfil.id(),perfil.version(),PresupuestoIaService.claveDeIntento(new ReferenciaInterna(actor.idOrganizacion(),s.operacion())),
                    "INTENCION",null,null,null,null,null,0);
            o=nueva.execute(tx->{bloquearEntrada(s,actor,sesion);var anterior=repo.obtener(creada.organizacion(),creada.id());
                if(anterior.isPresent()){misma(anterior.get(),s,actor);return anterior.get();}
                repo.insertar(creada);return cargar(creada.organizacion(),creada.id());});
        }
        var fuente=autoridad.snapshot(o);AutoridadMaterialIa.exigirEvidencia(fuente);
        var proveedor=proveedor(s.accion(),o.perfil(),o.versionPerfil());var controles=control(s.accion(),o.perfil(),o.versionPerfil());
        String idProveedor=proveedor.identificador();
        exigirIdentidadProveedor(idProveedor);
        byte[] foto=s.accion()==Accion.REDACTAR_FICHA?null:autoridad.fuente().fotoParaProcesar(s,fuente,actor,sesion);
        long org=o.organizacion();UUID id=o.id();
        // Mismo prefijo C1→original→job; previsión física antes del fondo financiero y del proveedor.
        nueva.executeWithoutResult(tx->{bloquearEntrada(s,actor,sesion);var actual=cargar(org,id);misma(actual,s,actor);
            if("INTENCION".equals(actual.estado())){
                if(controles instanceof Facturable externo)congelarDestinoControl(actual,externo);
                if(s.accion()==Accion.MEJORAR_FOTO)almacen().previsionar(s,autoridad.snapshot(actual),actor,sesion);
                presupuesto.admitir(AutoridadMaterialIa.presupuesto(actual,s,autoridad.snapshot(actual)),actor,sesion);
                repo.fase(actual,"RESERVADA",null);
            }
        });
        boolean ejecutar=Boolean.TRUE.equals(nueva.execute(tx->{bloquearEntrada(s,actor,sesion);var actual=cargar(org,id);misma(actual,s,actor);
            if(!"RESERVADA".equals(actual.estado()))return false;
            if(controles instanceof Facturable&&repo.ultimoHecho(org,id,"QC_CONFIG").isEmpty())throw new CapacidadDeshabilitadaException();
            if(s.accion()==Accion.MEJORAR_FOTO)almacen().exigirPrevisionAntesDeClaim(id,actor,sesion);
            if(!presupuesto.iniciar(AutoridadMaterialIa.presupuesto(actual,s,autoridad.snapshot(actual)),actor,sesion)){
                repo.fase(actual,"INCIERTA","CLAIM_EXISTENTE");return false;
            }
            repo.fase(actual,"RECLAMADA",null);return true;
        }));
        if(!ejecutar)return consultarFuera(cargar(org,id),actor,sesion);
        Peticion peticion;
        try{
            contexto(actor,sesion);autoridad.revalidar(s,fuente,actor);AutoridadMaterialIa.exigirEvidencia(fuente);
            peticion=prepararTransferencia(o,s,fuente,proveedor,idProveedor,foto,actor,sesion);
        }catch(RuntimeException antesDeInvocar){
            // Sólo este coordinador recibió iniciar=true; todavía no entró en ejecutar ni cedió el intento.
            try{cancelarSinInvocacion(org,id,idProveedor,antesDeInvocar);}
            catch(RuntimeException sinConfirmar){
                antesDeInvocar.addSuppressed(sinConfirmar);
                try{incierta(org,id,"NO_INVOCACION_PENDIENTE_CONCILIACION");}
                catch(RuntimeException sinDiario){antesDeInvocar.addSuppressed(sinDiario);}
            }
            if("CANCELADA".equals(cargar(org,id).estado()))try{resolverSinArchivo(org,id);}catch(RuntimeException pendiente){antesDeInvocar.addSuppressed(pendiente);}
            throw antesDeInvocar;
        }
        Salida salida;var observacionHttp=new AtomicReference<ObservacionHttpIa>();
        try{
            try{
                // Desde esta llamada cualquier fallo puede haber causado procesamiento/cargo.
                salida=proveedor.ejecutar(peticion,observacionHttp::set);
            }catch(NoIniciadoComprobado noInicio){
                try{nueva.executeWithoutResult(tx->{bloquear(org,id);var actual=cargar(org,id);
                    presupuesto.cancelarNoProcesada(new ReferenciaInterna(org,id),noInicio.evidencia());
                    repo.fase(actual,"CANCELADA","NO_PROCESAMIENTO_COMPROBADO");
                });}catch(RuntimeException evidenciaNoConfirmada){incierta(org,id,"NO_INICIADO_PENDIENTE_CONCILIACION");throw evidenciaNoConfirmada;}
                resolverSinArchivo(org,id);
                return consultarFuera(cargar(org,id),actor,sesion);
            }catch(RuntimeException desconocido){
                incierta(org,id,"PROCESAMIENTO_SIN_CONFIRMAR");throw desconocido;
            }
            registrarResultadoVerificado(org,id,o.claveExterna(),salida);
        }finally{observarHttp(new ReferenciaInterna(org,id),null,observacionHttp.get());}
        return completarResultado(org,id,salida,controles,actor,sesion);
    }
    private Peticion prepararTransferencia(Operacion o,Solicitud s,Snapshot fuente,ProveedorMaterialIa proveedor,
            String idSeleccionado,byte[] foto,Actor actor,ContextoSesion sesion){
        var destino=new DestinoTratamiento(proveedor.identificador(),o.perfil(),o.versionPerfil());
        Peticion peticion;
        try{
            exigirIdentidadProveedor(destino.proveedor());
            if(!idSeleccionado.equals(destino.proveedor()))throw new ConflictoException("El destino del intento cambió antes de transferir.");
            if(!texto(fuente.instruccionPermitida()))throw new IllegalStateException("La fuente todavía no conserva una instrucción permitida para transferir.");
            exigirFueraSql();autoridad.fuente().exigirTransferencia(s,fuente,destino,actor,sesion);exigirFueraSql();
            var limite=s.accion()==Accion.MEJORAR_FOTO?almacen().limiteRecepcionParaInvocar(o.id(),actor):null;
            peticion=new Peticion(o.claveExterna(),s.accion(),fuente.datosPermitidos(),fuente.instruccionPermitida(),foto,limite);
        }catch(RuntimeException denegada){
            try{anotarDestino(o,destino,false,denegada.getClass().getName());}
            catch(RuntimeException sinEvidencia){denegada.addSuppressed(sinEvidencia);}
            throw denegada;
        }
        // Un fallo al comprometer esta evidencia tampoco permite llegar al proveedor.
        anotarDestino(o,destino,true,null);exigirFueraSql();return peticion;
    }
    private void anotarDestino(Operacion operacion,DestinoTratamiento destino,boolean verificado,String fallo){
        var hecho=new LinkedHashMap<String,Object>();hecho.put("origen","COORDINADOR_CORE");
        hecho.put("organizacion",operacion.organizacion());hecho.put("operacion",operacion.id());
        hecho.put("claveIntento",operacion.claveExterna());hecho.put("huellaSnapshot",operacion.huellaSnapshot());
        hecho.put("destino",destino);hecho.put("resultadoVerificacion",verificado?"VERIFICADO":"DENEGADO");
        hecho.put("fase","PREVIA_A_INVOCACION");hecho.put("comprobadaEn",Instant.now());hecho.put("tipoFallo",fallo);
        String contenido=json.objetoCanonico(hecho);
        nueva.executeWithoutResult(tx->{bloquear(operacion.organizacion(),operacion.id());var actual=cargar(operacion.organizacion(),operacion.id());
            if(!"RECLAMADA".equals(actual.estado())||actual.medicion()!=null)
                throw new ConflictoException("La verificación conserva el mismo intento reclamado sin resultado previo.");
            repo.evidencia(actual,verificado?"DESTINO_VERIFICADO":"DESTINO_DENEGADO",contenido);
        });
    }
    /** Callback interno verificable: no HTTP/herramienta. Conserva medición aun con SID/recurso revocados. */
    public void registrarResultadoVerificado(long org,UUID id,String claveExterna,Salida salida){
        fuera(()->{
            if(salida==null||salida.medicion()==null)throw new ReglaNegocioException("La operación conserva su medición verificable original.");
            String medicion=json.objetoCanonico(salida.medicion()),detalle=json.objetoCanonico(detalle(salida));
            nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
                if(!o.claveExterna().equals(claveExterna))throw new ConflictoException("El resultado técnico pertenece a otra operación.");
                if(o.medicion()!=null){
                    if(!o.medicion().equals(medicion)||!o.salida().equals(detalle))throw new ConflictoException("La operación ya conserva otro resultado técnico.");
                    return;
                }
                if(!Set.of("RECLAMADA","INCIERTA").contains(o.estado()))throw new ConflictoException("No hay una ejecución pendiente de resultado.");
                repo.medir(o,medicion,detalle);
            });
            nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
                if(!"MEDIDA".equals(o.estado()))return;
                presupuesto.registrarMediciones(new ReferenciaInterna(org,id),json.comoObjeto(o.medicion(),ResultadoFacturable.class));
            });
            var medida=cargar(org,id);var s=autoridad.solicitud(medida);
            // El tipo/destino se congeló antes de generar; conservar hechos no exige
            // volver a disponer del procesador de QC ni una autorización de transferencia.
            if(s.accion()==Accion.MEJORAR_FOTO&&salida.foto()==null){
                resolverSalidaSinBinario(org,id);return null;
            }
            if(salidaSinTexto(medida,json)){resolverSalidaNoUtilizable(org,id,AusenciaSalida.TEXTO);return null;}
            if(repo.ultimoHecho(org,id,"QC_CONFIG").isPresent())programarControl(medida,salida);
            else conciliarConocido(org,id);
            // Otra TX, sin pedir una sesión todavía vigente para conservar hechos ya causados.
            if(s.accion()==Accion.MEJORAR_FOTO){
                almacen().registrarGeneracion(org,id);
                try{almacen().recibirResultado(org,id,claveExterna,salida);}
                catch(RuntimeException fallo){falloResultado(org,id,"RECEPCION_NO_CONFIRMADA");throw fallo;}
            }
            return null;
        });
    }
    private void congelarDestinoControl(Operacion o,Facturable externo){
        var d=externo.destino(o.perfil(),o.versionPerfil());validarDestinoControl(d);
        String dato=json.objetoCanonico(d);var anterior=repo.ultimoHecho(o.organizacion(),o.id(),"QC_CONFIG");
        if(anterior.isPresent()){if(!anterior.get().equals(dato))throw new ConflictoException("El control conserva otro destino congelado.");}
        else repo.evidencia(o,"QC_CONFIG",dato);
    }
    private static void validarDestinoControl(DestinoTratamiento d){
        if(d==null||!texto(d.proveedor())||d.proveedor().length()>100||!texto(d.perfil())||!texto(d.versionPerfil()))throw new CapacidadDeshabilitadaException();
    }
    private void programarControl(Operacion o,Salida salida){
        var d=json.comoObjeto(repo.ultimoHecho(o.organizacion(),o.id(),"QC_CONFIG").orElseThrow(()->new ConflictoException("El intento no congeló su control facturable.")),DestinoTratamiento.class);
        validarDestinoControl(d);var ref=new ReferenciaInterna(o.organizacion(),o.id());
        String entrada=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("snapshot",o.huellaSnapshot(),"salida",detalle(salida),"destino",d)));
        var plan=new PlanControl(d,PresupuestoIaService.claveDeEtapa(ref,ETAPA_QC),entrada);String contenido=json.objetoCanonico(plan);
        nueva.executeWithoutResult(tx->{bloquear(o.organizacion(),o.id());var actual=cargar(o.organizacion(),o.id());
            var antes=repo.ultimoHecho(o.organizacion(),o.id(),"QC_PLAN");
            if(antes.isPresent()&&!antes.get().equals(contenido))throw new ConflictoException("El control conserva otra entrada.");
            if(antes.isEmpty())repo.evidencia(actual,"QC_PLAN",contenido);
            presupuesto.programarEtapa(ref,ETAPA_QC,d.proveedor(),entrada);
        });
    }
    private ResultadoFacturable conjuntoConocido(Operacion o){
        var gen=json.comoObjeto(o.medicion(),ResultadoFacturable.class);var q=repo.ultimoHecho(o.organizacion(),o.id(),"QC_RESULTADO");
        if(q.isEmpty())return gen;var control=json.comoObjeto(q.get(),RespuestaControl.class).resultado();
        if(control==null||control.medicion()==null||control.medicion().mediciones()==null||control.medicion().mediciones().isEmpty())throw new ReglaNegocioException("El control invocado conserva medición pendiente.");
        var todas=new ArrayList<>(gen.mediciones());todas.addAll(control.medicion().mediciones());return new ResultadoFacturable(todas);
    }
    private void conciliarConocido(long org,UUID id){
        nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
            var r=presupuesto.conciliar(new ReferenciaInterna(org,id),conjuntoConocido(o));
            if("LIQUIDADA".equals(r.estado())&&"MEDIDA".equals(o.estado()))repo.fase(o,"CONCILIADA",null);
        });
    }
    private Control evaluarControlFacturable(Operacion o,Salida salida,Facturable externo,Actor actor,ContextoSesion sesion){
        long org=o.organizacion();UUID id=o.id();var ref=new ReferenciaInterna(org,id);var s=autoridad.solicitud(o);var f=autoridad.snapshot(o);
        var plan=json.comoObjeto(repo.ultimoHecho(org,id,"QC_PLAN").orElseThrow(CapacidadDeshabilitadaException::new),PlanControl.class);
        var anterior=repo.ultimoHecho(org,id,"QC_RESULTADO");
        if(anterior.isPresent()){var r=json.comoObjeto(anterior.get(),RespuestaControl.class).resultado();cerrarControlMedido(org,id,r);return r.control();}
        if(repo.ultimoHecho(org,id,"QC_NO_INVOCADA").isPresent())throw new CapacidadDeshabilitadaException();
        if(repo.ultimoHecho(org,id,"QC_CLAIM").isPresent())throw new ProcesamientoIncierto();
        boolean reclamado;
        try{
            reclamado=Boolean.TRUE.equals(nueva.execute(tx->{bloquearEntrada(s,actor,sesion);var actual=cargar(org,id);
                if(repo.ultimoHecho(org,id,"QC_CLAIM").isPresent()||repo.ultimoHecho(org,id,"QC_NO_INVOCADA").isPresent())return false;
                if(!presupuesto.iniciarEtapa(AutoridadMaterialIa.presupuesto(actual,s,autoridad.snapshot(actual)),ETAPA_QC,actor,sesion))return false;
                repo.evidencia(actual,"QC_CLAIM",json.objetoCanonico(plan));return true;
            }));
        }catch(RuntimeException antes){acreditarControlNoInvocado(org,id,plan,antes,false);throw antes;}
        if(!reclamado)throw new ProcesamientoIncierto();
        PeticionControl peticion;
        try{
            contexto(actor,sesion);autoridad.revalidar(s,f,actor);AutoridadMaterialIa.exigirEvidencia(f);
            if(!plan.destino().equals(externo.destino(o.perfil(),o.versionPerfil())))throw new ConflictoException("El destino QC cambió antes de transferir.");
            exigirFueraSql();
            byte[] original=s.accion()==Accion.REDACTAR_FICHA?null:autoridad.fuente().fotoParaProcesar(s,f,actor,sesion);
            exigirFueraSql();peticion=new PeticionControl(s.accion(),f.datosPermitidos(),f.instruccionPermitida(),salida,plan.clave(),original);
            autoridad.fuente().exigirTransferencia(s,f,plan.destino(),actor,sesion);exigirFueraSql();
            nueva.executeWithoutResult(tx->{bloquear(org,id);repo.evidencia(cargar(org,id),"QC_DESTINO_VERIFICADO",json.objetoCanonico(plan));});
        }catch(RuntimeException antes){acreditarControlNoInvocado(org,id,plan,antes,true);throw antes;}
        EvaluacionFacturable r;var observacionHttp=new AtomicReference<ObservacionHttpIa>();
        try{
            try{exigirFueraSql();r=externo.evaluarFacturable(peticion,observacionHttp::set);}
            catch(NoIniciadoComprobado no){
                nueva.executeWithoutResult(tx->{bloquear(org,id);presupuesto.acreditarEtapaNoInvocada(ref,ETAPA_QC,no.evidencia());repo.evidencia(cargar(org,id),"QC_NO_INVOCADA",json.objetoCanonico(no.evidencia()));});
                conciliarConocido(org,id);throw no;
            }catch(RuntimeException incierto){controlIncierto(org,id);throw incierto;}
            // La respuesta original también se conserva si su Control es null/inválido.
            nueva.executeWithoutResult(tx->{bloquear(org,id);var actual=cargar(org,id);var previo=repo.ultimoHecho(org,id,"QC_RESULTADO");String dato=json.objetoCanonico(new RespuestaControl(r==null?"NO_RECIBIDA":"RECIBIDA",r));
                if(previo.isPresent()&&!previo.get().equals(dato))throw new ConflictoException("El control conserva otra respuesta.");
                if(previo.isEmpty())repo.evidencia(actual,"QC_RESULTADO",dato);
            });
            if(r==null||r.medicion()==null||r.medicion().mediciones()==null||r.medicion().mediciones().isEmpty()){controlIncierto(org,id);throw new ReglaNegocioException("El control invocado todavía no tiene medición verificable.");}
            cerrarControlMedido(org,id,r);return r.control();
        }finally{observarHttp(ref,ETAPA_QC,observacionHttp.get());}
    }
    private void cerrarControlMedido(long org,UUID id,EvaluacionFacturable r){
        if(r==null||r.medicion()==null)throw new ReglaNegocioException("Conserva la medición del control.");
        nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);presupuesto.registrarResultadoEtapa(new ReferenciaInterna(org,id),ETAPA_QC,conjuntoConocido(o));});
        conciliarConocido(org,id);
    }
    /** Ausencia en un doble/proveedor sin observación es desconocida, nunca cero llamadas. */
    private void observarHttp(ReferenciaInterna ref,String etapa,ObservacionHttpIa observacion){
        if(observacion==null)return;
        try{nueva.executeWithoutResult(tx->presupuesto.registrarObservacionHttp(ref,etapa,observacion));}
        catch(RuntimeException pendiente){org.slf4j.LoggerFactory.getLogger(MaterialIaServiceImpl.class)
                .warn("La observación HTTP IA conserva persistencia pendiente; no se repite el procesamiento.");}
    }
    private void controlIncierto(long org,UUID id){
        nueva.executeWithoutResult(tx->{bloquear(org,id);presupuesto.marcarEtapaIncierta(new ReferenciaInterna(org,id),ETAPA_QC);var o=cargar(org,id);repo.fase(o,"INCIERTA","CONTROL_FACTURABLE_INCIERTO");});
    }
    private void acreditarControlNoInvocado(long org,UUID id,PlanControl plan,RuntimeException causa,boolean exclusivo){
        nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
            if(repo.ultimoHecho(org,id,"QC_RESULTADO").isPresent()||repo.ultimoHecho(org,id,"QC_NO_INVOCADA").isPresent())return;
            if(!exclusivo&&repo.ultimoHecho(org,id,"QC_CLAIM").isPresent())throw new ConflictoException("Otra ejecución conserva el claim QC.");
            var datos=Map.of("origen","COORDINADOR_CORE","causa","NO_INVOCADO","clave",plan.clave(),"proveedor",plan.destino().proveedor(),"tipoFallo",causa.getClass().getName());
            String contenido=json.objetoCanonico(datos);long evento=repo.evidencia(o,"QC_NO_INVOCADA",contenido);
            var prueba=new ComprobacionNoProcesamiento(plan.clave(),plan.destino().proveedor(),plan.clave(),"MATERIAL-IA-QC-NO-INVOCADA:"+evento,Idempotencia.huellaExacta(contenido),repo.ahora());
            var ref=new ReferenciaInterna(org,id);
            if(exclusivo)presupuesto.acreditarEtapaNoInvocada(ref,ETAPA_QC,prueba);
            else if(!presupuesto.acreditarEtapaNoReclamada(ref,ETAPA_QC,prueba))
                throw new ConflictoException("El control conserva una ejecución que requiere su resultado original.");
        });
        conciliarConocido(org,id);
    }
    /** Generación medida sin foto: no acredita QC ni ausencia de IO por el estado genérico de fallo. */
    private void resolverSalidaSinBinario(long org,UUID id){
        resolverSalidaNoUtilizable(org,id,AusenciaSalida.FOTOGRAFIA);
        // Esta puerta vuelve a exigir ausencia de custodia, derivada e intento bajo sus fences O/J.
        resolverSinArchivo(org,id);
    }
    private enum AusenciaSalida { FOTOGRAFIA, TEXTO }
    /** Sólo el resultado medido exacto permite cerrar QC nunca reclamado; no un fallo genérico. */
    private void resolverSalidaNoUtilizable(long org,UUID id,AusenciaSalida ausencia){
        String hecho=ausencia==AusenciaSalida.FOTOGRAFIA?"SALIDA_SIN_BINARIO":"SALIDA_SIN_TEXTO";
        var o=cargar(org,id);if(!salidaAusente(o,ausencia))throw new ConflictoException("La generación conserva otro resultado.");
        var d=json.comoObjeto(o.salida(),DetalleSalida.class);
        if(repo.ultimoHecho(org,id,"QC_CONFIG").isPresent())programarControl(o,new Salida(d.texto(),d.contentType(),null,d.proceso(),d.versionProceso(),
                json.comoObjeto(o.medicion(),ResultadoFacturable.class)));
        nueva.executeWithoutResult(tx->{bloquear(org,id);var actual=cargar(org,id);
            if(!salidaAusente(actual,ausencia)||repo.ultimoHecho(org,id,"QC_CLAIM").isPresent()||repo.ultimoHecho(org,id,"QC_RESULTADO").isPresent())
                throw new ConflictoException("La operación conserva otra salida o un control que requiere recuperación.");
            var previo=repo.ultimoHecho(org,id,hecho);
            if(previo.isPresent()&&!previo.get().equals(actual.salida()))throw new ConflictoException("El antecedente conserva otro resultado.");
            if(previo.isEmpty())repo.evidencia(actual,hecho,actual.salida());
            if(!"FALLO_RESULTADO".equals(actual.estado()))repo.fase(actual,"FALLO_RESULTADO",hecho);
        });
        var plan=repo.ultimoHecho(org,id,"QC_PLAN");
        if(plan.isPresent())acreditarControlNoInvocado(org,id,json.comoObjeto(plan.get(),PlanControl.class),
                new ReglaNegocioException("La generación medida no devolvió contenido utilizable para su acción."),false);
        else conciliarConocido(org,id);
    }
    private boolean salidaAusente(Operacion o,AusenciaSalida ausencia){return ausencia==AusenciaSalida.FOTOGRAFIA?salidaSinBinario(o,json):salidaSinTexto(o,json);}
    static boolean salidaSinTexto(Operacion o,Documentos json){
        if(!Set.of("REDACTAR_FICHA","ANALIZAR_FOTO").contains(o.accion())||o.medicion()==null||o.salida()==null)return false;
        var d=json.comoObjeto(o.salida(),DetalleSalida.class);return !texto(d.texto())&&d.huellaFoto()==null;
    }
    static boolean salidaSinBinario(Operacion o,Documentos json){
        return "MEJORAR_FOTO".equals(o.accion())&&o.medicion()!=null&&o.salida()!=null
                &&json.comoObjeto(o.salida(),DetalleSalida.class).huellaFoto()==null;
    }
    /** Recupera el MISMO resultado verificable tras reinicio; nunca vuelve a llamar al procesador. */
    public Resultado recuperarResultadoDurable(long org,UUID id,Actor actor,ContextoSesion sesion){
        return fuera(()->{
            contexto(actor,sesion);var o=cargar(org,id);AutoridadMaterialIa.actor(o,actor);var s=autoridad.solicitud(o);
            if(Set.of("DISPONIBLE","OBSERVADA","RECHAZADA","CANCELADA").contains(o.estado()))return consultarFuera(o,actor,sesion);
            if(s.accion()!=Accion.MEJORAR_FOTO)throw new ReglaNegocioException("La recuperación binaria exige el mismo job de fotografía.");
            if(salidaSinBinario(o,json)){resolverSalidaSinBinario(org,id);return consultarFuera(cargar(org,id),actor,sesion);}
            Salida recibida=almacen().recuperarRecibido(org,id,o.claveExterna());
            return completarResultado(org,id,recibida,control(s.accion(),o.perfil(),o.versionPerfil()),actor,sesion);
        });
    }
    private Resultado completarResultado(long org,UUID id,Salida salida,ControlesMaterialIa controles,Actor actor,ContextoSesion sesion){
        var o=cargar(org,id);if(!Set.of("MEDIDA","CONCILIADA","CONTROLADA","FALLO_RESULTADO").contains(o.estado()))return consultarFuera(o,actor,sesion);
        var s=autoridad.solicitud(o);var f=autoridad.snapshot(o);Control qc;
        if(salidaSinBinario(o,json)){resolverSalidaSinBinario(org,id);return consultarFuera(cargar(org,id),actor,sesion);}
        if(salidaSinTexto(o,json)){resolverSalidaNoUtilizable(org,id,AusenciaSalida.TEXTO);return consultarFuera(cargar(org,id),actor,sesion);}
        // La salida original y su medición ya están comprometidas/conciliadas, aun si estos metadatos faltan.
        try{validarSalida(s,salida);}
        catch(RuntimeException fallo){falloResultado(org,id,"SALIDA_SIN_CONFIRMAR");throw fallo;}
        if(o.control()!=null){
            try{qc=json.comoObjeto(o.control(),Control.class);validarControl(s.accion(),qc);}
            catch(RuntimeException fallo){falloControl(org,id,o.control(),"CONTROL_INVALIDO",fallo);throw fallo;}
        }
        else{
            Control recibido;
            try{exigirFueraSql();recibido=controles instanceof Facturable externo?evaluarControlFacturable(o,salida,externo,actor,sesion):controles.evaluar(s,f,salida);}
            catch(RuntimeException fallo){falloControl(org,id,null,"CONTROL_SIN_CONFIRMAR",fallo);throw fallo;}
            try{validarControl(s.accion(),recibido);}
            catch(RuntimeException fallo){falloControl(org,id,recibido,"CONTROL_INVALIDO",fallo);throw fallo;}
            qc=recibido;
            nueva.executeWithoutResult(tx->{bloquear(org,id);var actual=cargar(org,id);
                if(!Set.of("CONCILIADA","FALLO_RESULTADO").contains(actual.estado())||actual.control()!=null)
                    throw new ConflictoException("El resultado ya fue controlado.");
                repo.controlar(actual,json.objetoCanonico(qc),qc.decision()==Decision.APROBADA?"CONTROLADA":qc.decision().name());
            });
        }
        if(qc.decision()!=Decision.APROBADA){resolverSinArchivo(org,id);return consultarFuera(cargar(org,id),actor,sesion);}
        UUID archivo=null;
        try{
            contexto(actor,sesion);autoridad.revalidar(s,f,actor);AutoridadMaterialIa.exigirEvidencia(f);
            if(s.accion()==Accion.MEJORAR_FOTO){
                exigirFueraSql();var guardado=almacen().guardar(s,f,salida,qc,actor,sesion);
                if(guardado==null||guardado.archivo()==null||!detalle(salida).huellaFoto().equals(guardado.huella()))
                    throw new IllegalStateException("La derivada todavía no tiene archivo y huella confirmados.");
                archivo=guardado.archivo();
            }
            UUID confirmado=archivo;
            nueva.executeWithoutResult(tx->{bloquear(org,id);var actual=cargar(org,id);
                if(!Set.of("CONTROLADA","FALLO_RESULTADO").contains(actual.estado())||actual.control()==null)
                    throw new ConflictoException("El material cambió durante su conservación.");
                repo.resultado(actual,json.objetoCanonico(new Entregado(salida.texto(),confirmado)));
            });
        }catch(RuntimeException fallo){falloResultado(org,id,"RESULTADO_PRIVADO_SIN_CONFIRMAR");throw fallo;}
        return consultarFuera(cargar(org,id),actor,sesion);
    }
    @Override public Resultado consultar(UUID id,Actor actor,ContextoSesion sesion){
        if(id==null)throw new ReglaNegocioException("Identifica la operación de material.");
        return fuera(()->{contexto(actor,sesion);return consultarFuera(cargar(actor.idOrganizacion(),id),actor,sesion);});
    }
    private Resultado consultarFuera(Operacion o,Actor actor,ContextoSesion sesion){
        contexto(actor,sesion);AutoridadMaterialIa.actor(o,actor);var s=autoridad.solicitud(o);var f=autoridad.snapshot(o);
        autoridad.exigirLecturaHistorica(s,f,actor);
        if("DISPONIBLE".equals(o.estado())){
            AutoridadMaterialIa.exigirEvidencia(f);
            var salida=json.comoObjeto(o.salida(),DetalleSalida.class);exigirProceso(salida.proceso(),salida.versionProceso());
            var qc=json.comoObjeto(o.control(),Control.class);validarControl(s.accion(),qc);
            if(qc.decision()!=Decision.APROBADA)throw new IllegalStateException("El material conserva un control que no autoriza su entrega.");
            var r=json.comoObjeto(o.resultado(),Entregado.class);
            validarEntrega(s,salida,r);
            if(s.accion()==Accion.MEJORAR_FOTO){
                if(!almacen().version(o.id(),actor).disponible())return new Resultado(o.id(),"RESULTADO_NO_DISPONIBLE",null,null,List.of("ORIGINAL_Y_GESTION_ORDINARIA_DISPONIBLES"));
                almacen().exigirLectura(s,f,r.archivo(),actor,sesion);
            }
            contexto(actor,sesion);autoridad.exigirLecturaHistorica(s,f,actor);
            return new Resultado(o.id(),"DISPONIBLE",r.texto(),r.archivo(),List.of());
        }
        String estado=switch(o.estado()){
            case "OBSERVADA","RECHAZADA" -> "REQUIERE_REVISION";
            case "FALLO_RESULTADO" -> "RESULTADO_NO_DISPONIBLE";
            case "CANCELADA" -> "NO_PROCESADA";
            case "INTENCION","RESERVADA" -> "PENDIENTE";
            default -> "EN_VERIFICACION";
        };
        return new Resultado(o.id(),estado,null,null,List.of("ORIGINAL_Y_GESTION_ORDINARIA_DISPONIBLES"));
    }
    private void incierta(long org,UUID id,String motivo){nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
        presupuesto.marcarIncierta(new ReferenciaInterna(org,id));
        if(Set.of("RECLAMADA","INCIERTA").contains(o.estado()))repo.fase(o,"INCIERTA",motivo);
    });}
    /** Prueba local exclusiva de NO invocación; no se llama desde callbacks ni después de entrar al proveedor. */
    private void cancelarSinInvocacion(long org,UUID id,String proveedor,RuntimeException causa){
        nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
            if(!"RECLAMADA".equals(o.estado())||o.medicion()!=null)
                throw new ConflictoException("El intento requiere conservar y conciliar su resultado original.");
            var ref=new ReferenciaInterna(org,id);String clave=PresupuestoIaService.claveDeIntento(ref);
            if(!clave.equals(o.claveExterna()))throw new ConflictoException("La comprobación pertenece a otro intento.");
            Instant fecha=Instant.now();String contenido=json.objetoCanonico(Map.of(
                    "origen","COORDINADOR_CORE","causa","NO_INVOCADO","organizacion",org,"operacion",id,
                    "claveIntento",clave,"proveedor",proveedor,"perfil",o.perfil(),"versionPerfil",o.versionPerfil(),
                    "comprobadaEn",fecha,"falloValidacion",causa.getClass().getName()));
            long evento=repo.evidencia(o,"NO_INVOCACION_PREVIA",contenido);
            presupuesto.cancelarNoProcesada(ref,new ComprobacionNoProcesamiento(clave,proveedor,clave,
                    "material_ia_evento:"+evento,Idempotencia.huellaExacta(contenido),fecha));
            repo.fase(o,"CANCELADA","NO_INVOCACION_COMPROBADA");
        });
    }
    private void falloControl(long org,UUID id,Object control,String motivo,RuntimeException fallo){
        var evidencia=new LinkedHashMap<String,Object>();evidencia.put("controlRecibido",control);
        // NON_NULL omite el valor nulo: conservar explícitamente el hecho de que no se recibió control.
        evidencia.put("estadoControl",control==null?"NO_RECIBIDO":"RECIBIDO");
        evidencia.put("tipoFallo",fallo.getClass().getName());
        String contenido=json.objetoCanonico(evidencia);
        nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
            repo.evidencia(o,motivo,contenido);
            if(Set.of("MEDIDA","CONCILIADA","CONTROLADA").contains(o.estado()))repo.fase(o,"FALLO_RESULTADO",motivo);
        });
    }
    private void falloResultado(long org,UUID id,String motivo){nueva.executeWithoutResult(tx->{bloquear(org,id);var o=cargar(org,id);
        if(Set.of("MEDIDA","CONCILIADA","CONTROLADA").contains(o.estado()))repo.fase(o,"FALLO_RESULTADO",motivo);
    });}
    private Operacion cargar(long org,UUID id){return repo.obtener(org,id).orElseThrow(()->new NoEncontradoException("Material IA"));}
    private void bloquearEntrada(Solicitud s,Actor actor,ContextoSesion sesion){
        contexto.iniciarConsumo(actor,sesion);
        if(s.accion()==Accion.MEJORAR_FOTO)almacen().bloquearOriginal(s.fotoOriginal(),actor);
        bloquear(actor.idOrganizacion(),s.operacion());
    }
    private void resolverSinArchivo(long org,UUID id){var o=cargar(org,id);if("MEJORAR_FOTO".equals(o.accion()))almacen().resolverSinArchivo(org,id,o.estado());}
    private void bloquear(long org,UUID id){String h=Idempotencia.huellaExacta("BROX-MATERIAL-IA-LOCK\u0000"+org+"\u0000"+id);repo.bloquear(Long.parseUnsignedLong(h.substring(0,16),16));}
    private void contexto(Actor a,ContextoSesion s){nueva.executeWithoutResult(tx->contexto.iniciarConsumo(a,s));}
    private String hashPeticion(Solicitud s,Actor a){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",s,"actor",a)));}
    private void misma(Operacion o,Solicitud s,Actor a){AutoridadMaterialIa.actor(o,a);
        if(!o.peticion().equals(hashPeticion(s,a)))throw new ConflictoException("La identidad ya corresponde a otra solicitud de material.");}
    private ProveedorMaterialIa proveedor(Accion a,String p,String v){var lista=proveedores.stream().filter(x->x.atiende(a,p,v)).toList();
        if(lista.size()!=1)throw new CapacidadDeshabilitadaException();return lista.getFirst();}
    private ControlesMaterialIa control(Accion a,String p,String v){var lista=controles.stream().filter(x->x.atiende(a,p,v)).toList();
        if(lista.size()!=1)throw new CapacidadDeshabilitadaException();return lista.getFirst();}
    private ResultadosMaterialIa almacen(){if(resultados.size()!=1)throw new CapacidadDeshabilitadaException();return resultados.getFirst();}
    private <T>T fuera(Supplier<T> trabajo){exigirFueraSql();return trabajo.get();}
    private static void exigirFueraSql(){if(TransactionSynchronizationManager.isActualTransactionActive())
        throw new IllegalStateException("El material IA requiere una entrada sin transacción SQL activa.");}
    static DetalleSalida detalle(Salida s){return new DetalleSalida(s.texto(),s.contentType(),s.foto()==null?null:
            com.controllocal.service.documentos.EvaluacionTecnicaFoto.huella(s.foto()),s.proceso(),s.versionProceso());}
    private static void validar(Solicitud s,boolean version){
        if(s==null||s.operacion()==null||s.accion()==null||s.recurso()==null||s.recurso().tipo()==null||!Set.of("PROPIEDAD","ENCARGO").contains(s.recurso().tipo())
                ||s.recurso().referencia()==null||!s.recurso().referencia().matches("[1-9][0-9]*")
                ||version&&(s.versionOrigen()==null||s.versionOrigen().isBlank()))
            throw new ReglaNegocioException("Identifica el recurso, la actuación inmobiliaria y su versión de origen.");
        if(s.accion()!=Accion.REDACTAR_FICHA&&(!"PROPIEDAD".equals(s.recurso().tipo())||s.fotoOriginal()==null)
                ||s.accion()==Accion.REDACTAR_FICHA&&s.fotoOriginal()!=null)
            throw new ReglaNegocioException("El análisis o mejora necesita la fotografía original del inmueble autorizado.");
    }
    private static void validarFuente(Snapshot f){if(f==null||f.versionOrigen()==null||f.versionOrigen().isBlank()
            ||f.huellaOrigen()==null||!f.huellaOrigen().matches("[0-9a-f]{64}"))throw new IllegalStateException("La fuente debe conservar su versión y huella verificables.");}
    static void validarControl(Accion accion,Control c){
        if(c==null||c.decision()==null||!texto(c.version())||c.evaluadaEn()==null||c.evaluadaEn().isAfter(Instant.now())||c.senales()==null)
            throw new IllegalStateException("Todo resultado necesita un control automático identificado y fechado por Core.");
        var vistos=EnumSet.noneOf(Aspecto.class);
        for(var senal:c.senales()){
            if(senal==null||senal.aspecto()==null||senal.estado()==null||!vistos.add(senal.aspecto())||!texto(senal.observacion())
                    ||senal.estado()!=EstadoSenal.NO_EVALUABLE&&!texto(senal.referenciaEvidencia()))
                throw new IllegalStateException("El control necesita señales únicas con observación y evidencia por aspecto.");
            boolean noAplica=accion==Accion.REDACTAR_FICHA&&senal.aspecto()==Aspecto.CALIDAD_VISUAL
                    ||accion==Accion.ANALIZAR_FOTO&&senal.aspecto()==Aspecto.PERDIDA_RELEVANTE;
            if(senal.estado()==EstadoSenal.NO_APLICA&&!noAplica
                    ||c.decision()==Decision.APROBADA&&(senal.estado()==EstadoSenal.NO_EVALUABLE||senal.hallazgoBloqueante()))
                throw new IllegalStateException("La aprobación automática necesita evidencia evaluable y ningún hallazgo bloqueante.");
        }
        if(!vistos.equals(EnumSet.allOf(Aspecto.class)))throw new IllegalStateException("El control conserva los cinco aspectos de calidad y fidelidad.");
    }
    static void validarSalida(Solicitud s,Salida r){
        if(r==null)throw new IllegalStateException("El resultado original todavía no está confirmado.");
        exigirProceso(r.proceso(),r.versionProceso());
        boolean foto=r.foto()!=null&&r.foto().length>0;
        validarContenido(s.accion(),foto,r.contentType(),r.texto());
    }
    static void validarEntrega(Solicitud s,DetalleSalida original,Entregado resultado){
        if(resultado==null)throw new IllegalStateException("El resultado privado todavía no está confirmado.");
        boolean foto=original.huellaFoto()!=null;
        if(foto&&!original.huellaFoto().matches("[0-9a-f]{64}"))throw new IllegalStateException("La derivada no conserva una huella verificable.");
        validarContenido(s.accion(),foto,original.contentType(),resultado.texto());
        if(!Objects.equals(original.texto(),resultado.texto())
                ||(s.accion()==Accion.MEJORAR_FOTO?resultado.archivo()==null:resultado.archivo()!=null))
            throw new IllegalStateException("La entrega no corresponde al resultado original de la actuación.");
    }
    static void validarContenido(Accion accion,boolean foto,String contentType,String texto){
        if(accion==Accion.MEJORAR_FOTO?(!foto||contentType==null||!Set.of("image/png","image/jpeg").contains(contentType)):
                (foto||!texto(texto)))throw new IllegalStateException("El resultado no corresponde a la actuación inmobiliaria solicitada.");
    }
    static void exigirProceso(String proceso,String version){if(!texto(proceso)||!texto(version))
        throw new IllegalStateException("La salida necesita su proceso y versión reales.");}
    private static boolean texto(String valor){return valor!=null&&!valor.isBlank();}
    private static void exigirIdentidadProveedor(String id){if(!texto(id)||!id.equals(id.trim())||id.length()>100)
        throw new IllegalStateException("El adaptador necesita una identidad privada estable y verificable.");}
}
