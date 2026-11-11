package com.controllocal.asistencia.brox;

/** Fallo del contrato Core conservado como fallo, sin inventar resultado comercial. */
public class ErrorBrox extends RuntimeException {
    public enum Tipo { PERMISO_INSUFICIENTE, CAPACIDAD_DESHABILITADA, FALLO_TECNICO, SOLICITUD_RECHAZADA }
    private final Tipo tipo;
    private final String codigo;
    private final int estadoHttp;
    private final ClienteBrox.ReferenciaAutomatico operacion;

    public ErrorBrox(Tipo tipo, String codigo, int estadoHttp) {
        this(tipo, codigo, estadoHttp, null);
    }
    public ErrorBrox(Tipo tipo, String codigo, int estadoHttp, ClienteBrox.ReferenciaAutomatico operacion) {
        super(codigo); this.tipo = tipo; this.codigo = codigo; this.estadoHttp = estadoHttp;
        this.operacion = operacion;
    }
    public Tipo tipo() { return tipo; }
    public String codigo() { return codigo; }
    public int estadoHttp() { return estadoHttp; }
    public ClienteBrox.ReferenciaAutomatico operacion() { return operacion; }
    public static ErrorBrox contratoInvalido() {
        return new ErrorBrox(Tipo.FALLO_TECNICO, "CONTRATO_CORE_INCOMPATIBLE", 502);
    }
}
