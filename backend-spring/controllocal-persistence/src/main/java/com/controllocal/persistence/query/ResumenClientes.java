package com.controllocal.persistence.query;

/**
 * KPI de la bandeja de clientes, calculados en PostgreSQL sobre el MISMO
 * conjunto que pagina la lista.
 *
 * <p>Lo que evita: contar sobre las filas que el cliente descargo. El Blazor
 * derivaba estos tres numeros de la cartera completa en memoria, asi que con
 * paginacion real habrian pasado a contar solo la pagina visible.
 */
public interface ResumenClientes {

    long getTotal();

    long getActivos();

    long getContactoAutorizado();

    long getUsoDatoAutorizado();
}
