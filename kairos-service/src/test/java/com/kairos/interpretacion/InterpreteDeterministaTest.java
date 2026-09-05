package com.kairos.interpretacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Traza;
import com.kairos.brox.Vocabulario;
import com.kairos.catalogo.CatalogoReal;
import com.kairos.conversacion.Accion;
import com.kairos.herramientas.Herramientas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el interprete puede sacar de una frase, y —sobre todo— lo que <b>no</b>
 * se permite sacar.
 *
 * <p>Estas pruebas son la linea base contra la que se comparara el LLM cuando
 * entre. Las que importan no son las que comprueban que entiende: son las que
 * comprueban que <b>calla</b>. Un modelo de lenguaje aprobara sin esfuerzo
 * "un depa en Miraflores"; lo que hay que exigirle es que ante "registra un
 * depa" no decida por su cuenta que se alquila.
 *
 * <h2>El catalogo es el REAL, y esa es la diferencia con la version anterior</h2>
 * Hasta aqui el doble devolvia cuatro atributos escritos a mano en este mismo
 * fichero, y entre ellos {@code area_terreno} <b>aplicado a un DEPARTAMENTO</b>,
 * cosa que el catalogo de BROX nunca ha dicho: la publica en CASA y en ALMACEN.
 * Un doble que contradice a su fuente da verde con el comportamiento
 * equivocado, y ningun cambio del Core podia ponerlo rojo. Ahora sale de
 * {@link CatalogoReal}, que son las siete respuestas de
 * {@code GET /captura/definicion} grabadas, y los distritos de la misma
 * definicion.
 */
class InterpreteDeterministaTest {

    private static final SesionBrox AGENTE = new SesionBrox("token-de-la-persona", 1L);
    private static final Traza TRAZA = new Traza(Traza.CANAL_API, "KAIROS", "determinista", "0.1.0",
            "conv-1", "turno-1", null, null, null);

    private ClienteBrox brox;
    private InterpreteDeterminista interprete;

    @BeforeEach
    void preparar() {
        brox = mock(ClienteBrox.class);
        when(brox.distritos(any(), any())).thenReturn(CatalogoReal.distritos());
        // El catalogo que se devuelve es el del tipo que se pide: los siete
        // difieren, y devolver siempre el mismo esconderia justo eso.
        when(brox.catalogoDe(any(), anyString(), any())).thenAnswer(
                invocacion -> CatalogoReal.catalogoDe(invocacion.getArgument(1)));
        interprete = new InterpreteDeterminista(brox);
    }

    // ==================================================================
    // La regla que existe para no romperse
    // ==================================================================

    @Test
    @DisplayName("la operacion NO se infiere: sin 'vende' ni 'alquila', no se emite")
    void laOperacionNoSeInfiere() {
        Interprete.Lectura lectura = leer("registra un depa en Miraflores de US$ 180 mil");

        assertEquals(Accion.REGISTRAR_PROPIEDAD, lectura.accion());
        assertEquals("DEPARTAMENTO", lectura.datos().get(Vocabulario.TIPO_PROPIEDAD));
        assertEquals("180000", lectura.datos().get(Vocabulario.IMPORTE));
        assertNull(lectura.datos().get(Vocabulario.OPERACIONES_DECLARADAS),
                "180 mil es un precio de venta plausible Y una renta imposible, pero la frase no "
                        + "dice cual: emitir una operacion aqui archivaria el importe en la serie "
                        + "equivocada y ningun CHECK podria notarlo");
    }

    @Test
    @DisplayName("la operacion se emite cuando la frase la dice, y solo entonces")
    void laOperacionSeEmiteSiSeDice() {
        assertEquals("VENTA", leer("registra un depa que se vende")
                .datos().get(Vocabulario.OPERACIONES_DECLARADAS));
        assertEquals("ALQUILER", leer("registra un local en alquiler")
                .datos().get(Vocabulario.OPERACIONES_DECLARADAS));
        assertEquals("ALQUILER", leer("registra una casa para arriendo")
                .datos().get(Vocabulario.OPERACIONES_DECLARADAS));
    }

