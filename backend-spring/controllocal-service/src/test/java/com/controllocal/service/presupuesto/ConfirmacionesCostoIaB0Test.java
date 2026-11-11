package com.controllocal.service.presupuesto;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.presupuesto.ConfirmacionesCostoIaB0.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfirmacionesCostoIaB0Test {
 final PresupuestoIaService b0=mock(PresupuestoIaService.class);final PresupuestoIaRepository repo=mock(PresupuestoIaRepository.class);
 final EvaluacionesIaB0Repository evaluaciones=mock(EvaluacionesIaB0Repository.class);final AtribucionesPlataforma autoridad=mock(AtribucionesPlataforma.class);
 final PlatformTransactionManager gestor=mock(PlatformTransactionManager.class);final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 final ConfirmacionesCostoIaB0 servicio=new ConfirmacionesCostoIaB0(b0,repo,evaluaciones,autoridad,new Documentos(mapper),gestor);
 final SesionPlataforma sesion=new SesionPlataforma("SID_TEST",1,UUID.randomUUID());
 Solicitud solicitud(BigDecimal costo){return new Solicitud(UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),null,
  "CUENTA_FINANCIERA_CANONICA_TEST","EMISOR_LEGAL_TEST","DOCUMENTO_TEST","LINEA_TEST","REVISION_TEST",costo,"2026-11-04","EVIDENCIA TEST NO FACTURA","b".repeat(64),"CAUSA TEST");}
 @Test void writeExigeLaAccionExpresaAntesDeCualquierLedger(){
  when(gestor.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  when(autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.AJUSTAR_COSTO,Campo.LEDGER)).thenThrow(new AccesoNoAutorizadoException());
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.confirmar(UUID.randomUUID(),UUID.randomUUID(),solicitud(BigDecimal.ONE),sesion));
  verifyNoInteractions(repo,evaluaciones,b0);verify(gestor).rollback(any());verify(gestor,never()).commit(any());
 }
 @Test void importeAusenteONegativoNoSeTransformaEnCeroNiTocaTransaccion(){
  for(var n:Arrays.asList(null,new BigDecimal("-0.1")))assertThrows(ReglaNegocioException.class,
    ()->servicio.confirmar(UUID.randomUUID(),UUID.randomUUID(),solicitud(n),sesion));
  verifyNoInteractions(gestor,repo,evaluaciones,b0,autoridad);
 }
 @Test void camposDeFacturaParcialORepartoNoSonIgnoradosAunqueMapperPermitaUnknowns(){
  mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);var arbol=mapper.valueToTree(solicitud(new BigDecimal("1.2300")));
  ((com.fasterxml.jackson.databind.node.ObjectNode)arbol).put("parcial",true);
  assertThrows(JsonProcessingException.class,()->mapper.treeToValue(arbol,Solicitud.class));
  assertThrows(JsonProcessingException.class,()->mapper.readValue("{\"confirmacion\":\""+UUID.randomUUID()+"\",\"costoTotalUsd\":5}",Recuperacion.class));
 }
 @Test void exponentesNoRepresentablesSeRechazanAntesDeAbrirTransaccionSinDesbordarEnteros(){
  for(String n:List.of("1e2147483647","1e-2147483647","1e-16384","1e26"))
   assertThrows(ReglaNegocioException.class,
     ()->servicio.confirmar(UUID.randomUUID(),UUID.randomUUID(),solicitud(new BigDecimal(n)),sesion),n);
  verifyNoInteractions(gestor,repo,evaluaciones,b0,autoridad);
 }
 @Test void conservaPrecisionNativaRepresentableSinExigirDoceDecimalesALaFuente(){
  when(gestor.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  when(autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.AJUSTAR_COSTO,Campo.LEDGER))
    .thenThrow(new AccesoNoAutorizadoException());
  assertThrows(AccesoNoAutorizadoException.class,
    ()->servicio.confirmar(UUID.randomUUID(),UUID.randomUUID(),solicitud(new BigDecimal("1e-16383")),sesion));
  verifyNoInteractions(repo,evaluaciones,b0);verify(gestor).rollback(any());
 }
 @Test void reciboNoExponeImportesCuentaFuenteNiConcedeLectura()throws Exception{
  String json=mapper.writeValueAsString(new Recibo(UUID.randomUUID(),3,false,"PROVISION_INSUFICIENTE"));
  for(String campo:List.of("Usd","cuentaProveedor","documento","evidencia","mediciones","historia"))assertFalse(json.contains(campo));
 }
}
