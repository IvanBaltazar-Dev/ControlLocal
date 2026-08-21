package com.controllocal.service.captura;

import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.BorradorCapturaRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.ResultadoRegistro;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AtributosDeEncargo;
import com.controllocal.service.soporte.ConversionDeValores;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.OperacionDelEncargo;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Procedencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * El motor, para la intencion {@code REGISTRAR_PROPIEDAD}.
 *
 * <h2>Como decide que falta</h2>
 * Suma dos listas y resta lo que ya sabe:
 * <ol>
 *   <li>lo <b>estructural</b> obligatorio, que declara {@link GuionRegistroPropiedad}
 *       y es igual para los siete tipos;</li>
 *   <li>lo <b>obligatorio del catalogo</b> para ese tipo, que sale de
 *       {@code catalogo_atributo} — y por eso un terreno pide zonificacion y un
 *       departamento pide dormitorios sin que este fichero sepa nada de
 *       ninguno de los dos.</li>
 * </ol>
 *
 * <p>La segunda lista <b>no se puede calcular hasta conocer el tipo</b>, y eso
 * explica el orden: mientras {@code tipoPropiedad} falte, lo unico que el motor
 * puede preguntar es el tipo.
 *
 * <h2>Validar al anotar, no al ejecutar</h2>
 * Cada valor se comprueba cuando entra: la operacion contra
 * {@link OperacionInmobiliaria}, los atributos contra su tipo de dato del
 * catalogo. Guardar en el borrador un "dormitorios: muchos" y descubrirlo al
 * final significa hacer al usuario recorrer otra vez todas las preguntas.
 */
@Service
public class MotorDeCapturaImpl implements MotorDeCaptura {

    private final BorradorCapturaRepository borradores;
    private final PropiedadUniversalService propiedades;
    private final AtributosGobernados gobierno;
    /** El otro sujeto. Nunca se le pregunta por lo fisico, ni al otro por esto. */
    private final AtributosDeEncargo condiciones;
    private final Documentos documentos;
    private final DistritoRepository distritos;

    public MotorDeCapturaImpl(BorradorCapturaRepository borradores,
                              PropiedadUniversalService propiedades,
                              AtributosGobernados gobierno, AtributosDeEncargo condiciones,
                              Documentos documentos, DistritoRepository distritos) {
        this.borradores = borradores;
        this.propiedades = propiedades;
        this.gobierno = gobierno;
        this.condiciones = condiciones;
        this.documentos = documentos;
        this.distritos = distritos;
    }

    // ==================================================================

