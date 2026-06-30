package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.controllocal.config.DBManager;
import com.controllocal.dao.PublicacionDAO;
import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.comercial.enums.CanalPublicacion;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;

public class PublicacionDAOImpl extends AbstractJdbcCrudDAO<Publicacion> implements PublicacionDAO {
    private static final String INSERT = """
            INSERT INTO publicacion (
                id_local, canal, url_publicacion, version_anuncio, titulo_anuncio,
                renta_publicada, moneda, inversion_pauta, codigo_origen,
                fecha_publicacion, fecha_baja, estado
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_publicacion, id_local, canal, url_publicacion, version_anuncio,
                   titulo_anuncio, renta_publicada, moneda, inversion_pauta,
                   codigo_origen, fecha_publicacion, fecha_baja, estado,
                   fecha_creacion, fecha_actualizacion
            FROM publicacion
            """;
    private static final String UPDATE = """
            UPDATE publicacion SET id_local = ?, canal = ?, url_publicacion = ?,
                version_anuncio = ?, titulo_anuncio = ?, renta_publicada = ?,
                moneda = ?, inversion_pauta = ?, codigo_origen = ?,
                fecha_publicacion = ?, fecha_baja = ?, estado = ?
            WHERE id_publicacion = ?
            """;
    private static final String DELETE = """
            UPDATE publicacion SET estado = 'C',
                fecha_baja = COALESCE(fecha_baja, CURRENT_TIMESTAMP)
            WHERE id_publicacion = ?
            """;

    @Override public List<Publicacion> listarPorInmueble(Long idLocal) {
        JdbcSupport.validarId(idLocal);
        return query(SELECT + " WHERE id_local = ? ORDER BY fecha_publicacion DESC",
                ps -> ps.setLong(1, idLocal));
    }

    @Override
    public Map<Long, String> codigosEstadoPorLocales(Collection<Long> idsLocal) {
        List<Long> ids = idsLocal == null ? List.of() : idsLocal.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> estados = new LinkedHashMap<>();
        String sql = SELECT
                + " WHERE id_local IN (" + JdbcSupport.placeholders(ids.size()) + ")"
                + " ORDER BY id_local, fecha_publicacion DESC, id_publicacion DESC";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long idLocal = rs.getLong("id_local");
                    EstadoPublicacion estado = JdbcSupport.getEnum(rs, "estado", EstadoPublicacion.class);
                    estados.putIfAbsent(idLocal,
                            estado != null ? estado.getCodigo() : EstadoPublicacion.BORRADOR.getCodigo());
                }
            }
            return estados;
        } catch (SQLException e) {
            throw failure("listar estados por locales de", e);
        }
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_publicacion"; }
    @Override protected String entityName() { return "la publicacion"; }

    @Override protected void bindInsert(PreparedStatement ps, Publicacion p) throws SQLException {
        bind(ps, p);
    }

    @Override protected void bindUpdate(PreparedStatement ps, Publicacion p) throws SQLException {
        bind(ps, p);
        ps.setLong(13, p.getIdPublicacion());
    }

    private void bind(PreparedStatement ps, Publicacion p) throws SQLException {
        ps.setLong(1, p.getInmueble().getIdLocal());
        JdbcSupport.setEnum(ps, 2, p.getCanal());
        ps.setString(3, p.getUrlPublicacion());
        ps.setInt(4, p.getVersionAnuncio());
        ps.setString(5, p.getTituloAnuncio());
        ps.setBigDecimal(6, p.getRentaPublicada());
        JdbcSupport.setEnum(ps, 7, p.getMoneda());
        ps.setBigDecimal(8, p.getInversionPauta());
        ps.setString(9, p.getCodigoOrigen());
        JdbcSupport.setTimestamp(ps, 10, p.getFechaPublicacion());
        JdbcSupport.setTimestamp(ps, 11, p.getFechaBaja());
        JdbcSupport.setEnum(ps, 12, p.getEstado());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException {
        ps.setLong(1, id);
    }

    @Override protected Publicacion mapRow(ResultSet rs) throws SQLException {
        Publicacion p = new Publicacion();
        p.setIdPublicacion(rs.getLong("id_publicacion"));
        p.setInmueble(JdbcSupport.local(rs.getLong("id_local")));
        p.setCanal(JdbcSupport.getEnum(rs, "canal", CanalPublicacion.class));
        p.setUrlPublicacion(rs.getString("url_publicacion"));
        p.setVersionAnuncio(rs.getInt("version_anuncio"));
        p.setTituloAnuncio(rs.getString("titulo_anuncio"));
        p.setRentaPublicada(rs.getBigDecimal("renta_publicada"));
        p.setMoneda(JdbcSupport.getEnum(rs, "moneda", Moneda.class));
        p.setInversionPauta(rs.getBigDecimal("inversion_pauta"));
        p.setCodigoOrigen(rs.getString("codigo_origen"));
        p.setFechaPublicacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_publicacion")));
        p.setFechaBaja(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_baja")));
        p.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoPublicacion.class));
        p.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        p.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return p;
    }

    @Override protected void assignId(Publicacion p, Long id) { p.setIdPublicacion(id); }

    @Override protected void validate(Publicacion p, boolean requireId) {
        if (p == null) throw new IllegalArgumentException("La publicacion es obligatoria.");
        if (requireId) JdbcSupport.validarId(p.getIdPublicacion());
        JdbcSupport.validarId(p.getInmueble() != null ? p.getInmueble().getIdLocal() : null);
        if (p.getCanal() == null || p.getTituloAnuncio() == null || p.getTituloAnuncio().isBlank()
                || p.getRentaPublicada() == null || p.getMoneda() == null
                || p.getCodigoOrigen() == null || p.getCodigoOrigen().isBlank()
                || p.getFechaPublicacion() == null || p.getEstado() == null) {
            throw new IllegalArgumentException("La publicacion tiene campos obligatorios incompletos.");
        }
        if (p.getVersionAnuncio() == null) p.setVersionAnuncio(1);
    }
}
