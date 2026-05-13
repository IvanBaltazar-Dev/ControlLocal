package com.controllocal.bl;

import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.config.DatabaseConfig;
import com.controllocal.dao.*;
import com.controllocal.model.comercial.*;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.*;
import com.controllocal.model.usuario.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

public class BusinessLogicManualTest {

    public static void main(String[] args) {
        BusinessLogicManualTest runner = new BusinessLogicManualTest();
        runner.ejecutar("No registrar captacion si agente no disponible", runner::noRegistraCaptacionSiAgenteNoDisponible);
        runner.ejecutar("Registrar captacion en pendiente revision", runner::registraCaptacionNuevaConEstadoPendienteRevision);
        runner.ejecutar("Broker aprueba captacion", runner::brokerApruebaCaptacionYCambiaEstadoActiva);
        runner.ejecutar("Broker rechaza captacion", runner::brokerRechazaCaptacionYCambiaEstadoRechazada);
        runner.ejecutar("No broker no revisa captaciones", runner::noPermiteQueNoBrokerReviseCaptaciones);
        runner.ejecutar("Reasignar captacion con historial", runner::reasignaCaptacionRegistrandoHistorial);
        runner.ejecutar("No reasignar a agente no disponible", runner::noPermiteReasignacionSiNuevoAgenteNoDisponible);
        runner.ejecutar("Solo administrador registra brokers", runner::soloBrokerAdministradorPuedeRegistrarBrokers);
        runner.ejecutar("No permitir dos brokers administradores", runner::noPermiteMasDeUnBrokerAdministrador);
        runner.ejecutar("No solicitud sobre captacion no activa", runner::noPermiteSolicitudSobreCaptacionNoActiva);
        runner.ejecutar("Motivo de no continuidad con referencia unica", runner::noPermiteMotivoNoContinuidadConVariasReferencias);
        runner.ejecutar("Rollback ante error", runner::operacionConErrorEjecutaRollback);
        System.out.println();
        System.out.println("Pruebas manuales de Business Logic completadas correctamente.");
    }

    private void ejecutar(String nombre, ManualCheck check) {
        try {
            check.run();
            System.out.println("[OK] " + nombre);
        } catch (Throwable e) {
            throw new RuntimeException("[ERROR] " + nombre + ": " + e.getMessage(), e);
        } finally {
            limpiarConexion();
        }
    }

    public void limpiarConexion() {
        DatabaseConfig.getConnectionHolder().remove();
    }

    public void noRegistraCaptacionSiAgenteNoDisponible() {
        Fixtures fx = new Fixtures();
        fx.agente.setEstadoOperativo(EstadoOperativoAgente.NO_DISPONIBLE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fx.captaciones.registerAcquisition(fx.captacionNueva()));

        assertTrue(ex.getMessage().contains("DISPONIBLE"));
        assertTrue(fx.captacionDAO.items.isEmpty());
    }

    public void registraCaptacionNuevaConEstadoPendienteRevision() {
        Fixtures fx = new Fixtures();

        Long id = fx.captaciones.registerAcquisition(fx.captacionNueva());

        assertEquals(Long.valueOf(1L), id);
        assertEquals(EstadoCaptacion.PENDIENTE_REVISION, fx.captacionDAO.items.get(id).getEstado());
    }

    public void brokerApruebaCaptacionYCambiaEstadoActiva() {
        Fixtures fx = new Fixtures();
        Captacion captacion = fx.guardarCaptacion(EstadoCaptacion.PENDIENTE_REVISION);

        fx.captaciones.aprobarCaptacion(captacion.getIdCaptacion(), fx.broker.getIdBroker(), "Aprobada");

        Captacion actual = fx.captacionDAO.items.get(captacion.getIdCaptacion());
        assertEquals(EstadoCaptacion.ACTIVA, actual.getEstado());
        assertEquals(fx.broker.getIdBroker(), actual.getBrokerRevisor().getIdBroker());
        assertNotNull(actual.getFechaRevision());
    }

    public void brokerRechazaCaptacionYCambiaEstadoRechazada() {
        Fixtures fx = new Fixtures();
        Captacion captacion = fx.guardarCaptacion(EstadoCaptacion.PENDIENTE_REVISION);

        fx.captaciones.rechazarCaptacion(captacion.getIdCaptacion(), fx.broker.getIdBroker(), "Documentos incompletos");

        assertEquals(EstadoCaptacion.RECHAZADA, fx.captacionDAO.items.get(captacion.getIdCaptacion()).getEstado());
    }

