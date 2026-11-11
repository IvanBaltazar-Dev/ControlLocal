package com.controllocal.asistencia.conversacion;
import com.controllocal.asistencia.brox.*;
import com.controllocal.asistencia.brox.ClienteBrox.*;
import com.controllocal.asistencia.interpretacion.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ComercialC4Test {
    private static final Set<OperacionComercial> PREPARACIONES=Set.of(
            OperacionComercial.ABRIR_OPORTUNIDAD,OperacionComercial.RECTIFICAR_INTERACCION,
            OperacionComercial.REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD,OperacionComercial.CANCELAR_VISITA,
            OperacionComercial.PRESENTAR_OFERTA,OperacionComercial.REGISTRAR_CONTRAOFERTA,
            OperacionComercial.REGISTRAR_ACEPTACION_OFERTA,OperacionComercial.REGISTRAR_RECHAZO_OFERTA,
            OperacionComercial.REGISTRAR_RETIRADA_OFERTA,
            OperacionComercial.PRESENTAR_EXPEDIENTE_OPERACION,OperacionComercial.EVALUAR_EXPEDIENTE_OPERACION,
            OperacionComercial.INFORMAR_HITO_CONTRACTUAL,OperacionComercial.CONFIRMAR_HITO_CONTRACTUAL,
            OperacionComercial.RECTIFICAR_HITO_CONTRACTUAL,OperacionComercial.REGISTRAR_COBRO,
            OperacionComercial.ASIGNAR_REPARTO_COMISION,OperacionComercial.AUTORIZAR_BORRADOR_ADENDA,
            OperacionComercial.REPROGRAMAR_GESTION_COBRO,OperacionComercial.CONCLUIR_GESTION_COBRO,
            OperacionComercial.PUBLICAR_ANUNCIO,OperacionComercial.ACTUALIZAR_ANUNCIO_VISIBLE,
            OperacionComercial.PAUSAR_ANUNCIO,OperacionComercial.REANUDAR_ANUNCIO,OperacionComercial.FINALIZAR_DIFUSION_ANUNCIO,
            OperacionComercial.OBTENER_PIEZA_PARA_DIFUSION);
    private static final SesionBrox SID=new SesionBrox(1,2,3,"AGENTE","sesion",Long.MAX_VALUE);
    private static final Kairos.Turno TURNO=new Kairos.Turno("API","c","t","m","gesto declarado",null,null);
    @ParameterizedTest @EnumSource(OperacionComercial.class)
    void cadaGestoTransportaVersionParteFechaYPropuestaSinConfirmar(OperacionComercial op) {
        var brox=mock(ClienteBrox.class);var interprete=mock(Interprete.class);
        String literal="{\"versionEsperada\":7,\"fechaHecho\":\"2026-10-29\",\"parte\":{\"persona\":91,\"lado\":\"PROPIETARIO\"}}";
        var accion=Accion.valueOf(op.name());
        boolean preparacion=PREPARACIONES.contains(op);
        assertEquals(preparacion,op.preparaPropuesta());
        when(interprete.leer(any(),eq(SID),any())).thenReturn(new Interprete.Lectura(accion,Map.of("datosComerciales",literal),List.of(),null));
        boolean lectura=op==OperacionComercial.CONSULTAR_OPORTUNIDAD||op==OperacionComercial.CONSULTAR_AGENDA_VISITAS
                ||op==OperacionComercial.CONSULTAR_SEGUIMIENTOS_PROSPECCION
                ||Set.of(OperacionComercial.EVALUAR_PUBLICABILIDAD,OperacionComercial.CONSULTAR_PUBLICACIONES,
                    OperacionComercial.CONSULTAR_PUBLICACION,OperacionComercial.CONSULTAR_HISTORIA_PUBLICACION,
                    OperacionComercial.CONSULTAR_ESTADO_PIEZA,OperacionComercial.CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA,
                    OperacionComercial.CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA,OperacionComercial.EVALUAR_FOTO_LOCAL).contains(op);
        when(brox.capacidades(eq(SID),any())).thenReturn(List.of(new Capacidad(accion.capacidad(),"C4",lectura?"LECTURA":"ESCRITURA",preparacion?"CONFIRMA":"AUTO",Map.of("required",List.of("versionEsperada","fechaHecho","parte")),1,"HABILITADA",null,null)));
        var propuesta=new PropuestaActuacion("opaque",accion.capacidad(),1,"huella",Long.MAX_VALUE,Map.of());
        when(brox.operarComercial(eq(SID),eq(op),any(),any())).thenReturn(new ResultadoComercial(Map.of("resultado","autorizado"),preparacion?propuesta:null,null));
        var r=new KairosImpl(interprete,brox,"KAIROS","TEST","1").turno(TURNO,SID);
        if(preparacion) {
            assertEquals(Kairos.Desenlace.PROPUESTA,r.desenlace());assertTrue(r.confirmaUnaPersona());
            assertSame(propuesta,r.resultado().comercial().propuesta());
        } else {
            assertEquals(lectura?Kairos.Desenlace.RESPONDIDO:Kairos.Desenlace.EJECUTADO,r.desenlace());
            assertFalse(r.confirmaUnaPersona());
        }
        verify(brox).operarComercial(eq(SID),eq(op),argThat(d->Long.valueOf(7).equals(d.get("versionEsperada"))&&"2026-10-29".equals(d.get("fechaHecho"))&&Long.valueOf(91).equals(((Map<?,?>)d.get("parte")).get("persona"))),any());
    }

    private static java.util.stream.Stream<OperacionComercial> preparaciones() {
        return PREPARACIONES.stream();
    }

    @ParameterizedTest @MethodSource("preparaciones")
    void unDescriptorMenorNoConvierteElPreparadorEnEfectoDirecto(OperacionComercial op) {
        var brox=mock(ClienteBrox.class);var interprete=mock(Interprete.class);var accion=Accion.valueOf(op.name());
        when(interprete.leer(any(),any(),any())).thenReturn(new Interprete.Lectura(accion,Map.of("datosComerciales","{}"),List.of(),null));
        when(brox.capacidades(any(),any())).thenReturn(List.of(new Capacidad(accion.capacidad(),"C4","ESCRITURA","AUTO",Map.of("required",List.of()),1,"HABILITADA",null,null)));
        var propuesta=new PropuestaActuacion("propuesta-real",accion.capacidad(),7,"huella",Long.MAX_VALUE,Map.of());
        when(brox.operarComercial(any(),eq(op),any(),any())).thenReturn(new ResultadoComercial(null,propuesta,null));
        var respuesta=new KairosImpl(interprete,brox,"KAIROS","TEST","1").turno(TURNO,SID);
        assertEquals(Kairos.Desenlace.PROPUESTA,respuesta.desenlace());
        assertSame(propuesta,respuesta.resultado().comercial().propuesta());
        assertTrue(respuesta.confirmaUnaPersona());
    }

    @ParameterizedTest @MethodSource("preparaciones")
    void unPreparadorSinPropuestaRealEsContratoIncompatible(OperacionComercial op) {
        var brox=mock(ClienteBrox.class);var interprete=mock(Interprete.class);var accion=Accion.valueOf(op.name());
        when(interprete.leer(any(),any(),any())).thenReturn(new Interprete.Lectura(accion,Map.of("datosComerciales","{}"),List.of(),null));
        when(brox.capacidades(any(),any())).thenReturn(List.of(new Capacidad(accion.capacidad(),"C4","ESCRITURA","CONFIRMA",Map.of("required",List.of()),1,"HABILITADA",null,null)));
        when(brox.operarComercial(any(),eq(op),any(),any())).thenReturn(new ResultadoComercial(Map.of("id",7),null,null));
        var respuesta=new KairosImpl(interprete,brox,"KAIROS","TEST","1").turno(TURNO,SID);
        assertEquals(Kairos.Desenlace.FALLO_TECNICO,respuesta.desenlace());
        assertEquals("CONTRATO_CORE_INCOMPATIBLE",respuesta.motivo());
        assertFalse(respuesta.confirmaUnaPersona());
        assertNull(respuesta.resultado().comercial());
    }
    @Test void fraseParcialConservaRecursoSinInventarVersionParteOFecha() {
        var brox=mock(ClienteBrox.class);var i=new InterpreteDeterminista(brox);
        var l=i.leer("presentar oferta oportunidad 42",SID,new Traza("API","KAIROS","TEST","1","c","t","m",null,null));
        assertEquals(Accion.PRESENTAR_OFERTA,l.accion());
        var datos=ArgumentosComerciales.leer(l.datos().get("datosComerciales"),OperacionComercial.PRESENTAR_OFERTA);
        assertEquals(Map.of("oportunidad",42L),datos);verifyNoInteractions(brox);
    }
    @Test void faltaVersionAnidadaEsPreguntaCoreSinEmitirPrimitivoCero() {
        var brox=mock(ClienteBrox.class);var i=mock(Interprete.class);
        when(i.leer(any(),any(),any())).thenReturn(new Interprete.Lectura(Accion.REGISTRAR_INTERACCION,Map.of("datosComerciales","{\"vinculos\":[{\"tipo\":\"PROPIEDAD\",\"referencia\":9}]}"),List.of(),null));
        var esquema=Map.<String,Object>of("required",List.of("vinculos"),"properties",Map.of("vinculos",Map.of(
                "items",Map.of("required",List.of("tipo","referencia","versionEsperada")))));
        when(brox.capacidades(any(),any())).thenReturn(List.of(new Capacidad("registrar_interaccion","C4","ESCRITURA","AUTO",esquema,1,"HABILITADA",null,null)));
        var r=new KairosImpl(i,brox,"KAIROS","TEST","1").turno(TURNO,SID);
        assertEquals(List.of("vinculos[0].versionEsperada"),r.falta());assertEquals(Kairos.Desenlace.PREGUNTA,r.desenlace());
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }
}
