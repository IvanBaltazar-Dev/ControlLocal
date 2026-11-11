package com.controllocal.service.network;

import com.controllocal.domain.colaboracion.LadoColaboracion;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.captura.SuficienciaRegistrada;
import com.controllocal.service.colaboracion.*;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.encargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.MotorMatchingDeterminista;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La concesión comercial no simula el tratamiento específico todavía no validado. */
class FuenteNetworkActualTest {
    private final AccesoEncargo acceso=mock(AccesoEncargo.class);
    private final EncargoService encargos=mock(EncargoService.class);
    private final Alcances alcances=mock(Alcances.class);
    private final OrganizacionRepository organizaciones=mock(OrganizacionRepository.class);
    private final SupervisionAgenteRepository supervisiones=mock(SupervisionAgenteRepository.class);
    private final AutoridadDePropiedad autoridadFisica=mock(AutoridadDePropiedad.class);
    private final LectorPorAutoridad lector=mock(LectorPorAutoridad.class);
    private final SuficienciaRegistrada suficiencia=mock(SuficienciaRegistrada.class);
    private final DisponibilidadContextualService disponibilidad=mock(DisponibilidadContextualService.class);
    private final ContextoNetworkRepository contextos=mock(ContextoNetworkRepository.class);
    private final AtributosGobernados definiciones=mock(AtributosGobernados.class);
    private final MotorMatchingDeterminista motor=mock(MotorMatchingDeterminista.class);
    private final GobiernoCatalogoCapturaService catalogos=mock(GobiernoCatalogoCapturaService.class);
    private final Actor receptor=new Actor(1,10,100,Actor.AGENTE),gobierno=new Actor(1,10,101,Actor.TENANT_ADMIN);
    private FuenteNetworkActual fuente;
    @BeforeEach void preparar() {
        fuente=new FuenteNetworkActual(acceso,encargos,alcances,organizaciones,supervisiones,autoridadFisica,lector,
                suficiencia,disponibilidad,contextos,new Documentos(new ObjectMapper().findAndRegisterModules()),definiciones,motor,catalogos,mock(CaptacionRepository.class),mock(ConfirmacionDisponibilidadContextualRepository.class));
    }
    @Test void gobiernoNoConsultaUnaOfertaConcedidaNiSuMetadata() {
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.verificarCamposColaboracion(proof(LadoColaboracion.ENCARGO),gobierno));
        verifyNoInteractions(contextos);sinAtributos();
    }
    @Test void ausenciaDeProofNoEsUnaConcesionGeneral() {
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.verificarCamposColaboracion(null,receptor));
        verifyNoInteractions(contextos);sinAtributos();
    }
    @Test void proofDeDemandaNoAutorizaElPuertoDeOferta() {
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.verificarCamposColaboracion(proof(LadoColaboracion.DEMANDA),receptor));
        verifyNoInteractions(contextos);sinAtributos();
    }
    @Test void metadataDeOtraOrganizacionNoSustituyeLaDelProof() {
        var metadata=contexto(3,9,200L);
        when(contextos.deReferenciasAprobadas(Set.of(9L))).thenReturn(List.of(metadata));
        assertThrows(NoEncontradoException.class,()->fuente.verificarCamposColaboracion(proof(LadoColaboracion.ENCARGO),receptor));
        sinAtributos();
    }
    @Test void metadataDeOtroRecursoTampocoEsLaOfertaConcedida() {
        var metadata=contexto(2,8,200L);
        when(contextos.deReferenciasAprobadas(Set.of(9L))).thenReturn(List.of(metadata));
        assertThrows(NoEncontradoException.class,()->fuente.verificarCamposColaboracion(proof(LadoColaboracion.ENCARGO),receptor));
        sinAtributos();
    }
    @Test void perdidaDelResponsableNoProduceUnaHuellaNiUnResultadoVacio() {
        var metadata=contexto(2,9,null);
        when(contextos.deReferenciasAprobadas(Set.of(9L))).thenReturn(List.of(metadata));
        assertThrows(ConflictoException.class,()->fuente.verificarCamposColaboracion(proof(LadoColaboracion.ENCARGO),receptor));
        sinAtributos();
    }
    @Test void proofComercialValidoSeDetieneAntesDeAtributosMientrasFalteTratamiento() {
        var metadata=contexto(2,9,200L);
        when(contextos.deReferenciasAprobadas(Set.of(9L))).thenReturn(List.of(metadata));
        var v=fuente.verificarCamposColaboracion(proof(LadoColaboracion.ENCARGO),receptor);
        assertEquals(2,v.organizacion());assertEquals(9,v.idEncargo());assertEquals(200,v.responsableActual());assertEquals(4,v.versionActual());
        assertFalse(v.elegible());assertNull(v.huellaSubset());assertEquals(Set.of(FuenteNetworkActual.TRATAMIENTO_PENDIENTE),v.causas());
        sinAtributos();
    }
    @Test void falloTecnicoDelLectorMinimoNoSeTransformaEnFaltaDeDatosODeTratamiento() {
        when(contextos.deReferenciasAprobadas(any())).thenThrow(new IllegalStateException("Fallo técnico TEST"));
        assertThrows(IllegalStateException.class,()->fuente.verificarCamposColaboracion(proof(LadoColaboracion.ENCARGO),receptor));
        sinAtributos();
    }
    @Test void revalidacionL2NoRecuperaValoresParaCalcularUnaHuellaOculta() {
        var metadata=contexto(2,9,200L);
        when(contextos.deReferenciasAprobadas(Set.of(9L))).thenReturn(List.of(metadata));
        var v=fuente.verificarActuales(List.of(referencia()),receptor).get(9L);
        assertNotNull(v);assertFalse(v.elegible());assertNull(v.huellaRelevante());assertNull(v.disponibilidad());
        assertTrue(v.causas().contains(FuenteNetworkActual.TRATAMIENTO_PENDIENTE));sinAtributos();
    }
    @Test void actorSinAlcancePropioNoLeeFichaNiDatosFisicos() {
        var c=mock(CaptacionRepository.ContextoActualEncargo.class);when(c.getResponsable()).thenReturn(200L);
        when(acceso.contexto(9,receptor)).thenReturn(c);when(alcances.alcanza(receptor,200L)).thenReturn(false);
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.consultarPropia(9,Set.of("ENCARGO:operacion"),FuenteDeNetwork.Accion.CONSULTAR,receptor));
        verifyNoInteractions(organizaciones,encargos,supervisiones,contextos);sinAtributos();
    }
    @Test void unCampoPrivadoNoSeAdmitePorEstarEnUnEncargoPropio() {
        assertThrows(ReglaNegocioException.class,()->fuente.consultarPropia(9,Set.of("PROPIEDAD:direccion"),FuenteDeNetwork.Accion.CONSULTAR,receptor));
        verifyNoInteractions(acceso,encargos,contextos);sinAtributos();
    }
    @Test void unidadDelMismoPrecioUsaElEavYaLeidoYCambiaLaHuellaSinAmpliarV1() {
        var actual=mock(CaptacionRepository.ContextoActualEncargo.class);when(actual.getResponsable()).thenReturn(100L);
        when(acceso.contexto(9,receptor)).thenReturn(actual);when(alcances.alcanza(receptor,100L)).thenReturn(true);
        var org=mock(com.controllocal.domain.organizacion.Organizacion.class);
        when(org.getModalidad()).thenReturn("INDIVIDUAL");when(org.estaActiva()).thenReturn(true);
        when(organizaciones.findById(1L)).thenReturn(Optional.of(org));
        var ficha=mock(EncargoService.Ficha.class);var condiciones=mock(EncargoService.Condiciones.class);var vigencia=mock(EncargoService.Vigencia.class);
        when(encargos.consultar(9,receptor)).thenReturn(ficha);when(ficha.idPropiedad()).thenReturn(5L);
        when(ficha.operacion()).thenReturn("ALQUILER");when(ficha.version()).thenReturn(3L);when(ficha.composicionActual()).thenReturn(42L);
        when(ficha.condiciones()).thenReturn(condiciones);when(ficha.vigencia()).thenReturn(vigencia);when(vigencia.habilitado()).thenReturn(true);
        when(condiciones.precioPedido()).thenReturn(new java.math.BigDecimal("10"));when(condiciones.monedaPrecio()).thenReturn("PEN");
        when(condiciones.periodoPrecio()).thenReturn("MENSUAL");
        var propiedad=mock(com.controllocal.domain.inmueble.Propiedad.class);when(propiedad.getVersionFisica()).thenReturn(7L);
        when(autoridadFisica.buscarParaLectura(receptor,5L)).thenReturn(Optional.of(propiedad));
        var confirmacion=new com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad(Instant.now().minusSeconds(60),false,"HECHO_TEST");
        when(disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.ENCARGO,9,receptor)).thenReturn(confirmacion);
        when(motor.disponibilidad(any(),any())).thenAnswer(i->new MotorMatchingDeterminista().disponibilidad(i.getArgument(0),i.getArgument(1)));
        when(lector.deEncargo(9)).thenReturn(ValoresGobernados.constructor().con("modalidad_precio",ValorLogico.deTexto("POR_M2_AL_MES")).construir());
        var campos=Set.of("ENCARGO:precio_pedido");
        var unitaria=fuente.consultarPropia(9,campos,FuenteDeNetwork.Accion.CONSULTAR,receptor);
        @SuppressWarnings("unchecked") var precio=(Map<String,Object>)unitaria.datosPermitidos().get("ENCARGO:precio_pedido");
        assertEquals("m2",precio.get("unidad"));assertEquals(new java.math.BigDecimal("10"),precio.get("valor"));
        assertEquals("PEN",precio.get("moneda"));assertEquals("MENSUAL",precio.get("periodo"));assertEquals(campos,unitaria.datosPermitidos().keySet());
        assertFalse(unitaria.elegible());assertTrue(unitaria.causas().contains(FuenteNetworkActual.TRATAMIENTO_PENDIENTE),"No se habilita producción por corregir unidad.");
        when(lector.deEncargo(9)).thenReturn(ValoresGobernados.constructor().con("modalidad_precio",ValorLogico.deTexto("MENSUAL_TOTAL")).construir());
        var objeto=fuente.consultarPropia(9,campos,FuenteDeNetwork.Accion.CONSULTAR,receptor);
        assertNotEquals(unitaria.huellaRelevante(),objeto.huellaRelevante(),"El cambio de metadata debe invalidar la aprobación exacta anterior.");
        @SuppressWarnings("unchecked") var precioObjeto=(Map<String,Object>)objeto.datosPermitidos().get("ENCARGO:precio_pedido");
        assertFalse(precioObjeto.containsKey("unidad"));assertEquals(precio.get("valor"),precioObjeto.get("valor"));
        verify(lector,times(2)).deEncargo(9);verifyNoMoreInteractions(lector);verifyNoInteractions(suficiencia,definiciones,catalogos);
    }
    private void sinAtributos(){verifyNoInteractions(autoridadFisica,lector,suficiencia,disponibilidad,definiciones,motor,catalogos);}
    private ContextoNetworkRepository.ContextoActual contexto(long org,long id,Long responsable) {
        var c=mock(ContextoNetworkRepository.ContextoActual.class);when(c.getOrganizacion()).thenReturn(org);when(c.getEncargo()).thenReturn(id);
        when(c.getVersion()).thenReturn(4L);when(c.getResponsable()).thenReturn(responsable);when(c.getBroker()).thenReturn(201L);
        when(c.getModalidad()).thenReturn("EMPRESARIAL");when(c.getEstadoOrganizacion()).thenReturn("A");return c;
    }
    private FuenteDeNetwork.ReferenciaAutorizada referencia(){return new FuenteDeNetwork.ReferenciaAutorizada(2,9,"oferta-test","version-test",1,200,201L,PoliticaProyeccionNetworkV1.VERSION,PoliticaProyeccionNetworkV1.FINALIDAD,Set.of("ENCARGO:operacion"),"a".repeat(64),"b".repeat(64));}
    private ReferenciaParteConcedida proof(LadoColaboracion lado){return ReferenciasConcedidasDePrueba.de("concesion-test",0,"solicitud-test",0,1,lado,2,9,200,3,Set.of("ENCARGO:operacion"),Set.of(),PoliticaProyeccionNetworkV1.FINALIDAD,Instant.parse("2026-10-01T00:00:00Z"),Instant.parse("2100-01-01T00:00:00Z"),"a".repeat(64));}
}
