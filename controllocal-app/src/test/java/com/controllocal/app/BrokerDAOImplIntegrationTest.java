package com.controllocal.app;

import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

import java.time.LocalDate;
import java.util.Optional;

public class BrokerDAOImplIntegrationTest {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);
        BrokerDAO dao = new BrokerDAOImpl();

        try {
            demoBroker(dao, sufijo);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error en demo de BrokerDAO.", e);
        }
    }

    private static Broker demoBroker(BrokerDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD BROKER -----");

        UsuarioInterno usuario = ManualTestSupport.crearUsuarioInterno(
                "83" + sufijo,
                "Broker Demo " + sufijo,
                "950" + sufijo.substring(0, 5),
                "broker." + sufijo.toLowerCase() + "@controllocal.pe",
                "broker" + sufijo.toLowerCase(),
                "HASH_BROKER_" + sufijo,
                RolUsuarioInterno.BROKER
        );

        Broker broker = new Broker();
        broker.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        broker.setPersona(usuario.getPersona());
        broker.setNombreUsuario(usuario.getNombreUsuario());
        broker.setContrasenaHash(usuario.getContrasenaHash());
        broker.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        broker.setRol(RolUsuarioInterno.BROKER);
        broker.setCodigoBroker("BRK" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);

        Long id = dao.crear(broker);
        System.out.println("[CREATE] Broker creado con ID: " + id);

        Optional<Broker> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoBroker() + " | Administrador: " + item.getEsAdministrador()
        ));

        if (encontrado.isPresent()) {
            Broker actualizar = encontrado.get();
            actualizar.setTelefono("951" + sufijo.substring(0, 5));
            actualizar.setCodigoBroker("UBR" + sufijo);
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[UPDATE] Codigo: " + item.getCodigoBroker() + " | Telefono: " + item.getTelefono()
        ));

        System.out.println("[LIST]   Total brokers: " + dao.listarTodos().size());

        dao.eliminar(id);
        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[DELETE] Estado final: " + item.getEstado()
        ));

        broker.setIdBroker(id);
        return broker;
    }
}
