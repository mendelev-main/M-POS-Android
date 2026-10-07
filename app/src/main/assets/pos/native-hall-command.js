(function(global){
 'use strict';
 if(!global.MPosCore?.HallCommands)return;
 const enabled=()=>global.MPosNativeHallCommandsEnabled!==false;
 const snapshot=value=>JSON.parse(JSON.stringify(value));
 const value=(id,fallback='')=>document.getElementById(id)?.value||fallback;
 const zone=()=>Intl.DateTimeFormat().resolvedOptions().timeZone;
 let busy=false,dragSnapshot=null;
 const originalLoad=global.loadKey;
 if(typeof originalLoad==='function')global.loadKey=function(key,fallback){return ['hallTables','bookings'].includes(key)?global.MPosCore.Storage.get(key,fallback,error=>{if(typeof markStorageBroken==='function')markStorageBroken(error)}):originalLoad.apply(this,arguments)};
 async function commit(command,after){
  if(busy)return false;
  if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending){flash('Перезапустите M POS для восстановления данных');return false}
  busy=true;
  const input=snapshot({version:1,...command,expected:{hallTables:state.hallTables,bookings:state.bookings}});
  try{
   const result=await global.MPosCore.HallCommands.commit(input);
   if(!result.ok||!result.authoritative||!Array.isArray(result.hallTables)||!Array.isArray(result.bookings))throw Error('Некорректный ответ сохранения зала');
   state.hallTables=result.hallTables;state.bookings=result.bookings;after?.();return true;
  }catch(error){
   if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
   flash('Не удалось сохранить данные зала. '+(error?.message||''));return false;
  }finally{busy=false}
 }
 function replace(name,fn){const original=global[name];if(typeof original==='function')global[name]=function(){if(!enabled())return original.apply(this,arguments);return fn.apply(this,arguments)}}
 const finish=message=>{closeModal();render();if(message)flash(message)};
 replace('createHallTable',shape=>{const name=value('new-hall-table-name').trim();if(!name){flash('Введите название стола');return}const id=uid();return commit({operation:'table-create',id,name,shape},()=>{state.selectedHallTableId=id;finish('Стол добавлен')})});
 replace('confirmDeleteHallTable',id=>commit({operation:'table-delete',id},()=>{if(state.selectedHallTableId===id)state.selectedHallTableId=null;finish()}));
 replace('rotateHallTable',(id,delta)=>{if(!state.hallTables.some(t=>t.id===id))return;return commit({operation:'table-rotate',id,delta},()=>finish())});
 replace('saveHallTableEdits',id=>{if(!state.hallTables.some(t=>t.id===id))return;const name=value('edit-hall-table-name').trim();if(!name){flash('Введите название стола');return}return commit({operation:'table-edit',id,name},()=>finish('Стол изменён'))});
 replace('saveNewBooking',tableId=>{
  const guestName=value('booking-name-input').trim();if(!guestName){flash('Введите имя гостя');return}
  const time=value('booking-time-input',state.bookingTime||'19:00'),rawDuration=Number(value('booking-duration-input',state.bookingDuration||120)),duration=Number(rawDuration||state.bookingDuration||120);
  return commit({operation:'booking-create',id:uid(),tableId,date:state.bookingDate||localDateString(new Date()),time,duration,zone:zone(),now:Date.now(),guestName,phone:value('booking-phone-input'),guests:value('booking-guests-input',2),note:value('booking-note-input')},()=>{state.bookingTime=time;state.bookingDuration=duration;finish('Бронирование создано')});
 });
 replace('cancelBooking',id=>{if(!state.bookings.some(b=>b.id===id))return;return commit({operation:'booking-cancel',id},()=>{render();flash('Бронирование отменено')})});
 replace('saveEditedBooking',id=>{
  const b=state.bookings.find(x=>x.id===id);if(!b)return;const guestName=value('edit-booking-name').trim();if(!guestName){flash('Введите имя гостя');return}
  return commit({operation:'booking-edit',id,date:b.date||localDateString(new Date(b.startAt)),time:value('edit-booking-time',new Date(b.startAt).toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'})),duration:Math.max(30,Number(value('edit-booking-duration',120))),zone:zone(),guestName,phone:value('edit-booking-phone'),guests:value('edit-booking-guests',2),note:value('edit-booking-note')},()=>finish());
 });
 const start=global.hallPointerStart,move=global.hallPointerMove,end=global.hallPointerEnd;
 if(typeof start==='function')global.hallPointerStart=function(){if(!enabled())return start.apply(this,arguments);if(busy)return;dragSnapshot=snapshot(state.hallTables);return start.apply(this,arguments)};
 if(typeof move==='function')global.hallPointerMove=function(){if(enabled()&&busy)return;return move.apply(this,arguments)};
 if(typeof end==='function')global.hallPointerEnd=function(){
  if(!enabled())return end.apply(this,arguments);
  const d=state._hallDrag;if(!d)return;
  if(!state.hallEditMode){if(dragSnapshot)state.hallTables=dragSnapshot;dragSnapshot=null;state._hallDrag=null;render();return}
  if(!d.moved){dragSnapshot=null;return end.apply(this,arguments)}
  const table=state.hallTables.find(t=>t.id===d.id),command={operation:'table-move',id:d.id,x:table?.x,y:table?.y};
  state.hallTables=dragSnapshot||state.hallTables;dragSnapshot=null;state._hallDrag=null;state._hallSuppressClick=true;render();
  return commit(command,()=>render());
 };
})(window);
