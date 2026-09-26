"""New immutable evidence snapshot. Never overwrite the prior release research."""
import importlib.util
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
spec = importlib.util.spec_from_file_location('prior_research', HERE.parent / '2026-09-16/research.py')
prior = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prior)
prior.HERE = HERE

def fetch(*args, **kwargs): return prior.fetch(*args, **kwargs)

if __name__ == '__main__':
    if sys.argv[1] == 'fetch':
        for meta in sorted((HERE.parent / '2026-09-16/sources').glob('calendar-*-2026.json')):
            old = json.loads(meta.read_text())
            current = fetch(meta.stem, old['url'], 'calendar', cityId=old['cityId'], year=2026, lineage='vaisnavacalendar.info/GCal-text')
            print('same prior bytes:', current.get('sha256') == old['sha256'], flush=True)
        for id, url in [
            ('gcal-tree', 'https://api.github.com/repos/gopaladasa/GCAL-for-Windows/git/trees/master?recursive=1'),
            ('gopa-tree', 'https://api.github.com/repos/gopa810/gaurabda-calendar/git/trees/master?recursive=1'),
            ('gopa-profile', 'https://api.github.com/users/gopa810'),
            ('gopa-repos', 'https://api.github.com/users/gopa810/repos?per_page=100'),
            ('about', 'https://www.vaisnavacalendar.info/about-the-vaisnava-calendar'),
            ('dst', 'https://www.vaisnavacalendar.info/daylight-savings'),
            ('gcal-rules', 'https://gopal.home.sk/gcal/docs/GCalVaisnavaCalculation.pdf'),
            ('gbc-2019', 'https://gbc.iskcon.org/2019-agm/'),
            ('gbc-committees', 'https://gbc.iskcon.org/commmittees/'),
        ]: fetch(id, url, 'upstream-discovery-or-documentation')
        for id, repo, paths in [
            ('gcal', 'gopaladasa/GCAL-for-Windows', ['README.md']),
            ('gopa', 'gopa810/gaurabda-calendar', ['README.md', 'setup.py', 'gaurabda/TCalendar.py', 'gaurabda/GCCalendarDay.py', 'gaurabda/GCSunData.py']),
        ]:
            tree = json.loads((HERE / f'sources/{id}-tree.raw').read_text())
            for path in paths:
                fetch(id + '-' + Path(path).stem, f"https://raw.githubusercontent.com/{repo}/{tree['sha']}/{path}",
                      'PRIMARY_UPSTREAM_IMPLEMENTATION_SUPPORTING_ONLY' if id == 'gopa' else 'DESIGNATED_LINEAGE_DOCUMENTATION', upstreamRevision=tree['sha'])
