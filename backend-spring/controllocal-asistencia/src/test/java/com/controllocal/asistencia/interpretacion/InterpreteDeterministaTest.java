package com.controllocal.asistencia.interpretacion;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.asistencia.brox.Vocabulario;
import com.controllocal.asistencia.catalogo.CatalogoReal;
import com.controllocal.asistencia.catalogo.DefinicionComunicacionDePrueba;
import com.controllocal.asistencia.conversacion.Accion;
import com.controllocal.asistencia.herramientas.Herramientas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import com.controllocal.asistencia.brox.ErrorBrox;
import com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * {@link CatalogoReal}, que son las ocho respuestas de
 * {@code GET /captura/definicion} grabadas, y los distritos de la misma
 * definicion.
 */
class InterpreteDeterministaTest {
    @Test void materialExplicitoUsaElPuertoCoreSinElegirProveedorNiConfirmarPorLaPersona(){
        var lectura=leer("solicitar material ia {\"operacion\":\"11111111-1111-4111-8111-111111111111\",\"accion\":\"MEJORAR_FOTO\",\"recurso\":{\"tipo\":\"PROPIEDAD\",\"referencia\":\"7\"},\"versionOrigen\":\"F1\",\"fotoOriginal\":\"22222222-2222-4222-8222-222222222222\"}");
        assertEquals(Accion.SOLICITAR_MATERIAL_IA,lectura.accion());
        assertFalse(lectura.datos().get("datosComerciales").contains("proveedor"));
        assertFalse(lectura.datos().get("datosComerciales").contains("decision"));
    }

    private static final SesionBrox AGENTE = new SesionBrox(1, 1, 1, "AGENTE", "sesion-controlada", Long.MAX_VALUE);
    private static final Traza TRAZA = new Traza(Traza.CANAL_API, "KAIROS", "determinista", "0.1.0",
            "conv-1", "turno-1", null, null, null);

    private ClienteBrox brox;
    private InterpreteDeterminista interprete;

    @BeforeEach
    void preparar() {
        brox = mock(ClienteBrox.class);
        when(brox.distritos(any(), any())).thenReturn(CatalogoReal.distritos());
        // El catalogo que se devuelve es el del tipo que se pide: los ocho
        // difieren, y devolver siempre el mismo esconderia justo eso.
        when(brox.catalogoDe(any(), anyString(), any())).thenAnswer(
                invocacion -> CatalogoReal.catalogoDe(invocacion.getArgument(1)));
        when(brox.definicionDeInteracciones(any(),any())).thenReturn(DefinicionComunicacionDePrueba.opciones());
        interprete = new InterpreteDeterminista(brox);
    }

    // ==================================================================
    // La regla que existe para no romperse
    // ==================================================================

    @Test
    void confirmarReconoceUnaSolicitudDeRevisionSinDatosNiAutoridad() {
        var lectura = leer("confirmar");
        assertEquals(Accion.PREPARAR_CAPTURA, lectura.accion());
        assertTrue(lectura.datos().isEmpty());
        assertEquals(Accion.PREPARAR_CAPTURA, leer("Confirma el borrador.").accion());
        assertFalse(leer("no confirmar").hayAccion());
        assertFalse(leer("confirmado por el propietario").hayAccion());
    }

    @Test
    @DisplayName("la operacion NO se infiere: sin 'vende' ni 'alquila', no se emite")
    void laOperacionNoSeInfiere() {
        Interprete.Lectura lectura = leer("registra un depa en Miraflores de US$ 180 mil");

        assertEquals(Accion.REGISTRAR_PROPIEDAD, lectura.accion());
        assertEquals("DEPARTAMENTO", lectura.datos().get(Vocabulario.TIPO_PROPIEDAD));
        assertNull(lectura.datos().get(Vocabulario.IMPORTE),
                "un importe sin encargo no viaja como dato global");
        assertTrue(lectura.noEntendido().contains("importe sin operacion"));
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
        assertEquals("180000", importeDe("un depa en venta US$ 180,000"));
        assertEquals("180000", importeDe("un depa en venta 180 mil dolares"));
        assertEquals("2500", importeDe("un local en alquiler a S/ 2500"));
        assertEquals("1500000", importeDe("una casa en venta de 1.5 millones de dolares"));
        assertEquals("180000", importeDe("un depa en venta de 180 mil"));
    }

