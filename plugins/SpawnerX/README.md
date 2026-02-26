# SpawnerX v2.0.0

**SpawnerX** é um plugin completo para gerenciamento de spawners em servidores Bukkit/Paper do Minecraft 1.21. O plugin oferece controle total sobre a quebra, colocação e interação com spawners, com sistema de raridade configurável.

---

## Características Principais

O plugin oferece as seguintes funcionalidades:

*   **Menu de Interação (Novo)**: Use **Shift + Botão Direito** no spawner para abrir um menu de informações.
*   **Sistema de Upgrade em Cadeia (v2)**: Use **Clique Esquerdo** no spawner com o material do próximo nível para evoluir (`0 -> 1 -> 2...`), com animação de partículas e sons.
*   **Sistema de Quebra Aprimorado (Novo)**: Opção para permitir a quebra de spawners mesmo sem cumprir os requisitos (com aviso e som), ou manter o comportamento padrão de cancelamento.
*   **Sistema de Explosões**: Configure se spawners podem dropar ao serem destruídos por explosões, com chance de drop ajustável.
*   **Sistema de Raridade v2**: Raridades definidas em `rarity-settings` e mapeamento por entidade em `mob-rarities`.
*   **Vendas Públicas por XP (Novo)**: Anuncie spawners com `/spawnerx sell xp <valor> <quantidade>`, compre pela aba de vendas públicas no `/spawnerx shop` e acompanhe status com `/spawnerx sell stats`.
*   **Expiração Segura de Anúncios**: Quando um anúncio expira, o spawner é devolvido ao vendedor (instantâneo se online, ou no próximo login se offline).
*   **Persistência SQLite**: O marketplace usa `plugins/SpawnerX/database/sales.db`, com migração automática de `sales.yml` (backup em `sales.yml.bak`).
*   **Locale Configurável**: Todas as mensagens do plugin são externalizadas em arquivos de locale, atualmente com suporte a português brasileiro.
*   **Comandos Administrativos**: Gerencie spawners através de comandos com auto-complete para jogadores e tipos de mobs.

---

## Comandos

O plugin utiliza um comando principal com diversos subcomandos:

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/spawnerx` ou `/sx` | Comando principal (mostra ajuda) | - |
| `/spawnerx reload` | Recarrega as configurações do plugin | `spawnerx.admin` |
| `/spawnerx give <player> <mob> [quantidade]` | Dá um spawner a um jogador | `spawnerx.admin` |
| `/spawnerx shop` | Abre a loja de spawners | `spawnerx.shop` |
| `/spawnerx help` | Mostra a mensagem de ajuda | - |
| `/spawnerx sell xp <valor> <quantidade>` | Lista a quantidade indicada de spawners da mão para venda pública por XP | `spawnerx.sell` |
| `/spawnerx sell stats` | Exibe status de anúncios ativos, XP pendente e histórico do próprio jogador | `spawnerx.sell` |
| `/spawnerx sell stats <player>` | Consulta status/histórico de outro jogador | `spawnerx.admin` |

**Auto-complete**: O plugin oferece auto-complete para jogadores online e tipos de mobs disponíveis ao usar o comando `/spawnerx give`.

---

## Menu de Interação (Shift + Botão Direito)

Ao interagir com um spawner segurando **Shift e clicando com o Botão Direito**, o jogador abrirá um menu de informações.

**Objetivo**: Fornecer informações rápidas sobre o spawner, como o tipo de entidade que ele gera e sua raridade, sem a necessidade de comandos.

**Ações**:
*   **Visualização**: O menu exibe um item central (o próprio spawner) com o nome da entidade e a raridade.
*   **Sem Ações de Modificação**: Este menu é apenas informativo e não permite que o jogador modifique o spawner.

---

## Sistema de Upgrade (Clique Esquerdo)

Para evoluir um spawner:

1. Segure na **mão principal** o material do próximo nível configurado em `spawner.upgrade.levels`.
2. Clique com o **botão esquerdo** no spawner.
3. O upgrade só acontece em cadeia (`0 -> 1 -> 2...`), sem pular níveis.
4. Spawners com stack maior que `1` não podem ser evoluídos.

Se o upgrade for válido, o plugin consome os materiais, aplica os novos atributos e executa animação visual/sonora.

---

## Permissões

O sistema de permissões do plugin é simples e direto:

| Permissão | Descrição | Padrão |
|-----------|-----------|-----------|
| `spawnerx.admin` | Permite uso de comandos administrativos (reload, give) | OP |
| `spawnerx.shop` | Permite abrir a loja de spawners | Todos |
| `spawnerx.sell` | Permite anunciar spawners em vendas públicas | Todos |
| `spawnerx.spawner.break` | Permite quebrar spawners respeitando os requisitos configurados | Todos |
| `spawnerx.spawner.break.explosion` | Permite drop de spawner ao ser destruído por explosão | OP |
| `spawnerx.spawner.upgrade` | Permite evoluir spawners (quando `require-permission` estiver ativo) | Todos |

---

## Configuração

### config.yml

O arquivo `config.yml` controla todos os aspectos do comportamento do plugin:

```yaml
# SpawnerX - Configuração Principal
locale: pt_BR

