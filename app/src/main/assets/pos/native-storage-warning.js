/* Android storage errors must not claim that every saved record was lost. */
(function(global){
  'use strict';
  if(global.__MPOS_PLATFORM__!=='android')return;
  global.renderStorageWarning=function(){
    return '<div class="storage-warning" role="alert">M POS: не удалось подтвердить сохранение одной из операций. Проверьте её результат перед повтором. Если ошибка повторяется, перезапустите приложение. Ранее сохранённые данные не считаются потерянными.</div>';
  };
})(window);
