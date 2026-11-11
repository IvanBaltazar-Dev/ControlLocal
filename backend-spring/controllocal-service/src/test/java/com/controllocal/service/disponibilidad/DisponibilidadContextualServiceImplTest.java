package com.controllocal.service.disponibilidad;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.disponibilidad.ConfirmacionDisponibilidadContextual;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static com.controllocal.service.disponibilidad.DisponibilidadContextualService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Contrato/orden del comando. La atomicidad real y carreras se prueban en PostgreSQL por root. */
class DisponibilidadContextualServiceImplTest {
    private final ConfirmacionDisponibilidadContextualRepository filas=mock(ConfirmacionDisponibilidadContextualRepository.class);
    private final AccesoEncargo acceso=mock(AccesoEncargo.class);
    private final RequerimientoService demandas=mock(RequerimientoService.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final ComandosIdempotentes comandos=mock(ComandosIdempotentes.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    private static final Instant AHORA=Instant.parse("2026-10-28T22:00:00Z");
    private static final Actor AGENTE=new Actor(1,2,3,Actor.AGENTE);
    private final ContextosService.SesionAccesoDTO dto=new ContextosService.SesionAccesoDTO("SID_SINTETICO_UNIT",1,
            new ContextosService.IdentidadAutenticada(4,"test","test@example.invalid","Test"),
            new ContextosService.Contexto("CTX",1,2,3,Actor.AGENTE,"INDIVIDUAL","Org TEST"),
            AHORA.minusSeconds(600),AHORA.plusSeconds(1800),new EstadoDeAcceso(null,false,false,Actor.AGENTE));
    private final ContextoSesion sesion=ContextoSesion.de(dto);
    private DisponibilidadContextualServiceImpl servicio;
    private long idNuevo;

    @BeforeEach void preparar() {
        servicio=new DisponibilidadContextualServiceImpl(filas,acceso,demandas,contextos,cuentas,miembros,
                registros,comandos,eventos,documentos,Clock.fixed(AHORA,ZoneOffset.UTC));
        when(miembros.cuentasActivasDePersona(1,2)).thenReturn(List.of(4L));
        when(cuentas.bloquearCambioAcceso(4)).thenReturn(Optional.of(mock(CuentaAcceso.class)));
        when(contextos.exigirContexto(sesion.referenciaSesion(),1,AGENTE)).thenReturn(dto);
        var meta=new RequerimientoService.ContextoDisponibilidadDemanda(9,7,OperacionInmobiliaria.ALQUILER,3,"A",1,null,null);
        when(demandas.contextoDisponibilidad(9,AGENTE,true)).thenReturn(meta);
        when(demandas.contextoDisponibilidad(9,AGENTE,false)).thenReturn(meta);
        when(filas.hechosParaMatching(eq(1L),eq("DEMANDA"),eq(9L),eq("ALQUILER"),eq(ASPECTO),eq("DEMANDA:9"))).thenReturn(List.of());
        when(comandos.buscar(any(),anyString(),anyString(),anyString())).thenReturn(Optional.empty());
        idNuevo=1;
        when(filas.saveAndFlush(any(ConfirmacionDisponibilidadContextual.class))).thenAnswer(i->{
            var fila=i.getArgument(0,ConfirmacionDisponibilidadContextual.class);fila.setId(idNuevo);return fila;
        });
        when(eventos.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }

    @Test void brokerNoConfirmaNiConsultaElRecursoParaIntentarlo() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.confirmar(datos(0,true,"2026-10-28T15:00:00Z"),
                new Actor(1,10,11,Actor.BROKER),sesion,"k",null));
        verifyNoInteractions(filas,acceso,demandas,contextos,cuentas,miembros,registros,comandos,eventos);
    }

    @Test void hechoFechaYRegistroNoSeConfundenYElComandoConservaRecibo() {
        var r=servicio.confirmar(datos(0,true,"2026-10-28T15:00:00Z"),AGENTE,sesion,"k",null);
        assertEquals(Aplicacion.APLICADA,r.aplicacion()); assertEquals(1,r.registrado().version());
        assertEquals(Instant.parse("2026-10-28T15:00:00Z"),r.registrado().baseFrescura());
        assertEquals(AHORA,r.registrado().registradoEn());
        assertEquals("DEMANDA:9",r.registrado().clave().contexto());
        verify(eventos).saveAndFlush(any());
        verify(comandos).registrar(eq(AGENTE),eq("k"),anyString(),anyString(),eq("CONFIRMACION_DISPONIBILIDAD"),eq(1L),any(),anyString());
    }

    @Test void antecedenteContrarioSeConservaSinReemplazarElVigente() {
        var actual=proyeccion(1,1,true,Instant.parse("2026-10-28T15:00:00Z"));
        when(filas.hechosParaMatching(1,"DEMANDA",9,"ALQUILER",ASPECTO,"DEMANDA:9")).thenReturn(List.of(actual));
        when(filas.findByOrganizacionIdAndId(1,1)).thenReturn(Optional.of(filaActual()));
        idNuevo=2;
        var r=servicio.confirmar(datos(1,false,"2026-10-27T15:00:00Z"),AGENTE,sesion,"k",null);
        assertEquals(Aplicacion.ANTECEDENTE_CONSERVADO,r.aplicacion()); assertEquals(2,r.registrado().version());
        assertEquals(1,r.vigente().id()); assertTrue(r.vigente().disponible());
        assertEquals(Instant.parse("2026-10-28T15:00:00Z"),r.vigente().baseFrescura());
        verify(filas).saveAndFlush(any());
    }

    @Test void fechaSolapadaContrariaDevuelvePrecisionPendienteNoRenovacion() {
        var actual=proyeccion(1,1,true,Instant.parse("2026-10-28T15:00:00Z"));
        when(filas.hechosParaMatching(1,"DEMANDA",9,"ALQUILER",ASPECTO,"DEMANDA:9")).thenReturn(List.of(actual));
        idNuevo=2;
        var datos=new DatosConfirmacion(TipoRecurso.DEMANDA,9,7,1,false,null,LocalDate.of(2026,10,28),"fuente TEST","declaración TEST");
        var r=servicio.confirmar(datos,AGENTE,sesion,"k",null);
        assertEquals(Aplicacion.PRECISION_PENDIENTE,r.aplicacion()); assertNull(r.vigente());
        assertEquals(com.controllocal.service.matching.ContratoMatching.Causa.CONFLICTO,r.indeterminacion());
        assertNull(r.registrado().fechaHecho()); assertEquals(PrecisionHecho.FECHA,r.registrado().precision());
    }

    @Test void versionDeConfirmacionObsoletaNoEsUnNuevoHecho() {
        var actual=proyeccion(1,1,true,Instant.parse("2026-10-28T15:00:00Z"));
        when(filas.hechosParaMatching(1,"DEMANDA",9,"ALQUILER",ASPECTO,"DEMANDA:9")).thenReturn(List.of(actual));
        assertThrows(ConflictoException.class,()->servicio.confirmar(datos(0,false,"2026-10-28T16:00:00Z"),AGENTE,sesion,"k",null));
        verify(filas,never()).saveAndFlush(any()); verifyNoInteractions(eventos);
        verify(comandos,never()).registrar(any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test void restriccionesNuevasBajoElMismoSIDNoPermitenConfirmar() {
        var restringida=new ContextosService.SesionAccesoDTO(dto.referencia(),dto.revision(),dto.identidad(),dto.activo(),
                dto.emitidaEn(),dto.venceEn(),new EstadoDeAcceso(null,false,true,Actor.AGENTE));
        when(contextos.exigirContexto(sesion.referenciaSesion(),1,AGENTE)).thenReturn(restringida);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.confirmar(datos(0,true,"2026-10-28T15:00:00Z"),AGENTE,sesion,"k",null));
        verifyNoInteractions(registros,comandos,eventos,filas,demandas);
    }

    @Test void replayEntregaReciboOriginalSinActualizarHoraYReautorizaRecurso() {
        var original=servicio.confirmar(datos(0,true,"2026-10-28T15:00:00Z"),AGENTE,sesion,"k",null);
        var comando=mock(ComandoIdempotente.class);when(comando.getResultado()).thenReturn(documentos.objetoCanonico(original));
        when(comandos.buscar(any(),eq("k"),anyString(),anyString())).thenReturn(Optional.of(comando));
        clearInvocations(filas,eventos,demandas);
        var repetido=servicio.confirmar(datos(0,true,"2026-10-28T15:00:00Z"),AGENTE,sesion,"k",null);
        assertEquals(original,repetido); verify(filas,never()).saveAndFlush(any());verifyNoInteractions(eventos);
        verify(demandas).contextoDisponibilidad(9,AGENTE,false);
        when(demandas.contextoDisponibilidad(9,AGENTE,false)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.confirmar(datos(0,true,"2026-10-28T15:00:00Z"),AGENTE,sesion,"k",null));
    }

    @Test void falloDeEventoNoDejaUnReciboDeExito() {
        when(eventos.saveAndFlush(any())).thenThrow(new IllegalStateException("sabotaje unitario de evento"));
        assertThrows(IllegalStateException.class,()->servicio.confirmar(datos(0,true,"2026-10-28T15:00:00Z"),AGENTE,sesion,"k",null));
        verify(comandos,never()).registrar(any(),any(),any(),any(),any(),any(),any(),any());
    }

    private static DatosConfirmacion datos(long version,boolean disponible,String fecha) {
        return new DatosConfirmacion(TipoRecurso.DEMANDA,9,7,version,disponible,Instant.parse(fecha),null,"fuente TEST","declaración TEST");
    }
    private static ConfirmacionDisponibilidadContextualRepository.HechoParaMatching proyeccion(long id,long version,boolean disponible,Instant fecha) {
        var p=mock(ConfirmacionDisponibilidadContextualRepository.HechoParaMatching.class);
        when(p.getId()).thenReturn(id);when(p.getVersion()).thenReturn(version);when(p.getDisponible()).thenReturn(disponible);
        when(p.getPrecision()).thenReturn("INSTANTE");when(p.getBaseFrescura()).thenReturn(fecha);when(p.getRegistradoEn()).thenReturn(AHORA.minusSeconds(1));
        return p;
    }
    private static ConfirmacionDisponibilidadContextual filaActual() {
        var f=new ConfirmacionDisponibilidadContextual();f.setId(1L);f.setOrganizacionId(1L);f.setTipoRecurso("DEMANDA");
        f.setIdRecurso(9);f.setOperacion("ALQUILER");f.setAspecto(ASPECTO);f.setContexto("DEMANDA:9");
        f.setVersion(1);f.setVersionRecursoObservada(7);f.setDisponible(true);f.setPrecisionHecho("INSTANTE");
        f.setFechaHecho(Instant.parse("2026-10-28T15:00:00Z"));f.setBaseFrescura(f.getFechaHecho());f.setRegistradoEn(AHORA.minusSeconds(1));
        f.setFuente("fuente previa TEST");f.setEvidenciaRef("evidencia previa TEST");f.setIdPersonaConfirmante(2);f.setIdRolConfirmante(3);return f;
    }
}
