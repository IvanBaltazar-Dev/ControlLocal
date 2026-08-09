package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoRegistroPropiedad;
import com.controllocal.domain.inmueble.DetalleLocalComercial;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.LocalListado;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.Pagina;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reglas heredadas del LocalComercialBusinessLogicImpl v1 (RF-004) con la
 * auditoria centralizada en {@link Transiciones}. Mensajes de error
 * identicos al contrato congelado, con dos mejoras documentadas:
 * el propietario se valida como rol PROPIETARIO vigente (la v1 dejaba
 * reventar la FK con un 500) y la transicion a un estado igual no genera
 * historial duplicado.
 */
@Service
public class LocalComercialServiceImpl implements LocalComercialService {

    /** Tope por pagina del cable congelado (el controlador ya acotaba a 100). */
    private static final int MAXIMO_POR_PAGINA = 100;

    private final PropiedadRepository propiedades;
    private final PersonaRolRepository roles;
    private final DistritoRepository distritos;
    private final FotoPropiedadRepository fotos;
    private final PrecioPropiedadRepository precios;
    private final PublicacionService publicaciones;
    private final ProspeccionService prospecciones;
    private final CaptacionRepository captaciones;
    private final ProspeccionRepository prospeccionesRepo;
    private final Transiciones transiciones;
    private final AlertaService alertas;

    public LocalComercialServiceImpl(PropiedadRepository propiedades,
                                     PersonaRolRepository roles,
                                     DistritoRepository distritos,
                                     FotoPropiedadRepository fotos,
                                     PrecioPropiedadRepository precios,
                                     PublicacionService publicaciones,
                                     ProspeccionService prospecciones,
                                     CaptacionRepository captaciones,
                                     ProspeccionRepository prospeccionesRepo,
                                     Transiciones transiciones,
                                     AlertaService alertas) {
        this.alertas = alertas;
        this.propiedades = propiedades;
        this.roles = roles;
        this.distritos = distritos;
        this.fotos = fotos;
        this.precios = precios;
        this.publicaciones = publicaciones;
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.prospeccionesRepo = prospeccionesRepo;
        this.transiciones = transiciones;
    }

