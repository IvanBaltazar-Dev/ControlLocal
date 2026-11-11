package com.controllocal.domain.planes;

import java.math.BigInteger;

/** Aprobación expresa31/10/2026: GB decimal comercial; GiB es una unidad distinta. */
public final class AlmacenamientoComercial {
    private AlmacenamientoComercial(){}
    public static final long BYTES_POR_GB=1_000_000_000L;
    public static final String VERSION_UNIDAD="GB-DECIMAL-20261031";
    public enum Aviso { NORMAL,UMBRAL_80,UMBRAL_95,LIMITE }
    public static long bytesDeGb(long gb){
        if(gb<0)throw new IllegalArgumentException("Capacidad no negativa requerida.");
        return Math.multiplyExact(gb,BYTES_POR_GB);
    }
    public static Aviso aviso(long bytes,long limite){
        if(bytes<0||limite<0)throw new IllegalArgumentException("Tamaño no negativo requerido.");
        if(bytes>=limite)return Aviso.LIMITE;
        BigInteger uso=BigInteger.valueOf(bytes).multiply(BigInteger.valueOf(100)),capacidad=BigInteger.valueOf(limite);
        if(uso.compareTo(capacidad.multiply(BigInteger.valueOf(95)))>=0)return Aviso.UMBRAL_95;
        if(uso.compareTo(capacidad.multiply(BigInteger.valueOf(80)))>=0)return Aviso.UMBRAL_80;
        return Aviso.NORMAL;
    }
}
