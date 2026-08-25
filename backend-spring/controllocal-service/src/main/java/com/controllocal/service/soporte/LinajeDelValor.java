package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.OpcionDeRastro;
import com.controllocal.domain.auditoria.RastroValorGobernado;
import com.controllocal.persistence.repositorio.OpcionDeRastroRepository;
import com.controllocal.persistence.repositorio.RastroValorGobernadoRepository;
import com.controllocal.service.Actor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <b>El unico sitio que escribe linaje</b> (4.P, V83).
 *
 * <h2>Que resuelve</h2>
 * Que de cualquier valor gobernado se pueda responder <i>«¿de donde salio ESTO,
 * exactamente?»</i> — y que la respuesta siga estando ahi despues de editarlo o
 * de borrarlo.
 *
 * <h2>Las tres escrituras, y por que son tres y no una</h2>
 * <pre>
 *   ALTA      no habia valor  -> hay uno
 *   EDICION   habia uno       -> hay otro, y el que habia queda CONSTATADO
 *   RETIRADA  habia uno       -> no hay ninguno, y el que habia queda CONSTATADO
 * </pre>
 *
 * <p>Un solo metodo con un {@code verbo} por parametro habria dejado que un
 * llamante escribiera una edicion sin decir que encontro, que es exactamente el
 * dato que este corte existe para no perder.
 *
 * <h2>Lo que se afirma del valor HALLADO, y lo que no</h2>
 * Se afirma <b>una sola cosa</b>: que en el momento de esta escritura, ahi habia
 * eso. Es una <b>constatacion del estado hallado</b> — la presencia la propia
 * operacion — y <b>no</b> una genesis: no se le atribuye canal, ni actor, ni
 * fecha de nacimiento, ni naturaleza.
 *
 * <p>La diferencia importa sobre todo en las cuatro claves {@code ESTRUCTURAL},
 * cuyo valor historico vive en una columna de {@code propiedad} sin fecha propia
 * ni autor conocido: al modificarse por primera vez tras el cutover, el linaje
 * conserva las dos cosas —el valor que el Core encontro y el nuevo con su
 * procedencia completa— sin inventar nada del primero.
 *
 * <h2>Misma transaccion</h2>
 * El linaje se escribe con el valor, no despues. Si la transaccion falla no
 * queda un linaje contando algo que nunca ocurrio; si tiene exito, esta
 * garantizado sin dos fases ni cola externa. Es la misma razon por la que
 * {@code evento_dominio} se escribe ahi mismo — y son <b>dos preguntas
 * distintas</b>: aquel dice como ocurrio una operacion, esto dice de donde sale
 * una afirmacion.
 *
 * <h2>Lo que este componente NO garantiza</h2>
 * Que nadie escriba un valor por SQL directo. Seis suites E2E y
 * {@code gate-modelo-universal.sql} lo hacen a proposito —para probar los
 * triggers de la base intentando romperlos—, asi que la procedencia <b>no puede
 * ser NOT NULL</b> en las cuatro tablas de valor. La invariante se sostiene en
 * la <b>frontera del servicio</b>, y lo vigila {@code LinajeDeTodaEscrituraTest}.
 */
@Component
public class LinajeDelValor {

    private final RastroValorGobernadoRepository rastros;
    private final OpcionDeRastroRepository opciones;

    public LinajeDelValor(RastroValorGobernadoRepository rastros,
                          OpcionDeRastroRepository opciones) {
        this.rastros = rastros;
        this.opciones = opciones;
    }

    /**
     * La clave no tenia valor y ahora lo tiene.
     *
     * @param escrito el valor que queda. Nunca {@code null}: un alta que no
     *                escribe nada no es un alta
     */
    public RastroValorGobernado anotarAlta(Actor actor, ProcedenciaDelValor procedencia,
                                           String sujeto, long idAgregado, String clave,
                                           ValorLogico escrito) {
        return anotar(actor, procedencia, sujeto, idAgregado, clave,
                RastroValorGobernado.VERBO_ALTA, null, escrito);
    }

