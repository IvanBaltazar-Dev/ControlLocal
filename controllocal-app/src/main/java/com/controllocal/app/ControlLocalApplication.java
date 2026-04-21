package com.controllocal.app;

import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.UsuarioInterno;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class ControlLocalApplication {

    public static void main(String[] args) {
        demoBroker();
        demoAgenteInmobiliario();
        demoUsuarioInterno();
    }

    // =========================================================
    // DEMO CRUD - BROKER
    // =========================================================
    private static void demoBroker() {
        System.out.println("\n========== CRUD BROKER ==========");
        BrokerDAO dao = new BrokerDAOImpl();

        // CREATE
        Broker broker = new Broker();
        broker.setNombres("Carlos");
        broker.setApellidos("Mendoza Torres");
        broker.setCorreo("carlos.mendoza@controllocal.com");
        broker.setTelefono("987654321");
        broker.setNombreUsuario("cmendoza");
        broker.setContrasenaHash("HASH_DEMO_001");
        broker.setEstado(EstadoActivoInactivo.ACTIVO);
        broker.setCodigoBroker("BRK-DEMO-01");
        broker.setFechaDesignacion(LocalDate.of(2024, 1, 15));

        Long id = dao.crear(broker);
        System.out.println("[CREATE] Broker creado con ID: " + id);

        // READ por ID
        Optional<Broker> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(b -> {
            System.out.println("[READ]   Broker encontrado: "
                    + b.getNombres() + " " + b.getApellidos()
                    + " | Codigo: " + b.getCodigoBroker()
                    + " | Estado: " + b.getEstado());
        });

        // UPDATE
        if (encontrado.isPresent()) {
            Broker actualizar = encontrado.get();
            actualizar.setTelefono("999111222");
            actualizar.setCodigoBroker("BRK-DEMO-01-V2");
            boolean ok = dao.actualizar(actualizar);
            System.out.println("[UPDATE] Broker actualizado: " + ok);

            dao.buscarPorId(id).ifPresent(b ->
                System.out.println("[READ]   Tras update - Telefono: " + b.getTelefono()
                        + " | Codigo: " + b.getCodigoBroker())
            );
        }

        // LIST
        List<Broker> todos = dao.listarTodos();
        System.out.println("[LIST]   Total brokers en BD: " + todos.size());

        // DELETE
        boolean eliminado = dao.eliminar(id);
        System.out.println("[DELETE] Broker eliminado: " + eliminado);
        System.out.println("[READ]   Existe tras eliminar: " + dao.buscarPorId(id).isPresent());
    }

    // =========================================================
    // DEMO CRUD - AGENTE INMOBILIARIO
    // =========================================================
    private static void demoAgenteInmobiliario() {
        System.out.println("\n========== CRUD AGENTE INMOBILIARIO ==========");
        AgenteInmobiliarioDAO dao = new AgenteInmobiliarioDAOImpl();

        // CREATE
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setNombres("Maria");
        agente.setApellidos("Quispe Rojas");
        agente.setCorreo("maria.quispe@controllocal.com");
        agente.setTelefono("912345678");
        agente.setNombreUsuario("mquispe");
        agente.setContrasenaHash("HASH_DEMO_002");
        agente.setEstado(EstadoActivoInactivo.ACTIVO);
        agente.setCodigoAgente("AGT-DEMO-01");
        agente.setZonaAsignada("Miraflores");
        agente.setFechaIngreso(LocalDate.of(2023, 6, 1));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);

        Long id = dao.crear(agente);
        System.out.println("[CREATE] Agente creado con ID: " + id);

        // READ por ID
        Optional<AgenteInmobiliario> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(a -> {
            System.out.println("[READ]   Agente encontrado: "
                    + a.getNombres() + " " + a.getApellidos()
                    + " | Zona: " + a.getZonaAsignada()
                    + " | Estado operativo: " + a.getEstadoOperativo());
        });

        // UPDATE
        if (encontrado.isPresent()) {
            AgenteInmobiliario actualizar = encontrado.get();
            actualizar.setZonaAsignada("San Isidro");
            actualizar.setEstadoOperativo(EstadoOperativoAgente.NO_DISPONIBLE);
            boolean ok = dao.actualizar(actualizar);
            System.out.println("[UPDATE] Agente actualizado: " + ok);

            dao.buscarPorId(id).ifPresent(a ->
                System.out.println("[READ]   Tras update - Zona: " + a.getZonaAsignada()
                        + " | Estado operativo: " + a.getEstadoOperativo())
            );
        }

        // LIST
        List<AgenteInmobiliario> todos = dao.listarTodos();
        System.out.println("[LIST]   Total agentes en BD: " + todos.size());

        // DELETE
        boolean eliminado = dao.eliminar(id);
        System.out.println("[DELETE] Agente eliminado: " + eliminado);
        System.out.println("[READ]   Existe tras eliminar: " + dao.buscarPorId(id).isPresent());
    }

    // =========================================================
    // DEMO CRUD - USUARIO INTERNO (lectura y actualizacion)
    // Se crea un broker y un agente para luego leerlos
    // y actualizarlos desde UsuarioInternoDAO.
    // =========================================================
    private static void demoUsuarioInterno() {
        System.out.println("\n========== CRUD USUARIO INTERNO ==========");

        BrokerDAO brokerDao = new BrokerDAOImpl();
        AgenteInmobiliarioDAO agenteDao = new AgenteInmobiliarioDAOImpl();
        UsuarioInternoDAO usuarioDao = new UsuarioInternoDAOImpl();

        // Crear datos de prueba
        Broker broker = new Broker();
        broker.setNombres("Luis");
        broker.setApellidos("Perez Vidal");
        broker.setCorreo("luis.perez@controllocal.com");
        broker.setTelefono("911223344");
        broker.setNombreUsuario("lperez");
        broker.setContrasenaHash("HASH_DEMO_003");
        broker.setEstado(EstadoActivoInactivo.ACTIVO);
        broker.setCodigoBroker("BRK-DEMO-02");
        broker.setFechaDesignacion(LocalDate.of(2024, 3, 10));
        Long idBroker = brokerDao.crear(broker);

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setNombres("Ana");
        agente.setApellidos("Flores Diaz");
        agente.setCorreo("ana.flores@controllocal.com");
        agente.setTelefono("933445566");
        agente.setNombreUsuario("aflores");
        agente.setContrasenaHash("HASH_DEMO_004");
        agente.setEstado(EstadoActivoInactivo.ACTIVO);
        agente.setCodigoAgente("AGT-DEMO-02");
        agente.setZonaAsignada("Barranco");
        agente.setFechaIngreso(LocalDate.of(2023, 9, 15));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);
        Long idAgente = agenteDao.crear(agente);

        // READ por ID (desde UsuarioInternoDAO)
        Optional<UsuarioInterno> usuarioBroker = usuarioDao.buscarPorId(idBroker);
        usuarioBroker.ifPresent(u ->
            System.out.println("[READ]   Usuario (Broker): "
                    + u.getNombres() + " | Rol: " + u.getRol())
        );

        Optional<UsuarioInterno> usuarioAgente = usuarioDao.buscarPorId(idAgente);
        usuarioAgente.ifPresent(u ->
            System.out.println("[READ]   Usuario (Agente): "
                    + u.getNombres() + " | Rol: " + u.getRol())
        );

        // LIST (lista todos los usuarios internos)
        List<UsuarioInterno> todos = usuarioDao.listarTodos();
        System.out.println("[LIST]   Total usuarios internos en BD: " + todos.size());
        for (UsuarioInterno u : todos) {
            System.out.println("         - " + u.getNombres() + " " + u.getApellidos()
                    + " [" + u.getRol() + "] Estado: " + u.getEstado());
        }

        // UPDATE (cambia solo campos base de usuario_interno)
        usuarioBroker.ifPresent(u -> {
            u.setTelefono("900000001");
            boolean ok = usuarioDao.actualizar(u);
            System.out.println("[UPDATE] UsuarioInterno (Broker) actualizado: " + ok);
        });

        usuarioAgente.ifPresent(u -> {
            u.setEstado(EstadoActivoInactivo.INACTIVO);
            boolean ok = usuarioDao.actualizar(u);
            System.out.println("[UPDATE] UsuarioInterno (Agente) desactivado: " + ok);
        });

        // DELETE (limpieza de datos de prueba)
        usuarioDao.eliminar(idBroker);
        usuarioDao.eliminar(idAgente);
        System.out.println("[DELETE] Usuarios de prueba eliminados.");
    }
}
