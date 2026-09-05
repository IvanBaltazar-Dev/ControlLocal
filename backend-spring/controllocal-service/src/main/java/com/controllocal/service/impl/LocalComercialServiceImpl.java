package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoRegistroPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.busqueda.ConjuntoDeCandidatos;
import com.controllocal.persistence.busqueda.CriterioBusquedaInmobiliaria;
import com.controllocal.persistence.busqueda.MotorBusquedaInmobiliaria;
import com.controllocal.persistence.query.LocalListado;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.Pagina;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.FiltrosDeListadoInmobiliario;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresGobernados;
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

    /**
     * <b>La operacion de este recurso, declarada donde se escribe.</b>
     *
     * <p>{@code /locales} es el alta heredada de la v1: un local comercial
     * <b>en alquiler</b>. No es una suposicion sobre datos ajenos —es lo que
     * este endpoint significa— y por eso se escribe aqui, con nombre, en vez
     * de dejar que la entidad lo rellene sola. La diferencia no es cosmetica:
     * un defecto en {@code PrecioPropiedad} se aplicaba tambien al camino
     * universal, donde la operacion puede perfectamente ser VENTA.
     *
     * <p>El alta universal —la que admite los siete tipos y las dos
     * operaciones— la recibe del llamante y no tiene ninguna constante como
     * esta.
     */
    private static final OperacionInmobiliaria OPERACION_DEL_ALTA = OperacionInmobiliaria.ALQUILER;

    private final PropiedadRepository propiedades;
    private final PersonaRolRepository roles;
    private final DistritoRepository distritos;
    private final FotoPropiedadRepository fotos;
    private final PrecioPropiedadRepository precios;
    private final PublicacionService publicaciones;
    private final ProspeccionService prospecciones;
    private final CaptacionRepository captaciones;
    // `prospeccionesRepo` se retiro con `exigirPertenencia` (P0): era su unico
    // lector. Una dependencia inyectada que ya no lee nadie no es inocua --
    // sugiere una relacion que el caso de uso ya no tiene.
    private final Transiciones transiciones;
    private final AlertaService alertas;

    /**
     * Las dos mitades de D-E4-3, y van juntas a proposito.
     *
     * <p>Este recurso escribia seis conceptos en columnas de {@code propiedad}
     * y los leia de las mismas columnas: una isla coherente consigo misma y
     * ciega respecto del modelo universal, que ya los guardaba como atributos
     * gobernados. Migrar solo el lector dejaria cada PUT escribiendo donde
     * nadie lee; migrar solo el escritor, al reves. Por eso entran los dos en
     * el mismo cambio.
     */
    private final LectorPorAutoridad lector;
    private final AtributosGobernados gobierno;

    /** La unica autoridad de escritura sobre la propiedad (P0). */
    private final AutoridadDePropiedad autoridad;

    /**
     * La unica busqueda sobre la cartera, compartida con el listado universal.
     *
     * <p>Este recurso <b>no</b> conserva una copia propia de la estrategia: la
     * consulta que tenia dentro se retiro entera. Si volviera a aparecer aqui
     * una busqueda por texto, {@code UnSoloMotorDeBusquedaTest} lo pararia.
     */
    private final MotorBusquedaInmobiliaria motor;

    public LocalComercialServiceImpl(PropiedadRepository propiedades,
                                     PersonaRolRepository roles,
                                     DistritoRepository distritos,
                                     FotoPropiedadRepository fotos,
                                     PrecioPropiedadRepository precios,
                                     PublicacionService publicaciones,
                                     ProspeccionService prospecciones,
                                     CaptacionRepository captaciones,
                                     Transiciones transiciones,
                                     AlertaService alertas,
                                     LectorPorAutoridad lector,
                                     AtributosGobernados gobierno,
                                     AutoridadDePropiedad autoridad,
                                     MotorBusquedaInmobiliaria motor) {
        this.motor = motor;
        this.alertas = alertas;
        this.propiedades = propiedades;
        this.roles = roles;
        this.distritos = distritos;
        this.fotos = fotos;
        this.precios = precios;
        this.publicaciones = publicaciones;
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.transiciones = transiciones;
        this.lector = lector;
        this.gobierno = gobierno;
        this.autoridad = autoridad;
    }

    /**
     * Filtro, orden, paginacion y conteo bajan a SQL; solo el enriquecimiento
     * de la pagina (portada y estado de publicacion) se resuelve aqui, y en
     * LOTE: dos consultas por pagina, no dos por fila.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Pagina<FichaLocal> listar(FiltrosLocal filtros, Actor actor) {
        // El motor decide QUE ids entran, con el tenant y todos los filtros
        // dentro y antes del LIMIT; aqui solo se carga la proyeccion de ESTE
        // recurso para esos ids. Es la misma estrategia que usa el listado
        // universal: lo que la configura es el criterio, no otra consulta.
        CriterioBusquedaInmobiliaria criterio = FiltrosDeListadoInmobiliario.deLocales(
                actor.idOrganizacion(), filtros.texto(), filtros.estado(),
                filtros.pagina(), filtros.tamano(), MAXIMO_POR_PAGINA);
        ConjuntoDeCandidatos candidatos = motor.resolver(criterio);

        List<LocalListado> filas = candidatos.vacio() ? List.of()
                : candidatos.ordenadas(
                        propiedades.buscarPorIds(actor.idOrganizacion(), candidatos.ids()),
                        LocalListado::getId);
        long total = candidatos.total();

        List<Long> ids = filas.stream().map(LocalListado::getId).toList();
        Map<Long, String> portadas = ids.isEmpty() ? Map.of() : fotos.portadas(ids).stream()
                .collect(Collectors.toMap(f -> f.getIdPropiedad(), f -> f.getClave()));
        Map<Long, String> estadosPublicacion =
                ids.isEmpty() ? Map.of() : publicaciones.codigosEstadoPublicacion(ids);

        // TERCERA consulta en lote, y por la misma razon que las dos de arriba:
        // las seis claves gobernadas ya no viajan en la proyeccion porque su
        // autoridad dejo de ser la columna (D-E4-3). Se piden para los ids de
        // ESTA pagina, nunca dentro del bucle.
        Map<Long, ValoresGobernados> gobernados = lector.gobernadosDeVarias(ids);

        return new Pagina<>(filas.stream()
                .map(p -> ficha(p, estadosPublicacion.get(p.getId()), portadas.get(p.getId()),
                        LectorPorAutoridad.de(gobernados, p.getId())))
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
        // El KPI mira EL MISMO conjunto que la lista —y ahora por construccion,
        // porque lo resuelve el mismo motor con el mismo criterio— o no
        // cuadraria con ella. El estado viaja nulo a proposito: el resumen
        // cuenta los tres cubos, no filtra por uno.
        Map<String, Long> porEstado = motor.contarPorEstadoLegado(
                FiltrosDeListadoInmobiliario.deLocales(actor.idOrganizacion(), texto, null,
                        1, 1, MAXIMO_POR_PAGINA));

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


    @Override
    @Transactional(readOnly = true)
    public Optional<FichaLocal> buscarPorId(long id, Actor actor) {
        validarId(id, "El id de local comercial");
        return propiedades.buscarFicha(actor.idOrganizacion(), id)
                .map(p -> fichaCompleta(actor, p));
    }

    @Override
    @Transactional
    public boolean desactivar(long id, Actor actor) {
        validarId(id, "El id de local comercial");
        // Con la fila TOMADA (F2.10): retirar del registro es una escritura, y
        // la autoridad de abajo tiene que comprobarse sobre el responsable que
        // seguira siendo verdad cuando esta transaccion escriba. Es la primera
        // carga de la fila en esta transaccion, que es lo que hace valido el
        // candado.
        Propiedad propiedad = propiedades.bloquearParaEscritura(actor.idOrganizacion(), id)
                .orElseThrow(() -> new ReglaNegocioException("Local no encontrado"));

        // Retirar del registro es el hecho mas irreversible de la ficha, asi
        // que lo escribe quien responde por ella (P0-1). Antes lo decidia
        // `exigirPertenencia`, un OR de tres condiciones —captacion viva,
        // prospeccion o `id_rol_incorporo`— que con dos encargos de agentes
        // distintos daba verdadero para los dos, y que ademas convertia una
        // procedencia historica en un permiso vigente.
        autoridad.exigirEdicion(actor, propiedad);

        // EstadoRegistroPropiedad, no Propiedad.LEGADO_INACTIVO: el destino de
        // esta transicion es la COLUMNA `estado_registro`, y aquella constante
        // pertenece al vocabulario D/N/I del cable legado, que es una
        // proyeccion derivada (`estadoLegado()`), no una columna. Hoy funciona
        // por casualidad —las dos valen "I"—; el dia que cualquiera de los dos
        // vocabularios cambiara una letra, esto romperia en silencio.
        transiciones.aplicar(propiedad, id, EstadoRegistroPropiedad.INACTIVO.codigo(), actor,
                "Desactivación de local por el agente");
        // Y se retira del mercado SOLO lo que estuvo en el mercado (V76).
        //
        // Esta segunda transicion era incondicional, y sobre una propiedad que
        // nunca se ofrecio escribia `disponibilidad_comercial = 'T'` (RETIRADO)
        // partiendo de NULL —`MaquinasEstado` se salta la validacion cuando el
        // origen es nulo—, dejando ademas su fila en `historial_estado`.
        // Afirmaba un hecho comercial que no ocurrio: no se puede retirar del
        // mercado algo que nunca estuvo en el. Y es irreversible, porque NULL
        // no es un codigo del vocabulario y no hay transicion de vuelta.
        if (propiedad.estaOfrecida()) {
            transiciones.aplicarDisponibilidad(propiedad, id, DisponibilidadComercial.RETIRADO,
                    actor, "Retiro comercial por desactivacion del registro");
        }
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
        Map<Long, ValoresGobernados> valores =
                lector.deVarias(actor.idOrganizacion(), pagina.getContent());
        List<FichaLocal> items = pagina.stream()
                .map(p -> ficha(p, estadosPublicacion.get(p.getId()), portadas.get(p.getId()),
                        nombrePropietario(p),
                        LectorPorAutoridad.de(valores, p.getId()),
                        // Listado: misma razon que el otro. La cartera propia
                        // del agente no pinta botones de escritura por fila.
                        null))
                .toList();
        return new Pagina<>(items, pagina.getTotalElements());
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
        // Las fotos SON la ficha del inmueble, asi que las escribe quien
        // responde por el (P0-1). Hasta V87 esto solo miraba el tenant: la
        // portada de cualquier propiedad de la corredora la ponia cualquiera.
        // Se carga la entidad en vez de preguntar `exists` porque la autoridad
        // necesita la fila, no su existencia. Y se carga TOMANDOLA (F2.10): la
        // foto es un hecho de la ficha, asi que la autoridad tiene que valer
        // tambien en el instante en que la fila de foto_propiedad se inserta,
        // no solo cuando se leyo la propiedad.
        Propiedad propiedad = propiedades
                .bloquearParaEscritura(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new ReglaNegocioException("El local no existe."));
        autoridad.exigirEdicion(actor, propiedad);

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
                    // La autoridad es la de la PROPIEDAD, no la de la foto: la
                    // foto no tiene dueno propio y llegar por su id no puede ser
                    // una puerta mas barata que llegar por el del inmueble
                    // (P0-1). Si la propiedad no aparece —imposible por la FK—
                    // se deniega, que es el lado seguro. Y se toma la fila
                    // (F2.10): borrar una foto es la escritura mas
                    // irreversible de la ficha, y tiene que decidirse sobre el
                    // responsable que seguira siendo verdad al borrarla.
                    Propiedad propiedad = propiedades
                            .bloquearParaEscritura(actor.idOrganizacion(), foto.getIdPropiedad())
                            .orElseThrow(() -> new ReglaNegocioException("El local no existe."));
                    autoridad.exigirEdicion(actor, propiedad);
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

    private static void texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException(campo + " es obligatorio.");
        }
    }

    // ------------------------------------------------------------------
    // Mapeo y soporte.
    // ------------------------------------------------------------------

    /** El numero como lo espera el enrutador, o null si no hay numero. */
    private static String texto(Number valor) {
        return valor == null ? null : valor.toString();
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

    /**
     * Ficha del detalle: portada real, estado de publicacion frescos y
     * <b>la autoridad de escritura ya resuelta</b> (GET/PUT).
     *
     * <p>Recibe el {@code Actor} entero y no solo su organizacion porque la
     * autoridad depende de <b>quien</b> mira, no solo de <b>desde donde</b>. Es
     * el cambio que arregla la ficha del encargo: su galeria decidia en Angular
     * con la regla derogada ("si eres AGENTE") y ofrecia dos botones que el
     * backend rechaza desde V87.
     */
    private FichaLocal fichaCompleta(Actor actor, Propiedad p) {
        String portada = fotos.findByIdPropiedadOrderByOrdenAscIdAsc(p.getId()).stream()
                .findFirst()
                .map(FotoPropiedad::getClave)
                .orElse(null);
        return ficha(p, publicaciones.codigoEstadoPublicacion(p.getId()), portada, nombrePropietario(p),
                lector.de(actor.idOrganizacion(), p), autoridad.responsabilidadDe(actor, p));
    }

    /**
     * El nombre del titular, o nada. Desde V76 una propiedad puede no tenerlo:
     * BROX conoce inmuebles que no gestiona. Era el unico lector de la columna
     * sin guarda —los otros ocho ya preguntaban por null—, asi que era el unico
     * que habria devuelto un 500 en vez de una ficha sin propietario.
     */
    private String nombrePropietario(Propiedad p) {
        return p.getRolPropietario() == null || p.getRolPropietario().getPersona() == null
                ? null
                : p.getRolPropietario().getPersona().getNombresORazonSocial();
    }

    /**
     * <b>El consumidor pide por clave logica; no sabe donde vive cada valor.</b>
     *
     * <p>{@code metraje} sale del agregado y los seis de {@code valores}, pero
     * esa diferencia no la decide este metodo: la decide la autoridad declarada
     * en el catalogo, y {@link LectorPorAutoridad} ya la resolvio. Si manana
     * {@code ambientes} se promoviera a estructural, aqui no cambia una linea.
     */
    private FichaLocal ficha(Propiedad p, String estadoPublicacion, String fotoPortadaClave,
                             String propietarioNombre, ValoresGobernados valores,
                             PropiedadUniversalService.Responsabilidad responsabilidad) {

        return new FichaLocal(
                p.getId(), p.getCodigo(), p.getDireccion(), p.getDistrito(), p.getMetraje(),
                p.getPrecioReferencial(), p.getMonedaReferencial(),
                valores.texto(CatalogoAtributo.CLAVE_RUBRO_PERMITIDO),
                p.getDescripcion(), p.estadoLegado(),
                p.getRolPropietario() != null ? p.getRolPropietario().getId() : null,
                propietarioNombre,
                p.getTipoInmueble(), p.getUso(),
                valores.entero(CatalogoAtributo.CLAVE_AMBIENTES),
                valores.entero(CatalogoAtributo.CLAVE_ANTIGUEDAD_ANIOS),
                p.getZonaUrbanizacion(), p.getGeoLat(), p.getGeoLong(), estadoPublicacion,
                valores.decimal(CatalogoAtributo.CLAVE_FRENTE),
                valores.texto(CatalogoAtributo.CLAVE_ZONIFICACION),
                valores.booleano(CatalogoAtributo.CLAVE_APTO_LICENCIA),
                valores.decimal(CatalogoAtributo.CLAVE_CARGA_ELECTRICA_KW),
                valores.entero(CatalogoAtributo.CLAVE_ESTACIONAMIENTOS),
                valores.decimal(CatalogoAtributo.CLAVE_CUOTA_MANTENIMIENTO), p.getIdDistrito(),
                Fechas.local(p.getFechaRegistro()), fotoPortadaClave,
                p.getEstadoRegistro(), p.getDisponibilidadComercial(), p.getInteriorUnidad(),
                p.getPiso(), p.getReferenciaInterna(), p.getNombreEdificioGaleria(),
                responsabilidad);
    }

    /**
     * Mapea la proyeccion del listado sin tocar asociaciones de entidades.
     *
     * <p>La proyeccion trae lo estructural —{@code metraje} entre ello— porque
     * eso es lo que un listado puede ordenar y filtrar en SQL; {@code valores}
     * trae lo gobernado, ya hidratado en lote para los ids de la pagina.
     */
    private FichaLocal ficha(LocalListado p, String estadoPublicacion, String fotoPortadaClave,
                             ValoresGobernados valores) {
        return new FichaLocal(
                p.getId(), p.getCodigoLocal(), p.getDireccion(), p.getDistrito(), p.getMetraje(),
                p.getPrecioReferencial(), p.getMonedaReferencial(),
                valores.texto(CatalogoAtributo.CLAVE_RUBRO_PERMITIDO), p.getDescripcion(), p.getEstado(),
                p.getIdPropietario(), p.getPropietarioNombre(), p.getTipoInmueble(), p.getUso(),
                valores.entero(CatalogoAtributo.CLAVE_AMBIENTES),
                valores.entero(CatalogoAtributo.CLAVE_ANTIGUEDAD_ANIOS),
                p.getZonaUrbanizacion(), p.getGeoLat(),
                p.getGeoLong(), estadoPublicacion,
                valores.decimal(CatalogoAtributo.CLAVE_FRENTE),
                valores.texto(CatalogoAtributo.CLAVE_ZONIFICACION),
                valores.booleano(CatalogoAtributo.CLAVE_APTO_LICENCIA),
                valores.decimal(CatalogoAtributo.CLAVE_CARGA_ELECTRICA_KW),
                valores.entero(CatalogoAtributo.CLAVE_ESTACIONAMIENTOS),
                valores.decimal(CatalogoAtributo.CLAVE_CUOTA_MANTENIMIENTO), p.getIdDistrito(),
                Fechas.local(p.getFechaRegistro()), fotoPortadaClave,
                // El listado no ofrece acciones de escritura por fila, asi que
                // no resuelve la autoridad: costaria una consulta por fila para
                // pintar nada. Ausente = el cliente no ofrece el boton.
                null, null, null, null, null, null, null);
    }
}
