package com.controllocal.service.documentos;

import com.controllocal.domain.auditoria.RastroValorGobernado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ComposicionEncargo;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.matching.ContratoMatching.TratamientoMensual;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pruebas de la frontera de proyección; no certifican PUB, SQL, render ni entrega real. */
class PlantillaComercialV08Test {
    private static final long ORG=1,ENCARGO=9,PROPIEDAD=7,RESPONSABLE=30;
    private static final LocalDate HOY=LocalDate.of(2026,10,31);
    private static final OffsetDateTime REGISTRO=OffsetDateTime.parse("2026-10-01T17:00:00Z");
    private static final OffsetDateTime ECONOMIA=OffsetDateTime.parse("2026-10-05T17:00:00Z");
    private final Actor actor=new Actor(ORG,10,RESPONSABLE,Actor.AGENTE);
    private final AccesoEncargo acceso=mock(AccesoEncargo.class);
    private final EncargoService ciclo=mock(EncargoService.class);
    private final PropiedadRepository propiedades=mock(PropiedadRepository.class);
    private final LectorPorAutoridad lector=mock(LectorPorAutoridad.class);
    private final AtributosGobernados fisicos=mock(AtributosGobernados.class);
    private final AtributosDeEncargo comerciales=mock(AtributosDeEncargo.class);
    private final RastroValorGobernadoRepository rastros=mock(RastroValorGobernadoRepository.class);
    private final CaptacionRepository.ContextoActualEncargo contexto=mock(CaptacionRepository.ContextoActualEncargo.class);
    private final ContextosDeActividad contextos=mock(ContextosDeActividad.class);
    private final PrecioPropiedadRepository precios=mock(PrecioPropiedadRepository.class);
    private final ComposicionEncargoRepository composiciones=mock(ComposicionEncargoRepository.class);
    private final SeleccionInmueblePieza seleccion=mock(SeleccionInmueblePieza.class);
    private final Captacion c=mock(Captacion.class,RETURNS_DEEP_STUBS);
    private final Propiedad p=mock(Propiedad.class);
    private final CondicionEconomicaCaptacion economia=mock(CondicionEconomicaCaptacion.class);
    private final ObjectMapper mapper=JsonMapper.builder().findAndAddModules().build();
    private final Map<String,CatalogoAtributo> definiciones=new LinkedHashMap<>();
    private final Map<String,CatalogoAtributo> condiciones=new LinkedHashMap<>();
    private final List<RastroValorGobernado> historia=new ArrayList<>();
    private EncargoService.ComposicionMensual mensual;
    private boolean habilitado=true;
    private PlantillaComercialV08 plantilla;

