package io.github.morichan.retuss.translator.cpp.header;

import io.github.morichan.retuss.translator.cpp.header.util.CppVisibilityMapper;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UmlToCppTranslator {
    private final CppVisibilityMapper visibilityMapper;

    public UmlToCppTranslator() {
        this.visibilityMapper = new CppVisibilityMapper();
    }

    public String addAttribute(String existingCode, Attribute attribute) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            Visibility targetVisibility = attribute.getVisibility();
            int insertPosition = findInsertPositionForAttribute(lines, targetVisibility);

            if (insertPosition == -1) {
                insertPosition = findClassEndPosition(lines) - 1;
                lines.add(insertPosition, "");
                lines.add(insertPosition, visibilityMapper.toSourceCode(targetVisibility) + ":");
                insertPosition++;
            }

            String attributeDeclaration = "  " + translateAttribute(attribute) + ";";
            lines.add(insertPosition + 1, attributeDeclaration);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add attribute: " + e.getMessage());
            return existingCode;
        }
    }

    private String translateAttribute(Attribute attribute) {
        System.err.println("Attributetype :" + attribute.getType().toString());
        System.err.println("Attributename :" + attribute.getName().toString());
        // System.err.println("Attributevalue :" +
        // attribute.getDefaultValue().toString());
        StringBuilder builder = new StringBuilder();
        // 型の処理
        builder.append(attribute.getType().toString());

        // 名前の処理
        builder.append(" ").append(attribute.getName().getNameText());

        // デフォルト値の処理
        try {
            if (attribute.getDefaultValue() != null &&
                    !attribute.getDefaultValue().toString().isEmpty()) {
                builder.append(" = ").append(attribute.getDefaultValue());
            }
        } catch (IllegalStateException ignored) {
        }

        return builder.toString();
    }

    public String addOperation(String existingCode, Operation operation) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            Visibility targetVisibility = operation.getVisibility();
            int insertPosition = findInsertPositionForOperation(lines, targetVisibility);

            if (insertPosition == -1) {
                // 適切なセクションが見つからない場合、新しいセクションを作成
                insertPosition = findClassEndPosition(lines) - 1;
                lines.add(insertPosition, "");
                lines.add(insertPosition, visibilityMapper.toSourceCode(targetVisibility) + ":");
                insertPosition++;
            }

            String operationDeclaration = "  " + translateOperation(operation) + ";";
            lines.add(insertPosition + 1, operationDeclaration);

            // デバッグ出力
            System.out.println("Generated operation declaration: " + operationDeclaration);
            System.out.println("Inserting at position: " + insertPosition);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
            e.printStackTrace();
            return existingCode; // エラー時は元のコードを返す
        }
    }

    private String translateOperation(Operation operation) {
        StringBuilder builder = new StringBuilder();
        System.out.println("Translating operation: " + operation.toString());

        // 戻り値の型
        builder.append(operation.getReturnType().toString()).append(" ");

        // メソッド名
        builder.append(operation.getName().getNameText());

        // パラメータリスト
        builder.append("(");
        List<String> params = new ArrayList<>();
        try {
            if (operation.getParameters() != null && !operation.getParameters().isEmpty()) {
                for (Parameter param : operation.getParameters()) {
                    StringBuilder paramBuilder = new StringBuilder();
                    String paramType = param.getType().toString();
                    // 配列の処理
                    paramBuilder.append(paramType)
                            .append(" ")
                            .append(param.getName().getNameText());

                    params.add(paramBuilder.toString());
                }
            }
        } catch (IllegalStateException e) {
            System.out.println("No parameters for method: " + operation.getName().getNameText());
        }

        builder.append(String.join(", ", params));
        builder.append(")");

        System.out.println("Translated operation: " + builder.toString());
        return builder.toString();
    }

    public String addRealization(String existingCode, String derivedClassName, String interfaceName) {
        try {
            return addGeneralization(existingCode, derivedClassName, interfaceName);
        } catch (Exception e) {
            System.err.println("Failed to add realization: " + e.getMessage());
            return existingCode;
        }
    }

    private boolean hasInheritance(List<String> lines, String className, String baseClassName) {
        Pattern classPattern = Pattern.compile("class\\s+" + className + "\\s*:([^{]+)\\{");

        for (String line : lines) {
            Matcher matcher = classPattern.matcher(line);
            if (matcher.find()) {
                String inheritanceList = matcher.group(1);
                // public や private などの修飾子を含めてチェック
                if (inheritanceList.contains(baseClassName)) {
                    System.out.println("Found existing inheritance: " + inheritanceList.trim());
                    return true;
                }
            }
        }
        return false;
    }

    public String addGeneralization(String existingCode, String derivedClassName, String baseClassName) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));

            int classDefLine = -1;
            Pattern classDefPattern = Pattern.compile("class\\s+" + derivedClassName + "\\s*(?::|\\{)");

            for (int i = 0; i < lines.size(); i++) {
                if (classDefPattern.matcher(lines.get(i)).find()) {
                    classDefLine = i;
                    break;
                }
            }

            if (classDefLine == -1)
                return existingCode;

            // 既に継承関係が存在するかチェック
            if (hasInheritance(lines, derivedClassName, baseClassName)) {
                System.out.println("Inheritance already exists, skipping addition");
                return existingCode;
            }

            String currentLine = lines.get(classDefLine);
            if (currentLine.contains(":")) {
                int colonPos = currentLine.indexOf(":");
                String beforeColon = currentLine.substring(0, colonPos + 1);
                String afterColon = currentLine.substring(colonPos + 1);
                lines.set(classDefLine, beforeColon + " public " + baseClassName + "," + afterColon);
            } else {
                String newLine = currentLine.replace("{", ": public " + baseClassName + " {");
                lines.set(classDefLine, newLine);
            }

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add inheritance: " + e.getMessage());
            return existingCode;
        }
    }

    public String addAssociation(String existingCode, String targetClassName,
            String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            String declaration = "  " + targetClassName + "* " + memberName + ";";
            lines.add(insertPosition + 1, declaration);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add association: " + e.getMessage());
            return existingCode;
        }
    }

    public String addComposition(String existingCode, String componentName, String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            // コンポジション用のメンバ変数を追加
            String declaration = "  " + componentName + " " + memberName + ";";
            lines.add(insertPosition + 1, declaration);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add composition: " + e.getMessage());
            return existingCode;
        }
    }

    public String addCompositionWithAnnotation(String existingCode, String componentName,
            String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            // アノテーションの追加
            lines.add(insertPosition + 1, "    // @relationship composition");
            // メンバ変数の追加
            String declaration = "  " + componentName + "* " + memberName + ";";
            lines.add(insertPosition + 2, declaration);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add composition with annotation: " + e.getMessage());
            return existingCode;
        }
    }

    public String addAggregationWithAnnotation(String existingCode, String componentName,
            String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            // アノテーションの追加
            lines.add(insertPosition + 1, "    // @relationship aggregation");
            // メンバ変数の追加（shared_ptr使用）
            String declaration = "  " + componentName + "* " + memberName + ";";
            lines.add(insertPosition + 2, declaration);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add aggregation with annotation: " + e.getMessage());
            return existingCode;
        }
    }

    public String removeOperation(String existingCode, Operation operation) {
        List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
        String opName = operation.getName().getNameText();

        // 引数リストの取得（nullチェック付き）
        List<String> paramNames = new ArrayList<>();
        try {
            List<Parameter> params = operation.getParameters();
            if (params != null) {
                paramNames = params.stream()
                        .map(param -> param.getName().getNameText())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            // 引数取得に失敗した場合は空リストのまま
        }

        // メソッド名のパターンを作成
        String methodPattern = "\\b" + Pattern.quote(opName) + "\\s*\\(";
        Pattern pattern = Pattern.compile(methodPattern);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // コメントの除去
            int lineCommentIndex = line.indexOf("//");
            if (lineCommentIndex >= 0) {
                line = line.substring(0, lineCommentIndex).trim();
            }

            while (true) {
                int commentStart = line.indexOf("/*");
                if (commentStart == -1)
                    break;

                int commentEnd = line.indexOf("*/", commentStart);
                if (commentEnd == -1)
                    break;

                line = line.substring(0, commentStart).trim() + " " +
                        line.substring(commentEnd + 2).trim();
            }

            if (line.isEmpty())
                continue;

            int semicolonIndex = line.indexOf(';');
            if (semicolonIndex == -1)
                continue;

            // セミコロンまでの部分だけを取得
            String codePart = line.substring(0, semicolonIndex).trim();
            Matcher matcher = pattern.matcher(codePart);

            if (matcher.find()) {
                int start = codePart.indexOf("(") + 1;
                int end = codePart.lastIndexOf(")");

                if (start > 0 && end > start) {
                    String params = codePart.substring(start, end).trim();

                    if (paramNames.isEmpty()) {
                        if (params.isEmpty()) {
                            lines.remove(i);
                            break;
                        }
                    } else {
                        boolean allParamsFound = paramNames.stream()
                                .allMatch(params::contains);
                        if (allParamsFound) {
                            lines.remove(i);
                            break;
                        }
                    }
                } else if (start > 0 && end == start) {
                    if (paramNames.isEmpty()) {
                        lines.remove(i);
                        break;
                    }
                }
            }
        }
        return String.join("\n", lines);
    }

    public String removeAttribute(String existingCode, Attribute attribute) {
        List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
        String attrName = attribute.getName().getNameText();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // コメント除去処理（変更なし）
            int lineCommentIndex = line.indexOf("//");
            if (lineCommentIndex >= 0) {
                line = line.substring(0, lineCommentIndex).trim();
            }

            while (true) {
                int commentStart = line.indexOf("/*");
                if (commentStart == -1)
                    break;

                int commentEnd = line.indexOf("*/", commentStart);
                if (commentEnd == -1)
                    break;

                line = line.substring(0, commentStart).trim() + " " +
                        line.substring(commentEnd + 2).trim();
            }

            if (line.isEmpty())
                continue;

            if (line.endsWith(";") && !line.contains("(")) {
                // 属性名を検索
                int nameEndIndex = -1;
                if (line.contains("[")) {
                    // 配列宣言の場合、[ の前までが属性名
                    nameEndIndex = line.indexOf("[");
                } else if (line.contains("=")) {
                    // 配列宣言の場合、= の前までが属性名
                    nameEndIndex = line.indexOf("=");
                } else {
                    // 通常の変数宣言の場合、; の前までが属性名（または型も含む）
                    nameEndIndex = line.indexOf(";");
                }

                if (nameEndIndex > 0) {
                    // アスタリスクの前後に空白を挿入して分割を適切に行う
                    String declarationPart = line.substring(0, nameEndIndex);
                    declarationPart = declarationPart.replace("*", " * ").replace("&", " & ");
                    String[] parts = declarationPart.split("\\s+");

                    // 最後の部分が属性名と一致するか確認
                    if (parts[parts.length - 1].equals(attrName)) {
                        lines.remove(i);
                        break;
                    }
                }
            }
        }
        return String.join("\n", lines);
    }

    public String removeRealization(String existingCode, String interfaceName) {
        return removeGeneralization(existingCode, interfaceName);
    }

    public String removeGeneralization(String existingCode, String baseClassName) {
        System.out.println("Removing inheritance/realization for base class: " + baseClassName);
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));

            // クラス定義行を見つける
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("class ") && line.contains(": ")) {
                    System.out.println("Found class definition line: " + line);

                    // 継承リストを処理
                    int colonPos = line.indexOf(":");
                    String beforeColon = line.substring(0, colonPos);
                    String afterColon = line.substring(colonPos + 1);

                    // 継承リストをカンマで分割して処理
                    String[] inheritances = afterColon.split(",");
                    List<String> remainingInheritances = new ArrayList<>();

                    for (String inheritance : inheritances) {
                        String trimmed = inheritance.trim();
                        // public や private のスコープも含めてチェック
                        if (!trimmed.contains(baseClassName)) {
                            remainingInheritances.add(trimmed);
                        } else {
                            System.out.println("Removing inheritance: " + trimmed);
                        }
                    }

                    // 継承リストを再構築
                    if (remainingInheritances.isEmpty()) {
                        String newLine = beforeColon + " {";
                        System.out.println("Updated line (no inheritance): " + newLine);
                        lines.set(i, newLine);
                    } else {
                        String newLine = beforeColon + ": " + String.join(", ", remainingInheritances) + " {";
                        System.out.println("Updated line (with remaining inheritance): " + newLine);
                        lines.set(i, newLine);
                    }
                    break;
                }
            }

            String result = String.join("\n", lines);
            System.out.println("Inheritance removal completed");
            return result;
        } catch (Exception e) {
            System.err.println("Failed to remove inheritance: " + e.getMessage());
            e.printStackTrace();
            return existingCode;
        }
    }

    private int findInsertPositionForAttribute(List<String> lines, Visibility visibility) {
        String visibilityStr = visibilityMapper.toSourceCode(visibility) + ":";
        int classEnd = findClassEndPosition(lines);
        int lastVisibilityPos = -1;
        int currentSectionEnd = -1;
        int appropriatePos = -1;

        // クラス内を前から探索
        for (int i = 0; i < classEnd; i++) {
            String line = lines.get(i).trim();

            // 可視性セクションの開始を見つけた
            if (line.matches("^(public|protected|private):.*")) {
                if (line.equals(visibilityStr)) {
                    lastVisibilityPos = i;
                    currentSectionEnd = i;
                    continue;
                }
                // 異なる可視性セクションを見つけた
                if (lastVisibilityPos != -1) {
                    break;
                }
            }

            // 現在のセクション内の最後の要素を追跡
            if (lastVisibilityPos != -1 && !line.isEmpty() && !line.startsWith("//")) {
                currentSectionEnd = i;
            }
        }

        // 適切な挿入位置の決定
        if (lastVisibilityPos != -1) {
            // 既存のセクションの最後に追加
            appropriatePos = currentSectionEnd;
        } else {
            // 新しいセクションを作成（クラスの終わりの前）
            appropriatePos = classEnd - 1;
            lines.add(appropriatePos, "");
            lines.add(appropriatePos, visibilityStr);
            appropriatePos++;
        }

        return appropriatePos;
    }

    private int findInsertPositionForOperation(List<String> lines, Visibility visibility) {
        String visibilityStr = visibilityMapper.toSourceCode(visibility) + ":";
        int sectionStart = -1;
        int insertPos = -1;

        // 可視性セクションを探す
        for (int i = 0; i < findClassEndPosition(lines); i++) {
            String line = lines.get(i).trim();
            if (line.equals(visibilityStr)) {
                sectionStart = i;
                insertPos = i;
            } else if (sectionStart != -1) {
                // 次の可視性セクションが始まったら終了
                if (line.matches("^(public|protected|private):.*")) {
                    break;
                }
                // コメントでない行なら、それをinsertPosとする
                if (!line.isEmpty() && !line.startsWith("//")) {
                    insertPos = i;
                }
            }
        }

        // 可視性セクションが見つからない場合は作成
        if (sectionStart == -1) {
            // クラスの終わりの前に挿入
            int classEnd = findClassEndPosition(lines);
            sectionStart = classEnd;
            lines.add(sectionStart, "");
            lines.add(sectionStart, visibilityStr);
            insertPos = sectionStart;
        }

        return insertPos;
    }

    private int findClassEndPosition(List<String> lines) {
        // 後ろから検索してクラスの終了位置を見つける
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).trim().equals("};")) {
                return i;
            }
        }
        return lines.size() - 1;
    }
}