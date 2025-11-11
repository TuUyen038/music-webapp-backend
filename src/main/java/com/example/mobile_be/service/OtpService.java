package com.example.mobile_be.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.mobile_be.models.Otp;

@Service
public class OtpService {

    private final Map<String, Otp> otpMap = new ConcurrentHashMap<>();
    private final Map<String, PendingUser> pendingUsers = new ConcurrentHashMap<>();

    public static class PendingUser {
        private final String email;
        private final String fullName; // Dùng cho REGISTER
        private String password; // Dùng cho REGISTER
        private String role; // Dùng cho REGISTER
        private final String otp;
        private final LocalDateTime expiryTime;
        private final OtpType type;

        public PendingUser(String email, String fullName, String password, String role, String otp,
                LocalDateTime expiryTime, OtpType type) {
            this.email = email;
            this.fullName = fullName;
            this.password = password;
            this.role = role;
            this.otp = otp;
            this.expiryTime = expiryTime;
            this.type = type;
        }

        // Getters
        public String getEmail() {
            return email;
        }

        public String getFullName() {
            return fullName;
        }

        public String getPassword() {
            return password;
        }

        public String getRole() {
            return role;
        }

        public String getOtp() {
            return otp;
        }

        public LocalDateTime getExpiryTime() {
            return expiryTime;
        }

        public OtpType getType() {
            return type;
        }
    }

    public String generateAndStorePendingUser(String email, String fullName, String password, String role,
            OtpType type) {
        String otp = String.valueOf(100000 + new Random().nextInt(900000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);

        PendingUser pending = new PendingUser(email, fullName, password, role, otp, expiry, type);
        pendingUsers.put(email, pending);
        otpMap.put(email, new Otp(otp, expiry));

        System.out.println("[OtpService] Tạo OTP cho " + type + ": " + otp + " | Email: " + email);
        return otp;
    }

    public String generateAndStorePendingUser(String email, String fullName, OtpType type) {
        return generateAndStorePendingUser(email, fullName, null, null, type);
    }

    public boolean isValidOtp(String email, String otp) {
        Otp savedOtp = otpMap.get(email);
        if (savedOtp == null)
            return false;

        // Hết hạn OTP
        if (LocalDateTime.now().isAfter(savedOtp.getExpiresAt())) {
            otpMap.remove(email);
            pendingUsers.remove(email);
            return false;
        }

        // So sánh
        boolean valid = savedOtp.getOtp().equals(otp);
        if (valid) {
            otpMap.remove(email); // Xóa OTP sau khi xác thực thành công
        }
        return valid;
    }

    public Map<String, PendingUser> getPendingUsers() {
        return pendingUsers;
    }
}
