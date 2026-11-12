# Starter

A multi-user task tracker that is a generic beginning for an application built on
[Airness](https://github.com/ciechanowiec/airness): Spring Boot, server-rendered Thymeleaf pages with
htmx, Tailwind CSS with daisyUI, PostgreSQL, and English and Polish.

## What it does

- People sign in with an address and a password. There is no self-registration: an administrator adds
  every account, and the first administrator is written from the environment on the first start.
- A user keeps tasks of their own, each with a title, a description, a status and a due day. The list
  is searched, narrowed and ordered in place, and a task past its day is marked overdue.
- An administrator sees every task with who keeps it, and adds, changes, stops and readmits accounts.
- Five refused sign-ins in a row stop an account for fifteen minutes, and a current password refused
  on the page that changes it counts as one. Stopping an account, or changing its address or role, ends
  its sessions at once. A session lapses after thirty idle minutes.
- Every sign-in, every change to an account, and every change an administrator makes to a task somebody
  else keeps is written to an audit trail the database refuses to change.

## Build and run

| Command | What it does |
| --- | --- |
| `mvn clean verify` | Default verification, which also builds the archive |
| `mvn clean verify -Pextended` | Extended verification, including the browser sweep and a start of the Docker image against an empty database |
| `mvn process-resources -Pformat` | Applies the Java formatting |
| `pnpm run format` | Applies the TypeScript and stylesheet formatting |
| `docker compose up --build` | Runs the archive and a database on `http://localhost:8080`, once `.env` exists |

Before the first `docker compose up`, run `cp .env.example .env` and choose the two values the copy
leaves empty: `APP_DATABASE_PASSWORD`, which the database is created with, and
`APP_ADMINISTRATOR_PASSWORD`, which the first administrator signs in with under
`APP_ADMINISTRATOR_EMAIL` and which has to be twelve characters or longer and seventy-two bytes or
shorter. Neither starts with a blank one. The database keeps the password its volume was created with,
and the administrator's is read only on the start that finds no account.

Verification needs Java 25, Maven 3.10 or later and a running Docker daemon, which the tests start
their database in.
<!-- @SNAPSHOT_ONLY_BEGIN@ -->

This project builds against a SNAPSHOT of Airness, whose npm packages are never published, so every
build starts a local registry with `python3` that serves `@airness/node` from the Airness artifacts
installed in the local Maven repository. `python3` must be on the path and the Airness version this
project names must be installed there.
<!-- @SNAPSHOT_ONLY_END@ -->

## Where things are

| Path | What it holds |
| --- | --- |
| `src/main/java/.../identity` | Sign-in, roles, accounts and their pages |
| `src/main/java/.../task` | The example domain, which an application replaces |
| `src/main/resources/templates/components` | The component fragments every page is drawn with |
| `src/main/frontend` | The stylesheet and the page script the frontend build bundles |
| `src/main/resources/db/migration` | The Flyway migrations, which own the schema |
| `tests/e2e` | The browser specifications Extended verification runs |
