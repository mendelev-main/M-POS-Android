package com.mendelev.mpos.domain.backup

import java.math.BigDecimal

class BackupFailure(val kind: String,message: String): IllegalArgumentException(message)
internal fun backupBad(kind: String="format"): Nothing=throw BackupFailure(kind,"Формат резервной копии не поддерживается или данные повреждены")
sealed interface BackupJson {
    class Obj(val fields: Map<String,BackupJson>): BackupJson
    class Arr(val values: List<BackupJson>): BackupJson
    class Str(val value: String): BackupJson
    class Num(val raw: String): BackupJson {fun decimal()=BigDecimal(raw)}
    class Bool(val value: Boolean): BackupJson
    data object Nil: BackupJson
}
/** Same strict grammar on Android and JVM; no Android JSONTokener coercions or Double conversion. */
internal class BackupJsonParser(private val text: String) {
    private var offset=0;private var nodes=0
    fun parse(): BackupJson {val value=value(0);space();if(offset!=text.length) backupBad();return value}
    private fun space() {while(offset<text.length && text[offset] in " \t\r\n") offset++}
    private fun take(c: Char): Boolean {if(offset<text.length && text[offset]==c) {offset++;return true};return false}
    private fun need(c: Char) {if(!take(c)) backupBad()}
    private fun value(depth: Int): BackupJson {
        if(depth>64 || ++nodes>400_000) backupBad("limit")
        space();if(offset>=text.length) backupBad()
        return when(text[offset]) {
            '{'->{offset++;space();val fields=linkedMapOf<String,BackupJson>()
                if(!take('}')) while(true) {
                    space();if(offset>=text.length || text[offset]!='"') backupBad()
                    val key=string();if(key in fields) backupBad("duplicate")
                    space();need(':');fields[key]=value(depth+1);space()
                    if(take('}')) break;need(',')
                };BackupJson.Obj(fields)
            }
            '['->{offset++;space();val rows=mutableListOf<BackupJson>()
                if(!take(']')) while(true) {
                    if(rows.size>=100_000) backupBad("limit")
                    rows.add(value(depth+1));space();if(take(']')) break;need(',')
                };BackupJson.Arr(rows.toList())
            }
            '"'->BackupJson.Str(string())
            't'->{literal("true");BackupJson.Bool(true)}
            'f'->{literal("false");BackupJson.Bool(false)}
            'n'->{literal("null");BackupJson.Nil}
            '-',in '0'..'9'->number()
            else->backupBad()
        }
    }
    private fun literal(word: String) {if(!text.startsWith(word,offset)) backupBad();offset+=word.length}
    private fun number(): BackupJson.Num {
        val start=offset;take('-')
        if(!take('0')) {if(offset>=text.length || text[offset] !in '1'..'9') backupBad();while(offset<text.length && text[offset] in '0'..'9') offset++}
        if(take('.')) digits()
        if(take('e') || take('E')) {if(!take('+')) take('-');digits()}
        val raw=text.substring(start,offset)
        if(raw.length>128) backupBad("limit")
        val n=try {BigDecimal(raw)} catch(_:Exception) {backupBad()}
        if(n.precision()>512 || n.scale().toLong() !in -512L..512L) backupBad("limit")
        return BackupJson.Num(raw)
    }
    private fun digits() {val start=offset;while(offset<text.length && text[offset] in '0'..'9') offset++;if(start==offset) backupBad()}
    private fun string(): String {
        need('"');val out=StringBuilder()
        while(offset<text.length) {
            val c=text[offset++]
            if(c=='"') {
                var i=0
                while(i<out.length) {val ch=out[i++];if(ch.isHighSurrogate()) {if(i>=out.length || !out[i++].isLowSurrogate()) backupBad()} else if(ch.isLowSurrogate()) backupBad()}
                return out.toString()
            }
            if(c<' ') backupBad()
            if(c!='\\') out.append(c) else {
                if(offset>=text.length) backupBad()
                out.append(when(val e=text[offset++]) {
                    '"','\\','/'->e;'b'->'\b';'f'->'\u000c';'n'->'\n';'r'->'\r';'t'->'\t'
                    'u'->{if(offset+4>text.length) backupBad();val hex=text.substring(offset,offset+4);if(hex.any {it.digitToIntOrNull(16)==null}) backupBad();offset+=4;hex.toInt(16).toChar()}
                    else->backupBad()
                })
            }
            if(out.length>8*1024*1024) backupBad("limit")
        };backupBad()
    }
}
