package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * <b>El unico escritor de {@code foto_propiedad}</b> (WP-C).
 *
 * <p>Las cuatro operaciones de la galeria en un sitio, con la misma autoridad y
 * el mismo invariante de orden. Antes vivian dentro de
 * {@code LocalComercialServiceImpl} —tres de las cuatro; reordenar no existia—
 * y solo las alcanzaba {@code /locales/{id}/fotos}.
 *
 * <h2>El invariante de orden, dicho una vez</h2>
 * {@code orden} es 1..N sin huecos y sin repetidos, y la foto con orden 1 es la
 * portada (V91). Las tres escrituras lo mantienen: anadir pone {@code N+1},
 * retirar recoloca las activas siguientes y reordenar reescribe sus N posiciones. Un
 * hueco no rompe ninguna consulta —{@code ORDER BY orden, id_foto} sigue
 * respondiendo— y por eso mismo nadie lo notaria: lo que rompe es la frase «la
 * portada es la foto 1», que a partir de aqui es lo que el cliente usa.
 */
@Service
public class FotoPropiedadServiceImpl implements FotoPropiedadService {

    private final FotoPropiedadRepository fotos;
    private final PropiedadRepository propiedades;
    private final AutoridadDePropiedad autoridad;
    private final Transiciones transiciones;

