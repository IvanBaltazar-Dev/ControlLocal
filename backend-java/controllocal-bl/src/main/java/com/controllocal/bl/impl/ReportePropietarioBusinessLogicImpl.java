package com.controllocal.bl.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.ReportePropietarioBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.dao.ReportePropietarioDAO;
import com.controllocal.dao.impl.ReportePropietarioDAOImpl;
import com.controllocal.model.comercial.ReportePropietario;
import com.controllocal.model.comercial.enums.CanalContacto;

public class ReportePropietarioBusinessLogicImpl implements ReportePropietarioBusinessLogic {

    private final ReportePropietarioDAO reportes;

    public ReportePropietarioBusinessLogicImpl() {
        this(new ReportePropietarioDAOImpl());
    }

    public ReportePropietarioBusinessLogicImpl(ReportePropietarioDAO reportes) {
        this.reportes = reportes;
    }

    @Override
    public List<ReportePropietario> listarPorCaptacion(Long idCaptacion) {
        BusinessValidations.id(idCaptacion, "El id de captacion");
        return reportes.listarPorCaptacion(idCaptacion);
    }

    @Override
    public Long registrar(ReportePropietario reporte) {
        validar(reporte);
        aplicarDefaults(reporte);
        return reportes.crear(reporte);
    }

    private void validar(ReportePropietario r) {
        if (r == null) {
            throw new BusinessException("Los datos del reporte son obligatorios.");
        }
        if (r.getCaptacion() == null || r.getCaptacion().getIdCaptacion() == null
                || r.getCaptacion().getIdCaptacion() <= 0) {
            throw new BusinessException("La captacion del reporte es obligatoria.");
        }
        if (r.getAgente() == null || r.getAgente().getIdAgente() == null
                || r.getAgente().getIdAgente() <= 0) {
            throw new BusinessException("El agente del reporte es obligatorio.");
        }
        if (r.getPeriodoInicio() != null && r.getPeriodoFin() != null
                && r.getPeriodoFin().isBefore(r.getPeriodoInicio())) {
            throw new BusinessException("El fin del periodo no puede ser anterior al inicio.");
        }
        if (r.getConsultasReportadas() != null && r.getConsultasReportadas() < 0) {
            throw new BusinessException("Las consultas reportadas no pueden ser negativas.");
        }
        if (r.getVisitasReportadas() != null && r.getVisitasReportadas() < 0) {
            throw new BusinessException("Las visitas reportadas no pueden ser negativas.");
        }
    }

    // El DAO exige fecha, conteos y canal no nulos (NPE en setInt); cubrimos con defaults seguros.
    private static void aplicarDefaults(ReportePropietario r) {
        if (r.getFechaReporte() == null) {
            r.setFechaReporte(LocalDate.now());
        }
        if (r.getConsultasReportadas() == null) {
            r.setConsultasReportadas(0);
        }
        if (r.getVisitasReportadas() == null) {
            r.setVisitasReportadas(0);
        }
        if (r.getCanalEnvio() == null) {
            r.setCanalEnvio(CanalContacto.EMAIL);
        }
        if (r.getFechaCreacion() == null) {
            r.setFechaCreacion(LocalDateTime.now());
        }
    }
}
