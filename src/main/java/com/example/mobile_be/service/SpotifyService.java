// // package com.example.mobile_be.service;

// // import org.springframework.stereotype.Service;
// // import org.springframework.web.client.RestTemplate;
// // import org.springframework.http.*;

// // import java.util.*;

// // @Service
// // public class SpotifyService {

// //     private final String accessToken = "BQD2I56cQhx_k0Gpj05QqLW9QX5nFPZj6wdKYYN9Y223PD_adl8pzMbOx_v2NHMpI54dLuYVIXEYIaWiNrce9MVqceR2V2FgwX3I4PZHV9SFw9TBaBP4KYluNcFOLddmzzg-RZgNQI0";
// //     private final RestTemplate restTemplate = new RestTemplate();

// //     public Map getTrackMetadata(String trackId) {
// //         String url = "https://api.spotify.com/v1/tracks/" + trackId;
// //         HttpHeaders headers = new HttpHeaders();
// //         headers.setBearerAuth(accessToken);
// //         HttpEntity<Void> entity = new HttpEntity<>(headers);

// //         ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
// //         return response.getBody();
// //     }

// //     public Map getAudioFeatures(String trackId) {
// //         String url = "https://api.spotify.com/v1/audio-features/" + trackId;
// //         HttpHeaders headers = new HttpHeaders();
// //         headers.setBearerAuth(accessToken);
// //         HttpEntity<Void> entity = new HttpEntity<>(headers);

// //         ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
// //         return response.getBody();
// //     }

// //     // Ví dụ: Lấy track IDs từ playlist
// //     public List<String> getPlaylistTrackIds(String playlistId) {
// //         String url = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?limit=100";
// //         HttpHeaders headers = new HttpHeaders();
// //         headers.setBearerAuth(accessToken);
// //         HttpEntity<Void> entity = new HttpEntity<>(headers);

// //         ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
// //         Map body = response.getBody();
// //         List<String> trackIds = new ArrayList<>();
// //         List<Map> items = (List<Map>) body.get("items");
// //         for (Map item : items) {
// //             Map track = (Map) item.get("track");
// //             trackIds.add((String) track.get("id"));
// //         }
// //         return trackIds;
// //     }
// // }

// package com.example.mobile_be.service;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.http.*;
// import org.springframework.stereotype.Service;
// import org.springframework.util.LinkedMultiValueMap;
// import org.springframework.util.MultiValueMap;
// import org.springframework.web.client.RestTemplate;

// import java.io.File;
// import java.util.*;

// @Service
// public class SpotifyService {

//     @Value("${spotify.client.id}")
//     private String clientId;

//     @Value("${spotify.client.secret}")
//     private String clientSecret;

//     @Value("${spotify.refresh.token}")
//     private String refreshToken;

//     private String accessToken;
//     private long tokenExpiresAt = 0;

//     /**
//      * Refresh access token
//      */
//     // public void refreshAccessToken() {
//     // if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt -
//     // 60000) {
//     // return;
//     // }

//     // System.out.println("🔄 Refreshing access token...");

//     // RestTemplate restTemplate = new RestTemplate();
//     // String url = "https://accounts.spotify.com/api/token";

//     // HttpHeaders headers = new HttpHeaders();
//     // headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

//     // String auth = clientId + ":" + clientSecret;
//     // String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
//     // headers.set("Authorization", "Basic " + encodedAuth);

//     // MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
//     // body.add("grant_type", "refresh_token");
//     // body.add("refresh_token", refreshToken);

//     // HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body,
//     // headers);

//     // ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
//     // entity, Map.class);
//     // Map responseBody = response.getBody();

//     // if (responseBody != null) {
//     // accessToken = (String) responseBody.get("access_token");
//     // Integer expiresIn = (Integer) responseBody.get("expires_in");
//     // tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);

//     // System.out.println("✅ Token refreshed! Expires in " + expiresIn + "s");
//     // }
//     // }

//    public void refreshAccessToken() {
//     if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60000) {
//         return;
//     }

//     System.out.println("🔄 Refreshing access token...");
    
