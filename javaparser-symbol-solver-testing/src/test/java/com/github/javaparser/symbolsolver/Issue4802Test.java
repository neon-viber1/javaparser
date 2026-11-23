package com.github.javaparser.symbolsolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;

class Issue4802Test {

    private CompilationUnit parse(String body) {
        String code = "import java.util.*;\nclass Scratch {\n void foo() {\n"
                + "Map<Long, List<String>> map = new HashMap<>();\n" + body + "\n}\n}\n";
        ParserConfiguration config =
                new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));
        return new JavaParser(config).parse(code).getResult().get();
    }

    private MethodCallExpr call(CompilationUnit cu, String name) {
        return cu.findAll(MethodCallExpr.class).stream()
                .filter(e -> name.equals(e.getNameAsString()))
                .findFirst()
                .get();
    }

    @Test
    void computeIfAbsentWithDiamondInLambda() {
        CompilationUnit cu = parse("map.computeIfAbsent(0L, unused -> new ArrayList<>()).add(\"\");");
        ResolvedMethodDeclaration rmd = call(cu, "add").resolve();
        assertEquals("java.util.List.add(E)", rmd.getQualifiedSignature());
    }

    @Test
    void computeIfAbsentResultType() {
        CompilationUnit cu = parse("map.computeIfAbsent(0L, unused -> new ArrayList<>()).add(\"\");");
        assertEquals(
                "java.util.List<java.lang.String>",
                call(cu, "computeIfAbsent").calculateResolvedType().describe());
    }

    @Test
    void putWithDiamondArgument() {
        CompilationUnit cu = parse("map.put(0L, new ArrayList<>());");
        ResolvedMethodDeclaration rmd = call(cu, "put").resolve();
        assertEquals("java.util.Map.put(K, V)", rmd.getQualifiedSignature());
    }
}
