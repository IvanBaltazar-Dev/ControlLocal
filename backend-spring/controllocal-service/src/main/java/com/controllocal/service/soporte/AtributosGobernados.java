package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.RastroValorGobernado;
import com.controllocal.domain.inmueble.AtributoPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.ValorMultipleAtributo;
import com.controllocal.persistence.repositorio.AtributoPropiedadRepository;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.persistence.repositorio.ValorMultipleAtributoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * <b>El catalogo decide que se pregunta; esto lo aplica.</b>
 *
 * <h2>La regla que sostiene el motor de captura (D-E4-2)</h2>
 * Ninguna pantalla tiene una lista de campos escrita a mano. Lo que se pregunta
 * de un terreno sale de {@code catalogo_atributo}, igual que lo que se pregunta
 * de un departamento; anadir "Almacen" no anade un formulario, anade filas.
 *
 * <pre>
 *   TERRENO      -> area de terreno, frente, zonificacion   (no dormitorios)
 *   DEPARTAMENTO -> area, dormitorios, banos, piso, mantenimiento
 * </pre>
 *
 * <p>Angular <b>representa</b> la pregunta; no la decide. KAIROS hara
 * exactamente lo mismo. Si la regla viviera en el cliente habria dos copias
 * divergiendo desde el primer dia, y la tercera copia llegaria con el canal de
 * WhatsApp.
 *
 * <h2>Que hace con los valores</h2>
 * Los valores llegan como texto —de un formulario, de un JSON o de una frase
 * dictada— y el catalogo dice de que tipo son. La conversion se hace <b>aqui</b>
 * y no en el cliente: es la misma razon por la que el tipo esta gobernado.
 *
 * <p>Un valor que no encaja con su tipo se rechaza con el nombre del atributo
 * delante. El trigger {@code tg_atributo_gobernado} (V48) lo rechazaria
 * igualmente, pero con un mensaje de PostgreSQL a mitad de una transaccion, y
 * eso no se le puede ensenar a nadie.
 */
@Component
public class AtributosGobernados {

    private final CatalogoAtributoRepository catalogo;
    private final AtributoPropiedadRepository valores;
    private final ValorMultipleAtributoRepository multivalores;
    private final LinajeDelValor linaje;

    public AtributosGobernados(CatalogoAtributoRepository catalogo,
                               AtributoPropiedadRepository valores,
                               ValorMultipleAtributoRepository multivalores,
                               LinajeDelValor linaje) {
        this.catalogo = catalogo;
        this.valores = valores;
        this.multivalores = multivalores;
        this.linaje = linaje;
    }

    /** Lo que se pregunta para un tipo de propiedad, en orden de presentacion. */
    public List<CatalogoAtributo> aplicablesA(long idOrganizacion, String tipoPropiedad) {
        return catalogo.aplicablesA(idOrganizacion, tipoPropiedad);
    }

    /**
     * Las claves obligatorias de ese tipo. Es lo que el motor de captura
     * compara con lo que ya sabe para decir que falta, <b>antes</b> de intentar
     * guardar.
     */
    public List<String> obligatoriasDe(long idOrganizacion, String tipoPropiedad) {
        return aplicablesA(idOrganizacion, tipoPropiedad).stream()
                .filter(atributo -> atributo.esRequeridoPara(tipoPropiedad))
                .map(CatalogoAtributo::getClave)
                .toList();
    }

    /** Lo que le falta a una propiedad ya escrita. Consulta de V48, sobre su indice. */
    public List<String> obligatoriasQueFaltan(long idOrganizacion, long idPropiedad,
                                              String tipoPropiedad) {
        return valores.clavesObligatoriasQueFaltan(idOrganizacion, idPropiedad, tipoPropiedad);
    }

    /**
     * Lo que le falta, mirando <b>las dos autoridades</b>.
     *
     * <p>La consulta de arriba solo ve las claves gobernadas: una declarada
     * ESTRUCTURAL no deja fila en {@code atributo_propiedad}, asi que buscarla
     * alli la daria por faltante en todas las propiedades. Aqui se anaden las
     * estructurales cuyo campo canonico este vacio, que es donde de verdad
     * viven (D-E4-3).
     */
    public List<String> obligatoriasQueFaltan(long idOrganizacion, Propiedad propiedad) {
        String tipo = propiedad.getTipoInmueble();
        List<String> faltan = new ArrayList<>(
                valores.clavesObligatoriasQueFaltan(idOrganizacion, propiedad.getId(), tipo));

        for (CatalogoAtributo definicion : aplicablesA(idOrganizacion, tipo)) {
            if (definicion.esEstructural()
                    && definicion.esRequeridoPara(tipo)
                    && !EscritorEstructural.tieneValor(propiedad, definicion.getCampoEstructural())) {
                faltan.add(definicion.getClave());
            }
        }
        return List.copyOf(faltan);
    }

    /**
     * <b>Lo que le falta a una propiedad para poder ANUNCIARSE</b> (V72).
     *
     * <p>ALT y PUB, mirando las dos autoridades igual que
     * {@link #obligatoriasQueFaltan(long, Propiedad)}. Es la unica pregunta que
     * debe hacer el caso de uso de publicacion: si cada controlador o cada
     * pantalla volviera a interpretar los tres niveles, en dos cortes habria
     * tres interpretaciones -- y una regla con tres duenos no es una regla.
     *
     * <p>Que devuelva una lista vacia significa «se puede publicar». Que
     * devuelva claves significa que faltan, <b>con su nombre</b>, para que el
     * mensaje diga que hacer en vez de que no se puede.
     */
    public List<String> faltantesDePropiedadParaPublicar(long idOrganizacion, Propiedad propiedad) {
        String tipo = propiedad.getTipoInmueble();
        List<String> faltan = new ArrayList<>(
                valores.clavesQueImpidenPublicar(idOrganizacion, propiedad.getId(), tipo));

        for (CatalogoAtributo definicion : aplicablesA(idOrganizacion, tipo)) {
            if (definicion.esEstructural()
                    && definicion.bloqueaPublicacionPara(tipo)
                    && !EscritorEstructural.tieneValor(propiedad, definicion.getCampoEstructural())) {
                faltan.add(definicion.getClave());
            }
        }
        return List.copyOf(faltan);
    }

