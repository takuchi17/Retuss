package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.translator.cpp.header.CppTranslator;

import java.util.*;

public class CppModel {
    private static final CppModel model = new CppModel();
    private final Map<String, CppFile> headerFiles = new HashMap<>();
    private final Map<String, CppFile> implFiles = new HashMap<>();
    private final CppTranslator translator;
    private final List<ModelChangeListener> listeners = new ArrayList<>();

    private CppModel() {
        this.translator = createTranslator();
    }

    private CppTranslator createTranslator() {
        return new CppTranslator();
    }

    public static CppModel getInstance() {
        return model;
    }

    public void addNewFile(String fileName) {
        String baseName = fileName.replaceAll("\\.(h|hpp|cpp)$", "");
        if (!headerFiles.containsKey(baseName)) {
            createCppFiles(baseName);
        }
    }

    public void addNewFileFromUml(String className) {
        createCppFiles(className);
    }

    public void addNewClass(String className) {
        String baseName = className;
        if (!headerFiles.containsKey(baseName)) {
            createCppClass(baseName);
        }
    }

    private void createCppClass(String baseName) {
        if (headerFiles.containsKey(baseName)) {
            System.out.println("Files already exist for baseName: " + baseName);
            return;
        }
        // ヘッダーファイル作成
        CppFile headerFile = new CppFile(baseName + ".h", true, true);
        CppFile implFile = new CppFile(baseName + ".cpp", false, true);

        // 3. スケルトンコードの設定
        String headerSkeleton = String.format(
                "#ifndef %s_H\n" +
                        "#define %s_H\n\n" +
                        "class %s {\n" +
                        "public:\n" +
                        "private:\n" +
                        "};\n\n" +
                        "#endif // %s_H",
                baseName.toUpperCase(), baseName.toUpperCase(),
                baseName,
                baseName.toUpperCase());

        String implSkeleton = String.format(
                "#include \"%s.h\"\n\n" +
                        "%s::%s() {\n" +
                        "}\n\n" +
                        "%s::~%s() {\n" +
                        "}\n",
                baseName,
                baseName, baseName,
                baseName, baseName);
        implFile.setCode(implSkeleton);
        headerFile.setCode(headerSkeleton);

        // if (headerClass.isPresent()) {
        // headerFile.updateCode(headerFile.getCode());
        // }
        headerFiles.put(baseName, headerFile);
        implFiles.put(baseName, implFile);
        notifyFileAdded(headerFile);
        notifyFileAdded(implFile);

    }

    private void createCppFiles(String baseName) {
        if (headerFiles.containsKey(baseName)) {
            System.out.println("Files already exist for baseName: " + baseName);
            return;
        }
        // ヘッダーファイル作成
        CppFile headerFile = new CppFile(baseName + ".h", true);
        CppFile implFile = new CppFile(baseName + ".cpp", false);

        // if (headerClass.isPresent()) {
        // headerFile.updateCode(headerFile.getCode());
        // }

        headerFiles.put(baseName, headerFile);
        implFiles.put(baseName, implFile);

        notifyFileAdded(headerFile);
        notifyFileAdded(implFile);

    }

    public void updateCode(CppFile file, String code) {
        String baseName = file.getBaseName();

        if (file.isHeader()) {
            updateHeaderFile(file, code, baseName);
        } else {
            updateImplementationFile(file, code, baseName);
        }
    }

    private void updateHeaderFile(CppFile headerFile, String code, String baseName) {
        String oldClassName = headerFile.getBaseName();
        headerFile.updateCode(code);
        // 新しいクラス名を取得
        String newClassName = headerFile.getHeaderClasses().get(0).getName();
        if (!newClassName.equals(oldClassName)) {
            String newName = newClassName;
            handleClassNameChange(oldClassName, newName, headerFile);
        }

        notifyFileUpdated(headerFile);
    }

    private void updateImplementationFile(CppFile implFile, String code, String baseName) {
        implFile.updateCode(code);
        notifyFileUpdated(implFile);
    }

