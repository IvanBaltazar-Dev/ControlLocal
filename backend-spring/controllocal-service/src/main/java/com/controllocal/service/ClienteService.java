package com.controllocal.service;

import com.controllocal.service.soporte.Autorizaciones;

import java.time.LocalDateTime;

/**
 * Casos de uso del cliente interesado (F3, lado DEMANDA). Los records espejan
 * el contrato CONGELADO (Dtos.ClienteRequest/ClienteResponse de la v1).
 *
 * <p>El cliente es un ROL de persona (Party-Role), no una tabla de persona
 * aparte: {@code id} = {@code persona_rol.id} del rol CLIENTE, igual que
 * {@code idPropietario} en la vertical de oferta.
 *
 * <p><b>Alcance, la rareza que hay que preservar</b> (contrato §2): el cliente
 * es CATALOGO COMPARTIDO. ADMIN y AGENTE ven y editan TODOS los de su
 * organizacion —no hay regla de pertenencia—; solo el BROKER queda acotado, y
 * su conjunto se deriva de las oportunidades de su equipo.
 */
public interface ClienteService {

    /** Espejo de ClienteRequest. */
    /**
     * @param consentimientoUsoDato en el ALTA es la casilla unica de
     *                              autorizacion (D-27), y <b>lo unico que el
     *                              usuario aporta</b>: sin ella no hay alta.
     *                              Sigue viajando por el contrato congelado.
     *                              Canal, fecha, actor, tenant y version del
     *                              aviso los pone el backend, y por eso no hay
     *                              ningun otro campo de autorizacion aqui.
     */
    record DatosCliente(String tipoPersona, String tipoDocumento, String numeroDocumento, String nombre,
                        String telefono, String correo, String rubroComercial, Boolean consentimientoContacto,
                        Boolean consentimientoUsoDato, String estado) {
    }

    /** Espejo de ClienteResponse (id = persona_rol.id del rol CLIENTE). */
    record FichaCliente(Long id, String tipoPersona, String tipoDocumento, String numeroDocumento,
                        String nombre, String telefono, String correo, String rubroComercial, String estado,
                        Boolean consentimientoContacto, Boolean consentimientoUsoDato,
                        LocalDateTime fechaCreacion) {
    }

    /**
     * Filtros de la bandeja. TODOS opcionales y aditivos: con los cuatro nulos,
     * {@code GET /clientes} responde exactamente lo que respondia antes de
     * existir este record (contrato congelado F3 §2).
     *
     * @param texto       contiene, sin distinguir mayusculas, en nombre o razon
     *                    social, numero de documento o rubro comercial
     * @param tipoPersona codigo exacto {@code N} o {@code J}
     * @param rubro       coincidencia EXACTA (viene del selector data-driven)
     * @param estado      codigo exacto {@code A} o {@code I}
     */
    record FiltrosCliente(String texto, String tipoPersona, String rubro, String estado,
                          int pagina, int tamano) {
    }

    /** KPI de la bandeja, sobre el mismo conjunto que la lista. */
    record ResumenClientes(long total, long activos, long inactivos, long contactoAutorizado,
                           long usoDatoAutorizado, java.util.List<String> rubros) {
    }

    Pagina<FichaCliente> listar(int pagina, int tamano, Actor actor);

    /** Misma lista, con los filtros aditivos resueltos en SQL. */
    Pagina<FichaCliente> listar(FiltrosCliente filtros, Actor actor);

    /**
     * Contadores del alcance con los mismos filtros que la lista, salvo el
     * estado — que es justamente uno de los cubos que devuelve.
     */
    ResumenClientes resumen(FiltrosCliente filtros, Actor actor);

    FichaCliente obtener(long id, Actor actor);

    FichaCliente registrar(DatosCliente datos, Actor actor);

    /** Solo toca contacto, rubro, consentimientos y —si llega— el estado. NUNCA el documento. */
    FichaCliente actualizar(long id, DatosCliente datos, Actor actor);

    /** Baja LOGICA: la persona pasa a estado 'I'. false = no existe en el tenant. */
    boolean desactivar(long id, Actor actor);

    /**
     * Constancia de la autorizacion de datos (D-27) para la ficha. ADITIVO: no
     * existe en la v1. Mismo alcance que {@link #obtener}, porque es
     * informacion de la misma persona.
     * <p>
     * Devuelve el record de {@code service.soporte} y no uno propio para que
     * cliente y propietario respondan con <b>la misma forma</b>: es el mismo
     * hecho sobre la misma persona, y duplicar el tipo invita a que se separen.
     */
    Autorizaciones.Constancia autorizacion(long id, Actor actor);
}