    @Override
    @Transactional
    public EstadoCaptura avanzar(String intencion, Long idBorrador, Map<String, String> datos,
                                 Procedencia procedente, Actor actor) {
        Procedencia procedencia = Procedencia.oPantalla(procedente);
        String intencionValidada = intencionValidada(intencion);
        BorradorCaptura borrador = idBorrador == null
                ? abrir(actor, intencionValidada, procedencia)
                : cargar(idBorrador, actor);

        if (!borrador.estaEnCurso()) {
            throw new ReglaNegocioException(
                    "El borrador " + borrador.getCodigo() + " ya no esta en curso: se "
                            + (BorradorCaptura.EJECUTADO.equals(borrador.getEstado())
                                    ? "ejecuto y produjo " + borrador.getEntidadObjetivoTipo() + " "
                                            + borrador.getEntidadObjetivoId()
                                    : "descarto") + ".");
        }
        // El canal puede cambiar a mitad: empieza KAIROS, sigue la pantalla. Se
        // queda el ultimo que escribio, que es quien responde por el dato.
        borrador.setCanal(procedencia.canal());
        borrador.setAgente(procedencia.agente());
        // La conversacion, en cambio, se estampa UNA vez (V59): un borrador
        // nace de una conversacion o de ninguna, y si la ultima que lo tocara
        // pudiera sobrescribirla, el rastro diria que la propiedad salio de la
        // conversacion equivocada.
        borrador.nacioEn(procedencia.conversacionId());

        Map<String, Object> conocido = documentos.comoMapa(borrador.getDatosConocidos());
        incorporar(actor, conocido, datos);

        List<String> faltante = loQueFalta(actor, conocido);
        borrador.anotar(documentos.objeto(conocido), documentos.lista(faltante));
        borradores.save(borrador);

        return estado(borrador, conocido, faltante, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoCaptura consultar(long idBorrador, Actor actor) {
        BorradorCaptura borrador = cargar(idBorrador, actor);
        Map<String, Object> conocido = documentos.comoMapa(borrador.getDatosConocidos());
        return estado(borrador, conocido, documentos.comoLista(borrador.getDatosFaltantes()), actor);
    }

    /**
     * <b>Aquí es donde el catálogo decide, y por eso el cliente no tiene que
     * saber nada.</b>
     *
     * <p>Lo estructural sale de {@link GuionRegistroPropiedad} —y filtrado por
     * tipo: un terreno no tiene interior, ni piso, ni edificio—; lo del tipo
     * sale de {@code catalogo_atributo}, así que dar de alta un almacén no
     * necesita tocar este método; y lo económico se rotula según la operación,
     * porque «Renta mensual» sobre una venta es sencillamente falso.
     */
    @Override
    @Transactional(readOnly = true)
    public DefinicionCaptura definicion(String intencion, String tipoPropiedad, String operaciones,
                                        Actor actor) {
        String intencionValidada = intencionValidada(intencion);
        String codigoTipo = AtributosGobernados.codigoDelTipo(tipoPropiedad)
                .orElseThrow(() -> new ReglaNegocioException(
                        "Tipo de propiedad desconocido: \"" + tipoPropiedad + "\". Son siete: LOCAL, "
                                + "OFICINA, DEPARTAMENTO, CASA, TERRENO, ALMACEN y OTRO."));
        // La operación NO se infiere, y desde el Corte 0B tampoco se exige.
        //
        // Ausente significa «pregúntame por la COSA FÍSICA»: comunes y sección
        // del tipo, cero bloques de encargo. Es la consulta legítima de quien
        // quiere saber qué tiene un departamento sin fingir todavía una
        // intención comercial —KAIROS pregunta exactamente eso—, y es coherente
        // con la tesis del modelo: la propiedad no tiene operación, la operación
        // vive en el Encargo.
        //
        // Lo que no se hace es rellenarla con ALQUILER «porque es lo normal».
        // Eso devolvería un bloque económico que nadie pidió, rotulado con una
        // operación que nadie declaró.
        List<OperacionInmobiliaria> declaradas = operaciones == null || operaciones.isBlank()
                ? List.of()
                : operacionesDeclaradas(operaciones);

        List<Pregunta> comunes = new ArrayList<>();
        for (String clave : GuionRegistroPropiedad.COMUNES) {
            Pregunta pregunta = conCatalogoDelSistema(GuionRegistroPropiedad.pregunta(clave));
            if (pregunta != null) {
                comunes.add(pregunta.en(Pregunta.SECCION_COMUN, comunes.size()));
            }
        }

        // Estructurales condicionadas al tipo + todo lo que el catálogo declare
        // aplicable a ese tipo. Las dos cosas dependen del tipo físico, así que
        // viajan juntas y se descartan juntas al cambiarlo.
        List<Pregunta> delTipo = new ArrayList<>();
        for (String clave : List.of(GuionRegistroPropiedad.INTERIOR,
                GuionRegistroPropiedad.EDIFICIO)) {
            if (GuionRegistroPropiedad.aplicaAlTipo(clave, codigoTipo)) {
                delTipo.add(GuionRegistroPropiedad.pregunta(clave)
                        .en(Pregunta.SECCION_TIPO, delTipo.size()));
            }
        }
        for (CatalogoAtributo atributo : gobierno.aplicablesA(actor.idOrganizacion(), codigoTipo)) {
            delTipo.add(delCatalogo(atributo, codigoTipo));
        }

        // Un bloque por encargo. Los dos tienen la misma forma y distinto
        // nombre: es lo que permite que «venta y alquiler» no sea una rama sino
        // una vuelta más de este bucle.
        List<MotorDeCaptura.BloqueOperacion> deLaOperacion = new ArrayList<>();
        for (OperacionInmobiliaria op : declaradas) {
            List<Pregunta> economicas = new ArrayList<>();
            for (String base : GuionRegistroPropiedad.DE_LA_OPERACION) {
                Pregunta pregunta = GuionRegistroPropiedad.pregunta(
                        GuionRegistroPropiedad.para(base, op));
                if (pregunta != null) {
                    economicas.add(pregunta.en(Pregunta.SECCION_OPERACION, economicas.size()));
                }
            }
            // Y las condiciones GOBERNADAS de esa operacion, dentro del bloque
            // (V73). Aqui es donde el Corte 0C se ve: `garantia_meses` sale del
            // catalogo con su exigencia resuelta para ESTE par (tipo, operacion),
            // asi que en ALQUILER puede bloquear la publicacion y en VENTA ni
            // siquiera aparecer.
            //
            // Nunca un saco comun de condiciones comerciales. Con VENTA+ALQUILER
            // declaradas hay dos bloques y cada uno trae lo suyo: una lista
            // compartida obligaria al cliente a decidir a que encargo pertenece
            // cada respuesta, que es precisamente lo que no puede decidir.
            for (CatalogoAtributo atributo : condiciones.aplicablesA(
                    actor.idOrganizacion(), codigoTipo, op.codigo())) {
                economicas.add(delCatalogoDeEncargo(atributo, codigoTipo, op.codigo()));
            }
            deLaOperacion.add(new MotorDeCaptura.BloqueOperacion(op.name(),
                    op.rotuloDeLaCondicion(), List.copyOf(economicas)));
        }

        return new DefinicionCaptura(intencionValidada, nombreDelTipo(codigoTipo),
                declaradas.stream().map(OperacionInmobiliaria::name).toList(),
                List.copyOf(comunes), List.copyOf(delTipo), List.copyOf(deLaOperacion));
    }

    /**
     * <b>Las opciones que salen de una tabla del sistema</b>, no de una lista
     * escrita en el guion.
     *
     * <p>Hoy solo el distrito. Estaba declarado como texto libre, y el
     * resultado es que cada cliente llevaba su propia lista: el formulario de
     * locales tiene 43 distritos de Lima escritos a mano
     * ({@code catalogos-local.ts}), y KAIROS habria necesitado una segunda
     * copia. Es el mismo defecto que D-E4-3 cerró para los rangos, aplicado al
     * catálogo geográfico: una lista con dos dueños se separa.
     *
     * <p>Con la tabla vacía la pregunta se devuelve tal cual —texto libre— en
     * vez de con una lista de cero elementos: un selector sin opciones bloquea
     * el alta, y no poder registrar por un catálogo sin sembrar sería peor que
     * admitir un nombre escrito a mano.
     */
    private Pregunta conCatalogoDelSistema(Pregunta pregunta) {
        if (pregunta == null || !GuionRegistroPropiedad.DISTRITO.equals(pregunta.clave())) {
            return pregunta;
        }
        List<String> nombres = distritos.findByActivoTrueOrderByNombre().stream()
                .map(Distrito::getNombre)
                .toList();
        if (nombres.isEmpty()) {
            return pregunta;
        }
        // El distrito se ofrece con su nombre como valor Y como rotulo: el
        // catalogo de distritos no tiene codigo estable todavia, y fabricar uno
        // aqui seria inventar vocabulario en el sitio equivocado.
        return new Pregunta(pregunta.clave(), pregunta.rotulo(), "LISTA", pregunta.unidad(),
                nombres.stream().map(n -> new MotorDeCaptura.Opcion(n, n)).toList(),
                pregunta.obligatoria(), pregunta.ayuda());
    }

    /**
     * Las operaciones declaradas, o una regla de negocio que explica que falta.
     *
     * <p>Traduce la {@code IllegalArgumentException} del enum —que es la que
     * sabe por qué COMPRA es una perspectiva y AMBAS una combinación— en la
     * excepción que el cable convierte en 400 con su mensaje. El enum no debe
     * conocer la capa de servicio, y el servicio no debe reescribir el motivo.
     */
    /**
     * <b>Una pregunta con todo lo que el catalogo declara sobre ella</b> (0B).
     *
     * <p>Hasta este corte el motor construia la pregunta con {@code opciones =
     * null} y {@code ayuda = null}, y pisaba el {@code orden} con la posicion
     * del bucle. Consecuencia: la unica LISTA sembrada viajaba como TEXTO
     * —porque el control se deriva de si hay opciones—, y los dos endpoints de
     * definicion publicaban un orden distinto para la misma clave sin que nadie
     * lo notara.
     *
     * <p>Ahora el orden es <b>el del catalogo</b>. No se recalcula: si la
     * colocacion esta gobernada, el motor la transmite. Lo unico que sigue
     * saliendo del bucle es la posicion de las preguntas del guion, que no
     * estan en el catalogo y no tienen quien las ordene.
     */
    private static Pregunta delCatalogo(CatalogoAtributo atributo, String codigoTipo) {
        List<MotorDeCaptura.Opcion> opciones = atributo.opcionesVigentes().stream()
                .map(opcion -> new MotorDeCaptura.Opcion(opcion.getValor(), opcion.getRotulo()))
                .toList();
        Pregunta base = new Pregunta(atributo.getClave(), atributo.getRotulo(),
                atributo.getTipoDato(), atributo.getUnidad(), null, false, null);
        return conRestricciones(base, atributo)
                .conCatalogo(atributo.getFamilia(), atributo.getAyuda(),
                        atributo.exigenciaPara(codigoTipo).codigo(),
                        opciones.isEmpty() ? null : opciones)
                .en(Pregunta.SECCION_TIPO, atributo.getOrden());
    }

    /**
     * Lo mismo para una clave del ENCARGO, con una diferencia que importa: la
     * exigencia se resuelve con <b>las dos</b> coordenadas.
     *
     * <p>Es un metodo aparte y no un parametro anulable en el de arriba porque
     * ese parametro se olvidaria: la llamada seguiria compilando y devolveria
     * OPC --«no bloquea»-- para una condicion que si bloquea. Un dato deja de
     * exigirse y nadie se entera hasta que se publica algo incompleto.
     */
    private static Pregunta delCatalogoDeEncargo(CatalogoAtributo atributo, String codigoTipo,
                                                 String codigoOperacion) {
        List<MotorDeCaptura.Opcion> opciones = atributo.opcionesVigentes().stream()
                .map(opcion -> new MotorDeCaptura.Opcion(opcion.getValor(), opcion.getRotulo()))
                .toList();
        // La clave viaja CALIFICADA -- `garantia_meses:ALQUILER` --, igual que
        // el importe y la exclusividad. No es cosmetica: con la venta y el
        // alquiler declarados a la vez, la respuesta tiene que decir a cual de
        // los dos encargos pertenece, y la clave desnuda no lo dice. El
        // separador `:` no colisiona porque ninguna clave del catalogo lo lleva.
        String calificada = GuionRegistroPropiedad.para(atributo.getClave(),
                OperacionInmobiliaria.desde(codigoOperacion));
        Pregunta base = new Pregunta(calificada, atributo.getRotulo(),
                atributo.getTipoDato(), atributo.getUnidad(), null, false, null);
        return conRestricciones(base, atributo)
                .conCatalogo(atributo.getFamilia(), atributo.getAyuda(),
                        atributo.exigenciaPara(codigoTipo, codigoOperacion).codigo(),
                        opciones.isEmpty() ? null : opciones)
                .en(Pregunta.SECCION_OPERACION, atributo.getOrden());
    }

    private static List<OperacionInmobiliaria> operacionesDeclaradas(String valores) {
        try {
            return OperacionInmobiliaria.desdeLista(valores);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
    }

    private static String nombreDelTipo(String codigoTipo) {
        return AtributosGobernados.nombreDelTipo(codigoTipo);
    }

    /**
     * Las dos que ordenan el resto, en su orden. Salen de la misma lista de
     * obligatorias que usa {@link #loQueFalta}, cortada donde el plan empieza a
     * depender de ellas: {@code obligatorias(sin operaciones)} devuelve
     * exactamente {@code [tipoPropiedad, operaciones, ...]} y las demás no se
     * pueden preguntar todavía.
     */
    @Override
    public List<Pregunta> apertura(String intencion, Actor actor) {
        intencionValidada(intencion);
        List<Pregunta> preguntas = new ArrayList<>();
        for (String clave : List.of(GuionRegistroPropiedad.TIPO_PROPIEDAD,
                GuionRegistroPropiedad.OPERACIONES)) {
            Pregunta pregunta = GuionRegistroPropiedad.pregunta(clave);
            if (pregunta != null) {
                preguntas.add(pregunta.en(Pregunta.SECCION_APERTURA, preguntas.size()));
            }
        }
        return List.copyOf(preguntas);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstadoCaptura> enCurso(Actor actor) {
        return borradores.enCurso(actor.idOrganizacion()).stream()
                .map(borrador -> estado(borrador, documentos.comoMapa(borrador.getDatosConocidos()),
                        documentos.comoLista(borrador.getDatosFaltantes()), actor))
                .toList();
    }

    @Override
    @Transactional
    public Ejecucion ejecutar(long idBorrador, String claveIdempotencia, Procedencia procedente,
                              Actor actor) {
        Procedencia procedencia = Procedencia.oPantalla(procedente);
        BorradorCaptura borrador = cargar(idBorrador, actor);

        // Un borrador ya ejecutado devuelve lo que produjo, en vez de fallar.
        // Es la misma promesa que la idempotencia de comandos: reintentar y
        // acertar a la primera tienen que ser indistinguibles.
        //
        // Y "lo mismo" es LO MISMO, no solo el id: la respuesta se reconstruye
        // leyendo la propiedad. Devolver aqui `null` y una lista vacia —que es
        // lo que hacia -- dejaba al reintento sin el codigo ni los encargos, y
        // un canal conversacional que reintenta no puede recibir una respuesta
        // mas pobre que la del intento que si llego.
        if (BorradorCaptura.EJECUTADO.equals(borrador.getEstado())) {
            return reconstruir(borrador, actor);
        }
        if (!borrador.estaEnCurso()) {
            throw new ReglaNegocioException(
                    "El borrador " + borrador.getCodigo() + " esta descartado.");
        }

        Map<String, Object> conocido = documentos.comoMapa(borrador.getDatosConocidos());
        List<String> faltante = loQueFalta(actor, conocido);
        if (!faltante.isEmpty()) {
            throw new ReglaNegocioException(
                    "Todavia falta: " + String.join(", ", faltante)
                            + ". El alta no se ejecuta a medias.");
        }

        ResultadoRegistro resultado = propiedades.registrar(
                comandoDesde(actor, conocido, borrador, claveIdempotencia,
                        procedenciaDelAlta(borrador, procedencia)), actor);

        // `registrar` ya cierra el borrador cuando recibe su id. Se relee para
        // no devolver el objeto en memoria con un estado que la otra rama pudo
        // haber cambiado.
        BorradorCaptura despues = cargar(idBorrador, actor);
        if (despues.estaEnCurso()) {
            despues.ejecutado("PROPIEDAD", resultado.idPropiedad());
            borradores.save(despues);
        }
        return new Ejecucion(borrador.getId(), resultado.idPropiedad(), resultado.codigo(),
                resultado.idsEncargos(), resultado.reintento());
    }

    /**
     * La respuesta de un borrador que ya se ejecuto, leida de la propiedad que
     * produjo.
     *
     * <p>Se reconstruye en vez de guardarse porque la propiedad es la fuente:
     * si desde entonces se abrio un segundo encargo —el de alquiler junto al de
     * venta, que es el caso que el modelo universal existe para admitir—, la
     * respuesta lo refleja en vez de repetir una foto vieja.
     */
    private Ejecucion reconstruir(BorradorCaptura borrador, Actor actor) {
        Long idPropiedad = borrador.getEntidadObjetivoId();
        if (idPropiedad == null) {
            // No deberia poder pasar: `ck_borrador_objetivo` (V56) exige la
            // entidad cuando el estado es ejecutado. Si pasara, se dice.
            return new Ejecucion(borrador.getId(), null, null, List.of(), true);
        }
        FichaPropiedadUniversal ficha = propiedades.consultar(idPropiedad, actor);
        return new Ejecucion(borrador.getId(), idPropiedad, ficha.codigo(),
                ficha.encargos().stream().map(EncargoFicha::idEncargo).toList(), true);
    }

    @Override
    @Transactional
    public EstadoCaptura descartar(long idBorrador, Actor actor) {
        BorradorCaptura borrador = cargar(idBorrador, actor);
        if (borrador.estaEnCurso()) {
            borrador.descartar();
            borradores.save(borrador);
        }
        return estado(borrador, documentos.comoMapa(borrador.getDatosConocidos()),
                List.of(), actor);
    }

    // ==================================================================
    // Lo que sabe y lo que falta
    // ==================================================================

    /**
     * Incorpora lo que acaba de llegar, validando cada valor <b>al entrar</b>.
     * Un valor invalido guardado ahora es una pregunta repetida al final.
     */
    private void incorporar(Actor actor, Map<String, Object> conocido, Map<String, String> datos) {
        if (datos == null || datos.isEmpty()) {
            return;
        }
        datos.forEach((clave, valor) -> {
            if (clave == null || clave.isBlank()) {
                return;
            }
            String limpia = clave.trim();
            if (valor == null || valor.isBlank()) {
                // Vaciar un dato es legitimo: el usuario se corrige.
                conocido.remove(limpia);
                return;
            }
            conocido.put(limpia, validar(actor, conocido, limpia, valor.trim()));
        });
    }

    private Object validar(Actor actor, Map<String, Object> conocido, String clave, String valor) {
        if (GuionRegistroPropiedad.esDeLaOperacion(clave)) {
            return validarDeLaOperacion(conocido, clave, valor);
        }
        switch (clave) {
            case GuionRegistroPropiedad.TIPO_PROPIEDAD -> {
                return AtributosGobernados.codigoDelTipo(valor)
                        .map(AtributosGobernados::nombreDelTipo)
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Tipo de propiedad desconocido: \"" + valor + "\". Son siete: LOCAL, "
                                        + "OFICINA, DEPARTAMENTO, CASA, TERRENO, ALMACEN y OTRO."));
            }
            case GuionRegistroPropiedad.OPERACIONES -> {
                // Se normaliza a la forma canonica: "alquiler, venta" entra y
                // "ALQUILER,VENTA" se guarda. El orden se respeta porque es el
                // orden en que se preguntaran las dos condiciones economicas.
                return operacionesDeclaradas(valor).stream()
                        .map(OperacionInmobiliaria::name)
                        .collect(java.util.stream.Collectors.joining(","));
            }
            case GuionRegistroPropiedad.TITULARES -> {
                // Se valida la FORMA aqui; que las personas existan y que las
                // cuotas sumen 100 lo comprueba el caso de uso, que es quien
                // puede consultarlo.
                titularesDe(valor);
                return valor;
            }
            default -> {
                if (GuionRegistroPropiedad.esEstructural(clave)) {
                    return valor;
                }
                // No es estructural: entonces tiene que ser del catalogo, y su
                // valor tiene que encajar con el tipo de dato declarado.
                return validarAtributo(actor, conocido, clave, valor);
            }
        }
    }

    /**
     * <b>El importe, la moneda y la exclusividad son de un ENCARGO</b>, y por
     * eso su clave tiene que decir de cuál.
     *
     * <p>Mientras el sistema solo supo alquilar, {@code importe} a secas no
     * tenía ambigüedad. Con venta y alquiler vivos sobre la misma propiedad, un
     * {@code importe} sin apellido tiene dos dueños y el segundo pisa al
     * primero — un precio de venta guardado como renta, que es justo lo que no
     * detecta ningún CHECK porque 180 000 es una renta perfectamente legal.
     *
     * <p>La comprobación contra lo declarado solo se hace <b>si ya se declaró
     * algo</b>: alguien puede dictar el precio antes de decir qué operaciones
     * quiere, y rechazarlo entonces sería mentir sobre un dato correcto. Es el
     * mismo criterio que {@link #validarAtributo} aplica al tipo.
     */
    private Object validarDeLaOperacion(Map<String, Object> conocido, String clave, String valor) {
        String base = GuionRegistroPropiedad.claveBase(clave);
        OperacionInmobiliaria operacion = GuionRegistroPropiedad.operacionDe(clave);
        if (operacion == null) {
            throw new ReglaNegocioException(
                    "\"" + clave + "\" no dice de que encargo es. Se escribe " + base
                            + ":VENTA o " + base + ":ALQUILER, porque una propiedad puede tener "
                            + "las dos operaciones vivas a la vez y entonces hay dos.");
        }
        List<OperacionInmobiliaria> declaradas = operacionesConocidas(conocido);
        if (!declaradas.isEmpty() && !declaradas.contains(operacion)) {
            throw new ReglaNegocioException(
                    "Llego \"" + clave + "\", pero el alta declara "
                            + declaradas.stream().map(OperacionInmobiliaria::name)
                                    .collect(java.util.stream.Collectors.joining(" y "))
                            + ". Anade " + operacion.name() + " a \"operaciones\" si la propiedad "
                            + "tambien se ofrece para eso, o corrige la clave.");
        }

        if (GuionRegistroPropiedad.IMPORTE.equals(base)) {
            try {
                return new BigDecimal(valor.replace(",", ""));
            } catch (NumberFormatException e) {
                throw new ReglaNegocioException("El " + operacion.nombreDelImporte()
                        + " llego como \"" + valor + "\".");
            }
        }
        if (GuionRegistroPropiedad.MONEDA.equals(base)) {
            String moneda = valor.toUpperCase(java.util.Locale.ROOT);
            if (!"PEN".equals(moneda) && !"USD".equals(moneda)) {
                throw new ReglaNegocioException("Moneda desconocida: \"" + valor + "\".");
            }
            return moneda;
        }
        return valor;
    }

    /**
     * Las operaciones que el borrador ya declaro, o vacio si todavia ninguna.
     *
     * <p>Un valor ilegible se trata como "todavia ninguna" en vez de reventar:
     * quien tiene que quejarse de {@code operaciones} es su propia validacion,
     * no la de un campo que casualmente se anota despues.
     */
    private static List<OperacionInmobiliaria> operacionesConocidas(Map<String, Object> conocido) {
        Object declarado = conocido.get(GuionRegistroPropiedad.OPERACIONES);
        if (declarado == null || declarado.toString().isBlank()) {
            return List.of();
        }
        try {
            return OperacionInmobiliaria.desdeLista(declarado.toString());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private Object validarAtributo(Actor actor, Map<String, Object> conocido, String clave,
                                   String valor) {
        // Una clave calificada es una condicion de SU encargo, y se valida
        // contra el otro sujeto: otro catalogo de aplicabilidad, otro trigger,
        // otra tabla. Preguntarle a `gobierno` por ella contestaria "es una
        // condicion del ENCARGO" -- correcto, pero aqui ya se sabe.
        OperacionInmobiliaria operacion = GuionRegistroPropiedad.operacionDe(clave);
        if (operacion != null) {
            return validarCondicion(actor, conocido, GuionRegistroPropiedad.claveBase(clave),
                    operacion, valor);
        }
        CatalogoAtributo definicion = gobierno.definicionDe(actor.idOrganizacion(), clave);
        String tipo = codigoDelTipoConocido(conocido);
        if (tipo != null && !definicion.aplicaA(tipo)) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" no aplica a una propiedad de tipo "
                            + AtributosGobernados.nombreDelTipo(tipo) + ".");
        }
        // Solo se comprueba que el VALOR encaja con su tipo de dato. La
        // aplicabilidad ya se miro arriba SI el tipo se conoce: alguien puede
        // dictar "tres dormitorios" antes de decir que es un departamento, y
        // rechazarlo entonces seria mentir sobre un dato correcto. Al guardar
        // se comprueba otra vez, ya con el tipo definitivo.
        ConversionDeValores.exigirCompatible(definicion, valor);
        return valor;
    }

    /**
     * Una condicion del ENCARGO dictada durante la captura.
     *
     * <p>La aplicabilidad necesita las dos coordenadas y solo una se conoce
     * siempre: la operacion viene en la propia clave, el tipo puede no haberse
     * dicho todavia. Mientras no se sepa, se comprueba lo que si se puede -- que
     * la clave existe, que es del ENCARGO y que el valor encaja con su tipo --
     * y la aplicabilidad se mira al guardar, que es cuando el tipo ya es firme.
     * Rechazar antes seria mentir sobre un dato correcto.
     */
    private Object validarCondicion(Actor actor, Map<String, Object> conocido, String clave,
                                    OperacionInmobiliaria operacion, String valor) {
        CatalogoAtributo definicion = condiciones.definicionDe(actor.idOrganizacion(), clave);
        String tipo = codigoDelTipoConocido(conocido);
        if (tipo != null && !definicion.aplicaA(tipo, operacion.codigo())) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" no aplica a "
                            + AtributosGobernados.nombreDelTipo(tipo) + " en "
                            + operacion.name() + ".");
        }
        ConversionDeValores.exigirCompatible(definicion, valor);
        return valor;
    }

