package com.controllocal.domain.planes;

import java.math.BigDecimal;

/** Oferta aprobada de directriz5/5.2; no concede autoridad ni activa una compra. */
public enum PlanBrox {
    FREE("0",0,0,0,2,false), ONE("15",1,3000,0,5,false),
    ONE_PRO("29",1,9000,0,15,true), TEAM("75",5,3000,3000,30,false),
    GROWTH("150",10,3000,3000,60,false), SCALE("225",15,3000,3000,90,false),
    PRIME("300",20,3000,3000,120,false);
    private final BigDecimal precioUsd;
    private final int plazasAgente,cuotaAgente,bolsaBroker,almacenamientoGb;
    private final boolean proPersonal;
    PlanBrox(String precio,int plazas,int cuota,int broker,int almacenamiento,boolean pro){
        precioUsd=new BigDecimal(precio).setScale(2);plazasAgente=plazas;cuotaAgente=cuota;
        bolsaBroker=broker;almacenamientoGb=almacenamiento;proPersonal=pro;
    }
    public BigDecimal precioUsd(){return precioUsd;}
    public int plazasAgente(){return plazasAgente;}
    public int cuotaAgente(){return cuotaAgente;}
    public int bolsaBroker(){return bolsaBroker;}
    public int almacenamientoGb(){return almacenamientoGb;}
    public long almacenamientoBytes(){return AlmacenamientoComercial.bytesDeGb(almacenamientoGb);}
    public boolean proPersonal(){return proPersonal;}
    public boolean pagado(){return this!=FREE;}
    public boolean empresarial(){return bolsaBroker>0;}
    public int reservaAsignable(){return empresarial()?plazasAgente*cuotaAgente/5:0;}
    public int capacidadTotal(){return plazasAgente*cuotaAgente+bolsaBroker+reservaAsignable();}
    public static BigDecimal precioBrokerExtraUsd(){return new BigDecimal("5.00");}
}
