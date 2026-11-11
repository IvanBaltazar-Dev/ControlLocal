import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';

import { Agente, AgentesService, ResumenAgentes } from '../../core/api/agentes.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion } from '../../core/auth/sesion.model';
import { Agentes } from './agentes';

describe('Agentes — entrada por finalidad', () => {
  const AGENTE: Agente = {
    id: 30, nombre: 'Valeria Mora', codigoAgente: 'AG-30',
    estadoAdministrativo: 'A', estadoOperativo: 'D',
  };
  const RESUMEN: ResumenAgentes = {
    total: 1, activos: 1, inactivos: 0, disponibles: 1,
    ocupados: 0, vacaciones: 0, suspendidos: 0, zonas: [],
  };
  let router: jasmine.SpyObj<Router>;

  it('gobierno consulta el nombre como texto y sólo edita mediante su acción explícita', async () => {
    const fixture = await montar('TENANT_ADMIN');
    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('tbody td')?.textContent).toContain('Valeria Mora');
    expect(raiz.querySelector('tbody button.enlace')).toBeNull();
    expect(router.navigate).not.toHaveBeenCalled();

    const acciones = Array.from(raiz.querySelectorAll<HTMLButtonElement>('tbody .acciones-fila button'));
    expect(acciones.map((b) => b.textContent?.trim())).toEqual(['Editar', 'Nueva invitación']);
    acciones[0].click();
    expect(router.navigate).toHaveBeenCalledOnceWith(['/agentes', 30, 'editar']);
    acciones[1].click();
    expect(router.navigate).toHaveBeenCalledWith(['/agentes/nuevo']);
  });

  it('el broker conserva el enlace del nombre a la ficha comercial', async () => {
    const fixture = await montar('BROKER');
    const raiz = fixture.nativeElement as HTMLElement;
    const nombre = raiz.querySelector<HTMLButtonElement>('tbody button.enlace');
    expect(nombre?.textContent).toContain('Valeria Mora');
    expect(raiz.querySelector('tbody .acciones-fila button')).toBeNull();
    nombre!.click();
    expect(router.navigate).toHaveBeenCalledOnceWith(['/agentes', 30]);
  });

  async function montar(rol: RolSesion): Promise<ComponentFixture<Agentes>> {
    TestBed.resetTestingModule();
    const api = jasmine.createSpyObj<AgentesService>('AgentesService', ['pagina$', 'resumen$']);
    api.pagina$.and.returnValue(of({items: [AGENTE], totalRecords: 1, page: 1, pageSize: 20}));
    api.resumen$.and.returnValue(of(RESUMEN));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
    await TestBed.configureTestingModule({
      imports: [Agentes],
      providers: [
        {provide: AgentesService, useValue: api},
        {provide: AuthService, useValue: {sesion: signal({rol})}},
        {provide: ActivatedRoute, useValue: {queryParamMap: of(convertToParamMap({}))}},
        {provide: Router, useValue: router},
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(Agentes);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});
