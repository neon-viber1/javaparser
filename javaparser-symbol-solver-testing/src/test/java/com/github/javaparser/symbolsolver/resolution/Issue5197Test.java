package com.github.javaparser.symbolsolver.resolution;

import static org.junit.jupiter.api.Assertions.*;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.logic.InferenceVariableType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class Issue5197Test {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recursiveInvocationInfersItsOwnTypeParameter(boolean block) {
        String[] returnTypes = {"String", "T"};
        String[] expected = {"java.lang.String", "T"};
        for (boolean reverse : new boolean[] {false, true}) {
            StringBuilder source = new StringBuilder("import java.util.*; import java.util.function.*; class Example {"
                    + "static <T> T get(Supplier<T> supplier) {");
            for (String returnType : returnTypes) {
                source.append("get(() -> ");
                if (block) source.append("{ return ");
                source.append(returnType.equals("String") ? "\"x\"" : "(" + returnType + ") null");
                if (block) source.append("; }");
                source.append(");");
            }
            source.append("return null; }}");
            CompilationUnit unit = new JavaParser(new ParserConfiguration()
                            .setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver())))
                    .parse(source.toString())
                    .getResult()
                    .get();
            ResolvedTypeParameterDeclaration caller = unit.findFirst(MethodDeclaration.class)
                    .get()
                    .resolve()
                    .getTypeParameters()
                    .get(0);
            List<MethodCallExpr> calls = unit.findAll(MethodCallExpr.class);
            for (int repeat = 0; repeat < 2; repeat++) {
                for (int offset = 0; offset < calls.size(); offset++) {
                    int index = reverse ? calls.size() - offset - 1 : offset;
                    assertEquals(
                            "java.util.function.Supplier<" + expected[index] + ">",
                            calls.get(index)
                                    .findFirst(LambdaExpr.class)
                                    .get()
                                    .calculateResolvedType()
                                    .describe());
                    ResolvedType resolved = calls.get(index).calculateResolvedType();
                    assertEquals(expected[index], resolved.describe());
                    assertCallerParameter(resolved, caller);
                }
            }
        }
    }

    @Test
    void boundedCallerTypeCanSatisfyAConcreteFunctionalReturn() {
        String source = "import java.util.function.*; class Example {"
                + "static Number get(Supplier<Number> s) { return null; }"
                + "<U extends Number> void test() { Example.get(() -> (U) null); }}";
        CompilationUnit unit = new JavaParser(
                        new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver())))
                .parse(source)
                .getResult()
                .get();
        assertEquals(
                "java.lang.Number",
                unit.findFirst(MethodCallExpr.class)
                        .get()
                        .calculateResolvedType()
                        .describe());
        assertEquals(
                "java.util.function.Supplier<java.lang.Number>",
                unit.findFirst(LambdaExpr.class).get().calculateResolvedType().describe());
    }

    @ParameterizedTest
    @CsvSource({
        "() -> (List<U>) null, java.util.List<U>",
        "() -> (U[]) null, U[]",
        "() -> (U) null, U",
        "supplier, java.util.List<U>",
        "() -> (List<String>) null, java.util.List<java.lang.String>"
    })
    void reportedCalls(String argument, String expected) {
        String source = "import java.util.*; import java.util.function.*; class Example {"
                + "static <T> T get(Supplier<T> s) { return null; }"
                + "<U> void test(Supplier<List<U>> supplier) { Example.get(" + argument + "); }}";
        CompilationUnit unit = new JavaParser(
                        new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver())))
                .parse(source)
                .getResult()
                .get();
        assertEquals(
                expected,
                unit.findFirst(MethodCallExpr.class)
                        .get()
                        .calculateResolvedType()
                        .describe());
    }

    @ParameterizedTest
    @ValueSource(strings = {"U", "T"})
    void preservesCallerTypeParameters(String parameter) {
        String[] returnTypes = {
            "List<" + parameter + ">",
            parameter + "[]",
            parameter,
            "Map<String, List<" + parameter + "[]>>[]",
            "List<? extends " + parameter + "[]>"
        };
        for (boolean block : new boolean[] {false, true}) {
            for (boolean reverse : new boolean[] {false, true}) {
                StringBuilder source =
                        new StringBuilder("import java.util.*; import java.util.function.*; class Example {"
                                + "static <T> T get(Supplier<T> s) { return null; } <" + parameter
                                + "> void test(Supplier<List<" + parameter + ">> supplier) {");
                for (String returnType : returnTypes) {
                    source.append("Example.get(() -> ");
                    if (block) source.append("{ return ");
                    source.append("(").append(returnType).append(") null");
                    if (block) source.append("; }");
                    source.append(");");
                }
                source.append("Example.get(supplier); Example.get(() -> (List<String>) null); }}");
                CompilationUnit unit = new JavaParser(new ParserConfiguration()
                                .setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver())))
                        .parse(source.toString())
                        .getResult()
                        .get();
                ResolvedTypeParameterDeclaration caller = unit.findAll(MethodDeclaration.class).stream()
                        .filter(method -> method.getNameAsString().equals("test"))
                        .findFirst()
                        .get()
                        .resolve()
                        .getTypeParameters()
                        .get(0);
                List<MethodCallExpr> calls = unit.findAll(MethodCallExpr.class);
                String[] expected = {
                    "java.util.List<" + parameter + ">",
                    parameter + "[]",
                    parameter,
                    "java.util.Map<java.lang.String, java.util.List<" + parameter + "[]>>[]",
                    "java.util.List<? extends " + parameter + "[]>",
                    "java.util.List<" + parameter + ">",
                    "java.util.List<java.lang.String>"
                };
                for (int repeat = 0; repeat < 2; repeat++) {
                    for (int offset = 0; offset < calls.size(); offset++) {
                        int index = reverse ? calls.size() - offset - 1 : offset;
                        ResolvedType resolved = calls.get(index).calculateResolvedType();
                        assertEquals(
                                expected[index],
                                resolved.describe(),
                                calls.get(index).toString());
                        assertCallerParameter(resolved, caller);
                    }
                }
            }
        }
    }

    private void assertCallerParameter(ResolvedType type, ResolvedTypeParameterDeclaration caller) {
        assertFalse(type instanceof InferenceVariableType);
        if (type.isTypeVariable()) {
            assertEquals(caller, type.asTypeParameter());
            assertEquals(caller.getContainerId(), type.asTypeParameter().getContainerId());
            assertSame(caller.toAst().get(), type.asTypeParameter().toAst().get());
        } else if (type.isReferenceType()) {
            type.asReferenceType().typeParametersValues().forEach(value -> assertCallerParameter(value, caller));
        } else if (type.isArray()) {
            assertCallerParameter(type.asArrayType().getComponentType(), caller);
        } else if (type.isWildcard() && type.asWildcard().isBounded()) {
            assertCallerParameter(type.asWildcard().getBoundedType(), caller);
        }
    }
}
