package com.controllocal.bl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.MotivoNoContinuidadTipo;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;

public interface VisitaBusinessLogic {

    public Long registrar(Visita visita);
    public Optional<Visita> buscarPorId(Long idVisita);
    public List<Visita> listarTodos();
    public boolean actualizar(Visita visita);
    public boolean eliminar(Long idVisita);

    // Transiciones de estado de la cita. Validan la maquina de estados
    // (solo una visita PROGRAMADA o REPROGRAMADA admite cambios) e invocan
    // los metodos de dominio de Visita.
    public boolean reprogramar(Long idVisita, LocalDate nuevaFecha, LocalTime nuevaHora);
    public boolean cancelar(Long idVisita, String motivo);

    /**
     * Marca la visita como REALIZADA y registra su desenlace comercial.
     * Cuando {@code resultado.implicaNoContinuidad()} (NO_INTERESADO/DESCARTADO)
     * la razon es obligatoria: en la misma transaccion se crea el
     * {@code MotivoNoContinuidad} ligado a esta visita y se cierra la oportunidad.
     * Para resultados de seguimiento (INTERESADO/SEGUIMIENTO/PENDIENTE) la razon
     * se ignora y la oportunidad permanece abierta.
     */
    public boolean registrarResultado(Long idVisita, ResultadoInteraccion resultado,
            String observaciones, MotivoNoContinuidadTipo razonNoContinuidad);
}
