package com.controllocal.service.actividad;

import com.controllocal.service.InteraccionService.*;
import java.time.*;
import java.util.List;

/** Fecha efectiva conocida en el calendario Core. Ninguna hora ni fecha se rellena. */
public final class MomentosDeComunicacion {
    private MomentosDeComunicacion(){ }
    public static LocalDate local(MomentoHecho m){
        if(m==null||m.fecha()==null)return null;
        return m.hora()!=null&&m.offsetConocido()!=null?OffsetDateTime.of(m.fecha(),m.hora(),m.offsetConocido()).atZoneSameInstant(ZoneId.of("America/Lima")).toLocalDate():m.fecha();
    }
    public static LocalDate efectiva(DatosInteraccion d){
        if(d.momento()!=null&&d.momento().fecha()!=null)return local(d.momento());
        var eventos=d.eventos()==null?List.<EventoDeclarado>of():d.eventos();
        if(eventos.isEmpty()||eventos.stream().anyMatch(e->e.momento()==null||e.momento().fecha()==null))return null;
        var fechas=eventos.stream().map(e->local(e.momento())).distinct().toList();return fechas.size()==1?fechas.getFirst():null;
    }
}
