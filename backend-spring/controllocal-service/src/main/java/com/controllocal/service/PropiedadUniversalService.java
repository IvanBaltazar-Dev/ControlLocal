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
     *
     * <h2>Y la procedencia viaja POR VALOR (4.P)</h2>
     * Los cuatro ultimos campos son <b>opcionales</b> y describen de donde sale
     * <b>este</b> valor, no la operacion. Esa es la razon de ser del microcorte:
     * un mismo {@code PUT} puede cambiar {@code tipo_acceso} (una visita),
     * {@code zonificacion} (un certificado) y {@code vigilancia} (lo dijo el
     * propietario), y una sola respuesta al guardar estamparia una naturaleza
     * <b>falsa</b> en dos de las tres.
     *
     * <p><b>El canal, el agente y el actor NO van aqui</b>: los sabe el Core y
     * los deriva de la sesion y de las cabeceras ({@code Procedencia}). Lo que
     * viaja por valor es lo unico que el Core no puede saber.
     *
     * @param naturaleza   {@code DECLARADO}, {@code OBSERVADO} o {@code INFERIDO}.
     *                     <b>Ausente</b> cuando el productor no lo sabe — y
     *                     ausente no es una cuarta clase de evidencia: es que no
     *                     consta como se obtuvo el hecho. El Core no la deduce
     *                     jamas del canal ni del actor
     * @param confianza    de 0 a 1. <b>Obligatoria con {@code INFERIDO}</b>,
     *                     junto con el agente, el modelo y su version, que ya
     *                     viajan en la procedencia del acto
     * @param observadoEn  cuando se observo el hecho, si se sabe. Distinto de
     *                     cuando se anoto: una visita del martes registrada el
     *                     viernes vale por el martes
     * @param evidenciaRef el puntero a la prueba: un documento, una foto, la
     *                     columna de la que se transcribio
     */
    record ValorAtributo(String clave, String valor, String moneda, List<String> valores,
                         String naturaleza, BigDecimal confianza, LocalDate observadoEn,
                         String evidenciaRef) {

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

        /**
         * El valor sin declarar procedencia, que es como llega <b>todo</b> el
         * cable de hoy: el SPA no estrena superficie de captura en 4.P. No es un
         * defecto silencioso — el Core sigue registrando quien lo escribio y por
         * donde; lo que no hace es inventar como se conocio.
         */
        public ValorAtributo(String clave, String valor, String moneda, List<String> valores) {
            this(clave, valor, moneda, valores, null, null, null, null);
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

        /** El mismo valor declarando como se obtuvo el hecho. */
        public ValorAtributo declarando(String naturaleza) {
            return new ValorAtributo(clave, valor, moneda, valores, naturaleza, confianza,
                    observadoEn, evidenciaRef);
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
     * <h2>Y por que viajan {@code estadoDato}, {@code editable} y su motivo</h2>
     * Un valor puede estar escrito y <b>ya no formar parte de lo que hoy se
     * pregunta</b>. Ocurre de dos maneras distintas que dan el mismo resultado:
     *
     * <pre>
     *   la clave se retiro del catalogo        (servicios_disponibles, V84)
     *   la clave sigue viva pero ya no aplica  (area_terreno en un TERRENO, V85)
     *   a ESTE tipo
     * </pre>
     *
     * <p>Hasta aqui las dos llegaban <b>indistinguibles de un dato corregible</b>,
     * asi que quien lee la ficha lo intenta, no encuentra el campo en el editor
     * y nada se lo explica. Y una senal que dijera solo «retirada» solo
     * describiria la primera: {@code area_terreno} no esta retirada --se sigue
     * preguntando en una casa-- y llamarla asi seria falso.
     *
     * <p>Por eso lo que viaja es la pregunta generica que
     * {@link com.controllocal.service.soporte.ContratoDeEscritura} responde:
     * si la clave pertenece <b>hoy</b> al contrato de escritura de esta
     * propiedad. Va aqui y no en el cliente porque la respuesta la tiene el
     * catalogo, y con dos consumidores --BROX Web y KAIROS-- deducirla dos
     * veces serian dos deducciones que se separan (D-A-1 §5).
     *
     * @param moneda  la del IMPORTE, aparte de la cifra. {@code null} si la
     *                clave no es un importe
     * @param valores los elementos de un LISTA_MULTIPLE, uno por elemento.
     *                {@code null} si la clave no es multivalor
     * @param estadoDato {@code VIGENTE} o {@code HISTORICO}
     * @param editable si el Core aceptaria hoy un valor para esta clave en esta
     *                propiedad. Es lo mismo que contestara el {@code PUT}, que
     *                lo vuelve a comprobar
     * @param motivoNoEditable la frase, ya escrita, de por que no se corrige.
     *                {@code null} cuando si se corrige
     */
    record AtributoFicha(String clave, String rotulo, String tipoDato, String unidad,
                         String valor, String moneda, List<String> valores,
                         String estadoDato, boolean editable, String motivoNoEditable) {

        /** El atributo de un solo hueco: ni importe ni multivalor. */
        public AtributoFicha(String clave, String rotulo, String tipoDato, String unidad,
                             String valor) {
            this(clave, rotulo, tipoDato, unidad, valor, null, null,
                    com.controllocal.service.soporte.ContratoDeEscritura.VIGENTE, true, null);
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

    /**
     * @param historico la serie economica de ESTE encargo, o {@code null} si
     *                  quien pregunta no puede leerla (D-P0-6): la lee su propio
     *                  agente y el BROKER que lo supervisa hoy; el TENANT_ADMIN
     *                  no, porque gobernar no es operar. <b>Nulo no es vacio</b>
     *                  — con Jackson {@code NON_NULL} el campo no viaja, y eso
     *                  es lo que el cliente debe leer como «no disponible para
     *                  ti». Un encargo sin hitos manda una lista vacia.
     *                  <p>Es lo <b>unico</b> del bloque que se acota: el importe
     *                  vigente, la exclusividad, las condiciones, los anuncios y
     *                  {@code puedeEditar} siguen viajando, porque «no puedes ver
     *                  lo que se pidio en 2023» no es «este encargo no existe»
     */
    record EncargoFicha(Long idEncargo, String codigo, String operacion, String operacionRotulo,
                        String estado, String estadoRotulo, boolean vivo,
                        BigDecimal importe, String moneda, String importeRotulo,
                        Boolean exclusividad, Long idAgente, String agenteNombre,
                        LocalDate inicio, LocalDate fin, List<HitoFicha> historico,
                        List<AtributoFicha> condiciones,
                        List<AtributoQueFalta> faltanParaPublicar,
                        List<PublicacionService.FichaPublicacion> publicaciones,
                        GestionDePublicacion publicacionGestionable,
                        boolean puedeEditar) {
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
     * @param historia           la memoria del inmueble, o {@code null} si quien
     *                           pregunta no puede leerla (D-P0-6). La leen el
     *                           AGENTE responsable y el BROKER que lo alcanza; el
     *                           TENANT_ADMIN no, porque gobernar no es operar.
     *                           <p>Y cuando si puede, se compone <b>solo de los
     *                           encargos que ese actor puede ver</b>: la historia
     *                           agregada no puede convertirse en la puerta por la
     *                           que se lee el importe de un encargo ajeno. Sin
     *                           ningun episodio visible llega {@code null}
     * @param actividad          los hechos comerciales de <b>sus encargos
     *                           visibles</b> (D-P0-6). Sin ninguno, {@code null}.
     *                           <p><b>Nulo no es vacio</b>: Jackson va
     *                           {@code NON_NULL}, asi que el bloque no viaja, y
     *                           el cliente tiene que leer su ausencia como «no
     *                           disponible para ti» y no como «no ha pasado nada
     *                           con esta propiedad»
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
                                   LocalDateTime fechaRegistro,
                                   Responsabilidad responsabilidad) {
    }

    /**
     * <b>Quien responde por la propiedad y que puede hacer QUIEN PREGUNTA</b>
     * (P0). Las dos cosas juntas y calculadas en el Core, no dos campos sueltos
     * que el cliente combine.
     *
     * <p><b>Por que viaja el permiso ya resuelto.</b> Si la ficha publicara
     * solo {@code idResponsable}, cada consumidor —BROX Web, KAIROS y el
     * siguiente— tendria que llevar su propia copia de la regla ("si soy AGENTE
     * y mi rol coincide…"). Dos copias de una regla de autoridad divergen, y
     * divergen hacia el lado que deja pintar un boton que el backend va a
     * rechazar. Aqui lo decide el <b>mismo metodo</b> que despues deniega la
     * escritura, asi que la pantalla no puede prometer lo que el Core niega.
     *
     * <p><b>Es informacion interna del tenant.</b> Sale en la ficha operativa,
     * que ya es de la organizacion. No sale —ni {@code idResponsable}, ni
     * {@code motivo}— por ninguna proyeccion externa: publicaciones, anuncios,
     * fichas compartidas o exportaciones. La ficha se construye campo a campo
     * y nunca serializando la entidad, que es lo que hace que eso sea
     * comprobable y no una promesa.
     *
     * @param idResponsable   el rol del agente que responde HOY, o {@code null}
     *                        si esta <b>FALTANTE</b>. NULL no es "de todos": es
     *                        "no se sabe", y no se rellena con el agente de
     *                        ningun encargo
     * @param nombre          su nombre, para no obligar a la pantalla a
     *                        resolver el id contra otra lista
     * @param puedeEditar     si <b>este</b> actor puede escribir hechos de la
     *                        propiedad. No dice nada de los encargos: cada uno
     *                        responde por su cuenta en {@link EncargoFicha}
     * @param motivo          el codigo del rechazo cuando no puede
     *                        ({@code FALTA_RESPONSABLE}, {@code OTRO_RESPONSABLE},
     *                        {@code NO_OPERA}); {@code null} cuando si puede
     * @param motivoTexto     el mismo motivo en palabras, escrito por el Core.
     *                        El cliente pinta este texto y no traduce el codigo:
     *                        dos redacciones del mismo rechazo se separan
     * @param puedeTraspasar  si <b>este</b> actor puede <b>iniciar ahora</b> el
     *                        cambio de responsable de <b>esta</b> propiedad,
     *                        considerando su responsable actual (C7). Viaja
     *                        resuelto por la misma razon que {@code puedeEditar}:
     *                        sin el, la pantalla tendria que llevar su propia
     *                        copia de la regla de autoridad.
     *                        <p>Lo resuelven las dos guardas de {@code asignar}
     *                        que la ficha <b>si</b> puede mirar: la <b>banda</b>
     *                        —no ser AGENTE— y
     *                        {@code Alcances.alcanzaIncluidoSinDueno} sobre el
     *                        responsable <b>vigente</b>, que es el <b>mismo</b>
     *                        predicado que pregunta el POST. De ahi salen, sin
     *                        regla nueva, los dos casos que sorprenden:
     *                        TENANT_ADMIN responde {@code true} por
     *                        <b>autoridad de gobierno del tenant</b>, no como
     *                        super-broker —no gana edicion de hechos—, y una
     *                        propiedad <b>FALTANTE</b> responde {@code true} a
     *                        cualquier BROKER del tenant (C5), porque no hay
     *                        saliente a quien supervisar.
     *                        <p><b>No conoce el destino y no autoriza nada</b>:
     *                        en la ficha todavia no hay destino elegido, y el
     *                        POST de asignacion sigue siendo la autoridad
     *                        final, donde se vuelven a comprobar la banda, el
     *                        saliente y el destino
     */
    record Responsabilidad(Long idResponsable, String nombre, boolean puedeEditar,
                           String motivo, String motivoTexto, boolean puedeTraspasar) {
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

    // ------------------------------------------------------------------
    // El traspaso del responsable (P0-2)
    // ------------------------------------------------------------------

    /**
     * <b>Un traspaso de responsable, tal como queda en el expediente.</b>
     *
     * <p>Lleva las cinco cosas que hacen falta para auditarlo, y el
     * {@code anterior} viaja {@code null} cuando la propiedad estaba FALTANTE:
     * ese hueco es informacion —dice que no habia predecesor— y no se rellena
     *
     * <p>{@code origen} dice si la fijo el ALTA de una propiedad nueva o un
     * TRASPASO de broker. Viaja porque son dos hechos distintos y el
     * expediente tiene que poder distinguirlos: no se deducen del predecesor,
     * porque la primera asignacion de una propiedad FALTANTE tampoco lo tiene.
     */
    record TraspasoDeResponsable(Long id, Long idPropiedad,
                                 Long idResponsableAnterior, String responsableAnterior,
                                 Long idResponsableNuevo, String responsableNuevo,
                                 Long idPersonaActor, String rolActor, String origen,
                                 String motivo, LocalDateTime fecha) {
    }

    /**
     * <b>El responsable que el actor vio en la ficha cuando decidio</b>
     * (D-P0-9).
     *
     * <p>Un traspaso no es «pon a B»: es «cambia A por B». Sin decir de que
     * estado se parte, dos comandos que salieran del <b>mismo</b> A —uno hacia
     * B y otro hacia C— acabarian con la ultima escritura ganando, y el segundo
     * se habria reinterpretado en silencio como «de B a C», que es una decision
     * que nadie tomo. Por eso el estado observado entra en el comando y no se
     * deduce de la fila: la fila dice como esta <b>ahora</b>, no como estaba
     * cuando el broker miro.
     *
     * <p><b>FALTANTE se declara, no se infiere de una ausencia.</b>
     * {@link #faltante()} significa «vi que no tenia responsable»; es un hecho
     * observado, distinto de «no me consta» y distinto de «no lo mire». Donde
     * eso se hace cumplir es en el <b>comando</b>, que es por donde entra un
     * cliente: el cuerpo del POST tiene que traer o el responsable observado o
     * la declaracion de que estaba FALTANTE, y un cuerpo que no traiga ninguna
     * de las dos es 400 —no «FALTANTE», que seria justo la inferencia que este
     * P0 vino a quitar—. Aqui dentro, ya traducido, el FALTANTE es un
     * {@code idRol} nulo y nada mas.
     *
     * @param idRol {@code persona_rol.id} del responsable observado, o
     *              {@code null} <b>solo</b> a traves de {@link #faltante()}.
     */
    record ResponsableObservado(Long idRol) {

        /** «Lo mire y no tenia responsable»: FALTANTE observado, no supuesto. */
        public static ResponsableObservado faltante() {
            return new ResponsableObservado(null);
        }

        /** «Lo mire y respondia este agente». */
        public static ResponsableObservado de(Long idRol) {
            return new ResponsableObservado(idRol);
        }

        /** Si lo observado fue la ausencia de responsable. */
        public boolean esFaltante() {
            return idRol == null;
        }
    }

    /**
     * <b>Asigna o cambia quien responde por la propiedad</b> (P0-2). Lo hace un
     * BROKER o el gobierno del tenant, nunca un agente.
     *
     * <p>Es la unica forma de mover la autoridad despues del alta, y la unica
     * forma de sacar a una propiedad de FALTANTE. <b>No</b> reasigna ningun
     * encargo, <b>no</b> modifica ningun atributo inmobiliario y <b>no</b>
     * cambia lo que nadie puede leer.
     *
     * <p><b>El comando declara sobre que responsable actua</b> (D-P0-9). Si al
     * ejecutarse el responsable ya no es el observado, la respuesta es
     * {@code ConflictoException} (409) y <b>no</b> se reinterpreta el traspaso
     * sobre el estado nuevo. No hay sobrecarga sin {@code observado} a
     * proposito: seria la misma «ultima escritura gana» entrando por la puerta
     * de atras.
     */
    TraspasoDeResponsable asignarResponsable(long idPropiedad, long idRolAgente, String motivo,
                                             ResponsableObservado observado, Actor actor);

    /** El expediente de traspasos de una propiedad, el mas reciente primero. */
    List<TraspasoDeResponsable> traspasosDe(long idPropiedad, Actor actor);

    /**
     * <b>Un destino posible para el traspaso, ya elegible</b> (D-P0-12).
     *
     * <p>Lleva lo justo para elegir en una lista —quien es, su codigo y su
     * zona— y <b>ningun estado administrativo</b>: si el agente aparece es que
     * cumple las cinco condiciones de D-P0-7, y si no aparece no se publica por
     * que. El motivo de una ausencia es informacion de la ficha del agente, no
     * de un selector de traspaso.
     */
    record CandidatoResponsable(Long idAgente, String nombre, String codigoAgente,
                                String zonaAsignada) {
    }

    /**
     * <b>Los destinos que ESTE actor puede elegir para ESTA propiedad</b>
     * (D-P0-12).
     *
     * <p>El Core responde «que destinos puedo seleccionar» ya resuelto, con las
     * cinco condiciones de D-P0-7 aplicadas <b>en la base</b> y el responsable
     * actual fuera. Angular no decide autoridad: sin esta superficie, la
     * pantalla tendria que pedir la lista de agentes del tenant y depurarla con
     * su propia copia de la regla —que es una lista de permisos en el cliente— o
     * dejar que el broker descubra el rechazo despues de elegir.
     *
     * <p><b>No autoriza nada.</b> El {@code POST /propiedades/{id}/responsable}
     * revalida todo: banda, tenant, alcance del destino, alcance del saliente y
     * elegibilidad. Que la lista lo ofrezca no sustituye ni una de esas guardas
     * —un agente puede quedar desactivado entre la lista y el POST—, y esa es
     * exactamente la razon por la que las dos preguntas comparten el mismo
     * predicado SQL en vez de tener cada una el suyo.
     *
     * <p>Un id de otra corredora responde <b>404</b>; un actor que no puede
     * iniciar el traspaso de esta propiedad —el mismo predicado que apaga el
     * boton en la ficha— responde <b>403</b>, y no una lista vacia: "no hay
     * candidatos" y "no te corresponde" son dos respuestas distintas.
     */
    Pagina<CandidatoResponsable> candidatosAResponsable(long idPropiedad, String texto,
                                                        int pagina, int tamano, Actor actor);
}
