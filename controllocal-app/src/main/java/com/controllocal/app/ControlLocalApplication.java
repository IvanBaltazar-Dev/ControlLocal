package com.controllocal.app;

import com.controllocal.dao.*;
import com.controllocal.dao.impl.*;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;
import com.controllocal.model.usuario.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class ControlLocalApplication {

    public static void main(String[] args) {
        String sufijo = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        PersonaDAO personaDAO = new PersonaDAOImpl();
        PropietarioDAO propietarioDAO = new PropietarioDAOImpl();
        LocalComercialDAO localDAO = new LocalComercialDAOImpl();
        BrokerDAO brokerDAO = new BrokerDAOImpl();
        AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
        CaptacionDAO captacionDAO = new CaptacionDAOImpl();
        UsuarioInternoDAO usuarioInternoDAO = new UsuarioInternoDAOImpl();

        System.out.println("=== DEMO CONTROLLOCAL ===");
        System.out.println("Sufijo de ejecucion: " + sufijo);

        try {
            Propietario propietario = demoPropietario(personaDAO, propietarioDAO, sufijo);

            LocalComercial local = demoLocal(
                    localDAO,
                    propietario.getIdPropietario(),
                    sufijo
            );

            Broker broker = demoBroker(
                    personaDAO,
                    usuarioInternoDAO,
                    brokerDAO,
                    sufijo
            );

            AgenteInmobiliario agente = demoAgente(
                    personaDAO,
                    usuarioInternoDAO,
                    agenteDAO,
                    sufijo
            );

            Long idCaptacion = demoCaptacion(
                    captacionDAO,
                    local.getIdLocal(),
                    agente.getIdAgente(),
                    broker.getIdBroker(),
                    sufijo
            );

            demoEliminaciones(
                    captacionDAO,
                    agenteDAO,
                    brokerDAO,
                    localDAO,
                    propietarioDAO,
                    idCaptacion,
                    agente.getIdAgente(),
                    broker.getIdBroker(),
                    local.getIdLocal(),
                    propietario.getIdPropietario()
            );

            System.out.println();
            System.out.println("Demo completada correctamente.");

        } catch (Exception e) {
            System.err.println("Error durante la demo de ControlLocal: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdownMysqlCleanupThread();
        }

    }

    private static Propietario demoPropietario(
            PersonaDAO personaDAO,
            PropietarioDAO propietarioDAO,
            String sufijo
    ) {
        System.out.println();
        System.out.println("----- CRUD PROPIETARIO -----");

        Persona persona = new Persona();
        persona.setTipoPersona(TipoPersona.NATURAL);
        persona.setTipoDocumento("DNI");
        persona.setNumeroDocumento("7" + sufijo);
        persona.setNombresORazonSocial("Propietario Demo " + sufijo);
        persona.setTelefono("900" + sufijo.substring(0, 5));
        persona.setCorreo("propietario." + sufijo.toLowerCase() + "@controllocal.pe");
        persona.setEstado(EstadoActivoInactivo.ACTIVO);

        Long idPersona = personaDAO.crear(persona);
        persona.setIdPersona(idPersona);

        System.out.println("[CREATE PERSONA] ID: " + idPersona);

        Propietario propietario = new Propietario();
        propietario.setPersona(persona);

        Long idPropietario = propietarioDAO.crear(propietario);
        propietario.setIdPropietario(idPropietario);

        System.out.println("[CREATE PROPIETARIO] ID: " + idPropietario);

        Optional<Propietario> encontrado = propietarioDAO.buscarPorId(idPropietario);

        encontrado.ifPresent(item -> {
            Persona p = item.getPersona();

            System.out.println("[READ] Propietario ID: " + item.getIdPropietario()
                    + " | Persona ID: " + (p != null ? p.getIdPersona() : null)
                    + " | Nombre: " + (p != null ? p.getNombresORazonSocial() : null)
                    + " | Estado: " + (p != null ? p.getEstado() : null));
        });

        persona.setTelefono("955" + sufijo.substring(0, 5));
        persona.setCorreo("propietario.actualizado." + sufijo.toLowerCase() + "@controllocal.pe");
        persona.setNombresORazonSocial("Propietario Demo Actualizado " + sufijo);

        boolean actualizado = personaDAO.actualizar(persona);

        System.out.println("[UPDATE PERSONA] Actualizado: " + actualizado);

        propietarioDAO.buscarPorId(idPropietario).ifPresent(item -> {
            Persona p = item.getPersona();

            System.out.println("[READ UPDATE] Nombre: " + (p != null ? p.getNombresORazonSocial() : null)
                    + " | Telefono: " + (p != null ? p.getTelefono() : null)
                    + " | Correo: " + (p != null ? p.getCorreo() : null));
        });

        System.out.println("[LIST] Total propietarios: " + propietarioDAO.listarTodos().size());

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

    private static Broker demoBroker(
            PersonaDAO personaDAO,
            UsuarioInternoDAO usuarioInternoDAO,
            BrokerDAO brokerDAO,
            String sufijo
    ) {
        System.out.println();
        System.out.println("----- CRUD BROKER -----");

        Persona persona = new Persona();
        persona.setTipoPersona(TipoPersona.NATURAL);
        persona.setTipoDocumento("DNI");
        persona.setNumeroDocumento("8" + sufijo);
        persona.setNombresORazonSocial("Broker Demo " + sufijo);
        persona.setTelefono("910" + sufijo.substring(0, 5));
        persona.setCorreo("broker." + sufijo.toLowerCase() + "@controllocal.pe");
        persona.setEstado(EstadoActivoInactivo.ACTIVO);

        Long idPersona = personaDAO.crear(persona);
        persona.setIdPersona(idPersona);

        System.out.println("[CREATE PERSONA BROKER] ID: " + idPersona);

        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setPersona(persona);
        usuario.setNombreUsuario("broker" + sufijo.toLowerCase());
        usuario.setContrasenaHash("HASH_" + sufijo);
        usuario.setEstadoAdministrativo(EstadoActivoInactivo.ACTIVO);
        usuario.setRol(RolUsuarioInterno.BROKER);

        Long idUsuarioInterno = usuarioInternoDAO.crear(usuario);
        usuario.setIdUsuarioInterno(idUsuarioInterno);

        System.out.println("[CREATE USUARIO BROKER] ID: " + idUsuarioInterno);

        Broker broker = new Broker();
        broker.setIdUsuarioInterno(idUsuarioInterno);
        broker.setPersona(persona);
        broker.setNombreUsuario(usuario.getNombreUsuario());
        broker.setContrasenaHash(usuario.getContrasenaHash());
        broker.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        broker.setRol(RolUsuarioInterno.BROKER);
        broker.setCodigoBroker("BRK" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);

        Long idBroker = brokerDAO.crear(broker);
        broker.setIdBroker(idBroker);

        System.out.println("[CREATE BROKER] ID: " + idBroker);

        Optional<Broker> encontrado = brokerDAO.buscarPorId(idBroker);
        encontrado.ifPresent(item -> System.out.println(
                "[READ] Broker ID: " + item.getIdBroker()
                        + " | Usuario ID: " + item.getIdUsuarioInterno()
                        + " | Codigo: " + item.getCodigoBroker()
                        + " | Administrador: " + item.isEsAdministrador()
        ));

        broker.setCodigoBroker("BRK" + sufijo + "A");
        boolean actualizado = brokerDAO.actualizar(broker);

        System.out.println("[UPDATE BROKER] Actualizado: " + actualizado);

        brokerDAO.buscarPorId(idBroker).ifPresent(item -> System.out.println(
                "[READ UPDATE] Codigo: " + item.getCodigoBroker()
                        + " | Administrador: " + item.isEsAdministrador()
        ));

        System.out.println("[LIST] Total brokers: " + brokerDAO.listarTodos().size());

        return broker;
    }

    private static AgenteInmobiliario demoAgente(
            PersonaDAO personaDAO,
            UsuarioInternoDAO usuarioInternoDAO,
            AgenteInmobiliarioDAO agenteDAO,
            String sufijo
    ) {
        System.out.println();
        System.out.println("----- CRUD AGENTE INMOBILIARIO -----");

        // Crear Persona del agente
        Persona persona = new Persona();
        persona.setTipoPersona(TipoPersona.NATURAL);
        persona.setTipoDocumento("DNI");
        persona.setNumeroDocumento("9" + sufijo);
        persona.setNombresORazonSocial("Agente Demo " + sufijo);
        persona.setTelefono("920" + sufijo.substring(0, 5));
        persona.setCorreo("agente." + sufijo.toLowerCase() + "@controllocal.pe");
        persona.setEstado(EstadoActivoInactivo.ACTIVO);

        Long idPersona = personaDAO.crear(persona);
        persona.setIdPersona(idPersona);

        System.out.println("[CREATE PERSONA AGENTE] ID: " + idPersona);

        // Crear UsuarioInterno del agente
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setPersona(persona);
        usuario.setNombreUsuario("agente" + sufijo.toLowerCase());
        usuario.setContrasenaHash("HASH_AGT_" + sufijo);
        usuario.setEstadoAdministrativo(EstadoActivoInactivo.ACTIVO);
        usuario.setRol(RolUsuarioInterno.AGENTE);

        Long idUsuarioInterno = usuarioInternoDAO.crear(usuario);
        usuario.setIdUsuarioInterno(idUsuarioInterno);

        System.out.println("[CREATE USUARIO AGENTE] ID: " + idUsuarioInterno);

        // Crear AgenteInmobiliario asociado al UsuarioInterno
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdUsuarioInterno(idUsuarioInterno);
        agente.setPersona(persona);
        agente.setNombreUsuario(usuario.getNombreUsuario());
        agente.setContrasenaHash(usuario.getContrasenaHash());
        agente.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        agente.setRol(RolUsuarioInterno.AGENTE);

        agente.setCodigoAgente("AGT" + sufijo);
        agente.setZonaAsignada("Miraflores");
        agente.setFechaIngreso(LocalDate.now().minusDays(30));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);

        Long idAgente = agenteDAO.crear(agente);
        agente.setIdAgente(idAgente);

        System.out.println("[CREATE AGENTE] ID: " + idAgente);

        // LEER
        Optional<AgenteInmobiliario> encontrado = agenteDAO.buscarPorId(idAgente);

        encontrado.ifPresent(item -> System.out.println(
                "[READ] Agente ID: " + item.getIdAgente()
                        + " | Usuario ID: " + item.getIdUsuarioInterno()
                        + " | Codigo: " + item.getCodigoAgente()
                        + " | Zona: " + item.getZonaAsignada()
                        + " | Estado operativo: " + item.getEstadoOperativo()
        ));

        // ACTUALIZAR
        agente.setZonaAsignada("San Borja");
        agente.setEstadoOperativo(EstadoOperativoAgente.NO_DISPONIBLE);

        boolean actualizado = agenteDAO.actualizar(agente);

        System.out.println("[UPDATE AGENTE] Actualizado: " + actualizado);

        // LEER ACTUALIZAR
        agenteDAO.buscarPorId(idAgente).ifPresent(item -> System.out.println(
                "[READ UPDATE] Zona: " + item.getZonaAsignada()
                        + " | Estado operativo: " + item.getEstadoOperativo()
        ));

        // LISTAR
        System.out.println("[LIST] Total agentes: " + agenteDAO.listarTodos().size());

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
