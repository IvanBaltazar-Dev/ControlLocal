package com.controllocal.service.disponibilidad;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.disponibilidad.ConfirmacionDisponibilidadContextual;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad;
import com.controllocal.service.soporte.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.controllocal.service.disponibilidad.DisponibilidadContextualService.contextoDemanda;
import static com.controllocal.service.disponibilidad.DisponibilidadContextualService.contextoUnidad;
import static com.controllocal.service.disponibilidad.DisponibilidadContextualService.contextoComposicion;

/** Sólo el hecho D2. Cuenta/SID, recurso, CAS, antecedente, evento y recibo en la misma transacción. */
@Service
public class DisponibilidadContextualServiceImpl implements DisponibilidadContextualService {
    private static final String COMANDO = "CONFIRMAR_DISPONIBILIDAD_CONTEXTUAL";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final ConfirmacionDisponibilidadContextualRepository confirmaciones;
    private final AccesoEncargo acceso;
    private final RequerimientoService demandas;
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final EventoDominioRepository eventos;
    private final Documentos documentos;
    private final Clock reloj;

    @Autowired
    public DisponibilidadContextualServiceImpl(ConfirmacionDisponibilidadContextualRepository confirmaciones,
            AccesoEncargo acceso,RequerimientoService demandas,ContextosService contextos,
            CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,
            ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,
            EventoDominioRepository eventos,Documentos documentos) {
        this(confirmaciones,acceso,demandas,contextos,cuentas,miembros,registros,comandos,eventos,documentos,Clock.systemUTC());
    }
    DisponibilidadContextualServiceImpl(ConfirmacionDisponibilidadContextualRepository confirmaciones,
            AccesoEncargo acceso,RequerimientoService demandas,ContextosService contextos,
            CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,
            ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,
            EventoDominioRepository eventos,Documentos documentos,Clock reloj) {
        this.confirmaciones=confirmaciones;this.acceso=acceso;this.demandas=demandas;this.contextos=contextos;
        this.cuentas=cuentas;this.miembros=miembros;this.registros=registros;this.comandos=comandos;
        this.eventos=eventos;this.documentos=documentos;this.reloj=reloj;
    }

    @Override @Transactional(readOnly=true)
    public Vista consultar(TipoRecurso tipo,long id,Actor actor) {
        Recurso recurso=recurso(tipo,id,actor,false,null);
        var seleccion=seleccion(recurso.clave(),actor.idOrganizacion());
        return new Vista(recurso.clave(),recurso.version(),seleccion.versionSecuencial(),
                vigente(seleccion,actor.idOrganizacion(),null),seleccion.indeterminacion());
    }

    @Override @Transactional(readOnly=true)
    public ConfirmacionDisponibilidad paraMatching(TipoRecurso tipo,long id,Actor actor) {
        Recurso recurso=recurso(tipo,id,actor,false,null);
        return seleccion(recurso.clave(),actor.idOrganizacion()).paraMatching();
    }

    @Override @Transactional(readOnly=true)
    public Optional<ResultadoConfirmacion> consultarResultado(String clave,Actor actor) {
        Alcances.exigirOperador(actor);
        return comandos.consultarResultado(actor,clave,COMANDO).map(c->{
            var original=documentos.comoObjeto(c.getResultado(),ResultadoConfirmacion.class);
            recurso(original.registrado().clave().tipo(),original.registrado().clave().idRecurso(),actor,false,null);
            return original;
        });
    }

