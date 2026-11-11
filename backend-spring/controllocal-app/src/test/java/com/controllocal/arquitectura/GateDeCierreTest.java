package com.controllocal.arquitectura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Un reactor que omite en silencio los tests de PostgreSQL no es un gate de
 * cierre.</b>
 *
 * <p>Los tests de integración inventariados llevan
 * {@code @EnabledIfEnvironmentVariable(named = "TEST_DB_URL")}, asi que sin esa
 * variable JUnit los SALTA sin decir nada y Maven termina en verde. Eso ya
 * costo caro una vez: las migraciones V31, V37 y V38 introdujeron tres
 * columnas {@code estado} con la palabra completa —rompiendo el invariante de
 * codigo unitario que {@code RepositorioEstadosIntegrationTest} existe para
 * proteger— y el build siguio pasando durante todo el bloque de seguridad
 * porque ese gate nunca llego a ejecutarse. Lo arreglo V40.
 *
 * <p>Esta clase cierra el agujero por dentro: cuando la corrida se declara DE
 * CIERRE ({@code CONTROLLOCAL_CIERRE=1}, que pone {@code Verificar-Cierre.ps1}),
 * la ausencia de {@code TEST_DB_URL} deja de ser un salto silencioso y pasa a
 * ser un fallo. En una corrida normal de desarrollo no molesta.
 *
 * <p>Vive en el modulo {@code app} porque es donde estan los tests que vigila.
 */
class GateDeCierreTest {

    private static final String CIERRE = "CONTROLLOCAL_CIERRE";
    private static final String URL = "TEST_DB_URL";

    @Test
    void unaCorridaDeCierreNoPuedeOmitirLosTestsDePostgreSql() {
        if (!"1".equals(System.getenv(CIERRE))) {
            return;
        }
        String url = System.getenv(URL);
        assertTrue(url != null && !url.isBlank(),
                "Corrida de cierre sin " + URL + ": los tests de integracion se habrian saltado "
                        + "en silencio y el verde no significaria nada. Exporta "
                        + URL + " con la base de pruebas propia identificada por el corte "
                        + "o no declares la corrida como de cierre.");
        assertTrue(url.startsWith("jdbc:postgresql:"),
                URL + " tiene que apuntar a PostgreSQL real, no a otro motor: " + url);
    }

    /**
     * La guarda solo sirve si sigue cubriendo TODOS los tests que dependen de
     * la base. Si alguien anade otro test de integracion, esta prueba lo
     * obliga a declararlo aqui —y por tanto a pensar si la corrida de cierre
     * lo cubre— en vez de dejarlo saltando en silencio para siempre.
     */
    @Test
    void todoTestDeIntegracionDependeDeLaMismaVariableYEstaInventariado() throws IOException {
        Path raiz = Path.of("src", "test", "java", "com", "controllocal");
        assertTrue(Files.isDirectory(raiz), "No se encontro " + raiz.toAbsolutePath());

        List<String> conBaseDeDatos;
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            conBaseDeDatos = ficheros
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .filter(GateDeCierreTest::usaBaseDeDatos)
                    .map(f -> f.getFileName().toString().replace(".java", ""))
                    .sorted()
                    .toList();
        }

