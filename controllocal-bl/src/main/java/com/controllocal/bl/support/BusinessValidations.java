package com.controllocal.bl.support;

import com.controllocal.bl.BusinessException;
import com.controllocal.model.comercial.*;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.*;

import java.math.BigDecimal;

public final class BusinessValidations {

    private BusinessValidations() {
    }

    public static void id(Long id, String campo) {
        if (id == null || id <= 0) {
            throw new BusinessException(campo + " debe ser mayor que cero.");
        }
    }

    public static void texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new BusinessException(campo + " es obligatorio.");
        }
    }

    public static void persona(Persona persona) {
        if (persona == null) {
            throw new BusinessException("La persona es obligatoria.");
        }
        if (persona.getTipoPersona() == null) {
            throw new BusinessException("El tipo de persona es obligatorio.");
        }
        texto(persona.getTipoDocumento(), "El tipo de documento");
        texto(persona.getNumeroDocumento(), "El numero de documento");
        texto(persona.getNombresORazonSocial(), "El nombre o razon social");
        if (persona.getEstado() == null) {
            throw new BusinessException("El estado de la persona es obligatorio.");
        }
    }

    public static void usuarioInterno(UsuarioInterno usuario) {
        if (usuario == null) {
            throw new BusinessException("El usuario interno es obligatorio.");
        }
        id(idPersona(usuario.getPersona()), "La persona del usuario interno");
        texto(usuario.getNombreUsuario(), "El nombre de usuario");
        texto(usuario.getContrasenaHash(), "La contrasena hash");
        if (usuario.getEstadoAdministrativo() == null) {
            throw new BusinessException("El estado administrativo del usuario es obligatorio.");
        }
        if (usuario.getRol() == null) {
            throw new BusinessException("El rol del usuario es obligatorio.");
        }
    }

    public static void broker(Broker broker) {
        if (broker == null) {
            throw new BusinessException("El broker es obligatorio.");
        }
        id(broker.getIdUsuarioInterno(), "El usuario interno del broker");
        texto(broker.getCodigoBroker(), "El codigo del broker");
        if (broker.getFechaDesignacion() == null) {
            throw new BusinessException("La fecha de designacion del broker es obligatoria.");
        }
        if (broker.getRol() != null && broker.getRol() != RolUsuarioInterno.BROKER) {
            throw new BusinessException("El usuario asociado al broker debe tener rol BROKER.");
        }
    }

    public static void brokerValido(Broker broker) {
        if (broker == null) {
            throw new BusinessException("Broker no encontrado.");
        }
        if (broker.getEstadoAdministrativo() != null && broker.getEstadoAdministrativo() != com.controllocal.model.persona.EstadoActivoInactivo.ACTIVO) {
            throw new BusinessException("El broker no esta activo.");
        }
    }

    public static void brokerAdministrador(Broker broker) {
        brokerValido(broker);
        if (!broker.isEsAdministrador()) {
            throw new BusinessException("Solo el broker administrador puede realizar esta operacion.");
        }
    }

    public static void agente(AgenteInmobiliario agente) {
        if (agente == null) {
            throw new BusinessException("El agente inmobiliario es obligatorio.");
        }
        id(agente.getIdUsuarioInterno(), "El usuario interno del agente");
        texto(agente.getCodigoAgente(), "El codigo del agente");
        if (agente.getFechaIngreso() == null) {
            throw new BusinessException("La fecha de ingreso del agente es obligatoria.");
        }
        if (agente.getEstadoOperativo() == null) {
            throw new BusinessException("El estado operativo del agente es obligatorio.");
        }
        if (agente.getRol() != null && agente.getRol() != RolUsuarioInterno.AGENTE) {
            throw new BusinessException("El usuario asociado al agente debe tener rol AGENTE.");
        }
    }

    public static void agenteDisponible(AgenteInmobiliario agente) {
        if (agente == null) {
            throw new BusinessException("Agente no encontrado.");
        }
        if (agente.getEstadoOperativo() != EstadoOperativoAgente.DISPONIBLE) {
            throw new BusinessException("El agente debe estar DISPONIBLE.");
        }
    }

    public static void propietario(Propietario propietario) {
        if (propietario == null || propietario.getPersona() == null) {
            throw new BusinessException("El propietario debe estar asociado a una persona.");
        }
        persona(propietario.getPersona());
    }

    public static void cliente(ClienteInteresado cliente) {
        if (cliente == null || cliente.getPersona() == null) {
            throw new BusinessException("El cliente interesado debe estar asociado a una persona.");
        }
        persona(cliente.getPersona());
    }

    public static void local(LocalComercial local) {
        if (local == null) {
            throw new BusinessException("El local comercial es obligatorio.");
        }
        texto(local.getCodigoLocal(), "El codigo del local");
        texto(local.getDireccion(), "La direccion");
        texto(local.getDistrito(), "El distrito");
        positivo(local.getMetraje(), "El metraje");
        noNegativo(local.getPrecioReferencial(), "El precio referencial");
        texto(local.getRubroPermitido(), "El rubro permitido");
        id(local.getIdPropietario(), "El propietario del local");
        if (local.getEstado() == null) {
            throw new BusinessException("El estado del local es obligatorio.");
        }
    }

    public static void captacion(Captacion captacion) {
        if (captacion == null) {
            throw new BusinessException("La captacion es obligatoria.");
        }
        texto(captacion.getCodigoCaptacion(), "El codigo de captacion");
        if (captacion.getFechaCaptacion() == null) {
            throw new BusinessException("La fecha de captacion es obligatoria.");
        }
        noNegativo(captacion.getComisionPactada(), "La comision pactada");
        id(idLocal(captacion), "El local de la captacion");
        id(idAgente(captacion.getAgenteResponsable()), "El agente responsable");
    }

    public static void captacionPendienteRevision(Captacion captacion) {
        if (captacion.getEstado() != EstadoCaptacion.PENDIENTE_REVISION
                && captacion.getEstado() != EstadoCaptacion.OBSERVADA) {
            throw new BusinessException("La captacion debe estar pendiente de revision u observada.");
        }
    }

    public static void captacionActiva(Captacion captacion) {
        if (captacion == null || captacion.getEstado() != EstadoCaptacion.ACTIVA) {
            throw new BusinessException("La captacion debe estar ACTIVA.");
        }
    }

    public static void solicitud(SolicitudAlquiler solicitud) {
        if (solicitud == null) {
            throw new BusinessException("La solicitud de alquiler es obligatoria.");
        }
        texto(solicitud.getCodigoSolicitud(), "El codigo de solicitud");
        if (solicitud.getFechaRegistro() == null) {
            throw new BusinessException("La fecha de registro de solicitud es obligatoria.");
        }
        positivo(solicitud.getMontoPropuesto(), "El monto propuesto");
        id(idCliente(solicitud.getClienteInteresado()), "El cliente interesado");
        id(idCaptacion(solicitud.getCaptacion()), "La captacion de la solicitud");
        id(idAgente(solicitud.getAgenteResponsable()), "El agente responsable de la solicitud");
    }

    public static void evaluacion(EvaluacionSolicitud evaluacion) {
        if (evaluacion == null) {
            throw new BusinessException("La evaluacion es obligatoria.");
        }
        if (evaluacion.getResultado() == null) {
            throw new BusinessException("El resultado de evaluacion es obligatorio.");
        }
        if (evaluacion.getTipoEvaluacion() == null) {
            throw new BusinessException("El tipo de evaluacion es obligatorio.");
        }
        id(idBroker(evaluacion.getResponsableEvaluacion()), "El broker responsable de evaluacion");
        id(idSolicitud(evaluacion.getSolicitudAlquiler()), "La solicitud evaluada");
    }

    public static void motivo(MotivoNoContinuidad motivo) {
        if (motivo == null) {
            throw new BusinessException("El motivo de no continuidad es obligatorio.");
        }
        texto(motivo.getRazonPrincipal(), "La razon principal");
        id(idAgente(motivo.getAgenteResponsable()), "El agente responsable del motivo");
        int referencias = 0;
        referencias += motivo.getInteraccionComercial() != null ? 1 : 0;
        referencias += motivo.getVisita() != null ? 1 : 0;
        referencias += motivo.getSolicitudAlquiler() != null ? 1 : 0;
        if (referencias != 1) {
            throw new BusinessException("El motivo debe asociarse a una sola referencia principal.");
        }
    }

    public static void documento(DocumentoSolicitud documento) {
        if (documento == null) {
            throw new BusinessException("El documento de solicitud es obligatorio.");
        }
        texto(documento.getTipoDocumento(), "El tipo de documento");
        texto(documento.getNombreArchivo(), "El nombre de archivo");
        if (documento.getFechaEntrega() == null) {
            throw new BusinessException("La fecha de entrega es obligatoria.");
        }
        if (documento.getEstado() == null) {
            throw new BusinessException("El estado del documento es obligatorio.");
        }
        id(idSolicitud(documento.getSolicitudAlquiler()), "La solicitud del documento");
    }

    public static void interaccion(InteraccionComercial interaccion) {
        if (interaccion == null) {
            throw new BusinessException("La interaccion comercial es obligatoria.");
        }
        if (interaccion.getFechaHora() == null) {
            throw new BusinessException("La fecha y hora de interaccion son obligatorias.");
        }
        if (interaccion.getCanalContacto() == null || interaccion.getResultado() == null) {
            throw new BusinessException("La interaccion debe tener canal y resultado.");
        }
        id(idCliente(interaccion.getClienteInteresado()), "El cliente de la interaccion");
        id(idCaptacion(interaccion.getCaptacion()), "La captacion de la interaccion");
        id(idAgente(interaccion.getAgenteResponsable()), "El agente de la interaccion");
    }

    public static void visita(Visita visita) {
        if (visita == null) {
            throw new BusinessException("La visita es obligatoria.");
        }
        if (visita.getFechaVisita() == null || visita.getHoraVisita() == null || visita.getEstado() == null) {
            throw new BusinessException("La visita debe tener fecha, hora y estado.");
        }
        id(idCliente(visita.getClienteInteresado()), "El cliente de la visita");
        id(idCaptacion(visita.getCaptacion()), "La captacion de la visita");
        id(idAgente(visita.getAgenteResponsable()), "El agente de la visita");
    }

    public static Long idPersona(Persona persona) {
        return persona != null ? persona.getIdPersona() : null;
    }

    public static Long idAgente(AgenteInmobiliario agente) {
        return agente != null ? agente.getIdAgente() : null;
    }

    public static Long idBroker(Broker broker) {
        return broker != null ? broker.getIdBroker() : null;
    }

    public static Long idCaptacion(Captacion captacion) {
        return captacion != null ? captacion.getIdCaptacion() : null;
    }

    public static Long idLocal(Captacion captacion) {
        return captacion != null && captacion.getLocalComercial() != null
                ? captacion.getLocalComercial().getIdLocal()
                : null;
    }

    public static Long idCliente(ClienteInteresado cliente) {
        return cliente != null ? cliente.getIdCliente() : null;
    }

    public static Long idSolicitud(SolicitudAlquiler solicitud) {
        return solicitud != null ? solicitud.getIdSolicitud() : null;
    }

    private static void positivo(BigDecimal valor, String campo) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(campo + " debe ser mayor que cero.");
        }
    }

    private static void noNegativo(BigDecimal valor, String campo) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(campo + " no puede ser negativo.");
        }
    }
}
