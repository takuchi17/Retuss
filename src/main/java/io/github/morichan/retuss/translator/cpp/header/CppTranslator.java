package io.github.morichan.retuss.translator.cpp.header;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class CppTranslator {
    protected final CppToUmlTranslator cppToUmlTranslator;
    protected final UmlToCppTranslator umlToCppTranslator;

    public CppTranslator() {
        this.cppToUmlTranslator = createCodeToUmlTranslator();
        this.umlToCppTranslator = createUmlToCodeTranslator();
    }

    public List<CppHeaderClass> translateHeaderCodeToUml(String code) {
        return cppToUmlTranslator.translateHeader(code);
    }

    public Optional<String> extractClassName(String code) {
        try {
            if (code == null || code.trim().isEmpty()) {
                return Optional.empty();
            }

            // プリプロセッサディレクティブを一時的に削除
            String processedCode = code.replaceAll("#.*\\n", "\n");

            CharStream input = CharStreams.fromString(processedCode);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            // クラス名を抽出するリスナー
            class ClassNameListener extends CPP14ParserBaseListener {
                private String className = null;

                public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
                    if (ctx.classHead() != null &&
                            ctx.classHead().classHeadName() != null &&
                            ctx.classHead().classHeadName().className() != null) {
                        className = ctx.classHead().classHeadName().className().getText();
                    }
                }

                public Optional<String> getClassName() {
                    return Optional.ofNullable(className);
                }
            }

            ClassNameListener listener = new ClassNameListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, parser.translationUnit());

            return listener.getClassName();
        } catch (Exception e) {
            System.err.println("Failed to extract class name: " + e.getMessage());
            return Optional.empty();
        }
    }

    protected CppToUmlTranslator createCodeToUmlTranslator() {
        return new CppToUmlTranslator();
    }

    protected UmlToCppTranslator createUmlToCodeTranslator() {
        return new UmlToCppTranslator();
    }

    // CppTranslator.java に追加
    public String addAttribute(String existingCode, Attribute attribute) {
        return umlToCppTranslator.addAttribute(existingCode, attribute);
    }

    public String addOperation(String existingCode, Operation operation) {
        return umlToCppTranslator.addOperation(existingCode, operation);
    }

    public String addGeneralization(String existingCode, String derivedClassName, String baseClassName) {
        return umlToCppTranslator.addGeneralization(existingCode, derivedClassName, baseClassName);
    }

    public String addRealization(String code, String className, String interfaceName) {
        return umlToCppTranslator.addRealization(code, className, interfaceName);
    }

    public String removeGeneralization(String code, String baseClassName) {
        return umlToCppTranslator.removeGeneralization(code, baseClassName);
    }

    public String removeRealization(String code, String interfaceName) {
        return umlToCppTranslator.removeRealization(code, interfaceName);
    }

    public String addComposition(String existingCode, String componentName, String memberName, Visibility visibility) {
        return umlToCppTranslator.addComposition(existingCode, componentName, memberName, visibility);
    }

    public String addCompositionWithAnnotation(String existingCode, String componentName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addCompositionWithAnnotation(existingCode, componentName, memberName, visibility);
    }

    public String addAggregationWithAnnotation(String existingCode, String componentName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addAggregationWithAnnotation(existingCode, componentName, memberName, visibility);
    }

    public String addAssociation(String existingCode, String targetClassName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addAssociation(existingCode, targetClassName, memberName, visibility);
    }

    public String removeOperation(String existingCode, Operation operation) {
        return umlToCppTranslator.removeOperation(existingCode, operation);
    }

    public String removeAttribute(String existingCode, Attribute attribute) {
        return umlToCppTranslator.removeAttribute(existingCode, attribute);
    }
}