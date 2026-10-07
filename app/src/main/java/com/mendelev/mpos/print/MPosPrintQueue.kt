package com.mendelev.mpos.print

import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** At most four endpoints run; one endpoint always has a single FIFO worker. */
class MPosPrintQueue<T>(
    private val endpoint:(T)->String,
    private val send:(T)->Unit,
    private val cancelled:(T)->Unit={},
    private val failed:(T,Throwable)->Unit={_,_->},
    private val capacity:Int=128,
    private val executor:ExecutorService=Executors.newFixedThreadPool(4),
) {
    private val queues=linkedMapOf<String,ArrayDeque<T>>()
    private var count=0
    private var closed=false
    @Synchronized fun submit(batch:List<T>):Boolean {
        if(closed||batch.size>capacity-count)return false
        for(job in batch){val key=endpoint(job);val fresh=key !in queues;val queue=queues.getOrPut(key){ArrayDeque()};queue.addLast(job);count++;if(fresh)executor.execute{drain(key)}}
        return true
    }
    private fun drain(key:String){
        while(true){
            val job=synchronized(this){val queue=queues[key];if(closed||queue==null||queue.isEmpty()){queues.remove(key);return};queue.removeFirst()}
            try{send(job)}catch(error:Exception){runCatching{failed(job,error)}}finally{synchronized(this){count--}}
        }
    }
    fun close(){
        val abandoned=synchronized(this){if(closed)return;closed=true;queues.values.flatMap{it.toList()}.also{count-=it.size;queues.clear()}}
        abandoned.forEach(cancelled);executor.shutdownNow()
    }
}
