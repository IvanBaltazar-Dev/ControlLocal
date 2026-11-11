package com.controllocal.service.presupuesto;

import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.archivo.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Identidad/serialización; no es prueba de autorización o integración financiera. */
class EvaluacionIaB0Test {
 @Test void referenciaComercialConservaConstructorYSerializacion(){
  UUID id=UUID.randomUUID();var r=new ReferenciaInterna(17,id);var json=new Documentos(new ObjectMapper().findAndRegisterModules());
  assertEquals(Set.of("organizacion","operacion"),json.comoMapa(json.objetoCanonico(r)).keySet());
  assertEquals(Idempotencia.huellaExacta("BROX-IA\u0000"+"17\u0000"+id),PresupuestoIaService.claveDeIntento(r));
 }
 @Test void elMismoUuidEnDosOrganizacionesYUnaEvaluacionTieneTresClaves(){
  UUID id=UUID.randomUUID();assertEquals(3,Set.of(
   PresupuestoIaService.claveDeIntento(new ReferenciaInterna(17,id)),
   PresupuestoIaService.claveDeIntento(new ReferenciaInterna(18,id)),
   PresupuestoIaService.claveDeIntento(new ReferenciaEvaluacion(UUID.randomUUID(),id))).size());
 }
 @Test void claveDeEtapaComercialNoCambia(){
  var r=new ReferenciaInterna(17,UUID.randomUUID());
  assertEquals(Idempotencia.huellaExacta(PresupuestoIaService.claveDeIntento(r)+"\u0000ETAPA\u0000QC"),PresupuestoIaService.claveDeEtapa(r,"QC"));
 }
 @Test void journalOrganizacionalConservaLaIdentidadDelMutex(){
  UUID id=UUID.randomUUID();String h=Idempotencia.huellaExacta("BROX-SALIDA-BINARIA\u0000"+"17\u0000"+id);
  assertEquals(Long.parseUnsignedLong(h.substring(0,16),16),SeguimientoSalidaBinaria.identidad(new PropietarioBinario.Organizacion(17),id));
 }
 @Test void noSeAceptaOrganizacionOCasoFicticios(){
  assertThrows(IllegalArgumentException.class,()->new PropietarioBinario.Organizacion(0));
  assertThrows(IllegalArgumentException.class,()->new PropietarioBinario.CasoEvaluacion(null));
 }
}
