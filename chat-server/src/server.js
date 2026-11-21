import 'dotenv/config';
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';
import express from 'express';
import cors from 'cors';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import pg from 'pg';
import { WebSocketServer, WebSocket } from 'ws';

const { Pool } = pg;
const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET || JWT_SECRET.length < 32) {
  throw new Error('JWT_SECRET must contain at least 32 characters');
}
if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL.includes('localhost') ? false : { rejectUnauthorized: false },
  max: Number(process.env.DB_POOL_MAX || 10),
});

const app = express();
app.disable('x-powered-by');
app.use(cors({ origin: process.env.CORS_ORIGIN?.split(',').map((v) => v.trim()) || true }));
app.use(express.json({ limit: '64kb' }));

const socketsByUser = new Map();
const id = () => crypto.randomUUID();
const cleanText = (value, max) => String(value ?? '').trim().slice(0, max);
const publicUser = (row) => ({ id: row.id, username: row.username, displayName: row.display_name });
const tokenFor = (user) => jwt.sign({ sub: user.id, username: user.username }, JWT_SECRET, { expiresIn: '30d' });

function authFromRequest(req) {
  const header = req.headers.authorization || '';
  if (!header.startsWith('Bearer ')) return null;
  try { return jwt.verify(header.slice(7), JWT_SECRET); } catch { return null; }
}

function requireAuth(req, res, next) {
  const claims = authFromRequest(req);
  if (!claims?.sub) return res.status(401).json({ error: 'authentication_required' });
  req.userId = claims.sub;
  next();
}

async function userById(userId) {
  const { rows } = await pool.query('SELECT id, username, display_name FROM users WHERE id = $1', [userId]);
  return rows[0] || null;
}

async function isMember(userId, conversationId) {
  const { rowCount } = await pool.query(
    'SELECT 1 FROM conversation_members WHERE user_id = $1 AND conversation_id = $2',
    [userId, conversationId],
  );
  return rowCount > 0;
}

async function conversationForUser(userId, conversationId) {
  const { rows } = await pool.query(
    `SELECT c.id, c.kind, c.title, c.created_by, c.created_at,
            COALESCE(json_agg(json_build_object('id', u.id, 'username', u.username, 'displayName', u.display_name, 'role', cm.role))
              FILTER (WHERE u.id IS NOT NULL), '[]') AS members
       FROM conversations c
       JOIN conversation_members mine ON mine.conversation_id = c.id AND mine.user_id = $1
       LEFT JOIN conversation_members cm ON cm.conversation_id = c.id
       LEFT JOIN users u ON u.id = cm.user_id
      WHERE c.id = $2
      GROUP BY c.id`, [userId, conversationId]);
  return rows[0] || null;
}

function conversationJson(row) {
  return { id: row.id, kind: row.kind, title: row.title, createdBy: row.created_by, createdAt: row.created_at, members: row.members || [] };
}

async function messageRow(messageId) {
  const { rows } = await pool.query(
    `SELECT m.id, m.conversation_id, m.sender_id, m.body, m.track, m.created_at,
            u.username, u.display_name
       FROM messages m JOIN users u ON u.id = m.sender_id WHERE m.id = $1`, [messageId]);
  return rows[0];
}

function messageJson(row) {
  return { id: row.id, conversationId: row.conversation_id, sender: { id: row.sender_id, username: row.username, displayName: row.display_name }, body: row.body, track: row.track, createdAt: row.created_at };
}

function sendSocket(userId, payload) {
  const sockets = socketsByUser.get(userId) || new Set();
  const data = JSON.stringify(payload);
  for (const socket of sockets) if (socket.readyState === WebSocket.OPEN) socket.send(data);
}

async function broadcastConversation(conversationId, payload) {
  const { rows } = await pool.query('SELECT user_id FROM conversation_members WHERE conversation_id = $1', [conversationId]);
  for (const row of rows) sendSocket(row.user_id, payload);
}

app.get('/health', async (_req, res) => {
  try { await pool.query('SELECT 1'); res.json({ ok: true, service: 'yumaplayer-chat' }); }
  catch { res.status(503).json({ ok: false }); }
});

