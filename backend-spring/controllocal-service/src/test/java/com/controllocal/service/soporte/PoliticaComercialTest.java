package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.PoliticaComercial.Concepto;
import com.controllocal.service.soporte.PoliticaComercial.NivelAtencion;
import com.controllocal.service.soporte.PoliticaComercial.Regla;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blinda la politica comercial: una regla por nombre, un valor por regla y una
 * sola forma de clasificar.
 *
 * <p>Lo que de verdad vigila no es que 7 sea 7 —eso lo cambiara alguien algun
 * dia con razon—, sino que <b>cambiarlo sea un acto unico y visible</b>. Antes
 * de E1 el plazo de recontacto estaba cuadruplicado y la coherencia dependia de
 * un comentario pidiendo que los cuatro numeros cuadraran.
 */
class PoliticaComercialTest {

    // ------------------------------------------------------------------
    // Forma del catalogo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cada regla trae nombre estable, significado, unidad y version")
    void cadaReglaEstaDescrita() {
        for (Regla regla : PoliticaComercial.REGLAS) {
            assertFalse(regla.nombre().isBlank(), "regla sin nombre: " + regla);
            assertTrue(regla.nombre().matches("[a-z0-9-]+(\\.[a-z0-9-]+)+"),
                    "el nombre debe ser estable y legible (`ambito.que-mide`): " + regla.nombre());
            assertTrue(regla.significado().length() > 30,
                    "el significado tiene que explicar la regla a alguien que no lea el codigo: "
                            + regla.nombre());
            assertTrue(regla.valor() > 0, "valor no positivo en " + regla.nombre());
            assertTrue(regla.version() >= 1, "version invalida en " + regla.nombre());
        }
    }

    @Test
    @DisplayName("no hay dos reglas con el mismo nombre")
    void losNombresSonUnicos() {
        Set<String> vistos = new HashSet<>();
        for (Regla regla : PoliticaComercial.REGLAS) {
            assertTrue(vistos.add(regla.nombre()), "nombre repetido: " + regla.nombre());
        }
    }

    @Test
    @DisplayName("todas nacen GLOBAL: la configuracion por corredora no esta implementada")
    void todasSonGlobalesTodavia() {
        for (Regla regla : PoliticaComercial.REGLAS) {
            assertEquals(PoliticaComercial.Alcance.GLOBAL, regla.alcance(),
                    regla.nombre() + " declara alcance por organizacion, pero nada lo resuelve "
                            + "todavia: implementarlo antes de declararlo");
        }
    }

    // ------------------------------------------------------------------
    // Los valores vigentes
    // ------------------------------------------------------------------

    /**
     * Fija los valores acordados en el inventario de E1. Si este test falla es
     * porque alguien cambio una regla: <b>eso puede estar bien</b>, pero tiene
     * que ser deliberado, subir la {@code version} de esa regla y —en las dos
     * que el formulario necesita por adelantado— actualizar el espejo del SPA.
     */
    @Test
    @DisplayName("los valores vigentes son los del inventario de E1")
    void valoresVigentes() {
        assertEquals(7, PoliticaComercial.RECONTACTO.valor());
        assertEquals(3, PoliticaComercial.VISITA_PROXIMA.valor());
        assertEquals(15, PoliticaComercial.REPORTE_PROPIETARIO.valor());
        assertEquals(60, PoliticaComercial.COINCIDENCIA_PROPONIBLE.valor());
        assertEquals(200, PoliticaComercial.COMISION_MAXIMA.valor());

        // Estos dos tienen espejo en el SPA porque el formulario los necesita
        // ANTES de enviar: frontend-angular/src/app/core/politica-comercial.ts.
        assertEquals(6, PoliticaComercial.ENCARGO.valor(),
            "si cambia, actualiza tambien core/politica-comercial.ts (encargoMesesPorDefecto)");
        assertEquals(10, PoliticaComercial.MOTIVO_REASIGNACION.valor(),
            "si cambia, actualiza tambien core/politica-comercial.ts "
                    + "(motivoReasignacionCaracteres)");
    }

    @Test
    @DisplayName("las reglas aplicadas usan su propio valor, no una copia")
    void lasReglasSeAplicanConSuValor() {
        LocalDate hoy = LocalDate.of(2026, 8, 10);

        assertEquals(hoy.minusDays(PoliticaComercial.RECONTACTO.valor()),
                PoliticaComercial.limiteDeRecontacto(hoy));
        assertEquals(hoy.plusDays(PoliticaComercial.VISITA_PROXIMA.valor()),
                PoliticaComercial.horizonteDeVisitas(hoy));
        assertEquals(hoy.plusDays(PoliticaComercial.REPORTE_PROPIETARIO.valor()),
                PoliticaComercial.proximoReporteAlPropietario(hoy));
        assertEquals(hoy.plusMonths(PoliticaComercial.ENCARGO.valor()),
                PoliticaComercial.finDelEncargo(hoy));

        int minimo = PoliticaComercial.COINCIDENCIA_PROPONIBLE.valor();
        assertTrue(PoliticaComercial.valeLaPenaProponer(minimo), "el minimo entra");
        assertFalse(PoliticaComercial.valeLaPenaProponer(minimo - 1), "por debajo, no");
    }

    @Test
    @DisplayName("la comision maxima de CondicionesEconomicas sale de la politica")
    void condicionesEconomicasNoTieneSuPropioTope() {
        assertEquals(0, CondicionesEconomicas.COMISION_MAXIMA
                .compareTo(PoliticaComercial.comisionMaxima()));
    }

