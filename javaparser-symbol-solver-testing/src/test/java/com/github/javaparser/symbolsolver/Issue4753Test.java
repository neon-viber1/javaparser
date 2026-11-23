package com.github.javaparser.symbolsolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class Issue4753Test extends AbstractSymbolResolutionTest {

    private ResolvedMethodDeclaration method(String name) throws IOException {
        Path sourceRoot = adaptPath("src/test/resources/issue4753");
        Path jdaImplPath = sourceRoot.resolve("example/JDAImpl.java");
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(
                new CombinedTypeSolver(new JavaParserTypeSolver(sourceRoot), new ReflectionTypeSolver(true)));
        StaticJavaParser.setConfiguration(new ParserConfiguration().setSymbolResolver(symbolSolver));
        ResolvedReferenceTypeDeclaration jdaImpl = StaticJavaParser.parse(jdaImplPath)
                .findFirst(ClassOrInterfaceDeclaration.class)
                .get()
                .resolve();
        return jdaImpl.getDeclaredMethods().stream()
                .filter(r -> r.getName().equals(name))
                .findFirst()
                .get();
    }

    @Test
    void inheritedNestedTypeFromInterface() throws IOException {
        assertEquals(
                "example.TestEntitlementCreateAction.OwnerType",
                method("createTestEntitlement")
                        .getParam(0)
                        .getType()
                        .asReferenceType()
                        .getQualifiedName());
    }

    @Test
    void inheritedNestedTypeThroughTwoLevels() throws IOException {
        assertEquals(
                "example.TestEntitlementCreateAction.OwnerType",
                method("twoLevels").getParam(0).getType().asReferenceType().getQualifiedName());
    }

    @Test
    void missingNestedTypeIsUnsolved() throws IOException {
        ResolvedMethodDeclaration m = method("missing");
        assertThrows(UnsolvedSymbolException.class, () -> m.getParam(0).getType());
    }
}
