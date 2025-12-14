package com.example.mobile_be.controllers.authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.mobile_be.dto.AuthResponse;
import com.example.mobile_be.dto.LoginRequest;
import com.example.mobile_be.dto.RegisterRequest;
import com.example.mobile_be.dto.VerifyRequest;
import com.example.mobile_be.models.Library;
import com.example.mobile_be.models.Playlist;
import com.example.mobile_be.models.User;
import com.example.mobile_be.repository.LibraryRepository;
import com.example.mobile_be.repository.PlaylistRepository;
import com.example.mobile_be.repository.UserRepository;
import com.example.mobile_be.security.JwtUtil;
import com.example.mobile_be.security.UserDetailsImpl;
import com.example.mobile_be.service.JwtBlacklistService;
import com.example.mobile_be.service.OtpType;
import com.example.mobile_be.service.UserService;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final PasswordEncoder passwordEncoder;
    @Autowired
    PlaylistRepository playlistRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private LibraryRepository libraryRepository;
    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    public AuthController(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    // [POST] /api/register - Gửi OTP và lưu thông tin user tạm thời
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Email already used.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (request.getRole() == null || request.getRole().isEmpty()) {
            request.setRole("ROLE_USER");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setIsVerified(false);

        userRepository.save(user);

        userService.sendRegisterOtp(request);

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP sent to email. Please verify to complete registration.");
        return ResponseEntity.ok(response);
    }

    // [POST] /api/verify-email - Xác thực OTP và tạo tài khoản
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody VerifyRequest request) {
        boolean success = userService.verifyEmail(request.getEmail(), request.getOtp());
        if (!success) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid or expired OTP."));
        }

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found."));
        }

        String token = jwtUtil.generateToken(new UserDetailsImpl(user));

        // Chuẩn bị danh sách playlist mặc định
        List<String> defaultPlaylistIds = new ArrayList<>();

        // Favorites
        if (!playlistRepository.existsByUserIdAndName(user.getId(), "Favorites")) {
            try {
                Playlist favorites = new Playlist();
                favorites.setName("Favorites");
                favorites.setDescription("A list of your favorite songs");
                favorites.setUserId(user.getId());
                favorites.setIsPublic(false);
                favorites.setThumbnailUrl("https://res.cloudinary.com/denhj5ubh/image/upload/v1765705079/playlist_uwjlfz.png");
                favorites.setPlaylistType("favorites");

                playlistRepository.save(favorites);
                defaultPlaylistIds.add(favorites.getId()); // Lưu ID vào danh sách
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Lỗi khi tạo playlist Favorites: " + e.getMessage());
            }
        }

        // // Your Songs
        // if (!playlistRepository.existsByUserIdAndName(user.getId(), "Your Songs")) {
        // try {
        // Playlist yourSongs = new Playlist();
        // yourSongs.setName("Your Songs");
        // yourSongs.setDescription("A list of your songs");
        // yourSongs.setUserId(user.getId());
        // yourSongs.setIsPublic(false);
        // yourSongs.setThumbnailUrl("/uploads/playlists/default-img.jpg");
        // yourSongs.setPlaylistType("your_songs");

        // playlistRepository.save(yourSongs);
        // defaultPlaylistIds.add(yourSongs.getId());
        // } catch (Exception e) {
        // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        // .body("Lỗi khi tạo playlist Your Songs: " + e.getMessage());
        // }
        // }

        Library library = libraryRepository.findByUserId(user.getId());
        if (library == null) {
            library = new Library();
            library.setUserId(user.getId());
            library.setPlaylistIds(new ArrayList<>());
        }

        for (String pid : defaultPlaylistIds) {
            if (!library.getPlaylistIds().contains(pid)) {
                library.getPlaylistIds().add(pid);
            }
        }

        libraryRepository.save(library);
        return ResponseEntity.ok(new AuthResponse(token, user.getRole()));
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@RequestBody VerifyRequest request) {
        boolean success = userService.verifyEmail(request.getEmail(), request.getOtp());
        if (!success) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid or expired OTP."));
        }

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");
        }

        String token = jwtUtil.generateToken(new UserDetailsImpl(user));

        return ResponseEntity.ok(new AuthResponse(token, user.getRole()));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");

        boolean isValid = userService.verifyForgotPasswordOtp(email, otp);
        if (!isValid) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "OTP is invalid or has expired"));
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP hợp lệ, bạn có thể đặt lại mật khẩu");
        return ResponseEntity.ok(response);
    }

    // [POST] /api/resend-otp - Gửi lại OTP
    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String typeStr = body.get("type"); // "REGISTER" hoặc "FORGOT_PASSWORD"
        if (typeStr == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing OTP type"));
        }

        OtpType type;
        try {
            type = OtpType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP type"));
        }

        boolean success = userService.resendOtp(email, type);

        if (success) {
            return ResponseEntity.ok(Map.of("message", "A new OTP has been sent to your email."));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found or invalid state for OTP resend."));
        }
    }

    // [POST] /api/login - Đăng nhập
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            if (request.getEmail() == null || request.getPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
            }

            User user = userService.authenticate(request.getEmail(), request.getPassword());
            String token = jwtUtil.generateToken(new UserDetailsImpl(user));
            System.out.println(user.getEmail());
            return ResponseEntity.ok(new AuthResponse(token, user.getRole()));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        } catch (BadCredentialsException e) {
            if ("Wrong password".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Wrong password"));
            }
            if ("Unverified".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unverified account"));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Incorrect login information"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "INTERNAL SERVER ERROR"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Invalid token format");
        }

        String token = authHeader.substring(7);

        // Thêm token vào blacklist
        jwtBlacklistService.blacklistToken(token);

        return ResponseEntity.ok("Logout successful");
    }
}
