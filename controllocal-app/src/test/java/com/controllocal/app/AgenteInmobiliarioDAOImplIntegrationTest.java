package com.controllocal.app;

import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.EstadoOperativoAgente;

import java.time.LocalDate;
import java.util.Optional;

public class AgenteInmobiliarioDAOImplIntegrationTest {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);
        AgenteInmobiliarioDAO dao = new AgenteInmobiliarioDAOImpl();

        try {
            demoAgenteInmobiliario(dao, sufijo);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error en demo de AgenteInmobiliarioDAO.", e);
        }
    }

    private static AgenteInmobiliario demoAgenteInmobiliario(AgenteInmobiliarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD AGENTE INMOBILIARIO -----");

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setNombres("Agente Demo");
        agente.setApellidos("Apellido " + sufijo);
        agente.setCorreo("agente." + sufijo.toLowerCase() + "@controllocal.pe");
        agente.setTelefono("960" + sufijo.substring(0, 5));
        agente.setNombreUsuario("agente" + sufijo.toLowerCase());
        agente.setContrasenaHash("HASH_AGENTE_" + sufijo);
        agente.setEstado(EstadoActivoInactivo.ACTIVO);
        agente.setCodigoAgente("AGT" + sufijo);
        agente.setZonaAsignada("Surco");
        agente.setFechaIngreso(LocalDate.now().minusDays(7));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);

        Long id = dao.crear(agente);
        System.out.println("[CREATE] Agente creado con ID: " + id);

        Optional<AgenteInmobiliario> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoAgente() + " | Zona: " + item.getZonaAsignada()
                        + " | Estado: " + item.getEstadoOperativo()
        ));

        if (encontrado.isPresent()) {
            AgenteInmobiliario actualizar = encontrado.get();
            actualizar.setZonaAsignada("Magdalena");
            actualizar.setEstadoOperativo(EstadoOperativoAgente.NO_DISPONIBLE);
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[UPDATE] Zona: " + item.getZonaAsignada() + " | Estado operativo: " + item.getEstadoOperativo()
        ));

        System.out.println("[LIST]   Total agentes: " + dao.listarTodos().size());

        dao.eliminar(id);
        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[DELETE] Estado final: " + item.getEstado()
        ));

        agente.setIdAgente(id);
        agente.setIdUsuarioInterno(id);
        return agente;
    }
}
