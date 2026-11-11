package com.controllocal.service.documentos;

import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.FotoPropiedadService.FotoDePropiedad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * RF-052 / AC-121: Documentos resuelve una selección privada autorizada y su
 * orden. El propietario del contenido conserva este valor cuando lo utiliza;
 * seleccionar no reordena la galería, publica ni transforma el original.
 */
@Service
public class SeleccionFotosContextualesService {
    public enum Finalidad { REVISION_PRIVADA, DIFUSION }

    public record Contexto(String recurso, long id) { }
    public record FotoVersionada(UUID referencia, String nombreArchivo, int ordenGaleria) { }
    public record Observacion(Contexto contexto, List<FotoVersionada> fotos, String versionObservada) {
        public Observacion { fotos = List.copyOf(fotos); }
    }
    public record Solicitud(List<UUID> fotosVersionadas, Finalidad finalidad, String versionObservada) { }
    public record Seleccion(Contexto contexto, List<UUID> fotosVersionadas, Finalidad finalidad,
                            String versionObservada) {
        public Seleccion { fotosVersionadas = List.copyOf(fotosVersionadas); }
    }

    private final FotoPropiedadService fotos;

    public SeleccionFotosContextualesService(FotoPropiedadService fotos) {
        this.fotos = fotos;
    }

    /** La huella procede de la misma galería autorizada que se presenta. */
    @Transactional(readOnly = true)
    public Observacion observar(long idPropiedad, Actor actor) {
        exigirLectura(actor);
        return observarAutorizado(idPropiedad, actor);
    }

    /** Predicado interno; conserva la autoridad de la galería sin abrir su DTO ni sus binarios. */
    public boolean originalPermitidoParaLectura(long idPropiedad, UUID original, Actor actor) {
        try { exigirLectura(actor); } catch (AccesoNoAutorizadoException denegada) { return false; }
        return fotos.originalPermitidoParaLectura(idPropiedad, original, actor);
    }

    /**
     * Devuelve únicamente referencias de originales de esta galería, en el
     * orden solicitado. DIFUSION declara el propósito de la selección: obtener
     * una salida conserva su revalidación de permiso, metadatos y PUB.
     */
    @Transactional(readOnly = true)
    public Seleccion seleccionar(long idPropiedad, Solicitud datos, Actor actor) {
        Objects.requireNonNull(actor, "El actor procede del contexto verificado.");
        if (!actor.esAgente()) throw new AccesoNoAutorizadoException();
        if (datos == null || datos.fotosVersionadas() == null || datos.finalidad() == null
                || datos.versionObservada() == null || datos.versionObservada().isBlank()) {
            throw new ReglaNegocioException("Indique fotos, finalidad y versión de la galería observada.");
        }
        List<UUID> elegidas = new ArrayList<>(datos.fotosVersionadas());
        if (elegidas.contains(null))
            throw new ReglaNegocioException("Cada foto necesita su referencia de versión.");
        if (new LinkedHashSet<>(elegidas).size() != elegidas.size())
            throw new ReglaNegocioException("Una foto sólo puede aparecer una vez en la selección.");

        Observacion actual = observarAutorizado(idPropiedad, actor);
        if (!actual.versionObservada().equals(datos.versionObservada()))
            throw new ConflictoException("La galería cambió; revise su observación antes de seleccionar.");
        Set<UUID> permitidas = new LinkedHashSet<>();
        actual.fotos().forEach(foto -> permitidas.add(foto.referencia()));
        if (!permitidas.containsAll(elegidas))
            throw new ReglaNegocioException("Una foto no pertenece a la galería observada de esta propiedad.");
        return new Seleccion(actual.contexto(), elegidas, datos.finalidad(), actual.versionObservada());
    }

    private Observacion observarAutorizado(long idPropiedad, Actor actor) {
        if (idPropiedad <= 0) throw new ReglaNegocioException("El id de la propiedad debe ser mayor que cero.");
        List<FotoDePropiedad> galeria = fotos.listar(idPropiedad, actor);
        List<FotoVersionada> versiones = galeria.stream()
                .map(foto -> new FotoVersionada(referenciaOriginal(foto), foto.nombreArchivo(), foto.orden()))
                .toList();
        return new Observacion(new Contexto("PROPIEDAD", idPropiedad), versiones,
                huella(actor.idOrganizacion(), idPropiedad, galeria));
    }

    private static void exigirLectura(Actor actor) {
        Objects.requireNonNull(actor, "El actor procede del contexto verificado.");
        if (!actor.esAgente() && !actor.esBroker()) throw new AccesoNoAutorizadoException();
    }

    private static UUID referenciaOriginal(FotoDePropiedad foto) {
        try {
            return UUID.fromString(foto.clave());
        } catch (IllegalArgumentException | NullPointerException invalida) {
            throw new IllegalStateException("La foto carece de referencia original válida.", invalida);
        }
    }

    /** Formato canónico con longitudes explícitas; no depende de JSON ni del proveedor. */
    private static String huella(long organizacion, long propiedad, List<FotoDePropiedad> galeria) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream salida = new DataOutputStream(bytes)) {
                salida.writeInt(1);
                salida.writeLong(organizacion);
                salida.writeLong(propiedad);
                salida.writeInt(galeria.size());
                for (FotoDePropiedad foto : galeria) {
                    UUID referencia = referenciaOriginal(foto);
                    salida.writeLong(Objects.requireNonNull(foto.id(), "La foto necesita identidad."));
                    salida.writeLong(referencia.getMostSignificantBits());
                    salida.writeLong(referencia.getLeastSignificantBits());
                    salida.writeInt(foto.orden());
                    byte[] nombre = Objects.requireNonNull(foto.nombreArchivo(), "La foto necesita nombre.")
                            .getBytes(StandardCharsets.UTF_8);
                    salida.writeInt(nombre.length);
                    salida.write(nombre);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("No se pudo identificar la observación de la galería.", imposible);
        }
    }
}
