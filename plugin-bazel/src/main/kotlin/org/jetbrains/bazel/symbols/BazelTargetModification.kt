package org.jetbrains.bazel.symbols

import com.intellij.model.psi.PsiSymbolService
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.refactoring.safeDelete.symbol.DeletableSymbol
import com.intellij.refactoring.safeDelete.symbol.SafeDeleteUsage
import com.intellij.refactoring.safeDelete.symbol.SafeDeleteUsageConfirmation
import com.intellij.refactoring.safeDelete.symbol.SafeDeleteUsageView
import com.intellij.usageView.UsageInfo

class BazelTargetRenameSupport : RenameableSymbol {
  override fun getRenameTarget(symbol: Symbol): PsiElement? =
    if (symbol is BazelTarget) {
      PsiSymbolService.getInstance().getNavigationTarget(symbol)
    } else {
      null
    }
}

class BazelTargetSafeDeleteProvider : DeletableSymbol {
  override fun getSafeDeleteTarget(symbol: Symbol): PsiElement? =
    if (symbol is BazelTarget) {
      PsiSymbolService.getInstance().getNavigationTarget(symbol)
    } else {
      null
    }

  override fun findUsages(symbol: Symbol, project: Project): Collection<SafeDeleteUsage> {
    // to be implemented
    return emptyList()
  }

  override fun findConflicts(
    symbol: Symbol,
    project: Project,
    usages: Collection<SafeDeleteUsage>
  ): Collection<UsageInfo> {
    // to be implemented
    return emptyList()
  }

  override fun getUsageView(
    symbol: Symbol,
    project: Project,
    usages: Collection<SafeDeleteUsage>
  ): SafeDeleteUsageView {
    // to be implemented
    return SafeDeleteUsageView(project, usages, emptyList())
  }

  override fun getUsageConfirmation(
    symbol: Symbol,
    project: Project,
    usages: Collection<SafeDeleteUsage>
  ): SafeDeleteUsageConfirmation {
    // to be implemented
    return SafeDeleteUsageConfirmation.CONFIRM
  }
}
