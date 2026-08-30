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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

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
 * una via: eran <b>varias</b>, repartidas por varios servicios, y casi ninguna
 * comprobaba nada mas que el tenant.
 *
 * <h2>Por que aqui no va ninguna cifra</h2>
 * La llevaba, y era falsa. Se escribio "ocho vias" —la cuenta del inventario
 * inicial— y se repitio cuatro veces, incluida "la novena" como nombre de lo
 * que este gate protege. Para cuando se escribio ya no era cierto: el propio
 * gate encontro una via que el inventario no tenia
 * ({@code PublicacionServiceImpl}), el corte declaro trece y la auditoria
 * conto doce. Tres cifras distintas para una sola cosa, y el resultado era
 * correcto: lo falso era <b>la explicacion</b>.
 *
 * <p>Es la familia de fallos que mas caro ha salido en este repositorio —una
 * cifra transcrita a mano caduca sola y nadie la revisa, porque nada la
 * verifica—. Asi que la regla aqui es: <b>o la genera una comprobacion, o no se
 * escribe</b>. Las vias las cuenta este gate en cada build contra el bytecode,
 * y la lista sale en el mensaje de error cuando alguna se queda sin guarda.
 *
 * <p>Lo que este gate protege es el <b>manana</b>: la <b>siguiente</b>. Un caso
 * de uso nuevo que guarde una propiedad, una foto, un atributo gobernado o un
 * hito economico sin pasar por {@code AutoridadDePropiedad} pone el build en
 * rojo, en vez de reabrir el agujero en silencio dos cortes despues, cuando ya
 * nadie recuerde por que la columna existe.
 *
 * <h2>Por que un gate y no un {@code @PreAuthorize}</h2>
 * Dos razones medidas, y las dos aparecieron en el inventario de este P0:
 * <ol>
 *   <li>Las vias viven repartidas por varios servicios. Una anotacion protege
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

    /** El paquete de dominio donde vive todo lo que ES la propiedad. */
    private static final String PAQUETE_DEL_INMUEBLE = "com.controllocal.domain.inmueble";

    private static final String REPOSITORIOS = "com.controllocal.persistence.repositorio.";

    /**
     * <b>Los hechos que SON la propiedad</b>, y la tabla de cada uno.
     *
     * <p>Es un mapa {@code repositorio -> tabla} y no un {@code Set} de
     * repositorios porque la unidad de la decision es la <b>tabla</b>: lo que
     * hay que poder responder es "¿que tablas de hechos del inmueble estan
     * vigiladas?", y un conjunto de repositorios no responde eso sin que
     * alguien traduzca de memoria.
     *
     * <p><b>Las dos ultimas entradas faltaban</b>, y se llevaban la mayor parte
     * del hecho: casi todas las claves gobernadas de una PROPIEDAD se guardan
     * como fila de {@code atributo_propiedad} (o de su tabla de opciones, si
     * son multivalor), y solo las cuatro declaradas ESTRUCTURAL viajan por
     * columnas de {@code propiedad}. El gate vigilaba las columnas y dejaba
     * fuera las filas.
     *
     * <p><b>La proporcion no se escribe aqui</b>, a proposito: la mide
     * {@code AlcanceYGobiernoDeLaAutoridadIntegrationTest} contra el catalogo
     * vivo. Este mismo corte fue rechazado, entre otras cosas, por transcribir
     * cifras que ya no eran ciertas cuando se escribieron.
     */
    private static final Map<String, String> TABLAS_DE_LA_PROPIEDAD = Map.of(
            REPOSITORIOS + "PropiedadRepository", "propiedad",
            REPOSITORIOS + "FotoPropiedadRepository", "foto_propiedad",
            REPOSITORIOS + "TitularidadPropiedadRepository", "titularidad_propiedad",
            REPOSITORIOS + "AtributoPropiedadRepository", "atributo_propiedad",
            REPOSITORIOS + "ValorMultipleAtributoRepository", "atributo_propiedad_opcion");

    /**
     * <b>Los hechos que son del ENCARGO</b>, que es otro universo con OTRA
     * guarda: {@code exigirEdicionDelEncargo}.
     *
     * <p>No es una sutileza: fundir los dos universos hacia "llama a la
     * autoridad, la que sea" es lo que dejo pasar el primer sabotaje de este
     * gate. Quitar {@code exigirEdicion} de {@code editar} lo dejaba VERDE,
     * porque {@code editar} llama a {@code actualizarEncargo} y aquel si
     * consulta la autoridad -- <b>la del encargo</b>. Dos autoridades distintas
     * comprobadas como si fueran una es exactamente el OR que este P0 vino a
     * quitar de {@code exigirPertenencia}.
     *
     * <p>Aqui entran la serie economica y las <b>condiciones pactadas</b>
     * (garantia, adelanto, plazo): son datos del trato tanto como el importe, y
     * responden ante quien lo negocio, no ante quien responde por el inmueble.
     */
    private static final Map<String, String> TABLAS_DEL_ENCARGO = Map.of(
            REPOSITORIOS + "PrecioPropiedadRepository", "precio_propiedad",
            REPOSITORIOS + "AtributoEncargoRepository", "atributo_encargo",
            REPOSITORIOS + "ValorMultipleEncargoRepository", "atributo_encargo_opcion");

    /**
     * <b>Lo que vive en el paquete del inmueble y NO es un hecho gobernado
     * suyo</b>, con el motivo de cada uno.
     *
     * <p>Existe para que el control de cobertura pueda ser una comparacion de
     * verdad. La version anterior recorria el mismo {@code Set} que declaraba,
     * asi que una tabla ausente del conjunto era <b>invisible</b> — el gate no
     * podia ver lo que le faltaba, que es exactamente la forma del fallo de 4.P
     * que decia prevenir. Ahora se enumeran las entidades reales del paquete y
     * cada tabla tiene que estar clasificada: vigilada en un universo, o aqui
     * con su razon. Una entidad nueva pone el gate en <b>rojo</b> hasta que
     * alguien decida cual de las dos cosas es.
     */
    private static final Map<String, String> FUERA_DEL_GOBIERNO_DE_LA_PROPIEDAD = Map.of(
            "asignacion_responsable_propiedad",
            "es el rastro de QUIEN puede escribir, no un hecho escrito. Tiene su propio "
                    + "gate -- unSoloEscritorDelRastroDeTraspasos -- que es mas estricto: "
                    + "un unico escritor, no una guarda",

            "catalogo_atributo",
            "es el vocabulario de la organizacion, no un dato de ningun inmueble. Lo "
                    + "gobierna el tenant; una propiedad no lo escribe nunca",

            "distrito",
            "es geografia compartida, anterior a cualquier propiedad y comun a todas. No "
                    + "pertenece a ninguna",

            "observacion_mercado",
            "es lo que se VIO del mercado (V76), y BROX no lo autorizo, ni lo publico, ni "
                    + "lo negocio. Es append-only y a proposito NO escribe la propiedad: "
                    + "exigir aqui la autoridad de edicion impediria observar un inmueble "
                    + "ajeno, que es justo para lo que existe",

            "publicacion",
            "es el anuncio de un ENCARGO, no un hecho del inmueble. Su autoridad es la del "
                    + "encargo y ya se comprueba: el hito 'P' que escribe cae en "
                    + "precio_propiedad, que si esta vigilado en el universo del encargo");

    private static final Set<String> REPOSITORIOS_DE_LA_PROPIEDAD =
            Set.copyOf(TABLAS_DE_LA_PROPIEDAD.keySet());

    private static final Set<String> REPOSITORIOS_DEL_ENCARGO =
            Set.copyOf(TABLAS_DEL_ENCARGO.keySet());

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

    /**
     * Los metodos por los que un repositorio escribe.
     *
     * <p>Los tres ultimos son los borrados <b>por clave logica</b>, y estaban
     * fuera: un {@code deleteByIdPropiedadAndClave} retira un hecho gobernado
     * igual que un {@code save} lo pone, pero no se llama "delete" a secas y el
     * gate no lo reconocia. Ya estaban inventariados en
     * {@code LinajeDeTodaEscrituraTest.ESCRIBEN_UN_VALOR}: el linaje los veia y
     * la autoridad no.
     */
    private static final Set<String> ESCRIBEN = Set.of(
            "save", "saveAndFlush", "delete", "deleteById", "saveAll",
            "deleteByIdPropiedadAndClave", "deleteByIdCaptacionAndClave", "borrarDe");

    /**
     * <b>Las excepciones del universo PROPIEDAD, con su motivo.</b>
     *
     * <p>No es una lista de perdones: es la parte del inventario que se
     * respondio con "no, y por esto". Cada entrada tuvo que justificarse una
     * por una, y anadir la siguiente obliga a escribir su razon en el mismo
     * sitio donde cualquiera la va a leer.
     *
     * <h2>Por que hay DOS mapas y no uno</h2>
     * Habia uno solo, consultado <b>antes</b> de mirar que guarda tocaba, asi
     * que una exencion escrita pensando en la PROPIEDAD eximia al mismo metodo
     * en el universo del ENCARGO. {@code cerrarLocal} estaba exento en los dos
     * por una unica entrada: se sostenia <b>por suerte, no por construccion</b>.
     * Era la fusion "que llame a la autoridad, la que sea" —la misma que este
     * gate quito del lado de las guardas— reintroducida por el lado de las
     * exenciones. Un metodo que necesite las dos ahora lo dice dos veces, cada
     * vez con su razon.
     */
    private static final Map<String, String> SIN_AUTORIDAD_DE_LA_PROPIEDAD = Map.of(
            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#registrar",
            "el alta CREA la fila: no hay responsable anterior a quien respetar. Fija "
                    + "el suyo por AutoridadDePropiedad.fijarAlAlta y lo deja escrito por "
                    + "anotarElAlta (V88), que es lo contrario de saltarse la autoridad. "
                    + "Solo vale para una propiedad NUEVA: reutilizar una existente jamas "
                    + "puede pasar por aqui, y el indice parcial uq_asignacion_alta_por_"
                    + "propiedad lo impide en la base",

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
                    + "siempre",

            "com.controllocal.service.soporte.AtributosGobernados#escribirAlAlta",
            "es el ALTA y solo el alta: escribe los gobernados de una propiedad que acaba "
                    + "de nacer, cuyo responsable lo acaba de fijar fijarAlAlta unas lineas "
                    + "antes. Su gemelo de edicion -escribirEnEdicion- SI exige la "
                    + "autoridad, y el nombre de este metodo es la unica forma de llegar al "
                    + "camino del alta",

            "com.controllocal.service.soporte.AtributosGobernados#aplicarEstructuralesAlAlta",
            "la otra mitad del alta en dos tiempos: aplica las claves ESTRUCTURAL sobre el "
                    + "agregado ANTES del primer save, cuando la fila todavia no tiene id. "
                    + "Misma razon que escribirAlAlta");

    /**
     * <b>Las excepciones del universo ENCARGO, con su motivo.</b>
     *
     * <p>Lista aparte de la de la propiedad, y esa separacion es la correccion:
     * una exencion concedida en un universo ya no vale en el otro.
     */
    private static final Map<String, String> SIN_AUTORIDAD_DEL_ENCARGO = Map.of(
            "com.controllocal.service.impl.ContratoServiceImpl#cerrarLocal",
            "cierra el contrato y con el la serie: escribe el hito 'C' del encargo que se "
                    + "firmo. Lo ejecuta el BROKER, que nunca es agente de un encargo, asi "
                    + "que exigirEdicionDelEncargo aqui significaria que ningun contrato se "
                    + "puede cerrar. Es la MISMA exencion que en el universo de la "
                    + "propiedad, y esta escrita dos veces a proposito: alli el motivo es la "
                    + "disponibilidad del inmueble, aqui es el hito economico. Una sola "
                    + "entrada para las dos ocultaba que son dos decisiones",

            "com.controllocal.service.soporte.AtributosDeEncargo#escribir",
            "no puede preguntar: recibe Comercializacion -un record con idCaptacion, tipo y "
                    + "operacion-, no la entidad Captacion, y exigirEdicionDelEncargo "
                    + "necesita la fila para saber de quien es. Sus DOS unicos llamadores "
                    + "estan medidos y los dos responden: actualizarEncargo llama a "
                    + "exigirEdicionDelEncargo antes, y el alta del encargo lo esta creando "
                    + "en ese mismo instante. Deuda declarada: darle la Captacion para que "
                    + "pueda exigirla por si mismo, como ya hace AtributosGobernados",

            "com.controllocal.service.soporte.AtributosDeEncargo#retirar",
            "misma razon y mismos llamadores que escribir",

            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#abrirEncargo",
            "ABRE el encargo: escribe su primer hito 'U' y las condiciones que se pactaron "
                    + "al abrirlo. No hay agente anterior a quien respetar porque el encargo "
                    + "nace aqui, y su agente es el actor. Este metodo aparecio al separar "
                    + "los dos mapas de exenciones: hasta entonces lo cubria, por el camino "
                    + "de sus llamadores, la exencion que `registrar` tenia declarada para "
                    + "el universo de la PROPIEDAD -- una exencion de un universo tapando un "
                    + "hueco del otro, que es justo lo que la separacion vino a impedir",

            "com.controllocal.service.impl.ProspeccionServiceImpl#captar",
            "convierte una prospeccion en encargo: tambien lo CREA. Ya tiene su autoridad "
                    + "-cargarEnProceso exige que la prospeccion sea del actor- y el hito que "
                    + "escribe es el de entrada al mercado del encargo que acaba de abrir. "
                    + "Estaba declarado solo para el universo de la PROPIEDAD y se colaba en "
                    + "este por el mapa unico");

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
     * <b>La comprobacion que caza el olvido real: la via SIGUIENTE.</b>
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

        List<String> sinGuarda = sinGuarda(escrituras, GUARDAS_DE_LA_PROPIEDAD,
                SIN_AUTORIDAD_DE_LA_PROPIEDAD);
        assertEquals(List.of(), sinGuarda,
                "estos metodos escriben un hecho de la PROPIEDAD sin preguntar quien responde "
                        + "por ella: " + sinGuarda
                        + ". Tiene que ser exigirEdicion -- no vale exigirEdicionDelEncargo, que "
                        + "responde otra pregunta. O eso, o entran en "
                        + "SIN_AUTORIDAD_DE_LA_PROPIEDAD con el motivo escrito.");
    }

    /**
     * <b>Y el historico economico responde a la autoridad del ENCARGO.</b>
     *
     * <p>Universo aparte porque la pregunta es otra: un hito {@code U}, {@code P}
     * o {@code C} no es un hecho del inmueble, es un hecho del trato que lo
     * autorizo. De aqui salio el hallazgo de este corte —
     * {@code PublicacionServiceImpl}, que escribia un {@code P} en la serie de
     * cualquier encargo del tenant— y no estaba en el inventario inicial de vias:
     * lo encontro este gate.
     */
    @Test
    @DisplayName("todo hito economico pasa por la autoridad de SU encargo")
    void ningunHitoEconomicoSinLaAutoridadDelEncargo() {
        List<JavaMethod> escrituras = escriturasDe(AutoridadDeLaPropiedadTest::escribeLaSerie);
        List<String> nombres = nombresDe(escrituras);

        assertTrue(nombres.size() >= 4,
                "el gate dejo de reconocer las escrituras de la SERIE ECONOMICA: encontro "
                        + nombres + ". Revisa REPOSITORIOS_DEL_ENCARGO antes de creerte el verde.");
        assertTrue(nombres.contains(
                        "com.controllocal.service.impl.PublicacionServiceImpl"
                                + "#registrarImportePublicado"),
                "el gate no ve la escritura de hito desde la publicacion, que es justo la que "
                        + "nadie habia inventariado. Encontro: " + nombres);

        List<String> sinGuarda = sinGuarda(escrituras, GUARDAS_DEL_ENCARGO,
                SIN_AUTORIDAD_DEL_ENCARGO);
        assertEquals(List.of(), sinGuarda,
                "estos metodos escriben un hecho de un ENCARGO sin comprobar que sea del "
                        + "actor: " + sinGuarda
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

    private List<String> sinGuarda(List<JavaMethod> escrituras, Set<String> guardas,
                                   Map<String, String> exentos) {
        return escrituras.stream()
                .filter(m -> !cubierto(m, guardas, exentos, new HashSet<>()))
                .map(AutoridadDeLaPropiedadTest::nombre)
                .distinct()
                .sorted()
                .toList();
    }

    private static String nombre(JavaMethod metodo) {
        return metodo.getOwner().getName() + "#" + metodo.getName();
    }

    /**
     * <b>Ninguna tabla del inmueble se queda sin clasificar.</b>
     *
     * <p>Este es el control que <b>no podia existir</b> en la version anterior.
     * Aquella recorria el mismo {@code Set} que declaraba —"para cada
     * repositorio que he declarado, ¿veo alguna escritura suya?"— asi que una
     * tabla <b>ausente</b> del conjunto era invisible por construccion: el gate
     * no puede echar de menos lo que no se ha nombrado. Es, exactamente, la
     * forma del fallo de 4.P que decia prevenir.
     *
     * <p>La correccion es comparar contra una fuente <b>independiente</b>: las
     * entidades JPA reales del paquete del inmueble, leidas del bytecode. Cada
     * tabla que aparezca tiene que estar clasificada en uno de los tres sitios
     * —universo PROPIEDAD, universo ENCARGO, o exclusion con motivo— y una
     * entidad nueva pone esto en rojo hasta que alguien decida cual es.
     */
    @Test
    @DisplayName("toda tabla del inmueble esta vigilada o excluida con motivo")
    void ningunaTablaDelInmuebleSinClasificar() {
        Set<String> clasificadas = new HashSet<>(TABLAS_DE_LA_PROPIEDAD.values());
        clasificadas.addAll(TABLAS_DEL_ENCARGO.values());
        clasificadas.addAll(FUERA_DEL_GOBIERNO_DE_LA_PROPIEDAD.keySet());

        List<String> entidades = CLASES.stream()
                .filter(clase -> PAQUETE_DEL_INMUEBLE.equals(clase.getPackageName()))
                .filter(clase -> clase.isAnnotatedWith(Entity.class))
                .map(clase -> clase.getAnnotationOfType(Table.class).name())
                .sorted()
                .toList();

        // CONTROL POSITIVO. Si el paquete cambiara de nombre, o las entidades
        // dejaran de llevar @Table, esta lista saldria vacia y el bucle de abajo
        // no compararia nada -- verde sin haber mirado. Es la leccion del
        // barrido de `grep -iF` del 2026-08-24: un cero sin control positivo no
        // es una medicion.
        assertTrue(entidades.size() >= 10,
                "se esperaban las entidades JPA de " + PAQUETE_DEL_INMUEBLE + " y se "
                        + "encontraron " + entidades.size() + ": " + entidades + ". Sin ellas "
                        + "este control no compara nada y su verde no significa nada.");
        assertTrue(entidades.contains("propiedad"),
                "no se ve la tabla `propiedad` entre las entidades del paquete: " + entidades);

        List<String> huerfanas = entidades.stream()
                .filter(tabla -> !clasificadas.contains(tabla))
                .distinct()
                .sorted()
                .toList();
        assertEquals(List.of(), huerfanas,
                "estas tablas del inmueble no estan ni vigiladas ni excluidas: " + huerfanas
                        + ". Decide: si guardan un hecho gobernado de la propiedad van a "
                        + "TABLAS_DE_LA_PROPIEDAD; si es del trato, a TABLAS_DEL_ENCARGO; si no "
                        + "es ninguna de las dos, a FUERA_DEL_GOBIERNO_DE_LA_PROPIEDAD con el "
                        + "motivo escrito. Lo que no puede es quedarse sin respuesta: asi quedo "
                        + "atributo_propiedad fuera del gate, y ahi vive casi todo lo gobernado.");

        // Y lo declarado no puede sobrar: una exclusion para una tabla que ya no
        // existe es ruido que hace creer que se penso en algo.
        List<String> excluidasFantasma = FUERA_DEL_GOBIERNO_DE_LA_PROPIEDAD.keySet().stream()
                .filter(tabla -> !entidades.contains(tabla))
                .sorted()
                .toList();
        assertEquals(List.of(), excluidasFantasma,
                "estas exclusiones ya no corresponden a ninguna entidad del paquete: "
                        + excluidasFantasma + ". Borralas.");
    }

    /**
     * <b>Y cada repositorio declarado se ve de verdad escribiendo.</b>
     *
     * <p>Complemento del anterior y no un duplicado: aquel comprueba que no
     * falte ninguna tabla, este que ninguna de las declaradas haya dejado de
     * reconocerse. Un repositorio renombrado, movido de paquete o con otro
     * metodo de escritura dejaria de aparecer y el gate seguiria verde
     * vigilandolo sobre el papel.
     */
    @Test
    @DisplayName("el gate ve escrituras reales en cada tabla que declara vigilar")
    void elGateVeEscriturasEnCadaTablaVigilada() {
        Map<String, String> vigiladas = new java.util.HashMap<>(TABLAS_DE_LA_PROPIEDAD);
        vigiladas.putAll(TABLAS_DEL_ENCARGO);
        for (Map.Entry<String, String> entrada : vigiladas.entrySet()) {
            boolean alguna = CLASES.stream()
                    .filter(clase -> clase.getPackageName().startsWith("com.controllocal.service"))
                    .flatMap(clase -> clase.getMethods().stream())
                    .anyMatch(m -> m.getAccessesFromSelf().stream()
                            .anyMatch(a -> entrada.getKey().equals(a.getTargetOwner().getFullName())
                                    && ESCRIBEN.contains(a.getName())));
            assertTrue(alguna,
                    "el gate no encuentra NINGUNA escritura por " + entrada.getKey() + ", que "
                            + "declara vigilar la tabla `" + entrada.getValue() + "`. O el "
                            + "repositorio cambio de nombre o de paquete, o su metodo de "
                            + "escritura ya no se llama como los de ESCRIBEN. En cualquiera de "
                            + "los dos casos esa tabla ha dejado de estar vigilada.");
        }
    }

    /**
     * <b>La decision que este gate protege tiene que VIAJAR con el codigo.</b>
     *
     * <h2>Por que es una comprobacion y no una nota</h2>
     * `docs/ai/*` esta en `.gitignore` con una lista blanca de excepciones. Es
     * deliberado —solo viaja lo que un clon limpio necesita— pero tiene un
     * filo: <b>un documento que gobierna y no viaja no gobierna nada</b>. El
     * clon no lo tiene, el auditor no lo ve, y la unica copia vive en el disco
     * de una maquina.
     *
     * <p>Esta prueba cierra ese filo por el unico camino que no depende de que
     * alguien se acuerde: <b>lee el fichero</b>. En el arbol de trabajo pasa
     * siempre; en un <b>clon limpio</b> solo pasa si el documento esta en la
     * lista blanca de `.gitignore`. Y la corrida de cierre se ejecuta tambien
     * desde un clon limpio, asi que sacar la decision de la lista pone el
     * cierre en <b>rojo</b> en vez de dejar la regla huerfana en silencio.
     *
     * <h2>Lo que NO dice</h2>
     * No dice que el documento sea correcto ni que este al dia. Dice que
     * <b>existe donde el codigo lo cita</b>. Que la regla sea la que el codigo
     * aplica lo prueban las otras comprobaciones de esta clase y las de
     * integracion.
     */
    @Test
    @DisplayName("la decision que gobierna esta autoridad viaja con el codigo")
    void laAutoridadQueGobiernaViajaConElCodigo() throws IOException {
        for (String documento : List.of(
                "decision-autoridad-de-edicion-de-la-propiedad.md",
                "decision-brox-intelligence-alcances-y-frontera.md")) {
            Path ruta = RAIZ.resolve("docs/ai").resolve(documento);
            assertTrue(Files.isRegularFile(ruta),
                    "falta " + ruta + ". O el documento se borro, o salio de la lista blanca "
                            + "de .gitignore -- y entonces un clon limpio no lo tiene y la regla "
                            + "que este gate protege se quedo sin autoridad escrita.");
            assertTrue(Files.readString(ruta, StandardCharsets.UTF_8).length() > 1000,
                    ruta + " esta practicamente vacio: existe el fichero pero no la decision.");
        }
    }

    /**
     * La raiz del repositorio, resuelta subiendo desde el directorio de trabajo
     * del modulo. Igual que hace {@code FronteraKairosTest} para leer el POM.
     */
    private static final Path RAIZ = raizDelRepositorio();

    private static Path raizDelRepositorio() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null && !Files.isDirectory(actual.resolve("docs/ai"))) {
            actual = actual.getParent();
        }
        if (actual == null) {
            throw new AssertionError(
                    "no se encontro la raiz del repositorio subiendo desde "
                            + Path.of("").toAbsolutePath() + ": sin ella esta comprobacion no "
                            + "puede mirar nada, y un verde aqui no significaria nada.");
        }
        return actual;
    }

    // ------------------------------------------------------------------
    // Predicados
    // ------------------------------------------------------------------

    /**
     * Escribe la propiedad quien guarda en una de las tablas declaradas en
     * {@link #TABLAS_DE_LA_PROPIEDAD}, o quien
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
                REPOSITORIOS_DEL_ENCARGO.contains(a.getTargetOwner().getFullName())
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
    private static boolean cubierto(JavaMethod metodo, Set<String> guardas,
                                    Map<String, String> exentos, Set<String> visitados) {
        // La exencion se consulta en el mapa DEL UNIVERSO que se esta
        // recorriendo. Con un mapa unico, una exencion escrita para la PROPIEDAD
        // eximia el mismo metodo cuando se median los hechos del ENCARGO.
        if (exentos.containsKey(nombre(metodo))) {
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
                && llamadores.stream()
                        .allMatch(otro -> cubierto(otro, guardas, exentos, visitados));
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