    /**
     * La clave cambia de valor.
     *
     * @param hallado lo que el Core encontro. {@code null} cuando la autoridad
     *                estaba vacia —el caso de una clave que se escribe por
     *                primera vez dentro de una edicion— o cuando el valor
     *                anterior era un conjunto, que no cabe en un escalar y viaja
     *                en {@code rastro_valor_opcion}
     */
    public RastroValorGobernado anotarEdicion(Actor actor, ProcedenciaDelValor procedencia,
                                              String sujeto, long idAgregado, String clave,
                                              ValorLogico hallado, ValorLogico escrito) {
        return anotar(actor, procedencia, sujeto, idAgregado, clave,
                RastroValorGobernado.VERBO_EDICION, hallado, escrito);
    }

    /**
     * La clave se queda sin valor vigente, <b>y su historia no</b>.
     *
     * <p>Es la superficie que decide la forma del modelo: la fila de
     * {@code atributo_propiedad} desaparece, asi que el linaje no puede colgar de
     * su id. Cuelga de la clave logica, y por eso una clave puede quedar
     * <b>con linaje y sin valor</b> — que es exactamente lo que hay que poder
     * decir.
     */
    public RastroValorGobernado anotarRetirada(Actor actor, ProcedenciaDelValor procedencia,
                                               String sujeto, long idAgregado, String clave,
                                               ValorLogico hallado) {
        return anotar(actor, procedencia, sujeto, idAgregado, clave,
                RastroValorGobernado.VERBO_RETIRADA, hallado, null);
    }

    // ------------------------------------------------------------------

    private RastroValorGobernado anotar(Actor actor, ProcedenciaDelValor procedencia, String sujeto,
                                        long idAgregado, String clave, String verbo,
                                        ValorLogico hallado, ValorLogico escrito) {
        Procedencia acto = procedencia.acto();
        RastroValorGobernado rastro = new RastroValorGobernado(
                actor.idOrganizacion(), sujeto, idAgregado, clave, verbo);

        boolean multivalor = (escrito != null && escrito.esMultivalor())
                || (hallado != null && hallado.esMultivalor());
        if (escrito == null || escrito.esMultivalor()) {
            rastro.conValor(null, null, null, null, null, multivalor);
        } else {
            rastro.conValor(escrito.texto(), escrito.numero(), escrito.booleano(),
                    escrito.fecha(), escrito.moneda(), false);
        }
        if (hallado != null && !hallado.esMultivalor()) {
            rastro.hallando(hallado.texto(), hallado.numero(), hallado.booleano(),
                    hallado.fecha(), hallado.moneda());
        }

        rastro.porDondeEntro(acto.canal(), acto.agente(), acto.modelo(), acto.modeloVersion(),
                        acto.conversacionId(), acto.turnoId(), acto.mensajeId(), acto.peticion(),
                        acto.herramienta())
                // La naturaleza viaja TAL CUAL llego. Aqui no hay ningun `if` que
                // mire el canal, el actor ni la herramienta para rellenarla: si
                // el productor no la declaro, se queda ausente.
                .deNaturaleza(procedencia.naturaleza(), procedencia.observadoEn(),
                        procedencia.evidenciaRef(), procedencia.confianza())
                .porActor(actor.idRolOperativo(), actor.tipoRolOperativo());

        rastros.save(rastro);
        guardarConjunto(rastro, OpcionDeRastro.HALLADO, hallado);
        guardarConjunto(rastro, OpcionDeRastro.ESCRITO, escrito);
        return rastro;
    }

    /**
     * El conjunto entero, no la diferencia.
     *
     * <p>Guardar «se quito CASETA_24H» no permite reconstruir que habia cuando
     * el conjunto anterior es legado y nadie lo escribio nunca. Con los dos
     * conjuntos completos, la pregunta «que decia esta ficha en marzo» se
     * contesta leyendo una fila.
     */
    private void guardarConjunto(RastroValorGobernado rastro, String momento, ValorLogico valor) {
        if (valor == null || !valor.esMultivalor()) {
            return;
        }
        List<String> elementos = valor.valores();
        for (String elemento : elementos) {
            opciones.save(new OpcionDeRastro(rastro.getOrganizacionId(), rastro.getId(),
                    momento, elemento));
        }
    }
}