    /**
     * Filtro, orden, paginacion y conteo bajan a SQL; solo el enriquecimiento
     * de la pagina (portada y estado de publicacion) se resuelve aqui, y en
     * LOTE: dos consultas por pagina, no dos por fila.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Pagina<FichaLocal> listar(FiltrosLocal filtros, Actor actor) {
        int pagina = Math.max(1, filtros.pagina());
        int tamano = Math.max(1, Math.min(MAXIMO_POR_PAGINA, filtros.tamano()));
        String texto = enBlancoANulo(filtros.texto());
        String estado = estadoFiltrado(filtros.estado());

        List<LocalListado> filas;
        long total;
        if (texto == null) {
            // Sin texto no hay nada que unir: el filtro de estado ya baja al
            // WHERE y el indice del listado lo sirve directo.
            Page<LocalListado> resultado = propiedades.buscar(actor.idOrganizacion(), null, estado,
                    PageRequest.of(pagina - 1, tamano));
            filas = resultado.getContent();
            total = resultado.getTotalElements();
        } else {
            // Con texto, el conjunto de candidatos decide QUE ids entran y en
            // que pagina; la proyeccion completa se carga despues, solo para
            // esos ids. El total sale del MISMO conjunto, asi que la lista y el
            // contador no pueden discrepar.
            List<Long> ids = propiedades.idsPorTexto(actor.idOrganizacion(), texto, estado,
                    tamano, (pagina - 1) * tamano);
            filas = ids.isEmpty() ? List.of()
                    : propiedades.buscarPorIds(actor.idOrganizacion(), ids);
            total = propiedades.contarPorTexto(actor.idOrganizacion(), texto, estado);
        }

        List<Long> ids = filas.stream().map(LocalListado::getId).toList();
        Map<Long, String> portadas = ids.isEmpty() ? Map.of() : fotos.portadas(ids).stream()
                .collect(Collectors.toMap(f -> f.getIdPropiedad(), f -> f.getClave()));
        Map<Long, String> estadosPublicacion =
                ids.isEmpty() ? Map.of() : publicaciones.codigosEstadoPublicacion(ids);

        return new Pagina<>(filas.stream()
                .map(p -> ficha(p, estadosPublicacion.get(p.getId()), portadas.get(p.getId())))
                .toList(), total);
    }

    /**
     * Un solo {@code group by} en la BD. El total sale de sumar los tres
     * estados en vez de una cuarta consulta: asi <b>no puede</b> quedar
     * descuadrado respecto de sus partes.
     */
    @Override
    @Transactional(readOnly = true)
    public ResumenLocales resumen(String texto, Actor actor) {
        String buscado = enBlancoANulo(texto);
        // El KPI mira EL MISMO conjunto que la lista —con texto, el de
        // candidatos— o no cuadraria con ella. El estado viaja nulo a
        // proposito: el resumen cuenta los tres cubos, no filtra por uno.
        List<ConteoPorEstado> conteos = buscado == null
                ? propiedades.contarPorEstado(actor.idOrganizacion(), null)
                : propiedades.contarPorEstadoConTexto(actor.idOrganizacion(), buscado, null);
        Map<String, Long> porEstado = conteos.stream()
                .collect(Collectors.toMap(ConteoPorEstado::getEstado, ConteoPorEstado::getTotal));

        long disponibles = porEstado.getOrDefault(Propiedad.LEGADO_DISPONIBLE, 0L);
        long noDisponibles = porEstado.getOrDefault(Propiedad.LEGADO_NO_DISPONIBLE, 0L);
        long inactivos = porEstado.getOrDefault(Propiedad.LEGADO_INACTIVO, 0L);
        return new ResumenLocales(disponibles + noDisponibles + inactivos,
                disponibles, noDisponibles, inactivos);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosibleDuplicado> posiblesDuplicados(DatosLocal datos, Long idExcluir, Actor actor) {
        if (datos == null || datos.idPropietario() == null || datos.idPropietario() <= 0
                || datos.direccion() == null || datos.direccion().isBlank()
                || datos.metraje() == null || datos.metraje().compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        String direccion = normalizarTecnico(datos.direccion());
        return propiedades.findByOrganizacionIdAndRolPropietarioIdAndIdNotOrderById(
                        actor.idOrganizacion(), datos.idPropietario(),
                        idExcluir != null && idExcluir > 0 ? idExcluir : -1L).stream()
                .filter(candidato -> normalizarTecnico(candidato.getDireccion()).equals(direccion))
                .filter(candidato -> compatibleOpcional(candidato.getInteriorUnidad(), datos.interiorUnidad()))
                .filter(candidato -> compatibleOpcional(candidato.getPiso(), datos.piso()))
                .filter(candidato -> areaAproximada(candidato.getMetraje(), datos.metraje()))
                .map(candidato -> new PosibleDuplicado(candidato.getId(), candidato.getCodigo(),
                        candidato.getDireccion(), candidato.getInteriorUnidad(), candidato.getPiso(),
                        candidato.getMetraje(), criteriosCoincidentes(candidato, datos)))
                .toList();
    }

    /** Un estado que no existe filtra a vacio; no es un error del cliente. */
    private static String estadoFiltrado(String estado) {
        return enBlancoANulo(estado);
    }

    private static String enBlancoANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FichaLocal> buscarPorId(long id, Actor actor) {
        validarId(id, "El id de local comercial");
        return propiedades.buscarFicha(actor.idOrganizacion(), id).map(this::fichaCompleta);
    }

    @Override
    @Transactional
    public FichaLocal registrar(DatosLocal datos, Actor actor) {
        String estado = estadoResuelto(datos.estado());
        String tipoInmueble = tipoInmuebleResuelto(datos.tipoInmueble());
        validarUso(datos.uso());
        validarObligatorios(datos);

        Propiedad propiedad = new Propiedad();
        // El tenant se fija ANTES de copiar campos: asignarDetalleLocal lo
        // propaga al detalle, que es parte del mismo agregado.
        propiedad.setOrganizacionId(actor.idOrganizacion());
        copiarCampos(datos, propiedad);
        propiedad.setTipoInmueble(tipoInmueble);
        propiedad.setUso(Propiedad.USO_COMERCIAL);
        propiedad.setRolPropietario(rolPropietarioVigente(datos.idPropietario()));
        propiedad.aplicarEstadoLegado(estado);
        resolverDistrito(propiedad);
        propiedades.save(propiedad);

        publicaciones.sincronizar(propiedad.getId(), propiedad.getCodigo(),
                propiedad.getPrecioReferencial(), propiedad.getMonedaReferencial(),
                datos.estadoPublicacion(), actor);

        // Deuda F2 cerrada: el alta abre la PROSPECCION inicial del agente sobre
        // el local (paridad con registrarProspeccionInicial de la v1). Es lo que
        // ademas hace al agente "dueno" del local para poder editarlo despues.
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad.getId(), null), actor);

        // Paridad v1 del POST: no se re-lee la fila, asi que propietarioNombre,
        // fechaRegistro y portada salen nulos en la respuesta del alta.
        return ficha(propiedad, publicaciones.codigoEstadoPublicacion(propiedad.getId()), null, null);
    }

