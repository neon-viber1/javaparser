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
package com.github.javaparser.metamodel;

import static com.github.javaparser.utils.Utils.decapitalize;

import com.github.javaparser.ast.AllFieldsConstructor;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Meta-data about all classes in the AST. These are all Nodes, except NodeList.
 */
public abstract class BaseNodeMetaModel {

    private final Optional<BaseNodeMetaModel> superNodeMetaModel;

    private final List<PropertyMetaModel> declaredPropertyMetaModels = new ArrayList<>();

    private final List<PropertyMetaModel> derivedPropertyMetaModels = new ArrayList<>();

    private final List<PropertyMetaModel> constructorParameters = new ArrayList<>();

    private volatile List<PropertyMetaModel> allPropertyMetaModels;

    private final Class<? extends Node> type;

    private final String name;

    private final String packageName;

    private final boolean isAbstract;

    private final boolean hasWildcard;

    public BaseNodeMetaModel(
            Optional<BaseNodeMetaModel> superNodeMetaModel,
            Class<? extends Node> type,
            String name,
            String packageName,
            boolean isAbstract,
            boolean hasWildcard) {
        this.superNodeMetaModel = superNodeMetaModel;
        this.type = type;
        this.name = name;
        this.packageName = packageName;
        this.isAbstract = isAbstract;
        this.hasWildcard = hasWildcard;
    }

    /**
     * @return is this the meta model for this node class?
     */
    public boolean is(Class<? extends Node> c) {
        return type.equals(c);
    }

    /**
     * @return package name + class name
     */
    public String getQualifiedClassName() {
        return packageName + "." + name;
    }

    /**
     * @return the meta model for the node that this node extends. Note that this is to be used to find properties
     * defined in superclasses of a Node.
     */
    public Optional<BaseNodeMetaModel> getSuperNodeMetaModel() {
        return superNodeMetaModel;
    }

    /**
     * @return a list of all properties declared directly in this node (not its parent nodes.) These are also available
     * as fields. Note that a subclass may not support a property declared by its parent; see
     * {@link #getAllPropertyMetaModels()}.
     */
    public List<PropertyMetaModel> getDeclaredPropertyMetaModels() {
        return declaredPropertyMetaModels;
    }

    public List<PropertyMetaModel> getDerivedPropertyMetaModels() {
        return derivedPropertyMetaModels;
    }

    /**
     * @return a list of all properties that describe the parameters to the all-fields (but not "range" and "comment")
     * constructor, in the order of appearance in the constructor parameter list.
     */
    public List<PropertyMetaModel> getConstructorParameters() {
        return constructorParameters;
    }

    /**
     * @return an unmodifiable list of all properties that are valid for this node, including those of its parents.
     * For concrete nodes this is the constructor parameters plus the comment property, so a property that is declared
     * by a parent class but not supported by this node (such as "annotations" for a void type) is not included.
     * Note that this can differ from the union of {@link #getDeclaredPropertyMetaModels()} along the inheritance chain.
     * The list is computed on first use and cached.
     */
    public List<PropertyMetaModel> getAllPropertyMetaModels() {
        List<PropertyMetaModel> result = allPropertyMetaModels;
        if (result == null) {
            result = Collections.unmodifiableList(computeAllPropertyMetaModels());
            allPropertyMetaModels = result;
        }
        return result;
    }

    /**
     * @return a new list of the properties declared by this node and by all of its parents, whether or not the
     * concrete node supports them. This is what the code generators use, since the corresponding getters and setters
     * exist on the node class.
     */
    public List<PropertyMetaModel> getDeclaredPropertyMetaModelsInHierarchy() {
        List<PropertyMetaModel> all = new ArrayList<>(getDeclaredPropertyMetaModels());
        BaseNodeMetaModel walkNode = this;
        while (walkNode.getSuperNodeMetaModel().isPresent()) {
            walkNode = walkNode.getSuperNodeMetaModel().get();
            all.addAll(walkNode.getDeclaredPropertyMetaModels());
        }
        return all;
    }

    private List<PropertyMetaModel> computeAllPropertyMetaModels() {
        List<PropertyMetaModel> all = getDeclaredPropertyMetaModelsInHierarchy();
        if (!isAbstract) {
            all.removeIf(p -> !constructorParameters.contains(p) && !"comment".equals(p.getName()));
        }
        return all;
    }

    public boolean isInstanceOfMetaModel(BaseNodeMetaModel baseMetaModel) {
        if (this == baseMetaModel) {
            return true;
        }
        if (isRootNode()) {
            return false;
        }
        return getSuperNodeMetaModel().get().isInstanceOfMetaModel(baseMetaModel);
    }

    /**
     * @return the class for this AST node type.
     */
    public Class<? extends Node> getType() {
        return type;
    }

    /**
     * @return the package containing this AST node class.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * @return whether this AST node is abstract.
     */
    public boolean isAbstract() {
        return isAbstract;
    }

    /**
     * @return whether this AST node has a &lt;?&gt; at the end of its type.
     */
    public boolean hasWildcard() {
        return hasWildcard;
    }

    /**
     * @return whether this AST node is the root node, meaning that it is the meta model for "Node": "NodeMetaModel".
     */
    public boolean isRootNode() {
        return !superNodeMetaModel.isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseNodeMetaModel classMetaModel = (BaseNodeMetaModel) o;
        if (!type.equals(classMetaModel.type)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * @return the type name, with generics.
     */
    public String getTypeNameGenerified() {
        if (hasWildcard) {
            return getTypeName() + "<?>";
        }
        return getTypeName();
    }

    /**
     * @return the raw type name, so nothing but the name.
     */
    public String getTypeName() {
        return type.getSimpleName();
    }

    /**
     * The name of the field in JavaParserMetaModel for this node meta model.
     */
    public String getMetaModelFieldName() {
        return decapitalize(getClass().getSimpleName());
    }

    /**
     * Creates a new node of this type.
     *
     * @param parameters a map of propertyName -&gt; value.
     * This should at least contain a pair for every required property for this node.
     */
    public Node construct(Map<String, Object> parameters) {
        for (Constructor<?> constructor : getType().getConstructors()) {
            if (constructor.getAnnotation(AllFieldsConstructor.class) != null) {
                try {
                    Object[] paramArray = new Object[constructor.getParameterCount()];
                    int i = 0;
                    for (PropertyMetaModel constructorParameter : getConstructorParameters()) {
                        paramArray[i] = parameters.get(constructorParameter.getName());
                        if (paramArray[i] == null && constructorParameter.isRequired()) {
                            if (constructorParameter.isNodeList()) {
                                paramArray[i] = new NodeList<>();
                            }
                            // We could have more defaults here.
                        }
                        i++;
                    }
                    return (Node) constructor.newInstance(paramArray);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalStateException();
    }
}
