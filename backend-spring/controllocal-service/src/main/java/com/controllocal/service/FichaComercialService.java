package com.controllocal.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Lectura transversal congelada de la ficha comercial de personas. */
public interface FichaComercialService {

    int TAMANO_POR_DEFECTO = 8;

    FichaCliente fichaCliente(long idCliente, int tamano, Actor actor);

    FichaPropietario fichaPropietario(long idPropietario, int tamano, Actor actor);

    SeccionFicha seccionCliente(long idCliente, String seccion, int pagina, int tamano, Actor actor);

    SeccionFicha seccionPropietario(long idPropietario, String seccion, int pagina, int tamano, Actor actor);

    record FichaCliente(
            ClienteService.FichaCliente cliente,
            boolean requerimientoActivo,
            String ctaRuta,
            Map<String, SeccionFicha> sections) {
    }

    record FichaPropietario(
            PropietarioService.FichaPropietario propietario,
            Map<String, SeccionFicha> sections) {
    }

    record SeccionFicha(
            String section,
            long totalRecords,
            int page,
            int pageSize,
            List<FilaFicha> items) {
    }

    record FilaFicha(
            String id,
            String codigo,
            String proceso,
            String titulo,
            String subtitulo,
            String local,
            String distrito,
            String cliente,
            Long clienteId,
            String propietario,
            Long propietarioId,
            String agente,
            String estado,
            String fecha,
            String ruta,
            String icono,
            String tono,
            LocalDateTime fechaOrden) {
    }
}
