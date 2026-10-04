(() => {
  const SOUND_OPTIONS = [
    ['default', 'Прилавок — мягкий'], ['chime', 'Chime — двойной'], ['glass', 'Glass — лёгкий звон'],
    ['ding', 'Ding — кассовый'], ['double', 'Double — два сигнала'], ['warm', 'Warm — тёплый'],
    ['pop', 'Pop — короткий'], ['bright', 'Bright — светлый'], ['soft', 'Soft — спокойный'], ['classic', 'Classic — колокольчик']
  ];
  function nativeSound(name) {
    const handler = window.webkit?.messageHandlers?.printer;
    if (!handler) { window.flash?.('Нативный звук доступен только в приложении на iPad'); return false; }
    handler.postMessage({ action: 'print', order: { __notificationSound: name || 'default' } }); return true;
  }
  function notificationSettings() {
    let settings = { soundEnabled: true, sound: 'default' };
    try { settings = { ...settings, ...JSON.parse(localStorage.getItem('posNotificationSettings') || '{}') }; } catch (_) {}
    return settings;
  }
  window.previewNotificationSound = () => nativeSound(document.getElementById('notify-sound')?.value || 'default');
  window.playPOSEventSound = () => { const settings = notificationSettings(); if (settings.soundEnabled) nativeSound(settings.sound); };
  const originalOpenNotificationSettings = window.openNotificationSettings;
  if (typeof originalOpenNotificationSettings === 'function') window.openNotificationSettings = (...args) => {
    const result = originalOpenNotificationSettings(...args);
    requestAnimationFrame(() => {
      const select = document.getElementById('notify-sound'); if (!select) return;
      const selected = notificationSettings().sound;
      select.innerHTML = SOUND_OPTIONS.map(([value,label]) => `<option value="${value}" ${value===selected?'selected':''}>${label}</option>`).join('');
    });
    return result;
  };

  let readyBusy = false;
  const originalOpenParkedModal = typeof openParkedModal === 'function' ? openParkedModal : null;
  function readyButtonMarkup(order) {
    if (!order?.webOrderId) return '';
    if (order?.webReadyAt) return '<span class="badge" style="flex:none;padding:7px 11px;font-size:14px;white-space:nowrap;background:var(--accent-soft);color:var(--accent);">✓ Готов</span>';
    return `<button class="btn btn-primary web-ready-btn" style="flex:none;width:auto;min-width:0;min-height:40px;padding:8px 14px;font-size:14px;white-space:nowrap;" onclick="event.stopPropagation();confirmWebOrderReady('${escapeAttr(order.id)}')">Готов</button>`;
  }

  function confirmationModal(title, text, confirmLabel, confirmAction, danger=false) {
    showModal(`
      <div class="modal-title">${escapeHtml(title)}</div>
      <div style="font-size:16px;line-height:1.45;color:var(--text);padding:4px 0 8px;">${escapeHtml(text)}</div>
      <div class="modal-actions" style="display:flex;gap:10px;">
        <button class="btn btn-secondary" style="flex:1;" onclick="openParkedModal()">Отмена</button>
        <button class="btn ${danger?'btn-danger':'btn-primary'}" style="flex:1;" onclick="${confirmAction}">${escapeHtml(confirmLabel)}</button>
      </div>`);
  }
  window.confirmWebOrderReady = function (parkedId) {
    confirmationModal('Подтверждение', 'Подтвердить готовность заказа?', 'Подтвердить', `markWebOrderReady('${escapeAttr(parkedId)}')`);
  };
  window.confirmDeleteParkedOrder = function (parkedId) {
    confirmationModal('Удаление заказа', 'Вы уверены, что хотите удалить весь заказ?', 'Удалить', `deleteParked('${escapeAttr(parkedId)}')`, true);
  };

  if (originalOpenParkedModal) window.openParkedModal = function () {
    const list = state.parked.slice().sort((a,b)=>b.createdAt-a.createdAt);
    showModal(`
      <div style="width:min(900px,86vw);max-width:100%;min-width:0;">
        <div class="modal-title">Отложенные чеки</div>
        <div style="width:100%;min-width:0;max-height:68vh;overflow-y:auto;overflow-x:hidden;padding-right:2px;">
        ${list.length ? list.map(o=>`
          <div role="button" tabindex="0" onclick="resumeParked('${escapeAttr(o.id)}')" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();resumeParked('${escapeAttr(o.id)}')}" style="display:grid;grid-template-columns:minmax(0,1fr) auto;gap:14px;align-items:center;width:100%;min-width:0;padding:15px 14px;margin-bottom:8px;border:1px solid var(--border);border-radius:14px;background:var(--surface);cursor:pointer;">
            <div style="min-width:0;overflow:hidden;">
              <div class="list-row-name" style="font-size:16px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(o.orderLabel||'Без подписи')}</div>
              <div class="list-row-sub" style="margin-top:5px;white-space:normal;overflow-wrap:anywhere;">${o.items.reduce((s,i)=>s+i.qty,0)} поз. · ${fullMoney(o.total)} · ${escapeHtml(o.orderType||'На месте')} · ${fmtDate(o.createdAt)}</div>
            </div>
            <div style="display:flex;align-items:center;gap:8px;flex:none;">
              ${readyButtonMarkup(o)}
              <button class="icon-btn danger" style="flex:none;" aria-label="Удалить заказ" onclick="event.stopPropagation();confirmDeleteParkedOrder('${escapeAttr(o.id)}')">✕</button>
            </div>
          </div>`).join('') : `<div class="center-note">Нет отложенных чеков</div>`}
        </div>
        <div class="modal-actions"><button class="btn btn-secondary" style="width:100%;" onclick="closeModal()">Закрыть</button></div>
      </div>
    `);
  };
  window.markWebOrderReady = async function (parkedId) {
    if (readyBusy) return;
    const parked = state.parked.find(x=>x.id===parkedId); if (!parked?.webOrderId || parked.webReadyAt) return;
    const n = networkConfigFromState(); if (!n.backendUrl || !n.deviceKey) { flash('Проверьте сетевые настройки'); window.openParkedModal(); return; }
    readyBusy = true;
    try {
      const controller = new AbortController(); const timeout = setTimeout(()=>controller.abort(),30000); let response;
      try { response = await fetch(n.backendUrl.replace(/\/+$/,'')+'/api/orders/'+encodeURIComponent(parked.webOrderId)+'/ready',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':n.deviceKey},body:'{}',cache:'no-store',signal:controller.signal}); }
      finally { clearTimeout(timeout); }
      const data=await response.json().catch(()=>null); if(!response.ok) throw new Error(data?.error||('HTTP '+response.status));
      const next=state.parked.map(x=>x.id===parkedId?{...x,webReadyAt:Date.now(),webOrderStatus:'ready'}:x);
      await window.PrilavokCore.Storage.set('parked',next); state.parked=next; window.openParkedModal(); flash('Заказ отмечен как готов');
    } catch(e) { flash('Не удалось изменить статус: '+(e?.message||'ошибка сети')); window.openParkedModal(); }
    finally { readyBusy=false; }
  };

  let baselineReady=false,lastEventCount=0,checkTimer=0;
  function readEventsCount(){const controls=[...document.querySelectorAll('button, [role="button"]')];const control=controls.find(el=>/события/i.test(el.textContent||''));if(!control)return null;const badge=control.querySelector('.badge,.count,.counter,[class*="badge"],[class*="count"]');const badgeNumber=badge?.textContent?.match(/\d+/);if(badgeNumber)return Number(badgeNumber[0]);const text=(control.textContent||'').replace(/\s+/g,' ').trim();const number=text.match(/(?:события)\D*(\d+)/i);return number?Number(number[1]):0;}
  function checkForNewEvents(){checkTimer=0;const count=readEventsCount();if(count==null)return;if(!baselineReady){baselineReady=true;lastEventCount=count;return;}if(count>lastEventCount)window.playPOSEventSound?.();lastEventCount=count;}
  function scheduleCheck(){if(!checkTimer)checkTimer=window.setTimeout(checkForNewEvents,120);}
  const startObserver=()=>{checkForNewEvents();const observer=new MutationObserver(scheduleCheck);observer.observe(document.body,{childList:true,subtree:true,characterData:true});};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',startObserver,{once:true});else startObserver();
})();