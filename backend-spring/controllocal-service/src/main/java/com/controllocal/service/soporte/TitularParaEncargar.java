package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.TitularidadPropiedadRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

/**
 * <b>Conocer un inmueble no es poder venderlo</b> (V76).
 *
 * <p>El registro admite cero titularidades: se puede conocer legitimamente un
 * departamento de 90 m2 anunciado a 180 000 USD sin saber quien es el dueno, y
 * obligar a declararlo obligaria a inventarlo. Pero una <b>relacion comercial
 * nace de alguien que puede encargarla</b>, asi que la exigencia no desaparece:
 * se muda del alta al encargo.
 *
 * <h2>Por que vive aqui y no en cada servicio</h2>
 * Hay <b>tres</b> caminos por los que nace una fila de {@code captacion} —el
 * alta con operaciones, {@code POST /captaciones} y {@code captar} desde una
 * prospeccion— y hasta V76 ninguno comprobaba la titularidad: el sistema parecia
 * seguro solo porque el ALTA la exigia, que es exactamente el sitio del que esta
 * decision la retira. Con la regla repetida tres veces bastaria olvidarla en una
 * para que el gate no existiera; escrita una vez, el que abra el cuarto camino
 * la encuentra.
 *
 * <h2>Por que el gate es DEBIL, y a proposito</h2>
 * Pide <b>una titularidad vigente</b>. No cuotas al 100 %, no un representante
 * formal. Se midio lo que el negocio consume aguas abajo y es un nombre y un
 * interlocutor: {@code ComisionServiceImpl} no menciona al propietario en sus
 * 478 lineas, {@code ContratoServiceImpl} solo lo pinta, el reporte al
 * propietario ni siquiera lo resuelve, y la cuota no alimenta ningun reparto de
 * dinero. Exigir mas seria inventar una condicion que nadie usa — y cada
 * condicion inventada es una que alguien acabara rellenando a mano.
 */
@Component
public class TitularParaEncargar {

    private final TitularidadPropiedadRepository titularidades;

    public TitularParaEncargar(TitularidadPropiedadRepository titularidades) {
        this.titularidades = titularidades;
    }

    /**
     * @throws ReglaNegocioException si el inmueble no tiene ninguna titularidad
     *         vigente. El mensaje dice las dos cosas: que registrarlo asi es
     *         legitimo y que encargarlo no
     */
    public void exigirParaEncargo(Propiedad propiedad) {
        if (propiedad == null || propiedad.getId() == null) {
            return;
        }
        if (titularidades.vigentesDe(propiedad.getId()).isEmpty()) {
            throw new ReglaNegocioException(
                    "Esta propiedad no tiene titular conocido: registrarla asi es legitimo "
                            + "--BROX conoce inmuebles que no gestiona--, pero encargarla no. "
                            + "Sin titular no se sabe con quien se negocia ni quien autoriza el "
                            + "precio. Registra la titularidad antes de abrir el encargo.");
        }
    }
}
