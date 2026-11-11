package com.controllocal.service.presupuesto;
import java.util.UUID;
/** Descriptor binario neutral, privado y ligado al sobre inmutable; nunca permiso o bandera NO_IO. */
public record DescriptorSalidaEvaluacion(UUID archivo,UUID operacionIo,String huella,long bytes,String mime){
    public DescriptorSalidaEvaluacion{
        if(archivo==null||operacionIo==null||bytes<0||huella==null&&bytes!=0
                ||huella!=null&&(!huella.matches("[0-9a-f]{64}")||bytes<=0||mime==null||mime.isBlank()))
            throw new IllegalArgumentException("Descriptor de salida no íntegro.");
    }
    @Override public String toString(){return "DescriptorSalidaEvaluacion[privado]";}
}
