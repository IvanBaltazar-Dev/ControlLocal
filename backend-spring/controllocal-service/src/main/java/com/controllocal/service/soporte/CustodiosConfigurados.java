package com.controllocal.service.soporte;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Los dos custodios de la recuperacion de emergencia (D-S0-51).
 *
 * <h2>Configuracion, no subsistema</h2>
 * Dos identificadores y dos hashes, <b>fuera de PostgreSQL</b>. No hay tabla de
 * custodios, ni altas, ni bajas, ni pruebas selladas: seria administracion de
 * identidades para gobernar dos secretos que cambian una vez al año, y la
 * vigencia de un custodio es un acta firmada, no estado de aplicacion.
 *
 * <p><b>Y hay una razon mas fuerte para que los hashes vivan fuera de la
 * base:</b> este mecanismo existe para rescatar una instalacion. Guardar sus
 * llaves dentro de lo que viene a rescatar es guardarlas dentro de la casa.
 *
 * <h2>Por que tambien el identificador</h2>
 * Sin el, «custodio A» seria una etiqueta de ranura y las tres desigualdades de
 * D-S0-52 (`operador <> custodio_a`, etc.) compararian textos que nunca chocan:
 * el {@code CHECK} pasaria siempre y no probaria nada.
 *
 * <p>El reemplazo es <b>de uno en uno</b>, manteniendo valida la otra ranura; el
 * procedimiento esta en {@code operacion/custodios-y-recuperacion-de-emergencia.md}.
 */
@Component
public class CustodiosConfigurados {

    private final String idA;
    private final String hashA;
    private final String idB;
    private final String hashB;

    public CustodiosConfigurados(
            @Value("${controllocal.recuperacion.custodio-a.id:}") String idA,
            @Value("${controllocal.recuperacion.custodio-a.hash:}") String hashA,
            @Value("${controllocal.recuperacion.custodio-b.id:}") String idB,
            @Value("${controllocal.recuperacion.custodio-b.hash:}") String hashB) {
        this.idA = normalizar(idA);
        this.hashA = hashA == null ? "" : hashA.trim();
        this.idB = normalizar(idB);
        this.hashB = hashB == null ? "" : hashB.trim();
    }

    /** Las dos ranuras completas y con identificadores distintos. */
    public boolean estanConfigurados() {
        return !idA.isEmpty() && !hashA.isEmpty()
                && !idB.isEmpty() && !hashB.isEmpty()
                && !idA.equals(idB);
    }

    public String identificadorA() {
        return idA;
    }

    public String identificadorB() {
        return idB;
    }

    /**
     * ¿Es este el secreto de ese custodio?
     *
     * <p>Devuelve vacio si el identificador no es ninguno de los dos <b>o</b> si
     * el secreto no cuadra: al que se equivoca no se le dice cual de las dos
     * cosas fallo. Distinguirlo convertiria la herramienta en un comprobador de
     * identificadores validos.
     */
    public Optional<String> verificar(String identificador, char[] secreto) {
        if (!estanConfigurados() || identificador == null || secreto == null || secreto.length == 0) {
            return Optional.empty();
        }
        String normalizado = normalizar(identificador);
        String esperado = normalizado.equals(idA) ? hashA : normalizado.equals(idB) ? hashB : null;
        if (esperado == null || !PasswordHasher.verificar(secreto, esperado)) {
            return Optional.empty();
        }
        return Optional.of(normalizado);
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
