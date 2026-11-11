package com.controllocal.domain.planes;

import java.time.*;

/** Directriz7.1.1: aniversario original conservado y límites [inicio,fin). */
public final class CalendarioComercial {
    private CalendarioComercial(){}
    public static final ZoneId ZONA=ZoneId.of("America/Lima");
    public static Instant siguienteMes(Instant inicio,int diaAniversario){
        if(inicio==null||diaAniversario<1||diaAniversario>31)throw new IllegalArgumentException("Aniversario inválido.");
        ZonedDateTime local=inicio.atZone(ZONA);
        YearMonth mes=YearMonth.from(local).plusMonths(1);
        return ZonedDateTime.of(mes.atDay(Math.min(diaAniversario,mes.lengthOfMonth())),local.toLocalTime(),ZONA).toInstant();
    }
    public static Instant vencimientoBoost(Instant compra){
        if(compra==null)throw new IllegalArgumentException("Compra requerida.");
        return compra.atZone(ZONA).plusMonths(12).toInstant();
    }
    public static boolean contiene(Instant inicio,Instant fin,Instant corte){
        return inicio!=null&&fin!=null&&corte!=null&&!corte.isBefore(inicio)&&corte.isBefore(fin);
    }
}
