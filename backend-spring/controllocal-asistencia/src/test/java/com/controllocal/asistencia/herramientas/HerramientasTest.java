package com.controllocal.asistencia.herramientas;

import com.controllocal.asistencia.conversacion.Accion;
import com.controllocal.asistencia.brox.ClienteBrox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contratos de herramientas: catálogo vigente y BROX B.2.6/RF046–048.
 *
 * <p>Lo que estas pruebas protegen no es que la lista exista: es que
 * <b>ninguna herramienta pueda declararse sin caso de uso, sin permiso o sin
 * confirmacion</b>, y que las actuaciones sensibles declaradas sigan siendo sensibles. Una tabla
 * en un documento no impide anadir una llamada al Core sin decir por donde
 * entra; esto si.
 */
class HerramientasTest {

    @Test void materialTieneTresEntradasYSusDecisionesHumanasNoSonHerramientas(){
        assertEquals(Confirmacion.NUNCA,Herramientas.PREPARAR_MATERIAL_IA.confirmacion());
        assertEquals(Confirmacion.AVISA,Herramientas.SOLICITAR_MATERIAL_IA.confirmacion());
        assertEquals(Confirmacion.NUNCA,Herramientas.CONSULTAR_MATERIAL_IA.confirmacion());
        for(String nombre:List.of("decidir_resultado_material_ia","consultar_revision_material_ia","consultar_seleccion_material_ia","consultar_versiones_material_ia",
                "seleccionar_foto_material_ia","guardar_alternativa_material_ia","descartar_temporal_material_ia","eliminar_alternativa_material_ia",
                "presentar_tratamiento_material_ia","registrar_decision_tratamiento_ia","descargar_archivo_material_ia"))
            assertTrue(Herramientas.de(nombre).isEmpty(),nombre+" pertenece a la superficie humana");
        assertFalse(ClienteBrox.OperacionComercial.SOLICITAR_MATERIAL_IA.preparaPropuesta());
    }

    /** Las puertas de efecto directo conservan el suelo SIEMPRE; los preparadores no ejecutan. */
    private static final Set<String> SENSIBLES = Set.of(
            "decidir_captacion",
            "evaluar_solicitud", "cerrar_encargo","configurar_programa_personal","cambiar_estado_programa_personal");

    @Test
    @DisplayName("las puertas sensibles de efecto directo conservan confirmacion humana")
    void lasSensiblesLoSiguenSiendo() {
        for (String nombre : SENSIBLES) {
            Herramienta herramienta = Herramientas.de(nombre).orElseThrow(
                    () -> new AssertionError("falta la herramienta sensible " + nombre));
            assertEquals(Confirmacion.SIEMPRE, herramienta.confirmacion(),
                    nombre + " sale al exterior, firma algo, mueve dinero o no se deshace: la "
                            + "confirma una persona");
            assertTrue(herramienta.laConfirmaUnaPersona());
        }
    }

    @Test
    @DisplayName("los preparadores no sustituyen la revision que exige el descriptor Core")
    void elSueloDelPreparadorNoReemplazaLaAutoridadDelCore() {
        Herramientas.todas().stream()
                .filter(h -> !SENSIBLES.contains(h.nombre()))
                .forEach(h -> assertFalse(h.laConfirmaUnaPersona(),
                        h.nombre() + " no tiene suelo SIEMPRE en este catálogo; la capacidad Core "
                                + "sigue pudiendo exigir su revisión humana exacta"));
    }

    @Test
    @DisplayName("toda herramienta declara metodo, ruta y permiso: sin eso no hay caso de uso")
    void ningunaSinCasoDeUso() {
        for (Herramienta herramienta : Herramientas.todas()) {
            assertFalse(herramienta.casoDeUso().isBlank());
            assertTrue(herramienta.ruta().startsWith("/"),
                    herramienta.nombre() + " tiene que nombrar una ruta del Core");
            assertTrue(List.of("GET", "POST", "PUT", "PATCH", "DELETE")
                            .contains(herramienta.metodo()),
                    herramienta.nombre() + " tiene que nombrar un verbo HTTP");
            assertFalse(herramienta.permiso().isBlank(),
                    herramienta.nombre() + " tiene que copiar su celda de la matriz");
        }
    }

