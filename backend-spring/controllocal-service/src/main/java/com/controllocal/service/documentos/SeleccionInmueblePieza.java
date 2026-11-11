package com.controllocal.service.documentos;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.service.Actor;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** RF080/CAT-E17: una ficha describe un recurso físico, sin convertirlo en todo el conjunto. */
@Component
public class SeleccionInmueblePieza {
    public record Seleccion(Propiedad propiedad, EncargoService.Composicion composicion, boolean pertenece) { }
    private final EncargoService encargos;
    private final AutoridadDePropiedad autoridad;

    public SeleccionInmueblePieza(EncargoService encargos, AutoridadDePropiedad autoridad) {
        this.encargos=encargos;
        this.autoridad=autoridad;
    }

    /** El llamante autoriza antes el Encargo; su composición no concede lectura sobre sus unidades. */
    public Seleccion elegir(Captacion encargo, Long propiedadFicha, Actor actor) {
        return seleccionar(encargo,propiedadFicha,actor,false);
    }

    /** Sólo para una pieza ya conservada: pertenencia actual y permiso físico son hechos distintos. */
    public Seleccion revisarHistorica(Captacion encargo,long propiedad,Actor actor) {
        return seleccionar(encargo,propiedad,actor,true);
    }

    /** El snapshot no concede lectura perpetua, aunque no contenga ninguna fotografía. */
    public Propiedad exigirLecturaHistorica(long organizacion,long propiedad,Actor actor) {
        if(organizacion!=actor.idOrganizacion()||(!actor.esAgente()&&!actor.esBroker()))throw new AccesoNoAutorizadoException();
        Propiedad actual=autoridad.buscarParaLectura(actor,propiedad).orElseThrow(AccesoNoAutorizadoException::new);
        if(!Objects.equals(actual.getId(),propiedad)||!Objects.equals(actual.getOrganizacionId(),organizacion))
            throw new AccesoNoAutorizadoException();
        return actual;
    }

    private Seleccion seleccionar(Captacion encargo,Long propiedadFicha,Actor actor,boolean historica) {
        if(!Objects.equals(encargo.getOrganizacionId(),actor.idOrganizacion()))throw new AccesoNoAutorizadoException();
        long id=propiedadFicha==null?encargo.getPropiedad().getId():propiedadFicha;
        EncargoService.Composicion composicion=null;
        boolean pertenece=Objects.equals(encargo.getPropiedad().getId(),id);
        if (encargo.getIdComposicionActual()!=null) {
            composicion=encargos.consultarComposicion(encargo.getId(),null,actor);
            if (composicion==null || !Objects.equals(encargo.getIdComposicionActual(),composicion.id())
                    || composicion.encargo()!=encargo.getId() || composicion.version()<1
                    // C2 registra versionContenido del Encargo al declarar y al activar una adenda.
                    // No es el CAS de fila ni un contador independiente de ComposicionEncargo.
                    || composicion.version()>encargo.getVersionContenido())
                throw new ConflictoException("La composición del Encargo cambió; consulta su objeto actual.");
            pertenece=composicion.unidades().stream().anyMatch(u->u.idPropiedad()==id);
        }
        // Elegir expresamente no permite incorporar otra ficha física a este Encargo, tampoco como preview.
        if(!historica&&propiedadFicha!=null&&!pertenece)throw new AccesoNoAutorizadoException();
        Propiedad propiedad=exigirLecturaHistorica(encargo.getOrganizacionId(),id,actor);
        return new Seleccion(propiedad,composicion,pertenece);
    }
}
