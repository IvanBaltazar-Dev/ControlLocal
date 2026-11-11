import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { API_BASE_URL } from '../api/api.config';
import { AuthService, NAVEGACION_COMPLETA } from './auth.service';
import { ContextoAcceso, LoginResponse, RolSesion, Sesion, SesionEfectiva } from './sesion.model';

const CLAVE = 'controllocal.sesion.v3';
const OPCION: ContextoAcceso = { referencia: 'ofrecida-por-core', idOrganizacion: 7,
  idPersona: 2, idRolOperativo: 10, rol: 'BROKER', modalidad: 'EMPRESARIAL', nombreOrganizacion: 'Organización de prueba' };
function cable(rol: RolSesion = 'IDENTIDAD'): LoginResponse {
  return { token: 'token-prueba', expiraEnSegundos: 1800, rol, idUsuario: rol === 'IDENTIDAD' ? 0 : 2,
    idDominio: rol === 'IDENTIDAD' ? 0 : 10, nombre: 'Profesional', usuario: 'cuenta-global',
    expiraEn: new Date(Date.now()+1800000).toISOString(), contextoRequerido: rol === 'IDENTIDAD',
    contextos: [OPCION], idOrganizacion: rol === 'IDENTIDAD' ? 0 : 7,
    idCuenta: 99, revisionContexto: rol === 'IDENTIDAD' ? 0 : 1, modalidad: rol === 'IDENTIDAD' ? null : 'EMPRESARIAL' };
}
function perfil(rol: RolSesion = 'IDENTIDAD'): SesionEfectiva {
  const c = cable(rol);
  return { rol, usuario: c.usuario, idPersona: c.idUsuario, idDominio: c.idDominio,
    idOrganizacion: c.idOrganizacion, idCuenta: c.idCuenta, referenciaSesion: 'sesion-core',
    revisionContexto: c.revisionContexto, contextoRef: rol === 'IDENTIDAD' ? null : OPCION.referencia,
    modalidad: c.modalidad, contextoRequerido: c.contextoRequerido,
    debeCambiarContrasena: false, debeEnrolarMfa: false };
}
function guardada(rol: RolSesion = 'AGENTE'): Sesion {
  return { ...cable(rol), referenciaSesion: 'sesion-core', contextoRef: rol === 'IDENTIDAD' ? null : OPCION.referencia };
}
function tick(): Promise<void> { return new Promise(resolve => setTimeout(resolve,0)); }

