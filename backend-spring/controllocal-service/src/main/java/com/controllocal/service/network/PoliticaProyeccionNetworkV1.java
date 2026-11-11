package com.controllocal.service.network;

import com.controllocal.service.excepcion.ReglaNegocioException;
import java.util.Set;

/** Lista positiva aprobada28/10; selección por Encargo/versión, no permiso permanente de cartera. */
public final class PoliticaProyeccionNetworkV1 {
    private PoliticaProyeccionNetworkV1() { }
    public static final String VERSION="NETWORK_L2_V1_20261028";
    public static final String FINALIDAD="RED_COLABORATIVA";
    public static final Set<String> CAMPOS=Set.of("ENCARGO:operacion","PROPIEDAD:tipo_propiedad","PROPIEDAD:distrito",
            "PROPIEDAD:metraje_total","PROPIEDAD:metraje_construido","PROPIEDAD:area_terreno",
            "PROPIEDAD:dormitorios","PROPIEDAD:banos","PROPIEDAD:estacionamientos","PROPIEDAD:piso",
            "PROPIEDAD:ascensores","PROPIEDAD:zonificacion","ENCARGO:precio_pedido",
            "ENCARGO:se_ofrece_amoblado","ENCARGO:se_entrega_implementado","ENCARGO:disponibilidad");
    public static void exigirCampos(Set<String> campos) {
        if(campos==null||campos.isEmpty()||!CAMPOS.containsAll(campos))
            throw new ReglaNegocioException("Los campos no pertenecen a la proyección Network V1.");
    }
}
