package com.controllocal.service.planes;

import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Misma conexión/TX Core: el diario técnico aislado no puede acreditar vínculos aún sin commit. */
@Service
public class ConfirmacionAlmacenamientoB0 {
    private final UsoAlmacenamientoB0 uso;private final IntentoSalidaBinariaRepository intentos;private final EntityManager entidades;
    public ConfirmacionAlmacenamientoB0(UsoAlmacenamientoB0 uso,IntentoSalidaBinariaRepository intentos,EntityManager entidades){
        this.uso=uso;this.intentos=intentos;this.entidades=entidades;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void confirmar(ReferenciaReserva ref,ComprobacionObjeto objeto,String clavePrivada){
        entidades.flush();
        Long org=ref instanceof RefInterna x?x.organizacion():null;
        UUID caso=ref instanceof RefEvaluacion x?x.caso():null;
        if(!intentos.vinculado(intentos.dueno(org,caso),clavePrivada))throw new ConflictoException("El archivo todavía no conserva su vínculo autorizado.");
        uso.confirmar(ref,objeto);
    }
}
