package com.mendelev.mpos.workspace

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.mendelev.mpos.ui.MPosNativeTheme
import com.mendelev.mpos.shift.MPosShiftScreenController
import org.json.JSONObject

/** Native layout controls, dialogs and drag targets. Commands go straight to the Room FIFO. */
class MPosLayoutController(private val context:Context,private val host:FrameLayout,private val action:(JSONObject)->Unit) {
    private var model:JSONObject?=null
    private var token=""
    private var theme=MPosNativeTheme(context,false)
    private var panel:LinearLayout?=null
    private var dialog:AlertDialog?=null
    private var busy=false
    private var sequence=0L
    private var pending:String?=null
    private var reopenAdd=false
    private var nextParent:String?=null
    private var bounds:FrameLayout.LayoutParams?=null
    private val controls=mutableListOf<View>()
    fun handle(payload:JSONObject) {
        when(payload.optString("action")) {
            "layoutHide"->if(payload.optString("token")==token)hide()
            "layoutBack"->if(!busy){if(dialog!=null){dialog?.dismiss();dialog=null;notifyModal()}else if(model?.optString("parent")?.isNotEmpty()==true)read("")}
            "layoutShow"->{
                if(busy)return
                val frame=MPosShiftScreenController.bounds(payload,host.width,host.height)
                if(frame==null){action(JSONObject().put("action","fallback").put("token",payload.optString("token")));return}
                hide();token=payload.getString("token");theme=MPosNativeTheme(context,payload.optString("theme")=="dark")
                bounds=FrameLayout.LayoutParams(frame.width,frame.height).apply{leftMargin=frame.left;topMargin=frame.top}
                model=JSONObject(payload.getJSONObject("model").toString());draw()
                when(payload.optString("form")){"add"->addDialog();"folder"->folderDialog(payload.optString("id"));"move"->moveDialog(payload.getString("id"))}
            }
            "layoutResult"->if(payload.optString("requestId")==pending){
                pending=null;busy=false
                val result=payload.getJSONObject("result")
                if(result.optBoolean("ok")&&result.has("tiles")){model=JSONObject(result.toString());draw();if(reopenAdd){reopenAdd=false;addDialog()};notifyModal()}
                else {enable();Toast.makeText(context,result.optString("message","Не удалось изменить раскладку"),Toast.LENGTH_LONG).show()}
            }
            "layoutApplied"->if(payload.optString("requestId")==pending){pending=null;busy=false;dialog?.dismiss();dialog=null;notifyModal();val parent=nextParent?:model?.optString("parent")?:"";nextParent=null;read(parent)}
        }
    }
    private fun notifyModal(){action(JSONObject().put("action","modal").put("token",token).put("active",dialog!=null||model?.optString("parent")?.isNotEmpty()==true).put("parent",model?.optString("parent")?:""))}
    private fun button(label:String,primary:Boolean=false,danger:Boolean=false,onClick:()->Unit)=Button(theme.uiContext).apply {
        text=label;theme.button(this,primary=primary,destructive=danger);controls+=this
        setOnClickListener{if(!busy&&this in controls)onClick()}
    }
    private fun input(operation:String)=JSONObject().put("version",1).put("operation",operation)
        .put("expected",JSONObject(model!!.getJSONObject("expected").toString())).put("parent",model!!.getString("parent"))
    private fun read(parent:String) {
        if(busy||model==null)return
        dispatch("read",input("layoutView").put("parent",parent))
    }
    private fun commit(command:JSONObject) {
        if(busy||model==null)return
        reopenAdd=command.optString("operation")=="addTile"
        nextParent=if(command.optString("operation")=="removeFolder"&&command.optString("id")==model?.optString("parent"))"" else null
        dispatch("commit",input("layoutCommit").put("documentRevision",model!!.getString("documentRevision")).put("command",command))
    }
    private fun dispatch(kind:String,input:JSONObject) {
        busy=true;pending="$token-${++sequence}";enable()
        action(JSONObject().put("action",kind).put("token",token).put("requestId",pending).put("input",input))
    }
    private fun enable(){controls.forEach{it.isEnabled=!busy};dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=!busy;dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled=!busy;dialog?.setCancelable(!busy)}
    private fun draw() {
        panel?.let(host::removeView);controls.clear()
        val data=model?:return
        val body=LinearLayout(theme.uiContext).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(theme.bg);setPadding(theme.dp(12),theme.dp(12),theme.dp(12),theme.dp(12))}
        val title=TextView(theme.uiContext).apply{text=data.getString("title");theme.text(this,20f,700)};body.addView(title)
        val bar=LinearLayout(theme.uiContext)
        if(data.getString("parent").isNotEmpty())bar.addView(button("Закрыть папку"){read("")})
        else if(!data.getBoolean("root"))bar.addView(button("← Назад"){route("closeCategory")})
        bar.addView(button("Готово",primary=true){route("toggleEdit")})
        bar.addView(button("Обновить"){read(model?.optString("parent")?:"")})
        if(data.getBoolean("root"))bar.addView(button("＋ Изменить содержимое"){addDialog()})
        else if(data.getString("parent").isEmpty())bar.addView(button("Создать папку"){folderDialog("")})
        body.addView(HorizontalScrollView(theme.uiContext).apply{addView(bar);isHorizontalScrollBarEnabled=false})
        val scroll=ScrollView(theme.uiContext);val rows=LinearLayout(theme.uiContext).apply{orientation=LinearLayout.VERTICAL}
        val tiles=data.getJSONArray("tiles")
        if(data.getBoolean("root")) {
            val occupied=(0 until tiles.length()).map{tiles.getJSONObject(it).getInt("row")}.toSortedSet()
            val last=occupied.lastOrNull()?:0
            val targetRows=if(last in 0..128)(0..last+1).toSortedSet() else (occupied+0+(last.toLong()+1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).toSortedSet()
            for(row in targetRows){val line=LinearLayout(theme.uiContext)
                for(col in 0 until 5){val cell=FrameLayout(theme.uiContext).apply{minimumHeight=theme.dp(142)}
                    val tile=(0 until tiles.length()).map{tiles.getJSONObject(it)}.firstOrNull{it.getInt("row")==row&&it.getInt("col")==col}
                    if(tile!=null){val card=LinearLayout(theme.uiContext).apply{orientation=LinearLayout.VERTICAL;background=theme.shape(theme.surface,12,true);setPadding(theme.dp(8),theme.dp(8),theme.dp(8),theme.dp(8))}
                        card.addView(TextView(theme.uiContext).apply{text=tile.getString("label");theme.text(this,14f,600)})
                        card.addView(button("Удалить",danger=true){commit(JSONObject().put("operation","removeTile").put("index",tile.getInt("index")))})
                        card.setOnLongClickListener{if(busy)false else it.startDragAndDrop(ClipData.newPlainText("layout",tile.getInt("index").toString()),View.DragShadowBuilder(it),tile.getInt("index"),0)}
                        cell.addView(card,FrameLayout.LayoutParams(-1,-1))
                    }else cell.background=theme.shape(theme.bg,12,true)
                    cell.setOnDragListener{_,event->when(event.action){DragEvent.ACTION_DRAG_STARTED->!busy&&event.localState is Int;DragEvent.ACTION_DROP->{if(!busy)commit(JSONObject().put("operation","moveTile").put("index",event.localState as Int).put("col",col).put("row",row).put("cols",5));true};else->true}}
                    line.addView(cell,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{setMargins(theme.dp(4),theme.dp(4),theme.dp(4),theme.dp(4))})
                };rows.addView(line)}
        }else for(i in 0 until tiles.length()){
            val tile=tiles.getJSONObject(i);val row=LinearLayout(theme.uiContext).apply{orientation=LinearLayout.VERTICAL;background=theme.shape(theme.surface,12,true);setPadding(theme.dp(12),theme.dp(8),theme.dp(12),theme.dp(8))}
            row.addView(TextView(theme.uiContext).apply{text=tile.getString("label");theme.text(this,16f,600)})
            val actions=LinearLayout(theme.uiContext)
            if(tile.getString("type")=="folder"){
                actions.addView(button("Открыть"){read(tile.getString("id"))});actions.addView(button("Изменить"){folderDialog(tile.getString("id"))})
            }else actions.addView(button("Переместить"){moveDialog(tile.getString("id"))})
            row.addView(actions)
            row.setOnLongClickListener{if(busy||model?.getJSONObject("expected")?.optString("search")?.isNotEmpty()==true)false else it.startDragAndDrop(ClipData.newPlainText("layout",i.toString()),View.DragShadowBuilder(it),tile,0)}
            row.setOnDragListener{_,event->when(event.action){DragEvent.ACTION_DRAG_STARTED->!busy&&event.localState is JSONObject;DragEvent.ACTION_DROP->{if(!busy){val source=event.localState as JSONObject;commit(JSONObject().put("operation","reorder").put("type",source.getString("type")).put("id",source.get("id")).put("targetIndex",i))};true};else->true}}
            rows.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=theme.dp(8)})
        }
        if(tiles.length()==0)rows.addView(TextView(theme.uiContext).apply{text="В этой области пока нет плиток";theme.text(this,16f,secondary=true)})
        scroll.addView(rows);body.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));host.addView(body,bounds);panel=body;enable();notifyModal()
    }
    private fun route(operation:String){if(busy)return;busy=true;enable();action(JSONObject().put("action","route").put("token",token).put("operation",operation))}
    private fun form(title:String,content:View,onSave:()->Unit) {
        dialog?.dismiss()
        val view=AlertDialog.Builder(theme.uiContext).setTitle(title).setView(content).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create()
        dialog=view;view.setOnDismissListener{if(dialog===view){dialog=null;notifyModal()}}
        view.setOnShowListener{view.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{if(!busy)onSave()};enable()};view.show();theme.dialog(view);notifyModal()
    }
    private fun addDialog() {
        val choices=model!!.getJSONArray("choices")
        val list=ListView(theme.uiContext).apply {
            adapter=object:ArrayAdapter<String>(theme.uiContext,android.R.layout.simple_list_item_1,(0 until choices.length()).map{choices.getJSONObject(it).getString("label")}) {
                override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=(convertView as? TextView?:TextView(theme.uiContext)).apply {
                    text="＋ "+getItem(position);theme.text(this);minHeight=theme.dp(48);setPadding(theme.dp(16),theme.dp(12),theme.dp(16),theme.dp(12))
                }
            }
            setOnItemClickListener{_,_,position,_->if(!busy){val choice=choices.getJSONObject(position);commit(JSONObject().put("operation","addTile").put("type",choice.getString("type")).put("id",choice.get("id")))}}
        }
        controls+=list
        dialog?.dismiss();val view=AlertDialog.Builder(theme.uiContext).setTitle("Настройка рабочей зоны").setView(list).setPositiveButton("Готово",null).create()
        dialog=view;view.setOnDismissListener{if(dialog===view){dialog=null;notifyModal()}};view.show();theme.dialog(view);notifyModal()
    }
    private fun folderDialog(id:String) {
        val folders=model!!.getJSONArray("folders");val folder=(0 until folders.length()).map{folders.getJSONObject(it)}.firstOrNull{it.opt("id")==id}
        val body=LinearLayout(theme.uiContext).apply{orientation=LinearLayout.VERTICAL;setPadding(theme.dp(20),theme.dp(12),theme.dp(20),theme.dp(12))}
        val name=EditText(theme.uiContext).apply{setText(folder?.optString("name")?:"");hint="Название";theme.text(this);minHeight=theme.dp(48);background=theme.shape(theme.surface,12,true)}
        controls+=name;body.addView(name)
        if(folder!=null)body.addView(button("Удалить папку",danger=true){
            dialog?.dismiss();dialog=null
            val confirm=AlertDialog.Builder(theme.uiContext).setTitle("Удалить папку?").setMessage("Товары вернутся в корень категории.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",null).create()
            dialog=confirm;confirm.setOnDismissListener{if(dialog===confirm){dialog=null;notifyModal()}};confirm.setOnShowListener{confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{if(!busy)commit(JSONObject().put("operation","removeFolder").put("id",id))}};confirm.show();theme.dialog(confirm);notifyModal()
        })
        form(if(folder==null)"Новая папка" else "Изменить папку",body){commit(JSONObject().put("operation","saveFolder").put("id",id).put("name",name.text.toString()))}
    }
    private fun moveDialog(id:String) {
        val folders=model!!.getJSONArray("folders");val rows=(0 until folders.length()).map{folders.getJSONObject(it)}
        val picker=Spinner(theme.uiContext).apply{minimumHeight=theme.dp(48);background=theme.shape(theme.surface,12,true)
            adapter=object:ArrayAdapter<String>(theme.uiContext,android.R.layout.simple_spinner_dropdown_item,listOf("Корень категории")+rows.map{it.getString("name")}){
                private fun label(position:Int)=TextView(theme.uiContext).apply{text=getItem(position);theme.text(this);minHeight=theme.dp(48);setPadding(theme.dp(12),theme.dp(8),theme.dp(12),theme.dp(8))}
                override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=label(position)
                override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View=label(position)
            }}
        val tile=(0 until model!!.getJSONArray("tiles").length()).map{model!!.getJSONArray("tiles").getJSONObject(it)}.firstOrNull{it.opt("id")==id}
        picker.setSelection(rows.indexOfFirst{it.opt("id")==tile?.opt("parentId")}.let{if(it<0)0 else it+1});controls+=picker
        form("Переместить товар",picker){commit(JSONObject().put("operation","moveProduct").put("id",id).put("folderId",if(picker.selectedItemPosition==0)"" else rows[picker.selectedItemPosition-1].getString("id")))}
    }
    fun hide(){pending=null;busy=false;reopenAdd=false;nextParent=null;dialog?.dismiss();dialog=null;panel?.let(host::removeView);panel=null;controls.clear();model=null;token=""}
}
