# NewBedWars

Plugin de BedWars para **Minecraft 1.8.8** com setup 100% in-game, arquitetura modular e suporte a multiplos modos.

Open source sob licenca [MIT](LICENSE). Sinta-se a vontade para usar, modificar e criar addons.

---

## Sumario

- [Recursos](#recursos)
- [Modos Suportados](#modos-suportados)
- [Compatibilidade](#compatibilidade)
- [Instalacao](#instalacao)
- [Inicio Rapido](#inicio-rapido)
- [Comandos](#comandos)
- [Permissoes](#permissoes)
- [Guia de Setup](#guia-de-setup)
- [Fluxo da Partida](#fluxo-da-partida)
- [NPCs](#npcs)
- [Lojas e Upgrades](#lojas-e-upgrades)
- [Geradores e Eventos](#geradores-e-eventos)
- [Scoreboard e Tablist](#scoreboard-e-tablist)
- [Configuracao](#configuracao)
- [Para Developers](#para-developers)
  - [Estrutura do Projeto](#estrutura-do-projeto)
  - [Como Compilar](#como-compilar)
  - [Criando Addons](#criando-addons)
  - [API e Pontos de Integracao](#api-e-pontos-de-integracao)
- [Troubleshooting](#troubleshooting)
- [Licenca](#licenca)

---

## Recursos

- Setup de arena 100% in-game (sem editar arquivos manualmente)
- Multiplas arenas carregadas simultaneamente
- Clones runtime infinitos a partir do mesmo mapa-base
- Fila por modo com NPCs separados (Citizens)
- Loja de itens e loja de melhorias configuraveis
- Respawn estilo BedWars com espectador temporario
- Anti-void por arena
- Scoreboard e tablist dinamicas
- Chat isolado por arena (time e global)
- Mensagens totalmente configuraveis
- Persistencia em arquivos YML

## Modos Suportados

| Modo | Times | Jogadores/Time | Max |
|------|-------|----------------|-----|
| `1v1` | RED, BLUE | 1 | 2 |
| `2v2` | RED, BLUE | 2 | 4 |
| `3v3` | RED, BLUE | 3 | 6 |
| `4v4` | RED, BLUE | 4 | 8 |
| `solo` | 8 cores | 1 | 8 |
| `dupla` | 8 cores | 2 | 16 |
| `trio` | 4 cores | 3 | 12 |
| `quarteto` | 4 cores | 4 | 16 |

## Compatibilidade

- Minecraft **1.8.8**
- Java **8+**
- Spigot / PaperSpigot 1.8.8
- **Citizens** (dependencia obrigatoria)

## Instalacao

1. Coloque `Citizens.jar` em `plugins/`
2. Coloque `NewBedWars.jar` em `plugins/`
3. Coloque o mapa da arena na raiz do servidor
4. Inicie o servidor
5. Defina o lobby: `/bw setlobby`
6. Crie e configure arenas (veja abaixo)

Estrutura esperada:

```
server/
  [MAPA_ARENA]/
  plugins/
    Citizens/
    NewBedWars/
      config.yml
      messages.yml
      arenas/
```

## Inicio Rapido

```text
/bw setlobby
/bw create minhaarena [MUNDO] solo
/bw setup minhaarena
```

No setup, configure pelo menu:
- Spawn de espera e area de espera
- Anti-void
- Geradores globais (diamante/esmeralda)
- Cada time: spawn, cama, bau, ender chest, geradores, lojas, regiao

Finalize pelo menu e crie o NPC:

```text
/bw npc solo
```

## Comandos

### Administracao

| Comando | Descricao |
|---------|-----------|
| `/bw create <arena> [world] [mode]` | Cria arena |
| `/bw delete <arena>` | Remove arena |
| `/bw list` | Lista arenas |
| `/bw mode <arena> <modo>` | Troca modo |
| `/bw setup <arena>` | Entra no setup |
| `/bw setlobby` | Salva lobby principal |
| `/bw join <arena>` | Entra na arena |
| `/bw leave` | Sai da partida |
| `/bw reload` | Recarrega configs |
| `/bw npc <modo> [skin]` | Cria NPC de fila |
| `/bw npc skin <id> <skin>` | Troca skin do NPC |
| `/bw npc remove <id>` | Remove NPC |

### Jogador

| Comando | Descricao |
|---------|-----------|
| `/g <mensagem>` | Chat global da partida |
| `/lobby` | Volta ao lobby |

## Permissoes

| Permissao | Descricao | Default |
|-----------|-----------|---------|
| `newbedwars.admin` | Administracao completa | op |
| `newbedwars.teamselect` | Escolher time no lobby | op |

## Guia de Setup

### Entrando no setup

`/bw setup <arena>` salva seu inventario, teleporta para o mundo da arena e entrega a bussola do menu.

### Configurando pontos

Clique em blocos para definir: spawns, camas, baus, lojas, geradores. O anti-void salva o Y atual.

### Configurando regioes

Use os itens `pos1` e `pos2` entregues pelo menu para marcar: area de espera, ilha do time, protecao inicial.

### Validacao

A arena so fica pronta quando tiver:
- Mundo valido
- Spawn e area de espera
- Todos os times do modo configurados e confirmados
- Pelo menos 1 gerador de diamante e 1 de esmeralda

## Fluxo da Partida

1. Jogador entra na fila pelo NPC
2. Countdown inicia ao atingir minimo
3. Clone runtime do mapa e criado
4. Jogadores distribuidos nos times
5. Partida ativa: morte = respawn (se cama existe) ou eliminacao
6. Ultimo time vivo vence
7. Estado ENDING: jogadores retornam ao lobby

### Morte e Respawn

- Void durante INGAME = morte de partida
- Cama intacta: respawn em 5s (configuravel)
- Cama destruida: morte final
- Espectador temporario com voo

### Chat

- WAITING/STARTING: chat entre jogadores da mesma arena
- INGAME: chat normal = chat de time
- `/g`: chat global (so INGAME)
- Espectadores: chat separado

## NPCs

### Fila (por modo)

```text
/bw npc solo
/bw npc dupla Notch
```

- Skin customizavel
- Holograma com contador de jogadores
- Menu de selecao de arena filtrado por modo

### Loja e Melhorias

Criados automaticamente no setup do time. Usam NPCs jogadores do Citizens com skins configuraveis no `config.yml`.

## Lojas e Upgrades

### Loja de Itens

Categorias: blocos, combate, ferramentas, distancia, pocoes, utilidades.

Itens incluem: la, madeira, vidro anti-explosao, espadas, armaduras, picaretas/machados com tiers, arcos, TNT, fireball, perola, maca dourada, balde, golem, pocoes e mais.

### Regras de Equipamento

- Espada de madeira inicial (nao dropa, nao guarda em bau)
- Armadura de couro tingida (nao remove manualmente)
- Picareta/machado: progressao por tiers, inquebraveis
- Vidro anti-explosao: resiste a TNT/fireball, shift+clique na cama

### Loja de Melhorias

- Espadas afiadas
- Protecao
- Minerador maniaco
- Piscina de cura
- Melhorias de ferramenta

## Geradores e Eventos

| Tipo | Escopo |
|------|--------|
| IRON | Por time |
| GOLD | Por time |
| DIAMOND | Global |
| EMERALD | Global |

Eventos configuraveis: upgrade de diamante, destruicao global de camas.

## Scoreboard e Tablist

- Scoreboard: titulo `&b&lBEDWARS`, atualiza a cada 0.5s, mostra times/eventos/data
- Tablist: header/footer configuraveis, so durante partida, ordena por time

## Configuracao

| Arquivo | Funcao |
|---------|--------|
| `config.yml` | Lojas, upgrades, geradores, eventos, scoreboard, NPCs |
| `messages.yml` | Todas as mensagens do plugin |
| `arenas/<nome>.yml` | Dados de cada arena |

Use `/bw reload` para recarregar sem reiniciar.

---

## Para Developers

### Estrutura do Projeto

```
src/main/java/n/plugins/newbedwars/
  NewBedWars.java          # Bootstrap do plugin
  arena/                   # BedWarsMode, estados, clone de mundo
  command/                 # Handlers de comandos
  listener/                # Event listeners
  manager/                 # ArenaManager, GameManager, SetupManager,
                           # TeamManager, NpcManager, ShopManager,
                           # ScoreboardManager
  menu/                    # Menus de setup e fila
  model/                   # Modelos de dados
  npc/                     # Integracao Citizens
  setup/                   # Fluxo de setup in-game
  util/                    # Utilitarios
```

### Como Compilar

Requisitos: Java 8, Maven.

```bash
mvn clean package
```

Jar gerado em `target/NewBedWars-<versao>.jar`.

As dependencias (PaperSpigot API 1.8.8, Citizens) sao resolvidas pelos repositorios no `pom.xml`.

### Criando Addons

O plugin foi projetado para ser extensivel. Voce pode criar addons como plugins separados que interagem com o NewBedWars sem modificar o codigo-fonte.

#### Passo a passo

1. Crie um novo projeto Maven/Gradle
2. Adicione o NewBedWars como dependencia `provided`:

```xml
<dependency>
    <groupId>n.plugins</groupId>
    <artifactId>NewBedWars</artifactId>
    <version>1.3.1</version>
    <scope>provided</scope>
</dependency>
```

3. Declare dependencia no seu `plugin.yml`:

```yaml
depend: [NewBedWars]
```

4. Acesse a instancia principal:

```java
NewBedWars bedwars = (NewBedWars) Bukkit.getPluginManager().getPlugin("NewBedWars");
```

#### O que voce pode fazer em addons

- Ouvir eventos do Bukkit que o NewBedWars dispara durante o ciclo da partida
- Acessar managers publicos (ArenaManager, GameManager, TeamManager, etc.)
- Criar novos modos de jogo estendendo a logica existente
- Adicionar itens/upgrades customizados na loja
- Integrar com sistemas externos (estatisticas, ranks, economias)
- Criar menus e NPCs adicionais

### API e Pontos de Integracao

Principais classes para integracao:

| Classe | Uso |
|--------|-----|
| `NewBedWars` | Instancia principal, acesso a todos os managers |
| `ArenaManager` | CRUD de arenas, persistencia YML |
| `GameManager` | Estado da partida, entrada/saida, morte, respawn |
| `TeamManager` | Times, atribuicao, capacidade |
| `ShopManager` | Compras, upgrades, progressao |
| `NpcManager` | NPCs de fila e loja |
| `ScoreboardManager` | Scoreboard e tablist |
| `BedWarsMode` | Enum dos modos com times e capacidades |

#### Exemplo: addon de estatisticas

```java
public class StatsAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        NewBedWars bw = (NewBedWars) getServer().getPluginManager().getPlugin("NewBedWars");
        if (bw == null) {
            getLogger().severe("NewBedWars nao encontrado!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GameManager gm = bw.getGameManager();
        // Registre listeners, acesse estado das partidas, etc.
    }
}
```

#### Exemplo: addon de economia

```java
@EventHandler
public void onPlayerKill(PlayerDeathEvent e) {
    // Integre com Vault/economia ao detectar kills em arenas
    // Use ArenaManager para verificar se o jogador esta em partida
}
```

---

## Troubleshooting

| Problema | Solucao |
|----------|---------|
| Plugin nao inicia | Verifique Citizens instalado e versao 1.8.8 |
| NPC nao aparece | Recrie o NPC, verifique Citizens funcionando |
| Arena nao fica pronta | Confirme todos os times, geradores e regioes |
| Setup sem opcao nova | Apague config.yml/messages.yml antigos e recarregue |
| Jogador no mundo errado | Verifique nome do mundo na pasta da arena |

## Licenca

[MIT](LICENSE) - use, modifique e distribua livremente.
