package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.UnidadRelacionada;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.UnidadRelacionadaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.UnidadRelacionadaService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import com.controllocal.service.UnidadRelacionadaService.FichaUnidadRelacionada;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.excepcion.NoEncontradoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;

class UnidadRelacionadaServiceImplTest {

    private final UnidadRelacionadaRepository relaciones = mock(UnidadRelacionadaRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final AutoridadDePropiedad autoridad = mock(AutoridadDePropiedad.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final ComandosIdempotentes comandos=mock(ComandosIdempotentes.class);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final EntityManager entidades=mock(EntityManager.class);
    private final UnidadRelacionadaServiceImpl service =
            new UnidadRelacionadaServiceImpl(relaciones, propiedades, autoridad,contextos,cuentas,miembros,
                    registros,comandos,documentos,eventos,entidades);

    private static final Actor AGENTE=new Actor(7L,20L,30L,Actor.AGENTE);
    private static final LocalDate DESDE=LocalDate.of(2026,9,1);
    private static final Procedencia PANTALLA=Procedencia.deLaPantalla();
    private static final String CLAVE="origen-test-unico";
    private static final String COMANDO="REGISTRAR_ORIGEN_FISICO_UNIDAD";
    private final ContextosService.SesionAccesoDTO dto=new ContextosService.SesionAccesoDTO("sesion-test",3,
            new ContextosService.IdentidadAutenticada(100,"cuenta-test","test@example.invalid",null),
            new ContextosService.Contexto("ctx-test",7,20,30,Actor.AGENTE,"INDIVIDUAL","TEST"),
            Instant.parse("2026-10-28T10:00:00Z"),Instant.parse("2026-10-28T10:30:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
    private final ContextoSesion sesion=ContextoSesion.de(dto);

    @Test
    void fichaResuelveLaUnidadEnCualquierDireccionYConservaSuVigencia() {
        Actor actor = new Actor(7L, 20L, 30L, Actor.AGENTE);
        when(autoridad.buscarParaLectura(actor, 10L)).thenReturn(Optional.of(mock(Propiedad.class)));
        when(autoridad.alcanceDeLectura(actor)).thenReturn(new Alcances.Alcance(7L, false, List.of(30L)));
        UnidadRelacionada relacion = UnidadRelacionada.crear(7L, 10L, 11L, "I",
                LocalDate.of(2026, 1, 2));
        relacion.setVigenteHasta(LocalDate.of(2026, 4, 3));
        Propiedad destino = propiedad(11L, "E", "COC-001", "P-123");
        when(relaciones.deLaPropiedad(7L, 10L)).thenReturn(List.of(relacion));
        when(propiedades.buscarVariasParaLectura(eq(7L), anyCollection(), eq("{30}")))
                .thenReturn(List.of(destino));

        List<FichaUnidadRelacionada> ficha = service.deLaPropiedad(10L, actor);

        assertEquals(1, ficha.size());
        assertEquals(11L, ficha.get(0).idPropiedad());
        assertEquals("ESTACIONAMIENTO", ficha.get(0).tipoPropiedad());
        assertEquals("Independiente", ficha.get(0).tipoRelacionRotulo());
        assertEquals("P-123", ficha.get(0).partidaRegistral());
        assertEquals(LocalDate.of(2026, 4, 3), ficha.get(0).vigenteHasta());
    }

    @Test void unaRelacionNoPermiteDescubrirElOrigenAjeno() {
        Actor actor = new Actor(7L, 20L, 30L, Actor.AGENTE);
        when(autoridad.buscarParaLectura(actor, 10L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class, () -> service.deLaPropiedad(10L, actor));
        verifyNoInteractions(relaciones, propiedades);
    }

    @Test void brokerYGobiernoNoRegistranNiInspeccionanExtremos() {
        for(String rol:List.of(Actor.BROKER,Actor.TENANT_ADMIN)) {
            Actor otro=new Actor(7L,20L,31L,rol);
            assertThrows(AccesoNoAutorizadoException.class,()->service.registrarOrigen(datos(null),otro,sesion,CLAVE,PANTALLA));
            assertThrows(AccesoNoAutorizadoException.class,()->service.capacidadSobreOrigen(10,11,otro));
        }
        verifyNoInteractions(autoridad,relaciones,propiedades,miembros,cuentas,contextos,registros,comandos,eventos,entidades);
    }

    @Test void referenciasIgualesVersionAusenteYFechaInvertidaNoLleganALaEscritura() {
        assertThrows(ReglaNegocioException.class,()->service.registrarOrigen(
                new DatosOrigen(10L,10L,DESDE,null,null,"Fuente TEST",2L,4L),AGENTE,sesion,CLAVE,PANTALLA));
        assertThrows(ReglaNegocioException.class,()->service.registrarOrigen(
                new DatosOrigen(10L,11L,DESDE,null,null,"Fuente TEST",null,4L),AGENTE,sesion,CLAVE,PANTALLA));
        assertThrows(ReglaNegocioException.class,()->service.registrarOrigen(
                new DatosOrigen(10L,11L,DESDE,DESDE.minusDays(1),null,"Fuente TEST",2L,4L),AGENTE,sesion,CLAVE,PANTALLA));
        verifyNoInteractions(autoridad,relaciones,propiedades,miembros,contextos,eventos,comandos);
    }

    @Test void capacidadExigeResponsabilidadEnAmbosSinEfectos() {
        var m=propiedad(10L,"C","MAT-10",null);var u=propiedad(11L,"D","UNI-11",null);
        when(m.getVersionFisica()).thenReturn(2L);when(u.getVersionFisica()).thenReturn(4L);
        when(autoridad.buscarParaLectura(AGENTE,10L)).thenReturn(Optional.of(m));
        when(autoridad.buscarParaLectura(AGENTE,11L)).thenReturn(Optional.of(u));
        assertEquals(new ContextoOrigen(10,11,2,4),service.capacidadSobreOrigen(10,11,AGENTE));
        verify(autoridad).exigirEdicion(AGENTE,m);verify(autoridad).exigirEdicion(AGENTE,u);
        doThrow(new AccesoNoAutorizadoException()).when(autoridad).exigirEdicion(AGENTE,u);
        assertThrows(AccesoNoAutorizadoException.class,()->service.capacidadSobreOrigen(10,11,AGENTE));
        verifyNoInteractions(relaciones,eventos,comandos);
    }

    @Test void registroConservaDeclaracionVersionesYReciboSinInventarPartida() {
        prepararRegistro();
        var resultado=service.registrarOrigen(datos("Según documento: división en trámite"),AGENTE,sesion,CLAVE,PANTALLA);
        assertEquals("ORIGEN_MATRIZ_UNIDAD",resultado.relacion());
        assertEquals(EstadoDato.CONOCIDO,resultado.estadoSituacion());
        assertEquals("Según documento: división en trámite",resultado.situacionRegistral());
        assertEquals(3,resultado.versionMatriz());assertEquals(5,resultado.versionUnidad());
        assertEquals(90L,resultado.idRelacion());assertEquals(91L,resultado.idEvento());
        var vinculo=org.mockito.ArgumentCaptor.forClass(UnidadRelacionada.class);
        verify(relaciones).saveAndFlush(vinculo.capture());
        assertEquals("O",vinculo.getValue().getTipoRelacion());
        assertEquals(2L,vinculo.getValue().getVersionMatrizObservada());
        assertEquals(4L,vinculo.getValue().getVersionUnidadObservada());
        assertEquals(30L,vinculo.getValue().getIdRolActor());
        assertNotNull(vinculo.getValue().getProcedencia());
        var recibo=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(comandos).registrar(eq(AGENTE),eq(CLAVE),eq(COMANDO),anyString(),eq("UNIDAD_RELACIONADA"),eq(90L),eq(PANTALLA),recibo.capture());
        assertEquals(resultado,documentos.comoObjeto(recibo.getValue(),ResultadoOrigen.class));
        var orden=inOrder(cuentas,contextos,registros,relaciones,propiedades,eventos,comandos);
        orden.verify(cuentas).bloquearCambioAcceso(100L);
        orden.verify(contextos).exigirContexto("sesion-test",3,AGENTE);
        orden.verify(registros).bloquearIdentidad(anyLong());
        orden.verify(comandos).buscar(eq(AGENTE),eq(CLAVE),eq(COMANDO),anyString());
        orden.verify(relaciones).bloquearGrafoOrigen(7L);
        orden.verify(propiedades).bloquearParaEscritura(7L,10L);
        orden.verify(propiedades).bloquearParaEscritura(7L,11L);
        orden.verify(relaciones).saveAndFlush(any(UnidadRelacionada.class));
        orden.verify(eventos).saveAndFlush(any(EventoDominio.class));
    }

    @Test void situacionDesconocidaPermaneceFaltanteConFuenteSinExigirPartida() {
        prepararRegistro();
        var r=service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA);
        assertNull(r.situacionRegistral());assertEquals(EstadoDato.FALTANTE,r.estadoSituacion());
        assertEquals("Documento TEST de origen",r.fuente());
    }

    @Test void cambiarSesionAntesDelEfectoNoRegistraNiBloqueaElGrafo() {
        prepararRegistro();
        when(contextos.exigirContexto("sesion-test",3,AGENTE)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verifyNoInteractions(relaciones,propiedades,eventos,comandos,registros);
    }

    @Test void ambasVersionesSeComparanAntesDeMutar() {
        prepararRegistro();
        var vieja=new DatosOrigen(10L,11L,DESDE,null,null,"Documento TEST de origen",2L,3L);
        assertThrows(ConflictoException.class,()->service.registrarOrigen(vieja,AGENTE,sesion,CLAVE,PANTALLA));
        verify(propiedades,never()).incrementarVersionFisicaSi(anyLong(),anyLong(),anyLong());
        verify(relaciones,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }

    @Test void segundoExtremoInaccesibleNoRecibeOrigenNiVersionNueva() {
        prepararRegistro();when(autoridad.buscarParaLectura(AGENTE,11L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verify(propiedades,never()).incrementarVersionFisicaSi(anyLong(),anyLong(),anyLong());
        verify(relaciones,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }

    @Test void segundoExtremoLeiblePeroNoEditableTampocoAutorizaRegistro() {
        prepararRegistro();
        doThrow(new AccesoNoAutorizadoException()).when(autoridad).exigirEdicion(eq(AGENTE),argThat(p->p.getId().equals(11L)));
        assertThrows(AccesoNoAutorizadoException.class,()->service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verify(propiedades,never()).incrementarVersionFisicaSi(anyLong(),anyLong(),anyLong());
        verify(relaciones,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }

    @Test void parSolapadoSeRechazaSinTratarloComoReintento() {
        prepararRegistro();when(relaciones.existeDuplicadoOrigen(7,10,11,DESDE,null)).thenReturn(true);
        assertThrows(ConflictoException.class,()->service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verify(propiedades,never()).incrementarVersionFisicaSi(anyLong(),anyLong(),anyLong());
        verify(relaciones,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }

    @Test void caminoDeUnidadAMatrizImpideElCicloSinMutarNingunExtremo() {
        prepararRegistro();when(relaciones.existeCaminoOrigen(7,11,10)).thenReturn(true);
        assertThrows(ReglaNegocioException.class,()->service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verify(propiedades,never()).incrementarVersionFisicaSi(anyLong(),anyLong(),anyLong());
        verify(relaciones,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }

    @Test void perderElSegundoCasNoRegistraVinculoEventoNiRecibo() {
        prepararRegistro();when(propiedades.incrementarVersionFisicaSi(7,11,4)).thenReturn(0);
        assertThrows(ConflictoException.class,()->service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verify(relaciones,never()).saveAndFlush(any());verifyNoInteractions(eventos);
        verify(comandos,never()).registrar(any(),any(),any(),any(),any(),any(),any(),any());
        // El rollback del primer contador requiere la prueba PG de dos extremos, no lo demuestra Mockito.
    }

    @Test void replayDevuelveElOriginalAutorizadoSinRecompararCasNiPermisoDeEdicion() {
        prepararRegistro();var original=original();
        when(comandos.buscar(eq(AGENTE),eq(CLAVE),eq(COMANDO),anyString())).thenReturn(Optional.of(comando(original)));
        assertEquals(original,service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verifyNoInteractions(relaciones,propiedades,eventos);
        verify(autoridad,never()).exigirEdicion(any(),any());
    }

    @Test void replayNoEntregaFuenteNiReciboCuandoSePierdeLaLecturaDelSegundoExtremo() {
        prepararRegistro();when(comandos.buscar(eq(AGENTE),eq(CLAVE),eq(COMANDO),anyString())).thenReturn(Optional.of(comando(original())));
        when(autoridad.buscarParaLectura(AGENTE,11L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->service.registrarOrigen(datos(null),AGENTE,sesion,CLAVE,PANTALLA));
        verifyNoInteractions(relaciones,propiedades,eventos);
    }

    @Test void conciliacionLeeElReciboOriginalReautorizandoAmbosExtremos() {
        prepararRegistro();var original=original();
        when(comandos.consultarResultado(AGENTE,CLAVE,COMANDO)).thenReturn(Optional.of(comando(original)));
        assertEquals(Optional.of(original),service.consultarResultadoOrigen(CLAVE,AGENTE));
        verify(autoridad).buscarParaLectura(AGENTE,10L);verify(autoridad).buscarParaLectura(AGENTE,11L);
        verifyNoInteractions(relaciones,propiedades,eventos,cuentas,contextos,miembros,registros);
        when(autoridad.buscarParaLectura(AGENTE,11L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->service.consultarResultadoOrigen(CLAVE,AGENTE));
    }

    @Test void lecturaDesdeLaUnidadConservaDireccionOrigenYFiltraPorConocimientoYDiaLimaInclusivo() {
        var r=UnidadRelacionada.crearOrigen(7L,10L,11L,DESDE,DESDE,null,"Fuente TEST","{}",2,4,30,Instant.parse("2026-09-01T06:00:00Z"));
        when(autoridad.buscarParaLectura(AGENTE,11L)).thenReturn(Optional.of(mock(Propiedad.class)));
        when(autoridad.alcanceDeLectura(AGENTE)).thenReturn(new Alcances.Alcance(7,false,List.of(30L)));
        when(relaciones.origenesDeLaPropiedad(7,11)).thenReturn(List.of(r));
        Propiedad matriz=propiedad(10L,"C","MAT",null);
        when(propiedades.buscarVariasParaLectura(eq(7L),anyCollection(),eq("{30}"))).thenReturn(List.of(matriz));
        var vigentes=service.consultarOrigen(11,Instant.parse("2026-09-02T04:59:59Z"),AGENTE);
        assertEquals(1,vigentes.size());assertEquals(10L,vigentes.getFirst().idMatriz());assertEquals(11L,vigentes.getFirst().idUnidad());
        assertEquals(EstadoDato.FALTANTE,vigentes.getFirst().estadoSituacion());
        assertEquals(List.of(),service.consultarOrigen(11,Instant.parse("2026-09-02T05:00:00Z"),AGENTE));
        assertEquals(List.of(),service.consultarOrigen(11,Instant.parse("2026-09-01T05:59:59Z"),AGENTE));
        assertEquals(1,service.consultarOrigen(11,null,AGENTE).size());
    }

    @Test void unDestinoSinLecturaActualNoSeFiltraPorElGrafoNiPorSuFuente() {
        var r=UnidadRelacionada.crearOrigen(7L,10L,11L,DESDE,null,"Declarado","Fuente privada TEST","{}",2,4,30,Instant.parse("2026-09-01T06:00:00Z"));
        when(autoridad.buscarParaLectura(AGENTE,10L)).thenReturn(Optional.of(mock(Propiedad.class)));
        when(autoridad.alcanceDeLectura(AGENTE)).thenReturn(new Alcances.Alcance(7,false,List.of(30L)));
        when(relaciones.origenesDeLaPropiedad(7,10)).thenReturn(List.of(r));
        when(propiedades.buscarVariasParaLectura(eq(7L),anyCollection(),eq("{30}"))).thenReturn(List.of());
        assertEquals(List.of(),service.consultarOrigen(10,null,AGENTE));
    }

    private static DatosOrigen datos(String situacion) {
        return new DatosOrigen(10L,11L,DESDE,null,situacion,"Documento TEST de origen",2L,4L);
    }
    private void prepararRegistro() {
        when(miembros.cuentasActivasDePersona(7,20)).thenReturn(List.of(100L));
        when(cuentas.bloquearCambioAcceso(100)).thenReturn(Optional.of(mock(CuentaAcceso.class)));
        when(contextos.exigirContexto("sesion-test",3,AGENTE)).thenReturn(dto);
        var m=propiedad(10L,"C","MAT",null);var u=propiedad(11L,"D","UNI",null);
        var vm=new AtomicLong(2);var vu=new AtomicLong(4);
        when(m.getVersionFisica()).thenAnswer(i->vm.get());when(u.getVersionFisica()).thenAnswer(i->vu.get());
        when(autoridad.buscarParaLectura(AGENTE,10L)).thenReturn(Optional.of(m));
        when(autoridad.buscarParaLectura(AGENTE,11L)).thenReturn(Optional.of(u));
        when(propiedades.bloquearParaEscritura(7,10)).thenReturn(Optional.of(m));
        when(propiedades.bloquearParaEscritura(7,11)).thenReturn(Optional.of(u));
        when(propiedades.incrementarVersionFisicaSi(7,10,2)).thenAnswer(i->{vm.incrementAndGet();return 1;});
        when(propiedades.incrementarVersionFisicaSi(7,11,4)).thenAnswer(i->{vu.incrementAndGet();return 1;});
        when(relaciones.saveAndFlush(any(UnidadRelacionada.class))).thenAnswer(i->{var r=i.getArgument(0,UnidadRelacionada.class);id(r,90L);return r;});
        when(eventos.saveAndFlush(any(EventoDominio.class))).thenAnswer(i->{var e=i.getArgument(0,EventoDominio.class);id(e,91L);return e;});
    }
    private static void id(Object objeto,long valor) {
        try {var f=objeto.getClass().getDeclaredField("id");f.setAccessible(true);f.set(objeto,valor);}
        catch(ReflectiveOperationException fallo){throw new AssertionError(fallo);}
    }
    private static ResultadoOrigen original() {
        return new ResultadoOrigen(90L,10L,11L,"ORIGEN_MATRIZ_UNIDAD",DESDE,null,null,EstadoDato.FALTANTE,
                "Fuente original TEST",3,5,Instant.parse("2026-09-01T06:00:00Z"),91L);
    }
    private ComandoIdempotente comando(ResultadoOrigen r) {
        var mapa=new ObjectMapper().findAndRegisterModules().convertValue(r,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        return ComandoIdempotente.de(7L,CLAVE,COMANDO,"huella-test","UNIDAD_RELACIONADA",90L,30L,"SPA",null,null,documentos.objetoCanonico(mapa));
    }

    private static Propiedad propiedad(Long id, String tipo, String codigo, String partida) {
        Propiedad p = mock(Propiedad.class);
        when(p.getId()).thenReturn(id);
        when(p.getTipoInmueble()).thenReturn(tipo);
        when(p.getCodigo()).thenReturn(codigo);
        when(p.getPartidaRegistral()).thenReturn(partida);
        return p;
    }
}