    @Test
    @DisplayName("una herramienta sin caso de uso no se puede construir")
    void sinCasoDeUsoNoCompone() {
        IllegalArgumentException sinRuta = assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", " ", Herramienta.AGENTE,
                        Confirmacion.AVISA, Herramienta.Estado.VIVA));
        assertTrue(sinRuta.getMessage().contains("caso de uso"));

        assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", "/algo", " ", Confirmacion.AVISA,
                        Herramienta.Estado.VIVA));
        assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", "/algo", Herramienta.AGENTE, null,
                        Herramienta.Estado.VIVA));
        assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", "/algo", Herramienta.AGENTE,
                        Confirmacion.AVISA, null));
    }

    @Test
    @DisplayName("toda accion nombra una herramienta del catalogo, y esta implementada")
    void ningunaAccionApuntaAlVacio() {
        for (Accion accion : Accion.values()) {
            Herramienta herramienta = Herramientas.de(accion.capacidad()).orElseThrow(
                    () -> new AssertionError(accion + " nombra la capacidad \"" + accion.capacidad()
                            + "\", que no esta en el catalogo"));
            assertTrue(herramienta.implementada(),
                    accion + " apunta a " + herramienta.nombre() + ", declarada y sin implementar");
            assertEquals(herramienta, accion.herramienta());
        }
    }

    @Test
    @DisplayName("las declaradas y no implementadas siguen declaradas: no se decide dos veces")
    void lasDeclaradasSiguenDeclaradas() {
        List<String> pendientes = Herramientas.declaradas().stream()
                .map(Herramienta::nombre)
                .toList();
        assertEquals(List.of("configurar_programa_personal","cambiar_estado_programa_personal","decidir_captacion",
                        "evaluar_solicitud", "cerrar_encargo"),
                pendientes,
                "estas puertas tienen su caso de uso y su confirmacion escritos aunque KAIROS "
                        + "todavia no las invoque: el dia que se implementen no se vuelve a "
                        + "decidir si publicar sale al exterior");
    }

    @Test
    @DisplayName("la oferta prepara una actuacion revisable y no representa envio externo o aceptacion")
    void laOfertaPreparaActuacionSinSustituirRevisionHumana() {
        assertEquals("/propiedades", Herramientas.BUSCAR_PROPIEDADES.ruta());
        assertEquals("GET", Herramientas.CONSULTAR_AGENDA_VISITAS.metodo());
        assertEquals("/visitas/agenda", Herramientas.CONSULTAR_AGENDA_VISITAS.ruta());
        assertEquals("POST", Herramientas.PRESENTAR_OFERTA.metodo());
        assertEquals("/oportunidades/{id}/ofertas/propuestas", Herramientas.PRESENTAR_OFERTA.ruta());
        assertTrue(Herramientas.implementadas().contains(Herramientas.PRESENTAR_OFERTA));
        assertEquals(Confirmacion.AVISA,Herramientas.PRESENTAR_OFERTA.confirmacion(),
                "esta herramienta prepara; registrar el hecho exige la aprobación exacta de B.2.6/RF046");
        // Fixture del descriptor vigente ESCRITURA/CONFIRMA; no acredita transporte HTTP C4.
        var capacidad=new ClienteBrox.Capacidad("presentar_oferta","NEGOCIACION","ESCRITURA",
                ClienteBrox.Capacidad.CONFIRMA,Map.of(),1,"HABILITADA",null,null);
        assertTrue(capacidad.laConfirmaUnaPersona());
        assertEquals(Confirmacion.SIEMPRE,Herramientas.PRESENTAR_OFERTA.confirmacion().mas(
                capacidad.laConfirmaUnaPersona()?Confirmacion.SIEMPRE:Confirmacion.NUNCA),
                "AVISA del preparador no rebaja la revisión humana que exige Core");
        assertTrue(Herramientas.de("revisar_actuacion").isEmpty());
        assertTrue(Herramientas.de("confirmar_captura").isEmpty(),
                "el modelo no obtiene las puertas de revisión/confirmación humanas");
    }

    @Test
    @DisplayName("la confirmacion se compone hacia el lado estricto, nunca hacia el comodo")
    void laConfirmacionSeComponeHaciaLoEstricto() {
        assertEquals(Confirmacion.SIEMPRE, Confirmacion.SIEMPRE.mas(Confirmacion.NUNCA));
        assertEquals(Confirmacion.SIEMPRE, Confirmacion.NUNCA.mas(Confirmacion.SIEMPRE));
        assertEquals(Confirmacion.AVISA, Confirmacion.AVISA.mas(Confirmacion.NUNCA));
        assertEquals(Confirmacion.SIEMPRE, Confirmacion.AVISA.mas(Confirmacion.SIEMPRE));
        assertEquals(Confirmacion.AVISA, Confirmacion.AVISA.mas(null),
                "sin nada que componer, manda el catalogo");
    }

    @Test
    @DisplayName("los permisos copiados son celdas reales de la matriz operacion→rol")
    void losPermisosSonCeldasDeLaMatriz() {
        Set<String> celdas = Set.of(Herramienta.TODOS, Herramienta.AGENTE, Herramienta.BROKER,Herramienta.AGENTE_BROKER);
        Herramientas.todas().stream()
                .filter(h -> !h.sinPuertaEnElCore())
                .forEach(h -> assertTrue(celdas.contains(h.permiso()),
                        h.nombre() + " declara un permiso que no es ninguna celda de la matriz: "
                                + h.permiso()));

        assertEquals(Herramienta.AGENTE, Herramientas.PREPARAR_ACTUACION_CAPTURA.permiso(),
                "POST /captura/{id}/ejecutar es AGENTE en la matriz; abrir la captura es TODOS");
        assertEquals(Herramienta.TODOS, Herramientas.ABRIR_SESION_CAPTURA.permiso());
        assertEquals(Herramienta.BROKER, Herramientas.EVALUAR_SOLICITUD.permiso(),
                "firmar una evaluacion es del broker, y el TENANT_ADMIN dejo de alcanzarla");
    }

    @Test
    @DisplayName("una ruta que el Core no sirve no copia el permiso de una parecida")
    void loQueElCoreNoSirveNoInventaSuCelda() {
        for (Herramienta herramienta : Herramientas.sinPuertaEnElCore()) {
            assertEquals(Herramienta.SIN_FILA, herramienta.permiso(),
                    herramienta.nombre() + " no tiene fila en la matriz porque el Core no sirve "
                            + herramienta.casoDeUso() + ": copiar la celda de una ruta parecida "
                            + "seria interpretar, y el catalogo copia");
        }
        Herramientas.todas().stream()
                .filter(h -> Herramienta.SIN_FILA.equals(h.permiso()))
                .forEach(h -> assertTrue(h.sinPuertaEnElCore(),
                        h.nombre() + " dice no tener fila en la matriz y el Core si sirve su "
                                + "ruta: las dos cosas se mueven juntas o una de las dos miente"));
    }

    @Test
    @DisplayName("lo que le falta al Core sale del catalogo, no de un documento aparte")
    void loQueLeFaltaAlCoreEstaDicho() {
        assertEquals(List.of(), Herramientas.sinPuertaEnElCore().stream().map(Herramienta::nombre).toList(),
                "C0/K0 publica las dos puertas. Un 400/404 ya no se convierte en lista vacía.");

        Herramientas.sinPuertaEnElCore().forEach(h -> assertTrue(h.implementada(),
                h.nombre() + " la invoca KAIROS: que la llamada vuelva con un 404 es un hecho "
                        + "del Core, no una carencia de KAIROS, y confundirlos haria que "
                        + "implementarla del lado del Core no cambiara nada aqui"));
    }

    @Test
    @DisplayName("las tres lecturas de contrato estan en el catalogo: si no, viajan sin nombre")
    void lasLecturasDeContratoEstanDeclaradas() {
        for (String nombre : List.of("consultar_capacidades", "catalogo_de_captura",
                "definicion_de_interacciones")) {
            Herramienta herramienta = Herramientas.de(nombre).orElseThrow(
                    () -> new AssertionError("falta la lectura de contrato " + nombre));
            assertEquals("GET", herramienta.metodo(), nombre + " pregunta, no escribe");
            assertEquals(Confirmacion.NUNCA, herramienta.confirmacion());
            assertTrue(herramienta.implementada(),
                    nombre + " sale por el cable en cada turno o en cada frase con tipo");
        }
    }
}
