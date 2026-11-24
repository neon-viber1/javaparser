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

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.junit.jupiter.api.Test;

class SingleLineBlockInsertionTest extends AbstractLexicalPreservingTest {

    private String insert(String code, int index, String newStmt) {
        considerCode(code);
        BlockStmt block = cu.findAll(BlockStmt.class).get(0);
        Statement s = new ExpressionStmt(
                new AssignExpr(new NameExpr(newStmt), new NameExpr("x"), AssignExpr.Operator.ASSIGN));
        block.addStatement(index, s);
        return LexicalPreservingPrinter.print(cu);
    }

    @Test
    void splitDeclarationOnSingleLine() {
        considerCode("class MyClass{void myMethod(){String myParam = null;}}");
        com.github.javaparser.ast.body.VariableDeclarator vd = cu.findFirst(
                        com.github.javaparser.ast.body.VariableDeclarator.class)
                .get();
        com.github.javaparser.ast.expr.Expression init = vd.getInitializer().get();
        vd.removeInitializer();
        BlockStmt block = cu.findFirst(BlockStmt.class).get();
        block.addStatement(1, new AssignExpr(new NameExpr("myParam"), init, AssignExpr.Operator.ASSIGN));
        assertEquals(
                "class MyClass{void myMethod(){String myParam;myParam = null;}}", LexicalPreservingPrinter.print(cu));
    }

    @Test
    void insertAtEndOfSingleLineBlock() {
        assertEquals("class A{void m(){int a;b = x;}}", insert("class A{void m(){int a;}}", 1, "b"));
    }

    @Test
    void insertAtBeginningOfSingleLineBlock() {
        assertEquals("class A{void m(){b = x;int a;}}", insert("class A{void m(){int a;}}", 0, "b"));
    }

    @Test
    void insertInSingleLineBlockWithSeveralStatements() {
        assertEquals(
                "class A{void m(){int a;int c;b = x;int d;}}", insert("class A{void m(){int a;int c;int d;}}", 2, "b"));
    }

    @Test
    void multiLineBlockKeepsNewlineAndIndent() {
        assertEquals(
                "class A {\n  void m() {\n    int a;\n    b = x;\n  }\n}",
                insert("class A {\n  void m() {\n    int a;\n  }\n}", 1, "b"));
    }

    @Test
    void emptyBlockKeepsExistingBehaviour() {
        considerCode("class A{void m(){}}");
        BlockStmt block = cu.findAll(BlockStmt.class).get(0);
        block.addStatement(0, new AssignExpr(new NameExpr("b"), new NameExpr("x"), AssignExpr.Operator.ASSIGN));
        String out = LexicalPreservingPrinter.print(cu);
        assertEquals("class A{void m(){\n    b = x;\n}}", out);
    }

    @Test
    void crlfSingleLineBlock() {
        assertEquals("class A{void m(){int a;b = x;}}\r\n", insert("class A{void m(){int a;}}\r\n", 1, "b"));
    }
}