    public void noPermiteQueNoBrokerReviseCaptaciones() {
        Fixtures fx = new Fixtures();
        Captacion captacion = fx.guardarCaptacion(EstadoCaptacion.PENDIENTE_REVISION);

        assertThrows(BusinessException.class,
                () -> fx.captaciones.aprobarCaptacion(captacion.getIdCaptacion(), 999L, "Intento invalido"));
    }

    public void reasignaCaptacionRegistrandoHistorial() {
        Fixtures fx = new Fixtures();
        Captacion captacion = fx.guardarCaptacion(EstadoCaptacion.ACTIVA);

        fx.captaciones.reasignarCaptacion(captacion.getIdCaptacion(), fx.agenteNuevo.getIdAgente(), fx.broker.getIdBroker(), "Balance de carga");

        assertEquals(fx.agenteNuevo.getIdAgente(), fx.captacionDAO.items.get(captacion.getIdCaptacion()).getAgenteResponsable().getIdAgente());
        assertEquals(1, fx.reasignacionDAO.items.size());
        ReasignacionCaptacion historial = fx.reasignacionDAO.items.get(1L);
        assertEquals(fx.agente.getIdAgente(), historial.getAgenteAnterior().getIdAgente());
        assertEquals(fx.agenteNuevo.getIdAgente(), historial.getAgenteNuevo().getIdAgente());
        assertEquals(fx.broker.getIdBroker(), historial.getBrokerResponsable().getIdBroker());
        assertNotNull(historial.getFechaCambio());
    }

    public void noPermiteReasignacionSiNuevoAgenteNoDisponible() {
        Fixtures fx = new Fixtures();
        fx.agenteNuevo.setEstadoOperativo(EstadoOperativoAgente.NO_DISPONIBLE);
        Captacion captacion = fx.guardarCaptacion(EstadoCaptacion.ACTIVA);

        assertThrows(BusinessException.class,
                () -> fx.captaciones.reasignarCaptacion(captacion.getIdCaptacion(), fx.agenteNuevo.getIdAgente(), fx.broker.getIdBroker(), "No debe pasar"));
        assertTrue(fx.reasignacionDAO.items.isEmpty());
    }

    public void soloBrokerAdministradorPuedeRegistrarBrokers() {
        Fixtures fx = new Fixtures();
        Broker brokerNuevo = fx.broker(3L, false);

        assertThrows(BusinessException.class,
                () -> fx.brokers.registrarBroker(fx.brokerNoAdmin.getIdBroker(), brokerNuevo));
    }

    public void noPermiteMasDeUnBrokerAdministrador() {
        Fixtures fx = new Fixtures();
        Broker segundoAdmin = fx.broker(3L, true);

        assertThrows(BusinessException.class,
                () -> fx.brokers.registrarBroker(fx.broker.getIdBroker(), segundoAdmin));
    }

    public void noPermiteSolicitudSobreCaptacionNoActiva() {
        Fixtures fx = new Fixtures();
        Captacion captacion = fx.guardarCaptacion(EstadoCaptacion.PENDIENTE_REVISION);
        SolicitudAlquiler solicitud = fx.solicitud(captacion);

        assertThrows(BusinessException.class, () -> fx.solicitudes.registrar(solicitud));
    }

