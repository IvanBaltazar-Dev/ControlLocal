package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Autoridad por vínculo/campo de la comunicación. No hereda Persona desde un inmueble. */
@Component
public class ContextosDeInteraccion {
    public record ContactoOriginal(ReferenciaContactoContextual referencia,Long persona,boolean canalConocido) { }
    public record Autorizacion(long agente,List<VinculoActividad> vinculos,Map<String,ContactoOriginal> contactos,
                              List<VinculoFicha> padresOriginales,Long anterior,
                              @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<ReferenciaVersion> consultasOriginales) {
        public Autorizacion(long agente,List<VinculoActividad> vinculos,Map<String,ContactoOriginal> contactos,List<VinculoFicha> padresOriginales,Long anterior){this(agente,vinculos,contactos,padresOriginales,anterior,null);}
        public Autorizacion {vinculos=List.copyOf(vinculos);contactos=Map.copyOf(contactos);padresOriginales=List.copyOf(padresOriginales);consultasOriginales=consultasOriginales==null?null:List.copyOf(consultasOriginales);}
        public Set<Long> antecedentes(){return AtencionDeConsultas.antecedentes(anterior,consultasOriginales);}
    }
    public record Normalizado(DatosInteraccion datos,Autorizacion autorizacion,List<VinculoFicha> vinculos) { }
    /** Sólo metadatos originales: esta traducción no concede acceso ni hidrata contenido. */
    public static Autorizacion metadatos(com.controllocal.persistence.repositorio.InteraccionComercialRepository.Cabecera c,Documentos documentos){
        if(c.getAutorizacion()!=null)return documentos.comoObjeto(c.getAutorizacion(),Autorizacion.class);
        if(c.getDiccionario()!=null)throw new IllegalStateException("Falta acreditación de lectura de la interacción.");
        var links=new ArrayList<VinculoActividad>();
        if(c.getOportunidad()!=null)links.add(new VinculoActividad(TipoContexto.OPORTUNIDAD,c.getOportunidad(),null,null));
        if(c.getProspeccion()!=null)links.add(new VinculoActividad(TipoContexto.PROSPECCION,c.getProspeccion(),null,null));
        if(c.getEncargo()!=null)links.add(new VinculoActividad(TipoContexto.ENCARGO,c.getEncargo(),null,null));
        var contactos=new LinkedHashMap<String,ContactoOriginal>();
        if(c.getCliente()!=null){links.add(new VinculoActividad(TipoContexto.CLIENTE,c.getCliente(),null,null));
            contactos.put("clienteLegado",new ContactoOriginal(new ReferenciaContactoContextual(TipoContacto.CLIENTE,c.getCliente(),null,null),null,false));}
        return new Autorizacion(c.getAgente(),links,contactos,List.of(),null);
    }
    private final ContextosDeActividad contextos;
    private final ContactosDeInteraccion contactos;
    private final AutoridadDePropiedad propiedades;
    private final ProspeccionRepository prospecciones;
    private final CaptacionRepository encargos;
    private final AccesoOportunidad oportunidades;
    private final Alcances alcances;
    private final InteraccionComercialRepository comunicaciones;
    private final HistoriaInteraccionRepository historia;
    private final Documentos documentos;
    public ContextosDeInteraccion(ContextosDeActividad contextos,ContactosDeInteraccion contactos,
            AutoridadDePropiedad propiedades,ProspeccionRepository prospecciones,CaptacionRepository encargos,
            AccesoOportunidad oportunidades,Alcances alcances,InteraccionComercialRepository comunicaciones,
            HistoriaInteraccionRepository historia,Documentos documentos){
        this.contextos=contextos;this.contactos=contactos;this.propiedades=propiedades;this.prospecciones=prospecciones;
        this.encargos=encargos;this.oportunidades=oportunidades;this.alcances=alcances;
        this.comunicaciones=comunicaciones;this.historia=historia;this.documentos=documentos;
    }
    /** Autoridad única de fuentes originales: revalida contexto actual y cada versión citada, antes de hidratar. */
    public Set<ReferenciaVersion> versionesPermitidas(Collection<ReferenciaVersion> referencias,Actor actor,Long sinCicloCon){
        contextos.exigirOperador(actor);if(referencias.isEmpty())return Set.of();
        var actuales=new LinkedHashMap<Long,Autorizacion>();var historicas=new LinkedHashMap<HistoriaInteraccionRepository.Clave,Autorizacion>();var versionesActuales=new HashMap<Long,Long>();
        var consultadas=new HashSet<HistoriaInteraccionRepository.Clave>();var idsConsultados=new HashSet<Long>();
        var pendientes=new LinkedHashSet<HistoriaInteraccionRepository.Clave>();
        referencias.forEach(r->pendientes.add(new HistoriaInteraccionRepository.Clave(r.id(),r.version())));
        while(!pendientes.isEmpty()){
            var lote=pendientes.stream().filter(consultadas::add).toList();pendientes.clear();if(lote.isEmpty())break;
            var ids=new LinkedHashSet<Long>();lote.forEach(k->ids.add(k.id()));
            for(var h:historia.accesos(actor.idOrganizacion(),lote)){
                var a=documentos.comoObjeto(h.accesoJson(),Autorizacion.class);historicas.put(new HistoriaInteraccionRepository.Clave(h.id(),h.version()),a);
                ids.addAll(a.antecedentes());
                if(a.consultasOriginales()!=null)a.consultasOriginales().forEach(r->pendientes.add(new HistoriaInteraccionRepository.Clave(r.id(),r.version())));
            }
            var nuevas=ids.stream().filter(idsConsultados::add).toList();
            if(!nuevas.isEmpty())for(var c:comunicaciones.antecedentes(actor.idOrganizacion(),nuevas)){
                var a=metadatos(c,documentos);var anterior=versionesActuales.putIfAbsent(c.getId(),c.getVersion());
                if(anterior!=null&&anterior!=c.getVersion())throw new ConflictoException("Cambiaron los antecedentes de comunicación durante la lectura.");
                actuales.put(c.getId(),a);idsConsultados.add(c.getId());
                if(a.consultasOriginales()!=null)a.consultasOriginales().forEach(r->pendientes.add(new HistoriaInteraccionRepository.Clave(r.id(),r.version())));
            }
        }
        var todas=new LinkedHashSet<Autorizacion>(actuales.values());todas.addAll(historicas.values());
        var permitidas=verificarLote(List.copyOf(todas),actor,true);
        actuales.entrySet().removeIf(e->!permitidas.containsKey(e.getValue()));historicas.entrySet().removeIf(e->!permitidas.containsKey(e.getValue()));
        boolean retiro;do{
            retiro=actuales.entrySet().removeIf(e->!dependenciasLegibles(e.getValue(),actuales.keySet(),historicas.keySet()));
            retiro|=historicas.entrySet().removeIf(e->!dependenciasLegibles(e.getValue(),actuales.keySet(),historicas.keySet())||!actuales.containsKey(e.getKey().id()));
        }while(retiro);
        exigirGrafoSinCiclos(actuales,historicas);
        if(!actuales.isEmpty()){
            var ultimas=new HashMap<Long,Long>();for(var c:comunicaciones.antecedentes(actor.idOrganizacion(),List.copyOf(actuales.keySet())))ultimas.put(c.getId(),c.getVersion());
            for(var id:actuales.keySet())if(!Objects.equals(versionesActuales.get(id),ultimas.get(id)))throw new ConflictoException("Cambió una fuente de comunicación; actualiza la lectura.");
        }
        var salida=new LinkedHashSet<ReferenciaVersion>();
        for(var r:referencias)if(historicas.containsKey(new HistoriaInteraccionRepository.Clave(r.id(),r.version())))salida.add(r);
        if(sinCicloCon!=null&&salida.containsAll(referencias)&&(actuales.containsKey(sinCicloCon)||historicas.keySet().stream().anyMatch(k->k.id()==sinCicloCon)))
            throw new ReglaNegocioException("Los antecedentes no pueden volver a esta comunicación.");
        contextos.exigirOperador(actor);return Set.copyOf(salida);
    }
    private static void exigirGrafoSinCiclos(Map<Long,Autorizacion> actuales,Map<HistoriaInteraccionRepository.Clave,Autorizacion> historicas){
        var grafo=new LinkedHashMap<String,List<String>>();
        actuales.forEach((id,a)->grafo.put("C:"+id,aristas(a)));
        historicas.forEach((k,a)->{var e=new ArrayList<>(aristas(a));e.add("C:"+k.id());grafo.put("H:"+k.id()+":"+k.version(),e);});
        var terminados=new HashSet<String>();var enCurso=new HashSet<String>();var posiciones=new HashMap<String,Integer>();var pila=new ArrayDeque<String>();
        for(var inicio:grafo.keySet()){
            if(terminados.contains(inicio))continue;pila.push(inicio);enCurso.add(inicio);
            while(!pila.isEmpty()){
                var n=pila.peek();var aristas=grafo.get(n);int p=posiciones.getOrDefault(n,0);
                if(p==aristas.size()){pila.pop();enCurso.remove(n);terminados.add(n);continue;}
                posiciones.put(n,p+1);var destino=aristas.get(p);if(!grafo.containsKey(destino)||terminados.contains(destino))continue;
                if(!enCurso.add(destino))throw new IllegalStateException("Ciclo en los antecedentes de comunicación.");pila.push(destino);
            }
        }
    }
    private static List<String> aristas(Autorizacion a){
        var aristas=new ArrayList<String>();a.antecedentes().forEach(id->aristas.add("C:"+id));
        if(a.consultasOriginales()!=null)a.consultasOriginales().forEach(r->aristas.add("H:"+r.id()+":"+r.version()));return aristas;
    }
    private static boolean dependenciasLegibles(Autorizacion a,Set<Long> actuales,Set<HistoriaInteraccionRepository.Clave> historicas){
        return actuales.containsAll(a.antecedentes())&&(a.consultasOriginales()==null||a.consultasOriginales().stream().allMatch(r->historicas.contains(new HistoriaInteraccionRepository.Clave(r.id(),r.version()))));
    }
    public Normalizado normalizar(DatosInteraccion d,Actor actor,Procedencia proc,boolean persistir){
        if(d==null)throw new ReglaNegocioException("Conserva los datos conocidos de la comunicación real.");
        var pruebas=new LinkedHashMap<String,ContactoOriginal>();var links=new ArrayList<VinculoActividad>();
        var resolucion=new Resolucion(d,actor,proc,persistir,pruebas);
        var destinatario=resolucion.participante("destinatario");var interlocutor=resolucion.participante("interlocutor");
        int n=0;for(var v:lista(d.vinculos())){
            if(v==null||v.tipo()==null)throw new ReglaNegocioException("Identifica el tipo de vínculo.");
            if(v.tipo()==TipoContexto.CONTACTO){
                var res=resolucion.contacto(v.contacto());var ref=resolucion.salida(v.contacto(),res);
                Long persona=res.persona()==null?null:res.persona().getId();
                if(v.referencia()!=null&&persona!=null&&!v.referencia().equals(persona))throw new ReglaNegocioException("El vínculo no corresponde al contacto.");
                links.add(new VinculoActividad(v.tipo(),persona,v.versionEsperada(),ref));pruebas.put("vinculos."+n,new ContactoOriginal(res.referencia(),persona,canal(res)));
            }else{
                if(v.contacto()!=null||v.referencia()==null||v.referencia()<=0)throw new ReglaNegocioException("Referencia de vínculo incompleta.");
                links.add(v);
                if(v.tipo()==TipoContexto.CLIENTE){var ref=new ReferenciaContactoContextual(TipoContacto.CLIENTE,v.referencia(),null,null);
                    pruebas.put("vinculos."+n,new ContactoOriginal(ref,contactos.comprobar(ref,null,actor),false));}
            }n++;
        }
        RestriccionContacto restriccion=d.restriccion();
        if(restriccion!=null){
            if(vacio(restriccion.declaracion()))throw new ReglaNegocioException("Conserva la declaración de restricción de contacto.");
            if(restriccion.destinatario()!=null){var res=resolucion.contacto(restriccion.destinatario());
                restriccion=new RestriccionContacto(restriccion.declaracion(),resolucion.salida(restriccion.destinatario(),res),restriccion.finalidad(),restriccion.fuente());
                pruebas.put("restriccion",new ContactoOriginal(res.referencia(),res.persona()==null?null:res.persona().getId(),canal(res)));}
        }
        var declaraciones=lista(d.declaraciones()).stream().map(x->{var c=x.confirmacion();return new Declaracion(x.texto(),x.conceptoDeclarado(),x.valorDeclarado(),x.unidad(),x.moneda(),x.momento(),x.referenciaInterlocutor(),x.fuente(),
                c==null?new ConfirmacionContextual("POR_CONFIRMAR",null,null,null):new ConfirmacionContextual("POR_CONFIRMAR",c.referenciaDato(),c.versionDato(),c.evidencia()));}).toList();
        var datos=new DatosInteraccion(links,destinatario,interlocutor,d.tipo(),d.canal(),d.medioReal(),d.eventos(),d.resultado(),d.momento(),
                d.resumen(),declaraciones,d.proxima(),d.interaccionAnterior(),d.fuente(),restriccion,d.gestionContacto(),d.atencionConsultas());
        var consultas=AtencionDeConsultas.consultasReferidas(d);
        var a=new Autorizacion(actor.idRolOperativo(),links,pruebas,List.of(),d.interaccionAnterior(),consultas);
        var padres=verificar(a,actor,false);
        a=new Autorizacion(a.agente(),a.vinculos(),a.contactos(),padres,a.anterior(),a.consultasOriginales());
        return new Normalizado(datos,a,padres);
    }
    private final class Resolucion {
        private final DatosInteraccion datos;private final Actor actor;private final Procedencia proc;private final boolean persistir;
        private final Map<String,ContactoOriginal> prueba;private final Map<String,ParticipanteDeclarado> resueltos=new HashMap<>();
        private final Map<String,ContactosDeInteraccion.Resuelto> referencias=new HashMap<>();private final Set<String> resolviendo=new HashSet<>();
        Resolucion(DatosInteraccion datos,Actor actor,Procedencia proc,boolean persistir,Map<String,ContactoOriginal> prueba){
            this.datos=datos;this.actor=actor;this.proc=proc;this.persistir=persistir;this.prueba=prueba;
        }
        ParticipanteDeclarado participante(String campo){
            if(resueltos.containsKey(campo))return resueltos.get(campo);
            if(!resolviendo.add(campo))throw new ReglaNegocioException("Las referencias locales de participantes no pueden formar un ciclo.");
            var p="destinatario".equals(campo)?datos.destinatario():datos.interlocutor();ParticipanteDeclarado resultado=p;
            if(p!=null&&p.contacto()!=null){
                var r=contacto(p.contacto());referencias.put(campo,r);
                prueba.put(campo,new ContactoOriginal(r.referencia(),r.persona()==null?null:r.persona().getId(),canal(r)));
                String nombre=p.nombre(),telefono=p.telefono();
                if(r.persona()!=null){if(nombre==null)nombre=r.persona().getNombresORazonSocial();if(telefono==null)telefono=r.persona().getTelefono();}
                else if(r.referencia().datos()!=null){if(nombre==null)nombre=r.referencia().datos().nombre();if(telefono==null)telefono=r.referencia().datos().telefono();}
                resultado=new ParticipanteDeclarado(salida(p.contacto(),r),p.tipoDeclarado(),nombre,telefono,p.referenciaDeclarada());
            }
            resolviendo.remove(campo);resueltos.put(campo,resultado);return resultado;
        }
        ContactosDeInteraccion.Resuelto contacto(ReferenciaContactoContextual ref){
            if(ref!=null&&(ref.tipo()==TipoContacto.DESTINATARIO||ref.tipo()==TipoContacto.INTERLOCUTOR)){
                if(ref.referencia()!=null||ref.versionEsperada()!=null||ref.datos()!=null)throw new ReglaNegocioException("La referencia local no lleva datos ni IDs adicionales.");
                String campo=ref.tipo()==TipoContacto.DESTINATARIO?"destinatario":"interlocutor";participante(campo);
                var r=referencias.get(campo);if(r==null)throw new ReglaNegocioException("El participante local no tiene una referencia contextual; conserva el literal sin inventar Persona.");
                return r;
            }
            return contactos.resolver(ref,actor,proc,persistir);
        }
        ReferenciaContactoContextual salida(ReferenciaContactoContextual entrada,ContactosDeInteraccion.Resuelto r){
            // La preparación conserva el selector explícito: al confirmar no se duplican CONTACTO_NUEVO.
            return !persistir&&entrada!=null&&(entrada.tipo()==TipoContacto.DESTINATARIO||entrada.tipo()==TipoContacto.INTERLOCUTOR)?entrada:r.referencia();
        }
    }
    public List<VinculoFicha> verificar(Autorizacion a,Actor actor,boolean historica){
        var permitidos=verificarLote(List.of(a),actor,historica);
        if(!permitidos.containsKey(a))throw new NoEncontradoException("Interacción autorizada");
        return permitidos.get(a);
    }
    /** Una lectura de propiedades por lote; las referencias compartidas se comprueban una vez por operación. */
    public Map<Autorizacion,List<VinculoFicha>> verificarLote(List<Autorizacion> autorizaciones,Actor actor,boolean historica){
        contextos.exigirOperador(actor);var roles=new HashSet<>(alcances.de(actor).rolesAgente());
        var cache=new HashMap<String,List<VinculoFicha>>();var rechazados=new HashSet<String>();
        var candidatos=new LinkedHashMap<Autorizacion,List<VinculoFicha>>();var ids=new LinkedHashSet<Long>();
        var contactosVistos=new HashSet<ContactoOriginal>();var contactosRechazados=new HashSet<ContactoOriginal>();
        for(var a:autorizaciones){
            if(!roles.contains(a.agente()))continue;
            try{
                var salida=new LinkedHashMap<String,VinculoFicha>();
                for(var v:a.vinculos()){
                    String k=v.tipo()+":"+v.referencia();
                    if(rechazados.contains(k))throw new NoEncontradoException("Vínculo de interacción");
                    var actuales=cache.get(k);
                    if(actuales==null){
                        var m=new LinkedHashMap<String,VinculoFicha>();
                        try{verificarVinculo(v,actor,roles,m);}catch(NoEncontradoException|AccesoNoAutorizadoException denegada){rechazados.add(k);throw denegada;}
                        actuales=List.copyOf(m.values());cache.put(k,actuales);
                    }
                    actuales.forEach(vf->agregar(salida,vf));
                }
                for(var c:a.contactos().values()){
                    if(contactosRechazados.contains(c))throw new NoEncontradoException("Contacto original");
                    if(!contactosVistos.contains(c)){
                        try{contactos.comprobar(c.referencia(),c.persona(),actor);contactosVistos.add(c);}
                        catch(NoEncontradoException|AccesoNoAutorizadoException denegada){contactosRechazados.add(c);throw denegada;}
                    }
                }
                if(historica)for(var p:a.padresOriginales()){
                    TipoContexto t;try{t=TipoContexto.valueOf(p.tipo());}catch(IllegalArgumentException e){throw new IllegalStateException("Tipo histórico incompatible.",e);}
                    if(t==TipoContexto.CONTACTO||t==TipoContexto.CLIENTE)continue;
                    String k=t+":"+p.referencia();
                    if(rechazados.contains(k))throw new NoEncontradoException("Vínculo original");
                    if(!cache.containsKey(k)){
                        var m=new LinkedHashMap<String,VinculoFicha>();
                        try{verificarVinculo(new VinculoActividad(t,p.referencia(),p.versionReferencia(),null),actor,roles,m);cache.put(k,List.copyOf(m.values()));}
                        catch(NoEncontradoException|AccesoNoAutorizadoException denegada){rechazados.add(k);throw denegada;}
                    }
                }
                var l=List.copyOf(salida.values());candidatos.put(a,l);
                l.stream().filter(v->"PROPIEDAD".equals(v.tipo())).forEach(v->ids.add(v.referencia()));
                if(historica)a.padresOriginales().stream().filter(v->"PROPIEDAD".equals(v.tipo())).forEach(v->ids.add(v.referencia()));
            }catch(NoEncontradoException|AccesoNoAutorizadoException denegada){/* Sólo se omite esta identidad, antes de hidratar su contenido. */}
        }
        Set<Long> fisicas=propiedades.legibles(actor,ids);var resultado=new LinkedHashMap<Autorizacion,List<VinculoFicha>>();
        candidatos.forEach((a,l)->{
            boolean explicitas=a.vinculos().stream().filter(v->v.tipo()==TipoContexto.PROPIEDAD).allMatch(v->fisicas.contains(v.referencia()));
            boolean originales=!historica||a.padresOriginales().stream().filter(v->"PROPIEDAD".equals(v.tipo())).allMatch(v->fisicas.contains(v.referencia()));
            if(explicitas&&originales)resultado.put(a,l.stream().filter(v->!"PROPIEDAD".equals(v.tipo())||fisicas.contains(v.referencia())).toList());
        });
        return Collections.unmodifiableMap(resultado);
    }
    private void verificarVinculo(VinculoActividad v,Actor actor,Set<Long> roles,Map<String,VinculoFicha> salida){
        if(v.tipo()==TipoContexto.CONTACTO&&v.referencia()==null)return; // CONTACTO_NUEVO en propuesta: aún no tiene identidad persistida.
        long id=Objects.requireNonNull(v.referencia());
        switch(v.tipo()){
            case PROPIEDAD -> agregar(salida,new VinculoFicha("PROPIEDAD",id,v.versionEsperada()));
            case PROSPECCION -> {
                var p=prospecciones.contextoParaActividad(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Prospección"));
                if(!roles.contains(p.getResponsable()))throw new AccesoNoAutorizadoException();
                agregar(salida,new VinculoFicha("PROSPECCION",id,null));derivarPropiedad(p.getPropiedad(),salida);
            }
            case ENCARGO -> {
                var e=encargos.contextoActual(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Encargo"));
                if(!roles.contains(e.getResponsable()))throw new AccesoNoAutorizadoException();
                agregar(salida,new VinculoFicha("ENCARGO",id,e.getVersion()));derivarPropiedad(e.getPropiedad(),salida);
            }
            case OPORTUNIDAD -> {
                var o=oportunidades.buscarParaLectura(id,actor).orElseThrow(()->new NoEncontradoException("Oportunidad"));agregar(salida,new VinculoFicha("OPORTUNIDAD",id,o.version()));
                var e=encargos.contextoActual(actor.idOrganizacion(),o.idEncargo());
                if(e.isPresent()&&roles.contains(e.get().getResponsable()))agregar(salida,new VinculoFicha("ENCARGO",o.idEncargo(),e.get().getVersion()));
                derivarPropiedad(o.idPropiedad(),salida);
            }
            case CLIENTE -> agregar(salida,new VinculoFicha("CLIENTE",id,v.versionEsperada()));
            case CONTACTO -> agregar(salida,new VinculoFicha("CONTACTO",id,v.versionEsperada()));
        }
    }
    private void derivarPropiedad(Long id,Map<String,VinculoFicha> salida){
        if(id!=null)agregar(salida,new VinculoFicha("PROPIEDAD",id,null));
    }
    private static void agregar(Map<String,VinculoFicha> m,VinculoFicha v){m.putIfAbsent(v.tipo()+":"+v.referencia(),v);}
    private static <T> List<T> lista(List<T> l){return l==null?List.of():l;}
    private static boolean vacio(String s){return s==null||s.isBlank();}
    private static boolean canal(ContactosDeInteraccion.Resuelto r){
        if(r.persona()!=null)return !vacio(r.persona().getTelefono())||!vacio(r.persona().getCorreo());
        var d=r.referencia().datos();return d!=null&&(!vacio(d.telefono())||!vacio(d.correo()));
    }
}