    @Test
    @DisplayName("la moneda sale del simbolo, y si no hay simbolo no se inventa")
    void laMonedaSaleDelSimbolo() {
        assertEquals("USD", leer("registra un depa en venta US$ 180,000")
                .datos().get(Vocabulario.MONEDA + ":VENTA"));
        assertEquals("PEN", leer("registra un local en alquiler a S/ 2500")
                .datos().get(Vocabulario.MONEDA + ":ALQUILER"));
        assertNull(leer("registra un depa en venta de 180 mil").datos()
                        .get(Vocabulario.MONEDA + ":VENTA"),
                "sin simbolo la moneda se pregunta; suponerla es elegir entre 180 mil soles y "
                        + "180 mil dolares, que no son el mismo negocio");
    }

    @Test
    @DisplayName("cada importe conserva su operacion y no hay precio global")
    void cadaImporteConservaSuOperacion() {
        Interprete.Lectura lectura = leer(
                "registra un depa en venta US$ 180 mil y alquiler S/ 2800");

        assertEquals("VENTA,ALQUILER", lectura.datos().get(Vocabulario.OPERACIONES_DECLARADAS));
        assertEquals("180000", lectura.datos().get(Vocabulario.IMPORTE + ":VENTA"));
        assertEquals("USD", lectura.datos().get(Vocabulario.MONEDA + ":VENTA"));
        assertEquals("2800", lectura.datos().get(Vocabulario.IMPORTE + ":ALQUILER"));
        assertEquals("PEN", lectura.datos().get(Vocabulario.MONEDA + ":ALQUILER"));
        assertNull(lectura.datos().get(Vocabulario.IMPORTE));
        assertNull(lectura.datos().get(Vocabulario.MONEDA));
    }

    @Test
    @DisplayName("la cuota viaja como atributo con su propia moneda")
    void laCuotaViajaConSuMoneda() {
        Interprete.Lectura lectura = leer(
                "registra un depa en venta US$ 180 mil y cuota de mantenimiento S/ 350");

        assertEquals("180000", lectura.datos().get(Vocabulario.IMPORTE + ":VENTA"));
        assertEquals("350", lectura.datos().get(Vocabulario.CUOTA_MANTENIMIENTO));
        assertEquals("PEN", lectura.datos().get(Vocabulario.MONEDA + ":"
                + Vocabulario.CUOTA_MANTENIMIENTO));
        assertNull(lectura.datos().get(Vocabulario.IMPORTE));
        assertNull(lectura.datos().get(Vocabulario.MONEDA));
        assertFalse(lectura.noEntendido().stream()
                .anyMatch(trozo -> trozo.contains("cuota_mantenimiento")));

        Interprete.Lectura conPalabra = leer("registra un depa con mantenimiento 350 dolares");
        assertEquals("350", conPalabra.datos().get(Vocabulario.CUOTA_MANTENIMIENTO));
        assertEquals("USD", conPalabra.datos().get(Vocabulario.MONEDA + ":"
                + Vocabulario.CUOTA_MANTENIMIENTO));

        Interprete.Lectura cuota = leer("registra un depa con cuota 350 soles");
        assertEquals("350", cuota.datos().get(Vocabulario.CUOTA_MANTENIMIENTO));
        assertEquals("PEN", cuota.datos().get(Vocabulario.MONEDA + ":"
                + Vocabulario.CUOTA_MANTENIMIENTO));

        Interprete.Lectura sinMoneda = leer("registra un depa con cuota de mantenimiento 350 mil");
        assertEquals("350000", sinMoneda.datos().get(Vocabulario.CUOTA_MANTENIMIENTO));
        assertNull(sinMoneda.datos().get(Vocabulario.MONEDA + ":"
                + Vocabulario.CUOTA_MANTENIMIENTO));
        assertNull(sinMoneda.datos().get(Vocabulario.MONEDA));
    }

