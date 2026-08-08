# Self-contained build for GestureNav — PowerShell (bypasses build.bat issues)
$ErrorActionPreference = 'Stop'

$ROOT = 'D:\旧电脑备份\m3hu折腾项目\GestureNav'
$SDK  = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$BT   = "$SDK\build-tools\34.0.0"
$PLAT = "$SDK\platforms\android-30"
$JH   = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$AAPT2 = "$BT\aapt2.exe"
$JAVAC = "$JH\bin\javac.exe"
$D8    = "$BT\d8.bat"
$ZIPALIGN = "$BT\zipalign.exe"
$APKSIGNER = "$BT\apksigner.bat"

$SRC   = "$ROOT\src"
$RES   = "$ROOT\res"
$MAN   = "$ROOT\AndroidManifest.xml"
$BLD   = Join-Path $ROOT 'buildtmp'
$OBJ   = Join-Path $BLD 'obj'
$GEN   = Join-Path $BLD 'gen'
$DEX   = Join-Path $BLD 'dex'

if (Test-Path $BLD) { Remove-Item $BLD -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OBJ,$GEN,$DEX | Out-Null

Write-Host "[1/7] aapt2 compile"
Push-Location $RES
& $AAPT2 compile -o $OBJ --dir . | Out-Null
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
Pop-Location

Write-Host "[2/7] aapt2 link"
$flat = (Get-ChildItem "$OBJ\*.flat" | ForEach-Object { $_.FullName }) -join ' '
& $AAPT2 link -o "$BLD\base.apk" -I "$PLAT\android.jar" --manifest $MAN --java $GEN --min-sdk-version 30 --target-sdk-version 30 $flat.Split(' ') | Out-Null
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/7] javac"
$srcs = (Get-ChildItem "$SRC\com\m3h\gesturenav\*.java" | ForEach-Object { $_.FullName }) -join ';'
$gens = (Get-ChildItem "$GEN\com\m3h\gesturenav\*.java" | ForEach-Object { $_.FullName }) -join ';'
& $JAVAC -encoding UTF-8 -d $OBJ -classpath "$PLAT\android.jar" -sourcepath "$SRC;$GEN" --release 11 $srcs.Split(';') $gens.Split(';')
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/7] d8"
$cls = (Get-ChildItem "$OBJ\com\m3h\gesturenav\*.class" | ForEach-Object { $_.FullName })
& $D8 --lib "$PLAT\android.jar" --output $DEX $cls
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/7] merge dex into apk (ZipFile)"
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
Copy-Item "$BLD\base.apk" "$BLD\merged.apk" -Force
$zip = [System.IO.Compression.ZipFile]::Open("$BLD\merged.apk", [System.IO.Compression.ZipArchiveMode]::Update)
$entry = $zip.CreateEntry("classes.dex")
$ws = $entry.Open()
$bytes = [System.IO.File]::ReadAllBytes("$DEX\classes.dex")
$ws.Write($bytes, 0, $bytes.Length)
$ws.Dispose(); $zip.Dispose()

Write-Host "[6/7] zipalign"
& $ZIPALIGN -p 4 "$BLD\merged.apk" "$BLD\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[7/7] sign"
# 固定签名密钥位置（不在 buildtmp 内），避免每次构建都换签名导致无法 -r 覆盖安装
$KEYSTORE = "$ROOT\debug.keystore"
if (-not (Test-Path $KEYSTORE)) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "$JH\bin\keytool.exe"
    $psi.Arguments = '-genkey -v -keystore "' + $KEYSTORE + '" -alias debug -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug, OU=Dev, O=M3H, L=SZ, ST=GD, C=CN" -noprompt'
    $psi.UseShellExecute = $false
    $psi.RedirectStandardError = $true
    $psi.RedirectStandardOutput = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    $p.WaitForExit()
}
$ErrorActionPreference = 'Continue'
& $APKSIGNER sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android --out "$ROOT\GestureNav.apk" "$BLD\aligned.apk" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host ""
Write-Host "BUILD SUCCESS -> $ROOT\GestureNav.apk"
