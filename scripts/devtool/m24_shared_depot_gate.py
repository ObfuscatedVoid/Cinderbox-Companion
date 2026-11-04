#!/usr/bin/env python3
"""Source-bound M24 shared Steam depot + auxiliary UI safety acceptance."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
PATHS=(
'app/src/main/java/com/sdvsync/steam/SteamContentService.kt',
'app/src/main/java/com/sdvsync/download/GameDownloadService.kt',
'app/src/main/java/com/sdvsync/download/M24DepotManifestCrosscheck.kt',
'app/src/main/java/com/sdvsync/download/M24SharedDepotBinding.kt',
'app/src/main/java/com/sdvsync/download/GameDownloadManager.kt',
'app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt',
'app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt',
'app/src/test/java/com/sdvsync/download/M24SharedDepotBindingTest.kt',
)
def check() -> dict:
    data={}
    for path in PATHS:
        dest=ROOT/path
        if not dest.is_file() or dest.is_symlink(): raise RuntimeError('SOURCE_MISSING_OR_LINK:'+path)
        data[path]=dest.read_text(encoding='utf8')
    pcs=data[PATHS[0]];svc=data[PATHS[1]];cross=data[PATHS[2]];bind=data[PATHS[3]]
    mgr=data[PATHS[4]]; vm=data[PATHS[5]];ui=data[PATHS[6]];test=data[PATHS[7]]
    predicates={
      'pinned_live_parent_pics':all(x in pcs for x in ('sharedDepotFromApps','depot["depotfromapp"]','resolveSharedDepotManifests(', 'picsGetProductInfo(app = PICSRequest(parentId, token))', 'withTimeout(15_000)', 'appInfo = payload.apps[parentId]')),
      'live_source_no_cache_fallback':'loadManifestFromFile' not in pcs and 'getSharedDepotManifestIdsFromCache' not in pcs,
      'merge_guard':all(x in bind for x in ('inherited.keys == parents.keys','direct.keys intersect inherited.keys','Invalid authenticated PICS manifest set')),
      'service_enforces_full_binding':all(x in svc for x in ('M24SharedDepotBinding.merge(directIds, sharedSources, inheritedIds)', 'sessionDepotManifestIds == ids', 'recheckBranch()', 'M24DepotManifestCrosscheck.verify(root, depotIds, completedDepots)')),
      'negative_completed_depot_guard':all(x in cross for x in ('check(missing.isEmpty())','completedDepots - picsIds.keys')),
      'no_unverified_override':'allowUnverified' not in svc and 'skipManifestCheck' not in svc,
      'smapi_preflight_in_catch':'_smapiProgress.value = SmapiSetupProgress(isDownloading = true)\n        try {' in mgr and 'StoragePreflight.requireWritable(File(CinderboxPaths.SMAPI_DIR))' in mgr,
      'apk_cache_only':'_cinderboxProgress.value = CinderboxDownloadProgress(isDownloading = true)\n        withContext(Dispatchers.IO) {\n            try {' in mgr and 'StoragePreflight.requireWritable(File(CinderboxPaths.DESKTOP_DIR))' not in mgr,
      'installed_package_read':'getPackageInfo("com.game.cinderbox", 0)' in vm and 'cinderboxPackageInstalled' in vm,
      'visible_errors': all(token in ui for token in (
        'smapiProgress.errorMessage != null',
        'cinderboxProgress.errorMessage != null',
        'R46ErrorPanel(R46UiErrorArea.SMAPI)',
        'R46ErrorPanel(R46UiErrorArea.CINDERBOX)',
      )),
      'no_redundant_cinderbox_button':'if (!packageInstalled || (updateAvailable && latestVersion != null)) StardewButton' in ui,
      'smapi_overwrite_confirmation':'confirmSmapiReplacement' in ui and 'Replace existing SMAPI?' in ui,
      'negative_unit_cases':all(x in test for x in ('partialParentCannotAuthorize','spuriousParentDepotCannotAuthorize','mismatchedDirectDepotCannotAuthorize')),
    }
    return {'schema_version':1,'producer':'cinderbox-m24-shared-depot-and-setup-source','overall':
        'PASS_M24_SHARED_DEPOT_SETUP_SOURCE' if all(predicates.values()) else 'FAILED_OR_BLOCKED',
        'checks':{k:'PASS' if v else 'FAIL' for k,v in predicates.items()},
        'sha256':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in PATHS},
        'real_steam_download_verified':False,'valve_signature_verified':False,
        'release_qualified':False,'game_files_modified':False}
def main():
    try: record=check()
    except Exception as e:
        print(json.dumps({'overall':'FAILED_OR_BLOCKED','failure_code':type(e).__name__},sort_keys=True));return 1
    print(json.dumps(record,sort_keys=True))
    return 0 if record['overall'].startswith('PASS_') else 1
if __name__=='__main__':raise SystemExit(main())
