package com.controllocal.service;

import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <b>La propiedad universal: alta, lectura y edicion por el modelo nuevo</b>
 * (D-E4-1, D-E4-2).
 *
 * <h2>Que resuelve, y por que en un solo caso de uso</h2>
 * Registrar una propiedad no es escribir una fila. Son nueve cosas que solo
 * valen juntas: la propiedad, su ubicacion, sus titulares con sus cuotas, sus
 * atributos gobernados, uno o dos encargos con su operacion, la condicion
 * economica de cada uno, el primer hito {@code U} de cada serie y el evento de
 * dominio que deja constancia. <b>Todo o nada.</b>
 *
 * <p>Repartirlo en cinco endpoints —crear el inmueble, luego los titulares,
 * luego el encargo...— produce medias propiedades en cuanto una llamada falla
 * o el usuario cierra la pestana, y esas medias propiedades no las arregla
 * nadie: quedan en la cartera sin titular o sin precio, contando en los
 * listados y mintiendo en los indicadores.
 *
 * <h2>La operacion no se infiere jamas</h2>
 * Cada elemento de {@code operaciones} declara <b>VENTA</b> o <b>ALQUILER</b>.
 * Una propiedad disponible para las dos cosas se registra con <b>dos</b>
 * elementos, cada uno con su importe y su condicion — nunca con un valor
 * combinado, que obligaria a decidir a mano que pasa con el alquiler el dia
 * que se venda.
 *
 * <h2>El mismo modelo escribe y lee</h2>
 * {@link #consultar} devuelve lo que {@link #registrar} escribio, leido de las
 * estructuras nuevas: titularidades, atributos gobernados y encargos. No es un
 * detalle de implementacion — un POST que escribe en el modelo universal y un
 * GET que lee columnas viejas es una migracion a medias que parece terminada.
 *
 * <h2>Idempotencia</h2>
 * {@code claveIdempotencia} es del cliente y opcional. Cuando viene, un
 * reintento con la misma clave <b>no crea una segunda propiedad</b>: devuelve
 * la que produjo el primer intento, con {@code reintento = true}. Es lo que
 * hace seguro poner un canal conversacional delante, porque un canal
 * conversacional reintenta por diseno.
 */
public interface PropiedadUniversalService {

    // ------------------------------------------------------------------
    // Lo que entra
    // ------------------------------------------------------------------

    /**
     * Un titular y la parte que le corresponde.
     *
     * <p>Las cuotas vigentes de una propiedad tienen que sumar 100 — lo exige
     * un constraint trigger diferido (V47) y lo comprueba el servicio antes,
     * para poder decir "te faltan 30" en vez de dejar que estalle el COMMIT.
     * {@code representante} marca con quien se habla; si nadie lo marca, es el
     * primero.
     */
    record Titular(Long idRolPropietario, BigDecimal cuota, Boolean representante) {
    }

    /**
     * El valor de una caracteristica gobernada, tal cual se escribio. Llega
     * como texto y lo interpreta el catalogo: {@code dormitorios} es ENTERO y
     * {@code amoblado} BOOLEANO, y esa conversion no la decide el cliente.
     */
    /**
     * El valor de una clave gobernada, tal cual se escribio.
     *
     * <p>Llega como texto y lo interpreta el catalogo: {@code dormitorios} es
     * ENTERO y {@code amoblado} BOOLEANO, y esa conversion no la decide el
     * cliente.
     *
     * <p><b>Dos formas no caben en un escalar</b>, y por eso tienen su hueco
     * desde el Corte 0B en vez de codificarse dentro de {@code valor}:
     *
     * <ul>
     *   <li>{@code moneda} acompana a un IMPORTE. Meterla dentro del texto
     *       --{@code "120.50 USD"}-- obligaria al Core a parsear una cadena
     *       compuesta y a decidir que hacer con {@code "120,50 US$"}.</li>
     *   <li>{@code valores} es un LISTA_MULTIPLE. Mandarlo como
     *       {@code "AGUA, LUZ"} es exactamente el defecto de
     *       {@code servicios_disponibles} que 0B viene a cerrar: dos fichas que
     *       dicen lo mismo en distinto orden dejan de poder compararse.</li>
     * </ul>
     */
    record ValorAtributo(String clave, String valor, String moneda, List<String> valores) {

        /**
         * <b>Un multivalor no llega ademas como escalar</b> (V77).
         *
         * <p>La lista es la representacion canonica de un LISTA_MULTIPLE, y
         * mandar tambien {@code valor} son <b>dos formas del mismo dato</b>:
         * el escritor tomaba la lista y descartaba el escalar <b>en silencio</b>,
         * que es la clase de eleccion que este proyecto no hace. Es la misma
         * regla que ya rige para una clave que llega con valor y en
         * {@code atributosABorrar}: entre dos intenciones contrarias no se
         * elige, se avisa.
         *
         * <p>Se comprueba en el constructor y no en cada escritor porque hay
         * cuatro -- propiedad y encargo, alta y edicion --, y con la regla
         * repetida cuatro veces bastaria olvidarla en una. Aqui el estado
         * ambiguo <b>no se puede construir</b>.
         */
        public ValorAtributo {
            if (valores != null && (valor != null || moneda != null)) {
                throw new ReglaNegocioException(
                        "El atributo \"" + clave + "\" llego como lista y ademas como valor suelto. "
                                + "Un multivalor se manda con su lista y nada mas: son dos formas "
                                + "del mismo dato y elegir una por ti seria descartar la otra sin "
                                + "decirlo.");
            }
        }

        /** El caso normal: una clave y su valor. */
        public ValorAtributo(String clave, String valor) {
            this(clave, valor, null, null);
        }

        /** Un importe, que es monto y moneda o no es nada. */
        public static ValorAtributo importe(String clave, String monto, String moneda) {
            return new ValorAtributo(clave, monto, moneda, null);
        }

        /** Varios valores del mismo vocabulario. Sustituyen a los que hubiera. */
        public static ValorAtributo multiple(String clave, List<String> valores) {
            return new ValorAtributo(clave, null, null, valores);
        }
    }

    /** Donde esta. Las coordenadas son opcionales; la direccion y el distrito no. */
    record Ubicacion(String direccion, String distrito, String zonaUrbanizacion,
                     BigDecimal latitud, BigDecimal longitud, String interiorUnidad,
                     String piso, String referenciaInterna, String nombreEdificioGaleria) {
    }

    /**
     * Una relacion comercial: la operacion y lo que se pide por ella.
     *
     * <p>{@code operacion} es VENTA o ALQUILER, obligatoria. {@code importe} es
     * el precio de venta o la renta mensual segun cual sea — el mismo numero
     * significa cosas distintas y por eso la operacion viaja pegada a el.
     */
    record OperacionSolicitada(String operacion, BigDecimal importe, String moneda,
                               String tipoComision, String baseCalculo, BigDecimal valorComision,
                               String tratamientoIgv, Boolean exclusividad,
                               LocalDate inicioEncargo, LocalDate finEncargo,
                               List<ValorAtributo> condiciones) {

        /** El alta de siempre, sin condiciones gobernadas todavia. */
        public OperacionSolicitada(String operacion, BigDecimal importe, String moneda,
                                   String tipoComision, String baseCalculo,
                                   BigDecimal valorComision, String tratamientoIgv,
                                   Boolean exclusividad, LocalDate inicioEncargo,
                                   LocalDate finEncargo) {
            this(operacion, importe, moneda, tipoComision, baseCalculo, valorComision,
                    tratamientoIgv, exclusividad, inicioEncargo, finEncargo, null);
        }
    }

    /**
     * El comando completo del alta.
     *
     * @param claveIdempotencia del cliente; {@code null} si no reintenta
     * @param procedencia       de donde salio la peticion: origen y, si vino de
     *                          una conversacion, cual y de que turno (D-K-1 §5)
     * @param idBorrador        el borrador que se estaba completando, si lo hay
     */
    record ComandoRegistro(String claveIdempotencia, Procedencia procedencia, String codigo,
                           String tipoPropiedad, String uso, String descripcion,
                           Ubicacion ubicacion, List<Titular> titulares,
                           List<ValorAtributo> atributos, List<OperacionSolicitada> operaciones,
                           Long idBorrador, String origen) {

        /**
         * El alta sin declarar procedencia. No hay defecto silencioso: el Core
         * la lee de lo que el comando <b>esta haciendo</b> —un alta que abre
         * encargos es trabajo operativo; una que no abre ninguno es
         * conocimiento de mercado— y el cliente puede corregirla declarandola.
         */
        public ComandoRegistro(String claveIdempotencia, Procedencia procedencia, String codigo,
                               String tipoPropiedad, String uso, String descripcion,
                               Ubicacion ubicacion, List<Titular> titulares,
                               List<ValorAtributo> atributos,
                               List<OperacionSolicitada> operaciones, Long idBorrador) {
            this(claveIdempotencia, procedencia, codigo, tipoPropiedad, uso, descripcion,
                    ubicacion, titulares, atributos, operaciones, idBorrador, null);
        }
    }

    /**
     * La edicion. <b>La semantica completa, que es la invariante del Corte 0A</b>:
     *
     * <pre>
     *   bloque ausente o null      = no tocar
     *   campo ausente o null       = no tocar
     *   campo con valor            = cambiar a ese valor
     *   clave en atributosABorrar  = retirar el valor
     * </pre>
     *
     * <p>Las cuatro lineas dicen lo mismo desde arriba: <b>lo que la interfaz no
     * recibio o no modifico conserva exactamente su semantica anterior</b>.
     * Editar el precio no debe exigir reenviar los titulares, y reenviarlos
     * "para completar" es como se pierden datos que nadie queria cambiar.
     *
     * <p><b>{@code null} no significa borrar</b>, y no se le cambia el
     * significado en este corte: reinterpretar un valor existente es
     * exactamente la clase de perdida callada que 0A viene a contener. Borrar
     * es una <b>intencion declarada</b>, y el blanco tampoco sirve — mandar
     * {@code ""} donde iba un valor se rechaza, no se adivina.
     *
     * <p>{@code operaciones} sigue la misma regla que en el alta: cada una
     * declara la suya. Cambiar el importe de una operacion <b>anade</b> un hito
     * al historico; nunca sobrescribe el anterior.
     *
     * @param atributosABorrar claves <b>logicas</b> cuyo valor se retira. Un
     *                         nombre logico y no un sitio: quien pide retirar
     *                         dice «quita el piso» y no sabe si eso es una fila
     *                         de {@code atributo_propiedad}, una columna del
     *                         agregado o una pieza de la ubicacion. El Core
     *                         enruta el borrado por la misma autoridad por la
     *                         que enruta la lectura y la escritura. Una clave
     *                         que llegue <b>a la vez</b> con valor y en esta
     *                         lista es un error del cliente y se rechaza: entre
     *                         dos intenciones contrarias no se elige, se avisa
     */
    record ComandoEdicion(String claveIdempotencia, Procedencia procedencia, String descripcion,
                          Ubicacion ubicacion, List<Titular> titulares,
                          List<ValorAtributo> atributos, List<OperacionSolicitada> operaciones,
                          List<String> atributosABorrar,
                          List<CondicionesDeEncargo> condiciones) {

        /**
         * Una edicion que no toca ninguna condicion comercial.
         *
         * <p>{@code null} no es un descuido: es la regla de bloques dicha en el
         * constructor. Quien edita la ubicacion o los titulares no manda
         * condiciones, y no mandarlas significa <b>conservarlas</b> --nunca
         * vaciarlas ni completarlas por defecto.
         */
        public ComandoEdicion(String claveIdempotencia, Procedencia procedencia, String descripcion,
                              Ubicacion ubicacion, List<Titular> titulares,
                              List<ValorAtributo> atributos, List<OperacionSolicitada> operaciones,
                              List<String> atributosABorrar) {
            this(claveIdempotencia, procedencia, descripcion, ubicacion, titulares, atributos,
                    operaciones, atributosABorrar, null);
        }
    }

    /**
     * <b>Las condiciones pactadas en UN encargo</b> (Corte 0C).
     *
     * <p>Viaja como lista de bloques —uno por {@code idEncargo}— y no como un
     * saco comun. Es la unica forma de representar la propiedad que tiene una
     * venta y un alquiler abiertos a la vez: la garantia pertenece al alquiler,
     * y un saco unico obligaria al Core a adivinar a cual de los dos encargos
     * asignar cada respuesta.
     *
     * <p><b>La identidad es {@code idEncargo}, jamas la operacion.</b> Dos
     * alquileres sucesivos de la misma propiedad son dos episodios; enviarlos
     * como «bloque ALQUILER» haria que editar el de 2026 pisara el de 2024.
     *
     * <p>Y la regla de bloques de 0A se aplica entera, un nivel mas adentro:
     * <b>un bloque ausente no se toca, y un bloque presente no toca a ningun
     * otro</b>. Enviar solo el encargo de venta deja el de alquiler exactamente
     * como estaba, con sus valores, sus vacios y sus fechas.
     *
     * @param atributos        {@code null} = no tocar ninguna condicion de este
     *                         encargo. Lista vacia significa lo mismo: no es
     *                         «borralas todas»
     * @param atributosABorrar claves logicas a retirar <b>de este encargo</b>.
     *                         Misma regla que arriba: valor y borrado a la vez
     *                         son dos intenciones contrarias y se rechaza
     */
    record CondicionesDeEncargo(Long idEncargo, List<ValorAtributo> atributos,
                                List<String> atributosABorrar) {
    }

    // ------------------------------------------------------------------
    // Lo que sale
    // ------------------------------------------------------------------

    record TitularFicha(Long idRolPropietario, String nombre, BigDecimal cuota,
                        boolean representante, LocalDate desde) {
    }

    /**
     * Un atributo con su valor y su etiqueta. {@code rotulo}, {@code unidad} y
     * {@code tipoDato} vienen del catalogo para que la pantalla no tenga que
     * saberse la tabla de memoria.
     *
     * <h2>Por que el valor viaja DOS veces</h2>
     * {@code valor} es el texto ya compuesto —{@code "PEN 350"},
     * {@code "COCINA, LAVADORA"}— y es lo que una ficha pinta. Pero un editor
     * no puede partirlo de vuelta: un multivalor cuyo elemento contenga una
     * coma seria imposible de separar, y adivinar donde acaba la moneda es
     * inferir. Asi que {@code moneda} y {@code valores} viajan <b>crudos</b> al
     * lado, y quien escribe usa esos.
     *
     * <p>No es duplicar la verdad: es la misma, una vez para leer y otra para
     * poder corregirla. Lo que estaba mal era tener solo la primera y pedirle
     * al cliente que dedujera la segunda.
     *
     * @param moneda  la del IMPORTE, aparte de la cifra. {@code null} si la
     *                clave no es un importe
     * @param valores los elementos de un LISTA_MULTIPLE, uno por elemento.
     *                {@code null} si la clave no es multivalor
     */
    record AtributoFicha(String clave, String rotulo, String tipoDato, String unidad,
                         String valor, String moneda, List<String> valores) {

        /** El atributo de un solo hueco: ni importe ni multivalor. */
        public AtributoFicha(String clave, String rotulo, String tipoDato, String unidad,
                             String valor) {
            this(clave, rotulo, tipoDato, unidad, valor, null, null);
        }
    }

    /** Un hito de la serie economica de una operacion. */
    record HitoFicha(String hito, String hitoRotulo, BigDecimal monto, String moneda,
                     LocalDate fecha) {
    }

    /**
     * <b>Una relacion comercial con su historico propio.</b> Dos encargos de la
     * misma propiedad no comparten ni precio ni serie: es lo que hace real la
     * universalidad.
     *
     * <h2>La identidad es {@code idEncargo}, nunca la operacion</h2>
     * Una propiedad puede tener <b>varios</b> encargos de ALQUILER a lo largo
     * del tiempo -- 2024 cerrado, 2025 cerrado, 2026 vigente --. Lo que la base
     * prohibe ({@code uq_captacion_viva_por_operacion}, V50) es que haya dos
     * <b>vivos</b> de la misma operacion, no que hayan existido varios.
     *
     * <p>Por eso agrupar por {@code operacion} funde series economicas que no
     * tienen nada que ver y produce una linea temporal que no significa nada.
     * Cada encargo es un bloque, identificado por su id.
     *
     * <h2>Por que viajan los rotulos</h2>
     * {@code operacionRotulo}, {@code estadoRotulo} e {@code importeRotulo} no
     * son cortesia: son la diferencia entre que el nombre funcional del importe
     * lo decida el dominio o lo decida un ternario en la interfaz. Un precio de
     * venta rotulado «renta mensual» es un error de bulto, y con dos
     * consumidores -- BROX Web y KAIROS -- serian dos ternarios que se separan
     * (D-A-1 §5).
     *
     * @param vivo si el encargo sigue en juego ({@code P}, {@code O} o
     *             {@code A}). Los cerrados tambien viajan: esconderlos borraria
     *             su historico economico de la vista sin decir que existe
     * @param importeRotulo «precio de venta» o «renta mensual», segun la
     *                      operacion. Sale de
     *                      {@link com.controllocal.domain.inmueble.OperacionInmobiliaria#nombreDelImporte()}
     */
    /**
     * <b>Si se puede gestionar la publicacion de este encargo, y si no, por que
     * no.</b>
     *
     * <p>Viaja como capacidad y no como estado en crudo para que la pantalla no
     * tenga que escribir {@code encargo.estado === 'A'} ni saber que un encargo
     * cerrado no se publica: eso es una regla de negocio y su dueno es Core
     * (D-A-1 §5). El backend rechaza igualmente la operacion invalida — esto
     * solo evita ofrecer un boton que va a dar un 400.
     *
     * @param motivo el hecho, en palabras, cuando no se puede. El TONO es de la
     *               interfaz; el hecho lo da el Core
     */
    record GestionDePublicacion(boolean permitida, String motivo) {
    }

    record EncargoFicha(Long idEncargo, String codigo, String operacion, String operacionRotulo,
                        String estado, String estadoRotulo, boolean vivo,
                        BigDecimal importe, String moneda, String importeRotulo,
                        Boolean exclusividad, Long idAgente, String agenteNombre,
                        LocalDate inicio, LocalDate fin, List<HitoFicha> historico,
                        List<AtributoFicha> condiciones,
                        List<AtributoQueFalta> faltanParaPublicar,
                        List<PublicacionService.FichaPublicacion> publicaciones,
                        GestionDePublicacion publicacionGestionable) {
    }

    /** Una clave obligatoria que todavia no tiene valor, con su nombre legible. */
    record AtributoQueFalta(String clave, String rotulo) {
    }

    // ------------------------------------------------------------------
    // La historia comercial del INMUEBLE
    // ------------------------------------------------------------------

    /**
     * <b>Un importe con su fecha y el encargo del que sale.</b>
     *
     * <p>El {@code idEncargo} viaja incluso aqui, en un dato agregado, y ese es
     * justo el punto: la historia se lee junta pero <b>cada cifra sigue siendo
     * de su episodio</b>. Sin el, «la ultima renta fueron 2 400» seria una
     * afirmacion sobre la propiedad que no se puede auditar contra nada.
     */
    record ImporteFechado(BigDecimal monto, String moneda, LocalDate fecha, Long idEncargo,
                          String codigoEncargo) {
    }

    /**
     * <b>Que ha pasado con esta propiedad en UNA operacion, a lo largo del
     * tiempo.</b>
     *
     * <p>Responde «¿cuantas veces estuvo en alquiler?» y «¿a cuanto se alquilo
     * la ultima vez?» sin fusionar nada: {@code veces} cuenta episodios y los
     * dos importes apuntan al suyo.
     *
     * <h2>Pedido y cierre son dos cosas, y no se sustituyen</h2>
     * {@code ultimoPedido} es lo ultimo que se pidio ({@code U} autorizado o
     * {@code P} publicado). {@code ultimoCierre} es lo ultimo que se cerro de
     * verdad ({@code C}). <b>No son el mismo numero</b> y cuando no hay cierre
     * el campo llega {@code null} — nunca relleno con el precio pedido, que es
     * la forma exacta de convertir «lo que pediamos» en «lo que vale» sin que
     * nadie lo note.
     *
     * @param veces  cuantos encargos de esta operacion ha tenido la propiedad,
     *               vivos y cerrados
     * @param desde  cuando empezo el primero
     * @param hasta  cuando termino el ultimo, o {@code null} si sigue vivo
     */
    record EpisodiosDeOperacion(String operacion, String operacionRotulo, int veces,
                                LocalDate desde, LocalDate hasta, boolean vivoAhora,
                                ImporteFechado ultimoPedido, ImporteFechado ultimoCierre) {
    }

    /** Un movimiento economico de la propiedad, con su procedencia intacta. */
    record HitoDeLaHistoria(LocalDate fecha, String hito, String hitoRotulo, BigDecimal monto,
                            String moneda, Long idEncargo, String codigoEncargo,
                            String operacion, String operacionRotulo) {
    }

    /**
     * <b>La memoria del inmueble</b>, que es un concepto distinto del encargo.
     *
     * <pre>
     *   idEncargo    la identidad tecnica de UN episodio comercial
     *   idPropiedad  la continuidad historica del inmueble
     * </pre>
     *
     * <p>Los bloques de encargo sirven para auditar y negociar: este importe,
     * este agente, esta serie. Esta proyeccion sirve para otra pregunta, que un
     * CRM de operaciones vivas no sabe responder: <i>«¿a cuanto se alquilo la
     * ultima vez?»</i>, <i>«¿cuantas veces estuvo en venta?»</i>, <i>«¿cual fue
     * el ultimo precio de cierre?»</i>.
     *
     * <p><b>No fusiona historicos: los agrega para leerlos.</b> Cada elemento de
     * {@code linea} y cada importe de {@code porOperacion} sigue apuntando a su
     * {@code idEncargo}, asi que de cualquier cifra de la historia se puede
     * volver al episodio que la produjo.
     *
     * @param linea todos los movimientos de la propiedad en orden cronologico
     *              <b>descendente</b>, atravesando encargos. Es la lectura que
     *              contesta «en cuanto se intento vender en 2023»
     */
    record HistoriaComercial(List<EpisodiosDeOperacion> porOperacion,
                             List<HitoDeLaHistoria> linea) {
    }

    /**
     * <b>Un hecho comercial, con la constancia de donde viene.</b>
     *
     * <p>{@code idEncargo} es el campo que impide que esta lista vuelva a
     * mezclar lo que el modelo universal separo. Una visita de alguien que
     * quiere <b>comprar</b> y otra de alguien que quiere <b>alquilar</b> la
     * misma propiedad son dos hechos de dos relaciones comerciales distintas, y
     * en una lista plana se leen igual.
     *
     * <p>La procedencia la pone el productor, no el consumidor: el SPA no puede
     * deducir de que encargo cuelga una visita sin recorrer
     * visita -> oportunidad -> captacion, que es topologia del modelo y no le
     * corresponde conocer (D-E4-3).
     *
     * @param proceso  OPORTUNIDAD, VISITA, INTERACCION, EXPEDIENTE o CONTRATO
     * @param titulo   el hecho en una linea, ya escrito
     * @param detalle  lo que lo acompana: el cliente, el canal, el resultado
     * @param ruta     donde se abre, en el vocabulario del SPA
     */
    /**
     * @param monto  el importe del hecho, cuando lo tiene: lo que ofrece un
     *               expediente, lo que cerro un contrato. Viaja como
     *               <b>numero</b> y no dentro de {@code detalle} porque
     *               agrupar millares y elegir separador es presentacion, y
     *               concatenarlo aqui produce «PEN 5480.00» en pantalla
     */
    record HechoDeActividad(String proceso, Long id, String codigo, String titulo, String detalle,
                            String estado, String estadoRotulo, LocalDate fecha,
                            BigDecimal monto, String moneda,
                            Long idEncargo, String operacion, String operacionRotulo,
                            String ruta) {
    }

    /**
     * <b>La actividad de una propiedad, repartida por proceso.</b>
     *
     * <p>Va dentro de la ficha y no en cinco endpoints sueltos porque la regla
     * «la actividad de una propiedad es la de sus encargos» es de dominio.
     * Resuelta desde el cliente serian tres llamadas por encargo, una mas por
     * cada oportunidad para sus visitas, y un barrido de contratos filtrado a
     * mano -- y la regla acabaria escrita en Angular.
     *
     * <p>Ninguna lista se recorta: son los hechos de <b>una</b> propiedad, no
     * una bandeja. Un tope silencioso haria que la ficha pareciera completa
     * cuando no lo esta.
     */
    record ActividadPropiedad(List<HechoDeActividad> oportunidades,
                              List<HechoDeActividad> visitas,
                              List<HechoDeActividad> interacciones,
                              List<HechoDeActividad> expedientes,
                              List<HechoDeActividad> contratos) {
    }

    /**
     * <b>La propiedad leida por el modelo universal, lista para ser leida.</b>
     *
     * <p>Es un read model de detalle, y eso decide su forma: llega
     * <b>preparado para pintarse</b>. Cada codigo viaja con su rotulo al lado,
     * porque la alternativa es que cada consumidor -- BROX Web y KAIROS -- monte
     * su propia tabla de traduccion, y dos tablas de traduccion del mismo
     * vocabulario se separan siempre (D-A-1 §6).
     *
     * <p><b>El metraje aparece una sola vez</b>, entre {@code atributos}, con su
     * clave logica {@code metraje_total}. Aqui no hay campo {@code metraje}
     * suelto a proposito: su autoridad fisica es un campo canonico del agregado
     * desde D-E4-3, pero el contrato logico no se movio, y publicarlo ademas por
     * separado obligaria a la ficha a excluirlo de la lista para no ensenarlo
     * dos veces.
     *
     * <p><b>Las dos listas de faltantes responden a DOS preguntas distintas</b>,
     * y por eso son dos campos y no uno filtrado:
     *
     * <ul>
     *   <li>{@code atributosQueFaltan} -- «que impide el ALTA» -- lleva las
     *       {@code ALT}.</li>
     *   <li>{@code faltanParaPublicar} -- «que impide PUBLICAR» -- lleva las
     *       {@code ALT} <b>y</b> las {@code PUB}.</li>
     * </ul>
     *
     * <p>Una clave {@code ALT} ausente sale en las dos, y eso es correcto: bloquea
     * las dos cosas. Filtrar la segunda a solo-{@code PUB} crearia un segundo
     * criterio de publicabilidad --uno para decidir y otro para contar-- y ademas
     * mentiria: diria que solo falta X para publicar cuando tambien falta Y.
     *
     * @param atributosQueFaltan las claves obligatorias para su tipo que
     *                           todavia no tiene, <b>con su rotulo</b>. No es un
     *                           error: es lo que permite decir «no se puede
     *                           publicar sin el metraje» -- y decirlo con esa
     *                           palabra, no con la clave
     * @param faltanParaPublicar lo que le falta a la PROPIEDAD para poder
     *                           anunciarse, con su rotulo. Sale del mismo metodo
     *                           de dominio que decide la publicabilidad
     *                           ({@code faltantesDePropiedadParaPublicar}), no de
     *                           una segunda matriz. Su gemelo por sujeto es
     *                           {@code EncargoFicha.faltanParaPublicar}: cada
     *                           sujeto reporta su propia deuda bajo el mismo
     *                           nombre
     */
    record FichaPropiedadUniversal(Long id, String codigo, String tipoPropiedad, String tipoRotulo,
                                   String uso, String usoRotulo, String descripcion,
                                   String estadoRegistro, String estadoRegistroRotulo,
                                   String disponibilidadComercial, String disponibilidadRotulo,
                                   Ubicacion ubicacion,
                                   List<TitularFicha> titulares, List<AtributoFicha> atributos,
                                   List<EncargoFicha> encargos,
                                   List<AtributoQueFalta> atributosQueFaltan,
                                   List<AtributoQueFalta> faltanParaPublicar,
                                   HistoriaComercial historia,
                                   ActividadPropiedad actividad,
                                   LocalDateTime fechaRegistro) {
    }

    /** Que produjo el alta. {@code reintento} avisa de que ya existia. */
    record ResultadoRegistro(Long idPropiedad, String codigo, List<Long> idsEncargos,
                             boolean reintento) {
    }

    // ------------------------------------------------------------------
    // El listado
    // ------------------------------------------------------------------

    /**
     * Un encargo <b>tal como se ve en una lista</b>: su operacion y lo que se
     * pide por ella. Es el minimo para poder escribir «Departamento · Venta ·
     * USD 180 000» sin abrir la ficha.
     */
    record EncargoEnLista(String operacion, String estado, BigDecimal importe, String moneda) {
    }

    /**
     * Una fila del listado universal.
     *
     * <p>{@code encargos} es una <b>lista</b> y no un par operacion/precio, y
     * ahi esta toda la diferencia con el listado heredado: una propiedad en
     * venta y en alquiler trae dos, con su importe cada uno. «Venta + alquiler»
     * es algo que el cliente <b>compone</b> al pintar; no es un valor que
     * exista en ninguna parte.
     */
    record FilaPropiedad(Long id, String codigo, String tipoPropiedad, String tipoRotulo,
                         String uso, String direccion, String distrito, BigDecimal metraje,
                         String estado, Long idPropietario, String propietarioNombre,
                         long titulares, List<EncargoEnLista> encargos,
                         LocalDateTime fechaRegistro) {
    }

    /**
     * Lo que acota el listado. Todo opcional; omitido, no filtra.
     *
     * @param operaciones las que la propiedad tiene <b>vivas</b>: {@code VENTA},
     *                    {@code ALQUILER} o las dos, y con las dos significa
     *                    «tiene los dos encargos», no «tiene uno cualquiera».
     *                    Se resuelve con dos EXISTS porque no hay ninguna
     *                    columna que consultar: la combinacion emerge de los
     *                    encargos
     */
    record FiltrosPropiedad(String texto, String tipoPropiedad, String distrito, String estado,
                            String operaciones, int pagina, int tamano) {
    }

    /** Lo que el filtro puede ofrecer sin inventarse opciones que no existen. */
    record OpcionesDeFiltro(List<String> distritos) {
    }

    /** Pagina de la cartera del tenant, con los filtros resueltos en la base. */
    Pagina<FilaPropiedad> listar(FiltrosPropiedad filtros, Actor actor);

    /** Los valores que el filtro puede ofrecer, sacados de la cartera real. */
    OpcionesDeFiltro opcionesDeFiltro(Actor actor);

    /** El alta completa: propiedad, titulares, atributos y encargos, todo o nada. */
    ResultadoRegistro registrar(ComandoRegistro comando, Actor actor);

    // `catalogoDe` vivia aqui hasta el Corte 0B, y lo retiro el mismo cambio que
    // migro a KAIROS. Respondia a «que se pregunta para este tipo» con seis
    // campos, mientras `/captura/definicion` respondia a lo mismo con once y
    // desde la misma consulta al catalogo. Dos productores de una autoridad
    // divergen, y estos ya lo hacian: publicaban un `orden` distinto para la
    // misma clave y nadie lo notaba. La unica definicion publica es la del
    // motor, que es la maquina compartida por BROX Web y KAIROS (D-E4-2).

    /** La propiedad tal como la escribio el modelo universal. */
    FichaPropiedadUniversal consultar(long idPropiedad, Actor actor);

    /** Edicion parcial: lo que llega {@code null} no se toca. */
    FichaPropiedadUniversal editar(long idPropiedad, ComandoEdicion comando, Actor actor);
}
