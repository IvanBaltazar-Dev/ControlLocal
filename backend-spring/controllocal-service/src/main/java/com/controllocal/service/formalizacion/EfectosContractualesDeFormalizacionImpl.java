package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.Actor;
import com.controllocal.service.CierreVentaService;
import com.controllocal.service.ContratoService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.Procedencia;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Efectos RF059/060 sobre un hecho ya persistido en la misma transacción. */
@Component
public class EfectosContractualesDeFormalizacionImpl implements EfectosContractualesDeFormalizacion {
    private final HitosConfirmados fuentes;
    private final AccesoEncargo acceso;
    private final ContratoService alquileres;
    private final CierreVentaService ventas;
    public EfectosContractualesDeFormalizacionImpl(HitosConfirmados fuentes,AccesoEncargo acceso,
            ContratoService alquileres,CierreVentaService ventas) {
        this.fuentes=fuentes;this.acceso=acceso;this.alquileres=alquileres;this.ventas=ventas;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public VinculosContractuales aplicar(ContextoEconomico contexto,FuenteHitoConfirmado hito,
            Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia,
            FormalizacionService.AcuerdoFinalDocumentado acuerdoFinalRevisado,
            ContratoService.PreparacionContrato preparacionRevisada) {
        if(contexto==null||hito==null||!Objects.equals(contexto.referencia(),hito.contexto()))
            throw new ReglaNegocioException("El hito no identifica su contexto económico.");
        var inicio=acceso.iniciar(actor,sesion,contexto.encargo());
        var encargo=acceso.escribir(contexto.encargo(),actor,inicio);
        acceso.exigirDecision(encargo,actor);
        var actual=fuentes.consultarContexto(contexto.referencia(),actor);
        if(actual.organizacion()!=actor.idOrganizacion()||actual.encargo()!=contexto.encargo()
                ||actual.operacion()!=contexto.operacion()
                ||actual.hechosConfirmados().stream().noneMatch(hito::equals))
            throw new NoEncontradoException("Hito confirmado del contexto");
        var fuente=hito.fuenteEconomica()==null?null:
                fuentes.consultarFuente(contexto.referencia(),hito.fuenteEconomica(),actor);
        if(hito.tipo()==TipoHitoContractual.MINUTA) {
            if(actual.operacion()!=OperacionInmobiliaria.VENTA)
                throw new ReglaNegocioException("La minuta no corresponde a esta operación.");
            return new VinculosContractuales(actual.contratoAlquiler(),actual.contratoVenta());
        }
        if(hito.tipo()==TipoHitoContractual.ESCRITURA&&actual.operacion()==OperacionInmobiliaria.VENTA)
            return new VinculosContractuales(null,ventas.confirmarHito(actual,hito,fuente,acuerdoFinalRevisado,actor));
        if(hito.tipo()==TipoHitoContractual.CONTRATO_ALQUILER&&actual.operacion()==OperacionInmobiliaria.ALQUILER)
            return new VinculosContractuales(alquileres.confirmarHito(actual,hito,fuente,acuerdoFinalRevisado,actor,preparacionRevisada),null);
        throw new ReglaNegocioException("El hito no corresponde a la operación del expediente.");
    }
}
