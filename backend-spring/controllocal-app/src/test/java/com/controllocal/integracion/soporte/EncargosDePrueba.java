package com.controllocal.integracion.soporte;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Banco de integración C2: sólo hechos explícitos de prueba y casos Core reales.
 * No activa por SQL, no inventa mínimos físicos, titularidad o aceptación heredada.
 * El llamante prepara inmueble suficiente y títulos conocidos mediante su montaje autorizado.
 */
public final class EncargosDePrueba {
    private final JdbcTemplate jdbc;
    private final EncargoService encargos;
    private final TransactionTemplate tx;
    private final FirmasManuscritasDePrueba firmas;
    private final ActuacionesService actuaciones;
    private final ActuacionesHumanasC4DePrueba humanas;
    private final PlanesFinanciadosDePrueba planes;
    public EncargosDePrueba(JdbcTemplate jdbc,EncargoService encargos,PlatformTransactionManager gestor,
            AccesoArchivoService acceso,AlmacenDocumentos almacen,ActuacionesService actuaciones,Documentos documentos) {
        this.jdbc=jdbc;this.encargos=encargos;this.tx=new TransactionTemplate(gestor);
        this.firmas=new FirmasManuscritasDePrueba(encargos,acceso,almacen);this.actuaciones=actuaciones;
        this.humanas=new ActuacionesHumanasC4DePrueba(actuaciones,documentos);
        this.planes=new PlanesFinanciadosDePrueba(jdbc,gestor);
    }
    public record Actuante(Actor actor,ContextoSesion sesion) { }
    public record Caso(long idPropiedad,OperacionInmobiliaria operacion,LocalDate desde,LocalDate hasta,
                        boolean exclusividad,EncargoService.Condiciones condiciones,
                        Actuante responsable,Actuante decisor) { }
    public record Resultado(long encargo,EncargoService.Documento documento,
                             EncargoService.Peticion peticion,EncargoService.Ficha ficha) { }

    public Resultado habilitar(Caso caso) {
        var responsable=caso.responsable();
        var abierto=tx.execute(status->encargos.abrir(new EncargoService.DatosApertura(caso.idPropiedad(),
                responsable.actor().idRolOperativo(),"T2-"+UUID.randomUUID().toString().substring(0,12),
                caso.operacion(),caso.desde(),caso.hasta(),caso.exclusividad(),"Fixture explícito C2",caso.condiciones(),origen(),List.of()),
                responsable.actor()));
        if(abierto==null) throw new AssertionError("La apertura de prueba no produjo resultado.");
        return habilitarExistente(abierto.id(),caso);
    }

