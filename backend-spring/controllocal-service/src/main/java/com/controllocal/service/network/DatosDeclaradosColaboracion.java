package com.controllocal.service.network;

import com.controllocal.domain.colaboracion.*;
import com.controllocal.persistence.repositorio.CanalColaboracionRepository;
import com.controllocal.persistence.repositorio.IdentidadColaboracionRepository;
import com.controllocal.service.colaboracion.PoliticaColaboracionBilateral;
import java.time.LocalDate;
import java.util.*;

/** Fuente de los campos adicionales; el Provider comprueba SU autoridad o el proof del grant antes de invocarla. */
final class DatosDeclaradosColaboracion {
    private final IdentidadColaboracionRepository identidades;
    private final CanalColaboracionRepository canales;
    DatosDeclaradosColaboracion(IdentidadColaboracionRepository identidades,CanalColaboracionRepository canales) {
        this.identidades=identidades;this.canales=canales;
    }
    Map<String,Object> deLaParte(SolicitudColaboracion s,LadoColaboracion lado,Set<String> campos,LocalDate hoy) {
        boolean demanda=lado!=LadoColaboracion.ENCARGO;
        long org=demanda?s.getOrganizacionId():s.getOrganizacionEncargo();
        long responsable=demanda?s.responsableContraparte():s.getResponsableEncargo();
        Map<String,Object> datos=new LinkedHashMap<>();
        if(campos.contains(PoliticaColaboracionBilateral.NOMBRE_PROFESIONAL)||campos.contains(PoliticaColaboracionBilateral.INMOBILIARIA)) {
            identidades.deResponsable(org,responsable,hoy).ifPresent(i->{
                if(campos.contains(PoliticaColaboracionBilateral.NOMBRE_PROFESIONAL))ponerConocido(datos,PoliticaColaboracionBilateral.NOMBRE_PROFESIONAL,i.getNombreProfesional());
                if(campos.contains(PoliticaColaboracionBilateral.INMOBILIARIA)&&"EMPRESARIAL".equals(i.getModalidad()))ponerConocido(datos,PoliticaColaboracionBilateral.INMOBILIARIA,i.getInmobiliaria());
            });
        }
        if(campos.contains(PoliticaColaboracionBilateral.CANAL_COMERCIAL)&&s.canalDe(lado)!=null) {
            var c=canales.findByOrganizacionIdAndId(org,s.canalDe(lado)).orElseThrow(()->new IllegalStateException("La declaración seleccionada del canal no existe."));
            if(!Objects.equals(c.getSolicitud(),s.getId())||c.getLado()!=lado||c.getVersionAlcance()!=s.getVersionAlcance()
                    ||c.getDeclarante()!=responsable)throw new IllegalStateException("El canal declarado no corresponde al responsable y alcance de la parte.");
            ponerConocido(datos,PoliticaColaboracionBilateral.CANAL_COMERCIAL,c.getCanalComercial());
        }
        return Collections.unmodifiableMap(datos);
    }
    private static void ponerConocido(Map<String,Object> datos,String campo,String valor) {
        if(valor!=null&&!valor.isBlank())datos.put(campo,valor);
    }
}
