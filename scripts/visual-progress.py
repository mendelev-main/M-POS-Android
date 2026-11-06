#!/usr/bin/env python3
"""Generate one visual-parity ledger from the pinned scene IDs; never infer verification."""
import argparse
import json
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def render():
    scenes=json.loads((ROOT/'tools/web-reference/scenes.json').read_text())
    state=json.loads((ROOT/'config/ui-parity-progress.json').read_text())
    ids=[s[0] for s in scenes]
    if len(ids)!=len(set(ids)) or set(state['windows'])-set(ids):
        raise ValueError('Scene IDs must be unique; progress must belong to the reference registry')
    rows=[]
    for scene in scenes:
        item=state['windows'].get(scene[0],{})
        implementation=item.get('implementation','not_started')
        if any(type(item.get(k,False)) is not bool for k in ('source_reviewed','api_verified','pixel_verified')):
            raise ValueError('Review flags must be booleans: '+scene[0])
        if item.get('api_verified') and (implementation!='complete' or not item.get('evidence')):
            raise ValueError('Window API verification requires complete implementation and evidence: '+scene[0])
        if implementation not in ('not_started','partial','complete'):
            raise ValueError('Unknown implementation state: '+scene[0])
        if item.get('pixel_verified'):
            if implementation!='complete' or not item.get('api_verified') or set(item.get('themes',[]))!={'light','dark'} or not item.get('evidence'):
                raise ValueError('Pixel verification requires complete implementation, API, both themes and evidence: '+scene[0])
        rows.append((scene,item,implementation))
    counts=Counter(v for _,_,v in rows);verified=sum(bool(v.get('pixel_verified')) for _,v,_ in rows)
    reviewed=sum(bool(v.get('source_reviewed')) for _,v,_ in rows)
    api=sum(bool(v.get('api_verified')) for _,v,_ in rows)
    text=f'''# Счётчик сверки native с Web

Обновлено: {state['updated_on']}. Источник: tools/web-reference/scenes.json и config/ui-parity-progress.json.
Считаем **{len(rows)} окна/состояния**, а не172 PNG: светлая и тёмная темы входят в одно окно. Это статус визуального переноса, а не наличие функциональных экранов.

| Показатель | Количество |
|---|---:|
| Затронуто сверкой исходников (включая частичную) | {reviewed} / {len(rows)} |
| Внесены частичные исправления интерфейса | {counts['partial']} |
| Полностью реализовано соответствие Web | {counts['complete']} |
| Выполнена Android-проверка окна | {api} |
| Полностью подтверждено парными снимками обеих тем | {verified} |
| Ещё не начато | {counts['not_started']} |
| Осталось полностью закрыть | {len(rows)-verified} |

{state['blocker']}

Частичные исправления не считаются готовым окном. Общий компонент не повышает автоматически статус всех использующих его окон. Для закрытия нужны одинаковые данные, область экрана и состояния, обе темы и конкретное evidence. Нативные системные/дополнительные окна без Web-аналога учитываются отдельно в VISUAL-PARITY-PLAN.md.

| ID | Окно | Сверка исходников | Доработки | API36 | Парные снимки | Осталось |
|---|---|---|---|---|---|---|
'''
    labels={'not_started':'не начаты','partial':'частичные','complete':'завершены'}
    for scene,item,implementation in rows:
        text+=f"| {scene[0]} | {scene[1]} | {'частично' if item.get('source_reviewed') else 'нет'} | {labels[implementation]} | {'проверено' if item.get('api_verified') else 'нет'} | {'проверены' if item.get('pixel_verified') else 'нет'} | {item.get('remaining','Сверка и доработка по Web-эталону')} |\n"
    return text

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    output=ROOT/'docs/VISUAL-PARITY-PROGRESS.md';text=render()
    if args.check:
        if not output.is_file() or output.read_text()!=text:raise SystemExit('Visual progress is stale: run python3 scripts/visual-progress.py')
        print('Visual progress: registry and counts agree')
    else:output.write_text(text);print('Updated '+str(output.relative_to(ROOT)))
if __name__=='__main__':main()
