package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.OperacionDelEncargo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reglas heredadas del PrecioLocalBusinessLogicImpl v1 (moneda PEN y fecha
 * de hoy por defecto, monto no negativo), con una mejora documentada: el
 * local debe existir (la v1 dejaba reventar la FK con un 500).
 */
@Service
public class PrecioLocalServiceImpl implements PrecioLocalService {

    private final PrecioPropiedadRepository precios;
    private final PropiedadRepository propiedades;
    /** Para saber de QUE encargo es cada hito al leer la serie (D-P0-6). */
    private final CaptacionRepository captaciones;
    private final OperacionDelEncargo operaciones;
    /**
     * Un hito economico nace de un encargo: lo escribe SU agente (P0-4) y lo
     * <b>lee</b> quien D-P0-6 diga. Las dos preguntas salen del mismo sitio, y
     * son dos metodos distintos: escribir el encargo lo hace solo su agente,
     * leerlo lo hace ademas el broker que lo supervisa.
     */
    private final AutoridadDePropiedad autoridad;

    public PrecioLocalServiceImpl(PrecioPropiedadRepository precios, PropiedadRepository propiedades,
                                  CaptacionRepository captaciones,
                                  OperacionDelEncargo operaciones,
                                  AutoridadDePropiedad autoridad) {
        this.precios = precios;
        this.propiedades = propiedades;
        this.captaciones = captaciones;
        this.operaciones = operaciones;
        this.autoridad = autoridad;
    }

