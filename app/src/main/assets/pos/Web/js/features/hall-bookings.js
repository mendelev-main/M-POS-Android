/* Hall and bookings runtime. Compatibility globals are intentional during gradual migration. */
function bookingDateTime(date,time){
  return `${date}T${time||'00:00'}:00`;
}
function bookingOverlap(startA,endA,startB,endB){
  return startA < endB && endA > startB;
}
function bookingWindow(dateOverride=null,timeOverride=null,durationOverride=null){
  const date=dateOverride || state.bookingDate || localDateString(new Date());
  const time=timeOverride || state.bookingTime || '19:00';
  const duration=Number(durationOverride || state.bookingDuration || 120);
  const start=new Date(bookingDateTime(date,time));
  const end=new Date(start.getTime() + duration*60000);
  return {date,time,duration,start,end};
}
function tableBookings(tableId, date){
  return state.bookings.filter(b=>b.tableId===tableId && b.date===date && b.status!=='cancelled')
    .sort((a,b)=>String(a.startAt).localeCompare(String(b.startAt)));
}
function tableBusyAt(tableId,start,end){
  return state.bookings.some(b=>b.tableId===tableId && b.status!=='cancelled' && bookingOverlap(start,end,new Date(b.startAt),new Date(b.endAt)));
}
function hallTableLabel(t){ return t.name || `Стол ${t.number||''}`.trim(); }
function saveHall(){ saveKey('hallTables',state.hallTables); }
function saveBookings(){ saveKey('bookings',state.bookings); }
function ensureBookingDate(){ if(!state.bookingDate) state.bookingDate=localDateString(new Date()); }
function openCreateHallTableModal(shape){
  const normalized=shape==='rectangle'?'rectangle':'square';
  const nextNumber=Math.max(0,...state.hallTables.map(t=>Number(t.number)||0))+1;
  showModal(`<div class="modal-title">${normalized==='square'?'Квадратный стол':'Прямоугольный стол'}</div>
    <div class="settings-note">Задайте название стола. Его можно будет использовать для быстрого поиска брони.</div>
    <div class="field"><label>Название стола</label><input id="new-hall-table-name" value="Стол ${nextNumber}" maxlength="30" autofocus></div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal();openHallEditMenu()">Отмена</button><button class="btn btn-primary" onclick="createHallTable('${normalized}')">Добавить стол</button></div>`);
}
function createHallTable(shape){
  const normalized=shape==='rectangle'?'rectangle':'square';
  const n=Math.max(0,...state.hallTables.map(t=>Number(t.number)||0))+1;
  const name=(document.getElementById('new-hall-table-name')?.value||'').trim();
  if(!name){flash('Введите название стола');return;}
  const col=(n-1)%4, row=Math.floor((n-1)/4)%5;
  const table={id:uid(),number:n,name,shape:normalized,rotation:0,x:7+col*23,y:8+row*17};
  state.hallTables.push(table); state.selectedHallTableId=table.id; saveHall(); closeModal(); render(); flash('Стол добавлен');
}
function addHallTable(shape){ openCreateHallTableModal(shape); }
function deleteHallTable(id){
  const t=state.hallTables.find(x=>x.id===id); if(!t) return;
  showModal(`<div class="modal-title">Удалить ${escapeHtml(hallTableLabel(t))}?</div><div class="settings-note">Все бронирования этого стола также будут удалены.</div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn booking-danger" onclick="confirmDeleteHallTable('${id}')">Удалить</button></div>`);
}
function confirmDeleteHallTable(id){
  state.hallTables=state.hallTables.filter(t=>t.id!==id); state.bookings=state.bookings.filter(b=>b.tableId!==id); if(state.selectedHallTableId===id) state.selectedHallTableId=null; saveHall(); saveBookings(); closeModal(); render();
}
function rotateHallTable(id,delta){
  const t=state.hallTables.find(x=>x.id===id); if(!t)return;
  t.rotation=((Number(t.rotation)||0)+delta+360)%360;
  saveHall(); closeModal(); render();
}
function saveHallTableEdits(id){
  const t=state.hallTables.find(x=>x.id===id); if(!t)return;
  const name=(document.getElementById('edit-hall-table-name')?.value||'').trim();
  if(!name){flash('Введите название стола');return;}
  t.name=name;
  saveHall(); closeModal(); render(); flash('Стол изменён');
}
function openHallTableEditModal(id){
  const t=state.hallTables.find(x=>x.id===id); if(!t)return;
  showModal(`<div class="modal-title">Редактирование стола</div>
    <div class="settings-note">Измените название, ориентацию или удалите стол.</div>
    <div class="field"><label>Название стола</label><input id="edit-hall-table-name" value="${escapeAttr(hallTableLabel(t))}" maxlength="30" autofocus></div>
    <div class="hall-rotation-actions">
      <button class="btn btn-secondary" onclick="rotateHallTable('${id}',-90)">↶ Повернуть</button>
      <button class="btn btn-secondary" onclick="rotateHallTable('${id}',90)">↷ Повернуть</button>
    </div>
    <button class="btn booking-danger hall-full-width-action" onclick="deleteHallTable('${id}')">Удалить стол</button>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="saveHallTableEdits('${id}')">Сохранить</button></div>`);
}
function selectHallTable(id){
  state.selectedHallTableId=id;
  render();
}
function handleHallTableClick(e,id){
  if(state._hallSuppressClick){state._hallSuppressClick=false;return;}
  if(state.hallEditMode){openHallTableEditModal(id);return;}
  selectHallTable(id);
}
function enableHallEditMode(){
  state.hallEditMode=true;
  state._hallDrag=null;
  closeModal();
  render();
  flash('Режим редактирования включён');
}
function finishHallEditMode(){
  state.hallEditMode=false;
  state._hallDrag=null;
  closeModal();
  render();
  flash('Режим редактирования выключен');
}
function openHallEditMenu(){
  showModal(`<div class="modal-title">Редактирование карты</div>
    <div class="settings-note hall-edit-note">Добавьте стол или включите режим редактирования. В обычном режиме столы не перемещаются.</div>
    <div class="hall-shape-actions">
      <button class="btn" onclick="addHallTable('square')">＋ Квадратный стол</button>
      <button class="btn" onclick="addHallTable('rectangle')">＋ Прямоугольный стол</button>
    </div>
    ${state.hallEditMode
      ? `<button class="btn btn-secondary hall-full-width-action" onclick="finishHallEditMode()">Завершить редактирование</button>`
      : `<button class="btn btn-secondary hall-full-width-action" onclick="enableHallEditMode()">Редактировать карту</button>`}`);
}
function setBookingDate(v){ state.bookingDate=v; render(); }
function setBookingTime(v){ state.bookingTime=v||'19:00'; refreshBookingTimeSummary(); }
function setBookingDuration(v){ state.bookingDuration=Number(v)||120; refreshBookingTimeSummary(); }
function bookingStatusForTable(t){ const date=state.bookingDate || localDateString(new Date()); return tableBookings(t.id,date).length>0; }
function startBookingForSelected(){
  const t=state.hallTables.find(x=>x.id===state.selectedHallTableId); if(!t){flash('Выберите стол на карте');return;}
  const w=bookingWindow();
  showModal(`<div class="modal-title">Новое бронирование</div>
    <div class="settings-note">${escapeHtml(hallTableLabel(t))} · ${escapeHtml(w.date)}</div>
    <div class="grid-2 booking-time-fields">
      <div class="field"><label>Время начала</label><input id="booking-time-input" type="time" value="${escapeAttr(w.time)}" onchange="setBookingTime(this.value)"></div>
      <div class="field"><label>Длительность</label><select id="booking-duration-input" onchange="setBookingDuration(this.value)">${[30,60,90,120,150,180,240].map(m=>`<option value="${m}" ${m===Number(w.duration)?'selected':''}>${m<60?m+' мин':Math.floor(m/60)+' ч'+(m%60?' '+m%60+' мин':'')}</option>`).join('')}</select></div>
    </div>
    <div class="booking-time-summary">Бронь: <strong id="booking-time-summary">${escapeHtml(w.time)}–${escapeHtml(new Date(w.end).toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'}))}</strong></div>
    <div class="field"><label>Имя гостя</label><input id="booking-name-input" placeholder="Имя"></div>
    <div class="field"><label>Телефон</label><input id="booking-phone-input" placeholder="Телефон"></div>
    <div class="field"><label>Количество гостей</label>
      <div class="booking-guests-stepper">
        <button type="button" onclick="changeBookingGuests(-1)">−</button>
        <span id="booking-guests-value">2</span>
        <button type="button" onclick="changeBookingGuests(1)">+</button>
      </div>
      <input id="booking-guests-input" type="hidden" value="2">
    </div>
    <div class="field"><label>Комментарий</label><textarea id="booking-note-input" rows="3" placeholder="Пожелания гостя"></textarea></div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="saveNewBooking('${t.id}')">Забронировать</button></div>`);
}
function changeBookingGuests(delta){
  const input=document.getElementById('booking-guests-input');
  const out=document.getElementById('booking-guests-value');
  const current=Math.max(1,Math.min(30,Number(input?.value||2)));
  const next=Math.max(1,Math.min(30,current+Number(delta||0)));
  if(input) input.value=String(next);
  if(out) out.textContent=String(next);
}
function changeEditBookingGuests(id,delta){
  const input=document.getElementById('edit-booking-guests');
  const out=document.getElementById('edit-booking-guests-value');
  const current=Math.max(1,Math.min(30,Number(input?.value||2)));
  const next=Math.max(1,Math.min(30,current+Number(delta||0)));
  if(input) input.value=String(next);
  if(out) out.textContent=String(next);
}
function refreshBookingTimeSummary(){
  const time=document.getElementById('booking-time-input')?.value || state.bookingTime || '19:00';
  const duration=Number(document.getElementById('booking-duration-input')?.value || state.bookingDuration || 120);
  const w=bookingWindow(state.bookingDate,time,duration);
  const out=document.getElementById('booking-time-summary');
  if(out) out.textContent=`${time}–${w.end.toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'})}`;
}
function saveNewBooking(tableId){
  const name=(document.getElementById('booking-name-input')?.value||'').trim();
  const phone=(document.getElementById('booking-phone-input')?.value||'').trim();
  const guests=Math.max(1,Number(document.getElementById('booking-guests-input')?.value||2));
  const note=(document.getElementById('booking-note-input')?.value||'').trim();
  const time=(document.getElementById('booking-time-input')?.value||state.bookingTime||'19:00');
  const duration=Number(document.getElementById('booking-duration-input')?.value||state.bookingDuration||120);
  const w=bookingWindow(state.bookingDate,time,duration);
  if(!name){flash('Введите имя гостя');return;}
  if(tableBusyAt(tableId,w.start,w.end)){flash('На выбранное время стол уже забронирован');return;}
  state.bookingTime=time; state.bookingDuration=duration;
  state.bookings.push({id:uid(),tableId,date:w.date,startAt:w.start.toISOString(),endAt:w.end.toISOString(),guestName:name,phone,guests,note,status:'confirmed',createdAt:Date.now()});
  saveBookings(); closeModal(); render(); flash('Бронирование создано');
}
function cancelBooking(id){
  const b=state.bookings.find(x=>x.id===id); if(!b)return;
  b.status='cancelled'; saveBookings(); render(); flash('Бронирование отменено');
}
function editBooking(id){
  const b=state.bookings.find(x=>x.id===id); if(!b)return;
  const st=new Date(b.startAt); const en=new Date(b.endAt); const mins=Math.max(30,Math.round((en-st)/60000));
  const time=st.toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'});
  showModal(`<div class="modal-title">Бронирование</div>
    <div class="grid-2 booking-time-fields">
      <div class="field"><label>Время начала</label><input id="edit-booking-time" type="time" value="${escapeAttr(time)}"></div>
      <div class="field"><label>Длительность</label><select id="edit-booking-duration">${[30,60,90,120,150,180,240].map(m=>`<option value="${m}" ${m===mins?'selected':''}>${m<60?m+' мин':Math.floor(m/60)+' ч'+(m%60?' '+m%60+' мин':'')}</option>`).join('')}</select></div>
    </div>
    <div class="field"><label>Имя гостя</label><input id="edit-booking-name" value="${escapeAttr(b.guestName||'')}"></div>
    <div class="field"><label>Телефон</label><input id="edit-booking-phone" value="${escapeAttr(b.phone||'')}"></div>
    <div class="field"><label>Количество гостей</label>
      <div class="booking-guests-stepper">
        <button type="button" onclick="changeEditBookingGuests('${id}',-1)">−</button>
        <span id="edit-booking-guests-value">${Number(b.guests||2)}</span>
        <button type="button" onclick="changeEditBookingGuests('${id}',1)">+</button>
      </div>
      <input id="edit-booking-guests" type="hidden" value="${Number(b.guests||2)}">
    </div>
    <div class="field"><label>Комментарий</label><textarea id="edit-booking-note" rows="3">${escapeHtml(b.note||'')}</textarea></div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Закрыть</button><button class="btn btn-primary" onclick="saveEditedBooking('${id}')">Сохранить</button></div>`);
}
function saveEditedBooking(id){
  const b=state.bookings.find(x=>x.id===id); if(!b)return;
  const newTime=(document.getElementById('edit-booking-time')?.value||new Date(b.startAt).toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'}));
  const mins=Math.max(30,Number(document.getElementById('edit-booking-duration')?.value||120));
  const stOld=new Date(b.startAt);
  const date=b.date || localDateString(stOld);
  const w=bookingWindow(date,newTime,mins);
  if(state.bookings.some(x=>x.id!==id && x.tableId===b.tableId && x.status!=='cancelled' && bookingOverlap(w.start,w.end,new Date(x.startAt),new Date(x.endAt)))){flash('На выбранное время стол уже забронирован');return;}
  b.guestName=(document.getElementById('edit-booking-name')?.value||'').trim(); b.phone=(document.getElementById('edit-booking-phone')?.value||'').trim(); b.guests=Math.max(1,Number(document.getElementById('edit-booking-guests')?.value||2)); b.note=(document.getElementById('edit-booking-note')?.value||'').trim(); b.date=date; b.startAt=w.start.toISOString(); b.endAt=w.end.toISOString(); if(!b.guestName){flash('Введите имя гостя');return;} saveBookings(); closeModal(); render();
}
function hallPointerStart(e,el){
  if(!state.hallEditMode) return;
  e.preventDefault(); e.stopPropagation(); const t=state.hallTables.find(x=>x.id===el.dataset.id); if(!t)return;
  const map=document.getElementById('bookingsMap'); if(!map)return;
  state._hallDrag={id:t.id,startX:e.clientX,startY:e.clientY,origX:t.x,origY:t.y,mapW:map.clientWidth,mapH:map.clientHeight,moved:false};
  el.setPointerCapture?.(e.pointerId);
}
function hallPointerMove(e,el){
  if(!state.hallEditMode) return;
  const d=state._hallDrag; if(!d)return; e.preventDefault(); e.stopPropagation();
  const dxPx=e.clientX-d.startX, dyPx=e.clientY-d.startY;
  if(Math.abs(dxPx)+Math.abs(dyPx)>5)d.moved=true;
  const t=state.hallTables.find(x=>x.id===d.id); if(!t)return;
  const dx=dxPx/d.mapW*100, dy=dyPx/d.mapH*100;
  t.x=Math.max(0,Math.min(94,d.origX+dx)); t.y=Math.max(0,Math.min(88,d.origY+dy));
  el.style.left=t.x+'%'; el.style.top=t.y+'%';
}
function hallPointerEnd(e){
  const d=state._hallDrag; if(!d)return;
  if(!state.hallEditMode){state._hallDrag=null;return;}
  const id=d.id;
  const moved=d.moved;
  state._hallDrag=null;
  if(moved){
    saveHall();
    state._hallSuppressClick=true;
    render();
    return;
  }
  state._hallSuppressClick=true;
  openHallTableEditModal(id);
}
function renderBookingCard(b,t){
  const st=new Date(b.startAt), en=new Date(b.endAt);
  const time=`${st.toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'})}–${en.toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'})}`;
  return `<div class="booking-card" onclick="editBooking('${b.id}')"><div class="booking-card-top"><div class="booking-card-name">${escapeHtml(b.guestName)}</div><span class="booking-badge">${escapeHtml(hallTableLabel(t))}</span></div><div class="booking-card-meta">${time} · ${Number(b.guests||1)} ${Number(b.guests||1)===1?'гость':'гостей'}${b.phone?' · '+escapeHtml(b.phone):''}</div>${b.note?`<div class="booking-card-note">${escapeHtml(b.note)}</div>`:''}<div class="booking-card-actions"><button class="btn booking-danger booking-cancel-button" onclick="event.stopPropagation();cancelBooking('${b.id}')">Отменить</button></div></div>`;
}
function renderBookingsScreen(){
  ensureBookingDate();
  const selected=state.hallTables.find(t=>t.id===state.selectedHallTableId) || state.hallTables[0] || null;
  const date=state.bookingDate || localDateString(new Date());
  const selectedBookings=selected ? tableBookings(selected.id,date) : [];
  const busy=selectedBookings.length>0;
  return `<div class="screen bookings-screen ${state.tab==='bookings'?'active':''}">
    <div class="bookings-layout">
      <div class="bookings-map-card">
        <div class="bookings-map-toolbar">
          <button class="bookings-edit-btn" aria-label="Редактировать карту" onclick="openHallEditMenu()"><span class="ui-icon ui-icon-edit" aria-hidden="true"></span></button>
        </div>
        <div class="bookings-map-wrap" id="bookingsMap">
          ${state.hallTables.length?state.hallTables.map(t=>{const tb=bookingStatusForTable(t);return `<div class="hall-table ${t.shape} ${Number(t.rotation||0)?'rotated':''} ${state.selectedHallTableId===t.id?'selected':''} ${tb?'booked':'available'} ${state.hallEditMode?'editing':''}" data-id="${t.id}" style="left:${t.x}%;top:${t.y}%;transform:rotate(${Number(t.rotation||0)}deg)${state.selectedHallTableId===t.id?' scale(1.015)':''};" onclick="handleHallTableClick(event,'${t.id}')" onpointerdown="hallPointerStart(event,this)" onpointermove="hallPointerMove(event,this)" onpointerup="hallPointerEnd(event)" onpointercancel="hallPointerEnd(event)"><div><div class="hall-table-name">${escapeHtml(hallTableLabel(t))}</div><div class="hall-table-meta">${tb?'Существуют брони':'Свободен'}</div></div></div>`;}).join(''):`<div class="bookings-map-hint">Пока нет столов.<br>Откройте редактирование карты и добавьте стол.</div>`}
        </div>
      </div>
      <div class="bookings-side-card">
        <div class="booking-side-head"><div class="booking-side-title">Карточка стола</div><div class="booking-side-sub">Выберите дату — карта и список броней обновятся автоматически.</div>${selected?`<div class="booking-selected-table"><strong>${escapeHtml(hallTableLabel(selected))}</strong><span class="booking-mini">${selected.shape==='square'?'Квадратный':'Прямоугольный'}</span></div>`:''}</div>
        <div class="booking-filters single-column">
          <div class="booking-field"><label>Дата</label><div class="booking-date-wrap"><input type="date" value="${date}" onchange="setBookingDate(this.value)"></div></div>
        </div>
        ${selected?`<div class="booking-check"><span class="booking-check-label">${escapeHtml(date)} · броней: ${selectedBookings.length}</span><span class="booking-status ${busy?'busy':'free'}">${busy?'ЕСТЬ БРОНЬ':'СВОБОДЕН'}</span></div>`:''}
        <div class="booking-list">${selected? (selectedBookings.length?selectedBookings.map(b=>renderBookingCard(b,selected)).join(''):'<div class="booking-list-empty">На выбранную дату у этого стола броней нет.</div>'):'<div class="booking-list-empty">Выберите стол на карте.</div>'}</div>
        <div class="booking-side-actions">${selected?`<button class="bookings-tool booking-primary" onclick="startBookingForSelected()">Забронировать</button><div class="booking-mini booking-count-summary">${selectedBookings.length} ${selectedBookings.length===1?'бронь':'брони'} на дату</div>`:`<button class="bookings-tool booking-primary" disabled>Забронировать</button><div></div>`}</div>
      </div>
    </div>
  </div>`;
}
