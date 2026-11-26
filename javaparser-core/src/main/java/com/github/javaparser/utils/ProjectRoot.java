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

import com.github.javaparser.ParserConfiguration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groups a project's Java source roots so that its source code can be analysed or manipulated as a whole.
 * A project directory, available through {@link #getRoot()}, can contain zero or more {@link SourceRoot}s,
 * for example separate roots for production code, test code, or individual modules. Each source root is the
 * base directory of a Java package hierarchy, not an individual source file or parsed syntax tree.
 *
 * <p>Constructing a {@code ProjectRoot} only records the project directory and parser configuration; it does not
 * discover or parse source files. Use {@link #addSourceRoot(Path)} to register known source directories, or use a
 * {@link CollectionStrategy} to discover them. For example, {@link ParserCollectionStrategy} parses files to identify
 * source roots without populating the returned source roots' compilation-unit caches. Explicitly parse each root,
 * then traverse or modify its {@link com.github.javaparser.ast.CompilationUnit}s to work across the project.
 *
 * <p>For example, given an existing {@code Path projectDirectory}, the following collects source roots, parses
 * their files, and prints the names of all class and interface declarations. It uses {@link ParserCollectionStrategy},
 * which infers source roots from Java files and their package declarations rather than build files. The files must
 * use a package layout and Java syntax supported by that strategy and the chosen parser configuration. Here Java 9
 * is selected, including support for {@code module-info.java}; select a suitable language level for your project.
 * The enclosing method must handle or declare {@link java.io.IOException} from parsing.
 *
 * <pre>{@code
 * ParserConfiguration configuration = new ParserConfiguration()
 *         .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_9);
 * ProjectRoot project = new ParserCollectionStrategy(configuration).collect(projectDirectory);
 * for (SourceRoot sourceRoot : project.getSourceRoots()) {
 *     sourceRoot.tryToParse();
 *     for (com.github.javaparser.ast.CompilationUnit unit : sourceRoot.getCompilationUnits()) {
 *         unit.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
 *                 .forEach(declaration -> System.out.println(declaration.getNameAsString()));
 *     }
 * }
 * }</pre>
 *
 * <p>{@link SourceRoot#tryToParse()} caches parse results, including failures; inspect its returned results for
 * problems. {@link SourceRoot#getCompilationUnits()} exposes only successfully parsed units (or units added
 * manually). Collection is not a file watcher, and cached parse results are not automatically refreshed when files
 * change. Use separate project and source-root instances for independent versions of a project's sources.
 *
 * <p>Parsing and AST traversal do not require symbol resolution. Resolving references additionally requires a
 * configured {@linkplain ParserConfiguration#setSymbolResolver(com.github.javaparser.resolution.SymbolResolver)
 * symbol resolver} and the appropriate source and dependency type solvers, typically provided by the symbol-solver
 * module. Constructing a {@code ProjectRoot} does not register sources with a global symbol solver.
 */
public class ProjectRoot {

    private final Path root;

    private final Map<Path, SourceRoot> cache = new ConcurrentHashMap<>();

    private final ParserConfiguration parserConfiguration;

    public ProjectRoot(Path root) {
        this(root, new ParserConfiguration());
    }

    public ProjectRoot(Path root, ParserConfiguration parserConfiguration) {
        this.root = root;
        this.parserConfiguration = parserConfiguration;
    }

    public Optional<SourceRoot> getSourceRoot(Path sourceRoot) {
        return Optional.ofNullable(cache.get(sourceRoot));
    }

    public List<SourceRoot> getSourceRoots() {
        return new ArrayList<>(cache.values());
    }

    public void addSourceRoot(Path path) {
        cache.put(path, new SourceRoot(path).setParserConfiguration(parserConfiguration));
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public String toString() {
        return "ProjectRoot at " + root + " with " + cache.values().toString();
    }
}
