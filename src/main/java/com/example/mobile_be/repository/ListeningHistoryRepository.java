package com.example.mobile_be.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.mobile_be.models.ListeningHistory;

import java.util.List;
import java.util.Map;

@Repository
public interface ListeningHistoryRepository extends MongoRepository<ListeningHistory, ObjectId> {

  List<ListeningHistory> findByUserId(ObjectId userId);

  List<ListeningHistory> findByUserIdAndSongId(ObjectId userId, String songId);

  @Aggregation(pipeline = {
      "{ $match: { userId: ?0 } }",
      "{ $group: { _id: '$songId', count: { $sum: 1 } } }",
      "{ $sort: { count: -1 } }",
      "{ $limit: 10 }"
  })
  List<Map<String, Object>> findTopSongs(ObjectId userId);

}
