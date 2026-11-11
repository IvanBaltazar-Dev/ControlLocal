package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.service.Actor;
import com.controllocal.service.ContratoService;
import com.controllocal.service.PreparacionesDeContrato;
import com.controllocal.service.OfertaService;
import com.controllocal.service.OfertaService.*;
import com.controllocal.service.encargo.*;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.AccesoOportunidad;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import static com.controllocal.domain.comercial.ArchivoEncargo.*;
import static com.controllocal.service.negociacion.ContenidoNegociacion.dinero;

/** RF053: una cobertura material compartida por el escritor y sus lectores, no otro ciclo de negocio. */
@Component
final class FuentesDeFormalizacion {
    private final EncargoService encargos;
    private final AccesoEncargo acceso;
    private final AccesoOportunidad oportunidades;
    private final PactosDeEncargo pactos;
    private final OfertaService ofertas;
    private final EvidenciasDeFormalizacion evidencias;
    private final Documentos json;
    private final AutoridadDePropiedad autoridadFisica;
    private final PreparacionesDeContrato contratos;
    FuentesDeFormalizacion(EncargoService encargos,AccesoEncargo acceso,AccesoOportunidad oportunidades,
            PactosDeEncargo pactos,OfertaService ofertas,EvidenciasDeFormalizacion evidencias,Documentos json,AutoridadDePropiedad autoridadFisica,PreparacionesDeContrato contratos){
        this.encargos=encargos;this.acceso=acceso;this.oportunidades=oportunidades;this.pactos=pactos;
        this.ofertas=ofertas;this.evidencias=evidencias;this.json=json;this.autoridadFisica=autoridadFisica;this.contratos=contratos;
    }

    CoberturaRevision cubrir(Captacion c,long oportunidad,ReferenciaPacto pacto,Long acuerdo,Long version,
                             List<ReferenciaDocumento> documentos,Long contrato,LocalDate fecha,Actor actor){
        if(pacto==null||pacto.encargo()!=c.getId())throw new ReglaNegocioException("Identifica el pacto firmado de este Encargo.");
        var firmado=pactos.consultarPactoFirmado(pacto,actor);
        var op=oportunidades.contextoParaNegociacion(oportunidad,actor);
        if(op.idEncargo()!=c.getId())throw new NoEncontradoException("Oportunidad del expediente");
        if((acuerdo==null)!=(version==null))throw new ReglaNegocioException("El acuerdo conserva ID y versión juntos.");
        if(acuerdo!=null)acuerdo(oportunidad,acuerdo,version,actor);
        var partes=encargos.consultarPartesNegociacion(c.getId(),c.getVersion(),fecha,actor);
        var tit=new ArrayList<ReferenciaDocumento>();var rep=new ArrayList<ReferenciaDocumento>();
        for(var parte:partes.partes()){
            if(parte.evidenciaTitularidad()!=null)
                agregar(tit,evidencias.consultar(c.getId(),parte.evidenciaTitularidad(),TITULARIDAD,actor));
            if(parte.evidenciaRepresentacion()!=null)
                agregar(rep,evidencias.consultar(c.getId(),parte.evidenciaRepresentacion(),REPRESENTACION,actor));
        }
        evidencias.exigir(c.getId(),documentos,HITO,actor);
        var preparada=contrato==null?null:contratos.consultar(contrato,actor);
        if(preparada!=null&&(preparada.encargo()!=c.getId()||preparada.oportunidad()!=oportunidad))
            throw new NoEncontradoException("Contrato del contexto del expediente");
        return new CoberturaRevision(pacto,firmado.acreditacion(),objeto(c,actor),partes,op.interesado(),
                acuerdo,version,documentos,tit,rep,preparada);
    }

    private static void agregar(List<ReferenciaDocumento> refs,ReferenciaDocumento ref){if(!refs.contains(ref))refs.add(ref);}

