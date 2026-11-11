package com.controllocal.service.negociacion;

import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService.ObjetoExacto;
import com.controllocal.service.OfertaService.Dinero;
import com.controllocal.service.OfertaService.ReferenciaDeLaOferta;
import com.controllocal.service.soporte.Procedencia;
import java.time.LocalDate;

/** Adaptador del productor canónico P/U/O, no un segundo motor de precios. */
public interface FuentePrecioNegociacion {
    ReferenciaDeLaOferta congelarReferencia(ObjetoExacto objeto, Dinero importe, Actor actor);
    void registrarHitoOfrecido(ObjetoExacto objeto, Dinero importe, long idOferta,
                              LocalDate fechaHecho, Procedencia procedencia, Actor actor);
}
