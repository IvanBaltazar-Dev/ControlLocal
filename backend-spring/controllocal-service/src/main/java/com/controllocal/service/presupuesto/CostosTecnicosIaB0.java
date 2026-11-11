package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.domain.presupuesto.PrecisionCapacidad;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import java.math.BigDecimal;
import java.util.*;

/** Una proyección del mismo ledger. Todos los métodos mutantes requieren el lock canónico del fondo. */
final class CostosTecnicosIaB0 {
    private final PresupuestoIaRepository repo;private final Documentos json;
    CostosTecnicosIaB0(PresupuestoIaRepository repo,Documentos json){this.repo=repo;this.json=json;}
    static boolean cerrada(String estado){return Set.of("LIQUIDADA","CANCELADA").contains(estado);}
    static BigDecimal pendiente(String estado,BigDecimal maximo,BigDecimal efectivo,BigDecimal imputado){
        return efectivo.subtract(cerrada(estado)?imputado:maximo).max(BigDecimal.ZERO);
    }
    static BigDecimal total(List<LineaCosto> filas){
        BigDecimal nativo=BigDecimal.ZERO;
        for(var f:filas)if(f.medicion()!=null)nativo=nativo.add(Objects.requireNonNullElse(f.confirmado(),f.original()));
        return PrecisionCapacidad.liquidar(nativo);
    }
    private String huella(List<LineaCosto> filas){return Idempotencia.huellaExacta(json.lista(filas.stream().filter(f->f.medicion()!=null)
        .map(f->Arrays.asList(f.medicion(),f.original(),f.confirmado(),f.confirmacion())).toList()));}
    void asegurarFondo(UUID fondo){
        var grupos=new LinkedHashMap<UUID,List<LineaCosto>>();
        for(var f:repo.costosSinProyeccion(fondo))grupos.computeIfAbsent(f.operacion(),x->new ArrayList<>()).add(f);
        var nuevas=new ArrayList<ProyeccionCosto>();
        for(var g:grupos.values()){
            var o=g.getFirst();BigDecimal efectivo=total(g),imputado=cerrada(o.estado())?Objects.requireNonNull(o.cierre()):BigDecimal.ZERO;
            nuevas.add(new ProyeccionCosto(o.operacion(),efectivo,imputado,pendiente(o.estado(),o.maximo(),efectivo,imputado),huella(g),0));
        }
        if(!nuevas.isEmpty())repo.crearProyeccionesCosto(nuevas);
    }
    ProyeccionCosto refrescar(Operacion o){
        asegurarFondo(o.fondo());var anterior=repo.proyeccionCosto(o.tecnica()).orElseThrow();
        var filas=repo.costosOperacion(o.tecnica());if(filas.isEmpty())throw new IllegalStateException("El costo requiere su operación real.");
        return actualizar(anterior,total(filas),anterior.imputado(),pendiente(o.estado(),o.maximoTecnico(),total(filas),anterior.imputado()),huella(filas));
    }
    ProyeccionCosto imputarCierre(Operacion o,BigDecimal costo){
        var p=refrescar(o);if(p.efectivo().compareTo(costo)!=0)throw new IllegalStateException("El cierre conserva el conjunto nativo completo.");
        return actualizar(p,p.efectivo(),costo,BigDecimal.ZERO,p.huella());
    }
    ProyeccionCosto imputarAjuste(ProyeccionCosto p){return actualizar(p,p.efectivo(),p.efectivo(),BigDecimal.ZERO,p.huella());}
    private ProyeccionCosto actualizar(ProyeccionCosto p,BigDecimal efectivo,BigDecimal imputado,BigDecimal pendiente,String huella){
        if(p.efectivo().compareTo(efectivo)==0&&p.imputado().compareTo(imputado)==0&&p.pendiente().compareTo(pendiente)==0&&p.huella().equals(huella))return p;
        var nueva=new ProyeccionCosto(p.operacion(),efectivo,imputado,pendiente,huella,Math.addExact(p.version(),1));
        repo.actualizarProyeccionCosto(p,nueva);return nueva;
    }
    void exigirClaim(Operacion o,Fondo f){
        if(refrescar(o).efectivo().compareTo(o.maximoTecnico())>0)throw new CapacidadDeshabilitadaException();
        exigirDisponible(f,BigDecimal.ZERO);
    }
    void exigirDisponible(Fondo f,BigDecimal nuevaReserva){
        asegurarFondo(f.id());var pendiente=repo.pendienteCostoFondo(f.id());
        if(f.financiado().subtract(f.retenido()).subtract(f.liquidado()).subtract(pendiente).compareTo(nuevaReserva)<0)
            throw new CapacidadDeshabilitadaException();
    }
}