        assertEquals(List.of(
                        // B0/C6: inventario PG real; no implica IA calibrada ni financiación externa.
                        "B0AlmacenamientoIntegrationTest",
                        "B0CiclosPagadosIntegrationTest",
                        "B0NoProcesamientoIntegrationTest",
                        "B0PresupuestoIntegrationTest",
                        "TrabajoManualB0IntegrationTest",
                        "InvestigacionesMercadoIntegrationTest",
                        "ConfirmacionesCostoIaB0IntegrationTest",
                        "AsuntosGrupoAIntegrationTest",
                        "AcreditacionArchivoR25IntegrationTest",
                        "ComprasManualesB0IntegrationTest",
                        "CustodiaEvaluacionIaCarrilTecnicoIntegrationTest",
                        "CustodiaEvaluacionIaPoolSaturadoIntegrationTest",
                        "EvaluacionIaB0IntegrationTest",
                        "EvaluacionMaterialIaIntegrationTest",
                        "FenceSalidaEvaluacionIntegrationTest",
                        "MetricasIaAccesoHttpIntegrationTest",
                        "MetricasIaB0IntegrationTest",
                        "ObservacionMetricasIaB0IntegrationTest",
                        "B0PrevisionAlmacenamientoIaIntegrationTest",
                        "AvisosTemporalesMaterialIaC6IntegrationTest",
                        "CuotaGaleriaC6IntegrationTest",
                        "DocumentosAlmacenamientoC6IntegrationTest",
                        "FuentesMaterialIaCoreIntegrationTest",
                        "IntentosBinariosC6IntegrationTest",
                        "MaterialIaC6IntegrationTest",
                        "MaterialIaDiscoC6IntegrationTest",
                        "LecturaContextualC6IntegrationTest",
                        "PiezasComercialesC6IntegrationTest",
                        "PiezasDerivadasConcurrenciaC6IntegrationTest",
                        "PlanesFinanciadosDePruebaIntegrationTest",
                        "PublicacionesC6IntegrationTest",
                        "RecuperacionMaterialIaEntreProcesosC6IntegrationTest",
                        "TratamientoMaterialIaE44IntegrationTest",
                        "VocabularioActuacionesC6IntegrationTest",
                        // C4: comunicación contextual y negociación/rollback/concurrencia reales.
                        "ActividadNegociacionIntegrationTest",
                        "InteraccionContextualIntegrationTest",
                        "LecturaInteraccionConcurrenteIntegrationTest",
                        "LecturaInteraccionIndexadaIntegrationTest",
                        "PerfilLecturaInteraccionC4IntegrationTest",
                        "ComisionC5IntegrationTest",
                        "DesenlacesC7IntegrationTest",
                        "OrigenConversionC7IntegrationTest",
                        "CompromisosC7IntegrationTest",
                        "ProgramasPersonalesC7IntegrationTest",
                        "ReportesVersionadosC7IntegrationTest",
            "MetasVersionadasC7IntegrationTest",
            "InformesFiltradosC7IntegrationTest",
                        "EncargosPrivadosC7IntegrationTest",
                        "InvitacionEncargoC7IntegrationTest",
                        "EncargosClonadosC7IntegrationTest",
                        "ReasignacionProspeccionC7IntegrationTest",
                        "InicioFuturoC7IntegrationTest",
                        "FormalizacionC5ConcurrenciaIntegrationTest",
                        "LecturasFinancierasC5IntegrationTest",
                        // C3/K2: casos PG reales; inventariar no sustituye su ejecución sin skips.
                        "ColaboracionC3IntegrationTest",
                        "ConservacionHistoriasC3IntegrationTest",
                        "DemandaC3HttpIntegrationTest",
                        "DemandaC3IntegrationTest",
                        "DemandaPrecisionTemporalC3IntegrationTest",
                        "DemandaConOportunidadC3IntegrationTest",
                        "DisponibilidadC3IntegrationTest",
                        "ReconfirmacionesD2IntegrationTest",
                        "AvisosBrokerC7IntegrationTest",
                        "RadarFuentesC7IntegrationTest",
                        "MatchingC3ContextoIntegrationTest",
                        "MatchingC3MetadataIntegrationTest",
                        "NetworkC3IntegrationTest",
                        "RecomendacionesInternasC3IntegrationTest",
                        "CuentaPersonalIntegrationTest",
                        "InvitacionesMembresiaIntegrationTest",
                        "ComprobacionArranqueConsultasIntegrationTest",
                        "AccesoArchivosIntegrationTest",
                        "ActuacionesIntegrationTest",
                        // P0 (V87) — quien puede ESCRIBIR la propiedad y quien cada
                        // encargo. Recorre las vias de escritura una por una con dos
                        // agentes del mismo equipo, y mira precio_propiedad despues:
                        // el permiso importa, y el hito economico falso importa mas.
                        "AlcanceYGobiernoDeLaAutoridadIntegrationTest",
                        "AutomaticosHttpIntegrationTest",
                        "AutoridadDeEdicionIntegrationTest",
                        // D-E4-3 — una autoridad persistente por clave publicada.
                        "AutoridadDelDatoIntegrationTest",
                        "BusquedaLocalesIntegrationTest",
                        // C0/K0: contexto, permisos, vacío autorizado y aislamiento concurrente.
                        "CapacidadesContextualesIntegrationTest",
                        // C2: captura durable, catálogo privado, metadatos y CAS físico/Encargo reales.
                        "CapturaC2ConcurrenciaIntegrationTest",
                        "CapturaC2ContratosIntegrationTest",
                        "CapturaMetadataEdicionIntegrationTest",
                        "CasFisicoDePropiedadIntegrationTest",
                        "EncargoC2IntegrationTest",
                        "GobiernoCatalogoCapturaIntegrationTest",
                        // RF014: origen persistido/tenant/CAS/T4 y puerta HTTP de revisión exacta.
                        "OrigenFisicoIntegrationTest",
                        "OrigenFisicoHttpIntegrationTest",
                        // Corte 0B - el catalogo aprende a hablar.
                        "CatalogoQueHablaIntegrationTest",
                        // D-P0-9 / D-P0-10 / D-P0-7 sobre la SEGUNDA autoridad:
                        // el agente de un encargo. La gemela de la de abajo,
                        // dicha sobre captacion.id_rol_agente -- comando
                        // obsoleto, dos transacciones vivas, fallo inyectado en
                        // el rastro, dirty checking de la edicion y destino sin
                        // elegibilidad.
                        "CausalidadDeLaReasignacionIntegrationTest",
                        // D-P0-9 / D-P0-10 - un traspaso parte del estado que
                        // alguien miro, y ocurre entero o no ocurre. Va en el
                        // cierre porque lo que prueba no se puede probar leyendo
                        // el codigo: monta DOS transacciones vivas a la vez sobre
                        // PostgreSQL y rompe cada escritura por separado para ver
                        // si alguna sobrevive sola.
                        "CausalidadDelTraspasoIntegrationTest",
                        "ContextoHttpIntegrationTest",
                        "ContextosSesionIntegrationTest",
                        // D0-3 - una clave retirada se lee, se distingue y no
                        // se edita: la mitad de la retirada que mira el usuario.
                        "ClaveRetiradaEnLaFichaIntegrationTest",
                        "ComandosAutomaticosIntegrationTest",
                        "ConciliacionDeRegistroIntegrationTest",
                        // Corte 0A - editar no destruye lo que el usuario no toco.
                        "ConservacionDeLaEdicionIntegrationTest",
                        "ConvergenciaCampanaColaIntegrationTest",
                        // E0.2 — el historico economico contra PostgreSQL real.
                        "FocoDelBrokerIntegrationTest",
                        "HistoricoPrecioIntegrationTest",
                        "IdentidadDelBrokerIntegrationTest",
                        "InicioSeguroIntegrationTest",
                        "InterpretacionDelInicioIntegrationTest",
                        "InvariantesComisionIntegrationTest",
                        // D-P0-6 (F1) — quien LEE la informacion comercial
                        // historica. La otra mitad de P0: aquel decidio quien
                        // escribe, este quien lee el pasado. Va en el cierre
                        // porque lo que prueba es que la ficha, el bloque de cada
                        // encargo y la serie de precios responden lo MISMO -- y
                        // porque encontro una fuga de tenant que llevaba abierta
                        // en `/locales/{id}/precios`.
                        "LecturaContextualPropiedadIntegrationTest",
                        "LecturaHistoricaIntegrationTest",
                        // WP-A (V90) — la negociacion y el cierre de una venta.
                        // Necesita PostgreSQL y no es opcional: los dos invariantes
                        // de dinero de la mesa -una oferta viva, una aceptada- viven
                        // en indices parciales, y los tres triggers que atan una
                        // oferta a su encargo, un contrato a un encargo de venta y un
                        // hito a su captacion no los ejecuta ningun mock.
                        "NegociacionYCierreDeVentaIntegrationTest",
                        // D-E4-1 — las tres piezas del nucleo universal contra
                        // PostgreSQL: titularidad, atributos gobernados y outbox.
                        "NucleoUniversalIntegrationTest",
                        "OcupacionInmuebleIntegrationTest",
                        // Corte 5 · 5A (V84) - quien ocupa el inmueble y que
                        // servicios llegan: el par hecho/condicion en los siete,
                        // las dos PUB del terreno y la retirada de la ultima
                        // LISTA muda del catalogo.
                        "OcupacionYServiciosIntegrationTest",
                        "PadronDeGobiernoIntegrationTest",
                        // 4.P - la procedencia del DATO, no la del acto: los
                        // ocho casos de reconstruccion de una historia real.
                        "ProcedenciaDelValorIntegrationTest",
                        // D-E4-1 / D-E4-2 — los 15 escenarios de aceptacion de la
                        // propiedad universal y la captura. Es el unico que COMETE
                        // de verdad, en tenants propios: cuatro de sus invariantes
                        // (cuotas diferidas, idempotencia, rollback y encargos
                        // simultaneos) no existen dentro de una transaccion que se
                        // deshace.
                        // La Propiedad como activo de dato.
                        "PropiedadComoActivoDeDatoIntegrationTest",
                        // Convergencia del 0C: registrar no es encargar.
                        "PropiedadSinEncargoIntegrationTest",
                        "PropiedadUniversalIntegrationTest",
                        "RepositorioEstadosIntegrationTest",
                        "SimulacroRecuperacionIntegrationTest",
                        // Corte 5 · 5B (V85) - el suelo y lo que la norma deja
                        // hacer con el: las 18 claves del terreno, la unica PUB
                        // que estrena el corte y la retirada de `area_terreno`
                        // en T, que era la segunda clave para una sola verdad.
                        "SueloYParametrosUrbanisticosIntegrationTest",
                        // Corte 0C - de quien es cada dato.
                        "SujetoDelDatoIntegrationTest",
                        "VocabularioPersistidoIntegrationTest").stream().sorted().toList(),
                conBaseDeDatos,
                "Cambio el inventario de tests de integracion. Actualiza esta lista y comprueba "
                        + "que Verificar-Cierre.ps1 sigue exigiendo que TODOS se ejecuten.");

