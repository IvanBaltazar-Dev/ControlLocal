package com.controllocal.web.seguridad;

import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** JWT v2 transporta una sesión durable Core; no crea atribuciones. */
class TokenServiceTest {
    private final TokenService tokens = new TokenService("");
    private static final String SID = "s".repeat(43);
    private static final ContextosService.IdentidadAutenticada IDENTIDAD =
            new ContextosService.IdentidadAutenticada(1000,"persona","persona@cuentas.example.test","Persona");
    private static final ContextosService.Contexto CONTEXTO =
            new ContextosService.Contexto("opaco",92,99,880,"AGENTE","EMPRESARIAL","Agencia");
    private ContextosService.SesionAccesoDTO sesion(ContextosService.Contexto contexto,long revision) {
        Instant iat=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new ContextosService.SesionAccesoDTO(SID,revision,IDENTIDAD,contexto,iat,
                iat.plusSeconds(TokenService.DURACION_SEGUNDOS),EstadoDeAcceso.SIN_RESTRICCIONES);
    }
    @Test void firmaYValidaSesionCoreExactaConPersonaDelContextoElegido() {
        var core=sesion(CONTEXTO,4);var emitida=tokens.de(core);
        var validada=tokens.validar(tokens.firmar(emitida)).orElseThrow();
        assertEquals(emitida,validada);assertEquals(99,validada.idUsuario());assertEquals(880,validada.idDominio());
        assertNotEquals(IDENTIDAD.idCuenta(),validada.idUsuario());
        assertEquals(SID,validada.referenciaSesion());assertEquals(4,validada.revisionContexto());
    }
    @Test void sinContextoFirmaIdentidadSinInventarAtribucionComercial() {
        var validada=tokens.validar(tokens.firmar(tokens.de(sesion(null,1)))).orElseThrow();
        assertEquals("IDENTIDAD",validada.rol());assertEquals(0,validada.idUsuario());assertEquals(0,validada.idDominio());
    }
    @Test void claimsVersionSesionYRevisionSonExplicitos() throws Exception {
        String jwt=tokens.firmar(tokens.de(sesion(CONTEXTO,4)));
        var c=new ObjectMapper().readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        assertEquals(2,c.path("v").asInt());assertEquals(SID,c.path("sid").asText());
        assertEquals(4,c.path("ctx").asLong());assertFalse(c.has("organizacionId"));
    }
    @Test void rechazaTokenManipulado() {
        String jwt=tokens.firmar(tokens.de(sesion(CONTEXTO,1)));
        assertTrue(tokens.validar(jwt.substring(0,jwt.length()-2)+"xx").isEmpty());
    }
    @Test void rechazaFirmaDeOtroSecreto() {
        var otro=new TokenService("un-secreto-distinto-de-al-menos-32-caracteres");
        assertTrue(tokens.validar(otro.firmar(otro.de(sesion(CONTEXTO,1)))).isEmpty());
    }
    @Test void rechazaBasuraYClaimsObligatoriosAusentes() {
        for(String jwt:new String[]{null,"","a.b","a.b.c"})assertTrue(tokens.validar(jwt).isEmpty());
        for(String claim:new String[]{"v","sid","ctx","iat","exp","rol","idUsuario","idDominio"})
            assertTrue(tokens.validar(refirmar(c->c.remove(claim))).isEmpty(),claim);
    }
    @Test void versionUnoYAdminHistoricoNoAbrenSesionActual() {
        assertTrue(tokens.validar(refirmar(c->c.put("v",1))).isEmpty());
        assertTrue(tokens.validar(refirmar(c->c.put("rol","ADMIN"))).isEmpty());
    }
    @Test void referenciaInvalidaYRevisionNegativaSeRechazan() {
        assertTrue(tokens.validar(refirmar(c->c.put("sid","uuid-no-es-referencia-durable"))).isEmpty());
        assertTrue(tokens.validar(refirmar(c->c.put("ctx",-1))).isEmpty());
        var core=sesion(CONTEXTO,1);
        assertThrows(IllegalArgumentException.class,()->tokens.firmar(new TokenService.Sesion("persona","AGENTE",99,880,
                core.emitidaEn(),core.venceEn(),null,1)));
    }
    @Test void identidadNoAdmiteRolOperativoYContextoComercialNoAdmiteCero() {
        assertTrue(tokens.validar(refirmar(c->{c.put("rol","IDENTIDAD");c.put("idDominio",880);})).isEmpty());
        assertTrue(tokens.validar(refirmar(c->c.put("idDominio",0))).isEmpty());
    }
    @Test void expiracionYEmisionPosteriorAlVencimientoSeRechazan() {
        assertTrue(tokens.validar(refirmar(c->c.put("exp",Instant.now().minusSeconds(1).getEpochSecond()))).isEmpty());
        assertTrue(tokens.validar(refirmar(c->c.put("iat",Instant.now().plusSeconds(3600).getEpochSecond()))).isEmpty());
    }
    @Test void firmaConservaIatCoreEnPrecisionDeSegundo() {
        var core=sesion(CONTEXTO,1);var emitida=tokens.de(core);
        String una=tokens.firmar(emitida),otra=tokens.firmar(emitida);
        assertEquals(una,otra);assertEquals(core.emitidaEn(),tokens.validar(una).orElseThrow().emitidoEn());
        assertEquals(0,emitida.emitidoEn().getNano());
        assertEquals(TokenService.DURACION_SEGUNDOS,emitida.expiraEn().getEpochSecond()-emitida.emitidoEn().getEpochSecond());
    }
    @Test void dosRevisionesEnElMismoSegundoTienenTokensDistintos() {
        var a=sesion(CONTEXTO,1);
        var b=new ContextosService.SesionAccesoDTO(a.referencia(),2,a.identidad(),a.activo(),a.emitidaEn(),a.venceEn(),a.restricciones());
        String primero=tokens.firmar(tokens.de(a)),segundo=tokens.firmar(tokens.de(b));
        assertNotEquals(primero,segundo);
        assertEquals(tokens.validar(primero).orElseThrow().emitidoEn(),tokens.validar(segundo).orElseThrow().emitidoEn());
        assertNotEquals(tokens.validar(primero).orElseThrow().revisionContexto(),tokens.validar(segundo).orElseThrow().revisionContexto());
    }
    private String refirmar(Consumer<ObjectNode> cambio) {
        String[] partes=tokens.firmar(tokens.de(sesion(CONTEXTO,1))).split("\\.",-1);
        try {
            var carga=(ObjectNode)new ObjectMapper().readTree(Base64.getUrlDecoder().decode(partes[1]));cambio.accept(carga);
            String contenido=partes[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(carga.toString().getBytes(StandardCharsets.UTF_8));
            var firma=TokenService.class.getDeclaredMethod("firma",String.class);firma.setAccessible(true);
            return contenido+"."+firma.invoke(tokens,contenido);
        } catch(ReflectiveOperationException|java.io.IOException e){throw new IllegalStateException("Fixture JWT",e);}
    }
}

