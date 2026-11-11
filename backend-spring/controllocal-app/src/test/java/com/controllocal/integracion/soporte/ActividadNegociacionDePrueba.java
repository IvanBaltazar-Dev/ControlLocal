package com.controllocal.integracion.soporte;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.*;
import com.controllocal.service.OfertaService.*;
import com.controllocal.service.PropiedadUniversalService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Datos explícitos TEST para dos consumidores C4/C5. Crea personas por Core,
 * habilita sólo mediante el banco C2 y registra los hechos C4 por revisión real.
 * La identidad técnica inicial proviene de OrigenesDePrueba: no acredita login HTTP.
 */
public final class ActividadNegociacionDePrueba {
    private final JdbcTemplate jdbc;
    private final PropiedadUniversalService propiedades;
    private final EncargoService encargos;
    private final PropietarioService propietarios;
    private final ClienteService clientes;
    private final OportunidadService oportunidades;
    private final OfertaService ofertas;
    private final ActuacionesService actuaciones;
    private final OrigenesDePrueba origenes;
    private final SesionesComercialesDePrueba sesiones;
    private final EncargosDePrueba banco;
    private final ActuacionesHumanasC4DePrueba humanas;
    private static final AtomicLong DOCUMENTO = new AtomicLong(
            40_000_000L + Math.floorMod(System.nanoTime(), 40_000_000L));

    public ActividadNegociacionDePrueba(JdbcTemplate jdbc, PropiedadUniversalService propiedades,
            EncargoService encargos, PropietarioService propietarios, ClienteService clientes,
            OportunidadService oportunidades, OfertaService ofertas, ActuacionesService actuaciones,
            Documentos documentos, OrigenesDePrueba origenes, SesionesComercialesDePrueba sesiones,
            PlatformTransactionManager gestor,com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest,
            com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest) {
        this.jdbc=jdbc;this.propiedades=propiedades;this.encargos=encargos;
        this.propietarios=propietarios;this.clientes=clientes;this.oportunidades=oportunidades;
        this.ofertas=ofertas;this.actuaciones=actuaciones;this.origenes=origenes;
        this.sesiones=sesiones;this.banco=new EncargosDePrueba(jdbc,encargos,gestor,archivosFirmaTest,almacenFirmaTest,actuaciones,documentos);
        this.humanas=new ActuacionesHumanasC4DePrueba(actuaciones,documentos);
    }

    public record Caso(OrigenesDePrueba.Equipo equipo, Actor agente, ContextoSesion sesion,
            long propiedad,long encargo,long oportunidad,long personaInteresada,
            long personaPropietaria,String operacion,String moneda) { }

