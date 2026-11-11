package com.controllocal.service.actividad;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.Procedencia;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Almacenamiento común de los cuatro efectos C4 revisados; no ejecuta negocio ni concede acceso. */
@Component
public class RecibosDeActividad {
    public static final String ABRIR="ABRIR_OPORTUNIDAD",RECTIFICAR="RECTIFICAR_INTERACCION",
            NO_CONTINUAR="REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD",CANCELAR="CANCELAR_VISITA";
    private static final Set<String> TIPOS=Set.of(ABRIR,RECTIFICAR,NO_CONTINUAR,CANCELAR);
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository registros;
    private final EventoDominioRepository eventos;
    private final Documentos documentos;
    public RecibosDeActividad(ComandosIdempotentes comandos,ComandoIdempotenteRepository registros,
            EventoDominioRepository eventos,Documentos documentos){
        this.comandos=comandos;this.registros=registros;this.eventos=eventos;this.documentos=documentos;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<String> buscarOriginal(Actor actor,String clave,String tipo,Object datos,Procedencia procedencia){
        exigirTipo(tipo);clave=Idempotencia.exigirClave(clave);
        String h=Idempotencia.huellaExacta("BROX-COMANDO"+(char)0+actor.idOrganizacion()+(char)0+clave);
        registros.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));
        try {
            return comandos.buscar(actor,clave,tipo,huella(datos,procedencia)).map(c->c.getResultado());
        } catch(com.controllocal.service.excepcion.ReglaNegocioException conflictoDeContenido) {
            throw new com.controllocal.service.excepcion.ConflictoException("La identidad de la actuación no admite otro contenido.");
        }
    }
    public Optional<String> consultar(Actor actor,String clave,String tipo){
        exigirTipo(tipo);return comandos.consultarResultado(actor,clave,tipo).map(c->c.getResultado());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void registrar(Actor actor,String clave,String tipo,Object datos,Procedencia procedencia,
            String entidad,long id,Object resultado){
        exigirTipo(tipo);
        var contenido=documentos.comoMapa(escribir(resultado));
        if(!(contenido.get("version") instanceof Number version)||version.longValue()<0)
            throw new IllegalStateException("El resultado C4 debe identificar su versión conservada.");
        var referencia=procedencia.sinPeticion();
        eventos.saveAndFlush(referencia.sellar(EventoDominio.de(actor.idOrganizacion(),tipo,entidad,id,
                actor.idRolOperativo(),procedencia.canal())).con(escribir(Map.of(
                        "version",version.longValue(),"claveComando",clave,
                        "huellaResultado",Idempotencia.huellaExacta(escribir(resultado))))));
        comandos.registrar(actor,clave,tipo,huella(datos,procedencia),entidad,id,procedencia,escribir(resultado));
    }
    public String escribir(Object valor){return documentos.objetoCanonico(valor);}
    public <T>T leer(String json,Class<T> tipo){return documentos.comoObjeto(json,tipo);}
    private String huella(Object datos,Procedencia procedencia){return Idempotencia.huellaExacta(escribir(Map.of("datos",datos,"procedencia",procedencia)));}
    private static void exigirTipo(String tipo){if(!TIPOS.contains(tipo))throw new IllegalArgumentException("El recibo no corresponde a un caso revisado C4.");}
}
