package com.controllocal.service.documentos;

import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.FotoPropiedadService.FotoDePropiedad;
import com.controllocal.service.documentos.SeleccionFotosContextualesService.Contexto;
import com.controllocal.service.documentos.SeleccionFotosContextualesService.Finalidad;
import com.controllocal.service.documentos.SeleccionFotosContextualesService.FotoVersionada;
import com.controllocal.service.documentos.SeleccionFotosContextualesService.Solicitud;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** RF-052/AC-121: selección de referencias; el orden y original de la galería se conservan. */
class SeleccionFotosContextualesServiceTest {
    private static final Actor AGENTE = new Actor(7, 11, 13, Actor.AGENTE);
    private static final Actor BROKER = new Actor(7, 17, 19, Actor.BROKER);
    private static final long PROPIEDAD = 23;
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID AJENA = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private final FotoPropiedadService fotos = mock(FotoPropiedadService.class);
    private final SeleccionFotosContextualesService servicio = new SeleccionFotosContextualesService(fotos);

    @Test void elPredicadoInternoConservaLosRolesDeLecturaYNoCargaLaGaleria(){
        for(Actor actor:List.of(AGENTE,BROKER)){
            when(fotos.originalPermitidoParaLectura(PROPIEDAD,A,actor)).thenReturn(true);
            assertTrue(servicio.originalPermitidoParaLectura(PROPIEDAD,A,actor));
            verify(fotos).originalPermitidoParaLectura(PROPIEDAD,A,actor);
        }
        verify(fotos,never()).listar(anyLong(),any());
    }

    @Test void elGobiernoAdministrativoNoConcedeLecturaDeOriginales(){
        var admin=new Actor(7,37,41,Actor.TENANT_ADMIN);
        assertFalse(servicio.originalPermitidoParaLectura(PROPIEDAD,A,admin));verifyNoInteractions(fotos);
    }

    @Test void observacionPresentaReferenciasOriginalesYHuellaEstableSinClavesDelProveedor() {
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(galeria());
        var observacion = servicio.observar(PROPIEDAD, AGENTE);
        var repetida = servicio.observar(PROPIEDAD, AGENTE);

        assertEquals(new Contexto("PROPIEDAD", PROPIEDAD), observacion.contexto());
        assertEquals(List.of(new FotoVersionada(A, "foto-31.jpg", 1),
                new FotoVersionada(B, "foto-32.jpg", 2), new FotoVersionada(C, "foto-33.jpg", 3)),
                observacion.fotos());
        assertEquals(observacion, repetida);
        assertTrue(observacion.versionObservada().matches("[0-9a-f]{64}"));
        assertThrows(UnsupportedOperationException.class, () -> observacion.fotos().clear());
        verify(fotos, times(2)).listar(PROPIEDAD, AGENTE);
        verifyNoMoreInteractions(fotos);
    }

    @ParameterizedTest @EnumSource(Finalidad.class)
    void seleccionaSubconjuntoEnElOrdenPedidoSinReordenarNiSustituirOriginal(Finalidad finalidad) {
        List<FotoDePropiedad> originales = galeria();
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(originales);
        var observacion = servicio.observar(PROPIEDAD, AGENTE);
        var pedido = new ArrayList<>(List.of(C, A));
        var seleccion = servicio.seleccionar(PROPIEDAD,
                new Solicitud(pedido, finalidad, observacion.versionObservada()), AGENTE);
        pedido.clear();

        assertEquals(List.of(C, A), seleccion.fotosVersionadas());
        assertEquals(finalidad, seleccion.finalidad());
        assertEquals(observacion.contexto(), seleccion.contexto());
        assertEquals(observacion.versionObservada(), seleccion.versionObservada());
        assertEquals(galeria(), originales);
        assertThrows(UnsupportedOperationException.class, () -> seleccion.fotosVersionadas().clear());
        verify(fotos, times(2)).listar(PROPIEDAD, AGENTE);
        verifyNoMoreInteractions(fotos);
    }

