/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

use crate::test_operation::{TestOperationParser, TestRetryClassifier};
use aws_smithy_async::rt::sleep::TokioSleep;
use aws_smithy_client::test_connection::TestConnection;
use aws_smithy_client::Client;
use aws_smithy_http::body::SdkBody;
use aws_smithy_http::operation;
use aws_smithy_http::operation::Operation;
use aws_smithy_http::result::SdkError;
use std::sync::Arc;
use std::time::Duration;
use tower::layer::util::Identity;

mod test_operation {
    use aws_smithy_http::operation;
    use aws_smithy_http::response::ParseHttpResponse;
    use aws_smithy_http::result::SdkError;
    use aws_smithy_http::retry::ClassifyRetry;
    use aws_smithy_types::retry::{ErrorKind, ProvideErrorKind, RetryKind};
    use bytes::Bytes;
    use std::error::Error;
    use std::fmt::{self, Debug, Display, Formatter};

    #[derive(Clone)]
    pub(super) struct TestOperationParser;

    #[derive(Debug)]
    pub(super) struct OperationError;

    impl Display for OperationError {
        fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
            write!(f, "{:?}", self)
        }
    }

    impl Error for OperationError {}

    impl ProvideErrorKind for OperationError {
        fn retryable_error_kind(&self) -> Option<ErrorKind> {
            Some(ErrorKind::ThrottlingError)
        }

        fn code(&self) -> Option<&str> {
            None
        }
    }

    impl ParseHttpResponse for TestOperationParser {
        type Output = Result<String, OperationError>;

        fn parse_unloaded(&self, response: &mut operation::Response) -> Option<Self::Output> {
            if response.http().status().is_success() {
                Some(Ok("Hello!".to_string()))
            } else {
                Some(Err(OperationError))
            }
        }

        fn parse_loaded(&self, _response: &http::Response<Bytes>) -> Self::Output {
            Ok("Hello!".to_string())
        }
    }

    #[derive(Clone)]
    pub(super) struct TestRetryClassifier;

    impl<T, E> ClassifyRetry<T, SdkError<E>> for TestRetryClassifier
    where
        E: ProvideErrorKind + Debug,
        T: Debug,
    {
        fn classify_retry(&self, err: Result<&T, &SdkError<E>>) -> RetryKind {
            let kind = match err {
                Err(SdkError::ServiceError { err, .. }) => err.retryable_error_kind(),
                Ok(_) => return RetryKind::Unnecessary,
                _ => panic!("test handler only handles modeled errors got: {:?}", err),
            };
            match kind {
                Some(kind) => RetryKind::Error(kind),
                None => RetryKind::UnretryableFailure,
            }
        }
    }
}

fn test_operation() -> Operation<TestOperationParser, TestRetryClassifier> {
    let req = operation::Request::new(
        http::Request::builder()
            .uri("https://test-service.test-region.amazonaws.com/")
            .body(SdkBody::from("request body"))
            .unwrap(),
    );
    Operation::new(req, TestOperationParser).with_retry_classifier(TestRetryClassifier)
}

#[tokio::test]
async fn end_to_end_retry_test() {
    fn req() -> http::Request<SdkBody> {
        http::Request::builder()
            .body(SdkBody::from("request body"))
            .unwrap()
    }

    fn ok() -> http::Response<&'static str> {
        http::Response::builder()
            .status(200)
            .body("response body")
            .unwrap()
    }

    fn err() -> http::Response<&'static str> {
        http::Response::builder()
            .status(500)
            .body("response body")
            .unwrap()
    }
    // 1 failing response followed by 1 successful response
    let events = vec![
        // First operation
        (req(), err()),
        (req(), err()),
        (req(), ok()),
        // Second operation
        (req(), err()),
        (req(), ok()),
        // Third operation will fail, only errors
        (req(), err()),
        (req(), err()),
        (req(), err()),
        (req(), err()),
    ];
    let conn = TestConnection::new(events);
    let retry_config = aws_smithy_client::retry::Config::default()
        .with_max_attempts(4)
        // This is the default, just setting it to be explicit
        .with_initial_backoff(Duration::from_secs(1))
        .with_base(|| 1_f64);
    let client = Client::<TestConnection<_>, Identity>::new(conn.clone())
        .with_retry_config(retry_config)
        .with_sleep_impl(Arc::new(TokioSleep::new()));
    tokio::time::pause();
    let initial = tokio::time::Instant::now();
    let resp = client
        .call(test_operation())
        .await
        .expect("successful operation");
    assert_time_passed(initial, Duration::from_secs(3));
    assert_eq!(resp, "Hello!");
    // 3 requests should have been made, 2 failing & one success
    assert_eq!(conn.requests().len(), 3);

    let initial = tokio::time::Instant::now();
    client
        .call(test_operation())
        .await
        .expect("successful operation");
    assert_time_passed(initial, Duration::from_secs(1));
    assert_eq!(conn.requests().len(), 5);
    let initial = tokio::time::Instant::now();
    let err = client
        .call(test_operation())
        .await
        .expect_err("all responses failed");
    // 4 more tries followed by failure
    assert_eq!(conn.requests().len(), 9);
    assert!(matches!(err, SdkError::ServiceError { .. }));
    assert_time_passed(initial, Duration::from_secs(7));
}

/// Validate that time has passed with a 5ms tolerance
///
/// This is to account for some non-determinism in the Tokio timer
fn assert_time_passed(initial: tokio::time::Instant, passed: Duration) {
    let now = tokio::time::Instant::now();
    let delta = now - initial;
    if (delta.as_millis() as i128 - passed.as_millis() as i128).abs() > 5 {
        assert_eq!(delta, passed)
    }
}
