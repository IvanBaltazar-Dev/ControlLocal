/** Query param que recuerda a dónde volver después de reautenticarse. */
export const PARAM_DESTINO = 'volverA';

/**
 * Filtra el destino que viaja en la URL antes de navegar a él.
 *
 * Hace falta porque ese valor **entra por la barra de direcciones**: cualquiera
 * puede mandar un enlace a `/login?volverA=…` con lo que quiera dentro. Sin
 * este filtro sería un redirector abierto — un enlace que empieza en el dominio
 * de la aplicación y termina en otro sitio, que es la forma clásica de dar
 * apariencia legítima a una página de phishing.
 *
 * Solo pasan rutas internas. Se rechazan:
 * - lo que no empieza por `/` (`https://otro.sitio`, `javascript:…`);
 * - `//otro.sitio` y `/\otro.sitio`, que el navegador resuelve como absolutas
 *   aunque empiecen por barra;
 * - el propio `/login`, que devolvería al usuario al punto de partida;
 * - `/` a secas, que ya es el destino por defecto: anotarlo solo ensuciaría la
 *   URL con un parámetro que no cambia nada.
 */
export function destinoSeguro(valor: string | null | undefined): string | null {
  const ruta = (valor ?? '').trim();
  if (!ruta.startsWith('/') || ruta === '/') {
    return null;
  }
  if (ruta.startsWith('//') || ruta.startsWith('/\\')) {
    return null;
  }
  if (ruta === '/login' || ruta.startsWith('/login?') || ruta.startsWith('/login/')) {
    return null;
  }
  return ruta;
}
