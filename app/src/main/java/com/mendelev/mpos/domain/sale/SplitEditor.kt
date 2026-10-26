package com.mendelev.mpos.domain.sale

import com.mendelev.mpos.domain.catalog.CatalogFailure

/** IDs are local to one checkout. Persist nextId too: removed IDs must never be reused. */
data class SplitPart(val id: Long,val payment: PaymentInput,val paid: Boolean=false)
data class EditableSplit(val total: Long,val nextId: Long,val parts: List<SplitPart>)

/** Pure candidate edits only. Paid flags must come from the transactional progress repository. */
object SplitEditor {
    private fun invalid(message: String): Nothing=throw CatalogFailure("split",message)
    private fun sum(parts: List<SplitPart>): Long=try {
        parts.fold(0L) {amount,p->Math.addExact(amount,p.payment.amount)}
    } catch(_:ArithmeticException) {invalid("Сумма слишком велика")}

    fun validate(state: EditableSplit) {
        if(state.total<0 || state.nextId<=0 || state.parts.size !in 2..10 ||
            state.parts.map {it.id}.distinct().size!=state.parts.size ||
            state.parts.any {it.id<=0 || it.id>=state.nextId}) invalid("Некорректные части оплаты")
        state.parts.forEach {part->
            val p=part.payment
            if(p.amount<0 || (p.given!=null && p.given<0)) invalid("Сумма не может быть отрицательной")
            when(p.method) {
                "cash"->if(part.paid && (p.given==null || p.given<p.amount)) invalid("Проверьте принятые наличные")
                "card"->if(p.given!=null) invalid("У карты нет наличной суммы")
                else->invalid("Неизвестный способ оплаты")
            }
        }
        if(sum(state.parts)!=state.total) invalid("Сумма частей не совпадает с чеком")
    }

    fun create(total: Long,parts: List<PaymentInput>): EditableSplit =
        EditableSplit(total,parts.size.toLong()+1,parts.mapIndexed {i,p->SplitPart(i.toLong()+1,p)}).also(::validate)

    private fun editable(state: EditableSplit,id: Long): SplitPart {
        validate(state)
        val part=state.parts.find {it.id==id} ?: invalid("Часть оплаты не найдена")
        if(part.paid) invalid("Подтверждённую часть нельзя изменить")
        return part
    }

    private fun distribute(parts: List<SplitPart>,amount: Long): List<SplitPart> {
        if(parts.isEmpty()) {
            if(amount!=0L) invalid("Для остатка нужна неоплаченная часть")
            return emptyList()
        }
        val base=amount/parts.size;val remainder=amount%parts.size
        return parts.mapIndexed {i,p->
            // Even redistributed cash rows require fresh tender input; never retain stale change.
            p.copy(payment=p.payment.copy(amount=base+if(i<remainder) 1 else 0,given=null))
        }
    }

    fun resize(state: EditableSplit,count: Int): EditableSplit {
        validate(state)
        if(count !in 2..10) invalid("Разделите чек на 2–10 частей")
        if(count==state.parts.size) return state
        val paid=state.parts.filter {it.paid}
        if(count<paid.size) invalid("Нельзя удалить подтверждённые части")
        val unpaid=state.parts.filterNot {it.paid}.take(count-paid.size).toMutableList()
        var nextId=state.nextId
        while(unpaid.size<count-paid.size) {
            if(nextId==Long.MAX_VALUE) invalid("Закончились идентификаторы частей")
            unpaid+=SplitPart(nextId++,PaymentInput("cash",0))
        }
        return EditableSplit(state.total,nextId,paid+distribute(unpaid,state.total-sum(paid))).also(::validate)
    }

    fun amount(state: EditableSplit,id: Long,requested: Long): EditableSplit {
        val part=editable(state,id)
        if(requested<0) invalid("Сумма не может быть отрицательной")
        val index=state.parts.indexOf(part)
        val unpaidBefore=state.parts.take(index).filterNot {it.paid}
        val unpaidAfter=state.parts.drop(index+1).filterNot {it.paid}
        val remaining=state.total-sum(state.parts.filter {it.paid})
        val target=if(unpaidBefore.isEmpty() && unpaidAfter.isEmpty()) remaining
            else minOf(requested,remaining-sum(unpaidBefore))
        val redistributed=if(unpaidAfter.isNotEmpty()) distribute(unpaidAfter,remaining-sum(unpaidBefore)-target)
            else distribute(unpaidBefore,remaining-target)
        val replacements=redistributed.associateBy {it.id}+
            (id to part.copy(payment=part.payment.copy(amount=target,given=null)))
        return state.copy(parts=state.parts.map {replacements[it.id] ?: it}).also(::validate)
    }

    fun method(state: EditableSplit,id: Long,method: String): EditableSplit {
        val part=editable(state,id)
        if(method !in listOf("cash","card")) invalid("Неизвестный способ оплаты")
        if(method==part.payment.method) return state
        return state.copy(parts=state.parts.map {
            if(it.id==id) it.copy(payment=it.payment.copy(method=method,given=null)) else it
        }).also(::validate)
    }

    fun given(state: EditableSplit,id: Long,given: Long?): EditableSplit {
        val part=editable(state,id)
        if(part.payment.method!="cash") invalid("У карты нет наличной суммы")
        if(given!=null && given<part.payment.amount) invalid("Недостаточно внесённых наличных")
        return state.copy(parts=state.parts.map {
            if(it.id==id) it.copy(payment=it.payment.copy(given=given)) else it
        }).also(::validate)
    }
}


sealed interface SplitEdit {
    data class Count(val count: Int): SplitEdit
    data class Amount(val id: Long,val amount: Long): SplitEdit
    data class Method(val id: Long,val method: String): SplitEdit
    data class Given(val id: Long,val given: Long?): SplitEdit
    fun apply(state: EditableSplit): EditableSplit=when(this) {
        is Count->SplitEditor.resize(state,count)
        is Amount->SplitEditor.amount(state,id,amount)
        is Method->SplitEditor.method(state,id,method)
        is Given->SplitEditor.given(state,id,given)
    }
}
