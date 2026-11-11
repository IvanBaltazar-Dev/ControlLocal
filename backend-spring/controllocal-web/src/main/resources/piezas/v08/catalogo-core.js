// Generado por herramientas/derivar-catalogo.cjs. No editar a mano.
// Subconjunto derivado del catálogo Core para validar y presentar la ficha V08.
// No es autoridad: la autoridad es Core. Ver CONTRATO-DATOS-FICHA-V08.md §11.
(function (raiz) {
  var catalogo = {
 "fuente": "docs/ai/modelo/catalogo-core.json",
 "fuenteSha256": "3c22c6e6d372550698a1f9a0191a41342c80a52e5ef2b97282415a1c01d34c63",
 "fuenteGeneradaEl": "2026-09-05T06:37:53.626Z",
 "ampliaciones": [
  "E (ESTACIONAMIENTO) según la regla escrita en V94__estacionamiento_tipo_fisico.sql (claves aplica_todos con la exigencia de L; partida_registral, oficina_registral, cargas_gravamenes y estado_ocupacion como OPC).",
  "Claves de sujeto ENCARGO según V74__las_primeras_condiciones_del_encargo.sql y V77__completar_condiciones_del_encargo.sql."
 ],
 "derivadoEl": "2026-10-16",
 "tipos": {
  "L": "LOCAL",
  "O": "OFICINA",
  "D": "DEPARTAMENTO",
  "C": "CASA",
  "T": "TERRENO",
  "A": "ALMACEN",
  "E": "ESTACIONAMIENTO",
  "X": "OTRO"
 },
 "monedas": [
  "PEN",
  "USD"
 ],
 "operaciones": {
  "VENTA": "V",
  "ALQUILER": "A"
 },
 "propiedad": {
  "accesibilidad_movilidad_reducida": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "acceso_vehiculo_maximo": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "T": "OPC"
   },
   "opciones": [
    "CAMIONETA",
    "CAMION_2_EJES",
    "CAMION_3_EJES",
    "TRAILER_T3S3",
    "CONTENEDOR_40_PIES"
   ]
  },
  "aforo_itse": {
   "tipoDato": "ENTERO",
   "unidad": "personas",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "agua_caliente": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": [
    "NO_TIENE",
    "TERMA_ELECTRICA",
    "TERMA_A_GAS",
    "CENTRALIZADA",
    "SOLAR"
   ]
  },
  "agua_desague": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "T": "PUB"
   },
   "opciones": [
    "CONECTADO",
    "CON_FACTIBILIDAD_APROBADA",
    "SIN_SERVICIO"
   ]
  },
  "aire_acondicionado": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "NINGUNO",
    "SPLIT_EN_UNIDAD",
    "CENTRAL_DEL_EDIFICIO",
    "VRV_INDEPENDIENTE"
   ]
  },
  "alto_puerta_ingreso": {
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "altura_libre": {
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC"
   },
   "opciones": []
  },
  "altura_normativa_pisos": {
   "tipoDato": "ENTERO",
   "unidad": "pisos",
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "T": "OPC"
   },
   "opciones": []
  },
  "ambientes": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "amoblado": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "ancho_puerta_ingreso": {
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "antiguedad_anios": {
   "tipoDato": "ENTERO",
   "unidad": "años",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC",
    "T": "OPC",
    "X": "OPC",
    "E": "OPC"
   },
   "opciones": []
  },
  "apto_licencia_funcionamiento": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "area_jardin_patio": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "area_libre": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "area_libre_minima": {
   "tipoDato": "DECIMAL",
   "unidad": "%",
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "area_oficinas": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "area_segun_partida": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "T": "OPC"
   },
   "opciones": []
  },
  "area_terraza": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "area_terreno": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC"
   },
   "opciones": []
  },
  "areas_comunes": {
   "tipoDato": "LISTA_MULTIPLE",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "GIMNASIO",
    "PISCINA",
    "SUM",
    "PARRILLAS",
    "COWORKING",
    "SALA_DE_NINOS",
    "AZOTEA",
    "LAVANDERIA_COMUN",
    "JUEGOS_INFANTILES",
    "SALA_DE_CINE"
   ]
  },
  "ascensores": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "balanza_camionera": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "balcon": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC"
   },
   "opciones": []
  },
  "bano_servicio": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "banos": {
   "tipoDato": "DECIMAL",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "banos_comunes_piso": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "O": "OPC"
   },
   "opciones": []
  },
  "capacidad_portante_piso": {
   "tipoDato": "DECIMAL",
   "unidad": "t/m²",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "carga_electrica_kw": {
   "tipoDato": "DECIMAL",
   "unidad": "kW",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "cargas_gravamenes": {
   "tipoDato": "LISTA_MULTIPLE",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC",
    "T": "OPC",
    "E": "OPC"
   },
   "opciones": [
    "NINGUNA",
    "HIPOTECA",
    "EMBARGO",
    "SERVIDUMBRE",
    "COPROPIEDAD_SIN_DIVIDIR",
    "SUCESION_PENDIENTE",
    "LITIGIO"
   ]
  },
  "cercado": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "certificacion_sostenible": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "O": "OPC"
   },
   "opciones": [
    "NINGUNA",
    "LEED_CERTIFIED",
    "LEED_SILVER",
    "LEED_GOLD",
    "LEED_PLATINUM",
    "OTRA"
   ]
  },
  "certificado_itse": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "VIGENTE",
    "VENCIDO",
    "EN_TRAMITE",
    "NO_TIENE"
   ]
  },
  "certificado_parametros_vigente": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "clase_edificio": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "O": "OPC"
   },
   "opciones": [
    "A_PLUS",
    "A",
    "B",
    "C",
    "NO_APLICA"
   ]
  },
  "coeficiente_edificacion": {
   "tipoDato": "DECIMAL",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "condicion_almacenamiento": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": [
    "SECO",
    "REFRIGERADO",
    "CONGELADO",
    "MATERIALES_PELIGROSOS",
    "DEPOSITO_TEMPORAL_ADUANERO"
   ]
  },
  "condicion_terreno": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "PUB"
   },
   "opciones": [
    "URBANO_HABILITADO",
    "EN_PROCESO_DE_HABILITACION",
    "RUSTICO_ERIAZO",
    "ZONA_INFORMAL_SIN_HABILITAR"
   ]
  },
  "cuarto_servicio": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "cuota_mantenimiento": {
   "tipoDato": "DECIMAL",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "declaratoria_fabrica": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "deposito_area": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "depositos": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "dormitorios": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "ALT",
    "D": "ALT"
   },
   "opciones": []
  },
  "edificacion_existente": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "en_condominio": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC"
   },
   "opciones": []
  },
  "en_esquina": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "energia_electrica": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "PUB"
   },
   "opciones": [
    "CONECTADO",
    "CON_FACTIBILIDAD_APROBADA",
    "SIN_SERVICIO"
   ]
  },
  "estacionamientos": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC",
    "T": "OPC",
    "X": "OPC",
    "E": "OPC"
   },
   "opciones": []
  },
  "estacionamientos_camiones": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "estado_conservacion": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "ESTRENO",
    "MUY_BUENO",
    "BUENO",
    "REGULAR",
    "PARA_REMODELAR",
    "PARA_DEMOLER"
   ]
  },
  "estado_ocupacion": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC",
    "T": "OPC",
    "X": "OPC",
    "E": "OPC"
   },
   "opciones": [
    "DESOCUPADO",
    "OCUPADO_POR_EL_PROPIETARIO",
    "OCUPADO_POR_INQUILINO",
    "OCUPADO_POR_TERCEROS_SIN_TITULO"
   ]
  },
  "estado_via": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "T": "OPC"
   },
   "opciones": [
    "ASFALTADA",
    "AFIRMADA",
    "SIN_AFIRMAR"
   ]
  },
  "estudio": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "etapa_entrega": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "EN_PLANOS",
    "EN_CONSTRUCCION",
    "ENTREGA_INMEDIATA"
   ]
  },
  "extraccion_humos": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "L": "OPC"
   },
   "opciones": [
    "SIN_DUCTO",
    "DUCTO_PROYECTADO",
    "DUCTO_A_AZOTEA",
    "CAMPANA_INSTALADA"
   ]
  },
  "fibra_optica": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "fondo": {
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "T": "OPC"
   },
   "opciones": []
  },
  "frente": {
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "T": "OPC"
   },
   "opciones": []
  },
  "gas": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC",
    "T": "OPC"
   },
   "opciones": [
    "SIN_RED_CERCANA",
    "RED_EN_LA_VIA",
    "CON_FACTIBILIDAD_APROBADA",
    "INSTALADO",
    "GLP_TANQUE_EXTERNO",
    "GLP_BALONES"
   ]
  },
  "horario_acceso_edificio": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "H24_7",
    "LUN_VIE_OFICINA",
    "LUN_SAB_OFICINA",
    "OTRO"
   ]
  },
  "independizado": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "jardin": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "lavanderia": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC"
   },
   "opciones": [
    "INDEPENDIENTE",
    "EN_COCINA",
    "EN_TERRAZA",
    "COMUN_DEL_EDIFICIO",
    "NO_TIENE"
   ]
  },
  "lote_minimo_normativo": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "luz_entre_columnas": {
   "tipoDato": "TEXTO",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "mascotas_reglamento": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "medidor_servicios": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "INDEPENDIENTE",
    "COMPARTIDO_PRORRATEO",
    "SIN_MEDIDOR"
   ]
  },
  "medios_banos": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "metraje_arrendable": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "metraje_construido": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "metraje_total": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "aplicabilidad": {
    "A": "ALT",
    "C": "ALT",
    "D": "ALT",
    "L": "ALT",
    "O": "ALT",
    "T": "ALT",
    "X": "ALT",
    "E": "ALT"
   },
   "opciones": []
  },
  "muelles_carga": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "nivel_implementacion": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "CASCO_OBRA_GRIS",
    "PLANTA_LIBRE",
    "IMPLEMENTADO_PARCIAL",
    "IMPLEMENTADO_COMPLETO"
   ]
  },
  "niveles_internos": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "oficina_registral": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC",
    "T": "OPC",
    "E": "OPC"
   },
   "opciones": [
    "LIMA",
    "CALLAO",
    "HUAURA",
    "CANETE",
    "HUARAL",
    "BARRANCA"
   ]
  },
  "partida_registral": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC",
    "T": "OPC",
    "E": "OPC"
   },
   "opciones": []
  },
  "patio": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "piscina": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC"
   },
   "opciones": []
  },
  "piso": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "pisos_edificacion": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC"
   },
   "opciones": []
  },
  "posicion_en_manzana": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "T": "OPC"
   },
   "opciones": [
    "UN_FRENTE",
    "DOS_FRENTES",
    "TRES_FRENTES",
    "CUATRO_FRENTES",
    "ESQUINA"
   ]
  },
  "posiciones_pallet": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "posiciones_trabajo": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "O": "OPC"
   },
   "opciones": []
  },
  "profundidad_patio_maniobras": {
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "puertas_ingreso": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": []
  },
  "recepcion_edificio": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "O": "OPC"
   },
   "opciones": []
  },
  "respaldo_electrico": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "NO_TIENE",
    "GRUPO_ELECTROGENO_AREAS_COMUNES",
    "GRUPO_ELECTROGENO_TOTAL"
   ]
  },
  "restriccion_arqueologica": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": [
    "NO_APLICA",
    "CIRA_OBTENIDO",
    "EN_TRAMITE",
    "REQUERIDO_NO_INICIADO"
   ]
  },
  "restriccion_reglamento_interno": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "retiro_municipal": {
   "tipoDato": "DECIMAL",
   "unidad": "m",
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "rubro_permitido": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "salas_reunion": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "O": "OPC"
   },
   "opciones": []
  },
  "servicios_disponibles": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": false,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "sistema_contra_incendios": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "NINGUNO",
    "EXTINTORES",
    "GABINETES",
    "ROCIADORES",
    "ROCIADORES_ESFR"
   ]
  },
  "situacion_registral": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "T": "OPC"
   },
   "opciones": [
    "INSCRITO_EN_SUNARP",
    "EN_SANEAMIENTO",
    "NO_INSCRITO_SOLO_POSESION"
   ]
  },
  "suministro_electrico": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "MONOFASICO_220",
    "TRIFASICO_380",
    "TRIFASICO_440",
    "SUBESTACION_PROPIA"
   ]
  },
  "terraza": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC"
   },
   "opciones": []
  },
  "tipo_acceso": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "L": "PUB"
   },
   "opciones": [
    "A_PIE_DE_CALLE",
    "ESQUINA_A_CALLE",
    "GALERIA_INTERIOR",
    "PASAJE_COMERCIAL",
    "CENTRO_COMERCIAL",
    "INTERIOR_DE_EDIFICIO",
    "MERCADO"
   ]
  },
  "tipo_cocina": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC"
   },
   "opciones": [
    "CERRADA",
    "ABIERTA_A_SALA",
    "KITCHENETTE",
    "BARRA"
   ]
  },
  "tipo_estacionamiento": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "D": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "SIMPLE",
    "DOBLE_LINEAL",
    "DOBLE_PARALELO",
    "MOTO"
   ]
  },
  "tipo_muelle": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": [
    "SIN_MUELLE",
    "A_NIVEL_DE_PISO",
    "ANDEN_ELEVADO",
    "ANDEN_CON_NIVELADOR",
    "MIXTO"
   ]
  },
  "tipo_piso": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC"
   },
   "opciones": [
    "CONCRETO_PULIDO",
    "CONCRETO_ENDURECIDO",
    "LOSA_SIN_TRATAR",
    "AFIRMADO",
    "TIERRA"
   ]
  },
  "tipo_via_acceso": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "L": "OPC",
    "T": "OPC"
   },
   "opciones": [
    "AVENIDA",
    "CALLE_O_JIRON",
    "PASAJE",
    "CARRETERA",
    "TROCHA_O_SIN_VIA"
   ]
  },
  "tipologia": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC"
   },
   "opciones": [
    "MONOAMBIENTE",
    "FLAT",
    "DUPLEX",
    "TRIPLEX",
    "PENTHOUSE",
    "LOFT"
   ]
  },
  "topografia": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": [
    "PLANO",
    "PENDIENTE_LEVE",
    "PENDIENTE_PRONUNCIADA",
    "BAJO_NIVEL_DE_VIA",
    "ACCIDENTADO"
   ]
  },
  "torre_bloque": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC"
   },
   "opciones": []
  },
  "unidades_por_piso": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "O": "OPC"
   },
   "opciones": []
  },
  "usos_compatibles": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "T": "OPC"
   },
   "opciones": []
  },
  "via_de_acceso": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "T": "OPC"
   },
   "opciones": []
  },
  "vigilancia": {
   "tipoDato": "LISTA_MULTIPLE",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "D": "OPC",
    "L": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "NO_TIENE",
    "PORTERO_DIURNO",
    "CASETA_24H",
    "CAMARAS_CCTV",
    "CONTROL_DE_ACCESO",
    "CERCO_PERIMETRICO"
   ]
  },
  "vista": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "D": "OPC",
    "O": "OPC"
   },
   "opciones": [
    "INTERIOR",
    "EXTERIOR_A_CALLE",
    "VISTA_A_PARQUE",
    "VISTA_AL_MAR",
    "VISTA_A_AREAS_COMUNES"
   ]
  },
  "zona_de_riesgo": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "C": "OPC",
    "T": "OPC"
   },
   "opciones": []
  },
  "zonificacion": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "aplicabilidad": {
    "A": "OPC",
    "C": "OPC",
    "L": "OPC",
    "T": "ALT"
   },
   "opciones": []
  }
 },
 "encargo": {
  "garantia_meses": {
   "tipoDato": "ENTERO",
   "unidad": "meses",
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": []
  },
  "adelanto_meses": {
   "tipoDato": "ENTERO",
   "unidad": "meses",
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": []
  },
  "plazo_minimo_meses": {
   "tipoDato": "ENTERO",
   "unidad": "meses",
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": []
  },
  "disponible_desde": {
   "tipoDato": "FECHA",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ],
    "V": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": []
  },
  "mascotas_aceptadas": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "D",
     "C"
    ]
   },
   "opciones": []
  },
  "se_ofrece_amoblado": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "D",
     "C",
     "O"
    ]
   },
   "opciones": []
  },
  "entrega_desocupado": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "V": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": []
  },
  "apto_credito_hipotecario": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "V": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": []
  },
  "acepta_permuta": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "V": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": []
  },
  "acepta_financiamiento_directo": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "V": [
     "T",
     "C",
     "D"
    ]
   },
   "opciones": []
  },
  "acepta_venta_fraccionada": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "V": [
     "T"
    ]
   },
   "opciones": []
  },
  "acepta_aporte_a_proyecto": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "V": [
     "T"
    ]
   },
   "opciones": []
  },
  "igv_arrendamiento": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "D",
     "C",
     "T",
     "A",
     "X"
    ]
   },
   "opciones": [
    "GRAVADO_18",
    "NO_GRAVADO"
   ]
  },
  "modalidad_precio": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "A"
    ]
   },
   "opciones": [
    "MENSUAL_TOTAL",
    "POR_M2_AL_MES"
   ]
  },
  "mantenimiento_a_cargo_de": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "D",
     "O",
     "L",
     "C",
     "A"
    ]
   },
   "opciones": [
    "PROPIETARIO",
    "INQUILINO"
   ]
  },
  "estacionamientos_incluidos": {
   "tipoDato": "ENTERO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "D",
     "O",
     "L",
     "C",
     "A"
    ]
   },
   "opciones": []
  },
  "precio_estacionamiento_adicional": {
   "tipoDato": "IMPORTE",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "D",
     "O",
     "L"
    ]
   },
   "opciones": []
  },
  "equipamiento_incluido": {
   "tipoDato": "LISTA_MULTIPLE",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "D",
     "C"
    ]
   },
   "opciones": [
    "REFRIGERADORA",
    "COCINA",
    "LAVADORA",
    "SECADORA",
    "AIRE_ACONDICIONADO",
    "MUEBLES_SALA",
    "MUEBLES_DORMITORIO",
    "CORTINAS",
    "TELEVISOR"
   ]
  },
  "uso_admitido_por_titular": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "D",
     "C"
    ]
   },
   "opciones": [
    "VIVIENDA",
    "VIVIENDA_Y_OFICINA",
    "COMERCIAL"
   ]
  },
  "se_entrega_implementado": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "A"
    ]
   },
   "opciones": []
  },
  "meses_gracia_implementacion": {
   "tipoDato": "ENTERO",
   "unidad": "meses",
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "A"
    ]
   },
   "opciones": []
  },
  "respaldo_exigido": {
   "tipoDato": "LISTA",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "A"
    ]
   },
   "opciones": [
    "SOLO_DEPOSITO",
    "AVAL_PERSONAL",
    "AVAL_CON_INMUEBLE",
    "CARTA_FIANZA_BANCARIA"
   ]
  },
  "rubros_excluidos_por_titular": {
   "tipoDato": "TEXTO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "L",
     "O",
     "A"
    ]
   },
   "opciones": []
  },
  "racks_incluidos": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "A"
    ]
   },
   "opciones": []
  },
  "arrendamiento_parcial": {
   "tipoDato": "BOOLEANO",
   "unidad": null,
   "activa": true,
   "operaciones": {
    "A": [
     "A",
     "L",
     "O"
    ]
   },
   "opciones": []
  },
  "area_minima_arrendable": {
   "tipoDato": "DECIMAL",
   "unidad": "m²",
   "activa": true,
   "operaciones": {
    "A": [
     "A",
     "L",
     "O"
    ]
   },
   "opciones": []
  }
 }
};
  if (typeof module === 'object' && module.exports) module.exports = catalogo;
  else raiz.BROX_CATALOGO_CORE = catalogo;
})(typeof globalThis !== 'undefined' ? globalThis : this);
