package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.ProductRequestDto;
import com.ecommerce.product_service.dto.ProductResponseDto;
import com.ecommerce.product_service.exception.ResourceNotFoundException;
import com.ecommerce.product_service.mapper.ProductMapper;
import com.ecommerce.product_service.model.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService implements IProductService{

    private final ProductRepository productRepository;
    private final ProductMapper mapper;

    @Override
    public ProductResponseDto createProduct(ProductRequestDto requestDto) {
        return mapper.toDto(
                productRepository.save(
                        mapper.toProduct(requestDto)));
    }

    @Override
    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public ProductResponseDto getById(String id) {
        return productRepository.findById(id).stream()
                .map(mapper::toDto)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Producto","id",id));
    }

    @Override
    public ProductResponseDto updateProduct(String id, ProductRequestDto requestDto) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto","id",id));

        product.setName(requestDto.name());
        product.setDescription(requestDto.description());
        product.setPrice(requestDto.price());

        return mapper.toDto(productRepository.save(product));

    }

    @Override
    public void deleteById(String id) {

        if(!productRepository.existsById(id)) {
           throw new ResourceNotFoundException("Producto","id",id);
        }

        productRepository.deleteById(id);
    }
}