    /** habilitado=false conserva preparación legítima sin hito U; no elimina historia de precios. */
    public Caso mesa(String operacion,BigDecimal precio,String moneda,boolean habilitado) {
        return mesa(operacion,precio,moneda,habilitado,List.of());
    }
    /** Captura las condiciones explícitas del escenario antes de preparar y firmar su instrumento. */
    public Caso mesa(String operacion,BigDecimal precio,String moneda,boolean habilitado,List<ValorAtributo> condicionesDinamicas) {
        return mesaEnEquipo(origenes.empresa(),operacion,precio,moneda,habilitado,condicionesDinamicas);
    }
    /** Otra operación real del mismo equipo; comparte el productor C4/C2, no sus recursos comerciales. */
    public Caso mesaEnEquipo(OrigenesDePrueba.Equipo equipo,String operacion,BigDecimal precio,String moneda,boolean habilitado) {
        return mesaEnEquipo(equipo,operacion,precio,moneda,habilitado,List.of());
    }
    public Caso mesaEnEquipo(OrigenesDePrueba.Equipo equipo,String operacion,BigDecimal precio,String moneda,boolean habilitado,List<ValorAtributo> condicionesDinamicas) {
        var agente=equipo.agente();
        EvidenciaC4DePrueba.actor(agente);
        EvidenciaC4DePrueba.actor(equipo.broker());
        // El enrolamiento del broker ocurre antes de capturar las sesiones del caso.
        var sesionBroker=sesiones.de(equipo.broker());
        var sesionAgente=sesiones.de(agente);
        String documento=Long.toString(DOCUMENTO.getAndIncrement());
        long titular=propietarios.registrar(new PropietarioService.DatosPropietario("N","D",documento,
                "Propietario TEST C4 "+documento,"9"+documento,"propietario-"+documento+"@example.invalid",
                true,null),agente).id();
        long propiedad=propiedades.registrar(new ComandoRegistro(clave(),Procedencia.deLaPantalla(),null,
                "LOCAL_COMERCIAL","COMERCIAL","Local TEST C4 explícito",
                new Ubicacion("Dirección ficticia TEST C4 "+UUID.randomUUID(),"Miraflores",null,null,null,null,null,null,null),
                List.of(new Titular(titular,null,true)),List.of(),
                List.of(new OperacionSolicitada(operacion,precio,moneda,null,null,null,null,null,null,null,condicionesDinamicas)),null),agente).idPropiedad();
        long encargo=jdbc.queryForObject("select id_captacion from captacion where organizacion_id=? and id_propiedad=? and motivo_operacion=?",
                Long.class,agente.idOrganizacion(),propiedad,OperacionInmobiliaria.desde(operacion).codigo());
        prepararFisica(propiedad,agente);
        if(habilitado) {
            var op=OperacionInmobiliaria.desde(operacion);
            var condiciones=op==OperacionInmobiliaria.VENTA
                    ?EncargosDePrueba.venta(precio,moneda,new BigDecimal("0.03"),"PROPIETARIO_TEST","AGENCIA_TEST")
                    :EncargosDePrueba.alquiler(precio,moneda,BigDecimal.ONE,"PROPIETARIO_TEST","AGENCIA_TEST");
            banco.habilitarExistente(encargo,new EncargosDePrueba.Caso(propiedad,op,hoy(),hoy().plusMonths(6),false,
                    condiciones,new EncargosDePrueba.Actuante(agente,sesionAgente),
                    new EncargosDePrueba.Actuante(equipo.broker(),sesionBroker)));
        }
        long cliente=cliente(agente);
        long personaCliente=personaDelRol(cliente,agente);
        long personaPropietaria=personaDelRol(titular,agente);
        long oportunidad=abrir(encargo,cliente,agente,sesionAgente);
        EvidenciaC4DePrueba.recurso(agente,"PROPIEDAD",propiedad,null);
        EvidenciaC4DePrueba.recurso(agente,"ENCARGO",encargo,null);
        EvidenciaC4DePrueba.recurso(agente,"CLIENTE",cliente,null);
        return new Caso(equipo,agente,sesionAgente,propiedad,encargo,oportunidad,personaCliente,personaPropietaria,operacion,moneda);
    }

    /** Mismo Encargo, otro interesado declarado: una mesa explícita independiente. */
    public Caso otraMesa(Caso c) {
        long cliente=cliente(c.agente());
        long oportunidad=abrir(c.encargo(),cliente,c.agente(),c.sesion());
        EvidenciaC4DePrueba.recurso(c.agente(),"CLIENTE",cliente,null);
        return new Caso(c.equipo(),c.agente(),c.sesion(),c.propiedad(),c.encargo(),oportunidad,
                personaDelRol(cliente,c.agente()),c.personaPropietaria(),c.operacion(),c.moneda());
    }

