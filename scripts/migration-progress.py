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
expanded_done, expanded_total = done, total
for task in tasks:
    children = task.get('subtasks', [])
    if not children:
        continue
    assert task['status'] != 'superseded', f"superseded parent {task['id']}"
    child_ids = [child['id'] for child in children]
    assert len(child_ids) == len(set(child_ids)), 'duplicate subtask ID'
    child_graph = {}
    for child in children:
        assert child['id'].startswith(task['id'] + '.'), 'invalid subtask ID'
        assert child['status'] in {'done', 'planned', 'in_progress', 'superseded'}, child
        child_graph[child['id']] = child.get('dependencies', [])
        for dependency in child_graph[child['id']]:
            assert dependency in child_ids and dependency != child['id'], 'invalid subtask dependency'
        if child['status'] == 'done':
            assert child.get('evidence'), f"missing evidence {child['id']}"
            assert all((root / path).is_file() for path in child['evidence']), 'missing evidence file'
            assert all(next(c for c in children if c['id'] == dep)['status'] == 'done'
                       for dep in child_graph[child['id']]), 'unfinished prerequisite'
    graph = child_graph
    visited, visiting = set(), set()
    for child_id in child_ids:
        visit(child_id)
    child_done = sum(c['status'] == 'done' for c in children)
    child_total = sum(c['status'] != 'superseded' for c in children)
    assert child_total > 0, 'empty decomposition'
    assert task['status'] != 'done' or child_done == child_total, 'unfinished children of completed parent'
    print(f"{task['id']}: {child_done}/{child_total} subtasks completed "
          f"({child_done / child_total * 100:.2f}%); {child_total - child_done} remaining")
    expanded_done += child_done - (task['status'] == 'done')
    expanded_total += child_total - 1
print(f'Expanded: {expanded_done}/{expanded_total} tasks completed '
      f'({expanded_done / expanded_total * 100:.2f}%); {expanded_total - expanded_done} remaining')
print('Physical acceptance pending; this is not the proportion of fully native features.')
