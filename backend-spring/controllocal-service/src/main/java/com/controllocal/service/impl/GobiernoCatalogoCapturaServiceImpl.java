package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.AtributosGobernados;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/** CAT-E39/AC044–046. La extensión privada nunca sustituye la definición común. */
@Service
@Transactional
public class GobiernoCatalogoCapturaServiceImpl implements GobiernoCatalogoCapturaService {
    private static final String DEFINICION="DEFINICION_PRIVADA", PERFIL="PERFIL_OPCIONES";
    private final CatalogoAtributoRepository definiciones;
    private final PerfilCatalogoOpcionesRepository perfiles;
    private final RevisionCatalogoGobernadoRepository revisiones;
    private final EventoDominioRepository eventos;
    private final ContextosService contextos;
    private final EntityManager entidades;
    private final ObjectMapper json;

    public GobiernoCatalogoCapturaServiceImpl(CatalogoAtributoRepository definiciones,
            PerfilCatalogoOpcionesRepository perfiles,RevisionCatalogoGobernadoRepository revisiones,
            EventoDominioRepository eventos,ContextosService contextos,EntityManager entidades,ObjectMapper json) {
        this.definiciones=definiciones;this.perfiles=perfiles;this.revisiones=revisiones;
        this.eventos=eventos;this.contextos=contextos;this.entidades=entidades;this.json=json;
    }

    @Override
    public OpcionesCatalogo opcionesVigentes(long org,String clave,String jurisdiccion) {
        if(org<=0||clave==null||clave.isBlank())return pendientes("DEFINICION_NO_DISPONIBLE");
        // Proyección fresca: una entidad previamente hidratada no decide vigencia/versiones.
        var base=(soloLectura()?definiciones.actualParaCaptura(org,clave):
                definiciones.bloquearActualParaCaptura(org,clave)).orElse(null);
        if(base==null)return pendientes("DEFINICION_NO_DISPONIBLE");
        boolean zonificacion=CatalogoAtributo.CLAVE_ZONIFICACION.equals(base.getClave())
                && Sujeto.PROPIEDAD.codigo().equals(base.getSujeto());
        boolean lista=TipoDato.LISTA.codigo().equals(base.getTipoDato())
                ||TipoDato.LISTA_MULTIPLE.codigo().equals(base.getTipoDato());
        if(!lista&&!zonificacion)return pendientes("DEFINICION_NO_ES_LISTA");
        if(jurisdiccion!=null&&!jurisdiccion.isBlank()) {
            var actuales=soloLectura()?perfiles.actuales(org,base.getId(),jurisdiccion.trim()):
                    perfiles.bloquearActuales(org,base.getId(),jurisdiccion.trim());
            if(actuales.size()>1)return pendientes("PERFIL_AMBIGUO");
            if(actuales.size()==1) {
                var p=actuales.get(0);
                if(vacio(p.getVersionFuente())||vacio(p.getFuente()))return pendientes("FUENTE_DEFINICION_FALTANTE");
                var opciones=perfiles.opcionesActuales(p.getId()).stream().map(o->new OpcionCatalogo(o.getCodigo(),o.getRotulo())).toList();
                return new OpcionesCatalogo(refPerfil(p.getId(),p.getVersion()),p.getVersion(),opciones,
                        opciones.isEmpty()?"CATALOGO_SIN_OPCIONES":null,p.getJurisdiccion(),p.getVersionFuente());
            }
        }
        if(zonificacion)return pendientes(vacio(jurisdiccion)?"JURISDICCION_FALTANTE":"PERFIL_NO_DISPONIBLE");
        var opciones=definiciones.opcionesActuales(base.getId()).stream().map(o->new OpcionCatalogo(o.getCodigo(),o.getRotulo())).toList();
        return new OpcionesCatalogo(refDefinicion(base.getId(),base.getVersion()),base.getVersion(),opciones,
                opciones.isEmpty()?"CATALOGO_SIN_OPCIONES":null);
    }

