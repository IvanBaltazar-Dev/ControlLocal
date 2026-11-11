package com.controllocal.service.materialia;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.documentos.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.AmbitoTratamiento;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;

/** C6/E33/E44: fuentes reales por recurso; preparar no crea piezas ni Encargos ni invoca IA. */
@Component
public class FuentesMaterialIaCore implements FuentesMaterialIa {
    private record Fuente(Map<String,Object> datos,String version) { }
    private final PlantillaComercialV08 proyecciones;
    private final AccesoEncargo encargos;
    private final SeleccionInmueblePieza inmuebles;
    private final SeleccionFotosContextualesService galerias;
    private final ArchivosFoto archivos;
    private final EvaluacionTecnicaFoto evaluador;
    private final EvidenciasTratamientoMaterialIa evidencias;
    private final ContextoPresupuestoB0 contextos;
    private final ContextosDeActividad actividad;
    private final Documentos json;
    private final TransactionTemplate transaccion;

    public FuentesMaterialIaCore(PlantillaComercialV08 proyecciones,AccesoEncargo encargos,
            SeleccionInmueblePieza inmuebles,SeleccionFotosContextualesService galerias,ArchivosFoto archivos,
            EvaluacionTecnicaFoto evaluador,EvidenciasTratamientoMaterialIa evidencias,ContextoPresupuestoB0 contextos,
            ContextosDeActividad actividad,Documentos json,PlatformTransactionManager gestor){
        this.proyecciones=proyecciones;this.encargos=encargos;this.inmuebles=inmuebles;this.galerias=galerias;
        this.archivos=archivos;this.evaluador=evaluador;this.evidencias=evidencias;this.contextos=contextos;
        this.actividad=actividad;this.json=json;this.transaccion=new TransactionTemplate(gestor);
    }

    @Override public Snapshot preparar(Solicitud s,Actor actor,ContextoSesion sesion){
        Fuente f=tx(()->{contextos.iniciarConsumo(actor,sesion);return fuente(s,actor);});
        EvaluacionTecnicaFoto.Copia foto=s.accion()==Accion.REDACTAR_FICHA?null:procesar(s,actor);
        String instruccion=instruccion(s);
        var utilidad=new LinkedHashMap<String,Object>();
        if(!f.datos().isEmpty())utilidad.put("borradorEstructurado",f.datos());
        if(foto!=null)utilidad.put("evaluacionTecnicaLocal",json.comoMapa(json.objetoCanonico(foto.evaluacion())));
        String origen=huella(f,foto,instruccion);
        var ambito=ambito(s,origen,f.datos(),instruccion);
        var evidencia=tx(()->{
            contextos.iniciarConsumo(actor,sesion);exigirMismaFuente(s,f.version(),actor);
            return evidencias.resolver(ambito,actor,sesion);
        });
        List<String> pendientes=evidencia.estado()==EstadoEvidencia.ACREDITADA?List.of():List.of("TRATAMIENTO_IA_NO_ACREDITADO");
        return new Snapshot(f.version(),origen,f.datos(),instruccion,utilidad,pendientes,evidencia);
    }

    /** Superficie propia E44: el ámbito se resuelve en Core, nunca desde una declaración positiva del cliente/modelo. */
    public EvidenciasTratamientoMaterialIa.AvisoParaDecision presentarTratamiento(Solicitud s,Actor actor,ContextoSesion sesion){
        Snapshot f=preparar(s,actor,sesion);
        return tx(()->{
            contextos.iniciarConsumo(actor,sesion);exigirSnapshot(s,f,actor);
            return evidencias.presentar(ambito(s,f),actor,sesion);
        });
    }

    @Override public void revalidar(Solicitud s,Snapshot snapshot,Actor actor){
        tx(()->{
            exigirSnapshot(s,snapshot,actor);
            if(snapshot.evidencia()!=null&&snapshot.evidencia().estado()==EstadoEvidencia.ACREDITADA)
                evidencias.revalidarActual(snapshot.evidencia().referenciaEvidencia(),ambito(s,snapshot),actor);
            return null;
        });
    }

