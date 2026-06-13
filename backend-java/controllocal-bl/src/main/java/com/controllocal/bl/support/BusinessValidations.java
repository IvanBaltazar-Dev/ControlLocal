package com.controllocal.bl.support;

import java.math.BigDecimal;

import com.controllocal.bl.BusinessException;
import com.controllocal.model.comercial.*;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.*;
import com.controllocal.model.usuario.enums.EstadoOperativoAgente;
import com.controllocal.model.usuario.enums.RolUsuarioInterno;

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

    // Limita el tamano de pagina para impedir consultas masivas a la base.
    public static void pagina(int limite, int desplazamiento) {
        if (limite < 1 || limite > 100) {
            throw new BusinessException("El tamano de pagina debe estar entre 1 y 100.");
        }
        if (desplazamiento < 0) {
            throw new BusinessException("El desplazamiento de pagina no puede ser negativo.");
        }
    }

    public static void persona(Persona persona) {
        if (persona == null) {
            throw new BusinessException("La persona es obligatoria.");
        }
        if (persona.getTipoPersona() == null) {
            throw new BusinessException("El tipo de persona es obligatorio.");
        }
        if (persona.getTipoDocumento() == null) {
            throw new BusinessException("El tipo de documento es obligatorio.");
        }
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
        if (broker.getEstadoAdministrativo() != null && broker.getEstadoAdministrativo() != com.controllocal.model.persona.enums.EstadoActivoInactivo.ACTIVO) {
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
        if (agente.getEstadoAdministrativo() != null
                && agente.getEstadoAdministrativo() != com.controllocal.model.persona.enums.EstadoActivoInactivo.ACTIVO) {
            throw new BusinessException("El agente debe estar ACTIVO.");
        }
        if (agente.getEstadoOperativo() != EstadoOperativoAgente.DISPONIBLE) {
            throw new BusinessException("El agente debe estar DISPONIBLE.");
        }
    }

    public static void brokerAgente(BrokerAgente brokerAgente) {
        if (brokerAgente == null) {
            throw new BusinessException("La asignacion broker-agente es obligatoria.");
        }
        id(idBroker(brokerAgente.getBroker()), "El broker supervisor");
        id(idAgente(brokerAgente.getAgente()), "El agente supervisado");
        if (brokerAgente.getFechaAsignacion() == null) {
            throw new BusinessException("La fecha de asignacion broker-agente es obligatoria.");
        }
        texto(brokerAgente.getMotivo(), "El motivo de asignacion broker-agente");
        if (brokerAgente.getFechaFin() != null
                && brokerAgente.getFechaFin().isBefore(brokerAgente.getFechaAsignacion())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de asignacion.");
        }
        if (brokerAgente.getEstado() == null) {
            throw new BusinessException("El estado de asignacion broker-agente es obligatorio.");
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
        id(idOportunidad(solicitud.getOportunidadComercial()), "La oportunidad comercial de la solicitud");
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
        if (motivo.getRazonPrincipal() == null) {
            throw new BusinessException("La razon principal es obligatoria.");
        }
        id(idAgente(motivo.getAgenteResponsable()), "El agente responsable del motivo");
        id(idOportunidad(motivo.getOportunidadComercial()), "La oportunidad comercial del motivo");
        int referencias = 0;
        referencias += motivo.getInteraccionComercial() != null ? 1 : 0;
        referencias += motivo.getVisita() != null ? 1 : 0;
        referencias += motivo.getSolicitudAlquiler() != null ? 1 : 0;
        if (referencias > 1) {
            throw new BusinessException("El motivo debe asociarse como maximo a una referencia de origen.");
        }
    }

    public static void oportunidad(OportunidadComercial oportunidad) {
        if (oportunidad == null) {
            throw new BusinessException("La oportunidad comercial es obligatoria.");
        }
        texto(oportunidad.getCodigoOportunidad(), "El codigo de oportunidad");
        if (oportunidad.getFechaRegistro() == null) {
            throw new BusinessException("La fecha de registro de oportunidad es obligatoria.");
        }
        if (oportunidad.getEstado() == null) {
            throw new BusinessException("El estado de oportunidad es obligatorio.");
        }
        id(idCliente(oportunidad.getClienteInteresado()), "El cliente interesado");
        id(idCaptacion(oportunidad.getCaptacion()), "La captacion de la oportunidad");
        id(idAgente(oportunidad.getAgenteResponsable()), "El agente responsable de la oportunidad");
    }

    public static void oportunidadAbierta(OportunidadComercial oportunidad) {
        if (oportunidad == null || oportunidad.getEstado() != EstadoOportunidadComercial.ABIERTA) {
            throw new BusinessException("La oportunidad comercial debe estar ABIERTA.");
        }
    }

    public static void documento(DocumentoSolicitud documento) {
        if (documento == null) {
            throw new BusinessException("El documento de solicitud es obligatorio.");
        }
        if (documento.getTipoDocumento() == null) {
            throw new BusinessException("El tipo de documento es obligatorio.");
        }
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
        id(idOportunidad(interaccion.getOportunidadComercial()), "La oportunidad comercial de la interaccion");
        id(idAgente(interaccion.getAgenteResponsable()), "El agente de la interaccion");
    }

    public static void visita(Visita visita) {
        if (visita == null) {
            throw new BusinessException("La visita es obligatoria.");
        }
        if (visita.getFechaVisita() == null || visita.getHoraVisita() == null || visita.getEstado() == null) {
            throw new BusinessException("La visita debe tener fecha, hora y estado.");
        }
        id(idOportunidad(visita.getOportunidadComercial()), "La oportunidad comercial de la visita");
        id(idAgente(visita.getAgenteResponsable()), "El agente de la visita");
    }

    public static void prospeccion(Prospeccion prospeccion) {
        if (prospeccion == null) {
            throw new BusinessException("La prospeccion es obligatoria.");
        }
        if (prospeccion.getEstado() == null) {
            throw new BusinessException("El estado de la prospeccion es obligatorio.");
        }
        if (prospeccion.getLocalComercial() == null
                || prospeccion.getLocalComercial().getIdLocal() == null
                || prospeccion.getLocalComercial().getIdLocal() <= 0) {
            throw new BusinessException("El local de la prospeccion es obligatorio.");
        }
        id(idAgente(prospeccion.getAgenteResponsable()), "El agente de la prospeccion");
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

    public static Long idOportunidad(OportunidadComercial oportunidad) {
        return oportunidad != null ? oportunidad.getIdOportunidad() : null;
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