    public void noPermiteMotivoNoContinuidadConVariasReferencias() {
        Fixtures fx = new Fixtures();
        MotivoNoContinuidad motivo = fx.motivo();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> motivo.setInteraccionComercial(new InteraccionComercial()));
        assertTrue(ex.getMessage().contains("Solo una referencia"));
    }

    public void operacionConErrorEjecutaRollback() throws Exception {
        JdbcRecorder recorder = new JdbcRecorder();
        Connection connection = recorder.connection();
        DatabaseConfig.getConnectionHolder().set(connection);

        assertThrows(BusinessException.class, () -> TransactionRunner.write(() -> {
            throw new BusinessException("Falla controlada");
        }));

        assertFalse(recorder.commitCalled);
        assertTrue(recorder.rollbackCalled);
        assertTrue(recorder.closeCalled);
    }

    @FunctionalInterface
    private interface ManualCheck {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Se esperaba verdadero, pero fue falso.");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Se esperaba falso, pero fue verdadero.");
        }
    }

    private static void assertNotNull(Object value) {
        if (value == null) {
            throw new AssertionError("Se esperaba un valor no nulo.");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Se esperaba [" + expected + "], pero fue [" + actual + "].");
        }
    }

    private static <T extends Throwable> T assertThrows(Class<T> expectedType, CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable error) {
            if (expectedType.isInstance(error)) {
                return expectedType.cast(error);
            }
            throw new AssertionError("Se esperaba excepcion " + expectedType.getSimpleName()
                    + ", pero fue " + error.getClass().getSimpleName() + ".", error);
        }
        throw new AssertionError("Se esperaba excepcion " + expectedType.getSimpleName() + ", pero no se lanzo ninguna.");
    }

    private static class Fixtures {
        final InMemoryCaptacionDAO captacionDAO = new InMemoryCaptacionDAO();
        final InMemoryAgenteDAO agenteDAO = new InMemoryAgenteDAO();
        final InMemoryBrokerDAO brokerDAO = new InMemoryBrokerDAO();
        final InMemoryReasignacionDAO reasignacionDAO = new InMemoryReasignacionDAO();
        final InMemorySolicitudDAO solicitudDAO = new InMemorySolicitudDAO();
        final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogic(captacionDAO, agenteDAO, reasignacionDAO, brokerDAO);
        final BrokerBusinessLogic brokers = new BrokerBusinessLogic(brokerDAO);
        final SolicitudAlquilerBusinessLogic solicitudes = new SolicitudAlquilerBusinessLogic(solicitudDAO, captacionDAO);
        final AgenteInmobiliario agente = agente(1L, EstadoOperativoAgente.DISPONIBLE);
        final AgenteInmobiliario agenteNuevo = agente(2L, EstadoOperativoAgente.DISPONIBLE);
        final Broker broker = broker(1L, true);
        final Broker brokerNoAdmin = broker(2L, false);

        Fixtures() {
            agenteDAO.items.put(agente.getIdAgente(), agente);
            agenteDAO.items.put(agenteNuevo.getIdAgente(), agenteNuevo);
            brokerDAO.items.put(broker.getIdBroker(), broker);
            brokerDAO.items.put(brokerNoAdmin.getIdBroker(), brokerNoAdmin);
        }

        Captacion captacionNueva() {
            Captacion captacion = new Captacion();
            captacion.setCodigoCaptacion("CAP-001");
            captacion.setFechaCaptacion(LocalDate.now());
            captacion.setComisionPactada(new BigDecimal("5.50"));
            captacion.setLocalComercial(local(1L));
            captacion.setAgenteResponsable(agente);
            return captacion;
        }

        Captacion guardarCaptacion(EstadoCaptacion estado) {
            Captacion captacion = captacionNueva();
            captacion.setEstado(estado);
            captacion.setIdCaptacion(captacionDAO.crear(captacion));
            return captacion;
        }

        SolicitudAlquiler solicitud(Captacion captacion) {
            SolicitudAlquiler solicitud = new SolicitudAlquiler();
            solicitud.setCodigoSolicitud("SOL-001");
            solicitud.setFechaRegistro(LocalDate.now());
            solicitud.setMontoPropuesto(new BigDecimal("3000.00"));
            ClienteInteresado cliente = new ClienteInteresado();
            cliente.setIdCliente(1L);
            solicitud.setClienteInteresado(cliente);
            solicitud.setCaptacion(captacion);
            solicitud.setAgenteResponsable(agente);
            return solicitud;
        }

        MotivoNoContinuidad motivo() {
            MotivoNoContinuidad motivo = new MotivoNoContinuidad();
            motivo.setRazonPrincipal("No continua");
            motivo.setAgenteResponsable(agente);
            SolicitudAlquiler solicitud = new SolicitudAlquiler();
            solicitud.setIdSolicitud(1L);
            motivo.setSolicitudAlquiler(solicitud);
            return motivo;
        }

        AgenteInmobiliario agente(Long id, EstadoOperativoAgente estado) {
            AgenteInmobiliario agente = new AgenteInmobiliario();
            agente.setIdAgente(id);
            agente.setIdUsuarioInterno(id);
            agente.setCodigoAgente("AGT-" + id);
            agente.setFechaIngreso(LocalDate.now());
            agente.setEstadoOperativo(estado);
            agente.setRol(RolUsuarioInterno.AGENTE);
            return agente;
        }

        Broker broker(Long id, boolean admin) {
            Broker broker = new Broker();
            broker.setIdBroker(id);
            broker.setIdUsuarioInterno(id);
            broker.setCodigoBroker("BRK-" + id);
            broker.setFechaDesignacion(LocalDate.now());
            broker.setRol(RolUsuarioInterno.BROKER);
            broker.setEstadoAdministrativo(EstadoActivoInactivo.ACTIVO);
            broker.setEsAdministrador(admin);
            return broker;
        }

        LocalComercial local(Long id) {
            LocalComercial local = new LocalComercial();
            local.setIdLocal(id);
            return local;
        }
    }

    private static class InMemoryCaptacionDAO implements CaptacionDAO {
        final Map<Long, Captacion> items = new LinkedHashMap<>();
        long sequence = 1;

        public Long crear(Captacion captacion) {
            captacion.setIdCaptacion(sequence++);
            items.put(captacion.getIdCaptacion(), captacion);
            return captacion.getIdCaptacion();
        }

        public Optional<Captacion> buscarPorId(Long id) { return Optional.ofNullable(items.get(id)); }
        public List<Captacion> listarTodos() { return new ArrayList<>(items.values()); }
        public boolean actualizar(Captacion captacion) { items.put(captacion.getIdCaptacion(), captacion); return true; }
        public boolean eliminar(Long id) { items.get(id).setEstado(EstadoCaptacion.CERRADA); return true; }
    }

    private static class InMemoryAgenteDAO implements AgenteInmobiliarioDAO {
        final Map<Long, AgenteInmobiliario> items = new LinkedHashMap<>();
        public Long crear(AgenteInmobiliario agente) { items.put(agente.getIdAgente(), agente); return agente.getIdAgente(); }
        public Optional<AgenteInmobiliario> buscarPorId(Long id) { return Optional.ofNullable(items.get(id)); }
        public List<AgenteInmobiliario> listarTodos() { return new ArrayList<>(items.values()); }
        public boolean actualizar(AgenteInmobiliario agente) { items.put(agente.getIdAgente(), agente); return true; }
        public boolean eliminar(Long id) { return items.remove(id) != null; }
    }

    private static class InMemoryBrokerDAO implements BrokerDAO {
        final Map<Long, Broker> items = new LinkedHashMap<>();
        long sequence = 10;
        public Long crear(Broker broker) { broker.setIdBroker(sequence++); items.put(broker.getIdBroker(), broker); return broker.getIdBroker(); }
        public Optional<Broker> buscarPorId(Long id) { return Optional.ofNullable(items.get(id)); }
        public List<Broker> listarTodos() { return new ArrayList<>(items.values()); }
        public boolean actualizar(Broker broker) { items.put(broker.getIdBroker(), broker); return true; }
        public boolean eliminar(Long id) { return items.remove(id) != null; }
    }

    private static class InMemoryReasignacionDAO implements ReasignacionCaptacionDAO {
        final Map<Long, ReasignacionCaptacion> items = new LinkedHashMap<>();
        long sequence = 1;
        public Long crear(ReasignacionCaptacion reasignacion) { reasignacion.setIdReasignacion(sequence++); items.put(reasignacion.getIdReasignacion(), reasignacion); return reasignacion.getIdReasignacion(); }
        public Optional<ReasignacionCaptacion> buscarPorId(Long id) { return Optional.ofNullable(items.get(id)); }
        public List<ReasignacionCaptacion> listarTodos() { return new ArrayList<>(items.values()); }
        public boolean actualizar(ReasignacionCaptacion reasignacion) { items.put(reasignacion.getIdReasignacion(), reasignacion); return true; }
        public boolean eliminar(Long id) { return items.remove(id) != null; }
    }

    private static class InMemorySolicitudDAO implements SolicitudAlquilerDAO {
        final Map<Long, SolicitudAlquiler> items = new LinkedHashMap<>();
        long sequence = 1;
        public Long crear(SolicitudAlquiler solicitud) { solicitud.setIdSolicitud(sequence++); items.put(solicitud.getIdSolicitud(), solicitud); return solicitud.getIdSolicitud(); }
        public Optional<SolicitudAlquiler> buscarPorId(Long id) { return Optional.ofNullable(items.get(id)); }
        public List<SolicitudAlquiler> listarTodos() { return new ArrayList<>(items.values()); }
        public boolean actualizar(SolicitudAlquiler solicitud) { items.put(solicitud.getIdSolicitud(), solicitud); return true; }
        public boolean eliminar(Long id) { return items.remove(id) != null; }
    }

    private static class JdbcRecorder implements InvocationHandler {
        boolean commitCalled;
        boolean rollbackCalled;
        boolean closeCalled;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this
            );
        }

        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "commit" -> commitCalled = true;
                case "rollback" -> rollbackCalled = true;
                case "close" -> closeCalled = true;
                case "isClosed" -> {
                    return false;
                }
                default -> {
                    return defaultValue(method.getReturnType());
                }
            }
            return null;
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class || returnType == long.class || returnType == short.class || returnType == byte.class) {
                return 0;
            }
            return null;
        }
    }
}
