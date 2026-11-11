package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FuentesDeInteraccionTest {
    private static final Actor AG=new Actor(1,101,201,Actor.AGENTE);
    private static final ReferenciaVersion REF=new ReferenciaVersion(90,0);
    private static final class Entorno {
        final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
        final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
        final ContactosDeInteraccion contactos=mock(ContactosDeInteraccion.class);
        final AutoridadDePropiedad propiedades=mock(AutoridadDePropiedad.class);
        final ProspeccionRepository prospecciones=mock(ProspeccionRepository.class);
        final CaptacionRepository encargos=mock(CaptacionRepository.class);
        final AccesoOportunidad oportunidades=mock(AccesoOportunidad.class);
        final Alcances alcances=mock(Alcances.class);
        final InteraccionComercialRepository comunicaciones=mock(InteraccionComercialRepository.class);
        final HistoriaInteraccionRepository historia=mock(HistoriaInteraccionRepository.class);
        final Map<Long,InteraccionComercialRepository.Cabecera> actuales=new LinkedHashMap<>();
        final Map<HistoriaInteraccionRepository.Clave,Autorizacion> versiones=new LinkedHashMap<>();
        final ContextosDeInteraccion servicio=new ContextosDeInteraccion(contexto,contactos,propiedades,prospecciones,encargos,oportunidades,alcances,comunicaciones,historia,documentos);
        Entorno(){
            when(alcances.de(AG)).thenReturn(new Alcances.Alcance(1,false,List.of(201L)));
            var p=mock(ProspeccionRepository.ContextoParaActividad.class);when(p.getResponsable()).thenReturn(201L);when(p.getPropiedad()).thenReturn(9L);
            when(prospecciones.contextoParaActividad(1,10)).thenReturn(Optional.of(p));
            when(propiedades.legibles(eq(AG),anyCollection())).thenAnswer(i->Set.copyOf((Collection<Long>)i.getArgument(1)));
            when(comunicaciones.antecedentes(eq(1L),anyCollection())).thenAnswer(i->List.copyOf(actuales.values()));
            when(historia.accesos(eq(1L),anyCollection())).thenAnswer(i->{
                var salida=new ArrayList<HistoriaInteraccionRepository.Acceso>();
                for(var k:(Collection<HistoriaInteraccionRepository.Clave>)i.getArgument(1))if(versiones.containsKey(k))
                    salida.add(new HistoriaInteraccionRepository.Acceso(k.id(),k.version(),documentos.objetoCanonico(versiones.get(k))));
                return salida;
            });
        }
        Autorizacion sencilla(){return new Autorizacion(201,List.of(new VinculoActividad(TipoContexto.PROSPECCION,10L,null,null)),Map.of(),List.of(),null);}
        Autorizacion conContacto(ReferenciaContactoContextual ref){var a=sencilla();return new Autorizacion(a.agente(),a.vinculos(),Map.of("interlocutor",new ContactoOriginal(ref,12L,true)),a.padresOriginales(),null);}
        void actual(long id,Autorizacion a){actuales.put(id,new InteraccionComercialRepository.CabeceraActual(id,0,a.agente(),documentos.objetoCanonico(a),CatalogoInteracciones.VERSION,null,null,null,null));}
        void version(long id,long v,Autorizacion a){versiones.put(new HistoriaInteraccionRepository.Clave(id,v),a);}
        Set<ReferenciaVersion> consultar(){return servicio.versionesPermitidas(List.of(REF),AG,null);}
    }
    @Test void fuenteActualYOriginalAutorizadasSonUtilizables(){
        var e=new Entorno();e.actual(90,e.sencilla());e.version(90,0,e.sencilla());assertEquals(Set.of(REF),e.consultar());
    }
    @Test void versionAusenteNoSeSustituyePorLaFilaActual(){
        var e=new Entorno();e.actual(90,e.sencilla());assertTrue(e.consultar().isEmpty());
    }
    @Test void revocarElContactoHistoricoImpideLeerAunqueLaVersionActualSeaVisible(){
        var e=new Entorno();var ref=new ReferenciaContactoContextual(TipoContacto.PERSONA,12L,null,null);
        e.actual(90,e.sencilla());e.version(90,0,e.conContacto(ref));
        when(e.contactos.comprobar(ref,12L,AG)).thenThrow(new AccesoNoAutorizadoException());assertTrue(e.consultar().isEmpty());
    }
    @Test void fuenteOriginalVisibleNoConcedeAccesoALaActualDeOtroResponsable(){
        var e=new Entorno();var a=e.sencilla();e.actual(90,new Autorizacion(202,a.vinculos(),a.contactos(),a.padresOriginales(),null));e.version(90,0,a);
        assertTrue(e.consultar().isEmpty());
    }
    @Test void seRevalidaTambienLaVersionHistoricaDeLaConsultaAntecesora(){
        var e=new Entorno();var ref=new ReferenciaContactoContextual(TipoContacto.PERSONA,12L,null,null);
        var raiz=e.sencilla();var depende=new Autorizacion(201,raiz.vinculos(),Map.of(),List.of(),null,List.of(new ReferenciaVersion(80,0)));
        e.actual(90,depende);e.version(90,0,depende);e.actual(80,raiz);e.version(80,0,e.conContacto(ref));
        when(e.contactos.comprobar(ref,12L,AG)).thenThrow(new AccesoNoAutorizadoException());assertTrue(e.consultar().isEmpty());
    }
    @Test void ancestroCompartidoNoSeConfundeConCiclo(){
        var e=new Entorno();var a=e.sencilla();var doble=new Autorizacion(201,a.vinculos(),Map.of(),List.of(),80L,List.of(new ReferenciaVersion(80,0)));
        e.actual(90,doble);e.version(90,0,doble);e.actual(80,a);e.version(80,0,a);assertEquals(Set.of(REF),e.consultar());
    }
    @Test void elCicloAntiguoSigueSiendoUnErrorYNoUnPermisoOListaVacia(){
        var e=new Entorno();var a=e.sencilla();var x=new Autorizacion(201,a.vinculos(),Map.of(),List.of(),80L);var y=new Autorizacion(201,a.vinculos(),Map.of(),List.of(),90L);
        e.actual(90,x);e.version(90,0,x);e.actual(80,y);assertThrows(IllegalStateException.class,e::consultar);
    }
    @Test void unaRectificacionNoPuedeAgregarSuPropioAntecedenteIndirecto(){
        var e=new Entorno();var a=e.sencilla();var x=new Autorizacion(201,a.vinculos(),Map.of(),List.of(),80L);
        e.actual(90,x);e.version(90,0,x);e.actual(80,a);
        assertThrows(ReglaNegocioException.class,()->e.servicio.versionesPermitidas(List.of(REF),AG,80L));
    }
    @Test void falloTecnicoDelLectorNoEsUnResultadoVacio(){
        var e=new Entorno();when(e.historia.accesos(eq(1L),anyCollection())).thenThrow(new IllegalStateException("Lector caído"));
        assertThrows(IllegalStateException.class,e::consultar);
    }
    @Test void fuentesDuplicadasSeLeenPorLoteUnaSolaVezYSeRevalidan(){
        var e=new Entorno();e.actual(90,e.sencilla());e.version(90,0,e.sencilla());
        assertEquals(Set.of(REF),e.servicio.versionesPermitidas(List.of(REF,REF),AG,null));
        verify(e.historia,times(1)).accesos(eq(1L),anyCollection());verify(e.comunicaciones,times(2)).antecedentes(eq(1L),anyCollection());
    }
    @Test void niLaListaVaciaOmiteLaAutoridadDelActor(){
        var e=new Entorno();var admin=new Actor(1,102,202,Actor.TENANT_ADMIN);doThrow(new AccesoNoAutorizadoException()).when(e.contexto).exigirOperador(admin);
        assertThrows(AccesoNoAutorizadoException.class,()->e.servicio.versionesPermitidas(List.of(),admin,null));verifyNoInteractions(e.historia,e.comunicaciones);
    }
}
