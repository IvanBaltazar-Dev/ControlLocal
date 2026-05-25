package com.controllocal.bl;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.ClienteInteresadoDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.ClienteInteresadoDAOImpl;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.impl.PersonaDAOImpl;
import com.controllocal.dao.impl.PropietarioDAOImpl;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.PersonaDAO;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.enums.TipoDocumentoIdentidad;
import com.controllocal.model.persona.enums.TipoPersona;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.enums.EstadoOperativoAgente;
import com.controllocal.model.usuario.enums.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

public class PruebaSolicitudAlquiler {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);

        try {
            CaptacionBusinessLogic captacionService = new CaptacionBusinessLogicImpl();
            OportunidadComercialBusinessLogic oportunidadService = new OportunidadComercialBusinessLogicImpl();
            SolicitudAlquilerBusinessLogic solicitudService = new SolicitudAlquilerBusinessLogicImpl();

            LocalComercial local = crearLocalDisponible(sufijo);
            AgenteInmobiliario agente = crearAgenteDisponible(sufijo);
            Broker broker = crearBrokerActivo(sufijo);
            ClienteInteresado cliente = crearClienteInteresado(sufijo);
            Captacion captacion = crearCaptacionActiva(captacionService, local, agente, broker, sufijo);
            OportunidadComercial oportunidad = crearOportunidadComercial(
                    oportunidadService,
                    cliente,
                    captacion,
                    agente,
                    sufijo
            );

            SolicitudAlquiler solicitud = new SolicitudAlquiler();
            solicitud.setCodigoSolicitud("SOL" + sufijo);
            solicitud.setFechaRegistro(LocalDate.now());
            solicitud.setMontoPropuesto(new BigDecimal("8500.00"));
            solicitud.setPlazoTentativo("36 meses");
            solicitud.setObservaciones("Cliente interesado en contrato de 3 anios.");
            solicitud.setOportunidadComercial(oportunidad);
            solicitud.setClienteInteresado(cliente);
            solicitud.setCaptacion(captacion);
            solicitud.setAgenteResponsable(agente);

            System.out.println("Registrando solicitud...");
            Long id = solicitudService.registrar(solicitud);
            System.out.println("Solicitud registrada correctamente.");
            System.out.println("ID generado: " + id);
        } catch (Exception ex) {
            System.err.println("Error en prueba:");
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static OportunidadComercial crearOportunidadComercial(
            OportunidadComercialBusinessLogic oportunidadService,
            ClienteInteresado cliente,
            Captacion captacion,
            AgenteInmobiliario agente,
            String sufijo
    ) {
        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setCodigoOportunidad("OPP" + sufijo);
        oportunidad.setClienteInteresado(cliente);
        oportunidad.setCaptacion(captacion);
        oportunidad.setAgenteResponsable(agente);
        oportunidad.setObservaciones("Oportunidad creada para prueba de solicitud.");

        Long idOportunidad = oportunidadService.registrar(oportunidad);
        oportunidad.setIdOportunidad(idOportunidad);
        return oportunidadService.buscarPorId(idOportunidad)
                .orElseThrow(() -> new IllegalStateException("No se encontro la oportunidad creada."));
    }

    private static Captacion crearCaptacionActiva(
            CaptacionBusinessLogic captacionService,
            LocalComercial local,
            AgenteInmobiliario agente,
            Broker broker,
            String sufijo
    ) {
        Captacion captacion = new Captacion();
        captacion.setCodigoCaptacion("CAPS" + sufijo);
        captacion.setFechaCaptacion(LocalDate.now());
        captacion.setComisionPactada(new BigDecimal("5.50"));
        captacion.setObservaciones("Captacion para prueba de solicitud");
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);

        Long idCaptacion = captacionService.registrar(captacion);
        captacion.setIdCaptacion(idCaptacion);

        captacionService.aprobarCaptacion(idCaptacion, broker.getIdBroker(), "Aprobada para prueba de solicitud.");
        return captacionService.buscarPorId(idCaptacion)
                .orElseThrow(() -> new IllegalStateException("No se encontro la captacion creada."));
    }

    private static ClienteInteresado crearClienteInteresado(String sufijo) {
        Persona persona = crearPersona(
                "93" + sufijo,
                "Cliente Solicitud " + sufijo,
                "951" + sufijo.substring(0, 5),
                "cliente.solicitud." + sufijo + "@controllocal.pe"
        );

        ClienteInteresado cliente = new ClienteInteresado();
        cliente.setPersona(persona);
        cliente.setRubroComercial("Restaurante");

        ClienteInteresadoDAO clienteDAO = new ClienteInteresadoDAOImpl();
        cliente.setIdCliente(clienteDAO.crear(cliente));
        return cliente;
    }

    private static LocalComercial crearLocalDisponible(String sufijo) {
        Persona persona = crearPersona(
                "94" + sufijo,
                "Propietario Solicitud " + sufijo,
                "952" + sufijo.substring(0, 5),
                "prop.solicitud." + sufijo + "@controllocal.pe"
        );

        Propietario propietario = new Propietario();
        propietario.setPersona(persona);
        PropietarioDAO propietarioDAO = new PropietarioDAOImpl();
        propietario.setIdPropietario(propietarioDAO.crear(propietario));

        LocalComercial local = new LocalComercial();
        local.setCodigoLocal("LCS" + sufijo);
        local.setDireccion("Av. Solicitud " + sufijo);
        local.setDistrito("Miraflores");
        local.setMetraje(new BigDecimal("95.00"));
        local.setPrecioReferencial(new BigDecimal("7800.00"));
        local.setRubroPermitido("Restaurante");
        local.setDescripcion("Local disponible para prueba de solicitud");
        local.setEstado(EstadoLocalComercial.DISPONIBLE);
        local.setIdPropietario(propietario.getIdPropietario());

        LocalComercialDAO localDAO = new LocalComercialDAOImpl();
        local.setIdLocal(localDAO.crear(local));
        return local;
    }

    private static AgenteInmobiliario crearAgenteDisponible(String sufijo) {
        UsuarioInterno usuario = crearUsuarioInterno(
                "95" + sufijo,
                "Agente Solicitud " + sufijo,
                "953" + sufijo.substring(0, 5),
                "agente.solicitud." + sufijo + "@controllocal.pe",
                "agentsol" + sufijo,
                "HASH_AGENTE_SOL_" + sufijo,
                RolUsuarioInterno.AGENTE
        );

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        agente.setPersona(usuario.getPersona());
        agente.setNombreUsuario(usuario.getNombreUsuario());
        agente.setContrasenaHash(usuario.getContrasenaHash());
        agente.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        agente.setRol(RolUsuarioInterno.AGENTE);
        agente.setCodigoAgente("AGS" + sufijo);
        agente.setZonaAsignada("Miraflores");
        agente.setFechaIngreso(LocalDate.now().minusDays(7));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);

        AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
        agente.setIdAgente(agenteDAO.crear(agente));
        return agente;
    }

    private static Broker crearBrokerActivo(String sufijo) {
        UsuarioInterno usuario = crearUsuarioInterno(
                "96" + sufijo,
                "Broker Solicitud " + sufijo,
                "954" + sufijo.substring(0, 5),
                "broker.solicitud." + sufijo + "@controllocal.pe",
                "brokersol" + sufijo,
                "HASH_BROKER_SOL_" + sufijo,
                RolUsuarioInterno.BROKER
        );

        Broker broker = new Broker();
        broker.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        broker.setPersona(usuario.getPersona());
        broker.setNombreUsuario(usuario.getNombreUsuario());
        broker.setContrasenaHash(usuario.getContrasenaHash());
        broker.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        broker.setRol(RolUsuarioInterno.BROKER);
        broker.setCodigoBroker("BRS" + sufijo);
        broker.setFechaDesignacion(LocalDate.now().minusDays(10));
        broker.setEsAdministrador(false);

        BrokerDAO brokerDAO = new BrokerDAOImpl();
        broker.setIdBroker(brokerDAO.crear(broker));
        return broker;
    }

    private static UsuarioInterno crearUsuarioInterno(
            String numeroDocumento,
            String nombrePersona,
            String telefono,
            String correo,
            String nombreUsuario,
            String contrasenaHash,
            RolUsuarioInterno rol
    ) {
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setPersona(crearPersona(numeroDocumento, nombrePersona, telefono, correo));
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setContrasenaHash(contrasenaHash);
        usuario.setEstadoAdministrativo(EstadoActivoInactivo.ACTIVO);
        usuario.setRol(rol);

        UsuarioInternoDAO usuarioDAO = new UsuarioInternoDAOImpl();
        usuario.setIdUsuarioInterno(usuarioDAO.crear(usuario));
        return usuario;
    }

    private static Persona crearPersona(String numeroDocumento, String nombre, String telefono, String correo) {
        Persona persona = new Persona();
        persona.setTipoPersona(TipoPersona.NATURAL);
        persona.setTipoDocumento(TipoDocumentoIdentidad.DNI);
        persona.setNumeroDocumento(numeroDocumento);
        persona.setNombresORazonSocial(nombre);
        persona.setTelefono(telefono);
        persona.setCorreo(correo);
        persona.setEstado(EstadoActivoInactivo.ACTIVO);

        PersonaDAO personaDAO = new PersonaDAOImpl();
        persona.setIdPersona(personaDAO.crear(persona));
        return persona;
    }
}
