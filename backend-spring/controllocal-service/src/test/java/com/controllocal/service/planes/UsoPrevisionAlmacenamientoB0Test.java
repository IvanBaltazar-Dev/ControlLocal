package com.controllocal.service.planes;

import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.presupuesto.UsoAlmacenamientoB0Repository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import com.controllocal.service.planes.ClasificadorCargaAlmacenamientoB0.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unidades del ledger; políticas/capacidades sintéticas explícitas, sin aceptación de calibración. */
class UsoPrevisionAlmacenamientoB0Test {
    final Instant ahora=Instant.parse("2026-11-01T12:00:00Z");
    final UUID dueno=UUID.randomUUID();
    final UUID job=UUID.randomUUID();final String namespace="a".repeat(64),hash="b".repeat(64),origen="c".repeat(64);
    final Actor actor=new Actor(1,2,3,Actor.AGENTE);final ContextoSesion sid=new ContextoSesion("s".repeat(64),2_000_000_000,"SID_TEST",1);
    final Recurso recurso=new Recurso("DERIVADA_TEST",job.toString(),"PRUEBA",origen);
    final RefInterna ref=UsoAlmacenamientoB0.referenciaPrevision(1,job);
    final PrevisionIa peticion=new PrevisionIa(job,recurso,"PERFIL_TEST","V_TEST",namespace);
    final String identidad=namespace+":tenant/1/salidas/TEST.png",objeto=Idempotencia.huellaExacta(identidad);
    final ContextoPresupuestoB0 contexto=mock(ContextoPresupuestoB0.class);
    final PresupuestoIaRepository dinero=mock(PresupuestoIaRepository.class);
    final UsoAlmacenamientoB0Repository repo=mock(UsoAlmacenamientoB0Repository.class);
    final ClasificadorCargaAlmacenamientoB0 clasificador=mock(ClasificadorCargaAlmacenamientoB0.class);
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final UsoAlmacenamientoB0 servicio=new UsoAlmacenamientoB0(contexto,dinero,repo,json,List.of(clasificador),Clock.fixed(ahora,ZoneOffset.UTC));
    @BeforeEach void preparar(){
        when(repo.dueno(1L,null)).thenReturn(dueno);
        when(clasificador.atiende(recurso)).thenReturn(true);
        when(clasificador.clasificarPrevision(peticion,actor)).thenReturn(new Clasificacion(CategoriaAlmacenamiento.DERIVADA_IA,Necesidad.ORDINARIA,origen));
        when(clasificador.clasificar(any(),eq(actor))).thenReturn(new Clasificacion(CategoriaAlmacenamiento.DERIVADA_IA,Necesidad.ORDINARIA,origen));
        when(dinero.bloquearOrganizacion(1)).thenReturn(new PresupuestoIaRepository.Organizacion("INDIVIDUAL",true));
        when(repo.bloquearUso(1)).thenReturn(new Uso(0,0));
        when(repo.limiteSalida("PERFIL_TEST","V_TEST",ahora)).thenReturn(Optional.of(new LimiteSalida(100)));
        when(repo.bloquearCapacidadFisica(namespace,ahora)).thenReturn(Optional.of(fisica(1_000,0,0,true)));
        when(repo.bloquearFisicaReservada(namespace,"FISICA_TEST")).thenReturn(fisica(1_000,0,100,false));
        when(repo.ahoraDeAlmacenamiento()).thenReturn(ahora);
    }
    CapacidadFisica fisica(long limite,long usados,long reservados,boolean vigente){return new CapacidadFisica(namespace,"FISICA_TEST",limite,usados,reservados,vigente);}
    Detalle detalle(boolean reclamada){return new Detalle("PREVISION_IA",job,"PERFIL_TEST","V_TEST",namespace,100L,reclamada?ahora.minusSeconds(1):null,"FISICA_TEST",100,null);}
    Reserva reserva(String estado,Long bytes){return new Reserva(ref.reserva(),2,3,"d".repeat(64),recurso.tipo(),recurso.referencia(),recurso.finalidad(),origen,
            CategoriaAlmacenamiento.DERIVADA_IA.name(),Necesidad.ORDINARIA.name(),bytes,bytes==null?null:hash,100,"TEST","{}",estado,bytes==null?null:objeto);}
    void existente(String estado,Long bytes,boolean reclamada){when(repo.reserva(dueno,ref.reserva())).thenReturn(Optional.of(reserva(estado,bytes)));when(repo.detalle(dueno,ref.reserva())).thenReturn(Optional.of(detalle(reclamada)));}
    ComprobacionObjeto comprobacion(){return new ComprobacionObjeto(identidad,60,hash,"IO_VERIFICADO_TEST");}
    Solicitud salida(long bytes,String huella){return new Solicitud(ref.reserva(),recurso,bytes,huella,identidad,namespace);}
    AusenciaComprobada ausencia(){UUID retiro=UUID.randomUUID();return new AusenciaComprobada(retiro,job,UUID.randomUUID(),1,identidad,60,hash,"MATERIAL-IA-RETIRO:"+retiro);}
    Presencia presente(){return new Presencia(namespace,"FISICA_TEST",true,true,"PRESENTE",0,null,null);}
    @Test void techoSeReservaSinInventarBytesNiHashDeContenido(){
        var r=servicio.previsionar(peticion,actor,sid);assertEquals(ref,r.referencia());assertEquals(100,r.techoBytes());
        var reserva=ArgumentCaptor.forClass(Reserva.class);
        verify(repo).crearPrevision(eq(dueno),reserva.capture(),eq(job),eq("PERFIL_TEST"),eq("V_TEST"),eq(namespace),eq(100L),eq("FISICA_TEST"),eq(ahora));
        assertNull(reserva.getValue().bytes());assertNull(reserva.getValue().contenido());assertNull(reserva.getValue().objeto());
        assertEquals("DERIVADA_IA",reserva.getValue().categoria());verify(repo).retener(1,100);verify(repo).retenerFisica(namespace,100);
        verify(dinero,never()).retenerFondo(any(),any());
    }
    @Test void faltaDeLimiteCalibradoDeniegaSinCrearReserva(){
        when(repo.limiteSalida(anyString(),anyString(),any())).thenReturn(Optional.empty());
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.previsionar(peticion,actor,sid));sinRetencion();
    }
    @Test void faltaDeCapacidadFisicaDeniegaAntesDeAdmitirContenido(){
        when(repo.bloquearCapacidadFisica(namespace,ahora)).thenReturn(Optional.empty());
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.previsionar(peticion,actor,sid));sinRetencion();
    }
    @Test void politicaFisicaVencidaNoFabricaEspacioLibre(){
        when(repo.bloquearCapacidadFisica(namespace,ahora)).thenReturn(Optional.of(fisica(1_000,0,0,false)));
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.previsionar(peticion,actor,sid));sinRetencion();
    }
    @Test void cuotaComercialIncluyeElTechoDeTemporalesIa(){
        when(repo.bloquearUso(1)).thenReturn(new Uso(1_999_999_950L,0));
        assertThrows(ReglaNegocioException.class,()->servicio.previsionar(peticion,actor,sid));sinRetencion();
    }
    @Test void otraPrevisionFisicaPuedeImpedirLaGeneracion(){
        when(repo.bloquearCapacidadFisica(namespace,ahora)).thenReturn(Optional.of(fisica(1_000,850,100,true)));
        assertThrows(ReglaNegocioException.class,()->servicio.previsionar(peticion,actor,sid));sinRetencion();
    }
    @Test void sumaFisicaNoDesbordaLongNiAceptaTechoArtificial(){
        when(repo.bloquearCapacidadFisica(namespace,ahora)).thenReturn(Optional.of(fisica(Long.MAX_VALUE,Long.MAX_VALUE,1,true)));
        assertThrows(ReglaNegocioException.class,()->servicio.previsionar(peticion,actor,sid));sinRetencion();
    }
    @Test void huerfanoFisicoNoEsCuotaComercialAgotadaNiEspacioLibre(){
        when(repo.existenIntentosFisicosSinCobertura(namespace,null,null,0,null,AmbitoIntentos.NAMESPACE)).thenReturn(true);
        var e=assertThrows(IncertidumbreFisicaException.class,()->servicio.previsionar(peticion,actor,sid));
        assertEquals("INCERTIDUMBRE_TECNICA",e.causa());assertFalse(e.getMessage().contains(namespace));sinRetencion();
        verify(repo,never()).dueno(0L,null);
    }
    @Test void claimRevalidaIncertidumbreSinReclamarNiGastar(){
        servicio.previsionar(peticion,actor,sid);var captor=ArgumentCaptor.forClass(Reserva.class);verify(repo).crearPrevision(eq(dueno),captor.capture(),any(),anyString(),anyString(),anyString(),anyLong(),anyString(),any());
        when(repo.reserva(dueno,ref.reserva())).thenReturn(Optional.of(captor.getValue()));when(repo.detalle(dueno,ref.reserva())).thenReturn(Optional.of(detalle(false)));
        when(repo.bloquearCapacidadFisica(namespace,ahora)).thenReturn(Optional.of(fisica(1_000,0,100,true)));
        when(repo.existenIntentosFisicosSinCobertura(namespace,null,null,0,null,AmbitoIntentos.NAMESPACE)).thenReturn(true);
        assertThrows(IncertidumbreFisicaException.class,()->servicio.exigirAntesDeClaim(ref,actor,sid));
        verify(repo,never()).reclamarPrevision(any(UUID.class),any(),any());verify(dinero,never()).retenerFondo(any(),any());
        verify(repo,never()).dueno(0L,null);
    }
    @Test void nuevoIntentoPropioNoDuplicaElEspacioDeUnaPrevision(){
        existente("RESERVADA",null,true);
        when(repo.existenIntentosFisicosSinCobertura(namespace,dueno,objeto,60,hash,AmbitoIntentos.OPERACION_PROPIA)).thenReturn(true);
        assertThrows(IncertidumbreFisicaException.class,()->servicio.admitirPrevisionParaAlmacenamiento(ref,salida(60,hash),actor,sid));
        verify(repo,never()).fijarContenido(any(UUID.class),any(),anyLong(),anyString(),anyString(),any());sinRetencion();
    }
    @Test void recuperacionDeIdentidadFijadaNoBloqueaSuPropiaConciliacion(){
        existente("ALMACENANDO",60L,true);
        when(repo.existenIntentosFisicosSinCobertura(anyString(),nullable(UUID.class),any(),anyLong(),any(),any())).thenReturn(true);
        assertEquals("ALMACENANDO",servicio.admitirPrevisionParaAlmacenamiento(ref,salida(60,hash),actor,sid).estado());
        verify(repo,never()).existenIntentosFisicosSinCobertura(anyString(),nullable(UUID.class),any(),anyLong(),any(),any());
    }
    @Test void asignacionExactaTambienRespetaElEspacioFisicoIncierto(){
        var s=new Solicitud(UUID.randomUUID(),recurso,60,hash,identidad,namespace);
        when(clasificador.clasificar(s,actor)).thenReturn(new Clasificacion(CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,origen));
        when(repo.existenIntentosFisicosSinCobertura(namespace,dueno,objeto,60,hash,AmbitoIntentos.NAMESPACE)).thenReturn(true);
        assertThrows(IncertidumbreFisicaException.class,()->servicio.reservar(s,actor,sid));sinRetencion();
    }
    @Test void confirmarResultadoMenorLiberaExcesoYSumaBytesRealesUnaVez(){
        existente("ALMACENANDO",60L,true);servicio.confirmar(ref,comprobacion());
        verify(repo).contabilizar(1,100,60);verify(repo).contabilizarFisica(namespace,100,60);
        verify(repo).objetoConfirmado(eq(dueno),argThat(o->o.bytes()==60&&o.contenido().equals(hash)&&o.categoria().equals("DERIVADA_IA")),eq("IO_VERIFICADO_TEST"),eq(ahora));
        verify(repo).presencia(dueno,objeto,namespace,"FISICA_TEST",true,true);
    }
    @Test void replayConfirmadoNoCobraOtraVezElMismoObjeto(){
        existente("CONFIRMADA",60L,true);when(repo.presencia(dueno,objeto)).thenReturn(Optional.of(presente()));
        assertEquals("CONFIRMADA",servicio.confirmar(ref,comprobacion()).estado());
        verify(repo,never()).contabilizar(anyLong(),anyLong(),anyLong());verify(repo,never()).contabilizarFisica(anyString(),anyLong(),anyLong());
    }
    @Test void noPuedeGuardarSinClaimNiAunqueConozcaElHash(){
        existente("RESERVADA",null,false);
        assertThrows(ConflictoException.class,()->servicio.admitirPrevisionParaAlmacenamiento(ref,salida(60,hash),actor,sid));
        verify(repo,never()).fijarContenido(any(UUID.class),any(),anyLong(),anyString(),anyString(),any());
    }
    @Test void resultadoMayorQueTechoNoConsumeOtraReserva(){
        existente("RESERVADA",null,true);
        assertThrows(ConflictoException.class,()->servicio.admitirPrevisionParaAlmacenamiento(ref,salida(101,hash),actor,sid));
        verify(repo,never()).fijarContenido(any(UUID.class),any(),anyLong(),anyString(),anyString(),any());sinRetencion();
    }
    @Test void contenidoYaFijadoNoCambiaDeHash(){
        existente("ALMACENANDO",60L,true);
        assertThrows(ConflictoException.class,()->servicio.admitirPrevisionParaAlmacenamiento(ref,salida(60,"e".repeat(64)),actor,sid));
        verify(repo,never()).fijarContenido(any(UUID.class),any(),anyLong(),anyString(),anyString(),any());
    }
    @Test void cancelarExactoNoSirveParaLiberarPrevisionIaReclamada(){
        existente("RESERVADA",null,true);
        assertThrows(ConflictoException.class,()->servicio.cancelarNoAlmacenado(ref,null));
        verify(repo,never()).contabilizar(anyLong(),anyLong(),anyLong());
    }
    @Test void qcSinEscritorNecesitaPruebaDelPropietarioAntesDeOrg(){
        existente("RESERVADA",null,true);var p=new NoAlmacenadoComprobado(job,"MATERIAL-IA-NO-ALMACENADO:7","f".repeat(64),ahora);
        doThrow(new ConflictoException("TEST sin prueba")).when(clasificador).exigirNoAlmacenado(recurso,p);
        assertThrows(ConflictoException.class,()->servicio.cancelarPrevisionNoAlmacenada(ref,p));verify(dinero,never()).bloquearOrganizacion(anyLong());
    }
    @Test void qcTerminadoSinArchivoLiberaBytesSinModificarInferencia(){
        existente("RESERVADA",null,true);var p=new NoAlmacenadoComprobado(job,"MATERIAL-IA-NO-ALMACENADO:7","f".repeat(64),ahora);
        assertEquals("CANCELADA",servicio.cancelarPrevisionNoAlmacenada(ref,p).estado());
        verify(repo).contabilizar(1,100,0);verify(repo).contabilizarFisica(namespace,100,0);
        verify(dinero,never()).liquidarFondo(any(),any(),any());
    }
    @Test void pruebaDbValidaNoSeRechazaPorqueJvmEsteAtras(){
        existente("RESERVADA",null,true);var p=new NoAlmacenadoComprobado(job,"MATERIAL-IA-NO-ALMACENADO:7",hash,ahora);
        assertEquals("CANCELADA",conRelojHost(ahora.minusSeconds(60)).cancelarPrevisionNoAlmacenada(ref,p).estado());
        verify(repo).contabilizar(1,100,0);verify(repo).contabilizarFisica(namespace,100,0);
    }
    @Test void pruebaDbValidaNoDependeDeQueJvmEsteAdelante(){
        existente("RESERVADA",null,true);var p=new NoAlmacenadoComprobado(job,"MATERIAL-IA-NO-ALMACENADO:7",hash,ahora);
        assertEquals("CANCELADA",conRelojHost(ahora.plusSeconds(60)).cancelarPrevisionNoAlmacenada(ref,p).estado());
        verify(repo).contabilizar(1,100,0);verify(repo).contabilizarFisica(namespace,100,0);
    }
    @Test void pruebaFuturaRespectoDbSigueDenegadaAunqueJvmLaAdmitiese(){
        existente("RESERVADA",null,true);var p=new NoAlmacenadoComprobado(job,"MATERIAL-IA-NO-ALMACENADO:7",hash,ahora.plusNanos(1_000));
        assertThrows(ReglaNegocioException.class,()->conRelojHost(ahora.plusSeconds(60)).cancelarPrevisionNoAlmacenada(ref,p));
        verify(repo,never()).contabilizar(anyLong(),anyLong(),anyLong());verify(repo,never()).contabilizarFisica(anyString(),anyLong(),anyLong());
    }
    @Test void pruebaAnteriorAlClaimDbNoLiberaNiConOtraHoraJvm(){
        existente("RESERVADA",null,true);var d=detalle(true);
        when(repo.detalle(dueno,ref.reserva())).thenReturn(Optional.of(new Detalle(d.modalidad(),d.job(),d.perfil(),d.versionPerfil(),d.namespace(),d.techo(),ahora,d.versionFisica(),d.retenidosFisicos(),d.liberacion())));
        var p=new NoAlmacenadoComprobado(job,"MATERIAL-IA-NO-ALMACENADO:7",hash,ahora.minusNanos(1_000));
        assertThrows(ConflictoException.class,()->conRelojHost(ahora.plusSeconds(60)).cancelarPrevisionNoAlmacenada(ref,p));
        verify(repo,never()).contabilizar(anyLong(),anyLong(),anyLong());verify(repo,never()).contabilizarFisica(anyString(),anyLong(),anyLong());
    }
    @Test void primerClaimConservaLaHoraDbSinSustituirLaAuditoriaHost(){
        servicio.previsionar(peticion,actor,sid);var captor=ArgumentCaptor.forClass(Reserva.class);
        verify(repo).crearPrevision(eq(dueno),captor.capture(),any(),anyString(),anyString(),anyString(),anyLong(),anyString(),any());
        when(repo.reserva(dueno,ref.reserva())).thenReturn(Optional.of(captor.getValue()));when(repo.detalle(dueno,ref.reserva())).thenReturn(Optional.of(detalle(false)));
        when(repo.bloquearCapacidadFisica(namespace,ahora)).thenReturn(Optional.of(fisica(1_000,0,100,true)));
        Instant db=ahora.plusSeconds(60);when(repo.ahoraDeAlmacenamiento()).thenReturn(db);
        servicio.exigirAntesDeClaim(ref,actor,sid);verify(repo).reclamarPrevision(dueno,ref.reserva(),db);
        verify(dinero).antecedente(eq(1L),isNull(),eq("PREVISION_IA_RECLAMADA"),anyString(),eq(ahora));
    }
    private UsoAlmacenamientoB0 conRelojHost(Instant host){return new UsoAlmacenamientoB0(contexto,dinero,repo,json,List.of(clasificador),Clock.fixed(host,ZoneOffset.UTC));}
    @Test void retiradaProtegidaNoLiberaNiLlegaAOrg(){
        existente("CONFIRMADA",60L,true);when(repo.presencia(dueno,objeto)).thenReturn(Optional.of(presente()));var a=ausencia();
        doThrow(new ConflictoException("TEST versión protegida")).when(clasificador).exigirRetiro(recurso,a);
        assertThrows(ConflictoException.class,()->servicio.liberarRetiradaVerificada(ref,a));verify(dinero,never()).bloquearOrganizacion(anyLong());
        verify(repo,never()).liberarConfirmados(anyLong(),anyLong());
    }
    @Test void ausenciaVerificadaLiberaMismoObjetoUnaVezYConservaMetadata(){
        existente("CONFIRMADA",60L,true);when(repo.presencia(dueno,objeto)).thenReturn(Optional.of(presente()));
        when(repo.objeto(dueno,objeto)).thenReturn(Optional.of(new Objeto(objeto,hash,60,"DERIVADA_IA")));var a=ausencia();
        assertEquals("RETIRADO_VERIFICADO",servicio.liberarRetiradaVerificada(ref,a).estado());
        verify(repo).liberarConfirmados(1,60);verify(repo).liberarFisicosConfirmados(namespace,60);
        when(repo.presencia(dueno,objeto)).thenReturn(Optional.of(new Presencia(namespace,"FISICA_TEST",true,true,"RETIRADO_VERIFICADO",1,a.retiro(),Idempotencia.huellaExacta(json.objetoCanonico(a)))));
        assertEquals("RETIRADO_VERIFICADO",servicio.liberarRetiradaVerificada(ref,a).estado());
        verify(repo,times(1)).liberarConfirmados(1,60);verify(repo,times(1)).retirar(eq(dueno),eq(objeto),eq(a.retiro()),anyString(),eq(ahora));
        verify(repo,never()).objetoConfirmado(any(UUID.class),any(),anyString(),any());
    }
    @Test void metadataRetiradaNoPermiteConfirmarNiRecuperarGratis(){
        existente("CONFIRMADA",60L,true);var a=ausencia();
        when(repo.presencia(dueno,objeto)).thenReturn(Optional.of(new Presencia(namespace,"FISICA_TEST",true,true,"RETIRADO_VERIFICADO",1,a.retiro(),"f".repeat(64))));
        assertThrows(ConflictoException.class,()->servicio.confirmar(ref,comprobacion()));verify(repo,never()).contabilizar(anyLong(),anyLong(),anyLong());
    }
    @Test void nuevasGuardasSonDenegadasParaUnClasificadorSinExtension(){
        var ajeno=new ClasificadorCargaAlmacenamientoB0(){public boolean atiende(Recurso r){return true;}public Clasificacion clasificar(Solicitud s,Actor a){return new Clasificacion(CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,origen);}};
        assertThrows(CapacidadDeshabilitadaException.class,()->ajeno.clasificarPrevision(peticion,actor));
        assertThrows(CapacidadDeshabilitadaException.class,()->ajeno.exigirRetiro(recurso,ausencia()));
        assertThrows(CapacidadDeshabilitadaException.class,()->ajeno.exigirNoAlmacenado(recurso,new NoAlmacenadoComprobado(job,"TEST","f".repeat(64),ahora)));
    }
    @Test void categoriaIaComputaSinCambiarExclusionesTecnicas(){
        assertTrue(CategoriaAlmacenamiento.DERIVADA_IA.computa());
        for(var c:List.of(CategoriaAlmacenamiento.TEMPORAL,CategoriaAlmacenamiento.PREVIEW,CategoriaAlmacenamiento.CACHE,CategoriaAlmacenamiento.COPIA_TECNICA))assertFalse(c.computa());
    }
    @Test void derivadaIaNoUsaLaViaExactaTardia(){
        assertThrows(ConflictoException.class,()->servicio.reservar(new Solicitud(UUID.randomUUID(),recurso,60,hash,identidad,namespace),actor,sid));
        verify(dinero,never()).bloquearOrganizacion(anyLong());sinRetencion();
    }
    private record SolicitudAnterior(UUID reserva,Recurso recurso,long bytes,String huellaContenido,String identidadObjetoConocida){}
    @Test void namespaceTecnicoNoCambiaHuellaDeLaSolicitudExactaAnterior(){
        var s=new Solicitud(UUID.randomUUID(),recurso,60,hash,identidad,namespace);
        var c=new Clasificacion(CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,origen);when(clasificador.clasificar(s,actor)).thenReturn(c);
        servicio.reservar(s,actor,sid);var captor=ArgumentCaptor.forClass(Reserva.class);verify(repo).crearReserva(eq(dueno),captor.capture(),eq(ahora));
        String anterior=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",new SolicitudAnterior(s.reserva(),s.recurso(),s.bytes(),s.huellaContenido(),s.identidadObjetoConocida()),"clasificacion",c,"actor",actor)));
        assertEquals(anterior,captor.getValue().peticion());
    }
    private void sinRetencion(){verify(repo,never()).retener(anyLong(),anyLong());verify(repo,never()).retenerFisica(anyString(),anyLong());}
}
