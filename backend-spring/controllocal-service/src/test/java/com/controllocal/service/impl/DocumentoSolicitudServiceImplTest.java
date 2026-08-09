package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.TipoDocumentoRequerido;
import com.controllocal.persistence.repositorio.DocumentoSolicitudRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.TipoDocumentoRequeridoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.DocumentoSolicitudService.DatosDocumento;
import com.controllocal.service.DocumentoSolicitudService.FichaDocumento;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoSolicitud;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda los mensajes del cable de la parte documental de SolicitudesRest, la
 * maquina R -> {V, O} y la mejora MEJ-01 (cada revision deja fila en
 * historial_estado, cosa que la v1 no hacia).
 *
 * <p>La asimetria D-F4-5 quedo CERRADA (decision de equipo, 2026-07-29): en el
 * cable v1 revisar un documento suelto solo exigia el ROL —conformar en bloque
 * y evaluar si comprobaban el alcance—, asi que un broker podia tocar el
 * expediente de otro equipo. Ahora las tres operaciones comprueban igual, al
 * precio de que una peticion que la v1 respondia con 200 responde 403.
 */
class DocumentoSolicitudServiceImplTest {

    private static final long ORG = 1L;
    private static final long SOLICITUD = 5L;

    private final DocumentoSolicitudRepository documentos = mock(DocumentoSolicitudRepository.class);
    private final TipoDocumentoRequeridoRepository tipos = mock(TipoDocumentoRequeridoRepository.class);
    private final AccesoSolicitud acceso = mock(AccesoSolicitud.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    private final DocumentoSolicitudServiceImpl service = new DocumentoSolicitudServiceImpl(
            documentos, tipos, acceso, new Transiciones(historial), mock(AlertaService.class));

    /** vmora: organizacion 1, persona 3, rol operativo 30. */
    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");
    /** rsalas: broker supervisor. */
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    // ------------------------------------------------------------------
    // Alta: mensajes y orden del cable
    // ------------------------------------------------------------------

    @Test
    void elTipoDeDocumentoEsObligatorio() {
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(SOLICITUD, new DatosDocumento("  ", "dni.pdf", null, null), agente));
        assertEquals("El tipo de documento es obligatorio.", error.getMessage());
    }

