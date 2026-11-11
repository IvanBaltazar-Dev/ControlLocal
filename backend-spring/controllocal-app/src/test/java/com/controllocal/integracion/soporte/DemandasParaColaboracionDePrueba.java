package com.controllocal.integracion.soporte;

import com.controllocal.service.Actor;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.RequerimientoService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.soporte.Procedencia;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Sólo fixture comercial declarado; cuenta, SID y concesión legítimos los aporta el caso. */
public final class DemandasParaColaboracionDePrueba {
    private final JdbcTemplate jdbc;
    private final RequerimientoService demandas;
    public DemandasParaColaboracionDePrueba(JdbcTemplate jdbc,RequerimientoService demandas){this.jdbc=jdbc;this.demandas=demandas;}
    public FichaRequerimiento crear(Actor actor,ContextoSesion sesion) {
        var contacto=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,
                new DatosContacto(null,null,null,null,"999000773",null));
        var datos=new DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",List.of("DEPARTAMENTO"),null,
                new BigDecimal("3000"),"PEN",null,null,null,null,"Nota privada del fixture bilateral TEST",null,
                List.of(ascensores(1)),contacto,null,null,"TOTAL_MENSUAL_OBLIGATORIO","MENSUAL",Set.of());
        return demandas.crear(datos,actor,sesion,clave(),Procedencia.deLaPantalla());
    }
    public FichaRequerimiento editarPrivado(FichaRequerimiento observada,Actor actor,ContextoSesion sesion) {
        var datos=new DatosRequerimiento(null,null,null,null,null,null,null,null,null,new BigDecimal("9000"),null,
                null,null,null,null,"Otra nota privada del fixture TEST",null,null,null,null,null,null,null,Set.of());
        return demandas.actualizar(observada.id(),observada.version(),datos,actor,sesion,clave(),Procedencia.deLaPantalla());
    }
    public FichaRequerimiento editarFisico(FichaRequerimiento observada,Actor actor,ContextoSesion sesion) {
        var datos=new DatosRequerimiento(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,
                null,List.of(ascensores(2)),null,null,null,null,null,Set.of());
        return demandas.actualizar(observada.id(),observada.version(),datos,actor,sesion,clave(),Procedencia.deLaPantalla());
    }
    private CriterioPedido ascensores(int minimo) {
        return jdbc.queryForObject("select id_catalogo_atributo,version_definicion,unidad from catalogo_atributo where organizacion_id is null and activo and clave='ascensores'",(r,n)->
                new CriterioPedido("ascensores","MINIMO","I",null,BigDecimal.valueOf(minimo),null,null,null,null,null,null,null,
                        new ContextoValor(null,r.getString(3),null,null,"ALQUILER",null),
                        new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:"+r.getLong(1)+":"+r.getLong(2),r.getLong(2),
                                "evidencia-privada-TEST","Declaración privada del fixture TEST",LocalDate.now(ZoneId.of("America/Lima")),null,null),false,null));
    }
    private static String clave(){return "colab-demanda-test-"+UUID.randomUUID();}
}
