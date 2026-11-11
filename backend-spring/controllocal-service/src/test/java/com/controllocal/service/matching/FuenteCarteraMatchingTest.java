package com.controllocal.service.matching;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.inmueble.*;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.captura.*;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.encargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.controllocal.service.matching.ContratoMatching.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Proyección local: scope antes de EAV, lotes, conceptos reales y ausencia sin materialización. */
class FuenteCarteraMatchingTest {
    private static final Actor AGENTE=new Actor(1,2,3,Actor.AGENTE);
    private static final Instant CORTE=Instant.parse("2026-10-29T12:00:00Z");
    private final CaptacionRepository captaciones=mock(CaptacionRepository.class);
    private final ProspeccionRepository prospecciones=mock(ProspeccionRepository.class);
    private final AccesoEncargo acceso=mock(AccesoEncargo.class);
    private final EncargoService encargos=mock(EncargoService.class);
    private final AutoridadDePropiedad autoridad=mock(AutoridadDePropiedad.class);
    private final Alcances alcances=mock(Alcances.class);
    private final LectorPorAutoridad lector=mock(LectorPorAutoridad.class);
    private final AtributosGobernados atributos=mock(AtributosGobernados.class);
    private final AtributosDeEncargo condiciones=mock(AtributosDeEncargo.class);
    private final CatalogoAtributoRepository catalogo=mock(CatalogoAtributoRepository.class);
    private final SuficienciaRegistrada suficiencia=mock(SuficienciaRegistrada.class);
    private final MotorDeCaptura capturas=mock(MotorDeCaptura.class);
    private final DisponibilidadContextualService disponibilidad=mock(DisponibilidadContextualService.class);
    private final GobiernoCatalogoCapturaService vocabularios=mock(GobiernoCatalogoCapturaService.class);
    private final ResolverDeDistrito distritos=mock(ResolverDeDistrito.class);
    private final CondicionesDeEncargo pactos=new CondicionesDeEncargo(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    private final FuenteCarteraMatching fuente=new FuenteCarteraMatching(captaciones,prospecciones,acceso,encargos,autoridad,
            alcances,lector,atributos,condiciones,catalogo,suficiencia,capturas,disponibilidad,vocabularios,distritos,pactos);
    @BeforeEach void preparar(){
        when(lector.de(anyLong(),any(Propiedad.class))).thenReturn(ValoresGobernados.vacio());
        when(lector.deEncargo(anyLong())).thenReturn(ValoresGobernados.vacio());
        when(suficiencia.datos(any(Propiedad.class),any(ValoresGobernados.class))).thenReturn(Map.of());
        when(suficiencia.metadatos(any())).thenReturn(Map.of());
        when(atributos.definicionesParaLeer(anyLong(),any(),anyCollection())).thenReturn(Map.of());
        when(condiciones.definicionesParaLeer(anyLong(),any(Comercializacion.class),anyCollection())).thenReturn(Map.of());
        when(encargos.consultarVigencia(anyLong(),any(),eq(AGENTE))).thenReturn(new EncargoService.Vigencia(true,false,false,true,List.of(),LocalDate.of(2026,10,29)));
    }
    @Test void gobiernoNoLeeReferenciasNiAtributos() {
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.encargos(List.of(11L),new Actor(1,2,8,Actor.TENANT_ADMIN),CORTE));
        verifyNoInteractions(acceso,lector,captaciones,encargos);
    }
    @Test void denegacionDeEncargoPrecedeAHidratarAtributos() {
        when(acceso.leer(11,AGENTE)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.encargo(11,AGENTE,CORTE));verifyNoInteractions(lector,atributos,condiciones,disponibilidad);
    }
    @Test void permisoFisicoInsuficienteSeOmiteAntesDelLoteEav() {
        var p=propiedad(12);var c=encargo(11,p);when(acceso.leer(11,AGENTE)).thenReturn(c);when(autoridad.puedeLeer(AGENTE,12)).thenReturn(false);
        assertTrue(fuente.encargos(List.of(11L),AGENTE,CORTE).isEmpty());verifyNoInteractions(lector,atributos,condiciones,encargos);
    }
    @Test void eavDeCarteraUsaDosLotesYDefinicionesPorTipoOperacion() {
        var p1=propiedad(12);var p2=propiedad(14);var c1=encargo(11,p1);var c2=encargo(13,p2);
        when(acceso.leer(11,AGENTE)).thenReturn(c1);when(acceso.leer(13,AGENTE)).thenReturn(c2);
        when(autoridad.puedeLeer(eq(AGENTE),anyLong())).thenReturn(true);
        when(lector.deVarias(eq(1L),anyCollection())).thenReturn(Map.of(12L,ValoresGobernados.vacio(),14L,ValoresGobernados.vacio()));
        when(lector.deEncargos(anyCollection())).thenReturn(Map.of(11L,ValoresGobernados.vacio(),13L,ValoresGobernados.vacio()));
        var resultado=fuente.encargos(List.of(11L,13L),AGENTE,CORTE);assertEquals(2,resultado.size());
        verify(lector,times(1)).deVarias(eq(1L),anyCollection());verify(lector,times(1)).deEncargos(anyCollection());
        verify(lector,never()).de(anyLong(),any(Propiedad.class));verify(lector,never()).deEncargo(anyLong());
        verify(atributos,times(1)).definicionesParaLeer(eq(1L),eq("D"),anyCollection());
        verify(condiciones,times(1)).definicionesParaLeer(eq(1L),any(Comercializacion.class),anyCollection());
    }
    @Test void capturaParcialUsaDistritoRealSinCrearPropiedadEncargoNiOperacionFicticia() {
        var b=borrador(Map.of("tipoPropiedad","D","distrito","Santiago de Surco"));when(capturas.consultar(8,AGENTE)).thenReturn(b);
        when(distritos.idParaNombre("Santiago de Surco")).thenReturn(35L);
        var c=fuente.preliminar(CoincidenciaService.Origen.CAPTURA,8,1L,AGENTE,"VENTA",CORTE);
        assertEquals(Conclusion.NO_DETERMINADO,c.oferta().habilitacion());
        assertTrue(c.oferta().datos().stream().anyMatch(d->DISTRITO.equals(d.clave())&&"35".equals(d.valor().texto())));
        assertTrue(c.oferta().datos().stream().noneMatch(d->OPERACION.equals(d.clave())));
        verifyNoInteractions(acceso,encargos,captaciones,autoridad,disponibilidad);
    }
    @Test void capturaConTipoNombradoProyectaCodigoCanonicoSinModificarBorrador() {
        var conocidos=Map.<String,Object>of("tipoPropiedad","TERRENO","operaciones","ALQUILER");
        var b=borrador(conocidos);
        var contexto=new SuficienciaInmueble.Contexto("TERRENO",null,null,"ALQUILER",Map.of(),Set.of());
        when(b.contexto()).thenReturn(contexto);
        when(suficiencia.validarDatos(eq(1L),eq(contexto),anyMap())).thenReturn(Map.of());
        when(capturas.consultar(8,AGENTE)).thenReturn(b);
        var c=fuente.preliminar(CoincidenciaService.Origen.CAPTURA,8,1L,AGENTE,"ALQUILER",CORTE);
        var tipo=c.oferta().datos().stream().filter(d->TIPO_PROPIEDAD.equals(d.clave())).findFirst().orElseThrow();
        assertEquals("T",tipo.valor().texto());assertEquals(conocidos,b.conocido());
        assertEquals("TERRENO",b.contexto().tipoPropiedad());assertEquals(1,b.version());
        verifyNoInteractions(acceso,encargos,captaciones,autoridad,disponibilidad);
    }
    @Test void capturaConDosOperacionesNoUsaElPrecioDeVentaComoRenta() {
        var b=borrador(Map.of("tipoPropiedad","D","operaciones","VENTA,ALQUILER","importe:VENTA",new BigDecimal("100000"),"moneda:VENTA","USD"));
        when(capturas.consultar(8,AGENTE)).thenReturn(b);
        var venta=fuente.preliminar(CoincidenciaService.Origen.CAPTURA,8,1L,AGENTE,"VENTA",CORTE);
        var alquiler=fuente.preliminar(CoincidenciaService.Origen.CAPTURA,8,1L,AGENTE,"ALQUILER",CORTE);
        var precio=venta.oferta().datos().stream().filter(d->PRECIO_VENTA.equals(d.clave())).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("100000").compareTo(precio.valor().numero()));assertEquals("USD",precio.contexto().moneda());
        assertTrue(alquiler.oferta().datos().stream().noneMatch(d->PRECIO_VENTA.equals(d.clave())||TOTAL_MENSUAL_OBLIGATORIO.equals(d.clave())));
        assertNotEquals(venta.oferta().clave().configuracion(),alquiler.oferta().clave().configuracion());verifyNoInteractions(acceso,encargos,captaciones);
    }
    @Test void versionDeCapturaObsoletaNoMiraElCatalogo() {
        var b=borrador(Map.of());when(capturas.consultar(8,AGENTE)).thenReturn(b);
        assertThrows(ConflictoException.class,()->fuente.preliminar(CoincidenciaService.Origen.CAPTURA,8,0L,AGENTE,"VENTA",CORTE));verifyNoInteractions(catalogo,vocabularios,encargos);
    }
    @Test void composicionNoHeredaMatrizNiPrecioAnteriorSinTotalDeclarado() {
        var p=propiedad(12);var c=encargo(11,p);when(c.getIdComposicionActual()).thenReturn(19L);when(acceso.leer(11,AGENTE)).thenReturn(c);
        when(encargos.consultarVigencia(eq(11L),any(),eq(AGENTE))).thenReturn(new EncargoService.Vigencia(true,false,false,false,List.of(),LocalDate.of(2026,10,29)));
        var comp=new EncargoService.Composicion(19,11,2,List.of(new EncargoService.Unidad(12,null,null,"TEST"),new EncargoService.Unidad(14,null,null,"TEST")),null,null,"TEST");
        when(encargos.consultarComposicion(11,null,AGENTE)).thenReturn(comp);
        var resultado=fuente.encargo(11,AGENTE,CORTE);var precio=resultado.oferta().datos().stream().filter(d->PRECIO_VENTA.equals(d.clave())).findFirst().orElseThrow();
        assertNull(precio.valor());assertEquals(EstadoDato.FALTANTE,precio.metadatos().estado());assertEquals("COMPOSICION:19",precio.contexto().configuracion());
        verify(c,never()).getCondicionEconomica();verify(lector,never()).de(anyLong(),any(Propiedad.class));verifyNoInteractions(atributos,suficiencia);
    }
    @Test void composicionHabilitadaUsaPrecioDelPactoActualNoElTotalDelDesgloseAnterior() {
        var p=propiedad(12);var c=encargo(11,p);when(c.getIdComposicionActual()).thenReturn(19L);when(acceso.leer(11,AGENTE)).thenReturn(c);
        var pacto=mock(CondicionEconomicaCaptacion.class);when(pacto.getImporteReferencia()).thenReturn(new BigDecimal("200000"));
        when(pacto.getMonedaReferencia()).thenReturn("USD");when(pacto.getPeriodoPrecio()).thenReturn("TOTAL");when(c.getCondicionEconomica()).thenReturn(pacto);
        var comp=new EncargoService.Composicion(19,11,2,List.of(new EncargoService.Unidad(12,null,null,"TEST"),new EncargoService.Unidad(14,null,null,"TEST")),new BigDecimal("150000"),"USD","TEST");
        when(encargos.consultarComposicion(11,null,AGENTE)).thenReturn(comp);
        var precio=fuente.encargo(11,AGENTE,CORTE).oferta().datos().stream().filter(d->PRECIO_VENTA.equals(d.clave())).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("200000").compareTo(precio.valor().numero()));assertEquals("USD",precio.contexto().moneda());
        assertEquals("COMPOSICION:19",precio.contexto().configuracion());verify(lector,never()).de(anyLong(),any(Propiedad.class));
    }
    @Test void zonificacionConservaDefinicionRealYContextoPerfilSeparados() {
        var p=propiedad(12);var c=encargo(11,p);when(acceso.leer(11,AGENTE)).thenReturn(c);when(autoridad.puedeLeer(AGENTE,12)).thenReturn(true);
        var valores=ValoresGobernados.constructor().con("zonificacion",ValorLogico.deTexto("RDM")).construir();when(lector.de(1,p)).thenReturn(valores);
        var def=mock(CatalogoAtributo.class);when(def.getId()).thenReturn(7L);when(def.getClave()).thenReturn("zonificacion");when(def.getSujeto()).thenReturn("PROPIEDAD");when(def.getTipoDato()).thenReturn("TEXTO");when(def.getVersionDefinicion()).thenReturn(4L);
        when(atributos.definicionesParaLeer(eq(1L),eq("D"),anyCollection())).thenReturn(Map.of("zonificacion",def));
        var material=new SuficienciaInmueble.Dato("RDM",EstadoDato.CONOCIDO,"evidencia TEST",null,"CAT:P:8:2");when(suficiencia.datos(p,valores)).thenReturn(Map.of("zonificacion",material));
        var contexto=new SuficienciaInmueble.Contexto("D","VIVIENDA",null,"VENTA",Map.of(),Set.of(),"JURISDICCION_TEST");when(suficiencia.contexto((String)null)).thenReturn(contexto);
        var opciones=new GobiernoCatalogoCapturaService.OpcionesCatalogo("CAT:P:8:2",2L,List.of(new GobiernoCatalogoCapturaService.OpcionCatalogo("RDM","TEST")),null,"JURISDICCION_TEST","FUENTE_TEST_1");
        when(vocabularios.opcionesVigentes(1,"zonificacion","JURISDICCION_TEST")).thenReturn(opciones);
        var dato=fuente.encargo(11,AGENTE,CORTE).oferta().datos().stream().filter(d->ZONIFICACION.equals(d.clave())).findFirst().orElseThrow();
        assertEquals("CAT:D:7:4",dato.metadatos().referenciaDefinicion());assertEquals("CAT:P:8:2",dato.contexto().referenciaCatalogo());
        assertEquals("JURISDICCION_TEST",dato.contexto().jurisdiccion());assertEquals("FUENTE_TEST_1",dato.contexto().versionFuente());
    }
    @Test void totalMensualUnicoDelPactoNoSumaLaRentaOtraVezNiLeeUnCargoFueraDelEncargo() {
        alquiler(new EncargoService.ComposicionMensual(true,"Términos aceptados del alquiler",List.of(mensual("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))),"2500");
        var oferta=fuente.encargo(11,AGENTE,CORTE).oferta();var total=dato(oferta,TOTAL_MENSUAL_OBLIGATORIO);
        assertEquals(0,new BigDecimal("2700").compareTo(total.valor().numero()));assertFalse(total.intervalo());
        assertEquals("DOCUMENTO_ENCARGO:17",total.metadatos().evidenciaRef());assertEquals("UNIDAD:12",total.contexto().configuracion());
        assertEquals(0,new BigDecimal("2500").compareTo(dato(oferta,"ENCARGO:precio_pedido").valor().numero()));
        verify(lector,times(1)).deEncargo(11);verifyNoInteractions(captaciones,prospecciones);
    }
    @Test void mensualIncluidoSeCuentaUnaVezYPendienteNoSeConvierteEnCero() {
        alquiler(new EncargoService.ComposicionMensual(true,"Cuota incluida en la renta",List.of(mensual("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null),mensual("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.INCLUIDO_EN_OTRO,"RENTA_DEL_ENCARGO"))),"2500");
        assertEquals(0,new BigDecimal("2500").compareTo(dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO).valor().numero()));
        alquiler(new EncargoService.ComposicionMensual(true,"Cuota todavía desconocida",List.of(mensual("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null),mensual("cuota","MANTENIMIENTO",null,"PEN",TratamientoMensual.EXIGIBLE,null))),"2500");
        var parcial=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);assertTrue(parcial.intervalo());assertNull(parcial.hasta());
    }
    @Test void cuotaYPagadorEavNoCertificanQueNoExistanOtrosCargos() {
        alquiler(null,"2500");
        when(lector.deEncargo(11)).thenReturn(ValoresGobernados.constructor().con("mantenimiento_a_cargo_de",ValorLogico.deTexto("INQUILINO")).construir());
        var total=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);
        assertTrue(total.intervalo());assertNull(total.hasta());assertEquals(0,new BigDecimal("2500").compareTo(total.valor().numero()));
    }
    @Test void configuracionNuevaNoHeredaElTotalMensualDeLaUnidadAnterior() {
        var c=alquiler(new EncargoService.ComposicionMensual(true,"Oferta de una unidad",List.of(mensual("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))),"2500");
        when(c.getIdComposicionActual()).thenReturn(19L);
        when(encargos.consultarComposicion(11,null,AGENTE)).thenReturn(new EncargoService.Composicion(19,11,2,List.of(new EncargoService.Unidad(12,null,null,"TEST"),new EncargoService.Unidad(14,null,null,"TEST")),null,null,"TEST"));
        var total=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);
        assertNull(total.valor());assertEquals(Causa.CONFIGURACION_INCOMPATIBLE,total.metadatos().causa());assertEquals("COMPOSICION:19",total.contexto().configuracion());
    }
    @Test void declaracionNoHabilitadaConservaLaSeparacionEntreCapturaYAcreditacion() {
        alquiler(new EncargoService.ComposicionMensual(true,"Declaración todavía no revisada",List.of(mensual("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))),"2500");
        when(encargos.consultarVigencia(eq(11L),any(),eq(AGENTE))).thenReturn(new EncargoService.Vigencia(true,false,false,false,List.of("REVISION_APROBATORIA"),LocalDate.of(2026,10,29)));
        var oferta=fuente.encargo(11,AGENTE,CORTE).oferta();assertEquals(Conclusion.INCUMPLE,oferta.habilitacion());assertTrue(dato(oferta,TOTAL_MENSUAL_OBLIGATORIO).intervalo());
    }
    @Test void totalDeOtraMonedaNoSeConvierteNiRecibeCeroFicticio() {
        alquiler(new EncargoService.ComposicionMensual(true,"Oferta en su moneda original",List.of(mensual("total","TOTAL_MENSUAL_OBLIGATORIO","700","USD",TratamientoMensual.EXIGIBLE,null))),"2500");
        var total=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);assertNull(total.valor());assertEquals(Causa.MONEDA_NO_COMPARABLE,total.metadatos().causa());
    }
    @Test void tarifaPorMetroCuadradoNoRepresentaRentaNiTotalDelObjeto() {
        alquiler(null,"6");when(lector.deEncargo(11)).thenReturn(ValoresGobernados.constructor().con("modalidad_precio",ValorLogico.deTexto("POR_M2_AL_MES")).construir());
        var oferta=fuente.encargo(11,AGENTE,CORTE).oferta();var total=dato(oferta,TOTAL_MENSUAL_OBLIGATORIO);
        assertNull(total.valor());assertEquals(Causa.UNIDAD_INCOMPATIBLE,total.metadatos().causa());
        var tarifa=dato(oferta,"ENCARGO:precio_pedido");assertEquals("POR_M2_AL_MES",tarifa.contexto().concepto());assertEquals("m2",tarifa.contexto().unidad());assertEquals(0,new BigDecimal("6").compareTo(tarifa.valor().numero()));
    }
    @Test void totalAcreditadoDeConfiguracionCoexisteConTarifaPorMetroSinMultiplicarla() {
        alquiler(new EncargoService.ComposicionMensual(true,"Total de esta configuración expresamente declarado",List.of(mensual("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))),"6");
        when(lector.deEncargo(11)).thenReturn(ValoresGobernados.constructor().con("modalidad_precio",ValorLogico.deTexto("POR_M2_AL_MES")).construir());
        var total=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);assertFalse(total.intervalo());assertEquals(0,new BigDecimal("2700").compareTo(total.valor().numero()));
        verifyNoInteractions(captaciones,prospecciones);
    }
    @Test void cargosSeparadosDelPactoSiempreIncluyenLaRentaYaConocida() {
        alquiler(new EncargoService.ComposicionMensual(true,"Renta y mantenimiento separado",List.of(mensual("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.EXIGIBLE,null))),"2500");
        var total=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);assertFalse(total.intervalo());assertEquals(0,new BigDecimal("2700").compareTo(total.valor().numero()));
    }
    @Test void sinExtrasExpresamenteDeclaradosElTotalEsLaRentaNoCero() {
        alquiler(new EncargoService.ComposicionMensual(true,"Sin otros cargos mensuales",List.of()),"2500");
        var total=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);assertFalse(total.intervalo());assertEquals(0,new BigDecimal("2500").compareTo(total.valor().numero()));
    }
    @Test void tarifaPorMetroMasExtrasDeclaradosNoConfirmaUnTotalDelObjeto() {
        alquiler(new EncargoService.ComposicionMensual(true,"Tarifa por m² y cuota",List.of(mensual("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.EXIGIBLE,null))),"6");
        when(lector.deEncargo(11)).thenReturn(ValoresGobernados.constructor().con("modalidad_precio",ValorLogico.deTexto("POR_M2_AL_MES")).construir());
        var total=dato(fuente.encargo(11,AGENTE,CORTE).oferta(),TOTAL_MENSUAL_OBLIGATORIO);assertNull(total.valor());assertEquals(Causa.UNIDAD_INCOMPATIBLE,total.metadatos().causa());
    }
    private Captacion alquiler(EncargoService.ComposicionMensual mensual,String precio) {
        var c=encargo(11,propiedad(12));when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);when(c.getMotivoOperacion()).thenReturn("A");
        var condicion=pactos.nueva(1,OperacionInmobiliaria.ALQUILER,new EncargoService.Condiciones(new BigDecimal(precio),"PEN","MENSUAL","E","R",BigDecimal.ONE,"PEN","N",null,"Firma","Propietario","Agencia","Firma","Pacto documentado",List.of(),mensual,null));
        pactos.vincularMensual(condicion,"UNIDAD:12");when(c.getCondicionEconomica()).thenReturn(condicion);
        when(c.getFechaInicioVigencia()).thenReturn(LocalDate.of(2026,10,1));when(c.getFechaFinVigencia()).thenReturn(LocalDate.of(2026,11,30));when(c.getIdDocumentoActual()).thenReturn(17L);
        when(acceso.leer(11,AGENTE)).thenReturn(c);when(autoridad.puedeLeer(AGENTE,12)).thenReturn(true);return c;
    }
    private static EncargoService.ImporteMensual mensual(String ref,String concepto,String importe,String moneda,TratamientoMensual trato,String incluido) {
        return new EncargoService.ImporteMensual(ref,concepto,importe==null?null:new BigDecimal(importe),null,false,moneda,"MENSUAL",trato,incluido,null,null,null);
    }
    private static Dato dato(Oferta oferta,String clave){return oferta.datos().stream().filter(d->clave.equals(d.clave())).findFirst().orElseThrow();}
    private MotorDeCaptura.EstadoCaptura borrador(Map<String,Object> conocido) {
        var b=mock(MotorDeCaptura.EstadoCaptura.class);when(b.version()).thenReturn(1);when(b.conocido()).thenReturn(conocido);when(b.metadatos()).thenReturn(Map.of());
        var contexto=new SuficienciaInmueble.Contexto("D",null,null,null,Map.of(),Set.of());when(b.contexto()).thenReturn(contexto);
        when(suficiencia.datos(eq(conocido),anyMap())).thenReturn(Map.of());when(suficiencia.validarDatos(eq(1L),eq(contexto),anyMap())).thenReturn(Map.of());return b;
    }
    private static Propiedad propiedad(long id){var p=mock(Propiedad.class);when(p.getId()).thenReturn(id);when(p.getTipoInmueble()).thenReturn("D");when(p.getVersionFisica()).thenReturn(1L);return p;}
    private static Captacion encargo(long id,Propiedad p){var c=mock(Captacion.class);var agente=mock(DetalleAgente.class);when(agente.getId()).thenReturn(3L);
        when(c.getId()).thenReturn(id);when(c.getPropiedad()).thenReturn(p);when(c.getAgente()).thenReturn(agente);when(c.getVersion()).thenReturn(1L);when(c.getIdComposicionActual()).thenReturn(null);
        when(c.operacion()).thenReturn(OperacionInmobiliaria.VENTA);when(c.getMotivoOperacion()).thenReturn("V");when(c.getCodigoCaptacion()).thenReturn("TEST-"+id);return c;}
}

