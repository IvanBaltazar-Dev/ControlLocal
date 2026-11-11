package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.OfertaService.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.negociacion.*;
import com.controllocal.service.excepcion.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** C4-17–20 en reglas reales/infraestructura doblada. Las carreras/rollback requieren PG. */
class OfertaServiceImplTest {
    ActividadDePrueba h;
    OfertaRepository ofertas;
    MesaNegociacionRepository mesas;
    FuentePrecioNegociacion precios;
    OfertaServiceImpl servicio;
    final Map<Long,Oferta> filas=new LinkedHashMap<>();
    final Map<Long,String> propuestas=new HashMap<>();
    final Map<Long,Long> versionesMesa=new HashMap<>();
    final Map<Long,MesaNegociacionRepository.AcuerdoGuardado> acuerdos=new HashMap<>();
    long secuencia=1000;
    @BeforeEach void montar(){
        h=new ActividadDePrueba();ofertas=mock(OfertaRepository.class);mesas=mock(MesaNegociacionRepository.class);precios=mock(FuentePrecioNegociacion.class);
        servicio=new OfertaServiceImpl(ofertas,mesas,h.acceso,h.contextos,h.encargos,h.propiedades,precios,h.recibos,h.comandos,h.documentos,
            h.transiciones,h.eventos,h.supervisiones,h.entidades,h.captaciones,h.autoridad);
        when(mesas.version(anyLong(),anyLong())).thenAnswer(i->versionesMesa.getOrDefault(i.<Long>getArgument(1),0L));
        when(mesas.bloquear(anyLong(),anyLong())).thenAnswer(i->versionesMesa.getOrDefault(i.<Long>getArgument(1),0L));
        doAnswer(i->{long op=i.getArgument(1),v=i.getArgument(2);assertEquals(v,versionesMesa.getOrDefault(op,0L));versionesMesa.put(op,v+1);return null;})
            .when(mesas).avanzar(anyLong(),anyLong(),anyLong());
        when(ofertas.vivaDe(anyLong(),anyLong())).thenAnswer(i->filas.values().stream().filter(o->o.getIdOportunidad().equals(i.getArgument(1))&&o.estaViva()).findFirst());
        when(ofertas.aceptadaDe(anyLong(),anyLong())).thenAnswer(i->filas.values().stream().filter(o->o.getIdOportunidad().equals(i.getArgument(1))&&Oferta.ACEPTADA.equals(o.estadoActual())).findFirst());
        when(ofertas.buscarFicha(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(filas.get(i.<Long>getArgument(1))).filter(o->o.getOrganizacionId().equals(i.getArgument(0))));
        when(ofertas.ultimaRondaDe(anyLong(),anyLong())).thenAnswer(i->filas.values().stream().filter(o->o.getIdOportunidad().equals(i.getArgument(1))).mapToInt(Oferta::getRonda).max().orElse(0));
        when(ofertas.saveAndFlush(any())).thenAnswer(i->{
            var o=i.<Oferta>getArgument(0);if(o.getId()==null){ActividadDePrueba.id(o,++secuencia);
                org.springframework.test.util.ReflectionTestUtils.setField(o,"fechaCreacion",OffsetDateTime.now());}
            else ActividadDePrueba.version(o,o.getVersion()+1);
            filas.put(o.getId(),o);return o;
        });
        doAnswer(i->{propuestas.put(i.getArgument(1),i.getArgument(2));return null;}).when(mesas).propuesta(anyLong(),anyLong(),anyString(),any());
        when(mesas.propuesta(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(propuestas.get(i.<Long>getArgument(1))));
        when(precios.congelarReferencia(any(),any(),any())).thenReturn(new ReferenciaDeLaOferta("U","Autorizado",900L,new BigDecimal("100000"),"PEN"));
        when(mesas.acuerdo(anyLong(),anyLong(),anyLong(),anyString(),any())).thenAnswer(i->{
            long oferta=i.getArgument(1),id=5000+oferta;var a=new MesaNegociacionRepository.AcuerdoGuardado(id,1,oferta,i.getArgument(2),i.getArgument(3),i.getArgument(4));
            acuerdos.put(filas.get(oferta).getIdOportunidad(),a);return id;
        });
        when(mesas.acuerdoDeLaMesa(anyLong(),anyLong(),any())).thenAnswer(i->Optional.ofNullable(acuerdos.get(i.<Long>getArgument(1))).filter(a->!a.registradoEn().isAfter(i.getArgument(2))));
    }
    ObjetoExacto objeto(){return new ObjetoExacto(TipoObjeto.UNIDAD,20L,0L,30L,0L,null,null,null);}
    DatosRegistroOferta datos(long op,long vm,String lado,long persona,String monto,Long anterior,Long vAnterior){
        return new DatosRegistroOferta(op,0L,vm,new ParteReal(lado,persona),new Dinero(new BigDecimal(monto),"PEN","TOTAL",null),objeto(),
            "Condiciones TEST",LocalDate.of(2026,10,30),"Manifestación declarada TEST",anterior,vAnterior);
    }
    ResultadoNegociacion abrir(){return servicio.registrarOferta(datos(10,0,"I",100,"95000",null,null),h.agente,h.sid,"oferta-1",h.procedencia);}
    ResultadoNegociacion contra(ResultadoNegociacion anterior,String monto){
        return servicio.registrarContraoferta(new DatosContraoferta(10L,0L,anterior.versionMesa(),anterior.oferta(),anterior.versionOferta(),
            new ParteReal("P",200L),new Dinero(new BigDecimal(monto),"PEN","TOTAL",null),objeto(),"Contra TEST",LocalDate.of(2026,10,30),"Manifestación P"),
            h.agente,h.sid,"contra-"+anterior.oferta(),h.procedencia);
    }
    DatosAceptacion aceptar(ResultadoNegociacion o,String lado,long persona){
        return new DatosAceptacion(10L,0L,o.versionMesa(),0L,o.oferta(),o.versionOferta(),new ParteReal(lado,persona),LocalDate.of(2026,10,30),"Evidencia aceptación TEST",null);
    }
    @Test void primeraMesaExponeVersionCeroRealSinInventarlaEnCliente(){
        assertEquals(0,servicio.capacidades(10,h.agente).versionMesa());assertTrue(servicio.capacidades(10,h.agente).puedeOfertar());
    }
    @Test void propuestaInicialConservaMonedaObjetoParteYReferenciaSinCambiarPrecioAutorizado(){
        var r=abrir();var p=h.documentos.comoObjeto(propuestas.get(r.oferta()),OfertaServiceImpl.PropuestaPersistida.class).propuesta();
        assertEquals(new ParteReal("I",100L),p.proponente());assertEquals("PEN",p.importe().moneda());assertEquals(objeto(),p.objeto());
        assertEquals(0,new BigDecimal("95000").compareTo(filas.get(r.oferta()).getMonto()));assertEquals(Oferta.PRESENTADA,r.estado());
        verify(precios).registrarHitoOfrecido(eq(objeto()),any(),eq(r.oferta()),eq(LocalDate.of(2026,10,30)),eq(h.procedencia),eq(h.agente));
        assertTrue(mockingDetails(h.encargos).getInvocations().stream().noneMatch(i->Set.of("editar","decidir","finalizar").contains(i.getMethod().getName())));
        assertEquals(Captacion.EN_PREPARACION,h.cap.estadoActual());verify(h.autoridad).buscarParaLectura(h.agente,30);
    }
    @Test void contraSuperaLaAnteriorYCreaOtraRondaConLadoReal(){
        var a=abrir();var b=contra(a,"98000");assertEquals(Oferta.SUPERADA,filas.get(a.oferta()).estadoActual());
        assertEquals(Oferta.PRESENTADA,b.estado());assertNotEquals(a.oferta(),b.oferta());assertEquals(2,filas.get(b.oferta()).getRonda());assertEquals(a.oferta(),filas.get(b.oferta()).getIdOfertaAnterior());
    }
    @Test void volverAlMismoImporteCreaOtraIdentidadNoResucitaLaPrimera(){
        var a=abrir();var b=contra(a,"98000");
        var c=servicio.registrarOferta(datos(10,2,"P",200,"95000",b.oferta(),b.versionOferta()),h.agente,h.sid,"manifestacion-P-2",h.procedencia);
        assertNotEquals(a.oferta(),c.oferta());assertNotEquals(b.oferta(),c.oferta());assertEquals(3,filas.get(c.oferta()).getRonda());
        assertEquals(Oferta.SUPERADA,filas.get(a.oferta()).estadoActual());assertEquals(Oferta.SUPERADA,filas.get(b.oferta()).estadoActual());
        assertEquals(0,new BigDecimal("95000").compareTo(filas.get(c.oferta()).getMonto()));
    }
    @Test void contraNoSeUsaParaUnaManifestacionDelMismoLado(){
        var a=abrir();
        var d=new DatosContraoferta(10L,0L,1L,a.oferta(),0L,new ParteReal("I",100L),new Dinero(new BigDecimal("96000"),"PEN",null,null),objeto(),null,LocalDate.of(2026,10,30),"TEST");
        assertThrows(ReglaNegocioException.class,()->servicio.validarContraoferta(d,h.agente));assertEquals(1,filas.size());
    }
    @Test void nuevaOfertaConOtraPresentadaExigeAntecedenteYVersionExplicitos(){
        abrir();assertThrows(ConflictoException.class,()->servicio.validarRegistroOferta(datos(10,1,"I",100,"96000",null,null),h.agente));
    }
    @Test void parejaAntecedenteVersionNoPuedeSerParcial(){
        assertThrows(ReglaNegocioException.class,()->servicio.validarRegistroOferta(datos(10,0,"I",100,"95000",7L,null),h.agente));
    }
    @Test void reemplazoDelLadoOpuestoDebeUsarContraoferta(){
        var a=abrir();assertThrows(ReglaNegocioException.class,()->servicio.validarRegistroOferta(datos(10,1,"P",200,"98000",a.oferta(),0L),h.agente));
    }
    @Test void versionMesaAntiguaNoModificaNingunaPropuesta(){
        abrir();assertThrows(ConflictoException.class,()->servicio.registrarOferta(datos(10,0,"I",100,"96000",null,null),h.agente,h.sid,"otra",h.procedencia));assertEquals(1,filas.size());
    }
    @Test void omitirCasNoSeConvierteEnCero(){
        var d=datos(10,0,"I",100,"95000",null,null);
        assertThrows(ReglaNegocioException.class,()->servicio.validarRegistroOferta(new DatosRegistroOferta(d.oportunidad(),null,0L,d.proponente(),d.importe(),d.objeto(),null,d.fechaHecho(),d.evidencia(),null,null),h.agente));
    }
    @Test void actorNoEsLaParteYNoPuedeInventarUnTitular(){
        assertThrows(ReglaNegocioException.class,()->servicio.validarRegistroOferta(datos(10,0,"P",999,"95000",null,null),h.agente));
        assertThrows(ReglaNegocioException.class,()->servicio.validarRegistroOferta(datos(10,0,"I",7,"95000",null,null),h.agente));
    }
    @Test void brokerYGobiernoNoRegistranPorElAgente(){
        var d=datos(10,0,"I",100,"95000",null,null);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarRegistroOferta(d,h.broker));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarRegistroOferta(d,h.gobierno));
        assertTrue(filas.isEmpty());
    }
    @Test void otroTenantNoLeeMesaNiAcuerdo(){
        var ajeno=new Actor(2,107,101,Actor.AGENTE);
        when(h.oportunidades.contextoOperativoActual(eq(2L),eq(107L),eq(101L),eq(Actor.AGENTE),any())).thenReturn(true);
        assertThrows(NoEncontradoException.class,()->servicio.consultarAcuerdoAceptado(10,null,ajeno));
    }
    @Test void noSeAsumeMonedaNiSeRedondeaUnImporteDeclarado(){
        var d=datos(10,0,"I",100,"95000",null,null);
        assertThrows(ReglaNegocioException.class,()->servicio.validarRegistroOferta(new DatosRegistroOferta(10L,0L,0L,d.proponente(),new Dinero(new BigDecimal("95000"),null,null,null),d.objeto(),null,d.fechaHecho(),d.evidencia(),null,null),h.agente));
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.dinero(new Dinero(new BigDecimal("95000.001"),"PEN",null,null)));
    }
    @Test void mismaClaveMismoContenidoDevuelveReciboOriginalSinOtroHito(){
        var a=abrir();contra(a,"98000");var repetida=abrir();assertEquals(a,repetida);assertEquals(2,filas.size());
        verify(precios,times(2)).registrarHitoOfrecido(any(),any(),anyLong(),any(),any(),any());
    }
    @Test void mismaClaveOtroContenidoEsConflictoNoOtroEfecto(){
        abrir();assertThrows(ConflictoException.class,()->servicio.registrarOferta(datos(10,0,"I",100,"96000",null,null),h.agente,h.sid,"oferta-1",h.procedencia));assertEquals(1,filas.size());
    }
    @Test void sesionRevocadaNoRecuperaNiReejecutaElRecibo(){
        abrir();doThrow(new AccesoNoAutorizadoException()).when(h.sesiones).exigirContexto(anyString(),anyLong(),any());
        assertThrows(AccesoNoAutorizadoException.class,this::abrir);assertEquals(1,filas.size());
    }
    @Test void aceptacionPerteneceALaContraparteYConservaAcuerdoExacto(){
        var a=abrir();var r=servicio.registrarAceptacion(aceptar(a,"P",200),h.agente,h.sid,"aceptacion",h.procedencia);
        assertEquals(Oferta.ACEPTADA,r.estado());assertNotNull(r.acuerdoAceptado());var acuerdo=servicio.consultarAcuerdoAceptado(10,null,h.agente).orElseThrow();
        assertEquals(a.oferta(),acuerdo.oferta());assertEquals(0,acuerdo.versionOferta());assertEquals(1,r.versionOferta());
        assertEquals(new ParteReal("P",200L),acuerdo.aceptacion().parte());assertEquals(new ParteReal("I",100L),acuerdo.propuesta().proponente());
        assertEquals(0,new BigDecimal("95000").compareTo(acuerdo.propuesta().importe().importe()));assertEquals(1L,acuerdo.idRolOperador());
    }
    @Test void elProponenteNoSeAceptaASiMismo(){
        var a=abrir();assertThrows(ReglaNegocioException.class,()->servicio.validarAceptacion(aceptar(a,"I",100),h.agente));
    }
    @Test void acuerdoAceptadoBloqueaLaMismaMesaYNoDesaparece(){
        var a=abrir();servicio.registrarAceptacion(aceptar(a,"P",200),h.agente,h.sid,"aceptada",h.procedencia);
        assertThrows(ConflictoException.class,()->servicio.validarRegistroOferta(datos(10,2,"I",100,"97000",a.oferta(),1L),h.agente));assertEquals(1,acuerdos.size());
    }
    @Test void noSeAceptaUnaPropuestaSuperada(){
        var a=abrir();contra(a,"98000");
        var vieja=new DatosAceptacion(10L,0L,2L,0L,a.oferta(),1L,new ParteReal("P",200L),LocalDate.of(2026,10,30),"TEST",null);
        assertThrows(ConflictoException.class,()->servicio.validarAceptacion(vieja,h.agente));
    }
    @Test void rechazoNoCierraTodaLaOportunidadNiCambiaPrecio(){
        var a=abrir();servicio.registrarRechazo(new DatosRechazo(10L,0L,1L,0L,a.oferta(),0L,new ParteReal("P",200L),LocalDate.of(2026,10,30),"TEST","No acepta"),h.agente,h.sid,"rechazo",h.procedencia);
        assertEquals(OportunidadComercial.ABIERTA,h.mesas.get(10L).estadoActual());assertEquals(Oferta.RECHAZADA,filas.get(a.oferta()).estadoActual());assertTrue(acuerdos.isEmpty());
    }
    @Test void retirarEsActoDelProponenteNoDeLaContraparte(){
        var a=abrir();assertThrows(ReglaNegocioException.class,()->servicio.validarRetirada(new DatosRetirada(10L,0L,1L,0L,a.oferta(),0L,new ParteReal("P",200L),LocalDate.of(2026,10,30),"TEST","Retira"),h.agente));
    }
    @Test void seRetomaConOtraVersionYConservaLaRetirada(){
        var a=abrir();var t=servicio.registrarRetirada(new DatosRetirada(10L,0L,1L,0L,a.oferta(),0L,new ParteReal("I",100L),LocalDate.of(2026,10,30),"TEST","Retira"),h.agente,h.sid,"retiro",h.procedencia);
        var b=servicio.registrarOferta(datos(10,2,"I",100,"95000",a.oferta(),t.versionOferta()),h.agente,h.sid,"retoma",h.procedencia);
        assertNotEquals(a.oferta(),b.oferta());assertEquals(Oferta.RETIRADA,filas.get(a.oferta()).estadoActual());
    }
    @Test void dosInteresadosSonMesasIndependientes(){
        h.mesas.put(11L,h.oportunidad(11,101));abrir();var otra=servicio.registrarOferta(datos(11,0,"I",101,"96000",null,null),h.agente,h.sid,"interesado-2",h.procedencia);
        assertEquals(11,otra.oportunidad());assertEquals(1,otra.versionMesa());assertEquals(2,filas.values().stream().filter(Oferta::estaViva).count());
    }
    @Test void unaAceptacionNoAutorizaDatosDelObjetoQueCambio(){
        var a=abrir();org.springframework.test.util.ReflectionTestUtils.setField(h.propiedad,"versionFisica",1L);
        assertThrows(ConflictoException.class,()->servicio.validarAceptacion(aceptar(a,"P",200),h.agente));assertTrue(acuerdos.isEmpty());
    }
    @Test void unCambioFisicoNoImpideRetirarLaPropuestaOriginalPermitida(){
        var a=abrir();org.springframework.test.util.ReflectionTestUtils.setField(h.propiedad,"versionFisica",1L);
        assertDoesNotThrow(()->servicio.validarRetirada(new DatosRetirada(10L,0L,1L,0L,a.oferta(),0L,new ParteReal("I",100L),LocalDate.of(2026,10,30),"TEST","Retira"),h.agente));
    }
    @Test void fuenteAntesDeMesaEvitaInvertirElOrdenDeC2(){
        abrir();var orden=inOrder(h.propiedades,h.captaciones,h.oportunidades,mesas);
        orden.verify(h.propiedades).bloquearParaEscritura(1,30);orden.verify(h.captaciones).bloquearParaEscritura(1,20);
        orden.verify(h.oportunidades).bloquear(1,10);orden.verify(mesas).bloquear(1,10);
    }
    @Test void falloAlGuardarNoRegistraExitoNiAcuerdo(){
        doThrow(new IllegalStateException("DB TEST")).when(ofertas).saveAndFlush(any());
        assertThrows(IllegalStateException.class,this::abrir);assertTrue(h.ejecutados.isEmpty());assertTrue(acuerdos.isEmpty());verifyNoInteractions(h.eventos);
    }
    @Test void lecturaDePropuestaNoExigeCasActualDeEscritura(){
        var a=abrir();contra(a,"98000");
        var datos=h.documentos.comoMapa(h.documentos.objetoCanonico(datos(10,0,"I",100,"95000",null,null)));
        assertDoesNotThrow(()->servicio.exigirLecturaActuacion("presentar_oferta",datos,h.agente));
    }
    @Test void ausenciaDeReferenciaNoSeConvierteEnPrecioCeroNiEnImporteOfrecido(){
        when(precios.congelarReferencia(any(),any(),any())).thenReturn(null);
        var r=abrir();assertFalse(filas.get(r.oferta()).tieneReferencia());assertNull(filas.get(r.oferta()).getMontoReferencia());
    }
}
