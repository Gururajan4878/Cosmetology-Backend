package com.cosmetology.service;

import com.cosmetology.model.User;
import com.cosmetology.repository.UserRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService implements InitializingBean {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    // Temporary in-memory storage for registration data before verification
    private final Map<String, TempUser> pendingRegistrations = new ConcurrentHashMap<>();

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
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        System.out.println("Checking if mobile exists: " + mobile);
        if (userRepository.findByMobile(mobile).isPresent()) {
            throw new RuntimeException("Mobile number already registered");
        }

        System.out.println("Generating OTP");
        String otp = generateOtp();
        Instant otpExpiry = Instant.now().plus(10, ChronoUnit.MINUTES);

        // Store temporarily in memory
        pendingRegistrations.put(normalizedEmail, new TempUser(
                normalizedEmail,
                passwordEncoder.encode(password),
                mobile,
                otp,
                otpExpiry
        ));

        System.out.println("Sending OTP SMS");
        sendOtpSms(mobile, otp);
        System.out.println("OTP sent: " + otp + " to " + mobile);
    }

    private void sendOtpSms(String toMobile, String otp) {
        int maxRetries = 3;
        int delayBetweenRetriesMs = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String messageBody = "Your OTP code is: " + otp;
                Message.creator(
                        new com.twilio.type.PhoneNumber(toMobile),
                        new com.twilio.type.PhoneNumber(twilioPhoneNumber),
                        messageBody
                ).create();

                System.out.println("OTP sent successfully to " + toMobile + " on attempt " + attempt);
                return;

            } catch (Exception e) {
                System.err.println("Failed to send OTP SMS on attempt " + attempt + ": " + e.getMessage());

                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to send OTP after " + maxRetries + " attempts", e);
                }

                try {
                    Thread.sleep(delayBetweenRetriesMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("OTP sending interrupted", ie);
                }
            }
        }
    }

    public User verifyOtp(String email, String otp) {
        String normalizedEmail = email.toLowerCase();
        TempUser tempUser = pendingRegistrations.get(normalizedEmail);

        if (tempUser == null) {
            throw new RuntimeException("No pending registration found");
        }

        if (!tempUser.getOtp().equals(otp) || Instant.now().isAfter(tempUser.getOtpExpiry())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        // Create actual user and save in DB
        User user = new User();
        user.setEmail(tempUser.getEmail());
        user.setPassword(tempUser.getPasswordHash());
        user.setMobile(tempUser.getMobile());
        user.setVerified(true);

        userRepository.save(user);

        // Remove from pending registrations
        pendingRegistrations.remove(normalizedEmail);

        return user;
    }

    public User loginWithIdentifier(String identifier, String password) {
        Optional<User> userOpt = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByMobile(identifier);

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

    public void sendForgotPasswordEmailOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not registered"));

        String otp = generateOtp();
        user.setForgotPasswordOtp(otp);
        user.setForgotPasswordOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        System.out.println("📌 TEST MODE: Forgot Password OTP (email) for " + email + " is " + otp);
    }

    public void sendForgotPasswordPhoneOtp(String phone) {
        User user = userRepository.findByMobile(phone)
                .orElseThrow(() -> new RuntimeException("Phone number not registered"));

        String otp = generateOtp();
        user.setForgotPasswordOtp(otp);
        user.setForgotPasswordOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

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

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String generateResetToken() {
        return UUID.randomUUID().toString();
    }

    // Inner class for storing temporary registration data
    private static class TempUser {
        private final String email;
        private final String passwordHash;
        private final String mobile;
        private final String otp;
        private final Instant otpExpiry;

        public TempUser(String email, String passwordHash, String mobile, String otp, Instant otpExpiry) {
            this.email = email;
            this.passwordHash = passwordHash;
            this.mobile = mobile;
            this.otp = otp;
            this.otpExpiry = otpExpiry;
        }

        public String getEmail() { return email; }
        public String getPasswordHash() { return passwordHash; }
        public String getMobile() { return mobile; }
        public String getOtp() { return otp; }
        public Instant getOtpExpiry() { return otpExpiry; }
    }
}