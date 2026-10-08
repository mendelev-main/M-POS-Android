package com.mendelev.mpos.workspace

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.json.JSONTokener

/** One in-flight Back, with lifecycle invalidation and no effects from stale callbacks. */
class MPosSystemBackController(
    private val evaluate:(String,(String?)->Unit)->Unit,
    private val background:()->Unit,
    private val rollback:()->Unit,
    private val nativeCapture:(()->JSONObject?)?=null,
    private val nativeCurrent:((Long)->Boolean)?=null
) {
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0L
    private var pending=false
    private var closed=false
    private var timeout:Runnable?=null
    fun invalidate(){generation++;pending=false;timeout?.let(handler::removeCallbacks);timeout=null}
    fun close(){invalidate();closed=true}
    fun handle() {
        if(closed||pending)return
        val ticket=++generation;pending=true
        fun current()= !closed&&pending&&generation==ticket
        fun finish(){if(current()){pending=false;timeout?.let(handler::removeCallbacks);timeout=null}}
        timeout=Runnable{finish()}.also{handler.postDelayed(it,15000)}
        val native=nativeCapture?.invoke()
        if(native!=null) {
            val revision=native.getLong("revision");val action=MPosSystemBackPolicy.choose(native)
            val request=JSONObject().put("revision",revision).put("action",action.name)
            try{evaluate("window.MPosCore?.SystemBack?.applyNative(${request})===true"){applied->
                if(!current())return@evaluate
                finish();if(applied=="true"&&action==MPosSystemBackPolicy.Action.BACKGROUND&&nativeCurrent?.invoke(revision)!=false)background()
            }}catch(_:Exception){finish()}
            return
        }
        try {
            evaluate("window.MPosCore?.SystemBack?.capture()??null") capture@{ raw ->
                if(!current())return@capture
                if(raw==null||raw=="null") {finish();rollback();return@capture}
                val snapshot=runCatching{JSONObject(JSONTokener(raw).nextValue() as String)}.getOrNull()
                val action=snapshot?.let{runCatching{MPosSystemBackPolicy.choose(it)}.getOrNull()}
                if(snapshot==null||action==null||snapshot.opt("token") !is Number){finish();return@capture}
                val request=JSONObject().put("token",snapshot.getLong("token")).put("action",action.name)
                try {
                    evaluate("window.MPosCore?.SystemBack?.apply(${request})===true") apply@{ applied ->
                        if(!current())return@apply
                        finish();if(applied=="true"&&action==MPosSystemBackPolicy.Action.BACKGROUND)background()
                    }
                }catch(_:Exception){finish()}
            }
        }catch(_:Exception){finish()}
    }
}
