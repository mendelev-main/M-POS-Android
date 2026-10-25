package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.*

internal data class WorkspaceDrag(val modifier: Modifier,val source: Int?,val target: Int?)

/** Long press distinguishes editing from scrolling/clicking. Disposal/revision changes cancel the gesture. */
@Composable internal fun workspaceDrag(state: LazyGridState,enabled: Boolean,revision: Long,context: String,
    onDrop: (Int,Int)->Unit): WorkspaceDrag {
    var source by remember {mutableStateOf<Int?>(null)};var target by remember {mutableStateOf<Int?>(null)}
    val drop by rememberUpdatedState(onDrop)
    val scope=rememberCoroutineScope()
    var scrolling by remember {mutableStateOf<Job?>(null)}
    fun hit(p: Offset)=state.layoutInfo.visibleItemsInfo.firstOrNull {
        p.x>=it.offset.x && p.x<it.offset.x+it.size.width && p.y>=it.offset.y && p.y<it.offset.y+it.size.height
    }?.index
    val modifier=if(!enabled) Modifier else Modifier.pointerInput(state,revision,context) {
        var position=Offset.Zero
        try {
            detectDragGesturesAfterLongPress(
                onDragStart={position=it;source=hit(it);target=source},
                onDragCancel={source=null;target=null;scrolling?.cancel();scrolling=null},
                onDragEnd={
                    val from=source;val to=hit(position)
                    scrolling?.cancel();scrolling=null;source=null;target=null
                    if(from!=null && to!=null && from!=to) drop(from,to)
                },
                onDrag={change,delta->
                    change.consume();position+=delta;target=hit(position)
                    val direction=when {position.y<48f->-1;position.y>state.layoutInfo.viewportEndOffset-48f->1;else->0}
                    scrolling?.cancel();scrolling=null
                    if(source!=null && direction!=0) scrolling=scope.launch {
                        while(isActive) {if(state.scrollBy(direction*16f)==0f) break;delay(16);target=hit(position)}
                    }
                }
            )
        } finally {source=null;target=null;scrolling?.cancel();scrolling=null}
    }
    DisposableEffect(enabled,revision,context) {onDispose {scrolling?.cancel();scrolling=null;source=null;target=null}}
    return WorkspaceDrag(modifier,source,target)
}