//     RestTemplate restTemplate = new RestTemplate();
//     String url = "https://accounts.spotify.com/api/token";

//     HttpHeaders headers = new HttpHeaders();
//     headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    
//     String auth = clientId + ":" + clientSecret;
//     String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
//     headers.set("Authorization", "Basic " + encodedAuth);

//     MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
//     body.add("grant_type", "refresh_token"); // ✅ GIỮ NGUYÊN refresh_token
//     body.add("refresh_token", refreshToken);

//     HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

//     try {
//         ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
//         Map responseBody = response.getBody();
        
//         if (responseBody != null) {
//         accessToken = (String) responseBody.get("access_token");
        
//         // ✅ IN RA ĐỂ COPY
//         System.out.println("=".repeat(80));
//         System.out.println("ACCESS TOKEN FOR POSTMAN:");
//         System.out.println(accessToken);
//         System.out.println("=".repeat(80));
        
//         Integer expiresIn = (Integer) responseBody.get("expires_in");
//         tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
//     }

//     } catch (Exception e) {
//         System.err.println("❌ Error refreshing token: " + e.getMessage());
//         throw e;
//     }
// }

//     /**
//      * Fetch data và lưu vào file JSON
//      */
//     public Map<String, Object> fetchAndSaveData(String[] genres, int limitPerGenre) throws Exception {
//         refreshAccessToken();

//         List<Map<String, Object>> allTracks = new ArrayList<>();
//         Map<String, Integer> genreCounts = new HashMap<>();

//         for (String genre : genres) {
//             System.out.println("\n📂 Fetching genre: " + genre);
//             List<Map<String, Object>> tracks = fetchTracksWithAudioFeatures(genre.trim(), limitPerGenre);
//             allTracks.addAll(tracks);
//             genreCounts.put(genre, tracks.size());
//             System.out.println("✅ Got " + tracks.size() + " tracks");
//             Thread.sleep(500);
//         }

//         // Remove duplicates
//         allTracks = removeDuplicates(allTracks);

//         // Save to JSON
//         String folderPath = "data";
//         File folder = new File(folderPath);
//         if (!folder.exists())
//             folder.mkdirs();

//         ObjectMapper mapper = new ObjectMapper();
//         File outputFile = new File(folder, "songs.json");
//         mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, allTracks);

//         System.out.println("\n🎉 Saved " + allTracks.size() + " tracks to " + outputFile.getAbsolutePath());

//         return Map.of(
//                 "success", true,
//                 "total_tracks", allTracks.size(),
//                 "genres", genreCounts,
//                 "file_path", outputFile.getAbsolutePath());
//     }

//     /**
//      * Fetch tracks với audio features
//      */
//     private List<Map<String, Object>> fetchTracksWithAudioFeatures(String genre, int limit) {
//         refreshAccessToken();

//         RestTemplate restTemplate = new RestTemplate();
//         List<Map<String, Object>> resultList = new ArrayList<>();

//         String urlTemplate = "https://api.spotify.com/v1/search?q=genre:%s&type=track&limit=50&offset=%d";
//         int offset = 0;
//         boolean hasNext = true;

//         HttpHeaders headers = new HttpHeaders();
//         headers.setBearerAuth(accessToken);
//         HttpEntity<String> entity = new HttpEntity<>(headers);

//         while (hasNext && resultList.size() < limit) {
//             try {
//                 String url = String.format(urlTemplate, genre, offset);
//                 ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
//                 Map tracksMap = (Map) response.getBody().get("tracks");
//                 List<Map> items = (List<Map>) tracksMap.get("items");

//                 if (items.isEmpty())
//                     break;

//                 // Batch fetch audio features
//                 List<String> trackIds = new ArrayList<>();
//                 for (Map item : items) {
//                     trackIds.add((String) item.get("id"));
//                 }

//                 Map<String, Map<String, Object>> audioFeaturesMap = fetchAudioFeaturesBatch(trackIds, restTemplate);

