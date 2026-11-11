package com.controllocal.service.documentos;
import com.controllocal.service.Actor;
import com.controllocal.service.ReportePropietarioService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.DocumentosEncargo.Contenido;
import java.util.UUID;

/** Puerto de composición/I-O del reporte. Las decisiones y los campos proceden de Core. */
public interface DocumentosReporteAvance {
    record Origen(UUID operacion,Referencia reporte,UUID archivo,long encargo,String huellaOrigen,Actor actor,ContextoSesion sesion) { }
    com.controllocal.service.encargo.DocumentosEncargo.Guardado generar(Origen origen,Contenido contenido);
}
