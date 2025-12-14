package com.example.mobile_be.service;

import java.time.LocalDateTime;
import java.util.HashMap;
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

        List<Map<String, Object>> topSongs = repo.findTopSongs(userId);

        if (topSongs == null || topSongs.isEmpty()) {
            return songService.getRandomSongs(10);
        }

        Set<String> artists = new HashSet<>();
        Set<String> genres = new HashSet<>();

        // NEW: map đếm lượt nghe theo artist
        Map<String, Long> artistPlayCount = new HashMap<>();

        for (Map<String, Object> row : topSongs) {
            String songId = (String) row.get("_id");
            long count = ((Number) row.get("count")).longValue(); // số lần nghe bài này

            songService.getSongById(new ObjectId(songId)).ifPresent(song -> {
                if (song.getArtistId() != null) {
                    artists.add(song.getArtistId());

                    // cộng dồn lượt nghe vào artist đó
                    artistPlayCount.merge(song.getArtistId(), count, Long::sum);
                }
                if (song.getGenreId() != null) {
                    genres.addAll(song.getGenreId());
                }
            });
        }

        if (artists.isEmpty() && genres.isEmpty()) {
            return songService.getRandomSongs(10);
        }

        List<Song> recommendList = songService.findByArtistOrGenre(artists, genres);

        if (recommendList.isEmpty()) {
            return songService.getRandomSongs(10);
        }
        // Giả sử Song có field: private Instant createdAt; hoặc Date, LocalDateTime...
        // Điều chỉnh lại cho đúng kiểu dữ liệu của bạn nhé

        recommendList.sort((s1, s2) -> {
            long score1 = artistPlayCount.getOrDefault(s1.getArtistId(), 0L);
            long score2 = artistPlayCount.getOrDefault(s2.getArtistId(), 0L);

            // 1. So sánh theo lượt nghe (artistScore) – ưu tiên nhiều nghe hơn
            int cmp = Long.compare(score2, score1); // score2 > score1 -> s2 đứng trước

            // 2. Nếu chênh lệch lượt nghe KHÔNG NHIỀU (ví dụ <= 5 lần)
            // hoặc bằng nhau, thì ưu tiên bài MỚI HƠN
            long diff = Math.abs(score1 - score2);
            if ((cmp == 0 || diff <= 50) // "thêm xíu ưu tiên" cho bài mới
                    && s1.getCreatedAt() != null
                    && s2.getCreatedAt() != null) {

                // Mới hơn đứng trước
                cmp = s2.getCreatedAt().compareTo(s1.getCreatedAt());
            }

            return cmp;
        });
        return recommendList;
    }

    public List<ListeningHistory> getHistory(ObjectId userId) {
        return repo.findByUserId(userId); // lấy lịch sử nghe của user
    }

}
