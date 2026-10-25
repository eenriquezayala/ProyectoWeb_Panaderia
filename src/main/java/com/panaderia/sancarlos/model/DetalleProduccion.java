
package com.panaderia.sancarlos.model;

public class DetalleProduccion {

    private String producto;
    private int cantidadProgramada;
    private int cantidadProducida;

    public DetalleProduccion() {
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public int getCantidadProgramada() {
        return cantidadProgramada;
    }

    public void setCantidadProgramada(int cantidadProgramada) {
        this.cantidadProgramada = cantidadProgramada;
    }

    public int getCantidadProducida() {
        return cantidadProducida;
    }

    public void setCantidadProducida(int cantidadProducida) {
        this.cantidadProducida = cantidadProducida;
    }
}
