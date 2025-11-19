# Maven Installation Script for Windows
# Run this in PowerShell as Administrator (or regular user for local install)

$mavenVersion = "3.9.6"
$mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
$installPath = "$env:USERPROFILE\maven"
$zipFile = "$env:TEMP\maven.zip"

Write-Host "Installing Apache Maven $mavenVersion..." -ForegroundColor Green

# Download Maven
Write-Host "Downloading Maven from Apache archive..." -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri $mavenUrl -OutFile $zipFile
    Write-Host "Download completed!" -ForegroundColor Green
} catch {
    Write-Host "Failed to download Maven. Error: $_" -ForegroundColor Red
    exit 1
}

# Extract Maven
Write-Host "Extracting Maven to $installPath..." -ForegroundColor Yellow
if (Test-Path $installPath) {
    Remove-Item -Path $installPath -Recurse -Force
}
Expand-Archive -Path $zipFile -DestinationPath $env:USERPROFILE -Force
Rename-Item -Path "$env:USERPROFILE\apache-maven-$mavenVersion" -NewName "maven" -Force

# Clean up
Remove-Item -Path $zipFile -Force

# Add to PATH for current session
$env:PATH += ";$installPath\bin"
$env:M2_HOME = $installPath

# Add to PATH permanently (User level)
Write-Host "Adding Maven to system PATH..." -ForegroundColor Yellow
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($currentPath -notlike "*$installPath\bin*") {
    [Environment]::SetEnvironmentVariable("Path", "$currentPath;$installPath\bin", "User")
    Write-Host "Added Maven to PATH (User level)" -ForegroundColor Green
}

# Set M2_HOME
[Environment]::SetEnvironmentVariable("M2_HOME", $installPath, "User")

Write-Host ""
Write-Host "Maven installed successfully!" -ForegroundColor Green
Write-Host "Location: $installPath" -ForegroundColor Cyan
Write-Host ""
Write-Host "Please restart your terminal or run:" -ForegroundColor Yellow
Write-Host '  $env:PATH += ";$env:USERPROFILE\maven\bin"' -ForegroundColor Cyan
Write-Host ""
Write-Host "Then verify with: mvn --version" -ForegroundColor Yellow