    @Test
    @DisplayName("la cuota solo se reconoce con una frase monetaria clara")
    void laCuotaNecesitaImporteAnclado() {
        Interprete.Lectura lectura = leer("registra un depa con cuota de mantenimiento 350");

        assertNull(lectura.datos().get(Vocabulario.CUOTA_MANTENIMIENTO));
        assertNull(lectura.datos().get(Vocabulario.MONEDA + ":"
                + Vocabulario.CUOTA_MANTENIMIENTO));
    }

    @Test
    @DisplayName("la lectura contextual continua un borrador con un dato inequívoco")
    void laLecturaContextualContinuaUnBorrador() {
        when(brox.captura(any(), eq(7L), any())).thenReturn(
                new ClienteBrox.EstadoCaptura(7L, "CAP-00007", "REGISTRAR_PROPIEDAD", "E", "API", Map.of(Vocabulario.TIPO_PROPIEDAD, "DEPARTAMENTO"), List.of("dormitorios"), null, false, null, null, null, 4, Map.of(), Map.of(), Map.of(), Map.of(), List.of()));

        Interprete.Lectura lectura = interprete.leerEnCaptura(
                "tiene 3 dormitorios", 7L, AGENTE, TRAZA);

        assertEquals(Accion.REGISTRAR_PROPIEDAD, lectura.accion());
        assertEquals("3", lectura.datos().get("dormitorios"));
    }

    @Test
    @DisplayName("USD responde el faltante activo de moneda y conserva el sujeto")
    void unaMonedaRespondeElFaltanteActivo() {
        String clave = Vocabulario.MONEDA + ":VENTA";
        when(brox.captura(any(), eq(7L), any())).thenReturn(
                estadoConFaltante(clave, pregunta(clave, "LISTA")));

        Interprete.Lectura lectura = interprete.leerEnCaptura("USD", 7L, AGENTE, TRAZA);

        assertEquals(Accion.REGISTRAR_PROPIEDAD, lectura.accion());
        assertEquals("USD", lectura.datos().get(clave));
        assertNull(lectura.datos().get(Vocabulario.MONEDA),
                "una respuesta escalar no puede volver a introducir moneda global");
        assertTrue(lectura.noEntendido().isEmpty());
    }

    @Test
    @DisplayName("soles responde el primer faltante activo y no el de otra operacion")
    void unaMonedaRespondeElPrimerFaltanteActivo() {
        String venta = Vocabulario.MONEDA + ":VENTA";
        String alquiler = Vocabulario.MONEDA + ":ALQUILER";
        when(brox.captura(any(), eq(7L), any())).thenReturn(
                estadoConFaltantes(List.of(venta, alquiler), pregunta(venta, "LISTA")));

        Interprete.Lectura lectura = interprete.leerEnCaptura("soles", 7L, AGENTE, TRAZA);

        assertEquals("PEN", lectura.datos().get(venta));
        assertNull(lectura.datos().get(alquiler),
                "la respuesta solo puede completar el hueco que BROX puso primero");
    }

    @Test
    @DisplayName("PEN responde la moneda propia de la cuota de mantenimiento")
    void laCuotaUsaSuClaveDeMoneda() {
        String clave = Vocabulario.MONEDA + ":" + Vocabulario.CUOTA_MANTENIMIENTO;
        when(brox.captura(any(), eq(7L), any())).thenReturn(
                estadoConFaltante(clave, pregunta(clave, "LISTA")));

        Interprete.Lectura lectura = interprete.leerEnCaptura("PEN", 7L, AGENTE, TRAZA);

        assertEquals("PEN", lectura.datos().get(clave));
        assertNull(lectura.datos().get(Vocabulario.MONEDA));
    }

