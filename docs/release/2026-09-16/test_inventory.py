"""Regression checks on evidence classification; no reference or calculation mutation on disk."""
import copy
from pathlib import Path
import unittest
import inventory

class InventoryChecks(unittest.TestCase):
    def test_discovery_matches_escaped_dot_segment_urls_but_keeps_publishers_distinct(self):
        indexed='https://www.vaisnavacalendar.info/wp2020/../calendars/2026/Mayapur [India].txt'
        downloaded='https://www.vaisnavacalendar.info/calendars/2026/Mayapur%20%5BIndia%5D.txt'
        self.assertEqual(inventory.source_url_identity(indexed),inventory.source_url_identity(downloaded))
        self.assertNotEqual(inventory.source_url_identity(downloaded),inventory.source_url_identity(downloaded.replace('www.vaisnavacalendar.info','example.org')))
        self.assertNotEqual(inventory.source_url_identity(downloaded),inventory.source_url_identity(downloaded+'?version=other'))

    def sample(self, city='mayapur'):
        return inventory.read(Path(__file__).parent/f'measurements/{city}-2026.json')

    def test_agreement_does_not_grant_support_or_approval(self):
        row=inventory.compare(self.sample())
        self.assertEqual('SUFFICIENT_COMPARISON_EVIDENCE_PENDING_REVIEW',row['observanceGroupEvidence'])
        self.assertFalse(row['supported']);self.assertFalse(row['approved'])
        self.assertIn('WITHHELD',row['currentPublication'])

    def test_reference_only_fasting_date_is_a_disagreement(self):
        row=inventory.compare(self.sample('vrindavan'))
        self.assertEqual('DISAGREES',row['fields'][0]['status'])
        self.assertTrue(any(d.get('date')=='2026-06-26' and d['field']=='fastingDate' for d in row['details']))

    def test_missing_end_and_stale_clock_cannot_claim_complete_evidence(self):
        sample=copy.deepcopy(self.sample())
        ref=next(r for r in sample['reference'] if r.get('parana'))
        ref['parana']['end']=None;ref['parana']['endBasis']=None
        row=inventory.compare(sample)
        self.assertEqual('PARTIAL_OR_UNAVAILABLE',row['fields'][4]['status'])
        sample=self.sample()
        next(r for r in sample['daily'] if r['date']==ref['date'])['referenceClockMatchesZone']=False
        row=inventory.compare(sample)
        self.assertEqual('PARTIAL_OR_UNAVAILABLE',row['fields'][3]['status'])
        self.assertEqual('DISAGREES_OR_INCOMPLETE',row['observanceGroupEvidence'])

    def test_equal_displayed_minute_does_not_hide_basis_disagreement(self):
        row=inventory.compare(self.sample('ahmedabad'))
        self.assertEqual('DISAGREES',row['fields'][4]['status'])
        self.assertTrue(any(d.get('date')=='2026-11-06' and d.get('basisStatus')=='DISAGREES' for d in row['details']))

    def test_changed_point_cannot_inherit_adjacent_year_context(self):
        sample=self.sample()
        sample['member']['place']['elevationMeters']=100.0
        self.assertEqual('INCOMPLETE_OR_DISAGREES',inventory.compare(sample)['crossYearContext'])

if __name__=='__main__': unittest.main()
