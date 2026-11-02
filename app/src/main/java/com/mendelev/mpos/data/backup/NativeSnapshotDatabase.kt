package com.mendelev.mpos.data.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.mendelev.mpos.data.local.CatalogDatabase
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File

internal object NativeSnapshotDatabase {
    private val tables=setOf("categories","products","stock_balances","stock_movements","operation_results","audit_events","editor_drafts","app_config","recipe_components","modifier_groups","modifier_options","workspace_documents","shifts","shift_register","receipts","receipt_products","refunds","room_master_table")
    private const val IDENTITY="9bb6667a9d85501c211c4629dbdaa2d2"
    private fun quoted(name: String)="\""+name.replace("\"","\"\"")+"\""
    /** Clone committed tables, exact SQLite types and schema under one source transaction, including WAL data. */
    suspend fun capture(db: CatalogDatabase,photos: ProductPhotoStore,directory: File): NativeBackupFiles=db.withTransaction {
        val context=currentCoroutineContext();val source=db.openHelper.writableDatabase
        if(source.version!=NativeBackupArchive.SCHEMA_VERSION) throw BackupFailure("version","Версия базы не поддерживается")
        val schema=mutableListOf<Triple<String,String,String>>()
        source.query("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY type DESC,name").use {c->
            while(c.moveToNext()) schema+=Triple(c.getString(0),c.getString(1),c.getString(2))
        }
        val actual=schema.filter {it.first=="table"}.map {it.second}.toSet()
        if(actual-tables-setOf("android_metadata")!=emptySet<String>() || !actual.containsAll(tables) || schema.any {it.first !in setOf("table","index")}) throw BackupFailure("schema","Структура базы не поддерживается")
        val file=File(directory,"database.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file,null).use {target->
            target.execSQL("PRAGMA foreign_keys=OFF");target.execSQL("DROP TABLE IF EXISTS android_metadata")
            target.beginTransaction()
            try {
                schema.filter {it.first=="table"}.forEach {target.execSQL(it.third)}
                var rows=0L
                for(name in actual.sorted()) {
                    context.ensureActive()
                    source.query("SELECT * FROM ${quoted(name)}").use {cursor->
                        val insert="INSERT INTO ${quoted(name)} ("+cursor.columnNames.joinToString(",",transform=::quoted)+") VALUES ("+List(cursor.columnCount){"?"}.joinToString(",")+")"
                        target.compileStatement(insert).use {statement->
                            while(cursor.moveToNext()) {
                                context.ensureActive();if(++rows>2_000_000) throw BackupFailure("limit","Слишком большая база")
                                statement.clearBindings()
                                for(i in 0 until cursor.columnCount) when(cursor.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL->statement.bindNull(i+1)
                                    Cursor.FIELD_TYPE_INTEGER->statement.bindLong(i+1,cursor.getLong(i))
                                    Cursor.FIELD_TYPE_STRING->statement.bindString(i+1,cursor.getString(i))
                                    Cursor.FIELD_TYPE_BLOB->statement.bindBlob(i+1,cursor.getBlob(i))
                                    else->throw BackupFailure("schema","Неожиданный тип данных")
                                }
                                statement.executeInsert()
                            }
                        }
                    }
                }
                schema.filter {it.first=="index"}.forEach {target.execSQL(it.third)}
                target.version=NativeBackupArchive.SCHEMA_VERSION;target.setTransactionSuccessful()
            } finally {target.endTransaction()}
        }
        val refs=referencedPhotos(file)
        if(file.length()>NativeBackupArchive.MAX_DATABASE || refs.size>5000) throw BackupFailure("limit","Копия превышает ограничения")
        var total=file.length()
        refs.forEach {id->photos.requirePrepared(id);total+=photos.file(id).length();if(total>NativeBackupArchive.MAX_TOTAL) throw BackupFailure("limit","Копия превышает ограничения")}
        val copies=refs.associateWith {id->
            context.ensureActive();photos.requirePrepared(id)
            val copy=File(directory,id);photos.file(id).inputStream().use {input->java.io.FileOutputStream(copy).use {out->
                val buffer=ByteArray(32768);var count=0L
                while(true) {context.ensureActive();val n=input.read(buffer);if(n<0) break;count+=n;if(count>NativeBackupArchive.MAX_PHOTO) throw BackupFailure("photo","Фото превышает ограничения");out.write(buffer,0,n)}
                out.fd.sync()
            }};copy
        }
        // CleanupPhotos also takes a Room transaction: references/files cannot disappear during capture.
        NativeBackupFiles(file,copies,System.currentTimeMillis()).also {validate(it)}
    }
    private fun open(file: File)=SQLiteDatabase.openDatabase(file.absolutePath,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    private fun referencedPhotos(file: File): Set<String> = open(file).use {db->
        val refs=mutableSetOf<String>()
        db.rawQuery("SELECT photoId FROM products WHERE photoId IS NOT NULL",null).use {c->while(c.moveToNext()) refs+=c.getString(0)}
        db.rawQuery("SELECT payload FROM editor_drafts",null).use {c->while(c.moveToNext()) {
            val j=JSONObject(c.getString(0));if(j.has("photoId") && !j.isNull("photoId")) {
                val value=j.get("photoId");if(value !is String) throw BackupFailure("photo","Ссылка на фото повреждена")
                if(value.isNotEmpty() && value!="null") refs+=value
            }
        }}
        if(refs.any {!it.matches(Regex("[0-9a-f-]{36}\\.jpg"))}) throw BackupFailure("photo","Ссылка на фото повреждена")
        refs
    }
    fun validate(files: NativeBackupFiles): Map<String,Long> {
        if(files.database.length() !in 1..NativeBackupArchive.MAX_DATABASE) throw BackupFailure("size","База превышает ограничения")
        val counts=open(files.database).use {db->
            if(db.version!=NativeBackupArchive.SCHEMA_VERSION) throw BackupFailure("version","Версия базы не поддерживается")
            db.rawQuery("PRAGMA integrity_check",null).use {if(!it.moveToFirst() || it.getString(0)!="ok" || it.moveToNext()) throw BackupFailure("database","База повреждена")}
            db.rawQuery("PRAGMA foreign_key_check",null).use {if(it.moveToFirst()) throw BackupFailure("database","Связи базы повреждены")}
            val actual=mutableSetOf<String>()
            db.rawQuery("SELECT type,name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'",null).use {c->while(c.moveToNext()) {
                if(c.getString(0)=="table") actual+=c.getString(1)
                else if(c.getString(0)!="index") throw BackupFailure("schema","Структура базы не поддерживается")
            }}
            if(actual-tables-setOf("android_metadata")!=emptySet<String>() || !actual.containsAll(tables)) throw BackupFailure("schema","Структура базы не поддерживается")
            db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42",null).use {if(!it.moveToFirst() || it.getString(0)!=IDENTITY || it.moveToNext()) throw BackupFailure("schema","Структура базы не поддерживается")}
            tables.filter {it!="room_master_table"}.associateWith {name->db.rawQuery("SELECT COUNT(*) FROM ${quoted(name)}",null).use {c->c.moveToFirst();c.getLong(0)}}
        }
        if(referencedPhotos(files.database)!=files.photos.keys) throw BackupFailure("missingPhoto","Не все фотографии доступны")
        for(file in files.photos.values) {
            if(file.length() !in 1..NativeBackupArchive.MAX_PHOTO) throw BackupFailure("photo","Фото превышает ограничения")
            val b=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,b)
            if(b.outWidth<=0 || b.outHeight<=0 || b.outWidth.toLong()*b.outHeight>16_000_000 || b.outMimeType!="image/jpeg") throw BackupFailure("photo","Фото повреждено")
        }
        return counts
    }
    /** Logical target identity: independent of SQLite page layout, ZIP timestamps and device locale. */
    fun fingerprint(files: NativeBackupFiles,check: ()->Unit={}): String {
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        val sink=java.io.DataOutputStream(java.security.DigestOutputStream(object: java.io.OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray,off: Int,len: Int) {}
        },digest))
        fun bytes(b: ByteArray) {sink.writeInt(b.size);sink.write(b)}
        fun text(s: String)=bytes(s.toByteArray(Charsets.UTF_8))
        text("mpos-target-v1");sink.writeInt(NativeBackupArchive.SCHEMA_VERSION);var rowCount=0L
        open(files.database).use {db->
            for(table in tables.sorted()) {
                check();text(table)
                db.rawQuery("SELECT * FROM ${quoted(table)} LIMIT 0",null).use {shape->
                    val columns=shape.columnNames;text(columns.joinToString("|"))
                    db.rawQuery("SELECT * FROM ${quoted(table)} ORDER BY "+columns.joinToString(",",transform=::quoted),null).use {rows->
                        while(rows.moveToNext()) {
                            check();if(++rowCount>2_000_000) throw BackupFailure("limit","Слишком большая база");sink.writeByte(1)
                            for(i in columns.indices) {val type=rows.getType(i);sink.writeByte(type);when(type) {
                                Cursor.FIELD_TYPE_NULL->Unit
                                Cursor.FIELD_TYPE_INTEGER->sink.writeLong(rows.getLong(i))
                                Cursor.FIELD_TYPE_STRING->text(rows.getString(i))
                                Cursor.FIELD_TYPE_BLOB->bytes(rows.getBlob(i))
                                else->throw BackupFailure("schema","Неожиданный тип данных")
                            }}
                        }
                        sink.writeByte(0)
                    }
                }
            }
        }
        files.photos.toSortedMap().forEach {(id,file)->check();text(id);text(NativeBackupArchive.sha256(file))}
        sink.flush();return digest.digest().joinToString("") {"%02x".format(it)}
    }
    fun activity(files: NativeBackupFiles): NativeSnapshotActivity = open(files.database).use {db->
        val shifts=db.rawQuery("SELECT COUNT(*) FROM shifts WHERE activeSlot IS NOT NULL",null).use {it.moveToFirst();it.getInt(0)}
        try {
            val raw=db.rawQuery("SELECT payload FROM workspace_documents WHERE id='split-progress'",null).use {if(it.moveToFirst()) it.getString(0) else null}
            if(raw==null) NativeSnapshotActivity(shifts,0,0) else {
                val j=JSONObject(raw);require(j.getInt("version") in 1..2)
                if(j.getBoolean("completed")) NativeSnapshotActivity(shifts,0,0) else {
                    val saved=com.mendelev.mpos.data.local.SavedCheckout(j.getLong("checkoutRevision"),com.mendelev.mpos.data.local.CheckoutCodec.decode(j.getJSONObject("checkout").toString()))
                    val a=j.getJSONArray("paid");val paid=(0 until a.length()).map {a.getInt(it)}
                    require(saved.draft.parts.size in 2..10 && paid.isNotEmpty() && paid.distinct().size==paid.size && paid.all {it in saved.draft.parts.indices})
                    val progress=if(j.getInt("version")==1) com.mendelev.mpos.data.local.SplitProgress(0,saved,paid.toSet()) else {
                        val ids=j.getJSONArray("partIds");require(ids.length()==saved.draft.parts.size)
                        com.mendelev.mpos.data.local.SplitProgress(0,saved,paid.toSet(),(0 until ids.length()).map {ids.getLong(it)},j.getLong("nextPartId"),2)
                    }
                    com.mendelev.mpos.domain.sale.SplitEditor.validate(progress.editable())
                    NativeSnapshotActivity(shifts,1,progress.paidAmount)
                }
            }
        } catch(_:Exception) {NativeSnapshotActivity(shifts,null,null)}
    }

}
