package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.AsignacionResponsablePropiedad;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.OrigenIncorporacion;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.TitularidadPropiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.BorradorCapturaRepository;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.query.PropiedadListado;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.TitularidadPropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.soporte.ContratoDeEscritura;
import com.controllocal.service.soporte.ValorLogico;
import com.controllocal.service.soporte.ValoresGobernados;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ActividadDeLaPropiedad;
import com.controllocal.service.soporte.AnunciosDeLosEncargos;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.AtributosDeEncargo;
import com.controllocal.service.soporte.Comercializacion;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.ProcedenciaDelValor;
import com.controllocal.service.soporte.ValorEntrante;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.TitularParaEncargar;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * El alta, la lectura y la edicion universales (D-E4-1, D-E4-2).
 *
 * <h2>Una transaccion, nueve efectos</h2>
 * <ol>
 *   <li>la propiedad;</li>
 *   <li>su ubicacion (PostGIS la deriva por trigger, V46);</li>
 *   <li>los titulares, con sus cuotas;</li>
 *   <li>la titularidad vigente y su representante;</li>
 *   <li>los atributos gobernados, validados contra el catalogo;</li>
 *   <li>un encargo por operacion declarada;</li>
 *   <li>la condicion economica de cada encargo;</li>
 *   <li>el primer hito {@code U} de cada serie, atado a su encargo;</li>
 *   <li>el evento de dominio.</li>
 * </ol>
 *
 * <p><b>Todo o nada.</b> {@code @Transactional} no es aqui una anotacion de
 * rutina: si falla la titularidad, un atributo obligatorio, el tenant, la
 * operacion o cualquier invariante, no puede quedar media propiedad. Una
 * propiedad sin titular o sin precio no la arregla nadie despues — cuenta en
 * los listados y miente en los indicadores.
 *
 * <h2>Tres columnas viejas que se siguen escribiendo, y por que</h2>
 * {@code propiedad.id_rol_propietario}, {@code precio_referencial} y
 * {@code moneda_referencial} son NOT NULL y las lee todo el cable v2 actual.
 * Aqui se escriben como <b>proyeccion</b> de la fuente nueva —el representante
 * de la titularidad y el importe de un encargo—, con eso anotado en cada sitio.
 * Quitarlas es una tanda propia: hay 57 pantallas leyendolas.
 *
 * <h2>Lo que este servicio NO hace</h2>
 * No publica, no crea prospecciones y no abre oportunidades. El alta deja la
 * propiedad y sus encargos <b>pendientes de revision</b>, que es donde el
 * proceso dice que tienen que estar: el agente registra, el broker decide.
 */
@Service
public class PropiedadUniversalServiceImpl implements PropiedadUniversalService {

    private static final String COMANDO_REGISTRO = "REGISTRAR_PROPIEDAD";
    private static final String COMANDO_EDICION = "EDITAR_PROPIEDAD";

    private static final String EVENTO_REGISTRADA = "PROPIEDAD_REGISTRADA";
    private static final String EVENTO_EDITADA = "PROPIEDAD_EDITADA";
    private static final String EVENTO_ENCARGO = "ENCARGO_ABIERTO";
    private static final String EVENTO_PRECIO = "PRECIO_AUTORIZADO";
    /** El traspaso de autoridad es un hecho, y como tal deja evento (P0-2). */
    private static final String EVENTO_RESPONSABLE = "PROPIEDAD_RESPONSABLE_ASIGNADO";

    private final PropiedadRepository propiedades;
    private final PersonaRolRepository roles;
    private final DetalleAgenteRepository agentes;
    private final DistritoRepository distritos;
    private final TitularidadPropiedadRepository titularidades;
    private final CaptacionRepository captaciones;
    private final PrecioPropiedadRepository precios;
    private final EventoDominioRepository eventos;
    private final BorradorCapturaRepository borradores;
    private final AtributosGobernados gobierno;
    /** El enrutador del OTRO sujeto. Nunca se le pide nada de la propiedad. */
    private final AtributosDeEncargo condiciones;
    /** Conocer un inmueble no es poder venderlo: el ENCARGO si exige titular (V76). */
    private final TitularParaEncargar titularParaEncargar;
    private final LectorPorAutoridad lector;
    private final ComandosIdempotentes comandos;
    private final Documentos documentos;
    private final Transiciones transiciones;
    private final ActividadDeLaPropiedad actividad;
    private final AnunciosDeLosEncargos publicaciones;
    /** Quien puede escribir la propiedad, y quien cada encargo (P0). */
    private final AutoridadDePropiedad autoridad;

    // Las cuatro tablas de valor gobernado ya no se inyectan aqui (4.P). Este
    // caso de uso ORQUESTA -- decide que se escribe, en que orden y dentro de
    // que transaccion -- y el que ESCRIBE es el enrutador de cada sujeto, que es
    // el mismo que anota de donde salio cada valor. Mientras hubiera dos
    // escritores habria un camino por el que un dato entra sin procedencia, y
    // eso no se arregla acordandose: se arregla quitando el camino.

    public PropiedadUniversalServiceImpl(PropiedadRepository propiedades, PersonaRolRepository roles,
                                         DetalleAgenteRepository agentes, DistritoRepository distritos,
                                         TitularidadPropiedadRepository titularidades,
                                         CaptacionRepository captaciones,
                                         PrecioPropiedadRepository precios,
                                         EventoDominioRepository eventos,
                                         BorradorCapturaRepository borradores,
                                         AtributosGobernados gobierno,
                                         AtributosDeEncargo condiciones,
                                         TitularParaEncargar titularParaEncargar,
                                         LectorPorAutoridad lector,
                                         ComandosIdempotentes comandos, Documentos documentos,
                                         Transiciones transiciones,
                                         ActividadDeLaPropiedad actividad,
                                         AnunciosDeLosEncargos publicaciones,
                                         AutoridadDePropiedad autoridad) {
        this.propiedades = propiedades;
        this.roles = roles;
        this.agentes = agentes;
        this.distritos = distritos;
        this.titularidades = titularidades;
        this.captaciones = captaciones;
        this.precios = precios;
        this.eventos = eventos;
        this.borradores = borradores;
        this.gobierno = gobierno;
        this.condiciones = condiciones;
        this.titularParaEncargar = titularParaEncargar;
        this.lector = lector;
        this.comandos = comandos;
        this.documentos = documentos;
        this.transiciones = transiciones;
        this.actividad = actividad;
        this.publicaciones = publicaciones;
        this.autoridad = autoridad;
    }

    // ==================================================================
    // Alta
    // ==================================================================

    @Override
    @Transactional
    public ResultadoRegistro registrar(ComandoRegistro comando, Actor actor) {
        if (comando == null) {
            throw new ReglaNegocioException("El comando de registro es obligatorio.");
        }
        String tipoPropiedad = tipoValidado(comando.tipoPropiedad());
        Procedencia procedencia = Procedencia.oPantalla(comando.procedencia());

        // La huella se calcula sobre lo que DEFINE el comando. Si cambia
        // cualquiera de estos campos, ya no es el mismo comando y una clave
        // repetida deja de ser un reintento.
        String huella = documentos.huellaDe(new LinkedHashMap<>(Map.of(
                "tipo", tipoPropiedad,
                "direccion", texto(comando.ubicacion() == null ? null : comando.ubicacion().direccion()),
                "titulares", titularesEnHuella(comando.titulares()),
                "operaciones", operacionesEnHuella(comando.operaciones()))));

        Optional<ComandoIdempotente> yaHecho =
                comandos.buscar(actor, comando.claveIdempotencia(), COMANDO_REGISTRO, huella);
        if (yaHecho.isPresent()) {
            // El reintento recibe LO MISMO que el primer intento. Si aqui se
            // devolviera un conflicto, un canal conversacional le diria al
            // usuario que fallo algo que en realidad salio bien.
            return reconstruir(yaHecho.get());
        }

        List<OperacionSolicitada> operaciones = operacionesValidadas(comando.operaciones());
        // El `piso` del hueco `ubicacion` entra AQUI, entre los atributos, antes
        // de que se compruebe nada y mucho antes de que se escriba nada: es una
        // clave gobernada y tiene que recorrer el mismo camino que las demas.
        Map<String, ValorAtributo> valores = conElPisoGobernado(actor.idOrganizacion(),
                atributosValidados(comando.atributos()), comando.ubicacion());
        List<Titular> titulares = titularesValidados(comando.titulares());

        // Se comprueba ANTES de escribir nada: "te falta el metraje" es un
        // mensaje que se entiende, y un fallo del trigger a mitad de la
        // transaccion no lo es.
        exigirObligatorios(actor.idOrganizacion(), tipoPropiedad, valores.keySet());

        // El agente se resuelve solo si va a abrirse algun encargo: es el
        // encargo el que necesita saber quien responde por el, y registrar una
        // propiedad para prospectarla no abre ninguno. Exigirlo igualmente
        // dejaba el mensaje de error hablando de un encargo inexistente.
        DetalleAgente agente = operaciones.isEmpty() ? null : agenteDe(actor);
        Ubicacion ubicacion = ubicacionValidada(comando.ubicacion());

        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(actor.idOrganizacion());
        propiedad.setCodigo(codigoDePropiedad(actor.idOrganizacion(), comando.codigo()));
        propiedad.setTipoInmueble(tipoPropiedad);
        propiedad.setUso(usoValidado(comando.uso(), tipoPropiedad));
        propiedad.setDescripcion(comando.descripcion());
        aplicarUbicacion(propiedad, ubicacion);

        // `metraje` es NOT NULL, asi que tiene que estar puesto ANTES del
        // primer save. Lo pone el enrutador de autoridad, no este metodo: el
        // caso de uso ya no sabe que clave lo alimenta (D-E4-3, paso 4).
        //
        // Se hace aqui y se repite despues del save para los atributos porque
        // el orden lo impone la BD: la propiedad no se puede insertar sin
        // metraje, y un atributo no se puede insertar sin propiedad. Y el
        // LINAJE de estos estructurales tambien va despues, por la misma razon
        // al reves: se direcciona por el id de la propiedad, que aqui todavia
        // no existe (4.P).
        List<ValorEntrante> entrantes = entrantes(valores, procedencia);
        gobierno.aplicarEstructuralesAlAlta(actor.idOrganizacion(), propiedad, entrantes);

        // `precio_referencial` y `moneda_referencial` son NOT NULL y todo el
        // cable actual las lee. Se proyectan del encargo de ALQUILER si lo hay
        // -- la columna se llama "renta referencial" en media docena de sitios
        // -- y del de venta si solo hay venta.
        operacionDeReferencia(operaciones).ifPresent(referencia -> {
            propiedad.setPrecioReferencial(referencia.importe());
            propiedad.setMonedaReferencial(referencia.moneda());
        });

        List<PersonaRol> rolesTitulares = rolesDeTitulares(actor.idOrganizacion(), titulares);
        // La proyeccion heredada solo se escribe si hay a quien proyectar. Con
        // cero titulares esto era un `get(0)` sobre una lista vacia: la puerta
        // que quedaba detras de la validacion (V76).
        if (!titulares.isEmpty()) {
            propiedad.setRolPropietario(rolesTitulares.get(indiceDelRepresentante(titulares)));
        }
        // Como llego BROX a conocer este inmueble. Se declara al nacer y no
        // cambia despues: una propiedad observada que luego se capta siguio
        // conociendose observando el mercado.
        propiedad.incorporadaPor(origenDeclarado(comando.origen(), operaciones),
                actor.idRolOperativo());
        // Y quien responde por ella desde hoy (P0). Es lo unico que fija la
        // autoridad sin pasar por un broker, y solo puede serlo aqui: el alta
        // CREA la fila, asi que no hay responsable anterior a quien desplazar y
        // no hay nada que traspasar. Despues del alta la autoridad solo se
        // mueve por `AutoridadDePropiedad.asignar`, que exige broker y deja
        // rastro — volver a registrar una propiedad no captura la de nadie,
        // porque el alta jamas toca una fila existente.
        autoridad.fijarAlAlta(actor, propiedad);
        // Registrada y activa. La OFERTA la abre el encargo, no el alta: con
        // cero operaciones la propiedad queda en el registro maestro sin decir
        // que esta disponible, que es lo que seria falso (V75).
        propiedad.registrarSinOferta();
        propiedades.save(propiedad);

        // Y el alta del responsable queda en el expediente (V88). Va DESPUES
        // del save y no antes por la misma razon que el linaje: la fila se
        // direcciona por el id de la propiedad, que hasta el insert no existe.
        //
        // Solo aqui, y solo en un alta de verdad: este metodo CREA la fila,
        // asi que no hay forma de que se ejecute sobre una propiedad que ya
        // existia. Y si alguien la buscara, el indice parcial
        // `uq_asignacion_alta_por_propiedad` rechaza una segunda alta.
        autoridad.anotarElAlta(actor, propiedad);

        escribirTitularidades(actor, propiedad.getId(), titulares, rolesTitulares);
        // Y aqui se anota el linaje de TODO lo del alta -- lo gobernado que se
        // acaba de escribir y lo estructural que ya estaba aplicado.
        gobierno.escribirAlAlta(actor, propiedad, entrantes);

        List<Long> idsEncargos = new ArrayList<>();
        if (!operaciones.isEmpty()) {
            // Abrir un encargo es empezar una relacion comercial, y esa si
            // necesita saber de quien es el inmueble (V76).
            titularParaEncargar.exigirParaEncargo(propiedad);
        }
        for (OperacionSolicitada solicitada : operaciones) {
            idsEncargos.add(abrirEncargo(actor, propiedad, agente, solicitada, procedencia));
        }

        anotarEvento(actor, EVENTO_REGISTRADA, Propiedad.ENTIDAD_TIPO, propiedad.getId(), procedencia,
                Map.of("idPropiedad", propiedad.getId(), "tipoPropiedad", tipoPropiedad,
                        "idsEncargos", idsEncargos, "titulares", titulares.size(),
                        "operaciones", operaciones.stream().map(OperacionSolicitada::operacion).toList()));

        cerrarBorrador(actor, comando.idBorrador(), propiedad.getId());

        ResultadoRegistro resultado = new ResultadoRegistro(
                propiedad.getId(), propiedad.getCodigo(), idsEncargos, false);
        comandos.registrar(actor, comando.claveIdempotencia(), COMANDO_REGISTRO, huella,
                Propiedad.ENTIDAD_TIPO, propiedad.getId(), procedencia,
                documentos.objeto(Map.of("idPropiedad", propiedad.getId(),
                        "codigo", propiedad.getCodigo(), "idsEncargos", idsEncargos)));
        return resultado;
    }

