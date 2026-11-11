package com.controllocal.asistencia.conversacion;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.ClienteBrox.Capacidad;
import com.controllocal.asistencia.brox.ClienteBrox.CoincidenciaComercial;
import com.controllocal.asistencia.brox.ClienteBrox.CoincidenciasComerciales;
import com.controllocal.asistencia.brox.ClienteBrox.ConsultaCoincidencias;
import com.controllocal.asistencia.brox.ClienteBrox.EstadoCaptura;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.asistencia.brox.Vocabulario;
import com.controllocal.asistencia.herramientas.Confirmacion;
import com.controllocal.asistencia.herramientas.Herramienta;
import com.controllocal.asistencia.herramientas.Herramientas;
import com.controllocal.asistencia.interpretacion.Interprete;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * El adaptador. <b>Ninguna regla inmobiliaria vive aqui.</b>
 *
 * <h2>Como leer esta clase</h2>
 * Cada accion es el mismo parrafo: mirar que herramienta del catalogo se va a
 * invocar, comprobar que BROX se la ofrece a esta sesion, resolver lo que la
 * frase nombra con una lectura que ya existe, y llamar a la operacion. Si
 * alguna vez una de ellas empieza a decidir algo —que un terreno no lleva
 * dormitorios, cuando un encargo esta vivo, si un precio es razonable—, esa
 * decision esta en el sitio equivocado y hay que devolverla a BROX.
 *
 * <h2>La confirmacion se compone, y siempre hacia el lado estricto</h2>
 * <pre>
 *   catalogo de KAIROS   suelo: lo sensible se confirma SIEMPRE   (D-K-1 §3.3)
 *          +
 *   capacidad de BROX    puede exigir MAS, nunca menos            (D-K-1 §4)
 * </pre>
 * Una capacidad declarada {@code AUTO} <b>no</b> apaga la confirmacion de
 * {@code preparar_actuacion_captura}: si pudiera, bastaria un error de configuracion del
 * Core para que una conversacion creara propiedades, titularidades y encargos
 * sin que nadie dijera que si. Al reves si: BROX puede pedir confirmacion para
 * algo que el catalogo considera reversible, y KAIROS la pide.
 *
 * <h2>Contrato C0/K0</h2>
 * Un catalogo valido vacio no habilita herramientas. Su ausencia, incompatibilidad
 * o fallo detiene la actuacion dependiente; no se sustituye con permisos locales.
 * Cada puerta Core revalida la habilitacion y la autoridad al ejecutarse.
 */
@Service
public class KairosImpl implements Kairos {

    private final Interprete interprete;
    private final ClienteBrox brox;
    private final String nombreDelAgente;
    private final String modelo;
    private final String modeloVersion;

    public KairosImpl(Interprete interprete, ClienteBrox brox,
                      @Value("${kairos.agente:KAIROS}") String nombreDelAgente,
                      @Value("${kairos.modelo:determinista}") String modelo,
                      @Value("${kairos.modelo-version:0.1.0}") String modeloVersion) {
        this.interprete = interprete;
        this.brox = brox;
        this.nombreDelAgente = nombreDelAgente;
        this.modelo = modelo;
        this.modeloVersion = modeloVersion;
    }

    // ==================================================================

    @Override
    public Respuesta turno(Turno turno, SesionBrox sesion) {
        try {
            return atender(turno, sesion);
        } catch (com.controllocal.asistencia.brox.ErrorBrox error) {
            Desenlace desenlace = switch (error.tipo()) {
                case PERMISO_INSUFICIENTE -> Desenlace.SIN_PERMISO;
                case CAPACIDAD_DESHABILITADA -> Desenlace.CAPACIDAD_DESHABILITADA;
                case FALLO_TECNICO -> Desenlace.FALLO_TECNICO;
                case SOLICITUD_RECHAZADA -> Desenlace.RECHAZADO;
            };
            if (error.operacion() != null) desenlace = Desenlace.RESULTADO_INCIERTO;
            if (RESERVADA_A_UNA_PERSONA.equals(error.codigo())) desenlace = Desenlace.SOLO_HUMANO;
            return new Respuesta(turno.conversacionId(), turno.turnoId(), null, desenlace,
                    error.codigo(), Map.of(), List.of(), List.of(), List.of(), false,
                    Resultado.NADA.conAutomatico(error.operacion()));

        }
    }

