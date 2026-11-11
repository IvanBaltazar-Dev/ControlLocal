package com.controllocal.service.materialia;

import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.materialia.*;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository.Operacion;
import com.controllocal.persistence.materialia.DerivadasMaterialIaRepository.*;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository.Intento;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.FuentesMaterialIa.Snapshot;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import com.controllocal.service.materialia.MaterialIaServiceImpl.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.materialia.ProveedorMaterialIa.Salida;
import com.controllocal.service.planes.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import com.controllocal.service.materialia.ResultadosMaterialIa.*;

/** Registro144 y clasificador; sin Fuente, I/O, Cuota ni Uso en su grafo. */
@Service
public class DerivadasMaterialIaService implements ClasificadorCargaAlmacenamientoB0 {
    public static final String TIPO="CARGA_DERIVADA_MATERIAL_IA",FINALIDAD="CONSERVAR_DERIVADA_PRIVADA";
    public record PlanPrevision(UsoAlmacenamientoB0.Recurso recurso,String perfil,String versionPerfil) { }
    public record PlanSalida(long organizacion,UUID job,UUID operacionIo,UsoAlmacenamientoB0.Recurso recursoCuota,
            String fuente,String extension,String contentType,String huella,long bytes,boolean vinculoExistente) { }
    public record ObjetoVerificado(int numeroIntento,String namespaceHash,String clavePrivada,String huella,long bytes) { }
    public record Recibido(Operacion job,Solicitud solicitud,DetalleSalida detalle,Binario binario){}
    @FunctionalInterface public interface Escritura { ObjetoVerificado guardar(PlanSalida plan); }
    public record Binario(String clavePrivada,String namespaceHash,String huella,long bytes,String contentType) { }
    public record Lectura(long organizacion,UUID operacion,String huellaAutorizacion,Solicitud solicitud,Snapshot snapshot,
            UUID archivo,UUID original,long propiedad,String proceso,String versionProceso,Binario binario) { }
    public record PlanRetirada(UUID id,long organizacion,UUID operacion,UUID archivo,long revision,String causa,String estado,Binario binario) { }
    private record Origen(Operacion job,Solicitud solicitud,Snapshot snapshot,DetalleSalida salida,long propiedad,long foto) { }
    private record EpisodioCausa(UUID operacion,long revision,Instant temporalDesde,Instant venceEn) { }
    private record DatosRetencion(String estado,boolean guardada,long revision,String causa,Long eventoGeneracion,
            Instant generadaEn,String procedenciaFecha,Instant temporalDesde,Instant venceEn) { }
    private final RegistroMaterialIaRepository jobs;private final DerivadasMaterialIaRepository derivadas;
    private final ContextoPresupuestoB0 contextos;private final AutoridadDePropiedad propiedades;private final Documentos json;
    public DerivadasMaterialIaService(RegistroMaterialIaRepository jobs,DerivadasMaterialIaRepository derivadas,
            ContextoPresupuestoB0 contextos,AutoridadDePropiedad propiedades,Documentos json){
        this.jobs=jobs;this.derivadas=derivadas;this.contextos=contextos;this.propiedades=propiedades;this.json=json;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bloquearOriginal(UUID original,Actor actor){escritura();foto(original,actor);bloquearO(actor.idOrganizacion(),original);foto(original,actor);}
    @Transactional(propagation=Propagation.MANDATORY)
    public PlanPrevision previsionar(Solicitud s,Snapshot f,Actor actor,ContextoSesion sid,String namespaceHash){
        escritura();exigirO(actor.idOrganizacion(),s.fotoOriginal());exigirJ(actor.idOrganizacion(),s.operacion());
        var j=cargar(actor.idOrganizacion(),s.operacion());AutoridadMaterialIa.actor(j,actor);var base=base(j,actor);
        if(!Set.of("INTENCION","RESERVADA").contains(j.estado()))throw new ConflictoException("La previsión pertenece a la admisión del mismo job.");
        if(!json.objetoCanonico(s).equals(j.solicitud())||!json.objetoCanonico(f).equals(j.snapshot()))throw new ConflictoException("La previsión exige la intención exacta.");
        var existente=derivadas.slot(j.organizacion(),j.id());
        if(existente.isEmpty()){
            derivadas.asegurarOriginal(j.organizacion(),foto(s.fotoOriginal(),actor));capacidad(s.fotoOriginal(),seleccion(j.organizacion(),s.fotoOriginal()).operacion(),actor.idOrganizacion(),1);
            if(namespaceHash==null||!namespaceHash.matches("[0-9a-f]{64}"))throw new ConflictoException("Falta el namespace real de esta previsión.");
            derivadas.reservarSlot(j.organizacion(),j.id(),s.fotoOriginal(),namespaceHash);
        }else if(!existente.get().original().equals(s.fotoOriginal())||!"RESERVADA".equals(existente.get().estado())||!existente.get().namespaceHash().equals(namespaceHash))throw new ConflictoException("El lugar de esta generación ya no está reservado.");
        return new PlanPrevision(recurso(base),j.perfil(),j.versionPerfil());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void exigirReserva(UUID job,Actor actor,String namespace){
        var j=cargar(actor.idOrganizacion(),job);var b=base(j,actor);AutoridadMaterialIa.actor(j,actor);
        exigirO(j.organizacion(),b.solicitud().fotoOriginal());exigirJ(j.organizacion(),job);
        var slot=slot(j.organizacion(),job);if(!"RESERVADA".equals(slot.estado())||!Objects.equals(slot.namespaceHash(),namespace))throw new ConflictoException("La generación no conserva su lugar y namespace reservados.");
    }
    @Transactional(readOnly=true) public void exigirNamespaceParaInvocar(UUID job,Actor actor,String namespace){
        var j=cargar(actor.idOrganizacion(),job);AutoridadMaterialIa.actor(j,actor);base(j,actor);var slot=slot(j.organizacion(),job);
        if(!"RECLAMADA".equals(j.estado())||!"RESERVADA".equals(slot.estado())||!Objects.equals(slot.namespaceHash(),namespace))throw new ConflictoException("El almacén real cambió después de la previsión; no se inicia la inferencia.");
    }
    @Transactional
    public void registrarGeneracion(long org,UUID job){
        var j=cargar(org,job);var s=solicitud(j);bloquearO(org,s.fotoOriginal());bloquearJ(org,job);
        var slot=slot(org,job);if(slot.generadaEn()!=null)return;
        if(Set.of("NO_ALMACENADA","RETIRADA").contains(slot.estado()))throw new ConflictoException("La generación ya fue resuelta sin archivo.");
        var g=derivadas.generacion(org,job).orElseThrow(()->new ConflictoException("Falta la observación Core MEDIDA."));
        actualizar(slot,"GENERADA",slot.guardada(),g.evento(),g.observadaEn(),g.observadaEn(),g.observadaEn().plus(Duration.ofDays(7)),"GENERACION_OBSERVADA_CORE");
    }
    /** Captura técnica exacta: no consulta cuenta/SID y no concede lectura o transferencia. */
    @Transactional public void recibir(long org,UUID job,String clave,Salida salida,Escritura escritura){
        var j=cargar(org,job);var o=basePrivada(j);bloquearO(org,o.solicitud().fotoOriginal());bloquearJ(org,job);j=cargar(org,job);o=basePrivada(j);
        if(!j.claveExterna().equals(clave)||j.medicion()==null||o.salida()==null||!Set.of("MEDIDA","CONCILIADA","CONTROLADA","FALLO_RESULTADO","DISPONIBLE","OBSERVADA","RECHAZADA").contains(j.estado()))throw new ConflictoException("Falta el resultado causado del mismo claim.");
        MaterialIaServiceImpl.validarSalida(o.solicitud(),salida);
        if(!json.objetoCanonico(MaterialIaServiceImpl.detalle(salida)).equals(j.salida())||!json.objetoCanonico(salida.medicion()).equals(j.medicion()))throw new ConflictoException("La recepción exige el resultado y medición originales.");
        var s=slot(org,job);if(Set.of("RETIRADA_PENDIENTE","RETIRADA","NO_ALMACENADA").contains(s.estado()))throw new ConflictoException("La recepción ya está retirada.");
        var previa=jobs.custodia(org,job);if(previa.isPresent()&&"RETIRADA".equals(previa.get().estado()))throw new ConflictoException("La recepción no admite otra escritura.");
        var plan=new PlanSalida(org,job,operacionIo(org,job),recurso(o),fuente(o),"image/png".equals(salida.contentType())?"png":"jpg",salida.contentType(),o.salida().huellaFoto(),salida.foto().length,previa.isPresent());
        var b=Objects.requireNonNull(escritura.guardar(plan));var c=jobs.custodia(org,job).orElseThrow();var i=derivadas.intento(org,c.operacionIo(),c.numero()).orElseThrow();
        validarIntentoRecepcion(o,c,i);if(!b.namespaceHash().equals(i.almacenHuella())||!b.huella().equals(i.huella())||b.bytes()!=i.bytes())throw new ConflictoException("La recepción no confirmó el mismo objeto.");
        jobs.verificarCustodia(c);
    }
    public RegistroMaterialIaRepository.Custodia custodiar(long org,UUID job,String clave,UUID io,int numero){return jobs.custodiar(org,job,clave,io,numero);}
    @Transactional(readOnly=true) public RecepcionComprobada pruebaRecepcion(long org,UUID job,String clave){
        var j=cargar(org,job);var c=jobs.custodia(org,job).orElseThrow();
        if(!j.claveExterna().equals(clave)||j.medicion()==null||"RETIRADA".equals(c.estado())||!c.operacionIo().equals(operacionIo(org,job)))throw new ConflictoException("La prueba no conserva el claim de recepción.");
        return new RecepcionComprobada(job,c.operacionIo(),c.numero(),clave,c.archivo());
    }
    @Transactional(readOnly=true) public Recibido recibido(long org,UUID job,String clave){
        var j=cargar(org,job);var o=basePrivada(j);var c=jobs.custodia(org,job).orElseThrow(()->new NoEncontradoException("Recepción"));
        if(!j.claveExterna().equals(clave)||"RETIRADA".equals(c.estado()))throw new NoEncontradoException("Recepción");
        var i=derivadas.intento(org,c.operacionIo(),c.numero()).orElseThrow();
        if(!c.operacionIo().equals(operacionIo(org,job))||!fuente(o).equals(i.fuente())||!o.salida().huellaFoto().equals(i.huella()))throw new ConflictoException("La recepción conserva otro intento.");
        return new Recibido(j,o.solicitud(),o.salida(),new Binario(i.clave(),i.almacenHuella(),i.huella(),i.bytes(),i.contentType()));
    }
    private void validarIntentoRecepcion(Origen o,RegistroMaterialIaRepository.Custodia c,Intento i){
        if(!c.operacionIo().equals(operacionIo(i.organizacion(),o.job().id()))||!fuente(o).equals(i.fuente())||!o.salida().huellaFoto().equals(i.huella())||!o.salida().contentType().equals(i.contentType())||i.escrituraIncierta()||!Set.of("ESCRITO_VERIFICADO","VINCULADO").contains(i.estado()))throw new ConflictoException("La recepción no conserva escritura verificada.");
    }
    public Salida reconstruir(Recibido r,byte[] bytes){return new Salida(r.detalle().texto(),r.detalle().contentType(),bytes,r.detalle().proceso(),r.detalle().versionProceso(),json.comoObjeto(r.job().medicion(),com.controllocal.service.presupuesto.PresupuestoIaService.ResultadoFacturable.class));}
    @Transactional
    public Guardado guardar(Solicitud s,Snapshot f,Salida r,Control qc,Actor actor,ContextoSesion sid,Escritura escritura){
        contextos.iniciarArchivo(actor,sid);bloquearOriginal(s.fotoOriginal(),actor);bloquearJ(actor.idOrganizacion(),s.operacion());
        var o=origen(s.operacion(),actor);AutoridadMaterialIa.actor(o.job(),actor);mismaFuente(o,s,f);MaterialIaServiceImpl.validarSalida(s,r);
        var estado=slot(actor.idOrganizacion(),s.operacion());if(!Set.of("GENERADA","DISPONIBLE").contains(estado.estado()))throw new ConflictoException("La salida no conserva un lugar admitido.");
        if(!json.objetoCanonico(MaterialIaServiceImpl.detalle(r)).equals(json.objetoCanonico(o.salida()))||!json.objetoCanonico(qc).equals(json.objetoCanonico(json.comoObjeto(o.job().control(),Control.class))))throw new ConflictoException("La derivada pertenece a la salida y control originales.");
        var anterior=derivadas.obtener(actor.idOrganizacion(),s.operacion());if(anterior.isPresent())validarVinculo(o,anterior.get());
        else if(!Set.of("CONTROLADA","FALLO_RESULTADO").contains(o.job().estado()))throw new ConflictoException("El job no admite otra conservación.");
        UUID io=operacionIo(actor.idOrganizacion(),s.operacion());
        var plan=new PlanSalida(actor.idOrganizacion(),s.operacion(),io,recurso(o),fuente(o),"image/png".equals(r.contentType())?"png":"jpg",r.contentType(),o.salida().huellaFoto(),r.foto().length,anterior.isPresent());
        var objeto=Objects.requireNonNull(escritura.guardar(plan));var actual=origen(s.operacion(),actor);mismaFuente(actual,s,f);mismoOrigen(o,actual);
        Intento intento=derivadas.intento(actor.idOrganizacion(),io,objeto.numeroIntento()).orElseThrow(()->new NoEncontradoException("Intento de derivada"));validarIntento(actual,intento);
        if(!Objects.equals(objeto.namespaceHash(),intento.almacenHuella())||!Objects.equals(objeto.clavePrivada(),intento.clave())||!Objects.equals(objeto.huella(),intento.huella())||objeto.bytes()!=intento.bytes()||objeto.bytes()!=plan.bytes())throw new ConflictoException("El objeto no coincide con la conservación verificada.");
        Vinculo v;if(anterior.isPresent()){v=anterior.get();if(!v.operacionIo().equals(io)||v.numeroIo()!=intento.numero())throw new ConflictoException("El job ya conserva otro archivo.");}
        else{var custodia=jobs.custodia(actor.idOrganizacion(),s.operacion()).orElseThrow(()->new ConflictoException("Falta recepción verificada antes de promover."));
            if(!"VERIFICADA".equals(custodia.estado())||!custodia.operacionIo().equals(io)||custodia.numero()!=intento.numero())throw new ConflictoException("La promoción exige el mismo objeto recibido.");
            v=new Vinculo(actor.idOrganizacion(),s.operacion(),o.propiedad(),o.foto(),s.fotoOriginal(),custodia.archivo(),io,intento.numero());derivadas.insertar(v);}
        if(!"DISPONIBLE".equals(estado.estado()))actualizar(estado,"DISPONIBLE",estado.guardada(),estado.eventoGeneracion(),estado.generadaEn(),estado.temporalDesde(),estado.venceEn(),"ARCHIVO_VERIFICADO");
        return new Guardado(v.archivo(),intento.huella());
    }
    @Transactional public Lectura autorizar(UUID job,UUID archivo,Actor actor,ContextoSesion sid){contextos.iniciarArchivo(actor,sid);return autorizarContenido(job,archivo,actor);}
    @Transactional public Lectura autorizarArchivo(UUID job,UUID archivo,Actor actor){
        var o=origen(job,actor);if(!"DISPONIBLE".equals(o.job().estado()))throw new NoEncontradoException("Derivada registrada");
        var v=derivadas.obtener(actor.idOrganizacion(),job).orElseThrow(()->new NoEncontradoException("Derivada"));validarVinculo(o,v);
        var e=json.comoObjeto(o.job().resultado(),Entregado.class);MaterialIaServiceImpl.validarEntrega(o.solicitud(),o.salida(),e);
        if(!v.archivo().equals(archivo)||!v.archivo().equals(e.archivo()))throw new NoEncontradoException("Derivada de este job");
        var i=derivadas.intento(actor.idOrganizacion(),v.operacionIo(),v.numeroIo()).orElseThrow(()->new NoEncontradoException("Binario de derivada"));validarIntento(o,i);return lectura(o,v,i);
    }
    private Lectura autorizarContenido(UUID job,UUID archivo,Actor actor){var l=autorizarArchivo(job,archivo,actor);if(!disponible(slot(actor.idOrganizacion(),job)))throw new NoEncontradoException("Derivada disponible");return l;}
    @Transactional public void revalidar(Lectura previa,Actor actor,ContextoSesion sid){if(!previa.equals(autorizar(previa.operacion(),previa.archivo(),actor,sid)))throw new ConflictoException("La autorización de lectura cambió.");}
    @Transactional public Archivo archivo(Lectura l){return new Archivo(l.operacion(),l.original(),l.archivo(),l.binario().huella(),l.binario().contentType(),l.proceso(),l.versionProceso(),derivadas.fechaOriginal(l.organizacion(),l.propiedad(),l.original()).orElseThrow(()->new ReglaNegocioException("Falta la fecha real del original.")));}
    public void exigirPeticion(Lectura l,Solicitud s,Snapshot f){if(!json.objetoCanonico(l.solicitud()).equals(json.objetoCanonico(s))||!json.objetoCanonico(l.snapshot()).equals(json.objetoCanonico(f)))throw new ConflictoException("La derivada corresponde a otra fuente.");}
    @Transactional public Seleccion seleccionActual(UUID original,Actor actor,ContextoSesion sid){contextos.iniciarArchivo(actor,sid);foto(original,actor);return seleccion(actor.idOrganizacion(),original);}
    @Transactional(readOnly=true) public List<Seleccion> consultarSelecciones(List<UUID> originales,Actor actor){var r=new ArrayList<Seleccion>();for(UUID o:originales(originales)){foto(o,actor);r.add(seleccion(actor.idOrganizacion(),o));}return List.copyOf(r);}
    @Transactional(propagation=Propagation.MANDATORY) public List<Seleccion> bloquearSelecciones(List<UUID> originales,Actor actor){
        return bloquearSelecciones(originales,actor,false);
    }
    /** Snapshot de consulta: comparte lectores, excluye selección/retirada hasta commit. */
    @Transactional(propagation=Propagation.MANDATORY) public List<Seleccion> bloquearSeleccionesParaLectura(List<UUID> originales,Actor actor){
        return bloquearSelecciones(originales,actor,true);
    }
    private List<Seleccion> bloquearSelecciones(List<UUID> originales,Actor actor,boolean lectura){
        escritura();var ids=originales(originales);
        for(UUID o:ids){
            if(lectura){foto(o,actor);jobs.bloquearCompartido(llave("BROX-MATERIAL-IA-ORIGINAL",actor.idOrganizacion(),o));foto(o,actor);}
            else bloquearOriginal(o,actor);
        }
        var actuales=ids.stream().map(o->seleccion(actor.idOrganizacion(),o)).toList();
        actuales.stream().map(Seleccion::operacion).filter(Objects::nonNull).sorted().forEach(j->{
            if(lectura)jobs.bloquearCompartido(llave("BROX-MATERIAL-IA-LOCK",actor.idOrganizacion(),j));
            else bloquearJ(actor.idOrganizacion(),j);
        });return actuales;
    }
    @Transactional(propagation=Propagation.MANDATORY) public void exigirMismasSelecciones(List<Seleccion> anteriores,Actor actor){
        exigirMismasSelecciones(anteriores,actor,false);
    }
    @Transactional(propagation=Propagation.MANDATORY) public void exigirMismasSeleccionesParaLectura(List<Seleccion> anteriores,Actor actor){
        exigirMismasSelecciones(anteriores,actor,true);
    }
    private void exigirMismasSelecciones(List<Seleccion> anteriores,Actor actor,boolean lectura){
        escritura();for(var s:List.copyOf(anteriores)){
            if(lectura)exigirLockParaLectura(llave("BROX-MATERIAL-IA-ORIGINAL",actor.idOrganizacion(),s.original()));
            else exigirO(actor.idOrganizacion(),s.original());
            foto(s.original(),actor);
            if(!s.equals(seleccion(actor.idOrganizacion(),s.original())))throw new ConflictoException("La selección del original cambió.");
            if(s.operacion()!=null){
                if(lectura)exigirLockParaLectura(llave("BROX-MATERIAL-IA-LOCK",actor.idOrganizacion(),s.operacion()));
                else exigirJ(actor.idOrganizacion(),s.operacion());
                autorizarContenido(s.operacion(),s.archivo(),actor);
            }
        }
    }
    private void exigirLockParaLectura(long clave){
        if(!derivadas.poseeLockParaLectura(clave))throw new IllegalStateException("La consulta debe conservar su bloqueo Core hasta commit.");
    }
    @Transactional public Seleccion seleccionar(UUID original,UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid,Consumer<Lectura> comprobar){
        if(original==null||esperada<0||(job==null)!=(archivo==null))throw new ReglaNegocioException("Identifica original, selección y revisión.");
        contextos.iniciarArchivo(actor,sid);foto(original,actor);String key=clave(clave);bloquearK(actor.idOrganizacion(),key);bloquearO(actor.idOrganizacion(),original);var f=foto(original,actor);
        derivadas.asegurarOriginal(actor.idOrganizacion(),f);var actual=seleccion(actor.idOrganizacion(),original);
        var js=new TreeSet<UUID>();if(actual.operacion()!=null)js.add(actual.operacion());if(job!=null)js.add(job);for(UUID j:js)bloquearJ(actor.idOrganizacion(),j);
        String h=peticion("SELECCION",actor,original,job,archivo,esperada);var repetida=repetir(actor,key,h,Seleccion.class);if(repetida.isPresent())return repetida.get();
        if(actual.revision()!=esperada)throw new ConflictoException("La selección ya tiene otra revisión.");
        if(job!=null){var l=autorizarContenido(job,archivo,actor);if(!original.equals(l.original()))throw new ConflictoException("La mejora pertenece a otro original.");comprobar.accept(l);}
        if(Objects.equals(actual.operacion(),job)&&Objects.equals(actual.archivo(),archivo)){guardarGestion(actor,key,h,actual);return actual;}
        capacidad(original,job,actor.idOrganizacion(),0);
        if(derivadas.cambiarSeleccion(actor.idOrganizacion(),original,esperada,job,archivo)!=1)throw new ConflictoException("La selección cambió.");
        Instant ahora=derivadas.ahora();var nueva=new Seleccion(original,esperada+1,job,archivo);
        derivadas.eventoSeleccion(actor.idOrganizacion(),original,nueva.revision(),job,archivo,actual.operacion(),actual.archivo(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),sid.identidad(),sid.revisionContexto(),ahora);
        if(actual.operacion()!=null){var anterior=slot(actor.idOrganizacion(),actual.operacion());if(!anterior.guardada()&&!anterior.protegida()){
            actualizar(anterior,anterior.estado(),false,anterior.eventoGeneracion(),anterior.generadaEn(),ahora,ahora.plus(Duration.ofDays(7)),"CAMBIO_SELECCION");}}
        guardarGestion(actor,key,h,nueva);return nueva;
    }
    @Transactional public Version guardarAlternativa(UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid,Consumer<Lectura> comprobar){
        contextos.iniciarArchivo(actor,sid);String key=clave(clave);var b=base(cargar(actor.idOrganizacion(),job),actor);
        bloquearK(actor.idOrganizacion(),key);bloquearO(actor.idOrganizacion(),b.solicitud().fotoOriginal());foto(b.solicitud().fotoOriginal(),actor);bloquearJ(actor.idOrganizacion(),job);
        String h=peticion("GUARDAR_ALTERNATIVA",actor,job,archivo,esperada);var repetida=repetir(actor,key,h,Version.class);if(repetida.isPresent())return repetida.get();
        var l=autorizarContenido(job,archivo,actor);var s=slot(actor.idOrganizacion(),job);if(s.revision()!=esperada)throw new ConflictoException("La versión ya tiene otra revisión de retención.");comprobar.accept(l);
        if(!s.guardada())actualizar(s,s.estado(),true,s.eventoGeneracion(),s.generadaEn(),s.temporalDesde(),s.venceEn(),"GUARDADA_EXPRESA");
        Version v=version(job,actor);guardarGestion(actor,key,h,v);return v;
    }
    @Transactional(readOnly=true) public Version version(UUID job,Actor actor){base(cargar(actor.idOrganizacion(),job),actor);var s=slot(actor.idOrganizacion(),job);var v=derivadas.obtenerGestion(actor.idOrganizacion(),job).orElseThrow(()->new NoEncontradoException("Artefacto"));return version(s,v.archivo());}
    @Transactional(readOnly=true) public boolean promovida(UUID job,Actor actor){base(cargar(actor.idOrganizacion(),job),actor);return derivadas.obtener(actor.idOrganizacion(),job).isPresent();}
    @Transactional public List<Version> versiones(UUID original,Actor actor,ContextoSesion sid){contextos.iniciarArchivo(actor,sid);foto(original,actor);return versionesInternas(original,actor);}
    private List<Version> versionesInternas(UUID original,Actor actor){return derivadas.slots(actor.idOrganizacion(),original).stream().map(s->derivadas.obtenerGestion(actor.idOrganizacion(),s.job()).map(v->version(s,v.archivo()))).flatMap(Optional::stream).toList();}
    @Transactional(readOnly=true) public List<GrupoCaducidad> grupos(Actor actor,Predicate<Lectura> permitida){
        Instant ahora=derivadas.ahora();var grupos=new ArrayList<GrupoCaducidad>();
        for(UUID original:derivadas.originalesConSlots(actor.idOrganizacion())){
            try{foto(original,actor);}catch(AccesoNoAutorizadoException|NoEncontradoException denegada){continue;}
            var versiones=versionesInternas(original,actor).stream().filter(v->v.venceEn()!=null&&v.disponible()&&!ahora.isBefore(v.venceEn().minus(Duration.ofHours(24)))&&ahora.isBefore(v.venceEn())).filter(v->{
                Lectura lectura;
                // Llamada interna: capturar la denegación propia antes de salir por una frontera @Transactional.
                try{lectura=autorizarArchivo(v.operacion(),v.archivo(),actor);}
                catch(AccesoNoAutorizadoException|NoEncontradoException|CapacidadDeshabilitadaException denegada){return false;}
                return permitida.test(lectura);
            }).toList();
            if(versiones.isEmpty())continue;Instant vence=versiones.stream().map(Version::venceEn).min(Comparator.naturalOrder()).orElseThrow();
            var o=derivadas.seleccion(actor.idOrganizacion(),original).orElseThrow();String huella=huellaCausa(versiones.stream().map(v->new EpisodioCausa(v.operacion(),v.revision(),v.temporalDesde(),v.venceEn())).toList());grupos.add(new GrupoCaducidad(original,o.revisionMaterial(),huella,vence.minus(Duration.ofHours(24)),vence,versiones));
        }
        return List.copyOf(grupos);
    }
    @Transactional(propagation=Propagation.MANDATORY) public boolean registrarCausaCaducidad(long org,GrupoCaducidad grupo){
        escritura();if(org<=0||grupo==null||grupo.versiones().isEmpty())throw new ReglaNegocioException("Identifica la causa de conservación.");
        var refs=grupo.versiones().stream().map(v->new EpisodioCausa(v.operacion(),v.revision(),v.temporalDesde(),v.venceEn())).toList();
        if(refs.stream().map(EpisodioCausa::operacion).distinct().count()!=refs.size())throw new ConflictoException("La causa repite una versión de la misma operación.");
        if(!huellaCausa(refs).equals(grupo.huellaCausa()))throw new ConflictoException("La causa no conserva sus episodios exactos.");
        Instant ahora=derivadas.ahora();var miembros=new ArrayList<MiembroCausa>();
        for(var v:grupo.versiones()){
            var opt=derivadas.slot(org,v.operacion());if(opt.isEmpty())return false;var s=opt.get();
            if(!s.original().equals(grupo.original())||!s.original().equals(v.original())||s.revision()!=v.revision()||!Objects.equals(s.temporalDesde(),v.temporalDesde())||!Objects.equals(s.venceEn(),v.venceEn())
                    ||s.guardada()||s.protegida()||derivadas.seleccionada(org,s.job())||!"DISPONIBLE".equals(s.estado())
                    ||s.venceEn()==null||ahora.isBefore(s.venceEn().minus(Duration.ofHours(24)))||!ahora.isBefore(s.venceEn()))return false;
            long evento=derivadas.eventoVersion(s).orElseThrow(()->new ConflictoException("Falta la versión durable de retención."));
            miembros.add(new MiembroCausa(s.job(),s.revision(),evento,s.temporalDesde(),s.venceEn()));
        }
        derivadas.causa(org,grupo.original(),grupo.huellaCausa(),miembros);return true;
    }
    @Transactional(readOnly=true) public EstadoCausaCaducidad estadoCausaCaducidad(long org,UUID original,String huella){
        var miembros=causaIntegra(org,original,huella);if(miembros.isEmpty())return EstadoCausaCaducidad.NO_EVALUABLE;
        boolean vigente=false;for(var m:miembros.get()){
            var opt=derivadas.slot(org,m.job());if(opt.isEmpty()||!opt.get().original().equals(original)||opt.get().revision()<m.revision())return EstadoCausaCaducidad.NO_EVALUABLE;
            var s=opt.get();if(Objects.equals(s.temporalDesde(),m.temporalDesde())&&Objects.equals(s.venceEn(),m.venceEn())
                    &&!s.guardada()&&!s.protegida()&&!derivadas.seleccionada(org,s.job())&&!Set.of("RETIRADA","NO_ALMACENADA").contains(s.estado()))vigente=true;
        }
        return vigente?EstadoCausaCaducidad.VIGENTE:EstadoCausaCaducidad.FINALIZADA;
    }
    @Transactional(readOnly=true) public EstadoCausaCaducidad estadoCausaCaducidad(long org,UUID original,String anterior,String actual){
        var antes=causaIntegra(org,original,anterior);var despues=causaIntegra(org,original,actual);
        if(antes.isEmpty()||despues.isEmpty())return EstadoCausaCaducidad.NO_EVALUABLE;
        var estado=estadoCausaCaducidad(org,original,actual);if(estado!=EstadoCausaCaducidad.VIGENTE)return estado;
        var episodios=new HashSet<String>();for(var m:antes.get())episodios.add(claveEpisodio(m));
        for(var m:despues.get())if(!episodios.contains(claveEpisodio(m))&&episodioActual(org,original,m))return EstadoCausaCaducidad.SUSTITUIDA;
        return EstadoCausaCaducidad.VIGENTE;
    }
    private boolean episodioActual(long org,UUID original,MiembroCausa m){
        var actual=derivadas.slot(org,m.job());if(actual.isEmpty())return false;var s=actual.get();
        return s.original().equals(original)&&s.revision()>=m.revision()&&Objects.equals(s.temporalDesde(),m.temporalDesde())&&Objects.equals(s.venceEn(),m.venceEn())
                &&!s.guardada()&&!s.protegida()&&!derivadas.seleccionada(org,s.job())&&!Set.of("RETIRADA","NO_ALMACENADA").contains(s.estado());
    }
    private Optional<List<MiembroCausa>> causaIntegra(long org,UUID original,String huella){
        if(org<=0||original==null||huella==null||!huella.matches("[0-9a-f]{64}"))return Optional.empty();
        var miembros=derivadas.causa(org,original,huella);if(miembros.isEmpty())return Optional.empty();
        var refs=miembros.stream().map(m->new EpisodioCausa(m.job(),m.revision(),m.temporalDesde(),m.venceEn())).toList();if(!huellaCausa(refs).equals(huella))return Optional.empty();
        for(var m:miembros){
            var ev=derivadas.evento(org,m.eventoRetencion());if(ev.isEmpty()||!ev.get().job().equals(m.job())||!Idempotencia.huellaExacta(ev.get().datos()).equals(ev.get().huella()))return Optional.empty();
            try{var d=json.comoObjeto(ev.get().datos(),DatosRetencion.class);if(d.revision()!=m.revision()||!Objects.equals(d.temporalDesde(),m.temporalDesde())||!Objects.equals(d.venceEn(),m.venceEn()))return Optional.empty();}
            catch(RuntimeException incompleta){return Optional.empty();}
        }
        return Optional.of(miembros);
    }
    private String huellaCausa(List<EpisodioCausa> refs){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("miembros",refs.stream().sorted(Comparator.comparing(e->e.operacion().toString())).toList())));}
    private static String claveEpisodio(MiembroCausa m){return m.job()+"|"+m.temporalDesde()+"|"+m.venceEn();}
    @Transactional(readOnly=true) public LoteConservacion candidatosConservacion(String namespaceHash,CursorConservacion desde,int limite){
        if(namespaceHash==null||!namespaceHash.matches("[0-9a-f]{64}")||limite<=0||desde!=null&&(desde.organizacion()<=0||desde.operacion()==null))throw new ReglaNegocioException("Identifica namespace, cursor y lote de conservación.");
        var candidatos=derivadas.candidatos(namespaceHash,desde==null?null:desde.organizacion(),desde==null?null:desde.operacion(),limite).stream().map(c->new CandidatoConservacion(c.organizacion(),c.job(),c.revision())).toList();
        var ultimo=candidatos.isEmpty()?desde:new CursorConservacion(candidatos.getLast().organizacion(),candidatos.getLast().operacion());return new LoteConservacion(candidatos,ultimo,candidatos.size()<limite);
    }
    @Transactional(propagation=Propagation.MANDATORY) public void proteger(UUID pieza,long versionPieza,List<Seleccion> snapshot,Actor actor,ContextoSesion sid){
        escritura();contextos.iniciarArchivo(actor,sid);exigirMismasSelecciones(snapshot,actor);
        var p=derivadas.pieza(actor.idOrganizacion(),pieza).orElseThrow(()->new NoEncontradoException("Pieza"));
        if(p.version()!=versionPieza||p.archivoDifusion()==null)throw new ConflictoException("La protección exige emisión DIFUSION_AUTORIZADA real.");
        if(snapshot.isEmpty())return;
        var solicitud=json.comoMapa(p.solicitud());Object ss=solicitud.get("selecciones");Object datos=solicitud.get("datos");
        if(!(ss instanceof List<?> guardadas)||!(datos instanceof Map<?,?> preparacion)||!(preparacion.get("fotos") instanceof List<?> fotos))throw new ConflictoException("La pieza no conserva su snapshot de selección.");
        var registradas=guardadas.stream().map(x->json.comoObjeto(json.objetoCanonico(x),Seleccion.class)).toList();
        if(registradas.size()!=snapshot.size()||!new HashSet<>(registradas).equals(new HashSet<>(snapshot)))throw new ConflictoException("La protección exige todas las selecciones del snapshot de la pieza.");
        for(var s:snapshot){
            if(fotos.stream().noneMatch(x->fotoPieza(x,s)))throw new ConflictoException("La pieza no conserva esta versión seleccionada.");
            if(s.operacion()==null)continue;var l=autorizarContenido(s.operacion(),s.archivo(),actor);if(l.propiedad()!=p.propiedad())throw new ConflictoException("La pieza pertenece a otro inmueble.");
            derivadas.proteger(actor.idOrganizacion(),s.operacion(),s.archivo(),pieza,versionPieza,s.revision());derivadas.tocarOriginal(actor.idOrganizacion(),s.original());}
    }
    @Transactional public PlanRetirada descartarTemporal(UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid){
        return retiradaExpresa(job,archivo,esperada,clave,actor,sid,false);
    }
    @Transactional public PlanRetirada retirarAlternativa(UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid){
        return retiradaExpresa(job,archivo,esperada,clave,actor,sid,true);
    }
    private PlanRetirada retiradaExpresa(UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid,boolean alternativa){
        contextos.iniciarArchivo(actor,sid);String key=clave(clave);var b=base(cargar(actor.idOrganizacion(),job),actor);
        bloquearK(actor.idOrganizacion(),key);bloquearO(actor.idOrganizacion(),b.solicitud().fotoOriginal());foto(b.solicitud().fotoOriginal(),actor);bloquearJ(actor.idOrganizacion(),job);String h=peticion(alternativa?"RETIRAR_ALTERNATIVA":"DESCARTAR_TEMPORAL",actor,job,archivo,esperada);
        var repetida=repetir(actor,key,h,Retirada.class);if(repetida.isPresent())return planRetirada(actor.idOrganizacion(),job);
        var l=derivadas.obtener(actor.idOrganizacion(),job).isPresent()?autorizarArchivo(job,archivo,actor):lecturaGestion(cargar(actor.idOrganizacion(),job));
        if(!l.archivo().equals(archivo))throw new NoEncontradoException("Derivada de este job");var s=slot(actor.idOrganizacion(),job);if(s.revision()!=esperada)throw new ConflictoException("La versión ya tiene otra revisión de retención.");
        if(s.guardada()!=alternativa)throw new ConflictoException(alternativa?"Identifica una alternativa guardada.":"Una guardada requiere retirar alternativa expresamente.");
        var plan=iniciarRetiro(l,s,alternativa?"RETIRADA_ALTERNATIVA":"DESCARTE_EXPLICITO");guardarGestion(actor,key,h,new Retirada(plan.id(),job,archivo,plan.revision()));return plan;
    }
    @Transactional public Optional<PlanRetirada> caducidad(long org,UUID job){
        var j=cargar(org,job);var s=solicitud(j);bloquearO(org,s.fotoOriginal());bloquearJ(org,job);
        var retiro=derivadas.retiro(org,job);if(retiro.isPresent())return Optional.of(planRetirada(org,job));
        var slot=slot(org,job);if(!Set.of("GENERADA","DISPONIBLE").contains(slot.estado())||slot.guardada()||slot.protegida()||derivadas.seleccionada(org,job)||slot.venceEn()==null||derivadas.ahora().isBefore(slot.venceEn()))return Optional.empty();
        var v=derivadas.obtenerGestion(org,job).orElseThrow();var i=derivadas.intento(org,v.operacionIo(),v.numeroIo()).orElseThrow();
        return Optional.of(iniciarRetiro(new Lectura(org,job,"",s,snapshot(j),v.archivo(),v.original(),v.propiedad(),"","",new Binario(i.clave(),i.almacenHuella(),i.huella(),i.bytes(),i.contentType())),slot,"CADUCIDAD"));
    }
    @Transactional public PlanRetirada planRetirada(long org,UUID job){
        var r=derivadas.retiro(org,job).orElseThrow(()->new NoEncontradoException("Retirada"));var v=derivadas.obtenerGestion(org,job).orElseThrow();var i=derivadas.intento(org,v.operacionIo(),v.numeroIo()).orElseThrow();
        return new PlanRetirada(r.id(),org,job,r.archivo(),r.revision(),r.causa(),r.estado(),new Binario(i.clave(),i.almacenHuella(),i.huella(),i.bytes(),i.contentType()));
    }
    @Transactional public void confirmarAusencia(PlanRetirada plan,Consumer<AusenciaComprobada> liberar,CierreControlRetirado cierre){
        var j=cargar(plan.organizacion(),plan.operacion());bloquearO(j.organizacion(),solicitud(j).fotoOriginal());bloquearJ(j.organizacion(),j.id());
        var r=derivadas.retiro(j.organizacion(),j.id()).orElseThrow();if(!r.id().equals(plan.id())||!r.archivo().equals(plan.archivo())||r.revision()!=plan.revision())throw new ConflictoException("La retirada cambió.");
        if("LIBERADA".equals(r.estado()))return;prescindible(slot(j.organizacion(),j.id()),"RETIRADA_ALTERNATIVA".equals(r.causa()));
        String prueba="MATERIAL-IA-RETIRO:"+r.id();if("INTENCION".equals(r.estado())){if(derivadas.ausencia(r,prueba,derivadas.ahora())!=1)throw new ConflictoException("La retirada cambió.");r=derivadas.retiro(j.organizacion(),j.id()).orElseThrow();}
        liberar.accept(new AusenciaComprobada(r.id(),r.job(),r.archivo(),r.revision(),r.identidadObjeto(),r.bytes(),r.huella(),r.referenciaComprobacion()));
        if(derivadas.liberar(r)!=1)throw new ConflictoException("La liberación de esta retirada cambió.");
        var slot=slot(j.organizacion(),j.id());actualizar(slot,"RETIRADA",false,slot.eventoGeneracion(),slot.generadaEn(),slot.temporalDesde(),slot.venceEn(),"AUSENCIA_VERIFICADA");
        jobs.retirarCustodia(j.organizacion(),j.id());
        resolverControlRetirado(j.organizacion(),j.id(),cierre);
    }
    @Transactional public void resolverSinArchivo(long org,UUID job,Consumer<NoAlmacenadoComprobado> liberar){
        var j=cargar(org,job);var s=solicitud(j);bloquearO(org,s.fotoOriginal());bloquearJ(org,job);var slot=slot(org,job);
        if("NO_ALMACENADA".equals(slot.estado()))return;
        if(jobs.custodia(org,job).isPresent())return;
        if(!resolucionSinArchivo(j))return;
        sinWriter(org,job);var anterior=derivadas.ultimoEvento(org,job,"NO_ALMACENADO_COMPROBADO");Evento e;
        if(anterior.isPresent())e=anterior.get();else{String datos=json.objetoCanonico(Map.of("job",job,"causa",j.estado(),"sinIntento",true));e=derivadas.evento(org,job,slot.revision(),"NO_ALMACENADO_COMPROBADO",datos,Idempotencia.huellaExacta(datos));}
        liberar.accept(new NoAlmacenadoComprobado(job,"MATERIAL-IA-NO-ALMACENADO:"+e.id(),e.huella(),e.registradaEn()));
        actualizar(slot,"NO_ALMACENADA",false,slot.eventoGeneracion(),slot.generadaEn(),slot.temporalDesde(),slot.venceEn(),"NO_ALMACENADO_COMPROBADO");
    }
    @Override public boolean atiende(UsoAlmacenamientoB0.Recurso r){return r!=null&&TIPO.equals(r.tipo());}
    @Override public Clasificacion clasificarPrevision(PrevisionIa p,Actor actor){var b=base(cargar(actor.idOrganizacion(),p.job()),actor);AutoridadMaterialIa.actor(b.job(),actor);exigirO(actor.idOrganizacion(),b.solicitud().fotoOriginal());exigirJ(actor.idOrganizacion(),p.job());
        var slot=slot(actor.idOrganizacion(),p.job());if(!"RESERVADA".equals(slot.estado())||!slot.namespaceHash().equals(p.namespaceHash())||!recurso(b).equals(p.recurso())||!b.job().perfil().equals(p.perfil())||!b.job().versionPerfil().equals(p.versionPerfil()))throw new ConflictoException("La previsión no corresponde a la intención.");return new Clasificacion(CategoriaAlmacenamiento.DERIVADA_IA,Necesidad.ORDINARIA,p.recurso().huellaOrigen());}
    @Override public Clasificacion clasificar(UsoAlmacenamientoB0.Solicitud s,Actor actor){
        if(actor==null||s.recurso()==null||s.recurso().referencia()==null||!s.recurso().referencia().startsWith(actor.idOrganizacion()+":"))throw new AccesoNoAutorizadoException();
        var j=cargarPorRecurso(s.recurso());if(j.organizacion()!=actor.idOrganizacion())throw new AccesoNoAutorizadoException();var o=origen(j.id(),actor);exigirO(actor.idOrganizacion(),o.solicitud().fotoOriginal());exigirJ(actor.idOrganizacion(),o.job().id());
        if(!recurso(o).equals(s.recurso())||!o.salida().huellaFoto().equals(s.huellaContenido()))throw new ConflictoException("La carga no pertenece a esta salida.");return new Clasificacion(CategoriaAlmacenamiento.DERIVADA_IA,Necesidad.ORDINARIA,s.recurso().huellaOrigen());}
    @Override public Clasificacion clasificarRecepcion(UsoAlmacenamientoB0.Solicitud s,RecepcionComprobada p){
        var j=cargarPorRecurso(s.recurso());var o=basePrivada(j);exigirO(j.organizacion(),o.solicitud().fotoOriginal());exigirJ(j.organizacion(),j.id());
        var c=jobs.custodia(j.organizacion(),j.id()).orElseThrow(()->new ConflictoException("Falta custodia durable."));
        var i=derivadas.intento(j.organizacion(),c.operacionIo(),c.numero()).orElseThrow();
        if(j.medicion()==null||!j.id().equals(p.job())||!j.claveExterna().equals(p.claveIntento())||!c.archivo().equals(p.archivo())||!c.operacionIo().equals(p.operacionIo())||c.numero()!=p.numero()||"RETIRADA".equals(c.estado())
                ||!c.operacionIo().equals(operacionIo(j.organizacion(),j.id()))||!fuente(o).equals(i.fuente())||!i.huella().equals(s.huellaContenido())||i.bytes()!=s.bytes()||!i.almacenHuella().equals(s.namespaceHash())||!(i.almacenHuella()+":"+i.clave()).equals(s.identidadObjetoConocida()))throw new ConflictoException("La recepción no corresponde a esta previsión e intento.");
        var slot=slot(j.organizacion(),j.id());if(Set.of("RETIRADA_PENDIENTE","RETIRADA","NO_ALMACENADA").contains(slot.estado()))throw new ConflictoException("La recepción está retirada.");
        return new Clasificacion(CategoriaAlmacenamiento.DERIVADA_IA,Necesidad.ORDINARIA,s.recurso().huellaOrigen());
    }
    @Override public void exigirRetiro(UsoAlmacenamientoB0.Recurso recurso,AusenciaComprobada p){
        var j=cargarPorRecurso(recurso);var s=solicitud(j);exigirO(j.organizacion(),s.fotoOriginal());exigirJ(j.organizacion(),j.id());
        var r=derivadas.retiro(j.organizacion(),j.id()).orElseThrow(()->new ConflictoException("Falta intención de retirada."));var slot=slot(j.organizacion(),j.id());prescindible(slot,"RETIRADA_ALTERNATIVA".equals(r.causa()));
        if(!"RETIRADA_PENDIENTE".equals(slot.estado())||slot.revision()!=r.revision())throw new ConflictoException("La ausencia exige el fence vigente de esta retirada.");
        if(!r.id().equals(p.retiro())||!r.job().equals(p.job())||!r.archivo().equals(p.archivo())||r.revision()!=p.revision()||!"AUSENCIA_COMPROBADA".equals(r.estado())||!r.identidadObjeto().equals(p.identidadObjeto())||r.bytes()!=p.bytes()||!r.huella().equals(p.huellaContenido())||!Objects.equals(r.referenciaComprobacion(),p.referenciaComprobacion()))throw new ConflictoException("La ausencia no corresponde al retiro acreditado.");
    }
    @Override public void exigirNoAlmacenado(UsoAlmacenamientoB0.Recurso recurso,NoAlmacenadoComprobado p){
        var j=cargarPorRecurso(recurso);exigirO(j.organizacion(),solicitud(j).fotoOriginal());exigirJ(j.organizacion(),j.id());
        if(!j.id().equals(p.job())||!resolucionSinArchivo(j))throw new ConflictoException("No hay resolución terminal sin archivo.");sinWriter(j.organizacion(),j.id());
        var e=derivadas.ultimoEvento(j.organizacion(),j.id(),"NO_ALMACENADO_COMPROBADO").orElseThrow();
        if(!p.referenciaEvidencia().equals("MATERIAL-IA-NO-ALMACENADO:"+e.id())||!p.huellaEvidencia().equals(e.huella())||!p.comprobadaEn().equals(e.registradaEn())||!Idempotencia.huellaExacta(e.datos()).equals(e.huella()))throw new ConflictoException("Falta la prueba durable de no almacenamiento.");
    }
    private Operacion cargarPorRecurso(UsoAlmacenamientoB0.Recurso r){
        if(!atiende(r)||!FINALIDAD.equals(r.finalidad()))throw new CapacidadDeshabilitadaException();String[] partes=r.referencia().split(":",2);if(partes.length!=2)throw new ConflictoException("El recurso debe conservar organización y job.");
        long org;try{org=Long.parseLong(partes[0]);}catch(RuntimeException e){throw new ConflictoException("Recurso inválido.");}var j=cargar(org,uuid(partes[1]));
        if(!recurso(basePrivada(j)).equals(r))throw new ConflictoException("El recurso no corresponde a la intención.");return j;
    }
    private PlanRetirada iniciarRetiro(Lectura l,Slot s,String causa){
        prescindible(s,"RETIRADA_ALTERNATIVA".equals(causa));if(!Set.of("GENERADA","DISPONIBLE").contains(s.estado()))throw new ConflictoException("La versión no admite una retirada nueva.");
        UUID id=UUID.randomUUID();long revision=s.revision()+1;var r=new Retiro(id,l.organizacion(),l.operacion(),l.archivo(),revision,causa,"INTENCION",l.binario().namespaceHash()+":"+l.binario().clavePrivada(),l.binario().bytes(),l.binario().huella(),null,null);
        derivadas.iniciarRetiro(r);actualizar(s,"RETIRADA_PENDIENTE",s.guardada(),s.eventoGeneracion(),s.generadaEn(),s.temporalDesde(),s.venceEn(),causa);return planRetirada(l.organizacion(),l.operacion());
    }
    private void prescindible(Slot s,boolean alternativa){if(s.guardada()&&!alternativa||s.protegida()||derivadas.seleccionada(s.organizacion(),s.job()))throw new ConflictoException("La versión seleccionada o protegida no puede retirarse; guardar requiere retirada expresa de alternativa.");}
    private boolean resolucionSinArchivo(Operacion j){
        if(Set.of("CANCELADA","OBSERVADA","RECHAZADA").contains(j.estado()))return true;
        return "FALLO_RESULTADO".equals(j.estado())&&MaterialIaServiceImpl.salidaSinBinario(j,json)
                &&jobs.ultimoHecho(j.organizacion(),j.id(),"SALIDA_SIN_BINARIO").filter(j.salida()::equals).isPresent();
    }
    private void sinWriter(long org,UUID job){if(derivadas.obtener(org,job).isPresent()||derivadas.hayIntento(org,operacionIo(org,job)))throw new ConflictoException("Un intento de escritura conserva su recuperación y cuota.");}
    private Lectura lecturaGestion(Operacion j){var o=basePrivada(j);var v=derivadas.obtenerGestion(j.organizacion(),j.id()).orElseThrow(()->new NoEncontradoException("Recepción"));var i=derivadas.intento(j.organizacion(),v.operacionIo(),v.numeroIo()).orElseThrow();return lectura(o,v,i);}
    @FunctionalInterface public interface CierreControlRetirado { boolean cerrar(com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento prueba,com.controllocal.service.presupuesto.PresupuestoIaService.ResultadoFacturable generacion); }
    /** Sólo causa Core ya retirada; nunca presume ausencia de invocación de una etapa reclamada. */
    private void resolverControlRetirado(long org,UUID job,CierreControlRetirado cierre){
        var j=cargar(org,job);bloquearO(org,solicitud(j).fotoOriginal());bloquearJ(org,job);j=cargar(org,job);
        var c=jobs.custodia(org,job);if(c.isEmpty()||!"RETIRADA".equals(c.get().estado()))return;
        var plan=jobs.ultimoHecho(org,job,"QC_PLAN");if(plan.isEmpty()||jobs.ultimoHecho(org,job,"QC_CLAIM").isPresent()||jobs.ultimoHecho(org,job,"QC_RESULTADO").isPresent()||jobs.ultimoHecho(org,job,"QC_NO_INVOCADA").isPresent())return;
        var p=json.comoObjeto(plan.get(),PlanControl.class);String dato=json.objetoCanonico(Map.of("origen","CORE_CONSERVACION","causa","RECEPCION_RETIRADA","clave",p.clave()));
        long id=jobs.evidencia(j,"QC_RETIRO_COMPROBADO",dato);
        var prueba=new com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento(p.clave(),p.destino().proveedor(),p.clave(),"QC-RETIRO:"+id,Idempotencia.huellaExacta(dato),jobs.ahora());
        if(cierre.cerrar(prueba,json.comoObjeto(j.medicion(),com.controllocal.service.presupuesto.PresupuestoIaService.ResultadoFacturable.class)))jobs.evidencia(j,"QC_NO_INVOCADA",json.objetoCanonico(prueba));
    }
    private Version version(Slot s,UUID archivo){boolean temporal=!s.guardada()&&!s.protegida()&&!derivadas.seleccionada(s.organizacion(),s.job());boolean disponible=disponible(s);
        String estado="RETIRADA".equals(s.estado())?"RETIRADA_VERIFICADA":"RETIRADA_PENDIENTE".equals(s.estado())?"RETIRADA_PENDIENTE":disponible?"DISPONIBLE":temporal&&s.venceEn()!=null&&!derivadas.ahora().isBefore(s.venceEn())?"VENCIDA":"NO_DISPONIBLE";
        return new Version(s.job(),s.original(),archivo,s.revision(),s.generadaEn(),s.temporalDesde(),temporal?s.venceEn():null,s.guardada(),s.protegida(),disponible,estado);}
    private boolean disponible(Slot s){return "DISPONIBLE".equals(s.estado())&&(s.guardada()||s.protegida()||derivadas.seleccionada(s.organizacion(),s.job())||s.venceEn()!=null&&derivadas.ahora().isBefore(s.venceEn()));}
    private Seleccion seleccion(long org,UUID original){return derivadas.seleccion(org,original).map(s->new Seleccion(original,s.revision(),s.job(),s.archivo())).orElseGet(()->new Seleccion(original,0,null,null));}
    private void capacidad(UUID original,UUID seleccionada,long org,int adicional){long n=derivadas.slots(org,original).stream().filter(s->!Set.of("RETIRADA","NO_ALMACENADA").contains(s.estado())&&!s.guardada()&&!s.protegida()&&!Objects.equals(s.job(),seleccionada)).count();if(n+adicional>2)throw new ConflictoException("Gestiona qué versión guardar o descartar antes de superar dos temporales por original.");}
    private Foto foto(UUID original,Actor actor){
        if(original==null||actor==null||!actor.esAgente())throw new AccesoNoAutorizadoException();var f=derivadas.foto(actor.idOrganizacion(),original).orElseThrow(()->new NoEncontradoException("Fotografía original"));
        propiedades.buscarParaLectura(actor,f.propiedad()).orElseThrow(()->new NoEncontradoException("Fotografía original"));return f;
    }
    private Origen base(Operacion j,Actor a){if(j.organizacion()!=a.idOrganizacion())throw new NoEncontradoException("Derivada");var o=basePrivada(j);foto(o.solicitud().fotoOriginal(),a);return o;}
    private Origen basePrivada(Operacion j){
        var s=solicitud(j);var f=snapshot(j);if(!j.id().equals(s.operacion())||s.accion()!=Accion.MEJORAR_FOTO||!"MEJORAR_FOTO".equals(j.accion())||s.recurso()==null||!"PROPIEDAD".equals(s.recurso().tipo())||s.fotoOriginal()==null)throw new ConflictoException("El job no conserva una mejora de original.");
        long propiedad;try{propiedad=Long.parseLong(s.recurso().referencia());}catch(RuntimeException e){throw new ConflictoException("La fuente no identifica una propiedad.");}
        long foto=derivadas.fotoHistorica(j.organizacion(),propiedad,s.fotoOriginal()).orElseThrow(()->new NoEncontradoException("Fotografía original"));return new Origen(j,s,f,j.salida()==null?null:json.comoObjeto(j.salida(),DetalleSalida.class),propiedad,foto);
    }
    private Origen origen(UUID job,Actor actor){
        var o=base(cargar(actor.idOrganizacion(),job),actor);AutoridadMaterialIa.exigirEvidencia(o.snapshot());
        if(o.job().medicion()==null||o.salida()==null||o.job().control()==null||!Set.of("CONTROLADA","FALLO_RESULTADO","DISPONIBLE").contains(o.job().estado()))throw new ConflictoException("El job no conserva mejora controlada y conciliada.");
        var qc=json.comoObjeto(o.job().control(),Control.class);MaterialIaServiceImpl.validarControl(o.solicitud().accion(),qc);if(qc.decision()!=Decision.APROBADA)throw new ConflictoException("El control no permite entregar la derivada.");
        MaterialIaServiceImpl.exigirProceso(o.salida().proceso(),o.salida().versionProceso());if(o.salida().huellaFoto()==null||!o.salida().huellaFoto().matches("[0-9a-f]{64}"))throw new IllegalStateException("La derivada no conserva una huella verificable.");MaterialIaServiceImpl.validarContenido(o.solicitud().accion(),true,o.salida().contentType(),o.salida().texto());return o;
    }
    private UsoAlmacenamientoB0.Recurso recurso(Origen o){return new UsoAlmacenamientoB0.Recurso(TIPO,o.job().organizacion()+":"+o.job().id(),FINALIDAD,Idempotencia.huellaExacta(json.objetoCanonico(Map.of("organizacion",o.job().organizacion(),"job",o.job().id(),"snapshot",o.job().huellaSnapshot(),"original",o.solicitud().fotoOriginal()))));}
    private void validarVinculo(Origen o,Vinculo v){if(v.organizacion()!=o.job().organizacion()||!v.job().equals(o.job().id())||v.propiedad()!=o.propiedad()||v.foto()!=o.foto()||!v.original().equals(o.solicitud().fotoOriginal())||!v.operacionIo().equals(operacionIo(v.organizacion(),v.job())))throw new ConflictoException("El vínculo no conserva el linaje de este job.");}
    private void validarIntento(Origen o,Intento i){if(i.organizacion()!=o.job().organizacion()||!i.operacion().equals(operacionIo(i.organizacion(),o.job().id()))||!fuente(o).equals(i.fuente())||!o.salida().huellaFoto().equals(i.huella())||!o.salida().contentType().equals(i.contentType())||i.bytes()<=0||i.escrituraIncierta()||!Set.of("ESCRITO_VERIFICADO","VINCULADO").contains(i.estado())||i.almacenHuella()==null)throw new ConflictoException("El intento no conserva el binario verificado del job.");}
    private Lectura lectura(Origen o,Vinculo v,Intento i){return new Lectura(o.job().organizacion(),o.job().id(),Idempotencia.huellaExacta(json.objetoCanonico(o.job())),o.solicitud(),o.snapshot(),v.archivo(),v.original(),v.propiedad(),o.salida().proceso(),o.salida().versionProceso(),new Binario(i.clave(),i.almacenHuella(),i.huella(),i.bytes(),i.contentType()));}
    private void mismaFuente(Origen o,Solicitud s,Snapshot f){if(!json.objetoCanonico(o.solicitud()).equals(json.objetoCanonico(s))||!json.objetoCanonico(o.snapshot()).equals(json.objetoCanonico(f)))throw new ConflictoException("La conservación corresponde a otra solicitud o fuente.");}
    private static void mismoOrigen(Origen a,Origen b){if(!a.solicitud().equals(b.solicitud())||!a.snapshot().equals(b.snapshot())||!a.salida().equals(b.salida())||!a.job().control().equals(b.job().control())||a.foto()!=b.foto())throw new ConflictoException("El origen cambió durante la conservación.");}
    private void actualizar(Slot s,String estado,boolean guardada,Long evento,Instant generada,Instant desde,Instant vence,String causa){
        if(derivadas.actualizarSlot(s,estado,guardada,evento,generada,desde,vence,causa)!=1)throw new ConflictoException("La retención ya tiene otra revisión.");
        var datos=new LinkedHashMap<String,Object>();datos.put("estado",estado);datos.put("guardada",guardada);datos.put("revision",s.revision()+1);datos.put("causa",causa);datos.put("eventoGeneracion",evento);datos.put("generadaEn",generada);datos.put("procedenciaFecha",generada==null?null:"OBSERVADA_CORE");datos.put("temporalDesde",desde);datos.put("venceEn",vence);
        String d=json.objetoCanonico(datos);derivadas.evento(s.organizacion(),s.job(),s.revision()+1,causa,d,Idempotencia.huellaExacta(d));
    }
    private <T>Optional<T> repetir(Actor a,String key,String peticion,Class<T> tipo){var g=derivadas.gestion(a.idOrganizacion(),key);if(g.isEmpty())return Optional.empty();if(!g.get().peticion().equals(peticion)||g.get().persona()!=a.idPersona()||g.get().rol()!=a.idRolOperativo())throw new ConflictoException("La clave conserva otra gestión.");return Optional.of(json.comoObjeto(g.get().respuesta(),tipo));}
    private void guardarGestion(Actor a,String key,String peticion,Object respuesta){derivadas.gestionar(a.idOrganizacion(),key,peticion,json.objetoCanonico(respuesta),a.idPersona(),a.idRolOperativo());}
    private String peticion(String accion,Actor actor,Object...datos){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("accion",accion,"actor",actor,"datos",Arrays.asList(datos))));}
    private static String clave(String key){return Idempotencia.huellaExacta(Idempotencia.exigirClave(key));}
    private static List<UUID> originales(List<UUID> ids){if(ids==null||ids.stream().anyMatch(Objects::isNull))throw new ReglaNegocioException("Identifica las fotografías originales.");return ids.stream().distinct().sorted().toList();}
    private static boolean fotoPieza(Object x,Seleccion s){
        if(!(x instanceof Map<?,?> f)||!s.original().toString().equals(String.valueOf(f.get("original"))))return false;
        if(s.operacion()==null)return f.get("derivada")==null;
        if(!(f.get("derivada") instanceof Map<?,?> d))return false;
        return s.operacion().toString().equals(String.valueOf(d.get("operacion")))&&s.archivo().toString().equals(String.valueOf(d.get("archivo")))&&Long.toString(s.revision()).equals(String.valueOf(d.get("versionSeleccion")));
    }
    private Operacion cargar(long org,UUID job){return jobs.obtener(org,Objects.requireNonNull(job)).orElseThrow(()->new NoEncontradoException("Material IA"));}
    private Solicitud solicitud(Operacion j){return json.comoObjeto(j.solicitud(),Solicitud.class);}
    private Snapshot snapshot(Operacion j){if(!Idempotencia.huellaExacta(j.snapshot()).equals(j.huellaSnapshot()))throw new IllegalStateException("La fuente privada no conserva su integridad.");return json.comoObjeto(j.snapshot(),Snapshot.class);}
    private Slot slot(long org,UUID job){return derivadas.slot(org,job).orElseThrow(()->new ConflictoException("Falta el lugar admitido de esta generación."));}
    private static String fuente(Origen o){return "MATERIAL-IA:"+o.job().organizacion()+":"+o.job().id()+":"+o.job().huellaSnapshot()+":"+o.salida().huellaFoto();}
    public static UUID operacionIo(long org,UUID job){return UUID.nameUUIDFromBytes(("MATERIAL-IA-DERIVADA\u0000"+org+"\u0000"+job).getBytes(StandardCharsets.UTF_8));}
    private static long llave(String prefijo,long org,UUID id){return Long.parseUnsignedLong(Idempotencia.huellaExacta(prefijo+"\u0000"+org+"\u0000"+id).substring(0,16),16);}
    private void bloquearO(long org,UUID original){jobs.bloquear(llave("BROX-MATERIAL-IA-ORIGINAL",org,original));}
    private void bloquearJ(long org,UUID job){jobs.bloquear(llave("BROX-MATERIAL-IA-LOCK",org,job));}
    private void bloquearK(long org,String key){jobs.bloquear(Long.parseUnsignedLong(Idempotencia.huellaExacta("BROX-GESTION-MATERIAL\u0000"+org+"\u0000"+key).substring(0,16),16));}
    private void exigirO(long org,UUID original){escritura();if(!derivadas.poseeLock(llave("BROX-MATERIAL-IA-ORIGINAL",org,original)))throw new IllegalStateException("El original debe permanecer bloqueado antes del job/B0.");}
    private void exigirJ(long org,UUID job){escritura();if(!derivadas.poseeLock(llave("BROX-MATERIAL-IA-LOCK",org,job)))throw new IllegalStateException("El job debe permanecer bloqueado antes de B0.");}
    private static void escritura(){if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("La operación requiere la transacción de escritura Core previa.");}
    private static UUID uuid(String s){try{return UUID.fromString(s);}catch(RuntimeException e){throw new ReglaNegocioException("Identifica el job de material.");}}
}
