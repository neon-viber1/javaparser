/*
 * Copyright (C) 2013-2026 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */
package com.github.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * HotSpot never JIT-compiles methods of 8000 bytes of bytecode or more (-XX:+DontCompileHugeMethods).
 * The lexer's methods must stay below that limit, otherwise lexing always runs in the interpreter.
 */
class GeneratedJavaParserTokenManagerSizeTest {

    private static final int HUGE_METHOD_LIMIT = 8000;

    @Test
    void noMethodIsTooLargeToBeCompiled() throws IOException {
        Map<String, Integer> sizes = readMethodCodeSizes(GeneratedJavaParserTokenManager.class);
        List<String> tooLarge = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sizes.entrySet()) {
            if (entry.getValue() >= HUGE_METHOD_LIMIT) {
                tooLarge.add(entry.getKey() + " (" + entry.getValue() + " bytes)");
            }
        }
        assertTrue(
                tooLarge.isEmpty(),
                "Methods of GeneratedJavaParserTokenManager must be smaller than " + HUGE_METHOD_LIMIT
                        + " bytes of bytecode, but found: " + tooLarge);
    }

    @Test
    void nfaMethodHasBeenSplit() throws IOException {
        // Fails if the post-processing of the generated lexer no longer finds the code it splits.
        Map<String, Integer> sizes = readMethodCodeSizes(GeneratedJavaParserTokenManager.class);
        assertTrue(sizes.containsKey("jjMoveNfa_0"), "jjMoveNfa_0 not found");
        assertEquals(
                3,
                sizes.keySet().stream()
                        .filter(name -> name.startsWith("jjMoveNfa_0_"))
                        .count(),
                "jjMoveNfa_0 should have been split into three helper methods: " + sizes.keySet());
    }

    /** Minimal class file reader returning the length of the Code attribute of each method. */
    private static Map<String, Integer> readMethodCodeSizes(Class<?> clazz) throws IOException {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(in != null, "Class file not found: " + resource);
            DataInputStream data = new DataInputStream(in);
            data.readInt(); // magic
            data.readUnsignedShort(); // minor
            data.readUnsignedShort(); // major
            int poolCount = data.readUnsignedShort();
            String[] utf8 = new String[poolCount];
            for (int i = 1; i < poolCount; i++) {
                int tag = data.readUnsignedByte();
                switch (tag) {
                    case 1:
                        utf8[i] = data.readUTF();
                        break;
                    case 3:
                    case 4:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        data.readInt();
                        break;
                    case 5:
                    case 6:
                        data.readLong();
                        i++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        data.readUnsignedShort();
                        break;
                    case 15:
                        data.readUnsignedByte();
                        data.readUnsignedShort();
                        break;
                    default:
                        throw new IOException("Unknown constant pool tag " + tag);
                }
            }
            data.readUnsignedShort(); // access flags
            data.readUnsignedShort(); // this class
            data.readUnsignedShort(); // super class
            int interfaces = data.readUnsignedShort();
            for (int i = 0; i < interfaces; i++) {
                data.readUnsignedShort();
            }
            int fields = data.readUnsignedShort();
            for (int i = 0; i < fields; i++) {
                data.readUnsignedShort();
                data.readUnsignedShort();
                data.readUnsignedShort();
                skipAttributes(data);
            }
            int methods = data.readUnsignedShort();
            Map<String, Integer> result = new HashMap<>();
            for (int i = 0; i < methods; i++) {
                data.readUnsignedShort(); // access flags
                String name = utf8[data.readUnsignedShort()];
                data.readUnsignedShort(); // descriptor
                int attributes = data.readUnsignedShort();
                for (int a = 0; a < attributes; a++) {
                    String attributeName = utf8[data.readUnsignedShort()];
                    int length = data.readInt();
                    if ("Code".equals(attributeName)) {
                        data.readUnsignedShort(); // max stack
                        data.readUnsignedShort(); // max locals
                        int codeLength = data.readInt();
                        result.merge(name, codeLength, Math::max);
                        skipFully(data, codeLength);
                        skipFully(data, length - 8 - codeLength);
                    } else {
                        skipFully(data, length);
                    }
                }
            }
            return result;
        }
    }

    private static void skipAttributes(DataInputStream data) throws IOException {
        int attributes = data.readUnsignedShort();
        for (int a = 0; a < attributes; a++) {
            data.readUnsignedShort();
            skipFully(data, data.readInt());
        }
    }

    private static void skipFully(DataInputStream data, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = data.skipBytes(remaining);
            if (skipped <= 0) {
                throw new IOException("Unexpected end of class file");
            }
            remaining -= skipped;
        }
    }
}
