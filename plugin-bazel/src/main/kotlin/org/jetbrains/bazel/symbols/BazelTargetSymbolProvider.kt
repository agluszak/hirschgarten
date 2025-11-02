package org.jetbrains.bazel.symbols

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalSymbolProvider
import com.intellij.psi.PsiElement
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.languages.starlark.psi.StarlarkFile
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkCallExpression

class BazelTargetSymbolProvider : PsiExternalSymbolProvider {
  override fun getSymbols(element: PsiElement): Collection<Symbol> {
    if (element !is StarlarkCallExpression) return emptyList()

    val containingFile = element.containingFile
    if (containingFile !is StarlarkFile || !containingFile.isBuildFile()) {
      return emptyList()
    }
    val targetName = element.getArgumentList()?.getNameArgumentValue() ?: return emptyList()
    val kind = element.getKind() ?: return emptyList()
    val label = Label.create(containingFile.virtualFile.path, targetName)
    return listOf(BazelTarget(label, kind, containingFile.virtualFile))
  }

  private fun StarlarkCallExpression.getKind(): String? = this.expression.text
}
