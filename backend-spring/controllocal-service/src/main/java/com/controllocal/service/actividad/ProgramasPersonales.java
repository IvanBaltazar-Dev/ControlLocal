package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.ProgramaPersonalRepository.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.DerechosDeInformesC7;
import com.controllocal.service.soporte.*;
import com.controllocal.domain.auditoria.EventoDominio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

/** CAT-E41/RF-K047: mandato personal y ocurrencia técnica, separados de los estados comerciales. */
@Service
public class ProgramasPersonales {
    public enum Familia { REPORTE_PROGRAMADO,RESUMEN_PROGRAMADO,ALERTA_CONDICIONADA,RECORDATORIO_PROPUESTA }
    public enum Metrica { RECONTACTOS_VENCIDOS,OPORTUNIDADES_ABIERTAS,VISITAS_PENDIENTES,SOLICITUDES_SIN_CIERRE }
    public enum Comparador { MAYOR,MAYOR_O_IGUAL,IGUAL,MENOR_O_IGUAL,MENOR }
    public enum AccionEstado { PAUSAR,REANUDAR,CANCELAR }
    public record Recurso(String tipo,long id,Long version) { }
    public record Condicion(Metrica metrica,Comparador comparador,BigDecimal umbral) { }
    public record Configuracion(Familia familia,String finalidad,Recurso recurso,InformesC7.Consulta informe,Condicion condicion,
                                 Integer diasPeriodo,Instant proximaEn,Instant venceEn,long frecuenciaSegundos,int maxOcurrencias,
                                 int maxIntentos,String destinoPrivado) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String campo,com.fasterxml.jackson.databind.JsonNode valor){throw regla("El mandato contiene un campo no admitido.");}
    }
    public record DatosConfiguracion(UUID referencia,Long versionEsperada,Configuracion mandato) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String campo,com.fasterxml.jackson.databind.JsonNode valor){throw regla("La configuración contiene un campo no admitido.");}
    }
    public record CambioEstado(Long versionEsperada,AccionEstado accion,String motivo,Instant proximaEn) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String campo,com.fasterxml.jackson.databind.JsonNode valor){throw regla("El cambio contiene un campo no admitido.");}
    }
    public record Vista(UUID referencia,long version,long versionMandato,String estado,String causa,Configuracion mandato,int ocurrencias,Instant proximaEn) { }
    public record Ocurrencia(UUID referencia,UUID programa,long versionMandato,Instant programadaEn,String estado,int intentos,String causa,Instant finalizadaEn,Object resultado) { }
    public record Reclamo(Programa programa,Paso paso) { }
    public record FuenteSeguimiento(long id,long version,long idProspeccion) { }
    public record PruebaEntrega(List<Recurso> recursos,List<InteraccionService.ReferenciaVersion> interacciones,List<FuenteSeguimiento> seguimientos) {
        public PruebaEntrega {recursos=List.copyOf(recursos);interacciones=List.copyOf(interacciones);seguimientos=List.copyOf(seguimientos);}
    }
    public record Produccion(boolean comunicar,Object contenido,PruebaEntrega prueba) { }
    private final ProgramaPersonalRepository repo;
    private final ContextosDeActividad contexto;
    private final DerechosDeInformesC7 derechos;
    private final GeneradorProgramaPersonal generador;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository recibos;
    private final EventoDominioRepository eventos;
    private final Documentos json;
    private final long leaseSegundos;
    public ProgramasPersonales(ProgramaPersonalRepository r,ContextosDeActividad c,DerechosDeInformesC7 d,GeneradorProgramaPersonal g,
            ComandosIdempotentes co,ComandoIdempotenteRepository re,EventoDominioRepository ev,Documentos j,
            @Value("${controllocal.programas.lease-segundos:60}")long lease){repo=r;contexto=c;derechos=d;generador=g;comandos=co;recibos=re;eventos=ev;json=j;if(lease<=0)throw new IllegalArgumentException("Lease técnico positivo requerido.");leaseSegundos=lease;}
    @Transactional
    public Vista configurar(DatosConfiguracion d,Actor a,ContextoSesion sid,String clave,Procedencia proc){
        contexto.iniciar(a,sid);derechos.exigirPro(a,contexto.ahora());String tipo="CONFIGURAR_PROGRAMA_PERSONAL",h=huella(tipo,d,a,proc);
        var previo=previo(tipo,clave,h,a);if(previo.isPresent())return reproducir(previo.get(),a);
        if(d==null)throw regla("Mandato requerido.");validar(d.mandato());generador.verificar(d.mandato(),a);
        Programa p;
        if(d.referencia()==null){if(d.versionEsperada()!=null)throw regla("La creación no tiene una versión anterior.");
            var q=d.mandato();p=repo.crear(a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),json.objetoCanonico(q),q.proximaEn(),q.venceEn(),q.frecuenciaSegundos(),q.maxOcurrencias(),contexto.ahora());
        }else{
            p=propio(d.referencia(),a,true);version(d.versionEsperada(),p);
            if(d.mandato().maxOcurrencias()<p.ocurrencias())throw regla("El límite no puede borrar ocurrencias anteriores.");
            var q=d.mandato();if(!repo.configurar(p,json.objetoCanonico(q),q.proximaEn(),q.venceEn(),q.frecuenciaSegundos(),q.maxOcurrencias(),contexto.ahora()))throw conflicto();
            repo.detenerPendiente(p,"CANCELADA","MANDATO_SUSTITUIDO",contexto.ahora());p=propio(d.referencia(),a,false);
        }
        return conservar(p,a,tipo,clave,h,proc);
    }
    @Transactional
    public Vista cambiarEstado(UUID referencia,CambioEstado d,Actor a,ContextoSesion sid,String clave,Procedencia proc){
        contexto.iniciar(a,sid);derechos.exigirPro(a,contexto.ahora());String tipo="ESTADO_PROGRAMA_PERSONAL",h=huella(tipo,Arrays.asList(referencia,d),a,proc);
        var previo=previo(tipo,clave,h,a);if(previo.isPresent())return reproducir(previo.get(),a);
        var p=propio(referencia,a,true);version(d==null?null:d.versionEsperada(),p);
        if(d.accion()==null||vacio(d.motivo())||Set.of("CANCELADO","FINALIZADO").contains(p.estado()))throw regla("Indica una acción y motivo sobre un programa vigente.");
        String estado=switch(d.accion()){case PAUSAR->"PAUSADO";case REANUDAR->"ACTIVO";case CANCELAR->"CANCELADO";};
        Instant proxima=p.proxima();
        if(d.accion()==AccionEstado.REANUDAR){
            if(!"PAUSADO".equals(p.estado())||d.proximaEn()==null||!d.proximaEn().isAfter(contexto.ahora())||!d.proximaEn().isBefore(p.vence())||p.ocurrencias()>=p.maxOcurrencias())throw regla("Reanudar fija una próxima ocurrencia futura dentro del mandato; no recupera una avalancha anterior.");
            generador.verificar(configuracion(p),a);proxima=d.proximaEn();
        }
        if(!repo.estado(p,estado,d.motivo(),proxima,contexto.ahora()))throw conflicto();
        repo.detenerPendiente(p,d.accion()==AccionEstado.CANCELAR?"CANCELADA":"PAUSADA",d.motivo(),contexto.ahora());
        return conservar(propio(referencia,a,false),a,tipo,clave,h,proc);
    }
    @Transactional(readOnly=true)
    public Vista consultar(UUID ref,Actor a){return vista(propio(ref,a,false));}
    @Transactional(readOnly=true)
    public List<Vista> listar(int pagina,int tamano,Actor a){contexto.exigirAgente(a);paginacion(pagina,tamano);var lista=repo.listar(a.idOrganizacion(),a.idRolOperativo(),pagina,tamano).stream().map(this::vista).toList();contexto.exigirAgente(a);return lista;}
    @Transactional(readOnly=true)
    public List<Ocurrencia> ocurrencias(UUID ref,int pagina,int tamano,Actor a){var p=propio(ref,a,false);paginacion(pagina,tamano);var lista=repo.pasos(p,pagina,tamano).stream().map(s->ocurrencia(p,s,null)).toList();contexto.exigirAgente(a);return lista;}
    @Transactional(readOnly=true)
    public Ocurrencia resultado(UUID programa,UUID paso,Actor a){
        var p=propio(programa,a,false);var s=repo.paso(a.idOrganizacion(),paso).filter(x->x.programa()==p.id()).orElseThrow(()->new NoEncontradoException("Ocurrencia"));
        if(!Set.of("COMPLETADA","SIN_RESULTADO").contains(s.estado()))return ocurrencia(p,s,null);
        exigirMandato(p,s,a);generador.verificar(configuracion(p),a);
        if(s.prueba()==null)throw new AccesoNoAutorizadoException();
        var prueba=json.comoObjeto(s.prueba(),PruebaEntrega.class);generador.exigirEntrega(prueba,a);
        Object datos=repo.resultadoAutorizado(p,s).map(v->json.comoObjeto(v,Object.class)).orElse(null);
        exigirMandato(propio(programa,a,false),s,a);generador.exigirEntrega(prueba,a);return ocurrencia(p,s,datos);
    }
    /** E_C06 único reclamador; no actúa como profesional ficticio ni conserva un JWT. */
    @Transactional
    public Optional<Reclamo> reclamar(){
        var ahora=contexto.ahora();var candidato=repo.candidato(ahora);if(candidato.isEmpty())return Optional.empty();var p=candidato.get();var a=actor(p);
        if(!ahora.isBefore(p.vence())){detener(p,"FINALIZADO","VENCIMIENTO_MANDATO");return Optional.empty();}
        try{derechos.exigirPro(a,ahora);generador.verificar(configuracion(p),a);}
        catch(AccesoNoAutorizadoException|CapacidadDeshabilitadaException|NoEncontradoException|ConflictoException denegada){detener(p,"PAUSADO","REAUTORIZACION_REQUERIDA");return Optional.empty();}
        var anterior=repo.pendiente(p);UUID token=UUID.randomUUID();Paso s;
        if(anterior.isPresent()){
            var previa=anterior.get();
            if(previa.versionMandato()!=p.versionMandato()||previa.intentos()>=configuracion(p).maxIntentos()){detener(p,"PAUSADO","REINTENTOS_AGOTADOS");return Optional.empty();}
            s=repo.reclamarDeNuevo(p,previa,token,ahora.plusSeconds(leaseSegundos),ahora);
        }else{
            if(p.ocurrencias()>=p.maxOcurrencias()){detener(p,"FINALIZADO","LIMITE_MANDATO");return Optional.empty();}
            s=repo.crearPaso(p,token,ahora.plusSeconds(leaseSegundos),ahora);
        }
        tecnico(p,"OCURRENCIA_PERSONAL_RECLAMADA",Map.of("ocurrencia",s.referencia(),"intento",s.intentos(),"versionMandato",s.versionMandato()));
        return Optional.of(new Reclamo(p,s));
    }
    @Transactional(readOnly=true)
    public Produccion producir(Reclamo r){var a=actor(r.programa());var p=propio(r.programa().referencia(),a,false);var s=exigirReclamo(p,r.paso());exigirMandato(p,s,a);var resultado=generador.producir(configuracion(p),a,contexto.ahora());exigirMandato(propio(p.referencia(),a,false),s,a);return resultado;}
    @Transactional
    public boolean finalizar(Reclamo r,Produccion resultado){
        if(resultado==null)throw new IllegalArgumentException("Resultado requerido.");
        var a=actor(r.programa());var p=propio(r.programa().referencia(),a,true);var s=exigirReclamo(p,r.paso());exigirMandato(p,s,a);generador.verificar(configuracion(p),a);
        generador.exigirEntrega(resultado.prueba(),a);
        if(!repo.terminar(p,s,r.paso().reclamo(),resultado.comunicar()?"COMPLETADA":"SIN_RESULTADO",resultado.contenido()==null?null:json.objetoCanonico(resultado.contenido()),json.objetoCanonico(resultado.prueba()),null,contexto.ahora()))throw conflicto();
        tecnico(p,"OCURRENCIA_PERSONAL_DISPONIBLE",Map.of("ocurrencia",s.referencia(),"comunicar",resultado.comunicar(),"ejecucion","DETERMINISTA_SIN_CONSUMO_IA"));return true;
    }
    @Transactional
    public void fallar(Reclamo r,RuntimeException error){
        // Un error de ejecución no usa el contexto revocado para adquirir nueva autoridad de negocio.
        var p=repo.propio(r.programa().organizacion(),r.programa().propietario(),r.programa().referencia(),true).orElseThrow();
        var s=repo.paso(p.organizacion(),r.paso().referencia()).orElseThrow();
        if(!"RECLAMADA".equals(s.estado())||!s.reclamo().equals(r.paso().reclamo())||s.versionMandato()!=p.versionMandato()||!contexto.ahora().isBefore(s.lease()))return;
        boolean permiso=error instanceof AccesoNoAutorizadoException||error instanceof CapacidadDeshabilitadaException||error instanceof NoEncontradoException||error instanceof ConflictoException;
        if(permiso||s.intentos()>=configuracion(p).maxIntentos())detener(p,"PAUSADO",permiso?"REAUTORIZACION_REQUERIDA":"REINTENTOS_AGOTADOS");
        else repo.falloReintentable(p,s,r.paso().reclamo(),contexto.ahora().plusSeconds(p.segundos()),contexto.ahora());
        tecnico(p,"OCURRENCIA_PERSONAL_NO_COMPLETADA",Map.of("ocurrencia",s.referencia(),"causa",permiso?"PERMISO_O_MANDATO":"FALLO_TECNICO"));
    }
    private Paso exigirReclamo(Programa p,Paso original){var s=repo.paso(p.organizacion(),original.referencia()).filter(x->x.programa()==p.id()).orElseThrow(()->new NoEncontradoException("Ocurrencia"));if(!"RECLAMADA".equals(s.estado())||!s.reclamo().equals(original.reclamo())||!contexto.ahora().isBefore(s.lease()))throw conflicto();return s;}
    private void exigirMandato(Programa p,Paso s,Actor a){derechos.exigirPro(a,contexto.ahora());if(p.versionMandato()!=s.versionMandato()||Set.of("PAUSADO","CANCELADO").contains(p.estado())||!contexto.ahora().isBefore(p.vence()))throw new CapacidadDeshabilitadaException();}
    private void detener(Programa p,String estado,String causa){if(!repo.estado(p,estado,causa,p.proxima(),contexto.ahora()))throw conflicto();repo.detenerPendiente(p,"CANCELADO".equals(estado)?"CANCELADA":"PAUSADA",causa,contexto.ahora());tecnico(p,"PROGRAMA_PERSONAL_DETENIDO",Map.of("estado",estado,"causa",causa));}
    private void tecnico(Programa p,String tipo,Map<String,Object> detalle){eventos.saveAndFlush(EventoDominio.de(p.organizacion(),tipo,"PLAN_TAREA_ACOTADO",p.id(),null,"SISTEMA").con(json.objeto(detalle)));}
    private Vista conservar(Programa p,Actor a,String tipo,String clave,String h,Procedencia proc){
        contexto.exigirAgente(a);derechos.exigirPro(a,contexto.ahora());
        if("ACTIVO".equals(p.estado()))generador.verificar(configuracion(p),a);
        eventos.saveAndFlush(proc.sellar(EventoDominio.de(a.idOrganizacion(),tipo,"PLAN_TAREA_ACOTADO",p.id(),a.idRolOperativo(),proc.canal())).con(json.objetoCanonico(vista(p))));
        var v=vista(p);comandos.registrar(a,Idempotencia.exigirClave(clave),tipo,h,"PLAN_TAREA_ACOTADO",p.id(),proc,json.objetoCanonico(v));return v;
    }
    private Vista reproducir(String recibo,Actor a){var original=json.comoObjeto(recibo,Vista.class);propio(original.referencia(),a,false);derechos.exigirPro(a,contexto.ahora());return original;}
    private Programa propio(UUID ref,Actor a,boolean lock){contexto.exigirAgente(a);return repo.propio(a.idOrganizacion(),a.idRolOperativo(),ref,lock).filter(p->p.persona()==a.idPersona()).orElseThrow(()->new NoEncontradoException("Programa personal"));}
    private Optional<String> previo(String tipo,String clave,String h,Actor a){String k=Idempotencia.exigirClave(clave),id=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+a.idOrganizacion()+"\u0000"+k);recibos.bloquearIdentidad(Long.parseUnsignedLong(id.substring(0,16),16));return comandos.buscar(a,k,tipo,h).map(c->c.getResultado());}
    private String huella(String tipo,Object datos,Actor a,Procedencia p){if(p==null)throw regla("Procedencia requerida.");return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("entrada",Arrays.asList(tipo,datos,a,p))));}
    private Configuracion configuracion(Programa p){return json.comoObjeto(p.mandato(),Configuracion.class);}
    private Vista vista(Programa p){return new Vista(p.referencia(),p.version(),p.versionMandato(),p.estado(),p.causa(),configuracion(p),p.ocurrencias(),p.proxima());}
    private static Actor actor(Programa p){return new Actor(p.organizacion(),p.persona(),p.propietario(),"AGENTE");}
    private static Ocurrencia ocurrencia(Programa p,Paso s,Object datos){return new Ocurrencia(s.referencia(),p.referencia(),s.versionMandato(),s.programada(),s.estado(),s.intentos(),s.causa(),s.finalizada(),datos);}
    private void validar(Configuracion q){
        if(q==null||q.familia()==null||vacio(q.finalidad())||q.proximaEn()==null||q.venceEn()==null
                ||q.proximaEn().isBefore(contexto.ahora())||!q.venceEn().isAfter(q.proximaEn())||q.frecuenciaSegundos()<=0||q.maxOcurrencias()<=0||q.maxIntentos()<=0||!"BANDEJA_PRIVADA".equals(q.destinoPrivado()))throw regla("El mandato requiere propósito, período, recurrencia, vencimiento, límites expresos y bandeja privada.");
        boolean periodo=q.familia()==Familia.REPORTE_PROGRAMADO||q.familia()==Familia.RESUMEN_PROGRAMADO;
        if(periodo&&(q.diasPeriodo()==null||q.diasPeriodo()<=0))throw regla("Indica los días del período que preparará el reporte o resumen.");
        try{contexto.ahora().plusSeconds(q.frecuenciaSegundos());q.proximaEn().plusSeconds(q.frecuenciaSegundos());
            if(periodo&&contexto.hoy().minusDays(q.diasPeriodo()-1L).getYear()<1)throw regla("El período comienza fuera del calendario de BROX.");}
        catch(DateTimeException|ArithmeticException e){throw regla("La recurrencia no puede representarse en el calendario de BROX.");}
        if(!periodo&&q.diasPeriodo()!=null)throw regla("Esta familia consulta la situación actual y no admite un período analítico adicional.");
        if(q.familia()==Familia.ALERTA_CONDICIONADA&&(q.condicion()==null||q.condicion().metrica()==null||q.condicion().comparador()==null||q.condicion().umbral()==null))throw regla("La alerta conserva su condición y umbral expresamente elegidos.");
    }
    private static void version(Long v,Programa p){if(v==null||v!=p.version())throw conflicto();}
    private static void paginacion(int p,int t){if(p<1||t<1||t>100)throw regla("Página y tamaño entre1y100 requeridos.");}
    private static boolean vacio(String s){return s==null||s.isBlank();}
    private static ReglaNegocioException regla(String s){return new ReglaNegocioException(s);}
    private static ConflictoException conflicto(){return new ConflictoException("Cambió el programa o la reclamación; consulta su versión actual.");}
}
