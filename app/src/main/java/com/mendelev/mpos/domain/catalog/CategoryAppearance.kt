package com.mendelev.mpos.domain.catalog

/** Category tile metadata; symbols are user-perceived Unicode characters. */
object CategoryAppearance {
    const val DEFAULT_COLOR = "#EEF1F5"
    val palette = listOf(DEFAULT_COLOR,"#DDEBFF","#DFF5E8","#FFF0C7","#FFE0E0","#E9DDFB","#DDF4F4","#F5E1D3","#E5E5E5","#DCE6F7")
    fun color(raw: String): String {
        val value=raw.trim().uppercase(java.util.Locale.ROOT)
        if(!Regex("#[0-9A-F]{6}").matches(value)) throw CatalogFailure("categoryColor","Выберите цвет категории")
        return value
    }
    fun symbol(raw: String): String {
        val value=raw.trim()
        if(value.length>256 || raw.any { Character.isISOControl(it) } || Regex("\\X").findAll(value).count()>3)
            throw CatalogFailure("categorySymbol","Обозначение: до 3 символов, без переносов строк")
        return value
    }
}
