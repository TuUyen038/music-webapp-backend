package com.example.mobile_be.controllers.common;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.mobile_be.dto.AddSongsRequest;
import com.example.mobile_be.dto.PlaylistRequest;
import com.example.mobile_be.dto.SongResponse;
import com.example.mobile_be.models.Library;
import com.example.mobile_be.models.Playlist;
import com.example.mobile_be.models.Song;
import com.example.mobile_be.models.User;
import com.example.mobile_be.repository.LibraryRepository;
import com.example.mobile_be.repository.PlaylistRepository;
import com.example.mobile_be.repository.UserRepository;
import com.example.mobile_be.repository.SongRepository;
import com.example.mobile_be.security.UserDetailsImpl;
import com.example.mobile_be.service.ImageStorageService;

@RestController
@RequestMapping("/api/common/playlist")

public class CommonPlaylistController {
  @Autowired
  private PlaylistRepository playlistRepository;
  @Autowired
  UserRepository userRepository;
  @Autowired
  LibraryRepository libraryRepository;
  @Autowired
  private ImageStorageService imageStorageService;
  @Autowired
  private SongRepository songRepository;
  @Autowired
  private MongoTemplate mongoTemplate;

  private User getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserDetailsImpl)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    UserDetailsImpl u = (UserDetailsImpl) auth.getPrincipal();

    return userRepository.findById(u.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  @GetMapping("/favorite/songs/{songId}/exists")
  public ResponseEntity<?> checkSongInFavorite(@PathVariable("songId") String songId) {
    User currentUser = getCurrentUser();
    String userId = currentUser.getId();

    boolean isFavorite = playlistRepository.existsByUserIdAndNameAndSongsContaining(userId, "Favorites", songId);

    return ResponseEntity.ok(Map.of(
        "songId", songId,
        "inFavorite", isFavorite));
  }

  // getNewReleasePlaylists
  @GetMapping("/new-releases")
  public ResponseEntity<?> getNewReleasePlaylists() {
    List<Playlist> playlists = playlistRepository.findTop6ByIsPublicTrueOrderByCreatedAtDesc();
    return ResponseEntity.ok(playlists);
  }

  @GetMapping("/public-playlists")
  public ResponseEntity<?> getAllPublicPlaylists() {
    List<Playlist> playlists = playlistRepository.findByIsPublicTrue();
    return ResponseEntity.ok(playlists);
  }

  // getFeaturedPlaylists
  @GetMapping("/featured")
  public List<Playlist> getFeaturedPlaylists() {
    List<Playlist> all = playlistRepository.findByIsPublicTrue();
    Collections.shuffle(all);
    return all.stream().limit(6).collect(Collectors.toList());
  }

  // [GET] http://localhost:8081/api/common/playlist
  // lấy tất cả playlist cua user
  @GetMapping
  public ResponseEntity<List<Playlist>> getAllPlaylists() {
    User user = getCurrentUser();

    Library library = libraryRepository.findByUserId(user.getId());
    if (library == null || library.getPlaylistIds().isEmpty()) {
      return ResponseEntity.ok(List.of());
    }

    List<ObjectId> playlistIds = library.getPlaylistIds().stream()
        .map(ObjectId::new)
        .collect(Collectors.toList());

    List<Playlist> playlists = playlistRepository.findAllById(playlistIds);

    return ResponseEntity.ok(playlists);
  }

  // [GET] http://localhost:8081/api/common/playlist/{playlistId}
  // lấy playlist theo ID
  @GetMapping("/{id}")
  public ResponseEntity<?> getPlaylistById(@PathVariable("id") String id) {
    ObjectId objId;
    try {
      objId = new ObjectId(id);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body("ID không hợp lệ");
    }

    Optional<Playlist> playlistOpt = playlistRepository.findById(objId);
    if (playlistOpt.isEmpty()) {

      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy playlist");
    }

    Playlist playlist = playlistOpt.get();
    if (!playlist.getUserId().equals(getCurrentUser().getId())) {

      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Người dùng không có quyền truy cập");
    }

    return ResponseEntity.ok(playlist);
  }

  // hàm lấy tất cả bài hát trong library của mình
  public Set<String> getAllSongIdsInUserLibrary(String userId) {
    Query query = new Query(Criteria.where("userId").is(userId));
    query.fields().include("songs");

    List<Playlist> playlists = mongoTemplate.find(query, Playlist.class);

    Set<String> songIdSet = new HashSet<>();
    for (Playlist p : playlists) {
      if (p.getSongs() != null) {
        songIdSet.addAll(p.getSongs());
      }
    }
    return songIdSet;
  }

  // lấy songs của playlist theo playlistId
  @GetMapping("/{playlistId}/songs")
  public ResponseEntity<?> getSongsInPlaylist(@PathVariable String playlistId) {
    ObjectId playlistObjId;
    try {
      playlistObjId = new ObjectId(playlistId);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body("Invalid playlist ID");
    }

    Playlist playlist = playlistRepository.findById(playlistObjId)
        .orElseThrow(() -> new RuntimeException("Playlist not found"));

    // Kiểm tra quyền truy cập
    User currentUser = getCurrentUser();
    if (!playlist.getUserId().equals(currentUser.getId()) && !Boolean.TRUE.equals(playlist.getIsPublic())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
    }

    // Lấy danh sách songId từ playlist
    List<ObjectId> songObjectIds = playlist.getSongs().stream()
        .map(ObjectId::new)
        .toList();

    // Truy vấn các bài hát công khai có trong danh sách
    List<Song> songsInDb = songRepository.findByIdInAndIsPublicTrue(songObjectIds);

    // Map<songId, Song> để giữ thứ tự ban đầu
    Map<String, Song> songMap = songsInDb.stream()
        .collect(Collectors.toMap(Song::getId, Function.identity()));

    // Lấy tất cả artistId từ songs
    Set<ObjectId> artistObjectIds = songsInDb.stream()
        .map(Song::getArtistId)
        .map(ObjectId::new)
        .collect(Collectors.toSet());

    // Truy vấn các nghệ sĩ
    List<User> artists = userRepository.findAllById(artistObjectIds);
    Map<String, String> artistNameMap = artists.stream()
        .collect(Collectors.toMap(User::getId, User::getFullName));

    // Duyệt theo thứ tự ban đầu trong playlist
    List<SongResponse> orderedSongs = new ArrayList<>();
    for (String songId : playlist.getSongs()) {
      Song song = songMap.get(songId);
      if (song == null)
        continue;

      SongResponse res = new SongResponse();
      res.setId(song.getId());
      res.setTitle(song.getTitle());
      res.setArtistId(song.getArtistId());
      res.setAudioUrl(song.getAudioUrl());
      res.setDuration(song.getDuration());
      res.setViews(song.getViews());
      res.setDescription(song.getDescription());
      res.setCoverImageUrl(song.getCoverImageUrl());

      String artistName = artistNameMap.getOrDefault(song.getArtistId(), "Unknown Artist");
      res.setArtistName(artistName);

      orderedSongs.add(res);
    }

    return ResponseEntity.ok(orderedSongs);
  }

  // lấy playlist Favourite và các bài hát trong đó
  @GetMapping("/favourite/songs")
  public ResponseEntity<?> getFavouriteSongs() {
    User currentUser = getCurrentUser();

    Library library = libraryRepository.findByUserId(currentUser.getId());
    if (library == null || library.getPlaylistIds() == null || library.getPlaylistIds().isEmpty()) {
      return ResponseEntity.ok(Collections.emptyList());
    }

    List<ObjectId> playlistObjIds = library.getPlaylistIds().stream()
        .map(ObjectId::new)
        .toList();

    // Lấy toàn bộ playlist
    List<Playlist> playlists = playlistRepository.findAllById(playlistObjIds);

    // Tìm playlist Favourite theo tên
    Playlist favourite = playlists.stream()
        .filter(p -> "favourites".equalsIgnoreCase(p.getPlaylistType()))
        .findFirst()
        .orElse(null);

    if (favourite == null || favourite.getSongs() == null || favourite.getSongs().isEmpty()) {
      return ResponseEntity.ok(Collections.emptyList());
    }

    // Lấy danh sách bài hát giữ đúng thứ tự
    List<ObjectId> songIds = favourite.getSongs().stream()
        .map(ObjectId::new)
        .toList();

    List<Song> songs = songRepository.findByIdInAndIsPublicTrue(songIds);

    return ResponseEntity.ok(songs);
  }

  // Lấy playlist dựa vào artistId (playlist do co isPublic = true)
  @GetMapping("/artist/{artistId}")
  public ResponseEntity<?> getPlaylistByArtistId(@PathVariable String artistId) {
    System.out.println("Artist ID: " + artistId);
    List<Playlist> pll = playlistRepository.findByUserIdAndIsPublicTrue(artistId);

    if (pll.isEmpty()) {

      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy playlist");
    }

    return ResponseEntity.ok(pll);

  }

  // [GET] http://localhost:8081/api/common/playlist/search?keyword=...
  // tìm kiếm playlist theo tên
  @GetMapping("/search")
  public ResponseEntity<List<Playlist>> searchPlaylistByName(@RequestParam String name) {
    List<Playlist> playlists = playlistRepository.findByNameContainingIgnoreCaseAndIsPublicTrue(name);
    return ResponseEntity.ok(playlists);
  }

  // [POST] http://localhost:8081/api/common/playlist/create
  // tạo playlist
  @PostMapping("/create")
  public ResponseEntity<?> postPlaylist(@RequestBody PlaylistRequest request) {
    try {
      User user = getCurrentUser();
      Playlist playlist = new Playlist();
      playlist.setName(request.getName());
      playlist.setDescription(request.getDescription());
      playlist.setUserId(user.getId());
      playlist.setIsPublic(false);

      String thumbnail = request.getThumbnail();

      if (thumbnail != null && !thumbnail.isEmpty()) {
        playlist.setThumbnailUrl(thumbnail);
      } else {
        playlist.setThumbnailUrl("https://res.cloudinary.com/denhj5ubh/image/upload/v1765705079/playlist_uwjlfz.png");
      }

      playlistRepository.save(playlist);

      String playlistId = playlist.getId();

      Library library = libraryRepository.findByUserId((playlist.getUserId()));
      if (library == null) {
        library = new Library();
        library.setUserId(playlist.getUserId());
        library.setPlaylistIds(new ArrayList<>());
      }

      library.getPlaylistIds().add(playlistId);

      libraryRepository.save(library);
      return ResponseEntity.status(201).body("Playlist created successfully.");
    } catch (Exception e) {
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(java.util.Map.of("message", "Error in playlist: " + e.getMessage()));
    }
  }

  // [PATCH] http://localhost:8081/api/common/playlist/change/{playlistId}
  // Chỉ bao gồm thay đổi name, thumbnail, description, danh sach bai hat
  @PatchMapping("/change/{id}")
  public ResponseEntity<?> updatePlaylist(
      @PathVariable("id") String id,
      @RequestBody PlaylistRequest request) {
    try {
      User user = getCurrentUser();

      // validate id
      if (!ObjectId.isValid(id)) {
        return ResponseEntity.badRequest()
            .body(java.util.Map.of("message", "Invalid playlist id"));
      }

      ObjectId objectId = new ObjectId(id);

      Playlist playlist = playlistRepository.findById(objectId)
          .orElseThrow(() -> new RuntimeException("Playlist not found"));

      // ownership check
      if (!playlist.getUserId().equals(user.getId())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(java.util.Map.of("message", "Access denied"));
      }

      String name = request.getName();
      String description = request.getDescription();
      String thumbnail = request.getThumbnail();
      Boolean isPublic = request.getIsPublic();
      if (name != null && !name.trim().isEmpty()) {
        playlist.setName(name.trim());
      }
      if (description != null) {
        playlist.setDescription(description.trim());
      }
      if (thumbnail != null && !thumbnail.trim().isEmpty()) {
        playlist.setThumbnailUrl(thumbnail.trim());
      }
      if (isPublic != null) {
        playlist.setIsPublic(isPublic);
      }
      List<String> songs = request.getSongs();
      if (songs != null) {
        List<String> cleaned = songs.stream()
            .filter(s -> s != null && !s.trim().isEmpty())
            .map(String::trim)
            .toList();

        // validate ObjectId format
        for (String sid : cleaned) {
          if (!ObjectId.isValid(sid)) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("message", "Invalid song id in songs: " + sid));
          }
        }

        // optional: reject duplicates
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(cleaned));
        if (unique.size() != cleaned.size()) {
          return ResponseEntity.badRequest()
              .body(java.util.Map.of("message", "Songs contains duplicates"));
        }

        playlist.setSongs(new ArrayList<>(unique)); // giữ đúng thứ tự
      }

      playlistRepository.save(playlist);

      return ResponseEntity.ok(playlist);

    } catch (RuntimeException e) {
      if ("Playlist not found".equals(e.getMessage())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(java.util.Map.of("message", e.getMessage()));
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(java.util.Map.of("message", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(java.util.Map.of("message", "Error updating playlist: " + e.getMessage()));
    }
  }

  // [PATCH] http://localhost:8081/api/common/playlist/addSong
  // them 1 hoac nhieu bat hat vao playlist
  @PostMapping("/addSongs")
  public ResponseEntity<?> addMultipleSongsToLibrary(@RequestBody AddSongsRequest request) {
    List<String> songIds = request.getSongs();
    String playlistId = request.getPlaylistId();

    if (songIds == null || songIds.isEmpty()) {
      return ResponseEntity.badRequest().body("Missing songIds");
    }

    User currentUser = getCurrentUser();
    String userId = currentUser.getId();

    Playlist targetPlaylist;

    if (playlistId != null && !playlistId.trim().isEmpty()) {
      targetPlaylist = playlistRepository.findById(new ObjectId(playlistId))
          .orElseThrow(() -> new RuntimeException("Playlist not found"));
      if (!targetPlaylist.getUserId().equals(userId)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
      }
    } else {
      System.out.println(userId);
      Optional<Playlist> optional = playlistRepository.findByUserIdAndPlaylistType(userId, "favorites");
      if (optional.isPresent()) {
        targetPlaylist = optional.get();
      } else {
        targetPlaylist = new Playlist();
        targetPlaylist.setName("Favorites");
        targetPlaylist.setUserId(userId);
        targetPlaylist.setIsPublic(false);
        targetPlaylist.setPlaylistType("favorites");
        targetPlaylist
            .setThumbnailUrl("https://res.cloudinary.com/denhj5ubh/image/upload/v1765705079/playlist_uwjlfz.png");
        targetPlaylist.setDescription("A list of your favorite songs");
        targetPlaylist.setSongs(new ArrayList<>());

        playlistRepository.save(targetPlaylist);
      }
    }

    for (String songId : songIds) {
      if (!targetPlaylist.getSongs().contains(songId)) {
        targetPlaylist.getSongs().add(songId);
      }
    }

    playlistRepository.save(targetPlaylist);
    return ResponseEntity.ok("Songs added successfully");
  }

  @PostMapping("/favorites/songs/{songId}")
public ResponseEntity<?> addSongToFavorites(@PathVariable("songId") String songId) {
  try {
    if (!ObjectId.isValid(songId)) {
      return ResponseEntity.badRequest().body(java.util.Map.of("message", "Invalid songId"));
    }

    // BR: If (!existsSong(songId)) return "No songs found"
    boolean exists = songRepository.existsById(new ObjectId(songId));
    if (!exists) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(java.util.Map.of("message", "No songs found"));
    }

    String userId = getCurrentUser().getId();

    // Get or create Favorites playlist
    Playlist fav = playlistRepository.findByUserIdAndPlaylistType(userId, "favorites")
        .orElseGet(() -> {
          Playlist p = new Playlist();
          p.setName("Favorites");
          p.setUserId(userId);
          p.setIsPublic(false);
          p.setPlaylistType("favorites");
          p.setThumbnailUrl("https://res.cloudinary.com/denhj5ubh/image/upload/v1765705079/playlist_uwjlfz.png");
          p.setDescription("A list of your favorite songs");
          p.setSongs(new ArrayList<>());
          return playlistRepository.save(p);
        });

    if (fav.getSongs() == null) fav.setSongs(new ArrayList<>());

    // BR: Else if (isFavorite(userId, songId)) return "Song already in Favorites"
    if (fav.getSongs().contains(songId)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(java.util.Map.of("message", "Song already in Favorites"));
    }

    fav.getSongs().add(songId);
    playlistRepository.save(fav);

    return ResponseEntity.ok(java.util.Map.of(
        "message", "Added to Favorites",
        "playlistId", fav.getId()
    ));

  } catch (Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(java.util.Map.of("message", "Error adding to favorites: " + e.getMessage()));
  }
}

@DeleteMapping("/favorites/songs/{songId}")
public ResponseEntity<?> removeSongFromFavorites(@PathVariable("songId") String songId) {
  try {
    if (!ObjectId.isValid(songId)) {
      return ResponseEntity.badRequest().body(java.util.Map.of("message", "Invalid songId"));
    }

    // BR: If (!existsSong(songId)) return "No songs found"
    boolean exists = songRepository.existsById(new ObjectId(songId));
    if (!exists) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(java.util.Map.of("message", "No songs found"));
    }

    String userId = getCurrentUser().getId();

    Playlist fav = playlistRepository.findByUserIdAndPlaylistType(userId, "favorites")
        .orElseThrow(() -> new RuntimeException("Favorite playlist not found"));

    if (fav.getSongs() == null) fav.setSongs(new ArrayList<>());

    // BR: Else if (!isFavorite(userId, songId)) return "Song is not in Favorites"
    if (!fav.getSongs().contains(songId)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(java.util.Map.of("message", "Song is not in Favorites"));
    }

    fav.getSongs().removeIf(id -> songId.equals(id));
    playlistRepository.save(fav);

    return ResponseEntity.ok(java.util.Map.of(
        "message", "Removed from Favorites",
        "playlistId", fav.getId()
    ));

  } catch (RuntimeException e) {
    if ("Favorite playlist not found".equals(e.getMessage())) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(java.util.Map.of("message", e.getMessage()));
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(java.util.Map.of("message", "Error removing from favorites: " + e.getMessage()));
  } catch (Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(java.util.Map.of("message", "Error removing from favorites: " + e.getMessage()));
  }
}

  // [PATCH] http://localhost:8081/api/common/playlist/{playlistId}/removeSong
  // xoa 1 bai hat khoi playlist
  @PatchMapping("/{playlistId}/removeSongs")
  public ResponseEntity<?> removeSongsFromPlaylist(
      @PathVariable("playlistId") String playlistId,
      @RequestBody Map<String, List<String>> body) {
    try {
      // 1) validate playlistId
      if (!ObjectId.isValid(playlistId)) {
        return ResponseEntity.badRequest()
            .body(java.util.Map.of("message", "Invalid playlistId"));
      }

      ObjectId playlistObjId = new ObjectId(playlistId);

      Playlist playlist = playlistRepository.findById(playlistObjId)
          .orElseThrow(() -> new RuntimeException("Playlist not found"));

      String userId = getCurrentUser().getId();

      // 2) ownership
      if (!userId.equals(playlist.getUserId())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(java.util.Map.of("message", "Access denied"));
      }

      // 3) request songs
      List<String> songsToRemove = body.get("songs");
      if (songsToRemove == null || songsToRemove.isEmpty()) {
        return ResponseEntity.badRequest()
            .body(java.util.Map.of("message", "Missing songs"));
      }

      // 4) clean + detect duplicates + validate format
      List<String> cleaned = new java.util.ArrayList<>();
      java.util.Set<String> seen = new java.util.HashSet<>();
      java.util.Set<String> duplicates = new java.util.LinkedHashSet<>();
      java.util.Set<String> invalidIds = new java.util.LinkedHashSet<>();

      for (String sid : songsToRemove) {
        if (sid == null || sid.trim().isEmpty())
          continue;
        String s = sid.trim();

        if (!seen.add(s))
          duplicates.add(s);
        cleaned.add(s);

        if (!ObjectId.isValid(s))
          invalidIds.add(s);
      }

      if (!invalidIds.isEmpty()) {
        return ResponseEntity.badRequest().body(java.util.Map.of(
            "message", "Invalid songId(s)",
            "invalidSongIds", invalidIds));
      }

      // 5) check song existence (you asked "song not found")
      // NOTE: this is N queries if you do existsById in a loop; better do a single
      // query.
      java.util.Set<String> uniqueIds = new java.util.LinkedHashSet<>(cleaned);

      // Convert to ObjectId list
      List<ObjectId> oidList = uniqueIds.stream().map(ObjectId::new).toList();

      // Fetch existing songs by _id
      // Assumes you have SongRepository extends MongoRepository<Song, ObjectId>
      List<Song> existingSongs = songRepository.findAllById(oidList);
      java.util.Set<String> existingIds = existingSongs.stream()
          .map(s -> s.getId()) // your Song.getId() returns hex string
          .collect(java.util.stream.Collectors.toSet());

      java.util.Set<String> notFoundSongIds = new java.util.LinkedHashSet<>();
      for (String sid : uniqueIds) {
        if (!existingIds.contains(sid))
          notFoundSongIds.add(sid);
      }

      if (!notFoundSongIds.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of(
            "message", "Some songs not found",
            "notFoundSongIds", notFoundSongIds));
      }

      // 6) remove only those present in playlist
      if (playlist.getSongs() == null)
        playlist.setSongs(new java.util.ArrayList<>());

      java.util.Set<String> playlistSet = new java.util.HashSet<>(playlist.getSongs());

      java.util.Set<String> notInPlaylist = new java.util.LinkedHashSet<>();
      java.util.Set<String> removedIds = new java.util.LinkedHashSet<>();

      for (String sid : uniqueIds) {
        if (!playlistSet.contains(sid)) {
          notInPlaylist.add(sid);
        } else {
          removedIds.add(sid);
        }
      }

      boolean changed = playlist.getSongs().removeIf(removedIds::contains);
      if (changed)
        playlistRepository.save(playlist);

      return ResponseEntity.ok(java.util.Map.of(
          "message", "Remove songs completed",
          "playlistId", playlist.getId(),
          "removedCount", removedIds.size(),
          "removedSongIds", removedIds,
          "duplicateInRequest", duplicates,
          "notInPlaylist", notInPlaylist));

    } catch (RuntimeException e) {
      if ("Playlist not found".equals(e.getMessage())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(java.util.Map.of("message", e.getMessage()));
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(java.util.Map.of("message", "Error removing songs: " + e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(java.util.Map.of("message", "Error removing songs: " + e.getMessage()));
    }
  }

  // [DELETE] http://localhost:8081/api/common/playlist/delete/{id}
  // xoá playlist
  @DeleteMapping("/delete/{id}")
  public ResponseEntity<?> deletePlaylist(@PathVariable("id") String id) {
    try {
      ObjectId objectId = new ObjectId(id);
      Optional<Playlist> playlistOpt = playlistRepository.findById(objectId);

      if (playlistOpt.isEmpty()) {
        return ResponseEntity.notFound().build();
      }

      Playlist playlist = playlistOpt.get();
      String currentUserId = getCurrentUser().getId();

      // Kiểm tra quyền
      if (!playlist.getUserId().equals(currentUserId)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
      }

      // Xoá playlist
      playlistRepository.deleteById(objectId);

      // Gỡ ID khỏi Library của user
      Library library = libraryRepository.findByUserId((currentUserId));
      if (library != null) {
        List<String> playlistIds = library.getPlaylistIds();
        if (playlistIds != null && playlistIds.remove(id)) {
          libraryRepository.save(library);
        }
      }

      return ResponseEntity.ok("Playlist deleted successfully.");
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lỗi khi xoá playlist: " + e.getMessage());
    }
  }

}
