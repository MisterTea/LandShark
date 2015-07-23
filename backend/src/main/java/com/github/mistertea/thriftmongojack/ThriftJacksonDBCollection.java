package com.github.mistertea.thriftmongojack;

import java.lang.reflect.Field;
import java.util.Map;

import org.mongojack.JacksonDBCollection;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;

public class ThriftJacksonDBCollection<T> extends
		JacksonDBCollection<T, String> {
	Field idField;

	protected ThriftJacksonDBCollection(DBCollection dbCollection,
			Class<T> payloadClass, JavaType type, ObjectMapper objectMapper,
			Class<?> view, Map<Feature, Boolean> features) {
		super(dbCollection, type, objectMapper.constructType(String.class),
				objectMapper, view, features);
		try {
			idField = payloadClass.getField("_id");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> ThriftJacksonDBCollection<T> wrap(
			DBCollection dbCollection, Class<T> type, ObjectMapper objectMapper) {
		return new ThriftJacksonDBCollection<T>(dbCollection, type,
				objectMapper.constructType(type), objectMapper, null, null);
	}

	public T upsert(BasicDBObject query, T payload) {
		T existingPayload = findOne(query);
		if (existingPayload == null) {
			return insert(payload).getSavedObject();
		} else {
			try {
				String id = (String)idField.get(existingPayload);
				idField.set(payload, id);
				updateById(id, payload);
				return payload;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

}
