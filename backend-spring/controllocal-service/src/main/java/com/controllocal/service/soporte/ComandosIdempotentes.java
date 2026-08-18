package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <b>Un comando se ejecuta una vez, aunque llegue dos veces.</b>
 *
 * <h2>El problema, en una frase</h2>
 * Un canal conversacional reintenta por diseno: el emisor no distingue "no
 * llego" de "llego y no me contestaron". Sin nada que lo impida, cada corte de
 * red durante un alta deja una propiedad duplicada en la cartera.
 *
 * <h2>Por que el servidor no puede resolverlo solo</h2>
 * Deduplicar por contenido seria perder datos: dos departamentos identicos en
 * el mismo edificio son un caso normal. <b>Solo el cliente sabe</b> si esto es
 * una operacion nueva o el mismo intento otra vez, y lo dice con una clave
 * explicita — un identificador por operacion, repetido igual en cada reintento
 * de ESA operacion.
 *
 * <h2>Como se usa</h2>
 * <pre>
 *   var yaHecho = comandos.buscar(actor, clave, "REGISTRAR_PROPIEDAD", huella);
 *   if (yaHecho.isPresent()) {
 *       return respuestaDe(yaHecho.get());   // sin re-ejecutar nada
 *   }
 *   ... el caso de uso ...
 *   comandos.registrar(actor, clave, "REGISTRAR_PROPIEDAD", huella, tipo, id, origen, json);
 * </pre>
 *
 * <p>El registro va <b>dentro de la misma transaccion</b> que el caso de uso.
 * Si el alta se cae, no queda una fila diciendo que se hizo algo que no se
 * hizo; y si sale bien, la fila esta garantizada sin dos fases ni cola.
 *
 * <h2>La clave sin la huella no basta</h2>
 * La misma clave con OTRO contenido no es un reintento: es una clave
 * reutilizada por error. Devolver el resultado anterior confirmaria una
 * operacion que nadie pidio, asi que se rechaza con 409 en vez de con un exito
 * enganoso.
 */
@Component
public class ComandosIdempotentes {

    private final ComandoIdempotenteRepository comandos;

    public ComandosIdempotentes(ComandoIdempotenteRepository comandos) {
        this.comandos = comandos;
    }

    /**
     * ¿Este comando ya se ejecuto?
     *
     * @return el resultado anterior si la clave ya se uso para el MISMO
     *         comando; {@code empty()} si la clave es nueva o no viaja
     * @throws ReglaNegocioException si la clave ya se uso para otra cosa
     */
    public Optional<ComandoIdempotente> buscar(Actor actor, String clave, String tipoComando,
                                               String huella) {
        String normalizada = Idempotencia.normalizar(clave);
        if (normalizada == null) {
            // Sin clave no hay idempotencia. Es opcional a proposito: la
            // pantalla no la necesita —una persona no reintenta a ciegas— y
            // exigirsela romperia a todos los clientes actuales.
            return Optional.empty();
        }
        Optional<ComandoIdempotente> previo = comandos
                .findByOrganizacionIdAndClaveIdempotencia(actor.idOrganizacion(), normalizada);
        if (previo.isEmpty()) {
            return Optional.empty();
        }
        ComandoIdempotente comando = previo.get();
        if (!comando.coincideCon(tipoComando, huella)) {
            throw new ReglaNegocioException(
                    "La clave de idempotencia \"" + normalizada + "\" ya se uso para otro comando "
                            + "(" + comando.getTipoComando() + " sobre " + comando.getEntidadTipo()
                            + " " + comando.getEntidadId() + "). Usa una clave nueva: repetirla con "
                            + "otro contenido confirmaria una operacion que nadie pidio.");
        }
        return previo;
    }

    /**
     * Deja constancia de lo que produjo. No hace nada si no hubo clave: sin
     * clave no hay nada que recordar.
     */
    public void registrar(Actor actor, String clave, String tipoComando, String huella,
                          String entidadTipo, Long entidadId, Procedencia procedencia,
                          String resultadoJson) {
        String normalizada = Idempotencia.normalizar(clave);
        if (normalizada == null) {
            return;
        }
        comandos.save(ComandoIdempotente.de(actor.idOrganizacion(), normalizada, tipoComando,
                huella, entidadTipo, entidadId, actor.idRolOperativo(), procedencia.canal(),
                procedencia.agente(), procedencia.mensajeId(), resultadoJson));
    }
}