    private void handleClassNameChange(String oldClassName, String newClassName, CppFile headerFile) {
        try {
            // 1. マップの更新前に両方のファイルの参照を保持
            CppFile implFile = implFiles.get(oldClassName);

            // 2. 実装ファイルの更新
            if (implFile != null) {

                // ファイル名の更新
                implFile.updateFileName(newClassName + ".cpp");
            }

            // 3. マップの更新
            headerFiles.remove(oldClassName);
            implFiles.remove(oldClassName);

            // 4. ヘッダーファイルの更新
            headerFile.updateFileName(newClassName + ".h");

            headerFiles.put(newClassName, headerFile);
            if (implFile != null) {
                implFiles.put(newClassName, implFile);
            }

            // 5. リスナーへの通知
            if (implFile != null) {
                notifyFileRenamed(oldClassName + ".cpp", newClassName + ".cpp");
            }
            notifyFileRenamed(oldClassName + ".h", newClassName + ".h");
        } catch (Exception e) {
            System.err.println("Error during class name change: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Optional<CppHeaderClass> findHeaderClassByName(String className) {
        return headerFiles.values().stream()
                .flatMap(file -> file.getHeaderClasses().stream())
                .filter(cls -> cls.getName().equals(className))
                .findFirst();
    }

    public void addAttribute(String className, Attribute attribute) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(className);
        if (headerClass.isEmpty())
            return;

        try {
            // まずCppHeaderClassにデータを追加
            headerClass.get().addAttribute(attribute);

            // 型からコンポジション関係を追加（プリミティブ型でない場合）
            String typeName = attribute.getType().toString();
            if (!isPrimitiveType(typeName)) {
                headerClass.get().getRelationshipManager().addComposition(
                        typeName,
                        attribute.getName().getNameText(),
                        "1",
                        attribute.getVisibility());
            }

            // 対応するファイルを見つけてコードを更新
            Optional<CppFile> headerFile = findHeaderFileByClassName(className);
            if (headerFile.isPresent()) {
                String newCode = translator.addAttribute(headerFile.get().getCode(), attribute);
                headerFile.get().setCode(newCode);
                notifyFileUpdated(headerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add attribute: " + e.getMessage());
        }
    }

    public void addOperation(String className, Operation operation) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(className);
        if (headerClass.isEmpty())
            return;

        try {
            // まずCppHeaderClassにデータを追加
            headerClass.get().addOperation(operation);

            // 戻り値の型から依存関係を追加（プリミティブ型でない場合）
            if (operation.getReturnType() != null) {
                String returnType = normalizeTypeName(operation.getReturnType().toString());
                if (!isPrimitiveType(returnType)) {
                    headerClass.get().getRelationshipManager().addRelationship(
                            new RelationshipInfo(returnType, RelationType.DEPENDENCY_USE));
                }
            }

            // パラメータの型から依存関係を追加（パラメータがある場合のみ）
            try {
                List<Parameter> parameters = operation.getParameters();
                if (parameters != null && !parameters.isEmpty()) {
                    for (Parameter param : parameters) {
                        if (param.getType() != null) {
                            String paramType = normalizeTypeName(param.getType().toString());
                            if (!isPrimitiveType(paramType)) {
                                headerClass.get().getRelationshipManager().addRelationship(
                                        new RelationshipInfo(paramType, RelationType.DEPENDENCY_PARAMETER));
                            }
                        }
                    }
                }
            } catch (IllegalStateException e) {
                // パラメータリストが初期化されていない場合は無視して続行
                System.out.println("No parameters list available");
            }

            // コードの更新
            Optional<CppFile> headerFile = findHeaderFileByClassName(className);
            if (headerFile.isPresent()) {
                String newCode = translator.addOperation(headerFile.get().getCode(), operation);
                headerFile.get().setCode(newCode);
                notifyFileUpdated(headerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void delete(String className) {
        System.out.println("DEBUG: Attempting to delete class: " + className);

        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty()) {
            System.out.println("Header file not found for class: " + className);
            return;
        }

        try {
            System.out.println("Found header file, proceeding with deletion");
            CppFile headerFile = headerFileOpt.get();

            // ヘッダーファイルのクラスリストをチェック
            System.out.println("Header classes count: " + headerFile.getHeaderClasses().size());

            if (!headerFile.getHeaderClasses().isEmpty()) {
                System.out.println("Removing class from header file");
                headerFile.removeClass(headerFile.getHeaderClasses().get(0));
            }

            headerFiles.remove(className);
            implFiles.remove(className);

            notifyFileDeleted(className);
        } catch (Exception e) {
            System.err.println("Error during class deletion:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private String normalizeTypeName(String type) {
        return type.replaceAll("[*&\\[\\]]+", "").trim();
    }

    public void delete(String className, Attribute attribute) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(className);
        if (headerClass.isEmpty())
            return;

        try {
            // CppHeaderClassから属性を削除
            headerClass.get()
                    .removeAttributeIf(attr -> attr.getName().getNameText().equals(attribute.getName().getNameText()) &&
                            attr.getType().toString().equals(attribute.getType().toString()));

            // 関連する関係を削除
            String typeName = normalizeTypeName(attribute.getType().toString());
            String attrName = attribute.getName().getNameText();
            if (!isPrimitiveType(typeName)) {
                RelationshipManager relationshipManager = headerClass.get().getRelationshipManager();
                relationshipManager.removeRelationshipsByCondition(r -> (r.getType() == RelationType.COMPOSITION ||
                        r.getType() == RelationType.AGGREGATION ||
                        r.getType() == RelationType.ASSOCIATION) &&
                        normalizeTypeName(r.getTargetClass()).equals(typeName) &&
                        r.getElement() != null &&
                        r.getElement().getName().equals(attrName));
            }

            // コードの更新
            Optional<CppFile> headerFile = findHeaderFileByClassName(className);
            if (headerFile.isPresent()) {
                String newCode = translator.removeAttribute(headerFile.get().getCode(), attribute);
                headerFile.get().setCode(newCode);
                notifyFileUpdated(headerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to delete attribute: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void delete(String className, Operation operation) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(className);
        if (headerClass.isEmpty())
            return;

        try {
            // 操作の削除（引数の型も考慮）
            headerClass.get().removeOperationIf(op -> {
                if (!op.getName().getNameText().equals(operation.getName().getNameText()) ||
                        !op.getReturnType().toString().equals(operation.getReturnType().toString())) {
                    return false;
                }

                List<Parameter> params1 = new ArrayList<>();
                List<Parameter> params2 = new ArrayList<>();
                try {
                    params1.addAll(op.getParameters());
                } catch (IllegalStateException e) {
                }
                try {
                    params2.addAll(operation.getParameters());
                } catch (IllegalStateException e) {
                }

                if (params1.size() != params2.size())
                    return false;

                for (int i = 0; i < params1.size(); i++) {
                    if (!params1.get(i).getType().toString().equals(params2.get(i).getType().toString())) {
                        return false;
                    }
                }
                return true;
            });

            RelationshipManager relationshipManager = headerClass.get().getRelationshipManager();

            // 戻り値の型の依存関係を削除
            String returnType = normalizeTypeName(operation.getReturnType().toString());
            if (!isPrimitiveType(returnType)) {
                relationshipManager.getAllRelationships().stream()
                        .filter(r -> r.getType() == RelationType.DEPENDENCY_USE &&
                                normalizeTypeName(r.getTargetClass()).equals(returnType))
                        .findFirst()
                        .ifPresent(relation -> relationshipManager.removeRelationshipsByCondition(r -> r == relation));
            }

            // パラメータからの依存関係を削除
            List<Parameter> parameters = new ArrayList<>();
            try {
                parameters.addAll(operation.getParameters());
            } catch (IllegalStateException e) {
            }

            for (Parameter param : parameters) {
                String paramType = normalizeTypeName(param.getType().toString());
                if (!isPrimitiveType(paramType)) {
                    relationshipManager.getAllRelationships().stream()
                            .filter(r -> r.getType() == RelationType.DEPENDENCY_PARAMETER &&
                                    normalizeTypeName(r.getTargetClass()).equals(paramType))
                            .findFirst()
                            .ifPresent(
                                    relation -> relationshipManager.removeRelationshipsByCondition(r -> r == relation));
                }
            }

            // コードの更新
            Optional<CppFile> headerFile = findHeaderFileByClassName(className);
            if (headerFile.isPresent()) {
                String newCode = translator.removeOperation(headerFile.get().getCode(), operation);
                headerFile.get().setCode(newCode);
                notifyFileUpdated(headerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to delete operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addGeneralization(String derivedClassName, String baseClassName) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(derivedClassName);
        if (headerClass.isEmpty() || baseClassName == null)
            return;

        try {
            // まずRelationshipManagerに追加
            headerClass.get().getRelationshipManager().addGeneralization(baseClassName);

            // 対応するファイルを見つけてコードを更新
            Optional<CppFile> derivedFile = findHeaderFileByClassName(derivedClassName);
            if (derivedFile.isPresent()) {
                String newCode = translator.addGeneralization(derivedFile.get().getCode(), derivedClassName,
                        baseClassName);
                derivedFile.get().setCode(newCode);
                notifyFileUpdated(derivedFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add inheritance: " + e.getMessage());
        }
    }

    public void addRealization(String sourceClassName, String interfaceName) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(sourceClassName);
        if (headerClass.isEmpty() || interfaceName == null)
            return;

        try {
            // RelationshipManagerに追加
            headerClass.get().getRelationshipManager().addRealization(interfaceName);

            // コードの更新
            Optional<CppFile> sourceFile = findHeaderFileByClassName(sourceClassName);
            if (sourceFile.isPresent()) {
                String newCode = translator.addRealization(
                        sourceFile.get().getCode(),
                        sourceClassName,
                        interfaceName);
                sourceFile.get().setCode(newCode);
                notifyFileUpdated(sourceFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add realization: " + e.getMessage());
        }
    }

    private boolean isPrimitiveType(String type) {
        Set<String> primitiveTypes = new HashSet<>(Arrays.asList(
                "int", "char", "bool", "float", "double", "void",
                "long", "short", "signed", "unsigned"));
        return primitiveTypes.contains(type.toLowerCase());
    }

    public void addComposition(String ownerClassName, String componentClassName, Visibility visibility) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(ownerClassName);
        if (headerClass.isEmpty())
            return;

        try {
            String memberName = componentClassName.toLowerCase();

            // RelationshipManagerに追加
            headerClass.get().getRelationshipManager().addComposition(
                    componentClassName,
                    memberName,
                    "1", // multiplicity
                    visibility);

            // メンバ変数（Attribute）の追加
            Attribute attribute = new Attribute(new Name(memberName));
            attribute.setType(new Type(componentClassName));
            attribute.setVisibility(visibility);
            headerClass.get().addAttribute(attribute);

            // コードの更新
            Optional<CppFile> ownerFile = findHeaderFileByClassName(ownerClassName);
            if (ownerFile.isPresent()) {
                String newCode = translator.addComposition(
                        ownerFile.get().getCode(),
                        componentClassName,
                        memberName,
                        visibility);
                ownerFile.get().setCode(newCode);
                notifyFileUpdated(ownerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add composition: " + e.getMessage());
        }
    }

    public void addCompositionWithAnnotation(String ownerClassName, String componentClassName, Visibility visibility) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(ownerClassName);
        if (headerClass.isEmpty())
            return;

        try {
            String memberName = componentClassName.toLowerCase() + "CompositionPtr";

            // RelationshipManagerに追加
            headerClass.get().getRelationshipManager().addComposition(
                    componentClassName,
                    memberName,
                    "0..1",
                    visibility);

            // メンバ変数の追加
            Attribute attribute = new Attribute(new Name(memberName));
            attribute.setType(new Type(componentClassName + "*"));
            attribute.setVisibility(visibility);
            headerClass.get().addAttribute(attribute);

            // コードの更新
            Optional<CppFile> ownerFile = findHeaderFileByClassName(ownerClassName);
            if (ownerFile.isPresent()) {
                String newCode = translator.addCompositionWithAnnotation(
                        ownerFile.get().getCode(),
                        componentClassName,
                        memberName,
                        visibility);
                ownerFile.get().setCode(newCode);
                notifyFileUpdated(ownerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add annotated composition: " + e.getMessage());
        }
    }

    public void addAggregationWithAnnotation(String ownerClassName, String componentClassName, Visibility visibility) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(ownerClassName);
        if (headerClass.isEmpty())
            return;

        try {
            String memberName = componentClassName.toLowerCase() + "AggregationPtr";

            // RelationshipManagerに追加
            headerClass.get().getRelationshipManager().addAggregation(
                    componentClassName,
                    memberName,
                    "0..1",
                    visibility);

            // メンバ変数の追加
            Attribute attribute = new Attribute(new Name(memberName));
            attribute.setType(new Type(componentClassName + "*"));
            attribute.setVisibility(visibility);
            headerClass.get().addAttribute(attribute);

            // コードの更新
            Optional<CppFile> ownerFile = findHeaderFileByClassName(ownerClassName);
            if (ownerFile.isPresent()) {
                String newCode = translator.addAggregationWithAnnotation(
                        ownerFile.get().getCode(),
                        componentClassName,
                        memberName,
                        visibility);
                ownerFile.get().setCode(newCode);
                notifyFileUpdated(ownerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add annotated aggregation: " + e.getMessage());
        }
    }

    public void addAssociation(String sourceClassName, String targetClassName, Visibility visibility) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(sourceClassName);
        if (headerClass.isEmpty())
            return;

        try {
            String memberName = targetClassName.toLowerCase() + "AssociationPtr";

            // RelationshipManagerに追加
            headerClass.get().getRelationshipManager().addAssociation(
                    targetClassName,
                    memberName,
                    "0..1",
                    visibility);

            // メンバ変数の追加
            Attribute attribute = new Attribute(new Name(memberName));
            attribute.setType(new Type(targetClassName + "*")); // ポインタとして追加
            attribute.setVisibility(visibility);
            headerClass.get().addAttribute(attribute);

            // コードの更新処理
            Optional<CppFile> sourceFile = findHeaderFileByClassName(sourceClassName);
            if (sourceFile.isPresent()) {
                String newCode = translator.addAssociation(
                        sourceFile.get().getCode(),
                        targetClassName,
                        memberName,
                        visibility);
                sourceFile.get().setCode(newCode);
                notifyFileUpdated(sourceFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to add association: " + e.getMessage());
        }
    }

    public void removeGeneralization(String className, String baseClassName) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(className);
        if (headerClass.isEmpty())
            return;

        try {
            // 継承関係を削除
            RelationshipManager relationshipManager = headerClass.get().getRelationshipManager();
            relationshipManager.removeRelationshipsByCondition(r -> r.getType() == RelationType.GENERALIZATION &&
                    r.getTargetClass().equals(baseClassName));

            // コードの更新
            Optional<CppFile> headerFile = findHeaderFileByClassName(className);
            if (headerFile.isPresent()) {
                String newCode = translator.removeGeneralization(headerFile.get().getCode(), baseClassName);
                headerFile.get().setCode(newCode);
                notifyFileUpdated(headerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to remove inheritance: " + e.getMessage());
        }
    }

    public void removeRealization(String className, String interfaceName) {
        Optional<CppHeaderClass> headerClass = findHeaderClassByName(className);
        if (headerClass.isEmpty())
            return;

        try {
            // 実現関係を削除
            RelationshipManager relationshipManager = headerClass.get().getRelationshipManager();
            relationshipManager.removeRelationshipsByCondition(r -> r.getType() == RelationType.REALIZATION &&
                    r.getTargetClass().equals(interfaceName));

            // コードの更新
            Optional<CppFile> headerFile = findHeaderFileByClassName(className);
            if (headerFile.isPresent()) {
                String newCode = translator.removeRealization(headerFile.get().getCode(), interfaceName);
                headerFile.get().setCode(newCode);
                notifyFileUpdated(headerFile.get());
            }
        } catch (Exception e) {
            System.err.println("Failed to remove realization: " + e.getMessage());
        }
    }

    public Map<String, CppFile> getHeaderFiles() {
        return Collections.unmodifiableMap(headerFiles);
    }

    public Map<String, CppFile> getImplFiles() {
        return Collections.unmodifiableMap(implFiles);
    }

    public List<CppHeaderClass> getHeaderClasses() {
        List<CppHeaderClass> allClasses = new ArrayList<>();
        for (CppFile headerFile : headerFiles.values()) {
            allClasses.addAll(headerFile.getHeaderClasses());
        }
        System.out.println("CppModel classes: " + allClasses.size());
        for (CppHeaderClass cls : allClasses) {
            CppHeaderClass cppHeaderClass = cls;
            System.out.println("  Class: " + cppHeaderClass.getName());
            System.out.println("  Attributes: " + cls.getAttributeList());
            System.out.println("  Operations: " + cls.getOperationList());
            System.out.println("  Relations: ");
            for (RelationshipInfo relation : cppHeaderClass.getRelationships()) {
                System.out.println("    " + relation.getTargetClass() +
                        " (" + relation.getType() + ")");
                if (relation.getElement() != null)
                    System.out.println("      - " + relation.getElement().getName() +
                            " [" + relation.getElement().getMultiplicity() + "]");
            }
        }
        return Collections.unmodifiableList(allClasses);
    }

    public interface ModelChangeListener {
        void onModelChanged();

        void onFileAdded(CppFile file);

        void onClassAdded(CppFile file);

        void onFileUpdated(CppFile file);

        void onFileDeleted(String className);

        void onFileRenamed(String oldName, String newName);
    }

    public void addChangeListener(ModelChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(ModelChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyFileAdded(CppFile file) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileAdded(file);
            } catch (Exception e) {
                System.err.println("Error notifying file addition: " + e.getMessage());
            }
        }
    }

    private void notifyFileUpdated(CppFile file) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileUpdated(file);
            } catch (Exception e) {
                System.err.println("Error notifying file update: " + e.getMessage());
            }
        }
    }

    private void notifyFileDeleted(String className) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileDeleted(className);
            } catch (Exception e) {
                System.err.println("Error notifying file deletion: " + e.getMessage());
            }
        }
    }

    private void notifyFileRenamed(String oldName, String newName) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileRenamed(oldName, newName);
            } catch (Exception e) {
                System.err.println("Error notifying file rename: " + e.getMessage());
            }
        }
    }

    public void updateCodeFile(CppFile changedCodeFile, String code) {
        try {
            CppFile file = changedCodeFile;
            System.out.println("DEBUG: Updating code for file: " + file.getFileName());

            updateCode(file, code);
        } catch (Exception e) {
            System.err.println("Failed to update code: " + e.getMessage());
        }
    }

    public Optional<CppHeaderClass> findClass(String className) {
        String baseName = getBaseName(className);
        CppFile headerFile = headerFiles.get(baseName);
        if (headerFile != null) {
            List<CppHeaderClass> classes = headerFile.getHeaderClasses();
            if (!classes.isEmpty()) {
                return Optional.of(classes.get(0));
            }
        }
        return Optional.empty();
    }

    public Optional<CppFile> findHeaderFileByClassName(String className) {
        String baseName = getBaseName(className);
        return Optional.ofNullable(headerFiles.get(baseName));
    }

    private String getBaseName(String fileName) {
        return fileName.replaceAll("\\.(h|hpp|cpp)$", "");
    }

    /**
     * 実装ファイルを取得する
     *
     * @param className 拡張子を除いたファイル名
     * @return 実装ファイル、存在しない場合はnull
     */
    public CppFile findImplFile(String className) {
        return implFiles.get(className);
    }

    /**
     * ヘッダーファイルを取得する
     *
     * @param baseName 拡張子を除いたファイル名
     * @return ヘッダーファイル、存在しない場合はnull
     */
    public CppFile findHeaderFile(String baseName) {
        return headerFiles.get(baseName);
    }

}