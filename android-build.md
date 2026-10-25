# Android build unlock (a-b)

Windows + Desktop + Android Studio часто держат файлы в `android/**/build`. Gradle тогда падает с `Unable to delete directory`.

Когда человек пишет **a-b**, выполнить этот файл целиком и после этого сказать, что можно собирать APK.

Не убивать Android Studio (`studio64`). Не трогать `HiddenStore`, `secrets/`, исходники.

## 1. Снять демоны Gradle / Kotlin

Остановить только процессы Java, у которых в командной строке есть `GradleDaemon`, `kotlin-daemon` или `kotlin-compiler`.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
  $_.CommandLine -match 'GradleDaemon|kotlin-daemon|kotlin-compiler'
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
```

Если `android\gradlew.bat` есть — ещё `.\gradlew.bat --stop` из `android\`.

Проверка: таких Java-процессов больше нет.

## 2. Удалить папки, которые Gradle не может перезаписать

Корень: `android\`. Если `Remove-Item` не смог — переименовать в `*.old` и удалить уже это имя.

```powershell
$root = "android"
$paths = @(
  "$root\app\build\intermediates\merged_res_blame_folder",
  "$root\app\build\intermediates\packaged_res",
  "$root\app\build\intermediates\incremental",
  "$root\app\build\intermediates\project_dex_archive",
  "$root\app\build\intermediates\desugar_graph",
  "$root\app\build\intermediates\dex",
  "$root\calculator\build\.transforms",
  "$root\calculator\build\intermediates"
)
foreach ($p in $paths) {
  if (-not (Test-Path $p)) { continue }
  Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue
  if (Test-Path $p) {
    $old = "$p.old"
    if (Test-Path $old) { Remove-Item -LiteralPath $old -Recurse -Force -ErrorAction SilentlyContinue }
    Rename-Item -LiteralPath $p -NewName (Split-Path $old -Leaf) -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $old -Recurse -Force -ErrorAction SilentlyContinue
  }
}
```

## 3. Что сказать человеку

- Если все папки из списка исчезли (или остались только `*.old`, которые Studio ещё держит) — **можно собирать**.
- Если конкретную папку так и не удалось снять — назвать её: её ещё держит Android Studio. Сборку всё равно можно пробовать; при повторной ошибке снова **a-b**.
