package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comun.EstadosDominio.EstadoPublicacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.service.PublicacionService.FichaPublicacion;
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

    public AnunciosDeLosEncargos(PublicacionRepository publicaciones) {
        this.publicaciones = publicaciones;
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
        Map<Long, OperacionInmobiliaria> operaciones = new LinkedHashMap<>();
        encargos.forEach(encargo -> operaciones.put(encargo.getId(), encargo.operacion()));

        Map<Long, List<FichaPublicacion>> porEncargo = new LinkedHashMap<>();
        for (Publicacion anuncio : publicaciones.deEncargos(idOrganizacion, operaciones.keySet())) {
            OperacionInmobiliaria operacion = operaciones.get(anuncio.getIdEncargo());
            porEncargo.computeIfAbsent(anuncio.getIdEncargo(), id -> new ArrayList<>())
                    .add(ficha(anuncio, operacion));
        }
        return porEncargo;
    }

    private static FichaPublicacion ficha(Publicacion p, OperacionInmobiliaria operacion) {
        return new FichaPublicacion(p.getId(), p.getIdEncargo(), p.getCanal(), p.getTituloAnuncio(),
                p.getImportePublicado(), p.getMoneda(),
                operacion == null ? null : operacion.nombreDelImporte(),
                p.getEstado(),
                p.getEstado() == null ? null : EstadoPublicacion.desde(p.getEstado()).descripcion(),
                Fechas.local(p.getFechaPublicacion()), Fechas.local(p.getFechaBaja()),
                p.getUrlPublicacion(), p.getCodigoOrigen());
    }
}