    @Test
    @DisplayName("'disponible' o 'en cartera' NO son operaciones")
    void loQueNoDeclaraOperacionNoLaDeclara() {
        assertNull(leer("registra un depa disponible en cartera")
                        .datos().get(Vocabulario.OPERACIONES_DECLARADAS),
                "estar disponible no dice si se vende o se alquila");
    }

    // ==================================================================
    // Un numero no es un precio por ser un numero
    // ==================================================================

    @Test
    @DisplayName("un numero suelto no se toma por importe")
    void unNumeroSueltoNoEsUnPrecio() {
        Interprete.Lectura lectura = leer("registra un depa de 3 dormitorios");

        assertNull(lectura.datos().get(Vocabulario.IMPORTE),
                "sin moneda ni escala, un 3 es tres dormitorios y no tres soles");
        assertEquals("3", lectura.datos().get("dormitorios"));
    }

    @Test
    @DisplayName("el importe se ancla con moneda o con escala")
    void elImporteSeAncla() {
        assertEquals("180000", importeDe("un depa en US$ 180,000"));
        assertEquals("180000", importeDe("un depa en 180 mil dolares"));
        assertEquals("2500", importeDe("un local a S/ 2500"));
        assertEquals("1500000", importeDe("una casa de 1.5 millones de dolares"));
        assertEquals("180000", importeDe("un depa de 180 mil"));
    }

    @Test
    @DisplayName("la moneda sale del simbolo, y si no hay simbolo no se inventa")
    void laMonedaSaleDelSimbolo() {
        assertEquals("USD", leer("registra un depa en US$ 180,000")
                .datos().get(Vocabulario.MONEDA));
        assertEquals("PEN", leer("registra un local a S/ 2500")
                .datos().get(Vocabulario.MONEDA));
        assertNull(leer("registra un depa de 180 mil").datos().get(Vocabulario.MONEDA),
                "sin simbolo la moneda se pregunta; suponerla es elegir entre 180 mil soles y "
                        + "180 mil dolares, que no son el mismo negocio");
    }

    @Test
    @DisplayName("180.000 y 180,000 son ciento ochenta mil; 180.5 no")
    void separadoresDeMiles() {
        assertEquals("180000", InterpreteDeterminista.numero("180.000").toPlainString());
        assertEquals("180000", InterpreteDeterminista.numero("180,000").toPlainString());
        assertEquals("180.5", InterpreteDeterminista.numero("180.5").toPlainString());
    }

    @Test
    @DisplayName("una escala o una palabra suelta no se declara: no es una medida perdida")
    void loQueNoEsUnidadNoSeDeclara() {
        assertTrue(leer("registra un depa de 180 mil").noEntendido().isEmpty(),
                "'mil' no es clave, ni rotulo, ni unidad del catalogo: declararlo llenaria "
                        + "noEntendido de ruido y la persona dejaria de leerlo");
    }

    // ==================================================================
    // Un numero con unidad se declara, nunca se asigna (N28/N29)
    // ==================================================================

    @Test
    @DisplayName("'120 m2' encaja con las CINCO claves en m² de un DEPARTAMENTO: se declara")
    void laUnidadCompartidaNoDesambigua() {
        Interprete.Lectura lectura = leer("registra un depa de 120 m2");

        assertNull(lectura.datos().get("metraje_total"));
        assertNull(lectura.datos().get("metraje_construido"));
        assertNull(lectura.datos().get("area_terraza"));
        assertNull(lectura.datos().get("area_jardin_patio"));
        assertNull(lectura.datos().get("deposito_area"));
        assertTrue(lectura.noEntendido().contains("120 m2"),
                "cinco atributos de un departamento se miden en m²: elegir uno seria decidir por "
                        + "el usuario cual midio");
    }

    @Test
    @DisplayName("el superindice y el 2 son la misma unidad: NFD no los une, llano() si")
    void elSuperindiceYElDosSonLoMismo() {
        assertTrue(leer("registra un depa de 500 m²").noEntendido().contains("500 m2"),
                "el catalogo publica m² y la gente escribe m2; si no se pliegan, la medida se "
                        + "pierde sin dejar rastro");
        assertTrue(leer("registra un depa de 500 m2").noEntendido().contains("500 m2"));
    }

