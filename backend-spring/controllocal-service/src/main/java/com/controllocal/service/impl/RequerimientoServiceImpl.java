package com.controllocal.service.impl;

import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Fechas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Reglas y mensajes calcados de RequerimientosRest +
 * RequerimientoClienteBusinessLogicImpl de la v1.
 *
 * <p>Los distritos llegan por NOMBRE y se resuelven contra el catalogo activo
 * (global, compartido entre organizaciones), normalizando acentos y
 * mayusculas. Los que no esten catalogados se DESCARTAN en silencio: asi lo
 * hace la v1 para no romper la FK de {@code requerimiento_distrito}.
 */
@Service
public class RequerimientoServiceImpl implements RequerimientoService {

    private final RequerimientoClienteRepository requerimientos;
    private final DetalleClienteRepository clientes;
    private final DistritoRepository distritos;

    public RequerimientoServiceImpl(RequerimientoClienteRepository requerimientos,
                                    DetalleClienteRepository clientes, DistritoRepository distritos) {
        this.requerimientos = requerimientos;
        this.clientes = clientes;
        this.distritos = distritos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaRequerimiento> listarPorCliente(long idCliente, Actor actor) {
        return requerimientos.listarPorCliente(actor.idOrganizacion(), idCliente).stream()
                .map(RequerimientoServiceImpl::ficha)
                .toList();
    }

    @Override
    @Transactional
    public FichaRequerimiento crear(DatosRequerimiento datos, Actor actor) {
        validar(datos, datos != null ? datos.idCliente() : null);
        DetalleCliente cliente = clientes.buscarFicha(actor.idOrganizacion(), datos.idCliente())
                .orElseThrow(() -> new NoEncontradoException("Cliente"));

        RequerimientoCliente r = new RequerimientoCliente();
        r.setOrganizacionId(actor.idOrganizacion());
        r.setCliente(cliente);
        aplicar(r, datos);
        return ficha(requerimientos.save(r));
    }

    @Override
    @Transactional
    public FichaRequerimiento actualizar(long id, DatosRequerimiento datos, Actor actor) {
        RequerimientoCliente actual = requerimientos.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Requerimiento"));
        Long idCliente = datos != null && datos.idCliente() != null
                ? datos.idCliente()
                : (actual.getCliente() != null ? actual.getCliente().getId() : null);
        validar(datos, idCliente);

        if (datos.idCliente() != null && !datos.idCliente().equals(actual.getCliente().getId())) {
            actual.setCliente(clientes.buscarFicha(actor.idOrganizacion(), datos.idCliente())
                    .orElseThrow(() -> new NoEncontradoException("Cliente")));
        }
        aplicar(actual, datos);
        return ficha(requerimientos.save(actual));
    }

    @Override
    @Transactional
    public FichaRequerimiento cambiarEstado(long id, String estado, Actor actor) {
        if (estado == null || estado.isBlank()) {
            throw new ReglaNegocioException("El estado del requerimiento es obligatorio.");
        }
        String codigo = estado.trim();
        if (!RequerimientoCliente.ESTADOS.contains(codigo)) {
            throw new ReglaNegocioException("Estado de requerimiento no valido: " + estado);
        }
        RequerimientoCliente actual = requerimientos.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Requerimiento"));
        actual.setEstado(codigo);
        return ficha(requerimientos.save(actual));
    }

    // ------------------------------------------------------------------
    // Validacion y mapeo (mensajes exactos de la v1).
    // ------------------------------------------------------------------

    private static void validar(DatosRequerimiento datos, Long idCliente) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos del requerimiento son obligatorios.");
        }
        if (idCliente == null || idCliente <= 0) {
            throw new ReglaNegocioException("El cliente del requerimiento es obligatorio.");
        }
        if (datos.rubro() == null || datos.rubro().isBlank()) {
            throw new ReglaNegocioException("El rubro del requerimiento es obligatorio.");
        }
        if (datos.rentaMin() != null && datos.rentaMax() != null
                && datos.rentaMax().compareTo(datos.rentaMin()) < 0) {
            throw new ReglaNegocioException("La renta maxima no puede ser menor que la renta minima.");
        }
        if (datos.metrajeMin() != null && datos.metrajeMax() != null
                && datos.metrajeMax().compareTo(datos.metrajeMin()) < 0) {
            throw new ReglaNegocioException("El metraje maximo no puede ser menor que el metraje minimo.");
        }
    }

    private void aplicar(RequerimientoCliente r, DatosRequerimiento datos) {
        r.setRubro(datos.rubro());
        r.setTipoInmueble(opcional(datos.tipoInmueble(), RequerimientoCliente.TIPOS_INMUEBLE,
                "tipo de inmueble"));
        r.setRentaMin(datos.rentaMin());
        r.setRentaMax(datos.rentaMax());
        r.setMoneda(datos.moneda() == null || datos.moneda().isBlank()
                ? "PEN"
                : exigirCodigo(datos.moneda(), RequerimientoCliente.MONEDAS, "moneda"));
        r.setMetrajeMin(datos.metrajeMin());
        r.setMetrajeMax(datos.metrajeMax());
        r.setFrenteMinimo(datos.frenteMinimo());
        r.setEstado(datos.estado() == null || datos.estado().isBlank()
                ? RequerimientoCliente.ACTIVO
                : exigirCodigo(datos.estado(), RequerimientoCliente.ESTADOS, "estado del requerimiento"));
        r.setObservaciones(datos.observaciones());
        r.setDistritos(resolverDistritos(datos.distritos()));
    }

    /** Resuelve nombres contra el catalogo ACTIVO; descarta lo no catalogado y los repetidos. */
    private List<Distrito> resolverDistritos(List<String> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return new ArrayList<>();
        }
        List<Distrito> catalogo = distritos.findByActivoTrueOrderByNombre();
        List<Distrito> resueltos = new ArrayList<>();
        for (String pedido : pedidos) {
            if (pedido == null || pedido.isBlank()) {
                continue;
            }
            String objetivo = normalizar(pedido);
            catalogo.stream()
                    .filter(d -> d.getNombre() != null && normalizar(d.getNombre()).equals(objetivo))
                    .findFirst()
                    .filter(d -> resueltos.stream().noneMatch(ya -> ya.getId().equals(d.getId())))
                    .ifPresent(resueltos::add);
        }
        return resueltos;
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String opcional(String valor, java.util.Set<String> validos, String campo) {
        return valor == null || valor.isBlank() ? null : exigirCodigo(valor, validos, campo);
    }

    private static String exigirCodigo(String valor, java.util.Set<String> validos, String campo) {
        String codigo = valor.trim();
        if (!validos.contains(codigo)) {
            throw new ReglaNegocioException("Valor invalido para " + campo + ": " + valor);
        }
        return codigo;
    }

    private static FichaRequerimiento ficha(RequerimientoCliente r) {
        List<String> nombres = r.getDistritos() == null ? List.of()
                : r.getDistritos().stream().map(Distrito::getNombre).filter(Objects::nonNull).toList();
        return new FichaRequerimiento(
                r.getId(),
                r.getCliente() != null ? r.getCliente().getId() : null,
                r.getRubro(), r.getTipoInmueble(), r.getRentaMin(), r.getRentaMax(), r.getMoneda(),
                r.getMetrajeMin(), r.getMetrajeMax(), r.getFrenteMinimo(), r.getEstado(),
                r.getObservaciones(), nombres,
                Fechas.local(r.getFechaCreacion()), Fechas.local(r.getFechaActualizacion()));
    }
}
