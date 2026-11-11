package com.controllocal.integracion;

import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** T4 selectiva #replay: semilla única y dos clones literales PostgreSQL, nunca dos B aproximados. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class EncargosClonadosC7IntegrationTest extends EncargosPrivadosC7IntegrationTest {
    @org.springframework.test.context.DynamicPropertySource static void baseEspecifica(org.springframework.test.context.DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    record Semilla(OrigenesDePrueba.Equipo oficinaA,long propiedadA,OrigenesDePrueba.Equipo oficinaB,long propietarioB) { }
    Path archivo(String nombre){
        var limite=Path.of("D:/init/ControlLocal/work/c7-k34-20261104/clones-4brechas");
        var raiz=Path.of(System.getProperty("brox.prueba.clon.directorio",limite.toString())).toAbsolutePath().normalize();
        assertTrue(raiz.startsWith(limite));return raiz.resolve(nombre);
    }
    void escribir(String nombre,Object dato)throws Exception{var p=archivo(nombre);Files.createDirectories(p.getParent());assertFalse(Files.exists(p));Files.write(p,json.writerWithDefaultPrettyPrinter().writeValueAsBytes(dato));}
    com.fasterxml.jackson.databind.JsonNode altaFisica;
    long propiedad(Actor a,long titular){
        var alta=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),procedencia(),null,"LOCAL_COMERCIAL","COMERCIAL","Objeto comercial TEST A/B",
                new PropiedadUniversalService.Ubicacion("Dirección ficticia común de ensayo clonado TEST","Miraflores",null,null,null,null,null,null,null),
                List.of(new PropiedadUniversalService.Titular(titular,null,true)),List.of(),List.of(),null),a);
        long id=alta.idPropiedad();var n=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(alta);n.put("idPropiedad","PROPIEDAD_PROPIA");n.put("codigo","CODIGO_PROPIO");altaFisica=n;
        b.negociacion.prepararFisica(id,a);return id;
    }
    @Test void replay()throws Exception{
        String fase=Objects.requireNonNull(System.getProperty("brox.prueba.clon.fase"),"T4 requiere fase explícita; no se simula aceptación.");
        if(fase.equals("SEMILLA")){
            var o=new OrigenesDePrueba(b.jdbc,gestor,contexto.getBean(com.controllocal.service.captura.MotorDeCaptura.class),contexto.getBean(ContextosService.class));
            var a=o.empresa();var c=o.empresa();
            var propietarios=contexto.getBean(PropietarioService.class);
            long pa=propietarios.registrar(new PropietarioService.DatosPropietario("N","D","99123501","Titular TEST común","999123501","clon-a@example.invalid",true,null),a.agente()).id();
            long pb=propietarios.registrar(new PropietarioService.DatosPropietario("N","D","99123501","Titular TEST común","999123501","clon-b@example.invalid",true,null),c.agente()).id();
            long id=propiedad(a.agente(),pa);
            new PlanesFinanciadosDePrueba(b.jdbc,gestor).asegurarTeam(a.agente().idOrganizacion());new PlanesFinanciadosDePrueba(b.jdbc,gestor).asegurarTeam(c.agente().idOrganizacion());
            b.sesiones.de(a.broker());b.sesiones.de(c.broker());
            assertEquals(0,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id in (?,?)",Long.class,a.agente().idOrganizacion(),c.agente().idOrganizacion()));
            escribir("semilla.json",new Semilla(a,id,c,pb));return;
        }
        assertTrue(Set.of("SIN_A","CON_A").contains(fase));var s=json.readValue(Files.readAllBytes(archivo("semilla.json")),Semilla.class);
        String db=b.jdbc.queryForObject("select current_database()",String.class);assertTrue(db.startsWith("controllocal_ci_c7_clon_"));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id in (?,?)",Long.class,s.oficinaA().agente().idOrganizacion(),s.oficinaB().agente().idOrganizacion()));
        long ca=0;
        if(fase.equals("CON_A")){
            var actor=s.oficinaA().agente();
            ca=new TransactionTemplate(gestor).execute(t->b.encargos.abrir(new EncargoService.DatosApertura(s.propiedadA(),actor.idRolOperativo(),"CLON-A-PRIVADO",OperacionInmobiliaria.ALQUILER,
                    hoy(),hoy().plusMonths(6),true,"Exclusiva privada A TEST",EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"PROPIETARIO_TEST","AGENCIA_TEST"),procedencia(),List.of()),actor).id());
            var c=new ActividadNegociacionDePrueba.Caso(s.oficinaA(),actor,b.sesiones.de(actor),s.propiedadA(),ca,0,0,0,"ALQUILER","PEN");
            banco().habilitarExistente(ca,condiciones(c));archivoIgual(ca,actor);
        }
        var avisos=contexto.getBean(AlertaService.class);var agAntes=avisos.listar(1,100,s.oficinaA().agente());var brAntes=avisos.listar(1,100,s.oficinaA().broker());
        var cuentaAntes=cuentasA(s.oficinaA().agente());
        long id=propiedad(s.oficinaB().agente(),s.propietarioB());
        var cb=new ActividadNegociacionDePrueba.Caso(s.oficinaB(),s.oficinaB().agente(),b.sesiones.de(s.oficinaB().agente()),id,0,0,0,0,"ALQUILER","PEN");
        var r=recorrido(cb);var archivoB=archivoIgual(r.encargo(),cb.agente());
        var respuestas=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();respuestas.add(altaFisica);respuestas.addAll(r.respuestas());
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(archivoB);n.put("referencia","ARCHIVO_PROPIO_B");n.put("encargo","ENCARGO_B");respuestas.add(n);
        assertEquals(agAntes,avisos.listar(1,100,s.oficinaA().agente()));assertEquals(brAntes,avisos.listar(1,100,s.oficinaA().broker()));assertEquals(cuentaAntes,cuentasA(s.oficinaA().agente()));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id=? and id_propiedad=? and estado='A'",Long.class,cb.agente().idOrganizacion(),id));
        escribir(fase+".json",Map.of("respuestas",respuestas,"avisosAIntactos",true,"historiaAIntacta",true,"base",db,"encargoA",ca));
    }
    Map<String,Long> cuentasA(Actor a){var m=new TreeMap<String,Long>();for(String t:List.of("alerta","evento_dominio","historial_estado"))m.put(t,b.jdbc.queryForObject("select count(*) from "+t+" where organizacion_id=?",Long.class,a.idOrganizacion()));return m;}
    EncargoService.ArchivoRegistrado archivoIgual(long id,Actor a){
        long v=b.encargos.consultar(id,a).version();var carga=b.encargos.cargarArchivo(new EncargoService.CargaArchivo(id,null,"TITULARIDAD_ENCARGO","mismos-bytes-test.txt","text/plain","MISMA EVIDENCIA SINTETICA TEST NO FIRMA".getBytes(java.nio.charset.StandardCharsets.UTF_8),v),a,b.sesiones.de(a),clave(),procedencia());
        return b.encargos.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),id,null,"TITULARIDAD_ENCARGO",v),a,b.sesiones.de(a),clave(),procedencia());
    }
}
