package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.ProductRequestDto;
import com.ecommerce.product_service.dto.ProductResponseDto;

import java.util.List;

public interface IProductService {

    ProductResponseDto createProduct(ProductRequestDto requestDto);
    List<ProductResponseDto> getAllProducts();
    ProductResponseDto getById(String id);
    ProductResponseDto updateProduct(String id, ProductRequestDto requestDto);
    void deleteById(String id);

}
