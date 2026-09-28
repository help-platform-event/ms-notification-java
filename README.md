# ms-notification-java

Notification service of the [H.E.L.P platform](https://github.com/help-platform-event), in Java 21 / Spring Boot 4.

It **consumes Kafka events** published by the other services and turns them into notifications for users: emails today, in-app notifications next. It never calls the other services: everything it knows about users (email, notification preferences) comes from the events.

> Status: work in progress. Emails are done: account and security emails from ms-auth's events, and participation emails from the Gateway's events, filtered by the user's preferences. In-app notifications (REST API + bell in the Front) are the next step.

## How it works

```
ms-auth-java ── auth.user.registered ─────────┐
             ── auth.user.settings-changed ───┤
             ── auth.password.changed ────────┤
Gateway ────── event.participation.requested ─┤
          ──── event.participation.decided ───┤
                                              ▼
                          ms-notification-java  (consumer group "ms-notification")
                           ├─ recipients: local copy of users (email + notification preferences)
                           ├─ processed_events: ids of the events already handled
                           └─ email ──► SMTP
                                │ failure: retried after 2 s, 4 s, 8 s
                                ▼
                           <topic>-dlt  (dead-letter topic)
```

- **Projection.** `auth.user.registered` gives a user's email, `auth.user.settings-changed` their preferences (a full snapshot on a log-compacted topic). The service keeps its own copy in the `recipients` table, so it never has to ask ms-auth.
- **Replay.** A new consumer group starts from the oldest message (`auto-offset-reset=earliest`), and ms-auth keeps `auth.user.registered` forever. On its first start, the service therefore rebuilds its view of every existing user.
- **Idempotency.** Kafka delivers at least once, so the same message can arrive twice (after a crash, a rebalance, an offset reset). Every handled message id is stored in `processed_events` in the same transaction as its effect, and a duplicate is ignored. One limit is accepted: if the service crashes after sending an email but before committing, the event is handled again and that email is sent twice. For notifications, that's acceptable.
- **Transactional emails.** A welcome email on registration, and a security email on password change. They are sent whatever the user's preferences, like the account and security categories they belong to.
- **Event activity notifications, filtered by preferences.** The Gateway publishes `event.participation.requested` (the organizer is told someone wants to join a slot) and `event.participation.decided` (the volunteer is told whether they were accepted). Before sending, the service checks the recipient's preferences from its local copy: the master switch **and** the "event activity" switch must both be on (`NotificationPreferences.allows`). Nobody is notified of their own action (e.g. an organizer joining their own slot). A skipped event is still marked processed.
- **Retries and dead-letter topic.** Each event handler sends its email itself, inside its database transaction. If sending fails (SMTP server down, 5 s timeout), the exception rolls the transaction back and Spring Kafka's standard error handler retries the event after 2 s, 4 s, then 8 s. After that it publishes the event to a **dead-letter topic** (`<topic>-dlt`, e.g. `auth.user.registered-dlt`), logs an error and moves on. The event stays there for inspection or replay. A message that can't be parsed goes straight to the dead-letter topic.
- **Topics.** Whoever publishes a topic declares it: ms-auth and the Gateway create theirs, and this service only creates its dead-letter topics. It never creates a topic by subscribing to it.

Code layout: strict Clean Architecture, the same as ms-auth-java (`domain` / `application` / `infrastructure`). See `CLAUDE.md`.

## Run

### Full stack (recommended)

From [`event-app`](https://github.com/help-platform-event/event-app), with this repo and `ms-auth-java` cloned next to it:

```bash
pnpm stack:up
```

This starts this service as well, on port 8085, with its MySQL and Mailpit.

### Alone, on the host (hot reload)

This service uses ms-auth-java's Kafka broker (`localhost:9094`), so start ms-auth-java first (`./mvnw spring-boot:run` in its repo). Then:

```bash
./mvnw spring-boot:run
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

With `pnpm stack:up` running, open Kafka UI (http://localhost:8082) and Mailpit (http://localhost:8025):

1. **Live flow.** Sign up in the Front. The message shows up in `auth.user.registered`, the welcome email in Mailpit, and the `ms-notification` consumer group stays at lag 0.
2. **Catch-up.** Stop the service (`docker stop event-app-notification-app-1`) and sign up a few users: the group's lag grows. Start it again (`docker start event-app-notification-app-1`): it catches up and the emails arrive.
3. **Preferences.** Log in as a volunteer and turn off "Activité événement" in the Settings, then have the organizer accept the volunteer's request: no email. Turn it back on and accept another request: the email arrives. The message shows up in `event.participation.decided` either way.
4. **Retries and DLT.** Stop Mailpit (`docker stop event-app-mailpit-1`) and sign up. About 15 s later (4 attempts), the event is in `auth.user.registered-dlt` and the service logs `Giving up on auth.user.registered-…`. Start Mailpit again (`docker start event-app-mailpit-1`).
5. **Replay without duplicates.** Stop the service, reset the group's offsets, then start it again. It re-reads everything but sends nothing twice (`processed_events`):
   ```bash
   docker exec event-app-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 \
     --group ms-notification --reset-offsets --to-earliest --all-topics --execute
   ```
