package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.inmueble.DetalleLocalComercial;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.HallazgoService.Hallazgo;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresDePropiedad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * <b>El hallazgo: misma evidencia, otra salida</b> (E2.3).
 *
 * <p>Lo que se protege aquí no es el número: es que el hallazgo <b>lleve consigo
 * por qué apareció</b>. Un descubrimiento sin evidencia es una corazonada, y una
 * corazonada no se puede contradecir — el agente no tiene forma de decidir si
 * vale la pena llamar al cliente.
 */
class HallazgoServiceImplTest {

    private static final long ORG = 1L;
    private static final long ROL_AGENTE = 30L;
    private static final long ID_CLIENTE = 77L;

    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final RequerimientoClienteRepository requerimientos =
            mock(RequerimientoClienteRepository.class);
    private final OportunidadComercialRepository oportunidades =
            mock(OportunidadComercialRepository.class);
    private final LectorPorAutoridad lector = mock(LectorPorAutoridad.class);

    private final HallazgoServiceImpl service =
            new HallazgoServiceImpl(captaciones, requerimientos, oportunidades, lector);

    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    @BeforeEach
    void escenarioBase() {
        when(captaciones.activasConLocalDisponible(anyLong(), anyLong()))
                .thenReturn(List.of(captacion(500L, "CAP-0007", propiedadQueEncaja())));
        when(oportunidades.idsClienteDelEquipo(anyLong(), any())).thenReturn(List.of(ID_CLIENTE));
        when(oportunidades.paresClienteCaptacionDelEquipo(anyLong(), any())).thenReturn(List.of());
        when(requerimientos.listarActivos(anyLong())).thenReturn(List.of(requerimientoQueEncaja()));
        when(lector.deVarias(anyLong(), any())).thenReturn(Map.of());
    }

    // ==================================================================

    @Test
    @DisplayName("un hallazgo lleva su evidencia: por que aparecio y que criterios cruza")
    void elHallazgoTraeSuEvidencia() {
        List<Hallazgo> hallazgos = service.de(agente);

        assertEquals(1, hallazgos.size());
        Hallazgo h = hallazgos.get(0);

        assertTrue(h.puntaje() > 0, "el puntaje es el mismo que el panel de coincidencias");
        assertFalse(h.cumple().isEmpty(),
                "sin criterios cumplidos, el hallazgo seria una corazonada");
        assertTrue(h.porQue().contains("Cruza"),
                "la interpretacion la redacta el dominio, no el cliente: " + h.porQue());
        assertEquals("cliente-detail/" + ID_CLIENTE, h.destino(),
                "lleva a donde se actua: la ficha del cliente, que es donde vive el panel");
    }

    @Test
    @DisplayName("la identidad es estable: la misma coincidencia tiene el mismo id entre lecturas")
    void laIdentidadEsEstable() {
        String primera = service.de(agente).get(0).id();
        String segunda = service.de(agente).get(0).id();

        assertEquals(primera, segunda,
                "si el id cambiara en cada lectura, la pantalla no podria recordar que ya lo miraste");
        assertTrue(primera.contains(String.valueOf(ID_CLIENTE)) && primera.contains("500"),
                "se compone de los DOS extremos que la producen: " + primera);
    }

    @Test
    @DisplayName("el titulo NO entra en la identidad: cambiarlo no convierte el hallazgo en otro")
    void elTituloNoEntraEnLaIdentidad() {
        String antes = service.de(agente).get(0).id();

        when(captaciones.activasConLocalDisponible(anyLong(), anyLong())).thenReturn(
                List.of(captacion(500L, "CAP-0007", conDireccion(propiedadQueEncaja(), "Otra calle 99"))));

        List<Hallazgo> despues = service.de(agente);
        assertEquals(antes, despues.get(0).id(), "misma coincidencia, mismo id");
        assertNotEquals("", despues.get(0).titulo());
    }

