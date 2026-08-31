package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.AtributoFicha;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ContratoDeEscritura;
import com.controllocal.web.dto.PropiedadUniversalDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Un dato HISTORICO se lee, se distingue con su motivo, y no se escribe.</b>
 *
 * <h2>Que defecto cierra</h2>
 * Un valor puede estar escrito y <b>ya no formar parte de lo que hoy se
 * pregunta</b>, y eso ocurre de DOS maneras que dan el mismo resultado:
 *
 * <pre>
 *   servicios_disponibles   la clave se RETIRO del catalogo (V84)
 *   area_terreno en un T    la clave sigue VIVA --se pregunta en una casa y en
 *                           un almacen-- y ya no APLICA a un terreno (V85, D-7)
 * </pre>
 *
 * <p>Por el cable las dos llegaban indistinguibles de un dato corregible. El
 * broker lo intenta, no encuentra el campo en el editor, y nada se lo explica.
 * Y una senal que dijera solo «retirada» describiria la primera y
 * <b>mentiria</b> sobre la segunda.
 *
 * <p>Por eso lo que se publica es la pregunta generica que
 * {@link ContratoDeEscritura} responde --si la clave pertenece HOY al contrato
 * de escritura de esta propiedad-- en forma de {@code estadoDato},
 * {@code editable} y {@code motivoNoEditable}.
 *
 * <h2>Y la puerta no confia en el cliente</h2>
 * La senal existe para no ofrecer lo imposible. El {@code PUT} <b>vuelve a
 * preguntarle al catalogo</b>, y rechaza la clave que no pertenece al contrato
 * <b>aunque el valor enviado sea identico al conservado</b>: la pregunta es si
 * la clave pertenece, no si el valor cambia. Es la decision del titular sobre
 * {@code N33} --opcion B--, y evita la excepcion «una clave no aplicable si
 * puede escribirse si coincide con algo historico», que abriria una segunda
 * puerta a lo que D-7 cerro.
 *
 * <h2>Por que contra PostgreSQL real</h2>
 * Lo que decide todo esto son filas: {@code catalogo_atributo.activo} y
 * {@code catalogo_atributo_tipo}. No las lee javac, ni Hibernate, ni ArchUnit.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClaveRetiradaEnLaFichaIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired MotorDeCaptura captura;

    // ==================================================================
    // 1, 2 y 6 · El valor sobrevive, llega marcado con su motivo, y la MISMA
    //            senal cubre las dos formas de salir del contrato.
    // ==================================================================

    /**
     * Caso A: la clave se <b>retira del catalogo</b>. Deja de preguntarse en
     * todos los tipos a la vez.
     */
    @Test
    @DisplayName("clave retirada del catalogo: el valor sigue visible, marcado HISTORICO y con motivo")
    void unaClaveRetiradaSigueVisibleYLlegaMarcada() {
        long org = actor().idOrganizacion();
        String clave = sembrarClaveDeTenant(org);
        try {
            long id = registrarTerreno();
            editar(id, new ValorAtributo(clave, "lo que se sabia"));

            AtributoFicha antes = fichaDe(id, clave);
            assertNotNull(antes, "el caso no mediria nada si el valor no se leyera antes");
            assertEquals(ContratoDeEscritura.VIGENTE, antes.estadoDato(),
                    "mientras la clave esta activa y aplica, el dato es VIGENTE: si ya fuera "
                            + "historico, la comprobacion de despues no distinguiria nada");
            assertTrue(antes.editable(), "y editable");
            assertNull(antes.motivoNoEditable(),
                    "un dato editable no lleva motivo: no hay nada que explicar");

            retirar(clave, org);

            AtributoFicha despues = fichaDe(id, clave);
            assertNotNull(despues,
                    "retirar la pregunta no puede borrar el valor de la vista: el dato acumula");
            assertEquals("lo que se sabia", despues.valor(), "y llega intacto");
            assertEquals("Clave de prueba", despues.rotulo(),
                    "y con su nombre, no como clave desnuda (esto ya lo garantizaba V84)");
            assertEquals(ContratoDeEscritura.HISTORICO, despues.estadoDato(),
                    "sin esta senal el dato historico y el corregible llegan iguales");
            assertFalse(despues.editable(), "y no editable, que es lo que el PUT va a contestar");
            assertNotNull(despues.motivoNoEditable(),
                    "marcar sin explicar deja al broker sin saber por que no puede corregirlo");
            assertTrue(despues.motivoNoEditable().contains("retiro del catalogo"),
                    "y el motivo tiene que decir CUAL de las dos causas fue: " + despues);
        } finally {
            retirar(clave, org);
        }
    }

    /**
     * Caso B: la clave <b>sigue activa</b> y deja de aplicar a este tipo. Es
     * {@code area_terreno} sobre un TERRENO desde D-7, y es el caso que una
     * bandera «retirada» describiria mal: la clave no se retiro de ningun sitio
     * --se sigue preguntando en una casa y en un almacen--.
     */
    @Test
    @DisplayName("clave activa que ya no aplica al tipo: el mismo estado HISTORICO, con otro motivo")
    void unaClaveQueYaNoAplicaAlTipoLlegaIgualDeMarcada() {
        String clave = claveActivaQueNoAplicaATerreno();
        long id = registrarTerreno();
        sembrarValorHuerfano(id, clave, "777");

        AtributoFicha ficha = fichaDe(id, clave);
        assertNotNull(ficha,
                "el valor conservado tiene que seguir leyendose: retirar la aplicabilidad "
                        + "tampoco retira el dato");
        assertEquals(ContratoDeEscritura.HISTORICO, ficha.estadoDato(),
                "una clave que ya no aplica a este tipo produce un dato historico igual que "
                        + "una retirada: para quien lee la ficha son la misma cosa");
        assertFalse(ficha.editable(), "y tampoco se puede corregir");
        assertNotNull(ficha.motivoNoEditable(), "con su motivo");
        assertFalse(ficha.motivoNoEditable().contains("retiro del catalogo"),
                "pero el motivo NO puede decir que se retiro: `" + clave + "` sigue activa y se "
                        + "sigue preguntando en otros tipos. Decir lo contrario es mentir: "
                        + ficha.motivoNoEditable());

        // Y sigue VIGENTE donde si aplica: la senal es por PROPIEDAD, no por
        // clave. Si fuera por clave, marcarla aqui la marcaria en todas.
        long enCasa = registrarCasa();
        editar(enCasa, new ValorAtributo(clave, "300"));
        AtributoFicha viva = fichaDe(enCasa, clave);
        assertNotNull(viva, "la misma clave sobre una casa");
        assertEquals(ContratoDeEscritura.VIGENTE, viva.estadoDato(),
                "la misma clave es VIGENTE donde sigue aplicando. Si llegara historica aqui, "
                        + "la senal seria de la clave y no del dato, y apagaria la edicion de "
                        + "un campo perfectamente vivo.");
        assertTrue(viva.editable());
    }

    // ==================================================================
    // 3 · No aparece entre las entradas modificables
    // ==================================================================

    @Test
    @DisplayName("/captura/definicion no ofrece ni la clave retirada ni la que dejo de aplicar")
    void ningunaDeLasDosSeOfreceParaEditar() {
        long org = actor().idOrganizacion();
        String clave = sembrarClaveDeTenant(org);
        try {
            assertTrue(seOfreceEnTerreno(clave),
                    "mientras esta activa se pregunta: si no, el caso mediria la retirada de "
                            + "algo que nunca se ofrecio");
            retirar(clave, org);
            assertFalse(seOfreceEnTerreno(clave),
                    "la clave retirada sigue en el guion: el editor la ofreceria y aceptaria "
                            + "valor nuevo sobre una pregunta cerrada");
        } finally {
            retirar(clave, org);
        }

        String noAplica = claveActivaQueNoAplicaATerreno();
        assertFalse(seOfreceEnTerreno(noAplica),
                "`" + noAplica + "` se ofrece para un TERRENO y ya no aplica ahi (D-7)");
        assertTrue(seOfreceEnCasa(noAplica),
                "y tiene que seguir ofreciendose donde SI aplica: si no, el caso estaria "
                        + "midiendo una clave muerta en vez de una aplicabilidad retirada");
    }

    // ==================================================================
    // 4 · Sin valor historico no hay fila fantasma
    // ==================================================================

    @Test
    @DisplayName("una propiedad sin valor historico no recibe fila fantasma, por ninguna de las dos causas")
    void sinValorEscritoNoApareceNingunaFila() {
        long org = actor().idOrganizacion();
        String clave = sembrarClaveDeTenant(org);
        try {
            long conValor = registrarTerreno();
            editar(conValor, new ValorAtributo(clave, "si se sabia"));
            long sinValor = registrarTerreno();

            retirar(clave, org);

            assertNotNull(fichaDe(conValor, clave),
                    "la que SI tenia valor tiene que seguir leyendolo: si no, este caso no "
                            + "distingue «no hay fila» de «la ficha no lee historicos»");
            assertNull(fichaDe(sinValor, clave),
                    "la propiedad que nunca respondio esa pregunta recibio una fila historica. "
                            + "Un hueco no es historia: declararlo asi inventa un dato que "
                            + "nadie escribio.");

            // Y por la otra causa tampoco.
            assertNull(fichaDe(sinValor, claveActivaQueNoAplicaATerreno()),
                    "un terreno sin valor conservado tampoco recibe fila por una clave que "
                            + "dejo de aplicarle");
        } finally {
            retirar(clave, org);
        }
    }

    // ==================================================================
    // 5 · El PUT rechaza, con el valor identico y al cambiarlo
    // ==================================================================

    /**
     * <b>La decision del titular sobre {@code N33}: opcion B.</b>
     *
     * <p>La pregunta que el Core se hace <b>no</b> es «¿coincide con lo que ya
     * habia?», sino «¿esta clave pertenece hoy al contrato de escritura de esta
     * propiedad?». Si no pertenece, rechazo -- tambien cuando el valor enviado
     * es <b>identico</b> al conservado.
     *
     * <p>No es lo observado: es lo decidido. Tolerar el reenvio identico
     * abriria una excepcion --«una clave no aplicable si puede escribirse si
     * coincide con algo historico»-- y esa excepcion es una segunda puerta a lo
     * que D-7 cerro; ademas obligaria a la puerta a comparar valores para
     * decidir competencias, que son dos preguntas distintas.
     */
    @Test
    @DisplayName("N33 opcion B: el PUT rechaza la clave fuera del contrato aunque el valor sea identico")
    void elPutRechazaAunqueElValorSeaIdentico() {
        String clave = claveActivaQueNoAplicaATerreno();
        long id = registrarTerreno();
        sembrarValorHuerfano(id, clave, "777");

        AtributoFicha leido = fichaDe(id, clave);
        assertNotNull(leido, "el caso necesita el valor conservado en la ficha");
        assertEquals("777", leido.valor());

        // IDENTICO al conservado, tal como lo devolvio la ficha.
        ReglaNegocioException identico = assertThrows(ReglaNegocioException.class,
                () -> editar(id, new ValorAtributo(clave, leido.valor())),
                "reenviar el valor conservado se acepto. Eso es la excepcion que D-7 no "
                        + "tiene: la clave no pertenece al contrato de este terreno, y que el "
                        + "valor coincida no la devuelve a el.");
        assertTrue(identico.getMessage().contains("no aplica"),
                "el rechazo tiene que explicar que la clave no aplica, no fallar de cualquier "
                        + "manera: " + identico.getMessage());

        // Y cambiandolo, por si acaso la puerta solo mirara la igualdad.
        assertThrows(ReglaNegocioException.class,
                () -> editar(id, new ValorAtributo(clave, "888")),
                "cambiar el valor de una clave fuera del contrato tambien se rechaza");

        assertEquals("777", fichaDe(id, clave).valor(),
                "y el conservado no se pierde en ninguno de los dos intentos: `editar` es "
                        + "transaccional, asi que no queda a medias");
    }

    @Test
    @DisplayName("el PUT rechaza tambien la clave retirada, y lo dice sin acusar de clave inexistente")
    void elPutRechazaLaClaveRetiradaConSuRazon() {
        long org = actor().idOrganizacion();
        String clave = sembrarClaveDeTenant(org);
        try {
            long id = registrarTerreno();
            editar(id, new ValorAtributo(clave, "lo que se sabia"));
            retirar(clave, org);

            ReglaNegocioException rechazo = assertThrows(ReglaNegocioException.class,
                    () -> editar(id, new ValorAtributo(clave, "lo que se sabia")),
                    "reenviar el valor de una clave retirada se acepto: la pregunta esta "
                            + "cerrada y el dato volveria a escribirse");
            assertTrue(rechazo.getMessage().contains("se retiro del catalogo"),
                    "el error decia «no esta en el catalogo», y de una clave retirada eso es "
                            + "FALSO -- esta, con su rotulo y sus valores --, asi que mandaba a "
                            + "buscar una clave mal escrita: " + rechazo.getMessage());

            assertEquals("lo que se sabia", fichaDe(id, clave).valor(),
                    "y el conservado sigue donde estaba");
        } finally {
            retirar(clave, org);
        }
    }

    // ==================================================================
    // 7 · Nada hardcodeado, ni en el backend ni en el SPA
    // ==================================================================

    /**
     * <b>El mecanismo no nombra NINGUNA clave del catalogo.</b>
     *
     * <p>Se barre contra el catalogo ENTERO y no contra
     * {@code servicios_disponibles} y {@code area_terreno}: una prueba escrita
     * sobre las dos claves de hoy no distingue un mecanismo de un {@code if}
     * con el nombre siguiente dentro.
     *
     * <p>Y se barre sobre la pieza que DECIDE --{@link ContratoDeEscritura}--,
     * no sobre el repositorio entero. Medido: barrer todo produce falsos
     * positivos de bulto, porque varias claves del catalogo son palabras
     * corrientes ({@code piso} dentro de «episodios», {@code vista} dentro de
     * «de la vista») y {@code ambientes} es ademas un campo del cable heredado
     * de locales. Un gate que grita por «de la vista» acaba aflojado, y
     * entonces deja de vigilar lo unico que importaba. Lo que decide el SPA lo
     * cubre {@link #elSpaNoDecidePorLaClaveNiLlevaSuPropiaLista()}, que mira la
     * forma de la decision en vez de las palabras.
     *
     * <p>Las lineas de comentario se descartan: el javadoc que explica por que
     * se retiro una clave tiene que poder nombrarla, y prohibir la palabra
     * obligaria a contar la decision sin decirla. Un comentario al final de una
     * linea de codigo SI cuenta.
     */
    @Test
    @DisplayName("la pieza que decide la senal no nombra ninguna clave del catalogo")
    void elMecanismoNoConoceNingunaClavePorSuNombre() throws IOException {
        List<String> catalogo = jdbc.queryForList("""
                select clave from catalogo_atributo
                 where organizacion_id is null and del_sistema
                 order by clave
                """, String.class);
        assertTrue(catalogo.size() > 50,
                "el barrido mira " + catalogo.size() + " claves del catalogo: son demasiado "
                        + "pocas para ser el catalogo del sistema, asi que su cero no valdria");
        assertTrue(catalogo.contains("servicios_disponibles"),
                "el catalogo tiene que incluir la clave RETIRADA: el barrido no filtra por "
                        + "`activo` a proposito, porque justamente las retiradas son las que "
                        + "tientan a escribirse a mano");

        Path raiz = raizDelRepositorio();

        // CONTROL POSITIVO: el barrido SI caza un nombre cuando esta. Se prueba
        // sobre las migraciones, que siembran las claves por su nombre. Sin
        // esto, un `enCodigo` roto dejaria el cero de abajo sin significado.
        List<Path> migraciones = fuentes(raiz.resolve(
                "backend-spring/controllocal-app/src/main/resources/db/migration"), ".sql");
        assertFalse(migraciones.isEmpty(), "no se encontraron migraciones");
        assertTrue(migraciones.stream().anyMatch(f -> enCodigo(leer(f), catalogo)),
                "el barrido no encuentra ninguna clave en las migraciones, donde estan todas: "
                        + "el patron no caza y su cero no probaria nada");

        Path decision = raiz.resolve("backend-spring/controllocal-service/src/main/java/"
                + "com/controllocal/service/soporte/ContratoDeEscritura.java");
        assertTrue(Files.isRegularFile(decision),
                "no se encontro " + decision + ": la pieza que decide se movio y este gate dejo "
                        + "de vigilarla");

        List<String> nombradas = catalogo.stream()
                .filter(clave -> enCodigo(leer(decision), List.of(clave)))
                .toList();

        assertEquals(List.of(), nombradas, """
                La pieza que decide si un dato pertenece al contrato de escritura nombra una
                clave del catalogo: %s. Funcionaria hoy y dejaria muda la siguiente. Si una
                clave pertenece al contrato lo dicen `catalogo_atributo.activo` y
                `catalogo_atributo_tipo`, no una lista escrita a mano.
                """.formatted(nombradas));
    }

    /**
     * <b>Y el SPA no decide por la clave ni lleva su propia lista.</b>
     *
     * <p>Aqui se mira la FORMA de la decision y no las palabras, porque varias
     * claves del catalogo son palabras corrientes en castellano y una plantilla
     * que diga «de la vista» no esta cableando {@code vista}.
     */
    @Test
    @DisplayName("el SPA no decide por la clave ni mantiene ninguna lista de campos no editables")
    void elSpaNoDecidePorLaClaveNiLlevaSuPropiaLista() throws IOException {
        Path raiz = raizDelRepositorio();
        Path componente = raiz.resolve(
                "frontend-angular/src/app/features/propiedad-detail/propiedad-detail.ts");
        Path plantilla = raiz.resolve(
                "frontend-angular/src/app/features/propiedad-detail/propiedad-detail.html");
        for (Path pieza : List.of(componente, plantilla)) {
            assertTrue(Files.isRegularFile(pieza), "no se encontro " + pieza);
        }

        assertTrue(leer(componente).contains("estadoDato === 'HISTORICO'"),
                "la ficha ya no decide con `estadoDato`. Si lo dedujera de otra cosa seria una "
                        + "segunda deduccion, y la de KAIROS se separaria de esta.");

        // Ninguna de las dos compara la CLAVE para decidir nada. Es la forma que
        // tendria un cableado, y la unica que no se confunde con una palabra.
        for (Path pieza : List.of(componente, plantilla)) {
            String texto = leer(pieza);
            // Solo COMPARACIONES. `track atributo.clave` es un uso legitimo --
            // identifica la fila para el @for -- y prohibirlo seria un rojo por
            // usar la clave como identidad, que es justo para lo que sirve.
            for (String forma : List.of("clave ===", "clave ==", "clave !==",
                    "clave !=", "includes(atributo.clave", "startsWith(")) {
                assertFalse(enCodigo(texto, List.of(forma)),
                        pieza.getFileName() + " compara la clave del atributo (`" + forma
                                + "`). Decidir por el nombre es la lista de campos escrita a "
                                + "mano, disfrazada.");
            }
            assertFalse(enCodigo(texto, List.of("noEditables", "clavesHistoricas",
                            "CLAVES_RETIRADAS", "NO_EDITABLES")),
                    pieza.getFileName() + " declara su propia lista de claves. Con dos "
                            + "consumidores serian dos listas, y la segunda se olvida.");
        }
    }

    // ==================================================================
    // 8 · Mirar no deja rastro
    // ==================================================================

    @Test
    @DisplayName("consultar la ficha con un dato historico no escribe ningun rastro de edicion")
    void visualizarUnHistoricoNoDejaRastro() {
        String clave = claveActivaQueNoAplicaATerreno();
        long id = registrarTerreno();
        sembrarValorHuerfano(id, clave, "777");

        int antes = rastrosDe(id);
        assertTrue(antes > 0,
                "la propiedad tiene que tener algun rastro del alta: si no, este caso no "
                        + "distingue «no anadio» de «no hay tabla que mirar»");

        assertNotNull(fichaDe(id, clave), "y el historico se lee");
        assertNotNull(fichaDe(id, clave), "dos veces, por si la primera lectura fuera la que crea");

        assertEquals(antes, rastrosDe(id),
                "leer una ficha escribio en el linaje. Consultar no es un acto sobre el dato: "
                        + "un rastro por cada visita convierte la procedencia en un registro de "
                        + "visitas y esconde las ediciones de verdad.");
    }

    // ==================================================================
    // 9 · Una clave vigente se comporta igual que antes
    // ==================================================================

    @Test
    @DisplayName("una clave activa y aplicable se comporta exactamente igual que antes")
    void unaClaveVigenteNoCambiaDeComportamiento() {
        long id = registrarTerreno();
        editar(id, new ValorAtributo("via_de_acceso", "Av. Los Alamos"));

        AtributoFicha vigente = fichaDe(id, "via_de_acceso");
        assertNotNull(vigente, "el caso necesita una clave vigente con valor escrito");
        assertEquals(ContratoDeEscritura.VIGENTE, vigente.estadoDato(),
                "una clave activa y aplicable llego marcada como historica: la ficha diria que "
                        + "un dato corregible es historia, y nadie volveria a corregirlo");
        assertTrue(vigente.editable());
        assertNull(vigente.motivoNoEditable());
        assertTrue(seOfreceEnTerreno("via_de_acceso"), "y el editor la sigue ofreciendo");

        editar(id, new ValorAtributo("via_de_acceso", "Jr. Los Cedros"));
        assertEquals("Jr. Los Cedros", fichaDe(id, "via_de_acceso").valor(),
                "y se corrige, que es lo que la distingue de un historico");
    }

    // ==================================================================
    // 10 · La senal llega al cable, igual para los dos consumidores
    // ==================================================================

    /**
     * Se comprueba sobre el DTO que se serializa, porque el hueco puede existir
     * en {@code AtributoFicha} y no viajar: bastaria con que
     * {@code AtributoResponse.desde} no lo copiara, y el cliente leeria
     * {@code undefined} sin que ninguna prueba del servicio lo notara.
     *
     * <p>Y es UNA respuesta: BROX Web y KAIROS leen la misma ficha por el mismo
     * recurso, asi que ninguno puede recibir mas permiso que el otro -- no hay
     * dos representaciones donde uno de los dos pueda quedarse sin la senal.
     */
    @Test
    @DisplayName("la senal viaja en el DTO, y es la misma respuesta para BROX Web y para KAIROS")
    void laSenalLlegaAlCableUnaSolaVez() {
        String clave = claveActivaQueNoAplicaATerreno();
        long id = registrarTerreno();
        sembrarValorHuerfano(id, clave, "777");

        PropiedadUniversalDtos.PropiedadResponse respuesta =
                PropiedadUniversalDtos.PropiedadResponse.desde(propiedades.consultar(id, actor()));

        PropiedadUniversalDtos.AtributoResponse historico = respuesta.atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .findFirst().orElse(null);
        assertNotNull(historico, "el atributo historico no llego al DTO");
        assertEquals(ContratoDeEscritura.HISTORICO, historico.estadoDato(),
                "llego al cable SIN el estado: el servicio lo sabe y el cliente no");
        assertFalse(historico.editable(), "y sin decir que no se edita");
        assertNotNull(historico.motivoNoEditable(), "y sin el motivo, que llega redactado");

        PropiedadUniversalDtos.AtributoResponse vigente = respuesta.atributos().stream()
                .filter(a -> "metraje_total".equals(a.clave()))
                .findFirst().orElse(null);
        assertNotNull(vigente, "el metraje tiene que estar: es ALT en los siete tipos");
        assertEquals(ContratoDeEscritura.VIGENTE, vigente.estadoDato());
        assertTrue(vigente.editable());
        assertNull(vigente.motivoNoEditable(),
                "un atributo editable no lleva motivo, y por `NON_NULL` ni siquiera viaja");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * <b>Una clave del sistema, activa, que NO aplica a un terreno.</b>
     *
     * <p>Se busca en el catalogo en vez de escribir {@code area_terreno}: lo
     * que se prueba es el mecanismo, y una prueba con el nombre dentro no
     * distinguiria un mecanismo de un {@code if}. Se exige que exista alguna --
     * si el catalogo dejara de tener claves de esta forma, este caso dejaria de
     * medir la mitad que importa y hay que saberlo.
     */
    private String claveActivaQueNoAplicaATerreno() {
        List<String> candidatas = jdbc.queryForList("""
                select c.clave
                  from catalogo_atributo c
                 where c.activo and c.del_sistema and c.sujeto = 'PROPIEDAD'
                   and c.destino = 'ATRIBUTO'
                   and c.tipo_dato in ('ENTERO', 'DECIMAL')
                   and exists (select 1 from catalogo_atributo_tipo t
                                where t.id_catalogo_atributo = c.id_catalogo_atributo
                                  and t.tipo_propiedad = 'C')
                   and not exists (select 1 from catalogo_atributo_tipo t
                                    where t.id_catalogo_atributo = c.id_catalogo_atributo
                                      and t.tipo_propiedad = 'T')
                 order by c.clave
                """, String.class);
        assertFalse(candidatas.isEmpty(),
                "no hay ninguna clave activa que aplique a una CASA y no a un TERRENO. Este "
                        + "caso existe para medir esa forma exacta -- la de `area_terreno` tras "
                        + "D-7 -- y sin ella no mide nada.");
        return candidatas.get(0);
    }

    /**
     * Escribe un valor que la puerta de escritura ya no aceptaria: es el
     * LEGADO, lo que quedo escrito cuando la clave si aplicaba. Se hace por
     * SQL a proposito -- por el caso de uso es imposible, y ese es justamente
     * el punto de la prueba.
     */
    /**
     * <b>Lo sembrado por SQL, para poder retirarlo.</b>
     *
     * <p>Sin esto la prueba deja residuo: un valor sin linaje que ninguna puerta
     * habria aceptado, y que el gate del modelo universal caza —con razón— en
     * «4P despues del cutover ningun hecho del inmueble sin linaje». Cuatro
     * filas por corrida, que en {@code controllocal_repositorios} se acumulan
     * hasta poner el gate en rojo. Lo destapó el cierre de P0 con <b>36</b>
     * filas de nueve corridas del mismo dia.
     *
     * <p>Es la misma disciplina que ya aplicaba
     * {@code SueloYParametrosUrbanisticosIntegrationTest} —«y se deja la base
     * como se encontro»—: <b>quien se salta la puerta a proposito, limpia</b>.
     */
    private final List<Object[]> sembradosPorSql = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void retirarLoSembradoPorSql() {
        for (Object[] fila : sembradosPorSql) {
            jdbc.update("delete from atributo_propiedad where id_propiedad = ? and clave = ?",
                    fila[0], fila[1]);
        }
        sembradosPorSql.clear();
    }

    private void sembrarValorHuerfano(long idPropiedad, String clave, String valor) {
        sembradosPorSql.add(new Object[] {idPropiedad, clave});
        // Se reabre la aplicabilidad, se escribe y se vuelve a cerrar, todo en
        // UNA sentencia: el trigger `exigir_atributo_gobernado` rechaza un valor
        // de una clave que no aplique al tipo, que es exactamente la puerta que
        // este caso va a probar despues. Es la misma tecnica que usa el gate de
        // 5B para reconstruir su huerfano, y no la debilita: la fila queda como
        // quedo el legado, escrita cuando la clave si aplicaba.
        jdbc.execute("""
                do $huerfano$
                begin
                    insert into catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
                                                        requerido, exigencia)
                    select c.id_catalogo_atributo, 'T', false, 'OPC' from catalogo_atributo c
                     where c.clave = '%2$s' and c.organizacion_id is null;

                    insert into atributo_propiedad (organizacion_id, id_propiedad, clave,
                                                    valor_numero)
                    select organizacion_id, id_propiedad, '%2$s', %3$s
                      from propiedad where id_propiedad = %1$d;

                    delete from catalogo_atributo_tipo t using catalogo_atributo c
                     where c.id_catalogo_atributo = t.id_catalogo_atributo
                       and c.clave = '%2$s' and c.organizacion_id is null
                       and t.tipo_propiedad = 'T';
                end $huerfano$;
                """.formatted(idPropiedad, clave, valor));
    }

    /** Una clave del tenant, activa y aplicable a los siete tipos. */
    private String sembrarClaveDeTenant(long org) {
        String clave = "zz_historica_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato,
                                               aplica_todos, del_sistema, orden)
                values (?, ?, 'Clave de prueba', 'TEXTO', false, false, 995)
                """, org, clave);
        jdbc.update("""
                insert into catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
                                                    requerido, exigencia)
                select c.id_catalogo_atributo, t.tipo, false, 'OPC'
                  from catalogo_atributo c
                  cross join tipos_de_propiedad() as t(tipo)
                 where c.clave = ? and c.organizacion_id = ?
                """, clave, org);
        return clave;
    }

    private void retirar(String clave, long org) {
        jdbc.update("update catalogo_atributo set activo = false "
                + "where clave = ? and organizacion_id = ?", clave, org);
    }

    private int rastrosDe(long idPropiedad) {
        return jdbc.queryForObject("""
                select count(*) from rastro_valor_gobernado
                 where sujeto = 'PROPIEDAD' and id_agregado = ?
                """, Integer.class, idPropiedad);
    }

    private boolean seOfreceEnTerreno(String clave) {
        return seOfrece("TERRENO", clave);
    }

    private boolean seOfreceEnCasa(String clave) {
        return seOfrece("CASA", clave);
    }

    private boolean seOfrece(String tipo, String clave) {
        return captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, tipo, "VENTA", actor())
                .todas().stream().anyMatch(p -> clave.equals(p.clave()));
    }

    private AtributoFicha fichaDe(long id, String clave) {
        return propiedades.consultar(id, actor()).atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .findFirst().orElse(null);
    }

    private void editar(long id, ValorAtributo valor) {
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                List.of(valor), null, null), actor());
    }

    private long registrarTerreno() {
        return registrar("TERRENO",
                List.of(new ValorAtributo("metraje_total", "500"),
                        new ValorAtributo("zonificacion", "RDM"),
                        new ValorAtributo("condicion_terreno", "URBANO_HABILITADO")));
    }

    private long registrarCasa() {
        return registrar("CASA",
                List.of(new ValorAtributo("metraje_total", "180"),
                        new ValorAtributo("dormitorios", "3")));
    }

    private long registrar(String tipo, List<ValorAtributo> atributos) {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, tipo, null,
                "Caso D0-3",
                new Ubicacion("Av. Historica " + UUID.randomUUID(), "Lurin",
                        null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                atributos,
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("300000"), "USD",
                        null, null, null, null, null, null, null)),
                null), actor).idPropiedad();
    }

    private Actor actor() {
        var fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }

    // ==================================================================
    // Barrido
    // ==================================================================

    /**
     * ¿Aparece alguno de estos nombres en el CODIGO? Se descartan las lineas de
     * comentario, y no por comodidad: el javadoc que explica por que se retiro
     * una clave tiene que poder nombrarla. Un comentario al final de una linea
     * de codigo SI cuenta -- el barrido prefiere un rojo de mas a un verde que
     * no ha mirado.
     */
    private static boolean enCodigo(String fichero, List<String> nombres) {
        return fichero.lines()
                .map(String::strip)
                .filter(linea -> !linea.startsWith("*") && !linea.startsWith("/*")
                        && !linea.startsWith("//") && !linea.startsWith("<!--"))
                .anyMatch(linea -> nombres.stream().anyMatch(linea::contains));
    }

    private static String ruta(Path fichero) {
        return fichero.toString().replace('\\', '/');
    }

    private static List<Path> fuentes(Path raiz, String extension) throws IOException {
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            return ficheros
                    .filter(f -> f.toString().endsWith(extension))
                    .filter(f -> !ruta(f).contains("/target/"))
                    .filter(f -> !ruta(f).contains("/node_modules/"))
                    .toList();
        }
    }

    private static String leer(Path fichero) {
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + fichero, e);
        }
    }

    private static Path raizDelRepositorio() {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            if (Files.isDirectory(directorio.resolve("frontend-angular/src"))
                    && Files.isDirectory(directorio.resolve("backend-spring"))) {
                return directorio;
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro la raiz del repositorio subiendo desde "
                + Path.of("").toAbsolutePath() + ". Sin el SPA este barrido no vigila nada.");
    }
}
