package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.CoincidenciaService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.Categoria;
import com.controllocal.service.matching.ContratoMatching.Modalidad;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** CAT-E20/D4: Core real reautoriza el cursor; una referencia no transporta permisos. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class MatchingC3ContextoIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired RequerimientoService demandas;
    @Autowired CoincidenciaService coincidencias;
    private OrigenesDePrueba identidades;
    private OrigenesDePrueba.Equipo equipo;
    private Actor actor;
    private ContextoSesion sesion;
    private RequerimientoService.FichaRequerimiento demanda;
    @BeforeEach void origenReal(){
        identidades=new OrigenesDePrueba(jdbc,gestor,captura,contextos);equipo=identidades.empresa();
        actor=equipo.agente();sesion=identidades.sesion(actor);
        var contacto=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,new DatosContacto(null,null,null,null,"999000001",null));
        var datos=new RequerimientoService.DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",List.of("DEPARTAMENTO"),
                null,null,null,null,null,null,null,"Contexto matching TEST",null,null,contacto,null,null,null,null,Set.of());
        demanda=demandas.crear(datos,actor,sesion,UUID.randomUUID().toString(),Procedencia.deLaPantalla());
    }
    @Test void vacioLegitimoTieneCorteCoberturaYContinuidadSinFabricarOferta(){
        var r=buscar();assertEquals(EstadoResultado.DISPONIBLE,r.estado());assertEquals(0L,r.total());
        assertEquals(Cobertura.COMPLETA,r.cobertura());assertTrue(r.grupos().isEmpty());
        assertNotNull(r.corte());assertTrue(r.referenciaConsulta().matches("[A-Za-z0-9_-]{43}"));
        var continuada=coincidencias.continuar(r.referenciaConsulta(),actor,sesion);
        assertEquals(r.corte(),continuada.corte());assertEquals(0L,continuada.total());
        assertEquals(0L,jdbc.queryForObject("select count(*) from captacion where organizacion_id=?",Long.class,actor.idOrganizacion()));
    }
    @Test void mismoActorEnOtroSidNoHeredaRecorrido(){
        var r=buscar();var otraSesion=identidades.sesion(actor);
        assertNotEquals(sesion.identidad(),otraSesion.identidad());
        assertThrows(NoEncontradoException.class,()->coincidencias.continuar(r.referenciaConsulta(),actor,otraSesion));
    }
    @Test void colegaMismoTenantNoHeredaCursorNiDemanda(){
        var r=buscar();var otro=equipo.colega();var sid=identidades.sesion(otro);
        assertThrows(NoEncontradoException.class,()->coincidencias.continuar(r.referenciaConsulta(),otro,sid));
    }
    @Test void otroTenantNoDistingueCursorPrivado(){
        var r=buscar();var otro=identidades.empresa().agente();var sid=identidades.sesion(otro);
        assertThrows(NoEncontradoException.class,()->coincidencias.continuar(r.referenciaConsulta(),otro,sid));
    }
    @Test void sidCerradoNoReutilizaResultadoVacioAnterior(){
        var r=buscar();contextos.cerrar(sesion.referenciaSesion(),sesion.revisionContexto());
        // C1: el SID revocado invalida autenticación (401), antes de autorizar el recurso.
        assertThrows(CredencialesInvalidasException.class,()->coincidencias.continuar(r.referenciaConsulta(),actor,sesion));
    }
    @Test void versionObservadaIncorrectaNoSeCompletaConLaActual(){
        assertThrows(ConflictoException.class,()->coincidencias.consultar(consulta(demanda.version()+1),actor,sesion));
        assertEquals(0L,jdbc.queryForObject("select count(*) from matching_recorrido where organizacion_id=?",Long.class,actor.idOrganizacion()));
    }
    private Coincidencias buscar(){return coincidencias.consultar(consulta(demanda.version()),actor,sesion);}
    private Consulta consulta(long version){return new Consulta(Origen.DEMANDA,demanda.id(),version,TipoVersion.DEMANDA,
            Modalidad.PRELIMINAR,Set.of(Capa.L0),Categoria.POTENCIAL,20,null);}
}