//                 for (Map item : items) {
//                     String trackId = (String) item.get("id");
//                     Map album = (Map) item.get("album");
//                     List<Map> artistList = (List<Map>) item.get("artists");

//                     List<Map<String, String>> artists = new ArrayList<>();
//                     for (Map artist : artistList) {
//                         artists.add(Map.of(
//                                 "name", (String) artist.get("name"),
//                                 "id", (String) artist.get("id")));
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
//                     trackInfo.put("genre", genre);

//                     Map<String, Object> audioFeatures = audioFeaturesMap.get(trackId);
//                     if (audioFeatures != null && !audioFeatures.isEmpty()) {
//                         trackInfo.put("audio_features", audioFeatures);
//                     }

//                     resultList.add(trackInfo);
//                 }

//                 offset += items.size();
//                 hasNext = tracksMap.get("next") != null && resultList.size() < limit;

//                 Thread.sleep(100);

//             } catch (Exception e) {
//                 System.err.println("❌ Error: " + e.getMessage());
//                 break;
//             }
//         }

//         return resultList;
//     }

//     /**
//      * Batch fetch audio features
//      */
//     private Map<String, Map<String, Object>> fetchAudioFeaturesBatch(List<String> trackIds, RestTemplate restTemplate) {
//         Map<String, Map<String, Object>> result = new HashMap<>();

//         for (int i = 0; i < trackIds.size(); i += 100) {
//             List<String> batch = trackIds.subList(i, Math.min(i + 100, trackIds.size()));
//             String ids = String.join(",", batch);
//             String url = "https://api.spotify.com/v1/audio-features?ids=" + ids;

//             // ✅ THÊM LOG ĐỂ KIỂM TRA
//             System.out.println("🔑 Calling audio-features with token: "
//                     + (accessToken != null ? accessToken.substring(0, 20) + "..." : "NULL"));
//             System.out.println("📍 URL: " + url);

//             // ✅ TẠO LẠI HEADERS ĐỂ ĐẢM BẢO TOKEN MỚI NHẤT
//             HttpHeaders freshHeaders = new HttpHeaders();
//             freshHeaders.setBearerAuth(accessToken);
//             freshHeaders.set("Accept", "application/json");

//             HttpEntity<String> entity = new HttpEntity<>(freshHeaders);

//             try {
//                 ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
//                 Map body = response.getBody();

//                 System.out.println("✅ Audio features response: " + response.getStatusCode());

//                 if (body != null) {
//                     List<Map> audioFeaturesList = (List<Map>) body.get("audio_features");

//                     if (audioFeaturesList != null) {
//                         for (Map features : audioFeaturesList) {
//                             if (features == null)
//                                 continue;

//                             String id = (String) features.get("id");
//                             Map<String, Object> cleanFeatures = new LinkedHashMap<>();

//                             cleanFeatures.put("danceability", features.get("danceability"));
//                             cleanFeatures.put("energy", features.get("energy"));
//                             cleanFeatures.put("key", features.get("key"));
//                             cleanFeatures.put("loudness", features.get("loudness"));
//                             cleanFeatures.put("mode", features.get("mode"));
//                             cleanFeatures.put("speechiness", features.get("speechiness"));
//                             cleanFeatures.put("acousticness", features.get("acousticness"));
//                             cleanFeatures.put("instrumentalness", features.get("instrumentalness"));
//                             cleanFeatures.put("liveness", features.get("liveness"));
//                             cleanFeatures.put("valence", features.get("valence"));
//                             cleanFeatures.put("tempo", features.get("tempo"));
//                             cleanFeatures.put("duration_ms", features.get("duration_ms"));
//                             cleanFeatures.put("time_signature", features.get("time_signature"));

//                             result.put(id, cleanFeatures);
//                         }
//                     }
//                 }

//                 Thread.sleep(100);

//             } catch (Exception e) {
//                 System.err.println("❌ Error batch: " + e.getMessage());
//                 e.printStackTrace(); // ✅ THÊM STACK TRACE ĐỂ XEM CHI TIẾT
//             }
//         }

//         return result;
//     }

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