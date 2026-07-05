# Music Server - Desktop (Linux)



Este módulo fornece o suporte para rodar o Music Server em ambientes Desktop, especificamente focado em Linux, conforme solicitado na issue #10.



## Como rodar



Para iniciar a aplicação desktop, utilize o comando Gradle:



```bash

./gradlew :desktop:run

```



## Estrutura



O módulo utiliza **Compose Multiplatform** para a interface gráfica e **Ktor** para o servidor de mídia, permitindo o compartilhamento de lógica (em futuras refatorações) entre Android e Desktop.

