package com.example.demo.dto;

import java.util.List;

public record PrediccionesResponse(String semana, List<PrediccionDto> predicciones) {}