    /** Los rotulos de esas claves, para poder decirlo en palabras y no en claves. */
    public List<String> rotulosDe(long idOrganizacion, String tipoPropiedad, List<String> claves) {
        if (claves.isEmpty()) {
            return List.of();
        }
        Map<String, CatalogoAtributo> porClave = definicionesDe(idOrganizacion, tipoPropiedad);
        return claves.stream()
                .map(clave -> porClave.containsKey(clave) ? porClave.get(clave).getRotulo() : clave)
                .toList();
    }

    /**
     * Comprueba que la clave existe, que aplica a ese tipo y que el valor
     * encaja con su tipo de dato. Devuelve el atributo listo para guardar.
     *
     * <p>Se hace en este orden a proposito: "esa clave no existe" y "esa clave
     * no aplica a un terreno" son errores distintos y se arreglan distinto.
     */
    private AtributoPropiedad convertir(long idOrganizacion, long idPropiedad, String tipoPropiedad,
                                       String clave, String valor, String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, tipoPropiedad);
        String limpio = exigirValor(clave, valor);

        return switch (definicion.tipo()) {
            case ENTERO -> AtributoPropiedad.deNumero(
                    idOrganizacion, idPropiedad, clave, enRango(definicion, entero(clave, limpio)));
            case DECIMAL -> AtributoPropiedad.deNumero(
                    idOrganizacion, idPropiedad, clave, enRango(definicion, decimal(clave, limpio)));
            case IMPORTE -> AtributoPropiedad.deImporte(
                    idOrganizacion, idPropiedad, clave, enRango(definicion, decimal(clave, limpio)),
                    exigirMoneda(clave, moneda));
            case BOOLEANO -> AtributoPropiedad.deBooleano(
                    idOrganizacion, idPropiedad, clave, booleano(clave, limpio));
            case FECHA -> AtributoPropiedad.deFecha(
                    idOrganizacion, idPropiedad, clave, fecha(clave, limpio));
            case TEXTO, LISTA -> AtributoPropiedad.deTexto(
                    idOrganizacion, idPropiedad, clave, enLongitud(definicion, limpio));
            // Un multivalor no se construye con un valor suelto: su fila es un
            // ancla y sus valores viven aparte. Quien llegue aqui con uno esta
            // usando la puerta equivocada, y decirselo es mas util que guardar
            // el primero y perder los demas.
            case LISTA_MULTIPLE -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" admite varios valores: se guarda con la via de "
                            + "multivalor, no con un valor suelto.");
        };
    }

    /**
     * El ancla de un multivalor, con sus valores.
     *
     * <p>Va aparte de {@link #convertir} porque no es el mismo acto: alli se
     * escribe UN valor, aqui se sustituye un CONJUNTO. Y sustituir es lo
     * correcto -- anadir dejaria sin forma de quitar una opcion, que es la
     * mitad de lo que significa editar una lista.
     */
    private AtributoPropiedad convertirMultivalor(long idOrganizacion, long idPropiedad,
                                                 String tipoPropiedad, String clave) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, tipoPropiedad);
        if (!definicion.tipo().esMultivalor()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" no admite varios valores.");
        }
        return AtributoPropiedad.anclaDeMultivalor(idOrganizacion, idPropiedad, clave);
    }

    /**
     * Solo comprueba que el VALOR encaja con el tipo de dato de su clave, sin
     * mirar a qué tipo de propiedad aplica.
     *
     * <p>Existe por el motor de captura: alguien puede dictar
     * <i>«tres dormitorios»</i> <b>antes</b> de decir que es un departamento.
     * Ahí no se puede comprobar la aplicabilidad —todavía no se sabe de qué tipo
     * es la propiedad— y rechazarlo con «no aplica a una propiedad de tipo OTRO»
     * sería un mensaje falso sobre un dato correcto. La aplicabilidad se
     * comprueba en cuanto el tipo se conoce, y otra vez al guardar.
     */
    public void exigirValorCompatible(long idOrganizacion, String clave, String valor) {
        ConversionDeValores.exigirCompatible(definicionDe(idOrganizacion, clave), valor);
    }

    // ------------------------------------------------------------------
    // Convertir un texto en un valor tipado NO depende del sujeto: un entero es
    // un entero lo lleve una propiedad o un encargo, y las monedas que existen
    // son las mismas. Eso vive entero en ConversionDeValores desde V73.
    //
    // Copiarlo en el enrutador del encargo habria creado dos definiciones de la
    // misma regla, y habrian divergido en el primer arreglo hecho en una sola.
    // Aqui quedan los atajos para que el codigo de enrutamiento --que si
    // depende del sujeto-- se lea seguido.
    // ------------------------------------------------------------------

    private static LocalDate fecha(String clave, String valor) {
        return ConversionDeValores.fecha(clave, valor);
    }

    private static String exigirMoneda(String clave, String moneda) {
        return ConversionDeValores.exigirMoneda(clave, moneda);
    }

    private static String enLongitud(CatalogoAtributo definicion, String valor) {
        return ConversionDeValores.enLongitud(definicion, valor);
    }

    private static void exigirQueAplique(CatalogoAtributo definicion, String tipoPropiedad) {
        if (!definicion.aplicaA(tipoPropiedad)) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" no aplica a una propiedad de tipo "
                            + nombreDelTipo(tipoPropiedad) + ". Los atributos de cada tipo salen del "
                            + "catalogo, no del formulario.");
        }
    }

    private static String exigirValor(String clave, String valor) {
        return ConversionDeValores.exigirValor(clave, valor);
    }

    /**
     * <b>Lo que se le exige a un valor ANTES de escribirlo en su campo
     * canonico</b> (V79).
     *
     * <p>Hasta este corte el camino estructural solo comprobaba que el valor no
     * llegara vacio, y la conversion la hacia {@code EscritorEstructural} al
     * asignarlo. Con dos conceptos estructurales numericos y de texto libre eso
     * bastaba; con el primero de tipo LISTA deja de bastar, porque la
     * pertenencia al vocabulario vivia <b>solo</b> dentro del trigger de
     * {@code atributo_propiedad}, por donde un valor estructural no pasa.
     *
     * <p>Se apoya en {@link ConversionDeValores}, que es exactamente la mitad
     * que <b>no depende del sujeto</b>: un entero es un entero y un vocabulario
     * es un vocabulario, lo lleve una fila o una columna. Asi el camino
     * estructural y el gobernado exigen lo mismo, que es lo unico que hace
     * cierta la promesa de D-E4-3 — <i>la autoridad fisica cambia, el contrato
     * logico no</i>.
     */
    private static String valorEstructural(CatalogoAtributo definicion, String valor) {
        String limpio = exigirValor(definicion.getClave(), valor);
        ConversionDeValores.exigirCompatible(definicion, limpio);
        return ConversionDeValores.exigirDelVocabulario(definicion, limpio);
    }

    /**
     * Cambia el valor de un atributo que ya existe, respetando su tipo. Se
     * separa de {@link #convertir} porque actualizar y crear no son lo mismo
     * para JPA: reemplazar la fila perderia {@code fecha_creacion}, que es el
     * dato que dice desde cuando se sabe eso de la propiedad.
     */
    private void actualizar(long idOrganizacion, AtributoPropiedad existente, String valor,
                           String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, existente.getClave());
        String clave = existente.getClave();
        String limpio = valor == null ? null : valor.trim();
        if (limpio == null || limpio.isEmpty()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" llego sin valor. Para quitarlo, borralo; "
                            + "no se guarda vacio.");
        }
        switch (definicion.tipo()) {
            case ENTERO ->
                    existente.cambiarANumero(enRango(definicion, entero(clave, limpio)));
            case DECIMAL ->
                    existente.cambiarANumero(enRango(definicion, decimal(clave, limpio)));
            case IMPORTE -> existente.cambiarAImporte(
                    enRango(definicion, decimal(clave, limpio)), exigirMoneda(clave, moneda));
            case BOOLEANO -> existente.cambiarABooleano(booleano(clave, limpio));
            case FECHA -> existente.cambiarAFecha(fecha(clave, limpio));
            case TEXTO, LISTA -> existente.cambiarATexto(enLongitud(definicion, limpio));
            case LISTA_MULTIPLE -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" admite varios valores: se edita con la via de "
                            + "multivalor, no con un valor suelto.");
        }
    }

    // ==================================================================
    // Escritura con linaje (4.P)
    //
    // <b>Escribe un valor donde su AUTORIDAD diga</b> (D-E4-3, paso 4):
    //
    //   CampoCaptura
    //     +- destino = ATRIBUTO     -> AtributoPropiedad
    //     +- destino = ESTRUCTURAL  -> campoEstructural -> EscritorEstructural
    //
    // Los dos caminos son MUTUAMENTE EXCLUYENTES: un valor no se escribe nunca
    // en los dos sitios. Escribirlo en ambos era exactamente la doble verdad
    // que D-E4-3 vino a cerrar.
    //
    // Las TRES superficies de escritura de la PROPIEDAD entran por aqui, y por
    // aqui salen con su procedencia: escalar (atributo o campo canonico),
    // multivalor y retirada. El caso de uso ya no toca `atributo_propiedad` ni
    // `atributo_propiedad_opcion`: si lo hiciera, habria un camino por el que un
    // valor se escribe sin dejar de donde salio, y esa es exactamente la fuga
    // que 4.P cierra.
    // ==================================================================

    /**
     * <b>Los estructurales del ALTA, aplicados ANTES del primer save</b>, que es
     * lo unico que este metodo hace.
     *
     * <p>No anota linaje, y no puede: {@code propiedad.metraje} es NOT NULL, asi
     * que el valor tiene que estar puesto antes del {@code save} — y antes del
     * {@code save} la propiedad <b>no tiene id</b>, que es justo la coordenada
     * por la que se direcciona el rastro. Las dos exigencias son ciertas y
     * apuntan en direcciones contrarias, asi que el alta ocurre en dos tiempos.
     *
     * <p>El segundo tiempo es {@link #escribirAlAlta}, que anota el linaje de
     * <b>todos</b> los valores del alta, incluidos estos. Llamar a este metodo y
     * no al otro dejaria un valor escrito sin procedencia; es la unica excepcion
     * declarada del gate {@code LinajeDeTodaEscrituraTest}, y esta ahi con su
     * motivo para que se vea, no para que se olvide.
     */
    public void aplicarEstructuralesAlAlta(long idOrganizacion, Propiedad propiedad,
                                           List<ValorEntrante> entrantes) {
        for (ValorEntrante entrante : entrantes) {
            CatalogoAtributo definicion = definicionDe(idOrganizacion, entrante.clave());
            if (definicion.esEstructural()) {
                // La aplicabilidad se comprueba AQUI, y hasta la tercera vuelta
                // de 4.P no se comprobaba en ninguna parte del alta.
                //
                // El camino gobernado la exigia dentro de `convertir`, pero el
                // ESTRUCTURAL no pasa por ahi -- no crea fila --, asi que una
                // CASA se podia REGISTRAR con un `piso` que despues no se podia
                // EDITAR nunca: `escribirEnEdicion` si lo rechazaba. Un dato que
                // entra y ya no se puede corregir es peor que uno rechazado.
                exigirQueAplique(definicion, propiedad.getTipoInmueble());
                EscritorEstructural.aplicar(propiedad, definicion.getCampoEstructural(),
                        valorEstructural(definicion, entrante.valor()), entrante.clave());
            }
        }
    }

    /**
     * El ALTA de una propiedad: escribe lo gobernado y anota el linaje de todo.
     *
     * <p>Todo es {@code ALTA} por construccion —la propiedad acaba de nacer, asi
     * que no habia nada que hallar— y por eso este metodo no lee el estado
     * anterior de nada. Los estructurales ya estan aplicados sobre el agregado
     * ({@link #aplicarEstructuralesAlAlta}); aqui solo reciben su fila de
     * rastro, que es lo que hace que una clave que NO CREA FILA en
     * {@code atributo_propiedad} tenga linaje igual que las demas.
     */
    public void escribirAlAlta(Actor actor, Propiedad propiedad, List<ValorEntrante> entrantes) {
        for (ValorEntrante entrante : entrantes) {
            if (entrante.esMultivalor()) {
                escribirMultivalor(actor, propiedad, entrante);
                continue;
            }
            CatalogoAtributo definicion = definicionDe(actor.idOrganizacion(), entrante.clave());
            ValorLogico escrito;
            if (definicion.esEstructural()) {
                // Gemela de la de `aplicarEstructuralesAlAlta`: las dos mitades
                // del alta en dos tiempos tienen que exigir lo mismo, o la que
                // no exija se convierte en la puerta permisiva.
                exigirQueAplique(definicion, propiedad.getTipoInmueble());
                escrito = EscritorEstructural.leerValor(
                        propiedad, definicion.getCampoEstructural());
            } else {
                AtributoPropiedad fila = valores.save(convertir(actor.idOrganizacion(),
                        propiedad.getId(), propiedad.getTipoInmueble(), entrante.clave(),
                        entrante.valor(), entrante.moneda()));
                escrito = ValorLogico.deFila(fila, null);
            }
            linaje.anotarAlta(actor, entrante.procedencia(),
                    RastroValorGobernado.SUJETO_PROPIEDAD, propiedad.getId(), entrante.clave(),
                    escrito);
        }
    }

    /**
     * <b>Una escritura sobre una propiedad que ya existe.</b>
     *
     * <p>Lee la autoridad <b>antes</b> de pisarla —sea una fila o un campo
     * canonico— y ese hallazgo viaja al linaje. Es lo que convierte una edicion
     * en algo reconstruible: la fila vigente sigue siendo una sola, y el valor
     * que habia deja de perderse.
     *
     * <p>Con un valor legado —anterior al cutover de V83, sin linaje propio—
     * esto es lo unico que se puede afirmar de el con verdad: <i>«en el momento
     * de esta edicion, el Core encontro esto»</i>. No se le pone fecha de
     * nacimiento, ni autor, ni canal, ni naturaleza. Es la diferencia entre
     * constatar y fechar.
     */
    public void escribirEnEdicion(Actor actor, Propiedad propiedad, ValorEntrante entrante) {
        if (entrante.esMultivalor()) {
            escribirMultivalor(actor, propiedad, entrante);
            return;
        }
        long idOrganizacion = actor.idOrganizacion();
        CatalogoAtributo definicion = definicionDe(idOrganizacion, entrante.clave());
        exigirQueAplique(definicion, propiedad.getTipoInmueble());

        ValorLogico hallado;
        ValorLogico escrito;
        if (definicion.esEstructural()) {
            hallado = EscritorEstructural.leerValor(propiedad, definicion.getCampoEstructural());
            EscritorEstructural.aplicar(propiedad, definicion.getCampoEstructural(),
                    valorEstructural(definicion, entrante.valor()), entrante.clave());
            escrito = EscritorEstructural.leerValor(propiedad, definicion.getCampoEstructural());
        } else {
            AtributoPropiedad existente = valores
                    .findByIdPropiedadAndClave(propiedad.getId(), entrante.clave())
                    .orElse(null);
            // La foto se toma ANTES de actualizar: `actualizar` muta la misma
            // entidad, asi que leerla despues devolveria el valor nuevo dos
            // veces y la historia diria que no cambio nada.
            hallado = existente == null ? null : ValorLogico.deFila(existente, null);
            AtributoPropiedad fila = existente != null ? existente
                    : convertir(idOrganizacion, propiedad.getId(), propiedad.getTipoInmueble(),
                            entrante.clave(), entrante.valor(), entrante.moneda());
            if (existente != null) {
                actualizar(idOrganizacion, existente, entrante.valor(), entrante.moneda());
            }
            escrito = ValorLogico.deFila(valores.save(fila), null);
        }
        anotarSegunHabia(actor, entrante, propiedad.getId(), hallado, escrito);
    }

    /**
     * <b>Un multivalor: su ancla y sus valores, sustituyendo.</b>
     *
     * <p>Sustituir y no anadir es toda la decision: sin borrar antes no habria
     * forma de QUITAR una opcion, y quitar es la mitad de lo que significa
     * editar una lista.
     *
     * <p>Y ese borrado es lo que destruia el conjunto anterior. Desde 4.P se lee
     * <b>entero</b> antes de borrarlo y viaja al linaje como conjunto, no como
     * diferencia: «se quito CASETA_24H» no permite reconstruir que habia si el
     * conjunto anterior era legado y nadie lo escribio nunca.
     */
    private void escribirMultivalor(Actor actor, Propiedad propiedad, ValorEntrante entrante) {
        AtributoPropiedad ancla = valores
                .findByIdPropiedadAndClave(propiedad.getId(), entrante.clave())
                .orElse(null);
        boolean existia = ancla != null;
        if (!existia) {
            ancla = valores.save(convertirMultivalor(actor.idOrganizacion(), propiedad.getId(),
                    propiedad.getTipoInmueble(), entrante.clave()));
        }
        List<String> antes = existia ? multivalores.valoresDe(ancla.getId()) : List.of();
        ValorLogico hallado = existia ? ValorLogico.deConjunto(antes) : null;

        // Se escribe la DIFERENCIA y se anota el CONJUNTO. No es lo mismo, y las
        // dos mitades importan: el linaje tiene que poder decir que habia y que
        // quedo, enteros; la base solo tiene que quedar con lo segundo. Borrarlo
        // todo y reescribirlo hacia lo segundo apoyandose en que nadie hubiera
        // leido antes esos elementos -- una invariante que no fijaba ningun
        // test. Tocar solo lo que cambia la vuelve innecesaria.
        List<String> despues = entrante.valores();
        List<String> seVan = antes.stream().filter(valor -> !despues.contains(valor)).toList();
        if (!seVan.isEmpty()) {
            multivalores.borrarDe(ancla.getId(), seVan);
        }
        for (String valor : despues) {
            if (!antes.contains(valor)) {
                multivalores.save(new ValorMultipleAtributo(
                        actor.idOrganizacion(), ancla.getId(), valor));
            }
        }
        anotarSegunHabia(actor, entrante, propiedad.getId(), hallado,
                ValorLogico.deConjunto(despues));
    }

    /**
     * <b>Retira el valor de una clave logica, enrutando por su autoridad.</b>
     *
     * <p>Quien llama dice «quiero quitar el piso» y nada mas. No sabe —ni tiene
     * por que saber— si esa clave se guarda hoy como fila de
     * {@code atributo_propiedad} o como columna del agregado, ni si manana
     * cambia de sitio. La regla del trazado se completa aqui:
     *
     * <pre>
     *   clave  →  autoridad  →  leer / escribir / <b>borrar</b>
     * </pre>
     *
     * <p>Es distinto de mandar el valor en blanco, y a proposito: en blanco es
     * un valor que llego mal, y retirar es una <b>intencion declarada</b>. Este
     * corte no le da a {@code ""} ningun significado nuevo.
     *
     * <p><b>Desde 4.P devuelve el valor que quito</b> en vez de un
     * {@code boolean}. El borrado es fisico —la fila se va, y con ella sus
     * opciones por {@code ON DELETE CASCADE}—, asi que lo que se lee aqui es la
     * ultima vez que ese dato existe. Se lee antes de borrarlo y se anota; sin
     * eso, «esta propiedad tuvo vigilancia con caseta hasta marzo» seria una
     * afirmacion que la base no puede sostener.
     *
     * @return {@link ValorRetirado#NO_ES_DEL_CATALOGO} si la clave no esta en el
     *         catalogo, para que el llamante pueda probar el otro espacio de
     *         nombres antes de rechazarla. Lanza si esta y su autoridad no
     *         admite quedarse vacia
     */
    public ValorRetirado retirar(Actor actor, Propiedad propiedad, String clave,
                                 ProcedenciaDelValor procedencia) {
        Optional<CatalogoAtributo> definicion =
                catalogo.porClave(actor.idOrganizacion(), clave);
        if (definicion.isEmpty()) {
            return ValorRetirado.NO_ES_DEL_CATALOGO;
        }
        // Borrar enruta por sujeto igual que leer y escribir. Sin esto, pedir
        // que se retire una condicion del encargo saldria como "no esta en el
        // catalogo" -- un mensaje falso sobre una clave que existe.
        exigirQueSeaDePropiedad(definicion.get());

        ValorLogico hallado;
        if (definicion.get().esEstructural()) {
            String concepto = definicion.get().getCampoEstructural();
            hallado = EscritorEstructural.leerValor(propiedad, concepto);
            // `vaciar` lanza para los conceptos que no admiten quedarse vacios
            // -- METRAJE es NOT NULL --, y tiene que lanzar ANTES de que se anote
            // nada: un linaje de una retirada que no ocurrio seria peor que no
            // tenerlo.
            EscritorEstructural.vaciar(propiedad, concepto, clave);
        } else {
            AtributoPropiedad existente = valores
                    .findByIdPropiedadAndClave(propiedad.getId(), clave)
                    .orElse(null);
            hallado = existente == null ? null : loQueTenia(definicion.get(), existente);
            valores.deleteByIdPropiedadAndClave(propiedad.getId(), clave);
        }
        // NO se anota una retirada que no ocurrio. Nombrar en `atributosABorrar`
        // una clave que nunca tuvo valor es legitimo -- el cliente no siempre
        // sabe si estaba -- y no pasa nada: se borra lo que no habia. Pero
        // escribir por eso una fila fechada, con autor y canal, en una tabla que
        // no se puede corregir ni borrar, seria dejar constancia de un hecho que
        // no existio. El linaje cuenta lo que paso, no lo que se pidio.
        if (hallado == null) {
            return ValorRetirado.de(null);
        }
        linaje.anotarRetirada(actor, procedencia, RastroValorGobernado.SUJETO_PROPIEDAD,
                propiedad.getId(), clave, hallado);
        return ValorRetirado.de(hallado);
    }

    /**
     * <b>Lo que una fila tenia, leido segun la FORMA de su clave</b> (4.P).
     *
     * <p>La forma la decide el catalogo, no lo que haya en las columnas. Un
     * {@code LISTA_MULTIPLE} con el conjunto vacio es un ancla: la clave estaba
     * <b>respondida</b>, y {@code deFila} con una lista vacia caeria a la rama
     * escalar y devolveria {@code null} -- o sea, «aqui no habia nada», que es
     * falso y ademas convertiria la retirada en un no-hecho.
     *
     * <p>Es el mismo principio que aplica {@code escribirMultivalor} al escribir
     * ({@link ValorLogico#deConjunto}): <b>el conjunto vacio es un conjunto</b>.
     * Estaban a dos metros y decian cosas distintas.
     */
    private ValorLogico loQueTenia(CatalogoAtributo definicion, AtributoPropiedad existente) {
        return definicion.tipo().esMultivalor()
                ? ValorLogico.deConjunto(multivalores.valoresDe(existente.getId()))
                : ValorLogico.deFila(existente, null);
    }

    /**
     * ALTA o EDICION segun lo que la autoridad tuviera, y no segun por que
     * endpoint entro la peticion.
     *
     * <p>La distincion es del DATO: que una clave reciba valor por primera vez
     * dentro de un {@code PUT} de edicion sigue siendo su alta, y llamarlo
     * edicion diria que habia algo antes.
     */
    private void anotarSegunHabia(Actor actor, ValorEntrante entrante, long idPropiedad,
                                  ValorLogico hallado, ValorLogico escrito) {
        if (hallado == null) {
            linaje.anotarAlta(actor, entrante.procedencia(),
                    RastroValorGobernado.SUJETO_PROPIEDAD, idPropiedad, entrante.clave(), escrito);
        } else {
            linaje.anotarEdicion(actor, entrante.procedencia(),
                    RastroValorGobernado.SUJETO_PROPIEDAD, idPropiedad, entrante.clave(),
                    hallado, escrito);
        }
    }

    /**
     * <b>Como se llama, en ESTA organizacion, la clave que alimenta un campo
     * canonico</b> (4.P).
     *
     * <p>El cable tiene un hueco {@code ubicacion.piso} que parece una
     * coordenada y no lo es: {@code piso} es una clave gobernada, declarada
     * {@code ESTRUCTURAL} sobre el campo {@code PISO}. Escribirla por el hueco
     * de la ubicacion la sacaba del enrutador -- y por tanto del linaje --, que
     * es el defecto que 4.P cerro en su segunda vuelta.
     *
     * <p>Se pregunta por el CONCEPTO y no por el nombre, por la misma razon por
     * la que {@link EscritorEstructural} conmuta sobre el concepto: la clave es
     * dato y una organizacion puede llamarla como quiera, mientras que
     * {@code PISO} existe con ese nombre pase lo que pase.
     *
     * @return {@code empty()} si el catalogo de esta organizacion no declara
     *         ninguna clave sobre ese campo. No es un error: significa que ese
     *         concepto no se gobierna aqui, y entonces el hueco del cable no
     *         tiene nada que enrutar
     */
    public Optional<String> claveDelCampo(long idOrganizacion, String campoEstructural) {
        return catalogo.porCampoEstructural(idOrganizacion, campoEstructural)
                .map(CatalogoAtributo::getClave);
    }

    /** La definicion de una clave: la de la organizacion gana sobre la del sistema. */
    public CatalogoAtributo definicionDe(long idOrganizacion, String clave) {
        CatalogoAtributo definicion = catalogo.porClave(idOrganizacion, clave)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El atributo \"" + clave + "\" no esta en el catalogo. Una clave existe "
                                + "antes que su valor: si no, dos propiedades dicen lo mismo con "
                                + "nombres distintos y dejan de poder compararse."));
        exigirQueSeaDePropiedad(definicion);
        return definicion;
    }

    /**
     * La mitad Java de la regla que el trigger {@code tg_atributo_gobernado}
     * garantiza en la base desde V73.
     *
     * <p>Es la direccion contraria de la que vigila {@link AtributosDeEncargo},
     * y ninguna es simetrica de la otra. Guardar una condicion negociada como
     * hecho del inmueble no falla: <b>miente</b>, y ademas la pierde -- porque
     * `uq_atributo_propiedad_clave` deja un valor por propiedad, asi que el
     * segundo encargo pisa al primero sin dejar rastro.
     */
    private static void exigirQueSeaDePropiedad(CatalogoAtributo definicion) {
        if (definicion.esDeEncargo()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" es una condicion del ENCARGO "
                            + "y se intento tratar como un hecho de la propiedad. Se pacta en "
                            + "cada comercializacion: guardarlo en el inmueble haria que el "
                            + "siguiente encargo heredara lo pactado en el anterior.");
        }
    }

    /** Las definiciones de un lote de claves, por clave. Evita el N+1 de la ficha. */
    public Map<String, CatalogoAtributo> definicionesDe(long idOrganizacion, String tipoPropiedad) {
        Map<String, CatalogoAtributo> porClave = new LinkedHashMap<>();
        for (CatalogoAtributo atributo : aplicablesA(idOrganizacion, tipoPropiedad)) {
            porClave.putIfAbsent(atributo.getClave(), atributo);
        }
        return porClave;
    }

    /**
     * <b>Las definiciones para LEER lo que ya esta escrito</b> (Corte 5 · 5A).
     *
     * <p>Parte de lo aplicable —que es lo que se pregunta— y <b>completa</b> las
     * claves que tienen valor y ya no se preguntan: las retiradas
     * ({@code activo = false}) y las que dejaron de aplicar a este tipo.
     *
     * <h2>La asimetria es deliberada, y es la que faltaba</h2>
     * <pre>
     *   CAPTURA (alta y edicion) -> {@link #aplicablesA}: solo lo ACTIVO
     *   LECTURA (la ficha)       -> esto: tambien lo RETIRADO
     * </pre>
     *
     * <p>{@code V84} retira {@code servicios_disponibles} <b>conservando sus
     * valores</b> —cuantos haya: en {@code controllocal_dev} son cero y en la
     * base de pruebas son los que dejaron las corridas anteriores. El tamano se
     * escribe como INVARIANTE y jamas como cifra, porque una cifra caduca en
     * cuanto corre una suite; quien lo quiera medido lo lee en la columna
     * {@code nota} del gate («legado realmente presente en esta base: N
     * filas»)—, y hasta 5A la ficha los mostraba con <b>la clave desnuda</b>
     * —{@code rotulo = "servicios_disponibles"}, {@code tipoDato = null},
     * colocados al final de la lista— porque la definicion ya no llegaba. El
     * valor se conservaba y la <b>lectura</b> se degradaba: exactamente el
     * defecto que este repositorio ya nombra en {@code propiedad-detail.html}
     * («falta metraje_total», que no es una frase para nadie).
     *
     * <p>Retirar la pregunta no puede degradar la respuesta. Y no reabre ninguna
     * puerta de escritura: {@link #aplicablesA} sigue filtrando por
     * {@code activo} —o sea que la clave retirada no se pregunta—, y quien
     * rechaza el valor por la API es {@link #definicionDe} →
     * {@code CatalogoAtributoRepository.porClave}, cuyo JPQL lleva
     * {@code and c.activo = true}. El trigger
     * {@code exigir_atributo_gobernado} exige lo mismo, pero es la <b>red de
     * atras</b>: actua contra SQL directo, no contra esta ruta. Esta frase
     * atribuia el cierre solo al trigger y se corrigio el 2026-08-26, medido por
     * HTTP (evidencia de 5A, §15).
     *
     * <p><b>Con una excepcion, registrada y no cerrada</b>: la rama MULTIVALOR
     * no pasa por {@link #definicionDe} cuando el ancla ya existe, y el trigger
     * de opciones no ve el {@code DELETE}. Hoy no hay dato afectado y la deuda
     * es {@code pendientes-brox.md} §2.3 sexies · N22.
     *
     * <h2>HASTA DONDE LLEGA ESA FRASE, Y DONDE NO LLEGA</h2>
     *
     * <p>Vale para las claves cuyo valor vive en {@code atributo_propiedad}, que
     * son las que este metodo completa: el lector las encuentra escritas, las
     * trae en {@code clavesLeidas}, y aqui se les devuelve su definicion.
     *
     * <p><b>NO vale para las ESTRUCTURALES</b>, y decirlo sin matiz era falso.
     * Una clave estructural no deja fila: su valor esta en una columna canonica
     * de {@code propiedad}, y {@code LectorPorAutoridad.armar} lo inyecta
     * recorriendo <b>las definiciones que recibe</b> —el mapa ya filtrado por
     * {@code activo}— y no las claves escritas. Este metodo se aplica
     * <b>despues</b>, asi que llega tarde: la clave retirada no esta en
     * {@code clavesLeidas} porque el lector nunca la produjo. El dia que se
     * retire una clave ESTRUCTURAL, su valor <b>desaparece de la ficha</b>
     * aunque la columna siga llena.
     *
     * <p>Hoy no se retira ninguna, asi que <b>no hay dato perdido</b> y el codigo
     * no se toca aqui: arreglar esa quinta superficie es un cambio con su propio
     * alcance. La deuda esta anotada en {@code docs/ai/pendientes-brox.md} con su
     * ruta y su condicion de disparo (auditoria del 2026-08-25, N3).
     *
     * @param clavesLeidas las claves que el lector encontro escritas
     */
    public Map<String, CatalogoAtributo> definicionesParaLeer(long idOrganizacion,
                                                              String tipoPropiedad,
                                                              Collection<String> clavesLeidas) {
        Map<String, CatalogoAtributo> porClave = definicionesDe(idOrganizacion, tipoPropiedad);
        completarRetiradas(catalogo, idOrganizacion, porClave, clavesLeidas);
        return porClave;
    }

    /**
     * Rellena las claves que la consulta de captura no resolvio, preguntando al
     * catalogo <b>sin</b> el filtro de {@code activo}.
     *
     * <p>Es {@code static} y recibe el repositorio porque los dos sujetos
     * —PROPIEDAD y ENCARGO— tienen el mismo problema y no puede resolverse dos
     * veces: dos lectores del mismo dato divergen, que es la regla que sostiene
     * {@code UnSoloLectorPorSujetoTest}. Cada enrutador sigue conociendo un solo
     * sujeto; lo que se comparte es la consulta al catalogo, que no tiene
     * sujeto.
     */
    static void completarRetiradas(CatalogoAtributoRepository catalogo, long idOrganizacion,
                                   Map<String, CatalogoAtributo> porClave,
                                   Collection<String> clavesLeidas) {
        List<String> faltan = clavesLeidas.stream()
                .filter(clave -> !porClave.containsKey(clave))
                .distinct()
                .toList();
        if (faltan.isEmpty()) {
            return;
        }
        for (CatalogoAtributo definicion : catalogo.paraLeer(idOrganizacion, faltan)) {
            porClave.putIfAbsent(definicion.getClave(), definicion);
        }
    }

    /** El valor de un atributo tal como se muestra: sin ceros de mas ni "true". */
    public static String comoTexto(AtributoPropiedad atributo) {
        if (atributo.getValorTexto() != null) {
            return atributo.getValorTexto();
        }
        if (atributo.getValorNumero() != null) {
            return atributo.getValorNumero().stripTrailingZeros().toPlainString();
        }
        return atributo.getValorBooleano() == null ? null : atributo.getValorBooleano().toString();
    }

    /**
     * Que claves de las obligatorias no estan en lo que se conoce. El motor de
     * captura lo usa <b>antes</b> de que exista la propiedad, cuando todavia no
     * hay nada que consultar en la base.
     */
    public List<String> faltantesEntre(long idOrganizacion, String tipoPropiedad,
                                       Iterable<String> clavesConocidas) {
        List<String> conocidas = new ArrayList<>();
        clavesConocidas.forEach(conocidas::add);
        return obligatoriasDe(idOrganizacion, tipoPropiedad).stream()
                .filter(clave -> !conocidas.contains(clave))
                .toList();
    }

    // ------------------------------------------------------------------

    /**
     * El rango que declara el catalogo, comprobado <b>aqui</b> y no solo en el
     * trigger.
     *
     * <p>El trigger lo rechazaria igualmente, pero con un mensaje de PostgreSQL
     * a mitad de una transaccion, y eso no se le puede ensenar a nadie: la base
     * es la garantia, esto es el mensaje.
     */
    private static BigDecimal enRango(CatalogoAtributo definicion, BigDecimal valor) {
        return ConversionDeValores.enRango(definicion, valor);
    }

    private static BigDecimal entero(String clave, String valor) {
        return ConversionDeValores.entero(clave, valor);
    }

    private static BigDecimal decimal(String clave, String valor) {
        return ConversionDeValores.decimal(clave, valor);
    }

    private static Boolean booleano(String clave, String valor) {
        return ConversionDeValores.booleano(clave, valor);
    }

    /** El nombre del tipo, para que el error no diga "tipo T". */
    public static String nombreDelTipo(String tipoPropiedad) {
        return switch (tipoPropiedad) {
            case "L" -> "LOCAL";
            case "O" -> "OFICINA";
            case "D" -> "DEPARTAMENTO";
            case "C" -> "CASA";
            case "T" -> "TERRENO";
            case "A" -> "ALMACEN";
            case "X" -> "OTRO";
            default -> tipoPropiedad;
        };
    }

    /** El codigo de un tipo escrito con palabras. Acepta las dos formas. */
    /**
     * <b>Como se llama un tipo cuando lo lee una persona.</b>
     *
     * <p>Va aparte de {@link #nombreDelTipo}: aquel es el nombre del VALOR
     * —{@code LOCAL}, lo que viaja por el cable y lo que el cliente devuelve al
     * responder— y esto es el ROTULO, que lleva acentos y minusculas porque se
     * pinta en una tabla.
     *
     * <p>Existe para que el cliente no traduzca. Un {@code switch} en Angular
     * que convierta {@code "L"} en «Local comercial» seria la matriz «tipo →
     * texto» viviendo en la interfaz, y con dos interfaces habria dos (D-A-1).
     */
    public static String rotuloDelTipo(String tipoPropiedad) {
        return switch (tipoPropiedad == null ? "" : tipoPropiedad) {
            case "L" -> "Local comercial";
            case "O" -> "Oficina";
            case "D" -> "Departamento";
            case "C" -> "Casa";
            case "T" -> "Terreno";
            case "A" -> "Almacén";
            case "X" -> "Otro";
            default -> tipoPropiedad;
        };
    }

    /**
     * <b>Como se llama el uso cuando lo lee una persona.</b>
     *
     * <p>Gemelo de {@link #rotuloDelTipo} y por el mismo motivo: el uso viaja
     * como una letra --{@code "C"}, {@code "V"}-- y una ficha que quiera
     * escribir «Comercial» tendria que traducirla. El catalogo de usos ademas
     * crecio con el modelo universal --antes solo existia el comercial, porque
     * solo se alquilaban locales--, asi que la tabla que el SPA heredo esta
     * incompleta: convierte {@code C} y deja pasar {@code V}, {@code I} y
     * {@code M} en crudo.
     */
    public static String rotuloDelUso(String uso) {
        return switch (uso == null ? "" : uso) {
            case "C" -> "Comercial";
            case "V" -> "Vivienda";
            case "I" -> "Industrial";
            case "M" -> "Mixto";
            default -> uso;
        };
    }
    public static Optional<String> codigoDelTipo(String tipoPropiedad) {
        if (tipoPropiedad == null || tipoPropiedad.isBlank()) {
            return Optional.empty();
        }
        String limpio = tipoPropiedad.trim().toUpperCase(Locale.ROOT);
        return switch (limpio) {
            case "L", "LOCAL", "LOCAL_COMERCIAL" -> Optional.of("L");
            case "O", "OFICINA" -> Optional.of("O");
            case "D", "DEPARTAMENTO", "DEPTO" -> Optional.of("D");
            case "C", "CASA" -> Optional.of("C");
            case "T", "TERRENO" -> Optional.of("T");
            case "A", "ALMACEN", "ALMACÉN", "DEPOSITO", "DEPÓSITO" -> Optional.of("A");
            case "X", "OTRO" -> Optional.of("X");
            default -> Optional.empty();
        };
    }
}
