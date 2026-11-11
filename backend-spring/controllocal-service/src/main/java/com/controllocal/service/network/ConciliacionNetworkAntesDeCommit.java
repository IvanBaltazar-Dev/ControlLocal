package com.controllocal.service.network;

import com.controllocal.service.soporte.CambioDeOfertaComercial;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Callback local de la misma TX: el cambio correcto no deja publicada una versión incorrecta. */
@Component
public class ConciliacionNetworkAntesDeCommit {
    private final NetworkService network;
    public ConciliacionNetworkAntesDeCommit(NetworkService network){this.network=network;}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void conciliar(CambioDeOfertaComercial cambio) {
        var origen=cambio.recurso()==CambioDeOfertaComercial.Recurso.ENCARGO
                ?NetworkService.OrigenCambio.ENCARGO:NetworkService.OrigenCambio.PROPIEDAD;
        network.reconciliarCambioCore(origen,cambio.idRecurso(),cambio.actor(),cambio.eventoCausal());
    }
}
