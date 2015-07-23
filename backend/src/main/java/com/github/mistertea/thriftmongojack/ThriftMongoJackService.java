package com.github.mistertea.thriftmongojack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mongojack.JacksonDBCollection;
import org.mongojack.internal.MongoJackModule;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;
import com.mongodb.MongoClient;

public class ThriftMongoJackService {
	Map<String, JacksonDBCollection<?, String>> collections = new HashMap<>();

	class MixIn {
		@org.mongojack.ObjectId
		public String _id;
	};

	private static ObjectMapper getObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		mapper.registerModule(MongoJackModule.INSTANCE);
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		return mapper;
	}

	protected ThriftMongoJackService(MongoClient mongoClient, String databaseName,
			List<Class<?>> thriftClasses) {
		ObjectMapper mapper = getObjectMapper();

		MongoJackModule.configure(mapper);

		DB db = mongoClient.getDB(databaseName);

		for (Class<?> thriftClass : thriftClasses) {
			mapper.addMixIn(thriftClass, MixIn.class);
			collections.put(thriftClass.getName(), ThriftJacksonDBCollection.wrap(
					db.getCollection(thriftClass.getName()), thriftClass, mapper));
		}
	}

	private static ThriftMongoJackService _instance = null;

	public static ThriftMongoJackService initialize(MongoClient mongoClient,
			String databaseName, List<Class<?>> thriftClasses) {
		_instance = new ThriftMongoJackService(mongoClient, databaseName,
				thriftClasses);
		return _instance;
	}

	public static ThriftMongoJackService instance() {
		if (_instance == null) {
			throw new UnsupportedOperationException(
					"Tried to use MongoJackService without initializing");
		}
		return _instance;
	}

	@SuppressWarnings("unchecked")
	public <T> ThriftJacksonDBCollection<T> getCollection(Class<T> clazz) {
		return (ThriftJacksonDBCollection<T>) collections.get(clazz.getName());
	}

}
