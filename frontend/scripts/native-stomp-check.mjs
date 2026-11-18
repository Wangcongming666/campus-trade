/**
 * 验证后端原生 WebSocket STOMP 端点（不改前端默认 SockJS 实现）。
 * 用法：在 frontend 目录执行  node scripts/native-stomp-check.mjs
 */
import { Client } from '@stomp/stompjs'
import { execFileSync } from 'node:child_process'

const API = 'http://localhost:8080/api/v1'
const NATIVE_URL = 'ws://localhost:8080/ws'
const SOCKJS_RAW_URL = 'ws://localhost:8080/ws/websocket'
const TIMEOUT_MS = 12000

function redisGet(key) {
  return execFileSync('redis-cli', ['get', `captcha:${key}`], { encoding: 'utf8' }).trim()
}

async function api(method, urlPath, { token, body } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(`${API}${urlPath}`, { method, headers, body: payload })
  const data = await res.json().catch(() => ({}))
  return { http: res.status, ...data }
}

async function apiLogin(account, password) {
  const cap = await api('GET', '/auth/captcha')
  const res = await api('POST', '/auth/login', {
    body: {
      account,
      password,
      captchaKey: cap.data.captchaKey,
      captchaCode: redisGet(cap.data.captchaKey),
    },
  })
  if (res.code !== 200) throw new Error(`login ${account} ${JSON.stringify(res)}`)
  return res.data
}

function connectNative(url, token) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      try { client.deactivate() } catch {}
      reject(new Error(`CONNECT timeout: ${url}`))
    }, TIMEOUT_MS)
    const client = new Client({
      brokerURL: url,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      onConnect: () => {
        clearTimeout(timer)
        resolve(client)
      },
      onStompError: (frame) => {
        clearTimeout(timer)
        reject(new Error(`STOMP ERROR ${url}: ${frame?.headers?.message || frame?.body || 'unknown'}`))
      },
      onWebSocketError: (event) => {
        clearTimeout(timer)
        reject(new Error(`WebSocket error ${url}: ${event?.message || event?.type || 'error'}`))
      },
      onWebSocketClose: (event) => {
        // 若尚未 CONNECT 成功，关闭即失败
      },
    })
    client.activate()
  })
}

async function tryConnect(token) {
  const errors = []
  for (const url of [NATIVE_URL, SOCKJS_RAW_URL]) {
    try {
      const client = await connectNative(url, token)
      return { url, client }
    } catch (e) {
      errors.push(String(e.message || e))
    }
  }
  throw new Error(`无法 CONNECT 原生端点:\n- ${errors.join('\n- ')}`)
}

async function main() {
  const user1 = await apiLogin('user1', '123456')
  const user2 = await apiLogin('user2', '123456')
  const sessions = await api('GET', '/chat/sessions', { token: user1.accessToken })
  const list = Array.isArray(sessions.data) ? sessions.data : []
  const session = list.find((s) => s.peer?.id === user2.userInfo.id) || list[0]
  if (!session) throw new Error('user1 没有可用会话，无法发消息')
  const goodsId = session.goodsId
  const toUserId = user2.userInfo.id
  const content = `[native-ws] ${Date.now()}`

  const { url, client: sender } = await tryConnect(user1.accessToken)
  console.log('CONNECT ok:', url)

  const received = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('未在时限内收到回推消息')), TIMEOUT_MS)
    sender.subscribe('/user/queue/messages', (frame) => {
      try {
        const body = JSON.parse(frame.body || '{}')
        if (String(body.content || '').includes('[native-ws]')) {
          clearTimeout(timer)
          resolve(body)
        }
      } catch (e) {
        clearTimeout(timer)
        reject(e)
      }
    })
    sender.subscribe('/user/queue/errors', (frame) => {
      clearTimeout(timer)
      reject(new Error(`queue/errors: ${frame.body}`))
    })
    sender.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ toUserId, goodsId, content, type: 1 }),
    })
  })

  await sender.deactivate()
  console.log('SEND/RECV ok:', {
    id: received.id,
    sessionId: received.sessionId,
    type: received.type,
    content: received.content,
  })
  console.log('NATIVE_STOMP_OK')
}

main().catch((e) => {
  console.error('NATIVE_STOMP_FAIL', e)
  process.exitCode = 1
})
