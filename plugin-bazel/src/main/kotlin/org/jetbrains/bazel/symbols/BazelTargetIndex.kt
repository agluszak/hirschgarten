package org.jetbrains.bazel.symbols

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.bazel.label.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory index for efficiently finding Bazel targets by label.
 * Populated during project sync with accurate data from Bazel.
 */
@Service(Service.Level.PROJECT)
class BazelTargetIndex(private val project: Project) {

  // In-memory storage for target information
  private val targets = ConcurrentHashMap<Label, BazelTargetInfo>()
  
  /**
   * Update the target index with new data from sync
   */
  fun updateTargets(newTargets: Map<Label, BazelTargetInfo>) {
    targets.clear()
    targets.putAll(newTargets)
  }
  
  /**
   * Get target by exact label
   */
  fun getTargetByLabel(label: Label): BazelTargetInfo? {
    return targets[label]
  }

  /**
   * Get all labels in the project
   */
  fun getAllLabels(): Set<Label> {
    return targets.keys.toSet()
  }

  /**
   * Get targets in a specific package
   */
  fun getTargetsInPackage(packagePath: String, repo: RepoType): List<BazelTargetInfo> {
    return targets.entries
      .filter { (label, _) ->
        label is ResolvedLabel && 
        label.repo == repo &&
        label.packagePath.toString() == packagePath
      }
      .map { it.value }
  }

  /**
   * Find targets by name across all packages (for completion)
   */
  fun findTargetsByName(targetName: String): List<Pair<Label, BazelTargetInfo>> {
    return targets.entries
      .filter { (label, _) -> label.targetName == targetName }
      .map { (label, info) -> label to info }
  }
  
  companion object {
    fun getInstance(project: Project): BazelTargetIndex {
      return project.getService(BazelTargetIndex::class.java)
    }
  }
}

/**
 * Data class representing Bazel target information
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
   * Convert to Symbol API representation
   */
  fun toSymbol(): BazelTargetSymbol? {
    return try {
      val packageSegments = if (packagePath.isEmpty()) emptyList() else packagePath.split("/")
      
      val label = ResolvedLabel(
        repo = Main,  // For now, assume main workspace
        packagePath = Package(packageSegments),
        target = SingleTarget(targetName)
      )
      
      BazelTargetSymbol(
        label = label,
        buildFilePath = buildFilePath,
        targetType = targetType
      )
    } catch (e: IllegalArgumentException) {
      thisLogger().debug("Failed to create symbol for target '$targetName' in package '$packagePath': ${e.message}")
      null
    } catch (e: Exception) {
      thisLogger().warn("Unexpected error creating symbol for target '$targetName' in package '$packagePath'", e)
      null
    }
  }
}