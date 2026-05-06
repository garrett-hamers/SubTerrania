<#
.SYNOPSIS
    Loads SubTerrania release-signing material from an Azure Key Vault into the current
    PowerShell session as environment variables, plus a temporary keystore file.

.DESCRIPTION
    This script must be DOT-SOURCED (note the leading "." and space) so the environment
    variables it sets and the cleanup handler it registers persist in the calling shell:

        . .\tools\load-signing-from-akv.ps1

    On success the following are configured for the lifetime of the PowerShell session:

        $env:KEYSTORE_PATH      - absolute path to a temp PKCS12 keystore
        $env:KEYSTORE_PASSWORD  - keystore password (matches Gradle signing config)
        $env:KEY_PASSWORD       - key entry password (PKCS12: same as keystore password)
        $env:KEY_ALIAS          - key alias inside the keystore

    The temp keystore is written under $env:TEMP with a random suffix, locked down with
    an ACL that grants Read access only to the current user, and registered for deletion
    via Register-EngineEvent PowerShell.Exiting (best effort) plus an explicit
    Unregister-SubterraneaSigning function exposed for manual cleanup.

    Prerequisites:
        - Azure CLI on PATH (`az`)
        - Logged in: `az login`
        - Caller has at minimum the "Key Vault Secrets User" role on the target vault
          (or equivalent RBAC). The vault uses RBAC for the data plane, not legacy
          access policies.

.PARAMETER VaultName
    The Azure Key Vault to read from. Defaults to $env:SUBTERRANEA_VAULT.

.EXAMPLE
    PS> $env:SUBTERRANEA_VAULT = 'kv-subterranea-XXXX'
    PS> . .\tools\load-signing-from-akv.ps1
    PS> .\gradlew :app:bundleRelease

.EXAMPLE
    PS> . .\tools\load-signing-from-akv.ps1 -VaultName kv-subterranea-XXXX

.NOTES
    Phase M (2026): replaces the on-disk keystore + plaintext password file. See
    SECURITY.md "Loading signing material from Azure Key Vault" for the full
    procedure and rotation steps.
#>

[CmdletBinding()]
param(
    [string]$VaultName = $env:SUBTERRANEA_VAULT
)

$ErrorActionPreference = 'Stop'

if (-not $VaultName) {
    throw "VaultName required. Pass -VaultName <name> or set `$env:SUBTERRANEA_VAULT first."
}

# 1. Sanity check that az CLI is reachable and the user is logged in.
$azCmd = Get-Command az -ErrorAction SilentlyContinue
if (-not $azCmd) {
    throw "Azure CLI ('az') not found on PATH. Install Azure CLI and run 'az login' first."
}

try {
    $accountJson = az account show --output json 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $accountJson) {
        throw "az account show failed."
    }
} catch {
    throw "Not logged in to Azure CLI. Run 'az login' (interactive) and retry."
}

$account = $accountJson | ConvertFrom-Json
Write-Host "Azure account: $($account.user.name) | subscription: $($account.name)"
Write-Host "Vault: $VaultName"

function Get-AkvSecret {
    param([string]$Name)
    $val = az keyvault secret show --vault-name $VaultName --name $Name --query value -o tsv 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $val) {
        throw "Failed to read secret '$Name' from vault '$VaultName'. Verify RBAC role and that the secret exists."
    }
    return $val
}

# 2. Pull all four secrets up front so a missing one fails before we write any state.
$keystoreB64     = Get-AkvSecret -Name 'keystore-base64'
$keystorePass    = Get-AkvSecret -Name 'keystore-password'
$keyPass         = Get-AkvSecret -Name 'key-password'
$keyAlias        = Get-AkvSecret -Name 'key-alias'

# 3. Materialize the temp keystore.
$rand    = [guid]::NewGuid().ToString('N').Substring(0, 8)
$tmpPath = Join-Path $env:TEMP "subterranea-$rand.keystore"
$bytes   = [Convert]::FromBase64String($keystoreB64)
[IO.File]::WriteAllBytes($tmpPath, $bytes)

# 4. Lock down ACL: remove inheritance, grant Read to current user only.
try {
    icacls $tmpPath /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null
} catch {
    Write-Warning "Could not tighten ACL on $tmpPath ($_). File contents are still owned by you."
}

# 5. Export env vars for Gradle.
$env:KEYSTORE_PATH     = $tmpPath
$env:KEYSTORE_PASSWORD = $keystorePass
$env:KEY_PASSWORD      = $keyPass
$env:KEY_ALIAS         = $keyAlias

# 6. Best-effort cleanup on shell exit.
#    Register-EngineEvent's correct signature for built-in events is to pass
#    the event name as -SourceIdentifier directly. Use -SupportEvent (a switch)
#    to keep the registration hidden from Get-Job.
try {
    Register-EngineEvent -SourceIdentifier PowerShell.Exiting -SupportEvent -Action {
        try { Remove-Item $using:tmpPath -Force -ErrorAction SilentlyContinue } catch {}
    } | Out-Null
} catch {
    Write-Warning "Could not register exit cleanup ($_). Run Unregister-SubterraneaSigning when done."
}

function global:Unregister-SubterraneaSigning {
    <#
        Removes the temp keystore and clears signing env vars in this session.
        Useful if you want to release the file lock without exiting the shell.
    #>
    if ($env:KEYSTORE_PATH -and (Test-Path $env:KEYSTORE_PATH)) {
        Remove-Item $env:KEYSTORE_PATH -Force -ErrorAction SilentlyContinue
    }
    Remove-Item Env:\KEYSTORE_PATH      -ErrorAction SilentlyContinue
    Remove-Item Env:\KEYSTORE_PASSWORD  -ErrorAction SilentlyContinue
    Remove-Item Env:\KEY_PASSWORD       -ErrorAction SilentlyContinue
    Remove-Item Env:\KEY_ALIAS          -ErrorAction SilentlyContinue
    Write-Host "Cleared SubTerrania signing material from session."
}

Write-Host ""
Write-Host "Loaded signing material from $VaultName."
Write-Host "  KEYSTORE_PATH = $tmpPath"
Write-Host "  KEY_ALIAS     = $keyAlias"
Write-Host "  Temp keystore is auto-deleted on shell exit, or call Unregister-SubterraneaSigning."
