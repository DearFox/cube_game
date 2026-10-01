Попытка собрать проект

Что-бы запустить на windows:
Скачайте сборку игры и распакуйте архив cube-game-build.zip\distributions\cube_game.zip\ в удобное для вас место.

Скачайте репозиторий как архив и переместите папку src в cube_game\bin

Перейдите на https://www.lwjgl.org/browse/release/3.3.4/bin и скачайте все необходимые для вашей платформы нативные библиотеки.
В случае с windows это: 
lwjgl-3.3.4-natives-windows.jar
lwjgl-glfw-3.3.4-natives-windows.jar
lwjgl-openal-3.3.4-natives-windows.jar
lwjgl-opengl-3.3.4-natives-windows.jar
lwjgl-stb-3.3.4-natives-windows.jar

Их следует направить в cube_game\lib

Перейдите в cube_game\bin и отредактируйте cube_game.bat
Вам нужно заменить все нативные библиотеки в строке 71 начинающейся с set CLASSPATH с linux на windows.

После этого игра должна запуститься через cube_game.bat

Если что-то пойдёт не так и игра с консолью закроются - запустите cube_game.bat через cmd, что-бы консоль не закрывалась после ошибки и вылета игры. 
