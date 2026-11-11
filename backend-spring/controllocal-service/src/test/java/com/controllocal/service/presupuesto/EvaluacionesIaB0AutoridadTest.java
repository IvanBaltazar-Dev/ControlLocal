package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository.Caso;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvaluacionesIaB0AutoridadTest {
    @Test void registrarCasoExigeAcreditacionAntesDeConsultarPlan(){
        var repo=mock(EvaluacionesIaB0Repository.class);
        var autoridad=mock(AtribucionesPlataforma.class);
        var servicio=new EvaluacionesIaB0(repo,autoridad,new Documentos(new ObjectMapper().findAndRegisterModules()));
        var decision=UUID.randomUUID();
        var sesion=new SesionPlataforma("SID_TEST",1,UUID.randomUUID());
        var caso=new Caso(UUID.randomUUID(),UUID.randomUUID(),"V_TEST","CASO_TEST","a".repeat(64),
                "PROCEDENCIA_TEST","LEGITIMACION_TEST",UUID.randomUUID(),3,"image/png");
        when(autoridad.acreditar(eq(decision),eq("REGISTRAR_CASO_EVALUACION"),anyString(),isNull(),eq(sesion)))
                .thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.registrarCaso(caso,decision,sesion));
        verifyNoInteractions(repo);
    }
}
