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

package com.github.javaparser.utils;

import static com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_9;
import static com.github.javaparser.utils.CodeGenerationUtils.mavenModuleRoot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserCollectionStrategyTest {

    @Test
    void collectThenParseProjectSources(@TempDir Path projectDirectory) throws IOException {
        Path mainRoot = projectDirectory.resolve("src/main/java");
        Path testRoot = projectDirectory.resolve("src/test/java");
        Files.createDirectories(mainRoot.resolve("example"));
        Files.createDirectories(testRoot.resolve("example"));
        Files.write(mainRoot.resolve("example/Main.java"), "package example; class Main {}".getBytes());
        Files.write(testRoot.resolve("example/MainTest.java"), "package example; class MainTest {}".getBytes());
        Files.write(mainRoot.resolve("module-info.java"), "module example {}".getBytes());

        ParserConfiguration configuration =
                new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_9);
        ProjectRoot emptyProject = new ProjectRoot(projectDirectory, configuration);
        assertThat(emptyProject.getRoot()).isEqualTo(projectDirectory);
        assertThat(emptyProject.getSourceRoots()).isEmpty();

        ProjectRoot project = new ParserCollectionStrategy(configuration).collect(projectDirectory);
        assertThat(project.getSourceRoots())
                .extracting(SourceRoot::getRoot)
                .containsExactlyInAnyOrder(mainRoot, testRoot);
        for (SourceRoot sourceRoot : project.getSourceRoots()) {
            assertThat(sourceRoot.getParserConfiguration()).isSameAs(configuration);
            assertThat(sourceRoot.getCache()).isEmpty();
            assertThat(sourceRoot.getCompilationUnits()).isEmpty();
        }

        List<String> declarations = new ArrayList<>();
        for (SourceRoot sourceRoot : project.getSourceRoots()) {
            assertThat(sourceRoot.tryToParse()).allMatch(ParseResult::isSuccessful);
            for (CompilationUnit unit : sourceRoot.getCompilationUnits()) {
                unit.findAll(ClassOrInterfaceDeclaration.class).forEach(declaration -> {
                    System.out.println(declaration.getNameAsString());
                    declarations.add(declaration.getNameAsString());
                });
            }
        }
        assertThat(declarations).containsExactlyInAnyOrder("Main", "MainTest");
    }

    private final ParserCollectionStrategy parserCollectionStrategy =
            new ParserCollectionStrategy(new ParserConfiguration().setLanguageLevel(JAVA_9));

    @Test
    void getSourceRoots() {
        final Path root =
                mavenModuleRoot(ParserCollectionStrategyTest.class).resolve("").getParent();
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        assertThat(projectRoot.getSourceRoots()).isNotEmpty();
        assertThat(projectRoot.getSourceRoot(root.resolve("javaparser-core/src/main/java")))
                .isNotEmpty();
        assertThat(projectRoot.getSourceRoot(root.resolve("javaparser-core-generators/src/main/java")))
                .isNotEmpty();
        assertThat(projectRoot.getSourceRoot(root.resolve("javaparser-core-metamodel-generator/src/main/java")))
                .isNotEmpty();
        assertThat(projectRoot.getSourceRoot(root.resolve("javaparser-symbol-solver-core/src/main/java")))
                .isNotEmpty();
    }

    @Test
    void rootAreFound_singleJavaFileInPackage() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve("src/test/resources/com/github/javaparser/utils/projectroot/issue2615/without_module_info");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        sourceRoots.forEach(System.out::println);

        assertThat(sourceRoots)
                .map(SourceRoot::getRoot)
                .extracting(Path::getFileName)
                .map(Path::getFileName)
                .map(Path::toString)
                .containsExactly("without_module_info");
    }

    @Test
    void rootsAreFound_withModuleInfoAndJavaFileInPackage() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve("src/test/resources/com/github/javaparser/utils/projectroot/issue2615/with_module_info");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        sourceRoots.forEach(System.out::println);

        assertThat(sourceRoots)
                .map(SourceRoot::getRoot)
                .extracting(Path::getFileName)
                .map(Path::getFileName)
                .map(Path::toString)
                .containsExactly("with_module_info");
    }

    @Test
    void rootsAreFound_withModuleInfoInRootAndJavaFileInPackage() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve(
                        "src/test/resources/com/github/javaparser/utils/projectroot/issue2615/with_module_info_in_root");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        sourceRoots.forEach(System.out::println);

        assertThat(sourceRoots)
                .map(SourceRoot::getRoot)
                .extracting(Path::getFileName)
                .map(Path::getFileName)
                .map(Path::toString)
                .containsExactly("with_module_info_in_root");
    }

    @Test
    void rootsAreFound_parentOfMultipleSourceRootsWithAndWithoutModuleInfo() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve("src/test/resources/com/github/javaparser/utils/projectroot/issue2615");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();

        //        for (SourceRoot sourceRoot : sourceRoots) {
        //            sourceRoot.getRoot().normalize().endsWith("with_module_info");
        //            System.out.println(sourceRoot);
        //        }

        assertEquals(3, sourceRoots.size());
    }

    @Test
    void manualInspectionOfSystemOut_callbackOnSourceRootParse_parentOfMultipleSourceRootsWithAndWithoutModuleInfo() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve("src/test/resources/com/github/javaparser/utils/projectroot/issue2615");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        Callback cb = new Callback();

        final List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        assertThat(sourceRoots)
                .map(SourceRoot::getRoot)
                .extracting(Path::getFileName)
                .map(Path::getFileName)
                .map(Path::toString)
                .containsExactlyInAnyOrder("with_module_info_in_root", "without_module_info", "with_module_info");

        sourceRoots.forEach(sourceRoot -> {
            try {
                sourceRoot.parse("", cb);
            } catch (IOException e) {
                System.err.println("IOException: " + e);
            }
        });
    }

    @Test
    void manualInspectionOfSystemOut_callbackOnSourceRootParse_singleJavaFileInPackage() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve("src/test/resources/com/github/javaparser/utils/projectroot/issue2615/without_module_info");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        Callback cb = new Callback();

        final List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        assertThat(sourceRoots)
                .map(SourceRoot::getRoot)
                .extracting(Path::getFileName)
                .map(Path::getFileName)
                .map(Path::toString)
                .containsExactlyInAnyOrder("without_module_info");

        sourceRoots.forEach(sourceRoot -> {
            try {
                sourceRoot.parse("", cb);
            } catch (IOException e) {
                System.err.println("IOException: " + e);
            }
        });
    }

    @Test
    void manualInspectionOfSystemOut_callbackOnSourceRootParse_withModuleInfoAndJavaFileInPackage() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve("src/test/resources/com/github/javaparser/utils/projectroot/issue2615/with_module_info");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        Callback cb = new Callback();

        final List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        assertThat(sourceRoots)
                .map(SourceRoot::getRoot)
                .extracting(Path::getFileName)
                .map(Path::getFileName)
                .map(Path::toString)
                .containsExactlyInAnyOrder("with_module_info");

        sourceRoots.forEach(sourceRoot -> {
            try {
                sourceRoot.parse("", cb);
            } catch (IOException e) {
                System.err.println("IOException: " + e);
            }
        });
    }

    @Test
    void manualInspectionOfSystemOut_callbackOnSourceRootParse_withModuleInfoInRootAndJavaFileInPackage() {
        final Path root = mavenModuleRoot(SourceRootTest.class)
                .resolve(
                        "src/test/resources/com/github/javaparser/utils/projectroot/issue2615/with_module_info_in_root");
        final ProjectRoot projectRoot = parserCollectionStrategy.collect(root);

        Callback cb = new Callback();

        final List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        assertThat(sourceRoots)
                .map(SourceRoot::getRoot)
                .extracting(Path::getFileName)
                .map(Path::getFileName)
                .map(Path::toString)
                .containsExactlyInAnyOrder("with_module_info_in_root");

        sourceRoots.forEach(sourceRoot -> {
            try {
                sourceRoot.parse("", cb);
            } catch (IOException e) {
                System.err.println("IOException: " + e);
            }
        });
    }

    private static class Callback implements SourceRoot.Callback {

        @Override
        public Result process(Path localPath, Path absolutePath, ParseResult<CompilationUnit> result) {
            System.out.printf("Found %s%n", absolutePath);
            return Result.SAVE;
        }
    }
}
