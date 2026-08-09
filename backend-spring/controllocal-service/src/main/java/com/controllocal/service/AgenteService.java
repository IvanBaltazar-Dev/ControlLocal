package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso del agente inmobiliario contra el contrato congelado de E1.
 *
 * <p>Sobre el contrato congelado se añaden, de forma <b>aditiva</b>, la ficha
 * individual ({@link #ficha}), el listado filtrable y su resumen. La v1 no
 * tenía ninguno de los tres: su pantalla de detalle se limitaba a los datos
 * personales y su listado se filtraba en el navegador, que con paginación real
 * solo puede filtrar la página visible.
 */
public interface AgenteService {

    /**
     * {@code idBrokerSupervisor} es {@code detalle_broker.id_persona_rol} del
     * broker que se hara cargo del agente (D-S0-17, fila 17). Obligatorio en el
     * alta: el {@code TENANT_ADMIN} que la ejecuta no supervisa a nadie, asi
     * que el equipo de destino no se puede deducir de la sesion. En la
     * actualizacion se ignora — reasignar a otro broker es una operacion con
     * rastro propio ({@code /asignaciones/reasignar}), no un efecto lateral de
     * editar la ficha.
     */
    record DatosAgente(String nombre, String tipoPersona, String tipoDocumento,
                       String numeroDocumento, String telefono, String correo,
                       String usuario, String contrasena, String zona,
                       String codigoAgente, String estado, String estadoOperativo,
                       Long idBrokerSupervisor) {
    }

    record FichaAgente(Long id, String codigoAgente, String nombre,
                       String tipoPersona, String tipoDocumento,
                       String numeroDocumento, String telefono, String correo,
                       String usuario, String zona, LocalDate fechaIngreso,
                       String estadoAdministrativo, String estadoOperativo,
                       int captacionesActivas, int operacionesActivas) {
    }

    /**
     * Filtros ADITIVOS del catálogo. Los cuatro son opcionales: omitidos, la
     * respuesta es exactamente la del cable congelado.
     */
    record FiltrosAgente(String texto, String estado, String estadoOperativo, String zona,
                         int pagina, int tamano) {
    }

    /** Cubos del catálogo, contados en la base sobre el mismo conjunto. */
    record ResumenAgentes(long total, long activos, long inactivos,
                          long disponibles, long ocupados, long vacaciones, long suspendidos,
                          List<String> zonas) {
    }

    /** Supervisión vigente del agente. Nula si nadie lo supervisa hoy. */
    record SupervisionVigente(Long idBroker, String brokerNombre, String codigoBroker,
                              LocalDate fechaAsignacion, String motivo) {
    }

    /** Un importe con su moneda; PEN y USD nunca se suman entre sí. */
    record ImportePorMoneda(String moneda, BigDecimal monto) {
    }

    /**
     * Números económicos del agente, con las tres magnitudes SEPARADAS porque
     * responden preguntas distintas y casi nunca coinciden:
     * <ul>
     *   <li><b>generada</b>: el bruto pactado de sus cierres;</li>
     *   <li><b>cobrada</b>: lo que la corredora recibió de verdad (cobros menos
     *       reversiones);</li>
     *   <li><b>asignada</b>: la parte que el broker le adjudicó a él;</li>
     *   <li><b>pagada</b>: lo que efectivamente se le pagó.</li>
     * </ul>
     * Los dos saldos son diferencias derivadas, nunca negativas.
     */
    record ComisionesAgente(List<ImportePorMoneda> generada,
                            List<ImportePorMoneda> cobrada,
                            List<ImportePorMoneda> pendienteCobro,
                            List<ImportePorMoneda> asignadaAgente,
                            List<ImportePorMoneda> pagadaAgente,
                            List<ImportePorMoneda> pendientePagoAgente) {
    }

    /** Reparto por estado de una máquina del proceso. */
    record ConteoEstado(String estado, String descripcion, long total) {
    }

    /** Cierre atribuido al agente (V27), para la lista corta de la ficha. */
    record CierreDeAgente(Long idContrato, String codigoSolicitud, String direccionLocal,
                          String distrito, String clienteNombre, LocalDate fechaCierre,
                          String estadoContrato, BigDecimal comisionGenerada,
                          String monedaComision) {
    }

    /**
     * Ficha completa del agente: identidad, supervisión vigente, su actividad
     * repartida por estado y el dinero real de sus cierres.
     */
    record FichaCompletaAgente(FichaAgente agente, SupervisionVigente supervision,
                               List<ConteoEstado> captaciones,
                               List<ConteoEstado> oportunidades,
                               List<ConteoEstado> solicitudes,
                               long cierres,
                               ComisionesAgente comisiones,
                               List<CierreDeAgente> ultimosCierres) {
    }

    Pagina<FichaAgente> listar(int pagina, int tamano, Actor actor);

    Pagina<FichaAgente> listar(FiltrosAgente filtros, Actor actor);

    ResumenAgentes resumen(FiltrosAgente filtros, Actor actor);

    /**
     * Ficha individual. El BROKER solo alcanza a los agentes que supervisa;
     * el ADMIN, a todos los del tenant.
     */
    FichaCompletaAgente ficha(long id, Actor actor);

    FichaAgente registrar(DatosAgente datos, Actor actor);

    FichaAgente actualizar(long id, DatosAgente datos, Actor actor);
}
