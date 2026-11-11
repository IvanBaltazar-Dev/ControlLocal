package com.controllocal.service;

import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import java.util.Optional;
import java.util.Map;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <b>La negociacion: la oferta, la contraoferta y su desenlace</b> (WP-A,
 * D-N-1/D-N-2).
 *
 * <p>Una mesa es una OPORTUNIDAD —un interesado concreto frente a un encargo
 * concreto—. Dos interesados por el mismo inmueble son dos mesas y dos cadenas
 * separadas.
 *
 * <h2>Autoridad</h2>
 * Escribir en la mesa es <b>operar el encargo</b>, asi que la decide
 * {@code AutoridadDePropiedad.exigirEdicionDelEncargo}: el agente de ESE
 * encargo. No la sustituye ser responsable de la propiedad, y el gobierno del
 * tenant no la hereda —gobernar no es operar—. Leer la mesa va con el alcance
 * de siempre: el agente lo suyo, el broker por captacion supervisada, el
 * gobierno no obtiene lectura comercial por administrar el tenant.
 *
 * <h2>Lo que NO hace</h2>
 * Rechazar la ultima oferta <b>no</b> cierra la oportunidad. Que el propietario
 * diga que no a una cifra no significa que el interesado se haya ido; la caida
 * es un acto aparte, con su motivo tipificado
 * ({@code POST /oportunidades/{id}/no-continuidad}).
 */
public interface OfertaService {

    /**
     * Contra que precio se ofrecio, congelado al presentar (D-N-1).
     *
     * <p>{@code hito} es {@code "P"} —el ultimo publicado del encargo— o
     * {@code "U"} —el ultimo autorizado, cuando no hubo anuncio—. Cuando el
     * encargo no tiene ninguno de los dos, <b>los cuatro campos llegan
     * nulos</b>: la referencia es FALTANTE y se dice, no se rellena con el
     * importe del encargo ni con el de la propia oferta.
     */
    record ReferenciaDeLaOferta(String hito, String hitoRotulo, Long idPrecio,
                                BigDecimal monto, String moneda) {
    }

    /** Espejo de una fila de la mesa, ya rotulada: el cliente no traduce codigos. */
    record FichaOferta(Long id, Long idOportunidad, Long idCaptacion, Integer ronda,
                       String presentadaPor, String presentadaPorRotulo,
                       BigDecimal monto, String moneda,
                       String estado, String estadoRotulo,
                       String condiciones, LocalDate fecha,
                       Long idRolRegistra, String origen,
                       ReferenciaDeLaOferta referencia,
                       LocalDateTime fechaRegistro, long version, long versionMesa,
                       ParteReal parteProponente, ObjetoExacto objeto, Dinero importeDeclarado,
                       Long ofertaAnterior) {
    }

    /**
     * Que puede hacer el actor sobre ESTA mesa, resuelto en el Core.
     *
     * <p>Cada bandera sale del <b>mismo predicado que despues deniega</b> el
     * comando, no de una segunda tabla de decision: una copia de una regla de
     * autoridad en la pantalla diverge siempre hacia el lado que concede de mas,
     * y un boton activo que el backend rechaza es el peor error de esta
     * superficie.
     */
    record CapacidadesDeLaMesa(boolean puedeOfertar, boolean puedeContraofertar,
                               boolean puedeAceptar, boolean puedeRechazar,
                               boolean puedeRetirar,
                               boolean puedeRegistrarCaida,long versionMesa) {
    }

