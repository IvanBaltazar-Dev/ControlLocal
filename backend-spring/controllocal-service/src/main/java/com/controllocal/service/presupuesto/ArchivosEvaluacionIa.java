package com.controllocal.service.presupuesto;

import com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma;
import com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion;
import java.util.*;

/** Puerto interno de custodia. No concede descarga, autoridad QA ni transferencia externa. */
public interface ArchivosEvaluacionIa {
    void liberarSalidaSinBinario(ReferenciaEvaluacion ref);
    /** Original registrado del caso; el consumidor conserva su minimización y puerta de transferencia. */
    byte[] leerOriginalParaProcesar(UUID caso,SesionPlataforma sesion);
    /** Respuesta del claim ya causado: mismo journal/cuota y sin otra inferencia. */
    UUID recibir(ReferenciaEvaluacion ref,UUID archivo,String mime,byte[] contenido);
    /** Recupera sólo el mismo intento. Vacío significa que no existe escritura iniciada, nunca permiso para otro POST. */
    Optional<byte[]> recuperarResultadoParaProcesar(ReferenciaEvaluacion ref,UUID archivo,String mime,String huella,
                                                   SesionPlataforma sesion);
}
