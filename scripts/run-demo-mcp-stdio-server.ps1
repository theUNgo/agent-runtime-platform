Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$targetDir = Join-Path $projectRoot "target"
$classesDir = Join-Path $targetDir "classes"
$dependencyDir = Join-Path $targetDir "dependency"
$preferredJavaHome = "C:\Users\87710\.jdks\corretto-21.0.10"

if (Test-Path $preferredJavaHome) {
    $env:JAVA_HOME = $preferredJavaHome
}

if (-not $env:JAVA_HOME -or [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw "JAVA_HOME is not set. Please point it to JDK 21 first."
}

$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $javaExe)) {
    throw "java.exe was not found under JAVA_HOME: $javaExe"
}

if (-not (Test-Path $classesDir)) {
    throw "target/classes was not found. Please run mvn -DskipTests package first."
}

if (-not (Test-Path $dependencyDir)) {
    throw "target/dependency was not found. Please run mvn -DskipTests package first."
}

$classpath = "$classesDir;$dependencyDir\*"
& $javaExe -cp $classpath com.example.agentruntime.demo.DemoMcpStdioServer
