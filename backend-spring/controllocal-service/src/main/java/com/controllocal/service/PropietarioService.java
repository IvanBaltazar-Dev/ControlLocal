package com.controllocal.service;

import com.controllocal.service.soporte.Autorizaciones;

import java.time.LocalDateTime;

/**
 * Casos de uso del propietario: el dueno del local. Los records espejan el
 * contrato CONGELADO (Dtos.PropietarioRequest/Response v1).
 *
 * <p><b>El propietario es un ROL de persona sin tabla de detalle</b>, el unico
 * de los cinco: documento, nombre, contacto y estado viven en {@code persona},
 * y en el cable {@code id} = {@code persona_rol.id} del rol PROPIETARIO — el
 * mismo id con el que {@code /locales} referencia a su dueno.
 *
 * <p>Alcance (§ del cable v1): <b>ADMIN y AGENTE ven el catalogo entero</b>; el
 * BROKER solo los duenos de locales que su equipo prospecta o capta, mas los de
 * las captaciones que el revisa. Es la misma forma que en clientes —el unico rol
 * acotado es el broker—, pero por un camino distinto: alli via oportunidades,
 * aqui via propiedades.
 *
 * <p>No hay baja fisica: {@code desactivar} deja la persona en estado {@code I}.
 */
public interface PropietarioService {

    /** Espejo de PropietarioRequest. Sin estado, el alta queda ACTIVA. */
    /**
     * @param consentimientoUsoDato en el ALTA es la casilla unica de
     *                              autorizacion (D-27), y <b>lo unico que el
     *                              usuario aporta</b>: sin ella no hay alta.
     *                              Canal, fecha, actor, tenant y version del
     *                              aviso los pone el backend.
     */
    record DatosPropietario(String tipoPersona, String tipoDocumento, String numeroDocumento,
                            String nombre, String telefono, String correo,
                            Boolean consentimientoUsoDato, String estado) {
    }

    /**
     * Espejo de PropietarioResponse. {@code cantidadLocales} es DERIVADO y
     * depende de quien pregunta: son los locales del propietario en seguimiento
     * dentro del alcance del actor, contados sin duplicar los que tienen
     * captacion y prospeccion a la vez.
     */
    record FichaPropietario(Long id, String tipoPersona, String tipoDocumento, String numeroDocumento,
                            String nombre, String telefono, String correo, String estado,
                            Boolean consentimientoUsoDato, LocalDateTime fechaCreacion,
                            int cantidadLocales) {
    }

    /**
     * Filtros ADITIVOS del catálogo. Omitidos, la respuesta es la del cable
     * congelado.
     */
    record FiltrosPropietario(String texto, String estado, int pagina, int tamano) {
    }

    /** Cubos del catálogo, contados en la base sobre el mismo conjunto. */
    record ResumenPropietarios(long total, long activos, long inactivos) {
    }

    Pagina<FichaPropietario> listar(int pagina, int tamano, Actor actor);

    Pagina<FichaPropietario> listar(FiltrosPropietario filtros, Actor actor);

    ResumenPropietarios resumen(FiltrosPropietario filtros, Actor actor);

    FichaPropietario obtener(long id, Actor actor);

    FichaPropietario registrar(DatosPropietario datos, Actor actor);

    FichaPropietario actualizar(long id, DatosPropietario datos, Actor actor);

    /** Baja logica: la persona pasa a INACTIVA. {@code false} = no existe. */
    boolean desactivar(long id, Actor actor);

    /**
     * Constancia de la autorizacion de datos (D-27) para la ficha. ADITIVO, con
     * el mismo alcance que {@link #obtener} y la misma forma que la de cliente:
     * es el mismo hecho sobre la misma persona.
     */
    Autorizaciones.Constancia autorizacion(long id, Actor actor);
}
