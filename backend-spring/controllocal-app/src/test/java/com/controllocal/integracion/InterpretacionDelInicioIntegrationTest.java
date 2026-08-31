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
        unAsuntoQueEsperaAOtro();
        List<FichaTarea> bandeja = tareas.bandejaDe(actorAgente());

        // El asunto que espera al broker es de prioridad ALTA y aun asi su primer
        // hecho es un HECHO: la parte del agente esta cumplida. Si el estado se
        // dedujera del tono, saldria en rojo (D-E2-1 seccion 10.1).
        FichaTarea esperandoAOtro = bandeja.stream()
                .filter(t -> !t.dependeDeMi())
                .findFirst()
                .orElse(null);
        assertNotNull(esperandoAOtro,
                "no hay ningun asunto que espere a otro, asi que esta prueba no habria medido "
                        + "nada. Antes esto era un `return` y pasaba en verde SIN ejecutar una "
                        + "sola asercion; ahora el escenario lo construye "
                        + "`unAsuntoQueEsperaAOtro`, asi que si falta es que la construccion "
                        + "dejo de funcionar");
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
        long idSinContacto = unaProspeccionSinContacto();

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

    /**
     * <b>Una prospección sin fecha de contacto — construida, no heredada.</b>
     *
     * <p>Antes esto era un {@code select … limit 1} y bastaba: en la base de
     * pruebas compartida había cientos, acumuladas por corridas anteriores.
     * Contra una base <b>recién migrada</b> no hay ninguna que sirva, y no por
     * casualidad: <b>la semilla crea dos prospecciones y las dos nacen
     * contactadas</b> —{@code V5} inserta {@code PRO-0001} con
     * {@code fecha_contacto} 2026-06-20 y {@code PRO-0002} con 2026-07-01—, y la
     * única clase que crea una antes que ésta en el orden alfabético de surefire
     * ({@code AutoridadDeEdicion…}) llama a {@code contactar()} acto seguido,
     * así que también la deja <b>con</b> fecha. Las que quedan sin contacto las
     * escriben clases que corren <b>después</b> (las de {@code Propiedad…}).
     *
     * <p>La primera versión de este comentario decía «la semilla no crea
     * prospecciones». Era <b>falso</b> —y salió de un barrido en minúsculas
     * sobre unas migraciones escritas en mayúsculas, que devolvió cero sin
     * control positivo—. La conclusión operativa aguanta, porque lo que hace
     * falta no es una prospección cualquiera sino una <b>sin contactar</b>, y de
     * ésas la semilla no deja ninguna; pero la premisa escrita es la que usará
     * quien lea, así que se corrige.
     *
     * <p>Medido el 2026-08-30 sobre una instancia dedicada construida por las
     * migraciones reales: en el momento en que corre esta clase había <b>0</b>
     * prospecciones sin contacto, y el {@code queryForObject} moría con
     * {@code EmptyResultDataAccessException} — un rojo que no hablaba de lo que
     * la prueba mide. En la base compartida había 825, todas residuo.
     *
     * <p>Así que se construye. El hecho que la prueba necesita es «una
     * prospección abierta que todavía no se ha contactado», y eso se puede
     * escribir; heredarlo hacía que la prueba sólo midiera cuando alguien
     * hubiera pasado antes.
     */
    private long unaProspeccionSinContacto() {
        List<Long> existentes = jdbc.queryForList("""
                select p.id_prospeccion from prospeccion p
                 where p.fecha_contacto is null and p.organizacion_id = ?
                 order by p.id_prospeccion limit 1
                """, Long.class, organizacionDelAgente());
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        Actor agente = actorAgente();
        // Estado 'P' = abierta y sin contactar, que es exactamente el caso que
        // el renglón tiene que saber decir. Las tres fechas quedan NULL a
        // propósito: rellenarlas sería inventar el hecho que se está midiendo.
        //
        // `on conflict` sobre la MISMA clave con la que se inserta. La primera
        // versión buscaba por `fecha_contacto is null` e insertaba por
        // `codigo_prospeccion`, que es único por (organización, código): el día
        // que algo contactara esta prospección, la búsqueda dejaría de verla, la
        // inserción chocaría contra la unicidad y la clase moriría en @Test
        // tras @Test sobre esa base. Hoy no ocurre, y por eso no se deja para
        // luego: una idempotencia que sólo funciona mientras nadie toque la fila
        // no es idempotencia, es una coincidencia con fecha de caducidad.
        //
        // Llegar aquí significa que la consulta de arriba no encontró NINGUNA
        // sin contactar en esta organización — incluida ésta. Así que si la fila
        // existe es porque fue contactada, y devolverla a su estado es correcto:
        // es la fila de este montaje, identificada por su propio código, y no
        // hay ningún otro dueño a quien pisarle nada.
        return jdbc.queryForObject("""
                insert into prospeccion (codigo_prospeccion, estado, id_propiedad, id_rol_agente,
                                         organizacion_id)
                values (?, 'P', ?, ?, ?)
                on conflict (organizacion_id, codigo_prospeccion)
                do update set estado = 'P', fecha_contacto = null
                returning id_prospeccion
                """, Long.class, CODIGO_PROSPECCION_DEL_MONTAJE,
                unaPropiedadDe(agente.idOrganizacion()),
                agente.idRolOperativo(), agente.idOrganizacion());
    }

    /** El código de la prospección que este montaje crea, busca y reutiliza. */
    private static final String CODIGO_PROSPECCION_DEL_MONTAJE = "PR-SIN-CONTACTO";

    /**
     * Una propiedad de esa organización, creada si todavía no hay ninguna.
     *
     * <p>Nace <b>FALTANTE</b>: se inserta por SQL y no por el caso de uso, así
     * que no hay actor del alta a quien atribuirla, y un responsable inventado
     * sería justo la procedencia falsa que el P0 vino a quitar. La prospección
     * sólo necesita colgar de un inmueble; quién responda por él no entra en lo
     * que esta prueba mide.
     */
    private long unaPropiedadDe(long idOrganizacion) {
        List<Long> ids = jdbc.queryForList("""
                select id_propiedad from propiedad where organizacion_id = ?
                 order by id_propiedad limit 1
                """, Long.class, idOrganizacion);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject("""
                insert into propiedad (codigo, direccion, distrito, metraje, tipo_inmueble, uso,
                                       organizacion_id, estado_registro, origen_incorporacion)
                values (?, 'Av. Interpretacion 1', 'Miraflores', 70.00, 'D', 'C', ?, 'A', 'SEMILLA')
                returning id_propiedad
                """, Long.class, "INTERP-" + idOrganizacion, idOrganizacion);
    }

    /**
     * La entidad del asunto que espera a otro. Identificador <b>deliberadamente
     * fuera de rango</b>: la tarea no se engancha a ningun contrato real, para
     * que la prueba no dependa de que exista uno ni toque el estado de los que
     * existan. {@code tarea.entidad_id} no tiene FK —la tiene
     * {@code id_contrato_origen}, que se deja NULL—, asi que esto es legal y
     * explicito.
     */
    private static final long ENTIDAD_DEL_ASUNTO_QUE_ESPERA = 9_000_000_001L;

    /**
     * <b>Un asunto de la bandeja cuya siguiente accion NO es del agente.</b>
     *
     * <p>De los siete disparadores hay exactamente <b>uno</b> que no depende del
     * agente, y {@code NaturalezaDelAsunto} lo dice sin ambiguedad:
     * {@code SEGUIMIENTO} sobre {@code CONTRATO_ALQUILER} — la comision lista
     * para cobro, que registra el BROKER. Asi que el escenario no se busca: se
     * escribe, y se escribe exactamente ese.
     *
     * <p><b>Por que hizo falta.</b> Antes la prueba hacia
     * {@code findFirst().orElse(null)} y, si no habia ninguno, {@code return}:
     * pasaba en verde <b>sin ejecutar una sola asercion</b>. Medido el
     * 2026-08-31 sobre la instancia dedicada del cierre, al terminar una corrida
     * completa: <b>0</b> tareas {@code SEGUIMIENTO} sobre
     * {@code CONTRATO_ALQUILER}. La asercion no se ejecutaba <b>nunca</b>.
     *
     * <p>La fila se escribe por SQL y no por el caso de uso porque lo que se
     * mide es el <b>interprete</b>: dado un asunto que espera a otro, su primer
     * hecho tiene que salir en verde. {@code TareaServiceImpl.ficha} ya
     * contempla la tarea abierta sin disparador vivo, asi que no hace falta
     * montar la cascada comercial entera para medir una regla de presentacion.
     *
     * <h2>Por que nace EN_PROCESO y no PENDIENTE</h2>
     * Porque {@code bandejaDe} <b>reconcilia antes de leer</b>: una tarea
     * {@code PENDIENTE} cuya entidad esta en {@code ENTIDADES_AUTO} —y
     * {@code CONTRATO_ALQUILER} lo esta— y que no tiene disparador vigente,
     * <b>se completa sola</b> en esa misma llamada, antes de componer la lista.
     * La primera version de este montaje insertaba en {@code PENDIENTE} y la
     * fila aparecia en la base con {@code estado = 'C'}: el escenario se
     * autodestruia y la asercion seguia sin ejecutarse.
     *
     * <p>{@code EN_PROCESO} es un estado <b>abierto</b> del vocabulario
     * ({@code Tarea.ABIERTAS} = PENDIENTE, EN_PROCESO) y el reconcile no lo
     * toca, precisamente porque significa que alguien ya esta en ello. No es un
     * truco para esquivar la reconciliacion: es el estado que corresponde a un
     * asunto que ya no espera trabajo del agente.
     *
     * <p>Idempotente por la <b>misma</b> clave con la que escribe, y sin filtrar
     * por estado: si una corrida anterior dejo la fila cerrada, esta la vuelve a
     * abrir en vez de insertar otra. Buscar por un criterio y escribir por otro
     * es exactamente el defecto que se corrigio en
     * {@code unaProspeccionSinContacto}.
     */
    private void unAsuntoQueEsperaAOtro() {
        Actor agente = actorAgente();
        // La MISMA clave para buscar y para escribir -- sin filtrar por estado.
        // Filtrando por `estado in ('P','E')` la fila cerrada dejaba de verse y
        // cada corrida insertaba otra: la busqueda y la escritura tienen que
        // mirar lo mismo o la idempotencia es aparente.
        int actualizadas = jdbc.update("""
                update tarea set estado = 'E', fecha_completada = null,
                                 fecha_actualizacion = now()
                 where organizacion_id = ? and id_rol_agente = ?
                   and entidad_tipo = 'CONTRATO_ALQUILER' and entidad_id = ?
                """, agente.idOrganizacion(), agente.idRolOperativo(),
                ENTIDAD_DEL_ASUNTO_QUE_ESPERA);
        if (actualizadas > 0) {
            return;
        }
        jdbc.update("""
                insert into tarea (organizacion_id, tipo, entidad_tipo, entidad_id, id_rol_agente,
                                   descripcion, estado, prioridad)
                values (?, 'SEGUIMIENTO', 'CONTRATO_ALQUILER', ?, ?,
                        'Comision lista para cobro: la registra el broker', 'E', 'ALTA')
                """, agente.idOrganizacion(), ENTIDAD_DEL_ASUNTO_QUE_ESPERA,
                agente.idRolOperativo());
    }

    private Actor actorAgente() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }
}
