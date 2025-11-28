package com.example.mobile_be.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.mobile_be.dto.HistoryRequest;
import com.example.mobile_be.models.ListeningHistory;
import com.example.mobile_be.models.Song;
import com.example.mobile_be.repository.ListeningHistoryRepository;
import com.example.mobile_be.repository.SongRepository;
@Service
public class ListeningHistoryService {
  @Autowired
  private ListeningHistoryRepository repo;
  @Autowired
  private SongService songService; 
  @Autowired
  private SongRepository songRepository;
  public void saveHistory(ObjectId userId, HistoryRequest req) {
    ListeningHistory history = new ListeningHistory();      
    history.setUserId(userId);
    history.setSongId(req.getSongId());
    history.setDuration(req.getDuration());
    history.setCreatedAt(LocalDateTime.now());

    repo.save(history);
  }

  

  public List<Song> recommendForUser(ObjectId userId) {

        // 1. Lấy top bài hát user nghe nhiều nhất
        List<Map<String, Object>> topSongs = repo.findTopSongs(userId);

        // Nếu user chưa nghe gì → gợi ý random
        if (topSongs == null || topSongs.isEmpty()) {
            return songService.getRandomSongs(20);
        }

        Set<String> artists = new HashSet<>();
        Set<String> genres  = new HashSet<>();

        // 2. Lấy thông tin bài hát top để gom genre/artist
        for (Map<String, Object> row : topSongs) {

            String songId = (String) row.get("_id");

            songService.getSongById(new ObjectId(songId)).ifPresent(song -> {
                if (song.getArtistId() != null) artists.add(song.getArtistId());
                if (song.getGenreId() != null)  genres.addAll(song.getGenreId());
            });
        }

        // 3. Nếu không tìm được thông tin → random
        if (artists.isEmpty() && genres.isEmpty()) {
            return songService.getRandomSongs(20);
        }

        // 4. Gợi ý bài hát cùng artist/genre
        List<Song> recommendList = songService.findByArtistOrGenre(artists, genres);

        // Nếu vẫn rỗng → random
        if (recommendList.isEmpty()) {
            return songService.getRandomSongs(20);
        }

        return recommendList;
    }



  public List<ListeningHistory> getHistory(ObjectId userId) {
        return repo.findByUserId(userId); // lấy lịch sử nghe của user
    }

}
