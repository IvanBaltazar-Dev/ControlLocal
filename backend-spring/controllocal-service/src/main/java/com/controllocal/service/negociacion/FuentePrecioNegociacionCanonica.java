package com.controllocal.service.negociacion;

import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.Procedencia;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** D-N-1: mismo productor PrecioPropiedad; O no es precio pedido ni cambio de U/P. */
@Component
public class FuentePrecioNegociacionCanonica implements FuentePrecioNegociacion {
    private final PrecioPropiedadRepository precios;
    private final EncargoService encargos;
    private final AccesoEncargo acceso;
    public FuentePrecioNegociacionCanonica(PrecioPropiedadRepository precios,EncargoService encargos,AccesoEncargo acceso){
        this.precios=precios;this.encargos=encargos;this.acceso=acceso;
    }
    @Override @Transactional(readOnly=true)
    public ReferenciaDeLaOferta congelarReferencia(ObjetoExacto objeto,Dinero importe,Actor actor){
        var encargo=encargos.consultar(objeto.encargo(),actor);
        var precio=precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(encargo.id(),PrecioPropiedad.HITO_PUBLICADO)
            .or(()->precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(encargo.id(),PrecioPropiedad.HITO_AUTORIZADO));
        if(precio.isEmpty())return null;
        var p=precio.get();
        if(!Objects.equals(p.getOrganizacionId(),actor.idOrganizacion())||!Objects.equals(p.getIdCaptacion(),encargo.id())
                ||!Objects.equals(p.getOperacion(),OperacionInmobiliaria.desde(encargo.operacion()).codigo()))
            throw new IllegalStateException("La referencia de precio no corresponde a la serie autorizada.");
        return new ReferenciaDeLaOferta(p.getHito(),PrecioPropiedad.rotuloDelHito(p.getHito()),p.getId(),p.getMonto(),p.getMoneda());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void registrarHitoOfrecido(ObjetoExacto objeto,Dinero importe,long idOferta,LocalDate fecha,Procedencia p,Actor actor){
        var encargo=acceso.leer(objeto.encargo(),actor);
        acceso.exigirResponsable(encargo,actor);
        // El sujeto económico exacto permanece en la propuesta de idOferta; no se distribuye por unidades.
        precios.save(PrecioPropiedad.hito(actor.idOrganizacion(),encargo.getPropiedad().getId(),encargo.operacion(),
            PrecioPropiedad.HITO_OFERTADO,importe.moneda(),importe.importe(),fecha).delEncargo(encargo.getId()).deLaOferta(idOferta));
    }
}