    /**
     * Lo estructural que falta, mas lo obligatorio del catalogo para el tipo.
     *
     * <p>Mientras no se sepa el tipo, la segunda lista no existe: el catalogo
     * no puede decir que se pregunta de algo que todavia no se sabe que es.
     */
    private List<String> loQueFalta(Actor actor, Map<String, Object> conocido) {
        List<String> faltante = new ArrayList<>();
        // La lista se despliega sobre las operaciones ya declaradas: con dos,
        // faltan dos importes y dos monedas. Sin ninguna declarada llega hasta
        // `operaciones` y para, porque no se puede pedir un importe sin saber
        // de que es.
        for (String clave : GuionRegistroPropiedad.obligatorias(operacionesConocidas(conocido))) {
            if (!conocido.containsKey(clave)) {
                faltante.add(clave);
            }
        }
        String tipo = codigoDelTipoConocido(conocido);
        if (tipo != null) {
            faltante.addAll(gobierno.faltantesEntre(actor.idOrganizacion(), tipo, conocido.keySet()));
        }
        return faltante;
    }

    private EstadoCaptura estado(BorradorCaptura borrador, Map<String, Object> conocido,
                                 List<String> faltante, Actor actor) {
        Pregunta siguiente = faltante.isEmpty()
                ? null
                : preguntaDe(actor, conocido, faltante.get(0));
        return new EstadoCaptura(borrador.getId(), borrador.getCodigo(), borrador.getIntencion(),
                borrador.getEstado(), borrador.getCanal(), conocido, faltante, siguiente,
                faltante.isEmpty() && borrador.estaEnCurso(), borrador.getEntidadObjetivoTipo(),
                borrador.getEntidadObjetivoId(), Fechas.local(borrador.getActualizadoEn()));
    }