        try (Stream<Path> ficheros = Files.walk(raiz)) {
            List<String> conOtraVariable = ficheros
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .filter(GateDeCierreTest::usaBaseDeDatos)
                    .filter(f -> !leer(f).replaceAll("\\s", "").contains("named=\"" + URL + "\""))
                    .map(f -> f.getFileName().toString())
                    .toList();
            assertEquals(List.of(), conOtraVariable,
                    "Un test de integracion se activa con otra variable: la corrida de cierre "
                            + "no lo cubriria.");
        }
    }

    /**
     * <b>Los dos inventarios no pueden separarse.</b>
     *
     * <p>Se separaron, y el barrido de cierre de E2 lo encontró el 2026-08-19:
     * este test inventariaba <b>catorce</b> pruebas de integración y
     * {@code Verificar-Cierre.ps1} comprobaba que se hubieran ejecutado
     * <b>trece</b>. La que faltaba era
     * {@code AutoridadDelDatoIntegrationTest} — precisamente la que el 18 de
     * agosto escribió 162 propiedades en la base de desarrollo. La corrida de
     * cierre nunca probó que se hubiera ejecutado.
     *
     * <p>Es el mismo defecto que el gate entero existe para evitar, un nivel más
     * arriba: un verde que no significa lo que parece. Y no se arregla añadiendo
     * la línea que faltaba —dos listas mantenidas a mano vuelven a divergir—,
     * sino comprobando que la del script contenga todas las de la carpeta.
     */
    @Test
    void elScriptDeCierreComprobaraTodasLasPruebasDeIntegracion() throws IOException {
        Path raiz = Path.of("src", "test", "java", "com", "controllocal");
        List<String> enLaCarpeta;
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            enLaCarpeta = ficheros
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .filter(GateDeCierreTest::usaBaseDeDatos)
                    .map(f -> f.getFileName().toString().replace(".java", ""))
                    .sorted()
                    .toList();
        }

        String script = leer(Path.of("..", "verificacion", "Verificar-Cierre.ps1"));
        List<String> sinVigilar = enLaCarpeta.stream()
                .filter(nombre -> !script.contains("'" + nombre + "'"))
                .toList();

        assertEquals(List.of(), sinVigilar,
                "Verificar-Cierre.ps1 no comprueba que estas pruebas se hayan ejecutado, asi "
                        + "que una corrida de cierre podria darlas por buenas sin haberlas "
                        + "corrido. Anadelas a su lista $integracion.");
    }

    /** El script de cierre tiene que seguir existiendo y exigiendo la variable. */
    @Test
    void elScriptDeCierreExigeLaBaseDeIntegracion() {
        Path script = Path.of("..", "verificacion", "Verificar-Cierre.ps1");
        assertTrue(Files.isRegularFile(script),
                "Falta verificacion/Verificar-Cierre.ps1, que es el comando de cierre.");
        String texto = leer(script);
        assertTrue(texto.contains(URL), "El script de cierre no menciona " + URL);
        assertTrue(texto.contains(CIERRE),
                "El script de cierre no exporta " + CIERRE + ", asi que la guarda no se activa.");
        assertFalse(texto.contains("-DskipTests"),
                "Una corrida de cierre no puede saltarse los tests.");
    }

    private static boolean usaBaseDeDatos(Path fichero) {
        return java.util.regex.Pattern.compile("(?m)^\\s*@EnabledIfEnvironmentVariable\\s*\\(")
                .matcher(leer(fichero)).find();
    }

    private static boolean contiene(Path fichero, String fragmento) {
        return leer(fichero).contains(fragmento);
    }

    private static String leer(Path fichero) {
        try {
            return Files.readString(fichero);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + fichero, e);
        }
    }
}

