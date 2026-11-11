package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Autoridad/metadata TEST; sin DB, binarios reales ni habilitación de transferencias. */
class ArchivosEvaluacionIaPermisosTest {
    final EvaluacionesIaB0Repository repo=mock(EvaluacionesIaB0Repository.class);
    final AtribucionesPlataforma autoridad=mock(AtribucionesPlataforma.class);
    final EvaluacionesIaB0 servicio=new EvaluacionesIaB0(repo,autoridad,new Documentos(new ObjectMapper().findAndRegisterModules()));
    final UUID plan=UUID.randomUUID(),caso=UUID.randomUUID(),archivo=UUID.randomUUID(),b0=UUID.randomUUID();
    final SesionPlataforma sesion=new SesionPlataforma("SID_SINTETICO_TEST",1,UUID.randomUUID());
    final String namespace="a".repeat(64),hash="b".repeat(64);
    void preparar(){
        when(autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.INICIAR,Campo.ESTADO))
                .thenReturn(new Actual(71,UUID.randomUUID(),plan,"V_TEST","EVALUACION_TEST",caso,null));
        when(repo.caso(caso)).thenReturn(Optional.of(new Caso(caso,plan,"V_TEST","CASO_TEST",hash,
                "SINTETICO_TEST","DATOS_TEST",archivo,3,"image/png")));
        when(repo.plan(plan,"V_TEST")).thenReturn(Optional.of(new Plan(plan,"V_TEST",hash,"EVALUACION_TEST","{}",
                "MANIFIESTO_TEST","RUBRICA_TEST","SIN_POLITICA_REAL_TEST","DESTINO_TEST",namespace,BigDecimal.ONE,2,100,1)));
    }
    Archivo original(String huella){return new Archivo(archivo,caso,null,"ORIGINAL_CORPUS","VERIFICADA",1,
            UUID.randomUUID(),1,namespace,"CLAVE_TEST",huella,3,"image/png");}
    @Test void exigeIniciarAntesDeConsultarCualquierFuente(){
        when(autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.INICIAR,Campo.ESTADO)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirOriginalParaProcesar(caso,sesion));
        verifyNoInteractions(repo);
    }
    @Test void lecturaInternaReutilizaElOriginalExactoYSoloAutoridadOp(){
        preparar();when(repo.archivo(caso,archivo)).thenReturn(Optional.of(original(hash)));
        assertEquals(hash,servicio.exigirOriginalParaProcesar(caso,sesion).huella());
        verify(autoridad).resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.INICIAR,Campo.ESTADO);
        verifyNoMoreInteractions(autoridad);
    }
    @Test void huellaDistintaNoSeConvierteEnCopiaAutorizada(){
        preparar();when(repo.archivo(caso,archivo)).thenReturn(Optional.of(original("c".repeat(64))));
        assertThrows(ConflictoException.class,()->servicio.exigirOriginalParaProcesar(caso,sesion));
    }
    @Test void resultadoNoPuedeProcederDeOtroClaimDelMismoCaso(){
        preparar();UUID comando=UUID.randomUUID();when(repo.identidadOperacion(caso,comando)).thenReturn(Optional.of(b0));
        when(repo.archivo(caso,archivo)).thenReturn(Optional.of(new Archivo(archivo,caso,UUID.randomUUID(),"RESULTADO",
                "VERIFICADA",1,UUID.randomUUID(),1,namespace,"CLAVE_TEST",hash,3,"image/png")));
        assertThrows(ConflictoException.class,()->servicio.exigirResultadoParaProcesar(new PresupuestoIaService.ReferenciaEvaluacion(caso,comando),archivo,sesion));
        verify(repo).exigirClaim(caso,b0);
    }
}
