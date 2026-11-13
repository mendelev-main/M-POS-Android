package com.mendelev.mpos
import com.mendelev.mpos.core.ui.TransientMessages
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class TransientMessagesTest {
    @Test fun messageExpiresAfterExactlyTwoSeconds()=runTest {
        val messages=TransientMessages(this)
        messages.show("Товар сохранён");runCurrent()
        advanceTimeBy(1999);assertEquals("Товар сохранён",messages.state.value)
        advanceTimeBy(1);runCurrent();assertNull(messages.state.value)
    }
    @Test fun sameOrDifferentNextMessageGetsItsOwnFullLifetime()=runTest {
        val messages=TransientMessages(this)
        for(next in listOf("Товар сохранён","Товар удалён")) {
            messages.show("Товар сохранён");runCurrent()
            advanceTimeBy(1500);messages.show(next);runCurrent()
            advanceTimeBy(500);runCurrent();assertEquals(next,messages.state.value)
            advanceTimeBy(1500);runCurrent();assertNull(messages.state.value)
        }
    }
    @Test fun clearingDoesNotAllowAnOldTimerToDismissNewFeedback()=runTest {
        val messages=TransientMessages(this)
        messages.show("Первое");runCurrent();advanceTimeBy(1500)
        messages.clear();assertNull(messages.state.value)
        messages.show("Второе");runCurrent();advanceTimeBy(500);runCurrent()
        assertEquals("Второе",messages.state.value)
        advanceTimeBy(1500);runCurrent();assertNull(messages.state.value)
    }
}
