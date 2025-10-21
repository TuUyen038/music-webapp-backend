package com.example.mobile_be.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.mobile_be.models.Song;
@Repository
public interface SongRepository extends MongoRepository<Song, ObjectId> {
    Optional<Song> findById(ObjectId id);

    List<Song> findByTitleContainingIgnoreCase(String title);

   

    List<Song> findByArtistIdInAndIsPublicTrue(List<String> artistIds);
    List<Song> findByIdInAndIsPublicTrue(List<ObjectId> id);

    // public List<Song> findTop10ByOrderByLastPlayedAtDesc();

    List<Song> findTop6ByIsPublicTrueOrderByCreatedAtDesc();

    List<Song> findByOrderByViewsDesc();
    
     List<Song> findAllByOrderByCreatedAtDesc();
    // List<Song> findAllByOrderByCreatedAtAsc();

    List<Song> findByGenreId(String genreId);
    List<Song> findByArtistIdAndIsPublicTrue(String artistId);


}
