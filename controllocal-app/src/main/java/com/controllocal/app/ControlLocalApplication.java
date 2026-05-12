package com.controllocal.app;

import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.impl.PropietarioDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class ControlLocalApplication {

    public static void main(String[] args) {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        PropietarioDAO propietarioDAO = new PropietarioDAOImpl();
        LocalComercialDAO localDAO = new LocalComercialDAOImpl();
        BrokerDAO brokerDAO = new BrokerDAOImpl();
        AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
        CaptacionDAO captacionDAO = new CaptacionDAOImpl();

        System.out.println("=== DEMO CONTROLLOCAL ===");
        System.out.println("Sufijo de ejecucion: " + sufijo);

        try {
            Propietario propietario = demoPropietario(propietarioDAO, sufijo);
            LocalComercial local = demoLocal(localDAO, propietario.getIdPropietario(), sufijo);
            Broker broker = demoBroker(brokerDAO, sufijo);
            AgenteInmobiliario agente = demoAgente(agenteDAO, sufijo);
            Long idCaptacion = demoCaptacion(captacionDAO, local.getIdLocal(), agente.getIdAgente(), broker.getIdBroker(), sufijo);
            demoEliminaciones(captacionDAO, agenteDAO, brokerDAO, localDAO, propietarioDAO, idCaptacion,
                    agente.getIdAgente(), broker.getIdBroker(), local.getIdLocal(), propietario.getIdPropietario());

            System.out.println();
            System.out.println("Demo completada correctamente.");
        } catch (Exception e) {
            System.err.println("Error durante la demo de ControlLocal: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdownMysqlCleanupThread();
        }
    }

    private static Propietario demoPropietario(PropietarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD PROPIETARIO -----");

        Propietario propietario = new Propietario();
        propietario.setTipoPersona(TipoPersona.NATURAL);
        propietario.setTipoDocumento("DNI");
        propietario.setNumeroDocumento("7" + sufijo);
        propietario.setNombresORazonSocial("Propietario Demo " + sufijo);
        propietario.setTelefono("900" + sufijo.substring(0, 5));
        propietario.setCorreo("propietario." + sufijo.toLowerCase() + "@controllocal.pe");
        propietario.setEstado(EstadoActivoInactivo.ACTIVO);

        Long id = dao.crear(propietario);
        System.out.println("[CREATE] Propietario creado con ID: " + id);

        Optional<Propietario> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println("[READ]   " + item.getNombresORazonSocial() + " | Estado: " + item.getEstado()));

        if (encontrado.isPresent()) {
            Propietario actualizar = encontrado.get();
            actualizar.setTelefono("955" + sufijo.substring(0, 5));
            actualizar.setCorreo("propietario.actualizado." + sufijo.toLowerCase() + "@controllocal.pe");
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item ->
                System.out.println("[UPDATE] Telefono: " + item.getTelefono() + " | Correo: " + item.getCorreo()));

        System.out.println("[LIST]   Total propietarios: " + dao.listarTodos().size());

        propietario.setIdPropietario(id);
        return propietario;
    }

    private static LocalComercial demoLocal(LocalComercialDAO dao, long idPropietario, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD LOCAL COMERCIAL -----");

        LocalComercial local = new LocalComercial();
        local.setCodigoLocal("LOC" + sufijo);
        local.setDireccion("Av. Demo " + sufijo + " 123");
        local.setDistrito("San Isidro");
        local.setMetraje(new BigDecimal("85.50"));
        local.setPrecioReferencial(new BigDecimal("4500.00"));
        local.setRubroPermitido("Gastronomia");
        local.setDescripcion("Local de demostracion academica");
        local.setEstado(EstadoLocalComercial.DISPONIBLE);
        local.setIdPropietario(idPropietario);

        Long id = dao.crear(local);
        System.out.println("[CREATE] Local creado con ID: " + id);

        Optional<LocalComercial> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println("[READ]   " + item.getCodigoLocal() + " | Precio: " + item.getPrecioReferencial()));

        if (encontrado.isPresent()) {
            LocalComercial actualizar = encontrado.get();
            actualizar.setPrecioReferencial(new BigDecimal("4800.00"));
            actualizar.setDescripcion("Local de demostracion actualizado");
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item ->
                System.out.println("[UPDATE] Precio: " + item.getPrecioReferencial() + " | Estado: " + item.getEstado()));

        System.out.println("[LIST]   Total locales: " + dao.listarTodos().size());

        local.setIdLocal(id);
        return local;
    }

    private static Broker demoBroker(BrokerDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD BROKER -----");

        Broker broker = new Broker();
        broker.setNombres("Broker");
        broker.setApellidos("Demo " + sufijo);
        broker.setCorreo("broker." + sufijo.toLowerCase() + "@controllocal.pe");
        broker.setTelefono("910" + sufijo.substring(0, 5));
        broker.setNombreUsuario("broker" + sufijo.toLowerCase());
        broker.setContrasenaHash("HASH_" + sufijo);
        broker.setEstado(EstadoActivoInactivo.ACTIVO);
        broker.setCodigoBroker("BRK" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);

        Long id = dao.crear(broker);
        System.out.println("[CREATE] Broker creado con ID: " + id);

        Optional<Broker> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println("[READ]   " + item.getCodigoBroker() + " | Administrador: " + item.isEsAdministrador()));

        if (encontrado.isPresent()) {
            Broker actualizar = encontrado.get();
            actualizar.setTelefono("911" + sufijo.substring(0, 5));
            actualizar.setCodigoBroker("BRK" + sufijo + "A");
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item ->
                System.out.println("[UPDATE] Telefono: " + item.getTelefono() + " | Codigo: " + item.getCodigoBroker()));

        System.out.println("[LIST]   Total brokers: " + dao.listarTodos().size());

        broker.setIdBroker(id);
        broker.setIdUsuarioInterno(id);
        return broker;
    }

    private static AgenteInmobiliario demoAgente(AgenteInmobiliarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD AGENTE INMOBILIARIO -----");

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setNombres("Agente");
        agente.setApellidos("Demo " + sufijo);
        agente.setCorreo("agente." + sufijo.toLowerCase() + "@controllocal.pe");
        agente.setTelefono("920" + sufijo.substring(0, 5));
        agente.setNombreUsuario("agente" + sufijo.toLowerCase());
        agente.setContrasenaHash("HASH_AGT_" + sufijo);
        agente.setEstado(EstadoActivoInactivo.ACTIVO);
        agente.setCodigoAgente("AGT" + sufijo);
        agente.setZonaAsignada("Miraflores");
        agente.setFechaIngreso(LocalDate.now().minusDays(30));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);

        Long id = dao.crear(agente);
        System.out.println("[CREATE] Agente creado con ID: " + id);

        Optional<AgenteInmobiliario> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println("[READ]   " + item.getCodigoAgente() + " | Zona: " + item.getZonaAsignada()));

        if (encontrado.isPresent()) {
            AgenteInmobiliario actualizar = encontrado.get();
            actualizar.setZonaAsignada("San Borja");
            actualizar.setEstadoOperativo(EstadoOperativoAgente.NO_DISPONIBLE);
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item ->
                System.out.println("[UPDATE] Zona: " + item.getZonaAsignada() + " | Estado operativo: " + item.getEstadoOperativo()));

        System.out.println("[LIST]   Total agentes: " + dao.listarTodos().size());

        agente.setIdAgente(id);
        agente.setIdUsuarioInterno(id);
        return agente;
    }

    private static Long demoCaptacion(CaptacionDAO dao, long idLocal, long idAgente, long idBroker, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD CAPTACION -----");

        LocalComercial local = new LocalComercial();
        local.setIdLocal(idLocal);

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(idAgente);

        Broker broker = new Broker();
        broker.setIdBroker(idBroker);

        Captacion captacion = new Captacion();
        captacion.setCodigoCaptacion("CAP" + sufijo);
        captacion.setFechaCaptacion(LocalDate.now());
        captacion.setFechaInicioVigencia(LocalDate.now());
        captacion.setComisionPactada(7.5);
        captacion.setObservaciones("Captacion registrada desde main de demo");
        captacion.setEstado(EstadoCaptacion.PENDIENTE_REVISION);
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);

        Long id = dao.crear(captacion);
        System.out.println("[CREATE] Captacion creada con ID: " + id);

        Optional<Captacion> encontrada = dao.buscarPorId(id);
        encontrada.ifPresent(item -> System.out.println("[READ]   " + item.getCodigoCaptacion() + " | Estado: " + item.getEstado()));

        if (encontrada.isPresent()) {
            Captacion actualizar = encontrada.get();
            actualizar.setBrokerRevisor(broker);
            actualizar.setFechaRevision(LocalDateTime.now());
            actualizar.setObservacionRevision("Captacion aprobada en demo");
            actualizar.setEstado(EstadoCaptacion.ACTIVA);
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item ->
                System.out.println("[UPDATE] Estado: " + item.getEstado() + " | Broker revisor ID: "
                        + (item.getBrokerRevisor() != null ? item.getBrokerRevisor().getIdBroker() : null)));

        System.out.println("[LIST]   Total captaciones: " + dao.listarTodos().size());

        return id;
    }

    private static void demoEliminaciones(
            CaptacionDAO captacionDAO,
            AgenteInmobiliarioDAO agenteDAO,
            BrokerDAO brokerDAO,
            LocalComercialDAO localDAO,
            PropietarioDAO propietarioDAO,
            long idCaptacion,
            long idAgente,
            long idBroker,
            long idLocal,
            long idPropietario
    ) {
        System.out.println();
        System.out.println("----- ELIMINACIONES LOGICAS -----");

        captacionDAO.eliminar(idCaptacion);
        captacionDAO.buscarPorId(idCaptacion).ifPresent(item ->
                System.out.println("[CAPTACION] Estado tras cerrar: " + item.getEstado()));

        agenteDAO.eliminar(idAgente);
        agenteDAO.buscarPorId(idAgente).ifPresent(item ->
                System.out.println("[AGENTE]    Estado tras desactivar: " + item.getEstado()));

        brokerDAO.eliminar(idBroker);
        brokerDAO.buscarPorId(idBroker).ifPresent(item ->
                System.out.println("[BROKER]    Estado tras desactivar: " + item.getEstado()));

        localDAO.eliminar(idLocal);
        localDAO.buscarPorId(idLocal).ifPresent(item ->
                System.out.println("[LOCAL]     Estado tras inactivar: " + item.getEstado()));

        propietarioDAO.eliminar(idPropietario);
        propietarioDAO.buscarPorId(idPropietario).ifPresent(item ->
                System.out.println("[PROPIETARIO] Estado tras desactivar: " + item.getEstado()));
    }

    private static void shutdownMysqlCleanupThread() {
        try {
            Class<?> cleanupClass = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            cleanupClass.getMethod("checkedShutdown").invoke(null);
        } catch (Exception ignored) {
        }
    }
}
