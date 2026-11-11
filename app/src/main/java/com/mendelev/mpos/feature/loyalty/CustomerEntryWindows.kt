package com.mendelev.mpos.feature.loyalty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.loyalty.*

@Composable internal fun CustomerPickerWindow(query: String,rows: List<LoyaltyCustomer>,searching: Boolean,busy: Boolean,error: String?,
    onQuery: (String)->Unit,onSelect: (LoyaltyCustomer)->Unit,onCreate: ()->Unit,onClose: ()->Unit) {
    MposDialog("",onClose,closable=!busy,maxWidth=560.dp,header={
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp)) {MposCloseButton(onClose,!busy)}
            Text("Клиент заказа",Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center)
            Surface(onClick=onCreate,enabled=!busy,shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
                modifier=Modifier.size(44.dp).testTag("customerCreate").semantics {contentDescription="Добавить нового клиента"}) {
                Box(contentAlignment=Alignment.Center) {MposPosIcon("add",Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onPrimary)}
            }
        }
    }) {
        CustomerPhoneField(query,onQuery,"Последние 4 цифры телефона","+375 XX XXX","1234","customerSearch",!busy,true)
        Column(Modifier.fillMaxWidth().heightIn(min=112.dp,max=330.dp).verticalScroll(rememberScrollState()).padding(2.dp).testTag("customerResults"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val note=when {
                query.length<4->"Введите последние 4 цифры номера"
                searching->"Поиск…"
                error!=null->"Нет связи с сервером. Повторите поиск."
                rows.isEmpty()->"Клиент не найден. Можно создать нового."
                else->null
            }
            if(note!=null) Box(Modifier.fillMaxWidth().heightIn(min=108.dp),contentAlignment=Alignment.Center) {
                Text(note,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
            } else rows.forEach {customer->
                Surface(onClick={onSelect(customer)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=68.dp).testTag("customer:"+customer.id),
                    shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(horizontal=13.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.size(44.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary) {
                            Box(contentAlignment=Alignment.Center) {Text(customer.name.trim().take(1).uppercase(),fontSize=17.sp,fontWeight=FontWeight.ExtraBold)}
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(customer.name,fontSize=15.sp,fontWeight=FontWeight.Bold)
                            Text(customer.phone,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Выбрать",fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable internal fun CustomerCreateWindow(name: String,phone: String,enroll: Boolean,busy: Boolean,error: String?,
    identityLocked: Boolean=false,onName: (String)->Unit,onPhone: (String)->Unit,onEnroll: (Boolean)->Unit,onBack: ()->Unit,onClose: ()->Unit,onCreate: ()->Unit) {
    MposDialog("Новый клиент",onClose,closable=!busy,maxWidth=460.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            CustomerPhoneField(name,onName,"Имя","","","customerName",!busy && !identityLocked,false)
            CustomerPhoneField(phone,onPhone,"Телефон","+375","29 123 45 67","customerPhone",!busy && !identityLocked,false)
            Surface(onClick={onEnroll(!enroll)},enabled=!busy,shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Checkbox(enroll,null,enabled=!busy,modifier=Modifier.size(22.dp).testTag("customerCreateEnroll"))
                    Text("После создания оформить участие в лояльности на кассе",fontSize=14.sp,fontWeight=FontWeight.Bold)
                }
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Назад",onBack,Modifier.weight(1f),enabled=!busy,style=ActionStyle.Secondary)
            MposButton("Создать",onCreate,Modifier.weight(1f).testTag("customerCreateSave"),enabled=!busy,busy=busy)
        }
    }
}

/** Android Web adapter uses 56px pill fields and a non-editable country prefix. */
@Composable private fun CustomerPhoneField(value: String,onChange: (String)->Unit,label: String,prefix: String,placeholder: String,tag: String,enabled: Boolean,suffix: Boolean) {
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(999.dp),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                if(prefix.isNotEmpty()) {
                    Text(prefix,Modifier.padding(horizontal=14.dp),fontSize=16.sp,fontWeight=FontWeight.Bold)
                    VerticalDivider(Modifier.fillMaxHeight(),color=MaterialTheme.colorScheme.outline)
                }
                BasicTextField(value,onChange,Modifier.weight(1f).padding(horizontal=12.dp).testTag(tag).semantics {contentDescription=label},
                    enabled=enabled,singleLine=true,cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle=TextStyle(color=MaterialTheme.colorScheme.onSurface,fontSize=20.sp,fontFamily=MaterialTheme.typography.bodyLarge.fontFamily,textAlign=if(suffix) TextAlign.Center else TextAlign.Start,letterSpacing=if(suffix) 2.sp else 0.sp),
                    keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=if(prefix.isNotEmpty()) KeyboardType.Phone else KeyboardType.Text,imeAction=if(suffix) ImeAction.Search else ImeAction.Next),
                    decorationBox={inner->Box {if(value.isEmpty()) Text(placeholder,fontSize=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);inner()}})
            }
        }
    }
}
