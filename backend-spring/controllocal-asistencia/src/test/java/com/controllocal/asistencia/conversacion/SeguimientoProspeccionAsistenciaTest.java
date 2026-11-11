package com.controllocal.asistencia.conversacion;

import com.controllocal.asistencia.brox.*;
import com.controllocal.asistencia.brox.ClienteBrox.*;
import com.controllocal.asistencia.herramientas.Confirmacion;
import com.controllocal.asistencia.interpretacion.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Bindings del contrato CAT-R27; no calcula cadencia ni acredita los permisos de Core. */
class SeguimientoProspeccionAsistenciaTest {
    private static final SesionBrox SID=new SesionBrox(1,2,3,"AGENTE","sid",Long.MAX_VALUE);

    @Test void tresRutasCerradasConservanLecturaYAutoSinNuevasPropuestas() {
        var consulta=Accion.CONSULTAR_SEGUIMIENTOS_PROSPECCION.herramienta();
        assertEquals("GET",consulta.metodo());assertEquals("/prospecciones/{id}/seguimientos",consulta.ruta());
        assertEquals(Confirmacion.NUNCA,consulta.confirmacion());
        var plan=Accion.PLANIFICAR_SEGUIMIENTO_PROSPECCION.herramienta();
        assertEquals("POST",plan.metodo());assertEquals("/prospecciones/{id}/seguimientos/{idSeguimiento}/planificacion",plan.ruta());
        var pos=Accion.POSPONER_SEGUIMIENTO_PROSPECCION.herramienta();
        assertEquals("POST",pos.metodo());assertEquals("/prospecciones/{id}/seguimientos/{idSeguimiento}/posposicion",pos.ruta());
        assertEquals(Confirmacion.AVISA,plan.confirmacion());assertEquals(Confirmacion.AVISA,pos.confirmacion());
        for(var op:List.of(OperacionComercial.CONSULTAR_SEGUIMIENTOS_PROSPECCION,
                OperacionComercial.PLANIFICAR_SEGUIMIENTO_PROSPECCION,OperacionComercial.POSPONER_SEGUIMIENTO_PROSPECCION))
            assertFalse(op.preparaPropuesta(),"El binding CAT-R27 no se convierte en una propuesta sensible.");
    }

    @Test void argumentosConocidosNoInventanVersionNiFechaNiOrigen() {
        var brox=mock(ClienteBrox.class);var interprete=new InterpreteDeterminista(brox);
        var consulta=leer(interprete,"consultar seguimientos de prospección 42");
        assertEquals(Map.of("idProspeccion",42L),consulta);
        var parcial=leer(interprete,"posponer seguimiento prospección 42 seguimiento 91");
        assertEquals(Map.of("idProspeccion",42L,"idSeguimiento",91L),parcial);
        var espera=leer(interprete,"planificar seguimiento prospeccion {\"idProspeccion\":42,\"idSeguimiento\":91,\"versionEsperada\":7,\"origen\":\"INTENCION_PROFESIONAL\",\"fecha\":null,\"detalle\":\"Esperar al propietario sin fecha acordada\"}");
        assertTrue(espera.containsKey("fecha"));assertNull(espera.get("fecha"));
        assertEquals("INTENCION_PROFESIONAL",espera.get("origen"));
        assertEquals(7L,espera.get("versionEsperada"));
        verifyNoInteractions(brox);
    }

    @ParameterizedTest @EnumSource(value=OperacionComercial.class,names={"PLANIFICAR_SEGUIMIENTO_PROSPECCION","POSPONER_SEGUIMIENTO_PROSPECCION"})
    void autoTransportaSoloLaActuacionDeclaradaYConservaReciboOriginal(OperacionComercial op) {
        var brox=mock(ClienteBrox.class);var accion=Accion.valueOf(op.name());
        String datos=op==OperacionComercial.PLANIFICAR_SEGUIMIENTO_PROSPECCION
                ? "{\"idProspeccion\":42,\"idSeguimiento\":91,\"versionEsperada\":7,\"origen\":\"ACUERDO_EXTERNO\",\"fecha\":\"2026-11-04\",\"detalle\":\"Fecha acordada con el propietario\"}"
                : "{\"idProspeccion\":42,\"idSeguimiento\":91,\"versionEsperada\":7,\"hasta\":\"2026-11-06\",\"motivo\":\"Actuación propia pospuesta\"}";
        var referencia=new ReferenciaAutomatico("auto:"+accion.capacidad()+":opaque",accion.capacidad(),1);
        var original=new ResultadoComercial(Map.of("id",91L,"version",8L),null,referencia);
        when(brox.capacidades(eq(SID),any())).thenReturn(List.of(capacidad(accion,"ESCRITURA",List.of("idProspeccion","idSeguimiento","versionEsperada"))));
        when(brox.operarComercial(eq(SID),eq(op),any(),any())).thenReturn(original);
        var kairos=new KairosImpl(new InterpreteDeterminista(brox),brox,"KAIROS","TEST","1");
        String frase=op==OperacionComercial.PLANIFICAR_SEGUIMIENTO_PROSPECCION?"planificar seguimiento prospeccion ":"posponer seguimiento prospeccion ";

        var respuesta=kairos.turno(turno(frase+datos,null),SID);

        assertEquals(Kairos.Desenlace.EJECUTADO,respuesta.desenlace());assertFalse(respuesta.confirmaUnaPersona());
        assertSame(original,respuesta.resultado().comercial());assertSame(referencia,respuesta.resultado().automatico());
        verify(brox).operarComercial(eq(SID),eq(op),eq(ArgumentosComerciales.leer(datos,op)),any());
        when(brox.consultarActuacion(eq(SID),eq(referencia.referencia()),any()))
                .thenReturn(new ResultadoActuacion(referencia.referencia(),"EXITO",null,null,null,original));
        var recuperada=kairos.turno(turno("No volver a programar",referencia.referencia()),SID);
        assertEquals(Kairos.Desenlace.RESPONDIDO,recuperada.desenlace());assertSame(original,recuperada.resultado().comercial());
        verify(brox,times(1)).operarComercial(eq(SID),eq(op),any(),any());
    }