    private Respuesta atender(Turno turno, SesionBrox sesion) {
        // La traza se construye ANTES de mirar la frase, y falla si le falta la
        // conversacion o el turno. Es deliberado: no se empieza a trabajar sobre
        // algo que despues no se va a poder atribuir.
        Traza traza = new Traza(turno.canal(), nombreDelAgente, modelo, modeloVersion,
                turno.conversacionId(), turno.turnoId(), turno.mensajeId(), turno.texto(), null);

        if (turno.referenciaActuacion() != null && !turno.referenciaActuacion().isBlank()) {
            var operacion = brox.consultarActuacion(sesion, turno.referenciaActuacion(),
                    traza.invocando(Herramientas.CONSULTAR_ACTUACION.nombre()));
            if(operacion==null || operacion.estado()==null)
                throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
            Desenlace estado = switch (operacion.estado()) {
                case "EXITO" -> Desenlace.RESPONDIDO;
                case "RECHAZADO" -> Desenlace.RECHAZADO;
                case "PREPARADA" -> Desenlace.PROPUESTA;
                default -> Desenlace.RESULTADO_INCIERTO;
            };
            return new Respuesta(turno.conversacionId(), turno.turnoId(), null, estado,
                    operacion.motivo(), Map.of(), List.of(), List.of(), List.of(),
                    "PREPARADA".equals(operacion.estado()), Resultado.NADA.conActuacion(operacion)
                            .conComercial(operacion.comercial())
                            .conAutomatico(operacion.comercial() == null ? null : operacion.comercial().automatico()));
        }

        Interprete.Lectura lectura = interprete.leer(turno.texto(), sesion, traza);
        // Una respuesta de datos puede no repetir el verbo «registrar». Sólo
        // se intenta la lectura contextual cuando ya existe un borrador y la
        // lectura normal no reconoció una acción; la propia implementacion del
        // interprete decide si los datos son inequívocos y conserva SIN_ACCION
        // para una frase ambigua.
        if (turno.idBorrador() != null && lectura != null && !lectura.hayAccion()) {
            Interprete.Lectura contextual = interprete.leerEnCaptura(
                    turno.texto(), turno.idBorrador(), sesion, traza);
            if (contextual != null) {
                lectura = contextual;
            }
        }
        if (lectura == null) {
            lectura = Interprete.Lectura.nada(Interprete.Lectura.SIN_ACCION);
        }
        if (!lectura.hayAccion()) {
            return new Respuesta(turno.conversacionId(), turno.turnoId(), null,
                    Desenlace.NO_ENTENDIDO,
                    Interprete.Lectura.SIN_TEXTO.equals(lectura.motivo())
                            ? SIN_TEXTO : SIN_ACCION_RECONOCIDA,
                    Map.of(), List.of(), List.of(), List.of(), false, Resultado.NADA);
        }

        Accion accion = lectura.accion();
        Herramienta puerta = puertaDe(accion, turno);

        // Lo que puede lo dice BROX, no este codigo.
        Permisos permisos = new Permisos(brox.capacidades(sesion,
                traza.invocando(Herramientas.CONSULTAR_CAPACIDADES.nombre())));
        Optional<Respuesta> rechazo = permisos.rechazo(turno, accion, puerta, lectura);
        if (rechazo.isPresent()) {
            return rechazo.get();
        }
        Confirmacion confirmacion = permisos.confirmacionDe(puerta);
        if (lectura.datos().containsKey("datosComerciales") || esComercial(accion))
            return operarComercial(turno,lectura,sesion,traza,permisos.de(puerta).orElseThrow());

        return switch (accion) {
            case CONSULTAR_PROPIEDAD -> consultarPropiedad(turno, lectura, sesion, traza);
            case CONSULTAR_CLIENTE -> consultarCliente(turno, lectura, sesion, traza);
            case CONTINUAR_BORRADOR -> continuarBorrador(turno, lectura, confirmacion, sesion, traza);
            case PREPARAR_CAPTURA -> prepararCaptura(turno, lectura, permisos, sesion, traza);
            case REGISTRAR_PROPIEDAD ->
                    registrarPropiedad(turno, lectura, puerta, permisos, traza, sesion);
            case REGISTRAR_PROPIETARIO ->
                    registrarPropietario(turno, lectura, confirmacion, traza, sesion);

            case BUSCAR_COINCIDENCIAS -> buscarCoincidencias(turno, lectura, sesion, traza);
            case CONTINUAR_COINCIDENCIAS -> continuarCoincidencias(turno, lectura, sesion, traza);
            case EXPLICAR_MATCH -> explicarMatch(turno, lectura, sesion, traza);
            case QUE_RESOLVER_HOY -> queResolverHoy(turno, lectura, sesion, traza);
            default -> throw new IllegalStateException("Acción no despachada: "+accion);
        };
    }

