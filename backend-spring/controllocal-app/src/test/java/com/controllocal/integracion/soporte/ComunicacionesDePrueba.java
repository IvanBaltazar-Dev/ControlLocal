package com.controllocal.integracion.soporte;

import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/** Payload explícito TEST para dos App; no resuelve personas, autoridad, estados ni efectos. */
public final class ComunicacionesDePrueba {
    private ComunicacionesDePrueba() { }

    public static DatosInteraccion llamadaSalienteSinRespuesta(long idCliente,String resumen) {
        var contacto=new ReferenciaContactoContextual(TipoContacto.CLIENTE,idCliente,null,null);
        // Fecha/hora pasadas explícitas del fixture, sin depender de la hora de ejecución.
        var momento=new MomentoHecho(OrigenesDePrueba.hoy().minusDays(1),LocalTime.of(9,0),ZoneOffset.ofHours(-5));
        return new DatosInteraccion(List.of(new VinculoActividad(TipoContexto.CLIENTE,idCliente,null,null)),
                new ParticipanteDeclarado(contacto,"CLIENTE",null,null,null),null,
                TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
                List.of(new EventoDeclarado("LLAMADA-SALIENTE-TEST",EventoComunicacion.LLAMADA_SALIENTE,momento)),
                ResultadoInteraccion.SIN_RESPUESTA,momento,resumen,List.of(),null,null,
                "Declaración explícita del hecho TEST",null);
    }
}
