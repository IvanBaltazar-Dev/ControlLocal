package com.controllocal.service.captura;

import com.controllocal.service.captura.SuficienciaInmueble.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SuficienciaInmuebleTest {
    private final SuficienciaInmueble regla = new SuficienciaInmueble();

    @Test void guardarNoExigeIdentidadNiCamposDelEncargo() {
        assertTrue(regla.evaluar(Accion.GUARDAR, null, Map.of()).suficiente());
        assertTrue(regla.evaluar(Accion.GUARDAR, null, Map.of("distrito", Dato.conocido("Surco"))).suficiente());
    }

    @ParameterizedTest @ValueSource(strings={"DEPARTAMENTO","CASA","TERRENO","LOCAL","OFICINA","ALMACEN","ESTACIONAMIENTO","OTRO"})
    void identificarNoExigeAreaZonificacionTitularPrecioNiEncargo(String tipo) {
        var contexto = new Contexto(tipo, null, "Objeto identificado 501", null, Map.of(), Set.of());
        assertTrue(regla.evaluar(Accion.IDENTIFICAR, contexto, Map.of("distrito", Dato.conocido("Surco"),
                "referenciaInterna", Dato.conocido("MzA lote5/unidad501"))).suficiente());
    }

    @Test void edificioSoloNoIdentificaElDepartamento() {
        var contexto = new Contexto("DEPARTAMENTO", null, null, null, Map.of(), Set.of());
        var evaluacion = regla.evaluar(Accion.IDENTIFICAR, contexto,
                Map.of("distrito", Dato.conocido("Surco"), "direccion", Dato.conocido("AvPrueba123")));
        assertTrue(evaluacion.faltantes().stream().anyMatch(f -> f.motivo()==Motivo.UNIDAD_NO_IDENTIFICADA));
    }

    @ParameterizedTest @ValueSource(strings={"DEPARTAMENTO","CASA","TERRENO","LOCAL","OFICINA","ALMACEN","ESTACIONAMIENTO","OTRO"})
    void encargoExigeLosDatosPertinentesDelTipo(String tipo) {
        var datos = completos(tipo);
        var evaluacion = regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto(tipo), datos);
        assertTrue(evaluacion.suficiente(), evaluacion.faltantes().toString());
        datos.remove("metraje_total");
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto(tipo), datos).suficiente());
    }

    @Test void terrenoSinZonificacionSeIdentificaPeroNoHabilitaEncargo() {
        var datos = completos("TERRENO"); datos.remove("zonificacion");
        assertTrue(regla.evaluar(Accion.IDENTIFICAR, contexto("TERRENO"), datos).suficiente());
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("TERRENO"), datos).faltantes().stream()
                .anyMatch(f -> f.clave().equals("zonificacion")));
    }

    @Test void departamentoYOficinaOrdinariosNoExigenZonificacion() {
        for (String tipo : new String[]{"DEPARTAMENTO", "OFICINA"}) {
            var evaluacion = regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto(tipo), completos(tipo));
            assertTrue(evaluacion.suficiente(), evaluacion.faltantes().toString());
            assertFalse(evaluacion.aplicables().stream().anyMatch(c -> c.clave().equals("zonificacion")));
        }
    }

    @Test void datoFaltanteNoEsCeroNiAusenciaDeclarada() {
        var datos = completos("DEPARTAMENTO"); datos.put("ascensores", Dato.conocido(0));
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
        datos.put("ascensores", Dato.faltante());
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
        datos.put("ascensores", new Dato(null, EstadoDato.AUSENCIA_DECLARADA, "declaracion", null, null));
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
    }

    @Test void noAplicableNecesitaRespaldoYNoSustituyeUnArea() {
        var datos = completos("DEPARTAMENTO");
        datos.put("piso", new Dato(null, EstadoDato.NO_APLICABLE, null, null, null));
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
        datos.put("piso", new Dato(null, EstadoDato.NO_APLICABLE, "regimenAcreditado", null, null));
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
        datos.put("metraje_total", new Dato(null, EstadoDato.NO_APLICABLE, "nota", null, null));
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
    }

    @Test void terrenoAdmiteAusenciaDeclaradaDeServicios() {
        var datos = completos("TERRENO");
        datos.put("agua_desague", new Dato(null, EstadoDato.AUSENCIA_DECLARADA, "fuente", null, null));
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("TERRENO"), datos).suficiente());
        datos.put("agua_desague", Dato.faltante());
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("TERRENO"), datos).suficiente());
    }

    @Test void noIntercambiaAreaTerrenoConUnidadNiInventAConstruida() {
        var datos = completos("DEPARTAMENTO");
        datos.put("metraje_total", area(80, SuficienciaInmueble.AREA_TERRENO));
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
        datos.remove("metraje_total"); datos.put("area_terreno", area(80, SuficienciaInmueble.AREA_TERRENO));
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
        datos.put("metraje_construido", area(75, SuficienciaInmueble.AREA_CONSTRUIDA));
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).suficiente());
    }

    @Test void totalSinConceptoNoSeConvierteYConstruidaPuedeSuperarTerreno() {
        var datos = completos("CASA");
        datos.put("metraje_total", Dato.conocido(100));
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("CASA"), datos).suficiente());
        datos.put("area_terreno", area(120, SuficienciaInmueble.AREA_TERRENO));
        datos.put("metraje_construido", area(200, SuficienciaInmueble.AREA_CONSTRUIDA));
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("CASA"), datos).suficiente());
        assertEquals(3, datos.keySet().stream().filter(k -> k.startsWith("metraje") || k.equals("area_terreno")).count());
    }

    @Test void aplicabilidadDesconocidaNoSeTomaPorNoAplicable() {
        var base = contexto("LOCAL");
        var contexto = new Contexto(base.tipoPropiedad(), base.usoOfrecido(), base.objetoOfrecido(),
                base.operacion(), Map.of(), Set.of());
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto, completos("LOCAL")).faltantes().stream()
                .anyMatch(f -> f.motivo()==Motivo.CONTEXTO_NO_PRECISADO));
    }

    @Test void soloPrestacionOfrecidaSeExigeAdemasDeLosMinimos() {
        var base = contexto("ALMACEN");
        var contexto = new Contexto(base.tipoPropiedad(), base.usoOfrecido(), base.objetoOfrecido(),
                base.operacion(), base.aplicabilidad(), Set.of("altura_libre"));
        var datos = completos("ALMACEN");
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto, datos).suficiente());
        datos.put("altura_libre", Dato.conocido(6));
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto, datos).suficiente());
    }

    @ParameterizedTest @ValueSource(strings={"DEPARTAMENTO","CASA","TERRENO","LOCAL","OFICINA","ALMACEN","ESTACIONAMIENTO","OTRO"})
    void guardarYPublicarNoConfundenSusSuficiencias(String tipo) {
        var parcial = Map.of("distrito", Dato.conocido("Surco"));
        assertTrue(regla.evaluar(Accion.GUARDAR, contexto(tipo), parcial).suficiente());
        assertFalse(regla.evaluar(Accion.PUBLICAR, contexto(tipo), parcial).suficiente());
        var completos = completos(tipo);
        var publicacion = regla.evaluar(Accion.PUBLICAR, contexto(tipo), completos);
        assertTrue(publicacion.suficiente(), publicacion.faltantes().toString());
        assertEquals(Accion.PUBLICAR, publicacion.accion());
        assertEquals(SuficienciaInmueble.VERSION_REGLA, publicacion.versionRegla());
    }

    @Test void usoObjetoYOperacionNoSeDeducenDelTipoFisico() {
        var contexto = new Contexto("CASA", null, null, null, Map.of(), Set.of());
        var evaluacion = regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto, completos("CASA"));
        for (String clave : new String[]{"uso", "objetoOfrecido", "operacion"}) {
            assertTrue(evaluacion.faltantes().stream().anyMatch(f -> clave.equals(f.clave())
                    && f.motivo() == Motivo.CONTEXTO_NO_PRECISADO), clave);
        }
    }

    @Test void noAplicarUnaCoordenadaExigeRespaldoAunqueNoFalteSuDatoFisico() {
        var datos = completos("OFICINA");
        datos.remove("contexto." + SuficienciaInmueble.MANTENIMIENTO);
        var evaluacion = regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("OFICINA"), datos);
        assertTrue(evaluacion.faltantes().stream().anyMatch(f ->
                f.clave().equals("contexto." + SuficienciaInmueble.MANTENIMIENTO)
                        && f.motivo() == Motivo.NO_APLICABLE_SIN_EVIDENCIA));
    }

    @Test void unTotalSinDesgloseSeGuardaSinSatisfacerUnConceptoMaterialDesconocido() {
        var datos = completos("DEPARTAMENTO");
        datos.put("metraje_total", Dato.conocido(100));
        assertTrue(regla.evaluar(Accion.GUARDAR, contexto("DEPARTAMENTO"), datos).suficiente());
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto("DEPARTAMENTO"), datos).faltantes().stream()
                .anyMatch(f -> f.motivo() == Motivo.CONCEPTO_AREA_FALTANTE));
        assertFalse(datos.containsKey("metraje_construido"));
        assertFalse(datos.containsKey("area_terreno"));
    }

    @Test void soloElPotencialDeclaradoExigeZonificacionAdicionalEnCasa() {
        var base = contexto("CASA");
        var aplica = new LinkedHashMap<>(base.aplicabilidad());
        aplica.put(SuficienciaInmueble.POTENCIAL_O_CAMBIO_USO, Aplicabilidad.APLICA);
        var contexto = new Contexto(base.tipoPropiedad(), base.usoOfrecido(), base.objetoOfrecido(),
                base.operacion(), aplica, Set.of(), base.jurisdiccion());
        var datos = completos("CASA"); datos.remove("zonificacion");
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, base, datos).suficiente());
        assertTrue(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto, datos).faltantes().stream()
                .anyMatch(f -> "zonificacion".equals(f.clave())));
    }

    @Test void edificioCompletoEsObjetoDeOtroYSusPrestacionesNoCreanDormitoriosObligatorios() {
        var base = contexto("OTRO");
        var aplica = new LinkedHashMap<>(base.aplicabilidad());
        aplica.put(SuficienciaInmueble.EDIFICIO_COMPLETO, Aplicabilidad.APLICA);
        var contexto = new Contexto("OTRO", "MIXTO", "Edificio TEST completo", "VENTA", aplica, Set.of());
        var datos = completos("OTRO");
        datos.remove("dormitorios"); datos.remove("banos");
        datos.put("metraje_construido", area(500, SuficienciaInmueble.AREA_CONSTRUIDA));
        var evaluacion = regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto, datos);
        assertTrue(evaluacion.suficiente(), evaluacion.faltantes().toString());
        assertFalse(evaluacion.aplicables().stream().anyMatch(campo -> "dormitorios".equals(campo.clave())));
        datos.remove("pisos_edificacion");
        assertFalse(regla.evaluar(Accion.CONFIRMAR_ENCARGO, contexto, datos).suficiente());
    }

    static Contexto contexto(String tipo) {
        return new Contexto(tipo, "USO_DECLARADO", "Objeto501", "VENTA", Map.of(
                SuficienciaInmueble.UNIDAD_DE_EDIFICIO, Aplicabilidad.APLICA,
                SuficienciaInmueble.MANTENIMIENTO, Aplicabilidad.NO_APLICA,
                SuficienciaInmueble.POTENCIAL_O_CAMBIO_USO, Aplicabilidad.NO_APLICA,
                SuficienciaInmueble.EXPLOTACION_COMERCIAL, Aplicabilidad.NO_APLICA,
                SuficienciaInmueble.EDIFICIO_COMPLETO, Aplicabilidad.NO_APLICA), Set.of(), "JurisdiccionFixture");
    }
    static Map<String, Dato> completos(String tipo) {
        Map<String, Dato> datos = new LinkedHashMap<>();
        datos.put("direccion", Dato.conocido("AvPrueba123")); datos.put("distrito", Dato.conocido("Surco"));
        datos.put("metraje_total", area(80, "TERRENO".equals(tipo) ? SuficienciaInmueble.AREA_TERRENO
                : "ESTACIONAMIENTO".equals(tipo) ? SuficienciaInmueble.AREA_ESTACIONAMIENTO
                : "OTRO".equals(tipo) ? SuficienciaInmueble.AREA_OBJETO_OFRECIDO : SuficienciaInmueble.AREA_TOTAL));
        for (String clave : new String[]{"dormitorios","banos","pisos_edificacion","piso","ascensores"})
            datos.put(clave, Dato.conocido(0));
        for (String clave : new String[]{"agua_desague","energia_electrica","condicion_terreno","rubro_permitido",
                "tipo_acceso","condicion_almacenamiento","via_de_acceso","descripcion"}) datos.put(clave, Dato.conocido("DECLARADO"));
        for (String clave : new String[]{SuficienciaInmueble.MANTENIMIENTO,SuficienciaInmueble.POTENCIAL_O_CAMBIO_USO,
                SuficienciaInmueble.EXPLOTACION_COMERCIAL,SuficienciaInmueble.EDIFICIO_COMPLETO})
            datos.put("contexto."+clave, new Dato(null, EstadoDato.NO_APLICABLE, "respaldoFixture", null, null));
        datos.put("zonificacion", new Dato("ZF", EstadoDato.CONOCIDO,"documentoFixture",null,"CAT:P:1:1"));
        return datos;
    }
    private static Dato area(Object valor, String concepto) { return new Dato(valor, EstadoDato.CONOCIDO,"fuente",concepto,null); }
}
