package com.controllocal.app;

import com.controllocal.dao.*;
import com.controllocal.dao.impl.*;
import com.controllocal.model.comercial.*;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class ControlLocalCrudManualIntegrationTest {

    private final PersonaDAO personaDAO = new PersonaDAOImpl();
    private final UsuarioInternoDAO usuarioDAO = new UsuarioInternoDAOImpl();
    private final BrokerDAO brokerDAO = new BrokerDAOImpl();
    private final AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
    private final PropietarioDAO propietarioDAO = new PropietarioDAOImpl();
    private final LocalComercialDAO localDAO = new LocalComercialDAOImpl();
    private final CaptacionDAO captacionDAO = new CaptacionDAOImpl();
    private final ClienteInteresadoDAO clienteDAO = new ClienteInteresadoDAOImpl();
    private final InteraccionComercialDAO interaccionDAO = new InteraccionComercialDAOImpl();
    private final VisitaDAO visitaDAO = new VisitaDAOImpl();
    private final SolicitudAlquilerDAO solicitudDAO = new SolicitudAlquilerDAOImpl();
    private final DocumentoSolicitudDAO documentoDAO = new DocumentoSolicitudDAOImpl();
    private final EvaluacionSolicitudDAO evaluacionDAO = new EvaluacionSolicitudDAOImpl();
    private final ReasignacionCaptacionDAO reasignacionDAO = new ReasignacionCaptacionDAOImpl();
    private final MotivoNoContinuidadDAO motivoDAO = new MotivoNoContinuidadDAOImpl();

    public static void main(String[] args) {
        new ControlLocalCrudManualIntegrationTest().ejecutar();
    }

    private void ejecutar() {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);

        Persona personaPropietario = crearPersona("DNI", "71" + sufijo, "Propietario Demo " + sufijo);
        System.out.println("1. Persona creada: " + personaPropietario.getIdPersona());

        Propietario propietario = new Propietario();
        propietario.setPersona(personaPropietario);
        propietario.setIdPropietario(propietarioDAO.crear(propietario));
        System.out.println("2. Propietario creado: " + propietario.getIdPropietario());

        LocalComercial local = new LocalComercial();
        local.setCodigoLocal("LOC" + sufijo);
        local.setDireccion("Av. Demo " + sufijo);
        local.setDistrito("Surco");
        local.setMetraje(new BigDecimal("95.50"));
        local.setPrecioReferencial(new BigDecimal("5500.00"));
        local.setRubroPermitido("Retail");
        local.setDescripcion("Local comercial de prueba manual");
        local.setEstado(EstadoLocalComercial.DISPONIBLE);
        local.setPropietario(propietario);
        local.setIdLocal(localDAO.crear(local));
        System.out.println("3. Local comercial creado: " + local.getIdLocal());

        Broker broker = crearBroker(sufijo);
        System.out.println("4. Usuario interno + Broker creados: " + broker.getIdUsuarioInterno() + " / " + broker.getIdBroker());

        AgenteInmobiliario agente = crearAgente("A1", sufijo);
        System.out.println("5. Usuario interno + Agente creados: " + agente.getIdUsuarioInterno() + " / " + agente.getIdAgente());

        Captacion captacion = new Captacion();
        captacion.setCodigoCaptacion("CAP" + sufijo);
        captacion.setFechaCaptacion(LocalDate.now());
        captacion.setFechaInicioVigencia(LocalDate.now());
        captacion.setComisionPactada(new BigDecimal("6.50"));
        captacion.setObservaciones("Captacion de prueba manual");
        captacion.setEstado(EstadoCaptacion.PENDIENTE_REVISION);
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);
        captacion.setBrokerRevisor(broker);
        captacion.setIdCaptacion(captacionDAO.crear(captacion));
        System.out.println("6. Captacion creada: " + captacion.getIdCaptacion());

        ClienteInteresado cliente = new ClienteInteresado();
        cliente.setPersona(crearPersona("DNI", "72" + sufijo, "Cliente Demo " + sufijo));
        cliente.setRubroComercial("Gastronomia");
        cliente.setIdCliente(clienteDAO.crear(cliente));
        System.out.println("7. Cliente interesado creado: " + cliente.getIdCliente());

        InteraccionComercial interaccion = new InteraccionComercial();
        interaccion.setFechaHora(LocalDateTime.now());
        interaccion.setCanalContacto(CanalContacto.WHATSAPP);
        interaccion.setObservaciones("Contacto inicial");
        interaccion.setResultado(ResultadoInteraccion.INTERESADO);
        interaccion.setClienteInteresado(cliente);
        interaccion.setCaptacion(captacion);
        interaccion.setAgenteResponsable(agente);
        interaccion.setIdInteraccion(interaccionDAO.crear(interaccion));
        System.out.println("8. Interaccion comercial creada: " + interaccion.getIdInteraccion());

        Visita visita = new Visita();
        visita.setFechaVisita(LocalDate.now().plusDays(1));
        visita.setHoraVisita(LocalTime.of(10, 30));
        visita.setObservaciones("Visita programada");
        visita.setEstado(EstadoVisita.PROGRAMADA);
        visita.setClienteInteresado(cliente);
        visita.setCaptacion(captacion);
        visita.setAgenteResponsable(agente);
        visita.setIdVisita(visitaDAO.crear(visita));
        System.out.println("9. Visita creada: " + visita.getIdVisita());

        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setCodigoSolicitud("SOL" + sufijo);
        solicitud.setFechaRegistro(LocalDate.now());
        solicitud.setMontoPropuesto(new BigDecimal("5200.00"));
        solicitud.setPlazoTentativo("12 meses");
        solicitud.setObservaciones("Solicitud de prueba manual");
        solicitud.setEstado(EstadoSolicitudAlquiler.REGISTRADA);
        solicitud.setClienteInteresado(cliente);
        solicitud.setCaptacion(captacion);
        solicitud.setAgenteResponsable(agente);
        solicitud.setIdSolicitud(solicitudDAO.crear(solicitud));
        System.out.println("10. Solicitud de alquiler creada: " + solicitud.getIdSolicitud());

        DocumentoSolicitud documento = new DocumentoSolicitud();
        documento.setTipoDocumento("DNI");
        documento.setNombreArchivo("dni-" + sufijo + ".pdf");
        documento.setRutaArchivo("/tmp/dni-" + sufijo + ".pdf");
        documento.setFechaEntrega(LocalDateTime.now());
        documento.setResultadoRevision(ResultadoRevisionDocumento.PENDIENTE);
        documento.setEstado(EstadoDocumentoSolicitud.REGISTRADO);
        documento.setSolicitudAlquiler(solicitud);
        documento.setIdDocumento(documentoDAO.crear(documento));
        System.out.println("11. Documento de solicitud creado: " + documento.getIdDocumento());

        EvaluacionSolicitud evaluacion = new EvaluacionSolicitud();
        evaluacion.setFechaEvaluacion(LocalDateTime.now());
        evaluacion.setResultado(ResultadoEvaluacionSolicitud.OBSERVADA);
        evaluacion.setObservaciones("Evaluacion preliminar");
        evaluacion.setResponsableEvaluacion(broker);
        evaluacion.setTipoEvaluacion(TipoEvaluacionSolicitud.PRELIMINAR);
        evaluacion.setSolicitudAlquiler(solicitud);
        evaluacion.setIdEvaluacion(evaluacionDAO.crear(evaluacion));
        System.out.println("12. Evaluacion de solicitud creada: " + evaluacion.getIdEvaluacion());

        AgenteInmobiliario agenteNuevo = crearAgente("A2", sufijo);
        ReasignacionCaptacion reasignacion = new ReasignacionCaptacion();
        reasignacion.setFechaCambio(LocalDateTime.now());
        reasignacion.setMotivo("Redistribucion de carga");
        reasignacion.setCaptacion(captacion);
        reasignacion.setAgenteAnterior(agente);
        reasignacion.setAgenteNuevo(agenteNuevo);
        reasignacion.setBrokerResponsable(broker);
        reasignacion.setIdReasignacion(reasignacionDAO.crear(reasignacion));
        System.out.println("13. Reasignacion creada: " + reasignacion.getIdReasignacion());

        MotivoNoContinuidad motivo = new MotivoNoContinuidad();
        motivo.setFechaHora(LocalDateTime.now());
        motivo.setRazonPrincipal("Cliente descarta la oportunidad");
        motivo.setObservaciones("No continua por presupuesto");
        motivo.setAgenteResponsable(agente);
        motivo.setInteraccionComercial(interaccion);
        motivo.setIdMotivoNoContinuidad(motivoDAO.crear(motivo));
        System.out.println("14. Motivo de no continuidad creado: " + motivo.getIdMotivoNoContinuidad());
    }

    private Persona crearPersona(String tipoDocumento, String numeroDocumento, String nombre) {
        Persona persona = new Persona();
        persona.setTipoPersona(TipoPersona.NATURAL);
        persona.setTipoDocumento(tipoDocumento);
        persona.setNumeroDocumento(numeroDocumento);
        persona.setNombresORazonSocial(nombre);
        persona.setTelefono("999999999");
        persona.setCorreo(numeroDocumento + "@controllocal.pe");
        persona.setEstado(EstadoActivoInactivo.ACTIVO);
        persona.setIdPersona(personaDAO.crear(persona));
        return persona;
    }

    private Broker crearBroker(String sufijo) {
        UsuarioInterno usuario = crearUsuario("73" + sufijo, "broker" + sufijo, RolUsuarioInterno.BROKER);
        Broker broker = new Broker();
        broker.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        broker.setPersona(usuario.getPersona());
        broker.setNombreUsuario(usuario.getNombreUsuario());
        broker.setContrasenaHash(usuario.getContrasenaHash());
        broker.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        broker.setRol(RolUsuarioInterno.BROKER);
        broker.setCodigoBroker("BRK" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);
        broker.setIdBroker(brokerDAO.crear(broker));
        return broker;
    }

    private AgenteInmobiliario crearAgente(String prefijo, String sufijo) {
        UsuarioInterno usuario = crearUsuario(prefijo + "74" + sufijo, "agente" + prefijo + sufijo, RolUsuarioInterno.AGENTE);
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        agente.setPersona(usuario.getPersona());
        agente.setNombreUsuario(usuario.getNombreUsuario());
        agente.setContrasenaHash(usuario.getContrasenaHash());
        agente.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        agente.setRol(RolUsuarioInterno.AGENTE);
        agente.setCodigoAgente("AG" + prefijo + sufijo);
        agente.setZonaAsignada("Lima Centro");
        agente.setFechaIngreso(LocalDate.now());
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);
        agente.setIdAgente(agenteDAO.crear(agente));
        return agente;
    }

    private UsuarioInterno crearUsuario(String numeroDocumento, String nombreUsuario, RolUsuarioInterno rol) {
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setPersona(crearPersona("DNI", numeroDocumento, "Usuario " + nombreUsuario));
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setContrasenaHash("HASH_" + nombreUsuario);
        usuario.setEstadoAdministrativo(EstadoActivoInactivo.ACTIVO);
        usuario.setRol(rol);
        usuario.setIdUsuarioInterno(usuarioDAO.crear(usuario));
        return usuario;
    }
}
