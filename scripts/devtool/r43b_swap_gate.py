#!/usr/bin/env python3
"""R43-B1 static contract: transaction engine exists but is NOT wired to live Steam."""
from pathlib import Path
import json
import sys
import tomllib
ROOT=Path(__file__).resolve().parents[2]
def read(p): return (ROOT/p).read_text(encoding='utf-8')

def check():
    source=read('app/src/main/java/com/sdvsync/download/R43BUpdateSwap.kt')
    test=read('app/src/test/java/com/sdvsync/download/R43BUpdateSwapTest.kt')
    mgr=read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
    svc=read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
    rec=read('app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt')
    cfg=tomllib.loads(read('.devtool.toml'))
    for v in ('R43BUpdateSwap private constructor', 'internal class R43BUpdateSwap',
              'StandardCopyOption.ATOMIC_MOVE', 'fun recordVerifiedStage(',
              'fun publishVerifiedStage()', 'fun reconcile()', 'fun rollbackPublished()',
              'Finding.MANUAL_REVIEW','return Finding.ORIGINAL_RESTORED',
              'backupFor(target: File', 'displacedFor(target: File',
              '!Files.isSymbolicLink', 'journal.length() in 1..8192'):
        assert v in source, 'missing transaction guard '+v
    for v in ('crashAfterOldRenameRestoresOriginal',
              'crashAfterStageRenameRecognizesPublishedBackup',
              'explicitRollbackKeepsBothVersions','targetConflictRequiresManualReviewWithoutOverwrite',
              'linkedStageChildFailsClosed','unchanged(f)'):
        assert v in test, 'missing crash/safety test '+v
    assert 'R43BUpdateSwap' not in mgr and 'R43BUpdateSwap' not in svc, \
        'B1 MUST NOT WIRE UNVERIFIED TRANSACTION INTO LIVE DOWNLOADS'
    assert 'check(!safe.exists())' in rec and 'beginStaged(' in rec, \
        'M24 old no-overwrite boundary unexpectedly changed'
    assert 'r43b-check' in cfg['wrapper']['commands']
    assert cfg['targets']['cinderbox']['jobs']['r43b-swap-source']['metadata']['read_only'] is True
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r43b-check']] == \
        ['security','r43a','r43b']
    for term in ('release_enabled','publication_enabled','automatic_device_install'):
        assert json.loads(read('docs/security/M3_RELEASE_POLICY.json'))[term] is False
    return {'r43b_swap_source':'PASS_SOURCE_ONLY','device_tested':False,
            'live_update_enabled':False,'preexisting_game_untouched':True}

if __name__=='__main__':
    try:
        print(json.dumps(check(), sort_keys=True))
    except (AssertionError, OSError, ValueError, KeyError) as e:
        print('R43B_SWAP_SOURCE_FAIL:',str(e),file=sys.stderr)
        sys.exit(1)
