package com.controllocal.service.actividad;

import com.controllocal.domain.persona.Persona;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.SeguimientoProspeccionRepository.*;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.ProspeccionService.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.*;
import com.controllocal.service.actividad.SeguimientoDeProspeccion.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SeguimientoContextualTest {
    private static final Actor AG=new Actor(1,101,201,Actor.AGENTE);
    private static final LocalDate HOY=LocalDate.of(2026,10,30);
    private static final Instant AHORA=Instant.parse("2026-10-30T15:00:00Z");
    private static final class Entorno {
        final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
        final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
        final ContactosDeInteraccion contactos=mock(ContactosDeInteraccion.class);
        final Alcances alcances=mock(Alcances.class);
        final ContextosDeInteraccion acceso=mock(ContextosDeInteraccion.class);
        final Map<Long,Constancia> constancias=new HashMap<>();Fila fila;boolean retirada;
        final SeguimientoProspeccionRepository repo=mock(SeguimientoProspeccionRepository.class,i->{
            return switch(i.getMethod().getName()){
                case "insertar" -> {if(!SeguimientoDeProspeccion.FINALIDAD.equals(i.getArgument(16)))throw new IllegalArgumentException("Falta finalidad explícita");fila=new Fila(71,10,i.getArgument(2),201,0,i.getArgument(4),i.getArgument(5),i.getArgument(6),i.getArgument(7),i.getArgument(8),i.getArgument(9),i.getArgument(10),i.getArgument(11),i.getArgument(12),i.getArgument(13),AHORA,AHORA,null);yield fila;}
                case "conservarVersion" -> {long version=i.getArgument(2);if(constancias.putIfAbsent(version,new Constancia(71,version,i.getArgument(3)))!=null)throw new IllegalStateException("Constancia duplicada");yield null;}
                case "constancias" -> {var salida=new ArrayList<Constancia>();for(var c:(Collection<ClaveVersion>)i.getArgument(1)){var f=constancias.get(c.version());if(f!=null&&c.seguimiento()==71)salida.add(f);}yield salida;}
                case "retirar" -> {var anterior=fila;retirada=true;fila=new Fila(anterior.id(),10,anterior.destinatario(),201,anterior.version()+1,anterior.inicial(),anterior.ultimo(),anterior.versionUltimo(),anterior.fechaUltimo(),anterior.recontactos(),anterior.proxima(),anterior.origen(),anterior.pospuesta(),anterior.restriccionJson(),anterior.contextoJson(),anterior.registrado(),AHORA,anterior.antecedente());var fuente=constancias.get(anterior.version());String json=i.getArgument(3);if(json==null&&fuente!=null)json=fuente.fuentesJson();if(json!=null)constancias.put(fila.version(),new Constancia(71,fila.version(),json));yield null;}
                case "activas","enAlcance" -> fila==null||retirada?List.of():List.of(fila);
                case "porDestino","porId" -> retirada?Optional.empty():Optional.ofNullable(fila);
                case "porIdHistorico" -> Optional.ofNullable(fila);
                case "ultimoEstado" -> fila==null?Optional.empty():Optional.of(fila.id());
                default -> RETURNS_DEFAULTS.answer(i);
            };
        });
        final SeguimientoDeProspeccion servicio=new SeguimientoDeProspeccion(repo,contexto,contactos,alcances,documentos,acceso);
        Entorno(){
            when(contexto.hoy()).thenReturn(HOY);when(contexto.ahora()).thenReturn(AHORA);
            when(alcances.alcanza(AG,201L)).thenReturn(true);when(alcances.de(AG)).thenReturn(new Alcances.Alcance(1,false,List.of(201L)));
            when(repo.contexto(eq(1L),eq(10L),anyBoolean())).thenReturn(Optional.of(new Contexto(10,201,9,null,"P",false,false)));
            when(acceso.versionesPermitidas(anyCollection(),eq(AG),isNull())).thenAnswer(i->Set.copyOf((Collection<ReferenciaVersion>)i.getArgument(0)));
        }
        RevisionSeguimiento declarar(){servicio.iniciar(10,new DestinatarioDeclarado("PROPIETARIO","Nombre declarado","999999999",null,"Cartel"),AG);return servicio.revisar(10,AG).getFirst();}
    }
    @Test void declararElContactoNoCreaPersonaYResuelveLaLlamadaSinRepetirDatos(){
        var e=new Entorno();var revision=e.declarar();assertEquals(HOY,revision.proximaFecha());assertNotNull(revision.contactoDeclarado());
        String ref=revision.contactoDeclarado().referenciaDeclarada();assertNotNull(ref);
        var links=List.of(new VinculoActividad(TipoContexto.PROSPECCION,10L,null,null));
        var d=new DatosInteraccion(links,new ParticipanteDeclarado(null,null,null,null,ref),null,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
            List.of(new EventoDeclarado("intento",EventoComunicacion.LLAMADA_SALIENTE,new MomentoHecho(HOY,null,null))),ResultadoInteraccion.SIN_RESPUESTA,null,null,List.of(),null,null,null,null);
        var n=e.servicio.completarDestinoDeclarado(new Normalizado(d,new Autorizacion(201,links,Map.of(),List.of(),null),List.of()),AG);
        assertNull(n.datos().destinatario().contacto());assertEquals(ref,n.datos().destinatario().referenciaDeclarada());
        assertNotNull(n.datos().destinatario().telefono());assertEquals("PROPIETARIO",n.datos().destinatario().tipoDeclarado());
        var h=e.servicio.proyectar(n,AG).getFirst();var base=e.documentos.comoObjeto(e.fila.contextoJson(),ContextoEstado.class);
        var c=SeguimientoDeProspeccion.calcular(List.of(new Entrada(90,0,AHORA,h)),base,HOY);assertEquals(HOY.plusDays(2),c.proxima());
        verifyNoInteractions(e.contactos);assertEquals(1,e.constancias.size());
        assertFalse(e.constancias.get(0L).fuentesJson().contains("999999999"));
    }
    @Test void unCanalDesconocidoConservaElDeclaradoSinProgramarLlamadaImposible(){
        var e=new Entorno();e.servicio.iniciar(10,new DestinatarioDeclarado("REPRESENTANTE","Nombre",null,null,null),AG);
        var r=e.servicio.revisar(10,AG).getFirst();assertNull(r.proximaFecha());assertEquals("OBTENER_CONTACTO",r.causa());assertNotNull(r.contactoDeclarado());
    }
    @Test void unaReferenciaOpacaNoDaAccesoEnOtraProspeccion(){
        var e=new Entorno();var r=e.declarar();var links=List.of(new VinculoActividad(TipoContexto.PROSPECCION,20L,null,null));
        var d=new DatosInteraccion(links,new ParticipanteDeclarado(null,null,null,null,r.contactoDeclarado().referenciaDeclarada()),null,null,null,null,List.of(),null,null,null,List.of(),null,null,null,null);
        assertThrows(NoEncontradoException.class,()->e.servicio.completarDestinoDeclarado(new Normalizado(d,new Autorizacion(201,links,Map.of(),List.of(),null),List.of()),AG));
    }
    @Test void unaRestriccionPreviaDelMismoContextoYPersonaDetieneLaRevisionInicial(){
        var e=new Entorno();var persona=mock(Persona.class);when(persona.getId()).thenReturn(99L);when(persona.getTelefono()).thenReturn("999999999");
        var ref=new ReferenciaContactoContextual(TipoContacto.PROPIETARIO,7L,null,null);
        when(e.repo.propietario(1,9)).thenReturn(Optional.of(7L));when(e.contactos.resolver(ref,AG,null,false)).thenReturn(new ContactosDeInteraccion.Resuelto(ref,persona));
        when(e.contactos.comprobar(ref,99L,AG)).thenReturn(99L);
        when(e.repo.restriccionesContextuales(eq(1L),anyCollection(),eq(SeguimientoDeProspeccion.FINALIDAD))).thenReturn(List.of(new RestriccionActual(10,99,true,false)));
        e.servicio.iniciar(10,null,AG);var r=e.servicio.revisar(10,AG).getFirst();assertTrue(r.restringido());assertNull(r.proximaFecha());
        verify(e.repo,atLeastOnce()).restriccionesContextuales(eq(1L),argThat(d->d.equals(Set.of(new DestinoVerificado(10,9,99)))),eq(SeguimientoDeProspeccion.FINALIDAD));
    }
    @Test void sinConstanciaNoSeReinterpretaLaVersionActualComoHistoriaOriginal(){
        var e=new Entorno();var r=e.declarar();e.constancias.clear();
        assertThrows(AccesoNoAutorizadoException.class,()->e.servicio.exigirLecturaOriginal(r,AG));assertTrue(e.servicio.revisar(10,AG).isEmpty());
    }
    @Test void fallarElLectorDeFuentesNoSeConvierteEnListaVacia(){
        var e=new Entorno();e.declarar();var original=e.documentos.comoObjeto(e.constancias.get(0L).fuentesJson(),FuentesConstancia.class);
        e.constancias.put(0L,new Constancia(71,0,e.documentos.objetoCanonico(new FuentesConstancia(List.of(new ReferenciaVersion(90,0)),original.destino(),original.rolRegistrador()))));
        when(e.acceso.versionesPermitidas(anyCollection(),eq(AG),isNull())).thenThrow(new IllegalStateException("Fallo técnico"));
        assertThrows(IllegalStateException.class,()->e.servicio.revisar(10,AG));
    }
    @Test void unaRespuestaNoHaceInutilizableElDestinoDeclaradoParaUnNuevoHechoMaterial(){
        var e=new Entorno();var inicial=e.declarar();String ref=inicial.contactoDeclarado().referenciaDeclarada();
        var links=List.of(new VinculoActividad(TipoContexto.PROSPECCION,10L,null,null));
        var participante=new ParticipanteDeclarado(null,null,null,null,ref);
        var respuesta=new DatosInteraccion(links,participante,participante,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
            List.of(new EventoDeclarado("respuesta",EventoComunicacion.LLAMADA_SALIENTE,new MomentoHecho(HOY,null,null))),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,null,List.of(),null,null,null,null);
        var n=e.servicio.completarDestinoDeclarado(new Normalizado(respuesta,new Autorizacion(201,links,Map.of(),List.of(),null),List.of()),AG);
        var proyecciones=e.servicio.proyectar(n,AG);assertTrue(proyecciones.getFirst().respuestaConDestinatario());
        when(e.repo.hechos(1,10)).thenReturn(List.of(new Hecho(90,0,AHORA,e.documentos.objetoCanonico(new Proyecciones(proyecciones,null)))));
        e.servicio.actualizarDesde(Set.of(10L),AG);assertTrue(e.retirada);assertTrue(e.servicio.revisar(10,AG).isEmpty());
        var intento=new DatosInteraccion(links,participante,null,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
            List.of(new EventoDeclarado("nuevo intento",EventoComunicacion.LLAMADA_SALIENTE,new MomentoHecho(HOY.plusDays(1),null,null))),ResultadoInteraccion.SIN_RESPUESTA,null,null,List.of(),null,null,null,null);
        var conocido=assertDoesNotThrow(()->e.servicio.completarDestinoDeclarado(new Normalizado(intento,new Autorizacion(201,links,Map.of(),List.of(),null),List.of()),AG));
        assertEquals(ref,conocido.datos().destinatario().referenciaDeclarada());assertNotNull(conocido.datos().destinatario().telefono());assertNull(conocido.datos().destinatario().contacto());
    }
}
