package com.controllocal.integracion;

import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.service.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

/** Identidad individual TEST real en C1, sin broker/empresa ni credencial autenticable. */
final class EspacioIndividualB0DePrueba {
    private EspacioIndividualB0DePrueba(){}
    static Actor crear(JdbcTemplate jdbc,TransactionTemplate tx){return tx.execute(t->{
        String sufijo=UUID.randomUUID().toString();
        long org=jdbc.queryForObject("INSERT INTO organizacion(codigo,nombre,modalidad,estado) VALUES (?,?,'INDIVIDUAL','A') RETURNING id_organizacion",Long.class,"B0-"+sufijo.substring(0,20),"Independiente TEST B0");
        long cuenta=jdbc.queryForObject("INSERT INTO cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) VALUES (?,?,clock_timestamp(),'TEST NO LOGIN') RETURNING id_cuenta_acceso",Long.class,"b0-"+sufijo,"b0-"+sufijo+"@example.invalid");
        long persona=jdbc.queryForObject("INSERT INTO persona(organizacion_id,nombres_o_razon_social) VALUES (?,'Independiente TEST') RETURNING id_persona",Long.class,org);
        long rol=jdbc.queryForObject("INSERT INTO persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) VALUES (?,?,'AGENTE',?) RETURNING id_persona_rol",Long.class,org,persona,OrigenesDePrueba.hoy());
        jdbc.update("INSERT INTO detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) VALUES (?,?,?,?, 'D')",org,rol,"B0-"+rol,OrigenesDePrueba.hoy());
        jdbc.update("INSERT INTO usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) VALUES (?,?,?,'AGENTE',false,'A')",org,cuenta,persona);
        return new Actor(org,persona,rol,Actor.AGENTE);
    });}
}