    private long abrir(long encargo,long cliente,Actor agente,ContextoSesion sesion) {
        var datos=new OportunidadService.DatosOportunidad("C4-"+UUID.randomUUID().toString().substring(0,14),
                encargo,encargos.consultar(encargo,agente).version(),
                new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.CLIENTE,cliente,null,null,null),
                "Interés explícito del comprador TEST",null,null,null,"DIRECTO",null,null,
                new OportunidadService.EvidenciaInteres("Manifestación explícita de interés TEST","EVIDENCIA-INTERES-TEST"));
        EvidenciaC4DePrueba.accion("ABRIR_OPORTUNIDAD",agente,"ENCARGO",encargo,datos.versionEncargo());
        var propuesta=actuaciones.prepararAperturaOportunidad(agente,datos,sesion,Procedencia.deLaPantalla());
        return humanas.confirmar(propuesta,agente,sesion,OportunidadService.FichaOportunidad.class).datos().id();
    }

    public long cliente(Actor actor) {
        String documento=Long.toString(DOCUMENTO.getAndIncrement());
        return clientes.registrar(new ClienteService.DatosCliente("N","D",documento,"Comprador TEST C4 "+documento,
                "9"+documento,"cliente-"+documento+"@example.invalid","Comercio",true,true,null),actor).id();
    }

    public DatosRegistroOferta registro(Caso c,BigDecimal monto,String lado,Long anterior,Long versionAnterior) {
        return new DatosRegistroOferta(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),
                ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa(),parte(c,lado),
                new Dinero(monto,c.moneda(),"TOTAL","ALQUILER".equals(c.operacion())?"MENSUAL":null),
                objeto(c),null,hoy(),"Manifestación documentada TEST C4",anterior,versionAnterior);
    }

    public FichaOferta presentar(Caso c,BigDecimal monto) {
        var datos=registro(c,monto,"I",null,null);
        return presentar(c,datos,c.agente());
    }

    public FichaOferta presentar(Caso c,DatosRegistroOferta datos,Actor actor) {
        EvidenciaC4DePrueba.accion("REGISTRAR_OFERTA",actor,"OPORTUNIDAD",datos.oportunidad(),datos.versionOportunidad());
        EvidenciaC4DePrueba.recurso(actor,"MESA_NEGOCIACION",datos.oportunidad(),datos.versionMesa());
        var sid=sesiones.de(actor);
        var propuesta=actuaciones.prepararRegistroOferta(actor,datos,sid,Procedencia.deLaPantalla());
        long id=humanas.confirmar(propuesta,actor,sid,ResultadoNegociacion.class).datos().oferta();
        return ficha(c,id);
    }

    public FichaOferta contraofertar(Caso c,FichaOferta anterior,BigDecimal monto,String lado) {
        EvidenciaC4DePrueba.accion("CONTRAOFERTAR",c.agente(),"OFERTA",anterior.id(),anterior.version());
        var d=registro(c,monto,lado,null,null);
        EvidenciaC4DePrueba.recurso(c.agente(),"MESA_NEGOCIACION",c.oportunidad(),d.versionMesa());
        var datos=new DatosContraoferta(d.oportunidad(),d.versionOportunidad(),d.versionMesa(),anterior.id(),anterior.version(),
                d.proponente(),d.importe(),d.objeto(),d.condiciones(),d.fechaHecho(),d.evidencia());
        var p=actuaciones.prepararContraoferta(c.agente(),datos,c.sesion(),Procedencia.deLaPantalla());
        long id=humanas.confirmar(p,c.agente(),c.sesion(),ResultadoNegociacion.class).datos().oferta();return ficha(c,id);
    }

    public FichaOferta aceptar(Caso c,FichaOferta oferta,String motivo) {
        EvidenciaC4DePrueba.accion("ACEPTAR_OFERTA",c.agente(),"OFERTA",oferta.id(),oferta.version());
        String lado="I".equals(oferta.presentadaPor())?"P":"I"; // Elección expresa del escenario TEST.
        var d=new DatosAceptacion(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),
                ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa(),encargos.consultar(c.encargo(),c.agente()).version(),
                oferta.id(),oferta.version(),parte(c,lado),hoy(),"Aceptación declarada TEST C4",motivo);
        var p=actuaciones.prepararAceptacionOferta(c.agente(),d,c.sesion(),Procedencia.deLaPantalla());
        long id=humanas.confirmar(p,c.agente(),c.sesion(),ResultadoNegociacion.class).datos().oferta();return ficha(c,id);
    }

    public FichaOferta rechazar(Caso c,FichaOferta oferta,String motivo) {
        EvidenciaC4DePrueba.accion("RECHAZAR_OFERTA",c.agente(),"OFERTA",oferta.id(),oferta.version());
        String lado="I".equals(oferta.presentadaPor())?"P":"I";
        var d=new DatosRechazo(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),
                ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa(),encargos.consultar(c.encargo(),c.agente()).version(),
                oferta.id(),oferta.version(),parte(c,lado),hoy(),"Rechazo declarado TEST C4",motivo);
        var p=actuaciones.prepararRechazoOferta(c.agente(),d,c.sesion(),Procedencia.deLaPantalla());
        long id=humanas.confirmar(p,c.agente(),c.sesion(),ResultadoNegociacion.class).datos().oferta();return ficha(c,id);
    }

    public OportunidadService.FichaOportunidad noContinuidad(Caso c,String razon,String observaciones) {
        var d=new OportunidadService.NoContinuidad(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),razon,observaciones,hoy());
        EvidenciaC4DePrueba.accion("NO_CONTINUIDAD",c.agente(),"OPORTUNIDAD",d.idOportunidad(),d.versionEsperada());
        var p=actuaciones.prepararNoContinuidadOportunidad(c.agente(),d,c.sesion(),Procedencia.deLaPantalla());
        return humanas.confirmar(p,c.agente(),c.sesion(),OportunidadService.FichaOportunidad.class).datos();
    }

    public ParteReal parte(Caso c,String lado) {return new ParteReal(lado,"I".equals(lado)?c.personaInteresada():c.personaPropietaria());}
    public ObjetoExacto objeto(Caso c) {
        return new ObjetoExacto(TipoObjeto.UNIDAD,c.encargo(),encargos.consultar(c.encargo(),c.agente()).version(),
                c.propiedad(),propiedades.consultar(c.propiedad(),c.agente()).versionFisica(),null,null,List.of(c.propiedad()));
    }
    public FichaOferta ficha(Caso c,long id) {
        return ofertas.listar(c.oportunidad(),c.agente()).stream().filter(o->o.id()==id).findFirst().orElseThrow();
    }
    private long personaDelRol(long id,Actor actor) {
        return jdbc.queryForObject("select id_persona from persona_rol where organizacion_id=? and id_persona_rol=?",Long.class,actor.idOrganizacion(),id);
    }
    public void prepararFisica(long id,Actor actor) {
        var conocido=SuficienciaInmueble.EstadoDato.CONOCIDO;
        var noAplica=SuficienciaInmueble.EstadoDato.NO_APLICABLE;
        var contexto=new SuficienciaInmueble.Contexto("L","COMERCIAL","Local TEST C4 independiente",null,
                Map.of(SuficienciaInmueble.UNIDAD_DE_EDIFICIO,SuficienciaInmueble.Aplicabilidad.NO_APLICA,
                        SuficienciaInmueble.POTENCIAL_O_CAMBIO_USO,SuficienciaInmueble.Aplicabilidad.NO_APLICA),Set.of());
        propiedades.editar(id,new ComandoEdicion(clave(),Procedencia.deLaPantalla(),null,null,null,List.of(
                new ValorAtributo("metraje_total","120",null,null,"DECLARADO",null,hoy(),"SUPERFICIE TEST C4",
                        new MetadatosDeCaptura(conocido,"AREA_TOTAL",null,null,false)),
                new ValorAtributo("rubro_permitido","Comercio minorista TEST"),new ValorAtributo("tipo_acceso","A_PIE_DE_CALLE"),
                new ValorAtributo("contexto."+SuficienciaInmueble.UNIDAD_DE_EDIFICIO,null,null,null,"DECLARADO",null,hoy(),"Local independiente TEST",
                        new MetadatosDeCaptura(noAplica,null,null,null,false)),
                new ValorAtributo("contexto."+SuficienciaInmueble.POTENCIAL_O_CAMBIO_USO,null,null,null,"DECLARADO",null,hoy(),"Sin cambio de uso ofrecido TEST",
                        new MetadatosDeCaptura(noAplica,null,null,null,false))),null,null,null,contexto)
                .conVersion(propiedades.consultar(id,actor).versionFisica()),actor);
    }
    public static LocalDate hoy(){return OrigenesDePrueba.hoy();}
    public static String clave(){return "C4-TEST-"+UUID.randomUUID();}
}
