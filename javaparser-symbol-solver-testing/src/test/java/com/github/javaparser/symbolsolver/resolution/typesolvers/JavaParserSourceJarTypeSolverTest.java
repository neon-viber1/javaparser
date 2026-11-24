/*
 * Copyright (C) 2015-2016 Federico Tomassetti
 * Copyright (C) 2017-2026 The JavaParser Team.
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

package com.github.javaparser.symbolsolver.resolution.typesolvers;

import static org.junit.jupiter.api.Assertions.*;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserSourceJarTypeSolverTest {

    @TempDir
    Path tmp;

    private Path createJar() throws IOException {
        Path jar = tmp.resolve("sources.jar");
        try (OutputStream os = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(os)) {
            add(out, "a/b/Foo.java", "package a.b; public class Foo { public static class Inner { public int x; } }");
            add(
                    out,
                    "a/b/Const.java",
                    "package a.b; public class Const { public static final String ORDER_BY = \"x\"; }");
            add(out, "a/b/Bar.java", "package a.b; public class Bar { public String s = Const.ORDER_BY; }");
            add(out, "a/b/package-info.java", "package a.b;");
            add(out, "module-info.java", "module m {}");
        }
        return jar;
    }

    private static void add(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    @Test
    void solvesTopLevelAndNestedTypes() throws IOException {
        try (JavaParserSourceJarTypeSolver solver = new JavaParserSourceJarTypeSolver(createJar())) {
            SymbolReference<ResolvedReferenceTypeDeclaration> foo = solver.tryToSolveType("a.b.Foo");
            assertTrue(foo.isSolved());
            assertEquals("a.b.Foo", foo.getCorrespondingDeclaration().getQualifiedName());
            SymbolReference<ResolvedReferenceTypeDeclaration> inner = solver.tryToSolveType("a.b.Foo.Inner");
            assertTrue(inner.isSolved());
            assertEquals("a.b.Foo.Inner", inner.getCorrespondingDeclaration().getQualifiedName());
        }
    }

    @Test
    void unknownTypesAreUnsolved() throws IOException {
        try (JavaParserSourceJarTypeSolver solver = new JavaParserSourceJarTypeSolver(createJar())) {
            assertFalse(solver.tryToSolveType("a.b.Missing").isSolved());
            assertFalse(solver.tryToSolveType("a.b.Foo.Missing").isSolved());
            assertFalse(solver.tryToSolveType("a.b.package-info").isSolved());
        }
    }

    @Test
    void invalidPathFailsAtConstruction() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new JavaParserSourceJarTypeSolver(tmp.resolve("nope.jar")));
        Path notAJar = tmp.resolve("not.jar");
        Files.write(notAJar, "hello".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> new JavaParserSourceJarTypeSolver(notAJar));
    }

    @Test
    void resolvesFieldAccessThroughCombinedSolver() throws IOException {
        try (JavaParserSourceJarTypeSolver jarSolver = new JavaParserSourceJarTypeSolver(createJar())) {
            CombinedTypeSolver combined = new CombinedTypeSolver(new ReflectionTypeSolver(), jarSolver);
            ParserConfiguration config = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(combined));
            CompilationUnit cu = new JavaParser(config)
                    .parse("import a.b.Const; class U { Object o = Const.ORDER_BY; }")
                    .getResult()
                    .get();
            FieldAccessExpr access = cu.findFirst(FieldAccessExpr.class).get();
            assertEquals("java.lang.String", access.calculateResolvedType().describe());
            assertEquals("ORDER_BY", access.resolve().getName());
            // cross-reference within the jar
            assertTrue(combined.tryToSolveType("a.b.Bar").isSolved());
            assertEquals(
                    "java.lang.String",
                    combined.solveType("a.b.Bar").getField("s").getType().describe());
        }
    }
}
