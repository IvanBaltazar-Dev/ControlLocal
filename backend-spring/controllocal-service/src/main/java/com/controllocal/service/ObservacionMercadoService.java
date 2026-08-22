package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * <b>Lo que se vio del mercado sobre un inmueble</b> (V76).
 *
 * <p>Es la serie que hace util a una Propiedad que BROX <b>conoce</b> y no
 * <b>gestiona</b>: sin ella, autorizar propiedades sin encargo habria sido
 * permitir fichas huerfanas en vez de habilitar conocimiento.
 *
 * <blockquote>BROX nunca convierte una observacion de mercado en un hecho
 * comercial ni inventa una relacion para poder conservar conocimiento.</blockquote>
 *
 * <p>Por eso este servicio <b>no escribe nada</b> fuera de su propia tabla: ni
 * hitos de {@code precio_propiedad}, ni el precio referencial de la propiedad,
 * ni disponibilidad. Observar no autoriza, no publica y no negocia.
 */
public interface ObservacionMercadoService {

    /**
     * @param idPropiedad    el inmueble observado
     * @param fechaObservada cuando se vio. No es la fecha de captura: un aviso
     *                       de hace tres meses anotado hoy vale por su fecha
     * @param operacion      VENTA o ALQUILER, con palabras. Sin defecto: el
     *                       mismo importe significa un precio o una renta
     * @param fuente         de donde salio. Obligatoria: sin fuente es un rumor
     * @param detalle        el enlace del aviso, la referencia del cartel, la nota
     */
    record DatosObservacion(Long idPropiedad, LocalDate fechaObservada, String operacion,
                            BigDecimal importe, String moneda, String fuente, String detalle) {
    }

    /** Una observacion ya leida, con su evidencia entera. */
    record FichaObservacion(Long id, LocalDate fechaObservada, String operacion,
                            String operacionRotulo, BigDecimal importe, String moneda,
                            String importeRotulo, String fuente, String detalle,
                            Long idRolActor, String actorNombre) {
    }

    /**
     * Anota lo observado. <b>Append-only</b>: no hay editar ni borrar, porque
     * una observacion es un hecho fechado y corregirla borraria la muestra. Si
     * el precio cambio, se observa otra vez.
     */
    FichaObservacion registrar(DatosObservacion datos, Actor actor);

    /** Lo observado de una propiedad, de lo mas reciente a lo mas antiguo. */
    List<FichaObservacion> listarDe(long idPropiedad, Actor actor);
}