    /** La siguiente pregunta: del guion si es estructural, del catalogo si no. */
    private Pregunta preguntaDe(Actor actor, Map<String, Object> conocido, String clave) {
        Pregunta estructural = GuionRegistroPropiedad.pregunta(clave);
        if (estructural != null) {
            // Por el mismo catalogo que la definicion: un canal conversacional
            // y una pantalla no pueden ofrecer distritos distintos.
            return conCatalogoDelSistema(estructural);
        }
        CatalogoAtributo definicion = gobierno.definicionDe(actor.idOrganizacion(), clave);
        String tipo = codigoDelTipoConocido(conocido);
        return conRestricciones(new Pregunta(definicion.getClave(), definicion.getRotulo(),
                definicion.getTipoDato(), definicion.getUnidad(), null,
                tipo != null && definicion.esRequeridoPara(tipo), null), definicion);
    }

    /**
     * <b>Los limites del valor, publicados desde su unico dueno.</b>
     *
     * <p>{ Restricciones} existia en el contrato desde el principio y
     * viajaba SIEMPRE en null, asi que cada cliente acababa escribiendo su
     * propia copia: el formulario de locales llevaba "ambientes >= 1" a mano.
     * Eso es una regla con dos duenos, que es la misma clase de problema que
     * D-E4-3 cerro para los valores, aplicada a las reglas.
     *
     * <p>{ decimales} solo se declara cuando el catalogo lo sabe: un
     * ENTERO no admite decimales y eso se deduce de su tipo. Para un DECIMAL
     * NO se inventa una escala -- la de { valor_numero} es del
     * almacenamiento, no del concepto -- y viaja en null, que es la forma
     * honesta de decir que el catalogo no lo declara.
     */
    /**
     * Los limites que declara el catalogo, los CUATRO.
     *
     * <p>El maximo y la longitud maxima viajaban en {@code null} porque el
     * esquema no sabia declararlos; V72 les dio columna y aqui dejan de ser
     * huecos. El <b>minimo se conserva</b>: anadir el techo no puede costar el
     * suelo que ya existia desde V62, y esa es exactamente la clase de perdida
     * que se cuela al ampliar algo.
     */
    private static Pregunta conRestricciones(Pregunta pregunta, CatalogoAtributo definicion) {
        Integer decimales = definicion.tipo() == TipoDato.ENTERO ? 0 : null;
        if (definicion.getValorMinimo() == null && definicion.getValorMaximo() == null
                && definicion.getLongitudMaxima() == null && decimales == null) {
            return pregunta;
        }
        return new Pregunta(pregunta.clave(), pregunta.rotulo(), pregunta.seccion(),
                pregunta.familia(), pregunta.control(), pregunta.tipoDato(), pregunta.unidad(),
                pregunta.opciones(), pregunta.exigencia(), pregunta.obligatoria(),
                pregunta.ayuda(), pregunta.orden(),
                new MotorDeCaptura.Restricciones(sinEscalaDeAlmacen(definicion.getValorMinimo()),
                        sinEscalaDeAlmacen(definicion.getValorMaximo()),
                        definicion.getLongitudMaxima(), decimales));
    }

