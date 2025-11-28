// package com.example.mobile_be.models;

// import lombok.Data;
// import lombok.EqualsAndHashCode;

// import org.springframework.data.mongodb.core.index.Indexed;
// import org.springframework.data.mongodb.core.index.TextIndexed;
// import org.springframework.data.mongodb.core.mapping.Document;
// import org.bson.types.ObjectId;
// import org.springframework.data.annotation.Id;

// import java.lang.reflect.Array;
// import java.sql.Date;
// import java.util.ArrayList;
// import java.util.List;

// @Data
// public class TrackInfo {
//     private String id;
//     private String name;
//     private List<String> artists;
//     private List<String> genres;          // từ artist
//     private String album;
//     private Integer popularity;

//     // Audio features
//     private Double danceability;
//     private Double energy;
//     private Double acousticness;
//     private Double valence;
//     private Double tempo;
//     private Double liveness;
//     private Double speechiness;

//     private Integer durationMs;
//     private Boolean explicit;
// }
