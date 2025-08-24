package org.jetbrains.bazel.symbols

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bazel.label.*
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Service for executing bazel query commands to get target information
 */
@Service(Service.Level.PROJECT)
class BazelQueryService(private val project: Project) {
    
    /**
     * Query targets in a specific package using bazel query
     */
    fun queryPackageTargets(packagePath: String): List<BazelTargetInfo> {
        val targets = mutableListOf<BazelTargetInfo>()
        
        try {
            val workspaceRoot = findWorkspaceRoot() ?: return emptyList()
            
            // Run bazel query to get all targets in the package
            val queryExpression = "//$packagePath:*"
            val process = ProcessBuilder()
                .command("bazel", "query", queryExpression, "--output=xml")
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            
            val success = process.waitFor(10, TimeUnit.SECONDS)
            if (!success) {
                process.destroyForcibly()
                return emptyList()
            }
            
            if (process.exitValue() == 0) {
                val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                targets.addAll(parseQueryXmlOutput(output, packagePath))
            }
            
        } catch (e: Exception) {
            // Log error but don't fail - fall back to empty list
            // TODO: Add proper logging
        }
        
        return targets
    }
    
    private fun findWorkspaceRoot(): File? {
        val projectBasePath = project.basePath ?: return null
        var currentDir = File(projectBasePath)
        
        while (currentDir != null) {
            if (File(currentDir, "WORKSPACE").exists() || 
                File(currentDir, "WORKSPACE.bazel").exists() || 
                File(currentDir, "MODULE.bazel").exists()) {
                return currentDir
            }
            currentDir = currentDir.parentFile
        }
        
        return null
    }
    
    private fun parseQueryXmlOutput(xmlOutput: String, packagePath: String): List<BazelTargetInfo> {
        val targets = mutableListOf<BazelTargetInfo>()
        
        try {
            // Simple XML parsing - in production, use a proper XML parser
            val rulePattern = Regex("""<rule class="([^"]+)" location="([^"]+)" name="([^"]+)">""")
            val depPattern = Regex("""<label value="([^"]+)"/>""")
            
            val lines = xmlOutput.lines()
            var i = 0
            
            while (i < lines.size) {
                val line = lines[i]
                val ruleMatch = rulePattern.find(line)
                
                if (ruleMatch != null) {
                    val ruleName = ruleMatch.groupValues[1]
                    val location = ruleMatch.groupValues[2]
                    val targetName = ruleMatch.groupValues[3].substringAfterLast(":")
                    
                    // Extract dependencies from following lines until </rule>
                    val dependencies = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].contains("</rule>")) {
                        val depMatch = depPattern.find(lines[i])
                        if (depMatch != null) {
                            dependencies.add(depMatch.groupValues[1])
                        }
                        i++
                    }
                    
                    val targetType = BazelTargetType.fromRuleName(ruleName)
                    val buildFilePath = location.substringBefore(":")
                    
                    targets.add(BazelTargetInfo(
                        targetName = targetName,
                        packagePath = packagePath,
                        buildFilePath = buildFilePath,
                        targetType = targetType,
                        ruleName = ruleName,
                        dependencies = dependencies
                    ))
                }
                i++
            }
            
        } catch (e: Exception) {
            // Log error but don't fail
            // TODO: Add proper logging
        }
        
        return targets
    }
    
    companion object {
        fun getInstance(project: Project): BazelQueryService {
            return project.getService(BazelQueryService::class.java)
        }
    }
}