# Sistema de Quebra de Spawner
break:
  required-tool: "DIAMOND_PICKAXE"  # Ferramenta necessária
  require-silk-touch: true           # Se Toque Suave é obrigatório
  allow-break-without-requirements: true # NOVO: Permite quebrar sem requisitos (com aviso e som)

# Sistema de Explosões
explosion:
  allow-drop: true      # Se spawners podem dropar ao explodir
  drop-chance: 50.0     # Chance de drop em porcentagem (0-100)

# Sistema de Spawners
spawner:
  display-name: "&8[&f{rarity}&8] &eSpawner de {type}" # Removido {amount}x
  lore:
    - ""
    - "&7| &fInformações:"
    - "&7| &fEntidade: &e{type}"
    - "&7| &fRaridade: {rarity_color}{rarity}"
    - ""

# Sistema de Raridade
rarities:
  PIG: "&fComum"
  COW: "&fComum"
  SHEEP: "&fComum"
  CHICKEN: "&fComum"
  ZOMBIE: "&aRaro"
  SKELETON: "&aRaro"
  SPIDER: "&aRaro"
  CREEPER: "&6Épico"
  ENDERMAN: "&6Épico"
  BLAZE: "&dEspecial"
  IRON_GOLEM: "&bLendário"
  DEFAULT: "&fComum"  # Usado para mobs não listados
```

**Placeholders Disponíveis**:
*   `{type}` - Nome da entidade
*   `{rarity}` - Texto da raridade (sem cor)
*   `{rarity_color}` - Código de cor da raridade

### locale/pt_BR.yml

O arquivo de locale foi atualizado para incluir as mensagens do novo sistema de quebra e do menu de interação.

---

## Instalação

1.  Baixe o arquivo `SpawnerX.jar`
2.  Coloque o arquivo na pasta `plugins/` do seu servidor
3.  Inicie ou reinicie o servidor
4.  Configure os arquivos em `plugins/SpawnerX/` conforme necessário
5.  Use `/spawnerx reload` para aplicar mudanças sem reiniciar

---

## Compilação

### Requisitos

*   Java Development Kit (JDK) 25
*   Apache Maven 3.6 ou superior

### Comando de Build

```bash
mvn clean package
```

O arquivo compilado estará em `target/SpawnerX-2.0.0.jar`.

---

## Compatibilidade

*   **Plataforma**: Bukkit / Paper
*   **Versão do Minecraft**: 1.21.x (prioridade para 1.21.1)
*   **Java**: 25

---

## Estrutura do Projeto

```
SpawnerX/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── spawnerx/
│       │           ├── commands/
│       │           │   └── SpawnerXCommand.java
│       │           ├── listeners/
│       │           │   ├── SpawnerBreakListener.java
│       │           │   ├── SpawnerExplosionListener.java
│       │           │   ├── SpawnerPlaceListener.java
│       │           │   └── SpawnerInteractListener.java  <-- NOVO
│       │           ├── managers/
│       │           │   ├── ConfigManager.java
│       │           │   ├── LocaleManager.java
│       │           │   └── SpawnerManager.java
│       │           ├── utils/
│       │           │   └── SpawnerUtils.java
│       │           └── SpawnerX.java
│       └── resources/
│           ├── locale/
│           │   └── pt_BR.yml
│           ├── config.yml
│           └── plugin.yml
├── pom.xml
└── README.md
```

---

**Desenvolvido com dedicação para a comunidade Minecraft.**
