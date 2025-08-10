package com.cosmetology.controller;

import com.cosmetology.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/video")
public class VideoController {

    private final UserRepository userRepo;

    public VideoController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/access")
    public ResponseEntity<?> accessVideo(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body("Authentication required.");
        }

        String email = auth.getName();
        var user = userRepo.findByEmail(email);

        if (user.isPresent() && !user.get().getPaidVideoIds().isEmpty()) {
            return ResponseEntity.ok("You can now view the video!");
        } else {
            return ResponseEntity.status(403).body("Please complete payment to view content.");
        }
    }

    // For testing payment without Razorpay (only in dev)
    @PostMapping("/simulate-payment")
    public ResponseEntity<String> simulatePayment(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body("Authentication missing!");
        }

        String email = auth.getName();
        var user = userRepo.findByEmail(email);
        if (user.isPresent()) {
            var u = user.get();
            u.getPaidVideoIds().add("VIDEO123"); // Example: Mark one video as paid
            userRepo.save(u);
            return ResponseEntity.ok("Payment simulated successfully!");
        } else {
            return ResponseEntity.badRequest().body("User not found");
        }
    }
}
