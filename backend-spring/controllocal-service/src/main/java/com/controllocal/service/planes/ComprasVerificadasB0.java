package com.controllocal.service.planes;

import com.controllocal.domain.planes.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Fuente exclusivamente servidor de pedido consentido y fondos ya verificados.
 * No implementar leyendo retorno de checkout, booleanos/capturas del cliente o prompt.
 * Sin implementación válida, el productor Core deniega la acreditación.
 */
public interface ComprasVerificadasB0 {
    enum Tipo { ALTA_PLAN,MEJORA_PLAN,RENOVACION,BOOST,BROKER_ADICIONAL }
    record Pedido(UUID id,long organizacion,Tipo tipo,PlanBrox plan,PaqueteBoost boost,int brokersExtra,
            UUID cicloEsperado,Long versionCicloEsperada,String versionOferta,String huellaConsentimiento,
            Instant corte,BigDecimal importeUsd,String moneda,
            String proveedor,String transaccion,Instant fondosVerificadosEn,String evidenciaVerificacion){}
    Optional<Pedido> consultar(UUID idPedido);
}
