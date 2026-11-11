package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.seguridad.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static com.controllocal.service.PublicacionService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Privacidad20.9: B en contextos equivalentes, misma entrada; además misma lectura B antes/después de A.
 * Mutaciones por Core y lecturas HTTP con principal de prueba/contexto real; no certifica login HTTP.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters=false)
class EncargosPrivadosC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager gestor;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired PublicacionService publicaciones;
    FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void terminar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    EncargosDePrueba banco(){return new EncargosDePrueba(b.jdbc,b.encargos,gestor,
            contexto.getBean(com.controllocal.service.archivo.AccesoArchivoService.class),
            contexto.getBean(com.controllocal.web.almacen.AlmacenDocumentos.class),b.actuaciones,
            contexto.getBean(com.controllocal.service.soporte.Documentos.class));}
    EncargosDePrueba.Caso condiciones(ActividadNegociacionDePrueba.Caso c){return new EncargosDePrueba.Caso(c.propiedad(),OperacionInmobiliaria.ALQUILER,hoy(),hoy().plusMonths(6),true,
            EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"PROPIETARIO_TEST","AGENCIA_TEST"),
            new EncargosDePrueba.Actuante(c.agente(),c.sesion()),new EncargosDePrueba.Actuante(c.equipo().broker(),b.sesiones.de(c.equipo().broker())));}
    void direccion(ActividadNegociacionDePrueba.Caso c,String direccion){
        var f=propiedades.consultar(c.propiedad(),c.agente());
        propiedades.editar(c.propiedad(),new PropiedadUniversalService.ComandoEdicion(clave(),procedencia(),null,
                new PropiedadUniversalService.Ubicacion(direccion,"Miraflores",null,null,null,null,null,null,null),null,null,null,null,null,null).conVersion(f.versionFisica()),c.agente());
    }
    void sesion(Actor a){
        var s=b.sesiones.de(a);var acceso=contexto.getBean(ContextosService.class).exigirContexto(s.referenciaSesion(),s.revisionContexto(),a);
        var principal=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,AuthorityUtils.createAuthorityList("ROLE_"+a.rolEfectivo())));
    }
    ObjectNode http(String ruta)throws Exception{return (ObjectNode)json.readTree(mvc.perform(get(ruta)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    ObjectNode ficha(long encargo,ActividadNegociacionDePrueba.Caso c)throws Exception{
        var n=http("/encargos/"+encargo);assertEquals(c.agente().idRolOperativo(),n.path("responsable").asLong());assertEquals(c.propiedad(),n.path("idPropiedad").asLong());n.put("id","ENCARGO_B");n.put("responsable","RESPONSABLE_B");n.put("idPropiedad","PROPIEDAD_B");
        // Sólo referencias técnicas propias generadas dentro de esta ejecución; no se retiran causas, estados, valores o facultades.
        for(String k:List.of("peticionActual","documentoActual"))if(!n.path(k).isNull())n.put(k,"REFERENCIA_PROPIA_B");
        return n;
    }
    record Recorrido(long encargo,long anuncio,List<JsonNode> respuestas) { }
    Recorrido recorrido(ActividadNegociacionDePrueba.Caso c)throws Exception{
        sesion(c.agente());
        var apertura=new TransactionTemplate(gestor).execute(t->b.encargos.abrir(new EncargoService.DatosApertura(c.propiedad(),c.agente().idRolOperativo(),
                "PRIV-B-ALQUILER",OperacionInmobiliaria.ALQUILER,hoy(),hoy().plusMonths(6),true,"Instrumento B TEST",condiciones(c).condiciones(),procedencia(),List.of(),
                new EncargoService.OrigenProspeccion(EncargoService.TipoOrigenProspeccion.DIRECTO,null)),c.agente()));
        var alta=json.valueToTree(apertura);((ObjectNode)alta).put("id","ENCARGO_B");var preparando=ficha(apertura.id(),c);
        banco().habilitarExistente(apertura.id(),condiciones(c));var habilitada=ficha(apertura.id(),c);
        var datos=new DatosContenido("WEB_PROPIA",null,new BigDecimal("2500"),"PEN","Oferta privada B TEST",null);
        var borrador=publicaciones.crearBorradorAnuncio(new DatosBorrador(apertura.id(),b.encargos.consultar(apertura.id(),c.agente()).version(),null,null,datos),c.agente(),c.sesion(),clave(),procedencia());
        var p=b.actuaciones.prepararPublicacion(PUBLICAR,c.agente(),new DatosCambio(apertura.id(),b.encargos.consultar(apertura.id(),c.agente()).version(),borrador.id(),borrador.version(),null,null,null,null),c.sesion(),procedencia());
        var publicada=b.humanas.confirmar(p,c.agente(),c.sesion(),FichaPublicacion.class).datos();
        var anuncio=http("/encargos/"+apertura.id()+"/publicaciones/"+publicada.id());
        assertEquals("P",anuncio.path("estado").asText());assertFalse(anuncio.path("fechaPublicacion").isNull());
        anuncio.put("id","ANUNCIO_B");anuncio.put("idEncargo","ENCARGO_B");anuncio.put("fechaPublicacion","INSTANTE_PROPIO_B");
        return new Recorrido(apertura.id(),publicada.id(),List.of(alta,preparando,habilitada,anuncio,ficha(apertura.id(),c),http("/encargos/"+apertura.id()+"/vigencia")));
    }
    @Test void exclusivaPrivadaDeANoCambiaResultadosMensajesEstadosNiFacultadesDeB()throws Exception{
        var base=b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",false);
        var c=b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",false);
        String direccion="Dirección ficticia compartida TEST "+UUID.randomUUID();direccion(base,direccion);direccion(c,direccion);
        // Las confirmaciones reales tienen TX independiente. No envolverlas en un rollback que oculte sus fuentes.
        // Dos B con igual gobierno, financiación, objeto y datos. Sólo se normalizan sus referencias técnicas propias.
        var sinA=recorrido(base);
        assertEquals(0,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id=? and id_propiedad=? and motivo_operacion='A'",Integer.class,c.agente().idOrganizacion(),c.propiedad()));
        var a=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);direccion(a,direccion);
        banco().habilitarExistente(a.encargo(),condiciones(a));
        assertTrue(b.jdbc.queryForObject("select exclusividad from captacion where id_captacion=?",Boolean.class,a.encargo()));
        var avisos=contexto.getBean(AlertaService.class);
        var avisosAgenteA=avisos.listar(1,100,a.agente());var avisosBrokerA=avisos.listar(1,100,a.equipo().broker());
        long alertasA=b.jdbc.queryForObject("select count(*) from alerta where organizacion_id=?",Long.class,a.agente().idOrganizacion());
        long eventosA=b.jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=?",Long.class,a.agente().idOrganizacion());
        long historiasA=b.jdbc.queryForObject("select count(*) from historial_estado where organizacion_id=?",Long.class,a.agente().idOrganizacion());
        sesion(base.agente());assertEquals(sinA.respuestas().get(4),ficha(sinA.encargo(),base));
        assertEquals(sinA.respuestas().get(5),http("/encargos/"+sinA.encargo()+"/vigencia"));
        var conA=recorrido(c);assertEquals(sinA.respuestas(),conA.respuestas(),"No se normalizan mensajes, causas, estados, facultades ni valores comerciales.");
        assertEquals(avisosAgenteA,avisos.listar(1,100,a.agente()));assertEquals(avisosBrokerA,avisos.listar(1,100,a.equipo().broker()));
        assertEquals(alertasA,b.jdbc.queryForObject("select count(*) from alerta where organizacion_id=?",Long.class,a.agente().idOrganizacion()));
        assertEquals(eventosA,b.jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=?",Long.class,a.agente().idOrganizacion()));
        assertEquals(historiasA,b.jdbc.queryForObject("select count(*) from historial_estado where organizacion_id=?",Long.class,a.agente().idOrganizacion()));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id=? and id_propiedad=? and motivo_operacion='A' and estado='A'",Integer.class,c.agente().idOrganizacion(),c.propiedad()));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id=? and id_propiedad=? and motivo_operacion='A' and estado='A'",Integer.class,a.agente().idOrganizacion(),a.propiedad()));
    }
    @Test void dosInstrumentosAportadosSeConservanSinSobrescribirNiHabilitarOtroEncargo(){
        var c=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",true);
        var anterior=b.encargos.consultar(c.encargo(),c.agente());long version=anterior.version();
        var datos=new EncargoService.CargaArchivo(c.encargo(),null,"ACEPTACION_ENCARGO","instrumento-uno-test.pdf","application/pdf",
                DocumentoManuscritoDePrueba.acta("Instrumento firmado TEST uno",c.encargo(),"Aporte documental sintético, no acreditación legal",true),version);
        String k=clave();var uno=b.encargos.cargarArchivo(datos,c.agente(),c.sesion(),k,procedencia());
        assertEquals(uno,b.encargos.cargarArchivo(datos,c.agente(),c.sesion(),k,procedencia()));
        var vinculo=new EncargoService.VinculoArchivo(uno.referencia(),c.encargo(),null,"ACEPTACION_ENCARGO",version);String kv=clave();
        var primero=b.encargos.adjuntarOReutilizar(vinculo,c.agente(),c.sesion(),kv,procedencia());
        assertEquals(primero,b.encargos.adjuntarOReutilizar(vinculo,c.agente(),c.sesion(),kv,procedencia()));
        var dos=b.encargos.cargarArchivo(new EncargoService.CargaArchivo(c.encargo(),null,"ACEPTACION_ENCARGO","instrumento-dos-test.pdf","application/pdf",
                DocumentoManuscritoDePrueba.acta("Instrumento firmado TEST dos",c.encargo(),"Otro documento aportado, sin aprobación implícita",true),version),c.agente(),c.sesion(),clave(),procedencia());
        var segundo=b.encargos.adjuntarOReutilizar(new EncargoService.VinculoArchivo(dos.referencia(),c.encargo(),null,"ACEPTACION_ENCARGO",version),c.agente(),c.sesion(),clave(),procedencia());
        assertNotEquals(primero.referencia(),segundo.referencia());
        var actual=b.encargos.consultar(c.encargo(),c.agente());assertEquals(anterior.documentoActual(),actual.documentoActual());assertEquals(anterior.peticionActual(),actual.peticionActual());
        assertEquals(1,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id=? and id_propiedad=? and motivo_operacion='A' and estado in ('P','A')",Integer.class,c.agente().idOrganizacion(),c.propiedad()));
        assertEquals(2,b.jdbc.queryForObject("select count(*) from archivo_encargo where organizacion_id=? and id_captacion=? and archivo_ref in (?,?)",Integer.class,c.agente().idOrganizacion(),c.encargo(),primero.referencia(),segundo.referencia()));
        b.encargos.exigirHabilitado(c.encargo(),c.agente());
    }
}
