$version: "1.0"

namespace aws.protocoltests.misc

use aws.protocols#restJson1

/// A service to test miscellaneous aspects of code generation where protocol
/// selection is not relevant. If you want to test something protocol-specific,
/// add it to a separate `<protocol>-extras.smithy`.
@restJson1
@title("MiscService")
service MiscService {
    operations: [
        RequiredInnerShapeOperation,
        RequiredHeaderCollectionOperation,
    ],
}

/// This operation tests that (de)serializing required values from a nested
/// shape works correctly.
@http(uri: "/required-inner-shape-operation", method: "GET")
operation RequiredInnerShapeOperation {
    input: RequiredInnerShapeOperationInputOutput,
    output: RequiredInnerShapeOperationInputOutput,
}

@http(uri: "/required-header-collection-operation", method: "GET")
operation RequiredHeaderCollectionOperation {
    input: RequiredHeaderCollectionOperationInputOutput,
    output: RequiredHeaderCollectionOperationInputOutput,
}

structure RequiredInnerShapeOperationInputOutput {
    inner: InnerShape
}

structure InnerShape {
    @required
    requiredInnerMostShape: InnermostShape
}

structure InnermostShape {
    @required
    aString: String,

    @required
    aBoolean: Boolean,

    @required
    aByte: Byte,

    @required
    aShort: Short,

    @required
    anInt: Integer,

    @required
    aLong: Long,

    @required
    aFloat: Float,

    @required
    aDouble: Double,

    // TODO(https://github.com/awslabs/smithy-rs/issues/312)
    // @required
    // aBigInteger: BigInteger,

    // @required
    // aBigDecimal: BigDecimal,

    @required
    aTimestamp: Timestamp,

    @required
    aDocument: Timestamp,

    @required
    aStringList: AStringList,

    @required
    aStringMap: AMap,

    @required
    aStringSet: AStringSet,

    @required
    aBlob: Blob,

    @required
    aUnion: AUnion
}

list AStringList {
    member: String
}

list AStringSet {
    member: String
}

map AMap {
    key: String,
    value: Timestamp
}

union AUnion {
    i32: Integer,
    string: String,
    time: Timestamp,
}

structure RequiredHeaderCollectionOperationInputOutput {
    @required
    @httpHeader("X-Required-List")
    requiredHeaderList: HeaderList,

    @required
    @httpHeader("X-Required-Set")
    requiredHeaderSet: HeaderSet,
}

list HeaderList {
    member: String
}

set HeaderSet {
    member: String
}
