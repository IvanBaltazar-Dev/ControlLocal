package com.controllocal.integracion.soporte;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import java.util.UUID;

/**
 * Protocolo Core de revisión exacta en TEST: no fabrica pruebas de revisión,
 * no omite Actuaciones y no acredita un clic de navegador ni autenticación HTTP.
 * Cada consumidor aporta su actor y su sesión reales del escenario.
 */
public final class ActuacionesHumanasC4DePrueba {
    private final ActuacionesService actuaciones;
    private final Documentos documentos;

    public ActuacionesHumanasC4DePrueba(ActuacionesService actuaciones, Documentos documentos) {
        this.actuaciones = actuaciones;
        this.documentos = documentos;
    }

    /** Conserva el mismo evento/prueba para que el consumidor pueda probar un replay. */
    public record Confirmada<T>(Revision revision, Confirmacion confirmacion,
                                 Resultado resultado, T datos) { }

    public Confirmacion revisar(Propuesta propuesta, Actor actor, ContextoSesion sesion) {
        EvidenciaC4DePrueba.propuesta("REVISAR_"+propuesta.capacidad(),actor,propuesta);
        var revision = actuaciones.revisar(actor, propuesta.referencia(), sesion);
        return new Confirmacion(revision.propuesta().version(), revision.propuesta().huella(),
                revision.pruebaRevision(), "TEST_C4_" + UUID.randomUUID());
    }

    public <T> Confirmada<T> confirmar(Propuesta propuesta, Actor actor,
                                      ContextoSesion sesion, Class<T> tipo) {
        EvidenciaC4DePrueba.propuesta("CONFIRMAR_"+propuesta.capacidad(),actor,propuesta);
        var revision = actuaciones.revisar(actor, propuesta.referencia(), sesion);
        var confirmacion = new Confirmacion(revision.propuesta().version(),
                revision.propuesta().huella(), revision.pruebaRevision(),
                "TEST_C4_" + UUID.randomUUID());
        EvidenciaC4DePrueba.propuesta("CONFIRMACION_REVISADA_"+propuesta.capacidad(),actor,revision.propuesta());
        var resultado = actuaciones.confirmar(actor, propuesta.referencia(), sesion,
                confirmacion, Procedencia.deLaPantalla());
        EvidenciaC4DePrueba.estado(propuesta.capacidad(),actor,resultado.estado());
        if (!"EXITO".equals(resultado.estado()) || resultado.resultado() == null)
            throw new AssertionError("El acto TEST no fue confirmado: estado="
                    + resultado.estado() + ", motivo=" + resultado.motivo());
        T datos = documentos.comoObjeto(documentos.objetoCanonico(resultado.resultado()), tipo);
        EvidenciaC4DePrueba.resultado(propuesta.capacidad(),actor,datos);
        return new Confirmada<>(revision, confirmacion, resultado, datos);
    }
}
