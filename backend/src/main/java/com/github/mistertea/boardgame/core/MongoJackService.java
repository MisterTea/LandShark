package com.github.mistertea.boardgame.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.mongojack.JacksonDBCollection;
import org.mongojack.ThriftJacksonDBCollection;
import org.mongojack.internal.MongoJackModule;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mistertea.boardgame.landshark.LandsharkBox;
import com.github.mistertea.boardgame.landshark.LandsharkGame;
import com.mongodb.DB;
import com.mongodb.MongoClient;

public class MongoJackService {
  Map<String, JacksonDBCollection<?, String>> collections = new HashMap<>();

  private static ObjectMapper getObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    return mapper;
  }

  protected MongoJackService() throws RuntimeException {
    try {
      ObjectMapper mapper = getObjectMapper();

      MongoJackModule.configure(mapper);

      MongoClient mongoClient = new MongoClient("localhost", 27017);

      DB db = mongoClient.getDB("boardgamehub");
      collections
      .put(LandsharkBox.class.getName(), ThriftJacksonDBCollection.wrap(
          db.getCollection("landsharkboxes"), LandsharkBox.class, mapper));
      collections
      .put(LandsharkGame.class.getName(), ThriftJacksonDBCollection.wrap(
          db.getCollection("landsharkgames"), LandsharkGame.class, mapper));
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  private static MongoJackService _instance = null;

  public static MongoJackService instance() {
    if (_instance == null) {
      _instance = new MongoJackService();
    }
    return _instance;
  }

  @SuppressWarnings("unchecked")
  public <T> JacksonDBCollection<T, String> getCollection(Class<T> clazz) {
    return (JacksonDBCollection<T, String>) collections.get(clazz.getName());
  }

}
