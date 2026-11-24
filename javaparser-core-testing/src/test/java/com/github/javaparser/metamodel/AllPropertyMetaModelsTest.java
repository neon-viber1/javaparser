package com.github.javaparser.metamodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AllPropertyMetaModelsTest {

    private static Set<String> names(List<PropertyMetaModel> l) {
        return l.stream().map(PropertyMetaModel::getName).collect(Collectors.toSet());
    }

    @Test
    void annotationsAbsentWhereNotSupported() {
        BaseNodeMetaModel[] models = {
            JavaParserMetaModel.initializerDeclarationMetaModel,
            JavaParserMetaModel.unionTypeMetaModel,
            JavaParserMetaModel.unknownTypeMetaModel,
            JavaParserMetaModel.voidTypeMetaModel,
            JavaParserMetaModel.varTypeMetaModel
        };
        for (BaseNodeMetaModel m : models) {
            assertFalse(names(m.getAllPropertyMetaModels()).contains("annotations"), m.getTypeName());
        }
    }

    @Test
    void annotationsPresentWhereSupported() {
        BaseNodeMetaModel[] models = {
            JavaParserMetaModel.methodDeclarationMetaModel,
            JavaParserMetaModel.fieldDeclarationMetaModel,
            JavaParserMetaModel.classOrInterfaceTypeMetaModel,
            JavaParserMetaModel.primitiveTypeMetaModel
        };
        for (BaseNodeMetaModel m : models) {
            assertTrue(names(m.getAllPropertyMetaModels()).contains("annotations"), m.getTypeName());
        }
    }

    @Test
    void concreteNodesMatchConstructorParametersPlusComment() {
        for (BaseNodeMetaModel m : JavaParserMetaModel.getNodeMetaModels()) {
            if (m.isAbstract()) continue;
            Set<String> expected = new HashSet<>(names(m.getConstructorParameters()));
            expected.add("comment");
            assertEquals(expected, names(m.getAllPropertyMetaModels()), m.getTypeName());
        }
    }

    @Test
    void listIsCachedAndUnmodifiable() {
        BaseNodeMetaModel m = JavaParserMetaModel.methodDeclarationMetaModel;
        assertSame(m.getAllPropertyMetaModels(), m.getAllPropertyMetaModels());
        assertThrows(UnsupportedOperationException.class, () -> m.getAllPropertyMetaModels()
                .clear());
    }
}
