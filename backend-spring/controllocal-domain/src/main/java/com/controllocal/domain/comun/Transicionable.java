package com.controllocal.domain.comun;

/**
 * Contrato de comportamiento de las entidades con maquina de estados
 * (Doc 5 §5: interfaz en lugar de superclase de columnas, porque los
 * esquemas de los procesos divergen). El aspecto de auditoria de la capa
 * service se apoya en este contrato para emitir historial_estado en cada
 * transicion (F1/F6).
 */
public interface Transicionable {

    /** Codigo del catalogo entidad_tipo al que pertenece la entidad. */
    String entidadTipo();

    /** Estado actual de la maquina de estados de la entidad. */
    String estadoActual();

    /**
     * UNICO mutador del estado. Solo puede invocarlo el componente
     * Transiciones de la capa service (lo blinda un test ArchUnit): asi toda
     * transicion pasa por el punto que emite historial_estado y no se
     * reproduce la causa de MEJ-01 (llamadas manuales dispersas).
     */
    void transicionarA(String nuevoEstado);
}