    /**
     * El limite, sin la escala de la columna que lo guarda.
     *
     * <p>{ valor_minimo} es { NUMERIC(14,4)} porque es una columna
     * compartida por todas las claves, asi que un minimo de 1 sale de la base
     * como { 1.0000}. Publicarlo asi seria publicar la escala del
     * ALMACENAMIENTO -- exactamente lo que el cliente no debe ver, y el mismo
     * defecto que { ValorLogico} corrige para los valores (D-E4-3).
     */
    private static java.math.BigDecimal sinEscalaDeAlmacen(java.math.BigDecimal limite) {
        return limite == null ? null
                : new java.math.BigDecimal(limite.stripTrailingZeros().toPlainString());
    }

    // ==================================================================
    // Del borrador al comando
    // ==================================================================

    /**
     * De donde sale el alta que produce un borrador.
     *
     * <p>Es una <b>fusion</b>, y las dos mitades vienen de sitios distintos a
     * proposito. El origen y el turno los pone quien ejecuta AHORA: si el
     * agente confirma desde la pantalla, el hecho lo pidio la pantalla y eso es
     * lo cierto. La <b>conversacion</b>, en cambio, la pone el borrador, porque
     * la conversacion que produjo la propiedad es aquella en la que se dictaron
     * los datos, no aquella —si es que hubo alguna— desde la que se apreto el
     * boton.
     *
     * <p>Es literalmente la frase que V56 se comprometio a poder construir:
     * <i>"esta propiedad la registro KAIROS a partir de la conversacion del
     * martes"</i>. Sin esta fusion, un alta dictada el martes y confirmada el
     * miercoles por pantalla se quedaria sin conversacion, que es justo el caso
     * que el borrador existe para permitir.
     */
    private static Procedencia procedenciaDelAlta(BorradorCaptura borrador, Procedencia ejecuta) {
        if (ejecuta.conversacionId() != null || borrador.getConversacionId() == null) {
            return ejecuta;
        }
        return new Procedencia(ejecuta.canal(), ejecuta.agente(), ejecuta.modelo(),
                ejecuta.modeloVersion(), borrador.getConversacionId(), ejecuta.turnoId(),
                ejecuta.mensajeId(), ejecuta.peticion(), ejecuta.herramienta());
    }

