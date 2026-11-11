package com.controllocal.service.network;

import com.controllocal.persistence.repositorio.AvisosNetworkRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Documentos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Orquesta consultas DTO aisladas; ninguna TX padre retiene una conexión durante la revalidación. */
@Service
public class AvisosNetworkServiceImpl implements AvisosNetworkService {
    private final AvisosNetworkRepository avisos;
    private final Alcances alcances;
    private final ContextosService contextos;
    private final Documentos documentos;
    public AvisosNetworkServiceImpl(AvisosNetworkRepository avisos,Alcances alcances,ContextosService contextos,Documentos documentos) {
        this.avisos=avisos;this.alcances=alcances;this.contextos=contextos;this.documentos=documentos;
    }
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Pagina<Aviso> consultar(Integer pagina,Integer tamano,Actor actor,ContextoSesion sesion) {
        exigirSesion(actor,sesion);
        int numero=pagina==null?1:Math.max(1,pagina),cantidad=tamano==null?20:Math.max(1,Math.min(50,tamano));
        var inicial=alcances.de(actor);
        var filas=avisos.pagina(actor.idOrganizacion(),actor.idRolOperativo(),inicial.paramRoles(),cantidad,(long)(numero-1)*cantidad);
        if(filas==null)throw new IllegalStateException("La consulta de avisos no produjo una página.");
        List<Long> ids=filas.stream().map(AvisosNetworkRepository.Fila::getIdEvento).toList();
        if(ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())throw new IllegalStateException("La página contiene referencias de aviso inválidas.");
        var vigente=alcances.de(actor);
        var actuales=ids.isEmpty()?List.<AvisosNetworkRepository.Fila>of():avisos.revalidar(actor.idOrganizacion(),actor.idRolOperativo(),vigente.paramRoles(),ids);
        if(actuales==null)throw new IllegalStateException("No se recibió la revalidación de avisos.");
        Set<Long> solicitados=Set.copyOf(ids);
        if(actuales.stream().anyMatch(f->!solicitados.contains(f.getIdEvento())))throw new IllegalStateException("La revalidación incorporó un aviso no solicitado.");
        long total=avisos.contar(actor.idOrganizacion(),actor.idRolOperativo(),vigente.paramRoles());
        if(total<0)throw new IllegalStateException("El conteo de avisos es inválido.");
        exigirSesion(actor,sesion);
        return new Pagina<>(actuales.stream().map(this::aviso).toList(),total);
    }
    private void exigirSesion(Actor actor,ContextoSesion sesion) {
        Alcances.exigirOperador(actor);if(sesion==null)throw new AccesoNoAutorizadoException();
        try {
            if(!ContextoSesion.de(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor)).equals(sesion))throw new AccesoNoAutorizadoException();
        } catch(CredencialesInvalidasException e) {throw new AccesoNoAutorizadoException();}
    }
    private Aviso aviso(AvisosNetworkRepository.Fila f) {
        if(f.getIdEvento()==null || f.getIdEncargo()==null || f.getOcurridoEn()==null || f.getParticipacion()==null)
            throw new IllegalStateException("El aviso no conserva su identidad y contexto.");
        Set<String> causas=f.getCausas()==null?Set.of():Set.copyOf(documentos.comoLista(f.getCausas()));
        return new Aviso(f.getIdEvento(),f.getTipo(),f.getOcurridoEn(),f.getIdEncargo(),f.getCodigoEncargo(),
                f.getParticipacion(),f.getSolicitud(),f.getAccion(),f.getEstadoSolicitud(),f.getEstadoParticipacion(),f.getVersionProyeccion(),f.getMotivo(),causas);
    }
}