    // ==================================================================
    // Lectura
    // ==================================================================

    // ==================================================================
    // El listado
    // ==================================================================

    /**
     * <b>La cartera, sin decidir cual de los dos precios es "el precio".</b>
     *
     * <p>Son dos consultas y no una: la primera resuelve <b>que propiedades</b>
     * entran —con todos los filtros en SQL, antes del LIMIT— y la segunda les
     * cuelga sus encargos vivos de una vez, para los ids de la pagina.
     *
     * <p>Traerlo todo junto obligaria a una de dos cosas, y las dos son falsas:
     * multiplicar la fila —la misma propiedad dos veces, una por encargo— o
     * quedarse con un encargo y llamarlo el precio de la propiedad. La segunda
     * es exactamente lo que hacia el listado heredado con
     * {@code precio_referencial}, y es lo que el modelo universal vino a
     * quitar.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<FilaPropiedad> listar(FiltrosPropiedad filtros, Actor actor) {
        List<OperacionInmobiliaria> exigidas = operacionesDelFiltro(filtros.operaciones());
        int pagina = Math.max(1, filtros.pagina());
        int tamano = Math.max(1, Math.min(100, filtros.tamano()));

        Page<PropiedadListado> encontradas = propiedades.buscarUniversal(
                actor.idOrganizacion(), enBlancoANulo(filtros.texto()),
                enBlancoANulo(filtros.estado()),
                filtros.tipoPropiedad() == null || filtros.tipoPropiedad().isBlank()
                        ? null : tipoValidado(filtros.tipoPropiedad()),
                enBlancoANulo(filtros.distrito()),
                exigidas.contains(OperacionInmobiliaria.VENTA),
                exigidas.contains(OperacionInmobiliaria.ALQUILER),
                PageRequest.of(pagina - 1, tamano));

        List<Long> ids = encontradas.getContent().stream().map(PropiedadListado::getId).toList();
        Map<Long, List<EncargoEnLista>> porPropiedad = encargosDeLaPagina(actor, ids);

        List<FilaPropiedad> filas = encontradas.getContent().stream()
                .map(fila -> new FilaPropiedad(fila.getId(), fila.getCodigo(),
                        AtributosGobernados.nombreDelTipo(fila.getTipoPropiedad()),
                        AtributosGobernados.rotuloDelTipo(fila.getTipoPropiedad()),
                        fila.getUso(), fila.getDireccion(),
                        fila.getDistrito(), fila.getMetraje(), fila.getEstado(),
                        fila.getIdPropietario(), fila.getPropietarioNombre(),
                        fila.getTitulares() == null ? 0 : fila.getTitulares(),
                        porPropiedad.getOrDefault(fila.getId(), List.of()),
                        Fechas.local(fila.getFechaRegistro())))
                .toList();
        return new Pagina<>(filas, encontradas.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public OpcionesDeFiltro opcionesDeFiltro(Actor actor) {
        return new OpcionesDeFiltro(propiedades.distritosConCartera(actor.idOrganizacion()));
    }

    /** Los encargos vivos de toda la pagina, en una consulta. */
    private Map<Long, List<EncargoEnLista>> encargosDeLaPagina(Actor actor, List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<EncargoEnLista>> porPropiedad = new LinkedHashMap<>();
        for (Captacion encargo : captaciones.encargosVivosDe(actor.idOrganizacion(), ids)) {
            CondicionEconomicaCaptacion condicion = encargo.getCondicionEconomica();
            porPropiedad.computeIfAbsent(encargo.getPropiedad().getId(), id -> new ArrayList<>())
                    .add(new EncargoEnLista(encargo.operacion().name(), encargo.estadoActual(),
                            condicion == null ? null : condicion.getImporteReferencia(),
                            condicion == null ? null : condicion.getMonedaReferencia()));
        }
        // Venta primero, siempre. El orden lo da el dominio -- es el de
        // OperacionInmobiliaria -- y no el alfabetico de los codigos, que
        // pondria la A de alquiler delante y haria leer «Alquiler + venta»
        // donde el negocio dice «Venta + alquiler».
        porPropiedad.values().forEach(encargos -> encargos.sort(
                java.util.Comparator.comparing(
                        encargo -> OperacionInmobiliaria.desde(encargo.operacion()).ordinal())));
        return porPropiedad;
    }

