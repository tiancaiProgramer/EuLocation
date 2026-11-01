$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdkBin = 'G:\Data\ApkFuture\Loading\shared-tools\android-build\jdk\jdk-17.0.20.1+1\bin'
$jsonJar = Join-Path (Split-Path $projectRoot -Parent) 'Loading\shared-tools\java-libs\json-20180813.jar'
$testOutput = Join-Path $projectRoot 'app\build\didi-policy-tests'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$sources = @(
    (Join-Path $projectRoot 'app\src\main\java\com\ux5\hyperos3\elemeisland\DidiPolicy.java'),
    (Join-Path $projectRoot 'app\src\main\java\com\ux5\hyperos3\elemeisland\DidiPayload.java'),
    (Join-Path $projectRoot 'app\src\main\java\com\ux5\hyperos3\elemeisland\DidiProfile.java'),
    (Join-Path $projectRoot 'tests\DidiPolicyTest.java'),
    (Join-Path $projectRoot 'tests\DidiPayloadTest.java'),
    (Join-Path $projectRoot 'tests\DidiProfileTest.java')
)
& "$jdkBin\javac.exe" -encoding UTF-8 -cp $jsonJar -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
foreach ($test in @('DidiPolicyTest', 'DidiPayloadTest', 'DidiProfileTest')) {
    & "$jdkBin\java.exe" -cp "$testOutput;$jsonJar" "com.ux5.hyperos3.elemeisland.$test"
    if ($LASTEXITCODE -ne 0) { throw "$test failed" }
}
