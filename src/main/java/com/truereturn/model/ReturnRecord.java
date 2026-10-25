
package com.truereturn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "return_records")
public class ReturnRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String productCode;

    @Size(max = 1000)
    @Column(length = 1000)
    private String reason;

    // Verification details
    @Column(length = 100)
    private String category;

    // Paths of the four returned product photos
    @Column(length = 500)
    private String photo1;

    @Column(length = 500)
    private String photo2;

    @Column(length = 500)
    private String photo3;

    @Column(length = 500)
    private String photo4;

    @Column(length = 50)
    private String mlResult;

    @Column(length = 100)
    private String rfidResult;

    @Column(length = 100)
    private String weightResult;

    @Column(length = 50)
    private String finalResult;

    @Size(max = 2000)
    @Column(length = 2000)
    private String remark;

    @Column(length = 50)
    private String approvalStatus;

    @Column(nullable = false)
    private LocalDateTime returnDate;

    public ReturnRecord() {
        this.returnDate = LocalDateTime.now();
    }

    public ReturnRecord(String productCode, String reason) {
        this.productCode = productCode;
        this.reason = reason;
        this.returnDate = LocalDateTime.now();
    }

    // Initialize the date if it was not explicitly provided.
    @PrePersist
    protected void onCreate() {
        if (this.returnDate == null) {
            this.returnDate = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPhoto1() {
        return photo1;
    }

    public void setPhoto1(String photo1) {
        this.photo1 = photo1;
    }

    public String getPhoto2() {
        return photo2;
    }

    public void setPhoto2(String photo2) {
        this.photo2 = photo2;
    }

    public String getPhoto3() {
        return photo3;
    }

    public void setPhoto3(String photo3) {
        this.photo3 = photo3;
    }

    public String getPhoto4() {
        return photo4;
    }

    public void setPhoto4(String photo4) {
        this.photo4 = photo4;
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

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public LocalDateTime getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDateTime returnDate) {
        this.returnDate = returnDate;
    }
}
