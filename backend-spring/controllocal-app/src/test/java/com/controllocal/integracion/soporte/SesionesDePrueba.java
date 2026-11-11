package com.controllocal.integracion.soporte;

import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/** Fixture posterior a prueba de identidad; no afirma login ni sustituye la selección canónica. */
public final class SesionesDePrueba {
    private static final SecureRandom ALEATORIO=new SecureRandom();
    private SesionesDePrueba() { }
    public static SesionDeRequest abrir(JdbcTemplate jdbc,ContextosService contextos,Actor actor) {
        String tipo=actor.esTenantAdmin()?"ADMIN":actor.esBroker()?"BROKER":actor.esAgente()?"AGENTE":null;
        if(tipo==null||!Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from persona_rol r join persona p using(id_persona)
              join organizacion o on o.id_organizacion=r.organizacion_id
              where r.id_persona_rol=? and r.id_persona=? and r.organizacion_id=? and r.tipo_rol=?
                and r.vigencia_hasta is null and r.vigencia_desde<=current_date and p.estado='A'
                and o.estado='A' and o.modalidad in ('EMPRESARIAL','INDIVIDUAL'))
            """,Boolean.class,actor.idRolOperativo(),actor.idPersona(),actor.idOrganizacion(),tipo)))
            throw new IllegalStateException("El Actor del fixture requiere persona, rol y modalidad explicitos vigentes; no se inventan atribuciones.");
        var miembros=jdbc.queryForList("select id_usuario,rol,administrar,estado from usuario_organizacion where organizacion_id=? and id_persona=?",
                actor.idOrganizacion(),actor.idPersona());
        if(miembros.size()>1)throw new IllegalStateException("El fixture no puede elegir entre dos cuentas de una persona local.");
        long cuenta;
        if(miembros.isEmpty()) {
            String ref="fixture-c1-"+UUID.randomUUID();
            cuenta=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),?) returning id_cuenta_acceso",
                    Long.class,ref,ref+"@example.invalid","fixture-sin-credencial-autenticable");
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,?,?,'A')",
                    actor.idOrganizacion(),cuenta,actor.idPersona(),actor.esTenantAdmin()?null:actor.rolEfectivo(),actor.esTenantAdmin());
        } else {
            var miembro=miembros.getFirst();cuenta=((Number)miembro.get("id_usuario")).longValue();
            if(!"A".equals(miembro.get("estado"))||actor.esTenantAdmin()&&!Boolean.TRUE.equals(miembro.get("administrar"))
                    ||!actor.esTenantAdmin()&&!actor.rolEfectivo().equals(miembro.get("rol")))
                throw new IllegalStateException("El fixture no reactiva bajas ni concede otra atribucion a la pertenencia.");
        }
        byte[] material=new byte[32];ALEATORIO.nextBytes(material);
        String sid=Base64.getUrlEncoder().withoutPadding().encodeToString(material);Instant ahora=Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant emitida=ahora.truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("insert into sesion_acceso(referencia_hash,semilla_contextos,id_cuenta_acceso,revision,creada_en,emitida_en,vence_en) values(?,?,?,1,?,?,?)",
                Idempotencia.huellaExacta(sid),Idempotencia.huellaExacta(UUID.randomUUID().toString()),cuenta,
                Timestamp.from(ahora),Timestamp.from(emitida),Timestamp.from(emitida.plusSeconds(ContextosService.DURACION_SEGUNDOS)));
        var contexto=contextos.contextos(sid,1).stream().filter(c->c.idOrganizacion()==actor.idOrganizacion()
                &&c.idPersona()==actor.idPersona()&&c.idRolOperativo()==actor.idRolOperativo()&&c.rol().equals(actor.rolEfectivo()))
                .findFirst().orElseThrow(()->new IllegalStateException("El Actor del fixture no tiene un contexto vigente."));
        var elegida=contextos.seleccionar(sid,1,contexto.referencia());
        return new SesionDeRequest(new TokenService("").de(elegida),elegida);
    }
}
