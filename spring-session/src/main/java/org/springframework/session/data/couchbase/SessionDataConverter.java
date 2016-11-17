/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.session.data.couchbase;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.util.Base64Utils;
import org.springframework.util.ClassUtils;
import org.springframework.util.SerializationUtils;

/**
 * Converts between {@link CouchbaseSession} and {@link SessionDocument}.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
public class SessionDataConverter implements GenericConverter {

	protected static final String SERIALIZED_OBJECT_PREFIX = "_$object=";

	public Set<ConvertiblePair> getConvertibleTypes() {
		Set<ConvertiblePair> convertibleTypes = new HashSet<ConvertiblePair>(2);
		convertibleTypes
				.add(new ConvertiblePair(CouchbaseSession.class, SessionDocument.class));
		convertibleTypes
				.add(new ConvertiblePair(SessionDocument.class, CouchbaseSession.class));
		return Collections.unmodifiableSet(convertibleTypes);
	}

	public Object convert(Object source, TypeDescriptor sourceType,
			TypeDescriptor targetType) {
		if (CouchbaseSession.class.isAssignableFrom(sourceType.getType())) {
			CouchbaseSession session = (CouchbaseSession) source;
			return new SessionDocument(session.getId(),
					serializeSessionAttributes(session.getAttributes()));
		}
		else {
			SessionDocument document = (SessionDocument) source;
			return new CouchbaseSession(document.getId(),
					deserializeSessionAttributes(document.getData()));
		}
	}

	protected Map<String, Object> serializeSessionAttributes(
			Map<String, Object> attributes) {
		if (attributes == null) {
			return null;
		}
		Map<String, Object> serialized = new HashMap<String, Object>(attributes.size());
		for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
			if (isDeserializedObject(attribute.getValue())) {
				Object attributeValue = Base64Utils.encodeToString(
						SerializationUtils.serialize(attribute.getValue()));
				serialized.put(attribute.getKey(),
						SERIALIZED_OBJECT_PREFIX + attributeValue);
			}
			else {
				serialized.put(attribute.getKey(), attribute.getValue());
			}
		}
		return serialized;
	}

	protected Map<String, Object> deserializeSessionAttributes(
			Map<String, Object> attributes) {
		if (attributes == null) {
			return null;
		}
		Map<String, Object> deserialized = new HashMap<String, Object>(attributes.size());
		for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
			Object attributeValue = attribute.getValue();
			if (isSerializedObject(attribute.getValue())) {
				String content = StringUtils.removeStart(attribute.getValue().toString(),
						SERIALIZED_OBJECT_PREFIX);
				attributeValue = SerializationUtils
						.deserialize(Base64Utils.decodeFromString(content));
			}
			deserialized.put(attribute.getKey(), attributeValue);
		}
		return deserialized;
	}

	protected boolean isDeserializedObject(Object attributeValue) {
		return attributeValue != null
				&& !ClassUtils.isPrimitiveOrWrapper(attributeValue.getClass())
				&& !(attributeValue instanceof String);
	}

	protected boolean isSerializedObject(Object attributeValue) {
		return attributeValue != null && attributeValue instanceof String && StringUtils
				.startsWith(attributeValue.toString(), SERIALIZED_OBJECT_PREFIX);
	}
}
