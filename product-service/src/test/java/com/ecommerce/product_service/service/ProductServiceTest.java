package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.ProductRequestDto;
import com.ecommerce.product_service.dto.ProductResponseDto;
import com.ecommerce.product_service.exception.ResourceNotFoundException;
import com.ecommerce.product_service.mapper.ProductMapper;
import com.ecommerce.product_service.model.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService - Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper mapper;

    @InjectMocks
    private ProductService productService;

    // ========= Datos de pruebas reutilizables =========
    private ProductRequestDto productRequest;
    private Product mappedProduct;
    private Product savedProduct;
    private ProductResponseDto productResponse;

    private static final String PRODUCT_ID = "prod-abc-123";

    @BeforeEach
    void setUp() {

        productRequest = new ProductRequestDto(
                "Laptop Gaming",
                "Laptop de alto rendimiento",
                new BigDecimal("1500.00")
        );

        // Producto que retorna el mapper a partir del request (sin ID aun)
        mappedProduct = Product.builder()
                .name("Laptop Gaming")
                .description("Laptop de alto rendimiento")
                .price(new BigDecimal("1500.00"))
                .build();

        // Producto que retorna el repositorio luego de persistir (con ID asignado por MongoDB)
        savedProduct = Product.builder()
                .id(PRODUCT_ID)
                .name("Laptop Gaming")
                .description("Laptop de alto rendimiento")
                .price(new BigDecimal("1500.00"))
                .build();

        productResponse = new ProductResponseDto(
                PRODUCT_ID,
                "Laptop Gaming",
                "Laptop de alto rendimiento",
                new BigDecimal("1500.00")
        );


    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("createProduct()")
    class CreateProductTests {

        @Test
        @DisplayName("Debe crear y retornar el producto correctamente")
        void createProduct_HappyPath_ReturnsProductResponse() {
            // --- Arrange ---
            when(mapper.toProduct(productRequest)).thenReturn(mappedProduct);
            when(productRepository.save(mappedProduct)).thenReturn(savedProduct);
            when(mapper.toDto(savedProduct)).thenReturn(productResponse);

            // --- Act ---
            ProductResponseDto result = productService.createProduct(productRequest);

            // --- Assert ---
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(PRODUCT_ID);
            assertThat(result.name()).isEqualTo("Laptop Gaming");
            assertThat(result.price()).isEqualByComparingTo(new BigDecimal("1500.00"));

            // El flujo completo debe ejecutarse: mapper -> repository -> mapper
            verify(mapper).toProduct(productRequest);
            verify(productRepository).save(mappedProduct);
            verify(mapper).toDto(savedProduct);

        }

        @Test
        @DisplayName("Debe delegar el guardado al repositorio exactamente una vez")
        void createProduct_ShouldSaveExactlyOnce() {
            // --- Arrange ---
            when(mapper.toProduct(productRequest)).thenReturn(mappedProduct);
            when(productRepository.save(mappedProduct)).thenReturn(savedProduct);
            when(mapper.toDto(savedProduct)).thenReturn(productResponse);

            // --- Act ---
            productService.createProduct(productRequest);

            // --- Assert ---
            verify(productRepository, times(1)).save(any(Product.class));
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getAllProducts()")
    class GetAllProductsTests {

        @Test
        @DisplayName("Debe retornar todos los productos disponibles")
        void getAllProducts_whenProductsExist_ReturnsMappedList() {
            // --- Arrange ---
            Product secondProduct = Product.builder()
                    .id("prod-xyz-999")
                    .name("Mouse Inalambrico")
                    .price(new BigDecimal("25.00"))
                    .build();

            ProductResponseDto secondResponse = new ProductResponseDto(
                    "prod-xyz-999", "Mouse Inalambrico", null, new BigDecimal("25.00")
            );

            when(productRepository.findAll()).thenReturn(List.of(savedProduct, secondProduct));
            when(mapper.toDto(savedProduct)).thenReturn(productResponse);
            when(mapper.toDto(secondProduct)).thenReturn(secondResponse);

            // --- Act ---
            List<ProductResponseDto> result = productService.getAllProducts();

            // --- Assert ---
            assertThat(result).hasSize(2);
            assertThat(result).extracting(ProductResponseDto::name)
                    .containsExactly("Laptop Gaming", "Mouse Inalambrico");

        }

        @Test
        @DisplayName("Debe retornar lista vacia cuando no hay productos")
        void getAllProducts_WhenNoProducts_ReturnsEmptyList() {
            // --- Arrange ---
            when(productRepository.findAll()).thenReturn(List.of());

            // --- Act ---
            List<ProductResponseDto> result = productService.getAllProducts();

            // --- Assert ---
            assertThat(result).isEmpty();

            // Si no hay productos, el mapper no debe ser invocado
            verify(mapper, never()).toDto(any());
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("Debe retornar el producto cuando el ID existe")
        void getById_WhenExists_ReturnsProduct() {
            // --- Arrange ---
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(savedProduct));
            when(mapper.toDto(savedProduct)).thenReturn(productResponse);

            // --- Act ---
            ProductResponseDto result = productService.getById(PRODUCT_ID);

            // --- Assert ---
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(PRODUCT_ID);
            assertThat(result.name()).isEqualTo("Laptop Gaming");

        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el ID no existe")
        void getById_WhenNotFound_ThrowsResourceNotFoundException() {
            // --- Arrange ---
            when(productRepository.findById("id-inexistente")).thenReturn(Optional.empty());

            // --- Act & Assert ---
            assertThatThrownBy(() -> productService.getById("id-inexistente"))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Si el producto no existe, el mapper no debe ser invocado
            verify(mapper, never()).toDto(any());

        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateProduct()")
    class UpdateProductTests {

        @Test
        @DisplayName("Debe actualizar todos los campos y retornar el producto actualizado")
        void updateProduct_WhenExists_UpdatesAndReturnsProduct() {
            // --- Arrange ---
            ProductRequestDto updateRequest =  new ProductRequestDto(
                    "Laptop Gaming Pro",
                    "Version mejorada",
                    new BigDecimal("1800.00")
            );

            ProductResponseDto updateResponse = new ProductResponseDto(
                    PRODUCT_ID, "Laptop Gaming Pro", "Version mejorada", new BigDecimal("1800.00")
            );

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(savedProduct));
            when(productRepository.save(savedProduct)).thenReturn(savedProduct);
            when(mapper.toDto(savedProduct)).thenReturn(updateResponse);

            // --- Act ---
            ProductResponseDto result = productService.updateProduct(PRODUCT_ID, updateRequest);

            // --- Assert ---
            assertThat(result.name()).isEqualTo("Laptop Gaming Pro");
            assertThat(result.price()).isEqualByComparingTo(new BigDecimal("1800.00"));

            // Verificamos que los campos se sobreescribieron directamente en el objeto
            assertThat(savedProduct.getName()).isEqualTo("Laptop Gaming Pro");
            assertThat(savedProduct.getDescription()).isEqualTo("Version mejorada");
            assertThat(savedProduct.getPrice()).isEqualByComparingTo(new BigDecimal("1800.00"));

            verify(productRepository).save(savedProduct);
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el ID no existe")
        void updateProduct_WhenNotFound_ThrowsResourceNotFoundException() {
            // --- Arrange ---
            when(productRepository.findById("id-inexistente")).thenReturn(Optional.empty());

            // --- Act & Assert ---
            assertThatThrownBy(() -> productService.updateProduct("id-inexistente", productRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Si no se encontro el producto, no debe intentar guardar nada
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe permitir actualiuzar la descripcion a null (campo opcional)")
        void updateProduct_WithNullDescription_UpdatesSuccessfully() {
            // --- Arrange ---
            ProductRequestDto requestSinDescripcion =  new ProductRequestDto(
                    "Laptop Gaming", null, new BigDecimal("1500.00")
            );

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(savedProduct));
            when(productRepository.save(savedProduct)).thenReturn(savedProduct);
            when(mapper.toDto(savedProduct)).thenReturn(productResponse);

            // --- Act ---
            productService.updateProduct(PRODUCT_ID, requestSinDescripcion);

            // --- Assert ---
            // La descripcion null es valida segun el DTO (campo opcional)
            assertThat(savedProduct.getDescription()).isNull();
            verify(productRepository).save(savedProduct);


        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteById()")
    class DeleteByIdTests {

        @Test
        @DisplayName("Debe eliminar el producto cuando el ID existe")
        void deleteById_WhenExists_DeletesSuccessfully() {
            // --- Arrange ---
            when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);

            // --- Act ---
            productService.deleteById(PRODUCT_ID);

            // --- Assert ---
            verify(productRepository, times(1)).deleteById(PRODUCT_ID);
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el ID no existe")
        void deleteById_WhenNotFound_ThrowsResourceNotFoundException() {
            // --- Arrange ---
            when(productRepository.existsById("id-inexistente")).thenReturn(false);

            // --- Act & Assert ---
            assertThatThrownBy(() -> productService.deleteById("id-inexistente"))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Si el producto no existe, no debe intentar eliminar nada
            verify(productRepository, never()).deleteById(any());
        }

    }

}