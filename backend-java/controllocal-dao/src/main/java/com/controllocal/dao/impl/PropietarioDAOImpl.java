package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;

public class PropietarioDAOImpl implements PropietarioDAO {

    private static final String INSERT_SQL = "INSERT INTO propietario (id_persona) VALUES (?)";
    private static final String SELECT_SQL = """
            SELECT pr.id_propietario,
                   p.id_persona, p.tipo_persona, p.tipo_documento, p.numero_documento,
                   p.nombres_o_razon_social, p.telefono, p.correo, p.estado,
                   p.consentimiento_uso_dato,
                   p.fecha_creacion, p.fecha_actualizacion
            FROM propietario pr
            INNER JOIN persona p ON pr.id_persona = p.id_persona
            """;
    private static final String UPDATE_SQL = "UPDATE propietario SET id_persona = ? WHERE id_propietario = ?";
    private static final String DELETE_SQL = """
            UPDATE persona p
            INNER JOIN propietario pr ON pr.id_persona = p.id_persona
            SET p.estado = 'I'
            WHERE pr.id_propietario = ?
            """;

    @Override
    public Long crear(Propietario propietario) {
        validar(propietario, false);
        asegurarPersonaCreada(propietario);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, propietario.getPersona().getIdPersona());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    propietario.setIdPropietario(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de propietario.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear propietario.", e);
        }
    }

    @Override
    public Optional<Propietario> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE pr.id_propietario = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar propietario con id " + id + ".", e);
        }
    }

    @Override
    public List<Propietario> listarTodos() {
        List<Propietario> propietarios = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY pr.id_propietario DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                propietarios.add(mapRow(rs));
            }
            return propietarios;
        } catch (SQLException e) {
            throw new DAOException("Error al listar propietarios.", e);
        }
    }

    @Override
    public List<Propietario> listarPagina(int limite, int desplazamiento) {
        List<Propietario> propietarios = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY pr.id_propietario DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limite);
            ps.setInt(2, desplazamiento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    propietarios.add(mapRow(rs));
                }
            }
            return propietarios;
        } catch (SQLException e) {
            throw new DAOException("Error al listar la pagina de propietarios.", e);
        }
    }

    @Override
    public long contar() {
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM propietario");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DAOException("Error al contar propietarios.", e);
        }
    }

    @Override
    public List<Propietario> listarPorIds(Collection<Long> ids) {
        List<Propietario> propietarios = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return propietarios;
        }
        String sql = SELECT_SQL + " WHERE pr.id_propietario IN (" + JdbcSupport.placeholders(ids.size())
                + ") ORDER BY pr.id_propietario DESC";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Long id : ids) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    propietarios.add(mapRow(rs));
                }
            }
            return propietarios;
        } catch (SQLException e) {
            throw new DAOException("Error al listar propietarios por ids.", e);
        }
    }

    // Conteo, por propietario, de los locales distintos que aparecen "en seguimiento" (en alguna
    // captacion o prospeccion) DENTRO del alcance del usuario, restringido a los propietarios dados
    // (la pagina actual). Reemplaza el escaneo en memoria de todas las captaciones/prospecciones.
    // Alcance: idsAgente == null => sin filtro de agente (admin); vacio => ningun agente casa;
    // idsCaptacionSupervisadas => captaciones que el broker supervisa aunque el agente no sea suyo.
    @Override
    public Map<Long, Integer> contarLocalesEnSeguimiento(
            Collection<Long> idsPropietario,
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacionSupervisadas) {
        Map<Long, Integer> conteo = new HashMap<>();
        if (idsPropietario == null || idsPropietario.isEmpty()) {
            return conteo;
        }
        List<Long> params = new ArrayList<>();
        String propsPh = JdbcSupport.placeholders(idsPropietario.size());

        // Rama captacion: locales del propietario con al menos una captacion en alcance.
        StringBuilder cap = new StringBuilder()
                .append("SELECT l.id_propietario AS id_propietario, l.id_local AS id_local ")
                .append("FROM local_comercial l INNER JOIN captacion c ON c.id_local = l.id_local ")
                .append("WHERE l.id_propietario IN (").append(propsPh).append(")");
        params.addAll(idsPropietario);
        if (idsAgente != null) {
            List<String> ors = new ArrayList<>();
            if (!idsAgente.isEmpty()) {
                ors.add("c.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size()) + ")");
                params.addAll(idsAgente);
            }
            if (idsCaptacionSupervisadas != null && !idsCaptacionSupervisadas.isEmpty()) {
                ors.add("c.id_captacion IN (" + JdbcSupport.placeholders(idsCaptacionSupervisadas.size()) + ")");
                params.addAll(idsCaptacionSupervisadas);
            }
            cap.append(ors.isEmpty() ? " AND 1=0" : " AND (" + String.join(" OR ", ors) + ")");
        }

        // Rama prospeccion: locales del propietario con al menos una prospeccion en alcance.
        StringBuilder pros = new StringBuilder()
                .append("SELECT l.id_propietario, l.id_local ")
                .append("FROM local_comercial l INNER JOIN prospeccion pr ON pr.id_local = l.id_local ")
                .append("WHERE l.id_propietario IN (").append(propsPh).append(")");
        params.addAll(idsPropietario);
        if (idsAgente != null) {
            if (!idsAgente.isEmpty()) {
                pros.append(" AND pr.id_agente IN (").append(JdbcSupport.placeholders(idsAgente.size())).append(")");
                params.addAll(idsAgente);
            } else {
                pros.append(" AND 1=0");
            }
        }

        String sql = "SELECT id_propietario, COUNT(DISTINCT id_local) AS n FROM ("
                + cap + " UNION " + pros + ") t GROUP BY id_propietario";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long p : params) {
                ps.setLong(idx++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    conteo.put(rs.getLong("id_propietario"), rs.getInt("n"));
                }
            }
            return conteo;
        } catch (SQLException e) {
            throw new DAOException("Error al contar locales en seguimiento por propietario.", e);
        }
    }

    @Override
    public boolean actualizar(Propietario propietario) {
        validar(propietario, true);
        new PersonaDAOImpl().actualizar(propietario.getPersona());
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setLong(1, propietario.getPersona().getIdPersona());
            ps.setLong(2, propietario.getIdPropietario());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar propietario con id " + propietario.getIdPropietario() + ".", e);
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
            throw new DAOException("Error al eliminar propietario con id " + id + ".", e);
        }
    }

    private Propietario mapRow(ResultSet rs) throws SQLException {
        Propietario propietario = new Propietario();
        propietario.setIdPropietario(rs.getLong("id_propietario"));
        propietario.setPersona(JdbcSupport.mapPersona(rs));
        return propietario;
    }

    private void validar(Propietario propietario, boolean requiereId) {
        if (propietario == null) {
            throw new IllegalArgumentException("El propietario no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(propietario.getIdPropietario());
        }
        Persona persona = propietario.getPersona();
        if (persona == null) {
            throw new IllegalArgumentException("El propietario debe estar asociado a una persona.");
        }
        if (requiereId) {
            JdbcSupport.validarId(JdbcSupport.getIdPersona(persona));
        }
    }

    private void asegurarPersonaCreada(Propietario propietario) {
        if (propietario.getPersona().getIdPersona() == null
                || propietario.getPersona().getIdPersona() <= 0) {
            new PersonaDAOImpl().crear(propietario.getPersona());
        }
    }
}
