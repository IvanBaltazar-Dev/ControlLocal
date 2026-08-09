package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.CondicionesEconomicas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas de publicacion: estado/titulo/codigo conservan sus defaults, pero la
 * moneda es obligatoria y nunca se supone. Version de anuncio
 * incremental y fecha de baja al cerrar. Mensajes identicos (contrato
 * congelado).
 */
@Service
public class PublicacionServiceImpl implements PublicacionService {

    private final PublicacionRepository publicaciones;

    public PublicacionServiceImpl(PublicacionRepository publicaciones) {
        this.publicaciones = publicaciones;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaPublicacion> listarPorInmueble(long idPropiedad) {
        if (idPropiedad <= 0) {
            return List.of();
        }
        return publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(idPropiedad).stream()
                .map(PublicacionServiceImpl::ficha)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String codigoEstadoPublicacion(long idPropiedad) {
        if (idPropiedad <= 0) {
            return Publicacion.ESTADO_BORRADOR;
        }
        return publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(idPropiedad).stream()
                .findFirst()
                .map(Publicacion::getEstado)
                .orElse(Publicacion.ESTADO_BORRADOR);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> codigosEstadoPublicacion(Collection<Long> idsPropiedad) {
        List<Long> ids = idsPropiedad == null ? List.of() : idsPropiedad.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> estados = new HashMap<>();
        for (Long id : ids) {
            estados.put(id, Publicacion.ESTADO_BORRADOR);
        }
        publicaciones.estadosPublicacion(ids)
                .forEach(fila -> estados.put(fila.getIdPropiedad(), fila.getEstado()));
        return estados;
    }

    @Override
    @Transactional
    public FichaPublicacion crear(long idPropiedad, DatosPublicacion datos, Actor actor) {
        if (idPropiedad <= 0) {
            throw new ReglaNegocioException("El local de la publicacion es obligatorio.");
        }
        String canal = canalOpcional(datos.canal());
        if (canal == null) {
            throw new ReglaNegocioException("El canal de la publicacion es obligatorio.");
        }
        String estado = codigoOpcional(datos.estado(), Publicacion.ESTADOS, "estado de publicacion");
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "de la renta publicada");

        Publicacion p = new Publicacion();
        p.setOrganizacionId(actor.idOrganizacion());
        p.setIdPropiedad(idPropiedad);
        p.setCanal(canal);
        p.setUrlPublicacion(datos.urlPublicacion());
        p.setVersionAnuncio(1);
        p.setRentaPublicada(datos.rentaPublicada());
        p.setMoneda(moneda);
        p.setEstado(estado == null ? Publicacion.ESTADO_PUBLICADO : estado);
        p.setTituloAnuncio(enBlanco(datos.tituloAnuncio()) ? "Publicacion " + idPropiedad : datos.tituloAnuncio());
        p.setCodigoOrigen(enBlanco(datos.codigoOrigen()) ? canal + "-" + idPropiedad : datos.codigoOrigen());
        p.setFechaPublicacion(OffsetDateTime.now());
        p.setFechaBaja(Publicacion.ESTADO_CERRADO.equals(p.getEstado()) ? OffsetDateTime.now() : null);
        publicaciones.save(p);
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaPublicacion actualizar(long idPublicacion, DatosPublicacion datos) {
        Publicacion actual = publicaciones.findById(idPublicacion)
                .orElseThrow(() -> new ReglaNegocioException("Publicacion no encontrada."));
        if (datos != null) {
            String canal = canalOpcional(datos.canal());
            if (canal != null) {
                actual.setCanal(canal);
            }
            actual.setUrlPublicacion(datos.urlPublicacion());
            if (datos.rentaPublicada() != null) {
                actual.setRentaPublicada(datos.rentaPublicada());
            }
            actual.setMoneda(CondicionesEconomicas.moneda(
                    datos.moneda(), "de la renta publicada"));
            if (!enBlanco(datos.tituloAnuncio())) {
                actual.setTituloAnuncio(datos.tituloAnuncio());
            }
            if (!enBlanco(datos.codigoOrigen())) {
                actual.setCodigoOrigen(datos.codigoOrigen());
            }
        }
        actual.setVersionAnuncio((actual.getVersionAnuncio() == null ? 1 : actual.getVersionAnuncio()) + 1);
        publicaciones.save(actual);
        return ficha(actual);
    }

    @Override
    @Transactional
    public FichaPublicacion cambiarEstado(long idPublicacion, String estado) {
        if (enBlanco(estado)) {
            throw new ReglaNegocioException("El estado de la publicacion es obligatorio.");
        }
        if (!Publicacion.ESTADOS.contains(estado)) {
            throw new ReglaNegocioException("Estado de publicacion no valido: " + estado);
        }
        Publicacion actual = publicaciones.findById(idPublicacion)
                .orElseThrow(() -> new ReglaNegocioException("Publicacion no encontrada."));
        actual.setEstado(estado);
        actual.setFechaBaja(Publicacion.ESTADO_CERRADO.equals(estado) ? OffsetDateTime.now() : null);
        if (Publicacion.ESTADO_PUBLICADO.equals(estado) && actual.getFechaPublicacion() == null) {
            actual.setFechaPublicacion(OffsetDateTime.now());
        }
        publicaciones.save(actual);
        return ficha(actual);
    }

    @Override
    @Transactional
    public void sincronizar(long idPropiedad, String codigoLocal, BigDecimal precioReferencial,
                            String monedaReferencial, String codigoEstado, Actor actor) {
        if (enBlanco(codigoEstado)) {
            return;
        }
        if (!Publicacion.ESTADOS.contains(codigoEstado)) {
            // Mensaje identico al CodigoEnum.fromCodigo de la v1 (llega como 400).
            throw new IllegalArgumentException("Codigo invalido para EstadoPublicacion: " + codigoEstado);
        }
        String moneda = CondicionesEconomicas.moneda(
                monedaReferencial, "del precio referencial");
        Publicacion principal = publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(idPropiedad).stream()
                .findFirst()
                .orElse(null);
        if (principal == null && Publicacion.ESTADO_BORRADOR.equals(codigoEstado)) {
            return;
        }
        if (principal == null) {
            principal = new Publicacion();
            principal.setOrganizacionId(actor.idOrganizacion());
            principal.setIdPropiedad(idPropiedad);
            principal.setCanal(Publicacion.CANAL_WEB_PROPIA);
            principal.setVersionAnuncio(1);
            principal.setMoneda(moneda);
            principal.setCodigoOrigen("WEB-" + idPropiedad);
            principal.setFechaPublicacion(OffsetDateTime.now());
        }
        principal.setEstado(codigoEstado);
        principal.setRentaPublicada(precioReferencial);
        principal.setMoneda(moneda);
        principal.setTituloAnuncio("Publicacion " + codigoLocal);
        principal.setFechaBaja(Publicacion.ESTADO_CERRADO.equals(codigoEstado) ? OffsetDateTime.now() : null);
        publicaciones.save(principal);
    }

    private static String canalOpcional(String canal) {
        if (enBlanco(canal)) {
            return null;
        }
        if (!Publicacion.CANALES.contains(canal)) {
            throw new ReglaNegocioException("Valor invalido para canal de publicacion: " + canal);
        }
        return canal;
    }

    private static String codigoOpcional(String valor, java.util.Set<String> dominio, String campo) {
        if (enBlanco(valor)) {
            return null;
        }
        if (!dominio.contains(valor)) {
            throw new ReglaNegocioException("Valor invalido para " + campo + ": " + valor);
        }
        return valor;
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    private static FichaPublicacion ficha(Publicacion p) {
        return new FichaPublicacion(p.getId(), p.getCanal(), p.getTituloAnuncio(), p.getRentaPublicada(),
                p.getMoneda(), p.getEstado(), Fechas.local(p.getFechaPublicacion()),
                Fechas.local(p.getFechaBaja()), p.getUrlPublicacion(), p.getCodigoOrigen());
    }
}
