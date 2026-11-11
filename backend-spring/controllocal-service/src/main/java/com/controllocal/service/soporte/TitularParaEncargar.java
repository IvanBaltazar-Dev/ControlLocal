package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.TitularidadPropiedadRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

/** CAT-E06/RF015: preparación parcial y autorización del conjunto acreditado son hitos distintos. */
@Component
public class TitularParaEncargar {

    private final TitularidadPropiedadRepository titularidades;

    public TitularParaEncargar(TitularidadPropiedadRepository titularidades) {
        this.titularidades = titularidades;
    }

    /** CAT-E06: conjunto completo declarado al confirmar, sin inferir poderes del interlocutor. */
    public java.util.List<TitularidadPropiedadRepository.ParteActual> partesParaHabilitar(
            long org,long propiedad,java.time.LocalDate corte,java.util.Collection<Long> declaradas) {
        var actuales=partesConocidas(org,propiedad,corte);
        var ids=actuales.stream().map(TitularidadPropiedadRepository.ParteActual::getId)
                .collect(java.util.stream.Collectors.toSet());
        if(actuales.isEmpty() || declaradas==null || declaradas.size()!=ids.size()
                || !ids.equals(new java.util.HashSet<>(declaradas)))
            throw new ReglaNegocioException("La autorización debe identificar el conjunto actual completo de titulares.");
        var cuota=actuales.stream().map(TitularidadPropiedadRepository.ParteActual::getCuota)
                .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        if(cuota.compareTo(com.controllocal.domain.inmueble.TitularidadPropiedad.CUOTA_TOTAL)!=0)
            throw new ReglaNegocioException("Las cuotas declaradas deben completar el cien por ciento.");
        return java.util.List.copyOf(actuales);
    }
    public java.util.List<TitularidadPropiedadRepository.ParteActual> partesConocidas(
            long org,long propiedad,java.time.LocalDate corte) {
        return titularidades.partesActuales(org,propiedad,corte);
    }
    public java.util.Map<Long,String> identidades(long org,java.util.Collection<Long> ids) {
        var nombres=new java.util.HashMap<Long,String>();
        if(!ids.isEmpty()) titularidades.identidades(org,ids).forEach(i->nombres.put(i.getId(),i.getNombre()));
        return nombres;
    }
    public void bloquearIdentidades(long org,java.util.Collection<Long> ids) {
        if(!ids.isEmpty() && titularidades.bloquearIdentidades(org,ids).size()!=ids.size())
            throw new ReglaNegocioException("Las partes documentadas no pertenecen al contexto del Encargo.");
    }
}