    @Test
    @DisplayName("una unidad de UN caracter tambien se ve: '8 m' era invisible para el patron")
    void laUnidadDeUnCaracterSeVe() {
        assertTrue(leer("registra un terreno de 8 m de frente").noEntendido().contains("8 m"),
                "'m' mide un caracter y el patron exigia dos: la medida se descartaba en silencio");
    }

    @Test
    @DisplayName("area_terreno NO aplica a un DEPARTAMENTO, y el doble de antes decia que si")
    void elDobleDeAntesMentiaSobreElDepartamento() {
        assertTrue(CatalogoReal.catalogoDe("DEPARTAMENTO").stream()
                        .noneMatch(p -> "area_terreno".equals(p.clave())),
                "el catalogo real no publica area_terreno para un departamento");
        assertTrue(CatalogoReal.catalogoDe("CASA").stream()
                        .anyMatch(p -> "area_terreno".equals(p.clave())),
                "si que la publica para una casa: no es que no exista, es que no aplica alli");
    }

    @Test
    @DisplayName("con el nombre del atributo si se emite")
    void elNombreDelAtributoSiDesambigua() {
        assertEquals("120", leer("registra un depa de 120 dormitorios")
                .datos().get("dormitorios"));
    }

    // ==================================================================
    // El vocabulario sale del tenant, no de una lista escrita a mano
    // ==================================================================

    @Test
    @DisplayName("las dos lecturas del interprete viajan como catalogo_de_captura")
    void lasLecturasDelInterpreteLlevanSuNombre() {
        leer("registra un depa en Miraflores de 120 m2");

        ArgumentCaptor<Traza> deLosDistritos = ArgumentCaptor.forClass(Traza.class);
        verify(brox).distritos(any(), deLosDistritos.capture());
        assertEquals(Herramientas.CATALOGO_DE_CAPTURA.nombre(),
                deLosDistritos.getValue().herramienta(),
                "los distritos salen de GET /captura/definicion, que es la misma herramienta");

        ArgumentCaptor<Traza> delCatalogo = ArgumentCaptor.forClass(Traza.class);
        verify(brox).catalogoDe(any(), eq("DEPARTAMENTO"), delCatalogo.capture());
        assertEquals(Herramientas.CATALOGO_DE_CAPTURA.nombre(),
                delCatalogo.getValue().herramienta(),
                "el interprete tambien sale por el cable, y sus llamadas iban sin nombre: el "
                        + "rastro decia quien pregunto y no que se pregunto");
    }

    @Test
    @DisplayName("el distrito sale de la definicion de captura, y uno que no existe no se emite")
    void elDistritoSaleDeLaTabla() {
        assertEquals("Miraflores", leer("registra un depa en Miraflores")
                .datos().get(Vocabulario.DISTRITO));
        assertNull(leer("registra un depa en Bellavista").datos().get(Vocabulario.DISTRITO),
                "Bellavista es del Callao y este tenant no la tiene dada de alta: preguntarla es "
                        + "mejor que fallar al guardar");
    }

    @Test
    @DisplayName("entre dos distritos que encajan gana el nombre mas largo")
    void ganaElNombreMasLargo() {
        assertEquals("San Juan de Miraflores",
                leer("registra un depa en San Juan de Miraflores")
                        .datos().get(Vocabulario.DISTRITO),
                "'Miraflores' tambien encaja en esa frase, y es otro distrito");
    }

    @Test
    @DisplayName("los siete tipos salen del vocabulario del dominio; 'depa' es lo unico coloquial")
    void losTiposSalenDelDominio() {
        assertEquals("DEPARTAMENTO", tipoDe("registra un departamento"));
        assertEquals("DEPARTAMENTO", tipoDe("registra un depa"));
        assertEquals("TERRENO", tipoDe("registra un terreno"));
        assertEquals("ALMACEN", tipoDe("registra un almacen"));
        assertEquals("LOCAL", tipoDe("registra una tienda"));
    }

    // ==================================================================
    // Que quiere hacer
    // ==================================================================

