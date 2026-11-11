package com.controllocal.web.dto;

import com.controllocal.service.HallazgoService;

public record HallazgoResponse(String id,String tipo,String titulo,String porQue,CoincidenciaResponse aptitud,
                               String destino,Long idDemanda,Long idCaptacion,String codigoCaptacion) {
    public static HallazgoResponse desde(HallazgoService.Hallazgo h) {
        return new HallazgoResponse(h.id(),h.tipo(),h.titulo(),h.porQue(),
                h.aptitud()==null?null:CoincidenciaResponse.desde(h.aptitud()),h.destino(),h.idDemanda(),h.idCaptacion(),h.codigoCaptacion());
    }
}