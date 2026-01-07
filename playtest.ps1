$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
Write-Host "=== SubTerrania Automated Playtest ===" -ForegroundColor Cyan

$allResults = @()

for ($gameNum = 1; $gameNum -le 10; $gameNum++) {
    Write-Host "`nGame $gameNum/10 starting..." -ForegroundColor Yellow
    
    # Restart app fresh
    & $adb shell am force-stop com.axialgalileo.subterranea 2>$null | Out-Null
    Start-Sleep -Seconds 1
    & $adb shell am start -n com.axialgalileo.subterranea/.MainActivity 2>$null | Out-Null
    Start-Sleep -Seconds 2
    
    $startTime = Get-Date
    $won = $false
    $turnCount = 0
    
    for ($turn = 1; $turn -le 100; $turn++) {
        $turnCount = $turn
        
        # Get UI state
        $ui = & $adb exec-out uiautomator dump /dev/tty 2>$null
        
        # Check victory
        if ($ui -match 'VICTORY') { 
            $won = $true
            break 
        }
        
        # Dismiss any popups
        if ($ui -match 'dismiss') { 
            & $adb shell input tap 540 1500 | Out-Null
            Start-Sleep -Milliseconds 200
        }
        
        # Roll dice (button at bottom left)
        & $adb shell input tap 120 2205 | Out-Null
        Start-Sleep -Milliseconds 350
        
        # Select a tile in center area
        & $adb shell input tap 540 1000 | Out-Null
        Start-Sleep -Milliseconds 200
        
        # Try build button
        & $adb shell input tap 325 2205 | Out-Null
        Start-Sleep -Milliseconds 300
        
        # Check if build menu opened
        $ui = & $adb exec-out uiautomator dump /dev/tty 2>$null
        if ($ui -match 'Build Structure') {
            # Click first structure in list
            & $adb shell input tap 540 720 | Out-Null
            Start-Sleep -Milliseconds 200
        }
        
        # Dismiss any popup
        $ui = & $adb exec-out uiautomator dump /dev/tty 2>$null
        if ($ui -match 'dismiss') { 
            & $adb shell input tap 540 1500 | Out-Null
            Start-Sleep -Milliseconds 150
        }
        
        # End turn (button at bottom right)
        & $adb shell input tap 966 2205 | Out-Null
        Start-Sleep -Milliseconds 250
        
        # Progress indicator every 25 turns
        if ($turn % 25 -eq 0) {
            $ui = & $adb exec-out uiautomator dump /dev/tty 2>$null
            $vp = "?"
            if ($ui -match '(\d+)/10 VP') { $vp = $matches[1] }
            Write-Host "  Turn $turn, VP: $vp" -ForegroundColor Gray
        }
    }
    
    $elapsed = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 1)
    
    # Final state check
    $ui = & $adb exec-out uiautomator dump /dev/tty 2>$null
    $finalVP = "?"
    if ($ui -match '(\d+)/10 VP') { $finalVP = $matches[1] }
    if ($ui -match 'VICTORY') { $won = $true }
    
    $status = if ($won) { "WIN" } else { "DNF" }
    
    $result = [PSCustomObject]@{
        Game = $gameNum
        Status = $status
        Turns = $turnCount
        Seconds = $elapsed
        VP = $finalVP
    }
    $allResults += $result
    
    if ($won) {
        Write-Host "  Result: WIN in $turnCount turns ($elapsed seconds)" -ForegroundColor Green
    } else {
        Write-Host "  Result: Did not finish after $turnCount turns, VP: $finalVP" -ForegroundColor Red
    }
}

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "           PLAYTEST RESULTS SUMMARY         " -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

$allResults | Format-Table -AutoSize

$wins = @($allResults | Where-Object { $_.Status -eq 'WIN' })
$winCount = $wins.Count

Write-Host "`nStatistics:" -ForegroundColor Yellow
Write-Host "  Win Rate: $winCount/10 ($($winCount * 10)%)"

if ($winCount -gt 0) {
    $avgTurns = [math]::Round(($wins | Measure-Object -Property Turns -Average).Average, 1)
    $avgSecs = [math]::Round(($wins | Measure-Object -Property Seconds -Average).Average, 1)
    $minTurns = ($wins | Measure-Object -Property Turns -Minimum).Minimum
    $maxTurns = ($wins | Measure-Object -Property Turns -Maximum).Maximum
    
    Write-Host "  Average Turns to Win: $avgTurns"
    Write-Host "  Average Time to Win: $avgSecs seconds"
    Write-Host "  Fastest Win: $minTurns turns"
    Write-Host "  Slowest Win: $maxTurns turns"
}

Write-Host "`nDone!" -ForegroundColor Green
