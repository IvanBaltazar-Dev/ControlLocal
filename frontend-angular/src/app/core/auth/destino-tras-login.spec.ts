import { destinoSeguro } from './destino-tras-login';

describe('destinoSeguro', () => {
  it('acepta rutas internas conservando sus query params', () => {
    expect(destinoSeguro('/locales?estado=D&page=3')).toBe('/locales?estado=D&page=3');
    expect(destinoSeguro('/solicitudes/42')).toBe('/solicitudes/42');
  });

  it('rechaza destinos externos: seria un redirector abierto', () => {
    // El valor llega por la barra de direcciones, asi que cualquiera puede
    // mandar un enlace que empieza en el dominio y acaba en otro sitio.
    expect(destinoSeguro('https://otro.sitio')).toBeNull();
    expect(destinoSeguro('//otro.sitio')).toBeNull();
    expect(destinoSeguro('/\\otro.sitio')).toBeNull();
    expect(destinoSeguro('javascript:alert(1)')).toBeNull();
  });

  it('rechaza volver al propio login, que seria un bucle', () => {
    expect(destinoSeguro('/login')).toBeNull();
    expect(destinoSeguro('/login?aviso=x')).toBeNull();
  });

  it('trata lo vacio y lo ausente como "sin destino"', () => {
    expect(destinoSeguro(null)).toBeNull();
    expect(destinoSeguro(undefined)).toBeNull();
    expect(destinoSeguro('   ')).toBeNull();
  });

  it('no anota la raiz: ya es el destino por defecto', () => {
    expect(destinoSeguro('/')).toBeNull();
  });
});