    List<FichaOferta> listar(long idOportunidad, Actor actor);
    List<FichaOferta> listar(long idOportunidad, Instant corte, Actor actor);
    CapacidadesDeLaMesa capacidades(long idOportunidad, Actor actor);
    /** RF046–049: identidad de la parte manifestante, separada del operador. */
    record ParteReal(String lado, Long persona) { }
    enum TipoObjeto { UNIDAD, COMPOSICION }
    /** Referencias observadas exactas; no completar versiones ausentes con las actuales. */
    record ObjetoExacto(TipoObjeto tipo, Long encargo, Long versionEncargo,
                        Long propiedad, Long versionFisica, Long composicion,
                        Long versionComposicion, List<Long> unidades) {
        public ObjetoExacto { unidades = unidades == null ? null : List.copyOf(unidades); }
    }
    /** El sujeto es ObjetoExacto; la operación procede del episodio observado. */
    record Dinero(BigDecimal importe, String moneda, String base, String periodo) { }
    record DatosRegistroOferta(Long oportunidad, Long versionOportunidad, Long versionMesa,
                                ParteReal proponente, Dinero importe, ObjetoExacto objeto,
                                String condiciones, LocalDate fechaHecho, String evidencia,
                                Long ofertaAnterior, Long versionOfertaAnterior) { }
    record DatosContraoferta(Long oportunidad, Long versionOportunidad, Long versionMesa,
                             Long ofertaAnterior, Long versionOferta, ParteReal proponente,
                             Dinero importe, ObjetoExacto objeto, String condiciones,
                             LocalDate fechaHecho, String evidencia) { }
    record DatosAceptacion(Long oportunidad, Long versionOportunidad, Long versionMesa, Long versionEncargo,
                            Long oferta, Long versionOferta, ParteReal parte,
                            LocalDate fechaHecho, String evidencia, String motivo) { }
    record DatosRechazo(Long oportunidad, Long versionOportunidad, Long versionMesa, Long versionEncargo,
                         Long oferta, Long versionOferta, ParteReal parte,
                         LocalDate fechaHecho, String evidencia, String motivo) { }
    record DatosRetirada(Long oportunidad, Long versionOportunidad, Long versionMesa, Long versionEncargo,
                          Long oferta, Long versionOferta, ParteReal parte,
                          LocalDate fechaHecho, String evidencia, String motivo) { }
    record ResultadoNegociacion(long oportunidad, long versionMesa, long oferta,
                                 long versionOferta, String estado, Long acuerdoAceptado,
                                 Long versionAcuerdo, LocalDateTime registradoEn) { }
    record AcuerdoAceptado(long id,long version,long oportunidad,long oferta,long versionOferta,
                           DatosRegistroOferta propuesta,DatosAceptacion aceptacion,
                           Long idRolOperador,Procedencia procedencia,Instant registradoEn) { }
    /** RF050: acuerdo inmutable exacto, reautorizado hoy; no confirma hito jurídico ni dinero C5. */
    Optional<AcuerdoAceptado> consultarAcuerdoAceptado(long oportunidad,Instant corte,Actor actor);

    DatosRegistroOferta validarRegistroOferta(DatosRegistroOferta datos, Actor actor);
    DatosContraoferta validarContraoferta(DatosContraoferta datos, Actor actor);
    DatosAceptacion validarAceptacion(DatosAceptacion datos, Actor actor);
    DatosRechazo validarRechazo(DatosRechazo datos, Actor actor);
    DatosRetirada validarRetirada(DatosRetirada datos, Actor actor);
    ResultadoNegociacion registrarOferta(DatosRegistroOferta datos, Actor actor,
            ContextoSesion sesion, String clave, Procedencia procedencia);
    ResultadoNegociacion registrarContraoferta(DatosContraoferta datos, Actor actor,
            ContextoSesion sesion, String clave, Procedencia procedencia);
    ResultadoNegociacion registrarAceptacion(DatosAceptacion datos, Actor actor,
            ContextoSesion sesion, String clave, Procedencia procedencia);
    ResultadoNegociacion registrarRechazo(DatosRechazo datos, Actor actor,
            ContextoSesion sesion, String clave, Procedencia procedencia);
    ResultadoNegociacion registrarRetirada(DatosRetirada datos, Actor actor,
            ContextoSesion sesion, String clave, Procedencia procedencia);
    Optional<ResultadoNegociacion> consultarResultado(String capacidad, String clave, Actor actor);
    /** Sólo datos autorizados y exactos que integrarán la revisión humana canónica. */
    Map<String,Object> previsualizarActuacion(String capacidad, Map<String,Object> datos, Actor actor);
    /** Reautoriza consulta/replay; no reevalúa los CAS ni mínimos de escritura históricos. */
    void exigirLecturaMesa(long idOportunidad, Actor actor);
    /** Lectura de los datos originales de una actuación, sin exigir sus CAS de escritura. */
    void exigirLecturaActuacion(String capacidad,Map<String,Object> datos,Actor actor);
}
