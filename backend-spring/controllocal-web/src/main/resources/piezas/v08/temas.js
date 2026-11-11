// V08 · Catálogo de temas de color (1.12.0). Derivado por herramientas/derivar-temas.cjs; no editar a mano.
// La ficha acepta opciones.tema con uno de estos id (o un alias); lo demás cae en el predeterminado.
(function (raiz) {
  'use strict';
  var TEMAS = {
    "version": "1.12.0",
    "predeterminado": "marfil",
    "alias": {
      "claro": "marfil",
      "oscuro": "grafito",
      "porcelana": "zafiro",
      "arena": "marfil",
      "pizarra": "marfil",
      "burdeos": "carmin",
      "cobalto": "zafiro"
    },
    "grupos": [
      "BROX",
      "Clásicos",
      "Verdes",
      "Amarillo y negro",
      "Noche",
      "Tecnológicos",
      "SIVAN"
    ],
    "temas": [
      {
        "id": "marfil",
        "nombre": "Marfil",
        "grupo": "BROX",
        "descripcion": "La marca BROX: banda de tinta y el verde de la web en el botón, los iconos y los filetes.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#0A8533",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#ACD6BA"
          },
          "papel": {
            "base": "#0A8533",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#ACD6BA"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#0A8533",
          "acento": "#0A8533",
          "vivo": "#0A8533",
          "banda": "#111214",
          "filete": "#0A8533",
          "accion": "#0A8533",
          "halo": "#ACD6BA"
        }
      },
      {
        "id": "grafito",
        "nombre": "Grafito",
        "grupo": "BROX",
        "descripcion": "La versión oscura de BROX: banda grafito y oro fuerte en el botón, los destacados y los filetes.",
        "pantallaOscura": true,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#E8A317",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#7D5B16"
          },
          "papel": {
            "base": "#E8A317",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#F7E0B0"
          }
        },
        "muestra": {
          "fondo": "#0A0A0B",
          "panel": "#111214",
          "tinta": "#F6F5F1",
          "color": "#E8A317",
          "acento": "#2B2F36",
          "vivo": "#2B2F36",
          "banda": "#2B2F36",
          "filete": "#E8A317",
          "accion": "#E8A317",
          "halo": "#F7E0B0"
        }
      },
      {
        "id": "zafiro",
        "nombre": "Zafiro",
        "grupo": "Clásicos",
        "descripcion": "Azul fuerte: la banda, el botón y los iconos en el mismo azul, sobre blanco.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#0066B1",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#A8CBE4"
          },
          "papel": {
            "base": "#0066B1",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#A8CBE4"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#0066B1",
          "acento": "#0066B1",
          "vivo": "#0066B1",
          "banda": "#0066B1",
          "filete": "#00467A",
          "accion": "#0066B1",
          "halo": "#A8CBE4"
        }
      },
      {
        "id": "carmin",
        "nombre": "Carmín",
        "grupo": "Clásicos",
        "descripcion": "Rojo fuerte y blanco: la banda, el botón y los iconos en el mismo rojo.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#D50F1A",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#F1ADB1"
          },
          "papel": {
            "base": "#D50F1A",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#F1ADB1"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#D50F1A",
          "acento": "#D50F1A",
          "vivo": "#D50F1A",
          "banda": "#D50F1A",
          "filete": "#8C0A11",
          "accion": "#D50F1A",
          "halo": "#F1ADB1"
        }
      },
      {
        "id": "regata",
        "nombre": "Regata",
        "grupo": "Clásicos",
        "descripcion": "Azul marino, azul y rojo: la banda marino con filete rojo, el botón rojo y los iconos azules.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#E22718",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#F5B6B0"
          },
          "papel": {
            "base": "#E22718",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#F5B6B0"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#E22718",
          "acento": "#E22718",
          "vivo": "#0066B1",
          "banda": "#003C78",
          "filete": "#E22718",
          "accion": "#E22718",
          "halo": "#F5B6B0"
        }
      },
      {
        "id": "bosque",
        "nombre": "Bosque",
        "grupo": "Verdes",
        "descripcion": "Verde fuerte y blanco: la banda, el botón y los iconos en el mismo verde; en pantalla, oscuro.",
        "pantallaOscura": true,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#00A651",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#076134"
          },
          "papel": {
            "base": "#00703C",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#A8CEBD"
          }
        },
        "muestra": {
          "fondo": "#0B1712",
          "panel": "#0E1C16",
          "tinta": "#F1F4EF",
          "color": "#00703C",
          "acento": "#00703C",
          "vivo": "#00703C",
          "banda": "#00703C",
          "filete": "#004526",
          "accion": "#00703C",
          "halo": "#A8CEBD"
        }
      },
      {
        "id": "salvia",
        "nombre": "Salvia",
        "grupo": "Verdes",
        "descripcion": "Verde hoja claro con letra negra: fresco y luminoso.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#43B02A",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#BFE4B7"
          },
          "papel": {
            "base": "#43B02A",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#BFE4B7"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#43B02A",
          "acento": "#338620",
          "vivo": "#338620",
          "banda": "#43B02A",
          "filete": "#2A6F1B",
          "accion": "#43B02A",
          "halo": "#BFE4B7"
        }
      },
      {
        "id": "azabache",
        "nombre": "Azabache",
        "grupo": "Amarillo y negro",
        "descripcion": "Negro con amarillo fuerte: el contraste de un cartel, con el botón amarillo y letra negra.",
        "pantallaOscura": true,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#FFD100",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#88710A"
          },
          "papel": {
            "base": "#FFD100",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#FFEFA8"
          }
        },
        "muestra": {
          "fondo": "#0B0B0C",
          "panel": "#111113",
          "tinta": "#F5F4EF",
          "color": "#FFD100",
          "acento": "#111214",
          "vivo": "#111214",
          "banda": "#0E0E0F",
          "filete": "#FFD100",
          "accion": "#FFD100",
          "halo": "#FFEFA8"
        }
      },
      {
        "id": "ambar",
        "nombre": "Ámbar",
        "grupo": "Amarillo y negro",
        "descripcion": "Banda amarilla con letra negra y el botón del mismo amarillo: se reconoce de lejos.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#FFC200",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#FFEAA8"
          },
          "papel": {
            "base": "#FFC200",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#FFEAA8"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#FFC200",
          "acento": "#111214",
          "vivo": "#111214",
          "banda": "#FFC200",
          "filete": "#111214",
          "accion": "#FFC200",
          "halo": "#FFEAA8"
        }
      },
      {
        "id": "obsidiana",
        "nombre": "Obsidiana",
        "grupo": "Noche",
        "descripcion": "Negro con naranja fuerte: la banda negra con filete naranja y el botón naranja con letra negra.",
        "pantallaOscura": true,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#FF6B00",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#883E09"
          },
          "papel": {
            "base": "#FF6B00",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#FFCDA8"
          }
        },
        "muestra": {
          "fondo": "#0B0B0B",
          "panel": "#111111",
          "tinta": "#F4F2EE",
          "color": "#FF6B00",
          "acento": "#111214",
          "vivo": "#111214",
          "banda": "#111111",
          "filete": "#FF6B00",
          "accion": "#FF6B00",
          "halo": "#FFCDA8"
        }
      },
      {
        "id": "medianoche",
        "nombre": "Medianoche",
        "grupo": "Noche",
        "descripcion": "Azul noche con oro fuerte: la pareja clásica del lujo inmobiliario, con el botón oro.",
        "pantallaOscura": true,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#F2B705",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#806716"
          },
          "papel": {
            "base": "#F2B705",
            "texto": "#0B1F44",
            "icono": "#0B1F44",
            "halo": "#FBE7AA"
          }
        },
        "muestra": {
          "fondo": "#0A1220",
          "panel": "#0D1626",
          "tinta": "#EEF2F8",
          "color": "#F2B705",
          "acento": "#0B1F44",
          "vivo": "#0B1F44",
          "banda": "#0B1F44",
          "filete": "#F2B705",
          "accion": "#F2B705",
          "halo": "#FBE7AA"
        }
      },
      {
        "id": "circuito",
        "nombre": "Circuito",
        "grupo": "Tecnológicos",
        "descripcion": "Cian fuerte con letra negra: tecnológico y limpio; en pantalla, oscuro.",
        "pantallaOscura": true,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#00B2E3",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#05637F"
          },
          "papel": {
            "base": "#00B2E3",
            "texto": "#111214",
            "icono": "#111214",
            "halo": "#A8E5F5"
          }
        },
        "muestra": {
          "fondo": "#070D12",
          "panel": "#0A131A",
          "tinta": "#E6F3F7",
          "color": "#00B2E3",
          "acento": "#0080A3",
          "vivo": "#0080A3",
          "banda": "#00B2E3",
          "filete": "#111214",
          "accion": "#00B2E3",
          "halo": "#A8E5F5"
        }
      },
      {
        "id": "indigo",
        "nombre": "Índigo",
        "grupo": "Tecnológicos",
        "descripcion": "Violeta índigo fuerte: la banda, el botón y los iconos en el mismo color, sobre blanco.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#5B2BD9",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#C7B7F2"
          },
          "papel": {
            "base": "#5B2BD9",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#C7B7F2"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#5B2BD9",
          "acento": "#5B2BD9",
          "vivo": "#5B2BD9",
          "banda": "#5B2BD9",
          "filete": "#3A1A9E",
          "accion": "#5B2BD9",
          "halo": "#C7B7F2"
        }
      },
      {
        "id": "sivan-plata",
        "nombre": "SIVAN Plata",
        "grupo": "SIVAN",
        "descripcion": "Banda plata con letra azul profundo y filete azul real; el botón y los iconos, azul SIVAN.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#1961DC",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#B1C9F3"
          },
          "papel": {
            "base": "#1961DC",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#B1C9F3"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#1961DC",
          "acento": "#1961DC",
          "vivo": "#1961DC",
          "banda": "#DADDE2",
          "filete": "#0E43A6",
          "accion": "#1961DC",
          "halo": "#B1C9F3"
        }
      },
      {
        "id": "sivan-noche",
        "nombre": "SIVAN Noche",
        "grupo": "SIVAN",
        "descripcion": "Azul noche casi negro con el filete y el botón azul SIVAN: el más premium.",
        "pantallaOscura": true,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#1961DC",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#123A7F"
          },
          "papel": {
            "base": "#1961DC",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#B1C9F3"
          }
        },
        "muestra": {
          "fondo": "#080E1A",
          "panel": "#0B1222",
          "tinta": "#F1F3F7",
          "color": "#1961DC",
          "acento": "#1961DC",
          "vivo": "#1961DC",
          "banda": "#0B1426",
          "filete": "#1961DC",
          "accion": "#1961DC",
          "halo": "#B1C9F3"
        }
      },
      {
        "id": "sivan-azul",
        "nombre": "SIVAN Azul",
        "grupo": "SIVAN",
        "descripcion": "Banda azul real de SIVAN con filete plata; el botón y los iconos, azul SIVAN.",
        "pantallaOscura": false,
        "banda": true,
        "accion": {
          "pantalla": {
            "base": "#1961DC",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#B1C9F3"
          },
          "papel": {
            "base": "#1961DC",
            "texto": "#FFFFFF",
            "icono": "#FFFFFF",
            "halo": "#B1C9F3"
          }
        },
        "muestra": {
          "fondo": "#F5F4F0",
          "panel": "#FFFFFF",
          "tinta": "#111214",
          "color": "#1961DC",
          "acento": "#1961DC",
          "vivo": "#1961DC",
          "banda": "#0E43A6",
          "filete": "#B7B8BC",
          "accion": "#1961DC",
          "halo": "#B1C9F3"
        }
      }
    ]
  };
  if (typeof module === 'object' && module.exports) module.exports = TEMAS; else raiz.BROX_TEMAS_V08 = TEMAS;
})(this);
