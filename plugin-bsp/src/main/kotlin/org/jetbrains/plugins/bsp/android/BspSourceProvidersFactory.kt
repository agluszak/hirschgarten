package org.jetbrains.plugins.bsp.android

import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.SourceProvidersFactory
import com.android.tools.idea.projectsystem.SourceProvidersImpl
import com.android.tools.idea.projectsystem.emptySourceProvider
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.createSourceProvidersForLegacyModule
import org.jetbrains.plugins.bsp.impl.magicmetamodel.impl.workspacemodel.moduleEntity
import org.jetbrains.plugins.bsp.workspacemodel.entities.androidAddendumEntity
import org.jetbrains.sdkcompat.android.sourceProviderImpl

public class BspSourceProvidersFactory : SourceProvidersFactory {
  override fun createSourceProvidersFor(facet: AndroidFacet): SourceProviders {
    val sourceProvider = createSourceProviderForModule(facet) ?: return createSourceProvidersForLegacyModule(facet)

    return sourceProviderImpl(sourceProvider)
  }

  private fun createSourceProviderForModule(facet: AndroidFacet): NamedIdeaSourceProvider? {
    val module = facet.module
    val androidAddendum = module.moduleEntity?.androidAddendumEntity ?: return null
    val manifest = androidAddendum.manifest ?: return null
    return NamedIdeaSourceProviderBuilder
      .create(module.name, manifest.url)
      .withScopeType(ScopeType.MAIN)
      .withResDirectoryUrls(androidAddendum.resourceDirectories.map { it.url })
      .withAssetsDirectoryUrls(androidAddendum.assetsDirectories.map { it.url })
      .build()
  }
}
