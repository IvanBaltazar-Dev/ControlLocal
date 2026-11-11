package com.controllocal.service.archivo;

import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.domain.persona.Persona;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenciasBinariasTest {
    @Test void otraVersionDeFotoTieneOtraReferenciaPeroUnaMismaClaveNoLaRota() {
        var foto = new FotoPropiedad(); foto.setClave("original.png");
        var primera = foto.getArchivoRef(); assertNotNull(primera);
        foto.setClave("original.png"); assertEquals(primera, foto.getArchivoRef());
        foto.setClave("nuevo.png"); assertNotEquals(primera, foto.getArchivoRef());
    }

    @Test void documentoSinBinarioNoInventaReferencia() {
        var documento = new DocumentoSolicitud();
        assertNull(documento.getArchivoRef());
        documento.setRutaArchivo(" "); assertNull(documento.getArchivoRef());
        documento.setRutaArchivo("privado.pdf"); var primera = documento.getArchivoRef(); assertNotNull(primera);
        documento.setRutaArchivo("nuevo.pdf"); assertNotEquals(primera, documento.getArchivoRef());
        documento.setRutaArchivo(null); assertNull(documento.getArchivoRef());
    }

    @Test void cambiarAvatarRevocaLaReferenciaAnteriorSinConvertirRutaEnPermiso() {
        var persona = new Persona(); persona.setFotoClave("original.png"); var primera = persona.getFotoRef();
        assertNotNull(primera);
        persona.setFotoClave("otra.png"); assertNotEquals(primera, persona.getFotoRef());
        persona.setFotoClave(null); assertNull(persona.getFotoRef());
    }
}
