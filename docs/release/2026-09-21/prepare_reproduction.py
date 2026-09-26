"""Create a separate rerun harness and inputs, preserving every previous artifact."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
OLD = HERE.parent / '2026-09-16'
ROOT = HERE.parents[2]

if __name__ == '__main__':
    (HERE / 'comparison-inputs').mkdir(exist_ok=False)
    manifest = []
    for path in sorted((OLD / 'sources').glob('calendar-*.json')):
        old = json.loads(path.read_text())
        selected = path
        if old['year'] == 2026:
            selected = HERE / 'sources' / path.name
            if old['cityId'] == 'bangalore': selected = HERE / 'sources/calendar-bangalore-2026-retry.json'
        meta = json.loads(selected.read_text())
        assert meta['status'] == 200
        assert hashlib.sha256((ROOT / meta['file']).read_bytes()).hexdigest() == meta['sha256']
        assert meta['sha256'] == old['sha256'], 'Changed source bytes require explicit new comparison scope'
        (HERE / 'comparison-inputs' / path.name).write_text(json.dumps(meta, indent=2))
        manifest.append(dict(input=path.name, sourceMetadata=selected.relative_to(ROOT).as_posix(),
                             samePriorBytes=True, freshRetrieval=old['year'] == 2026))
    (HERE / 'source-recheck.json').write_text(json.dumps(manifest, indent=2))
    # The enhanced harness and init script are checked-in source alongside this script.
    # They are never regenerated over local changes.
