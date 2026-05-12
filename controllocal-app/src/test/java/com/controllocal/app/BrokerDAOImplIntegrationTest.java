package com.controllocal.app;

import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.usuario.Broker;

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

        Broker broker = new Broker();
        broker.setNombres("Broker Demo");
        broker.setApellidos("Apellido " + sufijo);
        broker.setCorreo("broker." + sufijo.toLowerCase() + "@controllocal.pe");
        broker.setTelefono("950" + sufijo.substring(0, 5));
        broker.setNombreUsuario("broker" + sufijo.toLowerCase());
        broker.setContrasenaHash("HASH_BROKER_" + sufijo);
        broker.setEstado(EstadoActivoInactivo.ACTIVO);
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
        broker.setIdUsuarioInterno(id);
        return broker;
    }
}
