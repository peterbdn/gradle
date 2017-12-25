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

package org.gradle.nativeplatform.test.cpp.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.plugins.CppApplicationPlugin;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.cpp.plugins.CppLibraryPlugin;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestSuite;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import javax.inject.Inject;


/**
 * A plugin that sets up the infrastructure for testing C++ binaries using a simple test executable.
 *
 * Gradle will create a {@link RunTestExecutable} task that relies on the exit code of the binary.
 *
 * @since 4.4
 */
@Incubating
public class CppUnitTestPlugin implements Plugin<ProjectInternal> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;

    @Inject
    public CppUnitTestPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);
        project.getPluginManager().apply(TestingBasePlugin.class);

        // Add the unit test and extension
        final DefaultCppTestSuite testComponent = componentFactory.newInstance(CppTestSuite.class, DefaultCppTestSuite.class, "unitTest");
        project.getExtensions().add(CppTestSuite.class, "unitTest", testComponent);
        project.getComponents().add(testComponent);

        Action<Plugin<ProjectInternal>> projectConfiguration = new Action<Plugin<ProjectInternal>>() {
            @Override
            public void execute(Plugin<ProjectInternal> plugin) {
                ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class);
                CppExecutable binary = testComponent.addExecutable(result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                final TaskContainer tasks = project.getTasks();
                final CppComponent mainComponent = project.getComponents().withType(CppComponent.class).findByName("main");
                testComponent.getTestedComponent().set(mainComponent);

                // TODO: This should be modeled as a kind of dependency vs wiring tasks together directly.
                final AbstractLinkTask linkTest = binary.getLinkTask().get();
                Provider<FileCollection> mainObjects = mainComponent.getDevelopmentBinary().map(new Transformer<FileCollection, CppBinary>() {
                    @Override
                    public FileCollection transform(CppBinary devBinary) {
                        return devBinary.getObjects();
                    }
                });
                linkTest.source(mainObjects);
                // TODO: We shouldn't have to do this
                linkTest.dependsOn(mainObjects);

                // TODO: Replace with native test task
                final RunTestExecutable testTask = tasks.create("runUnitTest", RunTestExecutable.class, new Action<RunTestExecutable>() {
                    @Override
                    public void execute(RunTestExecutable testTask) {
                        testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                        testTask.setDescription("Executes C++ unit tests.");

                        final InstallExecutable installTask = (InstallExecutable) tasks.getByName("installUnitTest");
                        testTask.setExecutable(installTask.getRunScript());
                        testTask.dependsOn(testComponent.getTestExecutable().map(new Transformer<Provider<Directory>, CppExecutable>() {
                            @Override
                            public Provider<Directory> transform(CppExecutable cppExecutable) {
                                return cppExecutable.getInstallDirectory();
                            }
                        }));
                        // TODO: Honor changes to build directory
                        testTask.setOutputDir(project.getLayout().getBuildDirectory().dir("test-results/unitTest").get().getAsFile());
                    }
                });

                tasks.getByName("check").dependsOn(testTask);
            }
        };

        project.getPlugins().withType(CppLibraryPlugin.class, projectConfiguration);
        // TODO: We will get symbol conflicts with executables since they already have a main()
        project.getPlugins().withType(CppApplicationPlugin.class, projectConfiguration);

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                testComponent.getBinaries().realizeNow();
            }
        });
    }
}
