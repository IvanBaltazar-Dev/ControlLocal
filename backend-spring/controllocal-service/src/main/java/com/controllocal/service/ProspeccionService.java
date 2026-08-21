package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Casos de uso de prospeccion (F2). Los records espejan el contrato REST
 * CONGELADO (Dtos.ProspeccionRequest/ProspeccionResponse de la v1). La
 * maquina de estados P->C->R->S->{T|D} corre sobre soporte/Transiciones, asi
 * que CADA transicion emite historial_estado (mejora MEJ-01 que la v1 no hacia,
 * sin cambiar el cable). El alcance por rol se resuelve en el service.
 */
public interface ProspeccionService {

    /** Espejo de ProspeccionRequest. */
    record DatosProspeccion(Long idLocal, String observaciones) {
    }

    /**
     * Filtros y paginacion del GET / (cable v1).
     *
     * <p>Dos que no son columnas y hay que conocer:
     * <ul>
     *   <li>{@code estado = "GESTION"} no es un estado: es el cubo de las
     *       ACTIVAS ({@code P, C, R, E, S}).</li>
     *   <li>{@code idBrokerSupervisor} filtra por el EQUIPO de ese broker. Se
     *       aplica ademas del alcance del actor, no en su lugar.</li>
     * </ul>
     */
    record FiltrosProspeccion(String estado, String distrito, Long idCaptacion, Long idLocal,
                              Long idAgente, Long idBrokerSupervisor, String q, String orden,
                              int pagina, int tamano) {
    }

    /** Espejo de ProspeccionResponse (22 campos; idAgente = persona_rol.id del rol AGENTE). */
    record FichaProspeccion(Long id, String codigoProspeccion, Long localId, String localCodigo,
                            String direccion, String distrito, BigDecimal areaM2, String rubro,
                            BigDecimal precioReferencial, String monedaReferencial,
                            String propietarioNombre, Long idAgente,
                            String agenteNombre, String estado, String resultadoPropuesta,
                            LocalDate fechaContacto, LocalDate fechaReunion, LocalDate fechaPropuesta,
                            LocalDate fechaRecontacto, String observaciones, Long idCaptacion,
                            String captacionCodigo, String disponibilidad) {
    }

    Pagina<FichaProspeccion> listar(FiltrosProspeccion filtros, Actor actor);

    /** Recontacto vencido (>= dias sin nueva accion), dentro del alcance del actor. */
    Pagina<FichaProspeccion> recontactar(int dias, int pagina, int tamano, Actor actor);

    FichaProspeccion obtener(long id, Actor actor);

    /** Alta de la prospeccion por el agente (estado inicial P). */
    FichaProspeccion registrar(DatosProspeccion datos, Actor actor);

    FichaProspeccion contactar(long id, Actor actor);

    FichaProspeccion registrarReunion(long id, Actor actor);

    FichaProspeccion entregarPropuesta(long id, Actor actor);

    /** Accion de seguimiento: reinicia el reloj de recontacto a 7 dias. */
    FichaProspeccion registrarSeguimiento(long id, Actor actor);

    FichaProspeccion rechazar(long id, String motivo, Actor actor);

    FichaProspeccion descartar(long id, String motivo, Actor actor);

    /**
     * <b>Lo que se pacta al captar</b> (V75).
     *
     * <p>Desde que la propiedad puede existir sin encargo, este es el sitio
     * donde el Encargo NACE, y por eso trae lo que un encargo necesita para
     * existir en vez de copiarlo del registro maestro.
     *
     * <p><b>La operacion es obligatoria y no tiene defecto.</b> Hasta V75 estaba
     * escrita a fuego como ALQUILER: una propiedad captada para venderse nacia
     * con un encargo de alquiler, con su serie economica bajo la operacion
     * equivocada y sin un solo aviso. El proyecto ya tuvo que retirar
     * exactamente esas inferencias.
     *
     * <p>El <b>importe</b> tampoco se hereda de {@code propiedad.precio_referencial}:
     * ese campo es la proyeccion de OTRO encargo —y desde V75 puede estar
     * vacio—, asi que tomarlo convertiria un dato de registro en precio
     * autorizado sin que nadie lo autorice. Si el encargo fuera de venta,
     * ademas, ese numero seria una renta.
     *
     * @param operacion       VENTA o ALQUILER, con esas palabras. Sin defecto
     * @param importe         el precio de venta o la renta mensual pactada
     * @param comisionPactada porcentaje; su significado depende de la base
     * @param tipoComision    {@code null} = el que corresponde a la operacion
     * @param baseCalculo     {@code null} = renta mensual en ALQUILER, precio
     *                        de venta en VENTA. No es una inferencia de dato:
     *                        es la aritmetica que la operacion implica, y es la
     *                        misma que aplica el alta al abrir un encargo
     */
    record DatosCaptura(String operacion, BigDecimal importe, String moneda,
                        BigDecimal comisionPactada, String tipoComision, String baseCalculo,
                        String tratamientoIgv, Boolean exclusividad,
                        LocalDate inicioEncargo, LocalDate finEncargo) {
    }

    /**
     * <b>Captar: aqui nace el Encargo</b> (estado P) y la prospeccion queda
     * CAPTADA (T).
     *
     * <p>Es el punto del embudo donde el propietario acepta:
     * {@code propietario -> prospeccion -> ENCARGO -> publicacion}. No
     * «completa» un encargo pendiente, porque antes de aceptar no hay ninguno
     * que completar: eso convertiria la intencion del agente en una relacion
     * comercial que el propietario no ha dado.
     */
    FichaProspeccion captar(long id, DatosCaptura datos, Actor actor);

    /** Enlaza una captacion ya creada por el agente y deja la prospeccion CAPTADA. */
    FichaProspeccion marcarCaptado(long id, Long idCaptacion, String codigoCaptacion, Actor actor);
}
