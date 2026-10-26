package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Celda de la matriz de tarifas: costo de llevar un pedido de zonaOrigen a zonaDestino (centavos). */
@Document(collection = "tarifas_envio")
public class TarifaEnvio {

    @Id
    private String id;

    @Field("zona_origen")
    private Integer zonaOrigen;

    @Field("zona_destino")
    private Integer zonaDestino;

    @Field("valor_centavos")
    private Long valorCentavos;

    public TarifaEnvio() {
    }

    public TarifaEnvio(Integer zonaOrigen, Integer zonaDestino, Long valorCentavos) {
        this.zonaOrigen = zonaOrigen;
        this.zonaDestino = zonaDestino;
        this.valorCentavos = valorCentavos;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getZonaOrigen() { return zonaOrigen; }
    public void setZonaOrigen(Integer zonaOrigen) { this.zonaOrigen = zonaOrigen; }

    public Integer getZonaDestino() { return zonaDestino; }
    public void setZonaDestino(Integer zonaDestino) { this.zonaDestino = zonaDestino; }

    public Long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(Long valorCentavos) { this.valorCentavos = valorCentavos; }
}
