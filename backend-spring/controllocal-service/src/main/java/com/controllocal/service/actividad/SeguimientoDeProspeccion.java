package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.SeguimientoProspeccionRepository;
import com.controllocal.persistence.repositorio.SeguimientoProspeccionRepository.*;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.ProspeccionService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

/** CAT-R27 §20.1. Una proyección privada por destinatario/finalidad; nunca envía al propietario. */
@Component
public class SeguimientoDeProspeccion {
    public static final String FINALIDAD="RECONTACTAR_PROPIETARIO",VERSION="CAT-R27-20261030-1";
    private static final List<String> RELACIONES_DE_RECONTACTO=List.of("PROPIETARIO","REPRESENTANTE","GESTOR_COMERCIAL");
    public static List<String> relacionesDeclaradasDeRecontacto(){return RELACIONES_DE_RECONTACTO;}
    private static boolean relacionDeRecontacto(String tipo){String valor=codigo(tipo);return valor!=null&&RELACIONES_DE_RECONTACTO.contains(valor);}
    private static final List<Integer> DIAS=List.of(2,5,10);
    /** Proyección sin nombres, teléfonos, notas o declaraciones: no es otra comunicación. */
    public record HechoR27(long prospeccion,String destinatario,ContactoOriginal origen,boolean elegible,
            boolean canalConocido,boolean salienteSinRespuesta,boolean respuestaConDestinatario,
            boolean resultadoPorPrecisar,LocalDate fecha,boolean proximaDeclarada,OrigenActuacion origenProxima,
            LocalDate proxima,boolean restringido,boolean alcancePorPrecisar,boolean destinatarioPorPrecisar,boolean intentoSaliente,
            String sustituyeDestinatario) {
        public HechoR27(long prospeccion,String destinatario,ContactoOriginal origen,boolean elegible,
                boolean canalConocido,boolean salienteSinRespuesta,boolean respuestaConDestinatario,
                boolean resultadoPorPrecisar,LocalDate fecha,boolean proximaDeclarada,OrigenActuacion origenProxima,
                LocalDate proxima,boolean restringido,boolean alcancePorPrecisar,boolean destinatarioPorPrecisar,boolean intentoSaliente){
            this(prospeccion,destinatario,origen,elegible,canalConocido,salienteSinRespuesta,respuestaConDestinatario,
                    resultadoPorPrecisar,fecha,proximaDeclarada,origenProxima,proxima,restringido,alcancePorPrecisar,destinatarioPorPrecisar,intentoSaliente,null);
        }
    }
    public record DestinoLiteral(DestinatarioDeclarado declarado,String referencia) { }
    public record Proyecciones(List<HechoR27> hechos,DestinoLiteral literal) { }
    public record ContextoEstado(ContactoOriginal destinatarioOriginal,String causa,boolean revisarContinuidad,
            boolean faltaFecha,boolean faltaContacto,boolean restringido,boolean alcancePorPrecisar,
            OrigenActuacion origenExplicito,LocalDate fechaPlanificada,String detallePlanificacion,
            Instant planificadoEn,String motivoPosposicion,long revisionPlanMaterial,
            DestinatarioDeclarado declarado,String referenciaDeclarada) { }
    public record DestinoConstancia(long prospeccion,String clave,String referenciaDeclarada,ContactoOriginal contactoOriginal) { }
    public record FuentesConstancia(List<ReferenciaVersion> comunicaciones,DestinoConstancia destino,long rolRegistrador) {
        public FuentesConstancia {comunicaciones=List.copyOf(comunicaciones);}
    }
    public record Entrada(long interaccion,long version,Instant registrado,HechoR27 hecho) { }
    public record Necesidad(RevisionSeguimiento revision,String marcaCausa,long responsableActual) { }
    private record Legible(Fila fila,Contexto actual,ContextoEstado contexto) { }
    public record Calculo(Long inicial,Long ultimo,Long versionUltimo,LocalDate fechaUltimo,int recontactos,
            LocalDate proxima,String origen,ContextoEstado contexto,boolean necesidad) { }
    private final SeguimientoProspeccionRepository estados;
    private final ContextosDeActividad contexto;
    private final ContactosDeInteraccion contactos;
    private final Alcances alcances;
    private final Documentos documentos;
    private final ContextosDeInteraccion acceso;
    public SeguimientoDeProspeccion(SeguimientoProspeccionRepository estados,ContextosDeActividad contexto,
            ContactosDeInteraccion contactos,Alcances alcances,Documentos documentos,ContextosDeInteraccion acceso){
        this.estados=estados;this.contexto=contexto;this.contactos=contactos;this.alcances=alcances;this.documentos=documentos;this.acceso=acceso;
    }
    public Normalizado completarDestinoDeclarado(Normalizado n,Actor actor){
        var d=n.datos();var p=d.destinatario();if(p==null||vacio(p.referenciaDeclarada()))return n;
        var candidatas=new ArrayList<Legible>();String clave="DECLARADO:"+Idempotencia.huellaExacta(p.referenciaDeclarada());
        for(var v:lista(d.vinculos()))if(v.tipo()==TipoContexto.PROSPECCION){var padre=exigirContexto(v.referencia(),actor,false);
            declaracionAutorizada(padre,clave,actor).ifPresent(candidatas::add);}
        if(candidatas.isEmpty()&&!p.referenciaDeclarada().startsWith("R27:"))return n;
        if(candidatas.size()!=1)throw new NoEncontradoException("Destinatario declarado de esta prospección");
        var c=candidatas.getFirst().contexto();var origen=c.declarado();
        if(origen==null||!p.referenciaDeclarada().equals(c.referenciaDeclarada()))throw new NoEncontradoException("Destinatario declarado");
        var completo=new ParticipanteDeclarado(null,p.tipoDeclarado()==null?origen.tipo():p.tipoDeclarado(),p.nombre()==null?origen.nombre():p.nombre(),p.telefono()==null?origen.telefono():p.telefono(),p.referenciaDeclarada());
        return new Normalizado(new DatosInteraccion(d.vinculos(),completo,d.interlocutor(),d.tipo(),d.canal(),d.medioReal(),d.eventos(),d.resultado(),d.momento(),d.resumen(),d.declaraciones(),d.proxima(),d.interaccionAnterior(),d.fuente(),d.restriccion(),d.gestionContacto(),d.atencionConsultas()),n.autorizacion(),n.vinculos());
    }
    private Optional<Legible> declaracionAutorizada(Contexto p,String clave,Actor actor){
        var fila=estados.porDestino(actor.idOrganizacion(),p.id(),clave).or(()->estados.ultimoEstado(actor.idOrganizacion(),p.id(),clave)
            .flatMap(id->estados.porIdHistorico(actor.idOrganizacion(),id)));
        if(fila.isEmpty())return Optional.empty();var f=fila.get();
        if(f.prospeccion()!=p.id()||!f.destinatario().equals(clave))throw new IllegalStateException("La declaración no corresponde a su contexto.");
        if(fuentesLegibles(List.of(new Legible(f,p,null)),actor).isEmpty())return Optional.empty();
        var c=documentos.comoObjeto(f.contextoJson(),ContextoEstado.class);exigirDestinatario(c,actor);
        return Optional.of(new Legible(f,p,c)); // Lectura de declaración; no reactiva una cadencia ni cambia el hecho real.
    }
    /** Identificador contextual técnico de este destinatario declarado, sin crear Persona ni comparar naturales. */
    public static Normalizado prepararDestinoDeclarado(Normalizado n){
        var d=n.datos();var p=d.destinatario();
        var declarado=identificarLiteral(p);var interlocutor=d.interlocutor();
        if(d.gestionContacto()!=null&&d.gestionContacto().continuarConInterlocutor())interlocutor=identificarLiteral(interlocutor);
        return new Normalizado(new DatosInteraccion(d.vinculos(),declarado,interlocutor,d.tipo(),d.canal(),d.medioReal(),d.eventos(),d.resultado(),d.momento(),d.resumen(),d.declaraciones(),d.proxima(),d.interaccionAnterior(),d.fuente(),d.restriccion(),d.gestionContacto(),d.atencionConsultas()),n.autorizacion(),n.vinculos());
    }
    private static ParticipanteDeclarado identificarLiteral(ParticipanteDeclarado p){
        return p==null||p.contacto()!=null||!vacio(p.referenciaDeclarada())?p:new ParticipanteDeclarado(null,p.tipoDeclarado(),p.nombre(),p.telefono(),"DECLARADO:"+UUID.randomUUID());
    }
    public List<HechoR27> proyectar(Normalizado n,Actor actor){
        var d=n.datos();var destino=d.destinatario();var original=n.autorizacion().contactos().get("destinatario");
        var gestion=d.gestionContacto();
        if(gestion!=null&&!ContactoEfectivoProspeccion.comercial(d)&&d.restriccion()==null)return List.of();
        String sustituye=null;
        if(gestion!=null&&gestion.continuarConInterlocutor()){
            sustituye=clave(destino,original);destino=d.interlocutor();original=n.autorizacion().contactos().get("interlocutor");
        }
        String clave=clave(destino,original);String rol=destino==null?null:codigo(destino.tipoDeclarado());
        boolean continua=gestion!=null&&gestion.continuarConInterlocutor();
        boolean elegible=relacionDeRecontacto(rol)||continua;
        boolean mismo=continua||ContactoEfectivoProspeccion.mismoInterlocutor(n);
        boolean saliente=lista(d.eventos()).stream().anyMatch(e->e.evento()==EventoComunicacion.LLAMADA_SALIENTE);
        LocalDate fecha=lista(d.eventos()).stream().filter(e->e.evento()==EventoComunicacion.LLAMADA_SALIENTE)
                .map(e->MomentosDeComunicacion.local(e.momento())).filter(Objects::nonNull).findFirst().orElse(MomentosDeComunicacion.efectiva(d));
        boolean respuesta=mismo&&(d.resultado()==ResultadoInteraccion.INTERCAMBIO_EFECTIVO||d.resultado()==ResultadoInteraccion.CONVERSACION_RECHAZADA||continua&&gestion.respondeAIntento()!=null);
        if(Objects.equals(sustituye,clave))sustituye=null;
        var restriccion=d.restriccion();boolean restringido=false,precisar=false;String claveRestringida=null;ContactoOriginal origenRestriccion=null;
        if(restriccion!=null){
            var r=n.autorizacion().contactos().get("restriccion");origenRestriccion=r;
            boolean finalidad=FINALIDAD.equals(codigo(restriccion.finalidad()));
            if(r!=null&&r.persona()!=null)claveRestringida="PERSONA:"+r.persona();
            restringido=finalidad&&Objects.equals(claveRestringida,clave)&&claveRestringida!=null;
            precisar=!restringido&&(vacio(restriccion.finalidad())||restriccion.destinatario()==null);
        }
        if(clave==null&&claveRestringida!=null){clave=claveRestringida;original=origenRestriccion;restringido=FINALIDAD.equals(codigo(restriccion.finalidad()));precisar=vacio(restriccion.finalidad());}
        if(clave==null&&restriccion!=null){clave="ALCANCE_POR_PRECISAR";precisar=true;}
        if(clave==null)return List.of();
        boolean canal=destino!=null&&!vacio(destino.telefono())||original!=null&&original.canalConocido();
        var resultado=new ArrayList<HechoR27>();
        for(var v:lista(d.vinculos()))if(v.tipo()==TipoContexto.PROSPECCION&&v.referencia()!=null){
            boolean destinoPorPrecisar=destino!=null&&(original==null||original.persona()==null)&&vacio(destino.referenciaDeclarada());
            boolean destinoElegible=elegible&&!destinoPorPrecisar;
            if(!destinoElegible&&destino!=null&&original!=null&&original.referencia().tipo()==TipoContacto.PROPIETARIO){
                var p=estados.contexto(actor.idOrganizacion(),v.referencia(),false);
                destinoElegible=p.isPresent()&&estados.propietario(actor.idOrganizacion(),p.get().propiedad()).filter(original.referencia().referencia()::equals).isPresent();
            }
            resultado.add(new HechoR27(v.referencia(),clave,original,destinoElegible,canal,destinoElegible&&saliente&&d.resultado()==ResultadoInteraccion.SIN_RESPUESTA,
                    respuesta,saliente&&d.resultado()==null,fecha,d.proxima()!=null,d.proxima()==null?null:d.proxima().origen(),d.proxima()==null?null:d.proxima().fecha(),restringido,precisar,destinoPorPrecisar,saliente,sustituye));
            if(claveRestringida!=null&&!claveRestringida.equals(clave)
                    &&(FINALIDAD.equals(codigo(restriccion.finalidad()))||vacio(restriccion.finalidad())))
                resultado.add(new HechoR27(v.referencia(),claveRestringida,origenRestriccion,false,false,false,false,false,fecha,
                        false,null,null,FINALIDAD.equals(codigo(restriccion.finalidad())),vacio(restriccion.finalidad()),false,false));
        }
        return resultado.stream().distinct().toList();
    }
    /** El padre ya se autorizó; el bloqueo serializa dos llamadas de una misma prospección. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void actualizarDesde(Set<Long> prospecciones,Actor actor){
        for(long id:new TreeSet<>(prospecciones)){
            var p=exigirContexto(id,actor,true);var actuales=estados.activas(actor.idOrganizacion(),id);
            if(p.encargoIniciado()&&!p.retirado())estados.marcarRetiro(actor.idOrganizacion(),id,contexto.ahora());
            if(!vigente(p)){for(var s:actuales)estados.retirar(actor.idOrganizacion(),s.id(),contexto.ahora(),null);continue;}
            var porDestino=new LinkedHashMap<String,List<Entrada>>();var sustituidos=new HashMap<String,String>();var literales=new HashMap<String,DestinoLiteral>();
            for(var f:estados.hechos(actor.idOrganizacion(),id)){
                var proyeccion=documentos.comoObjeto(f.proyeccionJson(),Proyecciones.class);
                for(var h:lista(proyeccion.hechos()))
                if(h.prospeccion()==id){
                    porDestino.computeIfAbsent(h.destinatario(),k->new ArrayList<>()).add(new Entrada(f.id(),f.version(),f.registrado(),h));
                    if(h.sustituyeDestinatario()!=null){sustituidos.remove(h.destinatario());sustituidos.put(h.sustituyeDestinatario(),h.destinatario());}
                    var literal=proyeccion.literal();
                    if(literal!=null&&!vacio(literal.referencia())&&h.destinatario().equals("DECLARADO:"+Idempotencia.huellaExacta(literal.referencia())))literales.put(h.destinatario(),literal);
                }
            }
            sustituidos.keySet().forEach(porDestino::remove);
            for(var s:actuales)if(sustituidos.containsKey(s.destinatario())){
                var previo=documentos.comoObjeto(s.contextoJson(),ContextoEstado.class);
                if(previo.alcancePorPrecisar())throw new ReglaNegocioException("La selección de otro contacto no resuelve el alcance pendiente de una restricción.");
            }
            var anteriores=new HashMap<String,Fila>();actuales.forEach(s->anteriores.put(s.destinatario(),s));
            for(var e:porDestino.entrySet()){
                var anterior=anteriores.remove(e.getKey());var base=anterior==null?null:documentos.comoObjeto(anterior.contextoJson(),ContextoEstado.class);
                if(base==null){var conocida=declaracionAutorizada(p,e.getKey(),actor);if(conocida.isPresent()){
                    var d=conocida.get().contexto();base=new ContextoEstado(null,"PROXIMA_ACTUACION",false,false,false,false,false,null,null,null,null,null,0,d.declarado(),d.referenciaDeclarada());}}
                if(literales.containsKey(e.getKey()))base=conLiteral(base,literales.get(e.getKey()));
                var calculo=calcular(e.getValue(),base,contexto.hoy());guardar(actor,p,e.getKey(),anterior,calculo,e.getValue().stream().map(h->new ReferenciaVersion(h.interaccion(),h.version())).distinct().toList());
            }
            for(var s:anteriores.values())if(s.inicial()!=null||sustituidos.containsKey(s.destinatario())||"POR_PRECISAR".equals(s.destinatario())&&!porDestino.isEmpty())estados.retirar(actor.idOrganizacion(),s.id(),contexto.ahora(),null);
        }
    }
    public static Calculo calcular(List<Entrada> originales,ContextoEstado anterior,LocalDate hoy){
        Objects.requireNonNull(hoy,"Fecha Core requerida");
        var entradas=originales.stream().sorted(Comparator.comparing((Entrada e)->e.hecho().fecha(),Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Entrada::registrado,Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(Entrada::interaccion)).toList();
        Long inicial=null,ultimo=null,vUltimo=null;LocalDate fecha=null,proxima=null;int intentos=0;String origen="SIN_FECHA";
        boolean revisar=false,faltaFecha=false,faltaContacto=false,restringido=false,precisar=false,necesidad=false,resultadoDesconocido=false,esperaExplicita=false,destinoPorPrecisar=false;
        ContactoOriginal destinatario=null;OrigenActuacion explicito=null;
        boolean respuestaConocida=false,intentoConocido=false,canalDeclarado=anterior!=null&&canal(anterior.declarado()),elegible=false;
        for(var e:entradas){var h=e.hecho();if(!h.elegible()&&!h.restringido()&&!h.alcancePorPrecisar()&&!h.destinatarioPorPrecisar())continue;
            respuestaConocida|=h.respuestaConDestinatario();intentoConocido|=h.intentoSaliente();canalDeclarado|=h.canalConocido();elegible|=h.elegible();
            destinoPorPrecisar|=h.destinatarioPorPrecisar();if(h.destinatarioPorPrecisar())necesidad=true;
            if(h.origen()!=null)destinatario=h.origen();faltaContacto=!h.canalConocido()&&!(anterior!=null&&canal(anterior.declarado()));
            restringido|=h.restringido();precisar|=h.alcancePorPrecisar();
            if(h.respuestaConDestinatario()){intentos=0;inicial=null;ultimo=null;vUltimo=null;fecha=null;proxima=null;revisar=false;faltaFecha=false;necesidad=false;resultadoDesconocido=false;esperaExplicita=false;origen="SIN_FECHA";}
            if(h.salienteSinRespuesta()){
                if(inicial==null)inicial=e.interaccion();ultimo=e.interaccion();vUltimo=e.version();fecha=h.fecha();intentos++;necesidad=true;
                faltaFecha|=fecha==null;revisar=intentos>=4;
                boolean explicitaVigente=esperaExplicita||!"CADENCIA".equals(origen)&&proxima!=null&&(fecha==null||proxima.isAfter(fecha));
                if(!explicitaVigente){proxima=faltaFecha?null:revisar?fecha:fecha.plusDays(DIAS.get(intentos-1));origen="CADENCIA";}
            }
            if(h.resultadoPorPrecisar()){resultadoDesconocido=true;necesidad=true;proxima=null;origen="SIN_FECHA";}
            if(h.proximaDeclarada()){explicito=h.origenProxima();proxima=h.proxima();esperaExplicita=proxima==null;origen=proxima==null?"SIN_FECHA":explicito==OrigenActuacion.ACUERDO_EXTERNO?"ACORDADA":"EXPLICITA";necesidad=true;resultadoDesconocido=false;}
        }
        if(elegible&&canalDeclarado&&!respuestaConocida&&!intentoConocido&&!necesidad){necesidad=true;proxima=hoy;origen="CADENCIA";}
        if(elegible&&intentoConocido&&!respuestaConocida&&!necesidad)necesidad=true;
        if(anterior!=null&&anterior.planificadoEn()!=null){
            LocalDate planDia=anterior.planificadoEn().atZone(ZoneId.of("America/Lima")).toLocalDate();
            boolean posterior=entradas.stream().anyMatch(e->e.hecho().proximaDeclarada()&&e.hecho().fecha()!=null&&!e.hecho().fecha().isBefore(planDia)
                    &&e.registrado()!=null&&e.registrado().isAfter(anterior.planificadoEn()));
            if(!posterior){explicito=anterior.origenExplicito();proxima=anterior.fechaPlanificada();origen=proxima==null?"SIN_FECHA":explicito==OrigenActuacion.ACUERDO_EXTERNO?"ACORDADA":"EXPLICITA";necesidad=true;}
        }
        if(restringido||precisar){proxima=null;necesidad=true;origen="SIN_FECHA";}
        if(faltaContacto&&"CADENCIA".equals(origen))proxima=null;
        String causa=restringido?"RESTRICCION_CONTACTO":precisar?"PRECISAR_ALCANCE_RESTRICCION":destinoPorPrecisar?"PRECISAR_DESTINATARIO":resultadoDesconocido?"PRECISAR_RESULTADO":faltaFecha?"PRECISAR_FECHA":revisar?"REVISAR_CONTINUIDAD":faltaContacto?"OBTENER_CONTACTO":!"CADENCIA".equals(origen)?"PROXIMA_ACTUACION":inicial==null?"CONTACTO_INICIAL":"CADENCIA";
        var c=new ContextoEstado(destinatario,causa,revisar,faltaFecha,faltaContacto,restringido,precisar,explicito,
                anterior==null?null:anterior.fechaPlanificada(),anterior==null?null:anterior.detallePlanificacion(),anterior==null?null:anterior.planificadoEn(),anterior==null?null:anterior.motivoPosposicion(),anterior==null?0:anterior.revisionPlanMaterial(),anterior==null?null:anterior.declarado(),anterior==null?null:anterior.referenciaDeclarada());
        return new Calculo(inicial,ultimo,vUltimo,fecha,Math.max(0,Math.min(3,intentos-1)),proxima,origen,c,necesidad);
    }
    private static ContextoEstado conLiteral(ContextoEstado c,DestinoLiteral d){
        if(c==null)return new ContextoEstado(null,"PROXIMA_ACTUACION",false,false,false,false,false,null,null,null,null,null,0,d.declarado(),d.referencia());
        return new ContextoEstado(c.destinatarioOriginal(),c.causa(),c.revisarContinuidad(),c.faltaFecha(),c.faltaContacto(),c.restringido(),c.alcancePorPrecisar(),c.origenExplicito(),c.fechaPlanificada(),c.detallePlanificacion(),c.planificadoEn(),c.motivoPosposicion(),c.revisionPlanMaterial(),d.declarado(),d.referencia());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void iniciar(long id,DestinatarioDeclarado declarado,Actor actor){
        var p=exigirContexto(id,actor,true);if(p.encargoIniciado()&&!p.retirado())estados.marcarRetiro(actor.idOrganizacion(),id,contexto.ahora());
        if(!vigente(p)||!estados.activas(actor.idOrganizacion(),id).isEmpty())return;
        if(declarado!=null){
            String referencia="R27:"+UUID.randomUUID();String tipo=codigo(declarado.tipo());
            boolean elegible=relacionDeRecontacto(tipo),disponible=elegible&&canal(declarado);
            var c=new ContextoEstado(null,!elegible?"PRECISAR_RELACION":disponible?"CONTACTO_INICIAL":"OBTENER_CONTACTO",false,false,!canal(declarado),false,false,null,null,null,null,null,0,declarado,referencia);
            var f=estados.insertar(actor.idOrganizacion(),id,"DECLARADO:"+Idempotencia.huellaExacta(referencia),p.responsable(),null,null,null,null,0,disponible?contexto.hoy():null,disponible?"CADENCIA":"SIN_FECHA",null,null,documentos.objetoCanonico(c),contexto.ahora(),null,FINALIDAD);
            constancia(f,c,List.of(),actor);return;
        }
        var titular=estados.propietario(actor.idOrganizacion(),p.propiedad());
        if(titular.isEmpty()){inicialPorPrecisar(p,actor,"OBTENER_CONTACTO");return;}
        var ref=new ReferenciaContactoContextual(TipoContacto.PROPIETARIO,titular.get(),null,null);
        ContactosDeInteraccion.Resuelto r;
        try{r=contactos.resolver(ref,actor,null,false);}catch(NoEncontradoException|AccesoNoAutorizadoException denegada){inicialPorPrecisar(p,actor,"PERMISO_CONTACTO");return;}
        boolean canal=!vacio(r.persona().getTelefono())||!vacio(r.persona().getCorreo());
        var original=new ContactoOriginal(ref,r.persona().getId(),canal);
        var c=new ContextoEstado(original,canal?"CONTACTO_INICIAL":"OBTENER_CONTACTO",false,false,!canal,false,false,null,null,null,null,null,0,null,null);
        var permitidas=restricciones(List.of(new Legible(null,p,c)),actor);
        if(permitidas.isEmpty()){inicialPorPrecisar(p,actor,"PERMISO_CONTACTO");return;}c=permitidas.getFirst().contexto();
        boolean disponible=canal&&!c.restringido()&&!c.alcancePorPrecisar();
        var f=estados.insertar(actor.idOrganizacion(),id,"PERSONA:"+r.persona().getId(),p.responsable(),null,null,null,null,0,disponible?contexto.hoy():null,disponible?"CADENCIA":"SIN_FECHA",null,null,documentos.objetoCanonico(c),contexto.ahora(),null,FINALIDAD);constancia(f,c,List.of(),actor);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void encargoIniciado(long idEncargo,Actor actor){
        contexto.exigirAgente(actor);if(idEncargo<=0)throw new ReglaNegocioException("Identifica el Encargo iniciado.");
        for(long id:estados.retirarPorEncargo(actor.idOrganizacion(),idEncargo,actor.idRolOperativo(),contexto.ahora()))
            for(var f:estados.activas(actor.idOrganizacion(),id))estados.retirar(actor.idOrganizacion(),f.id(),contexto.ahora(),null);
    }
    private void inicialPorPrecisar(Contexto p,Actor actor,String causa){
        // Es una revisión del registro, no una Persona ni una llamada inventada.
        var c=new ContextoEstado(null,causa,false,false,true,false,false,null,null,null,null,null,0,null,null);
        var f=estados.insertar(actor.idOrganizacion(),p.id(),"POR_PRECISAR",p.responsable(),null,null,null,null,0,null,"SIN_FECHA",null,null,documentos.objetoCanonico(c),contexto.ahora(),null,FINALIDAD);constancia(f,c,List.of(),actor);
    }
    @Transactional(readOnly=true)
    public List<RevisionSeguimiento> revisar(long id,Actor actor){
        exigirContexto(id,actor,false);return legibles(estados.activas(actor.idOrganizacion(),id),actor).stream().map(e->revision(e.fila(),e.contexto())).toList();
    }
    @Transactional(readOnly=true)
    public List<RevisionSeguimiento> enAlcance(Actor actor){
        contexto.exigirOperador(actor);return legibles(estados.enAlcance(actor.idOrganizacion(),alcances.de(actor).rolesAgente()),actor).stream().map(e->revision(e.fila(),e.contexto())).toList();
    }
    @Transactional(readOnly=true)
    public List<Necesidad> necesidadesActuales(Actor actor){
        contexto.exigirOperador(actor);return legibles(estados.enAlcance(actor.idOrganizacion(),alcances.de(actor).rolesAgente()),actor).stream()
                .map(e->new Necesidad(revision(e.fila(),e.contexto()),marca(e.fila(),e.contexto()),e.actual().responsable())).toList();
    }
    /** Sólo para mantener una proyección oculta durante revocación/posposición; no entrega datos. */
    @Transactional(readOnly=true)
    public Set<String> marcasActuales(Actor actor){
        contexto.exigirOperador(actor);var salida=new HashSet<String>();
        var filas=estados.enAlcance(actor.idOrganizacion(),alcances.de(actor).rolesAgente());var actuales=new HashMap<Long,ContextoEstado>();
        legibles(filas,actor).forEach(e->actuales.put(e.fila().id(),e.contexto()));
        for(var f:filas)salida.add(marca(f,actuales.getOrDefault(f.id(),documentos.comoObjeto(f.contextoJson(),ContextoEstado.class))));return Set.copyOf(salida);
    }
    @Transactional(readOnly=true)
    public List<Necesidad> necesidadesVencidas(Actor actor){return necesidadesActuales(actor).stream().filter(n->corresponde(n.revision())).toList();}
    private boolean corresponde(RevisionSeguimiento r){return !r.restringido()&&!r.alcancePorPrecisar()&&r.proximaFecha()!=null&&!r.proximaFecha().isAfter(contexto.hoy())
            &&(r.pospuestaHasta()==null||!r.pospuestaHasta().isAfter(contexto.hoy()));}
    private String marca(Fila f,ContextoEstado c){
        var valores=new LinkedHashMap<String,Object>();
        valores.put("seguimiento",f.id());valores.put("inicial",f.inicial());valores.put("ultimo",f.ultimo());
        valores.put("fecha",f.proxima());valores.put("origen",f.origen());valores.put("revisarContinuidad",c.revisarContinuidad());
        valores.put("revisionPlanMaterial",c.revisionPlanMaterial());
        valores.put("restringido",c.restringido());valores.put("alcancePorPrecisar",c.alcancePorPrecisar());
        return Idempotencia.huellaExacta(documentos.objetoCanonico(valores));
    }
    @Transactional(readOnly=true)
    public List<RevisionSeguimiento> vencidas(Actor actor){return necesidadesVencidas(actor).stream().map(Necesidad::revision).toList();}
    @Transactional(propagation=Propagation.MANDATORY)
    public void bloquearParaProyectar(Actor actor){contexto.exigirAgente(actor);estados.bloquearParaProyeccion(actor.idOrganizacion(),actor.idRolOperativo());contexto.exigirAgente(actor);}
    @Transactional(readOnly=true)
    public void exigirLecturaOriginal(RevisionSeguimiento r,Actor actor){
        if(r==null||r.id()<=0||r.version()<0)throw new AccesoNoAutorizadoException();
        if(!Set.of("Propietario o representante declarado","Contacto comercial declarado").contains(r.destinatario()))throw new AccesoNoAutorizadoException();
        var original=exigirFuenteOriginal(r.id(),r.version(),r.idProspeccion(),actor);
        if(r.contactoDeclarado()!=null&&!Objects.equals(r.contactoDeclarado().referenciaDeclarada(),original.destino().referenciaDeclarada()))throw new AccesoNoAutorizadoException();
    }
    @Transactional(readOnly=true)
    public void exigirLecturaVersion(long id,long version,long prospeccion,Actor actor){exigirFuenteOriginal(id,version,prospeccion,actor);}
    private FuentesConstancia exigirFuenteOriginal(long id,long version,long prospeccion,Actor actor){
        if(id<=0||version<0)throw new AccesoNoAutorizadoException();
        exigirContexto(prospeccion,actor,false);
        var f=estados.porIdHistorico(actor.idOrganizacion(),id).filter(s->s.prospeccion()==prospeccion)
                .orElseThrow(()->new NoEncontradoException("Revisión original del seguimiento"));
        if(version>f.version())throw new AccesoNoAutorizadoException();
        // El ID fija para siempre Prospección/destinatario/finalidad. La proyección no contiene nombres
        // ni contactos antiguos; no se sustituye el resultado original por el estado actual.
        var original=fuentesOriginales(f.id(),version,actor);
        if(original.destino()==null||original.destino().prospeccion()!=prospeccion||!original.destino().clave().equals(f.destinatario()))throw new AccesoNoAutorizadoException();
        exigirFuentes(List.of(original),actor);
        return original;
    }
    private List<Legible> legibles(List<Fila> filas,Actor actor){
        contexto.exigirOperador(actor);var padres=new HashMap<Long,Contexto>();var vistos=new HashSet<ContactoOriginal>();
        var salida=new ArrayList<Legible>();for(var f:filas){
            try{var p=padres.computeIfAbsent(f.prospeccion(),id->exigirContexto(id,actor,false));if(!vigente(p))continue;
                var c=documentos.comoObjeto(f.contextoJson(),ContextoEstado.class);
                if(c.destinatarioOriginal()!=null&&!vistos.contains(c.destinatarioOriginal())){exigirDestinatario(c,actor);vistos.add(c.destinatarioOriginal());}
                salida.add(new Legible(f,p,c));}
            catch(NoEncontradoException|AccesoNoAutorizadoException denegada){/* No revelar destinatario, contador ni fecha fuera del alcance. */}
        }contexto.exigirOperador(actor);return restricciones(fuentesLegibles(salida,actor),actor);
    }
    private List<Legible> fuentesLegibles(List<Legible> filas,Actor actor){
        if(filas.isEmpty())return filas;
        var porVersion=new HashMap<ClaveVersion,FuentesConstancia>();
        for(var f:estados.constancias(actor.idOrganizacion(),filas.stream().map(l->new ClaveVersion(l.fila().id(),l.fila().version())).toList()))
            porVersion.put(new ClaveVersion(f.seguimiento(),f.version()),documentos.comoObjeto(f.fuentesJson(),FuentesConstancia.class));
        var fuentes=List.copyOf(porVersion.values());var permitidas=fuentesPermitidas(fuentes,actor);
        return filas.stream().filter(l->{var f=porVersion.get(new ClaveVersion(l.fila().id(),l.fila().version()));
            return f!=null&&f.destino()!=null&&f.destino().prospeccion()==l.actual().id()&&f.destino().clave().equals(l.fila().destinatario())&&permitidas.contains(f);}).toList();
    }
    private FuentesConstancia fuentesOriginales(long seguimiento,long version,Actor actor){
        return estados.constancias(actor.idOrganizacion(),List.of(new ClaveVersion(seguimiento,version))).stream().findFirst()
            .map(f->documentos.comoObjeto(f.fuentesJson(),FuentesConstancia.class)).orElseThrow(AccesoNoAutorizadoException::new);
    }
    private void exigirFuentes(List<FuentesConstancia> fuentes,Actor actor){if(fuentesPermitidas(fuentes,actor).size()!=new HashSet<>(fuentes).size())throw new AccesoNoAutorizadoException();}
    private Set<FuentesConstancia> fuentesPermitidas(List<FuentesConstancia> fuentes,Actor actor){
        var referencias=fuentes.stream().flatMap(f->f.comunicaciones().stream()).distinct().toList();
        var autorizadas=acceso.versionesPermitidas(referencias,actor,null);
        var salida=new HashSet<FuentesConstancia>();var contactosComprobados=new HashSet<ContactoOriginal>();
        for(var f:fuentes){
            if(!autorizadas.containsAll(f.comunicaciones())||f.destino()==null)continue;
            var c=f.destino().contactoOriginal();try{if(c!=null&&!contactosComprobados.contains(c)){contactos.comprobar(c.referencia(),c.persona(),actor);contactosComprobados.add(c);}salida.add(f);}
            catch(NoEncontradoException|AccesoNoAutorizadoException denegada){/* No reinterpretar otra versión ni conceder un contacto. */}
        }return Set.copyOf(salida);
    }
    private List<Legible> restricciones(List<Legible> legibles,Actor actor){
        var destinos=new LinkedHashSet<DestinoVerificado>();
        for(var l:legibles){var d=l.contexto().destinatarioOriginal();if(d!=null&&d.persona()!=null)
            destinos.add(new DestinoVerificado(l.actual().id(),l.actual().propiedad(),d.persona()));}
        if(destinos.isEmpty())return List.copyOf(legibles);
        var restricciones=new HashMap<String,RestriccionActual>();
        for(var r:estados.restriccionesContextuales(actor.idOrganizacion(),destinos,FINALIDAD))restricciones.put(r.prospeccion()+":"+r.persona(),r);
        var salida=new ArrayList<Legible>();var comprobados=new HashSet<ContactoOriginal>();
        for(var l:legibles){var c=l.contexto();var d=c.destinatarioOriginal();
            try{
                if(d!=null&&!comprobados.contains(d)){exigirDestinatario(c,actor);comprobados.add(d);}
                var r=d==null?null:restricciones.get(l.actual().id()+":"+d.persona());
                if(r!=null){boolean noContactar=c.restringido()||r.restringido(),precisar=c.alcancePorPrecisar()||r.alcancePorPrecisar();
                    c=new ContextoEstado(c.destinatarioOriginal(),noContactar?"RESTRICCION_CONTACTO":precisar?"PRECISAR_ALCANCE_RESTRICCION":c.causa(),
                        c.revisarContinuidad(),c.faltaFecha(),c.faltaContacto(),noContactar,precisar,c.origenExplicito(),c.fechaPlanificada(),c.detallePlanificacion(),c.planificadoEn(),c.motivoPosposicion(),c.revisionPlanMaterial(),c.declarado(),c.referenciaDeclarada());}
                salida.add(new Legible(l.fila(),l.actual(),c));
            }catch(NoEncontradoException|AccesoNoAutorizadoException denegada){/* No se concede lectura del origen privado por la restricción derivada. */}
        }
        contexto.exigirOperador(actor);return List.copyOf(salida);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public RevisionSeguimiento planificar(long id,long seguimiento,PlanificacionSeguimiento d,Actor actor,ContextoSesion sid,Procedencia proc){
        contexto.iniciar(actor,sid);if(d==null||d.versionEsperada()==null||d.versionEsperada()<0||d.origen()==null||vacio(d.detalle())||proc==null)
            throw new ReglaNegocioException("La planificación conserva versión explícita, origen y detalle.");
        var p=exigirContexto(id,actor,true);if(!vigente(p))throw new ConflictoException("La prospección ya no admite recontacto.");
        var f=exigirFila(id,seguimiento,d.versionEsperada(),actor);var c=documentos.comoObjeto(f.contextoJson(),ContextoEstado.class);exigirDestinatario(c,actor);
        c=restricciones(fuentesLegibles(List.of(new Legible(f,p,c)),actor),actor).stream().findFirst().orElseThrow(AccesoNoAutorizadoException::new).contexto();
        var originales=fuentesOriginales(f.id(),f.version(),actor);
        if((c.restringido()||c.alcancePorPrecisar())&&d.fecha()!=null)throw new ReglaNegocioException("La restricción contextual impide proponer otro contacto; precisa su alcance por el caso autorizado.");
        boolean cambioMaterial=!Objects.equals(d.fecha(),f.proxima())||d.origen()!=c.origenExplicito();
        long revision=c.revisionPlanMaterial()+(cambioMaterial?1:0);
        var nuevo=new ContextoEstado(c.destinatarioOriginal(),"PROXIMA_ACTUACION",c.revisarContinuidad(),c.faltaFecha(),c.faltaContacto(),c.restringido(),c.alcancePorPrecisar(),d.origen(),d.fecha(),d.detalle(),contexto.ahora(),c.motivoPosposicion(),revision,c.declarado(),c.referenciaDeclarada());
        String origen=d.fecha()==null?"SIN_FECHA":d.origen()==OrigenActuacion.ACUERDO_EXTERNO?"ACORDADA":"EXPLICITA";
        if(estados.actualizar(actor.idOrganizacion(),f,p.responsable(),f.inicial(),f.ultimo(),f.versionUltimo(),f.fechaUltimo(),f.recontactos(),d.fecha(),origen,f.pospuesta(),f.restriccionJson(),documentos.objetoCanonico(nuevo),contexto.ahora())!=1)throw new ConflictoException("Cambió el seguimiento.");
        var actual=estados.porId(actor.idOrganizacion(),seguimiento).orElseThrow();constancia(actual,nuevo,originales.comunicaciones(),actor);return revision(actual,nuevo);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public RevisionSeguimiento posponer(long id,long seguimiento,PosposicionSeguimiento d,Actor actor,ContextoSesion sid,Procedencia proc){
        contexto.iniciar(actor,sid);if(d==null||d.versionEsperada()==null||d.versionEsperada()<0||d.hasta()==null||!d.hasta().isAfter(contexto.hoy())||vacio(d.motivo())||proc==null)
            throw new ReglaNegocioException("Posponer requiere versión explícita, fecha futura y motivo.");
        var p=exigirContexto(id,actor,true);if(!vigente(p))throw new ConflictoException("La prospección ya no admite recontacto.");
        var f=exigirFila(id,seguimiento,d.versionEsperada(),actor);var c=documentos.comoObjeto(f.contextoJson(),ContextoEstado.class);exigirDestinatario(c,actor);
        c=restricciones(fuentesLegibles(List.of(new Legible(f,p,c)),actor),actor).stream().findFirst().orElseThrow(AccesoNoAutorizadoException::new).contexto();
        var originales=fuentesOriginales(f.id(),f.version(),actor);
        var nuevo=new ContextoEstado(c.destinatarioOriginal(),c.causa(),c.revisarContinuidad(),c.faltaFecha(),c.faltaContacto(),c.restringido(),c.alcancePorPrecisar(),c.origenExplicito(),c.fechaPlanificada(),c.detallePlanificacion(),c.planificadoEn(),d.motivo(),c.revisionPlanMaterial(),c.declarado(),c.referenciaDeclarada());
        if(estados.actualizar(actor.idOrganizacion(),f,p.responsable(),f.inicial(),f.ultimo(),f.versionUltimo(),f.fechaUltimo(),f.recontactos(),f.proxima(),f.origen(),d.hasta(),f.restriccionJson(),documentos.objetoCanonico(nuevo),contexto.ahora())!=1)throw new ConflictoException("Cambió el seguimiento.");
        var actual=estados.porId(actor.idOrganizacion(),seguimiento).orElseThrow();constancia(actual,nuevo,originales.comunicaciones(),actor);return revision(actual,nuevo);
    }
    private Fila exigirFila(long id,long seguimiento,long version,Actor actor){
        var f=estados.porId(actor.idOrganizacion(),seguimiento).filter(s->s.prospeccion()==id).orElseThrow(()->new NoEncontradoException("Seguimiento"));
        if(f.version()!=version)throw new ConflictoException("Cambió el seguimiento; revisa la versión actual.");return f;
    }
    private Contexto exigirContexto(long id,Actor actor,boolean escritura){
        contexto.exigirOperador(actor);var p=estados.contexto(actor.idOrganizacion(),id,escritura).orElseThrow(()->new NoEncontradoException("Prospección"));
        if(escritura?actor.esAgente()&&p.responsable()==actor.idRolOperativo():alcances.alcanza(actor,p.responsable()))return p;
        throw new AccesoNoAutorizadoException();
    }
    private void exigirDestinatario(ContextoEstado c,Actor actor){if(c.destinatarioOriginal()!=null)contactos.comprobar(c.destinatarioOriginal().referencia(),c.destinatarioOriginal().persona(),actor);}
    private void guardar(Actor actor,Contexto p,String destinatario,Fila anterior,Calculo c,List<ReferenciaVersion> fuentes){
        if(!c.necesidad()){if(anterior!=null)estados.retirar(actor.idOrganizacion(),anterior.id(),contexto.ahora(),fuentesJson(anterior,c.contexto(),fuentes,actor));return;}
        String json=documentos.objetoCanonico(c.contexto());String restriccion=c.contexto().restringido()||c.contexto().alcancePorPrecisar()?documentos.objeto(Map.of("finalidad",FINALIDAD,"restringido",c.contexto().restringido(),"alcancePorPrecisar",c.contexto().alcancePorPrecisar())):null;
        if(anterior==null){var antecedente=estados.ultimoEstado(actor.idOrganizacion(),p.id(),destinatario).orElse(null);var f=estados.insertar(actor.idOrganizacion(),p.id(),destinatario,p.responsable(),c.inicial(),c.ultimo(),c.versionUltimo(),c.fechaUltimo(),c.recontactos(),c.proxima(),c.origen(),null,restriccion,json,contexto.ahora(),antecedente,FINALIDAD);constancia(f,c.contexto(),fuentes,actor);return;}
        boolean mismasFuentes=estados.constancias(actor.idOrganizacion(),List.of(new ClaveVersion(anterior.id(),anterior.version()))).stream().findFirst()
            .map(v->new HashSet<>(documentos.comoObjeto(v.fuentesJson(),FuentesConstancia.class).comunicaciones()).equals(new HashSet<>(fuentes))).orElse(false);
        if(mismasFuentes&&Objects.equals(anterior.inicial(),c.inicial())&&Objects.equals(anterior.ultimo(),c.ultimo())&&Objects.equals(anterior.versionUltimo(),c.versionUltimo())
                &&Objects.equals(anterior.fechaUltimo(),c.fechaUltimo())&&anterior.recontactos()==c.recontactos()&&Objects.equals(anterior.proxima(),c.proxima())
                &&Objects.equals(anterior.origen(),c.origen())&&Objects.equals(anterior.contextoJson(),json)&&anterior.responsable()==p.responsable())return;
        if(estados.actualizar(actor.idOrganizacion(),anterior,p.responsable(),c.inicial(),c.ultimo(),c.versionUltimo(),c.fechaUltimo(),c.recontactos(),c.proxima(),c.origen(),anterior.pospuesta(),restriccion,json,contexto.ahora())!=1)throw new ConflictoException("Cambió el seguimiento.");
        constancia(estados.porId(actor.idOrganizacion(),anterior.id()).orElseThrow(),c.contexto(),fuentes,actor);
    }
    private void constancia(Fila f,ContextoEstado c,List<ReferenciaVersion> fuentes,Actor actor){
        estados.conservarVersion(actor.idOrganizacion(),f.id(),f.version(),fuentesJson(f,c,fuentes,actor),contexto.ahora());
    }
    private String fuentesJson(Fila f,ContextoEstado c,List<ReferenciaVersion> fuentes,Actor actor){
        var destino=new DestinoConstancia(f.prospeccion(),f.destinatario(),c.referenciaDeclarada(),c.destinatarioOriginal());
        return documentos.objetoCanonico(new FuentesConstancia(fuentes,destino,actor.idRolOperativo()));
    }
    private static RevisionSeguimiento revision(Fila f,ContextoEstado c){
        var d=c.declarado();var declarado=d==null?null:new ContactoDeSeguimiento(c.referenciaDeclarada(),d.tipo(),d.nombre(),d.telefono(),d.correo(),d.fuente());
        return new RevisionSeguimiento(f.id(),f.version(),f.prospeccion(),"Contacto comercial declarado",c.causa(),f.fechaUltimo(),f.recontactos(),c.restringido()||c.alcancePorPrecisar()?null:f.proxima(),f.origen(),f.pospuesta(),c.motivoPosposicion(),c.revisarContinuidad(),c.restringido(),c.alcancePorPrecisar(),declarado);
    }
    private static boolean canal(DestinatarioDeclarado d){return d!=null&&(!vacio(d.telefono())||!vacio(d.correo()));}
    private static boolean vigente(Contexto p){return !p.retirado()&&!p.encargoIniciado()&&p.encargo()==null&&p.estado()!=null&&Set.of("P","C","R","E","S").contains(p.estado());}
    private String clave(ParticipanteDeclarado d,ContactoOriginal original){
        if(original!=null&&original.persona()!=null)return "PERSONA:"+original.persona();
        if(d!=null&&!vacio(d.referenciaDeclarada()))return "DECLARADO:"+Idempotencia.huellaExacta(d.referenciaDeclarada());
        // Una revisión de datos incompletos, no una identidad ni agrupación de personas por nombre/teléfono.
        return d==null?null:"POR_PRECISAR";
    }
    private static String codigo(String v){return v==null?null:v.trim().toUpperCase(Locale.ROOT);}
    private static boolean vacio(String v){return v==null||v.isBlank();}
    private static <T>List<T> lista(List<T> l){return l==null?List.of():l;}
}
