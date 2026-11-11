package com.controllocal.service.planes;

import com.controllocal.domain.planes.PlanBrox;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.presupuesto.ComprasManualesB0Repository.Compra;
import com.controllocal.persistence.presupuesto.ComprasManualesB0Repository.Evento;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import static com.controllocal.service.planes.DatosCompraManual.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Contrato de lectura/consultas con dobles explícitos. No demuestra latencia, PG o autoridad real. */
class ComprasManualesListadoTest {
    final ContextosService contextos=mock(ContextosService.class);
    final AtribucionesPlataforma atribuciones=mock(AtribucionesPlataforma.class);
    final ComprasManualesB0Repository repo=mock(ComprasManualesB0Repository.class);
    final PresupuestoIaRepository presupuesto=mock(PresupuestoIaRepository.class);
    final CiclosPagadosB0Repository ciclos=mock(CiclosPagadosB0Repository.class);
    final CiclosPagadosB0 acreditacion=mock(CiclosPagadosB0.class);
    final Documentos json=new Documentos(JsonMapper.builder().findAndAddModules().build());
    final Instant ahora=Instant.parse("2026-11-03T20:00:00Z");
    final Actor admin=new Actor(7,11,13,Actor.TENANT_ADMIN);
    final ContextosService.SesionAccesoDTO acceso=new ContextosService.SesionAccesoDTO("SID_LISTADO_TEST",1,
            new ContextosService.IdentidadAutenticada(77,"TEST","test@example.invalid","TEST"),
            new ContextosService.Contexto("CTX_TEST",7,11,13,Actor.TENANT_ADMIN,"EMPRESARIAL","TEST"),
            ahora.minusSeconds(10),ahora.plusSeconds(600),EstadoDeAcceso.SIN_RESTRICCIONES);
    final ContextoSesion sid=ContextoSesion.de(acceso);
    final SesionPlataforma financiero=new SesionPlataforma("SID_FINANCIERO_TEST",1,UUID.randomUUID());
    final Set<Campo> campos=Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL);
    ComprasManualesB0 servicio;
    @BeforeEach void preparar(){
        when(contextos.exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),admin)).thenReturn(acceso);
        when(presupuesto.bloquearOrganizacion(admin.idOrganizacion())).thenReturn(new PresupuestoIaRepository.Organizacion("EMPRESARIAL",true));
        servicio=new ComprasManualesB0(contextos,atribuciones,repo,presupuesto,ciclos,acreditacion,json,"",Clock.fixed(ahora,ZoneOffset.UTC));
    }
    List<DatosCompraManual.Compra> listar(boolean plataforma){return plataforma?servicio.listarFinanciero(financiero,20,3):servicio.listar(admin,sid,20,3);}
    List<Evento> historia(){return List.of(new Evento("PREPARADA",ahora.minusSeconds(300),"PENDIENTE"),
            new Evento("CONSENTIDA",ahora.minusSeconds(200),"PENDIENTE"),new Evento("FONDOS_VERIFICADOS",ahora.minusSeconds(100),"EN_VERIFICACION"));}
    Compra compra(boolean conFondos){
        var t=new Terminos(new Seleccion(ComprasVerificadasB0.Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0),"TEST_OFERTA",ahora,null,null,
                new BigDecimal("75.00"),new BigDecimal("0.00"),new BigDecimal("75.00"),"USD",null,null,"TEST_REGLA");
        var v=new Verificacion(new FechaPago(LocalDate.of(2026,11,3),null,"CUENTA_BANCARIA_PRIVADA_TEST"),"BANCO_PRIVADO_TEST",
                "TRANSACCION_PRIVADA_TEST",new BigDecimal("75.00"),"USD","EVIDENCIA_BANCARIA_PRIVADA_TEST");
        String fondos=conFondos?json.objetoCanonico(new Fondos(v,777,UUID.randomUUID(),ahora.minusSeconds(100))):null;
        return new Compra(UUID.randomUUID(),admin.idOrganizacion(),77,"a".repeat(64),json.objetoCanonico(t),ahora.minusSeconds(400),77L,
                ahora.minusSeconds(200),"b".repeat(64),conFondos?"EN_VERIFICACION":"PENDIENTE",conFondos?3:2,null,fondos,null);
    }
    void permisoAntesDeListar(boolean plataforma,Long org,List<UUID> ids){
        var orden=inOrder(contextos,presupuesto,atribuciones,repo);
        if(plataforma)orden.verify(atribuciones).exigirCompra(financiero,Accion.CONSULTAR_COMPRA,campos);
        else{
            orden.verify(contextos).exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),admin);
            orden.verify(presupuesto).bloquearOrganizacion(admin.idOrganizacion());
            orden.verify(contextos).exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),admin);
        }
        orden.verify(repo).listar(org,20,3);if(!ids.isEmpty())orden.verify(repo).historiales(ids);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void paginaVaciaLeeUnaPaginaSinConsultaDeHistoria(boolean plataforma){
        Long org=plataforma?null:admin.idOrganizacion();when(repo.listar(org,20,3)).thenReturn(List.of());
        assertTrue(listar(plataforma).isEmpty());permisoAntesDeListar(plataforma,org,List.of());
        verify(repo).listar(org,20,3);verifyNoMoreInteractions(repo);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void variasComprasConservanOrdenHistoriaCompletaYProyeccionSinNMasUno(boolean plataforma){
        var a=compra(false);var b=compra(true);var ids=List.of(b.id(),a.id());Long org=plataforma?null:admin.idOrganizacion();
        var historiaB=historia();var historiaA=historiaB.subList(0,2);
        when(repo.listar(org,20,3)).thenReturn(List.of(b,a));when(repo.historiales(ids)).thenReturn(Map.of(a.id(),historiaA,b.id(),historiaB));
        var resultado=listar(plataforma);assertEquals(ids,resultado.stream().map(DatosCompraManual.Compra::referencia).toList());
        assertEquals(3,resultado.getFirst().historial().size());assertEquals(2,resultado.getLast().historial().size());
        assertEquals(historiaB.stream().map(e->new DatosCompraManual.Evento(e.tipo(),e.registradaEn(),e.estado())).toList(),resultado.getFirst().historial());
        assertEquals(historiaA.stream().map(e->new DatosCompraManual.Evento(e.tipo(),e.registradaEn(),e.estado())).toList(),resultado.getLast().historial());
        assertEquals(Estado.EN_VERIFICACION,resultado.getFirst().estado());assertEquals(b.version(),resultado.getFirst().version());
        assertTrue(resultado.getFirst().consentida());assertEquals(new BigDecimal("75.00"),resultado.getFirst().terminos().totalUsd());
        assertEquals(LocalDate.of(2026,11,3),resultado.getFirst().pago().fecha());assertNull(resultado.getFirst().pago().instante());
        assertEquals(ahora.minusSeconds(100),resultado.getFirst().verificadaEn());assertNull(resultado.getLast().pago());
        String publica=json.lista(resultado);assertFalse(publica.contains("PRIVADA_TEST"));assertFalse(publica.contains("BANCO_PRIVADO_TEST"));
        assertFalse(publica.contains("\"fondos\""));assertFalse(publica.contains("\"cuenta\""));assertFalse(publica.contains("\"concesion\""));
        permisoAntesDeListar(plataforma,org,ids);verify(repo).listar(org,20,3);verify(repo).historiales(ids);verifyNoMoreInteractions(repo);
        verifyNoInteractions(ciclos,acreditacion);
    }
    @Test void compradorDenegadoNoConsultaPaginaNiHistoria(){
        doThrow(new AccesoNoAutorizadoException()).when(contextos).exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),admin);
        assertThrows(AccesoNoAutorizadoException.class,()->listar(false));verifyNoInteractions(repo);
    }
    @Test void financieroSinCamposNoConsultaPaginaNiHistoria(){
        doThrow(new AccesoNoAutorizadoException()).when(atribuciones).exigirCompra(financiero,Accion.CONSULTAR_COMPRA,campos);
        assertThrows(AccesoNoAutorizadoException.class,()->listar(true));verifyNoInteractions(repo);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void detalleConservaSuConsultaIndividualDeHistoria(boolean plataforma){
        var c=compra(true);when(repo.consultar(c.id())).thenReturn(Optional.of(c));when(repo.historial(c.id())).thenReturn(historia());
        var r=plataforma?servicio.consultarFinanciero(financiero,c.id()).compra():servicio.consultar(admin,sid,c.id());
        assertEquals(3,r.historial().size());assertEquals(c.id(),r.referencia());
        verify(repo).consultar(c.id());verify(repo).historial(c.id());verifyNoMoreInteractions(repo);
    }
    @Test void repositorioVacioNoConsultaNingunaTabla(){
        var jdbc=mock(JdbcTemplate.class);assertTrue(new ComprasManualesB0Repository(jdbc).historiales(List.of()).isEmpty());verifyNoInteractions(jdbc);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void repositorioAgrupaSoloIdsParametrizadosConOrdenHistoricoYUnaConsulta()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var filas=mock(ResultSet.class);
        UUID a=UUID.fromString("00000000-0000-0000-0000-000000000001"),b=UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(filas.next()).thenReturn(true,true,true,false);when(filas.getObject("id_compra",UUID.class)).thenReturn(a,a,b);
        when(filas.getString("tipo")).thenReturn("PREPARADA","CONSENTIDA","PREPARADA");
        when(filas.getString("estado")).thenReturn("PENDIENTE","PENDIENTE","PENDIENTE");
        when(filas.getObject("registrada_en",OffsetDateTime.class)).thenReturn(ahora.minusSeconds(300).atOffset(ZoneOffset.UTC),ahora.minusSeconds(200).atOffset(ZoneOffset.UTC),ahora.minusSeconds(100).atOffset(ZoneOffset.UTC));
        doAnswer(i->((ResultSetExtractor)i.getArgument(1)).extractData(filas)).when(jdbc).query(anyString(),any(ResultSetExtractor.class),any(Object[].class));
        var resultado=new ComprasManualesB0Repository(jdbc).historiales(List.of(b,a));
        assertEquals(List.of("PREPARADA","CONSENTIDA"),resultado.get(a).stream().map(Evento::tipo).toList());assertEquals(1,resultado.get(b).size());
        assertEquals(ahora.minusSeconds(200),resultado.get(a).getLast().registradaEn());assertEquals("PENDIENTE",resultado.get(a).getLast().estado());
        var sql=ArgumentCaptor.forClass(String.class);var parametros=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(),any(ResultSetExtractor.class),parametros.capture());verifyNoMoreInteractions(jdbc);
        assertArrayEquals(new Object[]{b,a},parametros.getValue());assertTrue(sql.getValue().contains("WHERE id_compra IN (?,?)"));
        assertTrue(sql.getValue().endsWith("ORDER BY id_compra,version_compra,registrada_en,id_comando"));
        assertTrue(sql.getValue().startsWith("SELECT id_compra,tipo,registrada_en,estado "));assertFalse(sql.getValue().contains(a.toString()));assertFalse(sql.getValue().contains(b.toString()));
        assertThrows(UnsupportedOperationException.class,()->resultado.get(a).clear());
    }
}
