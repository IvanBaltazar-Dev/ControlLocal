package com.controllocal.service;

import java.time.Instant;
import java.util.List;

/** RF-001 / CAT-E01: identidad segura y contexto elegido entre atribuciones vigentes. */
public interface ContextosService {
    long DURACION_SEGUNDOS = 30 * 60;

    record IdentidadAutenticada(long idCuenta,String nombreUsuario,String correoPropio,String nombreProfesionalDeclarado) { }
    record Contexto(String referencia, long idOrganizacion, long idPersona, long idRolOperativo,
                    String rol, String modalidad, String nombreOrganizacion) { }
    record SesionAccesoDTO(String referencia, long revision, IdentidadAutenticada identidad,
                           Contexto activo, Instant emitidaEn, Instant venceEn,
                           EstadoDeAcceso restricciones) { }
    record AccesoIniciado(SesionAccesoDTO sesion,MfaService.Desafio desafio) { }

    /** Prueba y apertura indivisibles; si corresponde retorna desafío en lugar de sesión. */
    AccesoIniciado iniciarAcceso(String identificador,char[] contrasena,boolean permitirDesafio);
    SesionAccesoDTO completarSegundoFactor(String desafio,String codigo,String ip,String agenteUsuario);

    SesionAccesoDTO comprobarSesion(String referencia, long revision, Instant emitidaEn, Instant venceEn);
    List<Contexto> contextos(String referencia, long revision);
    SesionAccesoDTO seleccionar(String referencia, long revision, String contextoRef);
    SesionAccesoDTO renovar(String referencia, long revision);
    /** Conserva el cierre global de todas las sesiones de la cuenta, no sólo este dispositivo. */
    void cerrar(String referencia, long revision);
    /** Revalida y bloquea sesión/contexto dentro de la transacción de una actuación sensible. */
    SesionAccesoDTO exigirContexto(String referencia, long revision, Actor actor);
    /** Misma guarda contextual con Cuenta/SID READ; no muta identidad ni revisión de sesión. */
    SesionAccesoDTO exigirContextoConLecturaProtegida(String referencia,long revision,Actor actor);
    /** Operación propia: Cuenta WRITE→Sesión WRITE, sin exigir pertenencia/contexto comercial. */
    SesionAccesoDTO exigirIdentidad(String referencia,long revision);
}
