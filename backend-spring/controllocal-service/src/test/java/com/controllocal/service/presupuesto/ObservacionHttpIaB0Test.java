package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObservacionHttpIaB0Test {
 @ParameterizedTest @NullSource @ValueSource(strings="CONTROL_AUTOMATICO")
 void versionAjenaNoSeConservaNiEnGenNiEnQcComerciales(String etapa){
  var repo=mock(PresupuestoIaRepository.class);UUID comando=UUID.randomUUID();
  var ref=new PresupuestoIaService.ReferenciaInterna(71,comando);
  var o=new Operacion(71,comando,7,8,"PERFIL_TEST","VERSION_COMPROMETIDA_TEST","PROPIEDAD","123","TEST",
   "a".repeat(64),"b".repeat(64),"EN_PROCESO",BigDecimal.ONE,BigDecimal.ONE,UUID.randomUUID(),Instant.now(),null);
  when(repo.bloquearOrganizacion(71)).thenReturn(new Organizacion("EMPRESARIAL",true));
  when(repo.operacion(71L,comando)).thenReturn(Optional.of(o));
  when(repo.inicio(71L,comando)).thenReturn(Optional.of(Instant.now()));
  if(etapa!=null)when(repo.etapa(71L,comando,PresupuestoIaService.claveDeEtapa(ref,etapa))).thenReturn(Optional.of(
   new Etapa(etapa,PresupuestoIaService.claveDeEtapa(ref,etapa),"PROVEEDOR_TEST","b".repeat(64),"RECLAMADA",null,Instant.now(),null,Instant.now())));
  when(repo.observacionHttp(eq(71L),eq(comando),anyString())).thenReturn(Optional.empty());
  var servicio=new PresupuestoIaService(repo,mock(ContextoPresupuestoB0.class),List.of(),
   new Documentos(new ObjectMapper().findAndRegisterModules()),mock(DerechosDePlanB0.class),false);
  var observacion=new ObservacionHttpIa(ObservacionHttpIa.ESQUEMA,
   etapa==null?PresupuestoIaService.claveDeIntento(ref):PresupuestoIaService.claveDeEtapa(ref,etapa),
   "PROVEEDOR_TEST","MODELO_TEST","OTRA_VERSION_TEST",Instant.now(),1,
   ObservacionHttpIa.Resultado.TIMEOUT,null,true);
  assertThrows(ConflictoException.class,()->servicio.registrarObservacionHttp(ref,etapa,observacion));
  verify(repo,never()).antecedente(any(),any(),anyString(),anyString(),any());
  var valida=new ObservacionHttpIa(observacion.esquema(),observacion.claveIntento(),observacion.proveedor(),observacion.modelo(),
   "VERSION_COMPROMETIDA_TEST",observacion.iniciadaEn(),observacion.duracionNanos(),observacion.resultado(),observacion.estadoHttp(),true);
  assertDoesNotThrow(()->servicio.registrarObservacionHttp(ref,etapa,valida));
  verify(repo).antecedente(eq(71L),eq(comando),eq("OBSERVACION_HTTP_IA"),anyString(),any());
  verify(repo,never()).medicion(any(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any(),anyString(),anyString(),anyString(),anyString(),any(),any());
 }
}