app.post('/v1/auth/register', async (req, res) => {
  const username = cleanText(req.body?.username, 40).toLowerCase();
  const displayName = cleanText(req.body?.displayName || username, 80);
  const password = String(req.body?.password || '');
  if (!/^[a-z0-9_.-]{3,40}$/.test(username)) return res.status(400).json({ error: 'invalid_username' });
  if (password.length < 8 || password.length > 128) return res.status(400).json({ error: 'invalid_password' });
  try {
    const hash = await bcrypt.hash(password, 12);
    const { rows } = await pool.query(
      'INSERT INTO users (username, display_name, password_hash) VALUES ($1, $2, $3) RETURNING id, username, display_name',
      [username, displayName || username, hash],
    );
    const user = publicUser(rows[0]);
    res.status(201).json({ token: tokenFor(user), user });
  } catch (error) {
    if (error.code === '23505') return res.status(409).json({ error: 'username_taken' });
    console.error(error); res.status(500).json({ error: 'server_error' });
  }
});

app.post('/v1/auth/login', async (req, res) => {
  const username = cleanText(req.body?.username, 40).toLowerCase();
  const password = String(req.body?.password || '');
  const { rows } = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
  if (!rows[0] || !(await bcrypt.compare(password, rows[0].password_hash))) return res.status(401).json({ error: 'invalid_credentials' });
  const user = publicUser(rows[0]);
  res.json({ token: tokenFor(user), user });
});

app.get('/v1/me', requireAuth, async (req, res) => {
  const user = await userById(req.userId);
  if (!user) return res.status(401).json({ error: 'user_not_found' });
  res.json({ user: publicUser(user) });
});

app.post('/v1/conversations', requireAuth, async (req, res) => {
  const kind = req.body?.kind === 'group' ? 'group' : 'private';
  const title = kind === 'group' ? cleanText(req.body?.title, 120) : null;
  const requestedMembers = Array.isArray(req.body?.memberUsernames) ? req.body.memberUsernames : [];
  if (kind === 'group' && !title) return res.status(400).json({ error: 'group_title_required' });
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const conversation = await client.query(
      'INSERT INTO conversations (kind, title, created_by) VALUES ($1, $2, $3) RETURNING *', [kind, title, req.userId]);
    const conversationId = conversation.rows[0].id;
    const usernames = [...new Set([...(kind === 'private' ? requestedMembers.slice(0, 1) : requestedMembers), (await userById(req.userId)).username])].map((v) => String(v).toLowerCase());
    if (kind === 'private' && usernames.length !== 2) return res.status(400).json({ error: 'private_chat_needs_one_member' });
    const members = await client.query('SELECT id, username FROM users WHERE username = ANY($1::text[])', [usernames]);
    if (members.rowCount !== usernames.length) return res.status(404).json({ error: 'member_not_found' });
    for (const member of members.rows) await client.query('INSERT INTO conversation_members (conversation_id, user_id, role) VALUES ($1, $2, $3)', [conversationId, member.id, member.id === req.userId ? 'owner' : 'member']);
    await client.query('COMMIT');
    res.status(201).json({ conversation: conversationJson(await conversationForUser(req.userId, conversationId)) });
  } catch (error) { await client.query('ROLLBACK'); console.error(error); res.status(500).json({ error: 'server_error' }); }
  finally { client.release(); }
});

app.get('/v1/conversations', requireAuth, async (req, res) => {
  const { rows } = await pool.query(
    `SELECT c.id, c.kind, c.title, c.created_by, c.created_at,
       (SELECT max(created_at) FROM messages m WHERE m.conversation_id = c.id) AS last_message_at,
       COALESCE((SELECT json_agg(json_build_object('id', u.id, 'username', u.username, 'displayName', u.display_name, 'role', cm2.role)) FROM conversation_members cm2 JOIN users u ON u.id = cm2.user_id WHERE cm2.conversation_id = c.id), '[]') AS members
      FROM conversations c JOIN conversation_members cm ON cm.conversation_id = c.id
     WHERE cm.user_id = $1 ORDER BY COALESCE((SELECT max(created_at) FROM messages m WHERE m.conversation_id = c.id), c.created_at) DESC`, [req.userId]);
  res.json({ conversations: rows.map(conversationJson) });
});