    /** Leer una salida previa no exige que los hechos físicos sigan en la versión usada al generarla. */
    @Override public void exigirLecturaHistorica(Solicitud s,Snapshot snapshot,Actor actor){
        tx(()->{
            exigirRecursoDeLectura(s,actor);
            if(snapshot.evidencia()!=null&&snapshot.evidencia().estado()==EstadoEvidencia.ACREDITADA)
                evidencias.revalidarActual(snapshot.evidencia().referenciaEvidencia(),ambito(s,snapshot),actor);
            return null;
        });
    }

    /** E33/UC-K36: el archivo del recurso no concede el job privado ni otra transferencia. */
    @Override public void exigirLecturaArtefacto(Solicitud s,Snapshot snapshot,Actor actor){
        tx(()->{
            exigirArtefactoLegible(s,snapshot,actor);
            evidencias.revalidarArtefacto(snapshot.evidencia().referenciaEvidencia(),ambito(s,snapshot),actor);
            return null;
        });
    }

    @Override public EstadoUsoArtefacto evaluarLecturaArtefacto(Solicitud s,Snapshot snapshot,Actor actor){
        return tx(()->{
            try {
                exigirArtefactoLegible(s,snapshot,actor);
                return Objects.requireNonNull(evidencias.evaluarUsoArtefacto(
                        snapshot.evidencia().referenciaEvidencia(),ambito(s,snapshot),actor),
                        "La evaluación de uso no devolvió un estado.");
            } catch (AccesoNoAutorizadoException | NoEncontradoException denegada) {
                return EstadoUsoArtefacto.PERMISO_INSUFICIENTE;
            } catch (CapacidadDeshabilitadaException deshabilitada) {
                return EstadoUsoArtefacto.CAPACIDAD_DESHABILITADA;
            }
        });
    }

    private void exigirArtefactoLegible(Solicitud s,Snapshot snapshot,Actor actor){
        exigirRecursoDeLectura(s,actor);
        if(s.accion()!=Accion.MEJORAR_FOTO||s.fotoOriginal()==null)
            throw new ReglaNegocioException("El artefacto requiere su fotografía original y mejora acreditada.");
        AutoridadMaterialIa.exigirEvidencia(snapshot);
    }

    private void exigirRecursoDeLectura(Solicitud s,Actor actor){
        actividad.exigirAgente(actor);validar(s);long id=numero(s.recurso().referencia());
        if("PROPIEDAD".equals(s.recurso().tipo())){
            inmuebles.exigirLecturaHistorica(actor.idOrganizacion(),id,actor);
            if(s.fotoOriginal()!=null&&!galerias.originalPermitidoParaLectura(id,s.fotoOriginal(),actor))
                throw new NoEncontradoException("Fotografía original de este inmueble");
        }else{
            var c=encargos.leer(id,actor);encargos.exigirResponsable(c,actor);
            inmuebles.exigirLecturaHistorica(actor.idOrganizacion(),c.getPropiedad().getId(),actor);
        }
    }

    @Override public void exigirTransferencia(Solicitud s,Snapshot snapshot,DestinoTratamiento destino,Actor actor,ContextoSesion sesion){
        tx(()->{
            contextos.iniciarConsumo(actor,sesion);exigirSnapshot(s,snapshot,actor);AutoridadMaterialIa.exigirEvidencia(snapshot);
            evidencias.exigirVigente(snapshot.evidencia().referenciaEvidencia(),ambito(s,snapshot),destino,actor,sesion);
            return null;
        });
    }

    @Override public byte[] fotoParaProcesar(Solicitud s,Snapshot snapshot,Actor actor,ContextoSesion sesion){
        Fuente f=tx(()->{contextos.iniciarConsumo(actor,sesion);return exigirMismaFuente(s,snapshot.versionOrigen(),actor);});
        var foto=procesar(s,actor);
        if(!snapshot.huellaOrigen().equals(huella(f,foto,instruccion(s))))
            throw new ConflictoException("La fotografía o el contenido cambió; vuelve a preparar la asistencia.");
        tx(()->{contextos.iniciarConsumo(actor,sesion);exigirMismaFuente(s,snapshot.versionOrigen(),actor);return null;});
        return foto.contenido();
    }

