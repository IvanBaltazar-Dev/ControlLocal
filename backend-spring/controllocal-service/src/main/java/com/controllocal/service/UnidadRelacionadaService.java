package com.controllocal.service;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.Procedencia;

/**
 * Relaciones físicas autorizadas. RF014 añade origen matriz→unidad explícito;
 * las asociaciones A/I/C conservan su significado y su lectura histórica.
 */
public interface UnidadRelacionadaService {

    /**
     * Destinos de todos los vinculos historicos de una propiedad, en orden
     * estable. La respuesta conserva tambien los vinculos cerrados.
     */
    List<FichaUnidadRelacionada> deLaPropiedad(long idPropiedad, Actor actor);

    /** Origen O, sin conceder lectura del otro extremo. Corte nulo conserva toda su historia. */
    List<FichaOrigen> consultarOrigen(long idPropiedad, Instant corte, Actor actor);

    /** Lectura para capacidad/revisión humana: mismas guardas físicas sobre ambos extremos. */
    ContextoOrigen capacidadSobreOrigen(long idMatriz, long idUnidad, Actor actor);

    /** Efecto de la confirmación exacta FISICO; no es un transporte alternativo de aprobación. */
    ResultadoOrigen registrarOrigen(DatosOrigen datos, Actor actor, ContextoSesion sesion,
                                     String claveIdempotencia, Procedencia procedencia);

    /** Conciliación del recibo original sin reemitir ni exigir edición; reautoriza ambas lecturas. */
    Optional<ResultadoOrigen> consultarResultadoOrigen(String claveIdempotencia, Actor actor);

    record DatosOrigen(Long idMatriz, Long idUnidad, LocalDate vigenteDesde, LocalDate vigenteHasta,
                       String situacionRegistral, String fuente,
                       Long versionMatrizEsperada, Long versionUnidadEsperada) { }

    record ContextoOrigen(long idMatriz, long idUnidad, long versionMatriz, long versionUnidad) { }

    record ResultadoOrigen(Long idRelacion, Long idMatriz, Long idUnidad, String relacion,
                           LocalDate vigenteDesde, LocalDate vigenteHasta, String situacionRegistral,
                           EstadoDato estadoSituacion, String fuente, long versionMatriz, long versionUnidad,
                           Instant registradoEn, Long idEvento) { }

    record FichaOrigen(Long idRelacion, Long idMatriz, Long idUnidad, String relacion,
                       LocalDate vigenteDesde, LocalDate vigenteHasta, String situacionRegistral,
                       EstadoDato estadoSituacion, String fuente, Long versionMatrizObservada,
                       Long versionUnidadObservada, Instant registradoEn) { }

    record FichaUnidadRelacionada(Long idRelacion, Long idPropiedad, String codigo,
                                  String tipoPropiedad, String tipoRotulo,
                                  String tipoRelacion, String tipoRelacionRotulo,
                                  String partidaRegistral, LocalDate vigenteDesde,
                                  LocalDate vigenteHasta) {
    }
}
