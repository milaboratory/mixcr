/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.milaboratory.mixcr.util.ParseUtil;
import com.milaboratory.util.GlobalObjectMappers;

import java.util.Iterator;
import java.util.Map;

public class JsonOverrider {
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
            throw new IllegalArgumentException();
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
        boolean b = false;
        if (override0(node, path, value))
            b = true;
        Iterator<JsonNode> iterator = node.iterator();
        while (iterator.hasNext())
            if (override1(iterator.next(), path, value, b || v))
                b = true;
        if (v && b)
            throw new IllegalArgumentException("Multiple matches of parameter " + path);
        return b;
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
        if (valueNode == null)
            return setToNull;
        if (valueNode instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) valueNode;
            arrayNode.removeAll();

            if (!value.startsWith("[") || !value.endsWith("]"))
                arrayNode.add(value);
            else {
                value = value.substring(1, value.length() - 1);
                String[] values = ParseUtil.splitWithBrackets(value, ',', "(){}[]");
                for (String v : values)
                    arrayNode.add(v);
            }
            return true;
        } else if (valueNode.isTextual()) {
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
            oNode.put(fieldName, v);
            return true;
        } else if (valueNode.isIntegralNumber()) {
            long v;
            try {
                v = Long.parseLong(value);
            } catch (NumberFormatException e) {
                return false;
            }
            oNode.put(fieldName, v);
            return true;
        } else if (valueNode.isFloatingPointNumber()) {
            double v;
            try {
                v = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return false;
            }
            oNode.put(fieldName, v);
            return true;
        } else if (valueNode.isObject() && setToNull) {
            oNode.set(fieldName, NullNode.getInstance());
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
