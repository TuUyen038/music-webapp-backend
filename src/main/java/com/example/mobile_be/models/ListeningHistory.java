package com.example.mobile_be.models;

import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Document(collection = "listening_history")

public class ListeningHistory {
  @Id
    private ObjectId id;

    private ObjectId userId;
    private String songId;
    private Long duration;
    private LocalDateTime createdAt;
}
