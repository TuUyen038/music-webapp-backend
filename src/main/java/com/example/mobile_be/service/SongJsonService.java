// package com.example.mobile_be.service;

// import com.example.mobile_be.models.Song;
// import com.fasterxml.jackson.databind.ObjectMapper;

// import org.bson.types.ObjectId;
// import org.springframework.stereotype.Service;

// import java.io.File;
// import java.util.*;

// @Service
// public class SongJsonService {

//     private final SpotifyService spotifyService;
//     private final ObjectMapper mapper = new ObjectMapper();

//     public SongJsonService(SpotifyService spotifyService) {
//         this.spotifyService = spotifyService;
//     }

//     public void exportPlaylistToJson(String playlistId, String outputFile) throws Exception {
//         List<String> trackIds = spotifyService.getPlaylistTrackIds(playlistId);
//         List<Song> songs = new ArrayList<>();

//         for (String trackId : trackIds) {
//             Map metadata = spotifyService.getTrackMetadata(trackId);
//             Map features = spotifyService.getAudioFeatures(trackId);

//             Song song = new Song();
//             song.setId(new ObjectId(trackId));
//             song.setTitle((String) metadata.get("name"));
//             Map artist = ((List<Map>) metadata.get("artists")).get(0);
//             song.setArtistId((String) artist.get("id"));
//             song.setArtistName((String) artist.get("name"));
//             song.setDuration(((Number) metadata.get("duration_ms")).doubleValue() / 1000);
//             song.setTempo(((Number) features.get("tempo")).doubleValue());
//             song.setEnergy(((Number) features.get("energy")).doubleValue());
//             song.setDanceability(((Number) features.get("danceability")).doubleValue());
//             song.setValence(((Number) features.get("valence")).doubleValue());
//             song.setGenres(new ArrayList<>()); // có thể map genre từ metadata nếu cần
//             song.setTags(new ArrayList<>());   // thêm tags tuỳ logic
//             song.setDescription(""); // tuỳ thêm mô tả
//             song.setSource("Spotify");

//             songs.add(song);
//         }

//         mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), songs);
//         System.out.println("Exported " + songs.size() + " songs to " + outputFile);
//     }
// }
