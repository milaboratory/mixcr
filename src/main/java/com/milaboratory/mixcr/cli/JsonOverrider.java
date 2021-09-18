/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.ParseUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonOverrider {
    static boolean suppressSameValueOverride = false;

    public static <T> T override(T object, Class<? super T> clazz, String... commands) {
        JsonNode node = GlobalObjectMappers.ONE_LINE.valueToTree(object);
        for (String command : commands)
            if (!override(node, command))
                return null;
        try {
            return (T) GlobalObjectMappers.ONE_LINE.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException();
        }
    }

    public static <T> T override(T object, Class<? super T> clazz, Map<String, String> overrideMap) {
        JsonNode node = GlobalObjectMappers.ONE_LINE.valueToTree(object);
        for (Map.Entry<String, String> entry : overrideMap.entrySet())
            if (!override(node, entry.getKey(), entry.getValue()))
                return null;
        try {
            return (T) GlobalObjectMappers.ONE_LINE.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean override(JsonNode node, String command) {
        String[] split = command.split("=", 2);
        String path = split[0];
        String value = split[1];
        return override(node, path, value);
    }

    public static boolean override(JsonNode node, String path, String value) {
        return override1(node, path, value, false);
    }

    private static boolean override1(JsonNode node, String path, String value, boolean v) {
        if (node == null)
            return false;
        value = value.replaceAll("^[\'\"]", "").replaceAll("[\'\"]$", "");
        boolean b = false;
        if (override0(node, path, value))
            b = true;
        else {
            Iterator<JsonNode> iterator = node.iterator();
            while (iterator.hasNext())
                if (override1(iterator.next(), path, value, b || v))
                    b = true;
        }
        if (v && b)
            throw new IllegalArgumentException("Multiple matches of parameter " + path);
        return b;
    }

    private static void overrideWarn(String fieldName, String newValue) {
        if (!suppressSameValueOverride)
            System.out.printf("WARNING: unnecessary override -O%s=%s with the same value.\n", fieldName, newValue);
    }

    public static boolean override0(JsonNode node, String path, String value) {
        String[] pathArray = path.split("\\.");
        for (int i = 0; i < pathArray.length - 1; ++i)
            if ((node = node.get(pathArray[i])) == null)
                return false;

        String fieldName = pathArray[pathArray.length - 1];

        boolean setToNull = value.equalsIgnoreCase("null");

        if (!(node instanceof ObjectNode))
            return false;

        ObjectNode oNode = (ObjectNode) node;

        JsonNode valueNode = oNode.get(fieldName);
        if (valueNode == null) {
            if (setToNull)
                overrideWarn(fieldName, value);
            return setToNull;
        }

        JsonNode valueTree = null;
        if (value.startsWith("{") && value.endsWith("}")) {
            try {
                valueTree = GlobalObjectMappers.ONE_LINE.readTree(value);
            } catch (Throwable ignored) {}
        }

        if (valueNode instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) valueNode;
            List<String> oldValues = new ArrayList<>();
            final Iterator<JsonNode> it = arrayNode.elements();
            while (it.hasNext())
                oldValues.add(it.next().asText());

            arrayNode.removeAll();

            boolean settingTheSame;
            if (!value.startsWith("[") || !value.endsWith("]")) {
                arrayNode.add(value);
                settingTheSame = oldValues.size() == 1 && oldValues.get(0).equalsIgnoreCase(value);
            } else {
                value = value.substring(1, value.length() - 1);
                String[] values = ParseUtil.splitWithBrackets(value, ',', "(){}[]");
                settingTheSame = true;
                for (int i = 0; i < values.length; i++) {
                    arrayNode.add(values[i]);
                    if (settingTheSame && oldValues.size() > i)
                        settingTheSame = oldValues.get(i).equalsIgnoreCase(values[i]);
                }
            }
            if (settingTheSame)
                overrideWarn(fieldName, value);
            return true;
        } else if (valueTree != null) {
            oNode.set(fieldName, valueTree);
            return true;
        } else if (valueNode.isTextual()) {
            if (valueNode.asText().equals(value))
                overrideWarn(fieldName, value);
            oNode.put(fieldName, value);
            return true;
        } else if (valueNode.isBoolean()) {
            boolean v;
            if (value.equalsIgnoreCase("true"))
                v = true;
            else if (value.equalsIgnoreCase("false"))
                v = false;
            else
                return false;
            if (v == valueNode.asBoolean())
                overrideWarn(fieldName, value);
            oNode.put(fieldName, v);
            return true;
        } else if (valueNode.isIntegralNumber()) {
            long v;
            try {
                v = Long.parseLong(value);
            } catch (NumberFormatException e) {
                return false;
            }
            if (v == valueNode.asLong())
                overrideWarn(fieldName, value);
            oNode.put(fieldName, v);
            return true;
        } else if (valueNode.isFloatingPointNumber()) {
            double v;
            try {
                v = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return false;
            }
            if (v == valueNode.asDouble())
                overrideWarn(fieldName, value);
            oNode.put(fieldName, v);
            return true;
        } else if (valueNode.isObject() && setToNull) {
            if (valueNode.isNull())
                overrideWarn(fieldName, value);
            oNode.set(fieldName, NullNode.getInstance());
            return true;
        } else if (valueNode.isNull()) {
            oNode.put(fieldName, value);
            return true;
        }
        return false;
    }

    public static JsonNode getNodeByPath(JsonNode node, String path) {
        return getNodeByPath(node, path.split("\\."));
    }

    public static JsonNode getNodeByPath(JsonNode node, String[] pathArray) {
        for (int i = 0; i < pathArray.length; ++i)
            if ((node = node.get(pathArray[i])) == null)
                return null;
        return node;
    }
}
