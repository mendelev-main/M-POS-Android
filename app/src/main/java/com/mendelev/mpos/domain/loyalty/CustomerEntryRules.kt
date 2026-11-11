package com.mendelev.mpos.domain.loyalty

/** Web android-customer-input.js: fixed Belarus prefix, paste normalization, ASCII digits only. */
internal object CustomerEntryRules {
    fun suffix(value: String)=value.filter {it in '0'..'9'}.takeLast(4)
    fun national(value: String): String {
        val text=value.trim();val digits=text.filter {it in '0'..'9'}
        if(text.startsWith("+") && !text.startsWith("+375")) return ""
        return (if(text.startsWith("+375") || digits.length==12 && digits.startsWith("375")) digits.drop(3) else digits).take(9)
    }
    fun registration(name: String,phone: String): Pair<String,String> {
        val n=name.trim()
        require(n.isNotEmpty()) {"Введите имя клиента"}
        require(n.length<=100 && n.none {it.isISOControl()}) {"Имя клиента: до 100 символов"}
        val digits=national(phone)
        require(digits.length==9) {"Введите 9 цифр телефона после +375"}
        return n to "+375$digits"
    }
    fun matching(rows: List<LoyaltyCustomer>,query: String)=rows.filter {it.phone.filter {c->c in '0'..'9'}.endsWith(query)}
}
