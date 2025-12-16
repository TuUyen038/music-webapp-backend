package com.example.mobile_be.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.mobile_be.dto.RegisterRequest;
import com.example.mobile_be.dto.ResetPasswordRequest;
import com.example.mobile_be.models.User;
import com.example.mobile_be.repository.UserRepository;
import com.example.mobile_be.security.UserDetailsImpl;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OtpService otpService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new UserDetailsImpl(user);
    }

    private String getLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty())
            return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    // Gửi OTP đăng ký
    public void sendRegisterOtp(RegisterRequest request) {
        String otp = otpService.generateAndStorePendingUser(
                request.getEmail(),
                request.getFullName(),
                OtpType.REGISTER);

        String subject = "Mã OTP xác thực tài khoản";
        String content = "Xin chào " + request.getFullName()
                + ",\nMã OTP của bạn là: " + otp
                + "\nCó hiệu lực trong 5 phút.";

        emailService.sendEmail(request.getEmail(), subject, content);
    }

    // Xác minh OTP đăng ký
    public boolean verifyEmail(String email, String otp) {
        // Check OTP validity
        boolean isValid = otpService.isValidOtp(email, otp);
        if (!isValid)
            return false;

        // Get the pending OTP info
        OtpService.PendingUser pending = otpService.getPendingUsers().get(email);
        if (pending == null || pending.getType() != OtpType.REGISTER)
            return false;

        // Remove from pending list after verification
        otpService.getPendingUsers().remove(email);

        // Find the existing user created at registration
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty())
            return false;

        User user = optionalUser.get();
        user.setIsVerified(true); // Only update verification flag

        userRepository.save(user);
        return true;
    }

    public User authenticate(String email, String rawPassword) {

    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("Invalid login")); 
            // không throw UsernameNotFoundException nữa để tránh lộ "user not found"

    // 1) Check password trước
    if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
        throw new BadCredentialsException("Invalid login"); 
        // không dùng "Wrong password"
    }

    // 2) Password đúng rồi mới check verified
    if (!Boolean.TRUE.equals(user.getIsVerified())) {
        throw new DisabledException("Unverified account"); 
        // hoặc custom exception; DisabledException là hợp lý cho account chưa active/verified
    }

    return user;
}

    // Gửi OTP reset password
    public boolean requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.getIsVerified())
            return false;

        String otp = otpService.generateAndStorePendingUser(
                email,
                null,
                OtpType.FORGOT_PASSWORD);

        emailService.sendPasswordResetOTP(email, otp);
        return true;
    }

    public boolean resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null)
            return false;

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return true;
    }

    // Gửi lại OTP
    public boolean resendOtp(String email, OtpType type) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty())
            return false;

        User user = optionalUser.get();

        if (type == OtpType.REGISTER && user.getIsVerified())
            return false;
        if (type == OtpType.FORGOT_PASSWORD && !user.getIsVerified())
            return false;

        String fullName = (type == OtpType.REGISTER) ? user.getFullName() : null;
        String newOtp = otpService.generateAndStorePendingUser(email, fullName, type);

        String subject = (type == OtpType.REGISTER) ? "OTP xác thực tài khoản" : "OTP đặt lại mật khẩu";
        String content = "Xin chào " + (fullName != null ? fullName : "")
                + ",\nMã OTP của bạn là: " + newOtp
                + "\nCó hiệu lực trong 5 phút.";

        emailService.sendEmail(email, subject, content);
        return true;
    }

    public boolean verifyForgotPasswordOtp(String email, String otp) {
        boolean isValid = otpService.isValidOtp(email, otp);
        if (!isValid)
            return false;

        OtpService.PendingUser pending = otpService.getPendingUsers().get(email);
        if (pending == null || pending.getType() != OtpType.FORGOT_PASSWORD)
            return false;

        // Sau khi xác minh xong, xóa pending
        otpService.getPendingUsers().remove(email);

        return true; // cho FE biết OTP hợp lệ, tiếp tục reset password
    }

}