    @Override
    public ValidacionOpcion validarOpcion(long org,String clave,String valor,String referencia,String jurisdiccion) {
        var lista=opcionesVigentes(org,clave,jurisdiccion);
        if(lista.motivo()!=null)return new ValidacionOpcion(false,lista.motivo(),lista.referenciaCatalogo(),null,null,lista.versionDefinicion());
        if(!vacio(referencia)&&!referencia.equals(lista.referenciaCatalogo()))
            return new ValidacionOpcion(false,"REFERENCIA_NO_VIGENTE",lista.referenciaCatalogo(),null,null,lista.versionDefinicion());
        var opcion=lista.opciones().stream().filter(o->o.codigo().equals(valor)).findFirst();
        return opcion.map(o->new ValidacionOpcion(true,null,lista.referenciaCatalogo(),o.codigo(),o.rotulo(),lista.versionDefinicion()))
                .orElseGet(()->new ValidacionOpcion(false,"OPCION_NO_VIGENTE",lista.referenciaCatalogo(),null,null,lista.versionDefinicion()));
    }

    @Override
    public CatalogoGobernado consultar(Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);
        var ds=definiciones.visiblesParaGobierno(actor.idOrganizacion()).stream().map(c->new ResumenDefinicion(c.getId(),
                refDefinicion(c.getId(),c.getVersionDefinicion()),c.getVersionDefinicion(),c.getClave(),c.getSujeto(),
                c.getTipoDato(),c.getRotulo(),c.getOrganizacionId()==null,c.isActivo())).toList();
        var ps=perfiles.findByOrganizacionIdOrderByIdAsc(actor.idOrganizacion()).stream().map(p->new ResumenPerfil(p.getId(),
                refPerfil(p.getId(),p.getVersionDefinicion()),p.getVersionDefinicion(),p.getIdCatalogoAtributo(),p.getJurisdiccion(),p.isActivo())).toList();
        return new CatalogoGobernado(ds,ps);
    }

    @Override
    public Definicion consultarDefinicion(long id,Long version,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);
        var c=definicionVisible(actor.idOrganizacion(),id);
        if(version==null||version==c.getVersionDefinicion())return vista(c);
        if(c.getOrganizacionId()==null)throw new NoEncontradoException("Version de definicion");
        return anterior(actor.idOrganizacion(),DEFINICION,id,version,Definicion.class);
    }

    @Override
    public Perfil consultarPerfil(long id,Long version,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);
        var p=perfilVisible(actor.idOrganizacion(),id);
        if(version==null||version==p.getVersionDefinicion())return vista(p);
        return anterior(actor.idOrganizacion(),PERFIL,id,version,Perfil.class);
    }

    @Override
    public Definicion crearDefinicion(DatosDefinicion datos,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);validar(datos);
        if(definiciones.porClave(actor.idOrganizacion(),datos.clave()).isPresent())
            throw new ConflictoException("La clave ya existe en el ambito comun o privado; no se puede sombrear.");
        var c=new CatalogoAtributo();c.setOrganizacionId(actor.idOrganizacion());c.setDelSistema(false);
        c.setClave(datos.clave());c.setSujeto(Sujeto.desde(datos.sujeto()));c.setTipoDato(TipoDato.desde(datos.tipoDato()).codigo());
        c.setUnidad(opcional(datos.unidad()));aplicar(c,datos);definiciones.saveAndFlush(c);
        evento(actor,"DEFINICION_CATALOGO_CREADA","CATALOGO_ATRIBUTO",c.getId(),c.getVersionDefinicion());
        return vista(c);
    }

    @Override
    public Definicion versionarDefinicion(long id,long version,DatosDefinicion datos,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);validar(datos);
        var c=definicionPrivadaBloqueada(actor.idOrganizacion(),id);exigirVersion(c.getVersionDefinicion(),version);
        if(!c.isActivo())throw new ConflictoException("La definicion esta retirada; sus valores anteriores se conservan.");
        if(!Objects.equals(c.getClave(),datos.clave())||!Objects.equals(c.getSujeto(),Sujeto.desde(datos.sujeto()).codigo())
                ||!Objects.equals(c.getTipoDato(),TipoDato.desde(datos.tipoDato()).codigo())||!Objects.equals(c.getUnidad(),opcional(datos.unidad())))
            throw new ConflictoException("La version no puede reinterpretar clave, sujeto, tipo o unidad de valores anteriores.");
        var previa=vista(c);
        validarConservacionOpciones(c.getOpciones(),datos.opciones());
        aplicar(c,datos);
        if(previa.datos().equals(vista(c).datos()))return previa;
        archivar(actor,DEFINICION,id,version,"VERSIONAR",previa);definiciones.saveAndFlush(c);
        evento(actor,"DEFINICION_CATALOGO_VERSIONADA","CATALOGO_ATRIBUTO",id,c.getVersionDefinicion());
        return vista(c);
    }

    @Override
    public Definicion retirarDefinicion(long id,long version,String sustituta,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);
        var c=definicionPrivadaBloqueada(actor.idOrganizacion(),id);exigirVersion(c.getVersionDefinicion(),version);
        if(!c.isActivo())throw new ConflictoException("La definicion ya esta retirada.");
        validarSustitucion(c,sustituta,actor.idOrganizacion());
        archivar(actor,DEFINICION,id,version,"RETIRAR",vista(c));
        c.setActivo(false);c.setFechaRetiro(OffsetDateTime.now());c.setSustituidaPor(opcional(sustituta));definiciones.saveAndFlush(c);
        evento(actor,"DEFINICION_CATALOGO_RETIRADA","CATALOGO_ATRIBUTO",id,c.getVersionDefinicion());return vista(c);
    }

    @Override
    public Perfil crearPerfil(DatosPerfil datos,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);validar(datos);
        var base=baseDePerfil(actor.idOrganizacion(),datos.idDefinicion());
        if(!perfiles.bloquearActuales(actor.idOrganizacion(),base.getId(),datos.jurisdiccion().trim()).isEmpty())
            throw new ConflictoException("Ya hay un perfil vigente para esa definicion y jurisdiccion; versiona el perfil existente.");
        var p=new PerfilCatalogoOpciones();p.setOrganizacionId(actor.idOrganizacion());p.setIdCatalogoAtributo(base.getId());
        p.setJurisdiccion(datos.jurisdiccion().trim());aplicar(p,datos);perfiles.saveAndFlush(p);
        evento(actor,"PERFIL_CATALOGO_CREADO","PERFIL_CATALOGO_OPCIONES",p.getId(),p.getVersionDefinicion());return vista(p);
    }

    @Override
    public Perfil versionarPerfil(long id,long version,DatosPerfil datos,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);validar(datos);
        // Mismo orden que captura: definición antes de perfil; evita ciclos con validación y retirada.
        var preliminar=perfilVisible(actor.idOrganizacion(),id);baseDePerfil(actor.idOrganizacion(),preliminar.getIdCatalogoAtributo());
        var p=perfilBloqueado(actor.idOrganizacion(),id);exigirVersion(p.getVersionDefinicion(),version);
        if(!p.isActivo())throw new ConflictoException("El perfil esta retirado.");
        if(p.getIdCatalogoAtributo()!=datos.idDefinicion()||!p.getJurisdiccion().equals(datos.jurisdiccion().trim()))
            throw new ConflictoException("La version conserva definicion base y jurisdiccion.");
        validarConservacionOpciones(p.getOpciones(),datos.opciones());var previa=vista(p);aplicar(p,datos);
        if(previa.datos().equals(vista(p).datos()))return previa;
        archivar(actor,PERFIL,id,version,"VERSIONAR",previa);perfiles.saveAndFlush(p);
        evento(actor,"PERFIL_CATALOGO_VERSIONADO","PERFIL_CATALOGO_OPCIONES",id,p.getVersionDefinicion());return vista(p);
    }

    @Override
    public Perfil retirarPerfil(long id,long version,Actor actor,ContextoSesion sesion) {
        exigirGobierno(actor,sesion);
        var previo=perfilVisible(actor.idOrganizacion(),id);baseDePerfil(actor.idOrganizacion(),previo.getIdCatalogoAtributo());
        var p=perfilBloqueado(actor.idOrganizacion(),id);exigirVersion(p.getVersionDefinicion(),version);
        if(!p.isActivo())throw new ConflictoException("El perfil ya esta retirado.");
        // Retirar una lista no elimina la obligación del Encargo: el lookup queda pendiente.
        archivar(actor,PERFIL,id,version,"RETIRAR",vista(p));p.retirar(Instant.now());perfiles.saveAndFlush(p);
        evento(actor,"PERFIL_CATALOGO_RETIRADO","PERFIL_CATALOGO_OPCIONES",id,p.getVersionDefinicion());return vista(p);
    }

    private void exigirGobierno(Actor actor,ContextoSesion sesion) {
        if(actor==null||!actor.esTenantAdmin()||sesion==null)throw new AccesoNoAutorizadoException();
        try {
            var actual=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(!ContextoSesion.de(actual).equals(sesion)||actual.restricciones()==null
                    ||actual.restricciones().debeCambiarContrasena()||actual.restricciones().debeEnrolarMfa())
                throw new AccesoNoAutorizadoException();
        }catch(CredencialesInvalidasException e){throw new AccesoNoAutorizadoException();}
    }
    private CatalogoAtributo definicionVisible(long org,long id) {
        return definiciones.visiblePorId(org,id).orElseThrow(()->new NoEncontradoException("Definicion"));
    }
    private CatalogoAtributo definicionPrivadaBloqueada(long org,long id) {
        var c=definicionVisible(org,id);
        if(c.getOrganizacionId()==null||c.isDelSistema())throw new AccesoNoAutorizadoException();
        entidades.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        if(!Objects.equals(c.getOrganizacionId(),org))throw new NoEncontradoException("Definicion");return c;
    }
    private CatalogoAtributo baseDePerfil(long org,long id) {
        var c=definicionVisible(org,id);entidades.refresh(c,LockModeType.PESSIMISTIC_READ);
        if(!c.isActivo())throw new ConflictoException("La definicion base esta retirada.");
        boolean lista=c.tipo()==TipoDato.LISTA||c.tipo()==TipoDato.LISTA_MULTIPLE;
        boolean zonificacion=CatalogoAtributo.CLAVE_ZONIFICACION.equals(c.getClave())&&c.sujeto()==Sujeto.PROPIEDAD&&c.tipo()==TipoDato.TEXTO;
        if(!lista&&!zonificacion)throw new ReglaNegocioException("La definicion no admite perfil de opciones.");return c;
    }
    private PerfilCatalogoOpciones perfilVisible(long org,long id) {
        return perfiles.findByOrganizacionIdAndId(org,id).orElseThrow(()->new NoEncontradoException("Perfil de catalogo"));
    }
    private PerfilCatalogoOpciones perfilBloqueado(long org,long id) {
        var p=perfilVisible(org,id);entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        if(!Objects.equals(p.getOrganizacionId(),org))throw new NoEncontradoException("Perfil de catalogo");return p;
    }
    private static void exigirVersion(long actual,long esperada) {
        if(esperada<0)throw new ReglaNegocioException("Indica la version esperada.");
        if(actual!=esperada)throw new ConflictoException("La definicion cambio. Consulta su version vigente.");
    }
    private <T> T anterior(long org,String clase,long id,long version,Class<T> tipo) {
        var r=revisiones.findByOrganizacionIdAndClaseRecursoAndIdRecursoAndVersionDefinicion(org,clase,id,version)
                .orElseThrow(()->new NoEncontradoException("Version de catalogo"));
        try{return json.readValue(r.getSnapshotJson(),tipo);}catch(JsonProcessingException e){throw new IllegalStateException("Snapshot de catalogo no interpretable",e);}
    }
    private void archivar(Actor actor,String clase,long id,long version,String actuacion,Object snapshot) {
        var r=new RevisionCatalogoGobernado();r.setOrganizacionId(actor.idOrganizacion());r.setClaseRecurso(clase);
        r.setIdRecurso(id);r.setVersionDefinicion(version);r.setActuacion(actuacion);r.setIdRolActor(actor.idRolOperativo());r.setSnapshotJson(serializar(snapshot));
        revisiones.save(r);
    }
    private void evento(Actor actor,String tipo,String entidad,long id,long version) {
        eventos.save(EventoDominio.de(actor.idOrganizacion(),tipo,entidad,id,actor.idRolOperativo(),EventoDominio.CANAL_API)
                .con(serializar(Map.of("versionDefinicion",version))));
    }
    private String serializar(Object valor) {
        try{return json.writeValueAsString(valor);}catch(JsonProcessingException e){throw new IllegalStateException("No se pudo conservar la definicion",e);}
    }
    private static boolean soloLectura(){return TransactionSynchronizationManager.isCurrentTransactionReadOnly();}
    private static OpcionesCatalogo pendientes(String motivo){return new OpcionesCatalogo(null,null,List.of(),motivo);}
    private static String refDefinicion(long id,long version){return "CAT:D:"+id+":"+version;}
    private static String refPerfil(long id,long version){return "CAT:P:"+id+":"+version;}
    private static boolean vacio(String valor){return valor==null||valor.isBlank();}
    private static String opcional(String valor){return vacio(valor)?null:valor.trim();}
    private static void texto(String valor,int max,String campo){if(vacio(valor)||valor.trim().length()>max)throw new ReglaNegocioException("Indica "+campo+" valido, hasta "+max+" caracteres.");}

    private static void validar(DatosPerfil d) {
        if(d==null||d.idDefinicion()<=0)throw new ReglaNegocioException("Indica la definicion del perfil.");
        texto(d.jurisdiccion(),160,"jurisdiccion");texto(d.versionFuente(),100,"version de fuente");texto(d.fuenteDefinicion(),Integer.MAX_VALUE,"fuente de definicion");
        opciones(d.opciones());if(d.opciones().stream().noneMatch(OpcionDefinida::activa))throw new ReglaNegocioException("El perfil requiere opciones vigentes de su fuente.");
    }
    private static void validar(DatosDefinicion d) {
        if(d==null)throw new ReglaNegocioException("Indica la definicion.");
        texto(d.clave(),60,"clave");texto(d.rotulo(),120,"rotulo");texto(d.versionFuente(),100,"version de fuente");texto(d.fuenteDefinicion(),Integer.MAX_VALUE,"fuente de definicion");
        if(!d.clave().equals(d.clave().trim()))throw new ReglaNegocioException("La clave no lleva espacios exteriores.");
        if(d.unidad()!=null&&d.unidad().length()>20||d.familia()!=null&&d.familia().length()>30||d.jurisdiccion()!=null&&d.jurisdiccion().length()>160)
            throw new ReglaNegocioException("Metadatos de definicion demasiado largos.");
        TipoDato tipo;Sujeto sujeto;
        try{tipo=TipoDato.desde(d.tipoDato());sujeto=Sujeto.desde(d.sujeto());}catch(IllegalArgumentException e){throw new ReglaNegocioException(e.getMessage());}
        if(d.valorMinimo()!=null&&d.valorMaximo()!=null&&d.valorMinimo().compareTo(d.valorMaximo())>0)
            throw new ReglaNegocioException("El minimo no puede superar al maximo.");
        if(d.longitudMaxima()!=null&&d.longitudMaxima()<=0)throw new ReglaNegocioException("La longitud maxima debe ser positiva.");
        opciones(d.opciones());boolean lista=tipo==TipoDato.LISTA||tipo==TipoDato.LISTA_MULTIPLE;
        if(lista&&d.opciones().stream().noneMatch(OpcionDefinida::activa)||!lista&&!d.opciones().isEmpty())
            throw new ReglaNegocioException("Las opciones corresponden solamente a listas con vocabulario vigente.");
        if(d.aplicaciones()==null||d.aplicaciones().isEmpty())throw new ReglaNegocioException("Declara la aplicabilidad de la extension.");
        var claves=new HashSet<String>();
        for(var a:d.aplicaciones()) {
            if(a==null||a.tipoPropiedad()==null||AtributosGobernados.codigoDelTipo(a.tipoPropiedad()).filter(a.tipoPropiedad()::equals).isEmpty())throw new ReglaNegocioException("Tipo de propiedad no reconocido.");
            try{Exigencia.desde(a.exigencia());if(sujeto==Sujeto.ENCARGO)OperacionInmobiliaria.desde(a.operacion());}
            catch(IllegalArgumentException e){throw new ReglaNegocioException(e.getMessage());}
            if(sujeto==Sujeto.PROPIEDAD&&!vacio(a.operacion()))throw new ReglaNegocioException("Un dato fisico no declara operacion.");
            String k=a.tipoPropiedad()+":"+(sujeto==Sujeto.ENCARGO?OperacionInmobiliaria.desde(a.operacion()).codigo():"");
            if(!claves.add(k))throw new ReglaNegocioException("Aplicabilidad repetida.");
        }
    }
    private static void opciones(List<OpcionDefinida> opciones) {
        if(opciones==null)throw new ReglaNegocioException("Declara las opciones, aunque la lista sea vacia.");
        var codigos=new HashSet<String>();
        for(var o:opciones){if(o==null)throw new ReglaNegocioException("Opcion incompleta.");texto(o.codigo(),40,"codigo de opcion");texto(o.rotulo(),120,"rotulo de opcion");
            if(!o.codigo().equals(o.codigo().trim())||!codigos.add(o.codigo()))throw new ReglaNegocioException("Codigo de opcion repetido o con espacios exteriores.");}
    }
    private static void validarConservacionOpciones(Set<OpcionDeAtributo> anteriores,List<OpcionDefinida> nuevas) {
        var codigos=nuevas.stream().map(OpcionDefinida::codigo).collect(java.util.stream.Collectors.toSet());
        if(anteriores.stream().anyMatch(o->!codigos.contains(o.getValor())))throw new ConflictoException("Retira las opciones anteriores sin borrar sus codigos ni valores.");
    }
    private static void aplicar(CatalogoAtributo c,DatosDefinicion d) {
        c.setRotulo(d.rotulo().trim());c.setAyuda(opcional(d.ayuda()));c.setFamilia(opcional(d.familia()));c.setOrden(d.orden());
        c.setValorMinimo(d.valorMinimo());c.setValorMaximo(d.valorMaximo());c.setLongitudMaxima(d.longitudMaxima());
        c.setJurisdiccion(opcional(d.jurisdiccion()));c.setVersionFuente(d.versionFuente().trim());c.setFuenteDefinicion(d.fuenteDefinicion().trim());
        c.getOpciones().clear();c.getOpciones().addAll(valores(d.opciones()));
        c.getAplicaciones().clear();c.getAplicacionesOperacion().clear();
        for(var a:d.aplicaciones())if(c.sujeto()==Sujeto.PROPIEDAD)c.getAplicaciones().add(new AplicacionAtributo(a.tipoPropiedad(),Exigencia.desde(a.exigencia())));
        else c.getAplicacionesOperacion().add(new AplicacionPorOperacion(a.tipoPropiedad(),OperacionInmobiliaria.desde(a.operacion()).codigo(),Exigencia.desde(a.exigencia())));
    }
    private static void aplicar(PerfilCatalogoOpciones p,DatosPerfil d) {
        p.setVersionFuente(d.versionFuente().trim());p.setFuenteDefinicion(d.fuenteDefinicion().trim());p.getOpciones().clear();p.getOpciones().addAll(valores(d.opciones()));
    }
    private static Set<OpcionDeAtributo> valores(List<OpcionDefinida> opciones) {
        var salida=new LinkedHashSet<OpcionDeAtributo>();for(var o:opciones)salida.add(new OpcionDeAtributo(o.codigo(),o.rotulo().trim(),o.orden(),o.activa()));return salida;
    }
    private static List<OpcionDefinida> vistas(Set<OpcionDeAtributo> opciones) {
        return opciones.stream().sorted(Comparator.comparingInt(OpcionDeAtributo::getOrden).thenComparing(OpcionDeAtributo::getValor))
                .map(o->new OpcionDefinida(o.getValor(),o.getRotulo(),o.getOrden(),o.isActivo())).toList();
    }
    private static Definicion vista(CatalogoAtributo c) {
        var apps=new ArrayList<Aplicacion>();
        c.getAplicaciones().forEach(a->apps.add(new Aplicacion(a.getTipoPropiedad(),null,a.getExigencia())));
        c.getAplicacionesOperacion().forEach(a->apps.add(new Aplicacion(a.getTipoPropiedad(),a.getTipoOperacion(),a.getExigencia())));
        apps.sort(Comparator.comparing(Aplicacion::tipoPropiedad).thenComparing(a->Objects.toString(a.operacion(),"")));
        return new Definicion(c.getId(),refDefinicion(c.getId(),c.getVersionDefinicion()),c.getVersionDefinicion(),c.getOrganizacionId()==null,c.isActivo(),
                c.getFechaRetiro()==null?null:c.getFechaRetiro().toInstant(),c.getSustituidaPor(),
                new DatosDefinicion(c.getClave(),c.getSujeto(),c.getTipoDato(),c.getUnidad(),c.getRotulo(),c.getAyuda(),c.getFamilia(),c.getOrden(),
                        c.getValorMinimo(),c.getValorMaximo(),c.getLongitudMaxima(),c.getJurisdiccion(),c.getVersionFuente(),c.getFuenteDefinicion(),List.copyOf(apps),vistas(c.getOpciones())));
    }
    private static Perfil vista(PerfilCatalogoOpciones p) {
        return new Perfil(p.getId(),refPerfil(p.getId(),p.getVersionDefinicion()),p.getVersionDefinicion(),p.isActivo(),p.getFechaRetiro(),
                new DatosPerfil(p.getIdCatalogoAtributo(),p.getJurisdiccion(),p.getVersionFuente(),p.getFuenteDefinicion(),vistas(p.getOpciones())));
    }
    private void validarSustitucion(CatalogoAtributo c,String clave,long org) {
        boolean obligatoria=c.getAplicaciones().stream().anyMatch(a->a.exigenciaTipada()!=Exigencia.OPC)
                ||c.getAplicacionesOperacion().stream().anyMatch(a->a.exigenciaTipada()!=Exigencia.OPC);
        if(vacio(clave)){if(obligatoria)throw new ConflictoException("La definicion conserva obligaciones: indica su sustituta vigente antes de retirarla.");return;}
        var otra=definiciones.porClaveYSujeto(org,clave,c.getSujeto()).orElseThrow(()->new ReglaNegocioException("La sustituta no esta disponible."));
        if(!otra.isActivo()||otra.getId().equals(c.getId())||!otra.getTipoDato().equals(c.getTipoDato())||!Objects.equals(otra.getUnidad(),c.getUnidad()))
            throw new ConflictoException("La sustituta debe estar vigente y conservar sujeto, tipo y unidad.");
        for(var a:c.getAplicaciones())if(a.exigenciaTipada()!=Exigencia.OPC&&!cubre(otra.exigenciaPara(a.getTipoPropiedad()),a.exigenciaTipada()))throw new ConflictoException("La sustituta no cubre la obligacion vigente.");
        for(var a:c.getAplicacionesOperacion())if(a.exigenciaTipada()!=Exigencia.OPC&&!cubre(otra.exigenciaPara(a.getTipoPropiedad(),a.getTipoOperacion()),a.exigenciaTipada()))throw new ConflictoException("La sustituta no cubre la obligacion vigente.");
    }
    private static boolean cubre(Exigencia nueva,Exigencia previa){return nueva==previa||nueva==Exigencia.ALT;}
}
