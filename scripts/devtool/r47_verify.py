#!/usr/bin/env python3
"""Independently validate R47's synthetic receipt and complete PNG inventory.

Host-only: no ADB, Gradle, network or device access. Never promotes release gates.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import zlib
from r47_capture import NAMES,EXPECTED_TESTS,png_dimensions


def verify(directory:Path,source_sha:str,run_id:str)->dict:
    assert re.fullmatch(r'[0-9a-f]{40}',source_sha),'bad expected source'
    assert re.fullmatch(r'[0-9]+',run_id),'bad expected run'
    receipt=directory/'R47_SYNTHETIC_EVIDENCE.json'
    assert receipt.is_file() and not receipt.is_symlink(),'receipt missing or linked'
    raw=receipt.read_bytes()
    assert len(raw)<64*1024,'oversized receipt'
    data=json.loads(raw)
    for key,expected in (
        ('schema_version',2),('producer','cinderbox-r47-synthetic-emulator'),
        ('overall','PASS_SYNTHETIC_EMULATOR'),('source_sha',source_sha),
        ('run_id',run_id),('synthetic_only',True),('real_app_screenshots',False),
        ('physical_device_qualified',False),('release_qualified',False),
        ('publication_allowed',False),('automatic_game_update',False),
        ('test_count',EXPECTED_TESTS),('screenshot_count',len(NAMES))):
        assert data.get(key)==expected, 'receipt claim invalid: '+key
    records=data.get('screenshots')
    assert isinstance(records,list) and len(records)==len(NAMES),'receipt inventory count'
    assert set(p.name for p in directory.glob('*.png'))==set(NAMES),'unexpected screenshot inventory'
    seen=set()
    for index,name in enumerate(NAMES):
        record=records[index]
        assert isinstance(record,dict),'invalid screenshot record type'
        assert record.get('name')==name,'receipt screenshot order or identity'
        path=directory/name
        assert path.is_file() and not path.is_symlink(),'screenshot missing or linked'
        content=path.read_bytes()
        width,height=png_dimensions(content)
        digest=hashlib.sha256(content).hexdigest()
        assert record.get('sha256')==digest,'screenshot digest mismatch'
        assert record.get('width_px')==width and record.get('height_px')==height,'dimensions mismatch'
        assert digest not in seen,'duplicate screenshot'
        seen.add(digest)
    assert not list(directory.glob('*.r47tmp')),'temporary files left behind'
    return {'overall':'PASS_R47_SYNTHETIC_EVIDENCE_VERIFIED',
            'source_sha':source_sha,'run_id':run_id,
            'screenshots_verified':len(NAMES),'tests_reported':EXPECTED_TESTS,
            'physical_device_qualified':False,'release_qualified':False}


def main()->int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir',required=True,type=Path)
    parser.add_argument('--source-sha',required=True)
    parser.add_argument('--run-id',required=True)
    args=parser.parse_args()
    try:
        record=verify(args.output_dir,args.source_sha,args.run_id)
        print(json.dumps(record,sort_keys=True));return 0
    except (AssertionError,OSError,ValueError,TypeError,KeyError,zlib.error) as exc:
        print('R47_EVIDENCE_REFUSED:'+str(exc)[:200],file=sys.stderr)
        return 1
if __name__=='__main__':raise SystemExit(main())
