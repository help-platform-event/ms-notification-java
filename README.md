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
                           ├─ notifications: in-app notifications ──► REST API (the bell)
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
| `GET` | `/api/notifications/unread-count` | `{ "count": n }`: what the bell polls every 30 s |
| `PATCH` | `/api/notifications/{id}/read` | 204; 404 if unknown **or someone else's** (so other users' ids can't be probed) |
| `POST` | `/api/notifications/read-all` | 204 |

Security: an OAuth2 resource server checks ms-auth's HS256 access tokens with the shared `JWT_SECRET` (Base64), and the user is the token's `sub` claim. `JWT_SECRET` has no default: without it, the service refuses to start.

Code layout: strict Clean Architecture, the same as ms-auth-java (`domain` / `application` / `infrastructure` / `web`).

## Run

To run it with the rest of the platform (it needs ms-auth-java's events, and the Gateway's for participations), see the [organization page](https://github.com/help-platform-event).

### Alone, on the host (hot reload)

This service uses ms-auth-java's Kafka broker (`localhost:9094`), so start ms-auth-java first (`./mvnw spring-boot:run` in its repo). Then, with the same JWT secret as ms-auth (here its dev value):

```bash
JWT_SECRET=c2FtcGxlLWRldi1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2QtMzJieXRlcyE= ./mvnw spring-boot:run
```

`spring-boot-docker-compose` starts this repo's `compose.yaml` (MySQL + Mailpit). The `notification-app` service is behind the `app` profile and only runs from event-app's stack.

| | URL |
|---|---|
| Health | http://localhost:8085/actuator/health |
| Mailpit (every email sent) | http://localhost:8025 |
| Kafka UI (from ms-auth-java's compose) | http://localhost:8082 |
| MySQL | `localhost:3309`, db/user/password `ms_notification` |

## Tests

```bash
./mvnw test
```

Docker must be running: the integration test uses Testcontainers (MySQL, Kafka) and an in-JVM SMTP server (GreenMail).

## See Kafka at work

This needs the whole platform running (`pnpm stack:up`, see the [organization page](https://github.com/help-platform-event)). Open Kafka UI (http://localhost:8082) and Mailpit (http://localhost:8025):

1. **Live flow.** Sign up in the Front. The message shows up in `auth.user.registered`, the welcome email in Mailpit, and the `ms-notification` consumer group stays at lag 0.
2. **Catch-up.** Stop the service (`docker stop event-app-notification-app-1`) and sign up a few users: the group's lag grows. Start it again (`docker start event-app-notification-app-1`): it catches up and the emails arrive.
3. **Preferences.** Log in as a volunteer and turn off "Activité événement" in the Settings, then have the organizer accept the volunteer's request: no email and nothing in the bell. Turn it back on and accept another request: the email arrives and the bell shows it (within 30 s). The message shows up in `event.participation.decided` either way.
4. **Retries and DLT.** Stop Mailpit (`docker stop event-app-mailpit-1`) and sign up. About 15 s later (4 attempts), the event is in `auth.user.registered-dlt` and the service logs `Giving up on auth.user.registered-…`. Start Mailpit again (`docker start event-app-mailpit-1`).
5. **Replay without duplicates.** Stop the service, reset the group's offsets, then start it again. It re-reads everything but sends nothing twice (`processed_events`):
   ```bash
   docker exec event-app-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 \
     --group ms-notification --reset-offsets --to-earliest --all-topics --execute
   ```

## Known limits (v1)

- **At least once:** a crash between sending an email and committing can send it twice (see Idempotency).
- **Polling, not push:** the bell learns about a new notification within 30 s (or as soon as the tab regains focus), not instantly.
- **One switch per category:** a user can't choose "email yes, bell no".
- **Deleted events:** their notifications stay in the bell.