    /**
     * La herramienta que este turno va a invocar primero.
     *
     * <p>Es la de la accion salvo en la captura: <b>abrir</b> y <b>responder</b>
     * son la misma ruta y lo unico que las distingue es que la segunda ya tiene
     * borrador. Se separan porque son dos herramientas del catalogo, y porque el
     * rastro tiene que poder decir cual de las dos fue.
     */
    private static boolean esComercial(Accion accion) {
        try { ClienteBrox.OperacionComercial.valueOf(accion.name());return true; }
        catch(IllegalArgumentException otra){return false;}
    }
    private Respuesta operarComercial(Turno turno,Interprete.Lectura lectura,SesionBrox sesion,
            Traza traza,Capacidad capacidad) {
        var operacion = ClienteBrox.OperacionComercial.valueOf(lectura.accion().name());
        if (capacidad.laConfirmaUnaPersona() && !operacion.preparaPropuesta())
            throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        Map<String,Object> datos;
        try {
            String argumentos = lectura.datos().get("datosComerciales");
            if (argumentos == null) throw new IllegalArgumentException("Faltan argumentos declarados");
            datos=com.controllocal.asistencia.interpretacion.ArgumentosComerciales.leer(
                    argumentos,operacion);
        }
        catch(IllegalArgumentException invalido) {
            return respuesta(turno,lectura.accion(),Desenlace.PREGUNTA,FALTAN_DATOS,lectura,List.of("argumentos estructurados"),List.of(),false,Resultado.NADA);
        }
        var faltan=new java.util.ArrayList<String>();
        faltantesEsquema(capacidad.esquema(),datos,"",faltan);
        if(capacidad.esquema()==null||!capacidad.esquema().containsKey("required"))throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        if(!faltan.isEmpty())return respuesta(turno,lectura.accion(),Desenlace.PREGUNTA,FALTAN_DATOS,lectura,List.copyOf(faltan),opcionesEsquema(capacidad.esquema(),faltan.getFirst()),capacidad.laConfirmaUnaPersona(),Resultado.NADA);
        var r=brox.operarComercial(sesion,operacion,datos,
                traza.invocando(lectura.accion().herramienta().nombre()));
        if(r==null)throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        if(r.propuesta()==null && r.datos()==null)
            throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        if(operacion.preparaPropuesta() && r.propuesta()==null)
            throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        var salida=Resultado.NADA.conComercial(r).conAutomatico(r.automatico());
        if(r.propuesta()!=null) return respuesta(turno,lectura.accion(),Desenlace.PROPUESTA,CONFIRMA_UNA_PERSONA,lectura,List.of(),List.of(),true,salida);
        return respuesta(turno,lectura.accion(),"LECTURA".equals(capacidad.efecto())?Desenlace.RESPONDIDO:Desenlace.EJECUTADO,
                null,lectura,List.of(),List.of(),false,salida);
    }
    private static List<String> opcionesEsquema(Map<String,Object> esquema,String ruta) {
        Object actual=esquema;
        for(String campo:ruta.split("\\.")) {
            if(!(actual instanceof Map<?,?> mapa)||!(mapa.get("properties") instanceof Map<?,?> propiedades))return List.of();
            int indice=campo.indexOf('[');
            actual=propiedades.get(indice<0?campo:campo.substring(0,indice));
            if(indice>=0) {
                if(!(actual instanceof Map<?,?> lista))return List.of();
                actual=lista.get("items");
            }
        }
        if(actual instanceof Map<?,?> mapa && mapa.get("oneOf") instanceof List<?> opciones) {
            var rotulos=opciones.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .filter(o->o.containsKey("const") && o.get("title") instanceof String)
                    .map(o->o.get("title").toString()).toList();
            if(!rotulos.isEmpty())return rotulos;
        }
        if(actual instanceof Map<?,?> mapa&&mapa.get("enum") instanceof List<?> opciones)
            return opciones.stream().map(Object::toString).toList();
        return List.of();
    }
    @SuppressWarnings("unchecked")
    private static void faltantesEsquema(Map<String,Object> esquema,Map<String,Object> datos,String prefijo,List<String> faltan) {
        if(esquema==null)return;
        if(esquema.get("required") instanceof List<?> requeridas)for(var clave:requeridas) {
            Object valor=datos.get(clave.toString());
            if(valor==null || valor instanceof String t&&t.isBlank())faltan.add(prefijo+clave);
        }
        if(esquema.get("properties") instanceof Map<?,?> propiedades)for(var e:propiedades.entrySet()) {
            Object valor=datos.get(e.getKey());
            if(e.getValue() instanceof Map<?,?> sub && valor!=null && sub.get("enum") instanceof List<?> opciones && !opciones.contains(valor))faltan.add(prefijo+e.getKey());
            if(e.getValue() instanceof Map<?,?> sub && valor instanceof Map<?,?> mapa)
                faltantesEsquema((Map<String,Object>)sub,(Map<String,Object>)mapa,prefijo+e.getKey()+".",faltan);
            if(e.getValue() instanceof Map<?,?> sub && valor instanceof List<?> lista
                    && sub.get("items") instanceof Map<?,?> item) {
                for(int i=0;i<lista.size();i++)if(lista.get(i) instanceof Map<?,?> fila)
                    faltantesEsquema((Map<String,Object>)item,(Map<String,Object>)fila,
                            prefijo+e.getKey()+"["+i+"].",faltan);
            }
        }
    }
    private static Herramienta puertaDe(Accion accion, Turno turno) {
        if (accion == Accion.REGISTRAR_PROPIEDAD && turno.idBorrador() != null) {
            return Herramientas.RESPONDER_CAPTURA;
        }
        return accion.herramienta();
    }

