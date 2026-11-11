package com.controllocal.web.almacen;

import com.controllocal.service.Actor;
import com.controllocal.service.archivo.PropietarioBinario;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import com.controllocal.service.materialia.FuentesMaterialIa.Snapshot;
import com.controllocal.service.materialia.ProveedorMaterialIa.Salida;
import com.controllocal.service.materialia.ControlesMaterialIa.Control;
import com.controllocal.service.presupuesto.PresupuestoIaService;
import com.controllocal.service.materialia.DerivadasMaterialIaService.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** Usa el registro144, Almacen y único ledger B0. Inferencia y job privado siguen en su coordinador. */
@Component
public class ResultadosMaterialIaAlmacen implements ResultadosMaterialIa {
    private final DerivadasMaterialIaService registro;private final FuentesMaterialIa fuentes;
    private final SalidaBinariaPersistente salidas;private final CuotaArchivosB0 cuota;private final AlmacenDocumentos almacen;
    private final UsoAlmacenamientoB0 uso;
    private final PresupuestoIaService presupuesto;
    public ResultadosMaterialIaAlmacen(DerivadasMaterialIaService registro,FuentesMaterialIa fuentes,
            SalidaBinariaPersistente salidas,CuotaArchivosB0 cuota,AlmacenDocumentos almacen,UsoAlmacenamientoB0 uso,PresupuestoIaService presupuesto){
        this.registro=registro;this.fuentes=fuentes;this.salidas=salidas;this.cuota=cuota;this.almacen=almacen;this.uso=uso;this.presupuesto=presupuesto;
    }
    @Override public void bloquearOriginal(UUID original,Actor actor){padreWrite();registro.bloquearOriginal(original,actor);}
    @Override public RefInterna previsionar(Solicitud s,Snapshot f,Actor actor,ContextoSesion sid){
        padreWrite();fuentes.revalidar(s,f,actor);String namespace=almacen.identidadTecnica();var p=registro.previsionar(s,f,actor,sid,namespace);
        var prevista=uso.previsionar(new PrevisionIa(s.operacion(),p.recurso(),p.perfil(),p.versionPerfil(),namespace),actor,sid);
        if(!UsoAlmacenamientoB0.referenciaPrevision(actor.idOrganizacion(),s.operacion()).equals(prevista.referencia()))throw new ConflictoException("La previsión no corresponde al mismo job.");return prevista.referencia();
    }
    @Override public void exigirPrevisionAntesDeClaim(UUID job,Actor actor,ContextoSesion sid){padreWrite();registro.exigirReserva(job,actor,almacen.identidadTecnica());uso.exigirAntesDeClaim(UsoAlmacenamientoB0.referenciaPrevision(actor.idOrganizacion(),job),actor,sid);}
    @Override public LimiteRecepcion limiteRecepcionParaInvocar(UUID job,Actor actor){
        fueraSql();String namespace=almacen.identidadTecnica();
        registro.exigirNamespaceParaInvocar(job,actor,namespace);
        var ref=UsoAlmacenamientoB0.referenciaPrevision(actor.idOrganizacion(),job);
        var limite=uso.limiteRecepcion(ref);
        if(limite==null||!ref.equals(limite.reserva())||!Objects.equals(namespace,limite.namespaceHash())
                ||!Objects.equals(namespace,almacen.identidadTecnica()))
            throw new ConflictoException("La previsión no conserva el mismo recurso y namespace.");
        return limite;
    }
    @Override public void recibirResultado(long org,UUID job,String clave,Salida salida){
        fueraSql();registro.recibir(org,job,clave,salida,plan->{
            var ref=UsoAlmacenamientoB0.referenciaPrevision(org,job);
            var admision=cuota.admisionRecepcion(ref,plan.recursoCuota(),()->registro.pruebaRecepcion(org,job,clave));
            var consulta=salidas.recuperar(new PropietarioBinario.Organizacion(org),plan.operacionIo(),plan.fuente(),admision);
            if(consulta.estado()==SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION)throw new ConflictoException("La recepción conserva su intento incierto; no repite el PUT.");
            SalidaBinariaPersistente.Guardado g=consulta.guardado();
            if(g==null){
                if(plan.vinculoExistente())throw new ConflictoException("La recepción debe recuperar su objeto preexistente.");
                g=salidas.recibir(new PropietarioBinario.Organizacion(org),plan.operacionIo(),plan.fuente(),plan.extension(),plan.contentType(),salida.foto(),admision,
                        i->registro.custodiar(org,job,clave,i.operacion(),i.numeroIntento()));
            }
            cuota.confirmarRecepcion(ref,g);
            return new ObjetoVerificado(g.numeroIntento(),g.namespaceHash(),g.clave(),g.huella(),g.bytes());
        });
    }
    @Override public Salida recuperarRecibido(long org,UUID job,String clave){
        fueraSql();var r=registro.recibido(org,job,clave);var contenido=leerBinario(r.binario());
        var salida=registro.reconstruir(r,contenido.contenido());recibirResultado(org,job,clave,salida);return salida;
    }
    @Override public Guardado guardar(Solicitud s,Snapshot f,Salida r,Control qc,Actor actor,ContextoSesion sid){
        fueraSql();return registro.guardar(s,f,r,qc,actor,sid,plan->{
            fuentes.revalidar(s,f,actor);var c=new CuotaArchivosB0.Contexto(plan.recursoCuota(),actor,sid);
            var prevision=UsoAlmacenamientoB0.referenciaPrevision(plan.organizacion(),plan.job());
            var recuperada=salidas.recuperar(new PropietarioBinario.Organizacion(plan.organizacion()),plan.operacionIo(),plan.fuente(),cuota.admision(c,prevision));
            SalidaBinariaPersistente.Guardado g;
            if(recuperada.estado()==SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION)throw new ConflictoException("La derivada conserva su intento en verificación; no se repite la escritura.");
            if(recuperada.guardado()!=null)g=recuperada.guardado();else{
                if(plan.vinculoExistente())throw new ConflictoException("La derivada vinculada requiere recuperar su objeto original.");
                g=salidas.guardar(new PropietarioBinario.Organizacion(plan.organizacion()),plan.operacionIo(),plan.fuente(),plan.extension(),plan.contentType(),r.foto(),cuota.admision(c,prevision));
            }
            fuentes.revalidar(s,f,actor);
            if(!plan.operacionIo().equals(g.operacion())||!plan.huella().equals(g.huella())||plan.bytes()!=g.bytes())throw new ConflictoException("El binario conservado no coincide con esta derivada.");
            cuota.confirmar(c,g,prevision);return new ObjetoVerificado(g.numeroIntento(),g.namespaceHash(),g.clave(),g.huella(),g.bytes());
        });
    }
    @Override public void registrarGeneracion(long org,UUID job){fueraSql();registro.registrarGeneracion(org,job);}
    @Override public void resolverSinArchivo(long org,UUID job,String causa){fueraSql();registro.resolverSinArchivo(org,job,p->uso.cancelarPrevisionNoAlmacenada(UsoAlmacenamientoB0.referenciaPrevision(org,job),p));}
    @Override public void exigirLectura(Solicitud s,Snapshot f,UUID archivo,Actor actor,ContextoSesion sid){fueraSql();var l=registro.autorizar(s.operacion(),archivo,actor,sid);registro.exigirPeticion(l,s,f);vigente(l,actor,sid);}
    @Override public Contenido descargar(UUID job,UUID archivo,Actor actor,ContextoSesion sid){fueraSql();return contenido(job,archivo,actor,sid);}
    @Override public Contenido leerParaPieza(UUID job,UUID archivo,Actor actor,ContextoSesion sid){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La composición requiere su transacción Core activa.");return contenido(job,archivo,actor,sid);
    }
    private Contenido contenido(UUID job,UUID archivo,Actor actor,ContextoSesion sid){var l=registro.autorizar(job,archivo,actor,sid);vigente(l,actor,sid);var contenido=leer(l);vigente(l,actor,sid);namespace(l.binario());return contenido;}
    @Override public Archivo exigirArchivo(UUID job,UUID archivo,Actor actor){
        // Sólo metadata fija: una pieza ya emitida puede contener su copia aunque el derivado haya sido retirado.
        var l=registro.autorizarArchivo(job,archivo,actor);artefacto(l,actor);var actual=registro.autorizarArchivo(job,archivo,actor);
        if(!l.equals(actual))throw new ConflictoException("El archivo del resultado cambió.");artefacto(actual,actor);return registro.archivo(actual);
    }
    @Override public Seleccion seleccionActual(UUID original,Actor actor,ContextoSesion sid){fueraSql();var s=registro.seleccionActual(original,actor,sid);autorizarSeleccion(s,actor);return s;}
    @Override public Seleccion seleccionar(UUID original,UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid){
        fueraSql();return registro.seleccionar(original,job,archivo,esperada,clave,actor,sid,l->comprobar(l,actor,sid));
    }
    @Override public List<Version> versiones(UUID original,Actor actor,ContextoSesion sid){fueraSql();return registro.versiones(original,actor,sid).stream().filter(v->permitida(v,actor)).toList();}
    @Override public Version version(UUID job,Actor actor){var v=registro.version(job,actor);if(registro.promovida(job,actor))artefacto(registro.autorizarArchivo(job,v.archivo(),actor),actor);return v;}
    @Override public Version guardarAlternativa(UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid){
        fueraSql();return registro.guardarAlternativa(job,archivo,esperada,clave,actor,sid,l->comprobar(l,actor,sid));
    }
    @Override public Version descartarTemporal(UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid){
        fueraSql();var plan=registro.descartarTemporal(job,archivo,esperada,clave,actor,sid);retirar(plan);return registro.version(job,actor);
    }
    @Override public Version retirarAlternativa(UUID job,UUID archivo,long esperada,String clave,Actor actor,ContextoSesion sid){
        fueraSql();var plan=registro.retirarAlternativa(job,archivo,esperada,clave,actor,sid);retirar(plan);return registro.version(job,actor);
    }
    @Override public List<Seleccion> consultarSelecciones(List<UUID> originales,Actor actor){var r=registro.consultarSelecciones(originales,actor);r.forEach(s->autorizarSeleccion(s,actor));return r;}
    @Override public List<Seleccion> bloquearSelecciones(List<UUID> originales,Actor actor){padreWrite();var r=registro.bloquearSelecciones(originales,actor);r.forEach(s->autorizarSeleccion(s,actor));return r;}
    @Override public List<Seleccion> bloquearSeleccionesParaLectura(List<UUID> originales,Actor actor){padreWrite();var r=registro.bloquearSeleccionesParaLectura(originales,actor);r.forEach(s->autorizarSeleccion(s,actor));return r;}
    @Override public void exigirMismasSelecciones(List<Seleccion> snapshot,Actor actor){padreWrite();registro.exigirMismasSelecciones(snapshot,actor);snapshot.forEach(s->autorizarSeleccion(s,actor));}
    @Override public void exigirMismasSeleccionesParaLectura(List<Seleccion> snapshot,Actor actor){padreWrite();registro.exigirMismasSeleccionesParaLectura(snapshot,actor);snapshot.forEach(s->autorizarSeleccion(s,actor));}
    @Override public void protegerPorPieza(UUID pieza,long version,List<Seleccion> snapshot,Actor actor,ContextoSesion sid){
        padreWrite();exigirMismasSelecciones(snapshot,actor);registro.proteger(pieza,version,snapshot,actor,sid);exigirMismasSelecciones(snapshot,actor);
    }
    @Override public List<GrupoCaducidad> avisosDeCaducidad(Actor actor){return registro.grupos(actor,v->permitida(v,actor));}
    @Override public boolean registrarCausaCaducidad(long org,GrupoCaducidad grupo){padreWrite();return registro.registrarCausaCaducidad(org,grupo);}
    @Override public EstadoCausaCaducidad estadoCausaCaducidad(long org,UUID original,String huella){return registro.estadoCausaCaducidad(org,original,huella);}
    @Override public EstadoCausaCaducidad estadoCausaCaducidad(long org,UUID original,String anterior,String actual){return registro.estadoCausaCaducidad(org,original,anterior,actual);}
    @Override public void conciliarCaducidad(long org,UUID job){fueraSql();resolverSinArchivo(org,job,"CONSERVACION");registro.caducidad(org,job).ifPresent(this::retirar);}
    @Override public LoteConservacion candidatosConservacion(String namespaceHash,CursorConservacion desde,int limite){
        if(!Objects.equals(namespaceHash,almacen.identidadTecnica()))throw new ConflictoException("El barrido requiere el namespace real actual.");return registro.candidatosConservacion(namespaceHash,desde,limite);
    }
    private void retirar(PlanRetirada plan){
        if("LIBERADA".equals(plan.estado()))return;
        var b=plan.binario();namespace(b);
        Optional<AlmacenDocumentos.ArchivoDescargado> actual;
        try{actual=almacen.abrir(b.clavePrivada());}catch(RuntimeException fallo){throw new AlmacenException("La ausencia de la derivada todavía no está comprobada.",null);}
        namespace(b);
        if(actual.isPresent()){
            if(!coincide(b,actual.get()))throw new ConflictoException("El objeto no coincide con la retirada autorizada.");
            try{almacen.eliminar(b.clavePrivada());}catch(RuntimeException fallo){throw new AlmacenException("La retirada conserva su intención pendiente de verificación.",null);}
        }
        namespace(b);
        try{if(almacen.abrir(b.clavePrivada()).isPresent())throw new AlmacenException("La derivada todavía no está retirada.",null);}
        catch(RuntimeException fallo){throw new AlmacenException("La ausencia de la derivada todavía no está comprobada.",null);}
        namespace(b);
        registro.confirmarAusencia(plan,p->cuota.liberarRetiradaVerificada(UsoAlmacenamientoB0.referenciaPrevision(plan.organizacion(),plan.operacion()),p),(prueba,gen)->{
            var ref=new PresupuestoIaService.ReferenciaInterna(plan.organizacion(),plan.operacion());
            if(!presupuesto.acreditarEtapaNoReclamada(ref,ControlesMaterialIa.ETAPA_FINANCIERA,prueba))return false;
            presupuesto.conciliar(ref,gen);return true;
        });
    }
    private void comprobar(Lectura l,Actor actor,ContextoSesion sid){artefacto(l,actor);leer(l);artefacto(l,actor);registro.revalidar(l,actor,sid);}
    private void autorizarSeleccion(Seleccion s,Actor actor){if(s.operacion()!=null){var v=registro.version(s.operacion(),actor);if(!v.disponible()||!s.archivo().equals(v.archivo()))throw new NoEncontradoException("Derivada seleccionada disponible");artefacto(registro.autorizarArchivo(s.operacion(),s.archivo(),actor),actor);}}
    private boolean permitida(Version v,Actor actor){try{if(!registro.promovida(v.operacion(),actor))return true;return permitida(registro.autorizarArchivo(v.operacion(),v.archivo(),actor),actor);}
        catch(AccesoNoAutorizadoException|NoEncontradoException|CapacidadDeshabilitadaException denegada){return false;}}
    private boolean permitida(Lectura l,Actor actor){
        var estado=fuentes.evaluarLecturaArtefacto(l.solicitud(),l.snapshot(),actor);
        if(estado==null)throw new IllegalStateException("La fuente no conserva una evaluación de acceso al artefacto.");
        if(estado!=EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.PERMITIDO)return false;
        namespace(l.binario());return true;
    }
    private void artefacto(Lectura l,Actor actor){fuentes.exigirLecturaArtefacto(l.solicitud(),l.snapshot(),actor);namespace(l.binario());}
    private void vigente(Lectura l,Actor actor,ContextoSesion sid){artefacto(l,actor);registro.revalidar(l,actor,sid);}
    private Contenido leer(Lectura l){
        var b=leerBinario(l.binario());return new Contenido(b.contenido(),l.binario().contentType(),"material-"+l.archivo()+("image/png".equals(l.binario().contentType())?".png":".jpg"));
    }
    private AlmacenDocumentos.ArchivoDescargado leerBinario(Binario esperado){
        namespace(esperado);Optional<AlmacenDocumentos.ArchivoDescargado> leido;
        try{leido=almacen.abrir(esperado.clavePrivada());}catch(RuntimeException fallo){throw new AlmacenException("No se pudo verificar el contenido privado de la derivada.",null);}
        var b=leido.orElseThrow(()->new NoEncontradoException("Archivo de derivada"));namespace(esperado);
        if(!coincide(esperado,b))throw new ConflictoException("La derivada no conserva su contenido verificado.");return b;
    }
    private static boolean coincide(Binario b,AlmacenDocumentos.ArchivoDescargado a){return a.contenido()!=null&&a.contenido().length==b.bytes()&&b.huella().equals(EvaluacionTecnicaFoto.huella(a.contenido()))&&b.contentType().equals(a.contentType());}
    private void namespace(Binario b){if(!b.namespaceHash().equals(almacen.identidadTecnica()))throw new ConflictoException("El namespace actual no corresponde a esta derivada.");}
    private static void padreWrite(){if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("La operación exige la transacción de escritura Core previa.");}
    private static void fueraSql(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La conservación, gestión o descarga requiere una entrada sin TX SQL activa.");}
}
