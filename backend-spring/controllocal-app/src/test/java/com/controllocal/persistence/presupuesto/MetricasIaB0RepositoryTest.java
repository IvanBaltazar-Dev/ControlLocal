package com.controllocal.persistence.presupuesto;

import com.controllocal.persistence.presupuesto.MetricasIaB0Repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Fallos y enlace de parámetros; el SQL real se prueba además en MetricasIaB0IntegrationTest. */
@SuppressWarnings({"unchecked","rawtypes"})
class MetricasIaB0RepositoryTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final MetricasIaB0Repository repo=new MetricasIaB0Repository(jdbc);
    final UUID plan=UUID.randomUUID(),caso=UUID.randomUUID(),fondo=UUID.randomUUID(),comando=UUID.randomUUID();
    final Instant desde=Instant.parse("2026-11-01T00:00:00Z"),hasta=desde.plusSeconds(60);
    Ambito ambito(){return new Ambito(plan,"VERSION_TEST","FINALIDAD_TEST",caso,fondo,desde,hasta);}
    @Test void cohorteSeEnlazaCompletaEnUnaSentenciaSinLeerImportesEnOps(){
        var fila=new TecnicaFila(hasta,0,0,0,0,0,"[]","[]","[]","[]");
        doReturn(List.of(fila)).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        assertSame(fila,repo.tecnica(new Ambito(plan,"VERSION_TEST","FINALIDAD_TEST",caso,null,desde,hasta)));
        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(),any(RowMapper.class),args.capture());
        assertArrayEquals(new Object[]{plan,"VERSION_TEST","FINALIDAD_TEST",caso,null,desde.atOffset(ZoneOffset.UTC),hasta.atOffset(ZoneOffset.UTC),null},args.getValue());
        assertTrue(sql.getValue().contains("c.plan=q.plan"));assertTrue(sql.getValue().contains("c.version_plan=q.version"));
        assertTrue(sql.getValue().contains("p.id=c.plan AND p.version=c.version_plan"));
        assertFalse(sql.getValue().contains("o.perfil"));assertFalse(sql.getValue().contains("o.version_perfil"));
        assertTrue(sql.getValue().contains("o.organizacion_id IS NULL"));assertTrue(sql.getValue().contains("o.admitida_en<q.hasta"));
        assertFalse(sql.getValue().contains("costo_usd"));assertFalse(sql.getValue().contains("tarifas_originales"));
        assertFalse(sql.getValue().contains("SUM((u->>'cantidad')"));
    }
    @Test void unaLecturaTecnicaSinFilaNoInventaUniversoVacio(){
        doReturn(List.of()).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        assertThrows(IllegalStateException.class,()->repo.tecnica(ambito()));
    }
    @Test void financieraSinCasoFondoOComandoNoTocaJdbc(){
        assertThrows(IllegalArgumentException.class,()->repo.financiera(new Ambito(plan,"V_TEST","F_TEST",null,fondo,desde,hasta),comando));
        assertThrows(IllegalArgumentException.class,()->repo.financiera(new Ambito(plan,"V_TEST","F_TEST",caso,null,desde,hasta),comando));
        assertThrows(IllegalArgumentException.class,()->repo.financiera(ambito(),null));verifyNoInteractions(jdbc);
    }
    @Test void noEligeLaPrimeraSiLaOperacionFinancieraEsAmbigua(){
        var fila=new FinancieraFila(hasta,caso,comando,"INCIERTA",desde,desde,null,null,"[]","[]","[]",null,null,null,null,"[]");
        doReturn(List.of(fila,fila)).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        assertThrows(IllegalStateException.class,()->repo.financiera(ambito(),comando));
    }
    @Test void consultaSinAuditoriaConfirmadaFalla(){
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
        assertThrows(IllegalStateException.class,()->repo.auditar(9,UUID.randomUUID(),ambito(),"FINANCIERA",comando));
    }
}
