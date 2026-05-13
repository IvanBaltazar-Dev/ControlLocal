package com.controllocal.app;

import com.controllocal.dao.*;
import com.controllocal.dao.impl.*;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public class CaptacionDAOImplIntegrationTest {

    public static void main(String[] args) {
        String sufijo = String.valueOf(System.currentTimeMillis()).substring(5);
        PropietarioDAO propietarioDAO = new PropietarioDAOImpl();
        LocalComercialDAO localDAO = new LocalComercialDAOImpl();
        BrokerDAO brokerDAO = new BrokerDAOImpl();
        AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
        CaptacionDAO captacionDAO = new CaptacionDAOImpl();

        try {
            Propietario propietario = demoPropietario(propietarioDAO, sufijo);
            LocalComercial local = demoLocalComercial(localDAO, propietario.getIdPropietario(), sufijo);
            Broker broker = demoBroker(brokerDAO, sufijo);
            AgenteInmobiliario agente = demoAgenteInmobiliario(agenteDAO, sufijo);
            demoCaptacion(captacionDAO, local.getIdLocal(), agente.getIdAgente(), broker.getIdBroker(), sufijo);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error en demo de CaptacionDAO.", e);
        }
    }

    private static Propietario demoPropietario(PropietarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD PROPIETARIO SOPORTE -----");

        Persona persona = ManualTestSupport.crearPersona(
                "87" + sufijo,
                "Propietario Demo " + sufijo,
                "900" + sufijo.substring(0, 5),
                "propietario." + sufijo.toLowerCase() + "@controllocal.pe"
        );

        Propietario propietario = new Propietario();
        propietario.setPersona(persona);
        propietario.setIdPropietario(dao.crear(propietario));
        System.out.println("[CREATE] Propietario creado con ID: " + propietario.getIdPropietario());

        dao.buscarPorId(propietario.getIdPropietario()).ifPresent(item -> System.out.println(
                "[READ]   " + item.getNombresORazonSocial() + " | Estado: " + item.getEstado()
        ));

        return propietario;
    }

    private static LocalComercial demoLocalComercial(LocalComercialDAO dao, Long idPropietario, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD LOCAL COMERCIAL SOPORTE -----");

        LocalComercial local = new LocalComercial();
        local.setCodigoLocal("LOC" + sufijo);
        local.setDireccion("Av. Demo " + sufijo + " 100");
        local.setDistrito("Surco");
        local.setMetraje(new BigDecimal("95.00"));
        local.setPrecioReferencial(new BigDecimal("5500.00"));
        local.setRubroPermitido("Retail");
        local.setDescripcion("Local soporte para demo de captacion");
        local.setEstado(EstadoLocalComercial.DISPONIBLE);
        local.setIdPropietario(idPropietario);
        local.setIdLocal(dao.crear(local));
        System.out.println("[CREATE] Local creado con ID: " + local.getIdLocal());

        dao.buscarPorId(local.getIdLocal()).ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoLocal() + " | Estado: " + item.getEstado()
        ));

        return local;
    }

    private static Broker demoBroker(BrokerDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD BROKER SOPORTE -----");

        UsuarioInterno usuario = ManualTestSupport.crearUsuarioInterno(
                "88" + sufijo,
                "Broker Demo " + sufijo,
                "950" + sufijo.substring(0, 5),
                "broker." + sufijo.toLowerCase() + "@controllocal.pe",
                "brokercap" + sufijo.toLowerCase(),
                "HASH_BROKER_" + sufijo,
                RolUsuarioInterno.BROKER
        );

        Broker broker = new Broker();
        broker.setIdUsuarioInterno(usuario.getIdUsuarioInterno());
        broker.setPersona(usuario.getPersona());
        broker.setNombreUsuario(usuario.getNombreUsuario());
        broker.setContrasenaHash(usuario.getContrasenaHash());
        broker.setEstadoAdministrativo(usuario.getEstadoAdministrativo());
        broker.setRol(RolUsuarioInterno.BROKER);
        broker.setCodigoBroker("BRC" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);
        broker.setIdBroker(dao.crear(broker));
        System.out.println("[CREATE] Broker creado con ID: " + broker.getIdBroker());

        dao.buscarPorId(broker.getIdBroker()).ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoBroker() + " | Estado: " + item.getEstado()
        ));

        return broker;
    }

    private static AgenteInmobiliario demoAgenteInmobiliario(AgenteInmobiliarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD AGENTE SOPORTE -----");

        UsuarioInterno usuario = ManualTestSupport.crearUsuarioInterno(
                "89" + sufijo,
                "Agente Demo " + sufijo,
                "960" + sufijo.substring(0, 5),
                "agente." + sufijo.toLowerCase() + "@controllocal.pe",
                "agentecap" + sufijo.toLowerCase(),
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
        agente.setCodigoAgente("AGC" + sufijo);
        agente.setZonaAsignada("Miraflores");
        agente.setFechaIngreso(LocalDate.now().minusDays(10));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);
        agente.setIdAgente(dao.crear(agente));
        System.out.println("[CREATE] Agente creado con ID: " + agente.getIdAgente());

        dao.buscarPorId(agente.getIdAgente()).ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoAgente() + " | Estado: " + item.getEstadoOperativo()
        ));

        return agente;
    }

    private static Captacion demoCaptacion(
            CaptacionDAO dao,
            Long idLocal,
            Long idAgente,
            Long idBroker,
            String sufijo
    ) {
        System.out.println();
        System.out.println("----- CRUD CAPTACION -----");

        LocalComercial local = new LocalComercial();
        local.setIdLocal(idLocal);

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(idAgente);

        Broker broker = new Broker();
        broker.setIdBroker(idBroker);

        Captacion captacion = new Captacion();
        captacion.setCodigoCaptacion("CAP" + sufijo);
        captacion.setFechaCaptacion(LocalDate.now());
        captacion.setFechaInicioVigencia(LocalDate.now());
        captacion.setComisionPactada(6.5);
        captacion.setObservaciones("Captacion demo " + sufijo);
        captacion.setEstado(EstadoCaptacion.PENDIENTE_REVISION);
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);

        Long id = dao.crear(captacion);
        System.out.println("[CREATE] Captacion creada con ID: " + id);

        Optional<Captacion> encontrado = dao.buscarPorId(id);
        encontrado.ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoCaptacion() + " | Estado: " + item.getEstado()
        ));

        if (encontrado.isPresent()) {
            Captacion actualizar = encontrado.get();
            actualizar.setEstado(EstadoCaptacion.ACTIVA);
            actualizar.setBrokerRevisor(broker);
            actualizar.setFechaRevision(LocalDateTime.now());
            actualizar.setObservacionRevision("Captacion aprobada en demo");
            dao.actualizar(actualizar);
        }

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[UPDATE] Estado: " + item.getEstado() + " | Broker: "
                        + (item.getBrokerRevisor() != null ? item.getBrokerRevisor().getIdBroker() : null)
        ));

        System.out.println("[LIST]   Total captaciones: " + dao.listarTodos().size());

        dao.eliminar(id);
        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[DELETE] Estado final: " + item.getEstado()
        ));

        captacion.setIdCaptacion(id);
        return captacion;
    }
}
