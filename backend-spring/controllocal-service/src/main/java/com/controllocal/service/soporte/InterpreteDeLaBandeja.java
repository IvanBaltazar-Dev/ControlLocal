package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Tarea;
import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.persistence.query.ExpedienteDeLaProspeccion;
import com.controllocal.persistence.query.ExpedienteDeLaPropiedad;
import com.controllocal.persistence.query.RangoDeRenta;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContrasteRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Avance;
import com.controllocal.service.soporte.InterpretacionDelAsunto.ComoEsta;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Hecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Interpretacion;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Renglon;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Ventana;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Convierte los asuntos de la bandeja en algo leído</b> (D-E2-1 §10, E2.4).
 *
 * <h2>Qué hace y qué no</h2>
 * Produce {@code comoEsta}, el expediente de cuatro renglones y la {@code
 * lectura}. <b>No decide el orden</b> —eso es {@link PoliticaDeDespacho}— ni
 * clasifica señales —eso es {@link PoliticaComercial}—: es un escalón más abajo
 * que las dos, donde el hecho suelto se convierte en hecho interpretado.
 *
 * <h2>Coste</h2>
 * <b>Tres consultas por página</b>, no tres por asunto:
 * <pre>
 *   1 · de que propiedad habla cada asunto   (una union, todos los tipos)
 *   2 · los cuatro renglones de esas propiedades
 *   3 · la serie de la renta, para la chispa
 * </pre>
 *
 * <h2>La regla que gobierna la redacción</h2>
 * <b>Ningún código técnico en el texto visible.</b> «Abierta el 22 jul ·
 * OPO-0098» no le dice nada a nadie: quien opera identifica la operación por la
 * dirección y la persona. Los códigos siguen vivos donde hacen falta —búsqueda,
 * soporte, la ficha real— y no aquí.
 */
@Component
public class InterpreteDeLaBandeja {

    private static final DateTimeFormatter DIA_Y_MES =
            DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es"));

    private final CaptacionRepository captaciones;
    private final PrecioPropiedadRepository precios;

    private final ContrasteRepository contrastes;
    private final ProspeccionRepository prospecciones;

    public InterpreteDeLaBandeja(CaptacionRepository captaciones,
                                 PrecioPropiedadRepository precios,
                                 ContrasteRepository contrastes,
                                 ProspeccionRepository prospecciones) {
        this.captaciones = captaciones;
        this.precios = precios;
        this.contrastes = contrastes;
        this.prospecciones = prospecciones;
    }

