package com.controllocal.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.controllocal.dao.ComisionLiquidacionDAO;
import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.FormaPago;
import com.controllocal.model.comercial.enums.Moneda;

public class ComisionLiquidacionDAOImpl extends AbstractJdbcCrudDAO<ComisionLiquidacion>
        implements ComisionLiquidacionDAO {
    private static final String INSERT = """
            INSERT INTO comision_liquidacion (
                id_contrato_alquiler, monto, moneda, monto_agente,
                monto_empresa, fecha_cobro, forma_pago, estado
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_comision_liquidacion, id_contrato_alquiler, monto, moneda,
                   monto_agente, monto_empresa, fecha_cobro, forma_pago, estado
            FROM comision_liquidacion
            """;
    private static final String UPDATE = """
            UPDATE comision_liquidacion SET id_contrato_alquiler = ?, monto = ?,
                moneda = ?, monto_agente = ?, monto_empresa = ?, fecha_cobro = ?,
                forma_pago = ?, estado = ?
            WHERE id_comision_liquidacion = ?
            """;
    private static final String DELETE =
            "UPDATE comision_liquidacion SET estado = 'ANULADA' WHERE id_comision_liquidacion = ?";

    @Override public List<ComisionLiquidacion> listarPorContrato(Long idContrato) {
        JdbcSupport.validarId(idContrato);
        return query(SELECT + " WHERE id_contrato_alquiler = ? ORDER BY id_comision_liquidacion",
                ps -> ps.setLong(1, idContrato));
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_comision_liquidacion"; }
    @Override protected String entityName() { return "la liquidacion de comision"; }
    @Override protected void bindInsert(PreparedStatement ps, ComisionLiquidacion c) throws SQLException { bind(ps, c); }
    @Override protected void bindUpdate(PreparedStatement ps, ComisionLiquidacion c) throws SQLException {
        bind(ps, c);
        ps.setLong(9, c.getIdComisionLiquidacion());
    }

    private void bind(PreparedStatement ps, ComisionLiquidacion c) throws SQLException {
        ps.setLong(1, c.getContratoAlquiler().getIdContratoAlquiler());
        ps.setBigDecimal(2, c.getMonto());
        JdbcSupport.setEnum(ps, 3, c.getMoneda());
        ps.setBigDecimal(4, c.getMontoAgente());
        ps.setBigDecimal(5, c.getMontoEmpresa());
        JdbcSupport.setDate(ps, 6, c.getFechaCobro());
        JdbcSupport.setEnum(ps, 7, c.getFormaPago());
        JdbcSupport.setEnum(ps, 8, c.getEstado());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected ComisionLiquidacion mapRow(ResultSet rs) throws SQLException {
        ComisionLiquidacion c = new ComisionLiquidacion();
        c.setIdComisionLiquidacion(rs.getLong("id_comision_liquidacion"));
        c.setContratoAlquiler(JdbcSupport.contrato(rs.getLong("id_contrato_alquiler")));
        c.setMonto(rs.getBigDecimal("monto"));
        c.setMoneda(JdbcSupport.getEnum(rs, "moneda", Moneda.class));
        c.setMontoAgente(rs.getBigDecimal("monto_agente"));
        c.setMontoEmpresa(rs.getBigDecimal("monto_empresa"));
        c.setFechaCobro(JdbcSupport.toLocalDate(rs.getDate("fecha_cobro")));
        c.setFormaPago(JdbcSupport.getNullableEnum(rs, "forma_pago", FormaPago.class));
        c.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoComision.class));
        return c;
    }

    @Override protected void assignId(ComisionLiquidacion c, Long id) { c.setIdComisionLiquidacion(id); }

    @Override protected void validate(ComisionLiquidacion c, boolean requireId) {
        if (c == null) throw new IllegalArgumentException("La liquidacion de comision es obligatoria.");
        if (requireId) JdbcSupport.validarId(c.getIdComisionLiquidacion());
        JdbcSupport.validarId(c.getContratoAlquiler() != null
                ? c.getContratoAlquiler().getIdContratoAlquiler() : null);
        if (c.getMonto() == null || c.getMoneda() == null || c.getEstado() == null) {
            throw new IllegalArgumentException("La liquidacion de comision tiene campos obligatorios incompletos.");
        }
    }
}
