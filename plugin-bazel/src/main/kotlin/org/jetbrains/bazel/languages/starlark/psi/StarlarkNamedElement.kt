package org.jetbrains.bazel.languages.starlark.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.bazel.languages.starlark.elements.StarlarkTokenTypes
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkTargetExpression
import org.jetbrains.bazel.languages.starlark.psi.functions.StarlarkFunctionDeclaration
import org.jetbrains.bazel.languages.starlark.psi.statements.StarlarkNamedLoadValue
import org.jetbrains.bazel.languages.starlark.rename.RenameUtils

abstract class StarlarkNamedElement(node: ASTNode) :
  StarlarkBaseElement(node),
  PsiNameIdentifierOwner {
  override fun getName(): String? = getNameNode()?.text

  override fun setName(name: String): PsiElement {
    val oldNode = getNameNode() ?: return this
    val newNode = RenameUtils.createNewName(project, name)
    node.replaceChild(oldNode, newNode)
    return this
  }

  override fun getNameIdentifier(): PsiElement? = getNameNode()?.psi

  override fun getTextOffset(): Int = getNameNode()?.startOffset ?: super.getTextOffset()

  override fun getUseScope(): SearchScope =
    when (this) {
      is StarlarkTargetExpression ->
        if (isTopLevel()) GlobalSearchScope.projectScope(project) else super.getUseScope()
      is StarlarkFunctionDeclaration ->
        if (isTopLevel()) GlobalSearchScope.projectScope(project) else super.getUseScope()
      is StarlarkNamedLoadValue -> GlobalSearchScope.projectScope(project)
      else -> super.getUseScope()
    }

  override fun toString(): String = "${super.toString()}('${getName()}')"

  fun getNameNode(): ASTNode? = node.findChildByType(StarlarkTokenTypes.IDENTIFIER)
}
