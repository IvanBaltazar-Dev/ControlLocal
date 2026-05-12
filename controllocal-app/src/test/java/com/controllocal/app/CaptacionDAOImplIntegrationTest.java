package com.controllocal.app;

import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.impl.PropietarioDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;

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

        Propietario propietario = new Propietario();
        propietario.setTipoPersona(TipoPersona.NATURAL);
        propietario.setTipoDocumento("DNI");
        propietario.setNumeroDocumento("7" + sufijo);
        propietario.setNombresORazonSocial("Propietario Demo " + sufijo);
        propietario.setTelefono("900" + sufijo.substring(0, 5));
        propietario.setCorreo("propietario." + sufijo.toLowerCase() + "@controllocal.pe");
        propietario.setEstado(EstadoActivoInactivo.ACTIVO);

        Long id = dao.crear(propietario);
        System.out.println("[CREATE] Propietario creado con ID: " + id);

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[READ]   " + item.getNombresORazonSocial() + " | Estado: " + item.getEstado()
        ));

        propietario.setIdPropietario(id);
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

        Long id = dao.crear(local);
        System.out.println("[CREATE] Local creado con ID: " + id);

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoLocal() + " | Estado: " + item.getEstado()
        ));

        local.setIdLocal(id);
        return local;
    }

    private static Broker demoBroker(BrokerDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD BROKER SOPORTE -----");

        Broker broker = new Broker();
        broker.setNombres("Broker Demo");
        broker.setApellidos("Apellido " + sufijo);
        broker.setCorreo("broker." + sufijo.toLowerCase() + "@controllocal.pe");
        broker.setTelefono("950" + sufijo.substring(0, 5));
        broker.setNombreUsuario("brokercap" + sufijo.toLowerCase());
        broker.setContrasenaHash("HASH_BROKER_" + sufijo);
        broker.setEstado(EstadoActivoInactivo.ACTIVO);
        broker.setCodigoBroker("BRC" + sufijo);
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(false);

        Long id = dao.crear(broker);
        System.out.println("[CREATE] Broker creado con ID: " + id);

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoBroker() + " | Estado: " + item.getEstado()
        ));

        broker.setIdBroker(id);
        broker.setIdUsuarioInterno(id);
        return broker;
    }

    private static AgenteInmobiliario demoAgenteInmobiliario(AgenteInmobiliarioDAO dao, String sufijo) {
        System.out.println();
        System.out.println("----- CRUD AGENTE SOPORTE -----");

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setNombres("Agente Demo");
        agente.setApellidos("Apellido " + sufijo);
        agente.setCorreo("agente." + sufijo.toLowerCase() + "@controllocal.pe");
        agente.setTelefono("960" + sufijo.substring(0, 5));
        agente.setNombreUsuario("agentecap" + sufijo.toLowerCase());
        agente.setContrasenaHash("HASH_AGENTE_" + sufijo);
        agente.setEstado(EstadoActivoInactivo.ACTIVO);
        agente.setCodigoAgente("AGC" + sufijo);
        agente.setZonaAsignada("Miraflores");
        agente.setFechaIngreso(LocalDate.now().minusDays(10));
        agente.setEstadoOperativo(EstadoOperativoAgente.DISPONIBLE);

        Long id = dao.crear(agente);
        System.out.println("[CREATE] Agente creado con ID: " + id);

        dao.buscarPorId(id).ifPresent(item -> System.out.println(
                "[READ]   " + item.getCodigoAgente() + " | Estado: " + item.getEstadoOperativo()
        ));

        agente.setIdAgente(id);
        agente.setIdUsuarioInterno(id);
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
        agente.setIdUsuarioInterno(idAgente);

        Broker broker = new Broker();
        broker.setIdBroker(idBroker);
        broker.setIdUsuarioInterno(idBroker);

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
