/**
 * Rubros comerciales sugeridos. Porta `EnumCatalog.RubrosComerciales` del
 * Blazor.
 *
 * **No es un catálogo del cable.** El backend guarda `rubroComercial` como
 * texto libre: no hay `CHECK` ni enum detrás, así que un valor fuera de esta
 * lista es perfectamente legal —lo escribió alguien por la v1, por Postman o
 * por una carga—. De ahí {@link rubrosCon}: al editar hay que ofrecer el valor
 * actual aunque no esté en la lista, o el selector lo cambiaría en silencio por
 * el primero. El Blazor ya lo resolvía así y conviene no perderlo.
 */
export const RUBROS_COMERCIALES: readonly string[] = [
  'Restaurante / Cafe',
  'Cafeteria / Postres',
  'Moda / Boutique',
  'Retail',
  'Minimarket / Bodega',
  'Panaderia / Pasteleria',
  'Farmacia / Botica',
  'Salud / Consultorio',
  'Belleza / Barberia',
  'Gimnasio / Fitness',
  'Educacion / Academia',
  'Oficina administrativa',
  'Servicios profesionales',
  'Ferreteria',
  'Veterinaria',
  'Tecnologia / Electronica',
  'Muebles / Decoracion',
  'Almacen / Deposito',
  'Logistica ligera',
  'Automotriz',
  'Mascotas',
  'Entretenimiento',
  'Comida rapida',
  'Dark kitchen',
  'Otro rubro comercial',
];

/**
 * La lista sugerida más el valor actual si no estuviera en ella, para que
 * editar un cliente nunca le cambie el rubro sin que nadie lo pida.
 */
export function rubrosCon(valorActual: string | undefined | null): string[] {
  const actual = (valorActual ?? '').trim();
  if (!actual || RUBROS_COMERCIALES.includes(actual)) {
    return [...RUBROS_COMERCIALES];
  }
  return [actual, ...RUBROS_COMERCIALES];
}
