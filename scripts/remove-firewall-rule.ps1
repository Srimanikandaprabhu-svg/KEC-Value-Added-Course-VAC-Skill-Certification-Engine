# ==============================================================================
# Script: remove-firewall-rule.ps1
# Description: Removes the Windows Defender Firewall inbound rule for Port 3307
# Usage (Run as Administrator):
#   powershell -ExecutionPolicy Bypass -File scripts\remove-firewall-rule.ps1
# ==============================================================================

[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [int]$Port = 3307
)

$DisplayName = "MySQL Database Remote Host (Port $Port)"

# Check for Administrator elevation
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Warning "[!] This script requires Administrator privileges. Please re-run in an elevated PowerShell terminal."
    exit 1
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "           KEC VAC ENGINE - FIREWALL RULE TEARDOWN                               " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

$existing = Get-NetFirewallRule -DisplayName $DisplayName -ErrorAction SilentlyContinue
if ($existing) {
    Remove-NetFirewallRule -DisplayName $DisplayName
    Write-Host "[+] Successfully removed firewall rule: '$DisplayName'" -ForegroundColor Green
} else {
    Write-Host "[-] Firewall rule '$DisplayName' not found. Nothing to remove." -ForegroundColor Yellow
}

Write-Host "================================================================================" -ForegroundColor Cyan
