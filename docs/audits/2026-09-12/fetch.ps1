param([string]$RequestFile='requests.json',[string]$ManifestFile='fetch-manifest.json')
$ErrorActionPreference = 'Stop'
$auditRoot = $PSScriptRoot
$projectRoot = (Resolve-Path (Join-Path $auditRoot '../../..')).Path
$requests = Get-Content -Raw (Join-Path $auditRoot $RequestFile) | ConvertFrom-Json
$rawDir = Join-Path $auditRoot 'raw'
New-Item -ItemType Directory -Force $rawDir | Out-Null
$manifest = [System.Collections.Generic.List[object]]::new()
$existing = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'verify/cache') -Recurse -Filter '*.provenance.json' | ForEach-Object {
    $stamp = Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json
    [pscustomobject]@{Stamp=$stamp; Directory=$_.DirectoryName}
}
foreach ($q in $requests) {
    $target = Join-Path $rawDir ($q.id + '.raw')
    $record = [ordered]@{id=$q.id; kind=$q.kind; url=$q.url; parameters=$q; retrievedAtUtc=[DateTime]::UtcNow.ToString('o'); origin='live'; httpStatus=0; file=('raw/' + $q.id + '.raw'); sha256=$null; error=$null}
    $cached = $existing | Where-Object { $_.Stamp.url -eq $q.url } | Select-Object -First 1
    if ($cached) {
        $cachedFile = Join-Path $cached.Directory $cached.Stamp.rawArtifactFile
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $cachedFile).Hash.ToLowerInvariant() -ne $cached.Stamp.responseSha256) { throw "Invalid cached checksum: $cachedFile" }
        Copy-Item -LiteralPath $cachedFile -Destination $target
        $record.origin='stored-cache'; $record.httpStatus=$cached.Stamp.httpStatus; $record.retrievedAtUtc=$cached.Stamp.retrievedAtUtc
    } else {
        $status = & curl.exe --silent --show-error --location --max-time 25 --connect-timeout 10 --user-agent 'panchang-service-verify/0.1 (bounded accuracy audit)' --output $target --write-out '%{http_code}' $q.url 2> (Join-Path $rawDir ($q.id + '.stderr.txt'))
        $record.httpStatus=[int]($status | Select-Object -Last 1)
        if ($LASTEXITCODE -ne 0) { $record.error=Get-Content -Raw (Join-Path $rawDir ($q.id + '.stderr.txt')) }
        Start-Sleep -Seconds 3
    }
    if (Test-Path -LiteralPath $target) { $record.sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLowerInvariant() }
    $manifest.Add([pscustomobject]$record)
    ConvertTo-Json -Depth 12 -InputObject @($manifest.ToArray()) | Set-Content -Encoding utf8 (Join-Path $auditRoot $ManifestFile)
    Write-Output "$($q.id): $($record.httpStatus) $($record.origin)"
}