    @Override @Transactional
    public ResultadoConfirmacion confirmar(DatosConfirmacion entrada,Actor actor,ContextoSesion sesion,
                                           String claveIdempotencia,Procedencia procedente) {
        if(actor==null||!actor.esAgente())throw new AccesoNoAutorizadoException();
        DatosConfirmacion d=datosValidos(entrada);
        String clave=Idempotencia.exigirClave(claveIdempotencia);
        Procedencia procedencia=Procedencia.oPantalla(procedente);
        AccesoEncargo.Inicio inicio=iniciar(d,actor,sesion);
        String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("datos",d,"procedencia",procedencia)));
        String identidad=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+clave);
        registros.bloquearIdentidad(Long.parseUnsignedLong(identidad.substring(0,16),16));
        var previo=comandos.buscar(actor,clave,COMANDO,huella);
        if(previo.isPresent()) {
            var original=documentos.comoObjeto(previo.get().getResultado(),ResultadoConfirmacion.class);
            recurso(original.registrado().clave().tipo(),original.registrado().clave().idRecurso(),actor,false,null);
            return original;
        }
        Recurso recurso=recurso(d.tipo(),d.idRecurso(),actor,true,inicio);
        if(recurso.version()!=d.versionRecursoObservada())throw new ConflictoException("Cambió el recurso observado.");
        List<SelectorConfirmacionVigente.Hecho> hechos=hechos(recurso.clave(),actor.idOrganizacion());
        var anterior=SelectorConfirmacionVigente.seleccionar(hechos);
        if(anterior.versionSecuencial()!=d.versionConfirmacionEsperada())
            throw new ConflictoException("Cambió la confirmación observada; conserva y consulta la actual.");
        Instant ahora=reloj.instant().truncatedTo(ChronoUnit.MICROS);
        Instant base=baseFrescura(d,ahora);
        var fila=new ConfirmacionDisponibilidadContextual();
        fila.setOrganizacionId(actor.idOrganizacion());fila.setTipoRecurso(recurso.clave().tipo().name());
        fila.setIdRecurso(recurso.clave().idRecurso());fila.setOperacion(recurso.clave().operacion().name());
        fila.setAspecto(ASPECTO);fila.setContexto(recurso.clave().contexto());
        fila.setVersion(anterior.versionSecuencial()+1);fila.setVersionRecursoObservada(d.versionRecursoObservada());
        fila.setDisponible(d.disponible());fila.setPrecisionHecho(d.fechaHecho()!=null?PrecisionHecho.INSTANTE.name():PrecisionHecho.FECHA.name());
        fila.setFechaHecho(d.fechaHecho());fila.setFechaSinHora(d.fechaSinHora());fila.setBaseFrescura(base);fila.setRegistradoEn(ahora);
        fila.setIdPersonaConfirmante(actor.idPersona());fila.setIdRolConfirmante(actor.idRolOperativo());
        fila.setFuente(d.fuente());fila.setEvidenciaRef(d.evidenciaRef());fila.setProcedencia(documentos.objetoCanonico(procedencia));
        fila.setHuellaSesion(sesion.identidad());fila.setReferenciaSesionHash(Idempotencia.huellaExacta(sesion.referenciaSesion()));
        fila.setRevisionContexto(sesion.revisionContexto());
        confirmaciones.saveAndFlush(fila);
        List<SelectorConfirmacionVigente.Hecho> posteriores=new ArrayList<>(hechos);posteriores.add(hecho(fila));
        var actual=SelectorConfirmacionVigente.seleccionar(posteriores);
        Aplicacion aplicacion=actual.indeterminacion()!=null&&actual.posiblesUltimos().contains(fila.getId())
                ?Aplicacion.PRECISION_PENDIENTE:actual.vigente()!=null&&actual.vigente().id()==fila.getId()
                ?Aplicacion.APLICADA:Aplicacion.ANTECEDENTE_CONSERVADO;
        var resultado=new ResultadoConfirmacion(registro(fila),aplicacion,
                vigente(actual,actor.idOrganizacion(),fila),actual.indeterminacion());
        var carga=Map.of("registro",resultado.registrado(),"aplicacion",aplicacion,
                "versionSecuencial",actual.versionSecuencial());
        eventos.saveAndFlush(procedencia.sellar(EventoDominio.de(actor.idOrganizacion(),
                "DISPONIBILIDAD_CONFIRMACION_REGISTRADA","CONFIRMACION_DISPONIBILIDAD",fila.getId(),
                actor.idRolOperativo(),procedencia.canal())).con(documentos.objetoCanonico(carga)));
        comandos.registrar(actor,clave,COMANDO,huella,"CONFIRMACION_DISPONIBILIDAD",fila.getId(),procedencia,
                documentos.objetoCanonico(resultado));
        return resultado;
    }

    private AccesoEncargo.Inicio iniciar(DatosConfirmacion d,Actor actor,ContextoSesion sesion) {
        if(sesion==null)throw new AccesoNoAutorizadoException();
        AccesoEncargo.Inicio inicio=null;
        if(d.tipo()==TipoRecurso.ENCARGO)inicio=acceso.iniciar(actor,sesion,d.idRecurso());
        else {
            var propias=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
            if(propias.size()!=1)throw new AccesoNoAutorizadoException();
            cuentas.bloquearCambioAcceso(propias.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        }
        try {
            var vigente=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(!ContextoSesion.de(vigente).equals(sesion)||vigente.restricciones()==null
                    ||vigente.restricciones().debeCambiarContrasena()||vigente.restricciones().debeEnrolarMfa())
                throw new AccesoNoAutorizadoException();
        } catch(CredencialesInvalidasException fallo) {throw new AccesoNoAutorizadoException();}
        return inicio;
    }

    private record Recurso(Clave clave,long version) { }
    private Recurso recurso(TipoRecurso tipo,long id,Actor actor,boolean confirmar,AccesoEncargo.Inicio inicio) {
        Alcances.exigirOperador(actor);
        if(tipo==null||id<=0)throw new ReglaNegocioException("Indica un recurso contextual válido.");
        if(tipo==TipoRecurso.DEMANDA) {
            var d=demandas.contextoDisponibilidad(id,actor,confirmar);
            return new Recurso(new Clave(tipo,id,d.operacion(),ASPECTO,contextoDemanda(id)),d.version());
        }
        Captacion c=confirmar?acceso.escribir(id,actor,inicio):acceso.leer(id,actor);
        if(confirmar)acceso.exigirResponsable(c,actor);
        String contexto=c.getIdComposicionActual()==null?contextoUnidad(c.getPropiedad().getId()):contextoComposicion(c.getIdComposicionActual());
        return new Recurso(new Clave(tipo,id,c.operacion(),ASPECTO,contexto),c.getVersion());
    }

    private SelectorConfirmacionVigente.Seleccion seleccion(Clave clave,long org) {
        return SelectorConfirmacionVigente.seleccionar(hechos(clave,org));
    }
    private List<SelectorConfirmacionVigente.Hecho> hechos(Clave c,long org) {
        return confirmaciones.hechosParaMatching(org,c.tipo().name(),c.idRecurso(),c.operacion().name(),c.aspecto(),c.contexto())
                .stream().map(h->new SelectorConfirmacionVigente.Hecho(h.getId(),h.getVersion(),h.getDisponible(),
                        PrecisionHecho.valueOf(h.getPrecision()),h.getBaseFrescura(),h.getFechaSinHora(),h.getRegistradoEn())).toList();
    }
    private Registro vigente(SelectorConfirmacionVigente.Seleccion s,long org,ConfirmacionDisponibilidadContextual nueva) {
        if(s.vigente()==null)return null;
        if(nueva!=null&&s.vigente().id()==nueva.getId())return registro(nueva);
        return registro(confirmaciones.findByOrganizacionIdAndId(org,s.vigente().id())
                .orElseThrow(()->new IllegalStateException("Falta el hecho seleccionado de disponibilidad.")));
    }
    private static SelectorConfirmacionVigente.Hecho hecho(ConfirmacionDisponibilidadContextual f) {
        return new SelectorConfirmacionVigente.Hecho(f.getId(),f.getVersion(),f.isDisponible(),
                PrecisionHecho.valueOf(f.getPrecisionHecho()),f.getBaseFrescura(),f.getFechaSinHora(),f.getRegistradoEn());
    }
    private static Registro registro(ConfirmacionDisponibilidadContextual f) {
        return new Registro(f.getId(),new Clave(TipoRecurso.valueOf(f.getTipoRecurso()),f.getIdRecurso(),
                OperacionInmobiliaria.valueOf(f.getOperacion()),f.getAspecto(),f.getContexto()),f.getVersion(),
                f.getVersionRecursoObservada(),f.isDisponible(),PrecisionHecho.valueOf(f.getPrecisionHecho()),
                f.getFechaHecho(),f.getFechaSinHora(),f.getBaseFrescura(),f.getRegistradoEn(),f.getFuente(),
                f.getEvidenciaRef(),f.getIdPersonaConfirmante(),f.getIdRolConfirmante());
    }
    private static DatosConfirmacion datosValidos(DatosConfirmacion d) {
        if(d==null||d.tipo()==null||d.idRecurso()<=0||d.versionRecursoObservada()<0||d.versionConfirmacionEsperada()<0
                ||d.disponible()==null||(d.fechaHecho()==null)==(d.fechaSinHora()==null)
                ||d.fuente()==null||d.fuente().isBlank()||d.evidenciaRef()==null||d.evidenciaRef().isBlank())
            throw new ReglaNegocioException("Indica recurso/versiones observadas, disponibilidad, precisión, fuente y evidencia declaradas.");
        return new DatosConfirmacion(d.tipo(),d.idRecurso(),d.versionRecursoObservada(),d.versionConfirmacionEsperada(),
                d.disponible(),d.fechaHecho()==null?null:d.fechaHecho().truncatedTo(ChronoUnit.MICROS),d.fechaSinHora(),
                d.fuente().trim(),d.evidenciaRef().trim());
    }
    private static Instant baseFrescura(DatosConfirmacion d,Instant ahora) {
        Instant base=d.fechaHecho()!=null?d.fechaHecho():d.fechaSinHora().atStartOfDay(LIMA).toInstant();
        if(base.isAfter(ahora))throw new ReglaNegocioException("La confirmación no puede situarse en el futuro.");
        return base;
    }
}
