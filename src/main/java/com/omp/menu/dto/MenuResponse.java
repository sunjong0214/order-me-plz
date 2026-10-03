package com.omp.menu.dto;

public record MenuResponse(Long id, String name, int price, boolean isSoldOut, String description) {
}
