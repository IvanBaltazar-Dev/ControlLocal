package com.controllocal.service.publicacion;

import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comun.EstadosDominio.EstadoPublicacion;
import com.controllocal.service.PublicacionService;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** Sólo formato de la lectura ya autorizada, compartido por ficha, historial y comando. */
public final class ProyeccionPublicacion {
    private ProyeccionPublicacion() { }
    public static PublicacionService.FichaPublicacion ficha(Publicacion p,Captacion c,Long versionComposicion){
        if((c.getIdComposicionActual()==null)!=(versionComposicion==null))throw new IllegalStateException("La referencia y versión de composición deben conservarse juntas.");
        var externo=p.getEstadoExterno()==null?null:new PublicacionService.EstadoExterno(
                p.getEstadoExterno(),p.getFechaEstadoExterno().toInstant(),p.getProcedenciaEstadoExterno());
        return new PublicacionService.FichaPublicacion(p.getId(),p.getIdEncargo(),p.getCanal(),p.getTituloAnuncio(),
                p.getImportePublicado(),p.getMoneda(),c.operacion().nombreDelImporte(),p.getEstado(),
                EstadoPublicacion.desde(p.getEstado()).descripcion(),local(p.getFechaPublicacion()),
                local(p.getFechaBaja()),p.getUrlPublicacion(),p.getCodigoOrigen(),p.getVersionAnuncio(),
                c.getVersion(),c.getPropiedad().getVersionFisica(),externo,c.getIdComposicionActual(),versionComposicion);
    }
    private static LocalDateTime local(OffsetDateTime fecha){
        return fecha==null?null:fecha.atZoneSameInstant(ZoneId.of("America/Lima")).toLocalDateTime();
    }
}
