package com.cosmetology.service;

import com.cosmetology.model.User;
import com.cosmetology.repository.UserRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
// import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class UserService implements InitializingBean {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final Random random = new Random();

    @Value("${twilio.account.sid}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token}")
    private String twilioAuthToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void afterPropertiesSet() {
        Twilio.init(twilioAccountSid, twilioAuthToken);
    }

    public void initiateRegistration(String email, String password, String mobile) {
    String normalizedEmail = email.toLowerCase();

    System.out.println("Checking if email exists: " + normalizedEmail);
    Optional<User> existingUserByEmail = userRepository.findByEmail(normalizedEmail);
    if (existingUserByEmail.isPresent()) {
        throw new RuntimeException("Email already registered");
    }

    System.out.println("Checking if mobile exists: " + mobile);
    Optional<User> existingUserByMobile = userRepository.findByMobile(mobile);
    if (existingUserByMobile.isPresent()) {
        throw new RuntimeException("Mobile number already registered");
    }

    System.out.println("Creating new user");
    User user = new User();
    user.setEmail(normalizedEmail);
    user.setPassword(passwordEncoder.encode(password));
    user.setMobile(mobile);
    user.setotpVerified(false);

    String otp = generateOtp();
    user.setOtp(otp);
    user.setOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));

    System.out.println("Saving user to DB");
    userRepository.save(user);

    System.out.println("Sending OTP SMS");
    sendOtpSms(mobile, otp);

    System.out.println("OTP sent: " + otp + " to " + mobile);
}


    private void sendOtpSms(String toMobile, String otp) {
    try {
        String messageBody = "Your OTP code is: " + otp;
        Message.creator(
                new com.twilio.type.PhoneNumber(toMobile),
                new com.twilio.type.PhoneNumber(twilioPhoneNumber),
                messageBody
        ).create();
    } catch (Exception e) {
        System.err.println("Failed to send OTP SMS: " + e.getMessage());
        // Optionally log the error or handle fallback (like console print)
    }
}


    public User verifyOtp(String email, String otp) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOpt.get();

         if (user.getOtp() == null || !user.getOtp().equals(otp) || user.getOtpExpiry() == null
            || Instant.now().isAfter(user.getOtpExpiry())) {
        // OTP invalid → delete user
        userRepository.delete(user);
        throw new RuntimeException("Invalid or expired OTP");
        }

        user.setOtp(null);
        user.setOtpExpiry(null);
        user.setVerified(true);
        user.setotpVerified(true);

        userRepository.save(user);
        return user;
    }

    public User loginWithIdentifier(String identifier, String password) {
        Optional<User> userOpt;

        if (identifier.contains("@")) {
            userOpt = userRepository.findByEmail(identifier);
        } else {
            userOpt = userRepository.findByMobile(identifier);
        }

        if (userOpt.isEmpty()) {
            throw new RuntimeException("Authentication Error - invalid user");
        }

        User user = userOpt.get();

        if (!user.isVerified()) {
            throw new RuntimeException("Account not verified. Please complete OTP verification.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Authentication Error - invalid password");
        }

        return user;
    }

    // ----------- Forgot Password Feature -----------

    public void sendForgotPasswordEmailOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not registered"));

        String otp = generateOtp();
        user.setForgotPasswordOtp(otp);
        user.setForgotPasswordOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        System.out.println("📌 TEST MODE: Forgot Password OTP (email) for " + email + " is " + otp);

        // Here you would send OTP via email using your email service.
    }

    public void sendForgotPasswordPhoneOtp(String phone) {
        User user = userRepository.findByMobile(phone)
                .orElseThrow(() -> new RuntimeException("Phone number not registered"));

        String otp = generateOtp();
        user.setForgotPasswordOtp(otp);
        user.setForgotPasswordOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        System.out.println("📌 TEST MODE: Forgot Password OTP (phone) for " + phone + " is " + otp);

        // Send OTP SMS using Twilio
        sendOtpSms(phone, otp);
    }

    public String verifyForgotPasswordEmailOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not registered"));

        if (user.getForgotPasswordOtp() == null || !user.getForgotPasswordOtp().equals(otp) ||
                user.getForgotPasswordOtpExpiry() == null || Instant.now().isAfter(user.getForgotPasswordOtpExpiry())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        user.setForgotPasswordOtp(null);
        user.setForgotPasswordOtpExpiry(null);

        String resetToken = generateResetToken();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(Instant.now().plus(30, ChronoUnit.MINUTES));

        userRepository.save(user);
        return resetToken;
    }

    public String verifyForgotPasswordPhoneOtp(String phone, String otp) {
        User user = userRepository.findByMobile(phone)
                .orElseThrow(() -> new RuntimeException("Phone number not registered"));

        if (user.getForgotPasswordOtp() == null || !user.getForgotPasswordOtp().equals(otp) ||
                user.getForgotPasswordOtpExpiry() == null || Instant.now().isAfter(user.getForgotPasswordOtpExpiry())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        user.setForgotPasswordOtp(null);
        user.setForgotPasswordOtpExpiry(null);

        String resetToken = generateResetToken();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(Instant.now().plus(30, ChronoUnit.MINUTES));

        userRepository.save(user);
        return resetToken;
    }

    public void resetPassword(String resetToken, String newPassword) {
        User user = userRepository.findByResetToken(resetToken)
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (user.getResetTokenExpiry() == null || Instant.now().isAfter(user.getResetTokenExpiry())) {
            throw new RuntimeException("Reset token expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        userRepository.save(user);
    }

    // ------- Helper Methods --------
    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String generateResetToken() {
        return UUID.randomUUID().toString();
    }
}
