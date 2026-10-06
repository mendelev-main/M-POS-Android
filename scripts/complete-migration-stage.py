#!/usr/bin/env python3
"""Complete an existing scope and keep its task board/progress headers consistent."""
import argparse
import json
import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('stage')
parser.add_argument('spec')
parser.add_argument('summary')
args = parser.parse_args()
p = root / 'docs/kotlin-migration-tasks.json'
data = json.loads(p.read_text())
task = next(t for t in data['tasks'] if t['id'] == args.stage)
assert task['status'] in {'planned', 'in_progress'}, 'scope already complete or superseded'
spec = (root / args.spec).resolve()
assert spec.is_relative_to(root / 'specs') and spec.is_file(), 'spec must exist inside specs/'
task.update(status='done', spec=str(spec.relative_to(root)))
done = sum(t['status'] == 'done' for t in data['tasks'])
total = sum(t['status'] != 'superseded' for t in data['tasks'])
ratio = f'{done}/{total}'
percent = f'{done / total * 100:.2f}'
p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
board = f'''# Реестр задач миграции на Kotlin

Базовый план v1, 6 октября 2026 года. **{ratio} задач выполнено ({percent}%)**.
Осталось {total-done}. Таблицы ниже содержат завершённые инженерные этапы
со спецификациями и оставшиеся границы из P1–P12/существующих feature-модулей.
030 заменена 033 и исключена из знаменателя.

Это доля завершённых инженерных задач, включая foundation/проверки/платформенные
границы, а не доля полностью нативных функций, кода или трудозатрат. Задачи
разного размера. Shadow/диагностические/частичные этапы закрыты только в своей
спецификации. Основной POS/payment shell ещё WebView. Физическая приёмка 110
ожидается, автоматическая готовность не означает её прохождения.

Критерий done: scope реализован, business/source/v13 parity и rollback описаны,
подходящие JS/JVM/lint прошли, результат опубликован в main. После этапа обновить
spec, status/roadmap/parity/tablet cases и реестр. IDs не переиспользуются;
знаменатель меняется только с объяснением изменения scope. Не меняем business
policy молча и не добавляем новые features ради процента.

Источник: [kotlin-migration-tasks.json](kotlin-migration-tasks.json).
Счётчик: `python scripts/migration-progress.py`; после проверки этапа:
`python scripts/complete-migration-stage.py ID specs/ID-name/spec.md "summary"`.
Порядок определяется зависимостями/приоритетом, номера не являются сроками.

## Реализованные этапы и отменённый этап

| ID | Статус | Спецификация / ограниченный scope |
|---|---|---|
'''
for t in data['tasks']:
    if t['status'] == 'done':
        board += f"| {t['id']} | выполнено | [{t['title']}](../{t['spec']}) |\n"
    elif t['status'] == 'superseded':
        board += f"| {t['id']} | заменена 033 | [{t['title']}](../{t['spec']}) |\n"
board += '\n## Оставшиеся задачи\n\n| ID | Статус | Область | Что переносим | Зависимости |\n|---|---|---|---|---|\n'
for t in data['tasks']:
    if t['status'] in {'planned', 'in_progress'}:
        status = 'запланировано' if t['status'] == 'planned' else 'в работе'
        board += f"| {t['id']} | {status} | {t.get('area', '')} | {t['title']} | {t.get('dependencies', '')} |\n"
board += f'\n## Последний этап\n\n{args.stage}: {args.summary}\nФизическая приёмка ожидается.\n'
(root / 'docs/KOTLIN_MIGRATION_TASKS.md').write_text(board)
for name in ['MIGRATION_STATUS_RU.md', 'NATIVE_MIGRATION_ROADMAP.md']:
    p = root / 'docs' / name
    text = p.read_text()
    text = re.sub(r'Актуальный срез: этап \d+', f'Актуальный срез: этап {args.stage}', text, count=1)
    text = re.sub(r'\d+/\d+ выполнено — \d+,\d+%', f'{ratio} выполнено — {percent.replace(".", ",")}%', text, count=1)
    text = re.sub(r'\d+/\d+ выполнено \(\d+,\d+%\)', f'{ratio} выполнено ({percent.replace(".", ",")}%)', text, count=1)
    text = re.sub(r'после \d+\.', f'после {args.stage}.', text, count=1)
    p.write_text(text)
print(f'{ratio} completed ({percent}%); {total-done} remaining')
