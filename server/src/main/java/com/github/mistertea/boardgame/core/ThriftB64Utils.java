package com.github.mistertea.boardgame.core;

import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TCompactProtocol;

public class ThriftB64Utils {
	protected static TCompactProtocol.Factory protocolFactory = new TCompactProtocol.Factory();

	public static String ThriftToString(TBase<?, ?> t) {
		TSerializer serializer = new TSerializer(protocolFactory);
		try {
			return Base64.encodeBase64String(serializer.serialize(t));
		} catch (TException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T extends TBase<?, ?>> T stringToThrift(String s,
			Class<T> clazz)
			throws IOException {
		T payload;
		try {
			payload = clazz.newInstance();
		} catch (Exception e1) {
			throw new IOException(e1);
		}
		TDeserializer deserializer = new TDeserializer(protocolFactory);
		try {
			deserializer.deserialize(payload, Base64.decodeBase64(s));
		} catch (TException e) {
			throw new IOException(e);
		}
		return payload;
	}
}
