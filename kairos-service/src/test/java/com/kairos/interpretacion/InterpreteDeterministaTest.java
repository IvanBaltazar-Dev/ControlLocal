package com.kairos.interpretacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Vocabulario;
import com.kairos.conversacion.Accion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
 */
class InterpreteDeterministaTest {

    private static final SesionBrox AGENTE = new SesionBrox("token-de-la-persona", 1L);

    private InterpreteDeterminista interprete;

    @BeforeEach
    void preparar() {
        ClienteBrox brox = mock(ClienteBrox.class);
        when(brox.distritos(any())).thenReturn(List.of(
                "Miraflores", "San Isidro", "Surco", "Santiago de Surco"));
        // El catalogo de un departamento: uno sin unidad y TRES que comparten m2.
        when(brox.catalogoDe(any(), anyString())).thenReturn(catalogoDeDepartamento());
        interprete = new InterpreteDeterminista(brox);
    }

    // ==================================================================
    // La regla que existe para no romperse
    // ==================================================================

    @Test
    @DisplayName("la operacion NO se infiere: sin 'vende' ni 'alquila', no se emite")
    void laOperacionNoSeInfiere() {
        Interprete.Lectura lectura = interprete.leer(
                "registra un depa en Miraflores de US$ 180 mil", AGENTE);

        assertEquals(Accion.REGISTRAR_PROPIEDAD, lectura.accion());
        assertEquals("DEPARTAMENTO", lectura.datos().get(Vocabulario.TIPO_PROPIEDAD));
        assertEquals("180000", lectura.datos().get(Vocabulario.IMPORTE));
        assertNull(lectura.datos().get(Vocabulario.OPERACION),
                "180 mil es un precio de venta plausible Y una renta imposible, pero la frase no "
                        + "dice cual: emitir una operacion aqui archivaria el importe en la serie "
                        + "equivocada y ningun CHECK podria notarlo");
    }

    @Test
    @DisplayName("la operacion se emite cuando la frase la dice, y solo entonces")
    void laOperacionSeEmiteSiSeDice() {
        assertEquals("VENTA", interprete.leer("registra un depa que se vende", AGENTE)
                .datos().get(Vocabulario.OPERACION));
        assertEquals("ALQUILER", interprete.leer("registra un local en alquiler", AGENTE)
                .datos().get(Vocabulario.OPERACION));
        assertEquals("ALQUILER", interprete.leer("registra una casa para arriendo", AGENTE)
                .datos().get(Vocabulario.OPERACION));
    }

    @Test
    @DisplayName("'disponible' o 'en cartera' NO son operaciones")
    void loQueNoDeclaraOperacionNoLaDeclara() {
        assertNull(interprete.leer("registra un depa disponible en cartera", AGENTE)
                        .datos().get(Vocabulario.OPERACION),
                "estar disponible no dice si se vende o se alquila");
    }

    // ==================================================================
    // Un numero no es un precio por ser un numero
    // ==================================================================