    /**
     * El contexto de una página de asuntos, cargado de una vez.
     *
     * <p>Se pide una sola vez por lectura de la bandeja y se consulta en memoria
     * asunto por asunto. Cargarlo dentro del bucle serían tres consultas por
     * fila, que es el N+1 que RC-003 quitó del listado y que vuelve cada vez que
     * alguien añade una capa de interpretación.
     */
    public record Contexto(Map<String, Long> propiedadPorAsunto,
                           Map<Long, ExpedienteDeLaPropiedad> expedientes,
                           Map<Long, List<BigDecimal>> series,
                           Map<Long, Contraste> contrastes,
                           Map<Long, ExpedienteDeLaProspeccion> prospecciones) {

        public static Contexto vacio() {
            return new Contexto(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        Long propiedadDe(String entidadTipo, Long entidadId) {
            return entidadId == null ? null : propiedadPorAsunto.get(clave(entidadTipo, entidadId));
        }

        static String clave(String entidadTipo, Long entidadId) {
            return entidadTipo + "#" + entidadId;
        }
    }

    /** Carga el contexto de una página. Tres consultas, y ninguna dentro del bucle. */
    public Contexto contextoDe(long idOrganizacion, Collection<AsuntoADescribir> asuntos) {
        if (asuntos.isEmpty()) {
            return Contexto.vacio();
        }
        Map<String, Long> porAsunto = new HashMap<>();
        for (Object[] fila : captaciones.propiedadPorAsunto(idOrganizacion)) {
            porAsunto.put(Contexto.clave((String) fila[0], numero(fila[1])), numero(fila[2]));
        }

        List<Long> idsPropiedad = asuntos.stream()
                .map(a -> porAsunto.get(Contexto.clave(a.entidadTipo(), a.entidadId())))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (idsPropiedad.isEmpty()) {
            return new Contexto(porAsunto, Map.of(), Map.of(), Map.of(),
                    prospeccionesDe(idOrganizacion, asuntos));
        }

        // La consulta devuelve una fila por CAPTACION y una propiedad puede tener
        // varias a lo largo del tiempo; se queda la primera, que el `order by`
        // deja siendo la mas reciente. Quedarse con la ultima daria el encargo
        // vencido de hace dos anios como si fuera el vigente.
        Map<Long, ExpedienteDeLaPropiedad> expedientes = new LinkedHashMap<>();
        for (ExpedienteDeLaPropiedad fila : captaciones.expedientesDe(idOrganizacion, idsPropiedad)) {
            expedientes.putIfAbsent(fila.getIdPropiedad(), fila);
        }

        Map<Long, List<BigDecimal>> series = new HashMap<>();
        for (Long idPropiedad : idsPropiedad) {
            List<BigDecimal> serie = precios.findByIdPropiedadOrderByFechaAscIdAsc(idPropiedad)
                    .stream().map(PrecioPropiedad::getMonto).toList();
            if (serie.size() > 1) {
                // Una linea plana se lee como lo que es: con un solo hito no hay
                // serie que ensenar, y una chispa de un punto sugiere movimiento
                // donde no lo hubo.
                series.put(idPropiedad, serie);
            }
        }
        return new Contexto(porAsunto, expedientes, series,
                contrastesDe(idOrganizacion, expedientes),
                prospeccionesDe(idOrganizacion, asuntos));
    }

    /**
     * El contraste de renta de cada propiedad de la pagina (E2.6).
     *
     * <p>Una consulta por propiedad, y no una por renglon: la pagina son cinco
     * asuntos y varios pueden apuntar al mismo inmueble.
     *
     * <p><b>Casi siempre devolvera la degradacion, y esta bien.</b> El rango
     * necesita diez propiedades distintas en la misma zona y tramo con renta
     * PUBLICADA, y el 2026-08-19 no habia ni un hito de renta publicada en toda
     * la base. Lo que viaja entonces es "sin referencia interna suficiente" con
     * su N -- nunca una cifra del sector, que es el salto que el producto existe
     * para no dar.
     */
    private Map<Long, Contraste> contrastesDe(long idOrganizacion,
                                              Map<Long, ExpedienteDeLaPropiedad> expedientes) {
        Map<Long, Contraste> porPropiedad = new HashMap<>();
        for (Map.Entry<Long, ExpedienteDeLaPropiedad> entrada : expedientes.entrySet()) {
            ExpedienteDeLaPropiedad datos = entrada.getValue();
            BandaDeMetraje banda = BandaDeMetraje.de(datos.getMetraje());
            String zona = datos.getDistrito();

            if (zona == null || zona.isBlank() || banda == null || datos.getMoneda() == null) {
                porPropiedad.put(entrada.getKey(), Contraste.sinGrupoComparable());
                continue;
            }
            RangoDeRenta rango = contrastes.rangoDeRenta(idOrganizacion, zona,
                    banda.desdeODesdeCero(), banda.hastaOInfinito(), datos.getMoneda());
            int observaciones = rango == null ? 0 : rango.getObservaciones();

            porPropiedad.put(entrada.getKey(),
                    PoliticaComercial.rangoPublicable(observaciones)
                            ? Contraste.enRango(rango.getMinimo(), rango.getMaximo(),
                                    datos.getRenta(), datos.getMoneda(), zona, banda.rotulo(),
                                    observaciones)
                            : Contraste.sinReferenciaSuficiente(zona, banda.rotulo(), observaciones));
        }
        return porPropiedad;
    }

    /**
     * El expediente de las prospecciones de la pagina, en UNA consulta.
     *
     * <p>Mismo patron que el contexto de propiedad, y por la misma razon: dentro
     * del bucle serian cinco consultas por pagina.
     *
     * <p><b>Una prospeccion no pasa por `propiedadPorAsunto`</b>, y no es un
     * olvido: todavia no hay encargo, asi que resolverla como inmueble daria los
     * cuatro renglones de una captacion que no existe.
     */
    private Map<Long, ExpedienteDeLaProspeccion> prospeccionesDe(
            long idOrganizacion, Collection<AsuntoADescribir> asuntos) {
        List<Long> ids = asuntos.stream()
                .filter(a -> Tarea.ENTIDAD_PROSPECCION.equals(a.entidadTipo()))
                .map(AsuntoADescribir::entidadId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ExpedienteDeLaProspeccion> porId = new HashMap<>();
        for (ExpedienteDeLaProspeccion fila : prospecciones.expedientesDe(idOrganizacion, ids)) {
            porId.put(fila.getIdProspeccion(), fila);
        }
        return porId;
    }

    /** Lo mínimo que hace falta saber de un asunto para describirlo. */
    public record AsuntoADescribir(String tipo, String entidadTipo, Long entidadId,
                                   String descripcion, Integer diasSinAccion,
                                   LocalDate fechaVencimiento, boolean dependeDeMi) {
    }

    // ==================================================================

    /** La interpretación de un asunto, con el contexto ya cargado. */
    public Interpretacion de(AsuntoADescribir asunto, Contexto contexto, LocalDate hoy) {
        Long idPropiedad = contexto.propiedadDe(asunto.entidadTipo(), asunto.entidadId());
        ExpedienteDeLaPropiedad datos = idPropiedad == null ? null
                : contexto.expedientes().get(idPropiedad);

        // Todo asunto lleva cuatro renglones; QUE cuatro depende de su etapa.
        // Una prospeccion es anterior a la captacion, asi que los suyos hablan de
        // la prospeccion y no de un encargo que todavia no existe.
        List<Renglon> expediente = Tarea.ENTIDAD_PROSPECCION.equals(asunto.entidadTipo())
                ? expedienteDeProspeccion(contexto.prospecciones().get(asunto.entidadId()), hoy)
                : expediente(datos, contexto.series().get(idPropiedad),
                        contexto.contrastes().get(idPropiedad), hoy);
        return new Interpretacion(comoEsta(asunto, hoy), expediente, lectura(datos, hoy));
    }

    // ==================================================================
    // CÓMO ESTÁ
    // ==================================================================

    /**
     * <b>Hasta tres hechos, en orden narrativo</b>: lo que ya está → lo que falta
     * → qué queda parado por ello.
     *
     * <p>El estado de cada uno sale del <b>tipo del asunto</b>, no del tono: es
     * lo que impide que un asunto en rojo pinte de rojo también sus buenas
     * noticias.
     */
    private static ComoEsta comoEsta(AsuntoADescribir asunto, LocalDate hoy) {
        List<Hecho> hechos = new ArrayList<>();
        String tipo = asunto.tipo() == null ? "" : asunto.tipo();

        // 1 · lo que ya esta
        if (!asunto.dependeDeMi()) {
            hechos.add(new Hecho(EstadoDelHecho.HECHO, "Tu parte esta hecha"));
        }

        // 2 · lo que falta, dicho como lo que hay que hacer
        hechos.add(new Hecho(EstadoDelHecho.FALTA, loQueFalta(tipo, asunto)));

        // 3 · el plazo, si lo hay
        if (asunto.fechaVencimiento() != null) {
            hechos.add(new Hecho(EstadoDelHecho.PLAZO,
                    InterpretacionDelAsunto.enDias(hoy, asunto.fechaVencimiento())));
        } else if (asunto.diasSinAccion() != null && asunto.diasSinAccion() > 0) {
            hechos.add(new Hecho(EstadoDelHecho.DATO,
                    "Sin movimiento desde hace " + asunto.diasSinAccion() + " dias"));
        }

        // 4 · que queda parado. Solo cuando de verdad frena algo: un FRENO
        //     inventado convierte la marca roja en ruido.
        String freno = loQueFrena(tipo, asunto);
        if (freno != null) {
            hechos.add(new Hecho(EstadoDelHecho.FRENO, freno));
        }

        return ComoEsta.de(avance(tipo, asunto), hechos);
    }

    private static String loQueFalta(String tipo, AsuntoADescribir asunto) {
        return switch (tipo) {
            case Tarea.RECONTACTO -> "Falta volver a contactar al propietario";
            case Tarea.VISITA -> "Falta cerrar la visita con su resultado";
            case Tarea.SUBIR_DOCUMENTOS -> "Faltan los documentos observados";
            case Tarea.REPORTE_PROPIETARIO -> "Falta enviar el reporte al propietario";
            case Tarea.REVISION_INMUEBLE -> "Falta revisar el estado del local";
            case Tarea.SEGUIMIENTO -> asunto.dependeDeMi()
                    ? "Falta firmar el contrato"
                    : "Falta que el broker registre el cobro";
            default -> "Falta atenderlo";
        };
    }

    /**
     * La consecuencia: qué queda parado por lo que falta.
     *
     * <p>Devuelve {@code null} cuando no frena nada, y eso es lo importante. Un
     * asunto sin consecuencia real no lleva línea roja: la marca ya carga la
     * alarma, y gastarla donde no hay nada parado hace que nadie la crea donde sí.
     */
    private static String loQueFrena(String tipo, AsuntoADescribir asunto) {
        return switch (tipo) {
            case Tarea.SUBIR_DOCUMENTOS -> "Hasta que lleguen, el broker no puede evaluar";
            case Tarea.REVISION_INMUEBLE -> "El local no se puede volver a ofrecer";
            case Tarea.SEGUIMIENTO -> asunto.dependeDeMi()
                    ? "La comision no se genera hasta la firma" : null;
            default -> null;
        };
    }

    /**
     * <b>Solo donde hay algo contable de verdad.</b>
     *
     * <p>Hoy ninguno de los seis disparadores trae un contador real —los
     * documentos verificados y las observaciones resueltas viven en la solicitud
     * y no en la tarea—, así que esto devuelve {@code null} y la barra no se
     * pinta. <b>Es el comportamiento correcto</b>: una barra de dos segmentos
     * inventada para rellenar promete una precisión que no existe.
     */
    private static Avance avance(String tipo, AsuntoADescribir asunto) {
        return null;
    }

    // ==================================================================
    // El expediente
    // ==================================================================

    /**
     * Cuatro renglones fijos, los mismos para todo asunto, porque son las cuatro
     * cosas con las que se sostiene una conversación comercial.
     *
     * <p>Cuando no hay expediente —una prospección no cuelga de ningún local— se
     * devuelve <b>vacío</b> y no cuatro renglones con guiones: un expediente en
     * blanco dice «no hay historial», y cuatro guiones dicen «lo hay y no lo
     * cargué».
     */
    // ==================================================================
    // El expediente de una PROSPECCIÓN (D-E2-1 §10.3, corregido 2026-08-20)
    // ==================================================================
    //
    // TODO ASUNTO LLEVA CUATRO RENGLONES. Lo que cambia es QUÉ cuatro: se eligen
    // según la etapa y el tipo, y nunca se inventa un inmueble, un encargo ni un
    // dato que todavía no existe.
    //
    // Una prospección es ANTERIOR a la captación: no hay encargo firmado, ni
    // renta publicada, ni visitas. Pedirle Encargo · Renta · Actividad ·
    // Propietario daría cuatro huecos, y un expediente de huecos es peor que
    // ninguno. Pero tiene historia propia, y esa es la que se cuenta:
    //
    //     Prospección  desde cuándo existe y en qué punto está
    //     Contacto     cuándo se le habló, o que todavía no
    //     Avance       hasta dónde llegó, y qué se tiene previsto
    //     Propietario  con quién se está tratando
    //
    // Y NO decide el próximo paso comercial. Eso es `lectura`, `ComoEsta` y la
    // recomendación; el expediente es evidencia condensada y nada más. Es la
    // separación que E2.4 dejó y que aquí no se rompe.

    /** Fecha corta para los renglones de la prospección. */
    private static String dia(LocalDate fecha) {
        return fecha == null ? null : fecha.format(DIA_Y_MES);
    }

    /**
     * Los cuatro de una prospección.
     *
     * <p>Los tres primeros salen de fechas que pueden faltar, y cuando faltan se
     * dice: «Sin contacto registrado» informa, y un renglón en blanco no.
     */
    private static List<Renglon> expedienteDeProspeccion(ExpedienteDeLaProspeccion datos,
                                                         LocalDate hoy) {
        if (datos == null) {
            return List.of();
        }
        List<Renglon> renglones = new ArrayList<>(4);
        renglones.add(prospeccion(datos, hoy));
        renglones.add(contacto(datos, hoy));
        renglones.add(avance(datos, hoy));
        renglones.add(propietarioDeLaProspeccion(datos));
        return List.copyOf(renglones);
    }

    /** Desde cuándo existe y en qué punto está. */
    private static Renglon prospeccion(ExpedienteDeLaProspeccion datos, LocalDate hoy) {
        LocalDate alta = datos.getFechaRegistro();
        var etapa = EstadosDominio.EstadoProspeccion.desde(datos.getEstado());
        String valor = InterpretacionDelAsunto.frase(
                alta == null ? null : "Abierta el " + dia(alta),
                etapa == null ? null : etapa.descripcion());
        return Renglon.historial("Prospección", valor.isBlank() ? "Sin fecha de alta" : valor);
    }

    /**
     * Cuándo se le habló por última vez.
     *
     * <p>Sin contacto **no es un fallo**: era el estado de 42 de las 63
     * prospecciones de la base el 2026-08-19. Se dice, y lleva señal porque una
     * prospección sin contactar es precisamente lo que hay que mirar.
     */
    private static Renglon contacto(ExpedienteDeLaProspeccion datos, LocalDate hoy) {
        LocalDate contacto = datos.getFechaContacto();
        if (contacto == null) {
            return Renglon.conSenal("Contacto", "Sin contacto registrado", Renglon.OJO);
        }
        long dias = ChronoUnit.DAYS.between(contacto, hoy);
        String valor = "Último el " + dia(contacto)
                + (dias <= 0 ? " · hoy" : " · hace " + dias + (dias == 1 ? " día" : " días"));
        // El umbral vive en la politica; aqui solo se pregunta.
        String estado = contacto.isBefore(PoliticaComercial.limiteDeRecontacto(hoy))
                ? Renglon.OJO : Renglon.BIEN;
        return Renglon.conSenal("Contacto", valor, estado);
    }

    /**
     * El último hito comercial alcanzado, y el próximo vencimiento conocido.
     *
     * <p><b>«Avance» y no «Propuesta»</b>: una prospección recién contactada o con
     * reunión registrada tendría un renglón permanentemente vacío bajo el nombre
     * más específico, aunque sí exista actividad. Así se expresa el último hecho
     * real sin inventar una fase que no ocurrió.
     */
    private static Renglon avance(ExpedienteDeLaProspeccion datos, LocalDate hoy) {
        String hito;
        if (datos.getFechaPropuesta() != null) {
            hito = "Propuesta entregada el " + dia(datos.getFechaPropuesta());
        } else if (datos.getFechaReunion() != null) {
            hito = "Reunión registrada el " + dia(datos.getFechaReunion());
        } else {
            hito = "Sin reunión ni propuesta registrada";
        }

        LocalDate proximo = datos.getFechaRecontacto();
        String valor = InterpretacionDelAsunto.frase(hito,
                proximo == null ? null : "recontacto previsto para el " + dia(proximo));

        // Rojo solo cuando lo previsto ya venció: es un hecho, no una opinión
        // sobre si la prospección va bien.
        String estado = proximo != null && proximo.isBefore(hoy) ? Renglon.MAL
                : datos.getFechaPropuesta() != null ? Renglon.BIEN : null;
        return estado == null ? Renglon.historial("Avance", valor)
                : Renglon.conSenal("Avance", valor, estado);
    }

    /** Con quién se está tratando, y sobre qué inmueble. */
    private static Renglon propietarioDeLaProspeccion(ExpedienteDeLaProspeccion datos) {
        String donde = InterpretacionDelAsunto.frase(datos.getDireccion(), datos.getDistrito());
        String valor = InterpretacionDelAsunto.frase(datos.getPropietario(),
                donde.isBlank() ? null : donde);
        return Renglon.historial("Propietario",
                valor.isBlank() ? "Sin propietario identificado" : valor);
    }

    private static List<Renglon> expediente(ExpedienteDeLaPropiedad datos,
                                            List<BigDecimal> serie, Contraste contraste,
                                            LocalDate hoy) {
        if (datos == null) {
            return List.of();
        }
        List<Renglon> renglones = new ArrayList<>(InterpretacionDelAsunto.RENGLONES_DEL_EXPEDIENTE);
        renglones.add(encargo(datos, hoy));
        renglones.add(renta(datos, serie, contraste, hoy));
        renglones.add(actividad(datos));
        renglones.add(propietario(datos));
        return List.copyOf(renglones);
    }

    private static Renglon encargo(ExpedienteDeLaPropiedad datos, LocalDate hoy) {
        LocalDate inicio = datos.getInicioVigencia() != null
                ? datos.getInicioVigencia() : datos.getFechaCaptacion();
        LocalDate fin = datos.getFinVigencia();
        String valor = InterpretacionDelAsunto.frase(
                inicio == null ? null : "Alta el " + inicio.format(DIA_Y_MES),
                fin == null ? null : InterpretacionDelAsunto.enDias(hoy, fin));

        if (inicio == null || fin == null) {
            return Renglon.historial("Encargo", valor.isBlank() ? "Sin vigencia pactada" : valor);
        }
        int total = (int) ChronoUnit.DAYS.between(inicio, fin);
        int consumido = (int) Math.max(0, Math.min(total, ChronoUnit.DAYS.between(inicio, hoy)));
        // La barra lleva sus dos numeros y no el porcentaje: 168/180 se puede
        // leer, y el 93 % solo se puede pintar.
        String estado = total <= 0 ? null
                : consumido >= total ? Renglon.MAL
                : consumido * 100 / total >= 80 ? Renglon.OJO : null;
        return new Renglon("Encargo", valor, estado, new Ventana(consumido, Math.max(total, 0)),
                null, null);
    }

    private static Renglon renta(ExpedienteDeLaPropiedad datos, List<BigDecimal> serie,
                                 Contraste contraste, LocalDate hoy) {
        BigDecimal monto = datos.getRenta();
        LocalDate desde = datos.getRentaDesde();
        String importe = monto == null ? null
                : (datos.getMoneda() == null ? "" : datos.getMoneda() + " ")
                        + monto.stripTrailingZeros().toPlainString();
        Long parada = desde == null ? null : ChronoUnit.DAYS.between(desde, hoy);

        String valor = InterpretacionDelAsunto.frase(importe,
                parada == null ? null
                        : parada == 0 ? "fijada hoy" : "sin cambios desde hace " + parada + " dias");
        // Una renta parada mas de lo que la casa admite es una renta que nadie
        // esta negociando. El umbral vive en la politica: estaba aqui como un
        // 45 suelto, con un comentario que lo llamaba "plazo de recontacto"
        // -que son 7 dias-, y la maqueta llevaba su propia copia con 60.
        String estado = parada != null && PoliticaComercial.rentaParada(parada) ? Renglon.OJO : null;
        return new Renglon("Renta", valor.isBlank() ? "Sin renta publicada" : valor,
                estado, null, serie, contraste);
    }

    private static Renglon actividad(ExpedienteDeLaPropiedad datos) {
        long realizadas = datos.getVisitasRealizadas() == null ? 0 : datos.getVisitasRealizadas();
        long totales = datos.getVisitasTotales() == null ? 0 : datos.getVisitasTotales();
        if (totales == 0) {
            return Renglon.conSenal("Actividad", "Ninguna visita todavia", Renglon.OJO);
        }
        String valor = realizadas + (realizadas == 1 ? " visita realizada" : " visitas realizadas")
                + (totales > realizadas ? " de " + totales + " agendadas" : "");
        return Renglon.conSenal("Actividad", valor,
                realizadas > 0 ? Renglon.BIEN : Renglon.OJO);
    }

    private static Renglon propietario(ExpedienteDeLaPropiedad datos) {
        String nombre = datos.getPropietario();
        String donde = InterpretacionDelAsunto.frase(datos.getDireccion(), datos.getDistrito());
        return Renglon.historial("Propietario",
                InterpretacionDelAsunto.frase(nombre, donde.isBlank() ? null : donde));
    }

    // ==================================================================
    // La lectura
    // ==================================================================

    /**
     * <b>Una frase que sintetiza los cuatro renglones sin recitarlos.</b>
     *
     * <p>Lo que hace que un panel se sienta inteligente no es adornar el dato: es
     * <b>relacionarlo</b>. Que la exclusiva se agote MIENTRAS la renta lleva dos
     * meses parada es una conclusión; repetir «Alta el 12 de mayo» es un eco.
     *
     * <p>Devuelve {@code null} cuando no hay nada que concluir. Una lectura de
     * relleno —«El expediente presenta actividad normal»— es peor que ninguna:
     * enseña a no leerla.
     */
    private static String lectura(ExpedienteDeLaPropiedad datos, LocalDate hoy) {
        if (datos == null) {
            return null;
        }
        List<String> partes = new ArrayList<>();

        LocalDate inicio = datos.getInicioVigencia() != null
                ? datos.getInicioVigencia() : datos.getFechaCaptacion();
        LocalDate fin = datos.getFinVigencia();
        if (inicio != null && fin != null) {
            long total = ChronoUnit.DAYS.between(inicio, fin);
            long restan = ChronoUnit.DAYS.between(hoy, fin);
            if (total > 0 && restan <= total * 0.2) {
                partes.add(restan < 0 ? "la exclusiva ya vencio" : "la exclusiva casi agotada");
            }
        }

        long realizadas = datos.getVisitasRealizadas() == null ? 0 : datos.getVisitasRealizadas();
        if (realizadas == 0) {
            partes.add("nadie lo ha visto todavia");
        } else if (realizadas >= 3) {
            partes.add("ya se enseno " + realizadas + " veces sin cerrar");
        }

        LocalDate rentaDesde = datos.getRentaDesde();
        if (rentaDesde != null && ChronoUnit.DAYS.between(rentaDesde, hoy) > 45) {
            partes.add("y la renta sin moverse");
        }

        // La regla vive en `InterpretacionDelAsunto`, no aqui: el Radar del
        // broker la necesita igual, y una frontera escrita dos veces deja de ser
        // una frontera.
        return InterpretacionDelAsunto.sintetizar(partes);
    }

    private static Long numero(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }
}