    @Override
    @Transactional
    public FichaLocal actualizar(long id, DatosLocal datos, Actor actor) {
        Propiedad original = propiedades.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new ReglaNegocioException("Local no encontrado"));

        exigirPertenencia(id, actor);

        String estadoNuevo = estadoResuelto(datos.estado());
        String tipoInmueble = tipoInmuebleResuelto(datos.tipoInmueble());
        validarUso(datos.uso());
        validarObligatorios(datos);
        PersonaRol rolPropietario = rolPropietarioVigente(datos.idPropietario());

        // Trazabilidad RF-004: precio nuevo => hito 'U' (autorizado) en el historico.
        if (original.getPrecioReferencial().compareTo(datos.precioReferencial()) != 0
                || !Objects.equals(original.getMonedaReferencial(), datos.monedaReferencial())) {
            PrecioPropiedad hito = new PrecioPropiedad();
            hito.setOrganizacionId(original.getOrganizacionId());
            hito.setIdPropiedad(id);
            hito.setHito(PrecioPropiedad.HITO_AUTORIZADO);
            hito.setMoneda(datos.monedaReferencial());
            hito.setMonto(datos.precioReferencial());
            hito.setFecha(LocalDate.now());
            precios.save(hito);
        }

        String rubroOriginal = original.getDetalleLocal() != null
                ? original.getDetalleLocal().getRubroPermitido() : null;
        boolean cambioSensible = original.getMetraje().compareTo(datos.metraje()) != 0
                || !Objects.equals(rubroOriginal, datos.rubroPermitido());
        if (cambioSensible) {
            // Deuda vieja de F2, cerrada con F6. Dos rarezas del cable que se
            // replican tal cual y NO se arreglan aqui:
            //  * viaja con el tipo SOLICITUD_EVALUADA. La v1 lo admite en un
            //    comentario —"por las restricciones del CHECK ck_alerta_tipo"—:
            //    no existe un tipo que encaje. Es un bug congelado (D-F6-5);
            //  * su entidad es INMUEBLE, que la v2 renombro a PROPIEDAD pero el
            //    cable sigue emitiendo asi (D-F6-4). Como ruta() no enruta
            //    INMUEBLE, esta alerta se muestra sin enlace.
            alertas.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_EVALUADA, Alerta.MEDIA,
                    "INMUEBLE", id, actor.idRolOperativo(),
                    "Modificación comercial sensible, revisar"), actor);
        }

        aplicarEstadoLegado(original, id, estadoNuevo, actor);

        copiarCampos(datos, original);
        original.setTipoInmueble(tipoInmueble);
        original.setUso(Propiedad.USO_COMERCIAL);
        original.setRolPropietario(rolPropietario);
        resolverDistrito(original);
        propiedades.save(original);

        publicaciones.sincronizar(id, original.getCodigo(),
                original.getPrecioReferencial(), original.getMonedaReferencial(),
                datos.estadoPublicacion(), actor);

        return fichaCompleta(original);
    }

    @Override
    @Transactional
    public boolean desactivar(long id, Actor actor) {
        validarId(id, "El id de local comercial");
        Propiedad propiedad = propiedades.findByOrganizacionIdAndId(actor.idOrganizacion(), id)
                .orElseThrow(() -> new ReglaNegocioException("Local no encontrado"));

        exigirPertenencia(id, actor);

        // EstadoRegistroPropiedad, no Propiedad.LEGADO_INACTIVO: el destino de
        // esta transicion es la COLUMNA `estado_registro`, y aquella constante
        // pertenece al vocabulario D/N/I del cable legado, que es una
        // proyeccion derivada (`estadoLegado()`), no una columna. Hoy funciona
        // por casualidad —las dos valen "I"—; el dia que cualquiera de los dos
        // vocabularios cambiara una letra, esto romperia en silencio.
        transiciones.aplicar(propiedad, id, EstadoRegistroPropiedad.INACTIVO.codigo(), actor,
                "Desactivación de local por el agente");
        transiciones.aplicarDisponibilidad(propiedad, id, DisponibilidadComercial.RETIRADO,
                actor, "Retiro comercial por desactivacion del registro");
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaLocal> listarMisLocales(int limite, int desplazamiento, Actor actor) {
        validarPagina(limite, desplazamiento);
        Page<Propiedad> pagina = captaciones.localesDelAgente(actor.idOrganizacion(),
                actor.idRolOperativo(), PageRequest.of(desplazamiento / limite, limite));
        List<Long> ids = pagina.stream().map(Propiedad::getId).toList();
        Map<Long, String> portadas = ids.isEmpty() ? Map.of() : fotos.portadas(ids).stream()
                .collect(Collectors.toMap(f -> f.getIdPropiedad(), f -> f.getClave()));
        Map<Long, String> estadosPublicacion = publicaciones.codigosEstadoPublicacion(ids);
        List<FichaLocal> items = pagina.stream()
                .map(p -> ficha(p, estadosPublicacion.get(p.getId()), portadas.get(p.getId()), nombrePropietario(p)))
                .toList();
        return new Pagina<>(items, pagina.getTotalElements());
    }

    /**
     * Regla de pertenencia (RF-004). Mejora v2 sobre el hueco de la v1: la v1
     * exigia una CAPTACION del agente (captacion.perteneceAlAgente), asi que un
     * local recien creado — que solo tiene su prospeccion inicial — no se podia
     * editar hasta captarlo. En v2 el agente es dueno si PROSPECTO o CAPTO el
     * local (misma intencion, sin el hueco). Mensaje del cable v1.
     */
    private void exigirPertenencia(long idPropiedad, Actor actor) {
        long idOrganizacion = actor.idOrganizacion();
        long idRolAgente = actor.idRolOperativo();
        boolean dueno = captaciones.existsByOrganizacionIdAndPropiedadIdAndAgenteIdAndEstadoNot(
                        idOrganizacion, idPropiedad, idRolAgente, Captacion.CERRADA)
                || prospeccionesRepo.existsByOrganizacionIdAndPropiedadIdAndAgenteId(
                        idOrganizacion, idPropiedad, idRolAgente);
        if (!dueno) {
            throw new ReglaNegocioException("Operación denegada. Este local no pertenece a tus captaciones.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FotoLocal> listarFotos(long idPropiedad) {
        validarId(idPropiedad, "El id del local");
        return fotos.findByIdPropiedadOrderByOrdenAscIdAsc(idPropiedad).stream()
                .map(f -> new FotoLocal(f.getId(), f.getClave(), f.getNombreArchivo()))
                .toList();
    }

    @Override
    @Transactional
    public FotoLocal agregarFoto(long idPropiedad, String clave, String nombreArchivo, Actor actor) {
        validarId(idPropiedad, "El id del local");
        if (!propiedades.existsByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)) {
            throw new ReglaNegocioException("El local no existe.");
        }
        long actuales = fotos.countByIdPropiedad(idPropiedad);
        if (actuales >= 6) {
            throw new ReglaNegocioException("Maximo 6 fotos por local. Elimina alguna para subir otra.");
        }
        FotoPropiedad foto = new FotoPropiedad();
        foto.setOrganizacionId(actor.idOrganizacion());
        foto.setIdPropiedad(idPropiedad);
        foto.setClave(clave);
        foto.setNombreArchivo(nombreArchivo);
        foto.setOrden((int) actuales);
        fotos.save(foto);
        return new FotoLocal(foto.getId(), foto.getClave(), foto.getNombreArchivo());
    }

    @Override
    @Transactional
    public Optional<String> eliminarFoto(long idFoto, Actor actor) {
        validarId(idFoto, "El id de la foto");
        return fotos.findById(idFoto)
                // Una foto de otra corredora se comporta como inexistente (404).
                .filter(foto -> foto.getOrganizacionId().equals(actor.idOrganizacion()))
                .map(foto -> {
                    fotos.delete(foto);
                    return foto.getClave();
                });
    }

    // ------------------------------------------------------------------
    // Validaciones del contrato (mensajes identicos a la v1).
    // ------------------------------------------------------------------

    private static void validarId(long id, String campo) {
        if (id <= 0) {
            throw new ReglaNegocioException(campo + " debe ser mayor que cero.");
        }
    }

    private static void validarPagina(int limite, int desplazamiento) {
        if (limite < 1 || limite > 100) {
            throw new ReglaNegocioException("El tamano de pagina debe estar entre 1 y 100.");
        }
        if (desplazamiento < 0) {
            throw new ReglaNegocioException("El desplazamiento de pagina no puede ser negativo.");
        }
    }

    private static String estadoResuelto(String estado) {
        if (estado == null || estado.isBlank()) {
            return Propiedad.LEGADO_DISPONIBLE;
        }
        if (!Propiedad.ESTADOS.contains(estado)) {
            throw new ReglaNegocioException("Valor invalido para estado del local: " + estado);
        }
        return estado;
    }

    private static String tipoInmuebleResuelto(String tipoInmueble) {
        String tipo = tipoInmueble == null || tipoInmueble.isBlank() ? Propiedad.TIPO_LOCAL : tipoInmueble;
        if (!Propiedad.TIPOS_INMUEBLE.contains(tipo)) {
            throw new ReglaNegocioException("Valor invalido para tipo de inmueble: " + tipoInmueble);
        }
        if (!Propiedad.TIPO_LOCAL.equals(tipo) && !Propiedad.TIPO_OFICINA.equals(tipo)) {
            throw new ReglaNegocioException(
                    "ControlLocal solo admite local u oficina como tipo de inmueble comercial.");
        }
        return tipo;
    }

    private static void validarUso(String uso) {
        if (uso != null && !uso.isBlank() && !Propiedad.USO_COMERCIAL.equals(uso)) {
            throw new ReglaNegocioException("ControlLocal solo admite inmuebles de uso comercial.");
        }
    }

    private static void validarObligatorios(DatosLocal datos) {
        texto(datos.codigoLocal(), "El codigo del local");
        texto(datos.direccion(), "La direccion");
        texto(datos.distrito(), "El distrito");
        if (datos.metraje() == null || datos.metraje().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioException("El metraje debe ser mayor que cero.");
        }
        if (datos.precioReferencial() == null || datos.precioReferencial().compareTo(BigDecimal.ZERO) < 0) {
            throw new ReglaNegocioException("El precio referencial no puede ser negativo.");
        }
        CondicionesEconomicas.moneda(datos.monedaReferencial(), "del precio referencial");
        texto(datos.rubroPermitido(), "El rubro permitido");
        if (datos.idPropietario() == null || datos.idPropietario() <= 0) {
            throw new ReglaNegocioException("El propietario del local debe ser mayor que cero.");
        }
    }

    private static void texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException(campo + " es obligatorio.");
        }
    }

    private PersonaRol rolPropietarioVigente(Long idRolPropietario) {
        return roles.findById(idRolPropietario)
                .filter(rol -> rol.getTipoRol() == TipoRol.PROPIETARIO && rol.estaVigente())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El propietario del local no existe o no tiene el rol de propietario vigente."));
    }

    // ------------------------------------------------------------------
    // Mapeo y soporte.
    // ------------------------------------------------------------------

    private void copiarCampos(DatosLocal datos, Propiedad propiedad) {
        propiedad.setCodigo(datos.codigoLocal());
        propiedad.setDireccion(datos.direccion());
        propiedad.setDistrito(datos.distrito());
        propiedad.setMetraje(datos.metraje());
        propiedad.setPrecioReferencial(datos.precioReferencial());
        propiedad.setMonedaReferencial(
                CondicionesEconomicas.moneda(datos.monedaReferencial(), "del precio referencial"));
        propiedad.setDescripcion(datos.descripcion());
        propiedad.setAmbientes(datos.ambientes());
        propiedad.setAntiguedadAnios(datos.antiguedadAnios());
        propiedad.setZonaUrbanizacion(datos.zonaUrbanizacion());
        propiedad.setGeoLat(datos.geoLat());
        propiedad.setGeoLong(datos.geoLong());
        propiedad.setFrente(datos.frente());
        propiedad.setZonificacion(datos.zonificacion());
        propiedad.setNumeroEstacionamientos(datos.numeroEstacionamientos());
        propiedad.setCuotaMantenimiento(datos.cuotaMantenimiento());
        propiedad.setInteriorUnidad(enBlancoANulo(datos.interiorUnidad()));
        propiedad.setPiso(enBlancoANulo(datos.piso()));
        propiedad.setReferenciaInterna(enBlancoANulo(datos.referenciaInterna()));
        propiedad.setNombreEdificioGaleria(enBlancoANulo(datos.nombreEdificioGaleria()));
        propiedad.asignarDetalleLocal(datos.rubroPermitido(), datos.aptoLicenciaFuncionamiento(),
                datos.cargaElectricaKw());
    }

    /**
     * Resuelve la FK al catalogo distrito desde el nombre escrito (solo Lima
     * por ahora), comparando sin acentos ni mayusculas ("Brena"/"Breña").
     * Si no esta catalogado, la FK queda NULL y el alta no se rompe (v1).
     */
    private void resolverDistrito(Propiedad propiedad) {
        String nombre = propiedad.getDistrito();
        if (nombre == null || nombre.isBlank()) {
            propiedad.setIdDistrito(null);
            return;
        }
        String objetivo = normalizar(nombre);
        propiedad.setIdDistrito(distritos.findByActivoTrueOrderByNombre().stream()
                .filter(distrito -> normalizar(distrito.getNombre()).equals(objetivo))
                .map(Distrito::getId)
                .findFirst()
                .orElse(null));
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase();
    }

    private static String normalizarTecnico(String valor) {
        if (valor == null) {
            return "";
        }
        return normalizar(valor)
                .replaceAll("\\b(nro|numero|n)\\W*o?\\s*(?=\\d)", "")
                .replaceAll("[^a-z0-9]", "")
                .replaceFirst("^(avenida|av|jiron|jr|calle|cl)", "");
    }

    private static boolean compatibleOpcional(String existente, String propuesto) {
        String izquierda = normalizarTecnico(existente);
        String derecha = normalizarTecnico(propuesto);
        return izquierda.isEmpty() || derecha.isEmpty() || izquierda.equals(derecha);
    }

    /** Mayor entre 5 m² y 5 % del área propuesta. */
    private static boolean areaAproximada(BigDecimal existente, BigDecimal propuesta) {
        if (existente == null) {
            return false;
        }
        BigDecimal tolerancia = propuesta.multiply(new BigDecimal("0.05")).max(new BigDecimal("5"));
        return existente.subtract(propuesta).abs().compareTo(tolerancia) <= 0;
    }

    private static List<String> criteriosCoincidentes(Propiedad candidato, DatosLocal datos) {
        java.util.ArrayList<String> criterios = new java.util.ArrayList<>();
        criterios.add("mismo propietario");
        criterios.add("dirección equivalente");
        if (!normalizarTecnico(datos.interiorUnidad()).isEmpty()
                && normalizarTecnico(candidato.getInteriorUnidad())
                .equals(normalizarTecnico(datos.interiorUnidad()))) {
            criterios.add("misma unidad/interior");
        }
        if (!normalizarTecnico(datos.piso()).isEmpty()
                && normalizarTecnico(candidato.getPiso()).equals(normalizarTecnico(datos.piso()))) {
            criterios.add("mismo piso");
        }
        criterios.add("metraje aproximado");
        return List.copyOf(criterios);
    }

    /** Ficha del detalle: portada real y estado de publicacion frescos (GET/PUT). */
    private FichaLocal fichaCompleta(Propiedad p) {
        String portada = fotos.findByIdPropiedadOrderByOrdenAscIdAsc(p.getId()).stream()
                .findFirst()
                .map(FotoPropiedad::getClave)
                .orElse(null);
        return ficha(p, publicaciones.codigoEstadoPublicacion(p.getId()), portada, nombrePropietario(p));
    }

    private String nombrePropietario(Propiedad p) {
        return p.getRolPropietario().getPersona().getNombresORazonSocial();
    }

    private void aplicarEstadoLegado(Propiedad propiedad, long id, String estadoLegado, Actor actor) {
        if (Propiedad.LEGADO_INACTIVO.equals(estadoLegado)) {
            transiciones.aplicar(propiedad, id, EstadoRegistroPropiedad.INACTIVO.codigo(),
                    actor, "Inactivacion del registro del local");
            transiciones.aplicarDisponibilidad(propiedad, id, DisponibilidadComercial.RETIRADO,
                    actor, "Retiro comercial por inactivacion del registro");
            return;
        }
        if (propiedad.estadoRegistroTipado() == EstadoRegistroPropiedad.INACTIVO) {
            transiciones.aplicar(propiedad, id, EstadoRegistroPropiedad.ACTIVO.codigo(),
                    actor, "Reactivacion del registro del local");
        }
        DisponibilidadComercial destino = Propiedad.LEGADO_DISPONIBLE.equals(estadoLegado)
                ? DisponibilidadComercial.DISPONIBLE : DisponibilidadComercial.RETIRADO;
        transiciones.aplicarDisponibilidad(propiedad, id, destino, actor,
                "Actualizacion de disponibilidad comercial");
    }

    private FichaLocal ficha(Propiedad p, String estadoPublicacion, String fotoPortadaClave,
                             String propietarioNombre) {
        DetalleLocalComercial detalle = p.getDetalleLocal();
        return new FichaLocal(
                p.getId(), p.getCodigo(), p.getDireccion(), p.getDistrito(), p.getMetraje(),
                p.getPrecioReferencial(), p.getMonedaReferencial(),
                detalle != null ? detalle.getRubroPermitido() : null,
                p.getDescripcion(), p.estadoLegado(),
                p.getRolPropietario() != null ? p.getRolPropietario().getId() : null,
                propietarioNombre,
                p.getTipoInmueble(), p.getUso(), p.getAmbientes(), p.getAntiguedadAnios(),
                p.getZonaUrbanizacion(), p.getGeoLat(), p.getGeoLong(), estadoPublicacion,
                p.getFrente(), p.getZonificacion(),
                detalle != null ? detalle.getAptoLicenciaFuncionamiento() : null,
                detalle != null ? detalle.getCargaElectricaKw() : null,
                p.getNumeroEstacionamientos(), p.getCuotaMantenimiento(), p.getIdDistrito(),
                Fechas.local(p.getFechaRegistro()), fotoPortadaClave,
                p.getEstadoRegistro(), p.getDisponibilidadComercial(), p.getInteriorUnidad(),
                p.getPiso(), p.getReferenciaInterna(), p.getNombreEdificioGaleria());
    }

    /** Mapea la proyeccion del listado sin tocar asociaciones de entidades. */
    private FichaLocal ficha(LocalListado p, String estadoPublicacion, String fotoPortadaClave) {
        return new FichaLocal(
                p.getId(), p.getCodigoLocal(), p.getDireccion(), p.getDistrito(), p.getMetraje(),
                p.getPrecioReferencial(), p.getMonedaReferencial(),
                p.getRubroPermitido(), p.getDescripcion(), p.getEstado(),
                p.getIdPropietario(), p.getPropietarioNombre(), p.getTipoInmueble(), p.getUso(),
                p.getAmbientes(), p.getAntiguedadAnios(), p.getZonaUrbanizacion(), p.getGeoLat(),
                p.getGeoLong(), estadoPublicacion, p.getFrente(), p.getZonificacion(),
                p.getAptoLicenciaFuncionamiento(), p.getCargaElectricaKw(),
                p.getNumeroEstacionamientos(), p.getCuotaMantenimiento(), p.getIdDistrito(),
                Fechas.local(p.getFechaRegistro()), fotoPortadaClave,
                null, null, null, null, null, null);
    }
}
