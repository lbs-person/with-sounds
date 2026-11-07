#!/usr/bin/env node
'use strict';

/*
 * 同频 · Tongpin room relay
 * ------------------------------------------------------------------
 * A deliberately dependency-free relay for the Tongpin Android app.
 *
 *   node server.js [port]
 *
 * It does three jobs:
 *   1. keeps a tiny room directory (6 digit codes, host tokens, members)
 *   2. accepts one audio upload per room and streams it back with HTTP Range
 *   3. fans out playback / chat / reaction events over WebSocket
 *
 * No npm install, no database. Room state lives in memory and uploaded
 * audio lives in ./data. Suitable for a LAN, a VPS or a container.
 */

const http = require('http');
const https = require('https');
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const crypto = require('crypto');
const os = require('os');

const PORT = Number(process.env.PORT || process.argv[2] || 8080);
const HOST = process.env.HOST || '0.0.0.0';
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(__dirname, 'data'));
const MAX_UPLOAD = Number(process.env.MAX_UPLOAD || 300 * 1024 * 1024);
const MAX_COVER = 6 * 1024 * 1024;
const MAX_WS_MESSAGE = 1024 * 1024;
const ROOM_TTL_MS = 12 * 60 * 60 * 1000;
const EMPTY_ROOM_TTL_MS = 10 * 60 * 1000;
const MAX_MEMBERS = 32;
const SERVER_NAME = process.env.SERVER_NAME || '同频电台';
const PUBLIC_URL = (process.env.PUBLIC_URL || '').replace(/\/+$/, '');
const ALLOWED_REACTIONS = new Set(['❤️', '🔥', '😭', '🎉', '👏', '✨', '🥺', '🫶']);

const MEMBER_COLORS = [
  '#FF8A66', '#91E8C9', '#F6C56B', '#A8A6FF', '#FF9EC4',
  '#79C7FF', '#C7E879', '#FFB38A', '#B8A1FF', '#7DE0C3',
];

fs.mkdirSync(DATA_DIR, { recursive: true });
// Remove half-written uploads left behind by a crash or a dropped connection.
try {
  for (const entry of fs.readdirSync(DATA_DIR)) {
    if (entry.endsWith('.part')) {
      fs.unlinkSync(path.join(DATA_DIR, entry));
    }
  }
} catch (err) {
  console.warn('could not clean partial uploads:', err.message);
}

/** @type {Map<string, Room>} */
const rooms = new Map();

function now() {
  return Date.now();
}

function randomHex(bytes = 8) {
  return crypto.randomBytes(bytes).toString('hex');
}

function randomCode() {
  for (let i = 0; i < 100; i++) {
    const code = String(crypto.randomInt(100000, 1000000));
    if (!rooms.has(code)) return code;
  }
  throw new Error('no room code available');
}

function clamp(n, min, max) {
  n = Number(n);
  if (!Number.isFinite(n)) return min;
  return Math.min(max, Math.max(min, n));
}

function safeText(value, maxLength) {
  if (typeof value !== 'string') return '';
  return value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '').trim().slice(0, maxLength);
}

function makeRoom(hostName) {
  return {
    code: randomCode(),
    hostToken: randomHex(24),
    hostId: null,
    hostName,
    createdAt: now(),
    lastActive: now(),
    track: null,
    cover: null,
    playback: { playing: false, positionMs: 0, startAt: 0, updatedAt: now() },
    members: new Map(),
    chats: [],
  };
}

function publicMembers(room) {
  return [...room.members.values()]
    .sort((a, b) => a.joinedAt - b.joinedAt)
    .map((m) => ({
      id: m.id,
      name: m.name,
      role: m.role,
      color: m.color,
      joinedAt: m.joinedAt,
    }));
}

function publicTrack(room) {
  if (!room.track) return null;
  const t = room.track;
  return {
    id: t.id,
    title: t.title,
    artist: t.artist,
    album: t.album,
    durationMs: t.durationMs,
    size: t.size,
    contentType: t.contentType,
    cover: Boolean(room.cover),
    uploadedAt: t.uploadedAt,
    audioUrl: `/api/rooms/${room.code}/audio?track=${t.id}`,
    coverUrl: room.cover ? `/api/rooms/${room.code}/cover?track=${t.id}` : null,
  };
}

