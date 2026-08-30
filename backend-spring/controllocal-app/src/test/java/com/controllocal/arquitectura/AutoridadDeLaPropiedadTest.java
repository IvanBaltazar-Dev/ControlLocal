package com.controllocal.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El sexto gate: una escritura nueva sobre la propiedad no puede nacer sin
 * autoridad</b> (P0).
 *
 * <h2>Que decision protege</h2>
 * Hasta V87 la propiedad no tenia dueno de escritura. {@code PUT
 * /propiedades/{id}} cargaba la fila por {@code (organizacion, id)} y escribia:
 * cualquier AGENTE del tenant editaba la ficha de cualquier inmueble. Y no era
 * una via, eran <b>ocho</b>, repartidas por cuatro servicios — de las cuales
 * siete no comprobaban absolutamente nada mas que el tenant.
 *
 * <p>Arreglar las ocho a mano las deja arregladas <b>hoy</b>. Lo que este gate
 * protege es el <b>manana</b>: la novena. Un caso de uso nuevo que guarde una
 * propiedad, una foto o un hito economico sin pasar por
 * {@code AutoridadDePropiedad} pone el build en rojo, en vez de reabrir el
 * agujero en silencio dos cortes despues, cuando ya nadie recuerde por que la
 * columna existe.
 *
 * <h2>Por que un gate y no un {@code @PreAuthorize}</h2>
 * Dos razones medidas, y las dos aparecieron en el inventario de este P0:
 * <ol>
 *   <li>Las ocho vias viven en cuatro servicios. Una anotacion protege
 *       <b>una puerta</b>; la autoridad tiene que proteger <b>el hecho</b>.</li>
 *   <li><b>KAIROS entra por los mismos endpoints</b> con la cabecera
 *       {@code X-Origen} y el mismo token — no tiene escritor propio. Una regla
 *       en la capa web tendria que reescribirse para cada canal; una regla en
 *       el servicio la heredan todos por construccion. Eso es lo que hace
 *       verdadera la afirmacion "Web y KAIROS reciben exactamente la misma
 *       regla": no es que se hayan comprobado las dos, es que <b>es la
 *       misma</b>.</li>
 * </ol>
 *
 * <h2>Lo que este gate NO dice, dicho en vez de disimulado</h2>
 * <ul>
 *   <li><b>No ve el SQL directo.</b> Un {@code UPDATE propiedad} a mano sigue
 *       siendo posible. Lo que garantiza es que ninguna <b>operacion del
 *       producto</b> escribe la propiedad sin decidir quien puede.</li>
 *   <li><b>No ve un metodo privado nuevo</b> dentro de un caso de uso cuyo
 *       metodo publico si comprueba: la comprobacion es transitiva dentro de la
 *       clase, igual que en {@code LinajeDeTodaEscrituraTest}.</li>
 *   <li><b>No decide si la regla es correcta.</b> Decide que la regla se
 *       consulta. Que diga lo que tiene que decir lo prueban las pruebas de
 *       integracion.</li>
 * </ul>
 */
class AutoridadDeLaPropiedadTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    private static final String AUTORIDAD = "com.controllocal.service.soporte.AutoridadDePropiedad";
    private static final String PROPIEDAD = "com.controllocal.domain.inmueble.Propiedad";
    private static final String TRANSICIONES = "com.controllocal.service.soporte.Transiciones";

    private static final String REPOSITORIO_DEL_RASTRO =
            "com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository";

    /**
     * Las tablas que <b>son</b> la propiedad: ella misma, sus fotos y su serie
     * economica. Escribir en cualquiera de las tres es escribir un hecho del
     * inmueble o de su encargo, y las tres estaban abiertas al tenant entero.
     */
    private static final Set<String> REPOSITORIOS_DE_LA_PROPIEDAD = Set.of(
            "com.controllocal.persistence.repositorio.PropiedadRepository",
            "com.controllocal.persistence.repositorio.FotoPropiedadRepository",
            "com.controllocal.persistence.repositorio.TitularidadPropiedadRepository");

    /**
     * <b>La serie economica es del ENCARGO, no de la propiedad</b>, y por eso
     * es un universo aparte con OTRA guarda.
     *
     * <p>No es una sutileza: fundir los dos universos hacia "llama a la
     * autoridad, la que sea" es lo que dejo pasar el primer sabotaje de este
     * gate. Quitar {@code exigirEdicion} de {@code editar} lo dejaba VERDE,
     * porque {@code editar} llama a {@code actualizarEncargo} y aquel si
     * consulta la autoridad -- <b>la del encargo</b>. Dos autoridades distintas
     * comprobadas como si fueran una es exactamente el OR que este P0 vino a
     * quitar de {@code exigirPertenencia}.
     */
    private static final Set<String> REPOSITORIOS_DE_LA_SERIE = Set.of(
            "com.controllocal.persistence.repositorio.PrecioPropiedadRepository");

    /**
     * <b>La guarda de los hechos de la PROPIEDAD: los metodos que DENIEGAN.</b>
     *
     * <p>{@code puedeEditar} y {@code motivoNoEditable} estuvieron aqui y
     * tuvieron que salir: <b>preguntan, no impiden</b>. Los usa la ficha para
     * pintar, asi que cualquier caso de uso que devuelva la ficha los llama de
     * rebote — y con ellos dentro de la lista, {@code editar} contaba como
     * protegido por el simple hecho de responder. Fue el segundo sabotaje de
     * este gate: quitar {@code exigirEdicion} de {@code editar} seguia dando
     * VERDE porque {@code editar} termina en {@code ficha(...)}.
     *
     * <p>La leccion es la que ya conocia este repositorio en otra forma: una
     * comprobacion que acepta un LECTOR como si fuera una guarda no comprueba
     * nada.
     */
    private static final Set<String> GUARDAS_DE_LA_PROPIEDAD =
            Set.of("exigirEdicion", "fijarAlAlta", "asignar");

    /** La guarda de los hechos de un ENCARGO y de su historico economico. */
    private static final Set<String> GUARDAS_DEL_ENCARGO =
            Set.of("exigirEdicionDelEncargo");

    private static final Set<String> ESCRIBEN = Set.of(
            "save", "saveAndFlush", "delete", "deleteById", "saveAll");

    /**
     * <b>Las excepciones, con su motivo, y aqui para que se vean.</b>
     *
     * <p>No es una lista de perdones: es la parte del inventario que se
     * respondio con "no, y por esto". Cada entrada tuvo que justificarse una
     * por una, y anadir la siguiente obliga a escribir su razon en el mismo
     * sitio donde cualquiera la va a leer.
     */
    private static final Map<String, String> SIN_AUTORIDAD_PROPIA = Map.of(
            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#registrar",
            "el alta CREA la fila: no hay responsable anterior a quien respetar. Fija el "
                    + "suyo por AutoridadDePropiedad.fijarAlAlta, que es lo contrario de "
                    + "saltarse la autoridad",

            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#asignarResponsable",
            "ES el traspaso: llama a AutoridadDePropiedad.asignar, que exige broker. "
                    + "Pedirle ademas la autoridad de edicion haria imposible asignar "
                    + "responsable a una propiedad FALTANTE, que es justo para lo que existe",

            "com.controllocal.service.impl.ProspeccionServiceImpl#captar",
            "es el ciclo del ENCARGO, no la edicion de la ficha. Ya tiene su autoridad "
                    + "-cargarEnProceso exige que la prospeccion sea del actor- y las tres "
                    + "columnas que toca son la proyeccion del importe del encargo y su "
                    + "entrada al mercado. Exigir aqui la de la propiedad impediria captar "
                    + "una propiedad FALTANTE, y FALTANTE no bloquea el encargo",

            "com.controllocal.service.impl.ContratoServiceImpl#cerrarLocal",
            "es el ciclo del CONTRATO, y lo ejecuta el BROKER, que por P0-1 nunca es "
                    + "responsable de una propiedad: exigir la autoridad de edicion aqui "
                    + "significaria que ningun contrato se puede cerrar jamas",

            "com.controllocal.service.impl.ContratoServiceImpl#revisarDisponibilidad",
            "misma razon que cerrarLocal, al reves: recupera la disponibilidad cuando el "
                    + "contrato termina. Exigirla dejaria todo inmueble ALQUILADO para "
                    + "siempre");

    /**
     * <b>Solo la autoridad mueve la autoridad.</b>
     *
     * <p>Si {@code Propiedad#responsable} fuera de uso libre, cualquier caso de
     * uso podria darse el permiso a si mismo antes de comprobarlo — y la
     * comprobacion seguiria ahi, verde, sin proteger nada. Es el mismo
     * razonamiento por el que el estado solo muta por {@code Transiciones}.
     */
    @Test
    @DisplayName("solo AutoridadDePropiedad fija quien responde por una propiedad")
    void unSoloEscritorDelResponsable() {
        List<String> intrusos = CLASES.stream()
                .filter(clase -> !AUTORIDAD.equals(clase.getFullName()))
                .flatMap(clase -> clase.getMethods().stream())
                .filter(metodo -> metodo.getAccessesFromSelf().stream()
                        .anyMatch(a -> PROPIEDAD.equals(a.getTargetOwner().getFullName())
                                && "responsable".equals(a.getName())))
                .map(metodo -> metodo.getOwner().getName() + "#" + metodo.getName())
                .sorted()
                .toList();

        assertEquals(List.of(), intrusos,
                "estos metodos fijan el responsable de una propiedad por su cuenta: " + intrusos
                        + ". Quien puede escribir la propiedad lo decide AutoridadDePropiedad, y "
                        + "solo cambia por el alta o por un traspaso de broker con su fila en "
                        + "asignacion_responsable_propiedad. Un setter suelto convierte la "
                        + "autoridad en autoservicio.");
    }

    /**
     * <b>Y el rastro del traspaso lo escribe quien decide el traspaso.</b>
     *
     * <p>Sin esto, un segundo escritor podria mover la columna dejando una fila
     * distinta —o ninguna— y el expediente diria dos cosas segun por donde se
     * hubiera hecho. Es la leccion de {@code EventosSeguridad}, que es el unico
     * escritor de {@code evento_seguridad} por la misma razon.
     */
    @Test
    @DisplayName("solo AutoridadDePropiedad escribe el rastro de los traspasos")
    void unSoloEscritorDelRastroDeTraspasos() {
        noClasses()
                .that().haveNameNotMatching(java.util.regex.Pattern.quote(AUTORIDAD) + "(\\$.*)?")
                .should().dependOnClassesThat(new DescribedPredicate<>(
                        "es el repositorio del rastro de traspasos") {
                    @Override
                    public boolean test(JavaClass clase) {
                        return REPOSITORIO_DEL_RASTRO.equals(clase.getFullName());
                    }
                })
                .because("""
                        el traspaso y su rastro son un solo hecho. Un segundo escritor \
                        podria mover la columna sin dejar fila -o dejando otra distinta- y \
                        el expediente diria dos cosas segun por donde se hubiera hecho. \
                        Quien necesite traspasar llama a AutoridadDePropiedad.asignar""")
                .check(CLASES);
    }

    /**
     * <b>La comprobacion que caza el olvido real: la via numero nueve.</b>
     *
     * <p>Se recorre <b>dos veces</b>, una por universo, y con la guarda que le
     * toca a cada uno. Esa separacion no es elegancia: es la correccion que
     * exigio el primer sabotaje de este gate. Con un solo universo y "que llame
     * a la autoridad, la que sea", quitar {@code exigirEdicion} de
     * {@code editar} dejaba el gate VERDE — porque {@code editar} llama a
     * {@code actualizarEncargo}, que consulta la autoridad <b>del encargo</b>.
     * Un gate que acepta la autoridad equivocada no protege de nada: es el
     * mismo OR de {@code exigirPertenencia} un nivel mas arriba.
     */
    @Test
    @DisplayName("toda escritura sobre la propiedad pasa por SU autoridad, o declara por que no")
    void ningunaEscrituraDeLaPropiedadSinSuAutoridad() {
        List<JavaMethod> escrituras = escriturasDe(AutoridadDeLaPropiedadTest::escribeLaPropiedad);
        List<String> nombres = nombresDe(escrituras);

        // CONTROL POSITIVO. Sin esto, el dia que alguien renombre `save` o mueva
        // un repositorio de paquete, el predicado dejaria de reconocer nada y
        // este gate pasaria en verde midiendo una lista vacia. Un cero que no se
        // ha comprobado contra un caso conocido no es una comprobacion -- es la
        // leccion de `grep -iF` del 2026-08-24.
        assertTrue(nombres.size() >= 6,
                "el gate dejo de reconocer las escrituras de la PROPIEDAD: encontro " + nombres
                        + ". Revisa REPOSITORIOS_DE_LA_PROPIEDAD y ESCRIBEN antes de creerte el "
                        + "verde.");
        for (String imprescindible : List.of(
                "com.controllocal.service.impl.PropiedadUniversalServiceImpl#editar",
                "com.controllocal.service.impl.LocalComercialServiceImpl#desactivar",
                "com.controllocal.service.impl.LocalComercialServiceImpl#agregarFoto")) {
            assertTrue(nombres.contains(imprescindible),
                    "el gate no ve " + imprescindible + ", que es una de las vias por las que "
                            + "empezo todo esto. Encontro: " + nombres);
        }

        assertEquals(List.of(), sinGuarda(escrituras, GUARDAS_DE_LA_PROPIEDAD),
                "estos metodos escriben un hecho de la PROPIEDAD sin preguntar quien responde "
                        + "por ella: " + sinGuarda(escrituras, GUARDAS_DE_LA_PROPIEDAD)
                        + ". Tiene que ser exigirEdicion -- no vale exigirEdicionDelEncargo, que "
                        + "responde otra pregunta. O eso, o entran en SIN_AUTORIDAD_PROPIA con "
                        + "el motivo escrito.");
    }

    /**
     * <b>Y el historico economico responde a la autoridad del ENCARGO.</b>
     *
     * <p>Universo aparte porque la pregunta es otra: un hito {@code U}, {@code P}
     * o {@code C} no es un hecho del inmueble, es un hecho del trato que lo
     * autorizo. De aqui salio el hallazgo de este corte —
     * {@code PublicacionServiceImpl}, que escribia un {@code P} en la serie de
     * cualquier encargo del tenant— y no estaba en el inventario de ocho vias:
     * lo encontro este gate.
     */
    @Test
    @DisplayName("todo hito economico pasa por la autoridad de SU encargo")
    void ningunHitoEconomicoSinLaAutoridadDelEncargo() {
        List<JavaMethod> escrituras = escriturasDe(AutoridadDeLaPropiedadTest::escribeLaSerie);
        List<String> nombres = nombresDe(escrituras);

        assertTrue(nombres.size() >= 4,
                "el gate dejo de reconocer las escrituras de la SERIE ECONOMICA: encontro "
                        + nombres + ". Revisa REPOSITORIOS_DE_LA_SERIE antes de creerte el verde.");
        assertTrue(nombres.contains(
                        "com.controllocal.service.impl.PublicacionServiceImpl"
                                + "#registrarImportePublicado"),
                "el gate no ve la escritura de hito desde la publicacion, que es justo la que "
                        + "nadie habia inventariado. Encontro: " + nombres);

        assertEquals(List.of(), sinGuarda(escrituras, GUARDAS_DEL_ENCARGO),
                "estos metodos escriben en la serie economica de un encargo sin comprobar que "
                        + "sea del actor: " + sinGuarda(escrituras, GUARDAS_DEL_ENCARGO)
                        + ". Tiene que ser exigirEdicionDelEncargo -- no vale exigirEdicion, "
                        + "porque responder por la propiedad no es responder por el encargo de "
                        + "otro.");
    }

    private List<JavaMethod> escriturasDe(java.util.function.Predicate<JavaMethod> escribe) {
        return CLASES.stream()
                .filter(clase -> clase.getPackageName().startsWith("com.controllocal.service"))
                // `Transiciones` ES la primitiva de transicion, no un llamador:
                // exigirle que consulte la autoridad seria pedirle que decida
                // sobre la propiedad a la pieza que existe justo para no decidir
                // -- solo aplica y audita. La autoridad se pregunta en el caso de
                // uso, que es quien sabe que esta haciendo.
                .filter(clase -> !TRANSICIONES.equals(clase.getFullName()))
                .flatMap(clase -> clase.getMethods().stream())
                .filter(escribe)
                .toList();
    }

    private static List<String> nombresDe(List<JavaMethod> metodos) {
        return metodos.stream().map(AutoridadDeLaPropiedadTest::nombre).distinct().sorted().toList();
    }

    private List<String> sinGuarda(List<JavaMethod> escrituras, Set<String> guardas) {
        return escrituras.stream()
                .filter(m -> !cubierto(m, guardas, new HashSet<>()))
                .map(AutoridadDeLaPropiedadTest::nombre)
                .distinct()
                .sorted()
                .toList();
    }

    private static String nombre(JavaMethod metodo) {
        return metodo.getOwner().getName() + "#" + metodo.getName();
    }

    /**
     * <b>Y el gate cubre las cuatro tablas que son la propiedad</b>, no las que
     * recuerde.
     *
     * <p>Control de cobertura, no documentacion: es la contramedida al fallo que
     * ya ocurrio en 4.P, donde se inventariaron los productores de cuatro tablas
     * y no los de cuatro columnas, y el gate quedo verde vigilando la mitad.
     */
    @Test
    @DisplayName("el gate ve escrituras en las cuatro tablas de la propiedad")
    void elGateCubreLasCuatroTablas() {
        for (String repositorio : REPOSITORIOS_DE_LA_PROPIEDAD) {
            boolean alguna = CLASES.stream()
                    .filter(clase -> clase.getPackageName().startsWith("com.controllocal.service"))
                    .flatMap(clase -> clase.getMethods().stream())
                    .anyMatch(m -> m.getAccessesFromSelf().stream()
                            .anyMatch(a -> repositorio.equals(a.getTargetOwner().getFullName())
                                    && ESCRIBEN.contains(a.getName())));
            assertTrue(alguna,
                    "el gate no encuentra NINGUNA escritura por " + repositorio + ". O el "
                            + "repositorio cambio de nombre o de paquete, o su metodo de "
                            + "escritura ya no se llama como los de ESCRIBEN. En cualquiera de "
                            + "los dos casos esa tabla ha dejado de estar vigilada.");
        }
    }

    // ------------------------------------------------------------------
    // Predicados
    // ------------------------------------------------------------------

    /**
     * Escribe la propiedad quien guarda en una de sus cuatro tablas, o quien
     * transiciona su estado.
     *
     * <p>La segunda mitad no es opcional: {@code desactivar} no llama a
     * {@code propiedades.save} en ningun momento — la transicion muta la entidad
     * gestionada y JPA la vuelca sola. Un gate que solo mirara los repositorios
     * dejaria fuera exactamente la via que retira una propiedad del registro.
     *
     * <p>Se mira {@code aplicarDisponibilidad} y no {@code aplicar} a secas
     * porque {@code Transiciones} sirve tambien a captaciones, contratos,
     * prospecciones y solicitudes: {@code aplicar} generico daba tres falsos
     * positivos medidos —{@code CaptacionServiceImpl#decidir},
     * {@code ProspeccionServiceImpl#marcarCaptado} y
     * {@code ContratoServiceImpl#transicionarContrato}— que transicionan OTRA
     * entidad y solo leen la propiedad de paso. {@code aplicarDisponibilidad},
     * en cambio, es de la propiedad por construccion: no hay otra entidad con
     * disponibilidad comercial.
     */
    private static boolean escribeLaPropiedad(JavaMethod metodo) {
        boolean porRepositorio = metodo.getAccessesFromSelf().stream().anyMatch(a ->
                REPOSITORIOS_DE_LA_PROPIEDAD.contains(a.getTargetOwner().getFullName())
                        && ESCRIBEN.contains(a.getName()));
        boolean porTransicion = metodo.getAccessesFromSelf().stream().anyMatch(a ->
                TRANSICIONES.equals(a.getTargetOwner().getFullName())
                        && "aplicarDisponibilidad".equals(a.getName()));
        return porRepositorio || porTransicion;
    }

    /** Escribe la serie economica quien guarda un hito de precio. */
    private static boolean escribeLaSerie(JavaMethod metodo) {
        return metodo.getAccessesFromSelf().stream().anyMatch(a ->
                REPOSITORIOS_DE_LA_SERIE.contains(a.getTargetOwner().getFullName())
                        && ESCRIBEN.contains(a.getName()));
    }

    /**
     * <b>Cubierto = pregunta, o esta declarado, o solo se llega por alguien que
     * cumple una de las dos.</b>
     *
     * <p>La tercera rama es la que evita convertir el gate en una lista de
     * perdones para metodos privados. {@code escribirTitularidades} no consulta
     * la autoridad y no hace falta que lo haga: su unico llamador es
     * {@code registrar}, que la fija. Exigirsela a cada auxiliar obligaria a
     * declarar media docena de excepciones cuyo motivo real seria "es privado",
     * y una lista de excepciones que nadie lee ya no protege nada.
     *
     * <p>Y no la debilita: lo que caza este gate es la <b>puerta nueva</b>, y
     * una puerta nueva es por definicion alcanzable desde fuera de la clase. Un
     * metodo sin ningun llamador dentro de su clase es publico de hecho, y
     * entonces la tercera rama no aplica y tiene que responder por si mismo.
     */
    private static boolean cubierto(JavaMethod metodo, Set<String> guardas, Set<String> visitados) {
        if (SIN_AUTORIDAD_PROPIA.containsKey(nombre(metodo))) {
            return true;
        }
        if (consultaLaAutoridad(metodo, guardas, new HashSet<>())) {
            return true;
        }
        if (!visitados.add(metodo.getFullName())) {
            // Ciclo entre auxiliares: no se puede concluir que este cubierto
            // por el camino de sus llamadores. El lado seguro es "no".
            return false;
        }
        List<JavaMethod> llamadores = metodo.getOwner().getMethods().stream()
                .filter(otro -> !otro.equals(metodo))
                .filter(otro -> otro.getAccessesFromSelf().stream()
                        .anyMatch(a -> a.getTargetOwner().equals(metodo.getOwner())
                                && a.getName().equals(metodo.getName())))
                .toList();
        return !llamadores.isEmpty()
                && llamadores.stream().allMatch(otro -> cubierto(otro, guardas, visitados));
    }

    /**
     * <b>Consulta LA guarda que le toca</b>, aunque sea a traves de un metodo
     * propio.
     *
     * <p>El {@code guardas} no es un detalle: sin el, llamar a cualquier metodo
     * de {@code AutoridadDePropiedad} contaria como haber preguntado, y
     * preguntar por el encargo no responde por la propiedad. El sabotaje que
     * quito {@code exigirEdicion} de {@code editar} paso por ahi.
     */
    private static boolean consultaLaAutoridad(JavaMethod metodo, Set<String> guardas,
                                               Set<String> visitados) {
        if (!visitados.add(metodo.getFullName())) {
            return false;
        }
        for (JavaAccess<?> llamada : metodo.getAccessesFromSelf()) {
            JavaClass destino = llamada.getTargetOwner();
            if (AUTORIDAD.equals(destino.getFullName()) && guardas.contains(llamada.getName())) {
                return true;
            }
            if (!destino.getFullName().equals(metodo.getOwner().getFullName())) {
                continue;
            }
            for (JavaMethod propio : destino.getMethods()) {
                if (propio.getName().equals(llamada.getName())
                        && consultaLaAutoridad(propio, guardas, visitados)) {
                    return true;
                }
            }
        }
        return false;
    }
}
