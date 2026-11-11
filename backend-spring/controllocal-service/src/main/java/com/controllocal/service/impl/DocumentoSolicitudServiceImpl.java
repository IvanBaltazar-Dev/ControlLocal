package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.TipoDocumentoRequerido;
import com.controllocal.persistence.repositorio.DocumentoSolicitudRepository;
import com.controllocal.persistence.repositorio.TipoDocumentoRequeridoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.DocumentoSolicitudService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoSolicitud;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Transiciones;
import com.controllocal.service.soporte.Vocabulario;
import com.controllocal.service.soporte.FuentesAsuntos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas y mensajes calcados de la parte documental de {@code SolicitudesRest}
 * + {@code DocumentoSolicitudBusinessLogicImpl} (contrato congelado F4 §3).
 *
 * <p>Mejora gratis del stack nuevo: la revision del broker pasa por
 * {@link Transiciones}, asi que R-&gt;V y R-&gt;O quedan en
 * {@code historial_estado} con actor y motivo. La v1 movia ese estado a mano
 * (MEJ-01).
 *
 * <p><b>Ojo con una asimetria del cable, replicada tal cual</b>: revisar un
 * documento suelto NO comprueba el alcance del broker sobre la solicitud (la
 * v1 solo exige el ROL), mientras que conformar en bloque SI lo comprueba.
 * Aqui se replica —el tenant siempre acota— y queda anotado como decision
 * D-F4-5 para el equipo.
 */
@Service
public class DocumentoSolicitudServiceImpl implements DocumentoSolicitudService {

    private final DocumentoSolicitudRepository documentos;
    private final TipoDocumentoRequeridoRepository tipos;
    private final AccesoSolicitud acceso;
    private final Transiciones transiciones;

    private final AlertaService alertas;
    private final FuentesAsuntos fuentesAsuntos;
    private final EntityManager em;