    // ==================================================================
    // Lecturas
    // ==================================================================

    /**
     * La ficha, buscada por el texto que la frase diga.
     *
     * <p>La busqueda es la <b>misma</b> que usa el listado de cartera de la
     * pantalla: cruza codigo, direccion, distrito, rubro y nombre del
     * propietario, y respeta el alcance de la sesion. Con una consulta propia
     * habria dos criterios de "que es una coincidencia", y una lista que no
     * cuadra con la que el mismo usuario ve en su pantalla.
     */
    private Respuesta consultarPropiedad(Turno turno, Interprete.Lectura lectura,
                                         SesionBrox sesion, Traza traza) {
        String texto = lectura.datos().get("texto");
        if (texto == null || texto.isBlank()) {
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("texto"), List.of(), false, Resultado.NADA);
        }
        List<ClienteBrox.Coincidencia> encontradas = brox.buscarPropiedades(sesion, texto,
                traza.invocando(Herramientas.BUSCAR_PROPIEDADES.nombre()));

        if (encontradas.isEmpty()) {
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO,
                    SIN_COINCIDENCIAS, lectura, List.of(), List.of(), false,
                    Resultado.NADA.conCoincidencias(List.of()));
        }
        if (encontradas.size() > 1) {
            // Varias: se devuelven para que la persona elija. Elegir por ella
            // —"la primera", "la mas reciente"— es inventar un criterio que
            // nadie declaro.
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO,
                    VARIAS_COINCIDENCIAS, lectura, List.of(), List.of(), false,
                    Resultado.NADA.conCoincidencias(encontradas));
        }
        Map<String, Object> ficha = brox.propiedad(sesion, encontradas.get(0).id(),
                traza.invocando(Herramientas.ESTADO_DE_PROPIEDAD.nombre()));
        return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO, null, lectura,
                List.of(), List.of(), false,
                Resultado.NADA.conCoincidencias(encontradas).conPropiedad(ficha));
    }

    private Respuesta consultarCliente(Turno turno, Interprete.Lectura lectura, SesionBrox sesion,
                                       Traza traza) {
        List<ClienteBrox.Persona> encontrados = brox.buscarClientes(sesion,
                lectura.datos().get("texto"),
                traza.invocando(Herramientas.BUSCAR_PERSONA.nombre()));
        return respuesta(turno, Accion.CONSULTAR_CLIENTE, Desenlace.RESPONDIDO,
                encontrados.isEmpty() ? SIN_COINCIDENCIAS : null, lectura, List.of(), List.of(),
                false, Resultado.NADA.conPersonas(encontrados));
    }

    // ==================================================================
    // Matching: que encaja con que, y por que
    // ==================================================================

    /** La interpretación aporta el origen y la versión observados; nunca elige una demanda por cliente. */
    private Respuesta buscarCoincidencias(Turno turno,Interprete.Lectura lectura,SesionBrox sesion,Traza traza) {
        var d=lectura.datos();var faltantes=new java.util.ArrayList<String>();
        for(String clave:List.of("origen","idOrigen","versionOrigen","tipoVersion","modalidad","capas","categoria"))
            if(d.get(clave)==null||d.get(clave).isBlank())faltantes.add(clave);
        Long id=longDe(d.get("idOrigen")),version=longDe(d.get("versionOrigen"));
        if(id==null&&!faltantes.contains("idOrigen"))faltantes.add("idOrigen");
        if(version==null&&!faltantes.contains("versionOrigen"))faltantes.add("versionOrigen");
        if(!faltantes.isEmpty())return respuesta(turno,Accion.BUSCAR_COINCIDENCIAS,Desenlace.PREGUNTA,FALTAN_DATOS,
                lectura,faltantes,List.of(),false,Resultado.NADA);
        Integer tamano=null;
        if(d.get("tamano")!=null&&!d.get("tamano").isBlank())try{tamano=Integer.valueOf(d.get("tamano"));}
        catch(NumberFormatException e){return respuesta(turno,Accion.BUSCAR_COINCIDENCIAS,Desenlace.PREGUNTA,FALTAN_DATOS,lectura,List.of("tamano"),List.of(),false,Resultado.NADA);}
        ClienteBrox.ExploracionPrecio exploracion=null;
        if(List.of("modoExploracion","limiteExploracion","monedaExploracion").stream().anyMatch(d::containsKey)) {
            ClienteBrox.ModoExploracion modo=null;
            try { modo=ClienteBrox.ModoExploracion.valueOf(d.get("modoExploracion")==null?"":d.get("modoExploracion").toUpperCase(Locale.ROOT)); }
            catch(IllegalArgumentException invalido) { faltantes.add("modoExploracion"); }
            java.math.BigDecimal limite=null;
            if(d.get("limiteExploracion")!=null)try{limite=new java.math.BigDecimal(d.get("limiteExploracion"));}
            catch(NumberFormatException invalido){faltantes.add("limiteExploracion");}
            if(modo==ClienteBrox.ModoExploracion.EXPLORAR) {
                if(limite==null&&!faltantes.contains("limiteExploracion"))faltantes.add("limiteExploracion");
                if(d.get("monedaExploracion")==null||d.get("monedaExploracion").isBlank())faltantes.add("monedaExploracion");
            }
            if(!faltantes.isEmpty())return respuesta(turno,Accion.BUSCAR_COINCIDENCIAS,Desenlace.PREGUNTA,FALTAN_DATOS,
                    lectura,faltantes,List.of(),false,Resultado.NADA);
            exploracion=new ClienteBrox.ExploracionPrecio(modo,limite,d.get("monedaExploracion"));
        }
        var capas=java.util.Arrays.stream(d.get("capas").split(",")).map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet());
        var consulta=new ConsultaCoincidencias(d.get("origen"),id,version,d.get("tipoVersion"),d.get("modalidad"),capas,d.get("categoria"),tamano,exploracion);
        var resultado=exigirResultado(brox.coincidencias(sesion,consulta,traza.invocando(Herramientas.BUSCAR_COINCIDENCIAS.nombre())));
        return respuesta(turno,Accion.BUSCAR_COINCIDENCIAS,Desenlace.RESPONDIDO,
                "DISPONIBLE".equals(resultado.estado())&&Long.valueOf(0).equals(resultado.total())?SIN_COINCIDENCIAS:null,
                lectura,List.of(),List.of(),false,Resultado.NADA.conMatching(resultado));
    }
    private Respuesta continuarCoincidencias(Turno turno,Interprete.Lectura lectura,SesionBrox sesion,Traza traza) {
        var cursor=lectura.datos().get("cursor");
        if(cursor==null||cursor.isBlank())return respuesta(turno,Accion.CONTINUAR_COINCIDENCIAS,Desenlace.PREGUNTA,FALTAN_DATOS,lectura,List.of("cursor"),List.of(),false,Resultado.NADA);
        var resultado=exigirResultado(brox.continuarCoincidencias(sesion,cursor,traza.invocando(Herramientas.CONTINUAR_COINCIDENCIAS.nombre())));
        return respuesta(turno,Accion.CONTINUAR_COINCIDENCIAS,Desenlace.RESPONDIDO,null,lectura,List.of(),List.of(),false,Resultado.NADA.conMatching(resultado));
    }
    /** Explicar reautoriza el candidato del mismo recorrido; no reconstruye porcentajes ni repite búsqueda. */
    private Respuesta explicarMatch(Turno turno,Interprete.Lectura lectura,SesionBrox sesion,Traza traza) {
        var d=lectura.datos();var faltantes=List.of("cursor","referenciaDemanda","referencia").stream().filter(k->d.get(k)==null||d.get(k).isBlank()).toList();
        if(!faltantes.isEmpty())return respuesta(turno,Accion.EXPLICAR_MATCH,Desenlace.PREGUNTA,FALTAN_DATOS,lectura,faltantes,List.of(),false,Resultado.NADA);
        var resultado=brox.explicarCoincidencia(sesion,d.get("cursor"),d.get("referenciaDemanda"),d.get("referencia"),traza.invocando(Herramientas.EXPLICAR_MATCH.nombre()));
        if(resultado==null)throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        return respuesta(turno,Accion.EXPLICAR_MATCH,Desenlace.RESPONDIDO,null,lectura,List.of(),List.of(),false,Resultado.NADA.conExplicacion(resultado));
    }
    private static Long longDe(String s){try{return s==null?null:Long.valueOf(s);}catch(NumberFormatException e){return null;}}
    private static CoincidenciasComerciales exigirResultado(CoincidenciasComerciales r){
        if(r==null||r.estado()==null||r.grupos()==null||r.cobertura()==null||r.capasNoDisponibles()==null)
            throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        return r;
    }

    // ==================================================================
    // El foco del Inicio
    // ==================================================================

    /**
     * Que hay que resolver hoy, con el alcance de quien pregunta.
     *
     * <p>No se filtra ni se reordena nada: la politica de despacho —que va
     * primero, que puede esperar— es del Core, y aplicarle aqui un criterio
     * propio produciria un foco que no coincide con el que esa misma persona ve
     * al abrir la pantalla.
     *
     * <p>Vacio es una respuesta, no un fallo, y se dice con su codigo: «hoy no
     * hay nada» y «no pude mirarlo» no son lo mismo.
     */
    private Respuesta queResolverHoy(Turno turno, Interprete.Lectura lectura, SesionBrox sesion,
                                     Traza traza) {
        ClienteBrox.Foco foco = brox.foco(sesion,
                traza.invocando(Herramientas.QUE_RESOLVER_HOY.nombre()));
        return respuesta(turno, Accion.QUE_RESOLVER_HOY, Desenlace.RESPONDIDO,
                foco == null || foco.vacio() ? NADA_QUE_RESOLVER : null, lectura, List.of(),
                List.of(), false, Resultado.NADA.conFoco(foco));
    }

    // ==================================================================
    // Captura: el motor es de BROX
    // ==================================================================

    /**
     * Retomar. Con codigo, ese; sin codigo y con uno solo en curso, ese; con
     * varios, se devuelven todos <b>sin elegir</b>.
     */
    private Respuesta continuarBorrador(Turno turno, Interprete.Lectura lectura,
                                        Confirmacion confirmacion, SesionBrox sesion,
                                        Traza traza) {
        Traza deLaLectura = traza.invocando(Herramientas.CAPTURAS_EN_CURSO.nombre());
        List<EstadoCaptura> enCurso = brox.capturasEnCurso(sesion, deLaLectura);
        String codigo = lectura.datos().get("codigo");

        List<EstadoCaptura> candidatos = codigo == null
                ? enCurso
                : enCurso.stream().filter(e -> codigo.equalsIgnoreCase(e.codigo())).toList();

        if (candidatos.isEmpty()) {
            return respuesta(turno, Accion.CONTINUAR_BORRADOR, Desenlace.RESPONDIDO,
                    SIN_BORRADOR_EN_CURSO, lectura, List.of(), List.of(), false, Resultado.NADA);
        }
        if (candidatos.size() > 1) {
            return respuesta(turno, Accion.CONTINUAR_BORRADOR, Desenlace.PREGUNTA,
                    VARIAS_COINCIDENCIAS, lectura, List.of("codigo"),
                    candidatos.stream().map(EstadoCaptura::codigo).toList(), false, Resultado.NADA);
        }
        return conEstadoDeCaptura(turno, Accion.CONTINUAR_BORRADOR, lectura, confirmacion,
                brox.captura(sesion, candidatos.get(0).idBorrador(), traza.invocando(Herramientas.CONSULTAR_CAPTURA.nombre())));
    }

    /** Completar datos puede preparar la revisión; este puerto nunca confirma el alta. */
    private Respuesta registrarPropiedad(Turno turno, Interprete.Lectura lectura,
                                         Herramienta puerta, Permisos permisos, Traza traza,
                                         SesionBrox sesion) {
        Confirmacion deLaPuerta = permisos.confirmacionDe(puerta);
        var avance = brox.avanzarCaptura(sesion, Vocabulario.INTENCION_REGISTRAR_PROPIEDAD,
                turno.idBorrador(), turno.versionBorrador(), lectura.datos(), traza.invocando(puerta.nombre()));

        Respuesta posterior;
        try {
            posterior = proponerCaptura(turno, Accion.REGISTRAR_PROPIEDAD, lectura, deLaPuerta,
                    permisos, sesion, traza, avance.resultado());
        } catch (com.controllocal.asistencia.brox.ErrorBrox error) {
            Desenlace desenlace = switch (error.tipo()) {
                case PERMISO_INSUFICIENTE -> Desenlace.SIN_PERMISO;
                case CAPACIDAD_DESHABILITADA -> Desenlace.CAPACIDAD_DESHABILITADA;
                case FALLO_TECNICO -> Desenlace.FALLO_TECNICO;
                case SOLICITUD_RECHAZADA -> Desenlace.RECHAZADO;
            };
            // El avance ya tiene resultado confirmado. Fallar al preparar su revisión no lo revierte.
            posterior = respuesta(turno, Accion.REGISTRAR_PROPIEDAD, desenlace, error.codigo(), lectura,
                    List.of(), List.of(), false, Resultado.NADA);
        }
        posterior = new Respuesta(posterior.conversacionId(), posterior.turnoId(), posterior.accion(),
                posterior.desenlace(), posterior.motivo(), posterior.comprendido(), posterior.noComprendido(),
                posterior.falta(), posterior.opciones(), posterior.confirmaUnaPersona(),
                posterior.resultado().conCaptura(avance.resultado()));
        return conAutomatico(posterior, avance.operacion());
    }

    private static Respuesta conAutomatico(Respuesta r, ClienteBrox.ReferenciaAutomatico operacion) {
        return new Respuesta(r.conversacionId(), r.turnoId(), r.accion(), r.desenlace(), r.motivo(),
                r.comprendido(), r.noComprendido(), r.falta(), r.opciones(), r.confirmaUnaPersona(),
                r.resultado().conAutomatico(operacion));
    }

    private Respuesta prepararCaptura(Turno turno, Interprete.Lectura lectura,
                                      Permisos permisos, SesionBrox sesion, Traza traza) {
        if (turno.idBorrador() == null) {
            return respuesta(turno, Accion.PREPARAR_CAPTURA, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("idBorrador"), List.of(), false, Resultado.NADA);
        }
        EstadoCaptura estado = brox.captura(sesion, turno.idBorrador(),
                traza.invocando(Herramientas.CONSULTAR_CAPTURA.nombre()));
        return proponerCaptura(turno, Accion.PREPARAR_CAPTURA, lectura,
                permisos.confirmacionDe(Herramientas.PREPARAR_ACTUACION_CAPTURA),
                permisos, sesion, traza, estado);
    }

    private Respuesta proponerCaptura(Turno turno, Accion accion, Interprete.Lectura lectura,
                                      Confirmacion confirmacion, Permisos permisos,
                                      SesionBrox sesion, Traza traza, EstadoCaptura estado) {
        if (!estado.listoParaEjecutar()) {
            return conEstadoDeCaptura(turno, accion, lectura, confirmacion, estado);
        }
        Herramienta preparar = Herramientas.PREPARAR_ACTUACION_CAPTURA;
        Optional<Respuesta> rechazo = permisos.rechazo(turno, accion, preparar, lectura);
        if (rechazo.isPresent()) {
            return rechazo.get();
        }
        // Preparar no ejecuta. La revisión/confirmación autenticada vive en su superficie segura;
        // este puerto no dispone de un método con el que inventar o enviar esa prueba.
        var propuesta = brox.prepararCaptura(sesion, estado.idBorrador(), traza.invocando(preparar.nombre()), estado);
        var operacion = new ClienteBrox.ResultadoActuacion(propuesta.referencia(), "PREPARADA",
                propuesta, null, null, null);
        return respuesta(turno, accion, Desenlace.PROPUESTA,
                CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true,
                Resultado.NADA.conCaptura(estado).conActuacion(operacion));
    }

    // ==================================================================
    // Escrituras
    // ==================================================================

    /**
     * Alta de propietario, <b>buscando antes de pedir</b>.
     *
     * <p>El duplicado de personas es el error mas caro de un CRM inmobiliario:
     * no lo arregla desactivar la ficha repetida, porque la historia queda
     * partida en dos y la busqueda del paso 1 de toda captacion futura devuelve
     * las dos. Por eso el documento manda: con el se busca y se devuelve el que
     * ya existe; sin el no se crea nada y se pregunta, en vez de fiarse de que
     * dos personas con el mismo apellido son la misma.
     */
    private Respuesta registrarPropietario(Turno turno, Interprete.Lectura lectura,
                                           Confirmacion confirmacion, Traza traza,
                                           SesionBrox sesion) {
        Map<String, String> datos = lectura.datos();
        String documento = datos.get("numeroDocumento");
        String nombre = datos.get("nombre");
        boolean confirma = confirmacion.exigeRevisionHumana();

        if (documento == null || documento.isBlank()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("numeroDocumento"), List.of(), confirma, Resultado.NADA);
        }
        Map<String, String> alta = new HashMap<>(datos);
        alta.putIfAbsent("tipoPersona", documento.length() == 11 ? "J" : "N");
        alta.putIfAbsent("tipoDocumento", documento.length() == 11 ? "R" : "D");
        Traza deAlta = traza.invocando(Herramientas.REGISTRAR_PROPIETARIO.nombre());
        if (!confirma) {
            var previo = brox.recuperarPropietario(sesion, alta, deAlta);
            if (previo.isPresent()) return propietarioRegistrado(turno, lectura, previo.get());
        }
        List<ClienteBrox.Persona> yaEstan = brox.buscarPropietarios(sesion, documento,
                traza.invocando(Herramientas.BUSCAR_PROPIETARIOS.nombre()));
        if (!yaEstan.isEmpty()) {
            // El primer intento pudo confirmar entre la consulta del comando y esta búsqueda.
            if (!confirma) {
                var previo = brox.recuperarPropietario(sesion, alta, deAlta);
                if (previo.isPresent()) return propietarioRegistrado(turno, lectura, previo.get());
            }
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.RESPONDIDO, YA_EXISTE,
                    lectura, List.of(), List.of(), false,
                    Resultado.NADA.conPersonas(yaEstan).conPersona(yaEstan.get(0)));
        }
        if (nombre == null || nombre.isBlank()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("nombre"), List.of(), confirma, Resultado.NADA);
        }
        if (confirma) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PROPUESTA,
                    CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true, Resultado.NADA);
        }
        return propietarioRegistrado(turno, lectura, brox.registrarPropietario(sesion, alta, deAlta));
    }

    private Respuesta propietarioRegistrado(Turno turno, Interprete.Lectura lectura,
                                            ClienteBrox.ResultadoAutomatico<ClienteBrox.Persona> creado) {
        return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.EJECUTADO, null, lectura,
                List.of(), List.of(), false, Resultado.NADA.conPersona(creado.resultado())
                        .conAutomatico(creado.operacion()));
    }


    // ==================================================================
    // Permisos y confirmacion
    // ==================================================================

    /**
     * Lo que BROX declara para esta sesion, y como se compone con el catalogo.
     *
     * <p>Es una clase y no tres ifs sueltos porque la composicion tiene que ser
     * <b>la misma</b> en las dos herramientas que usa una captura: si abrir
     * compusiera de una forma y ejecutar de otra, la segunda seria la que
     * concede de mas.
     */
    private record Permisos(List<Capacidad> publicadas) {

        Optional<Capacidad> de(Herramienta herramienta) {
            return publicadas.stream()
                    .filter(c -> herramienta.nombre().equals(c.idCore()))
                    .findFirst();
        }

        /** {@code true} si BROX publico su catalogo y esta herramienta no esta. */
        boolean noLaOfrece(Herramienta herramienta) {
            return de(herramienta).isEmpty();
        }

        Confirmacion confirmacionDe(Herramienta herramienta) {
            Confirmacion delCore = de(herramienta).filter(Capacidad::laConfirmaUnaPersona)
                    .map(c -> Confirmacion.SIEMPRE)
                    .orElse(Confirmacion.NUNCA);
            return herramienta.confirmacion().mas(delCore);
        }

        /** El motivo por el que este turno no llega a llamar, si lo hay. */
        Optional<Respuesta> rechazo(Turno turno, Accion accion, Herramienta herramienta,
                                    Interprete.Lectura lectura) {
            if (noLaOfrece(herramienta)) {
                // No es un fallo: es que esta sesion no tiene esa capacidad. Se
                // dice, en vez de intentarlo y recibir un 403 que habria que
                // traducir.
                return Optional.of(respuesta(turno, accion, Desenlace.CAPACIDAD_DESHABILITADA,
                        CAPACIDAD_NO_DISPONIBLE, lectura, List.of(), List.of(), false,
                        Resultado.NADA));
            }
            Capacidad capacidad = de(herramienta).orElseThrow();
            if ("PERMISO_INSUFICIENTE".equals(capacidad.estado())) {
                return Optional.of(respuesta(turno, accion, Desenlace.SIN_PERMISO,
                        "PERMISO_INSUFICIENTE", lectura, List.of(), List.of(), false, Resultado.NADA));
            }
            if ("DESHABILITADA".equals(capacidad.estado())) {
                return Optional.of(respuesta(turno, accion, Desenlace.CAPACIDAD_DESHABILITADA,
                        "CAPACIDAD_DESHABILITADA", lectura, List.of(), List.of(), false, Resultado.NADA));
            }
            if (de(herramienta).filter(c -> !c.laPuedePedirUnAgente()).isPresent()) {
                return Optional.of(respuesta(turno, accion, Desenlace.SOLO_HUMANO,
                        RESERVADA_A_UNA_PERSONA, lectura, List.of(), List.of(), false,
                        Resultado.NADA));
            }
            return Optional.empty();
        }
    }

    // ==================================================================

    private static String normalizado(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(Locale.ROOT);
    }

    /** El turno que termina enseñando el estado de una captura, tal como BROX lo dio. */
    private static Respuesta conEstadoDeCaptura(Turno turno, Accion accion,
                                                Interprete.Lectura lectura,
                                                Confirmacion confirmacion, EstadoCaptura estado) {
        // El ROTULO es lo que se dice en voz alta; el valor es lo que se
        // devuelve. Antes eran la misma cadena porque la opcion era un String,
        // y por eso KAIROS leia codigos como "LOCAL" en una conversacion.
        List<String> opciones = estado.siguiente() == null || estado.siguiente().opciones() == null
                ? List.of()
                : estado.siguiente().opciones().stream()
                        .map(ClienteBrox.Opcion::rotulo)
                        .toList();
        boolean completo = estado.faltante() == null || estado.faltante().isEmpty();
        // Completo no es ejecutado: lo que viene despues es preparar_actuacion_captura, y
        // esa la confirma SIEMPRE una persona.
        return new Respuesta(turno.conversacionId(), turno.turnoId(), accion,
                completo ? Desenlace.PROPUESTA : Desenlace.PREGUNTA,
                completo ? CONFIRMA_UNA_PERSONA : FALTAN_DATOS,
                lectura.datos() == null ? Map.of() : Map.copyOf(lectura.datos()),
                lectura.noEntendido(), estado.faltante() == null ? List.of() : estado.faltante(),
                opciones,
                completo || confirmacion.exigeRevisionHumana(),
                Resultado.NADA.conCaptura(estado));
    }

    private static Respuesta respuesta(Turno turno, Accion accion, Desenlace desenlace,
                                       String motivo, Interprete.Lectura lectura,
                                       List<String> falta, List<String> opciones,
                                       boolean confirmaUnaPersona, Resultado resultado) {
        return new Respuesta(turno.conversacionId(), turno.turnoId(), accion, desenlace, motivo,
                lectura.datos() == null ? Map.of() : Map.copyOf(lectura.datos()),
                lectura.noEntendido() == null ? List.of() : lectura.noEntendido(),
                falta, opciones, confirmaUnaPersona, resultado);
    }
}





