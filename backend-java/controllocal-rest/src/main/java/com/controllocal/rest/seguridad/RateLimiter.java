package com.controllocal.rest.seguridad;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de solicitudes por clave (IP) en ventanas fijas de un minuto.
 * Protege el API de fuerza bruta y abuso sin dependencias externas.
 */
public final class RateLimiter {

    private static final long VENTANA_MILIS = 60_000;
    private static final int MAX_CLAVES = 10_000;

    private final int maxPorVentana;
    private final ConcurrentHashMap<String, Contador> contadores = new ConcurrentHashMap<>();

    public RateLimiter(int maxPorVentana) {
        this.maxPorVentana = maxPorVentana;
    }

    public boolean permitir(String clave) {
        long ahora = System.currentTimeMillis();
        if (contadores.size() > MAX_CLAVES) {
            contadores.entrySet().removeIf(e -> ahora - e.getValue().inicio >= VENTANA_MILIS);
        }
        Contador contador = contadores.compute(clave, (k, actual) ->
                actual == null || ahora - actual.inicio >= VENTANA_MILIS ? new Contador(ahora) : actual);
        return contador.total.incrementAndGet() <= maxPorVentana;
    }

    private static final class Contador {
        final long inicio;
        final AtomicInteger total = new AtomicInteger();

        Contador(long inicio) {
            this.inicio = inicio;
        }
    }
}
