package com.mendelev.mpos.core.access

/** Same masked-edge repair as Web AdministrationPasswordEditText.ACTION_SET_TEXT.
 * Remote controllers may append a real key to the masked accessibility value.
 * Never persist or expose the private buffer; malformed edits are refused unchanged.
 */
internal object RemotePasswordInput {
    fun restore(previous: String,incoming: String): String {
        if('\u2022' !in incoming) return incoming.take(128)
        val prefix=incoming.takeWhile {it=='\u2022'}.length
        val suffix=if(prefix==incoming.length) 0 else incoming.takeLastWhile {it=='\u2022'}.length
        if(prefix+suffix>previous.length) return previous
        val middle=incoming.substring(prefix,incoming.length-suffix)
        if('\u2022' in middle) return previous
        val restored=previous.take(prefix)+middle+if(suffix>0) previous.takeLast(suffix) else ""
        return if(restored.length<=128) restored else previous
    }
}
