package com.yuuverne.smssender

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsNotificationListenerScopeTest {

    @Test
    fun childFailureDoesNotCancelScope() = runTest {
        val failures = mutableListOf<Throwable>()
        val scope = createNotificationScope(
            dispatcher = StandardTestDispatcher(testScheduler),
            exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                failures += throwable
            }
        )

        try {
            val failure = IllegalStateException("notification failed")
            scope.launch { throw failure }
            runCurrent()

            assertEquals(listOf(failure), failures)
            assertTrue(scope.isActive)

            var followUpExecuted = false
            scope.launch { followUpExecuted = true }
            runCurrent()

            assertTrue(followUpExecuted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingScopePreventsNewTasks() = runTest {
        val scope = createNotificationScope(
            dispatcher = StandardTestDispatcher(testScheduler),
            exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                throw AssertionError("Unexpected coroutine failure", throwable)
            }
        )

        scope.cancel()
        var taskExecuted = false
        scope.launch { taskExecuted = true }
        runCurrent()

        assertFalse(scope.isActive)
        assertFalse(taskExecuted)
    }
}
