package com.controllocal.service.planes;

import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.planes.DatosCompraManual.*;

class DatosCompraManualTest {
    final Instant ahora=Instant.parse("2026-11-03T20:00:00Z");
    @Test void fechaSinHoraNoInventaInicioHorarioDelObjetivo(){
        var f=new FechaPago(LocalDate.of(2026,11,2),null,"Fecha del recibo, hora no conocida");f.validar(ahora);assertNull(f.objetivoVerificacion());
    }
    @Test void objetivoSeMideDesdePagoNoDesdeRegistroOVerificacion(){
        var pago=Instant.parse("2026-11-02T18:30:00Z");var f=new FechaPago(LocalDate.of(2026,11,2),pago,"Referencia de prueba");
        f.validar(ahora);assertEquals(Instant.parse("2026-11-03T18:30:00Z"),f.objetivoVerificacion());
    }
    @Test void fechaLimaNoConfundeElDiaUtc(){
        var f=new FechaPago(LocalDate.of(2026,11,2),Instant.parse("2026-11-03T02:00:00Z"),"Instante conocido");assertDoesNotThrow(()->f.validar(ahora));
    }
    @Test void noAdmiteFechaContradictoriaOHoraFutura(){
        assertThrows(ReglaNegocioException.class,()->new FechaPago(LocalDate.of(2026,11,3),Instant.parse("2026-11-03T02:00:00Z"),"TEST").validar(ahora));
        assertThrows(ReglaNegocioException.class,()->new FechaPago(LocalDate.of(2026,11,3),ahora.plusSeconds(1),"TEST").validar(ahora));
    }
}
