package com.controllocal.domain.colaboracion;

import java.time.Instant;
import java.util.*;

/** CAT-E19/figura47b: valor inmutable; contener un campo no le concede legitimación. */
public record AlcanceColaboracion(Set<LadoColaboracion> destinatarios, Set<String> acciones,
        Map<LadoColaboracion,Set<String>> campos, String finalidad, Instant desde, Instant hasta) {
    public AlcanceColaboracion {
        destinatarios=destinatarios==null?Set.of():Collections.unmodifiableSet(new TreeSet<>(destinatarios));
        acciones=ordenadas(acciones);
        EnumMap<LadoColaboracion,Set<String>> c=new EnumMap<>(LadoColaboracion.class);
        if(campos!=null)campos.forEach((lado,valores)->c.put(Objects.requireNonNull(lado),ordenadas(valores)));
        campos=Collections.unmodifiableMap(c);
        finalidad=finalidad==null?null:finalidad.trim();
    }
    private static Set<String> ordenadas(Set<String> valores) {
        return valores==null?Set.of():Collections.unmodifiableSet(new TreeSet<>(valores));
    }
    public boolean contiene(AlcanceColaboracion otro) {
        return otro!=null && Objects.equals(finalidad,otro.finalidad)
                && desde!=null && hasta!=null && otro.desde!=null && otro.hasta!=null
                && !otro.desde.isBefore(desde) && !otro.hasta.isAfter(hasta)
                && destinatarios.containsAll(otro.destinatarios) && acciones.containsAll(otro.acciones)
                && otro.campos.entrySet().stream().allMatch(e->campos.getOrDefault(e.getKey(),Set.of()).containsAll(e.getValue()));
    }
    /** Figura82: sólo el alcance común que ambas decisiones ya permiten, sin sumar derechos. */
    public Optional<AlcanceColaboracion> interseccion(AlcanceColaboracion otro) {
        if(otro==null || !Objects.equals(finalidad,otro.finalidad))return Optional.empty();
        Set<LadoColaboracion> d=new TreeSet<>(destinatarios);d.retainAll(otro.destinatarios);
        Set<String> a=new TreeSet<>(acciones);a.retainAll(otro.acciones);
        EnumMap<LadoColaboracion,Set<String>> c=new EnumMap<>(LadoColaboracion.class);
        campos.forEach((lado,vs)->{Set<String> x=new TreeSet<>(vs);x.retainAll(otro.campos.getOrDefault(lado,Set.of()));
            if(!x.isEmpty())c.put(lado,x);});
        Instant inicio=desde.isAfter(otro.desde)?desde:otro.desde;
        Instant fin=hasta.isBefore(otro.hasta)?hasta:otro.hasta;
        if(d.isEmpty() || a.isEmpty() || c.isEmpty() || !inicio.isBefore(fin))return Optional.empty();
        return Optional.of(new AlcanceColaboracion(d,a,c,finalidad,inicio,fin));
    }
    public boolean vigente(Instant ahora) { return !ahora.isBefore(desde) && ahora.isBefore(hasta); }
}
