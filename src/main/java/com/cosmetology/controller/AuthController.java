package com.cosmetology.controller;

import com.cosmetology.model.User;
import com.cosmetology.security.JwtUtil;
import com.cosmetology.service.UserService;
// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register-initiate")
public ResponseEntity<?> registerInitiate(@RequestBody RegisterRequest r) {
    try {
        System.out.println("Register initiate called for email: " + r.email);

        // Call AbstractAPI email validation here:
//         String apiKey = "9512604653ec49be89183935387b7709";
//         String apiUrl = "https://emailvalidation.abstractapi.com/v1/?api_key=" + apiKey + "&email=" + r.email;

//         String result = org.apache.http.client.fluent.Request.Get(apiUrl)
//                 .execute()
//                 .returnContent()
//                 .asString();

//         System.out.println("AbstractAPI response: " + result);

//         ObjectMapper mapper = new ObjectMapper();
//         JsonNode json = mapper.readTree(result);

//         JsonNode validFormatNode = json.get("is_valid_format");
//         JsonNode smtpValidNode = json.get("is_smtp_valid");


//         JsonNode validFormatValueNode = validFormatNode.get("value");
//         JsonNode smtpValidValueNode = smtpValidNode.get("value");

//        if (validFormatValueNode == null || smtpValidValueNode == null) {
//       return ResponseEntity.badRequest().body(new MessageResponse("Email validation service returned incomplete data"));
// }

// boolean validFormat = validFormatValueNode.asBoolean(false);
// boolean smtpValid = smtpValidValueNode.asBoolean(false);

// if (!validFormat || !smtpValid) {
//     return ResponseEntity.badRequest().body(new MessageResponse("Invalid or non-existent email address."));
// }


        // Proceed with your existing registration initiation logic

        
        userService.initiateRegistration(r.email, r.password, r.mobile);

        System.out.println("Register initiate success");
        return ResponseEntity.ok().body(new MessageResponse("OTP sent"));
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
    }
}


    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpRequest r) {
        try {
            User user = userService.verifyOtp(r.email, r.otp);
            String token = jwtUtil.generateToken(user.getEmail());
            return ResponseEntity.ok(new TokenResponse(token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    // UPDATED LOGIN: accept identifier (email or mobile) + password
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest r) {
        try {
            User user = userService.loginWithIdentifier(r.identifier, r.password);
            // Always generate token with user email for consistency
            String token = jwtUtil.generateToken(user.getEmail());
            return ResponseEntity.ok(new TokenResponse(token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    // --- Forgot Password endpoints (unchanged) ---
    @PostMapping("/forgot-password-email")
    public ResponseEntity<?> forgotPasswordEmail(@RequestBody EmailRequest r) {
        try {
            userService.sendForgotPasswordEmailOtp(r.email);
            return ResponseEntity.ok(new MessageResponse("OTP sent to email"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/forgot-password-phone")
    public ResponseEntity<?> forgotPasswordPhone(@RequestBody PhoneRequest r) {
        try {
            userService.sendForgotPasswordPhoneOtp(r.mobile);
            return ResponseEntity.ok(new MessageResponse("OTP sent to mobile"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/forgot-verify-email-otp")
    public ResponseEntity<?> forgotVerifyEmailOtp(@RequestBody OtpEmailRequest r) {
        try {
            String resetToken = userService.verifyForgotPasswordEmailOtp(r.email, r.otp);
            return ResponseEntity.ok(new ResetTokenResponse(resetToken));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/forgot-verify-phone-otp")
    public ResponseEntity<?> forgotVerifyPhoneOtp(@RequestBody OtpPhoneRequest r) {
        try {
            String resetToken = userService.verifyForgotPasswordPhoneOtp(r.mobile, r.otp);
            return ResponseEntity.ok(new ResetTokenResponse(resetToken));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest r) {
        try {
            userService.resetPassword(r.resetToken, r.newPassword);
            return ResponseEntity.ok(new MessageResponse("Password reset successful"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    // --- DTOs ---
    public static class RegisterRequest {
         public String email;
         public String password;
         public String mobile;
         public String confirmPassword;
        }
    public static class OtpRequest { public String email; public String otp; }
    
    // UPDATED LOGIN DTO: identifier = email or mobile
    public static class LoginRequest { public String identifier; public String password; }
    
    public static class EmailRequest { public String email; }
    public static class PhoneRequest { public String mobile; }
    public static class OtpEmailRequest { public String email; public String otp; }
    public static class OtpPhoneRequest { public String mobile; public String otp; }
    public static class ResetPasswordRequest { public String resetToken; public String newPassword; }

    public static class TokenResponse { public String token; public TokenResponse(String t){token=t;} }
    public static class ResetTokenResponse { public String resetToken; public ResetTokenResponse(String t){resetToken=t;} }
    public static class MessageResponse { public String message; public MessageResponse(String m){message=m;} }
}
