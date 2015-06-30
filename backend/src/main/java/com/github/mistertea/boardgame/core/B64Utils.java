package com.github.mistertea.boardgame.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.commons.codec.binary.Base64;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TCompactProtocol;

public class B64Utils {
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
      Class<T> clazz) {
    T payload;
    try {
      payload = clazz.newInstance();
    } catch (Exception e1) {
      throw new RuntimeException(e1);
    }
    TDeserializer deserializer = new TDeserializer(protocolFactory);
    try {
      deserializer.deserialize(payload, Base64.decodeBase64(s));
    } catch (TException e) {
      throw new RuntimeException(e);
    }
    return payload;
  }

  /** Write the object to a Base64 string. */
  public static <T extends Serializable> String objectToString(T o) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(o);
      oos.close();
      return Base64.encodeBase64String(baos.toByteArray());
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /** Read the object from Base64 string. */
  @SuppressWarnings("unchecked")
  public static <T extends Serializable> T stringToObject(String s) {
    try {
      byte[] data = Base64.decodeBase64(s);
      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(
          data));
      T o = (T) ois.readObject();
      ois.close();
      return o;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
