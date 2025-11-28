// package com.example.mobile_be.runner;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.stereotype.Component;
// import org.springframework.web.client.RestTemplate;
// import org.springframework.http.*;
// import org.springframework.util.LinkedMultiValueMap;
// import org.springframework.util.MultiValueMap;

// import java.io.File;
// import java.util.*;

// @Component
// public class SpotifyJsonRunner implements CommandLineRunner {

//     @Value("${spotify.client.id}")
//     private String clientId;

//     @Value("${spotify.client.secret}")
//     private String clientSecret;

//     @Value("${spotify.refresh.token}")
//     private String refreshToken;

//     private String accessToken;
//     private long tokenExpiresAt = 0;

//     @Override
//     public void run(String... args) throws Exception {
//         System.out.println("🎵 Starting Spotify data fetch...");
        
//         String folderPath = "data";
//         File folder = new File(folderPath);
//         if (!folder.exists()) folder.mkdirs();

//         // Các genre Việt Nam và quốc tế phổ biến
//         String[] genres = {"vietnamese-pop", "k-pop", "pop", "hip-hop", "edm", "ballad"};
//         List<Map<String, Object>> allTracks = new ArrayList<>();

//         for (String genre : genres) {
//             System.out.println("\n📂 Fetching genre: " + genre);
//             List<Map<String, Object>> tracks = fetchTracksWithAudioFeatures(genre);
//             allTracks.addAll(tracks);
//             System.out.println("✅ Got " + tracks.size() + " tracks from " + genre);
            
//             // Tránh rate limit
//             Thread.sleep(1000);
//         }

//         // Loại bỏ duplicate tracks
//         allTracks = removeDuplicates(allTracks);

//         // Lưu file JSON
//         ObjectMapper mapper = new ObjectMapper();
//         File outputFile = new File(folder, "songs.json");
//         mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, allTracks);

//         System.out.println("\n🎉 SUCCESS! Saved " + allTracks.size() + " unique tracks to " + outputFile.getAbsolutePath());
//     }

//     /**
//      * Refresh access token từ refresh token
//      */
//     private void refreshAccessToken() {
//         if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60000) {
//             return; // Token còn hiệu lực
//         }

//         System.out.println("🔄 Refreshing access token...");
        
//         RestTemplate restTemplate = new RestTemplate();
//         String url = "https://accounts.spotify.com/api/token";

//         HttpHeaders headers = new HttpHeaders();
//         headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
//         // Basic Auth: Base64(client_id:client_secret)
//         String auth = clientId + ":" + clientSecret;
//         String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
//         headers.set("Authorization", "Basic " + encodedAuth);

//         MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
//         body.add("grant_type", "refresh_token");
//         body.add("refresh_token", refreshToken);

//         HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

//         try {
//             ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
//             Map responseBody = response.getBody();
            
//             if (responseBody != null) {
//                 accessToken = (String) responseBody.get("access_token");
//                 Integer expiresIn = (Integer) responseBody.get("expires_in");
//                 tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
                
//                 System.out.println("✅ Access token refreshed successfully! Expires in " + expiresIn + " seconds");
//             }
//         } catch (Exception e) {
//             System.err.println("❌ Error refreshing token: " + e.getMessage());
//             throw new RuntimeException("Failed to refresh access token", e);
//         }
//     }

//     /**
//      * Lấy danh sách tracks kèm audio features
//      */
//     private List<Map<String, Object>> fetchTracksWithAudioFeatures(String genre) {
//         refreshAccessToken();
        
//         RestTemplate restTemplate = new RestTemplate();
//         List<Map<String, Object>> resultList = new ArrayList<>();

//         String urlTemplate = "https://api.spotify.com/v1/search?q=genre:%s&type=track&limit=50&offset=%d&market=VN";
//         int offset = 0;
//         int maxTracks = 100; // Giới hạn để tránh quá nhiều request
//         boolean hasNext = true;

//         HttpHeaders headers = new HttpHeaders();
//         headers.setBearerAuth(accessToken);
//         HttpEntity<String> entity = new HttpEntity<>(headers);

//         while (hasNext && resultList.size() < maxTracks) {
//             try {
//                 String url = String.format(urlTemplate, genre, offset);
//                 ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
//                 Map tracksMap = (Map) response.getBody().get("tracks");
//                 List<Map> items = (List<Map>) tracksMap.get("items");

//                 if (items.isEmpty()) break;

//                 // Batch fetch audio features (tối đa 100 tracks/request)
//                 List<String> trackIds = new ArrayList<>();
//                 for (Map item : items) {
//                     trackIds.add((String) item.get("id"));
//                 }
//                 Map<String, Map<String, Object>> audioFeaturesMap = fetchAudioFeaturesBatch(trackIds, restTemplate, headers);

