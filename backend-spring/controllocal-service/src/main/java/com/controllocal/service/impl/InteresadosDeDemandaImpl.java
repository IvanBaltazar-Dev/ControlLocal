package com.controllocal.service.impl;

import com.controllocal.domain.persona.Persona;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.persistence.repositorio.HistoriaRequerimientoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Personas;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.RequerimientoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class InteresadosDeDemandaImpl implements InteresadosDeDemanda {
    private final ClienteService lectores;
    private final DetalleClienteRepository clientes;
    private final PersonaRepository personas;
    private final RequerimientoClienteRepository demandas;
    private final Documentos documentos;
    private final HistoriaRequerimientoRepository historia;
    private final Alcances alcances;
    public InteresadosDeDemandaImpl(ClienteService lectores,DetalleClienteRepository clientes,
            PersonaRepository personas,RequerimientoClienteRepository demandas,Documentos documentos,
            HistoriaRequerimientoRepository historia,Alcances alcances) {
        this.lectores=lectores;this.clientes=clientes;this.personas=personas;
        this.demandas=demandas;this.documentos=documentos;this.historia=historia;this.alcances=alcances;
    }
    @Override
    @Transactional(readOnly=true)
    public Referencia validar(Referencia ref,Actor actor) {
        Referencia normalizada=normalizar(ref);
        tratar(normalizada,actor,null,false);
        return normalizada;
    }
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public Interesado resolver(Referencia ref,Actor actor,Procedencia procedencia) {
        return tratar(normalizar(ref),actor,procedencia,true);
    }
    @Override @Transactional(readOnly=true)
    public void exigirLectura(Referencia ref,Long personaEsperada,Actor actor) {
        examinar(ref,personaEsperada,actor,true);
    }
    @Override @Transactional(readOnly=true)
    public Lectura consultarLectura(Referencia ref,Long personaEsperada,Actor actor){return examinar(ref,personaEsperada,actor,false);}
    private Lectura examinar(Referencia ref,Long personaEsperada,Actor actor,boolean exacta){
        Alcances.exigirOperador(actor);
        if(ref==null||ref.tipo()==null)throw new ReglaNegocioException("Identifica el origen del contacto.");
        return switch(ref.tipo()) {
            case CONTACTO_NUEVO -> {
                if(ref.idCliente()!=null||ref.idDemandaOrigen()!=null||ref.versionOrigen()!=null||ref.contacto()==null)
                    throw new ReglaNegocioException("La referencia del contacto propuesto es incompatible.");
                var d=ref.contacto();if(vacio(d.nombre())&&vacio(d.telefono())&&vacio(d.correo())&&vacio(d.numeroDocumento()))
                    throw new ReglaNegocioException("Conserva el dato conocido del contacto propuesto.");
                // Sólo el literal de la propuesta; no crea Persona ni lo usa como permiso sobre una existente.
                if(personaEsperada!=null)yield denegar(exacta,false,"Contacto original");
                yield new Lectura(true,null);
            }
            case CLIENTE -> {
                exclusiva(ref,ref.idCliente()!=null&&ref.idCliente()>0&&ref.idDemandaOrigen()==null&&ref.versionOrigen()==null&&ref.contacto()==null);
                var persona=lectores.personaLegible(ref.idCliente(),actor);
                if(persona.isEmpty()||personaEsperada!=null&&!personaEsperada.equals(persona.get()))yield denegar(exacta,false,"Contacto original");
                yield new Lectura(true,persona.get());
            }
            case DEMANDA_ORIGEN -> {
                exclusiva(ref,ref.idCliente()==null&&ref.contacto()==null&&ref.idDemandaOrigen()!=null&&ref.idDemandaOrigen()>0&&ref.versionOrigen()!=null&&ref.versionOrigen()>=0);
                var c=demandas.contexto(actor.idOrganizacion(),ref.idDemandaOrigen());
                if(c.isEmpty())yield denegar(exacta,false,"Demanda de origen");var actual=c.get();
                if(actual.getResponsable()==null||!alcances.alcanza(actor,actual.getResponsable()))yield denegar(exacta,true,"Demanda de origen");
                var conservada=historia.contactoOriginal(actor.idOrganizacion(),ref.idDemandaOrigen(),ref.versionOrigen());
                if(conservada.isEmpty())yield denegar(exacta,false,"Contacto original de la demanda");
                var s=conservada.get();
                if(!ref.idDemandaOrigen().equals(s.id())||!ref.versionOrigen().equals(s.version()))throw new IllegalStateException("La metadata histórica no corresponde al origen del contacto.");
                Long original=s.persona();
                if(original==null||!original.equals(actual.getPersonaInteresada())||personaEsperada!=null&&!personaEsperada.equals(original))
                    yield denegar(exacta,false,"Contacto original de la demanda");
                yield new Lectura(true,original);
            }
        };
    }
    private static Lectura denegar(boolean exacta,boolean alcance,String recurso){
        if(exacta){if(alcance)throw new AccesoNoAutorizadoException();throw new NoEncontradoException(recurso);}
        return new Lectura(false,null);
    }
    private Interesado tratar(Referencia ref,Actor actor,Procedencia procedencia,boolean persistir) {
        if(actor==null||!actor.esAgente())throw new AccesoNoAutorizadoException();
        if(ref==null||ref.tipo()==null)throw new ReglaNegocioException("Identifica al cliente o contacto de esta actuación.");
        var contexto=new LinkedHashMap<String,Object>();contexto.put("tipo",ref.tipo());
        contexto.put("procedencia",procedencia);
        return switch(ref.tipo()) {
            case CLIENTE -> {
                exclusiva(ref,ref.idCliente()!=null&&ref.idCliente()>0&&ref.idDemandaOrigen()==null
                        &&ref.versionOrigen()==null&&ref.contacto()==null);
                lectores.obtener(ref.idCliente(),actor);
                var cliente=clientes.buscarFicha(actor.idOrganizacion(),ref.idCliente())
                        .orElseThrow(()->new NoEncontradoException("Cliente"));
                contexto.put("idCliente",cliente.getId());
                yield new Interesado(cliente.getRol().getPersona(),cliente,documentos.objetoCanonico(contexto));
            }
            case CONTACTO_NUEVO -> {
                exclusiva(ref,ref.idCliente()==null&&ref.idDemandaOrigen()==null&&ref.versionOrigen()==null&&ref.contacto()!=null);
                var d=ref.contacto();
                if(vacio(d.nombre())&&vacio(d.telefono())&&vacio(d.correo())&&vacio(d.numeroDocumento()))
                    throw new ReglaNegocioException("Conserva el dato conocido del contacto; no se crea una persona ficticia.");
                String tipo=d.tipoPersona()==null?null:Personas.exigirCodigo(d.tipoPersona(),Personas.TIPOS_PERSONA,"tipo de persona");
                String documento=d.tipoDocumento()==null?null:Personas.exigirCodigo(d.tipoDocumento(),Personas.TIPOS_DOCUMENTO,"tipo de documento");
                Persona p=Personas.nueva(actor.idOrganizacion(),tipo,documento,limpiar(d.numeroDocumento()),
                        limpiar(d.nombre()),limpiar(d.telefono()),limpiar(d.correo()),Personas.ACTIVO,null);
                if(persistir)personas.saveAndFlush(p);
                if(p.getId()!=null)contexto.put("idPersona",p.getId());
                contexto.put("declaradoPor",actor.idRolOperativo());
                yield new Interesado(p,null,documentos.objetoCanonico(contexto));
            }
            case DEMANDA_ORIGEN -> {
                exclusiva(ref,ref.idCliente()==null&&ref.contacto()==null&&ref.idDemandaOrigen()!=null
                        &&ref.idDemandaOrigen()>0&&ref.versionOrigen()!=null&&ref.versionOrigen()>=0);
                var origen=demandas.contexto(actor.idOrganizacion(),ref.idDemandaOrigen())
                        .orElseThrow(()->new NoEncontradoException("Demanda de origen"));
                if(origen.getResponsable()==null||origen.getResponsable()!=actor.idRolOperativo())
                    throw new AccesoNoAutorizadoException();
                if(origen.getVersion()!=ref.versionOrigen())throw new ConflictoException("Cambió el contexto del interesado; vuelve a seleccionarlo.");
                if(origen.getPersonaInteresada()==null)throw new ReglaNegocioException("El origen no acredita una persona interesada.");
                var persona=personas.findByOrganizacionIdAndId(actor.idOrganizacion(),origen.getPersonaInteresada())
                        .orElseThrow(()->new NoEncontradoException("Persona contextual"));
                contexto.put("demandaOrigen",ref.idDemandaOrigen());contexto.put("versionOrigen",ref.versionOrigen());
                yield new Interesado(persona,null,documentos.objetoCanonico(contexto));
            }
        };
    }
    private static Referencia normalizar(Referencia ref) {
        if(ref==null||ref.contacto()==null)return ref;
        var c=ref.contacto();
        return new Referencia(ref.tipo(),ref.idCliente(),ref.idDemandaOrigen(),ref.versionOrigen(),
                new DatosContacto(limpiar(c.tipoPersona()),limpiar(c.tipoDocumento()),limpiar(c.numeroDocumento()),
                        limpiar(c.nombre()),limpiar(c.telefono()),limpiar(c.correo())));
    }
    private static boolean vacio(String v){return v==null||v.isBlank();}
    private static String limpiar(String v){return vacio(v)?null:v.trim();}
    private static void exclusiva(Referencia ref,boolean valida){
        if(!valida)throw new ReglaNegocioException("La referencia del interesado debe identificar un único contexto legítimo.");
    }
}
