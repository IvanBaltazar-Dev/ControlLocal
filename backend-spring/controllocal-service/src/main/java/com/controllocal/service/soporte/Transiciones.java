package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.service.Actor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * PUNTO UNICO de mutacion de estado de las entidades {@link Transicionable}
 * (pilar RC-002): toda transicion emite su fila en historial_estado dentro
 * de la misma transaccion del caso de uso. Un test ArchUnit del modulo app
 * falla el build si otra clase del service llama transicionarA directamente
 * (la causa de MEJ-01 en la v1: llamadas manuales dispersas que se olvidan).
 */
@Component
public class Transiciones {

    private final HistorialEstadoRepository historial;

    public Transiciones(HistorialEstadoRepository historial) {
        this.historial = historial;
    }

    /**
     * Fija el estado INICIAL de una entidad recien creada. No es una
     * transicion (la v1 tampoco la registraba): la fecha de registro de la
     * entidad documenta el alta.
     */
    public void iniciar(Transicionable entidad, String estadoInicial) {
        if (entidad.estadoActual() != null) {
            throw new IllegalStateException(
                    "La entidad ya tiene estado; usa aplicar() para transicionar.");
        }
        MaquinasEstado.validarCodigo(entidad.entidadTipo(), estadoInicial);
        entidad.transicionarA(estadoInicial);
    }

    /**
     * Transiciona y audita: captura el estado anterior ANTES de mutar
     * (Doc 5 §7) y persiste el evento con la identidad del actor. Si el
     * estado no cambia, no hace nada.
     *
     * <p>El tenant del evento se toma de la ENTIDAD auditada, no del actor:
     * historial_estado audita entidades de una organizacion, y asi la fila
     * sale bien tambien cuando no hay actor. Por eso la entidad tiene que ser
     * a la vez {@link Transicionable} y {@link EntidadDeOrganizacion}: es
     * imposible, en tiempo de compilacion, auditar una transicion sin tenant.
     *
     * @param actor null = actor de sistema (jobs).
     */
    public <E extends EntidadDeOrganizacion & Transicionable> void aplicar(
            E entidad, long idEntidad, String nuevoEstado, Actor actor, String motivo) {
        aplicar(entidad, idEntidad, nuevoEstado, actor, motivo, LocalDate.now());
    }

    /** Transicion con fecha efectiva de negocio separada del momento tecnico. */
    public <E extends EntidadDeOrganizacion & Transicionable> void aplicar(
            E entidad, long idEntidad, String nuevoEstado, Actor actor, String motivo,
            LocalDate fechaEfectiva) {
        String estadoAnterior = entidad.estadoActual();
        if (nuevoEstado.equals(estadoAnterior)) {
            return;
        }
        MaquinasEstado.validarTransicion(entidad.entidadTipo(), estadoAnterior, nuevoEstado);
        entidad.transicionarA(nuevoEstado);

        auditar(entidad, entidad.entidadTipo(), idEntidad, estadoAnterior, nuevoEstado,
                actor, motivo, fechaEfectiva);
    }

    /**
     * Transiciona la disponibilidad sin confundirla con el estado A/I del
     * registro. La mutacion conserva el flujo enum -&gt; codigo.
     */
    public void aplicarDisponibilidad(Propiedad propiedad, long idPropiedad,
                                      DisponibilidadComercial nuevaDisponibilidad,
                                      Actor actor, String motivo) {
        aplicarDisponibilidad(propiedad, idPropiedad, nuevaDisponibilidad, actor,
                motivo, LocalDate.now());
    }

    public void aplicarDisponibilidad(Propiedad propiedad, long idPropiedad,
                                      DisponibilidadComercial nuevaDisponibilidad,
                                      Actor actor, String motivo, LocalDate fechaEfectiva) {
        String anterior = propiedad.getDisponibilidadComercial();
        String nuevo = nuevaDisponibilidad.codigo();
        if (nuevo.equals(anterior)) {
            return;
        }
        MaquinasEstado.validarTransicion(Propiedad.ENTIDAD_DISPONIBILIDAD_TIPO, anterior, nuevo);
        propiedad.cambiarDisponibilidadA(nuevaDisponibilidad);
        auditar(propiedad, Propiedad.ENTIDAD_DISPONIBILIDAD_TIPO, idPropiedad,
                anterior, nuevo, actor, motivo, fechaEfectiva);
    }

    private void auditar(EntidadDeOrganizacion entidad, String entidadTipo, long idEntidad,
                         String estadoAnterior, String estadoNuevo, Actor actor,
                         String motivo, LocalDate fechaEfectiva) {
        HistorialEstado evento = new HistorialEstado();
        evento.setOrganizacionId(entidad.getOrganizacionId());
        evento.setEntidadTipo(entidadTipo);
        evento.setIdEntidad(idEntidad);
        evento.setEstadoAnterior(estadoAnterior);
        evento.setEstadoNuevo(estadoNuevo);
        if (actor != null) {
            evento.setIdActor(actor.idPersona());
            evento.setTipoRolActor(actor.tipoRolOperativo());
        }
        evento.setMotivo(motivo);
        evento.setFechaEfectiva(fechaEfectiva == null ? LocalDate.now() : fechaEfectiva);
        historial.save(evento);
    }
}
