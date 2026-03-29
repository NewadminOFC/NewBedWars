# NewBedWars

Plugin de BedWars para `Minecraft 1.8.8` com setup totalmente in-game, arquitetura modular, suporte a varios modos e foco em colocar uma arena para funcionar sem depender de edicao manual de arquivo.

O projeto foi pensado para:

- reaproveitar um mesmo mapa-base varias vezes por meio de clones runtime
- proteger o mundo original da arena
- deixar o setup inteiro dentro do jogo
- permitir filas por modo com NPCs separados
- manter configs, mensagens, scoreboard e loja altamente configuraveis

## Sumario

- [Visao Geral](#visao-geral)
- [Modos Suportados](#modos-suportados)
- [Principais Recursos](#principais-recursos)
- [Compatibilidade](#compatibilidade)
- [Dependencias](#dependencias)
- [Instalacao](#instalacao)
- [Inicio Rapido](#inicio-rapido)
- [Comandos](#comandos)
- [Permissoes](#permissoes)
- [Guia Completo de Setup](#guia-completo-de-setup)
- [Fluxo da Partida](#fluxo-da-partida)
- [NPCs](#npcs)
- [Lojas, Itens e Upgrades](#lojas-itens-e-upgrades)
- [Geradores e Eventos](#geradores-e-eventos)
- [Scoreboard e Tablist](#scoreboard-e-tablist)
- [Arquivos e Persistencia](#arquivos-e-persistencia)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Como Compilar](#como-compilar)
- [Troubleshooting](#troubleshooting)
- [Limitacoes Atuais](#limitacoes-atuais)
- [Licenca](#licenca)
- [Resumo Rapido](#resumo-rapido)

## Visao Geral

`NewBedWars` e uma base completa de BedWars 1.8.8 feita em `Java + Spigot API 1.8.8`, com:

- setup de arena 100% in-game
- multiplas arenas carregadas ao mesmo tempo
- suporte a varios modos de jogo
- filas por NPC e menu de selecao de arena
- lojas e upgrades configuraveis
- respawn estilo BedWars com espectador temporario
- anti-void por arena
- clones runtime para reaproveitar o mesmo template
- scoreboard e tablist dinamicas
- persistencia em arquivos `YML`

O objetivo da base e simples: voce instala, coloca um mapa, cria a arena, configura tudo dentro do jogo e ja consegue testar a partida.

## Modos Suportados

Hoje o plugin suporta estes modos:

| Modo | Times ativos | Jogadores por time | Maximo de jogadores |
| --- | --- | --- | --- |
| `1v1` | `RED`, `BLUE` | `1` | `2` |
| `2v2` | `RED`, `BLUE` | `2` | `4` |
| `3v3` | `RED`, `BLUE` | `3` | `6` |
| `4v4` | `RED`, `BLUE` | `4` | `8` |
| `solo` | `RED`, `BLUE`, `GREEN`, `YELLOW`, `CYAN`, `PINK`, `GRAY`, `WHITE` | `1` | `8` |
| `dupla` | `RED`, `BLUE`, `GREEN`, `YELLOW`, `CYAN`, `PINK`, `GRAY`, `WHITE` | `2` | `16` |
| `trio` | `RED`, `BLUE`, `GREEN`, `YELLOW` | `3` | `12` |
| `quarteto` | `RED`, `BLUE`, `GREEN`, `YELLOW` | `4` | `16` |

Observacoes importantes:

- o setup mostra apenas os times ativos do modo atual
- a validacao da arena considera somente os times necessarios para aquele modo
- a scoreboard encolhe automaticamente quando o modo usa menos times
- cada modo pode ter seu proprio NPC de fila

## Principais Recursos

- setup de arena 100% in-game
- `/bw create <arena> [world] [mode]` para criar arenas direto no modo desejado
- `/bw mode <arena> <modo>` para trocar o modo depois
- spawn de espera, area de espera, times, cama, baus, lojas, geradores e regioes configurados por menu
- itens de setup entregues somente quando realmente sao necessarios
- spawns de espera e de time definidos clicando no bloco
- ferramentas `/pos1` e `/pos2` para regioes com dicas no proprio setup
- anti-void salvo por arena usando o `Y` configurado no setup
- clima travado sem chuva e sempre de dia nas arenas
- clones runtime infinitos a partir do mesmo mapa-base
- respawn sem tela de morte, com espectador temporario
- void tratado como morte de partida apenas durante `INGAME`
- NPCs de fila por modo com `Citizens`
- NPCs de loja e melhorias criados a partir do setup
- hologramas de setup e hologramas de baus configuraveis
- loja de itens e loja de melhorias puxadas da `config.yml`
- scoreboard atualizando a cada `0.5s`
- tablist somente durante a partida
- mensagens configuraveis em `messages.yml`

## Compatibilidade

- `Minecraft 1.8.8`
- `Java 8`
- `Spigot 1.8.8`
- `PaperSpigot 1.8.8`

## Dependencias

Dependencia obrigatoria:

- `Citizens`

O plugin usa `Citizens` para:

- NPCs de fila por modo
- NPC da loja de itens
- NPC da loja de melhorias

Sem `Citizens`, o plugin nao inicia, porque a dependencia esta declarada em [`plugin.yml`](src/main/resources/plugin.yml).

## Instalacao

1. Coloque o `Citizens.jar` em `plugins/`.
2. Coloque o jar do `NewBedWars` em `plugins/`.
3. Coloque o mapa da arena na raiz do servidor.
4. Inicie o servidor.
5. Defina o lobby principal com `/bw setlobby`.
6. Crie e configure as arenas com `/bw create` e `/bw setup`.

Exemplo de estrutura no servidor:

```text
server/
  world/
  world_nether/
  world_the_end/
  [MAPABEDWARS]/
  plugins/
    Citizens/
    NewBedWars/
      config.yml
      messages.yml
      arenas/
```

## Inicio Rapido

### 1. Coloque o mapa da arena na raiz do servidor

Exemplo:

```text
server/[MAPABEDWARS]/
```

### 2. Defina o lobby principal

```text
/bw setlobby
```

Esse comando salva o lobby principal e tambem aplica regras no mundo do lobby, como controle de clima e horario.

### 3. Crie a arena

Usando o mundo atual:

```text
/bw create [NOMEPARAARENA] solo
```

Informando o mundo explicitamente:

```text
/bw create [NOMEPARAARENA] [MAPABEDWARS] solo
```

Se preferir criar primeiro e trocar depois:

```text
/bw create [NOMEPARAARENA] [MAPABEDWARS]
/bw mode [NOMEPARAARENA] quarteto
```

### 4. Entre no setup

```text
/bw setup [NOMEPARAARENA]
```

### 5. Configure os pontos gerais da arena

No menu principal, configure:

- spawn de espera
- area de espera
- anti-void
- geradores globais de diamante
- geradores globais de esmeralda

### 6. Configure os times ativos do modo

Para cada time necessario no modo atual, configure:

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

Depois confirme cada time pelo proprio menu.

### 7. Finalize a arena

Quando tudo estiver pronto:

- abra o menu principal
- clique em `Finalizar`
- confirme a validacao

### 8. Crie os NPCs de fila

Exemplos:

```text
/bw npc 1v1
/bw npc solo
/bw npc dupla Notch
/bw npc quarteto
```

## Comandos

### Administracao

| Comando | Descricao |
| --- | --- |
| `/bw create <arena> [world] [mode]` | Cria uma arena usando o mundo atual ou o mundo informado |
| `/bw delete <arena>` | Remove a arena e o arquivo YML dela |
| `/bw list` | Lista as arenas configuradas |
| `/bw mode <arena> <modo>` | Troca o modo da arena |
| `/bw setup <arena>` | Entra no modo setup da arena |
| `/bw setlobby` | Salva o lobby principal |
| `/bw join <arena>` | Entra na arena informada |
| `/bw leave` | Sai da partida atual |
| `/bw reload` | Recarrega `config.yml`, `messages.yml` e arenas |
| `/bw npc <modo> [skin]` | Cria um NPC de fila para o modo informado |
| `/bw npc skin <id> <skin>` | Troca a skin de um NPC BedWars |
| `/bw npc remove <id>` | Remove um NPC BedWars |

### Jogador

| Comando | Descricao |
| --- | --- |
| `/lobby` | Sai da fila ou da partida e volta para o lobby principal |

### Modos aceitos nos comandos

Os comandos aceitam:

- `1v1`
- `2v2`
- `3v3`
- `4v4`
- `solo`
- `dupla`
- `trio`
- `quarteto`

## Permissoes

| Permissao | Descricao | Default |
| --- | --- | --- |
| `newbedwars.admin` | Administracao completa do plugin | `op` |
| `newbedwars.teamselect` | Permite escolher o time no lobby de espera | `op` |

## Guia Completo de Setup

O setup foi desenhado para ser guiado. A ideia e voce nao precisar editar arquivo manualmente para deixar uma arena funcional.

### O que acontece ao usar `/bw setup <arena>`

- seu inventario e armadura atuais sao salvos
- voce e teleportado para o mundo da arena
- os hologramas da arena aparecem
- voce recebe a bussola para abrir o menu principal
- os itens auxiliares aparecem somente quando uma acao precisa deles

Quando o setup termina:

- seu inventario original volta
- o modo setup e encerrado
- voce e teleportado para o lobby principal, se ele estiver configurado

### Como o setup funciona hoje

O plugin usa dois tipos de configuracao:

- pontos, como `spawn`, `cama`, `bau`, `ender chest`, `gerador` e `loja`
- regioes, como `area de espera`, `ilha do time` e `protecao inicial`

### Configurando pontos

Pontos funcionam assim:

- spawns de espera e de time sao salvos clicando em um bloco
- o plugin salva a localizacao em cima do bloco clicado
- cama, baus, lojas e geradores tambem sao salvos por interacao no local
- o anti-void da arena salva o `Y` atual do jogador

### Configurando regioes

Quando uma regiao precisa ser marcada:

1. abra a opcao no menu
2. o plugin entrega os itens de `pos1` e `pos2`
3. use `pos1` no primeiro ponto
4. use `pos2` no segundo ponto

O plugin tambem envia dicas no chat para lembrar para que servem `pos1` e `pos2`.

### Menu principal da arena

No menu principal voce configura:

- spawn de espera
- area de espera
- anti-void
- modo atual da arena
- times ativos do modo
- geradores globais de diamante
- geradores globais de esmeralda
- finalizacao da arena

Regras de clique:

- clique esquerdo: configurar
- clique direito: limpar

### Menu de configuracao do time

Cada time ativo do modo possui um menu proprio com:

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
- confirmacao do time

### Validacao da arena

Uma arena so fica pronta quando o plugin valida o minimo necessario para o modo atual.

Na pratica, isso envolve:

- mundo valido
- spawn de espera
- area de espera
- todos os times ativos do modo devidamente configurados e confirmados
- pelo menos um gerador global de diamante
- pelo menos um gerador global de esmeralda

O numero de times exigidos muda conforme o modo escolhido.

## Fluxo da Partida

### Entrada na fila

Ao entrar na arena:

- o jogador vai para a sala de espera
- recebe o seletor de time
- recebe a cama para sair da fila

### Inicio automatico

Quando o minimo de jogadores e atingido:

- o countdown comeca
- o countdown e mostrado principalmente no chat
- o plugin prepara um clone runtime do mapa
- os jogadores sao distribuidos nos times
- cada jogador vai para o spawn do seu time
- a waiting room some visualmente do fluxo da partida

### Regras de mundo da arena

As arenas-template e os clones runtime ficam com:

- sempre de dia
- sem chuva

Isso vale desde a criacao da arena, nao so quando a partida comeca.

### Morte, void e respawn

Durante `INGAME`:

- cair no void conta como morte de partida
- o jogador entra em espectador temporario
- se a cama do time ainda existir, ele volta depois do tempo de respawn configurado
- se a cama tiver sido destruida, a morte passa a ser final

O tempo de respawn padrao hoje e `5 segundos`.

### Anti-void

O anti-void:

- e configurado por arena no setup
- usa o `Y` salvo para aquela arena
- so atua durante a partida
- nao afeta lobby, setup ou outros mundos fora do BedWars

### Fim da partida

Quando a partida termina:

- o plugin entra no estado `ENDING`
- os jogadores continuam no mapa por alguns segundos
- todo o inventario e limpo
- sobra apenas a cama para sair
- `/lobby` continua funcionando

## NPCs

### NPCs de fila

Voce pode criar um NPC separado para cada modo:

```text
/bw npc 1v1
/bw npc 2v2
/bw npc 3v3
/bw npc 4v4
/bw npc solo
/bw npc dupla
/bw npc trio
/bw npc quarteto
```

Recursos:

- skin customizavel
- holograma configuravel
- contador de jogadores no holograma
- menu de fila do modo correto
- menu de selecao de arena filtrado por modo

### NPC de loja

Configurado automaticamente no setup do time.

Visual padrao:

- holograma `&b&lLOJA`
- segunda linha `&eClique para abrir`

### NPC de melhorias

Configurado automaticamente no setup do time.

Visual padrao:

- holograma `&b&lMELHORIAS`
- segunda linha `&eClique para abrir`

Observacao:

- ao iniciar a partida, o plugin remove os hologramas auxiliares de setup
- os hologramas dos NPCs de loja continuam

## Lojas, Itens e Upgrades

As lojas foram montadas para serem altamente configuraveis por `config.yml`.

### Loja de itens

A loja atual suporta categorias como:

- blocos
- combate
- ferramentas
- distancia
- pocoes
- utilidades

Entre os itens ja presentes na base estao:

- la, madeira, vidro anti-explosao e outros blocos
- espadas e armaduras
- picaretas com sistema de evolucao
- machados com sistema de evolucao
- arcos e flechas
- TNT
- fireball
- perola do fim
- maca dourada
- balde de agua
- golem de ferro
- ovo das pontes
- percevejo
- pocoes de agilidade, super pulo e invisibilidade

### Espadas

Regras atuais:

- o jogador nasce com espada de madeira
- ela pode ser movida no inventario
- nao pode ser dropada
- nao pode ser colocada em baus
- upgrades de espada afetam a espada do jogador

### Picareta e machado

Regras atuais:

- funcionam por progressao de tiers na loja
- nao quebram durante a partida
- o efeito visual de inquebravel foi removido

### Armaduras

Regras atuais:

- o jogador nasce com couro tingido na cor do time
- compras de armadura substituem o tier do jogador
- a armadura nao pode ser removida manualmente durante a partida

### Vidro anti-explosao

O vidro anti-explosao:

- pode ser comprado na loja
- resiste a explosoes de TNT e fireball
- pode ser colocado sobre a cama usando `shift + clique direito`

### Bau do time

Durante a partida:

- clique esquerdo: guarda automaticamente o item da mao
- clique direito: abre o bau do time

### Ender chest

Durante a partida:

- clique esquerdo: guarda automaticamente o item da mao no ender chest do jogador
- clique direito: abre o ender chest do jogador

### Loja de melhorias

A base atual ja inclui upgrades como:

- espadas afiadas
- protecao
- minerador maniaco
- piscina de cura
- melhorias de ferramenta

## Geradores e Eventos

Tipos de gerador presentes:

- `IRON`
- `GOLD`
- `DIAMOND`
- `EMERALD`

Distribuicao:

- `IRON` e `GOLD` normalmente sao configurados por time
- `DIAMOND` e `EMERALD` sao globais da arena

Eventos atuais configuraveis em [`config.yml`](src/main/resources/config.yml):

- upgrade de diamante
- destruicao global de camas

## Scoreboard e Tablist

### Scoreboard

A scoreboard atual:

- usa titulo fixo `&b&lBEDWARS`
- atualiza a cada `0.5s`
- diminui automaticamente quando o modo usa menos times
- mostra data/hora, proximo evento e estado dos times durante a partida
- usa `newplugins.net` na linha final configurada

Estados suportados:

- lobby
- waiting
- starting
- ingame
- ending

### Tablist

A tablist:

- e configuravel por arquivo
- aparece apenas durante a partida
- mostra header, footer, arena, status e time do jogador
- ordena jogadores por estado e por time

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
- modo
- status de pronta
- estado da arena
- waiting spawn
- waiting region
- anti-void-y
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
- o plugin ganhou chaves novas

### Sobre o `config.yml`

O Bukkit nao sobrescreve o arquivo inteiro ao atualizar plugin. Se alguma secao nova nao aparecer:

1. copie manualmente a secao nova do projeto
2. ou apague o arquivo antigo para o plugin gerar outro

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

Arquivos importantes para leitura:

- [`NewBedWars.java`](src/main/java/n/plugins/newbedwars/NewBedWars.java): bootstrap do plugin
- [`ArenaManager.java`](src/main/java/n/plugins/newbedwars/manager/ArenaManager.java): CRUD e persistencia das arenas
- [`GameManager.java`](src/main/java/n/plugins/newbedwars/manager/GameManager.java): fluxo da partida, entrada, inicio, morte, respawn, eliminacao e fim
- [`SetupManager.java`](src/main/java/n/plugins/newbedwars/manager/SetupManager.java): fluxo completo do setup
- [`TeamManager.java`](src/main/java/n/plugins/newbedwars/manager/TeamManager.java): times, atribuicao, capacidade e status
- [`NpcManager.java`](src/main/java/n/plugins/newbedwars/manager/NpcManager.java): NPCs de fila e NPCs de loja
- [`ShopManager.java`](src/main/java/n/plugins/newbedwars/manager/ShopManager.java): compras, upgrades, picareta, machado, espada e armadura
- [`ScoreboardManager.java`](src/main/java/n/plugins/newbedwars/manager/ScoreboardManager.java): scoreboard e tablist
- [`BedWarsMode.java`](src/main/java/n/plugins/newbedwars/arena/BedWarsMode.java): definicao dos modos suportados

## Como Compilar

### Requisitos

- `Java 8`
- `Maven`
- jars locais de `Spigot API 1.8.8` e `Citizens`, conforme configurado no [`pom.xml`](pom.xml)

### Build padrao

```text
mvn clean package
```

### Build usado neste workspace

Se o `mvn` nao estiver no `PATH`, existe Maven local no projeto:

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
- versao do servidor compativel com `1.8.8`
- dependencias do [`pom.xml`](pom.xml) corretas, caso voce esteja compilando

### O NPC nao aparece ou nao abre menu

Verifique:

- se o Citizens esta funcionando corretamente
- se o NPC foi recriado depois de alteracoes grandes
- se a skin escolhida e valida

### A arena nao aparece no NPC

Confira:

- se a arena foi validada e marcada como pronta
- se o modo da arena bate com o modo do NPC
- se o numero minimo de configuracoes foi concluido

### O setup nao mostra a opcao nova

Provavelmente o `config.yml` ou o `messages.yml` do servidor esta antigo.

Solucao:

- copie as secoes novas do projeto
- ou apague os arquivos antigos para regenerar

### O jogador nao vai para o mapa certo

Verifique:

- nome do mundo salvo na arena
- pasta do mapa na raiz do servidor
- se o mundo existe com esse nome

### A arena nao fica pronta

Confira se faltou:

- waiting spawn
- waiting region
- confirmacao dos times ativos do modo
- gerador de diamante
- gerador de esmeralda

### A scoreboard nao mudou

Se o servidor ja tinha `config.yml` antigo:

- copie a secao `scoreboard:` nova do projeto
- recarregue o plugin ou reinicie o servidor

## Limitacoes Atuais

O plugin esta funcional e bem mais completo do que uma base inicial, mas ainda deve ser tratado como `beta`.

Pontos importantes:

- os varios modos ja existem, mas ainda merecem mais teste real de gameplay
- a base nao tenta copiar 100% um servidor especifico
- ainda ha espaco para polimento de upgrades, eventos e rollback
- a dependencia de `Citizens` continua obrigatoria

## Licenca

Este projeto usa uma licenca propria em portugues, disponivel em [LICENSE](LICENSE).

Em resumo:

- voce pode usar o plugin normalmente
- voce pode distribuir copias nao modificadas
- voce pode fazer integracoes externas, addons, wrappers e automacoes
- voce nao pode modificar o codigo-fonte sem permissao
- voce nao pode redistribuir jar ou source alterado sem permissao

## Resumo Rapido

Se voce quer colocar o projeto para rodar rapido:

1. instale `Citizens`
2. coloque o mapa da arena na raiz do servidor
3. use `/bw setlobby`
4. crie a arena com `/bw create <arena> [world] [mode]`
5. entre no setup com `/bw setup <arena>`
6. configure os pontos gerais e os times ativos do modo
7. finalize a arena
8. crie o NPC do modo com `/bw npc <modo> [skin]`
9. teste a fila e a partida

Se voce quer desenvolver em cima da base:

1. comece por [`NewBedWars.java`](src/main/java/n/plugins/newbedwars/NewBedWars.java)
2. depois leia [`ArenaManager.java`](src/main/java/n/plugins/newbedwars/manager/ArenaManager.java), [`SetupManager.java`](src/main/java/n/plugins/newbedwars/manager/SetupManager.java) e [`GameManager.java`](src/main/java/n/plugins/newbedwars/manager/GameManager.java)
3. por fim, aprofunde em [`TeamManager.java`](src/main/java/n/plugins/newbedwars/manager/TeamManager.java), [`NpcManager.java`](src/main/java/n/plugins/newbedwars/manager/NpcManager.java), [`ShopManager.java`](src/main/java/n/plugins/newbedwars/manager/ShopManager.java) e [`ScoreboardManager.java`](src/main/java/n/plugins/newbedwars/manager/ScoreboardManager.java)
