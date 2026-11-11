package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.*;
import com.controllocal.service.PropiedadUniversalService.*;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Edición física real: metadato nuevo explícito, sin Encargo ni atribución de evidencia anterior. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class CapturaMetadataEdicionIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired PropiedadUniversalService propiedades;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ObjectMapper json;
    private Actor actor;private long propiedad;
    private static final Procedencia ORIGEN=Procedencia.deLaPantalla();
    @BeforeEach void preparar(){
        actor=new TransactionTemplate(gestor).execute(tx->{
            String marca=UUID.randomUUID().toString().replace("-","");
            long org=jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,'INDIVIDUAL','A') returning id_organizacion",Long.class,"ME-"+marca.substring(0,20),"Espacio individual ficticio de ensayo");
            long cuenta=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),'fixture-sin-credencial-autenticable') returning id_cuenta_acceso",Long.class,"metadata-"+marca,"metadata-"+marca+"@example.invalid");
            long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Profesional ficticio de ensayo') returning id_persona",Long.class,org);
            long rol=jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'AGENTE',(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol",Long.class,org,persona);
            jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date,'D')",org,rol,"AG-ME-"+rol);
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'AGENTE',false,'A')",org,cuenta,persona);
            return new Actor(org,persona,rol,Actor.AGENTE);
        });
        propiedad=propiedades.registrar(new ComandoRegistro(UUID.randomUUID().toString(),ORIGEN,null,"DEPARTAMENTO",null,null,
                new Ubicacion("Direccion ficticia de ensayo "+UUID.randomUUID(),"Lince",null,null,null,"101",null,null,null),
                List.of(),List.of(new ValorAtributo("metraje_total","100")),List.of(),null),actor).idPropiedad();
    }
    @Test void areaCorregidaConservaConceptoDeclaradoYEvidenciaAnteriorEnEvento() throws Exception {
        editar(valor("metraje_total","100","AREA_TOTAL",null,null,"FUENTE-FICTICIA-1"),null);
        editar(valor("metraje_total","120","AREA_TOTAL",null,null,"FUENTE-FICTICIA-2"),null);
        assertEquals(0,new BigDecimal("120").compareTo(jdbc.queryForObject("select metraje from propiedad where id_propiedad=?",BigDecimal.class,propiedad)));
        JsonNode meta=metadatos().get("metraje_total");assertEquals("AREA_TOTAL",meta.get("concepto").asText());assertEquals("FUENTE-FICTICIA-2",meta.get("evidenciaRef").asText());
        var evento=jdbc.queryForObject("select carga_util from evento_dominio where entidad_tipo='PROPIEDAD' and entidad_id=? and tipo='PROPIEDAD_EDITADA' order by id_evento desc limit 1",String.class,propiedad);
        var anterior=json.readTree(json.readTree(evento).get("metadatosAnteriores").asText());assertEquals("FUENTE-FICTICIA-1",anterior.get("metraje_total").get("evidenciaRef").asText());
        assertEquals(0,jdbc.queryForObject("select count(*) from captacion where id_propiedad=?",Integer.class,propiedad));
    }
    @Test void cambioSinCapturaExplicitaInvalidaConceptoYEvidenciaDelValorAnterior() throws Exception {
        editar(valor("metraje_total","100","AREA_TOTAL",null,null,"FUENTE-FICTICIA-1"),null);
        editar(new ValorAtributo("metraje_total","120"),null);
        var meta=metadatos().get("metraje_total");assertEquals("CONOCIDO",meta.get("estado").asText());assertFalse(meta.has("concepto"));assertFalse(meta.has("evidenciaRef"));
    }
    @Test void zonificacionSeCorrigeConPerfilRealYFuenteDelValorNueva() throws Exception {
        long perfil=perfil();String ref="CAT:P:"+perfil+":0";
        editar(valor("zonificacion","COD-FICTICIO-1",null,ref,"0","DOCUMENTO-FICTICIO-1"),contexto("JURISDICCION-FICTICIA"));
        editar(valor("zonificacion","COD-FICTICIO-2",null,ref,"0","DOCUMENTO-FICTICIO-2"),null);
        assertEquals("COD-FICTICIO-2",jdbc.queryForObject("select valor_texto from atributo_propiedad where id_propiedad=? and clave='zonificacion'",String.class,propiedad));
        var meta=metadatos().get("zonificacion");assertEquals(ref,meta.get("referenciaCatalogo").asText());assertEquals("0",meta.get("versionDefinicion").asText());assertEquals("DOCUMENTO-FICTICIO-2",meta.get("evidenciaRef").asText());
    }
    @Test void codigoAjenoALaListaQuedaEnRevisionSinInventarOpcion() throws Exception {
        long perfil=perfil();editar(valor("zonificacion","COD-NO-EXISTE",null,"CAT:P:"+perfil+":0","0","DOCUMENTO-FICTICIO"),contexto("JURISDICCION-FICTICIA"));
        var meta=metadatos().get("zonificacion");assertEquals("FALTANTE",meta.get("estado").asText());assertEquals("COD-NO-EXISTE",meta.get("valorEnRevision").asText());assertEquals("OPCION_NO_VIGENTE",meta.get("incidencia").asText());
        assertEquals(0,jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave='zonificacion'",Integer.class,propiedad));
    }
    @Test void zonificacionSinMetadataTambienExigeCodigoDelPerfilActual() throws Exception {
        long perfil=perfil();
        var valida=new ValorAtributo("zonificacion","COD-FICTICIO-1",null,null,"DECLARADO",null,null,"DOCUMENTO-RAW-FICTICIO-1");
        editar(valida,contexto("JURISDICCION-FICTICIA"));
        var meta=metadatos().get("zonificacion");
        assertEquals("CONOCIDO",meta.get("estado").asText());
        assertEquals("CAT:P:"+perfil+":0",meta.get("referenciaCatalogo").asText());
        assertEquals("0",meta.get("versionDefinicion").asText());
        assertEquals("DOCUMENTO-RAW-FICTICIO-1",meta.get("evidenciaRef").asText());
        assertEquals("COD-FICTICIO-1",jdbc.queryForObject("select valor_texto from atributo_propiedad where id_propiedad=? and clave='zonificacion'",String.class,propiedad));
        editar(new ValorAtributo("zonificacion","COD-AJENO-AL-PERFIL",null,null,"DECLARADO",null,null,"DOCUMENTO-RAW-FICTICIO-2"),null);
        meta=metadatos().get("zonificacion");
        assertEquals("FALTANTE",meta.get("estado").asText());
        assertEquals("OPCION_NO_VIGENTE",meta.get("incidencia").asText());
        assertEquals("COD-AJENO-AL-PERFIL",meta.get("valorEnRevision").asText());
        assertEquals("DOCUMENTO-RAW-FICTICIO-2",meta.get("evidenciaRef").asText());
        assertEquals(0,jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave='zonificacion'",Integer.class,propiedad));
    }
    @Test void zonificacionSinMetadataNiJurisdiccionQuedaPendienteConSuFuente() throws Exception {
        editar(new ValorAtributo("zonificacion","COD-FICTICIO-SIN-JURISDICCION",null,null,"DECLARADO",null,null,"DOCUMENTO-RAW-FICTICIO"),null);
        var meta=metadatos().get("zonificacion");
        assertEquals("FALTANTE",meta.get("estado").asText());
        assertEquals("JURISDICCION_FALTANTE",meta.get("incidencia").asText());
        assertEquals("COD-FICTICIO-SIN-JURISDICCION",meta.get("valorEnRevision").asText());
        assertEquals("DOCUMENTO-RAW-FICTICIO",meta.get("evidenciaRef").asText());
        assertFalse(meta.hasNonNull("referenciaCatalogo"));assertFalse(meta.hasNonNull("versionDefinicion"));
        assertEquals(0,jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave='zonificacion'",Integer.class,propiedad));
        assertEquals(0,jdbc.queryForObject("select count(*) from captacion where id_propiedad=?",Integer.class,propiedad));
    }
    @Test void ceroConocidoNoSeTransformaEnFaltanteYNoAplicableExigeRespaldo() throws Exception {
        editar(valor("ascensores","0",null,null,null,"DECLARACION-FICTICIA"),null);
        assertEquals("CONOCIDO",metadatos().get("ascensores").get("estado").asText());
        var noAplica=new ValorAtributo("ascensores",null,null,null,null,null,null,null,new MetadatosDeCaptura(EstadoDato.NO_APLICABLE,null,null,null,false));
        assertThrows(ReglaNegocioException.class,()->editar(noAplica,null));
        var sustentado=new ValorAtributo("ascensores",null,null,null,null,null,null,"RESPALDO-FICTICIO",noAplica.captura());editar(sustentado,null);
        assertEquals("NO_APLICABLE",metadatos().get("ascensores").get("estado").asText());
    }
    @Test void versionDeDefinicionObsoletaRevierteValorYMetadata() {
        assertThrows(ConflictoException.class,()->editar(valor("ascensores","2",null,null,"999999","FUENTE-FICTICIA"),null));
        assertEquals(0,jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave='ascensores'",Integer.class,propiedad));
    }
    @Test void reintentoDelContextoNoRepiteRetiradaNiPierdeSuIdentidad() throws Exception {
        long perfil=perfil();editar(valor("zonificacion","COD-FICTICIO-1",null,"CAT:P:"+perfil+":0","0","FUENTE-FICTICIA"),contexto("JURISDICCION-FICTICIA"));
        String clave=UUID.randomUUID().toString();var c=new ComandoEdicion(clave,ORIGEN,null,null,null,null,null,null,null,contexto("OTRA-JURISDICCION-FICTICIA"))
                .conVersion(propiedades.consultar(propiedad,actor).versionFisica())
                .conMotivoRetiro("Cambio declarado de jurisdicción de prueba; revisar la zonificación anterior");
        propiedades.editar(propiedad,c,actor);long eventos=eventos();propiedades.editar(propiedad,c,actor);
        assertEquals(eventos,eventos());assertEquals("FALTANTE",metadatos().get("zonificacion").get("estado").asText());
    }
    @Test void usoNoDeclaradoNoSeInfiereDelTipo(){assertNull(jdbc.queryForObject("select uso from propiedad where id_propiedad=?",String.class,propiedad));}

    private void editar(ValorAtributo v,SuficienciaInmueble.Contexto contexto){
        long observada=propiedades.consultar(propiedad,actor).versionFisica();
        var comando=new ComandoEdicion(UUID.randomUUID().toString(),ORIGEN,null,null,null,List.of(v),null,null,null,contexto)
                .conVersion(observada).conMotivoRetiro("Corrección explícita de metadatos declarados en el fixture");
        propiedades.editar(propiedad,comando,actor);
    }
    private ValorAtributo valor(String clave,String valor,String concepto,String ref,String version,String evidencia){return new ValorAtributo(clave,valor,null,null,"DECLARADO",null,null,evidencia,new MetadatosDeCaptura(EstadoDato.CONOCIDO,concepto,ref,version,false));}
    private SuficienciaInmueble.Contexto contexto(String jurisdiccion){return new SuficienciaInmueble.Contexto("D",null,null,null,Map.of(),Set.of(),jurisdiccion);}
    private JsonNode metadatos() throws Exception{return json.readTree(jdbc.queryForObject("select metadatos_atributos from propiedad where id_propiedad=?",String.class,propiedad));}
    private long eventos(){return jdbc.queryForObject("select count(*) from evento_dominio where entidad_tipo='PROPIEDAD' and entidad_id=? and tipo='PROPIEDAD_EDITADA'",Long.class,propiedad);}
    private long perfil(){
        long base=jdbc.queryForObject("select id_catalogo_atributo from catalogo_atributo where clave='zonificacion' and organizacion_id is null",Long.class);
        long id=jdbc.queryForObject("insert into perfil_catalogo_opciones(organizacion_id,id_catalogo_atributo,jurisdiccion,version_fuente,fuente_definicion) values(?,?,'JURISDICCION-FICTICIA','v1-FICTICIA','Fuente exclusiva de test sin dato territorial real') returning id_perfil",Long.class,actor.idOrganizacion(),base);
        jdbc.update("insert into perfil_catalogo_opcion(id_perfil,valor,rotulo,orden) values(?,'COD-FICTICIO-1','Opcion ficticia uno',1),(?,'COD-FICTICIO-2','Opcion ficticia dos',2)",id,id);return id;
    }
}
