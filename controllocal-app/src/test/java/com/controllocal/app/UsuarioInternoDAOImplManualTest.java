package com.controllocal.app;

import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.RolUsuarioInterno;
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
        UsuarioInterno usuario = ManualTestSupport.crearUsuarioInterno(
                "81" + sufijo,
                "Broker Usuario Demo " + sufijo,
                "970" + sufijo.substring(0, 5),
                "broker.usuario." + sufijo.toLowerCase() + "@controllocal.pe",
                "usrbroker" + sufijo.toLowerCase(),
                "HASH_BROKER_USR_" + sufijo,
                RolUsuarioInterno.BROKER
        );

        Broker broker = new Broker();
        broker.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        broker.setPersona(usuario.getPersona());
        broker.setNombreUsuario(usuario.getNombreUsuario());
        broker.setContrasenaHash(usuario.getContrasenaHash());
        broker.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        broker.setRol(RolUsuarioInterno.BROKER);
        broker.setCodigoBroker("UBK" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);
        broker.setIdBroker(dao.crear(broker));
        return broker;
    }

    private static AgenteInmobiliario demoAgente(AgenteInmobiliarioDAO dao, String sufijo) {
        UsuarioInterno usuario = ManualTestSupport.crearUsuarioInterno(
                "82" + sufijo,
                "Agente Usuario Demo " + sufijo,
                "971" + sufijo.substring(0, 5),
                "agente.usuario." + sufijo.toLowerCase() + "@controllocal.pe",
                "usragente" + sufijo.toLowerCase(),
                "HASH_AGENTE_USR_" + sufijo,
                RolUsuarioInterno.AGENTE
        );

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        agente.setPersona(usuario.getPersona());
        agente.setNombreUsuario(usuario.getNombreUsuario());
        agente.setContrasenaHash(usuario.getContrasenaHash());
        agente.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        agente.setRol(RolUsuarioInterno.AGENTE);
        agente.setCodigoAgente("UAG" + sufijo);
        agente.setZonaAsignada("Lince");
        agente.setFechaIngreso(LocalDate.now().minusDays(3));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);
        agente.setIdAgente(dao.crear(agente));
        return agente;
    }

    private static void demoUsuarioInterno(UsuarioInternoDAO dao, Long idBrokerUsuario, Long idAgenteUsuario) {
        System.out.println();
        System.out.println("----- CRUD USUARIO INTERNO -----");

        List<UsuarioInterno> usuarios = dao.listarTodos();
        System.out.println("[LIST]   Total usuarios internos: " + usuarios.size());

        Optional<UsuarioInterno> encontrado = dao.buscarPorId(idBrokerUsuario);
        encontrado.ifPresent(item -> System.out.println(
                "[READ]   " + item.getIdUsuarioInterno() + " | " + item.getNombreUsuario()
                        + " | Rol: " + item.getRol()
        ));

        if (encontrado.isPresent()) {
            UsuarioInterno actualizar = encontrado.get();
            actualizar.setTelefono("999999999");
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(idBrokerUsuario).ifPresent(item -> System.out.println(
                "[UPDATE] Telefono: " + item.getTelefono() + " | Estado: " + item.getEstado()
        ));

        dao.eliminar(idBrokerUsuario);
        dao.eliminar(idAgenteUsuario);

        dao.buscarPorId(idBrokerUsuario).ifPresent(item -> System.out.println(
                "[DELETE] Broker estado final: " + item.getEstado()
        ));
        dao.buscarPorId(idAgenteUsuario).ifPresent(item -> System.out.println(
                "[DELETE] Agente estado final: " + item.getEstado()
        ));
    }
}
