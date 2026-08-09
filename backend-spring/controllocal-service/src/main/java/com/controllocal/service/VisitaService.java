package com.controllocal.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Casos de uso de la visita al local. Los records espejan el contrato
 * CONGELADO (Dtos.VisitaRequest/VisitaResponse de la v1).
 *
 * <p>Maquina: P Programada -> G Reprogramada -> {R Realizada | N No realizada |
 * C Cancelada}. Toda transicion pasa por soporte/Transiciones, asi que la
 * agenda queda auditada en historial_estado (mejora sobre la v1, mismo cable).
 *
 * <p>El DESENLACE (resultado + nivel de interes, objecion, opinion de precio y
 * proxima accion) exige una visita REALIZADA y es IRREPETIBLE; cancelar o
 * marcar no-realizada lo limpia. Si el resultado implica no continuidad,
 * ademas cierra la oportunidad con su motivo tipificado.
 *
 * <p>Alcance (§5): AGENTE = suyas; ADMIN = todas; BROKER = las de sus
 * captaciones. Ojo con el ALTA: exige que la oportunidad sea del PROPIO agente,
 * sin alcance de broker.
 */
public interface VisitaService {

    /** Espejo de VisitaRequest. */
    record DatosVisita(Long idOportunidad, LocalDate fechaVisita, LocalTime horaVisita, String observaciones) {
    }

    /** Espejo de ResultadoVisitaRequest. */
    record DesenlaceVisita(String resultado, String observaciones, String razonNoContinuidad,
                           Integer nivelInteres, String objecionPrincipal, String opinionPrecio,
                           String proximaAccion) {
    }

    /** Filtros del GET / (cable v1). */
    record FiltrosVisita(Long idOportunidad, String estado, String distrito, String query,
                         int pagina, int tamano) {
    }

    /** Espejo de VisitaResponse (cliente y captacion se derivan de la oportunidad). */
    record FichaVisita(Long id, Long idOportunidad, String codigoOportunidad, LocalDate fechaVisita,
                       LocalTime horaVisita, String observaciones, String estado, String resultado,
                       Long idCliente, String clienteNombre, Long idCaptacion, String codigoCaptacion,
                       String direccionLocal, String distritoLocal, Long idAgente, String agenteNombre,
                       Integer nivelInteres, String objecionPrincipal, String opinionPrecio,
                       String proximaAccion) {
    }

    /**
     * KPI de la bandeja por estado + distritos del alcance. Extension aditiva
     * del v2: los cinco contadores se calculan en la BASE sobre el mismo
     * conjunto que pagina {@link #listar}, y los distritos llegan aqui para que
     * el selector sea data-driven sin descargar la agenda.
     *
     * <p>Los filtros {@code estado} y {@code distrito} se ignoran: son los que
     * este resumen acota.
     */
    record ResumenVisitas(long total, long programadas, long reprogramadas, long realizadas,
                          long noRealizadas, long canceladas, List<String> distritos) {
    }

    Pagina<FichaVisita> listar(FiltrosVisita filtros, Actor actor);

    ResumenVisitas resumen(FiltrosVisita filtros, Actor actor);

    /** Agenda: las vivas desde hoy, la mas cercana primero. Tope duro de 8. */
    Pagina<FichaVisita> proximas(int tamano, Actor actor);

    /** Calendario del mes, sin paginar (cable v1). */
    Pagina<FichaVisita> mes(int anio, int mes, Actor actor);

    FichaVisita obtener(long id, Actor actor);

    FichaVisita programar(DatosVisita datos, Actor actor);

    FichaVisita reprogramar(long id, LocalDate fechaVisita, LocalTime horaVisita, Actor actor);

    FichaVisita cancelar(long id, String motivo, Actor actor);

    FichaVisita marcarRealizada(long id, Actor actor);

    FichaVisita marcarNoRealizada(long id, String motivo, Actor actor);

    FichaVisita registrarResultado(long id, DesenlaceVisita desenlace, Actor actor);
}
