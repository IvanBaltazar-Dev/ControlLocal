package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;
import com.controllocal.model.inmueble.enums.TipoInmueble;
import com.controllocal.model.inmueble.enums.UsoInmueble;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.Propietario;

/**
 * Implementacion JDBC de LocalComercialDAO usando PreparedStatement.
 */
public class LocalComercialDAOImpl implements LocalComercialDAO {

    private static final String COLUMNAS_SQL = """
                l.id_local,
                l.codigo_local,
                l.direccion,
                l.distrito,
                l.metraje,
                l.precio_referencial,
                l.rubro_permitido,
                l.descripcion,
                l.estado,
                l.id_propietario,
                l.tipo_inmueble,
                l.uso,
                l.ambientes,
                l.antiguedad_anios,
                l.zona_urbanizacion,
                l.geo_lat,
                l.geo_long,
                l.frente,
                l.zonificacion,
                l.apto_licencia_funcionamiento,
                l.carga_electrica_kw,
                l.numero_estacionamientos,
                l.cuota_mantenimiento,
                pp.nombres_o_razon_social AS propietario_nombre,
                l.fecha_registro,
                l.fecha_actualizacion
            FROM local_comercial l
            INNER JOIN propietario p ON p.id_propietario = l.id_propietario
            INNER JOIN persona pp ON pp.id_persona = p.id_persona
            """;

