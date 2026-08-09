package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.IntentoAcceso;
import com.controllocal.persistence.repositorio.IntentoAccesoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bloqueo por intentos fallidos por <b>cuenta e IP</b> (D-S0-21).
 *
 * <p>Lo que estas pruebas protegen no es el contador, sino las tres
 * propiedades que hacen que el bloqueo sirva de algo: que la dimension CUENTA
 * exista (un atacante con 50 IPs la necesitaba esquivar y no puede), que un
 * acierto limpie el contador, y que <b>nada de lo que responde revele si la
 * cuenta existe</b>.
 */
class BloqueoAccesosTest {

    private static final long ORG = 1L;

    private final IntentoAccesoRepository intentos = mock(IntentoAccesoRepository.class);
    private final BloqueoAccesos bloqueo = new BloqueoAccesos(intentos, 5, 10);

    // ------------------------------------------------------------------
    // 1. Las dos dimensiones
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("cuenta e IP son dimensiones independientes")
    class Dimensiones {

        @Test
        @DisplayName("la cuenta se bloquea aunque la IP cambie en cada intento")
        void laCuentaSeBloqueaAunqueCambieLaIp() {
            // Este es el ataque que el limitador anterior no frenaba: 50 IPs
            // distintas contra una sola cuenta.
            fallos(IntentoAcceso.CLAVE_CUENTA, 5);
            fallos(IntentoAcceso.CLAVE_IP, 0);

            BloqueoAccesos.Veredicto veredicto = bloqueo.permitir("vmora", "203.0.113.77");

            assertTrue(veredicto.bloqueado());
            assertEquals(IntentoAcceso.CLAVE_CUENTA, veredicto.dimension());
        }

        @Test
        @DisplayName("la IP se bloquea aunque pruebe una cuenta distinta cada vez")
        void laIpSeBloqueaAunqueCambieLaCuenta() {
            fallos(IntentoAcceso.CLAVE_CUENTA, 0);
            fallos(IntentoAcceso.CLAVE_IP, 10);

            BloqueoAccesos.Veredicto veredicto = bloqueo.permitir("otro", "203.0.113.77");

            assertTrue(veredicto.bloqueado());
            assertEquals(IntentoAcceso.CLAVE_IP, veredicto.dimension());
        }

        @Test
        @DisplayName("la cuenta se evalua ANTES que la IP")
        void laCuentaSeEvaluaPrimero() {
            fallos(IntentoAcceso.CLAVE_CUENTA, 5);
            fallos(IntentoAcceso.CLAVE_IP, 10);

            // Es la dimension que protege al usuario concreto y la que un
            // atacante distribuido no puede esquivar.
            assertEquals(IntentoAcceso.CLAVE_CUENTA, bloqueo.permitir("vmora", "1.2.3.4").dimension());
        }

        @Test
        @DisplayName("por debajo del umbral no se bloquea nada")
        void pordebajoDelUmbralNoSeBloquea() {
            fallos(IntentoAcceso.CLAVE_CUENTA, 4);
            fallos(IntentoAcceso.CLAVE_IP, 9);

            assertFalse(bloqueo.permitir("vmora", "1.2.3.4").bloqueado());
        }
    }

    // ------------------------------------------------------------------
    // 2. No revelar el padron de cuentas
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("no se revela si la cuenta existe")
    class SinOraculo {

        @Test
        @DisplayName("se cuenta un usuario INEXISTENTE igual que uno real")
        void seCuentaTambienElUsuarioInexistente() {
            bloqueo.registrar("no-existe-jamas", "1.2.3.4", false, ORG, "curl/8");

            // Si solo contaran las cuentas reales, bastaria observar quien se
            // bloquea para saber que nombres existen.
            ArgumentCaptor<IntentoAcceso> captor = ArgumentCaptor.forClass(IntentoAcceso.class);
            verify(intentos, times(2)).save(captor.capture());
            List<IntentoAcceso> guardados = captor.getAllValues();
            assertTrue(guardados.stream().anyMatch(i -> IntentoAcceso.CLAVE_CUENTA.equals(i.getClaveTipo())));
            assertTrue(guardados.stream().anyMatch(i -> IntentoAcceso.CLAVE_IP.equals(i.getClaveTipo())));
        }

        @Test
        @DisplayName("la tabla guarda el HASH, nunca el nombre de usuario")
        void seGuardaElHashYNoElUsuario() {
            bloqueo.registrar("vmora", "1.2.3.4", false, ORG, null);

            ArgumentCaptor<IntentoAcceso> captor = ArgumentCaptor.forClass(IntentoAcceso.class);
            verify(intentos, times(2)).save(captor.capture());
            IntentoAcceso cuenta = captor.getAllValues().stream()
                    .filter(i -> IntentoAcceso.CLAVE_CUENTA.equals(i.getClaveTipo()))
                    .findFirst().orElseThrow();

            // Si no, la tabla seria un padron de los usuarios que alguien
            // probo: exactamente el dato que un atacante querria robar.
            assertNotEquals("vmora", cuenta.getClaveValorHash());
            assertEquals(64, cuenta.getClaveValorHash().length(), "SHA-256 en hexadecimal");
        }