    // ------------------------------------------------------------------
    // El motivo de reasignacion, que antes solo vigilaba el formulario
    // ------------------------------------------------------------------

    @Test
    @DisplayName("rechaza el motivo corto y lo dice con el numero")
    void motivoCorto() {
        var error = assertThrows(ReglaNegocioException.class,
                () -> PoliticaComercial.exigirMotivoDeReasignacion("ok"));
        assertTrue(error.getMessage()
                        .contains(String.valueOf(PoliticaComercial.MOTIVO_REASIGNACION.valor())),
                error.getMessage());
    }

    @Test
    @DisplayName("un motivo de espacios no cuela por longitud")
    void motivoDeEspacios() {
        assertThrows(ReglaNegocioException.class,
                () -> PoliticaComercial.exigirMotivoDeReasignacion("            "));
    }

    @Test
    @DisplayName("nulo tambien es motivo invalido, no un NullPointerException")
    void motivoNulo() {
        assertThrows(ReglaNegocioException.class,
                () -> PoliticaComercial.exigirMotivoDeReasignacion(null));
    }

    @Test
    @DisplayName("devuelve el motivo recortado: lo que se guarda es lo que se lee")
    void motivoValidoSeRecorta() {
        assertEquals("cambio de zona del agente",
                PoliticaComercial.exigirMotivoDeReasignacion("  cambio de zona del agente  "));
    }

    // ------------------------------------------------------------------
    // Que urge, y en que orden
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cada concepto tiene una prioridad distinta: el orden no puede ser ambiguo")
    void lasPrioridadesSonUnicas() {
        Set<Integer> vistas = new HashSet<>();
        for (Concepto concepto : Concepto.values()) {
            assertTrue(vistas.add(concepto.prioridad()),
                    "prioridad repetida en " + concepto + ": el orden dejaria de ser determinista");
        }
    }

    @Test
    @DisplayName("lo que urge va antes que lo que solo informa")
    void elOrdenRespetaElNivel() {
        List<Concepto> porPrioridad = Arrays.stream(Concepto.values())
                .sorted((a, b) -> Integer.compare(a.prioridad(), b.prioridad()))
                .toList();
        int ultimoAlto = -1;
        int primerInformativo = Integer.MAX_VALUE;
        for (int i = 0; i < porPrioridad.size(); i++) {
            NivelAtencion nivel = porPrioridad.get(i).nivelCuandoHay();
            if (nivel == NivelAtencion.ALTO) {
                ultimoAlto = i;
            }
            if (nivel == NivelAtencion.INFORMATIVO) {
                primerInformativo = Math.min(primerInformativo, i);
            }
        }
        assertTrue(ultimoAlto < primerInformativo,
                "hay un concepto ALTO despues de uno INFORMATIVO: " + porPrioridad);
    }

    @Test
    @DisplayName("con algo pendiente sube al nivel del concepto; en cero, SIN_PENDIENTES")
    void clasificaPorConteo() {
        assertEquals(NivelAtencion.ALTO,
                PoliticaComercial.clasificar(Concepto.SOLICITUD_POR_EVALUAR, 1));
        assertEquals(NivelAtencion.SIN_PENDIENTES,
                PoliticaComercial.clasificar(Concepto.SOLICITUD_POR_EVALUAR, 0));
        assertEquals(NivelAtencion.MEDIO,
                PoliticaComercial.clasificar(Concepto.CAPTACION_POR_REVISAR, 4));
    }

    @Test
    @DisplayName("un informativo en cero es INFORMATIVO, no 'todo al dia'")
    void unCeroInformativoNoEsBuenaNoticia() {
        assertEquals(NivelAtencion.INFORMATIVO,
                PoliticaComercial.clasificar(Concepto.VISITA_PENDIENTE, 0));
        assertEquals(NivelAtencion.INFORMATIVO,
                PoliticaComercial.clasificar(Concepto.CIERRE_REGISTRADO, 0));
    }

    /**
     * La cuarta copia del 7 vivia justo aqui, en un ternario de {@code
     * dashboard.ts}. Ahora la demora se compara contra la misma regla que
     * define el plazo, asi que no puede quedarse atras.
     */
    @Test
    @DisplayName("la demora preocupa cuando supera el plazo entero de recontacto")
    void clasificaLaDemoraContraElPlazo() {
        int plazo = PoliticaComercial.RECONTACTO.valor();
        assertEquals(NivelAtencion.INFORMATIVO,
                PoliticaComercial.clasificar(Concepto.DEMORA_DE_SEGUIMIENTO, plazo));
        assertEquals(NivelAtencion.MEDIO,
                PoliticaComercial.clasificar(Concepto.DEMORA_DE_SEGUIMIENTO, plazo + 1));
        assertNotEquals(NivelAtencion.SIN_PENDIENTES,
                PoliticaComercial.clasificar(Concepto.DEMORA_DE_SEGUIMIENTO, 0),
                "cero dias de atraso promedio significa que no hay atrasados, "
                        + "no que la demora este 'al dia'");
    }

    @Test
    @DisplayName("solo ALTO y MEDIO obligan a hacer algo")
    void requiereAtencion() {
        assertTrue(PoliticaComercial.requiereAtencion(NivelAtencion.ALTO));
        assertTrue(PoliticaComercial.requiereAtencion(NivelAtencion.MEDIO));
        assertFalse(PoliticaComercial.requiereAtencion(NivelAtencion.INFORMATIVO));
        assertFalse(PoliticaComercial.requiereAtencion(NivelAtencion.SIN_PENDIENTES));
    }
}
