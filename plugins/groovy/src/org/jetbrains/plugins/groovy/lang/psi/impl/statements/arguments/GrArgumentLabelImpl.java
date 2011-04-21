/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrArgumentLabelImpl extends GroovyPsiElementImpl implements GrArgumentLabel {

  public GrArgumentLabelImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitArgumentLabel(this);
  }

  public String toString() {
    return "Argument label";
  }

  @Nullable
  private PsiPolyVariantReference getReferenceFromNamedArgumentProviders() {
    PsiElement namedArgument = getParent();
    if (!(namedArgument instanceof GrNamedArgument)) return null;

    PsiElement nameElement = getNameElement();
    if (!(nameElement instanceof LeafPsiElement)) return null;

    IElementType elementType = ((LeafPsiElement)nameElement).getElementType();
    if (elementType != GroovyTokenTypes.mIDENT && !CommonClassNames.JAVA_LANG_STRING.equals(TypesUtil.getBoxedTypeName(elementType))) {
      return null;
    }

    GrCall call = PsiUtil.getMethodByNamedParameter((GrNamedArgument)namedArgument);
    if (call == null) return null;

    String labelName = getName();

    GroovyNamedArgumentProvider.ArgumentDescriptor descr =
      GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, labelName, false).get(labelName);

    if (descr != null) {
      PsiPolyVariantReference res = descr.createReference(this);
      if (res != null) {
        return res;
      }
    }

    return null;
  }

  @NotNull
  private PsiPolyVariantReference getRealReference() {
    PsiReference[] otherReferences = ReferenceProvidersRegistry.getReferencesFromProviders(this, GrArgumentLabel.class);
    PsiPolyVariantReference reference = getReferenceFromNamedArgumentProviders();

    if (otherReferences.length == 0) {
      if (reference != null) {
        return reference;
      }
      else {
        return new PsiPolyVariantReferenceBase<PsiElement>(this) {

          @NotNull
          @Override
          public Object[] getVariants() {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
          }

          @NotNull
          @Override
          public ResolveResult[] multiResolve(boolean incompleteCode) {
            return ResolveResult.EMPTY_ARRAY;
          }
        };
      }
    }
    else {
      if (reference != null) {
        PsiReference[] refs = new PsiReference[otherReferences.length + 1];
        refs[0] = reference;
        //noinspection ManualArrayCopy
        for (int i = 0; i < otherReferences.length; i++) {
          refs[i + 1] = otherReferences[i];
        }

        otherReferences = refs;
      }

      return new PsiMultiReference(otherReferences, this);
    }
  }

  public PsiReference getReference() {
    return this;
  }

  @Nullable
  public String getName() {
    final PsiElement element = getNameElement();
    if (element instanceof GrLiteral) {
      final Object value = ((GrLiteral)element).getValue();
      if (value instanceof String) {
        return (String)value;
      }

      if (value instanceof Number) {
        return value.toString();
      }
    }
    if (element instanceof GrExpression) {
      final Object value = JavaPsiFacade.getInstance(getProject()).getConstantEvaluationHelper().computeConstantExpression(element);
      if (value instanceof String) {
        return (String)value;
      }
      if (value instanceof Number) {
        return value.toString();
      }
    }

    final IElementType elemType = element.getNode().getElementType();
    if (GroovyTokenTypes.mIDENT == elemType || TokenSets.KEYWORDS.contains(elemType)) {
      return element.getText();
    }

    if (CommonClassNames.JAVA_LANG_STRING.equals(TypesUtil.getBoxedTypeName(elemType))) {
      return GrStringUtil.removeQuotes(element.getText());
    }

    return null;
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Nullable
  public PsiElement resolve() {
    return getRealReference().resolve();
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return getRealReference().multiResolve(incompleteCode);
  }

  @NotNull
  public String getCanonicalText() {
    PsiElement resolved = resolve();
    if (resolved instanceof PsiMember && resolved instanceof PsiNamedElement) {
      PsiClass clazz = ((PsiMember) resolved).getContainingClass();
      if (clazz != null) {
        String qName = clazz.getQualifiedName();
        if (qName != null) {
          return qName + "." + ((PsiNamedElement) resolved).getName();
        }
      }
    }

    return getText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getRealReference().handleElementRename(newElementName);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return getRealReference().bindToElement(element);
  }

  public boolean isReferenceTo(PsiElement element) {
    return getRealReference().isReferenceTo(element);
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  @NotNull
  public PsiElement getNameElement() {
    final PsiElement element = getFirstChild();
    assert element != null;
    return element;
  }

  @Override
  public GrExpression getExpression() {
    final PsiElement nameElement = getNameElement();
    if (nameElement instanceof GrParenthesizedExpression) return ((GrParenthesizedExpression)nameElement).getOperand();
    return null;
  }

  @Nullable
  public PsiType getExpectedArgumentType() { // TODO use GroovyNamedArgumentProvider to determinate expected argument type.
    return null;
  }

  public PsiType getLabelType() {
    PsiElement el = getNameElement();
    if (el instanceof GrParenthesizedExpression) {
      return ((GrParenthesizedExpression)el).getType();
    }

    final ASTNode node = el.getNode();
    if (node == null) {
      return null;
    }

    PsiType nodeType = TypesUtil.getPsiType(el, node.getElementType());
    if (nodeType != null) {
      return nodeType;
    }
    return TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, this);
  }

  public GrNamedArgument getNamedArgument() {
    final PsiElement parent = getParent();
    assert parent instanceof GrNamedArgument;
    return (GrNamedArgument)parent;
  }

  @Override
  public PsiElement setName(@NotNull String newName) {
    PsiImplUtil.setName(newName, getNameElement());
    return this;
  }
}
