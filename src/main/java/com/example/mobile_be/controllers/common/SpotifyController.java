// package com.example.mobile_be.controllers.common;

// import com.example.mobile_be.service.SpotifyService;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.Map;

// @RestController
// @RequestMapping("/api/spotify")
// public class SpotifyController {

//     @Autowired
//     private SpotifyService spotifyService;

//     /**
//      * Endpoint để fetch data từ Spotify
//      * GET http://localhost:8080/api/spotify/fetch?genres=vietnamese-pop,k-pop&limit=50
//      */
//     @GetMapping("/fetch")
//     public ResponseEntity<Map<String, Object>> fetchSpotifyData(
//             @RequestParam(defaultValue = "vietnamese-pop,k-pop,pop") String genres,
//             @RequestParam(defaultValue = "50") int limit
//     ) {
//         try {
//             String[] genreArray = genres.split(",");
//             Map<String, Object> result = spotifyService.fetchAndSaveData(genreArray, limit);
//             return ResponseEntity.ok(result);
//         } catch (Exception e) {
//             return ResponseEntity.status(500).body(Map.of(
//                 "success", false,
//                 "error", e.getMessage()
//             ));
//         }
//     }

//     /**
//      * Test endpoint để check token
//      * GET http://localhost:8080/api/spotify/test
//      */
//     @GetMapping("/test")
//     public ResponseEntity<Map<String, Object>> testConnection() {
//         try {
//             spotifyService.refreshAccessToken();
//             return ResponseEntity.ok(Map.of(
//                 "success", true,
//                 "message", "Spotify connection OK"
//             ));
//         } catch (Exception e) {
//             return ResponseEntity.status(500).body(Map.of(
//                 "success", false,
//                 "error", e.getMessage()
//             ));
//         }
//     }
// }


// // ============================================
// // SpotifyService.java
// // ============================================
