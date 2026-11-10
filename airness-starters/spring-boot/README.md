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
- Five refused sign-ins in a row stop an account for fifteen minutes. Stopping an account, or changing
  its address or role, ends its sessions at once. A session lapses after thirty idle minutes.
- Every sign-in and every change to an account is written to an audit trail the database refuses to
  change.

## Build and run

| Command | What it does |
| --- | --- |
| `mvn clean verify` | Default verification, which also builds the archive |
| `mvn clean verify -Pextended` | Extended verification, including the browser sweep |
| `mvn process-resources -Pformat` | Applies the Java formatting |
| `pnpm run format` | Applies the TypeScript and stylesheet formatting |
| `docker compose up --build` | Runs the archive and a database, after `cp .env.example .env` |

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