    /** Sólo metadatos/lecturas Core en esta transacción; decodificar bytes queda fuera. */
    private Fuente fuente(Solicitud s,Actor actor){
        actividad.exigirAgente(actor);validar(s);
        long recurso=numero(s.recurso().referencia());
        var datos=new LinkedHashMap<String,Object>();var identidad=new LinkedHashMap<String,Object>();
        identidad.put("recurso",s.recurso());identidad.put("accion",s.accion());
        if("PROPIEDAD".equals(s.recurso().tipo())){
            if(s.accion()==Accion.REDACTAR_FICHA){
                var fisica=proyecciones.prepararFisicaPrivada(recurso,actor);
                datos.put("inmueble",fisica.datos());identidad.put("versionFisica",fisica.versionFisica());
            }else{
                var propiedad=inmuebles.exigirLecturaHistorica(actor.idOrganizacion(),recurso,actor);
                identidad.put("versionFisica",propiedad.getVersionFisica());
                var g=galerias.observar(recurso,actor);
                if(g.fotos().stream().noneMatch(f->f.referencia().equals(s.fotoOriginal())))throw new NoEncontradoException("Fotografía de este inmueble");
                identidad.put("galeria",g.versionObservada());identidad.put("original",s.fotoOriginal());
            }
        }else{
            var c=encargos.leer(recurso,actor);encargos.exigirResponsable(c,actor);
            var p=inmuebles.elegir(c,null,actor).propiedad();
            // Se reutiliza el proyector puro: la identidad del intento es sólo metadato temporal.
            // No se crea ni guarda una PiezaComercial ni una publicación.
            var entrada=new PiezasComercialesService.Preparacion(recurso,c.getVersion(),p.getVersionFisica(),
                    "WEB_PROPIA",List.of(),null,PiezasComercialesService.Formato.PDF_A4,"marfil",null,p.getId());
            var proyeccion=proyecciones.proyectar(entrada,s.operacion(),1,"REVISION_PRIVADA",actor);
            datos.put("inmueble",proyeccion.datos().get("inmueble"));
            var encargo=new LinkedHashMap<>(mapa(proyeccion.datos().get("encargo")));
            encargo.remove("id");encargo.remove("version");datos.put("encargo",encargo);
            identidad.put("versionComercial",proyeccion.huellaOrigen());
        }
        // Marca, contactos, IDs, expediente y evidencias no forman parte del payload al procesador.
        Map<String,Object> permitidos=depurar(datos);
        identidad.put("datos",permitidos);identidad.put("instruccion",instruccion(s));
        return new Fuente(permitidos,Idempotencia.huellaExacta(json.objetoCanonico(identidad)));
    }

