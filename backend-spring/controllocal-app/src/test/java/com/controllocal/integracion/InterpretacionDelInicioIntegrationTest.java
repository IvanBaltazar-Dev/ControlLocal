package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.TareaService.FichaTarea;
import com.controllocal.service.TareaService;
import com.controllocal.service.soporte.EstadoDelHecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Hecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Renglon;
import com.controllocal.service.soporte.InterpretacionDelAsunto;
import com.controllocal.service.soporte.InterpreteDeLaBandeja;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>La capa de interpretación, contra datos reales</b> (D-E2-1 §10, E2.4).
 *
 * <p>Los tests unitarios blindan las reglas de redacción sobre frases de
 * laboratorio. Esto comprueba lo otro: que <b>lo que de verdad sale de la base</b>
 * las cumple. Una regla que solo se verifica sobre un fixture escrito a mano no
 * ha visto nunca un expediente real.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InterpretacionDelInicioIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TareaService tareas;
    @Autowired InterpreteDeLaBandeja interprete;
    @Autowired CaptacionRepository captaciones;

    /**
     * Las palabras que D-E2-1 §10.3.2 rechaza.
     *
     * <p>Todo contraste sale de la base de la organización — el rango real de la
     * zona, tu media de propuestas por visita, el plazo de recontacto de tu casa.
     * <b>Ninguna estadística del sector.</b> Es lo que hace que el dato pese: es
     * comprobable.
     */
    private static final List<String> PROHIBIDAS =
            List.of("sector", "mercado nacional", "industria", "benchmark", "promedio del mercado");

    // ==================================================================

    @Test
    @DisplayName("cada asunto llega interpretado: como esta, expediente y lectura")
    void cadaAsuntoLlegaInterpretado() {
        List<FichaTarea> bandeja = tareas.bandejaDe(actorAgente());
        assertFalse(bandeja.isEmpty(), "el escenario exige una bandeja con asuntos");

        for (FichaTarea asunto : bandeja) {
            assertNotNull(asunto.interpretacion(),
                    "un asunto sin interpretar obliga a la pantalla a deducirla: " + asunto.tipo());
            assertNotNull(asunto.interpretacion().comoEsta());
            assertFalse(asunto.interpretacion().comoEsta().hechos().isEmpty(),
                    "«como esta» sin un solo hecho no dice como esta: " + asunto.tipo());
            assertTrue(asunto.interpretacion().comoEsta().hechos().size()
                            <= InterpretacionDelAsunto.MAXIMO_HECHOS,
                    "tres vinetas, sin parrafos");
        }
    }

    @Test
    @DisplayName("un hecho resuelto sale verde aunque el asunto este en rojo")
    void elEstadoEsDelHechoYNoDelAsunto() {
        List<FichaTarea> bandeja = tareas.bandejaDe(actorAgente());

        // El asunto que espera al broker es de prioridad ALTA y aun asi su primer
        // hecho es un HECHO: la parte del agente esta cumplida. Si el estado se
        // dedujera del tono, saldria en rojo (D-E2-1 seccion 10.1).
        FichaTarea esperandoAOtro = bandeja.stream()
                .filter(t -> !t.dependeDeMi())
                .findFirst()
                .orElse(null);
        if (esperandoAOtro == null) {
            return; // sin ese escenario no hay nada que comprobar
        }
        List<Hecho> hechos = esperandoAOtro.interpretacion().comoEsta().hechos();
        assertTrue(hechos.get(0).estado() == EstadoDelHecho.HECHO,
                "lo que ya esta va primero, y va en verde: " + hechos);
    }

    @Test
    @DisplayName("el orden de los hechos es narrativo: lo que esta, lo que falta, lo que frena")
    void elOrdenEsNarrativo() {
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            List<Hecho> hechos = asunto.interpretacion().comoEsta().hechos();
            int posicionDelFreno = -1;
            int posicionDeLoQueFalta = -1;
            for (int i = 0; i < hechos.size(); i++) {
                if (hechos.get(i).estado() == EstadoDelHecho.FRENO) posicionDelFreno = i;
                if (hechos.get(i).estado() == EstadoDelHecho.FALTA) posicionDeLoQueFalta = i;
            }
            if (posicionDelFreno >= 0 && posicionDeLoQueFalta >= 0) {
                assertTrue(posicionDeLoQueFalta < posicionDelFreno,
                        "la consecuencia va DESPUES de su causa; al reves se lee como una "
                                + "alarma con contexto detras, y nadie lee el contexto: " + hechos);
            }
        }
    }

    @Test
    @DisplayName("ningun codigo tecnico llega al texto visible")
    void ningunCodigoTecnicoEnLoVisible() {
        List<String> colados = new java.util.ArrayList<>();
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            var interpretacion = asunto.interpretacion();
            for (Hecho hecho : interpretacion.comoEsta().hechos()) {
                if (InterpretacionDelAsunto.llevaCodigoTecnico(hecho.texto())) {
                    colados.add("hecho: " + hecho.texto());
                }
            }
            for (Renglon renglon : interpretacion.expediente()) {
                if (InterpretacionDelAsunto.llevaCodigoTecnico(renglon.valor())) {
                    colados.add("expediente/" + renglon.rotulo() + ": " + renglon.valor());
                }
            }
            if (InterpretacionDelAsunto.llevaCodigoTecnico(interpretacion.lectura())) {
                colados.add("lectura: " + interpretacion.lectura());
            }
        }
        if (!colados.isEmpty()) {
            fail("""
                    Hay codigos tecnicos en el texto que se lee:

                    %s

                    Quien opera identifica la operacion por la DIRECCION y la PERSONA, no
                    por un consecutivo, y el codigo ocupa el sitio de algo que si se usa.
                    Siguen vivos donde hacen falta -- busqueda, soporte, la ficha real --
                    pero no en el Inicio (D-E2-1 seccion 10.3.3).
                    """.formatted(String.join("\n", colados)));
        }
    }

    @Test
    @DisplayName("la lectura sintetiza y no recita ninguno de los cuatro renglones")
    void laLecturaNoRecita() {
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            var interpretacion = asunto.interpretacion();
            if (interpretacion.lectura() == null) {
                continue; // sin nada que concluir, no se rellena
            }
            assertFalse(
                    InterpretacionDelAsunto.recita(interpretacion.lectura(),
                            interpretacion.expediente()),
                    "«" + interpretacion.lectura() + "» repite un renglon: si lo recita no "
                            + "aporta nada, el usuario lo tiene dos centimetros mas abajo");
        }
    }

    @Test
    @DisplayName("ninguna comparacion invoca al sector: todo sale de la casa")
    void ningunaEstadisticaDelSector() {
        List<String> colados = new java.util.ArrayList<>();
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            var interpretacion = asunto.interpretacion();
            List<String> textos = new java.util.ArrayList<>();
            interpretacion.comoEsta().hechos().forEach(h -> textos.add(h.texto()));
            interpretacion.expediente().forEach(r -> textos.add(r.valor()));
            textos.add(interpretacion.lectura());

            for (String texto : textos) {
                if (texto == null) continue;
                String enMinusculas = texto.toLowerCase(Locale.ROOT);
                PROHIBIDAS.stream().filter(enMinusculas::contains)
                        .forEach(palabra -> colados.add("\"" + palabra + "\" en: " + texto));
            }
        }
        assertTrue(colados.isEmpty(),
                "Todo contraste sale de la base de la organizacion, nunca del sector: es lo que "
                        + "lo hace comprobable y lo que hace que el dato pese. " + colados);
    }

    /**
     * <b>Todo asunto resoluble lleva CUATRO renglones.</b>
     *
     * <p>Esta prueba decía «cuatro o ninguno», y ese «o ninguno» tapaba un
     * hueco real: el barrido de cierre de E2 encontró que un asunto de tipo
     * {@code PROSPECCION} llegaba con <b>cero</b>, porque los cuatro renglones se
     * construían siempre desde el inmueble y una prospección todavía no tiene
     * captación.
     *
     * <p>La corrección no fue permitir el hueco en el documento, sino corregir
     * qué significa «cuatro»: cuatro <b>evidencias pertinentes al asunto</b>, no
     * cuatro entidades inmobiliarias obligatorias. Una prospección lleva
     * Prospección · Contacto · Avance · Propietario; un asunto con encargo lleva
     * Encargo · Renta · Actividad · Propietario.
     */
    @Test
    @DisplayName("todo asunto lleva cuatro renglones; nunca cero y nunca cuatro guiones")
    void todoAsuntoLlevaCuatroRenglones() {
        List<String> sinExpediente = new ArrayList<>();
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            List<Renglon> expediente = asunto.interpretacion().expediente();
            if (expediente.size() != InterpretacionDelAsunto.RENGLONES_DEL_EXPEDIENTE) {
                sinExpediente.add(asunto.entidadTipo() + "#" + asunto.entidadId()
                        + " -> " + expediente.size() + " renglones");
                continue;
            }
            for (Renglon renglon : expediente) {
                assertNotNull(renglon.valor());
                assertFalse(renglon.valor().isBlank(),
                        "un renglon vacio es peor que no estar: " + renglon.rotulo());
            }
        }
        assertEquals(List.of(), sinExpediente,
                "Un asunto sin expediente deja el Radar en blanco justo al abrirlo. Los cuatro "
                        + "renglones se eligen segun la etapa -nunca se inventa un inmueble ni un "
                        + "encargo que no existe-, pero son cuatro.");
    }

    /**
     * Los cuatro de una prospección hablan de la prospección.
     *
     * <p>Es el caso que la prueba anterior daba por bueno con cero. Aquí se exige
     * el contenido, no solo el número: si alguien resolviera una prospección por
     * el camino del inmueble, saldrían «Encargo» y «Renta» de una captación que
     * no existe.
     */
    @Test
    @DisplayName("una prospeccion lleva sus propios cuatro renglones, no los de un encargo")
    void laProspeccionLlevaSuPropioExpediente() {
        List<Renglon> expediente = expedienteDelPrimero("PROSPECCION");
        assertEquals(4, expediente.size(),
                "Una prospeccion tambien tiene historia: cuando aparecio, cuando se le hablo, "
                        + "hasta donde llego y con quien se esta tratando.");

        assertEquals(List.of("Prospección", "Contacto", "Avance", "Propietario"),
                expediente.stream().map(Renglon::rotulo).toList());

        String texto = expediente.stream().map(Renglon::valor).collect(Collectors.joining(" · "));
        assertFalse(texto.contains("Encargo") || texto.contains("vence en"),
                "Antes de la captacion no hay encargo del que hablar: " + texto);
    }

    /** Y los de un asunto con encargo siguen siendo los del inmueble. */
    @Test
    @DisplayName("un asunto con encargo conserva los cuatro renglones del inmueble")
    void elAsuntoConEncargoConservaLosSuyos() {
        List<Renglon> expediente = expedienteDelPrimero("VISITA");

        assertEquals(List.of("Encargo", "Renta", "Actividad", "Propietario"),
                expediente.stream().map(Renglon::rotulo).toList());
    }

    /**
     * <b>Una prospección no se resuelve por inmueble.</b>
     *
     * <p>No basta con que el resultado salga bien: el camino importa, porque
     * resolverla por {@code id_propiedad} funcionaría hoy —la columna es NOT
     * NULL— y produciría los renglones de un encargo inexistente. La consulta que
     * mapea asunto → propiedad no puede nombrar a la prospección.
     */
    @Test
    @DisplayName("la prospeccion no entra en el mapa de asunto a inmueble")
    void laProspeccionNoSeResuelvePorInmueble() {
        List<String> tipos = captaciones.propiedadPorAsunto(organizacionDelAgente()).stream()
                .map(fila -> (String) fila[0])
                .distinct()
                .toList();

        assertFalse(tipos.contains("PROSPECCION"),
                "Resolver una prospeccion como inmueble le pondria Encargo y Renta de una "
                        + "captacion que todavia no existe. Tipos mapeados: " + tipos);
    }

    /**
     * <b>Una fecha ausente se dice; no se rellena.</b>
     *
     * <p>42 de las 63 prospecciones de la base no tenían contacto el 2026-08-19.
     * Eso no es un fallo de carga: es el estado normal de una prospección recién
     * abierta, y el renglón tiene que decirlo con palabras en vez de enseñar un
     * guión, una fecha inventada o el día de hoy.
     */
    @Test
    @DisplayName("una fecha que falta se declara ausente, nunca se rellena")
    void unaFechaAusenteSeDeclara() {
        long idSinContacto = jdbc.queryForObject("""
                select p.id_prospeccion from prospeccion p
                 where p.fecha_contacto is null and p.organizacion_id = ?
                 limit 1
                """, Long.class, organizacionDelAgente());

        List<Renglon> expediente = interprete.de(
                new InterpreteDeLaBandeja.AsuntoADescribir("RECONTACTO", "PROSPECCION",
                        idSinContacto, "sin contacto", null, null, true),
                interprete.contextoDe(organizacionDelAgente(), List.of(
                        new InterpreteDeLaBandeja.AsuntoADescribir("RECONTACTO", "PROSPECCION",
                                idSinContacto, "sin contacto", null, null, true))),
                LocalDate.now()).expediente();

        Renglon contacto = expediente.stream()
                .filter(r -> "Contacto".equals(r.rotulo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta el renglon de contacto"));

        assertEquals("Sin contacto registrado", contacto.valor(),
                "La ausencia se nombra. Un guion obliga a preguntar si falta el dato o el hecho.");
    }

    // ------------------------------------------------------------------

    private List<Renglon> expedienteDelPrimero(String entidadTipo) {
        return tareas.bandejaDe(actorAgente()).stream()
                .filter(a -> entidadTipo.equals(a.entidadTipo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "La bandeja no trae ningun asunto de tipo " + entidadTipo
                                + ", asi que esta prueba no esta comprobando nada."))
                .interpretacion()
                .expediente();
    }

    private long organizacionDelAgente() {
        return actorAgente().idOrganizacion();
    }

    // ------------------------------------------------------------------

    private Actor actorAgente() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }
}
