package com.controllocal.service.impl;
import com.controllocal.service.*;
import com.controllocal.service.ReportePropietarioService.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.*;
import com.controllocal.service.encargo.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.documentos.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.impl.ReportePropietarioServiceImpl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** RF078/079 conserva autoridad original antes de leer cuerpos e historia sin reinterpretación. */
class ReportePropietarioServiceImplTest {
    final ReporteAvanceRepository repo=mock(ReporteAvanceRepository.class);
    final AvanceDeReporte avance=mock(AvanceDeReporte.class);
    final AccesoEncargo acceso=mock(AccesoEncargo.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final ContactosDeInteraccion contactos=mock(ContactosDeInteraccion.class);
    final InteraccionService interacciones=mock(InteraccionService.class);
    final AccesoOportunidad oportunidades=mock(AccesoOportunidad.class);
    final ActividadInformeC7Repository actividad=mock(ActividadInformeC7Repository.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();final Documentos json=new Documentos(mapper);
    final ComandoIdempotenteRepository recibos=mock(ComandoIdempotenteRepository.class);
    final ComandosIdempotentes comandos=mock(ComandosIdempotentes.class);
    final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    final ArchivoEncargoRepository archivos=mock(ArchivoEncargoRepository.class);
    @SuppressWarnings("unchecked") final ObjectProvider<DocumentosReporteAvance> pdf=mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") final ObjectProvider<AccesoArchivoEncargo> accesoArchivos=mock(ObjectProvider.class);
    final ReportePropietarioServiceImpl s=new ReportePropietarioServiceImpl(repo,avance,acceso,contexto,contactos,interacciones,oportunidades,actividad,json,recibos,comandos,eventos,archivos,pdf,accesoArchivos);
    final Actor agente=new Actor(1,3,30,"AGENTE"),broker=new Actor(1,2,23,"BROKER"),admin=new Actor(1,1,20,"TENANT_ADMIN");
    final Instant ahora=Instant.parse("2026-11-10T12:00:00Z");final Referencia ref=new Referencia(UUID.randomUUID(),0L);
    final ReferenciaContactoContextual contacto=new ReferenciaContactoContextual(TipoContacto.PERSONA,44L,null,null);
    final ReferenciaVersion fuente=new ReferenciaVersion(88,2);
    final UUID archivo=UUID.randomUUID();
    ReporteAvanceRepository.Cabecera cabecera;
    @BeforeEach void montar(){
        var fuentes=new Fuentes(List.of(fuente),List.of(new ActividadInformeC7Repository.Fuente("ENCARGO",9,3)),new FuenteContacto(contacto,44));
        cabecera=new ReporteAvanceRepository.Cabecera(77,ref.referencia(),ref.version(),9,30,LocalDate.of(2026,11,1),LocalDate.of(2026,11,10),ahora,"Avance",json.objetoCanonico(fuentes),archivo,ahora);
        when(repo.version(1,ref.referencia(),0)).thenReturn(Optional.of(cabecera));when(repo.porId(1,77)).thenReturn(Optional.of(cabecera));
        var hechos=new Hechos(4,1,2,1,0,2,1,Map.of("Sin respuesta",1),"Actividad autorizada registrada; no todo el mercado.");
        var v=new VistaPrevia(9,"ENC-TEST","VENTA","APROBADO",3,cabecera.desde(),cabecera.hasta(),ahora,hechos,List.of());
        var f=new FichaReporte(ref,9,ReportePropietarioService.PLANTILLA,new PersonaDestinataria(44,"Gestora TEST","Recibe propuestas","Declaración de la gestora"),"Avance",v,List.of("Recomendación profesional"),null,ahora);
        when(repo.contenido(1,77)).thenReturn(Optional.of(json.objetoCanonico(f)));when(contactos.comprobar(contacto,44L,agente)).thenReturn(44L);
        when(contexto.ahora()).thenReturn(ahora);when(contexto.hoy()).thenReturn(LocalDate.of(2026,11,10));
    }
    @Test void consultaConservaHechosCongeladosSinRecalcularNiDeclararArchivo(){var f=s.consultar(ref,agente);assertEquals(2,f.avance().hechos().consultasComercialesRegistradas());assertEquals(4,f.avance().hechos().comunicacionesRegistradas());assertEquals(0L,f.reporte().version());assertNull(f.archivo());verifyNoInteractions(avance,pdf,comandos);}
    @Test void permisoDelEncargoSeCompruebaAntesDelCuerpo(){doThrow(new AccesoNoAutorizadoException()).when(acceso).exigirLectura(9,agente);assertThrows(AccesoNoAutorizadoException.class,()->s.consultar(ref,agente));verify(repo,never()).contenido(anyLong(),anyLong());}
    @Test void permisoDelContactoOriginalSeCompruebaAntesDelCuerpo(){doThrow(new NoEncontradoException("Contacto")).when(contactos).comprobar(contacto,44L,agente);assertThrows(NoEncontradoException.class,()->s.consultar(ref,agente));verify(repo,never()).contenido(anyLong(),anyLong());}
    @Test void revocarUnaInteraccionImpideRevelarElAgregado(){doThrow(new AccesoNoAutorizadoException()).when(interacciones).exigirVersiones(List.of(fuente),agente,FinalidadLectura.REPORTE_PROPIETARIO);assertThrows(AccesoNoAutorizadoException.class,()->s.consultar(ref,agente));verify(repo,never()).contenido(anyLong(),anyLong());}
    @Test void revocacionDuranteLaLecturaDescartaLaSalida(){doNothing().doThrow(new AccesoNoAutorizadoException()).when(acceso).exigirLectura(9,agente);assertThrows(AccesoNoAutorizadoException.class,()->s.consultar(ref,agente));}
    @Test void otraOrganizacionConsultaSuPropioAmbitoYNoHidrata(){var b=new Actor(2,13,130,"AGENTE");assertThrows(NoEncontradoException.class,()->s.consultar(ref,b));verify(repo).version(2,ref.referencia(),0);verify(repo,never()).contenido(anyLong(),anyLong());}
    @Test void administrarNoConcedeLecturaComercial(){doThrow(new AccesoNoAutorizadoException()).when(contexto).exigirOperador(admin);assertThrows(AccesoNoAutorizadoException.class,()->s.consultar(ref,admin));verify(repo,never()).version(anyLong(),any(),anyLong());}
    @Test void noSeAsumeVersionCeroSiFalta(){assertThrows(ReglaNegocioException.class,()->s.consultar(new Referencia(ref.referencia(),null),agente));verify(repo,never()).contenido(anyLong(),anyLong());}
    @Test void listadoAutorizaAntesDeRevelarCabeceras(){when(repo.listar(1,9,1,20)).thenReturn(List.of(cabecera));doThrow(new AccesoNoAutorizadoException()).when(contactos).comprobar(contacto,44L,agente);assertThrows(AccesoNoAutorizadoException.class,()->s.listar(9,1,20,agente));}
    @Test void antecedenteSinFuentesNoInventaUnaPoblacionVacia(){var h=new ReporteAvanceRepository.Cabecera(77,null,null,9,30,null,null,null,null,null,null,ahora);when(repo.porId(1,77)).thenReturn(Optional.of(h));assertThrows(AccesoNoAutorizadoException.class,()->s.consultarAntecedente(77,agente));verify(repo,never()).antecedente(anyLong(),anyLong());}
    @Test void antecedenteConocidoNoSeConvierteEnVersionNiComunicacion(){String fuentes=json.objeto(Map.of("versionEsquema",1,"idEncargo",9,"interacciones",List.of(fuente)));
        var h=new ReporteAvanceRepository.Cabecera(77,null,null,9,30,null,null,null,null,fuentes,null,ahora);when(repo.porId(1,77)).thenReturn(Optional.of(h));
        when(repo.antecedente(1,77)).thenReturn(Optional.of(new ReporteAvanceRepository.Antecedente(77,LocalDate.of(2026,10,1),null,null,1,2,"NOTA_PRIVADA","TEL_PRIVADO","E",ahora)));
        String resultado=json.objetoCanonico(s.consultarAntecedente(77,agente));assertFalse(resultado.contains("NOTA_PRIVADA"));assertFalse(resultado.contains("TEL_PRIVADO"));assertTrue(resultado.contains("contadorHistoricoInteracciones"));verifyNoInteractions(comandos,eventos,pdf);}
    @Test void noHayCampoParaContadoresInventadosNiEnvioEnElInput(){String valor="{\"versionEncargo\":3,\"consultasReportadas\":900,\"canalEnvio\":\"W\"}";assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,()->mapper.copy().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(valor,DatosReporte.class));}
    @Test void rechazaPaginacionNoAcotadaAntesDeConsultar(){assertThrows(ReglaNegocioException.class,()->s.listar(9,0,20,agente));assertThrows(ReglaNegocioException.class,()->s.comunicaciones(9,1,101,agente));verify(repo,never()).listar(anyLong(),anyLong(),anyInt(),anyInt());}
    @Test void previsualizacionUsaLaMismaComposicionCanonica(){var v=json.comoObjeto(repo.contenido(1,77).orElseThrow(),FichaReporte.class).avance();when(avance.preparar(9,cabecera.desde(),cabecera.hasta(),broker)).thenReturn(new AvanceDeReporte.Lectura(v,List.of(fuente),List.of()));assertEquals(v,s.preview(9,cabecera.desde(),cabecera.hasta(),broker));verifyNoInteractions(eventos,comandos,pdf);}
}