function publicRoom(room) {
  return {
    code: room.code,
    hostId: room.hostId,
    hostName: room.hostName,
    createdAt: room.createdAt,
    members: publicMembers(room),
    track: publicTrack(room),
    playback: room.playback,
  };
}

function sendJson(res, status, body) {
  const payload = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': payload.length,
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
  });
  res.end(payload);
}

function sendError(res, status, message) {
  sendJson(res, status, { error: message });
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > limit) {
        reject(Object.assign(new Error('body too large'), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

async function readJson(req, limit = 64 * 1024) {
  const body = await readBody(req, limit);
  if (!body.length) return {};
  try {
    return JSON.parse(body.toString('utf8'));
  } catch {
    throw Object.assign(new Error('invalid json'), { status: 400 });
  }
}

function validateRoomCode(code) {
  return typeof code === 'string' && /^\d{6}$/.test(code);
}

function contentTypeFor(file, fallback) {
  if (fallback && /^(audio|image)\//.test(fallback)) return fallback;
  const ext = path.extname(file).toLowerCase();
  return ({
    '.mp3': 'audio/mpeg',
    '.flac': 'audio/flac',
    '.ogg': 'audio/ogg',
    '.opus': 'audio/ogg',
    '.m4a': 'audio/mp4',
    '.aac': 'audio/aac',
    '.wav': 'audio/wav',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.png': 'image/png',
    '.webp': 'image/webp',
  })[ext] || 'application/octet-stream';
}

async function removeQuietly(file) {
  if (!file) return;
  try { await fsp.unlink(file); } catch { /* already gone */ }
}

function broadcast(room, object, except = null) {
  const data = JSON.stringify(object);
  for (const member of room.members.values()) {
    if (member === except) continue;
    sendSocketText(member.socket, data);
  }
}

function broadcastMembers(room) {
  broadcast(room, { type: 'members', members: publicMembers(room), hostId: room.hostId, hostName: room.hostName });
}

function touch(room) {
  room.lastActive = now();
}

function playbackAt(room, at = now()) {
  const p = room.playback;
  if (!p.playing) return p.positionMs;
  return Math.max(0, p.positionMs + Math.max(0, at - p.startAt));
}

function stopPlayback(room) {
  room.playback = {
    playing: false,
    positionMs: Math.max(0, playbackAt(room)),
    startAt: 0,
    updatedAt: now(),
  };
}

function removeMember(room, member) {
  room.members.delete(member.id);
  const wasHost = room.hostId === member.id;
  if (wasHost) {
    stopPlayback(room);
    room.hostId = null;
    broadcast(room, {
      type: 'host_left',
      name: member.name,
      playback: room.playback,
      serverNow: now(),
    });
  }
  touch(room);
  broadcastMembers(room);
  console.log(`[room ${room.code}] - ${member.name} (${member.role}) left`);
}

// ---------------------------------------------------------------------------
// WebSocket framing (RFC 6455, text/binary, ping/pong/close)
// ---------------------------------------------------------------------------

function wsAccept(key) {
  return crypto.createHash('sha1')
    .update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
    .digest('base64');
}

function encodeFrame(data, opcode = 0x1) {
  const payload = Buffer.isBuffer(data) ? data : Buffer.from(String(data), 'utf8');
  let header;
  if (payload.length < 126) {
    header = Buffer.alloc(2);
    header[1] = payload.length;
  } else if (payload.length < 65536) {
    header = Buffer.alloc(4);
    header[1] = 126;
    header.writeUInt16BE(payload.length, 2);
  } else {
    header = Buffer.alloc(10);
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(payload.length), 2);
  }
  header[0] = 0x80 | opcode;
  return Buffer.concat([header, payload]);
}

function sendSocketText(socket, text) {
  if (!socket || socket.destroyed || !socket.writable) return;
  try { socket.write(encodeFrame(text)); } catch { /* closed */ }
}

function sendSocketControl(socket, opcode, payload = Buffer.alloc(0)) {
  if (socket.destroyed || !socket.writable) return;
  try { socket.write(encodeFrame(payload, opcode)); } catch { /* closed */ }
}

function closeSocket(socket, code = 1000, reason = '') {
  if (socket.destroyed) return;
  const payload = Buffer.alloc(2 + Buffer.byteLength(reason));
  payload.writeUInt16BE(code, 0);
  payload.write(reason, 2);
  sendSocketControl(socket, 0x8, payload);
  setTimeout(() => socket.destroy(), 80);
}

function attachWebSocket(socket, room, member) {
  member.socket = socket;
  member.alive = true;
  let buffer = Buffer.alloc(0);
  let fragments = [];
  let fragmentOpcode = 0;

  socket.setNoDelay(true);
  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (true) {
      if (buffer.length < 2) return;
      const b0 = buffer[0];
      const b1 = buffer[1];
      const fin = (b0 & 0x80) !== 0;
      const rsv = b0 & 0x70;
      const opcode = b0 & 0x0f;
      const masked = (b1 & 0x80) !== 0;
      let length = b1 & 0x7f;
      let offset = 2;
      if (rsv !== 0) return closeSocket(socket, 1002, 'RSV must be zero');
      if (!masked) return closeSocket(socket, 1002, 'client frames must be masked');
      if (length === 126) {
        if (buffer.length < 4) return;
        length = buffer.readUInt16BE(2);
        offset = 4;
      } else if (length === 127) {
        if (buffer.length < 10) return;
        const big = buffer.readBigUInt64BE(2);
        if (big > BigInt(MAX_WS_MESSAGE)) return closeSocket(socket, 1009, 'message too large');
        length = Number(big);
        offset = 10;
      }
      if (length > MAX_WS_MESSAGE) return closeSocket(socket, 1009, 'message too large');
      if (buffer.length < offset + 4 + length) return;
      const mask = buffer.subarray(offset, offset + 4);
      offset += 4;
      const payload = Buffer.allocUnsafe(length);
      for (let i = 0; i < length; i++) payload[i] = buffer[offset + i] ^ mask[i & 3];
      buffer = buffer.subarray(offset + length);

      if (opcode === 0x8) {
        closeSocket(socket, 1000);
        return;
      }
      if (opcode === 0x9) {
        sendSocketControl(socket, 0xA, payload);
        continue;
      }
      if (opcode === 0xA) {
        member.alive = true;
        continue;
      }
      if (opcode === 0x1 || opcode === 0x2) {
        if (fin) {
          handleMessage(room, member, payload.toString('utf8'));
        } else {
          fragments = [payload];
          fragmentOpcode = opcode;
        }
        continue;
      }
      if (opcode === 0x0) {
        fragments.push(payload);
        const total = fragments.reduce((n, b) => n + b.length, 0);
        if (total > MAX_WS_MESSAGE) return closeSocket(socket, 1009, 'message too large');
        if (fin) {
          const whole = Buffer.concat(fragments);
          fragments = [];
          if (fragmentOpcode === 0x1) handleMessage(room, member, whole.toString('utf8'));
        }
        continue;
      }
      return closeSocket(socket, 1002, 'unsupported opcode');
    }
  });

  socket.on('error', () => { /* close handler cleans up */ });
  socket.on('close', () => {
    if (room.members.get(member.id) === member) removeMember(room, member);
  });
}

function handleMessage(room, member, raw) {
  if (raw.length > MAX_WS_MESSAGE) return;
  let msg;
  try { msg = JSON.parse(raw); } catch { return; }
  if (!msg || typeof msg.type !== 'string') return;
  touch(room);

  const isHost = member.role === 'host';

  switch (msg.type) {
    case 'time': {
      sendSocketText(member.socket, JSON.stringify({
        type: 'time',
        t0: Number(msg.t0) || 0,
        t1: now(),
      }));
      return;
    }

    case 'play': {
      if (!isHost) return;
      const positionMs = clamp(msg.positionMs, 0, 24 * 60 * 60 * 1000);
      room.playback = {
        playing: true,
        positionMs,
        // 1.1s gives every phone time to buffer before the shared start.
        startAt: now() + 1100,
        updatedAt: now(),
      };
      broadcast(room, {
        type: 'playback',
        action: 'play',
        positionMs,
        startAt: room.playback.startAt,
        playing: true,
        by: member.name,
        serverNow: now(),
      });
      return;
    }

    case 'pause': {
      if (!isHost) return;
      const positionMs = clamp(msg.positionMs, 0, 24 * 60 * 60 * 1000);
      room.playback = { playing: false, positionMs, startAt: 0, updatedAt: now() };
      broadcast(room, {
        type: 'playback',
        action: 'pause',
        positionMs,
        startAt: 0,
        playing: false,
        by: member.name,
        serverNow: now(),
      });
      return;
    }

    case 'seek': {
      if (!isHost) return;
      const positionMs = clamp(msg.positionMs, 0, 24 * 60 * 60 * 1000);
      const playing = Boolean(msg.playing);
      const startAt = playing ? now() + 700 : 0;
      room.playback = { playing, positionMs, startAt, updatedAt: now() };
      broadcast(room, {
        type: 'playback',
        action: 'seek',
        positionMs,
        startAt,
        playing,
        by: member.name,
        serverNow: now(),
      });
      return;
    }

    case 'end': {
      if (!isHost) return;
      broadcast(room, { type: 'ended', by: member.name });
      setTimeout(() => destroyRoom(room.code, 'host ended the room'), 300);
      return;
    }

    case 'chat': {
      const text = safeText(msg.text, 300);
      if (!text) return;
      if (now() - (member.lastChatAt || 0) < 350) return;
      member.lastChatAt = now();
      const message = {
        id: randomHex(6),
        memberId: member.id,
        name: member.name,
        color: member.color,
        text,
        at: now(),
      };
      room.chats.push(message);
      if (room.chats.length > 80) room.chats.shift();
      broadcast(room, { type: 'chat', message });
      return;
    }

    case 'reaction': {
      const emoji = typeof msg.emoji === 'string' ? msg.emoji : '❤️';
      if (!ALLOWED_REACTIONS.has(emoji)) return;
      if (now() - (member.lastReactionAt || 0) < 180) return;
      member.lastReactionAt = now();
      broadcast(room, {
        type: 'reaction',
        emoji,
        memberId: member.id,
        name: member.name,
        at: now(),
      });
      return;
    }

    case 'ping': {
      sendSocketText(member.socket, JSON.stringify({ type: 'pong', at: now() }));
      return;
    }

    default:
      return;
  }
}

function upgradeWebSocket(req, socket, head) {
  const parsed = new URL(req.url, 'http://localhost');
  if (parsed.pathname !== '/ws') {
    socket.write('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
    socket.destroy();
    return;
  }
  const code = parsed.searchParams.get('code') || '';
  const name = safeText(parsed.searchParams.get('name') || '', 24) || '听众';
  const role = parsed.searchParams.get('role') === 'host' ? 'host' : 'guest';
  const room = rooms.get(code);
  if (!validateRoomCode(code) || !room) {
    socket.write('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
    socket.destroy();
    return;
  }
  if (role === 'host' && req.headers['x-host-token'] !== room.hostToken) {
    socket.write('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');
    socket.destroy();
    return;
  }
  if (room.members.size >= MAX_MEMBERS) {
    socket.write('HTTP/1.1 409 Conflict\r\nConnection: close\r\n\r\n');
    socket.destroy();
    return;
  }
  const key = req.headers['sec-websocket-key'];
  if (!key || req.headers.upgrade?.toLowerCase() !== 'websocket') {
    socket.write('HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n');
    socket.destroy();
    return;
  }

  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${wsAccept(key)}\r\n` +
    '\r\n'
  );

  const member = {
    id: randomHex(6),
    name: uniqueName(room, name),
    role,
    color: MEMBER_COLORS[room.members.size % MEMBER_COLORS.length],
    joinedAt: now(),
    lastSeen: now(),
    alive: true,
    socket: null,
    lastChatAt: 0,
    lastReactionAt: 0,
  };
  room.members.set(member.id, member);

  if (role === 'host') {
    room.hostId = member.id;
    room.hostName = member.name;
  }

  attachWebSocket(socket, room, member);

  sendSocketText(socket, JSON.stringify({
    type: 'welcome',
    version: 1,
    selfId: member.id,
    serverNow: now(),
    room: publicRoom(room),
    chats: room.chats.slice(-50),
  }));

  broadcastMembers(room);

  if (role === 'host') {
    broadcast(room, {
      type: 'host',
      hostId: room.hostId,
      hostName: room.hostName,
      serverNow: now(),
    });
  }

  console.log(`[room ${room.code}] + ${member.name} (${role}) from ${socket.remoteAddress}`);
  if (head && head.length) socket.emit('data', head);
}

function uniqueName(room, wanted) {
  const names = new Set([...room.members.values()].map((m) => m.name));
  if (!names.has(wanted)) return wanted;
  for (let i = 2; i < 100; i++) {
    const candidate = `${wanted} ${i}`;
    if (!names.has(candidate)) return candidate;
  }
  return `${wanted} ${randomHex(2)}`;
}

// ---------------------------------------------------------------------------
// HTTP API
// ---------------------------------------------------------------------------

async function handleTrackUpload(req, res, room, query) {
  if (req.headers['x-host-token'] !== room.hostToken) {
    return sendError(res, 403, 'host token required');
  }
  const title = safeText(query.get('title') || '未命名歌曲', 120) || '未命名歌曲';
  const artist = safeText(query.get('artist') || '未知艺术家', 120) || '未知艺术家';
  const album = safeText(query.get('album') || '', 120);
  const durationMs = clamp(query.get('durationMs') || 0, 0, 24 * 60 * 60 * 1000);
  const requestedType = req.headers['content-type'] || 'application/octet-stream';
  const contentType = /^audio\//.test(requestedType) ? requestedType : 'application/octet-stream';

  const trackId = randomHex(8);
  const temp = path.join(DATA_DIR, `${room.code}-${trackId}.part`);
  const final = path.join(DATA_DIR, `${room.code}-${trackId}.audio`);
  let written = 0;

  try {
    await new Promise((resolve, reject) => {
      const out = fs.createWriteStream(temp);
      req.on('data', (chunk) => {
        written += chunk.length;
        if (written > MAX_UPLOAD) {
          req.destroy();
          out.destroy();
          const err = new Error('audio file too large');
          err.status = 413;
          reject(err);
        }
      });
      req.pipe(out);
      out.on('finish', resolve);
      out.on('error', reject);
      req.on('error', reject);
      req.on('aborted', () => reject(Object.assign(new Error('upload aborted'), { status: 499 })));
    });
    if (written === 0) throw Object.assign(new Error('empty audio'), { status: 400 });
    await fsp.rename(temp, final);
  } catch (err) {
    await removeQuietly(temp);
    throw err;
  }

  if (room.track) await removeQuietly(room.track.file);
  if (room.cover) {
    await removeQuietly(room.cover.file);
    room.cover = null;
  }
  room.track = {
    id: trackId,
    title,
    artist,
    album,
    durationMs,
    size: written,
    contentType,
    file: final,
    uploadedAt: now(),
  };
  room.playback = { playing: false, positionMs: 0, startAt: 0, updatedAt: now() };
  touch(room);
  broadcast(room, { type: 'track', track: publicTrack(room), serverNow: now() });
  broadcast(room, {
    type: 'playback', action: 'pause', positionMs: 0, startAt: 0, playing: false,
    by: room.hostName, serverNow: now(),
  });
  console.log(`[room ${room.code}] track "${title}" ${(written / 1024 / 1024).toFixed(1)} MB`);
  sendJson(res, 201, { track: publicTrack(room) });
}

async function handleCoverUpload(req, res, room) {
  if (req.headers['x-host-token'] !== room.hostToken) {
    return sendError(res, 403, 'host token required');
  }
  if (!room.track) return sendError(res, 409, 'upload a track first');
  const requestedType = req.headers['content-type'] || '';
  if (!/^image\/(jpeg|png|webp)/.test(requestedType)) {
    return sendError(res, 415, 'cover must be jpeg, png or webp');
  }
  const coverId = randomHex(6);
  const temp = path.join(DATA_DIR, `${room.code}-${coverId}.cover.part`);
  const final = path.join(DATA_DIR, `${room.code}-${coverId}.cover`);
  let written = 0;
  try {
    await new Promise((resolve, reject) => {
      const out = fs.createWriteStream(temp);
      req.on('data', (chunk) => {
        written += chunk.length;
        if (written > MAX_COVER) {
          req.destroy();
          out.destroy();
          reject(Object.assign(new Error('cover too large'), { status: 413 }));
        }
      });
      req.pipe(out);
      out.on('finish', resolve);
      out.on('error', reject);
      req.on('error', reject);
    });
    if (!written) throw Object.assign(new Error('empty cover'), { status: 400 });
    await fsp.rename(temp, final);
  } catch (err) {
    await removeQuietly(temp);
    throw err;
  }
  if (room.cover) await removeQuietly(room.cover.file);
  room.cover = { file: final, contentType: requestedType };
  touch(room);
  broadcast(room, { type: 'track', track: publicTrack(room), serverNow: now() });
  sendJson(res, 201, { track: publicTrack(room) });
}

async function serveFile(req, res, file, contentType, cacheKey) {
  let stat;
  try { stat = await fsp.stat(file); } catch { return sendError(res, 404, 'file not found'); }
  const size = stat.size;
  const range = req.headers.range;
  const headers = {
    'Content-Type': contentType,
    'Accept-Ranges': 'bytes',
    'Cache-Control': 'private, max-age=3600',
    'Access-Control-Allow-Origin': '*',
    'Content-Disposition': 'inline',
  };
  if (cacheKey) headers['ETag'] = `"${cacheKey}"`;

  if (range) {
    const match = /^bytes=(\d*)-(\d*)$/.exec(range);
    if (!match) {
      res.writeHead(416, { 'Content-Range': `bytes */${size}` });
      return res.end();
    }
    let start = match[1] === '' ? NaN : Number(match[1]);
    let end = match[2] === '' ? NaN : Number(match[2]);
    if (Number.isNaN(start)) {
      const suffixLength = Number.isNaN(end) ? 0 : end;
      start = Math.max(0, size - suffixLength);
      end = size - 1;
    } else if (Number.isNaN(end)) {
      end = size - 1;
    }
    if (start > end || start >= size) {
      res.writeHead(416, { 'Content-Range': `bytes */${size}` });
      return res.end();
    }
    end = Math.min(end, size - 1);
    res.writeHead(206, {
      ...headers,
      'Content-Range': `bytes ${start}-${end}/${size}`,
      'Content-Length': end - start + 1,
    });
    if (req.method === 'HEAD') return res.end();
    const stream = fs.createReadStream(file, { start, end });
    stream.on('error', () => res.destroy());
    return stream.pipe(res);
  }

  res.writeHead(200, { ...headers, 'Content-Length': size });
  if (req.method === 'HEAD') return res.end();
  const stream = fs.createReadStream(file);
  stream.on('error', () => res.destroy());
  stream.pipe(res);
}

async function route(req, res) {
  const parsed = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = decodeURIComponent(parsed.pathname);

  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, HEAD, POST, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, X-Host-Token',
      'Access-Control-Max-Age': '86400',
    });
    return res.end();
  }

  if (pathname === '/' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end(
      `${SERVER_NAME}\n同频房间服务器正在运行。\n\n` +
      `健康检查: /api/health\n` +
      `当前房间: ${rooms.size}\n`
    );
  }

  if (pathname === '/api/health' && req.method === 'GET') {
    return sendJson(res, 200, {
      ok: true,
      name: SERVER_NAME,
      version: 1,
      time: now(),
      rooms: rooms.size,
      capacity: MAX_MEMBERS,
    });
  }

  if (pathname === '/api/rooms' && req.method === 'POST') {
    const body = await readJson(req);
    const hostName = safeText(body.hostName, 24) || '房主';
    const room = makeRoom(hostName);
    rooms.set(room.code, room);
    console.log(`[room ${room.code}] created by ${hostName}`);
    return sendJson(res, 201, {
      code: room.code,
      hostToken: room.hostToken,
      hostName: room.hostName,
      wsPath: '/ws',
      publicUrl: PUBLIC_URL || null,
    });
  }

  const match = /^\/api\/rooms\/(\d{6})(\/[a-z]+)?$/.exec(pathname);
  if (!match) return sendError(res, 404, 'not found');
  const code = match[1];
  const action = match[2] || '';
  const room = rooms.get(code);
  if (!room) return sendError(res, 404, 'room not found or expired');

  if (action === '' && req.method === 'GET') {
    return sendJson(res, 200, {
      ...publicRoom(room),
      serverNow: now(),
      audioAvailable: Boolean(room.track),
    });
  }

  if (action === '' && req.method === 'DELETE') {
    if (req.headers['x-host-token'] !== room.hostToken) {
      return sendError(res, 403, 'host token required');
    }
    await destroyRoom(code, 'deleted by host');
    return sendJson(res, 200, { ok: true });
  }

  if (action === '/track' && req.method === 'POST') {
    return handleTrackUpload(req, res, room, parsed.searchParams);
  }

  if (action === '/cover' && req.method === 'POST') {
    return handleCoverUpload(req, res, room);
  }

  if (action === '/audio' && (req.method === 'GET' || req.method === 'HEAD')) {
    if (!room.track) return sendError(res, 404, 'no track yet');
    const wanted = parsed.searchParams.get('track');
    if (wanted && wanted !== room.track.id) return sendError(res, 409, 'track changed');
    touch(room);
    return serveFile(req, res, room.track.file, room.track.contentType, room.track.id);
  }

  if (action === '/cover' && (req.method === 'GET' || req.method === 'HEAD')) {
    if (!room.cover) return sendError(res, 404, 'no cover');
    const wanted = parsed.searchParams.get('track');
    if (wanted && room.track && wanted !== room.track.id) return sendError(res, 409, 'track changed');
    return serveFile(req, res, room.cover.file, room.cover.contentType, `${room.track.id}-cover`);
  }

  return sendError(res, 405, 'method not allowed');
}

const requestHandler = (req, res) => {
  route(req, res).catch((err) => {
    const status = err.status || 500;
    if (status >= 500) console.error(err);
    if (!res.headersSent) sendError(res, status, err.message || 'internal error');
    else res.destroy();
  });
};

let server;
const tlsCert = process.env.TLS_CERT;
const tlsKey = process.env.TLS_KEY;
if (tlsCert && tlsKey) {
  server = https.createServer({
    cert: fs.readFileSync(tlsCert),
    key: fs.readFileSync(tlsKey),
  }, requestHandler);
} else {
  server = http.createServer(requestHandler);
}

server.on('upgrade', (req, socket, head) => {
  try { upgradeWebSocket(req, socket, head); }
  catch (err) {
    console.error('upgrade failed', err);
    socket.destroy();
  }
});

server.on('clientError', (err, socket) => {
  if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
  else socket.destroy();
});

async function destroyRoom(code, reason) {
  const room = rooms.get(code);
  if (!room) return;
  rooms.delete(code);
  for (const member of room.members.values()) {
    sendSocketText(member.socket, JSON.stringify({ type: 'ended', reason }));
    closeSocket(member.socket, 1000, 'room ended');
  }
  room.members.clear();
  if (room.track) await removeQuietly(room.track.file);
  if (room.cover) await removeQuietly(room.cover.file);
  console.log(`[room ${code}] destroyed: ${reason}`);
}

setInterval(() => {
  const t = now();
  for (const room of [...rooms.values()]) {
    for (const member of [...room.members.values()]) {
      if (!member.alive) {
        closeSocket(member.socket, 1001, 'heartbeat timeout');
        removeMember(room, member);
      } else {
        member.alive = false;
        sendSocketControl(member.socket, 0x9, Buffer.from('ping'));
      }
    }
    const idleFor = t - room.lastActive;
    const empty = room.members.size === 0;
    if ((empty && idleFor > EMPTY_ROOM_TTL_MS) || idleFor > ROOM_TTL_MS) {
      destroyRoom(room.code, empty ? 'empty for too long' : 'idle for too long');
    }
  }
}, 30000).unref();

function localAddresses() {
  const out = [];
  for (const list of Object.values(os.networkInterfaces())) {
    for (const item of list || []) {
      if (item.family === 'IPv4' && !item.internal) out.push(item.address);
    }
  }
  return out;
}

server.listen(PORT, HOST, () => {
  console.log('');
  console.log(`  ${SERVER_NAME} · 同频房间服务器`);
  console.log('  ------------------------------------');
  console.log(`  本机:   http://127.0.0.1:${PORT}`);
  for (const ip of localAddresses()) console.log(`  局域网: http://${ip}:${PORT}`);
  if (PUBLIC_URL) console.log(`  公网:   ${PUBLIC_URL}`);
  console.log(`  数据:   ${DATA_DIR}`);
  console.log(`  上限:   ${MAX_MEMBERS} 人/房间 · ${(MAX_UPLOAD / 1024 / 1024).toFixed(0)} MB/首歌`);
  console.log('  在 App 首页 → 设置里填入上面的地址。');
  console.log('');
});

process.on('SIGINT', () => {
  console.log('\n再见。');
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1500).unref();
});
