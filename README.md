# NewBedWars

Plugin de BedWars para `Minecraft 1.8.8` com setup totalmente in-game, arquitetura modular e foco atual em partidas `1v1`.

O projeto foi pensado para evitar o setup manual chato por arquivo e para permitir que o mesmo mapa-base gere varias partidas por meio de clones runtime, sem destruir o mundo original.

## Sumario

- [Visao Geral](#visao-geral)
- [Status Atual](#status-atual)
- [Principais Recursos](#principais-recursos)
- [Compatibilidade](#compatibilidade)
- [Dependencias](#dependencias)
- [Instalacao](#instalacao)
- [Estrutura de Pastas no Servidor](#estrutura-de-pastas-no-servidor)
- [Inicio Rapido](#inicio-rapido)
- [Comandos](#comandos)
- [Permissoes](#permissoes)
- [Guia Completo de Setup](#guia-completo-de-setup)
- [Fluxo da Partida](#fluxo-da-partida)
- [NPCs](#npcs)
- [Lojas, Itens e Upgrades](#lojas-itens-e-upgrades)
- [Geradores](#geradores)
- [Scoreboard e Tablist](#scoreboard-e-tablist)
- [Arquivos e Persistencia](#arquivos-e-persistencia)
- [Licenca](#licenca)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Como Compilar](#como-compilar)
- [Troubleshooting](#troubleshooting)
- [Limitacoes Atuais](#limitacoes-atuais)
- [Roadmap Sugerido](#roadmap-sugerido)

## Visao Geral

`NewBedWars` e uma base completa para BedWars 1.8.8 feita em `Java + Spigot API 1.8.8`, com:

- configuracao de arena feita dentro do jogo
- menus de setup para times, spawns, cama, lojas, forjas e regioes
- persistencia em arquivos `YML`
- gerenciamento de multiplas arenas
- clones de mapa para reutilizar o mesmo template varias vezes
- scoreboard, tablist, mensagens e NPCs configuraveis
- arquitetura separada por `manager`, `listener`, `menu`, `arena`, `setup`, `npc`, `util` e `command`

O foco da base e entregar um plugin que voce consiga:

1. instalar no servidor
2. colocar um mapa pronto
3. criar a arena
4. configurar tudo sem editar arquivo manualmente
5. colocar para jogar

## Status Atual

O projeto nasceu com a ideia de SOLO, mas o estado atual do gameplay esta focado em `1v1`.

Na pratica, hoje o fluxo principal funciona assim:

- capacidade padrao de `2 jogadores`
- times ativos na partida: `RED` e `BLUE`
- selecao de cor no lobby de espera como preferencia
- definicao final dos times no inicio da partida
- scoreboard/tablist voltadas para `1v1`

A estrutura continua preparada para expansao futura, mas a experiencia pronta hoje e `1v1`.

## Principais Recursos

- setup de arena 100% in-game
- `/bw setup <arena>` com teleporte automatico para o mundo da arena
- spawn de espera, waiting room e configuracao por menus
- configuracao por time com:
  - spawn
  - cama
  - bau do time
  - ender chest
  - geradores
  - loja
  - melhorias
  - regiao da ilha
  - protecao inicial
- hologramas de setup para mostrar tudo que ja foi salvo
- limpeza rapida de elementos do setup por clique direito no menu
- clones runtime infinitos do mapa-base
- protecao do mapa original
- respawn sem tela de morte
- TNT e fireball customizadas
- NPC principal com `Citizens`
- NPCs de `Loja` e `Melhorias`
- scoreboard configuravel por estado
- tablist configuravel somente durante a partida
- mensagens configuraveis em `messages.yml`

## Compatibilidade

- `Minecraft 1.8.8`
- `Java 8`
- `Spigot 1.8.8`
- `PaperSpigot 1.8.8`

## Dependencias

Dependencia obrigatoria:

- `Citizens`

O plugin usa `Citizens` para o NPC principal de entrada e para NPCs runtime. Sem `Citizens`, o plugin nao inicia, porque ele esta como `depend` no [`plugin.yml`](C:/Users/picha/Documents/backup/NewBedWars/src/main/resources/plugin.yml).

## Instalacao

1. Coloque o `Citizens.jar` em `plugins/`.
2. Coloque o jar do `NewBedWars` em `plugins/`.
3. Inicie o servidor.
4. Configure o lobby com `/bw setlobby`.
5. Coloque pelo menos um mapa de arena na raiz do servidor.
6. Crie e configure a arena com `/bw create` e `/bw setup`.

## Estrutura de Pastas no Servidor

O mapa da arena nao vai dentro do plugin. Ele deve ficar como um mundo normal na raiz do servidor.

Exemplo:

```text
server/
  world/
  world_nether/
  world_the_end/
  BedwarsTreasure/
  plugins/
    Citizens/
    NewBedWars/
      config.yml
      messages.yml
      arenas/
```

Exemplo do que deve existir dentro do mapa:

```text
BedwarsTreasure/
  level.dat
  region/
  data/
```

## Inicio Rapido

### 1. Coloque o mapa pronto no servidor

Copie a pasta do mapa para a raiz do servidor:

```text
server/BedwarsTreasure/
```

### 2. Crie a arena

Se voce estiver dentro do mundo da arena:

```text
/bw create BedwarsTreasure
```

Ou informando o mundo explicitamente:

```text
/bw create BedwarsTreasure BedwarsTreasure
```

### 3. Entre no setup

```text
/bw setup BedwarsTreasure
```

### 4. Configure o lobby principal

No hub:

```text
/bw setlobby
```

Esse comando tambem marca o mundo do lobby para:

- nao spawnar mobs naturalmente
- ficar sempre de dia
- manter o clima controlado pelo plugin

### 5. Crie o NPC principal

```text
/bw npc solo
```

Ou com skin customizada:

```text
/bw npc solo Notch
```

O comando `solo` continua existindo por compatibilidade, mas o modo atual do gameplay esta em `1v1`.

## Comandos

### Administracao

| Comando | Descricao |
| --- | --- |
| `/bw create <arena> [world]` | Cria uma arena usando o mundo atual ou o mundo informado |
| `/bw delete <arena>` | Remove a arena e seu arquivo YML |
| `/bw list` | Lista arenas carregadas |
| `/bw setup <arena>` | Entra no modo setup e teleporta para o mundo da arena |
| `/bw setlobby` | Salva o lobby principal |
| `/bw join <arena>` | Entra na arena/template informado |
| `/bw leave` | Sai da arena atual |
| `/bw reload` | Recarrega `config.yml`, `messages.yml` e arenas |
| `/bw npc solo [skin]` | Cria o NPC principal do BedWars |
| `/bw npc 1v1 [skin]` | Alias do NPC principal para o modo atual |
| `/bw npc skin <id> <skin>` | Troca a skin de um NPC BedWars |
| `/bw npc remove <id>` | Remove um NPC BedWars |

### Jogador

| Comando | Descricao |
| --- | --- |
| `/lobby` | Sai da partida/fila e volta para o lobby principal |

## Permissoes

| Permissao | Descricao | Default |
| --- | --- | --- |
| `newbedwars.admin` | Administracao completa do plugin | `op` |
| `newbedwars.teamselect` | Permite usar o seletor de preferencia de time | `op` |

## Guia Completo de Setup

O setup foi desenhado para ser intuitivo e nao depender de editar o arquivo da arena manualmente.

### O que acontece ao usar `/bw setup <arena>`

- seu inventario e armadura atuais sao salvos
- voce e teleportado para o mundo da arena
- voce recebe os itens de setup
- os hologramas da arena aparecem para mostrar o que ja esta configurado
- o menu principal da arena pode ser aberto a qualquer momento

Quando o setup termina:

- o inventario original do jogador volta
- o modo setup e encerrado
- o jogador volta ao lobby principal

### Itens iniciais do setup

| Item | Funcao |
| --- | --- |
| `Nether Star` | Salvar o spawn de espera da arena |
| `Slime Ball` | Marcar `pos1` usando o pe do jogador |
| `Magma Cream` | Marcar `pos2` usando o pe do jogador |
| `Compass` | Abrir o menu principal do setup |

### Filosofia do setup

O plugin evita depender de clique em bloco para tudo. Regioes podem ser marcadas pelo pe do jogador, o que ajuda quando:

- nao existe bloco exatamente onde voce quer marcar
- voce quer delimitar uma ilha no ar
- a protecao inicial precisa ser marcada no espaco

### Como marcar uma regiao

1. Abra o menu correspondente.
2. Escolha a opcao da regiao.
3. Va ate o primeiro ponto.
4. Use o item `&a/pos1`.
5. Va ate o segundo ponto.
6. Use o item `&c/pos2`.

Isso vale para:

- sala de espera
- ilha do time
- protecao inicial

### Menu principal da arena

No menu principal voce configura:

- spawn de espera
- area da sala de espera
- time vermelho
- time azul
- geradores globais de diamante
- geradores globais de esmeralda
- finalizacao da arena

Regra de interacao:

- clique esquerdo: configurar
- clique direito: limpar o que ja estava salvo

### Menu de configuracao do time

Cada time possui menu proprio com setup para:

- spawn do time
- cama
- bau do time
- ender chest
- gerador de ferro
- gerador de ouro
- loja de itens
- loja de melhorias
- regiao da ilha
- protecao inicial
- preview do progresso
- confirmar time
- voltar

Regra de interacao:

- clique esquerdo: configurar
- clique direito: apagar e deixar pendente de novo

### Hologramas do setup

Enquanto a arena esta sendo configurada, o plugin mostra hologramas indicando onde cada coisa foi salva.

Exemplos:

- `Waiting Spawn`
- `Waiting Region Pos1`
- `Waiting Region Pos2`
- `Spawn Vermelho`
- `Cama Azul`
- `Bau Vermelho`
- `Ender Chest Azul`
- `Loja`
- `Melhorias`
- `Diamante`
- `Esmeralda`

Quando a partida comeca, esses hologramas de setup somem automaticamente.

### Validacao da arena

Uma arena so e marcada como pronta quando o plugin consegue validar o minimo necessario.

Hoje, para o fluxo `1v1`, isso inclui:

- mundo valido
- spawn de espera
- area de espera
- time vermelho completo e confirmado
- time azul completo e confirmado
- pelo menos um gerador global de diamante
- pelo menos um gerador global de esmeralda

## Fluxo da Partida

### Entrada na arena

Ao entrar na fila/arena:

- o jogador vai para a sala de espera
- recebe o item de seletor de time
- recebe a cama para sair da fila

### Selecao de time no pre-game

O seletor no waiting lobby funciona como preferencia de time.

Fluxo:

- o jogador escolhe uma cor
- essa escolha e salva como preferencia
- no inicio da partida o plugin tenta respeitar a preferencia
- quem nao escolheu vai para time aleatorio
- se houver conflito, o plugin resolve automaticamente no start

Sem permissao:

- o item aparece
- o clique nao abre a selecao
- o plugin envia uma mensagem configuravel no `config.yml`

### Inicio automatico

Quando o minimo de jogadores e atingido:

- countdown automatico com base no `config.yml`
- preparacao do clone runtime do mapa
- distribuicao final dos times
- teleporte para o spawn correto do time
- limpeza da waiting room

### Respawn sem tela de morte

O plugin usa fluxo de respawn no estilo BedWars:

- o jogador morre
- nao fica preso em tela de morte tradicional
- entra temporariamente em espectador
- apos o tempo configurado, volta ao spawn do time se a cama ainda existir

Se a cama estiver destruida:

- a proxima morte elimina o jogador
- ele vira espectador
- quando todo o time acaba, o time e eliminado

### Sair da partida

Se o jogador:

- usar `/lobby`
- sair do servidor

o plugin trata isso como abandono da partida, atualiza o estado da arena e retorna o jogador para o mundo do lobby quando aplicavel.

## NPCs

### NPC principal

Criado por:

```text
/bw npc solo [skin]
```

Ou:

```text
/bw npc 1v1 [skin]
```

Recursos:

- skin customizavel
- holograma configuravel
- menu de fila
- menu de escolha de arena
- suporte a Citizens

### NPC de loja

Configurado automaticamente no setup de cada time.

Visual padrao:

- holograma `&b&lLOJA`
- segunda linha `&eClique para abrir`

### NPC de melhorias

Configurado automaticamente no setup de cada time.

Visual padrao:

- holograma `&b&lMELHORIAS`
- segunda linha `&eClique para abrir`

Observacao:

- ao iniciar a partida ficam apenas os hologramas dos NPCs
- hologramas de setup e de outros auxiliares sao removidos

## Lojas, Itens e Upgrades

### Loja de itens

A base atual inclui itens comuns de BedWars, como:

- blocos
- espadas
- armaduras
- ferramentas
- arco e flechas
- perola
- TNT
- fireball

### Espadas

Regras atuais:

- o jogador sempre nasce com espada de madeira
- a espada pode ser movida dentro do proprio inventario
- nao pode ser dropada
- nao pode ser colocada em baus ou containers
- upgrades melhoram a espada atual independentemente do slot
- ao morrer, o jogador volta para a espada de madeira

### Armaduras

Regras atuais:

- o jogador nasce com couro na cor do time
- upgrades trocam principalmente calca e bota
- a armadura nao pode ser removida manualmente durante a partida

### Bau do time

Comportamento durante a partida:

- clique esquerdo: guarda automaticamente o item da mao
- clique direito: abre o bau do time

### Ender chest

Comportamento durante a partida:

- clique esquerdo: guarda automaticamente o item da mao no ender chest do jogador
- clique direito: abre o ender chest do jogador
- qualquer ender chest configurado da arena pode ser usado

### Loja de melhorias

A base atual ja possui upgrades como:

- espadas afiadas
- protecao
- minerador maniaco
- piscina de cura

## Geradores

Tipos presentes no plugin:

- `IRON`
- `GOLD`
- `DIAMOND`
- `EMERALD`

Distribuicao:

- `IRON` e `GOLD` sao normalmente configurados por time
- `DIAMOND` e `EMERALD` sao globais na arena

Tudo e salvo em YML e relido quando o servidor reinicia.

## Fireball e TNT

### Fireball

O plugin possui fireball customizada com:

- ativacao por clique direito
- suporte a uso no ar
- dano removido
- knockback customizado
- velocidade configuravel

### TNT

O plugin possui TNT com:

- colocacao como `TNTPrimed`
- contador visual configuravel
- cores do contador por porcentagem restante
- knockback customizado
- comportamento de TNT chain
- empurrao ajustado para jogador e para outras TNTs

## Scoreboard e Tablist

### Scoreboard

A scoreboard e configuravel por estado:

- lobby
- waiting
- starting
- ingame
- ending

Ela pode mostrar, por exemplo:

- nome da arena
- modo
- jogadores
- proximo evento
- status das camas
- linhas dos times
- vencedor

### Tablist

A tablist atual:

- e totalmente configuravel
- aparece apenas quando a partida comeca
- fica desligada no lobby e no waiting
- suporta header e footer com placeholders
- suporta prefixos/ordem por time

## Arquivos e Persistencia

Arquivos principais do projeto:

```text
src/main/resources/config.yml
src/main/resources/messages.yml
src/main/resources/plugin.yml
src/main/resources/arenas/example.yml
```

Arquivos gerados no servidor:

```text
plugins/NewBedWars/config.yml
plugins/NewBedWars/messages.yml
plugins/NewBedWars/arenas/<arena>.yml
```

Cada arena salva, no minimo:

- nome
- mundo
- status de pronta
- estado da arena
- waiting spawn
- waiting region
- dados dos times
- spawns
- cama
- baus
- lojas
- geradores
- regioes

### Sobre o `messages.yml`

O plugin usa fallback para mensagens padrao do jar. Isso ajuda quando:

- o servidor tem um `messages.yml` antigo
- voce atualiza o plugin e surgem novas chaves

Ou seja, faltou uma chave no arquivo do servidor e a chance de quebrar tudo diminui bastante.

### Sobre o `config.yml`

O Bukkit nao sobrescreve tudo automaticamente ao atualizar plugin. Entao, se uma secao nova nao aparecer no servidor:

1. copie a secao nova manualmente do projeto
2. ou apague o arquivo antigo para o plugin gerar um novo

## Licenca

Este projeto usa uma licenca propria em portugues, disponivel em [LICENSE](C:/Users/picha/Documents/backup/NewBedWars/LICENSE).

Em resumo:

- voce pode usar o plugin normalmente
- voce pode distribuir copias nao modificadas
- voce pode fazer integracoes externas, addons, wrappers e automacoes
- voce nao pode modificar o codigo-fonte do projeto sem permissao
- voce nao pode redistribuir jar ou source alterado sem permissao

Se quiser termos diferentes, permissao comercial ou autorizacao para modificar e redistribuir, o contato deve ser feito diretamente com o autor.

## Estrutura do Projeto

Pacotes principais:

```text
src/main/java/n/plugins/newbedwars/
  arena/
  command/
  listener/
  manager/
  menu/
  model/
  npc/
  setup/
  util/
```

### Classe principal

- [`NewBedWars.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/NewBedWars.java)

Responsabilidades:

- bootstrap do plugin
- registro de managers
- registro de listeners
- registro de comandos
- carga de arquivos

### Managers

- [`ArenaManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/ArenaManager.java): CRUD e persistencia das arenas
- [`GameManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/GameManager.java): fluxo da partida, entrada, start, morte, respawn, eliminacao e fim
- [`GeneratorManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/GeneratorManager.java): geradores e drops
- [`HologramManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/HologramManager.java): hologramas runtime
- [`LobbyManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/LobbyManager.java): lobby principal e regras do mundo do lobby
- [`MenuManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/MenuManager.java): controle das GUIs
- [`MessageManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/MessageManager.java): mensagens configuraveis
- [`NpcManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/NpcManager.java): NPC principal, NPCs de loja e hologramas associados
- [`ScoreboardManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/ScoreboardManager.java): scoreboard e tablist
- [`SetupManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/SetupManager.java): todo o fluxo de setup
- [`ShopManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/ShopManager.java): compras, upgrades, espada, armadura e efeitos
- [`TeamManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/TeamManager.java): times, atribuicao e estado de cama/eliminacao
- [`WorldCloneManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/WorldCloneManager.java): clones runtime e limpeza

### Listeners

- [`SetupInteractListener.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/listener/SetupInteractListener.java)
- [`NpcListener.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/listener/NpcListener.java)
- [`InventoryListener.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/listener/InventoryListener.java)
- [`GamePlayerListener.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/listener/GamePlayerListener.java)
- [`GameItemListener.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/listener/GameItemListener.java)
- [`GameBlockListener.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/listener/GameBlockListener.java)

### Menus

- [`SetupMainMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/SetupMainMenu.java)
- [`TeamSetupMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/TeamSetupMenu.java)
- [`SetupConfirmMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/SetupConfirmMenu.java)
- [`SetupNpcMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/SetupNpcMenu.java)
- [`SoloQueueMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/SoloQueueMenu.java)
- [`ArenaSelectorMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/ArenaSelectorMenu.java)
- [`ItemShopMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/ItemShopMenu.java)
- [`UpgradeShopMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/UpgradeShopMenu.java)
- [`TeamSelectorMenu.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/menu/TeamSelectorMenu.java)

### Modelos e objetos principais

- [`Arena.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/arena/Arena.java)
- [`ArenaTeam.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/arena/ArenaTeam.java)
- [`ArenaState.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/arena/ArenaState.java)
- [`TeamColor.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/arena/TeamColor.java)
- [`BedData.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/arena/BedData.java)
- [`GeneratorPoint.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/arena/GeneratorPoint.java)
- [`GeneratorType.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/arena/GeneratorType.java)
- [`SetupSession.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/setup/SetupSession.java)
- [`SetupPointAction.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/setup/SetupPointAction.java)
- [`SetupRegionAction.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/setup/SetupRegionAction.java)
- [`PlayerSnapshot.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/model/PlayerSnapshot.java)

## Como Compilar

### Requisitos

- `Java 8`
- `Maven`
- jars locais de `Spigot API 1.8.8` e `Citizens` conforme o [`pom.xml`](C:/Users/picha/Documents/backup/NewBedWars/pom.xml)

### Build padrao

```text
mvn clean package
```

### Build usado neste workspace

Caso o `mvn` nao esteja no `PATH`, existe Maven local no projeto:

```text
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -DskipTests package
```

Jar final:

```text
target/NewBedWars-1.0-beta.jar
```

## Troubleshooting

### O plugin nao inicia

Verifique:

- `Citizens` instalado
- versao do servidor compativel com 1.8.8
- dependencias do `pom.xml` corretas para sua maquina se voce estiver compilando

### O NPC nao aparece ou nao abre menu

Verifique:

- se o Citizens esta rodando corretamente
- se o NPC foi recriado depois de atualizar o plugin
- se a localizacao foi configurada no setup

### O jogador nao vai para o mapa certo

Verifique:

- nome do mundo salvo na arena
- pasta do mapa na raiz do servidor
- se o mundo existe com esse nome

### O setup nao mostra item/acao nova

Provavelmente o `config.yml` ou o `messages.yml` do servidor esta antigo.

Solucao:

- copie manualmente a secao nova do projeto
- ou apague os arquivos antigos para o plugin regenerar

### A arena nao fica pronta

Confira se faltou:

- waiting spawn
- waiting region
- confirmacao do time vermelho
- confirmacao do time azul
- gerador de diamante
- gerador de esmeralda

### Itens da GUI estao estranhos

Sempre prefira:

- reiniciar o servidor apos atualizar o jar
- testar com inventario limpo
- recriar NPCs antigos se eles vieram de versoes bem anteriores

## Limitacoes Atuais

O plugin esta jogavel e funcional, mas hoje seria exagero chamar de BedWars completo estilo rede grande.

Pontos que ainda estao fora do escopo fechado:

- SOLO 8 times completamente fechado do inicio ao fim
- doubles, trios e squads
- sistema completo de ferramentas permanentes por tier
- todos os upgrades e refinamentos de redes grandes
- NPC proprio sem Citizens
- polimento total de loja para espelhar 100% um servidor especifico

## Roadmap Sugerido

Se quiser continuar evoluindo essa base, a ordem mais saudavel seria:

1. fechar completamente o `1v1` com mais testes de gameplay
2. extrair sistema de modos para suportar `SOLO`, `DOUBLES` e outros
3. ampliar upgrades e ferramentas permanentes
4. adicionar reset/rollback ainda mais robusto
5. criar NPC proprio sem depender de Citizens
6. melhorar observabilidade com logs e debug de arenas

## Resumo Rapido

Se voce quer colocar esse projeto para rodar rapido:

1. instale `Citizens`
2. coloque o mapa da arena na raiz do servidor
3. use `/bw create <arena> <world>`
4. use `/bw setup <arena>`
5. configure waiting, times, geradores, lojas, baus e regioes
6. finalize a arena
7. use `/bw setlobby`
8. crie o NPC com `/bw npc solo [skin]`
9. teste a fila e a partida

Se voce quer desenvolver em cima dele:

1. leia primeiro [`NewBedWars.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/NewBedWars.java)
2. depois passe por [`ArenaManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/ArenaManager.java), [`SetupManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/SetupManager.java) e [`GameManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/GameManager.java)
3. por fim, olhe [`ShopManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/ShopManager.java), [`NpcManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/NpcManager.java) e [`ScoreboardManager.java`](C:/Users/picha/Documents/backup/NewBedWars/src/main/java/n/plugins/newbedwars/manager/ScoreboardManager.java)
