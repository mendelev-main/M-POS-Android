package com.mendelev.mpos.workspace

import android.os.Looper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosSystemBackTest {
    private fun snapshot(mask:Int=0)=JSONObject().put("version",1).put("token",9)
        .put("pendingImport",mask and 1!=0).put("modal",mask and 2!=0)
        .put("warehouse",mask and 4!=0).put("receiving",mask and 8!=0)
    @Test fun allFlagCombinationsPreserveOriginalAndroidBackPriority() {
        val actions=MPosSystemBackPolicy.Action.entries
        for(mask in 0..15) {
            val expected=when {mask and 1!=0->actions[0];mask and 2!=0->actions[1];mask and 4!=0->actions[2];mask and 8!=0->actions[3];else->actions[4]}
            assertEquals(expected,MPosSystemBackPolicy.choose(snapshot(mask)))
        }
        assertThrows(IllegalArgumentException::class.java){MPosSystemBackPolicy.choose(snapshot().put("modal","true"))}
    }
    private class Host {
        val calls=mutableListOf<Pair<String,(String?)->Unit>>()
        var backgrounds=0;var rollbacks=0
        val controller=MPosSystemBackController({script,reply->calls.add(script to reply)},{backgrounds++},{rollbacks++})
    }
    @Test fun duplicateBackIsSuppressedAndBackgroundRequiresCurrentAcknowledgement() {
        val h=Host();h.controller.handle();h.controller.handle();assertEquals(1,h.calls.size)
        h.calls[0].second(JSONObject.quote(snapshot().toString()));assertEquals(2,h.calls.size)
        assertTrue(h.calls[1].first.contains("BACKGROUND"));assertEquals(0,h.backgrounds)
        h.calls[1].second("true");assertEquals(1,h.backgrounds);h.controller.close()
    }
    @Test fun lifecycleInvalidationIgnoresOldSnapshotsAndEffects() {
        val h=Host();h.controller.handle();h.controller.invalidate()
        h.calls[0].second(JSONObject.quote(snapshot(2).toString()));assertEquals(1,h.calls.size)
        h.controller.handle();h.calls[1].second(JSONObject.quote(snapshot().toString()));h.controller.close()
        h.calls[2].second("true");assertEquals(0,h.backgrounds);assertEquals(0,h.rollbacks)
    }
    @Test fun unavailableAdapterUsesRollbackButStaleDispatchAndMalformedSnapshotDoNot() {
        val h=Host();h.controller.handle();h.calls[0].second("null");assertEquals(1,h.rollbacks)
        h.controller.handle();h.calls[1].second(JSONObject.quote(snapshot().toString()));h.calls[2].second("false");assertEquals(0,h.backgrounds)
        h.controller.handle();h.calls[3].second(JSONObject.quote(snapshot().apply{remove("token")}.toString()))
        assertEquals(1,h.rollbacks);h.controller.close()
    }
    @Test fun captureTimeoutDropsItsCallbackAndAllowsNextBack() {
        val h=Host();h.controller.handle();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        h.calls[0].second(JSONObject.quote(snapshot().toString()));assertEquals(1,h.calls.size)
        h.controller.handle();assertEquals(2,h.calls.size);h.controller.close()
    }
    @Test fun nativeOwnerAvoidsDomCaptureAndDropsBackgroundAfterNewOverlay() {
        val owner=MPosBackStateOwner();owner.update(snapshot().put("revision",1))
        val calls=mutableListOf<Pair<String,(String?)->Unit>>();var backgrounds=0
        val controller=MPosSystemBackController({script,reply->calls.add(script to reply)},{backgrounds++},{fail("unexpected rollback")},owner::capture,owner::isCurrent)
        controller.handle();assertEquals(1,calls.size);assertTrue(calls[0].first.contains("applyNative"));assertFalse(calls[0].first.contains("capture()"))
        owner.update(snapshot(2).put("revision",2));calls[0].second("true");assertEquals(0,backgrounds)
        val copy=owner.capture()!!;copy.put("modal",false);assertTrue(owner.capture()!!.getBoolean("modal"))
        owner.update(snapshot().put("revision",1));assertEquals(2,owner.capture()!!.getLong("revision"))
        owner.update(snapshot().put("revision",3).put("enabled",false));assertNull(owner.capture());controller.close()
    }
}
