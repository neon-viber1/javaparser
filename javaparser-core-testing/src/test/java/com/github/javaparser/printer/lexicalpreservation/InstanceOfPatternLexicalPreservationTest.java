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

import static com.github.javaparser.utils.TestUtils.assertEqualsStringIgnoringEol;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.PatternExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.Test;

class InstanceOfPatternLexicalPreservationTest extends AbstractLexicalPreservingTest {

    private static final String CODE = "class A {\n"
            + "    void m(Object obj) {\n"
            + "        if (obj instanceof String s) {\n"
            + "            System.out.println(s);\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private void setUp(String code) {
        parser.getParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21);
        considerCode(code);
    }

    @Test
    void unmodifiedPatternIsPrintedUnchanged() {
        setUp(CODE);
        assertEqualsStringIgnoringEol(CODE, LexicalPreservingPrinter.print(cu));
    }

    @Test
    void removePattern() {
        setUp(CODE);
        cu.findAll(InstanceOfExpr.class).forEach(InstanceOfExpr::removePattern);
        assertEqualsStringIgnoringEol(
                CODE.replace("obj instanceof String s)", "obj instanceof String)"), LexicalPreservingPrinter.print(cu));
    }

    @Test
    void addPattern() {
        setUp(CODE.replace("String s", "String"));
        cu.findAll(InstanceOfExpr.class)
                .forEach(i -> i.setPattern(new TypePatternExpr(
                        new com.github.javaparser.ast.NodeList<>(),
                        i.getType(),
                        new com.github.javaparser.ast.expr.SimpleName("s"))));
        assertEqualsStringIgnoringEol(CODE, LexicalPreservingPrinter.print(cu));
    }

    @Test
    void replacePattern() {
        setUp(CODE);
        cu.findAll(InstanceOfExpr.class).forEach(i -> {
            PatternExpr p = new TypePatternExpr(
                    new com.github.javaparser.ast.NodeList<>(),
                    new ClassOrInterfaceType(null, "String"),
                    new com.github.javaparser.ast.expr.SimpleName("t"));
            i.setPattern(p);
        });
        assertEqualsStringIgnoringEol(CODE.replace("String s)", "String t)"), LexicalPreservingPrinter.print(cu));
    }

    @Test
    void recordPatternIsPrintedUnchangedAndCanBeRemoved() {
        String code = CODE.replace("String s", "Point(int x, int y)");
        setUp(code);
        assertEqualsStringIgnoringEol(code, LexicalPreservingPrinter.print(cu));
        cu.findAll(InstanceOfExpr.class).forEach(InstanceOfExpr::removePattern);
        assertEqualsStringIgnoringEol(
                CODE.replace("obj instanceof String s)", "obj instanceof Point)"), LexicalPreservingPrinter.print(cu));
    }
}
