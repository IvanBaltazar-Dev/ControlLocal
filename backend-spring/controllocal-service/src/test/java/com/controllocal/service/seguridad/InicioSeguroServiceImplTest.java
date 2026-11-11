package com.controllocal.service.seguridad;

import com.controllocal.domain.seguridad.VinculoInteraccionSegura;
import com.controllocal.persistence.repositorio.VinculoInteraccionSeguraRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.seguridad.InicioSeguroService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InicioSeguroServiceImplTest {
    private static final Instant T0 = Instant.parse("2026-10-27T12:00:00Z");
    private static final Actor AGENTE = new Actor(1, 2, 3, Actor.AGENTE);
    private static final String SID = "s".repeat(43);
    private static final String CONTEXTO = ContextoSesion.de(core(AGENTE,SID,1,T0.plusSeconds(2),T0.plusSeconds(1200))).identidad();
    private final VinculoInteraccionSeguraRepository repo = mock(VinculoInteraccionSeguraRepository.class);
    private final ContextosService contextos = mock(ContextosService.class);
    private final Map<String, VinculoInteraccionSegura> filas = new HashMap<>();
    private final Reloj reloj = new Reloj(T0);
    private InicioSeguroServiceImpl servicio;

    @BeforeEach void preparar() {
        AtomicInteger correlativo = new AtomicInteger();
        servicio = new InicioSeguroServiceImpl(repo, contextos, reloj,
                () -> String.valueOf((char) ('A' + correlativo.getAndIncrement())).repeat(43));
        when(repo.save(any())).thenAnswer(inv -> {
            VinculoInteraccionSegura fila = inv.getArgument(0);
            filas.put(fila.getHashFlujo(), fila);
            return fila;
        });
        when(repo.bloquearPorHuella(anyString())).thenAnswer(inv -> Optional.ofNullable(filas.get(inv.getArgument(0))));
        when(contextos.exigirContexto(anyString(),anyLong(),any())).thenAnswer(inv ->
                core(inv.getArgument(2),inv.getArgument(0),inv.getArgument(1),T0.plusSeconds(2),T0.plusSeconds(1200)));
    }

    private Vinculo iniciar() { return servicio.iniciar(Proposito.ACCESO, T0.plusSeconds(1800)); }
    private SesionVerificada sesion() {
        return verificada(AGENTE,SID,1,T0.plusSeconds(2),T0.plusSeconds(1200));
    }
    private static ContextosService.SesionAccesoDTO core(Actor a,String sid,long revision,Instant iat,Instant exp) {
        return new ContextosService.SesionAccesoDTO(sid,revision,
                new ContextosService.IdentidadAutenticada(1000,"persona","persona@cuentas.example.test","Persona"),
                new ContextosService.Contexto("opaco",a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),"EMPRESARIAL","Agencia"),
                iat.truncatedTo(java.time.temporal.ChronoUnit.SECONDS),exp.truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                EstadoDeAcceso.SIN_RESTRICCIONES);
    }
    private static SesionVerificada verificada(Actor a,String sid,long revision,Instant iat,Instant exp) {
        return new SesionVerificada(a,ContextoSesion.de(core(a,sid,revision,iat,exp)).identidad(),iat,exp,sid,revision);
    }
    private void autorizar(SesionVerificada s) {
        when(contextos.exigirContexto(s.referenciaSesion(),s.revisionContexto(),s.actor()))
                .thenReturn(core(s.actor(),s.referenciaSesion(),s.revisionContexto(),s.emitidaEn(),s.expiraEn()));
    }
    private RetornoPreparado completar(Vinculo vinculo) {
        reloj.ahora = T0.plusSeconds(10);
        return servicio.completar(vinculo.flujoRef(), Proposito.ACCESO, sesion());
    }

    @Test void laSesionSeCanonicalizaALaPrecisionExactaDeLosClaimsJwt() {
        var sesion = verificada(AGENTE,SID,1,T0.plusSeconds(2).plusNanos(123456789),T0.plusSeconds(1200).plusNanos(987654321));
        assertEquals(T0.plusSeconds(2), sesion.emitidaEn());
        assertEquals(T0.plusSeconds(1200), sesion.expiraEn());
        var vinculo = iniciar();
        reloj.ahora = T0.plusSeconds(10);
        var retorno = servicio.completar(vinculo.flujoRef(), Proposito.ACCESO, sesion);
        assertEquals(sesion.expiraEn(), filas.values().iterator().next().getSesionExpiraEn());
        assertEquals("CONFIRMADO", servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion).estado());
    }

    @Test void naceSinPersonaNiTenantYNuncaPersisteLaReferenciaEnClaro() {
        var respuesta = iniciar();
        var fila = filas.values().iterator().next();
        assertNotEquals(respuesta.flujoRef(), fila.getHashFlujo());
        assertEquals(64, fila.getHashFlujo().length());
        assertNull(fila.getOrganizacionId());
        assertNull(fila.getIdPersona());
        assertNull(fila.getContextoSesion());
        assertNull(fila.getHashRetorno());
        verifyNoInteractions(contextos);
    }

    @Test void autenticarLigaActorYContextoYAcortaVigenciaSinExtenderla() {
        var vinculo = iniciar();
        var retorno = completar(vinculo);
        var fila = filas.values().iterator().next();
        assertEquals(AGENTE.idOrganizacion(), fila.getOrganizacionId());
        assertEquals(AGENTE.idPersona(), fila.getIdPersona());
        assertEquals(AGENTE.idRolOperativo(), fila.getIdRolOperativo());
        assertEquals(CONTEXTO, fila.getContextoSesion());
        assertEquals(sesion().expiraEn(), retorno.venceEn());
        assertNotEquals(retorno.retornoRef(), fila.getHashRetorno());
    }

    @Test void retornoExactoSeConsumeUnaSolaVez() {
        var vinculo = iniciar();
        var retorno = completar(vinculo);
        assertEquals("CONFIRMADO", servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), sesion()).estado());
        assertNotNull(filas.values().iterator().next().getUsadoEn());
        assertThrows(ConflictoException.class, () -> servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), sesion()));
    }

    @Test void completarNoSeRepiteNiRegeneraUnaPruebaPerdida() {
        var vinculo = iniciar();
        completar(vinculo);
        assertThrows(ConflictoException.class, () -> servicio.completar(vinculo.flujoRef(), Proposito.ACCESO, sesion()));
    }

    @Test void unaSesionDeUnSegundoAnteriorNoAcreditaEsteInicio() {
        var vinculo = iniciar();
        reloj.ahora = T0.plusSeconds(10);
        for (Instant emitida : List.of(T0.minusSeconds(1), T0.minusSeconds(2))) {
            var anterior = verificada(AGENTE,SID,1,emitida,T0.plusSeconds(1200));
            autorizar(anterior);
            assertThrows(ConflictoException.class, () -> servicio.completar(vinculo.flujoRef(), Proposito.ACCESO, anterior));
        }
        assertNull(filas.values().iterator().next().getCompletadoEn());
    }

    @Test void loginDelMismoSegundoSeLigaSinIntroducirUnaEsperaArtificial() {
        reloj.ahora = T0.plusNanos(500000000);
        var vinculo = iniciar();
        reloj.ahora = T0.plusNanos(800000000);
        var rapida = verificada(AGENTE,SID,1,T0,T0.plusSeconds(600));
        autorizar(rapida);
        var retorno = servicio.completar(vinculo.flujoRef(), Proposito.ACCESO, rapida);
        assertEquals("CONFIRMADO", servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), rapida).estado());
    }

    @Test void noSeRetornaAntesDeCompletarPorLaAutoridadDeAcceso() {
        var vinculo = iniciar();
        reloj.ahora = T0.plusSeconds(10);
        assertThrows(ConflictoException.class, () -> servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                "B".repeat(43), sesion()));
    }

    static Stream<SesionVerificada> contextosAjenos() {
        return Stream.of(
            verificada(new Actor(99,2,3,Actor.AGENTE),SID,1,T0.plusSeconds(2),T0.plusSeconds(1200)),
            verificada(new Actor(1,99,3,Actor.AGENTE),SID,1,T0.plusSeconds(2),T0.plusSeconds(1200)),
            verificada(new Actor(1,2,99,Actor.AGENTE),SID,1,T0.plusSeconds(2),T0.plusSeconds(1200)),
            verificada(new Actor(1,2,3,Actor.BROKER),SID,1,T0.plusSeconds(2),T0.plusSeconds(1200)),
            verificada(AGENTE,SID,1,T0.plusSeconds(3),T0.plusSeconds(1200)),
            verificada(AGENTE,SID,1,T0.plusSeconds(2),T0.plusSeconds(1201)),
            verificada(AGENTE,"t".repeat(43),1,T0.plusSeconds(2),T0.plusSeconds(1200)),
            verificada(AGENTE,SID,2,T0.plusSeconds(2),T0.plusSeconds(1200)));
    }

    @ParameterizedTest @MethodSource("contextosAjenos")
    void actorEspacioAtribucionOSesionDistintosNoReutilizanRetorno(SesionVerificada ajena) {
        var vinculo = iniciar();
        var retorno = completar(vinculo);
        autorizar(ajena);
        assertThrows(ConflictoException.class, () -> servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), ajena));
        assertNull(filas.values().iterator().next().getUsadoEn());
        autorizar(sesion());
        assertEquals("CONFIRMADO", servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), sesion()).estado());
    }

    @ParameterizedTest @ValueSource(strings = {"", "correcto", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB", " ", "true"})
    void textoLibreOReferenciaAlteradaNoPruebaRetorno(String falsa) {
        var vinculo = iniciar();
        completar(vinculo);
        assertThrows(ConflictoException.class, () -> servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                falsa, sesion()));
        assertNull(filas.values().iterator().next().getUsadoEn());
    }

    @Test void referenciaDeOtroFlujoNoSatisfaceLaCorrelacion() {
        var primero = iniciar();
        var segundo = iniciar();
        var retorno = completar(primero);
        completar(segundo);
        assertThrows(ConflictoException.class, () -> servicio.consumir(segundo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), sesion()));
    }

    @Test void propositoAusenteOAlteradoNoEsUnAccesoReconocido() {
        var vinculo = iniciar();
        var retorno = completar(vinculo);
        assertThrows(ConflictoException.class, () -> servicio.consumir(vinculo.flujoRef(), null,
                retorno.retornoRef(), sesion()));
        filas.values().iterator().next().setProposito("OTRO");
        assertThrows(ConflictoException.class, () -> servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), sesion()));
    }

    @Test void igualdadConElVencimientoYaRechazaSinConsumir() {
        var vinculo = iniciar();
        var retorno = completar(vinculo);
        reloj.ahora = retorno.venceEn();
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), sesion()));
        assertNull(filas.values().iterator().next().getUsadoEn());
    }

    @ParameterizedTest @ValueSource(strings={"REVOCADA","CONTRASENA","MFA","ATRIBUCION"})
    void revalidaRevocacionContrasenaMfaYAtribucionAntesDeEntregar(String causa) {
        var vinculo = iniciar();
        var retorno = completar(vinculo);
        RuntimeException rechazo=causa.equals("REVOCADA")?new CredencialesInvalidasException():new AccesoNoAutorizadoException();
        when(contextos.exigirContexto(SID,1,AGENTE)).thenThrow(rechazo);
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.consumir(vinculo.flujoRef(), Proposito.ACCESO,
                retorno.retornoRef(), sesion()));
        assertNull(filas.values().iterator().next().getUsadoEn());
    }

    @Test void accesoLimitadoNoPuedeCompletarElFlujo() {
        var vinculo = iniciar();
        reloj.ahora = T0.plusSeconds(10);
        when(contextos.exigirContexto(SID,1,AGENTE)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.completar(vinculo.flujoRef(), Proposito.ACCESO, sesion()));
        assertNull(filas.values().iterator().next().getCompletadoEn());
    }

    @Test void datosDeSesionInvalidosNoConsultanLaCorrelacion() {
        var vinculo = iniciar();
        reloj.ahora = T0.plusSeconds(10);
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.completar(vinculo.flujoRef(), Proposito.ACCESO,
                new SesionVerificada(AGENTE,"texto de chat",T0.plusSeconds(2),T0.plusSeconds(1200),SID,1)));
        verify(repo, never()).bloquearPorHuella(anyString());
    }

    @Test void mismaPersonaEnOtraSesionNoPuedeCopiarLaHuellaDeLaSesionAnterior() {
        var vinculo=iniciar();var retorno=completar(vinculo);
        var ajena=new SesionVerificada(AGENTE,CONTEXTO,T0.plusSeconds(2),T0.plusSeconds(1200),"t".repeat(43),1);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consumir(vinculo.flujoRef(),Proposito.ACCESO,retorno.retornoRef(),ajena));
        assertNull(filas.values().iterator().next().getUsadoEn());
        assertEquals("CONFIRMADO",servicio.consumir(vinculo.flujoRef(),Proposito.ACCESO,retorno.retornoRef(),sesion()).estado());
    }

    @Test void revisionNuevaDelMismoSegundoNoPuedeCopiarLaHuellaDeLaAnterior() {
        var vinculo=iniciar();var retorno=completar(vinculo);
        var otra=new SesionVerificada(AGENTE,CONTEXTO,T0.plusSeconds(2),T0.plusSeconds(1200),SID,2);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consumir(vinculo.flujoRef(),Proposito.ACCESO,retorno.retornoRef(),otra));
        assertNull(filas.values().iterator().next().getUsadoEn());
    }

    @Test void sesionExpiradaNoConsultaLaCorrelacionNiElContexto() {
        var vinculo=iniciar();reloj.ahora=T0.plusSeconds(1200);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.completar(vinculo.flujoRef(),Proposito.ACCESO,sesion()));
        verify(repo,never()).bloquearPorHuella(anyString());verifyNoInteractions(contextos);
    }

    private static final class Reloj extends Clock {
        private Instant ahora;
        private Reloj(Instant ahora) { this.ahora = ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }
}
