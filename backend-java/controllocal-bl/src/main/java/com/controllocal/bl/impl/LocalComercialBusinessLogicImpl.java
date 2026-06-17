package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.LocalComercialBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.*;
import com.controllocal.dao.impl.*;
import com.controllocal.model.inmueble.LocalComercial;

public class LocalComercialBusinessLogicImpl implements LocalComercialBusinessLogic {

    private final LocalComercialDAO localDAO;

    public LocalComercialBusinessLogicImpl() {
        this(new LocalComercialDAOImpl());
    }

    public LocalComercialBusinessLogicImpl(LocalComercialDAO localDAO) {
        this.localDAO = localDAO;
    }

    public Long registrar(LocalComercial local) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.local(local);
            return localDAO.crear(local);
        });
    }

    public Optional<LocalComercial> buscarPorId(Long idLocal) {
        BusinessValidations.id(idLocal, "El id de local comercial");
        return localDAO.buscarPorId(idLocal);
    }

    public List<LocalComercial> listarTodos() {
        return localDAO.listarTodos();
    }

    public List<LocalComercial> listarPagina(int limite, int desplazamiento) {
        BusinessValidations.pagina(limite, desplazamiento);
        return localDAO.listarPagina(limite, desplazamiento);
    }

    public long contar() {
        return localDAO.contar();
    }

    private final CaptacionDAO captacionDAO = new CaptacionDAOImpl();
    private final PrecioLocalDAO precioLocalDAO = new PrecioLocalDAOImpl();
    private final HistorialEstadoDAO historialEstadoDAO = new HistorialEstadoDAOImpl();
    private final AlertaDAO alertaDAO = new AlertaDAOImpl();
    public boolean actualizar(LocalComercial localModificado, long idAgente) {
        // 1. Obtenemos el local original para comparar
        LocalComercial localOriginal = localDAO.buscarPorId(localModificado.getIdLocal())
                .orElseThrow(() -> new BusinessException("Local no encontrado"));

        // 2. REGLA: "Gestión de locales comerciales de captaciones PROPIAS"
        // Validar que el agente sea el dueño de la captación de este local
        if (!captacionDAO.perteneceAlAgente(localModificado.getIdLocal(), idAgente)) {
            throw new BusinessException("Operación denegada. Este local no pertenece a tus captaciones.");
        }

        // 3. REGLA: "Trazabilidad de cambios sensibles"
        // Historial de Precio (si cambió)
        if (localOriginal.getPrecioReferencial().compareTo(localModificado.getPrecioReferencial()) != 0) {
            precioLocalDAO.registrar(
                    localModificado.getIdLocal(),
                    "U", // Hito: Update
                    "PEN", // Moneda
                    localModificado.getPrecioReferencial(),
                    java.time.LocalDate.now()
            );
        }

        // Historial de Estado (si cambió a través del form de edición)
        if (localOriginal.getEstado() != localModificado.getEstado()) {
            historialEstadoDAO.registrar(
                    "INMUEBLE", localModificado.getIdLocal(),
                    localOriginal.getEstado().name(), localModificado.getEstado().name(),
                    idAgente, java.time.LocalDateTime.now(), "Cambio en edición comercial"
            );
        }

        // 4. REGLA: "Sujetos a validación"
        // Si cambia el metraje o rubro, alertamos
        if (localOriginal.getMetraje().compareTo(localModificado.getMetraje()) != 0 ||
                !localOriginal.getRubroPermitido().equals(localModificado.getRubroPermitido())) {

            alertaDAO.crearAlertaSensible(localModificado.getIdLocal(), idAgente, "Modificación comercial sensible, revisar");
        }

        // Finalmente, guardar
        return localDAO.actualizar(localModificado);
    }



    public boolean desactivar(Long idLocal, long idAgente) {
        return TransactionRunner.write(conn -> {
            // 1. Validaciones base
            BusinessValidations.id(idLocal, "El id de local comercial");

            // 2. Obtener el local original para saber su estado anterior
            LocalComercial localOriginal = localDAO.buscarPorId(idLocal)
                    .orElseThrow(() -> new BusinessException("Local no encontrado"));

            // 3. Validar que la captación le pertenece al agente
            if (!captacionDAO.perteneceAlAgente(idLocal, idAgente)) {
                throw new BusinessException("Operación denegada. Este local no pertenece a tus captaciones.");
            }

            // 4. Ejecutar la eliminación lógica (Update a estado inactivo en el DAO)
            boolean exito = localDAO.eliminar(idLocal);

            // 5. Trazabilidad: Registrar el cambio de estado si tuvo éxito
            if (exito) {
                historialEstadoDAO.registrar(
                        "INMUEBLE",
                        idLocal,
                        localOriginal.getEstado().name(),
                        "I", // O "INACTIVO" (dependiendo de cómo lo guardes en el historial)
                        idAgente,
                        java.time.LocalDateTime.now(),
                        "Desactivación de local por el agente"
                );
            }

            return exito;
        });
    }

    @Override
    public List<LocalComercial> listarPaginaPorAgente(long idAgente, int limite, int desplazamiento) {
        BusinessValidations.pagina(limite, desplazamiento);

        // Llamamos al DAO que tiene la consulta con el INNER JOIN que definimos antes
        return localDAO.listarPorAgente(idAgente, limite, desplazamiento);
    }
}

