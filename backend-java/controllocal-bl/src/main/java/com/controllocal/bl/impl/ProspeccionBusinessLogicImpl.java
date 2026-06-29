package com.controllocal.bl.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.InteraccionComercialDAO;
import com.controllocal.dao.ProspeccionDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.InteraccionComercialDAOImpl;
import com.controllocal.dao.impl.ProspeccionDAOImpl;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.enums.CanalContacto;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class ProspeccionBusinessLogicImpl implements ProspeccionBusinessLogic {

    private final ProspeccionDAO prospeccionDAO;
    private final CaptacionDAO captacionDAO;
    private final AgenteInmobiliarioDAO agenteDAO;
    private final AlertaDAO alertaDAO;
    private final InteraccionComercialDAO interaccionDAO;

    public ProspeccionBusinessLogicImpl() {
        this(new ProspeccionDAOImpl(), new CaptacionDAOImpl(), new AgenteInmobiliarioDAOImpl(),
                new AlertaDAOImpl(), new InteraccionComercialDAOImpl());
    }

    public ProspeccionBusinessLogicImpl(
            ProspeccionDAO prospeccionDAO,
            CaptacionDAO captacionDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this(prospeccionDAO, captacionDAO, agenteDAO, new AlertaDAOImpl(), new InteraccionComercialDAOImpl());
    }

    public ProspeccionBusinessLogicImpl(
            ProspeccionDAO prospeccionDAO,
            CaptacionDAO captacionDAO,
            AgenteInmobiliarioDAO agenteDAO,
            AlertaDAO alertaDAO
    ) {
        this(prospeccionDAO, captacionDAO, agenteDAO, alertaDAO, new InteraccionComercialDAOImpl());
    }

    public ProspeccionBusinessLogicImpl(
            ProspeccionDAO prospeccionDAO,
            CaptacionDAO captacionDAO,
            AgenteInmobiliarioDAO agenteDAO,
            AlertaDAO alertaDAO,
            InteraccionComercialDAO interaccionDAO
    ) {
        this.prospeccionDAO = prospeccionDAO;
        this.captacionDAO = captacionDAO;
        this.agenteDAO = agenteDAO;
        this.alertaDAO = alertaDAO;
        this.interaccionDAO = interaccionDAO;
    }

    public Long registrar(Prospeccion prospeccion) {
        return TransactionRunner.write(conn -> {
            prospeccion.registrar();
            BusinessValidations.prospeccion(prospeccion);
            validarAgenteDisponible(BusinessValidations.idAgente(prospeccion.getAgenteResponsable()));
            if (prospeccion.getCodigoProspeccion() == null || prospeccion.getCodigoProspeccion().isBlank()) {
                prospeccion.setCodigoProspeccion(generarCodigoProspeccion());
            }
            return prospeccionDAO.crear(prospeccion);
        });
    }

    public Optional<Prospeccion> buscarPorId(Long idProspeccion) {
        BusinessValidations.id(idProspeccion, "El id de prospeccion");
        return prospeccionDAO.buscarPorId(idProspeccion);
    }

    public List<Prospeccion> listarTodos() {
        return prospeccionDAO.listarTodos();
    }

    public List<Prospeccion> listarPorRecontactar(int diasAviso) {
        // Vencido = la ultima accion de seguimiento (fecha_recontacto) tiene ya diasAviso dias o mas.
        return prospeccionDAO.listarPorRecontactar(LocalDate.now().minusDays(Math.max(0, diasAviso)));
    }

    @Override
    public List<Prospeccion> listarPorAgentes(java.util.Collection<Long> idsAgente) {
        return prospeccionDAO.listarPorAgentes(idsAgente);
    }

    @Override
    public List<Prospeccion> listarPorPropietario(Long idPropietario) {
        return prospeccionDAO.listarPorPropietario(idPropietario);
    }

    public int sincronizarRecontacto() {
        return TransactionRunner.write(conn -> {
            LocalDate limite = LocalDate.now().minusDays(Prospeccion.DIAS_RECONTACTO);
            int creadas = 0;
            for (Prospeccion p : prospeccionDAO.listarPorRecontactar(limite)) {
                if (p.getAgenteResponsable() == null || p.getAgenteResponsable().getIdAgente() == null) {
                    continue;
                }
                boolean yaActiva = alertaDAO
                        .listarActivasPorEntidad(TipoEntidad.PROSPECCION, p.getIdProspeccion())
                        .stream().anyMatch(a -> a.getTipo() == TipoAlerta.SIN_RESPUESTA);
                if (!yaActiva) {
                    String codigo = p.getCodigoProspeccion() != null
                            ? p.getCodigoProspeccion() : ("#" + p.getIdProspeccion());
                    alertaDAO.crear(AlertaBusinessLogicImpl.construir(
                            TipoAlerta.SIN_RESPUESTA, Severidad.MEDIA, TipoEntidad.PROSPECCION,
                            p.getIdProspeccion(), p.getAgenteResponsable(),
                            "Recontacta o evalua descartar la prospeccion " + codigo + "."));
                    creadas++;
                }
            }
            return creadas;
        });
    }

    public boolean actualizar(Prospeccion prospeccion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(prospeccion != null ? prospeccion.getIdProspeccion() : null, "El id de prospeccion");
            BusinessValidations.prospeccion(prospeccion);
            return prospeccionDAO.actualizar(prospeccion);
        });
    }

    public boolean eliminar(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idProspeccion, "El id de prospeccion");
            return prospeccionDAO.eliminar(idProspeccion);
        });
    }

    public boolean contactar(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "contactar");
            p.contactar();
            registrarInteraccionHito(p, CanalContacto.LLAMADA, ResultadoInteraccion.CONTACTADO,
                    "Contacto inicial registrado desde la prospeccion.");
            boolean ok = prospeccionDAO.actualizar(p);
            atenderRecontacto(idProspeccion);
            return ok;
        });
    }

    public boolean registrarReunion(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "registrar la reunion de");
            p.registrarReunion();
            registrarInteraccionHito(p, CanalContacto.REUNION, ResultadoInteraccion.REUNION_AGENDADA,
                    "Reunion registrada desde la prospeccion.");
            boolean ok = prospeccionDAO.actualizar(p);
            atenderRecontacto(idProspeccion);
            return ok;
        });
    }

    public boolean entregarPropuesta(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "entregar la propuesta de");
            p.entregarPropuesta();
            registrarInteraccionHito(p, CanalContacto.EMAIL, ResultadoInteraccion.PROPUESTA_ENVIADA,
                    "Propuesta entregada al propietario desde la prospeccion.");
            boolean ok = prospeccionDAO.actualizar(p);
            atenderRecontacto(idProspeccion);
            return ok;
        });
    }

    public boolean registrarSeguimiento(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "registrar el seguimiento de");
            p.registrarSeguimiento();
            boolean ok = prospeccionDAO.actualizar(p);
            atenderRecontacto(idProspeccion);
            return ok;
        });
    }

    // Al registrar una nueva accion, atiende la alerta SIN_RESPUESTA activa (reinicia el conteo).
    private void atenderRecontacto(Long idProspeccion) {
        for (Alerta a : alertaDAO.listarActivasPorEntidad(TipoEntidad.PROSPECCION, idProspeccion)) {
            if (a.getTipo() == TipoAlerta.SIN_RESPUESTA && a.getIdAlerta() != null) {
                alertaDAO.marcarAtendida(a.getIdAlerta());
            }
        }
    }

    private void registrarInteraccionHito(
            Prospeccion prospeccion,
            CanalContacto canal,
            ResultadoInteraccion resultado,
            String observaciones) {
        InteraccionComercial interaccion = new InteraccionComercial();
        interaccion.setContexto("PROSPECCION");
        interaccion.setProspeccion(prospeccion);
        interaccion.setAgenteResponsable(prospeccion.getAgenteResponsable());
        interaccion.setCanalContacto(canal);
        interaccion.setResultado(resultado);
        interaccion.setObservaciones(observaciones);
        interaccion.registrar();
        interaccionDAO.crear(interaccion);
    }

    public boolean rechazar(Long idProspeccion, String motivo) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "rechazar");
            p.rechazarPropuesta(motivo);
            return prospeccionDAO.actualizar(p);
        });
    }

    public boolean descartar(Long idProspeccion, String motivo) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "descartar");
            p.descartar(motivo);
            return prospeccionDAO.actualizar(p);
        });
    }

    public Long captar(Long idProspeccion, BigDecimal comisionPactada) {
        return TransactionRunner.write(conn -> {
            if (comisionPactada == null || comisionPactada.signum() < 0) {
                throw new BusinessException("La comision pactada es obligatoria y no puede ser negativa.");
            }
            Prospeccion p = prospeccionEnProceso(idProspeccion, "captar");

            Captacion captacion = new Captacion();
            captacion.setCodigoCaptacion(generarCodigoCaptacion());
            captacion.setFechaCaptacion(LocalDate.now());
            captacion.setComisionPactada(comisionPactada);
            captacion.setLocalComercial(p.getLocalComercial());
            captacion.setAgenteResponsable(p.getAgenteResponsable());
            captacion.registrar(); // PENDIENTE_REVISION
            Long idCaptacion = captacionDAO.crear(captacion);

            p.aceptarPropuesta();
            p.setCaptacion(captacion);
            prospeccionDAO.actualizar(p);
            return idCaptacion;
        });
    }

    // Carga la prospeccion y valida que siga viva (ni CAPTADA ni DESCARTADA).
    private Prospeccion prospeccionEnProceso(Long idProspeccion, String accion) {
        BusinessValidations.id(idProspeccion, "El id de prospeccion");
        Prospeccion p = prospeccionDAO.buscarPorId(idProspeccion)
                .orElseThrow(() -> new BusinessException("Prospeccion no encontrada."));
        if (!p.getEstado().enProceso()) {
            throw new BusinessException("No se puede " + accion + " una prospeccion "
                    + p.getEstado().getDescripcion().toLowerCase() + ".");
        }
        return p;
    }

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para prospeccion."));
        BusinessValidations.agenteDisponible(agente);
    }

    private String generarCodigoProspeccion() {
        return String.format("PRO-%04d", prospeccionDAO.listarTodos().size() + 1);
    }

    private String generarCodigoCaptacion() {
        return String.format("CAP-%04d", captacionDAO.listarTodos().size() + 1);
    }
}
