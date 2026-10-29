# kek-earlywindow

Ранний экран загрузки Minecraft для сервера «Искра» (KEK.Team): провайдер `ImmediateWindowProvider` с именем `kekearlywindow` для FML (NeoForge).

Окно, выбор версии OpenGL, поток отрисовки и передача окна игре перенесены из `net.neoforged.fancymodloader:earlydisplay` 4.0.45 (Forge Development LLC and contributors, LGPL-2.1). Изменённые файлы помечены строкой «Изменено KEK.Team». Код распространяется под той же лицензией — см. `LICENSE`.

Провайдер включает KEK Launcher: кладёт jar в classpath игры и пишет `earlyWindowProvider = "kekearlywindow"` в `config/fml.toml`, только если версия earlydisplay в NeoForge совпадает с атрибутом манифеста `Kek-Earlydisplay-Version`. При любой ошибке своего окна все вызовы уходят стандартному `DisplayWindow`.

## Сборка

```bash
./gradlew build    # build/libs/kek-earlywindow-<версия>.jar, jar воспроизводимый
```
