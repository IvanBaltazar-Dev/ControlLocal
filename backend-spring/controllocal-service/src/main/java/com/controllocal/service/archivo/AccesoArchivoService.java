package com.controllocal.service.archivo;

import com.controllocal.persistence.repositorio.ReferenciaArchivoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PerfilService;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.encargo.AccesoArchivoEncargo;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoSolicitud;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/** CAT-E32/E33: poseer una referencia no sustituye recurso, finalidad ni autoridad vigentes. */
@Service
public class AccesoArchivoService {
    public record ReferenciaAutorizada(UUID referencia, String tipo, long idReferencia, long idRecurso,
                                      String finalidad, String claveProveedor, String huellaVinculo, String huellaContenido) { }

    private final ReferenciaArchivoRepository referencias;
    private final AccesoSolicitud solicitudes;
    private final AutoridadDePropiedad propiedades;
    private final PerfilService perfiles;
    private final AccesoArchivoEncargo encargos;

    public AccesoArchivoService(ReferenciaArchivoRepository referencias, AccesoSolicitud solicitudes,
                               AutoridadDePropiedad propiedades, PerfilService perfiles,
                               AccesoArchivoEncargo encargos) {
        this.referencias = referencias;
        this.solicitudes = solicitudes;
        this.propiedades = propiedades;
        this.perfiles = perfiles;
        this.encargos = encargos;
    }

    @Transactional(readOnly = true)
    public ReferenciaAutorizada resolver(String referencia, Actor actor) {
        UUID id = referenciaDe(referencia);
        Objects.requireNonNull(actor, "El actor procede del contexto verificado.");
        var actuales = referencias.actuales(actor.idOrganizacion(), id);
        if (actuales.isEmpty()) throw new NoEncontradoException("Archivo");
        if (actuales.size() != 1) throw new ConflictoException("La referencia de archivo no identifica un vínculo único.");
        var actual = actuales.get(0);
        if (!id.equals(actual.getReferencia()) || actual.getClaveProveedor() == null
                || actual.getClaveProveedor().isBlank())
            throw new IllegalStateException("El vínculo de archivo no conserva su identidad.");
        String huellaVinculo = null;
        String huellaContenido = null;
        String finalidad = switch (actual.getTipo()) {
            case "ARCHIVO_ENCARGO", "CARGA_TEMPORAL" -> {
                exigirActuacionComercial(actor);
                var autorizado = "CARGA_TEMPORAL".equals(actual.getTipo())
                        ? encargos.autorizarCarga(id,actor) : encargos.autorizar(id, actor);
                if (autorizado.idArchivo() != actual.getIdReferencia()
                        || !Objects.equals(autorizado.claveProveedor(),actual.getClaveProveedor()))
                    throw new ConflictoException("El vínculo de archivo del Encargo cambió.");
                huellaContenido = autorizado.huella();
                huellaVinculo = String.join("\u0000",Long.toString(autorizado.version()),
                        String.valueOf(autorizado.encargo()),String.valueOf(autorizado.documento()),
                        Long.toString(autorizado.autor()),autorizado.huella());
                yield autorizado.finalidad();
            }
            case "DOCUMENTO_SOLICITUD" -> {
                exigirActuacionComercial(actor);
                solicitudes.exigirConsultaActual(actual.getIdRecurso(), actor);
                yield "CONSULTA_EXPEDIENTE";
            }
            case "FOTO_PROPIEDAD" -> {
                if (!"A".equals(actual.getEstado())) throw new NoEncontradoException("Archivo");
                exigirActuacionComercial(actor);
                // La lectura relacional se resuelve por el mismo caso Core que describe la propiedad.
                if(!propiedades.puedeLeer(actor,actual.getIdRecurso())) throw new NoEncontradoException("Archivo");
                yield "CONSULTA_GALERIA";
            }
            case "FOTO_PERFIL" -> {
                if (actual.getIdReferencia() != actor.idPersona() || actual.getIdRecurso() != actor.idPersona())
                    throw new NoEncontradoException("Archivo");
                if (!id.toString().equals(perfiles.obtener(actor).fotoClave()))
                    throw new NoEncontradoException("Archivo");
                yield "PERFIL_PROPIO";
            }
            default -> throw new IllegalStateException("Tipo de referencia de archivo no reconocido.");
        };
        return new ReferenciaAutorizada(id, actual.getTipo(), actual.getIdReferencia(), actual.getIdRecurso(),
                finalidad, actual.getClaveProveedor(), huellaVinculo,huellaContenido);
    }

    /** No reutiliza una decisión anterior mientras se cargaban los bytes. */
    @Transactional(readOnly = true)
    public void revalidar(ReferenciaAutorizada previa, Actor actor) {
        if (!previa.equals(resolver(previa.referencia().toString(), actor)))
            throw new ConflictoException("El vínculo del archivo cambió; consulta su referencia vigente.");
    }

    /** Comprueba los bytes contra la huella conocida; no concede acceso ni acredita autenticidad. */
    public String verificarContenido(ReferenciaAutorizada referencia, byte[] contenido) {
        if(contenido==null)throw new IllegalStateException("El almacén no devolvió el contenido del archivo.");
        try {
            String actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(contenido));
            if(referencia.huellaContenido()!=null&&!referencia.huellaContenido().equals(actual))
                throw new IllegalStateException("El contenido del archivo no conserva la integridad de su versión.");
            return actual;
        } catch(java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("No se pudo comprobar la integridad del archivo.",imposible);
        }
    }

    private static UUID referenciaDe(String valor) {
        if (valor == null || !valor.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
            throw new ReglaNegocioException("Referencia de archivo inválida.");
        return UUID.fromString(valor);
    }

    private static void exigirActuacionComercial(Actor actor) {
        if (!actor.esAgente() && !actor.esBroker()) throw new AccesoNoAutorizadoException();
    }
}
