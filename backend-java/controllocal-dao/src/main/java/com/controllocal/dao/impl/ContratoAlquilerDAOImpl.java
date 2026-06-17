package com.controllocal.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.controllocal.dao.ContratoAlquilerDAO;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.enums.EstadoContrato;
import com.controllocal.model.comercial.enums.FormaPago;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.comercial.enums.TipoReajuste;

public class ContratoAlquilerDAOImpl extends AbstractJdbcCrudDAO<ContratoAlquiler>
        implements ContratoAlquilerDAO {
    private static final String INSERT = """
            INSERT INTO contrato_alquiler (
                id_oportunidad, id_solicitud, renta_mensual, moneda,
                plazo_contrato_meses, fecha_inicio_contrato, fecha_fin_contrato,
                meses_garantia, monto_garantia, meses_adelanto, cuota_mantenimiento,
                tipo_reajuste, porcentaje_reajuste, forma_pago, fecha_cierre,
                comision_generada, estado_contrato, incidencias
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_contrato_alquiler, id_oportunidad, id_solicitud, renta_mensual,
                   moneda, plazo_contrato_meses, fecha_inicio_contrato, fecha_fin_contrato,
                   meses_garantia, monto_garantia, meses_adelanto, cuota_mantenimiento,
                   tipo_reajuste, porcentaje_reajuste, forma_pago, fecha_cierre,
                   comision_generada, estado_contrato, incidencias,
                   fecha_creacion, fecha_actualizacion
            FROM contrato_alquiler
            """;
    private static final String UPDATE = """
            UPDATE contrato_alquiler SET id_oportunidad = ?, id_solicitud = ?,
                renta_mensual = ?, moneda = ?, plazo_contrato_meses = ?,
                fecha_inicio_contrato = ?, fecha_fin_contrato = ?, meses_garantia = ?,
                monto_garantia = ?, meses_adelanto = ?, cuota_mantenimiento = ?,
                tipo_reajuste = ?, porcentaje_reajuste = ?, forma_pago = ?,
                fecha_cierre = ?, comision_generada = ?, estado_contrato = ?, incidencias = ?
            WHERE id_contrato_alquiler = ?
            """;
    private static final String DELETE =
            "UPDATE contrato_alquiler SET estado_contrato = 'ANULADO' WHERE id_contrato_alquiler = ?";

    @Override public Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad) {
        JdbcSupport.validarId(idOportunidad);
        return query(SELECT + " WHERE id_oportunidad = ?", ps -> ps.setLong(1, idOportunidad))
                .stream().findFirst();
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_contrato_alquiler"; }
    @Override protected String entityName() { return "el contrato de alquiler"; }
    @Override protected void bindInsert(PreparedStatement ps, ContratoAlquiler c) throws SQLException { bind(ps, c); }
    @Override protected void bindUpdate(PreparedStatement ps, ContratoAlquiler c) throws SQLException {
        bind(ps, c);
        ps.setLong(19, c.getIdContratoAlquiler());
    }

    private void bind(PreparedStatement ps, ContratoAlquiler c) throws SQLException {
        ps.setLong(1, c.getOportunidad().getIdOportunidad());
        JdbcSupport.setLong(ps, 2, c.getSolicitudAlquiler() != null ? c.getSolicitudAlquiler().getIdSolicitud() : null);
        ps.setBigDecimal(3, c.getRentaMensual());
        JdbcSupport.setEnum(ps, 4, c.getMoneda());
        ps.setInt(5, c.getPlazoContratoMeses());
        JdbcSupport.setDate(ps, 6, c.getFechaInicioContrato());
        JdbcSupport.setDate(ps, 7, c.getFechaFinContrato());
        JdbcSupport.setInteger(ps, 8, c.getMesesGarantia());
        ps.setBigDecimal(9, c.getMontoGarantia());
        JdbcSupport.setInteger(ps, 10, c.getMesesAdelanto());
        ps.setBigDecimal(11, c.getCuotaMantenimiento());
        JdbcSupport.setEnum(ps, 12, c.getTipoReajuste());
        ps.setBigDecimal(13, c.getPorcentajeReajuste());
        JdbcSupport.setEnum(ps, 14, c.getFormaPago());
        JdbcSupport.setDate(ps, 15, c.getFechaCierre());
        ps.setBigDecimal(16, c.getComisionGenerada());
        JdbcSupport.setEnum(ps, 17, c.getEstadoContrato());
        ps.setString(18, c.getIncidencias());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected ContratoAlquiler mapRow(ResultSet rs) throws SQLException {
        ContratoAlquiler c = new ContratoAlquiler();
        c.setIdContratoAlquiler(rs.getLong("id_contrato_alquiler"));
        c.setOportunidad(JdbcSupport.oportunidad(rs.getLong("id_oportunidad")));
        Long idSolicitud = rs.getObject("id_solicitud", Long.class);
        if (idSolicitud != null) c.setSolicitudAlquiler(JdbcSupport.solicitud(idSolicitud));
        c.setRentaMensual(rs.getBigDecimal("renta_mensual"));
        c.setMoneda(JdbcSupport.getEnum(rs, "moneda", Moneda.class));
        c.setPlazoContratoMeses(rs.getInt("plazo_contrato_meses"));
        c.setFechaInicioContrato(JdbcSupport.toLocalDate(rs.getDate("fecha_inicio_contrato")));
        c.setFechaFinContrato(JdbcSupport.toLocalDate(rs.getDate("fecha_fin_contrato")));
        c.setMesesGarantia(JdbcSupport.getNullableInt(rs, "meses_garantia"));
        c.setMontoGarantia(rs.getBigDecimal("monto_garantia"));
        c.setMesesAdelanto(JdbcSupport.getNullableInt(rs, "meses_adelanto"));
        c.setCuotaMantenimiento(rs.getBigDecimal("cuota_mantenimiento"));
        c.setTipoReajuste(JdbcSupport.getEnum(rs, "tipo_reajuste", TipoReajuste.class));
        c.setPorcentajeReajuste(rs.getBigDecimal("porcentaje_reajuste"));
        c.setFormaPago(JdbcSupport.getEnum(rs, "forma_pago", FormaPago.class));
        c.setFechaCierre(JdbcSupport.toLocalDate(rs.getDate("fecha_cierre")));
        c.setComisionGenerada(rs.getBigDecimal("comision_generada"));
        c.setEstadoContrato(JdbcSupport.getEnum(rs, "estado_contrato", EstadoContrato.class));
        c.setIncidencias(rs.getString("incidencias"));
        c.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        c.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return c;
    }

    @Override protected void assignId(ContratoAlquiler c, Long id) { c.setIdContratoAlquiler(id); }

    @Override protected void validate(ContratoAlquiler c, boolean requireId) {
        if (c == null) throw new IllegalArgumentException("El contrato de alquiler es obligatorio.");
        if (requireId) JdbcSupport.validarId(c.getIdContratoAlquiler());
        JdbcSupport.validarId(c.getOportunidad() != null ? c.getOportunidad().getIdOportunidad() : null);
        if (c.getSolicitudAlquiler() != null) JdbcSupport.validarId(c.getSolicitudAlquiler().getIdSolicitud());
        if (c.getRentaMensual() == null || c.getMoneda() == null || c.getPlazoContratoMeses() == null
                || c.getFechaInicioContrato() == null || c.getFechaFinContrato() == null
                || c.getTipoReajuste() == null || c.getFormaPago() == null || c.getFechaCierre() == null
                || c.getComisionGenerada() == null || c.getEstadoContrato() == null) {
            throw new IllegalArgumentException("El contrato de alquiler tiene campos obligatorios incompletos.");
        }
        if (c.getFechaFinContrato().isBefore(c.getFechaInicioContrato())) {
            throw new IllegalArgumentException("La fecha fin del contrato no puede ser anterior al inicio.");
        }
    }
}
