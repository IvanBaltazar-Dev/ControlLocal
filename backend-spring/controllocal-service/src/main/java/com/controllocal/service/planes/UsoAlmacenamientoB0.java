package com.controllocal.service.planes;

import com.controllocal.domain.planes.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.presupuesto.UsoAlmacenamientoB0Repository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.controllocal.service.planes.ClasificadorCargaAlmacenamientoB0.*;

/** Directriz7.2/GB aprobado31Oct: bytes exactos por objeto/workspace, sin eliminar evidencia. */
@Service
public class UsoAlmacenamientoB0 {
    /** Prueba interna correlacionada con el claim y la custodia148; no acepta Actor/SID del cliente. */
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.archivo.SeguimientoSalidaBinaria seguimientoEvaluacion;
    public record RecepcionComprobada(UUID job,UUID operacionIo,int numero,String claveIntento,UUID archivo){}
    public record Recurso(String tipo,String referencia,String finalidad,String huellaOrigen){}
    public record Solicitud(UUID reserva,Recurso recurso,long bytes,String huellaContenido,String identidadObjetoConocida,String namespaceHash){
        public Solicitud(UUID reserva,Recurso recurso,long bytes,String huellaContenido,String identidadObjetoConocida){
            this(reserva,recurso,bytes,huellaContenido,identidadObjetoConocida,null);
        }
    }
    public record Resultado(UUID reserva,String estado){}
    public sealed interface ReferenciaReserva permits RefInterna,RefEvaluacion {UUID reserva();}
    public record RefInterna(long organizacion,UUID reserva) implements ReferenciaReserva {}
    public record RefEvaluacion(UUID caso,UUID reserva) implements ReferenciaReserva {}
    public record PrevisionEvaluacionAdmitida(RefEvaluacion referencia,long techoBytes) {}
    public record LimiteRecepcion(ReferenciaReserva reserva,long maximoBytesContenido,String versionFuente,
        String huellaFuente,String namespaceHash) {}
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.presupuesto.EvaluacionesIaB0 evaluaciones;
    /** Identidad incluye backend/clave/versión privados, resuelta por Core; nunca una ruta cliente. */
    public record ComprobacionObjeto(String identidadObjeto,long bytes,String huellaContenido,String referenciaComprobacion){}
    public record PrevisionIa(UUID job,Recurso recurso,String perfil,String versionPerfil,String namespaceHash){}
    public record PrevisionAdmitida(RefInterna referencia,long techoBytes){}
    private record ContenidoEsperado(String identidadObjeto,long bytes,String huellaContenido,String referenciaIntento){}
    public record AusenciaComprobada(UUID retiro,UUID job,UUID archivo,long revision,String identidadObjeto,long bytes,
            String huellaContenido,String referenciaComprobacion){}
    public record NoAlmacenadoComprobado(UUID job,String referenciaEvidencia,String huellaEvidencia,Instant comprobadaEn){}
    /** Gestión técnica pendiente; no acredita que la cuota comercial esté agotada. No contiene claves del almacén. */
    public static final class IncertidumbreFisicaException extends ConflictoException {
        public IncertidumbreFisicaException(){super("La capacidad física requiere una conciliación técnica pendiente.");}
        public String causa(){return "INCERTIDUMBRE_TECNICA";}
    }
    private record PlanUso(PlanBrox plan,boolean soloEsencial){}
    private record LimiteAdmitido(String politica,String contexto){}
    /** El namespace ya pertenece a identidadObjetoConocida: no altera la huella exacta existente. */
    private record SolicitudExacta(UUID reserva,Recurso recurso,long bytes,String huellaContenido,String identidadObjetoConocida){}
    private final ContextoPresupuestoB0 contexto;
    private final PresupuestoIaRepository presupuestos;
    private final UsoAlmacenamientoB0Repository repo;
    private final Documentos json;
    private final List<ClasificadorCargaAlmacenamientoB0> clasificadores;
    private final Clock reloj;
    @org.springframework.beans.factory.annotation.Autowired
    public UsoAlmacenamientoB0(ContextoPresupuestoB0 contexto,PresupuestoIaRepository presupuestos,UsoAlmacenamientoB0Repository repo,
            Documentos json,List<ClasificadorCargaAlmacenamientoB0> clasificadores){
        this(contexto,presupuestos,repo,json,clasificadores,Clock.systemUTC());
    }
    public UsoAlmacenamientoB0(ContextoPresupuestoB0 contexto,PresupuestoIaRepository presupuestos,UsoAlmacenamientoB0Repository repo,
            Documentos json,List<ClasificadorCargaAlmacenamientoB0> clasificadores,Clock reloj){
        this.contexto=contexto;this.presupuestos=presupuestos;this.repo=repo;this.json=json;this.clasificadores=List.copyOf(clasificadores);this.reloj=reloj;
    }
    /** Sólo metadata; la misma referencia identifica el job antes de conocer bytes o hash. */
    public static RefInterna referenciaPrevision(long org,UUID job){
        if(org<=0||job==null)throw new ReglaNegocioException("Identifica la organización y el job de la previsión.");
        return new RefInterna(org,UUID.nameUUIDFromBytes(("BROX-PREVISION-IA\u0000"+org+"\u0000"+job).getBytes(StandardCharsets.UTF_8)));
    }
    @Transactional
    public PrevisionAdmitida previsionar(PrevisionIa p,Actor actor,ContextoSesion sesion){
        validar(p);contexto.iniciarArchivo(actor,sesion);
        Clasificacion c=clasificador(p.recurso()).clasificarPrevision(p,actor);validarClasificacion(c,p.recurso());
        if(c.categoria()!=CategoriaAlmacenamiento.DERIVADA_IA||c.necesidad()!=Necesidad.ORDINARIA)throw new CapacidadDeshabilitadaException();
        RefInterna ref=referenciaPrevision(actor.idOrganizacion(),p.job());Instant ahora=ahora();String huella=huellaPrevision(p,c,actor);
        var org=presupuestos.bloquearOrganizacion(actor.idOrganizacion());if(org==null||!org.activa())throw new AccesoNoAutorizadoException();
        var fisica=repo.bloquearCapacidadFisica(p.namespaceHash(),ahora).orElseThrow(CapacidadDeshabilitadaException::new);
        Uso uso=repo.bloquearUso(actor.idOrganizacion());var previa=repo.reserva(duenoOrg(actor.idOrganizacion()),ref.reserva());
        if(previa.isPresent()){
            misma(previa.get(),huella,actor);Detalle d=exigirPrevision(ref);
            if(!Set.of("RESERVADA","ALMACENANDO","INCIERTA","CONFIRMADA").contains(previa.get().estado()))throw new ConflictoException("La previsión ya fue liberada.");
            return new PrevisionAdmitida(ref,d.techo());
        }
        long techo=repo.limiteSalida(p.perfil(),p.versionPerfil(),ahora).orElseThrow(CapacidadDeshabilitadaException::new).bytes();
        if(techo<=0||!fisica.vigente())throw new CapacidadDeshabilitadaException();
        exigirFisicaConciliada(p.namespaceHash(),null,null,AmbitoIntentos.NAMESPACE);
        exigirCapacidadFisica(fisica,techo);LimiteAdmitido limite=admitirComercial(actor.idOrganizacion(),org.modalidad(),uso,c,techo,ahora);
        Reserva r=new Reserva(ref.reserva(),actor.idPersona(),actor.idRolOperativo(),huella,p.recurso().tipo(),p.recurso().referencia(),p.recurso().finalidad(),p.recurso().huellaOrigen(),
                c.categoria().name(),c.necesidad().name(),null,null,techo,limite.politica(),limite.contexto(),"RESERVADA",null);
        repo.crearPrevision(dueno(ref),r,p.job(),p.perfil(),p.versionPerfil(),p.namespaceHash(),techo,fisica.version(),ahora);
        repo.retener(ref.organizacion(),techo);repo.retenerFisica(p.namespaceHash(),techo);
        auditar(ref.organizacion(),ref.reserva(),"PREVISION_IA_RESERVADA",ahora);return new PrevisionAdmitida(ref,techo);
    }
    /** El caller mantiene C1/O/J y llama después a Presupuesto.iniciar en la MISMA TX corta. */
    @Transactional
    public void exigirAntesDeClaim(RefInterna ref,Actor actor,ContextoSesion sesion){
        validarRef(ref);contexto.iniciarArchivo(actor,sesion);if(ref.organizacion()!=actor.idOrganizacion())throw new AccesoNoAutorizadoException();
        Reserva inicial=leer(ref);Detalle previa=exigirPrevision(ref);PrevisionIa p=prevision(inicial,previa);
        Clasificacion c=clasificador(p.recurso()).clasificarPrevision(p,actor);validarClasificacion(c,p.recurso());
        presupuestos.bloquearOrganizacion(ref.organizacion());
        var fisica=repo.bloquearCapacidadFisica(previa.namespace(),ahora()).orElseThrow(CapacidadDeshabilitadaException::new);
        repo.bloquearUso(ref.organizacion());Reserva r=leer(ref);Detalle d=exigirPrevision(ref);misma(r,huellaPrevision(p,c,actor),actor);
        long techo=repo.limiteSalida(d.perfil(),d.versionPerfil(),ahora()).orElseThrow(CapacidadDeshabilitadaException::new).bytes();
        if(!fisica.vigente()||!"RESERVADA".equals(r.estado())||r.bytes()!=null||techo!=d.techo()||!d.versionFisica().equals(fisica.version())
                ||fisica.reservados()<d.retenidosFisicos())throw new ConflictoException("La previsión original no admite otro claim.");
        exigirFisicaConciliada(d.namespace(),null,null,AmbitoIntentos.NAMESPACE);
        if(d.reclamada()==null){repo.reclamarPrevision(dueno(ref),ref.reserva(),repo.ahoraDeAlmacenamiento());auditar(ref.organizacion(),ref.reserva(),"PREVISION_IA_RECLAMADA",ahora());}
    }
    /** Entrada del bridge que conserva las mismas guardas actor/SID/recurso antes del I/O. */
    @Transactional
    public Resultado admitirPrevisionParaAlmacenamiento(RefInterna ref,Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s);validarRef(ref);contexto.iniciarArchivo(actor,sesion);Clasificacion c=clasificar(s,actor);
        if(ref.organizacion()!=actor.idOrganizacion()||!ref.reserva().equals(s.reserva()))throw new AccesoNoAutorizadoException();
        Reserva r=bloquear(ref);Detalle d=exigirPrevision(ref);
        if(r.persona()!=actor.idPersona()||r.rol()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        return admitirContenidoDentro(ref,s,c,r,d);
    }
    /** Guarda hechos ya causados tras revocación; no autoriza nueva transferencia o lectura. */
    @Transactional public Resultado admitirRecepcion(RefInterna ref,Solicitud s,RecepcionComprobada prueba){
        validar(s);validarRef(ref);
        if(prueba==null||prueba.job()==null||prueba.operacionIo()==null||prueba.numero()<1||prueba.archivo()==null||!hash(prueba.claveIntento())||!ref.reserva().equals(s.reserva()))throw new ConflictoException("La recepción no conserva su identidad.");
        var c=clasificador(s.recurso()).clasificarRecepcion(s,prueba);validarClasificacion(c,s.recurso());
        var r=bloquear(ref);var d=exigirPrevision(ref);
        if(!d.job().equals(prueba.job()))throw new ConflictoException("La recepción pertenece a otra previsión.");
        return admitirContenidoDentro(ref,s,c,r,d);
    }
    private Resultado admitirContenidoDentro(RefInterna ref,Solicitud s,Clasificacion c,Reserva r,Detalle d){
        if(!recurso(r).equals(s.recurso())||c.categoria()!=CategoriaAlmacenamiento.DERIVADA_IA
                ||!Objects.equals(d.namespace(),s.namespaceHash())||s.identidadObjetoConocida()==null)
            throw new ConflictoException("La salida no corresponde a la previsión de este job.");
        // La conciliación propia debe poder cerrar el huérfano incluso cuando otro intento siga pendiente.
        // Sólo una nueva identidad de I/O necesita espacio físico todavía no cubierto por esa identidad.
        if(r.objeto()==null)exigirFisicaConciliada(d.namespace(),s,dueno(ref),AmbitoIntentos.OPERACION_PROPIA);
        fijarDentro(ref,r,d,new ContenidoEsperado(s.identidadObjetoConocida(),s.bytes(),s.huellaContenido(),"OBJETO_ESPERADO:"+s.identidadObjetoConocida()));
        if("RESERVADA".equals(r.estado())&&!repo.iniciar(dueno(ref),ref.reserva()))throw new ConflictoException("La previsión cambió antes de almacenar.");
        return resultado(leer(ref));
    }
    @Transactional
    public Resultado cancelarPrevisionNoAlmacenada(RefInterna ref,NoAlmacenadoComprobado prueba){
        validar(prueba);Reserva inicial=leer(ref);Detalle previo=exigirPrevision(ref);
        if(!previo.job().equals(prueba.job()))throw new ConflictoException("La prueba pertenece a otro job.");
        // La autoridad se verifica ANTES de org; el propietario ya retiene O/J. No se inventa no inferencia.
        clasificador(recurso(inicial)).exigirNoAlmacenado(recurso(inicial),prueba);
        Reserva r=bloquear(ref);Detalle d=exigirPrevision(ref);String huella=Idempotencia.huellaExacta(json.objetoCanonico(prueba));
        if("CANCELADA".equals(r.estado())){
            if(!huella.equals(d.liberacion()))throw new ConflictoException("La previsión conserva otra prueba de liberación.");return resultado(r);
        }
        if("CONFIRMADA".equals(r.estado())||r.bytes()!=null||r.objeto()!=null||d.reclamada()!=null&&prueba.comprobadaEn().isBefore(d.reclamada()))
            throw new ConflictoException("La previsión conserva un contenido o intento que exige su recuperación original.");
        repo.contabilizar(ref.organizacion(),r.retenidos(),0);repo.contabilizarFisica(d.namespace(),d.retenidosFisicos(),0);
        repo.cancelarPrevision(dueno(ref),ref.reserva(),huella,ahora());
        presupuestos.antecedente(ref.organizacion(),null,"PREVISION_IA_NO_ALMACENADA",json.objetoCanonico(Map.of("reserva",ref.reserva(),"prueba",prueba)),ahora());
        return new Resultado(ref.reserva(),"CANCELADA");
    }
    /** Ausencia acreditada por el bridge; el ledger no borra binarios ni decide la política144. */
    @Transactional
    public Resultado liberarRetiradaVerificada(RefInterna ref,AusenciaComprobada ausencia){
        validar(ausencia);Reserva inicial=leer(ref);Detalle previa=exigirPrevision(ref);
        if(!"CONFIRMADA".equals(inicial.estado()))return liberarRecepcionPendiente(ref,inicial,previa,ausencia);
        exigirContenidoRetirado(inicial,previa,ausencia);String id=Idempotencia.huellaExacta(ausencia.identidadObjeto()),huella=Idempotencia.huellaExacta(json.objetoCanonico(ausencia));
        var anterior=repo.presencia(dueno(ref),id).orElseThrow(()->new ConflictoException("El objeto no conserva presencia contabilizada."));
        if("RETIRADO_VERIFICADO".equals(anterior.estado()))return mismoRetiro(ref,anterior,ausencia,huella);
        clasificador(recurso(inicial)).exigirRetiro(recurso(inicial),ausencia);
        Reserva r=bloquear(ref);Detalle d=exigirPrevision(ref);exigirContenidoRetirado(r,d,ausencia);
        Presencia p=repo.presencia(dueno(ref),id).orElseThrow(()->new ConflictoException("El objeto no conserva su presencia."));
        if("RETIRADO_VERIFICADO".equals(p.estado()))return mismoRetiro(ref,p,ausencia,huella);
        var objeto=repo.objeto(dueno(ref),id).orElseThrow(()->new ConflictoException("No se localizó la metadata del objeto original."));
        if(!CategoriaAlmacenamiento.DERIVADA_IA.name().equals(objeto.categoria()))throw new ConflictoException("Se conserva el original y la evidencia existente.");
        mismoContenido(objeto,ausencia.bytes(),ausencia.huellaContenido());
        if(p.comercial())repo.liberarConfirmados(ref.organizacion(),objeto.bytes());
        if(p.fisico())repo.liberarFisicosConfirmados(p.namespace(),objeto.bytes());
        repo.retirar(dueno(ref),id,ausencia.retiro(),huella,ahora());
        presupuestos.antecedente(ref.organizacion(),null,"DERIVADA_IA_RETIRADA_VERIFICADA",json.objetoCanonico(Map.of("reserva",ref.reserva(),"ausencia",ausencia)),ahora());
        return new Resultado(ref.reserva(),"RETIRADO_VERIFICADO");
    }
    /** Hubo writer: sólo la misma retirada/fence y ausencia exacta pueden cancelar su previsión pendiente. */
    private Resultado liberarRecepcionPendiente(RefInterna ref,Reserva inicial,Detalle d,AusenciaComprobada a){
        String h=Idempotencia.huellaExacta(json.objetoCanonico(a));
        if("CANCELADA".equals(inicial.estado())){
            if(!h.equals(d.liberacion()))throw new ConflictoException("La recepción conserva otra prueba de retirada.");return new Resultado(ref.reserva(),"RETIRADO_VERIFICADO");
        }
        if(!Set.of("RESERVADA","ALMACENANDO","INCIERTA").contains(inicial.estado())||!CategoriaAlmacenamiento.DERIVADA_IA.name().equals(inicial.categoria())||!d.job().equals(a.job())||!a.identidadObjeto().startsWith(d.namespace()+":"))throw new ConflictoException("La retirada no corresponde a la recepción pendiente.");
        if(inicial.bytes()!=null&&(inicial.bytes()!=a.bytes()||!Objects.equals(inicial.contenido(),a.huellaContenido())||!Objects.equals(inicial.objeto(),Idempotencia.huellaExacta(a.identidadObjeto()))))throw new ConflictoException("La recepción conserva otro contenido esperado.");
        clasificador(recurso(inicial)).exigirRetiro(recurso(inicial),a);var r=bloquear(ref);d=exigirPrevision(ref);
        if(!r.estado().equals(inicial.estado())||r.retenidos()!=inicial.retenidos())throw new ConflictoException("La previsión cambió durante la retirada.");
        repo.contabilizar(ref.organizacion(),r.retenidos(),0);repo.contabilizarFisica(d.namespace(),d.retenidosFisicos(),0);
        repo.cancelarPrevision(dueno(ref),ref.reserva(),h,ahora());
        presupuestos.antecedente(ref.organizacion(),null,"RECEPCION_IA_RETIRADA_VERIFICADA",json.objetoCanonico(Map.of("reserva",ref.reserva(),"ausencia",a)),ahora());
        return new Resultado(ref.reserva(),"RETIRADO_VERIFICADO");
    }
    @Transactional
    public Resultado reservar(Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s);contexto.iniciarArchivo(actor,sesion);Clasificacion c=clasificar(s,actor);
        if(c.categoria()==CategoriaAlmacenamiento.DERIVADA_IA)throw new ConflictoException("La derivada IA exige su previsión anterior al procesamiento.");
        var org=presupuestos.bloquearOrganizacion(actor.idOrganizacion());if(org==null||!org.activa())throw new AccesoNoAutorizadoException();
        var fisica=repo.bloquearCapacidadFisica(s.namespaceHash(),ahora());
        Uso uso=repo.bloquearUso(actor.idOrganizacion());String huella=huella(s,c,actor);
        var anterior=repo.reserva(duenoOrg(actor.idOrganizacion()),s.reserva());
        if(anterior.isPresent()){misma(anterior.get(),huella,actor);return resultado(anterior.get());}
        String objeto=s.identidadObjetoConocida()==null?null:Idempotencia.huellaExacta(s.identidadObjetoConocida());
        boolean contabilizado=false;
        boolean conocidoFisico=false;
        if(objeto!=null){var conocido=repo.objeto(duenoOrg(actor.idOrganizacion()),objeto);if(conocido.isPresent()){
            mismoContenido(conocido.get(),s.bytes(),s.huellaContenido());var presencia=repo.presencia(duenoOrg(actor.idOrganizacion()),objeto);exigirPresente(presencia);
            contabilizado=presencia.map(Presencia::comercial).orElse(CategoriaAlmacenamiento.valueOf(conocido.get().categoria()).computa());conocidoFisico=true;
        }}
        long retener=c.categoria().computa()&&!contabilizado?s.bytes():0;Instant ahora=ahora();
        LimiteAdmitido limite=admitirComercial(actor.idOrganizacion(),org.modalidad(),uso,c,retener,ahora);
        Reserva r=new Reserva(s.reserva(),actor.idPersona(),actor.idRolOperativo(),huella,s.recurso().tipo(),s.recurso().referencia(),s.recurso().finalidad(),s.recurso().huellaOrigen(),
                c.categoria().name(),c.necesidad().name(),s.bytes(),s.huellaContenido(),retener,limite.politica(),limite.contexto(),"RESERVADA",objeto);
        long retenerFisico=fisica.isPresent()&&!conocidoFisico?s.bytes():0;
        if(retenerFisico>0)exigirFisicaConciliada(s.namespaceHash(),s,duenoOrg(actor.idOrganizacion()),AmbitoIntentos.NAMESPACE);
        if(fisica.isPresent())exigirCapacidadFisica(fisica.get(),retenerFisico);
        repo.crearReserva(duenoOrg(actor.idOrganizacion()),r,ahora);if(retener>0)repo.retener(actor.idOrganizacion(),retener);
        if(s.namespaceHash()!=null){repo.asociarFisica(duenoOrg(actor.idOrganizacion()),r.id(),s.namespaceHash(),fisica.map(CapacidadFisica::version).orElse(null),retenerFisico);
            if(retenerFisico>0)repo.retenerFisica(s.namespaceHash(),retenerFisico);}
        auditar(actor.idOrganizacion(),r.id(),"ALMACENAMIENTO_RESERVADO",ahora);return resultado(r);
    }
    /** Una reclamación; revalida el recurso. El almacenamiento no se ejecuta dentro de B0. */
    @Transactional
    public boolean iniciar(Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s);contexto.iniciarArchivo(actor,sesion);Clasificacion c=clasificar(s,actor);
        Reserva r=bloquear(new RefInterna(actor.idOrganizacion(),s.reserva()));
        misma(r,huella(s,c,actor),actor);return repo.iniciar(duenoOrg(actor.idOrganizacion()),r.id());
    }
    /** Callback del adaptador Core, después de comprobar binario y enlace durable en su transacción. */
    @Transactional
    public Resultado confirmar(ReferenciaReserva ref,ComprobacionObjeto objeto){
        validar(objeto);Reserva r=bloquear(ref);String id=Idempotencia.huellaExacta(objeto.identidadObjeto());
        Detalle d=detalle(ref);var presencia=repo.presencia(dueno(ref),id);exigirPresente(presencia);
        if(r.bytes()==null||r.bytes()!=objeto.bytes()||!Objects.equals(r.contenido(),objeto.huellaContenido())||r.objeto()!=null&&!r.objeto().equals(id))
            throw new ConflictoException("El binario comprobado no coincide con la reserva original.");
        if("CONFIRMADA".equals(r.estado()))return resultado(r);
        if(!Set.of("ALMACENANDO","INCIERTA").contains(r.estado()))throw new ConflictoException("La carga no conserva una ejecución pendiente.");
        long agregar=0;Instant ahora=ahora();boolean comercial=ref instanceof RefInterna&&CategoriaAlmacenamiento.valueOf(r.categoria()).computa(),fisico=d!=null&&d.versionFisica()!=null;
        var previo=repo.objeto(dueno(ref),id);
        if(comercial||fisico){
            if(previo.isPresent())mismoContenido(previo.get(),objeto.bytes(),objeto.huellaContenido());
            else repo.objetoConfirmado(dueno(ref),new Objeto(id,objeto.huellaContenido(),objeto.bytes(),r.categoria()),objeto.referenciaComprobacion(),ahora);
            if(comercial&&!presencia.map(Presencia::comercial).orElseGet(()->previo.map(o->CategoriaAlmacenamiento.valueOf(o.categoria()).computa()).orElse(false)))agregar=objeto.bytes();
            if(presencia.isEmpty())repo.presencia(dueno(ref),id,d==null?null:d.namespace(),d==null?null:d.versionFisica(),comercial,fisico);
            else if(comercial&&!presencia.get().comercial())repo.promoverComercial(dueno(ref),id);
        }
        if(fisico)repo.contabilizarFisica(d.namespace(),d.retenidosFisicos(),previo.isEmpty()?objeto.bytes():0);
        if(ref instanceof RefInterna c)repo.contabilizar(c.organizacion(),r.retenidos(),agregar);repo.cerrar(dueno(ref),r.id(),"CONFIRMADA",id,ahora);
        if(ref instanceof RefInterna c)auditar(c.organizacion(),r.id(),"ALMACENAMIENTO_CONFIRMADO",ahora);return new Resultado(r.id(),"CONFIRMADA");
    }
    @Transactional
    public void marcarIncierta(ReferenciaReserva ref){
        Reserva r=bloquear(ref);if("ALMACENANDO".equals(r.estado())){repo.incierta(dueno(ref),r.id());if(ref instanceof RefInterna c)auditar(c.organizacion(),r.id(),"ALMACENAMIENTO_INCIERTO",ahora());}
    }
    /** Ausencia comprobada por el adaptador; no es un argumento o booleano del usuario. */
    @Transactional
    public Resultado cancelarNoAlmacenado(RefInterna ref,String referenciaAusencia){
        Reserva r=bloquear(ref);if("CANCELADA".equals(r.estado()))return resultado(r);
        Detalle d=detalle(ref);if(d!=null&&d.prevision())throw new ConflictoException("La previsión IA exige su prueba durable de ausencia de almacenamiento.");
        if("CONFIRMADA".equals(r.estado()))throw new ConflictoException("Se conserva el objeto ya confirmado.");
        if(!"RESERVADA".equals(r.estado())&&!texto(referenciaAusencia,2000))throw new ConflictoException("Conserva la comprobación de ausencia antes de liberar la carga incierta.");
        repo.contabilizar(ref.organizacion(),r.retenidos(),0);if(d!=null&&d.versionFisica()!=null)repo.contabilizarFisica(d.namespace(),d.retenidosFisicos(),0);
        repo.cerrar(dueno(ref),r.id(),"CANCELADA",r.objeto(),ahora());
        var contenido=new LinkedHashMap<String,Object>();contenido.put("reserva",r.id());contenido.put("referenciaAusencia",referenciaAusencia);
        presupuestos.antecedente(ref.organizacion(),null,"ALMACENAMIENTO_NO_REALIZADO",json.objetoCanonico(contenido),ahora());return new Resultado(r.id(),"CANCELADA");
    }
    /** Reconstrucción desde un objeto REAL ya conservado; no es una carga nueva ni inventa clientes. */
    @Transactional
    public void observarExistenteComprobado(long org,ComprobacionObjeto objeto,CategoriaAlmacenamiento categoria){
        validar(objeto);if(categoria==null)throw new ReglaNegocioException("Conserva la categoría Core.");
        if(presupuestos.bloquearOrganizacion(org)==null)throw new NoEncontradoException("Organización");
        Uso uso=repo.bloquearUso(org);if(!categoria.computa())return;
        String id=Idempotencia.huellaExacta(objeto.identidadObjeto());var conocido=repo.objeto(duenoOrg(org),id);
        if(conocido.isPresent()){mismoContenido(conocido.get(),objeto.bytes(),objeto.huellaContenido());exigirPresente(repo.presencia(duenoOrg(org),id));return;}
        sumar(sumar(uso.confirmados(),uso.reservados()),objeto.bytes());
        repo.objetoConfirmado(duenoOrg(org),new Objeto(id,objeto.huellaContenido(),objeto.bytes(),categoria.name()),objeto.referenciaComprobacion(),ahora());
        repo.contabilizar(org,0,objeto.bytes());presupuestos.antecedente(org,null,"ALMACENAMIENTO_EXISTENTE_COMPROBADO",json.objetoCanonico(Map.of("objeto",id,"categoria",categoria)),ahora());
    }
    private Reserva bloquear(ReferenciaReserva ref){
        validarRef(ref);
        if(ref instanceof RefInterna c&&presupuestos.bloquearOrganizacion(c.organizacion())==null)throw new NoEncontradoException("Reserva de archivo");
        Detalle d=detalle(ref);
        if(d!=null&&d.versionFisica()!=null)repo.bloquearFisicaReservada(d.namespace(),d.versionFisica());
        if(ref instanceof RefInterna c)repo.bloquearUso(c.organizacion());repo.bloquearReserva(dueno(ref),ref.reserva());return leer(ref);
    }
    private Reserva leer(ReferenciaReserva ref){validarRef(ref);return repo.reserva(dueno(ref),ref.reserva()).orElseThrow(()->new NoEncontradoException("Reserva de archivo"));}
    private Detalle detalle(ReferenciaReserva ref){validarRef(ref);return repo.detalle(dueno(ref),ref.reserva()).orElse(null);}
    private Detalle exigirPrevision(RefInterna ref){
        Detalle d=detalle(ref);if(d==null||!d.prevision()||!referenciaPrevision(ref.organizacion(),d.job()).equals(ref))
            throw new ConflictoException("Identifica la previsión original del job.");return d;
    }
    private static Recurso recurso(Reserva r){return new Recurso(r.tipo(),r.recurso(),r.finalidad(),r.origen());}
    private static PrevisionIa prevision(Reserva r,Detalle d){return new PrevisionIa(d.job(),recurso(r),d.perfil(),d.versionPerfil(),d.namespace());}
    private void fijarDentro(RefInterna ref,Reserva r,Detalle d,ContenidoEsperado contenido){
        validar(contenido);String objeto=Idempotencia.huellaExacta(contenido.identidadObjeto());
        if(d.reclamada()==null||contenido.bytes()>d.techo()||!contenido.identidadObjeto().startsWith(d.namespace()+":")
                ||!CategoriaAlmacenamiento.DERIVADA_IA.name().equals(r.categoria())||"CANCELADA".equals(r.estado()))
            throw new ConflictoException("El contenido excede o no corresponde a la previsión reclamada.");
        if(r.bytes()!=null){
            if(r.bytes()!=contenido.bytes()||!Objects.equals(r.contenido(),contenido.huellaContenido())||!Objects.equals(r.objeto(),objeto))
                throw new ConflictoException("El job ya conserva otro contenido final.");return;
        }
        repo.fijarContenido(dueno(ref),ref.reserva(),contenido.bytes(),contenido.huellaContenido(),objeto,ahora());
        presupuestos.antecedente(ref.organizacion(),null,"PREVISION_IA_CONTENIDO_FIJADO",json.objetoCanonico(Map.of("reserva",ref.reserva(),"contenido",contenido)),ahora());
    }
    private static void exigirContenidoRetirado(Reserva r,Detalle d,AusenciaComprobada a){
        if(!"CONFIRMADA".equals(r.estado())||!CategoriaAlmacenamiento.DERIVADA_IA.name().equals(r.categoria())||!d.job().equals(a.job())
                ||r.bytes()==null||r.bytes()!=a.bytes()||!Objects.equals(r.contenido(),a.huellaContenido())
                ||!Objects.equals(r.objeto(),Idempotencia.huellaExacta(a.identidadObjeto()))||!a.identidadObjeto().startsWith(d.namespace()+":"))
            throw new ConflictoException("La retirada no pertenece al objeto IA confirmado.");
    }
    private static Resultado mismoRetiro(RefInterna ref,Presencia p,AusenciaComprobada a,String huella){
        if(!a.retiro().equals(p.retiro())||!huella.equals(p.huellaRetiro()))throw new ConflictoException("El objeto conserva otra retirada verificada.");
        return new Resultado(ref.reserva(),"RETIRADO_VERIFICADO");
    }
    private static void exigirPresente(Optional<Presencia> p){
        if(p.isPresent()&&!"PRESENTE".equals(p.get().estado()))throw new ConflictoException("El binario fue retirado; su metadata no acredita disponibilidad.");
    }
    private static void exigirCapacidadFisica(CapacidadFisica p,long adicional){
        if(adicional<0||adicional>0&&!p.vigente()||sumar(sumar(p.confirmados(),p.reservados()),adicional)>p.limite())
            throw new ReglaNegocioException("No hay capacidad física acreditada para conservar esta salida.");
    }
    private void exigirFisicaConciliada(String namespace,Solicitud propia,UUID duenoPropio,AmbitoIntentos ambito){
        if(repo.existenIntentosFisicosSinCobertura(namespace,duenoPropio,propia==null?null:Idempotencia.huellaExacta(propia.identidadObjetoConocida()),
                propia==null?0:propia.bytes(),propia==null?null:propia.huellaContenido(),ambito)){
            org.slf4j.LoggerFactory.getLogger(UsoAlmacenamientoB0.class).warn("INCERTIDUMBRE_TECNICA: admisión física pendiente de conciliación del diario Core.");
            throw new IncertidumbreFisicaException();
        }
    }
    private LimiteAdmitido admitirComercial(long org,String modalidad,Uso uso,Clasificacion c,long retener,Instant ahora){
        if(retener==0)return new LimiteAdmitido(null,"{}");PlanUso plan=planActual(org,modalidad,ahora);
        if(plan.soloEsencial()&&c.necesidad()==Necesidad.ORDINARIA)throw new CapacidadDeshabilitadaException();
        long base=plan.plan().almacenamientoBytes(),maximo=base;String politica=AlmacenamientoComercial.VERSION_UNIDAD;
        var detalle=new LinkedHashMap<String,Object>();detalle.put("plan",plan.plan());detalle.put("limiteBaseBytes",base);detalle.put("unidad",AlmacenamientoComercial.VERSION_UNIDAD);
        if(c.necesidad()!=Necesidad.ORDINARIA){
            var politicas=repo.politicas(plan.plan().name(),ahora);if(politicas.size()>1)throw new CapacidadDeshabilitadaException();
            if(!politicas.isEmpty()){Politica p=politicas.getFirst();if(p.margen()!=null){maximo=sumar(base,p.margen());politica=p.version();detalle.put("margenFinanciadoBytes",p.margen());detalle.put("versionMargen",p.version());}}
        }
        if(sumar(sumar(uso.confirmados(),uso.reservados()),retener)>maximo)
            throw new ReglaNegocioException("No hay capacidad de almacenamiento disponible para esta carga. Se conserva la lectura y la evidencia existente.");
        return new LimiteAdmitido(politica,json.objetoCanonico(detalle));
    }
    private PlanUso planActual(long org,String modalidad,Instant ahora){
        var actual=presupuestos.cicloVigente(org,ahora);
        if(actual.isPresent())return new PlanUso(PlanBrox.valueOf(actual.get().plan()),actual.get().revocado());
        var ultimo=repo.ultimoPeriodo(org,ahora);
        if("INDIVIDUAL".equals(modalidad))return new PlanUso(PlanBrox.FREE,false);
        if(ultimo.isEmpty())throw new CapacidadDeshabilitadaException();
        if(!"EMPRESARIAL".equals(modalidad))throw new CapacidadDeshabilitadaException();
        return new PlanUso(PlanBrox.valueOf(ultimo.get().plan()),!ahora.isBefore(ultimo.get().fin().plus(Duration.ofDays(30))));
    }
    private Clasificacion clasificar(Solicitud s,Actor a){
        Clasificacion c=clasificador(s.recurso()).clasificar(s,a);validarClasificacion(c,s.recurso());return c;
    }
    private ClasificadorCargaAlmacenamientoB0 clasificador(Recurso r){var candidatos=clasificadores.stream().filter(c->c.atiende(r)).toList();if(candidatos.size()!=1)throw new CapacidadDeshabilitadaException();return candidatos.getFirst();}
    private static void validarClasificacion(Clasificacion c,Recurso r){if(c==null||c.categoria()==null||c.necesidad()==null||!r.huellaOrigen().equals(c.huellaFuente()))throw new ConflictoException("El recurso cambió antes de admitir el archivo.");}
    private String huellaPrevision(PrevisionIa p,Clasificacion c,Actor a){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("prevision",p,"clasificacion",c,"actor",a)));}
    private String huella(Solicitud s,Clasificacion c,Actor a){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",new SolicitudExacta(s.reserva(),s.recurso(),s.bytes(),s.huellaContenido(),s.identidadObjetoConocida()),"clasificacion",c,"actor",a)));}
    private void auditar(long org,UUID reserva,String hecho,Instant fecha){presupuestos.antecedente(org,null,hecho,json.objetoCanonico(Map.of("reserva",reserva)),fecha);}
    private static Resultado resultado(Reserva r){return new Resultado(r.id(),r.estado());}
    private static void misma(Reserva r,String huella,Actor a){
        if(r.persona()!=a.idPersona()||r.rol()!=a.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(!r.peticion().equals(huella))throw new ConflictoException("La reserva ya identifica otro archivo o actuación.");
    }
    private static void mismoContenido(Objeto o,long bytes,String huella){if(o.bytes()!=bytes||!o.contenido().equals(huella))throw new ConflictoException("La identidad del objeto ya conserva otro contenido.");}
    private static long sumar(long a,long b){try{return Math.addExact(a,b);}catch(ArithmeticException ex){throw new ReglaNegocioException("El tamaño excede la precisión admitida.");}}
    private static void validar(Solicitud s){
        if(s==null||s.reserva()==null||s.recurso()==null||s.bytes()<0||!hash(s.huellaContenido())||!texto(s.recurso().tipo(),60)
                ||!texto(s.recurso().referencia(),100)||!texto(s.recurso().finalidad(),100)||!hash(s.recurso().huellaOrigen())
                ||s.identidadObjetoConocida()!=null&&!texto(s.identidadObjetoConocida(),1000)
                ||s.namespaceHash()!=null&&(!hash(s.namespaceHash())||s.identidadObjetoConocida()==null||!s.identidadObjetoConocida().startsWith(s.namespaceHash()+":")))throw new ReglaNegocioException("Identifica archivo, tamaño y versiones exactas.");
    }
    private static void validar(PrevisionIa p){
        if(p==null||p.job()==null||p.recurso()==null||!texto(p.perfil(),60)||!texto(p.versionPerfil(),80)||!hash(p.namespaceHash())
                ||!texto(p.recurso().tipo(),60)||!texto(p.recurso().referencia(),100)||!texto(p.recurso().finalidad(),100)||!hash(p.recurso().huellaOrigen()))
            throw new ReglaNegocioException("Identifica el job, fuente, perfil y namespace reales de la previsión.");
    }
    private static void validar(ContenidoEsperado c){
        if(c==null||!texto(c.identidadObjeto(),1000)||c.bytes()<=0||!hash(c.huellaContenido())||!texto(c.referenciaIntento(),2000))throw new ReglaNegocioException("Conserva el contenido real y el intento esperado.");
    }
    private void validar(NoAlmacenadoComprobado p){
        if(p==null||p.job()==null||!texto(p.referenciaEvidencia(),2000)||!hash(p.huellaEvidencia())||p.comprobadaEn()==null||p.comprobadaEn().isAfter(repo.ahoraDeAlmacenamiento()))
            throw new ReglaNegocioException("Conserva la prueba Core durable de ausencia de almacenamiento.");
    }
    private static void validar(AusenciaComprobada a){
        if(a==null||a.retiro()==null||a.job()==null||a.archivo()==null||a.revision()<1||!texto(a.identidadObjeto(),1000)||a.bytes()<=0||!hash(a.huellaContenido())||!texto(a.referenciaComprobacion(),2000))
            throw new ReglaNegocioException("Conserva intención de retirada, versión y ausencia exacta comprobadas.");
    }
    private static void validarRef(ReferenciaReserva ref){if(ref==null||ref.reserva()==null||ref instanceof RefInterna c&&c.organizacion()<=0||ref instanceof RefEvaluacion v&&v.caso()==null)throw new ReglaNegocioException("Identifica la carga original.");}
    private static void validar(ComprobacionObjeto o){
        if(o==null||!texto(o.identidadObjeto(),1000)||o.bytes()<0||!hash(o.huellaContenido())||!texto(o.referenciaComprobacion(),2000))
            throw new ReglaNegocioException("Conserva la comprobación real del objeto almacenado.");
    }
    private static boolean texto(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max;}
    private static boolean hash(String s){return s!=null&&s.matches("[0-9a-f]{64}");}
    private Instant ahora(){return reloj.instant().truncatedTo(ChronoUnit.MICROS);}

    private UUID duenoOrg(long org){return repo.dueno(org,null);}
    private UUID dueno(ReferenciaReserva ref){return ref instanceof RefInterna c?duenoOrg(c.organizacion()):repo.dueno(null,((RefEvaluacion)ref).caso());}
    public static RefEvaluacion referenciaEvaluacion(UUID caso,UUID comando){
        if(caso==null||comando==null)throw new ReglaNegocioException("Conserva caso y operación.");
        return new RefEvaluacion(caso,UUID.nameUUIDFromBytes(("BROX-PREVISION-EVALUACION\u0000"+caso+"\u0000"+comando).getBytes(StandardCharsets.UTF_8)));
    }
    @Transactional
    public PrevisionEvaluacionAdmitida previsionarEvaluacion(
            com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion ref,String namespace,
            com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        var p=evaluaciones.exigirPlanDeCaso(ref.caso(),sesion,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.ADMITIR);
        var o=presupuestos.evaluacion(ref.caso(),ref.operacion()).orElseThrow(CapacidadDeshabilitadaException::new);
        evaluaciones.exigir(ref.caso(),p.version(),p.huella(),o.autorizacion(),sesion,
            com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.ADMITIR);
        if(!namespace.equals(p.namespace())||!"RESERVADA".equals(o.estado()))throw new ConflictoException("El plan no admite otra previsión.");
        var r=referenciaEvaluacion(ref.caso(),ref.operacion());var previa=repo.reserva(dueno(r),r.reserva());
        if(previa.isPresent())return new PrevisionEvaluacionAdmitida(r,Objects.requireNonNull(detalle(r)).techo());
        var fisica=repo.bloquearCapacidadFisica(namespace,ahora()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(repo.existenIntentosFisicosSinCobertura(namespace,dueno(r),null,0,null,AmbitoIntentos.NAMESPACE))throw new IncertidumbreFisicaException();
        exigirCapacidadFisica(fisica,p.salidaBytes());
        var reserva=new Reserva(r.reserva(),null,null,Idempotencia.huellaExacta(json.objetoCanonico(Map.of("caso",ref.caso(),"operacion",ref.operacion(),"plan",p.huella()))),
            "EVALUACION",ref.caso().toString(),p.finalidad(),p.huella(),CategoriaAlmacenamiento.DERIVADA_IA.name(),
            null,null,null,null,null,json.objetoCanonico(Map.of("versionPlan",p.version(),"huellaPlan",p.huella())),"RESERVADA",null,o.tecnica());
        repo.crearPrevision(dueno(r),reserva,null,null,null,namespace,p.salidaBytes(),fisica.version(),ahora());
        repo.retenerFisica(namespace,p.salidaBytes());return new PrevisionEvaluacionAdmitida(r,p.salidaBytes());
    }
    @Transactional
    public void exigirAntesDeClaimEvaluacion(
            com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion ref,
            com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        var p=evaluaciones.exigirPlanDeCaso(ref.caso(),sesion,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR);
        var o=presupuestos.evaluacion(ref.caso(),ref.operacion()).orElseThrow(CapacidadDeshabilitadaException::new);
        evaluaciones.exigir(ref.caso(),p.version(),p.huella(),o.autorizacion(),sesion,
            com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR);
        var r=referenciaEvaluacion(ref.caso(),ref.operacion());var reserva=bloquear(r);var d=detalle(r);
        if(d==null||!d.prevision()||!Objects.equals(reserva.operacionB0(),o.tecnica())||reserva.bytes()!=null
            ||!"RESERVADA".equals(reserva.estado())||!p.namespace().equals(d.namespace())||d.techo()!=p.salidaBytes())
            throw new ConflictoException("La previsión conserva otro claim.");
        if(d.reclamada()==null)repo.reclamarPrevision(dueno(r),r.reserva(),repo.ahoraDeAlmacenamiento());
    }
    /** Dato sólo servidor. No crea ni amplía presupuesto o cuota. */
    @Transactional(readOnly=true)
    public LimiteRecepcion limiteRecepcion(ReferenciaReserva ref){
        var r=leer(ref);var d=detalle(ref);
        if(d==null||!d.prevision()||d.techo()==null||d.techo()<=0
            ||!Set.of("RESERVADA","ALMACENANDO","INCIERTA","CONFIRMADA").contains(r.estado()))
            throw new CapacidadDeshabilitadaException();
        return new LimiteRecepcion(ref,d.techo(),d.versionPerfil()==null?json.comoMapa(r.contextoLimite()).get("versionPlan").toString():d.versionPerfil(),r.origen(),d.namespace());
    }
    @Transactional
    public Resultado reservarOriginalEvaluacion(UUID caso,UUID archivo,long bytes,String huella,String namespace,
            String identidadObjeto,com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        var p=evaluaciones.exigirOriginal(caso,archivo,bytes,huella,sesion);
        if(!p.namespace().equals(namespace)||identidadObjeto==null||!identidadObjeto.startsWith(namespace+":"))throw new ConflictoException("El original requiere su namespace.");
        var ref=new RefEvaluacion(caso,archivo);String hash=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("caso",caso,"archivo",archivo,"bytes",bytes,"huella",huella,"objeto",identidadObjeto)));
        var f=repo.bloquearCapacidadFisica(namespace,ahora()).orElseThrow(CapacidadDeshabilitadaException::new);
        var previa=repo.reserva(dueno(ref),archivo);
        if(previa.isPresent()){if(!previa.get().peticion().equals(hash))throw new ConflictoException("El original conserva otro contenido.");return resultado(previa.get());}
        if(repo.existenIntentosFisicosSinCobertura(namespace,dueno(ref),Idempotencia.huellaExacta(identidadObjeto),bytes,huella,AmbitoIntentos.OPERACION_PROPIA))throw new IncertidumbreFisicaException();
        exigirCapacidadFisica(f,bytes);
        var r=new Reserva(archivo,null,null,hash,"EVALUACION_ORIGINAL",caso.toString(),p.finalidad(),p.huella(),
            CategoriaAlmacenamiento.ORIGINAL.name(),null,bytes,huella,null,null,json.objetoCanonico(Map.of("namespace",namespace,"versionFisica",f.version())),"RESERVADA",Idempotencia.huellaExacta(identidadObjeto),null);
        repo.crearReserva(dueno(ref),r,ahora());repo.asociarFisica(dueno(ref),archivo,namespace,f.version(),bytes);repo.retenerFisica(namespace,bytes);
        if(!repo.iniciar(dueno(ref),archivo))throw new ConflictoException("El original cambió antes de almacenarse.");
        return new Resultado(archivo,"ALMACENANDO");
    }
    /** Sólo callback del claim original y custodia ya comprometida; no permite otra llamada. */
    @Transactional
    public Resultado admitirRecepcionEvaluacion(RefEvaluacion ref,UUID operacionB0,UUID archivo,
            UUID operacionIo,int numero,Solicitud s){
        validar(s);var r=bloquear(ref);var d=detalle(ref);
        if(r.operacionB0()==null||!r.operacionB0().equals(operacionB0)||d==null||d.reclamada()==null
            ||!d.namespace().equals(s.namespaceHash()))throw new ConflictoException("La recepción conserva otro claim.");
        evaluaciones.exigirCustodia(ref.caso(),operacionB0,archivo,operacionIo,numero,s.huellaContenido(),s.bytes());
        if(r.bytes()==null)fijarDentroEvaluacion(ref,r,d,s);
        else if(!r.bytes().equals(s.bytes())||!r.contenido().equals(s.huellaContenido())||!r.objeto().equals(Idempotencia.huellaExacta(s.identidadObjetoConocida())))
            throw new ConflictoException("La recepción conserva otro contenido.");
        if("RESERVADA".equals(r.estado()))repo.iniciar(dueno(ref),ref.reserva());
        return resultado(leer(ref));
    }
    private void fijarDentroEvaluacion(RefEvaluacion ref,Reserva r,Detalle d,Solicitud s){
        if(s.bytes()>d.techo()||s.bytes()<=0||s.identidadObjetoConocida()==null||!s.identidadObjetoConocida().startsWith(d.namespace()+":"))
            throw new ConflictoException("La salida excede su previsión acreditada.");
        repo.fijarContenido(dueno(ref),ref.reserva(),s.bytes(),s.huellaContenido(),Idempotencia.huellaExacta(s.identidadObjetoConocida()),ahora());
    }

    @Transactional(readOnly=true)
    public Solicitud solicitudRecepcionEvaluacion(RefEvaluacion ref,long bytes,String huella,String identidad,String namespace){
        var r=leer(ref);return new Solicitud(ref.reserva(),recurso(r),bytes,huella,identidad,namespace);
    }

    /** Sólo libera la previsión sin writer del mismo cierre B0 comprobado; no usa un booleano cliente. */
    @Transactional
    public void cancelarEvaluacionNoAlmacenada(
        com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion ref,String huellaCierre){
        var o=presupuestos.evaluacion(ref.caso(),ref.operacion()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!"CANCELADA".equals(o.estado())||!Objects.equals(huellaCierre,o.resultado()))throw new ConflictoException("Falta la resolución B0 original.");
        var rref=referenciaEvaluacion(ref.caso(),ref.operacion());var r=bloquear(rref);var d=detalle(rref);
        if("CANCELADA".equals(r.estado()))return;
        if(r.bytes()!=null||r.objeto()!=null||evaluaciones.tieneArchivoOperacion(o.tecnica()))
            throw new ConflictoException("El writer exige recuperación y ausencia comprobada.");
        repo.contabilizarFisica(d.namespace(),d.retenidosFisicos(),0);
        repo.cancelarPrevision(dueno(rref),r.id(),huellaCierre,ahora());
    }

    /** Sólo después del commit financiero; el propietario conserva IO antes de entrar a P/reserva/B0. */
    @Transactional
    public void liberarEvaluacionSinBinario(com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion ref,
            com.controllocal.service.presupuesto.PresupuestoIaService.RespuestaEvaluacion respuesta){
        var descriptor=respuesta.descriptor();if(descriptor==null)throw new ConflictoException("Falta el descriptor del claim.");
        seguimientoEvaluacion.exigirSinIntento(new com.controllocal.service.archivo.PropietarioBinario.CasoEvaluacion(ref.caso()),descriptor.operacionIo());
        var rref=referenciaEvaluacion(ref.caso(),ref.operacion());var r=bloquear(rref);var d=detalle(rref);
        var o=presupuestos.evaluacion(ref.caso(),ref.operacion()).orElseThrow(CapacidadDeshabilitadaException::new);
        presupuestos.bloquearOperacion(null,o.tecnica());o=presupuestos.evaluacion(ref.caso(),ref.operacion()).orElseThrow();
        if(!"LIQUIDADA".equals(o.estado())||o.resultado()==null)throw new ConflictoException("Falta el cierre financiero definitivo.");
        var originales=presupuestos.respuestasEvaluacion(o.tecnica()).stream().map(x->json.comoObjeto(x,com.controllocal.service.presupuesto.PresupuestoIaService.RespuestaEvaluacion.class)).filter(x->x.etapa()==null).toList();
        if(originales.size()!=1||!json.objetoCanonico(originales.getFirst()).equals(json.objetoCanonico(respuesta)))throw new ConflictoException("La liberación exige la respuesta original.");
        evaluaciones.exigirSalidaSinBinario(ref,respuesta);
        if(evaluaciones.tieneArchivoOperacion(o.tecnica())||r.bytes()!=null||r.objeto()!=null||d==null
                ||!d.prevision()||!o.tecnica().equals(r.operacionB0()))throw new ConflictoException("La custodia o presencia exige recuperación.");
        String prueba=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("hecho","SALIDA_SIN_BINARIO","caso",ref.caso(),"operacion",ref.operacion(),
                "respuesta",Idempotencia.huellaExacta(json.objetoCanonico(respuesta)),"cierre",o.resultado(),"reserva",r.id())));
        if("CANCELADA".equals(r.estado())){if(!prueba.equals(d.liberacion()))throw new ConflictoException("La reserva conserva otra liberación.");return;}
        if(!"RESERVADA".equals(r.estado()))throw new ConflictoException("La previsión conserva una recepción que requiere su propia recuperación.");
        repo.contabilizarFisica(d.namespace(),d.retenidosFisicos(),0);repo.cancelarPrevision(dueno(rref),r.id(),prueba,ahora());
    }
    @Transactional
    public void bloquearHechoEvaluacion(com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion ref){
        bloquear(referenciaEvaluacion(ref.caso(),ref.operacion()));
    }
}
