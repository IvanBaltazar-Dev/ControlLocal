package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.CuentaDeGobiernoFila;
import com.controllocal.service.Actor;
import com.controllocal.service.SeguridadService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** CAT-E01/R19: una fila por pertenencia local, sin actividad ni seguridad personal. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Transactional
class PadronDeGobiernoIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) {BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired CuentaAccesoRepository cuentas;
    @Autowired SeguridadService seguridad;
    private long orgA,orgB,broker,rolBroker,adminPuro,historico;
    private Actor gobierno;

    @BeforeEach void pertenenciasDePrueba() {
        orgA=jdbc.queryForObject("select min(id_organizacion) from organizacion",Long.class);
        orgB=jdbc.queryForObject("insert into organizacion(codigo,nombre,estado,modalidad) values(?,?,'I','EMPRESARIAL') returning id_organizacion",Long.class,
                "PG-"+UUID.randomUUID().toString().substring(0,24),"Otra organizacion ficticia del padron");
        broker=cuenta("Broker local");long pBroker=persona(orgA,"Broker local");
        rolBroker=rol(orgA,pBroker,"BROKER");long rolAdmin=rol(orgA,pBroker,"ADMIN");
        pertenencia(orgA,broker,pBroker,Actor.BROKER,true,"A");
        gobierno=new Actor(orgA,pBroker,rolAdmin,Actor.TENANT_ADMIN);
        adminPuro=cuenta("Gobierno sin rol comercial");long pAdmin=persona(orgA,"Gobierno local");
        rol(orgA,pAdmin,"ADMIN");pertenencia(orgA,adminPuro,pAdmin,null,true,"A");
        historico=cuenta("Nombre personal que no debe sustituir al local");
        long pAnterior=persona(orgA,"Antecedente local A");rol(orgA,pAnterior,"ADMIN");
        pertenencia(orgA,historico,pAnterior,null,true,"I");
        long pActual=persona(orgB,"Identidad local B reservada");rol(orgB,pActual,"ADMIN");
        pertenencia(orgB,historico,pActual,null,true,"A");
    }

    @Test void consultaNoColapsaFilasNiCruzaOrganizaciones() {
        long esperadas=jdbc.queryForObject("select count(*) from usuario_organizacion where organizacion_id=?",Long.class,orgA);
        var filas=cuentas.cuentasDeGobierno(orgA);assertEquals(esperadas,filas.size());
        assertTrue(filas.stream().anyMatch(f->f.idCuenta()==broker));
        assertTrue(filas.stream().anyMatch(f->f.idCuenta()==adminPuro));
        var idsLocales=new HashSet<>(jdbc.queryForList("select id_persona from persona where organizacion_id=?",Long.class,orgA));
        assertTrue(filas.stream().allMatch(f->idsLocales.contains(f.idPersona())));
        assertTrue(filas.stream().noneMatch(f->"Identidad local B reservada".equals(f.nombre())));
    }

    @Test void brokerYAdministracionPermanecenSeparadosYAdminPuroNoSePierde() {
        var filas=seguridad.cuentas(gobierno);
        var doble=filas.stream().filter(f->f.idCuenta()==broker).findFirst().orElseThrow();
        assertEquals(Actor.BROKER,doble.rolPrincipal());assertTrue(doble.administrar());assertTrue(doble.pertenenciaActiva());
        var puro=filas.stream().filter(f->f.idCuenta()==adminPuro).findFirst().orElseThrow();
        assertNull(puro.rolPrincipal());assertTrue(puro.administrar());
    }

    @Test void antecedenteLocalNoPublicaEstadoORestriccionesActualesEnOtraOrganizacion() {
        var antes=filaHistorica();assertFalse(antes.pertenenciaActiva());assertEquals("Antecedente local A",antes.nombre());
        jdbc.update("update cuenta_acceso set estado='I',debe_cambiar_contrasena=true,debe_enrolar_mfa=true where id_cuenta_acceso=?",historico);
        var despues=filaHistorica();assertEquals(antes,despues);
    }

    @Test void serializacionConservaCardinalidadYExponeSoloNueveCampos() throws Exception {
        var filas=seguridad.cuentas(gobierno);JsonNode json=new ObjectMapper().valueToTree(filas);
        assertTrue(json.isArray());assertEquals(cuentas.cuentasDeGobierno(orgA).size(),json.size());
        Set<String> permitidos=Set.of("idPersona","idCuenta","nombre","usuario","rolPrincipal","administrar","pertenenciaActiva","idMembresia","version");
        for(JsonNode fila:json) {
            var campos=new HashSet<String>();fila.fieldNames().forEachRemaining(campos::add);
            assertEquals(permitidos,campos);
        }
    }

    @Test void brokerSinAtribucionAdministrativaNoConsultaElPadron() {
        var brokerActor=new Actor(gobierno.idOrganizacion(),gobierno.idPersona(),rolBroker,Actor.BROKER);
        assertThrows(AccesoNoAutorizadoException.class,()->seguridad.cuentas(brokerActor));
    }

    private CuentaDeGobiernoFila filaHistorica() {
        return cuentas.cuentasDeGobierno(orgA).stream().filter(f->f.idCuenta()==historico).findFirst().orElseThrow();
    }
    private long cuenta(String nombre) {
        String ref=UUID.randomUUID().toString();var c=new CuentaAcceso();c.setNombreUsuario("padron-"+ref);
        c.setCorreoPropio("padron-"+ref+"@example.test");c.setCorreoConfirmadoEn(Instant.now());
        c.setNombreProfesionalDeclarado(nombre);c.setContrasenaHash("fixture-del-padron-no-autenticable");
        return cuentas.saveAndFlush(c).getId();
    }
    private long persona(long org,String nombre) {
        return jdbc.queryForObject("insert into persona(organizacion_id,tipo_persona,tipo_documento,numero_documento,nombres_o_razon_social) values(?,'N','P',?,?) returning id_persona",
                Long.class,org,UUID.randomUUID().toString().substring(0,25),nombre);
    }
    private long rol(long org,long persona,String tipo) {
        return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,?,?) returning id_persona_rol",Long.class,org,persona,tipo,LocalDate.now(ZoneId.of("America/Lima")));
    }
    private void pertenencia(long org,long cuenta,long persona,String principal,boolean administrar,String estado) {
        jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,?,?,?)",org,cuenta,persona,principal,administrar,estado);
    }
}
