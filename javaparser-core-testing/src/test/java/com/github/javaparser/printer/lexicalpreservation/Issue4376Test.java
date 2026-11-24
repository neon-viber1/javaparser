/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2026 The JavaParser Team.
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

package com.github.javaparser.printer.lexicalpreservation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.junit.jupiter.api.Test;

public class Issue4376Test extends AbstractLexicalPreservingTest {

    private String moveLastStatement(String code) {
        CompilationUnit cu = parser.parse(code);
        LexicalPreservingPrinter.setup(cu);
        BlockStmt block = cu.findFirst(BlockStmt.class).get();
        Statement last = block.getStatements().removeLast();
        block.addStatement(last);
        return LexicalPreservingPrinter.print(cu);
    }

    @Test
    void movedStatementKeepsLineComment() {
        String code = "class A {\n" + "    void f() {\n"
                + "        // first comment\n"
                + "        int a = 1;\n"
                + "        // second comment\n"
                + "        int b = 2;\n"
                + "        // third comment\n"
                + "        int c = 3;\n"
                + "    }\n"
                + "}\n";
        assertEquals(code, moveLastStatement(code));
    }

    @Test
    void movedStatementKeepsBlockComment() {
        String code = "class A {\n" + "    void f() {\n"
                + "        int a = 1;\n"
                + "        /* third comment */\n"
                + "        int c = 3;\n"
                + "    }\n"
                + "}\n";
        assertEquals(code, moveLastStatement(code));
    }

    @Test
    void movedStatementWithoutCommentIsUnchanged() {
        String code = "class A {\n" + "    void f() {\n"
                + "        // first comment\n"
                + "        int a = 1;\n"
                + "        int c = 3;\n"
                + "    }\n"
                + "}\n";
        assertEquals(code, moveLastStatement(code));
    }

    @Test
    void removedStatementTakesOnlyItsOwnComment() {
        String code = "class A {\n" + "    void f() {\n"
                + "        // first comment\n"
                + "        int a = 1;\n"
                + "        // third comment\n"
                + "        int c = 3;\n"
                + "    }\n"
                + "}\n";
        CompilationUnit cu = parser.parse(code);
        LexicalPreservingPrinter.setup(cu);
        cu.findFirst(BlockStmt.class).get().getStatements().removeLast();
        String expected = "class A {\n" + "    void f() {\n"
                + "        // first comment\n"
                + "        int a = 1;\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, LexicalPreservingPrinter.print(cu));
    }
}
