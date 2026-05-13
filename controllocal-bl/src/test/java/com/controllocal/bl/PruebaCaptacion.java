package com.controllocal.bl;

import com.controllocal.bl.impl.customerAcquisitionLifecycleImpl;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.PersonaDAO;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.impl.PersonaDAOImpl;
import com.controllocal.dao.impl.PropietarioDAOImpl;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PruebaCaptacion {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);

        try {
            customerAcquisitionLifecycle service = new customerAcquisitionLifecycleImpl();

            LocalComercial local = crearLocalDisponible(sufijo);
            AgenteInmobiliario agente = crearAgenteDisponible(sufijo);

            Captacion nueva = new Captacion();
            nueva.setCodigoCaptacion("CAPT" + sufijo);
            nueva.setFechaCaptacion(LocalDate.now());
            nueva.setComisionPactada(new BigDecimal("5.50"));
            nueva.setObservaciones("Captacion creada desde PruebaCaptacion");
            nueva.setLocalComercial(local);
            nueva.setAgenteResponsable(agente);

            System.out.println("Intentando registrar captacion...");
            Long idGenerado = service.registerAcquisition(nueva);

            System.out.println("Exito. Captacion registrada con ID: " + idGenerado);
        } catch (Exception e) {
            System.err.println("Error en la prueba: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static LocalComercial crearLocalDisponible(String sufijo) {
        Persona persona = crearPersona(
                "91" + sufijo,
                "Propietario Captacion " + sufijo,
                "900" + sufijo.substring(0, 5),
                "prop.captacion." + sufijo + "@controllocal.pe"
        );

        Propietario propietario = new Propietario();
        propietario.setPersona(persona);
        PropietarioDAO propietarioDAO = new PropietarioDAOImpl();
        propietario.setIdPropietario(propietarioDAO.crear(propietario));

        LocalComercial local = new LocalComercial();
        local.setCodigoLocal("LCP" + sufijo);
        local.setDireccion("Av. Prueba " + sufijo);
        local.setDistrito("Surco");
        local.setMetraje(new BigDecimal("85.00"));
        local.setPrecioReferencial(new BigDecimal("4200.00"));
        local.setRubroPermitido("Retail");
        local.setDescripcion("Local disponible para prueba de captacion");
        local.setEstado(EstadoLocalComercial.DISPONIBLE);
        local.setIdPropietario(propietario.getIdPropietario());

        LocalComercialDAO localDAO = new LocalComercialDAOImpl();
        local.setIdLocal(localDAO.crear(local));
        return local;
    }

    private static AgenteInmobiliario crearAgenteDisponible(String sufijo) {
        UsuarioInterno usuario = crearUsuarioInterno(
                "92" + sufijo,
                "Agente Captacion " + sufijo,
                "960" + sufijo.substring(0, 5),
                "agente.captacion." + sufijo + "@controllocal.pe",
                "agentecap" + sufijo,
                "HASH_AGENTE_" + sufijo,
                RolUsuarioInterno.AGENTE
        );

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        agente.setPersona(usuario.getPersona());
        agente.setNombreUsuario(usuario.getNombreUsuario());
        agente.setContrasenaHash(usuario.getContrasenaHash());
        agente.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        agente.setRol(RolUsuarioInterno.AGENTE);
        agente.setCodigoAgente("AGP" + sufijo);
        agente.setZonaAsignada("Surco");
        agente.setFechaIngreso(LocalDate.now().minusDays(7));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);

        AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
        agente.setIdAgente(agenteDAO.crear(agente));
        return agente;
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
        persona.setTipoDocumento("DNI");
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
