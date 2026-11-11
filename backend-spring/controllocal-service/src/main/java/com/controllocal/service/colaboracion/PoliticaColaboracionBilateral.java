package com.controllocal.service.colaboracion;

import java.util.Set;
import java.util.TreeSet;
import com.controllocal.domain.colaboracion.LadoColaboracion;
import com.controllocal.service.network.PoliticaProyeccionNetworkV1;
import com.controllocal.service.demanda.ProyeccionComercialDemanda;

/** Campos adicionales expresamente aprobados el 29/10; nunca contactos de perfil por defecto. */
public final class PoliticaColaboracionBilateral {
    private PoliticaColaboracionBilateral() { }
    public static final String NOMBRE_PROFESIONAL="nombreProfesional",INMOBILIARIA="inmobiliaria",CANAL_COMERCIAL="canalComercial";
    public static final Set<String> IDENTIDAD_Y_CANAL=Set.of(NOMBRE_PROFESIONAL,INMOBILIARIA,CANAL_COMERCIAL);
    public static final Set<String> CAMPOS_DEMANDA=Set.of("operacion","tiposAdmitidos","zona","criterios");
    /** Misma lista física aprobada, excluyendo precio y disponibilidad económica de Demanda ajena. */
    public static Set<String> selectoresFisicos() {
        return ProyeccionComercialDemanda.SELECTORES_FISICOS;
    }
    public static Set<String> camposDelRecurso(LadoColaboracion lado,Set<String> solicitados) {
        if(lado==LadoColaboracion.INVITADO)return Set.of(); // Profesional invitado; no hay una demanda ni sus criterios.
        var campos=new TreeSet<>(solicitados);campos.retainAll(lado==LadoColaboracion.DEMANDA?CAMPOS_DEMANDA:PoliticaProyeccionNetworkV1.CAMPOS);return Set.copyOf(campos);
    }
}
