package com.panaderia.sancarlos.model;

public class DetallePedido {

    private String producto;
    private int cantidadManana;
    private int cantidadResto;
    private int cantidadTarde;
    private int total;

    public DetallePedido() {
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public int getCantidadManana() {
        return cantidadManana;
    }

    public void setCantidadManana(int cantidadManana) {
        this.cantidadManana = cantidadManana;
    }

    public int getCantidadResto() {
        return cantidadResto;
    }

    public void setCantidadResto(int cantidadResto) {
        this.cantidadResto = cantidadResto;
    }

    public int getCantidadTarde() {
        return cantidadTarde;
    }

    public void setCantidadTarde(int cantidadTarde) {
        this.cantidadTarde = cantidadTarde;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}