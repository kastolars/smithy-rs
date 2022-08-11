/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import java.util.Optional

/**
 * [ServerRustSettings] and [ServerCodegenConfig] classes.
 *
 * These classes are entirely analogous to [ClientRustSettings] and [ClientCodegenConfig]. Refer to the documentation
 * for those.
 *
 * These classes have to live in the `codegen` subproject because they are referenced in [ServerCodegenContext],
 * which is used in common generators to both client and server.
 */

/**
 * Settings used by [RustCodegenServerPlugin].
 */
data class ServerRustSettings(
    override val service: ShapeId,
    override val moduleName: String,
    override val moduleVersion: String,
    override val moduleAuthors: List<String>,
    override val moduleDescription: String?,
    override val moduleRepository: String?,
    override val runtimeConfig: RuntimeConfig,
    override val codegenConfig: ServerCodegenConfig,
    override val license: String?,
    override val examplesUri: String?,
    override val customizationConfig: ObjectNode?,
) : CoreRustSettings(
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
) {
    companion object {
        fun from(model: Model, config: ObjectNode): ServerRustSettings {
            val coreRustSettings = CoreRustSettings.from(model, config)
            val codegenSettingsNode = config.getObjectMember(CODEGEN_SETTINGS)
            val coreCodegenConfig = CoreCodegenConfig.fromNode(codegenSettingsNode)
            return ServerRustSettings(
                service = coreRustSettings.service,
                moduleName = coreRustSettings.moduleName,
                moduleVersion = coreRustSettings.moduleVersion,
                moduleAuthors = coreRustSettings.moduleAuthors,
                moduleDescription = coreRustSettings.moduleDescription,
                moduleRepository = coreRustSettings.moduleRepository,
                runtimeConfig = coreRustSettings.runtimeConfig,
                codegenConfig = ServerCodegenConfig.fromCodegenConfigAndNode(coreCodegenConfig, codegenSettingsNode),
                license = coreRustSettings.license,
                examplesUri = coreRustSettings.examplesUri,
                customizationConfig = coreRustSettings.customizationConfig,
            )
        }
    }
}

/**
 * [publicConstrainedTypes]: Generate constrained wrapper newtypes for constrained shapes
 */
data class ServerCodegenConfig(
    override val formatTimeoutSeconds: Int = defaultFormatTimeoutSeconds,
    override val debugMode: Boolean = defaultDebugMode,
    override val eventStreamAllowList: Set<String> = defaultEventStreamAllowList,
    // TODO Unit test that we don't generate public constrained types when this setting is false.
    val publicConstrainedTypes: Boolean = defaultPublicConstrainedTypes
) : CoreCodegenConfig(
    formatTimeoutSeconds, debugMode, eventStreamAllowList,
) {
    companion object {
        private const val defaultPublicConstrainedTypes = true

        fun fromCodegenConfigAndNode(coreCodegenConfig: CoreCodegenConfig, node: Optional<ObjectNode>) =
            if (node.isPresent) {
                ServerCodegenConfig(
                    formatTimeoutSeconds = coreCodegenConfig.formatTimeoutSeconds,
                    debugMode = coreCodegenConfig.debugMode,
                    eventStreamAllowList = coreCodegenConfig.eventStreamAllowList,
                    publicConstrainedTypes = node.get().getBooleanMemberOrDefault("publicConstrainedTypes", defaultPublicConstrainedTypes),
                )
            } else {
                ServerCodegenConfig(
                    formatTimeoutSeconds = coreCodegenConfig.formatTimeoutSeconds,
                    debugMode = coreCodegenConfig.debugMode,
                    eventStreamAllowList = coreCodegenConfig.eventStreamAllowList,
                )
            }
    }
}
