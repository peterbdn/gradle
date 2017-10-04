/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.vcs;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Configuration related to source dependencies.
 * <pre class="autoTested">
 * // Configuring VCS mapping
 * // file: settings.gradle
 * sourceControl {
 *    vcsMappings {
 *        withModule('org.gradle:tooling-api') {
 *            from vcs(GitVersionControlSpec) {
 *                url = uri("https://github.com/gradle/gradle")
 *            }
 *        }
 *    }
 * }
 * </pre>
 *
 * @since 4.3
 */
@Incubating
public interface SourceControl {
    /**
     * Configures VCS mappings.
     */
    void vcsMappings(Action<? super VcsMappings> configuration);

    /**
     * Returns the VCS mappings configuration.
     */
    VcsMappings getVcsMappings();
}