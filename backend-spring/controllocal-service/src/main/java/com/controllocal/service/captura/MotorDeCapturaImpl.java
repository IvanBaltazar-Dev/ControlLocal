package com.controllocal.service.captura;

import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.BorradorCapturaRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * El motor, para la intencion {@code REGISTRAR_PROPIEDAD}.
 *
 * <h2>Como decide que falta</h2>
 * Despliega los bloqueadores sobre lo que ya sabe:
 * <ol>
 *   <li>los datos minimos de la propiedad y, si se declaro una operacion, el
 *       importe y la moneda propios de cada encargo, que declara
 *       {@link GuionRegistroPropiedad};</li>
 *   <li>lo <b>obligatorio del catalogo</b> para ese tipo, que sale de
 *       {@code catalogo_atributo} — y por eso un terreno pide zonificacion y un
 *       departamento pide dormitorios sin que este fichero sepa nada de
 *       ninguno de los dos.</li>
 * </ol>
 *
 * <p>La segunda lista <b>no se puede calcular hasta conocer el tipo</b>, y eso
 * explica el orden: mientras {@code tipoPropiedad} falte, el motor no puede
 * resolver los atributos del catalogo. Un borrador puede conservarse
 * incompleto; al ejecutar se exigen los minimos identificables y los pares
 * economicos solo de las operaciones declaradas.
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
    private final SuficienciaInmueble suficiencia;
    private final SuficienciaRegistrada registrada;
    private final PreparacionDeCaptura preparacion;
    private final EventoDominioRepository eventos;
    private final GobiernoCatalogoCapturaService catalogos;
    private final ValidadorRespuestaCaptura validadorRespuestas;

    public MotorDeCapturaImpl(BorradorCapturaRepository borradores,
                              PropiedadUniversalService propiedades,
                              AtributosGobernados gobierno, AtributosDeEncargo condiciones,
                              Documentos documentos, DistritoRepository distritos,
                              SuficienciaInmueble suficiencia, SuficienciaRegistrada registrada,
                              PreparacionDeCaptura preparacion, EventoDominioRepository eventos,
                              GobiernoCatalogoCapturaService catalogos, ValidadorRespuestaCaptura validadorRespuestas) {
        this.borradores = borradores;
        this.propiedades = propiedades;
        this.gobierno = gobierno;
        this.condiciones = condiciones;
        this.documentos = documentos;
        this.distritos = distritos;
        this.suficiencia = suficiencia;
        this.registrada = registrada;
        this.preparacion = preparacion;
        this.eventos = eventos;
        this.catalogos = catalogos;
        this.validadorRespuestas = validadorRespuestas;
    }

    // ==================================================================

    @Override
    @Transactional
    public EstadoCaptura avanzar(Avance comando, Procedencia procedente, Actor actor) {
        exigirActor(actor);
        if (comando == null) throw new ReglaNegocioException("El avance de captura es obligatorio.");
        Procedencia procedencia = Procedencia.oPantalla(procedente);
        String intencionValidada = intencionValidada(comando.intencion());
        if (comando.idBorrador() == null && comando.versionEsperada() != null)
            throw new ReglaNegocioException("Una apertura no tiene una versión anterior.");
        BorradorCaptura borrador = comando.idBorrador() == null
                ? abrir(actor, intencionValidada, procedencia)
                : cargar(comando.idBorrador(), actor);
        if (comando.idBorrador() != null) exigirVersion(borrador, comando.versionEsperada());

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
        // La conversacion, en cambio, se estampa UNA vez (V59): un borrador
        // nace de una conversacion o de ninguna, y si la ultima que lo tocara
        // pudiera sobrescribirla, el rastro diria que la propiedad salio de la
        // conversacion equivocada.
        borrador.nacioEn(procedencia.conversacionId());

        Map<String, Object> conocido = documentos.comoMapa(borrador.getDatosConocidos());
        Map<String, Object> anterior = new java.util.LinkedHashMap<>(conocido);
        Map<String, MetadatoRespuesta> metadatos = registrada.metadatos(borrador.getMetadatosRespuestas());
        Map<String, MetadatoRespuesta> metasAnteriores = new java.util.LinkedHashMap<>(metadatos);
        String contextoAnterior = borrador.getContextoCaptura();
        Map<String, String> entradas = new java.util.LinkedHashMap<>();
        comando.respuestas().forEach((clave, respuesta) -> {
            MetadatoRespuesta metadato = validadorRespuestas.validar(actor,clave,respuesta);
            entradas.put(clave, respuesta.estado() == SuficienciaInmueble.EstadoDato.CONOCIDO
                    ? respuesta.valor() : null);
            metadatos.put(clave,metadato);
        });
        incorporarContexto(entradas, conocido, comando.contexto());
        incorporar(actor, conocido, entradas);
        SuficienciaInmueble.Contexto contexto = contextoDe(conocido,
                comando.contexto() == null ? registrada.contexto(contextoAnterior) : comando.contexto(),
                comando.contexto() != null);
        contexto=validadorRespuestas.reconciliarContexto(registrada.contexto(contextoAnterior),contexto,
                comando.contexto(),comando.respuestas(),metadatos);
        validarZonificacion(actor, conocido, metadatos, contexto);
        borrador.setContextoCaptura(registrada.contextoJson(contexto));
        borrador.setMetadatosRespuestas(registrada.metadatosJson(metadatos));
        borrador.setCanal(procedencia.canal());
        borrador.setAgente(procedencia.agente());

        List<String> faltante = loQueFalta(actor, conocido, contexto, metadatos);
        borrador.anotar(documentos.objeto(conocido), documentos.lista(faltante));
        borradores.saveAndFlush(borrador);
        List<String> cambios = clavesCambiadas(anterior, conocido, metasAnteriores, metadatos);
        if (comando.idBorrador() == null || !cambios.isEmpty()
                || !java.util.Objects.equals(registrada.contexto(contextoAnterior), contexto)) {
            Map<String, Object> carga = new java.util.LinkedHashMap<>();
            carga.put("anterior", anterior); carga.put("conocido", conocido);
            carga.put("metadatosAnteriores", metasAnteriores); carga.put("metadatos", metadatos);
            carga.put("contextoAnterior", registrada.contexto(contextoAnterior)); carga.put("contexto", contexto);
            carga.put("version", borrador.getVersion());
            eventos.save(procedencia.sellar(EventoDominio.de(actor.idOrganizacion(), "CAPTURA_AVANZADA",
                    "BORRADOR_CAPTURA", borrador.getId(), actor.idRolOperativo(), procedencia.canal()))
                    .con(documentos.objeto(carga)));
        }
        return estado(borrador, conocido, faltante, actor, cambios);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoCaptura consultar(long idBorrador, Actor actor) {
        BorradorCaptura borrador = cargar(idBorrador, actor);
        Map<String, Object> conocido = documentos.comoMapa(borrador.getDatosConocidos());
        return estado(borrador, conocido, faltantesActuales(borrador, conocido, actor), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public CapacidadSobreCaptura capacidadSobre(long idBorrador, String operacion, String atributo, Actor actor) {
        BorradorCaptura b = cargar(idBorrador, actor);
        Map<String, Object> conocido = documentos.comoMapa(b.getDatosConocidos());
        List<OperacionInmobiliaria> operaciones = operacionesConocidas(conocido);
        String motivo = null;
        if (atributo != null && !GuionRegistroPropiedad.TIPO_PROPIEDAD.equals(atributo)
                && !GuionRegistroPropiedad.OPERACIONES.equals(atributo)) {
            Object tipo = conocido.get(GuionRegistroPropiedad.TIPO_PROPIEDAD);
            if (tipo == null) motivo = "TIPO_DE_PROPIEDAD_REQUERIDO";
            else {
                var contexto = registrada.contexto(b.getContextoCaptura());
                var definicion = definicion(b.getIntencion(), tipo.toString(),
                        operaciones.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")),
                        contexto == null ? null : contexto.jurisdiccion(), actor);
                boolean fisico = java.util.stream.Stream.concat(definicion.comunes().stream(), definicion.delTipo().stream())
                        .anyMatch(p -> atributo.equals(p.clave()));
                if (!fisico) {
                    boolean admitido = definicion.deLaOperacion().stream()
                            .filter(bloque -> operacion == null || operacion.equals(bloque.operacion()))
                            .anyMatch(bloque -> bloque.preguntas().stream().anyMatch(p -> atributo.equals(p.clave())
                                    || GuionRegistroPropiedad.para(atributo, OperacionInmobiliaria.valueOf(bloque.operacion())).equals(p.clave())));
                    if (!admitido) motivo = "ATRIBUTO_NO_APLICABLE";
                    else if (operacion == null && operaciones.size() > 1 && GuionRegistroPropiedad.operacionDe(atributo) == null) {
                        motivo = "OPERACION_REQUERIDA";
                    }
                }
            }
        }
        return new CapacidadSobreCaptura(b.getEstado(), b.estaEnCurso(), BorradorCaptura.EJECUTADO.equals(b.getEstado()),
                b.estaEnCurso() && loQueFalta(actor, conocido, contextoDe(conocido,
                        registrada.contexto(b.getContextoCaptura()), false),
                        registrada.metadatos(b.getMetadatosRespuestas())).isEmpty(),
                operaciones.stream().map(Enum::name).toList(), motivo);
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
                                        String jurisdiccion, Actor actor) {
        String intencionValidada = intencionValidada(intencion);
        String codigoTipo = AtributosGobernados.codigoDelTipo(tipoPropiedad)
                .orElseThrow(() -> new ReglaNegocioException(
                        "Tipo de propiedad desconocido: \"" + tipoPropiedad + "\". Tipos admitidos: "
                                + AtributosGobernados.tiposAdmitidos() + "."));
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
            delTipo.add(preguntaGobernada(atributo, codigoTipo, actor.idOrganizacion(), jurisdiccion));
        }

        // Las coordenadas necesarias proceden de la misma suficiencia por tipo;
        // los clientes reciben preguntas y opciones, sin otra matriz de negocio.
        var perfilContextual = suficiencia.evaluar(SuficienciaInmueble.Accion.CONFIRMAR_ENCARGO,
                new SuficienciaInmueble.Contexto(codigoTipo,null,null,null,Map.of(),Set.of(),jurisdiccion),Map.of());
        perfilContextual.aplicables().stream().map(SuficienciaInmueble.Campo::clave)
                .filter(clave->clave.startsWith("contexto.")).distinct()
                .forEach(clave->delTipo.add(preguntaContextual(clave).en(Pregunta.SECCION_TIPO,delTipo.size())));

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
                        atributo.exigenciaPara(codigoTipo) == com.controllocal.domain.inmueble.Exigencia.ALT
                                ? "OPC" : atributo.exigenciaPara(codigoTipo).codigo(),
                        opciones.isEmpty() ? null : opciones)
                .en(Pregunta.SECCION_TIPO, atributo.getOrden());
    }

    private Pregunta preguntaGobernada(CatalogoAtributo atributo, String tipo, long organizacion, String jurisdiccion) {
        Pregunta pregunta = delCatalogo(atributo, tipo).conDefinicion(
                "CAT:D:"+atributo.getId()+":"+atributo.getVersionDefinicion(),Long.toString(atributo.getVersionDefinicion()));
        if ("ascensores".equals(atributo.getClave())) {
            var sugerencias = java.util.stream.IntStream.rangeClosed(0, 4)
                    .mapToObj(n -> new Opcion(Integer.toString(n), n == 0 ? "Sin ascensor" : Integer.toString(n)))
                    .toList();
            return conControl(pregunta, "ENTERO_SUGERIDO", sugerencias);
        }
        if ("zonificacion".equals(atributo.getClave())) {
            var opciones = catalogos.opcionesVigentes(organizacion, atributo.getClave(), jurisdiccion);
            var lista = opciones.opciones().stream().map(o -> new Opcion(o.codigo(), o.rotulo())).toList();
            return conControl(pregunta, opciones.motivo() == null && !lista.isEmpty()
                    ? "CATALOGO_CERRADO" : "CATALOGO_PENDIENTE", lista)
                    .conDefinicion(opciones.referenciaCatalogo(),opciones.versionDefinicion()==null?null:opciones.versionDefinicion().toString());
        }
        return pregunta;
    }

    private static Pregunta preguntaContextual(String clave) {
        String coordenada=clave.substring("contexto.".length());
        String rotulo=switch(coordenada) {
            case SuficienciaInmueble.UNIDAD_DE_EDIFICIO -> "¿La unidad forma parte de un edificio?";
            case SuficienciaInmueble.MANTENIMIENTO -> "¿Corresponde un pago de mantenimiento en la oferta?";
            case SuficienciaInmueble.POTENCIAL_O_CAMBIO_USO -> "¿Se ofrece potencial de construcción o cambio de uso?";
            case SuficienciaInmueble.EXPLOTACION_COMERCIAL -> "¿Se ofrece el predio para explotación comercial del estacionamiento?";
            case SuficienciaInmueble.EDIFICIO_COMPLETO -> "¿Se ofrece el edificio completo?";
            default -> throw new ReglaNegocioException("Coordenada de contexto no reconocida.");
        };
        return new Pregunta(clave,rotulo,"LISTA",null,List.of(new Opcion("APLICA","Sí"),
                new Opcion("NO_APLICA","No")),false,
                "Para el Encargo, conserva el sustento de lo declarado; no se exige para guardar el avance.");
    }

    private static Pregunta conControl(Pregunta p, String control, List<Opcion> opciones) {
        return new Pregunta(p.clave(), p.rotulo(), p.seccion(), p.familia(), control, p.tipoDato(),
                p.unidad(), opciones, p.exigencia(), p.obligatoria(), p.ayuda(), p.orden(), p.restricciones(),p.referenciaCatalogo(),p.versionDefinicion());
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
        // Si el tipo es IMPORTE, su compañera se forma como
        // `moneda:precio_estacionamiento_adicional:ALQUILER` y no se publica
        // como una segunda condición.
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
     * Las preguntas de apertura orientan el plan, pero no convierten una
     * operación ausente en un bloqueo: registrar una propiedad sin encargo es
     * válido y el encargo se abre después, con sus guardas propias.
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
        exigirActor(actor);
        return borradores.enCursoDe(actor.idOrganizacion(), actor.idRolOperativo()).stream()
                .map(borrador -> {
                    var conocido = documentos.comoMapa(borrador.getDatosConocidos());
                    return estado(borrador, conocido, faltantesActuales(borrador, conocido, actor), actor);
                })
                .toList();
    }

    @Override
    @Transactional
    public Ejecucion ejecutar(long idBorrador, Integer versionEsperada, String claveIdempotencia, Procedencia procedente,
                              Actor actor) {
        claveIdempotencia = com.controllocal.service.soporte.Idempotencia.exigirClave(claveIdempotencia);
        Procedencia procedencia = Procedencia.oPantalla(procedente);
        BorradorCaptura borrador = cargar(idBorrador, actor);

        // Un borrador ya ejecutado devuelve lo que produjo, en vez de fallar.
        // Es la misma promesa que la idempotencia de comandos: reintentar y
        // acertar a la primera tienen que ser indistinguibles.
        //
        // Se conserva el resultado del comando original, no una ficha posterior:
        // nuevos encargos o ediciones no cambian lo que produjo aquella actuación.
        if (BorradorCaptura.EJECUTADO.equals(borrador.getEstado())) {
            var anterior = resultadoDel(borrador, claveIdempotencia, actor);
            if (anterior.estado() == EstadoDelResultado.EXITO) return anterior.resultado();
            throw new com.controllocal.service.excepcion.ConflictoException(
                    "La clave no permite conciliar la ejecución original de esta captura.");
        }
        if (!borrador.estaEnCurso()) {
            throw new ReglaNegocioException(
                    "El borrador " + borrador.getCodigo() + " esta descartado.");
        }
        exigirVersion(borrador, versionEsperada);

        Map<String, Object> conocido = documentos.comoMapa(borrador.getDatosConocidos());
        List<String> faltante = loQueFalta(actor, conocido, contextoDe(conocido,
                registrada.contexto(borrador.getContextoCaptura()), false),
                registrada.metadatos(borrador.getMetadatosRespuestas()));
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

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ResultadoConsultado consultarResultado(long idBorrador, String claveIdempotencia, Actor actor) {
        if (!actor.esAgente()) throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
        String clave = com.controllocal.service.soporte.Idempotencia.exigirClave(claveIdempotencia);
        BorradorCaptura b = cargar(idBorrador, actor);
        return resultadoDel(b, clave, actor);
    }

    private ResultadoConsultado resultadoDel(BorradorCaptura b, String clave, Actor actor) {
        var anterior = propiedades.consultarResultadoRegistro(clave, actor);
        if (anterior.isEmpty()) return new ResultadoConsultado(EstadoDelResultado.NO_LOCALIZABLE, null);
        var r = anterior.get();
        if (!BorradorCaptura.EJECUTADO.equals(b.getEstado()) || !"PROPIEDAD".equals(b.getEntidadObjetivoTipo())
                || !java.util.Objects.equals(b.getEntidadObjetivoId(), r.idPropiedad())) {
            throw new com.controllocal.service.excepcion.ConflictoException(
                    "La captura y la clave no identifican la misma actuación.");
        }
        return new ResultadoConsultado(EstadoDelResultado.EXITO,
                new Ejecucion(b.getId(), r.idPropiedad(), r.codigo(), r.idsEncargos(), true));
    }

    @Override
    @Transactional
    public EstadoCaptura descartar(long idBorrador, Integer versionEsperada, Procedencia procedente, Actor actor) {
        Procedencia procedencia = Procedencia.oPantalla(procedente);
        BorradorCaptura borrador = cargar(idBorrador, actor);
        exigirVersion(borrador, versionEsperada);
        if (borrador.estaEnCurso()) {
            borrador.descartar();
            borrador.setCanal(procedencia.canal());
            borrador.setAgente(procedencia.agente());
            borradores.saveAndFlush(borrador);
            eventos.save(procedencia.sellar(EventoDominio.de(actor.idOrganizacion(), "CAPTURA_AVANZADA",
                    "BORRADOR_CAPTURA", borrador.getId(), actor.idRolOperativo(), procedencia.canal()))
                    .con(documentos.objeto(Map.of("accion", "DESCARTAR_CAPTURA", "version", borrador.getVersion()))));
        }
        return estado(borrador, documentos.comoMapa(borrador.getDatosConocidos()),
                List.of(), actor);
    }

    @Override
    @Transactional
    public EstadoCaptura posponerRevision(long idBorrador, Integer versionEsperada,
            java.time.LocalDate hasta, String motivo, Procedencia procedente, Actor actor) {
        Procedencia procedencia = Procedencia.oPantalla(procedente);
        BorradorCaptura borrador = cargar(idBorrador, actor);
        exigirVersion(borrador, versionEsperada);
        if (!borrador.estaEnCurso()) throw new ReglaNegocioException("La captura no está en curso.");
        java.time.LocalDate hoy = preparacion.hoy();
        if (hasta == null || !hasta.isAfter(hoy))
            throw new ReglaNegocioException("La revisión debe posponerse a una fecha futura.");
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 500)
            throw new ReglaNegocioException("La posposición requiere un motivo de hasta 500 caracteres.");
        Map<String, Object> anterior = new java.util.LinkedHashMap<>();
        anterior.put("hasta", borrador.getRevisionPospuestaHasta()); anterior.put("motivo", borrador.getMotivoPosposicion());
        borrador.posponerRevision(hasta, motivo.trim());
        borrador.setCanal(procedencia.canal());
        borrador.setAgente(procedencia.agente());
        borradores.saveAndFlush(borrador);
        eventos.save(procedencia.sellar(EventoDominio.de(actor.idOrganizacion(), "CAPTURA_AVANZADA", "BORRADOR_CAPTURA",
                borrador.getId(), actor.idRolOperativo(), procedencia.canal())).con(documentos.objeto(Map.of(
                "accion", "POSPONER_REVISION", "anterior", anterior, "hasta", hasta,
                "motivo", motivo.trim(), "version", borrador.getVersion()))));
        var conocido = documentos.comoMapa(borrador.getDatosConocidos());
        return estado(borrador, conocido, faltantesActuales(borrador, conocido, actor), actor);
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
                // El importe y su moneda son un par. Al quitar el importe no
                // se conserva una moneda huérfana que luego parezca un dato
                // completo; al quitar la moneda se deja el importe para que
                // loQueFalta lo declare FALTANTE.
                if (GuionRegistroPropiedad.atributoDeMoneda(limpia) == null) {
                    OperacionInmobiliaria operacion = GuionRegistroPropiedad.operacionDe(limpia);
                    if (operacion != null
                            && GuionRegistroPropiedad.IMPORTE.equals(
                                    GuionRegistroPropiedad.claveBase(limpia))) {
                        conocido.remove(GuionRegistroPropiedad.para(
                                GuionRegistroPropiedad.MONEDA, operacion));
                    } else if (!GuionRegistroPropiedad.MONEDA.equals(
                            GuionRegistroPropiedad.claveBase(limpia))) {
                        conocido.remove(GuionRegistroPropiedad.claveMonedaDeAtributo(limpia));
                    }
                }
                return;
            }
            conocido.put(limpia, validar(actor, conocido, limpia, valor.trim()));
        });
    }

    private Object validar(Actor actor, Map<String, Object> conocido, String clave, String valor) {
        if(clave.startsWith("contexto."))return ValidadorRespuestaCaptura.valorAplicabilidad(clave,valor).name();
        // `moneda:<clave>` es una compañera. La forma anidada
        // `moneda:<clave-calificada>` pertenece a una condición del encargo;
        // la forma corta `moneda:VENTA|ALQUILER` sigue siendo estructural.
        String condicionMoneda = GuionRegistroPropiedad.atributoDeMonedaDeEncargo(clave);
        if (condicionMoneda != null) {
            return validarMonedaDeCondicion(actor, conocido, condicionMoneda, valor);
        }
        String atributoMoneda = GuionRegistroPropiedad.atributoDeMonedaDePropiedad(clave);
        if (atributoMoneda != null) {
            return validarMonedaDeAtributo(actor, conocido, clave, valor);
        }
        if (GuionRegistroPropiedad.esDeLaOperacion(clave)) {
            return validarDeLaOperacion(conocido, clave, valor);
        }
        switch (clave) {
            case GuionRegistroPropiedad.TIPO_PROPIEDAD -> {
                return AtributosGobernados.codigoDelTipo(valor)
                        .map(AtributosGobernados::nombreDelTipo)
                        .orElseThrow(() -> new ReglaNegocioException(
                                "Tipo de propiedad desconocido: \"" + valor + "\". Tipos admitidos: "
                                        + AtributosGobernados.tiposAdmitidos() + "."));
            }
            case GuionRegistroPropiedad.OPERACIONES -> {
                // Se normaliza a la forma canonica: "alquiler, venta" entra y
                // "ALQUILER,VENTA" se guarda. El orden se respeta porque es el
                // orden en que se preguntaran las dos condiciones economicas.
                return operacionesDeclaradas(valor).stream()
                        .map(OperacionInmobiliaria::name)
                        .collect(java.util.stream.Collectors.joining(","));
            }
            case GuionRegistroPropiedad.USO -> {
                return usoDeclarado(valor);
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
        return validarVocabulario(definicion, valor);
    }

    /** Valida la moneda compañera de un importe gobernado de la propiedad. */
    private Object validarMonedaDeAtributo(Actor actor, Map<String, Object> conocido,
                                           String clave, String valor) {
        String atributo = GuionRegistroPropiedad.atributoDeMoneda(clave);
        // La llamada solo llega aqui despues de reconocer el prefijo, pero la
        // guarda mantiene el mensaje de negocio local si mañana cambia el
        // helper de claves.
        if (atributo == null) {
            throw new ReglaNegocioException("La clave de moneda no identifica un atributo.");
        }
        CatalogoAtributo definicion = gobierno.definicionDe(actor.idOrganizacion(), atributo);
        if (!definicion.tipo().llevaMoneda()) {
            throw new ReglaNegocioException(
                    "La clave \"" + clave + "\" solo puede acompañar a un IMPORTE; \""
                            + atributo + "\" es " + definicion.getTipoDato() + ".");
        }
        String tipo = codigoDelTipoConocido(conocido);
        if (tipo != null && !definicion.aplicaA(tipo)) {
            throw new ReglaNegocioException(
                    "El atributo \"" + atributo + "\" no aplica a una propiedad de tipo "
                            + AtributosGobernados.nombreDelTipo(tipo) + ".");
        }
        return ConversionDeValores.exigirMoneda(atributo, valor);
    }

    /** Valida la moneda compañera de un IMPORTE de condición del encargo. */
    private Object validarMonedaDeCondicion(Actor actor, Map<String, Object> conocido,
                                            String importeCalificado, String valor) {
        OperacionInmobiliaria operacion = GuionRegistroPropiedad.operacionDe(importeCalificado);
        String atributo = GuionRegistroPropiedad.claveBase(importeCalificado);
        if (operacion == null || atributo == null || atributo.isBlank()
                || GuionRegistroPropiedad.IMPORTE.equals(atributo)) {
            throw new ReglaNegocioException(
                    "La clave de moneda \"" + GuionRegistroPropiedad.claveMonedaDeAtributo(
                            importeCalificado) + "\" no identifica un IMPORTE de condicion.");
        }
        CatalogoAtributo definicion = condiciones.definicionDe(actor.idOrganizacion(), atributo);
        if (!definicion.tipo().llevaMoneda()) {
            throw new ReglaNegocioException(
                    "La clave de moneda solo puede acompañar a un IMPORTE; \""
                            + atributo + "\" es " + definicion.getTipoDato() + ".");
        }
        String tipo = codigoDelTipoConocido(conocido);
        if (tipo != null && !definicion.aplicaA(tipo, operacion.codigo())) {
            throw new ReglaNegocioException(
                    "El atributo \"" + atributo + "\" no aplica a "
                            + AtributosGobernados.nombreDelTipo(tipo) + " en "
                            + operacion.name() + ".");
        }
        return ConversionDeValores.exigirMoneda(atributo, valor);
    }

    /**
     * Una condicion del ENCARGO dictada durante la captura.
     *
     * <p>La aplicabilidad necesita las dos coordenadas y solo una se conoce
     * siempre: la operacion viene en la propia clave, el tipo puede no haberse
     * dicho todavia. Mientras no se sepa, se comprueba lo que si se puede -- que
     * la clave existe, que es del ENCARGO y que el valor encaja con su tipo --
     * y la aplicabilidad se mira al guardar, que es cuando el tipo ya es firme.
     * Si el tipo es IMPORTE, su moneda se valida por la compañera calificada y
     * el par incompleto queda FALTANTE en el borrador.
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
        return validarVocabulario(definicion, valor);
    }

    private Object validarVocabulario(CatalogoAtributo definicion, String valor) {
        if (definicion.tipo() != TipoDato.LISTA_MULTIPLE)
            return ConversionDeValores.exigirDelVocabulario(definicion, valor);
        List<String> valores;
        try { valores = documentos.comoLista(valor); }
        catch (IllegalStateException error) {
            throw new ReglaNegocioException("La respuesta múltiple requiere una lista JSON de códigos.");
        }
        valores.forEach(codigo -> ConversionDeValores.exigirDelVocabulario(definicion, codigo));
        return List.copyOf(valores);
    }

    /**
     * Lo estructural que falta, mas lo obligatorio del catalogo para el tipo y
     * la mitad ausente de cualquier IMPORTE aplicable, incluida una condicion
     * del encargo ya declarada.
     *
     * <p>Mientras no se sepa el tipo, la segunda lista no existe: el catalogo
     * no puede decir que se pregunta de algo que todavia no se sabe que es.
     */
    private List<String> loQueFalta(Actor actor, Map<String, Object> conocido,
                                    SuficienciaInmueble.Contexto contexto,
                                    Map<String, MetadatoRespuesta> metadatos) {
        List<String> faltante = new ArrayList<>();
        var datos = registrada.validarDatos(actor.idOrganizacion(), contexto, registrada.datos(conocido, metadatos));
        suficiencia.evaluar(SuficienciaInmueble.Accion.IDENTIFICAR, contexto, datos)
                .faltantes().forEach(f -> faltante.add(f.clave()));
        String tipo = codigoDelTipoConocido(conocido);
        if (tipo != null) {
            faltantesDeMoneda(actor, tipo, conocido, faltante);
        }
        return faltante.stream().distinct().toList();
    }

    /**
     * Un IMPORTE conocido sin su compañera no es dinero completo. El borrador
     * conserva el monto y pregunta la moneda; si llega la moneda primero,
     * pregunta el monto. Los atributos OPC también pasan por esta guarda para
     * no intentar guardar un par incompleto al ejecutar.
     */
    private void faltantesDeMoneda(Actor actor, String tipo, Map<String, Object> conocido,
                                   List<String> faltante) {
        for (CatalogoAtributo definicion : gobierno.aplicablesA(actor.idOrganizacion(), tipo)) {
            if (!definicion.tipo().llevaMoneda()) {
                continue;
            }
            String atributo = definicion.getClave();
            anadirFaltanteDelParDeImporte(conocido, atributo, faltante);
        }
    }

    /** Añade solo la mitad que falta del par, sin crear moneda para un opcional ausente. */
    private static void anadirFaltanteDelParDeImporte(Map<String, Object> conocido,
                                                      String importe, List<String> faltante) {
        String moneda = GuionRegistroPropiedad.claveMonedaDeAtributo(importe);
        boolean tieneImporte = tieneValor(conocido, importe);
        boolean tieneMoneda = tieneValor(conocido, moneda);
        if (tieneImporte && !tieneMoneda && !faltante.contains(moneda)) {
            faltante.add(moneda);
        } else if (!tieneImporte && tieneMoneda && !faltante.contains(importe)) {
            faltante.add(importe);
        }
    }

    private static boolean tieneValor(Map<String, Object> conocido, String clave) {
        Object valor = conocido.get(clave);
        return valor != null && !valor.toString().isBlank();
    }

    private EstadoCaptura estado(BorradorCaptura borrador, Map<String, Object> conocido,
                                 List<String> faltante, Actor actor) {
        return estado(borrador, conocido, faltante, actor, List.of());
    }

    private EstadoCaptura estado(BorradorCaptura borrador, Map<String, Object> conocido,
                                 List<String> faltanteAnterior, Actor actor, List<String> cambios) {
        var metadatos = registrada.metadatos(borrador.getMetadatosRespuestas());
        var contexto = contextoDe(conocido, registrada.contexto(borrador.getContextoCaptura()), false);
        List<String> faltante = borrador.estaEnCurso() ? faltanteAnterior : List.of();
        var datosMateriales = registrada.validarDatos(actor.idOrganizacion(), contexto, registrada.datos(conocido, metadatos));
        var identificacion = suficiencia.evaluar(SuficienciaInmueble.Accion.IDENTIFICAR, contexto, datosMateriales);
        List<SuficienciaInmueble.Evaluacion> encargos = operacionesConocidas(conocido).stream()
                .map(operacion -> suficiencia.evaluar(SuficienciaInmueble.Accion.CONFIRMAR_ENCARGO,
                        new SuficienciaInmueble.Contexto(contexto.tipoPropiedad(), contexto.usoOfrecido(),
                                contexto.objetoOfrecido(), operacion.name(), contexto.aplicabilidad(),
                                contexto.prestacionesOfrecidas(), contexto.jurisdiccion()), datosMateriales)).toList();
        Pregunta siguiente = faltante.stream()
                .filter(clave -> !metadatos.containsKey(clave) || !metadatos.get(clave).noSabe())
                .findFirst().map(clave -> preguntaDe(actor, conocido, clave, contexto.jurisdiccion())).orElse(null);
        RetornoCaptura retorno;
        try {
            boolean pendienteIdentidad = !identificacion.suficiente();
            retorno = preparacion.preparar(conocido, metadatos, contexto, cambios,
                    borrador.getCreadoEn(), pendienteIdentidad && borrador.estaEnCurso());
            if (retorno.revision() != null && borrador.getRevisionPospuestaHasta() != null
                    && borrador.getRevisionPospuestaHasta().isAfter(preparacion.hoy())) {
                retorno = new RetornoCaptura(retorno.estado(), retorno.grupos(), List.of(), cambios,
                        new RetornoCaptura.Revision(retorno.revision().diasDesdeDeteccion(), "POSPUESTA", List.of(),
                                borrador.getRevisionPospuestaHasta(), borrador.getMotivoPosposicion()), retorno.referenciaPrecio());
            }
            if (!borrador.estaEnCurso()) retorno = new RetornoCaptura(borrador.getEstado(),
                    retorno.grupos().stream().map(g -> new RetornoCaptura.Grupo(g.codigo(), g.resultado(),
                            g.datos(), List.of())).toList(), List.of(), cambios, null, retorno.referenciaPrecio());
        } catch (RuntimeException falloDePreparacion) {
            // El avance correcto ya es durable en esta TX. Se distingue el fallo
            // de preparación de un resultado vacío; nunca se reemite el hecho.
            retorno = new RetornoCaptura("FALLO_TECNICO", List.of(), List.of(), cambios, null,
                    new RetornoCaptura.ReferenciaPrecio("NO_EVALUADA", List.of()));
        }
        return new EstadoCaptura(borrador.getId(), borrador.getCodigo(), borrador.getIntencion(),
                borrador.getEstado(), borrador.getCanal(), conocido, faltante, siguiente,
                faltante.isEmpty() && borrador.estaEnCurso(), borrador.getEntidadObjetivoTipo(),
                borrador.getEntidadObjetivoId(), Fechas.local(borrador.getActualizadoEn()),
                borrador.getVersion(), contexto, metadatos, retorno, identificacion, encargos);
    }

    /** La siguiente pregunta: del guion si es estructural, del catalogo si no. */
    private Pregunta preguntaDe(Actor actor, Map<String, Object> conocido, String clave, String jurisdiccion) {
        if (clave.startsWith("contexto.") || "objetoOfrecido".equals(clave)
                || "operacion".equals(clave)) {
            if(clave.startsWith("contexto."))return preguntaContextual(clave);
            return new Pregunta(clave,"objetoOfrecido".equals(clave)?"Objeto ofrecido":"Operación",
                    "TEXTO",null,null,false,null);
        }
        String importeDeCondicion = GuionRegistroPropiedad.atributoDeMonedaDeEncargo(clave);
        if (importeDeCondicion != null) {
            OperacionInmobiliaria operacion = GuionRegistroPropiedad.operacionDe(importeDeCondicion);
            CatalogoAtributo definicion = condiciones.definicionDe(actor.idOrganizacion(),
                    GuionRegistroPropiedad.claveBase(importeDeCondicion));
            if (!definicion.tipo().llevaMoneda()) {
                throw new ReglaNegocioException(
                        "La clave \"" + clave + "\" no acompaña a un IMPORTE.");
            }
            Pregunta preguntaMoneda = GuionRegistroPropiedad.pregunta(GuionRegistroPropiedad.MONEDA);
            return new Pregunta(clave, "Moneda de " + definicion.getRotulo(),
                    preguntaMoneda.tipoDato(), preguntaMoneda.unidad(), preguntaMoneda.opciones(),
                    true, "Moneda del importe de " + definicion.getRotulo() + ".")
                    .en(Pregunta.SECCION_OPERACION, definicion.getOrden() + 1);
        }
        String atributoMonetario = GuionRegistroPropiedad.atributoDeMonedaDePropiedad(clave);
        if (atributoMonetario != null) {
            CatalogoAtributo definicion = gobierno.definicionDe(actor.idOrganizacion(), atributoMonetario);
            if (!definicion.tipo().llevaMoneda()) {
                throw new ReglaNegocioException(
                        "La clave \"" + clave + "\" no acompaña a un IMPORTE.");
            }
            Pregunta preguntaMoneda = GuionRegistroPropiedad.pregunta(GuionRegistroPropiedad.MONEDA);
            return new Pregunta(clave, "Moneda de " + definicion.getRotulo(),
                    preguntaMoneda.tipoDato(), preguntaMoneda.unidad(), preguntaMoneda.opciones(),
                    true, "Moneda del importe de " + definicion.getRotulo() + ".")
                    .en(Pregunta.SECCION_TIPO, definicion.getOrden() + 1);
        }
        // Una moneda de condición puede llegar primero. En ese caso loQueFalta
        // devuelve el IMPORTE calificado y debe resolverlo contra el catálogo
        // del encargo, no contra el catálogo físico de la propiedad.
        OperacionInmobiliaria operacion = GuionRegistroPropiedad.operacionDe(clave);
        if (operacion != null && !GuionRegistroPropiedad.esDeLaOperacion(clave)) {
            String tipo = codigoDelTipoConocido(conocido);
            CatalogoAtributo definicion = condiciones.definicionDe(actor.idOrganizacion(),
                    GuionRegistroPropiedad.claveBase(clave));
            if (tipo != null && !definicion.aplicaA(tipo, operacion.codigo())) {
                throw new ReglaNegocioException(
                        "El atributo \"" + GuionRegistroPropiedad.claveBase(clave)
                                + "\" no aplica a " + AtributosGobernados.nombreDelTipo(tipo)
                                + " en " + operacion.name() + ".");
            }
            return delCatalogoDeEncargo(definicion, tipo, operacion.codigo());
        }
        Pregunta estructural = GuionRegistroPropiedad.pregunta(clave);
        if (estructural != null) {
            // Por el mismo catalogo que la definicion: un canal conversacional
            // y una pantalla no pueden ofrecer distritos distintos.
            return conCatalogoDelSistema(estructural);
        }
        CatalogoAtributo definicion = gobierno.definicionDe(actor.idOrganizacion(), clave);
        String tipo = codigoDelTipoConocido(conocido);
        return preguntaGobernada(definicion, tipo, actor.idOrganizacion(), jurisdiccion);
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
            if (!GuionRegistroPropiedad.esEstructural(clave) && !clave.startsWith("contexto.") && valor != null
                    && GuionRegistroPropiedad.operacionDe(clave) == null
                    && GuionRegistroPropiedad.atributoDeMoneda(clave) == null) {
                CatalogoAtributo definicion = gobierno.definicionDe(actor.idOrganizacion(), clave);
                if (definicion.tipo() == TipoDato.LISTA_MULTIPLE) {
                    if (!(valor instanceof List<?> lista))
                        throw new ReglaNegocioException("La captura múltiple requiere sus valores estructurados.");
                    atributos.add(ValorAtributo.multiple(clave, lista.stream().map(Object::toString).toList()));
                } else if (definicion.tipo().llevaMoneda()) {
                    atributos.add(ValorAtributo.importe(clave, valor.toString(),
                            texto(conocido, GuionRegistroPropiedad.claveMonedaDeAtributo(clave))));
                } else {
                    atributos.add(new ValorAtributo(clave, valor.toString()));
                }
            }
        });

        return new ComandoRegistro(claveIdempotencia, procedencia,
                texto(conocido, GuionRegistroPropiedad.CODIGO), tipo,
                texto(conocido, GuionRegistroPropiedad.USO),
                texto(conocido, GuionRegistroPropiedad.DESCRIPCION), ubicacion,
                titularesDe(texto(conocido, GuionRegistroPropiedad.TITULARES)), atributos,
                List.of(), borrador.getId());
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
        borradores.bloquearSerie(actor.idOrganizacion());
        long correlativo = borradores.countByOrganizacionId(actor.idOrganizacion()) + 1;
        BorradorCaptura borrador = BorradorCaptura.abrir(actor.idOrganizacion(),
                "CAP-%05d".formatted(correlativo), actor.idRolOperativo(), intencion,
                procedencia.canal(), procedencia.agente());
        borrador.nacioEn(procedencia.conversacionId());
        return borradores.save(borrador);
    }

    private BorradorCaptura cargar(long idBorrador, Actor actor) {
        exigirActor(actor);
        return borradores.findByOrganizacionIdAndIdAndIdPersonaRol(actor.idOrganizacion(), idBorrador,
                        actor.idRolOperativo())
                .orElseThrow(() -> new NoEncontradoException("Borrador de captura"));
    }

    private static void exigirActor(Actor actor) {
        if (actor == null || !actor.esAgente())
            throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
    }

    private List<String> faltantesActuales(BorradorCaptura b, Map<String, Object> conocido, Actor actor) {
        return b.estaEnCurso() ? loQueFalta(actor, conocido,
                contextoDe(conocido, registrada.contexto(b.getContextoCaptura()), false),
                registrada.metadatos(b.getMetadatosRespuestas())) : List.of();
    }

    private void validarZonificacion(Actor actor, Map<String, Object> conocido,
                                     Map<String, MetadatoRespuesta> metadatos,
                                     SuficienciaInmueble.Contexto contexto) {
        Object valor = conocido.get("zonificacion");
        if (valor == null) return;
        var revisada=validadorRespuestas.validarZonificacion(actor.idOrganizacion(),valor.toString(),metadatos.get("zonificacion"),contexto);
        if(revisada.valorConocido()==null) conocido.remove("zonificacion");
        metadatos.put("zonificacion",revisada.metadato());
    }
    private static void exigirVersion(BorradorCaptura borrador, Integer versionEsperada) {
        if (versionEsperada == null || versionEsperada < 0)
            throw new ReglaNegocioException("La versión esperada de la captura es obligatoria.");
        if (borrador.getVersion() != versionEsperada)
            throw new com.controllocal.service.excepcion.ConflictoException("La captura cambió desde la versión revisada.");
    }

    private static List<String> clavesCambiadas(Map<String, Object> anterior, Map<String, Object> conocido,
            Map<String, MetadatoRespuesta> previos, Map<String, MetadatoRespuesta> nuevos) {
        Set<String> claves = new java.util.LinkedHashSet<>(anterior.keySet());
        claves.addAll(conocido.keySet()); claves.addAll(previos.keySet()); claves.addAll(nuevos.keySet());
        return claves.stream().filter(clave -> !java.util.Objects.equals(anterior.get(clave), conocido.get(clave))
                || !java.util.Objects.equals(previos.get(clave), nuevos.get(clave))).toList();
    }

    private static SuficienciaInmueble.Contexto contextoDe(Map<String, Object> conocido,
            SuficienciaInmueble.Contexto declarado, boolean comprobar) {
        String tipo = texto(conocido, GuionRegistroPropiedad.TIPO_PROPIEDAD);
        String uso = texto(conocido, GuionRegistroPropiedad.USO);
        if (declarado != null) {
            if (comprobar && declarado.tipoPropiedad() != null && tipo != null
                    && !AtributosGobernados.codigoDelTipo(tipo).equals(
                            AtributosGobernados.codigoDelTipo(declarado.tipoPropiedad())))
                throw new ReglaNegocioException("El tipo del contexto y de la respuesta deben coincidir.");
            if (comprobar && declarado.usoOfrecido() != null && uso != null
                    && !usoDeclarado(declarado.usoOfrecido()).equals(uso))
                throw new ReglaNegocioException("El uso del contexto y de la respuesta deben coincidir.");
        }
        var operaciones = operacionesConocidas(conocido);
        String operacion = operaciones.size() == 1 ? operaciones.getFirst().name() : null;
        if (declarado != null && declarado.operacion() != null && !declarado.operacion().isBlank()) {
            var seleccionadas = operacionesDeclaradas(declarado.operacion());
            if (seleccionadas.size() != 1)
                throw new ReglaNegocioException("El contexto debe identificar una sola operación.");
            if (comprobar && !operaciones.contains(seleccionadas.getFirst()))
                throw new ReglaNegocioException("La operación del contexto debe estar entre las observadas.");
            if (operaciones.contains(seleccionadas.getFirst())) operacion = seleccionadas.getFirst().name();
        }
        var aplicabilidad = new LinkedHashMap<String,SuficienciaInmueble.Aplicabilidad>(declarado==null?Map.of():declarado.aplicabilidad());
        for(String clave:SuficienciaInmueble.COORDENADAS_APLICABILIDAD) {
            String valor=texto(conocido,"contexto."+clave);
            if(valor==null)continue;
            SuficienciaInmueble.Aplicabilidad elegida;
            try { elegida=SuficienciaInmueble.Aplicabilidad.valueOf(valor.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch(IllegalArgumentException error){throw new ReglaNegocioException("La aplicabilidad admite APLICA, NO_APLICA o dato faltante.");}
            if(comprobar&&declarado.aplicabilidad().containsKey(clave)&&declarado.aplicabilidad().get(clave)!=elegida)
                throw new ReglaNegocioException("La respuesta y el contexto deben coincidir: "+clave);
            aplicabilidad.put(clave,elegida);
        }
        return new SuficienciaInmueble.Contexto(tipo, uso, declarado == null ? null : declarado.objetoOfrecido(),
                operacion, aplicabilidad,
                declarado == null ? Set.of() : declarado.prestacionesOfrecidas(),
                declarado == null ? null : declarado.jurisdiccion());
    }

    /** Tipo/uso/operación declarados conservan una sola autoridad en conocido. */
    private static void incorporarContexto(Map<String, String> entradas, Map<String, Object> conocido,
                                          SuficienciaInmueble.Contexto contexto) {
        if (contexto == null) return;
        incorporarCoordenada(entradas, GuionRegistroPropiedad.TIPO_PROPIEDAD, contexto.tipoPropiedad());
        incorporarCoordenada(entradas, GuionRegistroPropiedad.USO, contexto.usoOfrecido());
        contexto.aplicabilidad().forEach((clave,valor)->{
            if(!SuficienciaInmueble.COORDENADAS_APLICABILIDAD.contains(clave))
                throw new ReglaNegocioException("Coordenada de aplicabilidad no reconocida: "+clave);
            incorporarCoordenada(entradas,"contexto."+clave,valor.name());
        });
        if (contexto.operacion() != null && !contexto.operacion().isBlank()
                && !entradas.containsKey(GuionRegistroPropiedad.OPERACIONES)
                && !conocido.containsKey(GuionRegistroPropiedad.OPERACIONES)) {
            entradas.put(GuionRegistroPropiedad.OPERACIONES, contexto.operacion());
        }
    }

    private static void incorporarCoordenada(Map<String, String> entradas, String clave, String valor) {
        if (valor == null || valor.isBlank()) return;
        if (entradas.containsKey(clave) && (entradas.get(clave) == null || entradas.get(clave).isBlank()))
            throw new ReglaNegocioException("No puede retirarse una respuesta y declararla conocida en el contexto.");
        entradas.putIfAbsent(clave, valor);
    }

    private static String usoDeclarado(String valor) {
        return switch (valor.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "C", "COMERCIAL" -> "COMERCIAL";
            case "V", "VIVIENDA" -> "VIVIENDA";
            case "I", "INDUSTRIAL" -> "INDUSTRIAL";
            case "M", "MIXTO" -> "MIXTO";
            default -> throw new ReglaNegocioException("Uso no reconocido: " + valor + ".");
        };
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

}
