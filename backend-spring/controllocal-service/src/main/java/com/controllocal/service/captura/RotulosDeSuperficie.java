package com.controllocal.service.captura;

import java.util.Map;
import java.util.Optional;

/** Rótulos comerciales CAT18/19; el concepto no convierte cifras ni reconcilia fuentes. */
public final class RotulosDeSuperficie {
    private static final Map<String,String> ROTULOS=Map.of(
            SuficienciaInmueble.AREA_TOTAL,"Área total",
            SuficienciaInmueble.AREA_CONSTRUIDA,"Área construida",
            SuficienciaInmueble.AREA_TERRENO,"Área de terreno",
            SuficienciaInmueble.AREA_ARRENDABLE,"Área arrendable",
            SuficienciaInmueble.AREA_ESTACIONAMIENTO,"Área del estacionamiento",
            SuficienciaInmueble.AREA_OBJETO_OFRECIDO,"Área del objeto ofrecido");

    private RotulosDeSuperficie() {}

    public static Optional<String> deConcepto(String concepto) {
        return concepto==null?Optional.empty():Optional.ofNullable(ROTULOS.get(concepto));
    }

    /** Conserva exactamente la presentación documental anterior, incluido su fallback. */
    public static String paraDocumento(String concepto) {
        if(concepto==null)return "Superficie declarada, concepto por precisar (m²)";
        return deConcepto(concepto).orElse(ROTULOS.get(SuficienciaInmueble.AREA_TOTAL))+" (m²)";
    }
}
