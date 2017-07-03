/*
 * Copyright 2014 the original author or authors.
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
package org.springframework.sync.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.sync.AddOperation;
import org.springframework.sync.CopyOperation;
import org.springframework.sync.FromOperation;
import org.springframework.sync.MoveOperation;
import org.springframework.sync.Patch;
import org.springframework.sync.PatchException;
import org.springframework.sync.PatchOperation;
import org.springframework.sync.RemoveOperation;
import org.springframework.sync.ReplaceOperation;
import org.springframework.sync.TestOperation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Convert {@link JsonNode}s containing JSON Patch to/from {@link Patch} objects.
 * @author Craig Walls
 */
public class JsonPatchPatchConverter implements PatchConverter<JsonNode> {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Constructs a {@link Patch} object given a JsonNode.
	 * @param jsonNode a JsonNode containing the JSON Patch
	 * @return a {@link Patch}
	 */
	public Patch convert(JsonNode jsonNode) {
		if (!(jsonNode instanceof ArrayNode)) {
			throw new IllegalArgumentException("JsonNode must be an instance of ArrayNode");
		}
		
		ArrayNode opNodes = (ArrayNode) jsonNode;
		List<PatchOperation> ops = new ArrayList<PatchOperation>(opNodes.size());
		for(Iterator<JsonNode> elements = opNodes.elements(); elements.hasNext(); ) {
			JsonNode opNode = elements.next();
			
			String opType = opNode.get("op").textValue();
			String path = opNode.get("path").textValue();
			
			JsonNode valueNode = opNode.get("value");
			Object value = valueFromJsonNode(path, valueNode);			
			String from = opNode.has("from") ? opNode.get("from").textValue() : null;

			if ("test".equals(opType)) {
				ops.add(new TestOperation(path, value));
			} else if ("replace".equals(opType)) {
				ops.add(new ReplaceOperation(path, value));
			} else if ("remove".equals(opType)) {
				ops.add(new RemoveOperation(path));
			} else if ("add".equals(opType)) {
				ops.add(new AddOperation(path, value));
			} else if ("copy".equals(opType)) {
				ops.add(new CopyOperation(path, from));
			} else if ("move".equals(opType)) {
				ops.add(new MoveOperation(path, from));
			} else {
				throw new PatchException("Unrecognized operation type: " + opType);
			}
		}
		
		return new Patch(ops);
	}
	
	/**
	 * Renders a {@link Patch} as a {@link JsonNode}.
	 * @param patch the patch
	 * @return a {@link JsonNode} containing JSON Patch.
	 */
	public JsonNode convert(Patch patch) {
		
		List<PatchOperation> operations = patch.getOperations();
		JsonNodeFactory nodeFactory = JsonNodeFactory.instance;
		ArrayNode patchNode = nodeFactory.arrayNode();
		for (PatchOperation operation : operations) {
			ObjectNode opNode = nodeFactory.objectNode();
			opNode.set("op", nodeFactory.textNode(operation.getOp()));
			opNode.set("path", nodeFactory.textNode(operation.getPath()));
			if (operation instanceof FromOperation) {
				FromOperation fromOp = (FromOperation) operation;
				opNode.set("from", nodeFactory.textNode(fromOp.getFrom()));
			}
			Object value = operation.getValue();
			if (value != null) {
				opNode.set("value", MAPPER.valueToTree(value));
			}
			patchNode.add(opNode);
		}
		
		return patchNode;
	}

	private Object valueFromJsonNode(String path, JsonNode valueNode) {
		if (valueNode == null || valueNode.isNull()) {
			return null;
		} else if (valueNode.isTextual()) {
			return valueNode.asText();
		} else if (valueNode.isFloatingPointNumber()) {
			return valueNode.asDouble();
		} else if (valueNode.isBoolean()) {
			return valueNode.asBoolean();
		} else if (valueNode.isInt()) {
			return valueNode.asInt();
		} else if (valueNode.isLong()) {
			return valueNode.asLong();
		} else if (valueNode.isObject()) {
			return new JsonLateObjectEvaluator(valueNode);
		} else if (valueNode.isArray()) {
			// TODO: Convert valueNode to array
		}
		
		return null;
	}
	
	

	
}
