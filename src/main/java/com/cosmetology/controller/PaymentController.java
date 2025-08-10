package com.cosmetology.controller;

import com.cosmetology.repository.UserRepository;
import com.cosmetology.security.JwtUtil;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "http://localhost:3000")
public class PaymentController {

    @Value("${razorpay.key_id}")
    private String razorpayKeyId;

    @Value("${razorpay.key_secret}")
    private String razorpayKeySecret;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public PaymentController(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                         @RequestBody CreateOrderRequest req) {
        String email = jwtUtil.extractUsernameFromAuthHeader(authHeader);
        if (email == null) return ResponseEntity.status(401).body(Map.of("message", "Invalid or missing token"));

        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", req.getAmount() * 100); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1);

            Order order = client.orders.create(orderRequest);

            return ResponseEntity.ok(Map.of(
                    "orderId", order.get("id"),
                    "amount", req.getAmount(),
                    "key", razorpayKeyId
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Failed to create order", "error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                    @RequestBody VerifyRequest req) {
        String email = jwtUtil.extractUsernameFromAuthHeader(authHeader);
        if (email == null) return ResponseEntity.status(401).body(Map.of("message", "Invalid or missing token"));

        try {
            String signature = hmacSha256(req.getRazorpay_order_id() + "|" + req.getRazorpay_payment_id(), razorpayKeySecret);
            if (!signature.equals(req.getRazorpay_signature())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid payment signature"));
            }

            userRepository.findByEmail(email).ifPresent(user -> {
                user.getPaidVideoIds().add(req.getVideoId());
                userRepository.save(user);
            });

            return ResponseEntity.ok(Map.of("message", "Payment verified"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Verification failed", "error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                    @RequestParam String videoId) {
        String email = jwtUtil.extractUsernameFromAuthHeader(authHeader);
        if (email == null) return ResponseEntity.status(401).body(Map.of("message", "Invalid or missing token"));

        boolean hasPaid = userRepository.findByEmail(email).map(user -> user.getPaidVideoIds().contains(videoId)).orElse(false);
        return ResponseEntity.ok(Map.of("hasPaid", hasPaid));
    }

    // helpers
    private String hmacSha256(String data, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes());
        return new String(org.apache.commons.codec.binary.Hex.encodeHex(hash));
    }

    // DTOs
    public static class CreateOrderRequest { private int amount; private String videoId; public int getAmount(){return amount;} public String getVideoId(){return videoId;} public void setAmount(int a){amount=a;} public void setVideoId(String v){videoId=v;} }
    public static class VerifyRequest {
        private String razorpay_order_id;
        private String razorpay_payment_id;
        private String razorpay_signature;
        private String videoId;
        public String getRazorpay_order_id(){return razorpay_order_id;} public String getRazorpay_payment_id(){return razorpay_payment_id;} public String getRazorpay_signature(){return razorpay_signature;} public String getVideoId(){return videoId;}
        public void setRazorpay_order_id(String r){this.razorpay_order_id=r;} public void setRazorpay_payment_id(String p){this.razorpay_payment_id=p;} public void setRazorpay_signature(String s){this.razorpay_signature=s;} public void setVideoId(String v){this.videoId=v;}
    }
}
