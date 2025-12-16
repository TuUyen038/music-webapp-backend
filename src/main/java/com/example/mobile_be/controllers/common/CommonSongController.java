package com.example.mobile_be.controllers.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.mobile_be.dto.HistoryRequest;
import com.example.mobile_be.dto.PlaylistResponse;
import com.example.mobile_be.dto.SearchResponse;
import com.example.mobile_be.dto.SongRequest;
import com.example.mobile_be.dto.SongResponse;
import com.example.mobile_be.dto.UserResponse;
import com.example.mobile_be.models.Library;
import com.example.mobile_be.models.Genre;
import com.example.mobile_be.models.MultiResponse;
import com.example.mobile_be.models.Playlist;
import com.example.mobile_be.models.Song;
import com.example.mobile_be.models.User;
import com.example.mobile_be.repository.LibraryRepository;
import com.example.mobile_be.repository.PlaylistRepository;
import com.example.mobile_be.repository.SongRepository;
import com.example.mobile_be.repository.UserRepository;
import com.example.mobile_be.security.UserDetailsImpl;
import com.example.mobile_be.service.SongService;
import com.example.mobile_be.service.ListeningHistoryService;
import com.example.mobile_be.service.ImageStorageService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/common/song")
public class CommonSongController {
    private final SongService songService;
    private final ListeningHistoryService listeningHistoryService;

    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final ImageStorageService imageStorageService;

    @Autowired
    private final LibraryRepository libraryRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @PostMapping("/listening-history")
    public ResponseEntity<?> saveHistory(
            @RequestBody HistoryRequest req) {
        User user = getCurrentUser();

        ObjectId userId = new ObjectId(user.getId()); // từ JWT

        listeningHistoryService.saveHistory(userId, req);

        return ResponseEntity.ok("saved");
    }

