package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.UnidadRelacionadaService;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.documentos.SeleccionInmueblePieza;
import com.controllocal.service.PropiedadUniversalService.FiltrosPropiedad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** C1: alcance real de lectura, conteo/página y reautorización sin caché de entidades. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class LecturaContextualPropiedadIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired CaptacionRepository captaciones;
    @Autowired UnidadRelacionadaService unidades;
    @Autowired PrecioLocalService precios;
    @Autowired LocalComercialService locales;
    @Autowired SeleccionInmueblePieza seleccionPieza;
    @Autowired EntityManager entidades;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    private Actor propio;
    private Actor ajeno;
    private String marca;
    private int secuencia;

    @BeforeEach void actores() {
        var equipo = new OrigenesDePrueba(jdbc, gestor, captura, contextos).empresa();
        propio = equipo.agente();
        ajeno = equipo.colega();
        marca = "C1L-" + Long.toUnsignedString(System.nanoTime(), 36);
    }

    @Test void tenantYResponsableFisicoHistoricoNoConcedenLectura() {
        long sinRelacion = inmueble("sin-relación");
        Actor supervisor = jdbc.queryForObject("""
                select r.organizacion_id,r.id_persona,r.id_persona_rol
                  from supervision_agente s
                  join persona_rol r on r.id_persona_rol=s.id_rol_broker
                 where s.organizacion_id=? and s.id_rol_agente=? and s.fecha_fin is null
                """, (r, n) -> new Actor(r.getLong(1), r.getLong(2), r.getLong(3), Actor.BROKER),
                propio.idOrganizacion(), propio.idRolOperativo());
        var traspaso = propiedades.asignarResponsable(sinRelacion, propio.idRolOperativo(),
                "Asignacion expresa para separar gobierno y lectura fisica",
                PropiedadUniversalService.ResponsableObservado.faltante(), supervisor);
        assertEquals("TRASPASO", traspaso.origen());
        assertThrows(NoEncontradoException.class, () -> propiedades.capacidadSobre(sinRelacion, null, propio));
        long relacionadoAjeno = inmueble("ajeno");
        prospectar(relacionadoAjeno, ajeno);
        assertThrows(NoEncontradoException.class, () -> propiedades.capacidadSobre(relacionadoAjeno, null, propio));
        assertNotNull(propiedades.capacidadSobre(relacionadoAjeno, null, ajeno));
    }

    @Test void prospeccionPropiaPermiteLeerSinGrantDeEdicion() {
        long id = inmueble("prospección");
        prospectar(id, propio);
        var capacidad = propiedades.capacidadSobre(id, null, propio);
        assertEquals(id, capacidad.id());
        assertFalse(capacidad.puedeEditar(), "la relación lectora no inventa permiso de edición física");
    }

    @Test void autoriaAprobadaLeeElRegistroInicialYNoPrevaleceSobreUnaRelacionAjena() {
        long id = inmueble("origen");
        jdbc.update("update propiedad set id_rol_incorporo=? where id_propiedad=?", propio.idRolOperativo(), id);
        assertNotNull(propiedades.capacidadSobre(id, null, propio));
        assertThrows(NoEncontradoException.class, () -> propiedades.capacidadSobre(id, null, ajeno));
        prospectar(id, ajeno);
        assertThrows(NoEncontradoException.class, () -> propiedades.capacidadSobre(id, null, propio));
        assertNotNull(propiedades.capacidadSobre(id, null, ajeno));
    }

    @Test void brokerLeeOrigenSoloMientrasConservaSupervisionActual() {
        List<Actor> supervisores = jdbc.query("""
                select r.organizacion_id,r.id_persona,r.id_persona_rol from supervision_agente s
                  join persona_rol r on r.id_persona_rol=s.id_rol_broker
                 where s.organizacion_id=? and s.id_rol_agente=? and s.fecha_fin is null
                 order by s.id_supervision limit 1
                """, (r, n) -> new Actor(r.getLong(1),r.getLong(2),r.getLong(3),Actor.BROKER),
                propio.idOrganizacion(), propio.idRolOperativo());
        assertEquals(1, supervisores.size(), "el agente del montaje debe conservar supervisor en la semilla");
        Actor broker = supervisores.getFirst();
        long id = inmueble("origen-supervisado");
        jdbc.update("update propiedad set id_rol_incorporo=? where id_propiedad=?", propio.idRolOperativo(), id);
        assertNotNull(propiedades.capacidadSobre(id, null, broker));
        jdbc.update("update supervision_agente set fecha_fin=current_date where organizacion_id=?"
                        + " and id_rol_broker=? and id_rol_agente=? and fecha_fin is null",
                propio.idOrganizacion(),broker.idRolOperativo(),propio.idRolOperativo());
        assertThrows(NoEncontradoException.class, () -> propiedades.capacidadSobre(id, null, broker));
    }

    @Test void relacionFisicaNoAmpliaLecturaDeLaUnidadAjena() {
        long origen = inmueble("matriz"); prospectar(origen, propio);
        long destino = inmueble("unidad"); prospectar(destino, ajeno);
        jdbc.update("update propiedad set partida_registral=? where id_propiedad=?", "PART-C1-" + destino, destino);
        jdbc.update("""
                insert into unidad_relacionada(organizacion_id,id_propiedad_principal,id_propiedad_relacionada,
                                               tipo_relacion,vigente_desde)
                values (?, ?, ?, 'I', current_date)
                """, propio.idOrganizacion(), origen, destino);
        assertTrue(unidades.deLaPropiedad(origen, propio).isEmpty());
        assertThrows(NoEncontradoException.class, () -> unidades.deLaPropiedad(origen, ajeno));
        prospectar(destino, propio);
        assertEquals(List.of(destino), unidades.deLaPropiedad(origen, propio).stream()
                .map(UnidadRelacionadaService.FichaUnidadRelacionada::idPropiedad).toList());
    }

    @Test void segundaLecturaVeReasignacionAunqueElEncargoSigaGestionadoPorJpa() {
        long id = inmueble("encargo");
        long encargo = encargar(id, propio, "A", true);
        var gestionado = captaciones.findById(encargo).orElseThrow();
        assertEquals(propio.idRolOperativo(), gestionado.getAgente().getId());
        assertNotNull(propiedades.capacidadSobre(id, null, propio));
        var inmuebleGestionado=seleccionPieza.exigirLecturaHistorica(propio.idOrganizacion(),id,propio);
        assertTrue(entidades.contains(inmuebleGestionado),"La propiedad debe estar gestionada antes del cambio de relación.");
        jdbc.update("update captacion set id_rol_agente=? where id_captacion=?", ajeno.idRolOperativo(), encargo);
        assertEquals(propio.idRolOperativo(), gestionado.getAgente().getId(), "se conserva la entidad antigua del contexto JPA");
        assertTrue(entidades.contains(inmuebleGestionado),"No se despeja el contexto JPA para reautorizar.");
        assertThrows(AccesoNoAutorizadoException.class,()->seleccionPieza.exigirLecturaHistorica(propio.idOrganizacion(),id,propio));
        assertThrows(NoEncontradoException.class, () -> propiedades.capacidadSobre(id, null, propio));
        assertNotNull(propiedades.capacidadSobre(id, null, ajeno));
        assertNotNull(seleccionPieza.exigirLecturaHistorica(ajeno.idOrganizacion(),id,ajeno));
    }

    @Test void busquedaCuentaYRecortaSoloResultadosAutorizados() {
        long primero = inmueble("visible-1"); prospectar(primero, propio);
        long segundo = inmueble("visible-2"); encargar(segundo, propio, "A", true);
        long oculto = inmueble("oculto"); prospectar(oculto, ajeno);
        var pagina = propiedades.listar(new FiltrosPropiedad(marca, null, null, null, null, 1, 1), propio);
        assertEquals(2, pagina.total());
        assertEquals(List.of(segundo), pagina.items().stream().map(PropiedadUniversalService.FilaPropiedad::id).toList());
        var siguiente = propiedades.listar(new FiltrosPropiedad(marca, null, null, null, null, 2, 1), propio);
        assertEquals(List.of(primero), siguiente.items().stream().map(PropiedadUniversalService.FilaPropiedad::id).toList());
        assertFalse(propiedades.opcionesDeFiltro(ajeno).distritos().isEmpty());
    }

    @Test void operacionAjenaNoSeDescubrePorFiltroNiSeIncluyeEnLaFila() {
        long id = inmueble("compartido"); prospectar(id, propio);
        encargar(id, propio, "A", true); encargar(id, ajeno, "V", true);
        var visibles = propiedades.listar(new FiltrosPropiedad(marca, null, null, null, null, 1, 20), propio);
        assertEquals(1, visibles.total());
        assertEquals(List.of("ALQUILER"), visibles.items().getFirst().encargos().stream()
                .map(PropiedadUniversalService.EncargoEnLista::operacion).toList());
        assertEquals(0, propiedades.listar(new FiltrosPropiedad(marca, null, null, null, "VENTA", 1, 20), propio).total());
    }

    @Test void serieDeEncargoNoExponeElEncargoAjeno() {
        long id = inmueble("serie"); prospectar(id, propio);
        long alquiler = encargar(id, propio, "A", true);
        long venta = encargar(id, ajeno, "V", true);
        long autorizado = hito(id, alquiler, "A", "U", 200);
        hito(id, venta, "V", "U", 300);
        assertEquals(List.of(autorizado), precios.listarPorLocal(id, propio).stream()
                .map(PrecioLocalService.FichaPrecio::id).toList());
        assertThrows(NoEncontradoException.class, () -> precios.listarPorLocal(id, new Actor(
                propio.idOrganizacion(), propio.idPersona(), 999999L, Actor.AGENTE)));
    }

    @Test void localLegadoNoHeredaPrecioFisicoNiPublicacionDeAlquilerAjeno() {
        long id = inmueble("local-legado"); prospectar(id, propio);
        jdbc.update("update propiedad set precio_referencial=9000,moneda_referencial='PEN' where id_propiedad=?", id);
        // Importe desconocido se representa en un Encargo en elaboración, nunca en un activo incompleto.
        long alquiler = encargar(id, ajeno, "A", false);
        var oculto = locales.buscarPorId(id, propio).orElseThrow();
        assertNull(oculto.precioReferencial());
        assertNull(oculto.monedaReferencial());
        assertNull(oculto.estadoPublicacion());
        jdbc.update("update captacion set id_rol_agente=? where id_captacion=?", propio.idRolOperativo(), alquiler);
        var propioSinCondicion = locales.buscarPorId(id, propio).orElseThrow();
        assertNull(propioSinCondicion.precioReferencial(), "no sustituye un importe desconocido con la columna física");
        assertEquals("B", propioSinCondicion.estadoPublicacion(), "el Encargo autorizado aún no tiene anuncio");
    }

    @Test void gobiernoNoAbreCarteraComercial() {
        Actor gobierno = new Actor(propio.idOrganizacion(), propio.idPersona(), 999L, Actor.TENANT_ADMIN);
        assertThrows(AccesoNoAutorizadoException.class,
                () -> propiedades.listar(new FiltrosPropiedad(null, null, null, null, null, 1, 20), gobierno));
        assertThrows(AccesoNoAutorizadoException.class, () -> propiedades.opcionesDeFiltro(gobierno));
    }

    private long inmueble(String detalle) {
        return jdbc.queryForObject("""
                insert into propiedad(codigo,direccion,distrito,metraje,tipo_inmueble,uso,estado_registro,
                                      disponibilidad_comercial,organizacion_id)
                values (?, ?, 'C1-LECTURA', 100, 'L', 'C', 'A', 'D', ?) returning id_propiedad
                """, Long.class, marca + "-" + ++secuencia, marca + " " + detalle, propio.idOrganizacion());
    }
    private void prospectar(long id, Actor actor) {
        jdbc.update("""
                insert into prospeccion(codigo_prospeccion,estado,id_propiedad,id_rol_agente,organizacion_id)
                values (?, 'P', ?, ?, ?)
                """, "P-" + id + "-" + actor.idRolOperativo(), id, actor.idRolOperativo(), actor.idOrganizacion());
    }
    private long encargar(long id, Actor actor, String operacion, boolean activo) {
        Long condicion = activo ? jdbc.queryForObject("""
                insert into condicion_economica_captacion(organizacion_id,tipo_operacion,
                    importe_referencia,moneda_referencia,tipo_comision,base_calculo,
                    valor_comision,moneda_comision,tratamiento_igv)
                values (?, ?, 2000, 'PEN', 'P', ?, 5, 'PEN', 'I') returning id_condicion_economica
                """, Long.class, actor.idOrganizacion(), operacion, "A".equals(operacion) ? "R" : "V") : null;
        return jdbc.queryForObject("""
                insert into captacion(codigo_captacion,fecha_captacion,fecha_inicio_encargo,fecha_fin_encargo,
                                      estado,id_propiedad,id_rol_agente,motivo_operacion,organizacion_id,
                                      exclusividad,id_condicion_economica)
                values (?, current_date, current_date, current_date + interval '1 year',
                        ?, ?, ?, ?, ?, ?, ?) returning id_captacion
                """, Long.class, "CAP-C1-" + id + operacion, activo ? "A" : "P", id,
                actor.idRolOperativo(), operacion, actor.idOrganizacion(), activo ? Boolean.FALSE : null, condicion);
    }

    private long hito(long idPropiedad, Long idCaptacion, String operacion, String hito, int monto) {
        return jdbc.queryForObject("""
                insert into precio_propiedad(organizacion_id,id_propiedad,id_captacion,operacion,hito,moneda,monto,fecha)
                values (?, ?, ?, ?, ?, 'PEN', ?, current_date) returning id_precio
                """, Long.class, propio.idOrganizacion(), idPropiedad, idCaptacion, operacion, hito, monto);
    }
}
