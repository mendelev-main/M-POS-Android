(function(global){
  'use strict';
  const commands=global.MPosCore?.AdminSettingsCommands;if(!commands)return;
  const originalNetwork=global.saveNetworkSettings,originalTelegram=global.saveTelegramSettings;
  const enabled=()=>global.MPosNativeAdminSettingsEnabled!==false;
  let busy=false,blocked=false;
  const clone=x=>JSON.parse(JSON.stringify(x));
  async function commit(key,fields){
    if(busy||blocked){flash(blocked?'Перезапустите приложение перед повторным сохранением':'Дождитесь завершения сохранения');return null;}
    busy=true;
    try{const result=await commands.commit({version:1,key,expected:clone(state[key]),fields});if(!result.settings)throw Error('Не удалось получить сохранённые настройки');return result.settings;}
    catch(error){if(String(error?.message).includes('commit status is uncertain')){blocked=true;markStorageBroken(error);}flash(blocked?'Статус сохранения неизвестен. Перезапустите приложение':error?.message||'Не удалось сохранить настройки');return null;}
    finally{busy=false;}
  }
  global.saveNetworkSettings=async function(...args){
    if(!enabled())return originalNetwork.apply(this,args);
    const current=networkConfigFromState();
    const fields={backendUrl:String(document.getElementById('network-backend-url')?.value??current.backendUrl??''),deviceName:String(document.getElementById('network-device-name')?.value??current.deviceName??'')};
    const next=await commit('network',fields);if(!next)return false;state.network=next;return true;
  };
  global.saveTelegramSettings=async function(closeAfter=false){
    if(!enabled())return originalTelegram.apply(this,arguments);
    const fields={};
    for(const [field,id]of [['botToken','telegram-token'],['chatId','telegram-chat-id'],['threadId','telegram-thread-id'],['deviceChatId','telegram-device-chat-id'],['ownerChatId','telegram-owner-chat-id']])fields[field]=(document.getElementById(id)?.value||'').trim();
    for(const [field,id]of [['enabled','telegram-enabled'],['notifyOnlineOrders','telegram-online-orders'],['notifyShiftOpened','telegram-shift-opened'],['notifyShiftClosed','telegram-shift-closed'],['notifyMonthlyWarehouse','telegram-monthly-warehouse']])fields[field]=!!document.getElementById(id)?.checked;
    const next=await commit('telegram',fields);if(!next)return false;
    state.telegram=next;if(closeAfter)closeModal();render();
    const synced=await syncTelegramBackendSettings(next);
    flash(synced?'Настройки Telegram сохранены':'Настройки сохранены на iPad, но серверные настройки Telegram не обновлены');return synced;
  };
})(window);
