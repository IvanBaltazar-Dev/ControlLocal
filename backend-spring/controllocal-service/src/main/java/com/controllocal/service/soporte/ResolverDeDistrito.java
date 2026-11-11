package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.persistence.repositorio.DistritoRepository;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.Locale;

/** La misma resolución exacta del registro físico; no deduce distrito desde dirección/jurisdicción. */
@Component
public class ResolverDeDistrito {
    private final DistritoRepository distritos;
    public ResolverDeDistrito(DistritoRepository distritos){this.distritos=distritos;}
    public Long idParaNombre(String nombre) {
        if(nombre==null||nombre.isBlank())return null;
        String objetivo=normalizar(nombre);
        return distritos.findByActivoTrueOrderByNombre().stream()
                .filter(d->normalizar(d.getNombre()).equals(objetivo)).map(Distrito::getId).findFirst().orElse(null);
    }
    private static String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
    }
}
