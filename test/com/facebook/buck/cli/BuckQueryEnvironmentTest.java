/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cli;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.facebook.buck.android.FakeAndroidDirectoryResolver;
import com.facebook.buck.event.BuckEventBusFactory;
import com.facebook.buck.httpserver.WebServer;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.java.FakeJavaPackageFinder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.NoopArtifactCache;
import com.facebook.buck.rules.Repository;
import com.facebook.buck.rules.TestRepositoryBuilder;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.devtools.build.lib.query2.engine.FunctionExpression;
import com.google.devtools.build.lib.query2.engine.QueryEnvironment;
import com.google.devtools.build.lib.query2.engine.QueryEnvironment.Argument;
import com.google.devtools.build.lib.query2.engine.QueryEnvironment.QueryFunction;
import com.google.devtools.build.lib.query2.engine.QueryException;
import com.google.devtools.build.lib.query2.engine.QueryExpression;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BuckQueryEnvironmentTest {

  private TestConsole console;
  private BuckQueryEnvironment buckQueryEnvironment;
  private CommandRunnerParams params;
  private ObjectMapper objectMapper = new ObjectMapper();

  // Create a dummy QueryExpression to be used when calling functions that require a
  // QueryExpression as the 'caller'.
  private QueryExpression createDummyQueryExpression() {
    QueryFunction function = new QueryKindFunction();
    List<Argument> arguments = new ArrayList<>();
    return new FunctionExpression(function, arguments);
  }

  @Before
  public void setUp() throws IOException, InterruptedException {
    console = new TestConsole();
    Repository repository = new TestRepositoryBuilder()
        .setFilesystem(
            new ProjectFilesystem(TestDataHelper.getTestDataScenario(
                this,
                "query_command")))
        .build();

    params = CommandRunnerParamsForTesting.createCommandRunnerParamsForTesting(
        console,
        repository,
        new FakeAndroidDirectoryResolver(),
        new InstanceArtifactCacheFactory(new NoopArtifactCache()),
        BuckEventBusFactory.newInstance(),
        new FakeBuckConfig(),
        Platform.detect(),
        ImmutableMap.copyOf(System.getenv()),
        new FakeJavaPackageFinder(),
        objectMapper,
        Optional.<WebServer>absent());

    buckQueryEnvironment = new BuckQueryEnvironment(
        params,
        /* settings */ new HashSet<QueryEnvironment.Setting>(),
        /* enableProfiling */ false);
  }

  @Test
  public void testResolveSingleTargets() throws QueryException {
    ImmutableSet<BuildTarget> targets;
    QueryExpression expr = createDummyQueryExpression();
    BuildTarget buildTargetSix = BuildTarget.builder("//example", "six").build();
    targets = buckQueryEnvironment.getTargetsMatchingPattern(expr, "//example:six");
    assertThat(targets, is(equalTo(ImmutableSet.of(buildTargetSix))));
    assertThat(
        buckQueryEnvironment.targetPatternEvaluator.getKnownTargets(),
        is(equalTo(ImmutableSet.of(buildTargetSix, buildTargetSix))));

    BuildTarget buildTargetSeven = BuildTarget.builder("//example/app", "seven").build();
    targets = buckQueryEnvironment.getTargetsMatchingPattern(expr, "//example/app:seven");
    assertThat(targets, is(equalTo(ImmutableSet.of(buildTargetSeven))));
    assertThat(
        buckQueryEnvironment.targetPatternEvaluator.getKnownTargets(),
        is(equalTo(ImmutableSet.of(buildTargetSix, buildTargetSeven))));
  }

  @Test
  public void testResolveTargetPattern() throws QueryException {
    ImmutableSet<BuildTarget> targets;
    QueryExpression expr = createDummyQueryExpression();
    ImmutableSet<BuildTarget> expectedTargets = ImmutableSortedSet.of(
        BuildTarget.builder("//example", "one").build(),
        BuildTarget.builder("//example", "two").build(),
        BuildTarget.builder("//example", "three").build(),
        BuildTarget.builder("//example", "four").build(),
        BuildTarget.builder("//example", "five").build(),
        BuildTarget.builder("//example", "six").build(),
        BuildTarget.builder("//example", "application-test-lib").build(),
        BuildTarget.builder("//example", "one-tests").build(),
        BuildTarget.builder("//example", "four-tests").build(),
        BuildTarget.builder("//example", "four-application-tests").build(),
        BuildTarget.builder("//example", "six-tests").build()
    );
    targets = buckQueryEnvironment.getTargetsMatchingPattern(expr, "//example:");
    assertThat(targets, is(equalTo(expectedTargets)));
    assertThat(
        buckQueryEnvironment.targetPatternEvaluator.getKnownTargets(),
        is(equalTo(expectedTargets)));
  }
}
