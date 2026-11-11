package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.*;
import com.controllocal.domain.persona.*;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Infraestructura doblada, reglas de Contextos/Alcances/Acceso/Transiciones reales.
 * No afirma login, MFA, SQL, concurrencia ni commit: éstos requieren la suite PG de Root.
 * Reutilizada por Oportunidad/Visita/Oferta; los datos TEST no son clientes reales.
 */
final class ActividadDePrueba {
    final Actor agente=new Actor(1,7,1,Actor.AGENTE),broker=new Actor(1,8,2,Actor.BROKER),gobierno=new Actor(1,9,3,Actor.TENANT_ADMIN);
    final OportunidadComercialRepository oportunidades=mock(OportunidadComercialRepository.class);
    final CaptacionRepository captaciones=mock(CaptacionRepository.class);
    final PropiedadRepository propiedades=mock(PropiedadRepository.class);
    final AutoridadDePropiedad autoridad=mock(AutoridadDePropiedad.class);
    final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    final ContextosService sesiones=mock(ContextosService.class);
    final SupervisionAgenteRepository supervisiones=mock(SupervisionAgenteRepository.class);
    final EntityManager entidades=mock(EntityManager.class);
    final HistorialEstadoRepository historial=mock(HistorialEstadoRepository.class);
    final ComandoIdempotenteRepository recibos=mock(ComandoIdempotenteRepository.class);
    final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    final EncargoService encargos=mock(EncargoService.class);
    final Documentos documentos=new Documentos(new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    final ComandosIdempotentes comandos=new ComandosIdempotentes(recibos);
    final com.controllocal.service.actividad.RecibosDeActividad recibosActividad=new com.controllocal.service.actividad.RecibosDeActividad(comandos,recibos,eventos,documentos);
    final Transiciones transiciones=new Transiciones(historial);
    final Alcances alcances=new Alcances(supervisiones);
    final ContextosDeActividad contextos=new ContextosDeActividad(cuentas,miembros,sesiones,oportunidades);
    final AccesoOportunidad acceso=new AccesoOportunidad(oportunidades,alcances,contextos,entidades);
    final Map<Long,OportunidadComercial> mesas=new HashMap<>();
    final Map<String,ComandoIdempotente> ejecutados=new HashMap<>();
    final Propiedad propiedad=new Propiedad();
    final Captacion cap=new Captacion();
    final DetalleAgente detalle=detalle(1,7);
    final Persona interesado=persona(100,"Interesado TEST");
    final ContextosService.SesionAccesoDTO sesion;
    final ContextoSesion sid;
    final Procedencia procedencia=Procedencia.deCabecera("SPA");
    ActividadDePrueba(){
        var ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        sesion=new ContextosService.SesionAccesoDTO("SID-C4-TEST",1,
            new ContextosService.IdentidadAutenticada(99,"test","cuenta@example.test","Profesional TEST"),
            new ContextosService.Contexto("ctx-test",1,7,1,Actor.AGENTE,"EMPRESARIAL","Empresa TEST"),ahora,ahora.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        sid=ContextoSesion.de(sesion);
        when(oportunidades.contextoOperativoActual(anyLong(),anyLong(),anyLong(),anyString(),any())).thenAnswer(i->
            i.<Long>getArgument(0)==1L&&((i.<Long>getArgument(1)==7L&&i.<Long>getArgument(2)==1L&&Actor.AGENTE.equals(i.getArgument(3)))
                ||(i.<Long>getArgument(1)==8L&&i.<Long>getArgument(2)==2L&&Actor.BROKER.equals(i.getArgument(3)))));
        when(miembros.cuentasActivasDePersona(1,7)).thenReturn(List.of(99L));
        when(cuentas.bloquearCambioAcceso(99)).thenReturn(Optional.of(new CuentaAcceso()));
        when(sesiones.exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),agente)).thenReturn(sesion);
        when(supervisiones.agentesSupervisados(1,2)).thenReturn(List.of(1L));
        propiedad.setOrganizacionId(1L);id(propiedad,30);propiedad.setDireccion("Dirección TEST");
        cap.setOrganizacionId(1L);id(cap,20);cap.setPropiedad(propiedad);cap.setAgente(detalle);cap.setMotivoOperacion("VENTA");
        transiciones.iniciar(cap,Captacion.EN_PREPARACION);
        when(captaciones.bloquearParaEscritura(1,20)).thenReturn(Optional.of(cap));
        when(captaciones.buscarFicha(1,20)).thenReturn(Optional.of(cap));
        when(propiedades.bloquearParaEscritura(1,30)).thenReturn(Optional.of(propiedad));
        when(autoridad.buscarParaLectura(agente,30)).thenReturn(Optional.of(propiedad));
        when(encargos.consultar(20,agente)).thenAnswer(i->fichaEncargo(1));
        when(encargos.consultar(20,broker)).thenAnswer(i->fichaEncargo(1));
        when(encargos.consultarPartesNegociacion(eq(20L),anyLong(),any(),any())).thenAnswer(i->new EncargoService.PartesNegociacion(20,cap.getVersion(),0,null,
            List.of(new EncargoService.ParteNegociacion(5L,200L,null,null,null,null,null,null))));
        mesas.put(10L,oportunidad(10,100));
        when(oportunidades.contexto(anyLong(),anyLong())).thenAnswer(i->{
            var o=mesas.get(i.<Long>getArgument(1));if(i.<Long>getArgument(0)!=1L||o==null)return Optional.empty();
            var p=mock(OportunidadComercialRepository.ContextoOportunidad.class);
            when(p.getId()).thenReturn(o.getId());when(p.getOrganizacion()).thenReturn(1L);when(p.getVersion()).thenReturn(o.getVersion());
            when(p.getEncargo()).thenReturn(20L);when(p.getResponsable()).thenReturn(o.getAgente().getId());when(p.getResponsableEncargo()).thenReturn(cap.getAgente().getId());
            when(p.getEstado()).thenReturn(o.estadoActual());when(p.getOperacion()).thenReturn("V");when(p.getPropiedad()).thenReturn(30L);
            when(p.getCliente()).thenReturn(o.getCliente()==null?null:o.getCliente().getId());
            when(p.getPersona()).thenReturn(o.getPersonaInteresada()==null?null:o.getPersonaInteresada().getId());return Optional.of(p);
        });
        when(oportunidades.bloquear(anyLong(),anyLong())).thenAnswer(i->i.<Long>getArgument(0)==1L?Optional.ofNullable(mesas.get(i.<Long>getArgument(1))):Optional.empty());
        when(oportunidades.buscarFicha(anyLong(),anyLong())).thenAnswer(i->i.<Long>getArgument(0)==1L?Optional.ofNullable(mesas.get(i.<Long>getArgument(1))):Optional.empty());
        when(recibos.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString())).thenAnswer(i->Optional.ofNullable(ejecutados.get(i.getArgument(0)+"/"+i.getArgument(1))));
        when(recibos.save(any(ComandoIdempotente.class))).thenAnswer(i->{var r=i.<ComandoIdempotente>getArgument(0);ejecutados.put(r.getOrganizacionId()+"/"+r.getClaveIdempotencia(),r);return r;});
    }
    EncargoService.Ficha fichaEncargo(long responsable){return new EncargoService.Ficha(20,"ENC-TEST",cap.estadoActual(),cap.getVersion(),responsable,
        null,null,null,null,null,null,null,null,30L,"VENTA",List.of());}
    OportunidadComercial oportunidad(long id,long persona){
        var o=new OportunidadComercial();o.setOrganizacionId(1L);id(o,id);o.setCodigoOportunidad("OP-TEST-"+id);o.setCaptacion(cap);o.setAgente(detalle);
        o.setPersonaInteresada(persona==100?interesado:persona(persona,"Otro interesado TEST"));transiciones.iniciar(o,OportunidadComercial.ABIERTA);
        o.fijarRegistro(OffsetDateTime.now());return o;
    }
    static Persona persona(long id,String nombre){var p=new Persona();p.setOrganizacionId(1L);id(p,id);p.setNombresORazonSocial(nombre);return p;}
    static DetalleAgente detalle(long id,long persona){var p=persona(persona,"Agente TEST");var rol=new PersonaRol();rol.setOrganizacionId(1L);id(rol,id);rol.setPersona(p);rol.setTipoRol(TipoRol.AGENTE);
        var d=new DetalleAgente();d.setOrganizacionId(1L);id(d,id);d.setRol(rol);return d;}
    static void id(Object valor,long id){ReflectionTestUtils.setField(valor,"id",id);}
    static void version(Object valor,long version){ReflectionTestUtils.setField(valor,"version",version);}
}
