# Android build unlock (a-b / a-d)

Windows + Desktop + Android Studio часто держат файлы в `android/**/build`. Gradle тогда падает с `Unable to delete directory`.

Выполнить этот файл целиком:

- человек написал **a-b** или **a-d** (или просит подготовить build / sync);
- агент менял `android/` и человеку снова нужна Sync Project или сборка APK.

После шагов сказать, что можно собирать.

Не убивать Android Studio (`studio64`). Можно снять её `fsnotifier`. Не трогать `HiddenStore`, `secrets/`, исходники. Не использовать `robocopy /mir` — его блокирует автопроверка.

## 1. Снять демоны Gradle / Kotlin и fsnotifier

Остановить Java, у которых в командной строке есть `GradleDaemon`, `kotlin-daemon` или `kotlin-compiler`. Затем `fsnotifier` / `fsnotifier64`.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
  $_.CommandLine -match 'GradleDaemon|kotlin-daemon|kotlin-compiler'
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Get-Process -Name fsnotifier,fsnotifier64 -ErrorAction SilentlyContinue | Stop-Process -Force
```

Если `android\gradlew.bat` есть — ещё `.\gradlew.bat --stop` из `android\`.

Проверка: таких Java-процессов больше нет.

## 2. Удалить папки, которые Gradle не может перезаписать

Корень: `android\`. Если `Remove-Item` не смог — переименовать в `*.old`.

Частые ошибки: `merged_res_blame_folder`, `packaged_res`, `incremental\packageDebug\tmp`.

```powershell
$root = "android"
$paths = @(
  "$root\app\build\intermediates\incremental\packageDebug\tmp",
  "$root\app\build\intermediates\incremental\packageDebug",
  "$root\app\build\intermediates\incremental",
  "$root\app\build\intermediates\merged_res_blame_folder",
  "$root\app\build\intermediates\packaged_res",
  "$root\app\build\intermediates\project_dex_archive",
  "$root\app\build\intermediates\desugar_graph",
  "$root\app\build\intermediates\dex",
  "$root\calculator\build\.transforms",
  "$root\calculator\build\intermediates\runtime_library_classes_dir",
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

- Если папки из списка исчезли (или остались только `*.old`) — **можно собирать**.
- Если конкретную папку так и не удалось снять — назвать её. При повторной ошибке снова **a-b** / **a-d**.