    public DocumentoSolicitudServiceImpl(DocumentoSolicitudRepository documentos,
                                         TipoDocumentoRequeridoRepository tipos,
                                         AccesoSolicitud acceso, Transiciones transiciones,
                                         AlertaService alertas,FuentesAsuntos fuentesAsuntos,EntityManager em) {
        this.documentos = documentos;
        this.tipos = tipos;
        this.acceso = acceso;
        this.transiciones = transiciones;
        this.alertas = alertas;
        this.fuentesAsuntos=fuentesAsuntos;this.em=em;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaDocumento> listarPorSolicitud(long idSolicitud, Actor actor) {
        acceso.conAcceso(idSolicitud, actor);
        return documentos.porSolicitud(actor.idOrganizacion(), idSolicitud).stream()
                .map(DocumentoSolicitudServiceImpl::ficha)
                .toList();
    }

    @Override
    @Transactional
    public String prepararCarga(long idSolicitud,String tipoDocumento,Actor actor) {
        acceso.exigirConsultaActual(idSolicitud,actor);
        var solicitud=acceso.conAcceso(idSolicitud,actor);
        var tipo=tipoDeCodigo(tipoDocumento);
        return com.controllocal.service.soporte.Idempotencia.huellaExacta("DOCUMENTO-SOLICITUD\u0000"+actor.idOrganizacion()+"\u0000"+idSolicitud+"\u0000"+solicitud.getAgente().getId()+"\u0000"+solicitud.estadoActual()+"\u0000"+tipo.getId());
    }

    @Override
    @Transactional
    public FichaDocumento registrar(long idSolicitud, DatosDocumento datos, Actor actor) {
        // La carga binaria puede haber esperado a S3; no usar como autoridad una entidad de OEIV anterior.
        acceso.exigirConsultaActual(idSolicitud, actor);
        SolicitudAlquiler solicitud = acceso.conAcceso(idSolicitud, actor);
        // Orden calcado del cable: primero el tipo, despues el nombre y solo
        // entonces se traduce el codigo a id de catalogo.
        if (datos == null || enBlanco(datos.tipoDocumento())) {
            throw new ReglaNegocioException("El tipo de documento es obligatorio.");
        }
        if (enBlanco(datos.nombreArchivo())) {
            throw new ReglaNegocioException("El nombre del archivo es obligatorio.");
        }
        TipoDocumentoRequerido tipo = tipoDeCodigo(datos.tipoDocumento());

        DocumentoSolicitud documento = new DocumentoSolicitud();
        // El tenant sale de la SOLICITUD, no del actor: el documento pertenece
        // al expediente, y la solicitud ya viene acotada a la organizacion.
        documento.setOrganizacionId(solicitud.getOrganizacionId());
        documento.setSolicitud(solicitud);
        documento.setTipoDocumento(tipo);
        documento.setNombreArchivo(datos.nombreArchivo());
        documento.setRutaArchivo(datos.rutaArchivo());
        documento.setObservaciones(datos.observaciones());
        documento.registrarEntrega();
        transiciones.iniciar(documento, DocumentoSolicitud.REGISTRADO);
        documento=documentos.saveAndFlush(documento);
        // Aviso al broker (§4 F6, punto 6), y SOLO si la solicitud esta en
        // revision: el mensaje no es "subio un documento", es "el expediente
        // cambio mientras lo evaluabas".
        if (SolicitudAlquiler.EN_REVISION.equals(solicitud.estadoActual())) {
            String nombre = enBlanco(datos.nombreArchivo()) ? "un documento" : datos.nombreArchivo();
            alertas.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_DOCUMENTO, Alerta.MEDIA,
                    "SOLICITUD_ALQUILER", idSolicitud,
                    solicitud.getAgente() != null ? solicitud.getAgente().getId() : null,
                    "El agente actualizo \"" + nombre + "\" en la solicitud "
                            + solicitud.getCodigoSolicitud() + " mientras esta en evaluacion.",
                    com.controllocal.service.soporte.FuentesAsuntos.Origen.de("ENTREGA_DOCUMENTO",documento.getId())), actor);
        }
        return ficha(documento);
    }

    @Override
    @Transactional
    public FichaDocumento revisar(long idSolicitud, long idDocumento, String resultado,
                                  String observaciones, Actor actor) {
        if (enBlanco(resultado)) {
            throw new ReglaNegocioException("El resultado de la revision es obligatorio.");
        }
        // Comparacion exacta salvo espacios, igual que el resto del stack: el
        // cable llega a este campo por CodigoEnum.fromCodigo, sin normalizar caja.
        String codigo = Vocabulario.exigir(resultado, DocumentoSolicitud.RESULTADOS_REVISION,
                "ResultadoRevisionDocumento");
        if (DocumentoSolicitud.REVISION_OBSERVADO.equals(codigo) && enBlanco(observaciones)) {
            throw new ReglaNegocioException("La observacion del documento es obligatoria.");
        }
        // D-F4-5 CERRADA (decision de equipo, 2026-07-29): el cable v1 solo
        // exige el ROL aqui, asi que un broker podia revisar documentos del
        // equipo de otro. Es el UNICO de los tres hermanos sin comprobacion
        // —conformar en bloque y evaluar si la hacen—, lo que delata un olvido
        // de la v1 y no una regla. Se cierra ANTES del corte: la peticion de un
        // broker ajeno pasa de 200 a 403 (404 si la solicitud no es del tenant),
        // divergencia deliberada y acotada del contrato congelado. El Blazor no
        // la alcanza por navegacion —sus listados ya vienen filtrados—, hace
        // falta escribir los dos ids a mano.
        acceso.conAcceso(idSolicitud, actor);
        DocumentoSolicitud documento = documentos.buscarFicha(actor.idOrganizacion(), idDocumento)
                .orElseThrow(() -> new ReglaNegocioException("Documento no encontrado para revisar."));
        if (documento.getSolicitud() == null
                || !Long.valueOf(idSolicitud).equals(documento.getSolicitud().getId())) {
            throw new ReglaNegocioException("El documento no pertenece a la solicitud indicada.");
        }
        return ficha(aplicarRevision(documento, codigo, observaciones, actor));
    }

    @Override
    @Transactional
    public List<FichaDocumento> conformarPendientes(long idSolicitud, Actor actor) {
        acceso.conAcceso(idSolicitud, actor);
        for (DocumentoSolicitud pendiente
                : documentos.pendientesDeRevision(actor.idOrganizacion(), idSolicitud).stream()
                    .sorted(java.util.Comparator.comparing(DocumentoSolicitud::getId)).toList()) {
            // Conformar BORRA la observacion previa (asi lo hace la v1); los
            // observados no entran en la consulta, que es justo lo que los
            // protege: son un hallazgo deliberado del broker.
            aplicarRevision(pendiente, DocumentoSolicitud.REVISION_CONFORME, null, actor);
        }
        // La v1 devuelve el expediente COMPLETO, no solo lo que toco.
        List<FichaDocumento> expediente = new ArrayList<>();
        for (DocumentoSolicitud documento : documentos.porSolicitud(actor.idOrganizacion(), idSolicitud)) {
            expediente.add(ficha(documento));
        }
        return expediente;
    }

    // ------------------------------------------------------------------

    /**
     * Desenlace de la revision: el estado se DERIVA del resultado —solo
     * "conforme" valida— y viaja por Transiciones para dejar auditoria.
     */
    private DocumentoSolicitud aplicarRevision(DocumentoSolicitud documento, String resultado,
                                               String observaciones, Actor actor) {
        // Serializa la revisión real y relee su fuente antes de modificarla; no crea O->O.
        em.refresh(documento,LockModeType.PESSIMISTIC_WRITE);
        documento.registrarRevision(resultado, observaciones);
        transiciones.aplicar(documento, documento.getId(),
                DocumentoSolicitud.estadoSegunRevision(resultado), actor,
                motivo(resultado, observaciones));
        var origen=fuentesAsuntos.registrarRevisionDocumento(documento,actor);
        // Aviso al agente (§4 F6, punto 7), SOLO al observar: conformar no
        // avisa, porque no hay nada que subsanar.
        if (DocumentoSolicitud.REVISION_OBSERVADO.equals(resultado)) {
            avisarDocumentoObservado(documento, observaciones, origen,actor);
        }
        return documentos.save(documento);
    }

    /**
     * El mensaje nombra el TIPO del documento (no el archivo) para que el
     * agente sepa que tiene que rehacer, y el detalle es literal:
     * {@code ": " + observacion} cuando la hay, {@code "."} cuando no.
     */
    private void avisarDocumentoObservado(DocumentoSolicitud documento, String observaciones,
                                          FuentesAsuntos.Origen origen,Actor actor) {
        SolicitudAlquiler solicitud = documento.getSolicitud();
        if (solicitud == null) {
            return;
        }
        String tipoDoc = documento.getTipoDocumento() != null
                && !enBlanco(documento.getTipoDocumento().getTipoDocumento())
                ? documento.getTipoDocumento().getTipoDocumento()
                : (enBlanco(documento.getNombreArchivo()) ? "un documento" : documento.getNombreArchivo());
        String detalle = enBlanco(observaciones) ? "." : ": " + observaciones;
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_DOCUMENTO_REVISADO, Alerta.MEDIA,
                "SOLICITUD_ALQUILER", solicitud.getId(),
                solicitud.getAgente() != null ? solicitud.getAgente().getId() : null,
                "El broker observo el documento \"" + tipoDoc + "\" de la solicitud "
                        + solicitud.getCodigoSolicitud() + detalle,
                origen), actor);
    }

    private static String motivo(String resultado, String observaciones) {
        if (!DocumentoSolicitud.REVISION_OBSERVADO.equals(resultado)) {
            return "Documento conforme.";
        }
        return "Documento observado: " + observaciones.trim();
    }

    /**
     * El cable viaja con el CODIGO de una letra y el catalogo con el id 1..8;
     * la traduccion es parte del contrato (ver TipoDocumentoRequerido). Un
     * codigo fuera del mapa y un id que no este en la BD dan el MISMO mensaje,
     * como en la v1.
     */
    private TipoDocumentoRequerido tipoDeCodigo(String codigo) {
        Long idCatalogo = TipoDocumentoRequerido.idDe(codigo);
        if (idCatalogo == null) {
            throw new ReglaNegocioException("Tipo de documento invalido: " + codigo);
        }
        return tipos.findById(idCatalogo)
                .orElseThrow(() -> new ReglaNegocioException("Tipo de documento invalido: " + codigo));
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    private static FichaDocumento ficha(DocumentoSolicitud d) {
        TipoDocumentoRequerido tipo = d.getTipoDocumento();
        return new FichaDocumento(
                d.getId(),
                d.getSolicitud() != null ? d.getSolicitud().getId() : null,
                tipo != null ? TipoDocumentoRequerido.codigoDe(tipo.getId()) : null,
                tipo != null ? tipo.getTipoDocumento() : null,
                d.getNombreArchivo(), d.getRutaArchivo() == null || d.getRutaArchivo().isBlank()
                        ? d.getRutaArchivo() : java.util.Objects.requireNonNull(d.getArchivoRef(),
                        "El documento carece de referencia de archivo").toString(), Fechas.local(d.getFechaEntrega()),
                d.estadoActual(), d.getResultadoRevision(), d.getObservaciones());
    }
}