    private ComandoRegistro comandoDesde(Actor actor, Map<String, Object> conocido,
                                         BorradorCaptura borrador, String claveIdempotencia,
                                         Procedencia procedencia) {
        String tipo = texto(conocido, GuionRegistroPropiedad.TIPO_PROPIEDAD);

        // Una propiedad, tantos encargos como operaciones declaradas. Cada uno
        // lee SU importe, SU moneda y SU exclusividad de la clave calificada:
        // es lo que hace que los dos no se pisen y que cada serie economica
        // nazca en su sitio.
        List<OperacionSolicitada> operaciones = new ArrayList<>();
        for (OperacionInmobiliaria operacion : operacionesConocidas(conocido)) {
            operaciones.add(new OperacionSolicitada(operacion.name(),
                    numero(conocido, GuionRegistroPropiedad.para(
                            GuionRegistroPropiedad.IMPORTE, operacion)),
                    texto(conocido, GuionRegistroPropiedad.para(
                            GuionRegistroPropiedad.MONEDA, operacion)),
                    null, null, null, null,
                    booleano(conocido, GuionRegistroPropiedad.para(
                            GuionRegistroPropiedad.EXCLUSIVIDAD, operacion)), null, null,
                    // Y las condiciones gobernadas que se dictaron para ESTA
                    // operacion. Viajan dentro de ella porque en el alta el
                    // encargo todavia no tiene id: la operacion declarada es lo
                    // unico que puede decir a cual pertenece cada respuesta.
                    condicionesDictadas(actor, conocido, operacion)));
        }

        Ubicacion ubicacion = new Ubicacion(
                texto(conocido, GuionRegistroPropiedad.DIRECCION),
                texto(conocido, GuionRegistroPropiedad.DISTRITO),
                texto(conocido, GuionRegistroPropiedad.ZONA),
                numero(conocido, GuionRegistroPropiedad.LATITUD),
                numero(conocido, GuionRegistroPropiedad.LONGITUD),
                texto(conocido, GuionRegistroPropiedad.INTERIOR),
                // El piso NO viaja por aqui desde V67: es la clave de catalogo
                // `piso`, y el enrutador de autoridad la lleva a la misma
                // columna. Rellenarlo tambien aqui seria el segundo dueno otra vez.
                null,
                texto(conocido, GuionRegistroPropiedad.REFERENCIA),
                texto(conocido, GuionRegistroPropiedad.EDIFICIO));

        // Todo lo que no es estructural es un atributo del catalogo. No hay
        // lista blanca: si el motor lo acepto al entrar, es porque el catalogo
        // lo reconocio.
        List<ValorAtributo> atributos = new ArrayList<>();
        conocido.forEach((clave, valor) -> {
            // Una clave CALIFICADA no es de la propiedad: ya se repartio arriba,
            // dentro de su operacion. Meterla aqui la mandaria a
            // `atributo_propiedad` y el enrutador la rechazaria -- con razon,
            // porque una condicion negociada no es un hecho del inmueble.
            if (!GuionRegistroPropiedad.esEstructural(clave) && valor != null
                    && GuionRegistroPropiedad.operacionDe(clave) == null) {
                atributos.add(new ValorAtributo(clave, valor.toString()));
            }
        });

        return new ComandoRegistro(claveIdempotencia, procedencia,
                texto(conocido, GuionRegistroPropiedad.CODIGO), tipo,
                texto(conocido, GuionRegistroPropiedad.USO),
                texto(conocido, GuionRegistroPropiedad.DESCRIPCION), ubicacion,
                titularesDe(texto(conocido, GuionRegistroPropiedad.TITULARES)), atributos,
                List.copyOf(operaciones), borrador.getId());
    }

