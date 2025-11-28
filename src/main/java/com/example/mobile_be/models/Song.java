package com.example.mobile_be.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;

import java.lang.reflect.Array;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)

@Document(collection = "song")

public class Song extends BaseDocument {

    @Id
    private ObjectId id;
    private String artistId;
    private String description;
    // private String artistName; //for spotify
    // private List<String> genres;
 
    @TextIndexed 
    private String title;
    private String audioUrl;
    private String coverImageUrl;
    @Indexed
    private List<String> genreId=  new ArrayList<>();
    private Boolean isApproved;
    private Boolean isPublic;
    private String lyricUrl;
    private Double duration;
    private Long views = 0l;
    private Date lastPlayedAt;

    // private Double tempo;          // BPM
    // private Double energy;         // 0-1
    // private Double danceability;   // 0-1
    // private Double valence;        // 0-1
    // private List<String> tags;     // mood, situation, playlist tags
    // private String source;
    public String getId() {
        return id != null ? id.toHexString() : null;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }
}
