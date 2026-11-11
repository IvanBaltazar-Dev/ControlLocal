package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultaDeComandoTest {
    private final ComandoIdempotenteRepository repo=mock(ComandoIdempotenteRepository.class);
    private final ComandosIdempotentes servicio=new ComandosIdempotentes(repo);
    private final Actor actor=new Actor(7,20,30,Actor.AGENTE);
    private ComandoIdempotente comando(String tipo,Long rol) {
        return ComandoIdempotente.de(7L,"clave",tipo,"huella","PROPIEDAD",5L,rol,"API",null,null,"{}");
    }
    @Test void consultaUnaVezLaClaveDelTenantSinEscribir() {
        var c=comando("REGISTRAR_PROPIEDAD",30L);
        when(repo.findByOrganizacionIdAndClaveIdempotencia(7,"clave")).thenReturn(Optional.of(c));
        assertSame(c,servicio.consultarResultado(actor," clave ","REGISTRAR_PROPIEDAD").orElseThrow());
        verify(repo).findByOrganizacionIdAndClaveIdempotencia(7,"clave");verifyNoMoreInteractions(repo);
    }
    @Test void otroActorNoObtieneElResultado() {
        when(repo.findByOrganizacionIdAndClaveIdempotencia(7,"clave"))
                .thenReturn(Optional.of(comando("REGISTRAR_PROPIEDAD",31L)));
        assertTrue(servicio.consultarResultado(actor,"clave","REGISTRAR_PROPIEDAD").isEmpty());
    }
    @Test void laClaveNoSustituyeElTipoDeComando() {
        when(repo.findByOrganizacionIdAndClaveIdempotencia(7,"clave"))
                .thenReturn(Optional.of(comando("EDITAR_PROPIEDAD",30L)));
        assertTrue(servicio.consultarResultado(actor,"clave","REGISTRAR_PROPIEDAD").isEmpty());
    }
    @Test void ausenciaNoSeRegistraComoRechazo() {
        when(repo.findByOrganizacionIdAndClaveIdempotencia(7,"clave")).thenReturn(Optional.empty());
        assertTrue(servicio.consultarResultado(actor,"clave","REGISTRAR_PROPIEDAD").isEmpty());
        verify(repo).findByOrganizacionIdAndClaveIdempotencia(7,"clave");verifyNoMoreInteractions(repo);
    }
    @Test void reintentarTampocoEntregaElResultadoDeOtroActor() {
        when(repo.findByOrganizacionIdAndClaveIdempotencia(7,"clave"))
                .thenReturn(Optional.of(comando("REGISTRAR_PROPIEDAD",31L)));
        assertThrows(com.controllocal.service.excepcion.ConflictoException.class,
                ()->servicio.buscar(actor,"clave","REGISTRAR_PROPIEDAD","huella"));
    }
    @ParameterizedTest @NullAndEmptySource void laConsultaRequiereClave(String clave) {
        assertThrows(ReglaNegocioException.class,()->servicio.consultarResultado(actor,clave,"REGISTRAR_PROPIEDAD"));
        verifyNoInteractions(repo);
    }
    @Test void claveExcesivaNoConsultaRepositorio() {
        assertThrows(ReglaNegocioException.class,()->servicio.consultarResultado(actor,"a".repeat(65),"REGISTRAR_PROPIEDAD"));
        verifyNoInteractions(repo);
    }
}
