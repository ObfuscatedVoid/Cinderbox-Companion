#!/usr/bin/env python3
"""Fail-closed synthetic UI evidence from one disposable GitHub emulator only.

Only seven allowlisted private debug fixtures are read. No users, Steam accounts,
actual app screens, permission changes or protected game directories are touched.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zlib
from r47_diag import summarize_instrumentation

CLASS='com.sdvsync.qualification.R47SyntheticExperienceDeviceTest'
RUNNER='com.sdvsync.test/androidx.test.runner.AndroidJUnitRunner'
NAMES=tuple('r47-synthetic-'+label+'-320dp-fs160.png' for label in (
    'home-earthy-day', 'home-earthy-night', 'home-classic-day',
    'game-earthy-day', 'saves-earthy-night', 'mods-earthy-day', 'more-earthy-day'))
EXPECTED_TESTS=11
MAX_PNG_BYTES=16*1024*1024
MAX_RAW_BYTES=64*1024*1024


def parse_result(output:str)->bool:
    """Require both the test count and the Android instrumentation success code."""
    return (bool(re.search(r'(?m)^OK \(11 tests\)\s*$',output))
            and bool(re.search(r'(?m)^INSTRUMENTATION_CODE: -1\s*$',output))
            and not any(s in output for s in (
                'FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed','shortMsg=',
                'INSTRUMENTATION_ABORTED','INSTRUMENTATION_RESULT: shortMsg')))


def png_dimensions(data:bytes)->tuple[int,int]:
    """Verify a complete 8-bit PNG with checksummed chunks and decodable pixels.

    A plausible 24-byte header is NOT acceptable evidence. Bound size and
    decompression to avoid trusting hostile or accidentally corrupt images.
    """
    if len(data)<67 or len(data)>MAX_PNG_BYTES or not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError('missing, truncated or oversized PNG')
    offset=8
    width=height=channels=None
    idat=[]
    got_ihdr=got_iend=False
    while offset<len(data):
        if len(data)-offset<12:raise ValueError('truncated PNG chunk')
        size=struct.unpack('>I',data[offset:offset+4])[0]
        kind=data[offset+4:offset+8]
        end=offset+12+size
        if end>len(data):raise ValueError('truncated PNG chunk data')
        payload=data[offset+8:offset+8+size]
        crc=struct.unpack('>I',data[offset+8+size:end])[0]
        if zlib.crc32(kind+payload)&0xffffffff!=crc:
            raise ValueError('invalid PNG chunk CRC')
        if not got_ihdr:
            if kind!=b'IHDR' or size!=13:raise ValueError('first PNG chunk is not IHDR')
            width,height,depth,color,compression,flt,interlace=struct.unpack('>IIBBBBB',payload)
            if not (320<=width<=4000 and 100<=height<=4000):
                raise ValueError('image dimensions outside synthetic bounds')
            if depth!=8 or color not in (2,6) or (compression,flt,interlace)!=(0,0,0):
                raise ValueError('unsupported synthetic image format')
            channels=3 if color==2 else 4
            got_ihdr=True
        elif kind==b'IHDR':
            raise ValueError('duplicate IHDR')
        elif kind==b'IDAT':
            idat.append(payload)
        elif kind==b'IEND':
            if size!=0 or not idat or end!=len(data):
                raise ValueError('invalid or nonterminal IEND')
            got_iend=True
            break
        offset=end
    if not got_ihdr or not got_iend or not idat or channels is None:
        raise ValueError('incomplete PNG payload')
    expected=(width*channels+1)*height
    if expected>MAX_RAW_BYTES:raise ValueError('oversized decoded screenshot')
    inflater=zlib.decompressobj()
    decoded=bytearray()
    for piece in idat:
        decoded.extend(inflater.decompress(piece,expected-len(decoded)+1))
        if len(decoded)>expected or inflater.unconsumed_tail:
            raise ValueError('PNG decompression exceeds expected dimensions')
    decoded.extend(inflater.flush())
    if not inflater.eof or inflater.unused_data or len(decoded)!=expected:
        raise ValueError('PNG pixel data missing or malformed')
    stride=width*channels+1
    if any(decoded[row*stride]>4 for row in range(height)):
        raise ValueError('invalid PNG scanline filter')
    return width,height


def main()->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output-dir',type=Path,required=True)
    p.add_argument('--serial',default='emulator-5554')
    p.add_argument('--source-sha',required=True)
    p.add_argument('--run-id',required=True)
    args=p.parse_args()
    result={'schema_version':2,'producer':'cinderbox-r47-synthetic-emulator',
            'overall':'REFUSED','source_sha':args.source_sha,'run_id':args.run_id,
            'synthetic_only':True,'real_app_screenshots':False,'physical_device_qualified':False,
            'release_qualified':False,'publication_allowed':False,'automatic_game_update':False}
    args.output_dir.mkdir(parents=True,exist_ok=True)
    # Re-running a failed collector must never upload stale or partially written PNGs.
    for name in NAMES:
        (args.output_dir/name).unlink(missing_ok=True)
        (args.output_dir/(name+'.r47tmp')).unlink(missing_ok=True)
    adb=['adb','-s',args.serial]
    def run(*items,timeout=120)->bytes:
        completed=subprocess.run(adb+list(items),capture_output=True,timeout=timeout)
        if completed.returncode:raise RuntimeError('adb failure for '+str(items[:3]))
        return completed.stdout
    try:
        if args.serial!='emulator-5554':raise RuntimeError('dedicated CI emulator required')
        if not re.fullmatch(r'[0-9a-f]{40}',args.source_sha) or not re.fullmatch(r'[0-9]+',args.run_id):
            raise RuntimeError('invalid GitHub provenance')
        git=subprocess.run(['git','rev-parse','HEAD'],capture_output=True,text=True,timeout=10)
        if git.returncode or git.stdout.strip()!=args.source_sha:
            raise RuntimeError('source revision is not the requested GitHub commit')
        if run('get-state').strip()!=b'device':raise RuntimeError('emulator unavailable')
        if run('shell','getprop','ro.kernel.qemu').strip()!=b'1':
            raise RuntimeError('physical device refused')
        run('shell','pm','path','com.sdvsync.test')
        run('shell','run-as','com.sdvsync','id')
        # Preserve allowlisted test results even if adb exits nonzero after a test crash.
        # No raw stacktrace, exception message, path or log is persisted or uploaded.
        completed=subprocess.run(adb+['shell','am','instrument','-w','-r','-e',
                                   'class',CLASS,RUNNER],capture_output=True,timeout=420)
        output=completed.stdout.decode('utf-8','replace')
        summary=summarize_instrumentation(output)
        result['instrumentation_summary']=summary
        result['instrumentation_exit_success']=(completed.returncode==0)
        if completed.returncode!=0 or not parse_result(output) or summary['failed_tests']:
            raise RuntimeError('all eleven synthetic instrumentation cases did not pass')
        verified=[]
        hashes=set()
        # Validate the *entire* inventory before writing even the first PNG.
        for name in NAMES:
            data=run('exec-out','run-as','com.sdvsync','cat','files/'+name)
            width,height=png_dimensions(data)
            digest=hashlib.sha256(data).hexdigest()
            if digest in hashes:raise ValueError('duplicate synthetic screenshot bytes')
            hashes.add(digest)
            verified.append((name,data,width,height,digest))
        screenshots=[]
        for name,data,width,height,digest in verified:
            tmp=args.output_dir/(name+'.r47tmp')
            tmp.write_bytes(data)
            os.replace(tmp,args.output_dir/name)
            screenshots.append({'name':name,'width_px':width,'height_px':height,'sha256':digest})
        result.update(overall='PASS_SYNTHETIC_EMULATOR',test_count=EXPECTED_TESTS,
                      screenshot_count=len(screenshots),screenshots=screenshots)
    except (OSError,ValueError,RuntimeError,subprocess.TimeoutExpired,zlib.error) as exc:
        for name in NAMES:
            (args.output_dir/name).unlink(missing_ok=True)
            (args.output_dir/(name+'.r47tmp')).unlink(missing_ok=True)
        result['failure_class']=type(exc).__name__
        result['failure_reason']=str(exc)[:180]
    (args.output_dir/'R47_SYNTHETIC_EVIDENCE.json').write_text(
        json.dumps(result,sort_keys=True,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='screenshots'},sort_keys=True))
    return 0 if result['overall']=='PASS_SYNTHETIC_EMULATOR' else 1

if __name__=='__main__':raise SystemExit(main())
