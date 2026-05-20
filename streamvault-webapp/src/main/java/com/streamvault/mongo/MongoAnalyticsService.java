package com.streamvault.mongo;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MongoAnalyticsService {

    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "streamvault_mongodb";
    private static final String COLLECTION_NAME = "activity_logs";

    public static void logPlayEvent(int userId, int contentId, Integer episodeId, String deviceType) {
        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {

            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            Document event = new Document("event_type", "PLAY_CONTENT")
                    .append("user_id", userId)
                    .append("content_id", contentId)
                    .append("episode_id", episodeId)
                    .append("device_type", deviceType)
                    .append("progress_pct", 0)
                    .append("timestamp", LocalDateTime.now().toString());

            collection.insertOne(event);

        } catch (Exception e) {
            System.out.println("MongoDB play logging failed: " + e.getMessage());
        }
    }

    public static void logSearchEvent(int userId, String search, String type, String genre, String sort) {
        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {

            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            Document event = new Document("event_type", "SEARCH")
                    .append("user_id", userId)
                    .append("search_text", search)
                    .append("type_filter", type)
                    .append("genre_filter", genre)
                    .append("sort_filter", sort)
                    .append("timestamp", LocalDateTime.now().toString());

            collection.insertOne(event);

        } catch (Exception e) {
            System.out.println("MongoDB search logging failed: " + e.getMessage());
        }
    }

    public static List<Document> getEventTypeCounts() {
        List<Document> results = new ArrayList<>();

        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {

            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            AggregateIterable<Document> aggregation = collection.aggregate(Arrays.asList(
                    new Document("$group",
                            new Document("_id", "$event_type")
                                    .append("total_events", new Document("$sum", 1))
                    ),
                    new Document("$sort", new Document("total_events", -1))
            ));

            for (Document doc : aggregation) {
                results.add(doc);
            }

        } catch (Exception e) {
            results.add(new Document("_id", "MongoDB unavailable")
                    .append("total_events", 0)
                    .append("error", e.getMessage()));
        }

        return results;
    }

    public static List<Document> getRecentEvents() {
        List<Document> results = new ArrayList<>();

        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {

            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            FindIterable<Document> events = collection.find()
                    .sort(new Document("timestamp", -1))
                    .limit(10);

            for (Document doc : events) {
                results.add(doc);
            }

        } catch (Exception e) {
            results.add(new Document("event_type", "MongoDB unavailable")
                    .append("timestamp", e.getMessage()));
        }

        return results;
    }
}