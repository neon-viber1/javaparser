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

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.Test;

class Issue3665Test {

    private static final String SRC = "List<Integer> list = new ArrayList<Integer>();";

    private ClassOrInterfaceType setup(BodyDeclaration<?>[] holder) {
        BodyDeclaration<?> node = com.github.javaparser.StaticJavaParser.parseBodyDeclaration(SRC);
        LexicalPreservingPrinter.setup(node);
        holder[0] = node;
        return node.findFirst(ObjectCreationExpr.class).get().getType();
    }

    @Test
    void clearThenAddTypeArgumentInPlace() {
        BodyDeclaration<?>[] h = new BodyDeclaration<?>[1];
        ClassOrInterfaceType t = setup(h);
        t.getTypeArguments().get().clear();
        t.getTypeArguments().get().add(new ClassOrInterfaceType().setName("Long"));
        assertEquals("List<Integer> list = new ArrayList<Long>();", LexicalPreservingPrinter.print(h[0]));
    }

    @Test
    void clearThenAddMatchesToString() {
        BodyDeclaration<?>[] h = new BodyDeclaration<?>[1];
        ClassOrInterfaceType t = setup(h);
        t.getTypeArguments().get().clear();
        t.getTypeArguments().get().add(new ClassOrInterfaceType().setName("Long"));
        assertEquals(h[0].toString(), LexicalPreservingPrinter.print(h[0]));
    }

    @Test
    void setTypeArgumentsOnParent() {
        BodyDeclaration<?>[] h = new BodyDeclaration<?>[1];
        ClassOrInterfaceType t = setup(h);
        t.setTypeArguments(new ClassOrInterfaceType().setName("Long"));
        assertEquals("List<Integer> list = new ArrayList<Long>();", LexicalPreservingPrinter.print(h[0]));
    }

    @Test
    void replaceTypeArgumentInPlace() {
        BodyDeclaration<?>[] h = new BodyDeclaration<?>[1];
        ClassOrInterfaceType t = setup(h);
        t.getTypeArguments().get().set(0, new ClassOrInterfaceType().setName("Long"));
        assertEquals("List<Integer> list = new ArrayList<Long>();", LexicalPreservingPrinter.print(h[0]));
    }
}
