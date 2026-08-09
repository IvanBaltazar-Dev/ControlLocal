package com.controllocal.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso del broker contra el contrato congelado de E1.
 */
public interface BrokerService {

    record DatosBroker(String nombre, String tipoPersona, String tipoDocumento,
                       String numeroDocumento, String telefono, String correo,
                       String usuario, String contrasena, String zona,
                       String codigoBroker, String estado, Boolean esAdministrador) {
    }

    record FichaBroker(Long id, String codigoBroker, String nombre,
                       String tipoPersona, String tipoDocumento,
                       String numeroDocumento, String telefono, String correo,
                       String usuario, String zona, LocalDate fechaDesignacion,
                       String estadoAdministrativo, boolean esAdministrador,
                       int agentesACargo) {
    }

    Pagina<FichaBroker> listar(int pagina, int tamano, Actor actor);

    FichaBroker obtener(long id, Actor actor);

    List<AgenteService.FichaAgente> agentes(long id, Actor actor);

    FichaBroker registrar(DatosBroker datos, Actor actor);

    FichaBroker actualizar(long id, DatosBroker datos, Actor actor);
}
