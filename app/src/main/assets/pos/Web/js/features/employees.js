function employeeShortName(name){
  const parts=String(name||'').trim().split(/\s+/).filter(Boolean);
  if(!parts.length) return 'Сотрудник';
  const surname=parts[0];
  const initials=parts.slice(1,3).map(x=>x.charAt(0).toUpperCase()+'.').join('');
  return surname + (initials ? ' ' + initials : '');
}

function employeeDisplayName(name){
  const parts=String(name||'').trim().split(/\s+/).filter(Boolean);
  if(parts.length<2)return parts[0]||'';
  return parts[0]+' '+parts.slice(1,3).map(part=>part.split('-').filter(Boolean).map(piece=>Array.from(piece)[0].toLocaleUpperCase('ru')+'.').join('-')).join(' ');
}

function openEmployeeModal(id=''){
  const employee=state.employees.find(e=>e.id===id);
  const editing=!!employee;
  const isAdmin=employee?.role==='admin';
  showModal(`
    <div class="modal-title">${editing?'Сотрудник':'Новый сотрудник'}</div>
    <div class="field"><label>ФИО</label><input id="ef-name" value="${escapeAttr(employee?.name||'')}" placeholder="Иванов Иван Иванович"></div>
    <div class="field"><label>Номер телефона</label><input id="ef-phone" type="tel" value="${escapeAttr(employee?.phone||'')}" inputmode="tel" placeholder="+375 XX XXX-XX-XX"></div>
    <div class="setting-row employee-admin-row">
      <div><div class="setting-title">Администратор</div><div class="setting-sub">${isAdmin?'Для снятия прав администратора потребуется пароль.':'Выдать сотруднику расширенные права приложения.'}</div></div>
      <label class="switch"><input id="ef-admin" type="checkbox" ${isAdmin?'checked':''} onchange="toggleEmployeeAdminPassword()"><span></span></label>
    </div>
    <div id="ef-admin-password-wrap" class="field employee-admin-password" data-original-admin="${isAdmin?'true':'false'}" hidden>
      <label>${isAdmin?'Пароль для снятия прав администратора':'Пароль для выдачи прав администратора'}</label>
      <input id="ef-admin-password" type="password" autocomplete="off" placeholder="Введите пароль">
    </div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button id="employee-save-confirm" class="btn btn-primary" onclick="saveEmployee('${escapeAttr(id)}')">${editing?'Сохранить':'Добавить'}</button></div>
  `);
}

function toggleEmployeeAdminPassword(){
  const checkbox=document.getElementById('ef-admin');
  const wrap=document.getElementById('ef-admin-password-wrap');
  if(!checkbox || !wrap) return;
  const originalAdmin=wrap.dataset.originalAdmin==='true';
  const changed=checkbox.checked!==originalAdmin;
  wrap.hidden=!changed;
  const label=wrap.querySelector('label');
  if(label) label.textContent=checkbox.checked?'Пароль для выдачи прав администратора':'Пароль для снятия прав администратора';
  if(changed) setTimeout(()=>document.getElementById('ef-admin-password')?.focus(),50);
}

async function saveEmployee(id=''){
  if(window._employeeSaveBusy)return false;
  const name=(document.getElementById('ef-name')?.value||'').trim();
  const phone=(document.getElementById('ef-phone')?.value||'').trim();
  const wantsAdmin=!!document.getElementById('ef-admin')?.checked;
  const adminPassword=(document.getElementById('ef-admin-password')?.value||'');
  if(!name){flash('Введите ФИО сотрудника');return false;}
  const existingIndex=id ? state.employees.findIndex(x=>x.id===id) : -1;
  if(id && existingIndex<0){flash('Сотрудник не найден');return false;}
  const wasAdmin=existingIndex>=0 && state.employees[existingIndex]?.role==='admin';
  if(wantsAdmin!==wasAdmin && adminPassword!=='Rom23061998'){
    flash(wantsAdmin?'Для выдачи прав администратора введите верный пароль':'Для снятия прав администратора введите верный пароль');return false;
  }

  const next=storageSnapshot(state.employees);
  if(existingIndex>=0){
    next[existingIndex].name=name;
    next[existingIndex].phone=phone;
    next[existingIndex].role=wantsAdmin?'admin':'employee';
  }else{
    next.push({id:uid(),name,phone,role:wantsAdmin?'admin':'employee'});
  }

  window._employeeSaveBusy=true;
  const button=document.getElementById('employee-save-confirm');if(button)button.disabled=true;
  try{
    await window.PrilavokCore.Storage.set('employees',next);
    state.employees=next;
    closeModal();render();flash(id?'Сотрудник сохранён':'Сотрудник добавлен');
    return true;
  }catch(e){
    markStorageBroken(e);
    flash('Не удалось сохранить сотрудника: '+(e?.message||'ошибка сохранения'));
    if(button)button.disabled=false;
    return false;
  }finally{
    window._employeeSaveBusy=false;
  }
}

function showEmployeeAdminInfo(id){
  const employee=state.employees.find(e=>e.id===id);if(!employee)return;
  showModal(`<div class="modal-title">Администратор</div><p>Сотрудник «${escapeHtml(employee.name)}» имеет права администратора. Удаление этой записи недоступно.</p><div class="modal-actions"><button class="btn btn-primary" onclick="closeModal()">Понятно</button></div>`);
}

function employeeDeletionAllowed(id){
  if(currentShift()?.employeeId===id){flash('Нельзя удалить самого себя');return false;}
  if(state.employees.find(e=>e.id===id)?.role==='admin'){flash('Нельзя удалить администратора');return false;}
  if(!currentShift()){flash('Для удаления сотрудника откройте смену');return false;}
  return true;
}

function deleteEmployee(id){
  if(!employeeDeletionAllowed(id))return;
  const employee=state.employees.find(e=>e.id===id);if(!employee)return;
  showModal(`<div class="modal-title">Удаление сотрудника</div><p>Удалить сотрудника «${escapeHtml(employee.name)}»?</p><p class="pe-note">История смен и чеков сохранится. Для удаления требуется пароль.</p><div class="field"><label for="employee-delete-password">Пароль</label><input id="employee-delete-password" type="password" autocomplete="off"></div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button id="employee-delete-confirm" class="btn btn-danger" onclick="confirmDeleteEmployee(${escapeAttr(JSON.stringify(id))})">Удалить</button></div>`);
}

async function confirmDeleteEmployee(id){
  if(window._employeeDeleteBusy||!employeeDeletionAllowed(id))return;
  if(!state.employees.some(e=>e.id===id))return;
  if((document.getElementById('employee-delete-password')?.value||'')!=="Rom23061998"){flash('Неверный пароль');return;}
  window._employeeDeleteBusy=true;
  const button=document.getElementById('employee-delete-confirm');if(button)button.disabled=true;
  const next=state.employees.filter(e=>e.id!==id);
  try{
    await window.PrilavokCore.Storage.set('employees',next);
    state.employees=next;
    closeModal();render();flash('Сотрудник удалён');
  }catch(e){markStorageBroken(e);flash('Не удалось удалить сотрудника: '+(e.message||'ошибка сохранения'));if(button)button.disabled=false;}
  finally{window._employeeDeleteBusy=false;}
}