describe('AuthService contextual', () => {
  let http: HttpTestingController;
  const navegar = jasmine.createSpy('navigate');
  const completa = jasmine.createSpy('navegacionCompleta');
  const router = { navigate: navegar, url: '/' };
  beforeEach(() => {
    localStorage.clear(); navegar.calls.reset(); completa.calls.reset(); navegar.and.resolveTo(true); router.url = '/';
    TestBed.configureTestingModule({ providers: [provideHttpClient(),provideHttpClientTesting(),
      { provide: Router, useValue: router }, { provide: NAVEGACION_COMPLETA, useValue: completa }] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); localStorage.clear(); });

  it('retira la sesión legada sin traducir ADMIN a otra atribución', () => {
    localStorage.setItem('controllocal.sesion.v2',JSON.stringify({ ...guardada(), rol:'ADMIN' }));
    const a = TestBed.inject(AuthService);
    expect(a.sesion()).toBeNull(); expect(a.token).toBeNull();
    expect(localStorage.getItem('controllocal.sesion.v2')).toBeNull();
  });
  it('descarta una sesión vencida o sin metadatos contextuales', () => {
    localStorage.setItem(CLAVE,JSON.stringify({ ...guardada(), expiraEn:new Date(Date.now()-1000).toISOString() }));
    expect(TestBed.inject(AuthService).sesion()).toBeNull();
  });
  it('limpia una sola vez ante cierres concurrentes y conserva el destino seguro', () => {
    localStorage.setItem(CLAVE,JSON.stringify(guardada())); router.url='/propiedades?pagina=3';
    const a=TestBed.inject(AuthService); a.cerrarSesion(); a.cerrarSesion();
    expect(a.sesion()).toBeNull(); expect(localStorage.getItem(CLAVE)).toBeNull();
    expect(navegar).toHaveBeenCalledOnceWith(['/login'],{queryParams:{volverA:'/propiedades?pagina=3'},replaceUrl:true});
  });
  it('logout conserva el token hasta avisar al servidor y limpia aunque falle la red', async () => {
    localStorage.setItem(CLAVE,JSON.stringify(guardada())); const a=TestBed.inject(AuthService);
    const salida=a.salir(); expect(a.token).toBe('token-prueba');
    http.expectOne(`${API_BASE_URL}/auth/logout`).flush({error:'red'},{status:500,statusText:'Error'});
    await salida; expect(a.token).toBeNull(); expect(navegar).toHaveBeenCalledOnceWith(['/login'],{replaceUrl:true});
  });
  it('un desafío MFA todavía no crea sesión ni consulta un contexto', async () => {
    const a=TestBed.inject(AuthService); const entrada=a.login({correo:'personal@brox.test',contrasena:'clave'});
    http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`).flush({desafio:'reto',expiraEn:'2099-01-01',metodo:'TOTP'},{status:202,statusText:'Accepted'});
    expect((await entrada).tipo).toBe('desafio'); expect(a.token).toBeNull(); http.expectNone(`${API_BASE_URL}/sesion`);
  });
  it('login abre IDENTIDAD y no elige ni siquiera la única opción ofrecida', async () => {
    const a=TestBed.inject(AuthService); const entrada=a.login({correo:'personal@brox.test',contrasena:'clave'});
    http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`).flush(cable()); await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush(perfil()); await entrada;
    expect(a.sesion()?.rol).toBe('IDENTIDAD'); expect(a.contextoRequerido()).toBeTrue(); expect(a.tieneContexto()).toBeFalse();
    expect(a.sesion()?.idCuenta).toBe(99); expect(a.sesion()?.revisionContexto).toBe(0);
    http.expectNone(`${API_BASE_URL}/sesion/contexto`); expect(completa).not.toHaveBeenCalled();
  });
  it('un fallo técnico de GET sesion elimina la sesión y no fabrica una banda', async () => {
    const a=TestBed.inject(AuthService); const entrada=a.login({correo:'personal@brox.test',contrasena:'clave'});
    const rechazo=expectAsync(entrada).toBeRejected();
    http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`).flush(cable()); await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush({error:'fallo'},{status:500,statusText:'Error'}); await rechazo;
    expect(a.sesion()).toBeNull(); expect(localStorage.getItem(CLAVE)).toBeNull();
  });
  it('la obligación MFA conserva únicamente IDENTIDAD y el acceso al enrolamiento', async () => {
    const a=TestBed.inject(AuthService); const entrada=a.login({correo:'personal@brox.test',contrasena:'clave'});
    http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`).flush(cable()); await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush({...perfil(),debeEnrolarMfa:true}); await entrada;
    expect(a.debeEnrolarMfa()).toBeTrue(); expect(a.sesion()?.rol).toBe('IDENTIDAD'); expect(a.tieneContexto()).toBeFalse();
  });
  it('la contraseña obligatoria no permite entrar al negocio', async () => {
    const a=TestBed.inject(AuthService); const entrada=a.login({correo:'personal@brox.test',contrasena:'clave'});
    http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`).flush(cable()); await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush({...perfil(),debeCambiarContrasena:true}); await entrada;
    expect(a.debeCambiarContrasena()).toBeTrue(); expect(a.tieneContexto()).toBeFalse();
  });
  it('canjear el segundo factor también comprueba la sesión actual', async () => {
    const a=TestBed.inject(AuthService); const entrada=a.verificarSegundoFactor('reto','123456');
    http.expectOne(`${API_BASE_URL}/auth/mfa/verificar`).flush(cable()); await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush(perfil()); await entrada;
    expect(a.sesion()?.referenciaSesion).toBe('sesion-core'); expect(a.tieneContexto()).toBeFalse();
  });
  it('consulta las opciones actuales sin construir otro tenant o rol', async () => {
    localStorage.setItem(CLAVE,JSON.stringify(guardada('IDENTIDAD'))); const a=TestBed.inject(AuthService);
    const consulta=a.contextosDisponibles(); http.expectOne(`${API_BASE_URL}/sesion/contextos`).flush([OPCION]);
    expect(await consulta).toEqual([OPCION]); expect(a.sesion()?.idOrganizacion).toBe(0);
  });
  it('selecciona sólo la referencia y reinicia la página con todos los IDs confirmados', async () => {
    localStorage.setItem(CLAVE,JSON.stringify(guardada('IDENTIDAD'))); const a=TestBed.inject(AuthService);
    const seleccion=a.seleccionarContexto(OPCION.referencia,'/propiedades?pagina=2');
    const post=http.expectOne(`${API_BASE_URL}/sesion/contexto`); expect(post.request.body).toEqual({referencia:OPCION.referencia});
    post.flush(cable('BROKER')); await tick(); http.expectOne(`${API_BASE_URL}/sesion`).flush(perfil('BROKER'));
    const s=await seleccion;
    expect([s.idCuenta,s.idOrganizacion,s.idUsuario,s.idDominio,s.revisionContexto]).toEqual([99,7,2,10,1]);
    expect(s.contextoRef).toBe(OPCION.referencia); expect(s.modalidad).toBe('EMPRESARIAL');
    expect(completa).toHaveBeenCalledOnceWith('/propiedades?pagina=2'); expect(a.sesion()?.rol).toBe('IDENTIDAD');
    expect(JSON.parse(localStorage.getItem(CLAVE)!).rol).toBe('BROKER');
  });
  it('rechaza metadatos mezclados y no navega con una selección no comprobada', async () => {
    localStorage.setItem(CLAVE,JSON.stringify(guardada('IDENTIDAD'))); const a=TestBed.inject(AuthService);
    const seleccion=a.seleccionarContexto(OPCION.referencia); const rechazo=expectAsync(seleccion).toBeRejected();
    http.expectOne(`${API_BASE_URL}/sesion/contexto`).flush(cable('BROKER')); await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush({...perfil('BROKER'),idOrganizacion:99}); await rechazo;
    expect(a.sesion()).toBeNull(); expect(completa).not.toHaveBeenCalled();
  });
  it('renovar vuelve a comprobar organización revisión y atribución', async () => {
    localStorage.setItem(CLAVE,JSON.stringify(guardada('AGENTE'))); const a=TestBed.inject(AuthService);
    const renovada=a.renovar(); http.expectOne(`${API_BASE_URL}/auth/renovar`).flush(cable('AGENTE')); await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush(perfil('AGENTE')); await renovada;
    expect(a.sesion()?.idOrganizacion).toBe(7); expect(a.tieneContexto()).toBeTrue();
  });
  it('un contexto de gobierno pendiente de MFA navega sólo al enrolamiento confirmado por Core', async () => {
    localStorage.setItem(CLAVE,JSON.stringify(guardada('IDENTIDAD')));const a=TestBed.inject(AuthService);
    const seleccion=a.seleccionarContexto(OPCION.referencia,'/seguridad');
    const login={...cable('TENANT_ADMIN'),contextos:[{...OPCION,rol:'TENANT_ADMIN'}]};
    http.expectOne(`${API_BASE_URL}/sesion/contexto`).flush(login);await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush({...perfil('TENANT_ADMIN'),debeEnrolarMfa:true});await seleccion;
    expect(completa).toHaveBeenCalledOnceWith('/enrolar-mfa?obligatorio=1');
  });
  it('una respuesta sin restricciones explícitas se rechaza, sin sesión provisional persistida', async () => {
    const a=TestBed.inject(AuthService);const entrada=a.login({correo:'personal@brox.test',contrasena:'clave'});
    const rechazo=expectAsync(entrada).toBeRejected();
    http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`).flush(cable());await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush({...perfil(),debeEnrolarMfa:undefined});await rechazo;
    expect(a.sesion()).toBeNull();expect(localStorage.getItem(CLAVE)).toBeNull();
  });

});
