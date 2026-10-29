param([string]$PythonExe = 'python')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
 git diff --quiet HEAD --
 if($LASTEXITCODE -ne 0){throw 'Commit source changes before publishing a release.'}
 $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
 $tempInfo=Join-Path $repoRoot 'deliverables/local-release-verification.json'
 & $PythonExe tools/verify_release_apk.py --usda-mode configured --out $tempInfo
 if($LASTEXITCODE -ne 0){throw 'Release verification failed; do not publish.'}
 $info=Get-Content -LiteralPath $tempInfo -Raw | ConvertFrom-Json
 $relative="deliverables/releases/$($info.versionName)-code$($info.versionCode)-private-$stamp"
 $folder=Join-Path $repoRoot $relative
 New-Item -ItemType Directory -Path $folder | Out-Null
 $name="Food.Logger-$($info.versionName)-code$($info.versionCode)-release.apk"
 Copy-Item -LiteralPath app/build/outputs/apk/release/app-release.apk -Destination "$folder/$name"
 Copy-Item -LiteralPath $tempInfo -Destination "$folder/BUILD_INFO.json"
 Copy-Item -LiteralPath docs/RELEASES.md -Destination "$folder/INSTALL-AND-UPDATE.md"
 Copy-Item -LiteralPath CURRENT_STATE.md -Destination $folder
 New-Item -ItemType Directory -Path "$folder/test-results" | Out-Null
 Get-ChildItem app/build/test-results/test*UnitTest -Directory | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination "$folder/test-results" -Recurse}
 git archive --format=zip "--output=$folder/Food.Logger-source.zip" HEAD
 if($LASTEXITCODE -ne 0){throw 'Source archive failed.'}
 if((Get-FileHash "$folder/$name" -Algorithm SHA256).Hash.ToLower() -ne $info.apk_sha256){throw 'Copied APK checksum differs.'}
 Copy-Item -LiteralPath "$folder/$name" -Destination deliverables/Food.Logger-release.apk
 @("$relative/$name","Source: $($info.source_commit)","SHA-256: $($info.apk_sha256)",'PRIVATE: contains USDA key; keep local.') | Set-Content deliverables/LATEST-RELEASE.txt
 Write-Output "Private release published: $folder/$name"
} finally {Pop-Location}
