/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.client.smithy.generators

import org.junit.jupiter.api.Test
import software.amazon.smithy.rust.codegen.client.rustlang.CargoDependency
import software.amazon.smithy.rust.codegen.client.rustlang.CratesIo
import software.amazon.smithy.rust.codegen.client.testutil.TestWorkspace
import software.amazon.smithy.rust.codegen.client.testutil.compileAndTest
import software.amazon.smithy.rust.codegen.client.testutil.unitTest
import software.amazon.smithy.rust.codegen.core.Version

class CargoTomlGeneratorTest {
    private val CargoMetadata: CargoDependency = CargoDependency("cargo_metadata", CratesIo("0.15.0"))

    @Test
    fun `adds codegen version to package metadata`() {
        val project = TestWorkspace.testProject()
        project.lib { writer ->
            writer.addDependency(CargoMetadata)
            writer.unitTest(
                "smithy_codegen_version_in_package_metadata",
                """
                let metadata = cargo_metadata::MetadataCommand::new()
                    .exec()
                    .expect("could not run `cargo metadata`");

                let pgk_metadata = &metadata.root_package().expect("missing root package").metadata;

                let codegen_version = pgk_metadata
                    .get("smithy")
                    .and_then(|s| s.get("codegen-version"))
                    .expect("missing `smithy.codegen-version` field")
                    .as_str()
                    .expect("`smithy.codegen-version` is not str");
                assert_eq!(codegen_version, "${Version.fullVersion()}");
                """,
            )
        }
        project.compileAndTest()
    }
}