    private static final String INSERT_SQL = """
            INSERT INTO local_comercial (
                codigo_local,
                direccion,
                distrito,
                metraje,
                precio_referencial,
                rubro_permitido,
                descripcion,
                estado,
                id_propietario,
                tipo_inmueble,
                uso,
                ambientes,
                antiguedad_anios,
                zona_urbanizacion,
                geo_lat,
                geo_long,
                frente,
                zonificacion,
                apto_licencia_funcionamiento,
                carga_electrica_kw,
                numero_estacionamientos,
                cuota_mantenimiento
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID_SQL =
            "SELECT" + COLUMNAS_SQL + " WHERE id_local = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT" + COLUMNAS_SQL + " ORDER BY id_local";

    private static final String SELECT_PAGE_SQL =
            "SELECT" + COLUMNAS_SQL + " ORDER BY id_local LIMIT ? OFFSET ?";

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM local_comercial";

    private static final String UPDATE_SQL = """
            UPDATE local_comercial
            SET codigo_local = ?,
                direccion = ?,
                distrito = ?,
                metraje = ?,
                precio_referencial = ?,
                rubro_permitido = ?,
                descripcion = ?,
                estado = ?,
                id_propietario = ?,
                tipo_inmueble = ?,
                uso = ?,
                ambientes = ?,
                antiguedad_anios = ?,
                zona_urbanizacion = ?,
                geo_lat = ?,
                geo_long = ?,
                frente = ?,
                zonificacion = ?,
                apto_licencia_funcionamiento = ?,
                carga_electrica_kw = ?,
                numero_estacionamientos = ?,
                cuota_mantenimiento = ?
            WHERE id_local = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE local_comercial
            SET estado = 'I'
            WHERE id_local = ?
            """;

    @Override
    public Long crear(LocalComercial local) {
        validarLocalParaPersistencia(local, false);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, local.getCodigoLocal());
            statement.setString(2, local.getDireccion());
            statement.setString(3, local.getDistrito());
            statement.setBigDecimal(4, local.getMetraje());
            statement.setBigDecimal(5, local.getPrecioReferencial());
            statement.setString(6, local.getRubroPermitido());
            statement.setString(7, local.getDescripcion());
            JdbcSupport.setEnum(statement, 8, local.getEstado());
            statement.setLong(9, local.getIdPropietario());
            JdbcSupport.setEnum(statement, 10, local.getTipoInmueble());
            JdbcSupport.setEnum(statement, 11, local.getUso());
            JdbcSupport.setInteger(statement, 12, local.getAmbientes());
            JdbcSupport.setInteger(statement, 13, local.getAntiguedadAnios());
            statement.setString(14, local.getZonaUrbanizacion());
            statement.setBigDecimal(15, local.getGeoLat());
            statement.setBigDecimal(16, local.getGeoLong());
            statement.setBigDecimal(17, local.getFrente());
            statement.setString(18, local.getZonificacion());
            JdbcSupport.setBoolean(statement, 19, local.getAptoLicenciaFuncionamiento());
            statement.setBigDecimal(20, local.getCargaElectricaKw());
            JdbcSupport.setInteger(statement, 21, local.getNumeroEstacionamientos());
            statement.setBigDecimal(22, local.getCuotaMantenimiento());

            int affectedRows = statement.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("No se pudo insertar el local comercial.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long idGenerado = generatedKeys.getLong(1);
                    local.setIdLocal(idGenerado);
                    return idGenerado;
                }
            }

            throw new DAOException("La insercion no devolvio el id generado del local comercial.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear el local comercial con codigo " + local.getCodigoLocal() + ".", e);
        }
    }

    @Override
    public Optional<LocalComercial> buscarPorId(Long id) {
        validarId(id);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar el local comercial con id " + id + ".", e);
        }
    }

    @Override
    public List<LocalComercial> listarTodos() {
        List<LocalComercial> locales = new ArrayList<>();

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                locales.add(mapRow(resultSet));
            }

            return locales;
        } catch (SQLException e) {
            throw new DAOException("Error al listar los locales comerciales.", e);
        }
    }

    @Override
    public List<LocalComercial> listarPagina(int limite, int desplazamiento) {
        List<LocalComercial> locales = new ArrayList<>();

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_PAGE_SQL)) {

            statement.setInt(1, limite);
            statement.setInt(2, desplazamiento);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    locales.add(mapRow(resultSet));
                }
            }
            return locales;
        } catch (SQLException e) {
            throw new DAOException("Error al listar la pagina de locales comerciales.", e);
        }
    }

    @Override
    public long contar() {
        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DAOException("Error al contar los locales comerciales.", e);
        }
    }

    @Override
    public boolean actualizar(LocalComercial local) {
        validarLocalParaPersistencia(local, true);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, local.getCodigoLocal());
            statement.setString(2, local.getDireccion());
            statement.setString(3, local.getDistrito());
            statement.setBigDecimal(4, local.getMetraje());
            statement.setBigDecimal(5, local.getPrecioReferencial());
            statement.setString(6, local.getRubroPermitido());
            statement.setString(7, local.getDescripcion());
            JdbcSupport.setEnum(statement, 8, local.getEstado());
            statement.setLong(9, local.getIdPropietario());
            JdbcSupport.setEnum(statement, 10, local.getTipoInmueble());
            JdbcSupport.setEnum(statement, 11, local.getUso());
            JdbcSupport.setInteger(statement, 12, local.getAmbientes());
            JdbcSupport.setInteger(statement, 13, local.getAntiguedadAnios());
            statement.setString(14, local.getZonaUrbanizacion());
            statement.setBigDecimal(15, local.getGeoLat());
            statement.setBigDecimal(16, local.getGeoLong());
            statement.setBigDecimal(17, local.getFrente());
            statement.setString(18, local.getZonificacion());
            JdbcSupport.setBoolean(statement, 19, local.getAptoLicenciaFuncionamiento());
            statement.setBigDecimal(20, local.getCargaElectricaKw());
            JdbcSupport.setInteger(statement, 21, local.getNumeroEstacionamientos());
            statement.setBigDecimal(22, local.getCuotaMantenimiento());
            statement.setLong(23, local.getIdLocal());

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar el local comercial con id " + local.getIdLocal() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        validarId(id);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {

            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar el local comercial con id " + id + ".", e);
        }
    }

    private LocalComercial mapRow(ResultSet rs) throws SQLException {
        Timestamp fechaRegistro = rs.getTimestamp("fecha_registro");
        Timestamp fechaActualizacion = rs.getTimestamp("fecha_actualizacion");
        long idPropietario = rs.getLong("id_propietario");

        LocalComercial local = new LocalComercial(
                rs.getLong("id_local"),
                rs.getString("codigo_local"),
                rs.getString("direccion"),
                rs.getString("distrito"),
                rs.getBigDecimal("metraje"),
                rs.getBigDecimal("precio_referencial"),
                rs.getString("rubro_permitido"),
                rs.getString("descripcion"),
                JdbcSupport.getEnum(rs, "estado", EstadoLocalComercial.class),
                idPropietario,
                fechaRegistro != null ? fechaRegistro.toLocalDateTime() : null,
                fechaActualizacion != null ? fechaActualizacion.toLocalDateTime() : null
        );

        local.setTipoInmueble(JdbcSupport.getNullableEnum(rs, "tipo_inmueble", TipoInmueble.class));
        local.setUso(JdbcSupport.getNullableEnum(rs, "uso", UsoInmueble.class));
        local.setAmbientes(JdbcSupport.getNullableInt(rs, "ambientes"));
        local.setAntiguedadAnios(JdbcSupport.getNullableInt(rs, "antiguedad_anios"));
        local.setZonaUrbanizacion(rs.getString("zona_urbanizacion"));
        local.setGeoLat(rs.getBigDecimal("geo_lat"));
        local.setGeoLong(rs.getBigDecimal("geo_long"));
        local.setFrente(rs.getBigDecimal("frente"));
        local.setZonificacion(rs.getString("zonificacion"));
        local.setAptoLicenciaFuncionamiento(JdbcSupport.getNullableBoolean(rs, "apto_licencia_funcionamiento"));
        local.setCargaElectricaKw(rs.getBigDecimal("carga_electrica_kw"));
        local.setNumeroEstacionamientos(JdbcSupport.getNullableInt(rs, "numero_estacionamientos"));
        local.setCuotaMantenimiento(rs.getBigDecimal("cuota_mantenimiento"));

        Propietario propietario = new Propietario();
        propietario.setIdPropietario(idPropietario);
        propietario.setNombresORazonSocial(rs.getString("propietario_nombre"));
        local.setPropietario(propietario);
        return local;
    }

    private void validarLocalParaPersistencia(LocalComercial local, boolean requiereId) {
        if (local == null) {
            throw new IllegalArgumentException("El local comercial no puede ser null.");
        }
        if (requiereId) {
            validarId(local.getIdLocal());
        }
        if (local.getCodigoLocal() == null || local.getCodigoLocal().isBlank()) {
            throw new IllegalArgumentException("El codigo del local es obligatorio.");
        }
        if (local.getDireccion() == null || local.getDireccion().isBlank()) {
            throw new IllegalArgumentException("La direccion del local es obligatoria.");
        }
        if (local.getDistrito() == null || local.getDistrito().isBlank()) {
            throw new IllegalArgumentException("El distrito del local es obligatorio.");
        }
        if (local.getMetraje() == null) {
            throw new IllegalArgumentException("El metraje del local es obligatorio.");
        }
        if (local.getMetraje().signum() <= 0) {
            throw new IllegalArgumentException("El metraje del local debe ser mayor que cero.");
        }
        if (local.getPrecioReferencial() == null) {
            throw new IllegalArgumentException("El precio referencial del local es obligatorio.");
        }
        if (local.getPrecioReferencial().signum() < 0) {
            throw new IllegalArgumentException("El precio referencial del local no puede ser negativo.");
        }
        if (local.getRubroPermitido() == null || local.getRubroPermitido().isBlank()) {
            throw new IllegalArgumentException("El rubro permitido es obligatorio.");
        }
        if (local.getEstado() == null) {
            throw new IllegalArgumentException("El estado del local es obligatorio.");
        }
        validarId(local.getIdPropietario());
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }
}
