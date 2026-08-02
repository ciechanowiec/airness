# Git hooks

These are unpacked to `target/airness/githooks/` by the build. Point git at them once, per clone:

```sh
git config core.hooksPath target/airness/githooks
```

They are not copied into the repository, and a copy there fails the build. A hook that lives in the
project is a hook that gets edited locally and then silently disagrees with CI, which is worse than
having none: the disagreement is invisible until a push that passed locally fails in the pipeline.
