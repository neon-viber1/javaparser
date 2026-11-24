package com.github.javaparser.printer.lexicalpreservation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import org.junit.jupiter.api.Test;

class Issue3746BlockIndentationTest extends AbstractLexicalPreservingTest {

    private static String source(String indent) {
        return "class A {\n" + indent + "void foo() {\n"
                + indent + indent + "int first = 1;\n"
                + indent + indent + "int second = 2;\n"
                + indent + indent + "int third = 3;\n"
                + indent + indent + "int fourth = 4;\n"
                + indent + "}\n}";
    }

    private static String expected(String indent, String... lines) {
        StringBuilder sb = new StringBuilder("class A {\n" + indent + "void foo() {\n");
        for (String l : lines) sb.append(indent).append(indent).append(l).append("\n");
        return sb.append(indent).append("}\n}").toString();
    }

    private BlockStmt block(String indent) {
        considerCode(source(indent));
        return cu.findAll(BlockStmt.class).get(0);
    }

    private static ExpressionStmt stmt() {
        return new ExpressionStmt(new MethodCallExpr("foo"));
    }

    private void addThenRemove(String indent) {
        BlockStmt b = block(indent);
        b.addStatement(1, stmt());
        b.getStatement(0).remove();
        assertEquals(
                expected(indent, "foo();", "int second = 2;", "int third = 3;", "int fourth = 4;"),
                LexicalPreservingPrinter.print(cu));
    }

    private void removeThenAdd(String indent) {
        BlockStmt b = block(indent);
        b.getStatement(0).remove();
        b.addStatement(0, stmt());
        assertEquals(
                expected(indent, "foo();", "int second = 2;", "int third = 3;", "int fourth = 4;"),
                LexicalPreservingPrinter.print(cu));
    }

    private void addFirst(String indent) {
        BlockStmt b = block(indent);
        b.addStatement(0, stmt());
        assertEquals(
                expected(indent, "foo();", "int first = 1;", "int second = 2;", "int third = 3;", "int fourth = 4;"),
                LexicalPreservingPrinter.print(cu));
    }

    private void removeFirst(String indent) {
        BlockStmt b = block(indent);
        b.getStatement(0).remove();
        assertEquals(
                expected(indent, "int second = 2;", "int third = 3;", "int fourth = 4;"),
                LexicalPreservingPrinter.print(cu));
    }

    @Test
    void addThenRemoveTwoSpaces() {
        addThenRemove("  ");
    }

    @Test
    void addThenRemoveFourSpaces() {
        addThenRemove("    ");
    }

    @Test
    void addThenRemoveTabs() {
        addThenRemove("\t");
    }

    @Test
    void removeThenAddTwoSpaces() {
        removeThenAdd("  ");
    }

    @Test
    void removeThenAddTabs() {
        removeThenAdd("\t");
    }

    @Test
    void addFirstTwoSpaces() {
        addFirst("  ");
    }

    @Test
    void addFirstFourSpaces() {
        addFirst("    ");
    }

    @Test
    void addFirstTabs() {
        addFirst("\t");
    }

    @Test
    void removeFirstTwoSpaces() {
        removeFirst("  ");
    }

    @Test
    void removeFirstTabs() {
        removeFirst("\t");
    }
}