    ObjetoExacto objeto(Captacion c,Actor actor){
        if(c.getIdComposicionActual()==null)return new ObjetoExacto(TipoObjeto.UNIDAD,c.getId(),c.getVersion(),
                c.getPropiedad().getId(),c.getPropiedad().getVersionFisica(),null,null,List.of(c.getPropiedad().getId()));
        var comp=encargos.consultarComposicion(c.getId(),null,actor);
        return new ObjetoExacto(TipoObjeto.COMPOSICION,c.getId(),c.getVersion(),null,null,comp.id(),comp.version(),
                comp.unidades().stream().map(EncargoService.Unidad::idPropiedad).sorted().toList());
    }

    AcuerdoAceptado acuerdo(long oportunidad,long id,long version,Actor actor){
        var a=ofertas.consultarAcuerdoAceptado(oportunidad,null,actor)
                .orElseThrow(()->new NoEncontradoException("Acuerdo aceptado del expediente"));
        if(a.id()!=id||a.version()!=version)throw new ConflictoException("Revisa la referencia exacta del acuerdo aceptado.");
        return a;
    }
    void exigirVersionOportunidad(long oportunidad,Long version,Actor actor){
        if(version==null||version<0||oportunidades.contextoParaNegociacion(oportunidad,actor).version()!=version)
            throw new ConflictoException("Cambió la oportunidad; conserva su versión actual.");
    }
    void reautorizarPacto(ReferenciaPacto p,Actor actor){pactos.consultarPactoFirmado(p,actor);}

    FuenteEconomicaFinal fuenteC4(CoberturaRevision cobertura,long oportunidad,Actor actor){
        if(cobertura.acuerdoC4()==null)return null;
        var a=acuerdo(oportunidad,cobertura.acuerdoC4(),cobertura.versionAcuerdoC4(),actor);
        // C4 conserva Dinero.base/periodo abiertos: no prueban precio total o renta sin cargos.
        // El antecedente exacto conserva su importe original; una revisión tipada acredita el concepto C5.
        return new FuenteEconomicaFinal(new ReferenciaFuenteEconomica("ACUERDO_C4",a.id(),a.version()),
                a.id(),a.version(),a.propuesta().objeto(),null,null,List.of(),a.procedencia());
    }

    void reautorizar(long encargo,long oportunidad,CoberturaRevision c,Actor actor){
        acceso.exigirLectura(encargo,actor);
        var op=oportunidades.contextoParaNegociacion(oportunidad,actor);
        if(op.idEncargo()!=encargo||c.pacto().encargo()!=encargo||!Objects.equals(c.objeto().encargo(),encargo)||c.partes().encargo()!=encargo)
            throw new NoEncontradoException("Cobertura del expediente");
        if(!Objects.equals(op.interesado(),c.interesado()))throw new AccesoNoAutorizadoException();
        var ids=new HashSet<Long>();if(c.objeto().propiedad()!=null)ids.add(c.objeto().propiedad());
        if(c.objeto().unidades()!=null)ids.addAll(c.objeto().unidades());
        for(long id:ids)if(!autoridadFisica.puedeLeer(actor,id))throw new AccesoNoAutorizadoException();
        pactos.consultarPactoFirmado(c.pacto(),actor);
        evidencias.exigir(encargo,c.documentos(),HITO,actor);
        evidencias.exigir(encargo,c.titularidad(),TITULARIDAD,actor);
        evidencias.exigir(encargo,c.representacion(),REPRESENTACION,actor);
        if(c.acuerdoC4()!=null)acuerdo(oportunidad,c.acuerdoC4(),c.versionAcuerdoC4(),actor);
        if(c.contratoPreparado()!=null){
            var original=c.contratoPreparado();var actual=contratos.consultar(original.id(),actor);
            if(actual.id()!=original.id()||actual.encargo()!=encargo||actual.oportunidad()!=oportunidad
                    ||!Objects.equals(actual.contratoAnterior(),original.contratoAnterior()))throw new AccesoNoAutorizadoException();
            // Una lectura histórica no vuelve a exigir P ni la huella de un contrato luego firmado/rectificado.
        }
    }

