param()
$ErrorActionPreference = 'Stop'
$connectorRoot = Split-Path -Parent $PSScriptRoot
$buildDirectory = Join-Path $connectorRoot 'release\desktop-build'
[System.IO.Directory]::CreateDirectory($buildDirectory) | Out-Null
$launcherPath = Join-Path $buildDirectory 'NexusEngineeringConnector.exe'
$manifestPath = Join-Path $connectorRoot 'desktop\app.manifest'
$parameters = New-Object System.CodeDom.Compiler.CompilerParameters
$parameters.GenerateExecutable = $true
$parameters.GenerateInMemory = $false
$parameters.OutputAssembly = $launcherPath
$parameters.CompilerOptions = '/target:winexe /platform:x64 /optimize+ /win32manifest:"' + $manifestPath + '"'
$parameters.ReferencedAssemblies.AddRange(@('System.dll', 'System.Core.dll', 'System.Drawing.dll', 'System.Windows.Forms.dll', 'System.Web.Extensions.dll'))
$provider = New-Object Microsoft.CSharp.CSharpCodeProvider
$sourcePaths = [string[]](Get-ChildItem -LiteralPath (Join-Path $connectorRoot 'desktop') -Filter '*.cs' -File | ForEach-Object { $_.FullName })
$result = $provider.CompileAssemblyFromFile($parameters, $sourcePaths)
if ($result.Errors.HasErrors) {
    $result.Errors | ForEach-Object { [Console]::Error.WriteLine($_.ToString()) }
    exit 1
}
Copy-Item -LiteralPath (Join-Path $connectorRoot 'desktop\app.config') -Destination ($launcherPath + '.config')
[Console]::WriteLine($launcherPath)
$provider.Dispose()