    @Test void repetirLaSeleccionDevuelveElMismoValorSinCrearEfectos() {
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(galeria());
        var observacion = servicio.observar(PROPIEDAD, AGENTE);
        var pedido = new Solicitud(List.of(B, A), Finalidad.REVISION_PRIVADA, observacion.versionObservada());
        var primera = servicio.seleccionar(PROPIEDAD, pedido, AGENTE);
        assertEquals(primera, servicio.seleccionar(PROPIEDAD, pedido, AGENTE));
        verify(fotos, times(3)).listar(PROPIEDAD, AGENTE);
        verifyNoMoreInteractions(fotos);
    }

    @Test void seleccionVaciaEsUnValorValidoYNoInventaUnMinimoDeFotos() {
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(List.of());
        var observacion = servicio.observar(PROPIEDAD, AGENTE);
        assertTrue(observacion.fotos().isEmpty());
        assertTrue(servicio.seleccionar(PROPIEDAD, new Solicitud(List.of(), Finalidad.REVISION_PRIVADA,
                observacion.versionObservada()), AGENTE).fotosVersionadas().isEmpty());
    }

    @Test void rechazaRepetidosSinConvertirLaSeleccionEnUnaReordenacionCompleta() {
        assertThrows(ReglaNegocioException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(A, A), Finalidad.REVISION_PRIVADA, "observacion"), AGENTE));
        verifyNoInteractions(fotos);
    }

    @Test void unaReferenciaAjenaSeRechazaSinConsultarOtraPropiedadNiOtroTenant() {
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(galeria());
        var observacion = servicio.observar(PROPIEDAD, AGENTE);
        assertThrows(ReglaNegocioException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(AJENA), Finalidad.REVISION_PRIVADA, observacion.versionObservada()), AGENTE));
        verify(fotos, times(2)).listar(PROPIEDAD, AGENTE);
        verifyNoMoreInteractions(fotos);
    }

    @ParameterizedTest @ValueSource(strings = {"CONJUNTO", "ORDEN", "ORIGINAL"})
    void versionObservadaNoCubreCambiosPosterioresDeGaleria(String cambio) {
        List<FotoDePropiedad> despues = switch (cambio) {
            case "CONJUNTO" -> List.of(foto(31, A, 1), foto(33, C, 2));
            case "ORDEN" -> List.of(foto(32, B, 1), foto(31, A, 2), foto(33, C, 3));
            default -> List.of(foto(31, AJENA, 1), foto(32, B, 2), foto(33, C, 3));
        };
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(galeria(), despues);
        var observada = servicio.observar(PROPIEDAD, AGENTE);
        assertThrows(ConflictoException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(A), Finalidad.REVISION_PRIVADA, observada.versionObservada()), AGENTE));
        verify(fotos, times(2)).listar(PROPIEDAD, AGENTE);
        verifyNoMoreInteractions(fotos);
    }

    @Test void huellaVinculaOrganizacionYPropiedadInclusoEnUnaGaleriaVacia() {
        Actor otroTenant = new Actor(71, 11, 13, Actor.AGENTE);
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(List.of());
        when(fotos.listar(PROPIEDAD + 1, AGENTE)).thenReturn(List.of());
        when(fotos.listar(PROPIEDAD, otroTenant)).thenReturn(List.of());
        var propia = servicio.observar(PROPIEDAD, AGENTE);
        var otraPropiedad = servicio.observar(PROPIEDAD + 1, AGENTE);
        var otraOrganizacion = servicio.observar(PROPIEDAD, otroTenant);
        assertNotEquals(propia.versionObservada(), otraPropiedad.versionObservada());
        assertNotEquals(propia.versionObservada(), otraOrganizacion.versionObservada());
        assertThrows(ConflictoException.class, () -> servicio.seleccionar(PROPIEDAD + 1,
                new Solicitud(List.of(), Finalidad.REVISION_PRIVADA, propia.versionObservada()), AGENTE));
        assertThrows(ConflictoException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(), Finalidad.REVISION_PRIVADA, propia.versionObservada()), otroTenant));
    }

    @Test void elProveedorNoCambiaLaVersionDelOriginalObservado() {
        var originales = galeria();
        var servidasPorOtro = originales.stream().map(foto -> foto.servidaPor("S3")).toList();
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(originales, servidasPorOtro);
        var observada = servicio.observar(PROPIEDAD, AGENTE);
        assertEquals(observada.versionObservada(), servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(C), Finalidad.REVISION_PRIVADA, observada.versionObservada()), AGENTE)
                .versionObservada());
    }

    @Test void brokerLeeLaObservacionPermitidaPeroNoComponeUnaSeleccionPorSupervisar() {
        when(fotos.listar(PROPIEDAD, BROKER)).thenReturn(galeria());
        var observada = servicio.observar(PROPIEDAD, BROKER);
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(A), Finalidad.REVISION_PRIVADA, observada.versionObservada()), BROKER));
        verify(fotos).listar(PROPIEDAD, BROKER);
        verifyNoMoreInteractions(fotos);
    }

    @ParameterizedTest @ValueSource(strings = {Actor.TENANT_ADMIN, "BROX_PLATFORM_QA", "IDENTIDAD"})
    void gobiernoYFuncionesSinOperacionNoPreleenLaGaleria(String rol) {
        Actor actor = new Actor(7, 17, 19, rol);
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.observar(PROPIEDAD, actor));
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(A), Finalidad.REVISION_PRIVADA, "observacion"), actor));
        verifyNoInteractions(fotos);
    }

    @Test void revocacionPosteriorAObservarImpideReutilizarLaHuellaComoPermiso() {
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(galeria()).thenThrow(new NoEncontradoException("Propiedad"));
        var observada = servicio.observar(PROPIEDAD, AGENTE);
        assertThrows(NoEncontradoException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(A), Finalidad.REVISION_PRIVADA, observada.versionObservada()), AGENTE));
        verify(fotos, times(2)).listar(PROPIEDAD, AGENTE);
        verifyNoMoreInteractions(fotos);
    }

    @Test void galeriaAjenaNoSeConvierteEnObservacionOSeleccionVacia() {
        when(fotos.listar(PROPIEDAD, AGENTE)).thenThrow(new NoEncontradoException("Propiedad"));
        assertThrows(NoEncontradoException.class, () -> servicio.observar(PROPIEDAD, AGENTE));
        assertThrows(NoEncontradoException.class, () -> servicio.seleccionar(PROPIEDAD,
                new Solicitud(List.of(), Finalidad.REVISION_PRIVADA, "observacion"), AGENTE));
    }

    @Test void fallaLaLecturaCanonicaSinSustituirUnFalloTecnicoPorUnaGaleriaVacia() {
        var fallo = new IllegalStateException("No fue posible leer la galería");
        when(fotos.listar(PROPIEDAD, AGENTE)).thenThrow(fallo);
        assertSame(fallo, assertThrows(IllegalStateException.class, () -> servicio.observar(PROPIEDAD, AGENTE)));
    }

    @Test void solicitudIncompletaNoProduceUnResultadoValidoNiLeeLaGaleria() {
        List<Solicitud> invalidas = Arrays.asList(null,
                new Solicitud(null, Finalidad.REVISION_PRIVADA, "observacion"),
                new Solicitud(List.of(A), null, "observacion"),
                new Solicitud(List.of(A), Finalidad.REVISION_PRIVADA, null),
                new Solicitud(List.of(A), Finalidad.REVISION_PRIVADA, " "),
                new Solicitud(Arrays.asList(A, null), Finalidad.REVISION_PRIVADA, "observacion"));
        for (Solicitud datos : invalidas)
            assertThrows(ReglaNegocioException.class, () -> servicio.seleccionar(PROPIEDAD, datos, AGENTE));
        verifyNoInteractions(fotos);
    }

    @Test void origenSinReferenciaPrivadaValidaEsUnFalloDeIntegridad() {
        when(fotos.listar(PROPIEDAD, AGENTE)).thenReturn(List.of(
                new FotoDePropiedad(31L, "7/propiedades/23/original.jpg", "original.jpg", 1)));
        assertThrows(IllegalStateException.class, () -> servicio.observar(PROPIEDAD, AGENTE));
    }

    private static List<FotoDePropiedad> galeria() {
        return List.of(foto(31, A, 1), foto(32, B, 2), foto(33, C, 3));
    }

    private static FotoDePropiedad foto(long id, UUID referencia, int orden) {
        return new FotoDePropiedad(id, referencia.toString(), "foto-" + id + ".jpg", orden);
    }
}
