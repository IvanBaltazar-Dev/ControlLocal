package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.DocumentoSolicitudDAO;
import com.controllocal.model.comercial.DocumentoSolicitud;
import com.controllocal.model.comercial.EstadoDocumentoSolicitud;
import com.controllocal.model.comercial.ResultadoRevisionDocumento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DocumentoSolicitudDAOImpl implements DocumentoSolicitudDAO {

    private static final String INSERT_SQL = """
            INSERT INTO documento_solicitud (
                tipo_documento, nombre_archivo, ruta_archivo, fecha_entrega,
                resultado_revision, observaciones, estado, id_solicitud
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_documento, tipo_documento, nombre_archivo, ruta_archivo,
                   fecha_entrega, resultado_revision, observaciones, estado, id_solicitud
            FROM documento_solicitud
            """;
    private static final String UPDATE_SQL = """
            UPDATE documento_solicitud
            SET tipo_documento = ?, nombre_archivo = ?, ruta_archivo = ?,
                fecha_entrega = ?, resultado_revision = ?, observaciones = ?,
                estado = ?, id_solicitud = ?
            WHERE id_documento = ?
            """;
    private static final String DELETE_SQL = "UPDATE documento_solicitud SET estado = 'OBSERVADO' WHERE id_documento = ?";

    @Override
    public Long crear(DocumentoSolicitud documento) {
        validar(documento, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(documento, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    documento.setIdDocumento(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de documento de solicitud.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear documento de solicitud.", e);
        }
    }

    @Override
    public Optional<DocumentoSolicitud> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_documento = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar documento de solicitud con id " + id + ".", e);
        }
    }

    @Override
    public List<DocumentoSolicitud> listarTodos() {
        List<DocumentoSolicitud> documentos = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_documento");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                documentos.add(mapRow(rs));
            }
            return documentos;
        } catch (SQLException e) {
            throw new DAOException("Error al listar documentos de solicitud.", e);
        }
    }

    @Override
    public boolean actualizar(DocumentoSolicitud documento) {
        validar(documento, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(documento, ps);
            ps.setLong(9, documento.getIdDocumento());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar documento de solicitud con id " + documento.getIdDocumento() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar documento de solicitud con id " + id + ".", e);
        }
    }

    private void bind(DocumentoSolicitud documento, PreparedStatement ps) throws SQLException {
        ps.setString(1, documento.getTipoDocumento());
        ps.setString(2, documento.getNombreArchivo());
        ps.setString(3, documento.getRutaArchivo());
        JdbcSupport.setTimestamp(ps, 4, documento.getFechaEntrega());
        ps.setString(5, documento.getResultadoRevision() != null ? documento.getResultadoRevision().name() : null);
        ps.setString(6, documento.getObservaciones());
        ps.setString(7, documento.getEstado().name());
        ps.setLong(8, documento.getSolicitudAlquiler().getIdSolicitud());
    }

    private DocumentoSolicitud mapRow(ResultSet rs) throws SQLException {
        DocumentoSolicitud documento = new DocumentoSolicitud();
        documento.setIdDocumento(rs.getLong("id_documento"));
        documento.setTipoDocumento(rs.getString("tipo_documento"));
        documento.setNombreArchivo(rs.getString("nombre_archivo"));
        documento.setRutaArchivo(rs.getString("ruta_archivo"));
        documento.setFechaEntrega(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_entrega")));
        String resultado = rs.getString("resultado_revision");
        documento.setResultadoRevision(resultado != null ? ResultadoRevisionDocumento.valueOf(resultado) : null);
        documento.setObservaciones(rs.getString("observaciones"));
        documento.setEstado(EstadoDocumentoSolicitud.valueOf(rs.getString("estado")));
        documento.setSolicitudAlquiler(JdbcSupport.solicitud(rs.getLong("id_solicitud")));
        return documento;
    }

    private void validar(DocumentoSolicitud documento, boolean requiereId) {
        if (documento == null) {
            throw new IllegalArgumentException("El documento de solicitud no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(documento.getIdDocumento());
        }
        if (documento.getTipoDocumento() == null || documento.getTipoDocumento().isBlank()
                || documento.getNombreArchivo() == null || documento.getNombreArchivo().isBlank()
                || documento.getFechaEntrega() == null || documento.getEstado() == null) {
            throw new IllegalArgumentException("El documento de solicitud tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdSolicitud(documento.getSolicitudAlquiler()));
    }
}
