package com.controllocal.domain.planes;

import java.math.BigDecimal;

/** Oferta aprobada directriz6.3. No incluye recarga automática ni descuento por volumen. */
public enum PaqueteBoost {
    B5K(5000,"15.00"),B10K(10000,"30.00"),B20K(20000,"60.00"),B30K(30000,"90.00"),B40K(40000,"120.00");
    private final int creditos;private final BigDecimal precio;
    PaqueteBoost(int creditos,String precio){this.creditos=creditos;this.precio=new BigDecimal(precio);}
    public int creditos(){return creditos;}
    public BigDecimal precioUsd(){return precio;}
}