    @Test
    @DisplayName("si responde un faltante booleano y se transporta como true")
    void unBooleanoRespondeElFaltanteActivo() {
        String clave = "exclusividad";
        when(brox.captura(any(), eq(7L), any())).thenReturn(
                estadoConFaltante(clave, pregunta(clave, "BOOLEANO")));

        Interprete.Lectura lectura = interprete.leerEnCaptura("sí", 7L, AGENTE, TRAZA);

        assertEquals(Accion.REGISTRAR_PROPIEDAD, lectura.accion());
        assertEquals("true", lectura.datos().get(clave));
        assertTrue(lectura.noEntendido().isEmpty());

        Interprete.Lectura negada = interprete.leerEnCaptura("no", 7L, AGENTE, TRAZA);
        assertEquals("false", negada.datos().get(clave));
    }

    @Test
    @DisplayName("un escalar fuera de su pregunta activa no se asigna")
    void unEscalarFueraDelFaltanteActivoNoSeAsigna() {
        String clave = "exclusividad";
        when(brox.captura(any(), eq(7L), any())).thenReturn(
                estadoConFaltante(clave, pregunta(clave, "BOOLEANO")));

        Interprete.Lectura lectura = interprete.leerEnCaptura("USD", 7L, AGENTE, TRAZA);

        assertFalse(lectura.hayAccion());
        assertTrue(lectura.datos().isEmpty());
    }

    @Test
    @DisplayName("una frase ambigua no se convierte en continuación")
    void unaFraseAmbiguaNoContinua() {
        Interprete.Lectura lectura = interprete.leerEnCaptura(
                "hola, ¿cómo estás?", 7L, AGENTE, TRAZA);

        assertNull(lectura.accion());
        assertEquals(Interprete.Lectura.SIN_ACCION, lectura.motivo());
    }

    @Test
    @DisplayName("180.000 y 180,000 son ciento ochenta mil; 180.5 no")
    void separadoresDeMiles() {
        assertEquals("180000", InterpreteDeterminista.numero("180.000").toPlainString());
        assertEquals("180000", InterpreteDeterminista.numero("180,000").toPlainString());
        assertEquals("180.5", InterpreteDeterminista.numero("180.5").toPlainString());
    }

