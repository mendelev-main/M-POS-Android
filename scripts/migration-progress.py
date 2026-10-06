#!/usr/bin/env python3
"""Validate the stable migration task registry and print its current engineering progress."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
tasks = json.loads((root / 'docs/kotlin-migration-tasks.json').read_text())['tasks']
ids = [task['id'] for task in tasks]
assert len(ids) == len(set(ids)), 'duplicate task ID'
for task in tasks:
    assert task['status'] in {'done', 'planned', 'in_progress', 'superseded'}, task
    if task['status'] == 'done':
        assert (root / task['spec']).is_file(), f"missing spec {task['id']}"
    for dependency in task.get('dependencies', '').split(','):
        if dependency:
            assert dependency in ids and dependency != task['id'], f"invalid dependency {task['id']}"
graph = {task['id']: [d for d in task.get('dependencies', '').split(',') if d] for task in tasks}
visited, visiting = set(), set()
def visit(task_id):
    assert task_id not in visiting, f'cyclic dependency {task_id}'
    if task_id in visited:
        return
    visiting.add(task_id)
    for dependency in graph[task_id]:
        visit(dependency)
    visiting.remove(task_id)
    visited.add(task_id)
for task_id in ids:
    visit(task_id)
done = sum(task['status'] == 'done' for task in tasks)
total = sum(task['status'] != 'superseded' for task in tasks)
print(f'{done}/{total} engineering tasks completed ({done / total * 100:.2f}%); {total - done} remaining')
print('Physical acceptance pending; this is not the proportion of fully native features.')
