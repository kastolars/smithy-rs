/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.client.testutil

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.NullableIndex
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.client.rustlang.Attribute
import software.amazon.smithy.rust.codegen.client.rustlang.CargoDependency
import software.amazon.smithy.rust.codegen.client.rustlang.CratesIo
import software.amazon.smithy.rust.codegen.client.rustlang.DependencyScope
import software.amazon.smithy.rust.codegen.client.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.client.rustlang.asType
import software.amazon.smithy.rust.codegen.client.smithy.CoreCodegenConfig
import software.amazon.smithy.rust.codegen.client.smithy.CoreCodegenContext
import software.amazon.smithy.rust.codegen.client.smithy.CoreRustSettings
import software.amazon.smithy.rust.codegen.client.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.client.smithy.RuntimeCrateLocation
import software.amazon.smithy.rust.codegen.client.smithy.RustCodegenPlugin
import software.amazon.smithy.rust.codegen.client.smithy.RustSymbolProvider
import software.amazon.smithy.rust.codegen.client.smithy.SymbolVisitorConfig
import software.amazon.smithy.rust.codegen.client.smithy.generators.BuilderGenerator
import software.amazon.smithy.rust.codegen.client.smithy.generators.CodegenTarget
import software.amazon.smithy.rust.codegen.client.smithy.generators.StructureGenerator
import software.amazon.smithy.rust.codegen.client.smithy.generators.implBlock
import software.amazon.smithy.rust.codegen.core.util.dq
import software.amazon.smithy.rust.codegen.core.util.letIf
import java.io.File

val TestRuntimeConfig =
    RuntimeConfig(runtimeCrateLocation = RuntimeCrateLocation.Path(File("../rust-runtime/").absolutePath))
val TestSymbolVisitorConfig = SymbolVisitorConfig(
    runtimeConfig = TestRuntimeConfig,
    renameExceptions = true,
    handleRustBoxing = true,
    nullabilityCheckMode = NullableIndex.CheckMode.CLIENT_ZERO_VALUE_V1,
)

fun testRustSettings(
    service: ShapeId = ShapeId.from("notrelevant#notrelevant"),
    moduleName: String = "test-module",
    moduleVersion: String = "0.0.1",
    moduleAuthors: List<String> = listOf("notrelevant"),
    moduleDescription: String = "not relevant",
    moduleRepository: String? = null,
    runtimeConfig: RuntimeConfig = TestRuntimeConfig,
    codegenConfig: CoreCodegenConfig = CoreCodegenConfig(),
    license: String? = null,
    examplesUri: String? = null,
) = CoreRustSettings(
    service,
    moduleName,
    moduleVersion,
    moduleAuthors,
    moduleDescription,
    moduleRepository,
    runtimeConfig,
    codegenConfig,
    license,
    examplesUri,
)

fun testSymbolProvider(model: Model, serviceShape: ServiceShape? = null): RustSymbolProvider =
    RustCodegenPlugin.baseSymbolProvider(
        model,
        serviceShape ?: ServiceShape.builder().version("test").id("test#Service").build(),
        TestSymbolVisitorConfig,
    )

fun testCodegenContext(
    model: Model,
    serviceShape: ServiceShape? = null,
    settings: CoreRustSettings = testRustSettings(),
    codegenTarget: CodegenTarget = CodegenTarget.CLIENT,
): CoreCodegenContext = CoreCodegenContext(
    model,
    testSymbolProvider(model),
    serviceShape
        ?: model.serviceShapes.firstOrNull()
        ?: ServiceShape.builder().version("test").id("test#Service").build(),
    ShapeId.from("test#Protocol"),
    settings,
    codegenTarget,
)

private const val SmithyVersion = "1.0"
fun String.asSmithyModel(sourceLocation: String? = null, smithyVersion: String = SmithyVersion): Model {
    val processed = letIf(!this.startsWith("\$version")) { "\$version: ${smithyVersion.dq()}\n$it" }
    return Model.assembler().discoverModels().addUnparsedModel(sourceLocation ?: "test.smithy", processed).assemble()
        .unwrap()
}

/**
 * In tests, we frequently need to generate a struct, a builder, and an impl block to access said builder.
 */
fun StructureShape.renderWithModelBuilder(model: Model, symbolProvider: RustSymbolProvider, writer: RustWriter, forWhom: CodegenTarget = CodegenTarget.CLIENT) {
    StructureGenerator(model, symbolProvider, writer, this).render(forWhom)
    val modelBuilder = BuilderGenerator(model, symbolProvider, this)
    modelBuilder.render(writer)
    writer.implBlock(this, symbolProvider) {
        modelBuilder.renderConvenienceMethod(this)
    }
}

val TokioWithTestMacros = CargoDependency(
    "tokio",
    CratesIo("1"),
    features = setOf("macros", "test-util", "rt"),
    scope = DependencyScope.Dev,
)

val TokioTest = Attribute.Custom("tokio::test", listOf(TokioWithTestMacros.asType()))
