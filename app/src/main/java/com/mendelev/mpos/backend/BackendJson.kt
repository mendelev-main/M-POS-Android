package com.mendelev.mpos.backend

import org.json.JSONObject

internal object BackendJson {
    fun obj(raw: String): JSONObject {
        require(raw.length<=1_048_576 && raw.trimStart().startsWith('{'))
        var depth=0;var quoted=false;var escape=false
        raw.forEach {c->
            if(quoted) {if(escape) escape=false else if(c=='\\') escape=true else if(c=='"') quoted=false}
            else when(c) {'"'->quoted=true;'[','{'->{depth++;require(depth<=64)};']','}'->{depth--;require(depth>=0)}}
        }
        require(depth==0 && !quoted)
        return JSONObject(raw)
    }
}