//                 for (Map item : items) {
//                     String trackId = (String) item.get("id");
//                     Map album = (Map) item.get("album");
//                     List<Map> artistList = (List<Map>) item.get("artists");
                    
//                     List<Map<String, String>> artists = new ArrayList<>();
//                     for (Map artist : artistList) {
//                         artists.add(Map.of(
//                                 "name", (String) artist.get("name"),
//                                 "id", (String) artist.get("id")
//                         ));
//                     }

//                     List<Map> images = (List<Map>) album.get("images");
//                     String imageUrl = images.size() > 0 ? (String) images.get(0).get("url") : "";

//                     Map<String, Object> trackInfo = new LinkedHashMap<>();
//                     trackInfo.put("track_id", trackId);
//                     trackInfo.put("track_name", item.get("name"));
//                     trackInfo.put("artists", artists);
//                     trackInfo.put("album_name", album.get("name"));
//                     trackInfo.put("album_id", album.get("id"));
//                     trackInfo.put("spotify_url", ((Map) item.get("external_urls")).get("spotify"));
//                     trackInfo.put("release_date", album.get("release_date"));
//                     trackInfo.put("image", imageUrl);
//                     trackInfo.put("popularity", item.get("popularity"));
//                     trackInfo.put("genre", genre); // Thêm genre để phân loại

//                     // Thêm audio features
//                     Map<String, Object> audioFeatures = audioFeaturesMap.get(trackId);
//                     if (audioFeatures != null && !audioFeatures.isEmpty()) {
//                         trackInfo.put("audio_features", audioFeatures);
//                     }

//                     resultList.add(trackInfo);
//                 }

//                 offset += items.size();
//                 hasNext = tracksMap.get("next") != null && resultList.size() < maxTracks;
                
//                 // Rate limiting
//                 Thread.sleep(100);
                
//             } catch (Exception e) {
//                 System.err.println("❌ Error fetching tracks: " + e.getMessage());
//                 break;
//             }
//         }

//         return resultList;
//     }

//     private Map<String, Map<String, Object>> fetchAudioFeaturesBatch(List<String> trackIds, RestTemplate restTemplate, HttpHeaders headers) {
//         Map<String, Map<String, Object>> result = new HashMap<>();
        
//         // Spotify cho phép tối đa 100 IDs/request
//         for (int i = 0; i < trackIds.size(); i += 100) {
//             List<String> batch = trackIds.subList(i, Math.min(i + 100, trackIds.size()));
//             String ids = String.join(",", batch);
//             String url = "https://api.spotify.com/v1/audio-features?ids=" + ids;
            
//             HttpEntity<String> entity = new HttpEntity<>(headers);
            
//             try {
//                 ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
//                 Map body = response.getBody();
                
//                 if (body != null) {
//                     List<Map> audioFeaturesList = (List<Map>) body.get("audio_features");
                    
//                     for (Map features : audioFeaturesList) {
//                         if (features == null) continue;
                        
//                         String id = (String) features.get("id");
//                         Map<String, Object> cleanFeatures = new LinkedHashMap<>();
                        
//                         // Lấy các features quan trọng cho recommendation
//                         cleanFeatures.put("danceability", features.get("danceability"));
//                         cleanFeatures.put("energy", features.get("energy"));
//                         cleanFeatures.put("key", features.get("key"));
//                         cleanFeatures.put("loudness", features.get("loudness"));
//                         cleanFeatures.put("mode", features.get("mode"));
//                         cleanFeatures.put("speechiness", features.get("speechiness"));
//                         cleanFeatures.put("acousticness", features.get("acousticness"));
//                         cleanFeatures.put("instrumentalness", features.get("instrumentalness"));
//                         cleanFeatures.put("liveness", features.get("liveness"));
//                         cleanFeatures.put("valence", features.get("valence"));
//                         cleanFeatures.put("tempo", features.get("tempo"));
//                         cleanFeatures.put("duration_ms", features.get("duration_ms"));
//                         cleanFeatures.put("time_signature", features.get("time_signature"));
                        
//                         result.put(id, cleanFeatures);
//                     }
//                 }
                
//                 Thread.sleep(100); // Rate limiting
                
//             } catch (Exception e) {
//                 System.err.println("❌ Error fetching audio features batch: " + e.getMessage());
//             }
//         }
        
//         return result;
//     }

//     /**
//      * Loại bỏ các track trùng lặp dựa trên track_id
//      */
//     private List<Map<String, Object>> removeDuplicates(List<Map<String, Object>> tracks) {
//         Map<String, Map<String, Object>> uniqueTracks = new LinkedHashMap<>();
        
//         for (Map<String, Object> track : tracks) {
//             String trackId = (String) track.get("track_id");
//             if (!uniqueTracks.containsKey(trackId)) {
//                 uniqueTracks.put(trackId, track);
//             }
//         }
        
//         return new ArrayList<>(uniqueTracks.values());
//     }
// }