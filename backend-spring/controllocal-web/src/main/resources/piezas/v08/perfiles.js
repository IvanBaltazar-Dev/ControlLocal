// Generado por herramientas/derivar-perfiles.cjs. No editar a mano.
// Parte 3 del contrato V08: configuración de presentación por perfil (criterios literales de
// v07/matriz-perfiles.csv más la estructura que usa el renderer) y registro de huecos.
(function (raiz) {
  var perfiles = {
 "version": "V08",
 "derivadoEl": "2026-10-16",
 "fuente": "v07/matriz-perfiles.csv",
 "fuenteSha256": "6f50990ee993c55597ff547b42ebe70b73c69034065426204cafd4d839b87f1b",
 "orden": [
  "local",
  "oficina",
  "departamento",
  "casa",
  "casa_campo",
  "casa_playa",
  "terreno",
  "almacen",
  "estacionamiento",
  "edificio",
  "otro"
 ],
 "perfiles": {
  "local": {
   "id": "local",
   "rotulo": "Local comercial",
   "tipo": "L",
   "tipoObservado": "LOCAL",
   "naturaleza": "BASE",
   "areas": [
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    },
    {
     "ref": "metraje_construido",
     "rotulo": "Área construida"
    },
    {
     "ref": "H-01",
     "rotulo": "Área ocupada"
    },
    {
     "ref": "metraje_arrendable",
     "rotulo": "Área arrendable"
    }
   ],
   "caracteristicas": [
    {
     "ref": "frente",
     "rotulo": "Frente",
     "icono": "frente",
     "formato": "metros",
     "grupo": "dimensiones"
    },
    {
     "ref": "altura_libre",
     "rotulo": "Altura libre",
     "icono": "altura",
     "formato": "metros",
     "grupo": "dimensiones"
    },
    {
     "ref": "H-16",
     "rotulo": "Baños",
     "icono": "bano",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "tipo_acceso",
     "rotulo": "Acceso",
     "icono": "puerta",
     "formato": "lista",
     "grupo": "edificio"
    },
    {
     "ref": "piso",
     "rotulo": "Piso",
     "icono": "piso",
     "formato": "piso",
     "grupo": "edificio"
    }
   ],
   "rasgos": [
    {
     "ref": "en_esquina",
     "rotulo": "En esquina",
     "icono": "esquina"
    }
   ],
   "condiciones": [
    {
     "ref": "nivel_implementacion",
     "rotulo": "Implementación",
     "formato": "lista"
    },
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    },
    {
     "ref": "cuota_mantenimiento",
     "rotulo": "Mantenimiento",
     "formato": "mantenimiento",
     "grupo": "costos"
    },
    {
     "ref": "entrega_desocupado",
     "rotulo": "Entrega",
     "formato": "rasgo",
     "texto": "Se entrega desocupado",
     "no": "No se entrega desocupado"
    }
   ],
   "documentos": [
    {
     "ref": "independizado",
     "rotulo": "Independización",
     "si": "Unidad independizada",
     "no": "Unidad sin independizar"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    },
    {
     "ref": "certificado_itse",
     "rotulo": "Certificado ITSE",
     "formato": "lista"
    },
    {
     "ref": "apto_licencia_funcionamiento",
     "rotulo": "Licencia de funcionamiento",
     "si": "Apto para licencia de funcionamiento según el documento revisado",
     "no": "No apto para licencia de funcionamiento según el documento revisado"
    }
   ],
   "cifras": [
    "@area",
    "frente",
    "altura_libre",
    "@via",
    "piso"
   ],
   "via": "tipo_via_acceso",
   "esquina": "en_esquina",
   "usoYConstruccion": "SI_PERTINENTE",
   "portadaSinFotos": "mapa",
   "reservasFoto": [
    "Fachada y acceso",
    "Espacio principal",
    "Distribución",
    "Instalaciones",
    "Entorno, aparte del local"
   ],
   "ambientesSugeridos": [
    "Fachada/acceso o espacio principal",
    "distribución",
    "instalaciones",
    "entorno separado"
   ],
   "criterios": {
    "superficies_prioritarias": "Área ofrecida con definición; área construida/techada o arrendable acreditada; terreno si integra el objeto",
    "distribucion_prioritaria": "Nivel/piso; accesos; frente; altura libre; baños propios/comunes separados; ambientes declarados",
    "ubicacion_prioritaria": "Vía del inmueble; esquina; entorno comercial con naturaleza; uso actual y zonificación diferenciados",
    "condiciones_pertinentes": "Composición; implementación; mantenimiento; servicios; condiciones y usos admitidos por titular con alcance",
    "foco_documental": "Resumen autorizado de uso/restricciones y documentos pertinentes; no licencia o giro inferidos",
    "orden_fotografias": "Fachada/acceso o espacio principal; distribución; instalaciones; entorno separado",
    "restricciones_semanticas": "No inferir licencia por comercio cercano; no aforo por m²; no dormitorios por defecto",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Priorizar actividad compatible acreditada; no equivale licencia; parámetros de nueva obra sólo si corresponden al objeto.",
    "fuente_tipo": "AtributosGobernados.java:68",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "oficina": {
   "id": "oficina",
   "rotulo": "Oficina",
   "tipo": "O",
   "tipoObservado": "OFICINA",
   "naturaleza": "BASE",
   "areas": [
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    },
    {
     "ref": "metraje_construido",
     "rotulo": "Área construida"
    },
    {
     "ref": "H-01",
     "rotulo": "Área ocupada"
    },
    {
     "ref": "metraje_arrendable",
     "rotulo": "Área arrendable"
    }
   ],
   "caracteristicas": [
    {
     "ref": "piso",
     "rotulo": "Piso",
     "icono": "piso",
     "formato": "piso",
     "grupo": "edificio"
    },
    {
     "ref": "ambientes",
     "rotulo": "Ambientes",
     "icono": "oficina",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "H-16",
     "rotulo": "Baños",
     "icono": "bano",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "estacionamientos",
     "rotulo": "Estacionamientos",
     "icono": "auto",
     "formato": "cochera",
     "grupo": "cochera"
    },
    {
     "ref": "ascensores",
     "rotulo": "Ascensores",
     "icono": "ascensor",
     "formato": "entero",
     "grupo": "edificio"
    }
   ],
   "rasgos": [
    {
     "ref": "recepcion_edificio",
     "rotulo": "Recepción en el edificio",
     "icono": "puerta",
     "grupo": "edificio"
    },
    {
     "ref": "fibra_optica",
     "rotulo": "Fibra óptica",
     "icono": "red",
     "grupo": "servicios"
    }
   ],
   "condiciones": [
    {
     "ref": "modalidad_precio",
     "rotulo": "Base del alquiler",
     "formato": "lista",
     "grupo": "precio"
    },
    {
     "ref": "estacionamientos_incluidos",
     "rotulo": "Estacionamientos incluidos",
     "formato": "incluidos",
     "grupo": "precio"
    },
    {
     "ref": "precio_estacionamiento_adicional",
     "rotulo": "Estacionamiento adicional",
     "formato": "importe",
     "grupo": "costos"
    },
    {
     "ref": "garantia_meses",
     "rotulo": "Garantía",
     "formato": "meses"
    },
    {
     "ref": "adelanto_meses",
     "rotulo": "Adelanto",
     "formato": "meses"
    },
    {
     "ref": "plazo_minimo_meses",
     "rotulo": "Plazo mínimo",
     "formato": "meses"
    },
    {
     "ref": "cuota_mantenimiento",
     "rotulo": "Mantenimiento",
     "formato": "mantenimiento",
     "grupo": "costos"
    },
    {
     "ref": "mantenimiento_a_cargo_de",
     "rotulo": "Quién paga el mantenimiento",
     "formato": "lista",
     "frase": true,
     "grupo": "costos"
    },
    {
     "ref": "igv_arrendamiento",
     "rotulo": "IGV",
     "formato": "lista",
     "frase": true,
     "grupo": "costos"
    },
    {
     "ref": "se_entrega_implementado",
     "rotulo": "Implementación",
     "formato": "rasgo",
     "texto": "Se entrega implementada"
    }
   ],
   "documentos": [
    {
     "ref": "independizado",
     "rotulo": "Independización",
     "si": "Unidad independizada",
     "no": "Unidad sin independizar"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    },
    {
     "ref": "certificado_itse",
     "rotulo": "Certificado ITSE",
     "formato": "lista"
    },
    {
     "ref": "apto_licencia_funcionamiento",
     "rotulo": "Licencia de funcionamiento",
     "si": "Apto para licencia de funcionamiento según el documento revisado",
     "no": "No apto para licencia de funcionamiento según el documento revisado"
    }
   ],
   "cifras": [
    "metraje_arrendable|@area",
    "ambientes",
    "H-16",
    "piso",
    "estacionamientos"
   ],
   "via": "H-25",
   "esquina": "en_esquina",
   "usoYConstruccion": "SOLO_SI_RELEVANTE",
   "reservasFoto": [
    "Espacio principal",
    "Salas",
    "Acceso y edificio",
    "Servicios",
    "Planos, si existen"
   ],
   "ambientesSugeridos": [
    "Espacio principal",
    "salas",
    "acceso/edificio",
    "servicios",
    "planos sólo existentes"
   ],
   "criterios": {
    "superficies_prioritarias": "Área de unidad con definición; superficie arrendable si acreditada; terraza separada si existe",
    "distribucion_prioritaria": "Piso; ambientes/salas declarados; baños propios/comunes; altura libre; ascensor y funcionamiento separados",
    "ubicacion_prioritaria": "Vía; zona; acceso; posición declarada; servicios públicos corroborados; zonificación sólo sustentada",
    "condiciones_pertinentes": "Implementación; estacionamientos/componentes; mantenimiento moneda/período; servicios y horario si constan",
    "foco_documental": "Resumen autorizado de unidad/restricciones; no seguridad técnica ni habilitación general",
    "orden_fotografias": "Espacio principal; salas; acceso/edificio; servicios; planos sólo existentes",
    "restricciones_semanticas": "No convertir área en puestos de trabajo; piso de oficina no pisos totales; no valorar mantenimiento por descuento automático",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Sólo si pertinente al objeto y documentado; parámetro del predio no concede derecho individual de ampliar una unidad.",
    "fuente_tipo": "AtributosGobernados.java:69",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "departamento": {
   "id": "departamento",
   "rotulo": "Departamento",
   "tipo": "D",
   "tipoObservado": "DEPARTAMENTO",
   "naturaleza": "BASE",
   "areas": [
    {
     "ref": "metraje_construido",
     "rotulo": "Área construida"
    },
    {
     "ref": "H-01",
     "rotulo": "Área ocupada"
    },
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    },
    {
     "ref": "area_terraza",
     "rotulo": "Terraza"
    },
    {
     "ref": "area_jardin_patio",
     "rotulo": "Jardín o patio"
    }
   ],
   "caracteristicas": [
    {
     "ref": "piso",
     "rotulo": "Piso",
     "icono": "piso",
     "formato": "piso",
     "grupo": "edificio"
    },
    {
     "ref": "dormitorios",
     "rotulo": "Dormitorios",
     "icono": "cama",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "banos",
     "rotulo": "Baños completos",
     "icono": "bano",
     "formato": "banos",
     "grupo": "distribucion"
    },
    {
     "ref": "estacionamientos",
     "rotulo": "Cochera",
     "icono": "auto",
     "formato": "cochera",
     "grupo": "cochera"
    },
    {
     "ref": "ascensores",
     "rotulo": "Ascensores",
     "icono": "ascensor",
     "formato": "entero",
     "grupo": "edificio"
    },
    {
     "ref": "antiguedad_anios",
     "rotulo": "Antigüedad",
     "icono": "reloj",
     "formato": "anios",
     "grupo": "edificio"
    }
   ],
   "rasgos": [
    {
     "ref": "jardin",
     "rotulo": "Jardín",
     "icono": "jardin"
    },
    {
     "ref": "terraza",
     "rotulo": "Terraza",
     "icono": "terraza"
    },
    {
     "ref": "balcon",
     "rotulo": "Balcón",
     "icono": "terraza"
    },
    {
     "ref": "vista",
     "rotulo": "Vista",
     "icono": "vista",
     "formato": "lista"
    },
    {
     "ref": "amoblado",
     "rotulo": "Amoblado",
     "icono": "casa"
    }
   ],
   "condiciones": [
    {
     "ref": "cuota_mantenimiento",
     "rotulo": "Mantenimiento",
     "formato": "mantenimiento",
     "grupo": "costos"
    },
    {
     "ref": "H-05",
     "rotulo": "Cochera en el precio",
     "formato": "rasgo",
     "texto": "La cochera está incluida en el precio",
     "no": "La cochera no está incluida en el precio",
     "siFalta": "si la cochera está incluida en el precio",
     "grupo": "precio"
    },
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    },
    {
     "ref": "entrega_desocupado",
     "rotulo": "Entrega",
     "formato": "rasgo",
     "texto": "Se entrega desocupado",
     "no": "No se entrega desocupado"
    },
    {
     "ref": "apto_credito_hipotecario",
     "rotulo": "Crédito hipotecario",
     "formato": "rasgo",
     "texto": "Acepta crédito hipotecario",
     "no": "No acepta crédito hipotecario"
    },
    {
     "ref": "estacionamientos_incluidos",
     "rotulo": "Estacionamientos incluidos",
     "formato": "incluidos",
     "grupo": "precio"
    },
    {
     "ref": "garantia_meses",
     "rotulo": "Garantía",
     "formato": "meses"
    },
    {
     "ref": "adelanto_meses",
     "rotulo": "Adelanto",
     "formato": "meses"
    },
    {
     "ref": "mascotas_aceptadas",
     "rotulo": "Mascotas",
     "formato": "rasgo",
     "texto": "Acepta mascotas",
     "no": "No acepta mascotas"
    },
    {
     "ref": "se_ofrece_amoblado",
     "rotulo": "Amoblado",
     "formato": "rasgo",
     "texto": "Se ofrece amoblado"
    }
   ],
   "documentos": [
    {
     "ref": "independizado",
     "rotulo": "Independización",
     "si": "Unidad independizada",
     "no": "Unidad sin independizar"
    },
    {
     "ref": "declaratoria_fabrica",
     "rotulo": "Declaratoria de fábrica",
     "si": "Con declaratoria de fábrica",
     "no": "Sin declaratoria de fábrica"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    }
   ],
   "cifras": [
    "@area",
    "dormitorios",
    "banos",
    "estacionamientos",
    "piso"
   ],
   "via": "H-25",
   "esquina": null,
   "usoYConstruccion": "SOLO_SI_RELEVANTE",
   "reservasFoto": [
    "Sala o espacio principal",
    "Distribución",
    "Dormitorios",
    "Terraza propia",
    "Edificio y acceso",
    "Complementos"
   ],
   "ambientesSugeridos": [
    "Sala/espacio representativo",
    "distribución",
    "dormitorios",
    "terraza propia",
    "edificio/acceso",
    "complementos"
   ],
   "criterios": {
    "superficies_prioritarias": "Área de la unidad con definición; ocupada/exclusiva/techada si constan; terraza/abierta/componentes separados",
    "distribucion_prioritaria": "Piso de unidad; niveles internos; dormitorios; baños completos y medios; cochera unidad/capacidad; ascensor",
    "ubicacion_prioritaria": "Zona aproximada; vía del inmueble; acceso; entorno público corroborado; esquina sólo declarada",
    "condiciones_pertinentes": "Cochera/depósito inclusión y modalidad; mantenimiento con moneda/período; servicios; edificio",
    "foco_documental": "Resumen documental autorizado y alcance de unidad; no independización o crédito inferidos",
    "orden_fotografias": "Sala/espacio representativo; distribución; dormitorios; terraza propia; edificio/acceso; complementos",
    "restricciones_semanticas": "No sumar cochera al denominador residencial sin definición; no intercambiar áreas; no transformar primer piso en accesibilidad certificada",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Sólo si pertinente al objeto y documentado; parámetro del predio no concede derecho individual de ampliar una unidad.",
    "fuente_tipo": "AtributosGobernados.java:70",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "casa": {
   "id": "casa",
   "rotulo": "Casa",
   "tipo": "C",
   "tipoObservado": "CASA",
   "naturaleza": "BASE",
   "areas": [
    {
     "ref": "area_terreno",
     "rotulo": "Área de terreno"
    },
    {
     "ref": "metraje_construido",
     "rotulo": "Área construida"
    },
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    },
    {
     "ref": "area_jardin_patio",
     "rotulo": "Jardín o patio"
    },
    {
     "ref": "area_terraza",
     "rotulo": "Terraza"
    }
   ],
   "caracteristicas": [
    {
     "ref": "pisos_edificacion",
     "rotulo": "Pisos de la casa",
     "icono": "piso",
     "formato": "pisos",
     "grupo": "distribucion"
    },
    {
     "ref": "dormitorios",
     "rotulo": "Dormitorios",
     "icono": "cama",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "H-08",
     "rotulo": "Habitaciones",
     "icono": "cama",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "banos",
     "rotulo": "Baños completos",
     "icono": "bano",
     "formato": "banos",
     "grupo": "distribucion"
    },
    {
     "ref": "estacionamientos",
     "rotulo": "Cochera",
     "icono": "auto",
     "formato": "cochera",
     "grupo": "cochera"
    },
    {
     "ref": "antiguedad_anios",
     "rotulo": "Antigüedad",
     "icono": "reloj",
     "formato": "anios",
     "grupo": "edificio"
    }
   ],
   "rasgos": [
    {
     "ref": "piscina",
     "rotulo": "Piscina",
     "icono": "piscina"
    },
    {
     "ref": "jardin",
     "rotulo": "Jardín",
     "icono": "jardin"
    },
    {
     "ref": "terraza",
     "rotulo": "Terraza",
     "icono": "terraza"
    },
    {
     "ref": "patio",
     "rotulo": "Patio",
     "icono": "jardin"
    }
   ],
   "condiciones": [
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    },
    {
     "ref": "entrega_desocupado",
     "rotulo": "Entrega",
     "formato": "rasgo",
     "texto": "Se entrega desocupada",
     "no": "No se entrega desocupada"
    },
    {
     "ref": "apto_credito_hipotecario",
     "rotulo": "Crédito hipotecario",
     "formato": "rasgo",
     "texto": "Acepta crédito hipotecario",
     "no": "No acepta crédito hipotecario"
    },
    {
     "ref": "acepta_financiamiento_directo",
     "rotulo": "Financiamiento",
     "formato": "rasgo",
     "texto": "El propietario financia parte del saldo"
    },
    {
     "ref": "cuota_mantenimiento",
     "rotulo": "Mantenimiento",
     "formato": "mantenimiento",
     "grupo": "costos"
    }
   ],
   "documentos": [
    {
     "ref": "situacion_registral",
     "rotulo": "Situación registral",
     "formato": "lista"
    },
    {
     "ref": "declaratoria_fabrica",
     "rotulo": "Declaratoria de fábrica",
     "si": "Con declaratoria de fábrica",
     "no": "Sin declaratoria de fábrica"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    }
   ],
   "cifras": [
    "area_terreno",
    "metraje_construido",
    "pisos_edificacion",
    "dormitorios|H-08",
    "banos",
    "estacionamientos"
   ],
   "via": "H-25",
   "esquina": "posicion_en_manzana",
   "usoYConstruccion": "SI_PERTINENTE",
   "reservasFoto": [
    "Fachada o espacio principal",
    "Niveles y distribución",
    "Interiores",
    "Complementos",
    "Accesos"
   ],
   "ambientesSugeridos": [
    "Fachada/espacio principal",
    "niveles y distribución",
    "interiores",
    "complementos",
    "accesos"
   ],
   "criterios": {
    "superficies_prioritarias": "Terreno y construida de fuente en métricas separadas; área por nivel sólo existente; patios/jardín sin doble suma",
    "distribucion_prioritaria": "Pisos/niveles de casa; dormitorios; baños completos y medios; cochera; distribución; materiales/conservación sustentados",
    "ubicacion_prioritaria": "Vía del inmueble; esquina; frente/fondo acreditados; entorno; uso actual y zonificación diferenciados",
    "condiciones_pertinentes": "Componentes de oferta; servicios; entrega/ocupación autorizadas; conservación y antigüedad fechadas",
    "foco_documental": "Resumen autorizado de terreno/construcción; situación de fábrica/registro sólo con alcance real",
    "orden_fotografias": "Fachada/espacio principal; niveles y distribución; interiores; complementos; accesos",
    "restricciones_semanticas": "Construcción mayor que terreno no error automático; no dividir construcción por pisos; dirección de oficina no vía del inmueble",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Potencial documentado pertinente del predio; altura separada de pisos existentes; no acredita ampliación estructural.",
    "fuente_tipo": "AtributosGobernados.java:71",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "casa_campo": {
   "id": "casa_campo",
   "rotulo": "Casa de campo",
   "tipo": "C",
   "tipoObservado": "CASA",
   "naturaleza": "VARIANTE_PRESENTACION",
   "areas": [
    {
     "ref": "area_terreno",
     "rotulo": "Área de terreno"
    },
    {
     "ref": "metraje_construido",
     "rotulo": "Área construida"
    },
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    },
    {
     "ref": "area_jardin_patio",
     "rotulo": "Jardín o patio"
    },
    {
     "ref": "area_terraza",
     "rotulo": "Terraza"
    }
   ],
   "caracteristicas": [
    {
     "ref": "pisos_edificacion",
     "rotulo": "Pisos de la casa",
     "icono": "piso",
     "formato": "pisos",
     "grupo": "distribucion"
    },
    {
     "ref": "dormitorios",
     "rotulo": "Dormitorios",
     "icono": "cama",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "H-08",
     "rotulo": "Habitaciones",
     "icono": "cama",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "banos",
     "rotulo": "Baños completos",
     "icono": "bano",
     "formato": "banos",
     "grupo": "distribucion"
    },
    {
     "ref": "estacionamientos",
     "rotulo": "Cochera",
     "icono": "auto",
     "formato": "cochera",
     "grupo": "cochera"
    },
    {
     "ref": "antiguedad_anios",
     "rotulo": "Antigüedad",
     "icono": "reloj",
     "formato": "anios",
     "grupo": "edificio"
    }
   ],
   "rasgos": [
    {
     "ref": "piscina",
     "rotulo": "Piscina",
     "icono": "piscina"
    },
    {
     "ref": "jardin",
     "rotulo": "Jardín",
     "icono": "jardin"
    },
    {
     "ref": "terraza",
     "rotulo": "Terraza",
     "icono": "terraza"
    },
    {
     "ref": "patio",
     "rotulo": "Patio",
     "icono": "jardin"
    }
   ],
   "condiciones": [
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    },
    {
     "ref": "entrega_desocupado",
     "rotulo": "Entrega",
     "formato": "rasgo",
     "texto": "Se entrega desocupada",
     "no": "No se entrega desocupada"
    },
    {
     "ref": "apto_credito_hipotecario",
     "rotulo": "Crédito hipotecario",
     "formato": "rasgo",
     "texto": "Acepta crédito hipotecario",
     "no": "No acepta crédito hipotecario"
    },
    {
     "ref": "acepta_financiamiento_directo",
     "rotulo": "Financiamiento",
     "formato": "rasgo",
     "texto": "El propietario financia parte del saldo"
    },
    {
     "ref": "cuota_mantenimiento",
     "rotulo": "Mantenimiento",
     "formato": "mantenimiento",
     "grupo": "costos"
    }
   ],
   "documentos": [
    {
     "ref": "situacion_registral",
     "rotulo": "Situación registral",
     "formato": "lista"
    },
    {
     "ref": "declaratoria_fabrica",
     "rotulo": "Declaratoria de fábrica",
     "si": "Con declaratoria de fábrica",
     "no": "Sin declaratoria de fábrica"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    }
   ],
   "cifras": [
    "area_terreno",
    "metraje_construido",
    "pisos_edificacion",
    "dormitorios|H-08",
    "banos",
    "estacionamientos"
   ],
   "via": "H-25",
   "esquina": "posicion_en_manzana",
   "usoYConstruccion": "SI_PERTINENTE",
   "reservasFoto": [
    "Casa o espacio principal",
    "Terreno y jardín",
    "Interiores",
    "Piscina y complementos",
    "Acceso"
   ],
   "ambientesSugeridos": [
    "Casa/espacio principal",
    "terreno/jardín",
    "interiores",
    "piscina/complementos",
    "acceso"
   ],
   "criterios": {
    "superficies_prioritarias": "Terreno y construcción separados; área de complementos/niveles sólo acreditada; jardín/piscina con sujeto",
    "distribucion_prioritaria": "Niveles; dormitorios; baños completos/medios; cocheras y capacidad; construcciones complementarias descritas",
    "ubicacion_prioritaria": "Acceso/vía; zona; pendiente; entorno público y servicios corroborados; restricciones documentadas",
    "condiciones_pertinentes": "Agua/desagüe/energía con naturaleza; composición; mantenimiento de condominio si consta; ocupación",
    "foco_documental": "Resumen autorizado de predio y obras; restricciones conocidas; bungalow no autónomo registral por descripción",
    "orden_fotografias": "Casa/espacio principal; terreno/jardín; interiores; piscina/complementos; acceso",
    "restricciones_semanticas": "Zona rural no determina tipo; no inventar hectáreas útiles ni rentabilidad; no certificación técnica por fotos",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Verificar naturaleza del suelo, jurisdicción y régimen antes de aplicar parámetros urbanos; sólo datos pertinentes acreditados.",
    "fuente_tipo": "AtributosGobernados.java:71",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "casa_playa": {
   "id": "casa_playa",
   "rotulo": "Casa de playa",
   "tipo": "C",
   "tipoObservado": "CASA",
   "naturaleza": "VARIANTE_PRESENTACION",
   "areas": [
    {
     "ref": "area_terreno",
     "rotulo": "Área de terreno"
    },
    {
     "ref": "metraje_construido",
     "rotulo": "Área construida"
    },
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    },
    {
     "ref": "area_jardin_patio",
     "rotulo": "Jardín o patio"
    },
    {
     "ref": "area_terraza",
     "rotulo": "Terraza"
    }
   ],
   "caracteristicas": [
    {
     "ref": "pisos_edificacion",
     "rotulo": "Pisos de la casa",
     "icono": "piso",
     "formato": "pisos",
     "grupo": "distribucion"
    },
    {
     "ref": "dormitorios",
     "rotulo": "Dormitorios",
     "icono": "cama",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "H-08",
     "rotulo": "Habitaciones",
     "icono": "cama",
     "formato": "entero",
     "grupo": "distribucion"
    },
    {
     "ref": "banos",
     "rotulo": "Baños completos",
     "icono": "bano",
     "formato": "banos",
     "grupo": "distribucion"
    },
    {
     "ref": "estacionamientos",
     "rotulo": "Cochera",
     "icono": "auto",
     "formato": "cochera",
     "grupo": "cochera"
    },
    {
     "ref": "antiguedad_anios",
     "rotulo": "Antigüedad",
     "icono": "reloj",
     "formato": "anios",
     "grupo": "edificio"
    }
   ],
   "rasgos": [
    {
     "ref": "piscina",
     "rotulo": "Piscina",
     "icono": "piscina"
    },
    {
     "ref": "jardin",
     "rotulo": "Jardín",
     "icono": "jardin"
    },
    {
     "ref": "terraza",
     "rotulo": "Terraza",
     "icono": "terraza"
    },
    {
     "ref": "patio",
     "rotulo": "Patio",
     "icono": "jardin"
    }
   ],
   "condiciones": [
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    },
    {
     "ref": "entrega_desocupado",
     "rotulo": "Entrega",
     "formato": "rasgo",
     "texto": "Se entrega desocupada",
     "no": "No se entrega desocupada"
    },
    {
     "ref": "apto_credito_hipotecario",
     "rotulo": "Crédito hipotecario",
     "formato": "rasgo",
     "texto": "Acepta crédito hipotecario",
     "no": "No acepta crédito hipotecario"
    },
    {
     "ref": "acepta_financiamiento_directo",
     "rotulo": "Financiamiento",
     "formato": "rasgo",
     "texto": "El propietario financia parte del saldo"
    },
    {
     "ref": "cuota_mantenimiento",
     "rotulo": "Mantenimiento",
     "formato": "mantenimiento",
     "grupo": "costos"
    }
   ],
   "documentos": [
    {
     "ref": "situacion_registral",
     "rotulo": "Situación registral",
     "formato": "lista"
    },
    {
     "ref": "declaratoria_fabrica",
     "rotulo": "Declaratoria de fábrica",
     "si": "Con declaratoria de fábrica",
     "no": "Sin declaratoria de fábrica"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    }
   ],
   "cifras": [
    "area_terreno",
    "metraje_construido",
    "pisos_edificacion",
    "dormitorios|H-08",
    "banos",
    "estacionamientos"
   ],
   "via": "H-25",
   "esquina": "posicion_en_manzana",
   "usoYConstruccion": "SI_PERTINENTE",
   "reservasFoto": [
    "Casa o espacio principal",
    "Terraza y vista propia",
    "Interiores",
    "Complementos",
    "Entorno, aparte del inmueble"
   ],
   "ambientesSugeridos": [
    "Casa/espacio principal",
    "terraza/vista propia",
    "interiores",
    "complementos",
    "entorno separado"
   ],
   "criterios": {
    "superficies_prioritarias": "Terreno y construcción separados; áreas abiertas/terrazas con definición; complementos acreditados",
    "distribucion_prioritaria": "Niveles; dormitorios; baños completos/medios; cochera; acceso; materiales/conservación sustentados",
    "ubicacion_prioritaria": "Vía/acceso; zona; vista y cercanía al mar sólo corroboradas; restricciones pertinentes documentadas",
    "condiciones_pertinentes": "Composición; servicios; mantenimiento condominio si consta; ocupación y condiciones autorizadas",
    "foco_documental": "Resumen documental autorizado; no derechos de playa/acceso exclusivo ni ausencia de restricciones inferidos",
    "orden_fotografias": "Casa/espacio principal; terraza/vista propia; interiores; complementos; entorno separado",
    "restricciones_semanticas": "No deducir acceso privado al mar por perfil; foto del entorno no vista propia; no estado estructural por salinidad supuesta",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Verificar naturaleza del suelo, jurisdicción y régimen antes de aplicar parámetros urbanos; sólo datos pertinentes acreditados.",
    "fuente_tipo": "AtributosGobernados.java:71",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "terreno": {
   "id": "terreno",
   "rotulo": "Terreno",
   "tipo": "T",
   "tipoObservado": "TERRENO",
   "naturaleza": "BASE",
   "areas": [
    {
     "ref": "metraje_total",
     "rotulo": "Área del terreno",
     "definicionCore": "En TERRENO el área del lote es metraje_total (decisión D-7, V85)."
    },
    {
     "ref": "area_segun_partida",
     "rotulo": "Área según la partida"
    },
    {
     "ref": "edificacion_existente",
     "rotulo": "Construcción existente"
    }
   ],
   "caracteristicas": [
    {
     "ref": "frente",
     "rotulo": "Frente",
     "icono": "frente",
     "formato": "metros",
     "grupo": "dimensiones"
    },
    {
     "ref": "fondo",
     "rotulo": "Fondo",
     "icono": "area",
     "formato": "metros",
     "grupo": "dimensiones"
    },
    {
     "ref": "topografia",
     "rotulo": "Topografía",
     "icono": "terreno",
     "formato": "lista",
     "grupo": "terreno"
    },
    {
     "ref": "agua_desague",
     "rotulo": "Agua y desagüe",
     "icono": "agua",
     "formato": "lista",
     "grupo": "servicios"
    },
    {
     "ref": "energia_electrica",
     "rotulo": "Energía eléctrica",
     "icono": "energia",
     "formato": "lista",
     "grupo": "servicios"
    },
    {
     "ref": "condicion_terreno",
     "rotulo": "Habilitación",
     "icono": "documento",
     "formato": "lista",
     "grupo": "terreno"
    }
   ],
   "rasgos": [
    {
     "ref": "cercado",
     "rotulo": "Terreno cercado",
     "icono": "area",
     "grupo": "terreno"
    }
   ],
   "condiciones": [
    {
     "ref": "acepta_venta_fraccionada",
     "rotulo": "Venta por partes",
     "formato": "rasgo",
     "texto": "Acepta vender por partes"
    },
    {
     "ref": "acepta_aporte_a_proyecto",
     "rotulo": "Aporte a proyecto",
     "formato": "rasgo",
     "texto": "Acepta aportar el terreno a un proyecto"
    },
    {
     "ref": "acepta_financiamiento_directo",
     "rotulo": "Financiamiento",
     "formato": "rasgo",
     "texto": "El propietario financia parte del saldo"
    },
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    }
   ],
   "documentos": [
    {
     "ref": "situacion_registral",
     "rotulo": "Situación registral",
     "formato": "lista"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    },
    {
     "ref": "certificado_parametros_vigente",
     "rotulo": "Certificado de parámetros",
     "si": "Certificado de parámetros vigente",
     "no": "Sin certificado de parámetros vigente"
    },
    {
     "ref": "restriccion_arqueologica",
     "rotulo": "Restricción arqueológica",
     "formato": "lista"
    }
   ],
   "cifras": [
    "metraje_total",
    "frente",
    "fondo",
    "condicion_terreno",
    "agua_desague",
    "energia_electrica"
   ],
   "via": "tipo_via_acceso",
   "esquina": "posicion_en_manzana",
   "usoYConstruccion": "PRIORITARIO",
   "portadaSinFotos": "mapa",
   "reservasFoto": [
    "Predio y entorno físico",
    "Accesos",
    "Forma o levantamiento, si existe",
    "Plano, sólo si está autorizado"
   ],
   "ambientesSugeridos": [
    "Predio/entorno físico",
    "accesos",
    "forma/levantamiento existente",
    "documentos/plano sólo autorizados"
   ],
   "criterios": {
    "superficies_prioritarias": "Área terreno; frente/fondo/forma acreditados; edificación existente separada si consta",
    "distribucion_prioritaria": "Accesos; posición; pendiente/topografía; servicios; construcciones existentes sin dormitorios por defecto",
    "ubicacion_prioritaria": "Vía; esquina; entorno; zonificación/instrumento/fecha; parámetros y restricciones separados de uso observado",
    "condiciones_pertinentes": "Composición; servicios; ocupación; condiciones de oferta; restricciones materialmente pertinentes autorizadas",
    "foco_documental": "Resumen autorizado registral/urbanístico; no libre de cargas o edificable por defecto",
    "orden_fotografias": "Predio/entorno físico; accesos; forma/levantamiento existente; documentos/plano sólo autorizados",
    "restricciones_semanticas": "Frente por fondo sólo rectángulo acreditado; altura normativa no pisos construidos; terreno sin área construida no cero inventado",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Priorizar potencial documentado pertinente; altura y uso separados de construcción actual; fuente, vigencia, predio y condiciones revisados.",
    "fuente_tipo": "AtributosGobernados.java:72",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "almacen": {
   "id": "almacen",
   "rotulo": "Almacén",
   "tipo": "A",
   "tipoObservado": "ALMACEN",
   "naturaleza": "BASE",
   "areas": [
    {
     "ref": "area_terreno",
     "rotulo": "Área de terreno"
    },
    {
     "ref": "metraje_construido",
     "rotulo": "Área construida"
    },
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    },
    {
     "ref": "area_libre",
     "rotulo": "Área libre o patio"
    },
    {
     "ref": "area_oficinas",
     "rotulo": "Área de oficinas"
    }
   ],
   "caracteristicas": [
    {
     "ref": "altura_libre",
     "rotulo": "Altura libre",
     "icono": "altura",
     "formato": "metros",
     "grupo": "dimensiones"
    },
    {
     "ref": "ancho_puerta_ingreso",
     "rotulo": "Ancho del ingreso",
     "icono": "puerta",
     "formato": "metros",
     "grupo": "dimensiones"
    },
    {
     "ref": "alto_puerta_ingreso",
     "rotulo": "Alto del ingreso",
     "icono": "puerta",
     "formato": "metros",
     "grupo": "dimensiones"
    },
    {
     "ref": "capacidad_portante_piso",
     "rotulo": "Carga admisible del piso",
     "icono": "carga",
     "formato": "decimal",
     "grupo": "dimensiones"
    },
    {
     "ref": "muelles_carga",
     "rotulo": "Muelles de carga",
     "icono": "carga",
     "formato": "entero",
     "grupo": "dimensiones"
    },
    {
     "ref": "suministro_electrico",
     "rotulo": "Suministro eléctrico",
     "icono": "energia",
     "formato": "lista",
     "grupo": "servicios"
    }
   ],
   "rasgos": [
    {
     "ref": "en_esquina",
     "rotulo": "En esquina",
     "icono": "esquina"
    },
    {
     "ref": "balanza_camionera",
     "rotulo": "Balanza camionera",
     "icono": "carga"
    }
   ],
   "condiciones": [
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    },
    {
     "ref": "entrega_desocupado",
     "rotulo": "Entrega",
     "formato": "rasgo",
     "texto": "Se entrega desocupado",
     "no": "No se entrega desocupado"
    }
   ],
   "documentos": [
    {
     "ref": "independizado",
     "rotulo": "Independización",
     "si": "Unidad independizada",
     "no": "Unidad sin independizar"
    },
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    },
    {
     "ref": "certificado_itse",
     "rotulo": "Certificado ITSE",
     "formato": "lista"
    },
    {
     "ref": "apto_licencia_funcionamiento",
     "rotulo": "Licencia de funcionamiento",
     "si": "Apto para licencia de funcionamiento según el documento revisado",
     "no": "No apto para licencia de funcionamiento según el documento revisado"
    }
   ],
   "cifras": [
    "area_terreno",
    "metraje_construido",
    "altura_libre",
    "ancho_puerta_ingreso",
    "muelles_carga"
   ],
   "via": "tipo_via_acceso",
   "esquina": "en_esquina",
   "usoYConstruccion": "SI_PERTINENTE",
   "reservasFoto": [
    "Nave o espacio principal",
    "Accesos y carga",
    "Altura e instalaciones",
    "Oficinas y patio",
    "Entorno"
   ],
   "ambientesSugeridos": [
    "Nave/espacio principal",
    "accesos/carga",
    "altura e instalaciones",
    "oficinas/patio",
    "entorno"
   ],
   "criterios": {
    "superficies_prioritarias": "Área ofrecida y definición; terreno y techada si integran objeto; oficinas/patio separados sin doble suma",
    "distribucion_prioritaria": "Altura libre; acceso; puertas/muelles declarados; baños; instalaciones/carga y capacidad sólo sustentadas",
    "ubicacion_prioritaria": "Vía/acceso; esquina; entorno logístico/comercial observado; uso actual; zonificación documentada",
    "condiciones_pertinentes": "Servicios; patio; equipos/racks incluidos; mantenimiento; composición y usos permitidos por titular",
    "foco_documental": "Resumen autorizado de uso/condiciones y evidencias técnicas pertinentes; no ITSE o carga portante certificados por imagen",
    "orden_fotografias": "Nave/espacio principal; accesos/carga; altura e instalaciones; oficinas/patio; entorno",
    "restricciones_semanticas": "No almacenamiento permitido universal; no capacidad pallet por área; depósito accesorio no almacén autónomo inferido",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Priorizar actividad compatible acreditada; no equivale licencia; parámetros de nueva obra sólo si corresponden al objeto.",
    "fuente_tipo": "AtributosGobernados.java:73",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "estacionamiento": {
   "id": "estacionamiento",
   "rotulo": "Estacionamiento / cochera",
   "tipo": "E",
   "tipoObservado": "ESTACIONAMIENTO",
   "naturaleza": "BASE",
   "areas": [
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    }
   ],
   "caracteristicas": [
    {
     "ref": "estacionamientos",
     "rotulo": "Unidades",
     "icono": "auto",
     "formato": "entero",
     "grupo": "cochera"
    },
    {
     "ref": "H-03",
     "rotulo": "Capacidad",
     "icono": "auto",
     "formato": "autos",
     "grupo": "cochera"
    },
    {
     "ref": "H-11",
     "rotulo": "Nivel",
     "icono": "piso",
     "formato": "nivel",
     "grupo": "cochera"
    },
    {
     "ref": "H-12",
     "rotulo": "Ancho declarado",
     "icono": "puerta",
     "formato": "metros",
     "grupo": "cochera"
    },
    {
     "ref": "H-13",
     "rotulo": "Altura libre",
     "icono": "altura",
     "formato": "metros",
     "grupo": "cochera"
    }
   ],
   "rasgos": [
    {
     "ref": "H-06",
     "rotulo": "Techada",
     "icono": "casa",
     "grupo": "cochera"
    }
   ],
   "condiciones": [
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    }
   ],
   "documentos": [
    {
     "ref": "cargas_gravamenes",
     "rotulo": "Cargas y gravámenes",
     "formato": "lista"
    }
   ],
   "cifras": [
    "estacionamientos",
    "H-11",
    "@area",
    "H-12"
   ],
   "via": "H-25",
   "esquina": null,
   "usoYConstruccion": "SOLO_SI_RELEVANTE",
   "reservasFoto": [
    "Espacio completo",
    "Acceso",
    "Circulación",
    "Conjunto",
    "Plano, si existe"
   ],
   "ambientesSugeridos": [
    "Espacio completo",
    "acceso",
    "circulación",
    "conjunto",
    "plano sólo existente"
   ],
   "criterios": {
    "superficies_prioritarias": "Área de unidad con definición; dimensiones sólo acreditadas; no área de vivienda",
    "distribucion_prioritaria": "Unidades; capacidad autos separada; modalidad; nivel; cubierta; acceso declarado",
    "ubicacion_prioritaria": "Acceso/vía y zona; posición dentro de conjunto si conocida; altura libre/giro sólo medidos",
    "condiciones_pertinentes": "Derecho y oferta propios; inclusión/exclusión; mantenimiento moneda/período si consta; restricciones de uso",
    "foco_documental": "Resumen autorizado de unidad/vínculo/situación registral; no partida independiente por simple espacio",
    "orden_fotografias": "Espacio completo; acceso; circulación; conjunto; plano sólo existente",
    "restricciones_semanticas": "Una lineal para dos autos no dos unidades; inmueble independiente no complemento automático; sin prorrateo de valor inventado",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Sólo si pertinente al objeto y documentado; parámetro del predio no concede derecho individual de ampliar una unidad.",
    "fuente_tipo": "AtributosGobernados.java:75",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "edificio": {
   "id": "edificio",
   "rotulo": "Edificio",
   "tipo": "X",
   "tipoObservado": "OTRO",
   "naturaleza": "PERFIL_EDITORIAL_SIN_NUEVO_TIPO",
   "areas": [
    {
     "ref": "H-14",
     "rotulo": "Área de terreno"
    },
    {
     "ref": "H-15",
     "rotulo": "Área construida"
    },
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    }
   ],
   "caracteristicas": [
    {
     "ref": "H-09",
     "rotulo": "Pisos",
     "icono": "piso",
     "formato": "pisos",
     "grupo": "edificio"
    },
    {
     "ref": "H-10",
     "rotulo": "Unidades descritas",
     "icono": "casa",
     "formato": "entero",
     "grupo": "edificio"
    },
    {
     "ref": "estacionamientos",
     "rotulo": "Estacionamientos",
     "icono": "auto",
     "formato": "cochera",
     "grupo": "cochera"
    },
    {
     "ref": "H-17",
     "rotulo": "Ascensores",
     "icono": "ascensor",
     "formato": "entero",
     "grupo": "edificio"
    },
    {
     "ref": "antiguedad_anios",
     "rotulo": "Antigüedad",
     "icono": "reloj",
     "formato": "anios",
     "grupo": "edificio"
    }
   ],
   "rasgos": [],
   "condiciones": [
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    },
    {
     "ref": "entrega_desocupado",
     "rotulo": "Entrega",
     "formato": "rasgo",
     "texto": "Se entrega desocupado",
     "no": "No se entrega desocupado"
    }
   ],
   "documentos": [],
   "cifras": [
    "H-14",
    "H-15",
    "H-09",
    "H-10"
   ],
   "via": "H-25",
   "esquina": null,
   "usoYConstruccion": "PRIORITARIO",
   "reservasFoto": [
    "Fachada y conjunto",
    "Niveles y espacios",
    "Accesos",
    "Instalaciones",
    "Complementos",
    "Planos, si existen"
   ],
   "ambientesSugeridos": [
    "Fachada/conjunto",
    "niveles/espacios",
    "accesos",
    "instalaciones",
    "complementos",
    "planos existentes"
   ],
   "criterios": {
    "superficies_prioritarias": "Terreno y construcción total definida separados; áreas por nivel/unidad sólo acreditadas; subtotales identificados",
    "distribucion_prioritaria": "Pisos edificio; unidades declaradas; accesos/ascensores; uso por nivel; baños/componentes sin suma inventada",
    "ubicacion_prioritaria": "Vía; esquina; frente/posición; entorno; usos actuales; zonificación y parámetros documentados",
    "condiciones_pertinentes": "Objeto/paquete exactos; unidades incluidas/excluidas; ocupación declarada; servicios/mantenimiento con alcance",
    "foco_documental": "Resumen autorizado de matriz/unidades/obra/restricciones; no fusionar partidas ni derechos de distintos objetos",
    "orden_fotografias": "Fachada/conjunto; niveles/espacios; accesos; instalaciones; complementos; planos existentes",
    "restricciones_semanticas": "No código EDIFICIO; no reclasificación automática; no sumar área común por unidad; edificio no casa de muchos dormitorios",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Priorizar potencial documentado pertinente; altura y uso separados de construcción actual; fuente, vigencia, predio y condiciones revisados.",
    "fuente_tipo": "AtributosGobernados.java:77",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  },
  "otro": {
   "id": "otro",
   "rotulo": "Otro",
   "tipo": "X",
   "tipoObservado": "OTRO",
   "naturaleza": "BASE_CONFIGURABLE",
   "areas": [
    {
     "ref": "metraje_total",
     "rotulo": "Área total declarada"
    }
   ],
   "caracteristicas": [
    {
     "ref": "estacionamientos",
     "rotulo": "Estacionamientos",
     "icono": "auto",
     "formato": "cochera",
     "grupo": "cochera"
    },
    {
     "ref": "antiguedad_anios",
     "rotulo": "Antigüedad",
     "icono": "reloj",
     "formato": "anios",
     "grupo": "edificio"
    }
   ],
   "rasgos": [],
   "condiciones": [
    {
     "ref": "estado_ocupacion",
     "rotulo": "Ocupación",
     "formato": "lista"
    }
   ],
   "documentos": [],
   "cifras": [
    "@area",
    "estacionamientos",
    "antiguedad_anios"
   ],
   "via": "H-25",
   "esquina": null,
   "usoYConstruccion": "SOLO_SI_RELEVANTE",
   "reservasFoto": [
    "Vista real del inmueble",
    "Componentes",
    "Acceso",
    "Entorno, aparte del inmueble"
   ],
   "ambientesSugeridos": [
    "Representación real del objeto",
    "componentes",
    "acceso",
    "entorno separado"
   ],
   "criterios": {
    "superficies_prioritarias": "Superficie pertinente con sujeto/definición; terreno/construcción separados si aplican",
    "distribucion_prioritaria": "Características de la naturaleza explícita; no paquete residencial heredado",
    "ubicacion_prioritaria": "Zona/vía/posición/entorno pertinentes; restricciones sólo acreditadas",
    "condiciones_pertinentes": "Composición/operación/moneda/período y condiciones publicables de su contexto",
    "foco_documental": "Resumen existente y autorizado adecuado a naturaleza; pendientes críticos explícitos",
    "orden_fotografias": "Representación real del objeto; componentes; acceso; entorno separado",
    "restricciones_semanticas": "Naturaleza desconocida FALTANTE; no nuevos códigos o campos ocultos; configuración visual no gobierna aplicabilidad",
    "seleccion_de_destacados": "Perfil pertinente y candidatos conocidos con fuente; selección profesional; rasgo distintivo; prioridad del tipo; identificador estable. Hasta cuatro destacados adicionales en V06; esenciales conservados. Sin relleno ni negativos accesorios.",
    "regla_de_condiciones": "Preservar hechos esenciales y condiciones materiales aunque no sean favorables. Confirmar o comunicar incertidumbre material; no completar faltantes accesorios en portada.",
    "uso_y_construccion": "Sólo si pertinente al objeto y documentado; parámetro del predio no concede derecho individual de ampliar una unidad.",
    "fuente_tipo": "AtributosGobernados.java:77",
    "version_presentacion_v07": "V07_PROPUESTA"
   }
  }
 },
 "huecos": {
  "H-01": {
   "nombre": "Área ocupada",
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "motivo": "Definida funcionalmente (catálogo operativo CAT:551) sin clave en Core; no se intercambia con la construida."
  },
  "H-02": {
   "nombre": "Área techada",
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "motivo": "Sin clave; renombrar metraje_total como «Área techada» quedó aplazado (V85)."
  },
  "H-03": {
   "nombre": "Capacidad vehicular de la cochera",
   "tipoDato": "ENTERO",
   "unidad": "autos",
   "motivo": "tipo_estacionamiento describe la disposición, no cuántos autos caben; una cochera para dos autos no son dos unidades."
  },
  "H-04": {
   "nombre": "Área de la cochera",
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "motivo": "Sin clave para el área de un componente de estacionamiento; no se suma al área de la vivienda."
  },
  "H-05": {
   "nombre": "Cochera incluida en el precio de venta",
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "motivo": "estacionamientos_incluidos (ENCARGO, V77) sólo aplica al alquiler."
  },
  "H-06": {
   "nombre": "Cochera techada",
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "motivo": "Sin clave para la cubierta de la cochera."
  },
  "H-07": {
   "nombre": "Disposición de la cochera según la fuente",
   "tipoDato": "TEXTO",
   "unidad": null,
   "motivo": "«Paralelo» en la fuente no equivale a DOBLE_PARALELO sin conocer la capacidad."
  },
  "H-08": {
   "nombre": "Habitaciones según la fuente",
   "tipoDato": "ENTERO",
   "unidad": null,
   "motivo": "La fuente usa «habitaciones»; no se convierte en dormitorios (criterios V06 §3)."
  },
  "H-09": {
   "nombre": "Pisos de la construcción en tipos distintos de casa",
   "tipoDato": "ENTERO",
   "unidad": "pisos",
   "motivo": "pisos_edificacion sólo aplica a CASA."
  },
  "H-10": {
   "nombre": "Unidades descritas en el edificio",
   "tipoDato": "ENTERO",
   "unidad": null,
   "motivo": "Sin clave para el perfil edificio de OTRO."
  },
  "H-11": {
   "nombre": "Nivel de la unidad de estacionamiento",
   "tipoDato": "TEXTO",
   "unidad": null,
   "motivo": "piso aplica a departamento, local y oficina."
  },
  "H-12": {
   "nombre": "Ancho de la unidad de estacionamiento",
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "motivo": "Sin clave de dimensiones para ESTACIONAMIENTO."
  },
  "H-13": {
   "nombre": "Altura libre en tipos sin la clave",
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "motivo": "altura_libre aplica a almacén y local."
  },
  "H-14": {
   "nombre": "Área de terreno en tipos sin area_terreno",
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "motivo": "area_terreno aplica a almacén y casa; en terreno el área es metraje_total (decisión D-7, V85)."
  },
  "H-15": {
   "nombre": "Área construida en tipos sin metraje_construido",
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "motivo": "metraje_construido no aplica a OTRO ni a ESTACIONAMIENTO."
  },
  "H-16": {
   "nombre": "Baños en locales y oficinas",
   "tipoDato": "ENTERO",
   "unidad": null,
   "motivo": "banos aplica a casa y departamento; oficina sólo tiene banos_comunes_piso."
  },
  "H-17": {
   "nombre": "Ascensor en tipos sin ascensores",
   "tipoDato": "ENTERO",
   "unidad": null,
   "motivo": "ascensores aplica a departamento, local y oficina."
  },
  "H-18": {
   "nombre": "Moneda y período de la cuota de mantenimiento",
   "tipoDato": "IMPORTE",
   "unidad": "mensual",
   "motivo": "cuota_mantenimiento es DECIMAL sin moneda; un importe sin moneda no es un importe (V72)."
  },
  "H-19": {
   "nombre": "Ambiente, autorización y origen de cada fotografía",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "FotoPropiedad guarda id, clave, nombre y orden; CAT-E33 los especifica sin implementación."
  },
  "H-20": {
   "nombre": "Punto autorizado y su precisión",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "Core guarda geo_lat/geo_long sin autorización de salida ni precisión. Desde 1.5.0 el punto viaja con su precisión (EXACTA, EDIFICIO o APROXIMADA con radio) y la ficha lo dibuja sobre el mapa."
  },
  "H-21": {
   "nombre": "Condición, registro y contactos autorizados del profesional",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "CAT:346, CAT:383, CAT:765 y CAT:1499 los especifican; Persona/Organizacion no los tienen."
  },
  "H-22": {
   "nombre": "Mapa del entorno y referencias públicas corroboradas",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "Producto de la investigación manual; Core no lo almacena."
  },
  "H-23": {
   "nombre": "Resumen documental autorizado para la salida",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "Sin estructura Core para la proyección pública del expediente (CAT-E17, CAT-E43)."
  },
  "H-24": {
   "nombre": "Revisión, aplicabilidad, condiciones y altura en metros de los parámetros urbanísticos",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "altura_normativa_pisos y zonificacion existen; revisión vigente, aplicabilidad al predio, condiciones y altura en metros no."
  },
  "H-25": {
   "nombre": "Tipo de vía en tipos sin tipo_via_acceso",
   "tipoDato": "LISTA",
   "unidad": null,
   "motivo": "tipo_via_acceso aplica a almacén, local y terreno."
  },
  "H-26": {
   "nombre": "Sujeto del precio",
   "tipoDato": "TEXTO",
   "unidad": null,
   "motivo": "CAT-R05 pide cifra, moneda, sujeto, operación, base y período; precio_propiedad no guarda qué comprende la cifra cuando no es sólo el inmueble descrito."
  },
  "H-27": {
   "nombre": "Ubicación de salida: zona, dirección y punto autorizados",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "La figura 32 declara Propiedad.ubicacion: Ubicacion sin definir su contenido; la ficha usa distrito y zona como zona y, si viajan, la dirección autorizada (H-33) y el punto autorizado (H-20) con la misma precisión."
  },
  "H-28": {
   "nombre": "Estado y vigencia del encargo en la proyección",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "El diseño usa EN_PREPARACION, APROBADO, FINALIZADO y RESCINDIDO con VENCIDO calculado; Core ejecutable guarda captacion.estado (PENDIENTE, OBSERVADA, RECHAZADA, ACTIVA, CERRADA, VENCIDA) y fecha_fin_encargo (V15)."
  },
  "H-29": {
   "nombre": "Copia pública sin EXIF",
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "motivo": "CAT-R14 pide depurar el EXIF de la copia pública; FotoPropiedad no registra esa depuración."
  },
  "H-30": {
   "nombre": "Tiempo referencial desde la ubicación registrada hasta un lugar público",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "Core no calcula ni guarda tiempos: requieren la ubicación exacta, que es privada, un cálculo de rutas y una regla de redondeo y de cuántos lugares mostrar, porque varios tiempos juntos acotan la ubicación. Desde 1.5.0 la ficha sólo los escribe con la ubicación autorizada del inmueble o de su edificio."
  },
  "H-31": {
   "nombre": "Logotipo autorizado de la organización o del profesional",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "CAT-E17 nombra la marca de la pieza (marcaYContacto) y la directriz pide que la empresa o el profesional sean la referencia principal; Persona y Organizacion no guardan un logotipo ni su autorización para una salida."
  },
  "H-32": {
   "nombre": "Fotografía autorizada de quien atiende",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "La firma de la ficha presenta al agente o al profesional con su fotografía cuando existe; Persona no guarda una fotografía ni su autorización para una salida, y la imagen de una persona sólo se usa con su autorización expresa."
  },
  "H-33": {
   "nombre": "Dirección autorizada para la salida y su precisión",
   "tipoDato": "OBJETO",
   "unidad": null,
   "motivo": "Core guarda direccion, interior_unidad, geo_lat y geo_long como datos privados del inmueble; no hay un campo con la dirección que el encargo autoriza para una ficha ni con su precisión (el inmueble o sólo su edificio). La exclusividad del encargo no es esa autorización."
  }
 },
 "rotulosOpciones": {
  "tipo_via_acceso": {
   "AVENIDA": "Frente a una avenida",
   "CALLE_O_JIRON": "Frente a una calle o jirón",
   "PASAJE": "Frente a un pasaje",
   "CARRETERA": "Frente a una carretera",
   "TROCHA_O_SIN_VIA": "Acceso por trocha o sin vía"
  },
  "H-25": {
   "AVENIDA": "Frente a una avenida",
   "CALLE_O_JIRON": "Frente a una calle o jirón",
   "PASAJE": "Frente a un pasaje",
   "CARRETERA": "Frente a una carretera",
   "TROCHA_O_SIN_VIA": "Acceso por trocha o sin vía"
  },
  "posicion_en_manzana": {
   "UN_FRENTE": "Con un frente",
   "DOS_FRENTES": "Con dos frentes",
   "TRES_FRENTES": "Con tres frentes",
   "CUATRO_FRENTES": "Con cuatro frentes",
   "ESQUINA": "En esquina"
  },
  "topografia": {
   "PLANO": "Terreno plano",
   "PENDIENTE_LEVE": "Pendiente leve",
   "PENDIENTE_PRONUNCIADA": "Pendiente pronunciada",
   "BAJO_NIVEL_DE_VIA": "Bajo el nivel de la vía",
   "ACCIDENTADO": "Terreno accidentado"
  },
  "agua_desague": {
   "CONECTADO": "Conectados",
   "CON_FACTIBILIDAD_APROBADA": "Con factibilidad aprobada",
   "SIN_SERVICIO": "Sin servicio"
  },
  "energia_electrica": {
   "CONECTADO": "Conectada",
   "CON_FACTIBILIDAD_APROBADA": "Con factibilidad aprobada",
   "SIN_SERVICIO": "Sin servicio"
  },
  "condicion_terreno": {
   "URBANO_HABILITADO": "Urbano habilitado",
   "EN_PROCESO_DE_HABILITACION": "En proceso de habilitación",
   "RUSTICO_ERIAZO": "Rústico o eriazo",
   "ZONA_INFORMAL_SIN_HABILITAR": "Zona sin habilitar"
  },
  "tipo_acceso": {
   "A_PIE_DE_CALLE": "A pie de calle",
   "ESQUINA_A_CALLE": "En esquina, a la calle",
   "GALERIA_INTERIOR": "Dentro de una galería",
   "PASAJE_COMERCIAL": "En un pasaje comercial",
   "CENTRO_COMERCIAL": "En un centro comercial",
   "INTERIOR_DE_EDIFICIO": "Dentro de un edificio",
   "MERCADO": "En un mercado"
  },
  "estado_ocupacion": {
   "DESOCUPADO": "Desocupado",
   "OCUPADO_POR_EL_PROPIETARIO": "Ocupado por el propietario",
   "OCUPADO_POR_INQUILINO": "Ocupado por un inquilino",
   "OCUPADO_POR_TERCEROS_SIN_TITULO": "Ocupado por terceros"
  },
  "vista": {
   "INTERIOR": "Vista interior",
   "EXTERIOR_A_CALLE": "Vista a la calle",
   "VISTA_A_PARQUE": "Vista a un parque",
   "VISTA_AL_MAR": "Vista al mar",
   "VISTA_A_AREAS_COMUNES": "Vista a áreas comunes"
  },
  "nivel_implementacion": {
   "CASCO_OBRA_GRIS": "En casco u obra gris",
   "PLANTA_LIBRE": "Planta libre",
   "IMPLEMENTADO_PARCIAL": "Implementado en parte",
   "IMPLEMENTADO_COMPLETO": "Implementado"
  },
  "tipo_estacionamiento": {
   "SIMPLE": "Simple",
   "DOBLE_LINEAL": "Doble, en línea",
   "DOBLE_PARALELO": "Doble, en paralelo",
   "MOTO": "Para moto"
  },
  "estado_conservacion": {
   "ESTRENO": "Para estrenar",
   "MUY_BUENO": "Muy buen estado",
   "BUENO": "Buen estado",
   "REGULAR": "Estado regular",
   "PARA_REMODELAR": "Para remodelar",
   "PARA_DEMOLER": "Para demoler"
  },
  "modalidad_precio": {
   "MENSUAL_TOTAL": "Alquiler total al mes",
   "POR_M2_AL_MES": "Alquiler por metro cuadrado al mes"
  },
  "mantenimiento_a_cargo_de": {
   "PROPIETARIO": "El mantenimiento lo paga el propietario",
   "INQUILINO": "El mantenimiento lo paga el inquilino"
  },
  "igv_arrendamiento": {
   "GRAVADO_18": "El alquiler está gravado con IGV (18 %)",
   "NO_GRAVADO": "El alquiler no está gravado con IGV"
  },
  "respaldo_exigido": {
   "SOLO_DEPOSITO": "Se pide sólo depósito en garantía",
   "AVAL_PERSONAL": "Se pide aval personal",
   "AVAL_CON_INMUEBLE": "Se pide aval con inmueble",
   "CARTA_FIANZA_BANCARIA": "Se pide carta fianza bancaria"
  },
  "situacion_registral": {
   "INSCRITO_EN_SUNARP": "Inscrito en registros públicos (SUNARP)",
   "EN_SANEAMIENTO": "En proceso de saneamiento",
   "NO_INSCRITO_SOLO_POSESION": "Sin inscripción; se transfiere la posesión"
  },
  "cargas_gravamenes": {
   "NINGUNA": "Sin cargas ni gravámenes declarados",
   "HIPOTECA": "Hipoteca",
   "EMBARGO": "Embargo",
   "SERVIDUMBRE": "Servidumbre",
   "COPROPIEDAD_SIN_DIVIDIR": "Copropiedad sin dividir",
   "SUCESION_PENDIENTE": "Sucesión pendiente",
   "LITIGIO": "Litigio"
  },
  "certificado_itse": {
   "VIGENTE": "Certificado ITSE vigente",
   "VENCIDO": "Certificado ITSE vencido",
   "EN_TRAMITE": "Certificado ITSE en trámite",
   "NO_TIENE": "Sin certificado ITSE"
  },
  "restriccion_arqueologica": {
   "NO_APLICA": "Sin restricción arqueológica aplicable",
   "CIRA_OBTENIDO": "Con CIRA obtenido",
   "EN_TRAMITE": "CIRA en trámite",
   "REQUERIDO_NO_INICIADO": "Requiere CIRA, sin trámite iniciado"
  },
  "suministro_electrico": {
   "MONOFASICO_220": "Monofásico de 220 V",
   "TRIFASICO_380": "Trifásico de 380 V",
   "TRIFASICO_440": "Trifásico de 440 V",
   "SUBESTACION_PROPIA": "Subestación propia"
  },
  "areas_comunes": {
   "GIMNASIO": "Gimnasio",
   "PISCINA": "Piscina",
   "SUM": "Salón de usos múltiples",
   "PARRILLAS": "Zona de parrillas",
   "COWORKING": "Sala de trabajo",
   "SALA_DE_NINOS": "Sala de niños",
   "AZOTEA": "Azotea",
   "LAVANDERIA_COMUN": "Lavandería común",
   "JUEGOS_INFANTILES": "Juegos infantiles",
   "SALA_DE_CINE": "Sala de cine"
  }
 }
};
  if (typeof module === 'object' && module.exports) module.exports = perfiles;
  else raiz.BROX_PERFILES_V08 = perfiles;
})(typeof globalThis !== 'undefined' ? globalThis : this);
