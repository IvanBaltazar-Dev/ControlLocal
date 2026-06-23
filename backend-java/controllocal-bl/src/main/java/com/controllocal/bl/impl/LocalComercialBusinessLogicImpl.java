package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.LocalComercialBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.*;
import com.controllocal.dao.impl.*;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.inmueble.FotoLocal;
import com.controllocal.model.inmueble.LocalComercial;

public class LocalComercialBusinessLogicImpl implements LocalComercialBusinessLogic {

    private final LocalComercialDAO localDAO;
    private final CaptacionDAO captacionDAO;
    private final PrecioLocalDAO precioLocalDAO;
    private final HistorialEstadoDAO historialEstadoDAO;
    private final AlertaDAO alertaDAO;
    private final DistritoDAO distritoDAO;
    // Galeria de fotos: DAO propio (no participa en los constructores inyectables existentes).
    private final FotoLocalDAO fotoLocalDAO = new FotoLocalDAOImpl();

    public LocalComercialBusinessLogicImpl() {
        this(new LocalComercialDAOImpl());
    }

    public LocalComercialBusinessLogicImpl(LocalComercialDAO localDAO) {
        this(localDAO, new CaptacionDAOImpl(), new PrecioLocalDAOImpl(),
                new HistorialEstadoDAOImpl(), new AlertaDAOImpl(), new DistritoDAOImpl());
    }

    public LocalComercialBusinessLogicImpl(LocalComercialDAO localDAO, CaptacionDAO captacionDAO,
            PrecioLocalDAO precioLocalDAO, HistorialEstadoDAO historialEstadoDAO, AlertaDAO alertaDAO,
            DistritoDAO distritoDAO) {
        this.localDAO = localDAO;
        this.captacionDAO = captacionDAO;
        this.precioLocalDAO = precioLocalDAO;
        this.historialEstadoDAO = historialEstadoDAO;
        this.alertaDAO = alertaDAO;
        this.distritoDAO = distritoDAO;
    }

    // Resuelve la FK al catalogo distrito (solo Lima por ahora) desde el nombre escrito.
    // Compara sin acentos ni mayusculas para tolerar variantes como "Brena"/"Breña".
    // Si el distrito no esta catalogado, deja id_distrito en NULL (no rompe el alta).
    private void resolverDistrito(LocalComercial local) {
        String nombre = local.getDistrito();
        if (nombre == null || nombre.isBlank()) {
            local.setIdDistrito(null);
            return;
        }
        String objetivo = normalizar(nombre);
        local.setIdDistrito(distritoDAO.listarActivos().stream()
                .filter(distrito -> normalizar(distrito.getNombre()).equals(objetivo))
                .map(Distrito::getIdDistrito)
                .findFirst()
                .orElse(null));
    }

    // Minuscula + sin tildes/diacriticos para una comparacion laxa de nombres de distrito.
    private static String normalizar(String valor) {
        return java.text.Normalizer.normalize(valor.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase();
    }

    public Long registrar(LocalComercial local) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.local(local);
            resolverDistrito(local);
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

        // Resolver la FK al catalogo distrito antes de persistir.
        resolverDistrito(localModificado);

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

    @Override
    public Long agregarFoto(Long idLocal, String clave, String nombreArchivo) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idLocal, "El id del local");
            if (localDAO.buscarPorId(idLocal).isEmpty()) {
                throw new BusinessException("El local no existe.");
            }
            int actuales = fotoLocalDAO.listarPorLocal(idLocal).size();
            if (actuales >= 6) {
                throw new BusinessException("Maximo 6 fotos por local. Elimina alguna para subir otra.");
            }
            FotoLocal foto = new FotoLocal();
            foto.setIdLocal(idLocal);
            foto.setClave(clave);
            foto.setNombreArchivo(nombreArchivo);
            foto.setOrden(actuales);
            return fotoLocalDAO.crear(foto);
        });
    }

    @Override
    public List<FotoLocal> listarFotos(Long idLocal) {
        BusinessValidations.id(idLocal, "El id del local");
        return fotoLocalDAO.listarPorLocal(idLocal);
    }

    @Override
    public boolean eliminarFoto(Long idFoto) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idFoto, "El id de la foto");
            return fotoLocalDAO.eliminar(idFoto);
        });
    }
}

