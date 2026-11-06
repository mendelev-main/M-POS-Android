package com.mendelev.mpos.data.recovery

import androidx.sqlite.db.SupportSQLiteDatabase
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** Public SQLite metadata only; independent of Room's restricted implementation utilities. */
internal object NativeSchema {
    private fun quote(s: String)="\""+s.replace("\"","\"\"")+"\""
    private fun rows(query: (String)->Cursor,sql: String): List<Map<String,String?>> = query(sql).use {c->
        buildList {while(c.moveToNext()) add(c.columnNames.indices.associate {i->c.getColumnName(i) to if(c.isNull(i)) null else c.getString(i)})}
    }
    private fun default(raw: String?): String? {
        var s=raw?.trim() ?: return null
        while(s.startsWith("(") && s.endsWith(")")) s=s.substring(1,s.length-1).trim()
        return s
    }
    fun equal(expected: SupportSQLiteDatabase,source: SQLiteDatabase,table: String): Boolean {
        val a: (String)->Cursor={expected.query(it)}
        val b: (String)->Cursor={source.rawQuery(it,null)}
        fun columns(db: (String)->Cursor)=rows(db,"PRAGMA table_info(${quote(table)})").associate {r->r.getValue("name") to listOf(r["type"]?.uppercase(java.util.Locale.ROOT),r["notnull"],default(r["dflt_value"]),r["pk"])}
        fun foreign(db: (String)->Cursor)=rows(db,"PRAGMA foreign_key_list(${quote(table)})").groupBy {it["id"]}.values.map {group->group.sortedBy {it["seq"]!!.toInt()}.map {r->listOf(r["table"],r["from"],r["to"],r["on_update"],r["on_delete"],r["match"])}.toString()}.sorted()
        fun indices(db: (String)->Cursor)=rows(db,"PRAGMA index_list(${quote(table)})").filter {it["origin"]!="pk"}.associate {r->
            val name=r.getValue("name")!!
            name to (listOf(r["unique"],r["origin"],r["partial"]).toString()+rows(db,"PRAGMA index_xinfo(${quote(name)})").filter {it["key"]=="1"}.map {listOf(it["name"],it["desc"],it["coll"])}.toString())
        }
        val sql=rows(b,"SELECT sql FROM sqlite_master WHERE type='table' AND name="+"'"+table.replace("'","''")+"'").single()["sql"] ?: return false
        if(Regex("\\b(CHECK|GENERATED|VIRTUAL|STRICT|WITHOUT|AUTOINCREMENT|COLLATE|CONFLICT|DEFERRABLE|INITIALLY)\\b",RegexOption.IGNORE_CASE).containsMatchIn(sql) || !sql.trim().endsWith(")")) return false
        return columns(a)==columns(b) && foreign(a)==foreign(b) && indices(a)==indices(b)
    }
}