    @Test
    void elNombreDeArchivoEsObligatorio() {
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(SOLICITUD, new DatosDocumento("I", null, null, null), agente));
        assertEquals("El nombre del archivo es obligatorio.", error.getMessage());
    }

    @Test
    void elNombreSeValidaAntesDeTraducirElTipo() {
        // Orden del cable: con el tipo invalido Y sin nombre, gana el nombre.
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(SOLICITUD, new DatosDocumento("Z", " ", null, null), agente));
        assertEquals("El nombre del archivo es obligatorio.", error.getMessage());
    }

    @Test
    void unTipoFueraDelCatalogoRespondeElMensajeV1() {
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(SOLICITUD, new DatosDocumento("Z", "dni.pdf", null, null), agente));
        assertEquals("Tipo de documento invalido: Z", error.getMessage());
        verifyNoInteractions(historial);
    }

    @Test
    void elDocumentoNaceRegistradoPendienteYConElTenantDeLaSolicitud() {
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud());
        prepararCatalogo("I", 1L, "Documento de identidad");
        when(documentos.save(any(DocumentoSolicitud.class))).thenAnswer(inv -> {
            DocumentoSolicitud guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", 77L);
            return guardado;
        });

        FichaDocumento ficha = service.registrar(SOLICITUD,
                new DatosDocumento("I", "dni.pdf", "SOL-260715103000/dni.pdf", null), agente);

        assertEquals(DocumentoSolicitud.REGISTRADO, ficha.estado());
        assertEquals(DocumentoSolicitud.REVISION_PENDIENTE, ficha.resultadoRevision());
        assertEquals("I", ficha.tipoDocumento());
        assertEquals("Documento de identidad", ficha.tipoNombre());
        assertEquals(SOLICITUD, ficha.idSolicitud());
        // El alta fija la fecha de entrega: si dependiera del DEFAULT de la BD
        // la respuesta del POST viajaria sin ella.
        assertNotNull(ficha.fechaEntrega());

        ArgumentCaptor<DocumentoSolicitud> guardado = ArgumentCaptor.forClass(DocumentoSolicitud.class);
        verify(documentos).save(guardado.capture());
        assertEquals(ORG, guardado.getValue().getOrganizacionId());
        // Nacer no es transicionar: la v1 tampoco lo registraba.
        verifyNoInteractions(historial);
    }

    // ------------------------------------------------------------------
    // Revision individual
    // ------------------------------------------------------------------

    @Test
    void elResultadoDeLaRevisionEsObligatorio() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.revisar(SOLICITUD, 77L, "  ", null, broker));
        assertEquals("El resultado de la revision es obligatorio.", error.getMessage());
    }

    @Test
    void unResultadoFueraDelVocabularioRespondeElMensajeDelEnum() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.revisar(SOLICITUD, 77L, "X", null, broker));
        assertEquals("Codigo invalido para ResultadoRevisionDocumento: X", error.getMessage());
    }

    @Test
    void observarExigeLaObservacion() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.revisar(SOLICITUD, 77L, "O", "   ", broker));
        assertEquals("La observacion del documento es obligatoria.", error.getMessage());
        verifyNoInteractions(documentos);
    }

    @Test
    void unDocumentoInexistenteRespondeElMensajeV1() {
        when(documentos.buscarFicha(ORG, 77L)).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.revisar(SOLICITUD, 77L, "C", null, broker));
        assertEquals("Documento no encontrado para revisar.", error.getMessage());
    }

    @Test
    void unDocumentoDeOtraSolicitudNoSeRevisa() {
        documentoRegistrado(77L, 1L, 999L);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.revisar(SOLICITUD, 77L, "C", null, broker));
        assertEquals("El documento no pertenece a la solicitud indicada.", error.getMessage());
    }

    @Test
    void conformarDejaElDocumentoValidadoYAudita() {
        documentoRegistrado(77L, 1L, SOLICITUD);

        FichaDocumento ficha = service.revisar(SOLICITUD, 77L, "C", null, broker);

        assertEquals(DocumentoSolicitud.VALIDADO, ficha.estado());
        assertEquals(DocumentoSolicitud.REVISION_CONFORME, ficha.resultadoRevision());

        HistorialEstado evento = eventoAuditado();
        assertEquals("DOCUMENTO_SOLICITUD", evento.getEntidadTipo());
        assertEquals("R", evento.getEstadoAnterior());
        assertEquals("V", evento.getEstadoNuevo());
        assertEquals(2L, evento.getIdActor());
        assertEquals("BROKER", evento.getTipoRolActor());
    }

    @Test
    void observarDejaElDocumentoObservadoYGuardaElPorque() {
        documentoRegistrado(77L, 1L, SOLICITUD);

        FichaDocumento ficha = service.revisar(SOLICITUD, 77L, "O", "El DNI esta vencido", broker);

        assertEquals(DocumentoSolicitud.OBSERVADO, ficha.estado());
        assertEquals(DocumentoSolicitud.REVISION_OBSERVADO, ficha.resultadoRevision());
        assertEquals("El DNI esta vencido", ficha.observaciones());
        assertEquals("Documento observado: El DNI esta vencido", eventoAuditado().getMotivo());
    }

    @Test
    void revisarSIComprueboElAlcanceSobreLaSolicitud() {
        // D-F4-5 CERRADA (decision de equipo, 2026-07-29). El cable v1 solo
        // exigia el ROL aqui, asi que un broker podia revisar documentos del
        // equipo de otro; conformar en bloque y evaluar si lo comprobaban. Se
        // cerro el hueco ANTES del corte: divergencia deliberada del contrato
        // congelado, acotada a esta operacion.
        documentoRegistrado(77L, 1L, SOLICITUD);

        service.revisar(SOLICITUD, 77L, "C", null, broker);

        verify(acceso).conAcceso(SOLICITUD, broker);
    }

    @Test
    void unBrokerDeOtroEquipoYaNoPuedeRevisarUnDocumentoAjeno() {
        // La otra cara de cerrar D-F4-5: donde la v1 respondia 200, ahora es un
        // 403. El alcance se comprueba ANTES de tocar el documento.
        doThrow(new AccesoNoAutorizadoException()).when(acceso).conAcceso(SOLICITUD, broker);

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.revisar(SOLICITUD, 77L, "C", null, broker));
        verifyNoInteractions(documentos);
    }

    // ------------------------------------------------------------------
    // Conformar en bloque
    // ------------------------------------------------------------------

    @Test
    void conformarEnBloqueSoloTocaLosPendientesYDevuelveElExpediente() {
        when(acceso.conAcceso(SOLICITUD, broker)).thenReturn(solicitud());
        DocumentoSolicitud pendiente = documento(80L, 5L, SOLICITUD,
                DocumentoSolicitud.REGISTRADO, DocumentoSolicitud.REVISION_PENDIENTE);
        DocumentoSolicitud observado = documento(81L, 6L, SOLICITUD,
                DocumentoSolicitud.OBSERVADO, DocumentoSolicitud.REVISION_OBSERVADO);
        when(documentos.pendientesDeRevision(ORG, SOLICITUD)).thenReturn(List.of(pendiente));
        when(documentos.porSolicitud(ORG, SOLICITUD)).thenReturn(List.of(pendiente, observado));
        when(documentos.save(any(DocumentoSolicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        List<FichaDocumento> expediente = service.conformarPendientes(SOLICITUD, broker);

        assertEquals(2, expediente.size());
        assertEquals(DocumentoSolicitud.VALIDADO, expediente.get(0).estado());
        // El observado es un hallazgo deliberado del broker: no se pisa.
        assertEquals(DocumentoSolicitud.OBSERVADO, expediente.get(1).estado());
        verify(historial).save(any(HistorialEstado.class));
    }

    @Test
    void conformarBorraLaObservacionPrevia() {
        when(acceso.conAcceso(SOLICITUD, broker)).thenReturn(solicitud());
        DocumentoSolicitud pendiente = documento(80L, 5L, SOLICITUD,
                DocumentoSolicitud.REGISTRADO, DocumentoSolicitud.REVISION_PENDIENTE);
        pendiente.setObservaciones("Comentario viejo del agente");
        when(documentos.pendientesDeRevision(ORG, SOLICITUD)).thenReturn(List.of(pendiente));
        when(documentos.porSolicitud(ORG, SOLICITUD)).thenReturn(List.of(pendiente));
        when(documentos.save(any(DocumentoSolicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNull(service.conformarPendientes(SOLICITUD, broker).get(0).observaciones());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void prepararCatalogo(String codigo, long idCatalogo, String nombre) {
        TipoDocumentoRequerido tipo = new TipoDocumentoRequerido();
        ReflectionTestUtils.setField(tipo, "id", idCatalogo);
        ReflectionTestUtils.setField(tipo, "tipoDocumento", nombre);
        when(tipos.findById(TipoDocumentoRequerido.idDe(codigo))).thenReturn(Optional.of(tipo));
    }

    private void documentoRegistrado(long idDocumento, long idCatalogo, long idSolicitud) {
        DocumentoSolicitud documento = documento(idDocumento, idCatalogo, idSolicitud,
                DocumentoSolicitud.REGISTRADO, DocumentoSolicitud.REVISION_PENDIENTE);
        when(documentos.buscarFicha(ORG, idDocumento)).thenReturn(Optional.of(documento));
        when(documentos.save(any(DocumentoSolicitud.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DocumentoSolicitud documento(long id, long idCatalogo, long idSolicitud,
                                                String estado, String revision) {
        TipoDocumentoRequerido tipo = new TipoDocumentoRequerido();
        ReflectionTestUtils.setField(tipo, "id", idCatalogo);
        ReflectionTestUtils.setField(tipo, "tipoDocumento", "Documento de identidad");

        DocumentoSolicitud documento = new DocumentoSolicitud();
        documento.setOrganizacionId(ORG);
        documento.setSolicitud(solicitud(idSolicitud));
        documento.setTipoDocumento(tipo);
        documento.setNombreArchivo("dni.pdf");
        documento.registrarEntrega();
        documento.setResultadoRevision(revision);
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(documento, estado);
        ReflectionTestUtils.setField(documento, "id", id);
        return documento;
    }

    private static SolicitudAlquiler solicitud() {
        return solicitud(SOLICITUD);
    }

    private static SolicitudAlquiler solicitud(long id) {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setOrganizacionId(ORG);
        solicitud.setCodigoSolicitud("SOL-260715103000");
        ReflectionTestUtils.setField(solicitud, "id", id);
        return solicitud;
    }

    private HistorialEstado eventoAuditado() {
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        return evento.getValue();
    }
}
