package com.panaderia.sancarlos.model;

public class PlanInsumosRespuesta {

    private int idProduccion;
    private int productosSinRecetaCompleta;
    private boolean puedeIniciar;
    private String mensaje;
    private java.util.List<PlanInsumo> insumos;

    public PlanInsumosRespuesta() {
    }

    public int getIdProduccion() {
        return idProduccion;
    }

    public void setIdProduccion(int idProduccion) {
        this.idProduccion = idProduccion;
    }

    public int getProductosSinRecetaCompleta() {
        return productosSinRecetaCompleta;
    }

    public void setProductosSinRecetaCompleta(int productosSinRecetaCompleta) {
        this.productosSinRecetaCompleta = productosSinRecetaCompleta;
    }

    public boolean isPuedeIniciar() {
        return puedeIniciar;
    }

    public void setPuedeIniciar(boolean puedeIniciar) {
        this.puedeIniciar = puedeIniciar;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public java.util.List<PlanInsumo> getInsumos() {
        return insumos;
    }

    public void setInsumos(java.util.List<PlanInsumo> insumos) {
        this.insumos = insumos;
    }

}
