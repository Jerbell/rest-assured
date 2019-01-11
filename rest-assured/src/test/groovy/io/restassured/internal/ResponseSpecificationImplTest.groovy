package io.restassured.internal

import io.restassured.config.FailureConfig
import io.restassured.config.LogConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.internal.log.LogRepository
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import org.junit.Test

import java.nio.charset.Charset

import static org.hamcrest.CoreMatchers.equalTo
import static org.mockito.Matchers.any
import static org.mockito.Matchers.same
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.never
import static org.mockito.Mockito.times
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

/**
 * Initial state for all checks here is response spec with response body loaded into its logRepository. This
 * structure means that ResponseLoggingFilter saved response for later to be printed only if validation fails.
 */
class ResponseSpecificationImplTest {

    private static final String EXPECTED_BODY = "goodTestBody"
    private static final String UNEXPECTED_BODY = "badTestBody"

    @Test
    void "Should call default failure listener to print response if log repository has response and validation failed"() {
        //given
        PrintStream printStreamMock = mock(PrintStream)
        ResponseSpecificationImpl respSpecImpl = createRespSpec(UNEXPECTED_BODY, printStreamMock)
        Response unexpectedResponse = when(mock(Response).asString()).thenReturn(UNEXPECTED_BODY).getMock()

        //when
        try {
            respSpecImpl.validate(unexpectedResponse)
        } catch (AssertionError ignored) {}

        //then
        verify(printStreamMock, times(1)).print(UNEXPECTED_BODY)
    }

    @Test
    void "Should NOT call default failure listener to print response if validation passed"() {
        //given
        PrintStream printStreamMock = mock(PrintStream)
        ResponseSpecificationImpl respSpecImpl = createRespSpec(EXPECTED_BODY, printStreamMock)
        Response expectedResponse = when(mock(Response).asString()).thenReturn(EXPECTED_BODY).getMock()

        //when
        respSpecImpl.validate(expectedResponse)
        //then
        verify(printStreamMock, never()).print(EXPECTED_BODY)
    }

    @Test
    void "Should call custom failure listener when validation fails"() {
        //given
        ResponseValidationFailureListener customListener = mock(ResponseValidationFailureListener)
        ResponseSpecificationImpl respSpecImpl = createRespSpec(UNEXPECTED_BODY, System.out, customListener)
        Response matchingResponse = when(mock(Response).asString()).thenReturn(UNEXPECTED_BODY).getMock()
        //when
        try {
            respSpecImpl.validate(matchingResponse)
        } catch (AssertionError ignored) {}
        //then
        verify(customListener, times(1)).onFailure(any(RequestSpecification.class), same(respSpecImpl), same(matchingResponse))
    }

    @Test
    void "Should NOT call custom failure listener when validation passes"() {
        //given
        ResponseValidationFailureListener customListener = mock(ResponseValidationFailureListener)
        ResponseSpecificationImpl respSpecImpl = createRespSpec(EXPECTED_BODY, System.out, customListener)
        Response nonmatchingResponse = when(mock(Response).asString()).thenReturn(EXPECTED_BODY).getMock()

        //when
        respSpecImpl.validate(nonmatchingResponse)
        //then
        verify(customListener, never()).onFailure(any(RequestSpecification.class), same(respSpecImpl), same(nonmatchingResponse))
    }

    private static ResponseSpecificationImpl createRespSpec(String responseContentInLogRepository, PrintStream logOutputStream = System.out, ResponseValidationFailureListener failureListener = null) {
        def customFailureListeners = failureListener == null ? [] : [failureListener]
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        baos.write(responseContentInLogRepository.getBytes(Charset.defaultCharset()))
        def logRepository = new LogRepository()
        logRepository.registerResponseLog(baos)

        def respSpecImpl = new ResponseSpecificationImpl('/', null, null,
                new RestAssuredConfig()
                        .logConfig(new LogConfig(logOutputStream, true))
                        .failureConfig(new FailureConfig(customFailureListeners)),
                logRepository)

        respSpecImpl.content(equalTo(EXPECTED_BODY))

        respSpecImpl
    }
}
