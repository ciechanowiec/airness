---
name: payload
description: Use when working with Payload CMS projects (payload.config.ts, collections, fields, hooks, access control, Payload API). Use when debugging validation errors, security issues, relationship queries, transactions, or hook behavior.
---

# Payload CMS Application Development

Payload is a Next.js native CMS with TypeScript-first architecture, providing admin panel, database management, REST/GraphQL APIs, authentication, and file storage.

In this repository `.airness/agent-guide-payload.md` and the gates it describes govern wherever this skill or its references show a different pattern, such as another database adapter, an inline hook, or a type assertion.

## Quick Reference

| Task                     | Solution                                  | Details                                                                                                                          |
| ------------------------ | ----------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Auto-generate slugs      | `slugField()`                             | [FIELDS.md#slug-field-helper](reference/FIELDS.md#slug-field-helper)                                                             |
| Restrict content by user | Access control with query                 | [ACCESS-CONTROL.md#row-level-security-with-complex-queries](reference/ACCESS-CONTROL.md#row-level-security-with-complex-queries) |
| Local API user ops       | `user` + `overrideAccess: false`          | [QUERIES.md#access-control-in-local-api](reference/QUERIES.md#access-control-in-local-api)                                       |
| Draft/publish workflow   | `versions: { drafts: true }`              | [COLLECTIONS.md#versioning--drafts](reference/COLLECTIONS.md#versioning--drafts)                                                 |
| Computed fields          | `virtual: true` with afterRead            | [FIELDS.md#virtual-fields](reference/FIELDS.md#virtual-fields)                                                                   |
| Conditional fields       | `admin.condition`                         | [FIELDS.md#conditional-fields](reference/FIELDS.md#conditional-fields)                                                           |
| Custom field validation  | `validate` function                       | [FIELDS.md#text-field](reference/FIELDS.md#text-field)                                                                           |
| Filter relationship list | `filterOptions` on field                  | [FIELDS.md#relationship](reference/FIELDS.md#relationship)                                                                       |
| Select specific fields   | `select` parameter                        | [QUERIES.md#local-api](reference/QUERIES.md#local-api)                                                               |
| Auto-set author/dates    | beforeChange hook                         | [HOOKS.md#collection-hooks](reference/HOOKS.md#collection-hooks)                                                                 |
| Prevent hook loops       | `req.context` check                       | [HOOKS.md#hook-context](reference/HOOKS.md#hook-context)                                                                                   |
| Cascading deletes        | beforeDelete hook                         | [HOOKS.md#collection-hooks](reference/HOOKS.md#collection-hooks)                                                                 |
| Geospatial queries       | `point` field with `near`/`within`        | [FIELDS.md#point-geolocation](reference/FIELDS.md#point-geolocation)                                                             |
| Reverse relationships    | `join` field type                         | [FIELDS.md#join-fields](reference/FIELDS.md#join-fields)                                                                         |
| Next.js revalidation     | Context control in afterChange            | [HOOKS.md#nextjs-revalidation-with-context-control](reference/HOOKS.md#nextjs-revalidation-with-context-control)                 |
| Query by relationship    | Nested property syntax                    | [QUERIES.md#nested-properties](reference/QUERIES.md#nested-properties)                                                           |
| Complex queries          | AND/OR logic                              | [QUERIES.md#andor-logic](reference/QUERIES.md#andor-logic)                                                                       |
| Transactions             | Pass `req` to operations                  | [ADAPTERS.md#threading-req-through-operations](reference/ADAPTERS.md#threading-req-through-operations)                           |
| Background jobs          | Jobs queue with tasks                     | [ADVANCED.md#jobs-queue](reference/ADVANCED.md#jobs-queue)                                                                       |
| Custom API routes        | Collection custom endpoints               | [ADVANCED.md#custom-endpoints](reference/ADVANCED.md#custom-endpoints)                                                           |
| Cloud storage            | Storage adapter plugins                   | [ADAPTERS.md#storage-adapters](reference/ADAPTERS.md#storage-adapters)                                                           |
| Multi-language           | `localization` config + `localized: true` | [ADVANCED.md#localization](reference/ADVANCED.md#localization)                                                                   |
| Create plugin            | `(options) => (config) => Config`         | [PLUGIN-DEVELOPMENT.md#plugin-architecture](reference/PLUGIN-DEVELOPMENT.md#plugin-architecture)                                 |
| Plugin package setup     | Package structure with SWC                | [PLUGIN-DEVELOPMENT.md#plugin-package-structure](reference/PLUGIN-DEVELOPMENT.md#plugin-package-structure)                       |
| Add fields to collection | Map collections, spread fields            | [PLUGIN-DEVELOPMENT.md#adding-fields-to-collections](reference/PLUGIN-DEVELOPMENT.md#adding-fields-to-collections)               |
| Plugin hooks             | Preserve existing hooks in array          | [PLUGIN-DEVELOPMENT.md#adding-hooks](reference/PLUGIN-DEVELOPMENT.md#adding-hooks)                                               |
| Check field type         | Type guard functions                      | [FIELD-TYPE-GUARDS.md](reference/FIELD-TYPE-GUARDS.md)                                                                           |

## Security Pitfall: Local API Access Control

**By default, Local API operations bypass ALL access control**, even when passing a user.

```ts
// ❌ SECURITY BUG: Passes user but ignores their permissions
await payload.find({
  collection: 'posts',
  user: someUser, // Access control is BYPASSED!
})

// ✅ SECURE: Actually enforces the user's permissions
await payload.find({
  collection: 'posts',
  user: someUser,
  overrideAccess: false, // REQUIRED for access control
})
```

**When to use each:**

- `overrideAccess: true` (default) - Server-side operations you trust (cron jobs, system tasks)
- `overrideAccess: false` - When operating on behalf of a user (API routes, webhooks)

See [QUERIES.md#access-control-in-local-api](reference/QUERIES.md#access-control-in-local-api).

## Reference Documentation

- **[FIELDS.md](reference/FIELDS.md)** - All field types, validation, admin options
- **[FIELD-TYPE-GUARDS.md](reference/FIELD-TYPE-GUARDS.md)** - Type guards for runtime field type checking and narrowing
- **[COLLECTIONS.md](reference/COLLECTIONS.md)** - Collection configs, auth, upload, drafts, live preview
- **[HOOKS.md](reference/HOOKS.md)** - Collection hooks, field hooks, context patterns
- **[ACCESS-CONTROL.md](reference/ACCESS-CONTROL.md)** - Collection, field, global access control, RBAC, multi-tenant
- **[ACCESS-CONTROL-ADVANCED.md](reference/ACCESS-CONTROL-ADVANCED.md)** - Context-aware, time-based, subscription-based access, factory functions, templates
- **[QUERIES.md](reference/QUERIES.md)** - Query operators, Local/REST/GraphQL APIs
- **[ENDPOINTS.md](reference/ENDPOINTS.md)** - Custom API endpoints: authentication, helpers, request/response patterns
- **[ADAPTERS.md](reference/ADAPTERS.md)** - Database, storage, email adapters, transactions
- **[ADVANCED.md](reference/ADVANCED.md)** - Authentication, jobs, endpoints, components, plugins, localization
- **[PLUGIN-DEVELOPMENT.md](reference/PLUGIN-DEVELOPMENT.md)** - Plugin architecture, monorepo structure, patterns, best practices

## Resources

- llms-full.txt: <https://payloadcms.com/llms-full.txt>
- Docs: <https://payloadcms.com/docs>
- GitHub: <https://github.com/payloadcms/payload>
- Examples: <https://github.com/payloadcms/payload/tree/3.x/examples>
- Templates: <https://github.com/payloadcms/payload/tree/3.x/templates>
