package com.controllocal.rest.seguridad;

public final class Entorno {

    private Entorno() {
    }

    public static boolean esProduccion() {
        String valor = ApiConfig.get("api.environment", "API_ENVIRONMENT", "development");
        return "production".equalsIgnoreCase(valor) || "produccion".equalsIgnoreCase(valor);
    }
}
