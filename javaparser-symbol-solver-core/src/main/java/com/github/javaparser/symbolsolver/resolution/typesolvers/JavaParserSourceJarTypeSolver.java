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

import static com.github.javaparser.ParseStart.COMPILATION_UNIT;
import static com.github.javaparser.ParserConfiguration.LanguageLevel.BLEEDING_EDGE;
import static com.github.javaparser.Providers.provider;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.resolution.Navigator;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Defines a source JAR (or ZIP archive of sources) that should be used for solving symbols.
 * The archive entries must follow the package layout, e.g. {@code a/b/C.java} declares type {@code a.b.C}.
 * <p>
 * Only types declared in a file named after the type are found; {@code module-info.java},
 * {@code package-info.java} and {@code META-INF} entries (including multi-release variants) are ignored.
 * The archive stays open until {@link #close()} is called.
 * <p>
 * To combine all of the source/class and directory/JAR dependency forms, put the matching solvers in a
 * {@link CombinedTypeSolver}: {@link JavaParserTypeSolver} (source directory), this class (source JAR),
 * {@link JarTypeSolver} (class JAR) and {@link ReflectionTypeSolver} (JDK classes).
 */
public class JavaParserSourceJarTypeSolver implements TypeSolver, Closeable {

    private final Path jarPath;
    private final ZipFile zipFile;
    private final JavaParser javaParser;
    private final Map<String, String> entriesByTypeName = new HashMap<>();
    private final Map<String, Optional<CompilationUnit>> parsedEntries = new ConcurrentHashMap<>();
    private final Map<String, SymbolReference<ResolvedReferenceTypeDeclaration>> foundTypes = new ConcurrentHashMap<>();

    private TypeSolver parent;

    public JavaParserSourceJarTypeSolver(File jarFile) {
        this(jarFile.toPath());
    }

    public JavaParserSourceJarTypeSolver(String jarFile) {
        this(new File(jarFile));
    }

    public JavaParserSourceJarTypeSolver(Path jarFile) {
        this(jarFile, new ParserConfiguration().setLanguageLevel(BLEEDING_EDGE));
    }

    public JavaParserSourceJarTypeSolver(File jarFile, ParserConfiguration parserConfiguration) {
        this(jarFile.toPath(), parserConfiguration);
    }

    public JavaParserSourceJarTypeSolver(String jarFile, ParserConfiguration parserConfiguration) {
        this(new File(jarFile), parserConfiguration);
    }

    /**
     * @param jarFile the source JAR (or ZIP) file.
     * @param parserConfiguration the configuration used when parsing the sources.
     * @throws IllegalArgumentException if the path does not exist or is not a readable archive.
     */
    public JavaParserSourceJarTypeSolver(Path jarFile, ParserConfiguration parserConfiguration) {
        Objects.requireNonNull(jarFile, "The jarFile can't be null.");
        Objects.requireNonNull(parserConfiguration, "The parserConfiguration can't be null.");
        if (!Files.exists(jarFile) || !Files.isRegularFile(jarFile)) {
            throw new IllegalArgumentException("Source JAR does not exist or is not a file: " + jarFile);
        }
        this.jarPath = jarFile;
        this.javaParser = new JavaParser(parserConfiguration);
        try {
            this.zipFile = new ZipFile(jarFile.toFile());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to open source JAR: " + jarFile, e);
        }
        indexEntries();
    }

    private void indexEntries() {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entry.isDirectory() || !entryName.endsWith(".java") || entryName.startsWith("META-INF/")) {
                continue;
            }
            String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
            if (fileName.equals("module-info.java") || fileName.equals("package-info.java")) {
                continue;
            }
            String typeName = entryName
                    .substring(0, entryName.length() - ".java".length())
                    .replace('/', '.');
            entriesByTypeName.putIfAbsent(typeName, entryName);
        }
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    @Override
    public String toString() {
        return "JavaParserSourceJarTypeSolver{" + "jarPath=" + jarPath + ", parent=" + parent + '}';
    }

    @Override
    public TypeSolver getParent() {
        return parent;
    }

    @Override
    public void setParent(TypeSolver parent) {
        Objects.requireNonNull(parent);
        if (this.parent != null) {
            throw new IllegalStateException("This TypeSolver already has a parent.");
        }
        if (parent == this) {
            throw new IllegalStateException("The parent of this TypeSolver cannot be itself.");
        }
        this.parent = parent;
    }

    private Optional<CompilationUnit> parse(String entryName) {
        Optional<CompilationUnit> cached = parsedEntries.get(entryName);
        if (cached != null) {
            return cached;
        }
        Optional<CompilationUnit> result = parseUncached(entryName);
        parsedEntries.put(entryName, result);
        return result;
    }

    private Optional<CompilationUnit> parseUncached(String entryName) {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            return Optional.empty();
        }
        // JavaParser only allows one parse at a time.
        synchronized (javaParser) {
            try (InputStream in = zipFile.getInputStream(entry)) {
                ParseResult<CompilationUnit> parseResult = javaParser.parse(
                        COMPILATION_UNIT,
                        provider(in, javaParser.getParserConfiguration().getCharacterEncoding()));
                parseResult.ifSuccessful(cu -> {
                    if (!cu.containsData(Node.SYMBOL_RESOLVER_KEY)) {
                        new JavaSymbolSolver(getRoot()).inject(cu);
                    }
                });
                return parseResult.getResult();
            } catch (IOException e) {
                throw new UncheckedIOException("Issue while parsing " + entryName + " in " + jarPath, e);
            }
        }
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
        SymbolReference<ResolvedReferenceTypeDeclaration> cached = foundTypes.get(name);
        if (cached != null) {
            return cached;
        }
        SymbolReference<ResolvedReferenceTypeDeclaration> result = tryToSolveTypeUncached(name);
        foundTypes.put(name, result);
        return result;
    }

    private SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveTypeUncached(String name) {
        String[] nameElements = name.split("\\.");
        for (int i = nameElements.length; i > 0; i--) {
            String topLevelName = String.join(".", java.util.Arrays.copyOfRange(nameElements, 0, i));
            String entryName = entriesByTypeName.get(topLevelName);
            if (entryName == null) {
                continue;
            }
            String typeName = String.join(".", java.util.Arrays.copyOfRange(nameElements, i - 1, nameElements.length));
            Optional<CompilationUnit> cu = parse(entryName);
            if (cu.isPresent()) {
                Optional<TypeDeclaration<?>> declaration = Navigator.findType(cu.get(), typeName);
                if (declaration.isPresent()) {
                    return SymbolReference.solved(JavaParserFacade.get(this).getTypeDeclaration(declaration.get()));
                }
            }
        }
        return SymbolReference.unsolved();
    }

    /**
     * Module-qualified lookups are not supported by this solver; the result is always unsolved.
     */
    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveTypeInModule(
            String qualifiedModuleName, String simpleTypeName) {
        return SymbolReference.unsolved();
    }
}
