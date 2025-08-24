package org.jetbrains.bazel.symbols

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.bazel.languages.starlark.StarlarkFileType
import org.jetbrains.bazel.label.*
import java.io.DataInput
import java.io.DataOutput

/**
 * Key descriptor for serializing Label objects in the index
 */
class BazelLabelKeyDescriptor : KeyDescriptor<Label> {
  override fun save(out: DataOutput, value: Label) {
    out.writeUTF(value.toString())
  }
  
  override fun read(`in`: DataInput): Label {
    val labelString = `in`.readUTF()
    return Label.parse(labelString)
  }
  
  override fun getHashCode(value: Label): Int = value.hashCode()
  
  override fun isEqual(val1: Label?, val2: Label?): Boolean = val1 == val2
}

/**
 * Creates a proper label for a target during indexing
 */
private fun createLabelForTarget(target: BazelTargetInfo, file: com.intellij.openapi.vfs.VirtualFile): Label {
  // For now, assume main workspace - proper repo context detection would go here
  val packageSegments = if (target.packagePath.isEmpty()) emptyList() else target.packagePath.split("/")
  
  return ResolvedLabel(
    repo = Main,
    packagePath = Package(packageSegments),
    target = SingleTarget(target.targetName)
  )
}

/**
 * Get package path from file location
 */
private fun getPackagePathFromFile(file: com.intellij.openapi.vfs.VirtualFile): String {
  val filePath = file.path
  
  // Find workspace root
  var currentDir = file.parent
  while (currentDir != null) {
    val children = currentDir.children
    if (children.any { it.name == "WORKSPACE" || it.name == "MODULE.bazel" }) {
      // Found workspace root
      val workspacePath = currentDir.path
      val relativePath = filePath.removePrefix(workspacePath).removePrefix("/")
      val packageDir = relativePath.removeSuffix("/${file.name}")
      
      return packageDir.takeIf { it.isNotEmpty() } ?: ""
    }
    currentDir = currentDir.parent
  }
  
  return ""
}

/**
 * File-based index for efficiently finding Bazel targets by label.
 */
class BazelTargetIndex : FileBasedIndexExtension<Label, BazelTargetInfo>() {

  companion object {
    val INDEX_ID: ID<Label, BazelTargetInfo> = ID.create("BazelTargetIndex")

    /**
     * Get target by exact label
     */
    fun getTargetByLabel(label: Label, project: Project): BazelTargetInfo? {
      return FileBasedIndex.getInstance()
        .getValues(INDEX_ID, label, GlobalSearchScope.projectScope(project))
        .firstOrNull()
    }

    /**
     * Get all labels in the project
     */
    fun getAllLabels(project: Project): Set<Label> {
      val allKeys = mutableSetOf<Label>()
      FileBasedIndex.getInstance().processAllKeys(INDEX_ID, { key ->
        allKeys.add(key)
        true
      }, GlobalSearchScope.projectScope(project), null)
      return allKeys
    }

    /**
     * Get targets in a specific package
     */
    fun getTargetsInPackage(packagePath: String, repo: RepoType, project: Project): List<BazelTargetInfo> {
      val targets = mutableListOf<BazelTargetInfo>()
      
      FileBasedIndex.getInstance().processAllKeys(INDEX_ID, { label ->
        if (label is ResolvedLabel && 
            label.repo == repo &&
            label.packagePath.toString() == packagePath) {
          val values = FileBasedIndex.getInstance()
            .getValues(INDEX_ID, label, GlobalSearchScope.projectScope(project))
          targets.addAll(values)
        }
        true
      }, GlobalSearchScope.projectScope(project), null)
      
      return targets
    }

    /**
     * Find targets by name across all packages (for completion)
     */
    fun findTargetsByName(targetName: String, project: Project): List<Pair<Label, BazelTargetInfo>> {
      val results = mutableListOf<Pair<Label, BazelTargetInfo>>()
      
      FileBasedIndex.getInstance().processAllKeys(INDEX_ID, { label ->
        if (label.targetName == targetName) {
          val values = FileBasedIndex.getInstance()
            .getValues(INDEX_ID, label, GlobalSearchScope.projectScope(project))
          for (value in values) {
            results.add(label to value)
          }
        }
        true
      }, GlobalSearchScope.projectScope(project), null)
      
      return results
    }
  }

