package org.jetbrains.bazel.languages.bazelrc.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.bazel.languages.bazelrc.BazelrcLanguage
import org.jetbrains.bazel.languages.bazelrc.elements.BazelrcElementTypes
import org.jetbrains.bazel.languages.bazelrc.elements.BazelrcTokenSets
import org.jetbrains.bazel.languages.bazelrc.lexer.BazelrcHighlightingLexer
import org.jetbrains.bazel.languages.bazelrc.psi.BazelrcFile

class BazelrcParserDefinition : ParserDefinition {
  private val file = IFileElementType(BazelrcLanguage)

  override fun createLexer(project: Project?): Lexer = BazelrcHighlightingLexer()

  override fun createParser(project: Project?): PsiParser =
    PsiParser { root, builder ->
      Parsing(root, builder).parseFile()
    }

  override fun getFileNodeType(): IFileElementType = file

  override fun getWhitespaceTokens(): TokenSet = BazelrcTokenSets.WHITE_SPACES

  override fun getCommentTokens(): TokenSet = BazelrcTokenSets.COMMENTS

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode): PsiElement = BazelrcElementTypes.createElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile = BazelrcFile(viewProvider)
}
