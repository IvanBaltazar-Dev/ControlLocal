package com.controllocal.persistence.repositorio;

/**
 * Proyeccion del <b>padron de cuentas</b> del tenant, para gobierno.
 *
 * <p><b>Lleva los DOS identificadores publicos y esa es su razon de ser.</b> El
 * contrato congelado identifica agentes y brokers por {@code persona_rol.id}
 * —el id del ROL—, mientras que las operaciones de acceso
 * ({@code /accesos/{idPersona}/…}) hablan de la PERSONA, porque una persona
 * puede tener mas de un rol y su credencial es una sola. Sin un sitio que
 * publique la correspondencia, el SPA no puede ofrecer ninguna accion de
 * gobierno sobre alguien que ve en una ficha comercial; y la alternativa
 * —anadir {@code idPersona} a {@code AgenteResponse} y {@code BrokerResponse}—
 * tocaria dos DTO congelados.
 *
 * <p>{@code idCredencial} <b>no sale al cable</b>: sirve para cruzar esta fila
 * con la banda de gobierno y con el recuento de codigos, que viajan en sus
 * propias consultas.
 *
 * <p><b>Por que esto es una consulta pelada.</b> La primera version resolvia
 * todo de una vez —banda por {@code left join} a una entidad sin relacion
 * declarada, y factores y codigos por subconsultas {@code count(...)} en el
 * {@code SELECT}— y devolvia <b>una unica fila</b> para todo el tenant. Aqui
 * solo quedan {@code join} por relacion; lo demas se cruza en el service, que
 * es aburrido, verificable y cuesta dos consultas mas sobre decenas de filas.
 */
public record CuentaDeGobiernoFila(Long idPersona,
                                   Long idRol,
                                   Long idCredencial,
                                   String nombre,
                                   String nombreUsuario,
                                   String estadoAdministrativo,
                                   boolean debeCambiarContrasena,
                                   boolean debeEnrolarMfa) {
}