  override fun getName(): ID<Label, BazelTargetInfo> = INDEX_ID

  override fun getIndexer(): DataIndexer<Label, BazelTargetInfo, FileContent> {
    return DataIndexer { inputData ->
      val result = mutableMapOf<Label, BazelTargetInfo>()
      
      try {
        val packagePath = getPackagePathFromFile(inputData.file)
        
        // Use bazel query to get accurate target information
        val project = inputData.project
        if (project != null) {
          val queryService = BazelQueryService.getInstance(project)
          val targets = queryService.queryPackageTargets(packagePath)
          
          for (target in targets) {
            val label = createLabelForTarget(target, inputData.file)
            result[label] = target
          }
        }
        
      } catch (e: Exception) {
        // Log error but don't fail indexing
        // TODO: Add proper logging
      }
      
      result
    }
  }

  override fun getKeyDescriptor(): KeyDescriptor<Label> = BazelLabelKeyDescriptor()

  override fun getValueExternalizer(): DataExternalizer<BazelTargetInfo> = BazelTargetInfoExternalizer()

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return FileBasedIndex.InputFilter { file ->
      file.fileType == StarlarkFileType && 
      (file.name == "BUILD" || file.name == "BUILD.bazel")
    }
  }

  override fun dependsOnFileContent(): Boolean = true

  override fun getVersion(): Int = 3  // Increment to force re-indexing with new Label keys
}

/**
 * Information about a Bazel target stored in the index
 */
data class BazelTargetInfo(
  val targetName: String,
  val packagePath: String,
  val buildFilePath: String,
  val targetType: BazelTargetType,
  val ruleName: String,
  val dependencies: List<String> = emptyList()
) {
  
  /**
   * Convert to a BazelTargetSymbol
   */
  fun toSymbol(): BazelTargetSymbol {
    val packageSegments = if (packagePath.isEmpty()) emptyList() else packagePath.split("/")
    val label = org.jetbrains.bazel.label.ResolvedLabel(
      repo = org.jetbrains.bazel.label.Main,
      packagePath = org.jetbrains.bazel.label.Package(packageSegments),
      target = org.jetbrains.bazel.label.SingleTarget(targetName)
    )
    
    return BazelTargetSymbol(
      label = label,
      buildFilePath = buildFilePath,
      targetType = targetType
    )
  }
}

/**
 * Serializer for BazelTargetInfo
 */
class BazelTargetInfoExternalizer : DataExternalizer<BazelTargetInfo> {
  
  override fun save(out: DataOutput, value: BazelTargetInfo) {
    out.writeUTF(value.targetName)
    out.writeUTF(value.packagePath)
    out.writeUTF(value.buildFilePath)
    out.writeUTF(value.targetType.name)
    out.writeUTF(value.ruleName)
    
    // Write dependencies
    DataInputOutputUtil.writeSeq(out, value.dependencies) { output, dep ->
      output.writeUTF(dep)
    }
  }
  
  override fun read(`in`: DataInput): BazelTargetInfo {
    val targetName = `in`.readUTF()
    val packagePath = `in`.readUTF()
    val buildFilePath = `in`.readUTF()
    val targetType = BazelTargetType.valueOf(`in`.readUTF())
    val ruleName = `in`.readUTF()
    
    // Read dependencies
    val dependencies = DataInputOutputUtil.readSeq(`in`) { input ->
      input.readUTF()
    }
    
    return BazelTargetInfo(
      targetName = targetName,
      packagePath = packagePath,
      buildFilePath = buildFilePath,
      targetType = targetType,
      ruleName = ruleName,
      dependencies = dependencies
    )
  }
}