    /**
     * Lo que se dicto para UNA operacion y es una condicion gobernada del
     * encargo.
     *
     * <p>Se reconoce por dos cosas juntas: la clave viene calificada con esa
     * operacion, y su base <b>no</b> es una clave del guion economico. Lo
     * segundo importa: {@code importe:VENTA} tambien viene calificado y no es
     * una condicion gobernada -- vive en {@code condicion_economica}, que es
     * una columna del encargo y no un atributo suyo.
     */
    private List<ValorAtributo> condicionesDictadas(Actor actor, Map<String, Object> conocido,
                                                    OperacionInmobiliaria operacion) {
        List<ValorAtributo> pactadas = new ArrayList<>();
        conocido.forEach((clave, valor) -> {
            if (valor == null || !operacion.equals(GuionRegistroPropiedad.operacionDe(clave))) {
                return;
            }
            if (GuionRegistroPropiedad.esEstructural(clave)) {
                return;
            }
            String base = GuionRegistroPropiedad.claveBase(clave);
            // Que exista y sea del ENCARGO ya lo comprobo `validarCondicion` al
            // entrar; volver a preguntarlo aqui seria una consulta por respuesta.
            pactadas.add(new ValorAtributo(base, valor.toString()));
        });
        return pactadas.isEmpty() ? null : List.copyOf(pactadas);
    }

