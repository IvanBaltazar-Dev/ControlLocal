package com.controllocal.service.publicacion;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Un escritor de versión/hito/outbox, incluido el cierre derivado del hecho contractual C5. */
@Component
public class HistoriadorPublicacion {
    private final PublicacionRepository anuncios;
    private final HistoriaPublicacionRepository historia;
    private final PrecioPropiedadRepository precios;
    private final EventoDominioRepository eventos;
    private final Documentos documentos;
    private final ContextosDeActividad reloj;
    public HistoriadorPublicacion(PublicacionRepository anuncios,HistoriaPublicacionRepository historia,
            PrecioPropiedadRepository precios,EventoDominioRepository eventos,Documentos documentos,
            ContextosDeActividad reloj){this.anuncios=anuncios;this.historia=historia;this.precios=precios;
        this.eventos=eventos;this.documentos=documentos;this.reloj=reloj;}

    @Transactional(propagation=Propagation.MANDATORY)
    public PublicacionService.FichaPublicacion guardar(Publicacion p,Captacion c,Actor actor,String accion,
            Procedencia origen,PublicacionService.EvaluacionPublicabilidad evaluacion,boolean nuevoPrecio,String motivo){
        if(!Objects.equals(p.getOrganizacionId(),actor.idOrganizacion())
                ||!Objects.equals(c.getOrganizacionId(),actor.idOrganizacion())
                ||!Objects.equals(p.getIdEncargo(),c.getId())
                ||!Objects.equals(p.getIdPropiedad(),c.getPropiedad().getId()))
            throw new IllegalStateException("Una publicación no puede cambiar de Encargo u organización.");
        anuncios.saveAndFlush(p);
        var fecha=reloj.ahora();var ficha=ProyeccionPublicacion.ficha(p,c,historia.versionComposicion(actor.idOrganizacion(),c.getIdComposicionActual()));
        Long precioRegistrado=null;
        // El total anunciado de una composición se conserva en su versión, no como precio de una unidad.
        if(nuevoPrecio&&Publicacion.ESTADO_PUBLICADO.equals(p.getEstado())&&c.getIdComposicionActual()==null){
            PrecioPropiedad precio=new PrecioPropiedad();precio.setOrganizacionId(actor.idOrganizacion());
            precio.setIdPropiedad(p.getIdPropiedad());precio.setOperacion(c.operacion());
            precio.setHito(PrecioPropiedad.HITO_PUBLICADO);precio.setMonto(p.getImportePublicado());
            precio.setMoneda(p.getMoneda());precio.setFecha(reloj.hoy());
            var guardado=precios.save(precio.delEncargo(c.getId()));
            if(guardado==null||guardado.getId()==null)throw new IllegalStateException("No se confirmó la identidad del hito de precio publicado.");
            precioRegistrado=guardado.getId();
        }
        var contenido=documentos.comoMapa(documentos.objetoCanonico(ficha));
        contenido.put("precioRegistradoEnEstaVersion",precioRegistrado);
        historia.guardar(actor.idOrganizacion(),p.getId(),p.getVersionAnuncio(),actor.idRolOperativo(),accion,
                fecha,documentos.objetoCanonico(contenido),documentos.objetoCanonico(origen.sinPeticion()),
                evaluacion==null?null:documentos.objetoCanonico(evaluacion),motivo);
        var carga=new LinkedHashMap<String,Object>();carga.put("encargo",c.getId());carga.put("publicacion",p.getId());
        carga.put("version",p.getVersionAnuncio());carga.put("actuacion",accion);carga.put("estadoLocal",p.getEstado());
        carga.put("precioRegistradoEnEstaVersion",precioRegistrado);
        eventos.save(origen.sinPeticion().sellar(EventoDominio.de(actor.idOrganizacion(),"PUBLICACION_VERSIONADA",
                "PUBLICACION",p.getId(),actor.idRolOperativo(),origen.canal()))
                .con(documentos.objetoCanonico(carga)));
        return ficha;
    }

    /** Sólo consecuencia del hito confirmado; el llamador C5 conserva su autoridad de decisión. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void cerrarPorHito(Captacion c,Actor actor){
        for(var lectura:anuncios.deEncargo(actor.idOrganizacion(),c.getId())){
            var p=anuncios.bloquear(actor.idOrganizacion(),c.getId(),lectura.getId()).orElseThrow();
            if(Publicacion.ESTADO_CERRADO.equals(p.getEstado()))continue;
            p.setEstado(Publicacion.ESTADO_CERRADO);p.setFechaBaja(reloj.ahora().atOffset(ZoneOffset.UTC));
            p.setVersionAnuncio(Math.addExact(p.getVersionAnuncio(),1));
            guardar(p,c,actor,"CIERRE_POR_HITO_CONFIRMADO",Procedencia.delSistema(),null,false,"Consecuencia del hito contractual confirmado.");
        }
    }
}
