param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
 if(-not $AndroidSdk){throw 'Set ANDROID_HOME or pass -AndroidSdk.'}
 git diff --quiet HEAD --
 if($LASTEXITCODE -ne 0){throw 'Commit tracked source changes before publishing.'}
 $sourceCommit = (git rev-parse HEAD).Trim()
 $apk = Join-Path $repoRoot 'app/build/outputs/apk/debug/app-debug.apk'
 if(-not (Test-Path -LiteralPath $apk)){throw 'Run gradlew.bat testDebugUnitTest assembleDebug successfully first.'}
 $buildTools = Join-Path $AndroidSdk 'build-tools/34.0.0'
 $badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apk
 if($LASTEXITCODE -ne 0){throw 'APK metadata verification failed.'}
 $package = [regex]::Match(($badging -join "`n"),"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
 if(-not $package.Success -or $package.Groups[1].Value -ne 'com.foodlogger.debug'){throw 'Unexpected APK package.'}
 $versionCode = $package.Groups[2].Value
 $versionName = $package.Groups[3].Value
 $signature = & (Join-Path $buildTools 'apksigner.bat') verify --print-certs $apk
 if($LASTEXITCODE -ne 0){throw 'APK signature verification failed.'}
 $certificate = ($signature | Select-String 'Signer #1 certificate SHA-256 digest:').Line
 if(-not $certificate){throw 'Signing certificate is missing.'}
 $latestApk = Join-Path $repoRoot 'deliverables/Food.Logger-debug.apk'
 if(Test-Path -LiteralPath $latestApk){
  $previousSignature = & (Join-Path $buildTools 'apksigner.bat') verify --print-certs $latestApk
  if($LASTEXITCODE -ne 0){throw 'Previous APK signature verification failed.'}
  $previousCertificate = ($previousSignature | Select-String 'Signer #1 certificate SHA-256 digest:').Line
  if($certificate -ne $previousCertificate){throw 'Signing key differs from the previous APK. Do not clear data or replace keys.'}
 }
 $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
 $relative = "deliverables/builds/$versionName-code$versionCode-$stamp"
 $folder = Join-Path $repoRoot $relative
 New-Item -ItemType Directory -Path $folder | Out-Null
 $versionedApk = Join-Path $folder "Food.Logger-$versionName-code$versionCode-debug.apk"
 Copy-Item -LiteralPath $apk -Destination $versionedApk
 git archive --format=zip "--output=$folder/Food.Logger-source.zip" HEAD
 if($LASTEXITCODE -ne 0){throw 'Source ZIP creation failed.'}
 $checksum = (Get-FileHash -LiteralPath $versionedApk -Algorithm SHA256).Hash.ToLower()
 if((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLower() -ne $checksum){throw 'APK copy verification failed.'}
 Copy-Item -LiteralPath CURRENT_STATE.md -Destination $folder
 $tests = 'app/build/test-results/testDebugUnitTest'
 if(Test-Path -LiteralPath $tests){New-Item -ItemType Directory -Path "$folder/test-results" | Out-Null; Copy-Item "$tests/TEST-*.xml" "$folder/test-results"}
 @("Version: $versionName ($versionCode)","Package: com.foodlogger.debug","Source commit: $sourceCommit","APK SHA-256: $checksum",$certificate,'USDA key: local build configuration; never included in source ZIP.','No device/camera testing is implied by this package.') | Set-Content "$folder/BUILD_INFO.txt"
 if(Test-Path -LiteralPath $latestApk){Copy-Item -LiteralPath $latestApk -Destination "$folder/previous-latest-debug.apk"}
 $latestSource = Join-Path $repoRoot 'deliverables/Food.Logger-source.zip'
 if(Test-Path -LiteralPath $latestSource){Copy-Item -LiteralPath $latestSource -Destination "$folder/previous-latest-source.zip"}
 Copy-Item -LiteralPath "$folder/Food.Logger-source.zip" -Destination $latestSource
 Copy-Item -LiteralPath CURRENT_STATE.md -Destination 'deliverables/CURRENT_STATE.md'
 Copy-Item -LiteralPath $versionedApk -Destination $latestApk
 @("$relative/$(Split-Path $versionedApk -Leaf)","Source commit: $sourceCommit","APK SHA-256: $checksum") | Set-Content 'deliverables/LATEST.txt'
 Write-Output "Published: $versionedApk"
 Write-Output "Latest APK: $latestApk"
} finally {Pop-Location}
