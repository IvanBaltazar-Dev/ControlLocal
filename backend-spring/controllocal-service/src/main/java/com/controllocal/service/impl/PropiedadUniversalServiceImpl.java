package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.inmueble.AtributoPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.TitularidadPropiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.AtributoPropiedadRepository;
import com.controllocal.persistence.repositorio.BorradorCapturaRepository;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.TitularidadPropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.EscritorEstructural;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Transiciones;
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
import java.util.Optional;
import java.util.Set;

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

    private final PropiedadRepository propiedades;
    private final PersonaRolRepository roles;
    private final DetalleAgenteRepository agentes;
    private final DistritoRepository distritos;
    private final TitularidadPropiedadRepository titularidades;
    private final AtributoPropiedadRepository atributos;
    private final CaptacionRepository captaciones;
    private final PrecioPropiedadRepository precios;
    private final EventoDominioRepository eventos;
    private final BorradorCapturaRepository borradores;
    private final AtributosGobernados gobierno;
    private final ComandosIdempotentes comandos;
    private final Documentos documentos;
    private final Transiciones transiciones;

    public PropiedadUniversalServiceImpl(PropiedadRepository propiedades, PersonaRolRepository roles,
                                         DetalleAgenteRepository agentes, DistritoRepository distritos,
                                         TitularidadPropiedadRepository titularidades,
                                         AtributoPropiedadRepository atributos,
                                         CaptacionRepository captaciones,
                                         PrecioPropiedadRepository precios,
                                         EventoDominioRepository eventos,
                                         BorradorCapturaRepository borradores,
                                         AtributosGobernados gobierno,
                                         ComandosIdempotentes comandos, Documentos documentos,
                                         Transiciones transiciones) {
        this.propiedades = propiedades;
        this.roles = roles;
        this.agentes = agentes;
        this.distritos = distritos;
        this.titularidades = titularidades;
        this.atributos = atributos;
        this.captaciones = captaciones;
        this.precios = precios;
        this.eventos = eventos;
        this.borradores = borradores;
        this.gobierno = gobierno;
        this.comandos = comandos;
        this.documentos = documentos;
        this.transiciones = transiciones;
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
        Map<String, String> valores = atributosValidados(comando.atributos());
        List<Titular> titulares = titularesValidados(comando.titulares());

        // Se comprueba ANTES de escribir nada: "te falta el metraje" es un
        // mensaje que se entiende, y un fallo del trigger a mitad de la
        // transaccion no lo es.
        exigirObligatorios(actor.idOrganizacion(), tipoPropiedad, valores.keySet());

        DetalleAgente agente = agenteDe(actor);
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
        // metraje, y un atributo no se puede insertar sin propiedad.
        enrutarEstructurales(actor, propiedad, valores);

        // `precio_referencial` y `moneda_referencial` son NOT NULL y todo el
        // cable actual las lee. Se proyectan del encargo de ALQUILER si lo hay
        // -- la columna se llama "renta referencial" en media docena de sitios
        // -- y del de venta si solo hay venta.
        OperacionSolicitada referencia = operacionDeReferencia(operaciones);
        propiedad.setPrecioReferencial(referencia.importe());
        propiedad.setMonedaReferencial(referencia.moneda());

        List<PersonaRol> rolesTitulares = rolesDeTitulares(actor.idOrganizacion(), titulares);
        propiedad.setRolPropietario(rolesTitulares.get(indiceDelRepresentante(titulares)));
        propiedad.aplicarEstadoLegado(Propiedad.LEGADO_DISPONIBLE);
        propiedades.save(propiedad);

        escribirTitularidades(actor, propiedad.getId(), titulares, rolesTitulares);
        escribirAtributos(actor, propiedad, valores);

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

    /**
     * <b>La lista de campos de cada tipo se sirve, no se escribe.</b> Angular
     * pinta lo que esto devuelve y KAIROS pregunta lo que esto devuelve; si
     * alguno de los dos llevara su propia lista, anadir un atributo obligaria a
     * desplegar tres cosas y las tres divergirian.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PreguntaCatalogo> catalogoDe(String tipoPropiedad, Actor actor) {
        String tipo = tipoValidado(tipoPropiedad);
        return gobierno.aplicablesA(actor.idOrganizacion(), tipo).stream()
                .map(atributo -> new PreguntaCatalogo(atributo.getClave(), atributo.getRotulo(),
                        atributo.getTipoDato(), atributo.getUnidad(),
                        atributo.esRequeridoPara(tipo), atributo.getOrden()))
                .toList();
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

        String huella = documentos.huellaDe(new LinkedHashMap<>(Map.of(
                "idPropiedad", idPropiedad,
                "descripcion", texto(comando.descripcion()),
                "titulares", titularesEnHuella(comando.titulares()),
                "atributos", atributosEnHuella(comando.atributos()),
                "operaciones", operacionesEnHuella(comando.operaciones()))));
        Optional<ComandoIdempotente> yaHecho =
                comandos.buscar(actor, comando.claveIdempotencia(), COMANDO_EDICION, huella);
        if (yaHecho.isPresent()) {
            return ficha(actor, propiedad);
        }

        if (comando.descripcion() != null) {
            propiedad.setDescripcion(comando.descripcion());
        }
        if (comando.ubicacion() != null) {
            aplicarUbicacion(propiedad, ubicacionValidada(comando.ubicacion()));
        }
        if (comando.titulares() != null) {
            reemplazarTitularidades(actor, propiedad, titularesValidados(comando.titulares()));
        }
        if (comando.atributos() != null) {
            actualizarAtributos(actor, propiedad, atributosValidados(comando.atributos()));
        }
        if (comando.operaciones() != null) {
            for (OperacionSolicitada solicitada : operacionesValidadas(comando.operaciones())) {
                actualizarEncargo(actor, propiedad, solicitada, procedencia);
            }
        }
        propiedades.save(propiedad);

        anotarEvento(actor, EVENTO_EDITADA, Propiedad.ENTIDAD_TIPO, propiedad.getId(), procedencia,
                Map.of("idPropiedad", propiedad.getId()));
        comandos.registrar(actor, comando.claveIdempotencia(), COMANDO_EDICION, huella,
                Propiedad.ENTIDAD_TIPO, propiedad.getId(), procedencia,
                documentos.objeto(Map.of("idPropiedad", propiedad.getId())));

        return ficha(actor, propiedad);
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
        propiedad.setRolPropietario(rolesTitulares.get(indiceDelRepresentante(titulares)));
    }

    /**
     * Guarda los valores cuya autoridad es {@code atributo_propiedad}.
     *
     * <p>Los estructurales ya se aplicaron sobre la propiedad antes del save, y
     * el enrutador devuelve {@code empty()} para ellos: <b>no se escriben dos
     * veces</b>. Esa exclusion mutua es toda la decision de D-E4-3.
     */
    private void escribirAtributos(Actor actor, Propiedad propiedad, Map<String, String> valores) {
        valores.forEach((clave, valor) -> gobierno
                .enrutar(actor.idOrganizacion(), propiedad, clave, valor)
                .ifPresent(atributos::save));
    }

    private void actualizarAtributos(Actor actor, Propiedad propiedad, Map<String, String> valores) {
        valores.forEach((clave, valor) -> {
            Optional<AtributoPropiedad> existente =
                    atributos.findByIdPropiedadAndClave(propiedad.getId(), clave);
            // El mismo enrutador que el alta: si la clave es estructural aplica
            // sobre la propiedad y NO deja atributo; si es gobernada, actualiza
            // el que hay o crea el que falta. Arreglar solo el alta dejaria la
            // fuga abierta en la operacion mas frecuente de las dos.
            gobierno.enrutarEdicion(actor.idOrganizacion(), propiedad, clave, valor,
                            existente.orElse(null))
                    .ifPresent(atributos::save);
        });
    }

    /**
     * Aplica las claves cuya autoridad es un campo canonico del agregado.
     *
     * <p>Se separa del resto porque tiene que ocurrir <b>antes</b> del primer
     * {@code save}: {@code propiedad.metraje} es NOT NULL. Los atributos
     * gobernados, en cambio, necesitan que la propiedad ya exista.
     */
    private void enrutarEstructurales(Actor actor, Propiedad propiedad, Map<String, String> valores) {
        valores.forEach((clave, valor) -> {
            CatalogoAtributo definicion = gobierno.definicionDe(actor.idOrganizacion(), clave);
            if (definicion.esEstructural()) {
                EscritorEstructural.aplicar(propiedad, definicion.getCampoEstructural(), valor, clave);
            }
        });
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

        // Primer hito 'U' (autorizado) de ESTA serie. Va atado al encargo: es
        // lo que permite que la venta y el alquiler de la misma propiedad
        // tengan historicos separados de verdad y no una lista mezclada en la
        // que 180 000 y 2 900 solo se distinguen por su magnitud.
        precios.save(PrecioPropiedad.hito(actor.idOrganizacion(), propiedad.getId(), operacion,
                        PrecioPropiedad.HITO_AUTORIZADO, solicitada.moneda(), solicitada.importe(),
                        LocalDate.now())
                .delEncargo(encargo.getId()));

        anotarEvento(actor, EVENTO_ENCARGO, "CAPTACION", encargo.getId(), procedencia,
                Map.of("idPropiedad", propiedad.getId(), "idCaptacion", encargo.getId(),
                        "operacion", operacion.name(), "importe", solicitada.importe(),
                        "moneda", solicitada.moneda()));
        return encargo.getId();
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
        Map<String, CatalogoAtributo> definiciones = gobierno.definicionesDe(idOrganizacion, tipo);
        List<AtributoFicha> valores = new ArrayList<>();

        for (AtributoPropiedad atributo : atributos.findByIdPropiedadOrderByClaveAsc(idPropiedad)) {
            CatalogoAtributo definicion = definiciones.get(atributo.getClave());
            valores.add(new AtributoFicha(atributo.getClave(),
                    definicion == null ? atributo.getClave() : definicion.getRotulo(),
                    definicion == null ? null : definicion.getTipoDato(),
                    definicion == null ? null : definicion.getUnidad(),
                    AtributosGobernados.comoTexto(atributo)));
        }

        for (CatalogoAtributo definicion : definiciones.values()) {
            if (!definicion.esEstructural()) {
                continue;
            }
            String valor = EscritorEstructural.leer(propiedad, definicion.getCampoEstructural());
            if (valor != null) {
                valores.add(new AtributoFicha(definicion.getClave(), definicion.getRotulo(),
                        definicion.getTipoDato(), definicion.getUnidad(), valor));
            }
        }
        valores.sort(java.util.Comparator.comparing(AtributoFicha::clave));

        // La serie se lee UNA vez y se reparte por operacion. Consultarla dentro
        // del bucle serian dos consultas identicas por propiedad — el N+1 que
        // RC-003 vino a quitar, en pequeno.
        List<PrecioPropiedad> serie = precios.findByIdPropiedadOrderByFechaAscIdAsc(idPropiedad);
        List<EncargoFicha> encargos = captaciones.encargosVivosDe(idOrganizacion, idPropiedad).stream()
                .map(encargo -> fichaDeEncargo(encargo, serie))
                .toList();

        return new FichaPropiedadUniversal(idPropiedad, propiedad.getCodigo(), tipo,
                propiedad.getUso(), propiedad.getDescripcion(), propiedad.getEstadoRegistro(),
                propiedad.getDisponibilidadComercial(), ubicacionDe(propiedad), titulares, valores,
                encargos, gobierno.obligatoriasQueFaltan(idOrganizacion, propiedad),
                Fechas.local(propiedad.getFechaRegistro()));
    }

    /**
     * El historico de un encargo es SOLO el suyo. Filtrar por operacion no es
     * cosmetico: una propiedad en venta y en alquiler tiene dos series, y
     * mezclarlas produce una linea temporal que no significa nada.
     */
    private EncargoFicha fichaDeEncargo(Captacion encargo, List<PrecioPropiedad> serie) {
        CondicionEconomicaCaptacion condicion = encargo.getCondicionEconomica();
        String operacion = encargo.getMotivoOperacion();
        List<HitoFicha> historico = serie.stream()
                .filter(precio -> operacion.equals(precio.getOperacion()))
                .map(precio -> new HitoFicha(precio.getHito(), precio.getMonto(), precio.getMoneda(),
                        precio.getFecha()))
                .toList();
        return new EncargoFicha(encargo.getId(), encargo.getCodigoCaptacion(),
                encargo.operacion().name(), encargo.estadoActual(),
                condicion == null ? null : condicion.getImporteReferencia(),
                condicion == null ? null : condicion.getMonedaReferencia(),
                // Se escribia y no se devolvia: un formulario en modo edicion la
                // pintaba desmarcada y la borraba al guardar. Lo destapo el
                // trazado campo -> DTO -> dominio -> persistencia -> LECTURA.
                encargo.getExclusividad(),
                encargo.getFechaInicioVigencia(), encargo.getFechaFinVigencia(), historico);
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
     * Las cuotas se comprueban <b>antes</b> de escribir. La base lo garantiza
     * con un constraint trigger diferido (V47), pero ese estalla al COMMIT y
     * su mensaje habla de sumas, no de titulares: aqui se puede decir "las
     * cuotas suman 90, faltan 10".
     */
    private static List<Titular> titularesValidados(List<Titular> titulares) {
        if (titulares == null || titulares.isEmpty()) {
            throw new ReglaNegocioException(
                    "Una propiedad sin titular no se puede registrar: no se sabe con quien se "
                            + "negocia ni quien autoriza el precio.");
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

    private static Map<String, String> atributosValidados(List<ValorAtributo> valores) {
        Map<String, String> porClave = new LinkedHashMap<>();
        if (valores == null) {
            return porClave;
        }
        for (ValorAtributo valor : valores) {
            if (valor.clave() == null || valor.clave().isBlank()) {
                throw new ReglaNegocioException("Un atributo sin clave no se puede guardar.");
            }
            if (porClave.put(valor.clave().trim(), valor.valor()) != null) {
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
    private static List<OperacionSolicitada> operacionesValidadas(List<OperacionSolicitada> operaciones) {
        if (operaciones == null || operaciones.isEmpty()) {
            throw new ReglaNegocioException(
                    "Declara al menos una operacion: VENTA o ALQUILER. Sin operacion no hay precio "
                            + "que registrar, porque el mismo numero significa cosas distintas.");
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
                    solicitada.inicioEncargo(), solicitada.finEncargo()));
        }
        return normalizadas;
    }

    /**
     * La operacion que alimenta {@code precio_referencial}. El alquiler manda
     * porque la columna se llama "renta referencial" en media docena de sitios
     * del cable actual, y una venta ahi haria que los listados mostraran
     * 180 000 donde esperan una mensualidad.
     */
    private static OperacionSolicitada operacionDeReferencia(List<OperacionSolicitada> operaciones) {
        return operaciones.stream()
                .filter(solicitada -> OperacionInmobiliaria.ALQUILER.name().equals(solicitada.operacion()))
                .findFirst()
                .orElse(operaciones.get(0));
    }

    private OperacionInmobiliaria operacionProyectada(Actor actor, Propiedad propiedad) {
        List<Captacion> vivos = captaciones.encargosVivosDe(actor.idOrganizacion(), propiedad.getId());
        return vivos.stream()
                .map(Captacion::operacion)
                .filter(operacion -> operacion == OperacionInmobiliaria.ALQUILER)
                .findFirst()
                .orElse(vivos.isEmpty() ? OperacionInmobiliaria.ALQUILER : vivos.get(0).operacion());
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

    private DetalleAgente agenteDe(Actor actor) {
        return agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El alta universal la registra un agente: el encargo necesita saber quien "
                                + "responde por el."));
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private void aplicarUbicacion(Propiedad propiedad, Ubicacion ubicacion) {
        propiedad.setDireccion(ubicacion.direccion().trim());
        propiedad.setDistrito(ubicacion.distrito().trim());
        propiedad.setZonaUrbanizacion(ubicacion.zonaUrbanizacion());
        propiedad.setInteriorUnidad(ubicacion.interiorUnidad());
        propiedad.setPiso(ubicacion.piso());
        propiedad.setReferenciaInterna(ubicacion.referenciaInterna());
        propiedad.setNombreEdificioGaleria(ubicacion.nombreEdificioGaleria());
        // `ubicacion` (geography) la deriva el trigger de V46 a partir de estas
        // dos: escribirla desde Java exigiria PostGIS en el modelo JPA.
        propiedad.setGeoLat(ubicacion.latitud());
        propiedad.setGeoLong(ubicacion.longitud());
        resolverDistrito(propiedad);
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

    private static String atributosEnHuella(List<ValorAtributo> valores) {
        if (valores == null) {
            return "";
        }
        return valores.stream()
                .map(valor -> valor.clave() + "=" + valor.valor())
                .sorted()
                .reduce((a, b) -> a + "," + b)
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
