package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.persistence.repositorio.HistoriaPublicacionRepository;
import com.controllocal.service.PublicacionService.FichaPublicacion;
import com.controllocal.service.publicacion.ProyeccionPublicacion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Los anuncios de varios encargos, de una vez</b> (V70).
 *
 * <h2>Por que existe</h2>
 * La ficha de una propiedad pinta un bloque por encargo, y cada bloque enseña
 * sus anuncios. Pedirlos dentro del bucle serian tantas consultas como encargos
 * — el N+1 en pequeño que RC-003 vino a quitar—. Aqui se piden todos juntos y se
 * reparten en memoria, igual que el listado cuelga los encargos de una pagina.
 *
 * <h2>Por que no vive en PublicacionServiceImpl</h2>
 * Porque no es un caso de uso de publicacion: es una lectura que necesita la
 * ficha. Ponerlo alli obligaria a {@code PropiedadUniversalServiceImpl} a
 * depender del servicio de publicaciones entero para usar un metodo, y a
 * {@code PublicacionServiceImpl} a conocer la forma de la ficha.
 *
 * <h2>La operacion sale del encargo, siempre</h2>
 * Cada anuncio se rotula con el {@code nombreDelImporte()} de la operacion de
 * SU encargo: «precio de venta» o «renta mensual». Es el mismo criterio que el
 * bloque del encargo, y por el mismo motivo — decidirlo en el cliente pondria
 * semantica inmobiliaria en la interfaz (D-A-1 §5).
 */
@Component
public class AnunciosDeLosEncargos {

    private final PublicacionRepository publicaciones;
    private final HistoriaPublicacionRepository historia;

    public AnunciosDeLosEncargos(PublicacionRepository publicaciones,HistoriaPublicacionRepository historia) {
        this.publicaciones = publicaciones;
        this.historia = historia;
    }

    /**
     * @param encargos los de la propiedad, ya leidos. Se pasan enteros y no solo
     *                 sus ids porque de ellos sale la operacion con la que se
     *                 rotula cada importe
     */
    public Map<Long, List<FichaPublicacion>> deEncargos(long idOrganizacion,
                                                        List<Captacion> encargos) {
        if (encargos.isEmpty()) {
            return Map.of();
        }
        Map<Long, Captacion> episodios = new LinkedHashMap<>();
        encargos.forEach(encargo -> episodios.put(encargo.getId(), encargo));
        var versionesComposicion=historia.versionesComposiciones(idOrganizacion,encargos.stream()
                .map(Captacion::getIdComposicionActual).filter(java.util.Objects::nonNull).distinct().toList());

        Map<Long, List<FichaPublicacion>> porEncargo = new LinkedHashMap<>();
        for (Publicacion anuncio : publicaciones.deEncargos(idOrganizacion, episodios.keySet())) {
            Captacion encargo = episodios.get(anuncio.getIdEncargo());
            porEncargo.computeIfAbsent(anuncio.getIdEncargo(), id -> new ArrayList<>())
                    .add(ProyeccionPublicacion.ficha(anuncio, encargo,encargo.getIdComposicionActual()==null?null:
                            versionesComposicion.get(encargo.getIdComposicionActual())));
        }
        return porEncargo;
    }

}
