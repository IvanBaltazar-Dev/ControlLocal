import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { AgentesService } from '../../core/api/agentes.service';
import { BrokersService } from '../../core/api/brokers.service';
import { MembresiasService } from '../../core/api/membresias.service';
import { MiembroForm } from './miembro-form';

interface AccesoForm { formulario:FormGroup; guardar():Promise<void> }
describe('Pertenencia y perfil laboral',()=>{
  let agentes:jasmine.SpyObj<AgentesService>,brokers:jasmine.SpyObj<BrokersService>,membresias:jasmine.SpyObj<MembresiasService>;
  function montar(edicion=false){
    agentes=jasmine.createSpyObj('AgentesService',['perfilParaGobierno','actualizar']);
    brokers=jasmine.createSpyObj('BrokersService',['pagina','obtener','actualizar']);
    membresias=jasmine.createSpyObj('MembresiasService',['invitar']);
    brokers.pagina.and.resolveTo({items:[],totalRecords:0,page:1,pageSize:50});
    agentes.perfilParaGobierno.and.resolveTo({id:30,nombre:'Profesional',correo:'laboral@example.test',estadoOperativo:'D'});
    agentes.actualizar.and.resolveTo({id:30});
    TestBed.configureTestingModule({imports:[MiembroForm],providers:[provideRouter([]),
      {provide:AgentesService,useValue:agentes},{provide:BrokersService,useValue:brokers},
      {provide:MembresiasService,useValue:membresias},
      {provide:ActivatedRoute,useValue:{snapshot:{paramMap:convertToParamMap(edicion?{id:'30'}:{}),data:{rolMiembro:edicion?'AGENTE':'BROKER'}}}},
    ]});
    spyOn(TestBed.inject(Router),'navigate').and.resolveTo(true);
    return TestBed.createComponent(MiembroForm);
  }
  it('un envío sin acuse conserva la clave original al reintentar y no solicita contraseña ajena',async()=>{
    const f=montar();let llamadas=0;
    membresias.invitar.and.callFake(async()=>{if(llamadas++===0)throw new Error('Acuse perdido');return {referencia:'original',venceEn:'2099-01-01T00:00:00Z',rolPrincipal:'BROKER',administrar:false};});
    f.detectChanges();await f.whenStable();
    const c=f.componentInstance as unknown as AccesoForm;
    c.formulario.patchValue({correoCuenta:'personal@example.test',venceEn:'2099-01-01T12:00',motivo:'Incorporación'});
    await c.guardar();await c.guardar();
    const [primera,segunda]=membresias.invitar.calls.allArgs();
    expect(primera[1]).toBeTruthy();expect(segunda).toEqual(primera);
    expect(c.formulario.get('contrasena')).toBeNull();expect(c.formulario.get('usuario')).toBeNull();
    expect(agentes.actualizar).not.toHaveBeenCalled();
  });
  it('editar solicita el perfil mínimo exacto y no muta credenciales o pertenencia',async()=>{
    const f=montar(true);f.detectChanges();await f.whenStable();
    const c=f.componentInstance as unknown as AccesoForm;await c.guardar();
    expect(agentes.perfilParaGobierno).toHaveBeenCalledOnceWith(30);expect(brokers.pagina).not.toHaveBeenCalled();
    const datos=agentes.actualizar.calls.mostRecent().args[1];
    expect(Object.keys(datos).sort()).toEqual(['correoLaboral','estadoOperativo','nombre','telefono','zona']);
    expect(membresias.invitar).not.toHaveBeenCalled();
  });
});
