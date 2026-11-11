package com.controllocal.integracion.soporte;

import com.controllocal.domain.planes.CalendarioComercial;
import com.controllocal.domain.planes.PlanBrox;
import com.controllocal.persistence.presupuesto.CiclosPagadosB0Repository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Financiación comercial SINTÉTICA de fixtures empresariales que necesitan documentos.
 * No PSP, pago real, promoción, consentimiento, perfil IA o fondo técnico acreditados.
 * No es un bean, seed ni acceso productivo. El test decide explícitamente cuándo usarlo.
 */
public final class PlanesFinanciadosDePrueba {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    private final PresupuestoIaRepository presupuestos;private final CiclosPagadosB0Repository ciclos;
    public PlanesFinanciadosDePrueba(JdbcTemplate jdbc,PlatformTransactionManager gestor){
        this.jdbc=jdbc;this.tx=new TransactionTemplate(gestor);this.presupuestos=new PresupuestoIaRepository(jdbc);this.ciclos=new CiclosPagadosB0Repository(jdbc);
    }
    /** No sustituye un plan vigente ni reabre, renueva o borra una historia existente. */
    public UUID asegurarTeam(long organizacion){
        if(organizacion<=0)throw new IllegalArgumentException("El fixture requiere una organización empresarial existente.");
        return tx.execute(t->{
            String modalidad=jdbc.queryForObject("SELECT modalidad FROM organizacion WHERE id_organizacion=? FOR NO KEY UPDATE",String.class,organizacion);
            if(!"EMPRESARIAL".equals(modalidad))throw new IllegalArgumentException("Este fixture no concede Team a un espacio individual.");
            Instant ahora=Instant.now().truncatedTo(ChronoUnit.MICROS);
            var actual=presupuestos.cicloVigente(organizacion,ahora);
            if(actual.isPresent()){
                if(actual.get().revocado()||!PlanBrox.valueOf(actual.get().plan()).empresarial())
                    throw new IllegalStateException("El fixture no reemplaza un ciclo revocado o de otra modalidad.");
                return actual.get().id();
            }
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM b0_ciclo WHERE organizacion_id=?)",Boolean.class,organizacion)))
                throw new IllegalStateException("El fixture debe tratar expresamente el período histórico o futuro existente; no renueva a ciegas.");
            PlanBrox plan=PlanBrox.TEAM;UUID ciclo=UUID.randomUUID();int aniversario=ahora.atZone(CalendarioComercial.ZONA).getDayOfMonth();
            Instant hasta=CalendarioComercial.siguienteMes(ahora,aniversario);
            ciclos.nuevoCiclo(organizacion,ciclo,plan.name(),ahora,hasta,aniversario,plan.precioUsd(),UUID.randomUUID(),
                    "FINANCIACION SINTETICA TEST: NO PAGO PSP REAL NI CONSENTIMIENTO EXTERNO",0,ahora);
            for(int numero=1;numero<=plan.plazasAgente();numero++){
                UUID fuente=ciclos.fuente(organizacion,ciclo,"MENSUAL_AGENTE",numero,BigDecimal.valueOf(plan.cuotaAgente()),
                        "TEST-CICLO:"+ciclo+":AGENTE:"+numero,ahora,hasta,ahora);
                ciclos.plaza(organizacion,ciclo,"AGENTE",numero,fuente);
            }
            UUID broker=ciclos.fuente(organizacion,ciclo,"MENSUAL_BROKER",null,BigDecimal.valueOf(plan.bolsaBroker()),"TEST-CICLO:"+ciclo+":BROKER",ahora,hasta,ahora);
            ciclos.plaza(organizacion,ciclo,"BROKER",1,broker);
            ciclos.fuente(organizacion,ciclo,"RESERVA",null,BigDecimal.valueOf(plan.reservaAsignable()),"TEST-CICLO:"+ciclo+":RESERVA",ahora,hasta,ahora);
            return ciclo;
        });
    }
}