    @Test
    @DisplayName("lo que ya se propuso no vuelve a descubrirse")
    void loYaPropuestoNoSeRepite() {
        when(oportunidades.paresClienteCaptacionDelEquipo(anyLong(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{ID_CLIENTE, 500L}));

        assertTrue(service.de(agente).isEmpty(),
                "proponer dos veces lo mismo es ruido, no descubrimiento");
    }

    @Test
    @DisplayName("por debajo del umbral de la politica no hay hallazgo")
    void debajoDelUmbralNoHayHallazgo() {
        RequerimientoCliente imposible = requerimientoQueEncaja();
        imposible.setRubro("Taller mecanico");
        imposible.setTipoInmueble("OFICINA");
        imposible.setDistritos(List.of(distrito("Comas")));
        imposible.setRentaMin(new BigDecimal("50000"));
        imposible.setRentaMax(new BigDecimal("90000"));
        when(requerimientos.listarActivos(anyLong())).thenReturn(List.of(imposible));

        assertTrue(service.de(agente).isEmpty(),
                "el umbral es el de PoliticaComercial, no uno propio de esta clase");
    }

    @Test
    @DisplayName("cambiar la evidencia cambia el hallazgo")
    void cambiarLaEvidenciaCambiaElHallazgo() {
        Hallazgo antes = service.de(agente).get(0);

        // Un requerimiento que ya no pide ese distrito: mismo motor, otro resultado.
        RequerimientoCliente otro = requerimientoQueEncaja();
        otro.setDistritos(List.of(distrito("San Isidro")));
        when(requerimientos.listarActivos(anyLong())).thenReturn(List.of(otro));

        Hallazgo despues = service.de(agente).get(0);

        assertNotEquals(antes.puntaje(), despues.puntaje(),
                "si la evidencia cambia y el hallazgo no, no estaba leyendo la evidencia");
        assertFalse(despues.noCumple().isEmpty(),
                "y el pero se declara: un hallazgo que solo presume se decide peor");
    }

    @Test
    @DisplayName("los hallazgos de cartera son del agente; el broker tiene su propia superficie")
    void elBrokerNoRecibeHallazgosDeCartera() {
        assertTrue(service.de(broker).isEmpty());
        verifyNoInteractions(captaciones);
    }

    @Test
    @DisplayName("leerlo dos veces no cambia nada: un hallazgo es una lectura, no un hecho")
    void leerloDosVecesNoCambiaNada() {
        List<Hallazgo> primera = service.de(agente);
        List<Hallazgo> segunda = service.de(agente);

        // No hay repositorio de escritura inyectado, y esto lo confirma desde
        // fuera: si un hallazgo dejara rastro, la segunda lectura lo veria y el
        // rastro sobreviviria al cambio que lo invalide -- mandando a proponer
        // algo que ya no encaja.
        assertEquals(primera, segunda, "calcularlo de nuevo da lo mismo y no acumula");
    }

    // ------------------------------------------------------------------
    // Fixtures: una cafeteria en Miraflores que encaja con el local demo.
    // ------------------------------------------------------------------

    private static Captacion captacion(long id, String codigo, Propiedad propiedad) {
        Captacion captacion = new Captacion();
        ReflectionTestUtils.setField(captacion, "id", id);
        captacion.setCodigoCaptacion(codigo);
        captacion.setPropiedad(propiedad);
        return captacion;
    }

    private static Propiedad propiedadQueEncaja() {
        Propiedad propiedad = new Propiedad();
        propiedad.setCodigo("LOC-0001");
        propiedad.setDireccion("Av. Larco 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setTipoInmueble(Propiedad.TIPO_LOCAL);
        propiedad.setMetraje(new BigDecimal("120.00"));
        propiedad.setPrecioReferencial(new BigDecimal("8500.00"));
        propiedad.asignarDetalleLocal("Cafeteria y panaderia", null, null);
        return propiedad;
    }

    private static Propiedad conDireccion(Propiedad propiedad, String direccion) {
        propiedad.setDireccion(direccion);
        return propiedad;
    }

    private static RequerimientoCliente requerimientoQueEncaja() {
        RequerimientoCliente r = new RequerimientoCliente();
        DetalleCliente cliente = new DetalleCliente();
        ReflectionTestUtils.setField(cliente, "id", ID_CLIENTE);
        r.setCliente(cliente);
        r.setRubro("Cafeteria");
        r.setTipoInmueble("LOCAL_COMERCIAL");
        r.setRentaMin(new BigDecimal("3000"));
        r.setRentaMax(new BigDecimal("12000"));
        r.setMoneda("PEN");
        r.setMetrajeMin(new BigDecimal("80"));
        r.setMetrajeMax(new BigDecimal("200"));
        r.setDistritos(List.of(distrito("Miraflores")));
        return r;
    }

    private static Distrito distrito(String nombre) {
        Distrito distrito = new Distrito();
        distrito.setNombre(nombre);
        return distrito;
    }
}
