
package com.truereturn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required.")
    @Size(max = 150, message = "Product name cannot exceed 150 characters.")
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters.")
    @Column(length = 2000)
    private String description;

    @NotNull(message = "Product price is required.")
    @DecimalMin(value = "0.0", message = "Price cannot be negative.")
    @Column(nullable = false)
    private Double price;

    @NotBlank(message = "Product code is required.")
    @Size(max = 100, message = "Product code cannot exceed 100 characters.")
    @Column(unique = true, nullable = false, length = 100)
    private String productCode;

    @Size(max = 100, message = "Category cannot exceed 100 characters.")
    @Column(length = 100)
    private String category;
    // Original registered product weight in grams
    @Column(nullable = false)
    private Double originalWeight;

    @NotBlank(message = "Product status is required.")
    @Column(nullable = false, length = 30)
    private String status;

    // Paths of the four original product photos
    @Column(length = 500)
    private String originalPhoto1;

    @Column(length = 500)
    private String originalPhoto2;

    @Column(length = 500)
    private String originalPhoto3;

    @Column(length = 500)
    private String originalPhoto4;

    // Default constructor required by JPA
    public Product() {
        this.status = "ACTIVE";
    }

    // Constructor
    public Product(
            String name,
            String description,
            double price,
            String productCode) {

        this.name = name;
        this.description = description;
        this.price = price;
        this.productCode = productCode;
        this.status = "ACTIVE";
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
    public Double getOriginalWeight() {
    return originalWeight;
    }

    public void setOriginalWeight(Double originalWeight) {
    this.originalWeight = originalWeight;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOriginalPhoto1() {
        return originalPhoto1;
    }

    public void setOriginalPhoto1(String originalPhoto1) {
        this.originalPhoto1 = originalPhoto1;
    }

    public String getOriginalPhoto2() {
        return originalPhoto2;
    }

    public void setOriginalPhoto2(String originalPhoto2) {
        this.originalPhoto2 = originalPhoto2;
    }

    public String getOriginalPhoto3() {
        return originalPhoto3;
    }

    public void setOriginalPhoto3(String originalPhoto3) {
        this.originalPhoto3 = originalPhoto3;
    }

    public String getOriginalPhoto4() {
        return originalPhoto4;
    }

    public void setOriginalPhoto4(String originalPhoto4) {
        this.originalPhoto4 = originalPhoto4;
    }
}
