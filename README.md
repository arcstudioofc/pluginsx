# PluginsX

Projeto do plugin PluginsX (Bukkit/Paper) e sua documentação oficial.

## Requisitos

- Node.js >= 24
- pnpm >= 10.9.0

## Rodando a documentação

```bash
pnpm install
pnpm -C web dev
```

Abra `http://localhost:3000`.

## Estrutura do monorepo

- `plugin/` — plugin PluginsX (Java/Maven). Veja detalhes em `plugin/README.md`.
- `web/` — site e docs (Next.js + Fumadocs).

## Comandos úteis

- `pnpm dev` — roda os projetos via Turbo.
- `pnpm -C web dev` — roda apenas o site/docs.
- `pnpm -C web build` — build do site/docs.
