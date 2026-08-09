package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.InteraccionComercial;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Transiciones;
import com.controllocal.service.soporte.Vocabulario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Reglas y mensajes calcados de InteraccionesRest +
 * InteraccionComercialBusinessLogicImpl de la v1.
 *
 * <p>Dos cosas del cable real que sorprenden y hay que respetar:
 * <ul>
 *   <li>{@code resultado} es OBLIGATORIO en el alta aunque el DTO lo declare
 *       opcional: la validacion del BL exige canal Y resultado;</li>
 *   <li>una interaccion de PROSPECCION mueve el embudo del propietario segun
 *       su resultado (contactado, reunion, propuesta, recontactar). Aqui esa
 *       transicion pasa por {@link Transiciones}, asi que ademas queda
 *       auditada — la v1 la hacia a mano y sin registrar.</li>
 * </ul>
 */
@Service
public class InteraccionServiceImpl implements InteraccionService {

    private final InteraccionComercialRepository interacciones;
    private final OportunidadComercialRepository oportunidades;
    private final ProspeccionRepository prospecciones;
    private final CaptacionRepository captaciones;
    private final DetalleClienteRepository clientes;
    private final DetalleAgenteRepository agentes;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final PlanDeConsulta plan;

    public InteraccionServiceImpl(InteraccionComercialRepository interacciones,
                                  OportunidadComercialRepository oportunidades,
                                  ProspeccionRepository prospecciones, CaptacionRepository captaciones,
                                  DetalleClienteRepository clientes, DetalleAgenteRepository agentes,
                                  Alcances alcances, Transiciones transiciones,
                                  PlanDeConsulta plan) {
        this.interacciones = interacciones;
        this.oportunidades = oportunidades;
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.clientes = clientes;
        this.agentes = agentes;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.plan = plan;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaInteraccion> listar(FiltrosInteraccion f, Actor actor) {
        int filtros = 0;
        filtros += f.idOportunidad() != null ? 1 : 0;
        filtros += f.idProspeccion() != null ? 1 : 0;
        filtros += f.idCaptacion() != null ? 1 : 0;
        filtros += f.idCliente() != null ? 1 : 0;
        if (filtros > 1) {
            throw new ReglaNegocioException("Filtra por una sola entidad de interaccion.");
        }
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        int tamanoValido = tamano(f.tamano());
        String texto = vacioNull(f.q());
        if (texto != null) {
            return porTexto(alcance, f, texto, Math.max(1, f.pagina()), tamanoValido);
        }
        Page<InteraccionComercial> page = interacciones.buscar(
                alcance.idOrganizacion(), alcance.global(), alcance.paramRoles(),
                contextoFiltro(f.contexto()), f.idOportunidad(), f.idProspeccion(), f.idCaptacion(),
                f.idCliente(), grupo(f.grupo()), vacioNull(f.resultado()), vacioNull(f.canal()),
                PageRequest.of(Math.max(0, f.pagina() - 1), tamanoValido));
        return new Pagina<>(page.getContent().stream().map(InteraccionServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (§5 del contrato de
     * listados): una rama por tabla, {@code UNION} en la base y el mismo
     * conjunto para el conteo y la pagina.
     */
    private Pagina<FichaInteraccion> porTexto(Alcance alcance, FiltrosInteraccion f,
                                              String texto, int pagina, int tamano) {
        plan.forzarPersonalizado();
        String roles = alcance.paramRolesArray();
        String contexto = contextoFiltro(f.contexto());
        Boolean soloPropietario = grupo(f.grupo());
        String resultado = vacioNull(f.resultado());
        String canal = vacioNull(f.canal());
        long total = interacciones.contarPorTexto(alcance.idOrganizacion(), alcance.global(), roles,
                contexto, f.idOportunidad(), f.idProspeccion(), f.idCaptacion(), f.idCliente(),
                soloPropietario, resultado, canal, texto);
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        List<Long> ids = interacciones.idsPorTexto(alcance.idOrganizacion(), alcance.global(), roles,
                contexto, f.idOportunidad(), f.idProspeccion(), f.idCaptacion(), f.idCliente(),
                soloPropietario, resultado, canal, texto, tamano, (pagina - 1) * tamano);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        return new Pagina<>(
                interacciones.buscarFichaPorIds(alcance.idOrganizacion(), ids).stream()
                        .map(InteraccionServiceImpl::ficha).toList(),
                total);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaInteraccion obtener(long id, Actor actor) {
        return ficha(cargarConAcceso(id, actor));
    }

    @Override
    @Transactional
    public FichaInteraccion registrar(DatosInteraccion datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("La interaccion es obligatoria.");
        }
        String contexto = contextoDe(datos);
        String canal = canal(datos.canalContacto());
        String resultado = resultado(datos.resultado(), contexto);
        if (resultado == null) {
            // Validacion del BL v1: sin resultado la interaccion no se guarda,
            // aunque el DTO lo declare opcional.
            throw new ReglaNegocioException("La interaccion debe tener canal y resultado.");
        }
        InteraccionComercial interaccion = new InteraccionComercial();
        interaccion.setOrganizacionId(actor.idOrganizacion());
        interaccion.setContexto(contexto);
        interaccion.setCanalContacto(canal);
        interaccion.setResultado(resultado);
        interaccion.setObservaciones(datos.observaciones());
        interaccion.setTranscripcionNota(datos.transcripcionNota());

        // ORDEN del cable: la entidad colgada se exige ANTES que el agente. En
        // la v1 el id del contexto lo valida el REST y el agente lo valida
        // despues el BL, asi que al que le falta el id le responde "La
        // oportunidad ... es obligatoria.", nunca "Agente no encontrado".
        Prospeccion prospeccion = enlazarEntidad(interaccion, datos, contexto, actor);
        DetalleAgente agente = agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado para interaccion."));
        interaccion.setAgente(agente);
        interacciones.save(interaccion);
        if (prospeccion != null) {
            avanzarEmbudo(prospeccion, resultado, actor);
        }
        return ficha(interaccion);
    }

    @Override
    @Transactional
    public FichaInteraccion actualizar(long id, DatosInteraccion datos, Actor actor) {
        InteraccionComercial interaccion = cargarConAcceso(id, actor);
        if (datos != null && datos.resultado() != null && !datos.resultado().isBlank()) {
            interaccion.setResultado(resultado(datos.resultado(), interaccion.getContexto()));
        }
        if (datos != null && datos.observaciones() != null) {
            interaccion.setObservaciones(datos.observaciones());
        }
        if (interaccion.getResultado() == null) {
            throw new ReglaNegocioException("La interaccion debe tener canal y resultado.");
        }
        interacciones.save(interaccion);
        return ficha(interaccion);
    }

    // ------------------------------------------------------------------
    // Contexto polimorfico
    // ------------------------------------------------------------------

    /**
     * Deriva el contexto del id presente cuando el request no lo trae, en el
     * ORDEN del cable: prospeccion, captacion, cliente y, si no, oportunidad.
     */
    private static String contextoDe(DatosInteraccion datos) {
        String contexto = datos.contexto();
        if (contexto == null || contexto.isBlank()) {
            if (datos.idProspeccion() != null) {
                contexto = InteraccionComercial.PROSPECCION;
            } else if (datos.idCaptacion() != null) {
                contexto = InteraccionComercial.CAPTACION;
            } else if (datos.idCliente() != null) {
                contexto = InteraccionComercial.CLIENTE;
            } else {
                contexto = InteraccionComercial.OPORTUNIDAD;
            }
        }
        contexto = contexto.trim().toUpperCase(Locale.ROOT);
        if (!InteraccionComercial.CONTEXTOS.contains(contexto)) {
            throw new ReglaNegocioException("Contexto de interaccion invalido: " + contexto);
        }
        return contexto;
    }

    /** Cuelga la interaccion de su entidad; devuelve la prospeccion si el contexto la usa. */
    private Prospeccion enlazarEntidad(InteraccionComercial interaccion, DatosInteraccion datos,
                                       String contexto, Actor actor) {
        long org = actor.idOrganizacion();
        switch (contexto) {
            case InteraccionComercial.PROSPECCION -> {
                if (datos.idProspeccion() == null) {
                    throw new ReglaNegocioException("La prospeccion de la interaccion es obligatoria.");
                }
                Prospeccion prospeccion = prospecciones.buscarFicha(org, datos.idProspeccion())
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Prospeccion no encontrada para interaccion."));
                if (!prospeccion.enProceso()) {
                    throw new ReglaNegocioException(
                            "La prospeccion debe estar activa y sin captar para registrar interacciones.");
                }
                interaccion.setProspeccion(prospeccion);
                return prospeccion;
            }
            case InteraccionComercial.CAPTACION -> {
                if (datos.idCaptacion() == null) {
                    throw new ReglaNegocioException("La captacion de la interaccion es obligatoria.");
                }
                interaccion.setCaptacion(captaciones.buscarFicha(org, datos.idCaptacion())
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Captacion no encontrada para interaccion.")));
            }
            case InteraccionComercial.CLIENTE -> {
                if (datos.idCliente() == null) {
                    throw new ReglaNegocioException("El cliente interesado de la interaccion es obligatorio.");
                }
                interaccion.setCliente(clientes.buscarFicha(org, datos.idCliente())
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Cliente interesado no encontrado para interaccion.")));
            }
            default -> {
                if (datos.idOportunidad() == null) {
                    throw new ReglaNegocioException("La oportunidad de la interaccion es obligatoria.");
                }
                OportunidadComercial oportunidad = oportunidades.buscarFicha(org, datos.idOportunidad())
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Oportunidad comercial no encontrada para interaccion."));
                if (!oportunidad.estaAbierta()) {
                    throw new ReglaNegocioException("La oportunidad comercial debe estar ABIERTA.");
                }
                interaccion.setOportunidad(oportunidad);
            }
        }
        return null;
    }

    /**
     * El resultado de una interaccion de prospeccion MUEVE el embudo del
     * propietario (cable v1): son asignaciones directas de estado, no un
     * avance monotono — un "CONTACTADO" sobre una prospeccion en seguimiento
     * la devuelve a contactado. Se replica tal cual, ahora auditado.
     */
    private void avanzarEmbudo(Prospeccion prospeccion, String resultado, Actor actor) {
        LocalDate hoy = LocalDate.now();
        switch (resultado) {
            case "CONTACTADO" -> {
                prospeccion.marcarContacto(hoy);
                transiciones.aplicar(prospeccion, prospeccion.getId(), Prospeccion.CONTACTADO, actor,
                        "Contacto registrado desde una interaccion.");
            }
            case "REUNION_AGENDADA" -> {
                prospeccion.marcarReunion(hoy);
                transiciones.aplicar(prospeccion, prospeccion.getId(), Prospeccion.REUNION, actor,
                        "Reunion registrada desde una interaccion.");
            }
            case "PROPUESTA_ENVIADA" -> {
                prospeccion.marcarPropuesta(hoy);
                transiciones.aplicar(prospeccion, prospeccion.getId(), Prospeccion.EN_SEGUIMIENTO, actor,
                        "Propuesta entregada desde una interaccion.");
            }
            case "RECONTACTAR" -> {
                prospeccion.marcarSeguimiento(hoy);
                transiciones.aplicar(prospeccion, prospeccion.getId(), Prospeccion.EN_SEGUIMIENTO, actor,
                        "Seguimiento registrado desde una interaccion.");
            }
            // Cualquier otro resultado solo saca del estado inicial.
            default -> {
                if (Prospeccion.PROSPECTO.equals(prospeccion.estadoActual())) {
                    prospeccion.marcarContacto(hoy);
                    transiciones.aplicar(prospeccion, prospeccion.getId(), Prospeccion.CONTACTADO, actor,
                            "Contacto registrado desde una interaccion.");
                }
            }
        }
        prospecciones.save(prospeccion);
    }

    // ------------------------------------------------------------------
    // Alcance por rol: AGENTE RESPONSABLE de la interaccion (§6).
    // ------------------------------------------------------------------

    private InteraccionComercial cargarConAcceso(long id, Actor actor) {
        InteraccionComercial interaccion = interacciones.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Interaccion"));
        if (!alcances.alcanza(actor, interaccion.getAgente() != null
                ? interaccion.getAgente().getId() : null)) {
            throw new AccesoNoAutorizadoException();
        }
        return interaccion;
    }

    private static String canal(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaNegocioException("El canal de contacto es obligatorio.");
        }
        String valor = codigo.trim();
        if (!Vocabulario.CANALES.contains(valor)) {
            throw new ReglaNegocioException("Canal de contacto invalido: " + codigo);
        }
        return valor;
    }

    private static String resultado(String codigo, String contexto) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String valor = codigo.trim();
        if (!Vocabulario.RESULTADOS.contains(valor)) {
            throw new ReglaNegocioException("Resultado de interaccion invalido: " + codigo);
        }
        if (!Vocabulario.resultadoPermitido(contexto, valor)) {
            throw new ReglaNegocioException("Resultado no valido para " + contexto + ": " + codigo);
        }
        return valor;
    }

    private static String contextoFiltro(String contexto) {
        if (contexto == null || contexto.isBlank()) {
            return null;
        }
        return contexto.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * {@code grupo} parte el universo en dos: PROPIETARIO son las de
     * prospeccion o captacion; cualquier otro valor (salvo TODAS) es el
     * complemento. null = sin filtro.
     */
    private static Boolean grupo(String grupo) {
        if (grupo == null || grupo.isBlank() || "TODAS".equalsIgnoreCase(grupo.trim())) {
            return null;
        }
        return "PROPIETARIO".equalsIgnoreCase(grupo.trim());
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    // ------------------------------------------------------------------
    // Mapeo del cable: los nombres salen de la entidad colgada o, en su
    // defecto, de la oportunidad (asi lo arma la v1).
    // ------------------------------------------------------------------

    private static FichaInteraccion ficha(InteraccionComercial i) {
        String contexto = i.getContexto() == null || i.getContexto().isBlank()
                ? InteraccionComercial.OPORTUNIDAD
                : i.getContexto();
        OportunidadComercial oportunidad = i.getOportunidad();
        Prospeccion prospeccion = i.getProspeccion();
        Captacion captacionDirecta = i.getCaptacion();
        Captacion captacion = captacionDirecta != null ? captacionDirecta
                : (oportunidad != null ? oportunidad.getCaptacion() : null);
        DetalleCliente clienteDirecto = i.getCliente();
        DetalleCliente cliente = clienteDirecto != null ? clienteDirecto
                : (oportunidad != null ? oportunidad.getCliente() : null);

        Propiedad propiedadPropietario = switch (contexto) {
            case InteraccionComercial.PROSPECCION -> prospeccion != null ? prospeccion.getPropiedad() : null;
            case InteraccionComercial.CAPTACION ->
                    captacionDirecta != null ? captacionDirecta.getPropiedad() : null;
            default -> captacion != null ? captacion.getPropiedad() : null;
        };
        PersonaRol rolPropietario = propiedadPropietario != null
                ? propiedadPropietario.getRolPropietario() : null;
        String propietarioNombre = nombre(rolPropietario);
        String clienteNombre = nombre(cliente != null ? cliente.getRol() : null);
        boolean esPropietario = InteraccionComercial.PROSPECCION.equals(contexto)
                || InteraccionComercial.CAPTACION.equals(contexto);

        DetalleAgente agente = i.getAgente();
        String agenteNombre = agente != null ? nombre(agente.getRol()) : null;
        if (agenteNombre == null && oportunidad != null && oportunidad.getAgente() != null) {
            agenteNombre = nombre(oportunidad.getAgente().getRol());
        }

        return new FichaInteraccion(
                i.getId(), contexto,
                oportunidad != null ? oportunidad.getId() : null,
                prospeccion != null ? prospeccion.getId() : null,
                captacion != null ? captacion.getId() : null,
                cliente != null ? cliente.getId() : null,
                rolPropietario != null ? rolPropietario.getId() : null,
                prospeccion != null ? prospeccion.getCodigoProspeccion() : null,
                Fechas.local(i.getFechaHora()),
                i.getCanalContacto(), i.getResultado(), i.getObservaciones(), i.getTranscripcionNota(),
                clienteNombre, propietarioNombre,
                esPropietario ? "Propietario" : "Cliente",
                esPropietario ? propietarioNombre : clienteNombre,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                agenteNombre);
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
