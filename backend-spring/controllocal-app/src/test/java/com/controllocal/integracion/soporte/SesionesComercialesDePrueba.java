package com.controllocal.integracion.soporte;

import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.CuentaService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.Totp;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;

/**
 * Fixture posterior a identidad de TEST. No certifica login por insertar SID.
 * La atribución se selecciona en Core; un MFA pendiente se enrola y confirma por
 * los casos personales reales, sin insertar factores ni modificar credenciales por SQL.
 */
public final class SesionesComercialesDePrueba {
    private final JdbcTemplate jdbc;private final ContextosService contextos;private final CuentaService cuentas;
    private final FactorAutenticacionRepository factores;private final CifradoSecretos cifrado;
    private final Map<Actor,ContextoSesion> abiertas=new HashMap<>();
    public SesionesComercialesDePrueba(JdbcTemplate jdbc,ContextosService contextos,CuentaService cuentas,
            FactorAutenticacionRepository factores,CifradoSecretos cifrado){
        this.jdbc=jdbc;this.contextos=contextos;this.cuentas=cuentas;this.factores=factores;this.cifrado=cifrado;
    }
    public synchronized ContextoSesion de(Actor actor){
        if(abiertas.containsKey(actor))return abiertas.get(actor);
        var s=SesionesDePrueba.abrir(jdbc,contextos,actor).acceso();
        if(s.restricciones().debeCambiarContrasena())
            throw new AssertionError("El montaje debe proporcionar una cuenta de TEST sin cambio de contraseña pendiente; no se altera por conveniencia.");
        if(s.restricciones().debeEnrolarMfa()){
            cuentas.iniciarMfa(s.referencia(),s.revision());
            var f=factores.pendientesDe(s.identidad().idCuenta()).getFirst();
            byte[] secreto=cifrado.descifrar(f.getSecretoCifrado(),f.getNonce(),f.getVersionClave());
            cuentas.confirmarMfa(s.referencia(),s.revision(),Totp.generar(secreto,Totp.pasoDe(Instant.now())));
            // El efecto personal acaba de revocar todos los SID de esa cuenta. Sólo se
            // invalida el caché del montaje: los ContextoSesion ya entregados conservan
            // su identidad anterior y deben seguir rechazándose si alguien los reutiliza.
            abiertas.keySet().removeIf(a->a.idOrganizacion()==actor.idOrganizacion()
                    &&a.idPersona()==actor.idPersona());
            s=SesionesDePrueba.abrir(jdbc,contextos,actor).acceso();
        }
        if(s.restricciones().debeCambiarContrasena()||s.restricciones().debeEnrolarMfa())
            throw new AssertionError("La sesión del fixture conserva restricciones; no puede certificar autoridad comercial.");
        var sesion=ContextoSesion.de(s);abiertas.put(actor,sesion);return sesion;
    }
    /** El rol ADMIN realmente atribuido en la pertenencia; no traduce un BROKER a gobierno. */
    public Actor gobiernoDe(long org){
        var filas=jdbc.queryForList("""
            select r.id_persona_rol,r.id_persona from usuario_organizacion u
              join persona_rol r on r.organizacion_id=u.organizacion_id and r.id_persona=u.id_persona
             where u.organizacion_id=? and u.estado='A' and u.administrar
               and r.tipo_rol='ADMIN' and r.vigencia_hasta is null
               and r.vigencia_desde<=(clock_timestamp() at time zone 'America/Lima')::date
             order by r.id_persona_rol
            """,org);
        if(filas.isEmpty())throw new AssertionError("El montaje necesita un ADMIN vigente explícito en la organización.");
        var f=filas.getFirst();return new Actor(org,((Number)f.get("id_persona")).longValue(),
                ((Number)f.get("id_persona_rol")).longValue(),Actor.TENANT_ADMIN);
    }
}