    @GetMapping("/rcm/{userId}")
    public ResponseEntity<?> recommend(@PathVariable("userId") String userId) {
        ObjectId objectId;
        try {
            objectId = new ObjectId(userId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        List<Song> recommendedSongs = listeningHistoryService.recommendForUser(objectId);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Song s : recommendedSongs) {

            // Lấy tên nghệ sĩ
            Optional<User> userOpt = userRepository.findById(new ObjectId(s.getArtistId()));

            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("title", s.getTitle());
            map.put("artistId", s.getArtistId());
            map.put("artistName", userOpt.map(User::getFullName).orElse("Unknown"));
            map.put("coverImageUrl", s.getCoverImageUrl());
            map.put("duration", s.getDuration());
            map.put("audioUrl", s.getAudioUrl());

            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code) {
        // Dùng code lấy access token
        return "Authorization code received: " + code;
    }

    @GetMapping()
    public ResponseEntity<List<Song>> getAllPublicSongs() {
        try {
            List<Song> songs = songRepository.findAll(); // Lấy tất cả bài hát
            return ResponseEntity.ok(songs); // Trả trực tiếp danh sách Song
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // them nhac
    @PostMapping("/add")
    public ResponseEntity<?> addSong(
            @RequestPart("file") MultipartFile file,
            @RequestPart("title") String title,
            @RequestPart("description") String description,
            @RequestPart("coverImage") MultipartFile coverImage,
            @RequestPart(value = "genreId", required = false) List<String> genreId,
            @RequestPart(value = "lyrics", required = false) MultipartFile lyrics) {
        User user = getCurrentUser();

        try {
            Song song = new Song();
            song.setArtistId(user.getId());
            if (title != null && !title.trim().isEmpty()) {
                song.setTitle(title);
            }
            if (description != null && !description.trim().isEmpty()) {
                song.setDescription(description);
            }
            if (genreId != null) {
                song.setGenreId(genreId);
            }
            song.setIsPublic(false);
            if (coverImage != null && !coverImage.isEmpty()) {
                String url = imageStorageService.saveFile(coverImage, "images");
                song.setCoverImageUrl(url);
            }
            songService.saveSongFile(song, file);
            songService.saveSongFile(song, lyrics);

            return ResponseEntity.ok("Song added successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload file failed: " + e.getMessage());
        }
    }

    // edit song (title, description, coverImageUrl, list of genreId)
    @PutMapping("/edit/{id}")
    public ResponseEntity<?> editSong(@PathVariable("id") String id, @ModelAttribute SongRequest request) {
        getCurrentUser();

        ObjectId oId = new ObjectId(id);
        Optional<Song> song0 = songRepository.findById(oId);
        if (song0.isEmpty()) {
            return ResponseEntity.status(404).body("Song not found!");
        }
        Song song = song0.get();
        try {
            if (request.getCoverImage() != null) {
                try {
                    String coverImageUrl = imageStorageService.saveFile(request.getCoverImage(), "images");
                    song.setCoverImageUrl(coverImageUrl);
                } catch (IOException e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Upload cover image failed: " + e.getMessage());
                }
            }
            if (request.getTitle() != null) {
                song.setTitle(request.getTitle());
            }
            if (request.getDescription() != null) {
                song.setDescription(request.getDescription());
            }
            if (request.getGenreId() != null && !request.getGenreId().isEmpty()) {

                ArrayList<String> cleanedGenreIds = new ArrayList<>(new LinkedHashSet<>(request.getGenreId()));

                song.setGenreId(cleanedGenreIds);
            }
            songRepository.save(song);
            return ResponseEntity.ok(song);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Edit song failed: " + e.getMessage());
        }
    }

    @GetMapping("/recently-songs")
    public ResponseEntity<?> getNewestSongs() {
        List<Song> songs = songRepository.findTop6ByIsPublicTrueOrderByCreatedAtDesc();
        songs.forEach(song -> {
            System.out.println("Title: " + song.getTitle() +
                    " | Public: " + song.getIsPublic() +
                    " | CreatedAt: " + song.getCreatedAt());
        });
        return ResponseEntity.ok(songs);
    }

    @GetMapping("/popular")
    public ResponseEntity<?> getPopularSongs() {

        List<Song> songs = songRepository.findByOrderByViewsDesc();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Song s : songs) {

            Optional<User> userOpt = userRepository.findById(new ObjectId(s.getArtistId()));

            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("artistId", s.getArtistId());
            map.put("title", s.getTitle());
            map.put("coverImageUrl", s.getCoverImageUrl());
            map.put("audioUrl", s.getAudioUrl());
            map.put("duration", s.getDuration());
            map.put("views", s.getViews());
            map.put("artistName", userOpt.map(User::getFullName).orElse("Unknown"));

            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    // hàm lấy tất cả bài hát trong library của mình
    public Set<String> getAllSongIdsInUserLibrary(String userId) {
        // 1. Lấy danh sách playlistIds từ Library
        Library library = libraryRepository.findByUserId((userId));
        if (library == null || library.getPlaylistIds() == null || library.getPlaylistIds().isEmpty()) {
            return Collections.emptySet();
        }

        List<ObjectId> playlistObjectIds = library.getPlaylistIds().stream()
                .map(ObjectId::new)
                .toList();

        // 2. Aggregation pipeline: lấy ra tất cả songId trong các playlist
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("_id").in(playlistObjectIds)),
                Aggregation.project("songs"),
                Aggregation.unwind("songs"),
                Aggregation.group().addToSet("songs").as("allSongs"));

        AggregationResults<Document> result = mongoTemplate.aggregate(agg, "playlist", Document.class);
        Document doc = result.getUniqueMappedResult();

        if (doc != null && doc.containsKey("allSongs")) {
            List<String> songs = (List<String>) doc.get("allSongs");
            return new HashSet<>(songs);
        }

        return Collections.emptySet();
    }

    // get song by songId + trả về artistName + co lyric
    @GetMapping("/{id}")
    public ResponseEntity<?> getSongById(@PathVariable("id") ObjectId id) {
        Optional<Song> songOpt = songService.getSongById(id);
        if (songOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Song not found!");
        }

        Song song = songOpt.get();

        // Tìm nghệ sĩ
        Optional<User> artistOpt = userRepository.findById(new ObjectId(song.getArtistId()));
        String artistName = artistOpt.map(User::getFullName).orElse("Unknown Artist");

        // tra ve lyric
        String lyricPath = song.getLyricUrl();
        List<String> lyrics = new ArrayList<>();

        try {
            // Case 1: lyricUrl từ Cloudinary (bắt đầu bằng http)
            if (lyricPath != null && lyricPath.startsWith("http")) {
                URL url = new URL(lyricPath);
                try (InputStream is = url.openStream()) {
                    lyrics = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines().collect(Collectors.toList());
                }
            }
            // Case 2: lyricUrl local (/uploads/lyrics/file.lrc)
            else if (lyricPath != null && lyricPath.startsWith("/")) {
                Path path = Paths.get("." + lyricPath);
                if (Files.exists(path)) {
                    lyrics = Files.readAllLines(path, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            System.err.println("Không thể đọc lyrics: " + e.getMessage());
        }

        SongResponse response = new SongResponse();
        response.setId(song.getId());
        response.setTitle(song.getTitle());
        response.setDescription(song.getDescription());
        response.setAudioUrl(song.getAudioUrl());
        response.setCoverImageUrl(song.getCoverImageUrl());
        response.setArtistId(song.getArtistId());
        response.setArtistName(artistName);
        response.setDuration(song.getDuration());
        response.setViews(song.getViews());
        response.setLyrics(lyrics);

        return ResponseEntity.ok(response);
    }

    // api lay playlistId cua song
    @GetMapping("/{id}/playlist")
    public ResponseEntity<?> getSongPlaylistById(@PathVariable String id) {
        Optional<Song> songOpt = songService.getSongById(new ObjectId(id));
        if (songOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Song not found!");
        }

        Song song = songOpt.get();
        String songIdStr = song.getId().toString();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        String myId = userDetails.getId().toString();

        List<Playlist> userPlaylists = playlistRepository.findByUserId(myId);

        Map<String, List<String>> songToPlaylistMap = new HashMap<>();
        for (Playlist playlist : userPlaylists) {
            for (String songId : playlist.getSongs()) {
                songToPlaylistMap.computeIfAbsent(songId, k -> new ArrayList<>()).add(playlist.getId());
            }
        }
        return ResponseEntity.ok(songToPlaylistMap.getOrDefault(songIdStr, List.of()));
    }

    // get song by artistId
    @GetMapping("/artist/{artistId}")
    public ResponseEntity<?> getSongByArtistId(@PathVariable("artistId") String artistId) {
        List<Song> songs = songRepository.findByArtistIdAndIsPublicTrue(artistId);
        if (songs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No result");
        }
        return ResponseEntity.ok(songs);
    }

    @GetMapping("/stream/{id}")
    public void streamSong(
            @PathVariable("id") ObjectId id,
            HttpServletRequest req,
            HttpServletResponse res) throws IOException {

        Optional<Song> songOpt = songService.getSongById(id);
        if (songOpt.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.getWriter().write("Song not found!!");
            return;
        }

        songService.incrementViews(id); // tăng views

        String audioUrl = songOpt.get().getAudioUrl();
        URL url = new URL(audioUrl);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        String range = req.getHeader("Range");
        long contentLength = conn.getContentLengthLong();
        long start = 0;
        long end = contentLength - 1;

        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.replace("bytes=", "").split("-");
            start = Long.parseLong(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty()) {
                end = Long.parseLong(parts[1]);
            }

            if (end >= contentLength)
                end = contentLength - 1;

            long rangeLength = end - start + 1;

            res.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            res.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + contentLength);
            res.setHeader("Content-Length", String.valueOf(rangeLength));

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
        } else {
            res.setStatus(HttpServletResponse.SC_OK);
            res.setHeader("Content-Length", String.valueOf(contentLength));
        }

        res.setHeader("Accept-Ranges", "bytes");
        res.setContentType("audio/mpeg");

        try (InputStream inputStream = conn.getInputStream();
                OutputStream outputStream = res.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private String normalizeText(String input) {
    String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
    return normalized
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase()
            .trim();
}

    @GetMapping("/search")
    public ResponseEntity<?> searchSongsByKeyword(
        @RequestParam("keyword") String keyword) {

            if (keyword == null) {
                return ResponseEntity.ok(List.of());
            }

            keyword = keyword.trim(); 

            if (keyword.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
        // tim song theo title(k check song public hay k vi de thay duoc song nay ben fe
        // da xu ly ispublic roi?)
        String normalizedKeyword = normalizeText(keyword);
        List<Song> songsByTitle = songRepository.findAll()
        .stream()
        .filter(song ->
            normalizeText(song.getTitle())
                .contains(normalizedKeyword)
        )
        .collect(Collectors.toList());
        // tim artist theo fullname va role
        List<User> artists = userRepository.findByRoleAndFullNameContainingIgnoreCase("ROLE_ARTIST", keyword);

        List<String> artistIds = artists.stream().map(User::getId).collect(Collectors.toList());
        List<Song> songsByArtist = songRepository.findByArtistIdInAndIsPublicTrue(artistIds);

        Map<String, Song> songMap = new HashMap<>();
        songsByTitle.forEach(song -> songMap.put(song.getId(), song));
        songsByArtist.forEach(song -> songMap.putIfAbsent(song.getId(), song));
        List<Song> matchedSongs = new ArrayList<>(songMap.values());

        User u = getCurrentUser();
        String myId = u.getId();

        List<Playlist> userPlaylists = playlistRepository.findByUserId(myId);

        // Map songId -> List<playlistId>
        Map<String, List<String>> songToPlaylistMap = new HashMap<>();
        for (Playlist playlist : userPlaylists) {
            for (String songId : playlist.getSongs()) {
                songToPlaylistMap.computeIfAbsent(songId, k -> new ArrayList<>()).add(playlist.getId());
            }
        }

       List<SongResponse> responses = matchedSongs.stream().map(song -> {

        Optional<User> artistOpt =
            userRepository.findById(new ObjectId(song.getArtistId()));

        SongResponse res = new SongResponse();
        res.setId(song.getId());
        res.setTitle(song.getTitle());
        res.setArtistId(song.getArtistId());
        res.setArtistName(
            artistOpt.map(User::getFullName).orElse("Unknown Artist")
        );

        res.setAudioUrl(song.getAudioUrl());
        res.setDuration(song.getDuration());
        res.setViews(song.getViews());
        res.setDescription(song.getDescription());
        res.setCoverImageUrl(song.getCoverImageUrl());
        res.setPlaylistIds(songToPlaylistMap.getOrDefault(song.getId(), List.of()));
        return res;
    }).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }


    @GetMapping("/search/multi")
    public ResponseEntity<?> searchAll(@RequestParam("keyword") String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return ResponseEntity.ok(new SearchResponse());
        }

        // 1. Lấy artist by fullname va role
        List<UserResponse> artists = userRepository
                .findByRoleAndFullNameContainingIgnoreCase("ROLE_ARTIST", keyword)
                .stream()
                .map(art -> {
                    UserResponse res = new UserResponse();
                    res.setId(art.getId());
                    res.setEmail(art.getEmail());
                    res.setFullName(art.getFullName());
                    res.setRole(art.getRole());
                    res.setAvatarUrl(art.getAvatarUrl());
                    // res.setIsVerified(art.getIsVerified());
                    return res;
                })
                .collect(Collectors.toList());

        User u = getCurrentUser();
        String myId = u.getId();

        // Load tất cả playlistId trong Library
        Library lib = libraryRepository.findByUserId(myId);
        Set<String> playlistIdsInLibrary = new HashSet<>(lib.getPlaylistIds());

        // playlist khong phai cua minh
        Criteria playlistCriteria = new Criteria().andOperator(
                Criteria.where("isPublic").is(true),
                Criteria.where("userId").ne(myId));

        Query playlistQuery = new Query(playlistCriteria);

        if (keyword.length() >= 3) {
            TextCriteria playlistText = TextCriteria.forDefaultLanguage().matching(keyword);
            playlistQuery.addCriteria(playlistText);
        } else {
            playlistQuery.addCriteria(Criteria.where("name").regex(".*" + Pattern.quote(keyword) + ".*", "i"));
        }
        List<Playlist> listplaylist = mongoTemplate.find(playlistQuery, Playlist.class);
        // playlist kem theo isInLibrary
        List<PlaylistResponse> playlists = listplaylist.stream().map(pll -> {
            PlaylistResponse res = new PlaylistResponse();
            res.setId(pll.getId());
            res.setName(pll.getName());
            res.setDescription(pll.getDescription());
            res.setThumbnailUrl(pll.getThumbnailUrl());
            res.setIsInLibrary(playlistIdsInLibrary.contains(pll.getId()));
            return res;
        }).collect(Collectors.toList());

        // song public hoac cua minh
        Criteria songCriteria = new Criteria();
        if (myId != null) {
            songCriteria = new Criteria().orOperator(
                    Criteria.where("isPublic").is(true),
                    Criteria.where("artistId").is(myId));
        } else {
            songCriteria = Criteria.where("isPublic").is(true);
        }

        Query songQuery = new Query().addCriteria(songCriteria);

        if (keyword.length() >= 3) {
            TextCriteria songText = TextCriteria.forDefaultLanguage().matching(keyword);
            songQuery.addCriteria(songText);
        } else {
            songQuery.addCriteria(Criteria.where("title").regex(".*" + Pattern.quote(keyword) + ".*", "i"));
        }
        List<Song> listSong = mongoTemplate.find(songQuery, Song.class);

        // songs in library
        Set<String> songIdsInLibrary = getAllSongIdsInUserLibrary(myId);

        // tra ve ket qua songs co isInlibrary
        List<SongResponse> songs = listSong.stream().map(song -> {
            SongResponse res = new SongResponse();
            res.setId(song.getId());
            res.setTitle(song.getTitle());
            res.setArtistId(song.getArtistId());
            res.setAudioUrl(song.getAudioUrl());
            res.setDuration(song.getDuration());
            res.setViews(song.getViews());
            res.setDescription(song.getDescription());
            res.setCoverImageUrl(song.getCoverImageUrl());
            res.setIsInLibrary(songIdsInLibrary.contains(song.getId()));
            return res;
        }).collect(Collectors.toList());

        // 4. Trộn kết quả
        List<MultiResponse> mixed = new ArrayList<>();
        int maxSize = Math.max(songs.size(), Math.max(playlists.size(), artists.size()));
        for (int i = 0; i < maxSize; i++) {
            if (i < songs.size()) {
                mixed.add(new MultiResponse("song", songs.get(i)));
            }
            if (i < artists.size()) {
                mixed.add(new MultiResponse("artist", artists.get(i)));
            }
            if (i < playlists.size()) {
                mixed.add(new MultiResponse("playlist", playlists.get(i)));
            }
        }
        return ResponseEntity.ok(mixed);
    }

    // filter
    @GetMapping("/filter")
    public ResponseEntity<?> filterSongsByCreatedAt(@RequestParam String filter) {
        List<Song> results;
        switch (filter) {
            case "newest":
                results = songRepository.findAllByOrderByCreatedAtDesc();
                break;
            case "trending":
                results = songRepository.findByOrderByViewsDesc();
                break;
            case "all":
                results = songRepository.findAll();
                break;
            default:
                return ResponseEntity.badRequest().body("Invalid filter: use 'newest', 'trending' or 'all'");
        }
        return ResponseEntity.ok(results);
    }

    // get songs by genre
    @GetMapping("/genre/{genreId}")
    public ResponseEntity<?> getSongsByGenre(@PathVariable("genreId") String genreId) {
        List<Song> songs = songRepository.findByGenreId(genreId);
        if (songs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No songs found for this genre.");
        }
        return ResponseEntity.ok(songs);
    }

}