    public Resultado habilitarExistente(long id,Caso caso) {
        var presentada=presentarExistente(id,caso); var a=caso.responsable();var d=caso.decisor();
        var antesDecision=encargos.consultar(id,d.actor());
        var manifestacion=java.util.Objects.requireNonNull(presentada.peticion().firma(),"El Core debe conservar la manifestacion firmada TEST.");
        var decision=new EncargoService.Decision("APROBAR",presentada.peticion().version(),antesDecision.version(),
                "Revision expresa del instrumento sintetico TEST",new EncargoService.RevisionManuscrita(
                        manifestacion.revisionPresentacion(),manifestacion.huellaManifestacion(),true));
        var propuesta=actuaciones.prepararDecisionEncargo(d.actor(),presentada.peticion().id(),decision,d.sesion(),origen());
        var aprobada=humanas.confirmar(propuesta,d.actor(),d.sesion(),EncargoService.Peticion.class).datos();
        encargos.exigirHabilitado(id,a.actor());
        return new Resultado(id,presentada.documento(),aprobada,encargos.consultar(id,a.actor()));
    }
    /** La petición queda realmente pendiente; el consumidor elige la decisión que desea demostrar. */
    public Resultado presentarExistente(long id,Caso caso) {
        var a=caso.responsable(); var d=caso.decisor();
        // Sólo el banco documental positivo aporta financiación TEST; crear una empresa no la financia.
        if("EMPRESARIAL".equals(jdbc.queryForObject("select modalidad from organizacion where id_organizacion=?",String.class,a.actor().idOrganizacion())))
            planes.asegurarTeam(a.actor().idOrganizacion());
        var inicial=encargos.consultar(id,a.actor());
        if(!java.util.Objects.equals(inicial.condiciones(),caso.condiciones())
                || !java.util.Objects.equals(inicial.desde(),caso.desde()) || !java.util.Objects.equals(inicial.hasta(),caso.hasta()))
            encargos.editar(id,new EncargoService.Edicion(inicial.version(),caso.desde(),caso.hasta(),caso.exclusividad(),
                    "Fixture explícito C2",caso.condiciones(),"Completar las condiciones declaradas del fixture C2",null,null),
                    a.actor(),a.sesion(),clave(),origen());
        var titulo=adjuntar(id,null,"TITULARIDAD_ENCARGO","titularidad-fixture.txt",
                ("Declaración de titularidad del fixture C2; inmueble "+caso.idPropiedad()).getBytes(StandardCharsets.UTF_8),a);
        List<EncargoService.Parte> partes=jdbc.query("""
           select t.id_titularidad,r.id_persona,t.cuota,p.nombres_o_razon_social
             from titularidad_propiedad t
             join persona_rol r on r.id_persona_rol=t.id_rol_propietario and r.organizacion_id=t.organizacion_id
             join persona p on p.id_persona=r.id_persona and p.organizacion_id=r.organizacion_id
            where t.organizacion_id=? and t.id_propiedad=? and t.vigente_hasta is null order by t.id_titularidad
           """,(r,n)->new EncargoService.Parte(r.getLong(1),r.getLong(2),r.getBigDecimal(3),titulo.referencia(),
                    null,null,null,null,null,r.getString(4),null),a.actor().idOrganizacion(),caso.idPropiedad());
        if(partes.isEmpty()) throw new AssertionError("El positivo C2 necesita titulares explícitos; no se fabrican en el banco.");
        var actual=encargos.consultar(id,a.actor());
        // Un instrumento del objeto vigente conserva sus unidades; List.of() sólo
        // corresponde al Encargo que no tiene una composición declarada.
        var unidades=actual.composicionActual()==null?List.<EncargoService.Unidad>of():
                encargos.consultarComposicion(id,null,a.actor()).unidades();
        var documento=encargos.prepararDocumento(new EncargoService.Preparacion(id,actual.version(),caso.idPropiedad(),
                null,partes,caso.operacion(),unidades,caso.condiciones(),caso.desde(),caso.hasta(),caso.exclusividad(),null,null),
                a.actor(),a.sesion(),clave(),origen());
        if(!documento.faltantes().isEmpty()) throw new AssertionError("El montaje físico no es suficiente para su Encargo: "+documento.faltantes());
        // Eleccion explicita del escenario: firman los titulares conocidos de este instrumento TEST.
        var firmantes=partes.stream().map(p->new EncargoService.Firmante(p.titularidad(),p.persona())).toList();
        var firmasDeclaradas=firmas.aportar(id,documento,firmantes,OrigenesDePrueba.hoy(),a.actor(),a.sesion());
        actual=encargos.consultar(id,a.actor());
        var peticion=encargos.presentar(id,new EncargoService.Presentacion(documento.id(),actual.version(),partes,
                firmasDeclaradas,null,null),a.actor(),a.sesion(),clave(),origen());
        return new Resultado(id,documento,peticion,encargos.consultar(id,a.actor()));
    }
    private EncargoService.ArchivoRegistrado adjuntar(long encargo,Long documento,String finalidad,String nombre,byte[] datos,Actuante a){
        long version=encargos.consultar(encargo,a.actor()).version();
        var carga=encargos.cargarArchivo(new EncargoService.CargaArchivo(encargo,documento,finalidad,nombre,"text/plain",datos,version),a.actor(),a.sesion(),clave(),origen());
        return encargos.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),encargo,documento,finalidad,version),a.actor(),a.sesion(),clave(),origen());
    }

    /** Condición elegida por el montaje, nunca un valor por defecto del producto. */
    public static EncargoService.Condiciones alquiler(BigDecimal precio,String moneda,BigDecimal mensualidades,
                                                       String obligado,String beneficiario) {
        return new EncargoService.Condiciones(precio,moneda,"MENSUAL","E","R",mensualidades,moneda,"N",null,
                "Firma del contrato",obligado,beneficiario,"A la firma del contrato","Declaración explícita fixture C2",List.of(),null,null);
    }
    /** El porcentaje se declara como fracción: 0.03 equivale a3%, igual que el pacto persistido. */
    public static EncargoService.Condiciones venta(BigDecimal precio,String moneda,BigDecimal porcentaje,
                                                    String obligado,String beneficiario) {
        return new EncargoService.Condiciones(precio,moneda,null,"P","V",porcentaje,moneda,"N",null,
                "FIRMA_ESCRITURA",obligado,beneficiario,"A_LA_FIRMA_ESCRITURA","Declaración explícita fixture C2",List.of(),null,null);
    }
    private static String clave() { return "C2-"+UUID.randomUUID(); }
    private static Procedencia origen() { return Procedencia.deCabecera("API"); }
}