    /**
     * Que operaciones exige el filtro. Vacio = no filtra por operacion.
     *
     * <p>Con las dos declaradas el significado es <b>«tiene las dos vivas»</b>,
     * no «tiene alguna»: es lo que hace util el filtro «Venta y alquiler» del
     * listado, que sirve para encontrar justo esas propiedades.
     */
    private static List<OperacionInmobiliaria> operacionesDelFiltro(String declaradas) {
        if (declaradas == null || declaradas.isBlank()) {
            return List.of();
        }
        try {
            return OperacionInmobiliaria.desdeLista(declaradas);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
    }

    private static String enBlancoANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public FichaPropiedadUniversal consultar(long idPropiedad, Actor actor) {
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                // 404 y no 403 cuando el id es de otro tenant: decir "existe
                // pero no puedes" ya seria filtrar la cartera del vecino.
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));
        return ficha(actor, propiedad);
    }

    // ==================================================================
    // Edicion
    // ==================================================================

    @Override
    @Transactional
    public FichaPropiedadUniversal editar(long idPropiedad, ComandoEdicion comando, Actor actor) {
        if (comando == null) {
            throw new ReglaNegocioException("El comando de edicion es obligatorio.");
        }
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));
        Procedencia procedencia = Procedencia.oPantalla(comando.procedencia());

        Map<String, ValorAtributo> valores = conElPisoGobernado(actor.idOrganizacion(),
                atributosDeEdicion(comando.atributos()), comando.ubicacion());
        // Se calcula DESPUES de fusionar el piso: mandar `ubicacion.piso` y a la
        // vez "piso" en atributosABorrar son dos ordenes contrarias, y aqui es
        // donde se ven las dos.
        List<String> aBorrar = clavesABorrar(comando.atributosABorrar(), valores);

        // ---------------------------------------------------------------
        // LA AUTORIDAD, antes de escribir nada (P0).
        //
        // Este endpoint mezcla DOS cosas que hasta V87 viajaban bajo la misma
        // autorizacion —la de nadie: solo se comprobaba el tenant—. Se separan
        // aqui porque son dos autoridades distintas y pueden ser dos personas:
        //
        //   la FICHA FISICA (descripcion, ubicacion, titulares, atributos y
        //   retiradas)          -> el responsable de la PROPIEDAD
        //
        //   los ENCARGOS (importe, exclusividad, vigencia y condiciones)
        //                       -> el agente de CADA encargo, uno por uno,
        //                          dentro de `actualizarEncargo` y
        //                          `aplicarCondicionesDe`
        //
        // Se comprueban por separado y no con un OR, y eso importa en las dos
        // direcciones: responder por la propiedad no deja tocar el encargo
        // ajeno, y tener un encargo no deja tocar la ficha. Tambien importa lo
        // que NO se comprueba: una peticion que solo trae `operaciones` o
        // `condiciones` no pide la autoridad de la propiedad, asi que el agente
        // de un encargo lo sigue operando aunque la propiedad este FALTANTE.
        if (tocaLaFicha(comando, valores, aBorrar)) {
            autoridad.exigirEdicion(actor, propiedad);
        }

        String huella = documentos.huellaDe(new LinkedHashMap<>(Map.of(
                "idPropiedad", idPropiedad,
                "descripcion", texto(comando.descripcion()),
                "titulares", titularesEnHuella(comando.titulares()),
                "atributos", atributosEnHuella(comando.atributos()),
                "borrados", String.join(",", aBorrar),
                "operaciones", operacionesEnHuella(comando.operaciones()),
                // Sin esto, dos ediciones que solo se diferencian en las
                // condiciones de un encargo tendrian la misma huella y la
                // segunda se descartaria como repetida (V73).
                "condiciones", condicionesEnHuella(comando.condiciones()))));
        Optional<ComandoIdempotente> yaHecho =
                comandos.buscar(actor, comando.claveIdempotencia(), COMANDO_EDICION, huella);
        if (yaHecho.isPresent()) {
            return ficha(actor, propiedad);
        }

        if (comando.descripcion() != null) {
            propiedad.setDescripcion(comando.descripcion());
        }
        if (comando.ubicacion() != null) {
            aplicarUbicacion(propiedad, ubicacionDeEdicion(comando.ubicacion()));
        }
        if (comando.titulares() != null) {
            conciliarTitularidades(actor, propiedad, titularesValidados(comando.titulares()));
        }
        // La condicion mira el MAPA y no `comando.atributos()`: el piso puede
        // venir solo dentro de `ubicacion`, y con la comprobacion sobre el
        // comando se quedaria sin escribir.
        if (!valores.isEmpty()) {
            actualizarAtributos(actor, propiedad, valores, procedencia);
        }
        // Despues de los valores: si la misma peticion cambia unas claves y
        // retira otras, el orden no puede depender de como se recorra el mapa.
        retirarValores(actor, propiedad, aBorrar, procedencia);
        if (comando.operaciones() != null) {
            for (OperacionSolicitada solicitada : operacionesValidadas(comando.operaciones())) {
                actualizarEncargo(actor, propiedad, solicitada, procedencia);
            }
        }
        // Y las condiciones de cada encargo, en su bloque (Corte 0C). Van
        // despues de las operaciones porque una operacion recien declarada abre
        // el encargo al que estas condiciones pueden pertenecer.
        aplicarCondiciones(actor, propiedad, comando.condiciones(), procedencia);
        propiedades.save(propiedad);

        anotarEvento(actor, EVENTO_EDITADA, Propiedad.ENTIDAD_TIPO, propiedad.getId(), procedencia,
                Map.of("idPropiedad", propiedad.getId()));
        comandos.registrar(actor, comando.claveIdempotencia(), COMANDO_EDICION, huella,
                Propiedad.ENTIDAD_TIPO, propiedad.getId(), procedencia,
                documentos.objeto(Map.of("idPropiedad", propiedad.getId())));

        return ficha(actor, propiedad);
    }

    // ==================================================================
    // El traspaso del responsable (P0-2)
    // ==================================================================

    /**
     * <b>Cambia quien responde por la propiedad, y lo deja escrito.</b>
     *
     * <p>Este caso de uso <b>solo</b> hace eso. No toca la ficha, ni los
     * encargos, ni la disponibilidad: si un dia hiciera falta que un traspaso
     * arrastre algo mas, sera otra decision y otro metodo — mezclarlo aqui
     * convertiria un acto de gobierno auditable en un cambio de datos con
     * efectos que nadie pidio.
     *
     * <p>El evento de dominio se anota como cualquier otro hecho y en la misma
     * transaccion, ademas de la fila del expediente: la fila es el rastro
     * dirigido —"de quien a quien"— y el evento es el que ve la auditoria
     * transversal.
     */
    @Override
    @Transactional
    public TraspasoDeResponsable asignarResponsable(long idPropiedad, long idRolAgente,
                                                    String motivo, Actor actor) {
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));

        AsignacionResponsablePropiedad fila =
                autoridad.asignar(actor, propiedad, idRolAgente, motivo);
        propiedades.save(propiedad);

        anotarEvento(actor, EVENTO_RESPONSABLE, Propiedad.ENTIDAD_TIPO, propiedad.getId(),
                Procedencia.oPantalla(null),
                fila.getIdRolResponsableAnterior() == null
                        ? Map.of("idPropiedad", propiedad.getId(),
                                 "responsableNuevo", fila.getIdRolResponsableNuevo())
                        : Map.of("idPropiedad", propiedad.getId(),
                                 "responsableAnterior", fila.getIdRolResponsableAnterior(),
                                 "responsableNuevo", fila.getIdRolResponsableNuevo()));
        return traspaso(fila);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TraspasoDeResponsable> traspasosDe(long idPropiedad, Actor actor) {
        // 1. FRONTERA DE TENANT, siempre primero (C1). Un id de otra corredora
        //    se comporta como inexistente: 404 y no 403, porque un 403 ya
        //    confirmaria que esa propiedad existe en alguna parte.
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));
        // 2. Y DESPUES la banda y el alcance (C2). El expediente es superficie
        //    de gobierno: lo leen el broker que supervisa a quien responde y el
        //    gobierno del tenant. El AGENTE no, ni siquiera el responsable
        //    vigente -- que sabe que responde el, pero no hereda a los
        //    anteriores ni los motivos por los que la propiedad cambio de manos.
        autoridad.exigirLecturaDelExpediente(actor, propiedad);
        return autoridad.historial(actor.idOrganizacion(), idPropiedad).stream()
                .map(this::traspaso)
                .toList();
    }

    private TraspasoDeResponsable traspaso(AsignacionResponsablePropiedad fila) {
        return new TraspasoDeResponsable(fila.getId(), fila.getIdPropiedad(),
                fila.getIdRolResponsableAnterior(), nombreDelAgente(fila.getIdRolResponsableAnterior()),
                fila.getIdRolResponsableNuevo(), nombreDelAgente(fila.getIdRolResponsableNuevo()),
                fila.getIdPersonaActor(), fila.getTipoRolActor(), fila.getOrigen(),
                fila.getMotivo(),
                Fechas.local(fila.getFechaAsignacion()));
    }

    private String nombreDelAgente(Long idRol) {
        return idRol == null ? null
                : agentes.findById(idRol).map(a -> nombreDe(a.getRol())).orElse(null);
    }

    /**
     * <b>¿Esta peticion escribe algun hecho de la PROPIEDAD?</b>
     *
     * <p>Mira los cinco huecos que pertenecen al inmueble, y ninguno de los dos
     * que pertenecen a sus encargos. Se pregunta sobre el <b>mapa ya fusionado</b>
     * y sobre la lista ya resuelta de retiradas, no sobre {@code comando
     * .atributos()}: el piso puede llegar solo dentro de {@code ubicacion}, y
     * mirando el comando en crudo una edicion que solo cambia el piso pasaria
     * por "no toca la ficha" — que es exactamente la puerta de al lado que este
     * P0 viene a cerrar.
     */
    private static boolean tocaLaFicha(ComandoEdicion comando, Map<String, ValorAtributo> valores,
                                       List<String> aBorrar) {
        return comando.descripcion() != null
                || comando.ubicacion() != null
                || comando.titulares() != null
                || !valores.isEmpty()
                || !aBorrar.isEmpty();
    }

    // ==================================================================
    // Escritura, pieza a pieza
    // ==================================================================

    /**
     * <b>Las condiciones de cada encargo, cada una en el suyo</b> (Corte 0C).
     *
     * <p>La regla de bloques de 0A, un nivel mas adentro y sin excepciones:
     *
     * <pre>
     *   condiciones == null            -> no se toca NINGUN encargo
     *   bloque de un encargo ausente   -> ese encargo queda como estaba
     *   bloque presente                -> solo toca a SU idEncargo
     * </pre>
     *
     * <p>Lo tercero es lo que hace falta vigilar. Guardar el bloque de la venta
     * no puede vaciar la garantia del alquiler, ni completarla por defecto, ni
     * copiar en el nuevo alquiler lo que se pacto en el anterior. Como el valor
     * cuelga de {@code id_captacion} y no de la operacion, aqui basta con no
     * salirse del encargo que el bloque nombra -- y eso es exactamente lo que
     * comprueba {@code SujetoDelDatoIntegrationTest}.
     */
    private void aplicarCondiciones(Actor actor, Propiedad propiedad,
                                    List<CondicionesDeEncargo> bloques, Procedencia procedencia) {
        if (bloques == null || bloques.isEmpty()) {
            return;
        }
        Set<Long> repetidos = new java.util.HashSet<>();
        for (CondicionesDeEncargo bloque : bloques) {
            if (bloque == null || bloque.idEncargo() == null) {
                throw new ReglaNegocioException(
                        "Unas condiciones comerciales llegaron sin decir de que encargo son. "
                                + "Con una venta y un alquiler abiertos a la vez no hay forma de "
                                + "adivinarlo, y adivinarlo seria escribir en el equivocado.");
            }
            if (!repetidos.add(bloque.idEncargo())) {
                throw new ReglaNegocioException(
                        "El encargo " + bloque.idEncargo() + " llego dos veces en la misma "
                                + "peticion. Cual de los dos bloques gana no lo puede decidir el "
                                + "Core: seria una regla inventada que el cliente no sabe.");
            }
            aplicarCondicionesDe(actor, propiedad, bloque, procedencia);
        }
    }

    private void aplicarCondicionesDe(Actor actor, Propiedad propiedad,
                                      CondicionesDeEncargo bloque, Procedencia procedencia) {
        // El encargo tiene que ser de ESTA propiedad y de ESTE tenant. Sin la
        // comprobacion, un id ajeno escribiria condiciones en la cartera de otra
        // corredora -- y la FK compuesta lo dejaria pasar, porque la organizacion
        // que viaja es la del actor.
        Captacion encargo = captaciones.findById(bloque.idEncargo())
                .filter(c -> c.getOrganizacionId() == actor.idOrganizacion())
                .filter(c -> c.getPropiedad() != null
                        && Objects.equals(c.getPropiedad().getId(), propiedad.getId()))
                .orElseThrow(() -> new NoEncontradoException("Encargo"));

        // Y tiene que ser SUYO (P0-4). Las condiciones comerciales gobernadas
        // —garantia, adelanto, plazo— son datos del encargo tanto como el
        // importe, y sin esto el agente del alquiler escribia las de la venta
        // ajena con solo nombrar su id.
        autoridad.exigirEdicionDelEncargo(actor, encargo);

        Comercializacion donde = Comercializacion.de(encargo, propiedad);
        Map<String, ValorAtributo> valores = atributosDeEdicion(bloque.atributos());
        List<String> aBorrar = clavesABorrar(bloque.atributosABorrar(), valores);

        if (bloque.atributos() != null) {
            for (ValorEntrante entrante : entrantes(valores, procedencia)) {
                condiciones.escribir(actor, donde, entrante);
            }
        }
        // Despues de los valores, igual que en la propiedad: si la misma
        // peticion cambia unas claves y retira otras, el resultado no puede
        // depender de como se recorra el mapa.
        for (String clave : aBorrar) {
            if (!condiciones.retirar(actor, donde, clave,
                    ProcedenciaDelValor.delActo(procedencia)).gobernada()) {
                throw new ReglaNegocioException(
                        "El atributo \"" + clave + "\" no esta en el catalogo, asi que no hay "
                                + "nada que retirar del encargo " + encargo.getCodigoCaptacion()
                                + ".");
            }
        }
    }

    private void escribirTitularidades(Actor actor, Long idPropiedad, List<Titular> titulares,
                                       List<PersonaRol> rolesTitulares) {
        int representante = indiceDelRepresentante(titulares);
        for (int i = 0; i < titulares.size(); i++) {
            TitularidadPropiedad titularidad = new TitularidadPropiedad();
            titularidad.setOrganizacionId(actor.idOrganizacion());
            titularidad.setIdPropiedad(idPropiedad);
            titularidad.setRolPropietario(rolesTitulares.get(i));
            titularidad.setCuota(titulares.get(i).cuota());
            titularidad.setEsRepresentante(i == representante);
            titularidad.setVigenteDesde(LocalDate.now());
            titularidades.save(titularidad);
        }
    }

    /**
     * La titularidad nueva sustituye a la anterior <b>cerrandola</b>, no
     * borrandola: una venta no borra al dueno de antes, le pone fecha de fin.
     * Sin eso, el historico de propiedad se pierde en el primer cierre.
     */
    /**
     * <b>Reemplaza solo si de verdad cambio algo.</b>
     *
     * <p>Devolver la titularidad exactamente como la ficha la publico —que es
     * lo que hace cualquier pantalla que carga el formulario del Core y lo
     * guarda— cerraba la vigente y abria otra con fecha de hoy. Los anios que
     * el dueno llevaba con la propiedad desaparecian en un guardado que no
     * cambio nada, y el gate de conservacion lo veia en los siete tipos:
     * <pre>
     *   titular.43.desde: "2022-08-20"  ->  "2026-08-20"
     * </pre>
     *
     * <p>Reemplazar una titularidad por si misma no es una transmision. La
     * comparacion es por <b>identidad y contenido</b> —quien, con que cuota, y
     * quien representa— y no por el orden en que llego la lista.
     */
    private void conciliarTitularidades(Actor actor, Propiedad propiedad, List<Titular> titulares) {
        if (mismaTitularidad(titularidades.vigentesDe(propiedad.getId()), titulares)) {
            return;
        }
        reemplazarTitularidades(actor, propiedad, titulares);
    }

    /** Mismo conjunto de titulares, con la misma cuota y el mismo representante. */
    private static boolean mismaTitularidad(List<TitularidadPropiedad> vigentes,
                                            List<Titular> titulares) {
        if (vigentes.size() != titulares.size()) {
            return false;
        }
        Map<Long, String> actual = new LinkedHashMap<>();
        for (TitularidadPropiedad titularidad : vigentes) {
            actual.put(titularidad.getRolPropietario().getId(),
                    huellaDeTitular(titularidad.getCuota(), titularidad.isEsRepresentante()));
        }
        // El representante se compara YA RESUELTO: si nadie lo declara manda el
        // primero, y comparar la bandera cruda haria distintas dos listas que
        // acabarian escribiendo lo mismo.
        int representante = indiceDelRepresentante(titulares);
        for (int i = 0; i < titulares.size(); i++) {
            String esperado = huellaDeTitular(titulares.get(i).cuota(), i == representante);
            if (!esperado.equals(actual.get(titulares.get(i).idRolPropietario()))) {
                return false;
            }
        }
        return true;
    }

    private static String huellaDeTitular(BigDecimal cuota, boolean representante) {
        return (cuota == null ? "-" : cuota.stripTrailingZeros().toPlainString())
                + "/" + representante;
    }

    private void reemplazarTitularidades(Actor actor, Propiedad propiedad, List<Titular> titulares) {
        List<TitularidadPropiedad> vigentes = titularidades.vigentesDe(propiedad.getId());
        for (TitularidadPropiedad anterior : vigentes) {
            anterior.cerrar(LocalDate.now(), "Reemplazo de titularidad");
            titularidades.save(anterior);
        }
        // El flush importa: el indice unico de representante vigente y el
        // constraint de cuotas miran el conjunto, y las nuevas no pueden
        // entrar mientras las viejas sigan figurando vigentes.
        titularidades.flush();

        List<PersonaRol> rolesTitulares = rolesDeTitulares(actor.idOrganizacion(), titulares);
        escribirTitularidades(actor, propiedad.getId(), titulares, rolesTitulares);
        if (!titulares.isEmpty()) {
            propiedad.setRolPropietario(rolesTitulares.get(indiceDelRepresentante(titulares)));
        }
    }

    /**
     * <b>Cada valor con SU procedencia, y el acto compartido</b> (4.P).
     *
     * <p>La {@link Procedencia} del acto —canal, agente, conversacion, turno— es
     * la misma para toda la peticion: es lo que el Core sabe siempre. Lo que
     * cambia valor a valor es la <b>naturaleza</b>, y por eso se compone aqui,
     * una por clave, en vez de estamparse una sola vez sobre el guardado entero.
     *
     * <p>Es la conversion que hace real la regla que abrio el microcorte: un
     * mismo {@code PUT} puede traer {@code tipo_acceso} observado en una visita,
     * {@code zonificacion} leida de un certificado y {@code vigilancia} dicha por
     * el propietario, y las tres salen de aqui con la suya.
     */
    private static List<ValorEntrante> entrantes(Map<String, ValorAtributo> valores,
                                                 Procedencia acto) {
        List<ValorEntrante> entrantes = new ArrayList<>();
        valores.forEach((clave, valor) -> entrantes.add(entrante(valor, acto)));
        return entrantes;
    }

    private static ValorEntrante entrante(ValorAtributo valor, Procedencia acto) {
        return new ValorEntrante(valor.clave(), valor.valor(), valor.moneda(), valor.valores(),
                new ProcedenciaDelValor(acto, valor.naturaleza(), valor.observadoEn(),
                        valor.evidenciaRef(), valor.confianza()));
    }

    /**
     * Los valores de una edicion. Igual que en el alta, mas una regla propia:
     * <b>un valor en blanco se rechaza</b>.
     *
     * <p>Porque {@code ""} es ambiguo y este corte no adivina. Puede ser "lo
     * quiero quitar", puede ser un campo que la pantalla no relleno, y puede
     * ser un espacio de mas. Darle a los tres el mismo destino es reinterpretar
     * lo que el usuario hizo, que es la fuga que 0A contiene. Retirar un valor
     * tiene su via, se llama por su nombre y viaja aparte.
     */
    private static Map<String, ValorAtributo> atributosDeEdicion(List<ValorAtributo> valores) {
        Map<String, ValorAtributo> porClave = atributosValidados(valores);
        porClave.forEach((clave, valor) -> {
            if (valor.valores() == null && (valor.valor() == null || valor.valor().isBlank())) {
                throw new ReglaNegocioException(
                        "El atributo \"" + clave + "\" llego vacio. Un valor en blanco no es una "
                                + "forma de borrar: si lo quieres retirar, nombralo en "
                                + "\"atributosABorrar\".");
            }
        });
        return porClave;
    }

    /**
     * Las claves a retirar, comprobadas contra lo que la misma peticion cambia.
     *
     * <p>Una clave que llega <b>a la vez</b> con valor y en la lista de borrado
     * son dos intenciones contrarias en el mismo comando. No se elige entre
     * ellas por precedencia —cualquier orden que se escoja seria una regla
     * inventada que el cliente no sabe— y no se aplica ninguna: se avisa.
     */
    private static List<String> clavesABorrar(List<String> claves, Map<String, ValorAtributo> valores) {
        if (claves == null || claves.isEmpty()) {
            return List.of();
        }
        List<String> limpias = new ArrayList<>();
        for (String clave : claves) {
            if (clave == null || clave.isBlank()) {
                throw new ReglaNegocioException("\"atributosABorrar\" trae una clave vacia.");
            }
            String limpia = clave.trim();
            if (valores.containsKey(limpia)) {
                throw new ReglaNegocioException(
                        "El atributo \"" + limpia + "\" llego con valor y a la vez en "
                                + "\"atributosABorrar\". Son dos ordenes contrarias: manda una.");
            }
            if (!limpias.contains(limpia)) {
                limpias.add(limpia);
            }
        }
        return limpias;
    }

    /**
     * <b>Retira cada clave por su autoridad</b>, igual que se lee y se escribe.
     *
     * <p>Quien pide el borrado manda un <b>nombre logico</b> y nada mas. No
     * dice —ni sabe— si {@code piso} vive hoy en {@code atributo_propiedad} o
     * en una columna del agregado, ni si manana se mueve. Primero se prueba el
     * catalogo, que es donde se declara la autoridad de las claves gobernadas;
     * lo que no esta ahi se busca entre los campos logicos que la propia ficha
     * publica. Lo que no encaja en ninguno de los dos se rechaza <b>con su
     * nombre</b>, porque un borrado que no borra nada y calla es peor que un
     * error.
     */
    private void retirarValores(Actor actor, Propiedad propiedad, List<String> claves,
                                Procedencia procedencia) {
        for (String clave : claves) {
            // El enrutador devuelve LO QUE QUITO, y no un si/no (4.P). El
            // borrado es fisico —la fila se va, y con ella sus opciones—, asi
            // que ese es el ultimo instante en que ese dato existe: se lee, se
            // anota en el linaje y la clave queda con historia y sin valor.
            if (gobierno.retirar(actor, propiedad, clave,
                    ProcedenciaDelValor.delActo(procedencia)).gobernada()) {
                continue;
            }
            switch (clave) {
                case "descripcion" -> propiedad.setDescripcion(null);
                case "zonaUrbanizacion" -> propiedad.setZonaUrbanizacion(null);
                case "interiorUnidad" -> propiedad.setInteriorUnidad(null);
                case "referenciaInterna" -> propiedad.setReferenciaInterna(null);
                case "nombreEdificioGaleria" -> propiedad.setNombreEdificioGaleria(null);
                case "latitud" -> propiedad.setGeoLat(null);
                case "longitud" -> propiedad.setGeoLong(null);
                case "direccion", "distrito" -> throw new ReglaNegocioException(
                        "\"" + clave + "\" no se puede retirar: toda propiedad esta en algun "
                                + "sitio. Corrigelo mandando el valor nuevo.");
                default -> throw new ReglaNegocioException(
                        "No se puede retirar \"" + clave + "\": no es una clave del catalogo ni "
                                + "un campo de la propiedad.");
            }
        }
    }

    /**
     * Los valores de una edicion, cada uno por su autoridad y con su linaje.
     *
     * <p>El mismo enrutador que el alta: si la clave es estructural aplica sobre
     * la propiedad y NO deja atributo; si es gobernada, actualiza la que hay o
     * crea la que falta; si es multivalor, sustituye el conjunto. Arreglar solo
     * el alta dejaria la fuga abierta en la operacion mas frecuente de las dos —
     * y desde 4.P «la fuga» es tambien la de la procedencia.
     */
    private void actualizarAtributos(Actor actor, Propiedad propiedad,
                                     Map<String, ValorAtributo> valores, Procedencia procedencia) {
        for (ValorEntrante entrante : entrantes(valores, procedencia)) {
            gobierno.escribirEnEdicion(actor, propiedad, entrante);
        }
    }

    /**
     * Un encargo con su condicion economica y el primer hito de su serie.
     *
     * <p>Nace PENDIENTE de revision, que es lo que el proceso dice: el agente
     * registra, el broker decide. Y la operacion viaja explicita hasta el
     * hito — {@code tg_precio_operacion_encargo} (V49) comprueba en la base
     * que las dos coinciden.
     */
    private Long abrirEncargo(Actor actor, Propiedad propiedad, DetalleAgente agente,
                              OperacionSolicitada solicitada, Procedencia procedencia) {
        OperacionInmobiliaria operacion = OperacionInmobiliaria.desde(solicitada.operacion());

        CondicionEconomicaCaptacion condicion = new CondicionEconomicaCaptacion();
        condicion.setOrganizacionId(actor.idOrganizacion());
        condicion.setTipoOperacion(operacion.codigo());
        condicion.setImporteReferencia(solicitada.importe());
        condicion.setMonedaReferencia(solicitada.moneda());
        condicion.setTipoComision(solicitada.tipoComision() != null
                ? solicitada.tipoComision() : CondicionEconomicaCaptacion.PORCENTAJE);
        condicion.setBaseCalculo(solicitada.baseCalculo() != null
                ? solicitada.baseCalculo()
                : (operacion == OperacionInmobiliaria.VENTA
                        ? CondicionEconomicaCaptacion.PRECIO_VENTA
                        : CondicionEconomicaCaptacion.RENTA_MENSUAL));
        condicion.setValorComision(solicitada.valorComision() != null
                ? solicitada.valorComision() : BigDecimal.ZERO);
        condicion.setMonedaComision(solicitada.moneda());
        condicion.setTratamientoIgv(solicitada.tratamientoIgv() != null
                ? solicitada.tratamientoIgv() : CondicionEconomicaCaptacion.IGV_NO_APLICA);
        // `ck_condicion_sin_comision` exige que una comision de CERO diga por
        // que. La regla es buena y no se rodea: en el alta la comision puede no
        // estar pactada todavia -- el encargo nace PENDIENTE y solo puede
        // ACTIVARSE con su condicion completa (`ck_captacion_activa_completa`)
        // --, asi que el motivo se declara con esas palabras en vez de colar un
        // cero mudo que nadie sabria interpretar seis meses despues.
        if (condicion.getValorComision().signum() == 0) {
            condicion.setMotivoSinComision(
                    "Comision no pactada al registrar la propiedad; se define antes de activar el encargo.");
        }

        // La duracion por defecto del encargo es una REGLA de negocio y vive en
        // un solo sitio (`PoliticaComercial.ENCARGO`). Escribir aqui seis meses
        // a mano crearia una segunda copia que divergiria en silencio el dia
        // que la corredora cambie el plazo — que es justo lo que el gate de la
        // politica unica existe para impedir.
        LocalDate inicio = solicitada.inicioEncargo() != null ? solicitada.inicioEncargo() : LocalDate.now();
        LocalDate fin = solicitada.finEncargo() != null
                ? solicitada.finEncargo() : PoliticaComercial.finDelEncargo(inicio);

        Captacion encargo = new Captacion();
        encargo.setOrganizacionId(actor.idOrganizacion());
        encargo.setCodigoCaptacion(codigoDeEncargo(actor.idOrganizacion()));
        encargo.setFechaCaptacion(LocalDate.now());
        encargo.setFechaInicioVigencia(inicio);
        encargo.setFechaFinVigencia(fin);
        encargo.setCondicionEconomica(condicion);
        encargo.setMotivoOperacion(operacion.codigo());
        encargo.setExclusividad(solicitada.exclusividad() != null ? solicitada.exclusividad() : Boolean.FALSE);
        encargo.setPropiedad(propiedad);
        encargo.setAgente(agente);
        transiciones.iniciar(encargo, Captacion.PENDIENTE_REVISION);
        captaciones.save(encargo);
        // Abrir el encargo es lo que pone la propiedad EN OFERTA (V75). Antes lo
        // hacia el alta de forma incondicional, y por eso una propiedad que solo
        // se prospectaba nacia diciendo «disponible».
        entrarEnOferta(actor, propiedad, "Entra al mercado: se abrio el encargo "
                + encargo.getCodigoCaptacion() + ".");

        // Primer hito 'U' (autorizado) de ESTA serie. Va atado al encargo: es
        // lo que permite que la venta y el alquiler de la misma propiedad
        // tengan historicos separados de verdad y no una lista mezclada en la
        // que 180 000 y 2 900 solo se distinguen por su magnitud.
        precios.save(PrecioPropiedad.hito(actor.idOrganizacion(), propiedad.getId(), operacion,
                        PrecioPropiedad.HITO_AUTORIZADO, solicitada.moneda(), solicitada.importe(),
                        LocalDate.now())
                .delEncargo(encargo.getId()));

        // Y las condiciones que se pactaron al abrirlo, si vinieron (V73). Van
        // aqui y no en el bloque de atributos de la propiedad: en el alta el
        // encargo todavia no tiene id, asi que la unica forma de decir a cual
        // pertenece cada condicion es que viajen DENTRO de su operacion.
        if (solicitada.condiciones() != null) {
            Comercializacion donde = new Comercializacion(encargo.getId(),
                    propiedad.getTipoInmueble(), operacion.codigo());
            for (ValorEntrante entrante
                    : entrantes(atributosValidados(solicitada.condiciones()), procedencia)) {
                condiciones.escribir(actor, donde, entrante);
            }
        }

        anotarEvento(actor, EVENTO_ENCARGO, "CAPTACION", encargo.getId(), procedencia,
                Map.of("idPropiedad", propiedad.getId(), "idCaptacion", encargo.getId(),
                        "operacion", operacion.name(), "importe", solicitada.importe(),
                        "moneda", solicitada.moneda()));
        return encargo.getId();
    }

    /**
     * <b>La propiedad entra al mercado</b>, y queda dicho en su expediente.
     *
     * <p>Va por {@code Transiciones} y no por el setter del agregado porque
     * {@code disponibilidad_comercial} tiene historial desde V20: «entra al
     * mercado» es el hecho comercial mas importante de una propiedad y no puede
     * ser el unico que no deje fila. Sin esto el expediente empezaria en
     * DISPONIBLE sin decir como llego.
     *
     * <p>Y no pisa lo ya declarado: si la propiedad esta ALQUILADA, RESERVADA o
     * RETIRADA, abrir otro encargo no la vuelve a poner disponible.
     */
    private void entrarEnOferta(Actor actor, Propiedad propiedad, String motivo) {
        if (propiedad.estaOfrecida()) {
            return;
        }
        transiciones.aplicarDisponibilidad(propiedad, propiedad.getId(),
                EstadosDominio.DisponibilidadComercial.DISPONIBLE, actor, motivo);
    }

    /**
     * Cambiar el importe de una operacion <b>anade</b> un hito. No sobrescribe
     * nada: el precio de salida es el que mide cuanto cedio el titular hasta el
     * cierre, y no se reconstruye desde ninguna otra tabla.
     */
    private void actualizarEncargo(Actor actor, Propiedad propiedad, OperacionSolicitada solicitada,
                                   Procedencia procedencia) {
        OperacionInmobiliaria operacion = OperacionInmobiliaria.desde(solicitada.operacion());
        Captacion encargo = captaciones
                .encargoVivoDe(actor.idOrganizacion(), propiedad.getId(), operacion.codigo())
                .orElseThrow(() -> new ReglaNegocioException(
                        "Esta propiedad no tiene ningun encargo vivo de " + operacion.name()
                                + ". Abrelo antes de cambiar su " + operacion.nombreDelImporte() + "."));

        // El encargo lo edita SU agente (P0-4). Va antes de tocar la condicion
        // economica a proposito: el efecto que hay que impedir no es solo el
        // cambio de importe, es el hito `U` que ese cambio anade a la serie
        // economica de un encargo que no es del actor.
        autoridad.exigirEdicionDelEncargo(actor, encargo);

        CondicionEconomicaCaptacion condicion = encargo.getCondicionEconomica();
        boolean cambioElImporte = condicion == null
                || condicion.getImporteReferencia() == null
                || condicion.getImporteReferencia().compareTo(solicitada.importe()) != 0
                || !solicitada.moneda().equals(condicion.getMonedaReferencia());

        if (condicion != null) {
            condicion.setImporteReferencia(solicitada.importe());
            condicion.setMonedaReferencia(solicitada.moneda());
        }
        if (solicitada.exclusividad() != null) {
            encargo.setExclusividad(solicitada.exclusividad());
        }
        if (solicitada.inicioEncargo() != null) {
            encargo.setFechaInicioVigencia(solicitada.inicioEncargo());
        }
        if (solicitada.finEncargo() != null) {
            encargo.setFechaFinVigencia(solicitada.finEncargo());
        }
        captaciones.save(encargo);

        if (cambioElImporte) {
            precios.save(PrecioPropiedad.hito(actor.idOrganizacion(), propiedad.getId(), operacion,
                            PrecioPropiedad.HITO_AUTORIZADO, solicitada.moneda(), solicitada.importe(),
                            LocalDate.now())
                    .delEncargo(encargo.getId()));
            anotarEvento(actor, EVENTO_PRECIO, "CAPTACION", encargo.getId(), procedencia,
                    Map.of("idPropiedad", propiedad.getId(), "idCaptacion", encargo.getId(),
                            "operacion", operacion.name(), "importe", solicitada.importe(),
                            "moneda", solicitada.moneda()));
        }

        // La columna espejo sigue lo que lee el cable: la renta si la hay.
        if (operacion == operacionProyectada(actor, propiedad)) {
            propiedad.setPrecioReferencial(solicitada.importe());
            propiedad.setMonedaReferencial(solicitada.moneda());
        }
    }

    // ==================================================================
    // Lectura, pieza a pieza
    // ==================================================================

    private FichaPropiedadUniversal ficha(Actor actor, Propiedad propiedad) {
        long idOrganizacion = actor.idOrganizacion();
        long idPropiedad = propiedad.getId();
        String tipo = propiedad.getTipoInmueble();

        List<TitularFicha> titulares = titularidades.vigentesDe(idPropiedad).stream()
                .map(titularidad -> new TitularFicha(
                        titularidad.getRolPropietario().getId(),
                        nombreDe(titularidad.getRolPropietario()),
                        titularidad.getCuota(),
                        titularidad.isEsRepresentante(),
                        titularidad.getVigenteDesde()))
                .toList();

        // El LECTOR tambien enruta por autoridad, igual que el escritor.
        //
        // Sin esto, mover `metraje` a su campo canonico lo saco de la lista de
        // atributos y con ella de la respuesta del API: el dato seguia guardado
        // y dejaba de poder leerse. Un cliente no tiene por que enterarse de
        // DONDE se guarda cada valor — sigue viendo `metraje_total` entre los
        // atributos aunque su fila ya no exista (D-E4-3).
        // Y lo hace por el MISMO lector que todos los demas consumidores.
        //
        // Hasta el Corte 0B la ficha tenia su propia copia del recorrido —leia
        // `atributo_propiedad` a mano y formateaba el valor ella misma—, y por
        // eso un IMPORTE llegaba aqui sin su moneda y un multivalor no llegaba
        // en absoluto: el lector sabia leerlos y este sitio no le preguntaba.
        // Dos lectores del mismo dato divergen, exactamente igual que dos
        // escritores.
        //
        // Y la LECTURA resuelve tambien las claves RETIRADAS (Corte 5 · 5A). El
        // catalogo de captura filtra `activo`, asi que en cuanto `V84` retiro
        // `servicios_disponibles` sus valores conservados —cuantos haya; el
        // tamano del legado se afirma como invariante y nunca como cifra, que
        // caduca en cuanto corre una suite— se seguian leyendo pero
        // llegaban con la CLAVE DESNUDA —`rotulo = "servicios_disponibles"`,
        // `tipoDato = null`, al final de la lista—. Conservar el valor y perder
        // su nombre es conservar a medias: el broker lee la clave, y un
        // `tipoDato` nulo ademas cambia lo que se pinta (el SPA decide con el si
        // un booleano se dice «Si/No»). La CAPTURA sigue sin verla.
        ValoresGobernados leidos = lector.de(idOrganizacion, propiedad);
        Map<String, CatalogoAtributo> definiciones =
                gobierno.definicionesParaLeer(idOrganizacion, tipo, leidos.claves());
        List<AtributoFicha> valores = new ArrayList<>();

        for (String clave : leidos.claves()) {
            CatalogoAtributo definicion = definiciones.get(clave);
            valores.add(fichaDeAtributo(clave, definicion, leidos,
                    ContratoDeEscritura.dePropiedad(definicion, tipo)));
        }
        // Por el ORDEN del catalogo, no alfabetico por clave: la colocacion la
        // gobierna el catalogo desde 0B, y ordenar por clave aqui la tiraba.
        valores.sort(java.util.Comparator
                .<AtributoFicha>comparingInt(a -> definiciones.containsKey(a.clave())
                        ? definiciones.get(a.clave()).getOrden() : Integer.MAX_VALUE)
                .thenComparing(AtributoFicha::clave));

        // La serie se lee UNA vez y se reparte por encargo. Consultarla dentro
        // del bucle serian dos consultas identicas por propiedad — el N+1 que
        // RC-003 vino a quitar, en pequeno.
        List<PrecioPropiedad> serie = precios.findByIdPropiedadOrderByFechaAscIdAsc(idPropiedad);

        // TODOS los encargos, no solo los vivos. La ficha responde "que ha
        // pasado con esta propiedad", y un encargo cerrado es el UNICO sitio
        // donde vive su historico economico: filtrarlo borraria de la vista una
        // serie entera sin decir que existe. El listado si se queda con los
        // vivos, porque su pregunta es "que hay en cartera".
        List<Captacion> todos = ordenados(captaciones.encargosDe(idOrganizacion, idPropiedad));

        // Los anuncios de TODOS los encargos en una consulta, no una por bloque.
        // Mismo patron con el que el listado cuelga los encargos de una pagina.
        Map<Long, List<PublicacionService.FichaPublicacion>> anuncios =
                publicaciones.deEncargos(idOrganizacion, todos);

        // Las condiciones de TODOS los encargos en una consulta, igual que los
        // anuncios. Preguntarlas dentro del bucle serian N consultas por ficha
        // -- el N+1 que RC-003 retiro, reapareciendo con un sujeto nuevo.
        Map<Long, ValoresGobernados> pactadas = lector.deEncargos(
                todos.stream().map(Captacion::getId).toList());

        // Con la clave desnuda, decir "no se puede publicar sin el metraje"
        // obligaria al cliente a traducir `metraje_total`. El rotulo esta al
        // lado, en el mismo catalogo que declaro la obligatoriedad.
        Function<String, AtributoQueFalta> conRotulo =
                clave -> new AtributoQueFalta(clave, definiciones.containsKey(clave)
                        ? definiciones.get(clave).getRotulo() : clave);

        // Lo que impide el ALTA: solo ALT.
        List<AtributoQueFalta> faltan = gobierno.obligatoriasQueFaltan(idOrganizacion, propiedad)
                .stream().map(conRotulo).toList();

        // Y lo que impide PUBLICAR: ALT y PUB. Son dos preguntas distintas con
        // dos respuestas verdaderas, no la misma lista contada dos veces.
        //
        // POR QUE SALE DE `faltantesDePropiedadParaPublicar` Y NO SE FILTRA.
        // Es el MISMO metodo que usa `PublicacionServiceImpl.exigirPublicable`
        // para decidir el rechazo. Si esta lista se calculara aparte --o se
        // recortara a solo-PUB para que "no repita" con la de arriba-- habria dos
        // criterios de publicabilidad: uno que decide y otro que cuenta, y en el
        // primer corte que los separe la pantalla diria una cosa y el backend
        // haria otra. Ademas seria falso: una clave ALT ausente TAMBIEN impide
        // publicar, y omitirla aqui prometeria que basta con completar las PUB.
        //
        // Que una ALT salga en las dos listas es la respuesta correcta a dos
        // preguntas distintas, no una duplicacion.
        //
        // Nace por la deuda que midio V82: `tipo_acceso` paso a PUB, dejo de
        // aparecer en `atributosQueFaltan` --que solo lleva ALT-- y no podia
        // aparecer en `encargos[].faltanParaPublicar`, que es del sujeto ENCARGO
        // y por el guard 2.5 de V78 no admite claves de la PROPIEDAD. Resultado
        // medido: 21 locales bloqueados y CERO con senal visible.
        List<AtributoQueFalta> faltanParaPublicar =
                gobierno.faltantesDePropiedadParaPublicar(idOrganizacion, propiedad)
                        .stream().map(conRotulo).toList();

        // Los encargos se arman DESPUES de esas dos listas y no antes: la
        // capacidad `publicacionGestionable` de cada bloque necesita la deuda de
        // la PROPIEDAD --que es una sola para todos-- ademas de la suya. Es la
        // unica razon del orden.
        List<EncargoFicha> encargos = todos.stream()
                .map(encargo -> fichaDeEncargo(actor, encargo, serie,
                        anuncios.getOrDefault(encargo.getId(), List.of()),
                        idOrganizacion, tipo,
                        pactadas.getOrDefault(encargo.getId(), ValoresGobernados.vacio()),
                        faltanParaPublicar))
                .toList();

        boolean seOfrece = encargos.stream().anyMatch(EncargoFicha::vivo);

        return new FichaPropiedadUniversal(idPropiedad, propiedad.getCodigo(),
                AtributosGobernados.nombreDelTipo(tipo), AtributosGobernados.rotuloDelTipo(tipo),
                propiedad.getUso(), AtributosGobernados.rotuloDelUso(propiedad.getUso()),
                propiedad.getDescripcion(),
                propiedad.getEstadoRegistro(), rotuloDe(propiedad.estadoRegistroTipado()),
                // La situacion comercial se DERIVA de los encargos vivos, no se
                // copia de la columna (V76 §2: "no se crea NO_OFRECIDA... crearia
                // dos autoridades para la misma verdad").
                //
                // Sin esto la ficha se contradecia a si misma en la misma
                // pantalla: "DISPONIBILIDAD COMERCIAL: Disponible" arriba y
                // "Ningun encargo vigente: hoy no esta ni en venta ni en
                // alquiler" tres bloques mas abajo. La columna conserva el
                // ultimo estado comercial conocido --es historia y no se borra--
                // pero cerrar un encargo no la devuelve a "no ofrecida": NULL no
                // es un codigo del vocabulario y no hay transicion de vuelta.
                // Medido el 2026-08-22: 10 propiedades de la cartera de
                // desarrollo y 68 de la de pruebas decian estar disponibles sin
                // que nadie las hubiera encargado.
                seOfrece ? propiedad.getDisponibilidadComercial() : null,
                seOfrece ? rotuloDe(propiedad.disponibilidadComercialTipada()) : null,
                ubicacionDe(propiedad), titulares, valores, encargos, faltan, faltanParaPublicar,
                // La misma materia prima, leida como continuidad del inmueble en
                // vez de como episodios sueltos. Sin consultas de mas.
                historiaDe(todos, serie),
                actividad.de(idOrganizacion, todos),
                Fechas.local(propiedad.getFechaRegistro()),
                responsabilidadDe(actor, propiedad));
    }

    /**
     * <b>Quien responde, y que puede hacer quien mira</b> (P0).
     *
     * <p>Lo resuelve la misma {@code AutoridadDePropiedad} que despues deniega
     * la escritura. Es a proposito: una segunda tabla de decision aqui haria
     * posible que la ficha dijera "puedes editar" y el PUT contestara 403 —el
     * peor fallo de esta pantalla, porque el usuario ya escribio.
     *
     * <p>Una propiedad FALTANTE llega igual que cualquier otra: <b>visible</b>,
     * completa y con el motivo dicho. No desaparece del listado ni de la ficha,
     * y no se le inventa un dueno.
     *
     * <p><b>No lo compone este metodo</b>: lo produce {@code AutoridadDePropiedad},
     * que es el unico productor del Core (P0-H1). Aqui se compuso hasta la
     * primera version de este corte, y por eso la ficha del encargo —otra
     * pantalla, otro servicio— acabo calculando su propia version de la regla
     * en Angular. Un productor, todas las pantallas.
     */
    private Responsabilidad responsabilidadDe(Actor actor, Propiedad propiedad) {
        return autoridad.responsabilidadDe(actor, propiedad);
    }

    /**
     * Vivos primero —venta antes que alquiler, que es el orden en que lo lee el
     * negocio— y detras los cerrados, del mas reciente al mas antiguo.
     *
     * <p>Es orden de PRESENTACION y por eso se decide aqui y no en el
     * {@code order by}: lo que la consulta no puede saber es que "vivo" pesa
     * mas que "reciente".
     *
     * <p>Lo que este metodo <b>no</b> hace es agrupar. Dos encargos de alquiler
     * de anos distintos siguen siendo dos elementos de la lista, cada uno con
     * su id: fundirlos por operacion mezclaria dos series economicas que no
     * tienen nada que ver.
     */
    private static List<Captacion> ordenados(List<Captacion> encargos) {
        return encargos.stream()
                .sorted(java.util.Comparator
                        .comparingInt((Captacion e) -> Captacion.esVivo(e.estadoActual()) ? 0 : 1)
                        .thenComparingInt(e -> e.operacion().ordinal())
                        .thenComparing(Captacion::getId, java.util.Comparator.reverseOrder()))
                .toList();
    }

    /**
     * <b>La memoria del inmueble, proyectada sobre sus encargos.</b>
     *
     * <p>Se calcula aqui, con lo que {@code ficha()} ya leyo: los encargos y la
     * serie de precios completa. <b>Ni una consulta mas</b> — es una lectura
     * distinta de los mismos hechos, no hechos distintos.
     *
     * <p>Y no fusiona nada. Cada cifra de la historia arrastra el
     * {@code idEncargo} que la produjo, de modo que de «la ultima renta fueron
     * 2 400» siempre se puede volver al episodio que lo dice. Es la diferencia
     * entre agregar para leer y mezclar.
     */
    private static HistoriaComercial historiaDe(List<Captacion> encargos,
                                                List<PrecioPropiedad> serie) {
        Map<Long, Captacion> porId = new LinkedHashMap<>();
        encargos.forEach(encargo -> porId.put(encargo.getId(), encargo));

        // La linea: todos los movimientos del inmueble, atravesando encargos, del
        // mas reciente al mas antiguo. Un hito huerfano -- de un encargo que ya
        // no esta -- se descarta: sin episodio no se puede decir de que operacion
        // era, y afirmarlo seria inventarlo.
        List<HitoDeLaHistoria> linea = serie.stream()
                .filter(precio -> porId.containsKey(precio.getIdCaptacion()))
                .map(precio -> {
                    Captacion encargo = porId.get(precio.getIdCaptacion());
                    OperacionInmobiliaria operacion = encargo.operacion();
                    return new HitoDeLaHistoria(precio.getFecha(), precio.getHito(),
                            PrecioPropiedad.rotuloDelHito(precio.getHito()), precio.getMonto(),
                            precio.getMoneda(), encargo.getId(), encargo.getCodigoCaptacion(),
                            operacion.name(), enFrase(operacion.name()));
                })
                .sorted(java.util.Comparator
                        .comparing(HitoDeLaHistoria::fecha,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(HitoDeLaHistoria::idEncargo,
                                java.util.Comparator.reverseOrder()))
                .toList();

        // Y el recuento por operacion: cuantas veces, desde cuando, y los dos
        // ultimos importes que NO son el mismo dato.
        List<EpisodiosDeOperacion> porOperacion = new ArrayList<>();
        for (OperacionInmobiliaria operacion : OperacionInmobiliaria.values()) {
            List<Captacion> episodios = encargos.stream()
                    .filter(encargo -> encargo.operacion() == operacion)
                    .sorted(java.util.Comparator.comparing(Captacion::getFechaCaptacion,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .toList();
            if (episodios.isEmpty()) {
                continue;
            }
            Captacion primero = episodios.get(0);
            Captacion ultimo = episodios.get(episodios.size() - 1);
            boolean vivoAhora = episodios.stream()
                    .anyMatch(encargo -> Captacion.esVivo(encargo.estadoActual()));

            List<HitoDeLaHistoria> suyos = linea.stream()
                    .filter(hito -> operacion.name().equals(hito.operacion()))
                    .toList();

            porOperacion.add(new EpisodiosDeOperacion(operacion.name(), enFrase(operacion.name()),
                    episodios.size(), primero.getFechaCaptacion(),
                    // Sin fin mientras haya uno vivo: escribir la fecha del
                    // ultimo cerrado diria que la propiedad ya no esta en esa
                    // operacion, y si lo esta.
                    vivoAhora ? null : ultimo.getFechaFinVigencia(),
                    vivoAhora,
                    importeDe(suyos, PEDIDOS), importeDe(suyos, CIERRES)));
        }
        return new HistoriaComercial(List.copyOf(porOperacion), linea);
    }

    /**
     * Lo que se PIDIO: el importe autorizado, o el publicado.
     *
     * <p>{@code O} ofertado no entra: una oferta es lo que ofrecio un
     * interesado, no lo que pedia el propietario.
     */
    private static final Set<String> PEDIDOS =
            Set.of(PrecioPropiedad.HITO_AUTORIZADO, PrecioPropiedad.HITO_PUBLICADO);

    /**
     * Lo que se CERRO de verdad. Solo {@code C}: {@code A} aceptado es un
     * acuerdo que todavia puede caerse antes de la firma.
     */
    private static final Set<String> CIERRES = Set.of("C");

    /**
     * El ultimo importe de una clase de hito, o {@code null}.
     *
     * <p><b>Devolver {@code null} es la respuesta correcta</b> cuando no hay
     * ninguno. La tentacion es caer al precio pedido cuando no hubo cierre, y
     * ese respaldo convierte «lo que pediamos» en «lo que vale» sin que nadie lo
     * note — que es exactamente el dato que despues se cita en una negociacion.
     */
    private static ImporteFechado importeDe(List<HitoDeLaHistoria> hitos, Set<String> clases) {
        // `hitos` ya viene del mas reciente al mas antiguo.
        return hitos.stream()
                .filter(hito -> clases.contains(hito.hito()))
                .findFirst()
                .map(hito -> new ImporteFechado(hito.monto(), hito.moneda(), hito.fecha(),
                        hito.idEncargo(), hito.codigoEncargo()))
                .orElse(null);
    }

    /**
     * El historico de un encargo es SOLO el suyo. Filtrar no es cosmetico: una
     * propiedad en venta y en alquiler tiene dos series, y mezclarlas produce
     * una linea temporal que no significa nada.
     *
     * <p><b>Se filtra por encargo y no por operacion</b>, que es la diferencia
     * que aparece en cuanto hay historia: tres alquileres sucesivos comparten
     * la operacion, asi que filtrar por ella le daria al encargo de 2026 los
     * precios de 2024. El hito se escribe atado a su encargo
     * ({@code precio_propiedad.id_captacion}) justo para esto.
     */
    private EncargoFicha fichaDeEncargo(Actor actor, Captacion encargo, List<PrecioPropiedad> serie,
                                        List<PublicacionService.FichaPublicacion> anuncios,
                                        long idOrganizacion, String tipoPropiedad,
                                        ValoresGobernados pactadas,
                                        List<AtributoQueFalta> faltanDeLaPropiedad) {
        CondicionEconomicaCaptacion condicion = encargo.getCondicionEconomica();
        // Se calcula UNA vez y se usa dos: para publicarla en el bloque y para
        // decidir la capacidad. Pedirla dos veces abriria la puerta a que las dos
        // respuestas se separaran.
        List<AtributoQueFalta> faltanDelEncargo =
                faltanEnElEncargo(idOrganizacion, encargo, tipoPropiedad);
        List<HitoFicha> historico = serie.stream()
                .filter(precio -> Objects.equals(precio.getIdCaptacion(), encargo.getId()))
                .map(precio -> new HitoFicha(precio.getHito(),
                        PrecioPropiedad.rotuloDelHito(precio.getHito()),
                        precio.getMonto(), precio.getMoneda(), precio.getFecha()))
                .toList();
        OperacionInmobiliaria operacion = encargo.operacion();
        return new EncargoFicha(encargo.getId(), encargo.getCodigoCaptacion(),
                operacion.name(), enFrase(operacion.name()),
                encargo.estadoActual(), rotuloDe(EstadoCaptacion.desde(encargo.estadoActual())),
                Captacion.esVivo(encargo.estadoActual()),
                condicion == null ? null : condicion.getImporteReferencia(),
                condicion == null ? null : condicion.getMonedaReferencia(),
                // "precio de venta" o "renta mensual". Viaja porque el nombre
                // del importe lo decide la OPERACION: con el ternario escrito en
                // el cliente habria uno por interfaz, y una ficha de venta
                // rotulada "renta" es un error de bulto (D-A-1 §5).
                operacion.nombreDelImporte(),
                // Se escribia y no se devolvia: un formulario en modo edicion la
                // pintaba desmarcada y la borraba al guardar. Lo destapo el
                // trazado campo -> DTO -> dominio -> persistencia -> LECTURA.
                encargo.getExclusividad(),
                encargo.getAgente() == null ? null : encargo.getAgente().getId(),
                encargo.getAgente() == null ? null : nombreDe(encargo.getAgente().getRol()),
                encargo.getFechaInicioVigencia(), encargo.getFechaFinVigencia(), historico,
                // Las condiciones pactadas EN ESTE encargo (V73). Van dentro del
                // bloque y no en la ficha porque no son del inmueble: la venta y
                // el alquiler abiertos a la vez ensenan aqui numeros distintos, y
                // el alquiler cerrado de 2024 sigue ensenando los suyos.
                condicionesDe(idOrganizacion, encargo, tipoPropiedad, pactadas),
                faltanDelEncargo,
                anuncios, gestionDePublicacion(encargo, faltanDeLaPropiedad, faltanDelEncargo),
                // Y si QUIEN PREGUNTA puede tocar ESTE encargo (P0-4). Es una
                // tercera autoridad, distinta de la de la propiedad: el
                // responsable del inmueble no manda sobre el encargo ajeno, y
                // el agente del alquiler no manda sobre la venta. Sin este
                // campo la pantalla lo deducia del rol -- un "el rol de la
                // sesion es AGENTE" que habilitaba el boton a TODOS los agentes
                // del tenant, incluidos los que iban a recibir un 403.
                autoridadDelEncargo(actor, encargo));
    }

    /**
     * Se pregunta con el <b>mismo metodo</b> que despues deniega. Un segundo
     * criterio "solo para pintar" es exactamente como se llega a un boton
     * activo que el backend rechaza cuando la persona ya escribio.
     */
    private boolean autoridadDelEncargo(Actor actor, Captacion encargo) {
        try {
            autoridad.exigirEdicionDelEncargo(actor, encargo);
            return true;
        } catch (AccesoNoAutorizadoException denegado) {
            return false;
        }
    }

    /**
     * Las condiciones de un encargo, con su rotulo y su tipo, en el orden del
     * catalogo.
     *
     * <p>Los valores llegan ya leidos por el lote --{@code pactadas}--; lo unico
     * que se pide aqui son las DEFINICIONES, y eso cuesta una consulta por
     * {@code (tipo, operacion)} distinto, no una por encargo. Con dos encargos
     * de la misma operacion es una sola.
     */
    /**
     * <b>Un atributo, con su texto para leer y sus huecos crudos para
     * corregir</b> (V77).
     *
     * <p>El texto compuesto —«PEN 350», «COCINA, LAVADORA»— no se puede partir
     * de vuelta sin inferir, y un elemento con una coma dentro lo haria
     * imposible. Asi que la moneda y los elementos viajan aparte, tal como los
     * guarda el lector. Se construye aqui y no en cada sitio porque los dos
     * sujetos —propiedad y encargo— tienen que decir esto igual: dos
     * constructores del mismo dato divergen, exactamente igual que dos
     * lectores.
     */
    private static AtributoFicha fichaDeAtributo(String clave, CatalogoAtributo definicion,
                                                 ValoresGobernados leidos,
                                                 ContratoDeEscritura.Vigencia vigencia) {
        ValorLogico crudo = leidos.valor(clave);
        return new AtributoFicha(clave,
                definicion == null ? clave : definicion.getRotulo(),
                definicion == null ? null : definicion.getTipoDato(),
                definicion == null ? null : definicion.getUnidad(),
                leidos.texto(clave),
                crudo == null ? null : crudo.moneda(),
                crudo == null ? null : crudo.valores(),
                // La senal sale del CATALOGO --de `activo` y de las filas por
                // tipo--, nunca de un nombre de clave. Un
                // `if (clave.equals("servicios_disponibles"))` funcionaria hoy,
                // dejaria muda la retirada siguiente y no diria nada de
                // `area_terreno`, que no esta retirada y tampoco se puede
                // corregir en un terreno.
                vigencia.estadoDato(), vigencia.editable(), vigencia.motivoNoEditable());
    }

    private List<AtributoFicha> condicionesDe(long idOrganizacion, Captacion encargo,
                                              String tipoPropiedad, ValoresGobernados pactadas) {
        if (pactadas.claves().isEmpty()) {
            return List.of();
        }
        Comercializacion donde = new Comercializacion(encargo.getId(), tipoPropiedad,
                encargo.getMotivoOperacion());
        // Con las RETIRADAS resueltas, igual que el bloque fisico: lo pactado en
        // un encargo cerrado se sigue leyendo con su nombre aunque la condicion
        // ya no se pacte (Corte 5 · 5A).
        Map<String, CatalogoAtributo> definiciones =
                condiciones.definicionesParaLeer(idOrganizacion, donde, pactadas.claves());
        List<AtributoFicha> valores = new ArrayList<>();
        for (String clave : pactadas.claves()) {
            CatalogoAtributo definicion = definiciones.get(clave);
            valores.add(fichaDeAtributo(clave, definicion, pactadas,
                    ContratoDeEscritura.deEncargo(definicion, tipoPropiedad,
                            encargo.getMotivoOperacion())));
        }
        valores.sort(java.util.Comparator
                .<AtributoFicha>comparingInt(a -> definiciones.containsKey(a.clave())
                        ? definiciones.get(a.clave()).getOrden() : Integer.MAX_VALUE)
                .thenComparing(AtributoFicha::clave));
        return List.copyOf(valores);
    }

    /**
     * Lo que le falta a ESTE encargo para poder anunciarse, con su nombre.
     *
     * <p>Va por encargo y no en la ficha, y esa colocacion es la respuesta
     * entera del corte: la misma propiedad puede estar lista para alquilarse y
     * no para venderse, asi que una unica lista de faltantes no podria decir
     * cual de los dos bloques hay que completar.
     */
    private List<AtributoQueFalta> faltanEnElEncargo(long idOrganizacion, Captacion encargo,
                                                     String tipoPropiedad) {
        Comercializacion donde = new Comercializacion(encargo.getId(), tipoPropiedad,
                encargo.getMotivoOperacion());
        List<String> claves = condiciones.faltantesDeEncargoParaPublicar(idOrganizacion, donde);
        if (claves.isEmpty()) {
            return List.of();
        }
        List<String> rotulos = condiciones.rotulosDe(idOrganizacion, donde, claves);
        List<AtributoQueFalta> faltan = new ArrayList<>();
        for (int i = 0; i < claves.size(); i++) {
            faltan.add(new AtributoQueFalta(claves.get(i), rotulos.get(i)));
        }
        return List.copyOf(faltan);
    }

    /**
     * <b>Si este encargo admite gestion de publicacion, y por que no.</b>
     *
     * <p>Se publica como capacidad para que la pantalla no la reimplemente con un
     * {@code estado === 'A'} ni contando faltantes: el backend la vuelve a
     * imponer al escribir, y la pantalla solo obedece.
     *
     * <h2>Hasta donde llega esta promesa, y hasta donde NO</h2>
     *
     * <p><b>Promete:</b> que no hay impedimento conocido -- el encargo esta vivo y
     * no falta ningun dato de catalogo que bloquee publicar, ni de la PROPIEDAD ni
     * de este ENCARGO.
     *
     * <p><b>NO promete que publicar vaya a funcionar.</b> {@code crearEnEncargo}
     * valida ademas tres cosas que dependen del PAYLOAD y que no existen cuando se
     * lee la ficha, asi que no se pueden plegar aqui:
     *
     * <ul>
     *   <li>el <b>canal</b> es obligatorio ({@code construir});</li>
     *   <li>el <b>estado</b> tiene que estar en {@code Publicacion.ESTADOS};</li>
     *   <li>la <b>moneda</b> del importe publicado tiene que ser valida.</li>
     * </ul>
     *
     * <p>Un {@code POST} con el canal vacio seguira devolviendo 400 con
     * {@code permitida = true}, y <b>eso esta bien</b>: son errores de lo que se
     * envia, no del estado del inmueble. Queda escrito para no convertir esta
     * capacidad en una promesa mas amplia de lo que puede demostrar. (Tampoco
     * cubre la pertenencia al tenant: un encargo ajeno no llega a esta ficha.)
     *
     * <h2>De donde sale, y por que no hay una segunda verdad</h2>
     *
     * <p>De <b>las dos listas que la ficha ya calculo</b>, no de una consulta
     * nueva. Y esas listas salen de {@code faltantesDePropiedadParaPublicar} y
     * {@code faltantesDeEncargoParaPublicar}, que son <b>los mismos metodos que
     * usa {@code PublicacionServiceImpl.exigirPublicable}</b> para decidir el
     * rechazo. Es literalmente la misma salida: no puede decir que si donde el
     * comando dice que no. Una tercera consulta, aunque hoy coincidiera, seria una
     * verdad que puede divergir manana.
     *
     * <p>Antes de este corte esto era {@code static} y solo recibia el encargo, asi
     * que solo sabia responder "esta vivo?". Con {@code tipo_acceso} en PUB desde
     * V82, las 21 propiedades bloqueadas decian {@code permitida = true} y el
     * comando las rechazaba con 400.
     *
     * @param faltanDeLaPropiedad la deuda del inmueble, una sola para todos sus
     *                            encargos
     * @param faltanDelEncargo    la de este encargo concreto
     */
    private GestionDePublicacion gestionDePublicacion(Captacion encargo,
                                                      List<AtributoQueFalta> faltanDeLaPropiedad,
                                                      List<AtributoQueFalta> faltanDelEncargo) {
        // No se publica lo que ya no se ofrece. Es la regla que ya estaba, y se
        // mira primero porque un encargo cerrado no se arregla completando datos.
        if (!Captacion.esVivo(encargo.estadoActual())) {
            return new GestionDePublicacion(false,
                    "El encargo " + encargo.getCodigoCaptacion() + " ya no esta vigente.");
        }

        boolean faltaFicha = !faltanDeLaPropiedad.isEmpty();
        boolean faltaPacto = !faltanDelEncargo.isEmpty();
        if (!faltaFicha && !faltaPacto) {
            return new GestionDePublicacion(true, null);
        }

        // El motivo dice CUAL de los tres impedimentos es, y no repite las listas:
        // ya viajan en `faltanParaPublicar` --el de la propiedad y el del bloque--
        // con su rotulo del catalogo. Repetirlas aqui daria dos sitios donde leer
        // lo mismo, y ninguna clave se nombra a mano.
        String motivo;
        if (faltaFicha && faltaPacto) {
            motivo = "Faltan datos de la ficha del inmueble y condiciones de este encargo.";
        } else if (faltaFicha) {
            motivo = "Faltan datos de la ficha del inmueble.";
        } else {
            motivo = "Faltan condiciones de este encargo.";
        }
        return new GestionDePublicacion(false, motivo);
    }

    /** `VENTA` -> `Venta`. El valor viaja en mayusculas; la persona lo lee en frase. */
    private static String enFrase(String valor) {
        return valor.charAt(0) + valor.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String rotuloDe(EstadosDominio.Codigo estado) {
        return estado == null ? null : estado.descripcion();
    }

    private static Ubicacion ubicacionDe(Propiedad propiedad) {
        return new Ubicacion(propiedad.getDireccion(), propiedad.getDistrito(),
                propiedad.getZonaUrbanizacion(), propiedad.getGeoLat(), propiedad.getGeoLong(),
                propiedad.getInteriorUnidad(), propiedad.getPiso(), propiedad.getReferenciaInterna(),
                propiedad.getNombreEdificioGaleria());
    }

    // ==================================================================
    // Validacion
    // ==================================================================

    private static String tipoValidado(String tipoPropiedad) {
        return AtributosGobernados.codigoDelTipo(tipoPropiedad)
                .orElseThrow(() -> new ReglaNegocioException(
                        "Tipo de propiedad desconocido: \"" + tipoPropiedad + "\". Son siete: LOCAL, "
                                + "OFICINA, DEPARTAMENTO, CASA, TERRENO, ALMACEN y OTRO."));
    }

    /**
     * El uso por defecto sale del TIPO y no de una constante: una vivienda es
     * uso V y un local es uso C. Ponerlo siempre a comercial —que es lo que
     * hacia el alta legada— etiquetaba mal cada departamento que entrara.
     */
    private static String usoValidado(String uso, String tipoPropiedad) {
        if (uso != null && !uso.isBlank()) {
            String limpio = uso.trim().toUpperCase(Locale.ROOT);
            String codigo = switch (limpio) {
                case "C", "COMERCIAL" -> "C";
                case "V", "VIVIENDA" -> "V";
                case "I", "INDUSTRIAL" -> "I";
                case "M", "MIXTO" -> "M";
                default -> null;
            };
            if (codigo == null) {
                throw new ReglaNegocioException(
                        "Uso desconocido: \"" + uso + "\". Son COMERCIAL, VIVIENDA, INDUSTRIAL o MIXTO.");
            }
            return codigo;
        }
        return switch (tipoPropiedad) {
            case "D", "C" -> "V";
            case "A" -> "I";
            default -> "C";
        };
    }

    /**
     * <b>El `piso` que llega dentro de `ubicacion` es una CLAVE GOBERNADA, y se
     * enruta como tal</b> (4.P, segunda vuelta).
     *
     * <p>De los nueve huecos de {@code UbicacionRequest}, ocho son de la
     * ubicacion fisica y no estan en el catalogo. {@code piso} es <b>el unico
     * solape</b>: existe como clave del catalogo, declarada {@code ESTRUCTURAL}
     * sobre el campo canonico {@code PISO}, con su vocabulario y su exigencia.
     *
     * <p>Hasta esta correccion se escribia con {@code propiedad::setPiso} desde
     * {@code aplicarUbicacion}, o sea <b>por fuera del enrutador</b>. El efecto
     * medido: {@code PUT /propiedades/{id}} con {@code {"ubicacion":{"piso":"7"}}}
     * respondia 200, cambiaba la columna, <b>publicaba el valor como gobernado en
     * la propia respuesta</b> y no dejaba ni una fila de linaje. Y como retirar
     * SI pasaba por el enrutador, la historia quedaba
     * {@code EDICION 7->8} + {@code RETIRADA 8->vacio} <b>sin ALTA</b>: el 7
     * aparecia de la nada como valor hallado.
     *
     * <p>No se cierra quitando el hueco del cable —el SPA y las suites lo mandan
     * ahi, y 4.P no estrena superficie— sino <b>enrutandolo</b>: el cliente manda
     * un nombre logico y el Core decide donde vive, que es la regla de D-E4-3
     * aplicada al tercer sitio por el que entraba.
     *
     * <p>La clave se resuelve por <b>concepto</b> y no por literal: preguntar
     * cual es la clave del campo {@code PISO} es lo mismo que hace
     * {@link EscritorEstructural} al escribir, y deja funcionar a una
     * organizacion que declare la suya con otro nombre.
     *
     * <p><b>Mandar el piso por los dos sitios a la vez se rechaza.</b> Son dos
     * intenciones sobre el mismo dato y no se elige entre ellas por precedencia:
     * cualquier orden que se escogiera seria una regla inventada que el cliente
     * no sabe. Es la misma decision que ya rige para una clave que llega con
     * valor y en {@code atributosABorrar}.
     */
    private Map<String, ValorAtributo> conElPisoGobernado(long idOrganizacion,
                                                          Map<String, ValorAtributo> valores,
                                                          Ubicacion ubicacion) {
        if (ubicacion == null || ubicacion.piso() == null) {
            return valores;
        }
        Optional<String> clave = gobierno.claveDelCampo(
                idOrganizacion, CatalogoAtributo.CAMPO_PISO);
        if (clave.isEmpty()) {
            // El catalogo de esta organizacion no declara ninguna clave activa
            // sobre el campo PISO, asi que no hay donde enrutar el valor.
            //
            // Y entonces SE RECHAZA, no se descarta. El comentario que habia
            // aqui decia que el valor "se queda donde estaba", y desde que
            // `aplicarUbicacion` dejo de escribirlo eso es falso: devolver el
            // mapa intacto lo PERDIA con un 200, que es la peor de las tres
            // respuestas posibles. "Aqui no se gobierna el piso" tiene que
            // significar «se rechaza» o «se dice»; nunca «se pierde en
            // silencio».
            //
            // Hoy es inalcanzable --`piso` es una clave del sistema y ninguna
            // organizacion puede retirarla--, y por eso mismo conviene que la
            // rama diga la verdad: el dia que sea alcanzable, lo sera sin que
            // nadie vuelva a mirar esta linea.
            throw new ReglaNegocioException(
                    "Llego un piso en \"ubicacion\" y el catalogo de esta organizacion no "
                            + "gobierna ninguna clave sobre el campo PISO, asi que no hay donde "
                            + "guardarlo. Se rechaza en vez de descartarlo: un valor que se "
                            + "pierde con un 200 es peor que un error.");
        }
        ValorAtributo yaVenia = valores.get(clave.get());
        if (yaVenia != null) {
            // Que llegue por los dos huecos NO es de suyo una contradiccion, y
            // aqui hay que ser exacto: LA FICHA PUBLICA EL PISO DOS VECES --
            // dentro de `ubicacion` y entre los `atributos` --, asi que un
            // cliente que devuelve lo que el Core le dio manda los dos. Rechazar
            // eso seria rechazar la ida y vuelta del propio Core.
            //
            // Contradiccion es que digan COSAS DISTINTAS. Entonces si se avisa,
            // porque elegir uno seria descartar el otro sin decirlo -- la misma
            // regla que ya rige para una clave que llega con valor y a la vez en
            // `atributosABorrar`.
            if (!ubicacion.piso().equals(yaVenia.valor())) {
                throw new ReglaNegocioException(
                        "El piso llego dos veces y con valores distintos: \""
                                + ubicacion.piso() + "\" dentro de \"ubicacion\" y \""
                                + yaVenia.valor() + "\" como atributo \"" + clave.get()
                                + "\". Es el mismo dato, y elegir uno por ti seria descartar el "
                                + "otro sin decirlo: manda uno.");
            }
            return valores;
        }
        Map<String, ValorAtributo> conElPiso = new LinkedHashMap<>(valores);
        conElPiso.put(clave.get(), new ValorAtributo(clave.get(), ubicacion.piso()));
        return conElPiso;
    }

    private static Ubicacion ubicacionValidada(Ubicacion ubicacion) {
        if (ubicacion == null) {
            throw new ReglaNegocioException("La ubicacion es obligatoria: direccion y distrito.");
        }
        if (ubicacion.direccion() == null || ubicacion.direccion().isBlank()) {
            throw new ReglaNegocioException("La direccion es obligatoria.");
        }
        if (ubicacion.distrito() == null || ubicacion.distrito().isBlank()) {
            throw new ReglaNegocioException("El distrito es obligatorio.");
        }
        return ubicacion;
    }

    /**
     * La misma ubicacion, con la regla de la <b>edicion</b>: {@code null} es "no
     * lo estoy editando", tambien en direccion y distrito.
     *
     * <p>Exigirlas aqui como en el alta convertiria la fusion en una mentira:
     * un contrato donde un campo ausente no se toca no puede tener dos campos
     * que hay que mandar siempre. Lo que <b>si</b> se rechaza es el blanco: son
     * NOT NULL y no existe forma de dejar una propiedad sin direccion, asi que
     * {@code ""} no puede colarse como un borrado disfrazado.
     */
    private static Ubicacion ubicacionDeEdicion(Ubicacion ubicacion) {
        if (ubicacion.direccion() != null && ubicacion.direccion().isBlank()) {
            throw new ReglaNegocioException(
                    "La direccion no se puede dejar en blanco: toda propiedad esta en algun sitio.");
        }
        if (ubicacion.distrito() != null && ubicacion.distrito().isBlank()) {
            throw new ReglaNegocioException(
                    "El distrito no se puede dejar en blanco: toda propiedad esta en algun sitio.");
        }
        return ubicacion;
    }

    /**
     * Las cuotas se comprueban <b>antes</b> de escribir. La base lo garantiza
     * con un constraint trigger diferido (V47), pero ese estalla al COMMIT y
     * su mensaje habla de sumas, no de titulares: aqui se puede decir "las
     * cuotas suman 90, faltan 10".
     */
    private static List<Titular> titularesValidados(List<Titular> titulares) {
        if (titulares == null || titulares.isEmpty()) {
            // CERO titulares es legitimo desde V76: se puede conocer un inmueble
            // sin saber de quien es. La exigencia no desaparece, se MUDA al
            // ENCARGO -- el mensaje que estaba aqui hablaba de "con quien se
            // negocia" y "quien autoriza el precio", que son cosas del encargo,
            // no del registro. Ver `exigirTitularidadConocida`.
            return List.of();
        }
        Set<Long> vistos = new HashSet<>();
        BigDecimal suma = BigDecimal.ZERO;
        List<Titular> normalizados = new ArrayList<>();
        for (Titular titular : titulares) {
            if (titular.idRolPropietario() == null) {
                throw new ReglaNegocioException("Cada titular tiene que identificar a su propietario.");
            }
            if (!vistos.add(titular.idRolPropietario())) {
                throw new ReglaNegocioException(
                        "El titular " + titular.idRolPropietario() + " figura dos veces. Una "
                                + "copropiedad son personas distintas con cuotas distintas.");
            }
            BigDecimal cuota = titular.cuota() != null
                    ? titular.cuota()
                    // Un solo titular sin cuota declarada es el 100 %: es el
                    // caso normal y obligar a escribirlo seria burocracia.
                    : (titulares.size() == 1 ? TitularidadPropiedad.CUOTA_TOTAL : null);
            if (cuota == null || cuota.signum() <= 0 || cuota.compareTo(TitularidadPropiedad.CUOTA_TOTAL) > 0) {
                throw new ReglaNegocioException(
                        "La cuota del titular " + titular.idRolPropietario()
                                + " tiene que estar entre 0 y 100.");
            }
            suma = suma.add(cuota);
            normalizados.add(new Titular(titular.idRolPropietario(), cuota, titular.representante()));
        }
        if (suma.compareTo(TitularidadPropiedad.CUOTA_TOTAL) != 0) {
            throw new ReglaNegocioException(
                    "Las cuotas suman " + suma.stripTrailingZeros().toPlainString()
                            + " y tienen que sumar 100.");
        }
        long representantes = normalizados.stream()
                .filter(titular -> Boolean.TRUE.equals(titular.representante())).count();
        if (representantes > 1) {
            throw new ReglaNegocioException(
                    "Solo un titular puede ser el representante: es con quien se habla.");
        }
        return normalizados;
    }

    private static int indiceDelRepresentante(List<Titular> titulares) {
        for (int i = 0; i < titulares.size(); i++) {
            if (Boolean.TRUE.equals(titulares.get(i).representante())) {
                return i;
            }
        }
        // Nadie lo declaro: el primero. Es una eleccion arbitraria y visible,
        // no una regla de negocio; la base exige que haya exactamente uno.
        return 0;
    }

    private static Map<String, ValorAtributo> atributosValidados(List<ValorAtributo> valores) {
        Map<String, ValorAtributo> porClave = new LinkedHashMap<>();
        if (valores == null) {
            return porClave;
        }
        for (ValorAtributo valor : valores) {
            if (valor.clave() == null || valor.clave().isBlank()) {
                throw new ReglaNegocioException("Un atributo sin clave no se puede guardar.");
            }
            if (porClave.put(valor.clave().trim(), valor) != null) {
                throw new ReglaNegocioException(
                        "El atributo \"" + valor.clave() + "\" llego dos veces con valores distintos.");
            }
        }
        return porClave;
    }

    /**
     * Las operaciones declaradas, sin repetir. Dos elementos de la misma
     * operacion serian dos encargos identicos, que es lo que
     * {@code uq_captacion_viva_por_operacion} prohibe (V50).
     */
    /**
     * Las operaciones declaradas, normalizadas. <b>Cero es una respuesta
     * valida</b> desde V75.
     *
     * <p>Exigir al menos una era la razon por la que toda propiedad nacia con
     * un encargo vivo, y eso contradecia el embudo: la prospeccion existe para
     * CONSEGUIR el encargo, asi que el encargo no puede tener que existir antes
     * de prospectar. Registrar no es encargar.
     *
     * <p>Lo que NO se afloja es el resto: si viene una operacion, sigue
     * teniendo que traer importe, moneda y una sola aparicion por operacion.
     * Que la lista pueda estar vacia no significa que pueda estar a medias.
     */
    private static List<OperacionSolicitada> operacionesValidadas(List<OperacionSolicitada> operaciones) {
        if (operaciones == null || operaciones.isEmpty()) {
            return List.of();
        }
        Set<OperacionInmobiliaria> vistas = new LinkedHashSet<>();
        List<OperacionSolicitada> normalizadas = new ArrayList<>();
        for (OperacionSolicitada solicitada : operaciones) {
            OperacionInmobiliaria operacion;
            try {
                operacion = OperacionInmobiliaria.desde(solicitada.operacion());
            } catch (IllegalArgumentException e) {
                throw new ReglaNegocioException(e.getMessage());
            }
            if (!vistas.add(operacion)) {
                throw new ReglaNegocioException(
                        "La operacion " + operacion.name() + " llego dos veces. Una propiedad tiene "
                                + "como mucho un encargo vivo de cada operacion.");
            }
            if (solicitada.importe() == null || solicitada.importe().signum() < 0) {
                throw new ReglaNegocioException(
                        "Falta el " + operacion.nombreDelImporte() + " de la operacion "
                                + operacion.name() + ".");
            }
            String moneda = CondicionesEconomicas.moneda(solicitada.moneda(), "de la operacion");
            normalizadas.add(new OperacionSolicitada(operacion.name(), solicitada.importe(), moneda,
                    solicitada.tipoComision(), solicitada.baseCalculo(), solicitada.valorComision(),
                    solicitada.tratamientoIgv(), solicitada.exclusividad(),
                    solicitada.inicioEncargo(), solicitada.finEncargo(),
                    // Un normalizador que RECONSTRUYE el record tiene que copiar
                    // todos los campos, y este se dejo las condiciones la primera
                    // vez: el alta se guardaba en verde y la garantia dictada
                    // desaparecia sin un solo error. Es la misma perdida callada
                    // que persigue el Corte 0A, en una linea que parecia inocente.
                    solicitada.condiciones()));
        }
        return normalizadas;
    }

    /**
     * La operacion que alimenta {@code precio_referencial}. El alquiler manda
     * porque la columna se llama "renta referencial" en media docena de sitios
     * del cable actual, y una venta ahi haria que los listados mostraran
     * 180 000 donde esperan una mensualidad.
     */
    private static Optional<OperacionSolicitada> operacionDeReferencia(
            List<OperacionSolicitada> operaciones) {
        if (operaciones.isEmpty()) {
            // Sin encargo no hay precio de referencia, y no se inventa uno.
            return Optional.empty();
        }
        return Optional.of(operaciones.stream()
                .filter(solicitada -> OperacionInmobiliaria.ALQUILER.name().equals(solicitada.operacion()))
                .findFirst()
                // `orElse` evalua su argumento SIEMPRE, tambien cuando el filtro
                // ya encontro uno: sobre una lista vacia reventaba con
                // IndexOutOfBounds antes de llegar a ninguna regla de negocio.
                .orElseGet(() -> operaciones.get(0)));
    }

    /**
     * Que operacion proyectan hoy las columnas espejo de la propiedad, o
     * {@code null} si no proyecta ninguna.
     *
     * <p>El {@code null} es la parte que importa (V76). Antes devolvia ALQUILER
     * cuando no habia ningun encargo vivo, y esa suposicion habria escrito una
     * <b>renta mensual</b> en {@code precio_referencial} de una propiedad que
     * nadie ha encargado: un precio que ningun propietario autorizo, indistinguible
     * en el listado de uno pactado. Sin encargo no hay oferta, y sin oferta no
     * hay precio que proyectar.
     */
    private OperacionInmobiliaria operacionProyectada(Actor actor, Propiedad propiedad) {
        List<Captacion> vivos = captaciones.encargosVivosDe(actor.idOrganizacion(), propiedad.getId());
        return vivos.stream()
                .map(Captacion::operacion)
                .filter(operacion -> operacion == OperacionInmobiliaria.ALQUILER)
                .findFirst()
                .orElseGet(() -> vivos.isEmpty() ? null : vivos.get(0).operacion());
    }

    private void exigirObligatorios(long idOrganizacion, String tipoPropiedad, Set<String> conocidas) {
        List<String> faltan = gobierno.faltantesEntre(idOrganizacion, tipoPropiedad, conocidas);
        if (!faltan.isEmpty()) {
            throw new ReglaNegocioException(
                    "Faltan atributos obligatorios de "
                            + AtributosGobernados.nombreDelTipo(tipoPropiedad) + ": "
                            + String.join(", ", faltan) + ".");
        }
    }

    private List<PersonaRol> rolesDeTitulares(long idOrganizacion, List<Titular> titulares) {
        List<PersonaRol> resueltos = new ArrayList<>();
        for (Titular titular : titulares) {
            resueltos.add(roles.buscarPropietario(idOrganizacion, titular.idRolPropietario())
                    .orElseThrow(() -> new ReglaNegocioException(
                            "El propietario " + titular.idRolPropietario() + " no existe en esta "
                                    + "organizacion o no tiene el rol PROPIETARIO vigente.")));
        }
        return resueltos;
    }

    /**
     * La procedencia declarada, o la que el propio comando demuestra.
     *
     * <p>No se adivina y no hay defecto silencioso: si el cliente no la declara,
     * se lee de lo que el alta ESTA HACIENDO. Un alta que abre encargos es
     * trabajo operativo por definicion; una que no abre ninguno es, hasta que
     * alguien diga otra cosa, conocimiento de mercado. Es la unica lectura que
     * no inventa nada, y el cliente puede corregirla declarandola.
     */
    private static OrigenIncorporacion origenDeclarado(String declarado,
                                                       List<OperacionSolicitada> operaciones) {
        if (declarado != null && !declarado.isBlank()) {
            try {
                return OrigenIncorporacion.desde(declarado);
            } catch (IllegalArgumentException e) {
                throw new ReglaNegocioException(e.getMessage());
            }
        }
        return operaciones.isEmpty()
                ? OrigenIncorporacion.OBSERVACION
                : OrigenIncorporacion.OPERACION;
    }

    private DetalleAgente agenteDe(Actor actor) {
        return agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El alta universal la registra un agente: el encargo necesita saber quien "
                                + "responde por el."));
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    /**
     * <b>Fusiona, no reemplaza.</b> Un campo que llega {@code null} no se toca.
     *
     * <p>Antes esto era una copia campo a campo del objeto entero, y por eso un
     * editor que solo pintaba direccion y distrito —lo unico que el Core
     * exige— borraba de un guardado la zona, el interior, el piso, la
     * referencia, el nombre del edificio y las dos coordenadas. Siete datos que
     * nadie pidio borrar, y el usuario no se enteraba: la pantalla se los
     * mostraba vacios como si nunca hubieran estado.
     *
     * <p>En el alta se comporta igual porque la propiedad es nueva y todo
     * empieza a null. Retirar un valor tiene su propia via declarada:
     * {@code atributosABorrar}.
     */
    private void aplicarUbicacion(Propiedad propiedad, Ubicacion ubicacion) {
        siViene(ubicacion.direccion(), valor -> propiedad.setDireccion(valor.trim()));
        siViene(ubicacion.distrito(), valor -> propiedad.setDistrito(valor.trim()));
        siViene(ubicacion.zonaUrbanizacion(), propiedad::setZonaUrbanizacion);
        siViene(ubicacion.interiorUnidad(), propiedad::setInteriorUnidad);
        // `piso` NO se escribe aqui, y es la correccion central de la segunda
        // vuelta de 4.P. Viaja en el hueco `ubicacion` del cable --lo mandan el
        // SPA y las suites-- pero NO es una coordenada: es una clave gobernada
        // declarada ESTRUCTURAL sobre el campo PISO. Escribirla con un setter
        // la sacaba del enrutador, y con el, del linaje: editar el piso desde
        // la pantalla no dejaba procedencia JAMAS, que es exactamente lo que la
        // frontera de V83 define como defecto. La enruta `pisoGobernado`, que
        // la mete entre los atributos antes de que nadie escriba nada.
        siViene(ubicacion.referenciaInterna(), propiedad::setReferenciaInterna);
        siViene(ubicacion.nombreEdificioGaleria(), propiedad::setNombreEdificioGaleria);
        // `ubicacion` (geography) la deriva el trigger de V46 a partir de estas
        // dos: escribirla desde Java exigiria PostGIS en el modelo JPA.
        siViene(ubicacion.latitud(), propiedad::setGeoLat);
        siViene(ubicacion.longitud(), propiedad::setGeoLong);
        resolverDistrito(propiedad);
    }

    private static <T> void siViene(T valor, Consumer<T> destino) {
        if (valor != null) {
            destino.accept(valor);
        }
    }

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
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private String codigoDePropiedad(long idOrganizacion, String declarado) {
        if (declarado != null && !declarado.isBlank()) {
            return declarado.trim();
        }
        // Correlativo por organizacion, como las captaciones (V6.3): cada
        // corredora numera desde 0001 y no ve el ritmo de las demas.
        return "PROP-%04d".formatted(propiedades.countByOrganizacionId(idOrganizacion) + 1);
    }

    private String codigoDeEncargo(long idOrganizacion) {
        return "ENC-%04d".formatted(captaciones.countByOrganizacionId(idOrganizacion) + 1);
    }

    private static String nombreDe(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }

    /**
     * El evento va en la MISMA transaccion que el hecho. Si la transaccion
     * falla no queda un evento anunciando algo que nunca ocurrio; si tiene
     * exito, el evento esta garantizado sin dos fases ni cola externa.
     */
    private void anotarEvento(Actor actor, String tipo, String entidadTipo, Long entidadId,
                              Procedencia procedencia, Map<String, ?> cargaUtil) {
        eventos.save(procedencia.sellar(EventoDominio
                        .de(actor.idOrganizacion(), tipo, entidadTipo, entidadId,
                                actor.idRolOperativo(), procedencia.canal()))
                .con(documentos.objeto(cargaUtil)));
    }

    /**
     * El borrador que dio lugar al alta queda marcado como ejecutado, con la
     * entidad que produjo. Es lo que permite responder despues "esta propiedad
     * la registro KAIROS a partir de la conversacion del martes".
     */
    private void cerrarBorrador(Actor actor, Long idBorrador, Long idPropiedad) {
        if (idBorrador == null) {
            return;
        }
        borradores.findByOrganizacionIdAndId(actor.idOrganizacion(), idBorrador)
                .filter(BorradorCaptura::estaEnCurso)
                .ifPresent(borrador -> {
                    borrador.ejecutado(Propiedad.ENTIDAD_TIPO, idPropiedad);
                    borradores.save(borrador);
                });
    }

    /** La respuesta del primer intento, reconstruida sin re-ejecutar nada. */
    private ResultadoRegistro reconstruir(ComandoIdempotente previo) {
        Map<String, Object> resultado = documentos.comoMapa(previo.getResultado());
        Object codigo = resultado.get("codigo");
        @SuppressWarnings("unchecked")
        List<Number> encargos = (List<Number>) resultado.getOrDefault("idsEncargos", List.of());
        return new ResultadoRegistro(previo.getEntidadId(),
                codigo == null ? null : codigo.toString(),
                encargos.stream().map(Number::longValue).toList(), true);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String titularesEnHuella(List<Titular> titulares) {
        if (titulares == null) {
            return "";
        }
        return titulares.stream()
                .map(titular -> titular.idRolPropietario() + ":"
                        + (titular.cuota() == null ? "" : titular.cuota().stripTrailingZeros().toPlainString()))
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * Los atributos en la huella de idempotencia, <b>con su naturaleza</b>.
     *
     * <p>La naturaleza entra en la huella desde 4.P y no es un detalle: sin
     * ella, corregir «lo observe» por «me lo dijo el propietario» sobre el mismo
     * valor produciria la misma huella y la segunda edicion se descartaria como
     * repetida. El valor no cambia, pero <b>lo que sabemos de el, si</b>.
     *
     * <p>La confianza va por lo mismo: reinferir el mismo valor con un modelo
     * mas seguro es una afirmacion distinta, y descartarla como repetida seria
     * perder precisamente la mejora.
     */
    private static String atributosEnHuella(List<ValorAtributo> valores) {
        if (valores == null) {
            return "";
        }
        return valores.stream()
                .map(valor -> valor.clave() + "=" + valor.valor()
                        + (valor.naturaleza() == null ? "" : "@" + valor.naturaleza())
                        + (valor.confianza() == null ? ""
                                : "~" + valor.confianza().stripTrailingZeros().toPlainString()))
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * Las condiciones de cada encargo en la huella de idempotencia.
     *
     * <p>Lleva el {@code idEncargo} delante, y por eso mismo: sin el, cambiar la
     * garantia de la venta y luego la del alquiler produciria la misma huella y
     * la segunda edicion se descartaria como repetida.
     */
    private static String condicionesEnHuella(List<CondicionesDeEncargo> bloques) {
        if (bloques == null) {
            return "";
        }
        return bloques.stream()
                .filter(java.util.Objects::nonNull)
                .map(bloque -> bloque.idEncargo() + ":" + atributosEnHuella(bloque.atributos())
                        + ":" + (bloque.atributosABorrar() == null ? ""
                                : String.join("|", bloque.atributosABorrar())))
                .sorted()
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static String operacionesEnHuella(List<OperacionSolicitada> operaciones) {
        if (operaciones == null) {
            return "";
        }
        return operaciones.stream()
                .map(solicitada -> solicitada.operacion() + ":"
                        + (solicitada.importe() == null ? "" : solicitada.importe().stripTrailingZeros().toPlainString())
                        + ":" + solicitada.moneda())
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
