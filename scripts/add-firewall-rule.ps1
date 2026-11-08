# ==============================================================================
# Script: add-firewall-rule.ps1
# Description: Creates a Windows Defender Firewall inbound rule allowing TCP 3307
# Usage (Run as Administrator):
#   powershell -ExecutionPolicy Bypass -File scripts\add-firewall-rule.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\add-firewall-rule.ps1 -RemoteAddress "192.168.29.0/24"
# ==============================================================================

[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [string]$RemoteAddress = "Any",

    [Parameter(Mandatory = $false)]
    [int]$Port = 3307
)

$RuleName = "KEC_VAC_MySQL_3307"
$DisplayName = "MySQL Database Remote Host (Port $Port)"

# Check for Administrator elevation
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Warning "[!] This script requires Administrator privileges. Please re-run in an elevated PowerShell terminal."
    Write-Host "Tip: Right-click PowerShell -> 'Run as Administrator', then run:"
    Write-Host "powershell -ExecutionPolicy Bypass -File `"$PSCommandPath`" -RemoteAddress `"$RemoteAddress`""
    exit 1
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "           KEC VAC ENGINE - INBOUND FIREWALL RULE CONFIGURATION                 " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "Configuring Windows Defender Firewall for Database Host (Student 2 Role)..."
Write-Host "Target Port   : $Port (TCP)"
Write-Host "Remote Address: $RemoteAddress"
Write-Host ""

# Remove any existing rule with same display name to avoid duplicates
$existing = Get-NetFirewallRule -DisplayName $DisplayName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "[-] Removing existing firewall rule '$DisplayName'..." -ForegroundColor Yellow
    Remove-NetFirewallRule -DisplayName $DisplayName -ErrorAction SilentlyContinue
}

# Create new Inbound Rule
Write-Host "[+] Creating Inbound Firewall Rule: '$DisplayName'..." -ForegroundColor Green
$rule = New-NetFirewallRule `
    -Name $RuleName `
    -DisplayName $DisplayName `
    -Description "Allows remote JDBC connectivity for KEC VAC & Skill Certification Engine Database Host" `
    -Direction Inbound `
    -Protocol TCP `
    -LocalPort $Port `
    -Action Allow `
    -RemoteAddress $RemoteAddress `
    -Profile Any `
    -Enabled True

Write-Host ""
Write-Host ">>> FIREWALL RULE CREATED SUCCESSFULLY! <<<" -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "FIREWALL RULE VERIFICATION PROOF:" -ForegroundColor Yellow
Write-Host "================================================================================" -ForegroundColor Cyan

# Display rule proof details
Get-NetFirewallRule -DisplayName $DisplayName | Select-Object Name, DisplayName, Direction, Action, Enabled | Format-List
Get-NetFirewallRule -DisplayName $DisplayName | Get-NetFirewallPortFilter | Select-Object Protocol, LocalPort, RemotePort | Format-List
Get-NetFirewallRule -DisplayName $DisplayName | Get-NetFirewallAddressFilter | Select-Object LocalAddress, RemoteAddress | Format-List

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "Proof Command to run anytime:" -ForegroundColor White
Write-Host "Get-NetFirewallRule -DisplayName `"$DisplayName`" | Get-NetFirewallPortFilter" -ForegroundColor Gray
Write-Host "================================================================================" -ForegroundColor Cyan
