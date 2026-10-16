<#
.SYNOPSIS
使用当前Windows用户的DPAPI保护合成或运行凭证；只从标准输入读取敏感内容。
#>
param([ValidateSet('protect','unprotect')][string]$Mode)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$inputText = [Console]::In.ReadToEnd()
if ($Mode -eq 'protect') {
  $inputBytes = [System.Text.Encoding]::UTF8.GetBytes($inputText)
  $protected = [System.Security.Cryptography.ProtectedData]::Protect($inputBytes, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
  [Console]::Out.Write([Convert]::ToBase64String($protected))
} else {
  $inputBytes = [Convert]::FromBase64String($inputText)
  $plain = [System.Security.Cryptography.ProtectedData]::Unprotect($inputBytes, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
  [Console]::Out.Write([System.Text.Encoding]::UTF8.GetString($plain))
}
