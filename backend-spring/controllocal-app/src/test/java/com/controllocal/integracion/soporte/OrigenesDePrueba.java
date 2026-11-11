package com.controllocal.integracion.soporte;

import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.UnidadRelacionadaService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.MotorDeCaptura.Respuesta;
import com.controllocal.service.soporte.Procedencia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/** Identidad técnica TEST explícita; propiedades y atribución física se crean por captura Core real. */
public final class OrigenesDePrueba {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MotorDeCaptura captura;
    private final ContextosService contextos;
    public OrigenesDePrueba(JdbcTemplate jdbc,PlatformTransactionManager gestor,
            MotorDeCaptura captura,ContextosService contextos) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(gestor);this.captura=captura;this.contextos=contextos;
    }
    public record Equipo(Actor agente,Actor colega,Actor broker,Actor admin) { }
    public Equipo empresa() {
        return tx.execute(t->{
            String ref=UUID.randomUUID().toString().replace("-","");
            long org=jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,'EMPRESARIAL','I') returning id_organizacion",Long.class,"RF14-"+ref.substring(0,15),"TEST origen físico");
            Actor broker=profesional(org,Actor.BROKER,true);
            long admin=jdbc.queryForObject("select id_persona_rol from persona_rol where organizacion_id=? and id_persona=? and tipo_rol='ADMIN'",Long.class,org,broker.idPersona());
            Actor a=profesional(org,Actor.AGENTE,false),b=profesional(org,Actor.AGENTE,false);
            for(Actor agente:java.util.List.of(a,b)) jdbc.update("insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo) values(?,?,?,?,?)",org,broker.idRolOperativo(),agente.idRolOperativo(),hoy(),"Supervisión TEST RF014 explícita");
            jdbc.update("update organizacion set estado='A' where id_organizacion=?",org);
            return new Equipo(a,b,broker,new Actor(org,broker.idPersona(),admin,Actor.TENANT_ADMIN));
        });
    }
    public Actor brokerAdicional(Equipo equipo){return tx.execute(t->profesional(equipo.broker().idOrganizacion(),Actor.BROKER,false));}
    public ContextoSesion sesion(Actor actor) {
        if(!actor.esAgente())throw new IllegalArgumentException("El helper usa sólo sesión AGENTE; para gobierno/broker reutilizar SesionesComercialesDePrueba con MFA real.");
        return ContextoSesion.de(SesionesDePrueba.abrir(jdbc,contextos,actor).acceso());
    }
    public long propiedad(Actor actor,String referencia) {
        var e=captura.avanzar(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD,null,null,
                Map.of("tipoPropiedad",Respuesta.conocida("TERRENO"),"distrito",Respuesta.conocida("Surco"),
                        "referenciaInterna",Respuesta.conocida(referencia+" TEST "+UUID.randomUUID())),null),
                Procedencia.deLaPantalla(),actor);
        if(!e.listoParaEjecutar())throw new AssertionError("El objeto físico parcial debe estar identificado sin Encargo o partida ficticios.");
        var alta=captura.ejecutar(e.idBorrador(),e.version(),clave(),Procedencia.deLaPantalla(),actor);
        if(!alta.idsEncargos().isEmpty())throw new AssertionError("El fixture de origen no abre Encargos.");
        return alta.idPropiedad();
    }
    public UnidadRelacionadaService.DatosOrigen datos(long matriz,long unidad,Actor actor,UnidadRelacionadaService origen) {
        var c=origen.capacidadSobreOrigen(matriz,unidad,actor);
        return new UnidadRelacionadaService.DatosOrigen(matriz,unidad,hoy(),null,null,"Declaración explícita TEST del origen",c.versionMatriz(),c.versionUnidad());
    }
    private Actor profesional(long org,String rol,boolean administrar) {
        String ref=UUID.randomUUID().toString().replace("-","");
        long cuenta=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),'fixture-rf014-no-login') returning id_cuenta_acceso",Long.class,"rf14-"+ref,"rf14-"+ref+"@example.invalid");
        long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Profesional TEST RF014') returning id_persona",Long.class,org);
        long id=rol(org,persona,rol);
        if(Actor.AGENTE.equals(rol))jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,?,'D')",org,id,"RF14-A-"+id,hoy());
        else jdbc.update("insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion) values(?,?,?,?)",org,id,"RF14-B-"+id,hoy());
        if(administrar)rol(org,persona,"ADMIN");
        jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,?,?,'A')",org,cuenta,persona,rol,administrar);
        return new Actor(org,persona,id,rol);
    }
    private long rol(long org,long persona,String rol) {
        return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,?,?) returning id_persona_rol",Long.class,org,persona,rol,hoy());
    }
    public static LocalDate hoy(){return LocalDate.now(ZoneId.of("America/Lima"));}
    public static String clave(){return "rf014-"+UUID.randomUUID();}
}
