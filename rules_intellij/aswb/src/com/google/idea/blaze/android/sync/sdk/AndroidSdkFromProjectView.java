/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.sdk;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.projectview.AndroidMinSdkSection;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewSet.ProjectViewFile;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.Navigatable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;

/** Calculates AndroidSdkPlatform. */
public class AndroidSdkFromProjectView {
  private static final Joiner COMMA_JOINER = Joiner.on(", ");
  public static final String NO_SDK_ERROR_TEMPLATE =
      "No such android_sdk_platform: '%s'. "
          + "Available android_sdk_platforms are: %s. "
          + "Please change android_sdk_platform or run SDK manager "
          + "to download missing SDK platforms.";

  @Nullable
  public static AndroidSdkPlatform getAndroidSdkPlatform(
      BlazeContext context, ProjectViewSet projectViewSet) {
    List<Sdk> sdks = BlazeSdkProvider.getInstance().getAllAndroidSdks();
    if (sdks.isEmpty()) {
      IssueOutput.error("No Android SDK configured. Please use the SDK manager to configure.")
          .navigatable(
              new Navigatable() {
                @Override
                public void navigate(boolean b) {
                  SdkUtil.openSdkManager();
                }

                @Override
                public boolean canNavigate() {
                  return true;
                }

                @Override
                public boolean canNavigateToSource() {
                  return false;
                }
              })
          .submit(context);
      return null;
    }
    if (projectViewSet == null) {
      return null;
    }

    String androidSdk = projectViewSet.getScalarValue(AndroidSdkPlatformSection.KEY).orElse(null);
    Integer androidMinSdk = projectViewSet.getScalarValue(AndroidMinSdkSection.KEY).orElse(null);

    if (androidSdk == null) {
      ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
      IssueOutput.error(
              ("No android_sdk_platform set. Please set to an android platform. "
                  + "Available android_sdk_platforms are: "
                  + getAvailableTargetHashesAsList(sdks)))
          .inFile(projectViewFile != null ? projectViewFile.projectViewFile : null)
          .submit(context);
      return null;
    }

    Sdk sdk = BlazeSdkProvider.getInstance().findSdk(androidSdk);
    if (sdk == null) {
      ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
      IssueOutput.error(
              String.format(NO_SDK_ERROR_TEMPLATE, androidSdk, getAllAvailableTargetHashes()))
          .inFile(projectViewFile != null ? projectViewFile.projectViewFile : null)
          .submit(context);
      return null;
    }

    if (androidMinSdk == null) {
      androidMinSdk = getAndroidSdkApiLevel(sdk);
    }
    return new AndroidSdkPlatform(androidSdk, androidMinSdk);
  }

  public static List<String> getAvailableSdkTargetHashes(Collection<Sdk> sdks) {
    Set<String> names = Sets.newHashSet();
    for (Sdk sdk : sdks) {
      String targetHash = BlazeSdkProvider.getInstance().getSdkTargetHash(sdk);
      if (targetHash != null) {
        names.add(targetHash);
      }
    }
    List<String> result = Lists.newArrayList(names);
    result.sort(String::compareTo);
    return result;
  }

  private static String getAvailableTargetHashesAsList(Collection<Sdk> sdks) {
    return COMMA_JOINER.join(getAvailableSdkTargetHashes(sdks));
  }

  private static String getAllAvailableTargetHashes() {
    return COMMA_JOINER.join(
        getAvailableSdkTargetHashes(BlazeSdkProvider.getInstance().getAllAndroidSdks()));
  }

  private static int getAndroidSdkApiLevel(Sdk sdk) {
    int androidSdkApiLevel = 1;
    AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData) sdk.getSdkAdditionalData();
    if (additionalData != null) {
      AndroidPlatform androidPlatform = additionalData.getAndroidPlatform();
      if (androidPlatform != null) {
        androidSdkApiLevel = androidPlatform.getApiLevel();
      }
    }
    return androidSdkApiLevel;
  }
}
