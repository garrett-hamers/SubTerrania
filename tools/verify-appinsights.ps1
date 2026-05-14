# Phase O-3 verification: post a synthetic Application Insights envelope using
# the same shape as ui/observability/Telemetry.kt, then query Log Analytics
# to confirm it arrived. This validates connection-string parsing, ingestion
# endpoint reachability, and envelope format without needing an Android device.

$rg = 'rg-subterranea'
$appi = 'appi-subterranea'
$workspace = 'log-subterranea'

$connStr = az monitor app-insights component show --app $appi --resource-group $rg --query connectionString -o tsv
$pairs = @{}
foreach ($entry in $connStr.Split(';')) {
    $idx = $entry.IndexOf('=')
    if ($idx -gt 0) {
        $pairs[$entry.Substring(0, $idx).Trim()] = $entry.Substring($idx + 1).Trim()
    }
}
$endpoint = $pairs['IngestionEndpoint'].TrimEnd('/')
$ikey = $pairs['InstrumentationKey']
Write-Host "Endpoint: $endpoint"
Write-Host "iKey:     $ikey"
Write-Host ""

# Build an Event envelope identical in shape to what Telemetry.kt produces.
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$verifyTag = "phase-o3-verify-" + (Get-Random -Maximum 999999)
$envelope = @{
    name = "Microsoft.ApplicationInsights.Event"
    time = $now
    iKey = $ikey
    tags = @{
        'ai.cloud.role' = 'subterranea-android'
        'ai.cloud.roleInstance' = [Guid]::NewGuid().ToString()
        'ai.application.ver' = '1.0.6-verify'
        'ai.device.osVersion' = 'Android verify'
        'ai.device.model' = 'Verify Script'
    }
    data = @{
        baseType = 'EventData'
        baseData = @{
            ver = 2
            name = 'phase_o3_verify'
            properties = @{
                version = '1.0.6-verify'
                versionCode = '7'
                deviceModel = 'Verify Script'
                apiLevel = '0'
                verifyTag = $verifyTag
            }
        }
    }
} | ConvertTo-Json -Depth 8 -Compress

Write-Host "Envelope:"
Write-Host $envelope
Write-Host ""

$url = "$endpoint/v2.1/track"
Write-Host "POST $url"
try {
    $resp = Invoke-WebRequest -Uri $url -Method Post -ContentType 'application/json; charset=utf-8' -Body $envelope -UseBasicParsing -TimeoutSec 15
    Write-Host "HTTP $($resp.StatusCode)"
    Write-Host $resp.Content
} catch {
    Write-Host "POST failed: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $sr.ReadToEnd()
    }
    exit 1
}

Write-Host ""
Write-Host "Verification tag: $verifyTag"
Write-Host "Telemetry usually appears in Log Analytics within 60-180 seconds."
Write-Host ""
Write-Host "To query later:"
Write-Host "  az monitor log-analytics query -w (az monitor log-analytics workspace show -g $rg -n $workspace --query customerId -o tsv) ``"
Write-Host "    --analytics-query `"AppEvents | where Properties.verifyTag == '$verifyTag' | project TimeGenerated, Name, Properties`""
