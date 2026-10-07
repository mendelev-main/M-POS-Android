package com.mendelev.mpos.print

import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.MPosOrderStorage
import com.mendelev.mpos.data.MPosStorageQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Native planning/Room admission is FIFO, network work is bounded by endpoint. */
class MPosPrintService(
    private val database:MPosDatabase,
    scope:CoroutineScope,
    private val onEvent:(JSONObject)->Unit,
    private val send:(JSONObject)->Unit,
    private val stop:()->Unit={},
    private val notify:()->Unit={},
) {
    private val repository=MPosPrintJobRepository(database)
    private val intake=MPosStorageQueue(scope)
    @Volatile private var closed=false
    private var initialized=false
    private val network=MPosPrintQueue<JSONObject>(
        endpoint={it.getJSONObject("order").optString("__networkPrinterIp").trim()+":"+it.getJSONObject("order").optInt("__networkPrinterPort",9100)},
        send=::transmit,
        cancelled={job->runBlocking{runCatching{repository.transition(job.getString("id"),"cancelled")}}},
        failed={job,_->event("printError","network_error","Не удалось завершить задание печати",job)},
    )
    fun handle(payload:JSONObject){
        if(payload.optJSONObject("order")?.optString("__notificationSound")?.isNotBlank()==true){notify();return}
        val raw=payload.toString()
        if(closed||!intake.submit({event("printError","network_error","Не удалось сохранить задание печати");admission(payload.optString("requestId"),false,0)}){prepare(JSONObject(raw))}){event("printError","network_error","Очередь печати заполнена или закрыта");admission(payload.optString("requestId"),false,0)}
    }
    private suspend fun prepare(input:JSONObject){
        if(closed)return
        if(!initialized){repository.recover();initialized=true}
        val request=if(input.optString("action")=="routePrint")input else JSONObject().put("version",1).put("trigger","direct").put("order",input.getJSONObject("order")).put("requestId",input.optString("requestId"))
        if(request.optString("trigger")=="completed"){
            val order=MPosOrderStorage(database).receipt(request.getJSONObject("order").opt("id"));check(order!=null){"Оплаченный чек не найден"};request.put("order",order)
        }
        val planned=MPosPrintPlan.calculate(request)
        if(planned.length()==0){if(request.optString("trigger")=="shift-close")event("printError","network_error","Чековый принтер не настроен");admission(request.optString("requestId"),true,0);return}
        val jobs=repository.append(request,planned)
        if(!network.submit(jobs)){for(job in jobs)repository.transition(job.getString("id"),"cancelled");event("printError","network_error","Очередь печати заполнена или закрыта");admission(request.optString("requestId"),false,0)}
        else admission(request.optString("requestId"),true,jobs.size)
    }
    private fun transmit(job:JSONObject)=runBlocking {
        if(closed){repository.transition(job.getString("id"),"cancelled");return@runBlocking}
        try{repository.transition(job.getString("id"),"sending")}
        catch(error:Exception){runCatching{repository.transition(job.getString("id"),"cancelled")};event("printError","network_error","Не удалось сохранить начало печати. Данные не отправлены",job);return@runBlocking}
        if(!MPosPrintPlan.validEndpoint(job.getJSONObject("order"))){repository.transition(job.getString("id"),"failed");event("printError","network_error","Неверный IP-адрес принтера",job);return@runBlocking}
        try{
            check(!closed){"Печать остановлена"};send(job.getJSONObject("order"))
        }catch(error:Exception){
            // TCP may have accepted bytes before any exception: never infer safe replay.
            runCatching{repository.transition(job.getString("id"),"uncertain")}
            event("printError","network_error","Ошибка печати. Проверьте принтер перед повторной отправкой",job);return@runBlocking
        }
        try{repository.transition(job.getString("id"),"sent")}
        catch(error:Exception){event("printError","network_error","Данные отправлены, но статус печати не сохранён. Проверьте принтер перед повтором",job);return@runBlocking}
        event("printed","network_printed",if(job.getJSONObject("order").optBoolean("__networkTest"))"Пробная печать отправлена" else "Чек отправлен на принтер",job)
    }
    fun ready()=event("status","network_ready","Сетевая печать готова")
    fun close(){closed=true;intake.close();stop();network.close()}
    private fun admission(requestId:String,ok:Boolean,count:Int){if(!closed)onEvent(JSONObject().put("type","printAdmission").put("requestId",requestId).put("ok",ok).put("count",count))}
    private fun event(type:String,status:String,message:String,job:JSONObject?=null){
        if(closed)return
        onEvent(JSONObject().put("type",type).put("status",status).put("message",message).apply{job?.let{put("jobId",it.getString("id")).put("requestId",it.optString("requestId"))}})
    }
}
