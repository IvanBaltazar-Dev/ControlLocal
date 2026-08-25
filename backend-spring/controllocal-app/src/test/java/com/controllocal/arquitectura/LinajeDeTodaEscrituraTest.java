package com.controllocal.arquitectura;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Toda escritura gobernada deja de donde salio</b> (4.P, invariante 1).
 *
 * <h2>Por que este gate y no un NOT NULL</h2>
 * La invariante «toda escritura gobernada deja procedencia» <b>no se puede
 * sostener en el esquema</b>, y eso es una decision medida, no una concesion:
 * seis suites E2E y {@code gate-modelo-universal.sql} escriben en
 * {@code atributo_propiedad} por SQL directo <b>a proposito</b>, para probar los
 * triggers de la base intentando romperlos. Un {@code NOT NULL} en la
 * procedencia convertiria al gate en rehen del servicio y le quitaria
 * exactamente la capacidad por la que existe.
 *
 * <p>Asi que la invariante vive en la <b>frontera del servicio</b>, y esta clase
 * es esa frontera hecha comprobable.
 *
 * <h2>Que comprueba</h2>
 * <ol>
 *   <li>Que solo {@code LinajeDelValor} escribe el rastro. Un segundo escritor
 *       no falla: <b>diverge</b>, y en dos cortes habria dos formas de contar la
 *       misma historia.</li>
 *   <li>Que <b>todo metodo que escribe un valor gobernado llama al linaje</b>.
 *       Es la comprobacion que caza el olvido real: anadir una superficie nueva
 *       —una sexta— y no anotarla.</li>
 *   <li>Que el vocabulario de {@code naturaleza} tiene <b>exactamente tres</b>
 *       valores. Un cuarto —{@code DESCONOCIDO}, y siempre es ese— colapsaria
 *       «no consta como se supo» con «se supo por una inferencia», que son cosas
 *       distintas y a Intelligence le importaran mucho.</li>
 * </ol>
 *
 * <h2>Que NO comprueba, dicho en vez de disimulado</h2>
 * <b>No ve un escritor privado nuevo dentro de los propios enrutadores</b> si lo
 * llama un metodo publico que si anota. Es el mismo limite que ya declara
 * {@code PuertasDePublicacionTest}, y se dice aqui para que nadie lea este gate
 * como una garantia mas fuerte de lo que es.
 *
 * <p>Y <b>no ve nada de lo que se escriba por SQL directo</b>. Un {@code INSERT}
 * manual en {@code atributo_propiedad} sigue siendo posible y sigue sin dejar
 * linaje: lo que garantiza el servicio es que <b>ninguna operacion del producto</b>
 * escribe un valor sin decir de donde sale.
 */
class LinajeDeTodaEscrituraTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    private static final String LINAJE = "com.controllocal.service.soporte.LinajeDelValor";

    private static final List<String> ENRUTADORES = List.of(
            "com.controllocal.service.soporte.AtributosGobernados",
            "com.controllocal.service.soporte.AtributosDeEncargo");

    /**
     * Quien puede escribir un valor gobernado.
     *
     * <p>Los dos enrutadores, porque son los que anotan el linaje; y
     * {@code EscritorEstructural}, que es el <b>adaptador</b> con el que el
     * enrutador de la propiedad toca las columnas canonicas. No es una
     * excepcion abierta: esta misma prueba comprueba que nadie mas invoca a ese
     * adaptador, asi que la unica forma de llegar a las cuatro columnas sigue
     * pasando por {@code AtributosGobernados}.
     */
    private static final Set<String> ESCRITORES_AUTORIZADOS = Set.of(
            "com.controllocal.service.soporte.AtributosGobernados",
            "com.controllocal.service.soporte.AtributosDeEncargo",
            "com.controllocal.service.soporte.EscritorEstructural");

    /** Los repositorios donde vive el linaje. Solo los toca quien lo escribe. */
    private static final Set<String> REPOSITORIOS_DE_RASTRO = Set.of(
            "com.controllocal.persistence.repositorio.RastroValorGobernadoRepository",
            "com.controllocal.persistence.repositorio.OpcionDeRastroRepository");

    /**
     * Los metodos por los que un valor gobernado llega al almacenamiento.
     *
     * <p>Las cinco superficies de escritura pasan por alguno de estos: escalar
     * (que puede acabar en una fila o en un campo canonico del agregado),
     * multivalor y retirada, en los dos sujetos.
     */
    private static final Set<String> ESCRIBEN_UN_VALOR = Set.of(
            "save", "saveAndFlush", "deleteByIdPropiedadAndClave", "deleteByIdCaptacionAndClave",
            "borrarDe", "aplicar", "vaciar",
            // Los setters de las cuatro columnas ESTRUCTURAL. Estaban FUERA de
            // esta lista y por ahi entro el defecto que la auditoria encontro:
            // `aplicarUbicacion` escribia `propiedad.setPiso(...)` directamente
            // -- `piso` es una clave gobernada declarada ESTRUCTURAL -- y ni
            // este gate ni el .sql podian verlo, porque una clave ESTRUCTURAL
            // por definicion NO CREA FILA en `atributo_propiedad`.
            "setMetraje", "setPiso", "setPartidaRegistral", "setOficinaRegistral");

    private static final Set<String> DESTINOS_DE_VALOR = Set.of(
            "com.controllocal.persistence.repositorio.AtributoPropiedadRepository",
            "com.controllocal.persistence.repositorio.AtributoEncargoRepository",
            "com.controllocal.persistence.repositorio.ValorMultipleAtributoRepository",
            "com.controllocal.persistence.repositorio.ValorMultipleEncargoRepository",
            "com.controllocal.service.soporte.EscritorEstructural",
            // La QUINTA superficie no tiene tabla: escribe columnas del
            // agregado. Vigilar solo las cuatro tablas dejaba esa mitad sin
            // gate, y el inventario que dio pie a 4.P barrio «productores de las
            // cuatro tablas de valor» sin inventariar nunca «productores de las
            // cuatro columnas ESTRUCTURAL».
            "com.controllocal.domain.inmueble.Propiedad");

    /**
     * Los conceptos canonicos declarados, con el setter que los escribe.
     *
     * <p>No es documentacion: es el <b>control de cobertura</b> del gate. Si
     * manana aparece un quinto {@code CAMPO_...} en {@link CatalogoAtributo} y
     * su setter no entra en {@link #ESCRIBEN_UN_VALOR}, esta lista deja de
     * cuadrar y el gate se pone rojo -- en vez de seguir en verde vigilando
     * cuatro columnas de cinco, que es exactamente como paso desapercibido el
     * agujero de {@code piso}.
     */
    private static final Map<String, String> SETTER_POR_CONCEPTO = Map.of(
            CatalogoAtributo.CAMPO_METRAJE, "setMetraje",
            CatalogoAtributo.CAMPO_PISO, "setPiso",
            CatalogoAtributo.CAMPO_PARTIDA_REGISTRAL, "setPartidaRegistral",
            CatalogoAtributo.CAMPO_OFICINA_REGISTRAL, "setOficinaRegistral");

    /**
     * <b>La unica excepcion, con su motivo</b>, y esta escrita aqui para que se
     * vea cada vez que alguien lea el gate.
     *
     * <p>{@code aplicarEstructuralesAlAlta} escribe {@code propiedad.metraje}
     * —NOT NULL— <b>antes</b> del primer {@code save}, y antes de ese
     * {@code save} la propiedad no tiene id, que es la coordenada por la que se
     * direcciona el rastro. Las dos exigencias son ciertas y apuntan en
     * direcciones contrarias, asi que el alta ocurre en dos tiempos y el linaje
     * lo anota el segundo, {@code escribirAlAlta}, en la misma transaccion.
     */
    private static final Set<String> SIN_LINAJE_PROPIO = Set.of("aplicarEstructuralesAlAlta");

    @Test
    @DisplayName("solo LinajeDelValor escribe el rastro de procedencia")
    void unSoloEscritorDelRastro() {
        noClasses()
                .that().haveNameNotMatching(java.util.regex.Pattern.quote(LINAJE) + "(\\$.*)?")
                .should().dependOnClassesThat(new DescribedPredicate<>(
                        "son repositorios del rastro de procedencia") {
                    @Override
                    public boolean test(JavaClass clase) {
                        return REPOSITORIOS_DE_RASTRO.contains(clase.getFullName());
                    }
                })
                .because("""
                        un segundo escritor del linaje no falla: DIVERGE. Uno anotaria el \
                        valor hallado y el otro no, o uno el conjunto entero de un \
                        multivalor y el otro la diferencia, y la historia diria dos cosas \
                        distintas segun por donde se hubiera escrito. Quien necesite dejar \
                        constancia llama a LinajeDelValor""")
                .check(CLASES);
    }

    @Test
    @DisplayName("todo metodo que escribe un valor gobernado anota su linaje")
    void ningunaEscrituraSinProcedencia() {
        for (String enrutador : ENRUTADORES) {
            JavaClass clase = CLASES.get(enrutador);
            List<String> escriben = clase.getMethods().stream()
                    .filter(LinajeDeTodaEscrituraTest::escribeUnValor)
                    .map(JavaMethod::getName)
                    .sorted()
                    .toList();

            // CONTROL POSITIVO. Sin esto, el dia que alguien renombre `save` o
            // mueva una escritura, `escribeUnValor` dejaria de reconocer nada y
            // el gate pasaria en verde midiendo una lista vacia. Un cero que no
            // se ha comprobado contra un caso conocido no es una comprobacion.
            assertTrue(escriben.size() >= 3,
                    "el gate dejo de reconocer las escrituras de " + enrutador + ": encontro "
                            + escriben + ". Deberia ver al menos las tres superficies -- escalar, "
                            + "multivalor y retirada. Revisa ESCRIBEN_UN_VALOR y DESTINOS_DE_VALOR "
                            + "antes de creerte el verde.");

            List<String> sinLinaje = escriben.stream()
                    .filter(nombre -> !SIN_LINAJE_PROPIO.contains(nombre))
                    .filter(nombre -> clase.getMethods().stream()
                            .filter(metodo -> metodo.getName().equals(nombre))
                            .noneMatch(LinajeDeTodaEscrituraTest::llamaAlLinaje))
                    .sorted()
                    .toList();

            assertEquals(List.of(), sinLinaje,
                    "en " + enrutador + " estos metodos escriben un valor gobernado sin anotar de "
                            + "donde sale: " + sinLinaje + ". Si la escritura tiene que ocurrir en "
                            + "dos tiempos, declaralo en SIN_LINAJE_PROPIO con su motivo, como "
                            + "aplicarEstructuralesAlAlta.");
        }
    }

    /**
     * <b>Y nadie escribe un valor gobernado fuera de los dos enrutadores.</b>
     *
     * <p>Sin esto, el gate anterior seria un teatro: bastaria con volver a
     * guardar desde el caso de uso —como se hacia hasta 4.P— para escribir un
     * valor sin pasar por el sitio que anota su procedencia.
     */
    @Test
    @DisplayName("el caso de uso no escribe valores gobernados: los orquesta")
    void soloLosEnrutadoresEscribenValores() {
        List<String> intrusos = CLASES.stream()
                .filter(clase -> !ESCRITORES_AUTORIZADOS.contains(clase.getFullName()))
                .filter(clase -> !clase.getFullName().startsWith(LINAJE))
                .flatMap(clase -> clase.getMethods().stream())
                .filter(LinajeDeTodaEscrituraTest::escribeUnValor)
                .map(metodo -> metodo.getOwner().getName() + "#" + metodo.getName())
                .sorted()
                .toList();

        assertEquals(List.of(), intrusos,
                "estos metodos escriben un valor gobernado fuera del enrutador de su sujeto: "
                        + intrusos + ". El enrutador es el que anota de donde sale cada valor; "
                        + "escribir por fuera es el camino por el que un dato entra sin "
                        + "procedencia. Si lo que escribes es una columna ESTRUCTURAL --"
                        + " metraje, piso, partida u oficina registral -- acuerdate de que esas "
                        + "NO crean fila en atributo_propiedad: enrutala por AtributosGobernados "
                        + "o no dejara linaje jamas, y ningun gate .sql podra verlo.");
    }

    /**
     * <b>La quinta superficie, vigilada entera</b> (segunda vuelta de 4.P).
     *
     * <p>El agujero que la auditoria encontro no fue un descuido de escritura:
     * fue un descuido de <b>inventario</b>. Se barrieron los productores de las
     * cuatro TABLAS de valor y no se inventariaron nunca los de las cuatro
     * COLUMNAS {@code ESTRUCTURAL}; la superficie 5 se demostro con
     * {@code metraje_total} —que no tiene segunda puerta— y la conclusion se
     * extendio a las cuatro sin medirlas una por una. {@code piso} si la tenia:
     * el hueco {@code ubicacion.piso} del cable, que es <b>el unico camino que
     * usa el producto</b>.
     *
     * <p>Esta prueba no vigila codigo: vigila que <b>el gate siga cubriendo
     * todos los conceptos declarados</b>. Un quinto campo canonico sin su setter
     * en la lista pone el gate en rojo, en vez de dejarlo en verde mirando
     * cuatro de cinco.
     */
    @Test
    @DisplayName("el gate cubre TODAS las columnas estructurales declaradas, no las que recuerde")
    void elGateCubreLasCuatroColumnasEstructurales() {
        List<String> conceptos = java.util.Arrays.stream(CatalogoAtributo.class.getFields())
                .filter(campo -> campo.getName().startsWith("CAMPO_"))
                .map(campo -> {
                    try {
                        return String.valueOf(campo.get(null));
                    } catch (IllegalAccessException e) {
                        throw new AssertionError(e);
                    }
                })
                .sorted()
                .toList();

        assertEquals(conceptos.stream().sorted().toList(),
                SETTER_POR_CONCEPTO.keySet().stream().sorted().toList(),
                "aparecio un campo canonico que este gate no conoce. Anadelo a "
                        + "SETTER_POR_CONCEPTO con el setter que lo escribe, o su columna quedara "
                        + "sin vigilar: una clave ESTRUCTURAL no crea fila, asi que si se escribe "
                        + "por fuera del enrutador NO deja linaje y NINGUN gate .sql lo ve.");

        List<String> sinVigilar = SETTER_POR_CONCEPTO.values().stream()
                .filter(setter -> !ESCRIBEN_UN_VALOR.contains(setter))
                .sorted()
                .toList();
        assertEquals(List.of(), sinVigilar,
                "estos setters de columna estructural no estan en ESCRIBEN_UN_VALOR: " + sinVigilar);

        // CONTROL POSITIVO de la superficie 5: el gate tiene que estar viendo de
        // verdad al escritor estructural. Si `escribeUnValor` dejara de
        // reconocerlo, las dos comprobaciones de arriba seguirian en verde
        // midiendo listas que ya no vigilan nada.
        JavaClass escritor = CLASES.get("com.controllocal.service.soporte.EscritorEstructural");
        assertTrue(escritor.getMethods().stream().anyMatch(LinajeDeTodaEscrituraTest::escribeUnValor),
                "el gate dejo de reconocer a EscritorEstructural como escritor de columnas "
                        + "canonicas: revisa ESCRIBEN_UN_VALOR y DESTINOS_DE_VALOR antes de "
                        + "creerte el verde.");
    }

    /**
     * <b>Tres naturalezas, y ni una mas.</b>
     *
     * <p>El cuarto valor que alguien intentara anadir se llama {@code DESCONOCIDO},
     * y no es un sinonimo de la ausencia:
     *
     * <pre>
     *   ausente   -> NO sabemos COMO se obtuvo el hecho
     *   INFERIDO  -> SI lo sabemos: por una inferencia
     * </pre>
     *
     * <p>Meter los dos en el mismo vocabulario los colapsa, y «no consta como se
     * supo» no es un metodo de obtencion.
     */
    @Test
    @DisplayName("la naturaleza tiene tres valores y la ausencia no es el cuarto")
    void elVocabularioDeNaturalezaNoCrece() {
        assertEquals(Set.of("DECLARADO", "OBSERVADO", "INFERIDO"),
                com.controllocal.domain.auditoria.RastroValorGobernado.NATURALEZAS,
                "cambio el vocabulario de naturaleza. Si el valor nuevo es DESCONOCIDO -- o "
                        + "cualquier sinonimo de \"no consta\" --, no va aqui: la ausencia se "
                        + "representa como ausencia, y confundirla con INFERIDO borra la "
                        + "diferencia entre no saber como se supo algo y saber que se dedujo.");
    }

    // ------------------------------------------------------------------

    /**
     * <b>Escribe un valor gobernado, la invoque como la invoque.</b>
     *
     * <p>Se mira {@code getAccessesFromSelf()} y no {@code getMethodCallsFromSelf()},
     * y esa palabra costo el defecto entero: <b>una referencia a metodo no es una
     * llamada</b>. El agujero real se escribia asi —
     * {@code siViene(ubicacion.piso(), propiedad::setPiso)}— y con
     * {@code getMethodCallsFromSelf} el gate lo miraba de frente y lo daba por
     * bueno. Comprobado reintroduciendo el defecto: 5/5 en verde.
     */
    private static boolean escribeUnValor(JavaMethod metodo) {
        return metodo.getAccessesFromSelf().stream().anyMatch(acceso ->
                DESTINOS_DE_VALOR.contains(acceso.getTargetOwner().getFullName())
                        && ESCRIBEN_UN_VALOR.contains(acceso.getName()));
    }

    /**
     * <b>Llama al linaje, aunque sea a traves de un metodo suyo.</b>
     *
     * <p>La comprobacion es transitiva <b>dentro de la misma clase</b> a
     * proposito. Sin eso, factorizar el «anota alta o edicion segun lo que
     * hubiera» en un metodo privado —que es exactamente lo que hay que hacer
     * para no repetir la decision cuatro veces— pondria el gate en rojo y
     * empujaria a copiarla, que es peor.
     *
     * <p>Y se queda en la clase, no recorre todo el grafo: en cuanto la cadena
     * sale del enrutador, quien anota deja de estar a la vista de quien escribe.
     */
    private static boolean llamaAlLinaje(JavaMethod metodo) {
        return llamaAlLinaje(metodo, new java.util.HashSet<>());
    }

    private static boolean llamaAlLinaje(JavaMethod metodo, Set<String> visitados) {
        if (!visitados.add(metodo.getFullName())) {
            return false;
        }
        for (com.tngtech.archunit.core.domain.JavaAccess<?> llamada : metodo.getAccessesFromSelf()) {
            JavaClass destino = llamada.getTargetOwner();
            if (LINAJE.equals(destino.getFullName())) {
                return true;
            }
            if (!destino.getFullName().equals(metodo.getOwner().getFullName())) {
                continue;
            }
            for (JavaMethod propio : destino.getMethods()) {
                if (propio.getName().equals(llamada.getName())
                        && llamaAlLinaje(propio, visitados)) {
                    return true;
                }
            }
        }
        return false;
    }
}
