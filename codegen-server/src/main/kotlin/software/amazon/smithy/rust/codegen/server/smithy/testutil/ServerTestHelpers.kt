/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.server.smithy.testutil

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.server.smithy.RustCodegenServerPlugin
import software.amazon.smithy.rust.codegen.server.smithy.ServerSymbolProviders
import software.amazon.smithy.rust.codegen.server.smithy.generators.ServerBuilderGenerator
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.RustSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.ServerCodegenConfig
import software.amazon.smithy.rust.codegen.smithy.ServerCodegenContext
import software.amazon.smithy.rust.codegen.smithy.ServerRustSettings
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitorConfig
import software.amazon.smithy.rust.codegen.smithy.generators.CodegenTarget
import software.amazon.smithy.rust.codegen.smithy.generators.StructureGenerator
import software.amazon.smithy.rust.codegen.smithy.generators.implBlock
import software.amazon.smithy.rust.codegen.testutil.TestRuntimeConfig

// These are the settings we default to if the user does not override them in their `smithy-build.json`.
val ServerTestSymbolVisitorConfig = SymbolVisitorConfig(
    runtimeConfig = TestRuntimeConfig,
    renameExceptions = false,
    handleRustBoxing = true,
    handleRequired = true,
)

private fun testServiceShapeFor(model: Model) =
    model.serviceShapes.firstOrNull() ?: ServiceShape.builder().version("test").id("test#Service").build()

fun serverTestSymbolProvider(model: Model, serviceShape: ServiceShape? = null) =
    serverTestSymbolProviders(model, serviceShape).symbolProvider

fun serverTestSymbolProviders(model: Model, serviceShape: ServiceShape? = null) =
    ServerSymbolProviders.from(
        model,
        serviceShape ?: testServiceShapeFor(model),
        ServerTestSymbolVisitorConfig,
        serverTestRustSettings((serviceShape ?: testServiceShapeFor(model)).id).codegenConfig.publicConstrainedTypes,
        RustCodegenServerPlugin::baseSymbolProvider,
    )

fun serverTestRustSettings(
    service: ShapeId = ShapeId.from("notrelevant#notrelevant"),
    moduleName: String = "test-module",
    moduleVersion: String = "0.0.1",
    moduleAuthors: List<String> = listOf("notrelevant"),
    moduleDescription: String = "not relevant",
    moduleRepository: String? = null,
    runtimeConfig: RuntimeConfig = TestRuntimeConfig,
    codegenConfig: ServerCodegenConfig = ServerCodegenConfig(),
    license: String? = null,
    examplesUri: String? = null,
    customizationConfig: ObjectNode? = null,
) = ServerRustSettings(
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
    customizationConfig,
)

fun serverTestCodegenContext(
    model: Model,
    serviceShape: ServiceShape? = null,
    settings: ServerRustSettings = serverTestRustSettings(),
    protocolShapeId: ShapeId? = null,
): ServerCodegenContext {
    val service =
        serviceShape
            ?: model.serviceShapes.firstOrNull()
            ?: ServiceShape.builder().version("test").id("test#Service").build()
    val protocol = protocolShapeId ?: ShapeId.from("test#Protocol")
    val serverSymbolProviders = ServerSymbolProviders.from(
        model,
        service,
        ServerTestSymbolVisitorConfig,
        settings.codegenConfig.publicConstrainedTypes,
        RustCodegenServerPlugin::baseSymbolProvider
    )

    return ServerCodegenContext(
        model,
        serverSymbolProviders.symbolProvider,
        service,
        protocol,
        settings,
        serverSymbolProviders.unconstrainedShapeSymbolProvider,
        serverSymbolProviders.constrainedShapeSymbolProvider,
        serverSymbolProviders.constraintViolationSymbolProvider,
        serverSymbolProviders.pubCrateConstrainedShapeSymbolProvider,
    )
}

/**
 * In tests, we frequently need to generate a struct, a builder, and an impl block to access said builder.
 */
fun StructureShape.serverRenderWithModelBuilder(model: Model, symbolProvider: RustSymbolProvider, writer: RustWriter) {
    StructureGenerator(model, symbolProvider, writer, this).render(CodegenTarget.SERVER)
    val serverCodegenContext = serverTestCodegenContext(model)
    val modelBuilder = ServerBuilderGenerator(
        serverCodegenContext,
        this,
    )
    modelBuilder.render(writer)
    writer.implBlock(this, symbolProvider) {
        modelBuilder.renderConvenienceMethod(this)
    }
}