    @Test
    @DisplayName("un numero suelto no se toma por importe")
    void unNumeroSueltoNoEsUnPrecio() {
        Interprete.Lectura lectura =
                interprete.leer("registra un depa de 3 dormitorios", AGENTE);

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
        assertEquals("USD", interprete.leer("registra un depa en US$ 180,000", AGENTE)
                .datos().get(Vocabulario.MONEDA));
        assertEquals("PEN", interprete.leer("registra un local a S/ 2500", AGENTE)
                .datos().get(Vocabulario.MONEDA));
        assertNull(interprete.leer("registra un depa de 180 mil", AGENTE)
                        .datos().get(Vocabulario.MONEDA),
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

    // ==================================================================
    // Lo ambiguo se declara, no se resuelve
    // ==================================================================

    @Test
    @DisplayName("'120 m2' encaja con tres atributos: no se emite ninguno, se declara")
    void laUnidadCompartidaNoDesambigua() {
        Interprete.Lectura lectura =
                interprete.leer("registra un depa de 120 m2", AGENTE);

        assertNull(lectura.datos().get("metraje_total"));
        assertNull(lectura.datos().get("metraje_construido"));
        assertNull(lectura.datos().get("area_terreno"));
        assertTrue(lectura.noEntendido().contains("120 m2"),
                "tres atributos usan m2: elegir uno seria decidir por el usuario cual midio");
    }

    @Test
    @DisplayName("con el nombre del atributo si se emite")
    void elNombreDelAtributoSiDesambigua() {
        assertEquals("120", interprete.leer("registra un depa de 120 dormitorios", AGENTE)
                .datos().get("dormitorios"));
    }

    // ==================================================================
    // El vocabulario sale del tenant, no de una lista escrita a mano
    // ==================================================================

    @Test
    @DisplayName("el distrito sale de la tabla, y uno que no existe no se emite")
    void elDistritoSaleDeLaTabla() {
        assertEquals("Miraflores", interprete.leer("registra un depa en Miraflores", AGENTE)
                .datos().get(Vocabulario.DISTRITO));
        assertNull(interprete.leer("registra un depa en Chorrillos", AGENTE)
                        .datos().get(Vocabulario.DISTRITO),
                "Chorrillos no esta dado de alta: preguntarlo es mejor que fallar al guardar");
    }

    @Test
    @DisplayName("entre dos distritos que encajan gana el nombre mas largo")
    void ganaElNombreMasLargo() {
        assertEquals("Santiago de Surco",
                interprete.leer("registra un depa en Santiago de Surco", AGENTE)
                        .datos().get(Vocabulario.DISTRITO));
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
        assertEquals(Accion.REGISTRAR_PROPIEDAD,
                interprete.leer("registra un local", AGENTE).accion());
        assertEquals(Accion.CONSULTAR_PROPIEDAD,
                interprete.leer("muestrame el local PROP-12", AGENTE).accion());
        assertEquals(Accion.CONSULTAR_CLIENTE,
                interprete.leer("busca al cliente Torres", AGENTE).accion());
        assertEquals(Accion.REGISTRAR_PROPIETARIO,
                interprete.leer("da de alta al propietario Torres con DNI 40506070", AGENTE)
                        .accion());
        assertEquals(Accion.CONTINUAR_BORRADOR,
                interprete.leer("sigue con lo de ayer", AGENTE).accion());
        assertEquals(Accion.REGISTRAR_INTERACCION,
                interprete.leer("anota la llamada de la oportunidad 7", AGENTE).accion());
    }

    @Test
    @DisplayName("sin verbo, o sin objeto, no se elige la accion mas comun")
    void sinLosDosNoHayAccion() {
        assertFalse(interprete.leer("registra", AGENTE).hayAccion());
        assertFalse(interprete.leer("un depa en Miraflores", AGENTE).hayAccion());
        assertFalse(interprete.leer("", AGENTE).hayAccion());
        assertEquals(Interprete.Lectura.SIN_TEXTO, interprete.leer("  ", AGENTE).motivo());
    }

    // ==================================================================
    // Las otras acciones
    // ==================================================================

    @Test
    @DisplayName("un propietario sin documento declara que le falta el documento")
    void sinDocumentoSeDeclara() {
        Interprete.Lectura conDni = interprete.leer(
                "registra al propietario Torres con DNI 40506070 y telefono 987654321", AGENTE);
        assertEquals("40506070", conDni.datos().get("numeroDocumento"));
        assertEquals("DNI", conDni.datos().get("tipoDocumento"));
        assertEquals("987654321", conDni.datos().get("telefono"));

        Interprete.Lectura conRuc = interprete.leer(
                "registra al propietario Inversiones con RUC 20505060708", AGENTE);
        assertEquals("RUC", conRuc.datos().get("tipoDocumento"));
        assertEquals("J", conRuc.datos().get("tipoPersona"),
                "once digitos es un RUC, y un RUC es una persona juridica");

        assertTrue(interprete.leer("registra al propietario Torres", AGENTE)
                        .noEntendido().contains("documento"),
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
        Interprete.Lectura suelta = interprete.leer("anota la llamada", AGENTE);
        assertTrue(suelta.noEntendido().contains("sobre que"));
        assertNull(suelta.datos().get("contexto"));

        Interprete.Lectura conExpediente =
                interprete.leer("anota la llamada de la oportunidad 7", AGENTE);
        assertEquals("OPORTUNIDAD", conExpediente.datos().get("contexto"));
        assertEquals("7", conExpediente.datos().get("idEntidad"));
    }

    @Test
    @DisplayName("el resultado de una interaccion no se deduce del tono")
    void elResultadoNoSeDeduce() {
        assertNull(interprete.leer("anota la llamada de la oportunidad 7, fue muy bien", AGENTE)
                        .datos().get("resultado"),
                "'fue muy bien' no es ninguno de los seis resultados que admite una oportunidad");
    }

    // ==================================================================

    private String importeDe(String frase) {
        return interprete.leer("registra " + frase, AGENTE)
                .datos().get(Vocabulario.IMPORTE);
    }

    private String tipoDe(String frase) {
        return interprete.leer(frase, AGENTE).datos().get(Vocabulario.TIPO_PROPIEDAD);
    }

    private String canalDe(String frase) {
        return interprete.leer(frase, AGENTE).datos().get("canalContacto");
    }

    private static List<ClienteBrox.Pregunta> catalogoDeDepartamento() {
        return List.of(
                atributo("dormitorios", "Dormitorios", "ENTERO", null),
                atributo("metraje_total", "Metraje total", "DECIMAL", "m2"),
                atributo("metraje_construido", "Metraje construido", "DECIMAL", "m2"),
                atributo("area_terreno", "Area de terreno", "DECIMAL", "m2"));
    }

    private static ClienteBrox.Pregunta atributo(String clave, String rotulo, String tipoDato,
                                                 String unidad) {
        return new ClienteBrox.Pregunta(clave, rotulo, tipoDato, unidad, null, false, null);
    }
}