    @BeforeEach void preparar(){
        when(c.getId()).thenReturn(ENCARGO);when(c.getOrganizacionId()).thenReturn(ORG);
        when(c.getIdComposicionActual()).thenReturn(null);
        when(c.getPropiedad()).thenReturn(p);when(c.getVersion()).thenReturn(0L);when(c.getVersionContenido()).thenReturn(4L);
        when(c.getAgente().getId()).thenReturn(RESPONSABLE);
        when(c.getAgente().getRol().getPersona().getNombresORazonSocial()).thenReturn("Responsable actual");
        when(c.getCondicionEconomica()).thenReturn(economia);when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);
        when(c.getMotivoOperacion()).thenReturn("A");when(c.estadoActual()).thenReturn(Captacion.APROBADO);
        when(c.getFechaCaptacion()).thenReturn(LocalDate.of(2026,10,1));when(c.getFechaActualizacion()).thenReturn(REGISTRO.plusDays(25));
        when(economia.getId()).thenReturn(81L);when(economia.getFechaCreacion()).thenReturn(ECONOMIA);
        when(economia.getImporteReferencia()).thenReturn(new BigDecimal("1000"));when(economia.getMonedaReferencia()).thenReturn("USD");
        when(p.getId()).thenReturn(PROPIEDAD);when(p.getOrganizacionId()).thenReturn(ORG);when(p.getTipoInmueble()).thenReturn("D");
        when(p.getDistrito()).thenReturn("Surco");when(p.getFechaRegistro()).thenReturn(REGISTRO);when(p.getVersionFisica()).thenReturn(2L);
        when(p.getMetadatosAtributos()).thenReturn("{}");when(p.getDireccion()).thenReturn("Dirección privada 123 interior 402");
        when(acceso.leer(ENCARGO,actor)).thenReturn(c);
        when(contexto.getVersion()).thenReturn(0L);when(contexto.getNombreProfesional()).thenReturn("Responsable actual");
        when(contexto.getNombreOrganizacion()).thenReturn("Marca actual");when(contexto.getModalidadOrganizacion()).thenReturn("EMPRESARIAL");
        when(acceso.contexto(ENCARGO,actor)).thenReturn(contexto);
        when(propiedades.versionFisicaActual(ORG,PROPIEDAD)).thenReturn(Optional.of(2L));
        when(seleccion.elegir(c,null,actor)).thenReturn(new SeleccionInmueblePieza.Seleccion(p,null,true));
        when(seleccion.exigirLecturaHistorica(ORG,PROPIEDAD,actor)).thenReturn(p);
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.vacio());when(lector.deEncargo(ENCARGO)).thenReturn(ValoresGobernados.vacio());
        when(fisicos.definicionesDe(ORG,"D")).thenReturn(definiciones);
        when(comerciales.definicionesDe(eq(ORG),any(Comercializacion.class))).thenReturn(condiciones);
        when(rastros.historiaDelAgregado(ORG,"PROPIEDAD",PROPIEDAD)).thenReturn(historia);
        when(rastros.historiaDelAgregado(ORG,"ENCARGO",ENCARGO)).thenReturn(List.of());
        when(contextos.hoy()).thenReturn(HOY);
        when(ciclo.consultar(ENCARGO,actor)).thenAnswer(i->ficha());
        plantilla=new PlantillaComercialV08(acceso,ciclo,propiedades,lector,fisicos,comerciales,rastros,contextos,
                new Documentos(mapper),mapper,precios,composiciones,seleccion);
    }

    @Test void elExpedienteYLosContactosPersonalesNoEntranEnNingunaFinalidad() throws Exception {
        definir("partida_registral",null);definir("direccion",null);definir("titular",null);definir("dormitorios",null);
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor().con("partida_registral",ValorLogico.deTexto("12345678"))
                .con("direccion",ValorLogico.deTexto("Privada" )).con("titular",ValorLogico.deTexto("Persona privada"))
                .con("dormitorios",ValorLogico.deNumero(new BigDecimal("3"))).construir());
        historia.add(rastro("dormitorios"));
        for(String finalidad:List.of("REVISION_PRIVADA","DIFUSION_AUTORIZADA")){
            var datos=proyectar(finalidad,null).datos();String serializado=mapper.writeValueAsString(datos);
            assertFalse(serializado.contains("12345678"));assertFalse(serializado.contains("Persona privada"));
            assertFalse(serializado.contains("Dirección privada"));assertFalse(serializado.contains("partida_registral"));
            assertEquals("FALTANTE",mapa(datos,"marcaYContacto").get("estado"));
            assertEquals(new BigDecimal("3"),mapa(datos,"inmueble","atributos","dormitorios").get("valor"));
        }
        verify(c.getAgente().getRol().getPersona(),never()).getCorreo();
    }

    @Test void unaAusenciaOpcionalNoSolicitadaNoActivaFaltantesSiFalta(){
        definir("estacionamientos_incluidos",null);definir("cuota_mantenimiento",null);definir("dormitorios",null);
        assertTrue(mapa(proyectar().datos(),"inmueble","atributos").isEmpty());
        assertEquals(Map.of("estado","FALTANTE"),mapa(proyectar().datos(),"inmueble","ubicacion","zonaUrbanizacion"));
    }

    @Test void zonaConocidaConservaTextoYProcedenciaSinInventarUrbanizacion(){
        when(p.getZonaUrbanizacion()).thenReturn("Urbanización declarada TEST");
        var zona=mapa(proyectar().datos(),"inmueble","ubicacion","zonaUrbanizacion");
        assertEquals("VIGENTE",zona.get("estado"));assertEquals("Urbanización declarada TEST",zona.get("valor"));
        assertEquals(Map.of("registradoEn","2026-10-01"),zona.get("procedencia"));
        when(p.getZonaUrbanizacion()).thenReturn("   ");
        assertEquals(Map.of("estado","FALTANTE"),mapa(proyectar().datos(),"inmueble","ubicacion","zonaUrbanizacion"));
    }

    @Test void laPreparacionFisicaPrivadaReusaLaListaPermitidaSinEncargoFicticio(){
        definir("dormitorios",null);definir("partida_registral",null);
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor().con("dormitorios",ValorLogico.deNumero(new BigDecimal("2")))
                .con("partida_registral",ValorLogico.deTexto("SECRETO TEST")).construir());historia.add(rastro("dormitorios"));
        var fisica=plantilla.prepararFisicaPrivada(PROPIEDAD,actor);
        assertEquals(2L,fisica.versionFisica());assertEquals(Set.of("tipo","ubicacion","atributos"),fisica.datos().keySet());
        assertEquals(new BigDecimal("2"),mapa(fisica.datos(),"atributos","dormitorios").get("valor"));
        assertFalse(mapa(fisica.datos(),"atributos").containsKey("partida_registral"));
        verifyNoInteractions(acceso,ciclo,comerciales,precios,composiciones);
    }

    @Test void unaLecturaFisicaRevocadaNoSePreparaPorConocerSuId(){
        when(seleccion.exigirLecturaHistorica(ORG,PROPIEDAD,actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->plantilla.prepararFisicaPrivada(PROPIEDAD,actor));
        verifyNoInteractions(lector,ciclo);
    }

    @Test void desconocidoExplicitoSeDistingueDeCeroYNoAplicable(){
        definir("ascensores",null);definir("dormitorios",null);definir("estacionamientos",null);
        when(p.getMetadatosAtributos()).thenReturn("{\"dormitorios\":{\"estado\":\"FALTANTE\",\"noSabe\":true},\"estacionamientos\":{\"estado\":\"NO_APLICABLE\"}}");
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor().con("ascensores",ValorLogico.deNumero(BigDecimal.ZERO)).construir());
        historia.add(rastro("ascensores"));var a=mapa(proyectar().datos(),"inmueble","atributos");
        assertEquals(BigDecimal.ZERO,mapa(a,"ascensores").get("valor"));
        assertEquals("FALTANTE",mapa(a,"dormitorios").get("estado"));assertFalse(a.containsKey("estacionamientos"));
    }

    @Test void areasConservanConceptoUnidadYValorSinSumar(){
        definir("metraje_total","m²");definir("metraje_construido","m²");
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor().con("metraje_total",ValorLogico.deNumero(new BigDecimal("120")))
                .con("metraje_construido",ValorLogico.deNumero(new BigDecimal("200"))).construir());
        historia.add(rastro("metraje_total"));historia.add(rastro("metraje_construido"));
        var a=mapa(proyectar().datos(),"inmueble","atributos");
        assertEquals(new BigDecimal("120"),mapa(a,"metraje_total").get("valor"));assertEquals(new BigDecimal("200"),mapa(a,"metraje_construido").get("valor"));
        assertEquals("m²",mapa(a,"metraje_total").get("unidad"));assertEquals(2,a.size());
    }

    @ParameterizedTest
    @CsvSource({
            "D,metraje_total,AREA_CONSTRUIDA,Área construida",
            "C,metraje_total,AREA_CONSTRUIDA,Área construida",
            "T,metraje_total,AREA_TERRENO,Área de terreno",
            "L,metraje_total,AREA_TOTAL,Área total",
            "O,metraje_arrendable,AREA_ARRENDABLE,Área arrendable",
            "A,metraje_total,AREA_TOTAL,Área total",
            "E,metraje_total,AREA_ESTACIONAMIENTO,Área del estacionamiento",
            "X,metraje_total,AREA_OBJETO_OFRECIDO,Área del objeto ofrecido",
            "X,metraje_total,AREA_CONSTRUIDA,Área construida",
            "X,metraje_total,AREA_TERRENO,Área de terreno"})
    void superficieEnOchoTiposConservaClaveValorYFuenteConRotuloExplicito(String tipo,String clave,
            String concepto,String rotulo) throws Exception {
        when(p.getTipoInmueble()).thenReturn(tipo);
        when(fisicos.definicionesDe(ORG,tipo)).thenReturn(definiciones);
        definir(clave,"m²");historia.add(rastro(clave));
        when(p.getMetadatosAtributos()).thenReturn(mapper.writeValueAsString(Map.of(clave,Map.of("concepto",concepto))));
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor()
                .con(clave,ValorLogico.deNumero(new BigDecimal("120.50"))).construir());
        var datos=proyectar().datos();var atributos=mapa(datos,"inmueble","atributos");
        var area=mapa(atributos,clave);
        assertEquals(Set.of(clave),atributos.keySet());
        assertEquals(0,new BigDecimal("120.50").compareTo(assertInstanceOf(BigDecimal.class,area.get("valor"))));
        assertEquals("m²",area.get("unidad"));assertEquals(rotulo,area.get("rotulo"));
        assertEquals("Corresponde al "+rotulo.toLowerCase(Locale.ROOT)+".",area.get("definicion"));
        assertFalse(area.get("definicion").toString().contains("AREA_"));
        assertEquals("1.14.0",datos.get("versionContrato"));
        assertEquals(REGISTRO.toLocalDate().toString(),mapa(area,"procedencia").get("registradoEn"));
    }

    @ParameterizedTest @ValueSource(strings={"120.50","200.75"})
    void dosHechosDeConstruccionNoSeFusionanNiSobrescribenAunqueCoincidanLasCifras(String segundo) throws Exception {
        definir("metraje_total","m²");definir("metraje_construido","m²");
        historia.add(rastro("metraje_total"));var segundaFuente=rastro("metraje_construido");
        ReflectionTestUtils.setField(segundaFuente,"registradoEn",REGISTRO.plusDays(1));historia.add(segundaFuente);
        when(p.getMetadatosAtributos()).thenReturn(mapper.writeValueAsString(Map.of(
                "metraje_total",Map.of("concepto","AREA_CONSTRUIDA"),
                "metraje_construido",Map.of("concepto","AREA_CONSTRUIDA"))));
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor()
                .con("metraje_total",ValorLogico.deNumero(new BigDecimal("120.50")))
                .con("metraje_construido",ValorLogico.deNumero(new BigDecimal(segundo))).construir());
        var atributos=mapa(proyectar().datos(),"inmueble","atributos");
        assertEquals(Set.of("metraje_total","metraje_construido"),atributos.keySet());
        assertEquals(0,new BigDecimal("120.50").compareTo(assertInstanceOf(BigDecimal.class,mapa(atributos,"metraje_total").get("valor"))));
        assertEquals(0,new BigDecimal(segundo).compareTo(assertInstanceOf(BigDecimal.class,mapa(atributos,"metraje_construido").get("valor"))));
        assertEquals("Área construida",mapa(atributos,"metraje_total").get("rotulo"));
        assertEquals("Área construida",mapa(atributos,"metraje_construido").get("rotulo"));
        assertEquals(REGISTRO.toLocalDate().toString(),mapa(atributos,"metraje_total","procedencia").get("registradoEn"));
        assertEquals(REGISTRO.plusDays(1).toLocalDate().toString(),mapa(atributos,"metraje_construido","procedencia").get("registradoEn"));
    }

    @ParameterizedTest @ValueSource(strings={"{}","{\"metraje_total\":{\"concepto\":\"AREA_NO_DEFINIDA\"}}"})
    void conceptoDesconocidoNoFabricaRotuloTotalNiFiltraElCodigo(String metadatos) {
        definir("metraje_total","m²");historia.add(rastro("metraje_total"));
        when(p.getMetadatosAtributos()).thenReturn(metadatos);
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor()
                .con("metraje_total",ValorLogico.deNumero(new BigDecimal("120"))).construir());
        var area=mapa(proyectar().datos(),"inmueble","atributos","metraje_total");
        assertFalse(area.containsKey("rotulo"));assertEquals(new BigDecimal("120"),area.get("valor"));
        assertEquals("Superficie declarada, concepto por precisar.",area.get("definicion"));
    }

    @Test void metadatoDeConceptoNoPermiteRotularUnCampoQueNoEsSuperficie() {
        definir("dormitorios",null);historia.add(rastro("dormitorios"));
        when(p.getMetadatosAtributos()).thenReturn("{\"dormitorios\":{\"concepto\":\"AREA_TOTAL\"}}");
        when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor()
                .con("dormitorios",ValorLogico.deNumero(new BigDecimal("3"))).construir());
        var dato=mapa(proyectar().datos(),"inmueble","atributos","dormitorios");
        assertFalse(dato.containsKey("rotulo"));assertFalse(dato.containsKey("definicion"));
    }

    @Test void alquilerUsdYMantenimientoPenConservanAmbasMonedasSinTotalNiFx(){
        cuota("250","PEN");mensual=mensual("250","PEN","MENSUAL",TratamientoMensual.EXIGIBLE,null);
        var datos=proyectar().datos();var h=h18(datos);
        assertEquals(new BigDecimal("250"),mapa(datos,"inmueble","atributos","cuota_mantenimiento").get("valor"));
        assertEquals(Map.of("importe",new BigDecimal("250"),"moneda","PEN","periodo","MENSUAL"),h.get("valor"));
        assertEquals("USD",mapa(datos,"encargo","precio","moneda").get("valor"));
        assertEquals(new BigDecimal("1000"),mapa(datos,"encargo","precio","importe").get("valor"));
        assertFalse(mapa(datos,"encargo","precio").containsKey("total"));
    }

    @Test void incluidoNoSeDibujaComoPagoAdicionalYConservaImporteYMoneda(){
        cuota("250","PEN");mensual=mensual("250","PEN","MENSUAL",TratamientoMensual.INCLUIDO_EN_OTRO,"RENTA_DEL_ENCARGO");
        var datos=proyectar().datos();assertFalse(mapa(datos,"inmueble","atributos").containsKey("cuota_mantenimiento"));
        assertTrue(lista(datos,"inmueble","fueraDeCatalogo").isEmpty());
        assertEquals("La renta incluye el mantenimiento de 250 PEN al mes.",mapa(datos,"encargo","precio","sujeto").get("valor"));
        assertEquals(new BigDecimal("1000"),mapa(datos,"encargo","precio","importe").get("valor"));
    }

    @Test void referenciaExpresaDeLaRentaNoDependeDelLiteralPredeterminado(){
        cuota("250","PEN");
        var mantenimiento=mensual("250","PEN","MENSUAL",TratamientoMensual.INCLUIDO_EN_OTRO,"renta-declarada").componentes().getFirst();
        var renta=new EncargoService.ImporteMensual("renta-declarada","PRECIO_ALQUILER",new BigDecimal("1000"),null,false,
                "USD","MENSUAL",TratamientoMensual.EXIGIBLE,null,"Declaración vigente",null,null);
        mensual=new EncargoService.ComposicionMensual(true,"Declaración vigente",List.of(renta,mantenimiento));
        var datos=proyectar().datos();
        assertEquals("La renta incluye el mantenimiento de 250 PEN al mes.",mapa(datos,"encargo","precio","sujeto").get("valor"));
        assertFalse(mapa(datos,"inmueble","atributos").containsKey("cuota_mantenimiento"));
    }

    @Test void incluidoEnOtroComponenteNoSeAtribuyeALaRenta(){
        cuota("250","PEN");
        var mantenimiento=mensual("250","PEN","MENSUAL",TratamientoMensual.INCLUIDO_EN_OTRO,"servicios").componentes().getFirst();
        var servicios=new EncargoService.ImporteMensual("servicios","OTRO",new BigDecimal("400"),null,false,
                "PEN","MENSUAL",TratamientoMensual.EXIGIBLE,null,"Declaración vigente",null,null);
        mensual=new EncargoService.ComposicionMensual(true,"Declaración vigente",List.of(servicios,mantenimiento));
        var datos=proyectar().datos();
        assertEquals("VIGENTE",mapa(datos,"encargo","precio","sujeto").get("estado"));
        assertEquals("El mantenimiento de 250 PEN al mes está incluido en el componente mensual declarado de 400 PEN. No se cobra otra vez como mantenimiento.",
                mapa(datos,"encargo","precio","sujeto").get("valor"));
        assertFalse(mapa(datos,"inmueble","atributos").containsKey("cuota_mantenimiento"));
    }

    @Test void firmaActualNoReutilizaNombresGestionadosEnElPersistenceContext(){
        var contacto=new PiezasComercialesService.Contacto("CORREO","comercial@example.test");
        var anterior=proyectar("REVISION_PRIVADA",contacto);
        when(contexto.getNombreOrganizacion()).thenReturn("Marca modificada en otra transacción");
        when(contexto.getNombreProfesional()).thenReturn("Nombre profesional actual");
        var actual=proyectar("REVISION_PRIVADA",contacto);
        assertNotEquals(anterior.huellaOrigen(),actual.huellaOrigen());
        assertEquals("Marca modificada en otra transacción",mapa(actual.datos(),"marcaYContacto").get("organizacion"));
        assertEquals("Nombre profesional actual",mapa(actual.datos(),"marcaYContacto").get("nombre"));
    }

    @Test void unaDeclaracionMensualConocidaNoDependeDeInventarCuotaFisica(){
        mensual=mensual("250","PEN","MENSUAL",TratamientoMensual.EXIGIBLE,null);
        assertEquals("PEN",mapa(h18(proyectar().datos()),"valor").get("moneda"));
    }

    @Test void periodoTratamientoVigenciaOImporteNoAcreditadosQuedanExplicitos(){
        cuota("250","PEN");
        for(var m:List.of(mensual("250","PEN",null,TratamientoMensual.EXIGIBLE,null),
                mensual("250","PEN","MENSUAL",null,null),mensual("251","PEN","MENSUAL",TratamientoMensual.EXIGIBLE,null))){
            mensual=m;var datos=proyectar().datos();assertTrue(lista(datos,"inmueble","fueraDeCatalogo").isEmpty());
            assertEquals("FALTANTE",mapa(datos,"encargo","precio","sujeto").get("estado"));
        }
        mensual=null;assertEquals("FALTANTE",mapa(proyectar().datos(),"encargo","precio","sujeto").get("estado"));
    }

    @Test void versionCasCeroConservaVersionMaterialSinDesplazamiento(){
        assertEquals(4L,mapa(proyectar().datos(),"encargo").get("version"));
        when(propiedades.versionFisicaActual(ORG,PROPIEDAD)).thenReturn(Optional.of(3L));
        assertThrows(ConflictoException.class,this::proyectar);
    }

    @Test void precioAutorizadoNoRejuveneceConEdicionIrrelevanteDelEncargo(){
        var u=PrecioPropiedad.hito(ORG,PROPIEDAD,OperacionInmobiliaria.ALQUILER,"U","USD",new BigDecimal("1000"),LocalDate.of(2026,10,2)).delEncargo(ENCARGO);
        ReflectionTestUtils.setField(u,"fechaCreacion",REGISTRO.plusDays(2));
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(ENCARGO,"U")).thenReturn(Optional.of(u));
        var inicial=mapa(proyectar().datos(),"encargo","precio","importe","procedencia");
        when(c.getFechaActualizacion()).thenReturn(REGISTRO.plusDays(100));
        assertEquals(inicial,mapa(proyectar().datos(),"encargo","precio","importe","procedencia"));
        assertEquals("2026-10-02",inicial.get("observadoEn"));
    }

    @Test void hitoDeOtroObjetoOImporteNoAcreditaElPrecioActual(){
        var u=PrecioPropiedad.hito(ORG,PROPIEDAD,OperacionInmobiliaria.ALQUILER,"U","PEN",new BigDecimal("1000"),LocalDate.of(2026,10,2)).delEncargo(ENCARGO);
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(ENCARGO,"U")).thenReturn(Optional.of(u));
        var f=mapa(proyectar().datos(),"encargo","precio","importe","procedencia");
        assertEquals("2026-10-05",f.get("registradoEn"));assertFalse(f.containsKey("observadoEn"));
    }

    @Test void fechaNoLocalizadaNoSeFabricaConHoyNiConFechaContenedora(){
        when(economia.getFechaCreacion()).thenReturn(null);var precio=mapa(proyectar().datos(),"encargo","precio","importe");
        assertEquals("FALTANTE",precio.get("estado"));assertTrue(precio.get("motivo").toString().contains("fecha"));
        assertFalse(precio.containsKey("valor"));
    }

    @Test void contactoEsEleccionExpresaDeLaPiezaYResponsableActual(){
        var contacto=new PiezasComercialesService.Contacto("CORREO","comercial@example.test");
        var firma=mapa(proyectar("REVISION_PRIVADA",contacto).datos(),"marcaYContacto");
        assertEquals("Responsable actual",firma.get("nombre"));assertEquals("Marca actual",firma.get("organizacion"));
        assertEquals("comercial@example.test",((Map<?,?>)((List<?>)firma.get("contactos")).getFirst()).get("valor"));
        assertEquals("FALTANTE",mapa(proyectar().datos(),"marcaYContacto").get("estado"));
    }

    @Test void conjuntoConservaObjetoVersionYUnidadDescritaSinRepartirTotal(){
        composicion(true);var datos=proyectar().datos();
        assertEquals("El precio corresponde al conjunto de 2 inmuebles. Los datos físicos y las fotografías describen una unidad de ese conjunto.",mapa(datos,"encargo","precio","sujeto").get("valor"));
        assertEquals(new BigDecimal("1000"),mapa(datos,"encargo","precio","importe").get("valor"));
        assertTrue(lista(datos,"pieza","versionesOrigen").stream().anyMatch(x->"COMPOSICION:51".equals(mapa(x).get("fuente"))&&"Versión 3".equals(mapa(x).get("alcance"))));
        verifyNoInteractions(precios);
        verify(composiciones,times(1)).findByOrganizacionIdAndId(ORG,51);
    }

    @Test void conjuntoEnPreparacionNoHeredaPrecioAnteriorNiTotalDelDesglose(){
        composicion(true);habilitado=false;var precio=mapa(proyectar().datos(),"encargo","precio","importe");
        assertEquals("FALTANTE",precio.get("estado"));assertFalse(precio.containsKey("valor"));
    }

    @Test void unidadFueraDelObjetoActualDeclaraElAlcanceFaltante(){
        composicion(false);var datos=proyectar().datos();
        assertEquals("FALTANTE",mapa(datos,"encargo","precio","sujeto").get("estado"));
        assertTrue(mapa(datos,"encargo","precio","sujeto").get("motivo").toString().contains("no pertenecen"));
        assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->proyectar("DIFUSION_AUTORIZADA",null));
    }

    @Test void helperExigeLecturaActualDelRecursoSinDerivarPermisoDeComposicion(){
        var autoridad=mock(AutoridadDePropiedad.class);var helper=new SeleccionInmueblePieza(ciclo,autoridad);
        when(c.getIdComposicionActual()).thenReturn(51L);when(ciclo.consultarComposicion(ENCARGO,null,actor)).thenReturn(comp());
        when(autoridad.buscarParaLectura(actor,8L)).thenReturn(Optional.empty());
        assertThrows(AccesoNoAutorizadoException.class,()->helper.elegir(c,8L,actor));
        verify(autoridad).buscarParaLectura(actor,8L);
        var otra=mock(Propiedad.class);when(otra.getId()).thenReturn(8L);when(otra.getOrganizacionId()).thenReturn(ORG);
        when(autoridad.buscarParaLectura(actor,8L)).thenReturn(Optional.of(otra));
        assertTrue(helper.elegir(c,8L,actor).pertenece());
        when(ciclo.consultarComposicion(ENCARGO,null,actor)).thenReturn(new EncargoService.Composicion(52,ENCARGO,3,List.of(),null,null,null));
        assertThrows(ConflictoException.class,()->helper.elegir(c,8L,actor));
    }

    @Test void helperHistoricoNoConcedeLecturaAGobiernoAunqueElLookupSeaPermisivo(){
        var autoridad=mock(AutoridadDePropiedad.class);var helper=new SeleccionInmueblePieza(ciclo,autoridad);
        var gobierno=new Actor(ORG,10,RESPONSABLE,Actor.TENANT_ADMIN);
        when(autoridad.buscarParaLectura(gobierno,PROPIEDAD)).thenReturn(Optional.of(p));
        assertThrows(AccesoNoAutorizadoException.class,()->helper.exigirLecturaHistorica(ORG,PROPIEDAD,gobierno));
        verify(autoridad,never()).buscarParaLectura(any(),anyLong());
    }

    @Test void helperSinComposicionRechazaOtroInmuebleAunqueSeaLegible(){
        var autoridad=mock(AutoridadDePropiedad.class);var helper=new SeleccionInmueblePieza(ciclo,autoridad);
        assertThrows(AccesoNoAutorizadoException.class,()->helper.elegir(c,8L,actor));
        verifyNoInteractions(autoridad);
        verify(ciclo,never()).consultarComposicion(anyLong(),any(),any());
    }

    @Test void helperNoMiembroExplicitoNoObtieneSuFichaFisica(){
        var autoridad=mock(AutoridadDePropiedad.class);var helper=new SeleccionInmueblePieza(ciclo,autoridad);
        when(c.getIdComposicionActual()).thenReturn(51L);when(ciclo.consultarComposicion(ENCARGO,null,actor)).thenReturn(comp());
        assertThrows(AccesoNoAutorizadoException.class,()->helper.elegir(c,99L,actor));
        verifyNoInteractions(autoridad);
    }

    @Test void helperPrimariaHistoricaSoloSeRevisaComoSeleccionImplicita(){
        var autoridad=mock(AutoridadDePropiedad.class);var helper=new SeleccionInmueblePieza(ciclo,autoridad);
        var actual=new EncargoService.Composicion(51,ENCARGO,3,List.of(new EncargoService.Unidad(8,null,null,null)),null,null,"Composición actual");
        when(c.getIdComposicionActual()).thenReturn(51L);when(ciclo.consultarComposicion(ENCARGO,null,actor)).thenReturn(actual);
        when(autoridad.buscarParaLectura(actor,PROPIEDAD)).thenReturn(Optional.of(p));
        assertFalse(helper.elegir(c,null,actor).pertenece());
        assertThrows(AccesoNoAutorizadoException.class,()->helper.elegir(c,PROPIEDAD,actor));
    }

    @Test void snapshotConservadoAdmiteUnidadFueraDelObjetoSoloConLecturaFisicaActual(){
        var autoridad=mock(AutoridadDePropiedad.class);var helper=new SeleccionInmueblePieza(ciclo,autoridad);
        var actual=new EncargoService.Composicion(51,ENCARGO,3,List.of(new EncargoService.Unidad(8,null,null,null)),null,null,"Composición actual");
        when(c.getIdComposicionActual()).thenReturn(51L);when(ciclo.consultarComposicion(ENCARGO,null,actor)).thenReturn(actual);
        when(autoridad.buscarParaLectura(actor,PROPIEDAD)).thenReturn(Optional.of(p));
        assertFalse(helper.revisarHistorica(c,PROPIEDAD,actor).pertenece());
        assertSame(p,helper.exigirLecturaHistorica(ORG,PROPIEDAD,actor));
        when(autoridad.buscarParaLectura(actor,PROPIEDAD)).thenReturn(Optional.empty());
        assertThrows(AccesoNoAutorizadoException.class,()->helper.revisarHistorica(c,PROPIEDAD,actor));
        assertThrows(AccesoNoAutorizadoException.class,()->helper.exigirLecturaHistorica(ORG,PROPIEDAD,actor));
    }

    @Test void helperComparaVersionMaterialC2YNoElCasDeReasignacion(){
        var autoridad=mock(AutoridadDePropiedad.class);var helper=new SeleccionInmueblePieza(ciclo,autoridad);
        when(c.getIdComposicionActual()).thenReturn(51L);when(c.getVersionContenido()).thenReturn(20L);
        var actual=new EncargoService.Composicion(51,ENCARGO,19,comp().unidades(),null,null,"Composición actual");
        when(ciclo.consultarComposicion(ENCARGO,null,actor)).thenReturn(actual);
        when(autoridad.buscarParaLectura(actor,PROPIEDAD)).thenReturn(Optional.of(p));
        assertTrue(helper.elegir(c,null,actor).pertenece());
        when(c.getVersion()).thenReturn(40L);assertTrue(helper.elegir(c,null,actor).pertenece());
        when(ciclo.consultarComposicion(ENCARGO,null,actor)).thenReturn(new EncargoService.Composicion(51,ENCARGO,21,comp().unidades(),null,null,"Composición futura"));
        assertThrows(ConflictoException.class,()->helper.elegir(c,null,actor));
    }

    @Test void reasignacionRevalidaResponsableAntesDeProyectarLaFicha(){
        doThrow(new AccesoNoAutorizadoException()).when(acceso).exigirResponsable(c,actor);
        assertThrows(AccesoNoAutorizadoException.class,this::proyectar);
        verifyNoInteractions(seleccion,lector);
    }

    @Test void identificadoresLargosSoloViajanComoReferenciasTecnicas(){
        var comp=new EncargoService.Composicion(123456789,ENCARGO,3,List.of(new EncargoService.Unidad(PROPIEDAD,null,null,null),new EncargoService.Unidad(987654321,null,null,null)),null,null,"Objeto actual");
        when(c.getIdComposicionActual()).thenReturn(comp.id());when(seleccion.elegir(c,null,actor)).thenReturn(new SeleccionInmueblePieza.Seleccion(p,comp,true));
        var original=ComposicionEncargo.declarar(ORG,ENCARGO,3,"[]",null,null,comp.procedencia(),RESPONSABLE,REGISTRO);
        ReflectionTestUtils.setField(original,"id",comp.id());when(composiciones.findByOrganizacionIdAndId(ORG,comp.id())).thenReturn(Optional.of(original));
        var datos=proyectar().datos();String texto=mapa(datos,"encargo","precio","sujeto").get("valor").toString();
        assertFalse(texto.matches(".*[0-9]{8,}.*"));
        assertTrue(lista(datos,"pieza","versionesOrigen").stream().anyMatch(x->"COMPOSICION:123456789".equals(mapa(x).get("fuente"))));
        verify(composiciones,times(1)).findByOrganizacionIdAndId(ORG,comp.id());
    }

    private EncargoService.Ficha ficha(){
        var cond=new EncargoService.Condiciones(new BigDecimal("1000"),"USD","MENSUAL",null,null,null,null,null,null,null,null,null,null,null,null,mensual,null);
        var v=new EncargoService.Vigencia(habilitado,false,false,habilitado,List.of(),HOY);
        return new EncargoService.Ficha(ENCARGO,"ENC-9","APROBADO",0,RESPONSABLE,HOY.minusDays(30),HOY.plusMonths(3),cond,v,null,null,c.getIdComposicionActual(),null,PROPIEDAD,"ALQUILER",List.of());
    }
    private void definir(String clave,String unidad){var d=mock(CatalogoAtributo.class);when(d.getClave()).thenReturn(clave);when(d.isActivo()).thenReturn(true);when(d.getUnidad()).thenReturn(unidad);when(d.getVersionDefinicion()).thenReturn(1L);definiciones.put(clave,d);}
    private RastroValorGobernado rastro(String clave){var r=new RastroValorGobernado(ORG,"PROPIEDAD",PROPIEDAD,clave,"DECLARAR");ReflectionTestUtils.setField(r,"registradoEn",REGISTRO);return r;}
    private void cuota(String importe,String moneda){definir("cuota_mantenimiento",null);historia.add(rastro("cuota_mantenimiento"));when(lector.de(ORG,p)).thenReturn(ValoresGobernados.constructor().con("cuota_mantenimiento",ValorLogico.deImporte(new BigDecimal(importe),moneda)).construir());}
    private EncargoService.ComposicionMensual mensual(String importe,String moneda,String periodo,TratamientoMensual tratamiento,String incluido){return new EncargoService.ComposicionMensual(true,"Declaración del mismo Encargo",List.of(new EncargoService.ImporteMensual("cuota","MANTENIMIENTO",new BigDecimal(importe),null,false,moneda,periodo,tratamiento,incluido,"Declaración del mismo Encargo",null,null)));}
    private EncargoService.Composicion comp(){return new EncargoService.Composicion(51,ENCARGO,3,List.of(new EncargoService.Unidad(PROPIEDAD,null,null,null),new EncargoService.Unidad(8,null,null,null)),new BigDecimal("9999"),"PEN","Desglose declarado");}
    private void composicion(boolean pertenece){var base=comp();var comp=pertenece?base:new EncargoService.Composicion(base.id(),base.encargo(),base.version(),List.of(new EncargoService.Unidad(8,null,null,null)),base.totalDeclarado(),base.moneda(),base.procedencia());when(c.getIdComposicionActual()).thenReturn(51L);when(seleccion.elegir(c,null,actor)).thenReturn(new SeleccionInmueblePieza.Seleccion(p,comp,pertenece));var original=ComposicionEncargo.declarar(ORG,ENCARGO,3,"[]",comp.totalDeclarado(),comp.moneda(),comp.procedencia(),RESPONSABLE,REGISTRO.plusDays(4));ReflectionTestUtils.setField(original,"id",51L);when(composiciones.findByOrganizacionIdAndId(ORG,51)).thenReturn(Optional.of(original));}
    private PlantillaComercialV08.Proyeccion proyectar(){return proyectar("REVISION_PRIVADA",null);}
    private PlantillaComercialV08.Proyeccion proyectar(String finalidad,PiezasComercialesService.Contacto contacto){return plantilla.proyectar(new PiezasComercialesService.Preparacion(ENCARGO,0,2,"CANAL",List.of(),"galeria",PiezasComercialesService.Formato.PDF_A4,"marfil",contacto,null),UUID.fromString("00000000-0000-0000-0000-000000000001"),1,finalidad,actor);}
    @SuppressWarnings("unchecked") private static Map<String,Object> mapa(Object datos,String... ruta){Object valor=datos;for(String clave:ruta)valor=((Map<String,Object>)valor).get(clave);return (Map<String,Object>)valor;}
    @SuppressWarnings("unchecked") private static List<Object> lista(Object datos,String... ruta){Object valor=datos;for(String clave:ruta)valor=((Map<String,Object>)valor).get(clave);return (List<Object>)valor;}
    private static Map<String,Object> h18(Map<String,Object> datos){return lista(datos,"inmueble","fueraDeCatalogo").stream().map(x->mapa(x)).filter(x->"H-18".equals(x.get("hueco"))).findFirst().orElseThrow();}
}
