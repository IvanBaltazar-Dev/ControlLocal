package com.controllocal.service.presupuesto;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** Reutiliza cuenta/SID/contexto C1; no concede finalidad de recurso o atribución financiera. */
@Component
public class ContextoPresupuestoB0 {
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final OportunidadComercialRepository operaciones;
    private final ContextosService contextos;
    public ContextoPresupuestoB0(CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,
            OportunidadComercialRepository operaciones,ContextosService contextos){
        this.cuentas=cuentas;this.miembros=miembros;this.operaciones=operaciones;this.contextos=contextos;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void iniciarConsumo(Actor a,ContextoSesion s){iniciar(a,s,false);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void iniciarArchivo(Actor a,ContextoSesion s){iniciar(a,s,true);}
    private void iniciar(Actor a,ContextoSesion s,boolean archivo){
        if(a==null||s==null||(!a.esAgente()&&!a.esBroker()&&!(archivo&&a.esTenantAdmin())))throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(a.idOrganizacion(),a.idPersona());if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        // La admisión de un archivo no cambia Cuenta/SID; consumo conserva su entrada anterior.
        (archivo?cuentas.bloquearLecturaContexto(ids.getFirst()):cuentas.bloquearCambioAcceso(ids.getFirst()))
                .orElseThrow(AccesoNoAutorizadoException::new);
        try{var actual=archivo?contextos.exigirContextoConLecturaProtegida(s.referenciaSesion(),s.revisionContexto(),a):
                contextos.exigirContexto(s.referenciaSesion(),s.revisionContexto(),a);
            if(!ContextoSesion.de(actual).equals(s))throw new AccesoNoAutorizadoException();}
        catch(CredencialesInvalidasException ex){throw new AccesoNoAutorizadoException();}
        LocalDate hoy=LocalDate.now(ZoneId.of("America/Lima"));
        boolean vigente=a.esTenantAdmin()?operaciones.contextoGobiernoParaMetadata(a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),hoy):
                operaciones.contextoOperativoActual(a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),hoy);
        if(!vigente)throw new AccesoNoAutorizadoException();
    }
}
