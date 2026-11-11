package com.controllocal.web.dto;

import com.controllocal.service.PublicacionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Proyección autorizada del anuncio; no selector genérico de estado ni publicación implícita. */
public final class PublicacionDtos {
    private PublicacionDtos() { }
    public record PublicacionResponse(Long id,Long idEncargo,String canal,String tituloAnuncio,
            BigDecimal importePublicado,String moneda,String importeRotulo,String estado,String estadoRotulo,
            LocalDateTime fechaPublicacion,LocalDateTime fechaBaja,String urlPublicacion,String codigoOrigen,
            long version,long versionEncargo,long versionFisica,PublicacionService.EstadoExterno estadoExterno,
            Long composicion,Long versionComposicion){
        public static PublicacionResponse desde(PublicacionService.FichaPublicacion f){return new PublicacionResponse(
                f.id(),f.idEncargo(),f.canal(),f.tituloAnuncio(),f.importePublicado(),f.moneda(),f.importeRotulo(),f.estado(),
                f.estadoRotulo(),f.fechaPublicacion(),f.fechaBaja(),f.urlPublicacion(),f.codigoOrigen(),f.version(),f.versionEncargo(),f.versionFisica(),f.estadoExterno(),f.composicion(),f.versionComposicion());}
    }
}