    /**
     * {@code "12:60,13:40"} o {@code "12"}. El formato es deliberadamente
     * pobre: lo escribe una maquina —el cliente, que ya resolvio los nombres
     * con el buscador de propietarios— y no una persona.
     */
    static List<Titular> titularesDe(String valor) {
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        List<Titular> titulares = new ArrayList<>();
        for (String parte : valor.split(",")) {
            String[] trozos = parte.trim().split(":");
            long idRol;
            try {
                idRol = Long.parseLong(trozos[0].trim());
            } catch (NumberFormatException e) {
                throw new ReglaNegocioException(
                        "Los titulares se declaran por id: \"" + parte.trim() + "\" no lo es. "
                                + "Formato: 12:60,13:40");
            }
            BigDecimal cuota = null;
            if (trozos.length > 1 && !trozos[1].isBlank()) {
                try {
                    cuota = new BigDecimal(trozos[1].trim());
                } catch (NumberFormatException e) {
                    throw new ReglaNegocioException(
                            "La cuota del titular " + idRol + " llego como \"" + trozos[1] + "\".");
                }
            }
            titulares.add(new Titular(idRol, cuota, titulares.isEmpty()));
        }
        return titulares;
    }

    // ==================================================================

    private BorradorCaptura abrir(Actor actor, String intencion, Procedencia procedencia) {
        long correlativo = borradores.countByOrganizacionId(actor.idOrganizacion()) + 1;
        BorradorCaptura borrador = BorradorCaptura.abrir(actor.idOrganizacion(),
                "CAP-%05d".formatted(correlativo), actor.idRolOperativo(), intencion,
                procedencia.canal(), procedencia.agente());
        borrador.nacioEn(procedencia.conversacionId());
        return borradores.save(borrador);
    }

    private BorradorCaptura cargar(long idBorrador, Actor actor) {
        return borradores.findByOrganizacionIdAndId(actor.idOrganizacion(), idBorrador)
                .orElseThrow(() -> new NoEncontradoException("Borrador de captura"));
    }

    private static String intencionValidada(String intencion) {
        String limpia = intencion == null || intencion.isBlank()
                ? REGISTRAR_PROPIEDAD : intencion.trim().toUpperCase(java.util.Locale.ROOT);
        if (!BorradorCaptura.INTENCIONES.contains(limpia)) {
            throw new ReglaNegocioException(
                    "Intencion de captura desconocida: \"" + intencion + "\". Por ahora solo "
                            + REGISTRAR_PROPIEDAD + ".");
        }
        return limpia;
    }

    private static String codigoDelTipoConocido(Map<String, Object> conocido) {
        Object tipo = conocido.get(GuionRegistroPropiedad.TIPO_PROPIEDAD);
        return tipo == null
                ? null
                : AtributosGobernados.codigoDelTipo(tipo.toString()).orElse(null);
    }

    private static String texto(Map<String, Object> conocido, String clave) {
        Object valor = conocido.get(clave);
        return valor == null ? null : valor.toString();
    }

    private static BigDecimal numero(Map<String, Object> conocido, String clave) {
        Object valor = conocido.get(clave);
        if (valor == null) {
            return null;
        }
        if (valor instanceof BigDecimal numero) {
            return numero;
        }
        try {
            return new BigDecimal(valor.toString());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("El valor de \"" + clave + "\" no es un numero.");
        }
    }

    private static Boolean booleano(Map<String, Object> conocido, String clave) {
        Object valor = conocido.get(clave);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Boolean booleano) {
            return booleano;
        }
        String texto = valor.toString().toLowerCase(java.util.Locale.ROOT);
        return "true".equals(texto) || "si".equals(texto) || "1".equals(texto);
    }
}
