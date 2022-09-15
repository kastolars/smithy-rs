/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

use tower::layer::util::Stack;

use crate::{
    operation::{Operation, OperationShape},
    plugin::{Pluggable, Plugin},
};

use super::{layer::InstrumentLayer, sensitivity::Sensitivity};

/// An [`Plugin`] which applies [`InstrumentLayer`] to all operations in the builder.
#[derive(Debug)]
pub struct TracePlugin;

impl<P, Op, S, L> Plugin<P, Op, S, L> for TracePlugin
where
    Op: OperationShape,
    Op: Sensitivity,
{
    type Service = S;
    type Layer = Stack<L, InstrumentLayer<Op::RequestFmt, Op::ResponseFmt>>;

    fn map(&self, operation: Operation<S, L>) -> Operation<Self::Service, Self::Layer> {
        let layer = InstrumentLayer::new(Op::NAME)
            .request_fmt(Op::request_fmt())
            .response_fmt(Op::response_fmt());
        operation.layer(layer)
    }
}

/// An extension trait for applying [`InstrumentLayer`] to all operations.
pub trait TraceExt: Pluggable<TracePlugin> {
    /// Applies [`InstrumentLayer`] to all operations. See [`InstrumentOperation`](super::InstrumentOperation) for more
    /// information.
    fn trace(self) -> Self::Output
    where
        Self: Sized,
    {
        self.apply(TracePlugin)
    }
}

impl<Builder> TraceExt for Builder where Builder: Pluggable<TracePlugin> {}