    private Fuente exigirMismaFuente(Solicitud s,String version,Actor actor){
        var actual=fuente(s,actor);
        if(!Objects.equals(version,actual.version()))throw new ConflictoException("El recurso cambió; vuelve a preparar la asistencia.");
        return actual;
    }
    private void exigirSnapshot(Solicitud s,Snapshot snapshot,Actor actor){
        var actual=exigirMismaFuente(s,snapshot.versionOrigen(),actor);
        if(!json.objetoCanonico(actual.datos()).equals(json.objetoCanonico(snapshot.datosPermitidos()))
                ||!instruccion(s).equals(snapshot.instruccionPermitida()))
            throw new ConflictoException("La proyección o instrucción no corresponde a esta preparación.");
    }
    private EvaluacionTecnicaFoto.Copia procesar(Solicitud s,Actor actor){
        if(s.accion()==Accion.REDACTAR_FICHA)throw new ReglaNegocioException("La redacción no procesa una fotografía.");
        return evaluador.evaluar(archivos.original(s.fotoOriginal(),actor));
    }
    private String huella(Fuente f,EvaluacionTecnicaFoto.Copia foto,String instruccion){
        return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("version",f.version(),"datos",f.datos(),
                "instruccion",instruccion,"copia",foto==null?"":foto.huella(),
                "original",foto==null?"":foto.evaluacion().huellaOriginal())));
    }
    private AmbitoTratamiento ambito(Solicitud s,Snapshot f){return ambito(s,f.huellaOrigen(),f.datosPermitidos(),f.instruccionPermitida());}
    private AmbitoTratamiento ambito(Solicitud s,String fuente,Map<String,Object> datos,String instruccion){
        var categorias=new TreeSet<String>(Set.of("INSTRUCCION"));
        if(s.accion()==Accion.REDACTAR_FICHA)categorias.add("CARACTERISTICAS_FISICAS");
        if(datos.containsKey("encargo"))categorias.add("CONDICIONES_COMERCIALES");
        if(s.accion()!=Accion.REDACTAR_FICHA)categorias.add("FOTO_COMERCIAL");
        return new AmbitoTratamiento(s,fuente,categorias,
                Idempotencia.huellaExacta(json.objetoCanonico(Map.of("datos",datos,"instruccion",instruccion,"fuente",fuente))));
    }
    /** Sólo se depuran metadatos del esquema comercial conocido, no se promete un filtro universal de texto. */
    @SuppressWarnings("unchecked") private Map<String,Object> depurar(Map<String,Object> datos){
        return (Map<String,Object>)sinReferencias(json.comoMapa(json.objetoCanonico(datos)));
    }
    private Object sinReferencias(Object valor){
        if(valor instanceof Map<?,?> m){var r=new LinkedHashMap<String,Object>();m.forEach((k,v)->{
            if(!Set.of("evidenciaRef","observacion").contains(k.toString()))r.put(k.toString(),sinReferencias(v));});return r;}
        if(valor instanceof List<?> l)return l.stream().map(this::sinReferencias).toList();return valor;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> mapa(Object o){return (Map<String,Object>)o;}
    private <T>T tx(Supplier<T> f){return transaccion.execute(s->f.get());}
    /** UC-K-37 y aprobación01/11: propósito acotado más indicación voluntaria, sin otra confirmación económica. */
    private static String instruccion(Solicitud s){
        String proposito=switch(s.accion()){
            case REDACTAR_FICHA->"Redactar una ficha inmobiliaria usando exclusivamente los hechos comerciales proporcionados. Conservar conceptos, unidades, monedas, condiciones y faltantes; no inventar atributos ni autorizaciones.";
            case ANALIZAR_FOTO->"Analizar la fotografía proporcionada sin retocarla. Describir las observaciones con su incertidumbre; no convertir inferencias visuales en hechos acreditados del inmueble.";
            case MEJORAR_FOTO->"Mejorar la fotografía proporcionada conservando estructura, elementos, proporciones y defectos reales. No crear atributos ni ocultar defectos. Producir un derivado sin reemplazar el original ni publicar.";
        };
        if(s.instruccion()==null||s.instruccion().isBlank())return proposito;
        // Forma parte del payload y de su cobertura E44; no se adjunta texto privado de repositorios.
        // No se promete filtrar toda información personal ni convertir texto en permiso comercial.
        return proposito+"\nIndicaciones personalizadas del solicitante (no acreditan hechos ni autorizan otros actos):\n"+s.instruccion();
    }
    private static long numero(String r){try{long n=Long.parseLong(r);if(n<=0)throw new NumberFormatException();return n;}catch(RuntimeException e){throw new ReglaNegocioException("Identifica el recurso de la asistencia.");}}
    private static void validar(Solicitud s){
        if(s==null||s.operacion()==null||s.accion()==null||s.recurso()==null||s.recurso().tipo()==null
                ||!Set.of("PROPIEDAD","ENCARGO").contains(s.recurso().tipo())||s.recurso().referencia()==null
                ||!s.recurso().referencia().matches("[1-9][0-9]*"))
            throw new ReglaNegocioException("Indica la acción y el recurso de la asistencia.");
        if(s.accion()!=Accion.REDACTAR_FICHA&&(!"PROPIEDAD".equals(s.recurso().tipo())||s.fotoOriginal()==null)
                ||s.accion()==Accion.REDACTAR_FICHA&&s.fotoOriginal()!=null)throw new ReglaNegocioException("La fotografía pertenece a una acción sobre su inmueble original.");
    }
}
