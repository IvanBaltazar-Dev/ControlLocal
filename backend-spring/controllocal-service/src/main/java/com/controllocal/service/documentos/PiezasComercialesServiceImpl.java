package com.controllocal.service.documentos;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.inmueble.PiezaComercial;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Una única preparación/entrega por comando. El renderer nunca concede autorización. */
@Service
public class PiezasComercialesServiceImpl implements PiezasComercialesService {
    private static final String PREPARAR="preparar_pieza_comercial",PREVISUALIZAR="previsualizar_pieza_comercial",REGENERAR="regenerar_pieza_comercial";
    public record Contenido(Preparacion datos,List<FotosComercialesService.FotoSalida> copias,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
            List<ResultadosMaterialIa.Seleccion> selecciones) {
        public Contenido { copias=List.copyOf(copias);selecciones=selecciones==null?List.of():List.copyOf(selecciones); }
    }
    private final PiezaComercialRepository piezas;private final AccesoEncargo acceso;
    private final ContextosDeActividad contextos;private final SeleccionFotosContextualesService seleccion;
    private final FotosComercialesService fotos;private final PlantillaComercialV08 proyecciones;
    private final PublicacionService publicaciones;private final CompositorPieza compositor;
    private final SeleccionInmueblePieza inmuebles;
    private final com.controllocal.service.materialia.ResultadosMaterialIa derivados;
    private final EvaluacionTecnicaFoto evaluador;
    private final ArchivosPieza archivos;private final ComandoIdempotenteRepository locks;
    private final ComandosIdempotentes comandos;private final Documentos json;private final EventoDominioRepository eventos;
    public PiezasComercialesServiceImpl(PiezaComercialRepository piezas,AccesoEncargo acceso,
            ContextosDeActividad contextos,SeleccionFotosContextualesService seleccion,FotosComercialesService fotos,
            PlantillaComercialV08 proyecciones,PublicacionService publicaciones,CompositorPieza compositor,
            ArchivosPieza archivos,ComandoIdempotenteRepository locks,ComandosIdempotentes comandos,
            Documentos json,EventoDominioRepository eventos,SeleccionInmueblePieza inmuebles,
            com.controllocal.service.materialia.ResultadosMaterialIa derivados,EvaluacionTecnicaFoto evaluador){
        this.piezas=piezas;this.acceso=acceso;this.contextos=contextos;this.seleccion=seleccion;this.fotos=fotos;
        this.proyecciones=proyecciones;this.publicaciones=publicaciones;this.compositor=compositor;this.archivos=archivos;
        this.locks=locks;this.comandos=comandos;this.json=json;this.eventos=eventos;this.inmuebles=inmuebles;
        this.derivados=derivados;this.evaluador=evaluador;
    }
    @Override
    public boolean composicionHabilitada(){return compositor.configurado();}
    @Override @Transactional(readOnly=true)
    public Map<String,Object> consultarPlantillasYFormatos(long encargo,Actor actor){
        leerEncargo(encargo,actor);
        return Map.of("plantillas",List.of(Map.of("id","BROX-FICHA","version","V08","versionContrato","1.13.0",
                "requiereIA",false,"formatos",List.of("PDF_A4","PNG_VERTICAL"))),
                "marcaBrox",true,"publicaAutomaticamente",false);
    }
    @Override @Transactional(readOnly=true)
    public SeleccionFotosContextualesService.Observacion consultarFotosPermitidas(long encargo,Long propiedadFicha,Actor actor){
        var s=inmuebles.elegir(leerEncargo(encargo,actor),propiedadFicha,actor);
        return seleccion.observar(s.propiedad().getId(),actor);
    }
    @Override @Transactional(readOnly=true)
    public Estado consultarEstado(UUID referencia,Actor actor){return estado(leer(referencia,actor),actor);}
    @Override @Transactional
    public Estado preparar(Preparacion d,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia){
        String hash=iniciar(PREPARAR,d,actor,sesion,clave,procedencia);
        var previo=recibo(PREPARAR,clave,hash,actor);
        if(previo.isPresent())return consultarEstado(UUID.fromString((String)previo.get().get("pieza")),actor);
        // C1 ya retiene la cuenta propia: rechazar alcance ajeno antes de tomar cuentas del recurso.
        contextos.exigirAgente(actor);acceso.exigirLectura(d.encargo(),actor);
        var c=acceso.leerParaActuar(d.encargo(),actor,acceso.iniciarConLecturaProtegida(actor,sesion,d.encargo()));
        acceso.exigirResponsable(c,actor);
        var objeto=inmuebles.elegir(c,d.propiedadFicha(),actor);
        Contenido contenido=contenido(d,objeto.propiedad().getId(),actor);
        UUID ref=UUID.randomUUID();var base=proyecciones.proyectar(contenido.datos(),ref,1,"REVISION_PRIVADA",actor);
        var p=PiezaComercial.preparar(actor.idOrganizacion(),d.encargo(),base.propiedad(),base.responsable(),
                ref,ref,1,json.objetoCanonico(contenido),json.objetoCanonico(base.datos()),huella(base,contenido),d.formato().name(),contextos.ahora());
        piezas.saveAndFlush(p);Estado resultado=estado(p,actor);
        registrar(PREPARAR,clave,hash,p,resultado,actor,procedencia);return resultado;
    }
    @Override @Transactional
    public Estado regenerar(Regeneracion d,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia){
        String hash=iniciar(REGENERAR,d,actor,sesion,clave,procedencia);
        var previo=recibo(REGENERAR,clave,hash,actor);
        if(previo.isPresent())return consultarEstado(UUID.fromString((String)previo.get().get("pieza")),actor);
        if(d==null||d.preparacion()==null)throw new ReglaNegocioException("Indica la pieza y sus datos actuales.");
        var anterior=piezas.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),d.pieza())
                .orElseThrow(()->new NoEncontradoException("Pieza"));
        contextos.exigirAgente(actor);acceso.exigirLectura(anterior.getEncargo(),actor);
        var c=acceso.leerParaActuar(anterior.getEncargo(),actor,acceso.iniciarConLecturaProtegida(actor,sesion,anterior.getEncargo()));
        acceso.exigirResponsable(c,actor);bloquearFamilia(anterior,actor);
        anterior=piezas.bloquear(actor.idOrganizacion(),d.pieza()).orElseThrow(()->new NoEncontradoException("Pieza"));
        var ultima=piezas.findFirstByOrganizacionIdAndFamiliaOrderByVersionContenidoDesc(actor.idOrganizacion(),anterior.getFamilia()).orElseThrow();
        if(anterior.getVersionContenido()!=d.versionContenido()||ultima.getVersionContenido()!=d.versionContenido())
            throw new ConflictoException("La pieza ya tiene otra versión; consulta la actual.");
        if(d.preparacion().encargo()!=anterior.getEncargo())throw new ReglaNegocioException("La nueva versión conserva el mismo Encargo.");
        var objeto=inmuebles.elegir(c,d.preparacion().propiedadFicha(),actor);
        Contenido contenido=contenido(d.preparacion(),objeto.propiedad().getId(),actor);long version=anterior.getVersionContenido()+1;
        UUID ref=UUID.randomUUID();var base=proyecciones.proyectar(contenido.datos(),ref,version,"REVISION_PRIVADA",actor);
        var p=PiezaComercial.preparar(actor.idOrganizacion(),anterior.getEncargo(),base.propiedad(),base.responsable(),
                ref,anterior.getFamilia(),version,json.objetoCanonico(contenido),json.objetoCanonico(base.datos()),huella(base,contenido),d.preparacion().formato().name(),contextos.ahora());
        piezas.saveAndFlush(p);Estado resultado=estado(p,actor);
        registrar(REGENERAR,clave,hash,p,resultado,actor,procedencia);return resultado;
    }
    @Override @Transactional
    public Archivo previsualizar(UUID pieza,long version,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia){
        var d=new Entrega(pieza,version,null);String hash=iniciar(PREVISUALIZAR,d,actor,sesion,clave,procedencia);
        var previo=recibo(PREVISUALIZAR,clave,hash,actor);
        if(previo.isPresent())return restaurarArchivo(previo.get(),actor);
        var p=paraGenerar(d,actor,sesion,false);Archivo resultado=generar(p,"REVISION_PRIVADA",actor,sesion);
        registrar(PREVISUALIZAR,clave,hash,p,resultado,actor,procedencia);return resultado;
    }
    @Override @Transactional(readOnly=true)
    public Entrega validarEntrega(Entrega d,Actor actor){
        if(d==null)throw new ReglaNegocioException("Indica la pieza que deseas obtener.");
        var p=leer(d.pieza(),actor);version(p,d.versionContenido());exigirActual(p,actor);
        var contenido=contenido(p);
        if(!inmuebles.elegir(leerEncargo(p.getEncargo(),actor),contenido.datos().propiedadFicha(),actor).pertenece())
            throw new ReglaNegocioException("Selecciona una unidad comprendida en el objeto vigente del Encargo antes de difundir.");
        publicaciones.exigirPublicabilidadDePieza(p.getEncargo(),p.getPropiedad(),contenido.datos().canal(),actor);
        if(p.archivo("DIFUSION_AUTORIZADA")==null&&!composicionHabilitada())throw new CapacidadDeshabilitadaException();
        if(d.huellaOrigen()!=null&&!p.getHuellaOrigen().equals(d.huellaOrigen()))throw desactualizada();
        return new Entrega(p.getReferencia(),p.getVersionContenido(),p.getHuellaOrigen());
    }
    @Override @Transactional(readOnly=true)
    public Object resumenEntrega(Entrega d,Actor actor){
        var exacta=validarEntrega(d,actor);var p=leer(exacta.pieza(),actor);var c=contenido(p);
        return Map.of("pieza",consultarEstado(p.getReferencia(),actor),"seleccionYContacto",c.datos(),
                "fotos",c.copias().stream().map(f->new FotoDeRevision(f.referencia(),f.original(),f.ancho(),f.alto(),
                        f.huella(),f.exifDepurado(),f.originalRegistradoEn())).toList(),
                "efecto","Preparar archivo autorizado para difusión; no publica ni envía.");
    }
    /** Proyección humana: el proceso técnico permanece exclusivamente en el snapshot interno. */
    private record FotoDeRevision(UUID referencia,UUID original,int ancho,int alto,String huella,
                                  boolean exifDepurado,java.time.Instant originalRegistradoEn) { }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaEntrega(Entrega d,Actor actor){leer(d.pieza(),actor);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Archivo obtenerParaDifusion(Entrega d,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia){
        String hash=iniciar(OBTENER,d,actor,sesion,clave,procedencia);var previo=recibo(OBTENER,clave,hash,actor);
        if(previo.isPresent())return restaurarArchivo(previo.get(),actor);
        if(d==null||d.huellaOrigen()==null)throw new ReglaNegocioException("Revisa el contenido exacto de la pieza antes de obtenerla.");
        var p=paraGenerar(d,actor,sesion,true);Archivo resultado=generar(p,"DIFUSION_AUTORIZADA",actor,sesion);
        registrar(OBTENER,clave,hash,p,resultado,actor,procedencia);return resultado;
    }
    @Override @Transactional(readOnly=true)
    public Optional<Archivo> consultarResultadoEntrega(String clave,Actor actor){
        return resultado(OBTENER,clave,actor).map(r->restaurarArchivo(r,actor));
    }
    @Override @Transactional(readOnly=true)
    public Optional<Map<String,Object>> consultarResultadoGeneracion(String cap,String clave,Actor actor){
        if(!Set.of(PREPARAR,PREVISUALIZAR,REGENERAR).contains(cap))throw new ReglaNegocioException("Operación de pieza no reconocida.");
        return resultado(cap,clave,actor).map(r->{
            if(PREVISUALIZAR.equals(cap))return json.comoMapa(json.objetoCanonico(restaurarArchivo(r,actor)));
            return json.comoMapa(json.objetoCanonico(consultarEstado(UUID.fromString((String)r.get("pieza")),actor)));
        });
    }
    @Override @Transactional(readOnly=true)
    public Descarga descargar(UUID ref,Actor actor){
        var p=piezas.deArchivo(actor.idOrganizacion(),Objects.requireNonNull(ref)).orElseThrow(()->new NoEncontradoException("Pieza"));
        leer(p.getReferencia(),actor);exigirFotosActuales(p,actor);
        String finalidad=ref.equals(p.archivo("REVISION_PRIVADA"))?"REVISION_PRIVADA":"DIFUSION_AUTORIZADA";
        if("DIFUSION_AUTORIZADA".equals(finalidad)){exigirActual(p,actor);publicaciones.exigirPublicabilidadDePieza(p.getEncargo(),p.getPropiedad(),contenido(p).datos().canal(),actor);}
        byte[] bytes=archivos.abrir(p.clave(finalidad),actor);
        if(!p.huella(finalidad).equals(EvaluacionTecnicaFoto.huella(bytes)))throw new ConflictoException("El archivo de la pieza no conserva su integridad.");
        leer(p.getReferencia(),actor);exigirFotosActuales(p,actor);
        if("DIFUSION_AUTORIZADA".equals(finalidad)){exigirActual(p,actor);publicaciones.exigirPublicabilidadDePieza(p.getEncargo(),p.getPropiedad(),contenido(p).datos().canal(),actor);}
        return new Descarga(archivo(p,finalidad),bytes);
    }
    private PiezaComercial paraGenerar(Entrega d,Actor actor,ContextoSesion sesion,boolean difusion){
        var vista=piezas.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),d.pieza())
                .orElseThrow(()->new NoEncontradoException("Pieza"));
        contextos.exigirAgente(actor);acceso.exigirLectura(vista.getEncargo(),actor);
        var c=difusion?acceso.escribir(vista.getEncargo(),actor,acceso.iniciar(actor,sesion,vista.getEncargo())):
                acceso.leerParaActuar(vista.getEncargo(),actor,acceso.iniciarConLecturaProtegida(actor,sesion,vista.getEncargo()));
        acceso.exigirResponsable(c,actor);
        var p=piezas.bloquear(actor.idOrganizacion(),d.pieza()).orElseThrow(()->new NoEncontradoException("Pieza"));
        version(p,d.versionContenido());exigirFotosActuales(p,actor);
        if(difusion)validarEntrega(d,actor);return p;
    }
    @SuppressWarnings("unchecked")
    private Archivo generar(PiezaComercial p,String finalidad,Actor actor,ContextoSesion sesion){
        if(p.archivo(finalidad)!=null){
            // Confirmar disponibilidad e integridad real antes de reutilizar la referencia.
            descargar(p.archivo(finalidad),actor);return archivo(p,finalidad);
        }
        boolean lectura=!"DIFUSION_AUTORIZADA".equals(finalidad);
        var inicial=contenido(p);bloquearSeleccionesNuevas(inicial,actor,lectura);
        var recuperado=archivos.recuperar(p.getReferencia(),finalidad,p.getHuellaOrigen(),actor);
        if(recuperado.isPresent()){
            leer(p.getReferencia(),actor);exigirFotosActuales(p,actor);
            exigirSeleccionesNuevas(contenido(p),actor,lectura);
            if("DIFUSION_AUTORIZADA".equals(finalidad)){exigirActual(p,actor);publicaciones.exigirPublicabilidadDePieza(p.getEncargo(),p.getPropiedad(),contenido(p).datos().canal(),actor);}
            p.registrarArchivo(finalidad,UUID.randomUUID(),recuperado.get().clave(),recuperado.get().huella(),contextos.ahora());
            piezas.saveAndFlush(p);protegerSiEmitida(p,finalidad,inicial,actor,sesion);return archivo(p,finalidad);
        }
        if(!composicionHabilitada())throw new CapacidadDeshabilitadaException();
        var c=contenido(p);boolean difusion="DIFUSION_AUTORIZADA".equals(finalidad);
        Map<String,Object> datos=difusion?json.comoMapa(json.objetoCanonico(proyecciones.proyectar(c.datos(),p.getReferencia(),p.getVersionContenido(),finalidad,actor).datos())):
                json.comoMapa(p.getProyeccion());
        if(!difusion){
            var causas=causas(p,actor);
            if(!causas.isEmpty()){
                var ds=new LinkedHashMap<String,Object>();ds.put("desde",contextos.hoy().toString());
                ds.put("causa",causas.contains("NUEVA_VERSION")?"NUEVA_VERSION":"ORIGEN_MODIFICADO");
                if(causas.contains("NUEVA_VERSION"))ds.put("versionVigente",piezas.findFirstByOrganizacionIdAndFamiliaOrderByVersionContenidoDesc(actor.idOrganizacion(),p.getFamilia()).orElseThrow().getVersionContenido());
                ((Map<String,Object>)datos.get("pieza")).put("desactualizada",ds);
            }
        }
        var fotosSalida=new ArrayList<Map<String,Object>>();
        for(int i=0;i<c.copias().size();i++){
            var foto=c.copias().get(i);var seleccionada=c.datos().fotos().get(i);
            byte[] bytes=seleccionada.derivada()==null?fotos.obtener(foto.referencia(),actor):
                    copiaDerivada(seleccionada.derivada(),actor).contenido();
            if(!foto.huella().equals(EvaluacionTecnicaFoto.huella(bytes)))throw desactualizada();
            var f=new LinkedHashMap<String,Object>();f.put("id",foto.referencia().toString());
            f.put("src","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes));
            f.put("ancho",foto.ancho());f.put("alto",foto.alto());
            f.put("ambiente",seleccionada.ambiente()==null?"Galería":seleccionada.ambiente());
            f.put("rotulo",seleccionada.rotulo()==null?"Fotografía" : seleccionada.rotulo());
            f.put("origen",seleccionada.derivada()==null?"ORIGINAL_FUENTE":"DERIVADA_IA");
            if(seleccionada.derivada()!=null)f.put("mejoraIA",true);
            f.put("autorizacion","DIFUSION_AUTORIZADA".equals(finalidad)?"SALIDA_AUTORIZADA":"REVISION_PRIVADA");
            f.put("exifDepurado",true);f.put("sha256",foto.huella());
            f.put("procedencia",Map.of("registradoEn",foto.originalRegistradoEn().atZone(java.time.ZoneId.of("America/Lima")).toLocalDate().toString()));
            fotosSalida.add(f);
        }
        ((Map<String,Object>)datos.get("inmueble")).put("fotos",fotosSalida);
        if(!fotosSalida.isEmpty())((Map<String,Object>)datos.get("presentacion")).put("portada",fotosSalida.getFirst().get("id"));
        var opciones=new LinkedHashMap<String,Object>();opciones.put("vista",Formato.PDF_A4.name().equals(p.getFormato())?"a4":"vertical");
        opciones.put("galeria",true);opciones.put("regional","es-PE");
        if(c.datos().tema()!=null)opciones.put("tema",c.datos().tema());
        var binario=compositor.componer(datos,opciones,p.getFormato());
        if(binario.contenido().length==0)throw new IllegalStateException("El compositor no produjo un archivo.");
        exigirSeleccionesNuevas(c,actor,lectura);
        if(difusion)exigirActual(p,actor);else exigirFotosActuales(p,actor);
        if("DIFUSION_AUTORIZADA".equals(finalidad))publicaciones.exigirPublicabilidadDePieza(p.getEncargo(),p.getPropiedad(),c.datos().canal(),actor);
        var guardado=archivos.guardar(p.getReferencia(),binario,finalidad,p.getHuellaOrigen(),actor);
        exigirSeleccionesNuevas(c,actor,lectura);
        if(difusion)exigirActual(p,actor);else exigirFotosActuales(p,actor);
        if(difusion)publicaciones.exigirPublicabilidadDePieza(p.getEncargo(),p.getPropiedad(),c.datos().canal(),actor);
        p.registrarArchivo(finalidad,UUID.randomUUID(),guardado.clave(),guardado.huella(),contextos.ahora());
        piezas.saveAndFlush(p);protegerSiEmitida(p,finalidad,c,actor,sesion);return archivo(p,finalidad);
    }
    private Contenido contenido(Preparacion d,long propiedad,Actor actor){
        if(d==null||d.formato()==null||d.versionGaleria()==null)throw new ReglaNegocioException("Indica formato y versión observada de la galería.");
        PlantillaComercialV08.validarContacto(d.contacto());
        if(d.tema()!=null&&!Set.of("marfil","claro").contains(d.tema()))throw new CapacidadDeshabilitadaException();
        if(d.fotos().stream().anyMatch(f->f==null||f.original()==null))throw new ReglaNegocioException("Cada fotografía necesita su referencia original.");
        seleccion.seleccionar(propiedad,new SeleccionFotosContextualesService.Solicitud(d.fotos().stream().map(Foto::original).toList(),
                SeleccionFotosContextualesService.Finalidad.REVISION_PRIVADA,d.versionGaleria()),actor);
        var originales=d.fotos().stream().map(Foto::original).toList();
        var vigentes=derivados.bloquearSeleccionesParaLectura(originales,actor);var porOriginal=indiceSelecciones(vigentes,originales);
        var resueltas=new ArrayList<Foto>();
        for(var foto:d.fotos()){
            var actual=porOriginal.get(foto.original());
            if(foto.derivada()!=null&&!corresponde(foto.derivada(),actual))throw desactualizada();
            var derivada=actual.operacion()==null?null:new Derivada(actual.operacion(),actual.archivo(),actual.revision());
            resueltas.add(new Foto(foto.original(),foto.ambiente(),foto.rotulo(),derivada));
        }
        var efectiva=new Preparacion(d.encargo(),d.versionEncargo(),d.versionFisica(),d.canal(),resueltas,
                d.versionGaleria(),d.formato(),d.tema(),d.contacto(),d.propiedadFicha());
        var sinDerivada=efectiva.fotos().stream().filter(f->f.derivada()==null).map(Foto::original).toList();
        var preparadas=sinDerivada.isEmpty()?Map.<UUID,FotosComercialesService.FotoSalida>of():fotos.prepararCopias(propiedad,sinDerivada,actor);
        var copias=new ArrayList<FotosComercialesService.FotoSalida>();
        for(var f:efectiva.fotos()){
            if(f.derivada()==null)copias.add(preparadas.get(f.original()));
            else{
                var opcion=porOriginal.get(f.original());var copia=copiaDerivada(f.derivada(),actor);
                var metadatos=derivados.exigirArchivo(opcion.operacion(),opcion.archivo(),actor);
                if(!f.original().equals(metadatos.original()))throw desactualizada();
                derivados.exigirMismasSeleccionesParaLectura(vigentes,actor);
                copias.add(new FotosComercialesService.FotoSalida(opcion.archivo(),f.original(),copia.evaluacion().anchoCopia(),
                        copia.evaluacion().altoCopia(),copia.huella(),procesoDerivada(metadatos),true,metadatos.originalRegistradoEn()));
            }
        }
        derivados.exigirMismasSeleccionesParaLectura(vigentes,actor);
        return new Contenido(efectiva,copias,vigentes);
    }
    private static boolean corresponde(Derivada d,ResultadosMaterialIa.Seleccion actual){
        return d.operacion()!=null&&d.archivo()!=null&&d.versionSeleccion()>=1
                &&d.operacion().equals(actual.operacion())&&d.archivo().equals(actual.archivo())&&d.versionSeleccion()==actual.revision();
    }
    private Map<UUID,ResultadosMaterialIa.Seleccion> indiceSelecciones(List<ResultadosMaterialIa.Seleccion> lista,List<UUID> originales){
        if(lista==null||lista.size()!=originales.size()||new HashSet<>(originales).size()!=originales.size())throw desactualizada();
        var indice=new HashMap<UUID,ResultadosMaterialIa.Seleccion>();
        for(var s:lista)if(s==null||s.original()==null||s.revision()<0||s.operacion()!=null&&s.revision()<1||(s.operacion()==null)!=(s.archivo()==null)
                ||indice.put(s.original(),s)!=null)throw desactualizada();
        if(!indice.keySet().equals(new HashSet<>(originales)))throw desactualizada();return indice;
    }
    private void bloquearSeleccionesNuevas(Contenido c,Actor actor,boolean lectura){
        var ids=c.datos().fotos().stream().map(Foto::original).toList();
        var actuales=lectura?derivados.bloquearSeleccionesParaLectura(ids,actor):derivados.bloquearSelecciones(ids,actor);
        if(!mismoConjunto(c.selecciones(),actuales))throw desactualizada();exigirSeleccionesNuevas(c,actor,lectura);
    }
    private void exigirSeleccionesNuevas(Contenido c,Actor actor,boolean lectura){
        var indice=indiceSelecciones(c.selecciones(),c.datos().fotos().stream().map(Foto::original).toList());
        for(var foto:c.datos().fotos()){
            var actual=indice.get(foto.original());
            if(foto.derivada()==null?actual.operacion()!=null:!corresponde(foto.derivada(),actual))throw desactualizada();
        }
        if(lectura)derivados.exigirMismasSeleccionesParaLectura(c.selecciones(),actor);
        else derivados.exigirMismasSelecciones(c.selecciones(),actor);
    }
    private static boolean mismoConjunto(List<ResultadosMaterialIa.Seleccion> a,List<ResultadosMaterialIa.Seleccion> b){
        return a!=null&&b!=null&&a.size()==b.size()&&new HashSet<>(a).equals(new HashSet<>(b));
    }
    private void protegerSiEmitida(PiezaComercial p,String finalidad,Contenido c,Actor actor,ContextoSesion sesion){
        if("DIFUSION_AUTORIZADA".equals(finalidad))
            derivados.protegerPorPieza(p.getReferencia(),p.getVersionContenido(),c.selecciones(),actor,sesion);
    }
    private EvaluacionTecnicaFoto.Copia copiaDerivada(Derivada d,Actor actor){
        return evaluador.evaluar(archivos.fotoDerivada(d.operacion(),d.archivo(),actor));
    }
    private String procesoDerivada(com.controllocal.service.materialia.ResultadosMaterialIa.Archivo a){
        // La firma profesional no recibe identidad del procesador/modelo. El snapshot conserva su huella.
        return procesoIaFijo(a)+"/"+EvaluacionTecnicaFoto.VERSION;
    }
    private String procesoIaFijo(com.controllocal.service.materialia.ResultadosMaterialIa.Archivo a){
        return "IA-"+Idempotencia.huellaExacta(a.proceso()+"\u0000"+a.versionProceso()+"\u0000"+a.huella());
    }
    private com.controllocal.domain.comercial.Captacion leerEncargo(long id,Actor actor){
        contextos.exigirAgente(actor);var c=acceso.leer(id,actor);acceso.exigirResponsable(c,actor);return c;
    }
    private PiezaComercial leer(UUID ref,Actor actor){
        contextos.exigirAgente(actor);if(ref==null)throw new ReglaNegocioException("Indica la referencia de la pieza.");
        var p=piezas.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),ref).orElseThrow(()->new NoEncontradoException("Pieza"));
        leerEncargo(p.getEncargo(),actor);inmuebles.exigirLecturaHistorica(p.getOrganizacionId(),p.getPropiedad(),actor);return p;
    }
    private void exigirActual(PiezaComercial p,Actor actor){if(!causas(p,actor).isEmpty())throw desactualizada();}
    private void exigirFotosActuales(PiezaComercial p,Actor actor){
        inmuebles.exigirLecturaHistorica(p.getOrganizacionId(),p.getPropiedad(),actor);
        exigirMetadatosFotos(contenido(p),actor);
    }
    private void exigirMetadatosFotos(Contenido c,Actor actor){
        for(int i=0;i<c.copias().size();i++){
            var copia=c.copias().get(i);var foto=c.datos().fotos().get(i);
            if(foto.derivada()==null){if(!copia.equals(fotos.consultar(copia.referencia(),actor)))throw desactualizada();}
            else{
                var a=derivados.exigirArchivo(foto.derivada().operacion(),foto.derivada().archivo(),actor);
                if(!copia.referencia().equals(a.archivo())||!copia.original().equals(a.original())
                        ||!copia.versionProceso().startsWith(procesoIaFijo(a)+"/")||!copia.originalRegistradoEn().equals(a.originalRegistradoEn()))throw desactualizada();
            }
        }
    }
    private void diagnosticarSelecciones(Contenido c,Actor actor){
        // Diagnóstico de estado, nunca concede componer ni toma el lock de una elección.
        var actual=derivados.consultarSelecciones(c.datos().fotos().stream().map(Foto::original).toList(),actor);
        if(!mismoConjunto(c.selecciones(),actual))throw desactualizada();
    }
    private List<String> causas(PiezaComercial p,Actor actor){
        var causas=new ArrayList<String>();var ultima=piezas.findFirstByOrganizacionIdAndFamiliaOrderByVersionContenidoDesc(actor.idOrganizacion(),p.getFamilia()).orElseThrow();
        if(ultima.getVersionContenido()!=p.getVersionContenido())causas.add("NUEVA_VERSION");
        try{
            if(!inmuebles.revisarHistorica(leerEncargo(p.getEncargo(),actor),p.getPropiedad(),actor).pertenece()){
                causas.add("ORIGEN_MODIFICADO");return causas;
            }
            var c=contenido(p);var base=proyecciones.proyectar(c.datos(),p.getReferencia(),p.getVersionContenido(),"REVISION_PRIVADA",actor);
            var g=seleccion.observar(p.getPropiedad(),actor);
            if(!g.versionObservada().equals(c.datos().versionGaleria())||!p.getHuellaOrigen().equals(huella(base,c)))causas.add("ORIGEN_MODIFICADO");
            exigirMetadatosFotos(c,actor);
            if(p.archivo("DIFUSION_AUTORIZADA")==null)diagnosticarSelecciones(c,actor);
        }catch(ConflictoException|NoEncontradoException|ReglaNegocioException e){causas.add("ORIGEN_MODIFICADO");}
        return causas;
    }
    private Estado estado(PiezaComercial p,Actor actor){
        var causas=causas(p,actor);return new Estado(p.getReferencia(),p.getVersionContenido(),p.getEncargo(),"BROX-FICHA",p.getPlantillaVersion(),
                Formato.valueOf(p.getFormato()),p.archivo("DIFUSION_AUTORIZADA")!=null?"SALIDA_AUTORIZADA_REGISTRADA":
                p.archivo("REVISION_PRIVADA")!=null?"ARCHIVO_PRIVADO_REGISTRADO":"PREPARADA",!causas.isEmpty(),causas,
                p.getHuellaOrigen(),p.archivo("REVISION_PRIVADA"),p.archivo("DIFUSION_AUTORIZADA"));
    }
    private Contenido contenido(PiezaComercial p){return json.comoObjeto(p.getSolicitud(),Contenido.class);}
    private String huella(PlantillaComercialV08.Proyeccion p,Contenido c){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("fuente",p.huellaOrigen(),"contenido",c)));}
    private void version(PiezaComercial p,long esperada){if(p.getVersionContenido()!=esperada)throw desactualizada();}
    private void bloquearFamilia(PiezaComercial p,Actor actor){bloquear("FAMILIA-PIEZA",actor.idOrganizacion()+"\u0000"+p.getFamilia());}
    private String iniciar(String cap,Object datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia){
        if(datos==null||procedencia==null)throw new ReglaNegocioException("Indica los datos y su procedencia.");
        if(OBTENER.equals(cap))contextos.iniciar(actor,sesion);else contextos.iniciarConLecturaProtegida(actor,sesion);
        String normalizada=Idempotencia.exigirClave(clave);bloquear("COMANDO",actor.idOrganizacion()+"\u0000"+normalizada);
        return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("datos",datos,"procedencia",procedencia)));
    }
    private void bloquear(String tipo,String clave){String h=Idempotencia.huellaExacta(("COMANDO".equals(tipo)?"BROX-COMANDO":"BROX-C6-"+tipo)+"\u0000"+clave);locks.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));}
    private Optional<Map<String,Object>> recibo(String cap,String clave,String hash,Actor actor){
        return comandos.buscar(actor,clave,cap,hash).map(c->json.comoMapa(c.getResultado()));
    }
    private Optional<Map<String,Object>> resultado(String cap,String clave,Actor actor){
        contextos.exigirAgente(actor);return locks.findByOrganizacionIdAndClaveIdempotencia(actor.idOrganizacion(),Idempotencia.exigirClave(clave))
                .filter(c->cap.equals(c.getTipoComando())&&Objects.equals(c.getIdPersonaRol(),actor.idRolOperativo()))
                .map(c->json.comoMapa(c.getResultado()));
    }
    private Archivo restaurarArchivo(Map<String,Object> r,Actor actor){
        var a=json.comoObjeto(json.objetoCanonico(r),Archivo.class);var p=leer(a.pieza(),actor);exigirFotosActuales(p,actor);
        if("DIFUSION_AUTORIZADA".equals(a.finalidad())){exigirActual(p,actor);publicaciones.exigirPublicabilidadDePieza(p.getEncargo(),p.getPropiedad(),contenido(p).datos().canal(),actor);}
        return a;
    }
    private Archivo archivo(PiezaComercial p,String finalidad){
        boolean pdf=Formato.PDF_A4.name().equals(p.getFormato());return new Archivo(p.archivo(finalidad),p.getReferencia(),p.getVersionContenido(),finalidad,
                ("REVISION_PRIVADA".equals(finalidad)?"Vista previa · ":"")+"ficha."+(pdf?"pdf":"png"),pdf?"application/pdf":"image/png",p.huella(finalidad));
    }
    private void registrar(String cap,String clave,String hash,PiezaComercial p,Object resultado,Actor actor,Procedencia procedencia){
        comandos.registrar(actor,clave,cap,hash,"PIEZA_COMERCIAL",p.getId(),procedencia,json.objetoCanonico(resultado));
        eventos.saveAndFlush(procedencia.sinPeticion().sellar(EventoDominio.de(actor.idOrganizacion(),"PIEZA_COMERCIAL_REGISTRADA", "PIEZA_COMERCIAL",p.getId(),actor.idRolOperativo(),procedencia.canal()))
                .con(json.objetoCanonico(Map.of("actuacion",cap,"pieza",p.getReferencia(),"versionContenido",p.getVersionContenido()))));
    }
    private static ConflictoException desactualizada(){return new ConflictoException("La pieza cambió o está desactualizada; revisa sus fuentes y regenera.");}
}
