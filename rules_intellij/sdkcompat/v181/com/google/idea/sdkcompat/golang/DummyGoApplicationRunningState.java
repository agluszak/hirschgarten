/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.golang;

import com.goide.runconfig.application.GoApplicationConfiguration;
import com.goide.runconfig.application.GoApplicationRunningState;
import com.goide.util.GoExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import java.util.List;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public class DummyGoApplicationRunningState extends GoApplicationRunningState {

  public DummyGoApplicationRunningState(
      ExecutionEnvironment executionEnvironment,
      Module module,
      GoApplicationConfiguration goApplicationConfiguration) {
    super(executionEnvironment, module, goApplicationConfiguration);
  }

  @Nullable
  @Override
  public List<String> getBuildingTarget() {
    return null;
  }

  @Nullable
  @Override
  public GoExecutor createBuildExecutor() {
    return null;
  }
}
