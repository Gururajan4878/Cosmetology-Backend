package com.cosmetology.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String email;
    private String password;
    private String mobile;

    private String otp;
    private Instant otpExpiry;

    private boolean verified = false;

    private boolean otpVerified = false;

    private List<String> paidVideoIds = new ArrayList<>();

    // Forgot password fields
    private String forgotPasswordOtp;
    private Instant forgotPasswordOtpExpiry;

    private String resetToken;
    private Instant resetTokenExpiry;

    // ===== Constructors =====
    public User() {}

    public User(String email, String password, String mobile) {
        this.email = email;
        this.password = password;
        this.mobile = mobile;
        this.verified = false;
        this.otpVerified = false;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public Instant getOtpExpiry() {
        return otpExpiry;
    }

    public void setOtpExpiry(Instant otpExpiry) {
        this.otpExpiry = otpExpiry;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean isotpVerified() {
    return otpVerified;
}

public void setotpVerified(boolean otpVerified) {
    this.otpVerified = otpVerified;
}

    public List<String> getPaidVideoIds() {
        return paidVideoIds;
    }

    public void setPaidVideoIds(List<String> paidVideoIds) {
        this.paidVideoIds = paidVideoIds;
    }

    public boolean hasPaidForVideo(String videoId) {
        return paidVideoIds != null && paidVideoIds.contains(videoId);
    }

    // Forgot password OTP getter/setter
    public String getForgotPasswordOtp() {
        return forgotPasswordOtp;
    }

    public void setForgotPasswordOtp(String forgotPasswordOtp) {
        this.forgotPasswordOtp = forgotPasswordOtp;
    }

    public Instant getForgotPasswordOtpExpiry() {
        return forgotPasswordOtpExpiry;
    }

    public void setForgotPasswordOtpExpiry(Instant forgotPasswordOtpExpiry) {
        this.forgotPasswordOtpExpiry = forgotPasswordOtpExpiry;
    }

    // Reset token getter/setter
    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public Instant getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(Instant resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }
}
