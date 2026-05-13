package com.controllocal.app;

import com.controllocal.dao.PropietarioDAO;
import com.controllocal.dao.impl.PropietarioDAOImpl;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;

import java.util.Optional;

public class PropietarioDAOImplIntegrationTest {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);
        PropietarioDAO dao = new PropietarioDAOImpl();

        try {
            demoPropietario(dao, sufijo);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error en demo de PropietarioDAO.", e);
        }
    }

    private static Propietario demoPropietario(PropietarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD PROPIETARIO -----");

        Persona persona = ManualTestSupport.crearPersona(
                "85" + sufijo,
                "Propietario Demo " + sufijo,
                "900" + sufijo.substring(0, 5),
                "propietario." + sufijo.toLowerCase() + "@controllocal.pe"
        );
        Propietario propietario = new Propietario();
        propietario.setPersona(persona);

        Long id = dao.crear(propietario);
        System.out.println("[CREATE] Propietario creado con ID: " + id);

        Optional<Propietario> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println(
                "[READ]   " + item.getNombresORazonSocial() + " | Estado: " + item.getEstado()
        ));

        if (encontrado.isPresent()) {
            Propietario actualizar = encontrado.get();
            actualizar.setTelefono("955" + sufijo.substring(0, 5));
            actualizar.setCorreo("propietario.actualizado." + sufijo.toLowerCase() + "@controllocal.pe");
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[UPDATE] Telefono: " + item.getTelefono() + " | Correo: " + item.getCorreo()
        ));

        System.out.println("[LIST]   Total propietarios: " + dao.listarTodos().size());

        dao.eliminar(id);
        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[DELETE] Estado final: " + item.getEstado()
        ));

        propietario.setIdPropietario(id);
        return propietario;
    }
}
