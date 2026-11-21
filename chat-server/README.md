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

## Implantar no Render

1. Faça push deste repositório para o GitHub.
2. No Render, selecione **New → Blueprint** e conecte o repositório.
3. O Render detectará o arquivo `/render.yaml`.
4. Confirme a criação do serviço `yumaplayer-chat` e do banco `yumaplayer-chat-db`.
5. Preencha `CORS_ORIGIN` com os clientes autorizados, ou deixe vazio durante o desenvolvimento.
6. Depois do deploy, teste `https://SEU-SERVICO.onrender.com/health`.

O Blueprint gera automaticamente `JWT_SECRET` e injeta `DATABASE_URL` do PostgreSQL. Não coloque esses valores no GitHub.

## Desenvolvimento local

Requisitos: Node.js 20+ e PostgreSQL.

```bash
cd chat-server
npm install
DATABASE_URL=postgres://usuario:senha@localhost:5432/yumaplayer_chat \
JWT_SECRET='uma-chave-local-com-pelo-menos-32-caracteres' \
npm start
```

O servidor escuta em `http://localhost:10000`. O schema é aplicado automaticamente na inicialização.

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

Todas as rotas, exceto `/health` e cadastro/login, usam `Authorization: Bearer SEU_JWT`.

Eventos WebSocket atuais: `connected`, `message` e `conversation_updated`. O histórico é sempre obtido pela API, então as mensagens continuam disponíveis depois que o usuário fecha o app.

## Próximo passo no Android

Após o deploy, o app precisa receber a URL pública do serviço, por exemplo `https://yumaplayer-chat.onrender.com`, e ganhar uma camada de repositório própria (`ChatRepository`) para login, conversas, histórico e WebSocket. Essa camada deve ficar separada de `MusicTogetherRepository`.