app.post('/v1/conversations/:conversationId/members', requireAuth, async (req, res) => {
  const conversationId = req.params.conversationId;
  const conversation = await conversationForUser(req.userId, conversationId);
  if (!conversation) return res.status(404).json({ error: 'conversation_not_found' });
  if (conversation.kind !== 'group') return res.status(400).json({ error: 'private_conversation_cannot_add_members' });
  const username = cleanText(req.body?.username, 40).toLowerCase();
  const member = (await pool.query('SELECT id, username, display_name FROM users WHERE username = $1', [username])).rows[0];
  if (!member) return res.status(404).json({ error: 'member_not_found' });
  try {
    await pool.query('INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1, $2)', [conversationId, member.id]);
    const updated = await conversationForUser(req.userId, conversationId);
    await broadcastConversation(conversationId, { type: 'conversation_updated', conversation: conversationJson(updated) });
    res.status(201).json({ conversation: conversationJson(updated) });
  } catch (error) { if (error.code === '23505') return res.status(409).json({ error: 'already_member' }); res.status(500).json({ error: 'server_error' }); }
});

app.get('/v1/conversations/:conversationId/messages', requireAuth, async (req, res) => {
  if (!(await isMember(req.userId, req.params.conversationId))) return res.status(404).json({ error: 'conversation_not_found' });
  const limit = Math.min(Math.max(Number(req.query.limit || 50), 1), 100);
  const before = req.query.before ? new Date(req.query.before) : new Date('9999-12-31');
  const { rows } = await pool.query(
    `SELECT m.id, m.conversation_id, m.sender_id, m.body, m.track, m.created_at, u.username, u.display_name
       FROM messages m JOIN users u ON u.id = m.sender_id
      WHERE m.conversation_id = $1 AND m.created_at < $2 ORDER BY m.created_at DESC LIMIT $3`, [req.params.conversationId, before, limit]);
  res.json({ messages: rows.reverse().map(messageJson) });
});

app.post('/v1/conversations/:conversationId/messages', requireAuth, async (req, res) => {
  const conversationId = req.params.conversationId;
  if (!(await isMember(req.userId, conversationId))) return res.status(404).json({ error: 'conversation_not_found' });
  const body = cleanText(req.body?.body, 4000);
  const track = req.body?.track && typeof req.body.track === 'object' ? req.body.track : null;
  if (!body && !track) return res.status(400).json({ error: 'message_empty' });
  const { rows } = await pool.query('INSERT INTO messages (conversation_id, sender_id, body, track) VALUES ($1, $2, $3, $4) RETURNING id', [conversationId, req.userId, body || '🎵', track ? JSON.stringify(track) : null]);
  const message = messageJson(await messageRow(rows[0].id));
  await broadcastConversation(conversationId, { type: 'message', message });
  res.status(201).json({ message });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });
server.on('upgrade', (request, socket, head) => {
  if (new URL(request.url, `http://${request.headers.host}`).pathname !== '/v1/ws') return socket.destroy();
  const token = new URL(request.url, `http://${request.headers.host}`).searchParams.get('token');
  let claims; try { claims = jwt.verify(token || '', JWT_SECRET); } catch { socket.destroy(); return; }
  wss.handleUpgrade(request, socket, head, (ws) => wss.emit('connection', ws, claims.sub));
});
wss.on('connection', (ws, userId) => {
  if (!socketsByUser.has(userId)) socketsByUser.set(userId, new Set());
  socketsByUser.get(userId).add(ws);
  ws.send(JSON.stringify({ type: 'connected' }));
  ws.on('close', () => { const sockets = socketsByUser.get(userId); sockets?.delete(ws); if (!sockets?.size) socketsByUser.delete(userId); });
});

const schema = await readFile(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'schema.sql'), 'utf8');
await pool.query(schema);
server.listen(PORT, '0.0.0.0', () => console.log(`YumaPlayer chat server listening on ${PORT}`));
process.on('SIGTERM', async () => { await pool.end(); process.exit(0); });
