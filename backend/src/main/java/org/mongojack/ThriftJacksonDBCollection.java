package org.mongojack;

import java.util.Map;

import org.mongojack.JacksonDBCollection;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DBCollection;

public class ThriftJacksonDBCollection<T> extends
    JacksonDBCollection<T, String> {
  protected ThriftJacksonDBCollection(DBCollection dbCollection, JavaType type,
      ObjectMapper objectMapper, Class<?> view, Map<Feature, Boolean> features) {
    super(dbCollection, type, objectMapper.constructType(String.class),
        objectMapper, view, features);
  }

  public static <T> JacksonDBCollection<T, String> wrap(
      DBCollection dbCollection, Class<T> type, ObjectMapper objectMapper) {
    return new ThriftJacksonDBCollection<T>(dbCollection,
        objectMapper.constructType(type), objectMapper, null, null);
  }

}
