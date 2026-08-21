package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.comercial.Tarea;
import com.controllocal.persistence.repositorio.AlertaRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.PoliticaComercial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso de la campana. Corto a proposito: lo que tiene sustancia en F6
 * no es este service, son las <b>nueve bocas</b> del flujo comercial que
 * llaman a {@link #emitir} (§4 del contrato).
 */
@Service
public class AlertaServiceImpl implements AlertaService {

    /** Tope del barrido: acota una operacion que en la v1 no tenia ninguno. */
    private static final int TOPE_BARRIDO = 500;

    private final AlertaRepository alertas;
    private final ProspeccionRepository prospecciones;
    private final DetalleAgenteRepository agentes;
    private final Alcances alcances;

    public AlertaServiceImpl(AlertaRepository alertas, ProspeccionRepository prospecciones,
                             DetalleAgenteRepository agentes, Alcances alcances) {
        this.alertas = alertas;
        this.prospecciones = prospecciones;
        this.agentes = agentes;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaAlerta> listar(int pagina, int tamano, Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        // Un BROKER sin equipo ve la campana vacia, no un 403 (igual que en el
        // resto del sistema).
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        Page<Alerta> page = alertas.buscarConAgente(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), PageRequest.of(paginaValida - 1, tamanoValido));
        return new Pagina<>(page.getContent().stream().map(AlertaServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    @Override
    @Transactional
    public boolean atender(long id, Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            throw new NoEncontradoException("Alerta");
        }
        // El cable comprueba VISIBILIDAD, no propiedad, y responde 404 —no 403—
        // cuando la alerta existe pero no es del alcance (D-F6-3). Como
        // buscarVisible ya filtra por ACTIVA, una alerta ya atendida cae aqui:
        // por eso el "false" del contrato sale de este mismo camino.
        return alertas.buscarVisible(alcance.idOrganizacion(), id, alcance.global(), alcance.paramRoles())
                .map(alerta -> {
                    alerta.atender();
                    alertas.save(alerta);
                    return true;
                })
                .orElseGet(() -> {
                    // No estaba activa: puede ser que ya se atendio (respuesta
                    // false del cable) o que no exista/no se alcance (404). Se
                    // distinguen mirando si existe dentro del tenant.
                    if (alertas.findById(id)
                            .filter(a -> a.getOrganizacionId() == alcance.idOrganizacion())
                            .isPresent()) {
                        return false;
                    }
                    throw new NoEncontradoException("Alerta");
                });
    }

    @Override
    @Transactional
    public void emitir(DatosAlerta datos, Actor actor) {
        // Sin agente o sin entidad no hay a quien avisar: la v1 tambien se
        // salta la emision en silencio en vez de romper la operacion.
        if (datos == null || datos.idRolAgente() == null || datos.entidadId() == null
                || datos.entidadId() <= 0) {
            return;
        }
        Alerta alerta = new Alerta();
        alerta.setOrganizacionId(actor.idOrganizacion());
        alerta.setTipo(datos.tipo());
        alerta.setSeveridad(datos.severidad());
        alerta.setEntidadTipo(datos.entidadTipo());
        alerta.setEntidadId(datos.entidadId());
        alerta.setAgente(agentes.getReferenceById(datos.idRolAgente()));
        alerta.setMensaje(datos.mensaje());
        alerta.nacer();
        alertas.save(alerta);
    }

    /**
     * <b>Reconciliacion simetrica del recontacto.</b>
     *
     * <p>Antes esto solo creaba. La tarea equivalente de la bandeja se
     * auto-completa al reconciliar, asi que en cuanto alguien contactaba la
     * prospeccion la tarea desaparecia y <b>el aviso se quedaba vivo</b>: la
     * campana enseñaba PRO-0003, PRO-0005 y PRO-0011 mientras la cola iba por
     * otras. Dos representaciones activas y contradictorias del mismo hecho.
     *
     * <p>Ahora las cuatro transiciones estan declaradas:
     *
     * <pre>
     *   entra en condicion      -> se crea el aviso
     *   sigue en condicion      -> se mantiene el MISMO (no se duplica)
     *   deja de cumplirla       -> se descarta solo, con su fecha
     *   vuelve a incumplir      -> NUEVO ciclo, aviso nuevo
     * </pre>
     *
     * <p>La cuarta es una decision, no un efecto: no se reabre el aviso viejo.
     * Un recontacto vencido en agosto y otro en octubre son dos hechos, y
     * reactivar el primero perderia que hubo un contacto en medio — que es
     * justo lo que la auditoria necesita poder leer.
     *
     * <p><b>El plazo sale de {@link PoliticaComercial} y de ningun otro sitio</b>,
     * que es lo que garantiza que campana, bandeja e indicador hablen del mismo
     * vencimiento. Y la condicion de cierre es literalmente la de creacion
     * negada, resuelta en SQL: dos redacciones de la misma regla volverian a
     * separarse.
     *
     * @return cuantos avisos se crearon. Los descartados no se cuentan aqui: el
     *         cable de la campana declara «creadas», y sumarlos cambiaria el
     *         significado del numero.
     */
    @Override
    @Transactional
    public int sincronizarRecontacto(Actor actor) {
        // El plazo es el de PoliticaComercial, el mismo que dispara la tarea de
        // la bandeja y el que cuenta el indicador: la campana no puede avisar de
        // un atraso que el tablero todavia no reconoce.
        LocalDate limite = PoliticaComercial.limiteDeRecontacto(LocalDate.now());

        // PRIMERO se cierra lo que ya no aplica. El orden importa: al reves, un
        // aviso recien creado para una prospeccion al limite podria caer en la
        // misma pasada si las dos consultas vieran instantes distintos.
        for (Alerta desfasada : alertas.recontactosQueYaNoAplican(
                actor.idOrganizacion(), Tarea.ENTIDAD_PROSPECCION, Alerta.SIN_RESPUESTA, limite)) {
            desfasada.descartar();
            alertas.save(desfasada);
        }
        // El barrido es del TENANT entero, no del alcance del que consulta: la
        // v1 recorre todas las prospecciones por recontactar sin mirar quien
        // abrio la campana. Lo unico que se le anade es la frontera de
        // organizacion, que la v1 no tenia.
        Page<Prospeccion> vencidas = prospecciones.recontactables(actor.idOrganizacion(), true,
                List.of(-1L), limite, PageRequest.of(0, TOPE_BARRIDO));
        int creadas = 0;
        for (Prospeccion p : vencidas.getContent()) {
            Long idAgente = p.getAgente() != null ? p.getAgente().getId() : null;
            if (idAgente == null) {
                continue;
            }
            if (alertas.existeActivaDe(actor.idOrganizacion(), Tarea.ENTIDAD_PROSPECCION, p.getId(),
                    Alerta.SIN_RESPUESTA)) {
                continue;
            }
            String codigo = p.getCodigoProspeccion() != null && !p.getCodigoProspeccion().isBlank()
                    ? p.getCodigoProspeccion()
                    : "#" + p.getId();
            emitir(new DatosAlerta(Alerta.SIN_RESPUESTA, Alerta.MEDIA, Tarea.ENTIDAD_PROSPECCION, p.getId(),
                    idAgente, "Recontacta o evalua descartar la prospeccion " + codigo + "."), actor);
            creadas++;
        }
        return creadas;
    }

    // ------------------------------------------------------------------

    private static FichaAlerta ficha(Alerta a) {
        return new FichaAlerta(a.getId(), a.getTipo(), a.getSeveridad(), a.getEntidadTipo(),
                a.getEntidadId(),
                a.getAgente() != null ? a.getAgente().getId() : null,
                nombreDe(a),
                a.getMensaje(), a.getEstado(), a.getFechaGeneracion(), a.getFechaResolucion(),
                ruta(a.getEntidadTipo(), a.getEntidadId()));
    }

    private static String nombreDe(Alerta a) {
        if (a.getAgente() == null || a.getAgente().getRol() == null
                || a.getAgente().getRol().getPersona() == null) {
            return null;
        }
        return a.getAgente().getRol().getPersona().getNombresORazonSocial();
    }

    /**
     * Ruta a la que navega la campana. Calcada del {@code Dtos.ruta(Alerta)} de
     * la v1, incluidos sus huecos: {@code INMUEBLE} y {@code CAPTACION} caen en
     * el {@code default} y viajan con ruta <b>null</b>, asi que esas alertas se
     * muestran sin enlace. Es cable real (D-F6-4), no un olvido que tapar aqui.
     */
    private static String ruta(String entidadTipo, Long id) {
        if (id == null || id <= 0 || entidadTipo == null) {
            return null;
        }
        return switch (entidadTipo) {
            case "SOLICITUD_ALQUILER" -> "solicitud-detail/" + id;
            case "OPORTUNIDAD" -> "oportunidad-detail/" + id;
            case "VISITA" -> "visitas?focus=" + id;
            case "CLIENTE_INTERESADO" -> "cliente-detail/" + id;
            case "PROPIETARIO" -> "owner-detail/" + id;
            case Tarea.ENTIDAD_PROSPECCION -> "prospeccion-detail/" + id;
            case "CONTRATO_ALQUILER" -> "comisiones";
            default -> null;
        };
    }
}
