# Changelog

## 1.3.1

- NPCs de loja e melhorias agora são jogadores do Citizens, em vez de
  Villagers.
- A loja de itens usa a skin de `_marlee1` e a loja de melhorias usa a skin de
  `smhliv`, com ambos os nomes configuráveis no `config.yml`.
- As skins são aplicadas somente na criação ou quando alteradas, sem novas
  consultas no ciclo periódico de atualização.
- O nome padrão da loja foi encurtado para caber no limite de nome de NPC
  jogador do Minecraft 1.8.8.
- Configuração atualizada para a estrutura 5.

## 1.3.0

- NPCs de loja e melhorias agora recebem nome visível usando a própria
  nameplate do Villager, sem criar ArmorStands adicionais.
- Os NPCs calculam a direção horizontal até o spawn da própria equipe e
  corrigem a rotação somente quando ela realmente se desvia.
- O estado de voo de jogadores mortos e espectadores é sincronizado novamente
  após o respawn e o teletransporte, corrigindo o voo inconsistente da 1.8.8.
- O sistema de clonagem agora remove todos os ArmorStands diretamente dos
  arquivos Anvil `.mca`, em segundo plano e antes de carregar o mundo.
- Chunks distantes não precisam mais ser visitados para que hologramas antigos
  sejam eliminados.
- Sanitização de regiões validada para preservar entidades e dados normais,
  além de ser segura para execuções repetidas.
- Prefixo, scoreboard, tablist e menu de fila atualizados para a identidade
  NewBedWars, com migração apenas dos valores padrão antigos.
- Configurações atualizadas para a estrutura 4 e mensagens para a estrutura 3.

## 1.2.0

- Removidos em lotes os hologramas persistidos (`ArmorStand`) dos mapas-base e
  dos clones, evitando picos ao processar milhares de entidades no mesmo tick.
- Novos hologramas são bloqueados em mundos de partida e também são limpos
  quando chunks do mapa são carregados posteriormente.
- A partida só é liberada após a limpeza inicial do clone.
- NPCs de loja e melhorias agora são criados automaticamente em uma fila, após
  o teletransporte dos jogadores, com carregamento de chunk e novas tentativas.
- NPCs de loja não criam ArmorStands de holograma.
- Mapas-base sem jogadores são descarregados depois que o clone fica pronto.
- Adicionados limites configuráveis de entidades e NPCs processados por tick.

## 1.1.1

- Corrigida `NullPointerException` no `NPCSpawnEvent` quando o Citizens
  restaurava NPCs antes da primeira atualização do cache de hologramas.
- Textos e entidades dos hologramas agora são validados em todas as entradas,
  com valores seguros mesmo durante o carregamento inicial.

## 1.1.0

- Corrigido carregamento antecipado de todos os mapas-base.
- Adicionada clonagem e exclusão assíncrona de mundos em fila.
- Adicionada entrada por callback após o mundo ficar pronto.
- Adicionados limites de blocos processados por tick.
- Otimizados scoreboard, tablist, visibilidade, geradores e NPCs.
- Adicionadas migrações versionadas e recarga segura.
- Modernizados prefixo, chat e mensagens principais.
- Corrigidos cache de jogadores mínimos por modo, entrada sem arena disponível,
  chat em mundos de setup e limpeza de caches de partidas.