        @Test
        @DisplayName("el usuario se normaliza: 'VMora ' y 'vmora' son la misma cuenta")
        void elUsuarioSeNormaliza() {
            assertEquals(BloqueoAccesos.hashear("vmora"),
                    hashDeCuentaAlRegistrar("  VMora "));
        }
    }

    // ------------------------------------------------------------------
    // 3. Ventana, limpieza y progresividad
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("ventana y progresividad")
    class Ventana {

        @Test
        @DisplayName("un login correcto limpia el contador SIN borrar filas")
        void unAciertoLimpiaElContador() {
            OffsetDateTime exito = OffsetDateTime.now().minusMinutes(2);
            when(intentos.ultimoExitoDesde(eq(IntentoAcceso.CLAVE_CUENTA), anyString(), any()))
                    .thenReturn(exito);
            when(intentos.contarFallosDesde(eq(IntentoAcceso.CLAVE_CUENTA), anyString(), eq(exito)))
                    .thenReturn(1L);
            fallos(IntentoAcceso.CLAVE_IP, 0);

            assertFalse(bloqueo.permitir("vmora", "1.2.3.4").bloqueado());

            // El registro sigue siendo append-only: lo que cambia es DESDE
            // donde se lee, no que se borre nada.
            verify(intentos, never()).delete(any());
            verify(intentos).contarFallosDesde(IntentoAcceso.CLAVE_CUENTA,
                    BloqueoAccesos.hashear("vmora"), exito);
        }

        @Test
        @DisplayName("la espera escala con los fallos y no al reves")
        void laEsperaEscala() {
            assertEquals(0, BloqueoAccesos.esperaSegundos(4));
            assertEquals(60, BloqueoAccesos.esperaSegundos(5));
            assertEquals(5 * 60, BloqueoAccesos.esperaSegundos(10));
            assertEquals(15 * 60, BloqueoAccesos.esperaSegundos(15));
            assertEquals(15 * 60, BloqueoAccesos.esperaSegundos(20));
            // Sin escalado, o se molesta al usuario legitimo o no se frena al
            // atacante: por eso hay cuatro peldanos y no un umbral unico.
        }

        @Test
        @DisplayName("a partir de 20 fallos el desbloqueo deja de ser esperar")
        void veinteFallosExigenDesbloqueoAdministrativo() {
            fallos(IntentoAcceso.CLAVE_CUENTA, 20);
            fallos(IntentoAcceso.CLAVE_IP, 0);

            assertTrue(bloqueo.permitir("vmora", "1.2.3.4").exigeDesbloqueoAdministrativo());
        }

        @Test
        @DisplayName("19 fallos todavia se resuelven esperando")
        void diecinueveFallosNoExigenAdministrador() {
            fallos(IntentoAcceso.CLAVE_CUENTA, 19);
            fallos(IntentoAcceso.CLAVE_IP, 0);

            BloqueoAccesos.Veredicto veredicto = bloqueo.permitir("vmora", "1.2.3.4");
            assertTrue(veredicto.bloqueado());
            assertFalse(veredicto.exigeDesbloqueoAdministrativo());
        }
    }

    // ------------------------------------------------------------------
    // 4. Bordes que no pueden tumbar el servicio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sin usuario solo se evalua la IP; no se inventa un bloqueo")
    void sinUsuarioSoloSeEvaluaLaIp() {
        fallos(IntentoAcceso.CLAVE_IP, 0);

        assertFalse(bloqueo.permitir(null, "1.2.3.4").bloqueado());

        // Un cuerpo malformado no puede convertirse en un bloqueo fantasma.
        verify(intentos, never()).contarFallosDesde(eq(IntentoAcceso.CLAVE_CUENTA), anyString(), any());
    }

    @Test
    @DisplayName("sin IP resoluble no se bloquea por IP: un proxy mal puesto no es una caida")
    void sinIpNoSeBloqueaPorIp() {
        fallos(IntentoAcceso.CLAVE_CUENTA, 0);

        assertFalse(bloqueo.permitir("vmora", null).bloqueado());
        verify(intentos, never()).contarFallosDesde(eq(IntentoAcceso.CLAVE_IP), anyString(), any());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void fallos(String claveTipo, long cuantos) {
        when(intentos.ultimoExitoDesde(eq(claveTipo), anyString(), any())).thenReturn(null);
        when(intentos.contarFallosDesde(eq(claveTipo), anyString(), any())).thenReturn(cuantos);
    }

    private String hashDeCuentaAlRegistrar(String usuario) {
        IntentoAccesoRepository repo = mock(IntentoAccesoRepository.class);
        new BloqueoAccesos(repo, 5, 10).registrar(usuario, "1.2.3.4", false, ORG, null);
        ArgumentCaptor<IntentoAcceso> captor = ArgumentCaptor.forClass(IntentoAcceso.class);
        verify(repo, times(2)).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(i -> IntentoAcceso.CLAVE_CUENTA.equals(i.getClaveTipo()))
                .findFirst().orElseThrow().getClaveValorHash();
    }
}
