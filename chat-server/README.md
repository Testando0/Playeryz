# YumaPlayer Chat Server

Backend independente do Together para o chat persistente do YumaPlayer. O serviço oferece contas próprias do chat, conversas privadas, grupos, histórico em PostgreSQL e entrega em tempo real via WebSocket.

## O que este serviço faz

- Cadastro e login com usuário e senha;
- JWT de longa duração para o aplicativo;
- Conversas privadas e grupos;
- Adição de participantes por nome de usuário;
- Histórico persistente de mensagens;
- Compartilhamento opcional de metadados de música em `track`;
- WebSocket para receber mensagens enquanto o app estiver aberto;
- PostgreSQL, sem depender do armazenamento efêmero do Render.

O Together continua sendo outra funcionalidade: ele sincroniza reprodução em uma sala. Este servidor não usa nem exige uma sessão Together.

## Neon

A configuração `neon.ts` provisiona, na branch `production`, PostgreSQL, Neon Auth, Object Storage, Functions e Realtime. O `neon deploy` gera as variáveis em `.env.local`; esse arquivo é ignorado pelo Git e nunca deve ser versionado.

O servidor carrega `.env.local` automaticamente e usa:

- `DATABASE_URL` para o pool PostgreSQL e aplicação automática de `schema.sql`;
- `NEON_AUTH_JWKS_URL` e `NEON_AUTH_BASE_URL` para verificar tokens do Neon Auth;
- `AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` para URLs assinadas do bucket privado `assets`;
- `NEON_FUNCTION_HOST_SUFFIX` e `NEON_BRANCH_ID` para expor a URL da Function `hello`;
- `NEON_REALTIME_URL` e `NEON_REALTIME_SECRET` para a configuração do Realtime, sem revelar o segredo na API.

Rotas de integração Neon:

- `GET /v1/services` — estado não secreto dos serviços e URL da Function/Realtime;
- `GET /v1/auth/neon/verify` — valida um Bearer token emitido pelo Neon Auth;
- `POST /v1/assets/upload-url` — gera uma URL assinada de upload no bucket `assets` (requer JWT local).

A entrega do chat em tempo real continua usando o WebSocket próprio (`/v1/ws`), enquanto o Neon Realtime fica provisionado e configurado para futuras integrações de eventos do banco.

## Implantar no Render

1. Faça push deste repositório para o GitHub.
2. No Render, selecione **New → Blueprint** e conecte o repositório.
3. O Render detectará o arquivo `/render.yaml`.
4. Confirme a criação do serviço `yumaplayer-chat` e do banco `yumaplayer-chat-db`.
5. Preencha `CORS_ORIGIN` com os clientes autorizados, ou deixe vazio durante o desenvolvimento.
6. Depois do deploy, teste `https://SEU-SERVICO.onrender.com/health`.

O Blueprint gera automaticamente `JWT_SECRET` e injeta `DATABASE_URL` do PostgreSQL. Não coloque esses valores no GitHub. Para usar o Neon no Render, configure as variáveis não secretas e secretas do `.env.local` no painel do serviço, sem publicar o arquivo.

## Desenvolvimento local

Requisitos: Node.js 20+ e PostgreSQL.

```bash
cd chat-server
npm install
DATABASE_URL=postgres://usuario:senha@localhost:5432/yumaplayer_chat \
JWT_SECRET='uma-chave-local-com-pelo-menos-32-caracteres' \
npm start
```

Com Neon CLI, use `neon deploy` para gerar `.env.local` e inicie com `JWT_SECRET` configurado no ambiente. O servidor escuta em `http://localhost:10000`; o schema é aplicado automaticamente na inicialização.

Validação de sintaxe:

```bash
npm run build
```

## API resumida

- `POST /v1/auth/register` — `{ username, displayName, password }`
- `POST /v1/auth/login` — `{ username, password }`
- `GET /v1/me`
- `POST /v1/conversations` — `{ kind: "private"|"group", title?, memberUsernames[] }`
- `GET /v1/conversations`
- `POST /v1/conversations/:id/members` — `{ username }`
- `GET /v1/conversations/:id/messages?limit=50`
- `POST /v1/conversations/:id/messages` — `{ body, track? }`
- `GET /v1/ws?token=JWT` — WebSocket de eventos em tempo real

Todas as rotas, exceto `/health`, `/v1/services` e cadastro/login, usam `Authorization: Bearer SEU_JWT`. A rota `/v1/auth/neon/verify` usa o Bearer token do Neon Auth, e `/v1/assets/upload-url` usa o JWT local do chat.

Eventos WebSocket atuais: `connected`, `message` e `conversation_updated`. O histórico é sempre obtido pela API, então as mensagens continuam disponíveis depois que o usuário fecha o app.

## Próximo passo no Android

Após o deploy, o app precisa receber a URL pública do serviço, por exemplo `https://yumaplayer-chat.onrender.com`, e ganhar uma camada de repositório própria (`ChatRepository`) para login, conversas, histórico e WebSocket. Essa camada deve ficar separada de `MusicTogetherRepository`.
