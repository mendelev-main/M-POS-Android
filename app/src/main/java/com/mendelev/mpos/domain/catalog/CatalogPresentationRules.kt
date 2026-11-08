package com.mendelev.mpos.domain.catalog

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

object CatalogPresentationRules {
    private val collators=ThreadLocal.withInitial {Collator.getInstance(Locale.forLanguageTag("ru")).apply {strength=Collator.PRIMARY}}
    fun search(value: String)=Normalizer.normalize(value,Normalizer.Form.NFC).lowercase(Locale.forLanguageTag("ru")).replace('ё','е').replace(Regex("[\\p{Z}\\s]+")," ").trim()
    fun matches(name: String,category: String,query: String)=search(query).let {it.isEmpty() || (search(name)+" "+search(category)).contains(it)}
    fun compareNames(left: String,right: String): Int {
        val collator=collators.get()
        val a=Regex("[0-9]+|[^0-9]+").findAll(left).map {it.value}.toList();val b=Regex("[0-9]+|[^0-9]+").findAll(right).map {it.value}.toList()
        for(i in 0 until minOf(a.size,b.size)) {
            val c=if(a[i].all(Char::isDigit) && b[i].all(Char::isDigit)) a[i].toBigInteger().compareTo(b[i].toBigInteger()) else collator.compare(a[i],b[i])
            if(c!=0) return c
        }
        return a.size.compareTo(b.size)
    }
}
