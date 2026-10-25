
package com.truereturn.dto;

public class VerificationResponse {

    private boolean valid;
    private String message;
    private String productCode;
    private String productName;
    private double price;
    private String status;
    private String category;

    private String mlResult;
    private String rfidResult;
    private String weightResult;
    private String finalResult;
    private String remark;

    // Default constructor
    public VerificationResponse() {
    }

    // Constructor for product verification responses
    public VerificationResponse(
            boolean valid,
            String message,
            String productCode,
            String productName,
            double price,
            String status) {

        this.valid = valid;
        this.message = message;
        this.productCode = productCode;
        this.productName = productName;
        this.price = price;
        this.status = status;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMlResult() {
        return mlResult;
    }

    public void setMlResult(String mlResult) {
        this.mlResult = mlResult;
    }

    public String getRfidResult() {
        return rfidResult;
    }

    public void setRfidResult(String rfidResult) {
        this.rfidResult = rfidResult;
    }

    public String getWeightResult() {
        return weightResult;
    }

    public void setWeightResult(String weightResult) {
        this.weightResult = weightResult;
    }

    public String getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(String finalResult) {
        this.finalResult = finalResult;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