    void exigirMismaCobertura(CoberturaRevision aprobada,CoberturaRevision actual){
        if(!mismaCobertura(aprobada,actual,json))
            throw new ConflictoException("Cambió un elemento material del expediente; revisa su cobertura antes de confirmar.");
    }
    /** Ignora sólo las versiones globales por notas/agenda; conserva cada elemento material y su procedencia. */
    static boolean mismaCobertura(CoberturaRevision a,CoberturaRevision b,Documentos json){
        return Objects.equals(a.pacto(),b.pacto())&&Objects.equals(a.acreditacionPacto(),b.acreditacionPacto())
                &&Objects.equals(objetoMaterial(a.objeto()),objetoMaterial(b.objeto()))
                &&Objects.equals(a.interesado(),b.interesado())
                &&Objects.equals(a.acuerdoC4(),b.acuerdoC4())&&Objects.equals(a.versionAcuerdoC4(),b.versionAcuerdoC4())
                &&Objects.equals(json.objetoCanonico(Map.of("partes",a.partes().partes())),json.objetoCanonico(Map.of("partes",b.partes().partes())))
                &&new HashSet<>(a.documentos()).equals(new HashSet<>(b.documentos()))
                &&new HashSet<>(a.titularidad()).equals(new HashSet<>(b.titularidad()))
                &&new HashSet<>(a.representacion()).equals(new HashSet<>(b.representacion()))
                &&mismaPreparacion(a.contratoPreparado(),b.contratoPreparado());
    }
    private static boolean mismaPreparacion(ContratoService.PreparacionContrato a,ContratoService.PreparacionContrato b){
        return a==null?b==null:b!=null&&a.id()==b.id()&&a.encargo()==b.encargo()&&a.oportunidad()==b.oportunidad()
                &&Objects.equals(a.contratoAnterior(),b.contratoAnterior())&&Objects.equals(a.huella(),b.huella());
    }
    private static ObjetoExacto objetoMaterial(ObjetoExacto o){
        return new ObjetoExacto(o.tipo(),o.encargo(),null,o.propiedad(),o.versionFisica(),o.composicion(),o.versionComposicion(),
                o.unidades()==null?null:o.unidades().stream().sorted().toList());
    }

    AcuerdoFinalDocumentado validarFinal(AcuerdoFinalDocumentado a,Captacion c,Actor actor){
        if(a==null)return null;
        if(a.precioVenta()!=null&&c.operacion()!=com.controllocal.domain.inmueble.OperacionInmobiliaria.VENTA
                ||a.rentaMensual()!=null&&c.operacion()!=com.controllocal.domain.inmueble.OperacionInmobiliaria.ALQUILER)
            throw new ReglaNegocioException("El concepto económico final no corresponde a esta operación.");
        if(a.documentos()==null||a.documentos().isEmpty())throw new ReglaNegocioException("Identifica la fuente documental del acuerdo final revisado.");
        evidencias.exigir(c.getId(),a.documentos(),HITO,actor);
        if(a.precioVenta()!=null)dinero(a.precioVenta());if(a.rentaMensual()!=null)dinero(a.rentaMensual());
        if(a.declaradoEnEscritura()!=null)dinero(a.declaradoEnEscritura());
        var t=a.terminosAlquiler();
        if(t!=null&&(t.inicio()==null||t.fin()==null||!t.fin().isAfter(t.inicio())))
            throw new ReglaNegocioException("Conserva fechas contractuales inicial y final válidas.");
        return a;
    }
    FuenteEconomicaFinal fuenteRevision(long expediente,long revision,ContenidoFormalizacion.Revision r){
        var a=r.datos().acuerdoFinal();if(a==null)return r.fuenteC4();
        return new FuenteEconomicaFinal(new ReferenciaFuenteEconomica("REVISION_C5",expediente,revision),
                r.cobertura().acuerdoC4(),r.cobertura().versionAcuerdoC4(),r.cobertura().objeto(),
                a.precioVenta(),a.rentaMensual(),a.documentos(),r.procedencia());
    }
}
