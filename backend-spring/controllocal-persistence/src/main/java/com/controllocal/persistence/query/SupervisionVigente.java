package com.controllocal.persistence.query;

/**
 * Par broker→agente de las supervisiones abiertas del tenant. El desempeno por
 * broker (E4) necesita TODOS los equipos de una vez; pedirlos broker por broker
 * seria el N+1 que la v1 pagaba.
 */
public interface SupervisionVigente {

    Long getIdBroker();

    Long getIdAgente();
}
