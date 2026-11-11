package com.controllocal.service.planes;

import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmacionAlmacenamientoB0Test {
    final UsoAlmacenamientoB0 uso=mock(UsoAlmacenamientoB0.class);
    final IntentoSalidaBinariaRepository principal=mock(IntentoSalidaBinariaRepository.class);
    final EntityManager entidades=mock(EntityManager.class);
    final ConfirmacionAlmacenamientoB0 servicio=new ConfirmacionAlmacenamientoB0(uso,principal,entidades);
    final UUID dueno=UUID.randomUUID();
    @org.junit.jupiter.api.BeforeEach void dueno(){when(principal.dueno(7L,null)).thenReturn(dueno);}
    final RefInterna ref=new RefInterna(7,UUID.randomUUID());
    final ComprobacionObjeto objeto=new ComprobacionObjeto("namespace:tenant/7/salidas/1",3,"a".repeat(64),"LECTURA COMPROBADA TEST");
    @Test void flushYVinculoEnConexionPrincipalPrecedenAConfirmarCuota(){
        when(principal.vinculado(dueno,"tenant/7/salidas/1")).thenReturn(true);
        servicio.confirmar(ref,objeto,"tenant/7/salidas/1");var orden=inOrder(entidades,principal,uso);
        orden.verify(entidades).flush();orden.verify(principal).vinculado(dueno,"tenant/7/salidas/1");orden.verify(uso).confirmar(ref,objeto);
    }
    @Test void ausenciaDeVinculoNoConfirmaAunqueElBinarioExista(){
        assertThrows(ConflictoException.class,()->servicio.confirmar(ref,objeto,"tenant/7/salidas/1"));verifyNoInteractions(uso);
    }
    @Test void falloDePersistenciaNuncaSeConvierteEnConfirmacion(){
        doThrow(new IllegalStateException("TEST flush")).when(entidades).flush();
        assertThrows(IllegalStateException.class,()->servicio.confirmar(ref,objeto,"tenant/7/salidas/1"));verifyNoInteractions(principal,uso);
    }
}
