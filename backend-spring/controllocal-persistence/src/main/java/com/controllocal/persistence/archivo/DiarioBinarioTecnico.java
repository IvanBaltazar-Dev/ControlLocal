package com.controllocal.persistence.archivo;

import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Carril JDBC técnico de RF080 en el mismo PostgreSQL. Dos conexiones técnicas
 * independientes evitan que N padres ocupando el pool principal impidan registrar
 * su intención antes de I/O. No es un DataSource/TransactionManager bean: JPA y
 * las transacciones comerciales conservan su configuración y su autoridad.
 */
@Component
public class DiarioBinarioTecnico {
    private final HikariDataSource conexiones;
    private final JdbcTemplate jdbc;
    private final IntentoSalidaBinariaRepository repositorio;
    private final JdbcTransactionManager gestor;

    public DiarioBinarioTecnico(DataSourceProperties datos,
            @Value("${spring.datasource.hikari.schema:}") String esquema) {
        conexiones=datos.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        conexiones.setPoolName("BROX-diario-binario-tecnico");
        // Límites de infraestructura; no cuotas, plazos de negocio ni política de conservación.
        conexiones.setMaximumPoolSize(2);conexiones.setMinimumIdle(0);
        conexiones.setConnectionTimeout(5000);conexiones.setValidationTimeout(1000);
        conexiones.setConnectionInitSql("SET lock_timeout='5s'");
        if(esquema!=null&&!esquema.isBlank())conexiones.setSchema(esquema);
        jdbc=new JdbcTemplate(conexiones);
        repositorio=new IntentoSalidaBinariaRepository(jdbc);
        gestor=new JdbcTransactionManager(conexiones);
    }
    public IntentoSalidaBinariaRepository repositorio(){return repositorio;}
    public JdbcTemplate jdbc(){return jdbc;}
    public PlatformTransactionManager gestor(){return gestor;}
    @PreDestroy public void cerrar(){conexiones.close();}
}