    public FotoPropiedadServiceImpl(FotoPropiedadRepository fotos,
                                    PropiedadRepository propiedades,
                                    AutoridadDePropiedad autoridad, Transiciones transiciones) {
        this.fotos = fotos;
        this.propiedades = propiedades;
        this.autoridad = autoridad;
        this.transiciones = transiciones;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FotoDePropiedad> listar(long idPropiedad, Actor actor) {
        validarId(idPropiedad, "El id de la propiedad");
        autoridad.buscarParaLectura(actor, idPropiedad)
                .orElseThrow(() -> new com.controllocal.service.excepcion.NoEncontradoException("Propiedad"));
        return fotos.deLaPropiedad(actor.idOrganizacion(), idPropiedad).stream()
                .map(FotoPropiedadServiceImpl::comoFicha)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean originalPermitidoParaLectura(long idPropiedad, java.util.UUID original, Actor actor) {
        validarId(idPropiedad, "El id de la propiedad");
        if (original == null) throw new ReglaNegocioException("Identifica la fotografía original.");
        try {
            if (autoridad.buscarParaLectura(actor, idPropiedad).isEmpty()) return false;
            return fotos.deLaPropiedad(actor.idOrganizacion(), idPropiedad).stream()
                    .anyMatch(f -> original.equals(f.getArchivoRef()));
        } catch (com.controllocal.service.excepcion.AccesoNoAutorizadoException
                 | com.controllocal.service.excepcion.NoEncontradoException denegada) {
            // La frontera transaccional devuelve la denegación; las averías siguen propagándose.
            return false;
        }
    }

    @Override
    @Transactional
    public String prepararCarga(long idPropiedad,int tope,Actor actor) {
        validarId(idPropiedad,"El id de la propiedad");
        if(tope!=TOPE_UNIVERSAL&&tope!=TOPE_DEL_LOCAL)throw new ReglaNegocioException("La puerta de la galería no tiene un tope definido.");
        Propiedad propiedad=tomarConAutoridad(idPropiedad,actor);
        int cantidad=fotos.deLaPropiedad(actor.idOrganizacion(),idPropiedad).size();
        if(cantidad>=tope)throw new ReglaNegocioException("Maximo "+tope+" fotos por propiedad. Elimina alguna para subir otra.");
        return com.controllocal.service.soporte.Idempotencia.huellaExacta("FOTO-ORIGINAL\u0000"+actor.idOrganizacion()+"\u0000"+idPropiedad+"\u0000"+propiedad.getVersionFisica()+"\u0000"+tope+"\u0000"+cantidad);
    }

    @Override
    @Transactional
    public FotoDePropiedad agregar(long idPropiedad, String clave, String nombreArchivo,
                                   int tope, Actor actor) {
        validarId(idPropiedad, "El id de la propiedad");
        Propiedad propiedad = tomarConAutoridad(idPropiedad, actor);

        List<FotoPropiedad> galeria = fotos.deLaPropiedad(actor.idOrganizacion(), idPropiedad);
        if (galeria.size() >= tope) {
            throw new ReglaNegocioException("Maximo " + tope + " fotos por propiedad. Elimina "
                    + "alguna para subir otra.");
        }
        FotoPropiedad foto = new FotoPropiedad();
        foto.setOrganizacionId(actor.idOrganizacion());
        foto.setIdPropiedad(propiedad.getId());
        foto.setClave(clave);
        foto.setNombreArchivo(nombreArchivo);
        foto.setIdPersonaRegistro(actor.idPersona());
        foto.setIdRolRegistro(actor.idRolOperativo());
        foto.setRolRegistro(actor.rolEfectivo());
        transiciones.iniciar(foto, FotoPropiedad.ACTIVA);
        // 1-based (V91): la portada es la foto 1, no la 0.
        foto.setOrden(galeria.size() + 1);
        fotos.save(foto);
        return comoFicha(foto);
    }

    @Override
    @Transactional
    public boolean eliminar(long idFoto, Actor actor) {
        validarId(idFoto, "El id de la foto");
        return fotos.findById(idFoto)
                // Una foto de otra corredora se comporta como inexistente (404).
                .filter(foto -> foto.getOrganizacionId().equals(actor.idOrganizacion()))
                .map(foto -> {
                    // La autoridad es la de la PROPIEDAD: la foto no tiene dueno
                    // propio, y llegar por su id no puede ser una puerta mas
                    // barata que llegar por el del inmueble (P0-1).
                    tomarConAutoridad(foto.getIdPropiedad(), actor);
                    if (!fotos.estaActiva(actor.idOrganizacion(), idFoto)) return false;
                    foto.setFechaRetiro(OffsetDateTime.now());
                    transiciones.aplicar(foto, foto.getId(), FotoPropiedad.RETIRADA, actor,
                            "Retirada de la galería activa; original conservado.");
                    fotos.save(foto);
                    // Y se cierra el hueco. Sin esto, borrar la portada dejaria
                    // la galeria empezando en 2 y «la foto 1» no existiria.
                    recolocar(actor.idOrganizacion(), foto.getIdPropiedad(), foto.getId());
                    return true;
                }).orElse(false);
    }

    @Override
    @Transactional
    public List<FotoDePropiedad> reordenar(long idPropiedad, List<Long> idsEnOrden, Actor actor) {
        validarId(idPropiedad, "El id de la propiedad");
        if (idsEnOrden == null || idsEnOrden.isEmpty()) {
            throw new ReglaNegocioException("Hay que decir el orden de las fotos.");
        }
        tomarConAutoridad(idPropiedad, actor);

        List<FotoPropiedad> galeria = fotos.deLaPropiedad(actor.idOrganizacion(), idPropiedad);
        exigirLaGaleriaCompleta(galeria, idsEnOrden);

        int posicion = 1;
        List<FotoDePropiedad> ordenada = new ArrayList<>();
        for (Long id : idsEnOrden) {
            FotoPropiedad foto = galeria.stream()
                    .filter(f -> f.getId().equals(id))
                    .findFirst()
                    .orElseThrow();
            foto.setOrden(posicion++);
            fotos.save(foto);
            ordenada.add(comoFicha(foto));
        }
        return List.copyOf(ordenada);
    }

    // ------------------------------------------------------------------

    /**
     * <b>La propiedad, tomada y con su autoridad comprobada</b> (P0-1, F2.10).
     *
     * <p>Se carga con candado y no con un {@code exists}: la autoridad necesita
     * la fila —quien responde por ella—, y necesita que ese responsable siga
     * siendo el mismo en el instante en que la foto se inserta o se borra. Una
     * escritura que llegue despues de un traspaso ya comiteado ve al nuevo
     * responsable y recibe el 403, en vez de aterrizar.
     */
    private Propiedad tomarConAutoridad(long idPropiedad, Actor actor) {
        Propiedad propiedad = propiedades
                .bloquearParaEscritura(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new ReglaNegocioException("La propiedad no existe."));
        autoridad.exigirEdicion(actor, propiedad);
        return propiedad;
    }

    /**
     * Exige que el orden nombre <b>exactamente</b> la galeria: mismos ids, sin
     * repetir y sin faltar.
     *
     * <p>Un subconjunto obligaria a decidir donde van los que no se nombraron, y
     * cualquiera de las dos respuestas —delante o detras— es una invencion sobre
     * la intencion de quien reordeno.
     */
    private static void exigirLaGaleriaCompleta(List<FotoPropiedad> galeria, List<Long> ids) {
        Set<Long> pedidos = new LinkedHashSet<>(ids);
        if (pedidos.size() != ids.size()) {
            throw new ReglaNegocioException(
                    "El orden repite alguna foto: cada una ocupa una sola posicion.");
        }
        Set<Long> reales = new LinkedHashSet<>();
        galeria.forEach(foto -> reales.add(foto.getId()));
        if (!reales.equals(pedidos)) {
            throw new ReglaNegocioException("El orden tiene que nombrar las " + reales.size()
                    + " foto(s) de la propiedad, ni una mas ni una menos. Recibido: "
                    + pedidos.size() + ".");
        }
    }

    /** Cierra el hueco de la galería activa; no cambia el orden histórico de la retirada. */
    private void recolocar(long idOrganizacion, long idPropiedad, Long idEliminada) {
        int posicion = 1;
        for (FotoPropiedad foto : fotos.deLaPropiedad(idOrganizacion, idPropiedad)) {
            if (foto.getId().equals(idEliminada)) {
                // No recolocar el antecedente si el contexto aún lo mantiene antes del flush.
                continue;
            }
            if (foto.getOrden() != posicion) {
                foto.setOrden(posicion);
                fotos.save(foto);
            }
            posicion++;
        }
    }

    private static FotoDePropiedad comoFicha(FotoPropiedad foto) {
        return new FotoDePropiedad(foto.getId(), java.util.Objects.requireNonNull(foto.getArchivoRef(),
                "La foto carece de referencia de archivo").toString(), foto.getNombreArchivo(),
                foto.getOrden());
    }

    private static void validarId(long id, String campo) {
        if (id <= 0) {
            throw new ReglaNegocioException(campo + " debe ser mayor que cero.");
        }
    }
}
