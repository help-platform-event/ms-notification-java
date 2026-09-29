# ms-notification-java

Notification service of the [H.E.L.P platform](https://github.com/help-platform-event).

It **consumes Kafka events** published by the other services and turns them into notifications for users: emails, and in-app notifications shown in the Front's bell. It never calls the other services: everything it knows about users (email, notification preferences) comes from the events.

## How it works

```
ms-auth-java ── auth.user.registered ─────────┐
             ── auth.user.settings-changed ───┤
             ── auth.password.changed ────────┤
Gateway ────── event.participation.requested ─┤
          ──── event.participation.decided ───┤
          ──── event.participation.cancelled ─┤
                                              ▼
                          ms-notification-java  (consumer group "ms-notification")
                           ├─ recipients: local copy of users (email + notification preferences)
                           ├─ processed_events: ids of the events already handled
                           ├─ notifications: in-app notifications ──► REST API + SSE stream (the bell)
                           └─ email ──► SMTP
                                │ failure: retried after 2 s, 4 s, 8 s
                                ▼
                           <topic>-dlt  (dead-letter topic)
```

- **Projection.** `auth.user.registered` gives a user's email, `auth.user.settings-changed` their preferences (a full snapshot on a log-compacted topic). The service keeps its own copy in the `recipients` table, so it never has to ask ms-auth.
- **Replay.** A new consumer group starts from the oldest message (`auto-offset-reset=earliest`), and ms-auth keeps `auth.user.registered` forever. On its first start, the service therefore rebuilds its view of every existing user.
- **Idempotency.** Kafka delivers at least once, so the same message can arrive twice (after a crash, a rebalance, an offset reset). Every handled message id is stored in `processed_events` in the same transaction as its effect, and a duplicate is ignored. One limit is accepted: if the service crashes after sending an email but before committing, the event is handled again and that email is sent twice. For notifications, that's acceptable.
- **Transactional emails.** A welcome email on registration, and a security email on password change. They are sent whatever the user's preferences, like the account and security categories they belong to.
- **Event activity notifications, filtered by preferences.** The Gateway publishes `event.participation.requested` (the organizer is told someone wants to join a slot), `event.participation.decided` (the volunteer is told whether they were accepted) and `event.participation.cancelled` (the other party is told: the organizer when a volunteer cancels, the volunteer when the organizer removes them). Before sending, the service checks the recipient's preferences from its local copy: the master switch **and** the "event activity" switch must both be on (`NotificationPreferences.allows`). Nobody is notified of their own action (e.g. an organizer joining their own slot). A skipped event is still marked processed. When allowed, the service stores an **in-app notification** (shown in the bell), then sends the email if it knows the address.
- **Retries and dead-letter topic.** Each event handler sends its email itself, inside its database transaction. If sending fails (SMTP server down, 5 s timeout), the exception rolls the transaction back and Spring Kafka's standard error handler retries the event after 2 s, 4 s, then 8 s. After that it publishes the event to a **dead-letter topic** (`<topic>-dlt`, e.g. `auth.user.registered-dlt`), logs an error and moves on. The event stays there for inspection or replay. A message that can't be parsed goes straight to the dead-letter topic.
- **Topics.** Whoever publishes a topic declares it: ms-auth and the Gateway create theirs, and this service only creates its dead-letter topics. It never creates a topic by subscribing to it.

## REST API (in-app notifications)

The Front reaches it through the Gateway (`/notifications`), which forwards the user's access token.

| Method | Path | |
|---|---|---|
| `GET` | `/api/notifications?page=0` | The user's notifications, newest first (20 per page) |
| `GET` | `/api/notifications/stream` | Server-Sent Events: each new notification, pushed as `event: notification` (see below) |
| `GET` | `/api/notifications/unread-count` | `{ "count": n }`: the bell's badge, reloaded each time the stream (re)connects |
| `PATCH` | `/api/notifications/{id}/read` | 204; 404 if unknown **or someone else's** (so other users' ids can't be probed) |
| `POST` | `/api/notifications/read-all` | 204 |

Security: an OAuth2 resource server checks ms-auth's HS256 access tokens with the shared `JWT_SECRET` (Base64), and the user is the token's `sub` claim. `JWT_SECRET` has no default: without it, the service refuses to start.

### Live push (SSE)

The bell doesn't poll: `GET /api/notifications/stream` keeps an HTTP connection open and the
service writes to it only when a notification is created.

- Each browser tab has its own stream (`SseEmitter`), kept per user in `SseNotificationStreams`.
- A notification is pushed **after the transaction that saved it commits** (a rolled-back one is
  never shown), to every open tab of its recipient.
- A comment (`:keep-alive`) is written every 25 s: it keeps idle connections open through the
  Gateway and proxies, and writing to a closed connection removes the stream of a user who left.
- The stream closes when the access token expires; the Front reconnects with a fresh token and
  reloads the unread count, which catches up on anything missed while disconnected.

## Run

To run it with the rest of the platform (it needs ms-auth-java's events, and the Gateway's for participations), see the [organization page](https://github.com/help-platform-event).

## Known limits (v1)

- **At least once:** a crash between sending an email and committing can send it twice (see Idempotency).
- **One instance only:** the open SSE streams live in memory (`SseNotificationStreams`). With several instances, a user's stream and their notification could land on different ones; it would take a fan-out (e.g. every instance consuming a `notification.created` topic).
- **One switch per category:** a user can't choose "email yes, bell no".
- **Deleted events:** their notifications stay in the bell.
