package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
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
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.busqueda.ConjuntoDeCandidatos;
import com.controllocal.persistence.busqueda.CriterioBusquedaInmobiliaria;
import com.controllocal.persistence.busqueda.MotorBusquedaInmobiliaria;
import com.controllocal.persistence.query.PropiedadListado;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.TitularidadPropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.Pagina;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.UnidadRelacionadaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.soporte.ContratoDeEscritura;
import com.controllocal.service.soporte.ValorLogico;
import com.controllocal.service.soporte.ValoresGobernados;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ActividadDeLaPropiedad;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaRegistrada;
import com.controllocal.service.captura.ValidadorRespuestaCaptura;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.soporte.AnunciosDeLosEncargos;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.AtributosDeEncargo;
import com.controllocal.service.soporte.Comercializacion;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.ElegibilidadDeResponsable;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.ProcedenciaDelValor;
import com.controllocal.service.soporte.ValorEntrante;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.FiltrosDeListadoInmobiliario;
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

import static com.controllocal.service.soporte.EntradasDeValores.*;

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
 * rutina: si falla una titularidad declarada, un atributo obligatorio, el
 * tenant, la operacion o cualquier invariante, no puede quedar media
 * propiedad. Una propiedad que aun no conoce titular, partida o encargo se
 * conserva con esos datos FALTANTES; cuando se declara una operacion, su
 * importe y moneda siguen siendo obligatorios para ese encargo.
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

    /** Tope por pagina, el mismo que publica el contrato del recurso. */
    private static final int MAXIMO_POR_PAGINA = 100;

    private static final String COMANDO_REGISTRO = "REGISTRAR_PROPIEDAD";
    private static final String COMANDO_EDICION = "EDITAR_PROPIEDAD";

    private static final String EVENTO_REGISTRADA = "PROPIEDAD_REGISTRADA";
    private static final String EVENTO_EDITADA = "PROPIEDAD_EDITADA";
    private static final String EVENTO_PRECIO = "PRECIO_AUTORIZADO";
    /** El traspaso de autoridad es un hecho, y como tal deja evento (P0-2). */
    private static final String EVENTO_RESPONSABLE = "PROPIEDAD_RESPONSABLE_ASIGNADO";

    private final PropiedadRepository propiedades;
    private final PersonaRolRepository roles;
    private final DetalleAgenteRepository agentes;
    private final com.controllocal.service.soporte.ResolverDeDistrito distritos;
    private final org.springframework.context.ApplicationEventPublisher cambiosComerciales;
    private final TitularidadPropiedadRepository titularidades;
    private final CaptacionRepository captaciones;
    private final PrecioPropiedadRepository precios;
    private final EventoDominioRepository eventos;
    private final BorradorCapturaRepository borradores;
    private final AtributosGobernados gobierno;
    /** El enrutador del OTRO sujeto. Nunca se le pide nada de la propiedad. */
    private final AtributosDeEncargo condiciones;
    private final LectorPorAutoridad lector;
    private final ComandosIdempotentes comandos;
    private final Documentos documentos;
    private final Transiciones transiciones;
    private final ActividadDeLaPropiedad actividad;
    private final AnunciosDeLosEncargos publicaciones;
    /** Quien puede escribir la propiedad, y quien cada encargo (P0). */
    private final AutoridadDePropiedad autoridad;
    /** Y quien puede RECIBIRLA: los candidatos que el Core ofrece (D-P0-7/D-P0-12). */
    private final ElegibilidadDeResponsable elegibilidad;

    /**
     * La unica busqueda sobre la cartera, compartida con {@code /locales}.
     *
     * <p>Antes de la normalizacion este servicio llevaba su propia consulta -un
     * OR de cuatro `like` que cruzaba tres tablas- y por eso no usaba ni uno de
     * los indices trigrama que RC-003 dejo puestos. Ya no hay dos busquedas.
     */
    private final MotorBusquedaInmobiliaria motor;

    /**
     * <b>La galeria, por su unico servicio</b> (WP-C).
     *
     * <p>La ficha no lee {@code foto_propiedad} por su cuenta aunque seria una
     * consulta trivial: el invariante «la portada es la foto con orden 1» vive
     * en un sitio, y el segundo lector es como se pierde -- no porque haga algo
     * distinto, sino porque deja de enterarse de los cambios del primero.
     */
    private final FotoPropiedadService fotos;
    private final UnidadRelacionadaService unidadesRelacionadas;
    private final EncargoService encargos;
    private final SuficienciaInmueble suficiencia;
    private final SuficienciaRegistrada suficienciaRegistrada;
    private final ValidadorRespuestaCaptura validadorRespuestas;
    private final jakarta.persistence.EntityManager entidades;

    // Las cuatro tablas de valor gobernado ya no se inyectan aqui (4.P). Este
    // caso de uso ORQUESTA -- decide que se escribe, en que orden y dentro de
    // que transaccion -- y el que ESCRIBE es el enrutador de cada sujeto, que es
    // el mismo que anota de donde salio cada valor. Mientras hubiera dos
    // escritores habria un camino por el que un dato entra sin procedencia, y
    // eso no se arregla acordandose: se arregla quitando el camino.

    public PropiedadUniversalServiceImpl(PropiedadRepository propiedades, PersonaRolRepository roles,
                                         DetalleAgenteRepository agentes, com.controllocal.service.soporte.ResolverDeDistrito distritos,
                                         TitularidadPropiedadRepository titularidades,
                                         CaptacionRepository captaciones,
                                         PrecioPropiedadRepository precios,
                                         EventoDominioRepository eventos,
                                         BorradorCapturaRepository borradores,
                                         AtributosGobernados gobierno,
                                         AtributosDeEncargo condiciones,
                                         LectorPorAutoridad lector,
                                         ComandosIdempotentes comandos, Documentos documentos,
                                         Transiciones transiciones,
                                         ActividadDeLaPropiedad actividad,
                                         AnunciosDeLosEncargos publicaciones,
                                         AutoridadDePropiedad autoridad,
                                         ElegibilidadDeResponsable elegibilidad,
                                         FotoPropiedadService fotos,
                                         MotorBusquedaInmobiliaria motor,
                                         UnidadRelacionadaService unidadesRelacionadas, EncargoService encargos, SuficienciaInmueble suficiencia,
                                         SuficienciaRegistrada suficienciaRegistrada,ValidadorRespuestaCaptura validadorRespuestas,
                                         jakarta.persistence.EntityManager entidades,org.springframework.context.ApplicationEventPublisher cambiosComerciales) {
        this.cambiosComerciales=cambiosComerciales;
        this.entidades=entidades;
        this.suficiencia = suficiencia;
        this.suficienciaRegistrada=suficienciaRegistrada;
        this.validadorRespuestas=validadorRespuestas;
        this.encargos = encargos;
        this.fotos = fotos;
        this.motor = motor;
        this.unidadesRelacionadas = unidadesRelacionadas;
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
        this.lector = lector;
        this.comandos = comandos;
        this.documentos = documentos;
        this.transiciones = transiciones;
        this.actividad = actividad;
        this.publicaciones = publicaciones;
        this.autoridad = autoridad;
        this.elegibilidad = elegibilidad;
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
        if(comando.atributos()!=null&&comando.atributos().stream().anyMatch(v->v!=null&&v.captura()!=null))
            throw new ReglaNegocioException("Los metadatos del alta se aportan en el borrador de captura; la corrección explícita corresponde a la edición física.");
        String tipoPropiedad = tipoValidado(comando.tipoPropiedad());
        Procedencia procedencia = Procedencia.oPantalla(comando.procedencia());

        // La huella se calcula sobre lo que DEFINE el comando. Si cambia
        // cualquiera de estos campos, ya no es el mismo comando y una clave
        // repetida deja de ser un reintento.
        Map<String, Object> comandoParaHuella = new LinkedHashMap<>();
        comandoParaHuella.put("tipo", tipoPropiedad);
        comandoParaHuella.put("codigo", valorEnHuella(comando.codigo()));
        comandoParaHuella.put("uso", valorEnHuella(comando.uso()));
        comandoParaHuella.put("descripcion", valorEnHuella(comando.descripcion()));
        comandoParaHuella.put("ubicacion", ubicacionEnHuella(comando.ubicacion()));
        comandoParaHuella.put("titulares", titularesEnHuella(comando.titulares()));
        comandoParaHuella.put("atributos", atributosEnHuella(comando.atributos()));
        comandoParaHuella.put("operaciones", operacionesEnHuella(comando.operaciones()));
        comandoParaHuella.put("idBorrador", valorEnHuella(comando.idBorrador()));
        comandoParaHuella.put("origen", valorEnHuella(comando.origen()));
        String huella = documentos.huellaDe(comandoParaHuella);

        Optional<ComandoIdempotente> yaHecho =
                comandos.buscar(actor, comando.claveIdempotencia(), COMANDO_REGISTRO, huella);
        if (yaHecho.isPresent()) {
            // El reintento recibe LO MISMO que el primer intento. Si aqui se
            // devolviera un conflicto, un canal conversacional le diria al
            // usuario que fallo algo que en realidad salio bien.
            consultarPropiedad(yaHecho.get().getEntidadId(), actor);
            return reconstruir(yaHecho.get());
        }

        List<OperacionSolicitada> operaciones = operacionesValidadas(comando.operaciones());
        // El `piso` del hueco `ubicacion` entra AQUI, entre los atributos, antes
        // de que se compruebe nada y mucho antes de que se escriba nada: es una
        // clave gobernada y tiene que recorrer el mismo camino que las demas.
        Map<String, ValorAtributo> valores = conElPisoGobernado(actor.idOrganizacion(),
                atributosValidados(comando.atributos()), comando.ubicacion());
        List<Titular> titulares = titularesValidados(comando.titulares());

        // CAT-E11/18: identificar el registro físico no exige mínimos comerciales.
        // Los datos materiales se comprueban al confirmar/habilitar el Encargo.
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
        propiedad.setUso(usoValidado(comando.uso()));
        propiedad.setDescripcion(comando.descripcion());
        aplicarUbicacion(propiedad, ubicacion);
        incorporarMetadatosDeCaptura(propiedad, comando, actor);
        exigirIdentificacion(tipoPropiedad, comando.uso(), ubicacion,
                suficienciaRegistrada.contexto(propiedad.getContextoCaptura()));
        validarZonificacionDeAlta(propiedad, valores, actor);

        // El enrutador conserva la autoridad de cada valor conocido; C2 admite
        // área desconocida y no fabrica cero para satisfacer una columna.
        List<ValorEntrante> entrantes = entrantes(valores, procedencia);
        gobierno.aplicarEstructuralesAlAlta(actor.idOrganizacion(), propiedad, entrantes);

        // Proyección comercial sólo cuando se declaró una operación con precio;
        // un registro físico sin Encargo conserva ausentes ambos componentes.
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

        // La MISMA estrategia que `/locales`, con las tres ramas de texto,
        // los filtros propios y el orden descendente del listado universal.
        // Lo que decide que ids entran es el motor; lo unico que queda aqui es
        // la fila universal y sus encargos, que es lo que de verdad distingue a
        // este listado del heredado.
        CriterioBusquedaInmobiliaria criterio = FiltrosDeListadoInmobiliario.dePropiedades(
                actor.idOrganizacion(), autoridad.alcanceDeLectura(actor).rolesAgente(), filtros.texto(), filtros.estado(),
                filtros.tipoPropiedad() == null || filtros.tipoPropiedad().isBlank()
                        ? null : tipoValidado(filtros.tipoPropiedad()),
                filtros.distrito(),
                exigidas.contains(OperacionInmobiliaria.VENTA),
                exigidas.contains(OperacionInmobiliaria.ALQUILER),
                filtros.pagina(), filtros.tamano(), MAXIMO_POR_PAGINA);
        ConjuntoDeCandidatos candidatos = motor.resolver(criterio);

        List<Long> ids = candidatos.ids();
        List<PropiedadListado> encontradas = candidatos.vacio() ? List.of()
                : candidatos.ordenadas(
                        propiedades.buscarUniversalPorIds(actor.idOrganizacion(), ids),
                        PropiedadListado::getId);
        Map<Long, List<EncargoEnLista>> porPropiedad = encargosDeLaPagina(actor, ids);

        List<FilaPropiedad> filas = encontradas.stream()
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
        return new Pagina<>(filas, candidatos.total());
    }

    @Override
    @Transactional(readOnly = true)
    public OpcionesDeFiltro opcionesDeFiltro(Actor actor) {
        return new OpcionesDeFiltro(propiedades.distritosConCartera(actor.idOrganizacion(),
                autoridad.alcanceDeLectura(actor).paramRolesArray()));
    }

    /** Los encargos vivos de toda la pagina, en una consulta. */
    private Map<Long, List<EncargoEnLista>> encargosDeLaPagina(Actor actor, List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<EncargoEnLista>> porPropiedad = new LinkedHashMap<>();
        for (Captacion encargo : captaciones.encargosVivosParaLectura(actor.idOrganizacion(), ids,
                autoridad.alcanceDeLectura(actor).paramRoles())) {
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
    @Override
    @Transactional(readOnly = true)
    public FichaPropiedadUniversal consultar(long idPropiedad, Actor actor) {
        return ficha(actor, consultarPropiedad(idPropiedad, actor));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResultadoRegistro> consultarResultadoRegistro(String claveIdempotencia, Actor actor) {
        if (!actor.esAgente()) throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
        return comandos.consultarResultado(actor, claveIdempotencia, COMANDO_REGISTRO).map(previo -> {
            if (!Propiedad.ENTIDAD_TIPO.equals(previo.getEntidadTipo()) || previo.getEntidadId() == null) {
                throw new IllegalStateException("El registro de comando no corresponde a una Propiedad.");
            }
            consultarPropiedad(previo.getEntidadId(), actor); // Autoridad vigente; sin ficha, fotos o historia.
            return reconstruir(previo);
        });
    }

    private Propiedad consultarPropiedad(long idPropiedad, Actor actor) {
        return autoridad.buscarParaLectura(actor, idPropiedad)
                // 404 y no 403 cuando el id es de otro tenant: decir "existe
                // pero no puedes" ya seria filtrar la cartera del vecino.
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));
    }

    @Override
    @Transactional(readOnly = true)
    public CapacidadSobrePropiedad capacidadSobre(long idPropiedad, String atributo, Actor actor) {
        Propiedad p = consultarPropiedad(idPropiedad, actor);
        boolean reconocida = true;
        boolean editable = true;
        if (atributo != null) {
            Map<String, CatalogoAtributo> definiciones = gobierno.definicionesParaLeer(
                    actor.idOrganizacion(), p.getTipoInmueble(), Set.of(atributo));
            CatalogoAtributo definicion = definiciones.get(atributo);
            reconocida = definicion != null || lector.de(actor.idOrganizacion(), p).claves().contains(atributo);
            editable = ContratoDeEscritura.dePropiedad(definicion, p.getTipoInmueble()).editable();
        }
        return new CapacidadSobrePropiedad(p.getId(), AtributosGobernados.nombreDelTipo(p.getTipoInmueble()),
                p.estadoActual(), autoridad.puedeEditar(actor, p), reconocida, editable);
    }

    // ==================================================================
    // Edicion
    // ==================================================================

    @Override
    @Transactional
    public ResultadoEdicion editar(long idPropiedad, ComandoEdicion comando, Actor actor) {
        if(comando==null) throw new ReglaNegocioException("El comando de edición es obligatorio.");
        Propiedad propiedad=propiedades.bloquearParaEscritura(actor.idOrganizacion(),idPropiedad)
                .orElseThrow(()->new NoEncontradoException("Propiedad"));
        // Un contexto de persistencia que leyó antes no puede conservar una versión antigua tras esperar el lock.
        entidades.refresh(propiedad,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        Procedencia procedencia=Procedencia.oPantalla(comando.procedencia());
        // El replay es lectura del recibo original; no pide conservar la facultad de editar.
        consultarPropiedad(idPropiedad,actor);
        // RF013/AC053: esta entrada sólo corrige el físico. AC066 conserva el único escritor del pacto.
        if(comando.operaciones()!=null&&!comando.operaciones().isEmpty()
                ||comando.condiciones()!=null&&!comando.condiciones().isEmpty())
            throw new ReglaNegocioException("Los términos y condiciones del Encargo se editan por su identificador y versión en /encargos/{id}/condiciones.");
        boolean tocaFisico=comando.descripcion()!=null||comando.ubicacion()!=null||comando.titulares()!=null
                ||comando.contextoCaptura()!=null||comando.atributos()!=null&&!comando.atributos().isEmpty()
                ||comando.atributosABorrar()!=null&&!comando.atributosABorrar().isEmpty();
        Map<String,Object> entrada=new LinkedHashMap<>();
        entrada.put("idPropiedad",idPropiedad);entrada.put("descripcion",valorEnHuella(comando.descripcion()));
        entrada.put("ubicacion",ubicacionEnHuella(comando.ubicacion()));entrada.put("titulares",titularesEnHuella(comando.titulares()));
        entrada.put("atributos",atributosEnHuella(comando.atributos()));entrada.put("borrados",clavesEnHuella(comando.atributosABorrar()));
        entrada.put("operaciones",operacionesEnHuella(comando.operaciones()));entrada.put("condiciones",comando.condiciones());
        entrada.put("contextoCaptura",contextoEnHuella(comando.contextoCaptura()));
        entrada.put("versionEsperada",comando.versionEsperada());entrada.put("motivoRetiro",comando.motivoRetiro());
        String huella=com.controllocal.service.soporte.Idempotencia.huellaExacta(documentos.objetoCanonico(entrada));
        // La sintaxis de clave sigue siendo un error de entrada, no un conflicto de contenido.
        com.controllocal.service.soporte.Idempotencia.normalizar(comando.claveIdempotencia());
        Optional<ComandoIdempotente> previo;
        try { previo=comandos.buscar(actor,comando.claveIdempotencia(),COMANDO_EDICION,huella); }
        catch(ReglaNegocioException reutilizada) {
            throw new com.controllocal.service.excepcion.ConflictoException("La clave ya identifica otra edición; no se reinterpretó su contenido.");
        }
        if(previo.isPresent()) return reconstruirEdicion(previo.get(),idPropiedad);
        if(tocaFisico) {
            autoridad.exigirEdicion(actor,propiedad);
            if(comando.versionEsperada()==null||comando.versionEsperada()<0)
                throw new ReglaNegocioException("La versión física observada es obligatoria para editar el físico.");
            if(comando.versionEsperada()!=propiedad.getVersionFisica())
                throw new com.controllocal.service.excepcion.ConflictoException("El físico cambió; consulta su versión antes de corregirlo.");
        }
        long versionAnterior=propiedad.getVersionFisica();
        ValoresGobernados valoresAnteriores=lector.de(actor.idOrganizacion(),propiedad);
        ResultadoEdicion fisicoAnterior=reciboFisico(propiedad,valoresAnteriores,List.of());
        // La identidad del comando usa lo solicitado, no una retirada derivada
        // del estado posterior; un replay no vuelve a evaluar ni escribir el efecto.
        var captura=prepararEdicionFisica(propiedad,comando,actor);
        Map<String,ValorAtributo> valores=conElPisoGobernado(actor.idOrganizacion(),atributosDeEdicion(captura.valores()),comando.ubicacion());
        List<String> aBorrar=clavesABorrar(captura.retiradas(),valores);

        String metadatosAnteriores = propiedad.getMetadatosAtributos();
        String contextoAnterior=propiedad.getContextoCaptura();
        boolean tocaAtributos = !valores.isEmpty() || !aBorrar.isEmpty()||!captura.metadatos().isEmpty();
        if(aBorrar.stream().anyMatch(k->valorFisicoVigente(k,propiedad,valoresAnteriores))
                &&(comando.motivoRetiro()==null||comando.motivoRetiro().isBlank()))
            throw new ReglaNegocioException("El retiro de un valor físico vigente necesita motivo.");
        if(tocaFisico) {
            if(propiedades.incrementarVersionFisicaSi(actor.idOrganizacion(),idPropiedad,comando.versionEsperada())!=1)
                throw new com.controllocal.service.excepcion.ConflictoException("El físico cambió; la edición no sobrescribe otra versión.");
            // Antes de mutar datos: adoptar el contador real sin descartar cambios de este comando.
            entidades.refresh(propiedad,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
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
        ValoresGobernados valoresDespues=tocaAtributos?lector.de(actor.idOrganizacion(),propiedad):valoresAnteriores;
        if (tocaAtributos) actualizarMetadatosFisicos(propiedad,valoresAnteriores,valoresDespues,valores);
        if(!captura.metadatos().isEmpty()){
            var metadatos=suficienciaRegistrada.metadatos(propiedad.getMetadatosAtributos());
            metadatos.putAll(captura.metadatos());propiedad.setMetadatosAtributos(suficienciaRegistrada.metadatosJson(metadatos));
        }
        if(comando.contextoCaptura()!=null||captura.metadatos().keySet().stream().anyMatch(k->k.startsWith("contexto.")))
            propiedad.setContextoCaptura(suficienciaRegistrada.contextoJson(captura.contexto()));
        propiedades.saveAndFlush(propiedad);
        List<String> afectadas=clavesFisicasAfectadas(comando,valores,aBorrar,captura.metadatos().keySet());
        ResultadoEdicion resultado=reciboFisico(propiedad,valoresDespues,afectadas);
        Map<String,Object> payload=comoMapaDeResultado(resultado);
        String resultadoCanonico=documentos.objetoCanonico(Map.of("resultado",payload));
        // La primera respuesta usa el mismo contrato tipado que la restauración durable.
        resultado=documentos.comoObjeto(documentos.objetoCanonico(payload),ResultadoEdicion.class);
        Map<String,Object> evento=new LinkedHashMap<>();
        evento.put("idPropiedad",propiedad.getId());evento.put("versionFisicaAnterior",versionAnterior);
        evento.put("versionFisicaActual",propiedad.getVersionFisica());evento.put("clavesAfectadas",afectadas);
        evento.put("motivoRetiro",comando.motivoRetiro());evento.put("fisicoAnterior",fisicoAnterior);evento.put("fisicoActual",resultado);
        evento.put("metadatosAnteriores",metadatosAnteriores);evento.put("metadatosActuales",propiedad.getMetadatosAtributos());
        evento.put("contextoAnterior",contextoAnterior);evento.put("contextoActual",propiedad.getContextoCaptura());
        anotarEvento(actor,EVENTO_EDITADA,Propiedad.ENTIDAD_TIPO,propiedad.getId(),procedencia,evento);
        comandos.registrar(actor,comando.claveIdempotencia(),COMANDO_EDICION,huella,Propiedad.ENTIDAD_TIPO,
                propiedad.getId(),procedencia,documentos.objetoCanonico(Map.of("version",1,"resultado",payload,
                        "huellaResultado",com.controllocal.service.soporte.Idempotencia.huellaExacta(resultadoCanonico))));
        return resultado;
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
     *
     * <h2>UN solo hecho, o ninguno (D-P0-10)</h2>
     * Cambiar el responsable, dejar el saliente y el destino en el expediente
     * con su actor, su rol y su motivo, y anotar el evento son <b>partes de un
     * mismo hecho</b>, no tres efectos que se puedan dar por separado. Van en
     * esta transaccion y si cualquiera falla se deshacen todas: un responsable
     * cambiado sin traza deja una autoridad que nadie puede explicar, y una
     * traza sin cambio afirma un traspaso que no ocurrio. Que sea verdad no lo
     * dice el {@code @Transactional} sino {@code CausalidadDelTraspaso}
     * {@code IntegrationTest}, que rompe a proposito cada una de las escrituras
     * y comprueba que no queda ninguna de las otras.
     *
     * <h2>Y parte del estado que el actor vio (D-P0-9)</h2>
     * El {@link ResponsableObservado} entra desde el comando y llega intacto a
     * la autoridad: este metodo <b>no lo deduce de la fila que acaba de
     * cargar</b>, que es precisamente el estado nuevo que puede haber escrito
     * otro. Deducirlo aqui haria que el 409 no ocurriera nunca.
     */
    @Override
    @Transactional
    public TraspasoDeResponsable asignarResponsable(long idPropiedad, long idRolAgente,
                                                    String motivo, ResponsableObservado observado,
                                                    Actor actor) {
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));

        AsignacionResponsablePropiedad fila =
                autoridad.asignar(actor, propiedad, idRolAgente, motivo, observado);
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

    /**
     * <b>Los destinos elegibles para ESTA propiedad y ESTE actor</b> (D-P0-12).
     *
     * <p>Tres pasos, y el orden es la mitad de la decision:
     * <ol>
     *   <li>la <b>frontera de tenant</b>, cargando la fila por
     *       {@code (organizacion, id)} — un id de otra corredora es 404 y no una
     *       lista vacia;</li>
     *   <li>la <b>misma pregunta que apaga el boton</b> en la ficha
     *       ({@code puedeIniciarTraspaso}). Si el actor no puede iniciar el
     *       traspaso de esta propiedad, tampoco tiene que ver a quien se la
     *       podria dar: seria ofrecerle el paso siguiente de algo que no puede
     *       empezar. Y es 403, no una lista vacia — "no hay candidatos" y "no te
     *       corresponde" son dos respuestas distintas;</li>
     *   <li>y los candidatos, resueltos en la base con las cinco condiciones de
     *       D-P0-7.</li>
     * </ol>
     *
     * <p>El POST revalida <b>todo</b> despues: esta lista no autoriza nada, y
     * entre pedirla y usarla un agente puede quedar desactivado.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<CandidatoResponsable> candidatosAResponsable(long idPropiedad, String texto,
                                                               int pagina, int tamano,
                                                               Actor actor) {
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));
        if (!autoridad.puedeIniciarTraspaso(actor, propiedad)) {
            throw new AccesoNoAutorizadoException(
                    "No puedes cambiar quien responde por esta propiedad, asi que tampoco hay "
                            + "destinos que ofrecerte. Es el mismo motivo por el que su ficha no "
                            + "te ofrece el traspaso: o no es tu banda, o responde hoy ante un "
                            + "agente que no supervisas.");
        }
        Page<DetalleAgente> page = elegibilidad.candidatos(actor, propiedad, texto,
                PageRequest.of(Math.max(0, pagina - 1), Math.max(1, Math.min(100, tamano))));
        return new Pagina<>(page.getContent().stream()
                .map(agente -> new CandidatoResponsable(agente.getId(),
                        nombreDe(agente.getRol()), agente.getCodigoAgente(),
                        agente.getZonaAsignada()))
                .toList(), page.getTotalElements());
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

    // ==================================================================
    // Escritura, pieza a pieza
    // ==================================================================

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
     * cambio nada, y el gate de conservacion lo veia en todos los tipos:
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
        EncargoService.Condiciones pacto = new EncargoService.Condiciones(
                solicitada.importe(), solicitada.moneda(),
                operacion == OperacionInmobiliaria.ALQUILER ? "MENSUAL" : null,
                solicitada.tipoComision(), solicitada.baseCalculo(), solicitada.valorComision(),
                null, solicitada.tratamientoIgv(), null, null, null, null, null, null, List.of(), null,null);
        EncargoService.Apertura apertura = encargos.abrir(new EncargoService.DatosApertura(
                propiedad.getId(), agente.getId(), null, operacion, solicitada.inicioEncargo(),
                solicitada.finEncargo(), solicitada.exclusividad(), null, pacto, procedencia,
                solicitada.condiciones(),solicitada.origen()), actor);
        return apertura.id();
    }

    // ==================================================================
    // Lectura, pieza a pieza
    // ==================================================================

    private FichaPropiedadUniversal ficha(Actor actor, Propiedad propiedad) {
        long idOrganizacion = actor.idOrganizacion();
        long idPropiedad = propiedad.getId();
        String tipo = propiedad.getTipoInmueble();

        List<TitularFicha> titulares = titularesFisicos(idPropiedad);

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
                    ContratoDeEscritura.dePropiedad(definicion, tipo,
                            rotuloDeLaSustituta(definicion, definiciones))));
        }
        ordenarConLosRetiradosAlFinal(valores, definiciones);

        // Encargos autorizados vivos y cerrados. Leer el inmueble físico no
        // concede condiciones, anuncios ni episodios comerciales de otro ámbito.
        List<Captacion> todos = ordenados(captaciones.encargosParaLectura(idOrganizacion, idPropiedad,
                autoridad.alcanceDeLectura(actor).paramRoles()));
        List<Long> idsEncargos = todos.stream().map(Captacion::getId).toList();
        List<PrecioPropiedad> serie = idsEncargos.isEmpty() ? List.of()
                : precios.findByIdPropiedadAndIdCaptacionInOrderByFechaAscIdAsc(idPropiedad, idsEncargos);

        // Los anuncios de los Encargos autorizados, en una consulta por lote.
        // Mismo patron con el que el listado cuelga los encargos de una pagina.
        Map<Long, List<PublicacionService.FichaPublicacion>> anuncios =
                publicaciones.deEncargos(idOrganizacion, todos);

        // Las condiciones de los Encargos autorizados en una consulta, igual que los
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

        // ------------------------------------------------------------------
        // D-P0-6: que HISTORIA puede leer quien pregunta
        // ------------------------------------------------------------------
        //
        // C1/RF007: el recurso físico ya fue autorizado; la consulta de Encargos
        // también cerró el alcance antes de cargar condiciones e historia.
        //
        // Jackson esta configurado NON_NULL, asi que un bloque nulo NO VIAJA por
        // el cable: en el SPA llega como `undefined`. Eso es lo que el cliente
        // tiene que leer como «no disponible para ti», y NO como «vacio» -- una
        // propiedad sin historia y una propiedad cuya historia no te corresponde
        // se pintan distinto, y la diferencia esta en que el campo exista.
        List<Captacion> visibles = todos;
        Set<Long> idsVisibles = visibles.stream().map(Captacion::getId)
                .collect(java.util.stream.Collectors.toSet());
        boolean leeLaHistoria = autoridad.puedeLeerHistoriaDeLaPropiedad(actor, propiedad);

        // Los encargos se arman DESPUES de esas dos listas y no antes: la
        // capacidad `publicacionGestionable` de cada bloque necesita la deuda de
        // la PROPIEDAD --que es una sola para todos-- ademas de la suya. Es la
        // unica razon del orden.
        List<EncargoFicha> encargos = visibles.stream()
                .map(encargo -> fichaDeEncargo(actor, encargo, serie,
                        anuncios.getOrDefault(encargo.getId(), List.of()),
                        idOrganizacion, tipo,
                        pactadas.getOrDefault(encargo.getId(), ValoresGobernados.vacio()),
                        faltanParaPublicar,
                        idsVisibles.contains(encargo.getId())))
                .toList();

        boolean seOfrece = encargos.stream().anyMatch(EncargoFicha::vivo);

        // La HISTORIA del inmueble se compone de los encargos que este actor
        // puede ver, y de nada mas. Es la lectura CONJUNTA de las dos filas de
        // D-P0-6: la fila de la propiedad dice si puede leer historia, y la del
        // encargo dice de cuales. Un responsable que solo tiene el alquiler no
        // puede leer «a cuanto se intento vender» por la puerta de la historia
        // agregada, que es justo la puerta que un bloque agregado abre sin que
        // se note.
        //
        // Y la serie se filtra a esos encargos antes de agregarla: un hito SIN
        // encargo no entra en la historia por su cuenta (`historiaDe` ya lo
        // descarta, porque sin episodio no se puede decir de que operacion era).
        HistoriaComercial historia = null;
        if (leeLaHistoria && !visibles.isEmpty()) {
            List<PrecioPropiedad> serieVisible = serie.stream()
                    .filter(precio -> idsVisibles.contains(precio.getIdCaptacion()))
                    .toList();
            HistoriaComercial candidata = historiaDe(visibles, serieVisible);
            // Sin ningun episodio no hay historia que contar: devolver un bloque
            // con dos listas vacias diria «esta propiedad no tiene pasado», que
            // es una afirmacion, no una ausencia.
            historia = candidata.porOperacion().isEmpty() && candidata.linea().isEmpty()
                    ? null : candidata;
        }

        // Los hechos comerciales usan los Encargos visibles. Las interacciones
        // se reautorizan por Propiedad y pueden preceder al primer Encargo.
        ActividadPropiedad actividadVisible = actividad.de(actor, idPropiedad, visibles);
        if (visibles.isEmpty() && actividadVisible.interacciones().isEmpty()) actividadVisible = null;

        // La galería revalida el mismo ámbito físico; sus bytes se autorizan
        // además por recurso, finalidad y versión al descargarlos.
        List<FotoPropiedadService.FotoDePropiedad> galeria = fotos.listar(idPropiedad, actor);
        List<UnidadRelacionadaService.FichaUnidadRelacionada> relacionadas =
                unidadesRelacionadas.deLaPropiedad(idPropiedad, actor);

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
                // vez de como episodios sueltos. Sin consultas de mas, y acotada
                // a lo que este actor puede leer (D-P0-6).
                historia,
                actividadVisible,
                // La galeria (WP-C). Una consulta, por el MISMO servicio que la
                // escribe: la ficha no vuelve a leer `foto_propiedad` por su
                // cuenta -- dos lectores del mismo dato divergen, y el primero
                // que se olvidara del orden dejaria la portada al azar.
                galeria, portadaDe(galeria),
                Fechas.local(propiedad.getFechaRegistro()),
                relacionadas,
                responsabilidadDe(actor, propiedad),propiedad.getVersionFisica());
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
     * <b>La portada: la foto con {@code orden = 1}</b> (V91).
     *
     * <p>No es «la primera de la lista»: es la que ocupa la posicion 1, y se
     * comprueba. Con el invariante 1..N las dos frases coinciden siempre, y
     * escribir la segunda haria que el dia que el invariante se rompiera la
     * ficha siguiera afirmando una portada — la que hubiera quedado delante.
     */
    private static FotoPropiedadService.FotoDePropiedad portadaDe(
            List<FotoPropiedadService.FotoDePropiedad> galeria) {
        return galeria.stream().filter(foto -> foto.orden() == 1).findFirst().orElse(null);
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
     * <p>Es <b>la constante del dominio</b> y no una copia suya. Habia una copia
     * aqui, con la misma lista y el mismo comentario, escrita antes de que WP-A
     * subiera la definicion a {@code PrecioPropiedad}: dos listas idénticas de
     * lo que cuenta como «precio del inmueble», y la que se olvidara de un hito
     * nuevo habria empezado a dibujar otra linea sin que nada avisara.
     *
     * <p>{@code O} ofertado no entra: una oferta es lo que ofrecio un
     * interesado, no lo que pedia el propietario.
     */
    private static final Set<String> PEDIDOS = PrecioPropiedad.HITOS_PEDIDOS;

    /**
     * Lo que se CERRO de verdad. Solo {@code C}: {@code A} aceptado es un
     * acuerdo que todavia puede caerse antes de la firma.
     */
    private static final Set<String> CIERRES = Set.of(PrecioPropiedad.HITO_CIERRE);

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
                                        List<AtributoQueFalta> faltanDeLaPropiedad,
                                        boolean historicoVisible) {
        CondicionEconomicaCaptacion condicion = encargo.getCondicionEconomica();
        // Se calcula UNA vez y se usa dos: para publicarla en el bloque y para
        // decidir la capacidad. Pedirla dos veces abriria la puerta a que las dos
        // respuestas se separaran.
        List<AtributoQueFalta> faltanDelEncargo =
                faltanEnElEncargo(idOrganizacion, encargo, tipoPropiedad);
        // El HISTORICO es lo unico del bloque que D-P0-6 acota: cuando el actor
        // no puede leerlo, el campo llega NULO y por NON_NULL no viaja --
        // «no disponible para ti», que no es lo mismo que una lista vacia--.
        // Todo lo demas del encargo sigue viajando igual: el importe VIGENTE, sus
        // condiciones, sus anuncios y `puedeEditar`. Ocultar el bloque entero
        // seria confundir «no puedes ver lo que se pidio en 2023» con «este
        // encargo no existe», y hay pantallas que necesitan saber que existe.
        List<HitoFicha> historico = !historicoVisible ? null : serie.stream()
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
                anuncios, gestionDePublicacion(actor, encargo, faltanDeLaPropiedad, faltanDelEncargo),
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
                vigencia.estadoDato(), vigencia.editable(), vigencia.motivoNoEditable(),
                // RETIRADO es mas estrecho que HISTORICO, y por eso viaja aparte
                // (`N16`). `estadoDato = HISTORICO` cubre DOS situaciones: la
                // clave se retiro del catalogo, o sigue viva y dejo de aplicar a
                // este tipo. Solo la primera es una retirada, y solo de la
                // primera se puede decir cuando fue y quien la sustituye. Un
                // cliente que dedujera «historico = retirado» diria de
                // `area_terreno` en un terreno que se retiro, y es falso: se
                // sigue preguntando en una casa.
                definicion != null && !definicion.isActivo(),
                definicion == null ? null : definicion.getSustituidaPor());
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
                            encargo.getMotivoOperacion(),
                            rotuloDeLaSustituta(definicion, definiciones))));
        }
        // Con los retirados al final, igual que el bloque fisico (`N16`): una
        // condicion que ya no se pacta no puede quedarse en medio de las vivas
        // por su orden de catalogo.
        ordenarConLosRetiradosAlFinal(valores, definiciones);
        return List.copyOf(valores);
    }

    /**
     * <b>El orden de la ficha: primero lo vigente, y lo RETIRADO al final</b>
     * (`N16`).
     *
     * <p>Hasta aqui la lista se ordenaba solo por el {@code orden} del catalogo,
     * asi que una clave retirada aparecia <b>en medio de las vivas</b> —en su
     * sitio de siempre— con su mismo rotulo y su mismo tipo. Desde 5A la lectura
     * completa la definicion de las retiradas, que era lo que faltaba, y el
     * efecto secundario fue ese: un atributo indistinguible de uno editable que
     * el editor no ofrece, sin que nadie explique por que.
     *
     * <p>Se mueven al final y no se esconden. Esconderlos seria perder el dato en
     * la lectura, que es exactamente lo que 5A vino a arreglar; y dejarlos donde
     * estaban obliga a la pantalla a decidir que hacer con ellos, que es una
     * regla de negocio en el cliente.
     *
     * <p>Dentro de cada grupo, el orden de siempre: el del catalogo, y la clave
     * como desempate.
     */
    private static void ordenarConLosRetiradosAlFinal(List<AtributoFicha> valores,
                                                      Map<String, CatalogoAtributo> definiciones) {
        valores.sort(java.util.Comparator
                .comparing(AtributoFicha::retirado)
                .thenComparingInt(a -> definiciones.containsKey(a.clave())
                        ? definiciones.get(a.clave()).getOrden() : Integer.MAX_VALUE)
                .thenComparing(AtributoFicha::clave));
    }

    /**
     * El <b>rotulo</b> de la clave que sustituyo a una retirada, si el catalogo
     * de esta lectura lo trae (`N16`).
     *
     * <p>Se resuelve contra el mapa que ya esta cargado y no con una consulta
     * nueva: seria una por atributo retirado. Cuando la sustituta no esta en el
     * mapa —porque no aplica a este tipo, o porque se declaro una clave que ya
     * no existe— se devuelve {@code null} y la frase usa la clave tal cual: no
     * se inventa un nombre.
     */
    private static String rotuloDeLaSustituta(CatalogoAtributo definicion,
                                              Map<String, CatalogoAtributo> definiciones) {
        if (definicion == null || definicion.getSustituidaPor() == null) {
            return null;
        }
        CatalogoAtributo sustituta = definiciones.get(definicion.getSustituidaPor());
        return sustituta == null ? null : sustituta.getRotulo();
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
    private GestionDePublicacion gestionDePublicacion(Actor actor, Captacion encargo,
                                                      List<AtributoQueFalta> faltanDeLaPropiedad,
                                                      List<AtributoQueFalta> faltanDelEncargo) {
        // No se publica lo que ya no se ofrece. Es la regla que ya estaba, y se
        // mira primero porque un encargo cerrado no se arregla completando datos.
        if (!Captacion.esVivo(encargo.estadoActual())) {
            return new GestionDePublicacion(false,
                    "El encargo " + encargo.getCodigoCaptacion() + " ya no esta vigente.");
        }

        var vigencia = encargos.consultarVigencia(encargo.getId(), LocalDate.now(java.time.ZoneId.of("America/Lima")), actor);
        if (!vigencia.habilitado()) return new GestionDePublicacion(false,
                "El Encargo no está habilitado: " + String.join(", ", vigencia.faltantes()));

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
                        "Tipo de propiedad desconocido: \"" + tipoPropiedad + "\". Tipos admitidos: "
                                + AtributosGobernados.tiposAdmitidos() + "."));
    }

    /** Un uso no declarado conserva su ausencia; el tipo no lo acredita. */
    private static String usoValidado(String uso) {
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
        return null;
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

    private void exigirIdentificacion(String tipo, String uso, Ubicacion ubicacion,
                                      SuficienciaInmueble.Contexto declarado) {
        Map<String, SuficienciaInmueble.Dato> datos = new LinkedHashMap<>();
        datos.put("distrito", SuficienciaInmueble.Dato.conocido(ubicacion.distrito()));
        datos.put("direccion", SuficienciaInmueble.Dato.conocido(ubicacion.direccion()));
        datos.put("referenciaInterna", SuficienciaInmueble.Dato.conocido(ubicacion.referenciaInterna()));
        datos.put("interiorUnidad", SuficienciaInmueble.Dato.conocido(ubicacion.interiorUnidad()));
        var contexto = new SuficienciaInmueble.Contexto(tipo, uso,
                declarado == null ? ubicacion.referenciaInterna() : declarado.objetoOfrecido(),
                null, Map.of(), Set.of());
        var evaluacion = suficiencia.evaluar(SuficienciaInmueble.Accion.IDENTIFICAR, contexto, datos);
        if (!evaluacion.suficiente()) {
            throw new ReglaNegocioException("Falta identificar el inmueble: " + evaluacion.faltantes().stream()
                    .map(SuficienciaInmueble.Faltante::clave).distinct().collect(java.util.stream.Collectors.joining(", ")));
        }
    }

    private static Ubicacion ubicacionValidada(Ubicacion ubicacion) {
        if (ubicacion == null) {
            throw new ReglaNegocioException("La ubicación debe declarar distrito y referencia suficiente del objeto.");
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
                    solicitada.condiciones(),solicitada.origen()));
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
        propiedad.setIdDistrito(distritos.idParaNombre(propiedad.getDistrito()));
    }

    private static <T> void siViene(T valor, Consumer<T> destino) {
        if (valor != null) {
            destino.accept(valor);
        }
    }

    private String codigoDePropiedad(long idOrganizacion, String declarado) {
        if (declarado != null && !declarado.isBlank()) {
            return declarado.trim();
        }
        // Correlativo por organizacion, como las captaciones (V6.3): cada
        // corredora numera desde 0001 y no ve el ritmo de las demas.
        propiedades.bloquearNumeracion(idOrganizacion);
        return "PROP-%04d".formatted(propiedades.countByOrganizacionId(idOrganizacion) + 1);
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
        var evento=procedencia.sellar(EventoDominio
                        .de(actor.idOrganizacion(), tipo, entidadTipo, entidadId,
                                actor.idRolOperativo(), procedencia.canal()))
                .con(documentos.objeto(cargaUtil));
        eventos.save(evento);
        if(EVENTO_EDITADA.equals(tipo)&&Propiedad.ENTIDAD_TIPO.equals(entidadTipo))
            cambiosComerciales.publishEvent(new com.controllocal.service.soporte.CambioDeOfertaComercial(
                    com.controllocal.service.soporte.CambioDeOfertaComercial.Recurso.PROPIEDAD,entidadId,actor,evento));
    }

    /**
     * El borrador que dio lugar al alta queda marcado como ejecutado, con la
     * entidad que produjo. Es lo que permite responder despues "esta propiedad
     * la registro KAIROS a partir de la conversacion del martes".
     */
    private void actualizarMetadatosFisicos(Propiedad p, ValoresGobernados antes, ValoresGobernados despues,
                                             Map<String,ValorAtributo> entradas) {
        Map<String,Object> metadatos = new LinkedHashMap<>(documentos.comoMapa(p.getMetadatosAtributos()));
        Set<String> claves = new LinkedHashSet<>(antes.claves()); claves.addAll(despues.claves());
        boolean cambio = false;
        for (String clave : claves) {
            if (Objects.equals(antes.valor(clave),despues.valor(clave))) continue;
            cambio = true;
            var dato = new LinkedHashMap<String,Object>();
            dato.put("estado",despues.valor(clave)==null ? "FALTANTE" : "CONOCIDO");
            ValorAtributo entrada = entradas.get(clave);
            if (entrada!=null && entrada.evidenciaRef()!=null) dato.put("evidenciaRef",entrada.evidenciaRef());
            if (entrada!=null && entrada.observadoEn()!=null) dato.put("observadoEn",entrada.observadoEn());
            // No adjudicar al valor nuevo el concepto/catálogo/evidencia de otro valor.
            metadatos.put(clave,dato);
        }
        if(cambio) p.setMetadatosAtributos(documentos.objetoCanonico(metadatos));
    }

    private record EdicionFisica(List<ValorAtributo> valores,List<String> retiradas,
            Map<String,MotorDeCaptura.MetadatoRespuesta> metadatos,SuficienciaInmueble.Contexto contexto) { }

    /** Una sola autoridad para el valor; los metadatos explícitos usan el contrato del borrador. */
    private EdicionFisica prepararEdicionFisica(Propiedad p,ComandoEdicion comando,Actor actor) {
        var entradas=atributosValidados(comando.atributos());
        var contexto= contextoFisico(p,comando.contextoCaptura());
        var valores=new LinkedHashMap<String,ValorAtributo>();
        var retiradas=new LinkedHashSet<String>(comando.atributosABorrar()==null?List.of():comando.atributosABorrar());
        var metadatos=new LinkedHashMap<String,MotorDeCaptura.MetadatoRespuesta>();
        var respuestasContextuales=new LinkedHashMap<String,MotorDeCaptura.Respuesta>();
        for(var entrada:entradas.entrySet()){
            String clave=entrada.getKey();ValorAtributo valor=entrada.getValue();
            if(valor.captura()==null&&!"zonificacion".equals(clave)){valores.put(clave,valor);continue;}
            if(retiradas.contains(clave))throw new ReglaNegocioException("Una respuesta de captura y atributosABorrar no se envían para la misma clave.");
            var c=valor.captura();String escalar=c==null||valor.valores()==null?valor.valor():documentos.lista(valor.valores());
            var estado=c==null?SuficienciaInmueble.EstadoDato.CONOCIDO:c.estado();
            if(estado!=SuficienciaInmueble.EstadoDato.CONOCIDO&&(valor.moneda()!=null||valor.valores()!=null))
                throw new ReglaNegocioException("Un estado sin valor conocido no admite moneda o lista ocultas.");
            var respuesta=new MotorDeCaptura.Respuesta(escalar,estado,valor.evidenciaRef(),c==null?null:c.concepto(),
                    c==null?null:c.referenciaCatalogo(),c==null?null:c.versionDefinicion(),valor.observadoEn(),c!=null&&c.noSabe());
            var meta=validadorRespuestas.validar(actor,clave,respuesta);
            if("zonificacion".equals(clave)&&estado==SuficienciaInmueble.EstadoDato.CONOCIDO){
                var revisada=validadorRespuestas.validarZonificacion(actor.idOrganizacion(),escalar,meta,contexto);
                meta=revisada.metadato();
            }
            metadatos.put(clave,meta);
            // Coordenada declarada del contexto, no otro atributo físico ni
            // un borrado de una clave inexistente en el catálogo.
            if(clave.startsWith("contexto.")){respuestasContextuales.put(clave,respuesta);continue;}
            if(meta.estado()==SuficienciaInmueble.EstadoDato.CONOCIDO)valores.put(clave,valor);
            else retiradas.add(clave);
        }
        contexto=validadorRespuestas.reconciliarContexto(suficienciaRegistrada.contexto(p.getContextoCaptura()),
                contexto,comando.contextoCaptura(),respuestasContextuales,metadatos);
        // Cambiar jurisdicción tampoco traslada a otro perfil la acreditación de la opción anterior.
        if(comando.contextoCaptura()!=null&&!entradas.containsKey("zonificacion")){
            var anteriores=lector.de(actor.idOrganizacion(),p);String valor=anteriores.texto("zonificacion");
            if(valor!=null){
                var meta=suficienciaRegistrada.metadatos(p.getMetadatosAtributos()).get("zonificacion");
                var revisada=validadorRespuestas.validarZonificacion(actor.idOrganizacion(),valor,meta,contexto);
                metadatos.put("zonificacion",revisada.metadato());
                if(revisada.valorConocido()==null)retiradas.add("zonificacion");
            }
        }
        return new EdicionFisica(List.copyOf(valores.values()),new ArrayList<>(retiradas),metadatos,contexto);
    }

    /** El alta técnica también conserva pendiente una opción que el catálogo no puede acreditar. */
    private void validarZonificacionDeAlta(Propiedad p,Map<String,ValorAtributo> valores,Actor actor){
        ValorAtributo valor=valores.get("zonificacion");
        if(valor==null)return;
        var metadatos=suficienciaRegistrada.metadatos(p.getMetadatosAtributos());
        var meta=metadatos.get("zonificacion");
        String escalar=valor.valor();
        if(meta==null)meta=validadorRespuestas.validar(actor,"zonificacion",new MotorDeCaptura.Respuesta(
                escalar,SuficienciaInmueble.EstadoDato.CONOCIDO,valor.evidenciaRef(),null,null,null,valor.observadoEn(),false));
        var revisada=validadorRespuestas.validarZonificacion(actor.idOrganizacion(),escalar,meta,
                suficienciaRegistrada.contexto(p,null,null));
        metadatos.put("zonificacion",revisada.metadato());
        p.setMetadatosAtributos(suficienciaRegistrada.metadatosJson(metadatos));
        if(revisada.valorConocido()==null)valores.remove("zonificacion");
    }

    private SuficienciaInmueble.Contexto contextoFisico(Propiedad p,SuficienciaInmueble.Contexto declarado){
        if(declarado!=null){
            if(declarado.tipoPropiedad()!=null&&!tipoValidado(declarado.tipoPropiedad()).equals(p.getTipoInmueble()))
                throw new ReglaNegocioException("El contexto conserva el tipo del inmueble identificado.");
            if(declarado.usoOfrecido()!=null&&!Objects.equals(usoValidado(declarado.usoOfrecido()),p.getUso()))
                throw new ReglaNegocioException("El contexto conserva el uso declarado del inmueble.");
            if(declarado.operacion()!=null)OperacionInmobiliaria.desde(declarado.operacion());
        }
        return suficienciaRegistrada.contexto(p,null,declarado);
    }

    private String contextoEnHuella(SuficienciaInmueble.Contexto contexto){
        if(contexto==null)return null;
        var mapa=documentos.comoMapa(suficienciaRegistrada.contextoJson(contexto));
        mapa.put("prestacionesOfrecidas",new java.util.TreeSet<>(contexto.prestacionesOfrecidas()));
        return documentos.objetoCanonico(mapa);
    }

    private void incorporarMetadatosDeCaptura(Propiedad propiedad, ComandoRegistro comando, Actor actor) {
        if (comando.idBorrador() == null) return;
        var b = borradores.bloquear(actor.idOrganizacion(), comando.idBorrador())
                .orElseThrow(() -> new NoEncontradoException("Captura"));
        if (!Objects.equals(b.getIdPersonaRol(),actor.idRolOperativo()))
            throw new AccesoNoAutorizadoException();
        if (!b.estaEnCurso()) throw new com.controllocal.service.excepcion.ConflictoException("La captura ya no está en curso.");
        var conocidos = documentos.comoMapa(b.getDatosConocidos());
        if (!Objects.equals(AtributosGobernados.codigoDelTipo(Objects.toString(conocidos.get("tipoPropiedad"),null)).orElse(null),
                propiedad.getTipoInmueble()))
            throw new com.controllocal.service.excepcion.ConflictoException("El tipo del registro difiere de la captura revisada.");
        // Un comando no puede reutilizar la procedencia de otro valor conocido.
        for (ValorAtributo valor : comando.atributos()==null ? List.<ValorAtributo>of() : comando.atributos()) {
            if (valor.valor()!=null && !Objects.equals(valor.valor(),Objects.toString(conocidos.get(valor.clave()),null)))
                throw new com.controllocal.service.excepcion.ConflictoException("El valor difiere de la captura revisada: " + valor.clave());
        }
        propiedad.setContextoCaptura(b.getContextoCaptura());
        propiedad.setMetadatosAtributos(b.getMetadatosRespuestas());
    }

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
        if (idDelResultado(resultado.get("idPropiedad")) != previo.getEntidadId()
                || !(resultado.get("codigo") instanceof String codigo) || codigo.isBlank()
                || !(resultado.get("idsEncargos") instanceof List<?> encargos)) {
            throw new IllegalStateException("El resultado persistido del registro está incompleto o es incompatible.");
        }
        return new ResultadoRegistro(previo.getEntidadId(), codigo,
                encargos.stream().map(PropiedadUniversalServiceImpl::idDelResultado).toList(), true);
    }

    private static long idDelResultado(Object valor) {
        if (valor instanceof Number numero) {
            try {
                long id = new BigDecimal(numero.toString()).longValueExact();
                if (id > 0) return id;
            } catch (NumberFormatException | ArithmeticException invalido) { /* Dato persistido incompatible. */ }
        }
        throw new IllegalStateException("El resultado persistido contiene una referencia incompatible.");
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String titularesEnHuella(List<Titular> titulares) {
        if (titulares == null) {
            return "<nulo>";
        }
        return titulares.stream()
                .map(titular -> titular == null ? "<nulo>"
                        : "id=" + valorEnHuella(titular.idRolPropietario())
                                + ";cuota=" + valorEnHuella(titular.cuota())
                                + ";representante=" + valorEnHuella(titular.representante()))
                .sorted()
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
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
            return "<nulo>";
        }
        return valores.stream()
                .map(valor -> valor == null ? "<nulo>"
                        : "clave=" + valorEnHuella(valor.clave())
                                + ";valor=" + valorEnHuella(valor.valor())
                                + ";moneda=" + valorEnHuella(valor.moneda())
                                + ";valores=" + valoresEnHuella(valor.valores())
                                + ";naturaleza=" + valorEnHuella(valor.naturaleza())
                                + ";confianza=" + valorEnHuella(valor.confianza())
                                + ";observadoEn=" + valorEnHuella(valor.observadoEn())
                                + ";evidenciaRef=" + valorEnHuella(valor.evidenciaRef())
                                + ";captura="+valorEnHuella(valor.captura()))
                .sorted()
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String operacionesEnHuella(List<OperacionSolicitada> operaciones) {
        if (operaciones == null) {
            return "<nulo>";
        }
        return operaciones.stream()
                .map(solicitada -> solicitada == null ? "<nulo>"
                        : "operacion=" + valorEnHuella(solicitada.operacion())
                                + ";importe=" + valorEnHuella(solicitada.importe())
                                + ";moneda=" + valorEnHuella(solicitada.moneda())
                                + ";tipoComision=" + valorEnHuella(solicitada.tipoComision())
                                + ";baseCalculo=" + valorEnHuella(solicitada.baseCalculo())
                                + ";valorComision=" + valorEnHuella(solicitada.valorComision())
                                + ";tratamientoIgv=" + valorEnHuella(solicitada.tratamientoIgv())
                                + ";exclusividad=" + valorEnHuella(solicitada.exclusividad())
                                + ";inicioEncargo=" + valorEnHuella(solicitada.inicioEncargo())
                                + ";finEncargo=" + valorEnHuella(solicitada.finEncargo())
                                + ";condiciones=" + atributosEnHuella(solicitada.condiciones()))
                .sorted()
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    /** Todos los campos de ubicación forman parte del comando de alta/edición. */
    private static String ubicacionEnHuella(Ubicacion ubicacion) {
        if (ubicacion == null) {
            return "<nulo>";
        }
        return "direccion=" + valorEnHuella(ubicacion.direccion())
                + ";distrito=" + valorEnHuella(ubicacion.distrito())
                + ";zonaUrbanizacion=" + valorEnHuella(ubicacion.zonaUrbanizacion())
                + ";latitud=" + valorEnHuella(ubicacion.latitud())
                + ";longitud=" + valorEnHuella(ubicacion.longitud())
                + ";interiorUnidad=" + valorEnHuella(ubicacion.interiorUnidad())
                + ";piso=" + valorEnHuella(ubicacion.piso())
                + ";referenciaInterna=" + valorEnHuella(ubicacion.referenciaInterna())
                + ";nombreEdificioGaleria=" + valorEnHuella(ubicacion.nombreEdificioGaleria());
    }

    private static String valoresEnHuella(List<String> valores) {
        if (valores == null) {
            return "<nulo>";
        }
        return valores.stream()
                .map(PropiedadUniversalServiceImpl::valorEnHuella)
                .sorted()
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String clavesEnHuella(List<String> claves) {
        return valoresEnHuella(claves);
    }

    /** Serializa null y valores delimitados sin colisiones de concatenación. */
    private static String valorEnHuella(Object valor) {
        if (valor == null) {
            return "<nulo>";
        }
        String texto = valor instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString()
                : valor.toString();
        return texto.length() + ":" + texto;
    }
    private List<TitularFicha> titularesFisicos(long propiedad) {
        return titularidades.vigentesDe(propiedad).stream().map(t->new TitularFicha(t.getRolPropietario().getId(),
                nombreDe(t.getRolPropietario()),t.getCuota(),t.isEsRepresentante(),t.getVigenteDesde())).toList();
    }
    private ResultadoEdicion reciboFisico(Propiedad p,ValoresGobernados leidos,List<String> claves) {
        var contexto=suficienciaRegistrada.contexto(p,null,null);
        var datos=suficienciaRegistrada.datos(p,leidos);
        SuficienciaInmueble.Evaluacion evaluacion;
        String estadoSuficiencia;
        try {
            evaluacion=suficiencia.evaluar(SuficienciaInmueble.Accion.IDENTIFICAR,contexto,datos);
            estadoSuficiencia="DISPONIBLE";
        } catch(RuntimeException falloInformativo) {
            // Esta recompensa pura no es una guarda: el hecho correcto y su recibo se conservan.
            evaluacion=null;estadoSuficiencia="FALLO_TECNICO";
        }
        return new ResultadoEdicion(p.getId(),p.getCodigo(),p.getVersionFisica(),
                AtributosGobernados.nombreDelTipo(p.getTipoInmueble()),p.getUso(),p.getDescripcion(),ubicacionDe(p),
                titularesFisicos(p.getId()),datos,contexto,suficienciaRegistrada.metadatos(p.getMetadatosAtributos()),claves,estadoSuficiencia,evaluacion,false);
    }
    private Map<String,Object> comoMapaDeResultado(ResultadoEdicion resultado) {
        var contenedor=documentos.comoMapa(documentos.objetoCanonico(Map.of("resultado",resultado)));
        if(!(contenedor.get("resultado") instanceof Map<?,?> crudo))throw new IllegalStateException("El recibo físico no es un objeto.");
        Map<String,Object> limpio=new LinkedHashMap<>();crudo.forEach((k,v)->limpio.put((String)k,v));return limpio;
    }
    private ResultadoEdicion reconstruirEdicion(ComandoIdempotente previo,long idPropiedad) {
        Map<String,Object> contenedor=documentos.comoMapa(previo.getResultado());
        if(!(contenedor.get("version") instanceof Number n)||n.intValue()!=1
                ||!(contenedor.get("resultado") instanceof Map<?,?> crudo))
            throw new IllegalStateException("El recibo original de edición física está incompleto.");
        Map<String,Object> resultado=new LinkedHashMap<>();crudo.forEach((k,v)->resultado.put((String)k,v));
        String canonico=documentos.objetoCanonico(Map.of("resultado",resultado));
        if(!Objects.equals(contenedor.get("huellaResultado"),com.controllocal.service.soporte.Idempotencia.huellaExacta(canonico)))
            throw new IllegalStateException("El recibo original de edición física perdió su huella.");
        ResultadoEdicion original=documentos.comoObjeto(documentos.objetoCanonico(resultado),ResultadoEdicion.class);
        if(original.idPropiedad()!=idPropiedad||!Propiedad.ENTIDAD_TIPO.equals(previo.getEntidadTipo())
                ||!Objects.equals(previo.getEntidadId(),idPropiedad)||original.versionFisica()<0)
            throw new IllegalStateException("El recibo físico no corresponde al recurso autorizado.");
        return original.comoReintento();
    }
    private static boolean valorFisicoVigente(String clave,Propiedad p,ValoresGobernados leidos) {
        if(leidos.tiene(clave))return true;
        return switch(clave) {
            case "descripcion"->p.getDescripcion()!=null;case "zonaUrbanizacion"->p.getZonaUrbanizacion()!=null;
            case "interiorUnidad"->p.getInteriorUnidad()!=null;case "referenciaInterna"->p.getReferenciaInterna()!=null;
            case "nombreEdificioGaleria"->p.getNombreEdificioGaleria()!=null;case "latitud"->p.getGeoLat()!=null;
            case "longitud"->p.getGeoLong()!=null;case "direccion"->p.getDireccion()!=null;case "distrito"->p.getDistrito()!=null;
            default->false;
        };
    }
    private static List<String> clavesFisicasAfectadas(ComandoEdicion c,Map<String,ValorAtributo> valores,
            List<String> retiradas,Set<String> metadatos) {
        Set<String> claves=new java.util.TreeSet<>(valores.keySet());claves.addAll(retiradas);claves.addAll(metadatos);
        if(c.descripcion()!=null)claves.add("descripcion");if(c.ubicacion()!=null)claves.add("ubicacion");
        if(c.titulares()!=null)claves.add("titulares");if(c.contextoCaptura()!=null)claves.add("contextoCaptura");
        return List.copyOf(claves);
    }

}
