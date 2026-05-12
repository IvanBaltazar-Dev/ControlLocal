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
import com.controllocal.model.usuario.UsuarioInterno;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class UsuarioInternoDAOImplManualTest {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);
        BrokerDAO brokerDAO = new BrokerDAOImpl();
        AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
        UsuarioInternoDAO usuarioInternoDAO = new UsuarioInternoDAOImpl();

        try {
            Broker broker = demoBroker(brokerDAO, sufijo);
            AgenteInmobiliario agente = demoAgente(agenteDAO, sufijo);
            demoUsuarioInterno(usuarioInternoDAO, broker.getIdUsuarioInterno(), agente.getIdUsuarioInterno());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error en demo de UsuarioInternoDAO.", e);
        }
    }

    private static Broker demoBroker(BrokerDAO dao, String sufijo) {
        Broker broker = new Broker();
        broker.setNombres("Broker Usuario");
        broker.setApellidos("Demo " + sufijo);
        broker.setCorreo("broker.usuario." + sufijo.toLowerCase() + "@controllocal.pe");
        broker.setTelefono("970" + sufijo.substring(0, 5));
        broker.setNombreUsuario("usrbroker" + sufijo.toLowerCase());
        broker.setContrasenaHash("HASH_BROKER_USR_" + sufijo);
        broker.setEstado(EstadoActivoInactivo.ACTIVO);
        broker.setCodigoBroker("UBK" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);

        Long id = dao.crear(broker);
        broker.setIdBroker(id);
        broker.setIdUsuarioInterno(id);
        return broker;
    }

    private static AgenteInmobiliario demoAgente(AgenteInmobiliarioDAO dao, String sufijo) {
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setNombres("Agente Usuario");
        agente.setApellidos("Demo " + sufijo);
        agente.setCorreo("agente.usuario." + sufijo.toLowerCase() + "@controllocal.pe");
        agente.setTelefono("971" + sufijo.substring(0, 5));
        agente.setNombreUsuario("usragente" + sufijo.toLowerCase());
        agente.setContrasenaHash("HASH_AGENTE_USR_" + sufijo);
        agente.setEstado(EstadoActivoInactivo.ACTIVO);
        agente.setCodigoAgente("UAG" + sufijo);
        agente.setZonaAsignada("Lince");
        agente.setFechaIngreso(LocalDate.now().minusDays(3));
        agente.setEstadoOperativo(com.controllocal.model.usuario.EstadoOperativoAgente.DISPONIBLE);

        Long id = dao.crear(agente);
        agente.setIdAgente(id);
        agente.setIdUsuarioInterno(id);
        return agente;
    }

    private static void demoUsuarioInterno(UsuarioInternoDAO dao, Long idBroker, Long idAgente) {
        System.out.println();
        System.out.println("----- CRUD USUARIO INTERNO -----");

        List<UsuarioInterno> usuarios = dao.listarTodos();
        System.out.println("[LIST]   Total usuarios internos: " + usuarios.size());

        Optional<UsuarioInterno> encontrado = dao.buscarPorId(idBroker);
        encontrado.ifPresent(item -> System.out.println(
                "[READ]   " + item.getIdUsuarioInterno() + " | " + item.getNombreUsuario()
                        + " | Rol: " + item.getRol()
        ));

        if (encontrado.isPresent()) {
            UsuarioInterno actualizar = encontrado.get();
            actualizar.setTelefono("999999999");
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(idBroker).ifPresent(item -> System.out.println(
                "[UPDATE] Telefono: " + item.getTelefono() + " | Estado: " + item.getEstado()
        ));

        dao.eliminar(idBroker);
        dao.eliminar(idAgente);

        dao.buscarPorId(idBroker).ifPresent(item -> System.out.println(
                "[DELETE] Broker estado final: " + item.getEstado()
        ));
        dao.buscarPorId(idAgente).ifPresent(item -> System.out.println(
                "[DELETE] Agente estado final: " + item.getEstado()
        ));
    }
}
