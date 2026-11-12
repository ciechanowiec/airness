// The services run the image gate boots a member's image in when the member declares no test wrapper: it starts no
// container, and gives the boot what every run gives it, a prefix and a network of its own, a guard, and the
// removal of everything carrying the prefix however the run ends.
import { withServices } from './services.js'

process.exitCode = await withServices({ project: 'airness-image', containers: [], environment: () => ({}) })
