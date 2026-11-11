package com.controllocal.integracion.soporte;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.PropiedadUniversalService.*;
import com.controllocal.service.captura.SuficienciaInmueble.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.controllocal.service.captura.SuficienciaInmueble.*;

/** Montaje explícito de TEST. No completa las PUB estudiadas ni activa Encargos por SQL. */
public final class PreparacionPublicacionDePrueba {
    private final JdbcTemplate jdbc;private final PropiedadUniversalService propiedades;
    private final EncargoService encargos;private final GobiernoCatalogoCapturaService catalogos;
    private final SesionesComercialesDePrueba sesiones;private final EncargosDePrueba banco;
    private final com.controllocal.service.actuacion.ActuacionesService actuaciones;
    private final ActuacionesHumanasC4DePrueba humanas;
    private final String jurisdiccion="JURISDICCION-PUB-TEST-"+UUID.randomUUID();
    private GobiernoCatalogoCapturaService.Perfil perfil;
    public PreparacionPublicacionDePrueba(JdbcTemplate jdbc,PropiedadUniversalService propiedades,
            EncargoService encargos,GobiernoCatalogoCapturaService catalogos,ContextosService contextos,
            CuentaService cuentas,FactorAutenticacionRepository factores,CifradoSecretos cifrado,
            PlatformTransactionManager gestor,com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest,
            com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest,
            com.controllocal.service.actuacion.ActuacionesService actuacionesFirmaTest,Documentos documentosFirmaTest){
        this.jdbc=jdbc;this.propiedades=propiedades;this.encargos=encargos;this.catalogos=catalogos;
        this.sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        this.banco=new EncargosDePrueba(jdbc,encargos,gestor,archivosFirmaTest,almacenFirmaTest,actuacionesFirmaTest,documentosFirmaTest);
        this.actuaciones=actuacionesFirmaTest;this.humanas=new ActuacionesHumanasC4DePrueba(actuacionesFirmaTest,documentosFirmaTest);
    }
    /** Datos elegidos por escenarios históricos de TEST; no es un contrato productivo ni una vía de escritura. */
    public record DatosAnuncio(String canal,String urlPublicacion,BigDecimal importePublicado,String moneda,
                               String tituloAnuncio,String codigoOrigen,String estado) { }
    public PublicacionService.FichaPublicacion publicar(PublicacionService servicio,long encargo,DatosAnuncio datos,Actor actor){
        var ficha=encargos.consultar(encargo,actor);
        var contenido=contenido(datos);
        var borrador=servicio.crearBorradorAnuncio(new PublicacionService.DatosBorrador(encargo,ficha.version(),null,null,contenido),
                actor,sesiones.de(actor),clave(),Procedencia.deLaPantalla());
        if("B".equals(datos.estado()))return borrador;
        return decidir(servicio,PublicacionService.PUBLICAR,borrador,null,actor);
    }
    public PublicacionService.FichaPublicacion editar(PublicacionService servicio,long id,DatosAnuncio datos,Actor actor){
        var actual=consultar(servicio,id,actor);
        if("B".equals(actual.estado()))return servicio.editarBorradorAnuncio(new PublicacionService.DatosBorrador(actual.idEncargo(),
                actual.versionEncargo(),id,actual.version(),contenido(datos)),actor,sesiones.de(actor),clave(),Procedencia.deLaPantalla());
        return decidir(servicio,PublicacionService.ACTUALIZAR_VISIBLE,actual,contenido(datos),actor);
    }
    public PublicacionService.FichaPublicacion cambiarEstado(PublicacionService servicio,long id,String estado,Actor actor){
        var actual=consultar(servicio,id,actor);
        String cap=switch(estado){case "P"->"S".equals(actual.estado())?PublicacionService.REANUDAR:PublicacionService.PUBLICAR;
            case "S"->PublicacionService.PAUSAR;case "C"->PublicacionService.FINALIZAR;
            default->throw new IllegalArgumentException("Un hecho difundido no vuelve a borrador.");};
        return decidir(servicio,cap,actual,null,actor);
    }
    private PublicacionService.FichaPublicacion consultar(PublicacionService servicio,long id,Actor actor){
        Long encargo=jdbc.queryForObject("select id_captacion from publicacion where organizacion_id=? and id_publicacion=?",Long.class,actor.idOrganizacion(),id);
        return servicio.obtener(encargo,id,actor);
    }
    private PublicacionService.FichaPublicacion decidir(PublicacionService servicio,String cap,PublicacionService.FichaPublicacion actual,
            PublicacionService.DatosContenido contenido,Actor actor){
        var sesion=sesiones.de(actor);
        var p=actuaciones.prepararPublicacion(cap,actor,new PublicacionService.DatosCambio(actual.idEncargo(),actual.versionEncargo(),
                actual.id(),actual.version(),contenido,null,null,(PublicacionService.PAUSAR.equals(cap)||PublicacionService.FINALIZAR.equals(cap))?"Motivo explícito de reducción TEST":null),sesion,Procedencia.deLaPantalla());
        return humanas.confirmar(p,actor,sesion,PublicacionService.FichaPublicacion.class).datos();
    }
    private static PublicacionService.DatosContenido contenido(DatosAnuncio d){return new PublicacionService.DatosContenido(
            d.canal(),d.urlPublicacion(),d.importePublicado(),d.moneda(),d.tituloAnuncio()==null?"Anuncio sintético TEST":d.tituloAnuncio(),d.codigoOrigen());}
    /** Predio TEST500m², uso residencial declarado y zona ficticia de catálogo. Servicios/suelo permanecen intactos. */
    public void prepararTerreno(long id,Actor actor){
        if(perfil==null){
            long definicion=jdbc.queryForObject("select id_catalogo_atributo from catalogo_atributo where clave='zonificacion' and organizacion_id is null and del_sistema",Long.class);
            var gobierno=sesiones.gobiernoDe(actor.idOrganizacion());
            perfil=catalogos.crearPerfil(new GobiernoCatalogoCapturaService.DatosPerfil(definicion,jurisdiccion,"FUENTE-PUB-TEST-V1","FUENTE-FICTICIA-PUB-TEST",
                    List.of(new GobiernoCatalogoCapturaService.OpcionDefinida("ZONA-PUB-TEST","Zona ficticia de prueba",1,true))),gobierno,sesiones.de(gobierno));
        }
        var zona=new ValorAtributo("zonificacion","ZONA-PUB-TEST",null,null,"DECLARADO",null,hoy(),"FUENTE-FICTICIA-PUB-TEST",
                new MetadatosDeCaptura(EstadoDato.CONOCIDO,null,perfil.referenciaCatalogo(),Long.toString(perfil.version()),false));
        editar(id,actor,new Contexto("T","VIVIENDA","Predio TEST completo",null,Map.of(),Set.of(),jurisdiccion),
                List.of(concepto("metraje_total","500",AREA_TERRENO),zona));
    }
    /** Unidad TEST de90m²/piso3/un ascensor. Mantenimiento no aplica por declaración explícita de este montaje. */
    public void prepararDepartamento(long id,Actor actor){
        editar(id,actor,new Contexto("D","VIVIENDA","Departamento TEST101",null,
                Map.of(UNIDAD_DE_EDIFICIO,Aplicabilidad.APLICA,MANTENIMIENTO,Aplicabilidad.NO_APLICA),Set.of()),
                List.of(concepto("metraje_total","90",AREA_TOTAL),new ValorAtributo("dormitorios","3"),new ValorAtributo("banos","2"),new ValorAtributo("piso","3"),new ValorAtributo("ascensores","1"),noAplica(MANTENIMIENTO)));
    }
    /** Local TEST90m² a nivel de calle, sin potencial/cambio de uso ofrecido. El consumidor declara rubro/acceso. */
    public void prepararLocal(long id,Actor actor,List<ValorAtributo> declarados){
        var entradas=new ArrayList<ValorAtributo>();entradas.add(concepto("metraje_total","90",AREA_TOTAL));
        entradas.add(noAplica(UNIDAD_DE_EDIFICIO));entradas.add(noAplica(POTENCIAL_O_CAMBIO_USO));
        if(declarados!=null)entradas.addAll(declarados);
        editar(id,actor,new Contexto("L","COMERCIAL","Local TEST a nivel de calle",null,
                Map.of(UNIDAD_DE_EDIFICIO,Aplicabilidad.NO_APLICA,POTENCIAL_O_CAMBIO_USO,Aplicabilidad.NO_APLICA),Set.of()),entradas);
    }
    private void editar(long id,Actor actor,Contexto contexto,List<ValorAtributo> valores){
        var actual=propiedades.consultar(id,actor);
        propiedades.editar(id,new ComandoEdicion(clave(),Procedencia.deLaPantalla(),null,null,null,valores,null,null,null,contexto).conVersion(actual.versionFisica()),actor);
    }
    /** El precio/moneda ya declarados se reutilizan; el resto son términos elegidos expresamente para TEST. */
    public EncargosDePrueba.Resultado habilitar(long idEncargo,Actor actor){
        var actual=encargos.consultar(idEncargo,actor);var precio=actual.condiciones();
        if(precio==null||precio.precioPedido()==null||precio.monedaPrecio()==null)throw new AssertionError("El positivo de publicación necesita precio y moneda explícitos en su fixture.");
        var op=OperacionInmobiliaria.desde(actual.operacion());
        var pacto=op==OperacionInmobiliaria.VENTA
                ?EncargosDePrueba.venta(precio.precioPedido(),precio.monedaPrecio(),new BigDecimal("0.03"),"Titular TEST","Agencia TEST")
                :EncargosDePrueba.alquiler(precio.precioPedido(),precio.monedaPrecio(),BigDecimal.ONE,"Titular TEST","Agencia TEST");
        Actor decisor=decisor(actor);
        return banco.habilitarExistente(idEncargo,new EncargosDePrueba.Caso(actual.idPropiedad(),op,hoy(),hoy().plusMonths(6),false,pacto,
                new EncargosDePrueba.Actuante(actor,sesiones.de(actor)),new EncargosDePrueba.Actuante(decisor,sesiones.de(decisor))));
    }
    private Actor decisor(Actor actor){
        String modalidad=jdbc.queryForObject("select modalidad from organizacion where id_organizacion=?",String.class,actor.idOrganizacion());
        if("INDIVIDUAL".equals(modalidad))return actor;
        var candidatos=jdbc.queryForList("""
            select r.id_persona_rol,r.id_persona from supervision_agente s
              join persona_rol r on r.organizacion_id=s.organizacion_id and r.id_persona_rol=s.id_rol_broker
              join usuario_organizacion u on u.organizacion_id=r.organizacion_id and u.id_persona=r.id_persona
             where s.organizacion_id=? and s.id_rol_agente=? and s.fecha_fin is null
               and s.fecha_asignacion<=? and r.tipo_rol='BROKER' and r.vigencia_hasta is null
               and r.vigencia_desde<=? and u.estado='A' and u.rol='BROKER' order by r.id_persona_rol
            """,actor.idOrganizacion(),actor.idRolOperativo(),hoy(),hoy());
        if(candidatos.isEmpty())throw new AssertionError("El positivo necesita supervisión BROKER actual; no se fabrica por el helper.");
        var c=candidatos.getFirst();return new Actor(actor.idOrganizacion(),((Number)c.get("id_persona")).longValue(),((Number)c.get("id_persona_rol")).longValue(),Actor.BROKER);
    }
    private static ValorAtributo concepto(String clave,String valor,String concepto){return new ValorAtributo(clave,valor,null,null,"DECLARADO",null,hoy(),"DECLARACION-FICTICIA-PUB-TEST",new MetadatosDeCaptura(EstadoDato.CONOCIDO,concepto,null,null,false));}
    private static ValorAtributo noAplica(String contexto){return new ValorAtributo("contexto."+contexto,null,null,null,"DECLARADO",null,hoy(),"NO-APLICA-DECLARADO-EXPRESAMENTE-EN-ESTE-TEST",new MetadatosDeCaptura(EstadoDato.NO_APLICABLE,null,null,null,false));}
    private static String clave(){return "PUB-TEST-"+UUID.randomUUID();}
    private static LocalDate hoy(){return LocalDate.now(ZoneId.of("America/Lima"));}
}