    @ParameterizedTest @ValueSource(strings={"AUSENTE","NULO","CONOCIDO"})
    void laConsultaConservaDatosConocidosYContactoDeclaradoEsOpcional(String contacto) {
        var brox=mock(ClienteBrox.class);var accion=Accion.CONSULTAR_SEGUIMIENTOS_PROSPECCION;
        var revision=new LinkedHashMap<String,Object>();
        revision.put("id",91L);revision.put("version",7L);revision.put("idProspeccion",42L);
        revision.put("destinatario","referencia-opaca");revision.put("causa","PROXIMA_ACTUACION");
        revision.put("fechaUltimoIntento","2026-10-29");revision.put("recontactosSinRespuesta",1);
        revision.put("proximaFecha",null);revision.put("origenFecha","SIN_FECHA");revision.put("pospuestaHasta",null);
        revision.put("motivoPosposicion",null);revision.put("revisarContinuidad",false);
        revision.put("restringido",false);revision.put("alcancePorPrecisar",false);
        if(!"AUSENTE".equals(contacto))revision.put("contactoDeclarado","NULO".equals(contacto)?null:
                Map.of("referenciaDeclarada","referencia-opaca-del-contexto","tipo","PROPIETARIO",
                        "nombre","Dato declarado TEST","telefono","900000001","fuente","Declaración TEST"));
        var original=new ResultadoComercial(Map.of("seguimientos",List.of(revision)),null,null);
        when(brox.capacidades(eq(SID),any())).thenReturn(List.of(capacidad(accion,"LECTURA",List.of("idProspeccion"))));
        when(brox.operarComercial(eq(SID),eq(OperacionComercial.CONSULTAR_SEGUIMIENTOS_PROSPECCION),any(),any())).thenReturn(original);
        var kairos=new KairosImpl(new InterpreteDeterminista(brox),brox,"KAIROS","TEST","1");

        var respuesta=kairos.turno(turno("consultar seguimientos prospeccion 42",null),SID);

        assertEquals(Kairos.Desenlace.RESPONDIDO,respuesta.desenlace());assertSame(original,respuesta.resultado().comercial());
        assertEquals(91L,revision.get("id"));assertEquals(7L,revision.get("version"));assertNull(revision.get("proximaFecha"));
        if("CONOCIDO".equals(contacto))assertEquals("900000001",((Map<?,?>)revision.get("contactoDeclarado")).get("telefono"));
        else assertNull(revision.get("contactoDeclarado"));
        assertNull(respuesta.resultado().automatico());
    }

    @Test void laVersionFaltanteLaPideElEsquemaCoreSinEmitirLaPosposicion() {
        var brox=mock(ClienteBrox.class);var accion=Accion.POSPONER_SEGUIMIENTO_PROSPECCION;
        when(brox.capacidades(eq(SID),any())).thenReturn(List.of(capacidad(accion,"ESCRITURA",List.of("idProspeccion","idSeguimiento","versionEsperada"))));
        var kairos=new KairosImpl(new InterpreteDeterminista(brox),brox,"KAIROS","TEST","1");
        var respuesta=kairos.turno(turno("posponer seguimiento prospeccion 42 seguimiento 91 hasta=2026-11-06 motivo=Viaje",null),SID);
        assertEquals(Kairos.Desenlace.PREGUNTA,respuesta.desenlace());assertEquals(List.of("versionEsperada"),respuesta.falta());
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }

    private static Map<String,Object> leer(InterpreteDeterminista interprete,String texto) {
        var lectura=interprete.leer(texto,SID,new Traza("API","KAIROS","TEST","1","c","t","m",null,null));
        assertTrue(lectura.noEntendido().isEmpty());
        return ArgumentosComerciales.leer(lectura.datos().get("datosComerciales"),OperacionComercial.valueOf(lectura.accion().name()));
    }
    private static Capacidad capacidad(Accion accion,String efecto,List<String> required) {
        return new Capacidad(accion.capacidad(),"PROSPECCION",efecto,"AUTO",Map.of("required",required),1,"HABILITADA",null,null);
    }
    private static Kairos.Turno turno(String texto,String referencia) {
        return new Kairos.Turno("API","c","t","m",texto,null,referencia);
    }
}