    /**
     * <b>La serie economica, filtrada hito a hito por quien pregunta</b>
     * (D-P0-6).
     *
     * <h2>Lo que estaba roto, medido</h2>
     * Este metodo no recibia actor y la consulta no llevaba tenant: bastaba con
     * estar autenticado en <b>cualquier</b> corredora para leer la serie de
     * precios de <b>cualquier</b> propiedad por su id. La fila de la matriz
     * decia «coleccion hija: se alcanza por el id del padre, que si va filtrado
     * por tenant» — y el padre <b>no se cargaba</b>. La frase describia una
     * proteccion que no existia.
     *
     * <h2>Dos comprobaciones, en este orden</h2>
     * <ol>
     *   <li>la <b>frontera de tenant</b>: la propiedad se carga por
     *       {@code (organizacion, id)} y un id ajeno responde INEXISTENTE, no
     *       "vacio". Vacio diria que esa propiedad existe y no tiene precios;</li>
     *   <li>y despues D-P0-6, <b>hito a hito</b>, porque los hitos de una
     *       propiedad no son todos del mismo sujeto:
     *       <ul>
     *         <li>con {@code idCaptacion} el hito pertenece al historico de ESE
     *             ENCARGO, y lo lee su agente o el broker que lo supervisa;</li>
     *         <li><b>sin</b> encargo —legado: 8 hitos en la base de desarrollo y
     *             102 en la de pruebas, medidos el 2026-09-01— no hay episodio
     *             al que atribuirlo, asi que sigue la regla de la PROPIEDAD: su
     *             responsable, o el broker que lo alcanza. No se le inventa un
     *             encargo para poder clasificarlo.</li>
     *       </ul></li>
     * </ol>
     *
     * <p>La respuesta puede quedar <b>vacia</b>, y eso es correcto: significa
     * "de esta serie no te corresponde ningun hito". El TENANT_ADMIN la recibe
     * vacia siempre — gobernar no es operar, y el importe pedido y el de cierre
     * son informacion comercial.
     *
     * <p>Se filtra en memoria y no en el {@code where} a proposito: la decision
     * de D-P0-6 depende del <b>encargo</b> de cada hito, y ya hace falta cargar
     * los encargos de la propiedad para poder responderla. Bajarla a SQL
     * significaria escribir por segunda vez —en otro lenguaje— el predicado que
     * {@code AutoridadDePropiedad} ya decide, y dos escrituras del mismo permiso
     * divergen.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FichaPrecio> listarPorLocal(long idPropiedad, Actor actor) {
        if (idPropiedad <= 0) {
            throw new ReglaNegocioException("El id de local comercial debe ser mayor que cero.");
        }
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                .orElseThrow(() -> new NoEncontradoException("Propiedad"));

        // Los encargos de la propiedad, en UNA consulta y por su id: preguntar
        // dentro del bucle seria una consulta por hito.
        Map<Long, Captacion> encargos = captaciones
                .encargosDe(actor.idOrganizacion(), idPropiedad).stream()
                .collect(Collectors.toMap(Captacion::getId, e -> e, (a, b) -> a,
                        LinkedHashMap::new));

        boolean leeLaPropiedad = autoridad.puedeLeerHistoriaDeLaPropiedad(actor, propiedad);
        Map<Long, Boolean> visibles = new HashMap<>();

        return precios.findByIdPropiedadOrderByFechaAscIdAsc(idPropiedad).stream()
                .filter(precio -> {
                    Long idEncargo = precio.getIdCaptacion();
                    if (idEncargo == null) {
                        return leeLaPropiedad;
                    }
                    // Un hito que apunta a un encargo que ya no esta se trata
                    // como no visible: sin episodio no se puede decir de quien
                    // era, y concederlo por defecto seria decidir por el lado que
                    // ensena de mas.
                    return visibles.computeIfAbsent(idEncargo, id -> autoridad
                            .puedeLeerHistoricoDelEncargo(actor, encargos.get(id)));
                })
                .map(PrecioLocalServiceImpl::ficha)
                .toList();
    }

    @Override
    @Transactional
    public FichaPrecio registrar(long idPropiedad, DatosPrecio datos, Actor actor) {
        if (idPropiedad <= 0) {
            throw new ReglaNegocioException("El id de local comercial debe ser mayor que cero.");
        }
        if (datos.hito() == null || !PrecioPropiedad.HITOS.contains(datos.hito())) {
            throw new ReglaNegocioException("Valor invalido para hito de precio: " + datos.hito());
        }
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "del precio");
        if (datos.monto() == null || datos.monto().signum() < 0) {
            throw new ReglaNegocioException("El monto del precio no puede ser negativo.");
        }
        if (!propiedades.existsByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)) {
            throw new ReglaNegocioException("El local no existe.");
        }

        // Las filas de los encargos VIVOS de esta propiedad, TOMADAS antes de
        // mirarlas (F2.10). Va aqui, delante de `resolver`, y no junto a la
        // guarda de mas abajo: `resolver` ya carga esos encargos para deducir la
        // operacion, y un candado tomado despues devolveria la instancia ya
        // cargada SIN refrescar -- o sea, la autoridad decidida sobre el agente
        // viejo, con el defecto intacto y con aspecto de arreglado.
        //
        // Se toma el conjunto candidato porque hasta despues de `resolver` no se
        // sabe en cual de los dos encargos cae el hito, y son como mucho dos
        // (V50). El orden de id lo fija la consulta, asi que dos peticiones
        // simultaneas no pueden bloquearse entre ellas.
        //
        // El resultado se descarta a proposito: lo que hace falta es el bloqueo;
        // las consultas de abajo devuelven las mismas instancias gestionadas.
        captaciones.bloquearEncargosVivosParaEscritura(actor.idOrganizacion(), idPropiedad);

        // De que operacion es este importe. Declarada si viene; deducida del
        // unico encargo vivo si no; y si no hay forma de saberlo, se rechaza en
        // vez de archivarlo como alquiler (D-E4-1).
        OperacionInmobiliaria operacion =
                operaciones.resolver(actor.idOrganizacion(), idPropiedad, datos.operacion());

        PrecioPropiedad precio = PrecioPropiedad.hito(actor.idOrganizacion(), idPropiedad, operacion,
                datos.hito(), moneda, datos.monto(),
                datos.fecha() != null ? datos.fecha() : LocalDate.now());
        // Atado a su encargo, y SIN encargo no hay hito (V76). Es lo que permite
        // que una venta y un alquiler de la misma propiedad tengan series
        // separadas de verdad -- y, sobre todo, lo que impide que un importe que
        // nadie autorizo entre en la serie economica de la propiedad. La base lo
        // rechazaria igual (`tg_precio_exige_encargo`); se dice aqui para que el
        // mensaje explique la alternativa en vez de llegar como error de
        // integridad.
        Captacion encargo = operaciones.encargoDe(actor.idOrganizacion(), idPropiedad, operacion)
                .orElseThrow(() -> new ReglaNegocioException(
                        "Esta propiedad no tiene un encargo vivo de "
                                + operacion.name().toLowerCase(Locale.ROOT)
                                + ": un hito economico nace del encargo que lo autorizo. Si lo que "
                                + "quieres guardar es lo que se VIO en el mercado, va en las "
                                + "observaciones de la propiedad."));
        // Y el encargo tiene que ser SUYO (P0-4).
        //
        // Esta es la via indirecta al historico economico ajeno: hasta V87 solo
        // se comprobaba `existsByOrganizacionIdAndId` sobre la propiedad, asi
        // que cualquier agente del tenant metia un hito en la serie de un
        // encargo que no habia negociado. La fila de la matriz ya declaraba
        // "un local de sus captaciones" — el codigo no lo comprobaba, y ahora
        // cumple lo que la matriz dice.
        //
        // Va DESPUES de resolver el encargo y no antes, porque hasta aqui no se
        // sabe de que encargo es el importe: la operacion puede venir declarada
        // o deducirse del unico encargo vivo (D-E4-1).
        autoridad.exigirEdicionDelEncargo(actor, encargo);
        precio.delEncargo(encargo.getId());
        precios.save(precio);
        return ficha(precio);
    }

    private static FichaPrecio ficha(PrecioPropiedad p) {
        return new FichaPrecio(p.getId(), p.getIdPropiedad(), p.getHito(), p.getMoneda(),
                p.getMonto(), p.getFecha(), Fechas.local(p.getFechaCreacion()),
                OperacionInmobiliaria.deCodigo(p.getOperacion()).name());
    }
}