    @Test
    @DisplayName("verbo + objeto: hacen falta los dos")
    void verboYObjeto() {
        assertEquals(Accion.REGISTRAR_PROPIEDAD, leer("registra un local").accion());
        assertEquals(Accion.CONSULTAR_PROPIEDAD, leer("muestrame el local PROP-12").accion());
        assertEquals(Accion.CONSULTAR_CLIENTE, leer("busca al cliente Torres").accion());
        assertEquals(Accion.REGISTRAR_PROPIETARIO,
                leer("da de alta al propietario Torres con DNI 40506070").accion());
        assertEquals(Accion.CONTINUAR_BORRADOR, leer("sigue con lo de ayer").accion());
        assertEquals(Accion.REGISTRAR_INTERACCION,
                leer("anota la llamada de la oportunidad 7").accion());
    }

    @Test
    @DisplayName("sin verbo, o sin objeto, no se elige la accion mas comun")
    void sinLosDosNoHayAccion() {
        assertFalse(leer("registra").hayAccion());
        assertFalse(leer("un depa en Miraflores").hayAccion());
        assertFalse(leer("").hayAccion());
        assertEquals(Interprete.Lectura.SIN_TEXTO, leer("  ").motivo());
    }

    // ==================================================================
    // Matching
    // ==================================================================

    @Test
    @DisplayName("buscar coincidencias necesita de QUE se parte, y no lo elige por nadie")
    void lasCoincidenciasNecesitanOrigen() {
        Interprete.Lectura conOrigen = leer("que propiedades le encajan al cliente 47");
        assertEquals(Accion.BUSCAR_COINCIDENCIAS, conOrigen.accion());
        assertEquals("CLIENTE", conOrigen.datos().get("origen"));
        assertEquals("47", conOrigen.datos().get("idOrigen"));

        Interprete.Lectura sinOrigen = leer("muestrame las coincidencias");
        assertEquals(Accion.BUSCAR_COINCIDENCIAS, sinOrigen.accion());
        assertNull(sinOrigen.datos().get("origen"));
        assertTrue(sinOrigen.noEntendido().contains("de que cliente, encargo o prospeccion"),
                "'las coincidencias' son las de alguien; elegir a quien seria inventar un criterio");
    }

    @Test
    @DisplayName("la captacion se nombra por codigo, que es como se nombra en una conversacion")
    void elOrigenAdmiteCodigoDeCaptacion() {
        Interprete.Lectura lectura = leer("que clientes encajan con la captacion CAP-00003");
        assertEquals("CAPTACION", lectura.datos().get("origen"));
        assertEquals("CAP-00003", lectura.datos().get("idOrigen"));
    }

    @Test
    @DisplayName("explicar un match: el codigo de la coincidencia no se confunde con el del origen")
    void elCodigoDelOrigenNoEsElDeLaCoincidencia() {
        Interprete.Lectura lectura =
                leer("por que encaja PROP-12 con la captacion CAP-00003");

        assertEquals(Accion.EXPLICAR_MATCH, lectura.accion());
        assertEquals("CAPTACION", lectura.datos().get("origen"));
        assertEquals("CAP-00003", lectura.datos().get("idOrigen"));
        assertEquals("PROP-12", lectura.datos().get("codigo"),
                "el patron de codigos reconoce CAP- y PROP-: sin quitar el origen de la frase, "
                        + "KAIROS explicaria el encargo del que parte");
    }

    @Test
    @DisplayName("'explica' sin coincidencias no pide criterios de un matching que nadie busco")
    void explicarNoSecuestraLaFrase() {
        assertFalse(leer("explicame la propiedad PROP-12").hayAccion(),
                "explicar es una forma de mirar coincidencias, no una accion por si sola");
    }

    // ==================================================================
    // El foco
    // ==================================================================

    @Test
    @DisplayName("el foco se reconoce sin verbo ni objeto: es la pregunta del dia")
    void elFocoSeReconoce() {
        assertEquals(Accion.QUE_RESOLVER_HOY, leer("que resuelvo hoy").accion());
        assertEquals(Accion.QUE_RESOLVER_HOY, leer("que tengo pendiente").accion());
        assertEquals(Accion.QUE_RESOLVER_HOY, leer("cuales son mis prioridades").accion());
    }