    @Test
    @DisplayName("un importe anclado sin sujeto se declara pendiente, no global")
    void loQueNoEsUnidadNoSeDeclara() {
        Interprete.Lectura lectura = leer("registra un depa de 180 mil");
        assertNull(lectura.datos().get(Vocabulario.IMPORTE));
        assertTrue(lectura.noEntendido().contains("importe sin operacion"),
                "el importe necesita el sujeto del encargo; no se puede mandar global");
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
    @DisplayName("los ocho tipos salen del vocabulario del dominio; 'depa' es lo unico coloquial")
    void losTiposSalenDelDominio() {
        assertEquals("DEPARTAMENTO", tipoDe("registra un departamento"));
        assertEquals("DEPARTAMENTO", tipoDe("registra un depa"));
        assertEquals("TERRENO", tipoDe("registra un terreno"));
        assertEquals("ALMACEN", tipoDe("registra un almacen"));
        assertEquals("LOCAL", tipoDe("registra una tienda"));
        assertEquals("ESTACIONAMIENTO", tipoDe("registra un estacionamiento"));
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

    @Test void coincidenciasConservanOrigenYVersionDeclarados(){
        var r=leer("coincidencias demanda 47 versionOrigen 2 tipoVersion DEMANDA modalidad COMERCIAL capas L0,L1 categoria POTENCIAL");
        assertEquals(Accion.BUSCAR_COINCIDENCIAS,r.accion());
        assertEquals("DEMANDA",r.datos().get("origen"));assertEquals("47",r.datos().get("idOrigen"));
        assertEquals("2",r.datos().get("versionOrigen"));assertEquals("L0,L1",r.datos().get("capas"));
        assertNull(r.datos().get("modoExploracion"));assertNull(r.datos().get("limiteExploracion"));
    }
    @Test void descubrirSoloTransportaElModoExpreso() {
        var r=leer("coincidencias demanda 47 versionOrigen 2 tipoVersion DEMANDA modalidad COMERCIAL capas L0 categoria INCOMPATIBLE modoExploracion DESCUBRIR");
        assertEquals(Accion.BUSCAR_COINCIDENCIAS,r.accion());assertEquals("DESCUBRIR",r.datos().get("modoExploracion"));
        assertEquals("L0",r.datos().get("capas"));assertNull(r.datos().get("limiteExploracion"));assertNull(r.datos().get("monedaExploracion"));
    }
    @Test void explorarLeeImporteYMonedaSinConvertirlosEnPresupuesto() {
        var r=leer("coincidencias demanda 47 versionOrigen 2 tipoVersion DEMANDA modalidad COMERCIAL capas L0 categoria INCOMPATIBLE modoExploracion EXPLORAR limiteExploracion 110000.50 monedaExploracion USD");
        assertEquals("EXPLORAR",r.datos().get("modoExploracion"));assertEquals("110000.50",r.datos().get("limiteExploracion"));
        assertEquals("USD",r.datos().get("monedaExploracion"));assertFalse(r.datos().containsKey("presupuesto"));assertEquals("L0",r.datos().get("capas"));
    }
    @Test void limiteIlegibleNoDesapareceParaConvertirLaConsultaEnOrdinaria() {
        var r=leer("coincidencias demanda 47 modoExploracion EXPLORAR limiteExploracion desconocido monedaExploracion USD");
        assertEquals("desconocido",r.datos().get("limiteExploracion"));assertEquals("EXPLORAR",r.datos().get("modoExploracion"));
    }
    @Test void clienteNoSeConvierteEnDemandaNiSeCompletaVersion(){
        var r=leer("coincidencias cliente 47");assertNull(r.datos().get("origen"));assertNull(r.datos().get("versionOrigen"));
    }
    @Test void explicarConservaReferenciasOpacasYSusMayusculas(){
        var r=leer("explica coincidencia cursor AbC_123 referenciaDemanda GrUpO_789 referencia XyZ-456");
        assertEquals(Accion.EXPLICAR_MATCH,r.accion());assertEquals("AbC_123",r.datos().get("cursor"));assertEquals("XyZ-456",r.datos().get("referencia"));
        assertEquals("GrUpO_789",r.datos().get("referenciaDemanda"));
        assertNull(r.datos().get("origen"));assertNull(r.datos().get("codigo"));
        assertTrue(r.noEntendido().isEmpty());
    }
    @Test void explicarConCursorSoloPideReferenciaSinInventarla(){
        var r=leer("explica coincidencia cursor AbC_123");
        assertEquals("AbC_123",r.datos().get("cursor"));assertNull(r.datos().get("referencia"));
        assertEquals(List.of("referenciaDemanda","referencia"),r.noEntendido());
    }
    @Test void explicarConReferenciaSolaPideCursorSinElegirElUltimo(){
        var r=leer("explica coincidencia referencia XyZ-456");
        assertEquals("XyZ-456",r.datos().get("referencia"));assertNull(r.datos().get("cursor"));
        assertEquals(List.of("cursor","referenciaDemanda"),r.noEntendido());
    }
    @Test void explicarSinGrupoNoInterpretaLaReferenciaDeOfertaComoDemanda(){
        var r=leer("explica coincidencia cursor AbC_123 referencia XyZ-456");
        assertEquals("XyZ-456",r.datos().get("referencia"));assertNull(r.datos().get("referenciaDemanda"));
        assertEquals(List.of("referenciaDemanda"),r.noEntendido());
    }
    @Test void continuarCoincidenciasNoSeConfundeConBorrador(){
        var r=leer("continua coincidencias cursor AbC_123");assertEquals(Accion.CONTINUAR_COINCIDENCIAS,r.accion());assertEquals("AbC_123",r.datos().get("cursor"));
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
        assertEquals("D", conDni.datos().get("tipoDocumento"));
        assertEquals("987654321", conDni.datos().get("telefono"));

        Interprete.Lectura conRuc = leer(
                "registra al propietario Inversiones con RUC 20505060708");
        assertEquals("R", conRuc.datos().get("tipoDocumento"));
        assertEquals("J", conRuc.datos().get("tipoPersona"),
                "once digitos es un RUC, y un RUC es una persona juridica");

        assertTrue(leer("registra al propietario Torres").noEntendido().contains("documento"),
                "sin documento no hay con que descartar un duplicado");
    }

    @ParameterizedTest
    @CsvSource({"DNI,40506070,D", "RUC,20505060708,R"})
    void elDocumentoDeclaradoConservaSuNumeroYUsaElCodigoCore(String documento,
                                                             String numero, String codigo) {
        var lectura = leer("registra al propietario Torres con " + documento + " " + numero);

        assertEquals(Accion.REGISTRAR_PROPIETARIO, lectura.accion());
        assertEquals(numero, lectura.datos().get("numeroDocumento"));
        assertEquals(codigo, lectura.datos().get("tipoDocumento"));
        assertFalse(lectura.datos().containsKey("consentimientoUsoDato"),
                "declarar un documento no acredita consentimiento para usar sus datos");
    }

    @Test
    @DisplayName("los canales usan los codigos del dominio, no los que uno supondria")
    void losCanalesSonLosDelDominio() {
        assertNull(canalDe("anota la llamada a la oportunidad 7"),"Llamada no dice si usó teléfono o una aplicación");
        assertEquals("TELEFONO", canalDe("anota la llamada por teléfono a la oportunidad 7"));
        assertEquals("WHATSAPP", canalDe("anota el whatsapp de la oportunidad 7"));
        assertEquals("CORREO_ELECTRONICO", canalDe("anota el correo electrónico de la oportunidad 7"));
        assertNull(canalDe("anota la reunion de la oportunidad 7"),"Una reunión no acredita presencialidad");
    }

    @Test
    @DisplayName("una interaccion sin entidad de la que colgar se declara incompleta")
    void interaccionSinExpediente() {
        Interprete.Lectura suelta = leer("anota la llamada");
        assertTrue(suelta.noEntendido().isEmpty(),"El esquema Core decide qué dato necesita la actuación");
        assertFalse(argumentos(suelta).containsKey("vinculos"));

        Interprete.Lectura conExpediente = leer("anota la llamada de la oportunidad 7");
        var vinculo=(Map<?,?>)((List<?>)argumentos(conExpediente).get("vinculos")).getFirst();
        assertEquals("OPORTUNIDAD",vinculo.get("tipo"));
        assertEquals(7L,vinculo.get("referencia"));
        assertFalse(vinculo.containsKey("versionEsperada"));
    }

    @Test
    @DisplayName("el resultado de una interaccion no se deduce del tono")
    void elResultadoNoSeDeduce() {
        assertNull(argumentos(leer("anota la llamada de la oportunidad 7, fue muy bien")).get("resultado"),
                "El tono no acredita un resultado comunicativo");
    }

    @Test
    void elPerfilNoConvierteUnaVisitaOUnMensajePreparadoEnComunicacionRealizada() {
        var visita=leer("anota la visita de la oportunidad 7");
        assertEquals(Accion.REGISTRAR_RESULTADO_VISITA,visita.accion());
        var conocidos=ArgumentosComerciales.leer(visita.datos().get("datosComerciales"),OperacionComercial.REGISTRAR_RESULTADO_VISITA);
        assertFalse(conocidos.containsKey("idVisita"));
        assertFalse(((Map<?,?>)conocidos.get("resultadoVisita")).containsKey("ocurrencia"));
        assertFalse(leer("anota el mensaje preparado para la oportunidad 7").hayAccion());
        assertFalse(leer("anota la reunión cancelada de la oportunidad 7").hayAccion());
    }

    @Test
    void laDefinicionAusenteOFallidaNoProduceUnVocabularioLocalDeReserva() {
        when(brox.definicionDeInteracciones(any(),any())).thenReturn(Map.of());
        assertThrows(ErrorBrox.class,()->leer("anota llamada de oportunidad 7"));
        when(brox.definicionDeInteracciones(any(),any())).thenThrow(new ErrorBrox(ErrorBrox.Tipo.FALLO_TECNICO,"FALLO",503));
        assertThrows(ErrorBrox.class,()->leer("anota llamada de oportunidad 7"));
    }

    @Test
    void unResultadoAnteriorNoSeConvierteEnExitoComunicativo() {
        var conocidos=argumentos(leer("anota llamada de oportunidad 7 con resultado CONTACTADO"));
        assertFalse(conocidos.containsKey("resultado"));
        assertTrue(conocidos.get("resumen").toString().contains("CONTACTADO"));
        assertFalse(conocidos.containsKey("momento"));
        assertFalse(conocidos.containsKey("interlocutor"));
    }

    // ==================================================================

    private Interprete.Lectura leer(String frase) {
        return interprete.leer(frase, AGENTE, TRAZA);
    }

    private String importeDe(String frase) {
        Interprete.Lectura lectura = leer("registra " + frase);
        String operacion = lectura.datos().get(Vocabulario.OPERACIONES_DECLARADAS);
        return lectura.datos().get(Vocabulario.IMPORTE + ":"
                + (operacion == null ? "" : operacion.split(",")[0]));
    }

    private String tipoDe(String frase) {
        return leer(frase).datos().get(Vocabulario.TIPO_PROPIEDAD);
    }

    private String canalDe(String frase) {
        return (String)argumentos(leer(frase)).get("canal");
    }

    private static Map<String,Object> argumentos(Interprete.Lectura lectura) {
        return ArgumentosComerciales.leer(lectura.datos().get("datosComerciales"),OperacionComercial.REGISTRAR_INTERACCION);
    }

    private static ClienteBrox.EstadoCaptura estadoConFaltante(String clave,
                                                                ClienteBrox.Pregunta siguiente) {
        return estadoConFaltantes(List.of(clave), siguiente);
    }

    private static ClienteBrox.EstadoCaptura estadoConFaltantes(List<String> faltantes,
                                                                 ClienteBrox.Pregunta siguiente) {
        return new ClienteBrox.EstadoCaptura(7L, "CAP-00007", "REGISTRAR_PROPIEDAD", "E", "API", Map.of(Vocabulario.TIPO_PROPIEDAD, "DEPARTAMENTO"), faltantes, siguiente, false, null, null, null, 4, Map.of(), Map.of(), Map.of(), Map.of(), List.of());
    }

    private static ClienteBrox.Pregunta pregunta(String clave, String tipoDato) {
        return new ClienteBrox.Pregunta(clave, clave, "OPERACION", null, "SELECTOR", tipoDato, null, List.of(), "ALT", true, null, 0, null, null);
    }

    /** Que el fixture esta donde se cree que esta. Si no, todo lo de arriba miente. */
    @Test
    @DisplayName("el catalogo grabado son los ocho tipos y ninguno viene vacio")
    void elCatalogoGrabadoEstaCompleto() {
        for (String tipo : CatalogoReal.TIPOS) {
            List<ClienteBrox.Pregunta> catalogo = CatalogoReal.catalogoDe(tipo);
            assertFalse(catalogo.isEmpty(), "el catalogo grabado de " + tipo + " esta vacio");
            assertEquals(tipo, CatalogoReal.definicionDe(tipo).tipoPropiedad());
        }
    }
}
