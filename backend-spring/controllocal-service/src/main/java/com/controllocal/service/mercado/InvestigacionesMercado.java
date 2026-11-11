package com.controllocal.service.mercado;

import com.controllocal.persistence.presupuesto.TrabajoManualB0Repository;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.InvestigacionesMercadoRepository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.mercado.ContratoInvestigacionManual.*;
import com.controllocal.service.mercado.DatosInvestigacionMercado.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.presupuesto.PresupuestoIaService;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.controllocal.domain.presupuesto.PrecisionCapacidad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** CAT-R24/R25, ampliación4.5/6.2: cola manual Core; ninguna navegación o llamada de IA automática. */
@Service
public class InvestigacionesMercado {
    private final InvestigacionesMercadoRepository repo;
    private final TrabajoManualB0Repository reglas;
    private final com.controllocal.persistence.presupuesto.PresupuestoIaRepository fondos;
    private final PresupuestoIaService presupuesto;
    private final AutoridadInvestigacionMercado autoridad;
    private final ContextosDeActividad contextos;
    private final AutoridadDePropiedad propiedades;
    private final AtribucionesPlataforma atribuciones;
    private final com.controllocal.persistence.presupuesto.AtribucionesPlataformaRepository concesiones;
    private final DistritoRepository distritos;
    private final Documentos json;
    private final boolean habilitada;
    public InvestigacionesMercado(InvestigacionesMercadoRepository repo,TrabajoManualB0Repository reglas,com.controllocal.persistence.presupuesto.PresupuestoIaRepository fondos,PresupuestoIaService presupuesto,AutoridadInvestigacionMercado autoridad,
            ContextosDeActividad contextos,AutoridadDePropiedad propiedades,AtribucionesPlataforma atribuciones,com.controllocal.persistence.presupuesto.AtribucionesPlataformaRepository concesiones,DistritoRepository distritos,
            Documentos json,@Value("${controllocal.investigacion.manual.habilitada:false}") boolean habilitada){
        this.repo=repo;this.reglas=reglas;this.fondos=fondos;this.presupuesto=presupuesto;this.autoridad=autoridad;this.contextos=contextos;this.propiedades=propiedades;
        this.atribuciones=atribuciones;this.concesiones=concesiones;this.distritos=distritos;this.json=json;this.habilitada=habilitada;
    }
    @Transactional public void configurar(Configuracion d,UUID decision,SesionPlataforma sesion){
        validar(d);var a=plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.CONFIGURAR_MERCADO,Campo.CONFIGURACION);
        String contenido=json.objetoCanonico(d),h=Idempotencia.huellaExacta(contenido);
        atribuciones.acreditar(decision,"CONFIGURAR_MERCADO_I0",h,a.cuenta(),sesion);
        reglas.regla(d.regla(),d.versionRegla(),true).orElseThrow(CapacidadDeshabilitadaException::new);
        repo.guardarPolitica(new Politica(d.id(),d.version(),contenido,d.regla(),d.versionRegla(),decision,a.cuenta(),d.evidencia(),h,d.desde(),d.hasta(),d.habilitada(),null));
        if(!h.equals(repo.politica(d.id(),d.version(),true).orElseThrow().huella()))throw new ConflictoException("La política conserva otra configuración aprobada.");
    }
    @Transactional public Configuracion consultarConfiguracion(UUID id,String version,SesionPlataforma sesion){
        plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.CONSULTAR_TRABAJO,Campo.CONFIGURACION);
        return json.comoObjeto(repo.politica(id,version,false).orElseThrow(()->new NoEncontradoException("Política")).configuracion(),Configuracion.class);
    }
    @Transactional public void retirarPolitica(UUID id,String version,RetiroPolitica d,SesionPlataforma sesion){
        if(id==null||!texto(version,80)||d==null||d.decision()==null||!texto(d.motivo(),2000))throw error("Identifica la política, decisión acreditada y motivo de retiro.");
        var a=plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.CONFIGURAR_MERCADO,Campo.CONFIGURACION);
        var h=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("politica",id,"version",version,"motivo",d.motivo())));
        atribuciones.acreditar(d.decision(),"RETIRAR_POLITICA_MERCADO_I0",h,a.cuenta(),sesion);
        if(repo.politica(id,version,false).isEmpty())throw new NoEncontradoException("Política");
        if(!repo.retirarPolitica(id,version,d.decision(),d.motivo()))throw new ConflictoException("La política conserva otra decisión de retiro.");
    }
    /** Preparación determinista; la lectura de la base no reserva créditos ni crea un trabajo. */
    @Transactional public Propuesta preparar(Preparar d,Actor actor,ContextoSesion sesion){
        contextos.iniciarOperador(actor,sesion);return propuesta(d,actor);
    }
    @Transactional public EstadoTrabajo solicitar(Aceptacion d,Actor actor,ContextoSesion sesion){
        contextos.iniciarOperador(actor,sesion);
        if(d==null||d.datos()==null||d.datos().solicitud()==null)throw error("Identifica la solicitud estable y el máximo aceptado.");
        if(fondos.bloquearOrganizacion(actor.idOrganizacion())==null)throw new AccesoNoAutorizadoException();
        repo.bloquearSolicitud(actor.idOrganizacion(),d.datos().solicitud());
        var anterior=repo.trabajo(actor.idOrganizacion(),d.datos().solicitud(),true);
        if(anterior.isPresent()){
            var t=anterior.get();exigirPropio(t,actor);
            if(!t.huellaSolicitud().equals(d.huellaAceptada())||d.maximoAceptado()==null||t.maximo().compareTo(d.maximoAceptado())!=0)throw new ConflictoException("La solicitud conserva su alcance y aceptación originales.");
            return estado(t,true);
        }
        var p=propuesta(d.datos(),actor);
        if(d.datos().plazoComunicado()==null||!p.huella().equals(d.huellaAceptada())||d.maximoAceptado()==null||p.maximo().compareTo(d.maximoAceptado())!=0)throw new ConflictoException("Revisa el plan, el plazo y el máximo comercial antes de comenzar.");
        if(!habilitada)throw new CapacidadDeshabilitadaException();
        var config=configuracion(d.datos().politica(),d.datos().versionPolitica());
        Instant ahora=repo.ahora();String origen=origen(p.datos(),p.plan(),actor);
        long id=repo.insertar(p.datos().solicitud(),actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),p.datos().propiedad(),null,
            config.id(),config.version(),p.huella(),origen,json.objetoCanonico(config.segmento()),json.objetoCanonico(p.plan()),BigDecimal.valueOf(p.plan().rondas()),p.maximo(),ahora);
        var s=new SolicitudManual(p.datos().solicitud(),config.regla(),config.versionRegla(),recurso(p.datos().solicitud(),origen),p.huella(),BigDecimal.valueOf(p.plan().rondas()),p.maximo());
        presupuesto.admitirManual(s,actor,sesion);
        repo.evento(id,p.datos().solicitud(),p.huella(),"SOLICITADA",0,null,actor.idOrganizacion(),actor.idRolOperativo(),null,json.objetoCanonico(Map.of("solicitud",p.datos().solicitud(),"politica",config.id(),"version",config.version())));
        return estado(repo.trabajo(actor.idOrganizacion(),p.datos().solicitud(),false).orElseThrow(),true);
    }
    @Transactional public EstadoTrabajo consultar(UUID solicitud,Actor actor,ContextoSesion sesion){
        contextos.iniciarOperador(actor,sesion);var t=repo.trabajo(actor.idOrganizacion(),solicitud,false).orElseThrow(()->new NoEncontradoException("Investigación"));exigirPropio(t,actor);return estado(t,true);
    }
    @Transactional public List<CabeceraTrabajo> cola(long despues,int cantidad,SesionPlataforma sesion){
        plataforma(sesion,Atribucion.INVESTIGACION_PLATFORM,Accion.CONSULTAR_TRABAJO,Campo.METADATOS_TRABAJO);
        if(despues<0||cantidad<1||cantidad>100)throw error("La página de trabajo no es válida.");
        return cabeceras(despues,cantidad,Cola.EJECUCION);
    }
    @Transactional public List<CabeceraTrabajo> colaRevision(long despues,int cantidad,SesionPlataforma sesion){
        plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.CONSULTAR_TRABAJO,Campo.METADATOS_TRABAJO);return cabeceras(despues,cantidad,Cola.REVISION);
    }
    @Transactional public List<CabeceraTrabajo> colaFinanciera(long despues,int cantidad,SesionPlataforma sesion){
        plataforma(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.CONSULTAR_TRABAJO,Campo.METADATOS_TRABAJO);return cabeceras(despues,cantidad,Cola.FINANZAS);
    }
    private List<CabeceraTrabajo> cabeceras(long despues,int cantidad,Cola cola){
        if(despues<0||cantidad<1||cantidad>100)throw error("La página de trabajo no es válida.");
        return repo.cola(despues,cantidad,cola).stream().map(c->new CabeceraTrabajo(c.id(),c.estado(),c.version(),c.creada(),c.plazo())).toList();
    }
    @Transactional public TrabajoFinanciero consultarFinanciero(long id,SesionPlataforma sesion){
        plataforma(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.CONSULTAR_TRABAJO,Campo.METADATOS_TRABAJO,Campo.LEDGER);
        var t=repo.trabajoPlataforma(id,false).orElseThrow(()->new NoEncontradoException("Trabajo"));var p=json.comoObjeto(t.plan(),Plan.class);
        var r=reglas.regla(UUID.fromString(p.regla()),p.versionRegla(),false).orElseThrow();
        return new TrabajoFinanciero(cabecera(t),t.organizacion(),t.solicitud(),r.id(),r.version(),t.maximo(),t.rondas(),t.acreditadas(),r.creditosPorRonda(),t.liquidada(),t.rondasLiquidadas());
    }
    /** Resultado privado reducido y revisado; no entrega informes internos, consultas o datos financieros. */
    @Transactional public ResultadoPrivado resultado(UUID solicitud,Actor actor,ContextoSesion sesion){
        contextos.iniciarOperador(actor,sesion);var t=repo.trabajo(actor.idOrganizacion(),solicitud,false).orElseThrow(()->new NoEncontradoException("Investigación"));exigirPropio(t,actor);
        if(!Set.of("REVISADA","PUBLICADA").contains(t.estado()))return new ResultadoPrivado(estado(t,true),"PENDIENTE_DE_RESULTADO_REVISADO",null);
        var c=configuracion(t.politica(),t.versionPolitica());var rs=rondas(t);
        var referencias=rs.stream().filter(r->r.cierre()==Cierre.COMPLETA).flatMap(r->r.referencias().stream()).filter(Referencia::incluidaEnCalculo).toList();
        var descripcion=CalculoPrecioMercado.describirUnitarios(contexto(c.segmento()),referencias.stream().map(Referencia::comparable).toList(),c.normalizacion());
        var limites=new ArrayList<String>();limites.add("Referencia preliminar del segmento; completar datos críticos mejora la precisión. No es tasación ni precio autorizado del Encargo.");
        limites.addAll(descripcion.exclusiones().stream().map(CalculoPrecioMercado.Exclusion::causa).distinct().toList());
        var resultado=rs.stream().anyMatch(r->r.cierre()==Cierre.INCIERTA)?"EJECUCION_INCIERTA":rs.stream().anyMatch(r->r.resultado()==Resultado.INCIDENCIA_TECNICA)?"INCIDENCIA_TECNICA":descripcion.promedio()==null?"SIN_REFERENCIA_NUMERICA":"REFERENCIA_PRELIMINAR";
        String etiqueta=descripcion.promedio()==null?"BASE_INSUFICIENTE":descripcion.comparables().size()==1?"REFERENCIA_UNICA":"REFERENCIA_DEL_SEGMENTO";
        var corte=rs.stream().map(r->r.revision().revisadaEn()).max(Comparator.naturalOrder()).orElse(null);
        var hasta=corte==null?null:corte.plus(c.vigenciaReferencia());if(hasta!=null&&hasta.isAfter(c.hasta()))hasta=c.hasta();
        boolean caducada=hasta!=null&&!repo.ahora().isBefore(hasta);
        if(caducada&&!c.permiteReferenciaHistorica())return new ResultadoPrivado(estado(t,true),"REFERENCIA_CADUCADA",null);
        if(caducada){etiqueta="REFERENCIA_CADUCADA";resultado="REFERENCIA_CADUCADA";limites.add("Conservada con permiso como antecedente, no como precio actual.");}
        var referencia=new ReferenciaOrientativa(t.revision(),c.segmento(),corte,hasta,etiqueta,descripcion.comparables().size(),descripcion.promedio(),"Entrega privada revisada; "+c.segmento().naturaleza(),limites);
        return new ResultadoPrivado(estado(t,true),resultado,referencia);
    }
    @Transactional public TrabajoOperador consultarOperador(long id,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.INVESTIGACION_PLATFORM,Accion.CONSULTAR_TRABAJO,Campo.METADATOS_TRABAJO,Campo.CONTEXTO_MINIMO,Campo.EVIDENCIA_PUBLICA);
        var t=repo.trabajoPlataforma(id,false).orElseThrow(()->new NoEncontradoException("Trabajo"));
        if(t.operador()==null||t.operador()!=a.cuenta())throw new AccesoNoAutorizadoException();
        boolean vigente=autorizacionDeBusquedaActual(t);
        return new TrabajoOperador(cabecera(t),json.comoObjeto(t.plan(),Plan.class),t.contexto(),rondas(t),vigente,vigente?null:"No iniciar otra busqueda; solo conservar hechos e incidencias para revision.");
    }
    @Transactional public TrabajoRevision consultarRevision(long id,SesionPlataforma sesion){
        plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.CONSULTAR_TRABAJO,Campo.METADATOS_TRABAJO,Campo.EVIDENCIA_PUBLICA);
        var t=repo.trabajoPlataforma(id,false).orElseThrow(()->new NoEncontradoException("Trabajo"));
        return new TrabajoRevision(cabecera(t),t.politica(),t.versionPolitica(),rondas(t));
    }
    /** Presupuesto corporativo aprobado; nunca se toma una bolsa comercial ni se inventa un solicitante. */
    @Transactional public CabeceraTrabajo mantenimiento(Preparar d,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.CONFIGURAR_MERCADO,Campo.CONFIGURACION,Campo.METADATOS_TRABAJO);
        if(d==null||d.solicitud()==null||d.propiedad()!=null||!texto(d.carencia(),2000)||d.rondas()<1)throw error("Delimita el mantenimiento corporativo sin una cartera privada.");
        repo.bloquearSolicitud(null,d.solicitud());
        var anterior=repo.trabajo(null,d.solicitud(),true);
        String h=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("datos",d,"cuenta",a.cuenta())));
        if(anterior.isPresent()){if(!h.equals(anterior.get().huellaSolicitud()))throw cambio();return cabecera(anterior.get());}
        var c=configuracion(d.politica(),d.versionPolitica());
        if(!habilitada)throw new CapacidadDeshabilitadaException();
        var r=reglas.regla(c.regla(),c.versionRegla(),true).orElseThrow(CapacidadDeshabilitadaException::new);var ahora=repo.ahora();
        if(!"MANTENIMIENTO_BROX".equals(r.financiacion())||!r.habilitada()||ahora.isBefore(r.desde())||!ahora.isBefore(r.hasta()))throw new CapacidadDeshabilitadaException();
        if(d.rondas()>c.maximoRondas()||r.cota().subtract(r.reservadas()).subtract(r.realizadas()).compareTo(BigDecimal.valueOf(d.rondas()))<0)throw error("No hay capacidad operativa disponible para las rondas del mantenimiento.");
        var plazo=d.plazoComunicado()==null?ahora.plus(c.plazoEntrega()):d.plazoComunicado();
        if(!plazo.isAfter(ahora)||plazo.isAfter(ahora.plus(c.plazoEntrega())))throw error("El plazo comunicado no corresponde al alcance aprobado.");
        var base=repo.ultima(segmento(c.segmento()),ahora).map(v->new BaseConsultada(v.id().toString(),json.comoObjeto(v.proyeccion(),CorpusPublicado.class).corte(),v.hasta(),d.carencia())).orElse(null);
        var p=new Plan(c.version(),json.objetoCanonico(c.segmento()),d.carencia(),base,c.fuentes(),c.parada(),c.limiteActivoPorRonda(),c.etapas(),d.rondas(),"Entrega estructurada y revisión competente",plazo,c.regla().toString(),c.versionRegla());ContratoInvestigacionManual.validar(p);
        long id=repo.insertar(d.solicitud(),null,null,null,null,a.cuenta(),c.id(),c.version(),h,h,json.objetoCanonico(c.segmento()),json.objetoCanonico(p),BigDecimal.valueOf(d.rondas()),null,ahora);
        repo.evento(id,d.solicitud(),h,"MANTENIMIENTO_SOLICITADO",0,a.cuenta(),null,null,a.concesion(),json.objetoCanonico(Map.of("politica",c.id(),"version",c.version())));
        return cabecera(repo.trabajoPlataforma(id,false).orElseThrow());
    }
    @Transactional public CabeceraTrabajo tomar(long id,Comando d,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.INVESTIGACION_PLATFORM,Accion.TOMAR_TRABAJO,Campo.METADATOS_TRABAJO,Campo.CONTEXTO_MINIMO);
        validar(d);var t=bloquear(id);String h=comando("TOMAR",d,a);
        if(replay(t,d,h))return cabecera(t);
        exigirVersion(t,d);configuracion(t.politica(),t.versionPolitica());
        if(!habilitada)throw new CapacidadDeshabilitadaException();
        if(!Set.of("PENDIENTE","ASIGNADA","OBSERVADA","RECHAZADA","INCIDENCIA").contains(t.estado()))throw new ConflictoException("El trabajo no está pendiente de ejecución o corrección.");
        if(t.operador()!=null&&t.operador()!=a.cuenta()){
            var c=concesiones.bloquear(t.concesionOperador()).orElseThrow(AccesoNoAutorizadoException::new);var ahora=repo.ahora();
            if(c.revocada()==null&&!ahora.isBefore(c.desde())&&ahora.isBefore(c.hasta()))throw new ConflictoException("El trabajo conserva un operador autorizado.");
        }
        if(t.organizacion()!=null&&t.liquidada()==null){
            var c=json.comoObjeto(repo.politica(t.politica(),t.versionPolitica(),true).orElseThrow().configuracion(),Configuracion.class);
            var actor=solicitante(t);
            presupuesto.iniciarManual(new SolicitudManual(t.solicitud(),c.regla(),c.versionRegla(),recurso(t.solicitud(),t.huellaRecurso()),t.huellaSolicitud(),BigDecimal.valueOf(t.rondas().longValueExact()),t.maximo()),actor);
        }
        if(!repo.asignar(t.id(),t.version(),a.cuenta(),a.concesion()))throw cambio();
        evento(t,d,h,"ASIGNADA",a,Map.of("operador",a.cuenta(),"antecedenteVersion",t.version()));
        return cabecera(repo.trabajoPlataforma(id,false).orElseThrow());
    }
    @Transactional public CabeceraTrabajo entregar(long id,Entrega d,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.INVESTIGACION_PLATFORM,Accion.ENTREGAR_EVIDENCIA,Campo.METADATOS_TRABAJO,Campo.EVIDENCIA_PUBLICA);
        if(d==null)throw error("Falta la entrega.");validar(d.comando());var t=bloquear(id);String h=comando("ENTREGAR",d,a);
        if(replay(t,d.comando(),h))return cabecera(t);
        exigirVersion(t,d.comando());var registro=politicaDeRegistro(t);var politica=json.comoObjeto(registro.configuracion(),Configuracion.class);
        if(t.operador()==null||t.operador()!=a.cuenta()||!Set.of("ASIGNADA","OBSERVADA","RECHAZADA","INCIDENCIA").contains(t.estado()))throw new AccesoNoAutorizadoException();
        ContratoInvestigacionManual.examinarEntrega(json.comoObjeto(t.plan(),Plan.class),d.rondas(),repo.ahora());
        validarHechosPrevios(d.rondas(),registro);
        if(d.rondas().stream().anyMatch(r->r.iniciadaEn().isBefore(t.creada())))throw error("La ejecución conserva la fecha del trabajo autorizado; no acredita búsquedas anteriores como rondas nuevas.");
        if(t.entrega()!=null){var anteriores=rondas(t);for(var r:d.rondas())for(var anterior:anteriores)if(r.numero()==anterior.numero()&&!r.identidad().equals(anterior.identidad()))throw new ConflictoException("Una corrección conserva la identidad de la ronda; no crea otra unidad.");}
        if(d.rondas().stream().flatMap(r->r.referencias().stream()).filter(Referencia::incluidaEnCalculo).anyMatch(r->!contexto(politica.segmento()).equals(r.comparable().contexto())||!politica.segmento().composicion().equals(r.comparable().composicionEquivalente())))throw error("La evidencia incluida no amplia el alcance aceptado; conserva la discrepancia como exclusion.");
        String contenido=json.lista(d.rondas()),firma=Idempotencia.huellaExacta(contenido);
        if(!repo.entregar(id,t.version(),contenido,firma))throw cambio();
        evento(t,d.comando(),h,"ENTREGADA",a,Map.of("entrega",d.rondas(),"huella",firma));
        return cabecera(repo.trabajoPlataforma(id,false).orElseThrow());
    }
    /** Finanzas liquida la prueba acreditada, no lo que el operador declare ni lo que se publique. */
    @Transactional public CabeceraTrabajo liquidar(long id,Comando d,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.CONCILIAR_TRABAJO,Campo.LEDGER);
        validar(d);var t=bloquear(id);String h=comando("LIQUIDAR",d,a);if(replay(t,d,h))return cabecera(t);
        exigirVersion(t,d);
        if(t.liquidada()!=null)throw new ConflictoException("El trabajo conserva su liquidación anterior; una corrección no genera otro cargo.");
        if(!Set.of("REVISADA","PUBLICADA").contains(t.estado())||t.acreditadas()==null||t.revision()==null)throw new ConflictoException("Falta revisión competente del trabajo.");
        var rs=rondas(t);if(rs.stream().anyMatch(r->r.cierre()==Cierre.INCIERTA))throw new ConflictoException("La incertidumbre conserva la operación y reserva para su aclaración.");
        var prueba=ContratoInvestigacionManual.comprobar(json.comoObjeto(t.plan(),Plan.class),rs,repo.ahora());
        if(BigDecimal.valueOf(prueba.rondasCompletas().size()).compareTo(t.acreditadas())!=0)throw cambio();
        if(t.organizacion()!=null)presupuesto.conciliarManual(new ConciliacionManual(t.organizacion(),t.solicitud(),t.acreditadas(),AutoridadInvestigacionMercado.revision(t.revision())),sesion);
        if(!repo.liquidar(id,t.version(),repo.ahora()))throw cambio();
        evento(t,d,h,"LIQUIDADA",a,Map.of("revision",t.revision(),"rondasAcreditadas",t.acreditadas()));
        return cabecera(repo.trabajoPlataforma(id,false).orElseThrow());
    }
    /** R24: concilia reserva conocida no iniciada si perdió autorización; no cancela trabajo ajeno vigente. */
    @Transactional public CabeceraTrabajo liberarNoIniciada(long id,Retiro d,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.CONCILIAR_TRABAJO,Campo.LEDGER);
        if(d==null||!texto(d.motivo(),2000))throw error("Registra la causa de la liberación conocida.");validar(d.comando());
        var t=bloquear(id);String h=comando("LIBERAR_NO_INICIADA",d,a);if(replay(t,d.comando(),h))return cabecera(t);exigirVersion(t,d.comando());
        if(!"PENDIENTE".equals(t.estado())||t.operador()!=null||t.entrega()!=null||t.revision()!=null)throw new ConflictoException("El trabajo pudo comenzar; conserva su reserva y acredita la ejecución real.");
        if(autorizacionDeBusquedaActual(t))throw new ConflictoException("El trabajo conserva autorizacion; su retiro voluntario mantiene la puerta del solicitante.");
        if(!repo.estado(id,t.version(),"RETIRADA"))throw cambio();evento(t,d.comando(),h,"RESERVA_NO_INICIADA_LIBERADA",a,Map.of("motivo",d.motivo(),"causa","AUTORIZACION_PERDIDA","rondasEjecutadas",0));
        if(t.organizacion()!=null)presupuesto.cancelarManualNoIniciada(t.organizacion(),t.solicitud(),AutoridadInvestigacionMercado.retiro(t.solicitud()));
        return cabecera(repo.trabajoPlataforma(id,false).orElseThrow());
    }
    @Transactional public EstadoTrabajo retirarNoIniciada(UUID solicitud,Retiro d,Actor actor,ContextoSesion sesion){
        contextos.iniciarOperador(actor,sesion);if(d==null||!texto(d.motivo(),2000))throw error("Conserva el motivo del retiro.");validar(d.comando());
        if(fondos.bloquearOrganizacion(actor.idOrganizacion())==null)throw new AccesoNoAutorizadoException();
        var t=repo.trabajo(actor.idOrganizacion(),solicitud,true).orElseThrow(()->new NoEncontradoException("Investigación"));exigirPropio(t,actor);
        String h=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("accion","RETIRAR","datos",d,"actor",actor)));
        if(replay(t,d.comando(),h))return estado(t,true);exigirVersion(t,d.comando());
        if(!"PENDIENTE".equals(t.estado())||t.operador()!=null)throw new ConflictoException("El trabajo pudo comenzar; no se cancela una reserva de ejecución incierta.");
        if(!repo.estado(t.id(),t.version(),"RETIRADA"))throw cambio();
        repo.evento(t.id(),d.comando().id(),h,"RETIRADA",t.version()+1,null,actor.idOrganizacion(),actor.idRolOperativo(),null,json.objetoCanonico(Map.of("motivo",d.motivo())));
        presupuesto.cancelarManualNoIniciada(t.organizacion(),t.solicitud(),AutoridadInvestigacionMercado.retiro(t.solicitud()));
        return estado(repo.trabajo(actor.idOrganizacion(),solicitud,false).orElseThrow(),true);
    }
    @Transactional public CabeceraTrabajo publicar(long id,Publicacion d,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.PUBLICAR_REFERENCIA,Campo.METADATOS_TRABAJO,Campo.EVIDENCIA_PUBLICA);
        if(d==null||d.referencia()==null||d.referenciasSeleccionadas()==null||d.referenciasSeleccionadas().isEmpty()||d.limitacionesPublicas()==null||d.limitacionesPublicas().isEmpty()||d.limitacionesPublicas().stream().anyMatch(v->!texto(v,2000)))throw error("Selecciona la evidencia pública revisada que se autoriza publicar.");validar(d.comando());
        var t=bloquear(id);String h=comando("PUBLICAR",d,a);if(replay(t,d.comando(),h))return cabecera(t);exigirVersion(t,d.comando());
        var c=configuracion(t.politica(),t.versionPolitica());var ahora=repo.ahora();
        if(!"REVISADA".equals(t.estado())||t.revision()==null)throw new ConflictoException("Sólo se publica una entrega revisada y aprobada.");
        if(d.desde()==null||d.hasta()==null||d.desde().isAfter(ahora)||!d.hasta().isAfter(ahora)||d.hasta().isAfter(d.desde().plus(c.vigenciaReferencia()))||d.hasta().isAfter(repo.politica(t.politica(),t.versionPolitica(),false).orElseThrow().hasta()))throw error("La publicación conserva la vigencia del segmento aprobado.");
        var seleccion=new HashSet<>(d.referenciasSeleccionadas());if(seleccion.size()!=d.referenciasSeleccionadas().size())throw error("Una referencia se selecciona una sola vez.");
        var rs=rondas(t);var referencias=rs.stream().filter(r->r.cierre()==Cierre.COMPLETA).flatMap(r->r.referencias().stream()).filter(r->seleccion.contains(r.id())).toList();
        if(referencias.size()!=seleccion.size()||referencias.stream().anyMatch(r->!r.incluidaEnCalculo()))throw error("La selección no corresponde a comparables depurados y revisados de la entrega.");
        var corpus=new CorpusPublicado(c.segmento(),ahora,referencias,d.limitacionesPublicas());
        if(referencias.stream().anyMatch(r->!contexto(c.segmento()).equals(r.comparable().contexto())||!c.segmento().composicion().equals(r.comparable().composicionEquivalente())))throw error("La publicación conserva el segmento y composición autorizados.");
        String contenido=json.objetoCanonico(corpus);
        repo.publicar(new Publicada(d.referencia(),t.id(),t.version()+1,c.id(),c.version(),segmento(c.segmento()),contenido,Idempotencia.huellaExacta(contenido),a.cuenta(),a.concesion(),d.desde(),d.hasta(),ahora));
        if(!repo.estado(id,t.version(),"PUBLICADA"))throw cambio();evento(t,d.comando(),h,"PUBLICADA",a,Map.of("referencia",d.referencia(),"seleccion",d.referenciasSeleccionadas()));
        return cabecera(repo.trabajoPlataforma(id,false).orElseThrow());
    }
    @Transactional public CabeceraTrabajo revisar(long id,RevisionEntrega d,SesionPlataforma sesion){
        var a=plataforma(sesion,Atribucion.REVISION_MERCADO_PLATFORM,Accion.REVISAR_EVIDENCIA,Campo.METADATOS_TRABAJO,Campo.EVIDENCIA_PUBLICA);
        if(d==null||d.decision()==null||!texto(d.motivo(),2000))throw error("Registra decisión y fundamento de revisión.");validar(d.comando());
        var t=bloquear(id);String h=comando("REVISAR",d,a);if(replay(t,d.comando(),h))return cabecera(t);
        exigirVersion(t,d.comando());var registro=politicaDeRegistro(t);
        boolean rectificacion=Set.of("REVISADA","PUBLICADA").contains(t.estado())&&d.decision()!=DecisionRevision.APROBAR;
        if((!"ENTREGADA".equals(t.estado())&&!rectificacion)||t.entrega()==null)throw new ConflictoException("No hay una entrega pendiente de esa revision.");
        if(rectificacion)repo.retirarReferencias(id,a.cuenta(),a.concesion(),d.motivo());
        String estado=switch(d.decision()){case APROBAR->"REVISADA";case OBSERVAR->"OBSERVADA";case RECHAZAR->"RECHAZADA";};
        var ahora=repo.ahora();var revisadas=rondas(t).stream().map(r->new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),r.consultas(),r.referencias(),r.informe(),r.limitaciones(),new Revision(d.comando().id().toString(),a.cuenta(),ahora,d.motivo()),r.etapasAcreditadas())).toList();
        BigDecimal acreditadas=null;
        if(d.decision()==DecisionRevision.APROBAR){validarHechosPrevios(revisadas,registro);var p=ContratoInvestigacionManual.comprobar(json.comoObjeto(t.plan(),Plan.class),revisadas,ahora);acreditadas=BigDecimal.valueOf(p.rondasCompletas().size());}
        String entrega=json.lista(revisadas),firma=Idempotencia.huellaExacta(entrega);
        if(!repo.revisar(id,t.version(),estado,d.comando().id(),a.cuenta(),a.concesion(),entrega,firma,acreditadas))throw cambio();
        evento(t,d.comando(),h,"REVISION_"+d.decision().name(),a,Map.of("decision",d.decision(),"motivo",d.motivo(),"entrega",revisadas));
        return cabecera(repo.trabajoPlataforma(id,false).orElseThrow());
    }
    /** Consulta determinista gratuita: proyección comercial, sin expediente de investigación. */
    @Transactional public Optional<ReferenciaOrientativa> referencia(UUID politica,String version,Actor actor,ContextoSesion sesion){
        contextos.iniciarOperador(actor,sesion);var solicitada=configuracion(politica,version);var ahora=repo.ahora();
        return repo.ultima(segmento(solicitada.segmento()),ahora).or(()->solicitada.permiteReferenciaHistorica()?repo.historica(segmento(solicitada.segmento()),ahora):Optional.empty()).map(v->{
            var c=configuracion(v.politica(),v.versionPolitica());
            var corpus=json.comoObjeto(v.proyeccion(),CorpusPublicado.class);
            var datos=CalculoPrecioMercado.describirUnitarios(contexto(c.segmento()),corpus.referencias().stream().map(Referencia::comparable).toList(),c.normalizacion());
            var limitaciones=new ArrayList<>(corpus.limitaciones());limitaciones.addAll(datos.exclusiones().stream().map(CalculoPrecioMercado.Exclusion::causa).distinct().toList());
            if(!ahora.isBefore(v.hasta()))limitaciones.add("Referencia caducada conservada con permiso; no se presenta como precio actual.");
            limitaciones.add("Referencia del segmento; completar datos críticos mejora la precisión. No es precio total del inmueble ni tasación.");
            int n=datos.comparables().size();String estado=!ahora.isBefore(v.hasta())?"REFERENCIA_CADUCADA":datos.promedio()==null?"BASE_INSUFICIENTE":n==1?"REFERENCIA_UNICA":"REFERENCIA_DEL_SEGMENTO";
            return new ReferenciaOrientativa(v.id(),c.segmento(),corpus.corte(),v.hasta(),estado,n,datos.promedio(),"Promedio descriptivo por m² de vivienda; "+c.segmento().naturaleza(),limitaciones);
        });
    }
    private boolean autorizacionDeBusquedaActual(Trabajo t){
        try{
            if(!habilitada)throw new CapacidadDeshabilitadaException();
            if(t.organizacion()!=null){var p=json.comoObjeto(t.plan(),Plan.class);autoridad.reautorizar(new Solicitud(t.solicitud(),PresupuestoIaService.PERFIL_MANUAL,p.versionRegla(),recurso(t.solicitud(),t.huellaRecurso()),t.huellaSolicitud()),solicitante(t));}
            else configuracion(t.politica(),t.versionPolitica());
            return true;
        }catch(AccesoNoAutorizadoException|CapacidadDeshabilitadaException conocida){return false;}
        // Fallo tecnico no equivale a falta de permiso o ejecucion cero.
    }
    /** Registrar/revisar hechos conservados no habilita otra búsqueda ni su divulgación. */
    private Politica politicaDeRegistro(Trabajo t){return repo.politica(t.politica(),t.versionPolitica(),true).orElseThrow(()->new NoEncontradoException("Política del trabajo"));}
    private static void validarHechosPrevios(List<Ronda> rondas,Politica p){
        Instant hasta=p.retirada()!=null&&p.retirada().isBefore(p.hasta())?p.retirada():p.hasta();
        for(var r:rondas)if(r.cierre()==Cierre.COMPLETA){
            if(r.consultas().stream().filter(Consulta::ejecutada).anyMatch(c->c.realizadaEn().isBefore(p.desde())||!c.realizadaEn().isBefore(hasta))
                ||r.referencias().stream().anyMatch(v->!v.obtenidaEn().isBefore(hasta)))throw error("Sólo se acredita como completa la evidencia obtenida dentro de la autorización; conserva el incidente sin inventar otra ronda.");
        }
    }
    private Trabajo bloquear(long id){
        var t=repo.trabajoPlataforma(id,false).orElseThrow(()->new NoEncontradoException("Trabajo"));
        if(t.organizacion()!=null&&fondos.bloquearOrganizacion(t.organizacion())==null)throw new AccesoNoAutorizadoException();
        return repo.trabajoPlataforma(id,true).orElseThrow(()->new NoEncontradoException("Trabajo"));
    }
    private Actor solicitante(Trabajo t){return new Actor(t.organizacion(),t.persona(),t.rol(),repo.rolSolicitante(t).orElseThrow(AccesoNoAutorizadoException::new));}
    private CabeceraTrabajo cabecera(Trabajo t){return new CabeceraTrabajo(t.id(),t.estado(),t.version(),t.creada(),json.comoObjeto(t.plan(),Plan.class).plazoComunicado());}
    private List<Ronda> rondas(Trabajo t){return t.entrega()==null?List.of():List.of(json.comoObjeto(t.entrega(),Ronda[].class));}
    private String comando(String accion,Object datos,Actual a){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("accion",accion,"cuenta",a.cuenta(),"datos",datos)));}
    private boolean replay(Trabajo t,Comando d,String huella){var e=repo.evento(t.id(),d.id());if(e.isEmpty())return false;if(!e.get().huella().equals(huella))throw new ConflictoException("El comando conserva otro contenido o actor.");return true;}
    private void evento(Trabajo t,Comando d,String h,String tipo,Actual a,Map<String,?> contenido){repo.evento(t.id(),d.id(),h,tipo,t.version()+1,a.cuenta(),null,null,a.concesion(),json.objetoCanonico(contenido));}
    private static void validar(Comando d){if(d==null||d.id()==null||d.versionEsperada()<0)throw error("Identifica el comando estable y la versión observada.");}
    private static void exigirVersion(Trabajo t,Comando d){if(t.version()!=d.versionEsperada())throw cambio();}
    private static ConflictoException cambio(){return new ConflictoException("Cambió el trabajo o su versión; revisa la situación actual.");}
    private Propuesta propuesta(Preparar d,Actor a){
        if(d==null||d.solicitud()==null||d.politica()==null||!texto(d.versionPolitica(),80)||!texto(d.carencia(),2000)||d.rondas()<1)throw error("Delimita la carencia concreta y las rondas del trabajo.");
        if(d.propiedad()!=null&&!propiedades.puedeLeer(a,d.propiedad()))throw new AccesoNoAutorizadoException();
        var c=configuracion(d.politica(),d.versionPolitica());if(d.rondas()>c.maximoRondas())throw error("Las rondas exceden el alcance configurado; no se amplía silenciosamente.");
        var r=reglas.regla(c.regla(),c.versionRegla(),false).orElseThrow(CapacidadDeshabilitadaException::new);
        Instant ahora=repo.ahora();if(!r.habilitada()||!"SOLICITANTE".equals(r.financiacion())||ahora.isBefore(r.desde())||!ahora.isBefore(r.hasta()))throw new CapacidadDeshabilitadaException();
        Instant plazo=d.plazoComunicado()==null?ahora.plus(c.plazoEntrega()):d.plazoComunicado();
        if(!plazo.isAfter(ahora))throw new ConflictoException("El plazo comunicado requiere actualización antes de admitir un trabajo nuevo.");
        // Una propuesta nueva ofrece como máximo el plazo operativo de la política; el aceptado queda fijo.
        if(plazo.isAfter(ahora.plus(c.plazoEntrega())))throw error("El plazo no corresponde a la política revisada.");
        var base=repo.ultima(segmento(c.segmento()),ahora);
        BaseConsultada b=base.map(v->new BaseConsultada(v.id().toString(),json.comoObjeto(v.proyeccion(),CorpusPublicado.class).corte(),v.hasta(),d.carencia())).orElse(null);
        var plan=new Plan(c.version(),json.objetoCanonico(c.segmento()),d.carencia(),b,c.fuentes(),c.parada(),c.limiteActivoPorRonda(),c.etapas(),d.rondas(),"Entrega estructurada y revisión competente",plazo,c.regla().toString(),c.versionRegla());
        ContratoInvestigacionManual.validar(plan);
        var datos=new Preparar(d.solicitud(),d.politica(),d.versionPolitica(),d.propiedad(),d.carencia(),d.rondas(),plazo);
        BigDecimal maximo=PrecisionCapacidad.liquidar(r.creditosPorRonda().multiply(BigDecimal.valueOf(d.rondas())));
        String h=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("datos",datos,"plan",plan,"maximo",maximo,"actor",a)));
        return new Propuesta(datos,plan,maximo,h);
    }
    private Configuracion configuracion(UUID id,String version){
        var p=repo.politica(id,version,true).orElseThrow(CapacidadDeshabilitadaException::new);Instant ahora=repo.ahora();
        if(!p.habilitada()||p.retirada()!=null||ahora.isBefore(p.desde())||!ahora.isBefore(p.hasta()))throw new CapacidadDeshabilitadaException();
        return json.comoObjeto(p.configuracion(),Configuracion.class);
    }
    private void validar(Configuracion c){
        if(c==null||c.id()==null||!texto(c.version(),80)||c.regla()==null||!texto(c.versionRegla(),80)||!texto(c.evidencia(),2000)||c.segmento()==null||c.maximoRondas()<1||!duracion(c.plazoEntrega())||!duracion(c.vigenciaReferencia())||!duracion(c.limiteActivoPorRonda())||!texto(c.parada(),2000)||c.fuentes()==null||c.fuentes().isEmpty()||c.etapas()==null||!c.etapas().containsAll(Set.of(Etapa.BUSQUEDA,Etapa.REVISION,Etapa.ENTREGA))||c.desde()==null||c.hasta()==null||!c.hasta().isAfter(c.desde()))throw error("La política necesita alcance, fuentes, límites, entrega, vigencia y evidencia aprobados.");
        var s=c.segmento();if(s.operacion()==null||s.naturaleza()==null||!AtributosGobernados.nombresDeTipos().contains(s.tipo())||s.distrito()<=0||!texto(s.conceptoArea(),100)||!texto(s.composicion(),200))throw error("El segmento debe conservar operación, tipo gobernado, distrito, naturaleza, área y composición.");
        if(!CondicionesEconomicas.moneda(s.moneda(),"de la referencia").equals(s.moneda()))throw error("Utiliza la moneda canónica de Core.");
        var distrito=distritos.findById(s.distrito()).orElseThrow(()->error("Distrito no reconocido."));
        if(!distrito.isActivo()||!Set.of("LIMA","CALLAO").contains(distrito.getProvincia().trim().toUpperCase(Locale.ROOT)))throw error("La cobertura inicial corresponde a Lima Metropolitana y Callao.");
        var unicas=new HashSet<Fuente>();for(var f:c.fuentes())if(f==null||f.fuente()==null||!unicas.add(f.fuente())||!texto(f.versionPermiso(),80)||!texto(f.viaPermitida(),2000)||!texto(f.reutilizacionPermitida(),2000)||!texto(f.evidenciaPermiso(),2000))throw error("Cada fuente requiere una vía y reutilización permitidas, con evidencia; ser candidata no la habilita.");
    }
    private void exigirPropio(Trabajo t,Actor a){
        if(!Objects.equals(t.organizacion(),a.idOrganizacion())||!Objects.equals(t.persona(),a.idPersona())||!Objects.equals(t.rol(),a.idRolOperativo()))throw new NoEncontradoException("Investigación");
        if(t.propiedad()!=null&&!propiedades.puedeLeer(a,t.propiedad()))throw new AccesoNoAutorizadoException();
    }
    private EstadoTrabajo estado(Trabajo t,boolean maximo){var p=json.comoObjeto(t.plan(),Plan.class);return new EstadoTrabajo(t.solicitud(),t.estado(),t.version(),t.creada(),p.plazoComunicado(),maximo?t.maximo():null,repo.referenciaDelTrabajo(t.id()).orElse(null));}
    private Actual plataforma(SesionPlataforma s,Atribucion a,Accion accion,Campo...campos){return atribuciones.exigirMercado(s,a,accion,Set.of(campos));}
    private String segmento(Segmento s){return Idempotencia.huellaExacta(json.objetoCanonico(s));}
    private CalculoPrecioMercado.Contexto contexto(Segmento s){return new CalculoPrecioMercado.Contexto(s.operacion(),s.tipo(),segmento(s),s.moneda(),s.periodo(),s.naturaleza(),s.conceptoArea());}
    private String origen(Preparar d,Plan p,Actor a){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("datos",d,"plan",p,"actor",a)));}
    private static Recurso recurso(UUID id,String origen){return new Recurso("INVESTIGACION_MERCADO",id.toString(),"INVESTIGACION_PUNTUAL",origen);}
    private static boolean texto(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max;}
    private static boolean duracion(Duration d){return d!=null&&!d.isNegative()&&!d.isZero();}
    private static ReglaNegocioException error(String s){return new ReglaNegocioException(s);}
}
