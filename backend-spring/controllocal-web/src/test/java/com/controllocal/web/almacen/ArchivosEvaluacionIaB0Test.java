package com.controllocal.web.almacen;

import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.presupuesto.EvaluacionesIaB0;
import com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Fuente/almacén TEST. Sólo la lectura interna; no inferencia, DB ni evidencia real E44. */
class ArchivosEvaluacionIaB0Test {
    final EvaluacionesIaB0 casos=mock(EvaluacionesIaB0.class);
    final AlmacenDocumentos almacen=mock(AlmacenDocumentos.class);
    final ArchivosEvaluacionIaB0 servicio=new ArchivosEvaluacionIaB0(casos,mock(UsoAlmacenamientoB0.class),
            mock(SalidaBinariaPersistente.class),mock(CuotaArchivosB0.class),almacen,mock(PlatformTransactionManager.class),mock(com.controllocal.service.presupuesto.PresupuestoIaService.class),mock(com.controllocal.service.archivo.SeguimientoSalidaBinaria.class));
    final UUID caso=UUID.randomUUID();final SesionPlataforma sesion=new SesionPlataforma("SID_TEST",1,UUID.randomUUID());
    final byte[] bytes={1,2,3};final String namespace="a".repeat(64);
    final EvaluacionesIaB0.ArchivoMuestra archivo=new EvaluacionesIaB0.ArchivoMuestra(namespace,"CLAVE_TEST",3,
            EvaluacionTecnicaFoto.huella(bytes),"image/png","VERIFICADA");
    void preparar(){
        when(casos.exigirOriginalParaProcesar(caso,sesion)).thenReturn(archivo);
        when(almacen.identidadTecnica()).thenReturn(namespace);
        when(almacen.abrir("CLAVE_TEST")).thenReturn(Optional.of(new AlmacenDocumentos.ArchivoDescargado(bytes,"NOMBRE_TEST.png","image/png")));
    }
    @Test void denegacionAntesDeLeerNoConsultaElAlmacen(){
        when(casos.exigirOriginalParaProcesar(caso,sesion)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.leerOriginalParaProcesar(caso,sesion));
        verifyNoInteractions(almacen);
    }
    @Test void revocacionAlRevalidarNoEntregaBytes(){
        preparar();when(casos.exigirOriginalParaProcesar(caso,sesion)).thenReturn(archivo).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.leerOriginalParaProcesar(caso,sesion));
        verify(casos,times(2)).exigirOriginalParaProcesar(caso,sesion);verify(almacen).abrir("CLAVE_TEST");
    }
    @Test void cambioDeNamespaceDuranteLecturaImpideEntrega(){
        preparar();when(almacen.identidadTecnica()).thenReturn(namespace,"b".repeat(64));
        assertThrows(ConflictoException.class,()->servicio.leerOriginalParaProcesar(caso,sesion));
    }
}
