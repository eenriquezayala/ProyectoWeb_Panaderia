package com.panaderia.sancarlos.model;

public class AgregarProductoPedidoRequest {

    private int idProducto;
    private int manana;
    private int resto;
    private int tarde;

    public AgregarProductoPedidoRequest() {
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public int getManana() {
        return manana;
    }

    public void setManana(int manana) {
        this.manana = manana;
    }

    public int getResto() {
        return resto;
    }

    public void setResto(int resto) {
        this.resto = resto;
    }

    public int getTarde() {
        return tarde;
    }

    public void setTarde(int tarde) {
        this.tarde = tarde;
    }
}