    @Test
    @DisplayName("el foco no admite parametros: de quien es el foco no lo elige la frase")
    void elFocoNoLlevaDatos() {
        assertTrue(leer("que resuelvo hoy").datos().isEmpty(),
                "aceptar 'de Ana' seria dejar que la conversacion mirara la bandeja de otro; el "
                        + "alcance lo pone el token");
    }

    @Test
    @DisplayName("'lo que quedo pendiente de ayer' sigue siendo retomar un borrador")
    void elBorradorGanaAlFoco() {
        assertEquals(Accion.CONTINUAR_BORRADOR, leer("termina de registrar lo de ayer").accion());
    }

    // ==================================================================
    // Las otras acciones
    // ==================================================================

    @Test
    @DisplayName("un propietario sin documento declara que le falta el documento")
    void sinDocumentoSeDeclara() {
        Interprete.Lectura conDni = leer(
                "registra al propietario Torres con DNI 40506070 y telefono 987654321");
        assertEquals("40506070", conDni.datos().get("numeroDocumento"));
        assertEquals("DNI", conDni.datos().get("tipoDocumento"));
        assertEquals("987654321", conDni.datos().get("telefono"));

        Interprete.Lectura conRuc = leer(
                "registra al propietario Inversiones con RUC 20505060708");
        assertEquals("RUC", conRuc.datos().get("tipoDocumento"));
        assertEquals("J", conRuc.datos().get("tipoPersona"),
                "once digitos es un RUC, y un RUC es una persona juridica");

        assertTrue(leer("registra al propietario Torres").noEntendido().contains("documento"),
                "sin documento no hay con que descartar un duplicado");
    }

    @Test
    @DisplayName("los canales usan los codigos del dominio, no los que uno supondria")
    void losCanalesSonLosDelDominio() {
        assertEquals("L", canalDe("anota la llamada a la oportunidad 7"));
        assertEquals("W", canalDe("anota el whatsapp de la oportunidad 7"));
        assertEquals("E", canalDe("anota el correo de la oportunidad 7"));
        assertEquals("P", canalDe("anota la visita de la oportunidad 7"));
        assertEquals("R", canalDe("anota la reunion de la oportunidad 7"));
    }

    @Test
    @DisplayName("una interaccion sin entidad de la que colgar se declara incompleta")
    void interaccionSinExpediente() {
        Interprete.Lectura suelta = leer("anota la llamada");
        assertTrue(suelta.noEntendido().contains("sobre que"));
        assertNull(suelta.datos().get("contexto"));

        Interprete.Lectura conExpediente = leer("anota la llamada de la oportunidad 7");
        assertEquals("OPORTUNIDAD", conExpediente.datos().get("contexto"));
        assertEquals("7", conExpediente.datos().get("idEntidad"));
    }

    @Test
    @DisplayName("el resultado de una interaccion no se deduce del tono")
    void elResultadoNoSeDeduce() {
        assertNull(leer("anota la llamada de la oportunidad 7, fue muy bien")
                        .datos().get("resultado"),
                "'fue muy bien' no es ninguno de los seis resultados que admite una oportunidad");
    }

    // ==================================================================

    private Interprete.Lectura leer(String frase) {
        return interprete.leer(frase, AGENTE, TRAZA);
    }

    private String importeDe(String frase) {
        return leer("registra " + frase).datos().get(Vocabulario.IMPORTE);
    }

    private String tipoDe(String frase) {
        return leer(frase).datos().get(Vocabulario.TIPO_PROPIEDAD);
    }

    private String canalDe(String frase) {
        return leer(frase).datos().get("canalContacto");
    }

    /** Que el fixture esta donde se cree que esta. Si no, todo lo de arriba miente. */
    @Test
    @DisplayName("el catalogo grabado son los siete tipos y ninguno viene vacio")
    void elCatalogoGrabadoEstaCompleto() {
        for (String tipo : CatalogoReal.TIPOS) {
            List<ClienteBrox.Pregunta> catalogo = CatalogoReal.catalogoDe(tipo);
            assertFalse(catalogo.isEmpty(), "el catalogo grabado de " + tipo + " esta vacio");
            assertEquals(tipo, CatalogoReal.definicionDe(tipo).tipoPropiedad());
        }
    }
}
