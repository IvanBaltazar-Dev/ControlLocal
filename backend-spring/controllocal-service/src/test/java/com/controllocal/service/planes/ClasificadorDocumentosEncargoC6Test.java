package com.controllocal.service.planes;

import com.controllocal.domain.comercial.DocumentoEncargo;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.encargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sólo autorización/clasificación. La cuota y la existencia del binario se verifican en sus pruebas PG. */
class ClasificadorDocumentosEncargoC6Test {
    private final Actor a=new Actor(1,10,20,Actor.AGENTE);
    private final DocumentoEncargoRepository docs=mock(DocumentoEncargoRepository.class);
    private final EncargoService encargos=mock(EncargoService.class);
    private final AccesoEncargo acceso=mock(AccesoEncargo.class);
    private final ContextoPresupuestoB0 contexto=mock(ContextoPresupuestoB0.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<EncargoService> proveedor=mock(ObjectProvider.class);
    private final DocumentoEncargo d=mock(DocumentoEncargo.class);
    private final UUID ref=UUID.randomUUID();
    private ClasificadorDocumentosEncargoC6 clasificador;
    private EncargoService.Documento ficha(long revision){return new EncargoService.Documento(5,ref,null,0,"a".repeat(64),"ENCARGO_MVP_V1",List.of(),OffsetDateTime.parse("2026-10-31T12:00:00-05:00"),revision);}
    @BeforeEach void iniciar(){
        when(proveedor.getObject()).thenReturn(encargos);when(d.getId()).thenReturn(5L);when(d.getIdAutor()).thenReturn(20L);when(d.getHuella()).thenReturn("a".repeat(64));
        when(docs.findByOrganizacionIdAndArchivoRef(1,ref)).thenReturn(Optional.of(d));when(encargos.consultarDocumento(5,a)).thenReturn(ficha(0));
        clasificador=new ClasificadorDocumentosEncargoC6(docs,proveedor,acceso,new Documentos(JsonMapper.builder().findAndAddModules().build()),contexto);
    }
    private UsoAlmacenamientoB0.Solicitud solicitud(String tipo,String referencia,String finalidad){return new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),
            new UsoAlmacenamientoB0.Recurso(tipo,referencia,finalidad,"a".repeat(64)),100,"b".repeat(64),null);}
    private DocumentosEncargo.Origen origen(String tipo,String referencia,String finalidad){return new DocumentosEncargo.Origen(UUID.randomUUID(),a,
            new ContextoSesion("TEST",Long.MAX_VALUE,"SID-TEST",1),tipo,referencia,finalidad,"a".repeat(64));}
    @Test void preparacionPropiaSinEncargoNoInventaCaptacionNiExencion(){
        var c=clasificador.clasificar(solicitud("DOCUMENTO_ENCARGO_C6",ref.toString(),"PDF_ENCARGO"),a);
        assertEquals(CategoriaAlmacenamiento.DOCUMENTO,c.categoria());assertEquals(ClasificadorCargaAlmacenamientoB0.Necesidad.ORDINARIA,c.necesidad());
        verify(encargos).consultarDocumento(5,a);verifyNoInteractions(acceso);
    }
    @Test void fuenteDeOtraOrganizacionNoEsUnaCuotaAutorizada(){
        var ajeno=new Actor(2,10,20,Actor.AGENTE);
        assertThrows(NoEncontradoException.class,()->clasificador.clasificar(solicitud("DOCUMENTO_ENCARGO_C6",ref.toString(),"PDF_ENCARGO"),ajeno));
        verifyNoInteractions(encargos);
    }
    @Test void autoriaDeOtroRolNoAutorizaSuNuevoBinario(){
        when(d.getIdAutor()).thenReturn(30L);
        assertThrows(AccesoNoAutorizadoException.class,()->clasificador.clasificar(solicitud("DOCUMENTO_ENCARGO_C6",ref.toString(),"PDF_ENCARGO"),a));
        verifyNoInteractions(encargos);
    }
    @Test void contenidoDistintoNoReutilizaLaFuentePreparada(){
        when(d.getHuella()).thenReturn("c".repeat(64));
        assertThrows(AccesoNoAutorizadoException.class,()->clasificador.preparar(origen("DOCUMENTO_ENCARGO_C6",ref.toString(),"PDF_ENCARGO")));
    }
    @Test void cargarEvidenciaNoConcedeOperacionAlGobierno(){
        var gobierno=new Actor(1,10,20,Actor.TENANT_ADMIN);
        assertThrows(AccesoNoAutorizadoException.class,()->clasificador.clasificar(solicitud("EVIDENCIA_CONTEXTO_ENCARGO_C6","ENCARGO:9","ACEPTACION_ENCARGO"),gobierno));
        verifyNoInteractions(acceso,encargos);
    }
    @Test void laRevisionDocumentalActualFormaParteDeLaFuente(){
        var s=solicitud("DOCUMENTO_ENCARGO_C6",ref.toString(),"PDF_ENCARGO");var anterior=clasificador.clasificar(s,a);
        when(encargos.consultarDocumento(5,a)).thenReturn(ficha(1));
        assertNotEquals(anterior.huellaFuente(),clasificador.clasificar(s,a).huellaFuente());
    }
    @Test void admisionConservaLaHuellaDelContextoSinConfundirlaConElContenido(){
        var o=origen("DOCUMENTO_ENCARGO_C6",ref.toString(),"PDF_ENCARGO");var r=clasificador.preparar(o);
        var s=new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),r,100,"b".repeat(64),null);
        assertNotEquals(o.huellaOrigen(),r.huellaOrigen());assertEquals(r.huellaOrigen(),clasificador.clasificar(s,a).huellaFuente());
        verify(contexto).iniciarArchivo(a,o.sesion());
        when(encargos.consultarDocumento(5,a)).thenReturn(ficha(1));
        assertNotEquals(r.huellaOrigen(),clasificador.clasificar(s,a).huellaFuente());
    }
    @Test void evidenciaDocumentalUsaLaVersionDelDestinoNoLaHuellaDelComando(){
        when(docs.findByOrganizacionIdAndId(1,5)).thenReturn(Optional.of(d));
        var o=origen("EVIDENCIA_CONTEXTO_ENCARGO_C6","DOCUMENTO:5","ACEPTACION_ENCARGO");var r=clasificador.preparar(o);
        var s=new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),r,100,"b".repeat(64),null);
        assertNotEquals(o.huellaOrigen(),r.huellaOrigen());assertEquals(r.huellaOrigen(),clasificador.clasificar(s,a).huellaFuente());
    }
    @Test void sidRevocadoSeDeniegaAntesDeConsultarElDocumento(){
        var o=origen("DOCUMENTO_ENCARGO_C6",ref.toString(),"PDF_ENCARGO");
        doThrow(new AccesoNoAutorizadoException()).when(contexto).iniciarArchivo(a,o.sesion());
        assertThrows(AccesoNoAutorizadoException.class,()->clasificador.preparar(o));
        verifyNoInteractions(docs,encargos,acceso);
    }
}
