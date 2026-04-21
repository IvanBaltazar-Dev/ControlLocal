package com.controllocal.app;

import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class LocalComercialManualTest {

    public static void main(String[] args) {
        LocalComercialDAO dao = new LocalComercialDAOImpl();

        try {
            System.out.println("==== INICIO PRUEBA MANUAL CRUD LOCAL COMERCIAL ====");

            // 1. Crear
            LocalComercial local = new LocalComercial();
            local.setCodigoLocal("LC-TEST-001");
            local.setDireccion("Av. Prueba 123");
            local.setDistrito("San Isidro");
            local.setMetraje(new BigDecimal("120.50"));
            local.setPrecioReferencial(new BigDecimal("3500.00"));
            local.setRubroPermitido("Restaurante");
            local.setDescripcion("Local creado desde prueba manual");
            local.setEstado(EstadoLocalComercial.DISPONIBLE);
            local.setIdPropietario(9001L); // Debe existir en la BD

            Long idGenerado = dao.crear(local);
            System.out.println("\nRegistro creado con ID: " + idGenerado);

            // 2. Buscar por ID
                Optional<LocalComercial> encontrado = dao.buscarPorId(idGenerado);
            if (encontrado.isPresent()) {
                System.out.println("\nRegistro encontrado:");
                System.out.println(encontrado.get());
            } else {
                System.out.println("\nNo se encontró el registro recién creado.");
                return;
            }

            // 3. Actualizar
            LocalComercial localActualizado = encontrado.get();
            localActualizado.setPrecioReferencial(new BigDecimal("4000.00"));
            localActualizado.setDescripcion("\nDescripción actualizada desde prueba manual");

            boolean actualizado = dao.actualizar(localActualizado);
            System.out.println("\n¿Registro actualizado?: " + actualizado);

            // 4. Volver a consultar
            Optional<LocalComercial> consultadoOtraVez = dao.buscarPorId(idGenerado);
            System.out.println("\nRegistro luego de actualizar:");
            consultadoOtraVez.ifPresent(System.out::println);

            // 5. Listar todos
            List<LocalComercial> locales = dao.listarTodos();
            System.out.println("\nTotal de locales encontrados: " + locales.size());
            for (LocalComercial item : locales) {
                System.out.println(item);
            }

            // 6. Eliminar
            boolean eliminado = dao.eliminar(idGenerado);
            System.out.println("\n¿Registro eliminado?: " + eliminado);

            // 7. Verificar eliminación
            Optional<LocalComercial> despuesDeEliminar = dao.buscarPorId(idGenerado);
            System.out.println("¿Existe después de eliminar?: " + despuesDeEliminar.isPresent());

            System.out.println("==== FIN PRUEBA MANUAL CRUD LOCAL COMERCIAL ====");

        } catch (Exception e) {
            System.err.println("Error durante la prueba manual del CRUD de LocalComercial:");
            e.printStackTrace();
        }
    }
}