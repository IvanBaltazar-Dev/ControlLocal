import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AgentesService } from '../../core/api/agentes.service';
import { Broker, BrokersService } from '../../core/api/brokers.service';
import { MembresiasService, InvitacionEmitida } from '../../core/api/membresias.service';
import { ApiError } from '../../core/api/api.types';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { BroxPageHeader } from '../../shared/page-header/page-header';

/** Adaptador transitorio: editar contacto laboral o invitar; Core conserva las reglas. */
@Component({
  selector: 'cl-miembro-form', imports: [ReactiveFormsModule, EstadoListado, BroxPageHeader],
  templateUrl: './miembro-form.html', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MiembroForm implements OnInit {
  private readonly agentes=inject(AgentesService);
  private readonly brokers=inject(BrokersService);
  private readonly membresias=inject(MembresiasService);
  private readonly ruta=inject(ActivatedRoute);
  private readonly router=inject(Router);
  private readonly fb=inject(FormBuilder);
  // Identifica esta actuación: un fallo de red no crea otra invitación al reintentar.
  private readonly claveInvitacion=crypto.randomUUID();
  protected readonly id=Number(this.ruta.snapshot.paramMap.get('id')) || null;
  protected readonly esEdicion=this.id!==null;
  protected readonly cargando=signal(false);
  protected readonly guardando=signal(false);
  protected readonly error=signal<string|null>(null);
  protected readonly emitida=signal<InvitacionEmitida|null>(null);
  protected readonly supervisores=signal<Broker[]>([]);
  protected readonly totalSupervisores=signal(0);
  private paginaSupervisores=0;
  protected readonly hayMasSupervisores=computed(()=>this.supervisores().length<this.totalSupervisores());
  protected readonly formulario=this.fb.nonNullable.group({
    rol: this.fb.nonNullable.control(this.ruta.snapshot.data['rolMiembro'] as string || ''),
    administrar:false,nombre:'',telefono:'',correoLaboral:['',Validators.email],zona:'',
    estadoOperativo:'',correoCuenta:['',Validators.email],supervisor:'',venceEn:'',motivo:'',
  });
  ngOnInit(): void {
    if(this.esEdicion) void this.cargar();
    else void this.masSupervisores();
  }
  protected async masSupervisores(): Promise<void> {
    try {
      const p=await this.brokers.pagina(this.paginaSupervisores+1,50);
      this.supervisores.update(xs=>[...xs,...p.items]);
      this.totalSupervisores.set(p.totalRecords);this.paginaSupervisores++;
    } catch(e) {this.error.set(e instanceof ApiError?e.message:'No se pudieron consultar los supervisores.');}
  }
  protected async cargar(): Promise<void> {
    if(this.id===null)return;
    this.cargando.set(true);this.error.set(null);
    try {
      const f=this.formulario.controls.rol.value==='AGENTE'
        ? await this.agentes.perfilParaGobierno(this.id):await this.brokers.obtener(this.id);
      this.formulario.patchValue({nombre:f.nombre??'',telefono:f.telefono??'',correoLaboral:f.correo??'',zona:f.zona??'',
        estadoOperativo:'estadoOperativo' in f ? (f.estadoOperativo as string ?? ''):''});
    } catch(e) {this.error.set(e instanceof ApiError?e.message:'No se pudo consultar el perfil laboral.');}
    finally {this.cargando.set(false);}
  }
  protected async guardar(): Promise<void> {
    if(this.guardando())return;
    this.formulario.markAllAsTouched();
    if(this.formulario.invalid){this.error.set('Revisa el correo indicado.');return;}
    const v=this.formulario.getRawValue();
    this.error.set(null);this.guardando.set(true);
    try {
      const local={nombre:v.nombre.trim()||undefined,telefono:v.telefono.trim(),correoLaboral:v.correoLaboral.trim(),zona:v.zona.trim()};
      if(this.id!==null){
        if(v.rol==='AGENTE')await this.agentes.actualizar(this.id,{...local,estadoOperativo:v.estadoOperativo||undefined});
        else await this.brokers.actualizar(this.id,local);
        await this.router.navigate([v.rol==='AGENTE'?'/agentes':'/brokers']);
      }else{
        const limite=new Date(v.venceEn);
        if(!v.correoCuenta.trim()||!v.venceEn||Number.isNaN(limite.getTime())||!v.motivo.trim()){
          this.error.set('Indica el correo de la cuenta, el vencimiento y el motivo de la invitación.');return;
        }
        const persona=v.nombre.trim()||v.telefono.trim()||v.correoLaboral.trim()
          ? {nombre:local.nombre,telefono:local.telefono||undefined,correoLaboral:local.correoLaboral||undefined}:undefined;
        this.emitida.set(await this.membresias.invitar({correoCuenta:v.correoCuenta.trim(),persona,
          rolPrincipal:v.rol==='AGENTE'||v.rol==='BROKER'?v.rol:null,administrar:v.administrar,
          idBrokerSupervisor:v.rol==='AGENTE'?Number(v.supervisor)||undefined:undefined,
          zona:v.zona.trim()||undefined,estadoOperativo:v.rol==='AGENTE'?v.estadoOperativo||undefined:undefined,
          venceEn:limite.toISOString(),motivo:v.motivo.trim()},this.claveInvitacion));
      }
    }catch(e){this.error.set(e instanceof ApiError?e.message:'No se pudo completar la actuación.');}
    finally{this.guardando.set(false);}
  }
  protected volver(): void {void this.router.navigate(['/seguridad']);}
}
