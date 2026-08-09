package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comun.EstadosDominio.Codigo;
import com.controllocal.persistence.repositorio.AlertaRepository;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ComisionLiquidacionRepository;
import com.controllocal.persistence.repositorio.ComisionMovimientoRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.TareaRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate de persistencia real: Flyway crea una PostgreSQL vacia, Hibernate
 * valida el esquema y Spring Data compila todos los JPQL y metodos derivados
 * al levantar el contexto. Ademas se ejecutan las consultas/proyecciones que
 * transportan estados para detectar cruces String/enum que el javac no ve.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RepositorioEstadosIntegrationTest {

    private static final Pattern CODIGO_SQL = Pattern.compile("'([A-Z])'");

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadRepository propiedades;
    @Autowired CaptacionRepository captaciones;
    @Autowired ProspeccionRepository prospecciones;
    @Autowired OportunidadComercialRepository oportunidades;
    @Autowired VisitaRepository visitas;
    @Autowired InteraccionComercialRepository interacciones;
    @Autowired SolicitudAlquilerRepository solicitudes;
    @Autowired ContratoAlquilerRepository contratos;
    @Autowired ComisionLiquidacionRepository comisiones;
    @Autowired ComisionMovimientoRepository movimientos;
    @Autowired AlertaRepository alertas;
    @Autowired TareaRepository tareas;
    @Autowired PublicacionRepository publicaciones;
    @Autowired RequerimientoClienteRepository requerimientos;

    @Test
    void todasLasColumnasDeEstadoPersistenCodigosUnitarios() {
        List<String> columnasNoUnitarias = jdbc.queryForList("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = 'public'
                  and (column_name = 'estado' or column_name like 'estado\\_%' escape '\\')
                  and character_maximum_length <> 1
                order by table_name, column_name
                """, String.class);
        assertEquals(List.of(), columnasNoUnitarias);

        List<String> valoresNoUnitarios = jdbc.queryForList("""
                select tabla_columna from (
                    select 'persona.estado' tabla_columna from persona where length(estado) <> 1
                    union all select 'credencial_usuario.estado_administrativo' from credencial_usuario where length(estado_administrativo) <> 1
                    union all select 'detalle_agente.estado_operativo' from detalle_agente where length(estado_operativo) <> 1
                    union all select 'organizacion.estado' from organizacion where length(estado) <> 1
                    union all select 'usuario_organizacion.estado' from usuario_organizacion where length(estado) <> 1
                    union all select 'finalidad_tratamiento.estado' from finalidad_tratamiento where length(estado) <> 1
                    union all select 'propiedad.estado_registro' from propiedad where length(estado_registro) <> 1
                    union all select 'propiedad.disponibilidad_comercial' from propiedad where length(disponibilidad_comercial) <> 1
                    union all select 'publicacion.estado' from publicacion where length(estado) <> 1
                    union all select 'captacion.estado' from captacion where length(estado) <> 1
                    union all select 'prospeccion.estado' from prospeccion where length(estado) <> 1
                    union all select 'requerimiento_cliente.estado' from requerimiento_cliente where length(estado) <> 1
                    union all select 'oportunidad_comercial.estado' from oportunidad_comercial where length(estado) <> 1
                    union all select 'visita.estado' from visita where length(estado) <> 1
                    union all select 'solicitud_alquiler.estado' from solicitud_alquiler where length(estado) <> 1
                    union all select 'documento_solicitud.estado' from documento_solicitud where length(estado) <> 1
                    union all select 'contrato_alquiler.estado_contrato' from contrato_alquiler where length(estado_contrato) <> 1
                    union all select 'comision_liquidacion.estado' from comision_liquidacion where length(estado) <> 1
                    union all select 'alerta.estado' from alerta where length(estado) <> 1
                    union all select 'tarea.estado' from tarea where length(estado) <> 1
                    union all select 'regularizacion_dato_economico.estado' from regularizacion_dato_economico where length(estado) <> 1
                    union all select 'token_acceso.estado' from token_acceso where length(estado) <> 1
                    union all select 'factor_autenticacion.estado' from factor_autenticacion where length(estado) <> 1
                    union all select 'concesion_recuperacion.estado' from concesion_recuperacion where length(estado) <> 1
                    union all select 'historial_estado.estado_anterior' from historial_estado where estado_anterior is not null and length(estado_anterior) <> 1
                    union all select 'historial_estado.estado_nuevo' from historial_estado where length(estado_nuevo) <> 1
                ) estados
                """, String.class);
        assertEquals(List.of(), valoresNoUnitarios);
    }

    @Test
    void checksPostgresCoincidenExactamenteConEnumsCentrales() {
        for (RestriccionEstado restriccion : restriccionesEstado()) {
            String definicion = jdbc.queryForObject("""
                    select pg_get_constraintdef(oid)
                    from pg_constraint
                    where conname = ?
                    """, String.class, restriccion.nombre());
            assertNotNull(definicion, restriccion.nombre());

            Set<String> enSql = new LinkedHashSet<>();
            Matcher matcher = CODIGO_SQL.matcher(definicion);
            while (matcher.find()) enSql.add(matcher.group(1));

            Set<String> enJava = new LinkedHashSet<>();
            Arrays.stream(restriccion.tipoEnum().getEnumConstants())
                    .map(Codigo.class::cast)
                    .map(Codigo::codigo)
                    .forEach(enJava::add);
            assertEquals(enJava, enSql, restriccion.nombre() + " vs "
                    + restriccion.tipoEnum().getSimpleName());
        }
    }

    @Test
    void parametrosEstadoDeRepositoriosSonString() {
        for (Class<?> repositorio : repositoriosConEstado()) {
            for (Method metodo : repositorio.getMethods()) {
                Query consulta = AnnotationUtils.findAnnotation(metodo, Query.class);
                Parameter[] parametros = metodo.getParameters();
                for (Parameter parametro : parametros) {
                    Param nombre = anotacion(parametro, Param.class);
                    if (nombre != null && nombre.value().toLowerCase().contains("estado")) {
                        assertEquals(String.class, parametro.getType(),
                                repositorio.getSimpleName() + "." + metodo.getName());
                    }
                }
                if (consulta != null && consulta.value().contains(":estado")) {
                    assertTrue(List.of(parametros).stream().anyMatch(p -> p.getType() == String.class
                                    && anotacion(p, Param.class) != null
                                    && anotacion(p, Param.class).value().equals("estado")),
                            repositorio.getSimpleName() + "." + metodo.getName());
                }
            }
        }
    }

    @Test
    @Transactional
    void ejecutaJpqlDerivadasYProyeccionesQueInvolucranEstados() {
        long org = 1L;
        Collection<Long> sinRoles = List.of(-1L);
        PageRequest pagina = PageRequest.of(0, 5);

        assertNotNull(propiedades.buscar(org, null, null, pagina));
        assertNotNull(propiedades.buscar(org, null, "D", pagina));
        assertNotNull(propiedades.contarPorEstado(org, null));
        assertNotNull(propiedades.findByOrganizacionIdAndRolPropietarioIdAndIdNotOrderById(
                org, -1L, -1L));

        assertNotNull(captaciones.buscar(org, true, sinRoles, "A", null, null, pagina));
        assertNotNull(captaciones.pendientes(org, true, sinRoles, "P", null, null, pagina));
        assertNotNull(captaciones.carteraDelEquipo(org, true, sinRoles, null, null, pagina));
        assertNotNull(captaciones.resumenCarteraDelEquipo(org, true, sinRoles, null, null));
        assertNotNull(captaciones.distritosDelEquipo(org, true, sinRoles, null, null));
        assertFalse(captaciones.existsByOrganizacionIdAndPropiedadIdAndEstado(org, -1L, "A"));

        assertNotNull(prospecciones.buscar(org, true, sinRoles, "GESTION", null,
                null, null, null, false, sinRoles, null, pagina));
        assertNotNull(oportunidades.buscar(org, true, true, sinRoles, null, null, "A", pagina));
        assertNotNull(oportunidades.contarPorEstado(org, true, true, sinRoles, null, null, null));
        assertNotNull(visitas.buscar(org, true, true, sinRoles, null, "P", null, pagina));
        assertNotNull(visitas.contarPorEstado(org, true, true, sinRoles, null, null, null));
        assertNotNull(visitas.distritosDisponibles(org, true, true, sinRoles));

        // Busqueda por conjunto de candidatos (§5): las ramas nativas se
        // ejecutan de verdad contra PostgreSQL, que es donde se caen si el SQL
        // esta mal escrito — no en el arranque del contexto.
        String rolesArray = "{-1}";
        assertNotNull(oportunidades.idsPorTexto(org, true, true, rolesArray,
                null, null, null, "larco", 10, 0));
        assertNotNull(oportunidades.contarPorTexto(org, true, true, rolesArray,
                null, null, null, "larco"));
        assertNotNull(oportunidades.contarPorEstadoConTexto(org, true, true, rolesArray,
                null, null, null, "larco"));
        assertNotNull(visitas.idsPorTexto(org, true, true, rolesArray,
                null, null, null, "larco", 10, 0));
        assertNotNull(visitas.contarPorTexto(org, true, true, rolesArray,
                null, null, null, "larco"));
        assertNotNull(visitas.contarPorEstadoConTexto(org, true, true, rolesArray,
                null, null, null, "larco"));
        assertNotNull(interacciones.idsPorTexto(org, true, rolesArray,
                null, null, null, null, null, null, null, null, "larco", 10, 0));
        assertNotNull(interacciones.contarPorTexto(org, true, rolesArray,
                null, null, null, null, null, null, null, null, "larco"));
        assertNotNull(visitas.listarProximas(org, true, true, sinRoles, LocalDate.now(), pagina));
        assertNotNull(solicitudes.buscar(org, true, sinRoles, null, null, null, null, null, pagina));
        // El cubo PENDIENTES no es un estado: se resuelve como E + O.
        assertNotNull(solicitudes.buscar(org, true, sinRoles, null, null, null, "PENDIENTES", null,
                pagina));
        assertNotNull(solicitudes.contarPorEstado(org, true, sinRoles, null, null, null, null, null));
        assertNotNull(solicitudes.distritosDisponibles(org, true, sinRoles, null, null, null, null,
                null));
        assertNotNull(solicitudes.agentesDisponibles(org, true, sinRoles, null, null, null, null));
        assertNotNull(solicitudes.idsPorTexto(org, true, rolesArray, null, null, null, null, null,
                "sol", 10, 0));
        assertNotNull(solicitudes.contarPorTexto(org, true, rolesArray, null, null, null, null, null,
                "sol"));
        assertNotNull(solicitudes.contarPorEstadoConTexto(org, true, rolesArray, null, null, null,
                null, null, "sol"));
        assertNotNull(solicitudes.porEstadoDelAgente(org, -1L, "A"));

        // Sin texto: el WHERE comun, ya sin el OR cruzado de cuatro tablas.
        assertNotNull(contratos.resumenCierres(org, true, true, sinRoles, null, null));
        assertNotNull(contratos.comisionesGeneradas(org, true, true, sinRoles, null, null));
        assertNotNull(contratos.repartosPorMoneda(org, true, true, sinRoles, null, null));
        assertNotNull(contratos.movimientosPorMoneda(org, true, true, sinRoles, null, null));
        assertNotNull(contratos.distritosDeCierres(org, true, true, sinRoles, null, null));
        assertNotNull(contratos.agentesDeCierres(org, true, true, sinRoles, null, null));

        // Con texto: el conjunto de candidatos (RC-003). Son nativas, asi que
        // el unico sitio donde se compilan de verdad es contra PostgreSQL: un
        // alias o un cast mal puesto no lo ve el javac.
        assertNotNull(contratos.idsPorTextoPorId(org, true, true, rolesArray,
                "zzz", null, null, 20, 0));
        assertNotNull(contratos.idsPorTextoPorCierre(org, true, true, rolesArray,
                "zzz", null, null, 20, 0));
        assertEquals(0L, contratos.contarPorTexto(org, true, true, rolesArray, "zzz", null, null));
        // Las dos variantes de orden tienen que resolver el MISMO conjunto.
        assertEquals(contratos.idsPorTextoPorId(org, true, true, rolesArray, "a", null, null, 50, 0)
                        .stream().sorted().toList(),
                contratos.idsPorTextoPorCierre(org, true, true, rolesArray, "a", null, null, 50, 0)
                        .stream().sorted().toList());
        assertNotNull(contratos.comisionesGeneradasPorTexto(org, true, true, rolesArray,
                "zzz", null, null));
        assertNotNull(contratos.repartosPorMonedaPorTexto(org, true, true, rolesArray,
                "zzz", null, null));
        assertNotNull(contratos.movimientosPorMonedaPorTexto(org, true, true, rolesArray,
                "zzz", null, null));
        assertNotNull(contratos.distritosDeCierresPorTexto(org, true, true, rolesArray,
                "zzz", null, null));
        assertNotNull(contratos.agentesDeCierresPorTexto(org, true, true, rolesArray,
                "zzz", null, null));
        // resumenCierresPorTexto proyecta alias en camelCase entrecomillados;
        // si PostgreSQL los bajara a minusculas, esto devolveria nulls.
        var resumenTexto = contratos.resumenCierresPorTexto(org, true, true, rolesArray,
                "zzz", null, null);
        assertNotNull(resumenTexto);
        assertNotNull(resumenTexto.getCierres());
        assertNotNull(resumenTexto.getPorLiquidar());
        assertNotNull(resumenTexto.getSinLiquidacion());
        assertNotNull(contratos.conComisionListaParaCobro(org, -1L));
        assertFalse(contratos.existsByOrganizacionIdAndContratoAnteriorId(org, -1L));

        assertNotNull(comisiones.porContratos(org, List.of(-1L)));
        assertNotNull(movimientos.findByOrganizacionIdAndLiquidacionIdIn(org, List.of(-1L)));
        assertNotNull(alertas.buscarConAgente(org, true, sinRoles, pagina));
        assertFalse(alertas.existeActivaDe(org, "PRUEBA", -1L, "PRUEBA"));
        assertNotNull(tareas.abiertasDeEntidad(org, "PRUEBA", -1L));
        assertNotNull(publicaciones.estadosPublicacion(List.of(-1L)));
        assertNotNull(requerimientos.listarActivos(org));
    }

    private static List<Class<?>> repositoriosConEstado() {
        return List.of(PropiedadRepository.class, CaptacionRepository.class,
                ProspeccionRepository.class, OportunidadComercialRepository.class,
                VisitaRepository.class, SolicitudAlquilerRepository.class,
                ContratoAlquilerRepository.class, ComisionLiquidacionRepository.class,
                ComisionMovimientoRepository.class, AlertaRepository.class,
                TareaRepository.class, PublicacionRepository.class,
                RequerimientoClienteRepository.class);
    }

    private static List<RestriccionEstado> restriccionesEstado() {
        return List.of(
                new RestriccionEstado("ck_persona_estado", EstadosDominio.EstadoActivoInactivo.class),
                new RestriccionEstado("ck_credencial_estado", EstadosDominio.EstadoActivoInactivo.class),
                new RestriccionEstado("ck_detalle_agente_estado", EstadosDominio.EstadoOperativoAgente.class),
                new RestriccionEstado("ck_organizacion_estado", EstadosDominio.EstadoActivoInactivo.class),
                new RestriccionEstado("ck_usuario_org_estado", EstadosDominio.EstadoActivoInactivo.class),
                new RestriccionEstado("ck_finalidad_estado", EstadosDominio.EstadoActivoInactivo.class),
                new RestriccionEstado("ck_propiedad_estado_registro", EstadosDominio.EstadoRegistroPropiedad.class),
                new RestriccionEstado("ck_propiedad_disponibilidad", EstadosDominio.DisponibilidadComercial.class),
                new RestriccionEstado("ck_publicacion_estado", EstadosDominio.EstadoPublicacion.class),
                new RestriccionEstado("ck_prospeccion_estado", EstadosDominio.EstadoProspeccion.class),
                new RestriccionEstado("ck_captacion_estado", EstadosDominio.EstadoCaptacion.class),
                new RestriccionEstado("ck_oportunidad_estado", EstadosDominio.EstadoOportunidad.class),
                new RestriccionEstado("ck_requerimiento_estado", EstadosDominio.EstadoRequerimiento.class),
                new RestriccionEstado("ck_visita_estado", EstadosDominio.EstadoVisita.class),
                new RestriccionEstado("ck_solicitud_estado", EstadosDominio.EstadoSolicitud.class),
                new RestriccionEstado("ck_documento_estado", EstadosDominio.EstadoDocumentoSolicitud.class),
                new RestriccionEstado("ck_contrato_estado", EstadosDominio.EstadoContrato.class),
                new RestriccionEstado("ck_comision_estado", EstadosDominio.EstadoComision.class),
                new RestriccionEstado("ck_tarea_estado", EstadosDominio.EstadoTarea.class),
                new RestriccionEstado("ck_alerta_estado", EstadosDominio.EstadoAlerta.class),
                new RestriccionEstado("ck_regularizacion_estado", EstadosDominio.EstadoRegularizacionEconomica.class));
    }

    private record RestriccionEstado(String nombre, Class<?> tipoEnum) {
    }

    private static <A extends Annotation> A anotacion(Parameter parametro, Class<A> tipo) {
        return parametro.getAnnotation(tipo);
    }
}
