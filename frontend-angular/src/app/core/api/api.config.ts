/**
 * Base del API v2 (Spring). Misma base de ruta que el backend Jakarta
 * (/controllocal/Api) pero en el puerto 8090: el contrato está congelado
 * y el corte entre backends se hace por ruta en el proxy del Strangler.
 */
export const API_BASE_URL = 'http://localhost:8090/controllocal/Api';
