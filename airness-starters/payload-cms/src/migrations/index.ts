import * as migration_20260911_173624_initial from './20260911_173624_initial';
import * as migration_20261111_131844_media_object_key from './20261111_131844_media_object_key';
import * as migration_20261111_171218_audit_events from './20261111_171218_audit_events';
import * as migration_20261115_184906_posts_categories_site_settings from './20261115_184906_posts_categories_site_settings';

export const migrations = [
  {
    up: migration_20260911_173624_initial.up,
    down: migration_20260911_173624_initial.down,
    name: '20260911_173624_initial',
  },
  {
    up: migration_20261111_131844_media_object_key.up,
    down: migration_20261111_131844_media_object_key.down,
    name: '20261111_131844_media_object_key',
  },
  {
    up: migration_20261111_171218_audit_events.up,
    down: migration_20261111_171218_audit_events.down,
    name: '20261111_171218_audit_events',
  },
  {
    up: migration_20261115_184906_posts_categories_site_settings.up,
    down: migration_20261115_184906_posts_categories_site_settings.down,
    name: '20261115_184906_posts_categories_site_settings'
  },
];
